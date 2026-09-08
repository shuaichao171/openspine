package com.openspine.ui;

import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.SlotData;

/** Headless checks for semantic Path constraint dialog state. */
public final class PathConstraintDialogTest {
	public static void main(String[] args) {
		PathConstraintDialog.Draft baseline = PathConstraintDialog.pathDraft(
			" follow ", " arm， hand ", " path-slot ", true, true, " 百分比 ", " 比例 ", " 链缩放 ",
			"15.0", "2e1", "-3", "1.0", ".5", "0.25");
		assertEquals(baseline, PathConstraintDialog.pathDraft(
			"follow", "arm,hand", "path-slot", true, true, "百分比", "比例", "链缩放",
			"15", "20.000", "-3e0", "1e0", "0.50", ".25"),
			"equivalent text and numeric notation should not enable Apply");
		assertNotEquals(baseline, PathConstraintDialog.pathDraft(
			"follow", "hand,arm", "path-slot", true, true, "百分比", "比例", "链缩放",
			"15", "20", "-3", "1", ".5", ".25"),
			"constrained bone order is meaningful");
		assertNotEquals(baseline, PathConstraintDialog.pathDraft(
			"follow", "arm,hand", "path-slot", true, true, "固定", "比例", "链缩放",
			"15", "20", "-3", "1", ".5", ".25"),
			"mode changes are detected");
		assertNotEquals(baseline, PathConstraintDialog.pathDraft(
			"follow", "arm,hand", "path-slot", true, false, "百分比", "比例", "链缩放",
			"15", "20", "-3", "1", ".5", ".25"),
			"skin activation changes are detected");

		for (PathConstraintData.PositionMode mode : PathConstraintData.PositionMode.values())
			assertEquals(mode, PathConstraintDialog.positionMode(PathConstraintDialog.positionModeLabel(mode)),
				"Position mode labels round-trip for " + mode);
		for (PathConstraintData.SpacingMode mode : PathConstraintData.SpacingMode.values())
			assertEquals(mode, PathConstraintDialog.spacingMode(PathConstraintDialog.spacingModeLabel(mode)),
				"Spacing mode labels round-trip for " + mode);
		for (PathConstraintData.RotateMode mode : PathConstraintData.RotateMode.values())
			assertEquals(mode, PathConstraintDialog.rotateMode(PathConstraintDialog.rotateModeLabel(mode)),
				"Rotate mode labels round-trip for " + mode);
		assertTrue(PathConstraintDialog.positionMode("invalid") == null
			&& PathConstraintDialog.spacingMode("invalid") == null
			&& PathConstraintDialog.rotateMode("invalid") == null,
			"unknown Path mode labels are rejected");

		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		SlotData childSlot = new SlotData(0, "path-slot", child);
		assertTrue(PathConstraintDialog.isSlotInBoneSubtree(childSlot, root),
			"a descendant target slot is recognized as a dependency cycle");
		assertTrue(!PathConstraintDialog.isSlotInBoneSubtree(childSlot, new BoneData(2, "other", null)),
			"an unrelated target slot is accepted");
		System.out.println("ALL PATH CONSTRAINT DIALOG TESTS PASSED");
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNotEquals(Object first, Object second, String message) {
		if (first.equals(second)) throw new AssertionError(message + ": both were " + first);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private PathConstraintDialogTest() {}
}
