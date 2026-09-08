package com.openspine.ui;

/** Headless regression tests for appearance selection and system-theme parsing. */
public final class UiAppearanceTest {
	public static void main(String[] args) {
		assertEquals(UiAppearance.Mode.SYSTEM, UiAppearance.Mode.fromKey(null),
			"missing appearance mode follows the system");
		assertEquals(UiAppearance.Mode.LIGHT, UiAppearance.Mode.fromKey(" LIGHT "),
			"appearance mode keys ignore case and surrounding whitespace");
		assertEquals(UiAppearance.Theme.JADE, UiAppearance.Theme.fromKey("unknown"),
			"unknown themes restore the default");
		assertTrue(UiAppearance.resolveDark(UiAppearance.Mode.SYSTEM, true),
			"system mode follows a dark operating-system theme");
		assertTrue(!UiAppearance.resolveDark(UiAppearance.Mode.SYSTEM, false),
			"system mode follows a light operating-system theme");
		assertTrue(!UiAppearance.resolveDark(UiAppearance.Mode.LIGHT, true),
			"explicit light mode overrides the operating system");
		assertTrue(UiAppearance.resolveDark(UiAppearance.Mode.DARK, false),
			"explicit dark mode overrides the operating system");
		UiAppearance.Palette light = UiAppearance.resolve(UiAppearance.Mode.LIGHT, true,
			UiAppearance.Theme.AZURE, UiAppearance.Theme.CORAL);
		assertTrue(!light.dark() && light.theme() == UiAppearance.Theme.AZURE,
			"light and dark modes remember independent themes");
		UiAppearance.Palette dark = UiAppearance.resolve(UiAppearance.Mode.DARK, false,
			UiAppearance.Theme.AZURE, UiAppearance.Theme.CORAL);
		assertTrue(dark.dark() && dark.theme() == UiAppearance.Theme.CORAL,
			"dark mode resolves its own saved theme");
		assertTrue(light.text().r < light.appBackground().r,
			"light mode uses dark text on a light application surface");
		assertTrue(dark.text().r > dark.appBackground().r,
			"dark mode uses light text on a dark application surface");
		assertTrue(light.viewportBackground().r > 0.8f
			&& light.color(UiAppearance.Role.TIMELINE_BACKGROUND).r > 0.8f,
			"light mode also brightens the viewport and timeline work areas");
		assertTrue(dark.viewportBackground().r < 0.2f
			&& dark.color(UiAppearance.Role.TIMELINE_BACKGROUND).r < 0.2f,
			"dark mode keeps dark viewport and timeline work areas");
		assertTrue(dark.color(UiAppearance.Role.TIMELINE_ACTIVE).r
			> dark.color(UiAppearance.Role.TIMELINE_BACKGROUND).r,
			"dark timeline frames are brighter than the unused range");
		assertTrue(light.color(UiAppearance.Role.TIMELINE_ACTIVE).r
			> light.color(UiAppearance.Role.TIMELINE_BACKGROUND).r,
			"light timeline frames remain distinct from the unused range");
		assertTrue(dark.color(UiAppearance.Role.TIMELINE_ACTIVE_ROW_ALT).r
			> dark.color(UiAppearance.Role.TIMELINE_BACKGROUND).r,
			"alternate active rows do not blend into the unused range");
		assertTrue(light.viewportGrid().r < light.viewportBackground().r,
			"light viewport grid remains visible against its background");
		assertEquals(Boolean.TRUE, UiAppearance.windowsDarkModeFromRegistry(
			"AppsUseLightTheme    REG_DWORD    0x0"), "Windows dark registry value is recognized");
		assertEquals(Boolean.FALSE, UiAppearance.windowsDarkModeFromRegistry(
			"AppsUseLightTheme    REG_DWORD    0x1"), "Windows light registry value is recognized");
		assertEquals(null, UiAppearance.windowsDarkModeFromRegistry("missing"),
			"missing Windows theme data uses the safe fallback");
		System.out.println("ALL UI APPEARANCE TESTS PASSED");
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private UiAppearanceTest() {}
}
