package com.openspine.ui;

import java.util.List;

import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.openspine.model.AnimationEditor;
import com.openspine.model.EditHistory;
import com.openspine.model.PermanentSkinCompositionTest;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Tests the permanent-save command used by the dialog, including existing shared history. */
public final class SkinCombinationDialogTest {
	public static void main(String[] args) {
		compositionPreservesExistingPoseHistory();
		compositionPreservesExistingAndLaterTimelineHistory();
		renameAndLaterTrackEditsPreserveCompositionHistory();
		System.out.println("ALL SKIN COMBINATION DIALOG TESTS PASSED");
	}

	private static void compositionPreservesExistingPoseHistory() {
		SkeletonDocument doc = PermanentSkinCompositionTest.document(false);
		try {
			EditHistory history = new EditHistory(doc);
			doc.beginPoseEdit();
			history.begin("move root");
			doc.skeleton.getRootBone().getPose().setX(31f);
			check(history.commit(), "existing pose gesture recorded");
			var oldSkeleton = doc.skeleton;
			Skin saved = SkinCombinationDialog.savePermanent(doc, history, List.of("base", "overlay"), "saved");
			check(doc.skeleton != oldSkeleton, "save exercised skeleton rebuild");
			near(31f, doc.skeleton.getRootBone().getPose().getX(), "save retains live pose");
			check(history.undo() && doc.data.findSkin("saved") == null, "undo removes composition");
			near(31f, doc.skeleton.getRootBone().getPose().getX(), "undo composition retains preceding pose");
			check(history.undo(), "preceding pose can be undone across rebuild");
			near(0f, doc.skeleton.getRootBone().getPose().getX(), "preceding pose undo resolves current skeleton");
			check(history.redo(), "preceding pose can be redone");
			near(31f, doc.skeleton.getRootBone().getPose().getX(), "pose redo resolves current skeleton");
			check(history.redo() && doc.data.findSkin("saved") == saved, "composition redo restores stable skin identity");
			near(31f, doc.skeleton.getRootBone().getPose().getX(), "composition redo preserves redone pose");
		} finally { doc.dispose(); }
	}

	private static void compositionPreservesExistingAndLaterTimelineHistory() {
		SkeletonDocument doc = PermanentSkinCompositionTest.document(false);
		try {
			EditHistory history = new EditHistory(doc); history.markSaved();
			AnimationEditor editor = new AnimationEditor(doc);
			Animation animation = doc.data.findAnimation("motion");
			MeshAttachment source = (MeshAttachment)doc.data.findSkin("base").getAttachment(0, "image");
			var before = editor.snapshotDeformTimeline(animation, 0, source);
			float[] offsets = new float[8]; offsets[0] = 23f;
			editor.addDeformKeyframe(animation, 0, source, 0.8f, offsets);
			var after = editor.snapshotDeformTimeline(animation, 0, source);
			history.record("source deform", () -> editor.restoreTimeline(before), () -> editor.restoreTimeline(after));
			Skin saved = SkinCombinationDialog.savePermanent(doc, history, List.of("base", "overlay"), "saved");
			MeshAttachment copy = (MeshAttachment)saved.getAttachment(0, "image");
			near(23f, editor.findDeformTimeline(animation, 0, copy).getVertices()[1][0], "save copies preceding Deform edit");
			var copyBefore = editor.snapshotDeformTimeline(animation, 0, copy);
			offsets[0] = 41f;
			editor.addDeformKeyframe(animation, 0, copy, 0.8f, offsets);
			var copyAfter = editor.snapshotDeformTimeline(animation, 0, copy);
			history.record("saved deform", () -> editor.restoreTimeline(copyBefore), () -> editor.restoreTimeline(copyAfter));
			check(history.undo(), "later composed-skin track edit undo");
			near(23f, editor.findDeformTimeline(animation, 0, copy).getVertices()[1][0], "later undo retains snapshot value");
			check(history.undo() && doc.data.findSkin("saved") == null, "composition undo after track object replacement");
			check(history.undo(), "preceding source track edit undo");
			near(12f, editor.findDeformTimeline(animation, 0, source).getVertices()[1][0], "original source key restored");
			check(!doc.dirty && history.isAtSavedState(), "all document edits undo to savepoint");
			check(history.redo(), "source edit redo");
			near(23f, editor.findDeformTimeline(animation, 0, source).getVertices()[1][0], "source redo retained");
			check(history.redo() && doc.data.findSkin("saved") == saved, "composition redo with preceding track replacement");
			near(23f, editor.findDeformTimeline(animation, 0, source).getVertices()[1][0], "composition redo keeps source edit");
			near(23f, editor.findDeformTimeline(animation, 0, copy).getVertices()[1][0], "composition redo restores frozen copied key");
			check(history.redo(), "later copied track edit redo");
			near(41f, editor.findDeformTimeline(animation, 0, copy).getVertices()[1][0], "later copied key restored");
			check(doc.dirty, "redone persistent changes dirty the project");
			history.markSaved();
			check(history.undo() && doc.dirty, "undo away from saved state is dirty");
			check(history.redo() && !doc.dirty, "redo returns to saved state");
		} finally { doc.dispose(); }
	}

