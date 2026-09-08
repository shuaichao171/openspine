package com.openspine.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Headless regression tests for portable project ZIP creation. */
public final class ProjectPackagerTest {
	public static void main(String[] args) throws Exception {
		Path directory = Files.createTempDirectory("openspine-package-test-");
		try {
			Path source = directory.resolve("hero.json");
			Path atlas = directory.resolve("hero.atlas");
			Path page = directory.resolve("hero.png");
			Files.writeString(source, "{}", StandardCharsets.UTF_8);
			Files.writeString(atlas, "hero.png\nsize: 1, 1\nformat: RGBA8888\nfilter: Nearest, Nearest\nrepeat: none\n",
				StandardCharsets.UTF_8);
			Files.write(page, new byte[] {1, 2, 3, 4});

			SkeletonData data = new SkeletonData();
			data.setName("hero");
			data.setVersion("4.3.0");
			data.setFps(30f);
			BoneData root = new BoneData(0, "root", null);
			root.getSetupPose().setInherit(BoneData.Inherit.normal);
			data.getBones().add(root);
			SkeletonDocument document = new SkeletonDocument();
			document.replaceContent(new TextureAtlas(), data, new Skeleton(data), source.toString(),
				atlas.getFileName().toString(), source.toString());

			Path target = directory.resolve("hero-project.zip");
			ProjectPackager.PackageResult result = ProjectPackager.write(document, target);
			assertEquals(3, result.fileCount(), "skeleton, atlas, and page are packaged");
			assertEquals("hero.json", result.skeletonEntry(), "the associated skeleton name is retained");
			try (ZipFile zip = new ZipFile(target.toFile())) {
				assertTrue(zip.getEntry("hero.json") != null, "the current skeleton is exported into the package");
				assertTrue(zip.getEntry("hero.atlas") != null, "the source atlas is included");
				assertTrue(zip.getEntry("hero.png") != null, "every atlas page is included");
			}

			Path spineProject = directory.resolve("hero.spine");
			Files.writeString(spineProject, "official project", StandardCharsets.UTF_8);
			Path sidecar = ProjectStateStore.sidecarPath(spineProject);
			Files.writeString(sidecar, "{\"version\":1}", StandardCharsets.UTF_8);
			document.filePath = spineProject.toString();
			document.savePath = null;
			Path importedTarget = directory.resolve("imported-project.zip");
			ProjectPackager.PackageResult importedResult = ProjectPackager.write(document, importedTarget);
			assertEquals(5, importedResult.fileCount(),
				"runtime skeleton, original project, sidecar, atlas, and page are packaged");
			try (ZipFile zip = new ZipFile(importedTarget.toFile())) {
				assertTrue(zip.getEntry("hero.json") != null, "edited runtime data has a runtime extension");
				assertTrue(zip.getEntry("hero.spine") != null, "the untouched official project is included");
				assertTrue(zip.getEntry("hero.spine.openspine.json") != null,
					"OpenSpine editor state remains a separate companion file");
			}
			document.filePath = source.toString();
			document.savePath = source.toString();

			Path collisionPage = directory.resolve("HERO.JSON");
			if (!Files.exists(collisionPage)) Files.write(collisionPage, new byte[] {1});
			Files.writeString(atlas,
				"HERO.JSON\nsize: 1, 1\nformat: RGBA8888\nfilter: Nearest, Nearest\nrepeat: none\n",
				StandardCharsets.UTF_8);
			Path collisionTarget = directory.resolve("collision.zip");
			Files.writeString(collisionTarget, "original", StandardCharsets.UTF_8);
			assertThrows(() -> ProjectPackager.write(document, collisionTarget),
				"case-insensitive ZIP entry collisions are rejected");
			assertEquals("original", Files.readString(collisionTarget, StandardCharsets.UTF_8),
				"a failed package does not replace the destination");

			document.savePath = null;
			document.filePath = directory.resolve("hero.spine").toString();
			assertEquals("hero.json", ProjectPackager.skeletonEntryName(document),
				"an imported Spine project uses a runtime skeleton entry name");
			document.filePath = directory.resolve(".spine").toString();
			assertEquals("skeleton.json", ProjectPackager.skeletonEntryName(document),
				"an extension-only Spine project name receives a usable package entry");

			Path images = Files.createDirectories(directory.resolve("images").resolve("face"));
			Path bodyImage = directory.resolve("images").resolve("body.png");
			Path eyeImage = images.resolve("eye.PNG");
			Files.write(bodyImage, new byte[] {5, 6});
			Files.write(eyeImage, new byte[] {7, 8});
			document.filePath = source.toString();
			document.savePath = source.toString();
			document.atlasFileName = null;
			document.data.setImagesPath("./images/");
			document.atlas.getRegions().add(null);
			Path looseTarget = directory.resolve("loose-project.zip");
			ProjectPackager.PackageResult looseResult = ProjectPackager.write(document, looseTarget);
			assertEquals(3, looseResult.fileCount(), "skeleton and loose PNG files are packaged");
			try (ZipFile zip = new ZipFile(looseTarget.toFile())) {
				assertTrue(zip.getEntry("hero.json") != null, "the loose-image skeleton is included");
				assertTrue(zip.getEntry("images/body.png") != null, "top-level loose PNG is included");
				assertTrue(zip.getEntry("images/face/eye.PNG") != null, "nested loose PNG is included");
			}

			document.data.setImagesPath("../outside");
			assertThrows(() -> ProjectPackager.write(document, directory.resolve("unsafe-images.zip")),
				"loose image paths outside the project are rejected");

			document.data.setImagesPath("images");
			packagesReferencedAudio(directory);
			packagesAndReopensCompleteProject(directory);
			isolatesSameNamedResources(directory);
			packagesUnicodeAtlasPages(directory);
			reopensLatestSavedStateWithoutOverwritingChangedRuntimes(directory);
			disposesDetachedDocumentsWhenTransferFails(directory);
			matchesProjectNameCaseRules(directory);
			Path cancelledTarget = directory.resolve("cancelled-project.zip");
			Files.writeString(cancelledTarget, "existing package", StandardCharsets.UTF_8);
			CancellationToken cancelled = new CancellationToken() {
				private int checkpoints;

				@Override public void checkCancelled() {
					super.checkCancelled();
					if (++checkpoints == 7) {
						cancel();
						super.checkCancelled();
					}
				}
			};
			assertCancelled(() -> ProjectPackager.write(document, cancelledTarget, cancelled),
				"packaging cancelled after writing begins reports cancellation");
			assertEquals("existing package", Files.readString(cancelledTarget, StandardCharsets.UTF_8),
				"cancelled packaging preserves an existing destination");
			try (var paths = Files.list(directory)) {
				assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith("cancelled-project.zip")
					&& path.getFileName().toString().endsWith(".tmp")),
					"cancelled packaging leaves no temporary archive");
			}

