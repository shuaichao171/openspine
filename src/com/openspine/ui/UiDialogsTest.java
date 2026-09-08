package com.openspine.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;

/** Headless regression tests for modal dialog keyboard routing. */
public final class UiDialogsTest {
	public static void main(String[] args) {
		assertEquals(UiDialogs.KeyAction.CONFIRM, UiDialogs.keyAction(Input.Keys.ENTER),
			"main Enter confirms");
		assertEquals(UiDialogs.KeyAction.CONFIRM, UiDialogs.keyAction(Input.Keys.NUMPAD_ENTER),
			"numpad Enter confirms");
		assertEquals(UiDialogs.KeyAction.CANCEL, UiDialogs.keyAction(Input.Keys.ESCAPE),
			"Escape cancels");
		assertEquals(UiDialogs.KeyAction.NONE, UiDialogs.keyAction(Input.Keys.SPACE),
			"unrelated key is ignored");
		assertEquals("hero", UiDialogs.normalizedName("  hero  "), "names are trimmed");
		assertTrue(UiDialogs.normalizedName(null) == null, "null name is rejected");
		assertTrue(UiDialogs.normalizedName("   ") == null, "blank name is rejected");
		assertNear(UiDialogs.DIALOG_MAX_CONTENT_WIDTH, UiDialogs.dialogContentWidth(1920f),
			"wide stages keep a readable dialog line length");
		assertNear(312f, UiDialogs.dialogContentWidth(360f),
			"narrow stages preserve outer dialog margins");
		assertNear(UiDialogs.DIALOG_MAX_CONTENT_WIDTH, UiDialogs.dialogContentWidth(Float.NaN),
			"missing stage dimensions use the stable dialog width");
		Group window = new Group();
		Group row = new Group();
		Actor field = new Actor();
		window.addActor(row);
		row.addActor(field);
		assertTrue(UiDialogs.isWithin(window, window), "the modal itself owns focus");
		assertTrue(UiDialogs.isWithin(field, window), "nested controls belong to their modal");
		assertTrue(!UiDialogs.isWithin(new Actor(), window), "unrelated controls do not belong to the modal");
		assertTrue(!UiDialogs.isWithin(null, window), "missing focus does not belong to the modal");
		System.out.println("ALL UI DIALOG TESTS PASSED");
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Float.compare(expected, actual) != 0)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private UiDialogsTest() {}
}
