package com.openspine.ui;

import java.util.List;

import com.openspine.model.SkeletonDocument;

/** Headless regression tests for structured Spine weight encoding. */
public final class MeshWeightDialogTest {
	public static void main(String[] args) {
		int[] bones = {2, 0, 1, 1, 2};
		float[] values = {1f, 2f, 2f, 3f, 4f, 6f, 5f, 6f, 3f};
		MeshWeightDialog.WeightDraft draft = MeshWeightDialog.decode(bones, values, 2, 3);
		assertEquals(2, draft.vertexCount(), "decoded vertex count");
		assertEquals(2, draft.influenceCount(0), "first vertex influence count");
		assertEquals(2, draft.influences(1).get(0).boneIndex(), "second vertex bone index");

		MeshWeightDialog.EncodedWeights roundTrip = MeshWeightDialog.encode(draft);
		assertIntArray(bones, roundTrip.bones(), "weight bone encoding round trip");
		assertFloatArray(new float[] {1f, 2f, 0.25f, 3f, 4f, 0.75f, 5f, 6f, 1f},
			roundTrip.vertices(), "encoding always normalizes each vertex to 100 percent");

		draft.replace(0, List.of(
			new MeshWeightDialog.Influence(0, 1f, 2f, 0.7f, true),
			new MeshWeightDialog.Influence(1, 3f, 4f, 6f, false)));
		draft.normalize(0);
		assertNear(0.7f, draft.influences(0).get(0).weight(),
			"normalization preserves a locked bone weight");
		assertNear(0.3f, draft.influences(0).get(1).weight(),
			"normalization assigns only the remaining weight to unlocked bones");
		MeshWeightDialog.EncodedWeights lockedEncoding = MeshWeightDialog.encode(draft);
		SkeletonDocument.MeshWeightLock savedLock = new SkeletonDocument.MeshWeightLock(0, 0);
		if (!lockedEncoding.locks().contains(savedLock))
			throw new AssertionError("structured weight encoding must return the persistent lock identity");
		MeshWeightDialog.WeightDraft restoredLocks = MeshWeightDialog.decode(lockedEncoding.bones(),
			lockedEncoding.vertices(), 2, 3, lockedEncoding.locks());
		if (!restoredLocks.influences(0).get(0).locked())
			throw new AssertionError("structured weight decoding must restore persistent locks");

		draft.replace(1, List.of(new MeshWeightDialog.Influence(1, -2f, 7f, 4f)));
		MeshWeightDialog.EncodedWeights replaced = MeshWeightDialog.encode(draft);
		assertEquals(1, replaced.bones()[3], "structured edit replaces influence count");
		assertEquals(1, replaced.bones()[4], "structured edit replaces bone index");
		assertNear(-2f, replaced.vertices()[6], "structured edit replaces local X");

		MeshWeightDialog.EncodedWeights bound = MeshWeightDialog.bindToBone(
			new float[] {-1f, -2f, 3f, 4f}, 2, 1, 3);
		assertIntArray(new int[] {1, 1, 1, 1}, bound.bones(), "unweighted binding bones");
		assertFloatArray(new float[] {-1f, -2f, 1f, 3f, 4f, 1f}, bound.vertices(),
			"unweighted binding values");

		assertThrows(() -> MeshWeightDialog.decode(new int[] {2, 0}, new float[] {1f, 2f, 1f}, 1, 3),
			"incomplete bone encoding is rejected");
		assertThrows(() -> MeshWeightDialog.decode(new int[] {1, 0, 9},
			new float[] {1f, 2f, 1f}, 1, 3), "trailing weight data is rejected");
		assertThrows(() -> draft.replace(0, List.of(
			new MeshWeightDialog.Influence(0, 1f, 2f, 1f),
			new MeshWeightDialog.Influence(0, 3f, 4f, 1f))), "duplicate bone influences are rejected");
		assertThrows(() -> draft.replace(0, List.of(
			new MeshWeightDialog.Influence(0, 1f, 2f, 0f))), "zero total weight is rejected");
		draft.replace(0, List.of(
			new MeshWeightDialog.Influence(0, 1f, 2f, 0.8f, true),
			new MeshWeightDialog.Influence(1, 3f, 4f, 0.4f, true)));
		assertThrows(() -> draft.normalize(0), "locked weights over 100 percent are rejected");
		draft.replace(0, List.of(
			new MeshWeightDialog.Influence(0, 1f, 2f, 0.4f, true),
			new MeshWeightDialog.Influence(1, 3f, 4f, 0.4f, true)));
		assertThrows(() -> draft.normalize(0),
			"fully locked weights must already total 100 percent");
		assertThrows(() -> MeshWeightDialog.bindToBone(new float[] {1f, 2f}, 2, 0, 3),
			"binding requires one coordinate pair per vertex");
		System.out.println("ALL MESH WEIGHT DIALOG TESTS PASSED");
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertIntArray(int[] expected, int[] actual, String message) {
		if (!java.util.Arrays.equals(expected, actual))
			throw new AssertionError(message + ": expected " + java.util.Arrays.toString(expected)
				+ ", got " + java.util.Arrays.toString(actual));
	}

	private static void assertFloatArray(float[] expected, float[] actual, String message) {
		if (expected.length != actual.length)
			throw new AssertionError(message + ": length expected " + expected.length + ", got " + actual.length);
		for (int index = 0; index < expected.length; index++)
			assertNear(expected[index], actual[index], message + "[" + index + "]");
	}

	private static void assertNear(float expected, float actual, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.00001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private MeshWeightDialogTest() {}
}
