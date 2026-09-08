package com.openspine.model;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BonePose;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.VertexAttachment;

/** Plans a setup-only reparent compensation in isolated, unconstrained runtime skeletons. */
final class SetupReparenting {
	private static final float MATRIX_TOLERANCE = 0.0002f;
	private static final float POSITION_TOLERANCE = 0.0005f;

	private record AttachmentUse(VertexAttachment attachment, int slot) {}
	record Plan(SkeletonEditor.BonePoseState pose, Map<String, float[]> world, Map<AttachmentUse, float[]> vertices) {}

	static String blockReason(SkeletonDocument doc, BoneData target, BoneData parent) {
		if (doc.data.getConstraints().notEmpty())
			return "保持 Setup 世界姿态暂不支持含约束的骨架";
		Set<BoneData> dependencies = new LinkedHashSet<>();
		for (BoneData bone = target; bone != null; bone = bone.getParent()) dependencies.add(bone);
		for (BoneData bone = parent; bone != null; bone = bone.getParent()) dependencies.add(bone);
		for (BoneData bone : dependencies) {
			if (bone.getSkinRequired()) return "保持 Setup 世界姿态不支持相关骨骼的皮肤激活依赖：" + bone.getName();
			if (doc.poseMode && doc.skeleton != null) {
				Bone runtime = doc.skeleton.findBone(bone.getName());
				if (runtime != null && !SkeletonEditor.bonePoseState(runtime.getPose()).equals(SkeletonEditor.bonePoseState(bone.getSetupPose())))
					return "相关骨骼的当前姿态与 Setup 不同，请先重置姿态或设为 Setup：" + bone.getName();
			}
		}
		for (Animation animation : doc.data.getAnimations()) for (Animation.Timeline timeline : animation.getTimelines()) {
			if (!(timeline instanceof Animation.BoneTimeline track)) continue;
			int index = track.getBoneIndex();
			if (index >= 0 && index < doc.data.getBones().size && dependencies.contains(doc.data.getBones().get(index)))
				return "保持 Setup 世界姿态不补偿相关骨骼的动画：" + animation.getName() + " / " + doc.data.getBones().get(index).getName();
		}
		return null;
	}

	static Plan plan(SkeletonDocument doc, BoneData target, BoneData parent, Array<BoneData> ordered) {
		Skeleton original = setupSkeleton(doc.data.getBones(), null, null);
		Map<String, float[]> expected = worldMatrices(original);
		Skeleton candidate = setupSkeleton(ordered, target, parent);
		Bone moved = candidate.findBone(target.getName());
		BonePose parentPose = moved.getParent().getAppliedPose();
		double determinant = (double)parentPose.getA() * parentPose.getD() - (double)parentPose.getB() * parentPose.getC();
		if (!Double.isFinite(determinant) || Math.abs(determinant) < 1e-12)
			throw new IllegalArgumentException("新父级的 Setup 变换不可逆，无法保持世界姿态");
		BonePose pose = moved.getAppliedPose();
		float[] desired = expected.get(target.getName());
		pose.setA(desired[0]); pose.setB(desired[1]); pose.setC(desired[2]); pose.setD(desired[3]);
		pose.setWorldX(desired[4]); pose.setWorldY(desired[5]);
		pose.updateLocalTransform(candidate);
		SkeletonEditor.BonePoseState local = SkeletonEditor.bonePoseState(pose);
		if (!finite(local)) throw new IllegalArgumentException("无法为该继承模式求出有限的 Setup 补偿");
		moved.getData().getSetupPose().set(pose);
		candidate.setupPoseBones();
		candidate.updateWorldTransform(Physics.pose);
		verify(candidate, expected);
		return new Plan(local, expected, attachmentVertices(doc.data, original));
	}

	static void verify(SkeletonData data, Plan plan) {
		Skeleton setup = setupSkeleton(data.getBones(), null, null);
		verify(setup, plan.world());
		Map<AttachmentUse, float[]> vertices = attachmentVertices(data, setup);
		for (var entry : plan.vertices().entrySet()) {
			float[] before = entry.getValue(), after = vertices.get(entry.getKey());
			if (after == null || before.length != after.length) throw new IllegalArgumentException("改父级后附件顶点数量不一致");
			for (int index = 0; index < before.length; index++) {
				float tolerance = precisionTolerance(before[index], after[index], POSITION_TOLERANCE);
				if (!Float.isFinite(before[index]) || !Float.isFinite(after[index]) || Math.abs(before[index] - after[index]) > tolerance)
					throw new IllegalArgumentException("保持 Setup 世界姿态会改变附件顶点，已取消：" + entry.getKey().attachment().getName());
			}
		}
	}

