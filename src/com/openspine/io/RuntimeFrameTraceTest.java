package com.openspine.io;

import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.openspine.model.SkeletonDocument;

/** Headless checks for official Spine golden-trace comparison and diagnostics. */
public final class RuntimeFrameTraceTest {
	public static void main(String[] args) throws Exception {
		RuntimeFrameTrace expected = trace();
		RuntimeFrameTrace actual = trace();
		assertTrue(RuntimeFrameTrace.compare(expected, actual, 0.0001f, 0.0001f).matches(),
			"identical official traces match");
		actual.frames.get(0).sliders.get(0).time += 0.01f;
		RuntimeFrameTrace.Comparison difference = RuntimeFrameTrace.compare(expected, actual, 0.0001f, 0f);
		assertTrue(!difference.matches() && difference.frame() == 0
			&& difference.channel().equals("slider.driver.time"),
			"the first Slider channel difference is reported precisely");
		actual = trace();
		actual.fps = 60f;
		difference = RuntimeFrameTrace.compare(expected, actual, 0.0001f, 0f);
		assertTrue(!difference.matches() && difference.frame() == -1 && difference.channel().equals("fps"),
			"trace sampling metadata is compared before frame values");
		actual = trace();
		actual.frames.get(0).index = 4;
		difference = RuntimeFrameTrace.compare(expected, actual, 0.0001f, 0f);
		assertTrue(!difference.matches() && difference.frame() == 0 && difference.channel().equals("index"),
			"official frame indices are part of the golden comparison");

		Path directory = Files.createTempDirectory("openspine-runtime-trace-");
		try {
			Path path = directory.resolve("official-golden.json");
			expected.write(path);
			RuntimeFrameTrace loaded = RuntimeFrameTrace.read(path);
			assertTrue(RuntimeFrameTrace.compare(expected, loaded, 0f, 0f).matches(),
				"golden traces round-trip without numeric changes");
		} finally {
			try (var paths = Files.walk(directory)) {
				paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (Exception ignored) {}
				});
			}
		}
		captureRestoresDocumentWithoutAnimation();
		capturesExactNonFrameAlignedDuration();
		System.out.println("ALL RUNTIME FRAME TRACE TESTS PASSED");
	}

	private static void captureRestoresDocumentWithoutAnimation() {
		SkeletonData data = new SkeletonData();
		data.getBones().add(new BoneData(0, "root", null));
		Animation animation = new Animation("sample");
		animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		animation.setDuration(1f);
		data.getAnimations().add(animation);
		SkeletonDocument document = new SkeletonDocument();
		document.data = data;
		document.skeleton = new Skeleton(data);
		document.animationState = new AnimationState(new AnimationStateData(data));
		document.setPlaying(true);
		document.setPhysicsSimulationEnabled(false);
		document.setPhysicsDeterministic(false);

		RuntimeFrameTrace captured = RuntimeFrameTrace.capture(document, "sample", 30f, 0f);

		assertTrue(captured.frames.size() == 1, "zero-duration capture contains its initial frame");
		assertTrue(document.getCurrentAnimationName().isEmpty(),
			"capture restores a document that originally had no selected animation");
		assertTrue(document.playing, "capture restores the playback flag");
		assertTrue(!document.isPhysicsSimulationEnabled() && !document.isPhysicsDeterministic(),
			"capture restores physics preview options");
	}

	private static void capturesExactNonFrameAlignedDuration() {
		SkeletonData data = new SkeletonData();
		data.getBones().add(new BoneData(0, "root", null));
		Animation animation = new Animation("sample");
		animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		animation.setDuration(1.01f);
		data.getAnimations().add(animation);
		SkeletonDocument document = new SkeletonDocument();
		document.data = data;
		document.skeleton = new Skeleton(data);
		document.animationState = new AnimationState(new AnimationStateData(data));

		RuntimeFrameTrace captured = RuntimeFrameTrace.capture(document, "sample", 30f, 1.01f);

		assertTrue(captured.frames.size() == 32, "non-frame-aligned capture appends one exact endpoint");
		RuntimeFrameTrace.Frame penultimate = captured.frames.get(30);
		RuntimeFrameTrace.Frame endpoint = captured.frames.get(31);
		assertTrue(penultimate.index == 30 && Math.abs(penultimate.time - 1f) < 0.000001f,
			"the last regular frame remains at the FPS grid");
		assertTrue(endpoint.index == 31 && Math.abs(endpoint.time - 1.01f) < 0.000001f,
			"the appended trace frame records the exact requested duration");
	}

	private static RuntimeFrameTrace trace() {
		RuntimeFrameTrace trace = new RuntimeFrameTrace();
		trace.animation = "walk";
		trace.fps = 30f;
		RuntimeFrameTrace.Frame frame = new RuntimeFrameTrace.Frame();
		frame.index = 0;
		RuntimeFrameTrace.BoneSample bone = new RuntimeFrameTrace.BoneSample();
		bone.name = "root";
		bone.a = bone.d = 1f;
		frame.bones.add(bone);
		RuntimeFrameTrace.PhysicsSample physics = new RuntimeFrameTrace.PhysicsSample();
		physics.name = "spring";
		physics.inertia = 1f;
		physics.massInverse = 1f;
		frame.physics.add(physics);
		RuntimeFrameTrace.SliderSample slider = new RuntimeFrameTrace.SliderSample();
		slider.name = "driver";
		slider.time = 0.25f;
		slider.mix = 0.8f;
		frame.sliders.add(slider);
		trace.frames.add(frame);
		return trace;
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private RuntimeFrameTraceTest() {}
}
