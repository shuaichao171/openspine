package com.openspine.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Locale;
import java.util.stream.Stream;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;

/** Small, deterministic atlas writer used by the image-import workflow. */
public final class AtlasPacker {
	private record PreparedPixmap(Pixmap pixmap, int originalWidth, int originalHeight,
		int offsetX, int offsetY) { }

	private record RegionMeta(String canonical, int originalWidth, int originalHeight,
		int offsetX, int offsetY) { }

	public record PackResult(Path atlas, List<Path> pages, int regionCount, boolean premultipliedAlpha) {
		public PackResult(Path atlas, List<Path> pages, int regionCount) {
			this(atlas, pages, regionCount, false);
		}
	}

	/** User-selectable atlas page settings. Values are deliberately limited to libGDX's
	 * deterministic writer options so generated descriptors remain valid Spine atlases. */
	public record Settings(int pageWidth, int pageHeight, int padding, boolean duplicateBorder,
		Filter minFilter, Filter magFilter, Repeat repeat, boolean stripWhitespace,
		float scale, boolean premultipliedAlpha, boolean alias) {
		public enum Filter {
			Nearest("Nearest"), Linear("Linear"), MipMap("MipMap"),
			MipMapNearestNearest("MipMapNearestNearest"), MipMapLinearNearest("MipMapLinearNearest"),
			MipMapNearestLinear("MipMapNearestLinear"), MipMapLinearLinear("MipMapLinearLinear");

			private final String descriptor;
			Filter(String descriptor) { this.descriptor = descriptor; }
			@Override public String toString() { return descriptor; }
		}

		public enum Repeat {
			NONE("none"), X("x"), Y("y"), XY("xy");

			private final String descriptor;
			Repeat(String descriptor) { this.descriptor = descriptor; }
			@Override public String toString() { return descriptor; }
		}

		public Settings {
			if (pageWidth < 64 || pageWidth > 16384 || pageHeight < 64 || pageHeight > 16384
				|| padding < 0 || padding > 64 || !Float.isFinite(scale) || scale < 0.01f || scale > 4f)
				throw new IllegalArgumentException("图集尺寸或边距无效");
			if (minFilter == null || magFilter == null || repeat == null)
				throw new IllegalArgumentException("图集过滤或重复设置无效");
		}

		public Settings(int pageWidth, int pageHeight, int padding, boolean duplicateBorder,
			Filter minFilter, Filter magFilter, Repeat repeat) {
			this(pageWidth, pageHeight, padding, duplicateBorder, minFilter, magFilter, repeat,
				false, 1f, false, false);
		}

		public static Settings square(int pageSize, int padding) {
			return new Settings(pageSize, pageSize, padding, true, Filter.Linear, Filter.Linear, Repeat.NONE,
				false, 1f, false, false);
		}

		public static Settings defaults() { return square(2048, 2); }
	}

	public static PackResult pack(List<Path> imageFiles, Path atlasTarget, int pageSize, int padding)
		throws IOException {
		return pack(imageFiles, atlasTarget, pageSize, padding, new CancellationToken());
	}

	public static PackResult pack(List<Path> imageFiles, Path atlasTarget, int pageSize, int padding,
		CancellationToken cancellation) throws IOException {
		return pack(imageFiles, atlasTarget, Settings.square(pageSize, padding), cancellation);
	}

	public static PackResult pack(List<Path> imageFiles, Path atlasTarget, Settings settings)
		throws IOException {
		return pack(imageFiles, atlasTarget, settings, new CancellationToken());
	}

	public static PackResult pack(List<Path> imageFiles, Path atlasTarget, Settings settings,
		CancellationToken cancellation) throws IOException {
		return pack(imageFiles, atlasTarget, settings, cancellation, null);
	}

	/** Packs new images while retaining every page and region from an existing atlas. */
	public static PackResult packIncludingAtlas(List<Path> imageFiles, Path existingAtlas,
		Path atlasTarget, Settings settings, CancellationToken cancellation) throws IOException {
		if (existingAtlas == null) throw new IllegalArgumentException("现有图集路径不能为空");
		return pack(imageFiles, atlasTarget, settings, cancellation,
			existingAtlas.toAbsolutePath().normalize());
	}

