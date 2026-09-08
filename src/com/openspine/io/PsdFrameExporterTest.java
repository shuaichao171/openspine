package com.openspine.io;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;

/** Headless coverage for flattened PSD layout and atomic output. */
public final class PsdFrameExporterTest {
	public static void main(String[] args) throws Exception {
		GdxNativesLoader.load();
		Path directory = Files.createTempDirectory("openspine-psd-export-").toRealPath();
		Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
		try {
			pixmap.drawPixel(0, 0, 0xff000040);
			pixmap.drawPixel(1, 0, 0x00ff0080);
			pixmap.drawPixel(0, 1, 0x0000ffff);
			pixmap.drawPixel(1, 1, 0xffffffff);
			Path target = directory.resolve("frame.psd");
			PsdFrameExporter.writeAtomic(target, pixmap);
			byte[] bytes = Files.readAllBytes(target);
			ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
			assertAscii(bytes, 0, "8BPS", "PSD signature is present");
			assertEquals(1, data.getShort(4) & 0xffff, "PSD version is one");
			assertEquals(4, data.getShort(12) & 0xffff, "PSD stores RGBA channels");
			assertEquals(2, data.getInt(14), "PSD height is preserved");
			assertEquals(2, data.getInt(18), "PSD width is preserved");
			assertEquals(8, data.getShort(22) & 0xffff, "PSD uses eight-bit channels");
			assertEquals(3, data.getShort(24) & 0xffff, "PSD uses RGB color mode");
			assertEquals(0, data.getShort(42) & 0xffff, "PSD channel data uses raw compression");
			int pixels = 44;
			assertChannel(bytes, pixels, new int[] {0, 255, 255, 0},
				"red plane is top-down and channel-separated");
			assertChannel(bytes, pixels + 4, new int[] {0, 255, 0, 255},
				"green plane is top-down and channel-separated");
			assertChannel(bytes, pixels + 8, new int[] {255, 255, 0, 0},
				"blue plane is top-down and channel-separated");
			assertChannel(bytes, pixels + 12, new int[] {255, 255, 64, 128},
				"alpha plane preserves transparency");
			long temporaryFiles;
			try (var files = Files.list(directory)) {
				temporaryFiles = files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count();
			}
			assertEquals(0, temporaryFiles, "successful PSD writes leave no temporary files");
			System.out.println("PSD FRAME EXPORTER TEST PASSED");
		} finally {
			pixmap.dispose();
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	private static void assertAscii(byte[] bytes, int offset, String expected, String message) {
		for (int index = 0; index < expected.length(); index++)
			if (bytes[offset + index] != (byte)expected.charAt(index)) throw new AssertionError(message);
	}

	private static void assertChannel(byte[] bytes, int offset, int[] expected, String message) {
		for (int index = 0; index < expected.length; index++)
			if ((bytes[offset + index] & 0xff) != expected[index])
				throw new AssertionError(message + ": index=" + index + ", actual=" + (bytes[offset + index] & 0xff));
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual)
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
	}

	private PsdFrameExporterTest() {}
}
