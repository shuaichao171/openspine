package com.openspine.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

import com.badlogic.gdx.files.FileHandle;
import com.esotericsoftware.spine.Skeleton;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Saves complete projects by publishing a new manifest only after its isolated generation is complete. */
public final class NativeProjectStore {
	public static final String GENERATION_DIRECTORY = ".openspine-generations";
	public record FileStamp(Path manifestPath, boolean exists, String sha256) {}
	public record SaveResult(Path manifestPath, FileStamp stamp,
		List<ProjectPackageManifest.ResolvedSkeleton> skeletons) {
		public SaveResult { skeletons = List.copyOf(skeletons); }
	}

	/** The root sidecar is deliberately excluded: ordinary view-state updates do not conflict with a save. */
	public static FileStamp inspect(Path manifestPath) throws IOException {
		return inspect(manifestPath, new CancellationToken());
	}

	public static FileStamp inspect(Path manifestPath, CancellationToken cancellation) throws IOException {
		if (cancellation == null) throw new IllegalArgumentException("检查工程需要取消令牌");
		Path target = validatedTarget(manifestPath);
		return inspectAt(target, target, cancellation);
	}

	/** Contains no textures or owned files. Content hashing belongs to the preparation worker only. */
	static final class OpenSnapshot {
		private final Inspection inspection;
		private OpenSnapshot(Inspection inspection) { this.inspection = inspection; }
		FileStamp stamp() { return inspection.stamp(); }
		ProjectPackageManifest.Manifest manifest() { return inspection.manifest(); }
		void requireUnchanged(CancellationToken cancellation) throws IOException {
			// Do not rehash resources on the rendering thread. Detect ordinary intervening writes.
			requireObserved(inspection.observations(), cancellation);
		}
	}

	static OpenSnapshot prepareOpen(Path manifestPath, CancellationToken cancellation) throws IOException {
		if (cancellation == null) throw new IllegalArgumentException("打开工程需要取消令牌");
		Path target = validatedTarget(manifestPath);
		Inspection inspection = inspectSnapshot(target, target, cancellation);
		if (!inspection.stamp().exists()) throw new IOException("项目清单不存在：" + target);
		return new OpenSnapshot(inspection);
	}