	private static PackResult pack(List<Path> imageFiles, Path atlasTarget, Settings settings,
		CancellationToken cancellation, Path existingAtlas) throws IOException {
		if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
		if (settings == null) throw new IllegalArgumentException("图集设置不能为空");
		cancellation.checkCancelled();
		if (imageFiles == null || imageFiles.isEmpty()) throw new IllegalArgumentException("至少需要一张图片");
		if (atlasTarget == null) throw new IllegalArgumentException("图集输出路径不能为空");
		Path atlas = atlasTarget.toAbsolutePath().normalize();
		String atlasName = atlas.getFileName() == null ? "" : atlas.getFileName().toString();
		if (!atlasName.toLowerCase(Locale.ROOT).endsWith(".atlas"))
			throw new IllegalArgumentException("图集输出路径必须以 .atlas 结尾");
		Path parent = atlas.getParent();
		if (parent == null) throw new IllegalArgumentException("图集输出路径缺少目录");
		Files.createDirectories(parent);
		TextureAtlasData retained = null;
		if (existingAtlas != null) {
			if (!Files.isRegularFile(existingAtlas)) throw new IOException("现有图集不存在：" + existingAtlas);
			Path existingParent = existingAtlas.getParent();
			if (existingParent == null) throw new IOException("现有图集缺少资源目录：" + existingAtlas);
			retained = AtlasFiles.readData(new FileHandle(existingAtlas.toFile()),
				new FileHandle(existingParent.toFile()), false);
		}
		List<Path> sorted = new ArrayList<>();
		Set<String> regionNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		if (retained != null) for (Region region : retained.getRegions()) regionNames.add(region.name);
		for (Path file : imageFiles) {
			cancellation.checkCancelled();
			if (file == null || !Files.isRegularFile(file)) throw new IOException("图片不存在：" + file);
			String lower = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
			if (!isSupportedImage(lower)) throw new IllegalArgumentException("只支持 PNG、JPG 或 JPEG 图片：" + file);
			Path normalized = file.toAbsolutePath().normalize();
			String fileName = normalized.getFileName().toString();
			String region = fileName.substring(0, fileName.lastIndexOf('.'));
			if (!regionNames.add(region))
				throw new IllegalArgumentException("图片名称与现有或待打包区域重复：" + region);
			sorted.add(normalized);
		}
		sorted.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
		PixmapPacker packer = new PixmapPacker(settings.pageWidth(), settings.pageHeight(),
			Pixmap.Format.RGBA8888, settings.padding(), settings.duplicateBorder());
		Path staging = null;
		try {
			staging = Files.createTempDirectory(parent, ".openspine-atlas-");
			String base = atlas.getFileName().toString();
			int dot = base.lastIndexOf('.');
			if (dot > 0) base = base.substring(0, dot);
			LinkedHashMap<String, RegionMeta> regions = new LinkedHashMap<>();
			Map<String, String> aliases = new LinkedHashMap<>();
			for (Path file : sorted) {
				cancellation.checkCancelled();
				Pixmap image = new Pixmap(new FileHandle(file.toFile()));
				PreparedPixmap prepared = null;
				try {
					prepared = prepare(image, settings);
					if (prepared.pixmap().getWidth() + settings.padding() * 2 > settings.pageWidth()
						|| prepared.pixmap().getHeight() + settings.padding() * 2 > settings.pageHeight())
						throw new IllegalArgumentException("图片超过图集页面尺寸：" + file.getFileName());
					String region = file.getFileName().toString().substring(0,
						file.getFileName().toString().lastIndexOf('.'));
					String fingerprint = fingerprint(prepared);
					String canonical = settings.alias() ? aliases.get(fingerprint) : null;
					if (canonical == null) {
						canonical = region;
						if (settings.alias()) aliases.put(fingerprint, canonical);
						packer.pack(region, prepared.pixmap());
					}
					regions.put(region, new RegionMeta(canonical, prepared.originalWidth(),
						prepared.originalHeight(), prepared.offsetX(), prepared.offsetY()));
				} finally {
					if (prepared != null) prepared.pixmap().dispose();
					image.dispose();
				}
			}
			List<Path> pages = new ArrayList<>();
			List<Path> stagedPages = new ArrayList<>();
			StringBuilder descriptor = new StringBuilder();
			Set<String> outputNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			outputNames.add(atlas.getFileName().toString());
			for (int pageIndex = 0; pageIndex < packer.getPages().size; pageIndex++)
				outputNames.add(base + (packer.getPages().size == 1 ? "" : "_" + pageIndex) + ".png");
			int regionCount = retained == null ? 0 : appendRetainedAtlas(retained, base, parent,
				staging, pages, stagedPages, outputNames, descriptor, cancellation);
			for (int pageIndex = 0; pageIndex < packer.getPages().size; pageIndex++) {
				cancellation.checkCancelled();
				PixmapPacker.Page page = packer.getPages().get(pageIndex);
				if (descriptor.length() > 0) descriptor.append('\n');
				String pageName = base + (packer.getPages().size == 1 ? "" : "_" + pageIndex) + ".png";
				Path pagePath = parent.resolve(pageName);
				Path stagedPagePath = staging.resolve(pageName);
				PixmapIO.writePNG(new FileHandle(stagedPagePath.toFile()), page.getPixmap());
				pages.add(pagePath);
				stagedPages.add(stagedPagePath);
				descriptor.append(pageName).append('\n')
					.append("size: ").append(page.getPixmap().getWidth()).append(',').append(page.getPixmap().getHeight()).append('\n')
					.append("format: RGBA8888\nfilter: ").append(settings.minFilter()).append(',')
					.append(settings.magFilter()).append("\nrepeat: ").append(settings.repeat())
					.append("\npma: ").append(settings.premultipliedAlpha()).append('\n');
				for (var entry : page.getRects().entries()) {
					PixmapPacker.PixmapPackerRectangle rect = entry.value;
					RegionMeta canonical = regions.get(entry.key);
					appendRegion(descriptor, entry.key, rect, canonical);
					regionCount++;
					for (Map.Entry<String, RegionMeta> region : regions.entrySet()) {
						if (region.getKey().equals(entry.key) || !region.getValue().canonical().equals(entry.key)) continue;
						appendRegion(descriptor, region.getKey(), rect, region.getValue());
						regionCount++;
					}
				}
			}
			cancellation.checkCancelled();
			Path stagedAtlas = staging.resolve(atlas.getFileName().toString());
			Files.writeString(stagedAtlas, descriptor.toString(), StandardCharsets.UTF_8);
			List<Path> outputs = new ArrayList<>(pages.size() + 1);
			outputs.addAll(pages);
			outputs.add(atlas);
			Set<String> inputPaths = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			for (Path input : sorted) inputPaths.add(input.toAbsolutePath().normalize().toString());
			for (Path output : outputs) {
				if (inputPaths.contains(output.toAbsolutePath().normalize().toString()))
					throw new IOException("图集输出文件不能覆盖输入图片：" + output.getFileName());
			}
			cancellation.commit(() -> commitOutputs(stagedPages, stagedAtlas, outputs));
			boolean defaultPremultipliedAlpha = retained != null && retained.getPages().notEmpty()
				? retained.getPages().first().pma : settings.premultipliedAlpha();
			return new PackResult(atlas, List.copyOf(pages), regionCount, defaultPremultipliedAlpha);
		} finally {
			packer.dispose();
			deleteDirectory(staging);
		}
	}

