package com.openspine.ui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

/** Headless regression tests for desktop font coverage and atlas conversion. */
public final class DesktopBitmapFontTest {
	private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

	public static void main(String[] args) throws IOException {
		Set<Character> characters = DesktopBitmapFont.uiCharacters();
		assertTrue(characters.size() > 7000, "common Simplified Chinese characters are included");
		for (char c : "龙骨动画音频混合搜索覆盖".toCharArray())
			assertTrue(characters.contains(c), "font contains common UI or user-name character: " + c);
		assertSourceStringsCovered(characters, Path.of("src", "com", "openspine"));

		ByteBuffer rgba = ByteBuffer.allocate(8);
		DesktopBitmapFont.copyArgbToRgba(new int[] {0x11223344, 0xff80c0e0}, rgba);
		assertByte(0x22, rgba.get(0), "first red channel");
		assertByte(0x33, rgba.get(1), "first green channel");
		assertByte(0x44, rgba.get(2), "first blue channel");
		assertByte(0x11, rgba.get(3), "first alpha channel");
		assertByte(0xff, rgba.get(7), "second alpha channel");
		assertEquals(1, DesktopBitmapFont.nextPowerOfTwo(1), "unit texture size");
		assertEquals(2048, DesktopBitmapFont.nextPowerOfTwo(1537), "atlas texture size");
		assertThrows(() -> DesktopBitmapFont.nextPowerOfTwo(0), "invalid texture size is rejected");
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = probe.createGraphics();
		try {
			graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
			int measured = graphics.getFontMetrics().charWidth('S');
			assertEquals(measured, DesktopBitmapFont.glyphAdvance(graphics.getFontMetrics(), 'S'),
				"Latin glyphs use their measured advance without artificial letter spacing");
			BitmapFont.Glyph glyph = new BitmapFont.Glyph();
			glyph.id = 'S';
			glyph.width = measured + 12;
			DesktopBitmapFont.configureGlyphAdvance(glyph, measured);
			BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
			data.setGlyph('S', glyph);
			GlyphLayout.GlyphRun run = new GlyphLayout.GlyphRun();
			data.getGlyphs(run, "S", 0, 1, null);
			assertNear(measured, run.xAdvances.get(1),
				"text input cursor uses the glyph advance instead of transparent atlas padding");
		} finally {
			graphics.dispose();
		}
		System.out.println("ALL DESKTOP BITMAP FONT TESTS PASSED");
	}

	private static void assertSourceStringsCovered(Set<Character> characters, Path sourceRoot) throws IOException {
		if (!Files.isDirectory(sourceRoot)) throw new AssertionError("source root is missing: " + sourceRoot);
		try (Stream<Path> files = Files.walk(sourceRoot)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file, StandardCharsets.UTF_8);
				Matcher matcher = STRING_LITERAL.matcher(source);
				while (matcher.find()) {
					String literal = matcher.group();
					for (int i = 1; i < literal.length() - 1; i++) {
						char c = literal.charAt(i);
						if (c > 126 && !characters.contains(c))
							throw new AssertionError(file + " contains a string character missing from the UI font: " + c);
					}
				}
			}
		}
	}

	private static void assertByte(int expected, byte actual, String message) {
		int unsigned = actual & 0xff;
		if (expected != unsigned) throw new AssertionError(message + ": expected " + expected + ", got " + unsigned);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
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

	private DesktopBitmapFontTest() {}
}
