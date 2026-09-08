package com.openspine.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.esotericsoftware.spine.Animation.EventTimeline;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintPose;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.Animation.PathConstraintMixTimeline;
import com.esotericsoftware.spine.Animation.PathConstraintPositionTimeline;
import com.esotericsoftware.spine.Animation.PathConstraintSpacingTimeline;
import com.esotericsoftware.spine.Animation.SliderMixTimeline;
import com.esotericsoftware.spine.Animation.SliderTimeline;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.Sequence.SequenceMode;
import com.openspine.model.AnimationEditor;
import com.openspine.model.SkeletonDocument;

import java.util.IdentityHashMap;

/** Headless regression tests for semantic timeline inspector labels. */
public final class TimelinePanelTest {
	public static void main(String[] args) {
		attachmentTracksHaveSkinQualifiedIdentity();
		sharedAttachmentAddressesSurviveSkinEdits();
		assertNear(208f, TimelinePanel.TRACK_LABEL_COLUMN_WIDTH,
			"track label column accommodates the compact fixed filter header");
		assertNear(9f, TimelinePanel.TRACK_LABEL_LEFT_PADDING, "track label left padding");
		assertNear(208f, TimelinePanel.timelineBoundaryX(208f),
			"timeline boundary starts directly after the label column without a black gutter");
		assertNear(218f, TimelinePanel.timelineOriginX(208f),
			"frame zero retains enough padding for a complete marker after the fixed column");
		assertNear(52f, TimelinePanel.TOOLBAR_GROUP_GAP,
			"timeline toolbar groups keep one standard button slot between them");
		assertNear(46f, TimelinePanel.FPS_FIELD_WIDTH,
			"the FPS input exposes exactly enough width for four digits");
		assertNear(78f, TimelinePanel.TIMELINE_WHEEL_STEP,
			"timeline wheel movement stays at three track rows instead of scaling with content height");
		assertEquals(TimelinePanel.TimelineWheelAction.VERTICAL,
			TimelinePanel.timelineWheelAction(false, false, false), "plain wheel scrolls tracks vertically");
		assertEquals(TimelinePanel.TimelineWheelAction.FRAME_SPACING,
			TimelinePanel.timelineWheelAction(false, false, true),
			"plain wheel over the dopesheet ruler changes only frame spacing");
		assertEquals(TimelinePanel.TimelineWheelAction.HORIZONTAL,
			TimelinePanel.timelineWheelAction(false, true, true),
			"shift wheel still scrolls the timeline horizontally over the ruler");
		assertEquals(TimelinePanel.TimelineWheelAction.ZOOM,
			TimelinePanel.timelineWheelAction(true, true, true),
			"control wheel takes priority and keeps the original whole-timeline zoom");
		assertTrue(TimelinePanel.timelineRulerWheelRegion(300f, 285f, 900f, 300f, 204f, 40f),
			"the visible time ruler accepts frame-spacing wheel input");
		assertTrue(!TimelinePanel.timelineRulerWheelRegion(180f, 285f, 900f, 300f, 204f, 40f),
			"the fixed track column keeps its original wheel behavior");
		assertTrue(!TimelinePanel.timelineRulerWheelRegion(300f, 240f, 900f, 300f, 204f, 40f),
			"track rows below the ruler keep their original wheel behavior");
		assertNear(-2f, TimelinePanel.timelineWheelAmount(-2f, 0f),
			"horizontal touchpad input remains usable when vertical wheel input is absent");
		assertNear(55f, TimelinePanel.timelineWheelZoom(50f, -1f),
			"wheel up increases the unified timeline zoom");
		assertNear(45f, TimelinePanel.timelineWheelZoom(50f, 1f),
			"wheel down decreases the unified timeline zoom");
		assertNear(26f, TimelinePanel.timelineTrackRowHeight(TimelinePanel.DEFAULT_ZOOM),
			"default zoom preserves the original track density");
		assertNear(1f, TimelinePanel.timelineLabelScale(TimelinePanel.DEFAULT_ZOOM),
			"default zoom preserves the track label font size");
		assertNear(18f, TimelinePanel.timelineTrackRowHeight(25f),
			"zooming out reduces track height without making labels unreadable");
		assertNear(18f / 26f, TimelinePanel.timelineLabelScale(30f),
			"compact timeline zoom reduces left-side labels with the visible row height");
		assertNear(52f, TimelinePanel.timelineTrackRowHeight(150f),
			"zooming in enlarges the complete timeline instead of only its time axis");
		assertNear(2f, TimelinePanel.timelineLabelScale(150f),
			"expanded timeline zoom enlarges left-side labels with their rows");
		assertNear(156f, TimelinePanel.timelineWheelStep(150f),
			"wheel scrolling remains a three-track step at the active zoom");
		assertNear(0f, TimelinePanel.zoomAnchoredVerticalScroll(0f, 300f, 300f, 50f, 100f, 40f),
			"zooming at the top does not introduce a vertical jump");
		assertNear(160f, TimelinePanel.zoomAnchoredVerticalScroll(100f, 300f, 300f, 50f, 100f, 40f),
			"zooming preserves the track anchored under the pointer");
		assertThrows(IllegalArgumentException.class, () -> TimelinePanel.timelineOriginX(-1f),
			"negative timeline label widths are rejected");
		assertTrue(TimelinePanel.timelineSpanVisible(214f, 224f, 204f, 900f),
			"complete frame-zero markers remain visible inside the timeline viewport");
		assertTrue(!TimelinePanel.timelineSpanVisible(199f, 209f, 204f, 900f),
			"ruler decorations crossing the fixed left panel boundary are hidden");
		assertNear(31f / 30f, TimelinePanel.timelineActiveEndTime(1f, 30f),
			"the active timeline range includes the complete last frame cell");
		assertNear(1f / 30f, TimelinePanel.timelineActiveEndTime(0f, 30f),
			"a frame-zero-only animation still exposes one active frame cell");
		assertNear(0f, TimelinePanel.timelineActiveEndTime(Float.NaN, 30f),
			"an invalid animation duration does not paint an active range");
		assertNear(0f, TimelinePanel.timelineActiveEndTime(1f, 0f),
			"an invalid frame rate does not paint an active range");
		assertNear(60f, TimelinePanel.pannedScrollX(100f, 40f),
			"right-button drag right pans the timeline left");
		assertNear(140f, TimelinePanel.pannedScrollX(100f, -40f),
			"right-button drag left pans the timeline right");
		assertNear(130f, TimelinePanel.pannedScrollY(100f, 30f),
			"right-button drag up pans toward lower tracks");
		assertNear(70f, TimelinePanel.pannedScrollY(100f, -30f),
			"right-button drag down pans toward upper tracks");
		assertNear(-0.25f, TimelinePanel.clampedKeyframeDelta(-1f, 0.25f),
			"group movement cannot place its earliest key before zero");
		assertNear(0.5f, TimelinePanel.clampedKeyframeDelta(0.5f, 0.25f),
			"positive group movement is unchanged");
		assertTrue(TimelinePanel.hasKeyframeTimeDelta(0.00001f),
			"a representable subframe drag is not discarded as a no-op");
		assertTrue(!TimelinePanel.hasKeyframeTimeDelta(0f), "an exact zero drag remains a no-op");
		assertTrue(!TimelinePanel.sameKeyframeTime(1.00001f, 1.00002f),
			"dense subframe keys do not share selection state");
		assertTrue(TimelinePanel.sameKeyframeTime(1.00001f, 1.00001f),
			"the exact selected subframe remains selected");
		assertEquals(2, TimelinePanel.preferredCurveTrackIndex(2, 1, 0),
			"the currently selected continuous track takes curve-view priority");
		assertEquals(1, TimelinePanel.preferredCurveTrackIndex(-1, 1, 0),
			"curve view retains its track after the keyframe selection is cleared");
		assertTrue(TimelinePanel.curveBlankPressClearsSelection(false),
			"a plain curve blank press clears the current selection");
		assertTrue(!TimelinePanel.curveBlankPressClearsSelection(true),
			"an additive curve blank press preserves the current selection");
		assertNear(248f, TimelinePanel.curveContentHeight(220f, 14f, 69f),
			"the graph plot and hierarchy share the same vertical space instead of stacking");
		assertNear(276f, TimelinePanel.curveContentHeight(220f, 14f, 276f),
			"a tall graph hierarchy can still increase the canvas height");
		assertThrows(IllegalArgumentException.class,
			() -> TimelinePanel.curveContentHeight(220f, -1f, 69f),
			"negative graph spacing is rejected");
		assertEquals(0, TimelinePanel.preferredCurveTrackIndex(-1, -1, 0),
			"curve view falls back to the first keyed continuous track after an animation switch");
		assertEquals(-1, TimelinePanel.preferredCurveTrackIndex(-1, -1, -1),
			"curve view stays empty only when no continuous keyed track exists");
		assertTrue(TimelinePanel.pointInsideSelection(15f, 25f, 20f, 30f, 10f, 20f),
			"marquee selection supports reverse drag directions and inclusive edges");
		assertTrue(!TimelinePanel.pointInsideSelection(9f, 25f, 20f, 30f, 10f, 20f),
			"marquee selection rejects markers outside its bounds");
		assertNear(110f, TimelinePanel.centeredTextBaseline(100f, 20f),
			"track labels center from row and font heights");
		assertThrows(IllegalArgumentException.class,
			() -> TimelinePanel.centeredTextBaseline(0f, 0f),
			"invalid track label height is rejected");
		assertNear(113f, TimelinePanel.timelineHierarchyLabelBaseline(100f, 20f, 1f),
			"timeline hierarchy labels compensate for the desktop font's visible glyph baseline");
		assertNear(112.1f, TimelinePanel.timelineHierarchyLabelBaseline(100f, 20f, 0.7f),
			"timeline hierarchy baseline correction scales with compact rows");
		assertThrows(IllegalArgumentException.class,
			() -> TimelinePanel.timelineHierarchyLabelBaseline(100f, 20f, 0f),
			"invalid timeline hierarchy label scale is rejected");
		assertNear(10f, TimelinePanel.timelinePixelsPerFrame(TimelinePanel.DEFAULT_ZOOM),
			"default timeline zoom uses ten pixels per frame");
		assertNear(300f, TimelinePanel.timelinePixelsPerSecond(TimelinePanel.DEFAULT_ZOOM, 30f),
			"30 FPS time mapping preserves ten pixels per frame");
		assertNear(600f, TimelinePanel.timelinePixelsPerSecond(TimelinePanel.DEFAULT_ZOOM, 60f),
			"60 FPS time mapping keeps the same frame spacing");
		assertEquals(5, TimelinePanel.rulerFrameStep(300f, 30f),
			"default ruler labels every five frames");
		assertEquals(10, TimelinePanel.rulerFrameStep(150f, 30f),
			"zoomed-out ruler keeps labels readable");
		assertEquals(5, TimelinePanel.rulerFrameStep(600f, 60f),
			"ruler label density is independent of project FPS");
		assertEquals(1, TimelinePanel.rulerMinorFrameStep(50),
			"every short ruler tick represents one frame");
		assertNear(40f, (float)TimelinePanel.firstRulerFrame(1.2f, 30f, 10),
			"first ruler tick is calculated in frame space");
		assertEquals("120", TimelinePanel.rulerFrameLabel(120d),
			"ruler labels are frame numbers without a seconds suffix");
		assertEquals(10L, TimelinePanel.rulerFrameIndex(10f / 30f, 30f),
			"ruler clicks resolve to the matching project frame");
		assertEquals(10L, TimelinePanel.rulerFrameIndex(0.349f, 30f),
			"ruler selection uses the same nearest-frame rounding as key markers");
		assertEquals(11L, TimelinePanel.rulerFrameIndex(0.351f, 30f),
			"ruler selection advances after the half-frame boundary");
		assertNear(100f, TimelinePanel.rulerLabelBaseline(100f, true),
			"current frame label sits above the ruler markers");
		assertNear(95f, TimelinePanel.rulerLabelBaseline(100f, false),
			"regular frame labels retain their lower baseline");
		assertNear(660f, TimelinePanel.stickyRulerBottom(700f, 40f, 0f),
			"the ruler starts at the canvas top before vertical scrolling");
		assertNear(420f, TimelinePanel.stickyRulerBottom(700f, 40f, 240f),
			"vertical scroll offsets the ruler inside the canvas so it stays fixed in the viewport");
		assertThrows(IllegalArgumentException.class,
			() -> TimelinePanel.stickyRulerBottom(700f, 40f, -1f),
			"negative visual scroll is rejected");
		assertTrue(TimelinePanel.rulerScrubStarts(Input.Buttons.LEFT, 120f, 84f, 90f, 70f, 40f),
			"left-clicking any timeline coordinate starts a playhead drag");
		assertTrue(TimelinePanel.rulerScrubStarts(Input.Buttons.LEFT, 120f, 104f, 90f, 70f, 40f),
			"the frame-number area also starts a playhead drag");
		assertTrue(!TimelinePanel.rulerScrubStarts(Input.Buttons.LEFT, 80f, 84f, 90f, 70f, 40f),
			"the fixed track column never starts a playhead drag");
		assertTrue(!TimelinePanel.rulerScrubStarts(Input.Buttons.LEFT, 120f, 60f, 90f, 70f, 40f),
			"track rows below the ruler do not start a playhead drag");
		assertTrue(!TimelinePanel.rulerScrubStarts(Input.Buttons.RIGHT, 120f, 84f, 90f, 70f, 40f),
			"right-button ruler input remains reserved for panning");
		assertTrue(TimelinePanel.rulerScrubCommitsSelection(true, Input.Buttons.LEFT),
			"releasing a left-button ruler drag commits the frame selection");
		assertTrue(!TimelinePanel.rulerScrubCommitsSelection(false, Input.Buttons.LEFT),
			"a release without an active ruler drag does not change the frame selection");
		assertTrue(!TimelinePanel.rulerScrubCommitsSelection(true, Input.Buttons.RIGHT),
			"a non-left release cannot commit a ruler frame selection");
		assertNear(94f, TimelinePanel.centeredVisualLabelX(100f, 2f, 10f),
			"visible glyph bounds center exactly over the ruler marker");
		assertNear(10f, TimelinePanel.rulerGlyphVisualWidth(22f, 8f, 1f),
			"ruler centering ignores transparent atlas-cell padding");
		assertNear(7f, TimelinePanel.rulerGlyphVisualWidth(7f, 8f, 1f),
			"naturally tight glyph textures retain their measured width");
		assertThrows(IllegalArgumentException.class,
			() -> TimelinePanel.centeredVisualLabelX(100f, 10f, 2f),
			"reversed visible glyph bounds are rejected");
		assertEquals("旋转", TimelinePanel.propLabel(AnimationEditor.Prop.ROTATE), "rotate track label");
		assertEquals("角度", TimelinePanel.valueLabel(AnimationEditor.Prop.ROTATE, 0), "rotate value label");
		assertEquals("X", TimelinePanel.valueLabel(AnimationEditor.Prop.TRANSLATE, 0), "translate X label");
		assertEquals("Y", TimelinePanel.valueLabel(AnimationEditor.Prop.TRANSLATE, 1), "translate Y label");
		assertEquals("Y", TimelinePanel.valueLabel(AnimationEditor.Prop.TRANSLATE_Y, 0), "single translate label");
		assertEquals("X 倍率", TimelinePanel.valueLabel(AnimationEditor.Prop.SCALE, 0), "scale X label");
		assertEquals("Y 倍率", TimelinePanel.valueLabel(AnimationEditor.Prop.SCALE_Y, 0), "single scale label");
		assertEquals("X 角度", TimelinePanel.valueLabel(AnimationEditor.Prop.SHEAR, 0), "shear X label");
		assertEquals("Y 角度", TimelinePanel.valueLabel(AnimationEditor.Prop.SHEAR_Y, 0), "single shear label");
		assertEquals("数值", TimelinePanel.valueLabel(null, 0), "empty selection label");
		assertNear(TimelinePanel.DEFAULT_ZOOM, TimelinePanel.normalizeZoom(Float.NaN),
			"invalid timeline zoom restores the default");
		assertNear(25f, TimelinePanel.normalizeZoom(1f), "timeline zoom clamps to minimum");
		assertNear(150f, TimelinePanel.normalizeZoom(999f), "timeline zoom clamps to maximum");
		assertNear(35f, TimelinePanel.normalizeZoom(36f), "timeline zoom snaps to slider steps");
		assertEquals("缩放: 1.00×", TimelinePanel.timelineZoomLabel(TimelinePanel.DEFAULT_ZOOM),
			"default timeline zoom is displayed as a resettable multiplier");
		assertEquals("缩放: 0.50×", TimelinePanel.timelineZoomLabel(25f),
			"zoomed-out timeline multiplier is displayed precisely");
		assertEquals("缩放: 3.00×", TimelinePanel.timelineZoomLabel(150f),
			"maximum timeline zoom is displayed relative to the default");
		MeshAttachment sourceMesh = new MeshAttachment("source", new Sequence(1, false));
		MeshAttachment linkedMesh = new MeshAttachment("linked", new Sequence(1, false));
		linkedMesh.setSourceMesh(sourceMesh);
		linkedMesh.setTimelineAttachment(sourceMesh);
		assertTrue(TimelinePanel.ownsDeformTimeline(sourceMesh),
			"source Mesh owns its Deform timeline row");
		assertTrue(!TimelinePanel.ownsDeformTimeline(linkedMesh),
			"inherited Linked Mesh does not duplicate the source Deform timeline row");
		assertTrue(TimelinePanel.ownsSequenceTimeline(sourceMesh),
			"source Mesh owns its Sequence timeline row");
		assertTrue(!TimelinePanel.ownsSequenceTimeline(linkedMesh),
			"inherited Linked Mesh does not expose an ineffective direct Sequence row");
		linkedMesh.setTimelineAttachment(linkedMesh);
		assertTrue(TimelinePanel.ownsDeformTimeline(linkedMesh),
			"independent Linked Mesh keeps its own Deform timeline row");
		assertTrue(TimelinePanel.ownsSequenceTimeline(linkedMesh),
			"independent Linked Mesh keeps its own Sequence timeline row");
		assertEquals(TimelinePanel.FILTER_ALL, TimelinePanel.normalizeTrackFilterKey(null),
			"missing track filter restores all tracks");
		assertEquals(TimelinePanel.FILTER_SELECTED, TimelinePanel.normalizeTrackFilterKey("selected"),
			"selected-bone track filter is preserved");
		assertEquals(TimelinePanel.FILTER_KEYED, TimelinePanel.normalizeTrackFilterKey("keyed"),
			"keyed track filter is preserved");
		assertEquals(TimelinePanel.SearchKeyAction.CLEAR_SEARCH,
			TimelinePanel.searchKeyAction(Input.Keys.ESCAPE, true), "Escape clears a populated track search");
		assertEquals(TimelinePanel.SearchKeyAction.FOCUS_CANVAS,
			TimelinePanel.searchKeyAction(Input.Keys.ESCAPE, false), "Escape leaves an empty track search");
		assertEquals(TimelinePanel.SearchKeyAction.FOCUS_CANVAS,
			TimelinePanel.searchKeyAction(Input.Keys.ENTER, true), "Enter returns focus to the timeline canvas");
		assertEquals(TimelinePanel.SearchKeyAction.FOCUS_CANVAS,
			TimelinePanel.searchKeyAction(Input.Keys.DOWN, true), "Down returns focus to the timeline canvas");
		assertEquals(TimelinePanel.SearchKeyAction.NONE,
			TimelinePanel.searchKeyAction(Input.Keys.A, true), "typing remains in the search field");
		assertEquals(TimelinePanel.RebuildSelection.CLEAR,
			TimelinePanel.rebuildSelection(false, false), "deleted keyframe selection is cleared");
		assertEquals(TimelinePanel.RebuildSelection.VISIBLE,
			TimelinePanel.rebuildSelection(true, true), "visible keyframe selection remains editable");
		assertEquals(TimelinePanel.RebuildSelection.HIDDEN,
			TimelinePanel.rebuildSelection(true, false), "filtered keyframe selection is retained but hidden");
		assertNear(30f, TimelinePanel.DEFAULT_PROJECT_FPS, "FPS reset target remains the project default");
		assertNear(60f, TimelinePanel.parseProjectFps(" 60 "), "valid project FPS is parsed");
		assertTrue(TimelinePanel.parseProjectFps("") == null, "blank project FPS is rejected");
		assertTrue(TimelinePanel.parseProjectFps("NaN") == null, "non-finite project FPS is rejected");
		assertTrue(TimelinePanel.parseProjectFps("Infinity") == null, "infinite project FPS is rejected");
		assertTrue(TimelinePanel.projectFpsDraftIsEquivalent(30f, "30.0"),
			"equivalent project FPS notation is unchanged");
		assertTrue(!TimelinePanel.projectFpsDraftIsEquivalent(30f, "60"),
			"a different valid project FPS remains submittable");
		assertTrue(!TimelinePanel.projectFpsDraftIsEquivalent(30f, "invalid"),
			"invalid project FPS text still reaches validation");
		int[] fpsApplies = {0};
		com.badlogic.gdx.scenes.scene2d.utils.FocusListener fpsFocus =
			TimelinePanel.applyFpsOnFocusLost(() -> fpsApplies[0]++);
		fpsFocus.keyboardFocusChanged(new com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent(),
			new com.badlogic.gdx.scenes.scene2d.Actor(), true);
		assertEquals(0, fpsApplies[0], "focusing the FPS field preserves the unfinished value");
		fpsFocus.keyboardFocusChanged(new com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent(),
			new com.badlogic.gdx.scenes.scene2d.Actor(), false);
		assertEquals(1, fpsApplies[0], "leaving the FPS field automatically applies the completed value");
		TimelinePanel.KeyframeDraft boneDraft = TimelinePanel.boneKeyframeDraft("0.5", "1", "ignored", false,
			0);
		assertEquals(boneDraft, TimelinePanel.boneKeyframeDraft("5e-1", "1.0", "different ignored", false, 0),
			"equivalent single-value bone drafts ignore formatting and the unused second value");
		assertTrue(!boneDraft.equals(TimelinePanel.boneKeyframeDraft("0.5", "2", "ignored", false, 0)),
			"changed bone values enable Apply");
		assertTrue(!boneDraft.equals(TimelinePanel.boneKeyframeDraft("0.5", "1", "ignored", false, 1)),
			"changed interpolation enables Apply");
		assertTrue(!boneDraft.equals(TimelinePanel.boneKeyframeDraft("invalid", "1", "ignored", false, 0)),
			"invalid edited time remains a changed draft for validation");
		float[] deformOffsets = TimelinePanel.parseDeformOffsets("1, 2\n3.5 -4e-1", 4);
		assertNear(1f, deformOffsets[0], "Deform parser reads comma separated X");
		assertNear(2f, deformOffsets[1], "Deform parser reads comma separated Y");
		assertNear(3.5f, deformOffsets[2], "Deform parser reads line separated X");
		assertNear(-0.4f, deformOffsets[3], "Deform parser reads scientific notation");
		assertEquals("1, 2\n3.5, -0.4", TimelinePanel.formatDeformOffsets(deformOffsets),
			"Deform offsets format as editable coordinate pairs");
		assertThrows(IllegalArgumentException.class,
			() -> TimelinePanel.parseDeformOffsets("1 2 3", 4),
			"Deform parser rejects an incomplete coordinate array");
		assertThrows(IllegalArgumentException.class,
			() -> TimelinePanel.parseDeformOffsets("1 2 NaN 4", 4),
			"Deform parser rejects non-finite coordinates");
		TimelinePanel.KeyframeDraft deformDraft = TimelinePanel.deformKeyframeDraft("1", deformOffsets, 0);
		assertEquals(deformDraft, TimelinePanel.deformKeyframeDraft("1.0", deformOffsets.clone(), 0),
			"equivalent Deform drafts ignore time formatting and array identity");
		assertTrue(!deformDraft.equals(TimelinePanel.deformKeyframeDraft("1", new float[] {1f, 2f, 3f, 0f}, 0)),
			"changed Deform offsets enable Apply");
		TimelinePanel.KeyframeDraft colorDraft = TimelinePanel.colorKeyframeDraft("1", "#FFFFFFFF", false, 0);
		assertEquals(colorDraft, TimelinePanel.colorKeyframeDraft("1e0", "#ffffff", false, 0),
			"equivalent RGBA notation does not enable Apply");
		TimelinePanel.KeyframeDraft rgbDraft = TimelinePanel.colorKeyframeDraft("1", "#AABBCC", true, 0);
		assertEquals(rgbDraft, TimelinePanel.colorKeyframeDraft("1.0", "#aabbcc", true, 0),
			"equivalent RGB notation does not enable Apply");
		TimelinePanel.KeyframeDraft alphaDraft = TimelinePanel.alphaKeyframeDraft("1", "0.5", 0);
		assertEquals(alphaDraft, TimelinePanel.alphaKeyframeDraft("1e0", "5e-1", 0),
			"equivalent alpha notation does not enable Apply");
		assertTrue(!alphaDraft.equals(TimelinePanel.alphaKeyframeDraft("1", "0.25", 0)),
			"changed alpha values enable Apply");
		assertTrue(!alphaDraft.equals(TimelinePanel.alphaKeyframeDraft("1", "0.5", 1)),
			"changed alpha interpolation enables Apply");
		TimelinePanel.KeyframeDraft twoColorDraft = TimelinePanel.twoColorKeyframeDraft("1", "#FFFFFF", true,
			"#000000", 0);
		assertEquals(twoColorDraft, TimelinePanel.twoColorKeyframeDraft("1.0", "#ffffff", true,
			"#000000", 0), "equivalent RGB2 notation does not enable Apply");
		assertTrue(!twoColorDraft.equals(TimelinePanel.twoColorKeyframeDraft("1", "#FFFFFF", true,
			"invalid", 0)), "invalid dark color remains submittable for validation");
		assertEquals(TimelinePanel.eventKeyframeDraft("1", "marker"),
			TimelinePanel.eventKeyframeDraft("1.0", " marker "), "event drafts normalize time and selection text");
		assertTrue(!TimelinePanel.attachmentKeyframeDraft("1", "a").equals(
			TimelinePanel.attachmentKeyframeDraft("1", "b")), "attachment selection changes enable Apply");
		String[] sequenceModes = TimelinePanel.sequenceModeNames();
		assertEquals(SequenceMode.values().length, sequenceModes.length,
			"timeline exposes every Sequence playback mode");
		for (int index = 0; index < sequenceModes.length; index++)
			assertTrue(TimelinePanel.sequenceMode(sequenceModes[index]) == SequenceMode.values()[index],
				"Sequence mode names preserve runtime enum order");
		assertTrue(TimelinePanel.sequenceMode("invalid") == null, "unknown Sequence modes are rejected");
		assertEquals(12, TimelinePanel.parseSequenceIndex("12"), "Sequence index parser accepts integers");
		assertEquals(12, TimelinePanel.parseSequenceIndex("1.2e1"),
			"Sequence index parser accepts equivalent exact numeric notation");
		assertThrows(IllegalArgumentException.class, () -> TimelinePanel.parseSequenceIndex("1.5"),
			"Sequence index parser rejects fractions");
		assertThrows(IllegalArgumentException.class, () -> TimelinePanel.parseSequenceIndex("1048576"),
			"Sequence index parser rejects values that cannot be packed exactly");
		TimelinePanel.KeyframeDraft sequenceDraft = TimelinePanel.sequenceKeyframeDraft(
			"1", "loop", "2", "0.125");
		assertEquals(sequenceDraft, TimelinePanel.sequenceKeyframeDraft("1.0", "loop", "2e0", ".125"),
			"equivalent Sequence keyframe notation does not enable Apply");
		assertTrue(!sequenceDraft.equals(TimelinePanel.sequenceKeyframeDraft("1", "once", "2", "0.125")),
			"changed Sequence mode enables Apply");
		assertTrue(!sequenceDraft.equals(TimelinePanel.sequenceKeyframeDraft("1", "loop", "3", "0.125")),
			"changed Sequence start index enables Apply");
		assertTrue(!sequenceDraft.equals(TimelinePanel.sequenceKeyframeDraft("1", "loop", "2", "0.25")),
			"changed Sequence delay enables Apply");
		String[] inheritLabels = TimelinePanel.inheritLabels();
		assertTrue(inheritLabels.length == Inherit.values().length, "timeline exposes every inherit mode");
		for (int index = 0; index < inheritLabels.length; index++)
			assertTrue(PropertiesPanel.inheritMode(inheritLabels[index]) == Inherit.values()[index],
				"timeline inherit labels preserve runtime enum order");
		assertTrue(!TimelinePanel.inheritKeyframeDraft("1", inheritLabels[0]).equals(
			TimelinePanel.inheritKeyframeDraft("1", inheritLabels[1])),
			"inherit selection changes enable Apply");
		assertEquals(TimelinePanel.drawOrderKeyframeDraft("1", null),
			TimelinePanel.drawOrderKeyframeDraft("1.0", null),
			"setup draw-order drafts normalize equivalent time notation");
		assertEquals(TimelinePanel.drawOrderKeyframeDraft("1", new int[] {2, 0, 1}),
			TimelinePanel.drawOrderKeyframeDraft("1e0", new int[] {2, 0, 1}),
			"equal draw-order permutations produce equal drafts");
		assertTrue(!TimelinePanel.drawOrderKeyframeDraft("1", new int[] {2, 0, 1}).equals(
			TimelinePanel.drawOrderKeyframeDraft("1", new int[] {0, 2, 1})),
			"changed draw-order permutations enable Apply");
		TimelinePanel.KeyframeDraft ikDraft = TimelinePanel.ikKeyframeDraft("1", "0.5", "2", 1,
			false, true, 0);
		assertEquals(ikDraft, TimelinePanel.ikKeyframeDraft("1e0", "5e-1", "2.0", 1,
			false, true, 0), "equivalent IK numeric notation does not enable Apply");
		assertTrue(!ikDraft.equals(TimelinePanel.ikKeyframeDraft("1", "0.75", "2", 1,
			false, true, 0)), "changed IK mix enables Apply");
		assertTrue(!ikDraft.equals(TimelinePanel.ikKeyframeDraft("1", "0.5", "3", 1,
			false, true, 0)), "changed IK softness enables Apply");
		assertTrue(!ikDraft.equals(TimelinePanel.ikKeyframeDraft("1", "0.5", "2", -1,
			false, true, 0)), "changed IK bend direction enables Apply");
		assertTrue(!ikDraft.equals(TimelinePanel.ikKeyframeDraft("1", "0.5", "2", 1,
			true, true, 0)), "changed IK compression enables Apply");
		assertTrue(!ikDraft.equals(TimelinePanel.ikKeyframeDraft("1", "0.5", "2", 1,
			false, false, 0)), "changed IK stretch enables Apply");
		assertEquals("正向", TimelinePanel.ikBendLabel(1), "positive IK bend label");
		assertEquals("反向", TimelinePanel.ikBendLabel(-1), "negative IK bend label");
		assertEquals(-1, TimelinePanel.ikBendDirection("反向"), "negative IK bend direction");
		assertEquals(1, TimelinePanel.ikBendDirection("正向"), "positive IK bend direction");
		assertEquals("反向 · 压缩 · 拉伸", TimelinePanel.ikOptionsSummary(
			new AnimationEditor.IkKeyframe(1f, 0f, -1, true, true)), "IK discrete options summary");
		assertEquals("Setup 顺序", TimelinePanel.drawOrderSummary(null, 3),
			"setup draw order has a clear summary");
		assertEquals("已调整 3/3 个槽", TimelinePanel.drawOrderSummary(new int[] {2, 0, 1}, 3),
			"draw-order summary counts moved slots");
		Array<String> drawOrderNames = new Array<>(new String[] {"back", "middle", "front"});
		assertEquals(1, TimelinePanel.moveDrawOrderEntry(drawOrderNames, 0, 1),
			"moving a draw-order entry returns its new index");
		assertEquals("middle", drawOrderNames.get(0), "moving down swaps the preceding entry");
		assertEquals(0, TimelinePanel.moveDrawOrderEntry(drawOrderNames, 1, -1),
			"moving a draw-order entry back restores its index");
		assertEquals("back", drawOrderNames.get(0), "moving up restores the original order");
		EventData eventData = new EventData("marker");
		EventTimeline events = new EventTimeline(4);
		for (int frame = 0; frame < 4; frame++)
			events.setFrame(frame, new Event(frame < 3 ? 0.5f : 1f, eventData));
		assertNear(-6f, TimelinePanel.eventMarkerOffset(events, 0), "first same-time event marker offset");
		assertNear(0f, TimelinePanel.eventMarkerOffset(events, 1), "middle same-time event marker offset");
		assertNear(6f, TimelinePanel.eventMarkerOffset(events, 2), "last same-time event marker offset");
		assertNear(0f, TimelinePanel.eventMarkerOffset(events, 3), "single event marker stays centered");
		assertEquals(" · 同时刻 2/3", TimelinePanel.eventOccurrenceLabel(events, 1),
			"same-time event selection shows its exact position");
		assertEquals("", TimelinePanel.eventOccurrenceLabel(events, 3),
			"single event selection has no occurrence suffix");
		Array<String> eventNames = new Array<>();
		eventNames.add("step");
		eventNames.add("marker");
		assertEquals("marker", TimelinePanel.preferredEventName(eventNames, "marker"),
			"existing preferred event definition is retained");
		assertEquals("step", TimelinePanel.preferredEventName(eventNames, "removed"),
			"stale preferred event definition falls back to the first option");
		SkeletonData eventDefinitionData = new SkeletonData();
		EventData eventDefinition = new EventData("marker");
		eventDefinitionData.getEvents().add(eventDefinition);
		assertTrue(TimelinePanel.findEventDefinition(eventDefinitionData, null) == null,
			"missing event selection is safe");
		assertTrue(TimelinePanel.findEventDefinition(eventDefinitionData, "") == null,
			"blank event selection is safe");
		assertTrue(TimelinePanel.findEventDefinition(eventDefinitionData, "marker") == eventDefinition,
			"existing event selection resolves to the exact definition");
		BoneData root = new BoneData(0, "root", null);
		BoneData sameName = new BoneData(1, "root", null);
		IkConstraintData ik = new IkConstraintData("root-ik");
		ik.setTarget(root);
		ik.getBones().add(sameName);
		assertTrue(TimelinePanel.ikAffectsBone(ik, root), "IK selected-track filter includes its target");
		assertTrue(TimelinePanel.ikAffectsBone(ik, sameName), "IK selected-track filter includes constrained bones");
		assertTrue(!TimelinePanel.ikAffectsBone(ik, new BoneData(2, "other", null)),
			"IK selected-track filter excludes unrelated bones");
		TransformConstraintData transform = new TransformConstraintData("follow");
		transform.setSource(root);
		transform.getBones().add(sameName);
		assertTrue(TimelinePanel.transformAffectsBone(transform, root),
			"Transform selected-track filter includes its source");
		assertTrue(TimelinePanel.transformAffectsBone(transform, sameName),
			"Transform selected-track filter includes constrained bones");
		assertTrue(!TimelinePanel.transformAffectsBone(transform, new BoneData(2, "other", null)),
			"Transform selected-track filter excludes unrelated bones");
		assertEquals(TimelinePanel.transformKeyframeDraft(" 1.0 ", "0.1", ".2", "3e-1", "0.4", ".5", "0.6", 0),
			TimelinePanel.transformKeyframeDraft("1", ".1", "0.2", ".3", ".4", "0.50", ".6", 0),
			"equivalent Transform keyframe numbers compare semantically");
		SlotData rootSlot = new SlotData(0, "root-slot", root);
		SlotData otherSlot = new SlotData(1, "other-slot", sameName);
		PathConstraintData path = new PathConstraintData("route");
		path.setSlot(rootSlot);
		path.getBones().add(sameName);
		assertTrue(TimelinePanel.pathAffectsBone(path, root),
			"Path selected-track filter includes the target slot bone");
		assertTrue(TimelinePanel.pathAffectsBone(path, sameName),
			"Path selected-track filter includes constrained bones");
		assertTrue(!TimelinePanel.pathAffectsBone(path, new BoneData(2, "other", null)),
			"Path selected-track filter excludes unrelated bones");
		assertEquals("位置", TimelinePanel.pathPropLabel(AnimationEditor.PathProp.POSITION),
			"Path position track label");
		assertEquals(AnimationEditor.PathProp.POSITION,
			TimelinePanel.pathPropOf(new PathConstraintPositionTimeline(1, 0, 0)),
			"Path position timeline type is recognized");
		assertEquals(AnimationEditor.PathProp.SPACING,
			TimelinePanel.pathPropOf(new PathConstraintSpacingTimeline(1, 0, 0)),
			"Path spacing timeline type is recognized");
		assertEquals(AnimationEditor.PathProp.MIX,
			TimelinePanel.pathPropOf(new PathConstraintMixTimeline(1, 0, 0)),
			"Path mix timeline type is recognized");
		assertEquals(TimelinePanel.pathValueKeyframeDraft("1", AnimationEditor.PathProp.POSITION, "0.5", 0),
			TimelinePanel.pathValueKeyframeDraft("1.0", AnimationEditor.PathProp.POSITION, "5e-1", 0),
			"equivalent Path position keyframe numbers compare semantically");
		assertTrue(!TimelinePanel.pathValueKeyframeDraft("1", AnimationEditor.PathProp.POSITION, "0.5", 0).equals(
			TimelinePanel.pathValueKeyframeDraft("1", AnimationEditor.PathProp.SPACING, "0.5", 0)),
			"Path position and spacing clipboard kinds remain distinct");
		assertEquals(TimelinePanel.pathMixKeyframeDraft("1", "0.1", "0.2", "0.3", 0),
			TimelinePanel.pathMixKeyframeDraft("1.0", ".1", "2e-1", ".30", 0),
			"equivalent Path mix keyframe numbers compare semantically");
		PhysicsConstraintData physics = new PhysicsConstraintData("swing");
		physics.setBone(root);
		physics.setGravityGlobal(true);
		assertTrue(TimelinePanel.physicsAffectsBone(physics, root),
			"Physics selected-track filter includes its constrained bone");
		assertTrue(!TimelinePanel.physicsAffectsBone(physics, sameName),
			"Physics selected-track filter excludes unrelated bones");
		assertTrue(TimelinePanel.physicsPropertyIsGlobal(physics, AnimationEditor.PhysicsProp.GRAVITY),
			"Physics global-property routing reads the constraint flag");
		assertTrue(!TimelinePanel.physicsPropertyIsGlobal(physics, AnimationEditor.PhysicsProp.MIX),
			"Physics local properties remain constraint-specific");
		assertEquals("质量", TimelinePanel.physicsPropLabel(AnimationEditor.PhysicsProp.MASS),
			"Physics mass track label");
		PhysicsConstraintPose physicsPose = new PhysicsConstraintPose();
		physicsPose.setMassInverse(0.25f);
		assertNear(4f, TimelinePanel.physicsPoseValue(physicsPose, AnimationEditor.PhysicsProp.MASS),
			"Physics setup mass converts from the runtime inverse representation");
		assertEquals(TimelinePanel.physicsValueKeyframeDraft("1", AnimationEditor.PhysicsProp.GRAVITY, "-9.8", 0),
			TimelinePanel.physicsValueKeyframeDraft("1.0", AnimationEditor.PhysicsProp.GRAVITY, "-9.80", 0),
			"equivalent Physics keyframe numbers compare semantically");
		assertTrue(!TimelinePanel.physicsValueKeyframeDraft("1", AnimationEditor.PhysicsProp.GRAVITY, "-9.8", 0)
			.equals(TimelinePanel.physicsValueKeyframeDraft("1", AnimationEditor.PhysicsProp.WIND, "-9.8", 0)),
			"Physics clipboard kinds remain property-specific");
		assertEquals(TimelinePanel.physicsResetKeyframeDraft("1"),
			TimelinePanel.physicsResetKeyframeDraft("1.0"),
			"Physics reset drafts normalize equivalent time notation");
		SliderData slider = new SliderData("driver");
		slider.setBone(root);
		assertTrue(!TimelinePanel.sliderPropVisible(slider, AnimationEditor.SliderProp.TIME),
			"property-driven Slider hides its ineffective time track");
		assertTrue(TimelinePanel.sliderPropVisible(slider, AnimationEditor.SliderProp.MIX),
			"property-driven Slider keeps its mix track visible");
		SliderData timeDrivenSlider = new SliderData("time-driver");
		assertTrue(TimelinePanel.sliderPropVisible(timeDrivenSlider, AnimationEditor.SliderProp.TIME),
			"time-driven Slider keeps its time track visible");
		assertTrue(TimelinePanel.sliderAffectsBone(slider, root),
			"Slider selected-track filter includes its driving bone");
		assertTrue(!TimelinePanel.sliderAffectsBone(slider, sameName),
			"Slider selected-track filter excludes unrelated bones");
		SkeletonData sliderData = new SkeletonData();
		BoneData sliderTarget = new BoneData(1, "target", root);
		sliderData.getBones().add(root);
		sliderData.getBones().add(sliderTarget);
		Animation driven = new Animation("driven");
		IntArray drivenBones = new IntArray();
		drivenBones.add(sliderTarget.getIndex());
		driven.setTimelines(Array.of(Animation.Timeline[]::new), drivenBones);
		slider.setAnimation(driven);
		assertTrue(TimelinePanel.sliderAffectsBone(slider, sliderData, sliderTarget),
			"Slider selected-track filter includes bones modified by its target animation");
		assertEquals("时间", TimelinePanel.sliderPropLabel(AnimationEditor.SliderProp.TIME),
			"Slider time track label");
		assertEquals(AnimationEditor.SliderProp.TIME,
			TimelinePanel.sliderPropOf(new SliderTimeline(1, 0, 0)),
			"Slider time timeline type is recognized");
		assertEquals(AnimationEditor.SliderProp.MIX,
			TimelinePanel.sliderPropOf(new SliderMixTimeline(1, 0, 0)),
			"Slider mix timeline type is recognized");
		assertEquals(TimelinePanel.sliderValueKeyframeDraft("1", AnimationEditor.SliderProp.TIME, "0.5", 0),
			TimelinePanel.sliderValueKeyframeDraft("1.0", AnimationEditor.SliderProp.TIME, "5e-1", 0),
			"equivalent Slider keyframe numbers compare semantically");
		assertTrue(!TimelinePanel.sliderValueKeyframeDraft("1", AnimationEditor.SliderProp.TIME, "0.5", 0)
			.equals(TimelinePanel.sliderValueKeyframeDraft("1", AnimationEditor.SliderProp.MIX, "0.5", 0)),
			"Slider time and mix clipboard kinds remain distinct");
		Array<SlotData> slots = new Array<>();
		slots.add(rootSlot);
		slots.add(otherSlot);
		IdentityHashMap<BoneData, Array<SlotData>> slotsByBone = TimelinePanel.indexSlotsByBone(slots);
		assertEquals(2, slotsByBone.size(), "slot index keeps distinct bone identities");
		assertTrue(slotsByBone.get(root).first() == rootSlot, "root slot is indexed under its exact bone");
		assertTrue(slotsByBone.get(sameName).first() == otherSlot,
			"same-name bone does not receive another bone's slots");
		assertEquals(TimelinePanel.SlotProp.TWO_COLOR,
			TimelinePanel.primarySlotProp(false, false, true, false, true),
			"an alpha-only dark slot keeps the RGB2-compatible primary track");
		assertEquals(TimelinePanel.SlotProp.RGB,
			TimelinePanel.primarySlotProp(false, false, true, false, false),
			"an alpha-only regular slot exposes an RGB companion track");
		assertEquals(TimelinePanel.SlotProp.RGB,
			TimelinePanel.primarySlotProp(false, true, true, false, true),
			"an existing RGB track remains primary even on a dark slot");
		assertEquals(TimelinePanel.SlotProp.TWO_COLOR,
			TimelinePanel.primarySlotProp(false, true, true, true, true),
			"an existing two-color track takes precedence over RGB");
		System.out.println("ALL TIMELINE PANEL TESTS PASSED");
	}