	private static int appendRetainedAtlas(TextureAtlasData retained, String base, Path parent,
		Path staging, List<Path> pages, List<Path> stagedPages, Set<String> outputNames,
		StringBuilder descriptor, CancellationToken cancellation) throws IOException {
		int regionCount = 0;
		int pageIndex = 0;
		for (Page page : retained.getPages()) {
			cancellation.checkCancelled();
			Path source = page.textureFile == null ? null : page.textureFile.file().toPath().toAbsolutePath().normalize();
			if (source == null || !Files.isRegularFile(source))
				throw new IOException("现有图集页面不存在：" + (source == null ? page.name : source));
			String extension = extension(source.getFileName().toString());
			String stem = base + "_existing" + (pageIndex == 0 ? "" : "_" + pageIndex);
			String pageName = uniqueOutputName(parent, stem, extension, outputNames);
			Path staged = staging.resolve(pageName);
			Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
			Path output = parent.resolve(pageName);
			pages.add(output);
			stagedPages.add(staged);
			if (descriptor.length() > 0) descriptor.append('\n');
			appendRetainedPage(descriptor, pageName, page);
			for (Region region : retained.getRegions()) {
				if (region.page != page) continue;
				appendRetainedRegion(descriptor, region);
				regionCount++;
			}
			pageIndex++;
		}
		return regionCount;
	}

