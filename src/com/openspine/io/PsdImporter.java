package com.openspine.io;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.imageio.ImageIO;

/** Reads layered 8-bit RGB PSD files and extracts their pixel layers as PNG images. */
public final class PsdImporter {
	private static final long MAX_FILE_BYTES = 512L * 1024L * 1024L;
	private static final long MAX_IMAGE_PIXELS = 64L * 1024L * 1024L;
	private static final long MAX_TOTAL_LAYER_PIXELS = 128L * 1024L * 1024L;
	private static final int MAX_LAYERS = 10_000;
	private static final Charset PASCAL_NAME_CHARSET = pascalNameCharset();
	/** Identity ownership prevents a hand-built or already-cleaned result from deleting another import's files. */
	private static final Map<ImportResult, ExtractionFiles> EXTRACTIONS = new IdentityHashMap<>();

	public record PsdLayer(String name, int left, int top, int width, int height,
		boolean visible, int opacity, BufferedImage image) {
		public PsdLayer {
			name = name == null || name.isBlank() ? "Layer" : name;
			if (width < 1 || height < 1 || image == null || image.getWidth() != width
				|| image.getHeight() != height || opacity < 0 || opacity > 255)
				throw new IllegalArgumentException("PSD 图层数据无效");
		}
	}

	/** Pixel layers use the PSD file's bottom-to-top stacking order. */
	public record PsdDocument(int width, int height, List<PsdLayer> layers, List<String> warnings) {
		public PsdDocument {
			if (width < 1 || height < 1) throw new IllegalArgumentException("PSD 画布尺寸无效");
			layers = layers == null ? List.of() : List.copyOf(layers);
			warnings = warnings == null ? List.of() : List.copyOf(warnings);
		}

		public PsdDocument(int width, int height, List<PsdLayer> layers) {
			this(width, height, layers, List.of());
		}
	}

	public record ImportedLayer(String name, String regionName, int left, int top,
		int width, int height, boolean visible, int opacity, Path png, int sourceIndex) {
		public ImportedLayer {
			if (name == null || regionName == null || png == null || sourceIndex < -1)
				throw new IllegalArgumentException("PSD 导入图层无效");
		}

		public ImportedLayer(String name, String regionName, int left, int top,
			int width, int height, boolean visible, int opacity, Path png) {
			this(name, regionName, left, top, width, height, visible, opacity, png, -1);
		}
	}

	/** Extracted pixel layers retain bottom-to-top stacking order, including after hidden-layer filtering. */
	public record ImportResult(Path source, Path directory, int canvasWidth, int canvasHeight,
		List<ImportedLayer> layers, List<String> warnings, boolean hiddenLayersIgnored) {
		public ImportResult {
			if (source == null || directory == null || canvasWidth < 1 || canvasHeight < 1)
				throw new IllegalArgumentException("PSD 导入结果无效");
			layers = layers == null ? List.of() : List.copyOf(layers);
			warnings = warnings == null ? List.of() : List.copyOf(warnings);
		}

		public ImportResult(Path source, Path directory, int canvasWidth, int canvasHeight,
			List<ImportedLayer> layers, List<String> warnings) {
			this(source, directory, canvasWidth, canvasHeight, layers, warnings, false);
		}

		public ImportResult(Path source, Path directory, int canvasWidth, int canvasHeight,
			List<ImportedLayer> layers) {
			this(source, directory, canvasWidth, canvasHeight, layers, List.of(), false);
		}

		public ImportResult ignoringHiddenLayers() {
			ImportResult filtered = new ImportResult(source, directory, canvasWidth, canvasHeight,
				layers.stream().filter(ImportedLayer::visible).toList(), warnings, true);
			synchronized (EXTRACTIONS) {
				ExtractionFiles files = EXTRACTIONS.get(this);
				if (files != null) EXTRACTIONS.put(filtered, files);
			}
			return filtered;
		}
	}

	private record ChannelInfo(int id, int length) {}
	private record LayerRecord(int top, int left, int bottom, int right, int opacity,
		boolean visible, String name, List<ChannelInfo> channels, int divider) {}

