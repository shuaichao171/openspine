package com.openspine.model;

import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;

/** Headless regression tests for pose undo/redo behavior. */
public final class EditHistoryTest {
	public static void main(String[] args) {
		rollsBackPendingGroupWithoutChangingTheHistory();
		keepsPoseHistoryOutsideDocumentDirtyState();
		tracksExactFinitePoseChanges();
		tracksBoneInheritPoseChanges();
		tracksDocumentSavepointsAcrossUndoRedo();
		preservesPreexistingDirtyState();
		keepsNewDocumentsUnsavedAfterUndo();
		tracksProjectSettingEdits();
		clearsSilentlyForCoordinatedRefreshes();
		keepsStacksStableWhenActionsFail();
		System.out.println("ALL EDIT HISTORY TESTS PASSED");
	}

	private static void rollsBackPendingGroupWithoutChangingTheHistory() {
		SkeletonDocument doc = createDocument();
		var root = doc.skeleton.getRootBone();
		var child = doc.skeleton.findBone("child");
		doc.setSelectedBones(java.util.List.of(child, root));
		doc.poseMode = false;
		doc.playing = true;
		EditHistory history = new EditHistory(doc);
		history.begin("canceled selection");
		doc.updateBonePose(root, 9f, 0f, 12f, 1f, 1f, 0f, 0f);
		doc.updateBonePose(child, 18f, 0f, 22f, 1f, 1f, 0f, 0f);
		assertTrue(history.rollbackPending(), "pending group can be rolled back");
		assertNear(0f, root.getPose().getX(), "cancel restores root pose");
		assertNear(5f, child.getPose().getX(), "cancel restores child pose");
		assertTrue(!doc.poseMode && doc.playing, "cancel restores the previous playback mode");
		assertTrue(doc.getSelectedBone() == root && doc.getSelectedBones().size() == 2,
			"cancel preserves the complete selection with its promoted primary");
		assertTrue(!history.canUndo() && !history.canRedo() && !history.commit(),
			"cancel does not add or remove committed history entries");
	}

	private static void keepsStacksStableWhenActionsFail() {
		SkeletonDocument undoDoc = createDocument();
		EditHistory undoHistory = new EditHistory(undoDoc);
		int[] undoNotifications = {0};
		undoHistory.setOnChanged(() -> undoNotifications[0]++);
		undoHistory.record("失败撤销", () -> { throw new IllegalStateException("undo failed"); }, () -> {});
		boolean undoDirty = undoDoc.dirty;
		int notificationsBeforeFailure = undoNotifications[0];
		assertFails(undoHistory::undo, "failing undo propagates its error");
		assertTrue(undoHistory.canUndo() && !undoHistory.canRedo(),
			"failing undo leaves the entry on the undo stack");
		assertEquals("失败撤销", undoHistory.undoLabel(), "failing undo preserves its label");
		assertTrue(undoDoc.dirty == undoDirty, "failing undo preserves document dirty state");
		assertTrue(undoNotifications[0] == notificationsBeforeFailure,
			"failing undo does not publish a false history change");

		SkeletonDocument redoDoc = createDocument();
		EditHistory redoHistory = new EditHistory(redoDoc);
		int[] value = {2};
		redoHistory.record("失败重做", () -> value[0] = 1,
			() -> { throw new IllegalStateException("redo failed"); });
		assertTrue(redoHistory.undo() && value[0] == 1, "redo failure setup can be undone");
		boolean redoDirty = redoDoc.dirty;
		assertFails(redoHistory::redo, "failing redo propagates its error");
		assertTrue(!redoHistory.canUndo() && redoHistory.canRedo(),
			"failing redo leaves the entry on the redo stack");
		assertEquals("失败重做", redoHistory.redoLabel(), "failing redo preserves its label");
		assertTrue(redoDoc.dirty == redoDirty && value[0] == 1,
			"failing redo preserves document state");
	}

	private static void assertFails(Runnable action, String message) {
		try {
			action.run();
			throw new AssertionError(message + ": expected an exception");
		} catch (IllegalStateException expected) {
			// Expected action failure.
		}
	}

