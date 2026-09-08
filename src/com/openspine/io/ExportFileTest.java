package com.openspine.io;

import com.badlogic.gdx.utils.JsonReader;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.attachments.AttachmentLoader;
import com.openspine.model.SkeletonDocument;

/** 端到端：导出内置示例为真实 JSON 文件，再加载回并对比数量 */
public class ExportFileTest {
	static class StubLoader implements AttachmentLoader {
		@Override public com.esotericsoftware.spine.attachments.RegionAttachment newRegionAttachment(
			com.esotericsoftware.spine.Skin skin, String placeholder, String name, String path,
			com.esotericsoftware.spine.attachments.Sequence seq) {
			return new com.esotericsoftware.spine.attachments.RegionAttachment(name, seq);
		}
		@Override public com.esotericsoftware.spine.attachments.MeshAttachment newMeshAttachment(
			com.esotericsoftware.spine.Skin skin, String placeholder, String name, String path,
			com.esotericsoftware.spine.attachments.Sequence seq) {
			return new com.esotericsoftware.spine.attachments.MeshAttachment(name, seq);
		}
		@Override public com.esotericsoftware.spine.attachments.BoundingBoxAttachment newBoundingBoxAttachment(
			com.esotericsoftware.spine.Skin skin, String placeholder, String name) {
			return new com.esotericsoftware.spine.attachments.BoundingBoxAttachment(name);
		}
		@Override public com.esotericsoftware.spine.attachments.ClippingAttachment newClippingAttachment(
			com.esotericsoftware.spine.Skin skin, String placeholder, String name) {
			return new com.esotericsoftware.spine.attachments.ClippingAttachment(name);
		}
		@Override public com.esotericsoftware.spine.attachments.PathAttachment newPathAttachment(
			com.esotericsoftware.spine.Skin skin, String placeholder, String name) {
			return new com.esotericsoftware.spine.attachments.PathAttachment(name);
		}
		@Override public com.esotericsoftware.spine.attachments.PointAttachment newPointAttachment(
			com.esotericsoftware.spine.Skin skin, String placeholder, String name) {
			return new com.esotericsoftware.spine.attachments.PointAttachment(name);
		}
	}

	public static void main(String[] args) throws Exception {
		// 加载示例
		StubLoader loader = new StubLoader();
		var root = new JsonReader().parse(ExampleSkeleton.JSON);
		var data = new SkeletonJson(loader).readSkeletonData(root);

		// 构造 doc 并验证导出
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new com.esotericsoftware.spine.Skeleton(data);
		String json = SkeletonExporter.toJson(doc);

		// 往返
		var root2 = new JsonReader().parse(json);
		var data2 = new SkeletonJson(loader).readSkeletonData(root2);
		System.out.println("FORMAT OK: bones " + data.getBones().size + "->" + data2.getBones().size
			+ " | slots " + data.getSlots().size + "->" + data2.getSlots().size
			+ " | skins " + data.getSkins().size + "->" + data2.getSkins().size
			+ " | anims " + data.getAnimations().size + "->" + data2.getAnimations().size);
		if (data2.getBones().size != data.getBones().size) throw new RuntimeException("BONES MISMATCH");
		if (data2.getSlots().size != data.getSlots().size) throw new RuntimeException("SLOTS MISMATCH");
		if (data2.getSkins().size != data.getSkins().size) throw new RuntimeException("SKINS MISMATCH");
		var originalBlue = (com.esotericsoftware.spine.attachments.RegionAttachment)
			data.findSkin("blue").getAttachment(data.findSlot("torso").getIndex(), "torso");
		var reloadedBlue = (com.esotericsoftware.spine.attachments.RegionAttachment)
			data2.findSkin("blue").getAttachment(data2.findSlot("torso").getIndex(), "torso");
		if (!originalBlue.getColor().equals(reloadedBlue.getColor()))
			throw new RuntimeException("REGION COLOR MISMATCH");

		// 验证每个动画的时间线数量等价
		for (var a : data.getAnimations()) {
			var a2 = data2.findAnimation(a.getName());
			if (a2 == null) throw new RuntimeException("missing anim " + a.getName());
			if (a2.getTimelines().size != a.getTimelines().size)
				throw new RuntimeException("timeline mismatch in " + a.getName() + ": " + a.getTimelines().size + " vs " + a2.getTimelines().size);
			System.out.println("  anim " + a.getName() + " timelines " + a.getTimelines().size + " -> " + a2.getTimelines().size + " OK");
		}
		System.out.println("\nEXPORT FILE TEST PASSED");
	}
}
