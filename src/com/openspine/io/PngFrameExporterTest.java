package com.openspine.io;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import javax.imageio.ImageIO;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.SkeletonAttachment;
import com.openspine.io.PngFrameExporter.Bounds;
import com.openspine.io.PngFrameExporter.OutputSize;
import com.openspine.io.PngFrameExporter.Settings;

/** Headless regression tests for PNG sizing, alpha conversion, and atomic encoding. */
public final class PngFrameExporterTest {
	public static void main(String[] args) throws Exception {
		GdxNativesLoader.load();
		testsOutputSizing();
		testsSourceAlphaMode();
		testsAttachmentSuppression();
		testsStraightAlphaConversion();
		testsAtomicPngEncoding();
		System.out.println("ALL PNG FRAME EXPORTER TESTS PASSED");
	}

	private static void testsSourceAlphaMode() {
		SkeletonRenderer straight = PngFrameExporter.newRenderer(false);
		assertTrue(!straight.getPremultipliedAlphaColors() && !straight.getPremultipliedAlphaBlendModes(),
			"straight atlas rendering uses straight colors and blend modes");
		SkeletonRenderer premultiplied = PngFrameExporter.newRenderer(true);
		assertTrue(premultiplied.getPremultipliedAlphaColors()
			&& premultiplied.getPremultipliedAlphaBlendModes(),
			"PMA atlas rendering configures both vertex colors and blend modes");
	}

	private static void testsAttachmentSuppression() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		SlotData clippingData = new SlotData(0, "clip", root);
		SlotData hiddenEndData = new SlotData(1, "hidden-end", root);
		hiddenEndData.setVisible(false);
		SlotData nestedData = new SlotData(2, "nested", root);
		data.getSlots().add(clippingData);
		data.getSlots().add(hiddenEndData);
		data.getSlots().add(nestedData);
		Skeleton skeleton = new Skeleton(data);
		ClippingAttachment clipping = new ClippingAttachment("clip");
		SkeletonAttachment hidden = new SkeletonAttachment("hidden");
		SkeletonAttachment nested = new SkeletonAttachment("nested");
		skeleton.getSlots().get(0).getAppliedPose().setAttachment(clipping);
		skeleton.getSlots().get(1).getAppliedPose().setAttachment(hidden);
		skeleton.getSlots().get(2).getAppliedPose().setAttachment(nested);