			document.dispose();
			assertThrows(() -> ProjectPackager.safeEntryName("../escape.png"),
				"unsafe ZIP traversal paths are rejected");
			assertThrows(() -> ProjectPackager.safeEntryName("images/"),
				"directory-only ZIP entry names are rejected");
			System.out.println("ALL PROJECT PACKAGER TESTS PASSED");
		} finally {
			try (var paths = Files.walk(directory)) {
				paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (Exception ignored) {}
				});
			}
		}
	}

	private static void packagesAndReopensCompleteProject(Path directory) throws Exception {
		Path resources = Files.createDirectories(directory.resolve("complete-source"));
		Files.createDirectories(resources.resolve("audio"));
		Files.write(resources.resolve("audio/cue.wav"), new byte[] {8, 9});
		Path original = resources.resolve("hero.spine");
		Files.writeString(original, "original official project");
		Files.writeString(ProjectStateStore.sidecarPath(original), "stale state must not be reused");
		SkeletonDocument first = projectDocument(resources.resolve("hero.json"), 12f);
		first.filePath = original.toString();
		first.resourceDirectory = resources.toString();
		Path psd = resources.resolve("character-layout.psd");
		Files.write(psd, new byte[] {8, 66, 80, 83, 1});
		first.setPsdSource(new SkeletonDocument.PsdSource(psd.toString(), true, 800, 600, List.of(
			new SkeletonDocument.PsdLayerMapping(2, "Body", "body-region", "default",
				"body", "body-region", true))));
		first.data.setAudioPath("audio");
		EventData event = new EventData("cue");
		event.setAudioPath("cue.wav");
		event.getSetupPose().setString("");
		first.data.getEvents().add(event);
		SkeletonDocument second = projectDocument(resources.resolve("hero.skel"), 34f);
		SkeletonDocument active = new SkeletonDocument();
		SkeletonProject project = new SkeletonProject(active);
		project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("Body", first),
			new SkeletonProject.LoadedSkeleton("Equipment", second)));
		active.seek(0.75f);
		active.setPlaying(false);
		active.setTreeViewState(new SkeletonDocument.TreeViewState("root", 12f, 34f,
			java.util.Set.of("bone:root"), java.util.Set.of("bone:root")));
		active.setAnimationMixSettings(0.35f, List.of());
		project.switchTo(1);
		active.seek(1.25f);
		active.setPlaying(false);
		active.playbackSpeed = 1.5f;
		Path target = directory.resolve("complete-project.zip");
		try {
			ProjectPackager.ProjectPackageResult result = ProjectPackager.write(project, true, target);
			assertEquals(2, result.skeletonCount(), "every project skeleton is packaged");
			Path extracted = directory.resolve("complete-extracted");
			extract(target, extracted);
			Path manifestPath = extracted.resolve(ProjectPackageManifest.FILE_NAME);
			ProjectPackageManifest.Manifest manifest = ProjectPackageManifest.read(manifestPath);
			assertEquals(1, manifest.activeIndex(), "manifest retains the active skeleton");
			assertEquals("Body", manifest.skeletons().get(0).name(), "manifest retains project order and names");
			assertEquals("Equipment", manifest.skeletons().get(1).name(), "manifest retains the second name");
			assertEquals("original official project", Files.readString(original), "the original Spine project stays untouched");
			assertEquals("original official project", Files.readString(extracted.resolve("originals/001/hero.spine")),
				"the original Spine project is archived separately from edited runtime data");
			assertTrue(!Files.exists(extracted.resolve("originals/001/hero.spine.openspine.json")),
				"stale official-project sidecars are not used as current package state");
			assertTrue(Files.isRegularFile(extracted.resolve("skeletons/001/audio/cue.wav")),
				"audio accompanies its skeleton");
			Path packagedPsd = extracted.resolve("skeletons/001/psd/character-layout.psd");
			assertTrue(Files.isRegularFile(packagedPsd), "the managed PSD source accompanies its skeleton");
			String packagedState = Files.readString(extracted.resolve(ProjectPackageManifest.EDITOR_STATE));
			assertTrue(packagedState.contains("skeletons/001/psd/character-layout.psd")
				&& !packagedState.contains(psd.toString().replace('\\', '/')),
				"project editor state uses a portable PSD source path");
			ProjectPackageManifest.LoadedPackage loaded = ProjectPackageManifest.load(manifestPath);
			SkeletonDocument restoredActive = new SkeletonDocument();
			try (SkeletonProject restored = new SkeletonProject(restoredActive)) {
				restored.replaceAll(loaded.skeletons());
				restored.switchTo(loaded.activeIndex());
				assertEquals("Equipment", restored.activeName(), "reopening restores the active document");
				assertNear(34f, restoredActive.data.getBones().first().getSetupPose().getX(),
					"binary package contains unsaved setup changes");
				assertNear(1.25f, restoredActive.getCurrentTime(), "binary document restores its playhead");
				assertNear(1.5f, restoredActive.playbackSpeed, "binary document restores its playback settings");
				restored.switchTo(0);
				assertNear(12f, restoredActive.data.getBones().first().getSetupPose().getX(),
					"JSON package contains unsaved setup changes");
				assertNear(0.75f, restoredActive.getCurrentTime(), "inactive document restores its playhead");
				assertNear(0.35f, restoredActive.defaultAnimationMix(), "animation mixing survives packaging");
				assertEquals("root", restoredActive.treeViewState().filter(), "hierarchy view state survives packaging");
				assertTrue(restoredActive.psdSource() != null
					&& Path.of(restoredActive.psdSource().sourcePath()).equals(packagedPsd.toAbsolutePath().normalize())
					&& restoredActive.psdSource().layers().get(0).sourceIndex() == 2,
					"reopened packages resolve PSD metadata to the copied source");
			}
			for (ProjectPackageManifest.ResolvedSkeleton entry : manifest.skeletons()) {
				SkeletonDocument standalone = new SkeletonDocument();
				SkeletonLoader.loadRuntime(standalone, new FileHandle(entry.runtime().toFile()));
				standalone.animationState = standalone.newAnimationState();
				try (SkeletonProject single = new SkeletonProject(new SkeletonDocument())) {
					single.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("hero", standalone)));
					assertEquals(ProjectStateStore.LoadResult.RESTORED, ProjectStateStore.load(entry.runtime(), single),
						"fresh sidecar matches the newly exported JSON or binary fingerprint");
					assertTrue(single.documents().get(0).getCurrentTime() > 0f,
						"a runtime file opened alone also restores current editor state");
				}
			}
			Files.writeString(target, "existing archive");
			CancellationToken cancelled = new CancellationToken() {
				int checks;
				@Override public void checkCancelled() {
					if (++checks == 20) cancel();
					super.checkCancelled();
				}
			};
			assertCancelled(() -> ProjectPackager.write(project, true, target, cancelled),
				"complete project packaging can be cancelled after writing starts");
			assertEquals("existing archive", Files.readString(target), "cancellation preserves the destination archive");
			Files.writeString(manifestPath, "{\"format\":\"openspine-project\",\"version\":1,\"activeIndex\":0,"
				+ "\"editorState\":\"editor-state.openspine.json\",\"skeletons\":[{\"name\":\"bad\",\"runtime\":\"../outside.json\"}]}");
			try {
				ProjectPackageManifest.read(manifestPath);
				throw new AssertionError("manifest traversal must be rejected");
			} catch (java.io.IOException expected) {}
		} finally {
			project.close();
		}
	}

	private static void isolatesSameNamedResources(Path directory) throws Exception {
		List<SkeletonProject.LoadedSkeleton> loaded = new java.util.ArrayList<>();
		for (int index = 0; index < 2; index++) {
			Path folder = Files.createDirectories(directory.resolve("same-names-" + index));
			Files.writeString(folder.resolve("hero.atlas"),
				"page.png\nsize: 1, 1\nformat: RGBA8888\nfilter: Nearest, Nearest\nrepeat: none\n");
			Files.write(folder.resolve("page.png"), new byte[] {(byte)(index + 1)});
			SkeletonDocument document = projectDocument(folder.resolve("hero.json"), index);
			document.atlasFileName = "hero.atlas";
			loaded.add(new SkeletonProject.LoadedSkeleton("hero " + index, document));
		}
		try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
			project.replaceAll(loaded);
			Path target = directory.resolve("same-names.zip");
			ProjectPackager.write(project, false, target);
			try (ZipFile zip = new ZipFile(target.toFile())) {
				for (int index = 0; index < 2; index++) {
					String prefix = String.format(java.util.Locale.ROOT, "skeletons/%03d/", index + 1);
					assertTrue(zip.getEntry(prefix + "hero.json") != null, "same named runtime files remain separate");
					assertEquals(index + 1, zip.getInputStream(zip.getEntry(prefix + "page.png")).read(),
						"same named atlas pages retain the correct skeleton's content");
				}
			}
		}
	}

	private static void packagesUnicodeAtlasPages(Path directory) throws Exception {
		Path resources = Files.createDirectories(directory.resolve("unicode-atlas"));
		String pageName = "人物/图集页[1].png";
		Path page = resources.resolve(pageName);
		Files.createDirectories(page.getParent());
		Files.write(page, new byte[] {11, 22, 33});
		Files.writeString(resources.resolve("hero.atlas"), pageName
			+ "\nsize: 1,1\nformat: RGBA8888\nfilter: Nearest,Nearest\nrepeat: none\n", StandardCharsets.UTF_8);
		SkeletonDocument document = projectDocument(resources.resolve("hero.json"), 0f);
		document.atlasFileName = "hero.atlas";
		Path singleTarget = directory.resolve("unicode-single.zip");
		ProjectPackager.write(document, singleTarget);
		try (ZipFile zip = new ZipFile(singleTarget.toFile())) {
			assertEquals(11, zip.getInputStream(zip.getEntry(pageName)).read(), "single-document packaging reads atlas names as UTF-8");
		}
		try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
			project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("人物", document)));
			Path projectTarget = directory.resolve("unicode-project.zip");
			ProjectPackager.write(project, true, projectTarget);
			try (ZipFile zip = new ZipFile(projectTarget.toFile())) {
				assertEquals(11, zip.getInputStream(zip.getEntry("skeletons/001/" + pageName)).read(),
					"full-project packaging preserves Chinese page names independently of the JVM charset");
			}
		}
	}

	private static void reopensLatestSavedStateWithoutOverwritingChangedRuntimes(Path directory) throws Exception {
		Path source = Files.createDirectories(directory.resolve("reopen-source"));
		Path target = directory.resolve("reopen.zip");
		try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
			project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("Body", projectDocument(source.resolve("hero.json"), 0f)),
				new SkeletonProject.LoadedSkeleton("Equipment", projectDocument(source.resolve("gear.json"), 0f))));
			project.switchTo(1);
			ProjectPackager.write(project, true, target);
		}
		Path extracted = directory.resolve("reopen-extracted");
		extract(target, extracted);
		Path manifestPath = extracted.resolve(ProjectPackageManifest.FILE_NAME);
		ProjectPackageManifest.Manifest manifest = ProjectPackageManifest.read(manifestPath);
		Path firstRuntime = manifest.skeletons().get(0).runtime();
		Path secondRuntime = manifest.skeletons().get(1).runtime();
		try (SkeletonProject reopened = reopenedProject(manifestPath)) {
			reopened.switchTo(0);
			SkeletonDocument edited = reopened.documents().get(0);
			edited.data.setFps(72f);
			edited.data.setImagesPath("new-images");
			edited.data.setAudioPath("new-audio");
			edited.setTreeViewState(new SkeletonDocument.TreeViewState("latest", 3f, 4f, java.util.Set.of(), java.util.Set.of()));
			edited.setAnimationMixSettings(0.65f, List.of());
			SkeletonExporter.writeAtomic(edited, firstRuntime);
			ProjectStateStore.write(firstRuntime, reopened, false);
			Files.setLastModifiedTime(ProjectStateStore.sidecarPath(firstRuntime),
				java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 2000));
			SkeletonDocument externalEdit = reopened.documents().get(1);
			externalEdit.data.setFps(96f);
			SkeletonExporter.writeAtomic(externalEdit, secondRuntime);
		}
		ProjectPackageManifest.LoadedPackage loaded = ProjectPackageManifest.load(manifestPath);
		assertEquals(0, loaded.activeIndex(), "the latest whole-project sidecar restores the active name");
		assertTrue(!loaded.showAll(), "the latest valid sidecar restores whole-project visibility");
		try (SkeletonProject reopened = new SkeletonProject(new SkeletonDocument())) {
			reopened.replaceAll(loaded.skeletons());
			SkeletonDocument edited = reopened.documents().get(0);
			assertNear(72f, edited.data.getFps(), "saved FPS is not overwritten by the original package snapshot");
			assertEquals("new-images", edited.data.getImagesPath(), "saved image paths remain current");
			assertEquals("new-audio", edited.data.getAudioPath(), "saved audio paths remain current");
			assertEquals("latest", edited.treeViewState().filter(), "latest editor state is restored by the runtime fingerprint");
			assertNear(0.65f, edited.defaultAnimationMix(), "latest animation mixing is restored");
			assertNear(96f, reopened.documents().get(1).data.getFps(),
				"one valid whole-project sidecar cannot overwrite another runtime changed outside the editor");
		}
		Files.delete(ProjectStateStore.sidecarPath(firstRuntime));
		try (SkeletonProject reopened = reopenedProject(manifestPath)) {
			SkeletonDocument edited = reopened.documents().get(0);
			assertNear(72f, edited.data.getFps(), "missing sidecars preserve the saved runtime FPS");
			assertEquals("new-images", edited.data.getImagesPath(), "missing sidecars preserve saved resource paths");
			assertEquals("", edited.treeViewState().filter(), "a missing sidecar does not replay an old package snapshot");
		}
		SkeletonDocument standalone = new SkeletonDocument();
		SkeletonLoader.loadRuntime(standalone, new FileHandle(firstRuntime.toFile()));
		standalone.animationState = standalone.newAnimationState();
		try (SkeletonProject single = new SkeletonProject(new SkeletonDocument())) {
			single.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("hero", standalone)));
			single.documents().get(0).setTreeViewState(new SkeletonDocument.TreeViewState("standalone", 0f, 0f,
				java.util.Set.of(), java.util.Set.of()));
			ProjectStateStore.write(firstRuntime, single, false);
		}
		try (SkeletonProject reopened = reopenedProject(manifestPath)) {
			assertEquals("standalone", reopened.documents().get(0).treeViewState().filter(),
				"runtime sidecars saved when opened alone still map to their manifest display name");
		}
	}

	private static SkeletonProject reopenedProject(Path manifest) throws Exception {
		ProjectPackageManifest.LoadedPackage loaded = ProjectPackageManifest.load(manifest);
		SkeletonProject project = new SkeletonProject(new SkeletonDocument());
		project.replaceAll(loaded.skeletons());
		project.switchTo(loaded.activeIndex());
		return project;
	}

	private static void disposesDetachedDocumentsWhenTransferFails(Path directory) {
		java.util.concurrent.atomic.AtomicInteger firstCleanup = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicInteger secondCleanup = new java.util.concurrent.atomic.AtomicInteger();
		SkeletonDocument first = projectDocument(directory.resolve("cleanup-first.json"), 0f);
		SkeletonDocument second = projectDocument(directory.resolve("cleanup-second.json"), 0f);
		first.ownSourceResources(firstCleanup::incrementAndGet);
		second.ownSourceResources(secondCleanup::incrementAndGet);
		try (SkeletonProject staging = new SkeletonProject(new SkeletonDocument())) {
			staging.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("first", first),
				new SkeletonProject.LoadedSkeleton("second", second)));
			staging.documents().get(1).animationState = null;
			try {
				ProjectPackageManifest.detachDocuments(staging);
				throw new AssertionError("a partly initialized document must fail transfer");
			} catch (IllegalArgumentException expected) {}
			assertEquals(1, firstCleanup.get(), "already detached resources are disposed when a later transfer fails");
		}
		assertEquals(1, firstCleanup.get(), "detached resources are released exactly once");
		assertEquals(1, secondCleanup.get(), "untransferred staging resources are also released");
	}

	private static void matchesProjectNameCaseRules(Path directory) throws Exception {
		Path target = directory.resolve("name-cases.zip");
		try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
			project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("Hero", projectDocument(directory.resolve("case1.json"), 0f)),
				new SkeletonProject.LoadedSkeleton("hero", projectDocument(directory.resolve("case2.json"), 0f))));
			assertEquals("hero (2)", project.nameAt(1), "project names are already unique ignoring case");
			ProjectPackager.write(project, false, target);
		}
		Path extracted = directory.resolve("name-cases-extracted");
		extract(target, extracted);
		Path manifest = extracted.resolve(ProjectPackageManifest.FILE_NAME);
		assertEquals(2, ProjectPackageManifest.read(manifest).skeletons().size(), "normalized names are accepted by the manifest");
		com.badlogic.gdx.utils.JsonValue data = new com.badlogic.gdx.utils.JsonReader().parse(Files.readString(manifest));
		data.get("skeletons").get(1).get("name").set("hERO");
		Files.writeString(manifest, data.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json));
		try {
			ProjectPackageManifest.read(manifest);
			throw new AssertionError("manifest names follow the same case-insensitive rule");
		} catch (java.io.IOException expected) {}
	}

	private static SkeletonDocument projectDocument(Path source, float x) {
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
		return document;
	}

	private static void extract(Path source, Path directory) throws Exception {
		try (ZipFile zip = new ZipFile(source.toFile())) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				var entry = entries.nextElement();
				Path target = directory.resolve(ProjectPackager.safeEntryName(entry.getName()));
				Files.createDirectories(target.getParent());
				try (var input = zip.getInputStream(entry)) { Files.copy(input, target); }
			}
		}
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f) throw new AssertionError(message + ": " + actual);
	}

	private static void packagesReferencedAudio(Path directory) throws Exception {
		Path sourceDirectory = Files.createDirectories(directory.resolve("audio-source"));
		Path resourceDirectory = Files.createDirectories(directory.resolve("audio-resources"));
		Path voiceDirectory = Files.createDirectories(resourceDirectory.resolve("audio").resolve("voice"));
		Path voice = voiceDirectory.resolve("line.ogg");
		Path cue = resourceDirectory.resolve("audio").resolve("cue.wav");
		Files.write(voice, new byte[] {9, 8, 7});
		Files.write(cue, new byte[] {6, 5, 4});

		SkeletonData data = new SkeletonData();
		data.setName("audio-hero");
		data.setVersion("4.3.0");
		data.setFps(30f);
		data.setAudioPath("audio");
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root);
		EventData voiceEvent = new EventData("voice");
		voiceEvent.setAudioPath("voice/line.ogg");
		voiceEvent.getSetupPose().setString("");
		data.getEvents().add(voiceEvent);
		EventData cueEvent = new EventData("cue");
		cueEvent.setAudioPath("cue.wav");
		cueEvent.getSetupPose().setString("");
		data.getEvents().add(cueEvent);

		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(new TextureAtlas(), data, new Skeleton(data),
			sourceDirectory.resolve("audio-hero.spine").toString(), "", null);
		document.resourceDirectory = resourceDirectory.toString();
		try {
			Path target = directory.resolve("audio-project.zip");
			ProjectPackager.PackageResult result = ProjectPackager.write(document, target);
			assertEquals(3, result.fileCount(), "skeleton and referenced event audio files are packaged");
			try (ZipFile zip = new ZipFile(target.toFile())) {
				assertTrue(zip.getEntry("audio/voice/line.ogg") != null,
					"nested event audio keeps its skeleton.audio-relative path");
				assertTrue(zip.getEntry("audio/cue.wav") != null,
					"top-level event audio keeps its skeleton.audio-relative path");
				String json = new String(zip.getInputStream(zip.getEntry("audio-hero.json")).readAllBytes(),
					StandardCharsets.UTF_8);
				assertTrue(json.contains("\"audio\": \"audio\""),
					"packaged JSON keeps the skeleton.audio path unchanged");
				assertTrue(json.contains("\"audio\": \"voice/line.ogg\""),
					"packaged JSON keeps the event audio path unchanged");
			}

			data.setAudioPath("./");
			voiceEvent.setAudioPath("audio/voice/line.ogg");
			cueEvent.setAudioPath("audio/cue.wav");
			Path currentAudioTarget = directory.resolve("current-audio-root.zip");
			ProjectPackager.write(document, currentAudioTarget);
			try (ZipFile zip = new ZipFile(currentAudioTarget.toFile())) {
				assertTrue(zip.getEntry("audio/voice/line.ogg") != null,
					"skeleton.audio current-directory syntax maps to an empty ZIP prefix");
			}

			data.setAudioPath("audio");
			voiceEvent.setAudioPath("../outside.wav");
			assertThrows(() -> ProjectPackager.write(document, directory.resolve("unsafe-event-audio.zip")),
				"event audio traversal outside skeleton.audio is rejected");
			voiceEvent.setAudioPath(voice.toAbsolutePath().toString());
			assertThrows(() -> ProjectPackager.write(document, directory.resolve("absolute-event-audio.zip")),
				"absolute event audio paths are rejected");
			voiceEvent.setAudioPath("voice/line.ogg");
			data.setAudioPath("../audio");
			assertThrows(() -> ProjectPackager.write(document, directory.resolve("unsafe-audio-root.zip")),
				"skeleton.audio traversal outside the resource directory is rejected");
		} finally {
			document.dispose();
		}
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertThrows(ThrowingRunnable action, String message) throws Exception {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertCancelled(ThrowingRunnable action, String message) throws Exception {
		try {
			action.run();
		} catch (java.util.concurrent.CancellationException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private ProjectPackagerTest() {}
}
