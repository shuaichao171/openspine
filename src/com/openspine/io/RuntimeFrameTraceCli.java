package com.openspine.io;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.openspine.model.SkeletonDocument;

/** CLI workflow for capturing OpenSpine runtime traces and comparing official golden JSON. */
public final class RuntimeFrameTraceCli {
	private static final float DEFAULT_ABSOLUTE_TOLERANCE = 0.0001f;
	private static final float DEFAULT_RELATIVE_TOLERANCE = 0.0001f;

	public static void main(String[] args) {
		int exitCode = run(args, System.out, System.err);
		if (exitCode != 0) System.exit(exitCode);
	}

	static int run(String[] args, PrintStream output, PrintStream errorOutput) {
		try {
			if (args == null || args.length == 0 || "help".equalsIgnoreCase(args[0])
				|| "--help".equalsIgnoreCase(args[0]) || "-h".equalsIgnoreCase(args[0])) {
				output.print(helpText());
				return 0;
			}
			return switch (args[0].toLowerCase(Locale.ROOT)) {
			case "capture" -> runCapture(parseCapture(args), output, errorOutput);
			case "compare" -> runCompare(parseCompare(args), output, errorOutput);
			default -> throw new IllegalArgumentException("未知 trace 命令: " + args[0]);
			};
		} catch (IllegalArgumentException error) {
			errorOutput.println("错误: " + safeMessage(error));
			errorOutput.println("使用 trace.bat --help 查看命令说明。");
			return 2;
		} catch (Exception error) {
			errorOutput.println("Runtime trace 失败: " + safeMessage(error));
			return 1;
		}
	}

	private static int runCompare(CompareCommand command, PrintStream output,
		PrintStream errorOutput) throws Exception {
		RuntimeFrameTrace expected = RuntimeFrameTrace.read(command.expected);
		requireOfficialGolden(expected);
		RuntimeFrameTrace actual = RuntimeFrameTrace.read(command.actual);
		return reportComparison(expected, actual, command.absoluteTolerance,
			command.relativeTolerance, output, errorOutput);
	}

	private static int runCapture(CaptureCommand command, PrintStream output,
		PrintStream errorOutput) {
		AtomicInteger exitCode = new AtomicInteger(1);
		AtomicReference<String> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
		configuration.setTitle("OpenSpine Runtime Trace");
		configuration.setWindowedMode(32, 32);
		configuration.setInitialVisible(false);
		configuration.disableAudio(true);
		configuration.setForegroundFPS(30);
		try {
			new Lwjgl3Application(new ApplicationAdapter() {
				@Override public void create() {
					SkeletonDocument document = new SkeletonDocument();
					try {
						Path runtimeSource = loadCaptureSource(document, command.project);
						document.animationState = new AnimationState(new AnimationStateData(document.data));
						var animation = document.data.findAnimation(command.animation);
						if (animation == null)
							throw new IllegalArgumentException("动画不存在: " + command.animation);
						float duration = command.duration == null ? Math.max(0f, animation.getDuration())
							: command.duration;
						RuntimeFrameTrace actual = RuntimeFrameTrace.capture(document, command.animation,
							command.fps, duration);
						actual.sourceSha256 = sha256(runtimeSource);
						actual.write(command.output);
						String captured = "TRACE_CAPTURED output=" + command.output.toAbsolutePath().normalize()
							+ " frames=" + actual.frames.size() + " animation=" + actual.animation
							+ " fps=" + actual.fps + " duration=" + duration;
						if (command.golden == null) {
							result.set(captured);
							exitCode.set(0);
						} else {
							RuntimeFrameTrace expected = RuntimeFrameTrace.read(command.golden);
							requireOfficialGolden(expected);
							RuntimeFrameTrace.Comparison comparison = RuntimeFrameTrace.compare(expected, actual,
								command.absoluteTolerance, command.relativeTolerance);
							result.set(captured + System.lineSeparator()
								+ comparisonMessage(expected, actual, comparison));
							exitCode.set(comparison.matches() ? 0 : 3);
						}
					} catch (Throwable error) {
						failure.set(error);
					} finally {
						document.dispose();
						Gdx.app.exit();
					}
				}
			}, configuration);
		} catch (Throwable error) {
			failure.compareAndSet(null, error);
		}
		if (failure.get() != null) {
			errorOutput.println("Runtime trace 捕获失败: " + safeMessage(failure.get()));
			return 1;
		}
		PrintStream stream = exitCode.get() == 3 ? errorOutput : output;
		if (result.get() != null) stream.println(result.get());
		return exitCode.get();
	}

