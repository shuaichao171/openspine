package com.openspine.ui;

import com.badlogic.gdx.graphics.Color;

/** Headless regression tests for editable hexadecimal colors. */
public final class UiColorsTest {
	public static void main(String[] args) {
		Color rgba = UiColors.parse("#33669980");
		assertNear(0x33 / 255f, rgba.r, "red");
		assertNear(0x66 / 255f, rgba.g, "green");
		assertNear(0x99 / 255f, rgba.b, "blue");
		assertNear(0x80 / 255f, rgba.a, "alpha");
		Color rgb = UiColors.parse("abcdef");
		assertNear(1f, rgb.a, "six-digit color alpha");
		if (!"#ABCDEFff".equalsIgnoreCase(UiColors.format(rgb)))
			throw new AssertionError("formatted color did not round trip: " + UiColors.format(rgb));
		assertThrows(() -> UiColors.parse("#12345"), "short color");
		assertThrows(() -> UiColors.parse("#GG0000"), "invalid hex");
		Color dark = UiColors.parseRgb("#123ABC");
		if (!"#123ABC".equals(UiColors.formatRgb(dark)))
			throw new AssertionError("formatted RGB color did not round trip: " + UiColors.formatRgb(dark));
		assertNear(1f, dark.a, "RGB-only color alpha");
		assertThrows(() -> UiColors.parseRgb("#123ABCFF"), "dark color rejects alpha");
		assertThrows(() -> UiColors.parseRgb("#12XX34"), "invalid dark hex");
		System.out.println("ALL UI COLOR TESTS PASSED");
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.000001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message + ": expected IllegalArgumentException");
	}

	private UiColorsTest() {}
}
