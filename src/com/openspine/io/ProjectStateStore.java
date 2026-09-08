package com.openspine.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Persists OpenSpine-only editor state beside a supported skeleton or editor project source. */
public final class ProjectStateStore {
	public static final int FORMAT_VERSION = 12;
	private static final long MAX_STATE_BYTES = 8L * 1024L * 1024L;
	private static final String SUFFIX = ".openspine.json";
	// Positive allowlist: newly added authoring fields cannot accidentally enter automatic view saves.
	private static final Set<String> VIEW_FIELDS = Set.of("name", "animation", "time", "skin", "previewSkins",
		"selectedBone", "selectedBones", "selectionKind", "selectedConstraintType", "selectedConstraint",
		"selectedConstraintRelation", "selectedSlot", "selectedAttachmentPlaceholder", "selectedAttachment",
		"playing", "looping", "playbackSpeed", "physicsSimulate", "physicsDeterministic",
		"graphs", "tree", "timeline", "graphWorkspace");

	public enum LoadResult { NOT_FOUND, RESTORED, SOURCE_CHANGED }
	public record LoadOutcome(LoadResult result, boolean showAllSkeletons) {}

	public static Path sidecarPath(Path projectFile) {
		Path source = requireSpineProject(projectFile);
		return source.resolveSibling(source.getFileName().toString() + SUFFIX);
	}

	public static void write(Path projectFile, SkeletonProject project) throws IOException {
		write(projectFile, project, true);
	}

	public static void write(Path projectFile, SkeletonProject project, boolean showAllSkeletons) throws IOException {
		write(projectFile, project, showAllSkeletons, false);
	}

	/** Writes only navigation and preview state; unsaved authoring changes remain outside the root sidecar. */
	public static void writeViewState(Path projectFile, SkeletonProject project, boolean showAllSkeletons) throws IOException {
		write(projectFile, project, showAllSkeletons, true);
	}

