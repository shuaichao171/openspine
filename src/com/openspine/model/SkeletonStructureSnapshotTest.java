package com.openspine.model;

import java.util.Arrays;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;

/** Structural history must stay immutable while later timeline edits are undone and redone. */
public final class SkeletonStructureSnapshotTest {
	public static void main(String[] args) {
		doesNotResurrectUndoneKeyEdits();
		recalculatesDurationWhenTheLastFolderTrackDisappears();
		restoresTimelineSlotsForVertexAndImageAttachments();
		restoresAttachmentBindingsAfterLaterReplacement();
		restoresEventDefinitionsAndTimelineReferences();
		System.out.println("ALL SKELETON STRUCTURE SNAPSHOT TESTS PASSED");
	}

	private static void doesNotResurrectUndoneKeyEdits() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		AnimationEditor animationEditor = new AnimationEditor(doc);
		BoneData removed = editor.addBone("removed", doc.data.getBones().first());
		editor.addBone("keep", doc.data.getBones().first());
		Animation animation = animationEditor.addAnimation("keys");
		animationEditor.ensureTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		animationEditor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, 0, 1f, 10f, 0f);
		editor.rebuildSkeleton();
		doc.setAnimation("keys");
		SkeletonStructureSnapshot before = SkeletonStructureSnapshot.capture(editor);
		check(editor.deleteBone(removed), "structure edit succeeds");
		SkeletonStructureSnapshot after = SkeletonStructureSnapshot.capture(editor);
		EditHistory history = new EditHistory(doc);
		history.record("structure", before::restore, after::restore);

		AnimationEditor.TimelineSnapshot beforeKey = animationEditor.snapshotBoneTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		animationEditor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, 0, 1f, 30f, 0f);
		AnimationEditor.TimelineSnapshot afterKey = animationEditor.snapshotBoneTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		history.record("key", () -> animationEditor.restoreTimeline(beforeKey), () -> animationEditor.restoreTimeline(afterKey));
		check(history.undo(), "undo later key edit");
		near(10f, rotation(animationEditor, animation), "key value is restored before undoing structure");
		check(history.undo() && doc.data.findBone("removed") != null, "undo structure");
		near(10f, rotation(animationEditor, animation), "structure undo cannot resurrect an already undone key value");
		check(history.redo() && doc.data.findBone("removed") == null, "redo structure");
		near(10f, rotation(animationEditor, animation), "structure redo retains the pre-key-edit value");
		check(history.redo(), "redo the key edit separately");
		near(30f, rotation(animationEditor, animation), "key redo remains an independent operation");
		check(history.undo() && history.undo(), "undo both edits again");
		near(10f, rotation(animationEditor, animation), "repeated restoration never mutates the stored snapshot");

		after.restore();
		animationEditor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, 0, 1f, 70f, 0f);
		after.restore();
		near(10f, rotation(animationEditor, animation), "the restored working timeline cannot mutate its source snapshot");
	}

	private static void recalculatesDurationWhenTheLastFolderTrackDisappears() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		SlotData slot = editor.addSlot("empty", doc.data.getBones().first());
		Animation animation = new Animation("folder");
		Animation.DrawOrderFolderTimeline folder = new Animation.DrawOrderFolderTimeline(1, new int[] {0}, 1);
		folder.setFrame(0, 2f, null);
		Array<Animation.Timeline> tracks = new Array<>(true, 1, Animation.Timeline[]::new);
		tracks.add(folder);
		animation.setTimelines(tracks, new IntArray(new int[] {0}));
		animation.setDuration(2f);
		doc.data.getAnimations().add(animation);
		SliderData slider = new SliderData("loop");
		slider.setBone(doc.data.getBones().first());
		slider.setAnimation(animation);
		slider.setLoop(true);
		doc.data.getConstraints().add(slider);
		editor.rebuildSkeleton();
		doc.setAnimation("folder");
		SkeletonStructureSnapshot before = SkeletonStructureSnapshot.capture(editor);
		check(editor.deleteSlot(slot), "an empty slot referenced only by folder membership can be deleted");
		check(animation.getTimelines().isEmpty(), "an empty folder timeline is removed");
		near(0f, animation.getDuration(), "animation duration follows its retained timelines");
		near(0f, doc.animationState.getTrack(0).getAnimationEnd(), "playback end follows the shortened animation");
		check(!slider.getLoop(), "a slider cannot loop a zero-duration animation");
		check(Arrays.equals(animation.getBones().toArray(), new int[] {0}), "explicit animation activation bones are retained");
		SkeletonStructureSnapshot after = SkeletonStructureSnapshot.capture(editor);
		before.restore();
		near(2f, animation.getDuration(), "undo restores the original duration");
		near(2f, doc.animationState.getTrack(0).getAnimationEnd(), "undo restores playback bounds");
		check(slider.getLoop(), "undo restores the original slider loop flag");
		check(animation.getTimelines().size == 1, "undo restores the removed folder timeline");
		after.restore();
		near(0f, animation.getDuration(), "redo restores the shortened duration");
		check(!slider.getLoop() && animation.getTimelines().isEmpty(), "redo restores empty timeline and slider state together");
	}

	private static void restoresTimelineSlotsForVertexAndImageAttachments() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		for (int i = 0; i < 3; i++) editor.addSlot("slot" + i, doc.data.getBones().first());
		Skin skin = new Skin("default");
		RegionAttachment region = new RegionAttachment("region", new Sequence(1, false));
		region.setTimelineSlots(new int[] {1, 2});
		BoundingBoxAttachment box = new BoundingBoxAttachment("box");
		box.setTimelineSlots(new int[] {0, 2});
		skin.setAttachment(0, "region", region);
		skin.setAttachment(1, "box", box);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		SkeletonStructureSnapshot snapshot = SkeletonStructureSnapshot.capture(editor);
		region.setTimelineSlots(new int[] {2});
		box.setTimelineSlots(new int[] {1});
		snapshot.restore();
		check(Arrays.equals(region.getTimelineSlots(), new int[] {1, 2}), "image timeline slot references are restored");
		check(Arrays.equals(box.getTimelineSlots(), new int[] {0, 2}), "vertex timeline slot references are restored");
		region.getTimelineSlots()[0] = 0;
		box.getTimelineSlots()[0] = 1;
		snapshot.restore();
		check(Arrays.equals(region.getTimelineSlots(), new int[] {1, 2}), "image reference snapshots are immutable");
		check(Arrays.equals(box.getTimelineSlots(), new int[] {0, 2}), "vertex reference snapshots are immutable");
	}

	private static void restoresAttachmentBindingsAfterLaterReplacement() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		editor.addSlot("slot", doc.data.getBones().first());
		Skin skin = new Skin("default");
		BoundingBoxAttachment original = new BoundingBoxAttachment("original");
		skin.setAttachment(0, "box", original);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		SkeletonStructureSnapshot snapshot = SkeletonStructureSnapshot.capture(editor);
		skin.setAttachment(0, "box", new BoundingBoxAttachment("replacement"));
		snapshot.restore();
		check(skin.getAttachment(0, "box") == original, "later attachment replacement cannot alter stored bindings");
		skin.setAttachment(0, "box", new BoundingBoxAttachment("replacement-again"));
		snapshot.restore();
		check(skin.getAttachment(0, "box") == original, "restored attachment bindings remain independent of the snapshot");
	}

	private static void restoresEventDefinitionsAndTimelineReferences() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		AnimationEditor animations = new AnimationEditor(doc);
		Animation animation = animations.addAnimation("events");
		EventData original = animations.addEventDefinition("step");
		animations.addEventKeyframe(animation, 0.5f, original, 1, 2f, "before", 0f, 0f);
		SkeletonStructureSnapshot before = SkeletonStructureSnapshot.capture(editor);
		EventData updated = animations.updateEventDefinition(original, "step-edited", 3, 4f,
			"after", null, 0f, 0f);
		SkeletonStructureSnapshot after = SkeletonStructureSnapshot.capture(editor);
		EditHistory history = new EditHistory(doc);
		history.record("event definition", before::restore, after::restore);

		check(history.undo() && doc.data.getEvents().first() == original,
			"undo restores the original event definition object");
		Animation.EventTimeline undone = (Animation.EventTimeline)animation.getTimelines().first();
		check(undone.getEvents()[0].getData() == original,
			"undo restores timeline references to the original definition");
		check(history.redo() && doc.data.getEvents().first() == updated,
			"redo restores the replacement event definition object");
		Animation.EventTimeline redone = (Animation.EventTimeline)animation.getTimelines().first();
		check(redone.getEvents()[0].getData() == updated,
			"redo restores timeline references to the replacement definition");
	}

	private static float rotation(AnimationEditor editor, Animation animation) {
		Animation.Timeline timeline = editor.findTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		return editor.frameValue(timeline, editor.findFrame(timeline, 1f), 0);
	}

	private static SkeletonDocument document() {
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setScale(1f, 1f);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		doc.data.getBones().add(root);
		doc.skeleton = new Skeleton(doc.data);
		doc.animationState = doc.newAnimationState();
		return doc;
	}

	private static void near(float expected, float actual, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