	private static SkeletonData attachmentData() {
		SkeletonData data = new SkeletonData();
		BoneData bone = new BoneData(0, "root", null);
		data.getBones().add(bone);
		data.getSlots().add(new SlotData(0, "人物/面部", bone));
		return data;
	}

	private static Skin skin(SkeletonData data, String name, MeshAttachment mesh) {
		Skin skin = new Skin(name);
		skin.setAttachment(0, "相同占位", mesh);
		data.getSkins().add(skin);
		return skin;
	}

	private static void attachmentTracksHaveSkinQualifiedIdentity() {
		SkeletonData data = attachmentData();
		MeshAttachment original = new MeshAttachment("相同附件", new Sequence(2, true));
		MeshAttachment combined = new MeshAttachment("相同附件", new Sequence(2, true));
		skin(data, "原始/冬装", original);
		skin(data, "Permanent / 组合", combined);
		MeshAttachment linked = new MeshAttachment("linked", new Sequence(2, true));
		linked.setSourceMesh(original); linked.setTimelineAttachment(original);
		skin(data, "Linked 显示皮肤", linked);
		AttachmentTrackLabels.Index index = new AttachmentTrackLabels.Index(data);
		var first = index.target(0, original, "fallback");
		var second = index.target(0, combined, "fallback");
		assertTrue(!first.rowLabel("Deform").equals(second.rowLabel("Deform")), "same-named Deform rows expose their skin");
		assertTrue(second.rowLabel("Sequence").startsWith("Sequence · ") && second.rowLabel("Deform").startsWith("Deform · "),
			"long skin names cannot hide the attachment track type");
		assertTrue(second.fullLabel("Sequence").contains("Permanent / 组合")
			&& second.fullLabel("Sequence").contains("人物/面部") && second.fullLabel("Sequence").contains("相同附件"),
			"full labels retain skin, slot, placeholder and attachment");
		assertTrue(second.matches("Sequence", "PERMANENT") && !first.matches("Sequence", "PERMANENT"), "skin searches are independent and case insensitive");
		assertTrue(first.matches("Deform", "Linked 显示皮肤") && first.tooltip("Deform").contains("继承此轨道"),
			"searching an inherited skin explains its real timeline owner");
		for (String kind : new String[] {"deform", "sequence"}) {
			String key = second.address().stateKey(kind);
			assertTrue(!key.equals(first.address().stateKey(kind)), "same names cannot collide across skins: " + kind);
			assertTrue(index.resolveKey(key).attachment() == combined, "qualified key resolves its exact attachment: " + kind);
			assertTrue(index.resolveKey(first.address().legacyKey(kind)) == null, "ambiguous legacy key never picks the first skin: " + kind);
		}
		data.getSkins().reverse();
		AttachmentTrackLabels.Index reordered = new AttachmentTrackLabels.Index(data);
		assertEquals(second.address().stateKey("deform"), reordered.target(0, combined, "fallback").address().stateKey("deform"),
			"skin enumeration order does not change the persisted identity");
		assertTrue(!TimelinePanel.ownsDeformTimeline(linked) && !TimelinePanel.ownsSequenceTimeline(linked),
			"inherited aliases do not become ineffective independent tracks");
	}

