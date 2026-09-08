package com.openspine.model;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BonePose;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.VertexAttachment;

/** Retains the references changed by a structural edit, without copying or disposing textures. */
public final class SkeletonStructureSnapshot {
	private final SkeletonEditor editor;
	private final SkeletonData data;
	private final Array<BoneData> bones;
	private final Array<SlotData> slots;
	private final Array<EventData> events;
	private final List<Runnable> restorers = new ArrayList<>();
	private final Map<VertexAttachment, int[]> attachmentWeights = new IdentityHashMap<>();
	private final Map<String, SkeletonDocument.GraphViewState> graphs;
	private final Map<String, Set<SkeletonDocument.MeshWeightLock>> locks;
	private final Set<String> separate;
	private final Map<String, SkeletonDocument.AxisCombineState> combined;
	private final Set<SkeletonDocument.BoneEditorState> boneStates;
	private final SkeletonDocument.TreeViewState tree;
	private final SkeletonDocument.TimelineViewState timeline;
	private final SkeletonDocument.GraphWorkspaceState graphWorkspace;
	private final List<String> selection;
	private final Map<String, BonePose> poses = new java.util.LinkedHashMap<>();
	private final String kind, selectedBone, constraintType, constraintName, relation, slot, placeholder, attachment;
	private final boolean dirty;
	private final long revision;
	private record AttachmentBinding(int slotIndex, String placeholder, Attachment attachment) {}

	public static SkeletonStructureSnapshot capture(SkeletonEditor editor) {
		if (editor == null || editor.doc.data == null) throw new IllegalArgumentException("Skeleton data is required");
		return new SkeletonStructureSnapshot(editor);
	}