	/** The caller must retain its load/last-save stamp and may rebind documents only after this returns. */
	public static SaveResult save(SkeletonProject project, boolean showAllSkeletons, Path manifestPath,
		FileStamp expected, CancellationToken cancellation) throws IOException {
		if (project == null || project.size() == 0) throw new IllegalArgumentException("没有可保存的工程");
		if (cancellation == null || expected == null) throw new IllegalArgumentException("保存需要取消令牌和工程文件基线");
		Path target = validatedTarget(manifestPath);
		if (!target.equals(expected.manifestPath())) throw new IllegalArgumentException("工程文件基线与保存路径不匹配");
		cancellation.checkCancelled();
		Files.createDirectories(target.getParent());
		Path lockPath = target.resolveSibling(target.getFileName() + ".lock");
		if (Files.isSymbolicLink(lockPath)) throw new IOException("工程保存锁不能是符号链接：" + lockPath);
		try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
			try (FileLock lock = acquireLock(channel)) {
				if (lock == null) throw new IOException("另一项操作正在保存该工程，请稍后重试");
				return saveLocked(project, showAllSkeletons, target, expected, cancellation);
			}
		}
	}

	private static FileLock acquireLock(FileChannel channel) throws IOException {
		try { return channel.tryLock(); }
		catch (OverlappingFileLockException conflict) { return null; }
	}

	private static SaveResult saveLocked(SkeletonProject project, boolean showAllSkeletons, Path target,
		FileStamp expected, CancellationToken cancellation) throws IOException {
		requireUnchanged(expected, inspectAt(target, target, cancellation));
		ProjectGuard guard = new ProjectGuard(project);
		ResourceGuard resources = new ResourceGuard(project, cancellation);
		guard.requireUnchanged();
		Path archive = null, generation = null, temporaryManifest = null;
		AtomicBoolean committed = new AtomicBoolean();
		try {
			archive = Files.createTempFile(target.getParent(), ".openspine-project-", ".zip");
			ProjectPackager.write(project, showAllSkeletons, archive, stagingToken(cancellation));
			guard.requireUnchanged();
			Path storage = target.getParent().resolve(GENERATION_DIRECTORY);
			Files.createDirectories(storage);
			if (!storage.toRealPath().getParent().equals(target.getParent().toRealPath()) || Files.isSymbolicLink(storage))
				throw new IOException("工程资源目录不能重定向到其他目录：" + storage);
			generation = Files.createTempDirectory(storage, "generation-");
			extractOwnArchive(archive, generation, cancellation);
			ProjectPackageManifest.Manifest prepared = ProjectPackageManifest.read(generation.resolve(ProjectPackageManifest.FILE_NAME));
			List<PathObservation> copiedObservations = resources.requireCopied(prepared.skeletons(), cancellation);
			List<ProjectPackageManifest.SkeletonEntry> entries = new ArrayList<>();
			for (var skeleton : prepared.skeletons()) entries.add(new ProjectPackageManifest.SkeletonEntry(
				skeleton.name(), portable(target.getParent().relativize(skeleton.runtime())), skeleton.atlas()));
			String editorState = portable(target.getParent().relativize(prepared.editorState()));
			byte[] manifestBytes = ProjectPackageManifest.serialize(entries, prepared.activeIndex(), prepared.showAll(), editorState)
				.getBytes(StandardCharsets.UTF_8);
			temporaryManifest = Files.createTempFile(target.getParent(), ".openspine-manifest-", ".tmp");
			writeAndForce(temporaryManifest, manifestBytes);
			FileStamp nextStamp = inspectAt(target, temporaryManifest, cancellation);
			SaveResult result = new SaveResult(target, nextStamp, prepared.skeletons());
			Path stagedManifest = temporaryManifest;
			List<PathObservation> inputObservations = resources.requireUnchanged(cancellation);
			Inspection previous = inspectSnapshot(target, target, cancellation);
			requireUnchanged(expected, previous.stamp());
			Inspection staged = inspectSnapshot(target, stagedManifest, cancellation);
			requireUnchanged(nextStamp, staged.stamp());
			requireObserved(copiedObservations, cancellation);
			guard.requireUnchanged();
			cancellation.commit(() -> {
				// All parsing and content hashing is cancellable before this short commit section.
				// Metadata catches intervening ordinary writes; uncooperative writes cannot be made linearizable.
				requireObserved(inputObservations, null);
				requireObserved(copiedObservations, null);
				requireObserved(previous.observations(), null);
				requireObserved(staged.observations(), null);
				guard.requireUnchanged();
				try {
					Files.move(stagedManifest, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException unsupported) {
					throw new IOException("此文件系统不支持原子替换工程清单，上一版工程未被修改", unsupported);
				}
				committed.set(true);
			});
			return result;
		} finally {
			deleteTemporaryFile(archive);
			deleteTemporaryFile(temporaryManifest);
			if (!committed.get()) deleteOwnedGeneration(generation);
		}
	}

	private static CancellationToken stagingToken(CancellationToken parent) {
		return new CancellationToken() {
			@Override public void checkCancelled() { parent.checkCancelled(); }
			@Override public boolean isCancellationRequested() { return parent.isCancellationRequested(); }
			@Override public void commit(CommitAction action) throws IOException {
				parent.checkCancelled();
				action.run(); // This publishes only a temporary ZIP, never the user's manifest.
			}
		};
	}

	private static FileStamp inspectAt(Path identity, Path physical, CancellationToken cancellation) throws IOException {
		return inspectSnapshot(identity, physical, cancellation).stamp();
	}

	private record Inspection(FileStamp stamp, List<PathObservation> observations, ProjectPackageManifest.Manifest manifest) {}
	private static Inspection inspectSnapshot(Path identity, Path physical, CancellationToken cancellation) throws IOException {
		cancellation.checkCancelled();
		List<PathObservation> observations = new ArrayList<>();
		PathObservation manifestObservation = observe(physical);
		observations.add(manifestObservation);
		if (!manifestObservation.exists()) return new Inspection(new FileStamp(identity, false, ""), List.copyOf(observations), null);
		if (!Files.isRegularFile(physical, LinkOption.NOFOLLOW_LINKS)) throw new IOException("工程清单不是普通文件：" + physical);
		if (Files.size(physical) > 1024 * 1024) throw new IOException("工程清单过大：" + physical);
		byte[] manifest = Files.readAllBytes(physical);
		ProjectPackageManifest.Manifest parsed = ProjectPackageManifest.read(physical);
		MessageDigest digest = sha256();
		digest.update(manifest);
		Set<Path> directories = new TreeSet<>();
		Set<Path> sources = manifestSources(parsed, cancellation, directories);
		for (Path directory : directories) observations.add(observe(directory));
		for (Path source : sources) {
			cancellation.checkCancelled();
			digest.update((byte)0);
			digest.update(portable(identity.getParent().relativize(source)).getBytes(StandardCharsets.UTF_8));
			digest.update((byte)0);
			if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
				digest.update((byte)0);
				observations.add(observe(source));
				continue;
			}
			digest.update((byte)1);
			digest.update(fileHash(source, cancellation, observations));
		}
		Set<Path> finalDirectories = new TreeSet<>();
		if (!sources.equals(manifestSources(parsed, cancellation, finalDirectories)) || !directories.equals(finalDirectories))
			throw new IOException("读取期间工程引用的资源发生变化，请重新打开工程");
		if (!Arrays.equals(manifest, Files.readAllBytes(physical))) throw new IOException("读取期间工程清单发生变化，请重新打开工程");
		requireObserved(observations, cancellation);
		return new Inspection(new FileStamp(identity, true, HexFormat.of().formatHex(digest.digest())), List.copyOf(observations), parsed);
	}

	private static Set<Path> manifestSources(ProjectPackageManifest.Manifest manifest, CancellationToken cancellation, Set<Path> directories)
		throws IOException {
		Set<Path> sources = new TreeSet<>(Comparator.comparing(Path::toString));
		sources.add(manifest.editorState());
		for (var skeleton : manifest.skeletons()) {
			cancellation.checkCancelled();
			sources.add(skeleton.runtime());
			sources.add(ProjectStateStore.sidecarPath(skeleton.runtime()));
			sources.addAll(storedResources(skeleton, cancellation, directories).values());
		}
		return sources;
	}

	private static Map<String, Path> storedResources(ProjectPackageManifest.ResolvedSkeleton skeleton,
		CancellationToken cancellation, Set<Path> directories) throws IOException {
		cancellation.checkCancelled();
		try {
			SkeletonDocument source = new SkeletonDocument();
			source.data = SkeletonLoader.loadAnimationSource(new FileHandle(skeleton.runtime().toFile()));
			source.skeleton = new Skeleton(source.data);
			source.animationState = source.newAnimationState();
			source.filePath = skeleton.runtime().toString();
			source.resourceDirectory = skeleton.runtime().getParent().toString();
			source.atlasFileName = skeleton.atlas();
			try (SkeletonProject stateProject = new SkeletonProject(new SkeletonDocument())) {
				String runtimeName = skeleton.runtime().getFileName().toString();
				String standaloneName = runtimeName.substring(0, runtimeName.lastIndexOf('.'));
				stateProject.replaceAll(List.of(new SkeletonProject.LoadedSkeleton(standaloneName, source)));
				ProjectStateStore.LoadOutcome state = ProjectStateStore.loadPortable(
					skeleton.runtime(), stateProject, true);
				if (state.result() == ProjectStateStore.LoadResult.SOURCE_CHANGED)
					throw new IOException("工程编辑器状态与运行时文件不匹配：" + skeleton.runtime());
				cancellation.checkCancelled();
				return ProjectPackager.resourceFiles(stateProject.documents().get(0), cancellation, directories);
			}
		} catch (java.util.concurrent.CancellationException cancelled) { throw cancelled; }
		catch (RuntimeException invalid) { throw new IOException("工程引用的资源无效：" + skeleton.runtime(), invalid); }
	}

	/** Bounded memory even for large audio files; detect a replacement or rewrite during this read. */
	private static byte[] fileHash(Path source, CancellationToken cancellation, List<PathObservation> observations) throws IOException {
		cancellation.checkCancelled();
		PathObservation before = observe(source);
		if (!before.regularFile()) throw new IOException("工程数据不是普通文件：" + source);
		MessageDigest digest = sha256();
		byte[] buffer = new byte[65536];
		try (InputStream input = Files.newInputStream(source)) {
			for (int count; (count = input.read(buffer)) >= 0;) {
				cancellation.checkCancelled();
				digest.update(buffer, 0, count);
			}
		}
		PathObservation after = observe(source);
		if (!before.equals(after))
			throw new IOException("读取期间工程文件发生变化：" + source);
		if (observations != null) observations.add(after);
		return digest.digest();
	}

	private record PathObservation(Path path, boolean exists, boolean regularFile, boolean directory,
		long size, FileTime modified, Object fileKey, Path realPath) {}
	private static PathObservation observe(Path path) throws IOException {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return new PathObservation(path, false, false, false, 0, null, null, null);
		BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		return new PathObservation(path, true, attributes.isRegularFile(), attributes.isDirectory(), attributes.size(),
			attributes.lastModifiedTime(), attributes.fileKey(), path.toRealPath());
	}

	private static void requireObserved(List<PathObservation> observations, CancellationToken cancellation) throws IOException {
		for (PathObservation expected : observations) {
			if (cancellation != null) cancellation.checkCancelled();
			if (!expected.equals(observe(expected.path())))
				throw new IOException("提交前工程文件或资源目录发生变化，上一版工程未被修改：" + expected.path());
		}
	}

	private record ResourceFile(Path realPath, String sha256) {}
	private static final class ResourceGuard {
		private final SkeletonProject project;
		private final List<Map<String, ResourceFile>> baseline;

		ResourceGuard(SkeletonProject project, CancellationToken cancellation) throws IOException {
			this.project = project;
			baseline = capture(cancellation).files();
		}

		private record Capture(List<Map<String, ResourceFile>> files, List<PathObservation> observations) {}
		private Capture capture(CancellationToken cancellation) throws IOException {
			List<Map<String, ResourceFile>> result = new ArrayList<>();
			List<PathObservation> observations = new ArrayList<>();
			for (SkeletonDocument document : project.documents()) {
				Map<String, ResourceFile> files = new TreeMap<>();
				Set<Path> directories = new TreeSet<>();
				Map<String, Path> resources = ProjectPackager.resourceFiles(document, cancellation, directories);
				for (Path directory : directories) observations.add(observe(directory));
				for (var resource : resources.entrySet()) {
					Path path = resource.getValue();
					Path realPath = path.toRealPath();
					String hash = HexFormat.of().formatHex(fileHash(path, cancellation, observations));
					if (!realPath.equals(path.toRealPath())) throw changed();
					files.put(resource.getKey(), new ResourceFile(realPath, hash));
				}
				Set<Path> finalDirectories = new TreeSet<>();
				if (!resources.equals(ProjectPackager.resourceFiles(document, cancellation, finalDirectories)) || !directories.equals(finalDirectories))
					throw changed();
				result.add(Map.copyOf(files));
			}
			requireObserved(observations, cancellation);
			return new Capture(List.copyOf(result), List.copyOf(observations));
		}

		List<PathObservation> requireUnchanged(CancellationToken cancellation) throws IOException {
			Capture current = capture(cancellation);
			if (!baseline.equals(current.files())) throw changed();
			return current.observations();
		}

		List<PathObservation> requireCopied(List<ProjectPackageManifest.ResolvedSkeleton> skeletons, CancellationToken cancellation)
			throws IOException {
			if (skeletons.size() != baseline.size()) throw changed();
			List<PathObservation> observations = new ArrayList<>();
			for (int index = 0; index < skeletons.size(); index++) {
				Set<Path> directories = new TreeSet<>();
				Map<String, Path> copied = storedResources(skeletons.get(index), cancellation, directories);
				for (Path directory : directories) observations.add(observe(directory));
				Map<String, ResourceFile> expected = baseline.get(index);
				if (!copied.keySet().equals(expected.keySet())) throw changed();
				for (var resource : copied.entrySet())
					if (!expected.get(resource.getKey()).sha256().equals(HexFormat.of().formatHex(fileHash(resource.getValue(), cancellation, observations))))
						throw changed();
				Set<Path> finalDirectories = new TreeSet<>();
				if (!copied.equals(storedResources(skeletons.get(index), cancellation, finalDirectories)) || !directories.equals(finalDirectories))
					throw changed();
			}
			requireObserved(observations, cancellation);
			return List.copyOf(observations);
		}

		private IOException changed() { return new IOException("保存期间工程图片、图集或音频已改变，请重新保存；上一版工程未被修改"); }
	}

	private static Path validatedTarget(Path path) {
		if (path == null) throw new IllegalArgumentException("工程清单路径不能为空");
		Path target = path.toAbsolutePath().normalize();
		if (target.getFileName() == null || !ProjectPackageManifest.isManifestFileName(target.getFileName().toString())
			|| target.getParent() == null) throw new IllegalArgumentException("原生工程应保存为 " + ProjectPackageManifest.FILE_NAME);
		return target;
	}

	private static void requireUnchanged(FileStamp expected, FileStamp actual) throws IOException {
		if (!expected.equals(actual)) throw new IOException("工程文件已被外部修改，请重新打开或另存工程，当前修改仍保留在内存中");
	}

	private static void extractOwnArchive(Path archive, Path generation, CancellationToken cancellation) throws IOException {
		Set<String> entries = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
			var files = zip.entries();
			byte[] bytes = new byte[65536];
			while (files.hasMoreElements()) {
				cancellation.checkCancelled();
				var entry = files.nextElement();
				String name = ProjectPackager.safeEntryName(entry.getName());
				if (entry.isDirectory() || !entries.add(name)) throw new IOException("临时工程包含重复或不支持的条目：" + name);
				Path output = generation.resolve(name).normalize();
				if (!output.startsWith(generation)) throw new IOException("临时工程条目超出资源目录：" + name);
				Files.createDirectories(output.getParent());
				try (InputStream input = zip.getInputStream(entry);
					FileChannel channel = FileChannel.open(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
					for (int count; (count = input.read(bytes)) >= 0;) {
						cancellation.checkCancelled();
						ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, count);
						while (buffer.hasRemaining()) channel.write(buffer);
					}
					channel.force(true);
				}
			}
		}
	}

	private static void writeAndForce(Path path, byte[] bytes) throws IOException {
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining()) channel.write(buffer);
			channel.force(true);
		}
	}

	private static void deleteTemporaryFile(Path path) {
		if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) {}
	}

	/** Walks only the unique directory created by this save; older generations and user resources are never removed. */
	private static void deleteOwnedGeneration(Path generation) {
		if (generation == null) return;
		try (var paths = Files.walk(generation)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		} catch (IOException ignored) { /* An abandoned generation is harmless and is never referenced by the old manifest. */ }
	}

	private static String portable(Path path) { return path.toString().replace('\\', '/'); }
	private static MessageDigest sha256() {
		try { return MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
	}

	private static final class ProjectGuard {
		private final SkeletonProject project;
		private final List<SkeletonDocument> documents;
		private final List<String> names = new ArrayList<>();
		private final long[] revisions;
		private final int activeIndex;

		ProjectGuard(SkeletonProject project) {
			this.project = project;
			documents = project.documents();
			revisions = new long[documents.size()];
			activeIndex = project.activeIndex();
			for (int index = 0; index < documents.size(); index++) {
				names.add(project.nameAt(index));
				revisions[index] = documents.get(index).revision;
			}
		}

		void requireUnchanged() throws IOException {
			List<SkeletonDocument> current = project.documents();
			if (current.size() != documents.size() || activeIndex != project.activeIndex()) throw changed();
			for (int index = 0; index < current.size(); index++)
				if (current.get(index) != documents.get(index) || current.get(index).revision != revisions[index]
					|| !names.get(index).equals(project.nameAt(index))) throw changed();
		}

		private IOException changed() { return new IOException("保存期间工程已改变，请重新保存，当前修改仍保留在内存中"); }
	}

	private NativeProjectStore() {}
}
