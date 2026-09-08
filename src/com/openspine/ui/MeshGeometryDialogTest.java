package com.openspine.ui;

import java.util.Set;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor.MeshGeometryState;

/** Headless regression tests for mesh geometry and unsigned topology parsing. */
public final class MeshGeometryDialogTest {
	public static void main(String[] args) {
		float[] floats = MeshGeometryDialog.parseFloatArray("1, -2\n3.5; 4e-1", "顶点", false);
		assertNear(1f, floats[0], "first float");
		assertNear(-2f, floats[1], "negative float");
		assertNear(3.5f, floats[2], "decimal float");
		assertNear(0.4f, floats[3], "scientific float");

		int[] bones = MeshGeometryDialog.parseIntArray("2, 0, 1; 1, 2", "骨骼", false);
		assertEquals(5, bones.length, "bone encoding length");
		assertEquals(2, bones[4], "last bone index");
		assertTrue(MeshGeometryDialog.parseIntArray("  ", "骨骼", true) == null,
			"blank optional bones select unweighted mode");

		short[] indices = MeshGeometryDialog.parseIndexArray("0, 32768, 65535", "索引", false);
		assertEquals(0, Short.toUnsignedInt(indices[0]), "zero index");
		assertEquals(32768, Short.toUnsignedInt(indices[1]), "unsigned high index");
		assertEquals(65535, Short.toUnsignedInt(indices[2]), "unsigned maximum index");
		assertTrue(MeshGeometryDialog.parseIndexArray("", "边", true) == null,
			"blank optional edges remain absent");

		assertThrows(() -> MeshGeometryDialog.parseFloatArray("1, NaN", "顶点", false),
			"non-finite float is rejected");
		assertThrows(() -> MeshGeometryDialog.parseFloatArray("", "顶点", false),
			"required float data is rejected when blank");
		assertThrows(() -> MeshGeometryDialog.parseIntArray("1.5", "骨骼", false),
			"non-integer bone encoding is rejected");
		assertThrows(() -> MeshGeometryDialog.parseIndexArray("65536", "索引", false),
			"indices beyond unsigned short range are rejected");
		traceSamplingRespectsAtlasRotationAndTrimming();
		traceRejectsLockedAndDeformedGeometryBeforeChangingTheDraft();
		System.out.println("ALL MESH GEOMETRY DIALOG TESTS PASSED");
	}

	private static void traceSamplingRespectsAtlasRotationAndTrimming() {
		com.badlogic.gdx.graphics.g2d.TextureRegion region = new com.badlogic.gdx.graphics.g2d.TextureRegion() {
			@Override public float getU() { return 0.25f; }
			@Override public float getU2() { return 0.75f; }
			@Override public float getV() { return 0.25f; }
			@Override public float getV2() { return 0.75f; }
		};
		java.util.function.IntBinaryOperator pixels = (x, y) -> y * 40 + x * 10;
		int[] unrotated = MeshGeometryDialog.sampleAlpha(4, 4, region,
			new float[] {1f, 0f, 0f, 0f, 1f, 0f}, 4, 4, pixels);
		assertEquals(50, unrotated[5], "trimmed original coordinates locate the correct packed texel");
		assertEquals(100, unrotated[10], "the opposite packed corner maps without a vertical flip");
		for (int index : new int[] {0, 1, 2, 3, 4, 7, 8, 11, 12, 13, 14, 15})
			assertEquals(0, unrotated[index], "trimmed margins never sample neighboring atlas images");
		int[] rotated = MeshGeometryDialog.sampleAlpha(4, 4, region,
			new float[] {0f, -1f, 1f, 1f, 0f, 0f}, 4, 4, pixels);
		assertEquals(60, rotated[5], "90-degree rotation uses the correct first packed texel");
		assertEquals(100, rotated[6], "90-degree rotation swaps the original pixel axes");
		assertEquals(50, rotated[9], "90-degree rotation restores the original vertical axis");
		assertEquals(0, rotated[0], "rotated trimmed margins stay transparent");
		int[] flipped = MeshGeometryDialog.sampleAlpha(4, 4, region,
			new float[] {-1f, 0f, 1f, 0f, 1f, 0f}, 4, 4, pixels);
		assertEquals(60, flipped[5], "flipped source UVs retain their horizontal orientation");
	}

	private static void traceRejectsLockedAndDeformedGeometryBeforeChangingTheDraft() {
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = new SkeletonData();
		MeshAttachment mesh = new MeshAttachment("source", new Sequence(1, false));
		MeshGeometryState draft = new MeshGeometryState(Color.WHITE, "image", null,
			new float[] {0, 0, 1, 0, 1, 1, 0, 1}, new float[] {0, 0, 1, 0, 1, 1, 0, 1},
			new short[] {0, 1, 2, 2, 3, 0}, 8, new short[] {0, 1, 1, 2, 2, 3, 3, 0}, 1, 1);
		assertTrue(MeshGeometryDialog.traceBlockReason(doc, mesh, draft, Set.of()) == null,
			"an independent unweighted source permits tracing");
		assertTrue(MeshGeometryDialog.traceBlockReason(doc, mesh, draft,
			Set.of(new SkeletonDocument.MeshWeightLock(0, 0))).contains("锁定"),
			"rebuilding vertices cannot discard a locked influence, even after weights were cleared in a draft");
		Animation.DeformTimeline deform = new Animation.DeformTimeline(1, 0, 0, mesh);
		deform.setFrame(0, 0f, new float[8]);
		Animation animation = new Animation("deformed");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(deform);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		assertTrue(MeshGeometryDialog.traceBlockReason(doc, mesh, draft, Set.of()).contains("Deform"),
			"Deform blocks retracing even when the generated and existing vertex counts match");
		MeshAttachment linked = new MeshAttachment("linked", new Sequence(1, false));
		linked.setSourceMesh(mesh);
		Animation.DeformTimeline linkedDeform = new Animation.DeformTimeline(1, 0, 0, linked);
		linkedDeform.setFrame(0, 0f, new float[8]);
		timelines.set(0, linkedDeform);
		animation.setTimelines(timelines, new IntArray());
		assertTrue(MeshGeometryDialog.traceBlockReason(doc, mesh, draft, Set.of()).contains("Deform"),
			"independent Linked Mesh Deform timelines also protect their source's vertex correspondence");
		assertTrue(MeshGeometryDialog.traceBlockReason(doc, linked, draft, Set.of()).contains("Linked Mesh"),
			"a Linked Mesh cannot be traced independently");
		MeshAttachment unrelated = new MeshAttachment("unrelated", new Sequence(1, false));
		assertTrue(MeshGeometryDialog.traceBlockReason(doc, unrelated, draft, Set.of()) == null,
			"another mesh's Deform does not block an independent trace");
		assertEquals(4, draft.vertexCount(), "all protection checks leave the complete draft unchanged");
		assertEquals(8, linkedDeform.getVertices()[0].length, "protection checks never rewrite Deform frames");
		assertTrue(!doc.dirty, "rejected trace protection checks do not mark the project dirty");
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
			throw new AssertionError(message + ": expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private MeshGeometryDialogTest() {}
}
