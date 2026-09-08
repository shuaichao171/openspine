package com.openspine.view;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.SkeletonAttachment;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.AnimationEditor;
import com.openspine.model.BoneCoordinateSpace;
import com.openspine.model.BoneSelectionTransform;
import com.openspine.model.BoneSelectionTransformTest;
import com.openspine.model.BoneKeyframeBatch;

/** Headless regression tests for viewport input and framing decisions. */
public final class EditorViewportTest {
	public static void main(String[] args) {
		bonePickingMatchesDrawnGeometry();
		pixelSnappingPreservesRotatedAxes();
		preservesViewportBoneMultiSelection();
		coordinateAxisAutoKeyPreservesTheCompleteWorldMove();
		assertTrue(EditorViewport.autoKeyProp(EditorTool.ROTATE, EditorViewport.TransformAxis.ROTATE)
			== AnimationEditor.Prop.ROTATE, "rotation drags Auto Key only rotation");
		assertTrue(EditorViewport.autoKeyProp(EditorTool.MOVE, EditorViewport.TransformAxis.FREE)
			== AnimationEditor.Prop.TRANSLATE, "free move drags Auto Key both translation axes");
		assertTrue(EditorViewport.autoKeyProp(EditorTool.MOVE, EditorViewport.TransformAxis.X)
			== AnimationEditor.Prop.TRANSLATE_X, "move X handles Auto Key translation X");
		assertTrue(EditorViewport.autoKeyProp(EditorTool.MOVE, EditorViewport.TransformAxis.Y)
			== AnimationEditor.Prop.TRANSLATE_Y, "move Y handles Auto Key translation Y");
		assertTrue(EditorViewport.autoKeyProp(EditorTool.SCALE, EditorViewport.TransformAxis.FREE)
			== AnimationEditor.Prop.SCALE, "free scale drags Auto Key both scale axes");
		assertTrue(EditorViewport.autoKeyProp(EditorTool.SCALE, EditorViewport.TransformAxis.X)
			== AnimationEditor.Prop.SCALE_X, "scale X handles Auto Key scale X");
		assertTrue(EditorViewport.autoKeyProp(EditorTool.SCALE, EditorViewport.TransformAxis.Y)
			== AnimationEditor.Prop.SCALE_Y, "scale Y handles Auto Key scale Y");
		assertTrue(EditorViewport.autoKeyProp(EditorTool.SHEAR, EditorViewport.TransformAxis.X)
			== AnimationEditor.Prop.SHEAR_X, "shear X handles Auto Key shear X");
		assertTrue(EditorViewport.autoKeyProp(EditorTool.SHEAR, EditorViewport.TransformAxis.Y)
			== AnimationEditor.Prop.SHEAR_Y, "shear Y handles Auto Key shear Y");
		assertTrue(EditorViewport.autoKeyProp(EditorTool.SELECT, EditorViewport.TransformAxis.FREE) == null,
			"non-transform tools never trigger Auto Key");
		assertTrue(EditorViewport.isUsableAttachmentBounds(new Vector2(-20f, -10f), new Vector2(80f, 40f)),
			"finite attachment bounds are usable");
		assertTrue(EditorViewport.isUsableAttachmentBounds(new Vector2(4f, 5f), new Vector2(0f, 0f)),
			"point bounds remain usable");
		assertTrue(!EditorViewport.isUsableAttachmentBounds(new Vector2(Float.NaN, 0f), new Vector2(1f, 1f)),
			"non-finite offsets are rejected");
		assertTrue(!EditorViewport.isUsableAttachmentBounds(new Vector2(), new Vector2(-1f, 5f)),
			"empty Spine sentinel bounds are rejected");
		assertTrue(!EditorViewport.isUsableAttachmentBounds(null, new Vector2(1f, 1f)),
			"missing offsets are rejected");
		assertTrue(!EditorViewport.isUsableAttachmentBounds(new Vector2(Float.MAX_VALUE, 0f),
			new Vector2(Float.MAX_VALUE, 1f)), "overflowing attachment extents are rejected");

		assertTrue(EditorViewport.isHandledButton(Input.Buttons.LEFT), "left mouse button is handled");
		assertTrue(EditorViewport.isHandledButton(Input.Buttons.MIDDLE), "middle mouse button is handled");
		assertTrue(EditorViewport.isHandledButton(Input.Buttons.RIGHT), "right mouse button is handled");
		assertTrue(!EditorViewport.isHandledButton(Input.Buttons.BACK), "unsupported mouse buttons pass through");
		assertTrue(EditorViewport.shouldRetainAttachmentSelection(false, false, true),
			"blank clicks retain the current attachment selection");
		assertTrue(!EditorViewport.shouldRetainAttachmentSelection(true, false, true),
			"a directly hit attachment replaces the current selection");
		assertTrue(!EditorViewport.shouldRetainAttachmentSelection(false, true, true),
			"a visible bone hit selects the bone instead of retaining the attachment");
		assertTrue(!EditorViewport.shouldRetainAttachmentSelection(false, false, false),
			"blank clicks without an attachment keep no stale selection");
		assertTrue(EditorViewport.shouldRetainBoneSelection(false, false, true,
			SkeletonDocument.SelectionKind.BONE),
			"blank clicks retain an explicit visible bone selected from the hierarchy");
		assertTrue(!EditorViewport.shouldRetainBoneSelection(true, false, true,
			SkeletonDocument.SelectionKind.BONE),
			"a direct attachment hit replaces the retained hierarchy bone");
		assertTrue(!EditorViewport.shouldRetainBoneSelection(false, true, true,
			SkeletonDocument.SelectionKind.BONE),
			"a direct bone hit selects that bone instead of retaining another one");
		assertTrue(!EditorViewport.shouldRetainBoneSelection(false, false, true,
			SkeletonDocument.SelectionKind.ATTACHMENT),
			"blank drags cannot edit a stale bone behind an attachment selection");
		assertTrue(!EditorViewport.shouldRetainBoneSelection(false, false, true,
			SkeletonDocument.SelectionKind.CONSTRAINT),
			"blank drags cannot edit a stale bone behind a constraint selection");
		assertTrue(!EditorViewport.shouldRetainBoneSelection(false, false, false,
			SkeletonDocument.SelectionKind.BONE),
			"blank drags require a current visible bone target");
		assertTrue(EditorViewport.isBlankAttachmentDoubleClick(true, 1_300_000_000L, 1_000_000_000L,
			100f, 100f, 106f, 106f), "nearby blank clicks within the interval cancel attachment selection");
		assertTrue(!EditorViewport.isBlankAttachmentDoubleClick(false, 1_300_000_000L, 1_000_000_000L,
			100f, 100f, 100f, 100f), "blank double clicks require an explicit attachment selection");
		assertTrue(!EditorViewport.isBlankAttachmentDoubleClick(true, 1_400_000_000L, 1_000_000_000L,
			100f, 100f, 100f, 100f), "blank clicks outside the double-click interval stay selected");
		assertTrue(!EditorViewport.isBlankAttachmentDoubleClick(true, 1_300_000_000L, 1_000_000_000L,
			100f, 100f, 120f, 100f), "distant blank clicks do not form a double click");
		assertTrue(!EditorViewport.isBlankAttachmentDoubleClick(true, 1_300_000_000L, 0L,
			100f, 100f, 100f, 100f), "a first blank click never cancels attachment selection");
		assertTrue(EditorViewport.shouldStartBoneDrag(false, false, true, false, EditorTool.MOVE),
			"blank clicks can move an explicitly selected attachment");
		assertTrue(EditorViewport.shouldStartBoneDrag(false, false, true, false, EditorTool.ROTATE),
			"blank clicks can rotate an explicitly selected attachment");
		assertTrue(EditorViewport.shouldStartBoneDrag(false, false, true, false, EditorTool.SCALE),
			"blank clicks can scale an explicitly selected attachment");
		assertTrue(EditorViewport.shouldStartBoneDrag(false, false, false, true, EditorTool.MOVE),
			"blank clicks can move an explicit hierarchy bone selection");
		assertTrue(EditorViewport.shouldStartBoneDrag(false, false, false, true, EditorTool.ROTATE),
			"blank clicks can rotate an explicit hierarchy bone selection");
		assertTrue(EditorViewport.shouldStartBoneDrag(false, false, false, true, EditorTool.SCALE),
			"blank clicks can scale an explicit hierarchy bone selection");
		assertTrue(!EditorViewport.shouldStartBoneDrag(false, false, false, false, EditorTool.MOVE),
			"blank clicks without an explicit attachment selection do not start a transform");
		assertTrue(EditorViewport.shouldStartBoneDrag(true, false, false, false, EditorTool.ROTATE),
			"direct attachment hits can start a bone transform");
		assertTrue(EditorViewport.shouldStartBoneDrag(false, true, false, false, EditorTool.SCALE),
			"direct visible bone hits can start a bone transform");
		assertTrue(!EditorViewport.shouldStartBoneDrag(true, false, false, false, EditorTool.SHEAR),
			"shear requires an explicit X or Y handle instead of guessing a free two-axis edit");
		assertTrue(!EditorViewport.shouldStartBoneDrag(false, true, false, false, EditorTool.SHEAR),
			"clicking a bone body selects it without starting a free shear");
		assertTrue(!EditorViewport.shouldStartBoneDrag(false, false, true, false, EditorTool.SHEAR),
			"retained attachments do not create empty shear history transactions");
		assertTrue(!EditorViewport.shouldStartBoneDrag(false, false, false, true, EditorTool.SHEAR),
			"retained hierarchy bones still require a concrete shear axis handle");
		assertTrue(!EditorViewport.shouldStartBoneDrag(false, false, false, false, EditorTool.SHEAR),
			"blank viewport space never starts a shear");
		assertTrue(!EditorViewport.shouldStartBoneDrag(true, false, false, false, EditorTool.SELECT),
			"the selection tool never starts a bone transform");
		assertTrue(!EditorViewport.shouldStartBoneDrag(false, false, true, false, EditorTool.SELECT),
			"the selection tool does not modify a retained attachment");
		assertTrue(EditorViewport.transformAxisHit(30f, 2f, 0f, 0f,
			1f, 0f, 0f, 1f, 40f, 7f, 7f) == EditorViewport.TransformAxis.X,
			"the red horizontal transform axis has click priority over the attachment below it");
		assertTrue(EditorViewport.transformAxisHit(-2f, 28f, 0f, 0f,
			1f, 0f, 0f, 1f, 40f, 7f, 7f) == EditorViewport.TransformAxis.Y,
			"the green vertical transform axis can start a constrained drag");
		assertTrue(EditorViewport.transformAxisHit(2f, 2f, 0f, 0f,
			1f, 0f, 0f, 1f, 40f, 7f, 7f) == EditorViewport.TransformAxis.FREE,
			"the gizmo center preserves free transforms instead of choosing an ambiguous axis");
		assertTrue(EditorViewport.allowsBoneGizmo(EditorTool.SHEAR, SkeletonDocument.SelectionKind.BONE),
			"shear handles are available for an explicit bone selection");
		assertTrue(!EditorViewport.allowsBoneGizmo(EditorTool.SHEAR, SkeletonDocument.SelectionKind.SLOT),
			"slot selection cannot shear its owning bone");
		assertTrue(!EditorViewport.allowsBoneGizmo(EditorTool.SHEAR, SkeletonDocument.SelectionKind.ATTACHMENT),
			"region and mesh attachment selection cannot shear their owning bone");
		assertTrue(!EditorViewport.allowsBoneGizmo(EditorTool.SHEAR, SkeletonDocument.SelectionKind.CONSTRAINT),
			"constraint selection cannot expose shear handles");
		assertTrue(EditorViewport.allowsBoneGizmo(EditorTool.MOVE, SkeletonDocument.SelectionKind.BONE),
			"bone selection keeps viewport transform gizmos available");
		assertTrue(EditorViewport.allowsBoneGizmo(EditorTool.MOVE, SkeletonDocument.SelectionKind.ATTACHMENT),
			"the shear restriction does not change existing attachment move behavior");
		assertTrue(!EditorViewport.allowsBoneGizmo(EditorTool.MOVE, SkeletonDocument.SelectionKind.CONSTRAINT),
			"constraint selection cannot edit a stale related bone through the viewport gizmo");
		assertTrue(!EditorViewport.allowsBoneGizmo(null, SkeletonDocument.SelectionKind.BONE),
			"a missing tool cannot expose a bone transform gizmo");
		assertTrue(EditorViewport.transformAxisHit(2f, 30f, 0f, 0f,
			1f, 0f, 0f, 1f, 40f, 7f, 7f) == EditorViewport.TransformAxis.Y,
			"transform-axis picking uses the absolute vertical axis");
		assertNear(0f, EditorViewport.transformAxisWorldRotation(EditorTool.MOVE,
			EditorViewport.TransformAxis.X, 35f, 125f), 0.00001f,
			"move X uses the absolute horizontal axis");
		assertNear(90f, EditorViewport.transformAxisWorldRotation(EditorTool.MOVE,
			EditorViewport.TransformAxis.Y, 35f, 125f), 0.00001f,
			"move Y uses the absolute vertical axis");
		assertNear(35f, EditorViewport.transformAxisWorldRotation(EditorTool.SCALE,
			EditorViewport.TransformAxis.X, 35f, 125f), 0.00001f,
			"scale X follows the selected image's relative axis");
		assertNear(125f, EditorViewport.transformAxisWorldRotation(EditorTool.SCALE,
			EditorViewport.TransformAxis.Y, 35f, 125f), 0.00001f,
			"scale Y follows the selected image's relative axis");
		assertNear(35f, EditorViewport.transformAxisWorldRotation(EditorTool.SHEAR,
			EditorViewport.TransformAxis.X, 35f, 125f), 0.00001f,
			"shear X follows the selected bone's current world X axis");
		assertNear(125f, EditorViewport.transformAxisWorldRotation(EditorTool.SHEAR,
			EditorViewport.TransformAxis.Y, 35f, 125f), 0.00001f,
			"shear Y follows the selected bone's current world Y axis");
		Vector2 shearX = EditorViewport.shearValuesForDrag(EditorViewport.TransformAxis.X,
			12f, -4f, 170f, -170f, new Vector2());
		assertNear(32f, shearX.x, 0.00001f,
			"shear X drag stays continuous while the pointer crosses positive 180 degrees");
		assertNear(-4f, shearX.y, 0.00001f, "shear X drag preserves shear Y");
		Vector2 shearY = EditorViewport.shearValuesForDrag(EditorViewport.TransformAxis.Y,
			12f, -4f, -175f, 175f, null);
		assertNear(12f, shearY.x, 0.00001f, "shear Y drag preserves shear X");
		assertNear(-14f, shearY.y, 0.00001f,
			"shear Y drag stays continuous while the pointer crosses negative 180 degrees");
		assertTrue(EditorViewport.shearValuesForDrag(EditorViewport.TransformAxis.FREE,
			12f, -4f, 0f, 10f, new Vector2()) == null,
			"free shear does not implicitly modify both channels");
		assertTrue(EditorViewport.shearValuesForDrag(EditorViewport.TransformAxis.X,
			12f, -4f, 0f, Float.NaN, new Vector2()) == null,
			"non-finite shear drags are ignored");
		assertTrue(EditorViewport.rotationGizmoHit(28f, 0f, 0f, 0f, 28f, 5f),
			"the rotation ring can start a rotation drag");
		assertTrue(EditorViewport.rotationGizmoHit(0f, -25f, 0f, 0f, 28f, 5f),
			"the rotation ring has a usable hit tolerance");
		assertTrue(!EditorViewport.rotationGizmoHit(0f, 0f, 0f, 0f, 28f, 5f),
			"the rotation gizmo center remains available for selecting the image");
		assertTrue(!EditorViewport.rotationGizmoHit(40f, 0f, 0f, 0f, 28f, 5f),
			"points outside the rotation ring do not start a rotation drag");
		Vector2 constrained = EditorViewport.constrainDeltaToAxis(8f, 2f, 1f, 1f, new Vector2());
		assertNear(5f, constrained.x, 0.00001f, "axis-constrained movement projects the horizontal delta");
		assertNear(5f, constrained.y, 0.00001f, "axis-constrained movement projects the vertical delta");
		assertNear(1.5f, EditorViewport.axisScaleFactor(40f, 0f, 60f, 20f,
			1f, 0f, 1f), 0.00001f, "axis scaling ignores pointer movement perpendicular to its handle");
		assertTrue(Float.isNaN(EditorViewport.axisScaleFactor(0.5f, 0f, 20f, 0f,
			1f, 0f, 1f)), "axis scaling rejects a start position too close to the origin");
		assertTrue(EditorViewport.previewMinFilter(TextureFilter.Nearest, true) == TextureFilter.Linear,
			"smooth preview upgrades ordinary textures to linear filtering");
		assertTrue(EditorViewport.previewMinFilter(TextureFilter.MipMapNearestNearest, true)
			== TextureFilter.MipMapLinearLinear,
			"smooth preview uses trilinear filtering when mipmaps exist");
		assertTrue(EditorViewport.previewMinFilter(TextureFilter.Linear, false) == TextureFilter.Nearest,
			"pixel preview restores nearest filtering for ordinary textures");
		assertTrue(EditorViewport.previewMinFilter(TextureFilter.MipMapLinearLinear, false)
			== TextureFilter.MipMapNearestNearest,
			"pixel preview preserves existing mip levels without smoothing them");
		SkeletonRenderer renderer = new SkeletonRenderer();
		EditorViewport.configureSkeletonRenderer(renderer, true);
		assertTrue(renderer.getPremultipliedAlphaColors() && renderer.getPremultipliedAlphaBlendModes(),
			"PMA atlases configure viewport colors and blend modes together");
		EditorViewport.configureSkeletonRenderer(renderer, false);
		assertTrue(!renderer.getPremultipliedAlphaColors() && !renderer.getPremultipliedAlphaBlendModes(),
			"straight atlases restore straight viewport rendering");
		assertNear(0f, EditorViewport.visibleBoneDistance(25f, 0f, 50f, 0f,
			0f, 0f, true, 0f, 90f, 1f), 0.00001f,
			"the visible parent-to-bone line participates in bone picking");
		assertNear(0f, EditorViewport.visibleBoneDistance(0f, 8f, 0f, 0f,
			0f, 0f, false, 90f, 180f, 1f), 0.00001f,
			"the visible local axis participates in bone picking");
		assertTrue(EditorViewport.visibleBoneDistance(14f, 0f, 0f, 0f,
			0f, 0f, false, 90f, 180f, 1f) > 7f,
			"the removed selection cross does not leave an invisible bone hitbox");
		assertTrue(EditorViewport.visibleBoneDistance(60f, 0f, 0f, 0f,
			0f, 0f, false, 90f, 180f, 1f) > 7f,
			"an undrawn bone-length segment is treated as blank viewport space");

		SkeletonData visibilityData = new SkeletonData();
		BoneData visibleBoneData = new BoneData(0, "visible", null);
		visibleBoneData.setVisible(true);
		BoneData hiddenBoneData = new BoneData(1, "hidden", visibleBoneData);
		hiddenBoneData.setVisible(false);
		BoneData skinBoneData = new BoneData(2, "skin-required", visibleBoneData);
		skinBoneData.setSkinRequired(true);
		skinBoneData.setVisible(true);
		visibilityData.getBones().add(visibleBoneData);
		visibilityData.getBones().add(hiddenBoneData);
		visibilityData.getBones().add(skinBoneData);
		SlotData hiddenBoneSlotData = new SlotData(0, "hidden-bone-slot", hiddenBoneData);
		hiddenBoneSlotData.setVisible(true);
		visibilityData.getSlots().add(hiddenBoneSlotData);
		Skeleton visibilitySkeleton = new Skeleton(visibilityData);
		Skeleton foreignSkeleton = new Skeleton(visibilityData);
		var visibleBone = visibilitySkeleton.getBones().get(0);
		assertTrue(EditorViewport.isViewportBoneTarget(visibilitySkeleton, visibleBone),
			"a visible bone owned by the current skeleton can expose a transform gizmo");
		assertTrue(!EditorViewport.isViewportBoneTarget(visibilitySkeleton, foreignSkeleton.getBones().get(0)),
			"a stale bone from another skeleton cannot expose a transform gizmo");
		assertTrue(EditorViewport.isBoneDragTargetValid(visibilitySkeleton, visibleBone, visibleBone,
			EditorTool.SHEAR, SkeletonDocument.SelectionKind.BONE),
			"an explicit current bone selection can remain the shear drag target");
		assertTrue(!EditorViewport.isBoneDragTargetValid(visibilitySkeleton, visibleBone, visibleBone,
			EditorTool.SHEAR, SkeletonDocument.SelectionKind.SLOT),
			"switching to a slot selection stops an active shear drag");
		assertTrue(!EditorViewport.isBoneDragTargetValid(visibilitySkeleton, visibleBone, visibleBone,
			EditorTool.SHEAR, SkeletonDocument.SelectionKind.ATTACHMENT),
			"switching to a region or mesh selection stops an active shear drag");
		assertTrue(!EditorViewport.isBoneDragTargetValid(visibilitySkeleton, visibleBone,
			visibilitySkeleton.getBones().get(1), EditorTool.SHEAR, SkeletonDocument.SelectionKind.BONE),
			"switching to another bone cannot redirect an active shear drag");
		assertTrue(!EditorViewport.isBoneDragTargetValid(visibilitySkeleton,
			foreignSkeleton.getBones().get(0), foreignSkeleton.getBones().get(0),
			EditorTool.SHEAR, SkeletonDocument.SelectionKind.BONE),
			"a drag target from another skeleton is rejected");
		assertTrue(EditorViewport.isBoneDragTargetValid(visibilitySkeleton, visibleBone, visibleBone,
			EditorTool.MOVE, SkeletonDocument.SelectionKind.ATTACHMENT),
			"target locking preserves existing attachment move behavior");
		assertTrue(!EditorViewport.isBoneDragTargetValid(visibilitySkeleton, visibleBone, visibleBone,
			EditorTool.MOVE, SkeletonDocument.SelectionKind.CONSTRAINT),
			"switching to a constraint stops every active bone drag");
		assertTrue(EditorViewport.isViewportBoneVisible(visibilitySkeleton.getBones().get(0)),
			"visible active bones participate in viewport controls");
		assertTrue(!EditorViewport.isViewportBoneVisible(visibilitySkeleton.getBones().get(1)),
			"bone visibility metadata hides viewport controls");
		assertTrue(!EditorViewport.isViewportBoneVisible(visibilitySkeleton.getBones().get(2)),
			"inactive skin-required bones stay out of viewport controls");
		for (EditorTool tool : new EditorTool[] {EditorTool.MOVE, EditorTool.ROTATE, EditorTool.SCALE, EditorTool.SHEAR})
			assertTrue(!EditorViewport.isBoneDragTargetValid(visibilitySkeleton, visibilitySkeleton.getBones().get(2),
				visibilitySkeleton.getBones().get(2), tool, SkeletonDocument.SelectionKind.BONE),
				"inactive skin bones cannot begin a " + tool + " drag from a tree selection");
		assertTrue(!EditorViewport.isViewportBoneVisible(null),
			"missing bones stay out of viewport controls");
		assertTrue(EditorViewport.isEditableAttachmentSlot(visibilitySkeleton.getSlots().first()),
			"bone control visibility does not hide attachments on an active bone");

		assertNear(1f, EditorViewport.scrollZoomFactor(0f), 0f, "zero vertical scroll does not zoom");
		assertNear(1f, EditorViewport.scrollZoomFactor(Float.NaN), 0f, "invalid vertical scroll does not zoom");
		assertNear(1.12f * 1.12f, EditorViewport.scrollZoomFactor(2f), 0.00001f,
			"multiple wheel steps retain their magnitude");
		assertNear(1f / 1.12f, EditorViewport.scrollZoomFactor(-1f), 0.00001f,
			"negative wheel steps zoom in");
		assertTrue(EditorViewport.scrollChangesZoom(false, 1.12f),
			"an unlocked viewport applies a finite wheel zoom factor");
		assertTrue(!EditorViewport.scrollChangesZoom(true, 1.12f),
			"a locked viewport consumes wheel input without changing zoom");
		assertTrue(!EditorViewport.scrollChangesZoom(false, 1f),
			"an identity wheel factor does not change zoom");

		float anchored = EditorViewport.anchoredCameraCoordinate(10f, 80f, 1f, 0.5f);
		assertNear(90f, anchored + 80f * 0.5f, 0.00001f,
			"zoom preserves the world coordinate below the cursor");
		float flippedAnchor = EditorViewport.anchoredCameraCoordinate(10f, 80f, 1f, 0.5f, true);
		assertNear(-70f, flippedAnchor - 80f * 0.5f, 0.00001f,
			"flipped zoom preserves the mirrored world coordinate below the cursor");
		assertNear(60f, EditorViewport.screenToWorldCoordinate(75f, 100f, 2f, 10f, false), 0.00001f,
			"ordinary screen coordinates increase toward world X");
		assertNear(-40f, EditorViewport.screenToWorldCoordinate(75f, 100f, 2f, 10f, true), 0.00001f,
			"flipped screen coordinates decrease toward world X");
		assertNear(-10f, EditorViewport.panWorldDelta(5f, 2f, false), 0.00001f,
			"ordinary viewport panning follows the grabbed content");
		assertNear(10f, EditorViewport.panWorldDelta(5f, 2f, true), 0.00001f,
			"flipped viewport panning preserves the grabbed content");
		assertNear(0.5f, EditorViewport.snapWorldCoordinateToScreenPixel(0.3f, 0f, 100f, 0.5f, false),
			0.00001f, "pixel snapping rounds an attachment origin in screen space");
		assertNear(0.5f, EditorViewport.snapWorldCoordinateToScreenPixel(0.3f, 0f, 100f, 0.5f, true),
			0.00001f, "pixel snapping remains symmetric in a flipped viewport");
		assertNear(1f / 3f, EditorViewport.snapWorldCoordinateToScreenPixel(
			0.3f, 0f, 100f, 0.5f, false, 3f, 1.5f), 0.00001f,
			"pixel snapping targets physical backbuffer pixels on a high-DPI viewport");
		Vector2 snappedFree = EditorViewport.snapRegionMoveDelta(0.2f, 0.3f, 0.6f, 1.3f,
			0f, 0f, 100f, 100f, 1f, false, EditorViewport.TransformAxis.FREE, null);
		assertNear(0.8f, snappedFree.x, 0.00001f,
			"free region translation snaps its final X origin to a screen pixel");
		assertNear(1.7f, snappedFree.y, 0.00001f,
			"free region translation snaps its final Y origin to a screen pixel");
		Vector2 snappedX = EditorViewport.snapRegionMoveDelta(0.2f, 0.3f, 0.6f, 1.3f,
			0f, 0f, 100f, 100f, 1f, true, EditorViewport.TransformAxis.X, null);
		assertNear(0.8f, snappedX.x, 0.00001f,
			"axis-constrained region translation snaps the active screen axis");
		assertNear(1.3f, snappedX.y, 0.00001f,
			"axis-constrained pixel snapping does not introduce perpendicular movement");
		assertNear(14f, EditorViewport.displayWorldX(6f, 10f, true), 0.00001f,
			"unmirrored labels are positioned over horizontally flipped world content");
		Matrix4 projection = new Matrix4();
		projection.val[Matrix4.M00] = 2f;
		projection.val[Matrix4.M01] = 3f;
		projection.val[Matrix4.M02] = 4f;
		projection.val[Matrix4.M03] = 5f;
		Matrix4 projectionBackup = new Matrix4();
		EditorViewport.applyViewportFlip(projection, projectionBackup, true);
		assertNear(-2f, projection.val[Matrix4.M00], 0f,
			"viewport flip mirrors the projection's clip-space X row");
		assertNear(-5f, projection.val[Matrix4.M03], 0f,
			"viewport flip mirrors translated world content around the viewport center");
		assertNear(2f, projectionBackup.val[Matrix4.M00], 0f,
			"viewport flip retains an unmodified projection for readable text and cleanup");
		assertTrue(EditorViewport.isLocalPointInside(640f, 480f, 320f, 240f),
			"viewport center receives wheel input");
		assertTrue(!EditorViewport.isLocalPointInside(640f, 480f, 640f, 240f),
			"viewport right edge is outside the input bounds");
		assertTrue(!EditorViewport.isLocalPointInside(640f, 480f, 320f, Float.NaN),
			"invalid pointer coordinates are ignored");

		assertTrue(EditorViewport.isUsableViewBounds(-10f, -20f, 30f, 40f),
			"finite ordered view bounds are usable");
		assertTrue(!EditorViewport.isUsableViewBounds(Float.NaN, 0f, 1f, 1f),
			"non-finite view bounds are rejected");
		assertTrue(!EditorViewport.isUsableViewBounds(2f, 0f, 1f, 1f),
			"reversed view bounds are rejected");

		float[] middleGhosts = EditorViewport.ghostSampleTimes(0.5f, 1f, 10f, false);
		assertNear(0.4f, middleGhosts[0], 0.00001f, "ghosting samples the previous project frame");
		assertNear(0.6f, middleGhosts[1], 0.00001f, "ghosting samples the next project frame");
		float[] firstGhosts = EditorViewport.ghostSampleTimes(0f, 1f, 10f, false);
		assertTrue(Float.isNaN(firstGhosts[0]), "non-looping ghosting omits a duplicate before the first frame");
		assertNear(0.1f, firstGhosts[1], 0.00001f, "non-looping ghosting retains the next first-frame sample");
		float[] lastGhosts = EditorViewport.ghostSampleTimes(1f, 1f, 10f, false);
		assertNear(0.9f, lastGhosts[0], 0.00001f, "non-looping ghosting retains the previous last-frame sample");
		assertTrue(Float.isNaN(lastGhosts[1]), "non-looping ghosting omits a duplicate after the last frame");
		float[] loopingGhosts = EditorViewport.ghostSampleTimes(0f, 1f, 10f, true);
		assertNear(0.9f, loopingGhosts[0], 0.00001f, "looping ghosting wraps its previous sample");
		assertNear(0.1f, loopingGhosts[1], 0.00001f, "looping ghosting wraps its next sample");
		float[] duplicateGhosts = EditorViewport.ghostSampleTimes(0f, 0.05f, 10f, true);
		assertTrue(Float.isNaN(duplicateGhosts[0]) && Float.isNaN(duplicateGhosts[1]),
			"looping ghosting suppresses samples that resolve to the current pose");
		float[] invalidGhosts = EditorViewport.ghostSampleTimes(0f, 1f, Float.NaN, true);
		assertTrue(Float.isNaN(invalidGhosts[0]) && Float.isNaN(invalidGhosts[1]),
			"ghosting rejects an invalid project FPS");

		SkeletonData ghostData = new SkeletonData();
		ghostData.getBones().add(new BoneData(0, "ghost-root", null));
		Skeleton sourceSkeleton = new Skeleton(ghostData);
		sourceSkeleton.setPosition(12f, -4f);
		sourceSkeleton.setScale(1.5f, 0.75f);
		sourceSkeleton.setupPose();
		sourceSkeleton.updateWorldTransform(Physics.pose);
		Animation.TranslateTimeline ghostTranslate = new Animation.TranslateTimeline(2, 0, 0);
		ghostTranslate.setFrame(0, 0f, 0f, 0f);
		ghostTranslate.setFrame(1, 1f, 10f, 0f);
		Animation ghostAnimation = new Animation("ghost-move");
		Array<Animation.Timeline> ghostTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		ghostTimelines.add(ghostTranslate);
		ghostAnimation.setTimelines(ghostTimelines, new IntArray(new int[] {0}));
		ghostAnimation.setDuration(1f);
		Skeleton ghostSkeleton = new Skeleton(ghostData);
		Color ghostColor = new Color(0.25f, 0.5f, 0.75f, 0.2f);
		EditorViewport.prepareGhostSkeleton(ghostSkeleton, sourceSkeleton, ghostAnimation, 0.5f, ghostColor);
		assertNear(5f, ghostSkeleton.getRootBone().getPose().getX(), 0.00001f,
			"ghosting applies the sampled animation pose to its independent skeleton");
		assertNear(0f, sourceSkeleton.getRootBone().getPose().getX(), 0.00001f,
			"ghosting does not modify the current editor skeleton");
		assertNear(12f, ghostSkeleton.getX(), 0f, "ghosting follows the source skeleton position");
		assertNear(1.5f, ghostSkeleton.getScaleX(), 0f, "ghosting follows the source skeleton scale");
		assertTrue(ghostSkeleton.getColor().equals(ghostColor), "ghosting applies its configured tint");

		SkeletonData constrainedData = new SkeletonData();
		BoneData constrainedRoot = new BoneData(0, "constraint-root", null);
		constrainedRoot.getSetupPose().setScale(1f, 1f);
		BoneData constrainedBone = new BoneData(1, "constraint-bone", constrainedRoot);
		constrainedBone.setLength(10f);
		constrainedBone.getSetupPose().setScale(1f, 1f);
		constrainedBone.getSetupPose().setInherit(BoneData.Inherit.normal);
		BoneData ikTarget = new BoneData(2, "constraint-target", constrainedRoot);
		ikTarget.getSetupPose().setPosition(0f, 20f);
		ikTarget.getSetupPose().setScale(1f, 1f);
		ikTarget.getSetupPose().setInherit(BoneData.Inherit.normal);
		constrainedData.getBones().add(constrainedRoot);
		constrainedData.getBones().add(constrainedBone);
		constrainedData.getBones().add(ikTarget);
		IkConstraintData ik = new IkConstraintData("viewport-ik");
		ik.getBones().add(constrainedBone);
		ik.setTarget(ikTarget);
		ik.getSetupPose().setMix(1f);
		constrainedData.getConstraints().add(ik);
		Skeleton constrainedSkeleton = new Skeleton(constrainedData);
		constrainedSkeleton.updateWorldTransform(Physics.pose);
		Vector2 constrainedTip = EditorViewport.boneTipWorld(constrainedSkeleton.findBone("constraint-bone"), null);
		assertNear(0f, constrainedTip.x, 0.0001f,
			"bone endpoints use the constraint-applied world transform");
		assertNear(10f, constrainedTip.y, 0.0001f,
			"bone endpoints include the constraint-applied direction");
		constrainedSkeleton.setScale(-2f, 0.5f);
		constrainedSkeleton.updateWorldTransform(Physics.pose);
		Vector2 scaledTip = EditorViewport.boneTipWorld(constrainedSkeleton.findBone("constraint-bone"), null);
		assertNear(0f, scaledTip.x, 0.0001f,
			"bone endpoints preserve reflected world transforms");
		assertNear(5f, scaledTip.y, 0.0001f,
			"bone endpoints include skeleton world scale");

		float[] vertices = {-10f, -10f, 4f, 3f, 30f, 20f};
		assertEquals(1, EditorViewport.closestVertexIndex(vertices, 5f, 4f, 3f),
			"nearest Mesh vertex inside the hit radius is selected");
		assertEquals(-1, EditorViewport.closestVertexIndex(vertices, 10f, 10f, 3f),
			"Mesh vertices outside the hit radius are ignored");
		assertEquals(-1, EditorViewport.closestVertexIndex(new float[] {1f}, 1f, 1f, 3f),
			"incomplete Mesh vertex arrays are ignored");

		IntArray vertexSelection = new IntArray();
		assertTrue(EditorViewport.updateMeshVertexSelection(vertexSelection, 1, false),
			"ordinary Mesh selection selects a vertex");
		assertTrue(EditorViewport.updateMeshVertexSelection(vertexSelection, 3, true),
			"Shift selection adds a Mesh vertex");
		assertIntArray(new int[] {1, 3}, vertexSelection.toArray(),
			"Shift selection preserves the existing Mesh selection");
		assertTrue(EditorViewport.updateMeshVertexSelection(vertexSelection, 1, false),
			"ordinary click on an already selected vertex keeps the group");
		assertIntArray(new int[] {1, 3}, vertexSelection.toArray(),
			"an existing group remains available for dragging");
		assertTrue(!EditorViewport.updateMeshVertexSelection(vertexSelection, 1, true),
			"Shift selection toggles a selected Mesh vertex off");
		assertIntArray(new int[] {3}, vertexSelection.toArray(),
			"only the toggled Mesh vertex is removed");
		assertTrue(EditorViewport.updateMeshVertexSelection(vertexSelection, 2, false),
			"ordinary selection switches to an unselected vertex");
		assertIntArray(new int[] {2}, vertexSelection.toArray(),
			"ordinary selection replaces the previous group");

		float[] groupedVertices = {0f, 0f, 10f, 20f, -5f, 8f};
		assertTrue(EditorViewport.applyMeshVertexDelta(groupedVertices, new int[] {0, 2},
			new float[] {0f, 0f, -5f, 8f}, 3f, -2f),
			"Mesh group drag applies a shared delta");
		assertFloatArray(new float[] {3f, -2f, 10f, 20f, -2f, 6f}, groupedVertices,
			"Mesh group drag preserves unselected vertices and relative spacing");
		float[] unchangedVertices = groupedVertices.clone();
		assertTrue(!EditorViewport.applyMeshVertexDelta(groupedVertices, new int[] {0, 8},
			new float[] {3f, -2f, 0f, 0f}, 1f, 1f),
			"Mesh group drag rejects invalid vertex indices");
		assertFloatArray(unchangedVertices, groupedVertices,
			"invalid Mesh group drags do not partially update geometry");
		assertTrue(!EditorViewport.applyMeshVertexDelta(groupedVertices, new int[0], new float[0], 1f, 1f),
			"empty Mesh group drags are ignored");

		assertTrue(EditorViewport.pointInTriangle(2f, 2f, 0f, 0f, 10f, 0f, 0f, 10f),
			"points inside an attachment triangle are selected");
		assertTrue(EditorViewport.pointInTriangle(5f, 0f, 0f, 0f, 10f, 0f, 0f, 10f),
			"points on an attachment edge are selected");
		assertTrue(!EditorViewport.pointInTriangle(8f, 8f, 0f, 0f, 10f, 0f, 0f, 10f),
			"points outside an attachment triangle are ignored");
		assertTrue(!EditorViewport.pointInTriangle(1f, 0f, 0f, 0f, 5f, 0f, 10f, 0f),
			"degenerate attachment triangles are ignored");

		float[] quad = {10f, 0f, 0f, 0f, 0f, 10f, 10f, 10f};
		assertTrue(EditorViewport.pointInQuad(quad, 5f, 5f),
			"points anywhere inside a Region attachment are selected");
		assertTrue(!EditorViewport.pointInQuad(quad, 11f, 5f),
			"points outside a Region attachment are ignored");

		SkeletonData selectionData = new SkeletonData();
		selectionData.getBones().add(new BoneData(0, "root", null));
		SlotData selectionSlot = new SlotData(0, "image-slot", selectionData.getBones().first());
		selectionSlot.setAttachmentName("image");
		selectionData.getSlots().add(selectionSlot);
		Skin selectionSkin = new Skin("default");
		RegionAttachment originalImage = new RegionAttachment("image", new Sequence(1, false));
		selectionSkin.setAttachment(0, "image", originalImage);
		selectionData.getSkins().add(selectionSkin);
		selectionData.setDefaultSkin(selectionSkin);
		Skeleton originalSkeleton = new Skeleton(selectionData);
		assertTrue(EditorViewport.resolveAttachmentSelection(originalSkeleton, selectionSlot,
			originalImage, "image") == originalSkeleton.getSlots().first(),
			"logical attachment selection resolves its current runtime slot");
		RegionAttachment replacementImage = new RegionAttachment("image", new Sequence(1, false));
		selectionSkin.setAttachment(0, "image", replacementImage);
		Skeleton rebuiltSkeleton = new Skeleton(selectionData);
		assertTrue(EditorViewport.resolveAttachmentSelection(rebuiltSkeleton, selectionSlot,
			originalImage, "image") == rebuiltSkeleton.getSlots().first(),
			"logical attachment selection survives runtime rebuild and attachment replacement");
		RegionAttachment otherImage = new RegionAttachment("other", new Sequence(1, false));
		selectionSkin.setAttachment(0, "other", otherImage);
		rebuiltSkeleton.getSlots().first().getAppliedPose().setAttachment(otherImage);
		assertTrue(EditorViewport.resolveAttachmentSelection(rebuiltSkeleton, selectionSlot,
			originalImage, "image") == null,
			"selection outline stays hidden while another attachment is displayed");

		SkeletonData treeSelectionData = new SkeletonData();
		BoneData treeBone = new BoneData(0, "tree-bone", null);
		BoneData emptyTreeBone = new BoneData(1, "empty-tree-bone", null);
		treeSelectionData.getBones().add(treeBone);
		treeSelectionData.getBones().add(emptyTreeBone);
		SlotData lowerSlot = new SlotData(0, "lower-slot", treeBone);
		SlotData upperSlot = new SlotData(1, "upper-slot", treeBone);
		lowerSlot.setAttachmentName("lower");
		upperSlot.setAttachmentName("upper");
		treeSelectionData.getSlots().add(lowerSlot);
		treeSelectionData.getSlots().add(upperSlot);
		Skin treeSelectionSkin = new Skin("default");
		treeSelectionSkin.setAttachment(0, "lower", new RegionAttachment("lower", new Sequence(1, false)));
		treeSelectionSkin.setAttachment(1, "upper", new RegionAttachment("upper", new Sequence(1, false)));
		treeSelectionData.getSkins().add(treeSelectionSkin);
		treeSelectionData.setDefaultSkin(treeSelectionSkin);
		Skeleton treeSelectionSkeleton = new Skeleton(treeSelectionData);
		Slot selectedTreeSlot = EditorViewport.topmostVisibleAttachmentSlot(treeSelectionSkeleton,
			treeSelectionSkeleton.getBones().get(0));
		assertTrue(selectedTreeSlot != null && selectedTreeSlot.getData() == upperSlot,
			"bone-tree selection chooses the topmost visible attachment");
		upperSlot.setVisible(false);
		selectedTreeSlot = EditorViewport.topmostVisibleAttachmentSlot(treeSelectionSkeleton,
			treeSelectionSkeleton.getBones().get(0));
		assertTrue(selectedTreeSlot != null && selectedTreeSlot.getData() == lowerSlot,
			"bone-tree selection skips hidden attachment slots");
		assertTrue(EditorViewport.topmostVisibleAttachmentSlot(treeSelectionSkeleton,
			treeSelectionSkeleton.getBones().get(1)) == null,
			"a bone without an attachment clears the viewport attachment selection");

		SkeletonAttachment hiddenAttachment = new SkeletonAttachment("hidden-end-marker");
		treeSelectionSkeleton.getSlots().get(1).getAppliedPose().setAttachment(hiddenAttachment);
		Array<Slot> originalDrawOrder = new Array<>(treeSelectionSkeleton.getDrawOrder().getAppliedPose());
		Array<Slot> suppressedSlots = new Array<>();
		Array<Attachment> suppressedAttachments = new Array<>();
		EditorViewport.suppressInvisibleAttachments(treeSelectionSkeleton, suppressedSlots,
			suppressedAttachments);
		assertEquals(originalDrawOrder.size, treeSelectionSkeleton.getDrawOrder().getAppliedPose().size,
			"hiding attachments preserves clipping end-slot positions");
		for (int index = 0; index < originalDrawOrder.size; index++)
			assertTrue(originalDrawOrder.get(index) == treeSelectionSkeleton.getDrawOrder().getAppliedPose().get(index),
				"hiding attachments preserves draw order identity");
		assertTrue(treeSelectionSkeleton.getSlots().get(1).getAppliedPose().getAttachment() == null,
			"hidden slot attachments are suppressed without removing the slot");
		assertTrue(!EditorViewport.isEditableAttachmentSlot(treeSelectionSkeleton.getSlots().get(1)),
			"hidden slots cannot enter Mesh editing paths");
		EditorViewport.restoreSuppressedAttachments(suppressedSlots, suppressedAttachments);
		assertTrue(treeSelectionSkeleton.getSlots().get(1).getAppliedPose().getAttachment() == hiddenAttachment,
			"hidden attachments are restored exactly after rendering");
		assertTrue(EditorViewport.isEditableAttachmentSlot(treeSelectionSkeleton.getSlots().get(0)),
			"visible active slots remain editable");

		float[] mesh = {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f};
		short[] meshTriangles = {0, 1, 2, 0, 2, 3};
		assertTrue(EditorViewport.pointInTriangles(mesh, meshTriangles, 9f, 9f),
			"Mesh topology selects points in any rendered triangle");
		assertTrue(!EditorViewport.pointInTriangles(mesh, meshTriangles, 12f, 5f),
			"Mesh topology ignores points outside all rendered triangles");
		assertTrue(!EditorViewport.pointInTriangles(mesh, new short[] {0, 1, 8}, 5f, 1f),
			"invalid Mesh triangle indices are ignored");
		float[] barycentric = EditorViewport.barycentricCoordinates(2f, 3f,
			0f, 0f, 10f, 0f, 0f, 10f);
		assertFloatArray(new float[] {0.5f, 0.2f, 0.3f}, barycentric,
			"triangle hit testing returns normalized barycentric coordinates for topology insertion");
		assertTrue(EditorViewport.barycentricCoordinates(9f, 9f,
			0f, 0f, 10f, 0f, 0f, 10f) == null,
			"topology insertion rejects points outside the triangle");
		assertTrue(EditorViewport.barycentricCoordinates(1f, 0f,
			0f, 0f, 2f, 0f, 4f, 0f) == null,
			"topology insertion rejects degenerate triangles");
		assertIntArray(new int[] {0, 1, 2}, EditorViewport.orientedTriangle(0, 2, 1,
			mesh, new short[] {0, 1, 2}),
			"new faces are reordered to match the existing Mesh winding");
		assertTrue(EditorViewport.orientedTriangle(0, 1, 2,
			new float[] {0f, 0f, 5f, 0f, 10f, 0f}, new short[] {0, 1, 2}) == null,
			"collinear Mesh selections cannot create a face");
		assertTrue(EditorViewport.pointInPolygon(mesh, mesh.length, 5f, 5f),
			"Bounding Box and Clipping polygons select interior points");
		assertTrue(EditorViewport.pointInPolygon(mesh, mesh.length, 10f, 5f),
			"standard polygon attachment edges are selectable");
		assertTrue(!EditorViewport.pointInPolygon(mesh, mesh.length, 12f, 5f),
			"standard polygon attachments reject exterior points");
		float[] pathVertices = {-100f, 0f, -75f, 0f, -25f, 0f, 25f, 0f, 75f, 0f, 100f, 0f};
		assertEquals(1, EditorViewport.pathCurveCount(pathVertices.length, false),
			"an open six-vertex Path contains one rendered Bezier curve");
		assertEquals(2, EditorViewport.pathCurveCount(pathVertices.length, true),
			"a closed six-vertex Path contains the wrapped curve");
		assertNear(0f, EditorViewport.distanceToPath(pathVertices, pathVertices.length, false, 0f, 0f),
			0.0001f, "Path hit testing follows the rendered Bezier curve");
		assertNear(10f, EditorViewport.distanceToPath(pathVertices, pathVertices.length, false, 0f, 10f),
			0.01f, "Path hit testing returns screen-independent world distance");
		assertTrue(EditorViewport.isViewportDeformAttachment(new MeshAttachment("mesh", new Sequence(1, false))),
			"viewport Deform supports Mesh attachments");
		assertTrue(EditorViewport.isViewportDeformAttachment(new BoundingBoxAttachment("box")),
			"viewport Deform supports Bounding Box attachments");
		assertTrue(EditorViewport.isViewportDeformAttachment(new PathAttachment("path")),
			"viewport Deform supports Path attachments");
		assertTrue(EditorViewport.isViewportDeformAttachment(new ClippingAttachment("clip")),
			"viewport Deform supports Clipping attachments");
		assertTrue(!EditorViewport.isViewportDeformAttachment(
			new RegionAttachment("region", new Sequence(1, false))),
			"viewport Deform rejects non-vertex Region attachments");

		assertEquals(8, EditorViewport.meshHullValueCount(8, 12),
			"Mesh outlines use only the configured hull vertices");
		assertEquals(8, EditorViewport.meshHullValueCount(11, 8),
			"Mesh hull outlines are clamped to available complete vertex pairs");
		assertEquals(0, EditorViewport.meshHullValueCount(-2, 8),
			"invalid Mesh hull lengths are ignored");
		assertEquals(2, EditorViewport.dashedSegmentCount(11f, 6f, 4f),
			"long outline edges contain multiple dashes");
		assertEquals(1, EditorViewport.dashedSegmentCount(2f, 6f, 4f),
			"short outline edges still contain a visible dash");
		assertEquals(0, EditorViewport.dashedSegmentCount(Float.NaN, 6f, 4f),
			"invalid outline lengths are ignored");
		assertEquals(0, EditorViewport.dashedSegmentCount(20f, 0f, 4f),
			"zero-length dashes are ignored");
		assertNear(0.24f, EditorViewport.weightBrushFalloff(0f, 48f, 0.24f), 0.00001f,
			"weight brush uses full configured strength at its center");
		assertNear(0.06f, EditorViewport.weightBrushFalloff(24f, 48f, 0.24f), 0.00001f,
			"weight brush has a smooth quadratic falloff");
		assertNear(0f, EditorViewport.weightBrushFalloff(48f, 48f, 0.24f), 0f,
			"weight brush reaches zero at its boundary");
		Color coldWeight = EditorViewport.meshWeightColor(0f, new Color());
		Color hotWeight = EditorViewport.meshWeightColor(1f, new Color());
		assertTrue(coldWeight.b > coldWeight.r && hotWeight.r > hotWeight.b,
			"weight visualization maps low and high influence to distinct cool and warm colors");
		Vector2 dragged = EditorViewport.draggedVertexPosition(12f, -4f, 5f, 6f, 8f, 2f, new Vector2());
		assertNear(15f, dragged.x, 0.00001f, "Mesh drag preserves the pointer-to-vertex X offset");
		assertNear(-8f, dragged.y, 0.00001f, "Mesh drag preserves the pointer-to-vertex Y offset");

		Vector2 deformDelta = EditorViewport.inverseTransformDelta(2f, 1f, -1f, 3f, 6f, -10f,
			new Vector2());
		assertNear(4f, deformDelta.x, 0.00001f, "Deform drag converts world X into attachment space");
		assertNear(-2f, deformDelta.y, 0.00001f, "Deform drag converts world Y into attachment space");
		assertTrue(EditorViewport.inverseTransformDelta(1f, 2f, 2f, 4f, 3f, 5f, new Vector2()) == null,
			"Deform drag rejects singular transforms");

		MeshAttachment plainDeform = new MeshAttachment("plain", new Sequence(1, false));
		plainDeform.setVertices(new float[] {1f, 2f, 3f, 4f});
		assertFloatArray(new float[] {1.5f, 1f, 5f, 7f},
			EditorViewport.runtimeDeformValues(plainDeform, new float[] {0.5f, -1f, 2f, 3f}),
			"unweighted runtime Deform stores setup-relative vertices");
		MeshAttachment weightedDeform = new MeshAttachment("weighted", new Sequence(1, false));
		weightedDeform.setBones(new int[] {1, 0});
		weightedDeform.setVertices(new float[] {1f, 2f, 1f});
		assertFloatArray(new float[] {0.5f, -1f},
			EditorViewport.runtimeDeformValues(weightedDeform, new float[] {0.5f, -1f}),
			"weighted runtime Deform stores direct offsets");

		SkeletonData weightedData = new SkeletonData();
		weightedData.getBones().add(new BoneData(0, "root", null));
		weightedData.getBones().add(new BoneData(1, "child", weightedData.getBones().first()));
		weightedData.getSlots().add(new SlotData(0, "weighted-slot", weightedData.getBones().get(1)));
		Skeleton weightedSkeleton = new Skeleton(weightedData);
		weightedSkeleton.getBones().get(0).getAppliedPose().setA(2f);
		weightedSkeleton.getBones().get(0).getAppliedPose().setB(1f);
		weightedSkeleton.getBones().get(0).getAppliedPose().setC(0f);
		weightedSkeleton.getBones().get(0).getAppliedPose().setD(3f);
		weightedSkeleton.getBones().get(1).getAppliedPose().setA(4f);
		weightedSkeleton.getBones().get(1).getAppliedPose().setB(-1f);
		weightedSkeleton.getBones().get(1).getAppliedPose().setC(2f);
		weightedSkeleton.getBones().get(1).getAppliedPose().setD(1f);
		float[] standardTransform = new float[4];
		assertTrue(EditorViewport.deformVertexTransform(new BoundingBoxAttachment("box"),
			weightedSkeleton.getSlots().first(), 0, weightedSkeleton, standardTransform),
			"unweighted standard attachments use their slot bone for viewport Deform");
		assertFloatArray(new float[] {4f, -1f, 2f, 1f}, standardTransform,
			"standard attachment Deform uses the complete slot bone transform");
		MeshAttachment blended = new MeshAttachment("blended", new Sequence(1, false));
		blended.setBones(new int[] {2, 0, 1});
		blended.setVertices(new float[] {0f, 0f, 0.25f, 0f, 0f, 0.75f});
		float[] blendedTransform = new float[4];
		assertTrue(EditorViewport.weightedVertexTransform(blended, 0, weightedSkeleton, blendedTransform),
			"weighted Deform obtains a blended bone transform");
		assertFloatArray(new float[] {3.5f, -0.5f, 1.5f, 1.5f}, blendedTransform,
			"weighted Deform blends all influencing bone matrices");
		MeshAttachment weightedRanges = new MeshAttachment("ranges", new Sequence(1, false));
		weightedRanges.setBones(new int[] {2, 0, 1, 1, 1});
		weightedRanges.setVertices(new float[] {0f, 0f, 0.4f, 0f, 0f, 0.6f, 1f, 2f, 1f});
		assertTrue(EditorViewport.meshVertexHasBoneInfluence(weightedRanges, 0, 1),
			"weight painting detects an existing target influence");
		assertTrue(!EditorViewport.meshVertexHasBoneInfluence(weightedRanges, 1, 0),
			"weight painting distinguishes missing influences per logical vertex");
		assertIntArray(new int[] {0, 4}, EditorViewport.deformValueRange(weightedRanges, 0),
			"first weighted vertex owns two Deform offset pairs");
		assertIntArray(new int[] {4, 2}, EditorViewport.deformValueRange(weightedRanges, 1),
			"second weighted vertex starts after all prior influences");
		float[] startDeform = {1f, 2f, 3f, 4f, 5f, 6f};
		float[] movedDeform = startDeform.clone();
		assertTrue(EditorViewport.applyDeformDelta(startDeform, movedDeform, 0, 4, 2f, -3f),
			"weighted Deform drag updates a valid influence range");
		assertFloatArray(new float[] {3f, -1f, 5f, 1f, 5f, 6f}, movedDeform,
			"weighted Deform drag moves every influence for the selected vertex");
		float[] groupDeform = startDeform.clone();
		assertTrue(EditorViewport.applyDeformVertexDeltas(startDeform, groupDeform,
			new int[] {0, 4}, new int[] {4, 2},
			new float[] {2f, 0f, 0f, 2f, 1f, 0f, 0f, 0.5f}, 4f, -2f),
			"Deform group drag converts the world delta for every selected vertex");
		assertFloatArray(new float[] {3f, 1f, 5f, 3f, 9f, 2f}, groupDeform,
			"weighted Deform group drag updates each influence with its vertex transform");
		float[] unchangedDeform = groupDeform.clone();
		assertTrue(!EditorViewport.applyDeformVertexDeltas(startDeform, groupDeform,
			new int[] {0, 4}, new int[] {4, 2},
			new float[] {2f, 0f, 0f, 2f, 1f, 2f, 2f, 4f}, 4f, -2f),
			"Deform group drag rejects a singular vertex transform");
		assertFloatArray(unchangedDeform, groupDeform,
			"invalid Deform group drags do not partially update offsets");
		System.out.println("ALL EDITOR VIEWPORT TESTS PASSED");
	}

