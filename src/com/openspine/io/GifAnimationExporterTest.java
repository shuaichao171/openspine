package com.openspine.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Slider;
import com.esotericsoftware.spine.SliderData;

/** Headless regression tests for GIF sampling, pixel conversion, and sequence metadata. */
public final class GifAnimationExporterTest {
	public static void main(String[] args) throws Exception {
		GdxNativesLoader.load();
		validatesSettingsAndFrameTiming();
		samplesAnIndependentAnimationPose();
		suppressesSelfReferencedSlider();
		warmsUpPhysicsWithoutMutatingSource();
		convertsRgbaPixelsWithBinaryTransparency();
		writesAtomicAnimatedGif();
		System.out.println("ALL GIF ANIMATION EXPORTER TESTS PASSED");
	}

	private static void validatesSettingsAndFrameTiming() {
		GifAnimationExporter.Settings settings = new GifAnimationExporter.Settings(
			24f, 2, 12, 1.5f, 8, true, 0, 96);
		assertEquals(11, settings.frameCount(), "inclusive GIF frame count");
		GifAnimationExporter.Settings withoutLastFrame = new GifAnimationExporter.Settings(
			24f, 2, 12, 0, 1.5f, 8, true, 0, 96, false);
		assertEquals(10, withoutLastFrame.frameCount(), "GIF can omit the last frame");
		assertEquals(11, withoutLastFrame.lastExportFrame(), "GIF reports its actual last output frame");
		assertEquals(4, GifAnimationExporter.frameDelayCentiseconds(0, 24f), "first 24 FPS delay");
		assertEquals(4, GifAnimationExporter.frameDelayCentiseconds(1, 24f), "second 24 FPS delay");
		assertEquals(5, GifAnimationExporter.frameDelayCentiseconds(2, 24f),
			"fractional GIF timing distributes centiseconds without drift");
		PngFrameExporter.Bounds union = GifAnimationExporter.union(
			new PngFrameExporter.Bounds(-2f, 1f, 4f, 5f),
			new PngFrameExporter.Bounds(1f, -3f, 6f, 5f));
		assertNear(-2f, union.x(), "union x");
		assertNear(-3f, union.y(), "union y");
		assertNear(9f, union.width(), "union width");
		assertNear(9f, union.height(), "union height");

		assertThrows(() -> new GifAnimationExporter.Settings(101f, 0, 1, 1f, 0, true, 0, 128),
			"GIF FPS above the format resolution is rejected");
		assertThrows(() -> new GifAnimationExporter.Settings(30f, 2, 1, 1f, 0, true, 0, 128),
			"backwards GIF ranges are rejected");
		assertThrows(() -> new GifAnimationExporter.Settings(30f, 0,
			GifAnimationExporter.MAX_FRAME_INDEX + 1, 1f, 0, true, 0, 128),
			"excessive GIF warm-up ranges are rejected");
		assertThrows(() -> new GifAnimationExporter.Settings(30f, 0, 1, 1f, 0, true, -1, 128),
			"negative GIF repeat counts are rejected");
		assertThrows(() -> new GifAnimationExporter.Settings(30f, 0, 1, 1f, 0, true, 0, 256),
			"GIF alpha thresholds above 255 are rejected");
		assertThrows(() -> new GifAnimationExporter.Settings(30f, 0, 1,
			GifAnimationExporter.MAX_WARM_UP + 1, 1f, 0, true, 0, 128),
			"excessive GIF warm-up cycles are rejected");
		assertThrows(() -> new GifAnimationExporter.Settings(
			30f, 1, 1, 0, 1f, 0, true, 0, 128, false),
			"excluding the only GIF frame is rejected");
	}