	private static Path loadCaptureSource(SkeletonDocument document, Path project) {
		if (!SkeletonLoader.isSpineProjectFileName(project.getFileName().toString())) {
			SkeletonLoader.load(document, new FileHandle(project.toFile()));
			return project;
		}
		SpineProjectImporter.PreparedProject prepared = SpineProjectImporter.prepare(new FileHandle(project.toFile()));
		try {
			Path originalRuntime = prepared.runtimeSource();
			Path runtimeSource = originalRuntime == null ? prepared.skeletonFile() : originalRuntime;
			SpineProjectImporter.loadPrepared(document, prepared);
			return runtimeSource;
		} catch (RuntimeException error) {
			prepared.close();
			throw error;
		}
	}

	private static int reportComparison(RuntimeFrameTrace expected, RuntimeFrameTrace actual,
		float absoluteTolerance, float relativeTolerance, PrintStream output,
		PrintStream errorOutput) {
		RuntimeFrameTrace.Comparison comparison = RuntimeFrameTrace.compare(expected, actual,
			absoluteTolerance, relativeTolerance);
		String message = comparisonMessage(expected, actual, comparison);
		if (comparison.matches()) output.println(message); else errorOutput.println(message);
		return comparison.matches() ? 0 : 3;
	}

	static String comparisonMessage(RuntimeFrameTrace expected, RuntimeFrameTrace actual,
		RuntimeFrameTrace.Comparison comparison) {
		if (comparison.matches())
			return "TRACE_MATCH frames=" + actual.frames.size() + " animation=" + actual.animation
				+ " fps=" + actual.fps;
		String expectedValue = Float.toString(comparison.expected());
		String actualValue = Float.toString(comparison.actual());
		if ("animation".equals(comparison.channel())) {
			expectedValue = String.valueOf(expected.animation);
			actualValue = String.valueOf(actual.animation);
		} else if ("spineVersion".equals(comparison.channel())) {
			expectedValue = String.valueOf(expected.spineVersion);
			actualValue = String.valueOf(actual.spineVersion);
		} else if ("sourceSha256".equals(comparison.channel())) {
			expectedValue = String.valueOf(expected.sourceSha256);
			actualValue = String.valueOf(actual.sourceSha256);
		}
		String time = "n/a";
		if (comparison.frame() >= 0 && comparison.frame() < expected.frames.size())
			time = Float.toString(expected.frames.get(comparison.frame()).time);
		return "TRACE_DIFFERENCE frame=" + comparison.frame() + " time=" + time
			+ " channel=" + comparison.channel() + " expected=" + expectedValue
			+ " actual=" + actualValue + " tolerance=" + comparison.tolerance();
	}

	private static CaptureCommand parseCapture(String[] args) {
		if (args.length < 2) throw new IllegalArgumentException("capture 需要骨架工程路径。");
		Path project = path(args[1], "骨架工程");
		String animation = null;
		Path output = null, golden = null;
		Float duration = null;
		float fps = 30f, absoluteTolerance = DEFAULT_ABSOLUTE_TOLERANCE;
		float relativeTolerance = DEFAULT_RELATIVE_TOLERANCE;
		for (int index = 2; index < args.length; index++) {
			String option = args[index];
			String value = requireValue(args, ++index, option);
			switch (option) {
			case "--animation" -> animation = value;
			case "--fps" -> fps = positiveFloat(value, "--fps");
			case "--duration" -> duration = nonNegativeFloat(value, "--duration");
			case "--output" -> output = path(value, "输出 trace");
			case "--golden" -> golden = path(value, "官方 golden trace");
			case "--abs" -> absoluteTolerance = nonNegativeFloat(value, "--abs");
			case "--rel" -> relativeTolerance = nonNegativeFloat(value, "--rel");
			default -> throw new IllegalArgumentException("未知 capture 选项: " + option);
			}
		}
		if (!Files.isRegularFile(project)) throw new IllegalArgumentException("骨架工程不存在: " + project);
		if (!SkeletonLoader.isSupportedFileName(project.getFileName().toString()))
			throw new IllegalArgumentException("骨架工程必须是 .spine、.json 或 .skel。");
		if (animation == null || animation.isBlank()) throw new IllegalArgumentException("缺少 --animation。");
		if (output == null) throw new IllegalArgumentException("缺少 --output。");
		if (golden != null && !Files.isRegularFile(golden))
			throw new IllegalArgumentException("官方 golden trace 不存在: " + golden);
		return new CaptureCommand(project, animation, fps, duration, output, golden,
			absoluteTolerance, relativeTolerance);
	}

