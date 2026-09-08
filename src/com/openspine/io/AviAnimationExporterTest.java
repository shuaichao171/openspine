package com.openspine.io;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

/** Headless regression tests for MJPEG AVI headers, frames, index, and atomic output. */
public final class AviAnimationExporterTest {
	public static void main(String[] args) throws Exception {
		validatesSettings();
		writesValidAtomicAvi();
		System.out.println("ALL AVI ANIMATION EXPORTER TESTS PASSED");
	}

	private static void validatesSettings() {
		AviAnimationExporter.Settings settings = new AviAnimationExporter.Settings(
			24f, 2, 12, 1.5f, 8, 92);
		assertEquals(11, settings.frameCount(), "inclusive AVI frame count");
		assertEquals(0, settings.warmUp(), "legacy AVI settings default to no warm-up");
		AviAnimationExporter.Settings withoutLastFrame = new AviAnimationExporter.Settings(
			24f, 2, 12, 0, 1.5f, 8, 92, false);
		assertEquals(10, withoutLastFrame.frameCount(), "AVI can omit the last frame");
		assertEquals(11, withoutLastFrame.lastExportFrame(), "AVI reports its actual last output frame");
		assertThrows(() -> new AviAnimationExporter.Settings(101f, 0, 1, 1f, 0, 90),
			"AVI FPS above the supported range is rejected");
		assertThrows(() -> new AviAnimationExporter.Settings(30f, 2, 1, 1f, 0, 90),
			"backwards AVI ranges are rejected");
		assertThrows(() -> new AviAnimationExporter.Settings(30f, 0,
			AviAnimationExporter.MAX_FRAME_INDEX + 1, 1f, 0, 90),
			"excessive AVI warm-up ranges are rejected");
		assertThrows(() -> new AviAnimationExporter.Settings(30f, 0, 1,
			AviAnimationExporter.MAX_WARM_UP + 1, 1f, 0, 90),
			"excessive AVI warm-up cycles are rejected");
		assertThrows(() -> new AviAnimationExporter.Settings(30f, 0, 1, 1f, 0, 101),
			"AVI quality above 100 is rejected");
		assertThrows(() -> new AviAnimationExporter.Settings(30f, 1, 1, 0, 1f, 0, 90, false),
			"excluding the only AVI frame is rejected");
	}

