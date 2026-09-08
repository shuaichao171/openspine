package com.openspine.model;

import java.util.List;

import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;

/** Headless regression tests for selection transforms and gesture atomicity. */
public final class BoneSelectionTransformTest {
	public static void main(String[] args) {
		movesDifferentParentSpacesByTheSameWorldDelta();
		transformsSelectedDescendantsOnlyOnce();
		transformsChildFirstSelectionThroughThePromotedPrimary();
		usesRelativeRotationScaleAndShearWithoutAccumulation();
		rejectsTheEntireNonFiniteBatch();
		rejectsSingularParentMovement();
		parentFieldsRemainEditableUnderCollapsedParentAxes();
		restoresEveryBoneAndTheSelectionInOneHistoryEntry();
		System.out.println("ALL BONE SELECTION TRANSFORM TESTS PASSED");
	}

	public static SkeletonDocument document() {
		SkeletonData data = new SkeletonData();
		BoneData root = bone(data, "root", null, 0f, 0f, 0f, 1f, 1f);
		BoneData a = bone(data, "a", root, 10f, 20f, 15f, 2f, 3f);
		BoneData rotated = bone(data, "rotated", root, -4f, 8f, 90f, 2f, 2f);
		bone(data, "b", rotated, 4f, 5f, -5f, 4f, 6f);
		bone(data, "child", a, 5f, 2f, 7f, 1f, 1f);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.skeleton.updateWorldTransform(Physics.pose);
		doc.setSelectedBones(List.of(doc.skeleton.findBone("a"), doc.skeleton.findBone("b")));
		return doc;
	}

	private static BoneData bone(SkeletonData data, String name, BoneData parent,
		float x, float y, float rotation, float scaleX, float scaleY) {
		BoneData bone = new BoneData(data.getBones().size, name, parent);
		bone.getSetupPose().setPosition(x, y);
		bone.getSetupPose().setRotation(rotation);
		bone.getSetupPose().setScale(scaleX, scaleY);
		bone.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(bone);
		return bone;
	}

	private static void movesDifferentParentSpacesByTheSameWorldDelta() {
		SkeletonDocument doc = document();
		Bone a = doc.skeleton.findBone("a"), b = doc.skeleton.findBone("b");
		float bx = b.getAppliedPose().getWorldX(), by = b.getAppliedPose().getWorldY();
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		assertTrue(edit.applyPrimaryPose(19f, 16f, 15f, 2f, 3f, 0f, 0f), "first group move changes the pose");
		near(19f, a.getAppliedPose().getWorldX(), "primary world x");
		near(bx + 9f, b.getAppliedPose().getWorldX(), "different parent world x");
		near(by - 4f, b.getAppliedPose().getWorldY(), "different parent world y");
		edit.applyPrimaryPose(21f, 17f, 15f, 2f, 3f, 0f, 0f);
		near(bx + 11f, b.getAppliedPose().getWorldX(), "subsequent samples use the gesture start");
		near(by - 3f, b.getAppliedPose().getWorldY(), "gesture deltas never accumulate");
		doc.setSelectedBone(a);
		assertTrue(!edit.applyPrimaryPose(30f, 17f, 15f, 2f, 3f, 0f, 0f), "stale selections cannot keep editing");
	}

	private static void transformsSelectedDescendantsOnlyOnce() {
		SkeletonDocument doc = document();
		Bone a = doc.skeleton.findBone("a"), child = doc.skeleton.findBone("child");
		doc.setSelectedBones(List.of(a, child));
		float childWorldX = child.getAppliedPose().getWorldX();
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		assertTrue(edit.transformedBones().equals(List.of(a)), "only selected subtree roots transform");
		edit.applyPrimaryPose(15f, 20f, 15f, 2f, 3f, 0f, 0f);
		near(5f, child.getPose().getX(), "a selected descendant retains its local translation");
		near(childWorldX + 5f, child.getAppliedPose().getWorldX(), "the descendant moves once through inheritance");
		assertTrue(edit.selectedBones().size() == 2 && doc.getSelectedBones().size() == 2,
			"descendants remain selected for manual keying");
	}

	private static void transformsChildFirstSelectionThroughThePromotedPrimary() {
		SkeletonDocument doc = document();
		Bone parent = doc.skeleton.findBone("a"), child = doc.skeleton.findBone("child");
		doc.setSelectedBones(List.of(child, parent));
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		assertTrue(edit.primary() == parent && edit.transformedBones().equals(List.of(parent)),
			"a child-first selection exposes the same directly transformed primary to every editor");
		float childWorldX = child.getAppliedPose().getWorldX();
		float childWorldY = child.getAppliedPose().getWorldY();
		assertTrue(edit.applyPrimaryPose(15f, 20f, 15f, 2f, 3f, 0f, 0f),
			"the promoted primary accepts its requested pose");
		near(15f, doc.getSelectedBone().getPose().getX(), "the inspector target receives the exact requested x");
		near(5f, child.getPose().getX(), "the selected child's local pose remains unchanged");
		near(childWorldX + 5f, child.getAppliedPose().getWorldX(), "the child inherits the translation once");
		near(childWorldY, child.getAppliedPose().getWorldY(), "the child has no unintended y movement");
		assertTrue(edit.selectedBones().equals(List.of(parent, child)), "manual keying retains the complete selection");
	}

