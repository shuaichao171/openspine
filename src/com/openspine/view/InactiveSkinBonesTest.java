package com.openspine.view;

import java.util.Arrays;
import java.util.List;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BonePose;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.openspine.model.SkeletonDocument;

/** Inactive references must be current, separate from authored/runtime data and uneditable in the viewport. */
public final class InactiveSkinBonesTest {
	public static void main(String[] args) {
		SkeletonData data = new SkeletonData();
		BoneData root = bone(data, "root", null, 0f, 0f, false);
		BoneData parent = bone(data, "ik-parent", root, 0f, 0f, false);
		BoneData inactive = bone(data, "skin-bone", parent, 20f, 5f, true);
		BoneData nested = bone(data, "skin-child", inactive, 10f, -3f, true);
		BoneData target = bone(data, "target", root, 0f, 40f, false);
		IkConstraintData ik = new IkConstraintData("active-ik");
		ik.getBones().add(parent); ik.setTarget(target); ik.getSetupPose().setMix(1f);
		data.getConstraints().add(ik);
		Skeleton source = new Skeleton(data);
		source.setPosition(25f, 35f); source.setScale(1.5f, 2f);
		source.updateWorldTransform(Physics.pose);
		Bone original = source.findBone("skin-bone");
		original.getPose().setPosition(800f, -400f);
		original.getAppliedPose().setWorldX(-900f); original.getAppliedPose().setWorldY(1000f);
		InactiveSkinBones evaluator = new InactiveSkinBones();
		float[] before = snapshot(source);
		Object[] cache = source.getUpdateCache().toArray();
		Skeleton reference = evaluator.prepare(source);
		require(Arrays.equals(before, snapshot(source)), "Reference changed source setup/runtime/world poses");
		require(Arrays.equals(cache, source.getUpdateCache().toArray()), "Reference changed source evaluation cache");
		require(source.getSkin() == null && !original.isActive(), "Reference activated the real skin or bone");
		require(source.getConstraints().first().isActive(), "Reference disabled the real IK constraint");
		require(reference.getConstraints().isEmpty() && reference.getSlots().isEmpty() && reference.getSkin() == null,
			"Reference contains real constraints, slots or skin");
		assertAtSetupUnderParent(source.findBone("ik-parent").getAppliedPose(), inactive,
			reference.findBone("skin-bone").getAppliedPose());
		assertAtSetupUnderParent(reference.findBone("skin-bone").getAppliedPose(), nested,
			reference.findBone("skin-child").getAppliedPose());
		near(90f, source.findBone("ik-parent").getAppliedPose().getWorldRotationX(), "Test IK did not constrain the ancestor");
		require(EditorViewport.displayedBone(original, null) == null, "Inactive bone displayed with option disabled");
		require(EditorViewport.displayedBone(original, reference) == reference.findBone("skin-bone"),
			"Inactive display did not use its evaluated reference");
		require(!EditorViewport.isViewportBoneTarget(source, original), "Inactive reference became a gizmo target");
		SkeletonDocument document = new SkeletonDocument(); document.data = data; document.skeleton = source;
		document.setSelectedBones(List.of(source.findBone("ik-parent"), original));
		require(!EditorViewport.isViewportBoneSelectionActive(document), "Mixed selections can transform inactive bones");
		document.setSelectedBone(source.findBone("ik-parent"));
		require(EditorViewport.isViewportBoneSelectionActive(document), "Active selection was incorrectly blocked");

		// A previous skin's animated local pose is deliberately different from setup.
		Skin skin = new Skin("other"); skin.getBones().add(nested);
		source.setSkin(skin); source.updateWorldTransform(Physics.pose);
		require(original.isActive(), "Test skin did not activate its bone");
		evaluator.prepare(source);
		require(EditorViewport.displayedBone(original, reference) == original, "Active bones must display the actual runtime pose");
		source.setSkin((Skin)null);
		source.findBone("root").getPose().setPosition(50f, -30f);
		source.updateWorldTransform(Physics.pose);
		before = snapshot(source);
		require(evaluator.prepare(source) == reference, "A stable reference was unnecessarily replaced");
		assertAtSetupUnderParent(source.findBone("ik-parent").getAppliedPose(), inactive,
			reference.findBone("skin-bone").getAppliedPose());
		require(Arrays.equals(before, snapshot(source)), "Repeated reference changed source poses");
		inactive.setVisible(false);
		require(EditorViewport.displayedBone(original, reference) == null, "Bone visibility metadata was ignored");
		inactive.setVisible(true);
		inactive.getSetupPose().setPosition(-12f, 17f);
		evaluator.prepare(source);
		assertAtSetupUnderParent(source.findBone("ik-parent").getAppliedPose(), inactive,
			reference.findBone("skin-bone").getAppliedPose());
		inactive.getSetupPose().setScale(Float.NaN, 1f);
		evaluator.prepare(source);
		require(EditorViewport.displayedBone(original, reference) == null, "Nonfinite reference matrix reached rendering");
		inactive.getSetupPose().setScale(1f, 1f);
		Skeleton replacement = new Skeleton(data); replacement.updateWorldTransform(Physics.pose);
		require(evaluator.prepare(replacement) != reference, "Replacing a document reused its stale reference");
		require(evaluator.prepare(null) == null, "Clearing a document retained a reference");
		System.out.println("InactiveSkinBonesTest passed");
	}

	private static BoneData bone(SkeletonData data, String name, BoneData parent, float x, float y, boolean skin) {
		BoneData bone = new BoneData(data.getBones().size, name, parent);
		bone.getSetupPose().setPosition(x, y); bone.getSetupPose().setScale(1f, 1f);
		bone.getSetupPose().setInherit(BoneData.Inherit.normal);
		bone.setSkinRequired(skin); bone.setVisible(true); bone.setLength(25f);
		data.getBones().add(bone); return bone;
	}
	private static void assertAtSetupUnderParent(BonePose parent, BoneData setup, BonePose actual) {
		float x = setup.getSetupPose().getX(), y = setup.getSetupPose().getY();
		near(parent.getA() * x + parent.getB() * y + parent.getWorldX(), actual.getWorldX(), "Reference X is stale");
		near(parent.getC() * x + parent.getD() * y + parent.getWorldY(), actual.getWorldY(), "Reference Y is stale");
	}
	private static float[] snapshot(Skeleton source) {
		float[] values = new float[source.getBones().size * 3 * 14];
		int index = 0;
		for (Bone bone : source.getBones())
			for (BonePose pose : new BonePose[] {bone.getData().getSetupPose(), bone.getPose(), bone.getAppliedPose()}) {
				float[] fields = {pose.getX(), pose.getY(), pose.getRotation(), pose.getScaleX(), pose.getScaleY(),
					pose.getShearX(), pose.getShearY(), pose.getWorldX(), pose.getWorldY(),
					pose.getA(), pose.getB(), pose.getC(), pose.getD(), bone.isActive() ? 1f : 0f};
				System.arraycopy(fields, 0, values, index, fields.length); index += fields.length;
			}
		return values;
	}
	private static void near(float expected, float actual, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.0005f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}
	private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