	public static PsdDocument read(Path source) throws IOException {
		if (source == null) throw new IllegalArgumentException("PSD 路径不能为空");
		Path file = source.toAbsolutePath().normalize();
		if (!Files.isRegularFile(file)) throw new IOException("PSD 文件不存在：" + file);
		long size = Files.size(file);
		if (size < 26 || size > MAX_FILE_BYTES)
			throw new IOException("PSD 文件大小无效或超过 512 MB 限制：" + size);
		return parse(Files.readAllBytes(file), baseName(file));
	}

	public static ImportResult extract(Path source) throws IOException {
		return extract(source, new CancellationToken());
	}

	/** Writes to a staging directory and exposes the result only after every PNG succeeds. */
	public static ImportResult extract(Path source, CancellationToken cancellation) throws IOException {
		return extract(source, cancellation, List.of());
	}

	/** Extracts layers while reserving region names already owned by another atlas. */
	public static ImportResult extract(Path source, CancellationToken cancellation,
		Collection<String> reservedRegionNames) throws IOException {
		if (source == null) throw new IllegalArgumentException("PSD 路径不能为空");
		if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
		Path file = source.toAbsolutePath().normalize();
		cancellation.checkCancelled();
		PsdDocument document = read(file);
		Path parent = file.getParent();
		if (parent == null) throw new IOException("PSD 文件缺少父目录：" + file);
		Path target = uniqueDirectory(parent, baseName(file) + "_layers");
		Path staging = Files.createTempDirectory(parent, ".openspine-psd-");
		List<ImportedLayer> imported = new ArrayList<>();
		List<ExtractedFile> extractedFiles = new ArrayList<>();
		Set<String> names = new HashSet<>();
		if (reservedRegionNames != null) for (String name : reservedRegionNames)
			if (name != null && !name.isBlank()) names.add(name.toLowerCase(Locale.ROOT));
		try {
			for (int sourceIndex = 0; sourceIndex < document.layers().size(); sourceIndex++) {
				PsdLayer layer = document.layers().get(sourceIndex);
				cancellation.checkCancelled();
				String region = uniqueRegionName(layer.name(), names);
				Path stagedPng = staging.resolve(region + ".png");
				if (!ImageIO.write(layer.image(), "png", stagedPng.toFile()))
					throw new IOException("当前 Java 运行时没有 PNG 编码器");
				extractedFiles.add(new ExtractedFile(stagedPng.getFileName(),
					Files.readAttributes(stagedPng, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS), digest(stagedPng)));
				imported.add(new ImportedLayer(layer.name(), region, layer.left(), layer.top(),
					layer.width(), layer.height(), layer.visible(), layer.opacity(),
					target.resolve(stagedPng.getFileName()), sourceIndex));
			}
			if (imported.isEmpty()) throw new IOException("PSD 中没有可导入的像素图层");
			ExtractionFiles ownedFiles = new ExtractionFiles(target, parent.toRealPath().resolve(target.getFileName()),
				Files.readAttributes(staging, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS), List.copyOf(extractedFiles));
			Path completed = staging;
			cancellation.commit(() -> moveDirectory(completed, target));
			staging = null;
			ImportResult result = new ImportResult(file, target, document.width(), document.height(), imported,
				document.warnings());
			synchronized (EXTRACTIONS) { EXTRACTIONS.put(result, ownedFiles); }
			return result;
		} finally {
			deleteDirectory(staging);
		}
	}

	/** Removes only unchanged PNGs created by this extraction; atlas outputs and later user files are retained. */
	public static void cleanup(ImportResult imported) {
		if (imported == null) return;
		ExtractionFiles owned;
		synchronized (EXTRACTIONS) {
			owned = EXTRACTIONS.get(imported);
			if (owned == null) return;
			EXTRACTIONS.values().removeIf(value -> value == owned);
		}
		owned.cleanup();
	}

	private record ExtractedFile(Path name, BasicFileAttributes attributes, byte[] digest) {}

	private record ExtractionFiles(Path directory, Path realDirectory, BasicFileAttributes attributes,
		List<ExtractedFile> files) {
		private void cleanup() {
			try {
				BasicFileAttributes current = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				if (!current.isDirectory() || !sameIdentity(attributes, current)
					|| !directory.toRealPath().equals(realDirectory)) return;
			} catch (IOException error) { return; }
			for (ExtractedFile file : files) {
				Path path = directory.resolve(file.name()).normalize();
				if (!directory.equals(path.getParent())) continue;
				try {
					BasicFileAttributes current = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
					if (!current.isRegularFile() || !sameIdentity(file.attributes(), current)
						|| file.attributes().size() != current.size()
						|| !file.attributes().lastModifiedTime().equals(current.lastModifiedTime())
						|| !MessageDigest.isEqual(file.digest(), digest(path))) continue;
					Files.delete(path);
				} catch (IOException ignored) { }
			}
			try { Files.delete(directory); } // Deletes only an empty directory, never later-created content.
			catch (IOException ignored) { }
		}
	}

