package com.openspine.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Opens a project package after extraction, resolving all resources inside its package directory. */
public final class ProjectPackageManifest {
	public static final String FILE_NAME = "openspine-project.json";
	public static final String EDITOR_STATE = "editor-state.openspine.json";
	private static final int VERSION = 1;
	private static final int MAX_SKELETONS = 1000;
	private static final long MAX_STATE_BYTES = 8L * 1024L * 1024L;

	public record SkeletonEntry(String name, String runtime, String atlas) {}
	public record ResolvedSkeleton(String name, Path runtime, String atlas) {}
	public record Manifest(List<ResolvedSkeleton> skeletons, Path editorState, int activeIndex, boolean showAll) {
		public Manifest { skeletons = List.copyOf(skeletons); }
	}
	public record LoadedPackage(List<SkeletonProject.LoadedSkeleton> skeletons, int activeIndex, boolean showAll) {
		public LoadedPackage { skeletons = List.copyOf(skeletons); }
	}
	/** Immutable, texture-free preparation; safe to discard if a callback is cancelled or never delivered. */
	public static final class PreparedPackage {
		private final NativeProjectStore.OpenSnapshot snapshot;
		private PreparedPackage(NativeProjectStore.OpenSnapshot snapshot) { this.snapshot = snapshot; }
		public NativeProjectStore.FileStamp stamp() { return snapshot.stamp(); }
		public Path manifestPath() { return stamp().manifestPath(); }
		public void requireUnchanged(CancellationToken cancellation) throws IOException {
			if (cancellation == null) throw new IllegalArgumentException("打开工程需要取消令牌");
			snapshot.requireUnchanged(cancellation);
		}
	}

	/** Performs full resource discovery and content validation without creating any GL resources. */
	public static PreparedPackage prepare(Path manifestFile, CancellationToken cancellation) throws IOException {
		return new PreparedPackage(NativeProjectStore.prepareOpen(manifestFile, cancellation));
	}
	private record RestoredState(long modifiedMillis, boolean showAll, String activeName) {}

	public static boolean isManifestFileName(String name) {
		if (name == null) return false;
		String normalized = name.toLowerCase(Locale.ROOT);
		return FILE_NAME.equals(normalized) || normalized.endsWith("." + FILE_NAME);
	}

	static String serialize(List<SkeletonEntry> skeletons, int activeIndex, boolean showAll) {
		return serialize(skeletons, activeIndex, showAll, EDITOR_STATE);
	}

	static String serialize(List<SkeletonEntry> skeletons, int activeIndex, boolean showAll, String editorState) {
		JsonValue root = new JsonValue(JsonValue.ValueType.object);
		root.addChild("format", new JsonValue("openspine-project"));
		root.addChild("version", new JsonValue((long)VERSION));
		root.addChild("activeIndex", new JsonValue((long)activeIndex));
		root.addChild("showAll", new JsonValue(showAll));
		root.addChild("editorState", new JsonValue(ProjectPackager.safeEntryName(editorState)));
		JsonValue list = new JsonValue(JsonValue.ValueType.array);
		root.addChild("skeletons", list);
		for (SkeletonEntry skeleton : skeletons) {
			JsonValue item = new JsonValue(JsonValue.ValueType.object);
			item.addChild("name", new JsonValue(skeleton.name()));
			item.addChild("runtime", new JsonValue(skeleton.runtime()));
			item.addChild("atlas", new JsonValue(skeleton.atlas()));
			list.addChild(item);
		}
		return root.prettyPrint(JsonWriter.OutputType.json, 100);
	}