	private static void appendRetainedPage(StringBuilder descriptor, String name, Page page) {
		descriptor.append(name).append('\n');
		if (page.width > 0 && page.height > 0)
			descriptor.append("size: ").append(Math.round(page.width)).append(',')
				.append(Math.round(page.height)).append('\n');
		descriptor.append("format: ").append(page.format == null ? Pixmap.Format.RGBA8888 : page.format).append('\n')
			.append("filter: ").append(page.minFilter == null ? Texture.TextureFilter.Nearest : page.minFilter)
			.append(',').append(page.magFilter == null ? Texture.TextureFilter.Nearest : page.magFilter).append('\n')
			.append("repeat: ").append(repeat(page.uWrap, page.vWrap)).append('\n')
			.append("pma: ").append(page.pma).append('\n');
	}

	private static void appendRetainedRegion(StringBuilder descriptor, Region region) {
		descriptor.append(region.name).append('\n')
			.append("rotate: ").append(region.degrees == 0 ? "false" : Integer.toString(region.degrees)).append('\n')
			.append("xy: ").append(region.left).append(',').append(region.top).append('\n')
			.append("size: ").append(region.width).append(',').append(region.height).append('\n')
			.append("orig: ").append(region.originalWidth).append(',').append(region.originalHeight).append('\n')
			.append("offset: ").append(formatNumber(region.offsetX)).append(',')
			.append(formatNumber(region.offsetY)).append("\nindex: ").append(region.index).append('\n');
		if (region.names == null || region.values == null) return;
		for (int index = 0; index < Math.min(region.names.length, region.values.length); index++) {
			descriptor.append(region.names[index]).append(": ");
			int[] values = region.values[index];
			for (int value = 0; value < values.length; value++) {
				if (value > 0) descriptor.append(',');
				descriptor.append(values[value]);
			}
			descriptor.append('\n');
		}
	}

	private static String repeat(Texture.TextureWrap u, Texture.TextureWrap v) {
		boolean repeatX = u != null && u != Texture.TextureWrap.ClampToEdge;
		boolean repeatY = v != null && v != Texture.TextureWrap.ClampToEdge;
		return repeatX ? (repeatY ? "xy" : "x") : (repeatY ? "y" : "none");
	}

