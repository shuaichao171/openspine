package com.openspine.io;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.openspine.model.SkeletonDocument;

/** Headless validation tests that run before atlas or OpenGL access. */
public final class SkeletonLoaderTest {
	public static void main(String[] args) throws Exception {
		testsAtlasAlphaMode();
		rejectsLegacyRuntimeJsonBeforeParsing();
		rejectsVersionlessRuntimeJsonBeforeParsing();
		rejectsUnsupportedBinaryVersionBeforeParsing();
		readsLegacyBinaryVersionHeader();
		rejectsVersionlessAndDamagedBinaryHeaders();
		buildsOfficialRuntimeImportCommand();
		convertsLegacyRuntimeThroughConfiguredCli();
		convertsLegacyBinaryHeaderThroughConfiguredCli();
		loadsTexturelessSavedProjectsWithoutAtlas();
		loadsTexturelessAnimationSources();
		importsSpineProjectThroughConfiguredCli();
		fallsBackToMatchingSiblingJsonWhenCliNeedsActivation();
		rejectsLegacySiblingRuntimeWhenCliNeedsActivation();
		reportsActivationRequirementWithoutFallback();
		doesNotMaskCliExportFailuresWithSiblingRuntime();
		requiresSelectionForUnrelatedSingleSiblingRuntime();
		loadsSelectedAmbiguousSiblingRuntimeFallback();
		rejectsRuntimeJsonWithoutBones();
		readsMalformedCliLogSafely();
		resolvesLooseImageDirectoriesAndRegionNames();
		readsLooseImagePathFromBinarySkeletons();
		sizesLooseAtlasPagesWithinGpuLimits();
		findsAmbiguousSiblingAtlasesWithoutGuessing();
		try {
			SkeletonLoader.load(new SkeletonDocument(), new FileHandle("missing.unsupported"));
			throw new AssertionError("unsupported extension should fail");
		} catch (RuntimeException error) {
			if (error.getMessage() == null || !error.getMessage().contains("expected .spine, .json or .skel"))
				throw new AssertionError("wrong validation error: " + error.getMessage(), error);
		}
		try {
			SkeletonLoader.load(new SkeletonDocument(), new FileHandle("definitely-missing.json"));
			throw new AssertionError("missing skeleton should fail before atlas loading");
		} catch (RuntimeException error) {
			if (error.getMessage() == null || !error.getMessage().contains("Skeleton file not found"))
				throw new AssertionError("wrong missing-file error: " + error.getMessage(), error);
		}
		assertEquals("work/hero.JSON", SkeletonLoader.savePathFor("work/hero.JSON"),
			"JSON source is associated with Save");
		assertEquals("work/hero.skel", SkeletonLoader.savePathFor("work/hero.skel"),
			"binary source is associated with Save");
		assertEquals(null, SkeletonLoader.savePathFor(null), "missing source has no Save association");
		if (!SkeletonLoader.isSupportedFileName("work/hero.SPINE")
			|| !SkeletonLoader.isSupportedFileName("work/hero.JSON")
			|| !SkeletonLoader.isSupportedFileName("work/hero.skel"))
			throw new AssertionError("all supported skeleton and project extensions are recognized");
		if (SkeletonLoader.isSupportedFileName("work/hero.atlas"))
			throw new AssertionError("unrelated asset extensions are not treated as skeleton projects");
		if (!SkeletonLoader.isSpineProjectFileName("work/hero.SPINE")
			|| SkeletonLoader.isSpineProjectFileName("work/hero.json"))
			throw new AssertionError("only .spine projects use the background CLI conversion path");
		System.out.println("ALL SKELETON LOADER TESTS PASSED");
	}

