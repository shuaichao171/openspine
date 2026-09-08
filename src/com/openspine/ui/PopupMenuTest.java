package com.openspine.ui;

import com.badlogic.gdx.Input;

/** Headless regression tests for context-menu keyboard navigation. */
public final class PopupMenuTest {
	public static void main(String[] args) {
		assertEquals(PopupMenu.KeyAction.PREVIOUS, PopupMenu.keyAction(Input.Keys.UP), "Up selects previous");
		assertEquals(PopupMenu.KeyAction.NEXT, PopupMenu.keyAction(Input.Keys.DOWN), "Down selects next");
		assertEquals(PopupMenu.KeyAction.ACTIVATE, PopupMenu.keyAction(Input.Keys.ENTER), "Enter activates");
		assertEquals(PopupMenu.KeyAction.ACTIVATE, PopupMenu.keyAction(Input.Keys.NUMPAD_ENTER),
			"numpad Enter activates");
		assertEquals(PopupMenu.KeyAction.CANCEL, PopupMenu.keyAction(Input.Keys.ESCAPE), "Escape closes");
		assertEquals(PopupMenu.KeyAction.NONE, PopupMenu.keyAction(Input.Keys.SPACE), "unrelated key is ignored");
		assertEquals(-1, PopupMenu.moveSelection(0, 1, 0), "empty menu has no selection");
		assertEquals(0, PopupMenu.moveSelection(-1, 1, 3), "Down starts at first item");
		assertEquals(2, PopupMenu.moveSelection(-1, -1, 3), "Up starts at last item");
		assertEquals(0, PopupMenu.moveSelection(2, 1, 3), "Down wraps to first item");
		assertEquals(2, PopupMenu.moveSelection(0, -1, 3), "Up wraps to last item");
		assertEquals(PopupMenu.MenuItem.Tone.NORMAL, PopupMenu.MenuItem.tone(false, false),
			"inactive menu toggles use the normal background");
		assertEquals(PopupMenu.MenuItem.Tone.ACTIVE, PopupMenu.MenuItem.tone(true, false),
			"enabled menu toggles remain highlighted without check-mark text");
		assertEquals(PopupMenu.MenuItem.Tone.SELECTED, PopupMenu.MenuItem.tone(true, true),
			"keyboard or pointer selection remains visible on active toggles");
		System.out.println("ALL POPUP MENU TESTS PASSED");
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private PopupMenuTest() {}
}
