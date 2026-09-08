package com.openspine.model;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import com.esotericsoftware.spine.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Constraint;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slider;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SliderPose;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.Update;

/**
 * 文档模型：持有当前骨架、运行时状态、图集与用户选择。
 * 文档的渲染将由 EditorViewport 消费；UI 面板通过本对象读写。
 */
public class SkeletonDocument {
	public static final float MIN_PROJECT_FPS = 1f;
	public static final float MAX_PROJECT_FPS = 1000f;

	public TextureAtlas atlas;
	public Skeleton skeleton;
	public SkeletonData data;
	public AnimationState animationState;
	public String filePath;
	public String atlasFileName;
	/** Directory used to resolve runtime JSON image and audio paths. */
	public String resourceDirectory;
	/** Whether the loaded atlas pages contain premultiplied-alpha pixels. */
	public boolean premultipliedAlpha;
	/** JSON or binary skeleton file associated with Save; built-in and new documents start unassociated. */
	public String savePath;
	/** Cleanup for source assets that must remain available while this document is open. */
	private Runnable sourceResourceCleanup;
	/** Atlas instances retained by reversible resource edits, disposed with the document. */
	private final Set<TextureAtlas> historyAtlases = Collections.newSetFromMap(new IdentityHashMap<>());

	private Bone selectedBone;
	/** Primary selection plus the additional bones selected with Ctrl/Shift in the hierarchy. */
	private final LinkedHashSet<Bone> selectedBones = new LinkedHashSet<>();
	private SelectionKind selectionKind = SelectionKind.BONE;
	private ConstraintData<?, ?> selectedConstraint;
	private String selectedConstraintRelation = "";
	private String selectedSlotName = "";
	private String selectedAttachmentPlaceholder = "";
	private String selectedAttachmentName = "";
	private final LinkedHashSet<Bone> constraintSelectionBones = new LinkedHashSet<>();
	/** Editor-only bone display/selection flags. Runtime JSON has no Select or Name fields. */
	private final LinkedHashMap<String, BoneEditorState> boneEditorStates = new LinkedHashMap<>();

	/** 总动画时长(秒)，用于时间线滑块上限 */
	public float maxTime = 1f;

	/** 编辑器姿态模式：开启时暂停动画，允许直接编辑/摆姿势 */
	public boolean poseMode = false;
	/** 是否正在播放动画 */
	public boolean playing = false;
	/** 实时动画模式下的播放速度 */
	public float playbackSpeed = 1f;
	/** 当前动画是否循环播放 */
	public boolean looping = true;
	/** 当前文档是否包含尚未保存的编辑 */
	public boolean dirty;
	/** Editor-only Physics preview switches, matching Spine's Simulate and Deterministic controls. */
	private boolean physicsSimulationEnabled = true;
	private boolean physicsDeterministic;
	/** Temporary viewport evaluation switch. It never changes exported constraint data. */
	private boolean constraintsEnabled = true;
	private boolean autoSelectAnimation = true;
	/** Exact duration shown until the first playback tick after enabling a looping track at its end. */
	private float loopEndDisplayTime = Float.NaN;
	private PhysicsSimulationCursor physicsSimulationCursor;
	private static final int PHYSICS_STEPS_PER_TICK = 2048;
	private static final long PHYSICS_NANOS_PER_TICK = 4_000_000L;
	private final LinkedHashSet<String> axisSeparationPreferences = new LinkedHashSet<>();
	private final LinkedHashMap<String, AxisCombineState> axisCombineStates = new LinkedHashMap<>();
	private final LinkedHashMap<String, GraphViewState> graphViewStates = new LinkedHashMap<>();
	private final LinkedHashMap<String, Set<MeshWeightLock>> meshWeightLocks = new LinkedHashMap<>();
	private final LinkedHashMap<AnimationMixKey, Float> animationMixes = new LinkedHashMap<>();
	private final LinkedHashSet<AnimationMixKey> animationMixOverrides = new LinkedHashSet<>();
	private float defaultAnimationMix;
	private TreeViewState treeViewState = TreeViewState.empty();
	private TimelineViewState timelineViewState = TimelineViewState.empty();
	private GraphWorkspaceState graphWorkspaceState = GraphWorkspaceState.empty();
	private PsdSource psdSource;
	/** 文档内容修订号，供 UI 避免无意义的全量刷新 */
	public long revision;

	/** Stable link from one PSD pixel layer to the authoring objects created for it. */
	public record PsdLayerMapping(int sourceIndex, String sourceName, String regionName,
		String skinName, String slotName, String placeholder, boolean visible) {
		public PsdLayerMapping {
			if (sourceIndex < 0) throw new IllegalArgumentException("PSD source layer index must be non-negative");
			sourceName = requirePsdValue(sourceName, "PSD source layer name");
			regionName = requirePsdValue(regionName, "PSD atlas region name");
			skinName = requirePsdValue(skinName, "PSD target skin name");
			slotName = requirePsdValue(slotName, "PSD target slot name");
			placeholder = requirePsdValue(placeholder, "PSD attachment placeholder");
		}
	}

	/** Editor-only PSD source metadata used by save/reopen and future synchronization. */
	public record PsdSource(String sourcePath, boolean ignoreHiddenLayers, int canvasWidth,
		int canvasHeight, List<PsdLayerMapping> layers) {
		public PsdSource {
			sourcePath = requirePsdValue(sourcePath, "PSD source path");
			if (canvasWidth < 1 || canvasHeight < 1) throw new IllegalArgumentException("PSD canvas size is invalid");
			layers = layers == null ? List.of() : List.copyOf(layers);
			if (layers.isEmpty()) throw new IllegalArgumentException("PSD source has no installed layers");
			Set<Integer> indices = new java.util.HashSet<>();
			for (PsdLayerMapping layer : layers)
				if (layer == null || !indices.add(layer.sourceIndex()))
					throw new IllegalArgumentException("PSD source layer mapping is null or duplicated");
		}
	}

