package com.openspine.io;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.Deflater;

import javax.imageio.ImageIO;

/** Headless coverage for layered PSD decoding and atomic PNG extraction. */
public final class PsdImporterTest {
	private static final byte[][] CHANNELS = {
		{10, 20, 30, 40}, {50, 60, 70, 80}, {90, 100, 110, 120},
		{(byte)200, (byte)255, (byte)128, 0}
	};

	public static void main(String[] args) throws Exception {
		readsRawLayerAndUnicodeName();
		readsRleLayerAndVisibility();
		readsZipPredictionLayer();
		respectsNestedGroupVisibility();
		rejectsUnbalancedGroupMarkers();
		warnsAboutGroupOpacityWithoutIncorrectPerLayerMultiplication();
		fallsBackToCompositeImage();
		surfacesUnsupportedLayerAndColorFeatures();
		extractsWithoutOverwritingEarlierOutput();
		cleanupPreservesPackedAtlasAndUnownedFiles();
		cleanupDoesNotTrustConstructedResultsOrReusedPaths();
		rejectsUnsupportedDepthAndColorMode();
		System.out.println("PSD IMPORTER TEST PASSED");
	}

	private static void respectsNestedGroupVisibility() throws Exception {
		PsdImporter.PsdDocument hiddenOuter = PsdImporter.parse(groupedPsd(false, true, 255), "groups");
		assertEquals(java.util.List.of("Background", "Outer child", "Inner child", "Own hidden", "Foreground"),
			hiddenOuter.layers().stream().map(PsdImporter.PsdLayer::name).toList(),
			"PSD pixel records retain bottom-to-top file order and omit folder dividers");
		assertEquals(java.util.List.of(true, false, false, false, true),
			hiddenOuter.layers().stream().map(PsdImporter.PsdLayer::visible).toList(),
			"hiding an outer folder hides every descendant without hiding neighboring layers");
		PsdImporter.PsdDocument hiddenInner = PsdImporter.parse(groupedPsd(true, false, 255), "groups");
		assertEquals(java.util.List.of(true, true, false, false, true),
			hiddenInner.layers().stream().map(PsdImporter.PsdLayer::visible).toList(),
			"a hidden nested folder leaves the outer folder's other children visible");
		PsdImporter.PsdDocument visible = PsdImporter.parse(groupedPsd(true, true, 255), "groups");
		assertEquals(java.util.List.of(true, true, true, false, true),
			visible.layers().stream().map(PsdImporter.PsdLayer::visible).toList(),
			"a closed folder is still visible and an explicitly hidden child remains hidden");
		Path directory = Files.createTempDirectory("openspine-psd-groups-");
		try {
			Path source = directory.resolve("nested.psd"); Files.write(source, groupedPsd(false, true, 255));
			PsdImporter.ImportResult extracted = PsdImporter.extract(source);
			PsdImporter.ImportResult filtered = extracted.ignoringHiddenLayers();
			assertEquals(java.util.List.of("Background", "Foreground"),
				filtered.layers().stream().map(PsdImporter.ImportedLayer::name).toList(),
				"ignore-hidden filtering uses inherited group visibility");
			assertEquals(java.util.List.of(0, 4), filtered.layers().stream().map(PsdImporter.ImportedLayer::sourceIndex).toList(),
				"filtering inherited visibility preserves pixel-layer mapping indices");
			PsdImporter.cleanup(filtered);
		} finally { deleteTree(directory); }
	}

	private static void warnsAboutGroupOpacityWithoutIncorrectPerLayerMultiplication() throws Exception {
		PsdImporter.PsdDocument document = PsdImporter.parse(groupedPsd(true, true, 128), "groups");
		assertTrue(String.join("\n", document.warnings()).contains("组不透明度"),
			"group opacity limitations are reported explicitly");
		assertEquals(255, document.layers().get(1).image().getRGB(0, 0) >>> 24,
			"group opacity is not incorrectly multiplied into every overlapping child");
	}