	private static void tracksBoneInheritPoseChanges() {
		SkeletonDocument doc = createDocument();
		EditHistory history = new EditHistory(doc);
		var child = doc.skeleton.findBone("child");
		doc.setSelectedBone(child);
		history.begin("修改骨骼继承模式");
		assertTrue(doc.updateBoneInherit(child, Inherit.noScaleOrReflection),
			"runtime inherit mode can be previewed");
		assertTrue(history.commit(), "inherit preview creates one pose history entry");
		assertTrue(history.undo(), "inherit preview can be undone");
		assertTrue(child.getPose().getInherit() == Inherit.normal, "undo restores the previous inherit mode");
		assertTrue(history.redo(), "inherit preview can be redone");
		assertTrue(child.getPose().getInherit() == Inherit.noScaleOrReflection,
			"redo restores the previewed inherit mode");
		assertTrue(!doc.dirty, "inherit preview history does not dirty export data");
	}

	private static void clearsSilentlyForCoordinatedRefreshes() {
		SkeletonDocument doc = createDocument();
		EditHistory history = new EditHistory(doc);
		int[] value = {2};
		history.record("结构前编辑", () -> value[0] = 1, () -> value[0] = 2);
		int[] notifications = {0};
		history.setOnChanged(() -> notifications[0]++);
		history.clearSilently();
		assertTrue(!history.canUndo() && !history.canRedo(), "silent clear removes stale history");
		assertTrue(notifications[0] == 0, "silent clear leaves refresh notification to the coordinator");
		assertTrue(doc.dirty, "silent clear preserves the document dirty state");
	}

	private static void keepsPoseHistoryOutsideDocumentDirtyState() {
		SkeletonDocument doc = createDocument();
		EditHistory history = new EditHistory(doc);
		int[] notifications = {0};
		history.setOnChanged(() -> notifications[0]++);

		var child = doc.skeleton.findBone("child");
		doc.setSelectedBone(child);
		history.begin("移动子骨骼");
		child.getPose().setX(42f);
		child.getPose().setRotation(15f);
		child.getPose().setShearX(11f);
		child.getPose().setShearY(-7f);
		assertTrue(history.commit(), "changed pose should create an entry");
		assertTrue(!doc.dirty, "runtime pose edit must not mark export data dirty");
		assertTrue(history.canUndo() && !history.canRedo(), "commit state");
		assertEquals("移动子骨骼", history.undoLabel(), "undo label");

		assertTrue(history.undo(), "undo should succeed");
		assertTrue(!doc.dirty, "pose undo must not mark export data dirty");
		assertNear(5f, doc.skeleton.findBone("child").getPose().getX(), "undo x");
		assertNear(0f, doc.skeleton.findBone("child").getPose().getRotation(), "undo rotation");
		assertNear(0f, doc.skeleton.findBone("child").getPose().getShearX(), "undo shear x");
		assertNear(0f, doc.skeleton.findBone("child").getPose().getShearY(), "undo shear y");
		assertTrue(!history.canUndo() && history.canRedo(), "undo state");

		assertTrue(history.redo(), "redo should succeed");
		assertTrue(!doc.dirty, "pose redo must not mark export data dirty");
		assertNear(42f, doc.skeleton.findBone("child").getPose().getX(), "redo x");
		assertNear(15f, doc.skeleton.findBone("child").getPose().getRotation(), "redo rotation");
		assertNear(11f, doc.skeleton.findBone("child").getPose().getShearX(), "redo shear x");
		assertNear(-7f, doc.skeleton.findBone("child").getPose().getShearY(), "redo shear y");
		assertEquals("child", doc.getSelectedBone().getData().getName(), "selection restored");

		history.begin("no-op");
		assertTrue(!history.commit(), "unchanged pose should not create an entry");
		assertEquals("移动子骨骼", history.undoLabel(), "no-op keeps prior entry");

		// Structural edits replace runtime bone objects, so callers must clear pose history.
		doc.data.getBones().add(new BoneData(2, "added", doc.data.findBone("child")));
		doc.skeleton = new Skeleton(doc.data);
		history.clear();
		assertTrue(!history.canUndo() && !history.canRedo(), "clear after structural change");
		assertTrue(!doc.dirty, "clearing pose-only history keeps a clean document clean");
		assertTrue(notifications[0] >= 3, "history listener notifications");
	}

	private static void tracksExactFinitePoseChanges() {
		SkeletonDocument doc = createDocument();
		EditHistory history = new EditHistory(doc);
		var child = doc.skeleton.findBone("child");
		float before = child.getPose().getX();
		float after = Math.nextUp(before);
		history.begin("精确移动骨骼");
		child.getPose().setX(after);
		assertTrue(history.commit(), "a one-ULP pose change should create an undo entry");
		assertTrue(history.undo(), "one-ULP pose change can be undone");
		assertBits(before, child.getPose().getX(), "one-ULP pose undo value");
		assertTrue(history.redo(), "one-ULP pose change can be redone");
		assertBits(after, child.getPose().getX(), "one-ULP pose redo value");
	}

