package com.openspine.ui;

import java.util.List;

import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.TransformConstraintData;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonEditor.TransformFromPropertySetup;
import com.openspine.model.SkeletonEditor.TransformProperty;
import com.openspine.model.SkeletonEditor.TransformToPropertySetup;

/** Headless checks for semantic Transform constraint draft comparison. */
public final class TransformConstraintDialogTest {
	public static void main(String[] args) {
		List<TransformFromPropertySetup> mappings = complexMappings();
		List<TransformPropertyDialog.MappingDraft> flattened = TransformPropertyDialog.flattenProperties(mappings);
		assertTrue(flattened.size() == 3, "multi-target mappings flatten to independent editor rows");
		assertEquals(mappings, TransformPropertyDialog.groupMappings(flattened),
			"mapping editor rows preserve grouping, order, and all numeric values");
		assertThrows(() -> TransformPropertyDialog.groupMappings(List.of(
			new TransformPropertyDialog.MappingDraft(TransformProperty.X, 0f, TransformProperty.Y, 0f, 1f, 1f),
			new TransformPropertyDialog.MappingDraft(TransformProperty.X, 2f, TransformProperty.SCALE_X, 0f, 1f, 1f))),
			"one source cannot have conflicting source offsets");
		assertThrows(() -> TransformPropertyDialog.groupMappings(List.of(
			new TransformPropertyDialog.MappingDraft(TransformProperty.X, 0f, TransformProperty.Y, 0f, 1f, 1f),
			new TransformPropertyDialog.MappingDraft(TransformProperty.X, 0f, TransformProperty.Y, 2f, 3f, 4f))),
			"one source cannot map to the same target twice");

		TransformConstraintDialog.Draft baseline = TransformConstraintDialog.transformDraft(
			" follow ", " arm， hand ", " root ", true, true, true, false, true, false,
			"1.0", "0", "-2e0", "0.25", "0", "3", "1", "0.5", "0.75", "1", "0.25", "0");
		assertEquals(baseline, TransformConstraintDialog.transformDraft(
			"follow", "arm,hand", "root", true, true, true, false, true, false,
			"1e0", "0.000", "-2", ".25", "0e0", "3.0", "1.0", ".5", ".75", "1e0", ".25", "0"),
			"equivalent text and numeric notation should not enable Apply");
		assertNotEquals(baseline, TransformConstraintDialog.transformDraft(
			"follow", "hand,arm", "root", true, true, true, false, true, false,
			"1", "0", "-2", ".25", "0", "3", "1", ".5", ".75", "1", ".25", "0"),
			"constrained bone order is meaningful");
		assertNotEquals(baseline, TransformConstraintDialog.transformDraft(
			"follow", "arm,hand", "root", true, true, false, false, true, false,
			"1", "0", "-2", ".25", "0", "3", "1", ".5", ".75", "1", ".25", "0"),
			"mode changes are detected");
		assertNotEquals(baseline, TransformConstraintDialog.transformDraft(
			"follow", "arm,hand", "root", true, true, true, false, true, false,
			"1", "0", "-2", ".25", "0", "3", "1", ".5", ".7", "1", ".25", "0"),
			"mix changes are detected");
		assertNotEquals(baseline, TransformConstraintDialog.transformDraft(
			"follow", "arm,hand", "root", true, true, true, false, true, false,
			"1", "0", "-2", ".25", "0", "3", "1", ".5", ".75", "1", ".25", "0", mappings),
			"property mapping changes are detected by the main constraint draft");
		undoesMultiTargetPropertyMappingEdits();
		undoesConstraintOrderAcrossSetupReplacement();
		System.out.println("ALL TRANSFORM CONSTRAINT DIALOG TESTS PASSED");
	}