	private static void usesRelativeRotationScaleAndShearWithoutAccumulation() {
		SkeletonDocument doc = document();
		Bone b = doc.skeleton.findBone("b");
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		edit.applyPrimaryPose(10f, 20f, 25f, -1f, 6f, 8f, -6f);
		near(5f, b.getPose().getRotation(), "rotation is a shared local delta");
		near(-2f, b.getPose().getScaleX(), "scale uses a relative ratio including reflection");
		near(12f, b.getPose().getScaleY(), "each original scale is preserved proportionally");
		near(8f, b.getPose().getShearX(), "shear x is relative");
		near(-6f, b.getPose().getShearY(), "shear y is relative");
		assertTrue(!edit.applyPrimaryPose(10f, 20f, 25f, -1f, 6f, 8f, -6f),
			"the same gesture sample is a no-op");
		edit.restore();
		near(-5f, b.getPose().getRotation(), "cancel restores every target");
		assertTrue(!edit.hasChanges(), "returning to the starting pose does not trigger Auto Key");
	}

	private static void rejectsTheEntireNonFiniteBatch() {
		SkeletonDocument doc = document();
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		rejected(() -> edit.applyPrimaryPose(10f, 20f, 15f, Float.MAX_VALUE, 3f, 0f, 0f));
		near(2f, doc.skeleton.findBone("a").getPose().getScaleX(), "overflow in a later target does not edit the first");
		near(4f, doc.skeleton.findBone("b").getPose().getScaleX(), "overflow target remains unchanged");
		rejected(() -> edit.applyPrimaryPose(Float.NaN, 20f, 15f, 2f, 3f, 0f, 0f));
		near(10f, doc.skeleton.findBone("a").getPose().getX(), "NaN is rejected before mutation");
	}

	private static void rejectsSingularParentMovement() {
		SkeletonDocument doc = document();
		doc.skeleton.findBone("rotated").getPose().setScaleX(0f);
		doc.skeleton.updateWorldTransform(Physics.pose);
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		rejected(() -> edit.applyPrimaryPose(15f, 20f, 15f, 2f, 3f, 0f, 0f));
		near(10f, doc.skeleton.findBone("a").getPose().getX(), "singular movement rejects the whole group");
	}

	private static void parentFieldsRemainEditableUnderCollapsedParentAxes() {
		for (boolean multi : new boolean[] {false, true}) {
			SkeletonDocument doc = document();
			Bone a = doc.skeleton.findBone("a"), sibling = doc.skeleton.findBone("rotated");
			doc.skeleton.getRootBone().getPose().setScale(0f, 2f);
			doc.skeleton.updateWorldTransform(Physics.pose);
			doc.setSelectedBones(multi ? List.of(a, sibling) : List.of(a));
			EditHistory history = new EditHistory(doc);
			history.begin("edit beneath collapsed parent");
			BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
			assertTrue(edit.applyPrimaryPose(15f, 17f, 15f, 2f, 3f, 0f, 0f),
				"parent-space fields do not need an invertible parent");
			near(15f, a.getPose().getX(), "collapsed X axis retains the requested authored position");
			near(17f, a.getPose().getY(), "visible Y axis remains editable");
			if (multi) {
				near(1f, sibling.getPose().getX(), "same-parent selection shares the authored X delta");
				near(5f, sibling.getPose().getY(), "same-parent selection shares the authored Y delta");
			}
			assertTrue(history.commit() && history.undo() && !history.canUndo(), "collapsed-parent edit is one reversible gesture");
			near(10f, a.getPose().getX(), "undo restores the primary X position");
			near(20f, a.getPose().getY(), "undo restores the primary Y position");
			assertTrue(history.redo(), "collapsed-parent edit redoes");
			near(17f, a.getPose().getY(), "redo restores the primary Y position");
		}
	}

	private static void restoresEveryBoneAndTheSelectionInOneHistoryEntry() {
		SkeletonDocument doc = document();
		List<Bone> selected = List.copyOf(doc.getSelectedBones());
		EditHistory history = new EditHistory(doc);
		history.begin("group transform");
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		edit.applyPrimaryPose(10f, 20f, 25f, 2f, 3f, 0f, 0f);
		assertTrue(history.commit(), "one gesture creates one entry");
		doc.setSelectedBone(doc.skeleton.getRootBone());
		assertTrue(history.undo() && !history.canUndo(), "one undo restores the complete gesture");
		near(15f, selected.get(0).getPose().getRotation(), "undo first target");
		near(-5f, selected.get(1).getPose().getRotation(), "undo second target");
		assertTrue(List.copyOf(doc.getSelectedBones()).equals(selected), "undo restores primary-first multi selection");
		assertTrue(history.redo(), "redo group");
		near(5f, selected.get(1).getPose().getRotation(), "redo second target");
		history.begin("canceled group");
		BoneSelectionTransform.capture(doc).applyPrimaryPose(10f, 20f, 50f, 2f, 3f, 0f, 0f);
		assertTrue(history.rollbackPending(), "Escape cancels a pending gesture");
		near(25f, selected.get(0).getPose().getRotation(), "cancel restores the prior committed pose");
		assertTrue(!history.commit(), "cancel never records another entry");
	}

	private static void rejected(Runnable action) {
		try { action.run(); throw new AssertionError("expected rejection"); }
		catch (IllegalArgumentException expected) {}
	}

	private static void near(float expected, float actual, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
