package com.openspine.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Headless regression tests for image sequence naming and transactional replacement. */
public final class PngAnimationExporterTest {
	public static void main(String[] args) throws Exception {
		validatesSettingsAndNaming();
		commitsAndRollsBackSequences();
		System.out.println("ALL PNG ANIMATION EXPORTER TESTS PASSED");
	}

	private static void validatesSettingsAndNaming() {
		PngAnimationExporter.Settings settings = new PngAnimationExporter.Settings(
			24f, 2, 12, 1.5f, 8, true, false);
		assertEquals(11, settings.frameCount(), "inclusive PNG sequence frame count");
		assertEquals(0, settings.warmUp(), "legacy PNG sequence settings default to no warm-up");
		assertEquals(PngFrameExporter.DEFAULT_COMPRESSION, settings.compression(),
			"legacy PNG sequence settings use default compression");
		assertTrue(settings.includeLastFrame(), "legacy PNG sequence settings include the last frame");
		PngAnimationExporter.Settings withoutLastFrame = new PngAnimationExporter.Settings(
			24f, 2, 12, 0, 1.5f, 8, 9, true, false, false);
		assertEquals(10, withoutLastFrame.frameCount(), "PNG sequence can omit the last frame");
		assertEquals(11, withoutLastFrame.lastExportFrame(), "PNG sequence reports its actual last output frame");
		List<Path> withoutLastTargets = PngAnimationExporter.sequenceTargets(
			Path.of("loop.png"), withoutLastFrame.startFrame(), withoutLastFrame.lastExportFrame());
		assertEquals("loop_0011.png", withoutLastTargets.get(withoutLastTargets.size() - 1).getFileName().toString(),
			"PNG sequence naming omits the excluded last frame");
		List<Path> generated = PngAnimationExporter.sequenceTargets(Path.of("hero.png"), 2, 12);
		assertEquals("hero_0002.png", generated.get(0).getFileName().toString(),
			"PNG sequence adds a visible four-digit frame template");
		assertEquals("hero_0012.png", generated.get(generated.size() - 1).getFileName().toString(),
			"PNG sequence names use actual animation frame numbers");
		List<Path> explicit = PngAnimationExporter.sequenceTargets(Path.of("hero-02.PNG"), 2, 12);
		assertEquals("hero-02.PNG", explicit.get(0).getFileName().toString(),
			"an explicit matching frame template and extension case are preserved");
		assertEquals("hero-12.PNG", explicit.get(explicit.size() - 1).getFileName().toString(),
			"an explicit template keeps its requested digit width");
		assertThrows(IllegalArgumentException.class,
			() -> new PngAnimationExporter.Settings(101f, 0, 1, 1f, 0, true, false),
			"PNG sequence FPS above the supported range is rejected");
		assertThrows(IllegalArgumentException.class,
			() -> new PngAnimationExporter.Settings(30f, 0, 1,
				PngAnimationExporter.MAX_WARM_UP + 1, 1f, 0, true, false),
			"PNG sequence warm-up above the supported range is rejected");
		assertThrows(IllegalArgumentException.class,
			() -> new PngAnimationExporter.Settings(30f, 0, 1, 0, 1f, 0, 10, true, false),
			"PNG sequence compression above nine is rejected");
		assertThrows(IllegalArgumentException.class,
			() -> new PngAnimationExporter.Settings(30f, 2, 2, 0, 1f, 0, 6, true, false, false),
			"excluding the only PNG sequence frame is rejected");
		assertThrows(IllegalArgumentException.class,
			() -> PngAnimationExporter.sequenceTargets(Path.of("hero.gif"), 0, 1),
			"PNG sequence targets require a PNG extension");
	}

