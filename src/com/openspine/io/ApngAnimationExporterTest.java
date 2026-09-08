package com.openspine.io;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;

import javax.imageio.ImageIO;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;

/** Headless regression tests for APNG structure, pixels, timing, and atomic output. */
public final class ApngAnimationExporterTest {
	private static final byte[] PNG_SIGNATURE = {
		(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10};

	public static void main(String[] args) throws Exception {
		GdxNativesLoader.load();
		validatesSettingsAndFrameTiming();
		writesValidAtomicApng();
		System.out.println("ALL APNG ANIMATION EXPORTER TESTS PASSED");
	}

	private static void validatesSettingsAndFrameTiming() {
		ApngAnimationExporter.Settings settings = new ApngAnimationExporter.Settings(
			24f, 2, 12, 1.5f, 8, true, 0, 9);
		assertEquals(11, settings.frameCount(), "inclusive APNG frame count");
		assertEquals(0, settings.warmUp(), "legacy APNG settings default to no warm-up");
		ApngAnimationExporter.Settings withoutLastFrame = new ApngAnimationExporter.Settings(
			24f, 2, 12, 0, 1.5f, 8, true, 0, 9, false);
		assertEquals(10, withoutLastFrame.frameCount(), "APNG can omit the last frame");
		assertEquals(11, withoutLastFrame.lastExportFrame(), "APNG reports its actual last output frame");
		assertEquals(417, ApngAnimationExporter.frameDelayUnits(0, 24f), "first 24 FPS delay");
		assertEquals(416, ApngAnimationExporter.frameDelayUnits(1, 24f), "second 24 FPS delay");
		assertEquals(417, ApngAnimationExporter.frameDelayUnits(2, 24f),
			"fractional APNG timing distributes units without drift");
		assertThrows(() -> new ApngAnimationExporter.Settings(101f, 0, 1, 1f, 0, true, 0, 6),
			"APNG FPS above the supported range is rejected");
		assertThrows(() -> new ApngAnimationExporter.Settings(30f, 2, 1, 1f, 0, true, 0, 6),
			"backwards APNG ranges are rejected");
		assertThrows(() -> new ApngAnimationExporter.Settings(30f, 0,
			ApngAnimationExporter.MAX_FRAME_INDEX + 1, 1f, 0, true, 0, 6),
			"excessive APNG warm-up ranges are rejected");
		assertThrows(() -> new ApngAnimationExporter.Settings(30f, 0, 1,
			ApngAnimationExporter.MAX_WARM_UP + 1, 1f, 0, true, 0, 6),
			"excessive APNG warm-up cycles are rejected");
		assertThrows(() -> new ApngAnimationExporter.Settings(30f, 0, 1, 1f, 0, true, -1, 6),
			"negative APNG repeat counts are rejected");
		assertThrows(() -> new ApngAnimationExporter.Settings(30f, 0, 1, 1f, 0, true, 0, 10),
			"APNG compression above nine is rejected");
		assertThrows(() -> new ApngAnimationExporter.Settings(
			30f, 1, 1, 0, 1f, 0, true, 0, 6, false),
			"excluding the only APNG frame is rejected");
	}

	private static void writesValidAtomicApng() throws Exception {
		Path directory = Files.createTempDirectory("openspine-apng-export-").toRealPath();
		Path target = directory.resolve("animation.apng");
		Path failed = directory.resolve("failed.png");
		try {
			ApngAnimationExporter.writeSequenceAtomic(target, 2, 2, 3, 24f, 4, 6,
				ApngAnimationExporterTest::testFrame);
			byte[] bytes = Files.readAllBytes(target);
			assertTrue(bytes.length > PNG_SIGNATURE.length
				&& Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(bytes, PNG_SIGNATURE.length)),
				"atomic APNG writer produces a PNG signature");

			List<Chunk> chunks = chunks(bytes);
			assertChunkOrder(chunks);
			Chunk header = chunks.get(0);
			assertEquals(2, intAt(header.data(), 0), "IHDR width");
			assertEquals(2, intAt(header.data(), 4), "IHDR height");
			assertEquals(8, header.data()[8] & 0xff, "IHDR uses eight bits per channel");
			assertEquals(6, header.data()[9] & 0xff, "IHDR uses RGBA color");
			Chunk animationControl = chunks.get(1);
			assertEquals(3, intAt(animationControl.data(), 0), "acTL frame count");
			assertEquals(4, intAt(animationControl.data(), 4), "acTL repeat count");

			int expectedSequence = 0;
			for (Chunk chunk : chunks) {
				if (!chunk.type().equals("fcTL") && !chunk.type().equals("fdAT")) continue;
				assertEquals(expectedSequence++, intAt(chunk.data(), 0),
					"APNG animation sequence numbers are contiguous");
			}
			List<Chunk> frameControls = chunks.stream().filter(chunk -> chunk.type().equals("fcTL")).toList();
			assertEquals(3, frameControls.size(), "every APNG frame has a control chunk");
			assertEquals(417, unsignedShortAt(frameControls.get(0).data(), 20), "first frame delay numerator");
			assertEquals(416, unsignedShortAt(frameControls.get(1).data(), 20), "second frame delay numerator");
			assertEquals(417, unsignedShortAt(frameControls.get(2).data(), 20), "third frame delay numerator");
			for (Chunk control : frameControls) {
				assertEquals(10_000, unsignedShortAt(control.data(), 22), "APNG delay denominator");
				assertEquals(0, control.data()[24] & 0xff, "full-canvas frames use APNG dispose none");
				assertEquals(0, control.data()[25] & 0xff, "full-canvas frames use APNG source blending");
			}

			BufferedImage first = ImageIO.read(target.toFile());
			assertTrue(first != null, "the first APNG frame is readable as a regular PNG");
			try {
				assertEquals(0xffff0000, first.getRGB(0, 0), "first PNG top-left pixel preserves RGBA and row order");
				assertEquals(0xff00ff00, first.getRGB(1, 0), "first PNG top-right pixel preserves RGBA");
				assertEquals(0xff0000ff, first.getRGB(0, 1), "first PNG bottom-left pixel preserves RGBA");
				assertEquals(0, first.getRGB(1, 1), "first PNG bottom-right pixel preserves transparency");
			} finally {
				first.flush();
			}

			List<byte[]> frames = compressedFrames(chunks);
			assertEquals(3, frames.size(), "every APNG frame has compressed image data");
			assertFrame(frames.get(0), 0);
			assertFrame(frames.get(1), 1);
			assertFrame(frames.get(2), 2);

			byte[] sentinel = "existing APNG".getBytes(StandardCharsets.UTF_8);
			Files.write(failed, sentinel);
			try {
				ApngAnimationExporter.writeSequenceAtomic(failed, 2, 2, 2, 30f, 0, 6, frame -> {
					if (frame == 1) throw new IOException("expected frame failure");
					return testFrame(frame);
				});
				throw new AssertionError("failed APNG sequence should not replace its target");
			} catch (IOException expected) {
				assertTrue(Arrays.equals(sentinel, Files.readAllBytes(failed)),
					"failed APNG export preserves the existing target");
			}
			try (var entries = Files.list(directory)) {
				assertTrue(entries.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
					"APNG writer removes temporary files after success and failure");
			}
			assertThrows(() -> {
				try {
					ApngAnimationExporter.writeSequenceAtomic(directory.resolve("animation.gif"),
						1, 1, 1, 30f, 0, 6, frame -> testFrame(frame));
				} catch (IOException error) {
					throw new IllegalStateException(error);
				}
			}, "non-PNG targets are rejected");
		} finally {
			Files.deleteIfExists(target);
			Files.deleteIfExists(failed);
			try (var entries = Files.list(directory)) {
				for (Path entry : entries.toList()) Files.deleteIfExists(entry);
			}
			Files.deleteIfExists(directory);
		}
	}

