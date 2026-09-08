package com.openspine.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Skeleton;

/** Measures and renders at most one animation frame per call so the editor remains responsive. */
public final class IncrementalAnimationRenderer implements AutoCloseable {
	public enum Phase { MEASURING, RENDERING, COMPLETE }

	private final Skeleton source;
	private final Animation animation;
	private final float fps;
	private final int warmUp;
	private final int startFrame;
	private final int lastFrame;
	private final int frameCount;
	private final PngFrameExporter.Settings settings;
	private final boolean sourcePremultipliedAlpha;
	private final CancellationToken cancellation;
	private final Path directory;
	private final List<Path> frames;
	private GifAnimationExporter.AnimationSampler sampler;
	private PngFrameExporter.Bounds bounds;
	private PngFrameExporter.OutputSize size;
	private Phase phase = Phase.MEASURING;
	private int frameOffset;

	public IncrementalAnimationRenderer(Skeleton source, Animation animation, float fps, int warmUp,
		int startFrame, int lastFrame, PngFrameExporter.Settings settings,
		boolean sourcePremultipliedAlpha, CancellationToken cancellation) throws IOException {
		if (Gdx.gl == null) throw new IllegalStateException("动画导出需要可用的 OpenGL 上下文");
		if (source == null || animation == null || settings == null)
			throw new IllegalArgumentException("动画渲染参数不完整");
		if (startFrame < 0 || lastFrame < startFrame)
			throw new IllegalArgumentException("动画渲染帧范围无效");
		this.source = source;
		this.animation = animation;
		this.fps = fps;
		this.warmUp = warmUp;
		this.startFrame = startFrame;
		this.lastFrame = lastFrame;
		this.frameCount = Math.addExact(Math.subtractExact(lastFrame, startFrame), 1);
		this.settings = settings;
		this.sourcePremultipliedAlpha = sourcePremultipliedAlpha;
		this.cancellation = cancellation == null ? new CancellationToken() : cancellation;
		directory = Files.createTempDirectory("openspine-animation-frames-");
		List<Path> output = new ArrayList<>(frameCount);
		for (int index = 0; index < frameCount; index++)
			output.add(directory.resolve(String.format(Locale.ROOT, "frame_%06d.png", index)));
		frames = List.copyOf(output);
		sampler = new GifAnimationExporter.AnimationSampler(source, animation, fps, warmUp);
	}

	/** Advances measurement or rendering by one frame. Must be called on the GL thread. */
	public void advance() throws IOException {
		cancellation.checkCancelled();
		if (phase == Phase.COMPLETE) return;
		if (phase == Phase.MEASURING) {
			Skeleton pose = sampler.sample(startFrame + frameOffset);
			bounds = GifAnimationExporter.union(bounds, PngFrameExporter.measure(pose));
			frameOffset++;
			if (frameOffset == frameCount) {
				size = PngFrameExporter.outputSize(bounds, settings, PngFrameExporter.maxTextureSize());
				PngAnimationExporter.validateTotalPixels(size, frameCount, "动画");
				sampler = new GifAnimationExporter.AnimationSampler(source, animation, fps, warmUp);
				frameOffset = 0;
				phase = Phase.RENDERING;
			}
			return;
		}

		Skeleton pose = sampler.sample(startFrame + frameOffset);
		Pixmap pixels = PngFrameExporter.render(pose, bounds, size, settings, sourcePremultipliedAlpha);
		try {
			if (settings.transparent() && !settings.premultipliedAlpha())
				PngFrameExporter.unpremultiply(pixels);
			PngFrameExporter.writePixmapAtomic(frames.get(frameOffset), pixels, settings.compression(), true);
		} finally {
			pixels.dispose();
		}
		cancellation.checkCancelled();
		frameOffset++;
		if (frameOffset == frameCount) phase = Phase.COMPLETE;
	}

	public Phase phase() { return phase; }
	public boolean isComplete() { return phase == Phase.COMPLETE; }
	public int frameCount() { return frameCount; }
	public int completedFrames() { return phase == Phase.MEASURING ? 0 : frameOffset; }
	public int completedSteps() { return phase == Phase.MEASURING ? frameOffset : frameCount + frameOffset; }
	public int totalSteps() { return Math.multiplyExact(frameCount, 2); }
	public float progress() { return Math.min(1f, completedSteps() / (float)totalSteps()); }
	public PngFrameExporter.Bounds bounds() { return bounds; }
	public PngFrameExporter.OutputSize size() { return size; }
	public List<Path> frames() { return frames; }
	public Path directory() { return directory; }

	@Override public void close() throws IOException {
		if (!Files.exists(directory)) return;
		IOException[] failure = {null};
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try { Files.deleteIfExists(path); }
				catch (IOException error) {
					if (failure[0] == null) failure[0] = error;
					else failure[0].addSuppressed(error);
				}
			});
		}
		if (failure[0] != null) throw failure[0];
	}
}
