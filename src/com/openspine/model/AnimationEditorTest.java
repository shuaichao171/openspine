package com.openspine.model;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.Animation.SlotCurveTimeline;
import com.esotericsoftware.spine.attachments.AttachmentLoader;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.Sequence.SequenceMode;
import com.openspine.io.SkeletonExporter;

/** Headless regression tests for editable bone timelines. */
public final class AnimationEditorTest {
	public static void main(String[] args) {
		keysOnlyCompletedTransformChannels();
		editsSequenceTimelines();
		editsDeformTimelines();
		editsIkConstraintTimelines();
		editsTransformConstraintTimelines();
		editsPathConstraintTimelines();
		editsPhysicsConstraintTimelines();
		editsSliderConstraintTimelines();
		rejectsPropertyDrivenSliderTimeEdits();
		disablesLoopingSliderWhenTargetBecomesEmpty();
		editsDrawOrderTimelines();
		editsSplitColorTimelines();
		editsInheritTimelines();
		editsEventTimelinesAndDefinitions();
		previewsTwoColorTimeline();
		editsBezierControlPoints();
		preservesUneditedBezierChannelSamples();
		editsAbsoluteBezierHandles();
		preservesReturningBezierCurvesWhenPasting();
		editsGraphCurveModesPerChannel();
		editsGraphKeySelectionsAtomically();
		distinguishesExactSubframeKeys();
		copiesAndPastesMultiTrackKeyframeBlocks();
		cleansOnlyRedundantLinearKeyframes();
		importsAnimationsByStrictTargetNames();
		importsAnimationBatchesAtomically();
		convertsSeparatedAxisTimelinesWithoutLoss();
		restoresAxisConversionTransactionsExactly();
		editsMultiChannelBezierTimelines();
		copiesBezierCurvesBetweenKeys();
		managesActiveAnimationDeletion();
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("pose");
		var bone = doc.skeleton.getRootBone();
		bone.getPose().setX(14f);
		bone.getPose().setY(26f);
		bone.getPose().setRotation(50f);
		bone.getPose().setScaleX(3f);
		bone.getPose().setScaleY(6f);
		bone.getPose().setShearX(12f);
		bone.getPose().setShearY(-4f);

		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.ROTATE);
		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.TRANSLATE);
		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.TRANSLATE_X);
		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.TRANSLATE_Y);
		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.SCALE);
		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.SCALE_X);
		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.SCALE_Y);
		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.SHEAR);
		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.SHEAR_X);
		addPoseFrame(editor, animation, bone, AnimationEditor.Prop.SHEAR_Y);
		assertFrame(editor, animation, AnimationEditor.Prop.ROTATE, 0, 0f, 0f, 0f);
		assertFrame(editor, animation, AnimationEditor.Prop.TRANSLATE, 0, 0f, 0f, 0f);
		assertFrame(editor, animation, AnimationEditor.Prop.SCALE, 0, 0f, 1f, 1f);
		assertFrame(editor, animation, AnimationEditor.Prop.SHEAR, 0, 0f, 0f, 0f);
		assertFrame(editor, animation, AnimationEditor.Prop.ROTATE, 1, 0.5f, 20f, 20f);
		assertFrame(editor, animation, AnimationEditor.Prop.TRANSLATE, 1, 0.5f, 4f, 6f);
		assertFrame(editor, animation, AnimationEditor.Prop.SCALE, 1, 0.5f, 1.5f, 2f);
		assertFrame(editor, animation, AnimationEditor.Prop.TRANSLATE_X, 1, 0.5f, 4f, 4f);
		assertFrame(editor, animation, AnimationEditor.Prop.TRANSLATE_Y, 1, 0.5f, 6f, 6f);
		assertFrame(editor, animation, AnimationEditor.Prop.SCALE_X, 1, 0.5f, 1.5f, 1.5f);
		assertFrame(editor, animation, AnimationEditor.Prop.SCALE_Y, 1, 0.5f, 2f, 2f);
		assertFrame(editor, animation, AnimationEditor.Prop.SHEAR, 1, 0.5f, 7f, 6f);
		assertFrame(editor, animation, AnimationEditor.Prop.SHEAR_X, 1, 0.5f, 7f, 7f);
		assertFrame(editor, animation, AnimationEditor.Prop.SHEAR_Y, 1, 0.5f, 6f, 6f);
		for (AnimationEditor.Prop prop : new AnimationEditor.Prop[] {
			AnimationEditor.Prop.TRANSLATE_X, AnimationEditor.Prop.TRANSLATE_Y,
			AnimationEditor.Prop.SCALE_X, AnimationEditor.Prop.SCALE_Y,
			AnimationEditor.Prop.SHEAR_X, AnimationEditor.Prop.SHEAR_Y
		}) {
			assertTrue(editor.findTimeline(animation, prop, 0) != null, prop + " timeline can be found");
		}

		Animation fullPose = editor.addAnimation("full-pose");
		editor.ensureTimeline(fullPose, AnimationEditor.Prop.TRANSLATE_X, 0);
		editor.keyBonePose(fullPose, bone, 0.75f);
		assertTrue(editor.findTimeline(fullPose, AnimationEditor.Prop.TRANSLATE, 0) == null,
			"complete pose does not overlap an imported single-axis translation");
		assertFrame(editor, fullPose, AnimationEditor.Prop.ROTATE, 1, 0.75f, 20f, 20f);
		assertFrame(editor, fullPose, AnimationEditor.Prop.TRANSLATE_X, 1, 0.75f, 4f, 4f);
		assertFrame(editor, fullPose, AnimationEditor.Prop.TRANSLATE_Y, 1, 0.75f, 6f, 6f);
		assertFrame(editor, fullPose, AnimationEditor.Prop.SCALE, 1, 0.75f, 1.5f, 2f);
		assertFrame(editor, fullPose, AnimationEditor.Prop.SHEAR, 1, 0.75f, 7f, 6f);
		assertTrue(fullPose.getTimelines().size == 5, "complete pose creates exactly five non-overlapping tracks");
		doc.dirty = false;
		editor.keyBonePose(fullPose, bone, 0.75f);
		assertTrue(!doc.dirty, "recording an unchanged pose is a no-op");

		Animation historyPose = editor.addAnimation("history-pose");
		Array<AnimationEditor.Prop> historyProps = editor.poseKeyProps(historyPose, 0);
		AnimationEditor.TimelineSnapshot[] historyBefore = snapshotProps(editor, historyPose, historyProps);
		editor.keyBonePose(historyPose, bone, 1.25f);
		AnimationEditor.TimelineSnapshot[] historyAfter = snapshotCurrent(editor, historyBefore);
		EditHistory history = new EditHistory(doc);
		history.record("complete pose", () -> editor.restoreTimelines(historyBefore),
			() -> editor.restoreTimelines(historyAfter));
		assertTrue(history.undo(), "complete pose undo succeeds");
		for (AnimationEditor.Prop prop : historyProps)
			assertTrue(editor.findTimeline(historyPose, prop, 0) == null, "complete pose undo removes " + prop);
		assertTrue(history.redo(), "complete pose redo succeeds");
		assertFrame(editor, historyPose, AnimationEditor.Prop.ROTATE, 1, 1.25f, 20f, 20f);
		assertFrame(editor, historyPose, AnimationEditor.Prop.TRANSLATE, 1, 1.25f, 4f, 6f);
		assertFrame(editor, historyPose, AnimationEditor.Prop.SCALE, 1, 1.25f, 1.5f, 2f);
		assertFrame(editor, historyPose, AnimationEditor.Prop.SHEAR, 1, 1.25f, 7f, 6f);

		doc.skeleton.setupPose();
		doc.animationState = new AnimationState(new AnimationStateData(doc.data));
		doc.animationState.setAnimation(0, animation, false).setTrackTime(0.5f);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE_X, 0, 45f, 8f, 0f);
		assertNear(45f, animation.getDuration(), "long keyframe refreshes animation duration");
		assertNear(45f, doc.getCurrentDuration(), "active track follows edited animation duration");
		assertFrame(editor, animation, AnimationEditor.Prop.TRANSLATE_X, 2, 45f, 8f, 8f);
		doc.animationState.update(0f);
		doc.animationState.apply(doc.skeleton);
		assertNear(14f, bone.getPose().getX(), "applied x");
		assertNear(26f, bone.getPose().getY(), "applied y");
		assertNear(50f, bone.getPose().getRotation(), "applied rotation");
		assertNear(3f, bone.getPose().getScaleX(), "applied scale x");
		assertNear(6f, bone.getPose().getScaleY(), "applied scale y");
		assertNear(12f, bone.getPose().getShearX(), "applied shear x");
		assertNear(-4f, bone.getPose().getShearY(), "applied shear y");

		Animation shearEdits = editor.addAnimation("shear-edits");
		editor.ensureTimeline(shearEdits, AnimationEditor.Prop.SHEAR, 0);
		editor.addKeyframe(shearEdits, AnimationEditor.Prop.SHEAR, 0, 1f, 3f, 4f);
		editor.addKeyframe(shearEdits, AnimationEditor.Prop.SHEAR, 0, 2f, 5f, 6f);
		int movedShear = editor.editKeyframe(shearEdits, AnimationEditor.Prop.SHEAR, 0,
			1f, 1.25f, 7f, 8f, true);
		assertTrue(movedShear == 1, "shear key move returns sorted index");
		assertFrame(editor, shearEdits, AnimationEditor.Prop.SHEAR, 1, 1.25f, 7f, 8f);
		editor.removeKeyframe(shearEdits, AnimationEditor.Prop.SHEAR, 0, 2f);
		assertTrue(editor.findTimeline(shearEdits, AnimationEditor.Prop.SHEAR, 0).getFrameCount() == 2,
			"shear key removal rebuilds timeline");

		doc.dirty = false;
		Animation.Timeline rotate = editor.findTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		editor.setCurve(rotate, 0, true);
		assertTrue(doc.dirty, "curve edit marks document dirty");
		int lastFrame = rotate.getFrameCount() - 1;
		int terminalCurve = editor.curveType(rotate, lastFrame);
		doc.dirty = false;
		editor.setCurve(rotate, lastFrame, false);
		assertTrue(editor.curveType(rotate, lastFrame) == terminalCurve, "terminal curve sentinel remains unchanged");
		assertTrue(!doc.dirty, "terminal curve edit is ignored");

		Animation precise = editor.addAnimation("precise");
		editor.ensureTimeline(precise, AnimationEditor.Prop.TRANSLATE, 0);
		editor.addKeyframe(precise, AnimationEditor.Prop.TRANSLATE, 0, 1f, 2f, 3f);
		editor.addKeyframe(precise, AnimationEditor.Prop.TRANSLATE, 0, 2f, 4f, 5f);
		doc.animationState.setAnimation(0, precise, false);
		int edited = editor.editKeyframe(precise, AnimationEditor.Prop.TRANSLATE, 0,
			1f, 1.25f, 7f, 8f, true);
		assertTrue(edited == 1, "precise edit returns the sorted frame index");
		assertFrame(editor, precise, AnimationEditor.Prop.TRANSLATE, 1, 1.25f, 7f, 8f);
		Animation.Timeline preciseTimeline = editor.findTimeline(precise, AnimationEditor.Prop.TRANSLATE, 0);
		assertTrue(editor.curveType(preciseTimeline, edited) == Animation.CurveTimeline.STEPPED,
			"precise edit stores stepped interpolation");
		assertThrows(IllegalArgumentException.class,
			() -> editor.editKeyframe(precise, AnimationEditor.Prop.TRANSLATE, 0,
				1.25f, 2f, 9f, 10f, false),
			"moving onto another key must be blocked");
		assertThrows(IllegalArgumentException.class,
			() -> editor.editKeyframe(precise, AnimationEditor.Prop.TRANSLATE, 0,
				1.25f, Float.NaN, 9f, 10f, false),
			"non-finite key time must be blocked");
		editor.editKeyframe(precise, AnimationEditor.Prop.TRANSLATE, 0,
			2f, 3f, 4f, 5f, false);
		assertNear(3f, precise.getDuration(), "precise edit refreshes animation duration");
		assertNear(3f, doc.getCurrentDuration(), "active track follows precise edit duration");
		assertTrue(editor.addAttachmentKeyframe(precise, 0, 0.5f, "visible") == 0,
			"first attachment key index");
		assertTrue(editor.addAttachmentKeyframe(precise, 0, 2f, null) == 1,
			"second attachment key index");
		assertTrue(editor.editAttachmentKeyframe(precise, 0, 0.5f, 0.75f, "alternate") == 0,
			"attachment edit returns sorted index");
		assertThrows(IllegalArgumentException.class,
			() -> editor.editAttachmentKeyframe(precise, 0, 0.75f, 2f, "conflict"),
			"attachment move conflict must be blocked");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addAttachmentKeyframe(precise, 0, Float.NaN, "invalid"),
			"attachment time must be finite");
		Animation.AttachmentTimeline attachmentTimeline = editor.findAttachmentTimeline(precise, 0);
		assertNear(0.75f, editor.frameTime(attachmentTimeline, 0), "attachment edited time");
		assertEquals("alternate", editor.attachmentName(attachmentTimeline, 0), "attachment edited name");
		assertTrue(editor.attachmentName(attachmentTimeline, 1) == null, "attachment clear key");
		assertTrue(editor.addColorKeyframe(precise, 0, 0.25f, 1f, 0.5f, 0.25f, 1f) == 0,
			"first color key index");
		assertTrue(editor.addColorKeyframe(precise, 0, 2.5f, 0.1f, 0.2f, 0.3f, 0.4f) == 1,
			"second color key index");
		assertTrue(editor.editColorKeyframe(precise, 0, 0.25f, 0.6f,
			0.8f, 0.6f, 0.4f, 0.2f, true) == 0, "color edit returns sorted index");
		assertThrows(IllegalArgumentException.class,
			() -> editor.editColorKeyframe(precise, 0, 0.6f, 2.5f, 1f, 1f, 1f, 1f, false),
			"color move conflict must be blocked");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addColorKeyframe(precise, 0, 1f, 1.1f, 0f, 0f, 1f),
			"out-of-range color must be blocked");
		Animation.RGBATimeline colorTimeline = editor.findColorTimeline(precise, 0);
		assertColorFrame(editor, colorTimeline, 0, 0.6f, 0.8f, 0.6f, 0.4f, 0.2f);
		assertTrue(editor.curveType(colorTimeline, 0) == Animation.CurveTimeline.STEPPED,
			"color edit stores stepped interpolation");
		doc.dirty = false;
		long unchangedRevision = doc.revision;
		assertTrue(editor.editKeyframe(precise, AnimationEditor.Prop.TRANSLATE, 0,
			1.25f, 1.25f, 7f, 8f, true) == 1, "unchanged bone edit returns its frame index");
		assertTrue(editor.editAttachmentKeyframe(precise, 0, 0.75f, 0.75f, "alternate") == 0,
			"unchanged attachment edit returns its frame index");
		assertTrue(editor.editColorKeyframe(precise, 0, 0.6f, 0.6f,
			0.8f, 0.6f, 0.4f, 0.2f, true) == 0, "unchanged color edit returns its frame index");
		assertTrue(!doc.dirty, "unchanged keyframe edits do not dirty the document");
		assertTrue(doc.revision == unchangedRevision, "unchanged keyframe edits do not increment revision");

		precise.getColor().set(0.2f, 0.4f, 0.6f, 0.8f);
		Animation preciseCopy = editor.duplicateAnimation(precise);
		assertEquals("precise copy", preciseCopy.getName(), "duplicate animation name");
		assertTrue(preciseCopy.getColor().equals(precise.getColor()),
			"duplicate animation preserves its editor color");
		assertTrue(preciseCopy.getTimelines().first() != precise.getTimelines().first(),
			"duplicate owns independent timeline objects");
		editor.editKeyframe(preciseCopy, AnimationEditor.Prop.TRANSLATE, 0,
			1.25f, 1.5f, 70f, 80f, false);
		assertFrame(editor, precise, AnimationEditor.Prop.TRANSLATE, 1, 1.25f, 7f, 8f);
		assertFrame(editor, preciseCopy, AnimationEditor.Prop.TRANSLATE, 1, 1.5f, 70f, 80f);
		Animation.AttachmentTimeline copiedAttachments = findTimeline(preciseCopy, Animation.AttachmentTimeline.class);
		assertTrue(copiedAttachments != null && copiedAttachments != attachmentTimeline,
			"duplicate owns an independent attachment timeline");
		editor.editAttachmentKeyframe(preciseCopy, 0, 0.75f, 1f, "copy-only");
		assertEquals("alternate", attachmentTimeline.getAttachmentNames()[0],
			"editing duplicated attachments does not change source");
		assertTrue(editor.removeAttachmentKeyframe(preciseCopy, 0, 2f), "attachment key can be removed");
		assertTrue(copiedAttachments.getFrameCount() == 2, "stale copied timeline remains immutable after rebuild");
		assertTrue(editor.findAttachmentTimeline(preciseCopy, 0).getFrameCount() == 1,
			"attachment removal rebuilds copied timeline");
		assertTrue(attachmentTimeline.getFrameCount() == 2, "attachment removal does not affect source");
		Animation.RGBATimeline copiedColor = editor.findColorTimeline(preciseCopy, 0);
		assertTrue(copiedColor != null && copiedColor != colorTimeline,
			"duplicate owns an independent color timeline");
		editor.editColorKeyframe(preciseCopy, 0, 0.6f, 0.75f, 0f, 0f, 0f, 1f, false);
		assertColorFrame(editor, colorTimeline, 0, 0.6f, 0.8f, 0.6f, 0.4f, 0.2f);
		assertTrue(editor.removeColorKeyframe(preciseCopy, 0, 2.5f), "color key can be removed");
		assertTrue(editor.findColorTimeline(preciseCopy, 0).getFrameCount() == 1,
			"color removal rebuilds copied timeline");
		assertTrue(colorTimeline.getFrameCount() == 2, "color removal does not affect source");

		Animation missingDark = editor.addAnimation("missing-dark");
		assertThrows(IllegalStateException.class,
			() -> editor.addTwoColorKeyframe(missingDark, 0, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, true),
			"two color keys require a slot dark color");
		setDarkColor(doc.data.getSlots().first(), Color.BLACK);
		Animation twoColor = editor.addAnimation("two-color");
		assertTrue(editor.addTwoColorKeyframe(twoColor, 0, 0.25f,
			1f, 0.8f, 0.6f, 0.9f, 0.1f, 0.2f, 0.3f, true) == 0,
			"first RGBA2 key index");
		assertTrue(editor.addTwoColorKeyframe(twoColor, 0, 2.5f,
			0.4f, 0.5f, 0.6f, 0.7f, 0.7f, 0.6f, 0.5f, true) == 1,
			"second RGBA2 key index");
		assertTrue(editor.editTwoColorKeyframe(twoColor, 0, 0.25f, 0.6f,
			0.9f, 0.7f, 0.5f, 0.8f, 0.2f, 0.3f, 0.4f, true) == 0,
			"RGBA2 edit returns sorted index");
		SlotCurveTimeline twoColorTimeline = editor.findTwoColorTimeline(twoColor, 0);
		assertTrue(twoColorTimeline instanceof Animation.RGBA2Timeline, "new two color track includes alpha");
		assertTwoColorFrame(editor, twoColorTimeline, 0, 0.6f,
			0.9f, 0.7f, 0.5f, 0.8f, 0.2f, 0.3f, 0.4f);
		assertTrue(editor.curveType(twoColorTimeline, 0) == Animation.CurveTimeline.STEPPED,
			"RGBA2 edit stores stepped interpolation");
		assertThrows(IllegalArgumentException.class,
			() -> editor.editTwoColorKeyframe(twoColor, 0, 0.6f, 0.6f,
				1f, 1f, 1f, 1f, -0.1f, 0f, 0f, false),
			"invalid dark color rejects the complete key edit");
		assertTwoColorFrame(editor, twoColorTimeline, 0, 0.6f,
			0.9f, 0.7f, 0.5f, 0.8f, 0.2f, 0.3f, 0.4f);
		assertThrows(IllegalStateException.class,
			() -> editor.addColorKeyframe(twoColor, 0, 1f, 1f, 1f, 1f, 1f),
			"ordinary color track conflicts with RGBA2");
		assertThrows(IllegalStateException.class,
			() -> editor.addTwoColorKeyframe(precise, 0, 1f,
				1f, 1f, 1f, 1f, 0f, 0f, 0f, true),
			"RGBA2 track conflicts with ordinary color");
		doc.dirty = false;
		unchangedRevision = doc.revision;
		assertTrue(editor.editTwoColorKeyframe(twoColor, 0, 0.6f, 0.6f,
			0.9f, 0.7f, 0.5f, 0.8f, 0.2f, 0.3f, 0.4f, true) == 0,
			"unchanged RGBA2 edit returns its frame index");
		assertTrue(!doc.dirty && doc.revision == unchangedRevision,
			"unchanged RGBA2 edit is a clean no-op");

		AnimationEditor.TimelineSnapshot twoColorBefore = editor.snapshotTwoColorTimeline(twoColor, 0);
		editor.editTwoColorKeyframe(twoColor, 0, 0.6f, 0.75f,
			0f, 0f, 0f, 1f, 1f, 1f, 1f, false);
		AnimationEditor.TimelineSnapshot twoColorAfter = editor.snapshotTwoColorTimeline(twoColor, 0);
		editor.restoreTimeline(twoColorBefore);
		assertTwoColorFrame(editor, editor.findTwoColorTimeline(twoColor, 0), 0, 0.6f,
			0.9f, 0.7f, 0.5f, 0.8f, 0.2f, 0.3f, 0.4f);
		editor.restoreTimeline(twoColorAfter);
		assertTwoColorFrame(editor, editor.findTwoColorTimeline(twoColor, 0), 0, 0.75f,
			0f, 0f, 0f, 1f, 1f, 1f, 1f);
		Animation twoColorCopy = editor.duplicateAnimation(twoColor);
		SlotCurveTimeline copiedTwoColor = editor.findTwoColorTimeline(twoColorCopy, 0);
		assertTrue(copiedTwoColor != null && copiedTwoColor != editor.findTwoColorTimeline(twoColor, 0),
			"animation duplication owns an independent RGBA2 timeline");
		assertTrue(editor.removeTwoColorKeyframe(twoColorCopy, 0, 2.5f), "RGBA2 key can be removed");
		assertTrue(editor.findTwoColorTimeline(twoColorCopy, 0).getFrameCount() == 1,
			"RGBA2 key removal rebuilds the copied timeline");

		Animation rgb2Animation = editor.addAnimation("rgb2-import");
		Animation.RGB2Timeline rgb2 = new Animation.RGB2Timeline(2, 0, 0);
		rgb2.setFrame(0, 0f, 1f, 0.8f, 0.6f, 0.1f, 0.2f, 0.3f);
		rgb2.setFrame(1, 1f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f);
		Array<Animation.Timeline> rgb2Timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		rgb2Timelines.add(rgb2);
		rgb2Animation.setTimelines(rgb2Timelines, new com.badlogic.gdx.utils.IntArray());
		editor.editTwoColorKeyframe(rgb2Animation, 0, 0f, 0.2f,
			0.9f, 0.8f, 0.7f, 1f, 0.3f, 0.2f, 0.1f, false);
		SlotCurveTimeline editedRgb2 = editor.findTwoColorTimeline(rgb2Animation, 0);
		assertTrue(editedRgb2 instanceof Animation.RGB2Timeline, "editing imported RGB2 preserves its type");
		assertTwoColorFrame(editor, editedRgb2, 0, 0.2f,
			0.9f, 0.8f, 0.7f, 1f, 0.3f, 0.2f, 0.1f);
		Animation rgb2Copy = editor.duplicateAnimation(rgb2Animation);
		assertTrue(editor.findTwoColorTimeline(rgb2Copy, 0) instanceof Animation.RGB2Timeline,
			"animation duplication preserves imported RGB2 type");

		Animation snapshotAnimation = editor.addAnimation("snapshot");
		AnimationEditor.TimelineSnapshot absentShear = editor.snapshotBoneTimeline(snapshotAnimation,
			AnimationEditor.Prop.SHEAR, 0);
		editor.ensureTimeline(snapshotAnimation, AnimationEditor.Prop.SHEAR, 0);
		editor.addKeyframe(snapshotAnimation, AnimationEditor.Prop.SHEAR, 0, 1f, 3f, 4f);
		AnimationEditor.TimelineSnapshot presentShear = editor.snapshotBoneTimeline(snapshotAnimation,
			AnimationEditor.Prop.SHEAR, 0);
		assertTrue(!absentShear.sameAs(presentShear), "absent and present timeline snapshots differ");
		editor.restoreTimeline(absentShear);
		assertTrue(editor.findTimeline(snapshotAnimation, AnimationEditor.Prop.SHEAR, 0) == null,
			"restoring absent snapshot removes a newly created timeline");
		editor.restoreTimeline(presentShear);
		assertFrame(editor, snapshotAnimation, AnimationEditor.Prop.SHEAR, 1, 1f, 3f, 4f);

		AnimationEditor.TimelineSnapshot attachmentBefore = editor.snapshotAttachmentTimeline(precise, 0);
		editor.editAttachmentKeyframe(precise, 0, 0.75f, 1f, "snapshot-change");
		AnimationEditor.TimelineSnapshot attachmentAfter = editor.snapshotAttachmentTimeline(precise, 0);
		editor.restoreTimeline(attachmentBefore);
		assertEquals("alternate", editor.findAttachmentTimeline(precise, 0).getAttachmentNames()[0],
			"attachment snapshot restores value");
		editor.restoreTimeline(attachmentAfter);
		assertEquals("snapshot-change", editor.findAttachmentTimeline(precise, 0).getAttachmentNames()[0],
			"attachment snapshot reapplies value");

		AnimationEditor.TimelineSnapshot colorBefore = editor.snapshotColorTimeline(precise, 0);
		editor.editColorKeyframe(precise, 0, 0.6f, 0.9f, 0f, 0f, 0f, 1f, false);
		AnimationEditor.TimelineSnapshot colorAfter = editor.snapshotColorTimeline(precise, 0);
		editor.restoreTimeline(colorBefore);
		assertColorFrame(editor, editor.findColorTimeline(precise, 0), 0, 0.6f, 0.8f, 0.6f, 0.4f, 0.2f);
		editor.restoreTimeline(colorAfter);
		assertColorFrame(editor, editor.findColorTimeline(precise, 0), 0, 0.9f, 0f, 0f, 0f, 1f);

		Animation single = editor.addAnimation("single");
		editor.ensureTimeline(single, AnimationEditor.Prop.ROTATE, 0);
		editor.removeKeyframe(single, AnimationEditor.Prop.ROTATE, 0, 0f);
		assertTrue(editor.findTimeline(single, AnimationEditor.Prop.ROTATE, 0) == null,
			"removing the last key removes the empty timeline");

		doc.animationState.setAnimation(0, animation, false).setTrackTime(0.4f);
		doc.looping = false;
		animation.getColor().set(0.75f, 0.5f, 0.25f, 0.9f);
		SliderData slider = new SliderData("pose-slider");
		slider.setAnimation(animation);
		doc.data.getConstraints().add(slider);
		String renamed = editor.renameAnimation(animation, "renamed");
		assertEquals("renamed", renamed, "rename result");
		Animation renamedAnimation = doc.data.findAnimation("renamed");
		assertTrue(renamedAnimation.getColor().equals(animation.getColor()),
			"animation rename preserves its editor color");
		assertTrue(slider.getAnimation() == renamedAnimation, "slider follows renamed animation");
		assertTrue(!editor.removeAnimation(renamedAnimation), "slider reference blocks animation removal");
		assertEquals("renamed", doc.getCurrentAnimationName(), "current animation after rename");
		assertTrue(!doc.animationState.getTrack(0).getLoop(), "rename preserves loop state");
		assertNear(0.4f, doc.getCurrentTime(), "rename preserves time");
		doc.data.getConstraints().clear();

		Animation bezierAnimation = editor.addAnimation("bezier");
		var bezier = new Animation.RotateTimeline(2, 1, 0);
		bezier.setFrame(0, 0f, 0f);
		bezier.setFrame(1, 1f, 30f);
		bezier.setBezier(0, 0, 0, 0f, 0f, 0.25f, 0f, 0.75f, 30f, 1f, 30f);
		Array<Animation.Timeline> bezierTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		bezierTimelines.add(bezier);
		com.badlogic.gdx.utils.IntArray bezierBones = new com.badlogic.gdx.utils.IntArray();
		bezierBones.add(0);
		bezierAnimation.setTimelines(bezierTimelines, bezierBones);
		float bezierMidpoint = bezier.getCurveValue(0.5f);
		editor.addKeyframe(bezierAnimation, AnimationEditor.Prop.ROTATE, 0, 2f, 60f, 0f);
		bezier = findTimeline(bezierAnimation, Animation.RotateTimeline.class);
		assertTrue(bezier.getFrameCount() == 3 && editor.hasBezier(bezier),
			"Bezier timeline accepts an appended key");
		assertNear(bezierMidpoint, bezier.getCurveValue(0.5f),
			"appending a key preserves the unaffected Bezier segment");
		editor.removeKeyframe(bezierAnimation, AnimationEditor.Prop.ROTATE, 0, 2f);
		bezier = findTimeline(bezierAnimation, Animation.RotateTimeline.class);
		assertTrue(bezier.getFrameCount() == 2 && editor.hasBezier(bezier),
			"Bezier timeline accepts key deletion");
		assertNear(bezierMidpoint, bezier.getCurveValue(0.5f),
			"deleting an unrelated key preserves Bezier samples");
		editor.editKeyframe(bezierAnimation, AnimationEditor.Prop.ROTATE, 0,
			1f, 1.5f, 45f, 0f, false);
		bezier = findTimeline(bezierAnimation, Animation.RotateTimeline.class);
		assertTrue(editor.hasBezier(bezier), "moving a Bezier endpoint preserves its curve type");
		assertNear(45f, bezier.getCurveValue(1.5f), "moved Bezier endpoint keeps its edited value");
		editor.addKeyframe(bezierAnimation, AnimationEditor.Prop.ROTATE, 0, 0.75f, 20f, 0f);
		bezier = findTimeline(bezierAnimation, Animation.RotateTimeline.class);
		assertTrue(bezier.getFrameCount() == 3 && editor.hasBezier(bezier),
			"a key can be inserted inside a Bezier segment");
		editor.removeKeyframe(bezierAnimation, AnimationEditor.Prop.ROTATE, 0, 0.75f);
		bezier = findTimeline(bezierAnimation, Animation.RotateTimeline.class);
		assertTrue(bezier.getFrameCount() == 2 && editor.hasBezier(bezier),
			"removing an inserted key reconnects the outgoing Bezier curve");
		bone.getPose().setRotation(12f);
		editor.keyBonePose(bezierAnimation, bone, 0.5f);
		bezier = findTimeline(bezierAnimation, Animation.RotateTimeline.class);
		assertTrue(bezier.getFrameCount() == 3 && editor.hasBezier(bezier)
			&& editor.findTimeline(bezierAnimation, AnimationEditor.Prop.TRANSLATE, 0) != null,
			"record pose supports Bezier tracks and creates the remaining pose tracks");
		float snapshotBezierValue = bezier.getCurveValue(0.25f);

		AnimationEditor.TimelineSnapshot bezierBefore = editor.snapshotBoneTimeline(bezierAnimation,
			AnimationEditor.Prop.ROTATE, 0);
		editor.setCurve(bezier, 0, true);
		AnimationEditor.TimelineSnapshot bezierAfter = editor.snapshotBoneTimeline(bezierAnimation,
			AnimationEditor.Prop.ROTATE, 0);
		editor.restoreTimeline(bezierBefore);
		Animation.RotateTimeline restoredBezier = findTimeline(bezierAnimation, Animation.RotateTimeline.class);
		assertTrue(editor.hasBezier(restoredBezier), "Bezier snapshot restores curve type");
		assertNear(snapshotBezierValue, restoredBezier.getCurveValue(0.25f), "Bezier snapshot restores curve samples");
		editor.restoreTimeline(bezierAfter);
		assertTrue(editor.curveType(findTimeline(bezierAnimation, Animation.RotateTimeline.class), 0)
			== Animation.CurveTimeline.STEPPED, "stepped snapshot reapplies curve type");

		Animation duplicateBezier = editor.duplicateAnimation(bezierAnimation);
		Animation.RotateTimeline duplicatedBezier = findTimeline(duplicateBezier, Animation.RotateTimeline.class);
		assertTrue(editor.curveType(duplicatedBezier, 0) == Animation.CurveTimeline.STEPPED,
			"animation duplication preserves current curve data");
		editor.restoreTimeline(bezierBefore);
		Animation duplicateCurved = editor.duplicateAnimation(bezierAnimation);
		Animation.RotateTimeline duplicatedCurved = findTimeline(duplicateCurved, Animation.RotateTimeline.class);
		assertTrue(editor.hasBezier(duplicatedCurved), "animation duplication preserves Bezier curves");
		assertNear(snapshotBezierValue, duplicatedCurved.getCurveValue(0.25f), "duplicated Bezier samples");

		System.out.println("ALL ANIMATION EDITOR TESTS PASSED");
	}

	private static void keysOnlyCompletedTransformChannels() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		var bone = doc.skeleton.getRootBone();
		bone.getPose().setX(14f);
		bone.getPose().setY(26f);
		bone.getPose().setRotation(50f);
		bone.getPose().setScaleX(3f);
		bone.getPose().setScaleY(6f);
		bone.getPose().setShearX(12f);
		bone.getPose().setShearY(-4f);

		Animation rotate = editor.addAnimation("auto-rotate");
		editor.keyBoneTransform(rotate, bone, 0.5f, AnimationEditor.Prop.ROTATE, false, false, false);
		assertFrame(editor, rotate, AnimationEditor.Prop.ROTATE, 1, 0.5f, 20f, 20f);
		assertTrue(rotate.getTimelines().size == 1, "rotation Auto Key does not record the complete pose");

		Animation combinedMove = editor.addAnimation("auto-combined-move");
		assertProps(editor.transformKeyProps(combinedMove, 0, AnimationEditor.Prop.TRANSLATE_X,
			false, false, false), AnimationEditor.Prop.TRANSLATE);
		editor.keyBoneTransform(combinedMove, bone, 0.5f, AnimationEditor.Prop.TRANSLATE_X,
			false, false, false);
		assertFrame(editor, combinedMove, AnimationEditor.Prop.TRANSLATE, 1, 0.5f, 4f, 6f);
		assertTrue(combinedMove.getTimelines().size == 1,
			"one-axis editing in combined mode writes the current values of both axes");

		Animation separateMoveX = editor.addAnimation("auto-separated-move-x");
		assertProps(editor.transformKeyProps(separateMoveX, 0, AnimationEditor.Prop.TRANSLATE_X,
			true, false, false), AnimationEditor.Prop.TRANSLATE_X);
		editor.keyBoneTransform(separateMoveX, bone, 0.5f, AnimationEditor.Prop.TRANSLATE_X,
			true, false, false);
		assertFrame(editor, separateMoveX, AnimationEditor.Prop.TRANSLATE_X, 1, 0.5f, 4f, 4f);
		assertTrue(editor.findTimeline(separateMoveX, AnimationEditor.Prop.TRANSLATE_Y, 0) == null,
			"separated X editing does not create a Y timeline");

		Animation separateMoveFree = editor.addAnimation("auto-separated-move-free");
		assertProps(editor.transformKeyProps(separateMoveFree, 0, AnimationEditor.Prop.TRANSLATE,
			true, false, false), AnimationEditor.Prop.TRANSLATE_X, AnimationEditor.Prop.TRANSLATE_Y);
		editor.keyBoneTransform(separateMoveFree, bone, 0.5f, AnimationEditor.Prop.TRANSLATE,
			true, false, false);
		assertFrame(editor, separateMoveFree, AnimationEditor.Prop.TRANSLATE_X, 1, 0.5f, 4f, 4f);
		assertFrame(editor, separateMoveFree, AnimationEditor.Prop.TRANSLATE_Y, 1, 0.5f, 6f, 6f);

		Animation separateScale = editor.addAnimation("auto-separated-scale");
		editor.keyBoneTransform(separateScale, bone, 0.75f, AnimationEditor.Prop.SCALE_Y,
			false, true, false);
		assertFrame(editor, separateScale, AnimationEditor.Prop.SCALE_Y, 1, 0.75f, 2f, 2f);
		assertTrue(editor.findTimeline(separateScale, AnimationEditor.Prop.SCALE_X, 0) == null,
			"separated scale Y editing does not create scale X");

		Animation separateShear = editor.addAnimation("auto-separated-shear");
		Array<AnimationEditor.Prop> shearProps = editor.transformKeyProps(separateShear, 0,
			AnimationEditor.Prop.SHEAR, false, false, true);
		assertProps(shearProps, AnimationEditor.Prop.SHEAR_X, AnimationEditor.Prop.SHEAR_Y);
		AnimationEditor.TimelineSnapshot[] before = snapshotProps(editor, separateShear, shearProps);
		editor.keyBoneTransform(separateShear, bone, 1f, AnimationEditor.Prop.SHEAR,
			false, false, true);
		AnimationEditor.TimelineSnapshot[] after = snapshotCurrent(editor, before);
		EditHistory history = new EditHistory(doc);
		history.record("auto shear", () -> editor.restoreTimelines(before), () -> editor.restoreTimelines(after));
		assertTrue(history.undo(), "one Auto Key transform is undoable as one timeline operation");
		assertTrue(editor.findTimeline(separateShear, AnimationEditor.Prop.SHEAR_X, 0) == null
			&& editor.findTimeline(separateShear, AnimationEditor.Prop.SHEAR_Y, 0) == null,
			"Auto Key undo removes both timelines created by a free separated edit");
		assertTrue(history.redo(), "Auto Key transform redo succeeds");
		assertFrame(editor, separateShear, AnimationEditor.Prop.SHEAR_X, 1, 1f, 7f, 7f);
		assertFrame(editor, separateShear, AnimationEditor.Prop.SHEAR_Y, 1, 1f, 6f, 6f);

		Animation overlapping = editor.addAnimation("auto-overlapping");
		editor.ensureTimeline(overlapping, AnimationEditor.Prop.SCALE, 0);
		editor.ensureTimeline(overlapping, AnimationEditor.Prop.SCALE_X, 0);
		assertProps(editor.transformKeyProps(overlapping, 0, AnimationEditor.Prop.SCALE_X,
			false, false, false), AnimationEditor.Prop.SCALE, AnimationEditor.Prop.SCALE_X);
		assertProps(editor.transformKeyProps(overlapping, 0, AnimationEditor.Prop.SCALE_Y,
			false, false, false), AnimationEditor.Prop.SCALE);

		doc.dirty = false;
		editor.keyBoneTransform(combinedMove, bone, 0.5f, AnimationEditor.Prop.TRANSLATE_X,
			false, false, false);
		assertTrue(!doc.dirty, "re-keying an identical Auto Key value is a no-op");
	}

	private static void assertProps(Array<AnimationEditor.Prop> actual, AnimationEditor.Prop... expected) {
		assertTrue(actual.size == expected.length,
			"unexpected Auto Key property count: expected " + expected.length + ", got " + actual.size);
		for (int index = 0; index < expected.length; index++)
			assertTrue(actual.get(index) == expected[index],
				"unexpected Auto Key property at " + index + ": expected " + expected[index]
					+ ", got " + actual.get(index));
	}

	private static void editsSequenceTimelines() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		SlotData slot = new SlotData(0, "sequence-slot", root);
		slot.setBlendMode(BlendMode.normal);
		slot.setAttachmentName("sequence");
		data.getSlots().add(slot);
		Sequence attachmentSequence = new Sequence(4, true);
		RegionAttachment attachment = new RegionAttachment("sequence", attachmentSequence);
		MeshAttachment sourceMesh = new MeshAttachment("sequence-source", new Sequence(3, true));
		MeshAttachment inheritedMesh = new MeshAttachment("sequence-inherited", new Sequence(3, true));
		inheritedMesh.setSourceMesh(sourceMesh);
		inheritedMesh.setTimelineAttachment(sourceMesh);
		MeshAttachment independentMesh = new MeshAttachment("sequence-independent", new Sequence(3, true));
		independentMesh.setSourceMesh(sourceMesh);
		Skin skin = new Skin("default");
		skin.setAttachment(0, "sequence", attachment);
		skin.setAttachment(0, "sequence-source", sourceMesh);
		skin.setAttachment(0, "sequence-inherited", inheritedMesh);
		skin.setAttachment(0, "sequence-independent", independentMesh);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("sequence-animation");
		AnimationEditor.SequenceKeyframe hold = new AnimationEditor.SequenceKeyframe(SequenceMode.hold, 1, 0f);
		AnimationEditor.SequenceKeyframe loop = new AnimationEditor.SequenceKeyframe(SequenceMode.loop, 0, 0.125f);
		assertTrue(editor.addSequenceKeyframe(animation, 0, attachment, 0f, hold) == 0,
			"first Sequence key index");
		assertTrue(editor.addSequenceKeyframe(animation, 0, attachment, 1f, loop) == 1,
			"second Sequence key index");
		Animation.SequenceTimeline timeline = editor.findSequenceTimeline(animation, 0, attachment);
		assertTrue(timeline != null && editor.sequenceAt(timeline, 0).equals(hold),
			"Sequence hold value round trips through the data API");
		assertTrue(editor.sequenceAt(timeline, 1).equals(loop),
			"Sequence playback value round trips through the data API");
		Animation.SequenceTimeline malformed = new Animation.SequenceTimeline(1, 0, attachment);
		malformed.getFrames()[1] = -16f;
		assertThrows(IllegalArgumentException.class, () -> editor.sequenceAt(malformed, 0),
			"negative packed Sequence values are rejected instead of decoding to a huge index");

		long revision = doc.revision;
		assertTrue(editor.addSequenceKeyframe(animation, 0, attachment, 1f, loop) == 1,
			"unchanged Sequence add returns the existing frame");
		assertTrue(doc.revision == revision, "unchanged Sequence add is a no-op");
		AnimationEditor.SequenceKeyframe pingpong =
			new AnimationEditor.SequenceKeyframe(SequenceMode.pingpongReverse, 2, 0.2f);
		assertTrue(editor.editSequenceKeyframe(animation, 0, attachment, 1f, 0.5f, pingpong) == 1,
			"Sequence edit returns the sorted frame index");
		timeline = editor.findSequenceTimeline(animation, 0, attachment);
		assertNear(0.5f, editor.frameTime(timeline, 1), "Sequence edited time");
		assertTrue(editor.sequenceAt(timeline, 1).equals(pingpong), "Sequence edited value");

		AnimationEditor.TimelineSnapshot before = editor.snapshotSequenceTimeline(animation, 0, attachment);
		editor.editSequenceKeyframe(animation, 0, attachment, 0.5f, 0.75f, loop);
		editor.restoreTimeline(before);
		timeline = editor.findSequenceTimeline(animation, 0, attachment);
		assertNear(0.5f, editor.frameTime(timeline, 1), "Sequence snapshot restores time");
		assertTrue(editor.sequenceAt(timeline, 1).equals(pingpong), "Sequence snapshot restores value");

		Animation duplicate = editor.duplicateAnimation(animation);
		Animation.SequenceTimeline copied = editor.findSequenceTimeline(duplicate, 0, attachment);
		assertTrue(copied != null && copied != timeline,
			"animation duplication owns an independent Sequence timeline");
		assertTrue(editor.sequenceAt(copied, 1).equals(pingpong),
			"animation duplication preserves Sequence mode, index, and delay");
		assertTrue(editor.removeSequenceKeyframe(duplicate, 0, attachment, 0.5f),
			"Sequence key can be removed");
		assertTrue(editor.findSequenceTimeline(duplicate, 0, attachment).getFrameCount() == 1,
			"Sequence removal rebuilds the timeline");
		assertTrue(editor.removeSequenceKeyframe(duplicate, 0, attachment, 0f),
			"last Sequence key can be removed");
		assertTrue(editor.findSequenceTimeline(duplicate, 0, attachment) == null,
			"empty Sequence timeline is removed");

		assertThrows(IllegalArgumentException.class,
			() -> editor.editSequenceKeyframe(animation, 0, attachment, 0.5f, 0f, pingpong),
			"Sequence move conflict is rejected");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addSequenceKeyframe(animation, 0, attachment, 2f,
				new AnimationEditor.SequenceKeyframe(SequenceMode.loop, 0, 0f)),
			"animated Sequence mode requires a positive delay");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addSequenceKeyframe(animation, 0, attachment, 2f,
				new AnimationEditor.SequenceKeyframe(SequenceMode.hold, -1, 0f)),
			"Sequence index must be nonnegative");
		assertTrue(editor.addSequenceKeyframe(animation, 0, sourceMesh, 0f, hold) == 0,
			"source Mesh owns its Sequence timeline");
		assertTrue(editor.addSequenceKeyframe(animation, 0, independentMesh, 0f, hold) == 0,
			"independent Linked Mesh owns its Sequence timeline");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addSequenceKeyframe(animation, 0, inheritedMesh, 0f, hold),
			"inherited Linked Mesh cannot create a direct Sequence timeline");
		assertTrue(editor.findSequenceTimeline(animation, 0, inheritedMesh) == null,
			"rejected inherited Sequence edit leaves no invalid timeline");
	}

	private static void editsDeformTimelines() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		data.getBones().addAll(root, child);
		SlotData plainSlot = new SlotData(0, "plain-slot", root);
		SlotData weightedSlot = new SlotData(1, "weighted-slot", child);
		SlotData linkedSlot = new SlotData(2, "linked-slot", child);
		plainSlot.setBlendMode(BlendMode.normal);
		weightedSlot.setBlendMode(BlendMode.normal);
		linkedSlot.setBlendMode(BlendMode.normal);
		plainSlot.setAttachmentName("plain");
		weightedSlot.setAttachmentName("weighted");
		linkedSlot.setAttachmentName("linked");
		data.getSlots().addAll(plainSlot, weightedSlot, linkedSlot);

		MeshAttachment plain = new MeshAttachment("plain", new Sequence(1, false));
		plain.setWorldVerticesLength(8);
		plain.setVertices(new float[] {-2f, -1f, 2f, -1f, 2f, 1f, -2f, 1f});
		plain.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f});
		plain.setTriangles(new short[] {0, 1, 2, 2, 3, 0});
		plain.setHullLength(8);
		MeshAttachment weighted = new MeshAttachment("weighted", new Sequence(1, false));
		weighted.setWorldVerticesLength(4);
		weighted.setBones(new int[] {2, 0, 1, 1, 1});
		weighted.setVertices(new float[] {-2f, 1f, 0.25f, 3f, 4f, 0.75f, 6f, -5f, 1f});
		weighted.setRegionUVs(new float[] {0f, 0f, 1f, 1f});
		weighted.setTriangles(new short[] {0, 1, 0});
		weighted.setHullLength(4);
		Skin skin = new Skin("default");
		skin.setAttachment(0, "plain", plain);
		skin.setAttachment(1, "weighted", weighted);
		MeshAttachment linked = plain.newLinkedMesh();
		linked.setTimelineAttachment(plain);
		plain.setTimelineSlots(new int[] {2});
		skin.setAttachment(2, "linked", linked);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("deform");
		AnimationEditor.DeformTarget linkedTarget = editor.resolveDeformTarget(2, linked);
		assertTrue(linkedTarget.slotIndex() == 0 && linkedTarget.attachment() == plain,
			"cross-slot inherited Linked Mesh resolves to its JSON-addressable source Deform target");
		assertTrue(editor.currentDeformOffsets(2, plain).length == plain.getWorldVerticesLength(),
			"the displayed Linked Mesh slot can still provide a source-relative Deform preview");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addDeformKeyframe(animation, 2, plain, 0.25f, new float[8]),
			"a target-slot/source-attachment Deform timeline that JSON cannot address is rejected");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addDeformKeyframe(animation, 2, linked, 0.25f, new float[8]),
			"inherited Linked Mesh cannot create a direct Deform timeline");
		float[] zeroPlain = new float[8];
		float[] changedPlain = {1f, 0f, 0f, 2f, -1f, 0f, 0f, -2f};
		assertTrue(editor.addDeformKeyframe(animation, 0, plain, 0f, zeroPlain) == 0,
			"first unweighted Deform key index");
		assertTrue(editor.addDeformKeyframe(animation, 0, plain, 2f, changedPlain) == 1,
			"second unweighted Deform key index");
		Animation.DeformTimeline plainTimeline = editor.findDeformTimeline(animation, 0, plain);
		assertArray(plain.getVertices(), plainTimeline.getVertices()[0],
			"unweighted zero offsets are stored as setup vertices");
		assertArray(changedPlain, editor.deformOffsetsAt(plainTimeline, 1),
			"unweighted stored vertices are exposed as offsets");

		plainTimeline = (Animation.DeformTimeline)editor.setBezierCurve(animation, plainTimeline, 0,
			0.2f, 0.1f, 0.8f, 0.9f);
		assertTrue(editor.addDeformKeyframe(animation, 0, plain, 1f, new float[8]) == 1,
			"Deform insertion returns sorted index");
		plainTimeline = editor.findDeformTimeline(animation, 0, plain);
		AnimationEditor.BezierCurve insertedCurve = editor.bezierCurve(plainTimeline, 0, 0);
		assertNear(0.2f, insertedCurve.cx1(), "Deform insertion preserves Bezier x1");
		assertNear(0.1f, insertedCurve.cy1(), "Deform insertion preserves Bezier y1");
		plainTimeline = (Animation.DeformTimeline)editor.editCurveKey(animation, plainTimeline,
			1, 0, 1.1f, editor.curveFrameValue(plainTimeline, 1, 0));
		assertNear(1.1f, editor.frameTime(plainTimeline, 1),
			"Deform Graph can retime a key without changing vertex data");
		float[] graphSource = plainTimeline.getVertices()[1].clone();
		plainTimeline = (Animation.DeformTimeline)editor.insertCurveKey(animation, plainTimeline,
			1.5f, new float[] {0f});
		assertTrue(editor.findFrame(plainTimeline, 1.5f) == 2,
			"Deform Graph inserts a sorted sampled key");
		assertTrue(plainTimeline.getVertices()[2].length == graphSource.length,
			"Deform Graph insertion keeps the complete vertex channel");
		plainTimeline = (Animation.DeformTimeline)editor.removeCurveKeys(animation, plainTimeline,
			new boolean[] {false, false, true, false});
		assertTrue(editor.findFrame(plainTimeline, 1.5f) < 0,
			"Deform Graph removes selected keys");
		assertTrue(editor.editDeformKeyframe(animation, 0, plain, 1.1f, 0.75f,
			new float[] {0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f}, true) == 1,
			"Deform key edit returns sorted index");
		plainTimeline = editor.findDeformTimeline(animation, 0, plain);
		assertTrue(editor.curveType(plainTimeline, 1) == Animation.CurveTimeline.STEPPED,
			"Deform edit stores stepped interpolation");
		assertThrows(IllegalArgumentException.class,
			() -> editor.editDeformKeyframe(animation, 0, plain, 0.75f, 2f, zeroPlain, false),
			"Deform move conflict must be blocked");

		AnimationEditor.TimelineSnapshot before = editor.snapshotDeformTimeline(animation, 0, plain);
		editor.editDeformKeyframe(animation, 0, plain, 0.75f, 1.25f, changedPlain, false);
		editor.restoreTimeline(before);
		plainTimeline = editor.findDeformTimeline(animation, 0, plain);
		assertNear(0.75f, editor.frameTime(plainTimeline, 1), "Deform snapshot restores key time");
		assertTrue(editor.curveType(plainTimeline, 1) == Animation.CurveTimeline.STEPPED,
			"Deform snapshot restores curves");

		float[] weightedOffsets = {0f, 1f, -2f, 0f, 0f, 0.5f};
		assertTrue(editor.addDeformKeyframe(animation, 1, weighted, 0.5f, weightedOffsets) == 0,
			"weighted Deform key can be created");
		Animation.DeformTimeline weightedTimeline = editor.findDeformTimeline(animation, 1, weighted);
		assertArray(weightedOffsets, weightedTimeline.getVertices()[0],
			"weighted Deform offsets are stored directly");
		assertArray(weightedOffsets, editor.deformOffsetsAt(weightedTimeline, 0),
			"weighted Deform offsets round trip through the API");

		Animation duplicate = editor.duplicateAnimation(animation);
		Animation.DeformTimeline copied = editor.findDeformTimeline(duplicate, 0, plain);
		assertTrue(copied != null && copied != plainTimeline && copied.getVertices()[0] != plainTimeline.getVertices()[0],
			"duplicated Deform timeline owns independent arrays");
		editor.editDeformKeyframe(duplicate, 0, plain, 0f, 0f,
			new float[] {9f, 0f, 0f, 0f, 0f, 0f, 0f, 0f}, false);
		assertArray(zeroPlain, editor.deformOffsetsAt(plainTimeline, 0),
			"editing duplicated Deform does not change source");
		assertTrue(editor.removeDeformKeyframe(duplicate, 1, weighted, 0.5f),
			"last weighted Deform key can be removed with its track");
		assertTrue(editor.findDeformTimeline(duplicate, 1, weighted) == null,
			"empty weighted Deform track is removed");

		long revision = doc.revision;
		int existing = editor.addDeformKeyframe(animation, 1, weighted, 0.5f, weightedOffsets);
		assertTrue(existing == 0 && doc.revision == revision, "unchanged Deform add is a no-op");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addDeformKeyframe(animation, 0, plain, 3f, new float[2]),
			"Deform offset length must match the attachment");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addDeformKeyframe(animation, 1, plain, 3f, zeroPlain),
			"Deform attachment must belong to the target slot");
		int[] validBones = weighted.getBones();
		weighted.setBones(new int[] {1, 99, 1, 1});
		assertThrows(IllegalArgumentException.class,
			() -> editor.currentDeformOffsets(1, weighted),
			"weighted Deform target must reference existing bones");
		weighted.setBones(validBones);
	}

	private static void editsIkConstraintTimelines() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.getBones().first();
		BoneData target = new BoneData(1, "ik-target", root);
		doc.data.getBones().add(target);
		IkConstraintData constraint = new IkConstraintData("arm-ik");
		constraint.getBones().add(root);
		constraint.setTarget(target);
		constraint.getSetupPose().setMix(0.8f);
		constraint.getSetupPose().setSoftness(2f);
		constraint.getSetupPose().setBendDirection(1);
		doc.data.getConstraints().add(constraint);

		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("ik-animation");
		AnimationEditor.IkKeyframe first = new AnimationEditor.IkKeyframe(0.2f, 1f, 1, false, true);
		AnimationEditor.IkKeyframe second = new AnimationEditor.IkKeyframe(0.9f, 4f, -1, true, false);
		assertTrue(editor.addIkKeyframe(animation, 0, 0f, first) == 0, "first IK key index");
		assertTrue(editor.addIkKeyframe(animation, 0, 1f, second) == 1, "second IK key index");
		Animation.IkConstraintTimeline timeline = editor.findIkTimeline(animation, 0);
		assertTrue(timeline != null, "IK timeline is created");
		assertNear(0.9f, editor.ikAt(timeline, 1).mix(), "IK mix value");
		assertTrue(editor.ikAt(timeline, 1).bendDirection() == -1, "IK bend direction value");

		editor.setBezierCurve(animation, timeline, 0, 0.25f, 0.1f, 0.75f, 0.9f);
		timeline = editor.findIkTimeline(animation, 0);
		assertTrue(editor.hasBezier(timeline), "IK mix and softness support Bezier curves");
		assertTrue(editor.bezierCurve(timeline, 0, 0) != null, "IK mix Bezier channel is readable");
		assertTrue(editor.bezierCurve(timeline, 0, 1) != null, "IK softness Bezier channel is readable");
		AnimationEditor.CurveSnapshot ikCurve = editor.captureCurve(timeline, 0);
		Animation curveTargetAnimation = editor.addAnimation("ik-curve-target");
		editor.addIkKeyframe(curveTargetAnimation, 0, 0f, first);
		editor.addIkKeyframe(curveTargetAnimation, 0, 2f, second);
		Animation.IkConstraintTimeline curveTarget = editor.findIkTimeline(curveTargetAnimation, 0);
		curveTarget = (Animation.IkConstraintTimeline)editor.applyCurveSnapshot(
			curveTargetAnimation, curveTarget, 0, ikCurve);
		assertTrue(editor.bezierCurve(curveTarget, 0, 0) != null
			&& editor.bezierCurve(curveTarget, 0, 1) != null,
			"copied IK interpolation preserves both continuous channels despite discrete frame entries");

		AnimationEditor.IkKeyframe edited = new AnimationEditor.IkKeyframe(0.7f, 3f, 1, true, true);
		assertTrue(editor.editIkKeyframe(animation, 0, 1f, 1.25f, edited, false) == 1,
			"IK edit returns sorted frame index");
		timeline = editor.findIkTimeline(animation, 0);
		assertTrue(editor.hasBezier(timeline), "IK edit preserves Bezier data");
		assertTrue(editor.bezierCurve(timeline, 0, 0) != null, "IK mix Bezier survives edit");
		assertTrue(editor.bezierCurve(timeline, 0, 1) != null, "IK softness Bezier survives edit");
		assertTrue(editor.ikAt(timeline, 1).compress() && editor.ikAt(timeline, 1).stretch(),
			"IK discrete flags survive edit");

		AnimationEditor.TimelineSnapshot before = editor.snapshotIkTimeline(animation, 0);
		editor.editIkKeyframe(animation, 0, 1.25f, 1.5f,
			new AnimationEditor.IkKeyframe(0.5f, 0f, -1, false, false), true);
		editor.restoreTimeline(before);
		timeline = editor.findIkTimeline(animation, 0);
		assertNear(1.25f, editor.frameTime(timeline, 1), "IK snapshot restores edited time");
		assertTrue(editor.hasBezier(timeline), "IK snapshot restores Bezier data");

		Animation copy = editor.duplicateAnimation(animation);
		Animation.IkConstraintTimeline copied = editor.findIkTimeline(copy, 0);
		assertTrue(copied != null && copied != timeline, "animation duplication owns an independent IK timeline");
		assertTrue(editor.hasBezier(copied), "animation duplication preserves IK Bezier curves");
		assertTrue(editor.removeIkKeyframe(copy, 0, 1.25f), "IK key can be removed");
		assertTrue(editor.findIkTimeline(copy, 0).getFrameCount() == 1, "IK removal rebuilds the timeline");

		assertThrows(IllegalArgumentException.class,
			() -> editor.addIkKeyframe(animation, 1, 1.5f, first),
			"IK key rejects a missing constraint index");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addIkKeyframe(animation, 0, 1.5f,
				new AnimationEditor.IkKeyframe(1.5f, 0f, 1, false, false)),
			"IK mix rejects values outside 0 to 1");
	}

	private static void editsTransformConstraintTimelines() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.getBones().first();
		BoneData constrained = new BoneData(1, "transform-bone", root);
		constrained.getSetupPose().setInherit(Inherit.normal);
		doc.data.getBones().add(constrained);
		TransformConstraintData constraint = new TransformConstraintData("follow");
		constraint.getBones().add(constrained);
		constraint.setSource(root);
		doc.data.getConstraints().add(constraint);

		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("transform-animation");
		AnimationEditor.TransformKeyframe first = new AnimationEditor.TransformKeyframe(
			0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f);
		AnimationEditor.TransformKeyframe second = new AnimationEditor.TransformKeyframe(
			0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f);
		assertTrue(editor.addTransformKeyframe(animation, 0, 0f, first) == 0,
			"first Transform key index");
		assertTrue(editor.addTransformKeyframe(animation, 0, 1f, second) == 1,
			"second Transform key index");
		Animation.TransformConstraintTimeline timeline = editor.findTransformTimeline(animation, 0);
		assertTrue(timeline != null, "Transform timeline is created");
		assertNear(0.9f, editor.transformAt(timeline, 1).mixRotate(), "Transform rotate mix value");
		assertNear(0.4f, editor.transformAt(timeline, 1).mixShearY(), "Transform shear mix value");

		editor.setBezierCurve(animation, timeline, 0, 0.2f, 0.1f, 0.8f, 0.9f);
		timeline = editor.findTransformTimeline(animation, 0);
		assertTrue(editor.hasBezier(timeline), "Transform mixes support Bezier curves");
		for (int channel = 0; channel < 6; channel++)
			assertTrue(editor.bezierCurve(timeline, 0, channel) != null,
				"Transform Bezier channel " + channel + " is readable");

		AnimationEditor.TransformKeyframe edited = new AnimationEditor.TransformKeyframe(
			1.2f, -0.2f, 0.25f, 0.5f, 0.75f, 1.5f);
		assertTrue(editor.editTransformKeyframe(animation, 0, 1f, 1.25f, edited, false) == 1,
			"Transform edit returns sorted frame index");
		timeline = editor.findTransformTimeline(animation, 0);
		assertTrue(editor.hasBezier(timeline), "Transform edit preserves Bezier data");
		assertNear(1.5f, editor.transformAt(timeline, 1).mixShearY(),
			"Transform unbounded mixes survive edit");

		AnimationEditor.TimelineSnapshot before = editor.snapshotTransformTimeline(animation, 0);
		editor.editTransformKeyframe(animation, 0, 1.25f, 1.5f,
			new AnimationEditor.TransformKeyframe(0f, 0f, 0f, 0f, 0f, 0f), true);
		editor.restoreTimeline(before);
		timeline = editor.findTransformTimeline(animation, 0);
		assertNear(1.25f, editor.frameTime(timeline, 1), "Transform snapshot restores edited time");
		assertTrue(editor.hasBezier(timeline), "Transform snapshot restores Bezier data");

		Animation copy = editor.duplicateAnimation(animation);
		Animation.TransformConstraintTimeline copied = editor.findTransformTimeline(copy, 0);
		assertTrue(copied != null && copied != timeline,
			"animation duplication owns an independent Transform timeline");
		assertTrue(editor.hasBezier(copied), "animation duplication preserves Transform Bezier curves");
		assertTrue(editor.removeTransformKeyframe(copy, 0, 1.25f), "Transform key can be removed");
		assertTrue(editor.findTransformTimeline(copy, 0).getFrameCount() == 1,
			"Transform removal rebuilds the timeline");

		assertThrows(IllegalArgumentException.class,
			() -> editor.addTransformKeyframe(animation, 1, 1.5f, first),
			"Transform key rejects a missing constraint index");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addTransformKeyframe(animation, 0, 1.5f,
				new AnimationEditor.TransformKeyframe(Float.NaN, 0f, 0f, 0f, 0f, 0f)),
			"Transform key rejects non-finite mixes");
	}

	private static void editsPathConstraintTimelines() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.getBones().first();
		BoneData constrained = new BoneData(1, "path-bone", root);
		constrained.getSetupPose().setInherit(Inherit.normal);
		doc.data.getBones().add(constrained);
		PathConstraintData constraint = new PathConstraintData("follow-path");
		constraint.getBones().add(constrained);
		constraint.setSlot(doc.data.getSlots().first());
		constraint.setPositionMode(PathConstraintData.PositionMode.percent);
		constraint.setSpacingMode(PathConstraintData.SpacingMode.length);
		constraint.setRotateMode(PathConstraintData.RotateMode.chain);
		doc.data.getConstraints().add(constraint);

		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("path-animation");
		assertTrue(editor.addPathValueKeyframe(animation, 0, AnimationEditor.PathProp.POSITION,
			0f, 0f) == 0, "first Path position key index");
		assertTrue(editor.addPathValueKeyframe(animation, 0, AnimationEditor.PathProp.POSITION,
			1f, 25f) == 1, "second Path position key index");
		assertTrue(editor.addPathValueKeyframe(animation, 0, AnimationEditor.PathProp.SPACING,
			0f, 5f) == 0, "first Path spacing key index");
		assertTrue(editor.addPathValueKeyframe(animation, 0, AnimationEditor.PathProp.SPACING,
			1f, 15f) == 1, "second Path spacing key index");
		AnimationEditor.PathMixKeyframe firstMix = new AnimationEditor.PathMixKeyframe(0.2f, 0.3f, 0.4f);
		AnimationEditor.PathMixKeyframe secondMix = new AnimationEditor.PathMixKeyframe(0.8f, 0.7f, 0.6f);
		assertTrue(editor.addPathMixKeyframe(animation, 0, 0f, firstMix) == 0,
			"first Path mix key index");
		assertTrue(editor.addPathMixKeyframe(animation, 0, 1f, secondMix) == 1,
			"second Path mix key index");

		Animation.Timeline position = editor.findPathTimeline(animation, 0, AnimationEditor.PathProp.POSITION);
		Animation.Timeline spacing = editor.findPathTimeline(animation, 0, AnimationEditor.PathProp.SPACING);
		Animation.PathConstraintMixTimeline mix = (Animation.PathConstraintMixTimeline)
			editor.findPathTimeline(animation, 0, AnimationEditor.PathProp.MIX);
		assertTrue(position instanceof Animation.PathConstraintPositionTimeline,
			"Path position timeline is created");
		assertTrue(spacing instanceof Animation.PathConstraintSpacingTimeline,
			"Path spacing timeline is created");
		assertNear(25f, editor.pathValueAt(position, 1), "Path position value");
		assertNear(15f, editor.pathValueAt(spacing, 1), "Path spacing value");
		assertNear(0.7f, editor.pathMixAt(mix, 1).mixX(), "Path mix X value");

		editor.setBezierCurve(animation, position, 0, 0.2f, 0.1f, 0.8f, 0.9f);
		editor.setBezierCurve(animation, spacing, 0, 0.25f, 0.15f, 0.75f, 0.85f);
		editor.setBezierCurve(animation, mix, 0, 0.3f, 0.05f, 0.7f, 0.95f);
		position = editor.findPathTimeline(animation, 0, AnimationEditor.PathProp.POSITION);
		spacing = editor.findPathTimeline(animation, 0, AnimationEditor.PathProp.SPACING);
		mix = (Animation.PathConstraintMixTimeline)editor.findPathTimeline(
			animation, 0, AnimationEditor.PathProp.MIX);
		assertTrue(editor.bezierCurve(position, 0, 0) != null,
			"Path position Bezier channel is readable");
		assertTrue(editor.bezierCurve(spacing, 0, 0) != null,
			"Path spacing Bezier channel is readable");
		for (int channel = 0; channel < 3; channel++)
			assertTrue(editor.bezierCurve(mix, 0, channel) != null,
				"Path mix Bezier channel " + channel + " is readable");

		assertTrue(editor.editPathValueKeyframe(animation, 0, AnimationEditor.PathProp.POSITION,
			1f, 1.25f, 30f, false) == 1, "Path position edit returns sorted index");
		position = editor.findPathTimeline(animation, 0, AnimationEditor.PathProp.POSITION);
		assertNear(1.25f, editor.frameTime(position, 1), "Path position edited time");
		assertTrue(editor.bezierCurve(position, 0, 0) != null,
			"Path position edit preserves Bezier data");
		AnimationEditor.PathMixKeyframe editedMix = new AnimationEditor.PathMixKeyframe(1.2f, -0.2f, 0.5f);
		assertTrue(editor.editPathMixKeyframe(animation, 0, 1f, 1.5f, editedMix, false) == 1,
			"Path mix edit returns sorted index");
		mix = (Animation.PathConstraintMixTimeline)editor.findPathTimeline(
			animation, 0, AnimationEditor.PathProp.MIX);
		assertNear(-0.2f, editor.pathMixAt(mix, 1).mixX(), "Path unbounded mix survives edit");
		assertTrue(editor.bezierCurve(mix, 0, 2) != null, "Path mix edit preserves all Bezier channels");

		AnimationEditor.TimelineSnapshot before = editor.snapshotPathTimeline(
			animation, 0, AnimationEditor.PathProp.MIX);
		editor.editPathMixKeyframe(animation, 0, 1.5f, 1.75f,
			new AnimationEditor.PathMixKeyframe(0f, 0f, 0f), true);
		editor.restoreTimeline(before);
		mix = (Animation.PathConstraintMixTimeline)editor.findPathTimeline(
			animation, 0, AnimationEditor.PathProp.MIX);
		assertNear(1.5f, editor.frameTime(mix, 1), "Path snapshot restores edited time");
		assertTrue(editor.bezierCurve(mix, 0, 1) != null, "Path snapshot restores Bezier data");

		long revision = doc.revision;
		assertTrue(editor.addPathMixKeyframe(animation, 0, 1.5f, editedMix) == 1,
			"unchanged Path mix add returns existing index");
		assertTrue(doc.revision == revision, "unchanged Path mix add is a no-op");

		Animation copy = editor.duplicateAnimation(animation);
		Animation.Timeline copiedPosition = editor.findPathTimeline(copy, 0, AnimationEditor.PathProp.POSITION);
		Animation.PathConstraintMixTimeline copiedMix = (Animation.PathConstraintMixTimeline)
			editor.findPathTimeline(copy, 0, AnimationEditor.PathProp.MIX);
		assertTrue(copiedPosition != null && copiedPosition != position,
			"animation duplication owns an independent Path position timeline");
		assertTrue(copiedMix != null && copiedMix != mix,
			"animation duplication owns an independent Path mix timeline");
		assertTrue(editor.hasBezier(copiedPosition) && editor.hasBezier(copiedMix),
			"animation duplication preserves Path Bezier curves");
		assertTrue(editor.removePathValueKeyframe(copy, 0, AnimationEditor.PathProp.SPACING, 1f),
			"Path spacing key can be removed");
		assertTrue(editor.findPathTimeline(copy, 0, AnimationEditor.PathProp.SPACING).getFrameCount() == 1,
			"Path spacing removal rebuilds the timeline");
		assertTrue(editor.removePathMixKeyframe(copy, 0, 1.5f), "Path mix key can be removed");
		assertTrue(editor.findPathTimeline(copy, 0, AnimationEditor.PathProp.MIX).getFrameCount() == 1,
			"Path mix removal rebuilds the timeline");

		assertThrows(IllegalArgumentException.class,
			() -> editor.addPathValueKeyframe(animation, 1, AnimationEditor.PathProp.POSITION, 2f, 1f),
			"Path key rejects a missing constraint index");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addPathValueKeyframe(animation, 0, AnimationEditor.PathProp.MIX, 2f, 1f),
			"Path scalar edit rejects the mix property");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addPathMixKeyframe(animation, 0, 2f,
				new AnimationEditor.PathMixKeyframe(Float.NaN, 0f, 0f)),
			"Path mix rejects non-finite values");
	}

	private static void editsPhysicsConstraintTimelines() {
		SkeletonDocument doc = createDocument();
		PhysicsConstraintData constraint = new PhysicsConstraintData("swing");
		constraint.setBone(doc.data.getBones().first());
		doc.data.getConstraints().add(constraint);
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("physics-animation");
		assertTrue(!AnimationEditor.hasGlobalPhysicsProperty(doc.data, AnimationEditor.PhysicsProp.GRAVITY),
			"global Physics property requires an enabled setup flag");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addPhysicsValueKeyframe(animation, -1, AnimationEditor.PhysicsProp.GRAVITY,
				0.5f, -3f),
			"global Physics value key rejects a property with no effective target");
		constraint.setGravityGlobal(true);
		assertTrue(AnimationEditor.physicsPropertyIsGlobal(constraint, AnimationEditor.PhysicsProp.GRAVITY)
			&& AnimationEditor.hasGlobalPhysicsProperty(doc.data, AnimationEditor.PhysicsProp.GRAVITY),
			"global Physics target follows the runtime property flag");

		AnimationEditor.PhysicsProp[] numericProps = {
			AnimationEditor.PhysicsProp.INERTIA, AnimationEditor.PhysicsProp.STRENGTH,
			AnimationEditor.PhysicsProp.DAMPING, AnimationEditor.PhysicsProp.MASS,
			AnimationEditor.PhysicsProp.WIND, AnimationEditor.PhysicsProp.GRAVITY,
			AnimationEditor.PhysicsProp.MIX
		};
		for (int index = 0; index < numericProps.length; index++) {
			float first = numericProps[index] == AnimationEditor.PhysicsProp.MASS ? 1f : index * 0.1f;
			float second = numericProps[index] == AnimationEditor.PhysicsProp.MASS ? 2f : index * 0.1f + 0.5f;
			assertTrue(editor.addPhysicsValueKeyframe(animation, 0, numericProps[index], 0f, first) == 0,
				"first Physics " + numericProps[index] + " key index");
			assertTrue(editor.addPhysicsValueKeyframe(animation, 0, numericProps[index], 1f, second) == 1,
				"second Physics " + numericProps[index] + " key index");
			Animation.Timeline timeline = editor.findPhysicsTimeline(animation, 0, numericProps[index]);
			assertTrue(timeline != null && editor.physicsPropOf(timeline) == numericProps[index],
				"Physics " + numericProps[index] + " timeline is created");
			assertNear(second, editor.physicsValueAt(timeline, 1),
				"Physics " + numericProps[index] + " value");
		}
		assertTrue(editor.addPhysicsResetKeyframe(animation, 0, 0.25f) == 0,
			"first Physics reset key index");
		assertTrue(editor.addPhysicsResetKeyframe(animation, 0, 0.75f) == 1,
			"second Physics reset key index");
		assertTrue(editor.findPhysicsTimeline(animation, 0, AnimationEditor.PhysicsProp.RESET)
			instanceof Animation.PhysicsConstraintResetTimeline, "Physics reset timeline is created");

		Animation.Timeline inertia = editor.findPhysicsTimeline(animation, 0, AnimationEditor.PhysicsProp.INERTIA);
		editor.setBezierCurve(animation, inertia, 0, 0.2f, 0.1f, 0.8f, 0.9f);
		inertia = editor.findPhysicsTimeline(animation, 0, AnimationEditor.PhysicsProp.INERTIA);
		assertTrue(editor.hasBezier(inertia), "Physics values support Bezier curves");
		assertTrue(editor.editPhysicsValueKeyframe(animation, 0, AnimationEditor.PhysicsProp.INERTIA,
			1f, 1.25f, 0.9f, false) == 1, "Physics value edit returns sorted frame index");
		inertia = editor.findPhysicsTimeline(animation, 0, AnimationEditor.PhysicsProp.INERTIA);
		assertTrue(editor.hasBezier(inertia), "Physics value edit preserves Bezier data");
		assertTrue(editor.editPhysicsResetKeyframe(animation, 0, 0.75f, 1.5f) == 1,
			"Physics reset edit returns sorted frame index");

		assertTrue(editor.addPhysicsValueKeyframe(animation, -1, AnimationEditor.PhysicsProp.GRAVITY,
			0.5f, -3f) == 0, "global Physics value timeline is editable");
		assertTrue(editor.addPhysicsResetKeyframe(animation, -1, 0.6f) == 0,
			"global Physics reset timeline is editable");
		constraint.setGravityGlobal(false);
		assertThrows(IllegalArgumentException.class,
			() -> editor.editPhysicsValueKeyframe(animation, -1, AnimationEditor.PhysicsProp.GRAVITY,
				0.5f, 0.75f, -4f, false),
			"global Physics value edit rejects a target disabled outside the editor");
		constraint.setGravityGlobal(true);

		AnimationEditor.TimelineSnapshot before = editor.snapshotPhysicsTimeline(
			animation, 0, AnimationEditor.PhysicsProp.INERTIA);
		editor.editPhysicsValueKeyframe(animation, 0, AnimationEditor.PhysicsProp.INERTIA,
			1.25f, 1.75f, 1.2f, true);
		editor.restoreTimeline(before);
		inertia = editor.findPhysicsTimeline(animation, 0, AnimationEditor.PhysicsProp.INERTIA);
		assertNear(1.25f, editor.frameTime(inertia, 1), "Physics snapshot restores edited time");
		assertTrue(editor.hasBezier(inertia), "Physics snapshot restores Bezier data");

		Animation copy = editor.duplicateAnimation(animation);
		Animation.Timeline copiedInertia = editor.findPhysicsTimeline(copy, 0, AnimationEditor.PhysicsProp.INERTIA);
		assertTrue(copiedInertia != null && copiedInertia != inertia,
			"animation duplication owns an independent Physics value timeline");
		assertTrue(editor.hasBezier(copiedInertia), "animation duplication preserves Physics Bezier curves");
		assertTrue(editor.findPhysicsTimeline(copy, 0, AnimationEditor.PhysicsProp.RESET)
			instanceof Animation.PhysicsConstraintResetTimeline,
			"animation duplication preserves Physics reset timelines");
		assertTrue(editor.removePhysicsValueKeyframe(copy, 0, AnimationEditor.PhysicsProp.MASS, 1f),
			"Physics value key can be removed");
		assertTrue(editor.removePhysicsResetKeyframe(copy, 0, 1.5f), "Physics reset key can be removed");

		assertThrows(IllegalArgumentException.class,
			() -> editor.addPhysicsValueKeyframe(animation, 1, AnimationEditor.PhysicsProp.INERTIA, 2f, 1f),
			"Physics key rejects a missing constraint index");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addPhysicsValueKeyframe(animation, 0, AnimationEditor.PhysicsProp.RESET, 2f, 1f),
			"Physics scalar edit rejects Reset");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addPhysicsValueKeyframe(animation, 0, AnimationEditor.PhysicsProp.MASS, 2f, 0f),
			"Physics mass rejects zero");
	}

	private static void editsSliderConstraintTimelines() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation driven = editor.addAnimation("slider-driven");
		SliderData constraint = new SliderData("control");
		constraint.setAnimation(driven);
		doc.data.getConstraints().add(constraint);
		Animation animation = editor.addAnimation("slider-controller");

		for (AnimationEditor.SliderProp prop : AnimationEditor.SliderProp.values()) {
			assertTrue(editor.addSliderKeyframe(animation, 0, prop, 0f, 0.2f) == 0,
				"first Slider " + prop + " key index");
			assertTrue(editor.addSliderKeyframe(animation, 0, prop, 1f, 0.8f) == 1,
				"second Slider " + prop + " key index");
			Animation.Timeline timeline = editor.findSliderTimeline(animation, 0, prop);
			assertTrue(timeline != null && editor.sliderPropOf(timeline) == prop,
				"Slider " + prop + " timeline is created");
			assertNear(0.8f, editor.sliderValueAt(timeline, 1), "Slider " + prop + " value");
		}

		Animation.Timeline time = editor.findSliderTimeline(animation, 0, AnimationEditor.SliderProp.TIME);
		editor.setBezierCurve(animation, time, 0, 0.2f, 0.1f, 0.8f, 0.9f);
		time = editor.findSliderTimeline(animation, 0, AnimationEditor.SliderProp.TIME);
		assertTrue(editor.hasBezier(time), "Slider values support Bezier curves");
		assertTrue(editor.editSliderKeyframe(animation, 0, AnimationEditor.SliderProp.TIME,
			1f, 1.25f, 0.9f, false) == 1, "Slider edit returns sorted frame index");
		time = editor.findSliderTimeline(animation, 0, AnimationEditor.SliderProp.TIME);
		assertTrue(editor.hasBezier(time), "Slider value edit preserves Bezier data");

		AnimationEditor.TimelineSnapshot before = editor.snapshotSliderTimeline(
			animation, 0, AnimationEditor.SliderProp.TIME);
		editor.editSliderKeyframe(animation, 0, AnimationEditor.SliderProp.TIME,
			1.25f, 1.5f, 1.1f, true);
		editor.restoreTimeline(before);
		time = editor.findSliderTimeline(animation, 0, AnimationEditor.SliderProp.TIME);
		assertNear(1.25f, editor.frameTime(time, 1), "Slider snapshot restores edited time");
		assertNear(0.9f, editor.sliderValueAt(time, 1), "Slider snapshot restores edited value");
		assertTrue(editor.hasBezier(time), "Slider snapshot restores Bezier data");

		Animation copy = editor.duplicateAnimation(animation);
		Animation.Timeline copiedTime = editor.findSliderTimeline(copy, 0, AnimationEditor.SliderProp.TIME);
		Animation.Timeline copiedMix = editor.findSliderTimeline(copy, 0, AnimationEditor.SliderProp.MIX);
		assertTrue(copiedTime != null && copiedTime != time && copiedMix != null,
			"animation duplication owns independent Slider timelines");
		assertTrue(editor.hasBezier(copiedTime), "animation duplication preserves Slider Bezier curves");
		assertTrue(editor.removeSliderKeyframe(copy, 0, AnimationEditor.SliderProp.MIX, 1f),
			"Slider key can be removed");
		assertTrue(editor.findSliderTimeline(copy, 0, AnimationEditor.SliderProp.MIX).getFrameCount() == 1,
			"Slider removal rebuilds the timeline");

		assertThrows(IllegalArgumentException.class,
			() -> editor.addSliderKeyframe(animation, 1, AnimationEditor.SliderProp.TIME, 2f, 1f),
			"Slider key rejects a missing constraint index");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addSliderKeyframe(animation, 0, AnimationEditor.SliderProp.TIME, 2f, Float.NaN),
			"Slider key rejects non-finite values");
	}

	private static void rejectsPropertyDrivenSliderTimeEdits() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation driven = editor.addAnimation("property-driven-target");
		SliderData slider = new SliderData("property-driver");
		slider.setAnimation(driven);
		slider.setBone(doc.data.getBones().first());
		doc.data.getConstraints().add(slider);
		Animation controller = editor.addAnimation("property-driven-controller");

		assertThrows(IllegalArgumentException.class,
			() -> editor.addSliderKeyframe(controller, 0, AnimationEditor.SliderProp.TIME, 0f, 0.5f),
			"property-driven Slider rejects new time keys");
		assertTrue(editor.addSliderKeyframe(controller, 0, AnimationEditor.SliderProp.MIX, 0f, 0.5f) == 0,
			"property-driven Slider still accepts mix keys");

		Animation.SliderTimeline imported = new Animation.SliderTimeline(2, 0, 0);
		imported.setFrame(0, 0f, 0.25f);
		imported.setFrame(1, 1f, 0.75f);
		controller.getTimelines().add(imported);
		editor.refreshAnimation(controller);
		assertTrue(editor.findSliderTimeline(controller, 0, AnimationEditor.SliderProp.TIME) == imported,
			"an imported ineffective time track is retained");
		assertThrows(IllegalArgumentException.class,
			() -> editor.editSliderKeyframe(controller, 0, AnimationEditor.SliderProp.TIME,
				0f, 0f, 0.5f, false),
			"property-driven Slider rejects inspector time edits");
		assertThrows(IllegalArgumentException.class,
			() -> editor.editCurveKey(controller, imported, 0, 0, 0f, 0.5f),
			"property-driven Slider rejects Graph time edits");
		assertThrows(IllegalArgumentException.class,
			() -> editor.removeCurveKeys(controller, imported, new boolean[] {true, false}),
			"property-driven Slider rejects Graph time deletes");
		assertTrue(editor.findSliderTimeline(controller, 0, AnimationEditor.SliderProp.TIME) == imported
			&& imported.getFrameCount() == 2,
			"rejected Graph deletes preserve the imported time track");
		Animation copy = editor.duplicateAnimation(controller);
		assertTrue(editor.findSliderTimeline(copy, 0, AnimationEditor.SliderProp.TIME) != null,
			"duplicating an imported ineffective time track preserves its data");
	}

	private static void disablesLoopingSliderWhenTargetBecomesEmpty() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation target = editor.addAnimation("looping-slider-target");
		editor.ensureTimeline(target, AnimationEditor.Prop.ROTATE, 0);
		editor.addKeyframe(target, AnimationEditor.Prop.ROTATE, 0, 2f, 30f, 0f);
		SliderData slider = new SliderData("looping-slider");
		slider.setAnimation(target);
		slider.setLoop(true);
		doc.data.getConstraints().add(slider);

		Animation.Timeline timeline = editor.findTimeline(target, AnimationEditor.Prop.ROTATE, 0);
		AnimationEditor.TimelineSnapshot before = editor.snapshotBoneTimeline(target,
			AnimationEditor.Prop.ROTATE, 0);
		boolean[] removeAll = new boolean[timeline.getFrameCount()];
		java.util.Arrays.fill(removeAll, true);
		assertTrue(editor.removeCurveKeys(target, timeline, removeAll) == null,
			"deleting all keys still removes the target timeline");
		assertTrue(target.getTimelines().isEmpty(), "empty Slider targets do not retain deleted data");
		assertNear(0f, target.getDuration(), "empty Slider targets use their derived zero duration");
		assertTrue(!slider.getLoop(), "emptying a Slider target disables its invalid loop setting");

		AnimationEditor.TimelineSnapshot after = editor.snapshotCurrent(before);
		editor.restoreTimeline(before);
		assertTrue(editor.findTimeline(target, AnimationEditor.Prop.ROTATE, 0) != null,
			"undo restores an emptied Slider target timeline");
		assertNear(2f, target.getDuration(), "undo restores the Slider target duration");
		assertTrue(slider.getLoop(), "undo restores the Slider loop setting atomically");
		editor.restoreTimeline(after);
		assertTrue(target.getTimelines().isEmpty(), "redo empties the Slider target again");
		assertNear(0f, target.getDuration(), "redo restores the target's derived zero duration");
		assertTrue(!slider.getLoop(), "redo disables the invalid Slider loop again");

		String json = SkeletonExporter.toJson(doc);
		SkeletonData reloaded = new SkeletonJson(new StubAttachmentLoader())
			.readSkeletonData(new JsonReader().parse(json));
		SliderData reloadedSlider = (SliderData)reloaded.getConstraints().first();
		assertTrue(!reloadedSlider.getLoop(), "disabled Slider loops survive JSON export and reload");
		assertNear(0f, reloadedSlider.getAnimation().getDuration(),
			"reloaded empty Slider targets keep their derived zero duration");

		Animation ordinary = editor.addAnimation("ordinary-empty");
		editor.ensureTimeline(ordinary, AnimationEditor.Prop.ROTATE, 0);
		editor.addKeyframe(ordinary, AnimationEditor.Prop.ROTATE, 0, 2f, 30f, 0f);
		Animation.Timeline ordinaryTimeline = editor.findTimeline(ordinary, AnimationEditor.Prop.ROTATE, 0);
		boolean[] removeOrdinary = new boolean[ordinaryTimeline.getFrameCount()];
		java.util.Arrays.fill(removeOrdinary, true);
		editor.removeCurveKeys(ordinary, ordinaryTimeline, removeOrdinary);
		assertNear(0f, ordinary.getDuration(), "unreferenced empty animations still collapse to zero duration");
	}

	private static void editsDrawOrderTimelines() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.getBones().first();
		for (int index = 1; index < 3; index++) {
			SlotData slot = new SlotData(index, "slot-" + index, root);
			slot.setBlendMode(BlendMode.normal);
			doc.data.getSlots().add(slot);
		}
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("draw-order");
		SliderData slider = new SliderData("runtime-slider");
		slider.setAnimation(animation);
		doc.data.getConstraints().add(slider);
		Animation.DrawOrderFolderTimeline folder = new Animation.DrawOrderFolderTimeline(
			2, new int[] {0, 2}, doc.data.getSlots().size);
		folder.setFrame(0, 0.1f, null);
		folder.setFrame(1, 0.6f, new int[] {1, 0});
		animation.getTimelines().add(folder);
		Animation.SliderTimeline sliderTimeline = new Animation.SliderTimeline(2, 1, 0);
		sliderTimeline.setFrame(0, 0f, 0.2f);
		sliderTimeline.setFrame(1, 0.8f, 0.9f);
		sliderTimeline.setBezier(0, 0, 0, 0f, 0.2f, 0.2f, 0.1f, 0.6f, 1f, 0.8f, 0.9f);
		animation.getTimelines().add(sliderTimeline);
		animation.setTimelines(animation.getTimelines(), animation.getBones());
		assertTrue(editor.addDrawOrderKeyframe(animation, 0f, new int[] {0, 1, 2}) == 0,
			"setup draw order key index");
		Animation.DrawOrderTimeline timeline = editor.findDrawOrderTimeline(animation);
		assertTrue(timeline != null && editor.drawOrderAt(timeline, 0) == null,
			"identity draw order is normalized to setup order");

		int[] requested = {2, 0, 1};
		assertTrue(editor.addDrawOrderKeyframe(animation, 0.5f, requested) == 1,
			"changed draw order key index");
		requested[0] = 0;
		timeline = editor.findDrawOrderTimeline(animation);
		assertTrue(editor.drawOrderAt(timeline, 1)[0] == 2, "draw order owns an independent permutation");
		assertTrue(editor.addDrawOrderKeyframe(animation, 1f, new int[] {1, 2, 0}) == 2,
			"third draw order key index");

		assertTrue(editor.editDrawOrderKeyframe(animation, 0.5f, 1.25f, new int[] {2, 1, 0}) == 2,
			"draw order edit returns its sorted frame index");
		timeline = editor.findDrawOrderTimeline(animation);
		assertNear(1.25f, editor.frameTime(timeline, 2), "draw order edited time");
		assertTrue(editor.drawOrderAt(timeline, 2)[1] == 1, "draw order edited permutation");

		AnimationEditor.TimelineSnapshot before = editor.snapshotDrawOrderTimeline(animation);
		editor.editDrawOrderKeyframe(animation, 1.25f, 1.5f, new int[] {1, 0, 2});
		editor.restoreTimeline(before);
		timeline = editor.findDrawOrderTimeline(animation);
		assertNear(1.25f, editor.frameTime(timeline, 2), "draw order snapshot restores time");
		assertTrue(editor.drawOrderAt(timeline, 2)[0] == 2, "draw order snapshot restores permutation");

		Animation copy = editor.duplicateAnimation(animation);
		Animation.DrawOrderTimeline copied = editor.findDrawOrderTimeline(copy);
		assertTrue(copied != null && copied != timeline, "animation duplication owns an independent draw order timeline");
		Animation.DrawOrderFolderTimeline copiedFolder = findTimeline(
			copy, Animation.DrawOrderFolderTimeline.class);
		assertTrue(copiedFolder != null && copiedFolder != folder,
			"animation duplication owns an independent draw order folder timeline");
		assertTrue(copiedFolder.getSlots() != folder.getSlots(),
			"draw order folder copy owns its slot list");
		assertTrue(copiedFolder.getDrawOrders()[1] != folder.getDrawOrders()[1],
			"draw order folder copy owns its permutations");
		Animation.DrawOrderFolderTimeline liveFolder = editor.findDrawOrderFolderTimeline(
			animation, new int[] {0, 2});
		assertTrue(liveFolder == folder, "draw order folder can be addressed by its slot set");
		assertTrue(editor.addDrawOrderFolderKeyframe(animation, liveFolder, 0.35f,
			new int[] {1, 0}) == 1, "draw order folder key is inserted in sorted order");
		liveFolder = editor.findDrawOrderFolderTimeline(animation, new int[] {0, 2});
		assertTrue(editor.drawOrderFolderAt(liveFolder, 1)[0] == 1,
			"draw order folder stores a local permutation");
		AnimationEditor.TimelineSnapshot folderBefore =
			editor.snapshotDrawOrderFolderTimeline(animation, liveFolder);
		assertTrue(editor.editDrawOrderFolderKeyframe(animation, liveFolder, 0.35f, 0.4f,
			new int[] {0, 1}) == 1, "draw order folder edit returns its sorted frame");
		liveFolder = editor.findDrawOrderFolderTimeline(animation, new int[] {0, 2});
		assertTrue(editor.drawOrderFolderAt(liveFolder, 1) == null,
			"draw order folder setup permutation is normalized to null");
		editor.restoreTimeline(folderBefore);
		liveFolder = editor.findDrawOrderFolderTimeline(animation, new int[] {0, 2});
		assertNear(0.35f, editor.frameTime(liveFolder, 1),
			"draw order folder snapshot restores key time");
		assertTrue(editor.removeDrawOrderFolderKeyframe(animation, liveFolder, 0.35f),
			"draw order folder key can be removed");
		liveFolder = editor.findDrawOrderFolderTimeline(animation, new int[] {0, 2});
		assertTrue(liveFolder.getFrameCount() == 2,
			"draw order folder removal rebuilds only the local timeline");
		Animation.SliderTimeline copiedSlider = findTimeline(copy, Animation.SliderTimeline.class);
		assertTrue(copiedSlider != null && copiedSlider != sliderTimeline,
			"animation duplication owns an independent Slider timeline");
		assertTrue(copiedSlider.getCurveType(0) >= Animation.CurveTimeline.BEZIER,
			"animation duplication preserves Slider Bezier curves");
		int[] copiedOrder = editor.drawOrderAt(copied, 2);
		copiedOrder[0] = 0;
		assertTrue(editor.drawOrderAt(timeline, 2)[0] == 2, "draw order read returns an independent array");
		assertTrue(editor.removeDrawOrderKeyframe(copy, 1.25f), "draw order key can be removed");
		assertTrue(editor.findDrawOrderTimeline(copy).getFrameCount() == 2,
			"draw order removal rebuilds the duplicated timeline");

		assertThrows(IllegalArgumentException.class,
			() -> editor.addDrawOrderKeyframe(animation, 2f, new int[] {0, 1}),
			"draw order must contain every slot");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addDrawOrderKeyframe(animation, 2f, new int[] {0, 0, 2}),
			"draw order rejects duplicate slots");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addDrawOrderKeyframe(animation, 2f, new int[] {0, 1, 3}),
			"draw order rejects missing slot indices");
	}

	private static void editsSplitColorTimelines() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("split-color");
		assertTrue(editor.addRgbKeyframe(animation, 0, 0f, 1f, 0.5f, 0.25f) == 0,
			"first RGB key index");
		assertTrue(editor.addRgbKeyframe(animation, 0, 1f, 0.2f, 0.4f, 0.6f) == 1,
			"second RGB key index");
		assertTrue(editor.addAlphaKeyframe(animation, 0, 0f, 1f) == 0,
			"RGB and alpha tracks can coexist");
		assertTrue(editor.addAlphaKeyframe(animation, 0, 1f, 0.35f) == 1,
			"second alpha key index");
		Animation.RGBTimeline rgb = editor.findRgbTimeline(animation, 0);
		Animation.AlphaTimeline alpha = editor.findAlphaTimeline(animation, 0);
		assertTrue(rgb != null && alpha != null, "split color timelines are created independently");
		assertNear(0.4f, editor.rgbValue(rgb, 1, 1), "RGB channel value");
		assertNear(0.35f, editor.alphaValue(alpha, 1), "alpha channel value");

		editor.setBezierCurve(animation, rgb, 0, 0.3f, 0.1f, 0.7f, 0.9f);
		editor.setBezierCurve(animation, alpha, 0, 0.2f, 0f, 0.8f, 1f);
		assertTrue(editor.hasBezier(editor.findRgbTimeline(animation, 0)), "RGB supports Bezier curves");
		assertTrue(editor.hasBezier(editor.findAlphaTimeline(animation, 0)), "alpha supports Bezier curves");
		assertTrue(editor.editRgbKeyframe(animation, 0, 1f, 1.25f, 0.7f, 0.6f, 0.5f, false) == 1,
			"RGB edit returns sorted index");
		assertTrue(editor.editAlphaKeyframe(animation, 0, 1f, 1.25f, 0.2f, true) == 1,
			"alpha edit returns sorted index");
		assertTrue(editor.hasBezier(editor.findRgbTimeline(animation, 0)), "RGB edit preserves Bezier data");
		assertTrue(editor.hasBezier(editor.findAlphaTimeline(animation, 0)), "alpha edit preserves Bezier data");

		AnimationEditor.TimelineSnapshot rgbBefore = editor.snapshotRgbTimeline(animation, 0);
		AnimationEditor.TimelineSnapshot alphaBefore = editor.snapshotAlphaTimeline(animation, 0);
		editor.editRgbKeyframe(animation, 0, 1.25f, 1.5f, 0f, 0f, 0f, false);
		editor.editAlphaKeyframe(animation, 0, 1.25f, 1.5f, 0f, false);
		editor.restoreTimelines(rgbBefore, alphaBefore);
		assertNear(1.25f, editor.frameTime(editor.findRgbTimeline(animation, 0), 1), "RGB snapshot restores time");
		assertNear(0.2f, editor.alphaValue(editor.findAlphaTimeline(animation, 0), 1),
			"alpha snapshot restores value");

		Animation copy = editor.duplicateAnimation(animation);
		assertTrue(editor.findRgbTimeline(copy, 0) != editor.findRgbTimeline(animation, 0),
			"animation duplication owns an independent RGB timeline");
		assertTrue(editor.findAlphaTimeline(copy, 0) != editor.findAlphaTimeline(animation, 0),
			"animation duplication owns an independent alpha timeline");
		assertTrue(editor.removeRgbKeyframe(copy, 0, 1.25f), "RGB key can be removed");
		assertTrue(editor.removeAlphaKeyframe(copy, 0, 1.25f), "alpha key can be removed");
		assertTrue(editor.findRgbTimeline(copy, 0).getFrameCount() == 1, "RGB removal rebuilds timeline");
		assertTrue(editor.findAlphaTimeline(copy, 0).getFrameCount() == 1, "alpha removal rebuilds timeline");

		assertThrows(IllegalStateException.class,
			() -> editor.addColorKeyframe(animation, 0, 2f, 1f, 1f, 1f, 1f),
			"RGBA conflicts with split color timelines");
		Animation rgbaAnimation = editor.addAnimation("rgba-conflict");
		editor.addColorKeyframe(rgbaAnimation, 0, 0f, 1f, 1f, 1f, 1f);
		assertThrows(IllegalStateException.class,
			() -> editor.addRgbKeyframe(rgbaAnimation, 0, 1f, 1f, 1f, 1f), "RGB conflicts with RGBA");
		assertThrows(IllegalStateException.class,
			() -> editor.addAlphaKeyframe(rgbaAnimation, 0, 1f, 1f), "alpha conflicts with RGBA");

		setDarkColor(doc.data.getSlots().first(), Color.BLACK);
		Animation rgb2Animation = editor.addAnimation("rgb2-alpha");
		editor.addTwoColorKeyframe(rgb2Animation, 0, 0f,
			1f, 1f, 1f, 1f, 0f, 0f, 0f, false);
		assertTrue(editor.addAlphaKeyframe(rgb2Animation, 0, 0f, 0.8f) == 0,
			"RGB2 can coexist with a separate alpha track");
		Animation rgba2Animation = editor.addAnimation("rgba2-alpha-conflict");
		editor.addTwoColorKeyframe(rgba2Animation, 0, 0f,
			1f, 1f, 1f, 1f, 0f, 0f, 0f, true);
		assertThrows(IllegalStateException.class,
			() -> editor.addAlphaKeyframe(rgba2Animation, 0, 1f, 0.5f), "alpha conflicts with RGBA2");
	}

	private static void editsInheritTimelines() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("inherit");
		assertTrue(editor.addInheritKeyframe(animation, 0, 0.5f, Inherit.noScale) == 0,
			"first inherit key index");
		assertTrue(editor.addInheritKeyframe(animation, 0, 1f, Inherit.onlyTranslation) == 1,
			"second inherit key index");
		assertTrue(editor.addInheritKeyframe(animation, 0, 0.5f, Inherit.noRotationOrReflection) == 0,
			"same-time inherit key is replaced");
		Animation.InheritTimeline timeline = editor.findInheritTimeline(animation, 0);
		assertTrue(timeline != null && timeline.getFrameCount() == 2, "inherit timeline is created");
		assertTrue(editor.inheritAt(timeline, 0) == Inherit.noRotationOrReflection,
			"inherit replacement stores the requested mode");
		assertTrue(editor.editInheritKeyframe(animation, 0, 0.5f, 0.75f,
			Inherit.noScaleOrReflection) == 0, "inherit key edit returns sorted index");
		timeline = editor.findInheritTimeline(animation, 0);
		assertNear(0.75f, editor.frameTime(timeline, 0), "inherit edited time");
		assertTrue(editor.inheritAt(timeline, 0) == Inherit.noScaleOrReflection, "inherit edited mode");
		assertThrows(IllegalArgumentException.class,
			() -> editor.editInheritKeyframe(animation, 0, 0.75f, 1f, Inherit.normal),
			"inherit move conflict must be blocked");

		doc.dirty = false;
		long unchangedRevision = doc.revision;
		assertTrue(editor.editInheritKeyframe(animation, 0, 0.75f, 0.75f,
			Inherit.noScaleOrReflection) == 0, "unchanged inherit edit returns its frame index");
		assertTrue(!doc.dirty && doc.revision == unchangedRevision, "unchanged inherit edit is a clean no-op");

		AnimationEditor.TimelineSnapshot before = editor.snapshotInheritTimeline(animation, 0);
		editor.editInheritKeyframe(animation, 0, 0.75f, 0.8f, Inherit.normal);
		AnimationEditor.TimelineSnapshot after = editor.snapshotInheritTimeline(animation, 0);
		editor.restoreTimeline(before);
		assertTrue(editor.inheritAt(editor.findInheritTimeline(animation, 0), 0) == Inherit.noScaleOrReflection,
			"inherit snapshot restores mode");
		editor.restoreTimeline(after);
		assertTrue(editor.inheritAt(editor.findInheritTimeline(animation, 0), 0) == Inherit.normal,
			"inherit snapshot reapplies mode");

		Animation copy = editor.duplicateAnimation(animation);
		Animation.InheritTimeline copied = editor.findInheritTimeline(copy, 0);
		assertTrue(copied != null && copied != editor.findInheritTimeline(animation, 0),
			"animation duplication owns an independent inherit timeline");
		assertTrue(editor.removeInheritKeyframe(copy, 0, 1f), "inherit key can be removed");
		assertTrue(editor.findInheritTimeline(copy, 0).getFrameCount() == 1,
			"inherit removal rebuilds the duplicated timeline");
		assertTrue(editor.removeInheritKeyframe(copy, 0, 0.8f), "last inherit key can be removed");
		assertTrue(editor.findInheritTimeline(copy, 0) == null, "empty inherit timeline is removed");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addInheritKeyframe(animation, 0, Float.NaN, Inherit.normal),
			"inherit time must be finite");
		assertThrows(IllegalArgumentException.class,
			() -> editor.addInheritKeyframe(animation, 0, 2f, null), "inherit mode is required");
	}

	private static void editsEventTimelinesAndDefinitions() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("events");

		EventData step = editor.addEventDefinition("step");
		step = editor.updateEventDefinition(step, "step", 1, 0.5f, "left", "step.wav", 0.8f, -0.1f);
		EventData marker = editor.addEventDefinition("marker");
		marker = editor.updateEventDefinition(marker, "marker", 0, 2f, "", "", 0f, 0f);
		EventData spacedAudio = editor.addEventDefinition("spaced-audio");
		spacedAudio = editor.updateEventDefinition(spacedAudio, "spaced-audio", 0, 0f, "",
			" audio/step.wav ", 1f, 0f);
		assertTrue(" audio/step.wav ".equals(spacedAudio.getAudioPath()),
			"event definition edits preserve audio paths without trimming");
		int first = editor.addEventKeyframe(animation, 0.5f, step, 2, 0.75f, "right", 0.7f, 0.2f);
		int sameTime = editor.addEventKeyframe(animation, 0.5f, marker, 7, -1f, "same", 0f, 0f);
		int last = editor.addEventKeyframe(animation, 1f, step, 1, 0.5f, "left", 0.8f, -0.1f);
		assertTrue(first == 0 && sameTime == 1 && last == 2, "same-time events retain insertion order");

		Animation.EventTimeline timeline = editor.findEventTimeline(animation);
		assertTrue(timeline != null && timeline.getFrameCount() == 3, "event timeline is created");
		assertTrue(editor.eventAt(timeline, 0).getData() == step && editor.eventAt(timeline, 1).getData() == marker,
			"same-time events are independently addressable");
		int moved = editor.editEventKeyframe(animation, 1, 0.25f, marker, 9, -2f, "moved", 0f, 0f);
		assertTrue(moved == 0, "event editing returns its sorted frame index");
		timeline = editor.findEventTimeline(animation);
		assertNear(0.25f, editor.eventAt(timeline, 0).getTime(), "event move time");
		assertTrue(editor.eventAt(timeline, 0).getInt() == 9, "event payload is edited atomically");
		EditHistory history = new EditHistory(doc);
		AnimationEditor.TimelineSnapshot beforePayload = editor.snapshotEventTimeline(animation);
		editor.editEventKeyframe(animation, 0, 0.25f, marker, 10, -2f, "moved", 0f, 0f);
		AnimationEditor.TimelineSnapshot afterPayload = editor.snapshotCurrent(beforePayload);
		assertTrue(!beforePayload.sameAs(afterPayload), "event snapshots compare payload values");
		history.record("edit event", () -> editor.restoreTimeline(beforePayload),
			() -> editor.restoreTimeline(afterPayload));
		assertTrue(history.undo() && editor.eventAt(editor.findEventTimeline(animation), 0).getInt() == 9,
			"shared history undoes an event payload edit");
		assertTrue(history.redo() && editor.eventAt(editor.findEventTimeline(animation), 0).getInt() == 10,
			"shared history redoes an event payload edit");

		AnimationEditor.TimelineSnapshot snapshot = editor.snapshotEventTimeline(animation);
		assertTrue(editor.removeEventKeyframe(animation, 0), "event key can be removed");
		editor.restoreTimeline(snapshot);
		timeline = editor.findEventTimeline(animation);
		assertTrue(timeline.getFrameCount() == 3 && editor.eventAt(timeline, 0).getInt() == 10,
			"event snapshot restores payload and order");

		Animation duplicate = editor.duplicateAnimation(animation);
		Animation.EventTimeline duplicatedTimeline = editor.findEventTimeline(duplicate);
		assertTrue(duplicatedTimeline != timeline && duplicatedTimeline.getFrameCount() == timeline.getFrameCount(),
			"animation duplication creates an independent event timeline");
		assertTrue(editor.eventAt(duplicatedTimeline, 1).getData() == step,
			"duplicated event keys share the skeleton event definition");

		step = editor.updateEventDefinition(step, "footstep", 1, 0.5f, "left", "step.wav", 0.8f, -0.1f);
		assertTrue(doc.data.findEvent("step") == null && doc.data.findEvent("footstep") == step,
			"event definition rename replaces the skeleton definition");
		assertTrue(editor.eventAt(editor.findEventTimeline(animation), 1).getData() == step
			&& editor.eventAt(editor.findEventTimeline(duplicate), 1).getData() == step,
			"definition rename updates every animation reference");
		long revision = doc.revision;
		EventData unchanged = editor.updateEventDefinition(step, "footstep", 1, 0.5f, "left",
			"step.wav", 0.8f, -0.1f);
		assertTrue(unchanged == step && doc.revision == revision, "unchanged event definition is a clean no-op");
		assertTrue(editor.getEventDeleteBlockReason(step) != null && !editor.removeEventDefinition(step),
			"referenced event definition cannot be deleted");

		for (Animation candidate : new Animation[] {animation, duplicate}) {
			for (int frame = editor.findEventTimeline(candidate).getFrameCount() - 1; frame >= 0; frame--) {
				if (editor.eventAt(editor.findEventTimeline(candidate), frame).getData() == step)
					editor.removeEventKeyframe(candidate, frame);
			}
		}
		assertTrue(editor.removeEventDefinition(step), "unreferenced event definition can be deleted");
		EventData retainedMarker = marker;
		assertThrows(IllegalArgumentException.class,
			() -> editor.addEventKeyframe(animation, Float.NaN, retainedMarker, 0, 0f, "", 0f, 0f),
			"event key time must be finite");
	}

	private static void editsBezierControlPoints() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("bezier-controls");
		editor.ensureTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE, 0, 1f, 10f, 20f);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE, 0, 2f, 30f, 50f);
		Animation.Timeline timeline = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0);
		assertTrue(editor.bezierCurve(timeline, 0, 0) == null, "linear segment has no Bezier handles");

		timeline = editor.setBezierCurve(animation, timeline, 1, 0.4f, 0.1f, 0.6f, 0.9f);
		AnimationEditor.BezierCurve laterCurve = editor.bezierCurve(timeline, 1, 0);
		float laterSample = bezierValue(timeline, 1, 1.5f, 0);
		timeline = editor.setBezierCurve(animation, timeline, 0, 0.2f, -0.25f, 0.8f, 1.25f);
		assertTrue(editor.curveType(timeline, 0) >= Animation.CurveTimeline.BEZIER,
			"Bezier control edit changes the outgoing curve type");
		for (int channel = 0; channel < 2; channel++) {
			AnimationEditor.BezierCurve curve = editor.bezierCurve(timeline, 0, channel);
			assertNear(0.2f, curve.cx1(), "Bezier first control time channel " + channel);
			assertNear(-0.25f, curve.cy1(), "Bezier first control value channel " + channel);
			assertNear(0.8f, curve.cx2(), "Bezier second control time channel " + channel);
			assertNear(1.25f, curve.cy2(), "Bezier second control value channel " + channel);
		}
		AnimationEditor.BezierCurve preservedLater = editor.bezierCurve(timeline, 1, 0);
		assertNear(laterCurve.cx1(), preservedLater.cx1(), "later Bezier first control is preserved");
		assertNear(laterCurve.cy2(), preservedLater.cy2(), "later Bezier second control is preserved");
		assertNear(laterSample, bezierValue(timeline, 1, 1.5f, 0),
			"later Bezier samples are preserved exactly");

		AnimationEditor.BezierCurve preservedX = editor.bezierCurve(timeline, 0, 0);
		float preservedXSample = bezierValue(timeline, 0, 0.5f, 0);
		timeline = editor.setBezierCurveChannel(animation, timeline, 0, 1, 0.15f, 0.35f, 0.7f, 0.8f);
		AnimationEditor.BezierCurve editedY = editor.bezierCurve(timeline, 0, 1);
		assertNear(0.15f, editedY.cx1(), "single-channel edit updates selected first control time");
		assertNear(0.35f, editedY.cy1(), "single-channel edit updates selected first control value");
		assertNear(0.7f, editedY.cx2(), "single-channel edit updates selected second control time");
		assertNear(0.8f, editedY.cy2(), "single-channel edit updates selected second control value");
		AnimationEditor.BezierCurve unchangedX = editor.bezierCurve(timeline, 0, 0);
		assertNear(preservedX.cx1(), unchangedX.cx1(), "single-channel edit preserves other channel handles");
		assertNear(preservedX.cy1(), unchangedX.cy1(), "single-channel edit preserves other channel values");
		assertNear(preservedXSample, bezierValue(timeline, 0, 0.5f, 0),
			"single-channel edit preserves other channel samples");
		float originalYShape = bezierValue(timeline, 0, 0.5f, 1);
		timeline = editor.editCurveKey(animation, timeline, 1, 1, 1.1f, 24f);
		assertNear(1.1f, editor.frameTime(timeline, 1), "Graph edit moves the shared key time");
		assertNear(24f, editor.curveFrameValue(timeline, 1, 1), "Graph edit changes the selected channel value");
		assertNear(10f, editor.curveFrameValue(timeline, 1, 0), "Graph edit preserves other channel values");
		assertTrue(editor.bezierCurve(timeline, 0, 0) != null && editor.bezierCurve(timeline, 0, 1) != null,
			"Graph edit preserves per-channel Bezier handles");
		assertTrue(Float.isFinite(originalYShape), "Graph source sample is finite");

		doc.dirty = false;
		AnimationEditor.BezierCurve currentY = editor.bezierCurve(timeline, 0, 1);
		Animation.Timeline unchanged = editor.setBezierCurveChannel(animation, timeline, 0, 1,
			currentY.cx1(), currentY.cy1(), currentY.cx2(), currentY.cy2());
		assertTrue(unchanged == timeline && !doc.dirty, "reapplying identical Bezier handles is a no-op");
		Animation.Timeline liveTimeline = timeline;
		assertThrows(IllegalArgumentException.class,
			() -> editor.setBezierCurve(animation, liveTimeline, 0, -0.01f, 0f, 0.8f, 1f),
			"Bezier X controls outside the normalized range are rejected");
		assertThrows(IllegalArgumentException.class,
			() -> editor.setBezierCurve(animation, liveTimeline, 0, 0.2f, Float.NaN, 0.8f, 1f),
			"non-finite Bezier controls are rejected");
		assertThrows(IllegalArgumentException.class,
			() -> editor.setBezierCurve(animation, liveTimeline, liveTimeline.getFrameCount() - 1,
				0.2f, 0f, 0.8f, 1f),
			"terminal keys cannot create an outgoing Bezier curve");
	}

	private static void preservesUneditedBezierChannelSamples() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("returning-channel");
		Animation.TranslateTimeline source = new Animation.TranslateTimeline(2, 2, 0);
		source.setFrame(0, 0f, 4f, 3f);
		source.setFrame(1, 1f, 4f, 20f);
		source.setBezier(0, 0, 0, 0f, 4f, 0.2f, 40f, 0.7f, -8f, 1f, 4f);
		source.setBezier(1, 0, 1, 0f, 3f, 0.3f, 3f, 0.8f, 20f, 1f, 20f);
		animation.setTimelines(new Array<>(new Animation.Timeline[] {source}),
			new com.badlogic.gdx.utils.IntArray());
		AnimationEditor.TimelineSnapshot before = editor.snapshotBoneTimeline(animation,
			AnimationEditor.Prop.TRANSLATE, 0);
		Animation.Timeline edited = editor.setBezierCurveChannel(animation, source, 0, 1,
			0.15f, -0.3f, 0.75f, 1.2f);
		for (int sample = 1; sample < 100; sample++) {
			float time = sample / 100f;
			assertTrue(Float.floatToIntBits(bezierValue(source, 0, time, 0))
				== Float.floatToIntBits(bezierValue(edited, 0, time, 0)),
				"editing Y preserves every X sample, including a return to the same value");
		}
		assertTrue(Math.abs(bezierValue(source, 0, 0.4f, 1) - bezierValue(edited, 0, 0.4f, 1)) > 0.1f,
			"the requested Y channel really changes");
		AnimationEditor.TimelineSnapshot after = editor.snapshotCurrent(before);
		editor.restoreTimeline(before);
		assertNear(bezierValue(source, 0, 0.4f, 0), bezierValue(editor.findTimeline(animation,
			AnimationEditor.Prop.TRANSLATE, 0), 0, 0.4f, 0), "undo retains the returning X curve");
		editor.restoreTimeline(after);
		assertNear(bezierValue(edited, 0, 0.4f, 1), bezierValue(editor.findTimeline(animation,
			AnimationEditor.Prop.TRANSLATE, 0), 0, 0.4f, 1), "redo retains the edited Y curve");
	}

	private static void editsAbsoluteBezierHandles() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("absolute-handles");
		Animation.TranslateTimeline source = new Animation.TranslateTimeline(2, 2, 0);
		source.setFrame(0, 0f, 4f, 3f);
		source.setFrame(1, 1f, 4f, 20f);
		source.setBezier(0, 0, 0, 0f, 4f, 0.2f, 40f, 0.7f, -8f, 1f, 4f);
		source.setBezier(1, 0, 1, 0f, 3f, 0.3f, 3f, 0.8f, 20f, 1f, 20f);
		animation.setTimelines(new Array<>(new Animation.Timeline[] {source}),
			new com.badlogic.gdx.utils.IntArray());
		AnimationEditor.BezierHandles original = editor.bezierHandles(source, 0, 0);
		assertNear(40f, original.value1(), "Graph exposes the returning curve's absolute outgoing handle");
		assertNear(-8f, original.value2(), "Graph exposes the returning curve's absolute incoming handle");
		long revision = doc.revision;
		assertTrue(editor.setBezierHandlesChannel(animation, source, 0, 0, original.cx1(), original.value1(),
			original.cx2(), original.value2()) == source && doc.revision == revision,
			"reapplying absolute handles is a no-op even when endpoints match");
		Animation.Timeline edited = editor.setBezierHandlesChannel(animation, source, 0, 0,
			0.25f, 28f, original.cx2(), original.value2());
		AnimationEditor.BezierHandles changed = editor.bezierHandles(edited, 0, 0);
		assertNear(28f, changed.value1(), "dragging a returning handle changes its absolute value");
		assertNear(original.value2(), changed.value2(), "dragging one returning handle preserves the opposite handle");
		for (int sample = 1; sample < 100; sample++) {
			float time = sample / 100f;
			assertTrue(Float.floatToIntBits(bezierValue(source, 0, time, 1))
				== Float.floatToIntBits(bezierValue(edited, 0, time, 1)), "absolute handle edits preserve the Y channel exactly");
		}
		Animation.Timeline flat = editor.setBezierCurveChannel(animation, edited, 0, 0,
			changed.cx1(), 0f, changed.cx2(), 1f);
		assertTrue(flat != edited, "applying an explicit flat proportional preset is not mistaken for unchanged handles");
		assertNear(4f, bezierValue(flat, 0, 0.4f, 0), "explicit flat preset replaces the returning shape");
		Animation.Timeline flatSource = flat;
		assertThrows(IllegalArgumentException.class, () -> editor.setBezierHandlesChannel(animation,
			flatSource, 0, 0, 0.2f, Float.NaN, 0.7f, 4f), "absolute handle edits reject nonfinite values");

		MeshAttachment mesh = new MeshAttachment("deform-absolute", new Sequence(1, false));
		mesh.setWorldVerticesLength(2);
		mesh.setVertices(new float[] {0f, 0f});
		Animation.DeformTimeline deform = new Animation.DeformTimeline(3, 1, 0, mesh);
		deform.setFrame(0, 0f, new float[] {0f, 0f});
		deform.setFrame(1, 1f, new float[] {1f, 1f});
		deform.setFrame(2, 2f, new float[] {2f, 2f});
		deform.setBezier(0, 1, 0, 1f, 0f, 1.2f, 0.1f, 1.8f, 0.9f, 2f, 1f);
		animation.setTimelines(new Array<>(new Animation.Timeline[] {deform}),
			new com.badlogic.gdx.utils.IntArray());
		AnimationEditor.BezierHandles deformHandles = editor.bezierHandles(deform, 1, 0);
		assertNear(1.1f, deformHandles.value1(), "Deform absolute handles include the Graph segment offset");
		Animation.Timeline updatedDeform = editor.setBezierHandlesChannel(animation, deform, 1, 0,
			0.2f, 1.3f, 0.8f, 1.8f);
		assertNear(0.3f, editor.bezierCurve(updatedDeform, 1, 0).cy1(),
			"Deform absolute handle edits convert back to runtime interpolation percentages");
		assertNear(1.8f, editor.bezierHandles(updatedDeform, 1, 0).value2(),
			"Deform absolute handle values round trip on later segments");
	}

	private static void preservesReturningBezierCurvesWhenPasting() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation sourceAnimation = editor.addAnimation("returning-clipboard-source");
		Animation.RotateTimeline source = new Animation.RotateTimeline(2, 1, 0);
		source.setFrame(0, 0f, 5f);
		source.setFrame(1, 1f, 5f);
		source.setBezier(0, 0, 0, 0f, 5f, 0.2f, 35f, 0.8f, -10f, 1f, 5f);
		sourceAnimation.setTimelines(new Array<>(new Animation.Timeline[] {source}),
			new com.badlogic.gdx.utils.IntArray());
		Animation targetAnimation = editor.addAnimation("returning-clipboard-target");
		AnimationEditor.KeyframeBlock block = editor.copyKeyframes(sourceAnimation,
			new Animation.Timeline[] {source, source}, new int[] {0, 1});
		editor.pasteKeyframes(targetAnimation, block, 2f);
		Animation.Timeline pasted = editor.findTimeline(targetAnimation, AnimationEditor.Prop.ROTATE, 0);
		for (int sample = 1; sample < 20; sample++) {
			float time = sample / 20f;
			assertNear(source.getCurveValue(time), bezierValue(pasted, 0, time + 2f, 0),
				"copying both returning keys preserves the curve between them");
		}
		AnimationEditor.TimelineSnapshot before = editor.snapshotBoneTimeline(sourceAnimation,
			AnimationEditor.Prop.ROTATE, 0);
		AnimationEditor.KeyframeBlock laterKey = editor.copyKeyframes(sourceAnimation,
			new Animation.Timeline[] {source}, new int[] {1});
		editor.pasteKeyframes(sourceAnimation, laterKey, 3f);
		Animation.Timeline appended = editor.findTimeline(sourceAnimation, AnimationEditor.Prop.ROTATE, 0);
		assertNear(source.getCurveValue(0.3f), bezierValue(appended, 0, 0.3f, 0),
			"pasting a later key cannot flatten an unrelated existing segment");
		AnimationEditor.TimelineSnapshot after = editor.snapshotCurrent(before);
		editor.restoreTimeline(before);
		editor.restoreTimeline(after);
		assertNear(source.getCurveValue(0.3f), bezierValue(editor.findTimeline(sourceAnimation,
			AnimationEditor.Prop.ROTATE, 0), 0, 0.3f, 0), "paste undo and redo preserve returning curves");
		Animation.RotateTimeline offset = new Animation.RotateTimeline(2, 0, 0);
		offset.setFrame(0, 4f, 15f);
		offset.setFrame(1, 6f, 15f);
		targetAnimation.setTimelines(new Array<>(new Animation.Timeline[] {offset}),
			new com.badlogic.gdx.utils.IntArray());
		Animation.Timeline remapped = editor.applyCurveSnapshot(targetAnimation, offset, 0,
			editor.captureCurve(source, 0));
		for (int sample = 1; sample < 20; sample++) {
			float time = sample / 20f;
			assertNear(source.getCurveValue(time) + 10f, bezierValue(remapped, 0, 4f + time * 2f, 0),
				"single-curve paste preserves returning excursions after value offset and time scaling");
		}
	}

	private static void convertsSeparatedAxisTimelinesWithoutLoss() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("xy-separate");
		editor.ensureTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE, 0, 1f, 10f, 20f);
		Animation.Timeline combined = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0);
		combined = editor.setBezierCurveChannel(animation, combined, 0, 0, 0.2f, 0.1f, 0.7f, 0.8f);
		combined = editor.setBezierCurveChannel(animation, combined, 0, 1, 0.35f, -0.2f, 0.8f, 1.2f);
		float xSample = bezierValue(combined, 0, 0.5f, 0);
		float ySample = bezierValue(combined, 0, 0.5f, 1);
		doc.setGraphViewState(SkeletonDocument.graphViewStateKey(animation.getName(), combined),
			new SkeletonDocument.GraphViewState(0f, 1f, -5f, 25f, 1, 3L, true,
				new float[] {0f, 1f}, new int[] {0, 1}));

		assertTrue(editor.separateAxisTimeline(animation, AnimationEditor.AxisGroup.TRANSLATE, 0),
			"combined translation timeline can be separated");
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0) == null,
			"separation removes the combined timeline");
		Animation.Timeline x = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 0);
		Animation.Timeline y = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_Y, 0);
		assertTrue(x != null && y != null, "separation creates both axis timelines");
		assertNear(xSample, bezierValue(x, 0, 0.5f, 0), "separation preserves X curve samples");
		assertNear(ySample, bezierValue(y, 0, 0.5f, 0), "separation preserves Y curve samples");
		SkeletonDocument.GraphViewState xView = doc.graphViewState(
			SkeletonDocument.graphViewStateKey(animation.getName(), x));
		SkeletonDocument.GraphViewState yView = doc.graphViewState(
			SkeletonDocument.graphViewStateKey(animation.getName(), y));
		assertTrue(xView != null && java.util.Arrays.equals(new float[] {0f}, xView.selectedTimes())
			&& java.util.Arrays.equals(new int[] {0}, xView.selectedChannels()),
			"separation migrates the selected X keys to the X Graph");
		assertTrue(yView != null && java.util.Arrays.equals(new float[] {1f}, yView.selectedTimes())
			&& java.util.Arrays.equals(new int[] {0}, yView.selectedChannels()),
			"separation migrates the selected Y keys to the Y Graph");

		assertTrue(editor.combineAxisTimelines(animation, AnimationEditor.AxisGroup.TRANSLATE, 0),
			"compatible axis timelines can be combined");
		combined = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0);
		assertTrue(combined != null && editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 0) == null
			&& editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_Y, 0) == null,
			"combining restores one translation timeline");
		assertNear(xSample, curveValueAt(combined, 0.5f, 0), "combining preserves X curve samples");
		assertNear(ySample, curveValueAt(combined, 0.5f, 1), "combining preserves Y curve samples");
		SkeletonDocument.GraphViewState combinedView = doc.graphViewState(
			SkeletonDocument.graphViewStateKey(animation.getName(), combined));
		assertTrue(combinedView != null
			&& java.util.Arrays.equals(new float[] {0f, 1f}, combinedView.selectedTimes())
			&& java.util.Arrays.equals(new int[] {0, 1}, combinedView.selectedChannels()),
			"combining restores independent Graph key selections to their original channels");

		assertTrue(editor.separateAxisTimeline(animation, AnimationEditor.AxisGroup.TRANSLATE, 0),
			"timeline can be separated again");
		x = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 0);
		editor.setCurve(x, 0, true);
		assertThrows(IllegalStateException.class,
			() -> editor.combineAxisTimelines(animation, AnimationEditor.AxisGroup.TRANSLATE, 0),
			"incompatible stepped axes are rejected instead of losing interpolation");
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 0) != null
			&& editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_Y, 0) != null,
			"failed combine leaves both source timelines intact");

		Animation scaleAnimation = editor.addAnimation("scale-async");
		editor.ensureTimeline(scaleAnimation, AnimationEditor.Prop.SCALE, 0);
		editor.addKeyframe(scaleAnimation, AnimationEditor.Prop.SCALE, 0, 1f, 2f, 0.5f);
		editor.addKeyframe(scaleAnimation, AnimationEditor.Prop.SCALE, 0, 2f, 0.75f, 1.8f);
		Animation.Timeline scale = editor.findTimeline(scaleAnimation, AnimationEditor.Prop.SCALE, 0);
		scale = editor.setBezierCurveChannel(scaleAnimation, scale, 0, 0, 0.18f, -0.2f, 0.72f, 1.15f);
		scale = editor.setBezierCurveChannel(scaleAnimation, scale, 0, 1, 0.32f, 0.25f, 0.81f, 0.88f);
		scale = editor.setBezierCurveChannel(scaleAnimation, scale, 1, 0, 0.24f, 0.1f, 0.65f, 0.9f);
		scale = editor.setBezierCurveChannel(scaleAnimation, scale, 1, 1, 0.42f, -0.1f, 0.77f, 1.2f);
		assertTrue(editor.separateAxisTimeline(scaleAnimation, AnimationEditor.AxisGroup.SCALE, 0),
			"Scale Separate creates independent axes");
		Animation.Timeline scaleX = editor.findTimeline(scaleAnimation, AnimationEditor.Prop.SCALE_X, 0);
		Animation.Timeline scaleY = editor.findTimeline(scaleAnimation, AnimationEditor.Prop.SCALE_Y, 0);
		scaleX = editor.editCurveKey(scaleAnimation, scaleX, 1, 0, 0.75f,
			editor.curveFrameValue(scaleX, 1, 0));
		scaleY = editor.editCurveKey(scaleAnimation, scaleY, 1, 0, 1.25f,
			editor.curveFrameValue(scaleY, 1, 0));
		AnimationEditor.TimelineSnapshot originalScaleX = editor.snapshotBoneTimeline(
			scaleAnimation, AnimationEditor.Prop.SCALE_X, 0);
		AnimationEditor.TimelineSnapshot originalScaleY = editor.snapshotBoneTimeline(
			scaleAnimation, AnimationEditor.Prop.SCALE_Y, 0);
		float[] sampleTimes = {0.1f, 0.4f, 0.74f, 0.8f, 1f, 1.24f, 1.4f, 1.8f};
		float[] expectedX = new float[sampleTimes.length], expectedY = new float[sampleTimes.length];
		for (int index = 0; index < sampleTimes.length; index++) {
			expectedX[index] = curveValueAt(scaleX, sampleTimes[index], 0);
			expectedY[index] = curveValueAt(scaleY, sampleTimes[index], 0);
		}
		assertTrue(editor.combineAxisTimelines(scaleAnimation, AnimationEditor.AxisGroup.SCALE, 0),
			"asynchronous Scale axes merge on their key-time union");
		scale = editor.findTimeline(scaleAnimation, AnimationEditor.Prop.SCALE, 0);
		assertTrue(scale.getFrameCount() > 4,
			"asynchronous merge keeps key times and Runtime Bezier sample breakpoints");
		for (int index = 0; index < sampleTimes.length; index++) {
			assertNearAxisMerge(expectedX[index], curveValueAt(scale, sampleTimes[index], 0),
				"asynchronous Bezier merge preserves X sample " + index);
			assertNearAxisMerge(expectedY[index], curveValueAt(scale, sampleTimes[index], 1),
				"asynchronous Bezier merge preserves Y sample " + index);
		}

		assertTrue(editor.separateAxisTimeline(scaleAnimation, AnimationEditor.AxisGroup.SCALE, 0),
			"merged Scale timeline can be separated again");
		assertTrue(originalScaleX.sameAs(editor.snapshotBoneTimeline(
			scaleAnimation, AnimationEditor.Prop.SCALE_X, 0))
			&& originalScaleY.sameAs(editor.snapshotBoneTimeline(
				scaleAnimation, AnimationEditor.Prop.SCALE_Y, 0)),
			"an unchanged Combine to Separate round trip restores exact axis keys and curve storage");
		Animation firstTimeAnimation = editor.addAnimation("first-time-mismatch");
		editor.ensureTimeline(firstTimeAnimation, AnimationEditor.Prop.SCALE, 0);
		editor.addKeyframe(firstTimeAnimation, AnimationEditor.Prop.SCALE, 0, 1f, 2f, 0.5f);
		assertTrue(editor.separateAxisTimeline(firstTimeAnimation, AnimationEditor.AxisGroup.SCALE, 0),
			"first-key timing test can separate a Scale timeline");
		Animation.Timeline firstTimeX = editor.findTimeline(firstTimeAnimation, AnimationEditor.Prop.SCALE_X, 0);
		editor.editCurveKey(firstTimeAnimation, firstTimeX, 0, 0, 0.1f,
			editor.curveFrameValue(firstTimeX, 0, 0));
		assertThrows(IllegalStateException.class,
			() -> editor.combineAxisTimelines(firstTimeAnimation, AnimationEditor.AxisGroup.SCALE, 0),
			"different first-key times remain rejected because activation timing cannot be represented");

		Animation exactTimeAnimation = editor.addAnimation("exact-time-union");
		editor.ensureTimeline(exactTimeAnimation, AnimationEditor.Prop.TRANSLATE, 0);
		editor.addKeyframe(exactTimeAnimation, AnimationEditor.Prop.TRANSLATE, 0, 1f, 10f, 20f);
		assertTrue(editor.separateAxisTimeline(exactTimeAnimation, AnimationEditor.AxisGroup.TRANSLATE, 0),
			"exact-time union test can separate the source timeline");
		Animation.Timeline closeX = editor.findTimeline(exactTimeAnimation, AnimationEditor.Prop.TRANSLATE_X, 0);
		Animation.Timeline closeY = editor.findTimeline(exactTimeAnimation, AnimationEditor.Prop.TRANSLATE_Y, 0);
		closeX = editor.editCurveKey(exactTimeAnimation, closeX, 1, 0, 1.00001f,
			editor.curveFrameValue(closeX, 1, 0));
		closeY = editor.editCurveKey(exactTimeAnimation, closeY, 1, 0, 1.00002f,
			editor.curveFrameValue(closeY, 1, 0));
		assertTrue(editor.combineAxisTimelines(exactTimeAnimation, AnimationEditor.AxisGroup.TRANSLATE, 0),
			"axes with distinct sub-frame key times can be combined without tolerance-based collapse");
		Animation.Timeline exactCombined = editor.findTimeline(
			exactTimeAnimation, AnimationEditor.Prop.TRANSLATE, 0);
		assertTrue(exactCombined.getFrameCount() == 3
			&& Float.compare(editor.frameTime(exactCombined, 1), 1.00001f) == 0
			&& Float.compare(editor.frameTime(exactCombined, 2), 1.00002f) == 0,
			"axis combination preserves both distinct float key times exactly");
	}

	private static void restoresAxisConversionTransactionsExactly() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("axis-undo");
		editor.ensureTimeline(animation, AnimationEditor.Prop.SCALE, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.SCALE, 0, 1f, 1.8f, 0.6f);
		editor.addKeyframe(animation, AnimationEditor.Prop.SCALE, 0, 2f, 0.7f, 1.5f);
		Animation.Timeline combined = editor.findTimeline(animation, AnimationEditor.Prop.SCALE, 0);
		combined = editor.setBezierCurveChannel(animation, combined, 0, 0, 0.18f, -0.25f, 0.71f, 1.2f);
		combined = editor.setBezierCurveChannel(animation, combined, 0, 1, 0.33f, 0.2f, 0.84f, 0.92f);
		combined = editor.setBezierCurveChannel(animation, combined, 1, 0, 0.24f, 0.1f, 0.66f, 0.88f);
		combined = editor.setBezierCurveChannel(animation, combined, 1, 1, 0.41f, -0.15f, 0.79f, 1.15f);

		assertTrue(editor.separateAxisTimeline(animation, AnimationEditor.AxisGroup.SCALE, 0),
			"axis undo fixture separates the combined Scale timeline");
		Animation.Timeline x = editor.findTimeline(animation, AnimationEditor.Prop.SCALE_X, 0);
		Animation.Timeline y = editor.findTimeline(animation, AnimationEditor.Prop.SCALE_Y, 0);
		x = editor.editCurveKey(animation, x, 1, 0, 0.72f, editor.curveFrameValue(x, 1, 0));
		y = editor.editCurveKey(animation, y, 1, 0, 1.28f, editor.curveFrameValue(y, 1, 0));
		AnimationEditor.TimelineSnapshot exactX = editor.snapshotBoneTimeline(
			animation, AnimationEditor.Prop.SCALE_X, 0);
		AnimationEditor.TimelineSnapshot exactY = editor.snapshotBoneTimeline(
			animation, AnimationEditor.Prop.SCALE_Y, 0);
		doc.setGraphViewState(SkeletonDocument.graphViewStateKey(animation.getName(), x),
			new SkeletonDocument.GraphViewState(0f, 2f, 0f, 2f, 0, 1L, true,
				new float[] {0.72f}, new int[] {0}));
		doc.setGraphViewState(SkeletonDocument.graphViewStateKey(animation.getName(), y),
			new SkeletonDocument.GraphViewState(0f, 2f, 0f, 2f, 0, 1L, true,
				new float[] {1.28f}, new int[] {0}));
		doc.setAxisSeparationPreferred(animation.getName(), "root", "SCALE", true);

		assertTrue(editor.combineAxisTimelines(animation, AnimationEditor.AxisGroup.SCALE, 0),
			"axis undo fixture combines asynchronous Scale axes");
		doc.setAxisSeparationPreferred(animation.getName(), "root", "SCALE", false);
		combined = editor.findTimeline(animation, AnimationEditor.Prop.SCALE, 0);
		AnimationEditor.TimelineSnapshot exactCombined = editor.snapshotBoneTimeline(
			animation, AnimationEditor.Prop.SCALE, 0);
		SkeletonDocument.GraphViewState combinedGraph = doc.graphViewState(
			SkeletonDocument.graphViewStateKey(animation.getName(), combined));
		SkeletonDocument.AxisCombineState archived = doc.axisCombineState(animation.getName(), "root", "SCALE");
		AnimationEditor.AxisConversionSnapshot undo = editor.snapshotAxisConversion(
			animation, AnimationEditor.AxisGroup.SCALE, 0);

		assertTrue(editor.separateAxisTimeline(animation, AnimationEditor.AxisGroup.SCALE, 0),
			"axis undo fixture performs the operation to undo");
		doc.setAxisSeparationPreferred(animation.getName(), "root", "SCALE", true);
		editor.restoreAxisConversion(undo);
		assertTrue(exactCombined.sameAs(editor.snapshotBoneTimeline(
			animation, AnimationEditor.Prop.SCALE, 0)),
			"undo restores the exact combined frame and raw curve arrays");
		assertTrue(!doc.isAxisSeparationPreferred(animation.getName(), "root", "SCALE"),
			"undo restores the Separate preference");
		SkeletonDocument.AxisCombineState restoredArchive = doc.axisCombineState(
			animation.getName(), "root", "SCALE");
		assertTrue(restoredArchive != null
			&& java.util.Arrays.equals(archived.xFrames(), restoredArchive.xFrames())
			&& java.util.Arrays.equals(archived.xCurves(), restoredArchive.xCurves())
			&& java.util.Arrays.equals(archived.yFrames(), restoredArchive.yFrames())
			&& java.util.Arrays.equals(archived.yCurves(), restoredArchive.yCurves()),
			"undo restores the reversible asynchronous-axis archive");
		SkeletonDocument.GraphViewState restoredGraph = doc.graphViewState(
			SkeletonDocument.graphViewStateKey(animation.getName(),
				editor.findTimeline(animation, AnimationEditor.Prop.SCALE, 0)));
		assertTrue(restoredGraph != null
			&& java.util.Arrays.equals(combinedGraph.selectedTimes(), restoredGraph.selectedTimes())
			&& java.util.Arrays.equals(combinedGraph.selectedChannels(), restoredGraph.selectedChannels()),
			"undo restores the combined Graph selection state");

		assertTrue(editor.separateAxisTimeline(animation, AnimationEditor.AxisGroup.SCALE, 0),
			"the restored combined state can be separated again");
		assertTrue(exactX.sameAs(editor.snapshotBoneTimeline(animation, AnimationEditor.Prop.SCALE_X, 0))
			&& exactY.sameAs(editor.snapshotBoneTimeline(animation, AnimationEditor.Prop.SCALE_Y, 0)),
			"separating after undo recovers the original asynchronous axes and raw Bezier storage exactly");
	}

	private static void editsGraphCurveModesPerChannel() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("graph-channel-modes");
		editor.ensureTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE, 0, 1f, 10f, 20f);
		Animation.Timeline timeline = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0);
		timeline = editor.setBezierCurveChannel(animation, timeline, 0, 0, 0.15f, -0.2f, 0.72f, 1.2f);
		timeline = editor.setBezierCurveChannel(animation, timeline, 0, 1, 0.3f, 0.1f, 0.8f, 0.9f);
		float yQuarter = curveValueAt(timeline, 0.25f, 1);
		float yMiddle = curveValueAt(timeline, 0.5f, 1);

		timeline = editor.setCurveModeChannels(animation, timeline,
			new int[] {0}, new int[] {0}, false);
		assertNear(2.5f, curveValueAt(timeline, 0.25f, 0),
			"Graph Linear changes only the selected X channel");
		assertNearCurve(yQuarter, curveValueAt(timeline, 0.25f, 1),
			"Graph Linear preserves the unselected Y curve at quarter time");
		assertNearCurve(yMiddle, curveValueAt(timeline, 0.5f, 1),
			"Graph Linear preserves the unselected Y curve at midpoint");

		timeline = editor.setCurveModeChannels(animation, timeline,
			new int[] {0}, new int[] {0}, true);
		Animation.Timeline x = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 0);
		Animation.Timeline y = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_Y, 0);
		assertTrue(timeline == x && editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0) == null,
			"Graph automatically separates a combined XY timeline for per-channel Stepped");
		assertTrue(editor.curveType(x, 0) == Animation.CurveTimeline.STEPPED,
			"Graph applies Stepped to the selected X channel after separation");
		assertNearCurve(yQuarter, curveValueAt(y, 0.25f, 0),
			"automatic separation preserves the unselected Y Bezier curve");
		assertNearCurve(yMiddle, curveValueAt(y, 0.5f, 0),
			"automatic separation preserves the full unselected Y interpolation");

		timeline = editor.setCurveModeChannels(animation, x,
			new int[] {0}, new int[] {0}, false);
		assertTrue(timeline == x && editor.curveType(x, 0) == Animation.CurveTimeline.LINEAR,
			"Graph can return an independently stepped axis to Linear");

		Animation sharedStepped = editor.addAnimation("graph-shared-stepped");
		editor.ensureTimeline(sharedStepped, AnimationEditor.Prop.SCALE, 0);
		editor.addKeyframe(sharedStepped, AnimationEditor.Prop.SCALE, 0, 1f, 2f, 3f);
		Animation.Timeline shared = editor.findTimeline(sharedStepped, AnimationEditor.Prop.SCALE, 0);
		editor.setCurve(shared, 0, true);
		Animation.Timeline separatedY = editor.setCurveModeChannels(sharedStepped, shared,
			new int[] {0}, new int[] {1}, false);
		Animation.Timeline separatedX = editor.findTimeline(sharedStepped, AnimationEditor.Prop.SCALE_X, 0);
		assertTrue(separatedY == editor.findTimeline(sharedStepped, AnimationEditor.Prop.SCALE_Y, 0),
			"partial Linear on a shared Stepped segment returns the selected separated axis");
		assertTrue(editor.curveType(separatedX, 0) == Animation.CurveTimeline.STEPPED
			&& editor.curveType(separatedY, 0) == Animation.CurveTimeline.LINEAR,
			"partial Linear preserves Stepped on the unselected axis");
	}

	private static void editsGraphKeySelectionsAtomically() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("graph-selection");
		editor.ensureTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE, 0, 1f, 10f, 20f);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE, 0, 2f, 20f, 40f);
		Animation.Timeline timeline = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0);
		timeline = editor.setBezierCurveChannel(animation, timeline, 0, 0, 0.2f, 0.1f, 0.8f, 0.9f);
		long revision = doc.revision;
		float[] times = {0.25f, 1.25f, 2.25f};
		float[][] values = {{1f, 2f}, {11f, 22f}, {21f, 42f}};
		timeline = editor.editCurveKeys(animation, timeline, times, values);
		assertTrue(doc.revision == revision + 1, "Graph multi-edit performs one atomic document change");
		assertNear(1.25f, editor.frameTime(timeline, 1), "Graph multi-edit changes shared frame time");
		assertNear(42f, editor.curveFrameValue(timeline, 2, 1), "Graph multi-edit changes selected values");
		assertTrue(editor.hasBezier(timeline), "Graph multi-edit preserves Bezier interpolation");

		timeline = editor.insertCurveKey(animation, timeline, 1.5f, new float[] {15f, 30f});
		assertTrue(timeline.getFrameCount() == 4 && editor.findFrame(timeline, 1.5f) == 2,
			"Graph insert adds a sorted multi-channel key");
		boolean[] remove = {false, true, false, false};
		timeline = editor.removeCurveKeys(animation, timeline, remove);
		assertTrue(timeline.getFrameCount() == 3 && editor.findFrame(timeline, 1.25f) < 0,
			"Graph delete removes selected shared frames");
		AnimationEditor.TimelineSnapshot beforeDelete = editor.snapshotBoneTimeline(animation,
			AnimationEditor.Prop.TRANSLATE, 0);
		timeline = editor.removeCurveKeys(animation, timeline, new boolean[] {true, true, true});
		assertTrue(timeline == null
			&& editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0) == null,
			"Graph delete removes the timeline when all keys are selected");
		editor.restoreTimeline(beforeDelete);
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0) != null,
			"Graph whole-track deletion remains undoable through timeline snapshots");
	}

	private static void distinguishesExactSubframeKeys() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("dense-subframes");
		editor.ensureTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, 0, 1.00001f, 10f, 0f);
		editor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, 0, 1.00002f, 20f, 0f);
		Animation.Timeline timeline = editor.findTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		assertTrue(timeline.getFrameCount() == 3, "distinct subframe times create independent keys");
		assertTrue(editor.findFrame(timeline, 1.00001f) == 1, "first dense subframe resolves exactly");
		assertTrue(editor.findFrame(timeline, 1.00002f) == 2, "second dense subframe resolves exactly");
		assertTrue(editor.findFrame(timeline, 1.000015f) < 0,
			"a nearby time does not alias either dense key");

		timeline = editor.editCurveKey(animation, timeline, 2, 0, 1.00002f, 25f);
		assertNear(25f, editor.curveFrameValue(timeline, 2, 0),
			"Graph edits the requested dense key");
		editor.removeKeyframe(animation, AnimationEditor.Prop.ROTATE, 0, 1.00002f);
		timeline = editor.findTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		assertTrue(editor.findFrame(timeline, 1.00001f) == 1,
			"removing the second dense key preserves the first");
		assertTrue(editor.findFrame(timeline, 1.00002f) < 0,
			"removing the second dense key removes only that key");
	}

	private static void copiesAndPastesMultiTrackKeyframeBlocks() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation source = editor.addAnimation("block-source");
		editor.ensureTimeline(source, AnimationEditor.Prop.ROTATE, 0);
		editor.addKeyframe(source, AnimationEditor.Prop.ROTATE, 0, 1f, 10f, 0f);
		editor.addKeyframe(source, AnimationEditor.Prop.ROTATE, 0, 3f, 30f, 0f);
		editor.addKeyframe(source, AnimationEditor.Prop.ROTATE, 0, 4f, 40f, 0f);
		Animation.Timeline rotate = editor.findTimeline(source, AnimationEditor.Prop.ROTATE, 0);
		rotate = editor.setBezierCurve(source, rotate, 1, 0.2f, 0.1f, 0.8f, 0.9f);
		AnimationEditor.BezierCurve sourceCurve = editor.bezierCurve(rotate, 1, 0);

		editor.ensureTimeline(source, AnimationEditor.Prop.TRANSLATE_X, 0);
		editor.addKeyframe(source, AnimationEditor.Prop.TRANSLATE_X, 0, 2f, 25f, 0f);
		editor.addKeyframe(source, AnimationEditor.Prop.TRANSLATE_X, 0, 4f, 50f, 0f);
		Animation.Timeline translate = editor.findTimeline(source, AnimationEditor.Prop.TRANSLATE_X, 0);
		AnimationEditor.KeyframeBlock block = editor.copyKeyframes(source,
			new Animation.Timeline[] {rotate, translate, rotate}, new int[] {1, 1, 2});
		assertTrue(block.size() == 3, "block clipboard preserves every selected key");
		assertNear(2f, block.duration(), "block clipboard preserves relative duration");
		assertNear(1f, block.offsetAt(1), "block clipboard preserves cross-track timing");

		AnimationEditor.TimelineSnapshot[] before = editor.snapshotKeyframeBlockTargets(source, block);
		long revision = doc.revision;
		assertTrue(editor.pasteKeyframes(source, block, 5f) == 3,
			"block paste reports the complete pasted selection");
		assertTrue(doc.revision == revision + 1, "block paste is one atomic document mutation");
		rotate = editor.findTimeline(source, AnimationEditor.Prop.ROTATE, 0);
		translate = editor.findTimeline(source, AnimationEditor.Prop.TRANSLATE_X, 0);
		int pastedRotate = editor.findFrame(rotate, 5f);
		assertTrue(pastedRotate >= 0 && editor.findFrame(rotate, 7f) >= 0
			&& editor.findFrame(translate, 6f) >= 0,
			"block paste retains relative times across target tracks");
		assertNear(10f, editor.frameValue(rotate, pastedRotate, 0), "block paste retains rotate value");
		assertNear(25f, editor.frameValue(translate, editor.findFrame(translate, 6f), 0),
			"block paste retains translated track value");
		AnimationEditor.BezierCurve pastedCurve = editor.bezierCurve(rotate, pastedRotate, 0);
		assertNear(sourceCurve.cx1(), pastedCurve.cx1(), "block paste retains first Bezier X handle");
		assertNear(sourceCurve.cy2(), pastedCurve.cy2(), "block paste retains second Bezier Y handle");

		AnimationEditor.TimelineSnapshot[] collisionState = editor.snapshotKeyframeBlockTargets(source, block);
		revision = doc.revision;
		assertThrows(IllegalArgumentException.class, () -> editor.pasteKeyframes(source, block, 3f),
			"a collision rejects the whole block paste");
		assertTrue(doc.revision == revision, "a rejected block paste does not dirty the document");
		for (AnimationEditor.TimelineSnapshot snapshot : collisionState)
			assertTrue(snapshot.sameAs(editor.snapshotCurrent(snapshot)),
				"a rejected block paste leaves every target timeline unchanged");

		editor.restoreTimelines(before);
		assertTrue(editor.findFrame(editor.findTimeline(source, AnimationEditor.Prop.ROTATE, 0), 5f) < 0
			&& editor.findFrame(editor.findTimeline(source, AnimationEditor.Prop.TRANSLATE_X, 0), 6f) < 0,
			"one snapshot transaction undoes every pasted track");

		Animation destination = editor.addAnimation("block-destination");
		AnimationEditor.TimelineSnapshot[] destinationBefore = editor.snapshotKeyframeBlockTargets(destination, block);
		editor.pasteKeyframes(destination, block, 0f);
		assertTrue(editor.findFrame(editor.findTimeline(destination, AnimationEditor.Prop.ROTATE, 0), 0f) >= 0
			&& editor.findFrame(editor.findTimeline(destination, AnimationEditor.Prop.TRANSLATE_X, 0), 1f) >= 0,
			"a block can import matching target tracks into another animation");
		editor.restoreTimelines(destinationBefore);
		assertTrue(destination.getTimelines().isEmpty(), "undo removes tracks created by cross-animation paste");

		EventData firstEvent = editor.addEventDefinition("block-first-event");
		EventData secondEvent = editor.addEventDefinition("block-second-event");
		editor.addEventKeyframe(source, 8f, firstEvent, 1, 0f, "first", 0f, 0f);
		editor.addEventKeyframe(source, 8f, secondEvent, 2, 0f, "second", 0f, 0f);
		Animation.EventTimeline sourceEvents = editor.findEventTimeline(source);
		AnimationEditor.KeyframeBlock reversedEvents = editor.copyKeyframes(source,
			new Animation.Timeline[] {sourceEvents, sourceEvents}, new int[] {1, 0});
		Animation eventDestination = editor.addAnimation("block-event-destination");
		editor.pasteKeyframes(eventDestination, reversedEvents, 0f);
		Animation.EventTimeline pastedEvents = editor.findEventTimeline(eventDestination);
		assertTrue(editor.eventAt(pastedEvents, 0).getData() == firstEvent
			&& editor.eventAt(pastedEvents, 1).getData() == secondEvent,
			"same-time block paste preserves source event order instead of selection order");
	}

	private static void cleansOnlyRedundantLinearKeyframes() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("cleanup");
		editor.ensureTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, 0, 1f, 10f, 0f);
		editor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, 0, 2f, 20f, 0f);

		editor.ensureTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE_X, 0, 1f, 5f, 0f);
		editor.addKeyframe(animation, AnimationEditor.Prop.TRANSLATE_X, 0, 2f, 10f, 0f);
		Animation.Timeline stepped = editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 0);
		editor.setCurve(stepped, 0, true);

		editor.ensureTimeline(animation, AnimationEditor.Prop.SCALE_X, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.SCALE_X, 0, 1f, 1f, 0f);
		editor.addKeyframe(animation, AnimationEditor.Prop.SCALE_X, 0, 2f, 1f, 0f);

		editor.ensureTimeline(animation, AnimationEditor.Prop.SHEAR_X, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.SHEAR_X, 0, 1f, 10.001f, 0f);
		editor.addKeyframe(animation, AnimationEditor.Prop.SHEAR_X, 0, 2f, 20f, 0f);

		AnimationEditor.TimelineSnapshot[] before = editor.snapshotAnimationTimelines(animation);
		long revision = doc.revision;
		AnimationEditor.CleanupResult result = editor.cleanupAnimation(animation, 0f);
		assertTrue(result.removedKeyframes() == 1 && result.changedTimelines() == 1,
			"exact cleanup removes only the exactly redundant linear key");
		assertTrue(doc.revision == revision + 1, "animation cleanup is one atomic document mutation");
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.ROTATE, 0).getFrameCount() == 2,
			"cleanup removes the collinear rotate middle key");
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, 0).getFrameCount() == 3,
			"cleanup preserves stepped transitions");
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.SCALE_X, 0).getFrameCount() == 3,
			"cleanup preserves explicit equal-value hold keys");
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.SHEAR_X, 0).getFrameCount() == 3,
			"zero-tolerance cleanup preserves approximate values");

		editor.restoreTimelines(before);
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.ROTATE, 0).getFrameCount() == 3,
			"cleanup can be undone through grouped timeline snapshots");
		result = editor.cleanupAnimation(animation, 0.002f);
		assertTrue(result.removedKeyframes() == 2,
			"an explicit tolerance also removes values within the requested error bound");
	}

	private static void importsAnimationsByStrictTargetNames() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		SkeletonData sourceData = new SkeletonData();
		BoneData unused = new BoneData(0, "unused", null);
		BoneData mappedRoot = new BoneData(1, "root", unused);
		sourceData.getBones().add(unused);
		sourceData.getBones().add(mappedRoot);
		Animation source = new Animation("walk");
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(2, 0, 1);
		rotate.setFrame(0, 0f, 5f);
		rotate.setFrame(1, 1f, 25f);
		Array<Animation.Timeline> sourceTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		sourceTimelines.add(rotate);
		source.setTimelines(sourceTimelines, new com.badlogic.gdx.utils.IntArray());
		sourceData.getAnimations().add(source);

		long revision = doc.revision;
		Animation imported = editor.importAnimation(sourceData, source);
		assertEquals("walk", imported.getName(), "animation import keeps an available source name");
		Animation.Timeline importedRotate = editor.findTimeline(imported, AnimationEditor.Prop.ROTATE, 0);
		assertTrue(importedRotate != null && ((Animation.BoneTimeline)importedRotate).getBoneIndex() == 0,
			"animation import remaps a source bone index by exact name");
		assertNear(25f, editor.frameValue(importedRotate, 1, 0), "animation import preserves key values");
		assertTrue(doc.revision == revision + 1, "animation import is one document mutation");

		SkeletonData incompatible = new SkeletonData();
		BoneData missing = new BoneData(0, "missing", null);
		incompatible.getBones().add(missing);
		Animation invalid = new Animation("invalid");
		Animation.RotateTimeline invalidRotate = new Animation.RotateTimeline(1, 0, 0);
		invalidRotate.setFrame(0, 0f, 10f);
		Array<Animation.Timeline> invalidTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		invalidTimelines.add(invalidRotate);
		invalid.setTimelines(invalidTimelines, new com.badlogic.gdx.utils.IntArray());
		incompatible.getAnimations().add(invalid);
		int animationCount = doc.data.getAnimations().size;
		revision = doc.revision;
		assertThrows(IllegalArgumentException.class, () -> editor.importAnimation(incompatible, invalid),
			"animation import rejects a missing named target");
		assertTrue(doc.data.getAnimations().size == animationCount && doc.revision == revision,
			"a rejected animation import leaves the destination unchanged");

		SkeletonDocument deformDoc = createDocument();
		AnimationEditor deformEditor = new AnimationEditor(deformDoc);
		Skin targetSkin = new Skin("default");
		MeshAttachment targetMesh = weightedImportMesh("weighted", new int[] {2, 0});
		targetSkin.setAttachment(0, "weighted", targetMesh);
		deformDoc.data.getSkins().add(targetSkin);
		deformDoc.data.setDefaultSkin(targetSkin);

		SkeletonData malformedData = new SkeletonData();
		BoneData malformedRoot = new BoneData(0, "root", null);
		malformedData.getBones().add(malformedRoot);
		SlotData malformedSlot = new SlotData(0, "root-slot", malformedRoot);
		malformedSlot.setBlendMode(BlendMode.normal);
		malformedData.getSlots().add(malformedSlot);
		Skin malformedSkin = new Skin("default");
		MeshAttachment malformedMesh = weightedImportMesh("weighted", new int[] {2, 0});
		malformedSkin.setAttachment(0, "weighted", malformedMesh);
		malformedData.getSkins().add(malformedSkin);
		malformedData.setDefaultSkin(malformedSkin);
		Animation malformedAnimation = new Animation("malformed-deform");
		Animation.DeformTimeline malformedDeform = new Animation.DeformTimeline(1, 0, 0, malformedMesh);
		malformedDeform.setFrame(0, 0f, new float[] {0f, 0f});
		Array<Animation.Timeline> malformedTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		malformedTimelines.add(malformedDeform);
		malformedAnimation.setTimelines(malformedTimelines, new com.badlogic.gdx.utils.IntArray());
		malformedData.getAnimations().add(malformedAnimation);
		assertThrows(IllegalArgumentException.class,
			() -> deformEditor.importAnimation(malformedData, malformedAnimation),
			"animation import rejects truncated weighted Deform bone arrays without indexing past them");
	}

	private static MeshAttachment weightedImportMesh(String name, int[] bones) {
		MeshAttachment mesh = new MeshAttachment(name, new Sequence(1, false));
		mesh.setWorldVerticesLength(2);
		mesh.setBones(bones);
		mesh.setVertices(new float[] {0f, 0f, 1f});
		return mesh;
	}

	private static void importsAnimationBatchesAtomically() {
		SkeletonDocument replaceDoc = createDocument();
		AnimationEditor replaceEditor = new AnimationEditor(replaceDoc);
		Animation oldWalk = replaceEditor.addAnimation("walk");
		Animation idle = replaceEditor.addAnimation("idle");
		SliderData slider = new SliderData("driver");
		slider.setAnimation(oldWalk);
		replaceDoc.data.getConstraints().add(slider);
		replaceDoc.animationState = replaceDoc.newAnimationState();
		replaceDoc.setAnimation("walk");
		SkeletonData replacementSource = animationImportSource(
			simpleRotateAnimation("walk", 0, 15f), simpleRotateAnimation("jump", 0, 25f));
		AnimationEditor.AnimationImportResult replaced = replaceEditor.importAnimations(replacementSource,
			replacementSource.getAnimations(), AnimationEditor.AnimationImportConflict.REPLACE);
		Animation newWalk = replaced.importedAnimations().get(0);
		Animation jump = replaced.importedAnimations().get(1);
		assertTrue(replaced.replaced() == 1 && replaced.skipped() == 0,
			"replace batch reports replaced and imported entries");
		assertTrue(replaced.changes().get(0).index() == 0 && replaced.changes().get(1).index() == 2,
			"mixed replace and append changes store exact destination indices");
		assertTrue(replaceDoc.data.getAnimations().get(0) == newWalk
			&& replaceDoc.data.getAnimations().get(1) == idle
			&& replaceDoc.data.getAnimations().get(2) == jump,
			"replace batch preserves existing order and source append order");
		assertTrue(slider.getAnimation() == newWalk,
			"replace batch reconnects Slider references to the imported animation");
		assertTrue("walk".equals(replaceDoc.getCurrentAnimationName())
			&& replaceDoc.animationState.getTrack(0).getAnimation() == newWalk,
			"replace batch keeps the active animation by name");

		replaceEditor.restoreAnimationImport(replaced, false);
		assertTrue(replaceDoc.data.getAnimations().size == 2
			&& replaceDoc.data.getAnimations().get(0) == oldWalk
			&& replaceDoc.data.getAnimations().get(1) == idle,
			"batch undo removes additions and restores replacements in order");
		assertTrue(slider.getAnimation() == oldWalk,
			"batch undo restores Slider references");
		replaceEditor.restoreAnimationImport(replaced, true);
		assertTrue(replaceDoc.data.getAnimations().size == 3
			&& replaceDoc.data.getAnimations().get(0) == newWalk
			&& replaceDoc.data.getAnimations().get(2) == jump
			&& slider.getAnimation() == newWalk,
			"batch redo exactly reapplies replacements, additions, and Slider references");

		SkeletonDocument renameDoc = createDocument();
		AnimationEditor renameEditor = new AnimationEditor(renameDoc);
		renameEditor.addAnimation("walk");
		SkeletonData renameSource = animationImportSource(
			simpleRotateAnimation("walk", 0, 1f), simpleRotateAnimation("jump", 0, 2f));
		AnimationEditor.AnimationImportResult renamed = renameEditor.importAnimations(renameSource,
			renameSource.getAnimations(), AnimationEditor.AnimationImportConflict.RENAME);
		assertEquals("walk2", renamed.importedAnimations().get(0).getName(),
			"rename conflict generates a unique name");
		assertEquals("jump", renamed.importedAnimations().get(1).getName(),
			"rename batch retains available names");

		SkeletonDocument skipDoc = createDocument();
		AnimationEditor skipEditor = new AnimationEditor(skipDoc);
		skipEditor.addAnimation("walk");
		SkeletonData skipSource = animationImportSource(
			simpleRotateAnimation("walk", 0, 1f), simpleRotateAnimation("jump", 0, 2f));
		AnimationEditor.AnimationImportResult skipped = skipEditor.importAnimations(skipSource,
			skipSource.getAnimations(), AnimationEditor.AnimationImportConflict.SKIP);
		assertTrue(skipped.skipped() == 1 && skipped.importedAnimations().size() == 1
			&& "jump".equals(skipped.importedAnimations().get(0).getName()),
			"skip conflict ignores only colliding animations");

		SkeletonDocument atomicDoc = createDocument();
		AnimationEditor atomicEditor = new AnimationEditor(atomicDoc);
		Animation existing = atomicEditor.addAnimation("existing");
		SkeletonData invalidSource = new SkeletonData();
		BoneData sourceRoot = new BoneData(0, "root", null);
		BoneData missing = new BoneData(1, "missing", sourceRoot);
		invalidSource.getBones().add(sourceRoot);
		invalidSource.getBones().add(missing);
		invalidSource.getAnimations().add(simpleRotateAnimation("valid-first", 0, 3f));
		invalidSource.getAnimations().add(simpleRotateAnimation("invalid-second", 1, 4f));
		int count = atomicDoc.data.getAnimations().size;
		long revision = atomicDoc.revision;
		assertThrows(IllegalArgumentException.class,
			() -> atomicEditor.importAnimations(invalidSource, invalidSource.getAnimations(),
				AnimationEditor.AnimationImportConflict.RENAME),
			"a later invalid animation rejects the complete batch");
		assertTrue(atomicDoc.data.getAnimations().size == count
			&& atomicDoc.data.getAnimations().first() == existing && atomicDoc.revision == revision,
			"batch prevalidation prevents partial commits");
	}

	private static SkeletonData animationImportSource(Animation... animations) {
		SkeletonData source = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		source.getBones().add(root);
		for (Animation animation : animations) source.getAnimations().add(animation);
		return source;
	}

	private static Animation simpleRotateAnimation(String name, int boneIndex, float rotation) {
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(1, 0, boneIndex);
		rotate.setFrame(0, 0f, rotation);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(rotate);
		Animation animation = new Animation(name);
		animation.setTimelines(timelines, new com.badlogic.gdx.utils.IntArray());
		return animation;
	}

	private static void copiesBezierCurvesBetweenKeys() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("bezier-clipboard");
		Animation.RotateTimeline source = new Animation.RotateTimeline(3, 1, 0);
		source.setFrame(0, 0f, 0f);
		source.setFrame(1, 1f, 30f);
		source.setFrame(2, 3f, 60f);
		source.setBezier(0, 0, 0, 0f, 0f, 1f / 3f, -5f, 2f / 3f, 35f, 1f, 30f);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(source);
		com.badlogic.gdx.utils.IntArray bones = new com.badlogic.gdx.utils.IntArray();
		bones.add(0);
		animation.setTimelines(timelines, bones);

		AnimationEditor.BezierCurve sourceHandles = editor.bezierCurve(source, 0, 0);
		AnimationEditor.CurveSnapshot copied = editor.captureCurve(source, 0);
		float sourceMidpoint = source.getCurveValue(0.5f);
		editor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, 0, 2f, 0f, 0f);
		Animation.Timeline target = editor.findTimeline(animation, AnimationEditor.Prop.ROTATE, 0);
		int pastedFrame = editor.findFrame(target, 2f);
		target = editor.applyCurveSnapshot(animation, target, pastedFrame, copied);
		Animation.RotateTimeline pasted = (Animation.RotateTimeline)target;
		assertTrue(editor.curveType(pasted, pastedFrame) >= Animation.CurveTimeline.BEZIER,
			"pasted bone key retains Bezier interpolation");
		assertNear(sourceMidpoint + 15f, pasted.getCurveValue(2.5f),
			"pasted Bezier samples remap to the target endpoint");
		AnimationEditor.BezierCurve pastedHandles = editor.bezierCurve(pasted, pastedFrame, 0);
		assertNear(sourceHandles.cx1(), pastedHandles.cx1(), "pasted curve preserves normalized first X handle");
		assertNear(sourceHandles.cy1(), pastedHandles.cy1(), "pasted curve preserves normalized first Y handle");
		assertNear(sourceHandles.cx2(), pastedHandles.cx2(), "pasted curve preserves normalized second X handle");
		assertNear(sourceHandles.cy2(), pastedHandles.cy2(), "pasted curve preserves normalized second Y handle");

		AnimationEditor.TimelineSnapshot pastedSnapshot = editor.snapshotBoneTimeline(animation,
			AnimationEditor.Prop.ROTATE, 0);
		editor.setCurve(pasted, pastedFrame, false);
		editor.restoreTimeline(pastedSnapshot);
		Animation.RotateTimeline restored = (Animation.RotateTimeline)editor.findTimeline(animation,
			AnimationEditor.Prop.ROTATE, 0);
		assertNear(sourceMidpoint + 15f, restored.getCurveValue(2.5f),
			"timeline undo snapshot preserves pasted Bezier samples");

		Animation flatTargetAnimation = editor.addAnimation("flat-curve-target");
		Animation.RotateTimeline flatTarget = new Animation.RotateTimeline(2, 0, 0);
		flatTarget.setFrame(0, 0f, 5f);
		flatTarget.setFrame(1, 2f, 5f);
		Array<Animation.Timeline> flatTargetTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		flatTargetTimelines.add(flatTarget);
		flatTargetAnimation.setTimelines(flatTargetTimelines, bones);
		flatTarget = (Animation.RotateTimeline)editor.applyCurveSnapshot(flatTargetAnimation, flatTarget, 0, copied);
		for (float time : new float[] {0.2f, 1f, 1.8f})
			assertNear(5f, flatTarget.getCurveValue(time),
				"pasting onto equal target endpoints cannot create motion at " + time);

		Animation flatSourceAnimation = editor.addAnimation("flat-curve-source");
		Animation.RotateTimeline flatSource = new Animation.RotateTimeline(2, 1, 0);
		flatSource.setFrame(0, 0f, 3f);
		flatSource.setFrame(1, 1f, 3f);
		flatSource.setBezier(0, 0, 0, 0f, 3f, 0.2f, 3f, 0.8f, 3f, 1f, 3f);
		Array<Animation.Timeline> flatSourceTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		flatSourceTimelines.add(flatSource);
		flatSourceAnimation.setTimelines(flatSourceTimelines, bones);
		AnimationEditor.CurveSnapshot flatCopied = editor.captureCurve(flatSource, 0);

		Animation rangedTargetAnimation = editor.addAnimation("flat-source-ranged-target");
		Animation.RotateTimeline rangedTarget = new Animation.RotateTimeline(2, 0, 0);
		rangedTarget.setFrame(0, 0f, 10f);
		rangedTarget.setFrame(1, 1f, 20f);
		Array<Animation.Timeline> rangedTargetTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		rangedTargetTimelines.add(rangedTarget);
		rangedTargetAnimation.setTimelines(rangedTargetTimelines, bones);
		rangedTarget = (Animation.RotateTimeline)editor.applyCurveSnapshot(
			rangedTargetAnimation, rangedTarget, 0, flatCopied);
		AnimationEditor.BezierCurve flatSourceHandles = editor.bezierCurve(rangedTarget, 0, 0);
		assertNear(0f, flatSourceHandles.cy1(), "flat source uses the defined first Y handle fallback");
		assertNear(1f, flatSourceHandles.cy2(), "flat source uses the defined second Y handle fallback");
		assertTrue(Float.isFinite(rangedTarget.getCurveValue(0.5f)),
			"flat source curve mapping produces finite target samples");
	}

	private static void editsMultiChannelBezierTimelines() {
		SkeletonDocument doc = createDocument();
		AnimationEditor editor = new AnimationEditor(doc);

		Animation colorAnimation = editor.addAnimation("bezier-color");
		Animation.RGBATimeline color = new Animation.RGBATimeline(2, 4, 0);
		float[] colorStart = {1f, 0.8f, 0.6f, 1f};
		float[] colorEnd = {0.2f, 0.4f, 0.7f, 0.5f};
		color.setFrame(0, 0f, colorStart[0], colorStart[1], colorStart[2], colorStart[3]);
		color.setFrame(1, 1f, colorEnd[0], colorEnd[1], colorEnd[2], colorEnd[3]);
		for (int channel = 0; channel < colorStart.length; channel++)
			color.setBezier(channel, 0, channel, 0f, colorStart[channel], 0.25f, colorStart[channel],
				0.75f, colorEnd[channel], 1f, colorEnd[channel]);
		Array<Animation.Timeline> colorTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		colorTimelines.add(color);
		colorAnimation.setTimelines(colorTimelines, new com.badlogic.gdx.utils.IntArray());
		editor.addColorKeyframe(colorAnimation, 0, 2f, 0f, 0f, 0f, 1f);
		color = editor.findColorTimeline(colorAnimation, 0);
		assertTrue(color.getFrameCount() == 3 && editor.hasBezier(color),
			"RGBA Bezier track accepts insertion");
		editor.editColorKeyframe(colorAnimation, 0, 1f, 1.5f, 0.3f, 0.5f, 0.8f, 0.6f, false);
		color = editor.findColorTimeline(colorAnimation, 0);
		assertTrue(editor.hasBezier(color), "RGBA endpoint editing preserves all Bezier channels");
		assertTrue(editor.removeColorKeyframe(colorAnimation, 0, 2f), "RGBA Bezier key can be removed");
		color = editor.findColorTimeline(colorAnimation, 0);
		color = (Animation.RGBATimeline)editor.setBezierCurve(colorAnimation, color, 0, 0.3f, 0f, 0.7f, 1f);
		for (int channel = 0; channel < 4; channel++) {
			AnimationEditor.BezierCurve curve = editor.bezierCurve(color, 0, channel);
			assertNear(0.3f, curve.cx1(), "RGBA Bezier first control channel " + channel);
			assertNear(0.7f, curve.cx2(), "RGBA Bezier second control channel " + channel);
		}

		setDarkColor(doc.data.getSlots().first(), Color.BLACK);
		Animation twoColorAnimation = editor.addAnimation("bezier-two-color");
		Animation.RGBA2Timeline twoColor = new Animation.RGBA2Timeline(2, 7, 0);
		float[] twoStart = {1f, 0.8f, 0.6f, 1f, 0.1f, 0.2f, 0.3f};
		float[] twoEnd = {0.2f, 0.4f, 0.7f, 0.5f, 0.8f, 0.7f, 0.6f};
		twoColor.setFrame(0, 0f, twoStart[0], twoStart[1], twoStart[2], twoStart[3],
			twoStart[4], twoStart[5], twoStart[6]);
		twoColor.setFrame(1, 1f, twoEnd[0], twoEnd[1], twoEnd[2], twoEnd[3],
			twoEnd[4], twoEnd[5], twoEnd[6]);
		for (int channel = 0; channel < twoStart.length; channel++)
			twoColor.setBezier(channel, 0, channel, 0f, twoStart[channel], 0.25f, twoStart[channel],
				0.75f, twoEnd[channel], 1f, twoEnd[channel]);
		Array<Animation.Timeline> twoColorTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		twoColorTimelines.add(twoColor);
		twoColorAnimation.setTimelines(twoColorTimelines, new com.badlogic.gdx.utils.IntArray());
		editor.addTwoColorKeyframe(twoColorAnimation, 0, 2f,
			0f, 0f, 0f, 1f, 1f, 1f, 1f, true);
		SlotCurveTimeline rebuiltTwoColor = editor.findTwoColorTimeline(twoColorAnimation, 0);
		assertTrue(rebuiltTwoColor.getFrameCount() == 3 && editor.hasBezier(rebuiltTwoColor),
			"RGBA2 Bezier track accepts insertion");
		editor.editTwoColorKeyframe(twoColorAnimation, 0, 1f, 1.5f,
			0.3f, 0.5f, 0.8f, 0.6f, 0.9f, 0.8f, 0.7f, false);
		rebuiltTwoColor = editor.findTwoColorTimeline(twoColorAnimation, 0);
		assertTrue(editor.hasBezier(rebuiltTwoColor), "RGBA2 endpoint editing preserves all Bezier channels");
		assertTrue(editor.removeTwoColorKeyframe(twoColorAnimation, 0, 2f),
			"RGBA2 Bezier key can be removed");
		rebuiltTwoColor = editor.findTwoColorTimeline(twoColorAnimation, 0);
		AnimationEditor.CurveSnapshot copiedCurve = editor.captureCurve(rebuiltTwoColor, 0);
		editor.addTwoColorKeyframe(twoColorAnimation, 0, 3f,
			0f, 1f, 0f, 1f, 1f, 0f, 1f, true);
		editor.addTwoColorKeyframe(twoColorAnimation, 0, 2f,
			twoStart[0], twoStart[1], twoStart[2], twoStart[3],
			twoStart[4], twoStart[5], twoStart[6], true);
		rebuiltTwoColor = editor.findTwoColorTimeline(twoColorAnimation, 0);
		int pastedFrame = editor.findFrame(rebuiltTwoColor, 2f);
		rebuiltTwoColor = (SlotCurveTimeline)editor.applyCurveSnapshot(twoColorAnimation,
			rebuiltTwoColor, pastedFrame, copiedCurve);
		assertTrue(editor.curveType(rebuiltTwoColor, pastedFrame) >= Animation.CurveTimeline.BEZIER,
			"RGBA2 clipboard applies all seven Bezier channels");
		rebuiltTwoColor = (SlotCurveTimeline)editor.setBezierCurve(twoColorAnimation,
			rebuiltTwoColor, 0, 0.25f, -0.1f, 0.75f, 1.1f);
		assertTrue(rebuiltTwoColor instanceof Animation.RGBA2Timeline,
			"Bezier control editing preserves the RGBA2 timeline type");
		for (int channel = 0; channel < 7; channel++) {
			AnimationEditor.BezierCurve curve = editor.bezierCurve(rebuiltTwoColor, 0, channel);
			assertNear(-0.1f, curve.cy1(), "RGBA2 Bezier first value control channel " + channel);
			assertNear(1.1f, curve.cy2(), "RGBA2 Bezier second value control channel " + channel);
		}
	}

	private static void previewsTwoColorTimeline() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(Inherit.normal);
		data.getBones().add(root);
		SlotData slot = new SlotData(0, "slot", root);
		slot.setBlendMode(BlendMode.normal);
		setDarkColor(slot, Color.BLACK);
		data.getSlots().add(slot);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("two-color-preview");
		editor.addTwoColorKeyframe(animation, 0, 0f,
			1f, 0f, 0f, 1f, 0f, 0f, 0f, true);
		editor.addTwoColorKeyframe(animation, 0, 1f,
			0f, 0f, 1f, 0.5f, 1f, 0.5f, 0f, true);
		doc.animationState = new AnimationState(new AnimationStateData(data));
		assertTrue(doc.setAnimation(animation.getName()), "two color preview animation can be selected");
		doc.setPlaying(false);
		assertTrue(doc.scrubTo(0.5f), "two color preview can scrub to midpoint");
		var pose = doc.skeleton.findSlot("slot").getPose();
		assertNear(0.5f, pose.getColor().r, "two color preview light red");
		assertNear(0.5f, pose.getColor().b, "two color preview light blue");
		assertNear(0.75f, pose.getColor().a, "two color preview light alpha");
		assertNear(0.5f, pose.getDarkColor().r, "two color preview dark red");
		assertNear(0.25f, pose.getDarkColor().g, "two color preview dark green");
	}

	private static void managesActiveAnimationDeletion() {
		SkeletonDocument doc = createDocument();
		doc.animationState = new AnimationState(new AnimationStateData(doc.data));
		AnimationEditor editor = new AnimationEditor(doc);
		Animation first = editor.addAnimation("first");
		Animation middle = editor.addAnimation("middle");
		Animation last = editor.addAnimation("last");
		Animation inactive = editor.addAnimation("inactive");
		SkeletonDocument.GraphViewState inactiveGraph = new SkeletonDocument.GraphViewState(
			0f, 2f, -1f, 1f, 0, 1L);
		SkeletonDocument.GraphViewState firstGraph = new SkeletonDocument.GraphViewState(
			0f, 1f, 0f, 1f, 0, 1L);
		doc.setAxisSeparationPreferred(inactive.getName(), "root", "SCALE", true);
		doc.setGraphViewState(inactive.getName() + "|test.Timeline|[1]", inactiveGraph);
		doc.setGraphViewState(first.getName() + "|test.Timeline|[2]", firstGraph);
		AnimationStateData stateData = doc.animationState.getData();
		doc.restoreAnimationMixSettings(0.35f, java.util.List.of(
			new SkeletonDocument.AnimationMix(first.getName(), last.getName(), 0.72f)));
		doc.setPlaying(false);
		assertTrue(doc.setAnimation(middle.getName()), "middle animation can be selected");
		assertTrue(editor.removeAnimation(inactive), "inactive animation can be removed");
		assertEquals(middle.getName(), doc.getCurrentAnimationName(),
			"removing an inactive animation preserves the current preview");
		assertTrue(!doc.isAxisSeparationPreferred(inactive.getName(), "root", "SCALE")
			&& doc.graphViewState(inactive.getName() + "|test.Timeline|[1]") == null,
			"removing an animation clears its editor-only state");
		assertTrue(doc.graphViewState(first.getName() + "|test.Timeline|[2]") == firstGraph,
			"removing an animation preserves other animations' editor state");
		Animation replacement = editor.addAnimation("inactive");
		assertEquals("inactive", replacement.getName(), "a deleted animation name can be reused");
		assertTrue(!doc.isAxisSeparationPreferred(replacement.getName(), "root", "SCALE")
			&& doc.graphViewState(replacement.getName() + "|test.Timeline|[1]") == null,
			"a recreated animation does not inherit deleted editor state");
		assertTrue(editor.removeAnimation(replacement), "replacement animation can be removed");

		assertTrue(editor.removeAnimation(middle), "selected middle animation can be removed");
		assertEquals(last.getName(), doc.getCurrentAnimationName(),
			"removing a middle animation selects the next animation at the same list position");
		assertTrue(!doc.playing, "replacement animation preserves paused playback");
		assertTrue(doc.animationState.getData() == stateData,
			"removing the active animation preserves the AnimationStateData instance");
		assertNear(0.35f, stateData.getDefaultMix(), "removing an animation preserves default mix");
		assertNear(0.72f, stateData.getMix(first, last), "removing an animation preserves pair mix");

		assertTrue(editor.removeAnimation(last), "selected tail animation can be removed");
		assertTrue(doc.animationMixes().isEmpty(), "removing an animation removes its pair-mix references");
		assertEquals(first.getName(), doc.getCurrentAnimationName(),
			"removing the tail animation selects its previous neighbor");

		doc.setPlaying(true);
		doc.skeleton.getRootBone().getPose().setRotation(73f);
		assertTrue(editor.removeAnimation(first), "last animation can be removed");
		assertEquals("", doc.getCurrentAnimationName(), "removing the last animation clears the active track");
		assertTrue(doc.animationState.getTrack(0) == null && !doc.playing && !doc.poseMode,
			"empty animation list leaves a stopped non-pose document");
		assertNear(0f, doc.maxTime, "empty animation list clears the playback duration");
		assertNear(30f, doc.skeleton.getRootBone().getPose().getRotation(),
			"removing the last animation restores the setup pose");
	}

	private static void addPoseFrame(AnimationEditor editor, Animation animation,
		com.esotericsoftware.spine.Bone bone, AnimationEditor.Prop prop) {
		editor.ensureTimeline(animation, prop, bone.getData().getIndex());
		float[] values = editor.valuesForPose(bone, prop);
		editor.addKeyframe(animation, prop, bone.getData().getIndex(), 0.5f, values[0], values[1]);
	}

	private static AnimationEditor.TimelineSnapshot[] snapshotProps(AnimationEditor editor, Animation animation,
		Array<AnimationEditor.Prop> props) {
		AnimationEditor.TimelineSnapshot[] snapshots = new AnimationEditor.TimelineSnapshot[props.size];
		for (int index = 0; index < props.size; index++)
			snapshots[index] = editor.snapshotBoneTimeline(animation, props.get(index), 0);
		return snapshots;
	}

	private static AnimationEditor.TimelineSnapshot[] snapshotCurrent(AnimationEditor editor,
		AnimationEditor.TimelineSnapshot[] references) {
		AnimationEditor.TimelineSnapshot[] snapshots = new AnimationEditor.TimelineSnapshot[references.length];
		for (int index = 0; index < references.length; index++) snapshots[index] = editor.snapshotCurrent(references[index]);
		return snapshots;
	}

	private static void assertFrame(AnimationEditor editor, Animation animation, AnimationEditor.Prop prop,
		int frame, float time, float value1, float value2) {
		Animation.Timeline timeline = editor.findTimeline(animation, prop, 0);
		assertNear(time, editor.frameTime(timeline, frame), prop + " frame time");
		assertNear(value1, editor.frameValue(timeline, frame, 0), prop + " frame value 1");
		assertNear(value2, editor.frameValue(timeline, frame, 1), prop + " frame value 2");
	}

	private static void assertColorFrame(AnimationEditor editor, Animation.RGBATimeline timeline, int frame,
		float time, float red, float green, float blue, float alpha) {
		assertNear(time, editor.frameTime(timeline, frame), "color frame time");
		assertNear(red, editor.colorValue(timeline, frame, 0), "color red");
		assertNear(green, editor.colorValue(timeline, frame, 1), "color green");
		assertNear(blue, editor.colorValue(timeline, frame, 2), "color blue");
		assertNear(alpha, editor.colorValue(timeline, frame, 3), "color alpha");
	}

	private static void assertTwoColorFrame(AnimationEditor editor, Animation.Timeline timeline, int frame,
		float time, float lightRed, float lightGreen, float lightBlue, float lightAlpha,
		float darkRed, float darkGreen, float darkBlue) {
		assertNear(time, editor.frameTime(timeline, frame), "two color frame time");
		float[] expected = {lightRed, lightGreen, lightBlue, lightAlpha, darkRed, darkGreen, darkBlue};
		for (int channel = 0; channel < expected.length; channel++)
			assertNear(expected[channel], editor.twoColorValue(timeline, frame, channel),
				"two color channel " + channel);
	}

	private static float bezierValue(Animation.Timeline timeline, int frame, float time, int channel) {
		Animation.CurveTimeline curve = (Animation.CurveTimeline)timeline;
		int block = curve.getCurveType(frame) - Animation.CurveTimeline.BEZIER
			+ channel * Animation.CurveTimeline.BEZIER_SIZE;
		return curve.getBezierValue(time, frame * timeline.getFrameEntries(), channel + 1, block);
	}

	private static float curveValueAt(Animation.Timeline timeline, float time, int channel) {
		int entries = timeline.getFrameEntries();
		if (time <= timeline.getFrames()[0]) return timeline.getFrames()[channel + 1];
		int last = timeline.getFrameCount() - 1;
		if (time >= timeline.getFrames()[last * entries])
			return timeline.getFrames()[last * entries + channel + 1];
		Animation.CurveTimeline curve = (Animation.CurveTimeline)timeline;
		for (int frame = 0; frame < last; frame++) {
			float time1 = timeline.getFrames()[frame * entries];
			float time2 = timeline.getFrames()[(frame + 1) * entries];
			if (time > time2) continue;
			float value1 = timeline.getFrames()[frame * entries + channel + 1];
			float value2 = timeline.getFrames()[(frame + 1) * entries + channel + 1];
			int type = curve.getCurveType(frame);
			if (type == Animation.CurveTimeline.STEPPED) return value1;
			if (type < Animation.CurveTimeline.BEZIER)
				return value1 + (value2 - value1) * (time - time1) / (time2 - time1);
			return curve.getBezierValue(time, frame * entries, channel + 1,
				type - Animation.CurveTimeline.BEZIER + channel * Animation.CurveTimeline.BEZIER_SIZE);
		}
		throw new AssertionError("sample time was not inside the timeline");
	}

	private static void assertNearCurve(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNearAxisMerge(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void setDarkColor(SlotData slot, Color color) {
		try {
			java.lang.reflect.Field field = slot.getSetupPose().getClass().getDeclaredField("darkColor");
			field.setAccessible(true);
			field.set(slot.getSetupPose(), new Color(color));
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("unable to initialize slot dark color", error);
		}
	}

	private static SkeletonDocument createDocument() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(Inherit.normal);
		root.getSetupPose().setX(10f);
		root.getSetupPose().setY(20f);
		root.getSetupPose().setRotation(30f);
		root.getSetupPose().setScaleX(2f);
		root.getSetupPose().setScaleY(3f);
		root.getSetupPose().setShearX(5f);
		root.getSetupPose().setShearY(-10f);
		data.getBones().add(root);
		SlotData slot = new SlotData(0, "root-slot", root);
		slot.setBlendMode(BlendMode.normal);
		data.getSlots().add(slot);
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

	private static void assertArray(float[] expected, float[] actual, String message) {
		if (expected == null || actual == null || expected.length != actual.length)
			throw new AssertionError(message + ": array lengths differ");
		for (int index = 0; index < expected.length; index++)
			assertNear(expected[index], actual[index], message + "[" + index + "]");
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static <T extends Animation.Timeline> T findTimeline(Animation animation, Class<T> type) {
		for (Animation.Timeline timeline : animation.getTimelines()) {
			if (timeline.getClass() == type) return type.cast(timeline);
		}
		return null;
	}

	private static void assertThrows(Class<? extends Throwable> type, Runnable action, String message) {
		try {
			action.run();
		} catch (Throwable error) {
			if (type.isInstance(error)) return;
			throw new AssertionError(message + ": wrong exception " + error, error);
		}
		throw new AssertionError(message + ": expected " + type.getSimpleName());
	}

	private static final class StubAttachmentLoader implements AttachmentLoader {
		@Override public RegionAttachment newRegionAttachment(Skin skin, String placeholder, String name,
			String path, Sequence sequence) {
			return new RegionAttachment(name, sequence);
		}

		@Override public MeshAttachment newMeshAttachment(Skin skin, String placeholder, String name,
			String path, Sequence sequence) {
			return new MeshAttachment(name, sequence);
		}

		@Override public BoundingBoxAttachment newBoundingBoxAttachment(Skin skin, String placeholder, String name) {
			return new BoundingBoxAttachment(name);
		}

		@Override public ClippingAttachment newClippingAttachment(Skin skin, String placeholder, String name) {
			return new ClippingAttachment(name);
		}

		@Override public PathAttachment newPathAttachment(Skin skin, String placeholder, String name) {
			return new PathAttachment(name);
		}

		@Override public PointAttachment newPointAttachment(Skin skin, String placeholder, String name) {
			return new PointAttachment(name);
		}
	}

	private AnimationEditorTest() {}
}