	private static void rejectsUnbalancedGroupMarkers() throws Exception {
		byte[] original = groupedPsd(true, true, 255);
		assertThrows(() -> PsdImporter.parse(replaceSectionKind(original, 3, 0), "missing-end"),
			"a missing group boundary cannot silently change descendant visibility");
		assertThrows(() -> PsdImporter.parse(replaceSectionKind(original, 2, 0), "missing-header"),
			"a missing folder header is rejected before extracting incorrect layers");
		assertThrows(() -> PsdImporter.parse(replaceSectionKind(original, 3, 4), "unknown-divider"),
			"unsupported section divider types are rejected");
	}

	private static byte[] replaceSectionKind(byte[] original, int before, int after) {
		byte[] modified = original.clone();
		byte[] tag = "8BIMlsct".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		for (int offset = 0; offset <= modified.length - 16; offset++) {
			boolean match = true;
			for (int i = 0; i < tag.length; i++) if (modified[offset + i] != tag[i]) { match = false; break; }
			if (match && java.nio.ByteBuffer.wrap(modified).getInt(offset + 12) == before) {
				java.nio.ByteBuffer.wrap(modified).putInt(offset + 12, after); return modified;
			}
		}
		throw new AssertionError("missing fixture group marker");
	}

	/** Photoshop stores group end dividers before children and the folder header after children. */
	private static byte[] groupedPsd(boolean outerVisible, boolean innerVisible, int outerOpacity) throws Exception {
		ByteArrayOutputStream records = new ByteArrayOutputStream(), channels = new ByteArrayOutputStream();
		DataOutputStream record = new DataOutputStream(records); record.writeShort(9);
		writeGroupRecord(record, channels, "Background", 0, true, 255, false);
		writeGroupRecord(record, channels, "Outer end", 3, true, 255, false);
		writeGroupRecord(record, channels, "Outer child", 0, true, 255, false);
		writeGroupRecord(record, channels, "Inner end", 3, true, 255, true);
		writeGroupRecord(record, channels, "Inner child", 0, true, 255, false);
		writeGroupRecord(record, channels, "Own hidden", 0, false, 255, false);
		writeGroupRecord(record, channels, "Inner group", 1, innerVisible, 255, true);
		writeGroupRecord(record, channels, "Outer group", 2, outerVisible, outerOpacity, false);
		writeGroupRecord(record, channels, "Foreground", 0, true, 255, false);
		record.write(channels.toByteArray()); record.flush();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
		writeHeader(out, 2, 2, 4, 8, 3);
		out.writeInt(4 + records.size() + 4); out.writeInt(records.size()); out.write(records.toByteArray());
		out.writeInt(0); out.writeShort(0); out.write(new byte[16]); out.flush();
		return bytes.toByteArray();
	}

	private static void writeGroupRecord(DataOutputStream record, ByteArrayOutputStream channels,
		String name, int divider, boolean visible, int opacity, boolean nestedTag) throws Exception {
		record.writeInt(0); record.writeInt(0); record.writeInt(divider == 0 ? 2 : 0); record.writeInt(divider == 0 ? 2 : 0);
		record.writeShort(divider == 0 ? 4 : 0);
		if (divider == 0) for (int id : new int[] {0, 1, 2, -1}) {
			byte[] raw = {(byte)(id < 0 ? 255 : 30), (byte)(id < 0 ? 255 : 30),
				(byte)(id < 0 ? 255 : 30), (byte)(id < 0 ? 255 : 30)};
			byte[] encoded = channelData(raw, 0);
			record.writeShort(id); record.writeInt(encoded.length); channels.write(encoded);
		}
		record.writeBytes("8BIM"); record.writeBytes(divider == 1 || divider == 2 ? "pass" : "norm");
		record.writeByte(opacity); record.writeByte(0); record.writeByte(visible ? 0 : 2); record.writeByte(0);
		ByteArrayOutputStream extraBytes = new ByteArrayOutputStream(); DataOutputStream extra = new DataOutputStream(extraBytes);
		extra.writeInt(0); extra.writeInt(0); extra.writeByte(name.length()); extra.writeBytes(name);
		for (int pad = (4 - (name.length() + 1) % 4) % 4; pad > 0; pad--) extra.writeByte(0);
		if (divider != 0) {
			extra.writeBytes("8BIM"); extra.writeBytes(nestedTag ? "lsdk" : "lsct"); extra.writeInt(4); extra.writeInt(divider);
		}
		extra.flush(); record.writeInt(extraBytes.size()); record.write(extraBytes.toByteArray());
	}

