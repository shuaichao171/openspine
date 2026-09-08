package com.openspine.io;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;

/** Headless coverage for ffmpeg command generation and event-audio collection. */
public final class FfmpegAnimationExporterTest {
	public static void main(String[] args) throws Exception {
		buildsWebmCommandWithAudioMix();
		buildsOpaqueMovCommand();
		buildsAnimatedWebpCommandWithoutAudio();
		encodesAnimatedWebpWhenFfmpegIsAvailable();
		collectsOnlyAudioInsideExportRange();
		System.out.println("FFMPEG ANIMATION EXPORTER TEST PASSED");
	}

	private static void buildsWebmCommandWithAudioMix() {
		Path root = Path.of("build", "ffmpeg-command").toAbsolutePath();
		FfmpegAnimationExporter.setConfiguredExecutable("ffmpeg-test");
		try {
			var settings = new FfmpegAnimationExporter.Settings(24f,
				FfmpegAnimationExporter.Format.WEBM, FfmpegAnimationExporter.Profile.QUALITY,
				true, 1920, 1080);
			var cue = new FfmpegAnimationExporter.AudioCue(root.resolve("step.wav"), 0.25d, 0.5f, -0.25f);
			List<String> command = FfmpegAnimationExporter.buildCommand(root, 48, settings,
				List.of(cue), root.resolve("movie.webm"));
			assertEquals("ffmpeg-test", command.get(0), "configured executable is used");
			assertOption(command, "-frames:v", "48", "frame count is explicit");
			assertOption(command, "-pix_fmt", "yuva420p", "transparent WebM keeps alpha");
			assertOption(command, "-crf", "22", "quality profile uses quality CRF");
			assertOption(command, "-c:a", "libopus", "WebM event audio uses Opus");
			String filters = option(command, "-filter_complex");
			assertContains(filters, "volume=0.500000", "event volume enters the filter");
			assertContains(filters, "c1=c1*0.750000", "negative balance attenuates the right channel");
			assertContains(filters, "adelay=250|250", "event time becomes an audio delay");
			assertContains(option(command, "-vf"), "1920", "maximum output width is included");
			assertContains(option(command, "-vf"), "pad=ceil(iw/2)*2:ceil(ih/2)*2:color=black@0",
				"transparent WebM pads odd frame dimensions without adding an opaque edge");
		} finally { FfmpegAnimationExporter.setConfiguredExecutable(""); }
	}

	private static void buildsOpaqueMovCommand() {
		Path root = Path.of("build", "ffmpeg-command").toAbsolutePath();
		var settings = new FfmpegAnimationExporter.Settings(30f,
			FfmpegAnimationExporter.Format.MOV, FfmpegAnimationExporter.Profile.FAST,
			false, 0, 0);
		List<String> command = FfmpegAnimationExporter.buildCommand(root, 3, settings,
			List.of(), root.resolve("movie.mov"));
		assertOption(command, "-profile:v", "2", "opaque fast MOV uses ProRes standard profile");
		assertOption(command, "-pix_fmt", "yuv444p10le", "opaque MOV omits alpha");
		assertTrue(command.contains("-an"), "video without cues explicitly omits audio");
		assertTrue(!command.contains("-filter_complex"), "video without cues needs no audio filter");
		assertTrue(!command.contains("-vf"), "ProRes 4:4:4 output preserves odd source dimensions");
	}

	private static void buildsAnimatedWebpCommandWithoutAudio() {
		Path root = Path.of("build", "ffmpeg-command").toAbsolutePath();
		var settings = new FfmpegAnimationExporter.Settings(20f,
			FfmpegAnimationExporter.Format.AWEBP, FfmpegAnimationExporter.Profile.QUALITY,
			true, 640, 480);
		List<String> command = FfmpegAnimationExporter.buildCommand(root, 12, settings,
			List.of(), root.resolve("animation.webp"));
		assertOption(command, "-c:v", "libwebp_anim", "AWEBP uses the animated WebP encoder");
		assertOption(command, "-loop", "0", "AWEBP loops indefinitely");
		assertOption(command, "-q:v", "95", "quality profile maps to WebP quality");
		assertOption(command, "-compression_level", "6", "quality profile uses maximum WebP compression");
		assertTrue(command.contains("-an"), "AWEBP explicitly disables audio");
		assertContains(option(command, "-vf"), "640", "AWEBP honors maximum dimensions");

		var cue = new FfmpegAnimationExporter.AudioCue(root.resolve("step.wav"), 0d, 1f, 0f);
		assertThrows(() -> FfmpegAnimationExporter.encode(List.of(root.resolve("frame.png")), settings,
			List.of(cue), root.resolve("animation.webp"), new CancellationToken()),
			"AWEBP rejects event audio before touching frame files");
	}