	private static Map<AttachmentUse, float[]> attachmentVertices(SkeletonData data, Skeleton skeleton) {
		Map<AttachmentUse, float[]> result = new LinkedHashMap<>();
		for (Skin skin : data.getSkins()) for (Skin.SkinEntry entry : skin.getAttachments()) {
			if (!(entry.getAttachment() instanceof VertexAttachment attachment)) continue;
			AttachmentUse use = new AttachmentUse(attachment, entry.getSlotIndex());
			if (result.containsKey(use)) continue;
			SlotData sourceSlot = data.getSlots().get(entry.getSlotIndex());
			SlotData slotData = new SlotData(sourceSlot.getIndex(), sourceSlot.getName(), skeleton.getData().findBone(sourceSlot.getBoneData().getName()));
			Slot slot = new Slot(slotData, skeleton);
			float[] vertices = new float[attachment.getWorldVerticesLength()];
			attachment.computeWorldVertices(skeleton, slot, 0, vertices.length, vertices, 0, 2);
			result.put(use, vertices);
		}
		return result;
	}

	private static void verify(Skeleton candidate, Map<String, float[]> expected) {
		Map<String, float[]> actual = worldMatrices(candidate);
		for (var entry : expected.entrySet()) {
			float[] before = entry.getValue(), after = actual.get(entry.getKey());
			if (after == null) throw new IllegalArgumentException("改父级后缺少骨骼：" + entry.getKey());
			for (int i = 0; i < before.length; i++) {
				// Translation magnitude must not turn a subpixel check into a multi-pixel allowance.
				// Allow only the local calculation floor and a few representable float steps.
				float tolerance = precisionTolerance(before[i], after[i], i < 4 ? MATRIX_TOLERANCE : POSITION_TOLERANCE);
				if (!Float.isFinite(before[i]) || !Float.isFinite(after[i]) || Math.abs(before[i] - after[i]) > tolerance)
					throw new IllegalArgumentException("该继承模式或变换无法可靠保持 Setup 世界姿态：" + entry.getKey());
			}
		}
	}

	private static float precisionTolerance(float before, float after, float minimum) {
		return Math.max(minimum, 8f * Math.max(Math.ulp(before), Math.ulp(after)));
	}

	private static Skeleton setupSkeleton(Array<BoneData> ordered, BoneData target, BoneData newParent) {
		SkeletonData data = new SkeletonData();
		IdentityHashMap<BoneData, BoneData> mapped = new IdentityHashMap<>();
		for (BoneData original : ordered) {
			BoneData parent = original == target ? newParent : original.getParent();
			BoneData replacement = new BoneData(data.getBones().size, original.getName(), mapped.get(parent));
			if (parent != null && replacement.getParent() == null) throw new IllegalArgumentException("骨骼父级顺序无效");
			replacement.getSetupPose().set(original.getSetupPose());
			replacement.setLength(original.getLength());
			// Verify all setup bones, including descendants that a particular skin may hide.
			replacement.setSkinRequired(false);
			data.getBones().add(replacement);
			mapped.put(original, replacement);
		}
		Skeleton skeleton = new Skeleton(data);
		skeleton.updateWorldTransform(Physics.pose);
		return skeleton;
	}

	private static Map<String, float[]> worldMatrices(Skeleton skeleton) {
		Map<String, float[]> result = new LinkedHashMap<>();
		for (Bone bone : skeleton.getBones()) {
			BonePose pose = bone.getAppliedPose();
			result.put(bone.getData().getName(), new float[] {pose.getA(), pose.getB(), pose.getC(), pose.getD(), pose.getWorldX(), pose.getWorldY()});
		}
		return result;
	}

	private static boolean finite(SkeletonEditor.BonePoseState pose) {
		return Float.isFinite(pose.x()) && Float.isFinite(pose.y()) && Float.isFinite(pose.rotation())
			&& Float.isFinite(pose.scaleX()) && Float.isFinite(pose.scaleY())
			&& Float.isFinite(pose.shearX()) && Float.isFinite(pose.shearY());
	}
	private SetupReparenting() {}
}