	private static void writesValidAtomicAvi() throws Exception {
		Path directory = Files.createTempDirectory("openspine-avi-export-").toRealPath();
		Path target = directory.resolve("animation.avi");
		Path failed = directory.resolve("failed.avi");
		try {
			AviAnimationExporter.writeSequenceAtomic(target, 8, 6, 3, 24f, 100,
				AviAnimationExporterTest::testFrame);
			byte[] bytes = Files.readAllBytes(target);
			assertEquals("RIFF", fourCc(bytes, 0), "AVI has a RIFF signature");
			assertEquals(bytes.length - 8L, uintAt(bytes, 4), "RIFF size covers the complete AVI file");
			assertEquals("AVI ", fourCc(bytes, 8), "RIFF form type is AVI");

			List<Chunk> topLevel = chunks(bytes, 12, bytes.length);
			Chunk headerList = findList(bytes, topLevel, "hdrl");
			Chunk movieList = findList(bytes, topLevel, "movi");
			Chunk index = find(topLevel, "idx1");
			List<Chunk> headers = chunks(bytes, headerList.dataStart() + 4,
				headerList.dataStart() + headerList.size());
			Chunk mainHeader = find(headers, "avih");
			assertEquals(56, mainHeader.size(), "AVI main header has the standard size");
			assertEquals(41_667, uintAt(bytes, mainHeader.dataStart()), "AVI frame period encodes 24 FPS");
			assertEquals(0x10, uintAt(bytes, mainHeader.dataStart() + 12), "AVI declares an index");
			assertEquals(3, uintAt(bytes, mainHeader.dataStart() + 16), "AVI main header frame count");
			assertEquals(1, uintAt(bytes, mainHeader.dataStart() + 24), "AVI has one video stream");
			assertEquals(8, uintAt(bytes, mainHeader.dataStart() + 32), "AVI main header width");
			assertEquals(6, uintAt(bytes, mainHeader.dataStart() + 36), "AVI main header height");

			Chunk streamList = findList(bytes, headers, "strl");
			List<Chunk> streamHeaders = chunks(bytes, streamList.dataStart() + 4,
				streamList.dataStart() + streamList.size());
			Chunk streamHeader = find(streamHeaders, "strh");
			assertEquals("vids", fourCc(bytes, streamHeader.dataStart()), "AVI stream is video");
			assertEquals("MJPG", fourCc(bytes, streamHeader.dataStart() + 4), "AVI stream uses MJPEG");
			assertEquals(1_000_000, uintAt(bytes, streamHeader.dataStart() + 20), "AVI rate scale");
			assertEquals(24_000_000, uintAt(bytes, streamHeader.dataStart() + 24), "AVI rate value");
			assertEquals(3, uintAt(bytes, streamHeader.dataStart() + 32), "AVI stream frame count");
			assertEquals(10_000, uintAt(bytes, streamHeader.dataStart() + 40), "AVI stream quality");
			Chunk format = find(streamHeaders, "strf");
			assertEquals(40, format.size(), "AVI bitmap format has the standard size");
			assertEquals(8, uintAt(bytes, format.dataStart() + 4), "AVI bitmap width");
			assertEquals(6, uintAt(bytes, format.dataStart() + 8), "AVI bitmap height");
			assertEquals(1, ushortAt(bytes, format.dataStart() + 12), "AVI bitmap plane count");
			assertEquals(24, ushortAt(bytes, format.dataStart() + 14), "AVI bitmap depth");
			assertEquals("MJPG", fourCc(bytes, format.dataStart() + 16), "AVI bitmap compression");

			List<Chunk> frames = chunks(bytes, movieList.dataStart() + 4,
				movieList.dataStart() + movieList.size());
			assertEquals(3, frames.size(), "AVI movi list contains every frame");
			int maxFrameSize = 0;
			for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
				Chunk frame = frames.get(frameIndex);
				maxFrameSize = Math.max(maxFrameSize, frame.size());
				assertEquals("00dc", frame.type(), "AVI frame uses the compressed video chunk id");
				BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes,
					frame.dataStart(), frame.size()));
				assertTrue(decoded != null, "AVI MJPEG frame can be decoded by ImageIO");
				try {
					assertEquals(8, decoded.getWidth(), "decoded AVI frame width");
					assertEquals(6, decoded.getHeight(), "decoded AVI frame height");
					assertDominant(decoded.getRGB(4, 3), frameIndex,
						"decoded AVI frame preserves its requested color");
				} finally {
					decoded.flush();
				}
			}
			assertTrue(maxFrameSize > 8 * 6 * 3,
				"the fixture proves compressed MJPEG frames can exceed the raw RGB byte count");
			assertEquals(maxFrameSize, uintAt(bytes, mainHeader.dataStart() + 28),
				"AVI main header advertises a sufficient frame buffer");
			assertEquals(maxFrameSize, uintAt(bytes, streamHeader.dataStart() + 36),
				"AVI stream header advertises a sufficient frame buffer");
			assertEquals(maxFrameSize, uintAt(bytes, format.dataStart() + 20),
				"MJPEG bitmap size is the maximum encoded frame size");

			assertEquals(48, index.size(), "AVI idx1 has one entry per frame");
			for (int frameIndex = 0; frameIndex < 3; frameIndex++) {
				int entry = index.dataStart() + frameIndex * 16;
				assertEquals("00dc", fourCc(bytes, entry), "AVI index chunk id");
				assertEquals(0x10, uintAt(bytes, entry + 4), "every MJPEG frame is indexed as a key frame");
				long offset = uintAt(bytes, entry + 8);
				int chunkPosition = Math.toIntExact(movieList.dataStart() + offset);
				assertEquals("00dc", fourCc(bytes, chunkPosition), "AVI index offset resolves to its movi chunk");
				assertEquals(uintAt(bytes, chunkPosition + 4), uintAt(bytes, entry + 12),
					"AVI index size matches its movi chunk");
			}

			byte[] sentinel = "existing AVI".getBytes(StandardCharsets.UTF_8);
			Files.write(failed, sentinel);
			try {
				AviAnimationExporter.writeSequenceAtomic(failed, 8, 6, 2, 30f, 90, frame -> {
					if (frame == 1) throw new IOException("expected frame failure");
					return testFrame(frame);
				});
				throw new AssertionError("failed AVI sequence should not replace its target");
			} catch (IOException expected) {
				assertTrue(Arrays.equals(sentinel, Files.readAllBytes(failed)),
					"failed AVI export preserves the existing target");
			}
			try (var entries = Files.list(directory)) {
				assertTrue(entries.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
					"AVI writer removes temporary files after success and failure");
			}
			assertThrows(() -> {
				try {
					AviAnimationExporter.writeSequenceAtomic(directory.resolve("animation.mov"),
						8, 6, 1, 30f, 90, frame -> testFrame(frame));
				} catch (IOException error) {
					throw new IllegalStateException(error);
				}
			}, "non-AVI targets are rejected");
		} finally {
			Files.deleteIfExists(target);
			Files.deleteIfExists(failed);
			try (var entries = Files.list(directory)) {
				for (Path entry : entries.toList()) Files.deleteIfExists(entry);
			}
			Files.deleteIfExists(directory);
		}
	}

	private static BufferedImage testFrame(int frame) {
		BufferedImage image = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
		int color = switch (frame % 3) {
		case 0 -> 0xffff0000;
		case 1 -> 0xff00ff00;
		default -> 0xff0000ff;
		};
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, color);
		return image;
	}

	private static List<Chunk> chunks(byte[] data, int start, int end) {
		List<Chunk> result = new ArrayList<>();
		for (int offset = start; offset < end;) {
			assertTrue(offset + 8 <= end, "AVI chunk header stays inside its parent");
			long size = uintAt(data, offset + 4);
			assertTrue(size <= Integer.MAX_VALUE && offset + 8L + size <= end,
				"AVI chunk data stays inside its parent");
			result.add(new Chunk(fourCc(data, offset), (int)size, offset + 8));
			offset = Math.toIntExact(offset + 8L + size + (size & 1L));
		}
		return result;
	}

	private static Chunk findList(byte[] data, List<Chunk> chunks, String listType) {
		for (Chunk chunk : chunks)
			if (chunk.type().equals("LIST") && chunk.size() >= 4
				&& fourCc(data, chunk.dataStart()).equals(listType)) return chunk;
		throw new AssertionError("missing AVI LIST " + listType);
	}

	private static Chunk find(List<Chunk> chunks, String type) {
		for (Chunk chunk : chunks)
			if (chunk.type().equals(type)) return chunk;
		throw new AssertionError("missing AVI chunk " + type);
	}

	private static String fourCc(byte[] data, int offset) {
		return new String(data, offset, 4, StandardCharsets.US_ASCII);
	}

	private static long uintAt(byte[] data, int offset) {
		return Integer.toUnsignedLong((data[offset] & 0xff) | (data[offset + 1] & 0xff) << 8
			| (data[offset + 2] & 0xff) << 16 | (data[offset + 3] & 0xff) << 24);
	}

	private static int ushortAt(byte[] data, int offset) {
		return (data[offset] & 0xff) | (data[offset + 1] & 0xff) << 8;
	}

	private static void assertDominant(int argb, int channel, String message) {
		int red = argb >>> 16 & 0xff, green = argb >>> 8 & 0xff, blue = argb & 0xff;
		int dominant = channel == 0 ? red : channel == 1 ? green : blue;
		int other1 = channel == 0 ? green : red;
		int other2 = channel == 2 ? green : blue;
		if (dominant < 200 || dominant <= other1 || dominant <= other2)
			throw new AssertionError(message + ": got 0x" + Integer.toHexString(argb));
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

	private record Chunk(String type, int size, int dataStart) {}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private AviAnimationExporterTest() {}
}
