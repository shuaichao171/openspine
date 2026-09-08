package com.openspine.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Skeleton;

/** Renders one animation range to a broadly compatible MJPEG AVI 1.0 file. */
public final class AviAnimationExporter {
	public static final float MIN_FPS = 1f;
	public static final float MAX_FPS = 100f;
	public static final int MAX_FRAMES = 10_000;
	public static final int MAX_FRAME_INDEX = 100_000;
	public static final int MAX_WARM_UP = GifAnimationExporter.MAX_WARM_UP;
	public static final int MAX_DIMENSION = Short.MAX_VALUE;
	public static final long MAX_TOTAL_PIXELS = 512L * 1024L * 1024L;

	private static final long MAX_RIFF_SIZE = 0xffff_ffffL;
	private static final long RATE_SCALE = 1_000_000L;
	private static final int AVI_HAS_INDEX = 0x10;
	private static final int AVI_KEY_FRAME = 0x10;

	public record Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
		int quality, boolean includeLastFrame) {
		public Settings {
			PngAnimationExporter.validateRange(fps, startFrame, endFrame, includeLastFrame, "AVI");
			GifAnimationExporter.validateWarmUp(warmUp, "AVI");
			new JpegFrameExporter.Settings(scale, padding, quality);
		}

		public Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
			int quality) {
			this(fps, startFrame, endFrame, warmUp, scale, padding, quality, true);
		}

		public Settings(float fps, int startFrame, int endFrame, float scale, int padding, int quality) {
			this(fps, startFrame, endFrame, 0, scale, padding, quality, true);
		}

		public int frameCount() {
			return endFrame - startFrame + (includeLastFrame ? 1 : 0);
		}

		public int lastExportFrame() {
			return includeLastFrame ? endFrame : endFrame - 1;
		}

		PngFrameExporter.Settings renderSettings() {
			return new PngFrameExporter.Settings(scale, padding, false, false);
		}
	}

	public record ExportResult(Path path, int width, int height, int frameCount,
		PngFrameExporter.Bounds bounds) {}

	@FunctionalInterface
	interface FrameSupplier {
		/** Returns a newly allocated frame. The encoder flushes it after use. */
		BufferedImage get(int frameIndex) throws IOException;
	}

	public static int maxAnimationFrame(Animation animation, float fps) {
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (!Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException("AVI 帧率必须在 1 到 100 FPS 之间");
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
		if (Gdx.gl == null) throw new IllegalStateException("AVI 导出需要可用的 OpenGL 上下文");
		validateAnimation(source, animation, settings);
		Path target = normalizeTarget(requestedTarget);
		PngFrameExporter.Bounds bounds = measure(source, animation, settings);
		PngFrameExporter.Settings renderSettings = settings.renderSettings();
		int maxDimension = Math.min(PngFrameExporter.maxTextureSize(), MAX_DIMENSION);
		PngFrameExporter.OutputSize size = PngFrameExporter.outputSize(bounds, renderSettings, maxDimension);
		long totalPixels = Math.multiplyExact((long)size.width() * size.height(), settings.frameCount());
		if (totalPixels > MAX_TOTAL_PIXELS)
			throw new IllegalArgumentException("AVI 总像素数量过大：" + size.width() + "x" + size.height()
				+ " x " + settings.frameCount() + " 帧");

		GifAnimationExporter.AnimationSampler sampler =
			new GifAnimationExporter.AnimationSampler(source, animation, settings.fps(), settings.warmUp());
		writeSequenceAtomic(target, size.width(), size.height(), settings.frameCount(), settings.fps(),
			settings.quality(), frameIndex -> {
				Skeleton pose = sampler.sample(settings.startFrame() + frameIndex);
				Pixmap pixels = PngFrameExporter.render(pose, bounds, size, renderSettings,
					sourcePremultipliedAlpha);
				try {
					return JpegFrameExporter.toBufferedImage(pixels, true);
				} finally {
					pixels.dispose();
				}
			});
		return new ExportResult(target, size.width(), size.height(), settings.frameCount(), bounds);
	}

	static void writeSequenceAtomic(Path requestedTarget, int width, int height, int frameCount,
		float fps, int quality, FrameSupplier supplier) throws IOException {
		writeSequenceAtomic(requestedTarget, width, height, frameCount, fps, quality, supplier,
			new CancellationToken());
	}

	private static void writeSequenceAtomic(Path requestedTarget, int width, int height, int frameCount,
		float fps, int quality, FrameSupplier supplier, CancellationToken cancellation) throws IOException {
		Path target = normalizeTarget(requestedTarget);
		validateSequence(width, height, frameCount, fps, quality, supplier);
		Path parent = target.getParent();
		if (parent == null || !Files.isDirectory(parent))
			throw new IOException("AVI 目标目录不存在：" + parent);
		if (Files.exists(target) && !Files.isRegularFile(target))
			throw new IOException("AVI 目标不是普通文件：" + target);

		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			encodeSequence(temporary, width, height, frameCount, fps, quality, supplier);
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

	/** Encodes opaque PNG frames rendered incrementally by the editor without using OpenGL. */
	public static Path encodeRenderedPngs(List<Path> frames, Path requestedTarget, Settings settings,
		CancellationToken cancellation) throws IOException {
		if (frames == null || frames.isEmpty() || settings == null || frames.size() != settings.frameCount())
			throw new IllegalArgumentException("AVI 暂存帧数量无效");
		CancellationToken token = cancellation == null ? new CancellationToken() : cancellation;
		BufferedImage first = ImageIO.read(frames.get(0).toFile());
		if (first == null) throw new IOException("无法读取 AVI 暂存帧：" + frames.get(0));
		int width = first.getWidth(), height = first.getHeight();
		first.flush();
		writeSequenceAtomic(requestedTarget, width, height, frames.size(), settings.fps(), settings.quality(),
			index -> {
				token.checkCancelled();
				BufferedImage image = ImageIO.read(frames.get(index).toFile());
				if (image == null) throw new IOException("无法读取 AVI 暂存帧：" + frames.get(index));
				return image;
			}, token);
		return normalizeTarget(requestedTarget);
	}

	private static void validateSequence(int width, int height, int frameCount, float fps,
		int quality, FrameSupplier supplier) {
		if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION)
			throw new IllegalArgumentException("AVI 尺寸必须在 1 到 " + MAX_DIMENSION + " 像素之间");
		long pixels = (long)width * height;
		if (pixels > PngFrameExporter.MAX_PIXELS)
			throw new IllegalArgumentException("AVI 单帧像素数量过大：" + width + "x" + height);
		if (frameCount <= 0 || frameCount > MAX_FRAMES)
			throw new IllegalArgumentException("AVI 帧数必须在 1 到 " + MAX_FRAMES + " 之间");
		if (pixels * frameCount > MAX_TOTAL_PIXELS)
			throw new IllegalArgumentException("AVI 总像素数量过大");
		if (!Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException("AVI 帧率必须在 1 到 100 FPS 之间");
		if (quality < JpegFrameExporter.MIN_QUALITY || quality > JpegFrameExporter.MAX_QUALITY)
			throw new IllegalArgumentException("AVI 质量必须在 0 到 100 之间");
		if (supplier == null) throw new IllegalArgumentException("AVI 帧来源不能为空");
	}

	private static void encodeSequence(Path target, int width, int height, int frameCount,
		float fps, int quality, FrameSupplier supplier) throws IOException {
		try (RandomAccessFile output = new RandomAccessFile(target.toFile(), "rw")) {
			output.setLength(0L);
			writeFourCc(output, "RIFF");
			long riffSizePosition = output.getFilePointer();
			writeUInt(output, 0L);
			writeFourCc(output, "AVI ");

			long headerListSize = beginList(output, "hdrl");
			writeFourCc(output, "avih");
			writeUInt(output, 56L);
			writeUInt(output, Math.round(1_000_000d / fps));
			long maxBytesPerSecondPosition = output.getFilePointer();
			writeUInt(output, 0L);
			writeUInt(output, 0L);
			writeUInt(output, AVI_HAS_INDEX);
			writeUInt(output, frameCount);
			writeUInt(output, 0L);
			writeUInt(output, 1L);
			long mainSuggestedBufferPosition = output.getFilePointer();
			writeUInt(output, 0L);
			writeUInt(output, width);
			writeUInt(output, height);
			for (int index = 0; index < 4; index++) writeUInt(output, 0L);

			long streamListSize = beginList(output, "strl");
			writeFourCc(output, "strh");
			writeUInt(output, 56L);
			writeFourCc(output, "vids");
			writeFourCc(output, "MJPG");
			writeUInt(output, 0L);
			writeUShort(output, 0);
			writeUShort(output, 0);
			writeUInt(output, 0L);
			writeUInt(output, RATE_SCALE);
			writeUInt(output, Math.round((double)fps * RATE_SCALE));
			writeUInt(output, 0L);
			writeUInt(output, frameCount);
			long streamSuggestedBufferPosition = output.getFilePointer();
			writeUInt(output, 0L);
			writeUInt(output, quality * 100L);
			writeUInt(output, 0L);
			writeUShort(output, 0);
			writeUShort(output, 0);
			writeUShort(output, width);
			writeUShort(output, height);

			writeFourCc(output, "strf");
			writeUInt(output, 40L);
			writeUInt(output, 40L);
			writeUInt(output, width);
			writeUInt(output, height);
			writeUShort(output, 1);
			writeUShort(output, 24);
			writeFourCc(output, "MJPG");
			long bitmapSizeImagePosition = output.getFilePointer();
			writeUInt(output, 0L);
			writeUInt(output, 0L);
			writeUInt(output, 0L);
			writeUInt(output, 0L);
			writeUInt(output, 0L);
			endChunk(output, streamListSize);
			endChunk(output, headerListSize);

			long movieListSize = beginList(output, "movi");
			long movieTypePosition = movieListSize + 4L;
			long[] offsets = new long[frameCount];
			int[] sizes = new int[frameCount];
			long totalImageBytes = 0L;
			int maxImageBytes = 0;
			for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
				BufferedImage frame = supplier.get(frameIndex);
				try {
					if (frame == null || frame.getWidth() != width || frame.getHeight() != height)
						throw new IOException("AVI 第 " + frameIndex + " 帧尺寸无效");
					byte[] jpeg = JpegFrameExporter.encodeJpegBytes(frame, quality);
					if (jpeg.length == 0) throw new IOException("AVI 第 " + frameIndex + " 帧编码为空");
					long chunkPosition = output.getFilePointer();
					offsets[frameIndex] = chunkPosition - movieTypePosition;
					sizes[frameIndex] = jpeg.length;
					writeFourCc(output, "00dc");
					writeUInt(output, jpeg.length);
					output.write(jpeg);
					if ((jpeg.length & 1) != 0) output.write(0);
					totalImageBytes += jpeg.length;
					maxImageBytes = Math.max(maxImageBytes, jpeg.length);
				} finally {
					if (frame != null) frame.flush();
				}
			}
			endChunk(output, movieListSize);

			writeFourCc(output, "idx1");
			writeUInt(output, Math.multiplyExact(frameCount, 16L));
			for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
				writeFourCc(output, "00dc");
				writeUInt(output, AVI_KEY_FRAME);
				writeUInt(output, offsets[frameIndex]);
				writeUInt(output, sizes[frameIndex]);
			}

			long fileLength = output.getFilePointer();
			if (fileLength - 8L > MAX_RIFF_SIZE)
				throw new IOException("AVI 文件超过 RIFF 4 GiB 限制");
			patchUInt(output, riffSizePosition, fileLength - 8L);
			patchUInt(output, mainSuggestedBufferPosition, maxImageBytes);
			patchUInt(output, streamSuggestedBufferPosition, maxImageBytes);
			patchUInt(output, bitmapSizeImagePosition, maxImageBytes);
			long bytesPerSecond = Math.round(totalImageBytes / (double)frameCount * fps);
			patchUInt(output, maxBytesPerSecondPosition, Math.min(MAX_RIFF_SIZE, bytesPerSecond));
			output.getFD().sync();
		} catch (RuntimeException error) {
			throw new IOException("无法编码 AVI：" + target.getFileName(), error);
		}
	}

	private static long beginList(RandomAccessFile output, String type) throws IOException {
		writeFourCc(output, "LIST");
		long sizePosition = output.getFilePointer();
		writeUInt(output, 0L);
		writeFourCc(output, type);
		return sizePosition;
	}

	private static void endChunk(RandomAccessFile output, long sizePosition) throws IOException {
		long end = output.getFilePointer();
		long size = end - sizePosition - 4L;
		patchUInt(output, sizePosition, size);
		output.seek(end);
		if ((size & 1L) != 0L) output.write(0);
	}

	private static void patchUInt(RandomAccessFile output, long position, long value) throws IOException {
		long end = output.getFilePointer();
		output.seek(position);
		writeUInt(output, value);
		output.seek(end);
	}

	private static void writeFourCc(RandomAccessFile output, String value) throws IOException {
		if (value == null || value.length() != 4)
			throw new IllegalArgumentException("AVI FourCC 必须包含四个字符");
		for (int index = 0; index < 4; index++) output.write(value.charAt(index) & 0xff);
	}

	private static void writeUInt(RandomAccessFile output, long value) throws IOException {
		if (value < 0L || value > MAX_RIFF_SIZE)
			throw new IOException("AVI 32 位字段溢出：" + value);
		output.write((int)value & 0xff);
		output.write((int)(value >>> 8) & 0xff);
		output.write((int)(value >>> 16) & 0xff);
		output.write((int)(value >>> 24) & 0xff);
	}

	private static void writeUShort(RandomAccessFile output, int value) throws IOException {
		if (value < 0 || value > 0xffff) throw new IOException("AVI 16 位字段溢出：" + value);
		output.write(value & 0xff);
		output.write(value >>> 8 & 0xff);
	}

	private static void validateAnimation(Skeleton source, Animation animation, Settings settings) {
		if (source == null) throw new IllegalArgumentException("没有可导出的骨架");
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (settings == null) throw new IllegalArgumentException("AVI 导出设置不能为空");
		if (!source.getData().getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画不属于当前骨架");
		int maxFrame = maxAnimationFrame(animation, settings.fps());
		if (settings.endFrame() > maxFrame)
			throw new IllegalArgumentException("AVI 结束帧不能超过动画末帧 " + maxFrame);
	}

	private static Path normalizeTarget(Path requestedTarget) {
		if (requestedTarget == null) throw new IllegalArgumentException("AVI 目标路径不能为空");
		Path target = requestedTarget.toAbsolutePath().normalize();
		Path fileName = target.getFileName();
		if (fileName == null || !fileName.toString().toLowerCase(Locale.ROOT).endsWith(".avi"))
			throw new IllegalArgumentException("AVI 目标路径必须以 .avi 结尾");
		return target;
	}

	private AviAnimationExporter() {}
}
