package com.openspine.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Headless transaction and actual save/reopen checks for full native projects. */
public final class NativeProjectStoreTest {
	public static void main(String[] args) throws Exception {
		Path directory = Files.createTempDirectory("openspine-native-test-");
		try {
			savesCurrentDocumentsAndMembership(directory.resolve("roundtrip"));
			copiesExternalResourcesWithoutCollisions(directory.resolve("resources"));
			keepsOldProjectAfterFailedOrCancelledSave(directory.resolve("transactions"));
			rejectsExternalChanges(directory.resolve("conflicts"));
			detectsExternalResourceChanges(directory.resolve("resource-conflicts"));
			rejectsResourcesChangedDuringCopy(directory.resolve("resource-copy-race"));
			cancelsResourceHashWithoutBlocking(directory.resolve("resource-cancel"));
			preparesCancellableOpen(directory.resolve("prepared-open"));
			doesNotCollectNativeGenerationsAsLooseImages(directory.resolve("loose-root"));
			discardsUnsavedAuthoringChangesInRootSidecars(directory.resolve("view-sidecar"));
			rejectsUnreopenableResourceLayouts(directory.resolve("unsupported-resources"));
			rejectsUnsafeTargetsWithoutChangingThem(directory.resolve("validation"));
			System.out.println("ALL NATIVE PROJECT STORE TESTS PASSED");
		} finally {
			// The only cleanup root is the unique temporary directory created above.
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	private static void savesCurrentDocumentsAndMembership(Path directory) throws Exception {
		Files.createDirectories(directory);
		Path original = directory.resolve("original.spine");
		Files.writeString(original, "untouched Spine project");
		SkeletonDocument active = new SkeletonDocument();
		try (SkeletonProject project = new SkeletonProject(active)) {
			SkeletonDocument body = document(directory.resolve("body.json"), 12f);
			body.filePath = original.toString();
			Path psd = directory.resolve("body-layout.psd");
			Files.write(psd, new byte[] {8, 66, 80, 83, 2});
			body.setPsdSource(new SkeletonDocument.PsdSource(psd.toString(), false, 512, 256, List.of(
				new SkeletonDocument.PsdLayerMapping(0, "Body", "body", "default",
					"body", "body", true))));
			project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("身体", body),
				new SkeletonProject.LoadedSkeleton("Equipment", document(directory.resolve("gear.skel"), 34f))));
			active.seek(0.75f);
			active.setPlaying(false);
			active.markDirty();
			project.switchTo(1);
			active.seek(1.25f);
			active.playbackSpeed = 1.5f;
			active.setPlaying(false);
			active.markDirty();
			List<DocumentState> before = documentStates(project);
			Path target = directory.resolve("角色.openspine-project.json");
			NativeProjectStore.FileStamp missing = NativeProjectStore.inspect(target);
			assertTrue(!missing.exists(), "a new project begins with a missing-file stamp");
			NativeProjectStore.SaveResult first = NativeProjectStore.save(project, true, target, missing, new CancellationToken());
			assertEquals(first.stamp(), NativeProjectStore.inspect(target), "returned stamp matches published project");
			assertEquals(before, documentStates(project), "saving leaves document identities, dirty state, revisions and associations intact");
			assertTrue(project.isDirty(), "IO leaves marking histories saved to the successful UI commit");
			assertEquals("untouched Spine project", Files.readString(original), "original Spine source is unchanged");
			ProjectPackageManifest.Manifest manifest = ProjectPackageManifest.read(target);
			assertEquals(first.skeletons(), manifest.skeletons(), "saved associations match root manifest");
			Path generation = manifest.editorState().getParent();
			assertEquals("untouched Spine project", Files.readString(generation.resolve("originals/001/original.spine")),
				"native generation archives the original Spine source");
			Path packagedPsd = generation.resolve("skeletons/001/psd/body-layout.psd");
			assertTrue(Files.isRegularFile(packagedPsd), "native saves retain the managed PSD source");
			Map<Path, byte[]> firstGeneration = treeBytes(generation);
			assertRoundTrip(target, List.of("身体", "Equipment"), new float[] {12f, 34f}, 1, true);
			try (SkeletonProject reopened = open(target)) {
				assertNear(1.25f, reopened.documents().get(1).getCurrentTime(), "binary playhead survives reopen");
				assertNear(0.75f, reopened.documents().get(0).getCurrentTime(), "inactive JSON playhead survives reopen");
				assertNear(1.5f, reopened.documents().get(1).playbackSpeed, "editor playback settings survive reopen");
				assertTrue(reopened.documents().get(0).psdSource() != null
					&& Path.of(reopened.documents().get(0).psdSource().sourcePath()).equals(
						packagedPsd.toAbsolutePath().normalize()),
					"native reopen resolves PSD metadata to the published generation");
			}

			// A normal sidecar update must neither prevent Ctrl+S nor modify a generation.
			ProjectStateStore.write(target, project, false);
			assertEquals(first.stamp(), NativeProjectStore.inspect(target), "root view-state sidecar is outside the save conflict stamp");
			assertTreeUnchanged(firstGeneration, "root sidecar does not change a published generation");
			project.removeAt(0);
			project.rename(0, "武器");
			project.add(new SkeletonProject.LoadedSkeleton("背景", document(directory.resolve("background.json"), 56f)), true);
			project.move(1, 0);
			assertTrue(project.hasStructureChanges(), "member changes remain dirty until UI marks them saved");
			active.data.getBones().first().getSetupPose().setX(78f);
			active.markDirty();
			NativeProjectStore.SaveResult second = NativeProjectStore.save(project, false, target, first.stamp(), new CancellationToken());
			assertRoundTrip(target, List.of("背景", "武器"), new float[] {78f, 34f}, 0, false);
			assertTrue(!first.skeletons().get(0).runtime().equals(second.skeletons().get(0).runtime()),
				"each save publishes an isolated generation");
			assertTreeUnchanged(firstGeneration, "successful save retains every previous generation byte");
			assertEquals(ProjectStateStore.LoadResult.SOURCE_CHANGED, ProjectStateStore.load(target, project),
				"sidecar from previous manifest is stale after a new save");

			Path other = directory.resolve("another.OPENSPINE-PROJECT.JSON");
			NativeProjectStore.save(project, true, other, NativeProjectStore.inspect(other), new CancellationToken());
			assertRoundTrip(target, List.of("背景", "武器"), new float[] {78f, 34f}, 0, false);
			assertRoundTrip(other, List.of("背景", "武器"), new float[] {78f, 34f}, 0, true);
			assertNoTemporaryFiles(directory);
		}
	}

