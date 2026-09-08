package com.openspine.ui;

/** Headless regression tests for lossless editable number formatting. */
public final class UiNumbersTest {
	public static void main(String[] args) {
		float[] values = {1.2345678f, -98.76543f, 0.00000123f, 16777215f, Float.MIN_VALUE, Float.MAX_VALUE};
		for (float value : values) {
			float parsed = Float.parseFloat(UiNumbers.format(value));
			if (Float.floatToIntBits(parsed) != Float.floatToIntBits(value))
				throw new AssertionError("format changed " + value + " to " + parsed);
		}
		if (!"0".equals(UiNumbers.format(-0f))) throw new AssertionError("negative zero should display as zero");
		if (!"12".equals(UiNumbers.format(12f))) throw new AssertionError("whole numbers should omit .0");
		assertBits(6000f, UiNumbers.parseFinite(" 6000 "), "finite values outside a slider range remain editable");
		assertBits(Float.MAX_VALUE, UiNumbers.parseFinite(UiNumbers.format(Float.MAX_VALUE)),
			"maximum finite float remains editable");
		assertRejected("", "blank numeric input");
		assertRejected("NaN", "NaN numeric input");
		assertRejected("Infinity", "infinite numeric input");
		assertRejected("1e999", "overflowing numeric input");
		assertNear(1f / 24f, UiNumbers.snapTime(0.04f, 24f), "24 FPS snapping");
		assertNear(1f / 30f, UiNumbers.snapTime(0.04f, 30f), "30 FPS snapping");
		assertNear(1f / 30f, UiNumbers.snapTime(0.04f, 0f), "invalid FPS uses default");
		assertNear(1f / 30f, UiNumbers.snapTime(0.04f, Float.NaN), "non-finite FPS uses default");
		if (!Float.isNaN(UiNumbers.snapTime(Float.NaN, 24f)))
			throw new AssertionError("non-finite time must remain non-finite");
		assertNear(2f / 24f, UiNumbers.adjacentFrameTime(1f / 24f, 1f, 24f, 1),
			"next frame from exact frame");
		assertNear(0f, UiNumbers.adjacentFrameTime(0.04f, 1f, 24f, -1),
			"previous frame from between frames");
		assertNear(1f / 24f, UiNumbers.adjacentFrameTime(0.04f, 1f, 24f, 1),
			"next frame from between frames");
		assertNear(0f, UiNumbers.adjacentFrameTime(0f, 1f, 24f, -1),
			"previous frame clamps to start");
		assertNear(1.05f, UiNumbers.adjacentFrameTime(31f / 30f, 1.05f, 30f, 1),
			"next frame clamps to non-frame-aligned end");
		assertNear(1f / 30f, UiNumbers.adjacentFrameTime(0f, 1f, 0f, 1),
			"frame stepping uses default FPS");
		assertNear(0.5f, UiNumbers.adjacentFrameTime(0.5f, 1f, 30f, 0),
			"zero direction keeps current time");
		System.out.println("ALL UI NUMBER TESTS PASSED");
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.000001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertBits(float expected, float actual, String message) {
		if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertRejected(String text, String message) {
		try {
			UiNumbers.parseFinite(text);
			throw new AssertionError(message + " should be rejected");
		} catch (NumberFormatException expected) {
			// Expected.
		}
	}

	private UiNumbersTest() {}
}
