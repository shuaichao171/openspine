package com.openspine.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.spine.Skeleton;

/** WebP frame export through an installed ImageIO WebP writer. */
public final class WebpFrameExporter {
	public record ExportResult(Path path, int width, int height) {}

	public static boolean isAvailable() {
		return ImageIO.getImageWritersByFormatName("webp").hasNext();
	}

	public static ExportResult write(Skeleton skeleton, Path target, PngFrameExporter.Settings settings,
		boolean sourcePremultipliedAlpha) throws IOException {
		if (!isAvailable()) throw new IOException("当前 Java 环境没有 WebP ImageIO 编码器");
		if (skeleton == null || settings == null) throw new IllegalArgumentException("WebP 导出参数不完整");
		if (target == null) throw new IllegalArgumentException("WebP 目标路径不能为空");
		Path normalizedTarget = target.toAbsolutePath().normalize();
		if (normalizedTarget.getFileName() == null
			|| !normalizedTarget.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".webp"))
			throw new IllegalArgumentException("WebP 目标路径必须以 .webp 结尾");
		PngFrameExporter.Bounds bounds = PngFrameExporter.measure(skeleton);
		PngFrameExporter.OutputSize size = PngFrameExporter.outputSize(bounds, settings,
			PngFrameExporter.maxTextureSize());
		Pixmap pixels = PngFrameExporter.render(skeleton, bounds, size, settings, sourcePremultipliedAlpha);
		try {
			if (settings.transparent() && !settings.premultipliedAlpha()) PngFrameExporter.unpremultiply(pixels);
			writeAtomic(normalizedTarget, toBufferedImage(pixels));
		} finally {
			pixels.dispose();
		}
		return new ExportResult(normalizedTarget, size.width(), size.height());
	}

	static BufferedImage toBufferedImage(Pixmap pixmap) {
		BufferedImage image = new BufferedImage(pixmap.getWidth(), pixmap.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
		ByteBuffer source = pixmap.getPixels();
		for (int y = 0; y < pixmap.getHeight(); y++) for (int x = 0; x < pixmap.getWidth(); x++) {
			int sourceY = pixmap.getHeight() - 1 - y;
			int offset = (sourceY * pixmap.getWidth() + x) * 4;
			int r = source.get(offset) & 0xff, g = source.get(offset + 1) & 0xff;
			int b = source.get(offset + 2) & 0xff, a = source.get(offset + 3) & 0xff;
			image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
		}
		return image;
	}

	static void writeAtomic(Path target, BufferedImage image) throws IOException {
		Path normalized = target.toAbsolutePath().normalize();
		Path parent = normalized.getParent();
		if (parent == null) throw new IOException("WebP 目标目录不存在");
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "." + normalized.getFileName() + ".", ".tmp");
		try {
			Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
			if (!writers.hasNext()) throw new IOException("当前 Java 环境没有 WebP ImageIO 编码器");
			ImageWriter writer = writers.next();
			try (ImageOutputStream output = ImageIO.createImageOutputStream(temporary.toFile())) {
				writer.setOutput(output);
				writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
			} finally { writer.dispose(); }
			try {
				Files.move(temporary, normalized, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, normalized, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		} finally { Files.deleteIfExists(temporary); }
	}

	private WebpFrameExporter() {}
}
