package com.openspine.model;

import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BonePose;
import com.esotericsoftware.spine.Skeleton;

/** Parent/world positions, or incremental movement along the primary bone's own scaled axes. */
public enum BoneCoordinateSpace {
	PARENT, WORLD, LOCAL;

	public Vector2 position(Skeleton skeleton, Bone bone, Vector2 out) {
		if (out == null) out = new Vector2();
		// A bone's own origin is always zero. LOCAL fields accept deltas from each gesture start.
		if (this == LOCAL) return out.setZero();
		out.set(bone.getPose().getX(), bone.getPose().getY());
		if (this == PARENT) return out;
		Bone parent = bone.getParent();
		if (parent != null) return parent.getAppliedPose().localToWorld(out);
		return out.set(out.x * skeleton.getScaleX() + skeleton.getX(), out.y * skeleton.getScaleY() + skeleton.getY());
	}

	public float axisRotation(Skeleton skeleton, Bone bone, boolean yAxis) {
		if (this == WORLD) return yAxis ? 90f : 0f;
		BonePose axes = this == LOCAL ? bone.getAppliedPose() : parentAxes(skeleton, bone);
		return yAxis ? axes.getWorldRotationY() : axes.getWorldRotationX();
	}

	static BonePose boneAxes(Bone bone) {
		BonePose axes = new BonePose(), pose = bone.getAppliedPose();
		axes.setA(pose.getA()); axes.setB(pose.getB()); axes.setC(pose.getC()); axes.setD(pose.getD());
		return axes;
	}

	/** Snapshot only the linear part so later parent updates cannot change an in-progress gesture. */
	static BonePose parentAxes(Skeleton skeleton, Bone bone) {
		BonePose axes = new BonePose();
		Bone parent = bone.getParent();
		if (parent == null) {
			axes.setA(skeleton.getScaleX());
			axes.setD(skeleton.getScaleY());
		} else {
			BonePose pose = parent.getAppliedPose();
			axes.setA(pose.getA());
			axes.setB(pose.getB());
			axes.setC(pose.getC());
			axes.setD(pose.getD());
		}
		return axes;
	}

	static Vector2 worldDeltaToParent(BonePose axes, float x, float y) {
		double determinant = (double)axes.getA() * axes.getD() - (double)axes.getB() * axes.getC();
		if (!Double.isFinite(determinant) || determinant == 0d)
			throw new IllegalArgumentException("Cannot move a selection through a zero-scale parent.");
		// Float products can overflow for a valid large scale or underflow for a small one.
		// Test the actual determinant and compute the inverse in double before storing local floats.
		float localX = (float)(((double)axes.getD() * x - (double)axes.getB() * y) / determinant);
		float localY = (float)(((double)axes.getA() * y - (double)axes.getC() * x) / determinant);
		if (!Float.isFinite(localX) || !Float.isFinite(localY))
			throw new IllegalArgumentException("Bone transform must be finite.");
		return new Vector2(localX, localY);
	}
}
