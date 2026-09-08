package com.openspine;

/** Headless checks for native Windows title-bar color encoding and safe fallback behavior. */
public final class WindowsTitleBarTest {
	public static void main(String[] args) {
		assertEquals(0x241C19, WindowsTitleBar.colorRef(25, 28, 36),
			"Windows COLORREF stores red in the low byte");
		assertEquals(0xCCBBAA, WindowsTitleBar.colorRef(
			new com.badlogic.gdx.graphics.Color(0xAA / 255f, 0xBB / 255f, 0xCC / 255f, 1f)),
			"LibGDX colors convert to Windows COLORREF values");
		assertTrue(!WindowsTitleBar.applyDark(0L), "a missing native window handle is ignored");
		long customStyle = WindowsTitleBar.customFrameStyle(org.lwjgl.system.windows.User32.WS_CAPTION);
		assertTrue((customStyle & org.lwjgl.system.windows.User32.WS_CAPTION) == 0,
			"the custom frame removes the native caption");
		assertTrue((customStyle & org.lwjgl.system.windows.User32.WS_THICKFRAME) != 0
			&& (customStyle & org.lwjgl.system.windows.User32.WS_MINIMIZEBOX) != 0
			&& (customStyle & org.lwjgl.system.windows.User32.WS_MAXIMIZEBOX) != 0
			&& (customStyle & org.lwjgl.system.windows.User32.WS_SYSMENU) != 0,
			"the custom frame preserves resizing and standard window commands");
		assertTrue(!WindowsTitleBar.installCustomFrame(0L), "a missing custom-frame window is ignored");
		assertTrue(!WindowsTitleBar.windowProcedureRestored(0L, 0x1234L),
			"a failed WNDPROC replacement keeps the native callback alive");
		assertTrue(!WindowsTitleBar.windowProcedureRestored(0x5678L, 0x1234L),
			"replacing another subclass keeps this callback alive");
		assertTrue(WindowsTitleBar.windowProcedureRestored(0x1234L, 0x1234L),
			"a successful WNDPROC replacement allows the native callback to be released");
		assertTrue(!WindowsTitleBar.beginMove(0L), "a missing window cannot begin a native move");
		assertTrue(!WindowsTitleBar.minimize(0L), "a missing window cannot be minimized");
		assertTrue(!WindowsTitleBar.toggleMaximized(0L), "a missing window cannot be maximized");
		assertEquals(org.lwjgl.system.windows.User32.HTTOPLEFT,
			WindowsTitleBar.resizeHitTest(2, 3, 0, 0, 100, 80, 7, false),
			"the custom frame keeps its top-left resize corner");
		assertEquals(org.lwjgl.system.windows.User32.HTRIGHT,
			WindowsTitleBar.resizeHitTest(98, 40, 0, 0, 100, 80, 7, false),
			"the custom frame keeps its right resize edge");
		assertEquals(org.lwjgl.system.windows.User32.HTCLIENT,
			WindowsTitleBar.resizeHitTest(50, 40, 0, 0, 100, 80, 7, false),
			"the custom frame leaves its interior to the editor");
		assertEquals(org.lwjgl.system.windows.User32.HTCLIENT,
			WindowsTitleBar.resizeHitTest(2, 3, 0, 0, 100, 80, 7, true),
			"a maximized window does not expose resize edges");
		assertTrue(WindowsTitleBar.isWindowControlRegion(870, 3, 0, 0, 1000, 700, 96),
			"the top resize strip yields to window controls");
		assertTrue(WindowsTitleBar.isWindowControlRegion(835, 35, 0, 0, 1000, 700, 144),
			"window control hit testing follows display scaling");
		assertTrue(!WindowsTitleBar.isWindowControlRegion(800, 3, 0, 0, 1000, 700, 96),
			"the remaining title bar keeps its resize strip");
		assertTrue(!WindowsTitleBar.isWindowControlRegion(999, 40, 0, 0, 1000, 700, 96),
			"the right resize edge remains available below the title bar");
		assertEquals(-12, WindowsTitleBar.signedLowWord(0x0000fff4L),
			"hit testing preserves negative screen X coordinates");
		assertEquals(-9, WindowsTitleBar.signedHighWord(0xfff70000L),
			"hit testing preserves negative screen Y coordinates");
		assertThrows(() -> WindowsTitleBar.colorRef(-1, 0, 0), "negative color components are rejected");
		assertThrows(() -> WindowsTitleBar.colorRef(0, 0, 256), "color components above 255 are rejected");
		System.out.println("ALL WINDOWS TITLE BAR TESTS PASSED");
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private WindowsTitleBarTest() {}
}