	private static void samplesAnIndependentAnimationPose() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setX(3f);
		root.getSetupPose().setY(4f);
		data.getBones().add(root);
		Animation.TranslateTimeline translate = new Animation.TranslateTimeline(2, 0, 0);
		translate.setFrame(0, 0f, 0f, 0f);
		translate.setFrame(1, 1f, 10f, 20f);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(translate);
		Animation animation = new Animation("move");
		animation.setTimelines(timelines, new IntArray(new int[] {0}));
		animation.setDuration(1f);
		data.getAnimations().add(animation);
		Skeleton source = new Skeleton(data);
		source.getRootBone().getPose().setX(77f);
		source.setTime(9f);

		GifAnimationExporter.AnimationSampler sampler =
			new GifAnimationExporter.AnimationSampler(source, animation, 10f);
		Skeleton half = sampler.sample(5);
		assertNear(8f, half.getRootBone().getPose().getX(), "sampled animation uses setup-relative X");
		assertNear(14f, half.getRootBone().getPose().getY(), "sampled animation uses setup-relative Y");
		assertNear(0.5f, half.getTime(), "sampled animation advances the independent physics clock");
		assertNear(77f, source.getRootBone().getPose().getX(), "GIF sampling does not change the source pose");
		assertNear(9f, source.getTime(), "GIF sampling does not change the source clock");
		assertThrows(() -> sampler.sample(4), "GIF sampler rejects backwards access");

