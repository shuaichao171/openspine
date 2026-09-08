package com.openspine.io;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import javax.imageio.ImageIO;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;

/** Headless regression tests for JPEG settings, row orientation, and atomic encoding. */
public final class JpegFrameExporterTest {
	public static void main(String[] args) throws Exception {
		GdxNativesLoader.load();
		testsSettingsValidation();
		testsPixmapConversion();
		testsAtomicJpegEncoding();
		System.out.println("ALL JPEG FRAME EXPORTER TESTS PASSED");
	}

	private static void testsSettingsValidation() {
		JpegFrameExporter.Settings settings = new JpegFrameExporter.Settings(1.5f, 8, 92);
		assertNear(1.5f, settings.scale(), "JPEG scale is preserved");
		assertEquals(8, settings.padding(), "JPEG padding is preserved");
		assertEquals(92, settings.quality(), "JPEG quality is preserved");
		assertThrows(() -> new JpegFrameExporter.Settings(Float.NaN, 0, 90),
			"non-finite JPEG scale is rejected");
		assertThrows(() -> new JpegFrameExporter.Settings(1f, -1, 90),
			"negative JPEG padding is rejected");
		assertThrows(() -> new JpegFrameExporter.Settings(1f, 0, 101),
			"JPEG quality above 100 is rejected");
	}

	private static void testsPixmapConversion() {
		Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
		try {
			pixmap.drawPixel(0, 0, 0xff000040);
			pixmap.drawPixel(1, 0, 0x00ff0080);
			pixmap.drawPixel(0, 1, 0x0000ffff);
			pixmap.drawPixel(1, 1, 0xffffffff);
			BufferedImage image = JpegFrameExporter.toBufferedImage(pixmap, true);
			try {
				assertEquals(0xff0000ff, image.getRGB(0, 0),
					"framebuffer rows are flipped into top-down JPEG order");
				assertEquals(0xffffffff, image.getRGB(1, 0),
					"JPEG conversion preserves opaque RGB values");
				assertEquals(0xffff0000, image.getRGB(0, 1),
					"JPEG conversion discards alpha without darkening RGB");
				assertEquals(0xff00ff00, image.getRGB(1, 1),
					"the opposite framebuffer row remains intact");
			} finally {
				image.flush();
			}
		} finally {
			pixmap.dispose();
		}
	}

	private static void testsAtomicJpegEncoding() throws Exception {
		Path directory = Files.createTempDirectory("openspine-jpeg-export-").toRealPath();
		Pixmap pixmap = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
		try {
			pixmap.setColor(1f, 0f, 0f, 1f);
			pixmap.fillRectangle(0, 0, 32, 16);
			pixmap.setColor(0f, 0f, 1f, 1f);
			pixmap.fillRectangle(0, 16, 32, 16);
			Path target = directory.resolve("frame.jpg");
			JpegFrameExporter.writePixmapAtomic(target, pixmap, 100, true);
			byte[] bytes = Files.readAllBytes(target);
			assertTrue(bytes.length > 4 && bytes[0] == (byte)0xff && bytes[1] == (byte)0xd8,
				"atomic writer produces a JPEG signature");
			BufferedImage image = ImageIO.read(target.toFile());
			assertTrue(image != null, "encoded JPEG can be decoded by ImageIO");
			assertEquals(32, image.getWidth(), "encoded JPEG preserves width");
			assertEquals(32, image.getHeight(), "encoded JPEG preserves height");
			assertDominantBlue(image.getRGB(16, 4), "top JPEG rows come from the upper framebuffer rows");
			assertDominantRed(image.getRGB(16, 27), "bottom JPEG rows come from the lower framebuffer rows");
			Path alternate = directory.resolve("frame.jpeg");
			JpegFrameExporter.writePixmapAtomic(alternate, pixmap, 75, false);
			assertTrue(Files.isRegularFile(alternate), ".jpeg targets are accepted");
			long temporaryFiles;
			try (var files = Files.list(directory)) {
				temporaryFiles = files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count();
			}
			assertEquals(0L, temporaryFiles, "successful atomic writes leave no temporary files");
			assertThrows(() -> JpegFrameExporter.writePixmapAtomic(directory.resolve("frame.png"), pixmap, 90, true),
				"non-JPEG targets are rejected");
		} finally {
			pixmap.dispose();
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	private static void assertDominantBlue(int argb, String message) {
		int red = argb >>> 16 & 0xff, green = argb >>> 8 & 0xff, blue = argb & 0xff;
		assertTrue(blue > 200 && blue > red * 2 && blue > green * 2, message);
	}

	private static void assertDominantRed(int argb, String message) {
		int red = argb >>> 16 & 0xff, green = argb >>> 8 & 0xff, blue = argb & 0xff;
		assertTrue(red > 200 && red > green * 2 && red > blue * 2, message);
	}

	private static void assertThrows(ThrowingRunnable action, String message) {
		try {
			action.run();
		} catch (Exception expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.00001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private JpegFrameExporterTest() {}
}