	private SkeletonStructureSnapshot(SkeletonEditor editor) {
		this.editor = editor;
		SkeletonDocument doc = editor.doc;
		data = doc.data;
		bones = new Array<>(data.getBones());
		slots = new Array<>(data.getSlots());
		events = new Array<>(data.getEvents());
		dirty = doc.dirty;
		revision = doc.revision;
		graphs = doc.graphViewStates();
		locks = doc.meshWeightLockStates();
		separate = doc.axisSeparationPreferences();
		combined = doc.axisCombineStates();
		boneStates = doc.boneEditorStates();
		tree = doc.treeViewState();
		timeline = doc.timelineViewState();
		graphWorkspace = doc.graphWorkspaceState();
		selection = doc.getSelectedBones().stream().map(bone -> bone.getData().getName()).toList();
		kind = doc.getSelectionKind().name();
		selectedBone = doc.getSelectedBone() == null ? "" : doc.getSelectedBone().getData().getName();
		ConstraintData<?, ?> constraint = doc.getSelectedConstraint();
		constraintType = constraint == null ? "" : constraint.getClass().getSimpleName();
		constraintName = constraint == null ? "" : constraint.getName();
		relation = doc.getSelectedConstraintRelation();
		slot = doc.getSelectedSlotName();
		placeholder = doc.getSelectedAttachmentPlaceholder();
		attachment = doc.getSelectedAttachmentName();
		if (doc.poseMode && doc.skeleton != null) for (Bone bone : doc.skeleton.getBones()) {
			BonePose pose = new BonePose();
			pose.set(bone.getPose());
			poses.put(bone.getData().getName(), pose);
		}
		IdentityHashMap<Attachment, Boolean> seen = new IdentityHashMap<>();
		IdentityHashMap<int[], int[]> capturedWeights = new IdentityHashMap<>();
		for (Skin skin : data.getSkins()) {
			Array<BoneData> skinBones = new Array<>(skin.getBones());
			List<AttachmentBinding> entries = new ArrayList<>();
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				entries.add(new AttachmentBinding(entry.getSlotIndex(), entry.getPlaceholder(), entry.getAttachment()));
				Attachment attachment = entry.getAttachment();
				if (seen.put(attachment, true) == null) {
					int[] timelineSlots = attachment.getTimelineSlots() == null ? null : attachment.getTimelineSlots().clone();
					restorers.add(() -> attachment.setTimelineSlots(timelineSlots == null ? null : timelineSlots.clone()));
					if (attachment instanceof VertexAttachment vertex) {
						int[] weights = vertex.getBones();
						attachmentWeights.put(vertex, weights == null ? null
							: capturedWeights.computeIfAbsent(weights, value -> value.clone()));
					}
					if (attachment instanceof ClippingAttachment clipping) {
						SlotData end = clipping.getEndSlot();
						restorers.add(() -> clipping.setEndSlot(end));
					}
				}
			}
			restorers.add(() -> {
				skin.getBones().clear();
				skin.getBones().addAll(skinBones);
				Array<Skin.SkinEntry> current = new Array<>();
				for (Skin.SkinEntry entry : skin.getAttachments()) current.add(entry);
				for (Skin.SkinEntry entry : current) skin.removeAttachment(entry.getSlotIndex(), entry.getPlaceholder());
				for (AttachmentBinding entry : entries)
					skin.setAttachment(entry.slotIndex(), entry.placeholder(), entry.attachment());
			});
		}
		for (ConstraintData<?, ?> item : data.getConstraints()) captureConstraint(item);
		AnimationEditor animationEditor = new AnimationEditor(doc);
		for (Animation animation : data.getAnimations()) {
			Array<Animation.Timeline> tracks = copyTimelines(animationEditor, animation.getTimelines());
			IntArray activeBones = new IntArray(animation.getBones());
			float duration = animation.getDuration();
			restorers.add(() -> {
				animation.setTimelines(copyTimelines(animationEditor, tracks), new IntArray(activeBones));
				animation.setDuration(duration);
			});
		}
	}

	private void captureConstraint(ConstraintData<?, ?> item) {
		if (item instanceof IkConstraintData ik) {
			Array<BoneData> constrained = new Array<>(ik.getBones());
			BoneData target = ik.getTarget();
			restorers.add(() -> { replace(ik.getBones(), constrained); ik.setTarget(target); });
		} else if (item instanceof TransformConstraintData transform) {
			Array<BoneData> constrained = new Array<>(transform.getBones());
			BoneData source = transform.getSource();
			restorers.add(() -> { replace(transform.getBones(), constrained); transform.setSource(source); });
		} else if (item instanceof PathConstraintData path) {
			Array<BoneData> constrained = new Array<>(path.getBones());
			SlotData target = path.getSlot();
			restorers.add(() -> { replace(path.getBones(), constrained); path.setSlot(target); });
		} else if (item instanceof PhysicsConstraintData physics) {
			BoneData bone = physics.getBone();
			restorers.add(() -> physics.setBone(bone));
		} else if (item instanceof SliderData slider) {
			BoneData bone = slider.getBone();
			boolean loop = slider.getLoop();
			restorers.add(() -> { slider.setBone(bone); slider.setLoop(loop); });
		}
	}

	private static Array<Animation.Timeline> copyTimelines(AnimationEditor editor, Array<Animation.Timeline> source) {
		Array<Animation.Timeline> copy = new Array<>(true, source.size, Animation.Timeline[]::new);
		for (Animation.Timeline timeline : source) copy.add(editor.cloneTimeline(timeline));
		return copy;
	}

	public void restore() {
		SkeletonDocument doc = editor.doc;
		if (doc.data != data) throw new IllegalStateException("The structural snapshot belongs to another document");
		replace(data.getBones(), bones);
		replace(data.getSlots(), slots);
		replace(data.getEvents(), events);
		for (Runnable restorer : restorers) restorer.run();
		IdentityHashMap<int[], int[]> restoredWeights = new IdentityHashMap<>();
		for (var entry : attachmentWeights.entrySet()) {
			int[] weights = entry.getValue();
			entry.getKey().setBones(weights == null ? null
				: restoredWeights.computeIfAbsent(weights, value -> value.clone()));
		}
		doc.restoreGraphViewStates(graphs);
		doc.restoreMeshWeightLocks(locks);
		doc.restoreAxisSeparationPreferences(separate);
		doc.restoreAxisCombineStates(combined.values());
		doc.restoreBoneEditorStates(boneStates);
		doc.setTreeViewState(tree);
		doc.setTimelineViewState(timeline);
		doc.setGraphWorkspaceState(graphWorkspace);
		editor.rebuildSkeleton();
		if (doc.poseMode && !poses.isEmpty()) {
			for (Bone bone : doc.skeleton.getBones()) {
				BonePose pose = poses.get(bone.getData().getName());
				if (pose != null) bone.getPose().set(pose);
			}
			doc.updateWorldTransform(Physics.pose);
		}
		doc.restoreEditorSelection(kind, selectedBone, constraintType, constraintName, relation, slot, placeholder, attachment);
		if (doc.getSelectionKind() == SkeletonDocument.SelectionKind.BONE) {
			List<Bone> selected = new ArrayList<>();
			for (String name : selection) {
				Bone bone = doc.skeleton.findBone(name);
				if (bone != null) selected.add(bone);
			}
			doc.setSelectedBones(selected);
		}
		doc.markDirty();
	}

	void rollback() {
		restore();
		editor.doc.dirty = dirty;
		editor.doc.revision = revision;
	}

	private static <T> void replace(Array<T> target, Array<T> values) {
		target.clear();
		target.addAll(values);
	}
}
