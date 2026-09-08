package com.openspine.view;

import java.util.Arrays;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.VertexAttachment;
import com.esotericsoftware.spine.utils.SkeletonClipping;
import com.openspine.model.AnimationEditor;
import com.openspine.model.BoneSelectionTransform;
import com.openspine.model.BoneCoordinateSpace;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonEditor;
import com.openspine.ui.UiAppearance;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 视口：负责相机、网格、骨架渲染、骨骼拾取与 gizmo 编辑。
 * 作为一个 scene2d Actor 嵌入 UI 布局，接收鼠标输入。
 */
public class EditorViewport extends Actor {
	private final OrthographicCamera camera = new OrthographicCamera();
	private final PolygonSpriteBatch batch = new PolygonSpriteBatch();
	private BitmapFont overlayFont;
	private final ShapeRenderer shapes = new ShapeRenderer();
	private final SkeletonRenderer skeletonRenderer = new PageAwareSkeletonRenderer();
	private final Array<Slot> previewHiddenSlots = new Array<>();
	private final Array<Attachment> previewHiddenAttachments = new Array<>();
	private final SkeletonDocument doc;
	private final SkeletonEditor editor;
	private Supplier<List<SkeletonDocument>> visibleDocuments;

	private final Color GRID = new Color(0.16f, 0.17f, 0.20f, 1f);
	private final Color VIEWPORT_BACKGROUND = new Color(0.075f, 0.08f, 0.095f, 1f);
	private final Color VIEWPORT_AXIS = new Color(0.8f, 0.8f, 0.85f, 1f);
	private final Color PIXEL_GRID = new Color(0.65f, 0.70f, 0.78f, 0.24f);
	private final Color BONE_LINE = new Color(0.95f, 0.60f, 0.20f, 1f);
	private final Color BONE_SELECTED = new Color(0.30f, 0.85f, 1.00f, 1f);
	private final Color BONE_INACTIVE = new Color(0.45f, 0.48f, 0.54f, 1f);
	private final Color AXIS_X = new Color(1f, 0.35f, 0.35f, 1f);
	private final Color AXIS_Y = new Color(0.40f, 1f, 0.40f, 1f);
	private final Color MESH_EDGE = new Color(0.25f, 0.88f, 0.95f, 0.95f);
	private final Color MESH_WEIGHTED = new Color(1f, 0.72f, 0.20f, 0.95f);
	private final Color MESH_LINKED = new Color(0.72f, 0.55f, 0.92f, 0.8f);
	private final Color ATTACHMENT_SELECTED = new Color(1f, 1f, 1f, 1f);
	private final Color ATTACHMENT_HOVER = new Color(1f, 1f, 1f, 0.72f);
	private final Color WEIGHT_EDGE = new Color(0.82f, 0.86f, 0.9f, 0.55f);
	private final Color WEIGHT_BRUSH = new Color(1f, 1f, 1f, 0.85f);
	private final Color weightColorTemp = new Color();

	private boolean dragging;
	private boolean draggingMeshVertex;
	private boolean draggingView;
	private Bone dragBone;
	private BoneSelectionTransform boneSelectionDrag;
	private EditorTool dragTool;
	private TransformAxis dragTransformAxis = TransformAxis.FREE;
	private BoneCoordinateSpace coordinateSpace = BoneCoordinateSpace.PARENT;
	private BoneCoordinateSpace dragCoordinateSpace = BoneCoordinateSpace.PARENT;
	private boolean boneDragChanged;
	private TransformAxis hoveredTransformAxis = TransformAxis.FREE;
	private float dragAxisWorldX = 1f, dragAxisWorldY;
	private float lastScreenX, lastScreenY;
	private float startAngle, startX, startY, startRotation, startScaleX, startScaleY,
		startShearX, startShearY;
	private final Vector2 startDrag = new Vector2();
	private final Vector2 startBoneWorld = new Vector2();
	private final Vector2 tmp = new Vector2();
	private final Vector2 tmp2 = new Vector2();
	private final Vector2 viewportOrigin = new Vector2();
	private final Matrix4 unflippedProjection = new Matrix4();
	private final Matrix4 gridProjection = new Matrix4();
	private final Vector2 boundsOffset = new Vector2();
	private final Vector2 boundsSize = new Vector2();
	private final FloatArray boundsTemp = new FloatArray();
	private final FloatArray meshWorldTemp = new FloatArray();
	private final SkeletonClipping boundsClipper = new SkeletonClipping();
	private MeshAttachment selectedMesh;
	private Slot selectedMeshSlot;
	private int selectedMeshVertex = -1;
	private final IntArray selectedMeshVertices = new IntArray();
	private VertexAttachment selectedDeformAttachment;
	private Slot selectedDeformSlot;
	private final IntArray selectedDeformVertices = new IntArray();
	private float meshStartCursorLocalX, meshStartCursorLocalY;
	private int[] meshDragVertices;
	private float[] meshStartVertexPositions;
	private SkeletonEditor.MeshGeometryState meshDragBefore;
	private boolean draggingDeformVertex;
	private Animation deformDragAnimation;
	private VertexAttachment deformDragAttachment;
	private int deformDragSlotIndex = -1;
	private AnimationEditor.TimelineSnapshot deformDragBefore;
	private float deformDragTime, deformStartCursorWorldX, deformStartCursorWorldY;
	private float[] deformVertexTransforms;
	private int[] deformValueStarts, deformValueCounts;
	private float[] deformStartOffsets, deformDragOffsets;
	private float[] deformStartPose, deformStartAppliedPose;
	private boolean paintingWeights;
	private MeshAttachment weightPaintMesh;
	private Slot weightPaintSlot;
	private SkeletonEditor.MeshGeometryState weightPaintBefore;
	private SkeletonEditor.MeshWeightPaintContext weightPaintContext;
	private float weightPaintLastWorldX, weightPaintLastWorldY;
	private boolean weightPaintValidationError;
	private boolean pointerInside;
	private float pointerLocalX, pointerLocalY;
	private AttachmentHit hoveredAttachment;
	private AttachmentHit selectedAttachment;
	private SlotData selectedAttachmentSlotData;
	private String selectedAttachmentPlaceholder;
	private Attachment selectedAttachmentIdentity;
	private long lastBlankAttachmentClickNanos;
	private float lastBlankAttachmentClickX, lastBlankAttachmentClickY;
	private boolean blankAttachmentClickCandidate;
	private float blankAttachmentPressX, blankAttachmentPressY;
	private EditHistory history;
	private Runnable onSelectionChanged;
	private Runnable onPoseEdited;
	private BiFunction<List<Bone>, AnimationEditor.Prop, Boolean> onBoneTransformFinished;
	private BiConsumer<Slot, Attachment> onAttachmentSelected;
	private BiConsumer<String, Boolean> onStatus;
	private boolean showGrid = true;
	private boolean showPixelGrid;
	private boolean showBones = true;
	private boolean selectionHidden;
	private boolean showInactiveSkinBones;
	private final InactiveSkinBones inactiveSkinBones = new InactiveSkinBones();
	private boolean showGhosting;
	private boolean showGhostBones;
	private boolean smoothTextures = true;
	private boolean viewportFlipped;
	private boolean zoomLocked;
	private boolean pixelSnapping;
	private boolean snapRegionDrag;
	private float snapRegionStartWorldX, snapRegionStartWorldY;
	private float screenPixelOriginX, screenPixelOriginY;
	private float screenPixelScaleX = 1f, screenPixelScaleY = 1f;
	private boolean disposed;
	private Skeleton ghostSourceSkeleton;
	private Skeleton previousGhostSkeleton;
	private Skeleton nextGhostSkeleton;
	private boolean previousGhostPrepared;
	private boolean nextGhostPrepared;

	private static final float MIN_ZOOM = 0.05f, MAX_ZOOM = 8f;
	private static final float MAX_SCROLL_STEPS = 20f;
	private static final float WEIGHT_BRUSH_RADIUS_PIXELS = 48f;
	private static final float WEIGHT_BRUSH_STRENGTH = 0.24f;
	private static final float BONE_HIT_RADIUS_PIXELS = 7f;
	private static final float BONE_X_AXIS_PIXELS = 9f;
	private static final float BONE_Y_AXIS_PIXELS = 6f;
	private static final float TRANSFORM_AXIS_LENGTH_PIXELS = 40f;
	private static final float TRANSFORM_AXIS_HIT_RADIUS_PIXELS = 7f;
	private static final float TRANSFORM_AXIS_CENTER_RADIUS_PIXELS = 7f;
	private static final float TRANSFORM_AXIS_HANDLE_SIZE_PIXELS = 8f;
	private static final float ROTATION_GIZMO_RADIUS_PIXELS = 28f;
	private static final float ROTATION_GIZMO_HIT_RADIUS_PIXELS = 7f;
	private static final long DOUBLE_CLICK_INTERVAL_NANOS = 350_000_000L;
	private static final float DOUBLE_CLICK_RADIUS_PIXELS = 12f;
	private static final float CLICK_DRAG_TOLERANCE_PIXELS = 4f;
	private static final int ROTATION_GIZMO_SEGMENTS = 48;
	private static final Color PREVIOUS_GHOST_COLOR = new Color(0.35f, 0.72f, 1f, 0.28f);
	private static final Color NEXT_GHOST_COLOR = new Color(1f, 0.48f, 0.35f, 0.24f);
	private final AnimationEditor animationEditor;

	public EditorViewport(SkeletonDocument doc) {
		this.doc = doc;
		editor = new SkeletonEditor(doc);
		animationEditor = new AnimationEditor(doc);
		visibleDocuments = () -> List.of(doc);
		setBounds(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		addListener(new InputListener() {
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				return EditorViewport.this.touchDown(x, y, button);
			}

			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer) {
				EditorViewport.this.touchDragged(x, y);
			}

			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
				EditorViewport.this.touchUp();
			}

			@Override
			public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
				return zoomFromLocalScroll(x, y, amountY);
			}

			@Override
			public boolean mouseMoved(InputEvent event, float x, float y) {
				EditorViewport.this.mouseMoved(x, y);
				return true;
			}

			@Override
			public boolean keyDown(InputEvent event, int keycode) {
				if (keycode == Input.Keys.ESCAPE && hasActiveGesture()) {
					cancelActiveGestures();
					return true;
				}
				if (EditorAppState.tool != EditorTool.MESH) return false;
				if (keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) {
					deleteSelectedMeshVertices();
					return true;
				}
				if (keycode == Input.Keys.ENTER) {
					createSelectedMeshTriangle();
					return true;
				}
				return false;
			}