	private static void findsAmbiguousSiblingAtlasesWithoutGuessing() throws Exception {
		Path directory = Files.createTempDirectory("openspine-atlas-candidates-");
		try {
			Path skeleton = directory.resolve("hero.json");
			Files.writeString(skeleton, "{}");
			Files.writeString(directory.resolve("characters.atlas"), "");
			Files.writeString(directory.resolve("effects.atlas"), "");
			var candidates = SkeletonLoader.atlasCandidates(new FileHandle(skeleton.toFile()));
			if (!candidates.equals(java.util.List.of("characters.atlas", "effects.atlas")))
				throw new AssertionError("ambiguous sibling atlases must be returned in stable order: " + candidates);
			Files.writeString(directory.resolve("hero.atlas"), "");
			candidates = SkeletonLoader.atlasCandidates(new FileHandle(skeleton.toFile()));
			if (!candidates.equals(java.util.List.of("hero.atlas")))
				throw new AssertionError("same-named atlas must take precedence: " + candidates);
		} finally {
			try (var paths = Files.walk(directory)) {
				paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (Exception ignored) { }
				});
			}
		}
	}

	private static void loadsTexturelessAnimationSources() throws Exception {
		Path file = Files.createTempFile("openspine-animation-source-", ".json");
		try {
			Files.writeString(file, """
				{"skeleton":{"spine":"4.3.0"},"bones":[{"name":"root"}],
				 "animations":{"walk":{"bones":{"root":{"rotate":[
				 {"time":0,"value":0},{"time":1,"value":20}]}}}}}
				""", StandardCharsets.UTF_8);
			SkeletonData data = SkeletonLoader.loadAnimationSource(new FileHandle(file.toFile()));
			assertEquals("walk", data.getAnimations().first().getName(),
				"animation sources load without an atlas or OpenGL texture");
			if (data.getAnimations().first().getTimelines().first().getFrameCount() != 2)
				throw new AssertionError("animation source retains its keyframes");
		} finally {
			Files.deleteIfExists(file);
		}
	}

	private static void readsLooseImagePathFromBinarySkeletons() throws Exception {
		Path directory = Files.createTempDirectory("openspine-binary-images-");
		Path binary = directory.resolve("hero.skel");
		try {
			SkeletonData data = new SkeletonData();
			data.setName("hero");
			data.setImagesPath("art/characters");
			BoneData root = new BoneData(0, "root", null);
			root.getSetupPose().setInherit(BoneData.Inherit.normal);
			data.getBones().add(root);
			SkeletonDocument document = new SkeletonDocument();
			document.data = data;
			document.skeleton = new Skeleton(data);
			SkeletonBinaryExporter.writeAtomic(document, binary);
			assertEquals("art/characters", SkeletonLoader.readBinaryImagesPath(new FileHandle(binary.toFile())),
				"binary skeleton images path can be read before constructing a loose atlas");
			Path resolved = SkeletonLoader.resolveImagesDirectory("art/characters",
				new FileHandle(binary.toFile()), new FileHandle(directory.toFile()), "SKEL");
			assertEquals(directory.resolve("art/characters").toAbsolutePath().normalize().toString(),
				resolved.toString(), "binary loose image paths resolve relative to the resource directory");
		} finally {
			Files.deleteIfExists(binary);
			Files.deleteIfExists(directory);
		}
	}

	private static void rejectsLegacyRuntimeJsonBeforeParsing() throws Exception {
		Path file = Files.createTempFile("openspine-legacy-runtime-", ".json");
		SkeletonDocument document = new SkeletonDocument();
		try {
			Files.writeString(file, legacyConstraintJson(), StandardCharsets.UTF_8);
			assertUnsupportedVersion(() -> SkeletonLoader.load(document, new FileHandle(file.toFile())),
				"Spine 3.8", "a direct legacy JSON load must fail before the 4.3 parser can discard constraints");
		} finally {
			document.dispose();
			Files.deleteIfExists(file);
		}
	}

	private static void rejectsVersionlessRuntimeJsonBeforeParsing() throws Exception {
		Path file = Files.createTempFile("openspine-versionless-runtime-", ".json");
		SkeletonDocument document = new SkeletonDocument();
		try {
			Files.writeString(file, "{\"bones\":[{\"name\":\"root\"}]}", StandardCharsets.UTF_8);
			assertUnsupportedVersion(() -> SkeletonLoader.load(document, new FileHandle(file.toFile())),
				"未声明版本", "a versionless JSON file must not be guessed as Spine 4.3");
		} finally {
			document.dispose();
			Files.deleteIfExists(file);
		}
	}

	private static void rejectsUnsupportedBinaryVersionBeforeParsing() throws Exception {
		Path file = Files.createTempFile("openspine-legacy-runtime-", ".skel");
		SkeletonDocument source = texturelessDocument(false);
		SkeletonDocument loaded = new SkeletonDocument();
		try {
			SkeletonBinaryExporter.writeAtomic(source, file);
			byte[] bytes = Files.readAllBytes(file);
			replaceAscii(bytes, "4.3.0", "4.2.0");
			Files.write(file, bytes);
			assertEquals("4.2.0", SkeletonLoader.readBinaryVersion(new FileHandle(file.toFile())),
				"the binary version is read from the header without parsing its version-sensitive body");
			assertUnsupportedVersion(() -> SkeletonLoader.load(loaded, new FileHandle(file.toFile())),
				"Spine 4.2.0", "an unsupported SKEL version must be rejected before runtime parsing");
		} finally {
			source.dispose();
			loaded.dispose();
			Files.deleteIfExists(file);
		}
	}

	private static void readsLegacyBinaryVersionHeader() throws Exception {
		Path file = Files.createTempFile("openspine-legacy-header-", ".skel");
		try {
			Files.write(file, legacyBinaryHeader("legacy-hash", "3.8.99"));
			assertEquals("3.8.99", SkeletonLoader.declaredRuntimeVersion(new FileHandle(file.toFile())),
				"Spine 3.x string-hash binary headers expose their declared version safely");
		} finally {
			Files.deleteIfExists(file);
		}
	}

	private static void rejectsVersionlessAndDamagedBinaryHeaders() throws Exception {
		Path versionless = Files.createTempFile("openspine-versionless-header-", ".skel");
		Path damaged = Files.createTempFile("openspine-damaged-header-", ".skel");
		try {
			Files.write(versionless, legacyBinaryHeader("legacy-hash", null));
			assertEquals(null, SkeletonLoader.declaredRuntimeVersion(new FileHandle(versionless.toFile())),
				"a structurally valid legacy header may explicitly omit its version");

			Files.write(damaged, new byte[] {(byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80});
			try {
				SkeletonLoader.declaredRuntimeVersion(new FileHandle(damaged.toFile()));
				throw new AssertionError("a damaged binary header must not be guessed");
			} catch (RuntimeException expected) {
				if (expected.getMessage() == null || !expected.getMessage().contains("无法安全读取 SKEL 版本"))
					throw new AssertionError("damaged binary header error must be explicit: " + expected.getMessage(), expected);
			}
		} finally {
			Files.deleteIfExists(damaged);
			Files.deleteIfExists(versionless);
		}
	}

	private static void buildsOfficialRuntimeImportCommand() {
		Path cli = Path.of(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
			? "C:/tools/Spine.cmd" : "/opt/Spine/Spine");
		Path input = Path.of("C:/work/legacy.json");
		Path output = Path.of("C:/work/converted.spine");
		java.util.List<String> command = SpineProjectImporter.runtimeImportCommand(cli, input, output, "hero main");
		assertCommandSequence(command, "-i", input.toString());
		assertCommandSequence(command, "-o", output.toString());
		assertCommandSequence(command, "--to", "hero main");
		if (command.contains("--name") || !command.contains("-r"))
			throw new AssertionError("runtime conversion must use Spine's skeleton import arguments: " + command);
	}

	private static void convertsLegacyRuntimeThroughConfiguredCli() throws Exception {
		Path directory = Files.createTempDirectory("openspine-runtime-convert-test-");
		Path runtime = directory.resolve("legacy.json");
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		Path cli = directory.resolve(windows ? "convert-spine.cmd" : "convert-spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SpineProjectImporter.PreparedProject prepared = null;
		SpineProjectImporter.LoadedSkeleton loaded = null;
		Path sessionDirectory = null;
		String original = legacyConstraintJson();
		try {
			Files.writeString(runtime, original, StandardCharsets.UTF_8);
			Files.writeString(cli, windows ? convertingWindowsCli() : convertingUnixCli(), StandardCharsets.UTF_8);
			if (!windows && !cli.toFile().setExecutable(true))
				throw new AssertionError("unable to make the converting fake Spine CLI executable");
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());

			prepared = SpineProjectImporter.prepareRuntimeConversion(new FileHandle(runtime.toFile()));
			sessionDirectory = prepared.skeletonFile().getParent().getParent();
			if (!Files.isRegularFile(prepared.skeletonFile()))
				throw new AssertionError("runtime conversion must produce a temporary 4.3 skeleton");
			loaded = SpineProjectImporter.loadSelectedPrepared(prepared);
			prepared = null;
			assertEquals("converted-root", loaded.document().data.getBones().first().getName(),
				"the converted 4.3 runtime is loaded instead of parsing the legacy body directly");
			assertEquals(runtime.toAbsolutePath().normalize().toString(), loaded.document().filePath,
				"the legacy runtime remains the displayed source path");
			assertEquals(null, loaded.document().savePath,
				"converted runtime data requires Save As and cannot overwrite its legacy source");
			assertEquals(original, Files.readString(runtime, StandardCharsets.UTF_8),
				"official conversion never modifies the source runtime file");
			loaded.document().dispose();
			loaded = null;
			if (Files.exists(sessionDirectory))
				throw new AssertionError("disposing converted runtime data must clean the conversion session");
		} finally {
			if (previousCli == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
			else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previousCli);
			if (loaded != null) loaded.document().dispose();
			if (prepared != null) prepared.close();
			Files.deleteIfExists(cli);
			Files.deleteIfExists(runtime);
			Files.deleteIfExists(directory);
		}
	}

	private static void convertsLegacyBinaryHeaderThroughConfiguredCli() throws Exception {
		Path directory = Files.createTempDirectory("openspine-binary-runtime-convert-test-");
		Path runtime = directory.resolve("legacy.skel");
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		Path cli = directory.resolve(windows ? "convert-spine.cmd" : "convert-spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SpineProjectImporter.PreparedProject prepared = null;
		Path sessionDirectory = null;
		byte[] original = legacyBinaryHeader("legacy-hash", "3.8.99");
		try {
			Files.write(runtime, original);
			Files.writeString(cli, windows ? convertingWindowsCli() : convertingUnixCli(), StandardCharsets.UTF_8);
			if (!windows && !cli.toFile().setExecutable(true))
				throw new AssertionError("unable to make the converting fake Spine CLI executable");
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());

			prepared = SpineProjectImporter.prepareRuntimeConversion(new FileHandle(runtime.toFile()));
			sessionDirectory = prepared.skeletonFile().getParent().getParent();
			assertEquals(runtime.toAbsolutePath().normalize().toString(), prepared.projectFile().toString(),
				"legacy binary conversion keeps the original source identity");
			if (!java.util.Arrays.equals(original, Files.readAllBytes(runtime)))
				throw new AssertionError("legacy binary conversion must not modify the source file");
			prepared.close();
			prepared = null;
			if (Files.exists(sessionDirectory))
				throw new AssertionError("closing legacy binary conversion must remove temporary output");
		} finally {
			if (previousCli == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
			else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previousCli);
			if (prepared != null) prepared.close();
			Files.deleteIfExists(cli);
			Files.deleteIfExists(runtime);
			Files.deleteIfExists(directory);
		}
	}

	private static String convertingWindowsCli() {
		return """
			@echo off
			setlocal
			set "output="
			set "exportMode="
			:args
			if "%~1"=="" goto execute
			if /i "%~1"=="-o" (
			  set "output=%~2"
			  shift
			)
			if /i "%~1"=="-e" set "exportMode=1"
			shift
			goto args
			:execute
			if defined exportMode goto export
			> "%output%" echo fake converted project
			exit /b 0
			:export
			> "%output%\\converted.json" echo {"skeleton":{"spine":"4.3.0"},"bones":[{"name":"converted-root"}]}
			echo fake conversion complete
			""";
	}

	private static String convertingUnixCli() {
		return """
			#!/bin/sh
			output=
			export_mode=
			while [ "$#" -gt 0 ]; do
			  if [ "$1" = "-o" ]; then
			    output=$2
			    shift 2
			    continue
			  fi
			  if [ "$1" = "-e" ]; then export_mode=1; fi
			  shift
			done
			if [ -n "$export_mode" ]; then
			  printf '%s\n' '{"skeleton":{"spine":"4.3.0"},"bones":[{"name":"converted-root"}]}' > "$output/converted.json"
			else
			  printf '%s\n' 'fake converted project' > "$output"
			fi
			printf '%s\n' 'fake conversion complete'
			""";
	}

	private static void fallsBackToMatchingSiblingJsonWhenCliNeedsActivation() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-fallback-");
		Path project = directory.resolve("hero.spine");
		Path runtime = directory.resolve("hero.json");
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		Path cli = directory.resolve(windows ? "inactive-spine.cmd" : "inactive-spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SkeletonDocument loaded = new SkeletonDocument();
		SpineProjectImporter.PreparedProject prepared = null;
		Path sessionDirectory = null;
		try {
			Files.writeString(project, "fake Spine project", StandardCharsets.UTF_8);
			Files.writeString(runtime,
				"{\"skeleton\":{\"spine\":\"4.3.0\"},\"bones\":[{\"name\":\"fallback-root\"}]}",
				StandardCharsets.UTF_8);
			Files.writeString(cli, windows ? inactiveWindowsCli() : inactiveUnixCli(), StandardCharsets.UTF_8);
			if (!windows && !cli.toFile().setExecutable(true))
				throw new AssertionError("unable to make the inactive fake Spine CLI executable");
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());

			prepared = SpineProjectImporter.prepare(new FileHandle(project.toFile()));
			sessionDirectory = prepared.skeletonFile().getParent().getParent();
			assertEquals(runtime.toAbsolutePath().normalize().toString(), prepared.runtimeSource().toString(),
				"an inactive CLI falls back to a sibling runtime skeleton");
			if (prepared.skeletonFile().equals(runtime))
				throw new AssertionError("fallback preparation must not use the user's runtime JSON in place");
			SpineProjectImporter.loadPrepared(loaded, prepared);

			assertEquals("fallback-root", loaded.data.getBones().first().getName(),
				"a declared Spine 4.3 sibling runtime remains a valid fallback");
		} finally {
			if (previousCli == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
			else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previousCli);
			loaded.dispose();
			if (prepared != null) prepared.close();
			Files.deleteIfExists(cli);
			Files.deleteIfExists(runtime);
			Files.deleteIfExists(project);
			Files.deleteIfExists(directory);
			if (sessionDirectory != null && Files.exists(sessionDirectory))
				throw new AssertionError("fallback session directory was not cleaned up");
		}
	}

	private static void rejectsLegacySiblingRuntimeWhenCliNeedsActivation() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-legacy-fallback-");
		Path project = directory.resolve("hero.spine");
		Path runtime = directory.resolve("hero.json");
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		Path cli = directory.resolve(windows ? "inactive-spine.cmd" : "inactive-spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		try {
			Files.writeString(project, "fake Spine project", StandardCharsets.UTF_8);
			Files.writeString(runtime, legacyConstraintJson(), StandardCharsets.UTF_8);
			Files.writeString(cli, windows ? inactiveWindowsCli() : inactiveUnixCli(), StandardCharsets.UTF_8);
			if (!windows && !cli.toFile().setExecutable(true))
				throw new AssertionError("unable to make the inactive fake Spine CLI executable");
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			assertUnsupportedVersion(() -> SpineProjectImporter.prepare(new FileHandle(project.toFile())),
				"Spine 3.8", "a sibling legacy runtime must not bypass failed official conversion");
		} finally {
			if (previousCli == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
			else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previousCli);
			Files.deleteIfExists(cli);
			Files.deleteIfExists(runtime);
			Files.deleteIfExists(project);
			Files.deleteIfExists(directory);
		}
	}

	private static void reportsActivationRequirementWithoutFallback() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-activation-");
		Path project = directory.resolve("hero.spine");
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		Path cli = directory.resolve(windows ? "inactive-spine.cmd" : "inactive-spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		try {
			Files.writeString(project, "fake Spine project", StandardCharsets.UTF_8);
			Files.writeString(cli, windows ? inactiveWindowsCli() : inactiveUnixCli(), StandardCharsets.UTF_8);
			if (!windows && !cli.toFile().setExecutable(true))
				throw new AssertionError("unable to make the inactive fake Spine CLI executable");
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			try {
				SpineProjectImporter.prepare(new FileHandle(project.toFile()));
				throw new AssertionError("an inactive CLI without fallback data must fail");
			} catch (RuntimeException expected) {
				if (expected.getMessage() == null || !expected.getMessage().contains("尚未激活"))
					throw new AssertionError("activation failure should be explicit: " + expected.getMessage(), expected);
			}
		} finally {
			if (previousCli == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
			else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previousCli);
			Files.deleteIfExists(cli);
			Files.deleteIfExists(project);
			Files.deleteIfExists(directory);
		}
	}

	private static void doesNotMaskCliExportFailuresWithSiblingRuntime() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-cli-failure-");
		Path project = directory.resolve("hero.spine");
		Path runtime = directory.resolve("hero.json");
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		Path cli = directory.resolve(windows ? "failed-spine.cmd" : "failed-spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		try {
			Files.writeString(project, "fake Spine project", StandardCharsets.UTF_8);
			Files.writeString(runtime, "{\"bones\":[{\"name\":\"stale-root\"}]}", StandardCharsets.UTF_8);
			Files.writeString(cli, windows ? failedWindowsCli() : failedUnixCli(), StandardCharsets.UTF_8);
			if (!windows && !cli.toFile().setExecutable(true))
				throw new AssertionError("unable to make the failed fake Spine CLI executable");
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			try {
				SpineProjectImporter.prepare(new FileHandle(project.toFile()));
				throw new AssertionError("a real CLI export failure must not load stale sibling runtime data");
			} catch (RuntimeException expected) {
				if (expected.getMessage() == null || !expected.getMessage().contains("退出码 7"))
					throw new AssertionError("the original CLI failure must be preserved: " + expected.getMessage(), expected);
			}
		} finally {
			if (previousCli == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
			else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previousCli);
			Files.deleteIfExists(cli);
			Files.deleteIfExists(runtime);
			Files.deleteIfExists(project);
			Files.deleteIfExists(directory);
		}
	}

	private static void requiresSelectionForUnrelatedSingleSiblingRuntime() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-unrelated-fallback-");
		Path project = directory.resolve("hero.spine");
		Path runtime = directory.resolve("enemy.json");
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		Path cli = directory.resolve(windows ? "inactive-spine.cmd" : "inactive-spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SpineProjectImporter.PreparedProject prepared = null;
		try {
			Files.writeString(project, "fake Spine project", StandardCharsets.UTF_8);
			Files.writeString(runtime, """
				{"skeleton":{"spine":"4.3.0"},"bones":[{"name":"enemy-root"}]}
				""", StandardCharsets.UTF_8);
			Files.writeString(cli, windows ? inactiveWindowsCli() : inactiveUnixCli(), StandardCharsets.UTF_8);
			if (!windows && !cli.toFile().setExecutable(true))
				throw new AssertionError("unable to make the inactive fake Spine CLI executable");
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			prepared = SpineProjectImporter.prepare(new FileHandle(project.toFile()));
			if (!prepared.requiresRuntimeSelection())
				throw new AssertionError("an unrelated single runtime must require explicit confirmation");
			java.util.List<SpineProjectImporter.RuntimeCandidate> candidates = prepared.runtimeCandidates();
			if (candidates.size() != 1 || !"enemy.json".equals(candidates.get(0).name()))
				throw new AssertionError("the unrelated runtime must only be offered, never auto-selected: " + candidates);
		} finally {
			if (previousCli == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
			else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previousCli);
			if (prepared != null) prepared.close();
			Files.deleteIfExists(cli);
			Files.deleteIfExists(runtime);
			Files.deleteIfExists(project);
			Files.deleteIfExists(directory);
		}
	}

	private static void loadsSelectedAmbiguousSiblingRuntimeFallback() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-ambiguous-fallback-");
		Path project = directory.resolve("hero.spine");
		Path idle = directory.resolve("idle.json");
		Path walk = directory.resolve("walk.json");
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		Path cli = directory.resolve(windows ? "inactive-spine.cmd" : "inactive-spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		java.util.List<SpineProjectImporter.LoadedSkeleton> loaded = java.util.List.of();
		SpineProjectImporter.PreparedProject prepared = null;
		Path sessionDirectory = null;
		try {
			Files.writeString(project, "fake Spine project", StandardCharsets.UTF_8);
			Files.writeString(idle,
				"{\"skeleton\":{\"spine\":\"4.3.0\"},\"bones\":[{\"name\":\"idle-root\"}]}",
				StandardCharsets.UTF_8);
			Files.writeString(walk,
				"{\"skeleton\":{\"spine\":\"4.3.0\"},\"bones\":[{\"name\":\"walk-root\"}]}",
				StandardCharsets.UTF_8);
			Files.writeString(cli, windows ? inactiveWindowsCli() : inactiveUnixCli(), StandardCharsets.UTF_8);
			if (!windows && !cli.toFile().setExecutable(true))
				throw new AssertionError("unable to make the inactive fake Spine CLI executable");
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());
			prepared = SpineProjectImporter.prepare(new FileHandle(project.toFile()));
			if (!prepared.requiresRuntimeSelection())
				throw new AssertionError("multiple unrelated runtime skeletons must request a user selection");
			java.util.List<SpineProjectImporter.RuntimeCandidate> candidates = prepared.runtimeCandidates();
			if (candidates.size() != 2 || !"idle.json".equals(candidates.get(0).name())
				|| !"walk.json".equals(candidates.get(1).name()))
				throw new AssertionError("all sorted runtime candidates must be offered: " + candidates);
			prepared.selectRuntime(0);
			sessionDirectory = prepared.skeletonFile().getParent().getParent();
			loaded = SpineProjectImporter.loadAllPrepared(prepared);
			if (loaded.size() != 1)
				throw new AssertionError("only the explicitly selected fallback skeleton must be loaded");
			assertEquals("idle-root", loaded.get(0).document().skeleton.getRootBone().getData().getName(),
				"the selected runtime skeleton is loaded");
			loaded.get(0).document().dispose();
			if (Files.exists(sessionDirectory))
				throw new AssertionError("disposing the selected skeleton must clean the import session directory");
		} finally {
			if (previousCli == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
			else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previousCli);
			for (SpineProjectImporter.LoadedSkeleton skeleton : loaded) skeleton.document().dispose();
			if (prepared != null) prepared.close();
			Files.deleteIfExists(cli);
			Files.deleteIfExists(walk);
			Files.deleteIfExists(idle);
			Files.deleteIfExists(project);
			Files.deleteIfExists(directory);
		}
	}

	private static void rejectsRuntimeJsonWithoutBones() throws Exception {
		Path file = Files.createTempFile("openspine-not-a-skeleton-", ".json");
		SkeletonDocument document = new SkeletonDocument();
		try {
			Files.writeString(file,
				"{\"skeleton\":{\"spine\":\"4.3.0\"},\"name\":\"ordinary settings\"}",
				StandardCharsets.UTF_8);
			try {
				SkeletonLoader.load(document, new FileHandle(file.toFile()));
				throw new AssertionError("ordinary JSON must not be accepted as an empty skeleton");
			} catch (RuntimeException expected) {
				if (expected.getMessage() == null || !expected.getMessage().contains("至少需要一个骨骼"))
					throw new AssertionError("empty skeleton validation should be explicit: " + expected.getMessage(), expected);
			}
		} finally {
			document.dispose();
			Files.deleteIfExists(file);
		}
	}

	private static void readsMalformedCliLogSafely() throws Exception {
		Path log = Files.createTempFile("openspine-malformed-cli-log-", ".txt");
		try {
			Files.write(log, new byte[] {'x', (byte)0xc3, '(', '\n', 'a', 'c', 't', 'i', 'v', 'a', 't', 'i', 'o', 'n'});
			String output = SpineProjectImporter.readLog(log);
			if (!output.contains("activation"))
				throw new AssertionError("malformed CLI bytes must not hide later diagnostic text: " + output);
		} finally {
			Files.deleteIfExists(log);
		}
	}

	private static void resolvesLooseImageDirectoriesAndRegionNames() throws Exception {
		Path directory = Files.createTempDirectory("openspine-loose-images-");
		Path skeleton = directory.resolve("hero.json");
		Path images = Files.createDirectories(directory.resolve("images").resolve("beard"));
		Path image = images.resolve("beard-0.png");
		try {
			Files.writeString(skeleton,
				"{\"skeleton\":{\"images\":\"./images/\"},\"bones\":[{\"name\":\"root\"}]}",
				StandardCharsets.UTF_8);
			Path resolved = SkeletonLoader.resolveImagesDirectory(new FileHandle(skeleton.toFile()),
				new FileHandle(directory.toFile()));
			assertEquals(directory.resolve("images").toAbsolutePath().normalize().toString(), resolved.toString(),
				"relative skeleton.images path resolves against the runtime source directory");
			assertEquals("beard/beard-0", SkeletonLoader.looseRegionName(resolved, image),
				"loose PNG paths use Spine atlas region naming");
		} finally {
			Files.deleteIfExists(skeleton);
			Files.deleteIfExists(image);
			Files.deleteIfExists(images);
			Files.deleteIfExists(directory.resolve("images"));
			Files.deleteIfExists(directory);
		}
	}

	private static void sizesLooseAtlasPagesWithinGpuLimits() {
		if (SkeletonLoader.looseAtlasPageDimension(2048, 512, 8192) != 2048)
			throw new AssertionError("small loose images must keep the default page size");
		if (SkeletonLoader.looseAtlasPageDimension(2048, 3000, 8192) != 4096)
			throw new AssertionError("large loose images must grow to the next practical page size");
		if (SkeletonLoader.looseAtlasPageDimension(2048, 5000, 6000) != 6000)
			throw new AssertionError("non-power-of-two GPU limits remain usable");
		try {
			SkeletonLoader.looseAtlasPageDimension(2048, 4096, 4096);
			throw new AssertionError("images without room for atlas padding must be rejected");
		} catch (IllegalArgumentException expected) {
			if (expected.getMessage() == null || !expected.getMessage().contains("GPU"))
				throw new AssertionError("oversized loose image errors should identify the GPU limit", expected);
		}
	}

	private static String legacyConstraintJson() {
		return """
			{
			  "skeleton": {"spine": "3.8"},
			  "bones": [
			    {"name": "root"},
			    {"name": "source", "parent": "root", "transform": "onlyTranslation"},
			    {"name": "constrained", "parent": "root", "transform": "noRotationOrReflection"}
			  ],
			  "slots": [{"name": "path-slot", "bone": "root"}],
			  "ik": [{"name": "aim", "order": 2, "bones": ["constrained"], "target": "source"}],
			  "transform": [{
			    "name": "follow", "order": 0, "bones": ["constrained"], "target": "source",
			    "rotation": 5, "x": 2, "rotateMix": 0.25, "translateMix": 0.5,
			    "scaleMix": 0.75, "shearMix": 0.1
			  }],
			  "path": [{
			    "name": "path-follow", "order": 1, "bones": ["constrained"], "target": "path-slot",
			    "rotateMix": 0.2, "translateMix": 0.3
			  }],
			  "skins": [{"name": "default", "attachments": {}}],
			  "animations": {"move": {"transform": {"follow": [{
			    "rotateMix": 0.4, "translateMix": 0.6, "scaleMix": 0.7, "shearMix": 0.8
			  }]}}}
			}
			""";
	}

	private static void replaceAscii(byte[] bytes, String expected, String replacement) {
		byte[] search = expected.getBytes(StandardCharsets.US_ASCII);
		byte[] value = replacement.getBytes(StandardCharsets.US_ASCII);
		if (search.length != value.length) throw new IllegalArgumentException("replacement length must match");
		for (int offset = 0; offset <= bytes.length - search.length; offset++) {
			boolean matches = true;
			for (int index = 0; index < search.length; index++)
				if (bytes[offset + index] != search[index]) { matches = false; break; }
			if (!matches) continue;
			System.arraycopy(value, 0, bytes, offset, value.length);
			return;
		}
		throw new AssertionError("binary fixture does not contain version " + expected);
	}

	private static byte[] legacyBinaryHeader(String hash, String version) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		writeHeaderString(output, hash);
		writeHeaderString(output, version);
		return output.toByteArray();
	}

	private static void writeHeaderString(ByteArrayOutputStream output, String value) {
		if (value == null) {
			writePositiveVarInt(output, 0);
			return;
		}
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		writePositiveVarInt(output, bytes.length + 1);
		output.writeBytes(bytes);
	}

	private static void writePositiveVarInt(ByteArrayOutputStream output, int value) {
		while (true) {
			int next = value & 0x7f;
			value >>>= 7;
			if (value == 0) {
				output.write(next);
				return;
			}
			output.write(next | 0x80);
		}
	}

	private static void assertUnsupportedVersion(ThrowingRunnable action, String detail, String message)
		throws Exception {
		try {
			action.run();
			throw new AssertionError(message);
		} catch (RuntimeException expected) {
			String text = expected.getMessage();
			if (text == null || !text.contains(detail) || !text.contains("只支持直接读取 Spine 4.3")
				|| !text.contains("原文件不会被修改"))
				throw new AssertionError("unsupported version error must be explicit: " + text, expected);
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable { void run() throws Exception; }

	private static String inactiveWindowsCli() {
		return """
			@echo off
			echo Enter activation code:
			exit /b 1
			""";
	}

	private static String inactiveUnixCli() {
		return """
			#!/bin/sh
			printf '%s\\n' 'Enter activation code:'
			exit 1
			""";
	}

	private static String failedWindowsCli() {
		return """
			@echo off
			echo project parse failed
			exit /b 7
			""";
	}

	private static String failedUnixCli() {
		return """
			#!/bin/sh
			printf '%s\n' 'project parse failed'
			exit 7
			""";
	}

	private static void importsSpineProjectThroughConfiguredCli() throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-import-");
		Path project = directory.resolve("hero.spine");
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		Path cli = directory.resolve(windows ? "fake-spine.cmd" : "fake-spine");
		String previousCli = System.getProperty(SpineProjectImporter.CLI_PROPERTY);
		SkeletonDocument loaded = new SkeletonDocument();
		SpineProjectImporter.PreparedProject prepared = null;
		Path sessionDirectory = null;
		try {
			Files.writeString(project, "fake Spine project", StandardCharsets.UTF_8);
			Files.writeString(cli, windows ? fakeWindowsCli() : fakeUnixCli(), StandardCharsets.UTF_8);
			if (!windows && !cli.toFile().setExecutable(true))
				throw new AssertionError("unable to make the fake Spine CLI executable");
			System.setProperty(SpineProjectImporter.CLI_PROPERTY, cli.toString());

			prepared = SpineProjectImporter.prepare(new FileHandle(project.toFile()));
			sessionDirectory = prepared.skeletonFile().getParent().getParent();
			if (!Files.isRegularFile(prepared.skeletonFile()))
				throw new AssertionError("background preparation produces a runtime skeleton file");
			SpineProjectImporter.loadPrepared(loaded, prepared);

			assertEquals("imported-root", loaded.skeleton.getRootBone().getData().getName(),
				"a .spine project is exported through the configured CLI and loaded");
			assertEquals(project.toAbsolutePath().normalize().toString(), loaded.filePath,
				"the original .spine path remains the displayed source");
			assertEquals(null, loaded.savePath,
				"an imported editor project requires Save As instead of overwriting the source");
			assertEquals(null, loaded.atlasFileName,
				"a textureless imported project does not invent an atlas resource");
			loaded.dispose();
			if (Files.exists(sessionDirectory))
				throw new AssertionError("disposing an imported document must remove its CLI session directory");
		} finally {
			if (previousCli == null) System.clearProperty(SpineProjectImporter.CLI_PROPERTY);
			else System.setProperty(SpineProjectImporter.CLI_PROPERTY, previousCli);
			loaded.dispose();
			if (prepared != null) prepared.close();
			Files.deleteIfExists(cli);
			Files.deleteIfExists(project);
			Files.deleteIfExists(directory);
		}
	}

	private static String fakeWindowsCli() {
		return """
			@echo off
			setlocal
			set "output="
			:args
			if "%~1"=="" goto export
			if /i "%~1"=="-o" (
			  set "output=%~2"
			  shift
			)
			shift
			goto args
			:export
			> "%output%\\imported.json" echo {"skeleton":{"spine":"4.3.0"},"bones":[{"name":"imported-root"}]}
			echo fake export complete
			""";
	}

	private static String fakeUnixCli() {
		return """
			#!/bin/sh
			output=
			while [ "$#" -gt 0 ]; do
			  if [ "$1" = "-o" ]; then
			    output=$2
			    shift 2
			    continue
			  fi
			  shift
			done
			printf '%s\\n' '{"skeleton":{"spine":"4.3.0"},"bones":[{"name":"imported-root"}]}' > "$output/imported.json"
			printf '%s\\n' 'fake export complete'
			""";
	}

	private static void loadsTexturelessSavedProjectsWithoutAtlas() throws Exception {
		Path directory = Files.createTempDirectory("openspine-textureless-load-");
		Path json = directory.resolve("empty.json");
		Path officialJson = directory.resolve("official-empty.json");
		Path binary = directory.resolve("empty.skel");
		Path packageFile = directory.resolve("empty-project.zip");
		Path textured = directory.resolve("textured.json");
		SkeletonDocument source = texturelessDocument(true);
		SkeletonDocument binarySource = texturelessDocument(false);
		SkeletonDocument loadedJson = new SkeletonDocument();
		SkeletonDocument loadedBinary = new SkeletonDocument();
		try {
			SkeletonExporter.writeAtomic(source, json);
			Files.writeString(officialJson,
				"{\"skeleton\":{\"spine\":\"4.3.23\",\"images\":\"./images\"},\"bones\":[{\"name\":\"root\"}]}",
				StandardCharsets.UTF_8);
			SkeletonBinaryExporter.writeAtomic(binarySource, binary);
			SkeletonLoader.load(loadedJson, new FileHandle(json.toFile()));
			SkeletonLoader.load(loadedBinary, new FileHandle(binary.toFile()));
			SkeletonDocument officialLoaded = new SkeletonDocument();
			try {
				SkeletonLoader.load(officialLoaded, new FileHandle(officialJson.toFile()));
				assertEquals("root", officialLoaded.skeleton.getRootBone().getData().getName(),
					"an official textureless export ignores its absent default images directory");
			} finally {
				officialLoaded.dispose();
			}
			assertEquals("root", loadedJson.skeleton.getRootBone().getData().getName(),
				"a saved textureless JSON project reopens without an atlas");
			assertEquals("root", loadedBinary.skeleton.getRootBone().getData().getName(),
				"a saved textureless binary project reopens without an atlas");
			assertEquals(null, loadedJson.atlasFileName,
				"a missing optional atlas is not recorded as an external resource");
			ProjectPackager.PackageResult result = ProjectPackager.write(loadedJson, packageFile);
			if (result.fileCount() != 1)
				throw new AssertionError("textureless package should contain only its skeleton");

			Files.writeString(textured, ExampleSkeleton.JSON, StandardCharsets.UTF_8);
			try {
				SkeletonLoader.load(new SkeletonDocument(), new FileHandle(textured.toFile()));
				throw new AssertionError("textured skeletons must still reject a missing atlas");
			} catch (RuntimeException expected) {
				// AtlasAttachmentLoader rejects the first unresolved Region attachment.
			}
		} finally {
			source.dispose();
			binarySource.dispose();
			loadedJson.dispose();
			loadedBinary.dispose();
			Files.deleteIfExists(packageFile);
			Files.deleteIfExists(textured);
			Files.deleteIfExists(binary);
			Files.deleteIfExists(officialJson);
			Files.deleteIfExists(json);
			Files.deleteIfExists(directory);
		}
	}

	private static SkeletonDocument texturelessDocument(boolean emptyDefaultSkin) {
		SkeletonData data = new SkeletonData();
		data.setName("empty");
		data.setVersion("4.3.0");
		data.setFps(30f);
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		root.setVisible(true);
		data.getBones().add(root);
		if (emptyDefaultSkin) {
			Skin skin = new Skin("default");
			data.getSkins().add(skin);
			data.setDefaultSkin(skin);
		}
		Skeleton skeleton = new Skeleton(data);
		if (data.getDefaultSkin() != null) skeleton.setSkin(data.getDefaultSkin());
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(new TextureAtlas(), data, skeleton, "new:empty", "", null);
		return document;
	}

	private static void testsAtlasAlphaMode() {
		TextureAtlasData straight = new TextureAtlasData();
		straight.getPages().add(new Page());
		if (SkeletonLoader.premultipliedAlpha(straight))
			throw new AssertionError("ordinary atlas pages use straight alpha");

		TextureAtlasData premultiplied = new TextureAtlasData();
		Page first = new Page();
		first.pma = true;
		Page second = new Page();
		second.pma = true;
		premultiplied.getPages().add(first);
		premultiplied.getPages().add(second);
		if (!SkeletonLoader.premultipliedAlpha(premultiplied))
			throw new AssertionError("PMA atlas metadata is preserved");

		second.pma = false;
		if (!SkeletonLoader.premultipliedAlpha(premultiplied))
			throw new AssertionError("mixed atlas pages retain the first-page fallback while rendering per texture");
		DummyTexture firstTexture = new DummyTexture();
		DummyTexture secondTexture = new DummyTexture();
		first.texture = firstTexture;
		second.texture = secondTexture;
		AtlasAlphaModes.register(premultiplied);
		if (!AtlasAlphaModes.premultiplied(firstTexture, false)
			|| AtlasAlphaModes.premultiplied(secondTexture, true))
			throw new AssertionError("mixed atlas page PMA is retained by texture identity");
	}

	private static final class DummyTexture extends com.badlogic.gdx.graphics.Texture {
		DummyTexture() { super(); }
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertCommandSequence(java.util.List<String> command, String option, String value) {
		int index = command.indexOf(option);
		if (index < 0 || index + 1 >= command.size() || !value.equals(command.get(index + 1)))
			throw new AssertionError("missing command sequence " + option + " " + value + ": " + command);
	}

	private SkeletonLoaderTest() {}
}