	private static void renameAndLaterTrackEditsPreserveCompositionHistory() {
		SkeletonDocument doc = PermanentSkinCompositionTest.document(false);
		try {
			EditHistory history = new EditHistory(doc);
			history.markSaved();
			Skin saved = SkinCombinationDialog.savePermanent(doc, history, List.of("base", "overlay"), "saved");
			SkeletonEditor.SkinStructureEdit rename = new SkeletonEditor(doc).renameSkinEdit(saved, "renamed");
			AttachmentPanel.recordSkinStructureChange(history, "重命名皮肤", rename);
			Skin renamed = rename.skin();
			check(renamed == doc.data.findSkin("renamed") && doc.data.findSkin("saved") == null,
				"rename installs the retained after-instance");
			check(doc.getPreviewSkinNames().equals(List.of("renamed")), "rename keeps composed skin selected");
			MeshAttachment copy = (MeshAttachment)renamed.getAttachment(0, "image");
			check(copy == saved.getAttachment(0, "image"), "rename does not replace attachment identity");
			Animation animation = doc.data.findAnimation("motion");
			AnimationEditor editor = new AnimationEditor(doc);
			var before = editor.snapshotDeformTimeline(animation, 0, copy);
			float[] offsets = new float[8]; offsets[0] = 45f;
			editor.addDeformKeyframe(animation, 0, copy, 0.8f, offsets);
			var after = editor.snapshotDeformTimeline(animation, 0, copy);
			history.record("renamed skin deform", () -> editor.restoreTimeline(before), () -> editor.restoreTimeline(after));
			check(history.undo(), "track edit after rename undo");
			near(12f, editor.findDeformTimeline(animation, 0, copy).getVertices()[1][0], "post-rename track undo restores snapshot value");
			check(history.undo() && doc.data.findSkin("saved") == saved && doc.data.findSkin("renamed") == null,
				"rename undo restores the exact original skin needed by composition history");
			check(history.undo() && doc.data.findSkin("saved") == null && !history.canUndo(), "composition can undo after rename and track history");
			check(!doc.dirty && history.isAtSavedState(), "entire chain returns to clean saved state");
			check(history.redo() && doc.data.findSkin("saved") == saved, "composition redo reinstalls original instance");
			check(history.redo() && doc.data.findSkin("renamed") == renamed, "rename redo reinstalls same after-instance");
			check(history.redo(), "later attachment track edit can redo after both skin restores");
			near(45f, editor.findDeformTimeline(animation, 0, copy).getVertices()[1][0], "later renamed-skin key survives full redo");
			check(doc.getPreviewSkinNames().equals(List.of("renamed")) && doc.dirty, "full redo preserves editing skin and dirty state");
		} finally { doc.dispose(); }
	}

	private static void near(float expected, float actual, String message) {
		check(Math.abs(expected - actual) < 0.0001f, message + ": expected " + expected + ", actual " + actual);
	}
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private SkinCombinationDialogTest() {}
}
