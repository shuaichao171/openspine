package com.openspine.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BonePose;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;

/** Setup compensation must preserve full matrices, indexed mesh binding, and existing animation data. */
public final class SetupReparentingTest {
	public static void main(String[] args) {
		preservesEveryInheritanceModeWithShearAndMirroring();
		preservesSetupAtLargeWorldCoordinates();
		rejectsWorldDriftBeyondFloatPrecision();
		preservesLinkedWeightedMeshVerticesAndUndoRedo();
		preservesUnrelatedAndDescendantAnimationData();
		rejectsAnimatedDependenciesWithoutMutation();
		rejectsConstraintsSkinDependenciesAndUnsavedPosePreviews();
		rejectsSingularParentsWithoutChangingDirtyState();
		rollsBackAnUnreliableAttachmentResult();
		keepsLegacyLocalReparentSemantics();
		worksAfterRenamingTheTarget();
		System.out.println("ALL SETUP REPARENTING TESTS PASSED");
	}

	private static SkeletonDocument document(BoneData.Inherit inherit, boolean mirror) {
		SkeletonData data = new SkeletonData();
		BoneData root = bone(data, "root", null, 7f, -5f, 13f, 1.2f, 0.9f, 3f, -4f);
		BoneData old = bone(data, "old", root, 11f, 20f, 37f, mirror ? -2f : 2f, 0.65f, 12f, -16f);
		BoneData target = bone(data, "target", old, 8f, -3f, -27f, 1.3f, -1.1f, 9f, 22f);
		target.getSetupPose().setInherit(inherit);
		target.setLength(0f);
		bone(data, "child", target, 12f, 5f, 18f, 0.7f, 1.1f, -4f, 11f);
		bone(data, "new", root, -35f, 17f, -42f, 0.75f, mirror ? -1.7f : 1.7f, -8f, 14f);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.animationState = doc.newAnimationState();
		doc.skeleton.updateWorldTransform(Physics.pose);
		doc.setSelectedBone(doc.skeleton.findBone("target"));
		return doc;
	}

	private static BoneData bone(SkeletonData data, String name, BoneData parent, float x, float y,
		float rotation, float scaleX, float scaleY, float shearX, float shearY) {
		BoneData bone = new BoneData(data.getBones().size, name, parent);
		BonePose pose = bone.getSetupPose();
		pose.setPosition(x, y); pose.setRotation(rotation); pose.setScale(scaleX, scaleY);
		pose.setShearX(shearX); pose.setShearY(shearY); pose.setInherit(BoneData.Inherit.normal);
		bone.setLength(20f);
		data.getBones().add(bone);
		return bone;
	}

	private static void preservesEveryInheritanceModeWithShearAndMirroring() {
		for (BoneData.Inherit inherit : BoneData.Inherit.values()) for (boolean mirror : new boolean[] {false, true}) {
			SkeletonDocument doc = document(inherit, mirror);
			doc.poseMode = true;
			Map<String, float[]> before = matrices(doc.skeleton);
			SkeletonEditor editor = new SkeletonEditor(doc);
			assertTrue(editor.setBoneParent(doc.data.findBone("target"), doc.data.findBone("new"), true), "reparent " + inherit + "/" + mirror);
			assertMatrices(before, doc.skeleton, "all setup bones keep their world matrices for " + inherit + "/" + mirror);
			assertTrue(doc.data.findBone("target").getSetupPose().getInherit() == inherit, "inheritance mode does not silently change");
			assertTrue(doc.data.findBone("target").getLength() == 0f, "zero-length control bones remain valid");
			assertTrue(doc.data.findBone("target").getParent() == doc.data.findBone("new"), "parent changes");
			Skeleton setup = new Skeleton(doc.data);
			setup.updateWorldTransform(Physics.pose);
			assertMatrices(before, setup, "preservation is written into Setup data");
		}
	}

