package com.openspine.io;

import java.nio.file.Path;
import java.util.List;

/** Headless validation tests for JPEG animation sequence settings and naming. */
public final class JpegAnimationExporterTest {
	public static void main(String[] args) {
		JpegAnimationExporter.Settings settings = new JpegAnimationExporter.Settings(
			30f, 7, 14, 1.25f, 6, 87);
		assertEquals(8, settings.frameCount(), "inclusive JPEG sequence frame count");
		assertEquals(0, settings.warmUp(), "legacy JPEG sequence settings default to no warm-up");
		JpegAnimationExporter.Settings withoutLastFrame = new JpegAnimationExporter.Settings(
			30f, 7, 14, 0, 1.25f, 6, 87, false);
		assertEquals(7, withoutLastFrame.frameCount(), "JPEG sequence can omit the last frame");
		assertEquals(13, withoutLastFrame.lastExportFrame(), "JPEG sequence reports its actual last output frame");
		List<Path> targets = JpegAnimationExporter.sequenceTargets(Path.of("walk.jpeg"), 7, 14);
		assertEquals("walk_0007.jpeg", targets.get(0).getFileName().toString(),
			"JPEG sequence preserves the requested extension");
		assertEquals("walk_0014.jpeg", targets.get(targets.size() - 1).getFileName().toString(),
			"JPEG sequence uses actual frame numbers");
		assertThrows(() -> new JpegAnimationExporter.Settings(30f, 0, 1, 1f, 0, 101),
			"JPEG sequence quality above 100 is rejected");
		assertThrows(() -> new JpegAnimationExporter.Settings(30f, 0, 1,
			JpegAnimationExporter.MAX_WARM_UP + 1, 1f, 0, 90),
			"JPEG sequence warm-up above 100 is rejected");
		assertThrows(() -> new JpegAnimationExporter.Settings(30f, 1, 1, 0, 1f, 0, 90, false),
			"excluding the only JPEG sequence frame is rejected");
		assertThrows(() -> JpegAnimationExporter.sequenceTargets(Path.of("walk.png"), 0, 1),
			"JPEG sequence targets require a JPEG extension");
		System.out.println("ALL JPEG ANIMATION EXPORTER TESTS PASSED");
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (RuntimeException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private JpegAnimationExporterTest() {}
}