	private static CompareCommand parseCompare(String[] args) {
		if (args.length < 3) throw new IllegalArgumentException("compare 需要 official.json 和 actual.json。");
		Path expected = path(args[1], "官方 golden trace");
		Path actual = path(args[2], "实际 trace");
		float absoluteTolerance = DEFAULT_ABSOLUTE_TOLERANCE;
		float relativeTolerance = DEFAULT_RELATIVE_TOLERANCE;
		for (int index = 3; index < args.length; index++) {
			String option = args[index];
			String value = requireValue(args, ++index, option);
			switch (option) {
			case "--abs" -> absoluteTolerance = nonNegativeFloat(value, "--abs");
			case "--rel" -> relativeTolerance = nonNegativeFloat(value, "--rel");
			default -> throw new IllegalArgumentException("未知 compare 选项: " + option);
			}
		}
		if (!Files.isRegularFile(expected)) throw new IllegalArgumentException("官方 golden trace 不存在: " + expected);
		if (!Files.isRegularFile(actual)) throw new IllegalArgumentException("实际 trace 不存在: " + actual);
		return new CompareCommand(expected, actual, absoluteTolerance, relativeTolerance);
	}

	private static String requireValue(String[] args, int index, String option) {
		if (index >= args.length || args[index] == null || args[index].isBlank())
			throw new IllegalArgumentException(option + " 需要一个值。");
		return args[index];
	}

	private static Path path(String value, String label) {
		try { return Path.of(value).toAbsolutePath().normalize(); }
		catch (RuntimeException error) { throw new IllegalArgumentException(label + "路径无效: " + value, error); }
	}

	private static float positiveFloat(String value, String option) {
		float result = number(value, option);
		if (result <= 0f) throw new IllegalArgumentException(option + " 必须大于 0。");
		return result;
	}

	private static float nonNegativeFloat(String value, String option) {
		float result = number(value, option);
		if (result < 0f) throw new IllegalArgumentException(option + " 不能小于 0。");
		return result;
	}

	private static float number(String value, String option) {
		try {
			float result = Float.parseFloat(value);
			if (!Float.isFinite(result)) throw new NumberFormatException();
			return result;
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(option + " 需要有限数值: " + value);
		}
	}

	static String helpText() {
		return """
			OpenSpine Runtime Trace - Physics/Slider 逐帧对照

			用法:
			  trace.bat capture <工程.spine|骨架.json|骨架.skel> --animation <名称> --output <actual.json>
			            [--fps 30] [--duration 秒] [--golden official.json] [--abs 0.0001] [--rel 0.0001]
			  trace.bat compare <official.json> <actual.json> [--abs 0.0001] [--rel 0.0001]

			说明:
			  capture 使用 OpenSpine runtime 生成 actual；--golden 必须带 Spine Editor 生成方、版本和源文件 SHA-256。
			  未指定 --duration 时使用动画时长。差异退出码为 3，格式错误为 2，运行错误为 1。
			  .spine 输入仍通过官方 Spine CLI 导出 runtime 数据，因此需要可用且已激活的 Spine CLI。
			""";
	}

	static void requireOfficialGolden(RuntimeFrameTrace trace) {
		if (trace == null || !"Spine Editor".equalsIgnoreCase(trace.producer == null ? "" : trace.producer.trim()))
			throw new IllegalArgumentException("golden 缺少可信的 Spine Editor 生成方标记。");
		if (trace.producerVersion == null || trace.producerVersion.isBlank())
			throw new IllegalArgumentException("golden 缺少官方 Spine Editor 版本。");
		if (trace.spineVersion == null || trace.spineVersion.isBlank())
			throw new IllegalArgumentException("golden 缺少 Spine 数据版本。");
		if (trace.sourceSha256 == null || !trace.sourceSha256.matches("(?i)[0-9a-f]{64}"))
			throw new IllegalArgumentException("golden 缺少有效的源文件 SHA-256。");
	}

	private static String sha256(Path path) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (var input = Files.newInputStream(path)) {
			byte[] buffer = new byte[65536];
			for (int count; (count = input.read(buffer)) >= 0; )
				if (count > 0) digest.update(buffer, 0, count);
		}
		StringBuilder value = new StringBuilder(64);
		for (byte item : digest.digest()) value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
		return value.toString();
	}

	private static String safeMessage(Throwable error) {
		String message = error == null ? null : error.getMessage();
		return message == null || message.isBlank()
			? (error == null ? "未知错误" : error.getClass().getSimpleName()) : message;
	}

	private record CaptureCommand(Path project, String animation, float fps, Float duration,
		Path output, Path golden, float absoluteTolerance, float relativeTolerance) {}
	private record CompareCommand(Path expected, Path actual, float absoluteTolerance,
		float relativeTolerance) {}

	private RuntimeFrameTraceCli() {}
}
