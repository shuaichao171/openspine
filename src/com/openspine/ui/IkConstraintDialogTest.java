package com.openspine.ui;

import java.util.List;

import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Headless regression tests for semantic IK setup dialog state. */
public final class IkConstraintDialogTest {
	public static void main(String[] args) {
		IkConstraintDialog.Draft baseline = IkConstraintDialog.ikDraft(" arm-ik ", " upper ",
			IkConstraintDialog.NO_SECOND_BONE, " target ", true, " 等比 ", "1.0", "0e0",
			" 正向 ", false, true, true);
		assertEquals(baseline, IkConstraintDialog.ikDraft("arm-ik", "upper",
			IkConstraintDialog.NO_SECOND_BONE, "target", true, "等比", "1e0", "0.000",
			"正向", false, true, true), "equivalent text and numeric notation is unchanged");
		assertNotEquals(baseline, IkConstraintDialog.ikDraft("arm-ik", "upper",
			IkConstraintDialog.NO_SECOND_BONE, "target", false, "等比", "1", "0",
			"正向", false, true, true), "skin-required changes are retained");
		assertNotEquals(baseline, IkConstraintDialog.ikDraft("arm-ik", "upper",
			IkConstraintDialog.NO_SECOND_BONE, "target", true, "等比", "1", "0",
			"正向", true, true, true), "compression changes are retained");
		assertNotEquals(baseline, IkConstraintDialog.ikDraft("arm-ik", "upper",
			IkConstraintDialog.NO_SECOND_BONE, "target", true, "等比", "1", "0",
			"正向", false, false, true), "stretch changes are retained");
		assertNotEquals(baseline, IkConstraintDialog.ikDraft("arm-ik", "upper",
			IkConstraintDialog.NO_SECOND_BONE, "target", true, "等比", "1", "0",
			"正向", false, true, false), "skin activation changes are retained");

		String[] labels = IkConstraintDialog.scaleYLabels();
		assertEquals(3, labels.length, "all Spine 4.3 Scale Y modes are exposed");
		for (ScaleYMode mode : ScaleYMode.values()) {
			String label = IkConstraintDialog.scaleYLabel(mode);
			assertEquals(mode, IkConstraintDialog.scaleYMode(label),
				"Scale Y labels round-trip for " + mode);
		}
		assertTrue(IkConstraintDialog.scaleYMode("无效") == null, "unknown Scale Y labels are rejected");
		undoesConstraintCreationAndMultiSkinSetupEdits();
		System.out.println("ALL IK CONSTRAINT DIALOG TESTS PASSED");
	}