			@Override
			public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
				if (pointer == -1) {
					hoveredAttachment = null;
					pointerInside = false;
				}
			}
		});
	}

	public void setHistory(EditHistory history) { this.history = history; }
	public void applyAppearance(UiAppearance.Palette palette) {
		if (palette == null) throw new IllegalArgumentException("Appearance palette cannot be null.");
		VIEWPORT_BACKGROUND.set(palette.viewportBackground());
		GRID.set(palette.viewportGrid());
		VIEWPORT_AXIS.set(palette.viewportAxis());
	}

	/** Supplies the shared UI font used for optional bone name overlays. */
	public void setOverlayFont(BitmapFont font) {
		overlayFont = font;
	}
	public void setOnSelectionChanged(Runnable listener) { onSelectionChanged = listener; }
	public void setOnPoseEdited(Runnable listener) { onPoseEdited = listener; }
	public void setOnBonesTransformFinished(BiFunction<List<Bone>, AnimationEditor.Prop, Boolean> listener) {
		onBoneTransformFinished = listener;
	}
	public void setOnAttachmentSelected(BiConsumer<Slot, Attachment> listener) { onAttachmentSelected = listener; }
	public void setOnStatus(BiConsumer<String, Boolean> listener) { onStatus = listener; }
	public void setVisibleDocuments(Supplier<List<SkeletonDocument>> documents) {
		visibleDocuments = documents == null ? () -> List.of(doc) : documents;
	}
	public void selectAttachment(SlotData slotData, Attachment attachment) {
		clearAttachmentSelection();
		if (doc.skeleton == null || slotData == null || attachment == null) return;
		int slotIndex = slotData.getIndex();
		if (slotIndex < 0 || slotIndex >= doc.skeleton.getSlots().size) return;
		Slot slot = doc.skeleton.getSlots().get(slotIndex);
		if (slot.getData() != slotData) return;
		selectedAttachmentSlotData = slotData;
		selectedAttachmentIdentity = attachment;
		selectedAttachmentPlaceholder = attachmentPlaceholder(slotIndex, attachment);
		selectedAttachment = resolveAttachmentSelection();
	}
	public void selectAttachmentForBone(Bone bone) {
		Slot slot = topmostVisibleAttachmentSlot(doc.skeleton, bone);
		if (slot == null) {
			clearAttachmentSelection();
			return;
		}
		Attachment attachment = slot.getAppliedPose().getAttachment();
		setAttachmentSelection(new AttachmentHit(slot, attachment));
		if (onAttachmentSelected != null) onAttachmentSelected.accept(slot, attachment);
	}
	public boolean hasAttachmentSelection() { return selectedAttachmentSlotData != null; }
	public boolean isGridVisible() { return showGrid; }
	public boolean isPixelGridVisible() { return showPixelGrid; }
	public boolean isPixelGridActive() { return showPixelGrid && pixelGridPlan().visible(); }
	public boolean isBonesVisible() { return showBones; }
	public boolean isSelectionHidden() { return selectionHidden; }
	public boolean isShowInactiveSkinBones() { return showInactiveSkinBones; }
	public boolean isGhostingVisible() { return showGhosting; }
	public boolean isGhostBonesVisible() { return showGhostBones; }
	public boolean isTextureSmoothingEnabled() { return smoothTextures; }
	public boolean isViewportFlipped() { return viewportFlipped; }
	public boolean isZoomLocked() { return zoomLocked; }
	public boolean isPixelSnappingEnabled() { return pixelSnapping; }
	public boolean areConstraintsEnabled() { return doc.areConstraintsEnabled(); }
	public void setGridVisible(boolean visible) { showGrid = visible; }
	/** Project units are pixel boundaries; this display option never changes textures or authored coordinates. */
	public void setPixelGridVisible(boolean visible) { showPixelGrid = visible; }
	public void setBonesVisible(boolean visible) { showBones = visible; }
	/** Keeps selection and tool geometry; hides selection decoration and disables its invisible handles. */
	public void setSelectionHidden(boolean hidden) {
		if (selectionHidden == hidden) return;
		cancelActiveGestures();
		selectionHidden = hidden;
		hoveredAttachment = null;
		hoveredTransformAxis = TransformAxis.FREE;
	}
	public void setShowInactiveSkinBones(boolean visible) {
		showInactiveSkinBones = visible;
		if (!visible) inactiveSkinBones.clear();
	}
	public void setGhostingVisible(boolean visible) {
		showGhosting = visible;
		if (!visible) clearGhostSkeletons();
	}
	public void setGhostBonesVisible(boolean visible) { showGhostBones = visible; }
	public void setTextureSmoothing(boolean enabled) {
		smoothTextures = enabled;
		refreshTextureFiltering();
	}
	public void setViewportFlipped(boolean flipped) {
		if (viewportFlipped == flipped) return;
		cancelActiveGestures();
		viewportFlipped = flipped;
		hoveredAttachment = null;
		hoveredTransformAxis = TransformAxis.FREE;
	}
	public void setZoomLocked(boolean locked) { zoomLocked = locked; }
	public void setPixelSnappingEnabled(boolean enabled) {
		pixelSnapping = enabled;
		if (!enabled) snapRegionDrag = false;
	}
	public void setConstraintsEnabled(boolean enabled) {
		if (doc.areConstraintsEnabled() == enabled) return;
		cancelActiveGestures();
		doc.setConstraintsEnabled(enabled);
	}

	public boolean hasActiveGesture() {
		return dragging || draggingView || draggingMeshVertex || draggingDeformVertex || paintingWeights;
	}

	/** A coordinate/evaluation change invalidates every gesture's captured world transform. */
	private void cancelActiveGestures() {
		if (dragging) cancelBoneDrag();
		draggingView = false;
		resetBlankAttachmentClick();
		boolean refresh = draggingMeshVertex || draggingDeformVertex || paintingWeights;
		if (draggingMeshVertex) {
			draggingMeshVertex = false;
			SkeletonEditor.MeshGeometryState before = meshDragBefore;
			meshDragBefore = null;
			meshDragVertices = null;
			meshStartVertexPositions = null;
			if (selectedMesh != null && before != null) editor.applyMeshGeometryPreview(selectedMesh, before);
		}
		if (draggingDeformVertex) {
			if (selectedDeformSlot != null && deformStartPose != null && deformStartAppliedPose != null) {
				setDeform(selectedDeformSlot.getPose().getDeform(), deformStartPose);
				if (selectedDeformSlot.getAppliedPose() != selectedDeformSlot.getPose())
					setDeform(selectedDeformSlot.getAppliedPose().getDeform(), deformStartAppliedPose);
			}
			clearDeformDrag();
		}
		if (paintingWeights) {
			MeshAttachment mesh = weightPaintMesh;
			SkeletonEditor.MeshGeometryState before = weightPaintBefore;
			clearWeightPaint();
			if (mesh != null && before != null) editor.applyMeshGeometryPreview(mesh, before);
		}
		if (refresh && onPoseEdited != null) onPoseEdited.run();
	}
	public void refreshTextureFiltering() {
		for (SkeletonDocument document : currentVisibleDocuments())
			applyTextureFiltering(document.atlas, smoothTextures);
	}

	// ---- 输入 ----

	private boolean touchDown(float x, float y, int button) {
		if (!isHandledButton(button)) return false;
		if (getStage() != null) getStage().setKeyboardFocus(this);
		pointerInside = isLocalPointInside(getWidth(), getHeight(), x, y);
		pointerLocalX = x;
		pointerLocalY = y;
		if (button == Input.Buttons.MIDDLE || button == Input.Buttons.RIGHT) {
			resetBlankAttachmentClick();
			draggingView = true;
			lastScreenX = x;
			lastScreenY = y;
			return true;
		}
		finishBoneDrag();
		if (EditorAppState.tool == EditorTool.MESH) {
			resetBlankAttachmentClick();
			return touchDownMesh(x, y);
		}
		if (EditorAppState.tool == EditorTool.DEFORM) {
			resetBlankAttachmentClick();
			return touchDownDeform(x, y);
		}
		if (EditorAppState.tool == EditorTool.WEIGHT) {
			resetBlankAttachmentClick();
			return touchDownWeight(x, y);
		}
		if (isControlPressed() || isShiftPressed()) {
			Bone hit = showBones ? pickBone(x, y) : null;
			if (hit == null) {
				AttachmentHit attachment = pickAttachment(x, y);
				if (attachment != null) hit = attachment.slot().getBone();
			}
			if (hit != null) {
				clearAttachmentSelection();
				updateBoneSelection(doc, hit, true);
				if (onSelectionChanged != null) onSelectionChanged.run();
			}
			return true;
		}
		TransformAxis transformAxis = pickTransformAxis(x, y);
		if (transformAxis != TransformAxis.FREE) {
			resetBlankAttachmentClick();
			Bone selectedBone = doc.getSelectedBone();
			if (selectedBone != null) beginBoneDrag(selectedBone, x, y, transformAxis, true);
			return true;
		}
		AttachmentHit attachmentHit = pickAttachment(x, y);
		AttachmentHit retainedAttachment = resolveAttachmentSelection();
		if (!isCurrentAttachmentHit(retainedAttachment)) retainedAttachment = null;
		Bone boneHit = attachmentHit == null && showBones ? pickBone(x, y) : null;
		Bone previous = doc.getSelectedBone();
		boolean retainBone = shouldRetainBoneSelection(attachmentHit != null, boneHit != null,
			isViewportBoneTarget(doc.skeleton, previous), doc.getSelectionKind());
		boolean retainAttachment = shouldRetainAttachmentSelection(attachmentHit != null, boneHit != null,
			retainedAttachment != null);
		boolean explicitBlankAttachment = retainAttachment
			&& doc.getSelectionKind() == SkeletonDocument.SelectionKind.ATTACHMENT;
		long now = System.nanoTime();
		boolean cancelAttachmentSelection = isBlankAttachmentDoubleClick(explicitBlankAttachment,
			now, lastBlankAttachmentClickNanos, x, y,
			lastBlankAttachmentClickX, lastBlankAttachmentClickY);
		if (cancelAttachmentSelection) {
			resetBlankAttachmentClick();
			retainAttachment = false;
		} else if (explicitBlankAttachment) {
			blankAttachmentClickCandidate = true;
			blankAttachmentPressX = x;
			blankAttachmentPressY = y;
		} else {
			resetBlankAttachmentClick();
		}
		boolean dragRetainedAttachment = retainAttachment && explicitBlankAttachment;
		if (attachmentHit != null) {
			setAttachmentSelection(attachmentHit);
		} else if (retainAttachment) {
			selectedAttachment = retainedAttachment;
		} else {
			clearAttachmentSelection();
		}
		Bone directHit = attachmentHit != null ? attachmentHit.slot().getBone() : boneHit;
		Bone hit = directHit != null ? directHit
			: retainBone ? previous : retainAttachment ? retainedAttachment.slot().getBone() : null;
		if (hit != null) {
			boolean keepGroup = doc.getSelectionKind() == SkeletonDocument.SelectionKind.BONE
				&& doc.getSelectedBones().size() > 1 && doc.getSelectedBones().contains(hit);
			if (keepGroup) clearAttachmentSelection();
			else if (!retainAttachment) updateBoneSelection(doc, hit, false);
			if (!keepGroup && attachmentHit != null && onAttachmentSelected != null)
				onAttachmentSelected.accept(attachmentHit.slot(), attachmentHit.attachment());
			if ((hit != previous || attachmentHit != null) && onSelectionChanged != null) onSelectionChanged.run();
			beginBoneDrag(keepGroup ? doc.getSelectedBone() : hit, x, y, TransformAxis.FREE,
				shouldStartBoneDrag(attachmentHit != null, boneHit != null, !selectionHidden && dragRetainedAttachment,
					!selectionHidden && retainBone, EditorAppState.tool));
		} else {
			doc.setSelectedBone(null);
			if (previous != null && onSelectionChanged != null) onSelectionChanged.run();
		}
		return true;
	}

	static void updateBoneSelection(SkeletonDocument doc, Bone hit, boolean additive) {
		if (doc == null || hit == null || doc.skeleton == null
			|| !doc.skeleton.getBones().contains(hit, true)) return;
		LinkedHashSet<Bone> selected = doc.getSelectionKind() == SkeletonDocument.SelectionKind.BONE
			? new LinkedHashSet<>(doc.getSelectedBones()) : new LinkedHashSet<>();
		if (additive) {
			if (!selected.add(hit)) selected.remove(hit);
		} else if (!selected.contains(hit)) {
			selected.clear();
			selected.add(hit);
		}
		doc.setSelectedBones(selected);
	}

	private void touchUp() {
		completeBlankAttachmentClick();
		finishMeshVertexDrag();
		finishDeformVertexDrag();
		finishWeightPaint();
		finishBoneDrag();
		draggingView = false;
	}

	private void finishBoneDrag() {
		if (dragging && boneSelectionDrag != null && !boneSelectionDrag.isCurrent()) {
			cancelBoneDrag();
			return;
		}
		boolean wasDragging = dragging;
		BoneSelectionTransform selection = boneSelectionDrag;
		AnimationEditor.Prop editedProp = autoKeyProp(dragTool, dragTransformAxis, dragCoordinateSpace,
			selection == null ? 0 : selection.selectedBones().size());
		boolean changed = boneDragChanged && selection != null && selection.hasChanges();
		dragging = false;
		dragBone = null;
		boneSelectionDrag = null;
		dragTool = null;
		dragTransformAxis = TransformAxis.FREE;
		boneDragChanged = false;
		snapRegionDrag = false;
		boolean autoKeyHandled = changed && selection != null && editedProp != null
			&& onBoneTransformFinished != null
			&& Boolean.TRUE.equals(onBoneTransformFinished.apply(selection.transformedBones(), editedProp));
		if (wasDragging && !autoKeyHandled && history != null) history.commit();
	}

	private void cancelBoneDrag() {
		BoneSelectionTransform selection = boneSelectionDrag;
		dragging = false;
		dragBone = null;
		boneSelectionDrag = null;
		dragTool = null;
		dragTransformAxis = TransformAxis.FREE;
		boneDragChanged = false;
		snapRegionDrag = false;
		if (history != null) history.rollbackPending();
		else if (selection != null) selection.restore();
		if (onPoseEdited != null) onPoseEdited.run();
	}

	private void beginBoneDrag(Bone bone, float x, float y, TransformAxis axis, boolean active) {
		EditorTool tool = EditorAppState.tool;
		dragging = active && isViewportBoneSelectionActive(doc) && isBoneDragTargetValid(doc.skeleton, bone, doc.getSelectedBone(), tool,
			doc.getSelectionKind());
		dragBone = dragging ? bone : null;
		dragTool = dragging ? tool : null;
		dragCoordinateSpace = coordinateSpace;
		dragTransformAxis = dragging && axis != null ? axis : TransformAxis.FREE;
		boneDragChanged = false;
		snapRegionDrag = false;
		if (!dragging) return;
		try {
			boneSelectionDrag = BoneSelectionTransform.capture(doc);
		} catch (IllegalArgumentException error) {
			dragging = false;
			dragBone = null;
			showStatus("无法变换骨骼：" + error.getMessage(), true);
			return;
		}
		startDrag.set(x, y);
		Vector2 world = screenToWorld(x, y);
		var appliedPose = bone.getAppliedPose();
		startBoneWorld.set(appliedPose.getWorldX(), appliedPose.getWorldY());
		startAngle = (float)Math.toDegrees(Math.atan2(world.y - startBoneWorld.y, world.x - startBoneWorld.x));
		startX = bone.getPose().getX();
		startY = bone.getPose().getY();
		startRotation = bone.getPose().getRotation();
		startScaleX = bone.getPose().getScaleX();
		startScaleY = bone.getPose().getScaleY();
		startShearX = bone.getPose().getShearX();
		startShearY = bone.getPose().getShearY();
		AttachmentHit snapHit = pixelSnapping && tool == EditorTool.MOVE
			&& doc.getSelectionKind() == SkeletonDocument.SelectionKind.ATTACHMENT
			? resolveAttachmentSelection() : null;
		if (snapHit != null && snapHit.slot().getBone() == bone
			&& snapHit.attachment() instanceof RegionAttachment region) {
			regionOriginWorld(region, bone, tmp2);
			if (Float.isFinite(tmp2.x) && Float.isFinite(tmp2.y)) {
				snapRegionDrag = true;
				snapRegionStartWorldX = tmp2.x;
				snapRegionStartWorldY = tmp2.y;
			}
		}
		if (dragTransformAxis == TransformAxis.X || dragTransformAxis == TransformAxis.Y) {
			float rotation = transformAxisWorldRotation(dragTool, dragTransformAxis, bone);
			dragAxisWorldX = (float)Math.cos(Math.toRadians(rotation));
			dragAxisWorldY = (float)Math.sin(Math.toRadians(rotation));
		}
		if (history != null) history.begin("视口编辑骨骼");
	}

	public BoneCoordinateSpace coordinateSpace() { return coordinateSpace; }

	public void setCoordinateSpace(BoneCoordinateSpace space) {
		if (space == null || space == coordinateSpace) return;
		if (dragging) cancelBoneDrag();
		coordinateSpace = space;
		hoveredTransformAxis = TransformAxis.FREE;
	}

	private void touchDragged(float x, float y) {
		pointerInside = isLocalPointInside(getWidth(), getHeight(), x, y);
		pointerLocalX = x;
		pointerLocalY = y;
		if (blankAttachmentClickCandidate
			&& distanceSquared(x, y, blankAttachmentPressX, blankAttachmentPressY)
				> CLICK_DRAG_TOLERANCE_PIXELS * CLICK_DRAG_TOLERANCE_PIXELS)
			resetBlankAttachmentClick();
		if (draggingView) {
			float dx = x - lastScreenX;
			float dy = y - lastScreenY;
			lastScreenX = x;
			lastScreenY = y;
			camera.position.add(panWorldDelta(dx, camera.zoom, viewportFlipped),
				-dy * camera.zoom, 0);
			return;
		}
		if (dragging) applyToolDrag(x, y);
		if (draggingMeshVertex) applyMeshVertexDrag(x, y);
		if (draggingDeformVertex) applyDeformVertexDrag(x, y);
		if (paintingWeights) applyWeightBrushDrag(x, y);
	}

	private void mouseMoved(float x, float y) {
		pointerInside = isLocalPointInside(getWidth(), getHeight(), x, y);
		pointerLocalX = x;
		pointerLocalY = y;
		hoveredTransformAxis = pointerInside ? pickTransformAxis(x, y) : TransformAxis.FREE;
		hoveredAttachment = !selectionHidden && pointerInside && hoveredTransformAxis == TransformAxis.FREE
			? pickAttachment(x, y) : null;
	}

	private boolean touchDownMesh(float x, float y) {
		if (getStage() != null) getStage().setKeyboardFocus(this);
		if (isControlPressed()) {
			MeshTriangleHit triangle = pickMeshTriangle(x, y);
			if (triangle != null) {
				insertMeshVertex(triangle);
				return true;
			}
		}
		MeshVertexHit hit = pickMeshVertex(x, y);
		if (hit == null) {
			clearMeshSelection();
			return true;
		}
		if (!selectMeshHit(hit, isShiftPressed())) return true;
		if (hit.mesh().getSourceMesh() != null) {
			showStatus("Linked Mesh 顶点由源网格共享，请编辑源 Mesh", false);
			return true;
		}
		if (hit.mesh().getBones() != null) {
			showStatus("加权 Mesh 顶点请在 Mesh 权重编辑器中修改", false);
			return true;
		}
		float[] vertices = hit.mesh().getVertices();
		int value = hit.vertex() * 2;
		if (vertices == null || value + 1 >= vertices.length) return true;
		Vector2 world = screenToWorld(x, y);
		tmp2.set(world);
		hit.slot().getBone().getAppliedPose().worldToLocal(tmp2);
		meshStartCursorLocalX = tmp2.x;
		meshStartCursorLocalY = tmp2.y;
		meshDragVertices = selectedMeshVertices.toArray();
		meshStartVertexPositions = new float[meshDragVertices.length * 2];
		for (int selection = 0; selection < meshDragVertices.length; selection++) {
			int selectedValue = meshDragVertices[selection] * 2;
			if (selectedValue < 0 || selectedValue + 1 >= vertices.length) {
				meshDragVertices = null;
				meshStartVertexPositions = null;
				return true;
			}
			meshStartVertexPositions[selection * 2] = vertices[selectedValue];
			meshStartVertexPositions[selection * 2 + 1] = vertices[selectedValue + 1];
		}
		meshDragBefore = SkeletonEditor.meshGeometryState(hit.mesh());
		draggingMeshVertex = true;
		return true;
	}

	private void insertMeshVertex(MeshTriangleHit hit) {
		MeshAttachment mesh = hit.mesh();
		if (mesh.getSourceMesh() != null) {
			showStatus("Linked Mesh 共享源拓扑，请编辑源 Mesh", true);
			return;
		}
		SkeletonEditor.MeshTopologySnapshot before;
		SkeletonEditor.MeshTopologySnapshot after;
		int insertedVertex = mesh.getWorldVerticesLength() / 2;
		try {
			before = editor.snapshotMeshTopology(mesh);
			if (!editor.insertMeshVertexWithDeform(mesh, hit.triangleOffset(),
				hit.weightA(), hit.weightB(), hit.weightC())) return;
			after = editor.snapshotMeshTopology(mesh);
		} catch (IllegalArgumentException error) {
			showStatus("Mesh 插点失败：" + error.getMessage(), true);
			return;
		}
		finishMigratedMeshTopologyEdit(mesh, hit.slot().getData(), before, after, "插入 Mesh 顶点",
			"Mesh 顶点已插入，Deform 已按重心坐标迁移", new int[] {insertedVertex});
	}

	private void createSelectedMeshTriangle() {
		if (selectedMesh == null || selectedMeshSlot == null || selectedMeshVertices.size != 3) {
			showStatus("建面需要在 Mesh 工具中选择恰好 3 个顶点", true);
			return;
		}
		if (selectedMesh.getSourceMesh() != null) {
			showStatus("Linked Mesh 共享源拓扑，请编辑源 Mesh", true);
			return;
		}
		SlotData slotData = selectedMeshSlot.getData();
		if (doc.skeleton == null || slotData.getIndex() < 0 || slotData.getIndex() >= doc.skeleton.getSlots().size) return;
		Slot runtimeSlot = doc.skeleton.getSlots().get(slotData.getIndex());
		float[] world = setupWorldVertices(selectedMesh, runtimeSlot);
		int[] vertices = orientedTriangle(selectedMeshVertices.get(0), selectedMeshVertices.get(1),
			selectedMeshVertices.get(2), world, selectedMesh.getTriangles());
		if (vertices == null) {
			showStatus("所选 Mesh 顶点共线或索引无效，不能建面", true);
			return;
		}
		SkeletonEditor.MeshGeometryState before = SkeletonEditor.meshGeometryState(selectedMesh);
		SkeletonEditor.MeshGeometryState after;
		try {
			after = SkeletonEditor.addMeshTriangle(before, vertices[0], vertices[1], vertices[2]);
		} catch (IllegalArgumentException error) {
			showStatus("Mesh 建面失败：" + error.getMessage(), true);
			return;
		}
		applyMeshTopologyEdit(selectedMesh, slotData, before, after, "创建 Mesh 三角形",
			"Mesh 三角形已创建", selectedMeshVertices.toArray());
	}

	private void deleteSelectedMeshVertices() {
		if (selectedMesh == null || selectedMeshSlot == null || selectedMeshVertices.isEmpty()) {
			showStatus("请先选择要删除的 Mesh 顶点", true);
			return;
		}
		if (selectedMesh.getSourceMesh() != null) {
			showStatus("Linked Mesh 共享源拓扑，请编辑源 Mesh", true);
			return;
		}
		SkeletonEditor.MeshTopologySnapshot before;
		SkeletonEditor.MeshTopologySnapshot after;
		try {
			before = editor.snapshotMeshTopology(selectedMesh);
			if (!editor.removeMeshVerticesWithDeform(selectedMesh, selectedMeshVertices.toArray())) return;
			after = editor.snapshotMeshTopology(selectedMesh);
		} catch (IllegalArgumentException error) {
			showStatus("Mesh 删点失败：" + error.getMessage(), true);
			return;
		}
		finishMigratedMeshTopologyEdit(selectedMesh, selectedMeshSlot.getData(), before, after,
			"删除 Mesh 顶点", "Mesh 顶点、关联拓扑和 Deform 数据已同步删除", new int[0]);
	}

	private void finishMigratedMeshTopologyEdit(MeshAttachment mesh, SlotData slotData,
		SkeletonEditor.MeshTopologySnapshot before, SkeletonEditor.MeshTopologySnapshot after,
		String historyLabel, String successMessage, int[] nextSelection) {
		selectedMeshVertices.clear();
		if (nextSelection != null) selectedMeshVertices.addAll(nextSelection);
		if (selectedMeshVertices.notEmpty() && doc.skeleton != null && slotData.getIndex() >= 0
			&& slotData.getIndex() < doc.skeleton.getSlots().size) {
			Slot runtimeSlot = doc.skeleton.getSlots().get(slotData.getIndex());
			applyMeshSelection(new MeshVertexHit(mesh, runtimeSlot, selectedMeshVertices.peek()));
		} else {
			clearMeshSelection();
		}
		if (history != null) {
			history.record(historyLabel, () -> editor.restoreMeshTopology(before),
				() -> editor.restoreMeshTopology(after));
		} else if (onPoseEdited != null) {
			onPoseEdited.run();
		}
		showStatus(successMessage, false);
	}

	private boolean applyMeshTopologyEdit(MeshAttachment mesh, SlotData slotData,
		SkeletonEditor.MeshGeometryState before, SkeletonEditor.MeshGeometryState after,
		String historyLabel, String successMessage, int[] nextSelection) {
		String reason = editor.validateMeshGeometry(mesh, after);
		if (reason != null) {
			showStatus("Mesh 拓扑修改受保护：" + reason, true);
			return false;
		}
		if (!editor.updateMeshGeometry(mesh, after)) {
			showStatus("Mesh 拓扑状态已变化，请重试", true);
			return false;
		}
		selectedMeshVertices.clear();
		if (nextSelection != null) selectedMeshVertices.addAll(nextSelection);
		if (selectedMeshVertices.notEmpty() && doc.skeleton != null && slotData.getIndex() >= 0
			&& slotData.getIndex() < doc.skeleton.getSlots().size) {
			Slot runtimeSlot = doc.skeleton.getSlots().get(slotData.getIndex());
			applyMeshSelection(new MeshVertexHit(mesh, runtimeSlot, selectedMeshVertices.peek()));
		} else {
			clearMeshSelection();
		}
		if (history != null) {
			history.record(historyLabel, () -> restoreMeshGeometry(mesh, before),
				() -> restoreMeshGeometry(mesh, after));
		} else if (onPoseEdited != null) {
			onPoseEdited.run();
		}
		showStatus(successMessage, false);
		return true;
	}

	private boolean touchDownDeform(float x, float y) {
		VertexAttachmentHit hit = pickDeformVertex(x, y);
		if (hit == null) {
			clearDeformSelection();
			clearDeformDrag();
			return true;
		}
		if (!selectDeformHit(hit, isShiftPressed())) return true;
		Animation animation = currentAnimation();
		if (animation == null) {
			showStatus("变形工具需要当前动画", true);
			return true;
		}
		AnimationEditor.DeformTarget deformTarget;
		try {
			deformTarget = animationEditor.resolveDeformTarget(hit.slot().getData().getIndex(), hit.attachment());
		} catch (IllegalArgumentException error) {
			showStatus(error.getMessage(), true);
			return true;
		}
		VertexAttachment target = deformTarget.attachment();
		float time = doc.getCurrentTime();
		if (!Float.isFinite(time) || time < 0f || !doc.scrubTo(time)) {
			showStatus("无法在当前播放头创建 Deform 关键帧", true);
			return true;
		}

		float[] offsets;
		try {
			offsets = animationEditor.currentDeformOffsets(hit.slot().getData().getIndex(), target);
		} catch (IllegalArgumentException error) {
			showStatus(error.getMessage(), true);
			return true;
		}
		int selectionCount = selectedDeformVertices.size;
		float[] transforms = new float[selectionCount * 4];
		int[] valueStarts = new int[selectionCount];
		int[] valueCounts = new int[selectionCount];
		float[] transform = new float[4];
		for (int selection = 0; selection < selectionCount; selection++) {
			int vertex = selectedDeformVertices.get(selection);
			if (!deformVertexTransform(hit.attachment(), hit.slot(), vertex, doc.skeleton, transform)) {
				showStatus("选中顶点的骨骼变换不可逆，无法拖动变形", true);
				return true;
			}
			System.arraycopy(transform, 0, transforms, selection * 4, 4);
			int[] valueRange = deformValueRange(target, vertex);
			if (valueRange == null || valueRange[0] + valueRange[1] > offsets.length) {
				showStatus("Deform 顶点索引无效", true);
				return true;
			}
			valueStarts[selection] = valueRange[0];
			valueCounts[selection] = valueRange[1];
		}

		Vector2 world = screenToWorld(x, y);
		deformDragAnimation = animation;
		deformDragAttachment = target;
		deformDragSlotIndex = deformTarget.slotIndex();
		deformDragBefore = animationEditor.snapshotDeformTimeline(animation,
			deformDragSlotIndex, target);
		deformDragTime = time;
		deformStartCursorWorldX = world.x;
		deformStartCursorWorldY = world.y;
		deformVertexTransforms = transforms;
		deformValueStarts = valueStarts;
		deformValueCounts = valueCounts;
		deformStartOffsets = offsets.clone();
		deformDragOffsets = offsets;
		deformStartPose = hit.slot().getPose().getDeform().toArray();
		deformStartAppliedPose = hit.slot().getAppliedPose().getDeform().toArray();
		draggingDeformVertex = true;
		if (onPoseEdited != null) onPoseEdited.run();
		return true;
	}

	private boolean touchDownWeight(float x, float y) {
		AttachmentHit hit = pickAttachment(x, y);
		if (hit == null || !(hit.attachment() instanceof MeshAttachment mesh)) {
			showStatus("权重工具需要在 Mesh 图片范围内开始绘制", true);
			return true;
		}
		if (mesh.getSourceMesh() != null) {
			showStatus("Linked Mesh 共享源权重，请在源 Mesh 上绘制", true);
			return true;
		}
		Bone target = doc.getSelectedBone();
		if (target == null) {
			showStatus("请先在骨骼树中选择权重目标骨骼", true);
			return true;
		}
		SkeletonEditor.MeshWeightPaintContext context;
		try {
			context = editor.prepareMeshWeightPaint(mesh, hit.slot().getData(), target.getData());
		} catch (IllegalArgumentException error) {
			showStatus("无法开始绘制 Mesh 权重：" + error.getMessage(), true);
			return true;
		}

		weightPaintMesh = mesh;
		weightPaintSlot = hit.slot();
		weightPaintBefore = SkeletonEditor.meshGeometryState(mesh);
		weightPaintContext = context;
		weightPaintValidationError = false;
		paintingWeights = true;
		setAttachmentSelection(hit);
		hoveredAttachment = hit;
		pointerInside = true;
		pointerLocalX = x;
		pointerLocalY = y;
		if (onAttachmentSelected != null) onAttachmentSelected.accept(hit.slot(), mesh);
		if (onSelectionChanged != null) onSelectionChanged.run();
		Vector2 world = screenToWorld(x, y);
		weightPaintLastWorldX = world.x;
		weightPaintLastWorldY = world.y;
		applyWeightBrushDab(world.x, world.y, isShiftPressed());
		return true;
	}

	private void applyWeightBrushDrag(float x, float y) {
		if (!paintingWeights || weightPaintMesh == null || weightPaintSlot == null) return;
		Vector2 world = screenToWorld(x, y);
		float endX = world.x, endY = world.y;
		float dx = endX - weightPaintLastWorldX, dy = endY - weightPaintLastWorldY;
		float distance = (float)Math.hypot(dx, dy);
		float radius = WEIGHT_BRUSH_RADIUS_PIXELS * camera.zoom;
		float spacing = Math.max(4f * camera.zoom, radius * 0.22f);
		int steps = Float.isFinite(distance) && distance > 0f ? Math.max(1, (int)Math.ceil(distance / spacing)) : 0;
		boolean subtract = isShiftPressed();
		for (int step = 1; step <= steps; step++) {
			float alpha = step / (float)steps;
			applyWeightBrushDab(weightPaintLastWorldX + dx * alpha, weightPaintLastWorldY + dy * alpha, subtract);
		}
		weightPaintLastWorldX = endX;
		weightPaintLastWorldY = endY;
	}

	private boolean applyWeightBrushDab(float worldX, float worldY, boolean subtract) {
		if (weightPaintMesh == null || weightPaintSlot == null || weightPaintContext == null) return false;
		float[] worldVertices = setupWorldVertices(weightPaintMesh, weightPaintSlot);
		int vertexCount = weightPaintMesh.getWorldVerticesLength() / 2;
		if (worldVertices.length < vertexCount * 2) return false;
		float radius = WEIGHT_BRUSH_RADIUS_PIXELS * camera.zoom;
		float[] amounts = new float[vertexCount];
		boolean affected = false;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			float distance = (float)Math.hypot(worldVertices[vertex * 2] - worldX,
				worldVertices[vertex * 2 + 1] - worldY);
			float amount = weightBrushFalloff(distance, radius, WEIGHT_BRUSH_STRENGTH);
			if (amount == 0f) continue;
			amounts[vertex] = subtract ? -amount : amount;
			affected = true;
		}
		if (!affected) return false;

		SkeletonEditor.MeshGeometryState current = SkeletonEditor.meshGeometryState(weightPaintMesh);
		SkeletonEditor.MeshGeometryState next;
		String lockKey = editor.meshWeightStateKey(weightPaintMesh, weightPaintSlot.getData());
		var locks = doc.meshWeightLocks(lockKey);
		try {
			next = SkeletonEditor.paintMeshWeights(current, weightPaintContext, amounts, locks);
		} catch (IllegalArgumentException error) {
			showStatus("Mesh 权重绘制失败：" + error.getMessage(), true);
			weightPaintValidationError = true;
			return false;
		}
		if (current.sameContent(next)) return false;
		String reason = editor.validateMeshGeometry(weightPaintMesh, next);
		if (reason != null && reason.contains("Deform 顶点数量")) {
			boolean retained = false;
			for (int vertex = 0; vertex < amounts.length; vertex++) {
				if (amounts[vertex] != 0f && !meshVertexHasBoneInfluence(weightPaintMesh, vertex,
					weightPaintContext.targetBoneIndex())) amounts[vertex] = 0f;
				if (amounts[vertex] != 0f) retained = true;
			}
			if (retained) {
				next = SkeletonEditor.paintMeshWeights(current, weightPaintContext, amounts, locks);
				reason = current.sameContent(next) ? null : editor.validateMeshGeometry(weightPaintMesh, next);
			}
		}
		if (reason != null) {
			if (!weightPaintValidationError) showStatus("Mesh 权重绘制受保护：" + reason, true);
			weightPaintValidationError = true;
			return false;
		}
		if (current.sameContent(next)) return false;
		editor.applyMeshGeometryPreview(weightPaintMesh, next);
		weightPaintValidationError = false;
		return true;
	}

	private void finishWeightPaint() {
		if (!paintingWeights) return;
		MeshAttachment mesh = weightPaintMesh;
		SkeletonEditor.MeshGeometryState before = weightPaintBefore;
		SkeletonEditor.MeshGeometryState after = mesh == null ? null : SkeletonEditor.meshGeometryState(mesh);
		clearWeightPaint();
		if (mesh == null || before == null || after == null || before.sameContent(after)) return;

		editor.applyMeshGeometryPreview(mesh, before);
		if (!editor.updateMeshGeometry(mesh, after)) {
			String reason = editor.validateMeshGeometry(mesh, after);
			showStatus("Mesh 权重修改失败" + (reason == null ? "" : "：" + reason), true);
			return;
		}
		hoveredAttachment = null;
		clearMeshEditSelection();
		if (history != null) {
			history.record("绘制 Mesh 权重", () -> restoreMeshGeometry(mesh, before),
				() -> restoreMeshGeometry(mesh, after));
		} else if (onPoseEdited != null) {
			onPoseEdited.run();
		}
		showStatus("Mesh 权重已更新", false);
	}

	private void clearWeightPaint() {
		paintingWeights = false;
		weightPaintMesh = null;
		weightPaintSlot = null;
		weightPaintBefore = null;
		weightPaintContext = null;
		weightPaintValidationError = false;
	}

	static float weightBrushFalloff(float distance, float radius, float strength) {
		if (!Float.isFinite(distance) || !Float.isFinite(radius) || !Float.isFinite(strength)
			|| distance < 0f || radius <= 0f || strength <= 0f) return 0f;
		float normalized = Math.max(0f, 1f - distance / radius);
		return Math.min(1f, strength * normalized * normalized);
	}

	private Animation currentAnimation() {
		if (doc.data == null) return null;
		String name = doc.getCurrentAnimationName();
		return name.isEmpty() ? null : doc.data.findAnimation(name);
	}

	private void selectMeshHit(MeshVertexHit hit) {
		selectedMeshVertices.clear();
		selectedMeshVertices.add(hit.vertex());
		applyMeshSelection(hit);
	}

	private boolean selectMeshHit(MeshVertexHit hit, boolean toggle) {
		boolean sameMesh = selectedMesh == hit.mesh() && selectedMeshSlot != null
			&& selectedMeshSlot.getData() == hit.slot().getData();
		if (!sameMesh) selectedMeshVertices.clear();
		boolean selected = updateMeshVertexSelection(selectedMeshVertices, hit.vertex(), toggle && sameMesh);
		if (!selected) {
			if (selectedMeshVertices.size == 0) {
				clearMeshSelection();
				if (onSelectionChanged != null) onSelectionChanged.run();
			}
			else {
				selectedMeshVertex = selectedMeshVertices.peek();
				applyMeshSelection(new MeshVertexHit(hit.mesh(), hit.slot(), selectedMeshVertex));
			}
			return false;
		}
		applyMeshSelection(hit);
		return true;
	}

	private void applyMeshSelection(MeshVertexHit hit) {
		selectedMesh = hit.mesh();
		selectedMeshSlot = hit.slot();
		selectedMeshVertex = hit.vertex();
		setAttachmentSelection(new AttachmentHit(hit.slot(), hit.mesh()));
		doc.setSelectedBone(hit.slot().getBone());
		if (onAttachmentSelected != null) onAttachmentSelected.accept(hit.slot(), hit.mesh());
		if (onSelectionChanged != null) onSelectionChanged.run();
	}

	private boolean selectDeformHit(VertexAttachmentHit hit, boolean toggle) {
		boolean sameAttachment = selectedDeformAttachment == hit.attachment() && selectedDeformSlot != null
			&& selectedDeformSlot.getData() == hit.slot().getData();
		if (!sameAttachment) selectedDeformVertices.clear();
		boolean selected = updateMeshVertexSelection(selectedDeformVertices, hit.vertex(),
			toggle && sameAttachment);
		if (!selected) {
			if (selectedDeformVertices.isEmpty()) {
				clearDeformSelection();
				if (onSelectionChanged != null) onSelectionChanged.run();
			} else {
				applyDeformSelection(new VertexAttachmentHit(hit.attachment(), hit.slot(),
					selectedDeformVertices.peek()));
			}
			return false;
		}
		applyDeformSelection(hit);
		return true;
	}

	private void applyDeformSelection(VertexAttachmentHit hit) {
		selectedDeformAttachment = hit.attachment();
		selectedDeformSlot = hit.slot();
		setAttachmentSelection(new AttachmentHit(hit.slot(), hit.attachment()));
		doc.setSelectedBone(hit.slot().getBone());
		if (onAttachmentSelected != null) onAttachmentSelected.accept(hit.slot(), hit.attachment());
		if (onSelectionChanged != null) onSelectionChanged.run();
	}

	private void clearDeformSelection() {
		selectedDeformAttachment = null;
		selectedDeformSlot = null;
		selectedDeformVertices.clear();
		clearAttachmentSelection();
	}

	private void clearMeshSelection() {
		clearMeshEditSelection();
		clearAttachmentSelection();
	}

	private void clearMeshEditSelection() {
		selectedMesh = null;
		selectedMeshSlot = null;
		selectedMeshVertex = -1;
		selectedMeshVertices.clear();
	}

	private void applyMeshVertexDrag(float x, float y) {
		if (selectedMesh == null || selectedMeshSlot == null || selectedMeshVertex < 0
			|| meshDragVertices == null || meshStartVertexPositions == null
			|| selectedMesh.getSourceMesh() != null || selectedMesh.getBones() != null) return;
		float[] vertices = selectedMesh.getVertices();
		Vector2 world = screenToWorld(x, y);
		tmp2.set(world);
		selectedMeshSlot.getBone().getAppliedPose().worldToLocal(tmp2);
		if (!applyMeshVertexDelta(vertices, meshDragVertices, meshStartVertexPositions,
			tmp2.x - meshStartCursorLocalX, tmp2.y - meshStartCursorLocalY)) return;
		SkeletonDocument.updateWorldTransform(doc.skeleton, Physics.pose, doc.areConstraintsEnabled());
	}

	private void finishMeshVertexDrag() {
		if (!draggingMeshVertex) return;
		draggingMeshVertex = false;
		meshDragVertices = null;
		meshStartVertexPositions = null;
		if (selectedMesh == null || meshDragBefore == null) {
			meshDragBefore = null;
			return;
		}
		SkeletonEditor.MeshGeometryState after = SkeletonEditor.meshGeometryState(selectedMesh);
		SkeletonEditor.MeshGeometryState before = meshDragBefore;
		meshDragBefore = null;
		if (before.sameContent(after)) return;
		try {
			if (!editor.commitMeshGeometryPreview(selectedMesh, before)) return;
		} catch (RuntimeException error) {
			showStatus("Mesh 顶点修改失败：" + error.getMessage(), true);
			return;
		}
		if (history != null) {
			MeshAttachment editedMesh = selectedMesh;
			history.record("移动 Mesh 顶点", () -> restoreMeshGeometry(editedMesh, before),
				() -> restoreMeshGeometry(editedMesh, after));
		} else if (onPoseEdited != null) {
			onPoseEdited.run();
		}
		showStatus("Mesh 顶点已移动", false);
	}

	private void applyDeformVertexDrag(float x, float y) {
		if (!draggingDeformVertex || selectedDeformAttachment == null || selectedDeformSlot == null
			|| selectedDeformVertices.isEmpty() || deformDragOffsets == null || deformVertexTransforms == null
			|| deformValueStarts == null || deformValueCounts == null) return;
		Vector2 world = screenToWorld(x, y);
		if (!applyDeformVertexDeltas(deformStartOffsets, deformDragOffsets, deformValueStarts,
			deformValueCounts, deformVertexTransforms, world.x - deformStartCursorWorldX,
			world.y - deformStartCursorWorldY)) return;
		setRuntimeDeformPreview(selectedDeformSlot, selectedDeformAttachment, deformDragOffsets);
	}

	private void finishDeformVertexDrag() {
		if (!draggingDeformVertex) return;
		draggingDeformVertex = false;
		Animation animation = deformDragAnimation;
		VertexAttachment attachment = deformDragAttachment;
		AnimationEditor.TimelineSnapshot before = deformDragBefore;
		float time = deformDragTime;
		float[] offsets = deformDragOffsets;
		float[] startOffsets = deformStartOffsets;
		int slotIndex = deformDragSlotIndex;
		clearDeformDrag();
		if (animation == null || attachment == null || before == null || offsets == null
			|| startOffsets == null || slotIndex < 0) return;
		if (Arrays.equals(offsets, startOffsets)) {
			doc.refreshCurrentAnimationPose();
			return;
		}
		try {
			animationEditor.addDeformKeyframe(animation, slotIndex, attachment, time, offsets);
			doc.scrubTo(time);
			AnimationEditor.TimelineSnapshot after = animationEditor.snapshotCurrent(before);
			if (history != null && !before.sameAs(after)) {
				history.record("移动 Deform 顶点",
					() -> restoreDeformTimeline(before), () -> restoreDeformTimeline(after));
			} else if (onPoseEdited != null) {
				onPoseEdited.run();
			}
			showStatus("Deform 关键帧已更新", false);
		} catch (RuntimeException error) {
			animationEditor.restoreTimeline(before);
			doc.refreshCurrentAnimationPose();
			showStatus("Deform 顶点修改失败：" + error.getMessage(), true);
		}
	}

	private void restoreDeformTimeline(AnimationEditor.TimelineSnapshot snapshot) {
		animationEditor.restoreTimeline(snapshot);
		doc.refreshCurrentAnimationPose();
	}

	private void clearDeformDrag() {
		draggingDeformVertex = false;
		deformDragAnimation = null;
		deformDragAttachment = null;
		deformDragSlotIndex = -1;
		deformDragBefore = null;
		deformVertexTransforms = null;
		deformValueStarts = null;
		deformValueCounts = null;
		deformStartOffsets = null;
		deformDragOffsets = null;
		deformStartPose = null;
		deformStartAppliedPose = null;
	}

	static int[] deformValueRange(VertexAttachment attachment, int vertex) {
		if (attachment == null || vertex < 0) return null;
		if (attachment.getBones() == null) {
			int start = vertex * 2;
			return attachment.getVertices() != null && start + 1 < attachment.getVertices().length
				? new int[] {start, 2} : null;
		}
		int[] bones = attachment.getBones();
		int boneCursor = 0, valueStart = 0;
		for (int logicalVertex = 0; logicalVertex <= vertex; logicalVertex++) {
			if (boneCursor >= bones.length) return null;
			int influenceCount = bones[boneCursor++];
			if (influenceCount <= 0 || boneCursor + influenceCount > bones.length) return null;
			if (logicalVertex == vertex) return new int[] {valueStart, influenceCount * 2};
			boneCursor += influenceCount;
			valueStart += influenceCount * 2;
		}
		return null;
	}

	static boolean applyDeformDelta(float[] start, float[] target, int valueStart, int valueCount,
		float deltaX, float deltaY) {
		if (start == null || target == null || start.length != target.length || valueStart < 0
			|| valueCount <= 0 || (valueCount & 1) != 0 || valueStart + valueCount > start.length
			|| !Float.isFinite(deltaX) || !Float.isFinite(deltaY)) return false;
		for (int value = valueStart; value < valueStart + valueCount; value += 2) {
			if (!Float.isFinite(start[value]) || !Float.isFinite(start[value + 1])
				|| !Float.isFinite(start[value] + deltaX)
				|| !Float.isFinite(start[value + 1] + deltaY)) return false;
		}
		for (int value = valueStart; value < valueStart + valueCount; value += 2) {
			target[value] = start[value] + deltaX;
			target[value + 1] = start[value + 1] + deltaY;
		}
		return true;
	}

	static boolean applyDeformVertexDeltas(float[] start, float[] target, int[] valueStarts,
		int[] valueCounts, float[] transforms, float worldDeltaX, float worldDeltaY) {
		if (start == null || target == null || start.length != target.length || valueStarts == null
			|| valueCounts == null || transforms == null || valueStarts.length == 0
			|| valueCounts.length != valueStarts.length || transforms.length != valueStarts.length * 4
			|| !Float.isFinite(worldDeltaX) || !Float.isFinite(worldDeltaY)) return false;
		float[] localDeltas = new float[valueStarts.length * 2];
		Vector2 delta = new Vector2();
		for (int selection = 0; selection < valueStarts.length; selection++) {
			int transform = selection * 4;
			if (inverseTransformDelta(transforms[transform], transforms[transform + 1],
				transforms[transform + 2], transforms[transform + 3], worldDeltaX, worldDeltaY, delta) == null
				|| !Float.isFinite(delta.x) || !Float.isFinite(delta.y)) return false;
			int valueStart = valueStarts[selection], valueCount = valueCounts[selection];
			if (valueStart < 0 || valueCount <= 0 || (valueCount & 1) != 0
				|| valueStart > start.length - valueCount) return false;
			for (int value = valueStart; value < valueStart + valueCount; value += 2) {
				if (!Float.isFinite(start[value]) || !Float.isFinite(start[value + 1])
					|| !Float.isFinite(start[value] + delta.x)
					|| !Float.isFinite(start[value + 1] + delta.y)) return false;
			}
			localDeltas[selection * 2] = delta.x;
			localDeltas[selection * 2 + 1] = delta.y;
		}
		for (int selection = 0; selection < valueStarts.length; selection++) {
			if (!applyDeformDelta(start, target, valueStarts[selection], valueCounts[selection],
				localDeltas[selection * 2], localDeltas[selection * 2 + 1])) return false;
		}
		return true;
	}

	private static void setRuntimeDeformPreview(Slot slot, VertexAttachment displayedAttachment,
		float[] offsets) {
		float[] values = runtimeDeformValues(displayedAttachment, offsets);
		setDeform(slot.getPose().getDeform(), values);
		if (slot.getAppliedPose() != slot.getPose()) setDeform(slot.getAppliedPose().getDeform(), values);
	}

	private static void setDeform(FloatArray deform, float[] values) {
		float[] target = deform.setSize(values.length);
		System.arraycopy(values, 0, target, 0, values.length);
	}

	static float[] runtimeDeformValues(VertexAttachment attachment, float[] offsets) {
		if (attachment == null || offsets == null)
			throw new IllegalArgumentException("Deform 预览数据不完整");
		float[] values = offsets.clone();
		if (attachment.getBones() == null) {
			float[] setup = attachment.getVertices();
			if (setup == null || setup.length != values.length)
				throw new IllegalArgumentException("Deform 顶点数量与附件不匹配");
			for (int index = 0; index < values.length; index++) values[index] += setup[index];
		}
		return values;
	}

	static Vector2 inverseTransformDelta(float a, float b, float c, float d, float worldX, float worldY,
		Vector2 out) {
		if (!allFinite(a, b, c, d, worldX, worldY)) return null;
		float determinant = a * d - b * c;
		if (!Float.isFinite(determinant) || Math.abs(determinant) <= 0.000001f) return null;
		if (out == null) out = new Vector2();
		return out.set((d * worldX - b * worldY) / determinant,
			(a * worldY - c * worldX) / determinant);
	}

	static boolean deformVertexTransform(VertexAttachment attachment, Slot slot, int vertex,
		Skeleton skeleton, float[] out) {
		if (attachment == null || slot == null || out == null || out.length < 4 || vertex < 0) return false;
		if (attachment.getBones() == null) {
			var pose = slot.getBone().getAppliedPose();
			out[0] = pose.getA();
			out[1] = pose.getB();
			out[2] = pose.getC();
			out[3] = pose.getD();
		} else if (!weightedVertexTransform(attachment, vertex, skeleton, out)) {
			return false;
		}
		return isInvertibleTransform(out[0], out[1], out[2], out[3]);
	}

	static boolean weightedVertexTransform(VertexAttachment attachment, int vertex, Skeleton skeleton,
		float[] out) {
		if (attachment == null || vertex < 0 || skeleton == null || out == null || out.length < 4) return false;
		int[] bones = attachment.getBones();
		float[] vertices = attachment.getVertices();
		if (bones == null || vertices == null) return false;
		int boneCursor = 0, valueCursor = 0;
		for (int logicalVertex = 0; logicalVertex <= vertex; logicalVertex++) {
			if (boneCursor >= bones.length) return false;
			int influenceCount = bones[boneCursor++];
			if (influenceCount <= 0 || boneCursor + influenceCount > bones.length
				|| valueCursor + influenceCount * 3 > vertices.length) return false;
			if (logicalVertex != vertex) {
				boneCursor += influenceCount;
				valueCursor += influenceCount * 3;
				continue;
			}
			float a = 0f, b = 0f, c = 0f, d = 0f;
			for (int influence = 0; influence < influenceCount; influence++) {
				int boneIndex = bones[boneCursor++];
				if (boneIndex < 0 || boneIndex >= skeleton.getBones().size) return false;
				float weight = vertices[valueCursor + 2];
				if (!Float.isFinite(weight)) return false;
				var pose = skeleton.getBones().get(boneIndex).getAppliedPose();
				a += pose.getA() * weight;
				b += pose.getB() * weight;
				c += pose.getC() * weight;
				d += pose.getD() * weight;
				valueCursor += 3;
			}
			out[0] = a;
			out[1] = b;
			out[2] = c;
			out[3] = d;
			return allFinite(a, b, c, d);
		}
		return false;
	}

	private static boolean isInvertibleTransform(float a, float b, float c, float d) {
		if (!allFinite(a, b, c, d)) return false;
		float determinant = a * d - b * c;
		return Float.isFinite(determinant) && Math.abs(determinant) > 0.000001f;
	}

	private static boolean allFinite(float... values) {
		for (float value : values) if (!Float.isFinite(value)) return false;
		return true;
	}

	private void restoreMeshGeometry(MeshAttachment mesh, SkeletonEditor.MeshGeometryState state) {
		if (!editor.updateMeshGeometry(mesh, state)
			&& !SkeletonEditor.meshGeometryState(mesh).sameContent(state))
			throw new IllegalStateException("无法恢复 Mesh 顶点：" + mesh.getName());
	}

	private void showStatus(String message, boolean error) {
		if (onStatus != null) onStatus.accept(message, error);
	}

	static Vector2 draggedVertexPosition(float vertexX, float vertexY, float startCursorX, float startCursorY,
		float currentCursorX, float currentCursorY, Vector2 out) {
		if (out == null) out = new Vector2();
		return out.set(vertexX + currentCursorX - startCursorX, vertexY + currentCursorY - startCursorY);
	}

	static Vector2 regionOriginWorld(RegionAttachment region, Bone bone, Vector2 out) {
		if (out == null) out = new Vector2();
		if (region == null || bone == null) return out.set(Float.NaN, Float.NaN);
		return bone.getAppliedPose().localToWorld(out.set(region.getX(), region.getY()));
	}

	static Vector2 snapRegionMoveDelta(float startWorldX, float startWorldY, float deltaWorldX,
		float deltaWorldY, float cameraX, float cameraY, float viewportWidth, float viewportHeight,
		float zoom, boolean flipped, TransformAxis axis, Vector2 out) {
		return snapRegionMoveDelta(startWorldX, startWorldY, deltaWorldX, deltaWorldY,
			cameraX, cameraY, viewportWidth, viewportHeight, zoom, flipped, axis,
			0f, 0f, 1f, 1f, out);
	}

	static Vector2 snapRegionMoveDelta(float startWorldX, float startWorldY, float deltaWorldX,
		float deltaWorldY, float cameraX, float cameraY, float viewportWidth, float viewportHeight,
		float zoom, boolean flipped, TransformAxis axis, float screenOriginX, float screenOriginY,
		float screenScaleX, float screenScaleY, Vector2 out) {
		return snapRegionMoveDelta(startWorldX, startWorldY, deltaWorldX, deltaWorldY,
			cameraX, cameraY, viewportWidth, viewportHeight, zoom, flipped, axis,
			axis == TransformAxis.Y ? 0f : 1f, axis == TransformAxis.Y ? 1f : 0f,
			screenOriginX, screenOriginY, screenScaleX, screenScaleY, out);
	}

	static Vector2 snapRegionMoveDelta(float startWorldX, float startWorldY, float deltaWorldX,
		float deltaWorldY, float cameraX, float cameraY, float viewportWidth, float viewportHeight,
		float zoom, boolean flipped, TransformAxis axis, float axisWorldX, float axisWorldY,
		float screenOriginX, float screenOriginY, float screenScaleX, float screenScaleY, Vector2 out) {
		if (out == null) out = new Vector2();
		out.set(deltaWorldX, deltaWorldY);
		if (!allFinite(startWorldX, startWorldY, deltaWorldX, deltaWorldY, cameraX, cameraY,
			viewportWidth, viewportHeight, zoom, screenOriginX, screenOriginY, screenScaleX, screenScaleY)
			|| viewportWidth <= 0f || viewportHeight <= 0f || zoom <= 0f
			|| screenScaleX <= 0f || screenScaleY <= 0f)
			return out;
		TransformAxis effectiveAxis = axis == null ? TransformAxis.FREE : axis;
		if (effectiveAxis == TransformAxis.X || effectiveAxis == TransformAxis.Y) {
			if (!allFinite(axisWorldX, axisWorldY) || (axisWorldX == 0f && axisWorldY == 0f)) return out;
			// An oblique line need not intersect a pixel corner. Snap its dominant screen component,
			// then travel along that same line so the locked local/parent component stays unchanged.
			boolean horizontal = Math.abs((double)axisWorldX * screenScaleX)
				>= Math.abs((double)axisWorldY * screenScaleY);
			float target = horizontal ? startWorldX + deltaWorldX : startWorldY + deltaWorldY;
			float snapped = snapWorldCoordinateToScreenPixel(target, horizontal ? cameraX : cameraY,
				horizontal ? viewportWidth : viewportHeight, zoom, horizontal && flipped,
				horizontal ? screenOriginX : screenOriginY, horizontal ? screenScaleX : screenScaleY);
			double travel = ((double)snapped - target) / (horizontal ? axisWorldX : axisWorldY);
			float nextX = (float)(deltaWorldX + travel * axisWorldX);
			float nextY = (float)(deltaWorldY + travel * axisWorldY);
			return allFinite(nextX, nextY) ? out.set(nextX, nextY) : out;
		}
		float targetX = startWorldX + deltaWorldX, targetY = startWorldY + deltaWorldY;
		out.x += snapWorldCoordinateToScreenPixel(targetX, cameraX, viewportWidth, zoom, flipped,
			screenOriginX, screenScaleX) - targetX;
		out.y += snapWorldCoordinateToScreenPixel(targetY, cameraY, viewportHeight, zoom, false,
			screenOriginY, screenScaleY) - targetY;
		return out;
	}

	static float snapWorldCoordinateToScreenPixel(float world, float cameraCoordinate,
		float viewportSize, float zoom, boolean flipped) {
		return snapWorldCoordinateToScreenPixel(world, cameraCoordinate, viewportSize, zoom,
			flipped, 0f, 1f);
	}

	static float snapWorldCoordinateToScreenPixel(float world, float cameraCoordinate,
		float viewportSize, float zoom, boolean flipped, float screenOrigin, float screenScale) {
		if (!allFinite(world, cameraCoordinate, viewportSize, zoom, screenOrigin, screenScale)
			|| viewportSize <= 0f || zoom <= 0f || screenScale <= 0f)
			return world;
		double direction = flipped ? -1d : 1d;
		double localScreen = viewportSize * 0.5d + direction * (world - cameraCoordinate) / zoom;
		double snappedLocal = (Math.rint(screenOrigin + localScreen * screenScale) - screenOrigin) / screenScale;
		double snapped = cameraCoordinate + direction * (snappedLocal - viewportSize * 0.5d) * zoom;
		return Double.isFinite(snapped) && snapped >= -Float.MAX_VALUE && snapped <= Float.MAX_VALUE
			? (float)snapped : world;
	}

	// ---- 工具拖拽 ----

	private void applyToolDrag(float x, float y) {
		Bone bone = dragBone;
		EditorTool tool = dragTool;
		if (!isViewportBoneSelectionActive(doc) || !isBoneDragTargetValid(doc.skeleton, bone, doc.getSelectedBone(), tool,
			doc.getSelectionKind())) {
			cancelBoneDrag();
			return;
		}
		Vector2 world = screenToWorld(x, y);
		float wx = world.x;
		float wy = world.y;
		if (boneSelectionDrag == null || !boneSelectionDrag.isCurrent()) {
			cancelBoneDrag();
			return;
		}
		float nextX = startX, nextY = startY, nextRotation = startRotation;
		float nextScaleX = startScaleX, nextScaleY = startScaleY;
		float nextShearX = startShearX, nextShearY = startShearY;
		float moveWorldX = 0f, moveWorldY = 0f;
		boolean hasCandidate = true;

		switch (tool) {
		case ROTATE: {
			// 骨骼链：旋转自身 + 子骨骼整体绕父旋转
			float angleNow = (float) Math.toDegrees(Math.atan2(wy - startBoneWorld.y, wx - startBoneWorld.x));
			float delta = normalizeAngle(angleNow - startAngle);
			nextRotation = startRotation + delta;
			break;
		}
		case MOVE: {
			Vector2 worldDrag = screenToWorld(startDrag.x, startDrag.y);
			float startWorldX = worldDrag.x;
			float startWorldY = worldDrag.y;
			float deltaWorldX = wx - startWorldX;
			float deltaWorldY = wy - startWorldY;
			if (dragTransformAxis != TransformAxis.FREE) {
				Vector2 constrained = constrainDeltaToAxis(deltaWorldX, deltaWorldY,
					dragAxisWorldX, dragAxisWorldY, tmp2);
				deltaWorldX = constrained.x;
				deltaWorldY = constrained.y;
			}
			if (snapRegionDrag) {
				Vector2 snapped = snapRegionMoveDelta(snapRegionStartWorldX, snapRegionStartWorldY,
					deltaWorldX, deltaWorldY, camera.position.x, camera.position.y,
					getWidth(), getHeight(), camera.zoom, viewportFlipped, dragTransformAxis,
					dragAxisWorldX, dragAxisWorldY,
					screenPixelOriginX, screenPixelOriginY, screenPixelScaleX, screenPixelScaleY, tmp2);
				deltaWorldX = snapped.x;
				deltaWorldY = snapped.y;
			}
			moveWorldX = deltaWorldX;
			moveWorldY = deltaWorldY;
			break;
		}
		case SCALE: {
			Vector2 startWorld = screenToWorld(startDrag.x, startDrag.y);
			if (dragTransformAxis == TransformAxis.FREE) {
				float startDist = startWorld.dst(startBoneWorld);
				float nowDist = (float)Math.hypot(wx - startBoneWorld.x, wy - startBoneWorld.y);
				if (startDist > camera.zoom && nowDist > camera.zoom) {
					float scale = nowDist / startDist;
					nextScaleX = startScaleX * scale;
					nextScaleY = startScaleY * scale;
				} else hasCandidate = false;
			} else {
				float scale = axisScaleFactor(startWorld.x - startBoneWorld.x,
					startWorld.y - startBoneWorld.y, wx - startBoneWorld.x, wy - startBoneWorld.y,
					dragAxisWorldX, dragAxisWorldY, camera.zoom);
				if (Float.isFinite(scale)) {
					if (dragTransformAxis == TransformAxis.X) nextScaleX = startScaleX * scale;
					else nextScaleY = startScaleY * scale;
				} else hasCandidate = false;
			}
			break;
		}
		case SHEAR: {
			float angleNow = (float)Math.toDegrees(Math.atan2(wy - startBoneWorld.y, wx - startBoneWorld.x));
			Vector2 shear = shearValuesForDrag(dragTransformAxis, startShearX, startShearY,
				startAngle, angleNow, tmp2);
			if (shear == null) {
				hasCandidate = false;
			} else {
				nextShearX = shear.x;
				nextShearY = shear.y;
			}
			break;
		}
		default:
			hasCandidate = false;
			break;
		}
		try {
			boolean changed = hasCandidate && (tool == EditorTool.MOVE
				? boneSelectionDrag.applyWorldDelta(moveWorldX, moveWorldY)
				: boneSelectionDrag.applyPrimaryPose(nextX, nextY, nextRotation, nextScaleX, nextScaleY, nextShearX, nextShearY));
			if (changed) {
				boneDragChanged = true;
				if (onPoseEdited != null) onPoseEdited.run();
			}
		} catch (IllegalArgumentException error) {
			cancelBoneDrag();
			showStatus("无法变换骨骼：" + error.getMessage(), true);
		}
	}

	static AnimationEditor.Prop autoKeyProp(EditorTool tool, TransformAxis axis) {
		if (tool == null || axis == null) return null;
		return switch (tool) {
		case ROTATE -> AnimationEditor.Prop.ROTATE;
		case MOVE -> axis == TransformAxis.X ? AnimationEditor.Prop.TRANSLATE_X
			: axis == TransformAxis.Y ? AnimationEditor.Prop.TRANSLATE_Y : AnimationEditor.Prop.TRANSLATE;
		case SCALE -> axis == TransformAxis.X ? AnimationEditor.Prop.SCALE_X
			: axis == TransformAxis.Y ? AnimationEditor.Prop.SCALE_Y : AnimationEditor.Prop.SCALE;
		case SHEAR -> axis == TransformAxis.X ? AnimationEditor.Prop.SHEAR_X
			: axis == TransformAxis.Y ? AnimationEditor.Prop.SHEAR_Y : AnimationEditor.Prop.SHEAR;
		default -> null;
		};
	}

	static AnimationEditor.Prop autoKeyProp(EditorTool tool, TransformAxis axis, BoneCoordinateSpace space, int selectionSize) {
		if (tool == EditorTool.MOVE && (space != BoneCoordinateSpace.PARENT || selectionSize > 1))
			return AnimationEditor.Prop.TRANSLATE;
		return autoKeyProp(tool, axis);
	}

	static Vector2 shearValuesForDrag(TransformAxis axis, float startShearX, float startShearY,
		float startPointerAngle, float currentPointerAngle, Vector2 out) {
		if (axis != TransformAxis.X && axis != TransformAxis.Y) return null;
		if (!allFinite(startShearX, startShearY, startPointerAngle, currentPointerAngle)) return null;
		float delta = normalizeAngle(currentPointerAngle - startPointerAngle);
		float shearX = axis == TransformAxis.X ? startShearX + delta : startShearX;
		float shearY = axis == TransformAxis.Y ? startShearY + delta : startShearY;
		if (!allFinite(shearX, shearY)) return null;
		if (out == null) out = new Vector2();
		return out.set(shearX, shearY);
	}

	private static float normalizeAngle(float angle) {
		while (angle > 180f) angle -= 360f;
		while (angle < -180f) angle += 360f;
		return angle;
	}

	private TransformAxis pickTransformAxis(float sx, float sy) {
		if (selectionHidden || !isViewportBoneSelectionActive(doc)) return TransformAxis.FREE;
		EditorTool tool = EditorAppState.tool;
		if (tool != EditorTool.MOVE && tool != EditorTool.ROTATE && tool != EditorTool.SCALE
			&& tool != EditorTool.SHEAR)
			return TransformAxis.FREE;
		if (!allowsBoneGizmo(tool, doc.getSelectionKind())) return TransformAxis.FREE;
		Bone bone = doc.getSelectedBone();
		if (!isViewportBoneTarget(doc.skeleton, bone)) return TransformAxis.FREE;
		Vector2 point = screenToWorld(sx, sy);
		var pose = bone.getAppliedPose();
		if (tool == EditorTool.ROTATE) {
			return rotationGizmoHit(point.x, point.y, pose.getWorldX(), pose.getWorldY(),
				ROTATION_GIZMO_RADIUS_PIXELS * camera.zoom,
				ROTATION_GIZMO_HIT_RADIUS_PIXELS * camera.zoom) ? TransformAxis.ROTATE : TransformAxis.FREE;
		}
		float xRotation = (float)Math.toRadians(transformAxisWorldRotation(tool, TransformAxis.X, bone));
		float yRotation = (float)Math.toRadians(transformAxisWorldRotation(tool, TransformAxis.Y, bone));
		return transformAxisHit(point.x, point.y, pose.getWorldX(), pose.getWorldY(),
			(float)Math.cos(xRotation), (float)Math.sin(xRotation),
			(float)Math.cos(yRotation), (float)Math.sin(yRotation),
			TRANSFORM_AXIS_LENGTH_PIXELS * camera.zoom,
			TRANSFORM_AXIS_HIT_RADIUS_PIXELS * camera.zoom,
			TRANSFORM_AXIS_CENTER_RADIUS_PIXELS * camera.zoom);
	}

	static TransformAxis transformAxisHit(float pointX, float pointY, float originX, float originY,
		float xAxisX, float xAxisY, float yAxisX, float yAxisY,
		float axisLength, float hitRadius, float centerRadius) {
		if (!allFinite(pointX, pointY, originX, originY, xAxisX, xAxisY, yAxisX, yAxisY,
			axisLength, hitRadius, centerRadius) || axisLength <= centerRadius || hitRadius < 0f
			|| centerRadius < 0f) return TransformAxis.FREE;
		if (Math.hypot(pointX - originX, pointY - originY) <= centerRadius) return TransformAxis.FREE;
		float xLength = (float)Math.hypot(xAxisX, xAxisY);
		float yLength = (float)Math.hypot(yAxisX, yAxisY);
		if (!(xLength > 0f) || !(yLength > 0f)) return TransformAxis.FREE;
		xAxisX /= xLength;
		xAxisY /= xLength;
		yAxisX /= yLength;
		yAxisY /= yLength;
		float xDistance = distanceToSegment(pointX, pointY,
			originX + xAxisX * centerRadius, originY + xAxisY * centerRadius,
			originX + xAxisX * axisLength, originY + xAxisY * axisLength);
		float yDistance = distanceToSegment(pointX, pointY,
			originX + yAxisX * centerRadius, originY + yAxisY * centerRadius,
			originX + yAxisX * axisLength, originY + yAxisY * axisLength);
		boolean onX = xDistance <= hitRadius;
		boolean onY = yDistance <= hitRadius;
		if (onX && onY) return xDistance <= yDistance ? TransformAxis.X : TransformAxis.Y;
		if (onX) return TransformAxis.X;
		return onY ? TransformAxis.Y : TransformAxis.FREE;
	}

	static boolean rotationGizmoHit(float pointX, float pointY, float originX, float originY,
		float radius, float hitRadius) {
		if (!allFinite(pointX, pointY, originX, originY, radius, hitRadius)
			|| radius <= 0f || hitRadius < 0f) return false;
		float distance = (float)Math.hypot(pointX - originX, pointY - originY);
		return Math.abs(distance - radius) <= hitRadius;
	}

	static boolean allowsBoneGizmo(EditorTool tool, SkeletonDocument.SelectionKind selectionKind) {
		if (tool == null || selectionKind == null
			|| selectionKind == SkeletonDocument.SelectionKind.CONSTRAINT) return false;
		return tool != EditorTool.SHEAR || selectionKind == SkeletonDocument.SelectionKind.BONE;
	}

	static boolean isBoneDragTargetValid(Skeleton skeleton, Bone dragBone, Bone selectedBone,
		EditorTool tool, SkeletonDocument.SelectionKind selectionKind) {
		if (skeleton == null || dragBone == null || dragBone != selectedBone || tool == null
			|| selectionKind == null || selectionKind == SkeletonDocument.SelectionKind.CONSTRAINT
			|| !skeleton.getBones().contains(dragBone, true) || !dragBone.isActive()) return false;
		return tool != EditorTool.SHEAR
			|| selectionKind == SkeletonDocument.SelectionKind.BONE && isViewportBoneVisible(dragBone);
	}


	static float transformAxisWorldRotation(EditorTool tool, TransformAxis axis,
		float localXRotation, float localYRotation) {
		if (axis != TransformAxis.X && axis != TransformAxis.Y)
			throw new IllegalArgumentException("A concrete transform axis is required");
		if (tool == EditorTool.MOVE) return axis == TransformAxis.X ? 0f : 90f;
		if (tool == EditorTool.SCALE || tool == EditorTool.SHEAR)
			return axis == TransformAxis.X ? localXRotation : localYRotation;
		throw new IllegalArgumentException("Move, scale, or shear tool is required");
	}

	private float transformAxisWorldRotation(EditorTool tool, TransformAxis axis, Bone bone) {
		if (tool == EditorTool.MOVE) return coordinateSpace.axisRotation(doc.skeleton, bone, axis == TransformAxis.Y);
		var pose = bone.getAppliedPose();
		return transformAxisWorldRotation(tool, axis, pose.getWorldRotationX(), pose.getWorldRotationY());
	}

	static Vector2 constrainDeltaToAxis(float deltaX, float deltaY, float axisX, float axisY, Vector2 out) {
		if (out == null) out = new Vector2();
		float length2 = axisX * axisX + axisY * axisY;
		if (!allFinite(deltaX, deltaY, axisX, axisY, length2) || !(length2 > 0f)) return out.setZero();
		float projection = (deltaX * axisX + deltaY * axisY) / length2;
		return out.set(axisX * projection, axisY * projection);
	}

	static float axisScaleFactor(float startX, float startY, float currentX, float currentY,
		float axisX, float axisY, float minimumStartDistance) {
		if (!allFinite(startX, startY, currentX, currentY, axisX, axisY, minimumStartDistance)
			|| minimumStartDistance < 0f) return Float.NaN;
		float axisLength = (float)Math.hypot(axisX, axisY);
		if (!(axisLength > 0f)) return Float.NaN;
		float startProjection = (startX * axisX + startY * axisY) / axisLength;
		if (Math.abs(startProjection) <= minimumStartDistance) return Float.NaN;
		float currentProjection = (currentX * axisX + currentY * axisY) / axisLength;
		float scale = currentProjection / startProjection;
		return Float.isFinite(scale) ? scale : Float.NaN;
	}

	// ---- 拾取 ----

	private AttachmentHit pickAttachment(float sx, float sy) {
		if (doc.skeleton == null) return null;
		Vector2 world = screenToWorld(sx, sy);
		float worldX = world.x, worldY = world.y;
		Array<Slot> drawOrder = doc.skeleton.getDrawOrder().getAppliedPose();
		for (int slotIndex = drawOrder.size - 1; slotIndex >= 0; slotIndex--) {
			Slot slot = drawOrder.get(slotIndex);
			Bone bone = slot.getBone();
			if (!slot.getData().getVisible() || !bone.isActive()) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (attachment instanceof RegionAttachment region) {
				float[] vertices = meshWorldTemp.setSize(8);
				float[] offsets = region.getOffsets(slot.getAppliedPose());
				if (offsets == null || offsets.length < 8) continue;
				region.computeWorldVertices(slot, offsets, vertices, 0, 2);
				if (pointInQuad(vertices, worldX, worldY)) return new AttachmentHit(slot, attachment);
			} else if (attachment instanceof MeshAttachment mesh) {
				float[] vertices = setupWorldVertices(mesh, slot);
				if (pointInTriangles(vertices, mesh.getTriangles(), worldX, worldY))
					return new AttachmentHit(slot, attachment);
			} else if (attachment instanceof PathAttachment path) {
				float[] vertices = vertexWorldVertices(path, slot);
				if (distanceToPath(vertices, path.getWorldVerticesLength(), path.getClosed(), worldX, worldY)
					<= 9f * camera.zoom) return new AttachmentHit(slot, attachment);
			} else if (attachment instanceof BoundingBoxAttachment box) {
				float[] vertices = vertexWorldVertices(box, slot);
				if (pointInPolygon(vertices, box.getWorldVerticesLength(), worldX, worldY))
					return new AttachmentHit(slot, attachment);
			} else if (attachment instanceof ClippingAttachment clipping) {
				float[] vertices = vertexWorldVertices(clipping, slot);
				if (pointInPolygon(vertices, clipping.getWorldVerticesLength(), worldX, worldY))
					return new AttachmentHit(slot, attachment);
			} else if (attachment instanceof PointAttachment point) {
				point.computeWorldPosition(slot.getBone().getAppliedPose(), tmp2);
				if (tmp2.dst2(worldX, worldY) <= 11f * 11f * camera.zoom * camera.zoom)
					return new AttachmentHit(slot, attachment);
			}
		}
		return null;
	}

	private Bone pickBone(float sx, float sy) {
		Vector2 world = screenToWorld(sx, sy);
		Bone best = null;
		float bestDist = BONE_HIT_RADIUS_PIXELS * camera.zoom;
		if (doc.skeleton == null) return null;
		Array<Bone> bones = doc.skeleton.getBones();
		for (Bone bone : bones) {
			if (!isViewportBoneVisible(bone) || !doc.isBoneSelectable(bone)) continue;
			float d = visibleBoneDistance(bone, world.x, world.y, camera.zoom);
			if (d < bestDist) {
				bestDist = d;
				best = bone;
			}
		}
		return best;
	}

	private MeshTriangleHit pickMeshTriangle(float sx, float sy) {
		if (doc.skeleton == null) return null;
		Vector2 point = screenToWorld(sx, sy);
		float worldX = point.x, worldY = point.y;
		Array<Slot> drawOrder = doc.skeleton.getDrawOrder().getAppliedPose();
		for (int slotIndex = drawOrder.size - 1; slotIndex >= 0; slotIndex--) {
			Slot slot = drawOrder.get(slotIndex);
			if (!slot.getData().getVisible() || !slot.getBone().isActive()) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (!(attachment instanceof MeshAttachment mesh)) continue;
			float[] vertices = setupWorldVertices(mesh, slot);
			short[] triangles = mesh.getTriangles();
			if (triangles == null) continue;
			for (int triangle = 0; triangle + 2 < triangles.length; triangle += 3) {
				int a = Short.toUnsignedInt(triangles[triangle]);
				int b = Short.toUnsignedInt(triangles[triangle + 1]);
				int c = Short.toUnsignedInt(triangles[triangle + 2]);
				if (a * 2 + 1 >= vertices.length || b * 2 + 1 >= vertices.length
					|| c * 2 + 1 >= vertices.length) continue;
				float[] weights = barycentricCoordinates(worldX, worldY,
					vertices[a * 2], vertices[a * 2 + 1], vertices[b * 2], vertices[b * 2 + 1],
					vertices[c * 2], vertices[c * 2 + 1]);
				if (weights != null) return new MeshTriangleHit(mesh, slot, triangle,
					weights[0], weights[1], weights[2]);
			}
		}
		return null;
	}

	private MeshVertexHit pickMeshVertex(float sx, float sy) {
		if (doc.skeleton == null) return null;
		Vector2 world = screenToWorld(sx, sy);
		float worldX = world.x, worldY = world.y;
		float maxDistance = 10f * camera.zoom;
		Array<Slot> drawOrder = doc.skeleton.getDrawOrder().getAppliedPose();
		for (int slotIndex = drawOrder.size - 1; slotIndex >= 0; slotIndex--) {
			Slot slot = drawOrder.get(slotIndex);
			if (!isEditableAttachmentSlot(slot)) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (!(attachment instanceof MeshAttachment mesh)) continue;
			float[] vertices = setupWorldVertices(mesh, slot);
			int vertex = closestVertexIndex(vertices, worldX, worldY, maxDistance);
			if (vertex >= 0) return new MeshVertexHit(mesh, slot, vertex);
		}
		return null;
	}

	private VertexAttachmentHit pickDeformVertex(float sx, float sy) {
		if (doc.skeleton == null) return null;
		Vector2 world = screenToWorld(sx, sy);
		float maxDistance = 10f * camera.zoom;
		Array<Slot> drawOrder = doc.skeleton.getDrawOrder().getAppliedPose();
		for (int slotIndex = drawOrder.size - 1; slotIndex >= 0; slotIndex--) {
			Slot slot = drawOrder.get(slotIndex);
			if (!slot.getData().getVisible() || !slot.getBone().isActive()) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (!isViewportDeformAttachment(attachment)) continue;
			VertexAttachment vertexAttachment = (VertexAttachment)attachment;
			float[] vertices = vertexWorldVertices(vertexAttachment, slot);
			int vertex = closestVertexIndex(vertices, world.x, world.y, maxDistance);
			if (vertex >= 0) return new VertexAttachmentHit(vertexAttachment, slot, vertex);
		}
		return null;
	}

	static boolean isViewportDeformAttachment(Attachment attachment) {
		return attachment instanceof VertexAttachment;
	}

	static boolean isEditableAttachmentSlot(Slot slot) {
		return slot != null && slot.getData().getVisible() && slot.getBone().isActive();
	}

	static boolean isViewportBoneVisible(Bone bone) {
		return bone != null && bone.isActive() && bone.getData().getVisible();
	}

	static Bone displayedBone(Bone bone, Skeleton inactiveReference) {
		if (bone == null || !bone.getData().getVisible()) return null;
		if (bone.isActive()) return bone;
		if (inactiveReference == null) return null;
		int index = bone.getData().getIndex();
		if (index < 0 || index >= inactiveReference.getBones().size) return null;
		Bone reference = inactiveReference.getBones().get(index);
		if (reference.getData() != bone.getData()) return null;
		var pose = reference.getAppliedPose();
		return allFinite(pose.getWorldX(), pose.getWorldY(), pose.getA(), pose.getB(), pose.getC(), pose.getD())
			? reference : null;
	}

	static boolean isViewportBoneTarget(Skeleton skeleton, Bone bone) {
		return skeleton != null && bone != null && skeleton.getBones().contains(bone, true)
			&& isViewportBoneVisible(bone);
	}

	static boolean isViewportBoneSelectionActive(SkeletonDocument document) {
		if (document == null || document.skeleton == null || document.getSelectedBone() == null
			|| !document.getSelectedBone().isActive()) return false;
		for (Bone bone : document.getSelectedBones())
			if (!bone.isActive() || !document.skeleton.getBones().contains(bone, true)) return false;
		return true;
	}

	private float[] setupWorldVertices(MeshAttachment mesh, Slot slot) {
		int count = mesh.getWorldVerticesLength();
		float[] world = meshWorldTemp.setSize(count);
		if (mesh.getBones() != null) {
			mesh.computeWorldVertices(doc.skeleton, slot, 0, count, world, 0, 2);
			return world;
		}
		FloatArray deform = slot.getAppliedPose().getDeform();
		float[] vertices = deform.size == count ? deform.items : mesh.getVertices();
		var bone = slot.getBone().getAppliedPose();
		for (int value = 0; value < count; value += 2) {
			float x = vertices[value], y = vertices[value + 1];
			world[value] = x * bone.getA() + y * bone.getB() + bone.getWorldX();
			world[value + 1] = x * bone.getC() + y * bone.getD() + bone.getWorldY();
		}
		return world;
	}

	private float[] vertexWorldVertices(VertexAttachment attachment, Slot slot) {
		int count = attachment.getWorldVerticesLength();
		float[] world = meshWorldTemp.setSize(count);
		attachment.computeWorldVertices(doc.skeleton, slot, 0, count, world, 0, 2);
		return world;
	}

	static int closestVertexIndex(float[] vertices, float x, float y, float maxDistance) {
		if (vertices == null || (vertices.length & 1) != 0 || !Float.isFinite(x) || !Float.isFinite(y)
			|| !Float.isFinite(maxDistance) || maxDistance < 0f) return -1;
		int best = -1;
		float bestDistance2 = maxDistance * maxDistance;
		for (int value = 0; value < vertices.length; value += 2) {
			float dx = vertices[value] - x, dy = vertices[value + 1] - y;
			float distance2 = dx * dx + dy * dy;
			if (distance2 <= bestDistance2) {
				bestDistance2 = distance2;
				best = value / 2;
			}
		}
		return best;
	}

	static boolean updateMeshVertexSelection(IntArray selection, int vertex, boolean toggle) {
		if (selection == null || vertex < 0) return false;
		int existing = selection.indexOf(vertex);
		if (toggle) {
			if (existing >= 0) {
				selection.removeIndex(existing);
				return false;
			}
			selection.add(vertex);
			return true;
		}
		if (existing >= 0) return true;
		selection.clear();
		selection.add(vertex);
		return true;
	}

	static boolean applyMeshVertexDelta(float[] vertices, int[] selectedVertices, float[] startPositions,
		float deltaX, float deltaY) {
		if (vertices == null || selectedVertices == null || startPositions == null
			|| selectedVertices.length == 0 || startPositions.length != selectedVertices.length * 2
			|| !Float.isFinite(deltaX) || !Float.isFinite(deltaY)) return false;
		for (int selection = 0; selection < selectedVertices.length; selection++) {
			int value = selectedVertices[selection] * 2;
			int start = selection * 2;
			if (value < 0 || value + 1 >= vertices.length
				|| !Float.isFinite(startPositions[start]) || !Float.isFinite(startPositions[start + 1])
				|| !Float.isFinite(startPositions[start] + deltaX)
				|| !Float.isFinite(startPositions[start + 1] + deltaY)) return false;
		}
		for (int selection = 0; selection < selectedVertices.length; selection++) {
			int value = selectedVertices[selection] * 2;
			int start = selection * 2;
			vertices[value] = startPositions[start] + deltaX;
			vertices[value + 1] = startPositions[start + 1] + deltaY;
		}
		return true;
	}

	static boolean pointInQuad(float[] vertices, float x, float y) {
		if (vertices == null || vertices.length < 8) return false;
		return pointInTriangle(x, y,
			vertices[0], vertices[1], vertices[2], vertices[3], vertices[4], vertices[5])
			|| pointInTriangle(x, y,
				vertices[0], vertices[1], vertices[4], vertices[5], vertices[6], vertices[7]);
	}

	static boolean pointInTriangles(float[] vertices, short[] triangles, float x, float y) {
		if (vertices == null || triangles == null || (vertices.length & 1) != 0) return false;
		for (int triangle = 0; triangle + 2 < triangles.length; triangle += 3) {
			int a = Short.toUnsignedInt(triangles[triangle]) * 2;
			int b = Short.toUnsignedInt(triangles[triangle + 1]) * 2;
			int c = Short.toUnsignedInt(triangles[triangle + 2]) * 2;
			if (a + 1 >= vertices.length || b + 1 >= vertices.length || c + 1 >= vertices.length) continue;
			if (pointInTriangle(x, y, vertices[a], vertices[a + 1], vertices[b], vertices[b + 1],
				vertices[c], vertices[c + 1])) return true;
		}
		return false;
	}

	static boolean pointInPolygon(float[] vertices, int valueCount, float x, float y) {
		if (vertices == null || valueCount < 6 || valueCount > vertices.length || (valueCount & 1) != 0
			|| !Float.isFinite(x) || !Float.isFinite(y)) return false;
		boolean inside = false;
		int previous = valueCount - 2;
		for (int value = 0; value < valueCount; value += 2) {
			float x1 = vertices[previous], y1 = vertices[previous + 1];
			float x2 = vertices[value], y2 = vertices[value + 1];
			if (distanceToSegment(x, y, x1, y1, x2, y2) <= 0.0001f) return true;
			if ((y1 > y) != (y2 > y) && x < (x2 - x1) * (y - y1) / (y2 - y1) + x1) inside = !inside;
			previous = value;
		}
		return inside;
	}

	static int pathCurveCount(int valueCount, boolean closed) {
		if (valueCount < 12 || (valueCount & 1) != 0) return 0;
		int vertices = valueCount / 2;
		if (vertices % 3 != 0) return 0;
		return Math.max(0, vertices / 3 - (closed ? 0 : 1));
	}

	static float distanceToPath(float[] vertices, int valueCount, boolean closed, float x, float y) {
		int curves = pathCurveCount(valueCount, closed);
		if (vertices == null || valueCount > vertices.length || curves == 0
			|| !Float.isFinite(x) || !Float.isFinite(y)) return Float.POSITIVE_INFINITY;
		int vertexCount = valueCount / 2;
		float best = Float.POSITIVE_INFINITY;
		for (int curve = 0; curve < curves; curve++) {
			int start = (1 + curve * 3) % vertexCount;
			int control1 = (start + 1) % vertexCount;
			int control2 = (start + 2) % vertexCount;
			int end = (start + 3) % vertexCount;
			float previousX = vertices[start * 2], previousY = vertices[start * 2 + 1];
			for (int sample = 1; sample <= 20; sample++) {
				float t = sample / 20f;
				float nextX = cubic(vertices[start * 2], vertices[control1 * 2],
					vertices[control2 * 2], vertices[end * 2], t);
				float nextY = cubic(vertices[start * 2 + 1], vertices[control1 * 2 + 1],
					vertices[control2 * 2 + 1], vertices[end * 2 + 1], t);
				best = Math.min(best, distanceToSegment(x, y, previousX, previousY, nextX, nextY));
				previousX = nextX;
				previousY = nextY;
			}
		}
		return best;
	}

	private static float cubic(float start, float control1, float control2, float end, float t) {
		float inverse = 1f - t;
		return inverse * inverse * inverse * start + 3f * inverse * inverse * t * control1
			+ 3f * inverse * t * t * control2 + t * t * t * end;
	}

	static int meshHullValueCount(int hullLength, int worldVerticesLength) {
		if (hullLength < 0 || worldVerticesLength < 0) return 0;
		return Math.min(hullLength, worldVerticesLength) & ~1;
	}

	static float[] barycentricCoordinates(float px, float py, float ax, float ay, float bx, float by,
		float cx, float cy) {
		if (!allFinite(px, py, ax, ay, bx, by, cx, cy)) return null;
		float denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
		if (!Float.isFinite(denominator) || Math.abs(denominator) <= 0.000001f) return null;
		float weightA = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denominator;
		float weightB = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denominator;
		float weightC = 1f - weightA - weightB;
		if (!allFinite(weightA, weightB, weightC) || weightA < -0.0001f || weightB < -0.0001f
			|| weightC < -0.0001f) return null;
		weightA = Math.max(0f, weightA);
		weightB = Math.max(0f, weightB);
		weightC = Math.max(0f, weightC);
		float sum = weightA + weightB + weightC;
		if (!(sum > 0f) || !Float.isFinite(sum)) return null;
		return new float[] {weightA / sum, weightB / sum, weightC / sum};
	}

	static int[] orientedTriangle(int a, int b, int c, float[] world, short[] existingTriangles) {
		if (world == null || existingTriangles == null || a < 0 || b < 0 || c < 0
			|| a == b || b == c || c == a || a * 2 + 1 >= world.length
			|| b * 2 + 1 >= world.length || c * 2 + 1 >= world.length) return null;
		float area = cross(world[a * 2], world[a * 2 + 1], world[b * 2], world[b * 2 + 1],
			world[c * 2], world[c * 2 + 1]);
		if (!Float.isFinite(area) || Math.abs(area) <= 0.000001f) return null;
		float referenceArea = 0f;
		for (int index = 0; index + 2 < existingTriangles.length; index += 3) {
			int x = Short.toUnsignedInt(existingTriangles[index]);
			int y = Short.toUnsignedInt(existingTriangles[index + 1]);
			int z = Short.toUnsignedInt(existingTriangles[index + 2]);
			if (x * 2 + 1 >= world.length || y * 2 + 1 >= world.length || z * 2 + 1 >= world.length) continue;
			referenceArea = cross(world[x * 2], world[x * 2 + 1], world[y * 2], world[y * 2 + 1],
				world[z * 2], world[z * 2 + 1]);
			if (Float.isFinite(referenceArea) && Math.abs(referenceArea) > 0.000001f) break;
			referenceArea = 0f;
		}
		return referenceArea != 0f && area * referenceArea < 0f ? new int[] {a, c, b} : new int[] {a, b, c};
	}

	static boolean meshVertexHasBoneInfluence(MeshAttachment mesh, int vertex, int boneIndex) {
		if (mesh == null || vertex < 0 || boneIndex < 0 || mesh.getBones() == null) return false;
		int[] bones = mesh.getBones();
		int boneCursor = 0;
		for (int logicalVertex = 0; logicalVertex <= vertex; logicalVertex++) {
			if (boneCursor >= bones.length) return false;
			int influenceCount = bones[boneCursor++];
			if (influenceCount <= 0 || boneCursor + influenceCount > bones.length) return false;
			if (logicalVertex == vertex) {
				for (int influence = 0; influence < influenceCount; influence++)
					if (bones[boneCursor + influence] == boneIndex) return true;
				return false;
			}
			boneCursor += influenceCount;
		}
		return false;
	}

	static int dashedSegmentCount(float edgeLength, float dashLength, float gapLength) {
		if (!Float.isFinite(edgeLength) || !Float.isFinite(dashLength) || !Float.isFinite(gapLength)
			|| edgeLength <= 0f || dashLength <= 0f || gapLength < 0f) return 0;
		float period = dashLength + gapLength;
		if (!Float.isFinite(period) || period <= 0f) return 0;
		return Math.max(1, (int)Math.ceil(edgeLength / period));
	}

	static boolean pointInTriangle(float px, float py, float ax, float ay, float bx, float by,
		float cx, float cy) {
		if (!Float.isFinite(px) || !Float.isFinite(py) || !Float.isFinite(ax) || !Float.isFinite(ay)
			|| !Float.isFinite(bx) || !Float.isFinite(by) || !Float.isFinite(cx) || !Float.isFinite(cy))
			return false;
		float area = cross(ax, ay, bx, by, cx, cy);
		if (!Float.isFinite(area) || Math.abs(area) <= 0.000001f) return false;
		float ab = cross(ax, ay, bx, by, px, py);
		float bc = cross(bx, by, cx, cy, px, py);
		float ca = cross(cx, cy, ax, ay, px, py);
		if (!Float.isFinite(ab) || !Float.isFinite(bc) || !Float.isFinite(ca)) return false;
		return area > 0f ? ab >= 0f && bc >= 0f && ca >= 0f : ab <= 0f && bc <= 0f && ca <= 0f;
	}

	private static float cross(float ax, float ay, float bx, float by, float px, float py) {
		return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
	}

	enum TransformAxis { FREE, X, Y, ROTATE }

	private record MeshVertexHit(MeshAttachment mesh, Slot slot, int vertex) {}
	private record VertexAttachmentHit(VertexAttachment attachment, Slot slot, int vertex) {}
	private record MeshTriangleHit(MeshAttachment mesh, Slot slot, int triangleOffset,
		float weightA, float weightB, float weightC) {}
	private record AttachmentHit(Slot slot, Attachment attachment) {}

	private static float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
		float dx = x2 - x1, dy = y2 - y1;
		float length2 = dx * dx + dy * dy;
		if (length2 <= 0.0001f) return (float)Math.hypot(px - x1, py - y1);
		float t = Math.max(0f, Math.min(1f, ((px - x1) * dx + (py - y1) * dy) / length2));
		return (float)Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
	}

	private Vector2 screenToWorld(float sx, float sy) {
		return tmp.set(screenToWorldCoordinate(sx, getWidth(), camera.zoom, camera.position.x,
			viewportFlipped), screenToWorldCoordinate(sy, getHeight(), camera.zoom, camera.position.y, false));
	}

	static float screenToWorldCoordinate(float screen, float viewportSize, float zoom,
		float cameraCoordinate, boolean flipped) {
		return cameraCoordinate + (flipped ? -1f : 1f) * (screen - viewportSize * 0.5f) * zoom;
	}

	static float panWorldDelta(float screenDelta, float zoom, boolean flipped) {
		return (flipped ? 1f : -1f) * screenDelta * zoom;
	}

	static boolean isHandledButton(int button) {
		return button == Input.Buttons.LEFT || button == Input.Buttons.MIDDLE || button == Input.Buttons.RIGHT;
	}

	static boolean shouldRetainAttachmentSelection(boolean attachmentHit, boolean boneHit,
		boolean selectedAttachmentAvailable) {
		return !attachmentHit && !boneHit && selectedAttachmentAvailable;
	}

	static boolean shouldRetainBoneSelection(boolean attachmentHit, boolean boneHit,
		boolean selectedBoneAvailable, SkeletonDocument.SelectionKind selectionKind) {
		return !attachmentHit && !boneHit && selectedBoneAvailable
			&& selectionKind == SkeletonDocument.SelectionKind.BONE;
	}

	static boolean isBlankAttachmentDoubleClick(boolean explicitBlankAttachment,
		long nowNanos, long lastClickNanos, float x, float y, float lastX, float lastY) {
		if (!explicitBlankAttachment || lastClickNanos <= 0L || nowNanos < lastClickNanos
			|| nowNanos - lastClickNanos > DOUBLE_CLICK_INTERVAL_NANOS) return false;
		return distanceSquared(x, y, lastX, lastY)
			<= DOUBLE_CLICK_RADIUS_PIXELS * DOUBLE_CLICK_RADIUS_PIXELS;
	}

	private void completeBlankAttachmentClick() {
		if (!blankAttachmentClickCandidate) return;
		lastBlankAttachmentClickNanos = System.nanoTime();
		lastBlankAttachmentClickX = blankAttachmentPressX;
		lastBlankAttachmentClickY = blankAttachmentPressY;
		blankAttachmentClickCandidate = false;
	}

	private void resetBlankAttachmentClick() {
		lastBlankAttachmentClickNanos = 0L;
		blankAttachmentClickCandidate = false;
	}

	private static float distanceSquared(float x, float y, float otherX, float otherY) {
		float dx = x - otherX;
		float dy = y - otherY;
		return dx * dx + dy * dy;
	}

	static boolean shouldStartBoneDrag(boolean attachmentHit, boolean boneHit,
		boolean retainedAttachment, boolean retainedBone, EditorTool tool) {
		return attachmentHit || boneHit || retainedAttachment || retainedBone
			? tool == EditorTool.MOVE || tool == EditorTool.ROTATE || tool == EditorTool.SCALE
			: false;
	}

	static TextureFilter previewMinFilter(TextureFilter current, boolean smoothing) {
		boolean mipmapped = current != null && current.isMipMap();
		if (smoothing) return mipmapped ? TextureFilter.MipMapLinearLinear : TextureFilter.Linear;
		return mipmapped ? TextureFilter.MipMapNearestNearest : TextureFilter.Nearest;
	}

	static void applyTextureFiltering(TextureAtlas atlas, boolean smoothing) {
		if (atlas == null) return;
		for (Texture texture : atlas.getTextures()) {
			if (texture == null) continue;
			texture.setFilter(previewMinFilter(texture.getMinFilter(), smoothing),
				smoothing ? TextureFilter.Linear : TextureFilter.Nearest);
		}
	}

	static Slot topmostVisibleAttachmentSlot(Skeleton skeleton, Bone bone) {
		if (skeleton == null || bone == null || !skeleton.getBones().contains(bone, true) || !bone.isActive()) return null;
		Array<Slot> drawOrder = skeleton.getDrawOrder().getAppliedPose();
		for (int index = drawOrder.size - 1; index >= 0; index--) {
			Slot slot = drawOrder.get(index);
			if (slot.getBone() == bone && slot.getData().getVisible()
				&& slot.getAppliedPose().getAttachment() != null) return slot;
		}
		return null;
	}

	static float visibleBoneDistance(float pointX, float pointY, float boneX, float boneY,
		float parentX, float parentY, boolean hasParent, float rotationX, float rotationY,
		float zoom) {
		if (!allFinite(pointX, pointY, boneX, boneY, rotationX, rotationY, zoom) || zoom <= 0f
			|| hasParent && !allFinite(parentX, parentY)) return Float.POSITIVE_INFINITY;
		float distance = hasParent
			? distanceToSegment(pointX, pointY, parentX, parentY, boneX, boneY)
			: (float)Math.hypot(pointX - boneX, pointY - boneY);
		float radiansX = (float)Math.toRadians(rotationX);
		float radiansY = (float)Math.toRadians(rotationY);
		distance = Math.min(distance, distanceToSegment(pointX, pointY, boneX, boneY,
			boneX + (float)Math.cos(radiansX) * BONE_X_AXIS_PIXELS * zoom,
			boneY + (float)Math.sin(radiansX) * BONE_X_AXIS_PIXELS * zoom));
		distance = Math.min(distance, distanceToSegment(pointX, pointY, boneX, boneY,
			boneX + (float)Math.cos(radiansY) * BONE_Y_AXIS_PIXELS * zoom,
			boneY + (float)Math.sin(radiansY) * BONE_Y_AXIS_PIXELS * zoom));
		return distance;
	}

	/** Match the actual parent line, local axes, bone length and optional diamond icon. */
	static float visibleBoneDistance(Bone bone, float pointX, float pointY, float zoom) {
		if (!isViewportBoneVisible(bone)) return Float.POSITIVE_INFINITY;
		var pose = bone.getAppliedPose();
		Bone parent = bone.getParent();
		boolean parentVisible = isViewportBoneVisible(parent);
		float x = pose.getWorldX(), y = pose.getWorldY();
		float distance = visibleBoneDistance(pointX, pointY, x, y,
			parentVisible ? parent.getAppliedPose().getWorldX() : 0f,
			parentVisible ? parent.getAppliedPose().getWorldY() : 0f, parentVisible,
			pose.getWorldRotationX(), pose.getWorldRotationY(), zoom);
		float length = bone.getData().getLength();
		float tipX = x + pose.getA() * length, tipY = y + pose.getC() * length;
		if (length > 0f && allFinite(length, tipX, tipY))
			distance = Math.min(distance, distanceToSegment(pointX, pointY, x, y, tipX, tipY));
		if (bone.getData().getIcon() != null && !bone.getData().getIcon().isBlank()) {
			float radius = Math.max(3f * zoom, 3f * zoom * Math.max(0.25f, bone.getData().getIconSize()));
			if (Float.isFinite(radius)) {
				distance = Math.min(distance, distanceToSegment(pointX, pointY, x - radius, y, x, y + radius));
				distance = Math.min(distance, distanceToSegment(pointX, pointY, x, y + radius, x + radius, y));
				distance = Math.min(distance, distanceToSegment(pointX, pointY, x + radius, y, x, y - radius));
				distance = Math.min(distance, distanceToSegment(pointX, pointY, x, y - radius, x - radius, y));
			}
		}
		return distance;
	}

	private static boolean isShiftPressed() {
		return Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
			|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
	}

	private static boolean isControlPressed() {
		return Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
			|| Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT));
	}

	static float scrollZoomFactor(float amountY) {
		if (!Float.isFinite(amountY) || amountY == 0f) return 1f;
		float steps = Math.max(-MAX_SCROLL_STEPS, Math.min(MAX_SCROLL_STEPS, amountY));
		return (float)Math.pow(1.12, steps);
	}

	static float anchoredCameraCoordinate(float cameraCoordinate, float screenOffset, float oldZoom, float newZoom) {
		return anchoredCameraCoordinate(cameraCoordinate, screenOffset, oldZoom, newZoom, false);
	}

	static float anchoredCameraCoordinate(float cameraCoordinate, float screenOffset,
		float oldZoom, float newZoom, boolean flipped) {
		return cameraCoordinate + (flipped ? -1f : 1f) * screenOffset * (oldZoom - newZoom);
	}

	static boolean isLocalPointInside(float width, float height, float x, float y) {
		return Float.isFinite(width) && Float.isFinite(height) && width > 0f && height > 0f
			&& Float.isFinite(x) && Float.isFinite(y) && x >= 0f && x < width && y >= 0f && y < height;
	}

	/** Routes a stage-level wheel event to this viewport when the pointer is inside its bounds. */
	public boolean zoomFromStageScroll(float stageX, float stageY, float amountY) {
		if (getStage() == null || !isVisible()) return false;
		tmp2.set(stageX, stageY);
		stageToLocalCoordinates(tmp2);
		return zoomFromLocalScroll(tmp2.x, tmp2.y, amountY);
	}

	private boolean zoomFromLocalScroll(float x, float y, float amountY) {
		if (!isLocalPointInside(getWidth(), getHeight(), x, y)) return false;
		pointerInside = true;
		pointerLocalX = x;
		pointerLocalY = y;
		float factor = scrollZoomFactor(amountY);
		if (factor == 1f) return false;
		if (!scrollChangesZoom(zoomLocked, factor)) return true;
		zoomAt(factor, x, y);
		return true;
	}

	private void zoomAt(float factor, float sx, float sy) {
		if (!Float.isFinite(factor) || factor <= 0f || !Float.isFinite(camera.zoom) || camera.zoom <= 0f) return;
		float oldZoom = camera.zoom;
		float newZoom = oldZoom * factor;
		newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
		float offsetX = sx - getWidth() / 2f;
		float offsetY = sy - getHeight() / 2f;
		camera.position.x = anchoredCameraCoordinate(camera.position.x, offsetX, oldZoom, newZoom,
			viewportFlipped);
		camera.position.y = anchoredCameraCoordinate(camera.position.y, offsetY, oldZoom, newZoom);
		camera.zoom = newZoom;
	}

	static boolean scrollChangesZoom(boolean locked, float factor) {
		return !locked && Float.isFinite(factor) && factor > 0f && factor != 1f;
	}

	public void frameAll() {
		if (getWidth() <= 1 || getHeight() <= 1) return;
		float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
		for (SkeletonDocument document : currentVisibleDocuments()) {
			Skeleton skeleton = document.skeleton;
			if (skeleton == null) continue;
			Skeleton reference = showInactiveSkinBones && document == doc ? inactiveSkinBones.prepare(skeleton) : null;
			for (Bone original : skeleton.getBones()) {
				Bone bone = displayedBone(original, reference);
				if (bone == null) continue;
				var pose = bone.getAppliedPose();
				float x = pose.getWorldX(), y = pose.getWorldY();
				if (!Float.isFinite(x) || !Float.isFinite(y)) continue;
				minX = Math.min(minX, x); maxX = Math.max(maxX, x);
				minY = Math.min(minY, y); maxY = Math.max(maxY, y);
				boneTipWorld(bone, tmp);
				float ex = tmp.x, ey = tmp.y;
				if (Float.isFinite(ex) && Float.isFinite(ey)) {
					minX = Math.min(minX, ex); maxX = Math.max(maxX, ex);
					minY = Math.min(minY, ey); maxY = Math.max(maxY, ey);
				}
			}
			hideInvisibleSlots(skeleton);
			try {
				skeleton.getBounds(boundsOffset, boundsSize, boundsTemp, boundsClipper);
				if (isUsableAttachmentBounds(boundsOffset, boundsSize)) {
					minX = Math.min(minX, boundsOffset.x);
					minY = Math.min(minY, boundsOffset.y);
					maxX = Math.max(maxX, boundsOffset.x + boundsSize.x);
					maxY = Math.max(maxY, boundsOffset.y + boundsSize.y);
				}
			} finally {
				restoreInvisibleSlots(skeleton);
			}
		}
		frameBounds(minX, minY, maxX, maxY);
	}

	static boolean isUsableAttachmentBounds(Vector2 offset, Vector2 size) {
		return offset != null && size != null && Float.isFinite(offset.x) && Float.isFinite(offset.y)
			&& Float.isFinite(size.x) && Float.isFinite(size.y) && size.x >= 0f && size.y >= 0f
			&& Float.isFinite(offset.x + size.x) && Float.isFinite(offset.y + size.y);
	}

	public void frameSelected() {
		Bone bone = displayedBone(doc.getSelectedBone(), showInactiveSkinBones ? inactiveSkinBones.prepare(doc.skeleton) : null);
		if (bone == null || getWidth() <= 1 || getHeight() <= 1) { frameAll(); return; }
		var pose = bone.getAppliedPose();
		float x = pose.getWorldX(), y = pose.getWorldY();
		float extent = Math.max(40f, Math.abs(bone.getData().getLength()) * pose.getWorldScaleX() * 1.5f);
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(extent)) { frameAll(); return; }
		frameBounds(x - extent, y - extent, x + extent, y + extent);
	}

	private void frameBounds(float minX, float minY, float maxX, float maxY) {
		if (!isUsableViewBounds(minX, minY, maxX, maxY)) return;
		camera.position.set(minX * 0.5f + maxX * 0.5f, minY * 0.5f + maxY * 0.5f, 0f);
		float width = Math.max(20f, maxX - minX);
		float height = Math.max(20f, maxY - minY);
		camera.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM,
			Math.max(width / getWidth(), height / getHeight()) * 1.25f));
	}

	static boolean isUsableViewBounds(float minX, float minY, float maxX, float maxY) {
		return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(maxX) && Float.isFinite(maxY)
			&& minX <= maxX && minY <= maxY;
	}

	// ---- 渲染 ----

	@Override
	public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
		if (getWidth() <= 0 || getHeight() <= 0) return;
		batch.end();
		viewportOrigin.set(0f, 0f);
		localToScreenCoordinates(viewportOrigin);
		float scaleX = Gdx.graphics.getBackBufferWidth() / (float)Gdx.graphics.getWidth();
		float scaleY = Gdx.graphics.getBackBufferHeight() / (float)Gdx.graphics.getHeight();
		int vx = Math.max(0, Math.round(viewportOrigin.x * scaleX));
		int vy = Math.max(0, Math.round((Gdx.graphics.getHeight() - viewportOrigin.y) * scaleY));
		int vw = Math.max(1, Math.round(getWidth() * scaleX));
		int vh = Math.max(1, Math.round(getHeight() * scaleY));
		screenPixelOriginX = vx;
		screenPixelOriginY = vy;
		screenPixelScaleX = vw / getWidth();
		screenPixelScaleY = vh / getHeight();
		Gdx.gl.glViewport(vx, vy, vw, vh);
		// 相机保持与视口大小同步
		camera.viewportWidth = getWidth();
		camera.viewportHeight = getHeight();
		camera.update();
		applyViewportFlip(camera.combined, unflippedProjection, viewportFlipped);

		drawViewportBackground();
		if (showGrid) drawGrid();
		drawSkeletons();
		if (showPixelGrid) drawPixelGrid();
		drawGhostBones();
		drawStandardAttachmentOverlays();
		drawAttachmentOutlines();
		drawMeshOverlay();
		if (showBones) drawBones();
		drawGizmo();
		camera.combined.set(unflippedProjection);
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
		batch.begin();
	}

	static void applyViewportFlip(Matrix4 projection, Matrix4 backup, boolean flipped) {
		if (projection == null || backup == null || projection == backup)
			throw new IllegalArgumentException("Viewport projection matrices are required.");
		backup.set(projection);
		if (!flipped) return;
		float[] values = projection.val;
		values[Matrix4.M00] = -values[Matrix4.M00];
		values[Matrix4.M01] = -values[Matrix4.M01];
		values[Matrix4.M02] = -values[Matrix4.M02];
		values[Matrix4.M03] = -values[Matrix4.M03];
	}

	static float displayWorldX(float worldX, float cameraX, boolean flipped) {
		return flipped ? cameraX * 2f - worldX : worldX;
	}

	private void drawViewportBackground() {
		int width = framebufferWidth(), height = framebufferHeight();
		shapes.setProjectionMatrix(gridProjection.setToOrtho2D(0f, 0f, width, height));
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		shapes.setColor(VIEWPORT_BACKGROUND);
		shapes.rect(0f, 0f, width, height);
		shapes.end();
	}

	private void drawGrid() {
		ViewportGrid.Plan plan = ViewportGrid.plan(camera.position.x, camera.position.y, camera.zoom,
			getWidth(), getHeight(), framebufferWidth(), framebufferHeight(),
			ViewportGrid.backgroundStep(camera.zoom), viewportFlipped, 0f);
		drawGridPlan(plan, false);
	}

	private ViewportGrid.Plan pixelGridPlan() {
		return ViewportGrid.plan(camera.position.x, camera.position.y, camera.zoom,
			getWidth(), getHeight(), framebufferWidth(), framebufferHeight(), 1d,
			viewportFlipped, ViewportGrid.PIXEL_GRID_MIN_SPACING);
	}

	private int framebufferWidth() {
		return Math.max(1, Math.round(getWidth() * Gdx.graphics.getBackBufferWidth() / Gdx.graphics.getWidth()));
	}

	private int framebufferHeight() {
		return Math.max(1, Math.round(getHeight() * Gdx.graphics.getBackBufferHeight() / Gdx.graphics.getHeight()));
	}

	private void drawPixelGrid() {
		ViewportGrid.Plan plan = pixelGridPlan();
		if (!plan.visible()) return;
		boolean blending = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
			GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
		try { drawGridPlan(plan, true); }
		finally { if (!blending) Gdx.gl.glDisable(GL20.GL_BLEND); }
	}

	private void drawGridPlan(ViewportGrid.Plan plan, boolean pixels) {
		int width = framebufferWidth(), height = framebufferHeight();
		shapes.setProjectionMatrix(gridProjection.setToOrtho2D(0f, 0f, width, height));
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		try {
			for (int index = 0; index < plan.x().count(); index++) {
				shapes.setColor(pixels ? PIXEL_GRID : plan.x().isOrigin(index) ? VIEWPORT_AXIS : GRID);
				shapes.rect((float)Math.floor(plan.x().pixelPosition(index)), 0f, 1f, height);
			}
			for (int index = 0; index < plan.y().count(); index++) {
				shapes.setColor(pixels ? PIXEL_GRID : plan.y().isOrigin(index) ? VIEWPORT_AXIS : GRID);
				shapes.rect(0f, (float)Math.floor(plan.y().pixelPosition(index)), width, 1f);
			}
		} finally { shapes.end(); }
	}

	private void drawSkeletons() {
		List<SkeletonDocument> documents = currentVisibleDocuments();
		if (documents.isEmpty()) return;
		batch.setProjectionMatrix(camera.combined);
		batch.begin();
		try {
			for (SkeletonDocument document : documents) {
				if (document == doc || document.skeleton == null) continue;
				configureSkeletonRenderer(skeletonRenderer, document.premultipliedAlpha);
				drawRenderableSkeleton(document.skeleton);
			}
			if (doc.skeleton == null) return;
			configureSkeletonRenderer(skeletonRenderer, doc.premultipliedAlpha);
			drawGhosts();
			drawRenderableSkeleton(doc.skeleton);
		} finally {
			batch.end();
		}
	}

	private List<SkeletonDocument> currentVisibleDocuments() {
		List<SkeletonDocument> documents = visibleDocuments == null ? null : visibleDocuments.get();
		return documents == null || documents.isEmpty() ? List.of(doc) : documents;
	}

	static void configureSkeletonRenderer(SkeletonRenderer renderer, boolean premultipliedAlpha) {
		if (renderer == null) throw new IllegalArgumentException("Skeleton renderer is required.");
		renderer.setPremultipliedAlpha(premultipliedAlpha);
	}

	private void drawGhosts() {
		previousGhostPrepared = false;
		nextGhostPrepared = false;
		if (!showGhosting || doc.poseMode || doc.data == null || doc.animationState == null) return;
		AnimationState.TrackEntry track = doc.animationState.getTrack(0);
		if (track == null || track.getAnimation() == null) return;
		float animationStart = track.getAnimationStart();
		float duration = track.getAnimationEnd() - animationStart;
		float fps = doc.data.getFps();
		float[] samples = ghostSampleTimes(track.getAnimationTime() - animationStart, duration, fps, doc.looping);
		if (!Float.isFinite(samples[0]) && !Float.isFinite(samples[1])) return;

		ensureGhostSkeletons();
		Animation animation = track.getAnimation();
		if (Float.isFinite(samples[0])) {
			prepareGhostSkeleton(previousGhostSkeleton, doc.skeleton, animation,
				animationStart + samples[0], PREVIOUS_GHOST_COLOR, doc.areConstraintsEnabled());
			previousGhostPrepared = true;
			drawRenderableSkeleton(previousGhostSkeleton);
		}
		if (Float.isFinite(samples[1])) {
			prepareGhostSkeleton(nextGhostSkeleton, doc.skeleton, animation,
				animationStart + samples[1], NEXT_GHOST_COLOR, doc.areConstraintsEnabled());
			nextGhostPrepared = true;
			drawRenderableSkeleton(nextGhostSkeleton);
		}
	}

	private void ensureGhostSkeletons() {
		if (ghostSourceSkeleton == doc.skeleton && previousGhostSkeleton != null && nextGhostSkeleton != null) return;
		ghostSourceSkeleton = doc.skeleton;
		previousGhostSkeleton = new Skeleton(doc.data);
		nextGhostSkeleton = new Skeleton(doc.data);
	}

	static void prepareGhostSkeleton(Skeleton ghost, Skeleton source, Animation animation, float time, Color color) {
		prepareGhostSkeleton(ghost, source, animation, time, color, true);
	}

	static void prepareGhostSkeleton(Skeleton ghost, Skeleton source, Animation animation, float time,
		Color color, boolean constraintsEnabled) {
		if (ghost.getSkin() != source.getSkin()) ghost.setSkin(source.getSkin());
		ghost.setPosition(source.getX(), source.getY());
		ghost.setScale(source.getScaleX(), source.getScaleY());
		ghost.setTime(time);
		ghost.setupPose();
		animation.apply(ghost, time, time, false, null, 1f, Animation.MixFrom.setup,
			false, false, false);
		SkeletonDocument.updateWorldTransform(ghost, Physics.pose, constraintsEnabled);
		ghost.setColor(color);
	}

	private void drawRenderableSkeleton(Skeleton skeleton) {
		hideInvisibleSlots(skeleton);
		try {
			skeletonRenderer.draw(batch, skeleton);
		} finally {
			restoreInvisibleSlots(skeleton);
		}
	}

	static float[] ghostSampleTimes(float currentTime, float duration, float fps, boolean loop) {
		float[] samples = {Float.NaN, Float.NaN};
		if (!Float.isFinite(currentTime) || !Float.isFinite(duration) || duration <= 0f
			|| !Float.isFinite(fps) || fps <= 0f) return samples;
		float step = 1f / fps;
		if (!Float.isFinite(step) || step <= 0f) return samples;

		float time = Math.max(0f, Math.min(duration, currentTime));
		if (!loop) {
			if (time >= step) samples[0] = time - step;
			if (time <= duration - step) samples[1] = time + step;
			return samples;
		}

		float canonicalTime = wrapGhostTime(time, duration);
		float previous = wrapGhostTime(time - step, duration);
		float next = wrapGhostTime(time + step, duration);
		if (!sameLoopTime(previous, canonicalTime, duration)) samples[0] = previous;
		if (!sameLoopTime(next, canonicalTime, duration)
			&& !sameLoopTime(next, samples[0], duration)) samples[1] = next;
		return samples;
	}

	private static float wrapGhostTime(float time, float duration) {
		float wrapped = time % duration;
		return wrapped < 0f ? wrapped + duration : wrapped;
	}

	private static boolean sameLoopTime(float first, float second, float duration) {
		if (!Float.isFinite(first) || !Float.isFinite(second)) return false;
		float difference = Math.abs(first - second);
		float tolerance = Math.max(0.000001f, duration * 0.000001f);
		return difference <= tolerance || duration - difference <= tolerance;
	}

	private void clearGhostSkeletons() {
		ghostSourceSkeleton = null;
		previousGhostSkeleton = null;
		nextGhostSkeleton = null;
		previousGhostPrepared = false;
		nextGhostPrepared = false;
	}

	private void drawGhostBones() {
		if (!showGhosting || !showGhostBones) return;
		shapes.setProjectionMatrix(camera.combined);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		if (previousGhostPrepared) drawGhostBoneLines(previousGhostSkeleton, PREVIOUS_GHOST_COLOR);
		if (nextGhostPrepared) drawGhostBoneLines(nextGhostSkeleton, NEXT_GHOST_COLOR);
		shapes.end();
	}

	private void drawGhostBoneLines(Skeleton skeleton, Color color) {
		shapes.setColor(color);
		for (Bone bone : skeleton.getBones()) {
			if (!isViewportBoneVisible(bone)) continue;
			var pose = bone.getAppliedPose();
			float worldX = pose.getWorldX();
			float worldY = pose.getWorldY();
			Bone parent = bone.getParent();
			if (isViewportBoneVisible(parent))
				shapes.line(parent.getAppliedPose().getWorldX(), parent.getAppliedPose().getWorldY(), worldX, worldY);
			float length = bone.getData().getLength();
			if (!Float.isFinite(length) || length == 0f) continue;
			boneTipWorld(bone, tmp);
			shapes.line(worldX, worldY, tmp.x, tmp.y);
		}
	}

	static Vector2 boneTipWorld(Bone bone, Vector2 out) {
		if (out == null) out = new Vector2();
		if (bone == null) return out.set(0f, 0f);
		var pose = bone.getAppliedPose();
		float length = bone.getData().getLength();
		return out.set(pose.getWorldX() + pose.getA() * length,
			pose.getWorldY() + pose.getC() * length);
	}

	private void drawStandardAttachmentOverlays() {
		if (doc.skeleton == null) return;
		Array<Slot> drawOrder = doc.skeleton.getDrawOrder().getAppliedPose();
		shapes.setProjectionMatrix(camera.combined);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		for (Slot slot : drawOrder) {
			if (!slot.getData().getVisible() || !slot.getBone().isActive()) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (attachment instanceof PathAttachment path) {
				shapes.setColor(path.getColor());
				drawPath(vertexWorldVertices(path, slot), path.getWorldVerticesLength(), path.getClosed());
			} else if (attachment instanceof BoundingBoxAttachment box) {
				shapes.setColor(box.getColor());
				drawPolygon(vertexWorldVertices(box, slot), box.getWorldVerticesLength());
			} else if (attachment instanceof ClippingAttachment clipping) {
				shapes.setColor(clipping.getColor());
				drawPolygon(vertexWorldVertices(clipping, slot), clipping.getWorldVerticesLength());
			} else if (attachment instanceof PointAttachment point) {
				shapes.setColor(point.getColor());
				drawPointAttachment(point, slot);
			}
		}
		shapes.end();
	}

	private void drawPolygon(float[] vertices, int valueCount) {
		if (vertices == null || valueCount < 4 || valueCount > vertices.length || (valueCount & 1) != 0) return;
		for (int value = 0; value < valueCount; value += 2) {
			int next = value + 2 < valueCount ? value + 2 : 0;
			shapes.line(vertices[value], vertices[value + 1], vertices[next], vertices[next + 1]);
		}
	}

	private void drawPath(float[] vertices, int valueCount, boolean closed) {
		int curves = pathCurveCount(valueCount, closed);
		if (vertices == null || valueCount > vertices.length || curves == 0) return;
		int vertexCount = valueCount / 2;
		for (int curve = 0; curve < curves; curve++) {
			int start = (1 + curve * 3) % vertexCount;
			int control1 = (start + 1) % vertexCount;
			int control2 = (start + 2) % vertexCount;
			int end = (start + 3) % vertexCount;
			float previousX = vertices[start * 2], previousY = vertices[start * 2 + 1];
			for (int sample = 1; sample <= 20; sample++) {
				float t = sample / 20f;
				float nextX = cubic(vertices[start * 2], vertices[control1 * 2],
					vertices[control2 * 2], vertices[end * 2], t);
				float nextY = cubic(vertices[start * 2 + 1], vertices[control1 * 2 + 1],
					vertices[control2 * 2 + 1], vertices[end * 2 + 1], t);
				shapes.line(previousX, previousY, nextX, nextY);
				previousX = nextX;
				previousY = nextY;
			}
		}
	}

	private void drawPointAttachment(PointAttachment point, Slot slot) {
		point.computeWorldPosition(slot.getBone().getAppliedPose(), tmp2);
		float radius = 6f * camera.zoom;
		float length = 18f * camera.zoom;
		float radians = (float)Math.toRadians(point.computeWorldRotation(slot.getBone().getAppliedPose()));
		shapes.circle(tmp2.x, tmp2.y, radius, 20);
		shapes.line(tmp2.x, tmp2.y, tmp2.x + (float)Math.cos(radians) * length,
			tmp2.y + (float)Math.sin(radians) * length);
	}

	private void drawAttachmentOutlines() {
		if (selectionHidden) return;
		selectedAttachment = resolveAttachmentSelection();
		if (!isCurrentAttachmentHit(selectedAttachment)) selectedAttachment = null;
		hoveredAttachment = pointerInside ? pickAttachment(pointerLocalX, pointerLocalY) : null;
		if (selectedAttachment == null && hoveredAttachment == null) return;

		shapes.setProjectionMatrix(camera.combined);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		if (selectedAttachment != null) {
			shapes.setColor(ATTACHMENT_SELECTED);
			drawAttachmentOutline(selectedAttachment, 8f * camera.zoom, 5f * camera.zoom);
		}
		if (hoveredAttachment != null && !sameAttachmentHit(hoveredAttachment, selectedAttachment)) {
			shapes.setColor(ATTACHMENT_HOVER);
			drawAttachmentOutline(hoveredAttachment, 6f * camera.zoom, 4f * camera.zoom);
		}
		shapes.end();
	}

	private void drawAttachmentOutline(AttachmentHit hit, float dashLength, float gapLength) {
		Slot slot = hit.slot();
		Attachment attachment = hit.attachment();
		if (attachment instanceof RegionAttachment region) {
			float[] vertices = meshWorldTemp.setSize(8);
			float[] offsets = region.getOffsets(slot.getAppliedPose());
			if (offsets == null || offsets.length < 8) return;
			region.computeWorldVertices(slot, offsets, vertices, 0, 2);
			drawDashedPolygon(vertices, 8, dashLength, gapLength);
		} else if (attachment instanceof MeshAttachment mesh) {
			float[] vertices = setupWorldVertices(mesh, slot);
			drawDashedPolygon(vertices, meshHullValueCount(mesh.getHullLength(), vertices.length),
				dashLength, gapLength);
		} else if (attachment instanceof PathAttachment path) {
			float[] vertices = vertexWorldVertices(path, slot);
			drawDashedPolyline(vertices, path.getWorldVerticesLength(), path.getClosed(), dashLength, gapLength);
		} else if (attachment instanceof BoundingBoxAttachment box) {
			drawDashedPolygon(vertexWorldVertices(box, slot), box.getWorldVerticesLength(), dashLength, gapLength);
		} else if (attachment instanceof ClippingAttachment clipping) {
			drawDashedPolygon(vertexWorldVertices(clipping, slot), clipping.getWorldVerticesLength(),
				dashLength, gapLength);
		} else if (attachment instanceof PointAttachment point) {
			point.computeWorldPosition(slot.getBone().getAppliedPose(), tmp2);
			float radius = 9f * camera.zoom;
			float[] vertices = {tmp2.x - radius, tmp2.y - radius, tmp2.x - radius, tmp2.y + radius,
				tmp2.x + radius, tmp2.y + radius, tmp2.x + radius, tmp2.y - radius};
			drawDashedPolygon(vertices, vertices.length, dashLength, gapLength);
		}
	}

	private void drawDashedPolyline(float[] vertices, int valueCount, boolean closed,
		float dashLength, float gapLength) {
		if (vertices == null || valueCount < 4 || valueCount > vertices.length || (valueCount & 1) != 0) return;
		for (int value = 0; value + 3 < valueCount; value += 2)
			drawDashedLine(vertices[value], vertices[value + 1], vertices[value + 2], vertices[value + 3],
				dashLength, gapLength);
		if (closed) drawDashedLine(vertices[valueCount - 2], vertices[valueCount - 1], vertices[0], vertices[1],
			dashLength, gapLength);
	}

	private void drawDashedPolygon(float[] vertices, int valueCount, float dashLength, float gapLength) {
		if (vertices == null || valueCount < 4 || valueCount > vertices.length || (valueCount & 1) != 0) return;
		for (int value = 0; value < valueCount; value += 2) {
			int next = value + 2 < valueCount ? value + 2 : 0;
			drawDashedLine(vertices[value], vertices[value + 1], vertices[next], vertices[next + 1],
				dashLength, gapLength);
		}
	}

	private void drawDashedLine(float x1, float y1, float x2, float y2, float dashLength, float gapLength) {
		float dx = x2 - x1, dy = y2 - y1;
		float length = (float)Math.hypot(dx, dy);
		int segmentCount = dashedSegmentCount(length, dashLength, gapLength);
		if (segmentCount == 0) return;
		float ux = dx / length, uy = dy / length;
		float period = dashLength + gapLength;
		for (int segment = 0; segment < segmentCount; segment++) {
			float start = segment * period;
			float end = Math.min(start + dashLength, length);
			if (start >= length) break;
			shapes.line(x1 + ux * start, y1 + uy * start, x1 + ux * end, y1 + uy * end);
		}
	}

	private boolean isCurrentAttachmentHit(AttachmentHit hit) {
		if (hit == null || doc.skeleton == null) return false;
		Slot slot = hit.slot();
		return doc.skeleton.getSlots().contains(slot, true) && slot.getData().getVisible()
			&& slot.getBone().isActive() && slot.getAppliedPose().getAttachment() == hit.attachment();
	}

	private void setAttachmentSelection(AttachmentHit hit) {
		if (hit == null) {
			clearAttachmentSelection();
			return;
		}
		resetBlankAttachmentClick();
		selectedAttachment = hit;
		selectedAttachmentSlotData = hit.slot().getData();
		selectedAttachmentIdentity = hit.attachment();
		selectedAttachmentPlaceholder = attachmentPlaceholder(selectedAttachmentSlotData.getIndex(),
			hit.attachment());
	}

	private void clearAttachmentSelection() {
		resetBlankAttachmentClick();
		selectedAttachment = null;
		selectedAttachmentSlotData = null;
		selectedAttachmentPlaceholder = null;
		selectedAttachmentIdentity = null;
	}

	private AttachmentHit resolveAttachmentSelection() {
		Slot slot = resolveAttachmentSelection(doc.skeleton, selectedAttachmentSlotData,
			selectedAttachmentIdentity, selectedAttachmentPlaceholder);
		if (slot == null) return null;
		Attachment attachment = slot.getAppliedPose().getAttachment();
		selectedAttachmentIdentity = attachment;
		return new AttachmentHit(slot, attachment);
	}

	static Slot resolveAttachmentSelection(Skeleton skeleton, SlotData slotData, Attachment identity,
		String placeholder) {
		if (skeleton == null || slotData == null || identity == null) return null;
		int slotIndex = slotData.getIndex();
		if (slotIndex < 0 || slotIndex >= skeleton.getSlots().size) return null;
		Slot slot = skeleton.getSlots().get(slotIndex);
		if (slot.getData() != slotData) return null;
		Attachment displayed = slot.getAppliedPose().getAttachment();
		if (displayed == identity) return slot;
		return placeholder != null && displayed != null
			&& skeleton.getAttachment(slotIndex, placeholder) == displayed ? slot : null;
	}

	private String attachmentPlaceholder(int slotIndex, Attachment attachment) {
		if (doc.skeleton == null || doc.data == null || attachment == null) return null;
		String placeholder = attachmentPlaceholder(doc.skeleton.getSkin(), slotIndex, attachment);
		if (placeholder != null) return placeholder;
		Skin defaultSkin = doc.data.getDefaultSkin();
		return defaultSkin == doc.skeleton.getSkin() ? null
			: attachmentPlaceholder(defaultSkin, slotIndex, attachment);
	}

	private static String attachmentPlaceholder(Skin skin, int slotIndex, Attachment attachment) {
		if (skin == null) return null;
		for (Skin.SkinEntry entry : skin.getAttachments())
			if (entry.getSlotIndex() == slotIndex && entry.getAttachment() == attachment)
				return entry.getPlaceholder();
		return null;
	}

	private static boolean sameAttachmentHit(AttachmentHit first, AttachmentHit second) {
		return first != null && second != null && first.slot() == second.slot()
			&& first.attachment() == second.attachment();
	}

	private void hideInvisibleSlots(Skeleton skeleton) {
		previewHiddenSlots.clear();
		previewHiddenAttachments.clear();
		suppressInvisibleAttachments(skeleton, previewHiddenSlots, previewHiddenAttachments);
	}

	private void restoreInvisibleSlots(Skeleton skeleton) {
		restoreSuppressedAttachments(previewHiddenSlots, previewHiddenAttachments);
		previewHiddenSlots.clear();
		previewHiddenAttachments.clear();
	}

	static void suppressInvisibleAttachments(Skeleton skeleton, Array<Slot> hiddenSlots,
		Array<Attachment> hiddenAttachments) {
		if (skeleton == null || hiddenSlots == null || hiddenAttachments == null)
			throw new IllegalArgumentException("Skeleton visibility state is required.");
		for (Slot slot : skeleton.getDrawOrder().getAppliedPose()) {
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (slot.getData().getVisible() || attachment == null) continue;
			hiddenSlots.add(slot);
			hiddenAttachments.add(attachment);
			slot.getAppliedPose().setAttachment(null);
		}
	}

	static void restoreSuppressedAttachments(Array<Slot> hiddenSlots, Array<Attachment> hiddenAttachments) {
		if (hiddenSlots == null || hiddenAttachments == null || hiddenSlots.size != hiddenAttachments.size)
			throw new IllegalArgumentException("Suppressed attachment state is invalid.");
		for (int index = 0; index < hiddenSlots.size; index++)
			hiddenSlots.get(index).getAppliedPose().setAttachment(hiddenAttachments.get(index));
	}

	private void drawBones() {
		if (doc.skeleton == null) return;
		Skeleton reference = showInactiveSkinBones ? inactiveSkinBones.prepare(doc.skeleton) : null;
		shapes.setProjectionMatrix(camera.combined);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		Array<Bone> bones = doc.skeleton.getBones();
		float r = 3f * camera.zoom;
		for (Bone bone : bones) {
			Bone display = displayedBone(bone, reference);
			if (display == null) continue;
			Bone parent = displayedBone(bone.getParent(), reference);
			var pose = display.getAppliedPose();
			float wx = pose.getWorldX();
			float wy = pose.getWorldY();
			boolean selected = !selectionHidden && doc.isSelected(bone);
			Color boneColor = bone.isActive() ? bone.getData().getColor() : BONE_INACTIVE;
			if (selected) shapes.setColor(BONE_SELECTED);
			else if (boneColor != null && boneColor.a > 0f) shapes.setColor(boneColor);
			else shapes.setColor(BONE_LINE);
			if (parent != null) {
				shapes.line(parent.getAppliedPose().getWorldX(), parent.getAppliedPose().getWorldY(), wx, wy);
			}
			float length = bone.getData().getLength();
			if (Float.isFinite(length) && length > 0f) {
				float tx = wx + pose.getA() * length;
				float ty = wy + pose.getC() * length;
				shapes.line(wx, wy, tx, ty);
			}
			if (bone.getData().getIcon() != null && !bone.getData().getIcon().isBlank()) {
				float iconRadius = Math.max(r, r * Math.max(0.25f, bone.getData().getIconSize()));
				shapes.setColor(selected ? BONE_SELECTED : boneColor == null ? BONE_LINE : boneColor);
				shapes.line(wx - iconRadius, wy, wx, wy + iconRadius);
				shapes.line(wx, wy + iconRadius, wx + iconRadius, wy);
				shapes.line(wx + iconRadius, wy, wx, wy - iconRadius);
				shapes.line(wx, wy - iconRadius, wx - iconRadius, wy);
			}
			// 骨骼局部 X 轴/ Y 轴（帮助识别朝向）
			shapes.setColor(bone.isActive() ? AXIS_X : BONE_INACTIVE);
			float ax = wx + (float) Math.cos(Math.toRadians(pose.getWorldRotationX())) * r * 3f;
			float ay = wy + (float) Math.sin(Math.toRadians(pose.getWorldRotationX())) * r * 3f;
			shapes.line(wx, wy, ax, ay);
			shapes.setColor(bone.isActive() ? AXIS_Y : BONE_INACTIVE);
			float a2 = pose.getWorldRotationY();
			float bx = wx + (float) Math.cos(Math.toRadians(a2)) * r * 2f;
			float by = wy + (float) Math.sin(Math.toRadians(a2)) * r * 2f;
			shapes.line(wx, wy, bx, by);
		}
		shapes.end();
		if (overlayFont != null) {
			batch.setProjectionMatrix(unflippedProjection);
			batch.begin();
			for (Bone bone : bones) {
				Bone display = displayedBone(bone, reference);
				if (display == null || !doc.isBoneNameVisible(bone)) continue;
				var pose = display.getAppliedPose();
				Color color = !selectionHidden && doc.isSelected(bone) ? BONE_SELECTED
					: bone.isActive() ? bone.getData().getColor() : BONE_INACTIVE;
				overlayFont.setColor(color == null ? Color.WHITE : color);
				overlayFont.draw(batch, bone.getData().getName(),
					displayWorldX(pose.getWorldX(), camera.position.x, viewportFlipped) + r,
					pose.getWorldY() + r * 2f);
			}
			batch.end();
		}
	}

	private void drawMeshOverlay() {
		if (EditorAppState.tool != EditorTool.MESH && EditorAppState.tool != EditorTool.DEFORM
			&& EditorAppState.tool != EditorTool.WEIGHT
			|| doc.skeleton == null) return;
		if (EditorAppState.tool == EditorTool.WEIGHT) {
			drawWeightOverlay();
			return;
		}
		if (EditorAppState.tool == EditorTool.DEFORM) {
			drawDeformOverlay();
			return;
		}
		Array<Slot> drawOrder = doc.skeleton.getDrawOrder().getAppliedPose();
		shapes.setProjectionMatrix(camera.combined);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		for (Slot slot : drawOrder) {
			if (!isEditableAttachmentSlot(slot)) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (!(attachment instanceof MeshAttachment mesh)) continue;
			float[] world = setupWorldVertices(mesh, slot);
			short[] triangles = mesh.getTriangles();
			shapes.setColor(meshOverlayColor(mesh));
			if (triangles == null) continue;
			for (int index = 0; index + 2 < triangles.length; index += 3) {
				int a = Short.toUnsignedInt(triangles[index]);
				int b = Short.toUnsignedInt(triangles[index + 1]);
				int c = Short.toUnsignedInt(triangles[index + 2]);
				if (a * 2 + 1 >= mesh.getWorldVerticesLength() || b * 2 + 1 >= mesh.getWorldVerticesLength()
					|| c * 2 + 1 >= mesh.getWorldVerticesLength()) continue;
				shapes.line(world[a * 2], world[a * 2 + 1], world[b * 2], world[b * 2 + 1]);
				shapes.line(world[b * 2], world[b * 2 + 1], world[c * 2], world[c * 2 + 1]);
				shapes.line(world[c * 2], world[c * 2 + 1], world[a * 2], world[a * 2 + 1]);
			}
		}
		shapes.end();

		float radius = 4.5f * camera.zoom;
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		for (Slot slot : drawOrder) {
			if (!isEditableAttachmentSlot(slot)) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (!(attachment instanceof MeshAttachment mesh)) continue;
			float[] world = setupWorldVertices(mesh, slot);
			for (int vertex = 0; vertex < mesh.getWorldVerticesLength() / 2; vertex++) {
				boolean selected = !selectionHidden && mesh == selectedMesh && selectedMeshSlot != null
					&& slot.getData() == selectedMeshSlot.getData() && selectedMeshVertices.contains(vertex);
				shapes.setColor(selected ? BONE_SELECTED : meshOverlayColor(mesh));
				shapes.circle(world[vertex * 2], world[vertex * 2 + 1], selected ? radius * 1.45f : radius, 16);
			}
		}
		shapes.end();
	}

	private void drawDeformOverlay() {
		Array<Slot> drawOrder = doc.skeleton.getDrawOrder().getAppliedPose();
		shapes.setProjectionMatrix(camera.combined);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		for (Slot slot : drawOrder) {
			if (!slot.getData().getVisible() || !slot.getBone().isActive()) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (!isViewportDeformAttachment(attachment)) continue;
			VertexAttachment vertexAttachment = (VertexAttachment)attachment;
			float[] world = vertexWorldVertices(vertexAttachment, slot);
			shapes.setColor(attachment instanceof MeshAttachment mesh ? meshOverlayColor(mesh) : MESH_EDGE);
			if (attachment instanceof MeshAttachment mesh) {
				short[] triangles = mesh.getTriangles();
				if (triangles == null) continue;
				for (int index = 0; index + 2 < triangles.length; index += 3) {
					int a = Short.toUnsignedInt(triangles[index]);
					int b = Short.toUnsignedInt(triangles[index + 1]);
					int c = Short.toUnsignedInt(triangles[index + 2]);
					if (a * 2 + 1 >= world.length || b * 2 + 1 >= world.length
						|| c * 2 + 1 >= world.length) continue;
					shapes.line(world[a * 2], world[a * 2 + 1], world[b * 2], world[b * 2 + 1]);
					shapes.line(world[b * 2], world[b * 2 + 1], world[c * 2], world[c * 2 + 1]);
					shapes.line(world[c * 2], world[c * 2 + 1], world[a * 2], world[a * 2 + 1]);
				}
			} else if (attachment instanceof PathAttachment path) {
				drawPath(world, path.getWorldVerticesLength(), path.getClosed());
			} else {
				drawPolygon(world, vertexAttachment.getWorldVerticesLength());
			}
		}
		shapes.end();

		float radius = 4.5f * camera.zoom;
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		for (Slot slot : drawOrder) {
			if (!slot.getData().getVisible() || !slot.getBone().isActive()) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (!isViewportDeformAttachment(attachment)) continue;
			VertexAttachment vertexAttachment = (VertexAttachment)attachment;
			float[] world = vertexWorldVertices(vertexAttachment, slot);
			for (int vertex = 0; vertex < vertexAttachment.getWorldVerticesLength() / 2; vertex++) {
				boolean selected = !selectionHidden && vertexAttachment == selectedDeformAttachment && selectedDeformSlot != null
					&& slot.getData() == selectedDeformSlot.getData() && selectedDeformVertices.contains(vertex);
				shapes.setColor(selected ? BONE_SELECTED
					: attachment instanceof MeshAttachment mesh ? meshOverlayColor(mesh) : MESH_EDGE);
				shapes.circle(world[vertex * 2], world[vertex * 2 + 1], selected ? radius * 1.45f : radius, 16);
			}
		}
		shapes.end();
	}

	private void drawWeightOverlay() {
		Bone target = doc.getSelectedBone();
		if (target == null || doc.skeleton == null) return;
		int targetBoneIndex = target.getData().getIndex();
		Array<Slot> drawOrder = doc.skeleton.getDrawOrder().getAppliedPose();
		shapes.setProjectionMatrix(camera.combined);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		shapes.setColor(WEIGHT_EDGE);
		for (Slot slot : drawOrder) {
			if (!slot.getData().getVisible() || !slot.getBone().isActive()) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (!(attachment instanceof MeshAttachment mesh)) continue;
			float[] world = setupWorldVertices(mesh, slot);
			short[] triangles = mesh.getTriangles();
			if (triangles == null) continue;
			for (int index = 0; index + 2 < triangles.length; index += 3) {
				int a = Short.toUnsignedInt(triangles[index]);
				int b = Short.toUnsignedInt(triangles[index + 1]);
				int c = Short.toUnsignedInt(triangles[index + 2]);
				if (a * 2 + 1 >= world.length || b * 2 + 1 >= world.length || c * 2 + 1 >= world.length) continue;
				shapes.line(world[a * 2], world[a * 2 + 1], world[b * 2], world[b * 2 + 1]);
				shapes.line(world[b * 2], world[b * 2 + 1], world[c * 2], world[c * 2 + 1]);
				shapes.line(world[c * 2], world[c * 2 + 1], world[a * 2], world[a * 2 + 1]);
			}
		}
		if (pointerInside) {
			Vector2 world = screenToWorld(pointerLocalX, pointerLocalY);
			float cursorX = world.x, cursorY = world.y;
			shapes.setColor(WEIGHT_BRUSH);
			shapes.circle(cursorX, cursorY, WEIGHT_BRUSH_RADIUS_PIXELS * camera.zoom, 64);
		}
		shapes.end();

		float radius = 5.5f * camera.zoom;
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		for (Slot slot : drawOrder) {
			if (!slot.getData().getVisible() || !slot.getBone().isActive()) continue;
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (!(attachment instanceof MeshAttachment mesh)) continue;
			float[] world = setupWorldVertices(mesh, slot);
			float[] weights;
			try {
				weights = SkeletonEditor.meshWeightsForBone(mesh, targetBoneIndex,
					slot.getBone().getData().getIndex());
			} catch (IllegalArgumentException ignored) {
				continue;
			}
			for (int vertex = 0; vertex < weights.length && vertex * 2 + 1 < world.length; vertex++) {
				shapes.setColor(meshWeightColor(weights[vertex], weightColorTemp));
				shapes.circle(world[vertex * 2], world[vertex * 2 + 1], radius, 18);
			}
		}
		shapes.end();
	}

	static Color meshWeightColor(float weight, Color out) {
		if (out == null) out = new Color();
		float value = Float.isFinite(weight) ? Math.max(0f, Math.min(1f, weight)) : 0f;
		float startR, startG, startB, endR, endG, endB, alpha;
		if (value < 0.25f) {
			startR = 0.08f; startG = 0.10f; startB = 0.18f;
			endR = 0f; endG = 0.45f; endB = 1f;
			alpha = value * 4f;
		} else if (value < 0.5f) {
			startR = 0f; startG = 0.45f; startB = 1f;
			endR = 0f; endG = 0.85f; endB = 0.45f;
			alpha = (value - 0.25f) * 4f;
		} else if (value < 0.75f) {
			startR = 0f; startG = 0.85f; startB = 0.45f;
			endR = 1f; endG = 0.90f; endB = 0.05f;
			alpha = (value - 0.5f) * 4f;
		} else {
			startR = 1f; startG = 0.90f; startB = 0.05f;
			endR = 1f; endG = 0.18f; endB = 0.08f;
			alpha = (value - 0.75f) * 4f;
		}
		return out.set(startR + (endR - startR) * alpha, startG + (endG - startG) * alpha,
			startB + (endB - startB) * alpha, 1f);
	}

	private Color meshOverlayColor(MeshAttachment mesh) {
		if (mesh.getSourceMesh() != null) return MESH_LINKED;
		return mesh.getBones() == null ? MESH_EDGE : MESH_WEIGHTED;
	}

	private void drawGizmo() {
		if (selectionHidden || !isViewportBoneSelectionActive(doc)) return;
		EditorTool tool = EditorAppState.tool;
		if (tool != EditorTool.MOVE && tool != EditorTool.ROTATE && tool != EditorTool.SCALE
			&& tool != EditorTool.SHEAR) return;
		if (!allowsBoneGizmo(tool, doc.getSelectionKind())) return;
		Bone bone = doc.getSelectedBone();
		if (!isViewportBoneTarget(doc.skeleton, bone)) return;
		var pose = bone.getAppliedPose();
		float wx = pose.getWorldX();
		float wy = pose.getWorldY();
		TransformAxis highlighted = dragging ? dragTransformAxis : hoveredTransformAxis;

		shapes.setProjectionMatrix(camera.combined);
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		if (tool == EditorTool.ROTATE) {
			drawRotationGizmo(wx, wy, pose.getWorldRotationX(), highlighted == TransformAxis.ROTATE);
			shapes.end();
			return;
		}
		float xRotation = (float)Math.toRadians(transformAxisWorldRotation(tool, TransformAxis.X, bone));
		float yRotation = (float)Math.toRadians(transformAxisWorldRotation(tool, TransformAxis.Y, bone));
		drawTransformAxis(wx, wy, (float)Math.cos(xRotation), (float)Math.sin(xRotation),
			AXIS_X, tool, highlighted == TransformAxis.X);
		drawTransformAxis(wx, wy, (float)Math.cos(yRotation), (float)Math.sin(yRotation),
			AXIS_Y, tool, highlighted == TransformAxis.Y);
		shapes.setColor(BONE_SELECTED);
		shapes.circle(wx, wy, 4f * camera.zoom, 20);
		shapes.end();
	}

	private void drawRotationGizmo(float originX, float originY, float rotation, boolean highlighted) {
		float zoom = camera.zoom;
		float radius = ROTATION_GIZMO_RADIUS_PIXELS * zoom;
		float width = (highlighted ? 4f : 2.5f) * zoom;
		shapes.setColor(AXIS_X);
		float previousX = originX + radius;
		float previousY = originY;
		for (int segment = 1; segment <= ROTATION_GIZMO_SEGMENTS; segment++) {
			float angle = MathUtils.PI2 * segment / ROTATION_GIZMO_SEGMENTS;
			float nextX = originX + MathUtils.cos(angle) * radius;
			float nextY = originY + MathUtils.sin(angle) * radius;
			shapes.rectLine(previousX, previousY, nextX, nextY, width);
			previousX = nextX;
			previousY = nextY;
		}
		float direction = (float)Math.toRadians(rotation);
		float directionX = (float)Math.cos(direction);
		float directionY = (float)Math.sin(direction);
		float spokeStart = 6f * zoom;
		float handleX = originX + directionX * radius;
		float handleY = originY + directionY * radius;
		shapes.rectLine(originX + directionX * spokeStart, originY + directionY * spokeStart,
			handleX, handleY, width);
		shapes.circle(handleX, handleY, (highlighted ? 5f : 3.5f) * zoom, 16);
	}

	private void drawTransformAxis(float originX, float originY, float axisX, float axisY,
		Color color, EditorTool tool, boolean highlighted) {
		float zoom = camera.zoom;
		float length = TRANSFORM_AXIS_LENGTH_PIXELS * zoom;
		float width = (highlighted ? 4f : 2.5f) * zoom;
		float tipX = originX + axisX * length;
		float tipY = originY + axisY * length;
		shapes.setColor(color);
		if (tool == EditorTool.MOVE) {
			float headLength = (highlighted ? 11f : 9f) * zoom;
			float headWidth = (highlighted ? 6.5f : 5f) * zoom;
			float baseX = tipX - axisX * headLength;
			float baseY = tipY - axisY * headLength;
			shapes.rectLine(originX, originY, baseX, baseY, width);
			float perpendicularX = -axisY * headWidth;
			float perpendicularY = axisX * headWidth;
			shapes.triangle(tipX, tipY, baseX + perpendicularX, baseY + perpendicularY,
				baseX - perpendicularX, baseY - perpendicularY);
		} else if (tool == EditorTool.SHEAR) {
			shapes.rectLine(originX, originY, tipX, tipY, width);
			float radius = TRANSFORM_AXIS_HANDLE_SIZE_PIXELS * (highlighted ? 0.75f : 0.6f) * zoom;
			float perpendicularX = -axisY * radius;
			float perpendicularY = axisX * radius;
			float forwardX = axisX * radius;
			float forwardY = axisY * radius;
			shapes.triangle(tipX + forwardX, tipY + forwardY,
				tipX + perpendicularX, tipY + perpendicularY,
				tipX - forwardX, tipY - forwardY);
			shapes.triangle(tipX + forwardX, tipY + forwardY,
				tipX - forwardX, tipY - forwardY,
				tipX - perpendicularX, tipY - perpendicularY);
		} else {
			shapes.rectLine(originX, originY, tipX, tipY, width);
			float handleSize = TRANSFORM_AXIS_HANDLE_SIZE_PIXELS * (highlighted ? 1.25f : 1f) * zoom;
			shapes.rect(tipX - handleSize * 0.5f, tipY - handleSize * 0.5f, handleSize, handleSize);
		}
	}

	@Override
	public boolean remove() {
		dispose();
		return super.remove();
	}

	public void dispose() {
		if (disposed) return;
		disposed = true;
		inactiveSkinBones.clear();
		batch.dispose();
		shapes.dispose();
	}
}
