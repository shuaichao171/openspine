package com.openspine.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.JsonReader;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.openspine.model.SkeletonDocument;

/** Headless coverage for staged Spine project reconstruction and failure protection. */
public final class SpineProjectExporterTest {
	public static void main(String[] args) throws Exception {
		buildsOfficialImportCommand();
		rebuildsMultipleSkeletonsAndReplacesOnlyAfterSuccess();
		stagesImagesAndEventAudioForOfficialImport();
		preservesExistingTargetWhenCliFails();
		rollsBackProjectAndAssetsWhenCommitFails();
		rejectsInvalidTargetsAndHonorsCancellation();
		if (Boolean.getBoolean("openspine.test.realSpineCli")) roundTripsThroughInstalledSpine();
		System.out.println("ALL SPINE PROJECT EXPORTER TESTS PASSED");
	}

	private static void stagesImagesAndEventAudioForOfficialImport() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-resources-");
		Path cli = resourceCheckingCli(directory);
		Path source = Files.createDirectories(directory.resolve("source"));
		Path images = Files.createDirectories(source.resolve("source-images"));
		Path audio = Files.createDirectories(source.resolve("source-audio").resolve("voice"));
		Files.write(images.resolve("hero.png"), new byte[] {1, 2, 3});
		Files.write(audio.resolve("line.wav"), new byte[] {4, 5, 6});
		Path target = directory.resolve("resources.spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SkeletonDocument document = document("resource-hero");
		try {
			document.filePath = source.resolve("resource-hero.json").toString();
			document.resourceDirectory = source.toString();
			document.data.setImagesPath("source-images");
			document.data.setAudioPath("source-audio");
			EventData event = new EventData("voice");
			event.setAudioPath("voice/line.wav");
			event.getSetupPose().setString("");
			document.data.getEvents().add(event);
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			SpineProjectExporter.write(List.of(
				new SpineProjectExporter.ProjectSkeleton("resource hero", document)), target);
			assertEquals("resources-ok", Files.readString(target, StandardCharsets.UTF_8).trim(),
				"CLI receives staged images, audio, and rewritten relative JSON paths");
			Path assets = SpineProjectExporter.companionAssetDirectory(target);
			assertTrue(Files.isRegularFile(assets.resolve("skeleton-1/images/hero.png"))
				&& Files.isRegularFile(assets.resolve("skeleton-1/audio/voice/line.wav")),
				"rebuilt project keeps its external image and audio resources after staging cleanup");
			assertNoSessions(directory);
		} finally {
			restoreCli(previousCli);
			document.dispose();
			deleteDirectory(directory);
		}
	}

	private static void buildsOfficialImportCommand() {
		Path cli = Path.of(isWindows() ? "C:/tools/Spine.cmd" : "/opt/Spine/Spine");
		Path input = Path.of("C:/work/hero.json");
		Path output = Path.of("C:/work/hero.spine");
		List<String> command = SpineProjectExporter.command(cli, input, output, "hero main");
		assertSequence(command, "-i", input.toString());
		assertSequence(command, "-o", output.toString());
		assertSequence(command, "--to", "hero main");
		assertTrue(!command.contains("--name"), "texture pack naming is not used for skeleton imports");
		assertTrue(command.contains("-r"), "official skeleton import mode is enabled");
		assertTrue(!command.contains("-e"), "project reconstruction does not invoke runtime export mode");
	}

	private static void rebuildsMultipleSkeletonsAndReplacesOnlyAfterSuccess() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-rebuild-test-");
		Path cli = fakeCli(directory, false);
		Path target = directory.resolve("rebuilt.spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SkeletonDocument hero = document("hero-data");
		SkeletonDocument enemy = document("enemy-data");
		try {
			Files.writeString(target, "old project", StandardCharsets.UTF_8);
			Path staleAssets = Files.createDirectories(SpineProjectExporter.companionAssetDirectory(target));
			Files.writeString(staleAssets.resolve("stale.txt"), "old asset", StandardCharsets.UTF_8);
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			SpineProjectExporter.ExportResult result = SpineProjectExporter.write(List.of(
				new SpineProjectExporter.ProjectSkeleton("hero", hero),
				new SpineProjectExporter.ProjectSkeleton("enemy", enemy)), target);
			assertTrue(result.skeletonCount() == 2, "every project skeleton is imported");
			String rebuilt = Files.readString(target, StandardCharsets.UTF_8);
			assertTrue(rebuilt.contains("hero") && rebuilt.contains("enemy"),
				"successive CLI imports accumulate in the staged project");
			assertTrue(!rebuilt.contains("old project"), "the complete staged project replaces the old target");
			assertTrue(!Files.exists(staleAssets),
				"a rebuilt project without resources removes stale companion assets");
			assertNoSessions(directory);
		} finally {
			restoreCli(previousCli);
			hero.dispose();
			enemy.dispose();
			deleteDirectory(directory);
		}
	}