	private static void sharedAttachmentAddressesSurviveSkinEdits() {
		SkeletonData data = attachmentData();
		MeshAttachment shared = new MeshAttachment("相同附件", new Sequence(2, true));
		Skin a = skin(data, "A", shared), b = skin(data, "B", shared);
		AttachmentTrackLabels.Index original = new AttachmentTrackLabels.Index(data);
		String key = original.target(0, shared, "").address().stateKey("deform");
		String legacy = original.target(0, shared, "").address().legacyKey("deform");
		assertEquals(key, original.migrateUniqueLegacyKey(legacy), "shared registrations still have one legacy timeline owner");
		SkeletonDocument doc = new SkeletonDocument(); doc.data = data;
		doc.setTimelineViewState(new SkeletonDocument.TimelineViewState("保持动画", false, 0, key, 5f, 7f, true,
			new String[] {key}, new float[] {0.625f}, new int[] {0}));
		doc.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(true, "保持动画", "search", true, key,
			0.5f, 3f, 4f, 10f, 20f, 640f, 480f));
		doc.renameEditorSkinState("A", "Z");
		String renamedKey = doc.timelineViewState().selectedTrackKeys()[0];
		assertTrue(renamedKey.endsWith("\u001fZ") && doc.graphWorkspaceState().detailTrackKey().equals(renamedKey), "rename migrates both timeline and Graph keys");
		assertNear(0.625f, doc.timelineViewState().selectedTimes()[0], "rename preserves selected key time");
		assertEquals("保持动画", doc.timelineViewState().animation(), "rename preserves selected animation");
		data.getSkins().removeValue(a, true); skin(data, "Z", shared);
		AttachmentTrackLabels.Index renamed = new AttachmentTrackLabels.Index(data);
		String canonicalB = renamed.target(0, shared, "").address().stateKey("deform");
		assertTrue(canonicalB.endsWith("\u001fB") && renamed.resolveKey(renamedKey).attachment() == shared,
			"renamed shared alias resolves even when another skin becomes canonical");
		assertEquals(canonicalB, renamed.migrateKey(original, key), "live rebuild migrates removed canonical address by attachment identity");
		data.getSkins().removeValue(b, true);
		AttachmentTrackLabels.Index removed = new AttachmentTrackLabels.Index(data);
		String canonicalZ = removed.target(0, shared, "").address().stateKey("deform");
		assertEquals(canonicalZ, removed.migrateKey(renamed, canonicalB), "deleting canonical shared skin preserves the surviving owner selection");
		data.getSkins().add(b);
		AttachmentTrackLabels.Index restored = new AttachmentTrackLabels.Index(data);
		assertEquals(canonicalB, restored.migrateKey(removed, canonicalZ), "undo canonical skin removal restores a resolvable identity");
		doc.renameEditorSkinState("Z", "A");
		assertEquals(key, doc.timelineViewState().selectedTrackKeys()[0], "undo skin rename restores the original selection key");
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
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

	private TimelinePanelTest() {}
}
