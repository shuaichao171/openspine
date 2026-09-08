package com.openspine.ui;

import com.badlogic.gdx.graphics.Color;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Appearance mode, accent themes, and the resolved editor palette. */
public final class UiAppearance {
	public enum Mode {
		SYSTEM("system", "跟随系统", "System"),
		LIGHT("light", "浅色", "Light"),
		DARK("dark", "深色", "Dark");

		public final String key;
		private final String chinese;
		private final String english;

		Mode(String key, String chinese, String english) {
			this.key = key;
			this.chinese = chinese;
			this.english = english;
		}

		public String label() { return I18n.t(chinese, english); }

		public static Mode fromKey(String key) {
			for (Mode mode : values()) if (mode.key.equalsIgnoreCase(key == null ? "" : key.trim())) return mode;
			return SYSTEM;
		}
	}

	public enum Theme {
		JADE("jade", "翡翠", "Jade"),
		AZURE("azure", "晴蓝", "Azure"),
		CORAL("coral", "珊瑚", "Coral");

		public final String key;
		private final String chinese;
		private final String english;

		Theme(String key, String chinese, String english) {
			this.key = key;
			this.chinese = chinese;
			this.english = english;
		}

		public String label() { return I18n.t(chinese, english); }

		public static Theme fromKey(String key) {
			for (Theme theme : values())
				if (theme.key.equalsIgnoreCase(key == null ? "" : key.trim())) return theme;
			return JADE;
		}
	}

	enum Role {
		APP_BACKGROUND, PANEL, SURFACE, CONTROL, CONTROL_HOVER, CONTROL_DISABLED,
		ACCENT, ACCENT_PRESSED, ACCENT_HOVER, DANGER, DANGER_PRESSED, DANGER_HOVER,
		TEXT, TEXT_MUTED, TEXT_DISABLED, INPUT_TEXT, BORDER, SCROLL_TRACK, SCROLL_KNOB,
		SLIDER_TRACK, ICON, FIELD_CURSOR, WINDOW, OVERLAY, TREE_SELECTION,
		VIEWPORT_BACKGROUND, VIEWPORT_GRID, VIEWPORT_GRID_MAJOR, VIEWPORT_AXIS,
		TIMELINE_BACKGROUND, TIMELINE_ACTIVE, TIMELINE_ACTIVE_ROW_ALT,
		TIMELINE_ROW_ALT, TIMELINE_GRID, TIMELINE_DIVIDER,
		TIMELINE_RULER, TIMELINE_SIDEBAR, TIMELINE_GUTTER, TIMELINE_PLOT, TIMELINE_VALUE_PANEL
	}

	public static final class Palette {
		private final boolean dark;
		private final Theme theme;
		private final EnumMap<Role, Color> colors;

		private Palette(boolean dark, Theme theme, EnumMap<Role, Color> colors) {
			this.dark = dark;
			this.theme = theme;
			this.colors = colors;
		}

		public boolean dark() { return dark; }
		public Theme theme() { return theme; }
		Color color(Role role) { return colors.get(role); }
		public Color appBackground() { return color(Role.APP_BACKGROUND); }
		public Color panel() { return color(Role.PANEL); }
		public Color surface() { return color(Role.SURFACE); }
		public Color text() { return color(Role.TEXT); }
		public Color mutedText() { return color(Role.TEXT_MUTED); }
		public Color disabledText() { return color(Role.TEXT_DISABLED); }
		public Color accent() { return color(Role.ACCENT); }
		public Color errorText() { return color(Role.DANGER_HOVER); }
		public Color border() { return color(Role.BORDER); }
		public Color viewportBackground() { return color(Role.VIEWPORT_BACKGROUND); }
		public Color viewportGrid() { return color(Role.VIEWPORT_GRID); }
		public Color viewportGridMajor() { return color(Role.VIEWPORT_GRID_MAJOR); }
		public Color viewportAxis() { return color(Role.VIEWPORT_AXIS); }
	}

	private static final Pattern WINDOWS_LIGHT_THEME = Pattern.compile(
		"(?i)AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-f]+)");

	public static boolean resolveDark(Mode mode, boolean systemDark) {
		if (mode == null) mode = Mode.SYSTEM;
		return switch (mode) {
		case SYSTEM -> systemDark;
		case LIGHT -> false;
		case DARK -> true;
		};
	}

	public static Palette resolve(Mode mode, boolean systemDark, Theme lightTheme, Theme darkTheme) {
		boolean dark = resolveDark(mode, systemDark);
		return palette(dark, dark ? darkTheme : lightTheme);
	}

