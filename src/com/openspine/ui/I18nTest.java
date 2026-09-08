package com.openspine.ui;

import java.util.ArrayList;
import java.util.List;

import com.openspine.ui.I18n.Language;

/** Headless regression tests for the interface language runtime. */
public final class I18nTest {
	public static void main(String[] args) {
		try {
			assertEquals(Language.CHINESE, I18n.language(), "the editor starts in Chinese");
			assertEquals("撤销", I18n.t("撤销", "Undo"), "Chinese text is returned by default");

			assertEquals(Language.ENGLISH, Language.fromCode("en", Language.CHINESE), "en code parses");
			assertEquals(Language.ENGLISH, Language.fromCode(" EN ", Language.CHINESE),
				"codes are trimmed and case-insensitive");
			assertEquals(Language.CHINESE, Language.fromCode("zh", Language.ENGLISH), "zh code parses");
			assertEquals(Language.CHINESE, Language.fromCode("fr", Language.CHINESE),
				"unknown codes fall back to the supplied default");
			assertEquals(Language.ENGLISH, Language.fromCode(null, Language.ENGLISH),
				"missing codes fall back to the supplied default");
			assertEquals(Language.ENGLISH, Language.fromCode("  ", Language.ENGLISH),
				"blank codes fall back to the supplied default");

			List<String> notified = new ArrayList<>();
			Runnable listener = () -> notified.add(I18n.language().code);
			I18n.addListener(listener);
			try {
				I18n.setLanguage(Language.CHINESE);
				assertEquals(0, notified.size(), "setting the current language again does not notify");
				I18n.setLanguage(Language.ENGLISH);
				assertEquals(1, notified.size(), "language changes notify listeners once");
				assertEquals("en", notified.get(0), "listeners observe the new language");

				assertEquals("Undo", I18n.t("撤销", "Undo"), "English text is returned in English");
				assertEquals("Save", I18n.t("保存"), "shared interface labels translate to English");
				assertEquals("Interface", SettingsDialog.Page.INTERFACE.label(),
					"settings navigation labels translate");
				assertEquals("Appearance", SettingsDialog.Page.APPEARANCE.label(),
					"appearance settings navigation translates");
				assertEquals("Shortcuts", SettingsDialog.Page.SHORTCUTS.label(),
					"the shortcuts settings category translates");
				assertEquals("Dopesheet", I18n.t("摄影表"),
					"the timeline view uses Spine's dopesheet terminology");
				assertEquals("Auto Key", I18n.t("自动关键帧"),
					"the Auto Key timeline control translates to English");
				assertEquals("Search skeleton", I18n.t("搜索骨架"),
					"the hierarchy search placeholder translates to English");
				assertEquals("Bezier Curve", I18n.t("贝塞尔曲线"),
					"the complete Bezier editor label translates without an ellipsis");
				assertEquals("Graph Editor", I18n.t("Graph 编辑器"),
					"the complete Graph editor label translates without an ellipsis");
				assertEquals("On", SettingsDialog.toggleText(true), "settings state labels translate");
				assertTrue(I18n.matches("Translation Only", "仅平移", "Translation Only"),
					"either language is recognized while controls are relabeled");
				assertEquals("撤销", I18n.t("撤销", null), "untranslated text falls back to Chinese");
				assertEquals("撤销", I18n.t("撤销", " "), "blank translations fall back to Chinese");
			} finally {
				I18n.removeListener(listener);
			}
			I18n.setLanguage(Language.CHINESE);
			assertEquals(1, notified.size(), "removed listeners are no longer notified");
			assertEquals("保存", I18n.t("Save"), "shared labels translate back to Chinese");

			assertThrows(() -> I18n.setLanguage(null), "null language is rejected");
			assertThrows(() -> I18n.addListener(null), "null listener is rejected");
		} finally {
			I18n.setLanguage(Language.CHINESE);
		}
		System.out.println("ALL I18N TESTS PASSED");
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual == null : expected.equals(actual)) return;
		throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (RuntimeException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private I18nTest() {}
}