	private static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? ".png" : name.substring(dot);
	}

	private static String uniqueOutputName(Path parent, String stem, String extension,
		Set<String> outputNames) {
		String candidate = stem + extension;
		for (int suffix = 2; Files.exists(parent.resolve(candidate)) || !outputNames.add(candidate); suffix++)
			candidate = stem + "_" + suffix + extension;
		return candidate;
	}

	private static String formatNumber(float value) {
		return value == Math.round(value) ? Integer.toString(Math.round(value)) : Float.toString(value);
	}

	private static PreparedPixmap prepare(Pixmap source, Settings settings) {
		int scaledWidth = Math.max(1, Math.round(source.getWidth() * settings.scale()));
		int scaledHeight = Math.max(1, Math.round(source.getHeight() * settings.scale()));
		Pixmap scaled = new Pixmap(scaledWidth, scaledHeight, Pixmap.Format.RGBA8888);
		scaled.setBlending(Pixmap.Blending.None);
		source.setFilter(Pixmap.Filter.BiLinear);
		scaled.drawPixmap(source, 0, 0, source.getWidth(), source.getHeight(),
			0, 0, scaledWidth, scaledHeight);
		if (!settings.stripWhitespace() && !settings.premultipliedAlpha())
			return new PreparedPixmap(scaled, scaledWidth, scaledHeight, 0, 0);

		int minX = 0;
		int minY = 0;
		int maxX = scaledWidth - 1;
		int maxY = scaledHeight - 1;
		if (settings.stripWhitespace()) {
			minX = scaledWidth;
			minY = scaledHeight;
			maxX = -1;
			maxY = -1;
			for (int y = 0; y < scaledHeight; y++) {
				for (int x = 0; x < scaledWidth; x++) {
					if ((scaled.getPixel(x, y) & 0xff) == 0) continue;
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
			if (maxX < minX || maxY < minY) {
				minX = 0;
				minY = 0;
				maxX = 0;
				maxY = 0;
			}
		}

		int width = maxX - minX + 1;
		int height = maxY - minY + 1;
		Pixmap prepared = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		prepared.setBlending(Pixmap.Blending.None);
		prepared.drawPixmap(scaled, 0, 0, minX, minY, width, height);
		scaled.dispose();
		if (settings.premultipliedAlpha()) premultiply(prepared);
		return new PreparedPixmap(prepared, scaledWidth, scaledHeight, minX,
			scaledHeight - minY - height);
	}

	private static void premultiply(Pixmap pixmap) {
		pixmap.setBlending(Pixmap.Blending.None);
		for (int y = 0; y < pixmap.getHeight(); y++) {
			for (int x = 0; x < pixmap.getWidth(); x++) {
				int rgba = pixmap.getPixel(x, y);
				int alpha = rgba & 0xff;
				int red = ((rgba >>> 24) & 0xff) * alpha + 127;
				int green = ((rgba >>> 16) & 0xff) * alpha + 127;
				int blue = ((rgba >>> 8) & 0xff) * alpha + 127;
				pixmap.drawPixel(x, y, red / 255 << 24 | green / 255 << 16 | blue / 255 << 8 | alpha);
			}
		}
	}

	private static String fingerprint(PreparedPixmap prepared) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			updateInt(digest, prepared.pixmap().getWidth());
			updateInt(digest, prepared.pixmap().getHeight());
			updateInt(digest, prepared.originalWidth());
			updateInt(digest, prepared.originalHeight());
			updateInt(digest, prepared.offsetX());
			updateInt(digest, prepared.offsetY());
			for (int y = 0; y < prepared.pixmap().getHeight(); y++)
				for (int x = 0; x < prepared.pixmap().getWidth(); x++)
					updateInt(digest, prepared.pixmap().getPixel(x, y));
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
			return result.toString();
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 unavailable", impossible);
		}
	}

	private static void updateInt(MessageDigest digest, int value) {
		digest.update((byte)(value >>> 24));
		digest.update((byte)(value >>> 16));
		digest.update((byte)(value >>> 8));
		digest.update((byte)value);
	}

	private static void appendRegion(StringBuilder descriptor, String name,
		PixmapPacker.PixmapPackerRectangle rect, RegionMeta metadata) {
		int width = Math.round(rect.getWidth());
		int height = Math.round(rect.getHeight());
		int originalWidth = metadata == null ? width : metadata.originalWidth();
		int originalHeight = metadata == null ? height : metadata.originalHeight();
		int offsetX = metadata == null ? 0 : metadata.offsetX();
		int offsetY = metadata == null ? 0 : metadata.offsetY();
		descriptor.append(name).append('\n')
			.append("rotate: false\nxy: ").append(Math.round(rect.getX())).append(',')
			.append(Math.round(rect.getY())).append('\n')
			.append("size: ").append(width).append(',').append(height).append('\n')
			.append("orig: ").append(originalWidth).append(',').append(originalHeight).append('\n')
			.append("offset: ").append(offsetX).append(',').append(offsetY).append("\nindex: -1\n");
	}

	private static void commitOutputs(List<Path> stagedPages, Path stagedAtlas, List<Path> outputs)
		throws IOException {
		Path backup = Files.createTempDirectory(stagedAtlas.getParent(), ".openspine-atlas-backup-");
		List<Path> backedUp = new ArrayList<>();
		List<Path> installed = new ArrayList<>();
		try {
			for (Path output : outputs) {
				if (!Files.exists(output)) continue;
				if (!Files.isRegularFile(output)) throw new IOException("图集输出路径不是文件：" + output);
				Path backupPath = backup.resolve(output.getFileName().toString());
				move(output, backupPath, false);
				backedUp.add(output);
			}
			for (int index = 0; index < stagedPages.size(); index++) {
				move(stagedPages.get(index), outputs.get(index), true);
				installed.add(outputs.get(index));
			}
			move(stagedAtlas, outputs.get(outputs.size() - 1), true);
			installed.add(outputs.get(outputs.size() - 1));
		} catch (IOException error) {
			for (Path output : installed) Files.deleteIfExists(output);
			for (Path output : backedUp) {
				Path backupPath = backup.resolve(output.getFileName().toString());
				if (Files.exists(backupPath)) move(backupPath, output, true);
			}
			throw error;
		} finally {
			deleteDirectory(backup);
		}
	}

	private static void move(Path source, Path target, boolean replace) throws IOException {
		try {
			if (replace) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
			else Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			if (replace) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
			else Files.move(source, target);
		}
	}

	static boolean isSupportedImage(String fileName) {
		if (fileName == null || fileName.isBlank()) return false;
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
	}

	private static void deleteDirectory(Path directory) {
		if (directory == null || !Files.exists(directory)) return;
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// Temporary atlas files are best-effort cleanup after success or failure.
		}
	}

	private AtlasPacker() {}
}
