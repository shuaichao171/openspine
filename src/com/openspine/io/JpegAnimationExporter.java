package com.openspine.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Skeleton;

/** Renders an animation range to a numbered JPEG sequence with a stable canvas. */
public final class JpegAnimationExporter {
	public static final float MIN_FPS = PngAnimationExporter.MIN_FPS;
	public static final float MAX_FPS = PngAnimationExporter.MAX_FPS;
	public static final int MAX_FRAMES = PngAnimationExporter.MAX_FRAMES;
	public static final int MAX_FRAME_INDEX = PngAnimationExporter.MAX_FRAME_INDEX;
	public static final int MAX_WARM_UP = PngAnimationExporter.MAX_WARM_UP;
	public static final long MAX_TOTAL_PIXELS = PngAnimationExporter.MAX_TOTAL_PIXELS;

	public record Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
		int quality, boolean includeLastFrame) {
		public Settings {
			PngAnimationExporter.validateRange(fps, startFrame, endFrame, includeLastFrame, "JPEG");
			GifAnimationExporter.validateWarmUp(warmUp, "JPEG");
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

		JpegFrameExporter.Settings frameSettings() {
			return new JpegFrameExporter.Settings(scale, padding, quality);
		}
	}

	public record ExportResult(List<Path> paths, int width, int height, int frameCount,
		PngFrameExporter.Bounds bounds) {}

	public static int maxAnimationFrame(Animation animation, float fps) {
		return PngAnimationExporter.maxAnimationFrame(animation, fps, "JPEG");
	}

	public static List<Path> sequenceTargets(Path requestedTarget, int startFrame, int endFrame) {
		return FrameSequenceTransaction.targets(requestedTarget, startFrame, endFrame, ".jpg", ".jpeg");
	}

	public static int existingTargetCount(Path requestedTarget, int startFrame, int endFrame) {
		return FrameSequenceTransaction.existingTargetCount(
			sequenceTargets(requestedTarget, startFrame, endFrame));
	}

	/** Commits a previously encoded JPEG sequence atomically. */
	public static void commitStaged(List<Path> targets, List<Path> staged, boolean replaceExisting)
		throws IOException {
		FrameSequenceTransaction.commitStaged(targets, staged, replaceExisting);
	}

	/** Converts staged PNG frames to JPEG files without requiring an OpenGL context. */
	public static void encodeRenderedPngs(List<Path> pngFrames, List<Path> jpegFrames, int quality,
		CancellationToken cancellation) throws IOException {
		if (pngFrames == null || jpegFrames == null || pngFrames.size() != jpegFrames.size()
			|| pngFrames.isEmpty()) throw new IllegalArgumentException("JPEG 暂存帧列表无效");
		CancellationToken token = cancellation == null ? new CancellationToken() : cancellation;
		for (int index = 0; index < pngFrames.size(); index++) {
			token.checkCancelled();
			Path target = jpegFrames.get(index);
			Path parent = target == null ? null : target.getParent();
			if (parent == null) throw new IOException("JPEG 暂存目录不存在");
			Files.createDirectories(parent);
			BufferedImage image = ImageIO.read(pngFrames.get(index).toFile());
			if (image == null) throw new IOException("无法读取暂存 PNG 帧：" + pngFrames.get(index));
			try { JpegFrameExporter.writeBufferedImageAtomic(target, image, quality); }
			finally { image.flush(); }
		}
	}

	public static PngFrameExporter.Bounds measure(Skeleton source, Animation animation,
		Settings settings) {
		validateAnimation(source, animation, settings);
		GifAnimationExporter.AnimationSampler sampler =
			new GifAnimationExporter.AnimationSampler(source, animation, settings.fps(), settings.warmUp());
		PngFrameExporter.Bounds result = null;
		for (int frame = settings.startFrame(); frame <= settings.lastExportFrame(); frame++)
			result = GifAnimationExporter.union(result, PngFrameExporter.measure(sampler.sample(frame)));
		return result;
	}

	public static ExportResult write(Skeleton source, Animation animation, Path requestedTarget,
		Settings settings, boolean replaceExisting) throws IOException {
		return write(source, animation, requestedTarget, settings, replaceExisting, false);
	}

	public static ExportResult write(Skeleton source, Animation animation, Path requestedTarget,
		Settings settings, boolean replaceExisting, boolean sourcePremultipliedAlpha) throws IOException {
		if (Gdx.gl == null) throw new IllegalStateException("JPEG 序列导出需要可用的 OpenGL 上下文");
		validateAnimation(source, animation, settings);
		List<Path> targets = sequenceTargets(requestedTarget, settings.startFrame(), settings.lastExportFrame());
		PngFrameExporter.Bounds bounds = measure(source, animation, settings);
		JpegFrameExporter.Settings frameSettings = settings.frameSettings();
		PngFrameExporter.Settings renderSettings = frameSettings.renderSettings();
		PngFrameExporter.OutputSize size = JpegFrameExporter.outputSize(bounds, frameSettings,
			PngFrameExporter.maxTextureSize());
		PngAnimationExporter.validateTotalPixels(size, settings.frameCount(), "JPEG");
		GifAnimationExporter.AnimationSampler sampler =
			new GifAnimationExporter.AnimationSampler(source, animation, settings.fps(), settings.warmUp());
		FrameSequenceTransaction.writeAtomic(targets, replaceExisting, (frameIndex, stagedTarget) -> {
			Skeleton pose = sampler.sample(settings.startFrame() + frameIndex);
			Pixmap pixels = PngFrameExporter.render(pose, bounds, size, renderSettings,
				sourcePremultipliedAlpha);
			try {
				JpegFrameExporter.writePixmapAtomic(stagedTarget, pixels, settings.quality(), true);
			} finally {
				pixels.dispose();
			}
		});
		return new ExportResult(targets, size.width(), size.height(), settings.frameCount(), bounds);
	}

	private static void validateAnimation(Skeleton source, Animation animation, Settings settings) {
		if (source == null) throw new IllegalArgumentException("没有可导出的骨架");
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (settings == null) throw new IllegalArgumentException("JPEG 序列导出设置不能为空");
		if (!source.getData().getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画不属于当前骨架");
		int maxFrame = maxAnimationFrame(animation, settings.fps());
		if (settings.endFrame() > maxFrame)
			throw new IllegalArgumentException("JPEG 序列结束帧不能超过动画末帧 " + maxFrame);
	}

	private JpegAnimationExporter() {}
}