	private static void copiesExternalResourcesWithoutCollisions(Path directory) throws Exception {
		Files.createDirectories(directory);
		try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
			for (int index = 0; index < 2; index++) {
				Path resources = Files.createDirectories(directory.resolve("source-" + index));
				Files.write(resources.resolve("角色.png"), new byte[] {(byte)(index + 1), 9});
				Files.writeString(resources.resolve("hero.atlas"), "角色.png\nsize: 1, 1\nformat: RGBA8888\n"
					+ "filter: Nearest, Nearest\nrepeat: none\n", StandardCharsets.UTF_8);
				Path audio = Files.createDirectories(resources.resolve("audio"));
				Files.write(audio.resolve("cue.wav"), new byte[] {(byte)(index + 5)});
				SkeletonDocument document = document(directory.resolve("same.json"), index);
				document.resourceDirectory = resources.toString();
				document.atlasFileName = resources.resolve("hero.atlas").toString();
				document.data.setAudioPath("audio");
				EventData event = new EventData("cue");
				event.setAudioPath("cue.wav");
				event.getSetupPose().setString("");
				document.data.getEvents().add(event);
				project.add(new SkeletonProject.LoadedSkeleton("骨架 " + index, document), false);
			}
			Path looseSource = Files.createDirectories(directory.resolve("loose-source/images/face"));
			Files.write(looseSource.resolve("eye.PNG"), new byte[] {31, 32});
			SkeletonDocument loose = document(directory.resolve("loose.json"), 90f);
			loose.resourceDirectory = directory.resolve("loose-source").toString();
			loose.data.setImagesPath("images");
			project.add(new SkeletonProject.LoadedSkeleton("Loose", loose), false);
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			NativeProjectStore.SaveResult result = NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), new CancellationToken());
			for (int index = 0; index < 2; index++) {
				Path resources = result.skeletons().get(index).runtime().getParent();
				assertEquals("hero.atlas", result.skeletons().get(index).atlas(), "external atlas association becomes portable");
				assertBytes(new byte[] {(byte)(index + 1), 9}, Files.readAllBytes(resources.resolve("角色.png")),
					"same-named Unicode pages keep per-skeleton contents");
				assertBytes(new byte[] {(byte)(index + 5)}, Files.readAllBytes(resources.resolve("audio/cue.wav")),
					"same-named audio remains isolated");
			}
			assertBytes(new byte[] {31, 32}, Files.readAllBytes(result.skeletons().get(2).runtime().getParent()
				.resolve("images/face/eye.PNG")), "nested loose PNG retains its image-relative path");
			assertNoTemporaryFiles(directory);
		}
	}

	private static void keepsOldProjectAfterFailedOrCancelledSave(Path directory) throws Exception {
		Files.createDirectories(directory);
		try (SkeletonProject project = simpleProject(directory)) {
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			CancellationToken cancelled = new CancellationToken();
			cancelled.cancel();
			assertFailure(CancellationException.class, () -> NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), cancelled), "pre-cancellation prevents a new project");
			assertTrue(!Files.exists(target), "pre-cancellation leaves target absent");
			NativeProjectStore.SaveResult original = NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), new CancellationToken());
			Path marker = directory.resolve(NativeProjectStore.GENERATION_DIRECTORY).resolve("user-notes.txt");
			Files.writeString(marker, "keep user files");
			Map<Path, byte[]> originalFiles = treeBytes(directory);
			List<DocumentState> before = documentStates(project);
			int[] commits = {0};
			CancellationToken failCommit = new CancellationToken() {
				@Override public void commit(CommitAction action) throws IOException {
					commits[0]++;
					throw new IOException("injected final publication failure");
				}
			};
			assertFailure(IOException.class, () -> NativeProjectStore.save(project, true, target, original.stamp(), failCommit),
				"failed final publication reports failure");
			assertEquals(1, commits[0], "outer token commit is reserved for publishing the root manifest");
			assertEquals(before, documentStates(project), "failed save preserves all document state");
			assertExactTree(originalFiles, directory, "failed save removes only its own generation and temporary files");
			CancellationToken cancelCommit = new CancellationToken() {
				@Override public synchronized void commit(CommitAction action) throws IOException {
					cancel();
					super.commit(action);
				}
			};
			assertFailure(CancellationException.class, () -> NativeProjectStore.save(project, true, target, original.stamp(), cancelCommit),
				"cancellation at the final boundary prevents publication");
			assertExactTree(originalFiles, directory, "cancelled save preserves all existing bytes and membership");
			assertRoundTrip(target, List.of("Hero"), new float[] {7f}, 0, false);

			CancellationToken mutateDocument = new CancellationToken() {
				@Override public synchronized void commit(CommitAction action) throws IOException {
					project.documents().get(0).data.getBones().first().getSetupPose().setX(99f);
					project.documents().get(0).markDirty();
					super.commit(action);
				}
			};
			assertFailure(IOException.class, () -> NativeProjectStore.save(project, true, target, original.stamp(), mutateDocument),
				"document edits made during staging invalidate publication");
			assertNear(99f, project.documents().get(0).data.getBones().first().getSetupPose().getX(), "new in-memory edits remain intact");
			assertTrue(project.isDirty(), "new in-memory edits remain dirty");
			assertExactTree(originalFiles, directory, "concurrent document edits do not publish mixed project data");
			CancellationToken renameMember = new CancellationToken() {
				@Override public synchronized void commit(CommitAction action) throws IOException {
					project.rename(0, "Changed during save");
					super.commit(action);
				}
			};
			assertFailure(IOException.class, () -> NativeProjectStore.save(project, true, target, original.stamp(), renameMember),
				"member renames made during staging invalidate publication");
			assertEquals("Changed during save", project.nameAt(0), "concurrent member rename is retained");
			assertExactTree(originalFiles, directory, "member change failure preserves the last complete saved project");
		}
	}

	private static void rejectsExternalChanges(Path directory) throws Exception {
		Files.createDirectories(directory);
		try (SkeletonProject project = simpleProject(directory)) {
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			NativeProjectStore.SaveResult saved = NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), new CancellationToken());
			Path runtime = saved.skeletons().get(0).runtime();
			for (Path changed : List.of(target, runtime, ProjectStateStore.sidecarPath(runtime))) {
				byte[] old = Files.readAllBytes(changed);
				Files.writeString(changed, "\n ", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
				byte[] external = Files.readAllBytes(changed);
				assertFailure(IOException.class, () -> NativeProjectStore.save(project, true, target, saved.stamp(), new CancellationToken()),
					"old stamp detects external edits to " + changed.getFileName());
				assertBytes(external, Files.readAllBytes(changed), "conflict handling preserves the external edit");
				Files.write(changed, old);
			}
			assertEquals(saved.stamp(), NativeProjectStore.inspect(target), "restoring bytes restores the baseline stamp");
			byte[] manifestBefore = Files.readAllBytes(target);
			CancellationToken editBeforeCommit = new CancellationToken() {
				@Override public synchronized void commit(CommitAction action) throws IOException {
					Files.writeString(runtime, "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
					super.commit(action);
				}
			};
			assertFailure(IOException.class, () -> NativeProjectStore.save(project, true, target, saved.stamp(), editBeforeCommit),
				"external runtime edit during staging is checked again immediately before publication");
			assertBytes(manifestBefore, Files.readAllBytes(target), "late conflict preserves the previous manifest");
			assertRoundTrip(target, List.of("Hero"), new float[] {7f}, 0, false);
			assertNoTemporaryFiles(directory);
		}
	}

	private static void detectsExternalResourceChanges(Path directory) throws Exception {
		Files.createDirectories(directory);
		try (SkeletonProject project = resourceProject(directory)) {
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			NativeProjectStore.SaveResult saved = NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), new CancellationToken());
			Path first = saved.skeletons().get(0).runtime().getParent(), second = saved.skeletons().get(1).runtime().getParent();
			Path loose = saved.skeletons().get(2).runtime().getParent().resolve("images");
			byte[] manifest = Files.readAllBytes(target);
			List<DocumentState> documents = documentStates(project);
			for (Path file : List.of(first.resolve("sprite.atlas"), first.resolve("角色.png"), second.resolve("角色.png"),
				first.resolve("audio/cue.wav"), second.resolve("audio/cue.wav"), loose.resolve("nested/eye.jpg"))) {
				byte[] before = Files.readAllBytes(file);
				var timestamp = Files.getLastModifiedTime(file);
				byte[] external = before.clone();
				// Same size and timestamp: conflict detection must use content, not only metadata.
				if (file.getFileName().toString().endsWith(".atlas"))
					external = new String(before, StandardCharsets.UTF_8).replace("Nearest", "Linear ").getBytes(StandardCharsets.UTF_8);
				else external[0] ^= 0x23;
				Files.write(file, external);
				Files.setLastModifiedTime(file, timestamp);
				assertTrue(!saved.stamp().equals(NativeProjectStore.inspect(target)), "resource contents participate in project fingerprint: " + file);
				assertFailure(IOException.class, () -> NativeProjectStore.save(project, true, target, saved.stamp(), new CancellationToken()),
					"resource conflict must prevent replacing the complete project");
				assertBytes(external, Files.readAllBytes(file), "conflict must preserve externally edited resource");
				assertBytes(manifest, Files.readAllBytes(target), "resource conflict must preserve current manifest");
				Files.write(file, before);
				Files.setLastModifiedTime(file, timestamp);
				assertEquals(saved.stamp(), NativeProjectStore.inspect(target), "restored resource contents restore baseline");
			}
			Path missing = first.resolve("角色.png");
			byte[] page = Files.readAllBytes(missing);
			Files.delete(missing);
			try {
				assertFailure(IOException.class, () -> NativeProjectStore.save(project, false, target, saved.stamp(), new CancellationToken()),
					"missing referenced page must fail explicitly");
				assertBytes(manifest, Files.readAllBytes(target), "missing resource cannot replace old manifest");
			} finally { Files.write(missing, page); }
			Path added = loose.resolve("new-image.PNG");
			Files.write(added, new byte[] {17});
			assertFailure(IOException.class, () -> NativeProjectStore.save(project, false, target, saved.stamp(), new CancellationToken()),
				"new image in a referenced loose directory changes its resource set");
			Files.delete(added);
			Files.writeString(first.resolve("user-notes.txt"), "unrelated notes");
			Files.write(first.resolve("not-in-atlas.png"), new byte[] {19});
			Files.write(first.resolve("audio/not-referenced.wav"), new byte[] {21});
			assertEquals(saved.stamp(), NativeProjectStore.inspect(target), "unreferenced atlas-directory files do not cause a conflict");
			assertEquals(documents, documentStates(project), "all resource conflicts preserve document dirty state and associations");
			assertNoTemporaryFiles(directory);
		}
	}

	private static void rejectsResourcesChangedDuringCopy(Path directory) throws Exception {
		Files.createDirectories(directory);
		try (SkeletonProject project = resourceProject(directory)) {
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			Path page = directory.resolve("atlas-a/角色.png");
			byte[] original = Files.readAllBytes(page);
			CancellationToken replaceAndRestore = new CancellationToken() {
				boolean replaced, restored;
				@Override public void checkCancelled() {
					super.checkCancelled();
					try {
						if (!replaced && inMethod("com.openspine.io.ProjectPackager", "writeFile")) {
							replaced = true;
							Files.write(page, new byte[] {99, 98, 97});
						} else if (replaced && !restored && inMethod("com.openspine.io.NativeProjectStore", "extractOwnArchive")) {
							restored = true;
							Files.write(page, original);
						}
					} catch (IOException error) { throw new java.io.UncheckedIOException(error); }
				}
			};
			assertFailure(IOException.class, () -> NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), replaceAndRestore), "temporarily changed resources cannot publish different copied bytes");
			assertBytes(original, Files.readAllBytes(page), "the changed source was restored before final validation");
			assertTrue(!Files.exists(target), "first-save copy conflict leaves manifest absent");
			assertNoGenerations(directory);
			CancellationToken lateSourceChange = new CancellationToken() {
				@Override public synchronized void commit(CommitAction action) throws IOException {
					Files.write(page, new byte[] {1, 3, 5, 7});
					super.commit(action);
				}
			};
			assertFailure(IOException.class, () -> NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), lateSourceChange), "source rewrite after hash is caught by commit metadata check");
			assertBytes(new byte[] {1, 3, 5, 7}, Files.readAllBytes(page), "late source change is preserved");
			assertTrue(!Files.exists(target), "late source change cannot publish a manifest");
			assertNoGenerations(directory);
			Files.write(page, original);
			CancellationToken lateStagedChange = new CancellationToken() {
				@Override public synchronized void commit(CommitAction action) throws IOException {
					try (var directories = Files.list(directory.resolve(NativeProjectStore.GENERATION_DIRECTORY))) {
						Path staged = directories.filter(Files::isDirectory).findFirst().orElseThrow();
						Files.write(staged.resolve("skeletons/001/角色.png"), new byte[] {9, 8, 7, 6});
					}
					super.commit(action);
				}
			};
			assertFailure(IOException.class, () -> NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), lateStagedChange), "staged generation must remain unchanged until commit");
			assertTrue(!Files.exists(target), "tampered stage cannot publish a manifest");
			assertNoGenerations(directory);
			Path atlas = directory.resolve("atlas-a/sprite.atlas");
			String atlasText = Files.readString(atlas);
			Files.writeString(atlas, atlasText + "\n" + atlasText.replace("角色.png", "角色.PNG"));
			assertFailure(IllegalArgumentException.class, () -> NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), new CancellationToken()), "case-alias atlas pages fail before publication");
			assertTrue(!Files.exists(target), "resource aliases cannot publish a manifest");
			Files.writeString(atlas, atlasText);
			Path uppercaseAudio = directory.resolve("atlas-a/audio/CUE.wav");
			if (!Files.exists(uppercaseAudio)) Files.copy(directory.resolve("atlas-a/audio/cue.wav"), uppercaseAudio);
			EventData alias = new EventData("alias");
			alias.setAudioPath("CUE.wav");
			alias.getSetupPose().setString("");
			project.documents().get(0).data.getEvents().add(alias);
			assertFailure(IllegalArgumentException.class, () -> NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), new CancellationToken()), "case-alias event paths cannot silently use one differently cased package entry");
			assertTrue(!Files.exists(target), "audio aliases cannot publish a manifest");
			assertNoTemporaryFiles(directory);
		}
	}

	private static void cancelsResourceHashWithoutBlocking(Path directory) throws Exception {
		Files.createDirectories(directory);
		try (SkeletonProject project = resourceProject(directory)) {
			Path audio = directory.resolve("atlas-a/audio/cue.wav");
			try (var output = Files.newOutputStream(audio)) {
				byte[] chunk = new byte[65536];
				Arrays.fill(chunk, (byte)37);
				for (int index = 0; index < 128; index++) output.write(chunk);
			}
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			NativeProjectStore.SaveResult saved = NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), new CancellationToken());
			Map<Path, byte[]> original = treeBytes(directory);
			CountDownLatch hashing = new CountDownLatch(1), releaseHash = new CountDownLatch(1), cancelled = new CountDownLatch(1);
			AtomicBoolean gated = new AtomicBoolean();
			AtomicReference<Throwable> workerResult = new AtomicReference<>();
			CancellationToken token = new CancellationToken() {
				int checks;
				@Override public void checkCancelled() {
					if (inMethod("com.openspine.io.NativeProjectStore$ResourceGuard", "requireUnchanged")
						&& inMethod("com.openspine.io.NativeProjectStore", "fileHash") && ++checks == 4 && gated.compareAndSet(false, true)) {
						hashing.countDown();
						try {
							if (!releaseHash.await(5, TimeUnit.SECONDS)) throw new AssertionError("Hash gate timed out");
						} catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
					}
					super.checkCancelled();
				}
			};
			Thread worker = new Thread(() -> {
				try { NativeProjectStore.save(project, true, target, saved.stamp(), token); }
				catch (Throwable failure) { workerResult.set(failure); }
			}, "openspine-resource-cancel-test");
			Thread cancellation = new Thread(() -> { token.cancel(); cancelled.countDown(); }, "openspine-resource-cancel-request");
			worker.start();
			boolean responsive = false;
			try {
				assertTrue(hashing.await(5, TimeUnit.SECONDS), "worker must reach the final streaming resource hash");
				cancellation.start();
				responsive = cancelled.await(1, TimeUnit.SECONDS);
			} finally {
				releaseHash.countDown();
				worker.join(5000);
				cancellation.join(5000);
			}
			assertTrue(responsive, "cancel must return promptly while the final resource hash is still running");
			assertTrue(!worker.isAlive() && workerResult.get() instanceof CancellationException, "worker must stop without publishing after cancellation");
			assertExactTree(original, directory, "resource-hash cancellation preserves old manifest/resources and cleans its stage");
		}
	}

	private static void doesNotCollectNativeGenerationsAsLooseImages(Path directory) throws Exception {
		Files.createDirectories(directory);
		Files.write(directory.resolve("body.png"), new byte[] {3, 4});
		try (SkeletonProject project = simpleProject(directory)) {
			SkeletonDocument document = project.documents().get(0);
			document.data.setImagesPath("./");
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			NativeProjectStore.SaveResult first = NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), new CancellationToken());
			Map<Path, byte[]> oldGeneration = treeBytes(ProjectPackageManifest.read(target).editorState().getParent());
			// Retain the original root image association, as when Save As uses external source resources.
			NativeProjectStore.SaveResult second = NativeProjectStore.save(project, true, target, first.stamp(), new CancellationToken());
			Path generation = ProjectPackageManifest.read(target).editorState().getParent();
			try (var paths = Files.walk(generation)) {
				List<Path> images = paths.filter(path -> path.getFileName().toString().endsWith(".png")).toList();
				assertEquals(1, images.size(), "root-relative loose images collect only one source image, excluding all old generations");
				assertBytes(new byte[] {3, 4}, Files.readAllBytes(images.get(0)), "root loose image remains intact");
			}
			assertTreeUnchanged(oldGeneration, "saving root-relative images retains every old generation byte");
			assertEquals(second.stamp(), NativeProjectStore.inspect(target), "second root-relative save yields a stable resource fingerprint");
			assertNoTemporaryFiles(directory);
		}
	}

	private static SkeletonProject resourceProject(Path directory) throws Exception {
		SkeletonProject project = new SkeletonProject(new SkeletonDocument());
		for (int index = 0; index < 2; index++) {
			Path resources = Files.createDirectories(directory.resolve(index == 0 ? "atlas-a" : "atlas-b"));
			Files.write(resources.resolve("角色.png"), new byte[] {(byte)(index + 1), 9, 11});
			Files.writeString(resources.resolve("sprite.atlas"), "角色.png\nsize: 1, 1\nformat: RGBA8888\n"
				+ "filter: Nearest, Nearest\nrepeat: none\n", StandardCharsets.UTF_8);
			Files.createDirectories(resources.resolve("audio"));
			Files.write(resources.resolve("audio/cue.wav"), new byte[] {(byte)(index + 5), 17});
			SkeletonDocument document = document(resources.resolve(index == 0 ? "hero.json" : "hero.skel"), index);
			document.resourceDirectory = resources.toString();
			document.atlasFileName = "sprite.atlas";
			document.data.setAudioPath("audio");
			EventData event = new EventData("cue");
			event.setAudioPath("cue.wav");
			event.getSetupPose().setString("");
			document.data.getEvents().add(event);
			project.add(new SkeletonProject.LoadedSkeleton("Atlas " + index, document), false);
		}
		Path loose = Files.createDirectories(directory.resolve("loose/images/nested"));
		Files.write(loose.resolve("eye.jpg"), new byte[] {23, 25});
		SkeletonDocument document = document(directory.resolve("loose/loose.json"), 3f);
		document.data.setImagesPath("images");
		project.add(new SkeletonProject.LoadedSkeleton("Loose", document), false);
		return project;
	}

	private static boolean inMethod(String className, String method) {
		return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> className.equals(frame.getClassName()) && method.equals(frame.getMethodName())));
	}

	private static void assertNoGenerations(Path directory) throws Exception {
		Path storage = directory.resolve(NativeProjectStore.GENERATION_DIRECTORY);
		if (!Files.exists(storage)) return;
		try (var paths = Files.list(storage)) { assertTrue(paths.findAny().isEmpty(), "failed initial save must remove its generation"); }
	}

	private static void discardsUnsavedAuthoringChangesInRootSidecars(Path directory) throws Exception {
		Files.createDirectories(directory);
		try (SkeletonProject project = simpleProject(directory)) {
			SkeletonDocument source = project.documents().get(0);
			source.data.setX(10f);
			source.data.setY(20f);
			source.data.setWidth(100f);
			source.data.setHeight(200f);
			source.data.setReferenceScale(50f);
			source.data.setFps(24f);
			source.setAnimationMixSettings(0.25f, List.of());
			source.setBoneNameVisible(source.data.getBones().first(), true);
			source.setAxisSeparationPreferred("walk", "root", "SCALE", true);
			source.setAxisCombineState(axisState(2f));
			Path savedPsd = directory.resolve("saved-layout.psd");
			Files.write(savedPsd, new byte[] {1, 2, 3});
			source.setPsdSource(new SkeletonDocument.PsdSource(savedPsd.toString(), false, 64, 32, List.of(
				new SkeletonDocument.PsdLayerMapping(0, "Saved", "saved", "default", "slot", "saved", true))));
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			NativeProjectStore.SaveResult saved = NativeProjectStore.save(project, true, target,
				NativeProjectStore.inspect(target), new CancellationToken());
			source.data.setX(71f);
			source.data.setY(72f);
			source.data.setWidth(321f);
			source.data.setHeight(654f);
			source.data.setReferenceScale(75f);
			source.data.setFps(60f);
			source.data.setImagesPath("unsaved-images");
			source.data.setAudioPath("unsaved-audio");
			source.setAnimationMixSettings(1.5f, List.of());
			source.setBoneNameVisible(source.data.getBones().first(), false);
			source.setBoneSelectable(source.data.getBones().first(), false);
			source.setAxisSeparationPreferred("walk", "root", "SCALE", false);
			source.setAxisCombineState(axisState(99f));
			Path unsavedPsd = directory.resolve("unsaved-layout.psd");
			Files.write(unsavedPsd, new byte[] {9, 9, 9});
			source.setPsdSource(new SkeletonDocument.PsdSource(unsavedPsd.toString(), true, 128, 64, List.of(
				new SkeletonDocument.PsdLayerMapping(1, "Unsaved", "unsaved", "default", "slot", "unsaved", true))));
			source.markDirty();
			source.seek(1.4f);
			source.setPlaying(false);
			source.playbackSpeed = 2f;
			source.setTreeViewState(new SkeletonDocument.TreeViewState("root", 5f, 6f,
				java.util.Set.of("bone:root"), java.util.Set.of("bone:root")));
			for (boolean legacyFullSidecar : List.of(true, false)) {
				if (legacyFullSidecar) ProjectStateStore.write(target, project, false);
				else {
					ProjectStateStore.writeViewState(target, project, false);
					var root = new com.badlogic.gdx.utils.JsonReader().parse(Files.readString(ProjectStateStore.sidecarPath(target)));
					assertTrue(root.getBoolean("viewOnly"), "new root sidecars identify themselves as view-only");
					var skeleton = root.get("skeletons").get(0);
					for (String field : List.of("x", "y", "width", "height", "referenceScale", "fps", "imagesPath", "audioPath",
						"defaultAnimationMix", "animationMixes", "psdSource", "bones", "axisSeparation", "axisCombines", "meshWeightLocks"))
						assertTrue(skeleton.get(field) == null, "automatic root sidecars omit authoring field " + field);
				}
				assertEquals(saved.stamp(), NativeProjectStore.inspect(target), "view sidecar writes do not change native save baseline");
				try (SkeletonProject reopened = open(target)) {
					SkeletonDocument loaded = reopened.documents().get(0);
					String lockKey = SkeletonDocument.meshWeightStateKey("default", "slot", "mesh");
					loaded.setMeshWeightLocks(lockKey, List.of(new SkeletonDocument.MeshWeightLock(0, 0)));
					var locksBefore = loaded.meshWeightLockStates();
					ProjectStateStore.LoadOutcome outcome = legacyFullSidecar
						? ProjectStateStore.loadViewState(target, reopened, true)
						: ProjectStateStore.load(target, reopened, true);
					assertEquals(ProjectStateStore.LoadResult.RESTORED, outcome.result(), "root view state restores after discard and reopen");
					assertTrue(!outcome.showAllSkeletons(), "view state restores show-all preference");
					assertNear(10f, loaded.data.getX(), "discarded project x does not resurrect");
					assertNear(20f, loaded.data.getY(), "discarded project y does not resurrect");
					assertNear(100f, loaded.data.getWidth(), "discarded width does not resurrect as clean data");
					assertNear(200f, loaded.data.getHeight(), "discarded height does not resurrect");
					assertNear(50f, loaded.data.getReferenceScale(), "discarded reference scale does not resurrect");
					assertNear(24f, loaded.data.getFps(), "discarded frame rate does not resurrect");
					assertTrue(!"unsaved-images".equals(loaded.data.getImagesPath()) && !"unsaved-audio".equals(loaded.data.getAudioPath()),
						"discarded resource paths do not resurrect");
					assertNear(0.25f, loaded.defaultAnimationMix(), "native generation keeps the saved animation mix");
					assertTrue(loaded.isBoneNameVisible(loaded.data.getBones().first()) && loaded.isBoneSelectable(loaded.data.getBones().first()),
						"native generation keeps saved bone editor flags");
					assertTrue(loaded.isAxisSeparationPreferred("walk", "root", "SCALE"), "view state cannot change saved axis representation preferences");
					assertNear(2f, loaded.axisCombineState("walk", "root", "SCALE").xFrames()[3], "view state cannot overwrite reversible authoring data");
					assertTrue(loaded.psdSource() != null && Path.of(loaded.psdSource().sourcePath())
						.getFileName().toString().equals("saved-layout.psd") && !loaded.psdSource().ignoreHiddenLayers(),
						"view state cannot overwrite the committed managed PSD source");
					assertEquals(locksBefore, loaded.meshWeightLockStates(), "view state preserves existing authoring weight locks");
					assertNear(1.4f, loaded.getCurrentTime(), "view-only restore still recovers the latest playhead");
					assertNear(2f, loaded.playbackSpeed, "view-only restore still recovers playback speed");
					assertEquals("root", loaded.treeViewState().filter(), "view-only restore still recovers hierarchy view");
					assertTrue(!reopened.isDirty(), "discard and reopen returns the committed clean authoring state");
				}
			}
		}
		try (SkeletonProject project = simpleProject(directory)) {
			project.add(new SkeletonProject.LoadedSkeleton("Second", document(directory.resolve("second.json"), 2f)), true);
			Path target = directory.resolve("membership.openspine-project.json");
			NativeProjectStore.save(project, true, target, NativeProjectStore.inspect(target), new CancellationToken());
			project.move(1, 0);
			ProjectStateStore.writeViewState(target, project, true);
			try (SkeletonProject reopened = open(target)) {
				ProjectStateStore.loadViewState(target, reopened, true);
				assertEquals("Hero", reopened.nameAt(0), "discarded member reorder is not replayed");
				assertEquals("Second", reopened.activeName(), "active view resolves by saved name after discarded member reorder");
			}
		}
	}

	private static SkeletonDocument.AxisCombineState axisState(float x) {
		return new SkeletonDocument.AxisCombineState("walk", "root", "SCALE", true,
			new float[] {0f, 1f, 1f, x}, new float[] {0f, 0f},
			new float[] {0f, 1f, 1f, 1f}, new float[] {0f, 0f},
			new float[] {0f, 1f, 1f, 1f, x, 1f}, new float[] {0f, 0f});
	}

	private static void rejectsUnreopenableResourceLayouts(Path directory) throws Exception {
		Files.createDirectories(directory.resolve("images"));
		Files.write(directory.resolve("images/body.png"), new byte[] {1, 2});
		try (SkeletonProject project = simpleProject(directory)) {
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			NativeProjectStore.SaveResult saved = NativeProjectStore.save(project, false, target,
				NativeProjectStore.inspect(target), new CancellationToken());
			Map<Path, byte[]> before = treeBytes(directory);
			SkeletonDocument document = project.documents().get(0);
			document.data.setImagesPath("images");
			document.savePath = directory.resolve("source.skel").toString();
			assertFailure(IllegalArgumentException.class, () -> NativeProjectStore.save(project, false, target, saved.stamp(),
				new CancellationToken()), "binary skeleton without an atlas is rejected before replacing a usable project");
			assertExactTree(before, directory, "unsupported binary loose images leave every saved file intact");
			document.savePath = directory.resolve("source.json").toString();
			document.data.setImagesPath(null);
			document.atlas.getRegions().add(null);
			try {
				assertFailure(IllegalArgumentException.class, () -> NativeProjectStore.save(project, false, target, saved.stamp(),
					new CancellationToken()), "memory-only atlas reports missing original resources");
				assertExactTree(before, directory, "unsupported memory atlas leaves every saved file intact");
			} finally { document.atlas.getRegions().clear(); }
		}
	}

	private static void rejectsUnsafeTargetsWithoutChangingThem(Path directory) throws Exception {
		Files.createDirectories(directory);
		try (SkeletonProject project = simpleProject(directory)) {
			for (String name : List.of("hero.spine", "hero.json", "openspine-project.json.bak")) {
				Path invalid = directory.resolve(name);
				Files.writeString(invalid, "user file");
				assertFailure(IllegalArgumentException.class, () -> NativeProjectStore.inspect(invalid), "reject unsupported target extension");
				assertEquals("user file", Files.readString(invalid), "unsupported target remains untouched");
			}
			Path target = directory.resolve(ProjectPackageManifest.FILE_NAME);
			Files.writeString(target, "{\"bones\":[]}");
			assertFailure(IOException.class, () -> NativeProjectStore.inspect(target), "runtime JSON with a manifest filename is not a native project");
			assertEquals("{\"bones\":[]}", Files.readString(target), "invalid manifest remains untouched");
			Path valid = directory.resolve("safe.openspine-project.json");
			Path storage = directory.resolve(NativeProjectStore.GENERATION_DIRECTORY);
			Files.writeString(storage, "existing user file");
			assertFailure(IOException.class, () -> NativeProjectStore.save(project, false, valid,
				NativeProjectStore.inspect(valid), new CancellationToken()), "existing file cannot become the generation directory");
			assertEquals("existing user file", Files.readString(storage), "failed staging preserves conflicting user file");
			assertTrue(!Files.exists(valid), "failed staging creates no manifest");
			assertNoTemporaryFiles(directory);
		}
	}

	private static void preparesCancellableOpen(Path directory) throws Exception {
		Files.createDirectories(directory);
		Path manifest = directory.resolve("prepared.openspine-project.json");
		NativeProjectStore.SaveResult saved;
		try (SkeletonProject project = simpleProject(directory)) {
			Path audioDirectory = Files.createDirectories(directory.resolve("audio"));
			Files.write(audioDirectory.resolve("large.wav"), new byte[8 * 1024 * 1024]);
			SkeletonDocument document = project.documents().get(0);
			document.resourceDirectory = directory.toString();
			document.data.setAudioPath("audio");
			EventData event = new EventData("cue");
			event.setAudioPath("large.wav");
			event.getSetupPose().setString("");
			document.data.getEvents().add(event);
			saved = NativeProjectStore.save(project, false, manifest, NativeProjectStore.inspect(manifest), new CancellationToken());
		}
		Map<Path, byte[]> original = treeBytes(directory);
		AtomicReference<ProjectPackageManifest.PreparedPackage> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread worker = new Thread(() -> {
			try { result.set(ProjectPackageManifest.prepare(manifest, new CancellationToken())); }
			catch (Throwable error) { failure.set(error); }
		}, "native-open-preparation-test");
		worker.start();
		worker.join(5000);
		assertTrue(!worker.isAlive() && failure.get() == null, "preparation completes without a GL context: " + failure.get());
		ProjectPackageManifest.PreparedPackage prepared = result.get();
		assertEquals(saved.stamp(), prepared.stamp(), "prepared open retains the complete content fingerprint");
		CancellationToken metadataOnly = new CancellationToken() {
			@Override public void checkCancelled() {
				assertTrue(!StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
					frame.getClassName().equals(NativeProjectStore.class.getName()) && frame.getMethodName().equals("fileHash"))),
					"render-thread acceptance must never hash asset contents");
				super.checkCancelled();
			}
		};
		ProjectPackageManifest.LoadedPackage loaded = ProjectPackageManifest.load(prepared, metadataOnly);
		try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
			project.replaceAll(loaded.skeletons());
			assertNear(7f, project.documents().get(0).data.getBones().first().getSetupPose().getX(), "prepared load retains skeleton data");
		}
		CancellationToken cancelled = new CancellationToken();
		cancelled.cancel();
		assertFailure(CancellationException.class, () -> ProjectPackageManifest.load(prepared, cancelled),
			"cancel after preparation prevents the first candidate load");
		CancellationToken duringLoad = new CancellationToken() {
			private int checks;
			@Override public void checkCancelled() {
				if (StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
					frame.getClassName().equals(ProjectPackageManifest.class.getName()) && frame.getMethodName().equals("loadManifest")))
					&& ++checks == 2) cancel();
				super.checkCancelled();
			}
		};
		assertFailure(CancellationException.class, () -> ProjectPackageManifest.load(prepared, duringLoad),
			"cancel after candidate loading is preserved and cleans unadopted documents");
		Path runtime = saved.skeletons().get(0).runtime();
		byte[] runtimeBytes = Files.readAllBytes(runtime);
		Files.writeString(runtime, "\n", StandardOpenOption.APPEND);
		assertFailure(IOException.class, () -> ProjectPackageManifest.load(prepared, new CancellationToken()),
			"runtime modification between preparation and acceptance is rejected before GL loading");
		Files.write(runtime, runtimeBytes);
		ProjectPackageManifest.PreparedPackage finalPrepared = ProjectPackageManifest.prepare(manifest, new CancellationToken());
		Path audio = runtime.getParent().resolve("audio/large.wav");
		CancellationToken changedAfterLoad = new CancellationToken() {
			private boolean changed;
			@Override public void checkCancelled() {
				if (!changed && StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
					frame.getClassName().equals(ProjectPackageManifest.class.getName()) && frame.getMethodName().equals("loadManifest")))) {
					// The final snapshot check runs only after candidate documents have been loaded.
					boolean finalCheck = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
						frame.getClassName().equals(NativeProjectStore.class.getName()) && frame.getMethodName().equals("requireObserved")));
					if (finalCheck) {
						changed = true;
						try { Files.write(audio, new byte[] {1}, StandardOpenOption.APPEND); }
						catch (IOException error) { throw new AssertionError(error); }
					}
				}
				super.checkCancelled();
			}
		};
		assertFailure(IOException.class, () -> ProjectPackageManifest.load(finalPrepared, changedAfterLoad),
			"resource modifications during candidate loading reject the candidate after GL loading");
		Files.write(audio, original.get(audio));
		assertExactTree(original, directory, "preparation and rejected loads do not create files or alter the saved project");
		CountDownLatch hashing = new CountDownLatch(1), release = new CountDownLatch(1);
		CancellationToken gated = new CancellationToken() {
			@Override public void checkCancelled() {
				if (StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
					frame.getClassName().equals(NativeProjectStore.class.getName()) && frame.getMethodName().equals("fileHash")))) {
					hashing.countDown();
					try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("preparation hash gate timeout"); }
					catch (InterruptedException error) { Thread.currentThread().interrupt(); }
				}
				super.checkCancelled();
			}
		};
		failure.set(null);
		Thread cancelledWorker = new Thread(() -> {
			try { ProjectPackageManifest.prepare(manifest, gated); }
			catch (Throwable error) { failure.set(error); }
		}, "native-open-cancellation-test");
		cancelledWorker.start();
		try {
			assertTrue(hashing.await(5, TimeUnit.SECONDS), "preparation reaches cancellable content hashing");
			Thread canceller = new Thread(gated::cancel, "native-open-canceller-test");
			canceller.start();
			canceller.join(1000);
			assertTrue(!canceller.isAlive(), "cancel returns while the preparation hash is blocked");
		} finally {
			release.countDown();
			cancelledWorker.join(5000);
		}
		assertTrue(!cancelledWorker.isAlive() && failure.get() instanceof CancellationException,
			"cancelled preparation exits without GL allocation or temporary files: " + failure.get());
		assertExactTree(original, directory, "cancelled preparation leaves every source byte unchanged");
	}

	private static SkeletonProject simpleProject(Path directory) {
		SkeletonProject project = new SkeletonProject(new SkeletonDocument());
		project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("Hero", document(directory.resolve("source.json"), 7f))));
		return project;
	}

	private static SkeletonDocument document(Path source, float x) {
		SkeletonData data = new SkeletonData();
		data.setName("hero");
		data.setVersion("4.3.0");
		data.setFps(30f);
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setX(x);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root);
		Animation.RotateTimeline rotation = new Animation.RotateTimeline(2, 0, 0);
		rotation.setFrame(0, 0f, 0f);
		rotation.setFrame(1, 2f, 20f);
		Animation animation = new Animation("walk");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(rotation);
		animation.setTimelines(timelines, new com.badlogic.gdx.utils.IntArray());
		animation.setDuration(2f);
		data.getAnimations().add(animation);
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(new TextureAtlas(), data, new Skeleton(data), source.toString(), "", source.toString());
		document.animationState = document.newAnimationState();
		document.setAnimation("walk");
		document.setPlaying(false);
		return document;
	}

	private static SkeletonProject open(Path target) throws Exception {
		ProjectPackageManifest.LoadedPackage loaded = ProjectPackageManifest.load(target);
		SkeletonProject result = new SkeletonProject(new SkeletonDocument());
		result.replaceAll(loaded.skeletons());
		result.switchTo(loaded.activeIndex());
		return result;
	}

	private static void assertRoundTrip(Path target, List<String> names, float[] positions, int active, boolean showAll)
		throws Exception {
		ProjectPackageManifest.Manifest manifest = ProjectPackageManifest.read(target);
		assertEquals(showAll, manifest.showAll(), "manifest preserves visibility mode");
		assertEquals(active, manifest.activeIndex(), "manifest preserves active member");
		ProjectPackageManifest.LoadedPackage loaded = ProjectPackageManifest.load(target);
		try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
			project.replaceAll(loaded.skeletons());
			project.switchTo(loaded.activeIndex());
			assertEquals(names.size(), project.size(), "reopen exactly matches current membership");
			assertEquals(active, project.activeIndex(), "reopen restores active member");
			assertEquals(showAll, loaded.showAll(), "reopen restores visibility mode");
			for (int index = 0; index < names.size(); index++) {
				assertEquals(names.get(index), project.nameAt(index), "reopen restores names and order");
				assertNear(positions[index], project.documents().get(index).data.getBones().first().getSetupPose().getX(),
					"reopen contains the current runtime setup values");
			}
		}
	}

	private record DocumentState(SkeletonDocument document, boolean dirty, long revision, String filePath,
		String savePath, String resourceDirectory, String atlasFileName) {}
	private static List<DocumentState> documentStates(SkeletonProject project) {
		return project.documents().stream().map(document -> new DocumentState(document, document.dirty, document.revision,
			document.filePath, document.savePath, document.resourceDirectory, document.atlasFileName)).toList();
	}

	private static Map<Path, byte[]> treeBytes(Path directory) throws IOException {
		Map<Path, byte[]> result = new LinkedHashMap<>();
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) result.put(path, Files.readAllBytes(path));
		}
		return result;
	}

	private static void assertExactTree(Map<Path, byte[]> expected, Path directory, String message) throws IOException {
		assertEquals(expected.keySet(), treeBytes(directory).keySet(), message);
		assertTreeUnchanged(expected, message);
	}

	private static void assertTreeUnchanged(Map<Path, byte[]> files, String message) throws IOException {
		for (var entry : files.entrySet()) assertBytes(entry.getValue(), Files.readAllBytes(entry.getKey()), message);
	}

	private static void assertNoTemporaryFiles(Path directory) throws IOException {
		try (var files = Files.list(directory)) {
			assertTrue(files.noneMatch(path -> {
				String name = path.getFileName().toString();
				return name.startsWith(".openspine-project-") || name.startsWith(".openspine-manifest-");
			}), "save leaves no staging archives or manifests");
		}
	}

	@FunctionalInterface private interface CheckedAction { void run() throws Exception; }
	private static void assertFailure(Class<? extends Exception> type, CheckedAction action, String message) throws Exception {
		try { action.run(); }
		catch (Exception expected) {
			if (type.isInstance(expected)) return;
			throw new AssertionError(message + ": wrong failure type", expected);
		}
		throw new AssertionError(message + ": operation unexpectedly succeeded");
	}
	private static void assertBytes(byte[] expected, byte[] actual, String message) {
		if (!Arrays.equals(expected, actual)) throw new AssertionError(message);
	}
	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f) throw new AssertionError(message + ": " + actual);
	}
	private static void assertEquals(Object expected, Object actual, String message) {
		if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}
	private static void assertTrue(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
	private NativeProjectStoreTest() {}
}