	private static void cleanupPreservesPackedAtlasAndUnownedFiles() throws Exception {
		com.badlogic.gdx.utils.GdxNativesLoader.load();
		Path directory = Files.createTempDirectory("openspine-psd-cleanup-");
		try {
			Path source = directory.resolve("hero.psd");
			Files.write(source, layeredPsd(0, false, 8, 3));
			PsdImporter.ImportResult imported = PsdImporter.extract(source);
			Path input = imported.layers().get(0).png();
			AtlasPacker.PackResult packed = AtlasPacker.pack(java.util.List.of(input),
				imported.directory().resolve("hero.atlas"), 64, 2);
			Path note = imported.directory().resolve("user-note.txt");
			Path nested = Files.createDirectories(imported.directory().resolve("user-assets"));
			Files.writeString(note, "keep my file");
			Files.writeString(nested.resolve("keep.txt"), "keep nested files");
			PsdImporter.cleanup(imported);
			assertTrue(Files.isRegularFile(packed.atlas()) && Files.isRegularFile(packed.pages().get(0)),
				"cleanup preserves a completed atlas saved inside the extraction directory");
			assertTrue(!Files.exists(input), "cleanup removes its unchanged extracted PNG");
			assertTrue(Files.isRegularFile(note) && Files.isRegularFile(nested.resolve("keep.txt")),
				"cleanup preserves files and directories created after extraction");
			var atlas = AtlasFiles.readData(new com.badlogic.gdx.files.FileHandle(packed.atlas().toFile()),
				new com.badlogic.gdx.files.FileHandle(imported.directory().toFile()), false);
			assertEquals(1, atlas.getRegions().size, "packed descriptor remains reloadable after cleanup");
			assertTrue(ImageIO.read(packed.pages().get(0).toFile()) != null,
				"packed atlas page remains decodable after cleanup");

			PsdImporter.ImportResult edited = PsdImporter.extract(source);
			Path editedPng = edited.layers().get(0).png();
			byte[] bytes = Files.readAllBytes(editedPng);
			java.nio.file.attribute.FileTime modified = Files.getLastModifiedTime(editedPng);
			bytes[bytes.length - 1] ^= 1;
			Files.write(editedPng, bytes);
			Files.setLastModifiedTime(editedPng, modified);
			PsdImporter.cleanup(edited.ignoringHiddenLayers());
			assertTrue(Files.isRegularFile(editedPng) && java.util.Arrays.equals(bytes, Files.readAllBytes(editedPng)),
				"cleanup preserves changed contents even with the original size and timestamp");
		} finally { deleteTree(directory); }
	}

	private static void cleanupDoesNotTrustConstructedResultsOrReusedPaths() throws Exception {
		Path directory = Files.createTempDirectory("openspine-psd-ownership-");
		try {
			Path source = directory.resolve("hero.psd");
			Files.write(source, layeredPsd(0, false, 8, 3));
			Path unrelated = Files.createDirectory(directory.resolve("hero_layers_backup"));
			Path valuable = unrelated.resolve("keep.txt"); Files.writeString(valuable, "keep");
			PsdImporter.cleanup(new PsdImporter.ImportResult(source, unrelated, 4, 3, java.util.List.of()));
			assertTrue(Files.exists(valuable), "a constructed result cannot claim an existing similarly named directory");
			PsdImporter.ImportResult first = PsdImporter.extract(source);
			PsdImporter.cleanup(first.ignoringHiddenLayers());
			assertTrue(!Files.exists(first.directory()), "filtered result retains extraction ownership and removes an empty directory");
			PsdImporter.ImportResult second = PsdImporter.extract(source);
			assertEquals(first.directory(), second.directory(), "fixture reuses the released extraction path");
			PsdImporter.cleanup(first);
			assertTrue(Files.exists(second.layers().get(0).png()), "repeated cleanup cannot delete a later extraction at the same path");
			PsdImporter.cleanup(second);
			PsdImporter.ImportResult replaced = PsdImporter.extract(source);
			Path moved = directory.resolve("moved-original");
			Files.move(replaced.directory(), moved);
			Files.createDirectory(replaced.directory());
			Path originalName = replaced.layers().get(0).png().getFileName();
			Files.copy(moved.resolve(originalName), replaced.directory().resolve(originalName));
			PsdImporter.cleanup(replaced);
			assertTrue(Files.exists(replaced.directory().resolve(originalName)) && Files.exists(moved.resolve(originalName)),
				"a replaced extraction directory cannot inherit the original directory's cleanup permission");
			PsdImporter.ImportResult hidden = PsdImporter.extract(source);
			PsdImporter.ImportResult manuallyFiltered = new PsdImporter.ImportResult(hidden.source(), hidden.directory(),
				hidden.canvasWidth(), hidden.canvasHeight(), hidden.layers());
			PsdImporter.cleanup(manuallyFiltered);
			assertTrue(Files.exists(hidden.layers().get(0).png()), "a reconstructed result cannot claim cleanup ownership");
			PsdImporter.cleanup(hidden);
		} finally { deleteTree(directory); }
	}