	public static Palette palette(boolean dark, Theme theme) {
		Theme resolvedTheme = theme == null ? Theme.JADE : theme;
		EnumMap<Role, Color> colors = new EnumMap<>(Role.class);
		if (dark) fillDark(colors, resolvedTheme); else fillLight(colors, resolvedTheme);
		return new Palette(dark, resolvedTheme, colors);
	}

	private static void fillDark(EnumMap<Role, Color> colors, Theme theme) {
		colors.put(Role.APP_BACKGROUND, hex("13151A"));
		colors.put(Role.PANEL, hex("1A1D23"));
		colors.put(Role.SURFACE, hex("0D0F13"));
		colors.put(Role.CONTROL, hex("262930"));
		colors.put(Role.CONTROL_HOVER, hex("393E47"));
		colors.put(Role.CONTROL_DISABLED, hex("1B1E24"));
		putAccent(colors, theme, true);
		colors.put(Role.DANGER, hex("B9383D"));
		colors.put(Role.DANGER_PRESSED, hex("8F262B"));
		colors.put(Role.DANGER_HOVER, hex("E04A50"));
		colors.put(Role.TEXT, hex("D9DEE8"));
		colors.put(Role.TEXT_MUTED, hex("929CAA"));
		colors.put(Role.TEXT_DISABLED, hex("6B717B"));
		colors.put(Role.INPUT_TEXT, hex("E8ECF3"));
		colors.put(Role.BORDER, hex("3F4652"));
		colors.put(Role.SCROLL_TRACK, hex("2D323A"));
		colors.put(Role.SCROLL_KNOB, hex("737F8C"));
		colors.put(Role.SLIDER_TRACK, hex("40454D"));
		colors.put(Role.ICON, hex("C0CAD8"));
		colors.put(Role.FIELD_CURSOR, hex("38C3B6"));
		colors.put(Role.WINDOW, hex("21262E"));
		colors.put(Role.OVERLAY, rgba("040507A0"));
		colors.put(Role.TREE_SELECTION, hex("24565B"));
		colors.put(Role.VIEWPORT_BACKGROUND, hex("131418"));
		colors.put(Role.VIEWPORT_GRID, hex("292B33"));
		colors.put(Role.VIEWPORT_GRID_MAJOR, hex("3D4252"));
		colors.put(Role.VIEWPORT_AXIS, hex("CCCCD9"));
		colors.put(Role.TIMELINE_BACKGROUND, hex("16181D"));
		colors.put(Role.TIMELINE_ACTIVE, hex("303640"));
		colors.put(Role.TIMELINE_ACTIVE_ROW_ALT, hex("292F38"));
		colors.put(Role.TIMELINE_ROW_ALT, hex("181B20"));
		colors.put(Role.TIMELINE_GRID, hex("292B33"));
		colors.put(Role.TIMELINE_DIVIDER, hex("1F2129"));
		colors.put(Role.TIMELINE_RULER, hex("13151A"));
		colors.put(Role.TIMELINE_SIDEBAR, hex("121418"));
		colors.put(Role.TIMELINE_GUTTER, hex("0B0D0F"));
		colors.put(Role.TIMELINE_PLOT, hex("17191D"));
		colors.put(Role.TIMELINE_VALUE_PANEL, hex("0E0F11"));
	}

