package com.openspine.io;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.spine.Skeleton;

/** Renders the current applied skeleton pose to a cropped, opaque JPEG image. */
public final class JpegFrameExporter {
	public static final int MIN_QUALITY = 0;
	public static final int MAX_QUALITY = 100;
	public static final int DEFAULT_QUALITY = 90;

	public record Settings(float scale, int padding, int quality) {
		public Settings {
			new PngFrameExporter.Settings(scale, padding, false, false);
			if (quality < MIN_QUALITY || quality > MAX_QUALITY)
				throw new IllegalArgumentException("JPEG 质量必须在 0 到 100 之间");
		}

		PngFrameExporter.Settings renderSettings() {
			return new PngFrameExporter.Settings(scale, padding, false, false);
		}
	}

	public record ExportResult(Path path, int width, int height, PngFrameExporter.Bounds bounds) {}

	public static PngFrameExporter.OutputSize outputSize(PngFrameExporter.Bounds bounds, Settings settings,
		int maxDimension) {
		if (settings == null) throw new IllegalArgumentException("JPEG 导出设置不能为空");
		return PngFrameExporter.outputSize(bounds, settings.renderSettings(), maxDimension);
	}

	public static ExportResult write(Skeleton skeleton, Path requestedTarget, Settings settings) throws IOException {
		return write(skeleton, requestedTarget, settings, false);
	}

	public static ExportResult write(Skeleton skeleton, Path requestedTarget, Settings settings,
		boolean sourcePremultipliedAlpha) throws IOException {
		if (Gdx.gl == null) throw new IllegalStateException("JPEG 导出需要可用的 OpenGL 上下文");
		if (settings == null) throw new IllegalArgumentException("JPEG 导出设置不能为空");
		Path target = normalizeTarget(requestedTarget);
		PngFrameExporter.Bounds bounds = PngFrameExporter.measure(skeleton);
		PngFrameExporter.Settings renderSettings = settings.renderSettings();
		PngFrameExporter.OutputSize size = outputSize(bounds, settings, PngFrameExporter.maxTextureSize());
		Pixmap pixels = PngFrameExporter.render(skeleton, bounds, size, renderSettings,
			sourcePremultipliedAlpha);
		try {
			writePixmapAtomic(target, pixels, settings.quality(), true);
		} finally {
			pixels.dispose();
		}
		return new ExportResult(target, size.width(), size.height(), bounds);
	}

	public static void writePixmapAtomic(Path requestedTarget, Pixmap pixmap, int quality, boolean flipY)
		throws IOException {
		Path target = normalizeTarget(requestedTarget);
		if (pixmap == null || pixmap.isDisposed() || pixmap.getFormat() != Pixmap.Format.RGBA8888)
			throw new IllegalArgumentException("JPEG 像素数据必须是有效的 RGBA8888 Pixmap");
		if (quality < MIN_QUALITY || quality > MAX_QUALITY)
			throw new IllegalArgumentException("JPEG 质量必须在 0 到 100 之间");
		Path parent = target.getParent();
		if (parent == null || !Files.isDirectory(parent))
			throw new IOException("JPEG 目标目录不存在：" + parent);
		if (Files.exists(target) && !Files.isRegularFile(target))
			throw new IOException("JPEG 目标不是普通文件：" + target);

		BufferedImage image = toBufferedImage(pixmap, flipY);
		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			encodeJpeg(temporary, image, quality);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			image.flush();
			Files.deleteIfExists(temporary);
		}
	}

	static BufferedImage toBufferedImage(Pixmap pixmap, boolean flipY) {
		if (pixmap == null || pixmap.isDisposed() || pixmap.getFormat() != Pixmap.Format.RGBA8888)
			throw new IllegalArgumentException("JPEG 像素数据必须是有效的 RGBA8888 Pixmap");
		int width = pixmap.getWidth(), height = pixmap.getHeight();
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		int[] output = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
		ByteBuffer input = pixmap.getPixels();
		for (int y = 0; y < height; y++) {
			int sourceY = flipY ? height - 1 - y : y;
			int source = sourceY * width * 4;
			int target = y * width;
			for (int x = 0; x < width; x++) {
				int red = input.get(source) & 0xff;
				int green = input.get(source + 1) & 0xff;
				int blue = input.get(source + 2) & 0xff;
				output[target++] = red << 16 | green << 8 | blue;
				source += 4;
			}
		}
		return image;
	}

	/** Writes an already decoded RGB image through the same atomic JPEG path used by frame export. */
	public static void writeBufferedImageAtomic(Path requestedTarget, BufferedImage image, int quality)
		throws IOException {
		Path target = normalizeTarget(requestedTarget);
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
			throw new IllegalArgumentException("JPEG 图像无效");
		if (quality < MIN_QUALITY || quality > MAX_QUALITY)
			throw new IllegalArgumentException("JPEG 质量必须在 0 到 100 之间");
		Path parent = target.getParent();
		if (parent == null || !Files.isDirectory(parent))
			throw new IOException("JPEG 目标目录不存在：" + parent);
		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			encodeJpeg(temporary, image, quality);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally { Files.deleteIfExists(temporary); }
	}

	private static void encodeJpeg(Path target, BufferedImage image, int quality) throws IOException {
		try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
			if (output == null) throw new IOException("无法创建 JPEG 输出流：" + target.getFileName());
			encodeJpeg(output, image, quality, target.getFileName().toString());
		}
	}

	static byte[] encodeJpegBytes(BufferedImage image, int quality) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
			if (output == null) throw new IOException("无法创建内存 JPEG 输出流");
			encodeJpeg(output, image, quality, "内存帧");
		}
		return bytes.toByteArray();
	}

	private static void encodeJpeg(ImageOutputStream output, BufferedImage image, int quality,
		String description) throws IOException {
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
			throw new IllegalArgumentException("JPEG 图像无效");
		if (quality < MIN_QUALITY || quality > MAX_QUALITY)
			throw new IllegalArgumentException("JPEG 质量必须在 0 到 100 之间");
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) throw new IOException("当前 JRE 没有 JPEG 编码器");
		ImageWriter writer = writers.next();
		try {
			ImageWriteParam parameters = writer.getDefaultWriteParam();
			if (!parameters.canWriteCompressed()) throw new IOException("当前 JPEG 编码器不支持质量设置");
			parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			parameters.setCompressionQuality(quality / 100f);
			writer.setOutput(output);
			writer.write(null, new IIOImage(image, null, null), parameters);
			output.flush();
		} catch (RuntimeException error) {
			throw new IOException("无法编码 JPEG：" + description, error);
		} finally {
			writer.dispose();
		}
	}

	private static Path normalizeTarget(Path requestedTarget) {
		if (requestedTarget == null) throw new IllegalArgumentException("JPEG 目标路径不能为空");
		Path target = requestedTarget.toAbsolutePath().normalize();
		Path fileName = target.getFileName();
		if (fileName == null) throw new IllegalArgumentException("JPEG 目标路径必须包含文件名");
		String lower = fileName.toString().toLowerCase(Locale.ROOT);
		if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg"))
			throw new IllegalArgumentException("JPEG 目标路径必须以 .jpg 或 .jpeg 结尾");
		return target;
	}

	private JpegFrameExporter() {}
}
