package com.openspine.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.spine.Skeleton;

/** Writes a flattened, standards-compliant RGB+A PSD for the current applied pose. */
public final class PsdFrameExporter {
	public record ExportResult(Path path, int width, int height) {}

	public static ExportResult write(Skeleton skeleton, Path requestedTarget,
		PngFrameExporter.Settings settings, boolean sourcePremultipliedAlpha) throws IOException {
		if (skeleton == null || settings == null) throw new IllegalArgumentException("PSD 导出参数不完整");
		if (requestedTarget == null) throw new IllegalArgumentException("PSD 目标路径不能为空");
		Path target = requestedTarget.toAbsolutePath().normalize();
		if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".psd"))
			throw new IllegalArgumentException("PSD 目标路径必须以 .psd 结尾");
		PngFrameExporter.Bounds bounds = PngFrameExporter.measure(skeleton);
		PngFrameExporter.OutputSize size = PngFrameExporter.outputSize(bounds, settings,
			PngFrameExporter.maxTextureSize());
		Pixmap pixels = PngFrameExporter.render(skeleton, bounds, size, settings, sourcePremultipliedAlpha);
		try {
			if (settings.transparent() && !settings.premultipliedAlpha()) PngFrameExporter.unpremultiply(pixels);
			writeAtomic(target, pixels);
		} finally {
			pixels.dispose();
		}
		return new ExportResult(target, size.width(), size.height());
	}

	static void writeAtomic(Path target, Pixmap pixels) throws IOException {
		Path parent = target.getParent();
		if (parent == null) throw new IOException("PSD 目标目录不存在");
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temporary))) {
				out.writeBytes("8BPS");
				out.writeShort(1);
				out.write(new byte[6]);
				out.writeShort(4); // RGBA channels
				out.writeInt(pixels.getHeight());
				out.writeInt(pixels.getWidth());
				out.writeShort(8);
				out.writeShort(3); // RGB colour mode
				out.writeInt(0); // colour mode data
				out.writeInt(0); // image resources
				out.writeInt(4); // layer/mask section length
				out.writeInt(0); // no layers
				out.writeShort(0); // raw channel compression
				ByteBuffer source = pixels.getPixels();
				for (int channel = 0; channel < 4; channel++) {
					for (int y = pixels.getHeight() - 1; y >= 0; y--)
						for (int x = 0; x < pixels.getWidth(); x++) {
							int offset = (y * pixels.getWidth() + x) * 4 + channel;
							out.writeByte(source.get(offset) & 0xff);
						}
				}
			}
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private PsdFrameExporter() {}
}