	private static void write(Path projectFile, SkeletonProject project, boolean showAllSkeletons, boolean viewOnly)
		throws IOException {
		Path source = requireExistingSpineProject(projectFile);
		if (project == null || project.size() == 0)
			throw new IllegalArgumentException("Project state requires at least one skeleton");
		ProjectState state = snapshot(source, project, showAllSkeletons);
		Json json = json();
		String content = json.prettyPrint(state) + System.lineSeparator();
		if (viewOnly) {
			JsonValue root = new JsonReader().parse(content);
			root.addChild("viewOnly", new JsonValue(true));
			for (JsonValue skeleton : root.get("skeletons")) {
				for (JsonValue field = skeleton.child, next; field != null; field = next) {
					next = field.next;
					if (!VIEW_FIELDS.contains(field.name)) field.remove();
				}
			}
			content = root.prettyPrint(JsonWriter.OutputType.json, 100) + System.lineSeparator();
		}
		Path target = sidecarPath(source);
		Path parent = target.getParent();
		if (parent == null) throw new IOException("Project state path has no parent directory");
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			Files.writeString(temporary, content, StandardCharsets.UTF_8);
			moveAtomic(temporary, target);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public static LoadResult load(Path projectFile, SkeletonProject project) throws IOException {
		return load(projectFile, project, true).result();
	}

	public static LoadOutcome load(Path projectFile, SkeletonProject project, boolean defaultShowAllSkeletons)
		throws IOException {
		return load(projectFile, project, defaultShowAllSkeletons, false);
	}

	/** Ignores authoring fields even in older full-state root sidecars written before view-only persistence. */
	public static LoadOutcome loadViewState(Path projectFile, SkeletonProject project, boolean defaultShowAllSkeletons)
		throws IOException {
		return load(projectFile, project, defaultShowAllSkeletons, true);
	}

	private static LoadOutcome load(Path projectFile, SkeletonProject project, boolean defaultShowAllSkeletons,
		boolean viewOnly) throws IOException {
		return load(projectFile, project, defaultShowAllSkeletons, viewOnly, false);
	}

	/** Restores a packaged sidecar while rejecting PSD paths that escape its extracted directory. */
	static LoadOutcome loadPortable(Path projectFile, SkeletonProject project, boolean defaultShowAllSkeletons)
		throws IOException {
		return load(projectFile, project, defaultShowAllSkeletons, false, true);
	}

	private static LoadOutcome load(Path projectFile, SkeletonProject project, boolean defaultShowAllSkeletons,
		boolean viewOnly, boolean portableOnly) throws IOException {
		Path source = requireExistingSpineProject(projectFile);
		if (project == null || project.size() == 0)
			throw new IllegalArgumentException("Project state requires at least one skeleton");
		Path sidecar = sidecarPath(source);
		if (!Files.isRegularFile(sidecar)) return new LoadOutcome(LoadResult.NOT_FOUND, defaultShowAllSkeletons);
		long byteCount = Files.size(sidecar);
		if (byteCount <= 0L || byteCount > MAX_STATE_BYTES)
			throw new IOException("OpenSpine project state has an invalid size: " + byteCount);
		ProjectState state;
		try {
			state = json().fromJson(ProjectState.class, Files.readString(sidecar, StandardCharsets.UTF_8));
		} catch (RuntimeException error) {
			throw new IOException("OpenSpine project state is not valid JSON", error);
		}
		if (state == null || state.version < 1 || state.version > FORMAT_VERSION)
			throw new IOException("Unsupported OpenSpine project state version: "
				+ (state == null ? "missing" : state.version));
		if (!sourceMatches(source, state.source))
			return new LoadOutcome(LoadResult.SOURCE_CHANGED, defaultShowAllSkeletons);
		restore(project, state, viewOnly || state.viewOnly, sidecar.getParent(), portableOnly);
		return new LoadOutcome(LoadResult.RESTORED, state.showAllSkeletons);
	}

	/** Serializes editor-only state without requiring a persistent source file. */
	public static String snapshotEditorState(SkeletonProject project, boolean showAllSkeletons)
		throws IOException {
		if (project == null || project.size() == 0)
			throw new IllegalArgumentException("Project state requires at least one skeleton");
		return json().prettyPrint(snapshot(null, project, showAllSkeletons)) + System.lineSeparator();
	}

	/** Restores editor-only state from an autosave snapshot without source fingerprint checks. */
	public static LoadOutcome restoreEditorState(Path snapshotFile, SkeletonProject project,
		boolean defaultShowAllSkeletons) throws IOException {
		if (snapshotFile == null || !Files.isRegularFile(snapshotFile))
			return new LoadOutcome(LoadResult.NOT_FOUND, defaultShowAllSkeletons);
		if (project == null || project.size() == 0)
			throw new IllegalArgumentException("Project state requires at least one skeleton");
		long byteCount = Files.size(snapshotFile);
		if (byteCount <= 0L || byteCount > MAX_STATE_BYTES)
			throw new IOException("OpenSpine autosave state has an invalid size: " + byteCount);
		ProjectState state = parseState(Files.readString(snapshotFile, StandardCharsets.UTF_8));
		restore(project, state, state.viewOnly, snapshotFile.toAbsolutePath().normalize().getParent(), false);
		return new LoadOutcome(LoadResult.RESTORED, state.showAllSkeletons);
	}

	private static ProjectState snapshot(Path source, SkeletonProject project, boolean showAllSkeletons)
		throws IOException {
		ProjectState state = new ProjectState();
		state.version = FORMAT_VERSION;
		state.showAllSkeletons = showAllSkeletons;
		if (source != null) {
			state.source = new SourceState();
			state.source.fileName = source.getFileName().toString();
			state.source.size = Files.size(source);
			state.source.modifiedMillis = Files.getLastModifiedTime(source).toMillis();
			state.source.sha256 = sha256(source);
		}
		state.activeSkeleton = project.activeIndex();
		List<SkeletonDocument> documents = project.documents();
		for (int index = 0; index < documents.size(); index++) {
			SkeletonDocument document = documents.get(index);
			SkeletonState skeleton = new SkeletonState();
			skeleton.name = project.nameAt(index);
			if (document.data != null) {
				skeleton.imagesPath = document.data.getImagesPath();
				skeleton.audioPath = document.data.getAudioPath();
				skeleton.x = document.data.getX();
				skeleton.y = document.data.getY();
				skeleton.width = document.data.getWidth();
				skeleton.height = document.data.getHeight();
				skeleton.referenceScale = document.data.getReferenceScale();
				skeleton.fps = document.data.getFps();
			}
			skeleton.animation = document.getCurrentAnimationName();
			skeleton.time = finiteOrZero(document.getCurrentTime());
			skeleton.skin = document.getCurrentSkinName();
			skeleton.previewSkins = new ArrayList<>(document.getPreviewSkinNames());
			Bone selected = document.getSelectedBone();
			skeleton.selectedBone = selected == null ? "" : selected.getData().getName();
			skeleton.selectedBones = new ArrayList<>(document.getSelectedBones().stream()
				.map(bone -> bone.getData().getName()).toList());
			skeleton.selectionKind = document.getSelectionKind().name();
			if (document.getSelectedConstraint() != null) {
				skeleton.selectedConstraintType = document.getSelectedConstraint().getClass().getSimpleName();
				skeleton.selectedConstraint = document.getSelectedConstraint().getName();
			}
			skeleton.selectedConstraintRelation = document.getSelectedConstraintRelation();
			skeleton.selectedSlot = document.getSelectedSlotName();
			skeleton.selectedAttachmentPlaceholder = document.getSelectedAttachmentPlaceholder();
			skeleton.selectedAttachment = document.getSelectedAttachmentName();
			skeleton.playing = document.playing;
			skeleton.looping = document.looping;
			skeleton.playbackSpeed = document.playbackSpeed;
			skeleton.defaultAnimationMix = document.defaultAnimationMix();
			for (SkeletonDocument.AnimationMix mix : document.animationMixes()) {
				AnimationMixState savedMix = new AnimationMixState();
				savedMix.from = mix.from();
				savedMix.to = mix.to();
				savedMix.duration = mix.duration();
				skeleton.animationMixes.add(savedMix);
			}
			SkeletonDocument.PsdSource psdSource = document.psdSource();
			if (psdSource != null) {
				skeleton.psdSource = new PsdSourceState();
				skeleton.psdSource.path = psdSource.sourcePath();
				skeleton.psdSource.ignoreHiddenLayers = psdSource.ignoreHiddenLayers();
				skeleton.psdSource.canvasWidth = psdSource.canvasWidth();
				skeleton.psdSource.canvasHeight = psdSource.canvasHeight();
				for (SkeletonDocument.PsdLayerMapping mapping : psdSource.layers()) {
					PsdLayerState savedLayer = new PsdLayerState();
					savedLayer.sourceIndex = mapping.sourceIndex();
					savedLayer.sourceName = mapping.sourceName();
					savedLayer.regionName = mapping.regionName();
					savedLayer.skinName = mapping.skinName();
					savedLayer.slotName = mapping.slotName();
					savedLayer.placeholder = mapping.placeholder();
					savedLayer.visible = mapping.visible();
					skeleton.psdSource.layers.add(savedLayer);
				}
			}
			skeleton.physicsSimulate = document.isPhysicsSimulationEnabled();
			skeleton.physicsDeterministic = document.isPhysicsDeterministic();
			ArrayList<SkeletonDocument.BoneEditorState> boneStates =
				new ArrayList<>(document.boneEditorStates());
			boneStates.sort(Comparator.comparing(SkeletonDocument.BoneEditorState::bone));
			for (SkeletonDocument.BoneEditorState boneState : boneStates) {
				BoneState savedBone = new BoneState();
				savedBone.name = boneState.bone();
				savedBone.selectable = boneState.selectable();
				savedBone.nameVisible = boneState.nameVisible();
				skeleton.bones.add(savedBone);
			}
			skeleton.axisSeparation.addAll(document.axisSeparationPreferences());
			skeleton.axisSeparation.sort(String::compareTo);
			ArrayList<SkeletonDocument.AxisCombineState> axisCombines =
				new ArrayList<>(document.axisCombineStates().values());
			axisCombines.sort(Comparator.comparing(SkeletonDocument.AxisCombineState::animation)
				.thenComparing(SkeletonDocument.AxisCombineState::bone)
				.thenComparing(SkeletonDocument.AxisCombineState::group));
			for (SkeletonDocument.AxisCombineState combinedState : axisCombines) {
				AxisCombineState saved = new AxisCombineState();
				saved.animation = combinedState.animation();
				saved.bone = combinedState.bone();
				saved.group = combinedState.group();
				saved.xBeforeY = combinedState.xBeforeY();
				saved.xFrames = combinedState.xFrames();
				saved.xCurves = combinedState.xCurves();
				saved.yFrames = combinedState.yFrames();
				saved.yCurves = combinedState.yCurves();
				saved.combinedFrames = combinedState.combinedFrames();
				saved.combinedCurves = combinedState.combinedCurves();
				skeleton.axisCombines.add(saved);
			}
			ArrayList<Map.Entry<String, SkeletonDocument.GraphViewState>> graphEntries =
				new ArrayList<>(document.graphViewStates().entrySet());
			graphEntries.sort(Map.Entry.comparingByKey());
			for (Map.Entry<String, SkeletonDocument.GraphViewState> entry : graphEntries) {
				SkeletonDocument.GraphViewState view = entry.getValue();
				if (!validView(view)) continue;
				GraphState graph = new GraphState();
				graph.key = entry.getKey();
				graph.timeMin = view.timeMin();
				graph.timeMax = view.timeMax();
				graph.valueMin = view.valueMin();
				graph.valueMax = view.valueMax();
				graph.selectedChannel = view.selectedChannel();
				graph.visibleChannels = view.visibleChannels();
				graph.selectionStored = view.selectionStored();
				graph.selectedTimes = view.selectedTimes();
				graph.selectedChannels = view.selectedChannels();
				graph.editTime = view.editTime();
				graph.editValue = view.editValue();
				graph.snap = view.snap();
				graph.autoFrame = view.autoFrame();
				graph.separateHandles = view.separateHandles();
				graph.transformMode = view.transformMode();
				skeleton.graphs.add(graph);
			}
			ArrayList<Map.Entry<String, Set<SkeletonDocument.MeshWeightLock>>> weightEntries =
				new ArrayList<>(document.meshWeightLockStates().entrySet());
			weightEntries.sort(Map.Entry.comparingByKey());
			for (Map.Entry<String, Set<SkeletonDocument.MeshWeightLock>> entry : weightEntries) {
				MeshWeightState weights = new MeshWeightState();
				weights.key = entry.getKey();
				ArrayList<SkeletonDocument.MeshWeightLock> sortedLocks = new ArrayList<>(entry.getValue());
				sortedLocks.sort(Comparator.comparingInt(SkeletonDocument.MeshWeightLock::vertex)
					.thenComparingInt(SkeletonDocument.MeshWeightLock::boneIndex));
				for (SkeletonDocument.MeshWeightLock lock : sortedLocks) {
					MeshWeightLockState savedLock = new MeshWeightLockState();
					savedLock.vertex = lock.vertex();
					savedLock.boneIndex = lock.boneIndex();
					weights.locks.add(savedLock);
				}
				if (!weights.locks.isEmpty()) skeleton.meshWeightLocks.add(weights);
			}
			SkeletonDocument.TreeViewState tree = document.treeViewState();
			skeleton.tree.filter = tree.filter();
			skeleton.tree.scrollX = tree.scrollX();
			skeleton.tree.scrollY = tree.scrollY();
			skeleton.tree.expandedValues.addAll(tree.expandedValues());
			skeleton.tree.treeValues.addAll(tree.treeValues());
			skeleton.tree.expandedValues.sort(String::compareTo);
			skeleton.tree.treeValues.sort(String::compareTo);
			SkeletonDocument.TimelineViewState timeline = document.timelineViewState();
			skeleton.timeline.animation = timeline.animation();
			skeleton.timeline.curveView = timeline.curveView();
			skeleton.timeline.curveSelectedChannel = timeline.curveSelectedChannel();
			skeleton.timeline.curveTrackKey = timeline.curveTrackKey();
			skeleton.timeline.scrollX = timeline.scrollX();
			skeleton.timeline.scrollY = timeline.scrollY();
			skeleton.timeline.selectionStored = timeline.selectionStored();
			skeleton.timeline.selectedTrackKeys = timeline.selectedTrackKeys();
			skeleton.timeline.selectedTimes = timeline.selectedTimes();
			skeleton.timeline.selectedEventOccurrences = timeline.selectedEventOccurrences();
			SkeletonDocument.GraphWorkspaceState workspace = document.graphWorkspaceState();
			skeleton.graphWorkspace.open = workspace.open();
			skeleton.graphWorkspace.animation = workspace.animation();
			skeleton.graphWorkspace.filter = workspace.filter();
			skeleton.graphWorkspace.locked = workspace.locked();
			skeleton.graphWorkspace.detailTrackKey = workspace.detailTrackKey();
			skeleton.graphWorkspace.splitAmount = workspace.splitAmount();
			skeleton.graphWorkspace.scrollX = workspace.scrollX();
			skeleton.graphWorkspace.scrollY = workspace.scrollY();
			skeleton.graphWorkspace.x = workspace.x();
			skeleton.graphWorkspace.y = workspace.y();
			skeleton.graphWorkspace.width = workspace.width();
			skeleton.graphWorkspace.height = workspace.height();
			state.skeletons.add(skeleton);
		}
		return state;
	}

	private static void restore(SkeletonProject project, ProjectState state, boolean viewOnly,
		Path stateDirectory, boolean portableOnly) {
		Map<String, SkeletonState> savedByName = new LinkedHashMap<>();
		if (state.skeletons != null)
			for (SkeletonState saved : state.skeletons)
				if (saved != null && saved.name != null) savedByName.putIfAbsent(saved.name, saved);
		List<SkeletonDocument> documents = project.documents();
		for (int index = 0; index < documents.size(); index++) {
			SkeletonState saved = savedByName.get(project.nameAt(index));
			if (saved != null) restoreDocument(documents.get(index), saved, state.version, viewOnly,
				stateDirectory, portableOnly);
		}
		if (viewOnly) {
			// Uncommitted member reorders/removals must not select a different saved tab by index.
			if (state.skeletons != null && state.activeSkeleton >= 0 && state.activeSkeleton < state.skeletons.size()) {
				SkeletonState active = state.skeletons.get(state.activeSkeleton);
				if (active != null) for (int index = 0; index < project.size(); index++)
					if (project.nameAt(index).equals(active.name)) { project.switchTo(index); break; }
			}
			return;
		}
		if (state.activeSkeleton >= 0 && state.activeSkeleton < project.size()
			&& state.activeSkeleton != project.activeIndex()) project.switchTo(state.activeSkeleton);
	}

	private static void restoreDocument(SkeletonDocument document, SkeletonState saved, int version, boolean viewOnly,
		Path stateDirectory, boolean portableOnly) {
		if (document == null || document.data == null || document.skeleton == null) return;
		if (!viewOnly && version >= 10) {
			if (Float.isFinite(saved.x)) document.data.setX(saved.x);
			if (Float.isFinite(saved.y)) document.data.setY(saved.y);
			if (Float.isFinite(saved.width) && saved.width >= 0f) document.data.setWidth(saved.width);
			if (Float.isFinite(saved.height) && saved.height >= 0f) document.data.setHeight(saved.height);
			if (Float.isFinite(saved.referenceScale) && saved.referenceScale > 0f)
				document.data.setReferenceScale(saved.referenceScale);
			if (Float.isFinite(saved.fps) && saved.fps > 0f) document.data.setFps(saved.fps);
			if (saved.imagesPath != null) document.data.setImagesPath(saved.imagesPath);
			if (saved.audioPath != null) document.data.setAudioPath(saved.audioPath);
		}
		if (!viewOnly && version >= 11) {
			ArrayList<SkeletonDocument.AnimationMix> mixes = new ArrayList<>();
			if (saved.animationMixes != null) for (AnimationMixState mix : saved.animationMixes) {
				if (mix == null || mix.from == null || mix.to == null || mix.from.equals(mix.to)
					|| document.data.findAnimation(mix.from) == null || document.data.findAnimation(mix.to) == null
					|| !Float.isFinite(mix.duration) || mix.duration < 0f) continue;
				mixes.add(new SkeletonDocument.AnimationMix(mix.from, mix.to, mix.duration));
			}
			float defaultMix = Float.isFinite(saved.defaultAnimationMix) && saved.defaultAnimationMix >= 0f
				? saved.defaultAnimationMix : 0f;
			document.restoreAnimationMixSettings(defaultMix, mixes);
		}
		if (!viewOnly && version >= 12)
			document.restorePsdSource(restoredPsdSource(saved.psdSource, stateDirectory, portableOnly));
		document.setPlaying(false);
		if (saved.animation != null && !saved.animation.isBlank()) document.setAnimation(saved.animation);
		else if (version >= 7) document.clearAnimation();
		if (saved.previewSkins != null && !saved.previewSkins.isEmpty())
			document.restorePreviewSkins(saved.previewSkins);
		else if (saved.skin != null && !saved.skin.isBlank()) document.setSkin(saved.skin);
		document.setLooping(saved.looping);
		if (Float.isFinite(saved.playbackSpeed))
			document.playbackSpeed = Math.max(0f, Math.min(3f, saved.playbackSpeed));
		document.setPhysicsSimulationEnabled(saved.physicsSimulate);
		document.setPhysicsDeterministic(saved.physicsDeterministic);
		ArrayList<SkeletonDocument.BoneEditorState> boneStates = new ArrayList<>();
		if (saved.bones != null) for (BoneState state : saved.bones) {
			if (state == null || state.name == null || state.name.isBlank()
				|| document.data.findBone(state.name) == null) continue;
			boneStates.add(new SkeletonDocument.BoneEditorState(state.name,
				state.selectable, state.nameVisible));
		}
		if (!viewOnly) document.restoreBoneEditorStates(boneStates);
		if (Float.isFinite(saved.time) && saved.time >= 0f) document.seek(saved.time);
		document.setPlaying(saved.playing);
		document.restoreEditorSelection(saved.selectionKind, saved.selectedBone,
			saved.selectedConstraintType, saved.selectedConstraint, saved.selectedConstraintRelation, saved.selectedSlot,
			saved.selectedAttachmentPlaceholder, saved.selectedAttachment);
		if (document.getSelectionKind() == SkeletonDocument.SelectionKind.BONE && saved.selectedBones != null) {
			List<Bone> selected = saved.selectedBones.stream().filter(java.util.Objects::nonNull)
				.map(document.skeleton::findBone).filter(java.util.Objects::nonNull).toList();
			if (!selected.isEmpty()) document.setSelectedBones(selected);
		}
		if (!viewOnly) document.restoreAxisSeparationPreferences(saved.axisSeparation);
		ArrayList<SkeletonDocument.AxisCombineState> axisCombines = new ArrayList<>();
		if (saved.axisCombines != null) for (AxisCombineState state : saved.axisCombines) {
			if (!validAxisCombineState(document, state)) continue;
			axisCombines.add(new SkeletonDocument.AxisCombineState(state.animation, state.bone, state.group,
				state.xBeforeY, state.xFrames, state.xCurves, state.yFrames, state.yCurves,
				state.combinedFrames, state.combinedCurves));
		}
		if (!viewOnly) document.restoreAxisCombineStates(axisCombines);
		Map<String, SkeletonDocument.GraphViewState> views = new LinkedHashMap<>();
		if (saved.graphs != null) {
			for (GraphState graph : saved.graphs) {
				SkeletonDocument.GraphViewState view = restoredGraphView(graph);
				if (view != null) views.put(graph.key, view);
			}
		}
		document.restoreGraphViewStates(views);
		Map<String, Iterable<SkeletonDocument.MeshWeightLock>> locks = new LinkedHashMap<>();
		if (saved.meshWeightLocks != null) for (MeshWeightState weights : saved.meshWeightLocks) {
			if (weights == null || weights.key == null || weights.key.isBlank() || weights.locks == null) continue;
			int vertexCount = meshVertexCount(document, weights.key);
			if (vertexCount <= 0) continue;
			java.util.LinkedHashSet<SkeletonDocument.MeshWeightLock> restored = new java.util.LinkedHashSet<>();
			for (MeshWeightLockState lock : weights.locks)
				if (lock != null && lock.vertex >= 0 && lock.vertex < vertexCount && lock.boneIndex >= 0
					&& lock.boneIndex < document.data.getBones().size)
					restored.add(new SkeletonDocument.MeshWeightLock(lock.vertex, lock.boneIndex));
			if (!restored.isEmpty()) locks.put(weights.key, restored);
		}
		if (!viewOnly) document.restoreMeshWeightLocks(locks);
		TreeState tree = saved.tree == null ? new TreeState() : saved.tree;
		document.setTreeViewState(new SkeletonDocument.TreeViewState(tree.filter, tree.scrollX, tree.scrollY,
			new java.util.LinkedHashSet<>(tree.expandedValues == null ? List.of() : tree.expandedValues),
			new java.util.LinkedHashSet<>(tree.treeValues == null ? List.of() : tree.treeValues)));
		document.setTimelineViewState(restoredTimelineView(saved.timeline));
		document.setGraphWorkspaceState(restoredGraphWorkspaceState(document, saved.graphWorkspace));
	}

	private static SkeletonDocument.PsdSource restoredPsdSource(PsdSourceState saved,
		Path stateDirectory, boolean portableOnly) {
		if (saved == null || saved.path == null || saved.path.isBlank() || saved.layers == null
			|| saved.layers.isEmpty()) return null;
		try {
			Path path = Path.of(saved.path.trim());
			if (path.isAbsolute()) {
				if (portableOnly) return null;
				path = path.normalize();
			} else {
				if (stateDirectory == null) return null;
				Path base = stateDirectory.toAbsolutePath().normalize();
				path = base.resolve(path).normalize();
				if (portableOnly && !path.startsWith(base)) return null;
				if (portableOnly && (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
					|| !path.toRealPath().startsWith(base.toRealPath()))) return null;
			}
			ArrayList<SkeletonDocument.PsdLayerMapping> mappings = new ArrayList<>();
			for (PsdLayerState layer : saved.layers) {
				if (layer == null) return null;
				mappings.add(new SkeletonDocument.PsdLayerMapping(layer.sourceIndex, layer.sourceName,
					layer.regionName, layer.skinName, layer.slotName, layer.placeholder, layer.visible));
			}
			return new SkeletonDocument.PsdSource(path.toString(), saved.ignoreHiddenLayers,
				saved.canvasWidth, saved.canvasHeight, mappings);
		} catch (IOException | RuntimeException invalid) {
			return null;
		}
	}

	private static SkeletonDocument.GraphWorkspaceState restoredGraphWorkspaceState(
		SkeletonDocument document, GraphWorkspaceState workspace) {
		if (workspace == null) return SkeletonDocument.GraphWorkspaceState.empty();
		String animation = workspace.animation == null ? "" : workspace.animation;
		boolean validAnimation = animation.isEmpty() || document.data.findAnimation(animation) != null;
		return new SkeletonDocument.GraphWorkspaceState(workspace.open && validAnimation,
			validAnimation ? animation : "", workspace.filter, workspace.locked, workspace.detailTrackKey,
			workspace.splitAmount, workspace.scrollX, workspace.scrollY,
			workspace.x, workspace.y, workspace.width, workspace.height);
	}

	private static SkeletonDocument.TimelineViewState restoredTimelineView(TimelineState timeline) {
		if (timeline == null) return SkeletonDocument.TimelineViewState.empty();
		String[] trackKeys = timeline.selectedTrackKeys == null ? new String[0] : timeline.selectedTrackKeys;
		float[] times = timeline.selectedTimes == null ? new float[0] : timeline.selectedTimes;
		int[] occurrences = timeline.selectedEventOccurrences == null
			? new int[0] : timeline.selectedEventOccurrences;
		ArrayList<String> validTrackKeys = new ArrayList<>();
		ArrayList<Float> validTimes = new ArrayList<>();
		ArrayList<Integer> validOccurrences = new ArrayList<>();
		if (trackKeys.length == times.length && trackKeys.length == occurrences.length) {
			for (int index = 0; index < trackKeys.length; index++) {
				if (trackKeys[index] == null || trackKeys[index].isBlank()
					|| !Float.isFinite(times[index]) || times[index] < 0f || occurrences[index] < 0) continue;
				validTrackKeys.add(trackKeys[index]);
				validTimes.add(times[index]);
				validOccurrences.add(occurrences[index]);
			}
		}
		float[] cleanTimes = new float[validTimes.size()];
		int[] cleanOccurrences = new int[validOccurrences.size()];
		for (int index = 0; index < cleanTimes.length; index++) {
			cleanTimes[index] = validTimes.get(index);
			cleanOccurrences[index] = validOccurrences.get(index);
		}
		return new SkeletonDocument.TimelineViewState(timeline.animation, timeline.curveView,
			timeline.curveSelectedChannel, timeline.curveTrackKey, timeline.scrollX, timeline.scrollY,
			timeline.selectionStored, validTrackKeys.toArray(String[]::new), cleanTimes, cleanOccurrences);
	}

	private static SkeletonDocument.GraphViewState restoredGraphView(GraphState graph) {
		if (graph == null || graph.key == null || graph.key.isBlank()) return null;
		float[] selectedTimes = graph.selectedTimes == null ? new float[0] : graph.selectedTimes;
		int[] selectedChannels = graph.selectedChannels == null ? new int[0] : graph.selectedChannels;
		if (selectedTimes.length != selectedChannels.length) return null;
		SkeletonDocument.GraphViewState view = new SkeletonDocument.GraphViewState(graph.timeMin,
			graph.timeMax, graph.valueMin, graph.valueMax, graph.selectedChannel,
			graph.visibleChannels, graph.selectionStored, selectedTimes, selectedChannels,
			graph.editTime, graph.editValue, graph.snap, graph.autoFrame, graph.separateHandles,
			graph.transformMode);
		return validView(view) ? view : null;
	}

	private static int meshVertexCount(SkeletonDocument document, String key) {
		String[] tokens = key.split("\\u001f", -1);
		if (tokens.length != 3) return -1;
		SlotData slot = document.data.findSlot(tokens[1]);
		Skin skin = document.data.findSkin(tokens[0]);
		if (slot == null || skin == null) return -1;
		Attachment attachment = skin.getAttachment(slot.getIndex(), tokens[2]);
		return attachment instanceof MeshAttachment mesh ? mesh.getWorldVerticesLength() / 2 : -1;
	}

	private static boolean sourceMatches(Path source, SourceState saved) throws IOException {
		if (saved == null || saved.fileName == null
			|| !source.getFileName().toString().equalsIgnoreCase(saved.fileName)
			|| Files.size(source) != saved.size) return false;
		if (saved.sha256 != null && !saved.sha256.isBlank())
			return MessageDigest.isEqual(saved.sha256.getBytes(StandardCharsets.US_ASCII),
				sha256(source).getBytes(StandardCharsets.US_ASCII));
		return Files.getLastModifiedTime(source).toMillis() == saved.modifiedMillis;
	}

	private static String sha256(Path source) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
		try (var input = Files.newInputStream(source)) {
			byte[] buffer = new byte[65536];
			for (int count; (count = input.read(buffer)) >= 0; )
				if (count > 0) digest.update(buffer, 0, count);
		}
		StringBuilder hex = new StringBuilder(64);
		for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
		return hex.toString();
	}

