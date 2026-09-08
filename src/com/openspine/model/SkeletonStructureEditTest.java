package com.openspine.model;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.io.SkeletonExporter;

/** Reindexing must preserve sampled animation, references, and reversible editor state. */
public final class SkeletonStructureEditTest {
	public static void main(String[] args) {
		deletesInterleavedSubtreeAndRestoresHistory();
		reparentsToLaterBoneAndRejectsCycles();
		deletesMiddleSlotWithGlobalAndFolderOrder();
		remapsLinkedMeshActivationOnDeleteAndMove();
		preservesSharedLinkedWeightsWhenBoneIndicesChange();
		rollsBackFailedSlotReorder();
		rejectsReparentingThatInvalidatesConstraints();
		System.out.println("ALL SKELETON STRUCTURE EDIT TESTS PASSED");
	}

	private static void deletesInterleavedSubtreeAndRestoresHistory() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		BoneData removed = editor.addBone("removed", doc.data.getBones().first());
		BoneData keep = editor.addBone("keep", doc.data.getBones().first());
		editor.addBone("removed-child", removed);
		BoneData leaf = editor.addBone("keep-child", keep);
		SlotData slot = editor.addSlot("box-slot", keep);
		BoundingBoxAttachment box = new BoundingBoxAttachment("box");
		box.setWorldVerticesLength(6);
		box.setBones(new int[] {1, 4, 1, 4, 1, 2});
		box.setVertices(new float[] {0, 0, 1, 10, 0, 1, 0, 10, 1});
		Skin skin = new Skin("default");
		skin.setAttachment(0, "box", box);
		skin.getBones().add(leaf);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		slot.setAttachmentName("box");
		PhysicsConstraintData physics = new PhysicsConstraintData("spring");
		physics.setBone(leaf);
		physics.setStep(1f / 60f);
		physics.getSetupPose().setMassInverse(1f);
		doc.data.getConstraints().add(physics);
		Animation animation = rotation(doc, "move", 2);
		Animation.RotateTimeline original = (Animation.RotateTimeline)animation.getTimelines().first();
		Animation.InheritTimeline inherit = new Animation.InheritTimeline(1, 4);
		inherit.setFrame(0, 0f, BoneData.Inherit.onlyTranslation);
		Array<Animation.Timeline> tracks = new Array<>(animation.getTimelines());
		tracks.add(inherit);
		animation.setTimelines(tracks, new IntArray(new int[] {1, 2, 4}));
		editor.rebuildSkeleton();
		doc.setAnimation("move");
		doc.scrubTo(0.4f);
		float sampled = doc.skeleton.findBone("keep").getAppliedPose().getRotation();
		doc.setSelectedBones(List.of(doc.skeleton.findBone("keep"), doc.skeleton.findBone("keep-child")));
		String lockKey = SkeletonDocument.meshWeightStateKey("default", "box-slot", "box");
		doc.setMeshWeightLocks(lockKey, Set.of(new SkeletonDocument.MeshWeightLock(0, 4)));
		doc.setAxisSeparationPreferred("move", "removed", "TRANSLATE", true);
		var graph = new SkeletonDocument.GraphViewState(0, 1, 0, 30, 0, 1, false, new float[0], new int[0]);
		doc.setGraphViewState(SkeletonDocument.graphViewStateKey("move", original), graph);
		String beforeJson = SkeletonExporter.toJson(doc);
		var before = SkeletonStructureSnapshot.capture(editor);
		check(editor.getDeleteBlockReason(removed) == null, "only actual subtree references block deletion");
		check(editor.deleteBone(removed), "interleaved subtree deleted");
		check(doc.data.getBones().size == 3, "surviving bone count");
		check(doc.data.findBone("keep").getIndex() == 1 && doc.data.findBone("keep-child").getIndex() == 2,
			"survivors have dense indices");
		check(doc.data.findSlot("box-slot").getBoneData() == doc.data.findBone("keep"), "slot reference remapped");
		check(physics.getBone() == doc.data.findBone("keep-child") && skin.getBones().first() == physics.getBone(),
			"constraint and skin references remapped");
		check(Arrays.equals(box.getBones(), new int[] {1, 2, 1, 2, 1, 1}), "weighted influences remapped");
		check(doc.meshWeightLocks(lockKey).contains(new SkeletonDocument.MeshWeightLock(0, 2)), "weight lock remapped");
		check(Arrays.equals(animation.getBones().toArray(), new int[] {1, 2}), "activation bones remapped");
		Animation.RotateTimeline remapped = (Animation.RotateTimeline)animation.getTimelines().first();
		check(remapped.getBoneIndex() == 1, "rotation track remapped");
		check(((Animation.InheritTimeline)animation.getTimelines().get(1)).getBoneIndex() == 2, "inherit track remapped");
		check(doc.graphViewState(SkeletonDocument.graphViewStateKey("move", remapped)) == graph, "graph state remapped");
		doc.scrubTo(0.4f);
		near(sampled, doc.skeleton.findBone("keep").getAppliedPose().getRotation(), "Bezier sampling unchanged");
		var after = SkeletonStructureSnapshot.capture(editor);
		String afterJson = SkeletonExporter.toJson(doc);
		EditHistory history = new EditHistory(doc);
		history.record("Delete", before::restore, after::restore);
		check(history.undo(), "delete is undoable");
		check(beforeJson.equals(SkeletonExporter.toJson(doc)), "undo restores runtime JSON");
		check(doc.getSelectedBones().size() == 2, "undo restores complete selection");
		check(doc.isAxisSeparationPreferred("move", "removed", "TRANSLATE"), "undo restores removed editor state");
		check(history.redo() && afterJson.equals(SkeletonExporter.toJson(doc)), "redo restores edited runtime JSON");
	}

	private static void reparentsToLaterBoneAndRejectsCycles() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		BoneData moved = editor.addBone("moved", doc.data.getBones().first());
		editor.addBone("child", moved);
		BoneData later = editor.addBone("later", doc.data.getBones().first());
		Animation animation = rotation(doc, "rotate", moved.getIndex());
		editor.rebuildSkeleton();
		var before = SkeletonStructureSnapshot.capture(editor);
		String original = SkeletonExporter.toJson(doc);
		check(editor.setBoneParent(moved, later), "later parent accepted");
		check(doc.data.findBone("moved").getParent() == doc.data.findBone("later"), "new parent is live data");
		for (BoneData bone : doc.data.getBones())
			check(bone.getParent() == null || bone.getParent().getIndex() < bone.getIndex(), "parent precedes child");
		check(((Animation.RotateTimeline)animation.getTimelines().first()).getBoneIndex() == doc.data.findBone("moved").getIndex(),
			"reparent remaps tracks");
		check(!editor.setBoneParent(doc.data.findBone("later"), doc.data.findBone("child")), "cycles are rejected");
		check(!editor.setBoneParent(doc.data.findBone("root"), doc.data.findBone("child")), "root reparent rejected");
		before.restore();
		check(original.equals(SkeletonExporter.toJson(doc)), "reparent undo restores original array order");
	}

	private static void deletesMiddleSlotWithGlobalAndFolderOrder() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		for (String name : List.of("a", "removed", "b", "c")) editor.addSlot(name, doc.data.getBones().first());
		Skin skin = new Skin("default");
		ClippingAttachment clip = new ClippingAttachment("clip");
		clip.setWorldVerticesLength(6);
		clip.setVertices(new float[] {0, 0, 10, 0, 0, 10});
		clip.setEndSlot(doc.data.findSlot("c"));
		skin.setAttachment(0, "clip", clip);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		Animation animation = new Animation("order");
		Animation.DrawOrderTimeline global = new Animation.DrawOrderTimeline(1);
		global.setFrame(0, 0f, new int[] {3, 1, 2, 0});
		Animation.DrawOrderFolderTimeline folder = new Animation.DrawOrderFolderTimeline(1, new int[] {1, 2, 3}, 4);
		folder.setFrame(0, 0f, new int[] {2, 0, 1});
		Animation.AlphaTimeline alpha = new Animation.AlphaTimeline(1, 0, 3);
		alpha.setFrame(0, 0f, 0.25f);
		Array<Animation.Timeline> tracks = new Array<>(true, 3, Animation.Timeline[]::new);
		tracks.addAll(global, folder, alpha);
		animation.setTimelines(tracks, new IntArray());
		doc.data.getAnimations().add(animation);
		editor.rebuildSkeleton();
		String original = SkeletonExporter.toJson(doc);
		var before = SkeletonStructureSnapshot.capture(editor);
		check(editor.deleteSlot(doc.data.findSlot("removed")), "middle empty slot deleted");
		check(clip.getEndSlot() == doc.data.findSlot("c"), "clipping endpoint remapped");
		check(skin.getAttachment(0, "clip") == clip, "attachment identity retained");
		check(Arrays.equals(((Animation.DrawOrderTimeline)animation.getTimelines().get(0)).getDrawOrders()[0], new int[] {2, 1, 0}),
			"global order removes deleted index");
		Animation.DrawOrderFolderTimeline next = (Animation.DrawOrderFolderTimeline)animation.getTimelines().get(1);
		check(Arrays.equals(next.getSlots(), new int[] {1, 2}), "folder membership uses new slot indices");
		check(Arrays.equals(next.getDrawOrders()[0], new int[] {1, 0}), "folder order uses local membership indices");
		check(((Animation.AlphaTimeline)animation.getTimelines().get(2)).getSlotIndex() == 2, "slot color track remapped");
		doc.setAnimation("order");
		doc.scrubTo(0f);
		near(0.25f, doc.skeleton.findSlot("c").getAppliedPose().getColor().a, "remapped color applies");
		List<String> drawOrder = drawOrderNames(doc);
		SkeletonDocument saved = saveAndReload(doc);
		saved.setAnimation("order");
		saved.scrubTo(0f);
		check(drawOrder.equals(drawOrderNames(saved)), "saved global and folder order samples retain the same slot names");
		check(editor.moveSlot(doc.data.findSlot("c"), -1) != null, "folder member can move across another member");
		doc.scrubTo(0f);
		check(drawOrder.equals(drawOrderNames(doc)), "explicit global and folder order survive setup slot reordering");
		before.restore();
		check(original.equals(SkeletonExporter.toJson(doc)), "slot deletion undo restores all tracks");
	}

	private static void remapsLinkedMeshActivationOnDeleteAndMove() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		for (String name : List.of("empty", "source-slot", "linked-slot"))
			editor.addSlot(name, doc.data.getBones().first());
		Skin skin = new Skin("default");
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment source = new MeshAttachment("source", sequence);
		source.setPath("source");
		source.setWorldVerticesLength(6);
		source.setVertices(new float[] {0, 0, 10, 0, 0, 10});
		source.setRegionUVs(new float[] {0, 0, 1, 0, 0, 1});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.setTimelineSlots(new int[] {2});
		source.updateSequence();
		MeshAttachment linked = source.newLinkedMesh();
		skin.setAttachment(1, "source", source);
		skin.setAttachment(2, "linked", linked);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		doc.data.findSlot("source-slot").setAttachmentName("source");
		doc.data.findSlot("linked-slot").setAttachmentName("linked");
		AnimationEditor animations = new AnimationEditor(doc);
		Animation deform = animations.addAnimation("deform");
		animations.addDeformKeyframe(deform, 1, source, 0.5f, new float[] {1, 0, 1, 0, 1, 0});
		editor.rebuildSkeleton();
		var before = SkeletonStructureSnapshot.capture(editor);
		String original = SkeletonExporter.toJson(doc);
		check(editor.deleteSlot(doc.data.findSlot("empty")), "delete preceding empty slot");
		check(Arrays.equals(source.getTimelineSlots(), new int[] {1}), "source activation remapped");
		String edited = SkeletonExporter.toJson(doc);
		doc.setAnimation("deform");
		doc.scrubTo(0.5f);
		near(1f, doc.skeleton.findSlot("linked-slot").getAppliedPose().getDeform().get(0), "linked slot receives remapped deform");
		var after = SkeletonStructureSnapshot.capture(editor);
		before.restore();
		check(Arrays.equals(source.getTimelineSlots(), new int[] {2}), "undo restores activation indices");
		check(original.equals(SkeletonExporter.toJson(doc)), "linked mesh delete undo is exact");
		after.restore();
		check(edited.equals(SkeletonExporter.toJson(doc)), "linked mesh delete redo is exact");
		int[] indices = editor.moveSlot(doc.data.findSlot("linked-slot"), -1);
		check(indices != null, "linked slot moves atomically with its animation tracks");
		check(Arrays.equals(source.getTimelineSlots(), new int[] {0}), "slot reorder remaps linked activation");
		SkeletonExporter.toJson(doc);
	}

	private static void preservesSharedLinkedWeightsWhenBoneIndicesChange() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		BoneData removed = editor.addBone("removed", doc.data.getBones().first());
		BoneData keep = editor.addBone("keep", doc.data.getBones().first());
		SlotData slot = editor.addSlot("meshes", keep);
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment source = new MeshAttachment("source", sequence);
		source.setPath("source");
		source.setWorldVerticesLength(6);
		source.setBones(new int[] {1, 2, 1, 2, 1, 2});
		source.setVertices(new float[] {0, 0, 1, 10, 0, 1, 0, 10, 1});
		source.setRegionUVs(new float[] {0, 0, 1, 0, 0, 1});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.updateSequence();
		MeshAttachment linked = source.newLinkedMesh();
		Skin skin = new Skin("default");
		skin.setAttachment(slot.getIndex(), "source", source);
		skin.setAttachment(slot.getIndex(), "linked", linked);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		var before = SkeletonStructureSnapshot.capture(editor);
		String original = SkeletonExporter.toJson(doc);
		check(editor.deleteBone(removed), "unreferenced bone preceding weighted mesh can be removed");
		check(source.getBones() == linked.getBones(), "reindexing preserves shared linked mesh weights");
		check(Arrays.equals(source.getBones(), new int[] {1, 1, 1, 1, 1, 1}), "shared weights are remapped once");
		var after = SkeletonStructureSnapshot.capture(editor);
		String edited = SkeletonExporter.toJson(doc);
		before.restore();
		check(source.getBones() == linked.getBones(), "undo retains shared weights");
		check(original.equals(SkeletonExporter.toJson(doc)), "weighted linked mesh undo remains exportable");
		after.restore();
		check(source.getBones() == linked.getBones(), "redo retains shared weights");
		check(edited.equals(SkeletonExporter.toJson(doc)), "weighted linked mesh redo remains exportable");
		SkeletonDocument saved = saveAndReload(doc);
		MeshAttachment savedSource = (MeshAttachment)saved.data.getDefaultSkin().getAttachment(0, "source");
		MeshAttachment savedLinked = (MeshAttachment)saved.data.getDefaultSkin().getAttachment(0, "linked");
		check(savedLinked.getSourceMesh() == savedSource && savedLinked.getBones() == savedSource.getBones(),
			"save and reload retain the source and shared linked weights");
		check(Arrays.equals(source.getBones(), savedSource.getBones()), "save and reload retain remapped influence indices");
	}

	private static void rollsBackFailedSlotReorder() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		SlotData first = editor.addSlot("first", doc.data.getBones().first());
		editor.addSlot("second", doc.data.getBones().first());
		Skin skin = new Skin("default");
		BoundingBoxAttachment box = new BoundingBoxAttachment("box");
		box.setTimelineSlots(new int[] {99});
		skin.setAttachment(0, "box", box);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		Animation animation = new Animation("alpha");
		Animation.AlphaTimeline alpha = new Animation.AlphaTimeline(1, 0, 0);
		alpha.setFrame(0, 0f, 0.25f);
		Array<Animation.Timeline> tracks = new Array<>(true, 1, Animation.Timeline[]::new);
		tracks.add(alpha);
		animation.setTimelines(tracks, new IntArray());
		doc.data.getAnimations().add(animation);
		editor.rebuildSkeleton();
		doc.dirty = false;
		long revision = doc.revision;
		try {
			editor.moveSlot(first, 1);
			throw new AssertionError("invalid linked activation must reject a reorder");
		} catch (IllegalArgumentException expected) {}
		check(doc.data.getSlots().first() == first, "failed reorder restores original slot references");
		check(skin.getAttachment(0, "box") == box && skin.getAttachment(1, "box") == null,
			"failed reorder restores skin bindings");
		check(((Animation.AlphaTimeline)animation.getTimelines().first()).getSlotIndex() == 0,
			"failed reorder restores already prepared timeline indices");
		check(!doc.dirty && doc.revision == revision, "failed reorder preserves dirty state and revision");
	}

	private static void rejectsReparentingThatInvalidatesConstraints() {
		for (String type : List.of("ik-chain", "ik-target", "transform", "path")) {
			SkeletonDocument doc = document();
			SkeletonEditor editor = new SkeletonEditor(doc);
			BoneData root = doc.data.getBones().first();
			BoneData upper = editor.addBone("upper", root);
			upper.setLength(10f);
			BoneData lower = editor.addBone("lower", upper);
			lower.getSetupPose().setX(10f);
			lower.setLength(10f);
			BoneData source = editor.addBone("source", root);
			source.getSetupPose().setX(15f);
			if (type.startsWith("ik")) {
				IkConstraintData ik = new IkConstraintData("ik");
				ik.getBones().addAll(upper, lower);
				ik.setTarget(source);
				doc.data.getConstraints().add(ik);
			} else if (type.equals("transform")) {
				TransformConstraintData transform = new TransformConstraintData("transform");
				transform.getBones().add(upper);
				transform.setSource(source);
				doc.data.getConstraints().add(transform);
			} else {
				SlotData slot = editor.addSlot("path-slot", source);
				PathConstraintData path = new PathConstraintData("path");
				path.getBones().add(upper);
				path.setSlot(slot);
				doc.data.getConstraints().add(path);
			}
			editor.rebuildSkeleton();
			doc.dirty = false;
			long revision = doc.revision;
			boolean changed = type.equals("ik-chain") ? editor.setBoneParent(lower, root)
				: editor.setBoneParent(source, lower);
			check(!changed, "reparent cannot invalidate " + type + " hierarchy");
			check(lower.getParent() == upper && source.getParent() == root, "rejected reparent leaves hierarchy intact");
			check(!doc.dirty && doc.revision == revision, "rejected reparent leaves dirty state intact");
		}
	}

	private static List<String> drawOrderNames(SkeletonDocument doc) {
		List<String> names = new java.util.ArrayList<>();
		for (var slot : doc.skeleton.getDrawOrder().getAppliedPose()) names.add(slot.getData().getName());
		return names;
	}

	private static SkeletonDocument saveAndReload(SkeletonDocument doc) {
		try {
			java.nio.file.Path file = java.nio.file.Files.createTempFile("openspine-structure-roundtrip-", ".json");
			try {
				SkeletonExporter.writeAtomic(doc, file);
				SkeletonDocument reloaded = new SkeletonDocument();
				reloaded.data = com.openspine.io.SkeletonLoader.loadAnimationSource(new com.badlogic.gdx.files.FileHandle(file.toFile()));
				reloaded.skeleton = new Skeleton(reloaded.data);
				reloaded.animationState = reloaded.newAnimationState();
				return reloaded;
			} finally { java.nio.file.Files.deleteIfExists(file); }
		} catch (java.io.IOException error) { throw new AssertionError("structural edit could not be saved and reopened", error); }
	}

	private static Animation rotation(SkeletonDocument doc, String name, int bone) {
		Animation animation = new Animation(name);
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(2, 1, bone);
		rotate.setFrame(0, 0f, 0f);
		rotate.setFrame(1, 1f, 30f);
		rotate.setBezier(0, 0, 0, 0f, 0f, 0.2f, 4f, 0.7f, 28f, 1f, 30f);
		Array<Animation.Timeline> tracks = new Array<>(true, 1, Animation.Timeline[]::new);
		tracks.add(rotate);
		animation.setTimelines(tracks, new IntArray(new int[] {bone}));
		doc.data.getAnimations().add(animation);
		return animation;
	}

	private static SkeletonDocument document() {
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = new SkeletonData();
		doc.data.setName("structure-test");
		doc.data.setVersion("4.3.0");
		doc.data.getBones().add(new BoneData(0, "root", null));
		doc.data.getBones().first().getSetupPose().setInherit(BoneData.Inherit.normal);
		doc.skeleton = new Skeleton(doc.data);
		doc.animationState = doc.newAnimationState();
		doc.updateWorldTransform(Physics.pose);
		return doc;
	}

	private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
	private static void near(float expected, float actual, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": " + expected + " != " + actual);
	}
	private SkeletonStructureEditTest() {}
}
