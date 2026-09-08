package com.openspine.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.AttachmentLoader;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.model.SkeletonDocument;

/**
 * 骨架加载：支持 Spine .spine 编辑器工程、.json（文本格式）与 .skel（二进制格式）骨架文件。
 * 图片优先从同目录 .atlas 加载；JSON 也支持从 skeleton.images 指向的 loose 图片目录动态创建图集。
 */
public class SkeletonLoader {
	private static final int LOOSE_ATLAS_DEFAULT_PAGE_SIZE = 2048;
	private static final int LOOSE_ATLAS_PADDING = 2;

	/** 尝试从文件名后缀判断格式并加载，加载失败抛 RuntimeException */
	public static SkeletonDocument load(SkeletonDocument doc, FileHandle skeletonFile) {
		if (doc == null) throw new IllegalArgumentException("Document is required");
		if (skeletonFile == null) throw new IllegalArgumentException("Skeleton file is required");
		String name = skeletonFile.name().toLowerCase(Locale.ROOT);
		boolean project = isSpineProjectFileName(name);
		boolean json = name.endsWith(".json");
		boolean binary = name.endsWith(".skel");
		if (!project && !json && !binary) {
			throw new RuntimeException("Unsupported skeleton file: " + skeletonFile.name()
				+ " (expected .spine, .json or .skel)");
		}
		if (!skeletonFile.exists() || skeletonFile.isDirectory())
			throw new RuntimeException("Skeleton file not found: " + skeletonFile.path());
		if (project) return SpineProjectImporter.load(doc, skeletonFile);
		return loadRuntime(doc, skeletonFile);
	}

	static SkeletonDocument loadRuntime(SkeletonDocument doc, FileHandle skeletonFile) {
		return loadRuntime(doc, skeletonFile, skeletonFile.parent());
	}

	static SkeletonDocument loadRuntime(SkeletonDocument doc, FileHandle skeletonFile, FileHandle resourceDirectory) {
		return loadRuntime(doc, skeletonFile, resourceDirectory, null);
	}

	/** Loads a runtime skeleton using an explicitly selected sibling atlas when supplied. */
	public static SkeletonDocument loadRuntime(SkeletonDocument doc, FileHandle skeletonFile,
		FileHandle resourceDirectory, String preferredAtlasFileName) {
		return loadRuntime(doc, skeletonFile, resourceDirectory, preferredAtlasFileName, null);
	}

	/** Restricted resource loading for MCP previews; ordinary editor loads keep their existing behavior. */
	public static SkeletonDocument loadRuntime(SkeletonDocument doc, FileHandle skeletonFile,
		FileHandle resourceDirectory, String preferredAtlasFileName, PreviewResourceGuard guard) {
		if (guard != null) {
			skeletonFile = new FileHandle(guard.file(skeletonFile.file().toPath()).toFile());
			resourceDirectory = new FileHandle(guard.directory(resourceDirectory.file().toPath()).toFile());
		}
		String name = skeletonFile.name().toLowerCase(Locale.ROOT);
		boolean json = name.endsWith(".json");
		JsonValue source = json ? new JsonReader().parse(skeletonFile) : null;
		String version = json ? jsonVersion(source) : readBinaryVersion(skeletonFile);
		requireSupportedRuntimeVersion(version, json ? "JSON" : "SKEL", skeletonFile.path());
		LoadedAtlas loadedAtlas = loadAtlas(skeletonFile, resourceDirectory, json, preferredAtlasFileName, guard);
		TextureAtlas atlas = loadedAtlas.atlas();
		try {
			SkeletonData data;
			if (json) {
				SkeletonJson loader = new SkeletonJson(atlas);
				loader.setScale(1f);
				SkeletonStructureValidator.validateJson(source);
				data = loader.readSkeletonData(source);
			} else {
				SkeletonBinary loader = new SkeletonBinary(atlas);
				loader.setScale(1f);
				data = loader.readSkeletonData(skeletonFile);
			}
			validateSkeletonData(data, skeletonFile.path());
			Skeleton skeleton = new Skeleton(data);
			doc.replaceContent(atlas, data, skeleton, skeletonFile.path(), loadedAtlas.fileName(),
				savePathFor(skeletonFile.path()), loadedAtlas.premultipliedAlpha());
			doc.resourceDirectory = resourceDirectory.file().toPath().toAbsolutePath().normalize().toString();
			return doc;
		} catch (RuntimeException e) {
			atlas.dispose();
			throw e;
		}
	}