	private static void preservesLinkedWeightedMeshVerticesAndUndoRedo() {
		for (boolean originallyDirty : new boolean[] {false, true}) {
			SkeletonDocument doc = document(BoneData.Inherit.normal, true);
			MeshAttachment mesh = addWeightedMesh(doc);
			MeshAttachment linked = (MeshAttachment)doc.data.getDefaultSkin().getAttachment(1, "linked");
			float[] vertices = mesh.getVertices().clone();
			float[] beforeWorld = meshWorld(doc, mesh, 0), beforeLinked = meshWorld(doc, linked, 1);
			Map<String, float[]> matrices = matrices(doc.skeleton);
			doc.dirty = originallyDirty;
			SkeletonEditor editor = new SkeletonEditor(doc);
			EditHistory history = new EditHistory(doc);
			SkeletonStructureSnapshot before = SkeletonStructureSnapshot.capture(editor);
			assertTrue(editor.setBoneParent(doc.data.findBone("target"), doc.data.findBone("new"), true), "weighted reparent");
			SkeletonStructureSnapshot after = SkeletonStructureSnapshot.capture(editor);
			history.record("reparent setup", before::restore, after::restore);
			assertArray(beforeWorld, meshWorld(doc, mesh, 0), "weighted mesh world vertices");
			assertArray(beforeLinked, meshWorld(doc, linked, 1), "linked mesh world vertices");
			assertTrue(Arrays.equals(vertices, mesh.getVertices()), "weight values and local vertices are not rewritten");
			assertTrue(mesh.getBones() == linked.getBones(), "linked mesh retains shared remapped binding");
			assertTrue(history.undo() && !history.canUndo(), "one undo reverses the structural edit");
			assertTrue(doc.dirty == originallyDirty, "undo preserves the previous dirty state");
			assertMatrices(matrices, doc.skeleton, "undo setup world matrices");
			assertArray(beforeWorld, meshWorld(doc, mesh, 0), "undo weighted vertices");
			assertTrue(history.redo(), "redo structural edit");
			assertMatrices(matrices, doc.skeleton, "redo setup world matrices");
			assertArray(beforeLinked, meshWorld(doc, linked, 1), "redo linked vertices");
		}
	}

	private static void preservesSetupAtLargeWorldCoordinates() {
		for (BoneData.Inherit inherit : BoneData.Inherit.values()) for (boolean mirror : new boolean[] {false, true}) {
			SkeletonDocument doc = document(inherit, mirror);
			doc.data.findBone("root").getSetupPose().setPosition(1_000_000f, -1_000_000f);
			SkeletonEditor editor = new SkeletonEditor(doc);
			editor.rebuildSkeleton();
			Map<String, float[]> before = matrices(doc.skeleton);
			assertTrue(editor.setBoneParent(doc.data.findBone("target"), doc.data.findBone("new"), true), "large-coordinate reparent " + inherit + "/" + mirror);
			Map<String, float[]> after = matrices(doc.skeleton);
			for (var entry : before.entrySet()) {
				float[] expected = entry.getValue(), actual = after.get(entry.getKey());
				for (int i = 0; i < expected.length; i++) {
					float tolerance = i < 4 ? 0.0002f : 0.5f;
					assertTrue(Float.isFinite(actual[i]) && Math.abs(expected[i] - actual[i]) <= tolerance,
						"large-coordinate compensation preserves " + entry.getKey() + " component " + i);
				}
			}
		}
	}

	private static void rejectsWorldDriftBeyondFloatPrecision() {
		SkeletonDocument doc = document(BoneData.Inherit.normal, false);
		doc.data.findBone("root").getSetupPose().setPosition(1_000_000f, -1_000_000f);
		new SkeletonEditor(doc).rebuildSkeleton();
		// A one-pixel difference is 16 float steps here; the old relative threshold allowed 20 pixels.
		for (int component : new int[] {4, 5}) {
			Map<String, float[]> expected = matrices(doc.skeleton);
			expected.get("target")[component] += 1f;
			assertVerificationRejects(doc.data, expected, "one-pixel world translation drift must reject");
		}
		// Matrix scale must use float precision as well, independently of scene translation.
		SkeletonDocument scaled = document(BoneData.Inherit.normal, false);
		scaled.data.findBone("root").getSetupPose().setScale(10_000f, 10_000f);
		new SkeletonEditor(scaled).rebuildSkeleton();
		for (int component = 0; component < 4; component++) {
			Map<String, float[]> expected = matrices(scaled.skeleton);
			expected.get("target")[component] += 0.0625f;
			assertVerificationRejects(scaled.data, expected, "linear matrix drift above float precision must reject");
		}
	}

	private static void assertVerificationRejects(SkeletonData data, Map<String, float[]> expected, String message) {
		try {
			SetupReparenting.verify(data, new SetupReparenting.Plan(null, expected, Map.of()));
			throw new AssertionError(message);
		} catch (IllegalArgumentException rejected) {}
	}