	private static void undoesConstraintCreationAndMultiSkinSetupEdits() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		child.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root, child);
		Skin defaultSkin = new Skin("default");
		Skin alternateSkin = new Skin("alternate");
		data.getSkins().add(defaultSkin, alternateSkin);
		data.setDefaultSkin(defaultSkin);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		SkeletonEditor editor = new SkeletonEditor(doc);
		EditHistory history = new EditHistory(doc);

		SkeletonEditor.IkConstraintSetup original = new SkeletonEditor.IkConstraintSetup("arm", child, null,
			root, true, ScaleYMode.none, 1f, 0f, 1, false, false);
		IkConstraintData created = editor.addIkConstraint(original, defaultSkin, true);
		ConstraintHistory.recordApplied(history, editor, "创建 IK 约束", null, created, null, original,
			List.of(), editor.constraintActivationSkins(created), ikOperations(editor));
		assertTrue(history.undo() && data.getConstraints().isEmpty(),
			"undo removes a newly created constraint and its activation");
		assertTrue(history.redo() && data.getConstraints().size == 1,
			"redo recreates a constraint after its object identity changed");
		IkConstraintData current = (IkConstraintData)data.getConstraints().first();
		assertTrue(defaultSkin.getConstraints().contains(current, true),
			"redo restores the created constraint skin activation");

		assertTrue(editor.setConstraintActivationSkins(current, List.of(defaultSkin, alternateSkin)),
			"test setup activates the constraint in both skins");
		List<Skin> beforeActivations = editor.constraintActivationSkins(current);
		SkeletonEditor.IkConstraintSetup edited = new SkeletonEditor.IkConstraintSetup("arm-edited", child, null,
			root, false, ScaleYMode.none, 0.5f, 0f, -1, false, true);
		IkConstraintData editedConstraint = editor.updateIkConstraint(current, edited, defaultSkin, false);
		ConstraintHistory.recordApplied(history, editor, "编辑 IK 约束", current, editedConstraint, original, edited,
			beforeActivations, editor.constraintActivationSkins(editedConstraint), ikOperations(editor));
		assertTrue(history.undo(), "constraint setup edit can be undone");
		IkConstraintData restored = (IkConstraintData)data.getConstraints().first();
		assertTrue("arm".equals(restored.getName()) && restored.getSkinRequired(),
			"undo restores the previous constraint setup");
		assertTrue(defaultSkin.getConstraints().contains(restored, true)
			&& alternateSkin.getConstraints().contains(restored, true),
			"undo restores activation in every skin");
		assertTrue(history.redo(), "constraint setup edit can be redone");
		IkConstraintData redone = (IkConstraintData)data.getConstraints().first();
		assertTrue("arm-edited".equals(redone.getName()) && !redone.getSkinRequired()
			&& defaultSkin.getConstraints().isEmpty() && alternateSkin.getConstraints().isEmpty(),
			"redo restores the edited setup and cleared activations");

		assertTrue(history.undo(), "a repeated undo restores the original constraint again");
		assertTrue(history.undo() && data.getConstraints().isEmpty(),
			"undo creation resolves the latest replacement object after a later setup edit");
		assertTrue(history.redo() && data.getConstraints().size == 1,
			"redo creation works after the full multi-entry undo chain");
		assertTrue(history.redo(), "redo reapplies the later setup edit after recreating the constraint");
		IkConstraintData replayed = (IkConstraintData)data.getConstraints().first();
		assertTrue("arm-edited".equals(replayed.getName()) && !replayed.getSkinRequired(),
			"the full redo chain follows one logical constraint across every replacement object");

		PhysicsConstraintData blocker = new PhysicsConstraintData("blocker");
		blocker.setBone(root);
		blocker.setStep(1f / 60f);
		blocker.getSetupPose().setMassInverse(1f);
		data.getConstraints().add(blocker);
		editor.rebuildSkeleton();
		SkeletonEditor.IkConstraintSetup deletedSetup = SkeletonEditor.ikConstraintSetup(replayed);
		List<Skin> deletedActivations = editor.constraintActivationSkins(replayed);
		int deletedIndex = editor.constraintIndex(replayed);
		assertTrue(editor.deleteIkConstraint(replayed), "the edited constraint can be deleted");
		ConstraintHistory.recordDeleted(history, editor, "删除 IK 约束", replayed, deletedSetup,
			deletedActivations, deletedIndex, ikOperations(editor));
		assertTrue(history.undo(), "constraint deletion can be undone");
		IkConstraintData undeleted = data.findConstraint("arm-edited", IkConstraintData.class);
		assertTrue(undeleted != null && editor.constraintIndex(undeleted) == 0
			&& data.getConstraints().get(1) == blocker,
			"undo restores the deleted constraint at its unified index");
		assertTrue(history.undo(), "the edit before deletion remains undoable");
		IkConstraintData restoredOriginal = data.findConstraint("arm", IkConstraintData.class);
		assertTrue(restoredOriginal != null && defaultSkin.getConstraints().contains(restoredOriginal, true)
			&& alternateSkin.getConstraints().contains(restoredOriginal, true),
			"undo across deletion restores the previous setup and every activation");
		assertTrue(history.redo() && history.redo(), "edit and deletion can be replayed together");
		assertTrue(data.findConstraint("arm-edited", IkConstraintData.class) == null
			&& data.getConstraints().size == 1 && data.getConstraints().first() == blocker,
			"redo deletion resolves the latest replacement object without deleting its neighbor");
	}

	private static ConstraintHistory.Operations<IkConstraintData, SkeletonEditor.IkConstraintSetup> ikOperations(
		SkeletonEditor editor) {
		return new ConstraintHistory.Operations<>() {
			@Override public IkConstraintData add(SkeletonEditor.IkConstraintSetup setup) {
				return editor.addIkConstraint(setup, null, false);
			}
			@Override public IkConstraintData update(IkConstraintData current,
				SkeletonEditor.IkConstraintSetup setup) {
				return editor.updateIkConstraint(current, setup, null, false);
			}
			@Override public boolean delete(IkConstraintData current) {
				return editor.deleteIkConstraint(current);
			}
		};
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNotEquals(Object first, Object second, String message) {
		if (first.equals(second)) throw new AssertionError(message);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private IkConstraintDialogTest() {}
}
