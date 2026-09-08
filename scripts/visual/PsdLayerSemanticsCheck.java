package com.openspine.io;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Compares real normal-blend PSD layers against the embedded Photoshop composite; no UI or OpenGL.
 * Fixtures are fetched separately from https://github.com/psd-tools/psd-tools/tree/main/tests/psd_files
 * (group.psd and hidden-groups.psd); this script pins their SHA-256 values below.
 * Compile: javac -encoding UTF-8 -source 17 -target 17 -cp "bin;lib/*" -d build/round11-psd-check
 *   scripts/visual/PsdLayerSemanticsCheck.java
 * Run: java -cp "bin;build/round11-psd-check;lib/*" com.openspine.io.PsdLayerSemanticsCheck
 *   artifacts/spine-pro-review-11/psd build/round11-psd-check/group.psd build/round11-psd-check/hidden-groups.psd
 */
public final class PsdLayerSemanticsCheck {
	private static final Map<String, String> HASHES = Map.of(
		"group.psd", "1aaf572b69f0b0fa7c04b7b3a14c97e310a0c7bbdc06e733c9dd0d0cdcf7df1c",
		"hidden-groups.psd", "57ebb0195f61309c9c73fddec6a9c44ce931ccb3f165f8a78c73861a1fe3862d");

	public static void main(String[] args) throws Exception {
		if (args.length < 2) throw new IllegalArgumentException("Usage: output-directory fixture.psd [fixture.psd]");
		Path output = Files.createDirectories(Path.of(args[0]));
		for (int index = 1; index < args.length; index++) {
			Path source = Path.of(args[index]); byte[] bytes = Files.readAllBytes(source);
			String fileName = source.getFileName().toString();
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			require(hash.equals(HASHES.get(fileName)), "Unknown or changed reference fixture: " + fileName + " SHA-256=" + hash);
			PsdImporter.PsdDocument document = PsdImporter.parse(bytes, "fixture");
			BufferedImage reference = embeddedComposite(bytes);
			BufferedImage actual = compose(document.layers(), document.width(), document.height(), false);
			BufferedImage reversed = compose(document.layers(), document.width(), document.height(), true);
			String stem = fileName.substring(0, fileName.length() - 4);
			ImageIO.write(reference, "png", output.resolve(stem + "-reference.png").toFile());
			ImageIO.write(actual, "png", output.resolve(stem + "-corrected.png").toFile());
			ImageIO.write(reversed, "png", output.resolve(stem + "-reversed-order.png").toFile());
			long actualDifferences = differentPixels(reference, actual);
			long reversedDifferences = differentPixels(reference, reversed);
			System.out.println("SOURCE https://github.com/psd-tools/psd-tools/blob/main/tests/psd_files/" + fileName);
			System.out.println("SHA-256 " + hash);
			System.out.println("RGBA differences: corrected=" + actualDifferences + ", reversed order=" + reversedDifferences);
			for (PsdImporter.PsdLayer layer : document.layers())
				System.out.println("  " + layer.name() + ": visible=" + layer.visible());
			require(actualDifferences == 0, "Decoded layer composition differs from embedded Photoshop composite: " + fileName);
			require(reversedDifferences > 0, "Fixture must detect the incorrect reversed stacking order");
		}
		System.out.println("PASS: every reference PSD matches the corrected layer composition pixel-for-pixel.");
	}

	private static BufferedImage embeddedComposite(byte[] bytes) throws Exception {
		ByteBuffer header = ByteBuffer.wrap(bytes); header.position(26);
		int colorBytes = header.getInt(); header.position(header.position() + colorBytes);
		int resourceBytes = header.getInt(); header.position(header.position() + resourceBytes);
		int layerSectionOffset = header.position(), layerBytes = header.getInt();
		int compositeOffset = header.position() + layerBytes;
		ByteArrayOutputStream flat = new ByteArrayOutputStream();
		flat.write(bytes, 0, layerSectionOffset); flat.write(new byte[4]);
		flat.write(bytes, compositeOffset, bytes.length - compositeOffset);
		return PsdImporter.parse(flat.toByteArray(), "embedded-composite").layers().get(0).image();
	}

	private static BufferedImage compose(List<PsdImporter.PsdLayer> layers, int width, int height, boolean reversed) {
		BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = output.createGraphics();
		try {
			for (int index = 0; index < layers.size(); index++) {
				PsdImporter.PsdLayer layer = layers.get(reversed ? layers.size() - index - 1 : index);
				if (layer.visible()) graphics.drawImage(layer.image(), layer.left(), layer.top(), null);
			}
		} finally { graphics.dispose(); }
		return output;
	}

	private static long differentPixels(BufferedImage first, BufferedImage second) {
		require(first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight(), "Composite dimensions differ");
		long count = 0;
		for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++)
			if (first.getRGB(x, y) != second.getRGB(x, y)) count++;
		return count;
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
