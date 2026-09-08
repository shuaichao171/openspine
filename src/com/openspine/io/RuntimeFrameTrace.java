package com.openspine.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Constraint;
import com.esotericsoftware.spine.PhysicsConstraint;
import com.esotericsoftware.spine.PhysicsConstraintPose;
import com.esotericsoftware.spine.Slider;
import com.esotericsoftware.spine.SliderPose;
import com.openspine.model.SkeletonDocument;

/** Golden trace format for frame-by-frame comparison with an official Spine export. */
public final class RuntimeFrameTrace {
	public static final int FORMAT_VERSION = 1;

	public int version = FORMAT_VERSION;
	public String producer;
	public String producerVersion;
	public String spineVersion;
	public String sourceSha256;
	public String animation;
	public float fps;
	public ArrayList<Frame> frames = new ArrayList<>();

	public static RuntimeFrameTrace capture(SkeletonDocument document, String animation,
		float fps, float duration) {
		if (document == null || document.data == null || document.skeleton == null
			|| document.animationState == null) throw new IllegalArgumentException("Runtime trace document is incomplete");
		if (animation == null || animation.isBlank() || document.data.findAnimation(animation) == null)
			throw new IllegalArgumentException("Runtime trace animation does not exist");
		if (!Float.isFinite(fps) || fps <= 0f || !Float.isFinite(duration) || duration < 0f)
			throw new IllegalArgumentException("Runtime trace FPS and duration are invalid");

		String previousAnimation = document.getCurrentAnimationName();
		float previousTime = document.getCurrentTime();
		boolean previousPlaying = document.playing;
		boolean previousSimulation = document.isPhysicsSimulationEnabled();
		boolean previousDeterministic = document.isPhysicsDeterministic();
		boolean previousAutoSelect = document.shouldAutoSelectAnimation();
		RuntimeFrameTrace trace = new RuntimeFrameTrace();
		trace.producer = "OpenSpine";
		trace.producerVersion = "development";
		trace.spineVersion = document.data.getVersion();
		trace.animation = animation;
		trace.fps = fps;
		try {
			document.setAnimation(animation);
			document.setPlaying(false);
			document.setPhysicsSimulationEnabled(true);
			document.setPhysicsDeterministic(true);
			double exactFrame = (double)duration * fps;
			if (exactFrame > Integer.MAX_VALUE - 1d)
				throw new IllegalArgumentException("Runtime trace contains too many frames");
			int lastFrame = Math.max(0, (int)Math.floor(exactFrame));
			for (int frame = 0; frame <= lastFrame; frame++) {
				float time = Math.min(duration, frame / fps);
				document.seek(time);
				document.completePendingPhysicsSeek();
				trace.frames.add(captureFrame(document, frame, time));
			}
			float lastTime = trace.frames.get(trace.frames.size() - 1).time;
			if (lastTime < duration) {
				document.seek(duration);
				document.completePendingPhysicsSeek();
				trace.frames.add(captureFrame(document, lastFrame + 1, duration));
			}
		} finally {
			if (previousAnimation.isBlank()) document.clearAnimation();
			else document.setAnimation(previousAnimation);
			document.setPhysicsSimulationEnabled(previousSimulation);
			document.setPhysicsDeterministic(previousDeterministic);
			document.seek(previousTime);
			document.completePendingPhysicsSeek();
			document.setPlaying(previousPlaying);
			document.restoreAutomaticAnimationSelection(previousAutoSelect);
		}
		return trace;
	}

	private static Frame captureFrame(SkeletonDocument document, int index, float time) {
		Frame frame = new Frame();
		frame.index = index;
		frame.time = time;
		for (Bone bone : document.skeleton.getBones()) {
			var pose = bone.getAppliedPose();
			BoneSample sample = new BoneSample();
			sample.name = bone.getData().getName();
			sample.worldX = pose.getWorldX();
			sample.worldY = pose.getWorldY();
			sample.a = pose.getA();
			sample.b = pose.getB();
			sample.c = pose.getC();
			sample.d = pose.getD();
			frame.bones.add(sample);
		}
		for (Constraint<?, ?, ?> constraint : document.skeleton.getConstraints()) {
			if (constraint instanceof PhysicsConstraint physics) {
				PhysicsConstraintPose pose = physics.getAppliedPose();
				PhysicsSample sample = new PhysicsSample();
				sample.name = physics.getData().getName();
				sample.inertia = pose.getInertia();
				sample.strength = pose.getStrength();
				sample.damping = pose.getDamping();
				sample.massInverse = pose.getMassInverse();
				sample.wind = pose.getWind();
				sample.gravity = pose.getGravity();
				sample.mix = pose.getMix();
				frame.physics.add(sample);
			} else if (constraint instanceof Slider slider) {
				SliderPose pose = slider.getAppliedPose();
				SliderSample sample = new SliderSample();
				sample.name = slider.getData().getName();
				sample.time = pose.getTime();
				sample.mix = pose.getMix();
				frame.sliders.add(sample);
			}
		}
		return frame;
	}

