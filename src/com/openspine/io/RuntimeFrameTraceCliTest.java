package com.openspine.io;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Headless checks for the official golden-trace CLI diagnostics. */
public final class RuntimeFrameTraceCliTest {
	public static void main(String[] args) throws Exception {
		Path directory = Files.createTempDirectory("openspine-trace-cli-");
		try {
			Path official = directory.resolve("official.json");
			Path actual = directory.resolve("actual.json");
			RuntimeFrameTrace expected = trace();
			expected.producer = "Spine Editor";
			expected.producerVersion = "4.3.23";
			expected.sourceSha256 = "0".repeat(64);
			expected.write(official);
			RuntimeFrameTrace generated = trace();
			generated.sourceSha256 = expected.sourceSha256;
			generated.write(actual);

			Result match = run("compare", official.toString(), actual.toString(), "--abs", "0", "--rel", "0");
			assertTrue(match.code == 0 && match.output.contains("TRACE_MATCH") && match.error.isEmpty(),
				"matching official and actual traces return success");

			RuntimeFrameTrace changed = trace();
			changed.sourceSha256 = expected.sourceSha256;
			changed.frames.get(0).physics.get(0).gravity = 0.25f;
			changed.write(actual);
			Result mismatch = run("compare", official.toString(), actual.toString(), "--abs", "0.001");
			assertTrue(mismatch.code == 3 && mismatch.error.contains("frame=0")
				&& mismatch.error.contains("time=0.0")
				&& mismatch.error.contains("channel=physics.spring.gravity")
				&& mismatch.error.contains("expected=0.0")
				&& mismatch.error.contains("actual=0.25")
				&& mismatch.error.contains("tolerance=0.001"),
				"the CLI reports the first different frame, time, channel and values");

			Result invalid = run("compare", official.toString());
			assertTrue(invalid.code == 2 && invalid.error.contains("compare 需要"),
				"malformed CLI commands return a usage error");

			RuntimeFrameTrace untrusted = trace();
			untrusted.sourceSha256 = expected.sourceSha256;
			untrusted.write(official);
			Result rejected = run("compare", official.toString(), actual.toString());
			assertTrue(rejected.code == 2 && rejected.error.contains("生成方标记"),
				"an OpenSpine-generated file cannot be accepted as an official golden");

			expected.spineVersion = "";
			expected.write(official);
			Result missingSpineVersion = run("compare", official.toString(), actual.toString());
			assertTrue(missingSpineVersion.code == 2 && missingSpineVersion.error.contains("Spine 数据版本"),
				"an official golden without the Spine data version is rejected");
		} finally {
			try (var paths = Files.walk(directory)) {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (Exception ignored) {}
				});
			}
		}
		System.out.println("ALL RUNTIME FRAME TRACE CLI TESTS PASSED");
	}

	private static Result run(String... args) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream error = new ByteArrayOutputStream();
		int code;
		try (PrintStream stdout = new PrintStream(output, true, StandardCharsets.UTF_8);
			PrintStream stderr = new PrintStream(error, true, StandardCharsets.UTF_8)) {
			code = RuntimeFrameTraceCli.run(args, stdout, stderr);
		}
		return new Result(code, output.toString(StandardCharsets.UTF_8),
			error.toString(StandardCharsets.UTF_8));
	}

	private static RuntimeFrameTrace trace() {
		RuntimeFrameTrace trace = new RuntimeFrameTrace();
		trace.producer = "OpenSpine";
		trace.producerVersion = "test";
		trace.spineVersion = "4.3.0";
		trace.animation = "physics-slider";
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
		slider.mix = 1f;
		frame.sliders.add(slider);
		trace.frames.add(frame);
		return trace;
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private record Result(int code, String output, String error) {}

	private RuntimeFrameTraceCliTest() {}
}
