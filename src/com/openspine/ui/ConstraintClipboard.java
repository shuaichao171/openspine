package com.openspine.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SlotData;
import com.openspine.model.SkeletonEditor.*;

/** Session-only setup clipboard. Snapshots contain values and names, never live document references. */
final class ConstraintClipboard {
	static final ConstraintClipboard SESSION = new ConstraintClipboard();
	static final String PASTED = "设置已填入草稿，请点击应用；保留名称与当前皮肤激活，皮肤必需关闭时清除激活";

	private record Entry(Object values, List<String> bones, String target, String animation, String type) {
		Entry { bones = List.copyOf(bones); }
	}
	private Entry entry;

	static void addButtons(Table buttons, UiKit kit, Runnable copy, Runnable paste) {
		TextButton copyButton = new TextButton("复制设置", kit.toolButton);
		TextButton pasteButton = new TextButton("粘贴设置", kit.toolButton);
		copyButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { copy.run(); }
		});
		pasteButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { paste.run(); }
		});
		buttons.add(copyButton).width(96).height(28).padRight(6);
		buttons.add(pasteButton).width(96).height(28).padRight(20);
	}

	void copy(IkConstraintSetup setup) {
		List<String> bones = new ArrayList<>();
		bones.add(setup.firstBone().getName());
		if (setup.secondBone() != null) bones.add(setup.secondBone().getName());
		entry = new Entry(ikValues(setup, "", null, null, null), bones,
			setup.target().getName(), null, "IK");
	}

	IkConstraintSetup pasteIk(SkeletonData data, String name) {
		Entry value = require(IkConstraintSetup.class, "IK");
		List<BoneData> bones = bones(data, value.bones());
		return ikValues((IkConstraintSetup)value.values(), name, bones.get(0),
			bones.size() == 1 ? null : bones.get(1), bone(data, value.target()));
	}

	private static IkConstraintSetup ikValues(IkConstraintSetup s, String name,
		BoneData first, BoneData second, BoneData target) {
		return new IkConstraintSetup(name, first, second, target, s.skinRequired(), s.scaleYMode(),
			s.mix(), s.softness(), s.bendDirection(), s.compress(), s.stretch());
	}

	void copy(TransformConstraintSetup setup) {
		entry = new Entry(transformValues(setup, "", List.of(), null), boneNames(setup.bones()),
			setup.source().getName(), null, "Transform");
	}

	TransformConstraintSetup pasteTransform(SkeletonData data, String name) {
		Entry value = require(TransformConstraintSetup.class, "Transform");
		return transformValues((TransformConstraintSetup)value.values(), name,
			bones(data, value.bones()), bone(data, value.target()));
	}

	private static TransformConstraintSetup transformValues(TransformConstraintSetup s, String name,
		List<BoneData> bones, BoneData source) {
		return new TransformConstraintSetup(name, bones, source, s.skinRequired(), s.localSource(),
			s.localTarget(), s.additive(), s.clamp(), s.offsetRotation(), s.offsetX(), s.offsetY(),
			s.offsetScaleX(), s.offsetScaleY(), s.offsetShearY(), s.mixRotate(), s.mixX(), s.mixY(),
			s.mixScaleX(), s.mixScaleY(), s.mixShearY(), s.properties());
	}

	void copy(PathConstraintSetup setup) {
		entry = new Entry(pathValues(setup, "", List.of(), null), boneNames(setup.bones()),
			setup.slot().getName(), null, "Path");
	}

	PathConstraintSetup pastePath(SkeletonData data, String name) {
		Entry value = require(PathConstraintSetup.class, "Path");
		SlotData slot = data.findSlot(value.target());
		if (slot == null) throw missing("槽", value.target());
		return pathValues((PathConstraintSetup)value.values(), name, bones(data, value.bones()), slot);
	}

	private static PathConstraintSetup pathValues(PathConstraintSetup s, String name,
		List<BoneData> bones, SlotData slot) {
		return new PathConstraintSetup(name, bones, slot, s.skinRequired(), s.positionMode(), s.spacingMode(),
			s.rotateMode(), s.offsetRotation(), s.position(), s.spacing(), s.mixRotate(), s.mixX(), s.mixY());
	}

	void copy(PhysicsConstraintSetup setup) {
		entry = new Entry(physicsValues(setup, "", null), List.of(setup.bone().getName()), null, null, "Physics");
	}

	PhysicsConstraintSetup pastePhysics(SkeletonData data, String name) {
		Entry value = require(PhysicsConstraintSetup.class, "Physics");
		return physicsValues((PhysicsConstraintSetup)value.values(), name, bone(data, value.bones().get(0)));
	}

	private static PhysicsConstraintSetup physicsValues(PhysicsConstraintSetup s, String name, BoneData bone) {
		return new PhysicsConstraintSetup(name, bone, s.skinRequired(), s.scaleYMode(), s.x(), s.y(),
			s.rotate(), s.scaleX(), s.shearX(), s.limit(), s.fps(), s.inertia(), s.strength(), s.damping(),
			s.mass(), s.wind(), s.gravity(), s.mix(), s.inertiaGlobal(), s.strengthGlobal(), s.dampingGlobal(),
			s.massGlobal(), s.windGlobal(), s.gravityGlobal(), s.mixGlobal());
	}

	void copy(SliderConstraintSetup setup) {
		entry = new Entry(sliderValues(setup, "", null, null),
			setup.bone() == null ? List.of() : List.of(setup.bone().getName()), null,
			setup.animation().getName(), "Slider");
	}

	SliderConstraintSetup pasteSlider(SkeletonData data, String name) {
		Entry value = require(SliderConstraintSetup.class, "Slider");
		Animation animation = data.findAnimation(value.animation());
		if (animation == null) throw missing("动画", value.animation());
		return sliderValues((SliderConstraintSetup)value.values(), name, animation,
			value.bones().isEmpty() ? null : bone(data, value.bones().get(0)));
	}

	private static SliderConstraintSetup sliderValues(SliderConstraintSetup s, String name,
		Animation animation, BoneData bone) {
		return new SliderConstraintSetup(name, animation, s.skinRequired(), s.additive(), s.loop(), bone,
			s.property(), s.sourceOffset(), s.targetOffset(), s.scale(), s.max(), s.local(), s.time(), s.mix());
	}

	private Entry require(Class<?> type, String label) {
		if (entry == null) throw new IllegalArgumentException("尚未复制约束设置，请先点击复制设置");
		if (!type.isInstance(entry.values()))
			throw new IllegalArgumentException("已复制 " + entry.type() + " 设置，不能粘贴到 " + label + " 约束");
		return entry;
	}

	private static List<String> boneNames(List<BoneData> bones) {
		List<String> names = new ArrayList<>();
		for (BoneData bone : bones) names.add(bone.getName());
		return names;
	}

	private static List<BoneData> bones(SkeletonData data, List<String> names) {
		List<BoneData> bones = new ArrayList<>();
		for (String name : names) bones.add(bone(data, name));
		return bones;
	}

	private static BoneData bone(SkeletonData data, String name) {
		BoneData bone = data.findBone(name);
		if (bone == null) throw missing("骨骼", name);
		return bone;
	}

	private static IllegalArgumentException missing(String type, String name) {
		return new IllegalArgumentException("无法粘贴：当前项目缺少" + type + "“" + name + "”，草稿未改变");
	}
}
