package com.openspine.io;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Prepares fresh image resources without re-reading or changing authored skeleton data. */
public final class ImageReload {
	/** Must run on the GL thread. The caller owns the candidate until release transfers ownership. */
	public static Prepared prepare(SkeletonDocument document) {
		Source source = source(document);
		TextureAtlas candidate = null;
		try {
			boolean premultipliedAlpha;
			if (source.atlasFile() != null) {
				FileHandle file = new FileHandle(source.atlasFile().toFile());
				TextureAtlasData data = AtlasFiles.readData(file, file.parent(), false);
				candidate = new TextureAtlas();
				candidate.load(data);
				AtlasAlphaModes.register(data);
				premultipliedAlpha = SkeletonLoader.premultipliedAlpha(data);
			} else {
				SkeletonLoader.LoadedAtlas loaded = SkeletonLoader.loadLooseImageAtlas(source.imagesDirectory(), "当前骨架");
				candidate = loaded.atlas();
				premultipliedAlpha = loaded.premultipliedAlpha();
			}
			if (candidate.getRegions().isEmpty())
				throw new IllegalArgumentException("图片来源中没有可加载的图片区域：" + source.description());
			String blockReason = new SkeletonEditor(document).getAtlasReplacementBlockReason(candidate);
			if (blockReason != null) throw new IllegalArgumentException(blockReason);
			return new Prepared(candidate, source.atlasFileName(), source.resourceDirectory(),
				premultipliedAlpha, source.description());
		} catch (RuntimeException | Error failure) {
			if (candidate != null) {
				try { candidate.dispose(); }
				catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
			}
			throw failure;
		}
	}

	/** Resolves the live document's paths, including unsaved image-directory changes. No GL access. */
	static Source source(SkeletonDocument document) {
		if (document == null || document.data == null)
			throw new IllegalArgumentException("请先打开骨架，再重新加载图片");
		try {
			Path base = resourceBase(document);
			String atlasName = document.atlasFileName;
			if (atlasName != null && !atlasName.isBlank() && !atlasName.equals("builtin-example")) {
				Path atlas = resolve(Path.of(atlasName.trim()), base, "图集");
				if (!Files.isRegularFile(atlas)) throw new IllegalArgumentException("当前图集文件不存在：" + atlas);
				return new Source(atlas, null, atlasName, document.resourceDirectory);
			}
			String images = document.data.getImagesPath();
			if (images == null || images.isBlank())
				throw new IllegalArgumentException("当前骨架没有外部图片来源；请先设置图片目录或加载外部图集");
			Path directory = resolve(Path.of(images.trim()), base, "图片目录");
			if (!Files.isDirectory(directory)) throw new IllegalArgumentException("当前图片目录不存在：" + directory);
			return new Source(null, directory, null, document.resourceDirectory);
		} catch (InvalidPathException failure) {
			throw new IllegalArgumentException("当前骨架的图片来源路径无效", failure);
		}
	}

	private static Path resourceBase(SkeletonDocument document) {
		if (document.resourceDirectory != null && !document.resourceDirectory.isBlank())
			return Path.of(document.resourceDirectory).toAbsolutePath().normalize();
		if (document.filePath != null && !document.filePath.isBlank()
			&& !document.filePath.startsWith("builtin:") && !document.filePath.startsWith("new:"))
			return Path.of(document.filePath).toAbsolutePath().normalize().getParent();
		return null;
	}

	private static Path resolve(Path configured, Path base, String label) {
		if (!configured.isAbsolute() && base == null)
			throw new IllegalArgumentException(label + "使用相对路径，但当前骨架没有外部资源目录");
		return (configured.isAbsolute() ? configured : base.resolve(configured)).toAbsolutePath().normalize();
	}

	static record Source(Path atlasFile, Path imagesDirectory, String atlasFileName, String resourceDirectory) {
		String description() { return (atlasFile != null ? atlasFile : imagesDirectory).toString(); }
	}

	public static final class Prepared implements AutoCloseable {
		private final TextureAtlas atlas;
		private final String atlasFileName, resourceDirectory, sourceDescription;
		private final boolean premultipliedAlpha;
		private boolean owned = true;

		Prepared(TextureAtlas atlas, String atlasFileName, String resourceDirectory,
			boolean premultipliedAlpha, String sourceDescription) {
			this.atlas = atlas;
			this.atlasFileName = atlasFileName;
			this.resourceDirectory = resourceDirectory;
			this.premultipliedAlpha = premultipliedAlpha;
			this.sourceDescription = sourceDescription;
		}

		public TextureAtlas atlas() { return atlas; }
		public String atlasFileName() { return atlasFileName; }
		public String resourceDirectory() { return resourceDirectory; }
		public boolean premultipliedAlpha() { return premultipliedAlpha; }
		public String sourceDescription() { return sourceDescription; }

		/** Call only after the document has successfully adopted and retained this atlas. */
		public TextureAtlas release() {
			if (!owned) throw new IllegalStateException("候选图片资源已释放或转移");
			owned = false;
			return atlas;
		}

		@Override public void close() {
			if (!owned) return;
			owned = false;
			atlas.dispose();
		}
	}

	private ImageReload() {}
}
