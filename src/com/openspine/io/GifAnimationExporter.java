package com.openspine.io;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slider;
import com.esotericsoftware.spine.SliderPose;

/** Renders one animation range to an animated GIF with a stable canvas. */
public final class GifAnimationExporter {
	public static final float MIN_FPS = 1f;
	public static final float MAX_FPS = 100f;
	public static final int MAX_FRAMES = 10_000;
	public static final int MAX_FRAME_INDEX = 100_000;
	public static final int MAX_WARM_UP = 100;
	public static final long MAX_WARM_UP_STEPS = 1_000_000L;
	public static final int MAX_REPEAT = 65_535;
	public static final int DEFAULT_ALPHA_THRESHOLD = 128;
	public static final long MAX_TOTAL_PIXELS = 512L * 1024L * 1024L;

	public record Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
		boolean transparent, int repeat, int alphaThreshold, boolean includeLastFrame) {
		public Settings {
			PngAnimationExporter.validateRange(fps, startFrame, endFrame, includeLastFrame, "GIF");
			validateWarmUp(warmUp, "GIF");
			new PngFrameExporter.Settings(scale, padding, transparent, false);
			if (repeat < 0 || repeat > MAX_REPEAT)
				throw new IllegalArgumentException("GIF 循环次数必须在 0 到 " + MAX_REPEAT + " 之间");
			if (alphaThreshold < 0 || alphaThreshold > 255)
				throw new IllegalArgumentException("GIF 透明阈值必须在 0 到 255 之间");
		}

		public Settings(float fps, int startFrame, int endFrame, int warmUp, float scale, int padding,
			boolean transparent, int repeat, int alphaThreshold) {
			this(fps, startFrame, endFrame, warmUp, scale, padding, transparent, repeat, alphaThreshold, true);
		}

		public Settings(float fps, int startFrame, int endFrame, float scale, int padding,
			boolean transparent, int repeat, int alphaThreshold) {
			this(fps, startFrame, endFrame, 0, scale, padding, transparent, repeat, alphaThreshold, true);
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
		BufferedImage get(int frameIndex) throws IOException;
	}

	public static int maxAnimationFrame(Animation animation, float fps) {
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (!Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException("GIF 帧率必须在 1 到 100 FPS 之间");
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
		AnimationSampler sampler = new AnimationSampler(source, animation, settings.fps(), settings.warmUp());
		PngFrameExporter.Bounds result = null;
		for (int frame = settings.startFrame(); frame <= settings.lastExportFrame(); frame++)
			result = union(result, PngFrameExporter.measure(sampler.sample(frame)));
		return result;
	}

	public static ExportResult write(Skeleton source, Animation animation, Path requestedTarget,
		Settings settings) throws IOException {
		return write(source, animation, requestedTarget, settings, false);
	}

	public static ExportResult write(Skeleton source, Animation animation, Path requestedTarget,
		Settings settings, boolean sourcePremultipliedAlpha) throws IOException {
		if (Gdx.gl == null) throw new IllegalStateException("GIF 导出需要可用的 OpenGL 上下文");
		validateAnimation(source, animation, settings);
		Path target = normalizeTarget(requestedTarget);
		PngFrameExporter.Bounds bounds = measure(source, animation, settings);
		PngFrameExporter.Settings renderSettings = settings.renderSettings();
		PngFrameExporter.OutputSize size = PngFrameExporter.outputSize(bounds, renderSettings,
			PngFrameExporter.maxTextureSize());
		long totalPixels = Math.multiplyExact((long)size.width() * size.height(), settings.frameCount());
		if (totalPixels > MAX_TOTAL_PIXELS)
			throw new IllegalArgumentException("GIF 总像素数量过大：" + size.width() + "x" + size.height()
				+ " x " + settings.frameCount() + " 帧");

		AnimationSampler sampler = new AnimationSampler(source, animation, settings.fps(), settings.warmUp());
		writeSequenceAtomic(target, settings.frameCount(), settings.fps(), settings.repeat(), frameIndex -> {
			Skeleton pose = sampler.sample(settings.startFrame() + frameIndex);
			Pixmap pixels = PngFrameExporter.render(pose, bounds, size, renderSettings,
				sourcePremultipliedAlpha);
			try {
				if (settings.transparent()) PngFrameExporter.unpremultiply(pixels);
				return toBufferedImage(pixels, true, settings.transparent(), settings.alphaThreshold());
			} finally {
				pixels.dispose();
			}
		});
		return new ExportResult(target, size.width(), size.height(), settings.frameCount(), bounds);
	}

	static BufferedImage toBufferedImage(Pixmap pixmap, boolean flipY, boolean transparent,
		int alphaThreshold) {
		if (pixmap == null || pixmap.isDisposed() || pixmap.getFormat() != Pixmap.Format.RGBA8888)
			throw new IllegalArgumentException("GIF 像素数据必须是有效的 RGBA8888 Pixmap");
		if (alphaThreshold < 0 || alphaThreshold > 255)
			throw new IllegalArgumentException("GIF 透明阈值必须在 0 到 255 之间");
		int width = pixmap.getWidth(), height = pixmap.getHeight();
		BufferedImage image = new BufferedImage(width, height,
			transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		int[] output = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
		ByteBuffer input = pixmap.getPixels();
		for (int y = 0; y < height; y++) {
			int sourceY = flipY ? height - 1 - y : y;
			int sourceOffset = sourceY * width * 4;
			int targetOffset = y * width;
			for (int x = 0; x < width; x++) {
				int red = input.get(sourceOffset) & 0xff;
				int green = input.get(sourceOffset + 1) & 0xff;
				int blue = input.get(sourceOffset + 2) & 0xff;
				int alpha = input.get(sourceOffset + 3) & 0xff;
				int rgb = red << 16 | green << 8 | blue;
				output[targetOffset++] = transparent && alpha <= alphaThreshold ? 0 : 0xff000000 | rgb;
				sourceOffset += 4;
			}
		}
		return image;
	}

	static void writeSequenceAtomic(Path requestedTarget, int frameCount, float fps, int repeat,
		FrameSupplier supplier) throws IOException {
		writeSequenceAtomic(requestedTarget, frameCount, fps, repeat, supplier, new CancellationToken());
	}

	private static void writeSequenceAtomic(Path requestedTarget, int frameCount, float fps, int repeat,
		FrameSupplier supplier, CancellationToken cancellation) throws IOException {
		Path target = normalizeTarget(requestedTarget);
		if (frameCount <= 0 || frameCount > MAX_FRAMES)
			throw new IllegalArgumentException("GIF 帧数必须在 1 到 " + MAX_FRAMES + " 之间");
		if (!Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException("GIF 帧率必须在 1 到 100 FPS 之间");
		if (repeat < 0 || repeat > MAX_REPEAT)
			throw new IllegalArgumentException("GIF 循环次数必须在 0 到 " + MAX_REPEAT + " 之间");
		if (supplier == null) throw new IllegalArgumentException("GIF 帧来源不能为空");
		Path parent = target.getParent();
		if (parent == null || !Files.isDirectory(parent))
			throw new IOException("GIF 目标目录不存在：" + parent);
		if (Files.exists(target) && !Files.isRegularFile(target))
			throw new IOException("GIF 目标不是普通文件：" + target);

		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			encodeSequence(temporary, frameCount, fps, repeat, supplier);
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
		if (frames == null || settings == null || frames.size() != settings.frameCount())
			throw new IllegalArgumentException("GIF 暂存帧数量无效");
		CancellationToken token = cancellation == null ? new CancellationToken() : cancellation;
		writeSequenceAtomic(requestedTarget, frames.size(), settings.fps(), settings.repeat(), index -> {
			token.checkCancelled();
			BufferedImage source = ImageIO.read(frames.get(index).toFile());
			if (source == null) throw new IOException("无法读取 GIF 暂存帧：" + frames.get(index));
			BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(),
				settings.transparent() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
				int argb = source.getRGB(x, y);
				int alpha = argb >>> 24 & 0xff;
				output.setRGB(x, y, settings.transparent() && alpha <= settings.alphaThreshold()
					? 0 : 0xff000000 | argb & 0x00ffffff);
			}
			source.flush();
			return output;
		}, token);
		return normalizeTarget(requestedTarget);
	}

	static int frameDelayCentiseconds(int frameIndex, float fps) {
		if (frameIndex < 0 || !Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
			throw new IllegalArgumentException("GIF 帧延迟参数无效");
		long start = Math.round(frameIndex * 100d / fps);
		long end = Math.round((frameIndex + 1d) * 100d / fps);
		return (int)Math.max(1L, end - start);
	}

	static PngFrameExporter.Bounds union(PngFrameExporter.Bounds first,
		PngFrameExporter.Bounds second) {
		if (first == null) return second;
		if (second == null) return first;
		float minX = Math.min(first.x(), second.x());
		float minY = Math.min(first.y(), second.y());
		float maxX = Math.max(first.x() + first.width(), second.x() + second.width());
		float maxY = Math.max(first.y() + first.height(), second.y() + second.height());
		return new PngFrameExporter.Bounds(minX, minY, maxX - minX, maxY - minY);
	}

	private static void encodeSequence(Path target, int frameCount, float fps, int repeat,
		FrameSupplier supplier) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
		if (!writers.hasNext()) throw new IOException("当前 JRE 没有 GIF 编码器");
		ImageWriter writer = writers.next();
		try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
			if (output == null) throw new IOException("无法创建 GIF 输出流：" + target.getFileName());
			writer.setOutput(output);
			writer.prepareWriteSequence(null);
			for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
				BufferedImage image = supplier.get(frameIndex);
				if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
					throw new IOException("GIF 第 " + frameIndex + " 帧无效");
				try {
					ImageWriteParam parameters = writer.getDefaultWriteParam();
					IIOMetadata metadata = frameMetadata(writer, parameters, image,
						frameDelayCentiseconds(frameIndex, fps), frameIndex == 0 ? repeat : null);
					writer.writeToSequence(new IIOImage(image, null, metadata), parameters);
				} finally {
					image.flush();
				}
			}
			writer.endWriteSequence();
			output.flush();
		} catch (RuntimeException error) {
			throw new IOException("无法编码 GIF：" + target.getFileName(), error);
		} finally {
			writer.dispose();
		}
	}

	private static IIOMetadata frameMetadata(ImageWriter writer, ImageWriteParam parameters,
		BufferedImage image, int delayCentiseconds, Integer repeat) throws IOException {
		ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
		IIOMetadata metadata = writer.getDefaultImageMetadata(type, parameters);
		String format = metadata.getNativeMetadataFormatName();
		IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(format);
		IIOMetadataNode control = child(root, "GraphicControlExtension");
		control.setAttribute("disposalMethod",
			image.getColorModel().hasAlpha() ? "restoreToBackgroundColor" : "none");
		control.setAttribute("userInputFlag", "FALSE");
		control.setAttribute("transparentColorFlag", image.getColorModel().hasAlpha() ? "TRUE" : "FALSE");
		control.setAttribute("delayTime", Integer.toString(delayCentiseconds));
		control.setAttribute("transparentColorIndex", "0");
		if (repeat != null) {
			IIOMetadataNode extensions = child(root, "ApplicationExtensions");
			IIOMetadataNode extension = new IIOMetadataNode("ApplicationExtension");
			extension.setAttribute("applicationID", "NETSCAPE");
			extension.setAttribute("authenticationCode", "2.0");
			extension.setUserObject(new byte[] {1, (byte)(repeat & 0xff), (byte)(repeat >>> 8 & 0xff)});
			extensions.appendChild(extension);
		}
		metadata.setFromTree(format, root);
		return metadata;
	}

	private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
		for (int index = 0; index < parent.getLength(); index++)
			if (name.equals(parent.item(index).getNodeName())) return (IIOMetadataNode)parent.item(index);
		IIOMetadataNode node = new IIOMetadataNode(name);
		parent.appendChild(node);
		return node;
	}

	private static void validateAnimation(Skeleton source, Animation animation, Settings settings) {
		if (source == null) throw new IllegalArgumentException("没有可导出的骨架");
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (settings == null) throw new IllegalArgumentException("GIF 导出设置不能为空");
		if (!source.getData().getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画不属于当前骨架");
		int maxFrame = maxAnimationFrame(animation, settings.fps());
		if (settings.endFrame() > maxFrame)
			throw new IllegalArgumentException("GIF 结束帧不能超过动画末帧 " + maxFrame);
	}

	static void validateWarmUp(int warmUp, String format) {
		if (warmUp < 0 || warmUp > MAX_WARM_UP)
			throw new IllegalArgumentException(format + " 预热次数必须在 0 到 " + MAX_WARM_UP + " 之间");
	}

	private static Path normalizeTarget(Path requestedTarget) {
		if (requestedTarget == null) throw new IllegalArgumentException("GIF 目标路径不能为空");
		Path target = requestedTarget.toAbsolutePath().normalize();
		Path fileName = target.getFileName();
		if (fileName == null || !fileName.toString().toLowerCase(Locale.ROOT).endsWith(".gif"))
			throw new IllegalArgumentException("GIF 目标路径必须以 .gif 结尾");
		return target;
	}

	public static final class AnimationSampler {
		private final Skeleton skeleton;
		private final Animation animation;
		private final float fps;
		private final float physicsStep;
		private final Array<Slider> suppressedSliders = new Array<>();
		private final float[] suppressedSliderTimes;
		private final float[] suppressedSliderMixes;
		private int sampledFrame = -1;
		private float sampledTime = -1f;
		private boolean warmedUp;

		public AnimationSampler(Skeleton source, Animation animation, float fps) {
			this(source, animation, fps, 0);
		}

		public AnimationSampler(Skeleton source, Animation animation, float fps, int warmUp) {
			if (source == null || animation == null) throw new IllegalArgumentException("GIF 动画采样源无效");
			if (!Float.isFinite(fps) || fps < MIN_FPS || fps > MAX_FPS)
				throw new IllegalArgumentException("GIF 帧率必须在 1 到 100 FPS 之间");
			validateWarmUp(warmUp, "动画");
			this.animation = animation;
			this.fps = fps;
			physicsStep = physicsStep(source);
			skeleton = new Skeleton(source.getData());
			Skin skin = source.getSkin();
			if (skin != null) skeleton.setSkin(skin);
			skeleton.setPosition(source.getX(), source.getY());
			skeleton.setScale(source.getScaleX(), source.getScaleY());
			skeleton.setColor(source.getColor());
			skeleton.setWindX(source.getWindX());
			skeleton.setWindY(source.getWindY());
			skeleton.setGravityX(source.getGravityX());
			skeleton.setGravityY(source.getGravityY());
			skeleton.setupPose();
			skeleton.setTime(0f);
			for (var constraint : skeleton.getConstraints())
				if (constraint instanceof Slider slider && slider.getData().getAnimation() == animation)
					suppressedSliders.add(slider);
			suppressedSliderTimes = new float[suppressedSliders.size];
			suppressedSliderMixes = new float[suppressedSliders.size];
			warmUp(warmUp);
		}

		private void warmUp(int count) {
			float duration = animation.getDuration();
			if (!Float.isFinite(duration) || duration < 0f)
				throw new IllegalArgumentException("动画时长无效");
			if (count == 0 || duration == 0f) return;
			long stepsPerAnimation = Math.max(1L, (long)Math.ceil((double)duration / physicsStep));
			long totalSteps = Math.multiplyExact(stepsPerAnimation, count);
			if (totalSteps > MAX_WARM_UP_STEPS)
				throw new IllegalArgumentException("动画预热采样步数过多：" + totalSteps);
			animation.apply(skeleton, -1f, 0f, false, null, 1f,
				Animation.MixFrom.setup, false, false, false);
			updateWorldTransform(Physics.reset);
			double elapsed = 0d;
			for (int cycle = 0; cycle < count; cycle++) {
				for (long step = 1; step <= stepsPerAnimation; step++) {
					double cycleTime = Math.min(step * (double)physicsStep, duration);
					double next = cycle * (double)duration + cycleTime;
					skeleton.update((float)(next - elapsed));
					animation.apply(skeleton, (float)elapsed, (float)next, true, null, 1f,
						Animation.MixFrom.setup, false, false, false);
					updateWorldTransform(Physics.update);
					elapsed = next;
				}
			}
			warmedUp = true;
		}

		public Skeleton sample(int targetFrame) {
			if (targetFrame < sampledFrame)
				throw new IllegalArgumentException("GIF 动画采样必须按时间递增");
			float duration = animation.getDuration();
			for (int frame = sampledFrame + 1; frame <= targetFrame; frame++) {
				if (frame == 0 && warmedUp) {
					sampledFrame = 0;
					sampledTime = 0f;
					continue;
				}
				float time = Math.min(frame / fps, duration);
				if (frame == 0) {
					animation.apply(skeleton, -1f, 0f, false, null, 1f,
						Animation.MixFrom.setup, false, false, false);
					updateWorldTransform(Physics.reset);
				} else {
					while (sampledTime + 0.000001f < time) {
						float next = Math.min(time, sampledTime + physicsStep);
						skeleton.update(next - sampledTime);
						animation.apply(skeleton, sampledTime, next, false, null, 1f,
							Animation.MixFrom.setup, false, false, false);
						updateWorldTransform(Physics.update);
						sampledTime = next;
					}
				}
				sampledFrame = frame;
				sampledTime = time;
			}
			return skeleton;
		}

		private void updateWorldTransform(Physics physics) {
			if (suppressedSliders.isEmpty()) {
				skeleton.updateWorldTransform(physics);
				return;
			}
			for (int index = 0; index < suppressedSliders.size; index++) {
				SliderPose pose = suppressedSliders.get(index).getPose();
				suppressedSliderTimes[index] = pose.getTime();
				suppressedSliderMixes[index] = pose.getMix();
				pose.setMix(0f);
			}
			try {
				skeleton.updateWorldTransform(physics);
			} finally {
				for (int index = 0; index < suppressedSliders.size; index++) {
					SliderPose pose = suppressedSliders.get(index).getPose();
					pose.setTime(suppressedSliderTimes[index]);
					pose.setMix(suppressedSliderMixes[index]);
				}
			}
		}

		private static float physicsStep(Skeleton skeleton) {
			float step = Float.POSITIVE_INFINITY;
			for (ConstraintData<?, ?> constraint : skeleton.getData().getConstraints()) {
				if (!(constraint instanceof PhysicsConstraintData physics)) continue;
				float candidate = physics.getStep();
				if (Float.isFinite(candidate) && candidate > 0f) step = Math.min(step, candidate);
			}
			return Float.isFinite(step) ? Math.max(0.0001f, step) : Float.POSITIVE_INFINITY;
		}
	}

	private GifAnimationExporter() {}
}