	public static Manifest read(Path file) throws IOException {
		if (file == null || !Files.isRegularFile(file)) throw new IOException("项目清单不存在：" + file);
		Path source = file.toAbsolutePath().normalize();
		if (Files.size(source) > 1024 * 1024) throw new IOException("项目清单过大");
		try {
			JsonValue root = new JsonReader().parse(Files.readString(source, StandardCharsets.UTF_8));
			if (!root.isObject() || !"openspine-project".equals(root.getString("format", ""))
				|| root.getInt("version", -1) != VERSION) throw new IOException("不支持的项目清单格式");
			JsonValue values = root.get("skeletons");
			if (values == null || !values.isArray() || values.size == 0 || values.size > MAX_SKELETONS)
				throw new IOException("项目清单骨架数量无效");
			int active = root.getInt("activeIndex", -1);
			if (active < 0 || active >= values.size) throw new IOException("项目清单活动骨架无效");
			Path directory = source.getParent();
			Set<String> names = new HashSet<>(), paths = new HashSet<>();
			List<ResolvedSkeleton> skeletons = new ArrayList<>();
			for (JsonValue value : values) {
				String name = value.getString("name", "").trim();
				if (name.isEmpty() || !names.add(name.toLowerCase(Locale.ROOT)))
					throw new IOException("项目清单骨架名称为空或重复");
				Path runtime = resolveFile(directory, value.getString("runtime"));
				String runtimeName = runtime.getFileName().toString().toLowerCase(Locale.ROOT);
				if (!(runtimeName.endsWith(".json") || runtimeName.endsWith(".skel"))
					|| !paths.add(runtime.toString().toLowerCase(Locale.ROOT)))
					throw new IOException("项目清单骨架路径无效或重复");
				String atlas = value.getString("atlas", "");
				if (!atlas.isEmpty()) resolveFile(runtime.getParent(), atlas);
				skeletons.add(new ResolvedSkeleton(name, runtime, atlas));
			}
			return new Manifest(skeletons, resolveFile(directory, root.getString("editorState")), active,
				root.getBoolean("showAll", false));
		} catch (RuntimeException error) {
			throw new IOException("项目清单无效：" + error.getMessage(), error);
		}
	}

	/** Loads textures on the caller's rendering thread and returns independently owned documents. */
	public static LoadedPackage load(Path manifestFile) throws IOException {
		return loadManifest(read(manifestFile), new CancellationToken(), null);
	}

	/** Only metadata checks surround the GL work; callers should prepare on a cancellable worker. */
	public static LoadedPackage load(PreparedPackage prepared, CancellationToken cancellation) throws IOException {
		if (prepared == null) throw new IllegalArgumentException("缺少已准备的工程");
		prepared.requireUnchanged(cancellation);
		return loadManifest(prepared.snapshot.manifest(), cancellation, prepared);
	}

	private static LoadedPackage loadManifest(Manifest manifest, CancellationToken cancellation, PreparedPackage prepared)
		throws IOException {
		List<SkeletonProject.LoadedSkeleton> loaded = new ArrayList<>();
		SkeletonProject staging = new SkeletonProject(new SkeletonDocument());
		boolean adopted = false;
		RestoredState latestState = null;
		try {
			for (ResolvedSkeleton entry : manifest.skeletons()) {
				cancellation.checkCancelled();
				SkeletonDocument document = new SkeletonDocument();
				try {
					SkeletonLoader.loadRuntime(document, new FileHandle(entry.runtime().toFile()),
						new FileHandle(entry.runtime().getParent().toFile()), entry.atlas());
					document.animationState = document.newAnimationState();
					RestoredState state = restoreRuntimeState(entry, document, manifest);
					if (state != null && (latestState == null || state.modifiedMillis() > latestState.modifiedMillis()))
						latestState = state;
					loaded.add(new SkeletonProject.LoadedSkeleton(entry.name(), document));
				} catch (IOException | RuntimeException | Error error) {
					document.dispose();
					throw error;
				}
			}
			cancellation.checkCancelled();
			if (prepared != null) prepared.requireUnchanged(cancellation);
			staging.replaceAll(loaded);
			adopted = true;
			int activeIndex = manifest.activeIndex();
			if (latestState != null && latestState.activeName() != null) {
				for (int index = 0; index < manifest.skeletons().size(); index++)
					if (manifest.skeletons().get(index).name().equals(latestState.activeName())) activeIndex = index;
			}
			return new LoadedPackage(detachDocuments(staging), activeIndex,
				latestState == null ? manifest.showAll() : latestState.showAll());
		} catch (CancellationException cancelled) {
			throw cancelled;
		} catch (RuntimeException error) {
			throw new IOException("打开打包项目失败：" + error.getMessage(), error);
		} finally {
			if (!adopted) for (SkeletonProject.LoadedSkeleton document : loaded) document.document().dispose();
			staging.close();
		}
	}