	private static void fillLight(EnumMap<Role, Color> colors, Theme theme) {
		colors.put(Role.APP_BACKGROUND, hex("E8ECF1"));
		colors.put(Role.PANEL, hex("F5F7F9"));
		colors.put(Role.SURFACE, hex("FFFFFF"));
		colors.put(Role.CONTROL, hex("E9EDF1"));
		colors.put(Role.CONTROL_HOVER, hex("DDE3E9"));
		colors.put(Role.CONTROL_DISABLED, hex("F0F2F4"));
		putAccent(colors, theme, false);
		colors.put(Role.DANGER, hex("B93835"));
		colors.put(Role.DANGER_PRESSED, hex("982C2A"));
		colors.put(Role.DANGER_HOVER, hex("CF4B46"));
		colors.put(Role.TEXT, hex("252A31"));
		colors.put(Role.TEXT_MUTED, hex("657180"));
		colors.put(Role.TEXT_DISABLED, hex("9AA3AD"));
		colors.put(Role.INPUT_TEXT, hex("1E242B"));
		colors.put(Role.BORDER, hex("C4CBD4"));
		colors.put(Role.SCROLL_TRACK, hex("DDE2E7"));
		colors.put(Role.SCROLL_KNOB, hex("8995A2"));
		colors.put(Role.SLIDER_TRACK, hex("CDD4DC"));
		colors.put(Role.ICON, hex("596574"));
		colors.put(Role.FIELD_CURSOR, colors.get(Role.ACCENT));
		colors.put(Role.WINDOW, hex("FAFBFC"));
		colors.put(Role.OVERLAY, rgba("20252D4A"));
		colors.put(Role.TREE_SELECTION, hex("D5E6E5"));
		colors.put(Role.VIEWPORT_BACKGROUND, hex("E8ECF1"));
		colors.put(Role.VIEWPORT_GRID, hex("D6DCE3"));
		colors.put(Role.VIEWPORT_GRID_MAJOR, hex("AEB8C4"));
		colors.put(Role.VIEWPORT_AXIS, hex("7D8998"));
		colors.put(Role.TIMELINE_BACKGROUND, hex("DFE5EB"));
		colors.put(Role.TIMELINE_ACTIVE, hex("FAFBFC"));
		colors.put(Role.TIMELINE_ACTIVE_ROW_ALT, hex("F1F4F7"));
		colors.put(Role.TIMELINE_ROW_ALT, hex("F0F3F6"));
		colors.put(Role.TIMELINE_GRID, hex("D8DFE7"));
		colors.put(Role.TIMELINE_DIVIDER, hex("C9D1DA"));
		colors.put(Role.TIMELINE_RULER, hex("E8EDF2"));
		colors.put(Role.TIMELINE_SIDEBAR, hex("F1F4F7"));
		colors.put(Role.TIMELINE_GUTTER, hex("DCE3EA"));
		colors.put(Role.TIMELINE_PLOT, hex("F6F8FA"));
		colors.put(Role.TIMELINE_VALUE_PANEL, hex("ECF1F5"));
	}

	private static void putAccent(EnumMap<Role, Color> colors, Theme theme, boolean dark) {
		switch (theme) {
		case JADE -> {
			colors.put(Role.ACCENT, hex(dark ? "168F88" : "0F766E"));
			colors.put(Role.ACCENT_PRESSED, hex(dark ? "0E6E69" : "0B5F59"));
			colors.put(Role.ACCENT_HOVER, hex(dark ? "1AA39B" : "15847C"));
		}
		case AZURE -> {
			colors.put(Role.ACCENT, hex(dark ? "3B82F6" : "2563EB"));
			colors.put(Role.ACCENT_PRESSED, hex(dark ? "2867CB" : "1D4ED8"));
			colors.put(Role.ACCENT_HOVER, hex(dark ? "5795F7" : "3B73ED"));
		}
		case CORAL -> {
			colors.put(Role.ACCENT, hex(dark ? "D45E55" : "C2413A"));
			colors.put(Role.ACCENT_PRESSED, hex(dark ? "B94C45" : "A93630"));
			colors.put(Role.ACCENT_HOVER, hex(dark ? "E07168" : "D4554D"));
		}
		}
	}

	/** Reads the current Windows app theme. Dark is the conservative fallback. */
	public static boolean detectSystemDark() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return true;
		try {
			if (!GraphicsEnvironment.isHeadless()) {
				Object property = Toolkit.getDefaultToolkit().getDesktopProperty("win.darkMode");
				if (property instanceof Boolean dark) return dark;
			}
		} catch (RuntimeException ignored) {
		}
		Process process = null;
		try {
			process = new ProcessBuilder("reg.exe", "query",
				"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
				"/v", "AppsUseLightTheme").redirectErrorStream(true).start();
			if (!process.waitFor(750, TimeUnit.MILLISECONDS)) return true;
			String output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
			Boolean dark = windowsDarkModeFromRegistry(output);
			return dark == null || dark;
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
			return true;
		} catch (IOException ignored) {
			return true;
		} finally {
			if (process != null && process.isAlive()) process.destroyForcibly();
		}
	}

	static Boolean windowsDarkModeFromRegistry(String output) {
		if (output == null) return null;
		Matcher matcher = WINDOWS_LIGHT_THEME.matcher(output);
		if (!matcher.find()) return null;
		try {
			return Long.parseLong(matcher.group(1), 16) == 0L;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static Color hex(String rgb) { return Color.valueOf(rgb + "FF"); }
	private static Color rgba(String rgba) { return Color.valueOf(rgba); }

	private UiAppearance() {}
}
