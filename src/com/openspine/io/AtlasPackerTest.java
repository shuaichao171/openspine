package com.openspine.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.GdxNativesLoader;

/** Headless coverage for atlas staging, cancellation, and source protection. */
public final class AtlasPackerTest {
	public static void main(String[] args) throws Exception {
		GdxNativesLoader.load();
		Path directory = Files.createTempDirectory("openspine-atlas-test-");
		try {
			Path first = directory.resolve("first.png");
			Path second = directory.resolve("second.png");
			writePng(first, 4, 3);
			writePng(second, 2, 5);
			Path third = directory.resolve("third.jpg");
			writeJpeg(third, 3, 2);
			Path target = directory.resolve("packed.atlas");
			AtlasPacker.PackResult result = AtlasPacker.pack(List.of(first, second, third), target, 64, 2);
			assertTrue(result.regionCount() == 3, "every input PNG or JPEG becomes one atlas region");
			assertTrue(Files.isRegularFile(target) && result.pages().size() == 1
				&& Files.isRegularFile(result.pages().get(0)), "staged atlas outputs are committed");
			assertTrue(Files.readString(target).contains("first"), "atlas descriptor contains input region names");
			TextureAtlasData runtimeData = new TextureAtlasData(new FileHandle(target.toFile()),
				new FileHandle(directory.toFile()), false);
			assertTrue(runtimeData.getPages().size == 1 && runtimeData.getRegions().size == 3,
				"generated descriptor reloads as one page with every packed region");
			Path added = directory.resolve("added.png");
			writePng(added, 3, 4);
			AtlasPacker.Settings mixedPageSettings = new AtlasPacker.Settings(64, 64, 2, true,
				AtlasPacker.Settings.Filter.Linear, AtlasPacker.Settings.Filter.Linear,
				AtlasPacker.Settings.Repeat.NONE, false, 1f, true, false);
			AtlasPacker.PackResult merged = AtlasPacker.packIncludingAtlas(List.of(added), target, target,
				mixedPageSettings, new CancellationToken());
			TextureAtlasData mergedData = AtlasFiles.readData(new FileHandle(target.toFile()),
				new FileHandle(directory.toFile()), false);
			assertTrue(merged.regionCount() == 4 && mergedData.getPages().size == 2
				&& mergedData.getRegions().size == 4,
				"packing into the existing descriptor retains its pages and regions");
			assertTrue(!merged.premultipliedAlpha() && !mergedData.getPages().get(0).pma
				&& mergedData.getPages().get(1).pma,
				"retained and newly packed pages keep independent PMA settings");
			assertTrue(regionNames(mergedData).containsAll(List.of("first", "second", "third", "added")),
				"the merged atlas resolves both old attachments and the newly packed image");
			assertThrows(() -> AtlasPacker.packIncludingAtlas(List.of(first), target,
				directory.resolve("duplicate-region.atlas"), AtlasPacker.Settings.square(64, 2),
				new CancellationToken()), "new images cannot shadow retained atlas regions");
			Path largeFirst = directory.resolve("large-first.png");
			Path largeSecond = directory.resolve("large-second.png");
			Path largeThird = directory.resolve("large-third.png");
			writePng(largeFirst, 40, 40);
			writePng(largeSecond, 40, 40);
			writePng(largeThird, 40, 40);
			Path multiTarget = directory.resolve("multipage.atlas");
			AtlasPacker.PackResult multiResult = AtlasPacker.pack(
				List.of(largeFirst, largeSecond, largeThird), multiTarget, 64, 2);
			TextureAtlasData multiData = new TextureAtlasData(new FileHandle(multiTarget.toFile()),
				new FileHandle(directory.toFile()), false);
			assertTrue(multiResult.pages().size() == 3 && multiData.getPages().size == 3
				&& multiData.getRegions().size == 3,
				"blank lines separate atlas pages without turning regions into texture pages");
			AtlasPacker.PackResult secondResult = AtlasPacker.pack(List.of(first, second, third), target, 64, 2);
			assertTrue(secondResult.regionCount() == 3 && Files.readString(target).contains("third"),
				"repacking an existing atlas replaces it without stale backup collisions");

			Path configuredTarget = directory.resolve("configured.atlas");
			AtlasPacker.Settings configured = new AtlasPacker.Settings(64, 128, 1, false,
				AtlasPacker.Settings.Filter.Nearest, AtlasPacker.Settings.Filter.Nearest,
				AtlasPacker.Settings.Repeat.XY);
			AtlasPacker.pack(List.of(first, second, third), configuredTarget, configured);
			String configuredDescriptor = Files.readString(configuredTarget);
			assertTrue(configuredDescriptor.contains("filter: Nearest,Nearest")
				&& configuredDescriptor.contains("repeat: xy"),
				"configured atlas filter and repeat settings are written to the descriptor");

			Path bordered = directory.resolve("bordered.png");
			writeTransparentPng(bordered, 6, 5, 1, 1, 3, 2, 1f, 0.4f, 0.2f, 1f);
			Path trimmedTarget = directory.resolve("trimmed.atlas");
			AtlasPacker.Settings trimmed = new AtlasPacker.Settings(64, 64, 1, false,
				AtlasPacker.Settings.Filter.Linear, AtlasPacker.Settings.Filter.Linear,
				AtlasPacker.Settings.Repeat.NONE, true, 1f, false, false);
			AtlasPacker.pack(List.of(bordered), trimmedTarget, trimmed);
			String trimmedRegion = regionBlock(Files.readString(trimmedTarget), "bordered");
			assertTrue(trimmedRegion.contains("size: 3,2") && trimmedRegion.contains("orig: 6,5")
				&& trimmedRegion.contains("offset: 1,2"),
				"transparent whitespace is removed while preserving Spine region bounds and offset");

			Path scaledTarget = directory.resolve("scaled.atlas");
			AtlasPacker.Settings scaled = new AtlasPacker.Settings(64, 64, 1, false,
				AtlasPacker.Settings.Filter.Linear, AtlasPacker.Settings.Filter.Linear,
				AtlasPacker.Settings.Repeat.NONE, false, 0.5f, false, false);
			AtlasPacker.pack(List.of(first), scaledTarget, scaled);
			String scaledRegion = regionBlock(Files.readString(scaledTarget), "first");
			assertTrue(scaledRegion.contains("size: 2,2") && scaledRegion.contains("orig: 2,2"),
				"atlas scaling changes both the packed and original region dimensions");

			Path translucent = directory.resolve("translucent.png");
			writeTransparentPng(translucent, 2, 2, 0, 0, 2, 2, 1f, 0.5f, 0f, 0.5f);
			Path pmaTarget = directory.resolve("pma.atlas");
			AtlasPacker.Settings pma = new AtlasPacker.Settings(64, 64, 1, false,
				AtlasPacker.Settings.Filter.Linear, AtlasPacker.Settings.Filter.Linear,
				AtlasPacker.Settings.Repeat.NONE, false, 1f, true, false);
			AtlasPacker.PackResult pmaResult = AtlasPacker.pack(List.of(translucent), pmaTarget, pma);
			String pmaDescriptor = Files.readString(pmaTarget);
			assertTrue(pmaResult.premultipliedAlpha() && pmaDescriptor.contains("pma: true"),
				"PMA is reported by both the descriptor and pack result");
			int[] pmaXY = regionXY(regionBlock(pmaDescriptor, "translucent"));
			Pixmap packedPma = new Pixmap(new FileHandle(pmaResult.pages().get(0).toFile()));
			try {
				int pixel = packedPma.getPixel(pmaXY[0], pmaXY[1]);
				int red = pixel >>> 24 & 0xff;
				int green = pixel >>> 16 & 0xff;
				int alpha = pixel & 0xff;
				assertTrue(Math.abs(red - alpha) <= 1 && Math.abs(green * 2 - alpha) <= 2,
					"PMA multiplies RGB channels by alpha in the generated page");
			} finally {
				packedPma.dispose();
			}

			Path firstCopy = directory.resolve("first-copy.png");
			Files.copy(first, firstCopy);
			Path aliasTarget = directory.resolve("alias.atlas");
			AtlasPacker.Settings alias = new AtlasPacker.Settings(64, 64, 1, false,
				AtlasPacker.Settings.Filter.Linear, AtlasPacker.Settings.Filter.Linear,
				AtlasPacker.Settings.Repeat.NONE, false, 1f, false, true);
			AtlasPacker.PackResult aliasResult = AtlasPacker.pack(List.of(first, firstCopy), aliasTarget, alias);
			String aliasDescriptor = Files.readString(aliasTarget);
			assertTrue(aliasResult.regionCount() == 2
				&& java.util.Arrays.equals(regionXY(regionBlock(aliasDescriptor, "first")),
					regionXY(regionBlock(aliasDescriptor, "first-copy"))),
				"identical images share a packed rectangle but retain separate atlas region names");

			Path cancelledTarget = directory.resolve("cancelled.atlas");
			Files.writeString(cancelledTarget, "unchanged");
			CancellationToken cancelled = new CancellationToken();
			cancelled.cancel();
			assertThrows(() -> AtlasPacker.pack(List.of(first), cancelledTarget, 64, 2, cancelled),
				"pre-cancelled packing stops before touching outputs");
			assertTrue(Files.readString(cancelledTarget).equals("unchanged"),
				"cancellation preserves an existing atlas");

			Path collisionTarget = directory.resolve("first.atlas");
			assertThrows(() -> AtlasPacker.pack(List.of(directory.resolve("first.png")), collisionTarget, 64, 2),
				"a generated page cannot overwrite an input PNG");
			Path caseCollisionTarget = directory.resolve("FIRST.atlas");
			assertThrows(() -> AtlasPacker.pack(List.of(directory.resolve("first.png")), caseCollisionTarget, 64, 2),
				"case-insensitive output names cannot overwrite an input image");
			System.out.println("ALL ATLAS PACKER TESTS PASSED");
		} finally {
			try (var paths = Files.walk(directory)) {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (Exception ignored) { }
				});
			}
		}
	}

	private static void writePng(Path path, int width, int height) {
		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		try {
			pixmap.setColor(1f, 0.4f, 0.2f, 1f);
			pixmap.fill();
			PixmapIO.writePNG(new FileHandle(path.toFile()), pixmap);
		} finally {
			pixmap.dispose();
		}
	}

	private static void writeTransparentPng(Path path, int width, int height, int x, int y,
		int regionWidth, int regionHeight, float red, float green, float blue, float alpha) {
		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		try {
			pixmap.setBlending(Pixmap.Blending.None);
			pixmap.setColor(0f, 0f, 0f, 0f);
			pixmap.fill();
			pixmap.setColor(red, green, blue, alpha);
			pixmap.fillRectangle(x, y, regionWidth, regionHeight);
			PixmapIO.writePNG(new FileHandle(path.toFile()), pixmap);
		} finally {
			pixmap.dispose();
		}
	}

	private static String regionBlock(String descriptor, String name) {
		String marker = "\n" + name + "\n";
		int start = descriptor.indexOf(marker);
		if (start < 0) throw new AssertionError("missing atlas region: " + name);
		start += marker.length();
		int end = descriptor.indexOf("\n\n", start);
		return descriptor.substring(start, end < 0 ? descriptor.length() : end);
	}

	private static int[] regionXY(String block) {
		for (String line : block.split("\\R")) {
			if (!line.startsWith("xy: ")) continue;
			String[] values = line.substring(4).split(",");
			return new int[] {Integer.parseInt(values[0].trim()), Integer.parseInt(values[1].trim())};
		}
		throw new AssertionError("missing atlas region coordinates");
	}

	private static java.util.Set<String> regionNames(TextureAtlasData data) {
		java.util.Set<String> names = new java.util.HashSet<>();
		for (TextureAtlasData.Region region : data.getRegions()) names.add(region.name);
		return names;
	}

	private static void writeJpeg(Path path, int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
			image.setRGB(x, y, 0xff336699);
		if (!ImageIO.write(image, "jpg", path.toFile())) throw new AssertionError("JPEG writer unavailable");
	}

	private static void assertThrows(ThrowingAction action, String message) throws Exception {
		try {
			action.run();
		} catch (RuntimeException | java.io.IOException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingAction { void run() throws Exception; }

	private AtlasPackerTest() { }
}