	private static void readsRawLayerAndUnicodeName() throws Exception {
		PsdImporter.PsdDocument document = PsdImporter.parse(layeredPsd(0, false, 8, 3), "sample");
		assertEquals(4, document.width(), "canvas width");
		assertEquals(3, document.height(), "canvas height");
		assertEquals(1, document.layers().size(), "one pixel layer is decoded");
		PsdImporter.PsdLayer layer = document.layers().get(0);
		assertEquals("前景", layer.name(), "luni name overrides the Pascal fallback");
		assertEquals(1, layer.left(), "layer left");
		assertEquals(1, layer.top(), "layer top");
		assertEquals(2, layer.width(), "layer width");
		assertEquals(2, layer.height(), "layer height");
		assertTrue(layer.visible(), "visible flag is decoded");
		assertEquals(0x640A325A, layer.image().getRGB(0, 0), "RGBA channels and layer opacity are combined");
	}

	private static void readsRleLayerAndVisibility() throws Exception {
		PsdImporter.PsdLayer layer = PsdImporter.parse(layeredPsd(1, true, 8, 3), "sample").layers().get(0);
		assertTrue(!layer.visible(), "hidden layer flag is decoded");
		assertEquals(0x401E466E, layer.image().getRGB(0, 1), "PackBits rows decode independently");
	}

	private static void readsZipPredictionLayer() throws Exception {
		PsdImporter.PsdLayer layer = PsdImporter.parse(layeredPsd(3, false, 8, 3), "sample").layers().get(0);
		assertEquals(0x00285078, layer.image().getRGB(1, 1), "ZIP prediction restores byte deltas per row");
	}

	private static void fallsBackToCompositeImage() throws Exception {
		PsdImporter.PsdDocument document = PsdImporter.parse(compositePsd(), "flattened");
		assertEquals(1, document.layers().size(), "flattened PSD becomes one layer");
		PsdImporter.PsdLayer layer = document.layers().get(0);
		assertEquals("flattened", layer.name(), "fallback layer uses the source base name");
		assertEquals(0xFF010305, layer.image().getRGB(0, 0), "composite channel planes decode");
		assertEquals(0x80020406, layer.image().getRGB(1, 0), "composite alpha plane decodes");
	}

	private static void surfacesUnsupportedLayerAndColorFeatures() throws Exception {
		PsdImporter.PsdDocument document = PsdImporter.parse(
			layeredPsd(0, false, 8, 3, true), "complex");
		String warnings = String.join("\n", document.warnings());
		for (String expected : new String[] {"ICC", "混合模式", "剪贴", "蒙版", "图层效果", "图层组"})
			assertTrue(warnings.contains(expected), "unsupported " + expected + " is reported instead of silently ignored");
	}