	private static void preservesExistingTargetWhenCliFails() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-rebuild-failure-");
		Path cli = fakeCli(directory, true);
		Path target = directory.resolve("existing.spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SkeletonDocument document = document("hero");
		try {
			Files.writeString(target, "keep me", StandardCharsets.UTF_8);
			Path existingAssets = Files.createDirectories(SpineProjectExporter.companionAssetDirectory(target));
			Files.writeString(existingAssets.resolve("keep.txt"), "keep asset", StandardCharsets.UTF_8);
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			assertThrows(() -> SpineProjectExporter.write(List.of(
				new SpineProjectExporter.ProjectSkeleton("hero", document)), target),
				"a nonzero CLI exit must fail reconstruction");
			assertEquals("keep me", Files.readString(target, StandardCharsets.UTF_8),
				"a failed reconstruction never changes the existing destination");
			assertEquals("keep asset", Files.readString(existingAssets.resolve("keep.txt"), StandardCharsets.UTF_8),
				"a failed reconstruction never changes existing companion assets");
			assertNoSessions(directory);
		} finally {
			restoreCli(previousCli);
			document.dispose();
			deleteDirectory(directory);
		}
	}

	private static void rejectsInvalidTargetsAndHonorsCancellation() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-rebuild-validation-");
		Path cli = fakeCli(directory, false);
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SkeletonDocument document = document("hero");
		try {
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			assertThrows(() -> SpineProjectExporter.validateTarget(directory.resolve("hero.json")),
				"the destination must use the .spine extension");
			Path target = directory.resolve("cancelled.spine");
			Files.writeString(target, "unchanged", StandardCharsets.UTF_8);
			CancellationToken cancellation = new CancellationToken();
			cancellation.cancel();
			assertThrows(() -> SpineProjectExporter.write(List.of(
				new SpineProjectExporter.ProjectSkeleton("hero", document)), target, cancellation),
				"pre-cancelled reconstruction stops before invoking Spine");
			assertEquals("unchanged", Files.readString(target, StandardCharsets.UTF_8),
				"cancellation leaves the existing project unchanged");
			assertNoSessions(directory);
		} finally {
			restoreCli(previousCli);
			document.dispose();
			deleteDirectory(directory);
		}
	}

	private static void rollsBackProjectAndAssetsWhenCommitFails() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-commit-rollback-");
		Path session = Files.createDirectory(directory.resolve("session"));
		Path target = directory.resolve("rollback.spine");
		Path assets = SpineProjectExporter.companionAssetDirectory(target);
		Path stagedAssets = Files.createDirectories(session.resolve(assets.getFileName()));
		try {
			Files.writeString(target, "old project", StandardCharsets.UTF_8);
			Files.createDirectories(assets);
			Files.writeString(assets.resolve("old.txt"), "old asset", StandardCharsets.UTF_8);
			Files.writeString(stagedAssets.resolve("new.txt"), "new asset", StandardCharsets.UTF_8);
			assertThrows(() -> SpineProjectExporter.commitProject(session.resolve("missing.spine"),
				stagedAssets, target, assets, session), "a missing staged project must fail the final commit");
			assertEquals("old project", Files.readString(target, StandardCharsets.UTF_8),
				"commit failure restores the previous Spine project");
			assertEquals("old asset", Files.readString(assets.resolve("old.txt"), StandardCharsets.UTF_8),
				"commit failure restores the previous companion resources");
			assertTrue(!Files.exists(assets.resolve("new.txt")),
				"commit failure removes partially installed companion resources");
		} finally {
			deleteDirectory(directory);
		}
	}

	private static void roundTripsThroughInstalledSpine() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-rebuild-real-");
		Path target = directory.resolve("real-rebuilt.spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SkeletonDocument document = document("real-hero");
		SpineProjectImporter.PreparedProject prepared = null;
		try {
			Path cli = SpineProjectImporter.findCli();
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			SpineProjectExporter.ExportResult result = SpineProjectExporter.write(List.of(
				new SpineProjectExporter.ProjectSkeleton("real hero", document)), target);
			assertTrue(result.skeletonCount() == 1 && Files.size(target) > 0,
				"installed Spine creates a nonempty project");
			prepared = SpineProjectImporter.prepare(new FileHandle(target.toFile()));
			var root = new JsonReader().parse(Files.readString(prepared.skeletonFile(), StandardCharsets.UTF_8));
			assertTrue(root.get("bones") != null && root.get("bones").size > 0,
				"the rebuilt project can be exported by installed Spine again");
		} finally {
			if (prepared != null) prepared.close();
			restoreCli(previousCli);
			document.dispose();
			deleteDirectory(directory);
		}
	}

	private static SkeletonDocument document(String name) {
		SkeletonData data = new SkeletonData();
		data.setName(name);
		data.setVersion("4.3.0");
		data.setFps(30f);
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		root.setVisible(true);
		data.getBones().add(root);
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(new TextureAtlas(), data, new Skeleton(data), "new:" + name, "", null);
		return document;
	}

	private static Path fakeCli(Path directory, boolean fail) throws Exception {
		boolean windows = isWindows();
		Path cli = directory.resolve(fail
			? windows ? "failed-spine.cmd" : "failed-spine"
			: windows ? "fake-spine.cmd" : "fake-spine");
		String script;
		if (windows) {
			script = fail ? "@echo off\r\necho simulated import failure\r\nexit /b 9\r\n" : """
				@echo off
				setlocal
				set "output="
				set "skeleton="
				:args
				if "%~1"=="" goto import
				if "%~1"=="-o" (
				  set "output=%~2"
				  shift
				)
				if "%~1"=="--to" (
				  set "skeleton=%~2"
				  shift
				)
				shift
				goto args
				:import
				>> "%output%" echo %skeleton%
				echo fake import complete
				""";
		} else {
			script = fail ? "#!/bin/sh\nprintf '%s\\n' 'simulated import failure'\nexit 9\n" : """
				#!/bin/sh
				output=
				skeleton=
				while [ "$#" -gt 0 ]; do
				  if [ "$1" = "-o" ]; then output=$2; shift 2; continue; fi
				  if [ "$1" = "--to" ]; then skeleton=$2; shift 2; continue; fi
				  shift
				done
				printf '%s\n' "$skeleton" >> "$output"
				printf '%s\n' 'fake import complete'
				""";
		}
		Files.writeString(cli, script, StandardCharsets.UTF_8);
		if (!windows && !cli.toFile().setExecutable(true))
			throw new AssertionError("unable to make fake Spine CLI executable");
		return cli;
	}

	private static Path resourceCheckingCli(Path directory) throws Exception {
		boolean windows = isWindows();
		Path cli = directory.resolve(windows ? "resource-spine.cmd" : "resource-spine");
		String script = windows ? """
			@echo off
			setlocal
			set "input="
			set "output="
			:args
			if "%~1"=="" goto verify
			if "%~1"=="-i" (
			  set "input=%~2"
			  shift
			)
			if "%~1"=="-o" (
			  set "output=%~2"
			  shift
			)
			shift
			goto args
			:verify
			for %%I in ("%input%") do set "inputdir=%%~dpI"
			if not exist "%inputdir%resources.openspine-assets\\skeleton-1\\images\\hero.png" exit /b 7
			if not exist "%inputdir%resources.openspine-assets\\skeleton-1\\audio\\voice\\line.wav" exit /b 8
			findstr /c:"resources.openspine-assets/skeleton-1/images" "%input%" >nul || exit /b 9
			findstr /c:"resources.openspine-assets/skeleton-1/audio" "%input%" >nul || exit /b 10
			> "%output%" echo resources-ok
			""" : """
			#!/bin/sh
			input=
			output=
			while [ "$#" -gt 0 ]; do
			  if [ "$1" = "-i" ]; then input=$2; shift 2; continue; fi
			  if [ "$1" = "-o" ]; then output=$2; shift 2; continue; fi
			  shift
			done
			inputdir=$(dirname "$input")
			test -f "$inputdir/resources.openspine-assets/skeleton-1/images/hero.png" || exit 7
			test -f "$inputdir/resources.openspine-assets/skeleton-1/audio/voice/line.wav" || exit 8
			grep -q 'resources.openspine-assets/skeleton-1/images' "$input" || exit 9
			grep -q 'resources.openspine-assets/skeleton-1/audio' "$input" || exit 10
			printf '%s\n' 'resources-ok' > "$output"
			""";
		Files.writeString(cli, script, StandardCharsets.UTF_8);
		if (!windows && !cli.toFile().setExecutable(true))
			throw new AssertionError("unable to make resource-checking Spine CLI executable");
		return cli;
	}

	private static void assertNoSessions(Path directory) throws Exception {
		try (Stream<Path> paths = Files.list(directory)) {
			assertTrue(paths.noneMatch(path -> path.getFileName().toString()
				.startsWith(".openspine-spine-rebuild-")), "temporary rebuild directories are cleaned up");
		}
	}

	private static void assertSequence(List<String> command, String option, String value) {
		int index = command.indexOf(option);
		assertTrue(index >= 0 && index + 1 < command.size() && value.equals(command.get(index + 1)),
			"command must contain " + option + " followed by its value");
	}

	private static void restoreCli(String previous) {
		if (previous == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
		else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previous);
	}

	private static void deleteDirectory(Path directory) throws Exception {
		if (directory == null || !Files.exists(directory)) return;
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingRunnable { void run() throws Exception; }

	private static void assertThrows(ThrowingRunnable action, String message) {
		try {
			action.run();
		} catch (Exception expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private SpineProjectExporterTest() {}
}
