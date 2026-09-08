package com.openspine.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import javax.imageio.ImageIO;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;

/** Headless coverage for WebP pixel conversion, capability detection, and atomic cleanup. */
public final class WebpFrameExporterTest {
	public static void main(String[] args) throws Exception {
		GdxNativesLoader.load();
		testsPixmapConversion();
		testsWriterCapabilityBranch();
		System.out.println("WEBP FRAME EXPORTER TEST PASSED");
	}

	private static void testsPixmapConversion() {
		Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
		try {
			pixmap.drawPixel(0, 0, 0xff000040);
			pixmap.drawPixel(1, 0, 0x00ff0080);
			pixmap.drawPixel(0, 1, 0x0000ffff);
			pixmap.drawPixel(1, 1, 0xffffffff);
			BufferedImage image = WebpFrameExporter.toBufferedImage(pixmap);
			try {
				assertEquals(0xff0000ff, image.getRGB(0, 0), "top row is flipped from framebuffer order");
				assertEquals(0xffffffff, image.getRGB(1, 0), "opaque white is preserved");
				assertEquals(0x40ff0000, image.getRGB(0, 1), "straight alpha and red are preserved");
				assertEquals(0x8000ff00, image.getRGB(1, 1), "straight alpha and green are preserved");
			} finally { image.flush(); }
		} finally { pixmap.dispose(); }
	}

	private static void testsWriterCapabilityBranch() throws Exception {
		boolean writerPresent = ImageIO.getImageWritersByFormatName("webp").hasNext();
		assertTrue(WebpFrameExporter.isAvailable() == writerPresent,
			"WebP availability matches the ImageIO registry");
		Path directory = Files.createTempDirectory("openspine-webp-export-").toRealPath();
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_4BYTE_ABGR);
		try {
			Path target = directory.resolve("frame.webp");
			if (writerPresent) {
				WebpFrameExporter.writeAtomic(target, image);
				byte[] bytes = Files.readAllBytes(target);
				assertTrue(bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP"),
					"registered WebP writer produces a WebP container");
			} else {
				assertThrows(() -> WebpFrameExporter.writeAtomic(target, image),
					"missing WebP writer fails clearly");
				assertTrue(!Files.exists(target), "missing writer does not expose a partial target");
			}
			long temporaryFiles;
			try (var files = Files.list(directory)) {
				temporaryFiles = files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count();
			}
			assertEquals(0, temporaryFiles, "WebP writes leave no temporary files");
		} finally {
			image.flush();
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	private static boolean ascii(byte[] bytes, int offset, String expected) {
		for (int index = 0; index < expected.length(); index++)
			if (bytes[offset + index] != (byte)expected.charAt(index)) return false;
		return true;
	}

	private static void assertThrows(ThrowingRunnable action, String message) {
		try { action.run(); }
		catch (IOException expected) { return; }
		throw new AssertionError(message);
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual)
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingRunnable { void run() throws IOException; }

	private WebpFrameExporterTest() {}
}