	private static void extractsWithoutOverwritingEarlierOutput() throws Exception {
		Path directory = Files.createTempDirectory("openspine-psd-import-");
		try {
			Path source = directory.resolve("hero.psd");
			Files.write(source, layeredPsd(0, false, 8, 3));
			PsdImporter.ImportResult first = PsdImporter.extract(source);
			PsdImporter.ImportResult second = PsdImporter.extract(source);
			assertEquals(directory.resolve("hero_layers"), first.directory(), "first extraction directory");
			assertEquals(directory.resolve("hero_layers_2"), second.directory(), "existing output is not overwritten");
			Path png = first.layers().get(0).png();
			assertTrue(Files.isRegularFile(png), "layer PNG is committed");
			assertEquals(2, ImageIO.read(png.toFile()).getWidth(), "extracted PNG is readable");
			assertEquals("前景", first.layers().get(0).name(), "logical layer name is preserved");
			assertTrue(first.layers().get(0).sourceIndex() == 0 && !first.hiddenLayersIgnored(),
				"extraction retains the original layer index and filtering mode");
			PsdImporter.ImportResult reserved = PsdImporter.extract(source, new CancellationToken(),
				java.util.List.of("前景"));
			assertEquals("前景_2", reserved.layers().get(0).regionName(),
				"existing atlas region names are reserved during extraction");
			PsdImporter.cleanup(reserved);
			assertTrue(!Files.exists(reserved.directory()), "completed extraction directories can be cleaned safely");
			CancellationToken cancelled = new CancellationToken();
			cancelled.cancel();
			try { PsdImporter.extract(source, cancelled); }
			catch (java.util.concurrent.CancellationException expected) {
				assertTrue(!Files.exists(directory.resolve("hero_layers_3")),
					"pre-cancelled extraction creates no visible output directory");
				return;
			}
			throw new AssertionError("pre-cancelled PSD extraction must stop before writing files");
		} finally { deleteTree(directory); }
	}

	private static void rejectsUnsupportedDepthAndColorMode() throws Exception {
		assertThrows(() -> PsdImporter.parse(layeredPsd(0, false, 16, 3), "bad"),
			"16-bit PSD is rejected");
		assertThrows(() -> PsdImporter.parse(layeredPsd(0, false, 8, 4), "bad"),
			"CMYK PSD is rejected");
	}

	private static byte[] layeredPsd(int compression, boolean hidden, int depth, int colorMode) throws Exception {
		return layeredPsd(compression, hidden, depth, colorMode, false);
	}

	private static byte[] layeredPsd(int compression, boolean hidden, int depth, int colorMode,
		boolean complex) throws Exception {
		byte[][] encoded = new byte[4][];
		for (int index = 0; index < encoded.length; index++) encoded[index] = channelData(CHANNELS[index], compression);

		ByteArrayOutputStream extraBytes = new ByteArrayOutputStream();
		DataOutputStream extra = new DataOutputStream(extraBytes);
		extra.writeInt(complex ? 4 : 0); // Layer mask.
		if (complex) extra.writeInt(0);
		extra.writeInt(0); // Blending ranges.
		extra.writeByte(5);
		extra.writeBytes("Layer");
		extra.writeShort(0); // Pascal name padding to four bytes.
		extra.writeBytes("8BIM");
		extra.writeBytes("luni");
		byte[] unicode = "前景".getBytes(java.nio.charset.StandardCharsets.UTF_16BE);
		extra.writeInt(4 + unicode.length);
		extra.writeInt(2);
		extra.write(unicode);
		if (complex) {
			for (String key : new String[] {"lfx2", "lsct"}) {
				extra.writeBytes("8BIM");
				extra.writeBytes(key);
				extra.writeInt("lsct".equals(key) ? 4 : 0);
				if ("lsct".equals(key)) extra.writeInt(0); // SectionDivider.OTHER, a normal pixel layer.
			}
		}
		extra.flush();

		ByteArrayOutputStream layerInfoBytes = new ByteArrayOutputStream();
		DataOutputStream layerInfo = new DataOutputStream(layerInfoBytes);
		layerInfo.writeShort(1);
		layerInfo.writeInt(1); // top
		layerInfo.writeInt(1); // left
		layerInfo.writeInt(3); // bottom
		layerInfo.writeInt(3); // right
		layerInfo.writeShort(4);
		int[] ids = {0, 1, 2, -1};
		for (int index = 0; index < ids.length; index++) {
			layerInfo.writeShort(ids[index]);
			layerInfo.writeInt(encoded[index].length);
		}
		layerInfo.writeBytes("8BIM");
		layerInfo.writeBytes(complex ? "mul " : "norm");
		layerInfo.writeByte(128);
		layerInfo.writeByte(complex ? 1 : 0);
		layerInfo.writeByte(hidden ? 2 : 0);
		layerInfo.writeByte(0);
		layerInfo.writeInt(extraBytes.size());
		layerInfo.write(extraBytes.toByteArray());
		for (byte[] channel : encoded) layerInfo.write(channel);
		layerInfo.flush();

		ByteArrayOutputStream layerMaskBytes = new ByteArrayOutputStream();
		DataOutputStream layerMask = new DataOutputStream(layerMaskBytes);
		layerMask.writeInt(layerInfoBytes.size());
		layerMask.write(layerInfoBytes.toByteArray());
		layerMask.writeInt(0); // Global layer mask.
		layerMask.flush();

		ByteArrayOutputStream resultBytes = new ByteArrayOutputStream();
		DataOutputStream result = new DataOutputStream(resultBytes);
		byte[] resources = complex ? iccResource() : new byte[0];
		writeHeader(result, 4, 3, 4, depth, colorMode, resources);
		result.writeInt(layerMaskBytes.size());
		result.write(layerMaskBytes.toByteArray());
		result.writeShort(0); // Composite image data, unused when layers exist.
		result.write(new byte[4 * 3 * 4]);
		result.flush();
		return resultBytes.toByteArray();
	}