	private static void bonePickingMatchesDrawnGeometry() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "long", null);
		root.setVisible(true); root.setLength(80f);
		root.getSetupPose().setScale(2f, 1f); root.getSetupPose().setRotation(90f);
		data.getBones().add(root);
		Skeleton skeleton = new Skeleton(data);
		skeleton.updateWorldTransform(Physics.pose);
		var bone = skeleton.getBones().first();
		assertNear(0f, EditorViewport.visibleBoneDistance(bone, 0f, 140f, 1f), 0.001f,
			"long bone picking includes its actual scaled and rotated length line");
		assertTrue(EditorViewport.visibleBoneDistance(bone, 0f, 180f, 1f) > 7f,
			"bone length picking stops at the rendered endpoint");
		bone.getPose().setScale(-2f, 1f); skeleton.updateWorldTransform(Physics.pose);
		assertNear(0f, EditorViewport.visibleBoneDistance(bone, 0f, -140f, 1f), 0.001f,
			"mirrored bone length picking follows its applied matrix");
		root.setLength(0f);
		assertTrue(EditorViewport.visibleBoneDistance(bone, 14f, 0f, 1f) > 7f,
			"bone selection never introduces a phantom cross hitbox");
		root.setIcon("diamond"); root.setIconSize(10f);
		assertNear(0f, EditorViewport.visibleBoneDistance(bone, 15f, 15f, 1f), 0.0001f,
			"large drawn bone icons can be picked along their actual edges");
		assertTrue(EditorViewport.visibleBoneDistance(bone, 30f, 30f, 1f) > 7f,
			"icon picking does not include the entire bounding rectangle");
	}

	private static void coordinateAxisAutoKeyPreservesTheCompleteWorldMove() {
		for (BoneCoordinateSpace space : new BoneCoordinateSpace[] {BoneCoordinateSpace.WORLD, BoneCoordinateSpace.LOCAL})
			assertCoordinateAxisAutoKey(space);
	}

	private static void assertCoordinateAxisAutoKey(BoneCoordinateSpace space) {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		var bone = doc.skeleton.findBone("b");
		doc.skeleton.findBone("rotated").getPose().setRotation(37f);
		doc.data.findBone("rotated").getSetupPose().setRotation(37f);
		doc.skeleton.updateWorldTransform(Physics.pose);
		doc.setSelectedBone(bone);
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("world-move");
		doc.animationState = doc.newAnimationState();
		doc.setAnimation("world-move");
		float beforeX = bone.getAppliedPose().getWorldX(), beforeY = bone.getAppliedPose().getWorldY();
		float deltaX = space == BoneCoordinateSpace.WORLD ? 20f : bone.getAppliedPose().getA() * 3f;
		float deltaY = space == BoneCoordinateSpace.WORLD ? 0f : bone.getAppliedPose().getC() * 3f;
		if (space == BoneCoordinateSpace.WORLD) BoneSelectionTransform.capture(doc).applyWorldDelta(deltaX, deltaY);
		else BoneSelectionTransform.capture(doc).applyLocalDelta(3f, 0f);
		AnimationEditor.Prop property = EditorViewport.autoKeyProp(EditorTool.MOVE, EditorViewport.TransformAxis.X,
			space, 1);
		BoneKeyframeBatch.apply(doc, editor, animation, java.util.List.of(bone), property, 0.5f,
			ignored -> new boolean[] {true, false, false});
		doc.scrubTo(0.5f);
		assertTrue(Math.abs(bone.getAppliedPose().getWorldX() - beforeX - deltaX) < 0.0002f,
			"auto-key and scrub retain the full world X movement under a rotated parent: " + space);
		assertTrue(Math.abs(bone.getAppliedPose().getWorldY() - beforeY - deltaY) < 0.0002f,
			"auto-key does not introduce world Y drift by dropping a local translation axis: " + space);
		assertTrue(editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, bone.getData().getIndex()) != null
			&& editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_Y, bone.getData().getIndex()) != null,
			"world/local axis auto-key writes both channels when axes are separated: " + space);
		assertTrue(EditorViewport.autoKeyProp(EditorTool.MOVE, EditorViewport.TransformAxis.X, BoneCoordinateSpace.PARENT, 1)
			== AnimationEditor.Prop.TRANSLATE_X, "parent-axis auto-key keeps single-axis semantics");
	}

	private static void pixelSnappingPreservesRotatedAxes() {
		for (boolean flipped : new boolean[] {false, true}) {
			for (EditorViewport.TransformAxis axis : new EditorViewport.TransformAxis[] {
				EditorViewport.TransformAxis.X, EditorViewport.TransformAxis.Y}) {
				for (float angle : new float[] {0f, 30f, 60f, 90f, 135f, 220f, 270f}) {
					float ax = (float)Math.cos(Math.toRadians(angle));
					float ay = (float)Math.sin(Math.toRadians(angle));
					Vector2 move = EditorViewport.snapRegionMoveDelta(0.23f, 0.37f,
						ax * 0.63f, ay * 0.63f, 0f, 0f, 301f, 199f, 0.7f, flipped,
						axis, ax, ay, 13f, 27f, 1.5f, 2f, null);
					assertNear(0f, move.x * ay - move.y * ax, 0.00001f,
						"pixel snapping must not move perpendicular to the actual parent/local axis");
					boolean horizontal = Math.abs(ax * 1.5f) >= Math.abs(ay * 2f);
					double pixel = horizontal
						? 13d + (301d / 2d + (flipped ? -1d : 1d) * (0.23f + move.x) / 0.7f) * 1.5d
						: 27d + (199d / 2d + (0.37f + move.y) / 0.7f) * 2d;
					assertNear((float)Math.rint(pixel), (float)pixel, 0.00003f,
						"the dominant framebuffer coordinate still snaps on rotated and flipped axes");
				}
			}
		}
		Vector2 invalid = EditorViewport.snapRegionMoveDelta(0f, 0f, 2f, 3f, 0f, 0f,
			100f, 100f, 1f, false, EditorViewport.TransformAxis.X, 0f, 0f,
			0f, 0f, 1f, 1f, null);
		assertNear(2f, invalid.x, 0f, "an invalid drag axis preserves the requested X displacement");
		assertNear(3f, invalid.y, 0f, "an invalid drag axis preserves the requested Y displacement");
	}

	private static void preservesViewportBoneMultiSelection() {
		var doc = com.openspine.model.BoneSelectionTransformTest.document();
		var a = doc.skeleton.findBone("a");
		var b = doc.skeleton.findBone("b");
		var child = doc.skeleton.findBone("child");
		EditorViewport.updateBoneSelection(doc, b, false);
		assertTrue(doc.getSelectedBones().size() == 2 && doc.getSelectedBone() == a,
			"clicking an already selected bone retains the group and its primary");
		EditorViewport.updateBoneSelection(doc, child, true);
		assertTrue(doc.getSelectedBones().size() == 3 && doc.getSelectedBone() == a,
			"Ctrl/Shift adds another bone without clearing the selection");
		EditorViewport.updateBoneSelection(doc, a, true);
		assertTrue(doc.getSelectedBones().size() == 2 && doc.getSelectedBone() == b,
			"Ctrl/Shift removes a selected primary and chooses the next selected bone");
		EditorViewport.updateBoneSelection(doc, doc.skeleton.getRootBone(), false);
		assertTrue(doc.getSelectedBones().size() == 1,
			"plain clicks on unselected bones start a new selection");
		doc.setSelectedBone(child);
		EditorViewport.updateBoneSelection(doc, a, true);
		assertTrue(doc.getSelectedBone() == a
			&& java.util.List.copyOf(doc.getSelectedBones()).equals(java.util.List.of(a, child)),
			"Ctrl/Shift adding an ancestor promotes the primary and retains the child");
		EditorViewport.updateBoneSelection(doc, a, true);
		assertTrue(doc.getSelectedBone() == child && doc.getSelectedBones().size() == 1,
			"Ctrl/Shift removing the promoted ancestor returns the primary to the remaining child");
		EditorViewport.updateBoneSelection(doc, doc.skeleton.getRootBone(), true);
		assertTrue(doc.getSelectedBone() == doc.skeleton.getRootBone() && doc.getSelectedBones().size() == 2,
			"Ctrl/Shift promotes a selected ancestor across an unselected parent");
	}

	private static void assertFloatArray(float[] expected, float[] actual, String message) {
		if (expected.length != actual.length)
			throw new AssertionError(message + ": length expected " + expected.length + ", got " + actual.length);
		for (int index = 0; index < expected.length; index++)
			assertNear(expected[index], actual[index], 0.00001f, message + "[" + index + "]");
	}

	private static void assertIntArray(int[] expected, int[] actual, String message) {
		if (!java.util.Arrays.equals(expected, actual))
			throw new AssertionError(message + ": expected " + java.util.Arrays.toString(expected)
				+ ", got " + java.util.Arrays.toString(actual));
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertNear(float expected, float actual, float tolerance, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > tolerance)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private EditorViewportTest() {}
}
