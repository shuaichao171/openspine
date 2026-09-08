package com.openspine.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.esotericsoftware.spine.EventData;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Writes the current skeleton plus its external atlas or loose image resources to an atomic project ZIP. */
public final class ProjectPackager {
	public record PackageResult(int fileCount, String skeletonEntry) {}
	public record ProjectPackageResult(int fileCount, int skeletonCount, String manifestEntry) {}
	private record LooseImages(Path directory, String entryPrefix) {}
	private record AudioResource(Path source, String entryName) {}

	public static ProjectPackageResult write(SkeletonProject project, boolean showAllSkeletons, Path target)
		throws IOException {
		return write(project, showAllSkeletons, target, new CancellationToken());
	}

	/** Captures all live documents and editor state without changing their save paths or source projects. */
	public static ProjectPackageResult write(SkeletonProject project, boolean showAllSkeletons, Path target,
		CancellationToken cancellation) throws IOException {
		if (project == null || project.size() == 0) throw new IllegalArgumentException("没有可打包的项目");
		if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
		cancellation.checkCancelled();
		if (target == null) throw new IllegalArgumentException("打包路径不能为空");
		Path absoluteTarget = target.toAbsolutePath().normalize();
		if (!absoluteTarget.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
			throw new IllegalArgumentException("项目打包路径必须以 .zip 结尾");
		Path parent = absoluteTarget.getParent();
		if (parent == null) throw new IllegalArgumentException("打包路径缺少父目录");
		List<SkeletonDocument> documents = project.documents();
		String rawState = ProjectStateStore.snapshotEditorState(project, showAllSkeletons);
		String state = packagedProjectState(rawState, documents);
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp");
		int count = 0;
		try {
			Set<String> entries = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			List<ProjectPackageManifest.SkeletonEntry> skeletons = new ArrayList<>();
			Set<Path> originalProjects = new java.util.HashSet<>();
			try (OutputStream output = Files.newOutputStream(temporary);
				ZipOutputStream zip = new ZipOutputStream(output)) {
				for (int index = 0; index < documents.size(); index++) {
					cancellation.checkCancelled();
					SkeletonDocument document = documents.get(index);
					String prefix = String.format(Locale.ROOT, "skeletons/%03d/", index + 1);
					String skeletonName = skeletonEntryName(document);
					Path atlas = externalAtlasPath(document);
					Path psdSource = externalPsdSource(document);
					String psdEntry = psdSource == null ? null : psdEntryName(psdSource);
					LooseImages images = atlas == null ? externalLooseImages(document) : null;
					if (images != null && skeletonName.toLowerCase(Locale.ROOT).endsWith(".skel"))
						throw new IllegalArgumentException("二进制 .skel 项目必须使用 .atlas，不能只打包 loose 图片：" + project.nameAt(index));
					if (atlas == null && images == null && document.atlas != null
						&& document.atlas.getRegions().notEmpty())
						throw new IllegalArgumentException("当前项目使用内存图集，缺少可打包的原始图片：" + project.nameAt(index));
					byte[] bytes = skeletonName.toLowerCase(Locale.ROOT).endsWith(".skel")
						? SkeletonBinaryExporter.toBytes(document, cancellation)
						: SkeletonExporter.toJson(document, cancellation).getBytes(StandardCharsets.UTF_8);
					writeBytes(zip, entries, prefix + skeletonName, bytes, cancellation);
					writeBytes(zip, entries, prefix + skeletonName + ".openspine.json",
						skeletonState(state, index, skeletonName, bytes, psdEntry), cancellation);
					count += 2;
					count += writeResources(zip, entries, prefix, atlas, images,
						externalAudioResources(document), psdSource, cancellation);
					skeletons.add(new ProjectPackageManifest.SkeletonEntry(project.nameAt(index),
						prefix + skeletonName, atlas == null ? "" : atlas.getFileName().toString()));
					Path original = externalSpineProjectPath(document);
					if (original != null && originalProjects.add(original.toRealPath())) {
						writeFile(zip, entries, String.format(Locale.ROOT, "originals/%03d/", originalProjects.size())
							+ original.getFileName(), original, cancellation);
						count++;
					}
				}
				writeBytes(zip, entries, ProjectPackageManifest.EDITOR_STATE,
					state.getBytes(StandardCharsets.UTF_8), cancellation);
				writeBytes(zip, entries, ProjectPackageManifest.FILE_NAME,
					ProjectPackageManifest.serialize(skeletons, project.activeIndex(), showAllSkeletons)
						.getBytes(StandardCharsets.UTF_8), cancellation);
				count += 2;
			}
			cancellation.checkCancelled();
			cancellation.commit(() -> moveAtomic(temporary, absoluteTarget));
		} finally {
			Files.deleteIfExists(temporary);
		}
		return new ProjectPackageResult(count, documents.size(), ProjectPackageManifest.FILE_NAME);
	}

	private static byte[] skeletonState(String projectState, int index, String name, byte[] skeletonBytes,
		String psdEntry) {
		JsonValue state = new JsonReader().parse(projectState);
		JsonValue skeleton = state.get("skeletons").get(index);
		setPsdSourcePath(skeleton, psdEntry);
		skeleton.remove();
		// A runtime file opens as one document using its filename as the project tab name.
		skeleton.get("name").set(name.substring(0, name.lastIndexOf('.')));
		state.remove("skeletons");
		JsonValue skeletons = new JsonValue(JsonValue.ValueType.array);
		skeletons.addChild(skeleton);
		state.addChild("skeletons", skeletons);
		state.remove("activeSkeleton");
		state.addChild("activeSkeleton", new JsonValue(0L));
		JsonValue source = new JsonValue(JsonValue.ValueType.object);
		source.addChild("fileName", new JsonValue(name));
		source.addChild("size", new JsonValue((long)skeletonBytes.length));
		source.addChild("modifiedMillis", new JsonValue(0L));
		try {
			source.addChild("sha256", new JsonValue(java.util.HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(skeletonBytes))));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
		state.remove("source");
		state.addChild("source", source);
		return state.toJson(JsonWriter.OutputType.json).getBytes(StandardCharsets.UTF_8);
	}

	private static String packagedProjectState(String projectState, List<SkeletonDocument> documents)
		throws IOException {
		JsonValue state = new JsonReader().parse(projectState);
		JsonValue skeletons = state.get("skeletons");
		for (int index = 0; index < documents.size(); index++) {
			Path source = externalPsdSource(documents.get(index));
			setPsdSourcePath(skeletons.get(index), source == null ? null
				: String.format(Locale.ROOT, "skeletons/%03d/", index + 1) + psdEntryName(source));
		}
		return state.prettyPrint(JsonWriter.OutputType.json, 100) + System.lineSeparator();
	}

	private static void setPsdSourcePath(JsonValue skeleton, String path) {
		JsonValue psdSource = skeleton == null ? null : skeleton.get("psdSource");
		if (psdSource == null) return;
		if (path == null || path.isBlank()) {
			psdSource.remove();
			return;
		}
		JsonValue value = psdSource.get("path");
		if (value == null) psdSource.addChild("path", new JsonValue(path));
		else value.set(path);
	}

	private static int writeResources(ZipOutputStream zip, Set<String> entries, String prefix,
		Path atlasPath, LooseImages images, List<AudioResource> audio, Path psdSource,
		CancellationToken cancellation)
		throws IOException {
		Map<String, Path> resources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		resources.putAll(collectResources(atlasPath, images, audio, cancellation));
		if (psdSource != null) addResource(resources, psdEntryName(psdSource), psdSource);
		for (var resource : resources.entrySet())
			writeFile(zip, entries, prefix + resource.getKey(), resource.getValue(), cancellation);
		return resources.size();
	}

	/** The exact external files copied into a document's package directory, without loading textures. */
	static Map<String, Path> resourceFiles(SkeletonDocument document, CancellationToken cancellation) throws IOException {
		return resourceFiles(document, cancellation, new java.util.HashSet<>());
	}

	static Map<String, Path> resourceFiles(SkeletonDocument document, CancellationToken cancellation,
		Set<Path> directories) throws IOException {
		Path atlas = externalAtlasPath(document);
		Map<String, Path> resources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		resources.putAll(collectResources(atlas, atlas == null ? externalLooseImages(document) : null,
			externalAudioResources(document), cancellation, directories));
		Path psdSource = externalPsdSource(document);
		if (psdSource != null) addResource(resources, psdEntryName(psdSource), psdSource);
		return java.util.Collections.unmodifiableMap(resources);
	}

	private static Map<String, Path> collectResources(Path atlasPath, LooseImages images,
		List<AudioResource> audio, CancellationToken cancellation) throws IOException {
		return collectResources(atlasPath, images, audio, cancellation, new java.util.HashSet<>());
	}

	private static Map<String, Path> collectResources(Path atlasPath, LooseImages images,
		List<AudioResource> audio, CancellationToken cancellation, Set<Path> directories) throws IOException {
		Map<String, Path> resources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (AudioResource resource : audio) {
			cancellation.checkCancelled();
			addResource(resources, resource.entryName(), resource.source());
		}
		if (atlasPath != null) {
			cancellation.checkCancelled();
			if (!Files.isRegularFile(atlasPath)) throw new IOException("Atlas 不存在：" + atlasPath);
			addResource(resources, atlasPath.getFileName().toString(), atlasPath);
			TextureAtlasData atlas = AtlasFiles.readData(new FileHandle(atlasPath.toFile()),
				new FileHandle(atlasPath.getParent().toFile()), false);
			for (Page page : atlas.getPages()) {
				cancellation.checkCancelled();
				if (page.textureFile == null || !page.textureFile.exists())
					throw new IOException("图集图片不存在：" + page.name);
				Path source = page.textureFile.file().toPath().toAbsolutePath().normalize();
				requireResourceInside(source, atlasPath.getParent());
				addResource(resources, page.name, source);
			}
		} else if (images != null) {
			for (Path file : looseImageFiles(images.directory(), cancellation, directories)) {
				String relative = portablePath(images.directory().relativize(file));
				String entry = images.entryPrefix().isEmpty() ? relative : images.entryPrefix() + "/" + relative;
				addResource(resources, entry, file);
			}
		}
		return java.util.Collections.unmodifiableMap(resources);
	}

	private static void addResource(Map<String, Path> resources, String name, Path source) {
		String safe = safeEntryName(name);
		if (resources.putIfAbsent(safe, source.toAbsolutePath().normalize()) != null)
			throw new IllegalArgumentException("压缩包资源文件名重复：" + safe);
	}

	private static void requireResourceInside(Path source, Path directory) throws IOException {
		if (!Files.isRegularFile(source) || !source.toRealPath().startsWith(directory.toRealPath()))
			throw new IOException("资源文件缺失或通过链接超出资源目录：" + source);
	}

	public static PackageResult write(SkeletonDocument document, Path target) throws IOException {
		return write(document, target, new CancellationToken());
	}

	public static PackageResult write(SkeletonDocument document, Path target, CancellationToken cancellation)
		throws IOException {
		if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
		cancellation.checkCancelled();
		if (document == null || document.data == null || document.skeleton == null)
			throw new IllegalArgumentException("没有可打包的骨架数据");
		if (target == null) throw new IllegalArgumentException("打包路径不能为空");
		Path absoluteTarget = target.toAbsolutePath().normalize();
		if (!absoluteTarget.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
			throw new IllegalArgumentException("项目打包路径必须以 .zip 结尾");
		Path parent = absoluteTarget.getParent();
		if (parent == null) throw new IllegalArgumentException("项目打包路径缺少父目录");
		Files.createDirectories(parent);

		String skeletonEntry = skeletonEntryName(document);
		Path atlasPath = externalAtlasPath(document);
		LooseImages looseImages = atlasPath == null ? externalLooseImages(document) : null;
		List<AudioResource> audioResources = externalAudioResources(document);
		if (looseImages != null && skeletonEntry.toLowerCase(Locale.ROOT).endsWith(".skel"))
			throw new IllegalArgumentException("二进制 .skel 项目必须使用 .atlas，不能只打包 loose 图片");
		if (atlasPath == null && looseImages == null
			&& document.atlas != null && document.atlas.getRegions().notEmpty())
			throw new IllegalArgumentException("当前项目使用内存图集，无法打包缺失的原始图片资源");

		Path temporary = Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp");
		int fileCount;
		try {
			fileCount = writeZip(document, temporary, skeletonEntry, atlasPath, looseImages, audioResources,
				cancellation);
			cancellation.checkCancelled();
			cancellation.commit(() -> moveAtomic(temporary, absoluteTarget));
		} finally {
			Files.deleteIfExists(temporary);
		}
		return new PackageResult(fileCount, skeletonEntry);
	}

	private static int writeZip(SkeletonDocument document, Path target, String skeletonEntry,
		Path atlasPath, LooseImages looseImages, List<AudioResource> audioResources,
		CancellationToken cancellation) throws IOException {
		// The application is packaged for Windows, where differently cased paths collide on extraction.
		Set<String> entries = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		int count = 0;
		try (OutputStream stream = Files.newOutputStream(target); ZipOutputStream zip = new ZipOutputStream(stream)) {
			cancellation.checkCancelled();
			byte[] skeletonBytes = skeletonEntry.toLowerCase(Locale.ROOT).endsWith(".skel")
				? SkeletonBinaryExporter.toBytes(document, cancellation)
				: SkeletonExporter.toJson(document, cancellation).getBytes(StandardCharsets.UTF_8);
			writeBytes(zip, entries, skeletonEntry, skeletonBytes, cancellation);
			count++;
			Path spineProject = externalSpineProjectPath(document);
			if (spineProject != null) {
				writeFile(zip, entries, safeEntryName(spineProject.getFileName().toString()), spineProject,
					cancellation);
				count++;
				Path sidecar = ProjectStateStore.sidecarPath(spineProject);
				if (Files.isRegularFile(sidecar)) {
					writeFile(zip, entries, safeEntryName(sidecar.getFileName().toString()), sidecar,
						cancellation);
					count++;
				}
			}
			count += writeResources(zip, entries, "", atlasPath, looseImages, audioResources,
				null, cancellation);
		}
		return count;
	}

	static String skeletonEntryName(SkeletonDocument document) {
		String associated = document.savePath;
		if (associated == null || associated.isBlank()) associated = document.filePath;
		String name = associated == null || associated.isBlank() || associated.startsWith("builtin:")
			|| associated.startsWith("new:") ? document.data.getName() : Path.of(associated).getFileName().toString();
		if (name == null || name.isBlank()) name = "skeleton";
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".spine")) {
			name = name.substring(0, name.length() - ".spine".length());
			if (name.isBlank()) name = "skeleton";
			lower = name.toLowerCase(Locale.ROOT);
		}
		if (!lower.endsWith(".json") && !lower.endsWith(".skel")) name += ".json";
		return safeEntryName(name);
	}

