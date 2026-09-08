package com.openspine.model;

import java.util.List;

import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Bone;

/** Headless regression tests for all-or-nothing multi-bone keying. */
public final class BoneKeyframeBatchTest {
	public static void main(String[] args) {
		keysMixedAxisLayoutsWithOneUndo();
		keysTheCompletePoseIncludingSelectedChildren();
		rollsBackEarlierBonesWhenALaterBoneFails();
		rejectsInvalidTargetsBeforeWritingAnyKeys();
		System.out.println("ALL BONE KEYFRAME BATCH TESTS PASSED");
	}

	private static void keysMixedAxisLayoutsWithOneUndo() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("group");
		doc.animationState = new AnimationState(new AnimationStateData(doc.data));
		editor.ensureTimeline(animation, AnimationEditor.Prop.TRANSLATE, 1);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE, 1, 0f, 0f, 0f);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE, 1, 1f, 5f, 6f);
		Animation.Timeline original = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 1);
		editor.setBezierCurve(animation, original, 0, 0.2f, 0.3f, 0.8f, 0.9f);
		AnimationEditor.TimelineSnapshot originalCurve = editor.snapshotBoneTimeline(animation, AnimationEditor.Prop.TRANSLATE, 1);
		doc.setAnimation("group");
		doc.setPoseMode(true);
		List<Bone> bones = List.copyOf(doc.getSelectedBones());
		bones.get(0).getPose().setPosition(30f, 40f);
		bones.get(1).getPose().setPosition(14f, 15f);
		doc.markSaved();
		EditHistory history = new EditHistory(doc);
		BoneKeyframeBatch.Result result = BoneKeyframeBatch.apply(doc, editor, animation, bones,
			AnimationEditor.Prop.TRANSLATE, 2f, bone -> new boolean[] {bone == bones.get(1), false, false});
		assertTrue(result.changed(), "both selected bones are keyed");
		result.record(history, "group keys");
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 1) != null,
			"the combined primary track stays combined");
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 3) != null
			&& editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_Y, 3) != null
			&& editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 3) == null,
			"another bone uses its own separate-axis preferences");
		AnimationEditor.BezierCurve curve = editor.bezierCurve(editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 1), 0, 0);
		near(0.2f, curve.cx1(), "existing Bezier handles survive group keying");
		assertTrue(history.undo() && !history.canUndo(), "one undo removes the complete batch");
		assertTrue(originalCurve.sameAs(editor.snapshotBoneTimeline(animation, AnimationEditor.Prop.TRANSLATE, 1)),
			"undo restores exact curve data and keys");
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 3) == null,
			"undo removes newly created axis tracks");
		assertTrue(!doc.dirty && doc.getSelectedBones().size() == 2, "undo restores savepoint and selection");
		assertTrue(history.redo() && editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 3) != null,
			"redo restores every bone's keys together");
	}

	private static void keysTheCompletePoseIncludingSelectedChildren() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("pose");
		List<Bone> bones = List.of(doc.skeleton.findBone("a"), doc.skeleton.findBone("child"));
		doc.setSelectedBones(bones);
		BoneKeyframeBatch.apply(doc, editor, animation, bones, null, 0.5f, bone -> new boolean[] {true, true, true});
		for (Bone bone : bones) {
			int index = bone.getData().getIndex();
			for (AnimationEditor.Prop prop : editor.poseKeyProps(animation, index, true, true, true))
				assertTrue(editor.findFrame(editor.findTimeline(animation, prop, index), 0.5f) >= 0,
					"manual pose keys every selected bone/channel: " + bone.getData().getName() + " " + prop);
		}
	}

	private static void rollsBackEarlierBonesWhenALaterBoneFails() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		AnimationEditor editor = new AnimationEditor(doc) {
			@Override public void keyBoneTransform(Animation animation, Bone bone, float time,
				Prop prop, boolean translate, boolean scale, boolean shear) {
				if (bone.getData().getName().equals("b")) throw new IllegalStateException("second bone failed");
				super.keyBoneTransform(animation, bone, time, prop, translate, scale, shear);
			}
		};
		Animation animation = editor.addAnimation("rollback");
		animation.setDuration(3f);
		doc.markSaved();
		try {
			BoneKeyframeBatch.apply(doc, editor, animation, List.copyOf(doc.getSelectedBones()),
				AnimationEditor.Prop.ROTATE, 0.5f, null);
			throw new AssertionError("the injected failure must propagate");
		} catch (IllegalStateException expected) {}
		assertTrue(animation.getTimelines().isEmpty(), "the first bone's newly created keys roll back");
		near(3f, animation.getDuration(), "rollback preserves the previous animation duration");
		assertTrue(!doc.dirty, "a rejected batch does not dirty the document");
	}

	private static void rejectsInvalidTargetsBeforeWritingAnyKeys() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("invalid");
		List<Bone> bones = List.copyOf(doc.getSelectedBones());
		bones.get(1).getPose().setRotation(Float.NaN);
		try {
			BoneKeyframeBatch.apply(doc, editor, animation, bones, AnimationEditor.Prop.ROTATE, 0.5f, null);
			throw new AssertionError("invalid later values must be rejected");
		} catch (IllegalArgumentException expected) {}
		assertTrue(animation.getTimelines().isEmpty(), "validation covers the whole selection before editing");
	}

	private static void near(float expected, float actual, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
