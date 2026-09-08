package com.openspine.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;

/** Headless regression tests for shared UI sizing rules. */
public final class UiKitTest {
	public static void main(String[] args) {
		BaseDrawable verticalKnob = new BaseDrawable();
		assertSame(verticalKnob, UiKit.withMinimumSize(verticalKnob,
			UiKit.SCROLLBAR_THICKNESS, UiKit.SCROLLBAR_KNOB_LENGTH),
			"sizing preserves the drawable instance");
		assertNear(10f, verticalKnob.getMinWidth(), "vertical scrollbar keeps a usable hit width");
		assertNear(28f, verticalKnob.getMinHeight(), "vertical scrollbar keeps a usable minimum length");
		assertNear(10f, UiKit.SPLIT_HANDLE_THICKNESS, "workspace dividers keep a usable drag target");

		BaseDrawable disclosure = new BaseDrawable();
		UiKit.withMinimumSize(disclosure, UiKit.TREE_DISCLOSURE_SIZE, UiKit.TREE_DISCLOSURE_SIZE);
		assertNear(12f, disclosure.getMinWidth(), "tree disclosure icon width is usable");
		assertNear(12f, disclosure.getMinHeight(), "tree disclosure icon height is usable");
		BaseDrawable visibilityIcon = new BaseDrawable();
		UiKit.withMinimumSize(visibilityIcon, UiKit.TREE_VISIBILITY_ICON_SIZE, UiKit.TREE_VISIBILITY_ICON_SIZE);
		assertNear(16f, visibilityIcon.getMinWidth(), "tree visibility icon width is usable");
		assertNear(16f, visibilityIcon.getMinHeight(), "tree visibility icon height is usable");
		assertTrue(UiKit.WINDOW_CONTROL_ICON_SIZE >= 18f,
			"window control icons remain clearly visible in the custom title bar");
		BaseDrawable normalWindowIcon = new BaseDrawable();
		BaseDrawable activeWindowIcon = new BaseDrawable();
		var windowControlStyle = UiKit.windowControlImageStyle(new ButtonStyle(),
			normalWindowIcon, activeWindowIcon);
		assertSame(normalWindowIcon, windowControlStyle.imageUp,
			"window controls use the themed icon at rest");
		assertSame(activeWindowIcon, windowControlStyle.imageOver,
			"window controls can use a contrasting hover icon");
		assertSame(activeWindowIcon, windowControlStyle.imageDown,
			"window controls keep the contrasting icon while pressed");

		assertThrows(() -> UiKit.withMinimumSize(new BaseDrawable(), Float.NaN, 1f),
			"invalid drawable dimensions are rejected");

		BaseDrawable controlBackground = new BaseDrawable();
		controlBackground.setLeftWidth(6f);
		controlBackground.setRightWidth(6f);
		controlBackground.setTopHeight(3f);
		controlBackground.setBottomHeight(3f);
		controlBackground.setMinWidth(16f);
		controlBackground.setMinHeight(16f);
		var offsetBackground = UiKit.withVerticalContentOffset(controlBackground, UiKit.BUTTON_TEXT_Y_OFFSET);
		assertNear(0f, offsetBackground.getTopHeight(), "input text gains room above its visible glyphs");
		assertNear(6f, offsetBackground.getBottomHeight(), "input text baseline moves upward");
		assertNear(6f, offsetBackground.getLeftWidth(), "input text keeps its horizontal padding");
		assertNear(16f, offsetBackground.getMinHeight(), "input controls keep their minimum height");
		assertNear(3f, controlBackground.getTopHeight(), "shared control backgrounds remain unchanged");
		assertNear(3f, controlBackground.getBottomHeight(), "shared background padding remains unchanged");
		assertThrows(() -> UiKit.withVerticalContentOffset(controlBackground, 4f),
			"input text offset cannot consume more than the available top padding");

		TextButtonStyle buttonStyle = new TextButtonStyle();
		BaseDrawable disabledBackground = new BaseDrawable();
		Color disabledText = new Color(0.42f, 0.44f, 0.48f, 1f);
		assertSame(buttonStyle, UiKit.withDisabledState(buttonStyle, disabledBackground, disabledText),
			"disabled-state styling preserves the button style instance");
		assertSame(disabledBackground, buttonStyle.disabled,
			"disabled buttons use the supplied muted background");
		assertNear(disabledText.r, buttonStyle.disabledFontColor.r, "disabled button text red");
		assertNear(disabledText.g, buttonStyle.disabledFontColor.g, "disabled button text green");
		disabledText.set(Color.WHITE);
		assertNear(0.42f, buttonStyle.disabledFontColor.r,
			"button style keeps an independent disabled text color");
		assertThrows(() -> UiKit.withDisabledState(null, disabledBackground, disabledText),
			"missing button style is rejected");

		BaseDrawable listSelection = new BaseDrawable();
		BaseDrawable listHover = new BaseDrawable();
		ListStyle listStyle = new ListStyle(null, Color.WHITE, Color.WHITE, listSelection);
		listStyle.over = listHover;
		assertSame(listSelection, listStyle.selection,
			"list styles retain a drawable for the selected item");
		assertSame(listHover, listStyle.over,
			"list styles retain a drawable for the hovered item");

		TextFieldStyle fieldStyle = new TextFieldStyle();
		BaseDrawable normalFieldBackground = new BaseDrawable();
		BaseDrawable focusedFieldBackground = new BaseDrawable();
		BaseDrawable disabledFieldBackground = new BaseDrawable();
		BaseDrawable hoverFieldBackground = new BaseDrawable();
		fieldStyle.background = normalFieldBackground;
		fieldStyle.focusedBackground = focusedFieldBackground;
		fieldStyle.disabledBackground = disabledFieldBackground;
		TextFieldStyle hoveredFieldStyle = UiKit.hoveredTextFieldStyle(fieldStyle, hoverFieldBackground);
		assertTrue(hoveredFieldStyle != fieldStyle, "text field hover uses an isolated style copy");
		assertSame(hoverFieldBackground, hoveredFieldStyle.background,
			"hovered text fields use the supplied control hover background");
		assertSame(focusedFieldBackground, hoveredFieldStyle.focusedBackground,
			"text field hover preserves the focused background");
		assertSame(disabledFieldBackground, hoveredFieldStyle.disabledBackground,
			"text field hover preserves the disabled background");
		assertSame(normalFieldBackground, fieldStyle.background,
			"text field hover does not mutate the shared normal style");
		assertThrows(() -> UiKit.hoveredTextFieldStyle(null, hoverFieldBackground),
			"missing text field style is rejected");
		assertThrows(() -> UiKit.hoveredTextFieldStyle(fieldStyle, null),
			"missing text field hover background is rejected");

		TextButtonStyle centeredStyle = new TextButtonStyle();
		assertSame(centeredStyle, UiKit.withCenteredText(centeredStyle),
			"text alignment preserves the button style instance");
		assertNear(0f, centeredStyle.unpressedOffsetX, "normal button text is centered horizontally");
		assertNear(UiKit.BUTTON_TEXT_Y_OFFSET, centeredStyle.unpressedOffsetY,
			"normal button text uses the shared visual baseline correction");
		assertNear(0f, centeredStyle.pressedOffsetX, "pressed button text remains horizontally centered");
		assertNear(UiKit.BUTTON_TEXT_Y_OFFSET, centeredStyle.pressedOffsetY,
			"pressed button text keeps the shared visual baseline correction");
		assertNear(0f, centeredStyle.checkedOffsetX, "checked button text remains horizontally centered");
		assertNear(UiKit.BUTTON_TEXT_Y_OFFSET, centeredStyle.checkedOffsetY,
			"checked button text keeps the shared visual baseline correction");
		assertNear(13f, UiKit.controlLabelDrawY(10f),
			"control labels use the same visual baseline correction as buttons");
		assertTrue(UiKit.DIALOG_TITLE_SCALE > 1f, "dialog titles are visually distinct from body text");
		assertTrue(UiKit.DIALOG_TITLE_BAR_HEIGHT >= 30f, "dialog title bar fits the desktop font");
		assertTrue(UiKit.DIALOG_TITLE_TOP_PADDING > 0f && UiKit.DIALOG_TITLE_BOTTOM_PADDING > 0f,
			"dialog titles keep vertical breathing room");
		assertThrows(() -> UiKit.withCenteredText(null), "missing centered button style is rejected");

		assertThrows(() -> UiKit.asActionButton(null), "missing action button is rejected");
		System.out.println("ALL UI KIT TESTS PASSED");
	}

	private static void assertSame(Object expected, Object actual, String message) {
		if (expected != actual) throw new AssertionError(message);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Float.compare(expected, actual) != 0)
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

	private UiKitTest() {}
}