	private static void undoesMultiTargetPropertyMappingEdits() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		child.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root, child);
		Skin skin = new Skin("default");
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		SkeletonEditor editor = new SkeletonEditor(doc);
		EditHistory history = new EditHistory(doc);

		List<TransformFromPropertySetup> originalMappings = complexMappings();
		SkeletonEditor.TransformConstraintSetup original = transformSetup(
			"follow", child, root, true, 1f, originalMappings);
		TransformConstraintData current = editor.addTransformConstraint(original, skin, true);
		assertTrue(current != null, "test Transform constraint is created");
		List<Skin> beforeActivations = editor.constraintActivationSkins(current);

		List<TransformFromPropertySetup> editedMappings = List.of(
			new TransformFromPropertySetup(TransformProperty.Y, -7f, List.of(
				new TransformToPropertySetup(TransformProperty.X, 6f, 30f, 0.2f),
				new TransformToPropertySetup(TransformProperty.SHEAR_Y, -5f, 40f, 0.3f))),
			new TransformFromPropertySetup(TransformProperty.SCALE_Y, 8f, List.of(
				new TransformToPropertySetup(TransformProperty.SCALE_Y, 1f, 4f, 0.4f))));
		SkeletonEditor.TransformConstraintSetup edited = transformSetup(
			"follow-edited", child, root, false, 0.5f, editedMappings);
		TransformConstraintData replacement = editor.updateTransformConstraint(current, edited, skin, false);
		assertTrue(replacement != null && replacement != current, "Transform mapping edit replaces setup data");
		ConstraintHistory.recordApplied(history, editor, "编辑 Transform 约束", current, replacement,
			original, edited, beforeActivations, editor.constraintActivationSkins(replacement),
			transformOperations(editor));

		assertTrue(history.undo(), "Transform property mapping edit can be undone");
		TransformConstraintData restored = (TransformConstraintData)data.getConstraints().first();
		assertEquals(original, SkeletonEditor.transformConstraintSetup(restored),
			"undo restores every multi-target Transform mapping value");
		assertTrue(skin.getConstraints().contains(restored, true),
			"undo restores Transform skin activation");
		assertTrue(history.redo(), "Transform property mapping edit can be redone");
		TransformConstraintData redone = (TransformConstraintData)data.getConstraints().first();
		assertEquals(edited, SkeletonEditor.transformConstraintSetup(redone),
			"redo restores edited Transform mappings");
		assertTrue(skin.getConstraints().isEmpty(), "redo restores cleared Transform skin activation");
	}

	private static void undoesConstraintOrderAcrossSetupReplacement() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		child.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root, child);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		SkeletonEditor editor = new SkeletonEditor(doc);
		EditHistory history = new EditHistory(doc);

		SkeletonEditor.TransformConstraintSetup original = transformSetup(
			"ordered", child, root, false, 1f, complexMappings());
		TransformConstraintData target = editor.addTransformConstraint(original, null, false);
		IkConstraintData blocker = editor.addIkConstraint(new SkeletonEditor.IkConstraintSetup(
			"blocker", child, null, root, false, ScaleYMode.none, 1f, 0f, 1, false, false), null, false);
		assertTrue(target != null && blocker != null && editor.constraintIndex(target) == 0,
			"order history test starts with a valid cross-type pair");

		assertTrue(ConstraintOrderControls.move(history, editor, target, 1, "调整约束顺序"),
			"shared order control moves across a different constraint type");
		assertTrue(editor.constraintIndex(target) == 1, "the requested unified order is applied");
		SkeletonEditor.TransformConstraintSetup edited = transformSetup(
			"ordered-edited", child, root, false, 0.5f, List.of(
				new TransformFromPropertySetup(TransformProperty.X, 3f, List.of(
					new TransformToPropertySetup(TransformProperty.X, -4f, 10f, 0.75f),
					new TransformToPropertySetup(TransformProperty.SCALE_Y, 5f, 20f, 0.25f))),
				new TransformFromPropertySetup(TransformProperty.SHEAR_Y, -2f, List.of(
					new TransformToPropertySetup(TransformProperty.SHEAR_Y, 1f, 6f, 0.5f)))));
		TransformConstraintData replacement = editor.updateTransformConstraint(target, edited, null, false);
		ConstraintHistory.recordApplied(history, editor, "编辑 Transform 约束", target, replacement,
			original, edited, List.of(), List.of(), transformOperations(editor));

		assertTrue(history.undo(), "setup replacement after reordering can be undone");
		assertTrue(history.undo(), "the preceding constraint order move can be undone after object replacement");
		TransformConstraintData restored = data.findConstraint("ordered", TransformConstraintData.class);
		assertTrue(restored != null && editor.constraintIndex(restored) == 0
			&& data.getConstraints().get(1) == blocker,
			"order undo resolves the latest logical constraint object");
		assertTrue(history.redo(), "constraint order can be redone after replacement-aware undo");
		assertTrue(history.redo(), "setup replacement can be redone after order restoration");
		TransformConstraintData replayed = data.findConstraint("ordered-edited", TransformConstraintData.class);
		assertTrue(replayed != null && editor.constraintIndex(replayed) == 1
			&& SkeletonEditor.transformConstraintSetup(replayed).equals(edited),
			"combined redo restores both unified order and advanced property mappings");
	}

	private static SkeletonEditor.TransformConstraintSetup transformSetup(String name, BoneData bone,
		BoneData source, boolean skinRequired, float mix, List<TransformFromPropertySetup> mappings) {
		return new SkeletonEditor.TransformConstraintSetup(name, List.of(bone), source, skinRequired,
			false, false, false, false, 0f, 0f, 0f, 0f, 0f, 0f,
			0f, mix, 0f, 0f, mix, mix, mappings);
	}

	private static List<TransformFromPropertySetup> complexMappings() {
		return List.of(
			new TransformFromPropertySetup(TransformProperty.ROTATE, 2f, List.of(
				new TransformToPropertySetup(TransformProperty.X, -3f, 12f, 0.5f),
				new TransformToPropertySetup(TransformProperty.SHEAR_Y, 4f, 20f, -0.25f))),
			new TransformFromPropertySetup(TransformProperty.SCALE_X, 1f, List.of(
				new TransformToPropertySetup(TransformProperty.SCALE_Y, 0f, 2f, 1.5f))));
	}

	private static ConstraintHistory.Operations<TransformConstraintData,
		SkeletonEditor.TransformConstraintSetup> transformOperations(SkeletonEditor editor) {
		return new ConstraintHistory.Operations<>() {
			@Override public TransformConstraintData add(SkeletonEditor.TransformConstraintSetup setup) {
				return editor.addTransformConstraint(setup, null, false);
			}
			@Override public TransformConstraintData update(TransformConstraintData current,
				SkeletonEditor.TransformConstraintSetup setup) {
				return editor.updateTransformConstraint(current, setup, null, false);
			}
			@Override public boolean delete(TransformConstraintData current) {
				return editor.deleteTransformConstraint(current);
			}
		};
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNotEquals(Object expected, Object actual, String message) {
		if (expected.equals(actual)) throw new AssertionError(message + ": both were " + actual);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private TransformConstraintDialogTest() {}
}
