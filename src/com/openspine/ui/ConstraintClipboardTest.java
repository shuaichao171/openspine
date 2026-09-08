package com.openspine.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SlotData;
import com.openspine.model.SkeletonEditor.*;

/** Checks immutable typed settings, reference rebinding, and lossless Slider time/mapping transfer. */
public final class ConstraintClipboardTest {
	public static void main(String[] args) {
		ConstraintClipboard clipboard = new ConstraintClipboard();
		SkeletonData source = fixture(), target = fixture();
		BoneData child = source.findBone("child"), root = source.findBone("root");
		assertThrows(() -> clipboard.pasteIk(target, "keep"), "尚未复制");

		IkConstraintSetup ik = new IkConstraintSetup("source", child, null, root, true,
			ScaleYMode.values()[1], 0.375f, 12.3f, -1, true, true);
		clipboard.copy(ik);
		IkConstraintSetup pastedIk = clipboard.pasteIk(target, "keep");
		assertEquals("keep", pastedIk.name(), "destination name is preserved");
		assertTrue(pastedIk.firstBone() == target.findBone("child") && pastedIk.target() == target.findBone("root"),
			"IK references resolve in the receiving document");
		assertEquals(ik, clipboard.pasteIk(source, "source"), "all IK setup values round-trip");
		assertThrows(() -> clipboard.pastePhysics(target, "keep"), "不能粘贴到 Physics");
		assertEquals(ik, clipboard.pasteIk(source, "source"), "a rejected paste leaves clipboard unchanged");
		IkConstraintSetup twoBones = new IkConstraintSetup("chain", root, child, source.findBone("target"),
			false, ScaleYMode.none, 1f, 0f, 1, false, false);
		clipboard.copy(twoBones);
		assertTrue(clipboard.pasteIk(target, "chain").secondBone() == target.findBone("child"),
			"two-bone IK rebinds both constrained bones");

		List<TransformToPropertySetup> targets = new ArrayList<>();
		targets.add(new TransformToPropertySetup(TransformProperty.Y, 2.5f, -18f, 4.5f));
		List<TransformFromPropertySetup> properties = new ArrayList<>();
		properties.add(new TransformFromPropertySetup(TransformProperty.ROTATE, 8.5f, targets));
		TransformConstraintSetup transform = new TransformConstraintSetup("transform", List.of(child), root,
			true, true, false, true, true, -1, 2, 3, 4, 5, 6, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, properties);
		clipboard.copy(transform);
		targets.clear(); properties.clear();
		TransformConstraintSetup pastedTransform = clipboard.pasteTransform(target, "keep");
		assertTrue(pastedTransform.bones().get(0) == target.findBone("child")
			&& pastedTransform.source() == target.findBone("root"), "Transform rebinds every bone reference");
		assertEquals(transform, clipboard.pasteTransform(source, "transform"), "all Transform mapping values are immutable");
		assertEquals(1, pastedTransform.properties().get(0).targets().size(), "nested property targets are snapshotted");

		PathConstraintSetup path = new PathConstraintSetup("path", List.of(child), source.findSlot("path-slot"),
			true, PathConstraintData.PositionMode.fixed, PathConstraintData.SpacingMode.proportional,
			PathConstraintData.RotateMode.chainScale, -13f, 29f, 6f, 0.2f, 0.4f, 0.6f);
		clipboard.copy(path);
		assertEquals(path, clipboard.pastePath(source, "path"), "all Path values round-trip");
		assertTrue(clipboard.pastePath(target, "keep").slot() == target.findSlot("path-slot"), "Path slot is rebound");
		SkeletonData missingSlot = fixture(); missingSlot.getSlots().clear();
		assertThrows(() -> clipboard.pastePath(missingSlot, "keep"), "缺少槽“path-slot”");

		PhysicsConstraintSetup physics = new PhysicsConstraintSetup("physics", child, true, ScaleYMode.none,
			1, 2, 3, 4, 5, 678, 120, 0.5f, 123f, 0.8f, 2f, -3f, 4f, 0.6f,
			true, false, true, false, true, false, true);
		clipboard.copy(physics);
		assertEquals(physics, clipboard.pastePhysics(source, "physics"), "all Physics numeric and scope values round-trip");
		assertTrue(clipboard.pastePhysics(target, "keep").bone() == target.findBone("child"), "Physics bone is rebound");
		SkeletonData missingBone = fixture(); missingBone.getBones().removeValue(missingBone.findBone("child"), true);
		assertThrows(() -> clipboard.pastePhysics(missingBone, "keep"), "缺少骨骼“child”");

		SliderConstraintSetup slider = new SliderConstraintSetup("slider", source.findAnimation("driven"),
			true, true, true, child, TransformProperty.SHEAR_Y, -13f, 0.125f, -0.33333334f, 5.5f,
			true, 0f, 0.625f);
		clipboard.copy(slider);
		SliderConstraintSetup pastedSlider = clipboard.pasteSlider(target, "keep");
		assertTrue(pastedSlider.animation() == target.findAnimation("driven")
			&& pastedSlider.bone() == target.findBone("child"), "Slider animation and bone are rebound");
		assertEquals(slider, clipboard.pasteSlider(source, "slider"), "Slider runtime mapping stays lossless");
		SliderConstraintDialog.RangeMapping display24 = SliderConstraintDialog.rangeMapping(pastedSlider.sourceOffset(),
			pastedSlider.targetOffset(), pastedSlider.scale(), pastedSlider.max(), 24f);
		assertEquals(3f, display24.frameMin(), "Slider seconds are displayed at the destination FPS");
		SkeletonData missingAnimation = fixture(); missingAnimation.getAnimations().clear();
		assertThrows(() -> clipboard.pasteSlider(missingAnimation, "keep"), "缺少动画“driven”");
		assertThrows(() -> clipboard.pasteSlider(missingBone, "keep"), "缺少骨骼“child”");
		SliderConstraintSetup time = new SliderConstraintSetup("time", source.findAnimation("driven"),
			false, false, false, null, null, 0, 0, 1, 0, false, 0.33333334f, 1);
		clipboard.copy(time);
		assertEquals(time.time(), clipboard.pasteSlider(missingBone, "keep").time(),
			"time-driven Slider preserves seconds and does not require an unused bone");
		assertTrue(source.getConstraints().isEmpty() && target.getConstraints().isEmpty(),
			"copy and paste never mutate either document");
		System.out.println("ALL CONSTRAINT CLIPBOARD TESTS PASSED");
	}

	private static SkeletonData fixture() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null), child = new BoneData(1, "child", root),
			target = new BoneData(2, "target", root);
		data.getBones().addAll(root, child, target);
		data.getSlots().add(new SlotData(0, "path-slot", root));
		Animation animation = new Animation("driven");
		animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		animation.setDuration(2f);
		data.getAnimations().add(animation);
		return data;
	}

	private static void assertThrows(Runnable action, String expected) {
		try { action.run(); }
		catch (IllegalArgumentException error) {
			assertTrue(error.getMessage().contains(expected), "error explains the reason: " + error.getMessage());
			return;
		}
		throw new AssertionError("Expected failure: " + expected);
	}
	private static void assertTrue(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
	}
}