	private static RestoredState restoreRuntimeState(ResolvedSkeleton entry, SkeletonDocument document, Manifest manifest)
		throws IOException {
		Path sidecar = ProjectStateStore.sidecarPath(entry.runtime());
		if (!Files.isRegularFile(sidecar)) return null;
		long size = Files.size(sidecar);
		if (size <= 0 || size > MAX_STATE_BYTES) throw new IOException("项目编辑器状态大小无效：" + sidecar);
		JsonValue state = new JsonReader().parse(Files.readString(sidecar, StandardCharsets.UTF_8));
		JsonValue saved = state.get("skeletons");
		if (saved == null || !saved.isArray()) throw new IOException("项目编辑器状态缺少骨架：" + sidecar);
		String savedName = null;
		Set<String> savedNames = new HashSet<>();
		for (JsonValue skeleton : saved) {
			String name = skeleton.getString("name", "");
			savedNames.add(name);
			if (entry.name().equals(name)) savedName = name;
		}
		String runtimeName = entry.runtime().getFileName().toString();
		String standaloneName = runtimeName.substring(0, runtimeName.lastIndexOf('.'));
		if (savedName == null && saved.size == 1 && standaloneName.equals(saved.child.getString("name", "")))
			savedName = standaloneName;
		if (savedName == null) return null;
		String activeName = null;
		if (savedNames.size() == manifest.skeletons().size()
			&& manifest.skeletons().stream().allMatch(skeleton -> savedNames.contains(skeleton.name()))) {
			int active = state.getInt("activeSkeleton", 0);
			if (active >= 0 && active < saved.size) activeName = saved.get(active).getString("name", "");
		}
		// A sidecar fingerprints only its associated runtime, so restore only that document's state.
		SkeletonDocument restored = new SkeletonDocument();
		try {
			try (SkeletonProject single = new SkeletonProject(new SkeletonDocument())) {
				single.replaceAll(List.of(new SkeletonProject.LoadedSkeleton(savedName, document)));
				try {
					ProjectStateStore.LoadOutcome outcome = ProjectStateStore.loadPortable(
						entry.runtime(), single, manifest.showAll());
					if (outcome.result() != ProjectStateStore.LoadResult.RESTORED) return null;
					return new RestoredState(Files.getLastModifiedTime(sidecar).toMillis(), outcome.showAllSkeletons(), activeName);
				} finally {
					restored.adoptLoadedContentFrom(single.documents().get(0));
				}
			}
		} finally {
			if (restored.data != null) document.adoptLoadedContentFrom(restored);
			restored.dispose();
		}
	}

	static List<SkeletonProject.LoadedSkeleton> detachDocuments(SkeletonProject staging) {
		List<SkeletonProject.LoadedSkeleton> result = new ArrayList<>();
		try {
			List<SkeletonDocument> documents = staging.documents();
			for (int index = 0; index < documents.size(); index++) {
				SkeletonDocument detached = new SkeletonDocument();
				try {
					detached.adoptLoadedContentFrom(documents.get(index));
					result.add(new SkeletonProject.LoadedSkeleton(staging.nameAt(index), detached));
				} catch (RuntimeException | Error failure) {
					detached.dispose();
					throw failure;
				}
			}
			return List.copyOf(result);
		} catch (RuntimeException | Error failure) {
			for (SkeletonProject.LoadedSkeleton skeleton : result) skeleton.document().dispose();
			throw failure;
		}
	}

	private static Path resolveFile(Path directory, String relative) throws IOException {
		String safe = ProjectPackager.safeEntryName(relative);
		Path resolved = directory.resolve(safe).normalize();
		if (!resolved.startsWith(directory) || !Files.isRegularFile(resolved)
			|| !resolved.toRealPath().startsWith(directory.toRealPath()))
			throw new IOException("项目清单资源缺失或超出目录：" + relative);
		return resolved;
	}

	private ProjectPackageManifest() {}
}
