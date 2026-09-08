package com.openspine.ui;

import com.esotericsoftware.spine.ConstraintData.ScaleYMode;

/** Headless checks for semantic Physics constraint draft comparison. */
public final class PhysicsConstraintDialogTest {
	public static void main(String[] args) {
		PhysicsConstraintDialog.Draft baseline = PhysicsConstraintDialog.physicsDraft(
			" swing ", " tip ", true, " 体积 ", "0.2", ".3", "4e-1", "0.5", ".6", "5000", "60",
			".5", "100", ".85", "2.5", "-3", "6", ".8",
			true, false, true, false, true, false, true, true);
		assertEquals(baseline, PhysicsConstraintDialog.physicsDraft(
			"swing", "tip", true, "体积", ".20", "0.30", ".4", "5e-1", "0.600", "5e3", "6e1",
			"0.50", "1e2", "0.850", "2.50", "-3.0", "6.0", "0.80",
			true, false, true, false, true, false, true, true),
			"equivalent text and numeric notation should not enable Apply");
		assertNotEquals(baseline, PhysicsConstraintDialog.physicsDraft(
			"swing", "tip", true, "体积", ".2", ".3", ".4", ".5", ".6", "5000", "60",
			".5", "100", ".85", "2.5", "-3", "6", ".8",
			false, false, true, false, true, false, true, true),
			"global scope changes are detected");
		assertNotEquals(baseline, PhysicsConstraintDialog.physicsDraft(
			"swing", "tip", true, "体积", ".2", ".3", ".4", ".5", ".6", "5000", "60",
			".5", "100", ".85", "2.5", "-3", "6", ".8",
			true, false, true, false, true, false, true, false),
			"skin activation changes are detected");
		for (ScaleYMode mode : ScaleYMode.values()) {
			String label = IkConstraintDialog.scaleYLabel(mode);
			assertEquals(mode, IkConstraintDialog.scaleYMode(label),
				"Physics Scale Y labels round-trip for " + mode);
		}
		System.out.println("ALL PHYSICS CONSTRAINT DIALOG TESTS PASSED");
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNotEquals(Object expected, Object actual, String message) {
		if (expected.equals(actual)) throw new AssertionError(message + ": both were " + actual);
	}

	private PhysicsConstraintDialogTest() {}
}