	private static MeshAttachment addWeightedMesh(SkeletonDocument doc) {
		SlotData slot = new SlotData(0, "mesh", doc.data.findBone("target"));
		slot.setAttachmentName("mesh");
		doc.data.getSlots().add(slot);
		SlotData second = new SlotData(1, "linked", doc.data.findBone("child"));
		second.setAttachmentName("linked");
		doc.data.getSlots().add(second);
		MeshAttachment mesh = new MeshAttachment("mesh", new Sequence(1, false));
		mesh.setWorldVerticesLength(6);
		mesh.setBones(new int[] {2, 2, 4, 1, 3, 1, 0});
		mesh.setVertices(new float[] {1f, 2f, 0.6f, -4f, 6f, 0.4f, 3f, 8f, 1f, 5f, -1f, 1f});
		mesh.setTriangles(new short[] {0, 1, 2});
		mesh.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0f, 1f});
		mesh.setHullLength(6);
		MeshAttachment linked = mesh.newLinkedMesh();
		Skin skin = new Skin("default");
		skin.setAttachment(0, "mesh", mesh);
		skin.setAttachment(1, "linked", linked);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		new SkeletonEditor(doc).rebuildSkeleton();
		return mesh;
	}

	private static float[] meshWorld(SkeletonDocument doc, MeshAttachment mesh, int slot) {
		float[] result = new float[mesh.getWorldVerticesLength()];
		mesh.computeWorldVertices(doc.skeleton, doc.skeleton.getSlots().get(slot), 0, result.length, result, 0, 2);
		return result;
	}

	private static void preservesUnrelatedAndDescendantAnimationData() {
		SkeletonDocument doc = document(BoneData.Inherit.normal, false);
		Animation animation = addRotationAnimation(doc, "child", "child-motion");
		doc.setAnimation(animation.getName());
		doc.scrubTo(0.5f);
		Map<String, float[]> before = matrices(doc.skeleton);
		float[] frames = ((Animation.RotateTimeline)animation.getTimelines().first()).getFrames().clone();
		assertTrue(new SkeletonEditor(doc).setBoneParent(doc.data.findBone("target"), doc.data.findBone("new"), true),
			"a descendant animation can remain unchanged below an unchanged world frame");
		assertTrue(Arrays.equals(frames, ((Animation.RotateTimeline)animation.getTimelines().first()).getFrames()), "animation keys remain exact");
		assertMatrices(before, doc.skeleton, "descendant animated pose remains unchanged");
	}

	private static Animation addRotationAnimation(SkeletonDocument doc, String bone, String name) {
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation(name);
		int index = doc.data.findBone(bone).getIndex();
		editor.ensureTimeline(animation, AnimationEditor.Prop.ROTATE, index);
		editor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, index, 0f, 0f, 0f);
		editor.addKeyframe(animation, AnimationEditor.Prop.ROTATE, index, 1f, 40f, 0f);
		doc.animationState = doc.newAnimationState();
		return animation;
	}

	private static void rejectsAnimatedDependenciesWithoutMutation() {
		for (String bone : new String[] {"root", "old", "target", "new"}) {
			SkeletonDocument doc = document(BoneData.Inherit.normal, false);
			Animation animation = addRotationAnimation(doc, bone, "protected");
			Animation.Timeline original = animation.getTimelines().first();
			BoneData target = doc.data.findBone("target"), parent = doc.data.findBone("new");
			SkeletonEditor editor = new SkeletonEditor(doc);
			doc.dirty = true;
			long revision = doc.revision;
			assertTrue(editor.getBoneParentBlockReason(target, parent, true).contains("动画"), "animated dependency is explained");
			assertTrue(!editor.setBoneParent(target, parent, true), "animated dependency is rejected");
			assertTrue(doc.data.findBone("target") == target && animation.getTimelines().first() == original, "rejection retains object references");
			assertTrue(doc.dirty && doc.revision == revision, "rejection retains dirty state and revision");
		}
	}

	private static void rejectsConstraintsSkinDependenciesAndUnsavedPosePreviews() {
		SkeletonDocument doc = document(BoneData.Inherit.normal, false);
		SkeletonEditor editor = new SkeletonEditor(doc);
		BoneData target = doc.data.findBone("target"), parent = doc.data.findBone("new");
		IkConstraintData ik = new IkConstraintData("ik");
		ik.getBones().add(target); ik.setTarget(parent);
		doc.data.getConstraints().add(ik);
		assertTrue(editor.getBoneParentBlockReason(target, parent, true).contains("约束"), "constraints explicitly unsupported");
		doc.data.getConstraints().clear();
		parent.setSkinRequired(true);
		assertTrue(editor.getBoneParentBlockReason(target, parent, true).contains("皮肤"), "skin activation dependency rejected");
		parent.setSkinRequired(false);
		doc.poseMode = true;
		doc.skeleton.findBone("target").getPose().setX(100f);
		assertTrue(editor.getBoneParentBlockReason(target, parent, true).contains("当前姿态"), "unapplied pose preview is not discarded");
	}

	private static void rejectsSingularParentsWithoutChangingDirtyState() {
		for (boolean dirty : new boolean[] {false, true}) {
			SkeletonDocument doc = document(BoneData.Inherit.normal, false);
			BoneData target = doc.data.findBone("target"), parent = doc.data.findBone("new");
			parent.getSetupPose().setScale(0f);
			doc.dirty = dirty;
			long revision = doc.revision;
			try { new SkeletonEditor(doc).setBoneParent(target, parent, true); throw new AssertionError("singular parent must fail"); }
			catch (IllegalArgumentException expected) {}
			assertTrue(doc.data.findBone("target") == target && target.getParent() == doc.data.findBone("old"), "no partial structural mutation");
			assertTrue(doc.dirty == dirty && doc.revision == revision, "failed compensation preserves dirty state");
		}
	}

	private static void keepsLegacyLocalReparentSemantics() {
		SkeletonDocument doc = document(BoneData.Inherit.normal, false);
		SkeletonEditor.BonePoseState before = SkeletonEditor.bonePoseState(doc.data.findBone("target").getSetupPose());
		assertTrue(new SkeletonEditor(doc).setBoneParent(doc.data.findBone("target"), doc.data.findBone("new")), "legacy reparent stays supported");
		assertTrue(before.equals(SkeletonEditor.bonePoseState(doc.data.findBone("target").getSetupPose())), "unchecked option preserves local values");
	}

	private static void rollsBackAnUnreliableAttachmentResult() {
		SkeletonDocument doc = document(BoneData.Inherit.normal, true);
		MeshAttachment mesh = addWeightedMesh(doc);
		mesh.getVertices()[0] = Float.MAX_VALUE;
		mesh.getVertices()[1] = Float.MAX_VALUE;
		int[] weights = mesh.getBones().clone();
		BoneData target = doc.data.findBone("target");
		doc.dirty = true;
		long revision = doc.revision;
		try { new SkeletonEditor(doc).setBoneParent(target, doc.data.findBone("new"), true); throw new AssertionError("overflowing attachment result must reject"); }
		catch (IllegalArgumentException expected) {}
		assertTrue(doc.data.findBone("target") == target && target.getParent() == doc.data.findBone("old"), "failed vertex verification restores structure");
		assertTrue(Arrays.equals(weights, mesh.getBones()), "failed vertex verification restores indexed weights");
		assertTrue(doc.dirty && doc.revision == revision, "rollback preserves prior unsaved work");
	}

	private static void worksAfterRenamingTheTarget() {
		SkeletonDocument doc = document(BoneData.Inherit.normal, false);
		SkeletonEditor editor = new SkeletonEditor(doc);
		editor.renameBone(doc.data.findBone("target"), "renamed");
		Map<String, float[]> before = matrices(doc.skeleton);
		assertTrue(editor.setBoneParent(doc.data.findBone("renamed"), doc.data.findBone("new"), true), "renamed bone can be compensated");
		assertMatrices(before, doc.skeleton, "renaming does not alter compensation");
	}

	private static Map<String, float[]> matrices(Skeleton skeleton) {
		Map<String, float[]> result = new LinkedHashMap<>();
		for (Bone bone : skeleton.getBones()) {
			BonePose pose = bone.getAppliedPose();
			result.put(bone.getData().getName(), new float[] {pose.getA(), pose.getB(), pose.getC(), pose.getD(), pose.getWorldX(), pose.getWorldY()});
		}
		return result;
	}
	private static void assertMatrices(Map<String, float[]> expected, Skeleton actual, String message) {
		Map<String, float[]> values = matrices(actual);
		for (var entry : expected.entrySet()) assertArray(entry.getValue(), values.get(entry.getKey()), message + ": " + entry.getKey());
	}
	private static void assertArray(float[] expected, float[] actual, String message) {
		assertTrue(actual != null && expected.length == actual.length, message);
		for (int index = 0; index < expected.length; index++) if (!Float.isFinite(actual[index]) || Math.abs(expected[index] - actual[index]) > 0.002f)
			throw new AssertionError(message + " at " + index + ": " + expected[index] + " != " + actual[index]);
	}
	private static void assertTrue(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private SetupReparentingTest() {}
}
