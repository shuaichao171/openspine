package com.openspine.io;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Skeleton;

/** Renders one animation range to a full-color animated PNG with a stable canvas. */
public final class ApngAnimationExporter {
	public static final float MIN_FPS = 1f;
	public static final float MAX_FPS = 100f;
	public static final int MAX_FRAMES = 10_000;
	public static final int MAX_FRAME_INDEX = 100_000;
	public static final int MAX_WARM_UP = GifAnimationExporter.MAX_WARM_UP;
	public static final int MAX_REPEAT = 65_535;
	public static final int MIN_COMPRESSION = 0;
	public static final int MAX_COMPRESSION = 9;
	public static final int DEFAULT_COMPRESSION = 6;
	public static final long MAX_TOTAL_PIXELS = 512L * 1024L * 1024L;

	private static final byte[] SIGNATURE = {(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10};
	private static final int DELAY_TIMEBASE = 10_000;
	private static final int DEFLATE_BUFFER_SIZE = 64 * 1024;

	public record Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
		boolean transparent, int repeat, int compression, boolean includeLastFrame) {
		public Settings {
			PngAnimationExporter.validateRange(fps, startFrame, endFrame, includeLastFrame, "APNG");
			GifAnimationExporter.validateWarmUp(warmUp, "APNG");
			new PngFrameExporter.Settings(scale, padding, transparent, false);
			if (repeat < 0 || repeat > MAX_REPEAT)
				throw new IllegalArgumentException("APNG 循环次数必须在 0 到 " + MAX_REPEAT + " 之间");
			if (compression < MIN_COMPRESSION || compression > MAX_COMPRESSION)
				throw new IllegalArgumentException("APNG 压缩级别必须在 0 到 9 之间");
		}

		public Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
			boolean transparent, int repeat, int compression) {
			this(fps, startFrame, endFrame, warmUp, scale, padding, transparent, repeat, compression, true);
		}

		public Settings(float fps, int startFrame, int endFrame, float scale, int padding,
			boolean transparent, int repeat, int compression) {
			this(fps, startFrame, endFrame, 0, scale, padding, transparent, repeat, compression, true);
		}

		public int frameCount() {
			return endFrame - startFrame + (includeLastFrame ? 1 : 0);
		}

		public int lastExportFrame() {
			return includeLastFrame ? endFrame : endFrame - 1;
		}

