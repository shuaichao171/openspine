package com.openspine.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BonePose;
import com.esotericsoftware.spine.Skeleton;

/** A gesture's original selection and poses, shared by viewport and inspector transforms. */
public final class BoneSelectionTransform {
	private final SkeletonDocument doc;
	private final Skeleton skeleton;
	private final List<Bone> selected;
	private final List<Bone> roots;
	private final float[][] poses;
	private final BonePose[] parents;
	private final float[] primaryPose;
	private final BonePose primaryParent;
	private final BonePose primaryAxes;
	private final Vector2 primaryWorld;

	private BoneSelectionTransform(SkeletonDocument doc, List<Bone> selected) {
		this.doc = doc;
		skeleton = doc.skeleton;
		this.selected = List.copyOf(selected);
		ArrayList<Bone> rootList = new ArrayList<>();
		for (Bone bone : selected) {
			boolean inherited = false;
			for (Bone parent = bone.getParent(); parent != null; parent = parent.getParent())
				if (selected.contains(parent)) { inherited = true; break; }
			if (!inherited) rootList.add(bone);
		}
		roots = List.copyOf(rootList);
		poses = new float[roots.size()][];
		parents = new BonePose[roots.size()];
		for (int i = 0; i < roots.size(); i++) {
			poses[i] = pose(roots.get(i));
			parents[i] = BoneCoordinateSpace.parentAxes(skeleton, roots.get(i));
		}
		primaryPose = pose(selected.get(0));
		primaryParent = BoneCoordinateSpace.parentAxes(skeleton, selected.get(0));
		primaryAxes = BoneCoordinateSpace.boneAxes(selected.get(0));
		primaryWorld = BoneCoordinateSpace.WORLD.position(skeleton, selected.get(0), new Vector2());
	}

	public static BoneSelectionTransform capture(SkeletonDocument doc) {
		if (doc == null || doc.skeleton == null || doc.getSelectedBone() == null) return null;
		LinkedHashSet<Bone> bones = new LinkedHashSet<>();
		bones.add(doc.getSelectedBone());
		bones.addAll(doc.getSelectedBones());
		bones.removeIf(bone -> !doc.skeleton.getBones().contains(bone, true));
		return bones.isEmpty() ? null : new BoneSelectionTransform(doc, new ArrayList<>(bones));
	}

	public List<Bone> selectedBones() { return selected; }
	public List<Bone> transformedBones() { return roots; }
	public Bone primary() { return selected.get(0); }
	public float[] primaryPose() { return primaryPose.clone(); }
	public Vector2 primaryPosition(BoneCoordinateSpace space) {
		if (space == BoneCoordinateSpace.LOCAL) return new Vector2();
		return space == BoneCoordinateSpace.WORLD ? new Vector2(primaryWorld) : new Vector2(primaryPose[0], primaryPose[1]);
	}

	/** Local units include the primary bone's scale/shear; the entire selection shares this world delta. */
	public boolean applyLocalDelta(float x, float y) {
		if (!isCurrent()) return false;
		requireFinite(new float[] {x, y});
		Vector2 world = primaryAxes.localToWorld(new Vector2(x, y));
		return applyWorldDelta(world.x, world.y);
	}

	public boolean applyWorldPosition(float x, float y) {
		return applyWorldDelta(x - primaryWorld.x, y - primaryWorld.y);
	}

	/** Applies one world displacement to every selected subtree root, including scaled skeleton roots. */
	public boolean applyWorldDelta(float x, float y) {
		if (!isCurrent()) return false;
		requireFinite(new float[] {x, y});
		float[][] next = new float[roots.size()][];
		for (int i = 0; i < roots.size(); i++) {
			next[i] = poses[i].clone();
			translate(next[i], parents[i], x, y);
		}
		return apply(next);
	}

	public boolean hasChanges() {
		if (doc.skeleton != skeleton) return false;
		for (int i = 0; i < roots.size(); i++)
			if (!java.util.Arrays.equals(poses[i], pose(roots.get(i)))) return true;
		return false;
	}

	public boolean isCurrent() {
		return doc.skeleton == skeleton && doc.getSelectedBone() == primary()
			&& new LinkedHashSet<>(selected).equals(doc.getSelectedBones());
	}

	/** Selected descendants inherit their selected ancestor's transform exactly once. */
	public boolean applyPrimaryPose(float x, float y, float rotation, float scaleX, float scaleY,
		float shearX, float shearY) {
		if (!isCurrent()) return false;
		float[] requested = {x, y, rotation, scaleX, scaleY, shearX, shearY};
		requireFinite(requested);
		Vector2 world = primaryParent.localToWorld(new Vector2(x - primaryPose[0], y - primaryPose[1]));
		float[][] next = new float[roots.size()][];
		for (int i = 0; i < roots.size(); i++) {
			float[] start = poses[i];
			float[] values = next[i] = start.clone();
			if (roots.get(i) == primary()) { values[0] = x; values[1] = y; }
			else if (roots.get(i).getParent() == primary().getParent()) {
				// Authored parent coordinates remain editable even when that parent's scale is zero.
				// Siblings share this coordinate frame, including movement along a collapsed axis.
				values[0] = (float)(start[0] + (double)x - primaryPose[0]);
				values[1] = (float)(start[1] + (double)y - primaryPose[1]);
			} else translate(values, parents[i], world.x, world.y);
			values[2] = (float)(start[2] + (double)rotation - primaryPose[2]);
			values[3] = relativeScale(start[3], primaryPose[3], scaleX);
			values[4] = relativeScale(start[4], primaryPose[4], scaleY);
			values[5] = (float)(start[5] + (double)shearX - primaryPose[5]);
			values[6] = (float)(start[6] + (double)shearY - primaryPose[6]);
			requireFinite(values);
		}
		return apply(next);
	}

	private static void translate(float[] values, BonePose parent, float worldX, float worldY) {
		if (worldX == 0f && worldY == 0f) return;
		Vector2 delta = BoneCoordinateSpace.worldDeltaToParent(parent, worldX, worldY);
		values[0] += delta.x;
		values[1] += delta.y;
		requireFinite(values);
	}

	public boolean restore() {
		return doc.skeleton == skeleton && apply(poses);
	}

	private boolean apply(float[][] values) {
		boolean changed = false;
		// Validate every target before publishing any part of the gesture.
		for (int i = 0; i < roots.size(); i++) {
			if (!skeleton.getBones().contains(roots.get(i), true)) return false;
			requireFinite(values[i]);
		}
		for (int i = 0; i < roots.size(); i++) {
			float[] pose = values[i];
			changed |= doc.updateBonePose(roots.get(i), pose[0], pose[1], pose[2],
				pose[3], pose[4], pose[5], pose[6]);
		}
		return changed;
	}

	private static float relativeScale(float value, float primary, float requested) {
		if (requested == primary) return value;
		return primary == 0f ? (float)(value + (double)requested - primary)
			: (float)(value * ((double)requested / primary));
	}

	private static float[] pose(Bone bone) {
		var pose = bone.getPose();
		float[] values = {pose.getX(), pose.getY(), pose.getRotation(), pose.getScaleX(),
			pose.getScaleY(), pose.getShearX(), pose.getShearY()};
		requireFinite(values);
		return values;
	}

	private static void requireFinite(float[] values) {
		for (float value : values)
			if (!Float.isFinite(value)) throw new IllegalArgumentException("Bone transform must be finite.");
	}
}
