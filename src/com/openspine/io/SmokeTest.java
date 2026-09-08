package com.openspine.io;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.attachments.AttachmentLoader;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.Skin;

/**
 * 无头冒烟测试：验证示例骨架 JSON 加载、动画播放、骨骼编辑、皮肤切换。
 * 不依赖 GL/渲染（用 stub AttachmentLoader 替代纹理图集）。
 */
public class SmokeTest {
	/** 最简单的 AttachmentLoader：只创建无纹理的 RegionAttachment */
	static class StubLoader implements AttachmentLoader {
		@Override
		public RegionAttachment newRegionAttachment(Skin skin, String placeholder, String name, String path,
			Sequence sequence) {
			RegionAttachment att = new RegionAttachment(name, sequence);
			return att;
		}
		@Override
		public MeshAttachment newMeshAttachment(Skin skin, String placeholder, String name, String path,
			Sequence sequence) { return new MeshAttachment(name, sequence); }
		@Override
		public BoundingBoxAttachment newBoundingBoxAttachment(Skin skin, String placeholder, String name) {
			return new BoundingBoxAttachment(name);
		}
		@Override
		public ClippingAttachment newClippingAttachment(Skin skin, String placeholder, String name) {
			return new ClippingAttachment(name);
		}
		@Override
		public PathAttachment newPathAttachment(Skin skin, String placeholder, String name) {
			return new PathAttachment(name);
		}
		@Override
		public PointAttachment newPointAttachment(Skin skin, String placeholder, String name) {
			return new PointAttachment(name);
		}
	}