		Array<Slot> originalOrder = new Array<>(skeleton.getDrawOrder().getAppliedPose());
		Array<Slot> suppressedSlots = new Array<>();
		Array<Attachment> suppressedAttachments = new Array<>();
		PngFrameExporter.suppressNonExportableAttachments(skeleton, suppressedSlots, suppressedAttachments);
		assertEquals(originalOrder.size, skeleton.getDrawOrder().getAppliedPose().size,
			"attachment suppression preserves clipping end-slot positions");
		for (int index = 0; index < originalOrder.size; index++)
			assertTrue(originalOrder.get(index) == skeleton.getDrawOrder().getAppliedPose().get(index),
				"attachment suppression preserves draw order identity");
		assertTrue(skeleton.getSlots().get(0).getAppliedPose().getAttachment() == clipping,
			"visible clipping attachments remain active");
		assertTrue(skeleton.getSlots().get(1).getAppliedPose().getAttachment() == null,
			"invisible slot attachments are suppressed without removing their slot");
		assertTrue(skeleton.getSlots().get(2).getAppliedPose().getAttachment() == null,
			"nested skeleton attachments are outside current-frame PNG scope");
		PngFrameExporter.restoreSuppressedAttachments(suppressedSlots, suppressedAttachments);
		assertTrue(skeleton.getSlots().get(1).getAppliedPose().getAttachment() == hidden
			&& skeleton.getSlots().get(2).getAppliedPose().getAttachment() == nested,
			"suppressed attachments are restored exactly");
	}

	private static void testsOutputSizing() {
		Bounds bounds = new Bounds(-10f, 5f, 50.25f, 20f);
		Settings settings = new Settings(2f, 4, true, false);
		assertEquals(PngFrameExporter.DEFAULT_COMPRESSION, settings.compression(),
			"legacy PNG settings use the default compression level");
		OutputSize size = PngFrameExporter.outputSize(bounds, settings, 4096);
		assertEquals(109, size.width(), "scaled width rounds outward and includes both padding edges");
		assertEquals(48, size.height(), "scaled height includes both padding edges");
		OutputSize point = PngFrameExporter.outputSize(new Bounds(0f, 0f, 0f, 0f),
			new Settings(1f, 0, true, false), 4096);
		assertEquals(1, point.width(), "zero-width content still produces one pixel");
		assertEquals(1, point.height(), "zero-height content still produces one pixel");
		assertThrows(() -> new Settings(Float.NaN, 0, true, false), "non-finite scale is rejected");
		assertThrows(() -> new Settings(1f, -1, true, false), "negative padding is rejected");
		assertThrows(() -> new Settings(1f, 0, 10, true, false),
			"PNG compression above nine is rejected");
		assertThrows(() -> PngFrameExporter.outputSize(bounds, new Settings(64f, 0, true, false), 512),
			"GPU texture limits are enforced before allocating a framebuffer");
	}

	private static void testsStraightAlphaConversion() {
		Pixmap pixmap = new Pixmap(2, 1, Pixmap.Format.RGBA8888);
		try {
			pixmap.drawPixel(0, 0, 0x40201080);
			pixmap.drawPixel(1, 0, 0x60402000);
			PngFrameExporter.unpremultiply(pixmap);
			assertEquals(0x80402080, pixmap.getPixel(0, 0),
				"premultiplied RGB is converted to straight alpha without changing alpha");
			assertEquals(0x00000000, pixmap.getPixel(1, 0),
				"fully transparent pixels discard hidden RGB color");
			assertEquals(128, PngFrameExporter.unpremultiplyComponent(64, 128),
				"component conversion rounds to the nearest straight-alpha value");
		} finally {
			pixmap.dispose();
		}
	}

	private static void testsAtomicPngEncoding() throws Exception {
		Path directory = Files.createTempDirectory("openspine-png-export-").toRealPath();
		Pixmap pixmap = new Pixmap(1, 2, Pixmap.Format.RGBA8888);
		Pixmap compressible = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
		try {
			pixmap.drawPixel(0, 0, 0xff0000ff);
			pixmap.drawPixel(0, 1, 0x0000ffff);
			Path target = directory.resolve("frame.png");
			PngFrameExporter.writePixmapAtomic(target, pixmap, true);
			byte[] bytes = Files.readAllBytes(target);
			assertTrue(bytes.length > 8 && bytes[0] == (byte)0x89 && bytes[1] == 'P'
				&& bytes[2] == 'N' && bytes[3] == 'G', "atomic writer produces a PNG signature");
			BufferedImage image = ImageIO.read(target.toFile());
			assertEquals(1, image.getWidth(), "encoded PNG preserves width");
			assertEquals(2, image.getHeight(), "encoded PNG preserves height");
			assertEquals(0xff0000ff, image.getRGB(0, 0),
				"framebuffer rows are flipped into top-down PNG order");
			assertEquals(0xffff0000, image.getRGB(0, 1),
				"the opposite framebuffer row remains intact");
			long temporaryFiles;
			try (var files = Files.list(directory)) {
				temporaryFiles = files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count();
			}
			assertEquals(0L, temporaryFiles, "successful atomic writes leave no temporary files");
			compressible.setColor(0.2f, 0.4f, 0.8f, 1f);
			compressible.fill();
			Path uncompressed = directory.resolve("compression-0.png");
			Path compressed = directory.resolve("compression-9.png");
			PngFrameExporter.writePixmapAtomic(uncompressed, compressible, 0, false);
			PngFrameExporter.writePixmapAtomic(compressed, compressible, 9, false);
			assertTrue(Files.size(compressed) < Files.size(uncompressed),
				"higher PNG compression produces a smaller file for repeated pixels");
			assertThrows(() -> PngFrameExporter.writePixmapAtomic(
				directory.resolve("invalid-compression.png"), compressible, 10, false),
				"atomic PNG writer rejects compression above nine");
			assertThrows(() -> PngFrameExporter.writePixmapAtomic(directory.resolve("frame.jpg"), pixmap, true),
				"non-PNG targets are rejected");
		} finally {
			pixmap.dispose();
			compressible.dispose();
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	private static void assertThrows(ThrowingRunnable action, String message) {
		try {
			action.run();
		} catch (Exception expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private PngFrameExporterTest() {}
}