	private static boolean sameIdentity(BasicFileAttributes before, BasicFileAttributes after) {
		return before.fileKey() != null ? Objects.equals(before.fileKey(), after.fileKey())
			: before.creationTime().equals(after.creationTime());
	}

	private static byte[] digest(Path file) throws IOException {
		MessageDigest digest;
		try { digest = MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
		try (var input = Files.newInputStream(file)) {
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
		}
		return digest.digest();
	}

	static PsdDocument parse(byte[] bytes, String fallbackName) throws IOException {
		Cursor input = new Cursor(bytes, 0, bytes == null ? 0 : bytes.length);
		if (!"8BPS".equals(input.ascii(4))) throw new IOException("不是有效的 PSD 文件（缺少 8BPS 签名）");
		int version = input.u16();
		if (version != 1) throw new IOException("只支持 PSD v1，不支持 PSB 或版本 " + version);
		input.skip(6);
		int channelCount = input.u16();
		int height = input.positiveInt32("PSD 高度");
		int width = input.positiveInt32("PSD 宽度");
		int depth = input.u16();
		int colorMode = input.u16();
		if (channelCount < 3 || channelCount > 56) throw new IOException("PSD 通道数量无效：" + channelCount);
		if (depth != 8) throw new IOException("只支持 8 位 PSD，当前位深：" + depth);
		if (colorMode != 3) throw new IOException("只支持 RGB PSD，当前颜色模式：" + colorMode);
		checkedPixels(width, height, "PSD 画布");
		input.skipSection("颜色模式数据");
		Set<String> warnings = new LinkedHashSet<>();
		readImageResources(input, warnings);

		int layerMaskStart = input.position();
		long layerMaskLength = input.u32();
		Cursor layerMask = input.section(layerMaskLength, "图层与蒙版数据");
		List<PsdLayer> layers = readLayers(layerMask, warnings);
		input.position(layerMaskStart + 4 + checkedLength(layerMaskLength, "图层与蒙版数据"));
		if (!layers.isEmpty()) return new PsdDocument(width, height, layers, List.copyOf(warnings));

		BufferedImage composite = readComposite(input, width, height, channelCount);
		String name = fallbackName == null || fallbackName.isBlank() ? "Composite" : fallbackName;
		return new PsdDocument(width, height,
			List.of(new PsdLayer(name, 0, 0, width, height, true, 255, composite)), List.copyOf(warnings));
	}

	private static void readImageResources(Cursor input, Set<String> warnings) throws IOException {
		long length = input.u32();
		Cursor resources = input.section(length, "图像资源");
		while (resources.remaining() > 0) {
			if (resources.remaining() < 12) throw new IOException("PSD 图像资源数据不完整");
			String signature = resources.ascii(4);
			if (!"8BIM".equals(signature) && !"MeSa".equals(signature))
				throw new IOException("PSD 图像资源签名无效");
			int id = resources.u16();
			int nameLength = resources.u8();
			resources.skip(nameLength);
			if (((nameLength + 1) & 1) != 0) resources.skip(1);
			long dataLength = resources.u32();
			resources.skip(checkedLength(dataLength, "图像资源块"));
			if ((dataLength & 1L) != 0) resources.skip(1);
			if (id == 0x040f)
				warnings.add("PSD 包含 ICC 色彩配置；当前版本未应用，颜色可能与 Photoshop 不同");
		}
	}

	private static List<PsdLayer> readLayers(Cursor layerMask, Set<String> warnings) throws IOException {
		if (layerMask.remaining() < 4) return List.of();
		long layerInfoLength = layerMask.u32();
		if (layerInfoLength == 0) return List.of();
		Cursor layerInfo = layerMask.section(layerInfoLength, "图层信息");
		if (layerInfo.remaining() < 2) throw new IOException("PSD 图层信息不完整");
		int signedCount = layerInfo.s16();
		long absoluteCount = Math.abs((long)signedCount);
		if (absoluteCount > MAX_LAYERS) throw new IOException("PSD 图层数量过多：" + absoluteCount);
		int count = (int)absoluteCount;
		List<LayerRecord> records = new ArrayList<>(count);
		for (int index = 0; index < count; index++) records.add(readLayerRecord(layerInfo, index, warnings));
		boolean[] visible = inheritedVisibility(records);

		List<PsdLayer> layers = new ArrayList<>();
		long totalPixels = 0;
		for (int index = 0; index < records.size(); index++) {
			LayerRecord record = records.get(index);
			long widthValue = (long)record.right() - record.left();
			long heightValue = (long)record.bottom() - record.top();
			if (widthValue > Integer.MAX_VALUE || heightValue > Integer.MAX_VALUE)
				throw new IOException("PSD 图层尺寸超出整数范围：" + record.name());
			int width = (int)widthValue;
			int height = (int)heightValue;
			long pixels = record.divider() != 0 || width == 0 || height == 0 ? 0 : checkedPixels(width, height, "PSD 图层");
			totalPixels += pixels;
			if (totalPixels > MAX_TOTAL_LAYER_PIXELS)
				throw new IOException("PSD 图层像素总量超过 134217728 限制");
			byte[] red = null, green = null, blue = null, alpha = null;
			for (ChannelInfo channel : record.channels()) {
				Cursor encoded = layerInfo.section(channel.length(), "图层通道");
				if (pixels == 0 || (channel.id() != -1 && channel.id() != 0
					&& channel.id() != 1 && channel.id() != 2)) continue;
				byte[] values = decodeChannel(encoded, width, height);
				switch (channel.id()) {
				case -1 -> alpha = values;
				case 0 -> red = values;
				case 1 -> green = values;
				case 2 -> blue = values;
				default -> { }
				}
			}
			if (pixels == 0 || red == null || green == null || blue == null) continue;
			BufferedImage image = rgbaImage(width, height, red, green, blue, alpha, record.opacity());
			layers.add(new PsdLayer(record.name(), record.left(), record.top(), width, height,
				visible[index], record.opacity(), image));
		}
		return List.copyOf(layers);
	}

	private static boolean[] inheritedVisibility(List<LayerRecord> records) throws IOException {
		boolean[] visible = new boolean[records.size()];
		ArrayDeque<Boolean> groups = new ArrayDeque<>();
		// Traversing top-to-bottom encounters a folder header before its children and closing divider.
		for (int index = records.size() - 1; index >= 0; index--) {
			LayerRecord record = records.get(index);
			if (record.divider() == 3) {
				if (groups.isEmpty()) throw new IOException("PSD 图层组结束标记没有对应的组：" + record.name());
				groups.pop();
			} else {
				visible[index] = record.visible() && (groups.isEmpty() || groups.peek());
				if (record.divider() == 1 || record.divider() == 2) groups.push(visible[index]);
			}
		}
		if (!groups.isEmpty()) throw new IOException("PSD 图层组结构不完整（缺少结束标记）");
		return visible;
	}

	private static LayerRecord readLayerRecord(Cursor input, int index, Set<String> warnings) throws IOException {
		int top = input.s32();
		int left = input.s32();
		int bottom = input.s32();
		int right = input.s32();
		if (bottom < top || right < left) throw new IOException("PSD 图层边界无效：" + index);
		int channelCount = input.u16();
		if (channelCount < 0 || channelCount > 64) throw new IOException("PSD 图层通道数量无效：" + channelCount);
		List<ChannelInfo> channels = new ArrayList<>(channelCount);
		for (int channel = 0; channel < channelCount; channel++) {
			int id = input.s16();
			long length = input.u32();
			channels.add(new ChannelInfo(id, checkedLength(length, "图层通道")));
		}
		if (!"8BIM".equals(input.ascii(4))) throw new IOException("PSD 图层混合签名无效：" + index);
		String blendMode = input.ascii(4);
		int opacity = input.u8();
		if (input.u8() != 0) warnings.add("部分图层使用剪贴；当前版本未应用剪贴关系");
		int flags = input.u8();
		input.skip(1);
		long extraLength = input.u32();
		Cursor extra = input.section(extraLength, "图层附加数据");
		long maskLength = extra.u32();
		if (maskLength > 0) warnings.add("部分图层包含蒙版；当前版本未应用蒙版");
		extra.skip(checkedLength(maskLength, "图层蒙版"));
		extra.skipSection("图层混合范围");
		String name = readPascalName(extra);
		int divider = 0, nestedDivider = -1;
		while (extra.remaining() >= 12) {
			String signature = extra.ascii(4);
			if (!"8BIM".equals(signature) && !"8B64".equals(signature)) break;
			String key = extra.ascii(4);
			long blockLength = extra.u32();
			Cursor block = extra.section(blockLength, "图层附加块 " + key);
			if ("luni".equals(key)) name = readUnicodeName(block, name);
			if (Set.of("lrFX", "lfx2", "lmfx").contains(key))
				warnings.add("部分图层包含图层效果；当前版本未应用效果");
			if (Set.of("lsct", "lsdk").contains(key)) {
				int kind = block.s32();
				if (kind < 0 || kind > 3) throw new IOException("PSD 图层组标记无效：" + kind);
				if ("lsdk".equals(key)) nestedDivider = kind;
				else divider = kind;
				warnings.add("PSD 包含图层组；当前版本按扁平图层列表导入");
			}
			if ((blockLength & 1L) != 0) extra.skip(1);
		}
		if (name == null || name.isBlank()) name = "Layer " + (index + 1);
		if (nestedDivider >= 0) divider = nestedDivider;
		boolean folder = divider == 1 || divider == 2;
		if (!"norm".equals(blendMode) && !(folder && "pass".equals(blendMode)))
			warnings.add("部分图层使用非正常混合模式；当前版本按正常模式导入");
		if (folder && opacity != 255)
			warnings.add("部分图层组不透明度低于 100%；扁平图层导入未应用组不透明度，重叠区域可能不同");
		return new LayerRecord(top, left, bottom, right, opacity, (flags & 0x02) == 0,
			name, List.copyOf(channels), divider);
	}

	private static String readPascalName(Cursor input) throws IOException {
		if (input.remaining() < 1) return "";
		int start = input.position();
		int length = input.u8();
		byte[] value = input.bytes(length);
		int occupied = 1 + length;
		int padded = (occupied + 3) & ~3;
		input.skip(padded - occupied);
		String decoded = new String(value, PASCAL_NAME_CHARSET).trim();
		return decoded.isEmpty() ? "Layer " + start : decoded;
	}

	private static String readUnicodeName(Cursor input, String fallback) throws IOException {
		if (input.remaining() < 4) return fallback;
		long charCount = input.u32();
		if (charCount > input.remaining() / 2L) throw new IOException("PSD Unicode 图层名长度无效");
		String decoded = new String(input.bytes((int)charCount * 2), StandardCharsets.UTF_16BE);
		int nullIndex = decoded.indexOf('\0');
		if (nullIndex >= 0) decoded = decoded.substring(0, nullIndex);
		return decoded.isBlank() ? fallback : decoded;
	}

	private static BufferedImage readComposite(Cursor input, int width, int height,
		int channelCount) throws IOException {
		int compression = input.u16();
		int pixels = (int)checkedPixels(width, height, "PSD 合成图像");
		long sampleCount = (long)pixels * channelCount;
		if (sampleCount > Integer.MAX_VALUE) throw new IOException("PSD 合成图像通道数据过大");
		byte[] all;
		switch (compression) {
		case 0 -> all = input.bytes((int)sampleCount);
		case 1 -> all = decodeCompositeRle(input, width, height, channelCount);
		case 2, 3 -> {
			all = inflate(input, (int)sampleCount);
			if (compression == 3)
				for (int channel = 0; channel < channelCount; channel++)
					undoPrediction(all, channel * pixels, width, height);
		}
		default -> throw new IOException("不支持的 PSD 合成图像压缩方式：" + compression);
		}
		byte[] red = plane(all, 0, pixels);
		byte[] green = plane(all, 1, pixels);
		byte[] blue = plane(all, 2, pixels);
		byte[] alpha = channelCount > 3 ? plane(all, 3, pixels) : null;
		return rgbaImage(width, height, red, green, blue, alpha, 255);
	}

	private static byte[] decodeChannel(Cursor input, int width, int height) throws IOException {
		int compression = input.u16();
		int expected = Math.toIntExact(checkedPixels(width, height, "PSD 图层通道"));
		return switch (compression) {
		case 0 -> input.bytes(expected);
		case 1 -> decodeRle(input, width, height);
		case 2, 3 -> {
			byte[] decoded = inflate(input, expected);
			if (compression == 3) undoPrediction(decoded, 0, width, height);
			yield decoded;
		}
		default -> throw new IOException("不支持的 PSD 图层压缩方式：" + compression);
		};
	}

	private static byte[] decodeRle(Cursor input, int width, int height) throws IOException {
		int[] lengths = new int[height];
		for (int row = 0; row < height; row++) lengths[row] = input.u16();
		byte[] output = new byte[Math.multiplyExact(width, height)];
		for (int row = 0; row < height; row++)
			decodePackBits(input.section(lengths[row], "PackBits 行"), output, row * width, width);
		return output;
	}

	private static byte[] decodeCompositeRle(Cursor input, int width, int height,
		int channels) throws IOException {
		int rowCount = Math.multiplyExact(height, channels);
		int[] lengths = new int[rowCount];
		for (int row = 0; row < rowCount; row++) lengths[row] = input.u16();
		byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(width, height), channels)];
		for (int row = 0; row < rowCount; row++)
			decodePackBits(input.section(lengths[row], "PackBits 行"), output, row * width, width);
		return output;
	}

	private static void decodePackBits(Cursor input, byte[] output, int offset, int length) throws IOException {
		int written = 0;
		while (written < length && input.remaining() > 0) {
			int header = input.s8();
			if (header >= 0) {
				int count = header + 1;
				if (count > length - written) throw new IOException("PSD PackBits 行输出越界");
				input.copyTo(output, offset + written, count);
				written += count;
			} else if (header != -128) {
				int count = 1 - header;
				if (count > length - written) throw new IOException("PSD PackBits 重复数据越界");
				byte value = input.byteValue();
				for (int index = 0; index < count; index++) output[offset + written++] = value;
			}
		}
		if (written != length) throw new IOException("PSD PackBits 行数据不完整");
	}

	private static byte[] inflate(Cursor input, int expected) throws IOException {
		byte[] output = new byte[expected];
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(input.data, input.position, input.remaining());
			int written = 0;
			while (!inflater.finished() && written < expected) {
				int count = inflater.inflate(output, written, expected - written);
				if (count == 0) {
					if (inflater.needsDictionary()) throw new IOException("PSD ZIP 数据需要未知字典");
					if (inflater.needsInput()) break;
					throw new IOException("PSD ZIP 解压没有进展");
				}
				written += count;
			}
			if (written != expected || !inflater.finished()) throw new IOException("PSD ZIP 解压长度不匹配");
			input.skip((int)inflater.getBytesRead());
			return output;
		} catch (DataFormatException error) {
			throw new IOException("PSD ZIP 数据损坏", error);
		} finally { inflater.end(); }
	}

	private static void undoPrediction(byte[] values, int offset, int width, int height) {
		for (int row = 0; row < height; row++) {
			int start = offset + row * width;
			for (int x = 1; x < width; x++)
				values[start + x] = (byte)((values[start + x] & 0xff) + (values[start + x - 1] & 0xff));
		}
	}

	private static BufferedImage rgbaImage(int width, int height, byte[] red, byte[] green,
		byte[] blue, byte[] alpha, int opacity) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
		for (int index = 0; index < pixels.length; index++) {
			int a = alpha == null ? 255 : alpha[index] & 0xff;
			a = (a * opacity + 127) / 255;
			pixels[index] = a << 24 | (red[index] & 0xff) << 16
				| (green[index] & 0xff) << 8 | blue[index] & 0xff;
		}
		return image;
	}

	private static byte[] plane(byte[] values, int channel, int pixels) {
		byte[] plane = new byte[pixels];
		System.arraycopy(values, Math.multiplyExact(channel, pixels), plane, 0, pixels);
		return plane;
	}

	private static long checkedPixels(int width, int height, String label) throws IOException {
		if (width < 1 || height < 1) throw new IOException(label + "尺寸无效：" + width + " × " + height);
		long pixels = (long)width * height;
		if (pixels > MAX_IMAGE_PIXELS) throw new IOException(label + "超过 67108864 像素限制");
		return pixels;
	}

	private static int checkedLength(long value, String label) throws IOException {
		if (value < 0 || value > Integer.MAX_VALUE) throw new IOException(label + "长度过大：" + value);
		return (int)value;
	}

	private static String uniqueRegionName(String requested, Set<String> usedLowercase) {
		String base = requested == null ? "Layer" : requested.trim();
		base = base.replaceAll("[\\x00-\\x1f<>:\"/\\\\|?*]", "_").replaceAll("[. ]+$", "");
		if (base.isBlank()) base = "Layer";
		if (base.length() > 100) base = base.substring(0, 100).trim();
		if (isWindowsReservedName(base)) base = "_" + base;
		String candidate = base;
		int suffix = 2;
		while (!usedLowercase.add(candidate.toLowerCase(Locale.ROOT))) candidate = base + "_" + suffix++;
		return candidate;
	}

	private static boolean isWindowsReservedName(String value) {
		String name = value;
		int dot = name.indexOf('.');
		if (dot >= 0) name = name.substring(0, dot);
		name = name.toUpperCase(Locale.ROOT);
		if (Set.of("CON", "PRN", "AUX", "NUL").contains(name)) return true;
		return name.matches("COM[1-9]|LPT[1-9]");
	}

	private static Path uniqueDirectory(Path parent, String base) {
		Path candidate = parent.resolve(base);
		int suffix = 2;
		while (Files.exists(candidate)) candidate = parent.resolve(base + "_" + suffix++);
		return candidate;
	}

	private static void moveDirectory(Path source, Path target) throws IOException {
		try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
		catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target); }
	}

	private static void deleteDirectory(Path directory) {
		if (directory == null || !Files.exists(directory)) return;
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
				Files.deleteIfExists(path);
		} catch (IOException ignored) { }
	}

	private static String baseName(Path file) {
		String name = file.getFileName() == null ? "PSD" : file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private static Charset pascalNameCharset() {
		try { return Charset.forName("x-MacRoman"); }
		catch (RuntimeException unavailable) { return StandardCharsets.ISO_8859_1; }
	}

	private static final class Cursor {
		private final byte[] data;
		private int position;
		private final int limit;

		Cursor(byte[] data, int position, int limit) throws IOException {
			if (data == null || position < 0 || limit < position || limit > data.length)
				throw new IOException("PSD 数据范围无效");
			this.data = data;
			this.position = position;
			this.limit = limit;
		}

		int position() { return position; }
		int remaining() { return limit - position; }

		void position(int value) throws IOException {
			if (value < 0 || value > limit) throw new IOException("PSD 数据位置越界");
			position = value;
		}

		int u8() throws IOException { require(1); return data[position++] & 0xff; }
		int s8() throws IOException { require(1); return data[position++]; }
		byte byteValue() throws IOException { require(1); return data[position++]; }
		int u16() throws IOException { return (u8() << 8) | u8(); }
		int s16() throws IOException { return (short)u16(); }
		int s32() throws IOException { return (int)u32(); }
		long u32() throws IOException {
			require(4);
			return (long)(data[position++] & 0xff) << 24 | (long)(data[position++] & 0xff) << 16
				| (long)(data[position++] & 0xff) << 8 | data[position++] & 0xffL;
		}

		int positiveInt32(String label) throws IOException {
			long value = u32();
			if (value < 1 || value > Integer.MAX_VALUE) throw new IOException(label + "无效：" + value);
			return (int)value;
		}

		String ascii(int length) throws IOException {
			return new String(bytes(length), StandardCharsets.US_ASCII);
		}

		byte[] bytes(int length) throws IOException {
			require(length);
			byte[] result = java.util.Arrays.copyOfRange(data, position, position + length);
			position += length;
			return result;
		}

		void copyTo(byte[] target, int offset, int length) throws IOException {
			require(length);
			if (target == null || offset < 0 || length < 0 || offset > target.length - length)
				throw new IOException("PSD 输出缓冲区越界");
			System.arraycopy(data, position, target, offset, length);
			position += length;
		}

		void skip(int length) throws IOException { require(length); position += length; }

		void skipSection(String label) throws IOException {
			long length = u32();
			skip(checkedLength(length, label));
		}

		Cursor section(long length, String label) throws IOException {
			int count = checkedLength(length, label);
			require(count);
			Cursor result = new Cursor(data, position, position + count);
			position += count;
			return result;
		}

		private void require(int length) throws IOException {
			if (length < 0 || length > remaining()) throw new IOException("PSD 文件数据被截断");
		}
	}

	private PsdImporter() {}
}
