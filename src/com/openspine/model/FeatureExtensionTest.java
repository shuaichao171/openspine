package com.openspine.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;

/** Regression coverage for the newly exposed setup/project editing primitives. */
public final class FeatureExtensionTest {
	public static void main(String[] args) {
		testSlotReorderAndTimelineRemap();
		testSkinBoneActivationAndAppearance();
		System.out.println("ALL FEATURE EXTENSION TESTS PASSED");
	}

	private static void testSlotReorderAndTimelineRemap() {
		SkeletonDocument doc = document();
		BoneData root = doc.data.getBones().first();
		SlotData first = new SlotData(0, "first", root);
		SlotData second = new SlotData(1, "second", root);
		doc.data.getSlots().addAll(first, second);
		Skin skin = new Skin("default");
		RegionAttachment firstAttachment = new RegionAttachment("first", new Sequence(1, false));
		skin.setAttachment(0, "first", firstAttachment);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		Animation animation = new Animation("move");
		Animation.AttachmentTimeline attachment = new Animation.AttachmentTimeline(1, 0);
		attachment.setFrame(0, 0f, "first");
		Animation.DrawOrderTimeline order = new Animation.DrawOrderTimeline(1);
		order.setFrame(0, 0f, new int[] {1, 0});
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(attachment);
		timelines.add(order);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		new SkeletonEditor(doc).rebuildSkeleton();
		SkeletonEditor editor = new SkeletonEditor(doc);
		int[] map = editor.moveSlot(first, 1);
		assertTrue(map != null && map[0] == 1 && map[1] == 0, "slot map tracks moved indices");
		Animation.AttachmentTimeline remapped = (Animation.AttachmentTimeline)animation.getTimelines().first();
		assertTrue(remapped.getSlotIndex() == 1, "attachment timeline follows moved slot");
		assertTrue(skin.getAttachment(1, "first") == firstAttachment, "skin attachment follows moved slot");
		Animation.DrawOrderTimeline remappedOrder = (Animation.DrawOrderTimeline)animation.getTimelines().get(1);
		assertTrue(remappedOrder.getDrawOrders()[0][0] == 0 && remappedOrder.getDrawOrders()[0][1] == 1,
			"draw order payload follows moved slot");
	}

	private static void testSkinBoneActivationAndAppearance() {
		SkeletonDocument doc = document();
		BoneData bone = doc.data.getBones().first();
		bone.setSkinRequired(true);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		SkeletonEditor editor = new SkeletonEditor(doc);
		assertTrue(editor.setSkinBoneActive(skin, bone, true), "skin bone can be activated");
		assertTrue(editor.isSkinBoneActive(skin, bone), "skin activation is readable");
		assertTrue(editor.setSkinBoneActive(skin, bone, false), "skin bone can be deactivated");
		SkeletonEditor.BoneAppearanceState appearance = new SkeletonEditor.BoneAppearanceState(
			new Color(Color.RED), "arm", 16f, 25f, false);
		assertTrue(editor.updateBoneAppearance(bone, appearance), "bone appearance updates");
		assertTrue("arm".equals(bone.getIcon()) && !bone.getVisible() && bone.getColor().equals(Color.RED),
			"bone appearance values persist");
	}

	private static SkeletonDocument document() {
		SkeletonData data = new SkeletonData();
		data.setName("feature-test");
		data.getBones().add(new BoneData(0, "root", null));
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.animationState = new AnimationState(new AnimationStateData(data));
		return doc;
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private FeatureExtensionTest() {}
}