	private static Pixmap testFrame(int frame) {
		Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
		ByteBuffer pixels = pixmap.getPixels();
		int[] top = colors(frame);
		int[] bottom = colors(frame + 1);
		putRgba(pixels, 0, bottom[0]);
		putRgba(pixels, 4, bottom[1]);
		putRgba(pixels, 8, top[0]);
		putRgba(pixels, 12, top[1]);
		return pixmap;
	}

	private static int[] colors(int frame) {
		return switch (frame % 4) {
		case 0 -> new int[] {0xffff0000, 0xff00ff00};
		case 1 -> new int[] {0xff0000ff, 0x00000000};
		case 2 -> new int[] {0x80ffffff, 0xff000000};
		default -> new int[] {0xffff00ff, 0xffffff00};
		};
	}

	private static void assertFrame(byte[] compressed, int frame) throws IOException {
		byte[] raw;
		try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
			raw = inflater.readAllBytes();
		}
		assertEquals(18, raw.length, "inflated APNG frame length");
		assertEquals(0, raw[0] & 0xff, "first APNG scanline uses filter none");
		assertEquals(0, raw[9] & 0xff, "second APNG scanline uses filter none");
		int[] top = colors(frame);
		int[] bottom = colors(frame + 1);
		assertRgba(raw, 1, top[0], "APNG top-left pixel for frame " + frame);
		assertRgba(raw, 5, top[1], "APNG top-right pixel for frame " + frame);
		assertRgba(raw, 10, bottom[0], "APNG bottom-left pixel for frame " + frame);
		assertRgba(raw, 14, bottom[1], "APNG bottom-right pixel for frame " + frame);
	}

	private static List<byte[]> compressedFrames(List<Chunk> chunks) throws IOException {
		List<List<byte[]>> frames = new ArrayList<>();
		List<byte[]> current = null;
		for (Chunk chunk : chunks) {
			if (chunk.type().equals("fcTL")) {
				current = new ArrayList<>();
				frames.add(current);
			} else if (chunk.type().equals("IDAT") && current != null) {
				current.add(chunk.data());
			} else if (chunk.type().equals("fdAT") && current != null) {
				current.add(Arrays.copyOfRange(chunk.data(), 4, chunk.data().length));
			}
		}
		List<byte[]> joined = new ArrayList<>(frames.size());
		for (List<byte[]> parts : frames) joined.add(join(parts));
		return joined;
	}

	private static void assertChunkOrder(List<Chunk> chunks) {
		assertTrue(chunks.size() >= 9, "APNG contains all required chunks");
		assertEquals("IHDR", chunks.get(0).type(), "IHDR is the first APNG chunk");
		assertEquals("acTL", chunks.get(1).type(), "acTL precedes all APNG frames");
		assertEquals("IEND", chunks.get(chunks.size() - 1).type(), "IEND is the last APNG chunk");
		int frame = -1;
		int imageChunks = 0;
		for (int index = 2; index < chunks.size() - 1; index++) {
			String type = chunks.get(index).type();
			if (type.equals("fcTL")) {
				assertTrue(frame < 0 || imageChunks > 0, "each APNG frame has image data");
				frame++;
				imageChunks = 0;
			} else if (type.equals("IDAT")) {
				assertEquals(0, frame, "IDAT is used only by the first APNG frame");
				imageChunks++;
			} else if (type.equals("fdAT")) {
				assertTrue(frame > 0, "fdAT is used only after the first APNG frame");
				imageChunks++;
			} else {
				throw new AssertionError("unexpected APNG chunk: " + type);
			}
		}
		assertEquals(2, frame, "APNG contains three ordered frames");
		assertTrue(imageChunks > 0, "last APNG frame has image data");
	}

	private static byte[] join(List<byte[]> parts) throws IOException {
		int length = 0;
		for (byte[] part : parts) length = Math.addExact(length, part.length);
		byte[] result = new byte[length];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, result, offset, part.length);
			offset += part.length;
		}
		return result;
	}

	private static List<Chunk> chunks(byte[] png) throws IOException {
		List<Chunk> result = new ArrayList<>();
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(
			png, PNG_SIGNATURE.length, png.length - PNG_SIGNATURE.length))) {
			while (input.available() > 0) {
				int length = input.readInt();
				assertTrue(length >= 0 && length <= input.available() - 8, "PNG chunk length is valid");
				byte[] typeBytes = input.readNBytes(4);
				byte[] data = input.readNBytes(length);
				long storedCrc = Integer.toUnsignedLong(input.readInt());
				CRC32 crc = new CRC32();
				crc.update(typeBytes);
				crc.update(data);
				assertEquals(crc.getValue(), storedCrc, "PNG chunk CRC is valid");
				result.add(new Chunk(new String(typeBytes, StandardCharsets.US_ASCII), data));
			}
		}
		return result;
	}

	private static int intAt(byte[] data, int offset) {
		return (data[offset] & 0xff) << 24 | (data[offset + 1] & 0xff) << 16
			| (data[offset + 2] & 0xff) << 8 | data[offset + 3] & 0xff;
	}

	private static int unsignedShortAt(byte[] data, int offset) {
		return (data[offset] & 0xff) << 8 | data[offset + 1] & 0xff;
	}

	private static void putRgba(ByteBuffer pixels, int offset, int argb) {
		pixels.put(offset, (byte)(argb >>> 16));
		pixels.put(offset + 1, (byte)(argb >>> 8));
		pixels.put(offset + 2, (byte)argb);
		pixels.put(offset + 3, (byte)(argb >>> 24));
	}

	private static void assertRgba(byte[] data, int offset, int argb, String message) {
		int actual = (data[offset + 3] & 0xff) << 24 | (data[offset] & 0xff) << 16
			| (data[offset + 1] & 0xff) << 8 | data[offset + 2] & 0xff;
		assertEquals(argb, actual, message);
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertThrows(ThrowingRunnable action, String message) {
		try {
			action.run();
		} catch (Exception expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private record Chunk(String type, byte[] data) {}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private ApngAnimationExporterTest() {}
}