	private static void encodesAnimatedWebpWhenFfmpegIsAvailable() throws Exception {
		if (!FfmpegAnimationExporter.isAvailable()) return;
		Path directory = Files.createTempDirectory("openspine-awebp-");
		try {
			Path first = directory.resolve("first.png");
			Path second = directory.resolve("second.png");
			writeFrame(first, 0xffff0000);
			writeFrame(second, 0x8000ff00);
			Path target = directory.resolve("animation.webp");
			Path written = FfmpegAnimationExporter.encode(List.of(first, second),
				new FfmpegAnimationExporter.Settings(5f, FfmpegAnimationExporter.Format.AWEBP,
					FfmpegAnimationExporter.Profile.FAST, true, 0, 0),
				List.of(), target, new CancellationToken());
			byte[] bytes = Files.readAllBytes(written);
			assertTrue(bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I'
				&& bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
				&& bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P',
				"available ffmpeg produces a valid animated WebP container");
		} finally {
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	private static void writeFrame(Path path, int argb) throws Exception {
		BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
			image.setRGB(x, y, argb);
		if (!ImageIO.write(image, "png", path.toFile())) throw new AssertionError("PNG writer unavailable");
	}

	private static void collectsOnlyAudioInsideExportRange() throws Exception {
		Path directory = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "ffmpeg-audio-");
		try {
			Path audio = Files.createDirectories(directory.resolve("audio")).resolve("step.wav");
			Files.createFile(audio);
			EventData data = new EventData("step");
			data.setAudioPath("step.wav");
			Animation.EventTimeline timeline = new Animation.EventTimeline(3);
			timeline.setFrame(0, event(0.25f, data, 1f, 0f));
			timeline.setFrame(1, event(1.5f, data, 0.7f, 0.25f));
			timeline.setFrame(2, event(2.5f, data, 1f, 0f));
			Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
			timelines.add(timeline);
			Animation animation = new Animation("audio");
			animation.setTimelines(timelines, new IntArray());
			animation.setDuration(3f);

			List<FfmpegAnimationExporter.AudioCue> cues = FfmpegAnimationExporter.collectAudioCues(
				animation, 10, 19, 10f, null, directory.resolve("hero.json").toString(), "audio");
			assertEquals(1, cues.size(), "only events inside the selected frame range are mixed");
			assertNear(0.5d, cues.get(0).startSeconds(), "cue time is relative to the selected range");
			assertEquals(audio.toAbsolutePath().normalize(), cues.get(0).path(), "project audio path is resolved");
			assertNear(0.7d, cues.get(0).volume(), "event volume is retained");
			assertNear(0.25d, cues.get(0).balance(), "event balance is retained");
		} finally {
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	private static Event event(float time, EventData data, float volume, float balance) {
		Event event = new Event(time, data);
		event.setVolume(volume);
		event.setBalance(balance);
		return event;
	}

	private static String option(List<String> command, String name) {
		int index = command.indexOf(name);
		if (index < 0 || index + 1 >= command.size()) throw new AssertionError("missing option " + name + ": " + command);
		return command.get(index + 1);
	}

	private static void assertOption(List<String> command, String name, String expected, String message) {
		assertEquals(expected, option(command, name), message);
	}

	private static void assertContains(String actual, String expected, String message) {
		if (actual == null || !actual.contains(expected)) throw new AssertionError(message + ": " + actual);
	}

	private static void assertNear(double expected, double actual, String message) {
		if (Math.abs(expected - actual) > 0.00001d)
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertThrows(ThrowingRunnable action, String message) {
		try { action.run(); }
		catch (IllegalArgumentException expected) { return; }
		catch (Exception error) { throw new AssertionError(message + ": wrong exception " + error, error); }
		throw new AssertionError(message);
	}

	@FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }

	private FfmpegAnimationExporterTest() {}
}