	private static boolean validView(SkeletonDocument.GraphViewState view) {
		return view != null && Float.isFinite(view.timeMin()) && Float.isFinite(view.timeMax())
			&& Float.isFinite(view.valueMin()) && Float.isFinite(view.valueMax())
			&& view.timeMax() > view.timeMin() && view.valueMax() > view.valueMin()
			&& view.selectedChannel() >= 0 && validGraphSelection(view);
	}

	private static boolean validAxisCombineState(SkeletonDocument document, AxisCombineState state) {
		if (state == null || state.animation == null || state.animation.isBlank()
			|| state.bone == null || state.bone.isBlank() || state.group == null
			|| !(state.group.equals("TRANSLATE") || state.group.equals("SCALE") || state.group.equals("SHEAR"))
			|| document.data.findAnimation(state.animation) == null || document.data.findBone(state.bone) == null)
			return false;
		return validTimelineArrays(state.xFrames, 2, state.xCurves)
			&& validTimelineArrays(state.yFrames, 2, state.yCurves)
			&& validTimelineArrays(state.combinedFrames, 3, state.combinedCurves);
	}

	private static boolean validTimelineArrays(float[] frames, int entries, float[] curves) {
		if (frames == null || frames.length == 0 || frames.length % entries != 0 || curves == null) return false;
		int frameCount = frames.length / entries;
		if (curves.length < frameCount
			|| (curves.length - frameCount) % com.esotericsoftware.spine.Animation.CurveTimeline.BEZIER_SIZE != 0)
			return false;
		for (float value : frames) if (!Float.isFinite(value)) return false;
		for (float value : curves) if (!Float.isFinite(value)) return false;
		return true;
	}