		animation.setDuration(0.55f);
		GifAnimationExporter.AnimationSampler exactEndSampler =
			new GifAnimationExporter.AnimationSampler(source, animation, 10f);
		Skeleton exactEnd = exactEndSampler.sample(GifAnimationExporter.maxAnimationFrame(animation, 10f));
		assertNear(0.55f, exactEnd.getTime(),
			"a non-frame-aligned last frame stops at the exact animation duration");
	}

	private static void warmsUpPhysicsWithoutMutatingSource() {
		SkeletonData data = new SkeletonData();
		data.setReferenceScale(100f);
		BoneData root = new BoneData(0, "root", null);
		BoneData tip = new BoneData(1, "tip", root);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		tip.getSetupPose().setInherit(BoneData.Inherit.normal);
		tip.setLength(80f);
		data.getBones().add(root);
		data.getBones().add(tip);
		PhysicsConstraintData physics = new PhysicsConstraintData("lag");
		physics.setBone(tip);
		physics.setStep(1f / 60f);
		physics.setX(1f);
		physics.setLimit(10_000f);
		physics.getSetupPose().setInertia(1f);
		physics.getSetupPose().setStrength(20f);
		physics.getSetupPose().setDamping(0.8f);
		physics.getSetupPose().setMassInverse(1f);
		physics.getSetupPose().setMix(1f);
		data.getConstraints().add(physics);

		Animation.TranslateTimeline translate = new Animation.TranslateTimeline(3, 0, 0);
		translate.setFrame(0, 0f, 0f, 0f);
		translate.setFrame(1, 0.5f, 100f, 0f);
		translate.setFrame(2, 1f, 0f, 0f);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(translate);
		Animation animation = new Animation("swing");
		animation.setTimelines(timelines, new IntArray(new int[] {0}));
		animation.setDuration(1f);
		data.getAnimations().add(animation);

		Skeleton source = new Skeleton(data);
		source.setupPose();
		source.updateWorldTransform(Physics.reset);
		float sourceTipX = source.findBone("tip").getAppliedPose().getWorldX();
		GifAnimationExporter.AnimationSampler coldSampler =
			new GifAnimationExporter.AnimationSampler(source, animation, 60f, 0);
		float coldTipX = coldSampler.sample(0).findBone("tip").getAppliedPose().getWorldX();
		GifAnimationExporter.AnimationSampler warmSampler =
			new GifAnimationExporter.AnimationSampler(source, animation, 60f, 1);
		Skeleton warmed = warmSampler.sample(0);
		float warmTipX = warmed.findBone("tip").getAppliedPose().getWorldX();
		assertTrue(Math.abs(warmTipX - coldTipX) > 1f,
			"one warm-up cycle carries Physics inertia into exported frame zero");
		assertNear(1f, warmed.getTime(), "warm-up advances the independent Physics clock by one animation");
		assertNear(0f, source.getTime(), "warm-up does not advance the source skeleton clock");
		assertNear(sourceTipX, source.findBone("tip").getAppliedPose().getWorldX(),
			"warm-up does not change the source skeleton pose");
	}

	private static void suppressesSelfReferencedSlider() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(1, 0, 0);
		rotate.setFrame(0, 0f, 90f);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(rotate);
		Animation animation = new Animation("self-referenced");
		animation.setTimelines(timelines, new IntArray(new int[] {0}));
		animation.setDuration(1f);
		data.getAnimations().add(animation);
		SliderData slider = new SliderData("self-reference");
		slider.setAnimation(animation);
		slider.setAdditive(true);
		slider.getSetupPose().setTime(0f);
		slider.getSetupPose().setMix(1f);
		data.getConstraints().add(slider);

		Skeleton sampled = new GifAnimationExporter.AnimationSampler(new Skeleton(data), animation, 30f).sample(0);
		assertNear(90f, sampled.getRootBone().getPose().getRotation(),
			"export sampling applies an active animation once when a Slider references itself");
		assertNear(1f, ((Slider)sampled.getConstraints().first()).getPose().getMix(),
			"self-reference suppression restores the Slider mix after world transform evaluation");
	}

	private static void convertsRgbaPixelsWithBinaryTransparency() {
		Pixmap pixels = new Pixmap(1, 2, Pixmap.Format.RGBA8888);
		try {
			ByteBuffer buffer = pixels.getPixels();
			putRgba(buffer, 0, 240, 20, 10, 255);
			putRgba(buffer, 4, 10, 20, 240, 64);
			BufferedImage image = GifAnimationExporter.toBufferedImage(pixels, true, true, 128);
			try {
				assertEquals(0, image.getRGB(0, 0) >>> 24, "low-alpha upper pixel becomes transparent");
				assertEquals(255, image.getRGB(0, 1) >>> 24, "opaque lower pixel remains visible");
				assertTrue((image.getRGB(0, 1) & 0xff0000) > (image.getRGB(0, 1) & 0xff),
					"framebuffer rows are flipped into top-down GIF order");
			} finally {
				image.flush();
			}
		} finally {
			pixels.dispose();
		}
	}

	private static void writesAtomicAnimatedGif() throws Exception {
		Path directory = Files.createTempDirectory("openspine-gif-export-").toRealPath();
		Path target = directory.resolve("animation.gif");
		Path failed = directory.resolve("failed.gif");
		try {
			GifAnimationExporter.writeSequenceAtomic(target, 3, 24f, 0,
				frame -> testFrame(frame, 5, 4));
			byte[] bytes = Files.readAllBytes(target);
			assertTrue(bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F',
				"atomic GIF writer produces a GIF signature");

			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
			assertTrue(readers.hasNext(), "the JRE provides a GIF reader");
			ImageReader reader = readers.next();
			try (ImageInputStream input = ImageIO.createImageInputStream(target.toFile())) {
				reader.setInput(input, false, false);
				assertEquals(3, reader.getNumImages(true), "animated GIF preserves every frame");
				assertEquals(4, delay(reader.getImageMetadata(0)), "first encoded GIF delay");
				assertEquals("restoreToBackgroundColor", disposal(reader.getImageMetadata(0)),
					"transparent full-canvas frames clear the preceding frame before advancing");
				assertEquals(4, delay(reader.getImageMetadata(1)), "second encoded GIF delay");
				assertEquals(5, delay(reader.getImageMetadata(2)), "third encoded GIF delay");
				assertEquals(0, loopCount(reader.getImageMetadata(0)), "NETSCAPE metadata preserves infinite looping");
				BufferedImage first = reader.read(0);
				BufferedImage second = reader.read(1);
				try {
					assertEquals(0, first.getRGB(0, 0) >>> 24, "encoded GIF preserves transparent pixels");
					assertDominant(first.getRGB(3, 2), 16, "first GIF frame is red");
					assertDominant(second.getRGB(3, 2), 8, "second GIF frame is green");
				} finally {
					first.flush();
					second.flush();
				}
			} finally {
				reader.dispose();
			}

			try {
				GifAnimationExporter.writeSequenceAtomic(failed, 2, 30f, 1, frame -> {
					if (frame == 1) throw new IOException("expected frame failure");
					return testFrame(frame, 2, 2);
				});
				throw new AssertionError("failed GIF sequence should not replace its target");
			} catch (IOException expected) {
				assertTrue(!Files.exists(failed), "failed GIF export leaves no target file");
			}
			try (var entries = Files.list(directory)) {
				assertTrue(entries.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
					"GIF writer removes temporary files after success and failure");
			}
			assertThrows(() -> {
				try {
					GifAnimationExporter.writeSequenceAtomic(directory.resolve("animation.png"), 1, 30f, 1,
						frame -> testFrame(frame, 1, 1));
				} catch (IOException error) {
					throw new IllegalStateException(error);
				}
			}, "non-GIF targets are rejected");
		} finally {
			Files.deleteIfExists(target);
			Files.deleteIfExists(failed);
			try (var entries = Files.list(directory)) {
				for (Path entry : entries.toList()) Files.deleteIfExists(entry);
			}
			Files.deleteIfExists(directory);
		}
	}

	private static BufferedImage testFrame(int frame, int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int color = switch (frame % 3) {
		case 0 -> 0xffff0000;
		case 1 -> 0xff00ff00;
		default -> 0xff0000ff;
		};
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++) image.setRGB(x, y, color);
		image.setRGB(0, 0, 0);
		return image;
	}

	private static int delay(IIOMetadata metadata) {
		IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(metadata.getNativeMetadataFormatName());
		IIOMetadataNode control = find(root, "GraphicControlExtension");
		return Integer.parseInt(control.getAttribute("delayTime"));
	}

	private static String disposal(IIOMetadata metadata) {
		IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(metadata.getNativeMetadataFormatName());
		return find(root, "GraphicControlExtension").getAttribute("disposalMethod");
	}

	private static int loopCount(IIOMetadata metadata) {
		IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(metadata.getNativeMetadataFormatName());
		IIOMetadataNode extension = find(root, "ApplicationExtension");
		byte[] value = (byte[])extension.getUserObject();
		return (value[1] & 0xff) | (value[2] & 0xff) << 8;
	}

	private static IIOMetadataNode find(IIOMetadataNode node, String name) {
		if (name.equals(node.getNodeName())) return node;
		for (int index = 0; index < node.getLength(); index++) {
			IIOMetadataNode found = find((IIOMetadataNode)node.item(index), name);
			if (found != null) return found;
		}
		return null;
	}

	private static void putRgba(ByteBuffer buffer, int offset, int red, int green, int blue, int alpha) {
		buffer.put(offset, (byte)red);
		buffer.put(offset + 1, (byte)green);
		buffer.put(offset + 2, (byte)blue);
		buffer.put(offset + 3, (byte)alpha);
	}

	private static void assertDominant(int argb, int shift, String message) {
		int dominant = argb >>> shift & 0xff;
		int firstOther = argb >>> (shift == 16 ? 8 : 16) & 0xff;
		int secondOther = argb & 0xff;
		if (dominant < 200 || dominant <= firstOther || dominant <= secondOther)
			throw new AssertionError(message + ": got 0x" + Integer.toHexString(argb));
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertThrows(ThrowingRunnable action, String message) {
		try {
			action.run();
		} catch (Exception expected) {
			return;
		}
		throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private GifAnimationExporterTest() {}
}