	static Path externalAtlasPath(SkeletonDocument document) {
		if (document == null || document.atlasFileName == null || document.atlasFileName.isBlank()) return null;
		Path parent = null;
		if (document.resourceDirectory != null && !document.resourceDirectory.isBlank()) {
			try { parent = Path.of(document.resourceDirectory).toAbsolutePath().normalize(); }
			catch (InvalidPathException ignored) { return null; }
		}
		if (parent == null && document.filePath != null && !document.filePath.isBlank()
			&& !document.filePath.startsWith("builtin:") && !document.filePath.startsWith("new:")) {
			try {
				Path source = Path.of(document.filePath).toAbsolutePath().normalize();
				parent = source.getParent();
			} catch (InvalidPathException ignored) {
				// Fall through to the explicitly resolved resource directory below.
			}
		}
		if (parent == null) return null;
		try {
			Path configured = Path.of(document.atlasFileName.trim());
			return (configured.isAbsolute() ? configured : parent.resolve(configured)).normalize();
		} catch (InvalidPathException ignored) {
			return null;
		}
	}

	static Path externalPsdSource(SkeletonDocument document) throws IOException {
		if (document == null || document.psdSource() == null) return null;
		String configuredValue = document.psdSource().sourcePath();
		try {
			Path configured = Path.of(configuredValue);
			if (!configured.isAbsolute()) {
				if (document.resourceDirectory == null || document.resourceDirectory.isBlank())
					throw new IOException("PSD 来源使用相对路径但项目没有资源目录：" + configuredValue);
				configured = Path.of(document.resourceDirectory).resolve(configured);
			}
			Path source = configured.toAbsolutePath().normalize();
			Path name = source.getFileName();
			if (name == null || !name.toString().toLowerCase(Locale.ROOT).endsWith(".psd"))
				throw new IOException("PSD 来源文件扩展名无效：" + source);
			if (!Files.isRegularFile(source)) throw new IOException("PSD 来源文件不存在：" + source);
			return source;
		} catch (InvalidPathException invalid) {
			throw new IOException("PSD 来源路径无效：" + configuredValue, invalid);
		}
	}