	public static boolean isSupportedFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) return false;
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.endsWith(".spine") || lower.endsWith(".json") || lower.endsWith(".skel");
	}

	public static boolean isSpineProjectFileName(String fileName) {
		return fileName != null && !fileName.isBlank()
			&& fileName.toLowerCase(Locale.ROOT).endsWith(".spine");
	}

	/** Reads only the declared runtime version, without loading an atlas or parsing version-sensitive data. */
	public static String declaredRuntimeVersion(FileHandle skeletonFile) {
		if (skeletonFile == null) throw new IllegalArgumentException("Skeleton file is required");
		String name = skeletonFile.name().toLowerCase(Locale.ROOT);
		if (!name.endsWith(".json") && !name.endsWith(".skel"))
			throw new IllegalArgumentException("Runtime skeleton must use .json or .skel");
		if (!skeletonFile.exists() || skeletonFile.isDirectory())
			throw new RuntimeException("Skeleton file not found: " + skeletonFile.path());
		return name.endsWith(".json")
			? jsonVersion(new JsonReader().parse(skeletonFile)) : readBinaryVersion(skeletonFile);
	}

	/** Loads only skeleton structure and animation data, without opening textures or replacing a document. */
	public static SkeletonData loadAnimationSource(FileHandle skeletonFile) {
		if (skeletonFile == null) throw new IllegalArgumentException("动画源文件不能为空");
		String name = skeletonFile.name().toLowerCase(Locale.ROOT);
		boolean json = name.endsWith(".json");
		if (!json && !name.endsWith(".skel"))
			throw new IllegalArgumentException("动画导入仅支持 .json 或 .skel Runtime 数据");
		if (!skeletonFile.exists() || skeletonFile.isDirectory())
			throw new IllegalArgumentException("动画源文件不存在：" + skeletonFile.path());
		String version = declaredRuntimeVersion(skeletonFile);
		requireSupportedRuntimeVersion(version, json ? "动画 JSON" : "动画 SKEL", skeletonFile.path());
		SkeletonData data;
		if (json) {
			JsonValue source = new JsonReader().parse(skeletonFile);
			SkeletonStructureValidator.validateJson(source);
			SkeletonJson loader = new SkeletonJson(TEXTURELESS_ATTACHMENT_LOADER);
			loader.setScale(1f);
			data = loader.readSkeletonData(source);
		} else {
			SkeletonBinary loader = new SkeletonBinary(TEXTURELESS_ATTACHMENT_LOADER);
			loader.setScale(1f);
			data = loader.readSkeletonData(skeletonFile);
		}
		validateSkeletonData(data, skeletonFile.path());
		return data;
	}

	/**
	 * Returns the sibling atlas candidates for a runtime skeleton. A same-named atlas wins;
	 * otherwise all direct .atlas files are returned in stable order so the UI can ask the user
	 * instead of silently loading the wrong texture set.
	 */
	public static List<String> atlasCandidates(FileHandle skeletonFile) {
		if (skeletonFile == null) return List.of();
		FileHandle resourceDirectory = skeletonFile.parent();
		if (resourceDirectory == null || !resourceDirectory.exists()) return List.of();
		FileHandle named = resourceDirectory.child(skeletonFile.nameWithoutExtension() + ".atlas");
		if (named.exists() && !named.isDirectory()) return List.of(named.name());
		FileHandle[] files = resourceDirectory.list("atlas");
		List<FileHandle> sorted = new ArrayList<>();
		for (FileHandle file : files) if (file != null && file.exists() && !file.isDirectory()) sorted.add(file);
		sorted.sort(Comparator.comparing(FileHandle::name, String.CASE_INSENSITIVE_ORDER));
		List<String> names = new ArrayList<>(sorted.size());
		for (FileHandle file : sorted) names.add(file.name());
		return List.copyOf(names);
	}

	/** Loads a matching atlas, or packs loose JSON images when no atlas is available. */
	private static LoadedAtlas loadAtlas(FileHandle skeletonFile, FileHandle resourceDirectory, boolean json) {
		return loadAtlas(skeletonFile, resourceDirectory, json, null);
	}

	private static LoadedAtlas loadAtlas(FileHandle skeletonFile, FileHandle resourceDirectory, boolean json,
		String preferredAtlasFileName) {
		return loadAtlas(skeletonFile, resourceDirectory, json, preferredAtlasFileName, null);
	}

	private static LoadedAtlas loadAtlas(FileHandle skeletonFile, FileHandle resourceDirectory, boolean json,
		String preferredAtlasFileName, PreviewResourceGuard guard) {
		FileHandle assets = resourceDirectory == null ? skeletonFile.parent() : resourceDirectory;
		FileHandle atlasFile;
		if (preferredAtlasFileName != null && !preferredAtlasFileName.isBlank()) {
			atlasFile = assets.child(preferredAtlasFileName);
		} else {
			List<String> candidates = atlasCandidates(skeletonFile);
			if (candidates.size() > 1)
				throw new RuntimeException("发现多个候选图集，请在打开文件时选择一个：" + String.join(", ", candidates));
			atlasFile = candidates.isEmpty() ? assets.child(skeletonFile.nameWithoutExtension() + ".atlas")
				: assets.child(candidates.get(0));
		}
		if (!atlasFile.exists()) {
			if (guard != null && preferredAtlasFileName != null)
				throw new IllegalArgumentException("Requested preview atlas not found: " + preferredAtlasFileName);
			Path images = json ? resolveImagesDirectory(skeletonFile, assets)
				: resolveImagesDirectory(readBinaryImagesPath(skeletonFile), skeletonFile, assets, "SKEL");
			return loadLooseImageAtlas(images, json ? "JSON" : "SKEL", guard);
		}
		if (guard != null) atlasFile = new FileHandle(guard.file(atlasFile.file().toPath()).toFile());
		TextureAtlasData data = AtlasFiles.readData(atlasFile, atlasFile.parent(), false);
		if (guard != null) {
			for (Page page : data.getPages())
				page.textureFile = new FileHandle(guard.image(page.textureFile.file().toPath()).toFile());
		}
		boolean premultipliedAlpha = premultipliedAlpha(data);
		TextureAtlas atlas = new TextureAtlas();
		try {
			atlas.load(data);
			AtlasAlphaModes.register(data);
			if (atlas.getRegions().size == 0) throw new RuntimeException("Atlas is empty: " + atlasFile.path());
		} catch (RuntimeException | Error failure) {
			// A later page may fail after earlier textures were allocated; retain ownership until load completes.
			atlas.dispose();
			throw failure;
		}
		return new LoadedAtlas(atlas, premultipliedAlpha, atlasFile.name());
	}

	/** Loads an autosaved JSON snapshot while resolving its textures from the original project resources. */
	public static SkeletonDocument loadAutosaveSnapshot(SkeletonDocument doc, FileHandle snapshotFile,
		String filePath, String savePath, String resourceDirectory, String atlasFileName) {
		if (doc == null || snapshotFile == null || !snapshotFile.exists())
			throw new IllegalArgumentException("自动保存快照无效");
		JsonValue source = new JsonReader().parse(snapshotFile);
		requireSupportedRuntimeVersion(jsonVersion(source), "自动保存 JSON", snapshotFile.path());
		FileHandle resources = null;
		if (resourceDirectory != null && !resourceDirectory.isBlank())
			resources = new FileHandle(Path.of(resourceDirectory).toFile());
		if (resources == null || !resources.exists()) {
			String projectPath = savePath != null && !savePath.isBlank() ? savePath : filePath;
			if (projectPath != null && !projectPath.isBlank() && !projectPath.startsWith("builtin:")
				&& !projectPath.startsWith("new:")) resources = new FileHandle(Path.of(projectPath).toFile()).parent();
		}
		if (resources == null) resources = snapshotFile.parent();
		LoadedAtlas loadedAtlas;
		if (filePath != null && filePath.startsWith("builtin:")
			&& "builtin-example".equals(atlasFileName))
			loadedAtlas = new LoadedAtlas(ExampleSkeleton.createAtlas(), false, atlasFileName);
		else loadedAtlas = loadAtlas(snapshotFile, resources, true, atlasFileName);
		TextureAtlas atlas = loadedAtlas.atlas();
		try {
			SkeletonJson loader = new SkeletonJson(atlas);
			loader.setScale(1f);
			SkeletonStructureValidator.validateJson(source);
			SkeletonData data = loader.readSkeletonData(source);
			validateSkeletonData(data, snapshotFile.path());
			doc.replaceContent(atlas, data, new Skeleton(data), emptyToNull(filePath),
				loadedAtlas.fileName(), emptyToNull(savePath), loadedAtlas.premultipliedAlpha());
			doc.resourceDirectory = resources.file().toPath().toAbsolutePath().normalize().toString();
			doc.markDirty();
			return doc;
		} catch (RuntimeException error) {
			atlas.dispose();
			throw error;
		}
	}

	private static String emptyToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static LoadedAtlas loadLooseImageAtlas(FileHandle skeletonFile, FileHandle resourceDirectory) {
		Path imagesDirectory = resolveImagesDirectory(skeletonFile, resourceDirectory);
		return loadLooseImageAtlas(imagesDirectory, "JSON");
	}

	private static LoadedAtlas loadLooseBinaryImageAtlas(FileHandle skeletonFile, FileHandle resourceDirectory) {
		String images = readBinaryImagesPath(skeletonFile);
		Path imagesDirectory = resolveImagesDirectory(images, skeletonFile, resourceDirectory, "SKEL");
		return loadLooseImageAtlas(imagesDirectory, "SKEL");
	}

	static LoadedAtlas loadLooseImageAtlas(Path imagesDirectory, String format) {
		return loadLooseImageAtlas(imagesDirectory, format, null);
	}

	private static LoadedAtlas loadLooseImageAtlas(Path imagesDirectory, String format, PreviewResourceGuard guard) {
		if (imagesDirectory == null) return new LoadedAtlas(new TextureAtlas(), false, null);
		if (guard != null) imagesDirectory = guard.directory(imagesDirectory);
		if (!Files.isDirectory(imagesDirectory))
			return new LoadedAtlas(new TextureAtlas(), false, null);

		List<Path> imageFiles;
		try (Stream<Path> files = Files.walk(imagesDirectory)) {
			int[] visited = {0};
			imageFiles = files.peek(path -> {
					if (guard != null && (++visited[0] > 10_000 || Files.isSymbolicLink(path)))
						throw new IllegalArgumentException("Preview image traversal limit or symbolic link: " + path);
				}).filter(Files::isRegularFile)
				.filter(path -> AtlasPacker.isSupportedImage(path.getFileName().toString().toLowerCase(Locale.ROOT)))
				.sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER)).toList();
		} catch (IOException error) {
			throw new RuntimeException("无法读取 " + format + " 图片目录：" + imagesDirectory, error);
		}
		if (imageFiles.isEmpty()) return new LoadedAtlas(new TextureAtlas(), false, null);
		if (guard != null) for (Path image : imageFiles) guard.image(image);

		int maximumPageSize = PngFrameExporter.maxTextureSize();
		int initialPageSize = Math.min(LOOSE_ATLAS_DEFAULT_PAGE_SIZE, maximumPageSize);
		PixmapPacker packer = new PixmapPacker(initialPageSize, initialPageSize,
			Pixmap.Format.RGBA8888, LOOSE_ATLAS_PADDING, true);
		try {
			for (Path imageFile : imageFiles) {
				Pixmap pixmap = new Pixmap(new FileHandle((guard == null ? imageFile : guard.image(imageFile)).toFile()));
				try {
					packer.setPageWidth(looseAtlasPageDimension(
						packer.getPageWidth(), pixmap.getWidth(), maximumPageSize));
					packer.setPageHeight(looseAtlasPageDimension(
						packer.getPageHeight(), pixmap.getHeight(), maximumPageSize));
					packer.pack(looseRegionName(imagesDirectory, imageFile), pixmap);
				} finally {
					pixmap.dispose();
				}
			}
			TextureAtlas atlas = packer.generateTextureAtlas(TextureFilter.Linear, TextureFilter.Linear, false);
			if (atlas.getRegions().size == 0) {
				atlas.dispose();
				throw new RuntimeException("无法从 " + format + " 图片目录创建运行时图集：" + imagesDirectory);
			}
			return new LoadedAtlas(atlas, false, null);
		} catch (RuntimeException error) {
			throw new RuntimeException("加载 loose 图片失败：" + imagesDirectory, error);
		} finally {
			packer.dispose();
		}
	}

	static Path resolveImagesDirectory(FileHandle skeletonFile, FileHandle resourceDirectory) {
		try {
			JsonValue root = new JsonReader().parse(skeletonFile);
			JsonValue skeleton = root.get("skeleton");
			String images = skeleton == null ? null : skeleton.getString("images", null);
			return resolveImagesDirectory(images, skeletonFile, resourceDirectory, "JSON");
		} catch (InvalidPathException error) {
			throw new RuntimeException("JSON 的 skeleton.images 路径无效", error);
		}
	}

	static Path resolveImagesDirectory(String images, FileHandle skeletonFile,
		FileHandle resourceDirectory, String format) {
		if (images == null || images.isBlank()) return null;
		try {
			Path configured = Path.of(images.trim());
			Path base = (resourceDirectory == null ? skeletonFile.parent() : resourceDirectory)
				.file().toPath().toAbsolutePath().normalize();
			return (configured.isAbsolute() ? configured : base.resolve(configured)).toAbsolutePath().normalize();
		} catch (InvalidPathException error) {
			throw new RuntimeException(format + " 的 skeleton.images 路径无效", error);
		}
	}

	static String readBinaryImagesPath(FileHandle skeletonFile) {
		SkeletonBinary loader = new SkeletonBinary(TEXTURELESS_ATTACHMENT_LOADER);
		loader.setScale(1f);
		return loader.readSkeletonData(skeletonFile).getImagesPath();
	}

	static String jsonVersion(JsonValue root) {
		if (root == null || !root.isObject()) return null;
		JsonValue skeleton = root.get("skeleton");
		if (skeleton == null || !skeleton.isObject()) return null;
		JsonValue version = skeleton.get("spine");
		return version != null && version.isString() ? version.asString() : null;
	}

	/** Reads known Spine binary headers without allowing a version-sensitive runtime parse first. */
	static String readBinaryVersion(FileHandle skeletonFile) {
		if (skeletonFile == null) throw new IllegalArgumentException("SKEL 文件不能为空");
		BinaryVersionProbe fixedHash = probeBinaryVersion(skeletonFile, false);
		BinaryVersionProbe stringHash = probeBinaryVersion(skeletonFile, true);
		if (fixedHash.version() != null && stringHash.version() != null
			&& !fixedHash.version().equals(stringHash.version()))
			throw binaryVersionFailure(skeletonFile, new IOException("SKEL 头同时匹配多个不一致的版本格式"));
		if (fixedHash.version() != null) return fixedHash.version();
		if (stringHash.version() != null) return stringHash.version();
		if (fixedHash.missing() || stringHash.missing()) return null;

		Throwable cause = fixedHash.failure() != null ? fixedHash.failure() : stringHash.failure();
		if (cause != null && stringHash.failure() != null && cause != stringHash.failure())
			cause.addSuppressed(stringHash.failure());
		throw binaryVersionFailure(skeletonFile, cause);
	}

	private static BinaryVersionProbe probeBinaryVersion(FileHandle skeletonFile, boolean stringHash) {
		try (InputStream input = skeletonFile.read()) {
			if (stringHash) readBinaryHeaderString(input, 4096, "SKEL 哈希字段");
			else input.skipNBytes(Long.BYTES);
			String version = readBinaryHeaderString(input, 64, "SKEL 版本字段");
			if (version == null || version.isBlank()) return new BinaryVersionProbe(null, true, null);
			if (!version.matches("[0-9]+(?:\\.[0-9]+){1,3}"))
				throw new IOException("SKEL 版本字段无效");
			return new BinaryVersionProbe(version, false, null);
		} catch (IOException | RuntimeException error) {
			return new BinaryVersionProbe(null, false, error);
		}
	}

	private static String readBinaryHeaderString(InputStream input, int maximumBytes, String label) throws IOException {
		int encodedLength = readPositiveVarInt(input);
		if (encodedLength == 0) return null;
		int byteLength = encodedLength - 1;
		if (byteLength < 0 || byteLength > maximumBytes) throw new IOException(label + "长度无效");
		byte[] bytes = input.readNBytes(byteLength);
		if (bytes.length != byteLength) throw new EOFException(label + "不完整");
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static RuntimeException binaryVersionFailure(FileHandle skeletonFile, Throwable cause) {
		return new RuntimeException("无法安全读取 SKEL 版本：" + skeletonFile.path()
			+ "。OpenSpine 只支持直接读取 Spine 4.3 数据；请先使用 Spine 4.3 转换或重新导出。"
			+ "原文件不会被修改。", cause);
	}

	private record BinaryVersionProbe(String version, boolean missing, Throwable failure) {}

	private static int readPositiveVarInt(InputStream input) throws IOException {
		int result = 0;
		for (int shift = 0; shift <= 28; shift += 7) {
			int value = input.read();
			if (value < 0) throw new EOFException("SKEL 版本长度字段不完整");
			if (shift == 28 && (value & 0xf0) != 0)
				throw new IOException("SKEL 版本长度字段无效");
			result |= (value & 0x7f) << shift;
			if ((value & 0x80) == 0) return result;
		}
		throw new IOException("SKEL 版本长度字段无效");
	}

	static void requireSupportedRuntimeVersion(String version, String format, String source) {
		if (!SpineVersionPolicy.requiresConversion(version)) return;
		String label = version == null || version.isBlank() ? "未声明版本" : "Spine " + version.trim();
		throw new RuntimeException("不能直接打开 " + label + " 的 " + format + " 数据：" + source
			+ "。OpenSpine 只支持直接读取 Spine 4.3 数据；请先使用 Spine 4.3 转换或重新导出。"
			+ "原文件不会被修改。");
	}

	static String looseRegionName(Path imagesDirectory, Path imageFile) {
		String relative = imagesDirectory.toAbsolutePath().normalize()
			.relativize(imageFile.toAbsolutePath().normalize()).toString().replace('\\', '/');
		int dot = relative.lastIndexOf('.');
		return dot <= 0 ? relative : relative.substring(0, dot);
	}

	static void validateSkeletonData(SkeletonData data, String source) {
		if (data == null || data.getBones().size == 0)
			throw new RuntimeException("骨架数据至少需要一个骨骼：" + source);
		SkeletonStructureValidator.validate(data);
	}

	static int looseAtlasPageDimension(int currentPageSize, int imageSize, int maximumPageSize) {
		if (currentPageSize <= 0 || imageSize <= 0 || maximumPageSize <= 0)
			throw new IllegalArgumentException("Loose 图集尺寸参数无效");
		long required = imageSize + LOOSE_ATLAS_PADDING * 2L;
		if (required > maximumPageSize)
			throw new IllegalArgumentException("Loose PNG 尺寸 " + imageSize
				+ " 加上图集边距后超过 GPU 纹理限制 " + maximumPageSize);
		int pageSize = Math.min(currentPageSize, maximumPageSize);
		while (pageSize < required) {
			if (pageSize > maximumPageSize / 2) return maximumPageSize;
			pageSize *= 2;
		}
		return pageSize;
	}

	static boolean premultipliedAlpha(TextureAtlasData data) {
		if (data == null) throw new IllegalArgumentException("Atlas data is required");
		if (data.getPages().isEmpty()) return false;
		return data.getPages().first().pma;
	}

	static String savePathFor(String skeletonPath) {
		if (skeletonPath == null) return null;
		String lower = skeletonPath.toLowerCase(Locale.ROOT);
		return lower.endsWith(".json") || lower.endsWith(".skel") ? skeletonPath : null;
	}

	/** 从内置 JSON 字符串 + 程序生成的图集加载（用于内置示例骨架） */
	public static SkeletonDocument loadFromJsonString(SkeletonDocument doc, String json, TextureAtlas atlas) {
		try {
			JsonValue root = new JsonReader().parse(json);
			requireSupportedRuntimeVersion(jsonVersion(root), "内置 JSON", "builtin:example");
			SkeletonStructureValidator.validateJson(root);
			SkeletonJson loader = new SkeletonJson(atlas);
			loader.setScale(1f);
			SkeletonData data = loader.readSkeletonData(root);
			doc.replaceContent(atlas, data, new Skeleton(data), "builtin:example", "builtin-example", null);
			return doc;
		} catch (RuntimeException e) {
			atlas.dispose();
			throw e;
		}
	}

	static record LoadedAtlas(TextureAtlas atlas, boolean premultipliedAlpha, String fileName) {}

	private static final AttachmentLoader TEXTURELESS_ATTACHMENT_LOADER = new AttachmentLoader() {
		@Override public RegionAttachment newRegionAttachment(Skin skin, String placeholder, String name,
			String path, Sequence sequence) { return new RegionAttachment(name, sequence); }
		@Override public MeshAttachment newMeshAttachment(Skin skin, String placeholder, String name,
			String path, Sequence sequence) { return new MeshAttachment(name, sequence); }
		@Override public BoundingBoxAttachment newBoundingBoxAttachment(Skin skin, String placeholder,
			String name) { return new BoundingBoxAttachment(name); }
		@Override public ClippingAttachment newClippingAttachment(Skin skin, String placeholder,
			String name) { return new ClippingAttachment(name); }
		@Override public PathAttachment newPathAttachment(Skin skin, String placeholder,
			String name) { return new PathAttachment(name); }
		@Override public PointAttachment newPointAttachment(Skin skin, String placeholder,
			String name) { return new PointAttachment(name); }
	};

	private SkeletonLoader() {}
}
