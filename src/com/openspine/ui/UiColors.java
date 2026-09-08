package com.openspine.ui;

import com.badlogic.gdx.graphics.Color;

/** Hex color formatting and validation for editable UI fields. */
public final class UiColors {
	public static String format(Color color) {
		if (color == null) throw new IllegalArgumentException("颜色不能为空");
		return "#" + color.toString().toUpperCase(java.util.Locale.ROOT);
	}

	public static String formatRgb(Color color) {
		if (color == null) throw new IllegalArgumentException("颜色不能为空");
		return "#" + color.toString().substring(0, 6).toUpperCase(java.util.Locale.ROOT);
	}

	public static Color parse(String text) {
		if (text == null) throw new IllegalArgumentException("颜色不能为空");
		String hex = text.trim();
		if (hex.startsWith("#")) hex = hex.substring(1);
		if (hex.length() == 6) hex += "FF";
		if (hex.length() != 8) throw new IllegalArgumentException("颜色必须是 #RRGGBB 或 #RRGGBBAA");
		for (int index = 0; index < hex.length(); index++) {
			if (Character.digit(hex.charAt(index), 16) < 0)
				throw new IllegalArgumentException("颜色包含非十六进制字符");
		}
		return Color.valueOf(hex);
	}

	public static Color parseRgb(String text) {
		if (text == null) throw new IllegalArgumentException("暗色不能为空");
		String hex = text.trim();
		if (hex.startsWith("#")) hex = hex.substring(1);
		if (hex.length() != 6) throw new IllegalArgumentException("暗色必须是 #RRGGBB");
		for (int index = 0; index < hex.length(); index++) {
			if (Character.digit(hex.charAt(index), 16) < 0)
				throw new IllegalArgumentException("暗色包含非十六进制字符");
		}
		return Color.valueOf(hex + "FF");
	}

	private UiColors() {}
}
