package com.openspine.view;

import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BonePose;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;

/** A read-only reference for inactive skin bones, which the runtime does not update.
 * Active ancestors retain their actual constrained world transforms. Inactive branches use
 * their authored setup transforms, not stale animation poses left by a previously active skin.
 * The reference owns its runtime bones and has no skins, slots, animations or constraints. */
final class InactiveSkinBones {
	private Skeleton source;
	private Skeleton reference;

	Skeleton prepare(Skeleton skeleton) {
		if (skeleton == null) { clear(); return null; }
		if (!matches(skeleton)) {
			SkeletonData data = new SkeletonData();
			for (Bone bone : skeleton.getBones()) data.getBones().add(bone.getData());
			reference = new Skeleton(data);
			// Manual evaluation below includes inactive bones and never executes constraints.
			reference.getUpdateCache().clear();
			source = skeleton;
		}
		reference.setPosition(skeleton.getX(), skeleton.getY());
		reference.setScale(skeleton.getScaleX(), skeleton.getScaleY());
		// Advance the runtime pose generation before explicitly evaluating in parent order.
		reference.updateWorldTransform(Physics.pose);
		for (int index = 0; index < skeleton.getBones().size; index++) {
			Bone original = skeleton.getBones().get(index);
			BonePose pose = reference.getBones().get(index).getAppliedPose();
			if (original.isActive()) {
				BonePose applied = original.getAppliedPose();
				pose.set(applied);
				pose.setA(applied.getA()); pose.setB(applied.getB());
				pose.setC(applied.getC()); pose.setD(applied.getD());
				pose.setWorldX(applied.getWorldX()); pose.setWorldY(applied.getWorldY());
			} else {
				pose.set(original.getData().getSetupPose());
				pose.updateWorldTransform(reference);
			}
		}
		return reference;
	}

	private boolean matches(Skeleton skeleton) {
		if (source != skeleton || reference == null || reference.getBones().size != skeleton.getBones().size) return false;
		for (int index = 0; index < skeleton.getBones().size; index++) {
			Bone original = skeleton.getBones().get(index), copy = reference.getBones().get(index);
			if (original.getData() != copy.getData()) return false;
			if ((original.getParent() == null) != (copy.getParent() == null)) return false;
			if (original.getParent() != null && original.getParent().getData() != copy.getParent().getData()) return false;
		}
		return true;
	}

	void clear() { source = null; reference = null; }
}