	private static void tracksDocumentSavepointsAcrossUndoRedo() {
		SkeletonDocument doc = createDocument();
		EditHistory history = new EditHistory(doc);
		int[] value = {1};

		value[0] = 2;
		history.record("编辑关键帧", () -> value[0] = 1, () -> value[0] = 2);
		assertTrue(doc.dirty && !history.isAtSavedState(), "document edit leaves the loaded savepoint");
		assertEquals("编辑关键帧", history.undoLabel(), "document history label");

		history.markSaved();
		assertTrue(!doc.dirty && history.isAtSavedState(), "save records the current history position");
		assertTrue(history.undo() && value[0] == 1, "undo from saved edited state");
		assertTrue(doc.dirty && !history.isAtSavedState(), "undo away from saved state is dirty");
		assertTrue(history.redo() && value[0] == 2, "redo to saved edited state");
		assertTrue(!doc.dirty && history.isAtSavedState(), "redo back to savepoint is clean");

		assertTrue(history.undo(), "undo before moving the savepoint");
		history.markSaved();
		assertTrue(!doc.dirty && value[0] == 1, "saving an undone state moves the savepoint");
		assertTrue(history.redo() && value[0] == 2, "redo after saving undone state");
		assertTrue(doc.dirty, "redo away from the new savepoint is dirty");
		assertTrue(history.undo() && !doc.dirty, "undo returns to the new savepoint");

		value[0] = 3;
		history.record("分支编辑", () -> value[0] = 1, () -> value[0] = 3);
		assertTrue(doc.dirty && !history.canRedo(), "branch edit discards redo and remains dirty");
		history.clear();
		assertTrue(doc.dirty && !history.isAtSavedState(), "clearing dirty structural history invalidates savepoint");
		history.markSaved();
		assertTrue(!doc.dirty && history.isAtSavedState(), "saving after a structural clear establishes a savepoint");
	}

	private static void preservesPreexistingDirtyState() {
		SkeletonDocument doc = createDocument();
		doc.markDirty();
		EditHistory history = new EditHistory(doc);
		int[] value = {2};
		history.record("局部编辑", () -> value[0] = 1, () -> value[0] = 2);
		assertTrue(history.undo() && value[0] == 1, "preexisting dirty edit can be undone");
		assertTrue(doc.dirty && !history.isAtSavedState(),
			"undoing a tracked edit cannot clear unrelated preexisting changes");
	}

	private static void keepsNewDocumentsUnsavedAfterUndo() {
		SkeletonDocument doc = createDocument();
		EditHistory history = new EditHistory(doc);
		history.markUnsaved();
		int[] value = {2};
		history.record("首次编辑", () -> value[0] = 1, () -> value[0] = 2);
		assertTrue(history.undo() && value[0] == 1, "the first new-document edit can be undone");
		assertTrue(doc.dirty && !history.isAtSavedState(),
			"undoing the first edit cannot make a never-saved document clean");
		assertTrue(history.redo() && doc.dirty,
			"redoing the first edit keeps a never-saved document dirty");
	}

	private static void tracksProjectSettingEdits() {
		SkeletonDocument doc = createDocument();
		EditHistory history = new EditHistory(doc);
		float previous = doc.data.getFps();
		assertTrue(doc.setProjectFps(24f), "project FPS edit succeeds");
		history.record("修改项目 FPS", () -> doc.setProjectFps(previous), () -> doc.setProjectFps(24f));
		assertTrue(doc.dirty && doc.data.getFps() == 24f, "FPS edit is dirty and applied");
		history.markSaved();
		assertTrue(history.undo(), "FPS undo succeeds");
		assertNear(previous, doc.data.getFps(), "FPS undo value");
		assertTrue(doc.dirty, "undoing FPS away from savepoint is dirty");
		assertTrue(history.redo(), "FPS redo succeeds");
		assertNear(24f, doc.data.getFps(), "FPS redo value");
		assertTrue(!doc.dirty, "redoing FPS to savepoint is clean");
	}

	private static SkeletonDocument createDocument() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		root.getSetupPose().setInherit(Inherit.normal);
		child.getSetupPose().setInherit(Inherit.normal);
		child.getSetupPose().setX(5f);
		data.getBones().add(root);
		data.getBones().add(child);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.setSelectedBone(doc.skeleton.getRootBone());
		return doc;
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertBits(float expected, float actual, String message) {
		if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private EditHistoryTest() {}
}
