package com.openspine.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.IkConstraint;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.IkConstraintPose;
import com.esotericsoftware.spine.PathConstraint;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PathConstraintPose;
import com.esotericsoftware.spine.PhysicsConstraint;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintPose;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SliderPose;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.TransformConstraint;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.TransformConstraintPose;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.Animation.AttachmentTimeline;
import com.esotericsoftware.spine.Animation.AlphaTimeline;
import com.esotericsoftware.spine.Animation.BoneTimeline;
import com.esotericsoftware.spine.Animation.BoneTimeline2;
import com.esotericsoftware.spine.Animation.DrawOrderTimeline;
import com.esotericsoftware.spine.Animation.DeformTimeline;
import com.esotericsoftware.spine.Animation.EventTimeline;
import com.esotericsoftware.spine.Animation.InheritTimeline;
import com.esotericsoftware.spine.Animation.IkConstraintTimeline;
import com.esotericsoftware.spine.Animation.ConstraintTimeline;
import com.esotericsoftware.spine.Animation.PathConstraintMixTimeline;
import com.esotericsoftware.spine.Animation.PathConstraintPositionTimeline;
import com.esotericsoftware.spine.Animation.PathConstraintSpacingTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintDampingTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintGravityTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintInertiaTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintMassTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintMixTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintResetTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintStrengthTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintWindTimeline;
import com.esotericsoftware.spine.Animation.DrawOrderFolderTimeline;
import com.esotericsoftware.spine.Animation.SequenceTimeline;
import com.esotericsoftware.spine.Animation.SliderMixTimeline;
import com.esotericsoftware.spine.Animation.SliderTimeline;
import com.esotericsoftware.spine.Animation.TransformConstraintTimeline;
import com.esotericsoftware.spine.Animation.RGBATimeline;
import com.esotericsoftware.spine.Animation.RGB2Timeline;
import com.esotericsoftware.spine.Animation.RGBA2Timeline;
import com.esotericsoftware.spine.Animation.RGBTimeline;
import com.esotericsoftware.spine.Animation.SlotCurveTimeline;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.HasSequence;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.Sequence.SequenceMode;
import com.esotericsoftware.spine.attachments.VertexAttachment;
import com.openspine.model.AnimationEditor;
import com.openspine.model.BoneKeyframeBatch;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonStructureSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Objects;

/**
 * 时间线关键帧编辑面板。
	 * 轨道包括骨骼变换（旋转/缩放/平移/剪切/继承）、槽附件切换、RGBA 颜色、双色染色和事件。左列轨道名，右列关键帧画布。
 * 交互：左键选择或框选关键帧，拖拽移动所选帧，右键拖拽平移时间轴；骨骼帧可双击切换曲线。
 */
public class TimelinePanel extends Table implements Disposable {
	public static final float DEFAULT_PROJECT_FPS = 30f;
	private static final String NONE_ATTACHMENT = "（无附件）";
	private static final String TRACK_ALL = "全部轨道";
	private static final String TRACK_SELECTED = "选中骨骼";
	private static final String TRACK_KEYED = "已有关键帧";
	private static final String GLOBAL_PHYSICS_SELECTION = "";
	public static final String FILTER_ALL = "all";
	public static final String FILTER_SELECTED = "selected";
	public static final String FILTER_KEYED = "keyed";
	public static final float DEFAULT_ZOOM = 50f;
	static final float TRACK_LABEL_COLUMN_WIDTH = 208f;
	static final float TRACK_LABEL_LEFT_PADDING = 9f;
	static final float TRACK_ROW_HEIGHT = 26f;
	static final float TIMELINE_WHEEL_STEP = TRACK_ROW_HEIGHT * 3f;
	static final float MARQUEE_DRAG_THRESHOLD = 3f;
	static final float FPS_FIELD_WIDTH = 46f;
	static final float TIMELINE_LEFT_GUTTER = 0f;
	static final float TIMELINE_MARKER_PADDING = 10f;
	static final float TOOLBAR_GROUP_GAP = 52f;
	enum SearchKeyAction { NONE, CLEAR_SEARCH, FOCUS_CANVAS }
	enum RebuildSelection { CLEAR, VISIBLE, HIDDEN }
	record KeyframeDraft(String kind, String time, String primary, String secondary, int curve) {}
	private static final int NO_DRAFT_CURVE = -1;
	private static final float MIN_ZOOM = 25f;
	private static final float MAX_ZOOM = 150f;
	private static final float ZOOM_STEP = 5f;
	private static final float MIN_VERTICAL_ZOOM = 18f / TRACK_ROW_HEIGHT;
	private static final float MAX_VERTICAL_ZOOM = 2f;
	private static final float TOOLBAR_SCROLL_HEIGHT = 38f;
	enum TimelineWheelAction { VERTICAL, HORIZONTAL, ZOOM, FRAME_SPACING }

	static float pannedScrollX(float startScroll, float stageDeltaX) {
		return Math.max(0f, startScroll - stageDeltaX);
	}

	static float pannedScrollY(float startScroll, float stageDeltaY) {
		return Math.max(0f, startScroll + stageDeltaY);
	}

	static TimelineWheelAction timelineWheelAction(boolean control, boolean shift, boolean overDopesheetRuler) {
		if (control) return TimelineWheelAction.ZOOM;
		if (shift) return TimelineWheelAction.HORIZONTAL;
		return overDopesheetRuler ? TimelineWheelAction.FRAME_SPACING : TimelineWheelAction.VERTICAL;
	}

	static boolean timelineRulerWheelRegion(float x, float y, float viewportWidth, float viewportHeight,
		float timelineBoundary, float rulerHeight) {
		return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(viewportWidth)
			&& Float.isFinite(viewportHeight) && Float.isFinite(timelineBoundary)
			&& Float.isFinite(rulerHeight) && viewportWidth >= 0f && viewportHeight >= 0f
			&& rulerHeight >= 0f && x >= timelineBoundary && x <= viewportWidth
			&& y >= viewportHeight - rulerHeight && y <= viewportHeight;
	}

	static float timelineWheelAmount(float amountX, float amountY) {
		return Math.abs(amountY) > 0.0001f ? amountY : amountX;
	}

	static float timelineWheelZoom(float zoom, float wheelAmount) {
		if (!Float.isFinite(wheelAmount) || Math.abs(wheelAmount) <= 0.0001f) return normalizeZoom(zoom);
		return normalizeZoom(zoom - Math.signum(wheelAmount) * ZOOM_STEP);
	}

	static float timelineVerticalScale(float zoom) {
		return Math.max(MIN_VERTICAL_ZOOM,
			Math.min(MAX_VERTICAL_ZOOM, normalizeZoom(zoom) / DEFAULT_ZOOM));
	}

	static float timelineLabelScale(float verticalZoom) {
		return timelineVerticalScale(verticalZoom);
	}

	static float timelineTrackRowHeight(float zoom) {
		return TRACK_ROW_HEIGHT * timelineVerticalScale(zoom);
	}

	static float timelineWheelStep(float zoom) {
		return timelineTrackRowHeight(zoom) * 3f;
	}

	static float zoomAnchoredVerticalScroll(float scrollY, float viewportHeight, float anchorY,
		float previousZoom, float nextZoom, float fixedHeaderHeight) {
		float distanceBelowTop = Math.max(0f, viewportHeight - anchorY);
		float anchorDistance = Math.max(0f, scrollY + distanceBelowTop);
		if (anchorDistance <= fixedHeaderHeight) return Math.max(0f, anchorDistance - distanceBelowTop);
		float ratio = timelineVerticalScale(nextZoom) / timelineVerticalScale(previousZoom);
		float scaledDistance = fixedHeaderHeight + (anchorDistance - fixedHeaderHeight) * ratio;
		return Math.max(0f, scaledDistance - distanceBelowTop);
	}

	private static final class TimelineScrollPane extends ScrollPane {
		TimelineScrollPane(Actor actor, ScrollPaneStyle style) {
			super(actor, style);
			setSmoothScrolling(false);
		}

		@Override protected float getMouseWheelX() { return TIMELINE_WHEEL_STEP; }

		@Override protected float getMouseWheelY() { return TIMELINE_WHEEL_STEP; }

		@Override protected void scrollX(float pixelsX) {
			super.scrollX(pixelsX);
			visualScrollX(pixelsX);
		}

		@Override protected void scrollY(float pixelsY) {
			super.scrollY(pixelsY);
			visualScrollY(pixelsY);
		}
	}

	static float clampedKeyframeDelta(float requestedDelta, float earliestSelectedTime) {
		return Math.max(requestedDelta, -Math.max(0f, earliestSelectedTime));
	}

	static boolean hasKeyframeTimeDelta(float delta) {
		return Float.compare(delta, 0f) != 0;
	}

	static boolean sameKeyframeTime(float first, float second) {
		return Float.compare(first, second) == 0;
	}

	static int preferredCurveTrackIndex(int selectedIndex, int rememberedIndex, int fallbackIndex) {
		if (selectedIndex >= 0) return selectedIndex;
		if (rememberedIndex >= 0) return rememberedIndex;
		return fallbackIndex;
	}

	static boolean pointInsideSelection(float x, float y, float x1, float y1, float x2, float y2) {
		return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
			&& y >= Math.min(y1, y2) && y <= Math.max(y1, y2);
	}

	static float timelineBoundaryX(float labelWidth) {
		if (!Float.isFinite(labelWidth) || labelWidth < 0f)
			throw new IllegalArgumentException("Timeline label width must be finite and non-negative");
		return labelWidth + TIMELINE_LEFT_GUTTER;
	}

	static float timelineOriginX(float labelWidth) {
		return timelineBoundaryX(labelWidth) + TIMELINE_MARKER_PADDING;
	}

	static boolean timelineSpanVisible(float left, float right, float viewportLeft, float viewportRight) {
		return Float.isFinite(left) && Float.isFinite(right) && Float.isFinite(viewportLeft)
			&& Float.isFinite(viewportRight) && left <= right && viewportLeft <= viewportRight
			&& left >= viewportLeft && right <= viewportRight;
	}

	static float timelineActiveEndTime(float animationDuration, float frameRate) {
		if (!Float.isFinite(animationDuration) || animationDuration < 0f
			|| !Float.isFinite(frameRate) || frameRate <= 0f) return 0f;
		return animationDuration + 1f / frameRate;
	}
	private final SkeletonDocument doc;
	private final AnimationEditor animEditor;
	private final SkeletonEditor skeletonEditor;
	private final UiKit kit;
	private EditHistory history;

	private final SelectBox<String> animBox;
	private final SelectBox<String> trackFilterBox;
	private final TextField trackSearchField;
	private final Label trackCountLabel;
	private final TextField fpsField;
	private final TextButton addBtn, duplicateBtn, renBtn, delBtn, eventDefinitionsBtn, ikConstraintsBtn,
		transformConstraintsBtn, pathConstraintsBtn, physicsConstraintsBtn, sliderConstraintsBtn,
		translateSeparateBtn, scaleSeparateBtn, shearSeparateBtn;
	private final Slider zoomSlider;
	private final TextButton fpsResetBtn, zoomResetBtn;
	private final Table zoomControl;
	private final Label keySelectionLabel, keyCurveStatus, drawOrderSummaryLabel, ikOptionsSummaryLabel,
		deformSummaryLabel;
	private final Label keyValue1Label, keyValue2Label;
	private final TextField keyTimeField, keyValue1Field, keyValue2Field, keyColorField, keyDarkColorField,
		keyAlphaField, keyIkMixField, keyIkSoftnessField, keyTransformRotateField, keyTransformXField,
		keyTransformYField, keyTransformScaleXField, keyTransformScaleYField, keyTransformShearYField,
		keyPathValueField, keyPathRotateField, keyPathXField, keyPathYField, keyPhysicsValueField,
		keySequenceIndexField, keySequenceDelayField;
	private final SelectBox<String> keyAttachmentBox, keyInheritBox;
	private final SelectBox<String> keyEventBox, keySequenceModeBox;
	private final Image keyColorSwatch, keyDarkColorSwatch;
	private final Table keyValueHost, keyCurveHost, boneKeyEditor, inheritKeyEditor, attachmentKeyEditor, colorKeyEditor,
		alphaKeyEditor, twoColorKeyEditor, ikKeyEditor, transformKeyEditor, deformKeyEditor, drawOrderKeyEditor,
		pathValueKeyEditor, pathMixKeyEditor, physicsValueKeyEditor, physicsResetKeyEditor, sequenceKeyEditor,
		eventKeyEditor, curveEditor;
	private final Label keyPathValueLabel, keyPhysicsValueLabel;
	private final TextButton keyframeViewBtn, curveViewBtn;
	private final TextButton linearBtn, steppedBtn, bezierBtn, graphBtn, applyKeyBtn, deleteKeyBtn, copyKeyBtn, pasteKeyBtn,
		autoKeyBtn, poseKeyBtn, cleanBtn, eventPayloadBtn, drawOrderEditBtn, ikOptionsBtn, deformEditBtn;
	private final KeyframeCanvas canvas;
	private final ScrollPane scroll;
	private GraphWorkspaceWindow graphWorkspace;
	private float timelineZoom = DEFAULT_ZOOM;
	private float timelineVerticalZoom = DEFAULT_ZOOM;
	private AnimationEditor.Prop copiedProp;
	private float copiedValue1, copiedValue2;
	private AnimationEditor.CurveSnapshot copiedCurve;
	private AnimationEditor.KeyframeBlock copiedKeyframeBlock;
	private boolean copiedInheritKey;
	private Inherit copiedInherit;
	private boolean copiedAttachmentKey;
	private String copiedAttachmentName;
	private boolean copiedColorKey;
	private boolean copiedRgbKey;
	private boolean copiedAlphaKey;
	private float copiedAlpha;
	private final Color copiedColor = new Color();
	private boolean copiedTwoColorKey;
	private final Color copiedDarkColor = new Color();
	private boolean copiedTwoColorHasAlpha;
	private boolean copiedDrawOrderKey;
	private int[] copiedDrawOrder;
	private int[] copiedDrawOrderFolderSlots;
	private boolean copiedIkKey;
	private AnimationEditor.IkKeyframe copiedIk;
	private boolean copiedTransformKey;
	private AnimationEditor.TransformKeyframe copiedTransform;
	private boolean copiedPathKey;
	private AnimationEditor.PathProp copiedPathProp;
	private float copiedPathValue;
	private AnimationEditor.PathMixKeyframe copiedPathMix;
	private boolean copiedPhysicsKey;
	private AnimationEditor.PhysicsProp copiedPhysicsProp;
	private float copiedPhysicsValue;
	private boolean copiedSliderKey;
	private AnimationEditor.SliderProp copiedSliderProp;
	private float copiedSliderValue;
	private boolean copiedDeformKey;
	private float[] copiedDeformOffsets;
	private boolean copiedSequenceKey;
	private AnimationEditor.SequenceKeyframe copiedSequence;
	private boolean copiedEventKey;
	private EventData copiedEventDefinition;
	private int copiedEventInt;
	private float copiedEventFloat, copiedEventVolume, copiedEventBalance;
	private String copiedEventString;
	private String preferredEventDefinitionName;
	private float[] deformDraftOffsets;
	private KeyframeDraft keyframeBaseline;

	private boolean updating;
	private boolean refreshingKeyframeInspector;
	private String lastFilteredBoneName;
	private Runnable onDataChanged;
	private Runnable onSelectionChanged;
	private Runnable onAnimationChanged;
	private Runnable onProjectSettingsChanged;
	private Runnable onConstraintChanged;
	private Runnable onTimeChanged;
	private java.util.function.BiConsumer<String, Boolean> onStatus;

	public TimelinePanel(SkeletonDocument doc, UiKit kit, AnimationEditor animEditor,
		SkeletonEditor skeletonEditor, PlaybackPanel playbackPanel) {
		this.doc = doc;
		this.kit = kit;
		this.animEditor = animEditor;
		this.skeletonEditor = skeletonEditor;
		Objects.requireNonNull(playbackPanel, "playbackPanel");
		setBackground(kit.panelBg);
		pad(4);

		// ---- 顶部：动画工具条 ----
		Table bar = new Table();
		Label animationToolbarLabel = kit.controlLabel("动画");
		animBox = kit.selectBox();
		animBox.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				selectAnimation(animBox.getSelected());
			}
		});
		addBtn = UiKit.asActionButton(new TextButton("+ 新建", kit.toolButton));
		addBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) { addAnimation(); }
		});
		duplicateBtn = UiKit.asActionButton(new TextButton("复制", kit.toolButton));
		duplicateBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { duplicateAnimation(); }
		});
		renBtn = UiKit.asActionButton(new TextButton("重命名", kit.toolButton));
		renBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) { renameDialog(); }
		});
		delBtn = UiKit.asActionButton(new TextButton("删除", kit.toolButton));
		delBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) { removeAnimation(); }
		});
		cleanBtn = UiKit.asActionButton(new TextButton("清理", kit.toolButton));
		cleanBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { cleanAnimation(); }
		});
		eventDefinitionsBtn = UiKit.asActionButton(new TextButton("事件定义", kit.toolButton));
		eventDefinitionsBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showEventDefinitionsDialog(); }
		});
		ikConstraintsBtn = UiKit.asActionButton(new TextButton("IK 约束", kit.toolButton));
		ikConstraintsBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showIkConstraintsDialog(); }
		});
		transformConstraintsBtn = UiKit.asActionButton(new TextButton("Transform", kit.toolButton));
		transformConstraintsBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showTransformConstraintsDialog(); }
		});
		pathConstraintsBtn = UiKit.asActionButton(new TextButton("Path", kit.toolButton));
		pathConstraintsBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showPathConstraintsDialog(); }
		});
		physicsConstraintsBtn = UiKit.asActionButton(new TextButton("Physics", kit.toolButton));
		physicsConstraintsBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showPhysicsConstraintsDialog(); }
		});
		sliderConstraintsBtn = UiKit.asActionButton(new TextButton("Slider", kit.toolButton));
		sliderConstraintsBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showSliderConstraintsDialog(); }
		});
		translateSeparateBtn = new TextButton("平移 XY", kit.toolButton);
		translateSeparateBtn.setProgrammaticChangeEvents(false);
		translateSeparateBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) {
					toggleAxisSeparation(AnimationEditor.AxisGroup.TRANSLATE,
						translateSeparateBtn.isChecked());
					refreshAxisSeparationStyles();
				}
			}
		});
		scaleSeparateBtn = new TextButton("缩放 XY", kit.toolButton);
		scaleSeparateBtn.setProgrammaticChangeEvents(false);
		scaleSeparateBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) {
					toggleAxisSeparation(AnimationEditor.AxisGroup.SCALE,
						scaleSeparateBtn.isChecked());
					refreshAxisSeparationStyles();
				}
			}
		});
		shearSeparateBtn = new TextButton("剪切 XY", kit.toolButton);
		shearSeparateBtn.setProgrammaticChangeEvents(false);
		shearSeparateBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) {
					toggleAxisSeparation(AnimationEditor.AxisGroup.SHEAR,
						shearSeparateBtn.isChecked());
					refreshAxisSeparationStyles();
				}
			}
		});
		trackFilterBox = kit.selectBox();
		trackFilterBox.setItems(trackFilterLabels());
		trackFilterBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating || canvas == null) return;
				canvas.rebuild();
			}
		});
		trackSearchField = new TextField("", kit.textFieldStyle);
		trackSearchField.setAlignment(com.badlogic.gdx.utils.Align.center);
		trackSearchField.setMessageText("搜索");
		trackSearchField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating || canvas == null) return;
				canvas.rebuild();
			}
		});
		trackSearchField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (searchKeyAction(keycode, !trackSearchField.getText().isEmpty())) {
				case CLEAR_SEARCH -> { trackSearchField.setText(""); yield true; }
				case FOCUS_CANVAS -> { focusCanvas(); yield true; }
				case NONE -> false;
				};
			}
		});
		trackCountLabel = kit.controlLabel("0 条");
		fpsField = new TextField(UiNumbers.format(DEFAULT_PROJECT_FPS), kit.textFieldStyle);
		fpsField.setMaxLength(8);
		fpsField.setAlignment(com.badlogic.gdx.utils.Align.center);
		fpsField.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '.');
		fpsField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleFpsFieldKey(keycode);
			}
		});
		fpsField.addListener(applyFpsOnFocusLost(() -> {
			if (!updating) applyProjectFpsAfterEdit();
		}));
		fpsResetBtn = UiKit.asActionButton(new TextButton("FPS", kit.toolButton));
		fpsResetBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { resetProjectFps(); }
		});
		keyframeViewBtn = new TextButton("摄影表", kit.toolButton);
		curveViewBtn = new TextButton("曲线", kit.toolButton);
		keyframeViewBtn.setProgrammaticChangeEvents(false);
		curveViewBtn.setProgrammaticChangeEvents(false);
		keyframeViewBtn.setChecked(false);
		curveViewBtn.setChecked(true);
		keyframeViewBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { setTimelineView(false); }
		});
		curveViewBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { setTimelineView(true); }
		});
		zoomSlider = new Slider(MIN_ZOOM, MAX_ZOOM, ZOOM_STEP, false, kit.sliderStyle);
		zoomSlider.setProgrammaticChangeEvents(false);
		zoomSlider.setValue(timelineZoom);
		zoomSlider.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { syncZoomSlider(); }
		});
		zoomResetBtn = UiKit.asActionButton(new TextButton(timelineZoomLabel(timelineZoom), kit.toolButton));
		zoomResetBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { resetTimelineZoom(); }
		});
		zoomControl = new Table() {
			@Override public void act(float delta) {
				super.act(delta);
				syncZoomSlider();
			}
		};
		zoomControl.setBackground(kit.scrollBg);
		zoomControl.pad(2);
		zoomControl.add(zoomResetBtn).height(26).padRight(5);
		zoomControl.add(zoomSlider).width(PlaybackPanel.RATE_CONTROL_SLIDER_WIDTH);

		Table viewTools = toolbarGroup();
		viewTools.add(curveViewBtn).width(52).height(26).padRight(2);
		viewTools.add(keyframeViewBtn).width(62).height(26);
		Table trackTools = toolbarGroup();
		trackTools.add(trackFilterBox).width(84).height(26).padRight(4);
		trackTools.add(trackSearchField).width(56).height(26);
		trackTools.add(trackCountLabel).width(52).left().padLeft(5);
		Table animationTools = toolbarGroup();
		animationTools.add(animationToolbarLabel).left().padRight(5);
		animationTools.add(animBox).width(140).height(26).padRight(5);
		animationTools.add(addBtn).height(26).padRight(3);
		animationTools.add(duplicateBtn).height(26).padRight(3);
		animationTools.add(renBtn).height(26).padRight(3);
		animationTools.add(delBtn).height(26).padRight(3);
		animationTools.add(cleanBtn).height(26);
		Table constraintTools = toolbarGroup();
		constraintTools.add(eventDefinitionsBtn).height(26).padRight(3);
		constraintTools.add(ikConstraintsBtn).height(26).padRight(3);
		constraintTools.add(transformConstraintsBtn).height(26).padRight(3);
		constraintTools.add(pathConstraintsBtn).height(26).padRight(3);
		constraintTools.add(physicsConstraintsBtn).height(26).padRight(3);
		constraintTools.add(sliderConstraintsBtn).height(26);
		Table axisTools = toolbarGroup();
		axisTools.add(kit.controlLabel("XY 分离")).padRight(4);
		axisTools.add(translateSeparateBtn).height(26).padRight(3);
		axisTools.add(scaleSeparateBtn).height(26).padRight(3);
		axisTools.add(shearSeparateBtn).height(26);
		Table fpsTools = toolbarGroup();
		fpsTools.add(fpsResetBtn).height(26).padRight(4);
		fpsTools.add(fpsField).width(FPS_FIELD_WIDTH).height(26);
		bar.add(viewTools).height(30);
		bar.add().width(TOOLBAR_GROUP_GAP);
		bar.add(animationTools).height(30);
		bar.add().width(TOOLBAR_GROUP_GAP);
		bar.add(constraintTools).height(30);
		bar.add().width(TOOLBAR_GROUP_GAP);
		bar.add(axisTools).height(30).padRight(4);
		bar.add().growX();
		bar.add(fpsTools).height(30);
		add(horizontalToolbar(bar)).growX().minWidth(0f).height(TOOLBAR_SCROLL_HEIGHT).left().row();

		// ---- 选中关键帧的精确编辑 ----
		Table keyBar = new Table();
		keySelectionLabel = kit.controlLabel("未选择关键帧");
		keySelectionLabel.setEllipsis(true);
		keySelectionLabel.addListener(trackTooltip(this::selectedTrackTooltip));
		keyBar.add(keySelectionLabel).width(TRACK_LABEL_COLUMN_WIDTH - TRACK_LABEL_LEFT_PADDING)
			.left().padLeft(TRACK_LABEL_LEFT_PADDING).padRight(8);
		keyBar.add(kit.controlLabel("时间")).padRight(3);
		keyTimeField = createNumberField();
		keyBar.add(keyTimeField).width(72).padRight(7);
		keyValueHost = new Table();
		boneKeyEditor = new Table();
		keyValue1Label = kit.controlLabel("数值");
		boneKeyEditor.add(keyValue1Label).padRight(3);
		keyValue1Field = createNumberField();
		boneKeyEditor.add(keyValue1Field).width(72).padRight(7);
		keyValue2Label = kit.controlLabel("");
		boneKeyEditor.add(keyValue2Label).padRight(3);
		keyValue2Field = createNumberField();
		boneKeyEditor.add(keyValue2Field).width(72);

		curveEditor = new Table();
		curveEditor.add(kit.controlLabel("曲线")).padRight(3);
		keyCurveStatus = kit.controlLabel("-");
		curveEditor.add(keyCurveStatus).width(48).left().padRight(3);
		linearBtn = new TextButton("线性", kit.toolButton);
		steppedBtn = new TextButton("步进", kit.toolButton);
		bezierBtn = new TextButton("贝塞尔曲线", kit.toolButton);
		graphBtn = UiKit.asActionButton(new TextButton("曲线编辑", kit.toolButton));
		linearBtn.setProgrammaticChangeEvents(false);
		linearBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (linearBtn.isDisabled()) return;
				linearBtn.setChecked(true);
				steppedBtn.setChecked(false);
				bezierBtn.setChecked(false);
				refreshCurveButtonStyles();
				keyCurveStatus.setText("线性");
				refreshKeyApplyState();
			}
		});
		steppedBtn.setProgrammaticChangeEvents(false);
		steppedBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (steppedBtn.isDisabled()) return;
				linearBtn.setChecked(false);
				steppedBtn.setChecked(true);
				bezierBtn.setChecked(false);
				refreshCurveButtonStyles();
				keyCurveStatus.setText("步进");
				refreshKeyApplyState();
			}
		});
		bezierBtn.setProgrammaticChangeEvents(false);
		bezierBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!bezierBtn.isDisabled()) showBezierEditor();
			}
		});
		graphBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!graphBtn.isDisabled()) showGraphEditor();
			}
		});
		curveEditor.add(linearBtn).padRight(3);
		curveEditor.add(steppedBtn).padRight(3);
		curveEditor.add(bezierBtn).width(Value.prefWidth).padRight(3);
		curveEditor.add(graphBtn).width(Value.prefWidth);

		inheritKeyEditor = new Table();
		inheritKeyEditor.add(kit.controlLabel("继承")).padRight(4);
		keyInheritBox = kit.selectBox();
		keyInheritBox.setItems(inheritLabels());
		keyInheritBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshingKeyframeInspector) refreshKeyApplyState();
			}
		});
		inheritKeyEditor.add(keyInheritBox).width(190).padRight(6);
		Label inheritDiscreteLabel = kit.controlLabel("离散切换");
		inheritDiscreteLabel.setColor(0.55f, 0.7f, 0.78f, 1f);
		inheritKeyEditor.add(inheritDiscreteLabel).width(62).left();

		attachmentKeyEditor = new Table();
		attachmentKeyEditor.add(kit.controlLabel("附件")).padRight(4);
		keyAttachmentBox = kit.selectBox();
		keyAttachmentBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshingKeyframeInspector) refreshKeyApplyState();
			}
		});
		attachmentKeyEditor.add(keyAttachmentBox).width(190).padRight(6);
		Label discreteLabel = kit.controlLabel("离散切换");
		discreteLabel.setColor(0.55f, 0.7f, 0.78f, 1f);
		attachmentKeyEditor.add(discreteLabel).width(62).left();

		deformKeyEditor = new Table();
		deformKeyEditor.add(kit.controlLabel("顶点偏移")).padRight(4);
		deformSummaryLabel = kit.controlLabel("");
		deformSummaryLabel.setEllipsis(true);
		deformKeyEditor.add(deformSummaryLabel).width(148).left().padRight(4);
		deformEditBtn = UiKit.asActionButton(new TextButton("编辑...", kit.toolButton));
		deformEditBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showDeformOffsetsEditor(); }
		});
		deformKeyEditor.add(deformEditBtn).width(68);

		eventKeyEditor = new Table();
		eventKeyEditor.add(kit.controlLabel("事件")).padRight(4);
		keyEventBox = kit.selectBox();
		keyEventBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshingKeyframeInspector) refreshKeyApplyState();
			}
		});
		eventKeyEditor.add(keyEventBox).width(150).padRight(4);
		eventPayloadBtn = UiKit.asActionButton(new TextButton("载荷…", kit.toolButton));
		eventPayloadBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showEventPayloadEditor(); }
		});
		eventKeyEditor.add(eventPayloadBtn).width(68);

		drawOrderKeyEditor = new Table();
		drawOrderKeyEditor.add(kit.controlLabel("顺序")).padRight(4);
		drawOrderSummaryLabel = kit.controlLabel("Setup 顺序");
		drawOrderSummaryLabel.setEllipsis(true);
		drawOrderKeyEditor.add(drawOrderSummaryLabel).width(142).left().padRight(4);
		drawOrderEditBtn = UiKit.asActionButton(new TextButton("编辑…", kit.toolButton));
		drawOrderEditBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showDrawOrderEditor(); }
		});
		drawOrderKeyEditor.add(drawOrderEditBtn).width(68);

		ikKeyEditor = new Table();
		ikKeyEditor.add(kit.controlLabel("混合")).padRight(3);
		keyIkMixField = createNumberField();
		ikKeyEditor.add(keyIkMixField).width(54).padRight(4);
		ikKeyEditor.add(kit.controlLabel("柔度")).padRight(3);
		keyIkSoftnessField = createNumberField();
		ikKeyEditor.add(keyIkSoftnessField).width(58).padRight(4);
		ikOptionsSummaryLabel = kit.controlLabel("");
		ikOptionsSummaryLabel.setEllipsis(true);
		ikKeyEditor.add(ikOptionsSummaryLabel).width(70).left().padRight(3);
		ikOptionsBtn = UiKit.asActionButton(new TextButton("选项…", kit.toolButton));
		ikOptionsBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showIkOptionsEditor(); }
		});
		ikKeyEditor.add(ikOptionsBtn).width(58);

		transformKeyEditor = new Table();
		keyTransformRotateField = createNumberField();
		keyTransformXField = createNumberField();
		keyTransformYField = createNumberField();
		keyTransformScaleXField = createNumberField();
		keyTransformScaleYField = createNumberField();
		keyTransformShearYField = createNumberField();
		addCompactTransformField("R", keyTransformRotateField);
		addCompactTransformField("X", keyTransformXField);
		addCompactTransformField("Y", keyTransformYField);
		addCompactTransformField("SX", keyTransformScaleXField);
		addCompactTransformField("SY", keyTransformScaleYField);
		addCompactTransformField("Sh", keyTransformShearYField);

		pathValueKeyEditor = new Table();
		keyPathValueLabel = kit.controlLabel("数值");
		pathValueKeyEditor.add(keyPathValueLabel).padRight(4);
		keyPathValueField = createNumberField();
		pathValueKeyEditor.add(keyPathValueField).width(104);

		pathMixKeyEditor = new Table();
		keyPathRotateField = createNumberField();
		keyPathXField = createNumberField();
		keyPathYField = createNumberField();
		addCompactPathField("旋转", keyPathRotateField);
		addCompactPathField("X", keyPathXField);
		addCompactPathField("Y", keyPathYField);

		physicsValueKeyEditor = new Table();
		keyPhysicsValueLabel = kit.controlLabel("数值");
		physicsValueKeyEditor.add(keyPhysicsValueLabel).padRight(4);
		keyPhysicsValueField = createNumberField();
		physicsValueKeyEditor.add(keyPhysicsValueField).width(104);
		physicsResetKeyEditor = new Table();
		Label physicsResetLabel = kit.controlLabel("触发物理重置");
		physicsResetLabel.setColor(0.62f, 0.72f, 0.82f, 1f);
		physicsResetKeyEditor.add(physicsResetLabel).left();

		sequenceKeyEditor = new Table();
		sequenceKeyEditor.add(kit.controlLabel("Mode")).padRight(3);
		keySequenceModeBox = kit.selectBox();
		keySequenceModeBox.setItems(sequenceModeNames());
		keySequenceModeBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshingKeyframeInspector) refreshKeyApplyState();
			}
		});
		sequenceKeyEditor.add(keySequenceModeBox).width(108).padRight(5);
		sequenceKeyEditor.add(kit.controlLabel("Index")).padRight(3);
		keySequenceIndexField = createNumberField();
		sequenceKeyEditor.add(keySequenceIndexField).width(64).padRight(5);
		sequenceKeyEditor.add(kit.controlLabel("Delay")).padRight(3);
		keySequenceDelayField = createNumberField();
		sequenceKeyEditor.add(keySequenceDelayField).width(72);

		colorKeyEditor = new Table();
		keyColorSwatch = new Image(kit.white);
		keyColorSwatch.setColor(Color.WHITE);
		keyColorField = createColorField();

		twoColorKeyEditor = new Table();
		keyDarkColorSwatch = new Image(kit.white);
		keyDarkColorSwatch.setColor(Color.BLACK);
		keyDarkColorField = createRgbField();
		configureColorKeyEditor(false);

		alphaKeyEditor = new Table();
		alphaKeyEditor.add(kit.controlLabel("透明度")).padRight(4);
		keyAlphaField = createNumberField();
		alphaKeyEditor.add(keyAlphaField).width(92);

		keyValueHost.add(boneKeyEditor).left();
		keyValueHost.left();
		keyBar.add(keyValueHost).left();
		keyBar.add().width(TOOLBAR_GROUP_GAP);
		keyCurveHost = new Table();
		keyCurveHost.add(curveEditor).left();
		keyCurveHost.left();
		keyBar.add(keyCurveHost).left().padRight(6);
		applyKeyBtn = UiKit.asActionButton(new TextButton("应用", kit.accentButton));
		applyKeyBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { applySelectedKeyframe(); }
		});
		deleteKeyBtn = UiKit.asActionButton(new TextButton("删除帧", kit.toolButton));
		deleteKeyBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { deleteSelectedKeyframe(); }
		});
		copyKeyBtn = UiKit.asActionButton(new TextButton("复制帧", kit.toolButton));
		copyKeyBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { copySelectedKeyframe(); }
		});
		pasteKeyBtn = UiKit.asActionButton(new TextButton("粘贴帧", kit.toolButton));
		pasteKeyBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { pasteCopiedKeyframe(); }
		});
		autoKeyBtn = new TextButton("自动关键帧", kit.toolButton);
		autoKeyBtn.setChecked(true);
		autoKeyBtn.setStyle(kit.activeToolButton);
		autoKeyBtn.setProgrammaticChangeEvents(false);
		autoKeyBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				autoKeyBtn.setStyle(autoKeyBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
				showInfo(autoKeyBtn.isChecked() ? "自动关键帧已开启" : "自动关键帧已关闭");
			}
		});
		poseKeyBtn = UiKit.asActionButton(new TextButton("记录姿态", kit.accentButton));
		poseKeyBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { keySelectedPose(); }
		});
		keyBar.add(applyKeyBtn).padRight(4);
		keyBar.add(deleteKeyBtn).padRight(4);
		keyBar.add(copyKeyBtn).padRight(4);
		keyBar.add(pasteKeyBtn).padRight(4);
		keyBar.add(autoKeyBtn).padRight(4);
		keyBar.add(poseKeyBtn);
		keyBar.add().growX();
		add(horizontalToolbar(keyBar)).growX().minWidth(0f).height(TOOLBAR_SCROLL_HEIGHT)
			.left().padTop(3).padBottom(3).row();
		playbackPanel.add(zoomControl).height(30).padLeft(5);
		add(playbackPanel).growX().minWidth(0f).height(38).padTop(2).padBottom(2).row();
		// ---- 中部：画布 ----
		canvas = new KeyframeCanvas();
		scroll = new TimelineScrollPane(canvas, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		scroll.setFlickScroll(false);
		scroll.setOverscroll(false, false);
		scroll.addCaptureListener(new InputListener() {
			@Override public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
				if (!handleTimelineWheel(x, y, amountX, amountY)) return false;
				event.stop();
				return true;
			}
		});
		Stack timelineStack = new Stack();
		timelineStack.add(scroll);
		Table fixedHeader = new Table();
		fixedHeader.setTouchable(Touchable.childrenOnly);
		fixedHeader.top().left();
		fixedHeader.add(trackTools).width(TRACK_LABEL_COLUMN_WIDTH).height(KeyframeCanvas.RULER_H).top().left();
		fixedHeader.add().growX();
		timelineStack.add(fixedHeader);
		add(timelineStack).grow().minWidth(0f).minHeight(0f);
		refreshKeyframeInspector();
	}

	private void setTimelineView(boolean curve) {
		keyframeViewBtn.setChecked(!curve);
		curveViewBtn.setChecked(curve);
		canvas.setCurveView(curve);
		if (curve && canvas.curveTimeline() == null)
			showInfo("请先选择一条连续数值轨道的关键帧");
	}

	private ScrollPane horizontalToolbar(Table toolbar) {
		ScrollPane pane = new ScrollPane(toolbar, kit.scrollStyle);
		pane.setScrollingDisabled(false, true);
		pane.setFadeScrollBars(false);
		pane.setOverscroll(false, false);
		return pane;
	}

	private Table toolbarGroup() {
		Table group = new Table();
		group.setBackground(kit.scrollBg);
		group.pad(2);
		return group;
	}

	private TextField createNumberField() {
		TextField field = new TextField("", kit.textFieldStyle);
		field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E');
		field.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshingKeyframeInspector) refreshKeyApplyState();
			}
		});
		field.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleKeyframeFieldKey(keycode);
			}
		});
		return field;
	}

	private void addCompactTransformField(String label, TextField field) {
		transformKeyEditor.add(kit.controlLabel(label)).padRight(2);
		transformKeyEditor.add(field).width(42).padRight(4);
	}

	private void addCompactPathField(String label, TextField field) {
		pathMixKeyEditor.add(kit.controlLabel(label)).padRight(2);
		pathMixKeyEditor.add(field).width(62).padRight(5);
	}

	private TextField createColorField() {
		TextField field = new TextField("", kit.textFieldStyle);
		field.setMessageText("#RRGGBBAA");
		field.setTextFieldFilter((textField, c) -> c == '#' || Character.digit(c, 16) >= 0);
		field.setMaxLength(9);
		field.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				updateColorSwatches();
				if (!refreshingKeyframeInspector) refreshKeyApplyState();
			}
		});
		field.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleKeyframeFieldKey(keycode);
			}
		});
		return field;
	}

	private TextField createRgbField() {
		TextField field = new TextField("", kit.textFieldStyle);
		field.setMessageText("#RRGGBB");
		field.setMaxLength(7);
		field.setTextFieldFilter((textField, c) -> c == '#' || Character.digit(c, 16) >= 0);
		field.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				updateColorSwatches();
				if (!refreshingKeyframeInspector) refreshKeyApplyState();
			}
		});
		field.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleKeyframeFieldKey(keycode);
			}
		});
		return field;
	}

	private boolean handleFpsFieldKey(int keycode) {
		return switch (UiDialogs.keyAction(keycode)) {
		case CONFIRM -> {
			if (doc.data != null && projectFpsDraftIsEquivalent(doc.data.getFps(), fpsField.getText())
				|| applyProjectFps()) focusCanvas();
			yield true;
		}
		case CANCEL -> {
			refreshFpsField();
			focusCanvas();
			showInfo("已取消 FPS 编辑");
			yield true;
		}
		case NONE -> false;
		};
	}

	private boolean handleKeyframeFieldKey(int keycode) {
		return switch (UiDialogs.keyAction(keycode)) {
		case CONFIRM -> {
			if (applyKeyBtn.isDisabled() || applySelectedKeyframe()) focusCanvas();
			yield true;
		}
		case CANCEL -> {
			focusCanvas();
			refreshKeyframeInspector();
			showInfo("已取消关键帧编辑");
			yield true;
		}
		case NONE -> false;
		};
	}

	private void focusCanvas() {
		if (getStage() != null && canvas != null) getStage().setKeyboardFocus(canvas);
	}

	private void updateColorSwatches() {
		try {
			Animation.Timeline timeline = canvas == null ? null : canvas.selectedTimeline();
			keyColorSwatch.setColor(timeline instanceof RGB2Timeline || timeline instanceof RGBTimeline
				? UiColors.parseRgb(keyColorField.getText()) : UiColors.parse(keyColorField.getText()));
		} catch (IllegalArgumentException ignored) {
			keyColorSwatch.setColor(0.22f, 0.23f, 0.27f, 1f);
		}
		try {
			keyDarkColorSwatch.setColor(UiColors.parseRgb(keyDarkColorField.getText()));
		} catch (IllegalArgumentException ignored) {
			keyDarkColorSwatch.setColor(0.22f, 0.23f, 0.27f, 1f);
		}
	}

	private Color colorAt(RGBATimeline timeline, int frame) {
		return new Color(animEditor.colorValue(timeline, frame, 0), animEditor.colorValue(timeline, frame, 1),
			animEditor.colorValue(timeline, frame, 2), animEditor.colorValue(timeline, frame, 3));
	}

	private Color rgbAt(RGBTimeline timeline, int frame) {
		return new Color(animEditor.rgbValue(timeline, frame, 0), animEditor.rgbValue(timeline, frame, 1),
			animEditor.rgbValue(timeline, frame, 2), 1f);
	}

	private Color twoColorLightAt(Animation.Timeline timeline, int frame) {
		return new Color(animEditor.twoColorValue(timeline, frame, 0), animEditor.twoColorValue(timeline, frame, 1),
			animEditor.twoColorValue(timeline, frame, 2), animEditor.twoColorValue(timeline, frame, 3));
	}

	private Color twoColorDarkAt(Animation.Timeline timeline, int frame) {
		return new Color(animEditor.twoColorValue(timeline, frame, 4), animEditor.twoColorValue(timeline, frame, 5),
			animEditor.twoColorValue(timeline, frame, 6), 1f);
	}

	private TextField[] transformKeyFields() {
		return new TextField[] {keyTransformRotateField, keyTransformXField, keyTransformYField,
			keyTransformScaleXField, keyTransformScaleYField, keyTransformShearYField};
	}

	private void setTransformKeyFields(AnimationEditor.TransformKeyframe value) {
		float[] values = {value.mixRotate(), value.mixX(), value.mixY(), value.mixScaleX(),
			value.mixScaleY(), value.mixShearY()};
		TextField[] fields = transformKeyFields();
		for (int index = 0; index < fields.length; index++) fields[index].setText(UiNumbers.format(values[index]));
	}

	private AnimationEditor.TransformKeyframe transformKeyFromFields() {
		TextField[] fields = transformKeyFields();
		float[] values = new float[fields.length];
		for (int index = 0; index < fields.length; index++)
			values[index] = parseNumber(fields[index], "Transform 混合");
		return new AnimationEditor.TransformKeyframe(values[0], values[1], values[2], values[3], values[4], values[5]);
	}

	private TextField[] pathMixKeyFields() {
		return new TextField[] {keyPathRotateField, keyPathXField, keyPathYField};
	}

	private void setPathMixKeyFields(AnimationEditor.PathMixKeyframe value) {
		keyPathRotateField.setText(UiNumbers.format(value.mixRotate()));
		keyPathXField.setText(UiNumbers.format(value.mixX()));
		keyPathYField.setText(UiNumbers.format(value.mixY()));
	}

	private AnimationEditor.PathMixKeyframe pathMixKeyFromFields() {
		return new AnimationEditor.PathMixKeyframe(parseNumber(keyPathRotateField, "Path 旋转混合"),
			parseNumber(keyPathXField, "Path X 混合"), parseNumber(keyPathYField, "Path Y 混合"));
	}

	private void configureColorKeyEditor(boolean twoColor) {
		colorKeyEditor.clearChildren();
		twoColorKeyEditor.clearChildren();
		if (twoColor) {
			twoColorKeyEditor.add(kit.controlLabel("浅")).padRight(3);
			twoColorKeyEditor.add(keyColorSwatch).size(18).padRight(3);
			twoColorKeyEditor.add(keyColorField).width(92).padRight(7);
			twoColorKeyEditor.add(kit.controlLabel("暗")).padRight(3);
			twoColorKeyEditor.add(keyDarkColorSwatch).size(18).padRight(3);
			twoColorKeyEditor.add(keyDarkColorField).width(82);
		} else {
			colorKeyEditor.add(kit.controlLabel("颜色")).padRight(4);
			colorKeyEditor.add(keyColorSwatch).size(22).padRight(5);
			colorKeyEditor.add(keyColorField).width(128);
		}
	}

	private void refreshKeyframeInspector() {
		refreshingKeyframeInspector = true;
		try {
			populateKeyframeInspector();
		} finally {
			refreshingKeyframeInspector = false;
		}
		keyframeBaseline = currentKeyframeDraft();
		refreshKeyApplyState();
	}

	private void populateKeyframeInspector() {
		Animation.Timeline timeline = canvas == null ? null : canvas.selectedTimeline();
		int frame = timeline == null ? -1 : canvas.selectedFrame(timeline);
		boolean selected = frame >= 0;
		boolean hiddenSelection = canvas != null && canvas.hasHiddenSelection();
		boolean eventTrack = selected && timeline instanceof EventTimeline;
		boolean drawOrderTrack = selected && (timeline instanceof DrawOrderTimeline
			|| timeline instanceof DrawOrderFolderTimeline);
		boolean ikTrack = selected && timeline instanceof IkConstraintTimeline;
		boolean transformTrack = selected && timeline instanceof TransformConstraintTimeline;
		boolean pathValueTrack = selected && (timeline instanceof PathConstraintPositionTimeline
			|| timeline instanceof PathConstraintSpacingTimeline);
		boolean pathMixTrack = selected && timeline instanceof PathConstraintMixTimeline;
		AnimationEditor.PhysicsProp physicsProp = selected ? animEditor.physicsPropOf(timeline) : null;
		boolean physicsResetTrack = physicsProp == AnimationEditor.PhysicsProp.RESET;
		boolean physicsValueTrack = physicsProp != null && physicsProp != AnimationEditor.PhysicsProp.RESET;
		AnimationEditor.SliderProp sliderProp = selected ? sliderPropOf(timeline) : null;
		boolean sliderTrack = sliderProp != null;
		boolean sequenceTrack = selected && timeline instanceof SequenceTimeline;
		boolean deformTrack = selected && timeline instanceof DeformTimeline;
		boolean inheritTrack = selected && timeline instanceof InheritTimeline;
		boolean attachmentTrack = selected && timeline instanceof AttachmentTimeline;
		boolean colorTrack = selected && timeline instanceof RGBATimeline;
		boolean rgbTrack = selected && timeline instanceof RGBTimeline;
		boolean alphaTrack = selected && timeline instanceof AlphaTimeline;
		boolean twoColorTrack = selected && animEditor.isTwoColorTimeline(timeline);
		boolean terminal = selected && !drawOrderTrack && !inheritTrack && !attachmentTrack && !eventTrack
			&& !sequenceTrack
			&& !physicsResetTrack
			&& frame == timeline.getFrameCount() - 1;
		boolean editable = selected;
		int selectedCurve = selected && !drawOrderTrack && !inheritTrack && !attachmentTrack && !eventTrack
			&& !sequenceTrack
			&& !physicsResetTrack && !terminal
			? animEditor.curveType(timeline, frame) : Animation.CurveTimeline.LINEAR;

		configureBoneKeyEditor(selected && !eventTrack && !drawOrderTrack && !ikTrack && !transformTrack
			&& !pathValueTrack && !pathMixTrack && !physicsResetTrack && !physicsValueTrack
			&& !sliderTrack
			&& !sequenceTrack && !deformTrack && !inheritTrack && !attachmentTrack
			&& !colorTrack && !rgbTrack && !alphaTrack && !twoColorTrack ? canvas.selProp : null,
			selected && timeline instanceof BoneTimeline2);
		configureColorKeyEditor(twoColorTrack);
		keyValueHost.clearChildren();
		keyValueHost.add(eventTrack ? eventKeyEditor : drawOrderTrack ? drawOrderKeyEditor
			: ikTrack ? ikKeyEditor
			: transformTrack ? transformKeyEditor
			: pathValueTrack ? pathValueKeyEditor
			: pathMixTrack ? pathMixKeyEditor
			: physicsResetTrack ? physicsResetKeyEditor
			: physicsValueTrack ? physicsValueKeyEditor
			: sliderTrack ? physicsValueKeyEditor
			: sequenceTrack ? sequenceKeyEditor
			: deformTrack ? deformKeyEditor
			: inheritTrack ? inheritKeyEditor
			: attachmentTrack ? attachmentKeyEditor
			: twoColorTrack ? twoColorKeyEditor : colorTrack || rgbTrack ? colorKeyEditor
			: alphaTrack ? alphaKeyEditor : boneKeyEditor).left();
		keyCurveHost.clearChildren();
		if (!drawOrderTrack && !inheritTrack && !attachmentTrack && !eventTrack && !physicsResetTrack
			&& !sequenceTrack)
			keyCurveHost.add(curveEditor).left();

		if (!selected) {
			keySelectionLabel.setText(hiddenSelection ? "所选关键帧已被筛选隐藏" : "未选择关键帧");
			keyTimeField.setText("");
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyColorField.setText("");
			keyDarkColorField.setText("");
			keyAlphaField.setText("");
			keyIkMixField.setText("");
			keyIkSoftnessField.setText("");
			for (TextField field : transformKeyFields()) field.setText("");
			keyPathValueField.setText("");
			for (TextField field : pathMixKeyFields()) field.setText("");
			keyPhysicsValueField.setText("");
			keySequenceIndexField.setText("");
			keySequenceDelayField.setText("");
			ikOptionsSummaryLabel.setText("");
			deformSummaryLabel.setText("");
			deformDraftOffsets = null;
			drawOrderSummaryLabel.setText("Setup 顺序");
			keyCurveStatus.setText("-");
		} else if (drawOrderTrack) {
			int[] order;
			int slotCount;
			if (timeline instanceof DrawOrderFolderTimeline folder) {
				order = animEditor.drawOrderFolderAt(folder, frame);
				slotCount = folder.getSlots().length;
				keySelectionLabel.setText(drawOrderFolderLabel(folder));
			} else {
				order = animEditor.drawOrderAt((DrawOrderTimeline)timeline, frame);
				slotCount = doc.data == null ? 0 : doc.data.getSlots().size;
				keySelectionLabel.setText("绘制顺序");
			}
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			drawOrderSummaryLabel.setText(drawOrderSummary(order, slotCount));
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText("离散");
			linearBtn.setChecked(false);
			steppedBtn.setChecked(false);
			bezierBtn.setChecked(false);
		} else if (ikTrack) {
			IkConstraintTimeline ikTimeline = (IkConstraintTimeline)timeline;
			AnimationEditor.IkKeyframe value = animEditor.ikAt(ikTimeline, frame);
			IkConstraintData constraint = canvas.selectedIkConstraintData();
			keySelectionLabel.setText("IK · " + (constraint == null ? "?" : constraint.getName()));
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyIkMixField.setText(UiNumbers.format(value.mix()));
			keyIkSoftnessField.setText(UiNumbers.format(value.softness()));
			ikOptionsSummaryLabel.setText(ikOptionsSummary(value));
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (transformTrack) {
			TransformConstraintTimeline transformTimeline = (TransformConstraintTimeline)timeline;
			AnimationEditor.TransformKeyframe value = animEditor.transformAt(transformTimeline, frame);
			TransformConstraintData constraint = canvas.selectedTransformConstraintData();
			keySelectionLabel.setText("Transform · " + (constraint == null ? "?" : constraint.getName()));
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			setTransformKeyFields(value);
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (pathValueTrack) {
			AnimationEditor.PathProp prop = pathPropOf(timeline);
			PathConstraintData constraint = canvas.selectedPathConstraintData();
			keySelectionLabel.setText("Path · " + (constraint == null ? "?" : constraint.getName())
				+ " · " + pathPropLabel(prop));
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyPathValueLabel.setText(pathPropLabel(prop));
			keyPathValueField.setText(UiNumbers.format(animEditor.pathValueAt(timeline, frame)));
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (pathMixTrack) {
			PathConstraintData constraint = canvas.selectedPathConstraintData();
			keySelectionLabel.setText("Path · " + (constraint == null ? "?" : constraint.getName()) + " · 混合");
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			setPathMixKeyFields(animEditor.pathMixAt((PathConstraintMixTimeline)timeline, frame));
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (physicsResetTrack) {
			keySelectionLabel.setText(canvas.selectedPhysicsLabel() + " · 重置");
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyCurveStatus.setText("离散");
			linearBtn.setChecked(false);
			steppedBtn.setChecked(false);
			bezierBtn.setChecked(false);
		} else if (physicsValueTrack) {
			keySelectionLabel.setText(canvas.selectedPhysicsLabel() + " · " + physicsPropLabel(physicsProp));
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyPhysicsValueLabel.setText(physicsPropLabel(physicsProp));
			keyPhysicsValueField.setText(UiNumbers.format(animEditor.physicsValueAt(timeline, frame)));
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (sliderTrack) {
			SliderData constraint = canvas.selectedSliderConstraintData();
			keySelectionLabel.setText("Slider · " + (constraint == null ? "?" : constraint.getName())
				+ " · " + sliderPropLabel(sliderProp));
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyPhysicsValueLabel.setText(sliderPropLabel(sliderProp));
			keyPhysicsValueField.setText(UiNumbers.format(animEditor.sliderValueAt(timeline, frame)));
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (eventTrack) {
			EventTimeline events = (EventTimeline)timeline;
			Event event = animEditor.eventAt(events, frame);
			preferredEventDefinitionName = event.getData().getName();
			keySelectionLabel.setText("事件" + eventOccurrenceLabel(events, frame) + " · " + event.getData().getName());
			keyTimeField.setText(UiNumbers.format(event.getTime()));
			keyEventBox.setItems(eventDefinitionNames());
			keyEventBox.setSelected(event.getData().getName());
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText("离散");
			linearBtn.setChecked(false);
			steppedBtn.setChecked(false);
			bezierBtn.setChecked(false);
		} else if (inheritTrack) {
			InheritTimeline inheritTimeline = (InheritTimeline)timeline;
			Inherit inherit = animEditor.inheritAt(inheritTimeline, frame);
			keySelectionLabel.setText(canvas.selBoneName + " · 继承");
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyInheritBox.setItems(inheritLabels());
			keyInheritBox.setSelected(PropertiesPanel.inheritLabel(inherit));
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText("离散");
			linearBtn.setChecked(false);
			steppedBtn.setChecked(false);
			bezierBtn.setChecked(false);
		} else if (attachmentTrack) {
			AttachmentTimeline attachmentTimeline = (AttachmentTimeline)timeline;
			String attachmentName = animEditor.attachmentName(attachmentTimeline, frame);
			keySelectionLabel.setText(canvas.selSlotName + " · 附件");
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyAttachmentBox.setItems(attachmentOptions(attachmentTimeline.getSlotIndex(), attachmentName));
			keyAttachmentBox.setSelected(attachmentName == null ? NONE_ATTACHMENT : attachmentName);
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText("离散");
			linearBtn.setChecked(false);
			steppedBtn.setChecked(false);
			bezierBtn.setChecked(false);
		} else if (sequenceTrack) {
			SequenceTimeline sequenceTimeline = (SequenceTimeline)timeline;
			AnimationEditor.SequenceKeyframe value = animEditor.sequenceAt(sequenceTimeline, frame);
			keySelectionLabel.setText(canvas.selectedAttachmentTarget().fullLabel("Sequence"));
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keySequenceModeBox.setItems(sequenceModeNames());
			keySequenceModeBox.setSelected(value.mode().name());
			keySequenceIndexField.setText(Integer.toString(value.index()));
			keySequenceDelayField.setText(UiNumbers.format(value.delay()));
			keyCurveStatus.setText("Discrete");
			linearBtn.setChecked(false);
			steppedBtn.setChecked(false);
			bezierBtn.setChecked(false);
		} else if (deformTrack) {
			DeformTimeline deform = (DeformTimeline)timeline;
			deformDraftOffsets = animEditor.deformOffsetsAt(deform, frame);
			keySelectionLabel.setText(canvas.selectedAttachmentTarget().fullLabel("Deform"));
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			deformSummaryLabel.setText(deformSummary(deform.getAttachment(), deformDraftOffsets));
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (twoColorTrack) {
			Color light = twoColorLightAt(timeline, frame);
			Color dark = twoColorDarkAt(timeline, frame);
			boolean hasAlpha = animEditor.twoColorHasAlpha(timeline);
			keySelectionLabel.setText(canvas.selSlotName + " · 双色");
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyColorField.setMessageText(hasAlpha ? "#RRGGBBAA" : "#RRGGBB");
			keyColorField.setMaxLength(hasAlpha ? 9 : 7);
			keyColorField.setText(hasAlpha ? UiColors.format(light) : UiColors.formatRgb(light));
			keyDarkColorField.setText(UiColors.formatRgb(dark));
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (colorTrack) {
			RGBATimeline colorTimeline = (RGBATimeline)timeline;
			keySelectionLabel.setText(canvas.selSlotName + " · 颜色");
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyColorField.setMessageText("#RRGGBBAA");
			keyColorField.setMaxLength(9);
			keyColorField.setText(UiColors.format(colorAt(colorTimeline, frame)));
			keyDarkColorField.setText("");
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (rgbTrack) {
			RGBTimeline colorTimeline = (RGBTimeline)timeline;
			keySelectionLabel.setText(canvas.selSlotName + " · RGB");
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyColorField.setMessageText("#RRGGBB");
			keyColorField.setMaxLength(7);
			keyColorField.setText(UiColors.formatRgb(rgbAt(colorTimeline, frame)));
			keyDarkColorField.setText("");
			keyAlphaField.setText("");
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else if (alphaTrack) {
			AlphaTimeline alphaTimeline = (AlphaTimeline)timeline;
			keySelectionLabel.setText(canvas.selSlotName + " · 透明度");
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyAlphaField.setText(UiNumbers.format(animEditor.alphaValue(alphaTimeline, frame)));
			keyColorField.setText("");
			keyDarkColorField.setText("");
			keyValue1Field.setText("");
			keyValue2Field.setText("");
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		} else {
			keySelectionLabel.setText(canvas.selBoneName + " · " + propLabel(canvas.selProp));
			keyTimeField.setText(UiNumbers.format(animEditor.frameTime(timeline, frame)));
			keyValue1Field.setText(UiNumbers.format(animEditor.frameValue(timeline, frame, 0)));
			keyValue2Field.setText(timeline instanceof BoneTimeline2
				? UiNumbers.format(animEditor.frameValue(timeline, frame, 1)) : "-");
			keyCurveStatus.setText(terminal ? "末帧" : selectedCurve >= Animation.CurveTimeline.BEZIER ? "贝塞尔"
				: selectedCurve == Animation.CurveTimeline.STEPPED ? "步进" : "线性");
			linearBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.LINEAR);
			steppedBtn.setChecked(!terminal && selectedCurve == Animation.CurveTimeline.STEPPED);
			bezierBtn.setChecked(!terminal && selectedCurve >= Animation.CurveTimeline.BEZIER);
		}
		if (selected && canvas.selectionCount() > 1)
			keySelectionLabel.setText(canvas.selectionCount() + " 个关键帧 · " + keySelectionLabel.getText());

		keyTimeField.setDisabled(!editable);
		keyEventBox.setDisabled(!editable || !eventTrack);
		eventPayloadBtn.setDisabled(!editable || !eventTrack);
		drawOrderEditBtn.setDisabled(!editable || !drawOrderTrack);
		keyInheritBox.setDisabled(!editable || !inheritTrack);
		keyAttachmentBox.setDisabled(!editable || !attachmentTrack);
		keyColorField.setDisabled(!editable || (!colorTrack && !rgbTrack && !twoColorTrack));
		keyDarkColorField.setDisabled(!editable || !twoColorTrack);
		keyAlphaField.setDisabled(!editable || !alphaTrack);
		keyIkMixField.setDisabled(!editable || !ikTrack);
		keyIkSoftnessField.setDisabled(!editable || !ikTrack);
		ikOptionsBtn.setDisabled(!editable || !ikTrack);
		deformEditBtn.setDisabled(!editable || !deformTrack);
		for (TextField field : transformKeyFields()) field.setDisabled(!editable || !transformTrack);
		keyPathValueField.setDisabled(!editable || !pathValueTrack);
		for (TextField field : pathMixKeyFields()) field.setDisabled(!editable || !pathMixTrack);
		keyPhysicsValueField.setDisabled(!editable || !physicsValueTrack && !sliderTrack);
		keySequenceModeBox.setDisabled(!editable || !sequenceTrack);
		keySequenceIndexField.setDisabled(!editable || !sequenceTrack);
		keySequenceDelayField.setDisabled(!editable || !sequenceTrack);
		keyValue1Field.setDisabled(!editable || eventTrack || drawOrderTrack || inheritTrack
			|| colorTrack || rgbTrack || alphaTrack
			|| twoColorTrack || ikTrack || transformTrack || pathValueTrack || pathMixTrack
			|| physicsResetTrack || physicsValueTrack || sliderTrack || sequenceTrack || deformTrack
			|| attachmentTrack);
		keyValue2Field.setDisabled(!editable || !(timeline instanceof BoneTimeline2));
		linearBtn.setDisabled(!editable || eventTrack || drawOrderTrack || inheritTrack || attachmentTrack
			|| sequenceTrack
			|| physicsResetTrack || terminal);
		steppedBtn.setDisabled(!editable || eventTrack || drawOrderTrack || inheritTrack || attachmentTrack
			|| sequenceTrack
			|| physicsResetTrack || terminal);
		bezierBtn.setDisabled(!editable || eventTrack || drawOrderTrack || inheritTrack || attachmentTrack
			|| sequenceTrack
			|| physicsResetTrack || terminal);
		graphBtn.setDisabled(currentAnim() == null);
		applyKeyBtn.setDisabled(true);
		deleteKeyBtn.setDisabled(!editable);
		copyKeyBtn.setDisabled(!selected);
		boolean compatibleClipboard = eventTrack ? copiedEventKey && copiedEventDefinition != null
			&& doc.data != null && doc.data.getEvents().contains(copiedEventDefinition, true)
			: drawOrderTrack ? copiedDrawOrderKey
				&& (timeline instanceof DrawOrderFolderTimeline folder
					? java.util.Arrays.equals(copiedDrawOrderFolderSlots, folder.getSlots())
					: copiedDrawOrderFolderSlots == null)
			: inheritTrack ? copiedInheritKey && copiedInherit != null
			: attachmentTrack ? copiedAttachmentKey
			: ikTrack ? copiedIkKey && copiedIk != null && copiedCurve != null
			: transformTrack ? copiedTransformKey && copiedTransform != null && copiedCurve != null
			: pathValueTrack || pathMixTrack ? copiedPathKey && copiedPathProp == pathPropOf(timeline)
				&& copiedCurve != null && (pathMixTrack ? copiedPathMix != null : copiedPathMix == null)
			: physicsResetTrack ? copiedPhysicsKey && copiedPhysicsProp == AnimationEditor.PhysicsProp.RESET
			: physicsValueTrack ? copiedPhysicsKey && copiedPhysicsProp == physicsProp && copiedCurve != null
			: sliderTrack ? copiedSliderKey && copiedSliderProp == sliderProp && copiedCurve != null
			: sequenceTrack ? copiedSequenceKey && copiedSequence != null
			: deformTrack ? copiedDeformKey && copiedDeformOffsets != null && copiedCurve != null
				&& copiedDeformOffsets.length == animEditor.deformLength(((DeformTimeline)timeline).getAttachment())
			: colorTrack ? copiedColorKey && copiedCurve != null
			: rgbTrack ? copiedRgbKey && copiedCurve != null
			: alphaTrack ? copiedAlphaKey && copiedCurve != null
			: twoColorTrack ? copiedTwoColorKey && copiedCurve != null
				&& copiedTwoColorHasAlpha == animEditor.twoColorHasAlpha(timeline)
			: !copiedEventKey && !copiedDrawOrderKey && !copiedInheritKey && !copiedAttachmentKey
				&& !copiedColorKey && !copiedRgbKey
				&& !copiedAlphaKey && !copiedTwoColorKey && !copiedIkKey && !copiedTransformKey
				&& !copiedPathKey && !copiedPhysicsKey && !copiedSliderKey && !copiedSequenceKey && !copiedDeformKey
				&& copiedProp != null && copiedProp == canvas.selProp && copiedCurve != null;
		boolean blockClipboard = animEditor.canPasteKeyframes(copiedKeyframeBlock);
		pasteKeyBtn.setDisabled(currentAnim() == null || !blockClipboard && (!editable || !compatibleClipboard));
		if (!selected) {
			linearBtn.setChecked(false);
			steppedBtn.setChecked(false);
			bezierBtn.setChecked(false);
		}
		refreshCurveButtonStyles();
	}

	private void refreshCurveButtonStyles() {
		linearBtn.setStyle(linearBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
		steppedBtn.setStyle(steppedBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
		bezierBtn.setStyle(bezierBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
	}

	private KeyframeDraft currentKeyframeDraft() {
		Animation.Timeline timeline = canvas == null ? null : canvas.selectedTimeline();
		int frame = timeline == null ? -1 : canvas.selectedFrame(timeline);
		if (frame < 0) return null;
		String time = keyTimeField.getText();
		if (timeline instanceof DrawOrderTimeline drawOrder)
			return drawOrderKeyframeDraft(time, animEditor.drawOrderAt(drawOrder, frame));
		if (timeline instanceof DrawOrderFolderTimeline folder)
			return drawOrderKeyframeDraft(time, animEditor.drawOrderFolderAt(folder, frame));
		if (timeline instanceof EventTimeline)
			return eventKeyframeDraft(time, keyEventBox.getSelected());
		if (timeline instanceof InheritTimeline)
			return inheritKeyframeDraft(time, keyInheritBox.getSelected());
		if (timeline instanceof AttachmentTimeline)
			return attachmentKeyframeDraft(time, keyAttachmentBox.getSelected());
		if (timeline instanceof SequenceTimeline)
			return sequenceKeyframeDraft(time, keySequenceModeBox.getSelected(), keySequenceIndexField.getText(),
				keySequenceDelayField.getText());
		boolean terminal = frame == timeline.getFrameCount() - 1;
		int curve = terminal ? NO_DRAFT_CURVE : selectedDraftCurve();
		if (timeline instanceof IkConstraintTimeline ik) {
			AnimationEditor.IkKeyframe value = animEditor.ikAt(ik, frame);
			return ikKeyframeDraft(time, keyIkMixField.getText(), keyIkSoftnessField.getText(),
				value.bendDirection(), value.compress(), value.stretch(), curve);
		}
		if (timeline instanceof TransformConstraintTimeline)
			return transformKeyframeDraft(time, keyTransformRotateField.getText(), keyTransformXField.getText(),
				keyTransformYField.getText(), keyTransformScaleXField.getText(), keyTransformScaleYField.getText(),
				keyTransformShearYField.getText(), curve);
		if (timeline instanceof PathConstraintPositionTimeline
			|| timeline instanceof PathConstraintSpacingTimeline)
			return pathValueKeyframeDraft(time, pathPropOf(timeline), keyPathValueField.getText(), curve);
		if (timeline instanceof PathConstraintMixTimeline)
			return pathMixKeyframeDraft(time, keyPathRotateField.getText(), keyPathXField.getText(),
				keyPathYField.getText(), curve);
		AnimationEditor.PhysicsProp physicsProp = animEditor.physicsPropOf(timeline);
		if (physicsProp == AnimationEditor.PhysicsProp.RESET)
			return physicsResetKeyframeDraft(time);
		if (physicsProp != null)
			return physicsValueKeyframeDraft(time, physicsProp, keyPhysicsValueField.getText(), curve);
		AnimationEditor.SliderProp sliderProp = sliderPropOf(timeline);
		if (sliderProp != null)
			return sliderValueKeyframeDraft(time, sliderProp, keyPhysicsValueField.getText(), curve);
		if (timeline instanceof DeformTimeline)
			return deformKeyframeDraft(time, deformDraftOffsets, curve);
		if (timeline instanceof RGBATimeline)
			return colorKeyframeDraft(time, keyColorField.getText(), false, curve);
		if (timeline instanceof RGBTimeline)
			return colorKeyframeDraft(time, keyColorField.getText(), true, curve);
		if (timeline instanceof AlphaTimeline)
			return alphaKeyframeDraft(time, keyAlphaField.getText(), curve);
		if (animEditor.isTwoColorTimeline(timeline))
			return twoColorKeyframeDraft(time, keyColorField.getText(),
				!animEditor.twoColorHasAlpha(timeline), keyDarkColorField.getText(), curve);
		return boneKeyframeDraft(time, keyValue1Field.getText(), keyValue2Field.getText(),
			timeline instanceof BoneTimeline2, curve);
	}

	private int selectedDraftCurve() {
		if (steppedBtn.isChecked()) return Animation.CurveTimeline.STEPPED;
		if (linearBtn.isChecked()) return Animation.CurveTimeline.LINEAR;
		if (bezierBtn.isChecked()) return Animation.CurveTimeline.BEZIER;
		return NO_DRAFT_CURVE;
	}

	private void refreshKeyApplyState() {
		if (applyKeyBtn == null) return;
		KeyframeDraft current = currentKeyframeDraft();
		applyKeyBtn.setDisabled(current == null || keyframeBaseline == null || keyframeBaseline.equals(current));
	}

	static KeyframeDraft eventKeyframeDraft(String time, String definition) {
		return new KeyframeDraft("event", UiDrafts.number(time), UiDrafts.text(definition), null, NO_DRAFT_CURVE);
	}

	static KeyframeDraft attachmentKeyframeDraft(String time, String attachment) {
		return new KeyframeDraft("attachment", UiDrafts.number(time), UiDrafts.text(attachment), null,
			NO_DRAFT_CURVE);
	}

	static KeyframeDraft inheritKeyframeDraft(String time, String inherit) {
		return new KeyframeDraft("inherit", UiDrafts.number(time), UiDrafts.text(inherit), null,
			NO_DRAFT_CURVE);
	}

	static KeyframeDraft sequenceKeyframeDraft(String time, String mode, String index, String delay) {
		return new KeyframeDraft("sequence", UiDrafts.number(time), UiDrafts.text(mode) + ":" + UiDrafts.number(index),
			UiDrafts.number(delay), NO_DRAFT_CURVE);
	}

	static String[] sequenceModeNames() {
		SequenceMode[] modes = SequenceMode.values();
		String[] names = new String[modes.length];
		for (int index = 0; index < modes.length; index++) names[index] = modes[index].name();
		return names;
	}

	static SequenceMode sequenceMode(String name) {
		if (name == null) return null;
		for (SequenceMode mode : SequenceMode.values()) if (mode.name().equals(name)) return mode;
		return null;
	}

	static int parseSequenceIndex(String text) {
		float value = UiNumbers.parseFinite(text);
		int index = (int)value;
		if (Float.compare(value, index) != 0 || index < 0 || index > 0xfffff)
			throw new IllegalArgumentException("Sequence index must be an integer from 0 to 1048575");
		return index;
	}

	static KeyframeDraft drawOrderKeyframeDraft(String time, int[] order) {
		return new KeyframeDraft("drawOrder", UiDrafts.number(time),
			order == null ? "setup" : java.util.Arrays.toString(order), null, NO_DRAFT_CURVE);
	}

	static KeyframeDraft ikKeyframeDraft(String time, String mix, String softness, int bendDirection,
		boolean compress, boolean stretch, int curve) {
		return new KeyframeDraft("ik", UiDrafts.number(time), UiDrafts.number(mix),
			UiDrafts.number(softness) + ":" + bendDirection + ":" + compress + ":" + stretch, curve);
	}

	static KeyframeDraft transformKeyframeDraft(String time, String rotate, String x, String y,
		String scaleX, String scaleY, String shearY, int curve) {
		return new KeyframeDraft("transform", UiDrafts.number(time), UiDrafts.number(rotate),
			UiDrafts.number(x) + ":" + UiDrafts.number(y) + ":" + UiDrafts.number(scaleX) + ":"
				+ UiDrafts.number(scaleY) + ":" + UiDrafts.number(shearY), curve);
	}

	static KeyframeDraft pathValueKeyframeDraft(String time, AnimationEditor.PathProp prop, String value,
		int curve) {
		return new KeyframeDraft("path-" + (prop == null ? "?" : prop.name().toLowerCase(java.util.Locale.ROOT)),
			UiDrafts.number(time), UiDrafts.number(value), null, curve);
	}

	static KeyframeDraft pathMixKeyframeDraft(String time, String rotate, String x, String y, int curve) {
		return new KeyframeDraft("path-mix", UiDrafts.number(time), UiDrafts.number(rotate),
			UiDrafts.number(x) + ":" + UiDrafts.number(y), curve);
	}

	static KeyframeDraft physicsResetKeyframeDraft(String time) {
		return new KeyframeDraft("physics-reset", UiDrafts.number(time), null, null, NO_DRAFT_CURVE);
	}

	static KeyframeDraft physicsValueKeyframeDraft(String time, AnimationEditor.PhysicsProp prop, String value,
		int curve) {
		return new KeyframeDraft("physics-" + (prop == null ? "?" : prop.name().toLowerCase(java.util.Locale.ROOT)),
			UiDrafts.number(time), UiDrafts.number(value), null, curve);
	}

	static KeyframeDraft sliderValueKeyframeDraft(String time, AnimationEditor.SliderProp prop, String value,
		int curve) {
		return new KeyframeDraft("slider-" + (prop == null ? "?" : prop.name().toLowerCase(java.util.Locale.ROOT)),
			UiDrafts.number(time), UiDrafts.number(value), null, curve);
	}

	static AnimationEditor.SliderProp sliderPropOf(Animation.Timeline timeline) {
		if (timeline instanceof SliderTimeline) return AnimationEditor.SliderProp.TIME;
		if (timeline instanceof SliderMixTimeline) return AnimationEditor.SliderProp.MIX;
		return null;
	}

	static String sliderPropLabel(AnimationEditor.SliderProp prop) {
		if (prop == null) return "Slider";
		return switch (prop) {
		case TIME -> "时间";
		case MIX -> "混合";
		};
	}

	static boolean sliderPropVisible(SliderData slider, AnimationEditor.SliderProp prop) {
		return slider != null && prop != null
			&& (prop != AnimationEditor.SliderProp.TIME || slider.getBone() == null);
	}

	static AnimationEditor.PathProp pathPropOf(Animation.Timeline timeline) {
		if (timeline instanceof PathConstraintPositionTimeline) return AnimationEditor.PathProp.POSITION;
		if (timeline instanceof PathConstraintSpacingTimeline) return AnimationEditor.PathProp.SPACING;
		if (timeline instanceof PathConstraintMixTimeline) return AnimationEditor.PathProp.MIX;
		return null;
	}

	static String pathPropLabel(AnimationEditor.PathProp prop) {
		if (prop == null) return "Path";
		return switch (prop) {
		case POSITION -> "位置";
		case SPACING -> "间距";
		case MIX -> "混合";
		};
	}

	static String physicsPropLabel(AnimationEditor.PhysicsProp prop) {
		if (prop == null) return "Physics";
		return switch (prop) {
		case RESET -> "重置";
		case INERTIA -> "惯性";
		case STRENGTH -> "强度";
		case DAMPING -> "阻尼";
		case MASS -> "质量";
		case WIND -> "风力";
		case GRAVITY -> "重力";
		case MIX -> "混合";
		};
	}

	static boolean physicsPropertyIsGlobal(PhysicsConstraintData constraint, AnimationEditor.PhysicsProp prop) {
		return AnimationEditor.physicsPropertyIsGlobal(constraint, prop);
	}

	static boolean physicsAffectsBone(PhysicsConstraintData constraint, BoneData bone) {
		return constraint != null && bone != null && constraint.getBone() == bone;
	}

	static boolean sliderAffectsBone(SliderData constraint, BoneData bone) {
		return constraint != null && bone != null && constraint.getBone() == bone;
	}

	static boolean sliderAffectsBone(SliderData constraint, SkeletonData data, BoneData bone) {
		return constraint != null && data != null && bone != null
			&& SkeletonDocument.constraintSelectionBoneData(constraint, data).contains(bone, true);
	}

	static boolean curveBlankPressClearsSelection(boolean additive) {
		return !additive;
	}

	static float curveContentHeight(float minimumPlotHeight, float plotPadding, float hierarchyHeight) {
		if (!Float.isFinite(minimumPlotHeight) || minimumPlotHeight < 0f
			|| !Float.isFinite(plotPadding) || plotPadding < 0f
			|| !Float.isFinite(hierarchyHeight) || hierarchyHeight < 0f)
			throw new IllegalArgumentException("曲线视图尺寸无效");
		return Math.max(minimumPlotHeight + plotPadding * 2f, hierarchyHeight);
	}

	static float physicsPoseValue(PhysicsConstraintPose pose, AnimationEditor.PhysicsProp prop) {
		if (pose == null || prop == null || prop == AnimationEditor.PhysicsProp.RESET)
			throw new IllegalArgumentException("Physics 数值属性无效");
		return switch (prop) {
		case INERTIA -> pose.getInertia();
		case STRENGTH -> pose.getStrength();
		case DAMPING -> pose.getDamping();
		case MASS -> 1f / pose.getMassInverse();
		case WIND -> pose.getWind();
		case GRAVITY -> pose.getGravity();
		case MIX -> pose.getMix();
		case RESET -> throw new IllegalArgumentException("Physics Reset 没有数值");
		};
	}

	static String ikBendLabel(int bendDirection) {
		return bendDirection < 0 ? "反向" : "正向";
	}

	static int ikBendDirection(String label) {
		return "反向".equals(label) ? -1 : 1;
	}

	static String ikOptionsSummary(AnimationEditor.IkKeyframe value) {
		if (value == null) return "";
		StringBuilder summary = new StringBuilder(ikBendLabel(value.bendDirection()));
		if (value.compress()) summary.append(" · 压缩");
		if (value.stretch()) summary.append(" · 拉伸");
		return summary.toString();
	}

	static boolean ikAffectsBone(IkConstraintData constraint, BoneData bone) {
		return constraint != null && bone != null
			&& (constraint.getTarget() == bone || constraint.getBones().contains(bone, true));
	}

	static boolean transformAffectsBone(TransformConstraintData constraint, BoneData bone) {
		return constraint != null && bone != null
			&& (constraint.getSource() == bone || constraint.getBones().contains(bone, true));
	}

	static boolean pathAffectsBone(PathConstraintData constraint, BoneData bone) {
		return constraint != null && bone != null && (constraint.getBones().contains(bone, true)
			|| constraint.getSlot() != null && constraint.getSlot().getBoneData() == bone);
	}

	static String drawOrderSummary(int[] order, int slotCount) {
		if (order == null) return "Setup 顺序";
		int moved = 0;
		for (int position = 0; position < order.length; position++)
			if (order[position] != position) moved++;
		return "已调整 " + moved + "/" + slotCount + " 个槽";
	}

	private String drawOrderFolderLabel(DrawOrderFolderTimeline folder) {
		if (folder == null || doc.data == null) return "绘制顺序文件夹";
		StringBuilder label = new StringBuilder("绘制顺序 · 局部 [");
		int[] slots = folder.getSlots();
		for (int index = 0; index < slots.length; index++) {
			if (index > 0) label.append(", ");
			int slot = slots[index];
			label.append(slot >= 0 && slot < doc.data.getSlots().size
				? doc.data.getSlots().get(slot).getName() : "#" + slot);
			if (label.length() > 72 && index < slots.length - 1) { label.append("…"); break; }
		}
		return label.append(']').toString();
	}

	private int[] currentRuntimeDrawOrder() {
		if (doc.skeleton == null || doc.data == null) return null;
		Array<Slot> slots = doc.skeleton.getDrawOrder().getAppliedPose();
		if (slots.size != doc.data.getSlots().size)
			throw new IllegalStateException("当前绘制顺序与槽数量不一致");
		int[] order = new int[slots.size];
		for (int position = 0; position < slots.size; position++)
			order[position] = slots.get(position).getData().getIndex();
		return order;
	}

	private int[] currentRuntimeDrawOrderFolder(DrawOrderFolderTimeline folder) {
		if (folder == null || doc.skeleton == null || doc.data == null) return null;
		int[] controlled = folder.getSlots();
		int[] order = new int[controlled.length];
		int found = 0;
		for (Slot slot : doc.skeleton.getDrawOrder().getAppliedPose()) {
			int slotIndex = slot.getData().getIndex();
			for (int local = 0; local < controlled.length; local++) {
				if (controlled[local] != slotIndex) continue;
				order[found++] = local;
				break;
			}
		}
		if (found != controlled.length)
			throw new IllegalStateException("当前局部绘制顺序与槽集合不一致");
		return order;
	}

	static String[] inheritLabels() {
		Inherit[] values = Inherit.values();
		String[] labels = new String[values.length];
		for (int index = 0; index < values.length; index++)
			labels[index] = PropertiesPanel.localizedInheritLabel(values[index]);
		return labels;
	}

	static KeyframeDraft colorKeyframeDraft(String time, String color, boolean rgbOnly, int curve) {
		return new KeyframeDraft(rgbOnly ? "rgb" : "rgba", UiDrafts.number(time),
			UiDrafts.color(color, rgbOnly), null, curve);
	}

	static KeyframeDraft alphaKeyframeDraft(String time, String alpha, int curve) {
		return new KeyframeDraft("alpha", UiDrafts.number(time), UiDrafts.number(alpha), null, curve);
	}

	static KeyframeDraft twoColorKeyframeDraft(String time, String light, boolean lightRgbOnly, String dark,
		int curve) {
		return new KeyframeDraft(lightRgbOnly ? "rgb2" : "rgba2", UiDrafts.number(time),
			UiDrafts.color(light, lightRgbOnly), UiDrafts.color(dark, true), curve);
	}

	static KeyframeDraft boneKeyframeDraft(String time, String value1, String value2, boolean twoValues,
		int curve) {
		return new KeyframeDraft(twoValues ? "bone2" : "bone1", UiDrafts.number(time),
			UiDrafts.number(value1), twoValues ? UiDrafts.number(value2) : null, curve);
	}

	private void applyRequestedCurve(Animation.Timeline timeline, float time) {
		int frame = animEditor.findFrame(timeline, time);
		if (frame < 0 || frame >= timeline.getFrameCount() - 1) return;
		if (steppedBtn.isChecked()) animEditor.setCurve(timeline, frame, true);
		else if (linearBtn.isChecked()) animEditor.setCurve(timeline, frame, false);
	}

	static KeyframeDraft deformKeyframeDraft(String time, float[] offsets, int curve) {
		return new KeyframeDraft("deform", UiDrafts.number(time),
			offsets == null ? null : java.util.Arrays.toString(offsets), null, curve);
	}

	static boolean ownsDeformTimeline(VertexAttachment attachment) {
		return AnimationEditor.ownsAttachmentTimeline(attachment);
	}

	static boolean ownsSequenceTimeline(Attachment attachment) {
		return attachment instanceof HasSequence && AnimationEditor.ownsAttachmentTimeline(attachment);
	}

	static float[] parseDeformOffsets(String text, int expectedLength) {
		if (expectedLength <= 0 || (expectedLength & 1) != 0)
			throw new IllegalArgumentException("Deform 顶点数量无效");
		String trimmed = text == null ? "" : text.trim();
		String[] tokens = trimmed.isEmpty() ? new String[0] : trimmed.split("[\\s,;]+");
		if (tokens.length != expectedLength)
			throw new IllegalArgumentException("需要 " + expectedLength + " 个偏移数值，当前为 " + tokens.length + " 个");
		float[] offsets = new float[expectedLength];
		for (int index = 0; index < tokens.length; index++) {
			try {
				offsets[index] = Float.parseFloat(tokens[index]);
			} catch (NumberFormatException error) {
				throw new IllegalArgumentException("第 " + (index + 1) + " 个偏移不是有效数字", error);
			}
			if (!Float.isFinite(offsets[index]))
				throw new IllegalArgumentException("第 " + (index + 1) + " 个偏移必须是有限数值");
		}
		return offsets;
	}

	static String formatDeformOffsets(float[] offsets) {
		if (offsets == null) return "";
		StringBuilder text = new StringBuilder(offsets.length * 8);
		for (int index = 0; index < offsets.length; index += 2) {
			if (index > 0) text.append('\n');
			text.append(UiNumbers.format(offsets[index])).append(", ")
				.append(UiNumbers.format(offsets[index + 1]));
		}
		return text.toString();
	}

	private String deformSummary(VertexAttachment attachment, float[] offsets) {
		if (attachment == null || offsets == null) return "";
		int changed = 0;
		for (int index = 0; index < offsets.length; index += 2)
			if (offsets[index] != 0f || offsets[index + 1] != 0f) changed++;
		return offsets.length / 2 + " 组坐标，" + changed + " 组已偏移"
			+ (attachment.getBones() == null ? "" : "，加权");
	}

	private void showDeformOffsetsEditor() {
		Stage stage = getStage();
		Animation.Timeline timeline = canvas == null ? null : canvas.selectedTimeline();
		if (stage == null || !(timeline instanceof DeformTimeline deform) || deformDraftOffsets == null) return;
		Window window = kit.window("编辑 Deform 顶点偏移");
		window.setModal(true);
		window.setMovable(true);
		kit.padWindow(window, 10f);
		Label target = new Label(canvas.selectedAttachmentTarget().fullLabel("Deform"), kit.labelStyle);
		target.setWrap(true);
		target.addListener(trackTooltip((x, y) -> canvas.selectedTrackTooltip()));
		target.setEllipsis(true);
		window.add(target).width(420).left().row();
		TextArea values = new TextArea(formatDeformOffsets(deformDraftOffsets), kit.textFieldStyle);
		values.setPrefRows(Math.min(14, Math.max(5, deformDraftOffsets.length / 2)));
		ScrollPane valuesScroll = new ScrollPane(values, kit.scrollStyle);
		valuesScroll.setFadeScrollBars(false);
		window.add(valuesScroll).width(420).height(260).padTop(7).grow().row();
		Label errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(0.95f, 0.45f, 0.38f, 1f);
		window.add(errorLabel).width(420).left().padTop(4).row();
		Table buttons = new Table();
		TextButton zero = new TextButton("全部归零", kit.toolButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		TextButton apply = new TextButton("采用", kit.accentButton);
		zero.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				values.setText(formatDeformOffsets(new float[deformDraftOffsets.length]));
			}
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { window.remove(); }
		});
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				try {
					deformDraftOffsets = parseDeformOffsets(values.getText(), animEditor.deformLength(deform.getAttachment()));
					deformSummaryLabel.setText(deformSummary(deform.getAttachment(), deformDraftOffsets));
					refreshKeyApplyState();
					window.remove();
				} catch (RuntimeException error) {
					errorLabel.setText(error.getMessage());
				}
			}
		});
		buttons.add(zero).padRight(6);
		buttons.add().growX();
		buttons.add(cancel).padRight(5);
		buttons.add(apply);
		window.add(buttons).width(420).growX().padTop(7).row();
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(values);
	}

	private void showBezierEditor() {
		Stage stage = getStage();
		Animation animation = currentAnim();
		Animation.Timeline timeline = canvas == null ? null : canvas.selectedTimeline();
		int frame = timeline == null ? -1 : canvas.selectedFrame(timeline);
		if (stage == null || animation == null || frame < 0 || frame >= timeline.getFrameCount() - 1) return;
		for (int channel = 0; channel < AnimationEditor.curveChannelCount(timeline); channel++) {
			if (animEditor.curveFrameValue(timeline, frame, channel)
				== animEditor.curveFrameValue(timeline, frame + 1, channel)) {
				// The proportional preview cannot describe a return to the same value. Open the value Graph.
				showGraphDetails(animation, timeline, frame);
				return;
			}
		}
		Actor previousKeyboardFocus = stage.getKeyboardFocus();

		String[] channelLabels = curveChannelLabels(timeline);
		AnimationEditor.BezierCurve current;
		try {
			current = animEditor.bezierCurve(timeline, frame, 0);
		} catch (RuntimeException error) {
			showStatus("无法读取贝塞尔曲线：" + error.getMessage());
			return;
		}
		float initialX1 = current == null ? 0.42f : current.cx1();
		float initialY1 = current == null ? 0f : current.cy1();
		float initialX2 = current == null ? 0.58f : current.cx2();
		float initialY2 = current == null ? 1f : current.cy2();
		TextField x1Field = createBezierField(initialX1);
		TextField y1Field = createBezierField(initialY1);
		TextField x2Field = createBezierField(initialX2);
		TextField y2Field = createBezierField(initialY2);
		BezierPreview preview = new BezierPreview(x1Field, y1Field, x2Field, y2Field);

		Window window = kit.window("贝塞尔曲线");
		window.setModal(true);
		kit.padWindow(window, 10f);
		Label scope = new Label("编辑通道", kit.labelStyle);
		scope.setColor(0.62f, 0.68f, 0.78f, 1f);
		SelectBox<String> channelBox = kit.selectBox();
		Array<String> channelItems = new Array<>();
		for (String label : channelLabels) channelItems.add(label);
		if (channelLabels.length > 1) channelItems.add("全部通道");
		channelBox.setItems(channelItems);
		Table channelRow = new Table();
		channelRow.add(scope).padRight(8);
		channelRow.add(channelBox).width(180);
		channelRow.add().growX();
		window.add(channelRow).colspan(4).growX().left().pad(4).row();
		window.add(preview).colspan(4).size(360, 190).pad(4, 4, 8, 4).row();
		window.add(new Label("X1", kit.labelStyle)).right().padRight(4);
		window.add(x1Field).width(92).padRight(12);
		window.add(new Label("Y1", kit.labelStyle)).right().padRight(4);
		window.add(y1Field).width(92).row();
		window.add(new Label("X2", kit.labelStyle)).right().padRight(4).padTop(5);
		window.add(x2Field).width(92).padRight(12).padTop(5);
		window.add(new Label("Y2", kit.labelStyle)).right().padRight(4).padTop(5);
		window.add(y2Field).width(92).padTop(5).row();

		Table presets = new Table();
		TextButton easeIn = new TextButton("缓入", kit.toolButton);
		TextButton smooth = new TextButton("平滑", kit.toolButton);
		TextButton easeOut = new TextButton("缓出", kit.toolButton);
		easeIn.addListener(curvePresetListener(x1Field, y1Field, x2Field, y2Field, 0.42f, 0f, 1f, 1f));
		smooth.addListener(curvePresetListener(x1Field, y1Field, x2Field, y2Field, 0.42f, 0f, 0.58f, 1f));
		easeOut.addListener(curvePresetListener(x1Field, y1Field, x2Field, y2Field, 0f, 0f, 0.58f, 1f));
		presets.add(easeIn).width(72).height(27).padRight(4);
		presets.add(smooth).width(72).height(27).padRight(4);
		presets.add(easeOut).width(72).height(27);
		window.add(presets).colspan(4).left().padTop(9).padBottom(4).row();

		Label errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(4).width(360).left().pad(2, 4, 5, 4).row();
		TextButton apply = new TextButton("应用", kit.accentButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		boolean[] closed = {false};
		channelBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				int channel = channelBox.getSelectedIndex();
				if (channel < 0 || channel >= channelLabels.length) channel = 0;
				try {
					AnimationEditor.BezierCurve selected = animEditor.bezierCurve(timeline, frame, channel);
					x1Field.setText(UiNumbers.format(selected == null ? 0.42f : selected.cx1()));
					y1Field.setText(UiNumbers.format(selected == null ? 0f : selected.cy1()));
					x2Field.setText(UiNumbers.format(selected == null ? 0.58f : selected.cx2()));
					y2Field.setText(UiNumbers.format(selected == null ? 1f : selected.cy2()));
					errorLabel.setText("");
				} catch (RuntimeException error) {
					errorLabel.setText(error.getMessage());
				}
			}
		});
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousKeyboardFocus);
			refreshKeyframeInspector();
		};
		Runnable submit = () -> {
			if (closed[0]) return;
			try {
				float cx1 = parseNumber(x1Field, "X1");
				float cy1 = parseNumber(y1Field, "Y1");
				float cx2 = parseNumber(x2Field, "X2");
				float cy2 = parseNumber(y2Field, "Y2");
				AnimationEditor.TimelineSnapshot before = snapshotSelected(animation, timeline);
				long revision = doc.revision;
				int selectedChannel = channelBox.getSelectedIndex();
				if (selectedChannel >= channelLabels.length)
					animEditor.setBezierCurve(animation, timeline, frame, cx1, cy1, cx2, cy2);
				else
					animEditor.setBezierCurveChannel(animation, timeline, frame,
						Math.max(0, selectedChannel), cx1, cy1, cx2, cy2);
				boolean changed = doc.revision != revision;
				recordTimelineChange("编辑贝塞尔曲线", before);
				close.run();
				if (changed) notifyChanged();
				showInfo(changed ? "贝塞尔曲线已更新" : "贝塞尔曲线未变化");
			} catch (RuntimeException error) {
				errorLabel.setText(error.getMessage());
			}
		};
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (UiDialogs.keyAction(keycode)) {
				case CONFIRM -> { submit.run(); yield true; }
				case CANCEL -> { close.run(); yield true; }
				case NONE -> false;
				};
			}
		});
		Table buttons = new Table();
		buttons.add(apply).width(80).height(28).padRight(6);
		buttons.add(cancel).width(80).height(28);
		window.add(buttons).colspan(4).right().pad(4);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(x1Field);
		x1Field.selectAll();
	}

	/** Full names remain available for canvas rows and inspector labels that use ellipsis. */
	private String selectedTrackTooltip(float x, float y) { return canvas == null ? "" : canvas.selectedTrackTooltip(); }

	private TextTooltip trackTooltip(java.util.function.BiFunction<Float, Float, String> textAt) {
		TextTooltip.TextTooltipStyle style = new TextTooltip.TextTooltipStyle(kit.labelStyle, kit.panelBg);
		style.wrapWidth = 520f;
		TextTooltip tooltip = new TextTooltip("", style) {
			private String shown = "";
			private boolean update(InputEvent event, float x, float y) {
				String text = textAt.apply(x, y);
				if (text == null || text.isBlank()) { shown = ""; hide(); return false; }
				if (!text.equals(shown)) {
					shown = text;
					getActor().setText(text);
					float available = event.getStage() == null ? 520f : Math.max(80f, event.getStage().getWidth() - 40f);
					getContainer().width(Math.min(520f, available));
					getContainer().invalidateHierarchy();
					hide();
					super.enter(event, x, y, -1, null);
				}
				return true;
			}
			@Override public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
				if (pointer == -1 && update(event, x, y)) super.enter(event, x, y, pointer, fromActor);
			}
			@Override public boolean mouseMoved(InputEvent event, float x, float y) {
				return update(event, x, y) && super.mouseMoved(event, x, y);
			}
			@Override public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
				shown = "";
				super.exit(event, x, y, pointer, toActor);
			}
		};
		tooltip.getContainer().pad(8f);
		return tooltip;
	}

	private void showGraphEditor() {
		Stage stage = getStage();
		Animation animation = currentAnim();
		if (stage == null || animation == null) return;
		if (graphWorkspace != null) graphWorkspace.closeWorkspace(false);
		doc.setGraphWorkspaceState(SkeletonDocument.GraphWorkspaceState.empty());
		setTimelineView(true);
		canvas.persistViewState();
		stage.setKeyboardFocus(canvas);
	}

	private void showGraphDetails(Animation animation, Animation.Timeline timeline, int frame) {
		Stage stage = getStage();
		if (stage == null || animation == null || timeline == null || timeline.getFrameCount() == 0
			|| !animation.getTimelines().contains(timeline, true)) return;
		Track track = canvas == null ? null : canvas.curveTrack(timeline);
		String title = track == null ? timeline.getClass().getSimpleName() : canvas.trackLabel(track);
		GraphEditorDialog.show(stage, kit, doc, animEditor, animation, timeline, frame, title,
			curveChannelLabels(timeline), graphHost(animation));
	}

	private GraphEditorDialog.Host graphHost(Animation animation) {
		return graphHost(animation, null);
	}

	private GraphEditorDialog.Host graphHost(Animation animation,
		java.util.function.BiConsumer<Animation.Timeline, Integer> reopenHandler) {
		return new GraphEditorDialog.Host() {
				@Override public AnimationEditor.TimelineSnapshot snapshot(Animation.Timeline target) {
					return snapshotSelected(animation, target);
				}

				@Override public AnimationEditor.TimelineSnapshot[] snapshotCurveMode(Animation.Timeline target) {
					if (target instanceof BoneTimeline bone) {
						AnimationEditor.AxisGroup group = combinedAxisGroup(animEditor.propOf(target));
						if (group != null)
							return snapshotBoneTimelines(animation, bone.getBoneIndex(), axisTimelineProps(group));
					}
					AnimationEditor.TimelineSnapshot snapshot = snapshotSelected(animation, target);
					return snapshot == null ? null : new AnimationEditor.TimelineSnapshot[] {snapshot};
				}

				@Override public void record(String label, AnimationEditor.TimelineSnapshot before) {
					recordTimelineChange(label, before);
				}

				@Override public void recordCurveMode(String label, AnimationEditor.TimelineSnapshot[] before) {
					recordTimelineChanges(label, before);
				}

				@Override public void changed() { notifyChanged(); }

				@Override public void seek(float time) {
					if (doc.scrubTo(time) && onTimeChanged != null) onTimeChanged.run();
				}

				@Override public void reopen(Animation.Timeline target, int targetFrame) {
					Runnable reopen = () -> {
						if (reopenHandler == null) showGraphDetails(animation, target, targetFrame);
						else reopenHandler.accept(target, targetFrame);
					};
					if (Gdx.app == null) reopen.run(); else Gdx.app.postRunnable(reopen);
				}

				@Override public void status(String message, boolean error) {
					if (error) showStatus(message); else showInfo(message);
				}
			};
	}

	private static AnimationEditor.AxisGroup combinedAxisGroup(AnimationEditor.Prop prop) {
		return prop == AnimationEditor.Prop.TRANSLATE ? AnimationEditor.AxisGroup.TRANSLATE
			: prop == AnimationEditor.Prop.SCALE ? AnimationEditor.AxisGroup.SCALE
			: prop == AnimationEditor.Prop.SHEAR ? AnimationEditor.AxisGroup.SHEAR : null;
	}

	private String[] curveChannelLabels(Animation.Timeline timeline) {
		if (timeline == null) return new String[0];
		if (timeline instanceof DeformTimeline) return new String[] {"形状"};
		if (timeline instanceof IkConstraintTimeline) return new String[] {"混合", "柔度"};
		if (timeline instanceof TransformConstraintTimeline)
			return new String[] {"旋转", "X", "Y", "缩放 X", "缩放 Y", "剪切 Y"};
		if (timeline instanceof PathConstraintMixTimeline) return new String[] {"旋转", "X", "Y"};
		if (timeline instanceof RGBATimeline) return new String[] {"R", "G", "B", "A"};
		if (timeline instanceof RGBTimeline) return new String[] {"R", "G", "B"};
		if (timeline instanceof AlphaTimeline) return new String[] {"A"};
		if (timeline instanceof RGBA2Timeline) return new String[] {"浅 R", "浅 G", "浅 B", "浅 A", "暗 R", "暗 G", "暗 B"};
		if (timeline instanceof RGB2Timeline) return new String[] {"浅 R", "浅 G", "浅 B", "暗 R", "暗 G", "暗 B"};
		AnimationEditor.Prop prop = timeline instanceof BoneTimeline ? animEditor.propOf(timeline) : null;
		if (prop != null) {
			int count = AnimationEditor.curveChannelCount(timeline);
			String[] labels = new String[count];
			for (int channel = 0; channel < count; channel++) labels[channel] = valueLabel(prop, channel);
			return labels;
		}
		int count = Math.max(1, AnimationEditor.curveChannelCount(timeline));
		String[] labels = new String[count];
		for (int channel = 0; channel < count; channel++)
			labels[channel] = count == 1 ? "数值" : "通道 " + (channel + 1);
		return labels;
	}

	private void showIkOptionsEditor() {
		Stage stage = getStage();
		Animation animation = currentAnim();
		Animation.Timeline selected = canvas == null ? null : canvas.selectedTimeline();
		int frame = selected == null ? -1 : canvas.selectedFrame(selected);
		if (stage == null || animation == null || !(selected instanceof IkConstraintTimeline timeline)
			|| frame < 0) return;
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		AnimationEditor.IkKeyframe current = animEditor.ikAt(timeline, frame);
		float time = animEditor.frameTime(timeline, frame);

		SelectBox<String> bendBox = kit.selectBox();
		bendBox.setItems("正向", "反向");
		bendBox.setSelected(ikBendLabel(current.bendDirection()));
		TextButton compressButton = new TextButton("", kit.toolButton);
		TextButton stretchButton = new TextButton("", kit.toolButton);
		compressButton.setProgrammaticChangeEvents(false);
		stretchButton.setProgrammaticChangeEvents(false);
		compressButton.setChecked(current.compress());
		stretchButton.setChecked(current.stretch());
		refreshIkToggle(compressButton);
		refreshIkToggle(stretchButton);
		compressButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refreshIkToggle(compressButton); }
		});
		stretchButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refreshIkToggle(stretchButton); }
		});

		IkConstraintData constraint = canvas.selectedIkConstraintData();
		Window window = kit.window("IK 选项 · " + (constraint == null ? "?" : constraint.getName()));
		window.setModal(true);
		kit.padWindow(window, 10f);
		window.add(new Label("弯曲方向", kit.labelStyle)).right().padRight(7);
		window.add(bendBox).width(130).row();
		window.add(new Label("压缩", kit.labelStyle)).right().padRight(7).padTop(6);
		window.add(compressButton).width(130).padTop(6).row();
		window.add(new Label("拉伸", kit.labelStyle)).right().padRight(7).padTop(6);
		window.add(stretchButton).width(130).padTop(6).row();
		Label errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(2).width(250).left().padTop(5).row();

		TextButton apply = new TextButton("应用", kit.accentButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousKeyboardFocus);
			refreshKeyframeInspector();
		};
		Runnable submit = () -> {
			if (closed[0]) return;
			try {
				AnimationEditor.IkKeyframe source = animEditor.ikAt(timeline, frame);
				AnimationEditor.IkKeyframe value = new AnimationEditor.IkKeyframe(source.mix(), source.softness(),
					ikBendDirection(bendBox.getSelected()), compressButton.isChecked(), stretchButton.isChecked());
				AnimationEditor.TimelineSnapshot before = snapshotSelected(animation, timeline);
				long revision = doc.revision;
				boolean stepped = frame < timeline.getFrameCount() - 1
					&& animEditor.curveType(timeline, frame) == Animation.CurveTimeline.STEPPED;
				animEditor.editIkKeyframe(animation, timeline.getConstraintIndex(), time, time, value, stepped);
				boolean changed = doc.revision != revision;
				recordTimelineChange("编辑 IK 关键帧选项", before);
				close.run();
				if (changed) notifyChanged();
				showInfo(changed ? "IK 选项已更新" : "IK 选项未变化");
			} catch (RuntimeException error) {
				errorLabel.setText(error.getMessage());
			}
		};
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (UiDialogs.keyAction(keycode)) {
				case CONFIRM -> { submit.run(); yield true; }
				case CANCEL -> { close.run(); yield true; }
				case NONE -> false;
				};
			}
		});
		Table buttons = new Table();
		buttons.add(apply).width(80).height(28).padRight(6);
		buttons.add(cancel).width(80).height(28);
		window.add(buttons).colspan(2).right().padTop(6);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(bendBox);
	}

	private void refreshIkToggle(TextButton button) {
		button.setText(button.isChecked() ? "已启用" : "已关闭");
		button.setStyle(button.isChecked() ? kit.activeToolButton : kit.toolButton);
	}

	private void showDrawOrderEditor() {
		Stage stage = getStage();
		Animation animation = currentAnim();
		Animation.Timeline selected = canvas == null ? null : canvas.selectedTimeline();
		int frame = selected == null ? -1 : canvas.selectedFrame(selected);
		if (stage == null || animation == null || doc.data == null
			|| !(selected instanceof DrawOrderTimeline || selected instanceof DrawOrderFolderTimeline)
			|| frame < 0) return;
		Animation.Timeline timeline = selected;
		DrawOrderFolderTimeline folder = timeline instanceof DrawOrderFolderTimeline value ? value : null;
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		float time = animEditor.frameTime(timeline, frame);
		int[] currentOrder = folder == null
			? animEditor.drawOrderAt((DrawOrderTimeline)timeline, frame)
			: animEditor.drawOrderFolderAt(folder, frame);
		int[] controlledSlots = folder == null ? null : folder.getSlots();

		int controlledCount = controlledSlots == null ? doc.data.getSlots().size : controlledSlots.length;
		Array<String> names = new Array<>(controlledCount);
		if (currentOrder == null) {
			if (controlledSlots == null) for (SlotData slot : doc.data.getSlots()) names.add(slot.getName());
			else for (int slotIndex : controlledSlots) names.add(doc.data.getSlots().get(slotIndex).getName());
		} else {
			for (int index : currentOrder) {
				int slotIndex = controlledSlots == null ? index : controlledSlots[index];
				names.add(doc.data.getSlots().get(slotIndex).getName());
			}
		}
		List<String> orderList = new List<>(kit.selectStyle.listStyle);
		orderList.setItems(names);
		if (names.notEmpty()) orderList.setSelectedIndex(0);
		ScrollPane orderScroll = new ScrollPane(orderList, kit.scrollStyle);
		orderScroll.setFadeScrollBars(false);

		Window window = kit.window(folder == null ? "绘制顺序" : "局部绘制顺序");
		window.setModal(true);
		kit.padWindow(window, 10f);
		Label hint = new Label("从后向前的槽绘制顺序", kit.labelStyle);
		hint.setColor(0.62f, 0.68f, 0.78f, 1f);
		window.add(hint).colspan(2).left().padBottom(6).row();
		window.add(orderScroll).width(280).height(260).padRight(7);

		Table tools = new Table();
		TextButton moveUp = new TextButton("上移", kit.toolButton);
		TextButton moveDown = new TextButton("下移", kit.toolButton);
		TextButton setup = new TextButton("恢复 Setup", kit.toolButton);
		Runnable refreshTools = () -> {
			int index = orderList.getSelectedIndex();
			moveUp.setDisabled(index <= 0);
			moveDown.setDisabled(index < 0 || index >= orderList.getItems().size - 1);
		};
		moveUp.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				orderList.setSelectedIndex(moveDrawOrderEntry(orderList.getItems(), orderList.getSelectedIndex(), -1));
				orderList.invalidateHierarchy();
				refreshTools.run();
			}
		});
		moveDown.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				orderList.setSelectedIndex(moveDrawOrderEntry(orderList.getItems(), orderList.getSelectedIndex(), 1));
				orderList.invalidateHierarchy();
				refreshTools.run();
			}
		});
		setup.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				Array<String> setupNames = new Array<>(controlledCount);
				if (controlledSlots == null)
					for (SlotData slot : doc.data.getSlots()) setupNames.add(slot.getName());
				else for (int slotIndex : controlledSlots)
					setupNames.add(doc.data.getSlots().get(slotIndex).getName());
				orderList.setItems(setupNames);
				if (setupNames.notEmpty()) orderList.setSelectedIndex(0);
				refreshTools.run();
			}
		});
		orderList.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refreshTools.run(); }
		});
		tools.add(moveUp).width(92).height(28).row();
		tools.add(moveDown).width(92).height(28).padTop(5).row();
		tools.add(setup).width(92).height(28).padTop(14);
		window.add(tools).top().row();
		refreshTools.run();

		Label errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(2).width(380).left().padTop(5).row();
		TextButton apply = new TextButton("应用", kit.accentButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousKeyboardFocus);
			refreshKeyframeInspector();
		};
		Runnable submit = () -> {
			if (closed[0]) return;
			try {
				Array<String> orderedNames = orderList.getItems();
				int[] order = new int[orderedNames.size];
				for (int position = 0; position < orderedNames.size; position++) {
					SlotData slot = doc.data.findSlot(orderedNames.get(position));
					if (slot == null) throw new IllegalArgumentException("找不到槽：" + orderedNames.get(position));
					if (controlledSlots == null) order[position] = slot.getIndex();
					else {
						order[position] = -1;
						for (int local = 0; local < controlledSlots.length; local++)
							if (controlledSlots[local] == slot.getIndex()) { order[position] = local; break; }
						if (order[position] < 0) throw new IllegalArgumentException("槽不属于局部绘制顺序：" + slot.getName());
					}
				}
				AnimationEditor.TimelineSnapshot before = snapshotSelected(animation, timeline);
				long revision = doc.revision;
				if (folder == null) {
					animEditor.editDrawOrderKeyframe(animation, time, time, order);
					canvas.selectDrawOrderKeyframe(time);
				} else {
					animEditor.editDrawOrderFolderKeyframe(animation, folder, time, time, order);
					DrawOrderFolderTimeline live = animEditor.findDrawOrderFolderTimeline(animation, controlledSlots);
					canvas.selectDrawOrderFolderKeyframe(live, time);
				}
				boolean changed = doc.revision != revision;
				recordTimelineChange("编辑绘制顺序", before);
				close.run();
				if (changed) notifyChanged();
				showInfo(changed ? "绘制顺序已更新" : "绘制顺序未变化");
			} catch (RuntimeException error) {
				errorLabel.setText(error.getMessage());
			}
		};
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (UiDialogs.keyAction(keycode)) {
				case CONFIRM -> { submit.run(); yield true; }
				case CANCEL -> { close.run(); yield true; }
				case NONE -> false;
				};
			}
		});
		Table buttons = new Table();
		buttons.add(apply).width(80).height(28).padRight(6);
		buttons.add(cancel).width(80).height(28);
		window.add(buttons).colspan(2).right().padTop(7);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(orderList);
	}

	static int moveDrawOrderEntry(Array<String> order, int selected, int delta) {
		if (order == null || selected < 0 || selected >= order.size || delta == 0) return selected;
		int target = Math.max(0, Math.min(order.size - 1, selected + delta));
		if (target != selected) order.swap(selected, target);
		return target;
	}

	private void showEventPayloadEditor() {
		Stage stage = getStage();
		Animation animation = currentAnim();
		Animation.Timeline selected = canvas == null ? null : canvas.selectedTimeline();
		int frame = selected == null ? -1 : canvas.selectedFrame(selected);
		if (stage == null || animation == null || !(selected instanceof EventTimeline timeline) || frame < 0) return;
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Event event = animEditor.eventAt(timeline, frame);
		boolean audio = event.getData().getAudioPath() != null;

		TextField intField = new TextField(Integer.toString(event.getInt()), kit.textFieldStyle);
		intField.setMaxLength(12);
		intField.setTextFieldFilter((field, c) -> Character.isDigit(c) || c == '-' || c == '+');
		TextField floatField = createBezierField(event.getFloat());
		TextArea stringField = new TextArea(event.getString() == null ? "" : event.getString(), kit.textFieldStyle);
		stringField.setPrefRows(2);
		stringField.setMaxLength(512);
		TextField volumeField = createBezierField(event.getVolume());
		TextField balanceField = createBezierField(event.getBalance());
		volumeField.setDisabled(!audio);
		balanceField.setDisabled(!audio);

		Window window = kit.window("事件载荷 · " + event.getData().getName());
		window.setModal(true);
		kit.padWindow(window, 10f);
		window.add(new Label("整数", kit.labelStyle)).right().padRight(6);
		window.add(intField).width(150).row();
		window.add(new Label("浮点", kit.labelStyle)).right().padRight(6).padTop(5);
		window.add(floatField).width(150).padTop(5).row();
		window.add(new Label("字符串", kit.labelStyle)).right().padRight(6).padTop(5);
		window.add(stringField).width(260).height(52).padTop(5).row();
		if (audio) {
			Label audioLabel = new Label(event.getData().getAudioPath(), kit.labelStyle);
			audioLabel.setEllipsis(true);
			window.add(new Label("音频", kit.labelStyle)).right().padRight(6).padTop(5);
			window.add(audioLabel).width(260).left().padTop(5).row();
		}
		window.add(new Label("音量", kit.labelStyle)).right().padRight(6).padTop(5);
		window.add(volumeField).width(150).padTop(5).row();
		window.add(new Label("平衡", kit.labelStyle)).right().padRight(6).padTop(5);
		window.add(balanceField).width(150).padTop(5).row();
		Label errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(2).width(320).left().padTop(5).row();

		TextButton apply = new TextButton("应用", kit.accentButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousKeyboardFocus);
			refreshKeyframeInspector();
		};
		Runnable submit = () -> {
			if (closed[0]) return;
			try {
				int intValue = parseInteger(intField, "整数");
				float floatValue = parseNumber(floatField, "浮点");
				float volume = audio ? parseNumber(volumeField, "音量") : event.getVolume();
				float balance = audio ? parseNumber(balanceField, "平衡") : event.getBalance();
				AnimationEditor.TimelineSnapshot before = snapshotSelected(animation, timeline);
				long revision = doc.revision;
				int editedFrame = animEditor.editEventKeyframe(animation, frame, event.getTime(), event.getData(),
					intValue, floatValue, stringField.getText(), volume, balance);
				canvas.selectEventKeyframe(editedFrame);
				boolean changed = doc.revision != revision;
				recordTimelineChange("编辑事件载荷", before);
				close.run();
				if (changed) notifyChanged();
				showInfo(changed ? "事件载荷已更新" : "事件载荷未变化");
			} catch (RuntimeException error) {
				errorLabel.setText(error.getMessage());
			}
		};
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent change, Actor actor) { submit.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent change, Actor actor) { close.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent input, int keycode) {
				UiDialogs.KeyAction action = UiDialogs.keyAction(keycode);
				if (action == UiDialogs.KeyAction.CONFIRM && stage.getKeyboardFocus() instanceof TextArea
					&& !controlPressed()) return false;
				return switch (action) {
				case CONFIRM -> { submit.run(); yield true; }
				case CANCEL -> { close.run(); yield true; }
				case NONE -> false;
				};
			}
		});
		Table buttons = new Table();
		buttons.add(apply).width(80).height(28).padRight(6);
		buttons.add(cancel).width(80).height(28);
		window.add(buttons).colspan(2).right().padTop(7);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(intField);
		intField.selectAll();
	}

	private int parseInteger(TextField field, String label) {
		try {
			return Integer.parseInt(field.getText().trim());
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(label + " 必须是整数", error);
		}
	}

	private TextField createBezierField(float value) {
		TextField field = new TextField(UiNumbers.format(value), kit.textFieldStyle);
		field.setMaxLength(16);
		field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+' || c == '.'
			|| c == 'e' || c == 'E');
		return field;
	}

	private ChangeListener curvePresetListener(TextField x1, TextField y1, TextField x2, TextField y2,
		float cx1, float cy1, float cx2, float cy2) {
		return new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				setBezierFields(x1, y1, x2, y2, cx1, cy1, cx2, cy2);
			}
		};
	}

	private static void setBezierFields(TextField x1, TextField y1, TextField x2, TextField y2,
		float cx1, float cy1, float cx2, float cy2) {
		x1.setText(UiNumbers.format(cx1));
		y1.setText(UiNumbers.format(cy1));
		x2.setText(UiNumbers.format(cx2));
		y2.setText(UiNumbers.format(cy2));
	}

	private static boolean controlPressed() {
		return Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
			|| Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT));
	}

	private static boolean shiftPressed() {
		return Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
			|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
	}

	private void configureBoneKeyEditor(AnimationEditor.Prop prop, boolean twoValues) {
		boneKeyEditor.clearChildren();
		keyValue1Label.setText(valueLabel(prop, 0));
		boneKeyEditor.add(keyValue1Label).padRight(3);
		boneKeyEditor.add(keyValue1Field).width(72);
		if (twoValues) {
			keyValue2Label.setText(valueLabel(prop, 1));
			boneKeyEditor.add(keyValue2Label).padLeft(7).padRight(3);
			boneKeyEditor.add(keyValue2Field).width(72);
		}
	}

	private Array<String> attachmentOptions(int slotIndex, String currentName) {
		java.util.TreeSet<String> names = new java.util.TreeSet<>();
		if (doc.data != null) {
			for (Skin skin : doc.data.getSkins()) {
				for (Skin.SkinEntry entry : skin.getAttachments()) {
					String placeholder = entry.getPlaceholder();
					if (entry.getSlotIndex() == slotIndex && placeholder != null && !placeholder.isBlank())
						names.add(placeholder);
				}
			}
		}
		if (currentName != null && !currentName.isBlank()) names.add(currentName);
		Array<String> options = new Array<>();
		options.add(NONE_ATTACHMENT);
		for (String name : names) options.add(name);
		return options;
	}

	private Array<String> eventDefinitionNames() {
		Array<String> names = new Array<>();
		if (doc.data != null) for (EventData definition : doc.data.getEvents()) names.add(definition.getName());
		return names;
	}

	static String preferredEventName(Array<String> names, String preferredName) {
		if (names == null || names.isEmpty()) return null;
		return preferredName != null && names.contains(preferredName, false) ? preferredName : names.first();
	}

	static EventData findEventDefinition(SkeletonData data, String name) {
		return data == null || name == null || name.isBlank() ? null : data.findEvent(name);
	}

	private EventData preferredEventDefinition() {
		if (doc.data == null || doc.data.getEvents().isEmpty()) return null;
		EventData preferred = findEventDefinition(doc.data, preferredEventDefinitionName);
		return preferred == null ? doc.data.getEvents().first() : preferred;
	}

	private int addEventKeyframeWithSetup(Animation animation, float time, EventData definition) {
		if (definition == null) throw new IllegalArgumentException("事件定义无效");
		Event setup = definition.getSetupPose();
		int frame = animEditor.addEventKeyframe(animation, time, definition, setup.getInt(), setup.getFloat(),
			setup.getString(), setup.getVolume(), setup.getBalance());
		preferredEventDefinitionName = definition.getName();
		return frame;
	}

	private void showAddEventKeyframeDialog(Animation animation, float time,
		AnimationEditor.TimelineSnapshot before) {
		Stage stage = getStage();
		if (stage == null || animation == null || doc.data == null || doc.data.getEvents().isEmpty()) return;
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("添加事件关键帧");
		window.setModal(true);
		kit.padWindow(window, 10f);
		SelectBox<String> definitionBox = kit.selectBox();
		Array<String> names = eventDefinitionNames();
		definitionBox.setItems(names);
		definitionBox.setSelected(preferredEventName(names, preferredEventDefinitionName));
		window.add(new Label("时间", kit.labelStyle)).right().padRight(6);
		window.add(new Label(UiNumbers.format(time) + " 秒", kit.labelStyle)).width(220).left().row();
		window.add(new Label("事件定义", kit.labelStyle)).right().padRight(6).padTop(6);
		window.add(definitionBox).width(220).padTop(6).row();
		Label errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(2).width(300).left().padTop(5).row();

		TextButton add = new TextButton("添加", kit.accentButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousKeyboardFocus);
		};
		Runnable submit = () -> {
			if (closed[0]) return;
			try {
				EventData definition = findEventDefinition(doc.data, definitionBox.getSelected());
				int frame = addEventKeyframeWithSetup(animation, time, definition);
				canvas.selectEventKeyframe(frame);
				canvas.scrubTo(time);
				recordTimelineChange("添加事件关键帧", before);
				close.run();
				notifyChanged();
				showInfo("已添加事件 " + definition.getName());
			} catch (RuntimeException error) {
				errorLabel.setText(error.getMessage());
			}
		};
		add.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (UiDialogs.keyAction(keycode)) {
				case CONFIRM -> { submit.run(); yield true; }
				case CANCEL -> { close.run(); yield true; }
				case NONE -> false;
				};
			}
		});
		Table buttons = new Table();
		buttons.add(add).width(80).height(28).padRight(6);
		buttons.add(cancel).width(80).height(28);
		window.add(buttons).colspan(2).right().padTop(7);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(definitionBox);
	}

	private boolean applySelectedKeyframe() {
		Animation anim = currentAnim();
		Animation.Timeline timeline = canvas.selectedTimeline();
		int frame = timeline == null ? -1 : canvas.selectedFrame(timeline);
		if (anim == null || frame < 0) return false;
		AnimationEditor.TimelineSnapshot before = snapshotSelected(anim, timeline);
		try {
			long revision = doc.revision;
			float oldTime = animEditor.frameTime(timeline, frame);
			float newTime = parseNumber(keyTimeField, "时间");
			if (timeline instanceof EventTimeline eventTimeline) {
				Event source = animEditor.eventAt(eventTimeline, frame);
				String definitionName = keyEventBox.getSelected();
				EventData definition = findEventDefinition(doc.data, definitionName);
				int newFrame = animEditor.editEventKeyframe(anim, frame, newTime, definition,
					source.getInt(), source.getFloat(), source.getString(), source.getVolume(), source.getBalance());
				canvas.selectEventKeyframe(newFrame);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑事件关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("事件关键帧未变化");
				return true;
			}
			if (timeline instanceof DrawOrderTimeline drawOrderTimeline) {
				int[] order = animEditor.drawOrderAt(drawOrderTimeline, frame);
				animEditor.editDrawOrderKeyframe(anim, oldTime, newTime, order);
				canvas.selectDrawOrderKeyframe(newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑绘制顺序关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("绘制顺序关键帧未变化");
				return true;
			}
			if (timeline instanceof DrawOrderFolderTimeline folder) {
				int[] order = animEditor.drawOrderFolderAt(folder, frame);
				int[] slots = folder.getSlots().clone();
				animEditor.editDrawOrderFolderKeyframe(anim, folder, oldTime, newTime, order);
				DrawOrderFolderTimeline live = animEditor.findDrawOrderFolderTimeline(anim, slots);
				canvas.selectDrawOrderFolderKeyframe(live, newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑局部绘制顺序关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("局部绘制顺序关键帧未变化");
				return true;
			}
			if (timeline instanceof IkConstraintTimeline ikTimeline) {
				AnimationEditor.IkKeyframe source = animEditor.ikAt(ikTimeline, frame);
				float mix = parseNumber(keyIkMixField, "IK 混合");
				float softness = parseNumber(keyIkSoftnessField, "IK 柔度");
				AnimationEditor.IkKeyframe value = new AnimationEditor.IkKeyframe(mix, softness,
					source.bendDirection(), source.compress(), source.stretch());
				animEditor.editIkKeyframe(anim, ikTimeline.getConstraintIndex(), oldTime, newTime,
					value, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findIkTimeline(anim, ikTimeline.getConstraintIndex()), newTime);
				canvas.selectIkKeyframe(canvas.selectedIkConstraintData(), newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑 IK 关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("IK 关键帧未变化");
				return true;
			}
			if (timeline instanceof TransformConstraintTimeline transformTimeline) {
				AnimationEditor.TransformKeyframe value = transformKeyFromFields();
				animEditor.editTransformKeyframe(anim, transformTimeline.getConstraintIndex(), oldTime, newTime,
					value, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findTransformTimeline(anim,
					transformTimeline.getConstraintIndex()), newTime);
				canvas.selectTransformKeyframe(canvas.selectedTransformConstraintData(), newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑 Transform 关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("Transform 关键帧未变化");
				return true;
			}
			AnimationEditor.PathProp pathProp = pathPropOf(timeline);
			if ((pathProp == AnimationEditor.PathProp.POSITION
				|| pathProp == AnimationEditor.PathProp.SPACING) && timeline instanceof ConstraintTimeline pathTimeline) {
				float value = parseNumber(keyPathValueField, "Path " + pathPropLabel(pathProp));
				animEditor.editPathValueKeyframe(anim, pathTimeline.getConstraintIndex(), pathProp,
					oldTime, newTime, value, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findPathTimeline(anim, pathTimeline.getConstraintIndex(), pathProp), newTime);
				canvas.selectPathKeyframe(canvas.selectedPathConstraintData(), pathProp, newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑 Path " + pathPropLabel(pathProp) + "关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("Path 关键帧未变化");
				return true;
			}
			if (timeline instanceof PathConstraintMixTimeline pathTimeline) {
				AnimationEditor.PathMixKeyframe value = pathMixKeyFromFields();
				animEditor.editPathMixKeyframe(anim, pathTimeline.getConstraintIndex(), oldTime, newTime,
					value, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findPathTimeline(anim, pathTimeline.getConstraintIndex(),
					AnimationEditor.PathProp.MIX), newTime);
				canvas.selectPathKeyframe(canvas.selectedPathConstraintData(), AnimationEditor.PathProp.MIX, newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑 Path 混合关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("Path 关键帧未变化");
				return true;
			}
			AnimationEditor.PhysicsProp physicsProp = animEditor.physicsPropOf(timeline);
			if (physicsProp == AnimationEditor.PhysicsProp.RESET && timeline instanceof ConstraintTimeline physics) {
				animEditor.editPhysicsResetKeyframe(anim, physics.getConstraintIndex(), oldTime, newTime);
				canvas.selectPhysicsKeyframe(canvas.selectedPhysicsConstraintData(), physics.getConstraintIndex() == -1,
					AnimationEditor.PhysicsProp.RESET, newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑 Physics 重置关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("Physics 重置关键帧未变化");
				return true;
			}
			if (physicsProp != null && timeline instanceof ConstraintTimeline physics) {
				float value = parseNumber(keyPhysicsValueField, "Physics " + physicsPropLabel(physicsProp));
				animEditor.editPhysicsValueKeyframe(anim, physics.getConstraintIndex(), physicsProp,
					oldTime, newTime, value, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findPhysicsTimeline(anim, physics.getConstraintIndex(), physicsProp),
					newTime);
				canvas.selectPhysicsKeyframe(canvas.selectedPhysicsConstraintData(),
					physics.getConstraintIndex() == -1, physicsProp, newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑 Physics " + physicsPropLabel(physicsProp) + "关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("Physics 关键帧未变化");
				return true;
			}
			AnimationEditor.SliderProp sliderProp = sliderPropOf(timeline);
			if (sliderProp != null && timeline instanceof ConstraintTimeline slider) {
				float value = parseNumber(keyPhysicsValueField, "Slider " + sliderPropLabel(sliderProp));
				animEditor.editSliderKeyframe(anim, slider.getConstraintIndex(), sliderProp,
					oldTime, newTime, value, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findSliderTimeline(anim, slider.getConstraintIndex(), sliderProp), newTime);
				canvas.selectSliderKeyframe(canvas.selectedSliderConstraintData(), sliderProp, newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑 Slider " + sliderPropLabel(sliderProp) + "关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("Slider 关键帧未变化");
				return true;
			}
			if (timeline instanceof SequenceTimeline sequenceTimeline) {
				SequenceMode mode = sequenceMode(keySequenceModeBox.getSelected());
				if (mode == null) throw new IllegalArgumentException("Sequence mode is invalid");
				AnimationEditor.SequenceKeyframe value = new AnimationEditor.SequenceKeyframe(mode,
					parseSequenceIndex(keySequenceIndexField.getText()),
					parseNumber(keySequenceDelayField, "Sequence delay"));
				animEditor.editSequenceKeyframe(anim, sequenceTimeline.getSlotIndex(),
					sequenceTimeline.getAttachment(), oldTime, newTime, value);
				canvas.selectSequenceKeyframe(canvas.selectedSlotData(), sequenceTimeline.getAttachment(),
					canvas.selectedSequenceName(), newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("Edit Sequence keyframe", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("Sequence keyframe unchanged");
				return true;
			}
			if (timeline instanceof DeformTimeline deformTimeline) {
				if (deformDraftOffsets == null) throw new IllegalArgumentException("Deform 顶点偏移无效");
				animEditor.editDeformKeyframe(anim, deformTimeline.getSlotIndex(), deformTimeline.getAttachment(),
					oldTime, newTime, deformDraftOffsets, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findDeformTimeline(anim, deformTimeline.getSlotIndex(),
					deformTimeline.getAttachment()), newTime);
				canvas.selectDeformKeyframe(canvas.selectedSlotData(), deformTimeline.getAttachment(),
					canvas.selectedDeformName(), newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑 Deform 关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("Deform 关键帧未变化");
				return true;
			}
			if (timeline instanceof InheritTimeline inheritTimeline) {
				Bone bone = canvas.selectedBone();
				Inherit inherit = PropertiesPanel.inheritMode(keyInheritBox.getSelected());
				if (bone == null || inherit == null) throw new IllegalArgumentException("继承模式无效");
				animEditor.editInheritKeyframe(anim, inheritTimeline.getBoneIndex(), oldTime, newTime, inherit);
				canvas.selectInheritKeyframe(bone, newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑继承关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("继承关键帧未变化");
				return true;
			}
			if (timeline instanceof AttachmentTimeline attachmentTimeline) {
				String selectedName = keyAttachmentBox.getSelected();
				String attachmentName = NONE_ATTACHMENT.equals(selectedName) ? null : selectedName;
				animEditor.editAttachmentKeyframe(anim, attachmentTimeline.getSlotIndex(), oldTime,
					newTime, attachmentName);
				canvas.selectAttachmentKeyframe(canvas.selectedSlotData(), newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑附件关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("附件关键帧未变化");
				return true;
			}
			if (timeline instanceof RGBATimeline colorTimeline) {
				Color color = UiColors.parse(keyColorField.getText());
				animEditor.editColorKeyframe(anim, colorTimeline.getSlotIndex(), oldTime, newTime,
					color.r, color.g, color.b, color.a, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findColorTimeline(anim, colorTimeline.getSlotIndex()), newTime);
				canvas.selectColorKeyframe(canvas.selectedSlotData(), newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑颜色关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("颜色关键帧未变化");
				return true;
			}
			if (timeline instanceof RGBTimeline colorTimeline) {
				Color color = UiColors.parseRgb(keyColorField.getText());
				animEditor.editRgbKeyframe(anim, colorTimeline.getSlotIndex(), oldTime, newTime,
					color.r, color.g, color.b, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findRgbTimeline(anim, colorTimeline.getSlotIndex()), newTime);
				canvas.selectRgbKeyframe(canvas.selectedSlotData(), newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑 RGB 关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("RGB 关键帧未变化");
				return true;
			}
			if (timeline instanceof AlphaTimeline alphaTimeline) {
				float alpha = parseNumber(keyAlphaField, "透明度");
				animEditor.editAlphaKeyframe(anim, alphaTimeline.getSlotIndex(), oldTime, newTime,
					alpha, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findAlphaTimeline(anim, alphaTimeline.getSlotIndex()), newTime);
				canvas.selectAlphaKeyframe(canvas.selectedSlotData(), newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑透明度关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("透明度关键帧未变化");
				return true;
			}
			if (animEditor.isTwoColorTimeline(timeline)) {
				SlotCurveTimeline twoColorTimeline = (SlotCurveTimeline)timeline;
				boolean hasAlpha = animEditor.twoColorHasAlpha(timeline);
				Color light = hasAlpha ? UiColors.parse(keyColorField.getText())
					: UiColors.parseRgb(keyColorField.getText());
				Color dark = UiColors.parseRgb(keyDarkColorField.getText());
				animEditor.editTwoColorKeyframe(anim, twoColorTimeline.getSlotIndex(), oldTime, newTime,
					light.r, light.g, light.b, light.a, dark.r, dark.g, dark.b, steppedBtn.isChecked());
				applyRequestedCurve(animEditor.findTwoColorTimeline(anim, twoColorTimeline.getSlotIndex()), newTime);
				canvas.selectTwoColorKeyframe(canvas.selectedSlotData(), newTime);
				doc.scrubTo(newTime);
				recordTimelineChange("编辑双色染色关键帧", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("双色染色关键帧未变化");
				return true;
			}
			Bone bone = canvas.selectedBone();
			if (bone == null || canvas.selProp == null) return false;
			float value1 = parseNumber(keyValue1Field, valueLabel(canvas.selProp, 0));
			float value2 = timeline instanceof BoneTimeline2
				? parseNumber(keyValue2Field, valueLabel(canvas.selProp, 1)) : animEditor.frameValue(timeline, frame, 1);
			animEditor.editKeyframe(anim, canvas.selProp, bone.getData().getIndex(), oldTime,
				newTime, value1, value2, steppedBtn.isChecked());
			applyRequestedCurve(animEditor.findTimeline(anim, canvas.selProp, bone.getData().getIndex()), newTime);
			canvas.selectKeyframe(bone, canvas.selProp, newTime);
			doc.scrubTo(newTime);
			recordTimelineChange("编辑关键帧", before);
			if (doc.revision != revision) notifyChanged();
			else showInfo("关键帧未变化");
			return true;
		} catch (RuntimeException error) {
			showStatus("无法应用关键帧：" + error.getMessage());
			return false;
		}
	}

	private void deleteSelectedKeyframe() {
		if (canvas.deleteSelectedKeyframes()) return;
		Animation anim = currentAnim();
		Animation.Timeline timeline = canvas.selectedTimeline();
		int frame = timeline == null ? -1 : canvas.selectedFrame(timeline);
		if (anim == null || frame < 0) return;
		AnimationEditor.TimelineSnapshot before = snapshotSelected(anim, timeline);
		try {
			float time = animEditor.frameTime(timeline, frame);
			if (timeline instanceof EventTimeline) {
				animEditor.removeEventKeyframe(anim, frame);
			} else if (timeline instanceof DrawOrderTimeline) {
				animEditor.removeDrawOrderKeyframe(anim, time);
			} else if (timeline instanceof DrawOrderFolderTimeline folder) {
				animEditor.removeDrawOrderFolderKeyframe(anim, folder, time);
			} else if (timeline instanceof IkConstraintTimeline ikTimeline) {
				animEditor.removeIkKeyframe(anim, ikTimeline.getConstraintIndex(), time);
			} else if (timeline instanceof TransformConstraintTimeline transformTimeline) {
				animEditor.removeTransformKeyframe(anim, transformTimeline.getConstraintIndex(), time);
			} else if (timeline instanceof PathConstraintPositionTimeline pathTimeline) {
				animEditor.removePathValueKeyframe(anim, pathTimeline.getConstraintIndex(),
					AnimationEditor.PathProp.POSITION, time);
			} else if (timeline instanceof PathConstraintSpacingTimeline pathTimeline) {
				animEditor.removePathValueKeyframe(anim, pathTimeline.getConstraintIndex(),
					AnimationEditor.PathProp.SPACING, time);
			} else if (timeline instanceof PathConstraintMixTimeline pathTimeline) {
				animEditor.removePathMixKeyframe(anim, pathTimeline.getConstraintIndex(), time);
			} else if (timeline instanceof PhysicsConstraintResetTimeline physicsTimeline) {
				animEditor.removePhysicsResetKeyframe(anim, physicsTimeline.getConstraintIndex(), time);
			} else if (timeline instanceof PhysicsConstraintTimeline physicsTimeline) {
				animEditor.removePhysicsValueKeyframe(anim, physicsTimeline.getConstraintIndex(),
					animEditor.physicsPropOf(physicsTimeline), time);
			} else if (timeline instanceof ConstraintTimeline sliderTimeline
				&& sliderPropOf(timeline) != null) {
				animEditor.removeSliderKeyframe(anim, sliderTimeline.getConstraintIndex(),
					sliderPropOf(timeline), time);
			} else if (timeline instanceof SequenceTimeline sequenceTimeline) {
				animEditor.removeSequenceKeyframe(anim, sequenceTimeline.getSlotIndex(),
					sequenceTimeline.getAttachment(), time);
			} else if (timeline instanceof DeformTimeline deformTimeline) {
				animEditor.removeDeformKeyframe(anim, deformTimeline.getSlotIndex(), deformTimeline.getAttachment(), time);
			} else if (timeline instanceof InheritTimeline inheritTimeline) {
				animEditor.removeInheritKeyframe(anim, inheritTimeline.getBoneIndex(), time);
			} else if (timeline instanceof AttachmentTimeline attachmentTimeline) {
				animEditor.removeAttachmentKeyframe(anim, attachmentTimeline.getSlotIndex(), time);
			} else if (timeline instanceof RGBATimeline colorTimeline) {
				animEditor.removeColorKeyframe(anim, colorTimeline.getSlotIndex(), time);
			} else if (timeline instanceof RGBTimeline colorTimeline) {
				animEditor.removeRgbKeyframe(anim, colorTimeline.getSlotIndex(), time);
			} else if (timeline instanceof AlphaTimeline alphaTimeline) {
				animEditor.removeAlphaKeyframe(anim, alphaTimeline.getSlotIndex(), time);
			} else if (animEditor.isTwoColorTimeline(timeline)) {
				animEditor.removeTwoColorKeyframe(anim, ((SlotCurveTimeline)timeline).getSlotIndex(), time);
			} else {
				Bone bone = canvas.selectedBone();
				if (bone == null || canvas.selProp == null) return;
				animEditor.removeKeyframe(anim, canvas.selProp, bone.getData().getIndex(), time);
			}
			canvas.clearSelection();
			recordTimelineChange("删除关键帧", before);
			notifyChanged();
		} catch (RuntimeException error) {
			showStatus("无法删除关键帧：" + error.getMessage());
		}
	}

	private void copySelectedKeyframe() {
		if (canvas.selectionCount() > 1) {
			try {
				clearCopiedKeyKinds();
				copiedKeyframeBlock = canvas.copySelectedKeyframeBlock();
				refreshKeyframeInspector();
				showInfo("已复制 " + copiedKeyframeBlock.size() + " 个关键帧");
			} catch (RuntimeException error) {
				showStatus("无法复制多个关键帧：" + error.getMessage());
			}
			return;
		}
		Animation.Timeline timeline = canvas.selectedTimeline();
		int frame = timeline == null ? -1 : canvas.selectedFrame(timeline);
		if (frame < 0) return;
		clearCopiedKeyKinds();
		if (timeline instanceof EventTimeline eventTimeline) {
			Event event = animEditor.eventAt(eventTimeline, frame);
			copiedEventKey = true;
			copiedEventDefinition = event.getData();
			copiedEventInt = event.getInt();
			copiedEventFloat = event.getFloat();
			copiedEventString = event.getString();
			copiedEventVolume = event.getVolume();
			copiedEventBalance = event.getBalance();
			copiedInheritKey = false;
			copiedInherit = null;
			copiedAttachmentKey = copiedColorKey = copiedTwoColorKey = false;
			copiedProp = null;
			copiedCurve = null;
			refreshKeyframeInspector();
			showInfo("已复制事件关键帧");
			return;
		}
		if (timeline instanceof DrawOrderTimeline drawOrderTimeline) {
			clearCopiedKeyKinds();
			copiedDrawOrderKey = true;
			copiedDrawOrder = animEditor.drawOrderAt(drawOrderTimeline, frame);
			refreshKeyframeInspector();
			showInfo("已复制绘制顺序关键帧");
			return;
		}
		if (timeline instanceof DrawOrderFolderTimeline folder) {
			clearCopiedKeyKinds();
			copiedDrawOrderKey = true;
			copiedDrawOrder = animEditor.drawOrderFolderAt(folder, frame);
			copiedDrawOrderFolderSlots = folder.getSlots().clone();
			refreshKeyframeInspector();
			showInfo("已复制局部绘制顺序关键帧");
			return;
		}
		if (timeline instanceof IkConstraintTimeline ikTimeline) {
			copiedIkKey = true;
			copiedIk = animEditor.ikAt(ikTimeline, frame);
			copiedCurve = animEditor.captureCurve(timeline, frame);
			refreshKeyframeInspector();
			showInfo("已复制 IK 关键帧");
			return;
		}
		if (timeline instanceof TransformConstraintTimeline transformTimeline) {
			copiedTransformKey = true;
			copiedTransform = animEditor.transformAt(transformTimeline, frame);
			copiedCurve = animEditor.captureCurve(timeline, frame);
			refreshKeyframeInspector();
			showInfo("已复制 Transform 关键帧");
			return;
		}
		AnimationEditor.PathProp pathProp = pathPropOf(timeline);
		if (pathProp != null) {
			copiedPathKey = true;
			copiedPathProp = pathProp;
			copiedPathMix = timeline instanceof PathConstraintMixTimeline mix
				? animEditor.pathMixAt(mix, frame) : null;
			if (copiedPathMix == null) copiedPathValue = animEditor.pathValueAt(timeline, frame);
			copiedCurve = animEditor.captureCurve(timeline, frame);
			refreshKeyframeInspector();
			showInfo("已复制 Path " + pathPropLabel(pathProp) + "关键帧");
			return;
		}
		AnimationEditor.PhysicsProp physicsProp = animEditor.physicsPropOf(timeline);
		if (physicsProp != null) {
			copiedPhysicsKey = true;
			copiedPhysicsProp = physicsProp;
			if (physicsProp != AnimationEditor.PhysicsProp.RESET)
				copiedPhysicsValue = animEditor.physicsValueAt(timeline, frame);
			copiedCurve = physicsProp == AnimationEditor.PhysicsProp.RESET
				? null : animEditor.captureCurve(timeline, frame);
			refreshKeyframeInspector();
			showInfo("已复制 Physics " + physicsPropLabel(physicsProp) + "关键帧");
			return;
		}
		AnimationEditor.SliderProp sliderProp = sliderPropOf(timeline);
		if (sliderProp != null) {
			copiedSliderKey = true;
			copiedSliderProp = sliderProp;
			copiedSliderValue = animEditor.sliderValueAt(timeline, frame);
			copiedCurve = animEditor.captureCurve(timeline, frame);
			refreshKeyframeInspector();
			showInfo("已复制 Slider " + sliderPropLabel(sliderProp) + "关键帧");
			return;
		}
		if (timeline instanceof SequenceTimeline sequenceTimeline) {
			copiedSequenceKey = true;
			copiedSequence = animEditor.sequenceAt(sequenceTimeline, frame);
			copiedCurve = null;
			refreshKeyframeInspector();
			showInfo("Sequence keyframe copied");
			return;
		}
		if (timeline instanceof DeformTimeline deformTimeline) {
			copiedDeformKey = true;
			copiedDeformOffsets = animEditor.deformOffsetsAt(deformTimeline, frame);
			copiedCurve = animEditor.captureCurve(timeline, frame);
			refreshKeyframeInspector();
			showInfo("已复制 Deform 关键帧");
			return;
		}
		if (timeline instanceof InheritTimeline inheritTimeline) {
			copiedEventKey = false;
			copiedInheritKey = true;
			copiedInherit = animEditor.inheritAt(inheritTimeline, frame);
			copiedAttachmentKey = false;
			copiedAttachmentName = null;
			copiedColorKey = false;
			copiedTwoColorKey = false;
			copiedProp = null;
			copiedCurve = null;
			refreshKeyframeInspector();
			showInfo("已复制继承关键帧");
			return;
		}
		if (timeline instanceof AttachmentTimeline attachmentTimeline) {
			copiedEventKey = false;
			copiedInheritKey = false;
			copiedInherit = null;
			copiedAttachmentKey = true;
			copiedColorKey = false;
			copiedTwoColorKey = false;
			copiedAttachmentName = animEditor.attachmentName(attachmentTimeline, frame);
			copiedProp = null;
			copiedCurve = null;
			refreshKeyframeInspector();
			showInfo("已复制附件切换关键帧");
			return;
		}
		AnimationEditor.CurveSnapshot curve = animEditor.captureCurve(timeline, frame);
		if (timeline instanceof RGBATimeline colorTimeline) {
			copiedEventKey = false;
			copiedInheritKey = false;
			copiedInherit = null;
			copiedAttachmentKey = false;
			copiedAttachmentName = null;
			copiedColorKey = true;
			copiedTwoColorKey = false;
			copiedProp = null;
			copiedColor.set(colorAt(colorTimeline, frame));
			copiedCurve = curve;
			refreshKeyframeInspector();
			showInfo("已复制槽颜色关键帧");
			return;
		}
		if (timeline instanceof RGBTimeline colorTimeline) {
			copiedRgbKey = true;
			copiedColor.set(rgbAt(colorTimeline, frame));
			copiedCurve = curve;
			refreshKeyframeInspector();
			showInfo("已复制 RGB 关键帧");
			return;
		}
		if (timeline instanceof AlphaTimeline alphaTimeline) {
			copiedAlphaKey = true;
			copiedAlpha = animEditor.alphaValue(alphaTimeline, frame);
			copiedCurve = curve;
			refreshKeyframeInspector();
			showInfo("已复制透明度关键帧");
			return;
		}
		if (animEditor.isTwoColorTimeline(timeline)) {
			copiedEventKey = false;
			copiedInheritKey = false;
			copiedInherit = null;
			copiedAttachmentKey = false;
			copiedAttachmentName = null;
			copiedColorKey = false;
			copiedTwoColorKey = true;
			copiedProp = null;
			copiedColor.set(twoColorLightAt(timeline, frame));
			copiedDarkColor.set(twoColorDarkAt(timeline, frame));
			copiedTwoColorHasAlpha = animEditor.twoColorHasAlpha(timeline);
			copiedCurve = curve;
			refreshKeyframeInspector();
			showInfo("已复制双色染色关键帧");
			return;
		}
		if (canvas.selProp == null) return;
		copiedEventKey = false;
		copiedInheritKey = false;
		copiedInherit = null;
		copiedAttachmentKey = false;
		copiedAttachmentName = null;
		copiedColorKey = false;
		copiedTwoColorKey = false;
		copiedProp = canvas.selProp;
		copiedValue1 = animEditor.frameValue(timeline, frame, 0);
		copiedValue2 = animEditor.frameValue(timeline, frame, 1);
		copiedCurve = curve;
		refreshKeyframeInspector();
		showInfo("已复制 " + propLabel(copiedProp) + " 关键帧");
	}

	private void clearCopiedKeyKinds() {
		copiedKeyframeBlock = null;
		copiedEventKey = false;
		copiedEventDefinition = null;
		copiedDrawOrderKey = false;
		copiedDrawOrder = null;
		copiedDrawOrderFolderSlots = null;
		copiedInheritKey = false;
		copiedInherit = null;
		copiedAttachmentKey = false;
		copiedAttachmentName = null;
		copiedColorKey = false;
		copiedRgbKey = false;
		copiedAlphaKey = false;
		copiedTwoColorKey = false;
		copiedIkKey = false;
		copiedIk = null;
		copiedTransformKey = false;
		copiedTransform = null;
		copiedPathKey = false;
		copiedPathProp = null;
		copiedPathValue = 0f;
		copiedPathMix = null;
		copiedPhysicsKey = false;
		copiedPhysicsProp = null;
		copiedPhysicsValue = 0f;
		copiedSliderKey = false;
		copiedSliderProp = null;
		copiedSliderValue = 0f;
		copiedSequenceKey = false;
		copiedSequence = null;
		copiedDeformKey = false;
		copiedDeformOffsets = null;
		copiedProp = null;
		copiedCurve = null;
	}

	private void pasteCopiedKeyframe() {
		Animation animation = currentAnim();
		if (animation == null) return;
		float time = canvas.snapTime(doc.getCurrentTime());
		if (copiedKeyframeBlock != null) {
			AnimationEditor.TimelineSnapshot[] before = null;
			long revision = doc.revision;
			try {
				before = history == null ? null
					: animEditor.snapshotKeyframeBlockTargets(animation, copiedKeyframeBlock);
				int count = animEditor.pasteKeyframes(animation, copiedKeyframeBlock, time);
				canvas.clearSelection();
				doc.scrubTo(time);
				recordTimelineChanges("粘贴多个关键帧", before);
				notifyChanged();
				showInfo("已粘贴 " + count + " 个关键帧");
			} catch (RuntimeException error) {
				if (before != null && doc.revision != revision) restoreTimelines(before);
				showStatus("无法粘贴多个关键帧：" + error.getMessage());
			}
			return;
		}
		Animation.Timeline timeline = canvas.selectedTimeline();
		if (timeline == null) return;
		if (!(timeline instanceof EventTimeline) && animEditor.findFrame(timeline, time) >= 0) {
			showStatus("无法粘贴关键帧：当前时间已有关键帧");
			return;
		}
		AnimationEditor.TimelineSnapshot before = snapshotSelected(animation, timeline);
		try {
			if (timeline instanceof EventTimeline) {
				if (!copiedEventKey || copiedEventDefinition == null || doc.data == null
					|| !doc.data.getEvents().contains(copiedEventDefinition, true)) return;
				int frame = animEditor.addEventKeyframe(animation, time, copiedEventDefinition, copiedEventInt,
					copiedEventFloat, copiedEventString, copiedEventVolume, copiedEventBalance);
				canvas.selectEventKeyframe(frame);
				doc.scrubTo(time);
				recordTimelineChange("粘贴事件关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof DrawOrderTimeline) {
				if (!copiedDrawOrderKey || copiedDrawOrderFolderSlots != null) return;
				animEditor.addDrawOrderKeyframe(animation, time, copiedDrawOrder);
				canvas.selectDrawOrderKeyframe(time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴绘制顺序关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof DrawOrderFolderTimeline folder) {
				if (!copiedDrawOrderKey
					|| !java.util.Arrays.equals(copiedDrawOrderFolderSlots, folder.getSlots())) return;
				int[] slots = folder.getSlots().clone();
				animEditor.addDrawOrderFolderKeyframe(animation, folder, time, copiedDrawOrder);
				DrawOrderFolderTimeline live = animEditor.findDrawOrderFolderTimeline(animation, slots);
				canvas.selectDrawOrderFolderKeyframe(live, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴局部绘制顺序关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof IkConstraintTimeline ikTimeline) {
				if (!copiedIkKey || copiedIk == null || copiedCurve == null) return;
				IkConstraintData constraint = canvas.selectedIkConstraintData();
				if (constraint == null) return;
				animEditor.addIkKeyframe(animation, ikTimeline.getConstraintIndex(), time, copiedIk);
				IkConstraintTimeline pastedTimeline = animEditor.findIkTimeline(animation,
					ikTimeline.getConstraintIndex());
				int pastedFrame = animEditor.findFrame(pastedTimeline, time);
				if (pastedFrame >= 0)
					animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				canvas.selectIkKeyframe(constraint, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴 IK 关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof TransformConstraintTimeline transformTimeline) {
				if (!copiedTransformKey || copiedTransform == null || copiedCurve == null) return;
				TransformConstraintData constraint = canvas.selectedTransformConstraintData();
				if (constraint == null) return;
				animEditor.addTransformKeyframe(animation, transformTimeline.getConstraintIndex(), time,
					copiedTransform);
				TransformConstraintTimeline pastedTimeline = animEditor.findTransformTimeline(animation,
					transformTimeline.getConstraintIndex());
				int pastedFrame = animEditor.findFrame(pastedTimeline, time);
				if (pastedFrame >= 0)
					animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				canvas.selectTransformKeyframe(constraint, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴 Transform 关键帧", before);
				notifyChanged();
				return;
			}
			AnimationEditor.PathProp pathProp = pathPropOf(timeline);
			if (pathProp != null && timeline instanceof ConstraintTimeline pathTimeline) {
				if (!copiedPathKey || copiedPathProp != pathProp || copiedCurve == null) return;
				if (pathProp == AnimationEditor.PathProp.MIX) {
					if (copiedPathMix == null) return;
					animEditor.addPathMixKeyframe(animation, pathTimeline.getConstraintIndex(), time, copiedPathMix);
				} else {
					animEditor.addPathValueKeyframe(animation, pathTimeline.getConstraintIndex(), pathProp,
						time, copiedPathValue);
				}
				Animation.Timeline pastedTimeline = animEditor.findPathTimeline(animation,
					pathTimeline.getConstraintIndex(), pathProp);
				int pastedFrame = animEditor.findFrame(pastedTimeline, time);
				if (pastedFrame >= 0)
					animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				canvas.selectPathKeyframe(canvas.selectedPathConstraintData(), pathProp, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴 Path " + pathPropLabel(pathProp) + "关键帧", before);
				notifyChanged();
				return;
			}
			AnimationEditor.PhysicsProp physicsProp = animEditor.physicsPropOf(timeline);
			if (physicsProp != null && timeline instanceof ConstraintTimeline physicsTimeline) {
				if (!copiedPhysicsKey || copiedPhysicsProp != physicsProp) return;
				if (physicsProp == AnimationEditor.PhysicsProp.RESET) {
					animEditor.addPhysicsResetKeyframe(animation, physicsTimeline.getConstraintIndex(), time);
				} else {
					if (copiedCurve == null) return;
					animEditor.addPhysicsValueKeyframe(animation, physicsTimeline.getConstraintIndex(), physicsProp,
						time, copiedPhysicsValue);
					Animation.Timeline pastedTimeline = animEditor.findPhysicsTimeline(animation,
						physicsTimeline.getConstraintIndex(), physicsProp);
					int pastedFrame = animEditor.findFrame(pastedTimeline, time);
					if (pastedFrame >= 0)
						animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				}
				canvas.selectPhysicsKeyframe(canvas.selectedPhysicsConstraintData(),
					physicsTimeline.getConstraintIndex() == -1, physicsProp, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴 Physics " + physicsPropLabel(physicsProp) + "关键帧", before);
				notifyChanged();
				return;
			}
			AnimationEditor.SliderProp sliderProp = sliderPropOf(timeline);
			if (sliderProp != null && timeline instanceof ConstraintTimeline sliderTimeline) {
				if (!copiedSliderKey || copiedSliderProp != sliderProp || copiedCurve == null) return;
				animEditor.addSliderKeyframe(animation, sliderTimeline.getConstraintIndex(), sliderProp,
					time, copiedSliderValue);
				Animation.Timeline pastedTimeline = animEditor.findSliderTimeline(animation,
					sliderTimeline.getConstraintIndex(), sliderProp);
				int pastedFrame = animEditor.findFrame(pastedTimeline, time);
				if (pastedFrame >= 0)
					animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				canvas.selectSliderKeyframe(canvas.selectedSliderConstraintData(), sliderProp, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴 Slider " + sliderPropLabel(sliderProp) + "关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof SequenceTimeline sequenceTimeline) {
				if (!copiedSequenceKey || copiedSequence == null) return;
				animEditor.addSequenceKeyframe(animation, sequenceTimeline.getSlotIndex(),
					sequenceTimeline.getAttachment(), time, copiedSequence);
				canvas.selectSequenceKeyframe(canvas.selectedSlotData(), sequenceTimeline.getAttachment(),
					canvas.selectedSequenceName(), time);
				doc.scrubTo(time);
				recordTimelineChange("Paste Sequence keyframe", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof DeformTimeline deformTimeline) {
				if (!copiedDeformKey || copiedDeformOffsets == null || copiedCurve == null
					|| copiedDeformOffsets.length != animEditor.deformLength(deformTimeline.getAttachment())) return;
				animEditor.addDeformKeyframe(animation, deformTimeline.getSlotIndex(),
					deformTimeline.getAttachment(), time, copiedDeformOffsets);
				DeformTimeline pastedTimeline = animEditor.findDeformTimeline(animation,
					deformTimeline.getSlotIndex(), deformTimeline.getAttachment());
				int pastedFrame = animEditor.findFrame(pastedTimeline, time);
				if (pastedFrame >= 0)
					animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				canvas.selectDeformKeyframe(canvas.selectedSlotData(), deformTimeline.getAttachment(),
					canvas.selectedDeformName(), time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴 Deform 关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof InheritTimeline inheritTimeline) {
				if (!copiedInheritKey || copiedInherit == null) return;
				Bone bone = canvas.selectedBone();
				if (bone == null) return;
				animEditor.addInheritKeyframe(animation, inheritTimeline.getBoneIndex(), time, copiedInherit);
				canvas.selectInheritKeyframe(bone, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴继承关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof AttachmentTimeline attachmentTimeline) {
				if (!copiedAttachmentKey) return;
				SlotData slot = canvas.selectedSlotData();
				if (slot == null) return;
				animEditor.addAttachmentKeyframe(animation, attachmentTimeline.getSlotIndex(), time,
					copiedAttachmentName);
				canvas.selectAttachmentKeyframe(slot, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴附件关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof RGBATimeline colorTimeline) {
				if (!copiedColorKey || copiedCurve == null) return;
				SlotData slot = canvas.selectedSlotData();
				if (slot == null) return;
				animEditor.addColorKeyframe(animation, colorTimeline.getSlotIndex(), time,
					copiedColor.r, copiedColor.g, copiedColor.b, copiedColor.a);
				RGBATimeline pastedTimeline = animEditor.findColorTimeline(animation, colorTimeline.getSlotIndex());
				int pastedFrame = animEditor.findFrame(pastedTimeline, time);
				if (pastedFrame >= 0) animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				canvas.selectColorKeyframe(slot, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴颜色关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof RGBTimeline colorTimeline) {
				if (!copiedRgbKey || copiedCurve == null) return;
				SlotData slot = canvas.selectedSlotData();
				if (slot == null) return;
				animEditor.addRgbKeyframe(animation, colorTimeline.getSlotIndex(), time,
					copiedColor.r, copiedColor.g, copiedColor.b);
				RGBTimeline pastedTimeline = animEditor.findRgbTimeline(animation, colorTimeline.getSlotIndex());
				int pastedFrame = animEditor.findFrame(pastedTimeline, time);
				if (pastedFrame >= 0) animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				canvas.selectRgbKeyframe(slot, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴 RGB 关键帧", before);
				notifyChanged();
				return;
			}
			if (timeline instanceof AlphaTimeline alphaTimeline) {
				if (!copiedAlphaKey || copiedCurve == null) return;
				SlotData slot = canvas.selectedSlotData();
				if (slot == null) return;
				animEditor.addAlphaKeyframe(animation, alphaTimeline.getSlotIndex(), time, copiedAlpha);
				AlphaTimeline pastedTimeline = animEditor.findAlphaTimeline(animation, alphaTimeline.getSlotIndex());
				int pastedFrame = animEditor.findFrame(pastedTimeline, time);
				if (pastedFrame >= 0) animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				canvas.selectAlphaKeyframe(slot, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴透明度关键帧", before);
				notifyChanged();
				return;
			}
			if (animEditor.isTwoColorTimeline(timeline)) {
				if (!copiedTwoColorKey || copiedCurve == null
					|| copiedTwoColorHasAlpha != animEditor.twoColorHasAlpha(timeline)) return;
				SlotData slot = canvas.selectedSlotData();
				if (slot == null) return;
				SlotCurveTimeline twoColorTimeline = (SlotCurveTimeline)timeline;
				animEditor.addTwoColorKeyframe(animation, twoColorTimeline.getSlotIndex(), time,
					copiedColor.r, copiedColor.g, copiedColor.b, copiedColor.a,
					copiedDarkColor.r, copiedDarkColor.g, copiedDarkColor.b, copiedTwoColorHasAlpha);
				Animation.Timeline pastedTimeline = animEditor.findTwoColorTimeline(animation,
					twoColorTimeline.getSlotIndex());
				int pastedFrame = animEditor.findFrame(pastedTimeline, time);
				if (pastedFrame >= 0) animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
				canvas.selectTwoColorKeyframe(slot, time);
				doc.scrubTo(time);
				recordTimelineChange("粘贴双色染色关键帧", before);
				notifyChanged();
				return;
			}
			Bone bone = canvas.selectedBone();
			if (bone == null || copiedEventKey || copiedDrawOrderKey || copiedIkKey || copiedTransformKey
				|| copiedPathKey || copiedPhysicsKey || copiedSliderKey
				|| copiedSequenceKey || copiedInheritKey || copiedAttachmentKey || copiedColorKey
				|| copiedRgbKey || copiedAlphaKey || copiedTwoColorKey || copiedDeformKey
				|| copiedProp == null || copiedProp != canvas.selProp || copiedCurve == null) return;
			int boneIndex = bone.getData().getIndex();
			animEditor.addKeyframe(animation, copiedProp, boneIndex, time, copiedValue1, copiedValue2);
			Animation.Timeline pastedTimeline = animEditor.findTimeline(animation, copiedProp, boneIndex);
			int pastedFrame = animEditor.findFrame(pastedTimeline, time);
			if (pastedFrame >= 0) animEditor.applyCurveSnapshot(animation, pastedTimeline, pastedFrame, copiedCurve);
			canvas.selectKeyframe(bone, copiedProp, time);
			doc.scrubTo(time);
			recordTimelineChange("粘贴关键帧", before);
			notifyChanged();
		} catch (RuntimeException error) {
			showStatus("无法粘贴关键帧：" + error.getMessage());
		}
	}

	public void keySelectedPose() {
		Animation animation = currentAnim();
		java.util.List<Bone> bones = new ArrayList<>(doc.getSelectedBones());
		if (animation == null || bones.isEmpty()) return;
		float time = canvas.snapTime(doc.getCurrentTime());
		try {
			BoneKeyframeBatch.Result result = BoneKeyframeBatch.apply(doc, animEditor, animation, bones,
				null, time, bone -> axisPreferences(animation, bone));
			canvas.selectKeyframe(bones.get(0), AnimationEditor.Prop.ROTATE, time);
			doc.scrubTo(time);
			result.record(history, "记录骨骼姿态");
			if (result.changed()) notifyChanged();
			showInfo(result.changed() ? "已记录 " + bones.size() + " 个骨骼的完整姿态" : "当前时间的姿态关键帧未变化");
		} catch (RuntimeException error) {
			showStatus("无法记录姿态：" + error.getMessage());
		}
	}

	/** Writes one completed bone transform to the current animation when Auto Key is enabled. */
	public boolean autoKeyBoneTransform(Bone bone, AnimationEditor.Prop editedProp) {
		return bone != null && autoKeyBoneTransforms(java.util.List.of(bone), editedProp);
	}

	public boolean autoKeyBoneTransforms(java.util.List<Bone> bones, AnimationEditor.Prop editedProp) {
		if (!autoKeyBtn.isChecked()) return false;
		Animation animation = currentAnim();
		if (animation == null || editedProp == null || bones == null || bones.isEmpty() || doc.skeleton == null) return false;
		try {
			float time = canvas.snapTime(doc.getCurrentTime());
			BoneKeyframeBatch.Result result = BoneKeyframeBatch.apply(doc, animEditor, animation, bones,
				editedProp, time, bone -> axisPreferences(animation, bone));
			Bone primary = bones.get(0);
			boolean[] axes = axisPreferences(animation, primary);
			Array<AnimationEditor.Prop> props = animEditor.transformKeyProps(animation,
				primary.getData().getIndex(), editedProp, axes[0], axes[1], axes[2]);
			canvas.selectKeyframe(primary, props.first(), time);
			doc.scrubTo(time);
			result.record(history, "自动关键帧");
			if (result.changed()) {
				notifyChanged();
				showInfo("已自动记录 " + bones.size() + " 个骨骼的" + propLabel(editedProp));
			} else {
				if (history != null) history.cancel();
				doc.refreshCurrentAnimationPose();
				canvas.rebuild();
			}
			return true;
		} catch (RuntimeException error) {
			if (history != null) history.rollbackPending();
			else doc.refreshCurrentAnimationPose();
			showStatus("无法自动记录关键帧：" + error.getMessage());
			return true;
		}
	}

	private boolean[] axisPreferences(Animation animation, Bone bone) {
		int index = bone.getData().getIndex();
		return new boolean[] {
			axisSeparationEnabled(animation, index, AnimationEditor.AxisGroup.TRANSLATE),
			axisSeparationEnabled(animation, index, AnimationEditor.AxisGroup.SCALE),
			axisSeparationEnabled(animation, index, AnimationEditor.AxisGroup.SHEAR)
		};
	}

	private float parseNumber(TextField field, String label) {
		try {
			return UiNumbers.parseFinite(field.getText());
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(label + " 必须是有限数值", error);
		}
	}

	static String propLabel(AnimationEditor.Prop prop) {
		return switch (prop) {
		case ROTATE -> "旋转";
		case SCALE -> "缩放";
		case SCALE_X -> "缩放 X";
		case SCALE_Y -> "缩放 Y";
		case TRANSLATE -> "平移";
		case TRANSLATE_X -> "平移 X";
		case TRANSLATE_Y -> "平移 Y";
		case SHEAR -> "剪切";
		case SHEAR_X -> "剪切 X";
		case SHEAR_Y -> "剪切 Y";
		};
	}

	static String valueLabel(AnimationEditor.Prop prop, int component) {
		if (prop == null) return "数值";
		return switch (prop) {
		case ROTATE -> "角度";
		case TRANSLATE -> component == 0 ? "X" : "Y";
		case TRANSLATE_X -> "X";
		case TRANSLATE_Y -> "Y";
		case SCALE -> component == 0 ? "X 倍率" : "Y 倍率";
		case SCALE_X -> "X 倍率";
		case SCALE_Y -> "Y 倍率";
		case SHEAR -> component == 0 ? "X 角度" : "Y 角度";
		case SHEAR_X -> "X 角度";
		case SHEAR_Y -> "Y 角度";
		};
	}

	static float eventMarkerOffset(EventTimeline timeline, int frame) {
		int start = eventGroupStart(timeline, frame);
		int end = eventGroupEnd(timeline, frame);
		return eventMarkerOffset(frame, start, end);
	}

	private static float eventMarkerOffset(int frame, int start, int end) {
		int count = end - start + 1;
		return count <= 1 ? 0f : -6f + 12f * (frame - start) / (count - 1f);
	}

	static String eventOccurrenceLabel(EventTimeline timeline, int frame) {
		int start = eventGroupStart(timeline, frame);
		int count = eventGroupEnd(timeline, frame) - start + 1;
		return count <= 1 ? "" : " · 同时刻 " + (frame - start + 1) + "/" + count;
	}

	private static int eventGroupStart(EventTimeline timeline, int frame) {
		requireEventFrame(timeline, frame);
		float time = timeline.getFrames()[frame];
		int start = frame;
		while (start > 0 && Float.compare(timeline.getFrames()[start - 1], time) == 0) start--;
		return start;
	}

	private static int eventGroupEnd(EventTimeline timeline, int frame) {
		requireEventFrame(timeline, frame);
		float time = timeline.getFrames()[frame];
		int end = frame;
		while (end + 1 < timeline.getFrameCount()
			&& Float.compare(timeline.getFrames()[end + 1], time) == 0) end++;
		return end;
	}

	private static void requireEventFrame(EventTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("事件关键帧索引无效");
	}

	private void showEventDefinitionsDialog() {
		Stage stage = getStage();
		if (stage == null || doc.data == null) return;
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("事件定义");
		window.setModal(true);
		kit.padWindow(window, 10f);
		SelectBox<String> definitionBox = kit.selectBox();
		TextButton add = new TextButton("新建", kit.toolButton);
		TextButton remove = new TextButton("删除", kit.toolButton);
		Table selector = new Table();
		selector.add(definitionBox).width(220).padRight(5);
		selector.add(add).width(64).padRight(4);
		selector.add(remove).width(64);
		window.add(selector).colspan(2).left().padBottom(7).row();

		TextField nameField = new TextField("", kit.textFieldStyle);
		nameField.setMaxLength(64);
		TextField intField = new TextField("0", kit.textFieldStyle);
		intField.setMaxLength(12);
		intField.setTextFieldFilter((field, c) -> Character.isDigit(c) || c == '-' || c == '+');
		TextField floatField = createBezierField(0f);
		TextArea stringField = new TextArea("", kit.textFieldStyle);
		stringField.setPrefRows(2);
		stringField.setMaxLength(512);
		TextField audioField = new TextField("", kit.textFieldStyle);
		audioField.setMaxLength(260);
		TextField volumeField = createBezierField(0f);
		TextField balanceField = createBezierField(0f);
		addDefinitionRow(window, "名称", nameField);
		addDefinitionRow(window, "整数", intField);
		addDefinitionRow(window, "浮点", floatField);
		window.add(new Label("字符串", kit.labelStyle)).right().padRight(6).padTop(5);
		window.add(stringField).width(280).height(52).padTop(5).row();
		addDefinitionRow(window, "音频路径", audioField);
		addDefinitionRow(window, "默认音量", volumeField);
		addDefinitionRow(window, "默认平衡", balanceField);
		Label errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(2).width(380).left().padTop(5).row();

		TextButton apply = new TextButton("应用", kit.accentButton);
		TextButton closeButton = new TextButton("关闭", kit.toolButton);
		Runnable[] load = new Runnable[1];
		Runnable[] refresh = new Runnable[1];
		Runnable[] submitDefinition = new Runnable[1];
		String[] requestedSelection = {null};
		boolean[] loading = {false};
		boolean[] hadAudio = {false};
		boolean[] closed = {false};
		Runnable updateAudioControls = () -> {
			boolean enabled = !audioField.getText().isBlank() && !audioField.isDisabled();
			if (!loading[0] && enabled && !hadAudio[0]) volumeField.setText("1");
			volumeField.setDisabled(!enabled);
			balanceField.setDisabled(!enabled);
			hadAudio[0] = enabled;
		};
		audioField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { updateAudioControls.run(); }
		});

		load[0] = () -> {
			EventData definition = findEventDefinition(doc.data, definitionBox.getSelected());
			boolean enabled = definition != null;
			loading[0] = true;
			if (enabled) {
				Event setup = definition.getSetupPose();
				nameField.setText(definition.getName());
				intField.setText(Integer.toString(setup.getInt()));
				floatField.setText(UiNumbers.format(setup.getFloat()));
				stringField.setText(setup.getString() == null ? "" : setup.getString());
				audioField.setText(definition.getAudioPath() == null ? "" : definition.getAudioPath());
				volumeField.setText(UiNumbers.format(setup.getVolume()));
				balanceField.setText(UiNumbers.format(setup.getBalance()));
			} else {
				nameField.setText("");
				intField.setText("0");
				floatField.setText("0");
				stringField.setText("");
				audioField.setText("");
				volumeField.setText("0");
				balanceField.setText("0");
			}
			for (TextField field : new TextField[] {nameField, intField, floatField, stringField, audioField})
				field.setDisabled(!enabled);
			apply.setDisabled(!enabled);
			remove.setDisabled(!enabled);
			loading[0] = false;
			hadAudio[0] = enabled && !audioField.getText().isBlank();
			updateAudioControls.run();
			errorLabel.setText("");
		};
		refresh[0] = () -> {
			loading[0] = true;
			definitionBox.setItems(eventDefinitionNames());
			String selection = requestedSelection[0];
			if (selection != null && definitionBox.getItems().contains(selection, false))
				definitionBox.setSelected(selection);
			loading[0] = false;
			requestedSelection[0] = null;
			load[0].run();
		};
		definitionBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { if (!loading[0]) load[0].run(); }
		});

		add.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				try {
					SkeletonStructureSnapshot before = history == null ? null
						: SkeletonStructureSnapshot.capture(skeletonEditor);
					EventData created = animEditor.addEventDefinition("event");
					if (history != null) {
						SkeletonStructureSnapshot after = SkeletonStructureSnapshot.capture(skeletonEditor);
						history.record("新建事件定义", before::restore, after::restore);
					}
					preferredEventDefinitionName = created.getName();
					requestedSelection[0] = created.getName();
					refresh[0].run();
					notifyChanged();
					showInfo("已新建事件定义 " + created.getName());
				} catch (RuntimeException error) {
					errorLabel.setText(error.getMessage());
				}
			}
		});
		submitDefinition[0] = () -> {
			EventData current = findEventDefinition(doc.data, definitionBox.getSelected());
			if (current == null) return;
			try {
				SkeletonStructureSnapshot before = history == null ? null
					: SkeletonStructureSnapshot.capture(skeletonEditor);
				long revision = doc.revision;
				EventData updated = animEditor.updateEventDefinition(current, nameField.getText(),
					parseInteger(intField, "整数"), parseNumber(floatField, "浮点"), stringField.getText(),
					audioField.getText(), parseNumber(volumeField, "默认音量"),
					parseNumber(balanceField, "默认平衡"));
				boolean changed = doc.revision != revision;
				if (changed && history != null) {
					SkeletonStructureSnapshot after = SkeletonStructureSnapshot.capture(skeletonEditor);
					history.record("编辑事件定义", before::restore, after::restore);
				}
				preferredEventDefinitionName = updated.getName();
				requestedSelection[0] = updated.getName();
				refresh[0].run();
				if (changed) notifyChanged();
				showInfo(changed ? "事件定义已更新" : "事件定义未变化");
			} catch (RuntimeException error) {
				errorLabel.setText(error.getMessage());
			}
		};
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				submitDefinition[0].run();
			}
		});
		remove.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				EventData definition = findEventDefinition(doc.data, definitionBox.getSelected());
				if (definition == null) return;
				String reason = animEditor.getEventDeleteBlockReason(definition);
				if (reason != null) { errorLabel.setText("无法删除：" + reason); return; }
				UiDialogs.confirm(stage, kit, "删除事件定义", "删除事件定义 " + definition.getName() + "？"
					+ (history == null ? "此操作无法撤销。" : "可通过撤销恢复。"),
					"删除", () -> {
						SkeletonStructureSnapshot before = history == null ? null
							: SkeletonStructureSnapshot.capture(skeletonEditor);
						if (!animEditor.removeEventDefinition(definition)) {
							errorLabel.setText("事件定义已发生变化，无法删除");
							return;
						}
						if (history != null) {
							SkeletonStructureSnapshot after = SkeletonStructureSnapshot.capture(skeletonEditor);
							history.record("删除事件定义", before::restore, after::restore);
						}
						if (definition.getName().equals(preferredEventDefinitionName))
							preferredEventDefinitionName = null;
						refresh[0].run();
						notifyChanged();
						showInfo("事件定义已删除");
					});
			}
		});

		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousKeyboardFocus);
			canvas.rebuild();
		};
		closeButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				UiDialogs.KeyAction action = UiDialogs.keyAction(keycode);
				if (action == UiDialogs.KeyAction.CONFIRM && stage.getKeyboardFocus() instanceof TextArea
					&& !controlPressed()) return false;
				return switch (action) {
				case CONFIRM -> { submitDefinition[0].run(); yield true; }
				case CANCEL -> { close.run(); yield true; }
				case NONE -> false;
				};
			}
		});
		Table buttons = new Table();
		buttons.add(apply).width(80).height(28).padRight(6);
		buttons.add(closeButton).width(80).height(28);
		window.add(buttons).colspan(2).right().padTop(7);

		Animation.Timeline selectedTimeline = canvas == null ? null : canvas.selectedTimeline();
		if (selectedTimeline instanceof EventTimeline events) {
			int frame = canvas.selectedFrame(events);
			if (frame >= 0) requestedSelection[0] = animEditor.eventAt(events, frame).getData().getName();
		}
		refresh[0].run();
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		if (!definitionBox.getItems().isEmpty()) stage.setKeyboardFocus(nameField);
	}

	private void showIkConstraintsDialog() {
		Stage stage = getStage();
		if (stage == null || skeletonEditor == null || doc.data == null) return;
		IkConstraintDialog.show(stage, doc, kit, skeletonEditor, history, onConstraintChanged, onStatus);
	}

	private void showTransformConstraintsDialog() {
		Stage stage = getStage();
		if (stage == null || skeletonEditor == null || doc.data == null) return;
		TransformConstraintDialog.show(stage, doc, kit, skeletonEditor, history, onConstraintChanged, onStatus);
	}

	private void showPathConstraintsDialog() {
		Stage stage = getStage();
		if (stage == null || skeletonEditor == null || doc.data == null) return;
		PathConstraintDialog.show(stage, doc, kit, skeletonEditor, history, onConstraintChanged, onStatus);
	}

	private void showPhysicsConstraintsDialog() {
		Stage stage = getStage();
		if (stage == null || skeletonEditor == null || doc.data == null) return;
		PhysicsConstraintDialog.show(stage, doc, kit, skeletonEditor, history, onConstraintChanged, onStatus);
	}

	private void showSliderConstraintsDialog() {
		Stage stage = getStage();
		if (stage == null || skeletonEditor == null || doc.data == null) return;
		SliderConstraintDialog.show(stage, doc, kit, skeletonEditor, history, onConstraintChanged, onStatus);
	}

	private void toggleAxisSeparation(AnimationEditor.AxisGroup group, boolean separated) {
		Animation animation = currentAnim();
		Bone bone = doc.getSelectedBone();
		if (animation == null || bone == null) { refreshAxisSeparationControls(); return; }
		int boneIndex = bone.getData().getIndex();
		AnimationEditor.AxisConversionSnapshot before = history == null ? null
			: animEditor.snapshotAxisConversion(animation, group, boneIndex);
		try {
			long revision = doc.revision;
			boolean converted = separated
				? animEditor.separateAxisTimeline(animation, group, boneIndex)
				: animEditor.combineAxisTimelines(animation, group, boneIndex);
			setAxisSeparationPreference(animation, boneIndex, group, separated);
			if (converted) {
				if (history != null && before != null) {
					AnimationEditor.AxisConversionSnapshot after =
						animEditor.snapshotAxisConversion(animation, group, boneIndex);
					history.record((separated ? "分离" : "合并") + axisGroupLabel(group) + " XY 轨道",
						() -> animEditor.restoreAxisConversion(before),
						() -> animEditor.restoreAxisConversion(after));
				}
				notifyChanged();
			} else {
				canvas.rebuild();
				refreshControls();
			}
			if (doc.revision == revision && converted)
				throw new IllegalStateException("XY 轨道转换未生效");
			showInfo(axisGroupLabel(group) + (separated ? "已切换为 X/Y 独立轨道" : "已切换为组合轨道"));
		} catch (RuntimeException error) {
			if (before != null) {
				try { animEditor.restoreAxisConversion(before); }
				catch (RuntimeException ignored) {}
			}
			refreshAxisSeparationControls();
			showStatus("无法转换 " + axisGroupLabel(group) + " XY：" + error.getMessage());
		}
	}

	private Array<AnimationEditor.Prop> axisTimelineProps(AnimationEditor.AxisGroup group) {
		Array<AnimationEditor.Prop> props = new Array<>(3);
		switch (group) {
		case TRANSLATE -> {
			props.add(AnimationEditor.Prop.TRANSLATE);
			props.add(AnimationEditor.Prop.TRANSLATE_X);
			props.add(AnimationEditor.Prop.TRANSLATE_Y);
		}
		case SCALE -> {
			props.add(AnimationEditor.Prop.SCALE);
			props.add(AnimationEditor.Prop.SCALE_X);
			props.add(AnimationEditor.Prop.SCALE_Y);
		}
		case SHEAR -> {
			props.add(AnimationEditor.Prop.SHEAR);
			props.add(AnimationEditor.Prop.SHEAR_X);
			props.add(AnimationEditor.Prop.SHEAR_Y);
		}
		}
		return props;
	}

	private String axisGroupLabel(AnimationEditor.AxisGroup group) {
		return switch (group) {
		case TRANSLATE -> "平移";
		case SCALE -> "缩放";
		case SHEAR -> "剪切";
		};
	}

	private void setAxisSeparationPreference(Animation animation, int boneIndex,
		AnimationEditor.AxisGroup group, boolean separated) {
		if (animation == null || doc.data == null || boneIndex < 0 || boneIndex >= doc.data.getBones().size) return;
		doc.setAxisSeparationPreferred(animation.getName(), doc.data.getBones().get(boneIndex).getName(),
			group.name(), separated);
	}

	private boolean axisSeparationEnabled(Animation animation, int boneIndex, AnimationEditor.AxisGroup group) {
		if (animation == null) return false;
		if (animEditor.isAxisSeparated(animation, group, boneIndex)) return true;
		AnimationEditor.Prop combined = switch (group) {
		case TRANSLATE -> AnimationEditor.Prop.TRANSLATE;
		case SCALE -> AnimationEditor.Prop.SCALE;
		case SHEAR -> AnimationEditor.Prop.SHEAR;
		};
		if (animEditor.findTimeline(animation, combined, boneIndex) != null) return false;
		if (doc.data == null || boneIndex < 0 || boneIndex >= doc.data.getBones().size) return false;
		return doc.isAxisSeparationPreferred(animation.getName(), doc.data.getBones().get(boneIndex).getName(),
			group.name());
	}

	private void refreshAxisSeparationControls() {
		Animation animation = currentAnim();
		Bone bone = doc.getSelectedBone();
		boolean enabled = animation != null && bone != null;
		int boneIndex = enabled ? bone.getData().getIndex() : -1;
		updating = true;
		translateSeparateBtn.setChecked(enabled
			&& axisSeparationEnabled(animation, boneIndex, AnimationEditor.AxisGroup.TRANSLATE));
		scaleSeparateBtn.setChecked(enabled
			&& axisSeparationEnabled(animation, boneIndex, AnimationEditor.AxisGroup.SCALE));
		shearSeparateBtn.setChecked(enabled
			&& axisSeparationEnabled(animation, boneIndex, AnimationEditor.AxisGroup.SHEAR));
		updating = false;
		translateSeparateBtn.setDisabled(!enabled);
		scaleSeparateBtn.setDisabled(!enabled);
		shearSeparateBtn.setDisabled(!enabled);
		refreshAxisSeparationStyles();
	}

	private void refreshAxisSeparationStyles() {
		translateSeparateBtn.setStyle(translateSeparateBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
		scaleSeparateBtn.setStyle(scaleSeparateBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
		shearSeparateBtn.setStyle(shearSeparateBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
	}

	private void addDefinitionRow(Window window, String label, TextField field) {
		window.add(new Label(label, kit.labelStyle)).right().padRight(6).padTop(5);
		window.add(field).width(280).padTop(5).row();
	}

	// ================= 动画管理 =================

	private void selectAnimation(String name) {
		if (name == null || doc.animationState == null) return;
		if (!doc.setAnimation(name)) return;
		canvas.clearSelection();
		canvas.rebuild();
		refreshControls();
		if (onAnimationChanged != null) onAnimationChanged.run();
	}

	private void addAnimation() {
		if (doc.data == null || doc.animationState == null) return;
		Animation anim = animEditor.addAnimation("newAnim");
		if (anim == null) return;
		doc.setAnimation(anim.getName());
		recordCreatedAnimation("新建动画", anim, doc.data.getAnimations().indexOf(anim, true));
		canvas.clearSelection();
		rebuildAnimBox(anim.getName());
		notifyChanged();
	}

	private void duplicateAnimation() {
		Animation source = currentAnim();
		if (source == null || doc.animationState == null) return;
		try {
			Animation duplicate = animEditor.duplicateAnimation(source);
			doc.setAnimation(duplicate.getName());
			recordCreatedAnimation("复制动画", duplicate,
				doc.data.getAnimations().indexOf(duplicate, true));
			canvas.clearSelection();
			rebuildAnimBox(duplicate.getName());
			notifyChanged();
		} catch (RuntimeException error) {
			showStatus("无法复制动画：" + error.getMessage());
		}
	}

	/** Imports a validated animation source and records creation as one undoable edit. */
	public Animation importAnimation(SkeletonData sourceData, Animation sourceAnimation) {
		AnimationEditor.AnimationImportResult result = importAnimations(sourceData, java.util.List.of(sourceAnimation),
			AnimationEditor.AnimationImportConflict.RENAME);
		return result.importedAnimations().get(0);
	}

	/** Imports a prevalidated batch and records the complete operation as one undoable edit. */
	public AnimationEditor.AnimationImportResult importAnimations(SkeletonData sourceData,
		Iterable<Animation> sourceAnimations, AnimationEditor.AnimationImportConflict conflict) {
		AnimationEditor.AnimationImportResult result = animEditor.importAnimations(sourceData, sourceAnimations, conflict);
		if (result.changes().isEmpty()) return result;
		Animation imported = result.importedAnimations().get(result.importedAnimations().size() - 1);
		doc.setAnimation(imported.getName());
		if (history != null) history.record("导入 " + result.changes().size() + " 个动画",
			() -> restoreAnimationImport(result, false), () -> restoreAnimationImport(result, true));
		canvas.clearSelection();
		rebuildAnimBox(imported.getName());
		notifyChanged();
		return result;
	}

	private void restoreAnimationImport(AnimationEditor.AnimationImportResult result, boolean imported) {
		animEditor.restoreAnimationImport(result, imported);
		String selected = doc.getCurrentAnimationName();
		if (imported && !result.importedAnimations().isEmpty())
			selected = result.importedAnimations().get(result.importedAnimations().size() - 1).getName();
		if (doc.data != null && doc.data.findAnimation(selected) != null) doc.setAnimation(selected);
		canvas.clearSelection();
		rebuildAnimBox(selected);
		notifyChanged();
	}

	private void cleanAnimation() {
		Animation animation = currentAnim();
		Stage stage = getStage();
		if (animation == null || stage == null) return;
		UiDialogs.confirm(stage, kit, "清理动画",
			"删除可由相邻线性关键帧精确重建的冗余中间帧？步进、贝塞尔、离散帧和保持帧会保留。",
			"清理", () -> cleanAnimationNow(animation));
	}

	private void cleanAnimationNow(Animation animation) {
		AnimationEditor.TimelineSnapshot[] before = null;
		long revision = doc.revision;
		try {
			before = history == null ? null : animEditor.snapshotAnimationTimelines(animation);
			AnimationEditor.CleanupResult result = animEditor.cleanupAnimation(animation, 0f);
			if (!result.changed()) {
				showInfo("没有可安全删除的冗余关键帧");
				return;
			}
			canvas.clearSelection();
			recordTimelineChanges("清理动画", before);
			notifyChanged();
			showInfo("已清理 " + result.removedKeyframes() + " 个关键帧，涉及 "
				+ result.changedTimelines() + " 条轨道");
		} catch (RuntimeException error) {
			if (before != null && doc.revision != revision) restoreTimelines(before);
			showStatus("无法清理动画：" + error.getMessage());
		}
	}

	private void renameDialog() {
		Animation anim = currentAnim();
		if (anim == null) return;
		Stage st = getStage();
		if (st == null) return;
		UiDialogs.promptName(st, kit, "重命名动画", anim.getName(), 64, name -> {
			String oldName = anim.getName();
			String renamed = animEditor.renameAnimation(anim, name);
			if (renamed == null) {
				showStatus("无法重命名动画");
				return false;
			}
			if (oldName.equals(renamed)) {
				showInfo("动画名称未变化");
				return true;
			}
			recordRenamedAnimation(oldName, renamed);
			rebuildAnimBox(renamed);
			canvas.clearSelection();
			canvas.rebuild();
			if (onDataChanged != null) onDataChanged.run();
			return true;
		});
	}

	private void removeAnimation() {
		Animation anim = currentAnim();
		if (anim == null) return;
		String blockReason = animEditor.getAnimationDeleteBlockReason(anim);
		if (blockReason != null) {
			showStatus("无法删除：" + blockReason);
			return;
		}
		Stage stage = getStage();
		if (stage == null) return;
		UiDialogs.confirm(stage, kit, "删除动画",
			"删除动画 " + anim.getName() + "？", "删除", () -> removeAnimationNow(anim));
	}

	private void removeAnimationNow(Animation anim) {
		int index = doc.data == null ? -1 : doc.data.getAnimations().indexOf(anim, true);
		var axisPreferences = doc.axisSeparationPreferences();
		var axisStates = doc.axisCombineStates();
		var graphStates = doc.graphViewStates();
		var treeState = doc.treeViewState();
		var timelineState = doc.timelineViewState();
		var workspaceState = doc.graphWorkspaceState();
		float defaultMix = doc.defaultAnimationMix();
		var mixes = doc.animationMixes();
		if (!animEditor.removeAnimation(anim)) {
			String reason = animEditor.getAnimationDeleteBlockReason(anim);
			showStatus("无法删除动画：" + (reason == null ? "项目数据已发生变化" : reason));
			return;
		}
		if (history != null) {
			history.record("删除动画", () -> {
				restoreAnimationForHistory(anim, index);
				doc.restoreAxisSeparationPreferences(axisPreferences);
				doc.restoreAxisCombineStates(axisStates.values());
				doc.restoreGraphViewStates(graphStates);
				doc.setTreeViewState(treeState);
				doc.setTimelineViewState(timelineState);
				doc.setGraphWorkspaceState(workspaceState);
				doc.restoreAnimationMixSettings(defaultMix, mixes);
			}, () -> removeAnimationForHistory(anim.getName()));
		}
		canvas.clearSelection();
		rebuildAnimBox(doc.getCurrentAnimationName());
		notifyChanged();
	}

	private void recordCreatedAnimation(String label, Animation animation, int index) {
		if (history == null || animation == null || index < 0) return;
		history.record(label, () -> removeAnimationForHistory(animation.getName()),
			() -> restoreAnimationForHistory(animation, index));
	}

	private void recordRenamedAnimation(String oldName, String newName) {
		if (history == null) return;
		history.record("重命名动画", () -> renameAnimationForHistory(newName, oldName),
			() -> renameAnimationForHistory(oldName, newName));
	}

	private void renameAnimationForHistory(String currentName, String requestedName) {
		Animation current = doc.data == null ? null : doc.data.findAnimation(currentName);
		String renamed = current == null ? null : animEditor.renameAnimation(current, requestedName);
		if (!requestedName.equals(renamed))
			throw new IllegalStateException("无法恢复动画名称：" + requestedName);
		doc.setAnimation(requestedName);
	}

	private void removeAnimationForHistory(String name) {
		Animation current = doc.data == null ? null : doc.data.findAnimation(name);
		if (current == null || !animEditor.removeAnimation(current))
			throw new IllegalStateException("无法恢复动画删除：" + name);
	}

	private void restoreAnimationForHistory(Animation animation, int index) {
		if (doc.data == null || animation == null || doc.data.findAnimation(animation.getName()) != null)
			throw new IllegalStateException("无法恢复动画：" + (animation == null ? "" : animation.getName()));
		doc.data.getAnimations().insert(Math.max(0, Math.min(index, doc.data.getAnimations().size)), animation);
		doc.markDirty();
		if (!doc.setAnimation(animation.getName()))
			throw new IllegalStateException("无法选择恢复的动画：" + animation.getName());
	}

	private void rebuildAnimBox(String selectName) {
		updating = true;
		Array<String> animations = new Array<>();
		if (doc.data != null) {
			for (Animation a : doc.data.getAnimations()) animations.add(a.getName());
		}
		animBox.setItems(animations);
		if (selectName != null && animations.contains(selectName, false)) animBox.setSelected(selectName);
		else if (animations.notEmpty()) animBox.setSelectedIndex(0);
		updating = false;
		refreshControls();
	}

	private boolean applyProjectFps() {
		Float fps = parsedProjectFps();
		if (doc.data == null || fps == null || !SkeletonDocument.isValidProjectFps(fps)) {
			showStatus("FPS 必须是 1 到 1000 之间的数字");
			return false;
		}
		float previous = doc.data.getFps();
		if (!doc.setProjectFps(fps)) {
			showStatus("无法修改项目 FPS");
			return false;
		}
		refreshFpsField();
		if (Float.compare(previous, fps) == 0) {
			showInfo("项目 FPS 未变化");
			return true;
		}
		if (history != null) {
			if (SkeletonDocument.isValidProjectFps(previous)) {
				float applied = fps;
				history.record("修改项目 FPS", () -> restoreProjectFps(previous),
					() -> restoreProjectFps(applied));
			} else {
				// Imported invalid settings cannot be restored through the validated setter.
				history.clearSilently();
			}
		}
		canvas.rebuild();
		if (onProjectSettingsChanged != null) onProjectSettingsChanged.run();
		showInfo("项目 FPS 已更新为 " + UiNumbers.format(fps));
		return true;
	}

	private void resetProjectFps() {
		if (doc.data == null) return;
		updating = true;
		try {
			fpsField.setText(UiNumbers.format(DEFAULT_PROJECT_FPS));
		} finally {
			updating = false;
		}
		if (applyProjectFps()) {
			focusCanvas();
			showInfo("项目 FPS 已恢复默认值 " + UiNumbers.format(DEFAULT_PROJECT_FPS));
		}
	}

	private void applyProjectFpsAfterEdit() {
		if (doc.data == null || projectFpsDraftIsEquivalent(doc.data.getFps(), fpsField.getText())) return;
		if (!applyProjectFps()) refreshFpsField();
	}

	private void restoreProjectFps(float fps) {
		if (!doc.setProjectFps(fps))
			throw new IllegalStateException("无法恢复项目 FPS：" + UiNumbers.format(fps));
	}

	private Float parsedProjectFps() {
		return parseProjectFps(fpsField.getText());
	}

	static Float parseProjectFps(String text) {
		if (text == null || text.isBlank()) return null;
		try {
			return UiNumbers.parseFinite(text);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	static boolean projectFpsDraftIsEquivalent(float currentFps, String text) {
		Float requested = parseProjectFps(text);
		return requested != null && SkeletonDocument.isValidProjectFps(requested)
			&& Float.compare(currentFps, requested) == 0;
	}

	static FocusListener applyFpsOnFocusLost(Runnable apply) {
		Objects.requireNonNull(apply, "FPS apply action");
		return new FocusListener() {
			@Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
				if (!focused) apply.run();
			}
		};
	}

	private void refreshFpsField() {
		updating = true;
		fpsField.setText(doc.data == null ? "" : UiNumbers.format(frameRate()));
		updating = false;
		refreshControls();
	}

	private void refreshControls() {
		boolean hasDocument = doc.data != null && doc.animationState != null;
		boolean hasAnimation = hasDocument && currentAnim() != null;
		animBox.setDisabled(!hasDocument || animBox.getItems().isEmpty());
		trackFilterBox.setDisabled(!hasDocument);
		trackSearchField.setDisabled(!hasDocument);
		fpsResetBtn.setDisabled(!hasDocument);
		fpsField.setDisabled(!hasDocument);
		zoomSlider.setDisabled(!hasDocument);
		zoomResetBtn.setDisabled(!hasDocument);
		addBtn.setDisabled(!hasDocument);
		duplicateBtn.setDisabled(!hasAnimation);
		renBtn.setDisabled(!hasAnimation);
		delBtn.setDisabled(!hasAnimation);
		eventDefinitionsBtn.setDisabled(!hasDocument);
		ikConstraintsBtn.setDisabled(!hasDocument || doc.data.getBones().size < 2);
		transformConstraintsBtn.setDisabled(!hasDocument || doc.data.getBones().size < 2);
		pathConstraintsBtn.setDisabled(!hasDocument || doc.data.getBones().isEmpty() || doc.data.getSlots().isEmpty());
		physicsConstraintsBtn.setDisabled(!hasDocument || doc.data.getBones().isEmpty());
		sliderConstraintsBtn.setDisabled(!hasDocument || doc.data.getAnimations().isEmpty());
		autoKeyBtn.setDisabled(!hasAnimation);
		poseKeyBtn.setDisabled(!hasAnimation || doc.getSelectedBone() == null);
		refreshAxisSeparationControls();
	}

	private Animation currentAnim() {
		if (doc.data == null) return null;
		String name = doc.getCurrentAnimationName();
		return name.isEmpty() ? null : doc.data.findAnimation(name);
	}

	private float frameRate() {
		return UiNumbers.effectiveFps(doc.data == null ? DEFAULT_PROJECT_FPS : doc.data.getFps());
	}

	private String selectedBoneName() {
		Bone selected = doc.getSelectedBone();
		return selected == null ? null : selected.getData().getName();
	}

	public void setOnDataChanged(Runnable r) { this.onDataChanged = r; }
	public void setHistory(EditHistory history) { this.history = history; }
	public void setOnSelectionChanged(Runnable r) { this.onSelectionChanged = r; }
	public void setOnAnimationChanged(Runnable r) { this.onAnimationChanged = r; }
	public void setOnProjectSettingsChanged(Runnable r) { this.onProjectSettingsChanged = r; }
	public void setOnConstraintChanged(Runnable r) { this.onConstraintChanged = r; }
	public void setOnTimeChanged(Runnable r) { this.onTimeChanged = r; }
	public void setOnStatus(java.util.function.BiConsumer<String, Boolean> listener) { this.onStatus = listener; }

	public void restoreViewSettings(float zoom, String filterKey) {
		timelineZoom = normalizeZoom(zoom);
		timelineVerticalZoom = timelineZoom;
		zoomSlider.setValue(timelineZoom);
		refreshZoomLabel();
		updating = true;
		trackFilterBox.setSelected(trackFilterLabel(normalizeTrackFilterKey(filterKey)));
		updating = false;
		canvas.rebuild();
	}

	/** Copies the live main-timeline editing context into the active document for Sidecar persistence. */
	public void persistViewState() {
		if (canvas != null) canvas.persistViewState();
		if (graphWorkspace != null && graphWorkspace.getParent() != null)
			graphWorkspace.persistWorkspaceState(true);
	}

	public float getZoom() { return normalizeZoom(zoomSlider.getValue()); }

	private void syncZoomSlider() {
		float requestedZoom = normalizeZoom(zoomSlider.getValue());
		if (Float.compare(requestedZoom, timelineZoom) == 0) return;
		float anchorX = scroll == null || canvas == null ? 0f : canvas.timeOriginX();
		float anchorY = scroll == null ? 0f : scroll.getHeight();
		applyTimelineZoom(requestedZoom, anchorX, anchorY);
	}

	private void resetTimelineZoom() {
		zoomSlider.setValue(DEFAULT_ZOOM);
		float anchorX = scroll == null || canvas == null ? 0f : canvas.timeOriginX();
		float anchorY = scroll == null ? 0f : scroll.getHeight();
		applyTimelineZoom(DEFAULT_ZOOM, anchorX, anchorY);
	}

	private boolean handleTimelineWheel(float x, float y, float amountX, float amountY) {
		float amount = timelineWheelAmount(amountX, amountY);
		if (Math.abs(amount) <= 0.0001f || scroll == null) return false;
		boolean overDopesheetRuler = canvas != null && !canvas.curveView
			&& timelineRulerWheelRegion(x, y, scroll.getWidth(), scroll.getHeight(),
				timelineBoundaryX(canvas.labelWidth()), KeyframeCanvas.RULER_H);
		switch (timelineWheelAction(controlPressed(), shiftPressed(), overDopesheetRuler)) {
		case ZOOM -> {
			float targetZoom = timelineWheelZoom(timelineZoom, amount);
			if (Float.compare(targetZoom, timelineZoom) == 0) return true;
			applyTimelineZoom(targetZoom, x, y);
			zoomSlider.setValue(targetZoom);
		}
		case FRAME_SPACING -> {
			float targetZoom = timelineWheelZoom(timelineZoom, amount);
			if (Float.compare(targetZoom, timelineZoom) == 0) return true;
			applyTimelineFrameZoom(targetZoom, x);
			zoomSlider.setValue(targetZoom);
		}
		case HORIZONTAL -> {
			scroll.setScrollX(Math.max(0f,
				scroll.getScrollX() + amount * timelineWheelStep(timelineVerticalZoom)));
			scroll.updateVisualScroll();
		}
		case VERTICAL -> {
			scroll.setScrollY(Math.max(0f,
				scroll.getScrollY() + amount * timelineWheelStep(timelineVerticalZoom)));
			scroll.updateVisualScroll();
		}
		}
		return true;
	}

	private void applyTimelineZoom(float requestedZoom, float anchorX, float anchorY) {
		float nextZoom = normalizeZoom(requestedZoom);
		float previousZoom = timelineZoom;
		float previousVerticalZoom = timelineVerticalZoom;
		if (Float.compare(nextZoom, previousZoom) == 0
			&& Float.compare(nextZoom, previousVerticalZoom) == 0) return;
		float previousScrollX = scroll == null ? 0f : scroll.getScrollX();
		float previousScrollY = scroll == null ? 0f : scroll.getScrollY();
		float viewportHeight = scroll == null ? 0f : scroll.getHeight();
		float resolvedAnchorX = Float.isFinite(anchorX) ? Math.max(0f, anchorX) : 0f;
		float resolvedAnchorY = Float.isFinite(anchorY) ? Math.max(0f, anchorY) : viewportHeight;
		float oldPixelsPerSecond = timelinePixelsPerSecond(previousZoom, frameRate());
		float anchorTime = canvas == null || resolvedAnchorX <= timelineBoundaryX(canvas.labelWidth())
			? -1f : Math.max(0f,
				(previousScrollX + resolvedAnchorX - canvas.timeOriginX()) / oldPixelsPerSecond);
		timelineZoom = nextZoom;
		timelineVerticalZoom = nextZoom;
		refreshZoomLabel();
		if (canvas == null) return;
		canvas.rebuild();
		float targetScrollX = anchorTime < 0f ? previousScrollX
			: canvas.timeOriginX() + anchorTime * canvas.pixelsPerSecond() - resolvedAnchorX;
		float targetScrollY = zoomAnchoredVerticalScroll(previousScrollY, viewportHeight, resolvedAnchorY,
			previousVerticalZoom, nextZoom, KeyframeCanvas.RULER_H);
		Runnable restoreAnchor = () -> {
			scroll.layout();
			scroll.setScrollX(Math.max(0f, targetScrollX));
			scroll.setScrollY(Math.max(0f, targetScrollY));
			scroll.updateVisualScroll();
		};
		restoreAnchor.run();
		if (Gdx.app != null) Gdx.app.postRunnable(restoreAnchor);
	}

	private void applyTimelineFrameZoom(float requestedZoom, float anchorX) {
		float nextZoom = normalizeZoom(requestedZoom);
		float previousZoom = timelineZoom;
		if (Float.compare(nextZoom, previousZoom) == 0 || canvas == null) return;
		float previousScrollX = scroll == null ? 0f : scroll.getScrollX();
		float resolvedAnchorX = Float.isFinite(anchorX) ? Math.max(0f, anchorX) : 0f;
		float oldPixelsPerSecond = timelinePixelsPerSecond(previousZoom, frameRate());
		float anchorTime = resolvedAnchorX <= timelineBoundaryX(canvas.labelWidth()) ? -1f : Math.max(0f,
			(previousScrollX + resolvedAnchorX - canvas.timeOriginX()) / oldPixelsPerSecond);
		timelineZoom = nextZoom;
		refreshZoomLabel();
		canvas.rebuild();
		float targetScrollX = anchorTime < 0f ? previousScrollX
			: canvas.timeOriginX() + anchorTime * canvas.pixelsPerSecond() - resolvedAnchorX;
		Runnable restoreAnchor = () -> {
			scroll.layout();
			scroll.setScrollX(Math.max(0f, targetScrollX));
			scroll.updateVisualScroll();
		};
		restoreAnchor.run();
		if (Gdx.app != null) Gdx.app.postRunnable(restoreAnchor);
	}

	public String getTrackFilterKey() {
		String selected = trackFilterBox.getSelected();
		if (trackFilterMatches(selected, FILTER_SELECTED)) return FILTER_SELECTED;
		if (trackFilterMatches(selected, FILTER_KEYED)) return FILTER_KEYED;
		return FILTER_ALL;
	}

	static float normalizeZoom(float zoom) {
		if (!Float.isFinite(zoom)) return DEFAULT_ZOOM;
		float clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
		return Math.round(clamped / ZOOM_STEP) * ZOOM_STEP;
	}

	static String timelineZoomLabel(float zoom) {
		return String.format(Locale.ROOT, I18n.t("缩放: %.2f×", "Zoom: %.2f×"),
			normalizeZoom(zoom) / DEFAULT_ZOOM);
	}

	private void refreshZoomLabel() {
		zoomResetBtn.setText(timelineZoomLabel(timelineZoom));
	}

	static float centeredTextBaseline(float rowCenter, float textHeight) {
		if (!Float.isFinite(rowCenter) || !Float.isFinite(textHeight) || textHeight <= 0f)
			throw new IllegalArgumentException("Track label height must be finite and positive.");
		return rowCenter + textHeight / 2f;
	}

	static float timelineHierarchyLabelBaseline(float rowCenter, float textHeight, float fontScale) {
		if (!Float.isFinite(fontScale) || fontScale <= 0f)
			throw new IllegalArgumentException("Timeline label scale must be finite and positive.");
		return centeredTextBaseline(rowCenter, textHeight) + UiKit.BUTTON_TEXT_Y_OFFSET * fontScale;
	}

	static int rulerFrameStep(float pixelsPerSecond, float fps) {
		float resolvedPixels = Float.isFinite(pixelsPerSecond) && pixelsPerSecond > 0f
			? pixelsPerSecond : DEFAULT_ZOOM;
		double rawFrames = 60d * UiNumbers.effectiveFps(fps) / resolvedPixels;
		double magnitude = Math.pow(10d, Math.floor(Math.log10(rawFrames)));
		double normalized = rawFrames / magnitude;
		double multiplier = normalized < 1.5d ? 1d : normalized < 3.5d ? 2d
			: normalized < 7.5d ? 5d : 10d;
		return (int)Math.max(1d, Math.min(Integer.MAX_VALUE, multiplier * magnitude));
	}

	static int rulerMinorFrameStep(int majorFrameStep) {
		return 1;
	}

	static float timelinePixelsPerFrame(float zoom) {
		return normalizeZoom(zoom) / 5f;
	}

	static float timelinePixelsPerSecond(float zoom, float fps) {
		return timelinePixelsPerFrame(zoom) * UiNumbers.effectiveFps(fps);
	}

	static double firstRulerFrame(float visibleStartTime, float fps, int frameStep) {
		double startFrame = Math.max(0d, visibleStartTime) * UiNumbers.effectiveFps(fps);
		return Math.ceil(startFrame / Math.max(1, frameStep) - 0.000000001d) * Math.max(1, frameStep);
	}

	static String rulerFrameLabel(double frame) {
		return Long.toString(Math.round(frame));
	}

	static long rulerFrameIndex(float time, float fps) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("Timeline time must be finite");
		return Math.round((double)Math.max(0f, time) * UiNumbers.effectiveFps(fps));
	}

	static float rulerLabelBaseline(float rulerTop, boolean currentFrame) {
		if (!Float.isFinite(rulerTop)) throw new IllegalArgumentException("Ruler top must be finite");
		return rulerTop - (currentFrame ? 0f : 5f);
	}

	static float stickyRulerBottom(float canvasHeight, float rulerHeight, float visualScrollY) {
		if (!Float.isFinite(canvasHeight) || !Float.isFinite(rulerHeight) || !Float.isFinite(visualScrollY)
			|| canvasHeight < 0f || rulerHeight < 0f || visualScrollY < 0f)
			throw new IllegalArgumentException("Sticky ruler geometry must be finite and non-negative");
		return canvasHeight - rulerHeight - visualScrollY;
	}

	static boolean rulerScrubStarts(int button, float x, float y, float timelineBoundary,
		float rulerBottom, float rulerHeight) {
		return button == Input.Buttons.LEFT && Float.isFinite(x) && Float.isFinite(y)
			&& Float.isFinite(timelineBoundary) && Float.isFinite(rulerBottom) && Float.isFinite(rulerHeight)
			&& rulerHeight >= 0f && x >= timelineBoundary && y >= rulerBottom && y <= rulerBottom + rulerHeight;
	}

	static boolean rulerScrubCommitsSelection(boolean rulerScrubbing, int button) {
		return rulerScrubbing && button == Input.Buttons.LEFT;
	}

	static float centeredVisualLabelX(float centerX, float visualLeft, float visualRight) {
		if (!Float.isFinite(centerX) || !Float.isFinite(visualLeft) || !Float.isFinite(visualRight)
			|| visualRight < visualLeft) throw new IllegalArgumentException("Label bounds must be finite and ordered.");
		return centerX - (visualLeft + visualRight) / 2f;
	}

	static float rulerGlyphVisualWidth(float textureWidth, float advance, float scale) {
		if (!Float.isFinite(textureWidth) || !Float.isFinite(advance) || !Float.isFinite(scale)
			|| textureWidth < 0f || advance < 0f || scale <= 0f)
			throw new IllegalArgumentException("Glyph metrics must be finite and non-negative.");
		return Math.min(textureWidth, advance + 2f) * scale;
	}

	static String normalizeTrackFilterKey(String key) {
		return FILTER_SELECTED.equals(key) || FILTER_KEYED.equals(key) ? key : FILTER_ALL;
	}

	static SearchKeyAction searchKeyAction(int keycode, boolean hasQuery) {
		if (keycode == Input.Keys.ESCAPE)
			return hasQuery ? SearchKeyAction.CLEAR_SEARCH : SearchKeyAction.FOCUS_CANVAS;
		if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER || keycode == Input.Keys.DOWN)
			return SearchKeyAction.FOCUS_CANVAS;
		return SearchKeyAction.NONE;
	}

	static RebuildSelection rebuildSelection(boolean selectionExists, boolean trackVisible) {
		if (!selectionExists) return RebuildSelection.CLEAR;
		return trackVisible ? RebuildSelection.VISIBLE : RebuildSelection.HIDDEN;
	}

	static IdentityHashMap<BoneData, Array<SlotData>> indexSlotsByBone(Array<SlotData> slots) {
		IdentityHashMap<BoneData, Array<SlotData>> result = new IdentityHashMap<>();
		if (slots == null) return result;
		for (SlotData slot : slots)
			result.computeIfAbsent(slot.getBoneData(), ignored -> new Array<>()).add(slot);
		return result;
	}

	private static String trackFilterLabel(String key) {
		if (FILTER_SELECTED.equals(key)) return I18n.t(TRACK_SELECTED);
		if (FILTER_KEYED.equals(key)) return I18n.t(TRACK_KEYED);
		return I18n.t(TRACK_ALL);
	}

	private static String[] trackFilterLabels() {
		return new String[] {trackFilterLabel(FILTER_ALL), trackFilterLabel(FILTER_SELECTED),
			trackFilterLabel(FILTER_KEYED)};
	}

	private static boolean trackFilterMatches(String label, String key) {
		if (label == null) return false;
		return FILTER_SELECTED.equals(key) && I18n.matches(label, TRACK_SELECTED, "Selected Bone")
			|| FILTER_KEYED.equals(key) && I18n.matches(label, TRACK_KEYED, "Keyed Only")
			|| FILTER_ALL.equals(key) && I18n.matches(label, TRACK_ALL, "All Tracks");
	}

	private void showStatus(String message) { if (onStatus != null) onStatus.accept(message, true); }
	private void showInfo(String message) { if (onStatus != null) onStatus.accept(message, false); }

	private void notifyChanged() {
		doc.refreshCurrentAnimationPose();
		canvas.rebuild();
		if (onDataChanged != null) onDataChanged.run();
	}

	private AnimationEditor.TimelineSnapshot snapshotSelected(Animation animation, Animation.Timeline timeline) {
		if (history == null || animation == null || timeline == null) return null;
		if (timeline instanceof EventTimeline) return animEditor.snapshotEventTimeline(animation);
		if (timeline instanceof DrawOrderTimeline) return animEditor.snapshotDrawOrderTimeline(animation);
		if (timeline instanceof DrawOrderFolderTimeline folder)
			return animEditor.snapshotDrawOrderFolderTimeline(animation, folder);
		if (timeline instanceof IkConstraintTimeline ik)
			return animEditor.snapshotIkTimeline(animation, ik.getConstraintIndex());
		if (timeline instanceof TransformConstraintTimeline transform)
			return animEditor.snapshotTransformTimeline(animation, transform.getConstraintIndex());
		AnimationEditor.PathProp pathProp = pathPropOf(timeline);
		if (pathProp != null && timeline instanceof ConstraintTimeline path)
			return animEditor.snapshotPathTimeline(animation, path.getConstraintIndex(), pathProp);
		AnimationEditor.PhysicsProp physicsProp = animEditor.physicsPropOf(timeline);
		if (physicsProp != null && timeline instanceof ConstraintTimeline physics)
			return animEditor.snapshotPhysicsTimeline(animation, physics.getConstraintIndex(), physicsProp);
		AnimationEditor.SliderProp sliderProp = sliderPropOf(timeline);
		if (sliderProp != null && timeline instanceof ConstraintTimeline slider)
			return animEditor.snapshotSliderTimeline(animation, slider.getConstraintIndex(), sliderProp);
		if (timeline instanceof SequenceTimeline sequence)
			return animEditor.snapshotSequenceTimeline(animation, sequence.getSlotIndex(), sequence.getAttachment());
		if (timeline instanceof DeformTimeline deform)
			return animEditor.snapshotDeformTimeline(animation, deform.getSlotIndex(), deform.getAttachment());
		if (timeline instanceof InheritTimeline inherit)
			return animEditor.snapshotInheritTimeline(animation, inherit.getBoneIndex());
		if (timeline instanceof AttachmentTimeline attachment)
			return animEditor.snapshotAttachmentTimeline(animation, attachment.getSlotIndex());
		if (timeline instanceof RGBATimeline color)
			return animEditor.snapshotColorTimeline(animation, color.getSlotIndex());
		if (timeline instanceof RGBTimeline color)
			return animEditor.snapshotRgbTimeline(animation, color.getSlotIndex());
		if (timeline instanceof AlphaTimeline alpha)
			return animEditor.snapshotAlphaTimeline(animation, alpha.getSlotIndex());
		if (animEditor.isTwoColorTimeline(timeline))
			return animEditor.snapshotTwoColorTimeline(animation, ((SlotCurveTimeline)timeline).getSlotIndex());
		if (timeline instanceof BoneTimeline bone) {
			AnimationEditor.Prop prop = animEditor.propOf(timeline);
			return prop == null ? null
				: animEditor.snapshotBoneTimeline(animation, prop, bone.getBoneIndex());
		}
		return null;
	}

	private AnimationEditor.TimelineSnapshot snapshotTrack(Animation animation, Track track) {
		if (animation == null || track == null) return null;
		if (track.isEvent()) return animEditor.snapshotEventTimeline(animation);
		if (track.isDrawOrder()) return animEditor.snapshotDrawOrderTimeline(animation);
		if (track.isDrawOrderFolder())
			return animEditor.snapshotDrawOrderFolderTimeline(animation, (DrawOrderFolderTimeline)track.timeline);
		if (track.isIk()) return animEditor.snapshotIkTimeline(animation, track.constraintIndex);
		if (track.isTransform()) return animEditor.snapshotTransformTimeline(animation, track.constraintIndex);
		if (track.isPath()) return animEditor.snapshotPathTimeline(animation, track.constraintIndex, track.pathProp);
		if (track.isPhysics())
			return animEditor.snapshotPhysicsTimeline(animation, track.constraintIndex, track.physicsProp);
		if (track.isSlider())
			return animEditor.snapshotSliderTimeline(animation, track.constraintIndex, track.sliderProp);
		if (track.isSequence())
			return animEditor.snapshotSequenceTimeline(animation, track.slot.getIndex(), track.sequenceAttachment);
		if (track.isDeform())
			return animEditor.snapshotDeformTimeline(animation, track.slot.getIndex(), track.deformAttachment);
		if (track.isInherit())
			return animEditor.snapshotInheritTimeline(animation, track.bone.getData().getIndex());
		if (track.isAttachment()) return animEditor.snapshotAttachmentTimeline(animation, track.slot.getIndex());
		if (track.isColor()) return animEditor.snapshotColorTimeline(animation, track.slot.getIndex());
		if (track.isRgb()) return animEditor.snapshotRgbTimeline(animation, track.slot.getIndex());
		if (track.isAlpha()) return animEditor.snapshotAlphaTimeline(animation, track.slot.getIndex());
		if (track.isTwoColor()) return animEditor.snapshotTwoColorTimeline(animation, track.slot.getIndex());
		return animEditor.snapshotBoneTimeline(animation, track.prop, track.bone.getData().getIndex());
	}

	private void recordTimelineChange(String label, AnimationEditor.TimelineSnapshot before) {
		recordTimelineChanges(label, before == null ? null : new AnimationEditor.TimelineSnapshot[] {before});
	}

	private AnimationEditor.TimelineSnapshot[] snapshotBoneTimelines(Animation animation, int boneIndex,
		Array<AnimationEditor.Prop> props) {
		if (history == null || animation == null || props == null) return null;
		AnimationEditor.TimelineSnapshot[] snapshots = new AnimationEditor.TimelineSnapshot[props.size];
		for (int index = 0; index < props.size; index++)
			snapshots[index] = animEditor.snapshotBoneTimeline(animation, props.get(index), boneIndex);
		return snapshots;
	}

	private boolean recordTimelineChanges(String label, AnimationEditor.TimelineSnapshot[] before) {
		if (history == null || before == null || before.length == 0) return false;
		AnimationEditor.TimelineSnapshot[] after = new AnimationEditor.TimelineSnapshot[before.length];
		boolean changed = false;
		for (int index = 0; index < before.length; index++) {
			after[index] = animEditor.snapshotCurrent(before[index]);
			if (!before[index].sameAs(after[index])) changed = true;
		}
		if (!changed) return false;
		history.record(label, () -> restoreTimelines(before), () -> restoreTimelines(after));
		return true;
	}

	private void restoreTimelines(AnimationEditor.TimelineSnapshot[] snapshots) {
		animEditor.restoreTimelines(snapshots);
		doc.refreshCurrentAnimationPose();
	}

	/** 外部数据变化后重建 */
	public void rebuild() {
		rebuildAnimBox(doc.getCurrentAnimationName());
		refreshFpsField();
		canvas.rebuild();
		restoreGraphWorkspaceState();
		I18n.localize(this);
	}

	private void restoreGraphWorkspaceState() {
		SkeletonDocument.GraphWorkspaceState state = doc.graphWorkspaceState();
		if (graphWorkspace != null) graphWorkspace.closeWorkspace(false);
		if (!state.open()) return;
		// Migrate legacy Sidecars from the split Graph window to the unified embedded curve view.
		doc.setGraphWorkspaceState(SkeletonDocument.GraphWorkspaceState.empty());
		setTimelineView(true);
	}

	/** Relabels persistent timeline controls and translated filter choices. */
	public void refreshLanguage() {
		String filterKey = getTrackFilterKey();
		updating = true;
		trackFilterBox.setItems(trackFilterLabels());
		trackFilterBox.setSelected(trackFilterLabel(filterKey));
		keyInheritBox.setItems(inheritLabels());
		refreshZoomLabel();
		updating = false;
		rebuild();
	}

	/** 外部骨骼选择变化后，同步“选中骨骼”轨道过滤。 */
	public void refreshSelection() {
		refreshControls();
		if (FILTER_SELECTED.equals(getTrackFilterKey())) {
			String selectedName = selectedBoneName();
			if (!java.util.Objects.equals(selectedName, lastFilteredBoneName)) canvas.rebuild();
		}
	}

	@Override
	public void dispose() {
		if (graphWorkspace != null) graphWorkspace.closeWorkspace(false);
		canvas.dispose();
	}

	private final class GraphWorkspaceWindow extends Window {
		private static final float DEFAULT_WIDTH = 1160f;
		private static final float DEFAULT_HEIGHT = 680f;
		private Animation animation;
		private final TextField filterField;
		private final TextButton lockButton;
		private final Label countLabel;
		private final GraphOverviewCanvas overview;
		private final ScrollPane overviewScroll;
		private final Table detailHost;
		private final SplitPane workspaceSplit;
		private GraphEditorDialog detailEditor;
		private String detailTrackKey = "";
		private long observedRevision = Long.MIN_VALUE;
		private boolean closed;

		GraphWorkspaceWindow(Animation animation) {
			super("Graph · " + animation.getName(), kit.windowStyle);
			this.animation = animation;
			getTitleLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
			getTitleLabel().setFontScale(1.1f);
			getTitleTable().padLeft(6f).padRight(6f).padTop(6f).padBottom(5f);
			setModal(false);
			setMovable(true);
			setResizable(true);
			setKeepWithinStage(true);
			padLeft(9f);
			padRight(9f);
			padBottom(9f);
			padTop(38f);

			overview = new GraphOverviewCanvas();
			filterField = new TextField("", kit.textFieldStyle);
			filterField.setMessageText("筛选轨道");
			filterField.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { refreshTracks(true); }
			});
			lockButton = new TextButton("锁定", kit.toolButton);
			lockButton.setProgrammaticChangeEvents(false);
			lockButton.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					lockButton.setStyle(lockButton.isChecked() ? kit.activeToolButton : kit.toolButton);
					if (!lockButton.isChecked()) refreshTracks(true);
				}
			});
			countLabel = new Label("", kit.labelStyle);
			TextButton refresh = UiKit.asActionButton(new TextButton("刷新", kit.toolButton));
			refresh.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { refreshTracks(true); }
			});
			TextButton edit = UiKit.asActionButton(new TextButton("精细编辑", kit.accentButton));
			edit.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { overview.openSelectedTrack(); }
			});
			TextButton selectAll = UiKit.asActionButton(new TextButton("全选", kit.toolButton));
			selectAll.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { overview.selectAllPoints(); }
			});
			TextButton linear = UiKit.asActionButton(new TextButton("线性", kit.toolButton));
			linear.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { overview.setCurveMode(false); }
			});
			TextButton stepped = UiKit.asActionButton(new TextButton("步进", kit.toolButton));
			stepped.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { overview.setCurveMode(true); }
			});
			TextButton delete = UiKit.asActionButton(new TextButton("删除", kit.toolButton));
			delete.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { overview.deleteSelectedPoints(); }
			});
			TextButton close = UiKit.asActionButton(new TextButton("关闭", kit.toolButton));
			close.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { closeWorkspace(); }
			});

			Table tools = new Table();
			tools.left();
			tools.add(kit.controlLabel("轨道")).padRight(5f);
			tools.add(filterField).width(230f).height(27f).padRight(6f);
			tools.add(lockButton).height(27f).padRight(4f);
			tools.add(refresh).height(27f).padRight(8f);
			tools.add(countLabel).left();
			tools.add().growX();
			tools.add(selectAll).height(27f).padRight(4f);
			tools.add(linear).height(27f).padRight(4f);
			tools.add(stepped).height(27f).padRight(4f);
			tools.add(delete).height(27f).padRight(8f);
			tools.add(edit).height(27f).padRight(5f);
			tools.add(close).height(27f);
			add(tools).growX().minWidth(0f).height(31f).row();

			overviewScroll = new ScrollPane(overview, kit.scrollStyle);
			overviewScroll.setFadeScrollBars(false);
			overviewScroll.setFlickScroll(false);
			overviewScroll.setOverscroll(false, false);
			detailHost = new Table();
			detailHost.setBackground(kit.panelBg);
			showDetailPlaceholder();
			workspaceSplit = new SplitPane(overviewScroll, detailHost, false, kit.splitStyle);
			workspaceSplit.setSplitAmount(0.62f);
			workspaceSplit.setMinSplitAmount(0.28f);
			workspaceSplit.setMaxSplitAmount(0.72f);
			add(workspaceSplit).grow().minSize(900f, 360f).padTop(6f);
			addListener(new InputListener() {
				@Override public boolean keyDown(InputEvent event, int keycode) {
					if (keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) {
						overview.deleteSelectedPoints();
						return true;
					}
					if (controlPressed() && keycode == Input.Keys.A) {
						overview.selectAllPoints();
						return true;
					}
					return false;
				}
			});
			applyDocumentState(animation, doc.graphWorkspaceState());
		}

		void openOnStage(Stage stage) {
			SkeletonDocument.GraphWorkspaceState state = doc.graphWorkspaceState();
			float requestedWidth = state.width() > 0f ? state.width() : DEFAULT_WIDTH;
			float requestedHeight = state.height() > 0f ? state.height() : DEFAULT_HEIGHT;
			float width = Math.max(760f, Math.min(requestedWidth, stage.getWidth() - 36f));
			float height = Math.max(420f, Math.min(requestedHeight, stage.getHeight() - 36f));
			setSize(width, height);
			float x = state.x() >= 0f ? state.x() : (stage.getWidth() - width) / 2f;
			float y = state.y() >= 0f ? state.y() : (stage.getHeight() - height) / 2f;
			setPosition(Math.max(18f, Math.min(x, stage.getWidth() - width - 18f)),
				Math.max(18f, Math.min(y, stage.getHeight() - height - 18f)));
			validate();
			overviewScroll.setScrollX(state.scrollX());
			overviewScroll.setScrollY(state.scrollY());
			overviewScroll.updateVisualScroll();
			restoreDetailTrack(state.detailTrackKey(), state.splitAmount());
			toFront();
		}

		void applyDocumentState(Animation animation, SkeletonDocument.GraphWorkspaceState state) {
			if (closed || animation == null) return;
			closeTrackDetails(true);
			this.animation = animation;
			getTitleLabel().setText("Graph · " + animation.getName());
			boolean matchingAnimation = state.animation().isEmpty()
				|| state.animation().equals(animation.getName());
			filterField.setText(matchingAnimation ? state.filter() : "");
			lockButton.setChecked(matchingAnimation && state.locked());
			lockButton.setStyle(lockButton.isChecked() ? kit.activeToolButton : kit.toolButton);
			workspaceSplit.setSplitAmount(matchingAnimation ? state.splitAmount() : 0.62f);
			refreshTracks(true);
			if (matchingAnimation) {
				overviewScroll.setScrollX(state.scrollX());
				overviewScroll.setScrollY(state.scrollY());
				overviewScroll.updateVisualScroll();
				restoreDetailTrack(state.detailTrackKey(), state.splitAmount());
			}
		}

		private void restoreDetailTrack(String trackKey, float splitAmount) {
			if (trackKey == null || trackKey.isBlank()) return;
			Track track = overview.resolve(trackKey);
			if (track == null || track.timeline == null || track.timeline.getFrameCount() == 0) return;
			openTrackDetails(track.timeline, overview.nearestFrame(track.timeline, doc.getCurrentTime()));
			workspaceSplit.setSplitAmount(splitAmount);
		}

		void refreshTracks(boolean force) {
			if (closed) return;
			if (lockButton != null && lockButton.isChecked() && !force) {
				observedRevision = doc.revision;
				return;
			}
			overview.refreshTracks(filterField == null ? "" : filterField.getText());
			countLabel.setText(overview.trackCount() + " 条曲线轨道");
			observedRevision = doc.revision;
		}

		private void openTrackDetails(Animation.Timeline timeline, int frame) {
			Stage stage = getStage();
			if (stage == null || timeline == null || timeline.getFrameCount() == 0
				|| !animation.getTimelines().contains(timeline, true)) return;
			if (detailEditor != null) {
				GraphEditorDialog previous = detailEditor;
				detailEditor = null;
				previous.closeEmbedded(false);
			}
			Track track = canvas.curveTrack(timeline);
			String title = track == null ? timeline.getClass().getSimpleName() : canvas.trackLabel(track);
			detailTrackKey = track == null ? "" : canvas.trackStateKey(track);
			detailHost.clearChildren();
			detailEditor = GraphEditorDialog.embed(detailHost, stage, kit, doc, animEditor, animation,
				timeline, Math.max(0, Math.min(frame, timeline.getFrameCount() - 1)), title,
				curveChannelLabels(timeline), graphHost(animation, this::openTrackDetails), () -> {
					detailEditor = null;
					detailTrackKey = "";
					showDetailPlaceholder();
				});
			workspaceSplit.setSplitAmount(0.42f);
		}

		private void showDetailPlaceholder() {
			detailHost.clearChildren();
			Label empty = new Label("未选择曲线轨道", kit.labelStyle);
			empty.setColor(kit.palette().mutedText());
			detailHost.add(empty);
		}

		private void closeTrackDetails(boolean saveViewState) {
			if (detailEditor == null) return;
			GraphEditorDialog closing = detailEditor;
			detailEditor = null;
			closing.closeEmbedded(saveViewState);
		}

		@Override public void act(float delta) {
			super.act(delta);
			if (closed) return;
			Animation current = currentAnim();
			if (current == null) {
				closeWorkspace(true);
				return;
			}
			if (current != animation) {
				SkeletonDocument.GraphWorkspaceState state = doc.graphWorkspaceState();
				if (!state.open()) {
					closeWorkspace(false);
					return;
				}
				applyDocumentState(current, state);
			} else if (doc.revision != observedRevision) {
				refreshTracks(false);
			}
		}

		void persistWorkspaceState(boolean open) {
			if (closed && open) return;
			doc.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(open,
				animation == null ? "" : animation.getName(), filterField.getText(), lockButton.isChecked(),
				detailTrackKey, workspaceSplit.getSplitAmount(), overviewScroll.getScrollX(),
				overviewScroll.getScrollY(), getX(), getY(), getWidth(), getHeight()));
		}

		void closeWorkspace() { closeWorkspace(true); }

		void closeWorkspace(boolean persist) {
			if (closed) return;
			if (persist) persistWorkspaceState(false);
			closed = true;
			closeTrackDetails(true);
			remove();
			if (graphWorkspace == this) graphWorkspace = null;
		}

		private final class GraphOverviewCanvas extends Widget {
			private static final float ROW_HEIGHT = 112f;
			private static final float LABEL_WIDTH = 250f;
			private static final float PLOT_PAD = 14f;
			private static final float MIN_WIDTH = 1320f;
			private static final float HIT_RADIUS = 10f;
			private final Array<GraphTrackEntry> entries = new Array<>();
			private final Array<GraphPointSelection> pointSelections = new Array<>();
			private final BitmapFont font = kit.font;
			private final GlyphLayout textLayout = new GlyphLayout();
			private final Color oldBatchColor = new Color();
			private final Color oldFontColor = new Color();
			private final Color[] colors = {
				new Color(0.95f, 0.38f, 0.28f, 1f), new Color(0.30f, 0.78f, 0.42f, 1f),
				new Color(0.28f, 0.64f, 0.96f, 1f), new Color(0.95f, 0.76f, 0.24f, 1f),
				new Color(0.76f, 0.46f, 0.94f, 1f), new Color(0.25f, 0.84f, 0.82f, 1f),
				new Color(0.96f, 0.48f, 0.72f, 1f)
			};
			private float preferredHeight = ROW_HEIGHT;
			private long lastClickNanos;
			private String lastClickTrack = "";
			private int lastClickFrame = -1;
			private boolean marqueeSelecting;
			private boolean marqueeDragging;
			private boolean marqueeAdditive;
			private float marqueeStartX, marqueeStartY, marqueeCurrentX, marqueeCurrentY;
			private boolean draggingKeys;

			GraphOverviewCanvas() {
				setTouchable(Touchable.enabled);
				addListener(trackTooltip((x, y) -> {
					int row = (int)((getHeight() - y) / ROW_HEIGHT);
					return row >= 0 && row < entries.size ? entries.get(row).label : "";
				}));
				addListener(new InputListener() {
					@Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
						if (button != Input.Buttons.LEFT) return false;
						if (getStage() != null) getStage().setKeyboardFocus(GraphOverviewCanvas.this);
						onTouchDown(x, y);
						return true;
					}

					@Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
						onTouchDragged(x, y);
					}

					@Override public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
						if (button == Input.Buttons.LEFT) onTouchUp(x, y);
					}
				});
			}

			@Override public float getPrefWidth() { return MIN_WIDTH; }
			@Override public float getPrefHeight() { return preferredHeight; }

			int trackCount() { return entries.size; }

			void refreshTracks(String filter) {
				entries.clear();
				String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
				IdentityHashMap<Animation.Timeline, Boolean> seen = new IdentityHashMap<>();
				for (Animation.Timeline timeline : animation.getTimelines()) {
					Track track = canvas.trackForTimeline(timeline);
					if (!canvas.isCurveTrack(track) || seen.containsKey(track.timeline)) continue;
					String label = canvas.trackLabel(track);
					if (!query.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(query)) continue;
					seen.put(track.timeline, Boolean.TRUE);
					entries.add(new GraphTrackEntry(canvas.trackStateKey(track), label));
				}
				preferredHeight = Math.max(ROW_HEIGHT, entries.size * ROW_HEIGHT);
				invalidateHierarchy();
			}

			void openSelectedTrack() {
				if (entries.isEmpty()) {
					showStatus("当前动画没有可编辑的连续数值轨道");
					return;
				}
				Animation.Timeline selected = canvas.selectedTimelineIgnoringVisibility();
				GraphTrackEntry target = null;
				for (GraphTrackEntry entry : entries) {
					Track track = resolve(entry);
					if (track != null && track.timeline == selected) { target = entry; break; }
				}
				if (target == null) target = entries.first();
				Track track = resolve(target);
				if (track == null) return;
				int frame = nearestFrame(track.timeline, doc.getCurrentTime());
				canvas.selectGraphKeyframe(track, frame, false);
				canvas.scrubTo(animEditor.frameTime(track.timeline, frame));
				pointSelections.clear();
				syncPointSelectionsFromDopesheet();
				GraphWorkspaceWindow.this.openTrackDetails(track.timeline, frame);
			}

			private Track resolve(GraphTrackEntry entry) {
				if (entry == null || animation == null) return null;
				Track visible = canvas.findTrackByStateKey(entry.trackKey);
				if (visible != null) return visible;
				for (Animation.Timeline timeline : animation.getTimelines()) {
					Track track = canvas.trackForTimeline(timeline);
					if (track != null && entry.trackKey.equals(canvas.trackStateKey(track))) return track;
				}
				return null;
			}

			private void onTouchDown(float x, float y) {
				syncPointSelectionsFromDopesheet();
				int row = (int)((getHeight() - y) / ROW_HEIGHT);
				boolean additive = canvas.selectionModifierPressed();
				if (row < 0 || row >= entries.size) {
					if (!additive) clearPointSelection();
					return;
				}
				GraphTrackEntry entry = entries.get(row);
				Track track = resolve(entry);
				if (track == null || track.timeline == null) return;
				float duration = graphDuration();
				GraphPointHit hit = nearestPoint(entry, track, row, x, y, duration);
				if (hit == null) {
					if (!additive) clearPointSelection();
					if (x >= plotLeft()) {
						marqueeSelecting = true;
						marqueeDragging = false;
						marqueeAdditive = additive;
						marqueeStartX = marqueeCurrentX = clampPlotX(x);
						marqueeStartY = marqueeCurrentY = y;
						canvas.scrubTo(timeAtX(x, duration));
					}
					lastClickFrame = -1;
					lastClickTrack = "";
					return;
				}

				GraphPointSelection clicked = hit.selection();
				if (additive) {
					togglePointSelection(clicked);
					applyPointSelectionsToDopesheet(clicked);
					canvas.scrubTo(clicked.time);
					return;
				}
				if (!containsPoint(pointSelections, clicked)) {
					pointSelections.clear();
					pointSelections.add(clicked);
				}
				applyPointSelectionsToDopesheet(clicked);
				canvas.scrubTo(clicked.time);
				canvas.ensureTimeVisible(clicked.time);

				long now = System.nanoTime();
				boolean doubleClick = entry.trackKey.equals(lastClickTrack) && hit.frame == lastClickFrame
					&& now - lastClickNanos <= 400_000_000L;
				lastClickNanos = now;
				lastClickTrack = entry.trackKey;
				lastClickFrame = hit.frame;
				if (doubleClick) {
					GraphWorkspaceWindow.this.openTrackDetails(track.timeline, hit.frame);
					return;
				}
				draggingKeys = canvas.beginGraphKeyDrag(track, hit.frame);
			}

			private void onTouchDragged(float x, float y) {
				if (marqueeSelecting) {
					marqueeCurrentX = clampPlotX(x);
					marqueeCurrentY = Math.max(0f, Math.min(getHeight(), y));
					if (Math.abs(marqueeCurrentX - marqueeStartX) >= MARQUEE_DRAG_THRESHOLD
						|| Math.abs(marqueeCurrentY - marqueeStartY) >= MARQUEE_DRAG_THRESHOLD)
						marqueeDragging = true;
					return;
				}
				if (draggingKeys) canvas.updateGraphKeyDrag(timeAtX(x, graphDuration()));
			}

			private void onTouchUp(float x, float y) {
				if (marqueeSelecting) {
					marqueeCurrentX = clampPlotX(x);
					marqueeCurrentY = Math.max(0f, Math.min(getHeight(), y));
					finishMarquee();
					return;
				}
				if (!draggingKeys) return;
				draggingKeys = false;
				boolean moved = hasKeyframeTimeDelta(canvas.dragPreviewDelta);
				Array<GraphPointSelection> movedPoints = new Array<>();
				for (GraphPointSelection point : pointSelections) {
					Track track = resolve(point.trackKey);
					if (track == null || track.timeline == null) continue;
					int frame = animEditor.findFrame(track.timeline, point.time);
					if (frame < 0) continue;
					movedPoints.add(new GraphPointSelection(point.trackKey, moved
						? canvas.snapTime(canvas.graphPreviewTime(track, frame)) : point.time, point.channel));
				}
				canvas.commitGraphKeyDrag();
				pointSelections.clear();
				for (GraphPointSelection point : movedPoints) {
					Track track = resolve(point.trackKey);
					if (track != null && track.timeline != null
						&& animEditor.findFrame(track.timeline, point.time) >= 0
						&& point.channel < AnimationEditor.curveChannelCount(track.timeline))
						pointSelections.add(point);
				}
				if (pointSelections.isEmpty()) syncPointSelectionsFromDopesheet();
				else applyPointSelectionsToDopesheet(pointSelections.peek());
			}

			private GraphPointHit nearestPoint(GraphTrackEntry entry, Track track, int row,
				float x, float y, float duration) {
				Animation.Timeline timeline = track.timeline;
				if (x < plotLeft() || x > plotRight()) return null;
				float[] bounds = valueBounds(timeline);
				float bottom = rowBottom(row) + PLOT_PAD;
				float height = ROW_HEIGHT - PLOT_PAD * 2f;
				float best = HIT_RADIUS * HIT_RADIUS;
				GraphPointHit result = null;
				for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
					float keyX = xAtTime(animEditor.frameTime(timeline, frame), duration);
					for (int channel = 0; channel < AnimationEditor.curveChannelCount(timeline); channel++) {
						float keyY = valueY(animEditor.curveFrameValue(timeline, frame, channel), bounds,
							bottom, height);
						float dx = x - keyX, dy = y - keyY;
						float distance = dx * dx + dy * dy;
						if (distance <= best) {
							best = distance;
							result = new GraphPointHit(frame,
								new GraphPointSelection(entry.trackKey,
									animEditor.frameTime(timeline, frame), channel));
						}
					}
				}
				return result;
			}

			private void finishMarquee() {
				boolean dragged = marqueeDragging;
				boolean additive = marqueeAdditive;
				marqueeSelecting = false;
				marqueeDragging = false;
				if (!dragged) return;
				Array<GraphPointSelection> selected = additive
					? new Array<>(pointSelections) : new Array<>();
				GraphPointSelection preferred = null;
				float duration = graphDuration();
				for (int row = 0; row < entries.size; row++) {
					GraphTrackEntry entry = entries.get(row);
					Track track = resolve(entry);
					if (track == null || track.timeline == null) continue;
					Animation.Timeline timeline = track.timeline;
					float[] bounds = valueBounds(timeline);
					float bottom = rowBottom(row) + PLOT_PAD;
					float height = ROW_HEIGHT - PLOT_PAD * 2f;
					for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
						float pointX = xAtTime(animEditor.frameTime(timeline, frame), duration);
						for (int channel = 0; channel < AnimationEditor.curveChannelCount(timeline); channel++) {
							float pointY = valueY(animEditor.curveFrameValue(timeline, frame, channel),
								bounds, bottom, height);
							if (!pointInsideGraphMarquee(pointX, pointY)) continue;
							GraphPointSelection point = new GraphPointSelection(entry.trackKey,
								animEditor.frameTime(timeline, frame), channel);
							if (!containsPoint(selected, point)) selected.add(point);
							preferred = point;
						}
					}
				}
				pointSelections.clear();
				pointSelections.addAll(selected);
				applyPointSelectionsToDopesheet(preferred);
			}

			private int nearestFrame(Animation.Timeline timeline, float time) {
				int result = 0;
				float distance = Float.POSITIVE_INFINITY;
				for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
					float candidate = Math.abs(animEditor.frameTime(timeline, frame) - time);
					if (candidate < distance) { distance = candidate; result = frame; }
				}
				return result;
			}

			void selectAllPoints() {
				pointSelections.clear();
				for (GraphTrackEntry entry : entries) {
					Track track = resolve(entry);
					if (track == null || track.timeline == null) continue;
					for (int frame = 0; frame < track.timeline.getFrameCount(); frame++) {
						float time = animEditor.frameTime(track.timeline, frame);
						for (int channel = 0; channel < AnimationEditor.curveChannelCount(track.timeline); channel++)
							pointSelections.add(new GraphPointSelection(entry.trackKey, time, channel));
					}
				}
				applyPointSelectionsToDopesheet(pointSelections.isEmpty() ? null : pointSelections.peek());
			}

			void deleteSelectedPoints() {
				syncPointSelectionsFromDopesheet();
				applyPointSelectionsToDopesheet(pointSelections.isEmpty() ? null : pointSelections.peek());
				if (!canvas.deleteSelectedKeyframes()) {
					showStatus("请先选择要删除的关键帧");
					return;
				}
				pointSelections.clear();
				GraphWorkspaceWindow.this.refreshTracks(true);
			}

			void setCurveMode(boolean stepped) {
				syncPointSelectionsFromDopesheet();
				if (pointSelections.isEmpty()) {
					showStatus("请先选择要编辑的 Graph 关键点");
					return;
				}
				ArrayList<AnimationEditor.TimelineSnapshot> snapshots = new ArrayList<>();
				ArrayList<GraphCurveEdit> edits = new ArrayList<>();
				for (GraphTrackEntry entry : entries) {
					Track track = resolve(entry);
					if (track == null || track.timeline == null) continue;
					ArrayList<Integer> frames = new ArrayList<>(), channels = new ArrayList<>();
					for (GraphPointSelection point : pointSelections) {
						if (!entry.trackKey.equals(point.trackKey)) continue;
						int frame = animEditor.findFrame(track.timeline, point.time);
						if (frame < 0 || frame >= track.timeline.getFrameCount() - 1) continue;
						frames.add(frame);
						channels.add(point.channel);
					}
					if (frames.isEmpty()) continue;
					AnimationEditor.TimelineSnapshot[] before = graphHost(animation).snapshotCurveMode(track.timeline);
					if (before != null) for (AnimationEditor.TimelineSnapshot snapshot : before)
						if (snapshot != null) snapshots.add(snapshot);
					int[] frameArray = new int[frames.size()], channelArray = new int[channels.size()];
					for (int index = 0; index < frames.size(); index++) {
						frameArray[index] = frames.get(index);
						channelArray[index] = channels.get(index);
					}
					edits.add(new GraphCurveEdit(track, frameArray, channelArray));
				}
				if (edits.isEmpty()) {
					showStatus("末帧没有向后插值曲线");
					return;
				}
				AnimationEditor.TimelineSnapshot[] before = snapshots.toArray(AnimationEditor.TimelineSnapshot[]::new);
				long revision = doc.revision;
				try {
					for (GraphCurveEdit edit : edits)
						animEditor.setCurveModeChannels(animation, edit.track.timeline,
							edit.frames, edit.channels, stepped);
					if (doc.revision != revision) {
						recordTimelineChanges("Graph 曲线模式", before);
						notifyChanged();
						showInfo(stepped ? "已设为步进曲线" : "已设为线性曲线");
					}
				} catch (RuntimeException error) {
					if (before.length > 0) restoreTimelines(before);
					notifyChanged();
					showStatus("无法修改 Graph 曲线：" + error.getMessage());
				}
				pointSelections.clear();
				GraphWorkspaceWindow.this.refreshTracks(true);
				syncPointSelectionsFromDopesheet();
			}

			private void clearPointSelection() {
				pointSelections.clear();
				canvas.clearSelection();
			}

			private void togglePointSelection(GraphPointSelection clicked) {
				for (int index = pointSelections.size - 1; index >= 0; index--) {
					if (!samePoint(pointSelections.get(index), clicked)) continue;
					pointSelections.removeIndex(index);
					return;
				}
				pointSelections.add(clicked);
			}

			private void applyPointSelectionsToDopesheet(GraphPointSelection preferredPoint) {
				Array<KeyframeCanvas.KeySelection> keys = new Array<>();
				KeyframeCanvas.KeySelection preferred = null;
				for (GraphPointSelection point : pointSelections) {
					Track track = resolve(point.trackKey);
					if (track == null || track.timeline == null) continue;
					int frame = animEditor.findFrame(track.timeline, point.time);
					KeyframeCanvas.KeySelection key = canvas.keySelection(track, track.timeline, frame);
					if (key == null) continue;
					if (!canvas.containsSelection(keys, key)) keys.add(key);
					if (samePoint(point, preferredPoint)) preferred = key;
				}
				canvas.applySelectionSet(keys, preferred);
			}

			private void syncPointSelectionsFromDopesheet() {
				Array<KeyframeCanvas.KeySelection> keys = visibleDopesheetSelections();
				if (samePointProjection(keys)) return;
				pointSelections.clear();
				for (KeyframeCanvas.KeySelection key : keys) {
					KeyframeCanvas.ResolvedKey resolved = canvas.resolveSelection(key);
					if (resolved == null || !canvas.isCurveTrack(resolved.track)) continue;
					String trackKey = canvas.trackStateKey(resolved.track);
					if (!containsEntry(trackKey)) continue;
					float time = animEditor.frameTime(resolved.timeline, resolved.frame);
					for (int channel = 0; channel < AnimationEditor.curveChannelCount(resolved.timeline); channel++)
						pointSelections.add(new GraphPointSelection(trackKey, time, channel));
				}
			}

			private Array<KeyframeCanvas.KeySelection> visibleDopesheetSelections() {
				Array<KeyframeCanvas.KeySelection> visible = new Array<>();
				for (KeyframeCanvas.KeySelection key : canvas.allSelections()) {
					KeyframeCanvas.ResolvedKey resolved = canvas.resolveSelection(key);
					if (resolved != null && containsEntry(canvas.trackStateKey(resolved.track))) visible.add(key);
				}
				return visible;
			}

			private boolean samePointProjection(Array<KeyframeCanvas.KeySelection> keys) {
				int uniquePoints = 0;
				for (int index = 0; index < pointSelections.size; index++) {
					GraphPointSelection point = pointSelections.get(index);
					boolean first = true;
					for (int earlier = 0; earlier < index; earlier++) {
						GraphPointSelection other = pointSelections.get(earlier);
						if (point.trackKey.equals(other.trackKey) && Float.compare(point.time, other.time) == 0) {
							first = false;
							break;
						}
					}
					if (first) uniquePoints++;
				}
				if (uniquePoints != keys.size) return false;
				for (KeyframeCanvas.KeySelection key : keys) {
					KeyframeCanvas.ResolvedKey resolved = canvas.resolveSelection(key);
					if (resolved == null) return false;
					String trackKey = canvas.trackStateKey(resolved.track);
					float time = animEditor.frameTime(resolved.timeline, resolved.frame);
					boolean found = false;
					for (GraphPointSelection point : pointSelections)
						if (trackKey.equals(point.trackKey) && Float.compare(time, point.time) == 0) {
							found = true;
							break;
						}
					if (!found) return false;
				}
				return true;
			}

			private Track resolve(String trackKey) {
				if (trackKey == null || trackKey.isEmpty()) return null;
				for (GraphTrackEntry entry : entries)
					if (trackKey.equals(entry.trackKey)) return resolve(entry);
				return null;
			}

			private boolean containsEntry(String trackKey) {
				for (GraphTrackEntry entry : entries) if (entry.trackKey.equals(trackKey)) return true;
				return false;
			}

			private boolean pointSelected(String trackKey, float time, int channel) {
				for (GraphPointSelection point : pointSelections)
					if (point.channel == channel && point.trackKey.equals(trackKey)
						&& Float.compare(point.time, time) == 0) return true;
				return false;
			}

			private static boolean containsPoint(Array<GraphPointSelection> points, GraphPointSelection target) {
				if (target == null) return false;
				for (GraphPointSelection point : points) if (samePoint(point, target)) return true;
				return false;
			}

			private static boolean samePoint(GraphPointSelection first, GraphPointSelection second) {
				return first != null && second != null && first.channel == second.channel
					&& first.trackKey.equals(second.trackKey) && Float.compare(first.time, second.time) == 0;
			}

			private boolean pointInsideGraphMarquee(float x, float y) {
				return x >= Math.min(marqueeStartX, marqueeCurrentX)
					&& x <= Math.max(marqueeStartX, marqueeCurrentX)
					&& y >= Math.min(marqueeStartY, marqueeCurrentY)
					&& y <= Math.max(marqueeStartY, marqueeCurrentY);
			}

			private float clampPlotX(float x) { return Math.max(plotLeft(), Math.min(plotRight(), x)); }

			@Override public void draw(Batch batch, float parentAlpha) {
				syncPointSelectionsFromDopesheet();
				oldBatchColor.set(batch.getColor());
				oldFontColor.set(font.getColor());
				float duration = graphDuration();
				for (int row = 0; row < entries.size; row++) {
					GraphTrackEntry entry = entries.get(row);
					Track track = resolve(entry);
					if (track == null || track.timeline == null) continue;
					drawRow(batch, parentAlpha, row, entry, track, duration,
						canvas.graphTrackSelected(track));
				}
				if (entries.isEmpty()) {
					font.setColor(kit.palette().mutedText());
					font.draw(batch, "当前筛选没有连续数值轨道", getX() + 18f,
						getY() + getHeight() - 24f);
				}
				if (marqueeSelecting && marqueeDragging) {
					float left = getX() + Math.min(marqueeStartX, marqueeCurrentX);
					float bottom = getY() + Math.min(marqueeStartY, marqueeCurrentY);
					float width = Math.abs(marqueeCurrentX - marqueeStartX);
					float height = Math.abs(marqueeCurrentY - marqueeStartY);
					Color selection = kit.palette().color(UiAppearance.Role.TREE_SELECTION);
					batch.setColor(selection.r, selection.g, selection.b, parentAlpha * 0.28f);
					batch.draw(kit.white.getRegion(), left, bottom, width, height);
					batch.setColor(0.84f, 0.91f, 1f, parentAlpha);
					drawRect(batch, left, bottom, width, height, 1f);
				}
				batch.setColor(oldBatchColor);
				font.setColor(oldFontColor);
			}

			private void drawRow(Batch batch, float alpha, int row, GraphTrackEntry entry, Track track,
				float duration, boolean selectedTrack) {
				Animation.Timeline timeline = track.timeline;
				float bottom = getY() + rowBottom(row);
				Color background = kit.palette().color(row % 2 == 0
					? UiAppearance.Role.TIMELINE_BACKGROUND : UiAppearance.Role.TIMELINE_ROW_ALT);
				batch.setColor(background.r, background.g, background.b, alpha);
				batch.draw(kit.white.getRegion(), getX(), bottom, getWidth(), ROW_HEIGHT - 1f);
				if (selectedTrack) {
					Color highlight = kit.palette().color(UiAppearance.Role.TREE_SELECTION);
					batch.setColor(highlight.r, highlight.g, highlight.b, alpha * 0.8f);
					batch.draw(kit.white.getRegion(), getX(), bottom, LABEL_WIDTH, ROW_HEIGHT - 1f);
				}

				font.setColor(kit.palette().text());
				textLayout.setText(font, entry.label, 0, entry.label.length(), font.getColor(),
					LABEL_WIDTH - 22f, com.badlogic.gdx.utils.Align.left, false, "...");
				font.draw(batch, textLayout, getX() + 10f, bottom + ROW_HEIGHT - 24f);
				String channels = String.join(" · ", curveChannelLabels(timeline));
				font.setColor(kit.palette().mutedText());
				textLayout.setText(font, channels, 0, channels.length(), font.getColor(),
					LABEL_WIDTH - 22f, com.badlogic.gdx.utils.Align.left, false, "...");
				font.draw(batch, textLayout, getX() + 10f, bottom + ROW_HEIGHT - 49f);
				font.draw(batch, timeline.getFrameCount() + " K", getX() + 10f, bottom + 22f);

				float plotBottom = bottom + PLOT_PAD;
				float plotHeight = ROW_HEIGHT - PLOT_PAD * 2f;
				float[] bounds = valueBounds(timeline);
				Color grid = kit.palette().color(UiAppearance.Role.TIMELINE_GRID);
				batch.setColor(grid.r, grid.g, grid.b, alpha);
				for (int tick = 0; tick <= 4; tick++) {
					float x = getX() + plotLeft() + (plotRight() - plotLeft()) * tick / 4f;
					drawLine(batch, x, plotBottom, x, plotBottom + plotHeight, 1f);
				}
				if (bounds[0] <= 0f && bounds[1] >= 0f) {
					float zeroY = valueY(0f, bounds, plotBottom, plotHeight);
					drawLine(batch, getX() + plotLeft(), zeroY, getX() + plotRight(), zeroY, 1f);
				}

				for (int channel = 0; channel < AnimationEditor.curveChannelCount(timeline); channel++)
					drawChannel(batch, alpha, timeline, channel, duration, bounds, plotBottom, plotHeight,
						track);
				float playhead = getX() + xAtTime(doc.getCurrentTime(), duration);
				batch.setColor(0.92f, 0.32f, 0.27f, alpha * 0.95f);
				drawLine(batch, playhead, plotBottom, playhead, plotBottom + plotHeight, 1.4f);
			}

			private void drawChannel(Batch batch, float alpha, Animation.Timeline timeline, int channel,
				float duration, float[] bounds, float bottom, float height, Track track) {
				Color color = colors[Math.floorMod(channel, colors.length)];
				batch.setColor(color.r, color.g, color.b, alpha);
				for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
					float t1 = canvas.graphPreviewTime(track, frame);
					float t2 = canvas.graphPreviewTime(track, frame + 1);
					float v1 = animEditor.curveFrameValue(timeline, frame, channel);
					float v2 = animEditor.curveFrameValue(timeline, frame + 1, channel);
					float x1 = getX() + xAtTime(t1, duration);
					float x2 = getX() + xAtTime(t2, duration);
					float y1 = valueY(v1, bounds, bottom, height);
					float y2 = valueY(v2, bounds, bottom, height);
					int type = animEditor.curveType(timeline, frame);
					if (type == Animation.CurveTimeline.STEPPED) {
						drawLine(batch, x1, y1, x2, y1, 1.7f);
						drawLine(batch, x2, y1, x2, y2, 1f);
					} else if (type < Animation.CurveTimeline.BEZIER) {
						drawLine(batch, x1, y1, x2, y2, 1.7f);
					} else {
						AnimationEditor.BezierHandles curve;
						try { curve = animEditor.bezierHandles(timeline, frame, channel); }
						catch (RuntimeException ignored) { curve = null; }
						if (curve == null) {
							drawLine(batch, x1, y1, x2, y2, 1.7f);
						} else {
							float previousX = x1, previousY = y1;
							for (int step = 1; step <= 24; step++) {
								float u = step / 24f;
								float time = cubic(t1, t1 + (t2 - t1) * curve.cx1(),
									t1 + (t2 - t1) * curve.cx2(), t2, u);
								float value = cubic(v1, curve.value1(), curve.value2(), v2, u);
								float x = getX() + xAtTime(time, duration);
								float y = valueY(value, bounds, bottom, height);
								drawLine(batch, previousX, previousY, x, y, 1.7f);
								previousX = x;
								previousY = y;
							}
						}
					}
				}
				for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
					float time = animEditor.frameTime(timeline, frame);
					float x = getX() + xAtTime(canvas.graphPreviewTime(track, frame), duration);
					float y = valueY(animEditor.curveFrameValue(timeline, frame, channel), bounds, bottom, height);
					boolean selected = pointSelected(canvas.trackStateKey(track), time, channel);
					float size = selected ? 8f : 5f;
					if (selected) batch.setColor(0.98f, 0.98f, 1f, alpha);
					batch.draw(kit.white.getRegion(), x - size / 2f, y - size / 2f, size, size);
					batch.setColor(color.r, color.g, color.b, alpha);
				}
			}

			private float[] valueBounds(Animation.Timeline timeline) {
				float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
				for (int channel = 0; channel < AnimationEditor.curveChannelCount(timeline); channel++) {
					for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
						float value = animEditor.curveFrameValue(timeline, frame, channel);
						min = Math.min(min, value);
						max = Math.max(max, value);
					}
					for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
						try {
							AnimationEditor.BezierHandles curve = animEditor.bezierHandles(timeline, frame, channel);
							if (curve == null) continue;
							min = Math.min(min, Math.min(curve.value1(), curve.value2()));
							max = Math.max(max, Math.max(curve.value1(), curve.value2()));
						} catch (RuntimeException ignored) {
						}
					}
				}
				if (!Float.isFinite(min) || !Float.isFinite(max)) return new float[] {-1f, 1f};
				float margin = Math.abs(max - min) < 0.000001f
					? Math.max(1f, Math.abs(max) * 0.15f) : (max - min) * 0.12f;
				return new float[] {min - margin, max + margin};
			}

			private float graphDuration() {
				float duration = animation == null ? 0f : animation.getDuration();
				return Math.max(1f / UiNumbers.effectiveFps(doc.data == null ? 0f : doc.data.getFps()), duration);
			}

			private float plotLeft() { return LABEL_WIDTH + 12f; }
			private float plotRight() { return Math.max(plotLeft() + 1f, getWidth() - 18f); }
			private float xAtTime(float time, float duration) {
				return plotLeft() + Math.max(0f, Math.min(duration, time)) / duration * (plotRight() - plotLeft());
			}
			private float timeAtX(float x, float duration) {
				return Math.max(0f, Math.min(duration,
					(x - plotLeft()) / Math.max(1f, plotRight() - plotLeft()) * duration));
			}
			private float rowBottom(int row) { return getHeight() - (row + 1) * ROW_HEIGHT; }
			private float valueY(float value, float[] bounds, float bottom, float height) {
				return bottom + (value - bounds[0]) / Math.max(0.000001f, bounds[1] - bounds[0]) * height;
			}
			private float cubic(float a, float b, float c, float d, float t) {
				float inverse = 1f - t;
				return inverse * inverse * inverse * a + 3f * inverse * inverse * t * b
					+ 3f * inverse * t * t * c + t * t * t * d;
			}
			private void drawLine(Batch batch, float x1, float y1, float x2, float y2, float thickness) {
				float dx = x2 - x1, dy = y2 - y1;
				float length = (float)Math.sqrt(dx * dx + dy * dy);
				if (length < 0.0001f) return;
				float angle = (float)Math.toDegrees(Math.atan2(dy, dx));
				batch.draw(kit.white.getRegion(), x1, y1 - thickness / 2f, 0f, thickness / 2f,
					length, thickness, 1f, 1f, angle);
			}

			private void drawRect(Batch batch, float x, float y, float width, float height, float thickness) {
				batch.draw(kit.white.getRegion(), x, y, width, thickness);
				batch.draw(kit.white.getRegion(), x, y + height - thickness, width, thickness);
				batch.draw(kit.white.getRegion(), x, y, thickness, height);
				batch.draw(kit.white.getRegion(), x + width - thickness, y, thickness, height);
			}
		}

		private final class GraphTrackEntry {
			final String trackKey;
			final String label;

			GraphTrackEntry(String trackKey, String label) {
				this.trackKey = trackKey;
				this.label = label;
			}
		}

		private record GraphPointSelection(String trackKey, float time, int channel) {}
		private record GraphPointHit(int frame, GraphPointSelection selection) {}
		private record GraphCurveEdit(Track track, int[] frames, int[] channels) {}
	}

	private final class BezierPreview extends Actor {
		private static final float PAD = 18f;
		private final TextField x1Field, y1Field, x2Field, y2Field;
		private final Color oldBatchColor = new Color();
		private int draggedHandle;

		BezierPreview(TextField x1Field, TextField y1Field, TextField x2Field, TextField y2Field) {
			this.x1Field = x1Field;
			this.y1Field = y1Field;
			this.x2Field = x2Field;
			this.y2Field = y2Field;
			setTouchable(Touchable.enabled);
			addListener(new InputListener() {
				@Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
					if (button != Input.Buttons.LEFT) return false;
					draggedHandle = nearestHandle(x, y);
					if (draggedHandle == 0) return false;
					updateHandle(x, y);
					return true;
				}

				@Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
					updateHandle(x, y);
				}

				@Override public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
					draggedHandle = 0;
				}
			});
		}

		@Override public void draw(Batch batch, float parentAlpha) {
			oldBatchColor.set(batch.getColor());
			float left = getX() + PAD, bottom = getY() + PAD;
			float plotWidth = Math.max(1f, getWidth() - PAD * 2f);
			float plotHeight = Math.max(1f, getHeight() - PAD * 2f);
			Color previewBackground = kit.palette().color(UiAppearance.Role.TIMELINE_PLOT);
			batch.setColor(previewBackground.r, previewBackground.g, previewBackground.b, parentAlpha);
			batch.draw(kit.white.getRegion(), getX(), getY(), getWidth(), getHeight());
			Color previewBorder = kit.palette().color(UiAppearance.Role.TIMELINE_DIVIDER);
			batch.setColor(previewBorder.r, previewBorder.g, previewBorder.b, parentAlpha);
			drawLine(batch, left, bottom, left + plotWidth, bottom, 1f);
			drawLine(batch, left, bottom, left, bottom + plotHeight, 1f);
			drawLine(batch, left, bottom + plotHeight, left + plotWidth, bottom + plotHeight, 1f);
			drawLine(batch, left + plotWidth, bottom, left + plotWidth, bottom + plotHeight, 1f);
			float cx1 = unit(number(x1Field, 0.42f));
			float cy1 = number(y1Field, 0f);
			float cx2 = unit(number(x2Field, 0.58f));
			float cy2 = number(y2Field, 1f);
			float yMin = curveYMin(cy1, cy2), yMax = curveYMax(cy1, cy2);
			Color previewGrid = kit.palette().color(UiAppearance.Role.TIMELINE_GRID);
			batch.setColor(previewGrid.r, previewGrid.g, previewGrid.b, parentAlpha);
			drawLine(batch, left, bottom + mapY(0f, yMin, yMax) * plotHeight,
				left + plotWidth, bottom + mapY(1f, yMin, yMax) * plotHeight, 1f);

			float handle1X = left + cx1 * plotWidth;
			float handle1Y = bottom + mapY(cy1, yMin, yMax) * plotHeight;
			float handle2X = left + cx2 * plotWidth;
			float handle2Y = bottom + mapY(cy2, yMin, yMax) * plotHeight;
			Color previewMuted = kit.palette().mutedText();
			batch.setColor(previewMuted.r, previewMuted.g, previewMuted.b, parentAlpha);
			drawLine(batch, left, bottom, handle1X, handle1Y, 1.5f);
			drawLine(batch, left + plotWidth, bottom + plotHeight, handle2X, handle2Y, 1.5f);

			batch.setColor(0.28f, 0.62f, 0.96f, parentAlpha);
			float previousX = left, previousY = bottom;
			for (int step = 1; step <= 48; step++) {
				float t = step / 48f;
				float x = left + cubic(0f, cx1, cx2, 1f, t) * plotWidth;
				float y = bottom + mapY(cubic(0f, cy1, cy2, 1f, t), yMin, yMax) * plotHeight;
				drawLine(batch, previousX, previousY, x, y, 2f);
				previousX = x;
				previousY = y;
			}

			Color previewText = kit.palette().text();
			batch.setColor(previewText.r, previewText.g, previewText.b, parentAlpha);
			batch.draw(kit.white.getRegion(), handle1X - 4f, handle1Y - 4f, 8f, 8f);
			batch.draw(kit.white.getRegion(), handle2X - 4f, handle2Y - 4f, 8f, 8f);
			batch.setColor(oldBatchColor);
		}

		private int nearestHandle(float x, float y) {
			float plotWidth = Math.max(1f, getWidth() - PAD * 2f);
			float plotHeight = Math.max(1f, getHeight() - PAD * 2f);
			float cy1 = number(y1Field, 0f), cy2 = number(y2Field, 1f);
			float yMin = curveYMin(cy1, cy2), yMax = curveYMax(cy1, cy2);
			float handle1X = PAD + unit(number(x1Field, 0.42f)) * plotWidth;
			float handle1Y = PAD + mapY(cy1, yMin, yMax) * plotHeight;
			float handle2X = PAD + unit(number(x2Field, 0.58f)) * plotWidth;
			float handle2Y = PAD + mapY(cy2, yMin, yMax) * plotHeight;
			float distance1 = distanceSquared(x, y, handle1X, handle1Y);
			float distance2 = distanceSquared(x, y, handle2X, handle2Y);
			float nearest = Math.min(distance1, distance2);
			return nearest <= 14f * 14f ? distance1 <= distance2 ? 1 : 2 : 0;
		}

		private void updateHandle(float x, float y) {
			if (draggedHandle == 0) return;
			float plotWidth = Math.max(1f, getWidth() - PAD * 2f);
			float plotHeight = Math.max(1f, getHeight() - PAD * 2f);
			float currentY1 = number(y1Field, 0f), currentY2 = number(y2Field, 1f);
			float yMin = curveYMin(currentY1, currentY2), yMax = curveYMax(currentY1, currentY2);
			float cx = unit((x - PAD) / plotWidth);
			float cy = yMin + unit((y - PAD) / plotHeight) * (yMax - yMin);
			if (draggedHandle == 1) {
				x1Field.setText(UiNumbers.format(cx));
				y1Field.setText(UiNumbers.format(cy));
			} else {
				x2Field.setText(UiNumbers.format(cx));
				y2Field.setText(UiNumbers.format(cy));
			}
		}

		private void drawLine(Batch batch, float x1, float y1, float x2, float y2, float thickness) {
			float dx = x2 - x1, dy = y2 - y1;
			float length = (float)Math.sqrt(dx * dx + dy * dy);
			float angle = (float)Math.toDegrees(Math.atan2(dy, dx));
			batch.draw(kit.white.getRegion(), x1, y1 - thickness / 2f, 0f, thickness / 2f,
				length, thickness, 1f, 1f, angle);
		}

		private float number(TextField field, float fallback) {
			try {
				return UiNumbers.parseFinite(field.getText());
			} catch (RuntimeException ignored) {
				return fallback;
			}
		}

		private float unit(float value) { return Math.max(0f, Math.min(1f, value)); }

		private float curveYMin(float control1, float control2) {
			float minimum = Math.min(0f, Math.min(control1, control2));
			float maximum = Math.max(1f, Math.max(control1, control2));
			return minimum < 0f ? minimum - (maximum - minimum) * 0.08f : 0f;
		}

		private float curveYMax(float control1, float control2) {
			float minimum = Math.min(0f, Math.min(control1, control2));
			float maximum = Math.max(1f, Math.max(control1, control2));
			return maximum > 1f ? maximum + (maximum - minimum) * 0.08f : 1f;
		}

		private float mapY(float value, float minimum, float maximum) {
			return unit((value - minimum) / Math.max(0.0001f, maximum - minimum));
		}

		private float cubic(float start, float control1, float control2, float end, float t) {
			float inverse = 1f - t;
			return inverse * inverse * inverse * start + 3f * inverse * inverse * t * control1
				+ 3f * inverse * t * t * control2 + t * t * t * end;
		}

		private float distanceSquared(float x1, float y1, float x2, float y2) {
			float dx = x2 - x1, dy = y2 - y1;
			return dx * dx + dy * dy;
		}
	}

	// ================= 关键帧画布 =================

	enum SlotProp { ATTACHMENT, COLOR, RGB, ALPHA, TWO_COLOR }

	static SlotProp primarySlotProp(boolean hasColor, boolean hasRgb, boolean hasAlpha,
		boolean hasTwoColor, boolean darkEnabled) {
		if (hasColor) return SlotProp.COLOR;
		if (hasTwoColor) return SlotProp.TWO_COLOR;
		if (hasRgb) return SlotProp.RGB;
		if (darkEnabled) return SlotProp.TWO_COLOR;
		return hasAlpha ? SlotProp.RGB : SlotProp.COLOR;
	}

	private static class Track {
		final Bone bone;
		final AnimationEditor.Prop prop;
		final SlotData slot;
		final SlotProp slotProp;
		final IkConstraintData ik;
		final TransformConstraintData transform;
		final PathConstraintData path;
		final AnimationEditor.PathProp pathProp;
		final PhysicsConstraintData physics;
		final AnimationEditor.PhysicsProp physicsProp;
		SliderData slider;
		AnimationEditor.SliderProp sliderProp;
		Attachment sequenceAttachment;
		String sequenceName;
		final VertexAttachment deformAttachment;
		final String deformName;
		final int constraintIndex;
		final boolean event;
		final boolean drawOrder;
		final boolean inherit;
		DrawOrderFolderTimeline drawOrderFolder;
		Animation.Timeline timeline;

		Track() {
			this.bone = null;
			this.prop = null;
			this.slot = null;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = -1;
			this.event = true;
			this.drawOrder = false;
			this.inherit = false;
		}

		Track(boolean drawOrder) {
			this.bone = null;
			this.prop = null;
			this.slot = null;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = -1;
			this.event = false;
			this.drawOrder = drawOrder;
			this.inherit = false;
		}

		Track(DrawOrderFolderTimeline drawOrderFolder) {
			if (drawOrderFolder == null) throw new IllegalArgumentException("Draw order folder is required");
			this.bone = null;
			this.prop = null;
			this.slot = null;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = -1;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
			this.drawOrderFolder = drawOrderFolder;
		}

		Track(Bone bone, AnimationEditor.Prop prop) {
			this.bone = bone;
			this.prop = prop;
			this.slot = null;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = -1;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
		}

		Track(Bone bone, boolean inherit) {
			this.bone = bone;
			this.prop = null;
			this.slot = null;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = -1;
			this.event = false;
			this.drawOrder = false;
			this.inherit = inherit;
		}

		Track(Bone bone, SlotData slot, SlotProp slotProp) {
			this.bone = bone;
			this.prop = null;
			this.slot = slot;
			this.slotProp = slotProp;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = -1;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
		}

		Track(IkConstraintData ik, int constraintIndex) {
			this.bone = null;
			this.prop = null;
			this.slot = null;
			this.slotProp = null;
			this.ik = ik;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = constraintIndex;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
		}

		Track(TransformConstraintData transform, int constraintIndex) {
			this.bone = null;
			this.prop = null;
			this.slot = null;
			this.slotProp = null;
			this.ik = null;
			this.transform = transform;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = constraintIndex;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
		}

		Track(PathConstraintData path, AnimationEditor.PathProp pathProp, int constraintIndex) {
			this.bone = null;
			this.prop = null;
			this.slot = null;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = path;
			this.pathProp = pathProp;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = constraintIndex;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
		}

		Track(PhysicsConstraintData physics, AnimationEditor.PhysicsProp physicsProp, int constraintIndex) {
			this.bone = null;
			this.prop = null;
			this.slot = null;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = physics;
			this.physicsProp = physicsProp;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = constraintIndex;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
		}

		Track(SliderData slider, AnimationEditor.SliderProp sliderProp, int constraintIndex) {
			this.bone = null;
			this.prop = null;
			this.slot = null;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.slider = slider;
			this.sliderProp = sliderProp;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = constraintIndex;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
		}

		Track(Bone bone, SlotData slot, VertexAttachment attachment, String deformName) {
			this.bone = bone;
			this.prop = null;
			this.slot = slot;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.deformAttachment = attachment;
			this.deformName = deformName;
			this.constraintIndex = -1;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
		}

		Track(Bone bone, SlotData slot, Attachment attachment, String sequenceName, boolean sequence) {
			if (!sequence) throw new IllegalArgumentException("Sequence track flag is required");
			this.bone = bone;
			this.prop = null;
			this.slot = slot;
			this.slotProp = null;
			this.ik = null;
			this.transform = null;
			this.path = null;
			this.pathProp = null;
			this.physics = null;
			this.physicsProp = null;
			this.sequenceAttachment = attachment;
			this.sequenceName = sequenceName;
			this.deformAttachment = null;
			this.deformName = null;
			this.constraintIndex = -1;
			this.event = false;
			this.drawOrder = false;
			this.inherit = false;
		}

		boolean isEvent() { return event; }
		boolean isDrawOrder() { return drawOrder; }
		boolean isDrawOrderFolder() { return drawOrderFolder != null; }
		boolean isAnyDrawOrder() { return isDrawOrder() || isDrawOrderFolder(); }
		boolean isIk() { return ik != null; }
		boolean isTransform() { return transform != null; }
		boolean isPath() { return path != null && pathProp != null; }
		boolean isPhysics() { return physicsProp != null; }
		boolean isSlider() { return slider != null && sliderProp != null; }
		boolean isSequence() { return sequenceAttachment != null; }
		boolean isDeform() { return deformAttachment != null; }
		boolean isInherit() { return inherit; }

		boolean isAttachment() {
			return slotProp == SlotProp.ATTACHMENT;
		}

		boolean isColor() {
			return slotProp == SlotProp.COLOR;
		}

		boolean isRgb() {
			return slotProp == SlotProp.RGB;
		}

		boolean isAlpha() {
			return slotProp == SlotProp.ALPHA;
		}

		boolean isTwoColor() {
			return slotProp == SlotProp.TWO_COLOR;
		}

		boolean isSlot() {
			return slotProp != null;
		}

		boolean isDiscrete() { return isEvent() || isAnyDrawOrder() || isInherit() || isAttachment() || isSequence()
			|| isPhysics() && physicsProp == AnimationEditor.PhysicsProp.RESET; }
	}

	private class KeyframeCanvas extends Widget {
		private final ShapeRenderer shapes = new ShapeRenderer();
		private final BitmapFont font = kit.font;
		private final GlyphLayout trackLabelLayout = new GlyphLayout();
		private final GlyphLayout rulerLabelLayout = new GlyphLayout();
		private final GlyphLayout curveValueLayout = new GlyphLayout();
		private final Matrix4 savedBatchTransform = new Matrix4();
		private final Matrix4 identityBatchTransform = new Matrix4();
		private float preferredWidth = MIN_VIEW_WIDTH_PX;
		private float preferredHeight = 80f;
		private static final int RULER_H = 40;
		private static final int LABEL_W = (int)TRACK_LABEL_COLUMN_WIDTH;
		private static final int CURVE_LABEL_W = LABEL_W;
		private static final float BASE_CURVE_ROW_HEIGHT = 23f;
		private static final float BASE_CURVE_PLOT_PADDING = 14f;
		private static final float BASE_CURVE_MIN_PLOT_HEIGHT = 220f;
		private static final float CURVE_HIT_RADIUS = 10f;
		private static final float MIN_VIEW_WIDTH_PX = 1800f;
		private static final float END_PADDING_FRAMES = 10f;
		private final com.badlogic.gdx.math.Vector2 drawOrigin = new com.badlogic.gdx.math.Vector2();
		private final Color[] curveColors = {
			new Color(0.95f, 0.38f, 0.28f, 1f), new Color(0.30f, 0.78f, 0.42f, 1f),
			new Color(0.28f, 0.64f, 0.96f, 1f), new Color(0.95f, 0.76f, 0.24f, 1f),
			new Color(0.76f, 0.46f, 0.94f, 1f), new Color(0.25f, 0.84f, 0.82f, 1f),
			new Color(0.96f, 0.48f, 0.72f, 1f)
		};
		private final Array<Track> tracks = new Array<>();
		private AttachmentTrackLabels.Index attachmentLabels = new AttachmentTrackLabels.Index(null);
		private final Array<KeySelection> extraSelections = new Array<>();
		private final Array<KeySelection> dragSelections = new Array<>();
		private final HashSet<Long> rulerKeyFrames = new HashSet<>();
		private boolean draggingKeys;
		private float dragAnchorTime, dragPreviewDelta;
		private boolean marqueeSelecting, marqueeDragging, marqueeAdditive;
		private float marqueeStartX, marqueeStartY, marqueeCurrentX, marqueeCurrentY;
		private boolean panning;
		private boolean rulerScrubbing;
		private boolean curveView = true;
		private Track curveTargetTrack;
		private Object curveTargetDocumentData;
		private boolean curveTargetPinned;
		private Animation.Timeline lastCurveSelectionTimeline;
		/** Tracks explicitly shown by the Graph hierarchy when curveVisibilityExplicit is true. */
		private final HashSet<String> curveVisibleTrackKeys = new HashSet<>();
		private boolean curveVisibilityExplicit;
		private Animation curveVisibilityAnimation;
		private Object viewStateDocumentData;
		private final HashSet<String> collapsedHierarchyGroups = new HashSet<>();
		private int curveSelectedChannel;
		private int curveDragHandle;
		private int curvePreviewSegment = -1;
		private float curvePreviewX1, curvePreviewY1, curvePreviewX2, curvePreviewY2;
		private float curveDragValueMin, curveDragValueMax;
		private Animation.Timeline curveDragTimeline;
		private Animation.Timeline curveMarqueeTimeline;
		private AnimationEditor.TimelineSnapshot curveDragBeforeSnapshot;
		private float panStartStageX, panStartStageY, panStartScrollX, panStartScrollY;
		private int dragTrack = -1, dragFrame = -1;
		private AnimationEditor.TimelineSnapshot dragBeforeSnapshot;
		private boolean dragChanged;
		private String selAnimationName, selBoneName, selSlotName, selIkConstraintName, selTransformConstraintName,
			selPathConstraintName, selPhysicsConstraintName, selSliderConstraintName;
		private AnimationEditor.PathProp selPathProp;
		private AnimationEditor.PhysicsProp selPhysicsProp;
		private AnimationEditor.SliderProp selSliderProp;
		private String selSequenceName;
		private Attachment selSequenceAttachment;
		private String selDeformName;
		private VertexAttachment selDeformAttachment;
		private SlotProp selSlotProp;
		private AnimationEditor.Prop selProp;
		private boolean selDrawOrder, selInherit;
		private int[] selDrawOrderFolderSlots;
		private int selEventFrame = -1;
		private float selTime;
		private boolean selectionVisible = true;
		private long lastClickNanos;
		private int lastClickTrack = -1, lastClickFrame = -1;
		private long lastBlankClickNanos;
		private int lastBlankClickTrack = -1;
		private float lastBlankClickTime = -1f;

		private final class KeySelection {
			final Track track;
			final float time;
			final int eventOccurrence;

			KeySelection(Track track, float time, int eventOccurrence) {
				this.track = track;
				this.time = time;
				this.eventOccurrence = eventOccurrence;
			}
		}

		private final class ResolvedKey {
			final KeySelection selection;
			final Track track;
			final Animation.Timeline timeline;
			final int frame;

			ResolvedKey(KeySelection selection, Track track, Animation.Timeline timeline, int frame) {
				this.selection = selection;
				this.track = track;
				this.timeline = timeline;
				this.frame = frame;
			}
		}

		KeyframeCanvas() {
			setTouchable(Touchable.enabled);
			addListener(trackTooltip((x, y) -> {
				if (x < horizontalScroll() || x >= timelineBoundary()) return "";
				CurveHierarchyHit hit = curveView ? curveChannelAtY(y) : timelineHierarchyHitAtY(y);
				return hit == null ? "" : hit.track == null ? hit.group.label : trackTooltipText(hit.track);
			}));
			addListener(new InputListener() {
				@Override
				public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
					if (getStage() != null) getStage().setKeyboardFocus(KeyframeCanvas.this);
					KeyframeCanvas.this.onTouchDown(event, x, y, button);
					return true;
				}
				@Override
				public void touchDragged(InputEvent event, float x, float y, int pointer) {
					KeyframeCanvas.this.onTouchDragged(event, x, y);
				}
				@Override
				public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
					KeyframeCanvas.this.onTouchUp(event, x, y, button);
				}
				@Override
				public boolean mouseMoved(InputEvent event, float x, float y) {
					return false;
				}
				@Override
				public boolean keyDown(InputEvent event, int keycode) {
					boolean ctrl = controlPressed();
					if (ctrl && keycode == Input.Keys.C) { copySelectedKeyframe(); return true; }
					if (ctrl && keycode == Input.Keys.V) { pasteCopiedKeyframe(); return true; }
					if (keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) {
						deleteSelectedKeyframe(); return true;
					}
					if (keycode == Input.Keys.LEFT) { selectAdjacentFrame(-1); return true; }
					if (keycode == Input.Keys.RIGHT) { selectAdjacentFrame(1); return true; }
					return false;
				}
			});
		}

		@Override public float getPrefWidth() { return preferredWidth; }

		@Override public float getPrefHeight() { return preferredHeight; }

		@Override public void act(float delta) {
			super.act(delta);
			syncPointerOperations();
		}

		private void setPreferredSize(float width, float height) {
			if (Float.compare(preferredWidth, width) == 0 && Float.compare(preferredHeight, height) == 0) return;
			preferredWidth = width;
			preferredHeight = height;
			invalidateHierarchy();
		}

		void setCurveView(boolean enabled) {
			if (enabled) rememberSelectedCurveTrack();
			if (curveView == enabled) return;
			curveView = enabled;
			curveSelectedChannel = 0;
			cancelPointerOperations();
			resetCurveHandleDrag();
			rebuild();
			if (scroll != null) {
				scroll.setScrollY(0f);
				scroll.updateVisualScroll();
			}
		}

		private int labelWidth() { return curveView ? CURVE_LABEL_W : LABEL_W; }

		private float timeOriginX() { return timelineOriginX(labelWidth()); }

		private Animation.Timeline curveTimeline() {
			Track track = resolvedCurveTrack();
			return track == null ? null : track.timeline;
		}

		private Track trackForTimeline(Animation.Timeline timeline) {
			if (timeline == null || doc.data == null) return null;
			Track track = null;
			if (timeline instanceof IkConstraintTimeline ik) {
				int index = ik.getConstraintIndex();
				if (index >= 0 && index < doc.data.getConstraints().size
					&& doc.data.getConstraints().get(index) instanceof IkConstraintData data)
					track = new Track(data, index);
			} else if (timeline instanceof TransformConstraintTimeline transform) {
				int index = transform.getConstraintIndex();
				if (index >= 0 && index < doc.data.getConstraints().size
					&& doc.data.getConstraints().get(index) instanceof TransformConstraintData data)
					track = new Track(data, index);
			} else if (pathPropOf(timeline) != null && timeline instanceof ConstraintTimeline constraint) {
				int index = constraint.getConstraintIndex();
				if (index >= 0 && index < doc.data.getConstraints().size
					&& doc.data.getConstraints().get(index) instanceof PathConstraintData data)
					track = new Track(data, pathPropOf(timeline), index);
			} else if (animEditor.physicsPropOf(timeline) != null
				&& timeline instanceof ConstraintTimeline constraint) {
				int index = constraint.getConstraintIndex();
				PhysicsConstraintData data = index >= 0 && index < doc.data.getConstraints().size
					&& doc.data.getConstraints().get(index) instanceof PhysicsConstraintData physics ? physics : null;
				if (index == -1 || data != null)
					track = new Track(data, animEditor.physicsPropOf(timeline), index);
			} else if (sliderPropOf(timeline) != null && timeline instanceof ConstraintTimeline constraint) {
				int index = constraint.getConstraintIndex();
				if (index >= 0 && index < doc.data.getConstraints().size
					&& doc.data.getConstraints().get(index) instanceof SliderData data)
					track = new Track(data, sliderPropOf(timeline), index);
			} else if (timeline instanceof DeformTimeline deform) {
				int index = deform.getSlotIndex();
				if (index >= 0 && index < doc.data.getSlots().size && doc.skeleton != null) {
					SlotData slot = doc.data.getSlots().get(index);
					var target = attachmentLabels.target(index, deform.getAttachment(), deform.getAttachment().getName());
					track = new Track(doc.skeleton.findBone(slot.getBoneData().getName()), slot, deform.getAttachment(), target.address().placeholder());
				}
			} else if (timeline instanceof SequenceTimeline sequence) {
				int index = sequence.getSlotIndex();
				if (index >= 0 && index < doc.data.getSlots().size && doc.skeleton != null) {
					SlotData slot = doc.data.getSlots().get(index);
					var target = attachmentLabels.target(index, sequence.getAttachment(), sequence.getAttachment().getName());
					track = new Track(doc.skeleton.findBone(slot.getBoneData().getName()), slot, sequence.getAttachment(), target.address().placeholder(), true);
				}
			} else if (timeline instanceof SlotCurveTimeline slotTimeline) {
				int slotIndex = slotTimeline.getSlotIndex();
				if (slotIndex >= 0 && slotIndex < doc.data.getSlots().size) {
					SlotData slot = doc.data.getSlots().get(slotIndex);
					Bone bone = doc.skeleton == null ? null : doc.skeleton.findBone(slot.getBoneData().getName());
					SlotProp prop = timeline instanceof RGBATimeline ? SlotProp.COLOR
						: timeline instanceof RGBTimeline ? SlotProp.RGB
						: timeline instanceof AlphaTimeline ? SlotProp.ALPHA
						: animEditor.isTwoColorTimeline(timeline) ? SlotProp.TWO_COLOR : null;
					if (bone != null && prop != null) track = new Track(bone, slot, prop);
				}
			} else if (timeline instanceof BoneTimeline boneTimeline) {
				int boneIndex = boneTimeline.getBoneIndex();
				AnimationEditor.Prop prop = animEditor.propOf(timeline);
				if (prop != null && doc.skeleton != null && boneIndex >= 0
					&& boneIndex < doc.skeleton.getBones().size)
					track = new Track(doc.skeleton.getBones().get(boneIndex), prop);
			}
			if (track != null) track.timeline = timeline;
			return track;
		}

		private Track curveTrack(Animation.Timeline timeline) {
			if (timeline == null) return null;
			for (Track track : tracks) if (track.timeline == timeline) return track;
			return null;
		}

		private boolean isCurveTrack(Track track) {
			return track != null && track.timeline instanceof Animation.CurveTimeline
				&& !(track.timeline instanceof DeformTimeline) && track.timeline.getFrameCount() > 0;
		}

		private void rememberSelectedCurveTrack() {
			curveTargetPinned = false;
			Track selected = curveTrack(selectedTimeline());
			if (!isCurveTrack(selected)) return;
			curveTargetTrack = selected;
			curveTargetDocumentData = doc.data;
			lastCurveSelectionTimeline = selectedTimeline();
		}

		private Track resolvedCurveTrack() {
			if (curveTargetDocumentData != doc.data) {
				curveTargetTrack = null;
				curveTargetDocumentData = doc.data;
			}
			Animation.Timeline selectedTimeline = selectedTimeline();
			if (lastCurveSelectionTimeline != selectedTimeline) {
				// A normal Dopesheet/tree selection supersedes a curve-only click.
				curveTargetPinned = false;
				lastCurveSelectionTimeline = selectedTimeline;
			}
			if (curveTargetPinned) {
				Track pinned = liveCurveTrack(curveTargetTrack);
				if (pinned != null) return pinned;
				curveTargetPinned = false;
			}
			int selectedIndex = -1;
			int rememberedIndex = -1;
			int fallbackIndex = -1;
			for (int index = 0; index < tracks.size; index++) {
				Track track = tracks.get(index);
				if (!isCurveTrack(track)) continue;
				if (fallbackIndex < 0) fallbackIndex = index;
				if (track.timeline == selectedTimeline) selectedIndex = index;
				if (rememberedIndex < 0 && curveTargetTrack != null && sameTrack(track, curveTargetTrack))
					rememberedIndex = index;
			}
			int resolvedIndex = preferredCurveTrackIndex(selectedIndex, rememberedIndex, fallbackIndex);
			if (resolvedIndex < 0) return null;
			curveTargetTrack = tracks.get(resolvedIndex);
			curveTargetDocumentData = doc.data;
			return curveTargetTrack;
		}

		private int curveChannelCount() {
			Animation.Timeline timeline = curveTimeline();
			return timeline == null ? 0 : Math.max(1, AnimationEditor.curveChannelCount(timeline));
		}

		/**
		 * Returns every keyed continuous track currently available to the Graph.
		 * This is intentionally separate from {@link #curveDisplayTracks()}: the
		 * hierarchy must continue to list hidden rows so a second row can be made
		 * visible without first restoring the whole graph.
		 */
		private Array<Track> allCurveTracks() {
			Array<Track> result = new Array<>();
			for (int index = 0; index < tracks.size; index++)
				addCurveDisplayTrack(result, tracks.get(index));
			for (KeySelection selection : allSelections()) addCurveDisplayTrack(result, selection.track);
			addCurveDisplayTrack(result, liveCurveTrack(curveTargetTrack));
			if (result.isEmpty()) addCurveDisplayTrack(result, resolvedCurveTrack());
			return result;
		}

		/**
		 * Spine's Graph keeps the hierarchy independent from the plot filter. A
		 * plain hierarchy click isolates one track; Ctrl/Shift toggles tracks in the
		 * current visible set. With no explicit filter every keyed curve is shown.
		 */
		private Array<Track> curveDisplayTracks() {
			Array<Track> available = allCurveTracks();
			if (!curveVisibilityExplicit) return available;
			Array<Track> result = new Array<>();
			for (Track track : available) {
				if (curveVisibleTrackKeys.contains(trackStateKey(track))) result.add(track);
			}
			return result;
		}

		private boolean curveTrackVisible(Track track) {
			return isCurveTrack(track) && (!curveVisibilityExplicit
				|| curveVisibleTrackKeys.contains(trackStateKey(track)));
		}

		private void clearCurveVisibility() {
			curveVisibleTrackKeys.clear();
			curveVisibilityExplicit = false;
		}

		private void pruneCurveVisibility() {
			if (!curveVisibilityExplicit) return;
			HashSet<String> available = new HashSet<>();
			for (Track track : allCurveTracks()) available.add(trackStateKey(track));
			curveVisibleTrackKeys.retainAll(available);
		}

		private void setCurveVisibility(Array<Track> targets, boolean additive) {
			HashSet<String> targetKeys = new HashSet<>();
			if (targets != null) {
				for (Track target : targets) {
					if (isCurveTrack(target)) targetKeys.add(trackStateKey(target));
				}
			}
			if (targetKeys.isEmpty()) return;
			if (!additive) {
				curveVisibleTrackKeys.clear();
				curveVisibleTrackKeys.addAll(targetKeys);
				curveVisibilityExplicit = true;
				refreshCurveVisibilityCount();
				return;
			}
			if (!curveVisibilityExplicit) {
				curveVisibleTrackKeys.clear();
				for (Track track : allCurveTracks()) curveVisibleTrackKeys.add(trackStateKey(track));
				curveVisibilityExplicit = true;
			}
			for (String key : targetKeys) {
				if (!curveVisibleTrackKeys.add(key)) curveVisibleTrackKeys.remove(key);
			}
			refreshCurveVisibilityCount();
		}

		private void setCurveVisibility(Track target, boolean additive) {
			Array<Track> targets = new Array<>();
			if (target != null) targets.add(target);
			setCurveVisibility(targets, additive);
		}

		private void refreshCurveVisibilityCount() {
			if (curveView) trackCountLabel.setText(curveDisplayTracks().size + I18n.t(" 条", " tracks"));
		}

		private Track liveCurveTrack(Track candidate) {
			if (!isCurveTrack(candidate)) return null;
			Track live = curveTrack(candidate.timeline);
			if (live != null) {
				curveTargetTrack = live;
				curveTargetDocumentData = doc.data;
			}
			return live;
		}

		private void addCurveDisplayTrack(Array<Track> result, Track candidate) {
			if (!isCurveTrack(candidate)) return;
			Track live = curveTrack(candidate.timeline);
			if (live != null) candidate = live;
			Animation animation = currentAnim();
			if (animation == null || !animation.getTimelines().contains(candidate.timeline, true)) return;
			for (Track existing : result) if (sameTrack(existing, candidate)) return;
			result.add(candidate);
		}

		private enum CurveHierarchyKind {
			BONE, SLOT, IK, TRANSFORM, PATH, PHYSICS, SLIDER, EVENT, DRAW_ORDER, UNKNOWN
		}

		private final class CurveHierarchyGroup {
			final String key;
			final String label;
			final CurveHierarchyKind kind;
			final Array<Track> tracks = new Array<>();

			CurveHierarchyGroup(String key, String label, CurveHierarchyKind kind) {
				this.key = key;
				this.label = label;
				this.kind = kind;
			}
		}

		private Array<CurveHierarchyGroup> curveHierarchyGroups() {
			return hierarchyGroups(allCurveTracks());
		}

		private Array<CurveHierarchyGroup> timelineHierarchyGroups() {
			Array<Track> visible = new Array<>();
			for (Track track : tracks) {
				if (track != null && !track.isAnyDrawOrder()) visible.add(track);
			}
			return hierarchyGroups(visible);
		}

		private int timelineVisibleTrackCount() {
			int count = 0;
			for (Track track : tracks) if (track != null && !track.isAnyDrawOrder()) count++;
			return count;
		}

		private Array<CurveHierarchyGroup> hierarchyGroups(Array<Track> source) {
			Array<CurveHierarchyGroup> groups = new Array<>();
			for (int index = 0; index < source.size; index++) {
				Track track = source.get(index);
				String key = curveGroupKey(track);
				CurveHierarchyGroup group = null;
				for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
					CurveHierarchyGroup candidate = groups.get(groupIndex);
					if (candidate.key.equals(key)) { group = candidate; break; }
				}
				if (group == null) {
					group = new CurveHierarchyGroup(key, curveGroupLabel(track), curveGroupKind(track));
					groups.add(group);
				}
				group.tracks.add(track);
			}
			return groups;
		}

		private CurveHierarchyKind curveGroupKind(Track track) {
			if (track == null) return CurveHierarchyKind.UNKNOWN;
			if (track.isEvent()) return CurveHierarchyKind.EVENT;
			if (track.isAnyDrawOrder()) return CurveHierarchyKind.DRAW_ORDER;
			if (track.isIk()) return CurveHierarchyKind.IK;
			if (track.isTransform()) return CurveHierarchyKind.TRANSFORM;
			if (track.isPath()) return CurveHierarchyKind.PATH;
			if (track.isPhysics()) return CurveHierarchyKind.PHYSICS;
			if (track.isSlider()) return CurveHierarchyKind.SLIDER;
			if (track.isSlot() || track.isSequence() || track.isDeform()) return CurveHierarchyKind.SLOT;
			if (track.bone != null) return CurveHierarchyKind.BONE;
			return CurveHierarchyKind.UNKNOWN;
		}

		private String curveGroupKey(Track track) {
			if (track.isEvent()) return "event";
			if (track.isDrawOrder()) return "draw-order";
			if (track.isDrawOrderFolder()) return trackStateKey(track);
			if (track.isIk()) return stateKey("ik", track.ik.getName());
			if (track.isTransform()) return stateKey("transform", track.transform.getName());
			if (track.isPath()) return stateKey("path", track.path.getName());
			if (track.isPhysics()) return stateKey("physics",
				track.physics == null ? GLOBAL_PHYSICS_SELECTION : track.physics.getName());
			if (track.isSlider()) return stateKey("slider", track.slider.getName());
			if (track.isSequence() || track.isDeform() || track.isSlot())
				return stateKey("slot", track.slot == null ? "" : track.slot.getName());
			return stateKey("bone", track.bone == null ? "" : track.bone.getData().getName());
		}

		private String curveGroupLabel(Track track) {
			if (track.isEvent()) return "事件";
			if (track.isDrawOrder()) return "绘制顺序";
			if (track.isDrawOrderFolder()) return drawOrderFolderLabel(track.drawOrderFolder);
			// Spine keeps the object row focused on the object name. The type is
			// carried by the colored glyph, while the child row names the property.
			if (track.isIk()) return track.ik.getName();
			if (track.isTransform()) return track.transform.getName();
			if (track.isPath()) return track.path.getName();
			if (track.isPhysics()) return track.physics == null ? "Physics" : track.physics.getName();
			if (track.isSlider()) return track.slider.getName();
			if (track.isSequence() || track.isDeform() || track.isSlot())
				return track.slot == null ? "槽" : track.slot.getName();
			return track.bone == null ? "骨骼" : track.bone.getData().getName();
		}

		private String curveGraphTrackLabel(Track track) {
			if (track == null) return "轨道";
			if (track.isIk()) return "IK: " + track.ik.getName();
			if (track.isTransform()) return "Transform: " + track.transform.getName();
			if (track.isPath()) return pathPropLabel(track.pathProp);
			if (track.isPhysics()) return physicsPropLabel(track.physicsProp);
			if (track.isSlider()) return sliderPropLabel(track.sliderProp);
			return curveTrackLabel(track);
		}

		private String timelineGroupLabel(CurveHierarchyGroup group) {
			if (group == null || group.tracks.isEmpty()) return group == null ? "轨道" : group.label;
			Track track = group.tracks.first();
			if (track.isIk()) return "IK · " + track.ik.getName();
			if (track.isTransform()) return "Transform · " + track.transform.getName();
			if (track.isPath()) return "Path · " + track.path.getName();
			if (track.isPhysics()) return "Physics · "
				+ (track.physics == null ? "全局" : track.physics.getName());
			if (track.isSlider()) return "Slider · " + track.slider.getName();
			return group.label;
		}

		private String curveTrackLabel(Track track) {
			if (track.isEvent()) return "事件";
			if (track.isDrawOrder()) return "绘制顺序";
			if (track.isDrawOrderFolder()) return "局部绘制顺序";
			if (track.isIk()) return "混合 / 柔度";
			if (track.isTransform()) return "混合";
			if (track.isPath()) return pathPropLabel(track.pathProp);
			if (track.isPhysics()) return physicsPropLabel(track.physicsProp);
			if (track.isSlider()) return sliderPropLabel(track.sliderProp);
			if (track.isSequence()) return attachmentTarget(track).rowLabel("Sequence");
			if (track.isDeform()) return attachmentTarget(track).rowLabel("Deform");
			if (track.isInherit()) return "继承";
			if (track.isSlot()) return switch (track.slotProp) {
			case ATTACHMENT -> "附件";
			case COLOR -> "颜色";
			case RGB -> "RGB";
			case ALPHA -> "透明度";
			case TWO_COLOR -> "双色";
			};
			return propLabel(track.prop);
		}

		private boolean curveGroupCollapsed(CurveHierarchyGroup group) {
			return group != null && collapsedHierarchyGroups.contains(group.key);
		}

		private int curveHierarchyRows() {
			Array<CurveHierarchyGroup> groups = curveHierarchyGroups();
			int rows = 1; // animation row
			for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
				CurveHierarchyGroup group = groups.get(groupIndex);
				rows++;
				if (curveGroupCollapsed(group)) continue;
				for (int trackIndex = 0; trackIndex < group.tracks.size; trackIndex++) {
					Track track = group.tracks.get(trackIndex);
					rows += 1 + Math.max(1, AnimationEditor.curveChannelCount(track.timeline));
				}
			}
			return Math.max(2, rows);
		}

		private int timelineHierarchyRows() {
			int rows = 1; // animation row
			Array<CurveHierarchyGroup> groups = timelineHierarchyGroups();
			for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
				CurveHierarchyGroup group = groups.get(groupIndex);
				rows++;
				if (!curveGroupCollapsed(group)) rows += group.tracks.size;
			}
			return rows;
		}

		private int visibleTimelineRow(Track target) {
			if (target == null) return -1;
			int row = 1; // animation row
			Array<CurveHierarchyGroup> groups = timelineHierarchyGroups();
			for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
				CurveHierarchyGroup group = groups.get(groupIndex);
				row++;
				if (curveGroupCollapsed(group)) continue;
				for (int trackIndex = 0; trackIndex < group.tracks.size; trackIndex++) {
					if (sameTrack(group.tracks.get(trackIndex), target)) return row;
					row++;
				}
			}
			return -1;
		}

		private void refreshCurveHierarchyLayout() {
			if (!curveView) {
				float height = Math.max(80f, timelineHierarchyRows() * rowHeight() + RULER_H + 4f);
				setPreferredSize(preferredWidth, height);
			} else {
				Animation animation = currentAnim();
				float duration = animation == null || !Float.isFinite(animation.getDuration())
					? 0f : Math.max(0f, animation.getDuration());
				float width = Math.max(MIN_VIEW_WIDTH_PX,
					timeOriginX() + (duration * frameRate() + END_PADDING_FRAMES) * pixelsPerFrame());
				float curveHeight = RULER_H + curveContentHeight(curveMinPlotHeight(), curvePlotPadding(),
					curveHierarchyRows() * curveRowHeight());
				setPreferredSize(width, Math.max(280f, curveHeight));
			}
			TimelinePanel.this.invalidateHierarchy();
			TimelinePanel.this.validate();
			if (scroll != null) {
				scroll.layout();
				scroll.updateVisualScroll();
			}
		}

		private void selectAdjacentFrame(int direction) {
			Animation.Timeline timeline = selectedTimeline();
			int frame = selectedFrame(timeline);
			int next = frame + direction;
			if (timeline == null || next < 0 || next >= timeline.getFrameCount()) return;
			float time = animEditor.frameTime(timeline, next);
			if (timeline instanceof EventTimeline) selectEventKeyframe(next);
			else if (timeline instanceof DrawOrderTimeline) selectDrawOrderKeyframe(time);
			else if (timeline instanceof DrawOrderFolderTimeline folder)
				selectDrawOrderFolderKeyframe(folder, time);
			else if (timeline instanceof IkConstraintTimeline) selectIkKeyframe(selectedIkConstraintData(), time);
			else if (timeline instanceof TransformConstraintTimeline)
				selectTransformKeyframe(selectedTransformConstraintData(), time);
			else if (pathPropOf(timeline) != null)
				selectPathKeyframe(selectedPathConstraintData(), pathPropOf(timeline), time);
			else if (animEditor.physicsPropOf(timeline) != null)
				selectPhysicsKeyframe(selectedPhysicsConstraintData(),
					((ConstraintTimeline)timeline).getConstraintIndex() == -1,
					animEditor.physicsPropOf(timeline), time);
			else if (sliderPropOf(timeline) != null)
				selectSliderKeyframe(selectedSliderConstraintData(), sliderPropOf(timeline), time);
			else if (timeline instanceof SequenceTimeline sequence)
				selectSequenceKeyframe(selectedSlotData(), sequence.getAttachment(), selectedSequenceName(), time);
			else if (timeline instanceof DeformTimeline deform)
				selectDeformKeyframe(selectedSlotData(), deform.getAttachment(), selectedDeformName(), time);
			else if (timeline instanceof InheritTimeline) selectInheritKeyframe(selectedBone(), time);
			else if (timeline instanceof AttachmentTimeline) selectAttachmentKeyframe(selectedSlotData(), time);
			else if (timeline instanceof RGBATimeline) selectColorKeyframe(selectedSlotData(), time);
			else if (timeline instanceof RGBTimeline) selectRgbKeyframe(selectedSlotData(), time);
			else if (timeline instanceof AlphaTimeline) selectAlphaKeyframe(selectedSlotData(), time);
			else if (animEditor.isTwoColorTimeline(timeline)) selectTwoColorKeyframe(selectedSlotData(), time);
			else selectKeyframe(selectedBone(), selProp, time);
			scrubTo(time);
			ensureTimeVisible(time);
		}

		private void ensureTimeVisible(float time) {
			if (scroll == null) return;
			float keyX = xAtTime(time);
			float left = horizontalScroll() + timeOriginX();
			float right = horizontalScroll() + scroll.getWidth() - 12f;
			if (keyX < left) scroll.setScrollX(Math.max(0f, keyX - timeOriginX()));
			else if (keyX > right) scroll.setScrollX(keyX - scroll.getWidth() + 12f);
		}

		void rebuild() {
			// Rebuilt tracks and history restores invalidate previews captured by the old pointer press.
			cancelPointerOperations();
			boolean documentChanged = viewStateDocumentData != doc.data;
			AttachmentTrackLabels.Index previousLabels = documentChanged ? null : attachmentLabels;
			attachmentLabels = new AttachmentTrackLabels.Index(doc.data);
			migrateAttachmentViewState(previousLabels);
			SkeletonDocument.TimelineViewState restoredState = documentChanged ? doc.timelineViewState() : null;
			if (documentChanged) {
				viewStateDocumentData = doc.data;
				clearSelection();
				clearCurveVisibility();
				curveVisibilityAnimation = null;
				curveTargetPinned = false;
				lastCurveSelectionTimeline = null;
				curveView = restoredState != null && restoredState.curveView();
				curveSelectedChannel = restoredState == null ? 0 : restoredState.curveSelectedChannel();
				keyframeViewBtn.setChecked(!curveView);
				curveViewBtn.setChecked(curveView);
			}
			if (curveTargetDocumentData != doc.data) {
				curveTargetTrack = null;
				curveTargetPinned = false;
				lastCurveSelectionTimeline = null;
				curveTargetDocumentData = doc.data;
			}
			tracks.clear();
			lastFilteredBoneName = selectedBoneName();
			Bone root = doc.skeleton == null ? null : doc.skeleton.getRootBone();
			if (root == null) {
				curveTargetTrack = null;
				curveTargetPinned = false;
				clearSelection();
				trackCountLabel.setText(I18n.t("0 条", "0 tracks"));
				setPreferredSize(MIN_VIEW_WIDTH_PX, curveView ? 280f : 80f);
				if (documentChanged) restoreViewState(restoredState);
				return;
			}
			Animation animation = currentAnim();
			if (curveVisibilityAnimation != animation) {
				clearCurveVisibility();
				curveVisibilityAnimation = animation;
			}
			RebuildContext context = new RebuildContext(animation);
			if (animation != null && (doc.data.getEvents().notEmpty() || context.eventTimeline != null))
				addTrack(new Track(), context);
			if (animation != null && doc.data != null && doc.data.getSlots().notEmpty())
				addTrack(new Track(true), context);
			if (animation != null)
				for (DrawOrderFolderTimeline folder : context.drawOrderFolders)
					addTrack(new Track(folder), context);
			if (animation != null && doc.data != null) {
				boolean hasPhysicsConstraint = false;
				for (int index = 0; index < doc.data.getConstraints().size; index++) {
					if (doc.data.getConstraints().get(index) instanceof IkConstraintData ik)
						addTrack(new Track(ik, index), context);
					else if (doc.data.getConstraints().get(index) instanceof TransformConstraintData transform)
						addTrack(new Track(transform, index), context);
					else if (doc.data.getConstraints().get(index) instanceof PathConstraintData path) {
						addTrack(new Track(path, AnimationEditor.PathProp.POSITION, index), context);
						addTrack(new Track(path, AnimationEditor.PathProp.SPACING, index), context);
						addTrack(new Track(path, AnimationEditor.PathProp.MIX, index), context);
					} else if (doc.data.getConstraints().get(index) instanceof PhysicsConstraintData physics) {
						hasPhysicsConstraint = true;
						for (AnimationEditor.PhysicsProp prop : AnimationEditor.PhysicsProp.values())
							addTrack(new Track(physics, prop, index), context);
					} else if (doc.data.getConstraints().get(index) instanceof SliderData slider) {
						for (AnimationEditor.SliderProp prop : AnimationEditor.SliderProp.values())
							if (sliderPropVisible(slider, prop)) addTrack(new Track(slider, prop, index), context);
					}
				}
				if (hasPhysicsConstraint) {
					for (AnimationEditor.PhysicsProp prop : AnimationEditor.PhysicsProp.values()) {
						Track global = new Track(null, prop, -1);
						if (prop == AnimationEditor.PhysicsProp.RESET || context.timelineFor(global) != null
							|| anyGlobalPhysicsProperty(prop)) addTrack(global, context);
					}
				}
			}
			collectTracks(root, context);
			pruneCurveVisibility();
			int visibleTrackCount = curveView ? curveDisplayTracks().size : timelineVisibleTrackCount();
			trackCountLabel.setText(visibleTrackCount + I18n.t(" 条", " tracks"));
			float duration = animation == null || !Float.isFinite(animation.getDuration())
				? 0f : Math.max(0f, animation.getDuration());
			float width = Math.max(MIN_VIEW_WIDTH_PX,
				timeOriginX() + (duration * frameRate() + END_PADDING_FRAMES) * pixelsPerFrame());
			float curveHeight = RULER_H + curveContentHeight(curveMinPlotHeight(), curvePlotPadding(),
				curveHierarchyRows() * curveRowHeight());
			setPreferredSize(width, curveView ? Math.max(280f, curveHeight)
				: Math.max(80f, timelineHierarchyRows() * rowHeight() + RULER_H + 4f));
			pruneExtraSelections();
			RebuildSelection rebuilt = rebuildSelection(selectedTimelineIgnoringVisibility() != null, hasSelectedTrack());
			if (rebuilt == RebuildSelection.CLEAR) clearSelection();
			else {
				selectionVisible = rebuilt == RebuildSelection.VISIBLE;
				refreshKeyframeInspector();
			}
			if (documentChanged) restoreViewState(restoredState);
		}

		private void migrateAttachmentViewState(AttachmentTrackLabels.Index previous) {
			SkeletonDocument.TimelineViewState state = doc.timelineViewState();
			String[] keys = state.selectedTrackKeys();
			boolean changed = false;
			for (int i = 0; i < keys.length; i++) {
				String migrated = attachmentLabels.migrateKey(previous, keys[i]);
				changed |= !keys[i].equals(migrated);
				keys[i] = migrated;
			}
			String curve = attachmentLabels.migrateKey(previous, state.curveTrackKey());
			if (changed || !curve.equals(state.curveTrackKey()))
				doc.setTimelineViewState(new SkeletonDocument.TimelineViewState(state.animation(), state.curveView(),
					state.curveSelectedChannel(), curve, state.scrollX(), state.scrollY(), state.selectionStored(),
					keys, state.selectedTimes(), state.selectedEventOccurrences()));
			SkeletonDocument.GraphWorkspaceState graph = doc.graphWorkspaceState();
			String detail = attachmentLabels.migrateKey(previous, graph.detailTrackKey());
			if (!detail.equals(graph.detailTrackKey()))
				doc.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(graph.open(), graph.animation(),
					graph.filter(), graph.locked(), detail, graph.splitAmount(), graph.scrollX(), graph.scrollY(),
					graph.x(), graph.y(), graph.width(), graph.height()));
		}

		void persistViewState() {
			Animation animation = currentAnim();
			if (doc.data == null) {
				doc.setTimelineViewState(SkeletonDocument.TimelineViewState.empty());
				return;
			}
			Array<KeySelection> selections = allSelections();
			Array<String> trackKeys = new Array<>();
			com.badlogic.gdx.utils.FloatArray times = new com.badlogic.gdx.utils.FloatArray();
			com.badlogic.gdx.utils.IntArray occurrences = new com.badlogic.gdx.utils.IntArray();
			for (KeySelection selection : selections) {
				String trackKey = trackStateKey(selection.track);
				if (trackKey.isEmpty()) continue;
				trackKeys.add(trackKey);
				times.add(selection.time);
				occurrences.add(Math.max(0, selection.eventOccurrence));
			}
			if (trackKeys.isEmpty()) {
				Animation.Timeline selected = selectedTimelineIgnoringVisibility();
				String trackKey = selected == null ? "" : selectedTrackStateKey();
				if (!trackKey.isEmpty()) {
					trackKeys.add(trackKey);
					times.add(selTime);
					int occurrence = selected instanceof EventTimeline events
						? Math.max(0, eventOccurrence(events, selectedFrame(events))) : 0;
					occurrences.add(occurrence);
				}
			}
			String curveTrackKey = curveTargetDocumentData == doc.data && curveTargetTrack != null
				? trackStateKey(curveTargetTrack) : "";
			doc.setTimelineViewState(new SkeletonDocument.TimelineViewState(
				animation == null ? "" : animation.getName(), curveView, curveSelectedChannel,
				curveTrackKey, scroll == null ? 0f : scroll.getScrollX(),
				scroll == null ? 0f : scroll.getScrollY(), true,
				trackKeys.toArray(String[]::new), times.toArray(), occurrences.toArray()));
		}

		private void restoreViewState(SkeletonDocument.TimelineViewState state) {
			if (state == null) return;
			Animation animation = currentAnim();
			boolean matchingAnimation = animation != null && animation.getName().equals(state.animation());
			if (matchingAnimation && state.selectionStored()) {
				String[] trackKeys = state.selectedTrackKeys();
				float[] times = state.selectedTimes();
				int[] occurrences = state.selectedEventOccurrences();
				Array<KeySelection> restored = new Array<>();
				for (int index = 0; index < trackKeys.length; index++) {
					Track track = findTrackByStateKey(trackKeys[index]);
					if (track == null || track.timeline == null) continue;
					int frame = track.timeline instanceof EventTimeline events
						? eventFrameFor(events, times[index], occurrences[index])
						: animEditor.findFrame(track.timeline, times[index]);
					KeySelection selection = keySelection(track, track.timeline, frame);
					if (selection != null && !containsSelection(restored, selection)) restored.add(selection);
				}
				applySelectionSet(restored, restored.isEmpty() ? null : restored.first());
			}
			if (matchingAnimation && !state.curveTrackKey().isEmpty()) {
				Track restoredCurveTrack = findTrackByStateKey(state.curveTrackKey());
				if (isCurveTrack(restoredCurveTrack)) {
					curveTargetTrack = restoredCurveTrack;
					curveTargetPinned = false;
					curveTargetDocumentData = doc.data;
				}
			}
			curveSelectedChannel = Math.min(state.curveSelectedChannel(),
				Math.max(0, curveChannelCount() - 1));
			restoreScroll(state.scrollX(), state.scrollY());
		}

		private void restoreScroll(float scrollX, float scrollY) {
			if (scroll == null) return;
			Object restoredDocument = doc.data;
			Runnable apply = () -> {
				if (doc.data != restoredDocument || viewStateDocumentData != restoredDocument) return;
				TimelinePanel.this.invalidateHierarchy();
				TimelinePanel.this.validate();
				scroll.layout();
				scroll.setScrollX(scrollX);
				scroll.setScrollY(scrollY);
				scroll.updateVisualScroll();
			};
			apply.run();
			if (Gdx.app != null) Gdx.app.postRunnable(apply);
		}

		private Track findTrackByStateKey(String key) {
			if (key == null || key.isEmpty()) return null;
			for (Track track : tracks) if (key.equals(trackStateKey(track))) return track;
			AttachmentTrackLabels.Index.Binding binding = attachmentLabels.resolveKey(key);
			if (binding != null) for (Track track : tracks) {
				Attachment attachment = track.isDeform() ? track.deformAttachment : track.sequenceAttachment;
				if (attachment == binding.attachment() && track.slot.getIndex() == binding.slot()
					&& (key.startsWith("deform\u001f") ? track.isDeform() : track.isSequence())) return track;
			}
			return null;
		}

		private String selectedTrackStateKey() {
			if (selEventFrame >= 0) return stateKey("event");
			if (selDrawOrder) return stateKey("draw-order");
			if (selDrawOrderFolderSlots != null)
				return stateKey("draw-order-folder", java.util.Arrays.toString(selDrawOrderFolderSlots));
			if (selIkConstraintName != null) return stateKey("ik", selIkConstraintName);
			if (selTransformConstraintName != null) return stateKey("transform", selTransformConstraintName);
			if (selPathConstraintName != null && selPathProp != null)
				return stateKey("path", selPathConstraintName, selPathProp.name());
			if (selPhysicsConstraintName != null && selPhysicsProp != null)
				return stateKey("physics", selPhysicsConstraintName, selPhysicsProp.name());
			if (selSliderConstraintName != null && selSliderProp != null)
				return stateKey("slider", selSliderConstraintName, selSliderProp.name());
			if (selSequenceAttachment != null)
				return selectedAttachmentTarget().address().stateKey("sequence");
			if (selDeformAttachment != null)
				return selectedAttachmentTarget().address().stateKey("deform");
			if (selSlotName != null && selSlotProp != null)
				return stateKey("slot", selSlotName, selSlotProp.name());
			if (selBoneName != null && selInherit) return stateKey("bone", selBoneName, "INHERIT");
			if (selBoneName != null && selProp != null) return stateKey("bone", selBoneName, selProp.name());
			return "";
		}

		private String trackStateKey(Track track) {
			if (track == null) return "";
			if (track.isEvent()) return stateKey("event");
			if (track.isDrawOrder()) return stateKey("draw-order");
			if (track.isDrawOrderFolder())
				return stateKey("draw-order-folder", java.util.Arrays.toString(track.drawOrderFolder.getSlots()));
			if (track.isIk()) return stateKey("ik", track.ik.getName());
			if (track.isTransform()) return stateKey("transform", track.transform.getName());
			if (track.isPath()) return stateKey("path", track.path.getName(), track.pathProp.name());
			if (track.isPhysics()) return stateKey("physics",
				track.physics == null ? GLOBAL_PHYSICS_SELECTION : track.physics.getName(), track.physicsProp.name());
			if (track.isSlider()) return stateKey("slider", track.slider.getName(), track.sliderProp.name());
			if (track.isSequence()) return attachmentTarget(track).address().stateKey("sequence");
			if (track.isDeform()) return attachmentTarget(track).address().stateKey("deform");
			if (track.isSlot()) return stateKey("slot", track.slot.getName(), track.slotProp.name());
			return stateKey("bone", track.bone.getData().getName(),
				track.isInherit() ? "INHERIT" : track.prop.name());
		}

		private String stateKey(String... values) {
			String[] normalized = values.clone();
			for (int index = 0; index < normalized.length; index++)
				if (normalized[index] == null) normalized[index] = "";
			return String.join("\u001f", normalized);
		}

		private final class RebuildContext {
			final Animation animation;
			final String filter = trackFilterBox.getSelected();
			final String selectedName = lastFilteredBoneName;
			final BoneData selectedBone = doc.data == null || selectedName == null ? null : doc.data.findBone(selectedName);
			final String query = trackSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
			final IdentityHashMap<BoneData, Array<SlotData>> slotsByBone =
				indexSlotsByBone(doc.data == null ? null : doc.data.getSlots());
			final Animation.Timeline[][] boneTimelines;
			final InheritTimeline[] inheritTimelines;
			final AttachmentTimeline[] attachmentTimelines;
			final RGBATimeline[] colorTimelines;
			final RGBTimeline[] rgbTimelines;
			final AlphaTimeline[] alphaTimelines;
			final SlotCurveTimeline[] twoColorTimelines;
			final IkConstraintTimeline[] ikTimelines;
			final TransformConstraintTimeline[] transformTimelines;
			DrawOrderTimeline drawOrderTimeline;
			final Array<DrawOrderFolderTimeline> drawOrderFolders = new Array<>();
			EventTimeline eventTimeline;

			RebuildContext(Animation animation) {
				this.animation = animation;
				int boneCount = doc.data == null ? 0 : doc.data.getBones().size;
				int slotCount = doc.data == null ? 0 : doc.data.getSlots().size;
				boneTimelines = new Animation.Timeline[boneCount][AnimationEditor.Prop.values().length];
				inheritTimelines = new InheritTimeline[boneCount];
				attachmentTimelines = new AttachmentTimeline[slotCount];
				colorTimelines = new RGBATimeline[slotCount];
				rgbTimelines = new RGBTimeline[slotCount];
				alphaTimelines = new AlphaTimeline[slotCount];
				twoColorTimelines = new SlotCurveTimeline[slotCount];
				ikTimelines = new IkConstraintTimeline[doc.data == null ? 0 : doc.data.getConstraints().size];
				transformTimelines = new TransformConstraintTimeline[
					doc.data == null ? 0 : doc.data.getConstraints().size];
				if (animation == null) return;
				for (Animation.Timeline timeline : animation.getTimelines()) index(timeline);
			}

			private void index(Animation.Timeline timeline) {
				if (timeline instanceof DrawOrderTimeline drawOrder) {
					drawOrderTimeline = drawOrder;
				} else if (timeline instanceof DrawOrderFolderTimeline folder) {
					drawOrderFolders.add(folder);
				} else if (timeline instanceof IkConstraintTimeline ik) {
					int constraintIndex = ik.getConstraintIndex();
					if (constraintIndex >= 0 && constraintIndex < ikTimelines.length)
						ikTimelines[constraintIndex] = ik;
				} else if (timeline instanceof TransformConstraintTimeline transform) {
					int constraintIndex = transform.getConstraintIndex();
					if (constraintIndex >= 0 && constraintIndex < transformTimelines.length)
						transformTimelines[constraintIndex] = transform;
				} else if (timeline instanceof BoneTimeline boneTimeline) {
					int boneIndex = boneTimeline.getBoneIndex();
					if (timeline instanceof InheritTimeline inherit) {
						if (boneIndex >= 0 && boneIndex < inheritTimelines.length) inheritTimelines[boneIndex] = inherit;
						return;
					}
					AnimationEditor.Prop prop = animEditor.propOf(timeline);
					if (prop != null && boneIndex >= 0 && boneIndex < boneTimelines.length)
						boneTimelines[boneIndex][prop.ordinal()] = timeline;
				} else if (timeline instanceof AttachmentTimeline attachment) {
					int slotIndex = attachment.getSlotIndex();
					if (slotIndex >= 0 && slotIndex < attachmentTimelines.length)
						attachmentTimelines[slotIndex] = attachment;
				} else if (timeline instanceof RGBATimeline color) {
					int slotIndex = color.getSlotIndex();
					if (slotIndex >= 0 && slotIndex < colorTimelines.length) colorTimelines[slotIndex] = color;
				} else if (timeline instanceof RGBTimeline color) {
					int slotIndex = color.getSlotIndex();
					if (slotIndex >= 0 && slotIndex < rgbTimelines.length) rgbTimelines[slotIndex] = color;
				} else if (timeline instanceof AlphaTimeline alpha) {
					int slotIndex = alpha.getSlotIndex();
					if (slotIndex >= 0 && slotIndex < alphaTimelines.length) alphaTimelines[slotIndex] = alpha;
				} else if (animEditor.isTwoColorTimeline(timeline)) {
					SlotCurveTimeline twoColor = (SlotCurveTimeline)timeline;
					int slotIndex = twoColor.getSlotIndex();
					if (slotIndex >= 0 && slotIndex < twoColorTimelines.length)
						twoColorTimelines[slotIndex] = twoColor;
				} else if (timeline instanceof EventTimeline events) {
					eventTimeline = events;
				}
			}

			Animation.Timeline timelineFor(Track track) {
				if (track.isEvent()) return eventTimeline;
				if (track.isDrawOrder()) return drawOrderTimeline;
				if (track.isDrawOrderFolder())
					return animEditor.findDrawOrderFolderTimeline(animation, track.drawOrderFolder.getSlots());
				if (track.isIk()) return ikTimeline(track.constraintIndex);
				if (track.isTransform()) return transformTimeline(track.constraintIndex);
				if (track.isPath())
					return animEditor.findPathTimeline(animation, track.constraintIndex, track.pathProp);
				if (track.isPhysics())
					return animEditor.findPhysicsTimeline(animation, track.constraintIndex, track.physicsProp);
				if (track.isSlider())
					return animEditor.findSliderTimeline(animation, track.constraintIndex, track.sliderProp);
				if (track.isSequence())
					return animEditor.findSequenceTimeline(animation, track.slot.getIndex(), track.sequenceAttachment);
				if (track.isDeform())
					return animEditor.findDeformTimeline(animation, track.slot.getIndex(), track.deformAttachment);
				if (track.isInherit()) return inheritTimeline(track.bone.getData().getIndex());
				if (track.isSlot()) {
					int slotIndex = track.slot.getIndex();
					return switch (track.slotProp) {
					case ATTACHMENT -> attachmentTimeline(slotIndex);
					case COLOR -> colorTimeline(slotIndex);
					case RGB -> rgbTimeline(slotIndex);
					case ALPHA -> alphaTimeline(slotIndex);
					case TWO_COLOR -> twoColorTimeline(slotIndex);
					};
				}
				int boneIndex = track.bone.getData().getIndex();
				return boneIndex < 0 || boneIndex >= boneTimelines.length || track.prop == null
					? null : boneTimelines[boneIndex][track.prop.ordinal()];
			}

			InheritTimeline inheritTimeline(int boneIndex) {
				return boneIndex < 0 || boneIndex >= inheritTimelines.length ? null : inheritTimelines[boneIndex];
			}

			AttachmentTimeline attachmentTimeline(int slotIndex) {
				return slotIndex < 0 || slotIndex >= attachmentTimelines.length ? null : attachmentTimelines[slotIndex];
			}

			RGBATimeline colorTimeline(int slotIndex) {
				return slotIndex < 0 || slotIndex >= colorTimelines.length ? null : colorTimelines[slotIndex];
			}

			RGBTimeline rgbTimeline(int slotIndex) {
				return slotIndex < 0 || slotIndex >= rgbTimelines.length ? null : rgbTimelines[slotIndex];
			}

			AlphaTimeline alphaTimeline(int slotIndex) {
				return slotIndex < 0 || slotIndex >= alphaTimelines.length ? null : alphaTimelines[slotIndex];
			}

			SlotCurveTimeline twoColorTimeline(int slotIndex) {
				return slotIndex < 0 || slotIndex >= twoColorTimelines.length ? null : twoColorTimelines[slotIndex];
			}

			IkConstraintTimeline ikTimeline(int constraintIndex) {
				return constraintIndex < 0 || constraintIndex >= ikTimelines.length ? null : ikTimelines[constraintIndex];
			}

			TransformConstraintTimeline transformTimeline(int constraintIndex) {
				return constraintIndex < 0 || constraintIndex >= transformTimelines.length
					? null : transformTimelines[constraintIndex];
			}
		}

		private boolean hasSelectedTrack() {
			for (Track track : tracks) {
				if (track.isEvent()) {
					if (selEventFrame >= 0) return true;
				} else if (track.isDrawOrder()) {
					if (selDrawOrder) return true;
				} else if (track.isDrawOrderFolder()) {
					if (java.util.Arrays.equals(track.drawOrderFolder.getSlots(), selDrawOrderFolderSlots)) return true;
				} else if (track.isIk()) {
					if (track.ik.getName().equals(selIkConstraintName)) return true;
				} else if (track.isTransform()) {
					if (track.transform.getName().equals(selTransformConstraintName)) return true;
				} else if (track.isPath()) {
					if (track.path.getName().equals(selPathConstraintName) && track.pathProp == selPathProp) return true;
				} else if (track.isPhysics()) {
					String name = track.physics == null ? GLOBAL_PHYSICS_SELECTION : track.physics.getName();
					if (name.equals(selPhysicsConstraintName) && track.physicsProp == selPhysicsProp) return true;
				} else if (track.isSlider()) {
					if (track.slider.getName().equals(selSliderConstraintName)
						&& track.sliderProp == selSliderProp) return true;
				} else if (track.isSequence()) {
					if (track.sequenceAttachment == selSequenceAttachment
						&& track.slot.getName().equals(selSlotName)) return true;
				} else if (track.isDeform()) {
					if (track.deformAttachment == selDeformAttachment
						&& track.slot.getName().equals(selSlotName)) return true;
				} else if (track.isSlot()) {
					if (track.slot.getName().equals(selSlotName) && track.slotProp == selSlotProp) return true;
				} else if (selSlotName == null && track.isInherit() == selInherit && track.prop == selProp
					&& track.bone.getData().getName().equals(selBoneName)) return true;
			}
			return false;
		}

		private void selectKeyframe(Bone bone, AnimationEditor.Prop prop, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = bone == null ? null : bone.getData().getName();
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = null;
			selProp = prop;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectInheritKeyframe(Bone bone, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = bone == null ? null : bone.getData().getName();
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = true;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectDrawOrderKeyframe(float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = null;
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = true;
			selInherit = false;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectDrawOrderFolderKeyframe(DrawOrderFolderTimeline folder, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = null;
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selDrawOrderFolderSlots = folder == null ? null : folder.getSlots().clone();
			selInherit = false;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectIkKeyframe(IkConstraintData constraint, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = null;
			selSlotName = null;
			selIkConstraintName = constraint == null ? null : constraint.getName();
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectTransformKeyframe(TransformConstraintData constraint, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = null;
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = constraint == null ? null : constraint.getName();
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectPathKeyframe(PathConstraintData constraint, AnimationEditor.PathProp prop, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = null;
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = constraint == null ? null : constraint.getName();
			selPathProp = constraint == null ? null : prop;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectPhysicsKeyframe(PhysicsConstraintData constraint, boolean global,
			AnimationEditor.PhysicsProp prop, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = null;
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selPhysicsConstraintName = global ? GLOBAL_PHYSICS_SELECTION
				: constraint == null ? null : constraint.getName();
			selPhysicsProp = selPhysicsConstraintName == null ? null : prop;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectSliderKeyframe(SliderData constraint, AnimationEditor.SliderProp prop, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = null;
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSliderConstraintName = constraint == null ? null : constraint.getName();
			selSliderProp = constraint == null ? null : prop;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectAttachmentKeyframe(SlotData slot, float time) {
			selectSlotKeyframe(slot, SlotProp.ATTACHMENT, time);
		}

		private void selectSequenceKeyframe(SlotData slot, Attachment attachment, String name, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = slot == null ? null : slot.getBoneData().getName();
			selSlotName = slot == null ? null : slot.getName();
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selSequenceAttachment = attachment;
			selSequenceName = name;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectColorKeyframe(SlotData slot, float time) {
			selectSlotKeyframe(slot, SlotProp.COLOR, time);
		}

		private void selectRgbKeyframe(SlotData slot, float time) {
			selectSlotKeyframe(slot, SlotProp.RGB, time);
		}

		private void selectAlphaKeyframe(SlotData slot, float time) {
			selectSlotKeyframe(slot, SlotProp.ALPHA, time);
		}

		private void selectTwoColorKeyframe(SlotData slot, float time) {
			selectSlotKeyframe(slot, SlotProp.TWO_COLOR, time);
		}

		private void selectSlotKeyframe(SlotData slot, SlotProp slotProp, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = slot == null ? null : slot.getBoneData().getName();
			selSlotName = slot == null ? null : slot.getName();
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = slot == null ? null : slotProp;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void selectEventKeyframe(int frame) {
			clearDeformSelection();
			Animation animation = currentAnim();
			EventTimeline timeline = animEditor.findEventTimeline(animation);
			if (timeline == null || frame < 0 || frame >= timeline.getFrameCount()) { clearSelection(); return; }
			selAnimationName = animation.getName();
			selBoneName = null;
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = frame;
			selTime = animEditor.eventAt(timeline, frame).getTime();
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void clearSelection() {
			extraSelections.clear();
			cancelPointerOperations();
			clearDeformSelection();
			selAnimationName = null;
			selBoneName = null;
			selSlotName = null;
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selTime = 0f;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private void clearDeformSelection() {
			selDeformAttachment = null;
			selDeformName = null;
			selSequenceAttachment = null;
			selSequenceName = null;
			selPhysicsConstraintName = null;
			selPhysicsProp = null;
			selSliderConstraintName = null;
			selSliderProp = null;
			selDrawOrderFolderSlots = null;
		}

		private void selectDeformKeyframe(SlotData slot, VertexAttachment attachment, String name, float time) {
			clearDeformSelection();
			Animation animation = currentAnim();
			selAnimationName = animation == null ? null : animation.getName();
			selBoneName = slot == null ? null : slot.getBoneData().getName();
			selSlotName = slot == null ? null : slot.getName();
			selIkConstraintName = null;
			selTransformConstraintName = null;
			selPathConstraintName = null;
			selPathProp = null;
			selPhysicsConstraintName = null;
			selPhysicsProp = null;
			selSliderConstraintName = null;
			selSliderProp = null;
			selSequenceAttachment = null;
			selSequenceName = null;
			selSlotProp = null;
			selProp = null;
			selDrawOrder = false;
			selInherit = false;
			selEventFrame = -1;
			selDeformAttachment = attachment;
			selDeformName = name;
			selTime = time;
			selectionVisible = true;
			refreshKeyframeInspector();
		}

		private String selectedDeformName() {
			return selDeformName == null ? "Mesh" : selDeformName;
		}

		private String selectedSequenceName() {
			return selSequenceName == null ? "Attachment" : selSequenceName;
		}

		private AttachmentTrackLabels.Target selectedAttachmentTarget() {
			SlotData slot = selectedSlotData();
			return attachmentLabels.target(slot == null ? -1 : slot.getIndex(),
				selDeformAttachment != null ? selDeformAttachment : selSequenceAttachment,
				selDeformAttachment != null ? selectedDeformName() : selectedSequenceName());
		}

		private String selectedTrackTooltip() {
			if (selDeformAttachment != null) return selectedAttachmentTarget().tooltip("Deform");
			if (selSequenceAttachment != null) return selectedAttachmentTarget().tooltip("Sequence");
			return keySelectionLabel.getText().toString();
		}

		private Bone selectedBone() {
			return doc.skeleton == null || selBoneName == null ? null : doc.skeleton.findBone(selBoneName);
		}

		private SlotData selectedSlotData() {
			return doc.data == null || selSlotName == null ? null : doc.data.findSlot(selSlotName);
		}

		private IkConstraintData selectedIkConstraintData() {
			if (doc.data == null || selIkConstraintName == null) return null;
			for (com.esotericsoftware.spine.ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
				if (constraint instanceof IkConstraintData ik && ik.getName().equals(selIkConstraintName)) return ik;
			}
			return null;
		}

		private TransformConstraintData selectedTransformConstraintData() {
			if (doc.data == null || selTransformConstraintName == null) return null;
			for (com.esotericsoftware.spine.ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof TransformConstraintData transform
					&& transform.getName().equals(selTransformConstraintName)) return transform;
			return null;
		}

		private PathConstraintData selectedPathConstraintData() {
			if (doc.data == null || selPathConstraintName == null) return null;
			for (com.esotericsoftware.spine.ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof PathConstraintData path
					&& path.getName().equals(selPathConstraintName)) return path;
			return null;
		}

		private PhysicsConstraintData selectedPhysicsConstraintData() {
			if (doc.data == null || selPhysicsConstraintName == null || selPhysicsConstraintName.isEmpty()) return null;
			for (com.esotericsoftware.spine.ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof PhysicsConstraintData physics
					&& physics.getName().equals(selPhysicsConstraintName)) return physics;
			return null;
		}

		private SliderData selectedSliderConstraintData() {
			if (doc.data == null || selSliderConstraintName == null) return null;
			for (com.esotericsoftware.spine.ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof SliderData slider
					&& slider.getName().equals(selSliderConstraintName)) return slider;
			return null;
		}

		private int selectedPhysicsConstraintIndex() {
			if (GLOBAL_PHYSICS_SELECTION.equals(selPhysicsConstraintName)) return -1;
			PhysicsConstraintData physics = selectedPhysicsConstraintData();
			return physics == null || doc.data == null ? Integer.MIN_VALUE
				: doc.data.getConstraints().indexOf(physics, true);
		}

		private String selectedPhysicsLabel() {
			if (GLOBAL_PHYSICS_SELECTION.equals(selPhysicsConstraintName)) return "Physics · 全局";
			return "Physics · " + (selPhysicsConstraintName == null ? "?" : selPhysicsConstraintName);
		}

		private Animation.Timeline selectedTimeline() {
			return selectionVisible ? selectedTimelineIgnoringVisibility() : null;
		}

		private Animation.Timeline selectedTimelineIgnoringVisibility() {
			Animation animation = currentAnim();
			if (animation == null || !animation.getName().equals(selAnimationName)) return null;
			if (selEventFrame >= 0) {
				EventTimeline events = animEditor.findEventTimeline(animation);
				return selectedFrame(events) < 0 ? null : events;
			}
			if (selDrawOrder) {
				DrawOrderTimeline drawOrder = animEditor.findDrawOrderTimeline(animation);
				return selectedFrame(drawOrder) < 0 ? null : drawOrder;
			}
			if (selDrawOrderFolderSlots != null) {
				DrawOrderFolderTimeline folder = animEditor.findDrawOrderFolderTimeline(
					animation, selDrawOrderFolderSlots);
				return selectedFrame(folder) < 0 ? null : folder;
			}
			IkConstraintData ik = selectedIkConstraintData();
			if (ik != null) {
				int constraintIndex = doc.data.getConstraints().indexOf(ik, true);
				IkConstraintTimeline timeline = animEditor.findIkTimeline(animation, constraintIndex);
				return selectedFrame(timeline) < 0 ? null : timeline;
			}
			TransformConstraintData transform = selectedTransformConstraintData();
			if (transform != null) {
				int constraintIndex = doc.data.getConstraints().indexOf(transform, true);
				TransformConstraintTimeline timeline = animEditor.findTransformTimeline(animation, constraintIndex);
				return selectedFrame(timeline) < 0 ? null : timeline;
			}
			PathConstraintData path = selectedPathConstraintData();
			if (path != null && selPathProp != null) {
				int constraintIndex = doc.data.getConstraints().indexOf(path, true);
				Animation.Timeline timeline = animEditor.findPathTimeline(animation, constraintIndex, selPathProp);
				return selectedFrame(timeline) < 0 ? null : timeline;
			}
			int physicsIndex = selectedPhysicsConstraintIndex();
			if (physicsIndex != Integer.MIN_VALUE && selPhysicsProp != null) {
				Animation.Timeline timeline = animEditor.findPhysicsTimeline(animation, physicsIndex, selPhysicsProp);
				return selectedFrame(timeline) < 0 ? null : timeline;
			}
			SliderData slider = selectedSliderConstraintData();
			if (slider != null && selSliderProp != null) {
				int constraintIndex = doc.data.getConstraints().indexOf(slider, true);
				Animation.Timeline timeline = animEditor.findSliderTimeline(animation, constraintIndex, selSliderProp);
				return selectedFrame(timeline) < 0 ? null : timeline;
			}
			if (selSequenceAttachment != null) {
				SlotData sequenceSlot = selectedSlotData();
				if (sequenceSlot == null) return null;
				SequenceTimeline timeline = animEditor.findSequenceTimeline(animation, sequenceSlot.getIndex(),
					selSequenceAttachment);
				return selectedFrame(timeline) < 0 ? null : timeline;
			}
			if (selDeformAttachment != null) {
				SlotData deformSlot = selectedSlotData();
				if (deformSlot == null) return null;
				DeformTimeline timeline = animEditor.findDeformTimeline(animation, deformSlot.getIndex(),
					selDeformAttachment);
				return selectedFrame(timeline) < 0 ? null : timeline;
			}
			Animation.Timeline timeline;
			SlotData slot = selectedSlotData();
			if (slot != null && selProp == null) {
				if (selSlotProp == null) return null;
				timeline = switch (selSlotProp) {
				case COLOR -> animEditor.findColorTimeline(animation, slot.getIndex());
				case RGB -> animEditor.findRgbTimeline(animation, slot.getIndex());
				case ALPHA -> animEditor.findAlphaTimeline(animation, slot.getIndex());
				case TWO_COLOR -> animEditor.findTwoColorTimeline(animation, slot.getIndex());
				case ATTACHMENT -> animEditor.findAttachmentTimeline(animation, slot.getIndex());
				};
			} else {
				Bone bone = selectedBone();
				if (bone == null) return null;
				if (selInherit) timeline = animEditor.findInheritTimeline(animation, bone.getData().getIndex());
				else {
					if (selProp == null) return null;
					timeline = animEditor.findTimeline(animation, selProp, bone.getData().getIndex());
				}
			}
			return selectedFrame(timeline) < 0 ? null : timeline;
		}

		private boolean hasHiddenSelection() {
			return !selectionVisible && selectedTimelineIgnoringVisibility() != null;
		}

		private int selectedFrame(Animation.Timeline timeline) {
			if (timeline instanceof EventTimeline events) {
				if (selEventFrame < 0 || selEventFrame >= events.getFrameCount()) return -1;
				return Float.compare(animEditor.eventAt(events, selEventFrame).getTime(), selTime) == 0
					? selEventFrame : -1;
			}
			return animEditor.findFrame(timeline, selTime);
		}

		private void collectTracks(Bone bone, RebuildContext context) {
			addTrack(bone, AnimationEditor.Prop.ROTATE, context);
			addTrack(new Track(bone, true), context);
			collectAxisTracks(bone, AnimationEditor.AxisGroup.SCALE, context);
			collectAxisTracks(bone, AnimationEditor.AxisGroup.TRANSLATE, context);
			collectAxisTracks(bone, AnimationEditor.AxisGroup.SHEAR, context);
			Array<SlotData> boneSlots = context.slotsByBone.get(bone.getData());
			if (boneSlots != null) {
				for (SlotData slot : boneSlots) {
					addTrack(new Track(bone, slot, SlotProp.ATTACHMENT), context);
					collectAttachmentSpecificTracks(bone, slot, context);
					int slotIndex = slot.getIndex();
					boolean hasColor = context.colorTimeline(slotIndex) != null;
					boolean hasRgb = context.rgbTimeline(slotIndex) != null;
					boolean hasAlpha = context.alphaTimeline(slotIndex) != null;
					SlotCurveTimeline twoColor = context.twoColorTimeline(slotIndex);
					boolean hasTwoColor = twoColor != null;
					SlotProp primary = primarySlotProp(hasColor, hasRgb, hasAlpha, hasTwoColor,
						slot.getSetupPose().getDarkColor() != null);
					addTrack(new Track(bone, slot, primary), context);

					boolean alphaCompatible = !hasColor && !(twoColor instanceof com.esotericsoftware.spine.Animation.RGBA2Timeline);
					if (hasAlpha || alphaCompatible && (hasRgb || twoColor instanceof RGB2Timeline))
						addTrack(new Track(bone, slot, SlotProp.ALPHA), context);
				}
			}
			Array<Bone> children = bone.getChildren();
			for (Bone c : children) collectTracks(c, context);
		}

		private void collectAxisTracks(Bone bone, AnimationEditor.AxisGroup group, RebuildContext context) {
			AnimationEditor.Prop combined = switch (group) {
			case TRANSLATE -> AnimationEditor.Prop.TRANSLATE;
			case SCALE -> AnimationEditor.Prop.SCALE;
			case SHEAR -> AnimationEditor.Prop.SHEAR;
			};
			AnimationEditor.Prop x = switch (group) {
			case TRANSLATE -> AnimationEditor.Prop.TRANSLATE_X;
			case SCALE -> AnimationEditor.Prop.SCALE_X;
			case SHEAR -> AnimationEditor.Prop.SHEAR_X;
			};
			AnimationEditor.Prop y = switch (group) {
			case TRANSLATE -> AnimationEditor.Prop.TRANSLATE_Y;
			case SCALE -> AnimationEditor.Prop.SCALE_Y;
			case SHEAR -> AnimationEditor.Prop.SHEAR_Y;
			};
			int boneIndex = bone.getData().getIndex();
			boolean separated = axisSeparationEnabled(context.animation, boneIndex, group);
			boolean hasCombined = animEditor.findTimeline(context.animation, combined, boneIndex) != null;
			boolean hasAxes = animEditor.findTimeline(context.animation, x, boneIndex) != null
				|| animEditor.findTimeline(context.animation, y, boneIndex) != null;
			if (separated) {
				if (hasCombined && hasAxes) addTrack(bone, combined, context);
				addTrack(bone, x, context);
				addTrack(bone, y, context);
			} else {
				addTrack(bone, combined, context);
				if (hasAxes) {
					addTrackIfPresent(bone, x, context);
					addTrackIfPresent(bone, y, context);
				}
			}
		}

		private void collectAttachmentSpecificTracks(Bone bone, SlotData slot, RebuildContext context) {
			if (doc.data == null) return;
			Array<VertexAttachment> deformAttachments = new Array<>(false, 4);
			IdentityHashMap<Attachment, Boolean> sequenceAttachments = new IdentityHashMap<>();
			for (Skin skin : doc.data.getSkins())
				collectAttachmentSpecificTracks(bone, slot, skin, context, deformAttachments, sequenceAttachments);
			Skin defaultSkin = doc.data.getDefaultSkin();
			if (defaultSkin != null && !doc.data.getSkins().contains(defaultSkin, true))
				collectAttachmentSpecificTracks(bone, slot, defaultSkin, context, deformAttachments, sequenceAttachments);
		}

		private void collectAttachmentSpecificTracks(Bone bone, SlotData slot, Skin skin, RebuildContext context,
			Array<VertexAttachment> deformAttachments, IdentityHashMap<Attachment, Boolean> sequenceAttachments) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (entry.getSlotIndex() != slot.getIndex()) continue;
				Attachment attachment = entry.getAttachment();
				if (attachment instanceof HasSequence hasSequence && hasSequence.getSequence() != null
					&& ownsSequenceTimeline(attachment)
					&& !sequenceAttachments.containsKey(attachment)) {
					Track sequenceTrack = new Track(bone, slot, attachment,
						attachmentLabels.target(slot.getIndex(), attachment, entry.getPlaceholder()).address().placeholder(), true);
					if (hasSequence.getSequence().hasPathSuffix() || context.timelineFor(sequenceTrack) != null) {
						sequenceAttachments.put(attachment, Boolean.TRUE);
						addTrack(sequenceTrack, context);
					}
				}
				if (!(attachment instanceof VertexAttachment vertex) || !ownsDeformTimeline(vertex)
					|| deformAttachments.contains(vertex, true)) continue;
				deformAttachments.add(vertex);
				addTrack(new Track(bone, slot, vertex,
					attachmentLabels.target(slot.getIndex(), vertex, entry.getPlaceholder()).address().placeholder()), context);
			}
		}

		private void addTrackIfPresent(Bone bone, AnimationEditor.Prop prop, RebuildContext context) {
			Track track = new Track(bone, prop);
			if (context.timelineFor(track) != null) addTrack(track, context);
		}

		private void addTrack(Bone bone, AnimationEditor.Prop prop, RebuildContext context) {
			addTrack(new Track(bone, prop), context);
		}

		private boolean anyGlobalPhysicsProperty(AnimationEditor.PhysicsProp prop) {
			return AnimationEditor.hasGlobalPhysicsProperty(doc.data, prop);
		}

		private boolean globalPhysicsAffectsBone(AnimationEditor.PhysicsProp prop, BoneData bone) {
			if (doc.data == null || bone == null) return false;
			for (com.esotericsoftware.spine.ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
				if (!(constraint instanceof PhysicsConstraintData physics) || physics.getBone() != bone) continue;
				if (prop == AnimationEditor.PhysicsProp.RESET || physicsPropertyIsGlobal(physics, prop)) return true;
			}
			return false;
		}

		private PhysicsConstraintPose physicsPoseForTrack(Track track) {
			if (track == null || !track.isPhysics() || track.physicsProp == AnimationEditor.PhysicsProp.RESET)
				throw new IllegalArgumentException("Physics 数值轨道无效");
			PhysicsConstraintData source = track.physics;
			if (source == null && doc.data != null) {
				for (com.esotericsoftware.spine.ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
					if (!(constraint instanceof PhysicsConstraintData physics)) continue;
					if (source == null) source = physics;
					if (physicsPropertyIsGlobal(physics, track.physicsProp)) { source = physics; break; }
				}
			}
			if (source == null) throw new IllegalStateException("找不到 Physics 约束");
			PhysicsConstraint runtime = doc.skeleton == null ? null
				: doc.skeleton.findConstraint(source.getName(), PhysicsConstraint.class);
			return runtime == null ? source.getSetupPose() : runtime.getAppliedPose();
		}

		private float sliderPoseValue(Track track) {
			if (track == null || !track.isSlider()) throw new IllegalArgumentException("Slider 数值轨道无效");
			com.esotericsoftware.spine.Slider runtime = doc.skeleton == null ? null
				: doc.skeleton.findConstraint(track.slider.getName(), com.esotericsoftware.spine.Slider.class);
			SliderPose pose = runtime == null ? track.slider.getSetupPose() : runtime.getAppliedPose();
			return track.sliderProp == AnimationEditor.SliderProp.TIME ? pose.getTime() : pose.getMix();
		}

		private AnimationEditor.SequenceKeyframe currentSequenceKeyframe(Track track) {
			if (track == null || !track.isSequence()
				|| !(track.sequenceAttachment instanceof HasSequence hasSequence))
				throw new IllegalArgumentException("Sequence track is invalid");
			Sequence sequence = hasSequence.getSequence();
			if (sequence == null || sequence.getRegions() == null || sequence.getRegions().length == 0)
				throw new IllegalArgumentException("Sequence attachment data is invalid");
			int index = sequence.getSetupIndex();
			Slot slot = doc.skeleton == null ? null : doc.skeleton.findSlot(track.slot.getName());
			if (slot != null && slot.getAppliedPose().getAttachment() == track.sequenceAttachment
				&& slot.getAppliedPose().getSequenceIndex() >= 0)
				index = slot.getAppliedPose().getSequenceIndex();
			return new AnimationEditor.SequenceKeyframe(SequenceMode.hold, index, 1f / frameRate());
		}

		private void addTrack(Track track, RebuildContext context) {
			track.timeline = context.timelineFor(track);
			if (trackVisible(track, context)) tracks.add(track);
		}

		private boolean trackVisible(Track track, RebuildContext context) {
			if (trackFilterMatches(context.filter, FILTER_SELECTED)) {
				if (track.isEvent() || track.isAnyDrawOrder()) return false;
				if (track.isIk()) {
					if (!ikAffectsBone(track.ik, context.selectedBone)) return false;
				} else if (track.isTransform()) {
					if (!transformAffectsBone(track.transform, context.selectedBone)) return false;
				} else if (track.isPath()) {
					if (!pathAffectsBone(track.path, context.selectedBone)) return false;
				} else if (track.isPhysics()) {
					if (track.physics == null) {
						if (!globalPhysicsAffectsBone(track.physicsProp, context.selectedBone)) return false;
					} else if (!physicsAffectsBone(track.physics, context.selectedBone)) return false;
				} else if (track.isSlider()) {
					if (!sliderAffectsBone(track.slider, doc.data, context.selectedBone)) return false;
				} else if (context.selectedName == null
					|| !context.selectedName.equals(track.bone.getData().getName())) return false;
			} else if (trackFilterMatches(context.filter, FILTER_KEYED) && track.timeline == null) {
				return false;
			}
			if (track.isSequence() || track.isDeform())
				return attachmentTarget(track).matches(track.isSequence() ? "Sequence" : "Deform", context.query);
			return context.query.isEmpty()
				|| trackLabel(track).toLowerCase(java.util.Locale.ROOT).contains(context.query);
		}

		private String trackLabel(Track track) {
			if (track.isEvent()) return "事件";
			if (track.isDrawOrder()) return "绘制顺序";
			if (track.isDrawOrderFolder()) return drawOrderFolderLabel(track.drawOrderFolder);
			if (track.isIk()) return "IK · " + track.ik.getName();
			if (track.isTransform()) return "Transform · " + track.transform.getName();
			if (track.isPath()) return "Path · " + track.path.getName() + " · " + pathPropLabel(track.pathProp);
			if (track.isPhysics()) return "Physics · "
				+ (track.physics == null ? "全局" : track.physics.getName()) + " · " + physicsPropLabel(track.physicsProp);
			if (track.isSlider())
				return "Slider · " + track.slider.getName() + " · " + sliderPropLabel(track.sliderProp);
			if (track.isSequence()) return attachmentTarget(track).fullLabel("Sequence");
			if (track.isDeform()) return attachmentTarget(track).fullLabel("Deform");
			if (track.isInherit()) return track.bone.getData().getName() + " · 继承";
			if (track.isAttachment()) return track.slot.getName() + " · 附件";
			if (track.isColor()) return track.slot.getName() + " · 颜色";
			if (track.isRgb()) return track.slot.getName() + " · RGB";
			if (track.isAlpha()) return track.slot.getName() + " · 透明度";
			if (track.isTwoColor()) return track.slot.getName() + " · 双色";
			return track.bone.getData().getName() + " · " + propLabel(track.prop);
		}

		private AttachmentTrackLabels.Target attachmentTarget(Track track) {
			return attachmentLabels.target(track.slot.getIndex(),
				track.isDeform() ? track.deformAttachment : track.sequenceAttachment,
				track.isDeform() ? track.deformName : track.sequenceName);
		}

		private String trackTooltipText(Track track) {
			if (track.isDeform()) return attachmentTarget(track).tooltip("Deform");
			if (track.isSequence()) return attachmentTarget(track).tooltip("Sequence");
			return trackLabel(track);
		}

		private float timeAtX(float x) {
			return snapTime(Math.max(0, (x - timeOriginX()) / pixelsPerSecond()));
		}

		private float xAtTime(float t) {
			return timeOriginX() + t * pixelsPerSecond();
		}

		private float snapTime(float time) { return UiNumbers.snapTime(time, frameRate()); }

		private float horizontalScroll() { return scroll == null ? 0f : scroll.getScrollX(); }

		private float verticalScroll() { return scroll == null ? 0f : scroll.getVisualScrollY(); }

		private float rulerBottom() { return stickyRulerBottom(getHeight(), RULER_H, verticalScroll()); }

		private float pixelsPerFrame() { return timelinePixelsPerFrame(timelineZoom); }

		private float pixelsPerSecond() {
			return timelinePixelsPerSecond(timelineZoom, frameRate());
		}

		private float rowHeight() { return timelineTrackRowHeight(timelineVerticalZoom); }

		private float curveRowHeight() {
			return BASE_CURVE_ROW_HEIGHT * timelineVerticalScale(timelineVerticalZoom);
		}

		private float curvePlotPadding() {
			return BASE_CURVE_PLOT_PADDING * timelineVerticalScale(timelineVerticalZoom);
		}

		private float curveMinPlotHeight() {
			return BASE_CURVE_MIN_PLOT_HEIGHT * timelineVerticalScale(timelineVerticalZoom);
		}

		private float trackColumnBoundary() { return horizontalScroll() + labelWidth(); }

		private float timelineBoundary() {
			return horizontalScroll() + timelineBoundaryX(labelWidth());
		}

		private float visibleStartTime() {
			return Math.max(0f, (horizontalScroll() - TIMELINE_MARKER_PADDING) / pixelsPerSecond());
		}

		private float visibleEndTime() {
			float viewportWidth = scroll == null || scroll.getWidth() <= 0f ? getWidth() : scroll.getWidth();
			return Math.min((getWidth() - timeOriginX()) / pixelsPerSecond(),
				Math.max(0f, (horizontalScroll() + viewportWidth - timeOriginX()) / pixelsPerSecond()));
		}

		private int trackAtY(float y) {
			CurveHierarchyHit hit = timelineHierarchyHitAtY(y);
			return hit == null || hit.track == null ? -1 : trackOf(hit.track);
		}

		private CurveHierarchyHit timelineHierarchyHitAtY(float y) {
			int row = (int) ((getHeight() - RULER_H - y) / rowHeight());
			if (row < 1) return null; // animation row is informational
			int cursor = 1; // animation row
			Array<CurveHierarchyGroup> groups = timelineHierarchyGroups();
			for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
				CurveHierarchyGroup group = groups.get(groupIndex);
				if (row == cursor) return new CurveHierarchyHit(group, null, -1);
				cursor++;
				if (curveGroupCollapsed(group)) continue;
				for (int trackIndex = 0; trackIndex < group.tracks.size; trackIndex++) {
					if (row == cursor) return new CurveHierarchyHit(group, group.tracks.get(trackIndex), -1);
					cursor++;
				}
			}
			return null;
		}

		private void selectTrackKeyframe(Track track, int frame, float time) {
			if (isCurveTrack(track)) {
				curveTargetTrack = track;
				curveTargetDocumentData = doc.data;
			}
			if (track.isEvent()) selectEventKeyframe(frame);
			else if (track.isDrawOrder()) selectDrawOrderKeyframe(time);
			else if (track.isDrawOrderFolder())
				selectDrawOrderFolderKeyframe((DrawOrderFolderTimeline)track.timeline, time);
			else if (track.isIk()) selectIkKeyframe(track.ik, time);
			else if (track.isTransform()) selectTransformKeyframe(track.transform, time);
			else if (track.isPath()) selectPathKeyframe(track.path, track.pathProp, time);
			else if (track.isPhysics())
				selectPhysicsKeyframe(track.physics, track.constraintIndex == -1, track.physicsProp, time);
			else if (track.isSlider()) selectSliderKeyframe(track.slider, track.sliderProp, time);
			else if (track.isSequence())
				selectSequenceKeyframe(track.slot, track.sequenceAttachment, track.sequenceName, time);
			else if (track.isDeform())
				selectDeformKeyframe(track.slot, track.deformAttachment, track.deformName, time);
			else if (track.isInherit()) selectInheritKeyframe(track.bone, time);
			else if (track.isAttachment()) selectAttachmentKeyframe(track.slot, time);
			else if (track.isColor()) selectColorKeyframe(track.slot, time);
			else if (track.isRgb()) selectRgbKeyframe(track.slot, time);
			else if (track.isAlpha()) selectAlphaKeyframe(track.slot, time);
			else if (track.isTwoColor()) selectTwoColorKeyframe(track.slot, time);
			else selectKeyframe(track.bone, track.prop, time);
		}

		private void selectGraphKeyframe(Track track, int frame, boolean additive) {
			if (track == null || track.timeline == null || frame < 0 || frame >= track.timeline.getFrameCount()) return;
			KeySelection clicked = keySelection(track, track.timeline, frame);
			if (clicked == null) {
				extraSelections.clear();
				selectTrackKeyframe(track, frame, animEditor.frameTime(track.timeline, frame));
				return;
			}
			if (additive) toggleSelection(clicked);
			else {
				Array<KeySelection> selected = new Array<>();
				selected.add(clicked);
				applySelectionSet(selected, clicked);
			}
		}

		private boolean graphKeySelected(Track track, int frame) {
			if (track == null || track.timeline == null || frame < 0 || frame >= track.timeline.getFrameCount())
				return false;
			Animation.Timeline primaryTimeline = selectedTimelineIgnoringVisibility();
			if (primaryTimeline == track.timeline && selectedFrame(primaryTimeline) == frame) return true;
			float time = animEditor.frameTime(track.timeline, frame);
			for (KeySelection selection : extraSelections)
				if (sameTrack(selection.track, track) && Float.compare(selection.time, time) == 0) return true;
			return false;
		}

		private boolean graphTrackSelected(Track track) {
			if (track == null || track.timeline == null) return false;
			for (int frame = 0; frame < track.timeline.getFrameCount(); frame++)
				if (graphKeySelected(track, frame)) return true;
			return false;
		}

		private boolean beginGraphKeyDrag(Track track, int frame) {
			if (track == null || track.timeline == null || frame < 0 || frame >= track.timeline.getFrameCount())
				return false;
			KeySelection clicked = keySelection(track, track.timeline, frame);
			if (clicked == null) return false;
			beginKeyDrag(clicked);
			return true;
		}

		private void updateGraphKeyDrag(float targetTime) {
			if (!syncPointerOperations()) return;
			if (!draggingKeys || dragSelections.isEmpty()) return;
			float requested = targetTime - dragAnchorTime;
			dragPreviewDelta = clampedKeyframeDelta(requested, earliestDragTime());
		}

		private void commitGraphKeyDrag() {
			if (draggingKeys) commitKeyDrag();
		}

		private float graphPreviewTime(Track track, int frame) {
			if (track == null || track.timeline == null || frame < 0 || frame >= track.timeline.getFrameCount())
				return 0f;
			float time = animEditor.frameTime(track.timeline, frame);
			if (!draggingKeys) return time;
			for (KeySelection selection : dragSelections)
				if (sameTrack(selection.track, track) && Float.compare(selection.time, time) == 0)
					return time + dragPreviewDelta;
			return time;
		}

		private int hitFrame(Animation.Timeline timeline, float x, float y, int trackIndex) {
			int hit = -1;
			float closest = Float.POSITIVE_INFINITY;
			float rowHeight = rowHeight();
			float rowCenter = getHeight() - RULER_H - (trackIndex * rowHeight + rowHeight / 2f);
			int groupStart = 0;
			int groupEnd = -1;
			for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
				float xDistance = Math.abs(x - xAtTime(animEditor.frameTime(timeline, frame)));
				if (xDistance >= 7f) continue;
				float yDistance = 0f;
				if (timeline instanceof EventTimeline events) {
					if (frame > groupEnd) {
						groupStart = frame;
						groupEnd = eventGroupEnd(events, frame);
					}
					yDistance = Math.abs(y - (rowCenter + eventMarkerOffset(frame, groupStart, groupEnd)));
					if (yDistance >= 8f) continue;
				}
				float distance = xDistance * xDistance + yDistance * yDistance;
				if (distance < closest || (Float.compare(distance, closest) == 0
					&& timeline instanceof EventTimeline && frame == selEventFrame)) {
					hit = frame;
					closest = distance;
				}
			}
			return hit;
		}

		private void scrubTo(float time) {
			if (doc.scrubTo(time) && onTimeChanged != null) onTimeChanged.run();
		}

		private boolean selectRulerKeyframesAt(float time) {
			long targetFrame = rulerFrameIndex(time, frameRate());
			Animation.Timeline preferredTimeline = selectedTimelineIgnoringVisibility();
			Array<KeySelection> matches = new Array<>();
			KeySelection preferred = null;
			boolean preferredTimelineMatched = false;
			for (Track track : tracks) {
				Animation.Timeline timeline = track.timeline;
				if (timeline == null) continue;
				for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
					float keyTime = animEditor.frameTime(timeline, frame);
					if (rulerFrameIndex(keyTime, frameRate()) != targetFrame) continue;
					KeySelection selection = keySelection(track, timeline, frame);
					if (containsSelection(matches, selection)) continue;
					matches.add(selection);
					if (preferred == null || !preferredTimelineMatched && timeline == preferredTimeline) {
						preferred = selection;
						preferredTimelineMatched = timeline == preferredTimeline;
					}
				}
			}
			if (matches.isEmpty()) {
				clearSelection();
				return false;
			}
			applySelectionSet(matches, preferred);
			return true;
		}

		private void onRulerTouchDown(float x) {
			float time = timeAtX(x);
			rulerScrubbing = true;
			scrubTo(time);
		}

		private boolean selectionModifierPressed() {
			return controlPressed() || Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
				|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
		}

		private int eventOccurrence(EventTimeline timeline, int frame) {
			if (timeline == null || frame < 0 || frame >= timeline.getFrameCount()) return -1;
			float time = animEditor.frameTime(timeline, frame);
			int occurrence = 0;
			for (int index = 0; index < frame; index++)
				if (Float.compare(animEditor.frameTime(timeline, index), time) == 0) occurrence++;
			return occurrence;
		}

		private int eventFrameFor(EventTimeline timeline, float time, int occurrence) {
			if (timeline == null || occurrence < 0) return -1;
			int found = 0;
			for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
				if (Float.compare(animEditor.frameTime(timeline, frame), time) != 0) continue;
				if (found++ == occurrence) return frame;
			}
			return -1;
		}

		private KeySelection keySelection(Track track, Animation.Timeline timeline, int frame) {
			if (track == null || timeline == null || frame < 0 || frame >= timeline.getFrameCount()) return null;
			return new KeySelection(track, animEditor.frameTime(timeline, frame),
				timeline instanceof EventTimeline events ? eventOccurrence(events, frame) : 0);
		}

		private boolean sameSelection(KeySelection first, KeySelection second) {
			return first != null && second != null && sameTrack(first.track, second.track)
				&& Float.compare(first.time, second.time) == 0
				&& (!first.track.isEvent() || first.eventOccurrence == second.eventOccurrence);
		}

		private boolean containsSelection(Array<KeySelection> selections, KeySelection target) {
			for (KeySelection selection : selections)
				if (sameSelection(selection, target)) return true;
			return false;
		}

		private Animation.Timeline currentTimelineFor(Animation animation, Track track) {
			return animation == null || track == null ? null : new RebuildContext(animation).timelineFor(track);
		}

		private ResolvedKey resolveSelection(KeySelection selection) {
			Animation animation = currentAnim();
			int trackIndex = selection == null ? -1 : trackOf(selection.track);
			if (animation == null || selection == null) return null;
			Track track = trackIndex < 0 ? selection.track : tracks.get(trackIndex);
			Animation.Timeline timeline = currentTimelineFor(animation, track);
			if (timeline == null) return null;
			int frame = timeline instanceof EventTimeline events
				? eventFrameFor(events, selection.time, selection.eventOccurrence)
				: animEditor.findFrame(timeline, selection.time);
			return frame < 0 ? null : new ResolvedKey(selection, track, timeline, frame);
		}

		private KeySelection primarySelection() {
			Animation.Timeline timeline = selectedTimelineIgnoringVisibility();
			int frame = selectedFrame(timeline);
			if (timeline == null || frame < 0) return null;
			Animation animation = currentAnim();
			for (Track track : tracks) {
				Animation.Timeline current = track.timeline;
				if (current != timeline && currentTimelineFor(animation, track) != timeline) continue;
				return keySelection(track, timeline, frame);
			}
			Track hidden = trackForTimeline(timeline);
			return hidden == null ? null : keySelection(hidden, timeline, frame);
		}

		private Array<KeySelection> allSelections() {
			Array<KeySelection> result = new Array<>();
			KeySelection primary = primarySelection();
			if (primary != null) result.add(primary);
			for (KeySelection selection : extraSelections) {
				if (resolveSelection(selection) != null && !containsSelection(result, selection)) result.add(selection);
			}
			return result;
		}

		private AnimationEditor.KeyframeBlock copySelectedKeyframeBlock() {
			Animation animation = currentAnim();
			Array<KeySelection> selected = allSelections();
			if (animation == null || selected.size < 2)
				throw new IllegalArgumentException("至少需要选择两个关键帧");
			Animation.Timeline[] timelines = new Animation.Timeline[selected.size];
			int[] frames = new int[selected.size];
			for (int index = 0; index < selected.size; index++) {
				ResolvedKey resolved = resolveSelection(selected.get(index));
				if (resolved == null) throw new IllegalStateException("关键帧选择在复制前已变化");
				timelines[index] = resolved.timeline;
				frames[index] = resolved.frame;
			}
			return animEditor.copyKeyframes(animation, timelines, frames);
		}

		private int selectionCount() {
			return allSelections().size;
		}

		private void pruneExtraSelections() {
			KeySelection primary = primarySelection();
			for (int index = extraSelections.size - 1; index >= 0; index--) {
				KeySelection selection = extraSelections.get(index);
				if (resolveSelection(selection) == null || sameSelection(selection, primary))
					extraSelections.removeIndex(index);
			}
		}

		private long pointerRevision;
		private Animation pointerAnimation;
		private Object pointerSkeleton;

		private void rememberPointerDocument() {
			pointerRevision = doc.revision;
			pointerAnimation = currentAnim();
			pointerSkeleton = doc.skeleton;
		}

		private boolean syncPointerOperations() {
			if ((draggingKeys || curveDragHandle != 0) && (pointerRevision != doc.revision
				|| pointerAnimation != currentAnim() || pointerSkeleton != doc.skeleton)) {
				cancelPointerOperations();
				return false;
			}
			return true;
		}

		private void cancelPointerOperations() {
			draggingKeys = false;
			dragSelections.clear();
			dragPreviewDelta = 0f;
			marqueeSelecting = false;
			marqueeDragging = false;
			curveMarqueeTimeline = null;
			panning = false;
			rulerScrubbing = false;
			resetCurveHandleDrag();
		}

		private void resetCurveHandleDrag() {
			curveDragHandle = 0;
			curvePreviewSegment = -1;
			curveDragTimeline = null;
			curveDragBeforeSnapshot = null;
		}

		private void applySelectionSet(Array<KeySelection> selections, KeySelection preferredPrimary) {
			Array<KeySelection> valid = new Array<>();
			for (KeySelection selection : selections)
				if (resolveSelection(selection) != null && !containsSelection(valid, selection)) valid.add(selection);
			if (valid.isEmpty()) {
				clearSelection();
				return;
			}
			KeySelection primary = preferredPrimary;
			if (primary == null || !containsSelection(valid, primary)) primary = valid.peek();
			ResolvedKey resolvedPrimary = resolveSelection(primary);
			if (resolvedPrimary == null) {
				clearSelection();
				return;
			}
			extraSelections.clear();
			selectTrackKeyframe(resolvedPrimary.track, resolvedPrimary.frame,
				animEditor.frameTime(resolvedPrimary.timeline, resolvedPrimary.frame));
			for (KeySelection selection : valid)
				if (!sameSelection(selection, primary)) extraSelections.add(selection);
			refreshKeyframeInspector();
		}

		private void toggleSelection(KeySelection clicked) {
			Array<KeySelection> selected = allSelections();
			for (int index = selected.size - 1; index >= 0; index--) {
				if (!sameSelection(selected.get(index), clicked)) continue;
				selected.removeIndex(index);
				applySelectionSet(selected, selected.isEmpty() ? null : selected.peek());
				return;
			}
			selected.add(clicked);
			applySelectionSet(selected, clicked);
		}

		private void beginMarquee(float x, float y, boolean additive) {
			if (!additive) clearSelection();
			marqueeSelecting = true;
			marqueeDragging = false;
			marqueeAdditive = additive;
			marqueeStartX = marqueeCurrentX = Math.max(timelineBoundary(), x);
			marqueeStartY = marqueeCurrentY = y;
		}

		private void finishMarquee() {
			boolean dragged = marqueeDragging;
			boolean additive = marqueeAdditive;
			marqueeSelecting = false;
			marqueeDragging = false;
			if (!dragged) return;
			Array<KeySelection> selected = additive ? allSelections() : new Array<>();
			KeySelection preferred = null;
			for (int trackIndex = 0; trackIndex < tracks.size; trackIndex++) {
				Track track = tracks.get(trackIndex);
				Animation.Timeline timeline = track.timeline;
				if (timeline == null) continue;
				float rowHeight = rowHeight();
				int visibleRow = visibleTimelineRow(track);
				if (visibleRow < 0) continue;
				float rowCenter = getHeight() - RULER_H - (visibleRow * rowHeight + rowHeight / 2f);
				int groupStart = 0;
				int groupEnd = -1;
				for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
					float markerY = rowCenter;
					if (timeline instanceof EventTimeline events) {
						if (frame > groupEnd) {
							groupStart = frame;
							groupEnd = eventGroupEnd(events, frame);
						}
						markerY += eventMarkerOffset(frame, groupStart, groupEnd);
					}
					float time = animEditor.frameTime(timeline, frame);
					if (!pointInsideSelection(xAtTime(time), markerY, marqueeStartX, marqueeStartY,
						marqueeCurrentX, marqueeCurrentY)) continue;
					KeySelection key = keySelection(track, timeline, frame);
					if (!containsSelection(selected, key)) selected.add(key);
					preferred = key;
				}
			}
			if (!selected.isEmpty()) applySelectionSet(selected, preferred);
			else if (!additive) clearSelection();
		}

		private void beginPan(InputEvent event) {
			cancelPointerOperations();
			lastBlankClickTrack = -1;
			panning = true;
			panStartStageX = event.getStageX();
			panStartStageY = event.getStageY();
			panStartScrollX = scroll == null ? 0f : scroll.getScrollX();
			panStartScrollY = scroll == null ? 0f : scroll.getScrollY();
		}

		private void beginKeyDrag(KeySelection clicked) {
			rememberPointerDocument();
			dragSelections.clear();
			dragSelections.addAll(allSelections());
			if (!containsSelection(dragSelections, clicked)) dragSelections.add(clicked);
			dragAnchorTime = clicked.time;
			dragPreviewDelta = 0f;
			draggingKeys = true;
		}

		private float earliestDragTime() {
			float earliest = Float.POSITIVE_INFINITY;
			for (KeySelection selection : dragSelections) earliest = Math.min(earliest, selection.time);
			return earliest == Float.POSITIVE_INFINITY ? 0f : earliest;
		}

		private AnimationEditor.TimelineSnapshot[] snapshotTracks(Animation animation, Array<Track> sourceTracks) {
			if (animation == null || sourceTracks == null || sourceTracks.isEmpty()) return null;
			Array<Track> unique = new Array<>();
			for (Track track : sourceTracks) {
				boolean found = false;
				for (Track existing : unique) {
					if (sameTrack(existing, track)) { found = true; break; }
				}
				if (!found) unique.add(track);
			}
			AnimationEditor.TimelineSnapshot[] snapshots = new AnimationEditor.TimelineSnapshot[unique.size];
			for (int index = 0; index < unique.size; index++) snapshots[index] = snapshotTrack(animation, unique.get(index));
			return snapshots;
		}

		private void commitKeyDrag() {
			if (!syncPointerOperations()) return;
			float delta = dragPreviewDelta;
			Array<KeySelection> requested = new Array<>(dragSelections);
			draggingKeys = false;
			dragSelections.clear();
			dragPreviewDelta = 0f;
			if (!hasKeyframeTimeDelta(delta) || requested.isEmpty()) return;
			Animation animation = currentAnim();
			if (animation == null) return;

			ArrayList<ResolvedKey> resolved = new ArrayList<>();
			Array<Track> affectedTracks = new Array<>();
			KeySelection oldPrimary = primarySelection();
			for (KeySelection selection : requested) {
				ResolvedKey key = resolveSelection(selection);
				if (key == null) continue;
				resolved.add(key);
				affectedTracks.add(key.track);
			}
			if (resolved.isEmpty()) return;
			resolved.sort((first, second) -> {
				int trackCompare = Integer.compare(trackOf(first.track), trackOf(second.track));
				if (trackCompare != 0) return trackCompare;
				return delta > 0f ? Integer.compare(second.frame, first.frame)
					: Integer.compare(first.frame, second.frame);
			});
			AnimationEditor.TimelineSnapshot[] before = snapshotTracks(animation, affectedTracks);
			Array<KeySelection> moved = new Array<>();
			ArrayList<ResolvedKey> movedEvents = new ArrayList<>();
			KeySelection newPrimary = null;
			try {
				for (ResolvedKey original : resolved) {
					Animation.Timeline timeline = currentTimelineFor(animation, original.track);
					if (timeline == null || original.frame < 0 || original.frame >= timeline.getFrameCount())
						throw new IllegalStateException("关键帧在移动前已变化");
					float newTime = snapTime(original.selection.time + delta);
					int newFrame = moveKeyframe(animation, original.track, timeline, original.frame, newTime);
					if (original.track.isEvent()) {
						movedEvents.add(original);
						continue;
					}
					Animation.Timeline movedTimeline = currentTimelineFor(animation, original.track);
					KeySelection movedKey = keySelection(original.track, movedTimeline, newFrame);
					if (movedKey != null) moved.add(movedKey);
					if (sameSelection(original.selection, oldPrimary)) newPrimary = movedKey;
				}
				movedEvents.sort((first, second) -> {
					int trackCompare = Integer.compare(trackOf(first.track), trackOf(second.track));
					if (trackCompare != 0) return trackCompare;
					return delta > 0f ? Integer.compare(first.frame, second.frame)
						: Integer.compare(second.frame, first.frame);
				});
				IdentityHashMap<EventTimeline, boolean[]> matchedEventFrames = new IdentityHashMap<>();
				for (ResolvedKey original : movedEvents) {
					Event source = animEditor.eventAt((EventTimeline)original.timeline, original.frame);
					EventTimeline timeline = (EventTimeline)currentTimelineFor(animation, original.track);
					float newTime = snapTime(original.selection.time + delta);
					boolean[] matched = matchedEventFrames.computeIfAbsent(timeline,
						ignored -> new boolean[timeline.getFrameCount()]);
					int frame = findMovedEventFrame(timeline, source, newTime, matched, delta > 0f);
					if (frame < 0) throw new IllegalStateException("无法恢复移动后的事件选择");
					matched[frame] = true;
					KeySelection movedKey = keySelection(original.track, timeline, frame);
					moved.add(movedKey);
					if (sameSelection(original.selection, oldPrimary)) newPrimary = movedKey;
				}
				if (moved.isEmpty()) throw new IllegalStateException("移动后没有可选关键帧");
				applySelectionSet(moved, newPrimary == null && !moved.isEmpty() ? moved.peek() : newPrimary);
				scrubTo((newPrimary == null ? moved.peek() : newPrimary).time);
				recordTimelineChanges(moved.size > 1 ? "移动多个关键帧" : "移动关键帧", before);
				notifyChanged();
				showInfo(moved.size > 1 ? "已移动 " + moved.size + " 个关键帧" : "已移动关键帧");
			} catch (RuntimeException error) {
				if (before != null) restoreTimelines(before);
				notifyChanged();
				showStatus("无法移动关键帧：" + error.getMessage());
			}
		}

		private int findMovedEventFrame(EventTimeline timeline, Event source, float time, boolean[] matched,
			boolean forward) {
			int frame = forward ? 0 : timeline.getFrameCount() - 1;
			int end = forward ? timeline.getFrameCount() : -1;
			int step = forward ? 1 : -1;
			for (; frame != end; frame += step) {
				if (matched[frame] || Float.compare(animEditor.frameTime(timeline, frame), time) != 0) continue;
				if (sameEventPayload(source, animEditor.eventAt(timeline, frame))) return frame;
			}
			return -1;
		}

		private boolean sameEventPayload(Event first, Event second) {
			return first.getData() == second.getData() && first.getInt() == second.getInt()
				&& Float.compare(first.getFloat(), second.getFloat()) == 0
				&& Objects.equals(first.getString(), second.getString())
				&& Float.compare(first.getVolume(), second.getVolume()) == 0
				&& Float.compare(first.getBalance(), second.getBalance()) == 0;
		}

		private int moveKeyframe(Animation animation, Track track, Animation.Timeline timeline, int frame,
			float newTime) {
			float oldTime = animEditor.frameTime(timeline, frame);
			if (track.isEvent()) {
				Event event = animEditor.eventAt((EventTimeline)timeline, frame);
				return animEditor.editEventKeyframe(animation, frame, newTime, event.getData(), event.getInt(),
					event.getFloat(), event.getString(), event.getVolume(), event.getBalance());
			}
			if (track.isDrawOrderFolder())
				return animEditor.editDrawOrderFolderKeyframe(animation,
					(DrawOrderFolderTimeline)timeline, oldTime, newTime,
					animEditor.drawOrderFolderAt((DrawOrderFolderTimeline)timeline, frame));
			if (track.isDrawOrder())
				return animEditor.editDrawOrderKeyframe(animation, oldTime, newTime,
					animEditor.drawOrderAt((DrawOrderTimeline)timeline, frame));
			if (track.isIk())
				return animEditor.editIkKeyframe(animation, track.constraintIndex, oldTime, newTime,
					animEditor.ikAt((IkConstraintTimeline)timeline, frame), isStepped(timeline, frame));
			if (track.isTransform())
				return animEditor.editTransformKeyframe(animation, track.constraintIndex, oldTime, newTime,
					animEditor.transformAt((TransformConstraintTimeline)timeline, frame), isStepped(timeline, frame));
			if (track.isPath()) {
				if (track.pathProp == AnimationEditor.PathProp.MIX)
					return animEditor.editPathMixKeyframe(animation, track.constraintIndex, oldTime, newTime,
						animEditor.pathMixAt((PathConstraintMixTimeline)timeline, frame), isStepped(timeline, frame));
				return animEditor.editPathValueKeyframe(animation, track.constraintIndex, track.pathProp,
					oldTime, newTime, animEditor.pathValueAt(timeline, frame), isStepped(timeline, frame));
			}
			if (track.isPhysics()) {
				if (track.physicsProp == AnimationEditor.PhysicsProp.RESET)
					return animEditor.editPhysicsResetKeyframe(animation, track.constraintIndex, oldTime, newTime);
				return animEditor.editPhysicsValueKeyframe(animation, track.constraintIndex, track.physicsProp,
					oldTime, newTime, animEditor.physicsValueAt(timeline, frame), isStepped(timeline, frame));
			}
			if (track.isSlider())
				return animEditor.editSliderKeyframe(animation, track.constraintIndex, track.sliderProp, oldTime,
					newTime, animEditor.sliderValueAt(timeline, frame), isStepped(timeline, frame));
			if (track.isSequence())
				return animEditor.editSequenceKeyframe(animation, track.slot.getIndex(), track.sequenceAttachment,
					oldTime, newTime, animEditor.sequenceAt((SequenceTimeline)timeline, frame));
			if (track.isDeform())
				return animEditor.editDeformKeyframe(animation, track.slot.getIndex(), track.deformAttachment,
					oldTime, newTime, animEditor.deformOffsetsAt((DeformTimeline)timeline, frame),
					isStepped(timeline, frame));
			if (track.isInherit())
				return animEditor.editInheritKeyframe(animation, track.bone.getData().getIndex(), oldTime,
					newTime, animEditor.inheritAt((InheritTimeline)timeline, frame));
			if (track.isAttachment())
				return animEditor.editAttachmentKeyframe(animation, track.slot.getIndex(), oldTime, newTime,
					animEditor.attachmentName((AttachmentTimeline)timeline, frame));
			if (track.isColor()) {
				RGBATimeline color = (RGBATimeline)timeline;
				return animEditor.editColorKeyframe(animation, track.slot.getIndex(), oldTime, newTime,
					animEditor.colorValue(color, frame, 0), animEditor.colorValue(color, frame, 1),
					animEditor.colorValue(color, frame, 2), animEditor.colorValue(color, frame, 3),
					isStepped(timeline, frame));
			}
			if (track.isRgb()) {
				RGBTimeline color = (RGBTimeline)timeline;
				return animEditor.editRgbKeyframe(animation, track.slot.getIndex(), oldTime, newTime,
					animEditor.rgbValue(color, frame, 0), animEditor.rgbValue(color, frame, 1),
					animEditor.rgbValue(color, frame, 2), isStepped(timeline, frame));
			}
			if (track.isAlpha())
				return animEditor.editAlphaKeyframe(animation, track.slot.getIndex(), oldTime, newTime,
					animEditor.alphaValue((AlphaTimeline)timeline, frame), isStepped(timeline, frame));
			if (track.isTwoColor())
				return animEditor.editTwoColorKeyframe(animation, track.slot.getIndex(), oldTime, newTime,
					animEditor.twoColorValue(timeline, frame, 0), animEditor.twoColorValue(timeline, frame, 1),
					animEditor.twoColorValue(timeline, frame, 2), animEditor.twoColorValue(timeline, frame, 3),
					animEditor.twoColorValue(timeline, frame, 4), animEditor.twoColorValue(timeline, frame, 5),
					animEditor.twoColorValue(timeline, frame, 6), isStepped(timeline, frame));
			return animEditor.editKeyframe(animation, track.prop, track.bone.getData().getIndex(), oldTime,
				newTime, animEditor.frameValue(timeline, frame, 0), animEditor.frameValue(timeline, frame, 1),
				isStepped(timeline, frame));
		}

		private boolean isStepped(Animation.Timeline timeline, int frame) {
			return animEditor.curveType(timeline, frame) == Animation.CurveTimeline.STEPPED;
		}

		private void removeKeyframe(Animation animation, Track track, Animation.Timeline timeline, int frame) {
			float time = animEditor.frameTime(timeline, frame);
			lastBlankClickTrack = -1;
			if (track.isEvent()) animEditor.removeEventKeyframe(animation, frame);
			else if (track.isDrawOrder()) animEditor.removeDrawOrderKeyframe(animation, time);
			else if (track.isDrawOrderFolder())
				animEditor.removeDrawOrderFolderKeyframe(animation, (DrawOrderFolderTimeline)timeline, time);
			else if (track.isIk()) animEditor.removeIkKeyframe(animation, track.constraintIndex, time);
			else if (track.isTransform()) animEditor.removeTransformKeyframe(animation, track.constraintIndex, time);
			else if (track.isPath()) {
				if (track.pathProp == AnimationEditor.PathProp.MIX)
					animEditor.removePathMixKeyframe(animation, track.constraintIndex, time);
				else animEditor.removePathValueKeyframe(animation, track.constraintIndex, track.pathProp, time);
			} else if (track.isPhysics()) {
				if (track.physicsProp == AnimationEditor.PhysicsProp.RESET)
					animEditor.removePhysicsResetKeyframe(animation, track.constraintIndex, time);
				else animEditor.removePhysicsValueKeyframe(animation, track.constraintIndex, track.physicsProp, time);
			} else if (track.isSlider())
				animEditor.removeSliderKeyframe(animation, track.constraintIndex, track.sliderProp, time);
			else if (track.isSequence())
				animEditor.removeSequenceKeyframe(animation, track.slot.getIndex(), track.sequenceAttachment, time);
			else if (track.isDeform())
				animEditor.removeDeformKeyframe(animation, track.slot.getIndex(), track.deformAttachment, time);
			else if (track.isInherit())
				animEditor.removeInheritKeyframe(animation, track.bone.getData().getIndex(), time);
			else if (track.isAttachment())
				animEditor.removeAttachmentKeyframe(animation, track.slot.getIndex(), time);
			else if (track.isColor()) animEditor.removeColorKeyframe(animation, track.slot.getIndex(), time);
			else if (track.isRgb()) animEditor.removeRgbKeyframe(animation, track.slot.getIndex(), time);
			else if (track.isAlpha()) animEditor.removeAlphaKeyframe(animation, track.slot.getIndex(), time);
			else if (track.isTwoColor()) animEditor.removeTwoColorKeyframe(animation, track.slot.getIndex(), time);
			else animEditor.removeKeyframe(animation, track.prop, track.bone.getData().getIndex(), time);
		}

		private boolean deleteSelectedKeyframes() {
			Array<KeySelection> selected = allSelections();
			if (selected.isEmpty()) return false;
			Animation animation = currentAnim();
			if (animation == null) return false;
			ArrayList<ResolvedKey> resolved = new ArrayList<>();
			Array<Track> affectedTracks = new Array<>();
			for (KeySelection selection : selected) {
				ResolvedKey key = resolveSelection(selection);
				if (key == null) continue;
				resolved.add(key);
				affectedTracks.add(key.track);
			}
			if (resolved.isEmpty()) return false;
			resolved.sort(Comparator.comparingInt((ResolvedKey key) -> trackOf(key.track))
				.thenComparing((first, second) -> Integer.compare(second.frame, first.frame)));
			AnimationEditor.TimelineSnapshot[] before = snapshotTracks(animation, affectedTracks);
			try {
				for (ResolvedKey key : resolved) {
					Animation.Timeline timeline = currentTimelineFor(animation, key.track);
					if (timeline == null || key.frame < 0 || key.frame >= timeline.getFrameCount())
						throw new IllegalStateException("关键帧在删除前已变化");
					removeKeyframe(animation, key.track, timeline, key.frame);
				}
				clearSelection();
				recordTimelineChanges(resolved.size() > 1 ? "删除多个关键帧" : "删除关键帧", before);
				notifyChanged();
				showInfo(resolved.size() > 1 ? "已删除 " + resolved.size() + " 个关键帧" : "已删除关键帧");
			} catch (RuntimeException error) {
				if (before != null) restoreTimelines(before);
				notifyChanged();
				showStatus("无法删除关键帧：" + error.getMessage());
			}
			return true;
		}

		private void onTouchDown(InputEvent event, float x, float y, int button) {
			cancelPointerOperations();
			if (button == Input.Buttons.RIGHT) {
				beginPan(event);
				return;
			}
			if (curveView) {
				onCurveTouchDown(x, y, button);
				return;
			}
			Animation animation = currentAnim();
			if (animation == null) return;
			if (rulerScrubStarts(button, x, y, timelineBoundary(), rulerBottom(), RULER_H)) {
				onRulerTouchDown(x);
				return;
			}
			CurveHierarchyHit hierarchyHit = timelineHierarchyHitAtY(y);
			if (hierarchyHit == null) return;
			if (hierarchyHit.track == null) {
				if (x < timelineBoundary()) toggleCurveGroup(hierarchyHit.group);
				return;
			}
			Track track = hierarchyHit.track;
			int trackIndex = trackOf(track);
			int visibleRow = visibleTimelineRow(track);
			if (trackIndex < 0 || visibleRow < 0 || x < timelineBoundary()) return;
			Animation.Timeline timeline = track.timeline;
			if (button == Input.Buttons.MIDDLE) {
				if (timeline == null || track.isDiscrete()) return;
				int frame = hitFrame(timeline, x, y, visibleRow);
				if (frame < 0) return;
				AnimationEditor.TimelineSnapshot before = snapshotTrack(animation, track);
				selectTrackKeyframe(track, frame, animEditor.frameTime(timeline, frame));
				animEditor.setCurve(timeline, frame, !isStepped(timeline, frame));
				recordTimelineChange("切换关键帧曲线", before);
				notifyChanged();
				return;
			}
			if (button != Input.Buttons.LEFT) return;
			if (!track.isEvent() && !track.isAnyDrawOrder() && !track.isIk() && !track.isTransform()
				&& !track.isPath() && !track.isPhysics() && !track.isSlider()) {
				doc.setSelectedBone(track.bone);
				if (onSelectionChanged != null) onSelectionChanged.run();
			}
			int frame = timeline == null ? -1 : hitFrame(timeline, x, y, visibleRow);
			if (frame < 0) {
				boolean modifier = selectionModifierPressed();
				float blankTime = timeAtX(x);
				long now = System.nanoTime();
				if (!modifier && trackIndex == lastBlankClickTrack
					&& Math.abs(xAtTime(blankTime) - xAtTime(lastBlankClickTime)) < 7f
					&& now - lastBlankClickNanos < 350_000_000L) {
					lastBlankClickTrack = -1;
					onTouchDown(x, y, Input.Buttons.LEFT);
					return;
				}
				lastBlankClickNanos = now;
				lastBlankClickTrack = trackIndex;
				lastBlankClickTime = blankTime;
				beginMarquee(x, y, modifier);
				return;
			}

			float time = animEditor.frameTime(timeline, frame);
			KeySelection clicked = keySelection(track, timeline, frame);
			boolean modifier = selectionModifierPressed();
			if (modifier) {
				toggleSelection(clicked);
				lastClickTrack = lastClickFrame = -1;
				return;
			}
			Array<KeySelection> selected = allSelections();
			if (containsSelection(selected, clicked)) applySelectionSet(selected, clicked);
			else {
				selected.clear();
				selected.add(clicked);
				applySelectionSet(selected, clicked);
			}
			scrubTo(time);

			long now = System.nanoTime();
			if (!track.isDiscrete() && trackIndex == lastClickTrack && frame == lastClickFrame
				&& now - lastClickNanos < 350_000_000L) {
				AnimationEditor.TimelineSnapshot before = snapshotTrack(animation, track);
				long revision = doc.revision;
				animEditor.setCurve(timeline, frame, !isStepped(timeline, frame));
				lastClickTrack = lastClickFrame = -1;
				recordTimelineChange("切换关键帧曲线", before);
				if (doc.revision != revision) notifyChanged();
				else showInfo("末帧没有可切换的插值曲线");
				return;
			}
			lastClickNanos = now;
			lastClickTrack = trackIndex;
			lastClickFrame = frame;
			beginKeyDrag(clicked);
		}

		private void onTouchDragged(InputEvent event, float x, float y) {
			if (!syncPointerOperations()) return;
			if (panning) {
				if (scroll == null) return;
				scroll.setScrollX(pannedScrollX(panStartScrollX, event.getStageX() - panStartStageX));
				scroll.setScrollY(pannedScrollY(panStartScrollY, event.getStageY() - panStartStageY));
				scroll.updateVisualScroll();
				return;
			}
			if (rulerScrubbing) {
				scrubTo(timeAtX(x));
				return;
			}
			if (curveView) {
				if (curveDragHandle != 0) updateCurveHandleDrag(x, y);
				else if (marqueeSelecting) {
					marqueeCurrentX = Math.max(timelineBoundary(), x);
					marqueeCurrentY = Math.max(curvePlotBottom(), Math.min(curvePlotTop(), y));
					if (Math.abs(marqueeCurrentX - marqueeStartX) >= MARQUEE_DRAG_THRESHOLD
						|| Math.abs(marqueeCurrentY - marqueeStartY) >= MARQUEE_DRAG_THRESHOLD)
						marqueeDragging = true;
				}
				return;
			}
			if (marqueeSelecting) {
				marqueeCurrentX = Math.max(timelineBoundary(), x);
				marqueeCurrentY = Math.max(0f, Math.min(rulerBottom(), y));
				if (Math.abs(marqueeCurrentX - marqueeStartX) >= MARQUEE_DRAG_THRESHOLD
					|| Math.abs(marqueeCurrentY - marqueeStartY) >= MARQUEE_DRAG_THRESHOLD)
					marqueeDragging = true;
				if (marqueeDragging) lastBlankClickTrack = -1;
				return;
			}
			if (!draggingKeys || dragSelections.isEmpty()) return;
			float requested = timeAtX(x) - dragAnchorTime;
			dragPreviewDelta = clampedKeyframeDelta(requested, earliestDragTime());
		}

		private void onTouchUp(InputEvent event, float x, float y, int button) {
			if (!syncPointerOperations()) return;
			if (panning) {
				panning = false;
				return;
			}
			if (rulerScrubCommitsSelection(rulerScrubbing, button)) {
				float time = timeAtX(x);
				rulerScrubbing = false;
				selectRulerKeyframesAt(time);
				scrubTo(time);
				return;
			}
			if (rulerScrubbing) {
				rulerScrubbing = false;
				return;
			}
			if (curveView && curveDragHandle != 0) {
				commitCurveHandleDrag();
				return;
			}
			if (curveView && marqueeSelecting) {
				marqueeCurrentX = Math.max(timelineBoundary(), x);
				marqueeCurrentY = Math.max(curvePlotBottom(), Math.min(curvePlotTop(), y));
				finishCurveMarquee();
				return;
			}
			if (marqueeSelecting) {
				marqueeCurrentX = Math.max(timelineBoundary(), x);
				marqueeCurrentY = Math.max(0f, Math.min(rulerBottom(), y));
				finishMarquee();
				return;
			}
			if (draggingKeys) commitKeyDrag();
		}

		private void onCurveTouchDown(float x, float y, int button) {
			Animation animation = currentAnim();
			if (animation == null || button != Input.Buttons.LEFT) return;
			if (rulerScrubStarts(button, x, y, timelineBoundary(), rulerBottom(), RULER_H)) {
				onRulerTouchDown(x);
				return;
			}
			if (x < trackColumnBoundary()) {
				CurveHierarchyHit rowHit = curveChannelAtY(y);
				boolean modifier = selectionModifierPressed();
				if (rowHit == null) {
					// Clicking unused sidebar space is the Graph equivalent of clearing
					// the current curve filter.
					if (!modifier) {
						clearCurveVisibility();
						clearSelection();
						refreshCurveVisibilityCount();
						invalidate();
					}
					return;
				}
				if (rowHit.group != null && rowHit.track == null
					&& curveHierarchyDisclosureHit(x)) {
					toggleCurveGroup(rowHit.group);
					return;
				}
				if (rowHit.group != null && rowHit.track == null) {
					setCurveVisibility(rowHit.group.tracks, modifier);
					Track target = rowHit.group.tracks.isEmpty() ? null : rowHit.group.tracks.first();
					if (target == null) return;
					selectBoneForCurveTrack(target);
					curveTargetTrack = target;
					curveTargetPinned = true;
					curveTargetDocumentData = doc.data;
					curveSelectedChannel = 0;
					Animation.Timeline selectedTimeline = target.timeline;
					int frame = selectedFrame(selectedTimeline);
					if (frame < 0 && selectedTimeline.getFrameCount() > 0) {
						frame = nearestCurveFrame(selectedTimeline, doc.getCurrentTime());
					}
					if (frame >= 0) {
						KeySelection key = keySelection(target, selectedTimeline, frame);
						if (selectionModifierPressed()) toggleSelection(key);
						else {
							Array<KeySelection> selected = new Array<>();
							selected.add(key);
							applySelectionSet(selected, key);
						}
						scrubTo(animEditor.frameTime(selectedTimeline, frame));
					}
					invalidate();
				} else if (rowHit.track != null) {
					Track target = rowHit.track;
					setCurveVisibility(target, modifier);
					selectBoneForCurveTrack(target);
					curveTargetTrack = target;
					curveTargetPinned = true;
					curveTargetDocumentData = doc.data;
					if (rowHit.channel >= 0) curveSelectedChannel = rowHit.channel;
					Animation.Timeline selectedTimeline = target.timeline;
					int frame = selectedFrame(selectedTimeline);
					if (frame < 0 && selectedTimeline.getFrameCount() > 0)
						frame = nearestCurveFrame(selectedTimeline, doc.getCurrentTime());
					if (frame >= 0) {
						KeySelection key = keySelection(target, selectedTimeline, frame);
						if (modifier) toggleSelection(key);
						else {
							Array<KeySelection> selected = new Array<>();
							selected.add(key);
							applySelectionSet(selected, key);
						}
						scrubTo(animEditor.frameTime(selectedTimeline, frame));
					}
					invalidate();
				}
				return;
			}
			Animation.Timeline timeline = curveTimeline();
			Array<Track> display = curveDisplayTracks();
			if (timeline == null || display.isEmpty()) {
				if (!selectionModifierPressed()) {
					clearCurveVisibility();
					refreshCurveVisibilityCount();
					invalidate();
				}
				return;
			}
			if (x < timelineBoundary()) return;
			curveSelectedChannel = Math.min(curveSelectedChannel,
				Math.max(0, AnimationEditor.curveChannelCount(timeline) - 1));
			int handle = nearestCurveHandle(x, y, timeline);
			if (handle != 0) {
				beginCurveHandleDrag(handle, timeline);
				return;
			}
			int[] key = nearestCurveKey(x, y, display);
			if (key != null) {
				Track target = display.get(key[0]);
				setCurveVisibility(target, selectionModifierPressed());
				selectBoneForCurveTrack(target);
				curveTargetTrack = target;
				curveTargetPinned = true;
				curveTargetDocumentData = doc.data;
				selectCurveKey(target.timeline, key[1], key[2]);
				return;
			}
			CurveLineHit line = nearestCurveLine(x, y, display);
			if (line != null) {
				setCurveVisibility(line.track, selectionModifierPressed());
				selectBoneForCurveTrack(line.track);
				curveTargetTrack = line.track;
				curveTargetPinned = true;
				curveTargetDocumentData = doc.data;
				curveSelectedChannel = line.channel;
				invalidate();
				return;
			}
			beginCurveMarquee(x, y, selectionModifierPressed());
		}

		private final class CurveHierarchyHit {
			final CurveHierarchyGroup group;
			final Track track;
			final int channel;

			CurveHierarchyHit(CurveHierarchyGroup group, Track track, int channel) {
				this.group = group;
				this.track = track;
				this.channel = channel;
			}
		}

		private final class CurveLineHit {
			final Track track;
			final int channel;

			CurveLineHit(Track track, int channel) {
				this.track = track;
				this.channel = channel;
			}
		}

		private void toggleCurveGroup(CurveHierarchyGroup group) {
			if (group == null) return;
			if (!collapsedHierarchyGroups.add(group.key)) collapsedHierarchyGroups.remove(group.key);
			refreshCurveHierarchyLayout();
			invalidate();
		}

		private boolean curveHierarchyDisclosureHit(float x) {
			float fixedX = drawOrigin.x + horizontalScroll();
			return x >= fixedX + 2f && x <= fixedX + 20f;
		}

		private void selectBoneForCurveTrack(Track track) {
			Bone bone = track == null ? null : track.bone;
			if (bone == null) return;
			boolean changed = doc.getSelectedBone() != bone
				|| doc.getSelectionKind() != SkeletonDocument.SelectionKind.BONE;
			doc.setSelectedBone(bone);
			if (changed && onSelectionChanged != null) onSelectionChanged.run();
		}

		private void selectCurveKey(Animation.Timeline timeline, int frame, int channel) {
			Track track = curveTrack(timeline);
			if (track == null || frame < 0 || frame >= timeline.getFrameCount()) return;
			curveSelectedChannel = Math.min(Math.max(0, channel),
				Math.max(0, AnimationEditor.curveChannelCount(timeline) - 1));
			float time = animEditor.frameTime(timeline, frame);
			KeySelection clicked = keySelection(track, timeline, frame);
			if (selectionModifierPressed()) toggleSelection(clicked);
			else {
				Array<KeySelection> selected = new Array<>();
				selected.add(clicked);
				applySelectionSet(selected, clicked);
			}
			scrubTo(time);
		}

		private void beginCurveMarquee(float x, float y, boolean additive) {
			if (curveBlankPressClearsSelection(additive)) {
				// Keep the hierarchy filter intact. In Spine, clicking an empty graph
				// area clears key selection but does not restore every curve.
				clearSelection();
			}
			curveMarqueeTimeline = curveTimeline();
			marqueeSelecting = true;
			marqueeDragging = false;
			marqueeAdditive = additive;
			marqueeStartX = marqueeCurrentX = Math.max(timelineBoundary(), x);
			marqueeStartY = marqueeCurrentY = Math.max(curvePlotBottom(), Math.min(curvePlotTop(), y));
		}

		private void finishCurveMarquee() {
			boolean dragged = marqueeDragging;
			boolean additive = marqueeAdditive;
			marqueeSelecting = false;
			marqueeDragging = false;
			curveMarqueeTimeline = null;
			if (!dragged) return;
			Array<Track> display = curveDisplayTracks();
			if (display.isEmpty()) return;
			float[] bounds = curveDisplayValueBounds(display);
			Array<KeySelection> selected = additive ? allSelections() : new Array<>();
			KeySelection preferred = null;
			for (Track track : display) {
				Animation.Timeline timeline = track.timeline;
				for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
					float time = animEditor.frameTime(timeline, frame);
					for (int channel = 0; channel < AnimationEditor.curveChannelCount(timeline); channel++) {
						float value = animEditor.curveFrameValue(timeline, frame, channel);
						if (!pointInsideSelection(xAtTime(time), curveY(value, bounds),
							marqueeStartX, marqueeStartY, marqueeCurrentX, marqueeCurrentY)) continue;
						KeySelection key = keySelection(track, timeline, frame);
						if (!containsSelection(selected, key)) selected.add(key);
						preferred = key;
						break;
					}
				}
			}
			if (!selected.isEmpty()) applySelectionSet(selected, preferred);
			else if (!additive) clearSelection();
		}

		private CurveHierarchyHit curveChannelAtY(float y) {
			int row = Math.round((curveHierarchyTop() - y) / curveRowHeight());
			if (row <= 0) return null;
			int cursor = 1;
			Array<CurveHierarchyGroup> groups = curveHierarchyGroups();
			for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
				CurveHierarchyGroup group = groups.get(groupIndex);
				if (row == cursor) return new CurveHierarchyHit(group, null, -1);
				cursor++;
				if (curveGroupCollapsed(group)) continue;
				for (int trackIndex = 0; trackIndex < group.tracks.size; trackIndex++) {
					Track track = group.tracks.get(trackIndex);
					if (row == cursor) return new CurveHierarchyHit(group, track, -1);
					cursor++;
					int channels = AnimationEditor.curveChannelCount(track.timeline);
					if (row >= cursor && row < cursor + channels)
						return new CurveHierarchyHit(group, track, row - cursor);
					cursor += channels;
				}
			}
			return null;
		}

		private int[] nearestCurveKey(float x, float y, Array<Track> display) {
			float[] bounds = curveDisplayValueBounds(display);
			float best = CURVE_HIT_RADIUS * CURVE_HIT_RADIUS;
			int[] nearest = null;
			for (int trackIndex = 0; trackIndex < display.size; trackIndex++) {
				Animation.Timeline timeline = display.get(trackIndex).timeline;
				for (int channel = 0; channel < AnimationEditor.curveChannelCount(timeline); channel++) {
					for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
						float dx = x - xAtTime(animEditor.frameTime(timeline, frame));
						float dy = y - curveY(animEditor.curveFrameValue(timeline, frame, channel), bounds);
						float distance = dx * dx + dy * dy;
						if (distance <= best) {
							best = distance;
							nearest = new int[] {trackIndex, frame, channel};
						}
					}
				}
			}
			return nearest;
		}

		private CurveLineHit nearestCurveLine(float x, float y, Array<Track> display) {
			float[] bounds = curveDisplayValueBounds(display);
			float best = CURVE_HIT_RADIUS * CURVE_HIT_RADIUS;
			CurveLineHit nearest = null;
			// Use the same segment shapes as rendering, including stepped and Bezier curves.
			for (Track track : display) {
				Animation.Timeline timeline = track.timeline;
				int channels = AnimationEditor.curveChannelCount(timeline);
				for (int channel = 0; channel < channels; channel++) {
					for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
						float time1 = animEditor.frameTime(timeline, frame);
						float time2 = animEditor.frameTime(timeline, frame + 1);
						float value1 = animEditor.curveFrameValue(timeline, frame, channel);
						float value2 = animEditor.curveFrameValue(timeline, frame + 1, channel);
						float x1 = xAtTime(time1), y1 = curveY(value1, bounds);
						float x2 = xAtTime(time2), y2 = curveY(value2, bounds);
						int type = animEditor.curveType(timeline, frame);
						if (type == Animation.CurveTimeline.STEPPED) {
							float horizontalDistance = pointSegmentDistanceSquared(x, y, x1, y1, x2, y1);
							if (horizontalDistance < best) {
								best = horizontalDistance;
								nearest = new CurveLineHit(track, channel);
							}
							float verticalDistance = pointSegmentDistanceSquared(x, y, x2, y1, x2, y2);
							if (verticalDistance < best) {
								best = verticalDistance;
								nearest = new CurveLineHit(track, channel);
							}
							continue;
						}
						float[] handles = type >= Animation.CurveTimeline.BEZIER
							? curveHandles(timeline, frame, channel) : null;
						if (handles == null) {
							float distance = pointSegmentDistanceSquared(x, y, x1, y1, x2, y2);
							if (distance < best) {
								best = distance;
								nearest = new CurveLineHit(track, channel);
							}
							continue;
						}
						float previousX = x1;
						float previousY = y1;
						for (int step = 1; step <= 36; step++) {
							float u = step / 36f;
							float time = cubic(time1, time1 + (time2 - time1) * handles[0],
								time1 + (time2 - time1) * handles[2], time2, u);
							float value = cubic(value1, handles[1], handles[3], value2, u);
							float nextX = xAtTime(time);
							float nextY = curveY(value, bounds);
							float distance = pointSegmentDistanceSquared(x, y, previousX, previousY, nextX, nextY);
							if (distance < best) {
								best = distance;
								nearest = new CurveLineHit(track, channel);
							}
							previousX = nextX;
							previousY = nextY;
						}
					}
				}
			}
			return nearest;
		}

		private float pointSegmentDistanceSquared(float x, float y, float x1, float y1,
			float x2, float y2) {
			float dx = x2 - x1;
			float dy = y2 - y1;
			float lengthSquared = dx * dx + dy * dy;
			if (lengthSquared <= 0.000001f) return square(x - x1) + square(y - y1);
			float projection = ((x - x1) * dx + (y - y1) * dy) / lengthSquared;
			projection = Math.max(0f, Math.min(1f, projection));
			float nearestX = x1 + projection * dx;
			float nearestY = y1 + projection * dy;
			return square(x - nearestX) + square(y - nearestY);
		}

		private int nearestCurveFrame(Animation.Timeline timeline, float time) {
			int result = 0;
			float distance = Float.POSITIVE_INFINITY;
			for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
				float candidate = Math.abs(animEditor.frameTime(timeline, frame) - time);
				if (candidate < distance) { distance = candidate; result = frame; }
			}
			return result;
		}

		private int nearestCurveHandle(float x, float y, Animation.Timeline timeline) {
			int frame = selectedFrame(timeline);
			if (frame < 0) return 0;
			float best = CURVE_HIT_RADIUS * CURVE_HIT_RADIUS;
			int nearest = 0;
			float[] incoming = curveHandlePoint(timeline, frame - 1, 2, true);
			if (incoming != null) {
				float distance = square(x - incoming[0]) + square(y - incoming[1]);
				if (distance <= best) { best = distance; nearest = 1; }
			}
			float[] outgoing = curveHandlePoint(timeline, frame, 1, true);
			if (outgoing != null) {
				float distance = square(x - outgoing[0]) + square(y - outgoing[1]);
				if (distance <= best) nearest = 2;
			}
			return nearest;
		}

		private void beginCurveHandleDrag(int handle, Animation.Timeline timeline) {
			int frame = selectedFrame(timeline);
			int segment = handle == 1 ? frame - 1 : frame;
			float[] handles = curveHandles(timeline, segment, curveSelectedChannel);
			Track track = curveTrack(timeline);
			if (handles == null || track == null) return;
			rememberPointerDocument();
			curveDragHandle = handle;
			curvePreviewSegment = segment;
			curvePreviewX1 = handles[0];
			curvePreviewY1 = handles[1];
			curvePreviewX2 = handles[2];
			curvePreviewY2 = handles[3];
			float[] bounds = curveValueBounds(timeline);
			curveDragValueMin = bounds[0];
			curveDragValueMax = bounds[1];
			curveDragTimeline = timeline;
			curveDragBeforeSnapshot = snapshotTrack(currentAnim(), track);
		}

		private void updateCurveHandleDrag(float x, float y) {
			Animation.Timeline timeline = curveDragTimeline;
			int segment = curvePreviewSegment;
			if (timeline == null || segment < 0 || segment >= timeline.getFrameCount() - 1) return;
			float time1 = animEditor.frameTime(timeline, segment);
			float time2 = animEditor.frameTime(timeline, segment + 1);
			float pointerTime = Math.max(0f, (x - timeOriginX()) / pixelsPerSecond());
			float cx = Math.max(0f, Math.min(1f,
				(pointerTime - time1) / Math.max(0.000001f, time2 - time1)));
			float cy = curveValueAtY(y, new float[] {curveDragValueMin, curveDragValueMax});
			if (curveDragHandle == 1) {
				curvePreviewX2 = cx;
				curvePreviewY2 = cy;
			} else {
				curvePreviewX1 = cx;
				curvePreviewY1 = cy;
			}
		}

		private void commitCurveHandleDrag() {
			if (!syncPointerOperations()) return;
			Animation animation = currentAnim();
			Animation.Timeline timeline = curveDragTimeline;
			AnimationEditor.TimelineSnapshot before = curveDragBeforeSnapshot;
			int segment = curvePreviewSegment;
			long revision = doc.revision;
			try {
				if (animation != null && timeline != null && segment >= 0)
					animEditor.setBezierHandlesChannel(animation, timeline, segment, curveSelectedChannel,
						curvePreviewX1, curvePreviewY1, curvePreviewX2, curvePreviewY2);
				if (doc.revision != revision) {
					recordTimelineChange("编辑关键帧斜率", before);
					resetCurveHandleDrag();
					notifyChanged();
					showInfo("关键帧斜率已更新");
					return;
				}
			} catch (RuntimeException error) {
				showStatus("无法编辑关键帧斜率：" + error.getMessage());
			}
			resetCurveHandleDrag();
		}

		private void onTouchDown(float x, float y, int button) {
			Animation anim = currentAnim();
			if (anim != null && rulerScrubStarts(button, x, y, timelineBoundary(), rulerBottom(), RULER_H)) {
				scrubTo(timeAtX(x));
				return;
			}
			if (anim == null) return;
			CurveHierarchyHit hierarchyHit = timelineHierarchyHitAtY(y);
			if (hierarchyHit == null || hierarchyHit.track == null || x < timelineBoundary()) return;
			Track track = hierarchyHit.track;
			int ti = trackOf(track);
			int visibleRow = visibleTimelineRow(track);
			if (ti < 0 || visibleRow < 0) return;
			if (!track.isEvent() && !track.isAnyDrawOrder() && !track.isIk() && !track.isTransform()
				&& !track.isPath() && !track.isPhysics() && !track.isSlider()) {
				doc.setSelectedBone(track.bone);
				if (onSelectionChanged != null) onSelectionChanged.run();
			}
			Animation.Timeline tl = track.timeline;

			if (button == com.badlogic.gdx.Input.Buttons.LEFT) {
				// 命中现有帧？
				if (tl != null) {
					int f = hitFrame(tl, x, y, visibleRow);
					if (f >= 0) {
						float ft = animEditor.frameTime(tl, f);
						selectTrackKeyframe(track, f, ft);
						scrubTo(ft);
						long now = System.nanoTime();
						if (!track.isDiscrete() && ti == lastClickTrack && f == lastClickFrame
							&& now - lastClickNanos < 350_000_000L) {
							AnimationEditor.TimelineSnapshot before = snapshotTrack(anim, track);
							long revision = doc.revision;
							boolean stepped = animEditor.curveType(tl, f) == Animation.CurveTimeline.STEPPED;
							animEditor.setCurve(tl, f, !stepped);
							lastClickTrack = lastClickFrame = -1;
							recordTimelineChange("切换关键帧曲线", before);
							if (doc.revision != revision) notifyChanged();
							else showInfo("末帧没有可切换的插值曲线");
							return;
						}
						lastClickNanos = now;
						lastClickTrack = ti;
						lastClickFrame = f;
						dragTrack = ti; dragFrame = f;
						dragBeforeSnapshot = snapshotTrack(anim, track);
						dragChanged = false;
						return;
					}
				}
				// 未命中帧 → 添加
				float time = timeAtX(x);
				AnimationEditor.TimelineSnapshot before = snapshotTrack(anim, track);
				try {
					int newFrame;
					if (track.isEvent()) {
						if (doc.data == null || doc.data.getEvents().isEmpty()) {
							showEventDefinitionsDialog();
							showStatus("请先创建事件定义");
							return;
						}
						if (doc.data.getEvents().size > 1) {
							showAddEventKeyframeDialog(anim, time, before);
							return;
						}
						newFrame = addEventKeyframeWithSetup(anim, time, preferredEventDefinition());
					} else if (track.isDrawOrder()) {
						newFrame = animEditor.addDrawOrderKeyframe(anim, time, currentRuntimeDrawOrder());
					} else if (track.isDrawOrderFolder()) {
						DrawOrderFolderTimeline folder = (DrawOrderFolderTimeline)tl;
						newFrame = animEditor.addDrawOrderFolderKeyframe(anim, folder, time,
							currentRuntimeDrawOrderFolder(folder));
					} else if (track.isIk()) {
						IkConstraint runtime = doc.skeleton == null ? null
							: doc.skeleton.findConstraint(track.ik.getName(), IkConstraint.class);
						IkConstraintPose pose = runtime == null ? track.ik.getSetupPose() : runtime.getAppliedPose();
						newFrame = animEditor.addIkKeyframe(anim, track.constraintIndex, time,
							new AnimationEditor.IkKeyframe(pose.getMix(), pose.getSoftness(),
								pose.getBendDirection(), pose.getCompress(), pose.getStretch()));
					} else if (track.isTransform()) {
						TransformConstraint runtime = doc.skeleton == null ? null
							: doc.skeleton.findConstraint(track.transform.getName(), TransformConstraint.class);
						TransformConstraintPose pose = runtime == null ? track.transform.getSetupPose()
							: runtime.getAppliedPose();
						newFrame = animEditor.addTransformKeyframe(anim, track.constraintIndex, time,
							new AnimationEditor.TransformKeyframe(pose.getMixRotate(), pose.getMixX(), pose.getMixY(),
								pose.getMixScaleX(), pose.getMixScaleY(), pose.getMixShearY()));
					} else if (track.isPath()) {
						PathConstraint runtime = doc.skeleton == null ? null
							: doc.skeleton.findConstraint(track.path.getName(), PathConstraint.class);
						PathConstraintPose pose = runtime == null ? track.path.getSetupPose() : runtime.getAppliedPose();
						newFrame = switch (track.pathProp) {
						case POSITION -> animEditor.addPathValueKeyframe(anim, track.constraintIndex,
							AnimationEditor.PathProp.POSITION, time, pose.getPosition());
						case SPACING -> animEditor.addPathValueKeyframe(anim, track.constraintIndex,
							AnimationEditor.PathProp.SPACING, time, pose.getSpacing());
						case MIX -> animEditor.addPathMixKeyframe(anim, track.constraintIndex, time,
							new AnimationEditor.PathMixKeyframe(pose.getMixRotate(), pose.getMixX(), pose.getMixY()));
						};
					} else if (track.isPhysics()) {
						if (track.physicsProp == AnimationEditor.PhysicsProp.RESET)
							newFrame = animEditor.addPhysicsResetKeyframe(anim, track.constraintIndex, time);
						else
							newFrame = animEditor.addPhysicsValueKeyframe(anim, track.constraintIndex,
								track.physicsProp, time, physicsPoseValue(physicsPoseForTrack(track), track.physicsProp));
					} else if (track.isSlider()) {
						newFrame = animEditor.addSliderKeyframe(anim, track.constraintIndex, track.sliderProp,
							time, sliderPoseValue(track));
					} else if (track.isSequence()) {
						newFrame = animEditor.addSequenceKeyframe(anim, track.slot.getIndex(),
							track.sequenceAttachment, time, currentSequenceKeyframe(track));
					} else if (track.isDeform()) {
						newFrame = animEditor.addDeformKeyframe(anim, track.slot.getIndex(), track.deformAttachment,
							time, animEditor.currentDeformOffsets(track.slot.getIndex(), track.deformAttachment));
					} else if (track.isInherit()) {
						newFrame = animEditor.addInheritKeyframe(anim, track.bone.getData().getIndex(), time,
							track.bone.getPose().getInherit());
					} else if (track.isAttachment()) {
						Slot slot = doc.skeleton == null ? null : doc.skeleton.findSlot(track.slot.getName());
						Attachment attachment = slot == null ? null : slot.getPose().getAttachment();
						newFrame = animEditor.addAttachmentKeyframe(anim, track.slot.getIndex(), time,
							attachment == null ? null : attachment.getName());
					} else if (track.isColor()) {
						Slot slot = doc.skeleton == null ? null : doc.skeleton.findSlot(track.slot.getName());
						Color color = slot == null ? track.slot.getSetupPose().getColor() : slot.getPose().getColor();
						newFrame = animEditor.addColorKeyframe(anim, track.slot.getIndex(), time,
							color.r, color.g, color.b, color.a);
					} else if (track.isRgb()) {
						Slot slot = doc.skeleton == null ? null : doc.skeleton.findSlot(track.slot.getName());
						Color color = slot == null ? track.slot.getSetupPose().getColor() : slot.getPose().getColor();
						newFrame = animEditor.addRgbKeyframe(anim, track.slot.getIndex(), time,
							color.r, color.g, color.b);
					} else if (track.isAlpha()) {
						Slot slot = doc.skeleton == null ? null : doc.skeleton.findSlot(track.slot.getName());
						Color color = slot == null ? track.slot.getSetupPose().getColor() : slot.getPose().getColor();
						newFrame = animEditor.addAlphaKeyframe(anim, track.slot.getIndex(), time, color.a);
					} else if (track.isTwoColor()) {
						Slot slot = doc.skeleton == null ? null : doc.skeleton.findSlot(track.slot.getName());
						Color light = slot == null ? track.slot.getSetupPose().getColor() : slot.getPose().getColor();
						Color dark = slot == null ? track.slot.getSetupPose().getDarkColor()
							: slot.getPose().getDarkColor();
						if (dark == null) throw new IllegalStateException("该槽尚未启用双色染色");
						boolean includeAlpha = tl == null
							? animEditor.findAlphaTimeline(anim, track.slot.getIndex()) == null
							: animEditor.twoColorHasAlpha(tl);
						newFrame = animEditor.addTwoColorKeyframe(anim, track.slot.getIndex(), time,
							light.r, light.g, light.b, light.a, dark.r, dark.g, dark.b, includeAlpha);
					} else {
						int boneIndex = track.bone.getData().getIndex();
						float[] values = animEditor.valuesForPose(track.bone, track.prop);
						animEditor.ensureTimeline(anim, track.prop, boneIndex);
						animEditor.addKeyframe(anim, track.prop, boneIndex, time, values[0], values[1]);
						newFrame = animEditor.findFrame(animEditor.findTimeline(anim, track.prop, boneIndex), time);
					}
					selectTrackKeyframe(track, newFrame, time);
					doc.scrubTo(time);
					recordTimelineChange("添加关键帧", before);
					notifyChanged();
				} catch (RuntimeException error) {
					showStatus("无法添加关键帧：" + error.getMessage());
				}
			} else if (button == com.badlogic.gdx.Input.Buttons.RIGHT) {
				if (tl == null) return;
					int f = hitFrame(tl, x, y, visibleRow);
				if (f >= 0) {
					float ft = animEditor.frameTime(tl, f);
					AnimationEditor.TimelineSnapshot before = snapshotTrack(anim, track);
					try {
						if (track.isEvent())
							animEditor.removeEventKeyframe(anim, f);
						else if (track.isDrawOrder())
							animEditor.removeDrawOrderKeyframe(anim, ft);
						else if (track.isDrawOrderFolder())
							animEditor.removeDrawOrderFolderKeyframe(anim,
								(DrawOrderFolderTimeline)tl, ft);
						else if (track.isIk())
							animEditor.removeIkKeyframe(anim, track.constraintIndex, ft);
						else if (track.isTransform())
							animEditor.removeTransformKeyframe(anim, track.constraintIndex, ft);
						else if (track.isPath()) {
							if (track.pathProp == AnimationEditor.PathProp.MIX)
								animEditor.removePathMixKeyframe(anim, track.constraintIndex, ft);
							else
								animEditor.removePathValueKeyframe(anim, track.constraintIndex, track.pathProp, ft);
						}
						else if (track.isPhysics()) {
							if (track.physicsProp == AnimationEditor.PhysicsProp.RESET)
								animEditor.removePhysicsResetKeyframe(anim, track.constraintIndex, ft);
							else
								animEditor.removePhysicsValueKeyframe(anim, track.constraintIndex,
									track.physicsProp, ft);
						}
						else if (track.isSlider())
							animEditor.removeSliderKeyframe(anim, track.constraintIndex, track.sliderProp, ft);
						else if (track.isSequence())
							animEditor.removeSequenceKeyframe(anim, track.slot.getIndex(),
								track.sequenceAttachment, ft);
						else if (track.isDeform())
							animEditor.removeDeformKeyframe(anim, track.slot.getIndex(), track.deformAttachment, ft);
						else if (track.isInherit())
							animEditor.removeInheritKeyframe(anim, track.bone.getData().getIndex(), ft);
						else if (track.isAttachment())
							animEditor.removeAttachmentKeyframe(anim, track.slot.getIndex(), ft);
						else if (track.isColor())
							animEditor.removeColorKeyframe(anim, track.slot.getIndex(), ft);
						else if (track.isRgb())
							animEditor.removeRgbKeyframe(anim, track.slot.getIndex(), ft);
						else if (track.isAlpha())
							animEditor.removeAlphaKeyframe(anim, track.slot.getIndex(), ft);
						else if (track.isTwoColor())
							animEditor.removeTwoColorKeyframe(anim, track.slot.getIndex(), ft);
						else
							animEditor.removeKeyframe(anim, track.prop, track.bone.getData().getIndex(), ft);
						clearSelection();
						recordTimelineChange("删除关键帧", before);
						notifyChanged();
					} catch (RuntimeException error) {
						showStatus("无法删除关键帧：" + error.getMessage());
					}
					return;
				}
			} else if (button == com.badlogic.gdx.Input.Buttons.MIDDLE) {
				// 双击切换曲线：此处用中间键模拟
				if (tl == null || track.isDiscrete()) return;
				int f = hitFrame(tl, x, y, visibleRow);
				if (f >= 0) {
					float ft = animEditor.frameTime(tl, f);
					AnimationEditor.TimelineSnapshot before = snapshotTrack(anim, track);
					selectTrackKeyframe(track, f, ft);
					doc.scrubTo(ft);
					long revision = doc.revision;
					boolean stepped = animEditor.curveType(tl, f) == Animation.CurveTimeline.STEPPED;
					animEditor.setCurve(tl, f, !stepped);
					recordTimelineChange("切换关键帧曲线", before);
					if (doc.revision != revision) notifyChanged();
					else showInfo("末帧没有可切换的插值曲线");
					return;
				}
			}
		}

		private void onTouchDragged(float x) {
			if (dragTrack < 0 || dragFrame < 0) return;
			if (dragTrack >= tracks.size) {
				onTouchUp();
				return;
			}
			Animation anim = currentAnim();
			if (anim == null) {
				onTouchUp();
				return;
			}
			Track track = tracks.get(dragTrack);
			Animation.Timeline tl = track.timeline;
			if (tl == null || dragFrame >= tl.getFrameCount()) {
				onTouchUp();
				return;
			}
			float oldT = animEditor.frameTime(tl, dragFrame);
			float newTime = timeAtX(x);
			if (sameKeyframeTime(newTime, oldT)) return;
			try {
				int newFrame;
				if (track.isEvent()) {
					Event event = animEditor.eventAt((EventTimeline)tl, dragFrame);
					newFrame = animEditor.editEventKeyframe(anim, dragFrame, newTime, event.getData(),
						event.getInt(), event.getFloat(), event.getString(), event.getVolume(), event.getBalance());
				} else if (track.isDrawOrderFolder()) {
					DrawOrderFolderTimeline folder = (DrawOrderFolderTimeline)tl;
					newFrame = animEditor.editDrawOrderFolderKeyframe(anim, folder, oldT, newTime,
						animEditor.drawOrderFolderAt(folder, dragFrame));
				} else if (track.isDrawOrder()) {
					newFrame = animEditor.editDrawOrderKeyframe(anim, oldT, newTime,
						animEditor.drawOrderAt((DrawOrderTimeline)tl, dragFrame));
				} else if (track.isIk()) {
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					newFrame = animEditor.editIkKeyframe(anim, track.constraintIndex, oldT, newTime,
						animEditor.ikAt((IkConstraintTimeline)tl, dragFrame), stepped);
				} else if (track.isTransform()) {
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					newFrame = animEditor.editTransformKeyframe(anim, track.constraintIndex, oldT, newTime,
						animEditor.transformAt((TransformConstraintTimeline)tl, dragFrame), stepped);
				} else if (track.isPath()) {
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					if (track.pathProp == AnimationEditor.PathProp.MIX)
						newFrame = animEditor.editPathMixKeyframe(anim, track.constraintIndex, oldT, newTime,
							animEditor.pathMixAt((PathConstraintMixTimeline)tl, dragFrame), stepped);
					else
						newFrame = animEditor.editPathValueKeyframe(anim, track.constraintIndex, track.pathProp,
							oldT, newTime, animEditor.pathValueAt(tl, dragFrame), stepped);
				} else if (track.isPhysics()) {
					if (track.physicsProp == AnimationEditor.PhysicsProp.RESET)
						newFrame = animEditor.editPhysicsResetKeyframe(anim, track.constraintIndex, oldT, newTime);
					else {
						boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
						newFrame = animEditor.editPhysicsValueKeyframe(anim, track.constraintIndex, track.physicsProp,
							oldT, newTime, animEditor.physicsValueAt(tl, dragFrame), stepped);
					}
				} else if (track.isSlider()) {
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					newFrame = animEditor.editSliderKeyframe(anim, track.constraintIndex, track.sliderProp,
						oldT, newTime, animEditor.sliderValueAt(tl, dragFrame), stepped);
				} else if (track.isSequence()) {
					newFrame = animEditor.editSequenceKeyframe(anim, track.slot.getIndex(), track.sequenceAttachment,
						oldT, newTime, animEditor.sequenceAt((SequenceTimeline)tl, dragFrame));
				} else if (track.isDeform()) {
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					newFrame = animEditor.editDeformKeyframe(anim, track.slot.getIndex(), track.deformAttachment,
						oldT, newTime, animEditor.deformOffsetsAt((DeformTimeline)tl, dragFrame), stepped);
				} else if (track.isInherit()) {
					newFrame = animEditor.editInheritKeyframe(anim, track.bone.getData().getIndex(), oldT,
						newTime, animEditor.inheritAt((InheritTimeline)tl, dragFrame));
				} else if (track.isAttachment()) {
					String attachmentName = animEditor.attachmentName((AttachmentTimeline)tl, dragFrame);
					newFrame = animEditor.editAttachmentKeyframe(anim, track.slot.getIndex(), oldT,
						newTime, attachmentName);
				} else if (track.isColor()) {
					RGBATimeline colorTimeline = (RGBATimeline)tl;
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					newFrame = animEditor.editColorKeyframe(anim, track.slot.getIndex(), oldT, newTime,
						animEditor.colorValue(colorTimeline, dragFrame, 0),
						animEditor.colorValue(colorTimeline, dragFrame, 1),
						animEditor.colorValue(colorTimeline, dragFrame, 2),
						animEditor.colorValue(colorTimeline, dragFrame, 3), stepped);
				} else if (track.isRgb()) {
					RGBTimeline colorTimeline = (RGBTimeline)tl;
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					newFrame = animEditor.editRgbKeyframe(anim, track.slot.getIndex(), oldT, newTime,
						animEditor.rgbValue(colorTimeline, dragFrame, 0),
						animEditor.rgbValue(colorTimeline, dragFrame, 1),
						animEditor.rgbValue(colorTimeline, dragFrame, 2), stepped);
				} else if (track.isAlpha()) {
					AlphaTimeline alphaTimeline = (AlphaTimeline)tl;
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					newFrame = animEditor.editAlphaKeyframe(anim, track.slot.getIndex(), oldT, newTime,
						animEditor.alphaValue(alphaTimeline, dragFrame), stepped);
				} else if (track.isTwoColor()) {
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					newFrame = animEditor.editTwoColorKeyframe(anim, track.slot.getIndex(), oldT, newTime,
						animEditor.twoColorValue(tl, dragFrame, 0), animEditor.twoColorValue(tl, dragFrame, 1),
						animEditor.twoColorValue(tl, dragFrame, 2), animEditor.twoColorValue(tl, dragFrame, 3),
						animEditor.twoColorValue(tl, dragFrame, 4), animEditor.twoColorValue(tl, dragFrame, 5),
						animEditor.twoColorValue(tl, dragFrame, 6), stepped);
				} else {
					float value1 = animEditor.frameValue(tl, dragFrame, 0);
					float value2 = animEditor.frameValue(tl, dragFrame, 1);
					boolean stepped = animEditor.curveType(tl, dragFrame) == Animation.CurveTimeline.STEPPED;
					newFrame = animEditor.editKeyframe(anim, track.prop, track.bone.getData().getIndex(),
						oldT, newTime, value1, value2, stepped);
				}
				selectTrackKeyframe(track, newFrame, newTime);
				doc.scrubTo(newTime);
				notifyChanged();
				dragChanged = true;
				dragFrame = newFrame;
				dragTrack = trackOf(track);
				if (dragTrack < 0) onTouchUp();
			} catch (RuntimeException error) {
				showStatus("无法移动关键帧：" + error.getMessage());
			}
		}

		private void onTouchUp() {
			AnimationEditor.TimelineSnapshot before = dragBeforeSnapshot;
			boolean changed = dragChanged;
			dragTrack = -1;
			dragFrame = -1;
			dragBeforeSnapshot = null;
			dragChanged = false;
			if (changed) recordTimelineChange("移动关键帧", before);
		}

		private boolean sameTrack(Track first, Track second) {
			if (first == null || second == null) return false;
			if (first.isEvent()) return second.isEvent();
			if (first.isDrawOrder()) return second.isDrawOrder();
			if (first.isDrawOrderFolder()) return second.isDrawOrderFolder()
				&& java.util.Arrays.equals(first.drawOrderFolder.getSlots(), second.drawOrderFolder.getSlots());
			if (first.isIk()) return second.isIk() && first.ik.getName().equals(second.ik.getName());
			if (first.isTransform())
				return second.isTransform() && first.transform.getName().equals(second.transform.getName());
			if (first.isPath()) return second.isPath() && first.pathProp == second.pathProp
				&& first.path.getName().equals(second.path.getName());
			if (first.isPhysics()) return second.isPhysics() && first.constraintIndex == second.constraintIndex
				&& first.physicsProp == second.physicsProp;
			if (first.isSlider()) return second.isSlider() && first.constraintIndex == second.constraintIndex
				&& first.sliderProp == second.sliderProp;
			if (first.isSequence()) return second.isSequence()
				&& first.sequenceAttachment == second.sequenceAttachment
				&& first.slot.getName().equals(second.slot.getName());
			if (first.isDeform()) return second.isDeform() && first.deformAttachment == second.deformAttachment
				&& first.slot.getName().equals(second.slot.getName());
			if (first.isSlot()) return second.isSlot() && first.slotProp == second.slotProp
				&& first.slot.getName().equals(second.slot.getName());
			return !second.isSlot() && first.isInherit() == second.isInherit() && first.prop == second.prop
				&& first.bone.getData().getName().equals(second.bone.getData().getName());
		}

		private int trackOf(Track source) {
			for (int i = 0; i < tracks.size; i++) {
				Track candidate = tracks.get(i);
				if (sameTrack(candidate, source)) return i;
				if (source.isEvent()) {
					if (candidate.isEvent()) return i;
				} else if (source.isDrawOrder()) {
					if (candidate.isDrawOrder()) return i;
				} else if (source.isIk()) {
					if (candidate.isIk() && candidate.ik.getName().equals(source.ik.getName())) return i;
				} else if (source.isTransform()) {
					if (candidate.isTransform()
						&& candidate.transform.getName().equals(source.transform.getName())) return i;
				} else if (source.isPath()) {
					if (candidate.isPath() && candidate.pathProp == source.pathProp
						&& candidate.path.getName().equals(source.path.getName())) return i;
				} else if (source.isPhysics()) {
					if (candidate.isPhysics() && candidate.constraintIndex == source.constraintIndex
						&& candidate.physicsProp == source.physicsProp) return i;
				} else if (source.isSlider()) {
					if (candidate.isSlider() && candidate.constraintIndex == source.constraintIndex
						&& candidate.sliderProp == source.sliderProp) return i;
				} else if (source.isSequence()) {
					if (candidate.isSequence() && candidate.sequenceAttachment == source.sequenceAttachment
						&& candidate.slot.getName().equals(source.slot.getName())) return i;
				} else if (source.isDeform()) {
					if (candidate.isDeform() && candidate.deformAttachment == source.deformAttachment
						&& candidate.slot.getName().equals(source.slot.getName())) return i;
				} else if (source.isSlot()) {
					if (candidate.isSlot() && candidate.slotProp == source.slotProp
						&& candidate.slot.getName().equals(source.slot.getName())) return i;
				} else if (!candidate.isSlot() && candidate.isInherit() == source.isInherit()
					&& candidate.prop == source.prop
					&& candidate.bone.getData().getName().equals(source.bone.getData().getName())) return i;
			}
			return -1;
		}

		@Override
		public void draw(Batch batch, float parentAlpha) {
			validate();
			drawOrigin.set(0f, 0f);
			localToStageCoordinates(drawOrigin);
			batch.end();
			shapes.setProjectionMatrix(getStage().getCamera().combined);
			if (curveView) {
				drawCurveBackground();
				drawCurveGrid();
				drawCurves();
				drawSelectionMarquee();
			} else {
				drawBackground();
				drawGrid();
				drawKeyframes();
				drawSelectionMarquee();
			}
			drawFixedChrome();
			batch.begin();
			savedBatchTransform.set(batch.getTransformMatrix());
			batch.setTransformMatrix(identityBatchTransform.idt());
			try {
				if (curveView) {
					drawCurveValueLabels(batch);
					drawScaledTimelineHierarchy(batch, true);
					drawRulerLabels(batch);
				} else {
					drawRulerLabels(batch);
					drawScaledTimelineHierarchy(batch, false);
				}
			} finally {
				batch.setTransformMatrix(savedBatchTransform);
			}
		}

		private void drawScaledTimelineHierarchy(Batch batch, boolean curveHierarchy) {
			float previousScaleX = font.getData().scaleX;
			float previousScaleY = font.getData().scaleY;
			float scale = timelineLabelScale(timelineVerticalZoom);
			font.getData().setScale(previousScaleX * scale, previousScaleY * scale);
			try {
				if (curveHierarchy) drawCurveHierarchy(batch); else drawTrackLabels(batch);
			} finally {
				font.getData().setScale(previousScaleX, previousScaleY);
			}
		}

		private void drawCurveBackground() {
			shapes.begin(ShapeRenderer.ShapeType.Filled);
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_BACKGROUND));
			shapes.rect(drawOrigin.x, drawOrigin.y, getWidth(), getHeight());
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_PLOT));
			shapes.rect(drawOrigin.x + timeOriginX(), drawOrigin.y + curvePlotBottom(),
				Math.max(0f, getWidth() - timeOriginX()), curvePlotHeight());
			Animation animation = currentAnim();
			float activeLeft = timeOriginX();
			float activeRight = animation == null ? activeLeft : Math.min(getWidth(),
				xAtTime(timelineActiveEndTime(animation.getDuration(), frameRate())));
			float activeWidth = Math.max(0f, activeRight - activeLeft);
			if (activeWidth > 0f) {
				shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_ACTIVE));
				shapes.rect(drawOrigin.x + activeLeft, drawOrigin.y,
					activeWidth, Math.max(0f, curveContentTop()));
			}
			shapes.end();
		}

		private float viewportRightX() {
			// ScrollPane.getWidth() includes the vertical scrollbar. Spine's graph
			// keeps the value axis inside the viewport, leaving the scrollbar clear.
			float viewportWidth = scroll == null || scroll.getScrollWidth() <= 0f
				? getWidth() : scroll.getScrollWidth();
			return Math.min(getWidth(), horizontalScroll() + viewportWidth);
		}

		private void drawCurveGrid() {
			shapes.begin(ShapeRenderer.ShapeType.Line);
			float fps = frameRate();
			int frameStep = rulerFrameStep(pixelsPerSecond(), fps);
			double firstFrame = firstRulerFrame(visibleStartTime(), fps, frameStep);
			double endFrame = visibleEndTime() * fps;
			for (int index = 0; index < 10000; index++) {
				double frame = firstFrame + (double)index * frameStep;
				if (frame > endFrame + 0.000001d) break;
				float x = drawOrigin.x + xAtTime((float)(frame / fps));
				shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_GRID));
				shapes.line(x, drawOrigin.y + curvePlotBottom(), x,
					drawOrigin.y + curvePlotTop());
			}
			Array<Track> display = curveDisplayTracks();
			if (!display.isEmpty()) {
				float[] bounds = curveDisplayValueBounds(display);
				float step = curveTickStep(bounds[1] - bounds[0]);
				float first = (float)Math.ceil(bounds[0] / step - 0.000001f) * step;
				for (int index = 0; index < 1000; index++) {
					float value = first + index * step;
					if (value > bounds[1] + step * 0.000001f) break;
					float y = drawOrigin.y + curveY(value, bounds);
					boolean zero = Math.abs(value) <= step * 0.000001f;
					shapes.setColor(kit.palette().color(zero
						? UiAppearance.Role.BORDER : UiAppearance.Role.TIMELINE_GRID));
					shapes.line(drawOrigin.x + timeOriginX(), y,
						drawOrigin.x + viewportRightX(), y);
				}
			}
			float playheadX = drawOrigin.x + xAtTime(doc.getCurrentTime());
			shapes.setColor(0.05f, 0.78f, 0.88f, 1f);
			shapes.line(playheadX, drawOrigin.y + curvePlotBottom(), playheadX,
				drawOrigin.y + rulerBottom() + 11f);
			shapes.end();
		}

		private void drawCurves() {
			Array<Track> display = curveDisplayTracks();
			if (display.isEmpty()) return;
			float[] bounds = curveDisplayValueBounds(display);
			shapes.begin(ShapeRenderer.ShapeType.Line);
			for (int trackIndex = 0; trackIndex < display.size; trackIndex++) {
				Track track = display.get(trackIndex);
				Animation.Timeline timeline = track.timeline;
				int channels = AnimationEditor.curveChannelCount(timeline);
				for (int channel = 0; channel < channels; channel++) {
					Color color = curveColor(track, channel);
					boolean activeCurve = sameTrack(track, curveTargetTrack) && channel == curveSelectedChannel;
					shapes.setColor(color.r, color.g, color.b, activeCurve ? 1f : 0.46f);
					for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++)
						drawCurveSegment(timeline, frame, channel, bounds);
				}
			}
			Animation.Timeline primary = curveTimeline();
			if (primary != null) {
				curveSelectedChannel = Math.min(curveSelectedChannel,
					Math.max(0, AnimationEditor.curveChannelCount(primary) - 1));
				drawCurveHandleLines(primary, curveValueBounds(primary));
			}
			shapes.end();

			shapes.begin(ShapeRenderer.ShapeType.Filled);
			for (int trackIndex = 0; trackIndex < display.size; trackIndex++) {
				Track track = display.get(trackIndex);
				Animation.Timeline timeline = track.timeline;
				int channels = AnimationEditor.curveChannelCount(timeline);
				int selectedFrame = selectedFrame(timeline);
				for (int channel = 0; channel < channels; channel++) {
					Color color = curveColor(track, channel);
					for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
						float x = drawOrigin.x + xAtTime(animEditor.frameTime(timeline, frame));
						float y = drawOrigin.y + curveY(animEditor.curveFrameValue(timeline, frame, channel), bounds);
						boolean selected = graphKeySelected(track, frame);
						boolean active = track == curveTargetTrack && frame == selectedFrame
							&& channel == curveSelectedChannel;
						shapes.setColor(selected ? 0.08f : 0.025f, selected ? 0.84f : 0.03f,
							selected ? 0.94f : 0.035f, 1f);
						float outer = active ? 10f : 8f;
						shapes.rect(x - outer / 2f, y - outer / 2f, outer, outer);
						shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_PLOT));
						float middle = active ? 6f : 5f;
						shapes.rect(x - middle / 2f, y - middle / 2f, middle, middle);
						shapes.setColor(color);
						shapes.rect(x - 1.5f, y - 1.5f, 3f, 3f);
					}
				}
			}
			if (primary != null) drawCurveHandleNodes(primary);
			shapes.end();
		}

		private void drawCurveSegment(Animation.Timeline timeline, int frame, int channel, float[] bounds) {
			float time1 = animEditor.frameTime(timeline, frame);
			float time2 = animEditor.frameTime(timeline, frame + 1);
			float value1 = animEditor.curveFrameValue(timeline, frame, channel);
			float value2 = animEditor.curveFrameValue(timeline, frame + 1, channel);
			float x1 = drawOrigin.x + xAtTime(time1);
			float y1 = drawOrigin.y + curveY(value1, bounds);
			float x2 = drawOrigin.x + xAtTime(time2);
			float y2 = drawOrigin.y + curveY(value2, bounds);
			int type = animEditor.curveType(timeline, frame);
			if (type == Animation.CurveTimeline.STEPPED) {
				shapes.line(x1, y1, x2, y1);
				shapes.line(x2, y1, x2, y2);
				return;
			}
			float[] handles = type >= Animation.CurveTimeline.BEZIER ? curveHandles(timeline, frame, channel) : null;
			if (handles == null) {
				shapes.line(x1, y1, x2, y2);
				return;
			}
			float previousX = x1;
			float previousY = y1;
			for (int step = 1; step <= 36; step++) {
				float u = step / 36f;
				float time = cubic(time1, time1 + (time2 - time1) * handles[0],
					time1 + (time2 - time1) * handles[2], time2, u);
				float value = cubic(value1, handles[1], handles[3], value2, u);
				float x = drawOrigin.x + xAtTime(time);
				float y = drawOrigin.y + curveY(value, bounds);
				shapes.line(previousX, previousY, x, y);
				previousX = x;
				previousY = y;
			}
		}

		private void drawCurveHandleLines(Animation.Timeline timeline, float[] bounds) {
			int frame = selectedFrame(timeline);
			if (frame < 0) return;
			float keyX = drawOrigin.x + xAtTime(animEditor.frameTime(timeline, frame));
			float keyY = drawOrigin.y + curveY(
				animEditor.curveFrameValue(timeline, frame, curveSelectedChannel), bounds);
			Color color = curveColor(curveSelectedChannel);
			shapes.setColor(color.r, color.g, color.b, 0.85f);
			float[] incoming = curveHandlePoint(timeline, frame - 1, 2, false);
			if (incoming != null) drawDashedCurveLine(keyX, keyY, incoming[0], incoming[1]);
			float[] outgoing = curveHandlePoint(timeline, frame, 1, false);
			if (outgoing != null) drawDashedCurveLine(keyX, keyY, outgoing[0], outgoing[1]);
		}

		private void drawDashedCurveLine(float x1, float y1, float x2, float y2) {
			float dx = x2 - x1;
			float dy = y2 - y1;
			float length = (float)Math.sqrt(dx * dx + dy * dy);
			if (length <= 0.0001f) return;
			float dash = 5f;
			float gap = 3f;
			for (float start = 0f; start < length; start += dash + gap) {
				float end = Math.min(length, start + dash);
				float from = start / length;
				float to = end / length;
				shapes.line(x1 + dx * from, y1 + dy * from, x1 + dx * to, y1 + dy * to);
			}
		}

		private void drawCurveHandleNodes(Animation.Timeline timeline) {
			int frame = selectedFrame(timeline);
			if (frame < 0) return;
			float[] incoming = curveHandlePoint(timeline, frame - 1, 2, false);
			float[] outgoing = curveHandlePoint(timeline, frame, 1, false);
			shapes.setColor(0.05f, 0.86f, 0.94f, 1f);
			if (incoming != null) shapes.circle(incoming[0], incoming[1], 4f, 16);
			if (outgoing != null) shapes.circle(outgoing[0], outgoing[1], 4f, 16);
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_PLOT));
			if (incoming != null) shapes.circle(incoming[0], incoming[1], 2f, 12);
			if (outgoing != null) shapes.circle(outgoing[0], outgoing[1], 2f, 12);
		}

		private void drawBackground() {
			shapes.begin(ShapeRenderer.ShapeType.Filled);
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_BACKGROUND));
			shapes.rect(drawOrigin.x, drawOrigin.y, getWidth(), getHeight());
			Animation animation = currentAnim();
			float rowHeight = rowHeight();
			float contentTop = getHeight() - RULER_H;
			int visibleRows = timelineHierarchyRows();
			float trackHeight = Math.min(Math.max(0f, contentTop), visibleRows * rowHeight);
			float activeLeft = timeOriginX();
			float activeRight = animation == null ? activeLeft : Math.min(getWidth(),
				xAtTime(timelineActiveEndTime(animation.getDuration(), frameRate())));
			float activeWidth = Math.max(0f, activeRight - activeLeft);
			if (activeWidth > 0f && trackHeight > 0f) {
				shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_ACTIVE));
				shapes.rect(drawOrigin.x + activeLeft, drawOrigin.y + contentTop - trackHeight,
					activeWidth, trackHeight);
				shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_ACTIVE_ROW_ALT));
				for (int i = 1; i < visibleRows; i += 2) {
					float y = drawOrigin.y + getHeight() - RULER_H - (i + 1) * rowHeight;
					shapes.rect(drawOrigin.x + activeLeft, y, activeWidth, rowHeight);
				}
			}
			shapes.end();
		}

		private void drawGrid() {
			shapes.begin(ShapeRenderer.ShapeType.Line);
			float fps = frameRate();
			int majorFrameStep = rulerFrameStep(pixelsPerSecond(), fps);
			double firstFrame = firstRulerFrame(visibleStartTime(), fps, majorFrameStep);
			double endFrame = visibleEndTime() * fps;
			for (int index = 0; index < 10000; index++) {
				double frame = firstFrame + (double)index * majorFrameStep;
				if (frame > endFrame + 0.000001d) break;
				shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_GRID));
				float x = drawOrigin.x + xAtTime((float)(frame / fps));
				shapes.line(x, drawOrigin.y, x, drawOrigin.y + getHeight() - RULER_H);
			}
			// 轨道分隔
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_DIVIDER));
			float rowHeight = rowHeight();
			for (int i = 0; i <= timelineHierarchyRows(); i++) {
				float y = drawOrigin.y + getHeight() - RULER_H - i * rowHeight;
				shapes.line(drawOrigin.x, y, drawOrigin.x + getWidth(), y);
			}
			// 当前播放时间线
			shapes.setColor(0.05f, 0.78f, 0.88f, 1f);
			float cx = drawOrigin.x + xAtTime(Math.min(doc.getCurrentTime(),
				(getWidth() - timeOriginX()) / pixelsPerSecond()));
			shapes.line(cx, drawOrigin.y, cx, drawOrigin.y + rulerBottom() + 11f);
			shapes.end();
		}

		private void drawKeyframes() {
			Animation anim = currentAnim();
			if (anim == null) return;
			float startTime = visibleStartTime() - 0.2f;
			float endTime = visibleEndTime() + 0.2f;
			shapes.begin(ShapeRenderer.ShapeType.Filled);
			float rowHeight = rowHeight();
			if (!curveView) {
				HashSet<Long> animationFrames = new HashSet<>();
				float fps = frameRate();
				for (int trackIndex = 0; trackIndex < tracks.size; trackIndex++) {
					if (tracks.get(trackIndex).isAnyDrawOrder()) continue;
					Animation.Timeline timeline = tracks.get(trackIndex).timeline;
					if (timeline == null) continue;
					for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
						float time = animEditor.frameTime(timeline, frame);
						if (time >= startTime && time <= endTime)
							animationFrames.add((long)Math.round(time * fps));
					}
				}
				float animationCenter = drawOrigin.y + getHeight() - RULER_H - rowHeight / 2f;
				shapes.setColor(0.96f, 0.29f, 0.12f, 1f);
				for (long frame : animationFrames)
					diamond(drawOrigin.x + xAtTime(frame / fps), animationCenter, 4f);
				int row = 1;
				Array<CurveHierarchyGroup> groups = timelineHierarchyGroups();
				for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
					CurveHierarchyGroup group = groups.get(groupIndex);
					HashSet<Long> groupFrames = new HashSet<>();
					for (int trackIndex = 0; trackIndex < group.tracks.size; trackIndex++) {
						Animation.Timeline timeline = group.tracks.get(trackIndex).timeline;
						if (timeline == null) continue;
						for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
							float time = animEditor.frameTime(timeline, frame);
							if (time >= startTime && time <= endTime)
								groupFrames.add((long)Math.round(time * fps));
						}
					}
					float groupCenter = drawOrigin.y + getHeight() - RULER_H
						- (row * rowHeight + rowHeight / 2f);
					shapes.setColor(0.56f, 0.62f, 0.68f, 1f);
					for (long frame : groupFrames)
						diamond(drawOrigin.x + xAtTime(frame / fps), groupCenter, 4f);
					row++;
				}
			}
			for (int t = 0; t < tracks.size; t++) {
				Track track = tracks.get(t);
				Animation.Timeline tl = track.timeline;
				int visibleRow = visibleTimelineRow(track);
				if (visibleRow < 0) continue;
				float cy = drawOrigin.y + getHeight() - RULER_H
					- (visibleRow * rowHeight + rowHeight / 2f);
				if (tl == null) continue;
				int groupStart = 0;
				int groupEnd = -1;
				for (int f = 0; f < tl.getFrameCount(); f++) {
					float ft = animEditor.frameTime(tl, f);
					if (ft < startTime || ft > endTime) continue;
					float fx = drawOrigin.x + xAtTime(ft);
					float markerOffset = 0f;
					if (track.isEvent()) {
						EventTimeline events = (EventTimeline)tl;
						if (f > groupEnd) {
							groupStart = f;
							groupEnd = eventGroupEnd(events, f);
						}
						markerOffset = eventMarkerOffset(f, groupStart, groupEnd);
					}
					float fy = cy + markerOffset;
					boolean sameTrack = track.isEvent() ? selEventFrame >= 0
						: track.isDrawOrder() ? selDrawOrder
						: track.isDrawOrderFolder() ? java.util.Arrays.equals(
							track.drawOrderFolder.getSlots(), selDrawOrderFolderSlots)
						: track.isIk() ? track.ik.getName().equals(selIkConstraintName)
						: track.isTransform() ? track.transform.getName().equals(selTransformConstraintName)
						: track.isPath() ? track.path.getName().equals(selPathConstraintName)
							&& track.pathProp == selPathProp
						: track.isPhysics() ? track.constraintIndex == selectedPhysicsConstraintIndex()
							&& track.physicsProp == selPhysicsProp
						: track.isSlider() ? track.slider.getName().equals(selSliderConstraintName)
							&& track.sliderProp == selSliderProp
						: track.isSequence() ? track.sequenceAttachment == selSequenceAttachment
							&& track.slot.getName().equals(selSlotName)
						: track.isDeform() ? track.deformAttachment == selDeformAttachment
							&& track.slot.getName().equals(selSlotName)
						: track.isSlot()
						? track.slot.getName().equals(selSlotName) && track.slotProp == selSlotProp
						: track.bone.getData().getName().equals(selBoneName)
							&& track.isInherit() == selInherit && track.prop == selProp;
					boolean sel = anim.getName().equals(selAnimationName) && sameTrack
						&& (track.isEvent() ? f == selEventFrame : sameKeyframeTime(ft, selTime))
						|| isExtraSelection(track, tl, f, ft);
					if (draggingKeys && isDragSelection(track, tl, f, ft)) fx += dragPreviewDelta * pixelsPerSecond();
					if (sel) {
						shapes.setColor(1f, 0.95f, 0.3f, 1f);
						diamond(fx, fy, 6);
					} else if (track.isColor()) {
						shapes.setColor(0.68f, 0.72f, 0.78f, 1f);
						diamond(fx, fy, 6);
						Color color = colorAt((RGBATimeline)tl, f);
						shapes.setColor(color.r, color.g, color.b, 1f);
						diamond(fx, fy, 4);
					} else if (track.isRgb()) {
						shapes.setColor(0.68f, 0.72f, 0.78f, 1f);
						diamond(fx, fy, 6);
						Color color = rgbAt((RGBTimeline)tl, f);
						shapes.setColor(color.r, color.g, color.b, 1f);
						diamond(fx, fy, 4);
					} else if (track.isAlpha()) {
						float alpha = animEditor.alphaValue((AlphaTimeline)tl, f);
						shapes.setColor(0.3f + alpha * 0.55f, 0.45f + alpha * 0.4f,
							0.55f + alpha * 0.35f, 1f);
						diamond(fx, fy, 5);
					} else if (track.isTwoColor()) {
						Color light = twoColorLightAt(tl, f);
						Color dark = twoColorDarkAt(tl, f);
						shapes.setColor(light.r, light.g, light.b, 1f);
						diamond(fx, fy, 6);
						shapes.setColor(dark.r, dark.g, dark.b, 1f);
						diamond(fx, fy, 3);
					} else {
						if (track.isEvent()) shapes.setColor(0.35f, 0.82f, 0.55f, 1f);
						else if (track.isAnyDrawOrder()) shapes.setColor(0.92f, 0.42f, 0.62f, 1f);
						else if (track.isIk()) shapes.setColor(0.28f, 0.78f, 0.72f, 1f);
						else if (track.isTransform()) shapes.setColor(0.88f, 0.55f, 0.25f, 1f);
						else if (track.isPath()) shapes.setColor(0.45f, 0.72f, 0.92f, 1f);
						else if (track.isPhysics()) shapes.setColor(0.72f, 0.62f, 0.92f, 1f);
						else if (track.isSlider()) shapes.setColor(0.92f, 0.68f, 0.38f, 1f);
						else if (track.isSequence()) shapes.setColor(0.32f, 0.84f, 0.66f, 1f);
						else if (track.isDeform()) shapes.setColor(0.35f, 0.82f, 0.9f, 1f);
						else if (track.isInherit()) shapes.setColor(0.78f, 0.55f, 0.92f, 1f);
						else if (track.isAttachment()) shapes.setColor(0.25f, 0.72f, 0.88f, 1f);
						else shapes.setColor(0.95f, 0.65f, 0.2f, 1f);
						diamond(fx, fy, 5);
					}
				}
			}
			shapes.end();
		}

		private boolean isExtraSelection(Track track, Animation.Timeline timeline, int frame, float time) {
			int occurrence = timeline instanceof EventTimeline events ? eventOccurrence(events, frame) : 0;
			for (KeySelection selection : extraSelections) {
				if (sameTrack(selection.track, track) && Float.compare(selection.time, time) == 0
					&& (!track.isEvent() || selection.eventOccurrence == occurrence)) return true;
			}
			return false;
		}

		private boolean isDragSelection(Track track, Animation.Timeline timeline, int frame, float time) {
			int occurrence = timeline instanceof EventTimeline events ? eventOccurrence(events, frame) : 0;
			for (KeySelection selection : dragSelections) {
				if (sameTrack(selection.track, track) && Float.compare(selection.time, time) == 0
					&& (!track.isEvent() || selection.eventOccurrence == occurrence)) return true;
			}
			return false;
		}

		private void drawSelectionMarquee() {
			if (!marqueeSelecting || !marqueeDragging) return;
			float left = drawOrigin.x + Math.min(marqueeStartX, marqueeCurrentX);
			float bottom = drawOrigin.y + Math.min(marqueeStartY, marqueeCurrentY);
			float width = Math.abs(marqueeCurrentX - marqueeStartX);
			float height = Math.abs(marqueeCurrentY - marqueeStartY);
			shapes.begin(ShapeRenderer.ShapeType.Filled);
			shapes.setColor(0.18f, 0.62f, 0.9f, 0.16f);
			shapes.rect(left, bottom, width, height);
			shapes.end();
			shapes.begin(ShapeRenderer.ShapeType.Line);
			shapes.setColor(0.32f, 0.76f, 1f, 0.95f);
			shapes.rect(left, bottom, width, height);
			shapes.end();
		}

		private float curveHierarchyTop() {
			return curveContentTop() - curveRowHeight() / 2f;
		}

		private float curveContentTop() { return getHeight() - RULER_H; }

		private float curvePlotBottom() { return curvePlotPadding(); }

		private float curvePlotTop() {
			// The graph starts immediately below the time ruler, like Spine's Graph view.
			return Math.max(curvePlotBottom() + 1f, curveContentTop());
		}

		private float curvePlotHeight() { return curvePlotTop() - curvePlotBottom(); }

		private float curveY(float value, float[] bounds) {
			float range = Math.max(0.000001f, bounds[1] - bounds[0]);
			return curvePlotBottom() + (value - bounds[0]) / range * curvePlotHeight();
		}

		private float curveValueAtY(float y, float[] bounds) {
			float normalized = (y - curvePlotBottom()) / Math.max(1f, curvePlotHeight());
			return bounds[0] + normalized * (bounds[1] - bounds[0]);
		}

		private float[] curveValueBounds(Animation.Timeline timeline) {
			float min = Float.POSITIVE_INFINITY;
			float max = Float.NEGATIVE_INFINITY;
			int channels = AnimationEditor.curveChannelCount(timeline);
			for (int channel = 0; channel < channels; channel++) {
				for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
					float value = animEditor.curveFrameValue(timeline, frame, channel);
					min = Math.min(min, value);
					max = Math.max(max, value);
				}
				for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
					float[] handles = curveHandles(timeline, frame, channel);
					if (handles == null) continue;
					float handle1 = handles[1];
					float handle2 = handles[3];
					min = Math.min(min, Math.min(handle1, handle2));
					max = Math.max(max, Math.max(handle1, handle2));
				}
			}
			if (!Float.isFinite(min) || !Float.isFinite(max)) return new float[] {-1f, 1f};
			float margin = Math.abs(max - min) < 0.000001f
				? Math.max(1f, Math.abs(max) * 0.15f) : (max - min) * 0.12f;
			return new float[] {min - margin, max + margin};
		}

		private float[] curveDisplayValueBounds(Array<Track> display) {
			float min = Float.POSITIVE_INFINITY;
			float max = Float.NEGATIVE_INFINITY;
			for (Track track : display) {
				Animation.Timeline timeline = track.timeline;
				int channels = AnimationEditor.curveChannelCount(timeline);
				for (int channel = 0; channel < channels; channel++) {
					for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
						float value = animEditor.curveFrameValue(timeline, frame, channel);
						min = Math.min(min, value);
						max = Math.max(max, value);
					}
					for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
						float[] handles = curveHandles(timeline, frame, channel);
						if (handles == null) continue;
						min = Math.min(min, Math.min(handles[1], handles[3]));
						max = Math.max(max, Math.max(handles[1], handles[3]));
					}
				}
			}
			if (!Float.isFinite(min) || !Float.isFinite(max)) return new float[] {-1f, 1f};
			float margin = Math.abs(max - min) < 0.000001f
				? Math.max(1f, Math.abs(max) * 0.15f) : (max - min) * 0.12f;
			return new float[] {min - margin, max + margin};
		}

		private float[] curveHandles(Animation.Timeline timeline, int segment, int channel) {
			if (segment < 0 || segment >= timeline.getFrameCount() - 1
				|| animEditor.curveType(timeline, segment) < Animation.CurveTimeline.BEZIER) return null;
			if (timeline == curveDragTimeline && segment == curvePreviewSegment
				&& channel == curveSelectedChannel && curveDragHandle != 0)
				return new float[] {curvePreviewX1, curvePreviewY1, curvePreviewX2, curvePreviewY2};
			try {
				AnimationEditor.BezierHandles curve = animEditor.bezierHandles(timeline, segment, channel);
				return curve == null ? null
					: new float[] {curve.cx1(), curve.value1(), curve.cx2(), curve.value2()};
			} catch (RuntimeException ignored) {
				return null;
			}
		}

		private float[] curveHandlePoint(Animation.Timeline timeline, int segment, int control, boolean local) {
			float[] handles = curveHandles(timeline, segment, curveSelectedChannel);
			if (handles == null) return null;
			float time1 = animEditor.frameTime(timeline, segment);
			float time2 = animEditor.frameTime(timeline, segment + 1);
			int offset = control == 1 ? 0 : 2;
			float time = time1 + (time2 - time1) * handles[offset];
			float value = handles[offset + 1];
			float[] bounds = curveValueBounds(timeline);
			float x = xAtTime(time);
			float y = curveY(value, bounds);
			return local ? new float[] {x, y} : new float[] {drawOrigin.x + x, drawOrigin.y + y};
		}

		private Color curveColor(int channel) { return curveColors[Math.floorMod(channel, curveColors.length)]; }

		private Color curveColor(int trackIndex, int channel) {
			// Keep channels of one track adjacent while giving each track a stable hue.
			return curveColors[Math.floorMod(trackIndex * 3 + channel, curveColors.length)];
		}

		private int curveColorIndex(Track track) {
			for (int index = 0; index < tracks.size; index++)
				if (sameTrack(tracks.get(index), track)) return index;
			return 0;
		}

		private Color curveColor(Track track, int channel) {
			return curveColor(curveColorIndex(track), channel);
		}

		private float curveTickStep(float range) {
			double raw = Math.max(0.000001d, range / 5d);
			double magnitude = Math.pow(10d, Math.floor(Math.log10(raw)));
			double normalized = raw / magnitude;
			double multiplier = normalized <= 1d ? 1d : normalized <= 2d ? 2d
				: normalized <= 5d ? 5d : 10d;
			return (float)(multiplier * magnitude);
		}

		private float cubic(float start, float control1, float control2, float end, float t) {
			float inverse = 1f - t;
			return inverse * inverse * inverse * start + 3f * inverse * inverse * t * control1
				+ 3f * inverse * t * t * control2 + t * t * t * end;
		}

		private float square(float value) { return value * value; }

		private void drawCurveValueLabels(Batch batch) {
			Array<Track> display = curveDisplayTracks();
			if (display.isEmpty()) return;
			float[] bounds = curveDisplayValueBounds(display);
			float step = curveTickStep(bounds[1] - bounds[0]);
			float first = (float)Math.ceil(bounds[0] / step - 0.000001f) * step;
			Color oldBatchColor = new Color(batch.getColor());
			Color oldFontColor = new Color(font.getColor());
			Color labelBackground = kit.palette().color(UiAppearance.Role.TIMELINE_PLOT);
			float horizontalPadding = 3f;
			float verticalPadding = 2f;
			font.setColor(kit.palette().mutedText());
			try {
				for (int index = 0; index < 1000; index++) {
					float value = first + index * step;
					if (value > bounds[1] + step * 0.000001f) break;
					String text = UiNumbers.format(value);
					curveValueLayout.setText(font, text);
					float y = drawOrigin.y + curveY(value, bounds);
					float right = drawOrigin.x + viewportRightX() - 7f;
					float labelX = right - curveValueLayout.width;
					batch.setColor(labelBackground);
					batch.draw(kit.white.getRegion(), labelX - horizontalPadding,
						y - curveValueLayout.height / 2f - verticalPadding,
						curveValueLayout.width + horizontalPadding * 2f,
						curveValueLayout.height + verticalPadding * 2f);
					batch.setColor(Color.WHITE);
					font.draw(batch, curveValueLayout, labelX,
						centeredTextBaseline(y, curveValueLayout.height));
				}
			} finally {
				batch.setColor(oldBatchColor);
				font.setColor(oldFontColor);
			}
		}

		private void drawCurveHierarchy(Batch batch) {
			float fixedX = drawOrigin.x + horizontalScroll();
			Animation animation = currentAnim();
			Array<CurveHierarchyGroup> groups = curveHierarchyGroups();
			font.setColor(groups.isEmpty() ? kit.palette().mutedText() : kit.palette().text());
			drawCurveHierarchyLabel(batch, fixedX + 24f, 0,
				animation == null ? "动画" : "动画 · " + animation.getName(), labelWidth() - 32f);
			if (groups.isEmpty()) {
				font.setColor(kit.palette().mutedText());
				drawCurveHierarchyLabel(batch, fixedX + 40f, 1, "未选择连续数值轨道", labelWidth() - 48f);
				return;
			}
			float curveRowHeight = curveRowHeight();
			int row = 1;
			for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
				CurveHierarchyGroup group = groups.get(groupIndex);
				float groupCenter = drawOrigin.y + curveHierarchyTop() - row * curveRowHeight;
				font.setColor(kit.palette().text());
				drawDisclosure(batch, fixedX + 10f, groupCenter, curveGroupCollapsed(group));
				drawCurveHierarchyLabel(batch, fixedX + 40f, row, group.label, labelWidth() - 48f);
				row++;
				if (curveGroupCollapsed(group)) continue;
				for (int trackIndex = 0; trackIndex < group.tracks.size; trackIndex++) {
					Track track = group.tracks.get(trackIndex);
					Color trackText = track == curveTargetTrack ? kit.palette().text() : kit.palette().mutedText();
					font.setColor(trackText.r, trackText.g, trackText.b,
						curveTrackVisible(track) ? 1f : 0.42f);
					drawCurveHierarchyLabel(batch, fixedX + 57f, row, curveGraphTrackLabel(track), labelWidth() - 65f);
					row++;
					String[] labels = curveChannelLabels(track.timeline);
					for (int channel = 0; channel < labels.length; channel++, row++) {
						float center = drawOrigin.y + curveHierarchyTop() - row * curveRowHeight;
						Color color = curveColor(track, channel);
						batch.setColor(color);
						batch.draw(kit.white.getRegion(), fixedX + 62f, center - 4f, 8f, 8f);
						batch.setColor(Color.WHITE);
						Color channelText = track == curveTargetTrack && channel == curveSelectedChannel
							? kit.palette().text() : kit.palette().mutedText();
						font.setColor(channelText.r, channelText.g, channelText.b,
							curveTrackVisible(track) ? 1f : 0.42f);
						drawCurveHierarchyLabel(batch, fixedX + 76f, row, labels[channel], labelWidth() - 84f);
					}
				}
			}
		}

		private Color curveHierarchyKindColor(CurveHierarchyKind kind) {
			return switch (kind) {
			case BONE -> curveColors[2];
			case SLOT -> curveColors[0];
			case IK -> curveColors[0];
			case TRANSFORM -> curveColors[4];
			case PATH -> curveColors[3];
			case PHYSICS -> curveColors[4];
			case SLIDER -> curveColors[5];
			case EVENT, DRAW_ORDER -> curveColors[3];
			case UNKNOWN -> kit.palette().mutedText();
			};
		}

		private Color curveTrackIconColor(Track track) {
			if (track == null) return kit.palette().mutedText();
			if (track.bone != null && track.prop != null) return switch (track.prop) {
			case ROTATE -> curveColors[1];
			case TRANSLATE_X, TRANSLATE_Y, TRANSLATE -> curveColors[2];
			case SCALE_X, SCALE_Y, SCALE -> curveColors[3];
			case SHEAR_X, SHEAR_Y, SHEAR -> curveColors[4];
			};
			if (track.isIk()) return curveColors[0];
			if (track.isTransform()) return curveColors[4];
			if (track.isPath()) return curveColors[3];
			if (track.isPhysics()) return curveColors[4];
			if (track.isSlider()) return curveColors[5];
			if (track.isSlot() || track.isSequence() || track.isDeform()) return curveColors[0];
			return curveHierarchyKindColor(curveGroupKind(track));
		}

		private void drawCurveHierarchyGuidesAndIcons(float fixedX) {
			Array<CurveHierarchyGroup> groups = curveHierarchyGroups();
			if (groups.isEmpty()) return;
			float top = drawOrigin.y + curveHierarchyTop();
			float rowHeight = curveRowHeight();
			float groupX = fixedX + 27f;
			float trackX = fixedX + 46f;
			float channelX = fixedX + 66f;

			// The thin branches are intentionally behind the glyphs, matching the
			// compact tree treatment used by Spine's graph sidebar.
			shapes.begin(ShapeRenderer.ShapeType.Line);
			Color guideColor = kit.palette().mutedText();
			shapes.setColor(guideColor.r, guideColor.g, guideColor.b, 0.42f);
			int row = 1;
			for (CurveHierarchyGroup group : groups) {
				float groupCenter = top - row * rowHeight;
				row++;
				if (curveGroupCollapsed(group)) continue;
				float lastChildCenter = groupCenter;
				for (Track track : group.tracks) {
					float trackCenter = top - row * rowHeight;
					row++;
					shapes.line(groupX, trackCenter, trackX, trackCenter);
					lastChildCenter = trackCenter;
					int channels = Math.max(1, AnimationEditor.curveChannelCount(track.timeline));
					float lastChannelCenter = trackCenter;
					for (int channel = 0; channel < channels; channel++) {
						float center = top - row * rowHeight;
						shapes.line(trackX, center, channelX, center);
						lastChannelCenter = center;
						row++;
					}
					shapes.line(trackX, trackCenter - rowHeight * 0.18f, trackX,
						lastChannelCenter);
					lastChildCenter = lastChannelCenter;
				}
				shapes.line(groupX, groupCenter - rowHeight * 0.18f, groupX, lastChildCenter);
			}
			shapes.end();

			shapes.begin(ShapeRenderer.ShapeType.Filled);
			float animationCenter = top;
			shapes.setColor(0.18f, 0.72f, 0.73f, 1f);
			shapes.circle(fixedX + 11f, animationCenter, 4f, 12);
			row = 1;
			for (CurveHierarchyGroup group : groups) {
				float groupCenter = top - row * rowHeight;
				shapes.setColor(curveHierarchyKindColor(group.kind));
				drawCurveHierarchyObjectIcon(groupX, groupCenter, group.kind);
				row++;
				if (curveGroupCollapsed(group)) continue;
				for (Track track : group.tracks) {
					float trackCenter = top - row * rowHeight;
					drawCurveHierarchyTrackIcon(trackX, trackCenter, track);
					row++;
					row += Math.max(1, AnimationEditor.curveChannelCount(track.timeline));
				}
			}
			shapes.end();
		}

		private void drawCurveHierarchyObjectIcon(float cx, float cy, CurveHierarchyKind kind) {
			switch (kind) {
			case BONE -> diamond(cx, cy, 4.5f);
			case IK -> {
				shapes.circle(cx, cy, 4.5f, 12);
				shapes.rect(cx - 1f, cy - 5.5f, 2f, 11f);
			}
			case TRANSFORM -> shapes.triangle(cx - 5f, cy - 4f, cx + 5f, cy, cx - 5f, cy + 4f);
			case PATH -> shapes.rect(cx - 4.5f, cy - 4.5f, 9f, 9f);
			case PHYSICS -> shapes.circle(cx, cy, 4.5f, 12);
			case SLIDER -> shapes.rect(cx - 5f, cy - 2f, 10f, 4f);
			case SLOT -> shapes.rect(cx - 4.5f, cy - 4f, 9f, 8f);
			case EVENT, DRAW_ORDER -> shapes.triangle(cx, cy + 5f, cx + 5f, cy - 4f, cx - 5f, cy - 4f);
			case UNKNOWN -> shapes.rect(cx - 3f, cy - 3f, 6f, 6f);
			}
		}

		private void drawCurveHierarchyTrackIcon(float cx, float cy, Track track) {
			shapes.setColor(curveTrackIconColor(track));
			if (track != null && track.bone != null && track.prop != null) {
				switch (track.prop) {
				case ROTATE -> {
					shapes.circle(cx, cy, 4.5f, 12);
					shapes.rect(cx + 2f, cy + 2f, 3f, 2f);
				}
				case TRANSLATE_X, TRANSLATE_Y, TRANSLATE -> {
					shapes.rect(cx - 5f, cy - 1f, 10f, 2f);
					shapes.rect(cx - 1f, cy - 5f, 2f, 10f);
				}
				case SCALE_X, SCALE_Y, SCALE -> diamond(cx, cy, 4.5f);
				case SHEAR_X, SHEAR_Y, SHEAR ->
					shapes.triangle(cx - 5f, cy - 4f, cx + 5f, cy - 4f, cx - 2f, cy + 5f);
				}
				return;
			}
			if (track != null && track.isIk()) {
				shapes.triangle(cx - 5f, cy - 4f, cx + 5f, cy, cx - 5f, cy + 4f);
			} else if (track != null && track.isInherit()) {
				diamond(cx, cy, 4f);
			} else {
				shapes.rect(cx - 4f, cy - 4f, 8f, 8f);
			}
		}

		private void drawCurveHierarchyLabel(Batch batch, float x, int row, String text, float width) {
			trackLabelLayout.setText(font, text, 0, text.length(), font.getColor(), width,
				com.badlogic.gdx.utils.Align.left, false, "...");
			float center = drawOrigin.y + curveHierarchyTop() - row * curveRowHeight();
			font.draw(batch, trackLabelLayout, x,
				timelineHierarchyLabelBaseline(center, trackLabelLayout.height, font.getData().scaleY));
		}

		private void drawCurveHierarchyMarkers(float fixedX) {
			float markerX = fixedX + labelWidth() - 12f;
			float top = drawOrigin.y + curveHierarchyTop();
			float curveRowHeight = curveRowHeight();
			shapes.setColor(0.28f, 0.72f, 0.73f, 1f);
			shapes.circle(markerX, top, 3f, 12);
			int row = 1;
			Array<CurveHierarchyGroup> groups = curveHierarchyGroups();
			for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
				CurveHierarchyGroup group = groups.get(groupIndex);
				Color groupColor = curveHierarchyKindColor(group.kind);
				shapes.setColor(groupColor.r, groupColor.g, groupColor.b, 0.72f);
				shapes.circle(markerX, top - row++ * curveRowHeight, 3f, 12);
				if (curveGroupCollapsed(group)) continue;
				for (int trackIndex = 0; trackIndex < group.tracks.size; trackIndex++) {
					Track track = group.tracks.get(trackIndex);
					Color trackColor = curveTrackIconColor(track);
					float markerAlpha = !curveTrackVisible(track) ? 0.24f
						: track == curveTargetTrack ? 1f : 0.72f;
					shapes.setColor(trackColor.r, trackColor.g, trackColor.b, markerAlpha);
					shapes.circle(markerX, top - row++ * curveRowHeight, 3f, 12);
					int channels = AnimationEditor.curveChannelCount(track.timeline);
					for (int channel = 0; channel < channels; channel++) {
						Color color = curveColor(track, channel);
						shapes.setColor(color.r, color.g, color.b,
							!curveTrackVisible(track) ? 0.20f
								: track == curveTargetTrack && channel == curveSelectedChannel ? 1f : 0.72f);
						shapes.circle(markerX, top - row++ * curveRowHeight, 2.6f, 12);
					}
				}
			}
		}

		private void drawFixedChrome() {
			float fixedX = drawOrigin.x + horizontalScroll();
			float rulerY = drawOrigin.y + rulerBottom();
			shapes.begin(ShapeRenderer.ShapeType.Filled);
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_RULER));
			shapes.rect(drawOrigin.x, rulerY, getWidth(), RULER_H);
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_SIDEBAR));
			shapes.rect(fixedX, drawOrigin.y, labelWidth(), getHeight());
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_GUTTER));
			shapes.rect(fixedX + labelWidth(), drawOrigin.y, TIMELINE_LEFT_GUTTER, getHeight());
			if (curveView && !curveHierarchyGroups().isEmpty()) {
				float animationCenter = drawOrigin.y + curveHierarchyTop();
				float curveRowHeight = curveRowHeight();
				shapes.setColor(kit.palette().color(UiAppearance.Role.TREE_SELECTION));
				shapes.rect(fixedX, animationCenter - curveRowHeight / 2f, labelWidth(), curveRowHeight);
				int row = 1;
				Array<CurveHierarchyGroup> groups = curveHierarchyGroups();
				for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
					CurveHierarchyGroup group = groups.get(groupIndex);
					float groupCenter = animationCenter - row++ * curveRowHeight;
					shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_ROW_ALT));
					shapes.rect(fixedX, groupCenter - curveRowHeight / 2f, labelWidth(), curveRowHeight);
					if (curveGroupCollapsed(group)) continue;
					for (int trackIndex = 0; trackIndex < group.tracks.size; trackIndex++) {
						Track track = group.tracks.get(trackIndex);
						float trackCenter = animationCenter - row++ * curveRowHeight;
						shapes.setColor(track == curveTargetTrack
							? kit.palette().color(UiAppearance.Role.TIMELINE_ROW_ALT)
							: kit.palette().color(UiAppearance.Role.TIMELINE_SIDEBAR));
						shapes.rect(fixedX, trackCenter - curveRowHeight / 2f, labelWidth(), curveRowHeight);
						int channels = AnimationEditor.curveChannelCount(track.timeline);
						for (int channel = 0; channel < channels; channel++) {
							float center = animationCenter - row++ * curveRowHeight;
							if (track == curveTargetTrack && channel == curveSelectedChannel) {
								shapes.setColor(kit.palette().color(UiAppearance.Role.TREE_SELECTION));
								shapes.rect(fixedX, center - curveRowHeight / 2f, labelWidth(), curveRowHeight);
							}
						}
					}
				}
				drawCurveHierarchyMarkers(fixedX);
			}
			shapes.end();
			if (curveView) drawCurveHierarchyGuidesAndIcons(fixedX);

			shapes.begin(ShapeRenderer.ShapeType.Line);
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_DIVIDER));
			shapes.line(fixedX + labelWidth(), drawOrigin.y,
				fixedX + labelWidth(), drawOrigin.y + getHeight());
			shapes.setColor(kit.palette().color(UiAppearance.Role.BORDER));
			float boundaryX = fixedX + timelineBoundaryX(labelWidth());
			shapes.line(boundaryX, drawOrigin.y, boundaryX, drawOrigin.y + getHeight());
			float fps = frameRate();
			int majorFrameStep = rulerFrameStep(pixelsPerSecond(), fps);
			int minorFrameStep = rulerMinorFrameStep(majorFrameStep);
			double firstFrame = firstRulerFrame(visibleStartTime(), fps, minorFrameStep);
			double endFrame = visibleEndTime() * fps;
			for (int index = 0; index < 10000; index++) {
				double frame = firstFrame + (double)index * minorFrameStep;
				if (frame > endFrame + 0.000001d) break;
				float localX = xAtTime((float)(frame / fps));
				if (!timelineSpanVisible(localX, localX, timelineBoundary(), viewportRightX())) continue;
				boolean major = isMajorRulerFrame(frame, majorFrameStep);
				shapes.setColor(major ? kit.palette().mutedText()
					: kit.palette().color(UiAppearance.Role.BORDER));
				float x = drawOrigin.x + localX;
				shapes.line(x, rulerY, x, rulerY + (major ? 10f : 6f));
			}
			shapes.setColor(kit.palette().color(UiAppearance.Role.TIMELINE_DIVIDER));
			int fixedRows = curveView ? curveHierarchyRows() : timelineHierarchyRows();
			float fixedRowHeight = curveView ? curveRowHeight() : rowHeight();
			for (int i = 0; i <= fixedRows; i++) {
				float y = drawOrigin.y + getHeight() - RULER_H - i * fixedRowHeight;
				shapes.line(fixedX, y, fixedX + labelWidth(), y);
			}
			shapes.end();

			shapes.begin(ShapeRenderer.ShapeType.Filled);
			drawRulerKeyMarkers(rulerY);
			drawPlayheadHandle(rulerY);
			shapes.end();
		}

		private void drawRulerKeyMarkers(float rulerY) {
			Animation animation = currentAnim();
			if (animation == null) return;
			float fps = frameRate();
			long firstVisible = Math.max(0L, (long)Math.floor(visibleStartTime() * fps));
			long lastVisible = Math.max(firstVisible, (long)Math.ceil(visibleEndTime() * fps));
			rulerKeyFrames.clear();
			for (Track track : tracks) {
				if (!curveView && track.isAnyDrawOrder()) continue;
				Animation.Timeline timeline = track.timeline;
				if (timeline == null) continue;
				for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
					long projectFrame = Math.round(animEditor.frameTime(timeline, frame) * fps);
					if (projectFrame >= firstVisible && projectFrame <= lastVisible)
						rulerKeyFrames.add(projectFrame);
				}
			}
			shapes.setColor(0.96f, 0.29f, 0.12f, 1f);
			for (long projectFrame : rulerKeyFrames) {
				float localX = xAtTime(projectFrame / fps);
				if (!timelineSpanVisible(localX - 4f, localX + 4f,
					timelineBoundary(), viewportRightX())) continue;
				float x = drawOrigin.x + localX;
				diamond(x, rulerY + 5f, 4f);
			}
		}

		private void drawPlayheadHandle(float rulerY) {
			float localX = xAtTime(doc.getCurrentTime());
			if (!timelineSpanVisible(localX - 5f, localX + 5f,
				timelineBoundary(), viewportRightX())) return;
			float x = drawOrigin.x + localX;
			float baseY = rulerY + RULER_H - 22f;
			shapes.setColor(0.05f, 0.84f, 0.93f, 1f);
			shapes.triangle(x - 5f, baseY, x + 5f, baseY, x, baseY - 8f);
		}

		private void drawRulerLabels(Batch batch) {
			font.setColor(kit.palette().mutedText());
			float fps = frameRate();
			long currentFrame = Math.round(doc.getCurrentTime() * fps);
			int frameStep = rulerFrameStep(pixelsPerSecond(), fps);
			double firstFrame = firstRulerFrame(visibleStartTime(), fps, frameStep);
			double endFrame = visibleEndTime() * fps;
			for (int index = 0; index < 1000; index++) {
				double frame = firstFrame + (double)index * frameStep;
				if (frame > endFrame + 0.000001d) break;
				if (Math.round(frame) == currentFrame) continue;
				String label = rulerFrameLabel(frame);
				float markerX = drawOrigin.x + xAtTime((float)(frame / fps));
				float x = centeredRulerLabelX(label, markerX);
				if (timelineSpanVisible(x, x + rulerLabelLayout.width,
					drawOrigin.x + timelineBoundary(), drawOrigin.x + viewportRightX()))
					font.draw(batch, rulerLabelLayout, x,
						rulerLabelBaseline(drawOrigin.y + rulerBottom() + RULER_H, false));
			}
			font.setColor(0.05f, 0.84f, 0.93f, 1f);
			String currentLabel = Long.toString(currentFrame);
			float playheadX = drawOrigin.x + xAtTime(doc.getCurrentTime());
			float currentX = centeredRulerLabelX(currentLabel, playheadX);
			if (timelineSpanVisible(currentX, currentX + rulerLabelLayout.width,
				drawOrigin.x + timelineBoundary(), drawOrigin.x + viewportRightX()))
				font.draw(batch, rulerLabelLayout, currentX,
					rulerLabelBaseline(drawOrigin.y + rulerBottom() + RULER_H, true));
		}

		private float centeredRulerLabelX(String text, float centerX) {
			rulerLabelLayout.setText(font, text);
			float scaleX = font.getData().scaleX;
			float visualLeft = Float.POSITIVE_INFINITY;
			float visualRight = Float.NEGATIVE_INFINITY;
			for (GlyphLayout.GlyphRun run : rulerLabelLayout.runs) {
				float glyphX = run.x;
				for (int index = 0; index < run.glyphs.size; index++) {
					glyphX += run.xAdvances.get(index);
					BitmapFont.Glyph glyph = run.glyphs.get(index);
					float left = glyphX + glyph.xoffset * scaleX;
					float visibleWidth = rulerGlyphVisualWidth(glyph.width, glyph.xadvance, scaleX);
					visualLeft = Math.min(visualLeft, left);
					visualRight = Math.max(visualRight, left + visibleWidth);
				}
			}
			if (!Float.isFinite(visualLeft) || !Float.isFinite(visualRight))
				return centerX - rulerLabelLayout.width / 2f;
			return centeredVisualLabelX(centerX, visualLeft, visualRight);
		}

		private boolean isMajorRulerFrame(double frame, int majorFrameStep) {
			long roundedFrame = Math.round(frame);
			return Math.floorMod(roundedFrame, (long)Math.max(1, majorFrameStep)) == 0L;
		}

		private void drawTrackLabels(Batch batch) {
			float fixedX = drawOrigin.x + horizontalScroll();
			float rowHeight = rowHeight();
			font.setColor(kit.palette().text());
			Animation animation = currentAnim();
			float animationCenter = drawOrigin.y + getHeight() - RULER_H - rowHeight / 2f;
			drawTimelineLabel(batch, fixedX + 6f, animationCenter,
				animation == null ? "动画" : "动画 · " + animation.getName(), labelWidth() - 12f);
			int row = 1;
			Array<CurveHierarchyGroup> groups = timelineHierarchyGroups();
			for (int groupIndex = 0; groupIndex < groups.size; groupIndex++) {
				CurveHierarchyGroup group = groups.get(groupIndex);
				float groupCenter = drawOrigin.y + getHeight() - RULER_H
					- (row * rowHeight + rowHeight / 2f);
				font.setColor(kit.palette().text());
				drawDisclosure(batch, fixedX + 10f, groupCenter, curveGroupCollapsed(group));
				drawTimelineLabel(batch, fixedX + 19f, groupCenter, timelineGroupLabel(group), labelWidth() - 27f);
				row++;
				if (curveGroupCollapsed(group)) continue;
				for (int trackIndex = 0; trackIndex < group.tracks.size; trackIndex++) {
					Track track = group.tracks.get(trackIndex);
					float center = drawOrigin.y + getHeight() - RULER_H
						- (row * rowHeight + rowHeight / 2f);
					font.setColor(trackLabelSelected(track) ? kit.palette().text()
						: kit.palette().mutedText());
					drawTimelineLabel(batch, fixedX + 32f, center, curveTrackLabel(track), labelWidth() - 40f);
					row++;
				}
			}
		}

		private boolean trackLabelSelected(Track track) {
			Track selected = trackForTimeline(selectedTimelineIgnoringVisibility());
			if (selected == null) selected = curveTargetTrack;
			return sameTrack(track, selected);
		}

		private void drawTimelineLabel(Batch batch, float x, float center, String text, float width) {
			trackLabelLayout.setText(font, text, 0, text.length(), font.getColor(), width,
				com.badlogic.gdx.utils.Align.left, false, "...");
			font.draw(batch, trackLabelLayout, x,
				timelineHierarchyLabelBaseline(center, trackLabelLayout.height, font.getData().scaleY));
		}

		private void drawDisclosure(Batch batch, float cx, float cy, boolean collapsed) {
			batch.setColor(kit.palette().mutedText());
			batch.draw(kit.white.getRegion(), cx - 4f, cy - 1f, 8f, 2f);
			if (collapsed) batch.draw(kit.white.getRegion(), cx - 1f, cy - 4f, 2f, 8f);
			batch.setColor(Color.WHITE);
		}

		private void diamond(float cx, float cy, float r) {
			shapes.triangle(cx, cy + r, cx + r, cy, cx, cy - r);
			shapes.triangle(cx - r, cy, cx, cy + r, cx, cy - r);
		}

		void dispose() {
			shapes.dispose();
		}
	}
}