	private static byte[] compositePsd() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(bytes);
		writeHeader(output, 2, 1, 4, 8, 3);
		output.writeInt(0); // No layer/mask section.
		output.writeShort(0);
		output.write(new byte[] {1, 2, 3, 4, 5, 6, (byte)255, (byte)128});
		output.flush();
		return bytes.toByteArray();
	}

	private static void writeHeader(DataOutputStream output, int width, int height,
		int channels, int depth, int colorMode) throws Exception {
		writeHeader(output, width, height, channels, depth, colorMode, new byte[0]);
	}

	private static void writeHeader(DataOutputStream output, int width, int height,
		int channels, int depth, int colorMode, byte[] resources) throws Exception {
		output.writeBytes("8BPS");
		output.writeShort(1);
		output.write(new byte[6]);
		output.writeShort(channels);
		output.writeInt(height);
		output.writeInt(width);
		output.writeShort(depth);
		output.writeShort(colorMode);
		output.writeInt(0); // Color mode data.
		output.writeInt(resources.length);
		output.write(resources);
	}

	private static byte[] iccResource() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(bytes);
		output.writeBytes("8BIM");
		output.writeShort(0x040f);
		output.writeByte(0);
		output.writeByte(0);
		output.writeInt(0);
		output.flush();
		return bytes.toByteArray();
	}

	private static byte[] channelData(byte[] raw, int compression) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(bytes);
		output.writeShort(compression);
		if (compression == 0) output.write(raw);
		else if (compression == 1) {
			output.writeShort(3);
			output.writeShort(3);
			output.writeByte(1);
			output.write(raw, 0, 2);
			output.writeByte(1);
			output.write(raw, 2, 2);
		} else if (compression == 2 || compression == 3) {
			byte[] prepared = raw.clone();
			if (compression == 3) {
				for (int row = 0; row < 2; row++) {
					int start = row * 2;
					prepared[start + 1] = (byte)((raw[start + 1] & 0xff) - (raw[start] & 0xff));
				}
			}
			Deflater deflater = new Deflater();
			try {
				deflater.setInput(prepared);
				deflater.finish();
				byte[] compressed = new byte[64];
				int count = deflater.deflate(compressed);
				output.write(compressed, 0, count);
			} finally { deflater.end(); }
		} else throw new IllegalArgumentException("unsupported test compression");
		output.flush();
		return bytes.toByteArray();
	}

	private static void assertThrows(ThrowingRunnable action, String message) throws Exception {
		try { action.run(); }
		catch (java.io.IOException expected) { return; }
		throw new AssertionError(message);
	}

	private static void deleteTree(Path directory) throws Exception {
		if (directory == null || !Files.exists(directory)) return;
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	@FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
	private PsdImporterTest() {}
}