	public static void main(String[] args) throws Exception {
		if (ExampleSkeleton.scaledTextureSize(64) != 256)
			throw new AssertionError("built-in textures use 4x supersampled source images");
		StubLoader loader = new StubLoader();
		JsonValue root = new JsonReader().parse(ExampleSkeleton.JSON);
		SkeletonJson sj = new SkeletonJson(loader);
		var data = sj.readSkeletonData(root);
		var robotData = new SkeletonJson(loader).readSkeletonData(new JsonReader().parse(ExampleSkeleton.ROBOT_JSON));
		if (robotData.getBones().size == data.getBones().size)
			throw new AssertionError("built-in skeletons must have different hierarchies");
		if (robotData.findBone("robotRoot") == null || robotData.findBone("robotRoot").getSetupPose().getX() == 0f)
			throw new AssertionError("the robot example must be positioned separately from the character");
		if (robotData.findAnimation("hover") == null || robotData.findAnimation("signal") == null)
			throw new AssertionError("the robot example must provide its own animations");
		Skeleton skeleton = new Skeleton(data);
		System.out.println("OK: skeleton parsed");
		System.out.println("  bones=" + data.getBones().size);
		System.out.println("  skins=" + data.getSkins().size);
		System.out.println("  animations=" + data.getAnimations().size);
		for (var a : data.getAnimations()) {
			System.out.println("    anim: " + a.getName() + " dur=" + a.getDuration());
		}

		// 2. 动画播放
		var state = new AnimationState(new AnimationStateData(data));
		state.setAnimation(0, "walk", true);
		state.apply(skeleton);
		float walkStartRotation = skeleton.findBone("thighL").getPose().getRotation();
		state.getTrack(0).setTrackTime(0.5f);
		state.apply(skeleton);
		float walkMiddleRotation = skeleton.findBone("thighL").getPose().getRotation();
		if (Math.abs(walkStartRotation - walkMiddleRotation) < 1f)
			throw new AssertionError("built-in walk animation has no effective rotation values");
		state.getTrack(0).setTrackTime(0f);
		for (int i = 0; i < 120; i++) {
			state.update(0.016f);
			state.apply(skeleton);
			skeleton.updateWorldTransform(com.esotericsoftware.spine.Physics.update);
		}
		System.out.println("OK: play walk 120 frames, t=" + state.getTrack(0).getTrackTime());

		// 3. 骨骼选择+编辑（摆姿势）
		Bone rootBone = skeleton.getRootBone();
		System.out.println("OK: selected bone=" + rootBone.getData().getName());
		rootBone.getPose().setRotation(rootBone.getPose().getRotation() + 45);
		skeleton.updateWorldTransform(com.esotericsoftware.spine.Physics.update);
		System.out.println("OK: rotated root, worldX=" + rootBone.getPose().getWorldX()
			+ " worldY=" + rootBone.getPose().getWorldY());

		// 4. 皮肤切换
		skeleton.setSkin("blue");
		System.out.println("OK: switched skin to " + skeleton.getSkin().getName());

		// 5. 统计根骨骼 children（Viewport/树用）
		System.out.println("OK: root children=" + rootBone.getChildren().size);

		// 6. 时间线编辑：为 root 添加旋转关键帧并验证
		var sd = new com.openspine.model.SkeletonDocument();
		sd.data = data; sd.skeleton = skeleton;
		var animEditor = new com.openspine.model.AnimationEditor(sd);
		var anim = animEditor.addAnimation("testAnim");
		System.out.println("OK: added animation " + anim.getName());
		int bi = rootBone.getData().getIndex();
		animEditor.ensureTimeline(anim, com.openspine.model.AnimationEditor.Prop.ROTATE, bi);
		animEditor.addKeyframe(anim, com.openspine.model.AnimationEditor.Prop.ROTATE, bi, 0f, 0f, 0f);
		animEditor.addKeyframe(anim, com.openspine.model.AnimationEditor.Prop.ROTATE, bi, 0.5f, 45f, 0f);
		animEditor.addKeyframe(anim, com.openspine.model.AnimationEditor.Prop.ROTATE, bi, 1.0f, 90f, 0f);
		var tl = animEditor.findTimeline(anim, com.openspine.model.AnimationEditor.Prop.ROTATE, bi);
		System.out.println("OK: rotate frames=" + tl.getFrameCount() + " dur=" + anim.getDuration());
		if (tl.getFrameCount() != 3) throw new RuntimeException("expected 3 frames, got " + tl.getFrameCount());
		// 播放新动画到 0.5s，验证值生效
		state.setAnimation(0, anim, true);
		state.getTrack(0).setTrackTime(0.5f);
		state.update(0f); state.apply(skeleton); skeleton.updateWorldTransform(com.esotericsoftware.spine.Physics.update);
		System.out.println("OK: rotate@0.5s=" + rootBone.getPose().getRotation());
		animEditor.removeKeyframe(anim, com.openspine.model.AnimationEditor.Prop.ROTATE, bi, 0.5f);
		tl = animEditor.findTimeline(anim, com.openspine.model.AnimationEditor.Prop.ROTATE, bi);
		System.out.println("OK: after remove frames=" + tl.getFrameCount());
		if (tl.getFrameCount() != 2) throw new RuntimeException("expected 2 frames after remove");

		// 7. 导出为 Spine JSON，再加载回，验证等价
		String json = com.openspine.io.SkeletonExporter.toJson(sd);
		System.out.println("OK: exported JSON bytes=" + json.length() + " jsonLines=" + json.lines().count());
		com.badlogic.gdx.utils.JsonValue root2 = new com.badlogic.gdx.utils.JsonReader().parse(json);
		com.esotericsoftware.spine.SkeletonJson sj2 = new com.esotericsoftware.spine.SkeletonJson(loader);
		var data2 = sj2.readSkeletonData(root2);
		System.out.println("OK: reloaded bones=" + data2.getBones().size + " slots=" + data2.getSlots().size
			+ " skins=" + data2.getSkins().size + " anims=" + data2.getAnimations().size);
		if (data2.getBones().size != data.getBones().size) throw new RuntimeException("bone count mismatch");
		if (data2.getSlots().size != data.getSlots().size) throw new RuntimeException("slot count mismatch");
		if (data2.getSkins().size != data.getSkins().size) throw new RuntimeException("skin count mismatch");
		if (data2.getAnimations().size != data.getAnimations().size) throw new RuntimeException("anim count mismatch: "
			+ data2.getAnimations().size + "/" + data.getAnimations().size);
		// 验证新加动画的旋转关键帧存在
		var reloaded = data2.findAnimation("testAnim");
		if (reloaded == null) throw new RuntimeException("testAnim not exported");
		var reloadedTl = new com.openspine.model.AnimationEditor(sd).findTimeline(reloaded,
			com.openspine.model.AnimationEditor.Prop.ROTATE, bi);
		System.out.println("OK: testAnim reloaded frames=" + reloadedTl.getFrameCount());



		System.out.println("\nALL SMOKE TESTS PASSED");
	}
}
