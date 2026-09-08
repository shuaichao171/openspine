package com.openspine.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Bone;

/** Keys a complete bone selection with one rollback boundary and one history entry. */
public final class BoneKeyframeBatch {
	private BoneKeyframeBatch() {}

	public static Result apply(SkeletonDocument doc, AnimationEditor editor, Animation animation,
		List<Bone> requested, AnimationEditor.Prop editedProp, float time,
		Function<Bone, boolean[]> axisPreferences) {
		if (doc == null || editor == null || doc.data == null || doc.skeleton == null
			|| animation == null || !doc.data.getAnimations().contains(animation, true)
			|| requested == null || requested.isEmpty() || !Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("Invalid bone keyframe batch.");
		List<Bone> bones = new ArrayList<>(new LinkedHashSet<>(requested));
		List<boolean[]> preferences = new ArrayList<>();
		List<AnimationEditor.TimelineSnapshot> snapshots = new ArrayList<>();
		for (Bone bone : bones) {
			if (bone == null || !doc.skeleton.getBones().contains(bone, true))
				throw new IllegalArgumentException("The selected bone is no longer in this skeleton.");
			boolean[] axes = axisPreferences == null ? new boolean[3] : axisPreferences.apply(bone);
			if (axes == null || axes.length != 3) throw new IllegalArgumentException("Invalid axis preferences.");
			preferences.add(axes.clone());
			int index = bone.getData().getIndex();
			Array<AnimationEditor.Prop> props = editedProp == null
				? editor.poseKeyProps(animation, index, axes[0], axes[1], axes[2])
				: editor.transformKeyProps(animation, index, editedProp, axes[0], axes[1], axes[2]);
			for (AnimationEditor.Prop prop : props) {
				for (float value : editor.valuesForPose(bone, prop))
					if (!Float.isFinite(value)) throw new IllegalArgumentException("Keyframe values must be finite.");
				snapshots.add(editor.snapshotBoneTimeline(animation, prop, index));
			}
		}
		AnimationEditor.TimelineSnapshot[] before = snapshots.toArray(AnimationEditor.TimelineSnapshot[]::new);
		float beforeDuration = animation.getDuration();
		boolean wasDirty = doc.dirty;
		try {
			for (int i = 0; i < bones.size(); i++) {
				boolean[] axes = preferences.get(i);
				if (editedProp == null)
					editor.keyBonePose(animation, bones.get(i), time, axes[0], axes[1], axes[2]);
				else editor.keyBoneTransform(animation, bones.get(i), time, editedProp, axes[0], axes[1], axes[2]);
			}
			AnimationEditor.TimelineSnapshot[] after = new AnimationEditor.TimelineSnapshot[before.length];
			boolean changed = false;
			for (int i = 0; i < before.length; i++) {
				after[i] = editor.snapshotCurrent(before[i]);
				changed |= !before[i].sameAs(after[i]);
			}
			return new Result(doc, editor, animation, before, after, beforeDuration, animation.getDuration(), changed);
		} catch (RuntimeException error) {
			editor.restoreTimelines(before);
			restoreDuration(doc, animation, beforeDuration);
			doc.setDirtyFromHistory(wasDirty);
			throw error;
		}
	}

	private static void restoreDuration(SkeletonDocument doc, Animation animation, float duration) {
		animation.setDuration(duration);
		if (doc.animationState != null)
			for (var track : doc.animationState.getTracks())
				if (track != null && track.getAnimation() == animation) track.setAnimationEnd(duration);
	}

	public static final class Result {
		private final SkeletonDocument doc;
		private final AnimationEditor editor;
		private final Animation animation;
		private final AnimationEditor.TimelineSnapshot[] before, after;
		private final float beforeDuration, afterDuration;
		private final List<String> selection;
		private final boolean changed;

		private Result(SkeletonDocument doc, AnimationEditor editor, Animation animation,
			AnimationEditor.TimelineSnapshot[] before, AnimationEditor.TimelineSnapshot[] after,
			float beforeDuration, float afterDuration, boolean changed) {
			this.doc = doc;
			this.editor = editor;
			this.animation = animation;
			this.before = before;
			this.after = after;
			this.beforeDuration = beforeDuration;
			this.afterDuration = afterDuration;
			this.changed = changed;
			selection = doc.getSelectedBones().stream().map(bone -> bone.getData().getName()).toList();
		}

		public boolean changed() { return changed; }

		public void record(EditHistory history, String label) {
			if (changed && history != null) history.record(label,
				() -> restore(before, beforeDuration), () -> restore(after, afterDuration));
		}

		private void restore(AnimationEditor.TimelineSnapshot[] snapshots, float duration) {
			editor.restoreTimelines(snapshots);
			restoreDuration(doc, animation, duration);
			List<Bone> bones = new ArrayList<>();
			for (String name : selection) {
				Bone bone = doc.skeleton.findBone(name);
				if (bone != null) bones.add(bone);
			}
			doc.setSelectedBones(bones);
			doc.refreshCurrentAnimationPose();
		}
	}
}