	private static String psdEntryName(Path source) {
		return safeEntryName("psd/" + source.getFileName());
	}

	static Path externalSpineProjectPath(SkeletonDocument document) {
		if (document == null || document.filePath == null || document.filePath.isBlank()) return null;
		try {
			Path source = Path.of(document.filePath).toAbsolutePath().normalize();
			return source.getFileName() != null
				&& source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".spine")
				&& Files.isRegularFile(source) ? source : null;
		} catch (InvalidPathException error) {
			return null;
		}
	}

	private static LooseImages externalLooseImages(SkeletonDocument document) {
		if (document == null || document.data == null) return null;
		String configuredValue = document.data.getImagesPath();
		if (configuredValue == null || configuredValue.isBlank()) return null;
		try {
			Path configured = Path.of(configuredValue.trim());
			if (configured.isAbsolute())
				throw new IllegalArgumentException("打包要求 skeleton.images 使用相对路径：" + configuredValue);
			Path normalized = configured.normalize();
			String prefix = normalized.toString().replace('\\', '/');
			if (prefix.equals("..") || prefix.startsWith("../"))
				throw new IllegalArgumentException("skeleton.images 不能指向项目目录之外：" + configuredValue);
			if (prefix.equals(".")) prefix = "";
			Path parent = externalResourceDirectory(document);
			if (parent == null) return null;
			return new LooseImages(parent.resolve(normalized).normalize(), prefix);
		} catch (InvalidPathException error) {
			throw new IllegalArgumentException("skeleton.images 路径无效：" + configuredValue, error);
		}
	}

	private static List<AudioResource> externalAudioResources(SkeletonDocument document) throws IOException {
		boolean hasAudio = false;
		for (EventData event : document.data.getEvents()) {
			if (event.getAudioPath() != null && !event.getAudioPath().isBlank()) {
				hasAudio = true;
				break;
			}
		}
		if (!hasAudio) return List.of();

		Path resourceDirectory = externalResourceDirectory(document);
		if (resourceDirectory == null)
			throw new IllegalArgumentException("事件音频缺少可解析的项目资源目录");
		if (!Files.isDirectory(resourceDirectory))
			throw new IOException("项目资源目录不存在：" + resourceDirectory);
		Path realResourceDirectory = resourceDirectory.toRealPath();

		String configuredAudio = document.data.getAudioPath();
		Path audioRelative = configuredAudio == null || configuredAudio.isBlank()
			? Path.of("") : safeRelativeResourcePath(configuredAudio, "skeleton.audio", true);
		Path audioDirectory = resourceDirectory.resolve(audioRelative).normalize();
		if (!audioDirectory.startsWith(resourceDirectory))
			throw new IllegalArgumentException("skeleton.audio 不能指向项目资源目录之外：" + configuredAudio);
		if (!Files.isDirectory(audioDirectory)) throw new IOException("音频目录不存在：" + audioDirectory);
		Path realAudioDirectory = audioDirectory.toRealPath();
		if (!realAudioDirectory.startsWith(realResourceDirectory))
			throw new IllegalArgumentException("skeleton.audio 不能通过链接指向项目资源目录之外：" + configuredAudio);

		String entryPrefix = portablePath(audioRelative);
		Map<String, AudioResource> resources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (EventData event : document.data.getEvents()) {
			String configuredEvent = event.getAudioPath();
			if (configuredEvent == null || configuredEvent.isBlank()) continue;
			Path eventRelative = safeRelativeResourcePath(configuredEvent,
				"事件 " + event.getName() + " 的 audio", false);
			Path source = audioDirectory.resolve(eventRelative).normalize();
			if (!source.startsWith(audioDirectory))
				throw new IllegalArgumentException("事件音频不能指向音频目录之外：" + configuredEvent);
			if (!Files.isRegularFile(source)) throw new IOException("事件音频文件不存在：" + source);
			Path realSource = source.toRealPath();
			if (!realSource.startsWith(realAudioDirectory))
				throw new IllegalArgumentException("事件音频不能通过链接指向音频目录之外：" + configuredEvent);
			String eventEntry = portablePath(eventRelative);
			String entryName = safeEntryName(entryPrefix.isEmpty() ? eventEntry : entryPrefix + "/" + eventEntry);
			AudioResource resource = new AudioResource(realSource, entryName);
			AudioResource previous = resources.putIfAbsent(entryName, resource);
			if (previous != null && (!previous.entryName().equals(entryName) || !Files.isSameFile(previous.source(), realSource)))
				throw new IllegalArgumentException("事件音频在压缩包中发生大小写重名：" + entryName);
		}
		return List.copyOf(resources.values());
	}

	private static Path externalResourceDirectory(SkeletonDocument document) {
		String configured = document.resourceDirectory;
		if (configured != null && !configured.isBlank()) {
			try {
				return Path.of(configured).toAbsolutePath().normalize();
			} catch (InvalidPathException error) {
				throw new IllegalArgumentException("项目资源目录无效：" + configured, error);
			}
		}
		String sourcePath = document.filePath;
		if (sourcePath == null || sourcePath.isBlank() || sourcePath.startsWith("builtin:")
			|| sourcePath.startsWith("new:")) return null;
		try {
			Path source = Path.of(sourcePath).toAbsolutePath().normalize();
			return source.getParent();
		} catch (InvalidPathException error) {
			throw new IllegalArgumentException("项目来源路径无效：" + sourcePath, error);
		}
	}

	private static Path safeRelativeResourcePath(String value, String label, boolean allowCurrentDirectory) {
		String portable = value == null ? "" : value.trim().replace('\\', '/');
		if (portable.isEmpty()) throw new IllegalArgumentException(label + " 路径不能为空");
		if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*"))
			throw new IllegalArgumentException(label + " 必须使用项目内相对路径：" + value);
		for (String segment : portable.split("/", -1))
			if (segment.equals(".."))
				throw new IllegalArgumentException(label + " 不能包含 .. 路径：" + value);
		try {
			Path normalized = Path.of(portable).normalize();
			if (normalized.isAbsolute() || !allowCurrentDirectory && portablePath(normalized).isEmpty())
				throw new IllegalArgumentException(label + " 必须指向项目内文件：" + value);
			return normalized;
		} catch (InvalidPathException error) {
			throw new IllegalArgumentException(label + " 路径无效：" + value, error);
		}
	}

	private static String portablePath(Path path) {
		String value = path == null ? "" : path.normalize().toString().replace('\\', '/');
		return value.equals(".") ? "" : value;
	}

	private static List<Path> looseImageFiles(Path directory, CancellationToken cancellation, Set<Path> directories) throws IOException {
		if (!Files.isDirectory(directory)) throw new IOException("JSON 图片目录不存在：" + directory);
		cancellation.checkCancelled();
		List<Path> files = new ArrayList<>();
		Files.walkFileTree(directory, new SimpleFileVisitor<>() {
			@Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) {
				cancellation.checkCancelled();
				// Root-relative loose images must never collect old or currently staged native generations.
				if (!path.equals(directory) && NativeProjectStore.GENERATION_DIRECTORY.equalsIgnoreCase(path.getFileName().toString()))
					return FileVisitResult.SKIP_SUBTREE;
				directories.add(path);
				return FileVisitResult.CONTINUE;
			}
			@Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
				cancellation.checkCancelled();
				if (AtlasPacker.isSupportedImage(path.getFileName().toString().toLowerCase(Locale.ROOT))) {
					requireResourceInside(path, directory);
					files.add(path);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		cancellation.checkCancelled();
		if (files.isEmpty()) throw new IOException("JSON 图片目录中没有 PNG/JPG/JPEG 文件：" + directory);
		files.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(files);
	}

	static String safeEntryName(String name) {
		if (name == null || name.isBlank()) throw new IllegalArgumentException("压缩包文件名不能为空");
		String normalized = name.replace('\\', '/');
		if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*"))
			throw new IllegalArgumentException("压缩包文件名不能是绝对路径：" + name);
		for (String segment : normalized.split("/", -1)) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
				throw new IllegalArgumentException("压缩包文件名包含不安全路径：" + name);
		}
		return normalized;
	}

	private static void writeBytes(ZipOutputStream zip, Set<String> entries, String name, byte[] bytes,
		CancellationToken cancellation) throws IOException {
		ZipEntry entry = newEntry(entries, name);
		zip.putNextEntry(entry);
		for (int offset = 0; offset < bytes.length; offset += 65536) {
			cancellation.checkCancelled();
			zip.write(bytes, offset, Math.min(65536, bytes.length - offset));
		}
		zip.closeEntry();
	}

	private static void writeFile(ZipOutputStream zip, Set<String> entries, String name, Path source,
		CancellationToken cancellation) throws IOException {
		ZipEntry entry = newEntry(entries, name);
		zip.putNextEntry(entry);
		byte[] buffer = new byte[65536];
		try (InputStream input = Files.newInputStream(source)) {
			while (true) {
				cancellation.checkCancelled();
				int count = input.read(buffer);
				if (count < 0) break;
				zip.write(buffer, 0, count);
			}
		}
		zip.closeEntry();
	}

	private static ZipEntry newEntry(Set<String> entries, String name) {
		String safeName = safeEntryName(name);
		if (!entries.add(safeName)) throw new IllegalArgumentException("压缩包文件名重复：" + safeName);
		ZipEntry entry = new ZipEntry(safeName);
		entry.setTime(0L);
		return entry;
	}

	private static void moveAtomic(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private ProjectPackager() {}
}
