package com.openspine.ui;

import java.util.List;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.openspine.io.SkeletonExporter;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Skin history can outlive replacement constraint identities in earlier history entries. */
public final class SkinConstraintHistoryTest {
	public static void main(String[] args) {
		restoresRecreatedConstraintAfterSkinRedo(false);
		restoresRecreatedConstraintAfterSkinRedo(true);
		System.out.println("ALL SKIN CONSTRAINT HISTORY TESTS PASSED");
	}

	private static void restoresRecreatedConstraintAfterSkinRedo(boolean duplicate) {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null), arm = new BoneData(1, "arm", root);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		arm.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root, arm);
		Skin base = new Skin("default"), source = new Skin("source");
		data.getSkins().add(base, source); data.setDefaultSkin(base);
		SkeletonDocument doc = new SkeletonDocument(); doc.data = data; doc.skeleton = new Skeleton(data);
		SkeletonEditor editor = new SkeletonEditor(doc); EditHistory history = new EditHistory(doc);
		var operations = new ConstraintHistory.Operations<IkConstraintData, SkeletonEditor.IkConstraintSetup>() {
			public IkConstraintData add(SkeletonEditor.IkConstraintSetup setup) { return editor.addIkConstraint(setup, null, false); }
			public IkConstraintData update(IkConstraintData current, SkeletonEditor.IkConstraintSetup setup) {
				return editor.updateIkConstraint(current, setup, null, false);
			}
			public boolean delete(IkConstraintData current) { return editor.deleteIkConstraint(current); }
		};
		try {
			var setup = new SkeletonEditor.IkConstraintSetup("aim", arm, null, root, true,
				ScaleYMode.none, 1f, 0f, 1, false, false);
			IkConstraintData original = editor.addIkConstraint(setup, source, true);
			ConstraintHistory.recordApplied(history, editor, "Create constraint", null, original, null, setup,
				List.of(), editor.constraintActivationSkins(original), operations);
			SkeletonEditor.SkinStructureEdit edit = duplicate ? editor.duplicateSkinEdit(source, "target")
				: editor.renameSkinEdit(source, "target");
			check(edit != null, "skin command starts");
			history.record("Edit skin", edit::undo, edit::redo);
			Skin retained = edit.skin();
			String expected = SkeletonExporter.toJson(doc);
			history.markSaved();
			for (int round = 0; round < 3; round++) {
				check(history.undo() && history.undo(), "both operations undo");
				check(data.getConstraints().isEmpty(), "creation undo removes the constraint");
				check(history.redo(), "constraint creation redoes");
				ConstraintData<?, ?> recreated = data.getConstraints().first();
				check(recreated != original, "history really recreated a different constraint object");
				check(history.redo(), "skin edit redoes");
				check(data.findSkin("target") == retained, "skin history retains its stable skin object");
				check(retained.getConstraints().size == 1 && retained.getConstraints().first() == recreated,
					"skin redo must activate the current constraint, not its removed predecessor: duplicate=" + duplicate);
				check(expected.equals(SkeletonExporter.toJson(doc)), "redo remains exportable and restores all authored data");
				check(!doc.dirty, "redo returns to the saved document state");
			}
			check(history.undo(), "remove the retained skin before the invalid-reference check");
			IkConstraintData current = (IkConstraintData)data.getConstraints().first();
			editor.setConstraintActivationSkins(current, List.of());
			check(editor.deleteIkConstraint(current), "remove a dependency outside the pending skin edit");
			String beforeRejected = SkeletonExporter.toJson(doc);
			try { history.redo(); throw new AssertionError("a missing constraint must reject the skin restore"); }
			catch (IllegalStateException expectedFailure) {}
			check(data.findSkin("target") == null && data.findSkin("source") == source,
				"failed dependency resolution preserves the active skin list");
			check(beforeRejected.equals(SkeletonExporter.toJson(doc)) && history.canRedo(),
				"failed dependency resolution changes neither author data nor the redo stack");
		} finally { doc.dispose(); }
	}
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private SkinConstraintHistoryTest() {}
}