		PngFrameExporter.Settings renderSettings() {
			return new PngFrameExporter.Settings(scale, padding, transparent, false);
		}
	}

	public record ExportResult(Path path, int width, int height, int frameCount,
		PngFrameExporter.Bounds bounds) {}

	@FunctionalInterface
	interface FrameSupplier {
		/** Returns a newly allocated RGBA8888 frame. The encoder disposes it. */
		Pixmap get(int frameIndex) throws IOException;
	}

	public static int maxAnimationFrame(Animation animation, float fps) {
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (!Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException("APNG 帧率必须在 1 到 100 FPS 之间");
		float duration = animation.getDuration();
		if (!Float.isFinite(duration) || duration < 0f)
			throw new IllegalArgumentException("动画时长无效");
		double frame = Math.ceil((double)duration * fps);
		if (!Double.isFinite(frame) || frame > Integer.MAX_VALUE)
			throw new IllegalArgumentException("动画帧范围过大");
		return (int)frame;
	}

	public static PngFrameExporter.Bounds measure(Skeleton source, Animation animation, Settings settings) {
		validateAnimation(source, animation, settings);
		GifAnimationExporter.AnimationSampler sampler =
			new GifAnimationExporter.AnimationSampler(source, animation, settings.fps(), settings.warmUp());
		PngFrameExporter.Bounds result = null;
		for (int frame = settings.startFrame(); frame <= settings.lastExportFrame(); frame++)
			result = GifAnimationExporter.union(result, PngFrameExporter.measure(sampler.sample(frame)));
		return result;
	}

	public static ExportResult write(Skeleton source, Animation animation, Path requestedTarget,
		Settings settings) throws IOException {
		return write(source, animation, requestedTarget, settings, false);
	}

	public static ExportResult write(Skeleton source, Animation animation, Path requestedTarget,
		Settings settings, boolean sourcePremultipliedAlpha) throws IOException {
		if (Gdx.gl == null) throw new IllegalStateException("APNG 导出需要可用的 OpenGL 上下文");
		validateAnimation(source, animation, settings);
		Path target = normalizeTarget(requestedTarget);
		PngFrameExporter.Bounds bounds = measure(source, animation, settings);
		PngFrameExporter.Settings renderSettings = settings.renderSettings();
		PngFrameExporter.OutputSize size = PngFrameExporter.outputSize(bounds, renderSettings,
			PngFrameExporter.maxTextureSize());
		long totalPixels = Math.multiplyExact((long)size.width() * size.height(), settings.frameCount());
		if (totalPixels > MAX_TOTAL_PIXELS)
			throw new IllegalArgumentException("APNG 总像素数量过大：" + size.width() + "x" + size.height()
				+ " x " + settings.frameCount() + " 帧");

		GifAnimationExporter.AnimationSampler sampler =
			new GifAnimationExporter.AnimationSampler(source, animation, settings.fps(), settings.warmUp());
		writeSequenceAtomic(target, size.width(), size.height(), settings.frameCount(), settings.fps(),
			settings.repeat(), settings.compression(), frameIndex -> {
				Skeleton pose = sampler.sample(settings.startFrame() + frameIndex);
				Pixmap pixels = PngFrameExporter.render(pose, bounds, size, renderSettings,
					sourcePremultipliedAlpha);
				if (settings.transparent()) PngFrameExporter.unpremultiply(pixels);
				return pixels;
			});
		return new ExportResult(target, size.width(), size.height(), settings.frameCount(), bounds);
	}

	static void writeSequenceAtomic(Path requestedTarget, int width, int height, int frameCount,
		float fps, int repeat, int compression, FrameSupplier supplier) throws IOException {
		writeSequenceAtomic(requestedTarget, width, height, frameCount, fps, repeat, compression,
			supplier, new CancellationToken());
	}

	private static void writeSequenceAtomic(Path requestedTarget, int width, int height, int frameCount,
		float fps, int repeat, int compression, FrameSupplier supplier, CancellationToken cancellation)
		throws IOException {
		Path target = normalizeTarget(requestedTarget);
		validateSequence(width, height, frameCount, fps, repeat, compression, supplier);
		Path parent = target.getParent();
		if (parent == null || !Files.isDirectory(parent))
			throw new IOException("APNG 目标目录不存在：" + parent);
		if (Files.exists(target) && !Files.isRegularFile(target))
			throw new IOException("APNG 目标不是普通文件：" + target);

		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			encodeSequence(temporary, width, height, frameCount, fps, repeat, compression, supplier);
			cancellation.commit(() -> {
				try {
					Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException ignored) {
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				}
			});
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	/** Encodes PNG frames rendered incrementally by the editor without using OpenGL. */
	public static Path encodeRenderedPngs(List<Path> frames, Path requestedTarget, Settings settings,
		CancellationToken cancellation) throws IOException {
		if (frames == null || frames.isEmpty() || settings == null || frames.size() != settings.frameCount())
			throw new IllegalArgumentException("APNG 暂存帧数量无效");
		CancellationToken token = cancellation == null ? new CancellationToken() : cancellation;
		Pixmap first = new Pixmap(new FileHandle(frames.get(0).toFile()));
		int width = first.getWidth(), height = first.getHeight();
		first.dispose();
		writeSequenceAtomic(requestedTarget, width, height, frames.size(), settings.fps(), settings.repeat(),
			settings.compression(), index -> {
				token.checkCancelled();
				return new Pixmap(new FileHandle(frames.get(index).toFile()));
			}, token);
		return normalizeTarget(requestedTarget);
	}

	static int frameDelayUnits(int frameIndex, float fps) {
		if (frameIndex < 0 || !Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException("APNG 帧延迟参数无效");
		long start = Math.round(frameIndex * (double)DELAY_TIMEBASE / fps);
		long end = Math.round((frameIndex + 1d) * DELAY_TIMEBASE / fps);
		return (int)Math.max(1L, end - start);
	}

	private static void validateSequence(int width, int height, int frameCount, float fps,
		int repeat, int compression, FrameSupplier supplier) {
		if (width <= 0 || height <= 0) throw new IllegalArgumentException("APNG 尺寸必须为正数");
		long pixels = (long)width * height;
		if (pixels > PngFrameExporter.MAX_PIXELS)
			throw new IllegalArgumentException("APNG 单帧像素数量过大：" + width + "x" + height);
		if (frameCount <= 0 || frameCount > MAX_FRAMES)
			throw new IllegalArgumentException("APNG 帧数必须在 1 到 " + MAX_FRAMES + " 之间");
		if (pixels * frameCount > MAX_TOTAL_PIXELS)
			throw new IllegalArgumentException("APNG 总像素数量过大");
		if (!Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException("APNG 帧率必须在 1 到 100 FPS 之间");
		if (repeat < 0 || repeat > MAX_REPEAT)
			throw new IllegalArgumentException("APNG 循环次数必须在 0 到 " + MAX_REPEAT + " 之间");
		if (compression < MIN_COMPRESSION || compression > MAX_COMPRESSION)
			throw new IllegalArgumentException("APNG 压缩级别必须在 0 到 9 之间");
		if (supplier == null) throw new IllegalArgumentException("APNG 帧来源不能为空");
	}

	private static void encodeSequence(Path target, int width, int height, int frameCount,
		float fps, int repeat, int compression, FrameSupplier supplier) throws IOException {
		try (DataOutputStream output = new DataOutputStream(
			new BufferedOutputStream(Files.newOutputStream(target)))) {
			output.write(SIGNATURE);
			writeChunk(output, "IHDR", chunkData(data -> {
				data.writeInt(width);
				data.writeInt(height);
				data.writeByte(8);
				data.writeByte(6);
				data.writeByte(0);
				data.writeByte(0);
				data.writeByte(0);
			}));
			writeChunk(output, "acTL", chunkData(data -> {
				data.writeInt(frameCount);
				data.writeInt(repeat);
			}));

			int sequence = 0;
			for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
				int delay = frameDelayUnits(frameIndex, fps);
				int frameSequence = sequence++;
				writeChunk(output, "fcTL", chunkData(data -> {
					data.writeInt(frameSequence);
					data.writeInt(width);
					data.writeInt(height);
					data.writeInt(0);
					data.writeInt(0);
					data.writeShort(delay);
					data.writeShort(DELAY_TIMEBASE);
					data.writeByte(0);
					data.writeByte(0);
				}));

				Pixmap frame = supplier.get(frameIndex);
				try {
					if (frame == null || frame.isDisposed() || frame.getFormat() != Pixmap.Format.RGBA8888
						|| frame.getWidth() != width || frame.getHeight() != height)
						throw new IOException("APNG 第 " + frameIndex + " 帧尺寸或格式无效");
					sequence = writeCompressedFrame(output, frame, frameIndex == 0, sequence, compression);
				} finally {
					if (frame != null && !frame.isDisposed()) frame.dispose();
				}
			}
			writeChunk(output, "IEND", new byte[0]);
			output.flush();
		} catch (RuntimeException error) {
			throw new IOException("无法编码 APNG：" + target.getFileName(), error);
		}
	}

	private static int writeCompressedFrame(DataOutputStream output, Pixmap frame, boolean first,
		int sequence, int compression) throws IOException {
		int rowBytes = Math.multiplyExact(frame.getWidth(), 4);
		byte[] row = new byte[Math.addExact(rowBytes, 1)];
		byte[] compressed = new byte[DEFLATE_BUFFER_SIZE];
		ByteBuffer pixels = frame.getPixels().duplicate();
		Deflater deflater = new Deflater(compression);
		try {
			for (int y = frame.getHeight() - 1; y >= 0; y--) {
				row[0] = 0;
				pixels.position(y * rowBytes);
				pixels.get(row, 1, rowBytes);
				deflater.setInput(row);
				while (!deflater.needsInput()) {
					int count = deflater.deflate(compressed);
					if (count > 0) sequence = writeImageData(output, first, sequence, compressed, count);
					else if (!deflater.needsInput()) throw new IOException("APNG 压缩器无法继续处理帧数据");
				}
			}
			deflater.finish();
			while (!deflater.finished()) {
				int count = deflater.deflate(compressed);
				if (count <= 0) throw new IOException("APNG 压缩器未能结束帧数据");
				sequence = writeImageData(output, first, sequence, compressed, count);
			}
			return sequence;
		} finally {
			deflater.end();
		}
	}

	private static int writeImageData(DataOutputStream output, boolean first, int sequence,
		byte[] data, int length) throws IOException {
		if (first) {
			writeChunk(output, "IDAT", data, length);
			return sequence;
		}
		byte[] type = {'f', 'd', 'A', 'T'};
		byte[] sequenceBytes = {
			(byte)(sequence >>> 24), (byte)(sequence >>> 16), (byte)(sequence >>> 8), (byte)sequence};
		CRC32 crc = new CRC32();
		crc.update(type);
		crc.update(sequenceBytes);
		crc.update(data, 0, length);
		output.writeInt(length + 4);
		output.write(type);
		output.write(sequenceBytes);
		output.write(data, 0, length);
		output.writeInt((int)crc.getValue());
		return sequence + 1;
	}

	private static void writeChunk(DataOutputStream output, String type, byte[] data) throws IOException {
		writeChunk(output, type, data, data.length);
	}

	private static void writeChunk(DataOutputStream output, String type, byte[] data, int length)
		throws IOException {
		if (type.length() != 4) throw new IllegalArgumentException("PNG chunk type must have four characters");
		byte[] typeBytes = {(byte)type.charAt(0), (byte)type.charAt(1),
			(byte)type.charAt(2), (byte)type.charAt(3)};
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data, 0, length);
		output.writeInt(length);
		output.write(typeBytes);
		output.write(data, 0, length);
		output.writeInt((int)crc.getValue());
	}

	@FunctionalInterface
	private interface ChunkWriter {
		void write(DataOutputStream output) throws IOException;
	}

	private static byte[] chunkData(ChunkWriter writer) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(32);
		try (DataOutputStream data = new DataOutputStream(bytes)) {
			writer.write(data);
		}
		return bytes.toByteArray();
	}

	private static void validateAnimation(Skeleton source, Animation animation, Settings settings) {
		if (source == null) throw new IllegalArgumentException("没有可导出的骨架");
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (settings == null) throw new IllegalArgumentException("APNG 导出设置不能为空");
		if (!source.getData().getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画不属于当前骨架");
		int maxFrame = maxAnimationFrame(animation, settings.fps());
		if (settings.endFrame() > maxFrame)
			throw new IllegalArgumentException("APNG 结束帧不能超过动画末帧 " + maxFrame);
	}

	private static Path normalizeTarget(Path requestedTarget) {
		if (requestedTarget == null) throw new IllegalArgumentException("APNG 目标路径不能为空");
		Path target = requestedTarget.toAbsolutePath().normalize();
		Path fileName = target.getFileName();
		if (fileName == null) throw new IllegalArgumentException("APNG 目标路径必须包含文件名");
		String lower = fileName.toString().toLowerCase(Locale.ROOT);
		if (!lower.endsWith(".png") && !lower.endsWith(".apng"))
			throw new IllegalArgumentException("APNG 目标路径必须以 .png 或 .apng 结尾");
		return target;
	}

	private ApngAnimationExporter() {}
}