	public static Comparison compare(RuntimeFrameTrace expected, RuntimeFrameTrace actual,
		float absoluteTolerance, float relativeTolerance) {
		if (expected == null || actual == null) throw new IllegalArgumentException("Both runtime traces are required");
		if (!Float.isFinite(absoluteTolerance) || absoluteTolerance < 0f
			|| !Float.isFinite(relativeTolerance) || relativeTolerance < 0f)
			throw new IllegalArgumentException("Runtime trace tolerances are invalid");
		if (expected.version != actual.version)
			return Comparison.difference(-1, "version", expected.version, actual.version, 0f);
		if (!Objects.equals(expected.animation, actual.animation))
			return Comparison.difference(-1, "animation", 1f, 0f, 0f);
		if (!sameOptionalMetadata(expected.spineVersion, actual.spineVersion))
			return Comparison.difference(-1, "spineVersion", 1f, 0f, 0f);
		if (!sameOptionalMetadata(expected.sourceSha256, actual.sourceSha256))
			return Comparison.difference(-1, "sourceSha256", 1f, 0f, 0f);
		Comparison metadataDifference = compareValue(-1, "fps", expected.fps, actual.fps,
			absoluteTolerance, relativeTolerance);
		if (metadataDifference != null) return metadataDifference;
		if (expected.frames.size() != actual.frames.size())
			return Comparison.difference(-1, "frames.count", expected.frames.size(), actual.frames.size(), 0f);
		for (int index = 0; index < expected.frames.size(); index++) {
			Frame left = expected.frames.get(index), right = actual.frames.get(index);
			Comparison difference = left.index == right.index ? null
				: Comparison.difference(index, "index", left.index, right.index, 0f);
			if (difference != null) return difference;
			difference = compareValue(index, "time", left.time, right.time,
				absoluteTolerance, relativeTolerance);
			if (difference != null) return difference;
			difference = compareNamed(index, "bone", left.bones, right.bones,
				BoneSample::values, absoluteTolerance, relativeTolerance);
			if (difference != null) return difference;
			difference = compareNamed(index, "physics", left.physics, right.physics,
				PhysicsSample::values, absoluteTolerance, relativeTolerance);
			if (difference != null) return difference;
			difference = compareNamed(index, "slider", left.sliders, right.sliders,
				SliderSample::values, absoluteTolerance, relativeTolerance);
			if (difference != null) return difference;
		}
		return Comparison.match();
	}

	private static boolean sameOptionalMetadata(String expected, String actual) {
		if ((expected == null || expected.isBlank()) && (actual == null || actual.isBlank())) return true;
		return Objects.equals(expected, actual);
	}

	private static <T extends NamedSample> Comparison compareNamed(int frame, String group,
		ArrayList<T> expected, ArrayList<T> actual, java.util.function.Function<T, Map<String, Float>> values,
		float absoluteTolerance, float relativeTolerance) {
		Map<String, T> actualByName = new LinkedHashMap<>();
		for (T sample : actual) {
			if (actualByName.put(sample.name(), sample) != null)
				return Comparison.difference(frame, group + "." + sample.name() + ".duplicate", 0f, 1f, 0f);
		}
		if (expected.size() != actual.size())
			return Comparison.difference(frame, group + ".count", expected.size(), actual.size(), 0f);
		HashSet<String> expectedNames = new HashSet<>();
		for (T left : expected) {
			if (!expectedNames.add(left.name()))
				return Comparison.difference(frame, group + "." + left.name() + ".duplicate", 1f, 0f, 0f);
			T right = actualByName.get(left.name());
			if (right == null) return Comparison.difference(frame, group + "." + left.name() + ".missing", 1f, 0f, 0f);
			Map<String, Float> rightValues = values.apply(right);
			for (Map.Entry<String, Float> channel : values.apply(left).entrySet()) {
				Comparison difference = compareValue(frame, group + "." + left.name() + "." + channel.getKey(),
					channel.getValue(), rightValues.get(channel.getKey()), absoluteTolerance, relativeTolerance);
				if (difference != null) return difference;
			}
		}
		return null;
	}

