package com.openspine.ui;

import com.badlogic.gdx.graphics.Color;

/** Semantic normalization for editable UI drafts. Invalid text remains comparable as typed. */
final class UiDrafts {
	static String color(String text, boolean rgbOnly) {
		try {
			Color color = rgbOnly ? UiColors.parseRgb(text) : UiColors.parse(text);
			return rgbOnly ? UiColors.formatRgb(color) : UiColors.format(color);
		} catch (RuntimeException ignored) {
			return text(text);
		}
	}

	static String number(String text) {
		try {
			return UiNumbers.format(UiNumbers.parseFinite(text));
		} catch (RuntimeException ignored) {
			return text(text);
		}
	}

	static String text(String value) {
		return value == null ? "" : value.trim();
	}

	private UiDrafts() {}
}
