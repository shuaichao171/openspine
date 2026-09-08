package com.openspine.model;

import java.util.List;

import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Physics;

/** Regression coverage for authored parent/world translation and independent selection roots. */
public final class BoneCoordinateSpaceTest {
	public static void main(String[] args) {
		parentAxesFollowRotationScaleAndReflection();
		rootMovementAccountsForSkeletonTransform();
		worldPositionPreservesTheOtherWorldAxisAcrossParents();
		worldTranslationRemainsAtomicAndUndoableWithoutAnimation();
		translationCoordinatesDoNotDependOnRotationInheritance();
		localDeltasFollowThePrimaryBoneAcrossInheritanceAndSelection();
		localRootDeltasIncludeSkeletonScaleAndReflection();
		localDeltasRejectInvalidBatchesAtomically();
		worldDeltasSupportTinyAndHugeParentScale();
		worldDeltasRejectUnrepresentableLocalPositionsAtomically();
		System.out.println("ALL BONE COORDINATE SPACE TESTS PASSED");
	}

	private static void parentAxesFollowRotationScaleAndReflection() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		Bone b = doc.skeleton.findBone("b");
		near(90f, BoneCoordinateSpace.PARENT.axisRotation(doc.skeleton, b, false), "parent x follows parent rotation");
		near(180f, Math.abs(BoneCoordinateSpace.PARENT.axisRotation(doc.skeleton, b, true)), "parent y follows parent rotation");
		near(0f, BoneCoordinateSpace.WORLD.axisRotation(doc.skeleton, b, false), "world x stays horizontal");
		doc.skeleton.findBone("rotated").getPose().setScaleX(-2f);
		doc.skeleton.updateWorldTransform(Physics.pose);
		near(-90f, BoneCoordinateSpace.PARENT.axisRotation(doc.skeleton, b, false), "parent x preserves reflection");
		Vector2 position = BoneCoordinateSpace.WORLD.position(doc.skeleton, b, new Vector2());
		near(b.getAppliedPose().getWorldX(), position.x, "world x uses runtime parent transform");
		near(b.getAppliedPose().getWorldY(), position.y, "world y uses runtime parent transform");
	}

	private static void rootMovementAccountsForSkeletonTransform() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		doc.skeleton.setPosition(100f, -200f);
		doc.skeleton.setScale(-2f, 3f);
		Bone root = doc.skeleton.getRootBone();
		root.getPose().setPosition(5f, 7f);
		doc.skeleton.updateWorldTransform(Physics.pose);
		doc.setSelectedBone(root);
		Vector2 position = BoneCoordinateSpace.WORLD.position(doc.skeleton, root, new Vector2());
		near(90f, position.x, "root world x includes skeleton offset and scale");
		near(-179f, position.y, "root world y includes skeleton offset and scale");
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		assertTrue(edit.applyWorldDelta(12f, -15f), "root world movement applies");
		near(-1f, root.getPose().getX(), "root local x removes reflected skeleton scale");
		near(2f, root.getPose().getY(), "root local y removes skeleton scale");
		near(102f, root.getAppliedPose().getWorldX(), "root follows cursor world x exactly");
		near(-194f, root.getAppliedPose().getWorldY(), "root follows cursor world y exactly");
		assertTrue(!edit.applyWorldDelta(12f, -15f), "repeated pointer sample does not accumulate");
		edit.restore();
		near(5f, root.getPose().getX(), "cancel restores root local x");
	}

	private static void worldPositionPreservesTheOtherWorldAxisAcrossParents() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		Bone a = doc.skeleton.findBone("a"), b = doc.skeleton.findBone("b");
		doc.skeleton.findBone("rotated").getPose().setRotation(37f);
		doc.skeleton.updateWorldTransform(Physics.pose);
		doc.setSelectedBones(List.of(b, a));
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		Vector2 primary = edit.primaryPosition(BoneCoordinateSpace.WORLD);
		float ax = a.getAppliedPose().getWorldX(), ay = a.getAppliedPose().getWorldY();
		assertTrue(edit.applyWorldPosition(primary.x + 14f, primary.y), "world X field applies an absolute position");
		near(primary.x + 14f, b.getAppliedPose().getWorldX(), "primary reaches world x target");
		near(primary.y, b.getAppliedPose().getWorldY(), "world y is preserved under rotated parent");
		near(ax + 14f, a.getAppliedPose().getWorldX(), "other parent receives same world x displacement");
		near(ay, a.getAppliedPose().getWorldY(), "other parent world y stays fixed");
		assertTrue(Math.abs(b.getPose().getX() - 4f) > 1f && Math.abs(b.getPose().getY() - 5f) > 1f,
			"one world axis can require both local axes");
	}

	private static void worldTranslationRemainsAtomicAndUndoableWithoutAnimation() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		EditHistory history = new EditHistory(doc);
		history.begin("world move");
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		edit.applyWorldDelta(7f, 9f);
		assertTrue(history.commit() && history.undo() && !history.canUndo(), "world gesture has one undo without an animation");
		near(10f, doc.skeleton.findBone("a").getPose().getX(), "undo restores authored local data");
		doc.skeleton.findBone("rotated").getPose().setScale(0f);
		doc.skeleton.updateWorldTransform(Physics.pose);
		BoneSelectionTransform singular = BoneSelectionTransform.capture(doc);
		try { singular.applyWorldDelta(8f, 4f); throw new AssertionError("singular parent must reject move"); }
		catch (IllegalArgumentException expected) {}
		near(10f, doc.skeleton.findBone("a").getPose().getX(), "later invalid parent never partially moves earlier target");
	}

	private static void translationCoordinatesDoNotDependOnRotationInheritance() {
		for (BoneData.Inherit inherit : BoneData.Inherit.values()) {
			SkeletonDocument doc = BoneSelectionTransformTest.document();
			Bone bone = doc.skeleton.findBone("b");
			bone.getPose().setInherit(inherit);
			doc.skeleton.updateWorldTransform(Physics.pose);
			doc.setSelectedBone(bone);
			Vector2 before = BoneCoordinateSpace.WORLD.position(doc.skeleton, bone, new Vector2());
			BoneSelectionTransform.capture(doc).applyWorldDelta(8f, -3f);
			near(before.x + 8f, bone.getAppliedPose().getWorldX(), "translation parent remains valid with " + inherit);
			near(before.y - 3f, bone.getAppliedPose().getWorldY(), "translation y remains valid with " + inherit);
		}
	}

	private static void localDeltasFollowThePrimaryBoneAcrossInheritanceAndSelection() {
		for (BoneData.Inherit inherit : BoneData.Inherit.values()) for (boolean mirror : new boolean[] {false, true}) {
			SkeletonDocument doc = BoneSelectionTransformTest.document();
			Bone primary = doc.skeleton.findBone("b"), a = doc.skeleton.findBone("a"), child = doc.skeleton.findBone("child");
			Bone parent = primary.getParent();
			parent.getPose().setRotation(37f); parent.getPose().setScale(mirror ? -1.7f : 1.7f, 0.8f);
			primary.getPose().setRotation(-28f); primary.getPose().setScale(1.2f, -0.6f);
			primary.getPose().setShearX(9f); primary.getPose().setShearY(-12f); primary.getPose().setInherit(inherit);
			doc.skeleton.updateWorldTransform(Physics.pose);
			doc.setSelectedBones(List.of(primary, child, a));
			List<Bone> tracked = List.of(primary, a, child);
			Vector2[] before = tracked.stream().map(bone -> new Vector2(bone.getAppliedPose().getWorldX(), bone.getAppliedPose().getWorldY())).toArray(Vector2[]::new);
			float a00 = primary.getAppliedPose().getA(), a01 = primary.getAppliedPose().getB();
			float a10 = primary.getAppliedPose().getC(), a11 = primary.getAppliedPose().getD();
			near((float)Math.toDegrees(Math.atan2(a10, a00)), BoneCoordinateSpace.LOCAL.axisRotation(doc.skeleton, primary, false), "own x axis includes inheritance and reflection");
			near((float)Math.toDegrees(Math.atan2(a11, a01)), BoneCoordinateSpace.LOCAL.axisRotation(doc.skeleton, primary, true), "own y axis includes shear and scale");
			assertTrue(BoneCoordinateSpace.LOCAL.position(doc.skeleton, primary, new Vector2()).isZero(), "local inputs start with an incremental zero");
			BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
			assertTrue(edit.primaryPosition(BoneCoordinateSpace.LOCAL).isZero(), "gesture local input origin is zero");
			for (Vector2 delta : List.of(new Vector2(3f, -2f), new Vector2(-4f, 5f))) {
				assertTrue(edit.applyLocalDelta(delta.x, delta.y), "local delta applies for " + inherit);
				for (int i = 0; i < tracked.size(); i++) {
					near(before[i].x + a00 * delta.x + a01 * delta.y, tracked.get(i).getAppliedPose().getWorldX(), "shared local x displacement for " + inherit);
					near(before[i].y + a10 * delta.x + a11 * delta.y, tracked.get(i).getAppliedPose().getWorldY(), "shared local y displacement for " + inherit);
				}
				assertTrue(!edit.applyLocalDelta(delta.x, delta.y), "repeated local samples do not accumulate");
				near(5f, child.getPose().getX(), "selected child keeps its authored x");
				near(2f, child.getPose().getY(), "selected child keeps its authored y");
			}
			assertTrue(edit.restore(), "local delta can be canceled");
			for (int i = 0; i < tracked.size(); i++) {
				near(before[i].x, tracked.get(i).getAppliedPose().getWorldX(), "cancel restores every selected world x");
				near(before[i].y, tracked.get(i).getAppliedPose().getWorldY(), "cancel restores every selected world y");
			}
		}
	}

	private static void localRootDeltasIncludeSkeletonScaleAndReflection() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		doc.skeleton.setPosition(100f, -200f); doc.skeleton.setScale(-2f, 3f);
		Bone root = doc.skeleton.getRootBone();
		root.getPose().setRotation(90f); root.getPose().setScale(2f, 0.5f);
		doc.skeleton.updateWorldTransform(Physics.pose);
		doc.setSelectedBone(root);
		EditHistory history = new EditHistory(doc);
		history.begin("local root increment");
		assertTrue(BoneSelectionTransform.capture(doc).applyLocalDelta(4f, 2f), "own scaled root movement applies");
		near(102f, root.getAppliedPose().getWorldX(), "root own Y includes mirrored skeleton scale");
		near(-176f, root.getAppliedPose().getWorldY(), "root own X includes skeleton and own scale");
		assertTrue(history.commit() && history.undo() && !history.canUndo(), "local increment is one undo entry");
		near(100f, root.getAppliedPose().getWorldX(), "undo root local increment");
		assertTrue(history.redo(), "local increment redoes");
		near(-176f, root.getAppliedPose().getWorldY(), "redo root local increment");
	}

	private static void localDeltasRejectInvalidBatchesAtomically() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		Bone a = doc.skeleton.findBone("a");
		BoneSelectionTransform overflow = BoneSelectionTransform.capture(doc);
		try { overflow.applyLocalDelta(Float.MAX_VALUE, Float.MAX_VALUE); throw new AssertionError("overflowing local axes must reject"); }
		catch (IllegalArgumentException expected) {}
		near(10f, a.getPose().getX(), "overflow is atomic");
		doc.skeleton.findBone("rotated").getPose().setScale(0f);
		doc.skeleton.updateWorldTransform(Physics.pose);
		BoneSelectionTransform singular = BoneSelectionTransform.capture(doc);
		try { singular.applyLocalDelta(8f, 4f); throw new AssertionError("singular secondary parent must reject local move"); }
		catch (IllegalArgumentException expected) {}
		near(10f, a.getPose().getX(), "singular secondary parent leaves earlier target unchanged");
	}

	private static void worldDeltasSupportTinyAndHugeParentScale() {
		for (float scale : new float[] {1e-30f, 1e-8f, 1e20f, 1e30f}) for (boolean mirror : new boolean[] {false, true}) {
			SkeletonDocument doc = BoneSelectionTransformTest.document();
			Bone parent = doc.skeleton.findBone("rotated"), bone = doc.skeleton.findBone("b");
			parent.getPose().setPosition(0f, 0f);
			parent.getPose().setRotation(0f);
			parent.getPose().setScale(mirror ? -scale : scale, scale);
			bone.getPose().setPosition(0f, 0f);
			doc.skeleton.updateWorldTransform(Physics.pose);
			doc.setSelectedBone(bone);
			BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
			assertTrue(edit.applyWorldDelta(scale * 2f, -scale * 3f), "finite invertible scale accepts world movement: " + scale);
			near(mirror ? -2f : 2f, bone.getPose().getX(), "world X inverse remains accurate at extreme scale");
			near(-3f, bone.getPose().getY(), "world Y inverse remains accurate at extreme scale");
			assertTrue(!edit.applyWorldDelta(scale * 2f, -scale * 3f), "extreme-scale samples do not accumulate");
			assertTrue(edit.restore(), "extreme-scale world move can be canceled");
			near(0f, bone.getPose().getX(), "cancel restores local position at extreme scale");
		}
	}

	private static void worldDeltasRejectUnrepresentableLocalPositionsAtomically() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		Bone secondaryParent = doc.skeleton.findBone("rotated");
		secondaryParent.getPose().setScale(1e-20f);
		doc.skeleton.updateWorldTransform(Physics.pose);
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		try { edit.applyWorldDelta(Float.MAX_VALUE, 0f); throw new AssertionError("unrepresentable inverse must reject"); }
		catch (IllegalArgumentException expected) {}
		near(10f, doc.skeleton.findBone("a").getPose().getX(), "overflowing secondary inverse does not partially move the primary");
		near(4f, doc.skeleton.findBone("b").getPose().getX(), "overflowing inverse preserves the secondary pose");
	}

	private static void near(float expected, float actual, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.0002f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}
	private static void assertTrue(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private BoneCoordinateSpaceTest() {}
}
