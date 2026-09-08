package com.openspine.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Skeleton;

/** Renders an animation range to a numbered PNG sequence with a stable canvas. */
public final class PngAnimationExporter {
	public static final float MIN_FPS = GifAnimationExporter.MIN_FPS;
	public static final float MAX_FPS = GifAnimationExporter.MAX_FPS;
	public static final int MAX_FRAMES = GifAnimationExporter.MAX_FRAMES;
	public static final int MAX_FRAME_INDEX = GifAnimationExporter.MAX_FRAME_INDEX;
	public static final int MAX_WARM_UP = GifAnimationExporter.MAX_WARM_UP;
	public static final long MAX_TOTAL_PIXELS = GifAnimationExporter.MAX_TOTAL_PIXELS;

	public record Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
		int compression, boolean transparent, boolean premultipliedAlpha, boolean includeLastFrame) {
		public Settings {
			validateRange(fps, startFrame, endFrame, includeLastFrame, "PNG");
			GifAnimationExporter.validateWarmUp(warmUp, "PNG");
			new PngFrameExporter.Settings(scale, padding, compression, transparent,
				transparent && premultipliedAlpha);
			if (!transparent) premultipliedAlpha = false;
		}

		public Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
			int compression, boolean transparent, boolean premultipliedAlpha) {
			this(fps, startFrame, endFrame, warmUp, scale, padding, compression, transparent,
				premultipliedAlpha, true);
		}

		public Settings(float fps, int startFrame, int endFrame, float scale, int padding,
			boolean transparent, boolean premultipliedAlpha) {
			this(fps, startFrame, endFrame, 0, scale, padding, PngFrameExporter.DEFAULT_COMPRESSION,
				transparent, premultipliedAlpha, true);
		}

		public Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
			boolean transparent, boolean premultipliedAlpha) {
			this(fps, startFrame, endFrame, warmUp, scale, padding, PngFrameExporter.DEFAULT_COMPRESSION,
				transparent, premultipliedAlpha, true);
		}

		public int frameCount() {
			return endFrame - startFrame + (includeLastFrame ? 1 : 0);
		}

		public int lastExportFrame() {
			return includeLastFrame ? endFrame : endFrame - 1;
		}

		PngFrameExporter.Settings renderSettings() {
			return new PngFrameExporter.Settings(scale, padding, compression, transparent,
				premultipliedAlpha);
		}
	}

	public record ExportResult(List<Path> paths, int width, int height, int frameCount,
		PngFrameExporter.Bounds bounds) {}

	public static int maxAnimationFrame(Animation animation, float fps) {
		return maxAnimationFrame(animation, fps, "PNG");
	}

	public static List<Path> sequenceTargets(Path requestedTarget, int startFrame, int endFrame) {
		return FrameSequenceTransaction.targets(requestedTarget, startFrame, endFrame, ".png");
	}

	public static int existingTargetCount(Path requestedTarget, int startFrame, int endFrame) {
		return FrameSequenceTransaction.existingTargetCount(
			sequenceTargets(requestedTarget, startFrame, endFrame));
	}

	/** Commits a previously rendered sequence atomically. */
	public static void commitStaged(List<Path> targets, List<Path> staged, boolean replaceExisting)
		throws IOException {
		FrameSequenceTransaction.commitStaged(targets, staged, replaceExisting);
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
		if (Gdx.gl == null) throw new IllegalStateException("PNG 序列导出需要可用的 OpenGL 上下文");
		validateAnimation(source, animation, settings);
		List<Path> targets = sequenceTargets(requestedTarget, settings.startFrame(), settings.lastExportFrame());
		PngFrameExporter.Bounds bounds = measure(source, animation, settings);
		PngFrameExporter.Settings renderSettings = settings.renderSettings();
		PngFrameExporter.OutputSize size = PngFrameExporter.outputSize(bounds, renderSettings,
			PngFrameExporter.maxTextureSize());
		validateTotalPixels(size, settings.frameCount(), "PNG");
		GifAnimationExporter.AnimationSampler sampler =
			new GifAnimationExporter.AnimationSampler(source, animation, settings.fps(), settings.warmUp());
		FrameSequenceTransaction.writeAtomic(targets, replaceExisting, (frameIndex, stagedTarget) -> {
			Skeleton pose = sampler.sample(settings.startFrame() + frameIndex);
			Pixmap pixels = PngFrameExporter.render(pose, bounds, size, renderSettings,
				sourcePremultipliedAlpha);
			try {
				if (settings.transparent() && !settings.premultipliedAlpha())
					PngFrameExporter.unpremultiply(pixels);
				PngFrameExporter.writePixmapAtomic(stagedTarget, pixels, settings.compression(), true);
			} finally {
				pixels.dispose();
			}
		});
		return new ExportResult(targets, size.width(), size.height(), settings.frameCount(), bounds);
	}

	static void validateRange(float fps, int startFrame, int endFrame, String format) {
		validateRange(fps, startFrame, endFrame, true, format);
	}

	static void validateRange(float fps, int startFrame, int endFrame, boolean includeLastFrame, String format) {
		if (!Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException(format + " 序列帧率必须在 1 到 100 FPS 之间");
		if (startFrame < 0 || endFrame < startFrame)
			throw new IllegalArgumentException(format + " 序列帧范围无效");
		if (endFrame > MAX_FRAME_INDEX)
			throw new IllegalArgumentException(format + " 序列帧号不能超过 " + MAX_FRAME_INDEX);
		long frameCount = (long)endFrame - startFrame + (includeLastFrame ? 1L : 0L);
		if (frameCount <= 0L)
			throw new IllegalArgumentException(format + " 序列至少需要导出 1 帧");
		if (frameCount > MAX_FRAMES)
			throw new IllegalArgumentException(format + " 序列最多可导出 " + MAX_FRAMES + " 帧");
	}

	static int maxAnimationFrame(Animation animation, float fps, String format) {
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (!Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException(format + " 序列帧率必须在 1 到 100 FPS 之间");
		float duration = animation.getDuration();
		if (!Float.isFinite(duration) || duration < 0f)
			throw new IllegalArgumentException("动画时长无效");
		double frame = Math.ceil((double)duration * fps);
		if (!Double.isFinite(frame) || frame > Integer.MAX_VALUE)
			throw new IllegalArgumentException("动画帧范围过大");
		return (int)frame;
	}

	static void validateTotalPixels(PngFrameExporter.OutputSize size, int frameCount, String format) {
		long totalPixels = Math.multiplyExact((long)size.width() * size.height(), frameCount);
		if (totalPixels > MAX_TOTAL_PIXELS)
			throw new IllegalArgumentException(format + " 序列总像素数量过大：" + size.width() + "x"
				+ size.height() + " x " + frameCount + " 帧");
	}

	private static void validateAnimation(Skeleton source, Animation animation, Settings settings) {
		if (source == null) throw new IllegalArgumentException("没有可导出的骨架");
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (settings == null) throw new IllegalArgumentException("PNG 序列导出设置不能为空");
		if (!source.getData().getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画不属于当前骨架");
		int maxFrame = maxAnimationFrame(animation, settings.fps());
		if (settings.endFrame() > maxFrame)
			throw new IllegalArgumentException("PNG 序列结束帧不能超过动画末帧 " + maxFrame);
	}

	private PngAnimationExporter() {}
}