	private static Comparison compareValue(int frame, String channel, float expected, float actual,
		float absoluteTolerance, float relativeTolerance) {
		float tolerance = Math.max(absoluteTolerance, Math.abs(expected) * relativeTolerance);
		if (!Float.isFinite(expected) || !Float.isFinite(actual) || Math.abs(expected - actual) > tolerance)
			return Comparison.difference(frame, channel, expected, actual, tolerance);
		return null;
	}

	public static RuntimeFrameTrace read(Path path) throws IOException {
		RuntimeFrameTrace trace;
		try { trace = json().fromJson(RuntimeFrameTrace.class, Files.readString(path, StandardCharsets.UTF_8)); }
		catch (RuntimeException error) { throw new IOException("Runtime trace is not valid JSON", error); }
		if (trace == null || trace.version != FORMAT_VERSION)
			throw new IOException("Unsupported runtime trace format");
		try { validate(trace); }
		catch (IllegalArgumentException error) { throw new IOException(error.getMessage(), error); }
		return trace;
	}

	public void write(Path path) throws IOException {
		if (path == null || path.toAbsolutePath().getParent() == null)
			throw new IllegalArgumentException("Runtime trace output path is invalid");
		validate(this);
		Files.createDirectories(path.toAbsolutePath().getParent());
		Files.writeString(path, json().prettyPrint(this) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private static Json json() {
		Json json = new Json(JsonWriter.OutputType.json);
		json.setIgnoreUnknownFields(true);
		return json;
	}

	private static void validate(RuntimeFrameTrace trace) {
		if (trace == null || trace.version != FORMAT_VERSION)
			throw new IllegalArgumentException("Unsupported runtime trace format");
		if (trace.animation == null || trace.animation.isBlank())
			throw new IllegalArgumentException("Runtime trace animation is missing");
		if (!Float.isFinite(trace.fps) || trace.fps <= 0f)
			throw new IllegalArgumentException("Runtime trace FPS is invalid");
		if (trace.frames == null) throw new IllegalArgumentException("Runtime trace frames are missing");
		for (int index = 0; index < trace.frames.size(); index++) {
			Frame frame = trace.frames.get(index);
			if (frame == null || !Float.isFinite(frame.time) || frame.time < 0f)
				throw new IllegalArgumentException("Runtime trace frame " + index + " is invalid");
			if (frame.bones == null || frame.physics == null || frame.sliders == null)
				throw new IllegalArgumentException("Runtime trace frame " + index + " samples are missing");
			validateNames(frame.bones, "bone", index);
			validateNames(frame.physics, "physics", index);
			validateNames(frame.sliders, "slider", index);
		}
	}

	private static <T extends NamedSample> void validateNames(ArrayList<T> samples, String group, int frame) {
		for (T sample : samples)
			if (sample == null || sample.name() == null || sample.name().isBlank())
				throw new IllegalArgumentException("Runtime trace " + group + " name is missing at frame " + frame);
	}

	private interface NamedSample { String name(); }

	public static final class Frame {
		public int index;
		public float time;
		public ArrayList<BoneSample> bones = new ArrayList<>();
		public ArrayList<PhysicsSample> physics = new ArrayList<>();
		public ArrayList<SliderSample> sliders = new ArrayList<>();
	}

	public static final class BoneSample implements NamedSample {
		public String name;
		public float worldX, worldY, a, b, c, d;
		@Override public String name() { return name; }
		Map<String, Float> values() {
			return Map.of("worldX", worldX, "worldY", worldY, "a", a, "b", b, "c", c, "d", d);
		}
	}

	public static final class PhysicsSample implements NamedSample {
		public String name;
		public float inertia, strength, damping, massInverse, wind, gravity, mix;
		@Override public String name() { return name; }
		Map<String, Float> values() {
			Map<String, Float> values = new LinkedHashMap<>();
			values.put("inertia", inertia); values.put("strength", strength); values.put("damping", damping);
			values.put("massInverse", massInverse); values.put("wind", wind); values.put("gravity", gravity);
			values.put("mix", mix);
			return values;
		}
	}

	public static final class SliderSample implements NamedSample {
		public String name;
		public float time, mix;
		@Override public String name() { return name; }
		Map<String, Float> values() { return Map.of("time", time, "mix", mix); }
	}

	public record Comparison(boolean matches, int frame, String channel, float expected,
		float actual, float tolerance) {
		static Comparison match() { return new Comparison(true, -1, "", 0f, 0f, 0f); }
		static Comparison difference(int frame, String channel, float expected, float actual, float tolerance) {
			return new Comparison(false, frame, channel, expected, actual, tolerance);
		}
	}
}