	private static void commitsAndRollsBackSequences() throws Exception {
		Path directory = Files.createTempDirectory("openspine-sequence-transaction-").toRealPath();
		List<Path> targets = PngAnimationExporter.sequenceTargets(directory.resolve("walk.png"), 0, 2);
		try {
			Files.writeString(targets.get(0), "old-0", StandardCharsets.UTF_8);
			Files.writeString(targets.get(1), "old-1", StandardCharsets.UTF_8);
			assertThrows(FileAlreadyExistsException.class,
				() -> FrameSequenceTransaction.writeAtomic(targets, false,
					(index, staged) -> Files.writeString(staged, "new-" + index, StandardCharsets.UTF_8)),
				"sequence replacement requires explicit overwrite permission");
			assertEquals("old-0", Files.readString(targets.get(0)),
				"declined overwrite preserves the first existing frame");

			FrameSequenceTransaction.writeAtomic(targets, true,
				(index, staged) -> Files.writeString(staged, "new-" + index, StandardCharsets.UTF_8));
			for (int index = 0; index < targets.size(); index++)
				assertEquals("new-" + index, Files.readString(targets.get(index)),
					"successful sequence commit installs every staged frame");

			Files.writeString(targets.get(0), "sentinel", StandardCharsets.UTF_8);
			assertThrows(IOException.class, () -> FrameSequenceTransaction.writeAtomic(targets, true,
				(index, staged) -> {
					if (index == 1) throw new IOException("expected encoding failure");
					Files.writeString(staged, "partial", StandardCharsets.UTF_8);
				}), "encoding failure aborts before replacing any destination");
			assertEquals("sentinel", Files.readString(targets.get(0)),
				"encoding failure preserves existing sequence files");

			for (Path target : targets) Files.deleteIfExists(target);
			Files.writeString(targets.get(0), "rollback-old", StandardCharsets.UTF_8);
			assertThrows(IOException.class, () -> FrameSequenceTransaction.writeAtomic(targets, true,
				(index, staged) -> {
					Files.writeString(staged, "rollback-new-" + index, StandardCharsets.UTF_8);
					if (index == targets.size() - 1) Files.createDirectory(targets.get(1));
				}), "a commit-time collision triggers rollback");
			assertEquals("rollback-old", Files.readString(targets.get(0)),
				"commit rollback restores a replaced original frame");
			assertTrue(Files.isDirectory(targets.get(1)),
				"rollback does not remove an unrelated colliding directory");
			Files.delete(targets.get(1));

			Path stagedDirectory = Files.createDirectory(directory.resolve("rendered"));
			List<Path> staged = List.of(stagedDirectory.resolve("0.png"), stagedDirectory.resolve("1.png"),
				stagedDirectory.resolve("2.png"));
			for (int index = 0; index < staged.size(); index++)
				Files.writeString(staged.get(index), "rendered-" + index, StandardCharsets.UTF_8);
			FrameSequenceTransaction.commitStaged(targets, staged, true);
			for (int index = 0; index < targets.size(); index++)
				assertEquals("rendered-" + index, Files.readString(targets.get(index)),
					"staged sequence commit installs every rendered frame");
			Files.delete(stagedDirectory);
			assertNoTransactionDirectories(directory);
		} finally {
			for (Path target : targets) {
				if (Files.isDirectory(target)) Files.delete(target);
				else Files.deleteIfExists(target);
			}
			try (var entries = Files.walk(directory)) {
				for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList())
					if (!entry.equals(directory)) Files.deleteIfExists(entry);
			}
			Files.deleteIfExists(directory);
		}
	}

	private static void assertNoTransactionDirectories(Path directory) throws IOException {
		try (var entries = Files.list(directory)) {
			assertTrue(entries.noneMatch(path -> path.getFileName().toString()
				.startsWith(".openspine-sequence-")), "sequence transaction directories are cleaned up");
		}
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertThrows(Class<? extends Throwable> type, ThrowingRunnable action,
		String message) {
		try {
			action.run();
		} catch (Throwable error) {
			if (type.isInstance(error)) return;
			throw new AssertionError(message + ": wrong exception " + error, error);
		}
		throw new AssertionError(message + ": expected " + type.getSimpleName());
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private PngAnimationExporterTest() {}
}