	private static String requirePsdValue(String value, String label) {
		if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label + " is required");
		return value.trim();
	}

	public record GraphViewState(float timeMin, float timeMax, float valueMin, float valueMax,
		int selectedChannel, long visibleChannels, boolean selectionStored,
		float[] selectedTimes, int[] selectedChannels, boolean editTime, boolean editValue,
		boolean snap, boolean autoFrame, boolean separateHandles, int transformMode) {
		public GraphViewState(float timeMin, float timeMax, float valueMin, float valueMax,
			int selectedChannel, long visibleChannels) {
			this(timeMin, timeMax, valueMin, valueMax, selectedChannel, visibleChannels,
				false, new float[0], new int[0], true, true, true, false, false, 0);
		}

		public GraphViewState(float timeMin, float timeMax, float valueMin, float valueMax,
			int selectedChannel, long visibleChannels, boolean selectionStored,
			float[] selectedTimes, int[] selectedChannels) {
			this(timeMin, timeMax, valueMin, valueMax, selectedChannel, visibleChannels,
				selectionStored, selectedTimes, selectedChannels, true, true, true, false, false, 0);
		}

		public GraphViewState {
			selectedTimes = selectedTimes == null ? new float[0] : selectedTimes.clone();
			selectedChannels = selectedChannels == null ? new int[0] : selectedChannels.clone();
			if (selectedTimes.length != selectedChannels.length)
				throw new IllegalArgumentException("Graph selection arrays must have the same length");
			transformMode = Math.max(0, Math.min(2, transformMode));
		}

		@Override public float[] selectedTimes() { return selectedTimes.clone(); }
		@Override public int[] selectedChannels() { return selectedChannels.clone(); }
	}
	public record AxisCombineState(String animation, String bone, String group, boolean xBeforeY,
		float[] xFrames, float[] xCurves, float[] yFrames, float[] yCurves,
		float[] combinedFrames, float[] combinedCurves) {
		public AxisCombineState {
			animation = animation == null ? "" : animation;
			bone = bone == null ? "" : bone;
			group = group == null ? "" : group;
			xFrames = cloneFloats(xFrames);
			xCurves = cloneFloats(xCurves);
			yFrames = cloneFloats(yFrames);
			yCurves = cloneFloats(yCurves);
			combinedFrames = cloneFloats(combinedFrames);
			combinedCurves = cloneFloats(combinedCurves);
		}

		private static float[] cloneFloats(float[] values) {
			return values == null ? new float[0] : values.clone();
		}

		@Override public float[] xFrames() { return xFrames.clone(); }
		@Override public float[] xCurves() { return xCurves.clone(); }
		@Override public float[] yFrames() { return yFrames.clone(); }
		@Override public float[] yCurves() { return yCurves.clone(); }
		@Override public float[] combinedFrames() { return combinedFrames.clone(); }
		@Override public float[] combinedCurves() { return combinedCurves.clone(); }
	}
	public record TreeViewState(String filter, float scrollX, float scrollY,
		Set<String> expandedValues, Set<String> treeValues) {
		public TreeViewState {
			filter = filter == null ? "" : filter;
			scrollX = Float.isFinite(scrollX) ? Math.max(0f, scrollX) : 0f;
			scrollY = Float.isFinite(scrollY) ? Math.max(0f, scrollY) : 0f;
			expandedValues = cleanTreeValues(expandedValues);
			treeValues = cleanTreeValues(treeValues);
		}

		public static TreeViewState empty() { return new TreeViewState("", 0f, 0f, Set.of(), Set.of()); }

		private static Set<String> cleanTreeValues(Set<String> values) {
			if (values == null || values.isEmpty()) return Set.of();
			LinkedHashSet<String> clean = new LinkedHashSet<>();
			for (String value : values) if (value != null && !value.isBlank()) clean.add(value);
			return Set.copyOf(clean);
		}
	}
	public record BoneEditorState(String bone, boolean selectable, boolean nameVisible) {
		public BoneEditorState {
			bone = bone == null ? "" : bone.trim();
		}
	}
	public record AnimationMix(String from, String to, float duration) {
		public AnimationMix {
			from = from == null ? "" : from.trim();
			to = to == null ? "" : to.trim();
			if (from.isEmpty() || to.isEmpty() || from.equals(to)
				|| !Float.isFinite(duration) || duration < 0f)
				throw new IllegalArgumentException("动画混合设置无效");
		}
	}
	private record AnimationMixKey(String from, String to) {}
	public record GraphWorkspaceState(boolean open, String animation, String filter, boolean locked,
		String detailTrackKey, float splitAmount, float scrollX, float scrollY,
		float x, float y, float width, float height) {
		public GraphWorkspaceState {
			animation = animation == null ? "" : animation;
			filter = filter == null ? "" : filter;
			detailTrackKey = detailTrackKey == null ? "" : detailTrackKey;
			splitAmount = Float.isFinite(splitAmount)
				? Math.max(0.28f, Math.min(0.72f, splitAmount)) : 0.62f;
			scrollX = Float.isFinite(scrollX) ? Math.max(0f, scrollX) : 0f;
			scrollY = Float.isFinite(scrollY) ? Math.max(0f, scrollY) : 0f;
			x = Float.isFinite(x) ? x : -1f;
			y = Float.isFinite(y) ? y : -1f;
			width = Float.isFinite(width) ? Math.max(0f, width) : 0f;
			height = Float.isFinite(height) ? Math.max(0f, height) : 0f;
		}

		public static GraphWorkspaceState empty() {
			return new GraphWorkspaceState(false, "", "", false, "", 0.62f,
				0f, 0f, -1f, -1f, 0f, 0f);
		}
	}
	public record TimelineViewState(String animation, boolean curveView, int curveSelectedChannel,
		String curveTrackKey, float scrollX, float scrollY, boolean selectionStored,
		String[] selectedTrackKeys, float[] selectedTimes, int[] selectedEventOccurrences) {
		public TimelineViewState {
			animation = animation == null ? "" : animation;
			curveSelectedChannel = Math.max(0, curveSelectedChannel);
			curveTrackKey = curveTrackKey == null ? "" : curveTrackKey;
			scrollX = Float.isFinite(scrollX) ? Math.max(0f, scrollX) : 0f;
			scrollY = Float.isFinite(scrollY) ? Math.max(0f, scrollY) : 0f;
			selectedTrackKeys = selectedTrackKeys == null ? new String[0] : selectedTrackKeys.clone();
			selectedTimes = selectedTimes == null ? new float[0] : selectedTimes.clone();
			selectedEventOccurrences = selectedEventOccurrences == null
				? new int[0] : selectedEventOccurrences.clone();
			if (selectedTrackKeys.length != selectedTimes.length
				|| selectedTrackKeys.length != selectedEventOccurrences.length)
				throw new IllegalArgumentException("Timeline selection arrays must have the same length");
			for (int index = 0; index < selectedTrackKeys.length; index++)
				if (selectedTrackKeys[index] == null || selectedTrackKeys[index].isBlank()
					|| !Float.isFinite(selectedTimes[index]) || selectedTimes[index] < 0f
					|| selectedEventOccurrences[index] < 0)
					throw new IllegalArgumentException("Timeline selection contains an invalid key");
		}

		public static TimelineViewState empty() {
			return new TimelineViewState("", true, 0, "", 0f, 0f, false,
				new String[0], new float[0], new int[0]);
		}

		@Override public String[] selectedTrackKeys() { return selectedTrackKeys.clone(); }
		@Override public float[] selectedTimes() { return selectedTimes.clone(); }
		@Override public int[] selectedEventOccurrences() { return selectedEventOccurrences.clone(); }
	}
	public record MeshWeightLock(int vertex, int boneIndex) {
		public MeshWeightLock {
			if (vertex < 0 || boneIndex < 0) throw new IllegalArgumentException("Mesh weight lock indices must be non-negative");
		}
	}
	public enum SelectionKind { BONE, SLOT, ATTACHMENT, CONSTRAINT }

	private static final class PhysicsSimulationCursor {
		final Skeleton skeleton;
		final Animation animation;
		final AnimationState simulation;
		final float step;
		long completedSteps;
		long targetSteps;
		float targetTime;

		PhysicsSimulationCursor(Skeleton skeleton, Animation animation, AnimationState simulation, float step) {
			this.skeleton = skeleton;
			this.animation = animation;
			this.simulation = simulation;
			this.step = step;
		}
	}

	private final Vector2 tmp = new Vector2();
	private Skin combinedPreviewSkin;
	private List<String> previewSkinNames = List.of();

	public void dispose() {
		invalidatePhysicsSimulation();
		disposeAtlases(atlas, null);
		releaseSourceResources();
		atlas = null;
		skeleton = null;
		data = null;
		animationState = null;
		combinedPreviewSkin = null;
		previewSkinNames = List.of();
		selectedBone = null;
		selectedBones.clear();
		clearEditorSelection();
		filePath = null;
		atlasFileName = null;
		resourceDirectory = null;
		premultipliedAlpha = false;
		loopEndDisplayTime = Float.NaN;
		savePath = null;
		axisSeparationPreferences.clear();
		boneEditorStates.clear();
		axisCombineStates.clear();
		graphViewStates.clear();
		meshWeightLocks.clear();
		animationMixes.clear();
		animationMixOverrides.clear();
		defaultAnimationMix = 0f;
		treeViewState = TreeViewState.empty();
		timelineViewState = TimelineViewState.empty();
		graphWorkspaceState = GraphWorkspaceState.empty();
		psdSource = null;
		dirty = false;
		revision++;
	}

	/** 成功加载后一次性替换文档内容，旧图集只在新数据完整可用后释放。 */
	public void replaceContent(TextureAtlas newAtlas, SkeletonData newData, Skeleton newSkeleton,
		String newFilePath, String newAtlasFileName, String newSavePath) {
		replaceContent(newAtlas, newData, newSkeleton, newFilePath, newAtlasFileName, newSavePath, false);
	}

	public void replaceContent(TextureAtlas newAtlas, SkeletonData newData, Skeleton newSkeleton,
		String newFilePath, String newAtlasFileName, String newSavePath, boolean newPremultipliedAlpha) {
		invalidatePhysicsSimulation();
		TextureAtlas oldAtlas = atlas;
		Runnable oldSourceResourceCleanup = sourceResourceCleanup;
		atlas = newAtlas;
		data = newData;
		skeleton = newSkeleton;
		combinedPreviewSkin = null;
		previewSkinNames = List.of();
		animationState = null;
		filePath = newFilePath;
		atlasFileName = newAtlasFileName;
		resourceDirectory = null;
		premultipliedAlpha = newPremultipliedAlpha;
		savePath = newSavePath;
		sourceResourceCleanup = null;
		selectedBone = null;
		selectedBones.clear();
		clearEditorSelection();
		poseMode = false;
		playing = false;
		playbackSpeed = 1f;
		looping = true;
		loopEndDisplayTime = Float.NaN;
		physicsSimulationEnabled = true;
		physicsDeterministic = false;
		constraintsEnabled = true;
		autoSelectAnimation = true;
		axisSeparationPreferences.clear();
		boneEditorStates.clear();
		axisCombineStates.clear();
		graphViewStates.clear();
		meshWeightLocks.clear();
		animationMixes.clear();
		animationMixOverrides.clear();
		defaultAnimationMix = 0f;
		treeViewState = TreeViewState.empty();
		timelineViewState = TimelineViewState.empty();
		graphWorkspaceState = GraphWorkspaceState.empty();
		psdSource = null;
		maxTime = 1f;
		dirty = false;
		revision++;
		disposeAtlases(oldAtlas, newAtlas);
		runCleanup(oldSourceResourceCleanup);
	}

	/** Keeps an atlas alive while an undo entry may switch back to it. */
	public void retainAtlasForHistory(TextureAtlas retained) {
		if (retained != null) historyAtlases.add(retained);
	}

	/** Keeps converted or extracted source assets alive until this document is replaced or disposed. */
	public void ownSourceResources(Runnable cleanup) {
		if (cleanup == null) throw new IllegalArgumentException("Source resource cleanup is required");
		if (sourceResourceCleanup == cleanup) return;
		releaseSourceResources();
		sourceResourceCleanup = cleanup;
	}

	/**
	 * Atomically adopts a fully initialized loaded document. The source is detached so disposing it
	 * cannot release the atlas now owned by this document.
	 */
	public void adoptLoadedContentFrom(SkeletonDocument source) {
		if (source == null || source == this)
			throw new IllegalArgumentException("候选文档无效");
		if (source.data == null || source.skeleton == null || source.animationState == null)
			throw new IllegalArgumentException("候选文档尚未完成初始化");

		invalidatePhysicsSimulation();
		source.invalidatePhysicsSimulation();
		TextureAtlas oldAtlas = atlas;
		Set<TextureAtlas> adoptedHistoryAtlases = Collections.newSetFromMap(new IdentityHashMap<>());
		adoptedHistoryAtlases.addAll(source.historyAtlases);
		Runnable oldSourceResourceCleanup = sourceResourceCleanup;
		atlas = source.atlas;
		skeleton = source.skeleton;
		data = source.data;
		animationState = source.animationState;
		combinedPreviewSkin = source.combinedPreviewSkin;
		previewSkinNames = source.previewSkinNames;
		filePath = source.filePath;
		atlasFileName = source.atlasFileName;
		resourceDirectory = source.resourceDirectory;
		premultipliedAlpha = source.premultipliedAlpha;
		savePath = source.savePath;
		sourceResourceCleanup = source.sourceResourceCleanup;
		selectedBone = source.selectedBone;
		selectedBones.clear();
		selectedBones.addAll(source.selectedBones);
		selectionKind = source.selectionKind;
		selectedConstraint = source.selectedConstraint;
		selectedConstraintRelation = source.selectedConstraintRelation;
		selectedSlotName = source.selectedSlotName;
		selectedAttachmentPlaceholder = source.selectedAttachmentPlaceholder;
		selectedAttachmentName = source.selectedAttachmentName;
		constraintSelectionBones.clear();
		constraintSelectionBones.addAll(source.constraintSelectionBones);
		maxTime = source.maxTime;
		poseMode = source.poseMode;
		playing = source.playing;
		playbackSpeed = source.playbackSpeed;
		looping = source.looping;
		loopEndDisplayTime = source.loopEndDisplayTime;
		physicsSimulationEnabled = source.physicsSimulationEnabled;
		physicsDeterministic = source.physicsDeterministic;
		constraintsEnabled = source.constraintsEnabled;
		autoSelectAnimation = source.autoSelectAnimation;
		axisSeparationPreferences.clear();
		axisSeparationPreferences.addAll(source.axisSeparationPreferences);
		boneEditorStates.clear();
		boneEditorStates.putAll(source.boneEditorStates);
		axisCombineStates.clear();
		axisCombineStates.putAll(source.axisCombineStates);
		graphViewStates.clear();
		graphViewStates.putAll(source.graphViewStates);
		meshWeightLocks.clear();
		meshWeightLocks.putAll(source.meshWeightLocks);
		animationMixes.clear();
		animationMixes.putAll(source.animationMixes);
		animationMixOverrides.clear();
		animationMixOverrides.addAll(source.animationMixOverrides);
		defaultAnimationMix = source.defaultAnimationMix;
		treeViewState = source.treeViewState;
		timelineViewState = source.timelineViewState;
		graphWorkspaceState = source.graphWorkspaceState;
		psdSource = source.psdSource;
		dirty = source.dirty;
		revision++;

		source.atlas = null;
		source.skeleton = null;
		source.data = null;
		source.loopEndDisplayTime = Float.NaN;
		source.animationState = null;
		source.combinedPreviewSkin = null;
		source.previewSkinNames = List.of();
		source.filePath = null;
		source.atlasFileName = null;
		source.resourceDirectory = null;
		source.premultipliedAlpha = false;
		source.savePath = null;
		source.sourceResourceCleanup = null;
		source.historyAtlases.clear();
		source.selectedBone = null;
		source.selectedBones.clear();
		source.clearEditorSelection();
		source.physicsSimulationEnabled = true;
		source.physicsDeterministic = false;
		source.constraintsEnabled = true;
		source.autoSelectAnimation = true;
		source.axisSeparationPreferences.clear();
		source.boneEditorStates.clear();
		source.axisCombineStates.clear();
		source.graphViewStates.clear();
		source.meshWeightLocks.clear();
		source.animationMixes.clear();
		source.animationMixOverrides.clear();
		source.defaultAnimationMix = 0f;
		source.treeViewState = TreeViewState.empty();
		source.timelineViewState = TimelineViewState.empty();
		source.graphWorkspaceState = GraphWorkspaceState.empty();
		source.psdSource = null;
		source.dirty = false;
		source.revision++;
		disposeAtlases(oldAtlas, atlas);
		historyAtlases.addAll(adoptedHistoryAtlases);
		runCleanup(oldSourceResourceCleanup);
	}

	private void disposeAtlases(TextureAtlas current, TextureAtlas keep) {
		if (current != null) historyAtlases.add(current);
		if (keep != null) historyAtlases.remove(keep);
		for (TextureAtlas owned : historyAtlases) owned.dispose();
		historyAtlases.clear();
	}

	private void releaseSourceResources() {
		Runnable cleanup = sourceResourceCleanup;
		sourceResourceCleanup = null;
		runCleanup(cleanup);
	}

	private static void runCleanup(Runnable cleanup) {
		if (cleanup != null) cleanup.run();
	}

	public void markDirty() {
		invalidatePhysicsSimulation();
		dirty = true;
		revision++;
	}

	public void markSaved() {
		dirty = false;
		revision++;
	}

	void setDirtyFromHistory(boolean value) {
		if (dirty == value) return;
		dirty = value;
		revision++;
	}

	public static boolean isValidProjectFps(float fps) {
		return Float.isFinite(fps) && fps >= MIN_PROJECT_FPS && fps <= MAX_PROJECT_FPS;
	}

	/** Updates the project time base without resampling keyframe times. */
	public boolean setProjectFps(float fps) {
		if (data == null || !isValidProjectFps(fps)) return false;
		if (Float.compare(data.getFps(), fps) == 0) return true;
		data.setFps(fps);
		markDirty();
		return true;
	}

	/** Runtime pose changed without modifying exportable SkeletonData. */
	public void markPoseChanged() {
		invalidatePhysicsSimulation();
		revision++;
	}

	/** 播放模式下更新动画状态并应用到骨骼 */
	public void applyAnimation(float delta) {
		if (animationState == null || skeleton == null) return;
		if (advancePendingPhysicsSimulation()) return;
		if (poseMode || !playing) return; // 姿态模式或暂停时不应用动画
		float elapsed = delta * playbackSpeed;
		if (!Float.isFinite(elapsed) || elapsed < 0f) return;
		loopEndDisplayTime = Float.NaN;
		syncTrackLoopMode();
		animationState.update(elapsed);
		if (!physicsSimulationEnabled) {
			skeleton.update(elapsed);
			skeleton.setupPose();
			animationState.apply(skeleton);
			updateWorldTransform(Physics.pose);
		} else if (physicsDeterministic && hasPhysicsConstraints()) {
			requestPhysicsSimulation(currentTrackTime());
		} else {
			invalidatePhysicsSimulation();
			skeleton.update(elapsed);
			animationState.apply(skeleton);
			updateWorldTransform(Physics.update);
		}
	}

	public boolean isPhysicsSimulationEnabled() { return physicsSimulationEnabled; }

	public boolean isPhysicsDeterministic() { return physicsDeterministic; }

	public boolean areConstraintsEnabled() { return constraintsEnabled; }

	/** Enables or bypasses every runtime constraint while preserving its authored pose and timeline values. */
	public boolean setConstraintsEnabled(boolean enabled) {
		if (constraintsEnabled == enabled) return false;
		constraintsEnabled = enabled;
		invalidatePhysicsSimulation();
		if (animationState != null && skeleton != null && animationState.getTrack(0) != null && !poseMode)
			seek(currentTrackTime());
		else if (skeleton != null) updateWorldTransform(Physics.reset);
		revision++;
		return true;
	}

	public boolean setPhysicsSimulationEnabled(boolean enabled) {
		if (physicsSimulationEnabled == enabled) return false;
		physicsSimulationEnabled = enabled;
		if (!enabled) invalidatePhysicsSimulation();
		if (animationState != null && skeleton != null && animationState.getTrack(0) != null)
			seek(currentTrackTime());
		else if (skeleton != null) updateWorldTransform(Physics.reset);
		revision++;
		return true;
	}

	public boolean setPhysicsDeterministic(boolean enabled) {
		if (physicsDeterministic == enabled) return false;
		physicsDeterministic = enabled;
		if (physicsSimulationEnabled && animationState != null && skeleton != null
			&& animationState.getTrack(0) != null) seek(currentTrackTime());
		revision++;
		return true;
	}

	public boolean isAxisSeparationPreferred(String animation, String bone, String group) {
		return axisSeparationPreferences.contains(editorStateKey(animation, bone, group));
	}

	public void setAxisSeparationPreferred(String animation, String bone, String group, boolean separated) {
		String key = editorStateKey(animation, bone, group);
		if (separated ? axisSeparationPreferences.add(key) : axisSeparationPreferences.remove(key)) revision++;
	}

	public Set<String> axisSeparationPreferences() { return Set.copyOf(axisSeparationPreferences); }

	public void restoreAxisSeparationPreferences(Iterable<String> preferences) {
		axisSeparationPreferences.clear();
		if (preferences != null)
			for (String preference : preferences)
				if (preference != null && !preference.isBlank()) axisSeparationPreferences.add(preference);
		revision++;
	}

	public AxisCombineState axisCombineState(String animation, String bone, String group) {
		return axisCombineStates.get(editorStateKey(animation, bone, group));
	}

	public void setAxisCombineState(AxisCombineState state) {
		if (state == null || state.animation().isBlank() || state.bone().isBlank() || state.group().isBlank()) return;
		axisCombineStates.put(editorStateKey(state.animation(), state.bone(), state.group()), state);
	}

	public void removeAxisCombineState(String animation, String bone, String group) {
		axisCombineStates.remove(editorStateKey(animation, bone, group));
	}

	public Map<String, AxisCombineState> axisCombineStates() { return Map.copyOf(axisCombineStates); }

	public void restoreAxisCombineStates(Iterable<AxisCombineState> states) {
		axisCombineStates.clear();
		if (states != null) for (AxisCombineState state : states) setAxisCombineState(state);
		revision++;
	}

	public GraphViewState graphViewState(String key) { return graphViewStates.get(key); }

	public void setGraphViewState(String key, GraphViewState state) {
		if (key != null && !key.isBlank() && state != null) graphViewStates.put(key, state);
	}

	public void removeGraphViewState(String key) {
		if (key != null) graphViewStates.remove(key);
	}

	public void migrateGraphViewState(String animation, Animation.Timeline oldTimeline,
		Animation.Timeline newTimeline) {
		if (oldTimeline == null || newTimeline == null) return;
		String oldKey = graphViewStateKey(animation, oldTimeline);
		String newKey = graphViewStateKey(animation, newTimeline);
		if (oldKey.equals(newKey)) return;
		GraphViewState state = graphViewStates.remove(oldKey);
		if (state != null) graphViewStates.put(newKey, state);
	}

	public void migrateGraphViewStates(String animation,
		Map<? extends Animation.Timeline, ? extends Animation.Timeline> replacements) {
		if (replacements == null || replacements.isEmpty()) return;
		LinkedHashMap<String, GraphViewState> moved = new LinkedHashMap<>();
		for (Map.Entry<? extends Animation.Timeline, ? extends Animation.Timeline> entry : replacements.entrySet()) {
			Animation.Timeline oldTimeline = entry.getKey(), newTimeline = entry.getValue();
			if (oldTimeline == null || newTimeline == null) continue;
			String oldKey = graphViewStateKey(animation, oldTimeline);
			GraphViewState state = graphViewStates.get(oldKey);
			if (state != null) moved.put(graphViewStateKey(animation, newTimeline), state);
		}
		for (Animation.Timeline oldTimeline : replacements.keySet())
			if (oldTimeline != null) graphViewStates.remove(graphViewStateKey(animation, oldTimeline));
		graphViewStates.putAll(moved);
	}

	public void removeTimelineEditorState(String animation, Animation.Timeline timeline) {
		if (timeline == null) return;
		removeGraphViewState(graphViewStateKey(animation, timeline));
	}

	public Map<String, GraphViewState> graphViewStates() { return Map.copyOf(graphViewStates); }

	public void restoreGraphViewStates(Map<String, GraphViewState> states) {
		graphViewStates.clear();
		if (states != null) graphViewStates.putAll(states);
		revision++;
	}

	public static String meshWeightStateKey(String skin, String slot, String placeholder) {
		return editorStateKey(skin, slot, placeholder);
	}

	public Set<MeshWeightLock> meshWeightLocks(String key) {
		Set<MeshWeightLock> locks = meshWeightLocks.get(key);
		return locks == null ? Set.of() : locks;
	}

	public Map<String, Set<MeshWeightLock>> meshWeightLockStates() { return Map.copyOf(meshWeightLocks); }

	public void setMeshWeightLocks(String key, Iterable<MeshWeightLock> locks) {
		if (key == null || key.isBlank()) return;
		LinkedHashSet<MeshWeightLock> clean = new LinkedHashSet<>();
		if (locks != null) for (MeshWeightLock lock : locks) if (lock != null) clean.add(lock);
		if (clean.isEmpty()) meshWeightLocks.remove(key);
		else meshWeightLocks.put(key, Set.copyOf(clean));
		revision++;
	}

	public void restoreMeshWeightLocks(Map<String, ? extends Iterable<MeshWeightLock>> states) {
		meshWeightLocks.clear();
		if (states != null) for (Map.Entry<String, ? extends Iterable<MeshWeightLock>> entry : states.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.isBlank()) continue;
			LinkedHashSet<MeshWeightLock> clean = new LinkedHashSet<>();
			Iterable<MeshWeightLock> values = entry.getValue();
			if (values != null) for (MeshWeightLock lock : values) if (lock != null) clean.add(lock);
			if (!clean.isEmpty()) meshWeightLocks.put(key, Set.copyOf(clean));
		}
		revision++;
	}

	public TreeViewState treeViewState() { return treeViewState; }

	public void setTreeViewState(TreeViewState state) {
		treeViewState = state == null ? TreeViewState.empty() : state;
	}

	public TimelineViewState timelineViewState() { return timelineViewState; }

	public void setTimelineViewState(TimelineViewState state) {
		timelineViewState = state == null ? TimelineViewState.empty() : state;
	}

	public GraphWorkspaceState graphWorkspaceState() { return graphWorkspaceState; }

	public void setGraphWorkspaceState(GraphWorkspaceState state) {
		graphWorkspaceState = state == null ? GraphWorkspaceState.empty() : state;
	}

	public PsdSource psdSource() { return psdSource; }

	/** Changes the managed PSD source as an authoring edit. */
	public boolean setPsdSource(PsdSource source) {
		if (Objects.equals(psdSource, source)) return false;
		psdSource = source;
		markDirty();
		return true;
	}

	/** Restores PSD metadata from persistence or an enclosing undo transaction. */
	public void restorePsdSource(PsdSource source) {
		if (Objects.equals(psdSource, source)) return;
		psdSource = source;
		revision++;
	}

	public float defaultAnimationMix() { return defaultAnimationMix; }

	public List<AnimationMix> animationMixes() {
		ArrayList<AnimationMix> values = new ArrayList<>(animationMixes.size());
		for (Map.Entry<AnimationMixKey, Float> entry : animationMixes.entrySet())
			values.add(new AnimationMix(entry.getKey().from(), entry.getKey().to(), entry.getValue()));
		values.sort(Comparator.comparing(AnimationMix::from).thenComparing(AnimationMix::to));
		return List.copyOf(values);
	}

	/** Applies editor preview transition settings. Runtime skeleton formats do not store these values. */
	public boolean setAnimationMixSettings(float defaultMix, Iterable<AnimationMix> mixes) {
		return updateAnimationMixSettings(defaultMix, mixes, true);
	}

	/** Restores persisted preview transition settings without creating a new unsaved edit. */
	public boolean restoreAnimationMixSettings(float defaultMix, Iterable<AnimationMix> mixes) {
		return updateAnimationMixSettings(defaultMix, mixes, false);
	}

	private boolean updateAnimationMixSettings(float defaultMix, Iterable<AnimationMix> mixes, boolean dirty) {
		if (!Float.isFinite(defaultMix) || defaultMix < 0f)
			throw new IllegalArgumentException("默认动画混合时间必须是不小于 0 的有限数值");
		LinkedHashMap<AnimationMixKey, Float> requested = new LinkedHashMap<>();
		if (mixes != null) for (AnimationMix mix : mixes) {
			if (mix == null) throw new IllegalArgumentException("动画混合设置不能为空");
			if (data == null || data.findAnimation(mix.from()) == null || data.findAnimation(mix.to()) == null)
				throw new IllegalArgumentException("动画混合引用了不存在的动画");
			requested.put(new AnimationMixKey(mix.from(), mix.to()), mix.duration());
		}
		if (Float.compare(defaultAnimationMix, defaultMix) == 0 && animationMixes.equals(requested)) return false;

		AnimationStateData stateData = animationState == null ? null : animationState.getData();
		animationMixOverrides.addAll(animationMixes.keySet());
		animationMixOverrides.addAll(requested.keySet());
		defaultAnimationMix = defaultMix;
		animationMixes.clear();
		animationMixes.putAll(requested);
		if (stateData != null && data != null) for (AnimationMixKey previous : animationMixOverrides) {
			Animation from = data.findAnimation(previous.from());
			Animation to = data.findAnimation(previous.to());
			if (from != null && to != null) stateData.setMix(from, to, defaultMix);
		}
		applyAnimationMixSettings(stateData);
		if (dirty) markDirty();
		return true;
	}

	public AnimationState newAnimationState() {
		if (data == null) throw new IllegalStateException("骨架数据不能为空");
		AnimationStateData stateData = new AnimationStateData(data);
		applyAnimationMixSettings(stateData);
		return new AnimationState(stateData);
	}

	private void applyAnimationMixSettings(AnimationStateData stateData) {
		if (stateData == null) return;
		stateData.setDefaultMix(defaultAnimationMix);
		if (data == null) return;
		for (Map.Entry<AnimationMixKey, Float> entry : animationMixes.entrySet()) {
			Animation from = data.findAnimation(entry.getKey().from());
			Animation to = data.findAnimation(entry.getKey().to());
			if (from != null && to != null) stateData.setMix(from, to, entry.getValue());
		}
	}

	public void renameMeshWeightStateToken(int tokenIndex, String oldValue, String newValue) {
		if (tokenIndex < 0 || tokenIndex > 2 || oldValue == null || newValue == null || oldValue.equals(newValue)) return;
		LinkedHashMap<String, Set<MeshWeightLock>> renamed = new LinkedHashMap<>();
		for (Map.Entry<String, Set<MeshWeightLock>> entry : meshWeightLocks.entrySet()) {
			String[] tokens = entry.getKey().split("\\u001f", -1);
			if (tokens.length == 3 && tokens[tokenIndex].equals(oldValue)) tokens[tokenIndex] = newValue;
			renamed.put(tokens.length == 3 ? String.join("\u001f", tokens) : entry.getKey(), entry.getValue());
		}
		meshWeightLocks.clear();
		meshWeightLocks.putAll(renamed);
	}

	public void removeMeshWeightState(String skin, String slot, String placeholder) {
		meshWeightLocks.remove(meshWeightStateKey(skin, slot, placeholder));
	}

	public void removeMeshWeightStatesForToken(int tokenIndex, String value) {
		if (tokenIndex < 0 || tokenIndex > 2 || value == null) return;
		meshWeightLocks.keySet().removeIf(key -> {
			String[] tokens = key.split("\\u001f", -1);
			return tokens.length == 3 && tokens[tokenIndex].equals(value);
		});
	}

	public void removeMeshWeightLocksAtOrAfterBone(int firstBoneIndex) {
		if (firstBoneIndex < 0) return;
		for (String key : new java.util.ArrayList<>(meshWeightLocks.keySet())) {
			LinkedHashSet<MeshWeightLock> retained = new LinkedHashSet<>();
			for (MeshWeightLock lock : meshWeightLocks.get(key))
				if (lock.boneIndex() < firstBoneIndex) retained.add(lock);
			if (retained.isEmpty()) meshWeightLocks.remove(key);
			else meshWeightLocks.put(key, Set.copyOf(retained));
		}
	}

	public static String graphViewStateKey(String animation, Animation.Timeline timeline) {
		if (timeline == null) throw new IllegalArgumentException("Graph state timeline is required");
		return (animation == null ? "" : animation) + "|" + timeline.getClass().getName() + "|"
			+ Arrays.toString(timeline.getPropertyIds());
	}

	public void renameEditorAnimationState(String oldName, String newName) {
		if (oldName == null || newName == null || oldName.equals(newName)) return;
		remapAxisPreferenceToken(0, oldName, newName);
		remapAxisCombineToken(0, oldName, newName);
		String oldPrefix = oldName + "|", newPrefix = newName + "|";
		LinkedHashMap<String, GraphViewState> renamed = new LinkedHashMap<>();
		for (Map.Entry<String, GraphViewState> entry : graphViewStates.entrySet()) {
			String key = entry.getKey();
			renamed.put(key.startsWith(oldPrefix) ? newPrefix + key.substring(oldPrefix.length()) : key,
				entry.getValue());
		}
		graphViewStates.clear();
		graphViewStates.putAll(renamed);
		if (timelineViewState.animation().equals(oldName))
			timelineViewState = new TimelineViewState(newName, timelineViewState.curveView(),
				timelineViewState.curveSelectedChannel(), timelineViewState.curveTrackKey(),
				timelineViewState.scrollX(), timelineViewState.scrollY(), timelineViewState.selectionStored(),
				timelineViewState.selectedTrackKeys(), timelineViewState.selectedTimes(),
				timelineViewState.selectedEventOccurrences());
		if (selectedConstraintRelation.equals("animation:" + oldName))
			selectedConstraintRelation = "animation:" + newName;
		remapTreeViewValues(value -> remapTreeRelation(value, "animation:" + oldName,
			"animation:" + newName));
		if (graphWorkspaceState.animation().equals(oldName))
			graphWorkspaceState = new GraphWorkspaceState(graphWorkspaceState.open(), newName,
				graphWorkspaceState.filter(), graphWorkspaceState.locked(), graphWorkspaceState.detailTrackKey(),
				graphWorkspaceState.splitAmount(), graphWorkspaceState.scrollX(), graphWorkspaceState.scrollY(),
				graphWorkspaceState.x(), graphWorkspaceState.y(), graphWorkspaceState.width(),
				graphWorkspaceState.height());
		remapAnimationMixes(oldName, newName);
	}

	public void removeEditorAnimationState(String animation) {
		if (animation == null) return;
		removeAxisPreferenceToken(0, animation);
		removeAxisCombineToken(0, animation);
		String prefix = animation + "|";
		graphViewStates.keySet().removeIf(key -> key != null && key.startsWith(prefix));
		if (timelineViewState.animation().equals(animation)) timelineViewState = TimelineViewState.empty();
		if (selectedConstraintRelation.equals("animation:" + animation)) selectedConstraintRelation = "";
		removeTreeViewValues(value -> value != null && value.startsWith("constraint-relation:")
			&& value.endsWith(":animation:" + animation));
		if (graphWorkspaceState.animation().equals(animation))
			graphWorkspaceState = GraphWorkspaceState.empty();
		animationMixOverrides.removeIf(key -> key.from().equals(animation) || key.to().equals(animation));
		if (animationMixes.keySet().removeIf(key -> key.from().equals(animation) || key.to().equals(animation)))
			applyAnimationMixSettings(animationState == null ? null : animationState.getData());
	}

	private void remapAnimationMixes(String oldName, String newName) {
		if (animationMixes.isEmpty()) return;
		LinkedHashMap<AnimationMixKey, Float> remapped = new LinkedHashMap<>();
		for (Map.Entry<AnimationMixKey, Float> entry : animationMixes.entrySet()) {
			AnimationMixKey key = entry.getKey();
			remapped.put(new AnimationMixKey(key.from().equals(oldName) ? newName : key.from(),
				key.to().equals(oldName) ? newName : key.to()), entry.getValue());
		}
		animationMixes.clear();
		animationMixes.putAll(remapped);
		LinkedHashSet<AnimationMixKey> remappedOverrides = new LinkedHashSet<>();
		for (AnimationMixKey key : animationMixOverrides)
			remappedOverrides.add(new AnimationMixKey(key.from().equals(oldName) ? newName : key.from(),
				key.to().equals(oldName) ? newName : key.to()));
		animationMixOverrides.clear();
		animationMixOverrides.addAll(remappedOverrides);
		applyAnimationMixSettings(animationState == null ? null : animationState.getData());
	}

	public void renameEditorBoneState(String oldName, String newName) {
		if (oldName == null || newName == null || oldName.equals(newName)) return;
		BoneEditorState boneState = boneEditorStates.remove(oldName);
		if (boneState != null) boneEditorStates.put(newName,
			new BoneEditorState(newName, boneState.selectable(), boneState.nameVisible()));
		remapAxisPreferenceToken(1, oldName, newName);
		remapAxisCombineToken(1, oldName, newName);
		remapTimelineTrackState(1, oldName, newName, "bone");
		selectedConstraintRelation = remapConstraintBoneRelation(
			selectedConstraint, selectedConstraintRelation, oldName, newName);
		remapTreeViewValues(value -> remapTreeBoneValue(value, oldName, newName));
		remapGraphWorkspaceTrackState(1, oldName, newName, "bone");
	}

	private static String remapConstraintBoneRelation(ConstraintData<?, ?> constraint, String relation,
		String oldName, String newName) {
		if (constraint == null || relation == null || relation.isEmpty()) return relation == null ? "" : relation;
		if (constraint instanceof IkConstraintData ik) {
			if (ik.getTarget() != null && ik.getTarget().getName().equals(newName)
				&& relation.equals("target:" + oldName)) return "target:" + newName;
			for (BoneData bone : ik.getBones())
				if (bone.getName().equals(newName) && relation.equals("constrained:" + oldName))
					return "constrained:" + newName;
		} else if (constraint instanceof TransformConstraintData transform) {
			if (transform.getSource() != null && transform.getSource().getName().equals(newName)
				&& relation.equals("source:" + oldName)) return "source:" + newName;
			for (BoneData bone : transform.getBones())
				if (bone.getName().equals(newName) && relation.equals("target:" + oldName))
					return "target:" + newName;
		} else if (constraint instanceof PathConstraintData path) {
			for (BoneData bone : path.getBones())
				if (bone.getName().equals(newName) && relation.equals("constrained:" + oldName))
					return "constrained:" + newName;
		} else if (constraint instanceof PhysicsConstraintData physics) {
			if (physics.getBone() != null && physics.getBone().getName().equals(newName)
				&& relation.equals("physics:" + oldName)) return "physics:" + newName;
		} else if (constraint instanceof SliderData slider) {
			String sourcePrefix = "source:" + oldName + ":";
			if (slider.getBone() != null && slider.getBone().getName().equals(newName)
				&& relation.startsWith(sourcePrefix))
				return "source:" + newName + ":" + relation.substring(sourcePrefix.length());
			if (relation.equals("animation-bone:" + oldName)) return "animation-bone:" + newName;
		}
		return relation;
	}

	public void removeEditorBoneState(String bone) {
		if (bone == null) return;
		boneEditorStates.remove(bone);
		removeAxisPreferenceToken(1, bone);
		removeAxisCombineToken(1, bone);
		removeTimelineTrackState(1, bone, "bone");
		removeGraphWorkspaceTrackState(1, bone, "bone");
		removeTreeViewValues(value -> treeValueReferencesBone(value, bone));
	}

	public void renameEditorSlotState(String oldName, String newName) {
		if (oldName == null || newName == null || oldName.equals(newName)) return;
		remapTimelineTrackState(1, oldName, newName, "slot", "sequence", "deform");
		if ((selectionKind == SelectionKind.SLOT || selectionKind == SelectionKind.ATTACHMENT)
			&& selectedSlotName.equals(oldName)) selectedSlotName = newName;
		if (selectedConstraintRelation.equals("slot:" + oldName))
			selectedConstraintRelation = "slot:" + newName;
		remapTreeViewValues(value -> remapTreeSlotValue(value, oldName, newName));
		remapGraphWorkspaceTrackState(1, oldName, newName, "slot", "sequence", "deform");
		remapPsdSource(null, oldName, newName);
	}

	public void removeEditorSlotState(String slot) {
		if (slot == null) return;
		removeTimelineTrackState(1, slot, "slot", "sequence", "deform");
		removeGraphWorkspaceTrackState(1, slot, "slot", "sequence", "deform");
		removeTreeViewValues(value -> treeValueReferencesSlot(value, slot));
		if ((selectionKind == SelectionKind.SLOT || selectionKind == SelectionKind.ATTACHMENT)
			&& selectedSlotName.equals(slot)) setSelectedBone(selectedBone);
	}

	public void replaceEditorConstraintState(ConstraintData<?, ?> previous,
		ConstraintData<?, ?> replacement) {
		if (previous == null || replacement == null) return;
		String previousType = timelineConstraintTrackType(previous);
		String replacementType = timelineConstraintTrackType(replacement);
		if (!previousType.isEmpty()) {
			if (previousType.equals(replacementType))
				remapTimelineTrackState(1, previous.getName(), replacement.getName(), previousType);
			else removeTimelineTrackState(1, previous.getName(), previousType);
		}
		if (selectedConstraint == previous) {
			selectedConstraint = replacement;
			if (!constraintRelationExists(replacement, selectedConstraintRelation, data))
				selectedConstraintRelation = "";
		}
		if (previousType.equals(replacementType) && !previousType.isEmpty()) {
			remapTreeViewValues(value -> remapTreeConstraintValue(value,
				constraintTreeType(previous), previous.getName(), replacement.getName()));
			remapGraphWorkspaceTrackState(1, previous.getName(), replacement.getName(), previousType);
		} else if (!previousType.isEmpty()) {
			removeGraphWorkspaceTrackState(1, previous.getName(), previousType);
			removeTreeViewValues(value -> treeValueReferencesConstraint(value,
				constraintTreeType(previous), previous.getName()));
		}
	}

	public void removeEditorConstraintState(ConstraintData<?, ?> constraint) {
		if (constraint == null) return;
		String type = timelineConstraintTrackType(constraint);
		if (!type.isEmpty()) removeTimelineTrackState(1, constraint.getName(), type);
		if (!type.isEmpty()) removeGraphWorkspaceTrackState(1, constraint.getName(), type);
		removeTreeViewValues(value -> treeValueReferencesConstraint(value,
			constraintTreeType(constraint), constraint.getName()));
		if (selectedConstraint == constraint) setSelectedBone(selectedBone);
	}

	/** Keeps attachment-track selections attached to their skin while a reversible rename replaces Skin objects. */
	public void renameEditorSkinState(String oldName, String newName) {
		if (oldName == null || newName == null || oldName.equals(newName)) return;
		remapTimelineTrackState(4, oldName, newName, "sequence", "deform");
		remapGraphWorkspaceTrackState(4, oldName, newName, "sequence", "deform");
		remapPsdSource(oldName, null, newName);
	}

	private void remapPsdSource(String oldSkin, String oldSlot, String replacement) {
		if (psdSource == null) return;
		boolean changed = false;
		List<PsdLayerMapping> remapped = new ArrayList<>(psdSource.layers().size());
		for (PsdLayerMapping layer : psdSource.layers()) {
			String skin = oldSkin != null && layer.skinName().equals(oldSkin) ? replacement : layer.skinName();
			String slot = oldSlot != null && layer.slotName().equals(oldSlot) ? replacement : layer.slotName();
			changed |= !skin.equals(layer.skinName()) || !slot.equals(layer.slotName());
			remapped.add(new PsdLayerMapping(layer.sourceIndex(), layer.sourceName(), layer.regionName(),
				skin, slot, layer.placeholder(), layer.visible()));
		}
		if (changed) psdSource = new PsdSource(psdSource.sourcePath(), psdSource.ignoreHiddenLayers(),
			psdSource.canvasWidth(), psdSource.canvasHeight(), remapped);
	}

	private void remapGraphWorkspaceTrackState(int tokenIndex, String oldValue, String newValue,
		String... trackTypes) {
		String current = graphWorkspaceState.detailTrackKey();
		String remapped = remapTimelineTrackKey(current, tokenIndex, oldValue, newValue, trackTypes);
		if (current.equals(remapped)) return;
		graphWorkspaceState = new GraphWorkspaceState(graphWorkspaceState.open(),
			graphWorkspaceState.animation(), graphWorkspaceState.filter(), graphWorkspaceState.locked(), remapped,
			graphWorkspaceState.splitAmount(), graphWorkspaceState.scrollX(), graphWorkspaceState.scrollY(),
			graphWorkspaceState.x(), graphWorkspaceState.y(), graphWorkspaceState.width(),
			graphWorkspaceState.height());
	}

	private void removeGraphWorkspaceTrackState(int tokenIndex, String value, String... trackTypes) {
		if (!timelineTrackTokenMatches(graphWorkspaceState.detailTrackKey(), tokenIndex, value, trackTypes)) return;
		graphWorkspaceState = new GraphWorkspaceState(graphWorkspaceState.open(),
			graphWorkspaceState.animation(), graphWorkspaceState.filter(), graphWorkspaceState.locked(), "",
			graphWorkspaceState.splitAmount(), graphWorkspaceState.scrollX(), graphWorkspaceState.scrollY(),
			graphWorkspaceState.x(), graphWorkspaceState.y(), graphWorkspaceState.width(),
			graphWorkspaceState.height());
	}

	private void remapTreeViewValues(UnaryOperator<String> remapper) {
		if (remapper == null) return;
		Set<String> expanded = remapTreeValueSet(treeViewState.expandedValues(), remapper);
		Set<String> values = remapTreeValueSet(treeViewState.treeValues(), remapper);
		treeViewState = new TreeViewState(treeViewState.filter(), treeViewState.scrollX(),
			treeViewState.scrollY(), expanded, values);
	}

	private void removeTreeViewValues(Predicate<String> remove) {
		if (remove == null) return;
		LinkedHashSet<String> expanded = new LinkedHashSet<>(treeViewState.expandedValues());
		LinkedHashSet<String> values = new LinkedHashSet<>(treeViewState.treeValues());
		expanded.removeIf(remove);
		values.removeIf(remove);
		treeViewState = new TreeViewState(treeViewState.filter(), treeViewState.scrollX(),
			treeViewState.scrollY(), expanded, values);
	}

	private static Set<String> remapTreeValueSet(Set<String> values, UnaryOperator<String> remapper) {
		if (values == null || values.isEmpty()) return Set.of();
		LinkedHashSet<String> remapped = new LinkedHashSet<>();
		for (String value : values) remapped.add(remapper.apply(value));
		return Set.copyOf(remapped);
	}

	private static String remapTreeRelation(String value, String oldRelation, String newRelation) {
		if (value == null || !value.startsWith("constraint-relation:")) return value;
		String suffix = ":" + oldRelation;
		return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length())
			+ ":" + newRelation : value;
	}

	private static String remapTreeBoneValue(String value, String oldName, String newName) {
		if (value == null) return null;
		if (value.equals("bone:" + oldName)) return "bone:" + newName;
		for (String relationType : new String[] {"constrained", "target", "source", "physics", "animation-bone"}) {
			String remapped = remapTreeRelation(value, relationType + ":" + oldName,
				relationType + ":" + newName);
			if (!remapped.equals(value)) return remapped;
		}
		if (!value.startsWith("constraint-relation:")) return value;
		String oldSource = ":source:" + oldName + ":";
		int sourceIndex = value.lastIndexOf(oldSource);
		return sourceIndex < 0 ? value : value.substring(0, sourceIndex) + ":source:" + newName + ":"
			+ value.substring(sourceIndex + oldSource.length());
	}

	private static String remapTreeSlotValue(String value, String oldName, String newName) {
		if (value == null) return null;
		if (value.equals("slot:" + oldName)) return "slot:" + newName;
		String attachmentPrefix = "attachment:" + oldName + ":";
		if (value.startsWith(attachmentPrefix))
			return "attachment:" + newName + ":" + value.substring(attachmentPrefix.length());
		return remapTreeRelation(value, "slot:" + oldName, "slot:" + newName);
	}

	private static boolean treeValueReferencesBone(String value, String bone) {
		if (value == null || bone == null) return false;
		if (value.equals("bone:" + bone)) return true;
		for (String relationType : new String[] {"constrained", "target", "source", "physics", "animation-bone"})
			if (value.startsWith("constraint-relation:")
				&& value.endsWith(":" + relationType + ":" + bone)) return true;
		return value.startsWith("constraint-relation:") && value.contains(":source:" + bone + ":");
	}

	private static boolean treeValueReferencesSlot(String value, String slot) {
		if (value == null || slot == null) return false;
		return value.equals("slot:" + slot) || value.startsWith("attachment:" + slot + ":")
			|| value.startsWith("constraint-relation:") && value.endsWith(":slot:" + slot);
	}

	private static boolean treeValueReferencesConstraint(String value, String type, String name) {
		if (value == null || type == null || name == null) return false;
		return value.equals("constraint:" + type + ":" + name)
			|| value.startsWith("constraint-relation:" + type + ":" + name + ":");
	}

	private static String remapTreeConstraintValue(String value, String type, String oldName, String newName) {
		if (value == null) return null;
		String node = "constraint:" + type + ":" + oldName;
		if (value.equals(node)) return "constraint:" + type + ":" + newName;
		String relationPrefix = "constraint-relation:" + type + ":" + oldName + ":";
		return value.startsWith(relationPrefix) ? "constraint-relation:" + type + ":" + newName + ":"
			+ value.substring(relationPrefix.length()) : value;
	}

	private static String constraintTreeType(ConstraintData<?, ?> constraint) {
		if (constraint instanceof IkConstraintData) return "IK";
		if (constraint instanceof TransformConstraintData) return "Transform";
		if (constraint instanceof PathConstraintData) return "Path";
		if (constraint instanceof PhysicsConstraintData) return "Physics";
		return constraint instanceof SliderData ? "Slider" : "Constraint";
	}

	private void remapTimelineTrackState(int tokenIndex, String oldValue, String newValue,
		String... trackTypes) {
		if (oldValue == null || newValue == null || oldValue.equals(newValue)) return;
		String[] trackKeys = timelineViewState.selectedTrackKeys();
		for (int index = 0; index < trackKeys.length; index++)
			trackKeys[index] = remapTimelineTrackKey(trackKeys[index], tokenIndex, oldValue, newValue, trackTypes);
		timelineViewState = new TimelineViewState(timelineViewState.animation(), timelineViewState.curveView(),
			timelineViewState.curveSelectedChannel(),
			remapTimelineTrackKey(timelineViewState.curveTrackKey(), tokenIndex, oldValue, newValue, trackTypes),
			timelineViewState.scrollX(), timelineViewState.scrollY(), timelineViewState.selectionStored(),
			trackKeys, timelineViewState.selectedTimes(), timelineViewState.selectedEventOccurrences());
	}

	private void removeTimelineTrackState(int tokenIndex, String value, String... trackTypes) {
		String[] trackKeys = timelineViewState.selectedTrackKeys();
		float[] times = timelineViewState.selectedTimes();
		int[] occurrences = timelineViewState.selectedEventOccurrences();
		int retainedCount = 0;
		for (String trackKey : trackKeys)
			if (!timelineTrackTokenMatches(trackKey, tokenIndex, value, trackTypes)) retainedCount++;
		String[] retainedKeys = new String[retainedCount];
		float[] retainedTimes = new float[retainedCount];
		int[] retainedOccurrences = new int[retainedCount];
		for (int source = 0, target = 0; source < trackKeys.length; source++) {
			if (timelineTrackTokenMatches(trackKeys[source], tokenIndex, value, trackTypes)) continue;
			retainedKeys[target] = trackKeys[source];
			retainedTimes[target] = times[source];
			retainedOccurrences[target++] = occurrences[source];
		}
		timelineViewState = new TimelineViewState(timelineViewState.animation(), timelineViewState.curveView(),
			timelineViewState.curveSelectedChannel(),
			timelineTrackTokenMatches(timelineViewState.curveTrackKey(), tokenIndex, value, trackTypes)
				? "" : timelineViewState.curveTrackKey(),
			timelineViewState.scrollX(), timelineViewState.scrollY(), timelineViewState.selectionStored(),
			retainedKeys, retainedTimes, retainedOccurrences);
	}

	private static String remapTimelineTrackKey(String key, int tokenIndex, String oldValue, String newValue,
		String... trackTypes) {
		if (key == null || key.isEmpty()) return key == null ? "" : key;
		String[] tokens = key.split("\u001f", -1);
		if (timelineTrackTokenMatches(tokens, tokenIndex, oldValue, trackTypes)) tokens[tokenIndex] = newValue;
		return String.join("\u001f", tokens);
	}

	private static boolean timelineTrackTokenMatches(String key, int tokenIndex, String value,
		String... trackTypes) {
		return key != null && timelineTrackTokenMatches(key.split("\u001f", -1), tokenIndex, value, trackTypes);
	}

	private static boolean timelineTrackTokenMatches(String[] tokens, int tokenIndex, String value,
		String... trackTypes) {
		if (value == null || tokenIndex < 0 || tokens.length <= tokenIndex) return false;
		boolean matchingType = false;
		for (String trackType : trackTypes)
			if (tokens[0].equals(trackType)) { matchingType = true; break; }
		return matchingType && tokens[tokenIndex].equals(value);
	}

	private static String timelineConstraintTrackType(ConstraintData<?, ?> constraint) {
		if (constraint instanceof IkConstraintData) return "ik";
		if (constraint instanceof TransformConstraintData) return "transform";
		if (constraint instanceof PathConstraintData) return "path";
		if (constraint instanceof PhysicsConstraintData) return "physics";
		return constraint instanceof SliderData ? "slider" : "";
	}

	private void remapAxisPreferenceToken(int tokenIndex, String oldValue, String newValue) {
		LinkedHashSet<String> renamed = new LinkedHashSet<>();
		for (String preference : axisSeparationPreferences) {
			String[] tokens = preference.split("\u001f", -1);
			if (tokens.length == 3 && tokens[tokenIndex].equals(oldValue)) tokens[tokenIndex] = newValue;
			renamed.add(tokens.length == 3 ? String.join("\u001f", tokens) : preference);
		}
		axisSeparationPreferences.clear();
		axisSeparationPreferences.addAll(renamed);
	}

	private void removeAxisPreferenceToken(int tokenIndex, String value) {
		axisSeparationPreferences.removeIf(preference -> {
			String[] tokens = preference.split("\u001f", -1);
			return tokens.length == 3 && tokens[tokenIndex].equals(value);
		});
	}

	private void remapAxisCombineToken(int tokenIndex, String oldValue, String newValue) {
		LinkedHashMap<String, AxisCombineState> renamed = new LinkedHashMap<>();
		for (AxisCombineState state : axisCombineStates.values()) {
			String animation = tokenIndex == 0 && state.animation().equals(oldValue) ? newValue : state.animation();
			String bone = tokenIndex == 1 && state.bone().equals(oldValue) ? newValue : state.bone();
			AxisCombineState replacement = new AxisCombineState(animation, bone, state.group(), state.xBeforeY(),
				state.xFrames(), state.xCurves(), state.yFrames(), state.yCurves(),
				state.combinedFrames(), state.combinedCurves());
			renamed.put(editorStateKey(animation, bone, state.group()), replacement);
		}
		axisCombineStates.clear();
		axisCombineStates.putAll(renamed);
	}

	private void removeAxisCombineToken(int tokenIndex, String value) {
		axisCombineStates.entrySet().removeIf(entry -> {
			AxisCombineState state = entry.getValue();
			return tokenIndex == 0 ? state.animation().equals(value) : state.bone().equals(value);
		});
	}

	public void splitGraphViewState(String animation, Animation.Timeline combined,
		Animation.Timeline splitX, Animation.Timeline splitY) {
		String oldKey = graphViewStateKey(animation, combined);
		GraphViewState state = graphViewStates.remove(oldKey);
		if (state == null) return;
		graphViewStates.put(graphViewStateKey(animation, splitX), projectGraphChannel(state, 0, 0));
		graphViewStates.put(graphViewStateKey(animation, splitY), projectGraphChannel(state, 1, 0));
	}

	public void combineGraphViewState(String animation, Animation.Timeline splitX,
		Animation.Timeline splitY, Animation.Timeline combined) {
		GraphViewState x = graphViewStates.remove(graphViewStateKey(animation, splitX));
		GraphViewState y = graphViewStates.remove(graphViewStateKey(animation, splitY));
		if (x == null && y == null) return;
		GraphViewState base = x == null ? y : x;
		float[] xTimes = selectedTimes(x), yTimes = selectedTimes(y);
		float[] times = new float[xTimes.length + yTimes.length];
		int[] channels = new int[times.length];
		System.arraycopy(xTimes, 0, times, 0, xTimes.length);
		System.arraycopy(yTimes, 0, times, xTimes.length, yTimes.length);
		Arrays.fill(channels, xTimes.length, channels.length, 1);
		long visible = (x != null && (x.visibleChannels() & 1L) != 0L ? 1L : 0L)
			| (y != null && (y.visibleChannels() & 1L) != 0L ? 2L : 0L);
		graphViewStates.put(graphViewStateKey(animation, combined), new GraphViewState(
			base.timeMin(), base.timeMax(), base.valueMin(), base.valueMax(), x == null ? 1 : 0,
			visible, x != null && x.selectionStored() || y != null && y.selectionStored(), times, channels,
			base.editTime(), base.editValue(), base.snap(), base.autoFrame(), base.separateHandles(),
			base.transformMode()));
	}

	private static GraphViewState projectGraphChannel(GraphViewState state, int oldChannel, int newChannel) {
		float[] sourceTimes = state.selectedTimes();
		int[] sourceChannels = state.selectedChannels();
		int count = 0;
		for (int channel : sourceChannels) if (channel == oldChannel) count++;
		float[] times = new float[count];
		int[] channels = new int[count];
		for (int source = 0, target = 0; source < sourceChannels.length; source++) {
			if (sourceChannels[source] != oldChannel) continue;
			times[target] = sourceTimes[source];
			channels[target++] = newChannel;
		}
		long visible = (state.visibleChannels() & 1L << oldChannel) == 0L ? 0L : 1L << newChannel;
		return new GraphViewState(state.timeMin(), state.timeMax(), state.valueMin(), state.valueMax(),
			newChannel, visible, state.selectionStored(), times, channels, state.editTime(), state.editValue(),
			state.snap(), state.autoFrame(), state.separateHandles(), state.transformMode());
	}

	private static float[] selectedTimes(GraphViewState state) {
		return state != null && state.selectionStored() ? state.selectedTimes() : new float[0];
	}

	private static String editorStateKey(String animation, String bone, String group) {
		return (animation == null ? "" : animation) + "\u001f" + (bone == null ? "" : bone)
			+ "\u001f" + (group == null ? "" : group);
	}

	/** 姿态模式下，把骨架重置为 setup pose（清除用户编辑） */
	public void resetToSetupPose() {
		if (skeleton == null) return;
		invalidatePhysicsSimulation();
		skeleton.setupPose();
		updateWorldTransform(Physics.reset);
	}

	/** 切换 Setup/Animate 工作状态。编辑姿态时自动暂停，退出时重应用当前动画。 */
	public void setPoseMode(boolean enabled) {
		invalidatePhysicsSimulation();
		poseMode = enabled;
		if (enabled) {
			setPlaying(false);
		} else if (skeleton != null) {
			skeleton.setupPose();
			if (animationState != null) {
				syncTrackLoopMode();
				animationState.apply(skeleton);
			}
			updateWorldTransform(Physics.reset);
		}
	}

	public void beginPoseEdit() {
		setPoseMode(true);
	}

	/** 设置当前动画并同步真实时长。 */
	public boolean setAnimation(String name) {
		if (animationState == null || data == null || name == null || name.isEmpty()) return false;
		com.esotericsoftware.spine.Animation animation = data.findAnimation(name);
		if (animation == null) return false;
		invalidatePhysicsSimulation();
		loopEndDisplayTime = Float.NaN;
		animationState.setAnimation(0, animation, shouldLoopTrack());
		autoSelectAnimation = true;
		maxTime = Math.max(0.01f, animation.getDuration());
		poseMode = false;
		if (skeleton != null) {
			skeleton.setupPose();
			animationState.apply(skeleton);
			updateWorldTransform(Physics.reset);
		}
		return true;
	}

	/** Clears the active preview and returns the runtime skeleton to its setup pose. */
	public void clearAnimation() {
		invalidatePhysicsSimulation();
		loopEndDisplayTime = Float.NaN;
		if (animationState != null) animationState.clearTrack(0);
		playing = false;
		poseMode = false;
		maxTime = 0f;
		autoSelectAnimation = false;
		if (skeleton != null) {
			skeleton.setupPose();
			updateWorldTransform(Physics.reset);
		}
	}

	public boolean shouldAutoSelectAnimation() { return autoSelectAnimation; }

	public void restoreAutomaticAnimationSelection(boolean enabled) { autoSelectAnimation = enabled; }

	public void setLooping(boolean enabled) {
		AnimationState.TrackEntry track = animationState == null ? null : animationState.getTrack(0);
		float duration = trackDuration(track);
		float before = exactTrackTime(track);
		boolean preserveEnd = enabled && playing && !poseMode && track != null && atTrackEnd(before, duration);
		looping = enabled;
		syncTrackLoopMode();
		loopEndDisplayTime = preserveEnd ? duration : Float.NaN;
	}

	public void setPlaying(boolean enabled) {
		AnimationState.TrackEntry track = animationState == null ? null : animationState.getTrack(0);
		float duration = trackDuration(track);
		float before = exactTrackTime(track);
		boolean preserveEnd = enabled && looping && track != null && atTrackEnd(before, duration);
		playing = enabled;
		syncTrackLoopMode();
		loopEndDisplayTime = preserveEnd ? duration : Float.NaN;
	}

	boolean shouldLoopTrack() {
		return looping && playing && !poseMode;
	}

	private AnimationState.TrackEntry syncTrackLoopMode() {
		if (animationState == null) return null;
		AnimationState.TrackEntry track = animationState.getTrack(0);
		boolean shouldLoop = shouldLoopTrack();
		if (track != null && track.getLoop() != shouldLoop) {
			float animationTime = exactTrackTime(track);
			track.setLoop(shouldLoop);
			if (!shouldLoop)
				track.setTrackTime(Math.max(0f, animationTime - track.getAnimationStart()));
		}
		return track;
	}

	/** Switches preview skin and reapplies the paused animation time so slot timelines stay in sync. */
	public boolean setSkin(String name) {
		if (skeleton == null || data == null || name == null || name.isBlank()) return false;
		return setPreviewSkins(List.of(name));
	}

	/** Ordered preview layers; later skins win attachment conflicts without modifying any source skin. */
	public List<String> getPreviewSkinNames() {
		if (skeleton == null || skeleton.getSkin() == null) return List.of();
		Skin current = skeleton.getSkin();
		if (current == combinedPreviewSkin || data == null || !data.getSkins().contains(current, true))
			return previewSkinNames;
		// Runtime loaders may select a real skin directly; temporary constraint skins must not replace it.
		previewSkinNames = List.of(current.getName());
		return previewSkinNames;
	}

	public boolean setPreviewSkins(List<String> names) {
		if (!bindPreviewSkins(names)) return false;
		markPoseChanged();
		if (!applyCurrentAnimationPose(Physics.reset)) updateWorldTransform(Physics.reset);
		return true;
	}

	private boolean bindPreviewSkins(List<String> names) {
		if (data == null || skeleton == null || names == null || names.isEmpty()) return false;
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String name : names)
			if (name == null || data.findSkin(name) == null || !unique.add(name)) return false;
		List<String> selected = List.copyOf(unique);
		Skin preview = data.findSkin(selected.get(0));
		if (selected.size() > 1) {
			preview = new Skin("openspine-preview");
			for (String name : selected) preview.addSkin(data.findSkin(name));
		}
		if (selected.size() == 1 && skeleton.getSkin() == preview) return true;
		combinedPreviewSkin = selected.size() > 1 ? preview : null;
		previewSkinNames = selected;
		skeleton.setSkin(preview);
		skeleton.setupPoseSlots();
		return true;
	}

	void restorePreviewSkinBindings(List<String> names) {
		List<String> surviving = names.stream().filter(name -> data.findSkin(name) != null).distinct().toList();
		if (!surviving.isEmpty()) bindPreviewSkins(surviving);
		else if (data.getDefaultSkin() != null) bindPreviewSkins(List.of(data.getDefaultSkin().getName()));
	}

	/** Restores surviving layers after loading or structural edits, with the usual default-skin fallback. */
	public void restorePreviewSkins(List<String> names) {
		if (data == null || skeleton == null) return;
		List<String> surviving = names == null ? List.of() : names.stream()
			.filter(name -> name != null && data.findSkin(name) != null).distinct().toList();
		if (!surviving.isEmpty()) setPreviewSkins(surviving);
		else if (data.getDefaultSkin() != null) setSkin(data.getDefaultSkin().getName());
	}

	/** The last preview layer is the real skin edited by attachment and constraint controls. */
	public Skin getEditingSkin() {
		if (data == null) return null;
		Skin skin = data.findSkin(getCurrentSkinName());
		return skin == null ? data.getDefaultSkin() : skin;
	}

	/** Reapplies the active animation at its current track time without changing playback state. */
	public boolean refreshCurrentAnimationPose() {
		if (physicsSimulationEnabled && physicsDeterministic && hasPhysicsConstraints()) {
			requestPhysicsSimulation(currentTrackTime());
			return true;
		}
		return applyCurrentAnimationPose(Physics.pose);
	}

	private boolean applyCurrentAnimationPose(Physics physics) {
		if (poseMode || animationState == null || skeleton == null || animationState.getTrack(0) == null) return false;
		syncTrackLoopMode();
		skeleton.setupPose();
		animationState.apply(skeleton);
		updateWorldTransform(physics);
		return true;
	}

	/** Spine Editor does not apply a Slider that references the animation currently being previewed. */
	void updateWorldTransform(Physics physics) {
		if (skeleton == null) return;
		if (!constraintsEnabled) {
			updateWorldTransform(skeleton, physics, false);
			return;
		}
		AnimationState.TrackEntry track = animationState == null ? null : animationState.getTrack(0);
		Animation activeAnimation = track == null ? null : track.getAnimation();
		if (activeAnimation == null) {
			skeleton.updateWorldTransform(physics);
			return;
		}
		Array<Slider> suppressed = new Array<>();
		for (var constraint : skeleton.getConstraints())
			if (constraint instanceof Slider slider && slider.getData().getAnimation() == activeAnimation)
				suppressed.add(slider);
		if (suppressed.isEmpty()) {
			skeleton.updateWorldTransform(physics);
			return;
		}
		float[] times = new float[suppressed.size], mixes = new float[suppressed.size];
		for (int index = 0; index < suppressed.size; index++) {
			SliderPose pose = suppressed.get(index).getPose();
			times[index] = pose.getTime();
			mixes[index] = pose.getMix();
			pose.setMix(0f);
		}
		try {
			skeleton.updateWorldTransform(physics);
		} finally {
			for (int index = 0; index < suppressed.size; index++) {
				SliderPose pose = suppressed.get(index).getPose();
				pose.setTime(times[index]);
				pose.setMix(mixes[index]);
			}
		}
	}

	/** Updates a runtime skeleton while optionally omitting Constraint entries from its update cache. */
	public static void updateWorldTransform(Skeleton skeleton, Physics physics, boolean constraintsEnabled) {
		if (skeleton == null) return;
		if (constraintsEnabled) {
			skeleton.updateWorldTransform(physics);
			return;
		}
		Array<Update> cache = skeleton.getUpdateCache();
		Update[] original = cache.toArray(Update.class);
		boolean hasConstraint = false;
		for (Update update : original) {
			if (update instanceof Constraint) {
				hasConstraint = true;
				break;
			}
		}
		if (!hasConstraint) {
			skeleton.updateWorldTransform(physics);
			return;
		}
		cache.clear();
		for (Update update : original) if (!(update instanceof Constraint)) cache.add(update);
		try {
			skeleton.updateWorldTransform(physics);
		} finally {
			cache.clear();
			cache.addAll(original);
		}
	}

	/** 重新应用当前动画（用于跳转时间线后刷新） */
	public void seek(float time) {
		if (animationState == null || skeleton == null) return;
		loopEndDisplayTime = Float.NaN;
		AnimationState.TrackEntry track = syncTrackLoopMode();
		float target = 0f;
		boolean preserveLoopEnd = false;
		if (track != null) {
			float duration = trackDuration(track);
			target = Math.max(0f, Math.min(duration, time));
			preserveLoopEnd = track.getLoop() && atTrackEnd(target, duration);
			if (preserveLoopEnd) track.setLoop(false);
			track.setTrackTime(target);
		}
		if (track != null && physicsSimulationEnabled && physicsDeterministic
			&& hasPhysicsConstraints() && target > 0f) {
			requestPhysicsSimulation(target);
		} else {
			invalidatePhysicsSimulation();
			skeleton.setupPose();
			animationState.apply(skeleton);
			updateWorldTransform(Physics.reset);
		}
		if (preserveLoopEnd) {
			track.setLoop(true);
			loopEndDisplayTime = target;
		}
	}

	private float currentTrackTime() {
		if (animationState == null) return 0f;
		AnimationState.TrackEntry track = animationState.getTrack(0);
		return track == null ? 0f : Math.max(0f, exactTrackTime(track));
	}

	private float exactTrackTime(AnimationState.TrackEntry track) {
		return Float.isFinite(loopEndDisplayTime) ? loopEndDisplayTime
			: track == null ? 0f : track.getAnimationTime();
	}

	private static float trackDuration(AnimationState.TrackEntry track) {
		return track == null ? 0f : Math.max(0.01f, track.getAnimationEnd() - track.getAnimationStart());
	}

	private static boolean atTrackEnd(float time, float duration) {
		return Float.isFinite(time) && Float.isFinite(duration)
			&& Math.abs(time - duration) <= Math.max(0.000001f, duration * 0.000001f);
	}

	private boolean hasPhysicsConstraints() {
		if (!constraintsEnabled || data == null) return false;
		for (ConstraintData<?, ?> constraint : data.getConstraints())
			if (constraint instanceof PhysicsConstraintData) return true;
		return false;
	}

	private float physicsSimulationStep() {
		float step = 1f / 60f;
		if (data == null) return step;
		for (ConstraintData<?, ?> constraint : data.getConstraints()) {
			if (!(constraint instanceof PhysicsConstraintData physics)) continue;
			float candidate = physics.getStep();
			if (Float.isFinite(candidate) && candidate > 0f) step = Math.min(step, candidate);
		}
		return Math.max(0.0001f, step);
	}

	private void requestPhysicsSimulation(float targetTime) {
		AnimationState.TrackEntry active = animationState == null ? null : animationState.getTrack(0);
		if (active == null || active.getAnimation() == null || skeleton == null) return;
		float target = Math.max(0f, targetTime);
		float step = physicsSimulationStep();
		long targetSteps = physicsStepCount(target, step);
		float completedTime = physicsSimulationCursor == null ? -1f
			: (float)(physicsSimulationCursor.completedSteps * (double)physicsSimulationCursor.step);
		boolean reusable = physicsSimulationCursor != null
			&& physicsSimulationCursor.skeleton == skeleton
			&& physicsSimulationCursor.animation == active.getAnimation()
			&& Float.compare(physicsSimulationCursor.step, step) == 0
			&& target + 0.000001f >= completedTime
			&& targetSteps >= physicsSimulationCursor.completedSteps;
		if (!reusable) beginPhysicsSimulation(active.getAnimation(), step);
		physicsSimulationCursor.targetTime = target;
		physicsSimulationCursor.targetSteps = targetSteps;
		advancePhysicsSimulation(PHYSICS_STEPS_PER_TICK,
			System.nanoTime() + PHYSICS_NANOS_PER_TICK);
	}

	private void beginPhysicsSimulation(Animation animation, float step) {
		AnimationState simulation = new AnimationState(animationState.getData());
		simulation.setAnimation(0, animation, false);
		physicsSimulationCursor = new PhysicsSimulationCursor(skeleton, animation, simulation, step);
		skeleton.setTime(0f);
		skeleton.setupPose();
		simulation.apply(skeleton);
		updateWorldTransform(Physics.reset);
	}

	private boolean advancePendingPhysicsSimulation() {
		if (!isPhysicsSeekPending()) return false;
		advancePhysicsSimulation(PHYSICS_STEPS_PER_TICK,
			System.nanoTime() + PHYSICS_NANOS_PER_TICK);
		return isPhysicsSeekPending();
	}

	private void advancePhysicsSimulation(int maxSteps, long deadline) {
		PhysicsSimulationCursor cursor = physicsSimulationCursor;
		if (cursor == null || cursor.skeleton != skeleton) {
			invalidatePhysicsSimulation();
			return;
		}
		AnimationState.TrackEntry simulationTrack = cursor.simulation.getTrack(0);
		float canonicalTime = (float)(cursor.completedSteps * (double)cursor.step);
		if (simulationTrack != null) simulationTrack.setTrackTime(canonicalTime);
		skeleton.setTime(canonicalTime);
		int iterations = 0;
		while (cursor.completedSteps < cursor.targetSteps && iterations < maxSteps) {
			cursor.simulation.update(cursor.step);
			skeleton.update(cursor.step);
			skeleton.setupPose();
			cursor.simulation.apply(skeleton);
			updateWorldTransform(Physics.update);
			cursor.completedSteps++;
			iterations++;
			if ((iterations & 31) == 0 && System.nanoTime() >= deadline) break;
		}
		if (cursor.completedSteps == cursor.targetSteps) presentPhysicsTarget(cursor);
	}

	private void presentPhysicsTarget(PhysicsSimulationCursor cursor) {
		float canonicalTime = (float)(cursor.completedSteps * (double)cursor.step);
		float remainder = cursor.targetTime - canonicalTime;
		if (remainder > 0.000001f) {
			AnimationState.TrackEntry simulationTrack = cursor.simulation.getTrack(0);
			if (simulationTrack != null) simulationTrack.setTrackTime(canonicalTime);
			skeleton.setTime(canonicalTime);
			cursor.simulation.update(remainder);
			skeleton.update(remainder);
			skeleton.setupPose();
			cursor.simulation.apply(skeleton);
			updateWorldTransform(Physics.update);
			// The dynamic state now belongs to targetTime, not completedSteps * step.
			// Rebuild a later deterministic seek from zero instead of reusing a false canonical cursor.
			if (physicsSimulationCursor == cursor) physicsSimulationCursor = null;
		}
		skeleton.setTime(cursor.targetTime);
	}

	private static long physicsStepCount(float target, float step) {
		return Math.max(0L, (long)Math.floor(target / (double)step + 0.00001d));
	}

	private void invalidatePhysicsSimulation() {
		physicsSimulationCursor = null;
	}

	public boolean isPhysicsSeekPending() {
		return physicsSimulationCursor != null
			&& physicsSimulationCursor.completedSteps < physicsSimulationCursor.targetSteps;
	}

	/** Completes a pending seek for headless frame capture and other exact offline consumers. */
	public void completePendingPhysicsSeek() {
		if (!isPhysicsSeekPending()) return;
		advancePhysicsSimulation(Integer.MAX_VALUE, Long.MAX_VALUE);
	}

	/** Pauses playback, leaves pose mode, and applies the animation at an exact editor time. */
	public boolean scrubTo(float time) {
		if (!Float.isFinite(time) || animationState == null || skeleton == null
			|| animationState.getTrack(0) == null) return false;
		if (poseMode) setPoseMode(false);
		setPlaying(false);
		seek(time);
		return true;
	}

	/** 当前动画时间(秒) */
	public float getCurrentTime() {
		if (animationState == null) return 0;
		AnimationState.TrackEntry track = animationState.getTrack(0);
		return track == null ? 0 : exactTrackTime(track);
	}

	public float getCurrentDuration() {
		if (animationState == null) return Math.max(0.01f, maxTime);
		AnimationState.TrackEntry track = animationState.getTrack(0);
		if (track == null || track.getAnimation() == null) return Math.max(0.01f, maxTime);
		return Math.max(0.01f, track.getAnimationEnd() - track.getAnimationStart());
	}

	/** 当前播放中的动画名 */
	public String getCurrentAnimationName() {
		if (animationState == null) return "";
		AnimationState.TrackEntry track = animationState.getTrack(0);
		return track == null || track.getAnimation() == null ? "" : track.getAnimation().getName();
	}

	/** 当前皮肤名 */
	public String getCurrentSkinName() {
		List<String> names = getPreviewSkinNames();
		return names.isEmpty() ? "" : names.get(names.size() - 1);
	}

	// ---- 骨骼选择 ----

	public Bone getSelectedBone() {
		return selectedBone;
	}

	public void setSelectedBone(Bone bone) {
		this.selectedBone = bone;
		selectedBones.clear();
		if (bone != null && skeleton != null && skeleton.getBones().contains(bone, true)) selectedBones.add(bone);
		clearEditorSelection();
	}

	/** Returns the hierarchy selection in primary-first order. */
	public Set<Bone> getSelectedBones() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(selectedBones));
	}

	/** Sets a hierarchy selection, promoting the primary to its highest selected ancestor. */
	public void setSelectedBones(Iterable<Bone> bones) {
		selectedBones.clear();
		if (bones != null && skeleton != null) {
			for (Bone bone : bones) {
				if (bone != null && skeleton.getBones().contains(bone, true)) selectedBones.add(bone);
			}
		}
		selectedBone = selectedBones.isEmpty() ? null : selectedBones.iterator().next();
		if (selectedBone != null) {
			Bone requestedPrimary = selectedBone;
			for (Bone parent = selectedBone.getParent(); parent != null; parent = parent.getParent())
				if (selectedBones.contains(parent)) selectedBone = parent;
			if (selectedBone != requestedPrimary) {
				LinkedHashSet<Bone> ordered = new LinkedHashSet<>();
				ordered.add(selectedBone);
				ordered.addAll(selectedBones);
				selectedBones.clear();
				selectedBones.addAll(ordered);
			}
		}
		clearEditorSelection();
	}

	public boolean isSelected(Bone bone) {
		return bone != null && (selectedBones.contains(bone) || constraintSelectionBones.contains(bone));
	}

	public boolean isBoneSelectable(Bone bone) {
		return bone != null && isBoneSelectable(bone.getData());
	}

	public boolean isBoneSelectable(BoneData bone) {
		if (bone == null || bone.getName() == null) return true;
		BoneEditorState state = boneEditorStates.get(bone.getName());
		return state == null || state.selectable();
	}

	public boolean isBoneNameVisible(Bone bone) {
		return bone != null && isBoneNameVisible(bone.getData());
	}

	public boolean isBoneNameVisible(BoneData bone) {
		if (bone == null || bone.getName() == null) return false;
		BoneEditorState state = boneEditorStates.get(bone.getName());
		return state != null && state.nameVisible();
	}

	/** Returns the effective editor-only display state for one bone. */
	public BoneEditorState boneEditorState(BoneData bone) {
		if (bone == null || bone.getName() == null) return null;
		return new BoneEditorState(bone.getName(), isBoneSelectable(bone), isBoneNameVisible(bone));
	}

	public Set<BoneEditorState> boneEditorStates() {
		return Set.copyOf(boneEditorStates.values());
	}

	public void setBoneEditorState(BoneEditorState state) {
		if (state == null || state.bone().isBlank()) return;
		if (state.selectable() && !state.nameVisible()) boneEditorStates.remove(state.bone());
		else boneEditorStates.put(state.bone(), state);
	}

	/** Applies a subset of editor-only states, preserving unrelated Sidecar entries. */
	public void applyBoneEditorStates(Iterable<BoneEditorState> states) {
		if (states == null) return;
		boolean changed = false;
		for (BoneEditorState state : states) {
			if (state == null || state.bone().isBlank() || data == null || data.findBone(state.bone()) == null)
				continue;
			BoneEditorState before = boneEditorState(data.findBone(state.bone()));
			setBoneEditorState(state);
			changed |= before == null || !before.equals(boneEditorState(data.findBone(state.bone())));
		}
		if (changed) revision++;
	}

	public void restoreBoneEditorStates(Iterable<BoneEditorState> states) {
		boneEditorStates.clear();
		if (states != null && data != null) {
			for (BoneEditorState state : states) {
				if (state == null || state.bone().isBlank() || data.findBone(state.bone()) == null) continue;
				setBoneEditorState(state);
			}
		}
		revision++;
	}

	public boolean setBoneSelectable(BoneData bone, boolean selectable) {
		if (bone == null || data == null || !data.getBones().contains(bone, true)
			|| isBoneSelectable(bone) == selectable) return false;
		setBoneEditorState(new BoneEditorState(bone.getName(), selectable, isBoneNameVisible(bone)));
		markEditorStateDirty();
		return true;
	}

	public boolean setBoneNameVisible(BoneData bone, boolean visible) {
		if (bone == null || data == null || !data.getBones().contains(bone, true)
			|| isBoneNameVisible(bone) == visible) return false;
		setBoneEditorState(new BoneEditorState(bone.getName(), isBoneSelectable(bone), visible));
		markEditorStateDirty();
		return true;
	}

	/** Marks Sidecar-only editor state without resetting animation or physics preview state. */
	private void markEditorStateDirty() {
		dirty = true;
		revision++;
	}

	public SelectionKind getSelectionKind() { return selectionKind; }

	public ConstraintData<?, ?> getSelectedConstraint() { return selectedConstraint; }

	public String getSelectedConstraintRelation() { return selectedConstraintRelation; }

	public String getSelectedSlotName() { return selectedSlotName; }

	public String getSelectedAttachmentPlaceholder() { return selectedAttachmentPlaceholder; }

	public String getSelectedAttachmentName() { return selectedAttachmentName; }

	public Set<Bone> getConstraintSelectionBones() { return Set.copyOf(constraintSelectionBones); }

	public void setSlotSelection(Slot slot, String placeholder, String attachmentName) {
		if (slot == null || skeleton == null || !skeleton.getSlots().contains(slot, true)) {
			setSelectedBone(slot == null ? selectedBone : slot.getBone());
			return;
		}
		selectedBone = slot.getBone();
		selectedBones.clear();
		if (selectedBone != null) selectedBones.add(selectedBone);
		selectedConstraint = null;
		selectedConstraintRelation = "";
		constraintSelectionBones.clear();
		selectedSlotName = slot.getData().getName();
		selectedAttachmentPlaceholder = placeholder == null ? "" : placeholder;
		selectedAttachmentName = attachmentName == null ? "" : attachmentName;
		selectionKind = selectedAttachmentPlaceholder.isEmpty() && selectedAttachmentName.isEmpty()
			? SelectionKind.SLOT : SelectionKind.ATTACHMENT;
	}

	public void setConstraintSelection(ConstraintData<?, ?> constraint) {
		if (constraint == null || data == null || skeleton == null
			|| !data.getConstraints().contains(constraint, true)) {
			setSelectedBone(selectedBone);
			return;
		}
		selectedConstraint = constraint;
		selectedConstraintRelation = "";
		selectionKind = SelectionKind.CONSTRAINT;
		selectedSlotName = "";
		selectedAttachmentPlaceholder = "";
		selectedAttachmentName = "";
		constraintSelectionBones.clear();
		for (BoneData boneData : constraintSelectionBoneData(constraint, data)) {
			Bone bone = skeleton.findBone(boneData.getName());
			if (bone != null) constraintSelectionBones.add(bone);
		}
		selectedBone = constraintSelectionBones.isEmpty() ? null : constraintSelectionBones.iterator().next();
		selectedBones.clear();
		selectedBones.addAll(constraintSelectionBones);
	}

	public void setConstraintRelationSelection(ConstraintData<?, ?> constraint, String relation, String boneName) {
		setConstraintSelection(constraint);
		if (selectedConstraint != constraint || !constraintRelationExists(constraint, relation, data)) return;
		selectedConstraintRelation = relation;
		Bone relationBone = skeleton == null ? null : skeleton.findBone(boneName == null ? "" : boneName);
		if (relationBone != null && constraintSelectionBones.contains(relationBone)) selectedBone = relationBone;
	}

	private static boolean constraintRelationExists(ConstraintData<?, ?> constraint, String relation,
		SkeletonData data) {
		if (constraint == null || relation == null || relation.isBlank()) return false;
		if (constraint instanceof IkConstraintData ik) {
			for (BoneData bone : ik.getBones())
				if (relation.equals("constrained:" + bone.getName())) return true;
			return ik.getTarget() != null && relation.equals("target:" + ik.getTarget().getName());
		}
		if (constraint instanceof TransformConstraintData transform) {
			for (BoneData bone : transform.getBones())
				if (relation.equals("target:" + bone.getName())) return true;
			return transform.getSource() != null && relation.equals("source:" + transform.getSource().getName());
		}
		if (constraint instanceof PathConstraintData path) {
			for (BoneData bone : path.getBones())
				if (relation.equals("constrained:" + bone.getName())) return true;
			return path.getSlot() != null && relation.equals("slot:" + path.getSlot().getName());
		}
		if (constraint instanceof PhysicsConstraintData physics)
			return physics.getBone() != null && relation.equals("physics:" + physics.getBone().getName());
		if (!(constraint instanceof SliderData slider)) return false;
		if (slider.getBone() != null) {
			String property = slider.getProperty() == null ? "time"
				: slider.getProperty().getClass().getSimpleName();
			if (relation.equals("source:" + slider.getBone().getName() + ":" + property)) return true;
		}
		if (slider.getAnimation() == null) return false;
		if (relation.equals("animation:" + slider.getAnimation().getName())) return true;
		for (BoneData bone : constraintSelectionBoneData(slider, data))
			if (bone != slider.getBone() && relation.equals("animation-bone:" + bone.getName())) return true;
		return false;
	}

	public void restoreEditorSelection(String kind, String boneName, String constraintType,
		String constraintName, String slotName, String placeholder, String attachmentName) {
		restoreEditorSelection(kind, boneName, constraintType, constraintName, "", slotName, placeholder,
			attachmentName);
	}

	public void restoreEditorSelection(String kind, String boneName, String constraintType,
		String constraintName, String constraintRelation, String slotName, String placeholder,
		String attachmentName) {
		selectedBone = skeleton == null ? null : skeleton.findBone(boneName == null ? "" : boneName);
		selectedBones.clear();
		if (selectedBone != null) selectedBones.add(selectedBone);
		SelectionKind requested;
		try { requested = SelectionKind.valueOf(kind == null ? "BONE" : kind); }
		catch (IllegalArgumentException ignored) { requested = SelectionKind.BONE; }
		if (requested == SelectionKind.CONSTRAINT && data != null) {
			for (ConstraintData<?, ?> constraint : data.getConstraints()) {
				if (constraint.getName().equals(constraintName)
					&& constraint.getClass().getSimpleName().equals(constraintType)) {
					if (constraintRelation == null || constraintRelation.isBlank()) setConstraintSelection(constraint);
					else setConstraintRelationSelection(constraint, constraintRelation, boneName);
					return;
				}
			}
		}
		if ((requested == SelectionKind.SLOT || requested == SelectionKind.ATTACHMENT) && skeleton != null) {
			Slot slot = skeleton.findSlot(slotName == null ? "" : slotName);
			if (slot != null) {
				setSlotSelection(slot, requested == SelectionKind.ATTACHMENT ? placeholder : "",
					requested == SelectionKind.ATTACHMENT ? attachmentName : "");
				return;
			}
		}
		clearEditorSelection();
	}

	private void clearEditorSelection() {
		selectionKind = SelectionKind.BONE;
		selectedConstraint = null;
		selectedConstraintRelation = "";
		selectedSlotName = "";
		selectedAttachmentPlaceholder = "";
		selectedAttachmentName = "";
		constraintSelectionBones.clear();
	}

	/** Bones participating in a constraint, including the complete target-animation dependency set for Slider. */
	public static Array<BoneData> constraintSelectionBoneData(ConstraintData<?, ?> constraint, SkeletonData data) {
		Array<BoneData> bones = new Array<>();
		collectConstraintBoneData(constraint, data, bones,
			Collections.newSetFromMap(new IdentityHashMap<SliderData, Boolean>()));
		return bones;
	}

	private static void collectConstraintBoneData(ConstraintData<?, ?> constraint, SkeletonData data,
		Array<BoneData> bones, Set<SliderData> visitedSliders) {
		if (constraint instanceof IkConstraintData ik) {
			for (BoneData bone : ik.getBones()) addUnique(bones, bone);
			addUnique(bones, ik.getTarget());
		} else if (constraint instanceof TransformConstraintData transform) {
			for (BoneData bone : transform.getBones()) addUnique(bones, bone);
			addUnique(bones, transform.getSource());
		} else if (constraint instanceof PathConstraintData path) {
			for (BoneData bone : path.getBones()) addUnique(bones, bone);
			if (path.getSlot() != null) addUnique(bones, path.getSlot().getBoneData());
		} else if (constraint instanceof PhysicsConstraintData physics) {
			addUnique(bones, physics.getBone());
		} else if (constraint instanceof SliderData slider) {
			addUnique(bones, slider.getBone());
			collectSliderAnimationBoneData(slider, data, bones, visitedSliders);
		}
	}

	private static void collectSliderAnimationBoneData(SliderData slider, SkeletonData data,
		Array<BoneData> bones, Set<SliderData> visitedSliders) {
		if (slider == null || data == null || !visitedSliders.add(slider)) return;
		Animation animation = slider.getAnimation();
		if (animation == null) return;
		if (animation.getBones() != null) for (int index = 0; index < animation.getBones().size; index++)
			addBoneAt(bones, data, animation.getBones().get(index));
		if (animation.getTimelines() == null) return;
		for (Animation.Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof Animation.SlotTimeline slotTimeline) {
				addSlotBoneAt(bones, data, slotTimeline.getSlotIndex());
			} else if (timeline instanceof Animation.DrawOrderTimeline) {
				for (SlotData slot : data.getSlots()) addUnique(bones, slot.getBoneData());
			} else if (timeline instanceof Animation.DrawOrderFolderTimeline folder) {
				for (int slotIndex : folder.getSlots()) addSlotBoneAt(bones, data, slotIndex);
			} else if (timeline instanceof Animation.PhysicsConstraintTimeline physicsTimeline) {
				int constraintIndex = physicsTimeline.getConstraintIndex();
				if (constraintIndex == -1) {
					for (ConstraintData<?, ?> candidate : data.getConstraints())
						if (candidate instanceof PhysicsConstraintData)
							collectConstraintBoneData(candidate, data, bones, visitedSliders);
				} else {
					collectConstraintAt(bones, data, constraintIndex, visitedSliders);
				}
			} else if (timeline instanceof Animation.ConstraintTimeline constraintTimeline) {
				int constraintIndex = constraintTimeline.getConstraintIndex();
				if (constraintIndex != -1) collectConstraintAt(bones, data, constraintIndex, visitedSliders);
			}
		}
	}

	private static void collectConstraintAt(Array<BoneData> bones, SkeletonData data, int constraintIndex,
		Set<SliderData> visitedSliders) {
		if (constraintIndex < 0 || constraintIndex >= data.getConstraints().size) return;
		collectConstraintBoneData(data.getConstraints().get(constraintIndex), data, bones, visitedSliders);
	}

	private static void addBoneAt(Array<BoneData> bones, SkeletonData data, int boneIndex) {
		if (boneIndex >= 0 && boneIndex < data.getBones().size) addUnique(bones, data.getBones().get(boneIndex));
	}

	private static void addSlotBoneAt(Array<BoneData> bones, SkeletonData data, int slotIndex) {
		if (slotIndex >= 0 && slotIndex < data.getSlots().size)
			addUnique(bones, data.getSlots().get(slotIndex).getBoneData());
	}

	private static void addUnique(Array<BoneData> bones, BoneData bone) {
		if (bone != null && !bones.contains(bone, true)) bones.add(bone);
	}

	/** Applies a complete local bone pose only when it differs from the current runtime pose. */
	public boolean updateBonePose(Bone bone, float x, float y, float rotation,
		float scaleX, float scaleY, float shearX, float shearY) {
		if (skeleton == null || bone == null || !skeleton.getBones().contains(bone, true)
			|| !allFinite(x, y, rotation, scaleX, scaleY, shearX, shearY)) return false;
		var pose = bone.getPose();
		if (pose.getX() == x && pose.getY() == y && pose.getRotation() == rotation
			&& pose.getScaleX() == scaleX && pose.getScaleY() == scaleY
			&& pose.getShearX() == shearX && pose.getShearY() == shearY) return false;
		beginPoseEdit();
		pose.setX(x);
		pose.setY(y);
		pose.setRotation(rotation);
		pose.setScaleX(scaleX);
		pose.setScaleY(scaleY);
		pose.setShearX(shearX);
		pose.setShearY(shearY);
		boneEdited();
		return true;
	}

	/** Applies one runtime inherit mode as a reversible pose preview. */
	public boolean canUseBoneInherit(BoneData bone, BoneData.Inherit inherit) {
		if (bone == null || inherit == null || data == null) return false;
		for (ConstraintData<?, ?> constraint : data.getConstraints()) {
			if (!(constraint instanceof IkConstraintData ik) || ik.getBones().size < 2) continue;
			for (BoneData constrained : ik.getBones())
				if (constrained == bone) return inherit == BoneData.Inherit.normal;
		}
		return true;
	}

	public boolean updateBoneInherit(Bone bone, BoneData.Inherit inherit) {
		if (skeleton == null || bone == null || inherit == null
			|| !skeleton.getBones().contains(bone, true)
			|| !canUseBoneInherit(bone.getData(), inherit)) return false;
		var pose = bone.getPose();
		if (pose.getInherit() == inherit) return false;
		beginPoseEdit();
		pose.setInherit(inherit);
		boneEdited();
		return true;
	}

	private static boolean allFinite(float... values) {
		for (float value : values)
			if (!Float.isFinite(value)) return false;
		return true;
	}

	/** 骨骼的世界坐标（供拾取与 gizmo 绘制） */
	public Vector2 boneWorldPos(Bone bone, Vector2 out) {
		if (bone == null) return out.set(0, 0);
		out.set(bone.getAppliedPose().getWorldX(), bone.getAppliedPose().getWorldY());
		return out;
	}

	/** 该骨骼下是否还有其他骨骼（用于嵌入 gizmo 选择） */
	public Array<Bone> boneChildren(Bone bone) {
		return bone == null ? null : bone.getChildren();
	}

	/** 修改骨骼局部属性后刷新世界变换 */
	public void boneEdited() {
		if (skeleton == null) return;
		updateWorldTransform(Physics.pose);
		markPoseChanged();
	}
}