	private static boolean validGraphSelection(SkeletonDocument.GraphViewState view) {
		float[] times = view.selectedTimes();
		int[] channels = view.selectedChannels();
		if (times.length != channels.length) return false;
		for (int index = 0; index < times.length; index++)
			if (!Float.isFinite(times[index]) || times[index] < 0f || channels[index] < 0) return false;
		return true;
	}

	private static float finiteOrZero(float value) { return Float.isFinite(value) ? Math.max(0f, value) : 0f; }

	private static Path requireSpineProject(Path projectFile) {
		if (projectFile == null) throw new IllegalArgumentException("Skeleton project path is required");
		Path source = projectFile.toAbsolutePath().normalize();
		String name = source.getFileName() == null ? ""
			: source.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!(name.endsWith(".spine") || name.endsWith(".json") || name.endsWith(".skel")))
			throw new IllegalArgumentException(
				"Project state is only supported for .spine, .json, or .skel sources");
		return source;
	}

	private static Path requireExistingSpineProject(Path projectFile) {
		Path source = requireSpineProject(projectFile);
		if (!Files.isRegularFile(source))
			throw new IllegalArgumentException("Spine project file does not exist: " + source);
		return source;
	}

	private static Json json() {
		Json json = new Json(JsonWriter.OutputType.json);
		json.setIgnoreUnknownFields(true);
		json.setQuoteLongValues(true);
		return json;
	}

	private static ProjectState parseState(String content) throws IOException {
		ProjectState state;
		try { state = json().fromJson(ProjectState.class, content); }
		catch (RuntimeException error) {
			throw new IOException("OpenSpine project state is not valid JSON", error);
		}
		if (state == null || state.version < 1 || state.version > FORMAT_VERSION)
			throw new IOException("Unsupported OpenSpine project state version: "
				+ (state == null ? "missing" : state.version));
		return state;
	}

	private static void moveAtomic(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static final class ProjectState {
		public int version;
		public boolean viewOnly;
		public SourceState source;
		public int activeSkeleton;
		public boolean showAllSkeletons = true;
		public ArrayList<SkeletonState> skeletons = new ArrayList<>();
	}

	public static final class SourceState {
		public String fileName;
		public long size;
		public long modifiedMillis;
		public String sha256;
	}

	public static final class SkeletonState {
		public String name;
		public String imagesPath;
		public String audioPath;
		public float x;
		public float y;
		public float width;
		public float height;
		public float referenceScale = 100f;
		public float fps = 30f;
		public String animation;
		public float time;
		public String skin;
		public ArrayList<String> previewSkins;
		public String selectedBone;
		public ArrayList<String> selectedBones;
		public String selectionKind = "BONE";
		public String selectedConstraintType;
		public String selectedConstraint;
		public String selectedConstraintRelation;
		public String selectedSlot;
		public String selectedAttachmentPlaceholder;
		public String selectedAttachment;
		public boolean playing;
		public boolean looping = true;
		public float playbackSpeed = 1f;
		public float defaultAnimationMix;
		public ArrayList<AnimationMixState> animationMixes = new ArrayList<>();
		public PsdSourceState psdSource;
		public boolean physicsSimulate = true;
		public boolean physicsDeterministic;
		public ArrayList<BoneState> bones = new ArrayList<>();
		public ArrayList<String> axisSeparation = new ArrayList<>();
		public ArrayList<AxisCombineState> axisCombines = new ArrayList<>();
		public ArrayList<GraphState> graphs = new ArrayList<>();
		public ArrayList<MeshWeightState> meshWeightLocks = new ArrayList<>();
		public TreeState tree = new TreeState();
		public TimelineState timeline = new TimelineState();
		public GraphWorkspaceState graphWorkspace = new GraphWorkspaceState();
	}

	public static final class PsdSourceState {
		public String path;
		public boolean ignoreHiddenLayers;
		public int canvasWidth;
		public int canvasHeight;
		public ArrayList<PsdLayerState> layers = new ArrayList<>();
	}

	public static final class PsdLayerState {
		public int sourceIndex;
		public String sourceName;
		public String regionName;
		public String skinName;
		public String slotName;
		public String placeholder;
		public boolean visible = true;
	}

	public static final class AnimationMixState {
		public String from;
		public String to;
		public float duration;
	}

	public static final class BoneState {
		public String name;
		public boolean selectable = true;
		public boolean nameVisible;
	}

	public static final class AxisCombineState {
		public String animation;
		public String bone;
		public String group;
		public boolean xBeforeY = true;
		public float[] xFrames = new float[0];
		public float[] xCurves = new float[0];
		public float[] yFrames = new float[0];
		public float[] yCurves = new float[0];
		public float[] combinedFrames = new float[0];
		public float[] combinedCurves = new float[0];
	}

	public static final class GraphState {
		public String key;
		public float timeMin;
		public float timeMax;
		public float valueMin;
		public float valueMax;
		public int selectedChannel;
		public long visibleChannels;
		public boolean selectionStored;
		public float[] selectedTimes = new float[0];
		public int[] selectedChannels = new int[0];
		public boolean editTime = true;
		public boolean editValue = true;
		public boolean snap = true;
		public boolean autoFrame;
		public boolean separateHandles;
		public int transformMode;
	}

	public static final class MeshWeightState {
		public String key;
		public ArrayList<MeshWeightLockState> locks = new ArrayList<>();
	}

	public static final class MeshWeightLockState {
		public int vertex;
		public int boneIndex;
	}

	public static final class TreeState {
		public String filter = "";
		public float scrollX;
		public float scrollY;
		public ArrayList<String> expandedValues = new ArrayList<>();
		public ArrayList<String> treeValues = new ArrayList<>();
	}

	public static final class TimelineState {
		public String animation = "";
		public boolean curveView;
		public int curveSelectedChannel;
		public String curveTrackKey = "";
		public float scrollX;
		public float scrollY;
		public boolean selectionStored;
		public String[] selectedTrackKeys = new String[0];
		public float[] selectedTimes = new float[0];
		public int[] selectedEventOccurrences = new int[0];
	}

	public static final class GraphWorkspaceState {
		public boolean open;
		public String animation = "";
		public String filter = "";
		public boolean locked;
		public String detailTrackKey = "";
		public float splitAmount = 0.62f;
		public float scrollX;
		public float scrollY;
		public float x = -1f;
		public float y = -1f;
		public float width;
		public float height;
	}

	private ProjectStateStore() {}
}
