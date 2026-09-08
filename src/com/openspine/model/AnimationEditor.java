package com.openspine.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.IkConstraintPose;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.Animation.BoneTimeline;
import com.esotericsoftware.spine.Animation.AttachmentTimeline;
import com.esotericsoftware.spine.Animation.AlphaTimeline;
import com.esotericsoftware.spine.Animation.BoneTimeline1;
import com.esotericsoftware.spine.Animation.BoneTimeline2;
import com.esotericsoftware.spine.Animation.CurveTimeline;
import com.esotericsoftware.spine.Animation.ConstraintTimeline;
import com.esotericsoftware.spine.Animation.DrawOrderFolderTimeline;
import com.esotericsoftware.spine.Animation.DrawOrderTimeline;
import com.esotericsoftware.spine.Animation.DeformTimeline;
import com.esotericsoftware.spine.Animation.EventTimeline;
import com.esotericsoftware.spine.Animation.InheritTimeline;
import com.esotericsoftware.spine.Animation.IkConstraintTimeline;
import com.esotericsoftware.spine.Animation.PathConstraintMixTimeline;
import com.esotericsoftware.spine.Animation.PathConstraintPositionTimeline;
import com.esotericsoftware.spine.Animation.PathConstraintSpacingTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintDampingTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintGravityTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintInertiaTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintMassTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintMixTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintResetTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintStrengthTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintTimeline;
import com.esotericsoftware.spine.Animation.PhysicsConstraintWindTimeline;
import com.esotericsoftware.spine.Animation.TransformConstraintTimeline;
import com.esotericsoftware.spine.Animation.RGB2Timeline;
import com.esotericsoftware.spine.Animation.RGBATimeline;
import com.esotericsoftware.spine.Animation.RGBA2Timeline;
import com.esotericsoftware.spine.Animation.RGBTimeline;
import com.esotericsoftware.spine.Animation.RotateTimeline;
import com.esotericsoftware.spine.Animation.ScaleTimeline;
import com.esotericsoftware.spine.Animation.SequenceTimeline;
import com.esotericsoftware.spine.Animation.ShearTimeline;
import com.esotericsoftware.spine.Animation.ShearXTimeline;
import com.esotericsoftware.spine.Animation.ShearYTimeline;
import com.esotericsoftware.spine.Animation.SliderMixTimeline;
import com.esotericsoftware.spine.Animation.SliderTimeline;
import com.esotericsoftware.spine.Animation.TranslateTimeline;
import com.esotericsoftware.spine.Animation.CurveTimeline1;
import com.esotericsoftware.spine.Animation.Timeline;
import com.esotericsoftware.spine.Animation.SlotCurveTimeline;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.HasSequence;
import com.esotericsoftware.spine.attachments.Sequence.SequenceMode;
import com.esotericsoftware.spine.attachments.VertexAttachment;

/**
 * 时间线动画编辑器：封装对 Animation 内时间线的增删改关键帧操作。
 *
 * 约束（来自源码）：Timeline.frames / propertyIds 是 final 数组，
 * 因此改变帧数时必须"新建同类型实例 + 回填 + 替换"，再调用
 * animation.setTimelines() 重算 propertyIDs。
 *
 * 帧布局：
 * - 单值时间线 (BoneTimeline1): frames=[time,value]*, ENTRIES=2
 * - 双值时间线 (BoneTimeline2): frames=[time,v1,v2]*, ENTRIES=3
	 * - RGBA 时间线: frames=[time,r,g,b,a]*, ENTRIES=5
	 * - RGB2/RGBA2 时间线: frames=[time,light...,darkR,darkG,darkB]*, ENTRIES=7/8
 * - CurveTimeline.curves[]: 每帧 0=linear, 1=stepped, >=2 = bezier
 */
public class AnimationEditor {
	private static final Field CURVES_FIELD = findCurvesField();
	public final SkeletonDocument doc;

	public AnimationEditor(SkeletonDocument doc) {
		this.doc = doc;
	}

	// ================= 轨道枚举 =================

	/** 时间线属性类型 */
	public enum Prop {
		ROTATE, TRANSLATE_X, TRANSLATE_Y, TRANSLATE, SCALE_X, SCALE_Y, SCALE,
		SHEAR_X, SHEAR_Y, SHEAR
	}

	/** Bone transform groups which Spine can store as combined or independent axis timelines. */
	public enum AxisGroup { TRANSLATE, SCALE, SHEAR }

	/** Editable Path constraint channels. Each value maps to one Spine timeline property. */
	public enum PathProp { POSITION, SPACING, MIX }

	/** Editable Physics constraint channels. Reset is an instant timeline; all others are scalar curves. */
	public enum PhysicsProp { RESET, INERTIA, STRENGTH, DAMPING, MASS, WIND, GRAVITY, MIX }

	/** Editable Slider constraint channels. Both values are scalar curves. */
	public enum SliderProp { TIME, MIX }
	public enum AnimationImportConflict { RENAME, REPLACE, SKIP }
	public record AnimationImportChange(Animation previous, Animation imported, int index) {}
	public record AnimationImportResult(List<AnimationImportChange> changes, int skipped) {
		public AnimationImportResult {
			changes = changes == null ? List.of() : List.copyOf(changes);
			if (skipped < 0) throw new IllegalArgumentException("跳过动画数量不能小于零");
		}
		public List<Animation> importedAnimations() {
			return changes.stream().map(AnimationImportChange::imported).toList();
		}
		public long replaced() { return changes.stream().filter(change -> change.previous() != null).count(); }
	}

	/** Complete value stored by one instant Sequence key. */
	public record SequenceKeyframe(SequenceMode mode, int index, float delay) {}
	public record DeformTarget(int slotIndex, VertexAttachment attachment) {}

	/** 解析 timeline 的属性（根据具体类型） */
	public Prop propOf(Animation.Timeline tl) {
		if (tl instanceof RotateTimeline) return Prop.ROTATE;
		if (tl instanceof TranslateTimeline) return Prop.TRANSLATE;
		if (tl instanceof com.esotericsoftware.spine.Animation.TranslateXTimeline) return Prop.TRANSLATE_X;
		if (tl instanceof com.esotericsoftware.spine.Animation.TranslateYTimeline) return Prop.TRANSLATE_Y;
		if (tl instanceof ScaleTimeline) return Prop.SCALE;
		if (tl instanceof com.esotericsoftware.spine.Animation.ScaleXTimeline) return Prop.SCALE_X;
		if (tl instanceof com.esotericsoftware.spine.Animation.ScaleYTimeline) return Prop.SCALE_Y;
		if (tl instanceof ShearTimeline) return Prop.SHEAR;
		if (tl instanceof ShearXTimeline) return Prop.SHEAR_X;
		if (tl instanceof ShearYTimeline) return Prop.SHEAR_Y;
		return null;
	}

	/** 某骨骼的某属性，在指定动画中是否已有时间线 */
	public Animation.Timeline findTimeline(Animation anim, Prop prop, int boneIndex) {
		if (anim == null) return null;
		for (Animation.Timeline tl : anim.getTimelines()) {
			if (tl instanceof BoneTimeline && ((BoneTimeline) tl).getBoneIndex() == boneIndex && propOf(tl) == prop) {
				return tl;
			}
		}
		return null;
	}

	public boolean isAxisSeparated(Animation animation, AxisGroup group, int boneIndex) {
		Prop[] props = axisProps(group);
		return findTimeline(animation, props[1], boneIndex) != null
			|| findTimeline(animation, props[2], boneIndex) != null;
	}

	/** Captures every editor-owned value affected by one Separate/Combine operation. */
	public AxisConversionSnapshot snapshotAxisConversion(Animation animation, AxisGroup group, int boneIndex) {
		if (animation == null) throw new IllegalArgumentException("动画不能为空");
		String boneName = axisBoneName(boneIndex);
		Prop[] props = axisProps(group);
		TimelineSnapshot[] timelines = new TimelineSnapshot[props.length];
		LinkedHashMap<String, SkeletonDocument.GraphViewState> graphStates = new LinkedHashMap<>();
		for (int index = 0; index < props.length; index++) {
			timelines[index] = snapshotBoneTimeline(animation, props[index], boneIndex);
			Animation.Timeline timeline = findTimeline(animation, props[index], boneIndex);
			if (timeline == null) continue;
			String key = SkeletonDocument.graphViewStateKey(animation.getName(), timeline);
			SkeletonDocument.GraphViewState view = doc.graphViewState(key);
			if (view != null) graphStates.put(key, view);
		}
		return new AxisConversionSnapshot(animation, group, boneIndex, boneName, timelines,
			doc.isAxisSeparationPreferred(animation.getName(), boneName, group.name()),
			doc.axisCombineState(animation.getName(), boneName, group.name()), graphStates);
	}

	/** Restores an axis conversion as one transaction, including reversible and Graph editor state. */
	public void restoreAxisConversion(AxisConversionSnapshot snapshot) {
		if (snapshot == null) throw new IllegalArgumentException("轴转换快照不能为空");
		Prop[] props = axisProps(snapshot.group);
		LinkedHashSet<String> graphKeys = new LinkedHashSet<>(snapshot.graphStates.keySet());
		collectAxisGraphKeys(snapshot.animation, snapshot.boneIndex, props, graphKeys);
		restoreTimelines(snapshot.timelines);
		collectAxisGraphKeys(snapshot.animation, snapshot.boneIndex, props, graphKeys);
		for (String key : graphKeys) doc.removeGraphViewState(key);
		for (Map.Entry<String, SkeletonDocument.GraphViewState> entry : snapshot.graphStates.entrySet())
			doc.setGraphViewState(entry.getKey(), entry.getValue());
		doc.setAxisSeparationPreferred(snapshot.animation.getName(), snapshot.boneName,
			snapshot.group.name(), snapshot.separatedPreferred);
		if (snapshot.combineState == null)
			doc.removeAxisCombineState(snapshot.animation.getName(), snapshot.boneName, snapshot.group.name());
		else doc.setAxisCombineState(snapshot.combineState);
		doc.refreshCurrentAnimationPose();
	}

	private void collectAxisGraphKeys(Animation animation, int boneIndex, Prop[] props, LinkedHashSet<String> keys) {
		for (Prop prop : props) {
			Animation.Timeline timeline = findTimeline(animation, prop, boneIndex);
			if (timeline != null) keys.add(SkeletonDocument.graphViewStateKey(animation.getName(), timeline));
		}
	}

	/** Splits a combined X/Y timeline by copying each channel and its raw Bezier samples exactly. */
	public boolean separateAxisTimeline(Animation animation, AxisGroup group, int boneIndex) {
		if (animation == null) throw new IllegalArgumentException("动画不能为空");
		Prop[] props = axisProps(group);
		Animation.Timeline combined = findTimeline(animation, props[0], boneIndex);
		Animation.Timeline x = findTimeline(animation, props[1], boneIndex);
		Animation.Timeline y = findTimeline(animation, props[2], boneIndex);
		if (combined == null) return false;
		if (x != null || y != null)
			throw new IllegalStateException("组合轨道与单轴轨道同时存在，不能无损分离");
		if (!(combined instanceof BoneTimeline2) || !(combined instanceof CurveTimeline sourceCurve))
			throw new IllegalStateException("组合轨道类型无效");
		validateCurveData(combined);
		String boneName = axisBoneName(boneIndex);
		SkeletonDocument.AxisCombineState archived = doc.axisCombineState(
			animation.getName(), boneName, group.name());
		if (archived != null && axisCombinedStateMatches(archived, combined)) {
			Animation.Timeline splitX = restoreAxisTimeline(props[1], boneIndex,
				archived.xFrames(), archived.xCurves());
			Animation.Timeline splitY = restoreAxisTimeline(props[2], boneIndex,
				archived.yFrames(), archived.yCurves());
			if (archived.xBeforeY()) replaceOneWithTwo(animation, combined, splitX, splitY);
			else replaceOneWithTwo(animation, combined, splitY, splitX);
			doc.splitGraphViewState(animation.getName(), combined, splitX, splitY);
			doc.removeAxisCombineState(animation.getName(), boneName, group.name());
			doc.markDirty();
			return true;
		}
		if (archived != null) doc.removeAxisCombineState(animation.getName(), boneName, group.name());

		int count = combined.getFrameCount();
		int bezierSegments = 0;
		for (int frame = 0; frame < count - 1; frame++)
			if (sourceCurve.getCurveType(frame) >= CurveTimeline.BEZIER) bezierSegments++;
		Animation.Timeline splitX = newTimeline(props[1], boneIndex, count, bezierSegments);
		Animation.Timeline splitY = newTimeline(props[2], boneIndex, count, bezierSegments);
		for (int frame = 0; frame < count; frame++) {
			((CurveTimeline1)splitX).setFrame(frame, frameTime(combined, frame), frameValue(combined, frame, 0));
			((CurveTimeline1)splitY).setFrame(frame, frameTime(combined, frame), frameValue(combined, frame, 1));
		}
		copySplitChannelCurves(combined, splitX, 0);
		copySplitChannelCurves(combined, splitY, 1);
		replaceOneWithTwo(animation, combined, splitX, splitY);
		doc.splitGraphViewState(animation.getName(), combined, splitX, splitY);
		doc.markDirty();
		return true;
	}

	/** Combines independent axes on their time union when one Spine timeline can represent them exactly. */
	public boolean combineAxisTimelines(Animation animation, AxisGroup group, int boneIndex) {
		if (animation == null) throw new IllegalArgumentException("动画不能为空");
		Prop[] props = axisProps(group);
		Animation.Timeline combined = findTimeline(animation, props[0], boneIndex);
		Animation.Timeline x = findTimeline(animation, props[1], boneIndex);
		Animation.Timeline y = findTimeline(animation, props[2], boneIndex);
		if (combined != null) {
			if (x != null || y != null)
				throw new IllegalStateException("组合轨道与单轴轨道同时存在，请先删除冲突轨道");
			return false;
		}
		if (x == null && y == null) return false;
		if (x == null || y == null)
			throw new IllegalStateException("必须同时存在 X 和 Y 轨道才能无损合并");
		if (!(x instanceof CurveTimeline xCurve) || !(y instanceof CurveTimeline yCurve))
			throw new IllegalStateException("单轴轨道类型无效");
		validateCurveData(x);
		validateCurveData(y);
		if (Float.compare(frameTime(x, 0), frameTime(y, 0)) != 0)
			throw new IllegalStateException("X/Y 首帧时间不同，组合轨道无法无损表示生效时机");
		float[] times = axisRuntimeTimeUnion(x, y);
		int count = times.length;
		AxisSegment[] xSegments = new AxisSegment[Math.max(0, count - 1)];
		AxisSegment[] ySegments = new AxisSegment[Math.max(0, count - 1)];
		boolean[] stepped = new boolean[Math.max(0, count - 1)];
		for (int frame = 0; frame < count - 1; frame++) {
			xSegments[frame] = axisSegment(x, times[frame], times[frame + 1]);
			ySegments[frame] = axisSegment(y, times[frame], times[frame + 1]);
			boolean xStepped = xSegments[frame].stepped;
			boolean yStepped = ySegments[frame].stepped;
			if (xStepped != yStepped) {
				AxisSegment continuous = xStepped ? ySegments[frame] : xSegments[frame];
				AxisSegment step = xStepped ? xSegments[frame] : ySegments[frame];
				if (!continuous.constant && !step.constant)
					throw new IllegalStateException("X/Y 步进段与连续变化冲突，组合轨道无法无损表示");
				stepped[frame] = !step.constant;
			} else {
				stepped[frame] = xStepped;
			}
		}

		Animation.Timeline joined = newTimeline(props[0], boneIndex, count, 0);
		BoneTimeline2 joinedFrames = (BoneTimeline2)joined;
		for (int frame = 0; frame < count; frame++)
			joinedFrames.setFrame(frame, times[frame], axisValueAt(x, times[frame]), axisValueAt(y, times[frame]));
		CurveTimeline joinedCurve = (CurveTimeline)joined;
		for (int frame = 0; frame < count - 1; frame++) {
			if (stepped[frame]) {
				joinedCurve.setStepped(frame);
			} else joinedCurve.setLinear(frame);
		}
		String boneName = axisBoneName(boneIndex);
		int xIndex = animation.getTimelines().indexOf(x, true);
		int yIndex = animation.getTimelines().indexOf(y, true);
		doc.setAxisCombineState(new SkeletonDocument.AxisCombineState(animation.getName(), boneName,
			group.name(), xIndex <= yIndex, x.getFrames(), curveData(xCurve), y.getFrames(), curveData(yCurve),
			joined.getFrames(), curveData(joinedCurve)));
		replaceTwoWithOne(animation, x, y, joined);
		doc.combineGraphViewState(animation.getName(), x, y, joined);
		doc.markDirty();
		return true;
	}

	private String axisBoneName(int boneIndex) {
		if (doc.data == null || boneIndex < 0 || boneIndex >= doc.data.getBones().size)
			throw new IllegalArgumentException("XY 轨道骨骼索引无效");
		return doc.data.getBones().get(boneIndex).getName();
	}

	private static boolean axisCombinedStateMatches(SkeletonDocument.AxisCombineState state,
		Animation.Timeline combined) {
		return combined instanceof CurveTimeline curve
			&& java.util.Arrays.equals(state.combinedFrames(), combined.getFrames())
			&& java.util.Arrays.equals(state.combinedCurves(), curveData(curve));
	}

	private Animation.Timeline restoreAxisTimeline(Prop prop, int boneIndex, float[] frames, float[] curves) {
		if (frames == null || frames.length == 0 || (frames.length & 1) != 0)
			throw new IllegalStateException("保存的单轴关键帧结构无效");
		int frameCount = frames.length / 2;
		if (curves == null || curves.length < frameCount
			|| (curves.length - frameCount) % CurveTimeline.BEZIER_SIZE != 0)
			throw new IllegalStateException("保存的单轴曲线结构无效");
		int bezierCount = (curves.length - frameCount) / CurveTimeline.BEZIER_SIZE;
		Animation.Timeline restored = newTimeline(prop, boneIndex, frameCount, bezierCount);
		System.arraycopy(frames, 0, restored.getFrames(), 0, frames.length);
		float[] restoredCurves = curveData((CurveTimeline)restored);
		if (restoredCurves.length != curves.length)
			throw new IllegalStateException("保存的单轴曲线容量不匹配");
		System.arraycopy(curves, 0, restoredCurves, 0, curves.length);
		return restored;
	}

	private static Prop[] axisProps(AxisGroup group) {
		if (group == null) throw new IllegalArgumentException("XY 轨道组不能为空");
		return switch (group) {
		case TRANSLATE -> new Prop[] {Prop.TRANSLATE, Prop.TRANSLATE_X, Prop.TRANSLATE_Y};
		case SCALE -> new Prop[] {Prop.SCALE, Prop.SCALE_X, Prop.SCALE_Y};
		case SHEAR -> new Prop[] {Prop.SHEAR, Prop.SHEAR_X, Prop.SHEAR_Y};
		};
	}

	private static final class AxisSegment {
		final float value1, value2, x1, y1, x2, y2;
		final boolean stepped, bezier, constant;

		AxisSegment(float value1, float value2, float x1, float y1, float x2, float y2,
			boolean stepped, boolean bezier) {
			this.value1 = value1;
			this.value2 = value2;
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.stepped = stepped;
			this.bezier = bezier;
			this.constant = Float.compare(value1, value2) == 0 && Float.compare(value1, y1) == 0
			&& Float.compare(value1, y2) == 0;
		}
	}

	private static float[] axisRuntimeTimeUnion(Animation.Timeline x, Animation.Timeline y) {
		ArrayList<Float> union = new ArrayList<>(x.getFrameCount() + y.getFrameCount());
		addAxisRuntimeTimes(union, x);
		addAxisRuntimeTimes(union, y);
		union.sort(Float::compare);
		float[] result = new float[union.size()];
		int count = 0;
		for (float time : union) {
			if (count == 0 || Float.compare(result[count - 1], time) != 0) result[count++] = time;
		}
		return count == result.length ? result : java.util.Arrays.copyOf(result, count);
	}

	private static void addAxisRuntimeTimes(ArrayList<Float> times, Animation.Timeline timeline) {
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			times.add(frameTimeStatic(timeline, frame));
		if (!(timeline instanceof CurveTimeline curve)) return;
		float[] data = curveData(curve);
		for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
			int type = curve.getCurveType(frame);
			if (type < CurveTimeline.BEZIER) continue;
			int block = type - CurveTimeline.BEZIER;
			for (int point = 0; point < CurveTimeline.BEZIER_SIZE; point += 2) {
				float time = data[block + point];
				if (Float.isFinite(time)) times.add(time);
			}
		}
	}

	private static float frameTimeStatic(Animation.Timeline timeline, int frame) {
		return timeline.getFrames()[frame * timeline.getFrameEntries()];
	}

	private static float axisValueAt(Animation.Timeline timeline, float time) {
		int count = timeline.getFrameCount();
		if (time <= frameTimeStatic(timeline, 0)) return timeline.getFrames()[1];
		if (time >= frameTimeStatic(timeline, count - 1))
			return timeline.getFrames()[(count - 1) * timeline.getFrameEntries() + 1];
		int frame = axisSegmentIndex(timeline, time);
		float time1 = frameTimeStatic(timeline, frame), time2 = frameTimeStatic(timeline, frame + 1);
		float value1 = timeline.getFrames()[frame * timeline.getFrameEntries() + 1];
		float value2 = timeline.getFrames()[(frame + 1) * timeline.getFrameEntries() + 1];
		if (Float.compare(time, time1) == 0) return value1;
		if (Float.compare(time, time2) == 0) return value2;
		int type = ((CurveTimeline)timeline).getCurveType(frame);
		if (type == CurveTimeline.STEPPED) return value1;
		if (type < CurveTimeline.BEZIER) return value1 + (value2 - value1) * (time - time1) / (time2 - time1);
		return ((CurveTimeline)timeline).getBezierValue(time, frame * timeline.getFrameEntries(), 1,
			type - CurveTimeline.BEZIER);
	}

	private static int axisSegmentIndex(Animation.Timeline timeline, float time) {
		for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++)
			if (time < frameTimeStatic(timeline, frame + 1)) return frame;
		return timeline.getFrameCount() - 1;
	}

	private static AxisSegment axisSegment(Animation.Timeline timeline, float start, float end) {
		float value1 = axisValueAt(timeline, start), value2 = axisValueAt(timeline, end);
		int sourceFrame = axisSegmentIndex(timeline, start);
		if (sourceFrame >= timeline.getFrameCount() - 1) return linearAxisSegment(start, end, value1, value2);
		CurveTimeline curve = (CurveTimeline)timeline;
		int type = curve.getCurveType(sourceFrame);
		if (type == CurveTimeline.STEPPED)
			return new AxisSegment(value1, value2, start, value1, end, value1, true, false);
		// Runtime Bezier curves are evaluated as linear interpolation between their stored samples.
		// axisRuntimeTimeUnion includes every sample time, so this interval is exactly linear.
		return linearAxisSegment(start, end, value1, value2);
	}

	private static AxisSegment linearAxisSegment(float start, float end, float value1, float value2) {
		return new AxisSegment(value1, value2, start + (end - start) / 3f,
			value1 + (value2 - value1) / 3f, start + (end - start) * 2f / 3f,
			value1 + (value2 - value1) * 2f / 3f, false, false);
	}

	private static float solveCubicTime(float[] cubic, float time) {
		if (time <= cubic[0]) return 0f;
		if (time >= cubic[6]) return 1f;
		float low = 0f, high = 1f;
		for (int iteration = 0; iteration < 40; iteration++) {
			float middle = (low + high) * 0.5f;
			float value = cubic(cubic[0], cubic[2], cubic[4], cubic[6], middle);
			if (value < time) low = middle; else high = middle;
		}
		return (low + high) * 0.5f;
	}

	private static float[] cubicInterval(float[] source, float start, float end) {
		if (start <= 0f && end >= 1f) return source.clone();
		float[] left = splitCubic(source, end, true);
		if (start <= 0f) return left;
		return splitCubic(left, start / end, false);
	}

	private static float[] splitCubic(float[] source, float u, boolean left) {
		float x01 = lerp(source[0], source[2], u), y01 = lerp(source[1], source[3], u);
		float x12 = lerp(source[2], source[4], u), y12 = lerp(source[3], source[5], u);
		float x23 = lerp(source[4], source[6], u), y23 = lerp(source[5], source[7], u);
		float x012 = lerp(x01, x12, u), y012 = lerp(y01, y12, u);
		float x123 = lerp(x12, x23, u), y123 = lerp(y12, y23, u);
		float x = lerp(x012, x123, u), y = lerp(y012, y123, u);
		return left ? new float[] {source[0], source[1], x01, y01, x012, y012, x, y}
			: new float[] {x, y, x123, y123, x23, y23, source[6], source[7]};
	}

	private static float lerp(float start, float end, float percent) {
		return start + (end - start) * percent;
	}

	private static float cubic(float p0, float p1, float p2, float p3, float u) {
		float one = 1f - u;
		return one * one * one * p0 + 3f * one * one * u * p1 + 3f * one * u * u * p2 + u * u * u * p3;
	}

	private static void copySplitChannelCurves(Animation.Timeline source, Animation.Timeline target, int channel) {
		CurveTimeline sourceCurve = (CurveTimeline)source;
		CurveTimeline targetCurve = (CurveTimeline)target;
		float[] sourceData = curveData(sourceCurve);
		float[] targetData = curveData(targetCurve);
		int targetBlock = target.getFrameCount();
		for (int frame = 0; frame < source.getFrameCount() - 1; frame++) {
			int type = sourceCurve.getCurveType(frame);
			if (type == CurveTimeline.STEPPED) {
				targetCurve.setStepped(frame);
			} else if (type < CurveTimeline.BEZIER) {
				targetCurve.setLinear(frame);
			} else {
				targetData[frame] = CurveTimeline.BEZIER + targetBlock;
				System.arraycopy(sourceData, type - CurveTimeline.BEZIER + channel * CurveTimeline.BEZIER_SIZE,
					targetData, targetBlock, CurveTimeline.BEZIER_SIZE);
				targetBlock += CurveTimeline.BEZIER_SIZE;
			}
		}
		targetCurve.shrink((targetBlock - target.getFrameCount()) / CurveTimeline.BEZIER_SIZE);
	}

	private void replaceOneWithTwo(Animation animation, Animation.Timeline source,
		Animation.Timeline first, Animation.Timeline second) {
		Array<Animation.Timeline> timelines = animation.getTimelines();
		int index = timelines.indexOf(source, true);
		if (index < 0) throw new IllegalStateException("找不到待分离的轨道");
		timelines.removeIndex(index);
		timelines.insert(index, first);
		timelines.insert(index + 1, second);
		refreshAnimation(animation);
	}

	private void replaceTwoWithOne(Animation animation, Animation.Timeline first,
		Animation.Timeline second, Animation.Timeline replacement) {
		Array<Animation.Timeline> timelines = animation.getTimelines();
		int firstIndex = timelines.indexOf(first, true), secondIndex = timelines.indexOf(second, true);
		if (firstIndex < 0 || secondIndex < 0) throw new IllegalStateException("找不到待合并的轨道");
		int insert = Math.min(firstIndex, secondIndex);
		if (firstIndex > secondIndex) { timelines.removeIndex(firstIndex); timelines.removeIndex(secondIndex); }
		else { timelines.removeIndex(secondIndex); timelines.removeIndex(firstIndex); }
		timelines.insert(insert, replacement);
		refreshAnimation(animation);
	}

	/** Finds the discrete transform-inheritance timeline for a bone. */
	public InheritTimeline findInheritTimeline(Animation animation, int boneIndex) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof InheritTimeline inherit && inherit.getBoneIndex() == boneIndex) return inherit;
		}
		return null;
	}

	/** Finds the discrete attachment timeline for a slot. */
	public AttachmentTimeline findAttachmentTimeline(Animation animation, int slotIndex) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof AttachmentTimeline attachment && attachment.getSlotIndex() == slotIndex)
				return attachment;
		}
		return null;
	}

	/** Finds the light-color RGBA timeline for a slot. */
	public RGBATimeline findColorTimeline(Animation animation, int slotIndex) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof RGBATimeline color && color.getSlotIndex() == slotIndex) return color;
		}
		return null;
	}

	/** Finds the RGB-only light-color timeline for a slot. */
	public RGBTimeline findRgbTimeline(Animation animation, int slotIndex) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof RGBTimeline color && color.getSlotIndex() == slotIndex) return color;
		}
		return null;
	}

	/** Finds the alpha-only light-color timeline for a slot. */
	public AlphaTimeline findAlphaTimeline(Animation animation, int slotIndex) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof AlphaTimeline alpha && alpha.getSlotIndex() == slotIndex) return alpha;
		}
		return null;
	}

	/** Finds an RGB2 or RGBA2 two-color timeline for a slot. */
	public SlotCurveTimeline findTwoColorTimeline(Animation animation, int slotIndex) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof RGB2Timeline color && color.getSlotIndex() == slotIndex) return color;
			if (timeline instanceof RGBA2Timeline color && color.getSlotIndex() == slotIndex) return color;
		}
		return null;
	}

	public boolean isTwoColorTimeline(Timeline timeline) {
		return timeline instanceof RGB2Timeline || timeline instanceof RGBA2Timeline;
	}

	public boolean twoColorHasAlpha(Timeline timeline) {
		if (!isTwoColorTimeline(timeline)) throw new IllegalArgumentException("不是双色染色轨道");
		return timeline instanceof RGBA2Timeline;
	}

	/** Finds the single global event timeline for an animation. */
	public EventTimeline findEventTimeline(Animation animation) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof EventTimeline events) return events;
		}
		return null;
	}

	/** Finds the single global draw-order timeline for an animation. */
	public DrawOrderTimeline findDrawOrderTimeline(Animation animation) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof DrawOrderTimeline drawOrder) return drawOrder;
		}
		return null;
	}

	/** Finds one local draw-order folder by its setup-order slot set. */
	public DrawOrderFolderTimeline findDrawOrderFolderTimeline(Animation animation, int[] slots) {
		if (animation == null || slots == null) return null;
		for (Timeline timeline : animation.getTimelines())
			if (timeline instanceof DrawOrderFolderTimeline folder
				&& java.util.Arrays.equals(folder.getSlots(), slots)) return folder;
		return null;
	}

	/** Finds the IK timeline targeting one entry in SkeletonData.constraints. */
	public IkConstraintTimeline findIkTimeline(Animation animation, int constraintIndex) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof IkConstraintTimeline ik && ik.getConstraintIndex() == constraintIndex) return ik;
		}
		return null;
	}

	public record IkKeyframe(float mix, float softness, int bendDirection, boolean compress, boolean stretch) {}

	public IkKeyframe ikAt(IkConstraintTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("IK 关键帧索引无效");
		int base = frame * timeline.getFrameEntries();
		float[] values = timeline.getFrames();
		return new IkKeyframe(values[base + 1], values[base + 2], (int)values[base + 3],
			values[base + 4] != 0f, values[base + 5] != 0f);
	}

	/** Finds the Transform timeline targeting one entry in SkeletonData.constraints. */
	public TransformConstraintTimeline findTransformTimeline(Animation animation, int constraintIndex) {
		if (animation == null) return null;
		for (Timeline timeline : animation.getTimelines())
			if (timeline instanceof TransformConstraintTimeline transform
				&& transform.getConstraintIndex() == constraintIndex) return transform;
		return null;
	}

	public record TransformKeyframe(float mixRotate, float mixX, float mixY, float mixScaleX,
		float mixScaleY, float mixShearY) {}

	public TransformKeyframe transformAt(TransformConstraintTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("Transform keyframe index is invalid");
		int base = frame * timeline.getFrameEntries();
		float[] values = timeline.getFrames();
		return new TransformKeyframe(values[base + 1], values[base + 2], values[base + 3],
			values[base + 4], values[base + 5], values[base + 6]);
	}

	/** Finds one Path constraint timeline targeting an entry in SkeletonData.constraints. */
	public Timeline findPathTimeline(Animation animation, int constraintIndex, PathProp prop) {
		if (animation == null || prop == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (!(timeline instanceof Animation.ConstraintTimeline constraint)
				|| constraint.getConstraintIndex() != constraintIndex) continue;
			if (prop == PathProp.POSITION && timeline instanceof PathConstraintPositionTimeline) return timeline;
			if (prop == PathProp.SPACING && timeline instanceof PathConstraintSpacingTimeline) return timeline;
			if (prop == PathProp.MIX && timeline instanceof PathConstraintMixTimeline) return timeline;
		}
		return null;
	}

	public float pathValueAt(Timeline timeline, int frame) {
		if (!(timeline instanceof PathConstraintPositionTimeline
			|| timeline instanceof PathConstraintSpacingTimeline)
			|| frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("Path 数值关键帧索引无效");
		return timeline.getFrames()[frame * timeline.getFrameEntries() + 1];
	}

	public record PathMixKeyframe(float mixRotate, float mixX, float mixY) {}

	public PathMixKeyframe pathMixAt(PathConstraintMixTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("Path 混合关键帧索引无效");
		int base = frame * timeline.getFrameEntries();
		float[] values = timeline.getFrames();
		return new PathMixKeyframe(values[base + 1], values[base + 2], values[base + 3]);
	}

	public Timeline findPhysicsTimeline(Animation animation, int constraintIndex, PhysicsProp prop) {
		if (animation == null || prop == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (physicsPropOf(timeline) != prop) continue;
			if (((Animation.ConstraintTimeline)timeline).getConstraintIndex() == constraintIndex) return timeline;
		}
		return null;
	}

	public PhysicsProp physicsPropOf(Timeline timeline) {
		return physicsPropertyOf(timeline);
	}

	/** Maps a Spine Physics timeline class to the property it animates. */
	public static PhysicsProp physicsPropertyOf(Timeline timeline) {
		if (timeline instanceof PhysicsConstraintResetTimeline) return PhysicsProp.RESET;
		if (timeline instanceof PhysicsConstraintInertiaTimeline) return PhysicsProp.INERTIA;
		if (timeline instanceof PhysicsConstraintStrengthTimeline) return PhysicsProp.STRENGTH;
		if (timeline instanceof PhysicsConstraintDampingTimeline) return PhysicsProp.DAMPING;
		if (timeline instanceof PhysicsConstraintMassTimeline) return PhysicsProp.MASS;
		if (timeline instanceof PhysicsConstraintWindTimeline) return PhysicsProp.WIND;
		if (timeline instanceof PhysicsConstraintGravityTimeline) return PhysicsProp.GRAVITY;
		if (timeline instanceof PhysicsConstraintMixTimeline) return PhysicsProp.MIX;
		return null;
	}

	/** Matches the global-property filtering used by spine-libgdx PhysicsConstraintTimeline.apply. */
	public static boolean physicsPropertyIsGlobal(PhysicsConstraintData constraint, PhysicsProp prop) {
		if (constraint == null || prop == null || prop == PhysicsProp.RESET) return false;
		return switch (prop) {
		case INERTIA -> constraint.getInertiaGlobal();
		case STRENGTH -> constraint.getStrengthGlobal();
		case DAMPING -> constraint.getDampingGlobal();
		case MASS -> constraint.getMassGlobal();
		case WIND -> constraint.getWindGlobal();
		case GRAVITY -> constraint.getGravityGlobal();
		case MIX -> constraint.getMixGlobal();
		case RESET -> false;
		};
	}

	public static boolean hasGlobalPhysicsProperty(SkeletonData data, PhysicsProp prop) {
		if (data == null || prop == null || prop == PhysicsProp.RESET) return false;
		for (ConstraintData<?, ?> constraint : data.getConstraints())
			if (constraint instanceof PhysicsConstraintData physics && physicsPropertyIsGlobal(physics, prop))
				return true;
		return false;
	}

	public float physicsValueAt(Timeline timeline, int frame) {
		PhysicsProp prop = physicsPropOf(timeline);
		if (prop == null || prop == PhysicsProp.RESET || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("Physics 数值关键帧索引无效");
		return timeline.getFrames()[frame * timeline.getFrameEntries() + 1];
	}

	public Timeline findSliderTimeline(Animation animation, int constraintIndex, SliderProp prop) {
		if (animation == null || prop == null) return null;
		for (Timeline timeline : animation.getTimelines()) {
			if (sliderPropOf(timeline) != prop) continue;
			if (((Animation.ConstraintTimeline)timeline).getConstraintIndex() == constraintIndex) return timeline;
		}
		return null;
	}

	public SliderProp sliderPropOf(Timeline timeline) {
		if (timeline instanceof SliderTimeline) return SliderProp.TIME;
		if (timeline instanceof SliderMixTimeline) return SliderProp.MIX;
		return null;
	}

	public float sliderValueAt(Timeline timeline, int frame) {
		if (sliderPropOf(timeline) == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("Slider 关键帧索引无效");
		return timeline.getFrames()[frame * timeline.getFrameEntries() + 1];
	}

	/** Finds one attachment-specific Sequence timeline. */
	public SequenceTimeline findSequenceTimeline(Animation animation, int slotIndex, Attachment attachment) {
		if (animation == null || attachment == null) return null;
		for (Timeline timeline : animation.getTimelines())
			if (timeline instanceof SequenceTimeline sequence && sequence.getSlotIndex() == slotIndex
				&& sequence.getAttachment() == attachment) return sequence;
		return null;
	}

	/** Whether attachment-specific timelines target this attachment rather than an inherited source. */
	public static boolean ownsAttachmentTimeline(Attachment attachment) {
		return attachment != null && attachment.getTimelineAttachment() == attachment;
	}

	public SequenceKeyframe sequenceAt(SequenceTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("Sequence 关键帧索引无效");
		int base = frame * timeline.getFrameEntries();
		float packedValue = timeline.getFrames()[base + 1];
		int packed = (int)packedValue;
		int mode = packed & 0xf;
		if (Float.compare(packedValue, packed) != 0 || packed < 0 || mode >= SequenceMode.values.length)
			throw new IllegalArgumentException("Sequence 关键帧模式或索引无效");
		return new SequenceKeyframe(SequenceMode.values[mode], packed >>> 4, timeline.getFrames()[base + 2]);
	}

	/** Finds one attachment-specific Deform timeline. */
	public DeformTimeline findDeformTimeline(Animation animation, int slotIndex, VertexAttachment attachment) {
		if (animation == null || attachment == null) return null;
		for (Timeline timeline : animation.getTimelines())
			if (timeline instanceof DeformTimeline deform && deform.getSlotIndex() == slotIndex
				&& deform.getAttachment() == attachment) return deform;
		return null;
	}

	public int deformLength(VertexAttachment attachment) {
		if (attachment == null || attachment.getVertices() == null)
			throw new IllegalArgumentException("Deform 附件顶点数据无效");
		return attachment.getBones() == null ? attachment.getVertices().length
			: attachment.getVertices().length / 3 * 2;
	}

	/** Returns offsets from setup geometry for both weighted and unweighted attachments. */
	public float[] deformOffsetsAt(DeformTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("Deform 关键帧索引无效");
		float[] stored = timeline.getVertices()[frame];
		VertexAttachment attachment = timeline.getAttachment();
		int length = deformLength(attachment);
		if (stored == null || stored.length != length) throw new IllegalStateException("Deform 顶点数据不完整");
		float[] offsets = stored.clone();
		if (attachment.getBones() == null) {
			float[] setup = attachment.getVertices();
			for (int index = 0; index < offsets.length; index++) offsets[index] -= setup[index];
		}
		return offsets;
	}

	/** Samples the live slot deform as setup-relative offsets, or returns an undeformed zero array. */
	public float[] currentDeformOffsets(int slotIndex, VertexAttachment attachment) {
		validateDisplayedDeformTarget(slotIndex, attachment);
		int length = deformLength(attachment);
		float[] offsets = new float[length];
		if (doc.skeleton == null || slotIndex >= doc.skeleton.getSlots().size) return offsets;
		Slot slot = doc.skeleton.getSlots().get(slotIndex);
		if (slot.getAppliedPose().getAttachment() == null
			|| slot.getAppliedPose().getAttachment().getTimelineAttachment() != attachment) return offsets;
		var deform = slot.getAppliedPose().getDeform();
		if (deform.size != length) return offsets;
		System.arraycopy(deform.items, 0, offsets, 0, length);
		if (attachment.getBones() == null) {
			float[] setup = attachment.getVertices();
			for (int index = 0; index < offsets.length; index++) offsets[index] -= setup[index];
		}
		return offsets;
	}

	/** Resolves a displayed attachment to the primary, JSON-addressable Deform timeline target. */
	public DeformTarget resolveDeformTarget(int displayedSlotIndex, VertexAttachment displayedAttachment) {
		if (doc.data == null || displayedAttachment == null)
			throw new IllegalArgumentException("Deform attachment is invalid");
		if (displayedSlotIndex < 0 || displayedSlotIndex >= doc.data.getSlots().size)
			throw new IllegalArgumentException("Deform slot is invalid");
		if (!attachmentOwnedBySlot(displayedSlotIndex, displayedAttachment, false))
			throw new IllegalArgumentException("Displayed Deform attachment is not available in the target slot");
		if (!(displayedAttachment.getTimelineAttachment() instanceof VertexAttachment target))
			throw new IllegalArgumentException("Deform timeline attachment is invalid");
		int targetSlotIndex = findAttachmentSlot(target);
		if (targetSlotIndex < 0)
			throw new IllegalArgumentException("Deform timeline attachment is not available in any slot");
		validateDeformGeometry(target);
		return new DeformTarget(targetSlotIndex, target);
	}

	/** Returns an independent slot-index permutation, or null for setup order. */
	public int[] drawOrderAt(DrawOrderTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("绘制顺序关键帧索引无效");
		int[] order = timeline.getDrawOrders()[frame];
		return order == null ? null : order.clone();
	}

	/** Returns a local folder permutation, whose values index the folder's setup-order slot array. */
	public int[] drawOrderFolderAt(DrawOrderFolderTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("局部绘制顺序关键帧索引无效");
		int[] order = timeline.getDrawOrders()[frame];
		return order == null ? null : order.clone();
	}

	public Event eventAt(EventTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("事件关键帧索引无效");
		Event event = timeline.getEvents()[frame];
		if (event == null) throw new IllegalStateException("事件关键帧数据为空");
		return event;
	}

	public String attachmentName(AttachmentTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("附件关键帧索引无效");
		return timeline.getAttachmentNames()[frame];
	}

	public BoneData.Inherit inheritAt(InheritTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("继承关键帧索引无效");
		float stored = timeline.getFrames()[frame * timeline.getFrameEntries() + 1];
		int ordinal = (int)stored;
		BoneData.Inherit[] values = BoneData.Inherit.values();
		if (!Float.isFinite(stored) || Float.compare(stored, ordinal) != 0 || ordinal < 0 || ordinal >= values.length)
			throw new IllegalStateException("继承关键帧数据无效");
		return values[ordinal];
	}

	public float colorValue(RGBATimeline timeline, int frame, int channel) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount() || channel < 0 || channel > 3)
			throw new IllegalArgumentException("颜色关键帧索引无效");
		return timeline.getFrames()[frame * timeline.getFrameEntries() + 1 + channel];
	}

	public float rgbValue(RGBTimeline timeline, int frame, int channel) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount() || channel < 0 || channel > 2)
			throw new IllegalArgumentException("RGB 关键帧索引无效");
		return timeline.getFrames()[frame * timeline.getFrameEntries() + 1 + channel];
	}

	public float alphaValue(AlphaTimeline timeline, int frame) {
		if (timeline == null || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("透明度关键帧索引无效");
		return timeline.getFrames()[frame * timeline.getFrameEntries() + 1];
	}

	/** Canonical channels are light RGBA followed by dark RGB. */
	public float twoColorValue(Timeline timeline, int frame, int channel) {
		if (!isTwoColorTimeline(timeline) || frame < 0 || frame >= timeline.getFrameCount()
			|| channel < 0 || channel > 6) throw new IllegalArgumentException("双色关键帧索引无效");
		int base = frame * timeline.getFrameEntries();
		if (timeline instanceof RGBA2Timeline) return timeline.getFrames()[base + 1 + channel];
		if (channel == 3) return 1f;
		return timeline.getFrames()[base + (channel < 3 ? 1 + channel : channel)];
	}

	// ================= 关键帧数值 =================

	public int frameCount(Animation.Timeline tl) {
		return tl == null ? 0 : tl.getFrameCount();
	}

	public float frameTime(Animation.Timeline tl, int frame) {
		float[] frames = tl.getFrames();
		return frames[frame * tl.getFrameEntries()];
	}

	/** 获取某帧的值（单值取 v1；双值取由属性决定） */
	public float frameValue(Animation.Timeline tl, int frame, int which) {
		float[] frames = tl.getFrames();
		int e = tl.getFrameEntries();
		int base = frame * e;
		if (tl instanceof BoneTimeline2) {
			// [time, v1, v2]
			return frames[base + 1 + which];
		}
		return frames[base + 1];
	}

	/** Returns one continuous channel value for Graph editing. */
	public float curveFrameValue(Animation.Timeline timeline, int frame, int channel) {
		if (!(timeline instanceof CurveTimeline))
			throw new IllegalArgumentException("该轨道没有可编辑的 Graph 数值");
		if (frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("Graph 关键帧索引无效");
		int channels = curveChannelCount(timeline);
		if (channel < 0 || channel >= channels)
			throw new IllegalArgumentException("Graph 通道索引无效");
		if (timeline instanceof DeformTimeline) return frame;
		return timeline.getFrames()[frame * timeline.getFrameEntries() + 1 + channel];
	}

	/** Moves one Graph key and edits one channel while preserving all outgoing curve shapes. */
	public Animation.Timeline editCurveKey(Animation animation, Animation.Timeline timeline, int frame,
		int channel, float time, float value) {
		if (animation == null || timeline == null || !animation.getTimelines().contains(timeline, true))
			throw new IllegalArgumentException("Graph 目标轨道无效");
		if (!(timeline instanceof CurveTimeline))
			throw new IllegalArgumentException("该轨道不支持 Graph 关键点编辑");
		requireCurveTimelineEditable(timeline);
		if (frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("Graph 关键帧索引无效");
		int channels = curveChannelCount(timeline);
		if (channel < 0 || channel >= channels)
			throw new IllegalArgumentException("Graph 通道索引无效");
		if (!Float.isFinite(time) || time < 0f || !Float.isFinite(value))
			throw new IllegalArgumentException("Graph 时间和数值必须是有限数，且时间不能为负");
		if (frame > 0 && time <= frameTime(timeline, frame - 1))
			throw new IllegalArgumentException("关键帧时间必须晚于前一帧");
		if (frame < timeline.getFrameCount() - 1
			&& time >= frameTime(timeline, frame + 1))
			throw new IllegalArgumentException("关键帧时间必须早于后一帧");
		if (sameFrameTime(time, frameTime(timeline, frame))
			&& nearlyEqual(value, curveFrameValue(timeline, frame, channel))) return timeline;
		if (timeline instanceof DeformTimeline deform)
			return editDeformGraphTimes(animation, deform, changedTime(deform, frame, time));

		validateCurveData(timeline);
		int count = timeline.getFrameCount();
		Animation.Timeline rebuilt = newTimelineLike(timeline, count, bezierCapacity(timeline));
		int[] sourceFrames = new int[count];
		int[] curveTypes = new int[count];
		for (int index = 0; index < count; index++) {
			copyEditableFrame(timeline, index, rebuilt, index);
			sourceFrames[index] = index;
			curveTypes[index] = sourceCurveType(timeline, index);
		}
		float[] targetFrames = rebuilt.getFrames();
		int targetBase = frame * rebuilt.getFrameEntries();
		targetFrames[targetBase] = time;
		targetFrames[targetBase + 1 + channel] = value;
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		if (timelinesEqual(timeline, rebuilt)) return timeline;
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return rebuilt;
	}

	/** Atomically updates all Graph frame times and channel values with one timeline replacement. */
	public Animation.Timeline editCurveKeys(Animation animation, Animation.Timeline timeline,
		float[] times, float[][] values) {
		if (animation == null || timeline == null || !animation.getTimelines().contains(timeline, true))
			throw new IllegalArgumentException("Graph 目标轨道无效");
		if (!(timeline instanceof CurveTimeline))
			throw new IllegalArgumentException("该轨道不支持 Graph 关键点编辑");
		requireCurveTimelineEditable(timeline);
		int count = timeline.getFrameCount(), channels = curveChannelCount(timeline);
		if (times == null || values == null || times.length != count || values.length != count)
			throw new IllegalArgumentException("Graph 批量编辑数据与轨道帧数不一致");
		for (int frame = 0; frame < count; frame++) {
			if (!Float.isFinite(times[frame]) || times[frame] < 0f || values[frame] == null
				|| values[frame].length != channels)
				throw new IllegalArgumentException("Graph 批量编辑数据无效");
			if (frame > 0 && times[frame] <= times[frame - 1])
				throw new IllegalArgumentException("Graph 关键帧时间必须严格递增");
			for (float value : values[frame])
				if (!Float.isFinite(value)) throw new IllegalArgumentException("Graph 数值必须是有限数");
		}
		if (timeline instanceof DeformTimeline deform) return editDeformGraphTimes(animation, deform, times);
		validateCurveData(timeline);
		Animation.Timeline rebuilt = newTimelineLike(timeline, count, bezierCapacity(timeline));
		int[] sourceFrames = new int[count], curveTypes = new int[count];
		for (int frame = 0; frame < count; frame++) {
			copyEditableFrame(timeline, frame, rebuilt, frame);
			sourceFrames[frame] = frame;
			curveTypes[frame] = sourceCurveType(timeline, frame);
			int base = frame * rebuilt.getFrameEntries();
			rebuilt.getFrames()[base] = times[frame];
			for (int channel = 0; channel < channels; channel++)
				rebuilt.getFrames()[base + channel + 1] = values[frame][channel];
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		if (timelinesEqual(timeline, rebuilt)) return timeline;
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return rebuilt;
	}

	/** Inserts one Graph key. The new outgoing segments start linear and all unrelated curves are preserved. */
	public Animation.Timeline insertCurveKey(Animation animation, Animation.Timeline timeline,
		float time, float[] values) {
		if (animation == null || timeline == null || !animation.getTimelines().contains(timeline, true)
			|| !(timeline instanceof CurveTimeline))
			throw new IllegalArgumentException("Graph 目标轨道无效");
		requireCurveTimelineEditable(timeline);
		int channels = curveChannelCount(timeline);
		if (!Float.isFinite(time) || time < 0f || values == null || values.length != channels)
			throw new IllegalArgumentException("Graph 新关键帧数据无效");
		for (float value : values)
			if (!Float.isFinite(value)) throw new IllegalArgumentException("Graph 数值必须是有限数");
		if (findFrame(timeline, time) >= 0) throw new IllegalArgumentException("目标时间已有关键帧");
		if (timeline instanceof DeformTimeline deform) return insertDeformGraphKey(animation, deform, time);
		int oldCount = timeline.getFrameCount(), insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		Animation.Timeline rebuilt = newTimelineLike(timeline, oldCount + 1, bezierCapacity(timeline));
		int[] sourceFrames = new int[oldCount + 1], curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < oldCount + 1; target++) {
			if (target == insert) {
				copyEditableFrame(timeline, Math.max(0, Math.min(oldCount - 1, insert - 1)), rebuilt, target);
				int base = target * rebuilt.getFrameEntries();
				rebuilt.getFrames()[base] = time;
				for (int channel = 0; channel < channels; channel++)
					rebuilt.getFrames()[base + channel + 1] = values[channel];
				curveTypes[target] = CurveTimeline.LINEAR;
			} else {
				copyEditableFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		if (insert > 0) curveTypes[insert - 1] = CurveTimeline.LINEAR;
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return rebuilt;
	}

	/** Removes selected Graph frames, removing the timeline when no keys remain. */
	public Animation.Timeline removeCurveKeys(Animation animation, Animation.Timeline timeline,
		boolean[] remove) {
		if (animation == null || timeline == null || !animation.getTimelines().contains(timeline, true)
			|| !(timeline instanceof CurveTimeline))
			throw new IllegalArgumentException("Graph 目标轨道无效");
		requireCurveTimelineEditable(timeline);
		if (remove == null || remove.length != timeline.getFrameCount())
			throw new IllegalArgumentException("Graph 删除选择无效");
		int keep = 0;
		for (boolean selected : remove) if (!selected) keep++;
		if (keep == timeline.getFrameCount()) return timeline;
		if (keep == 0) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return null;
		}
		Animation.Timeline rebuilt = newTimelineLike(timeline, keep, bezierCapacity(timeline));
		int[] sourceFrames = new int[keep], curveTypes = new int[keep];
		int target = 0;
		for (int source = 0; source < timeline.getFrameCount(); source++) {
			if (remove[source]) continue;
			copyEditableFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			target++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return rebuilt;
	}

	private static float[] changedTime(Animation.Timeline timeline, int frame, float time) {
		float[] times = new float[timeline.getFrameCount()];
		for (int index = 0; index < times.length; index++)
			times[index] = index == frame ? time : frameTimeStatic(timeline, index);
		return times;
	}

	private Animation.Timeline editDeformGraphTimes(Animation animation, DeformTimeline timeline,
		float[] times) {
		int count = timeline.getFrameCount();
		boolean changed = false;
		for (int frame = 0; frame < count; frame++)
			changed |= Float.compare(times[frame], frameTime(timeline, frame)) != 0;
		if (!changed) return timeline;

		DeformTimeline rebuilt = new DeformTimeline(count, bezierCapacity(timeline),
			timeline.getSlotIndex(), timeline.getAttachment());
		int[] sourceFrames = new int[count], curveTypes = new int[count];
		for (int frame = 0; frame < count; frame++) {
			rebuilt.setFrame(frame, times[frame], timeline.getVertices()[frame].clone());
			sourceFrames[frame] = frame;
			curveTypes[frame] = sourceCurveType(timeline, frame);
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return rebuilt;
	}

	private Animation.Timeline insertDeformGraphKey(Animation animation, DeformTimeline timeline,
		float time) {
		int oldCount = timeline.getFrameCount(), insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		float[] vertices = sampleStoredDeform(timeline, time);
		DeformTimeline rebuilt = new DeformTimeline(oldCount + 1, bezierCapacity(timeline),
			timeline.getSlotIndex(), timeline.getAttachment());
		int[] sourceFrames = new int[oldCount + 1], curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < oldCount + 1; target++) {
			if (target == insert) {
				rebuilt.setFrame(target, time, vertices);
				curveTypes[target] = CurveTimeline.LINEAR;
			} else {
				copyDeformFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		// The inserted point samples the old segment exactly. Its two adjacent segments start linear,
		// matching ordinary Graph insertion while leaving every unrelated outgoing curve untouched.
		if (insert > 0) curveTypes[insert - 1] = CurveTimeline.LINEAR;
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return rebuilt;
	}

	private static float[] sampleStoredDeform(DeformTimeline timeline, float time) {
		int count = timeline.getFrameCount();
		if (time <= frameTimeStatic(timeline, 0)) return timeline.getVertices()[0].clone();
		if (time >= frameTimeStatic(timeline, count - 1)) return timeline.getVertices()[count - 1].clone();
		int frame = 0;
		while (frame + 1 < count && frameTimeStatic(timeline, frame + 1) <= time) frame++;
		float percent = deformCurvePercent(timeline, frame, time);
		float[] before = timeline.getVertices()[frame], after = timeline.getVertices()[frame + 1];
		if (before == null || after == null || before.length != after.length)
			throw new IllegalStateException("Deform 顶点数据不完整");
		float[] result = new float[before.length];
		for (int index = 0; index < result.length; index++)
			result[index] = before[index] + (after[index] - before[index]) * percent;
		return result;
	}

	/** Mirrors Spine Runtime DeformTimeline.getCurvePercent for stored vertex interpolation. */
	private static float deformCurvePercent(DeformTimeline timeline, int frame, float time) {
		float[] times = timeline.getFrames();
		float[] curves = curveData(timeline);
		int type = (int)curves[frame];
		if (type == CurveTimeline.LINEAR) {
			float start = times[frame];
			return (time - start) / (times[frame + 1] - start);
		}
		if (type == CurveTimeline.STEPPED) return 0f;

		int index = type - CurveTimeline.BEZIER;
		if (index < timeline.getFrameCount() || index + CurveTimeline.BEZIER_SIZE > curves.length)
			throw new IllegalStateException("Spine Deform 贝塞尔曲线数据不完整");
		if (curves[index] > time) {
			float start = times[frame];
			return curves[index + 1] * (time - start) / (curves[index] - start);
		}
		int end = index + CurveTimeline.BEZIER_SIZE;
		for (index += 2; index < end; index += 2) {
			if (curves[index] >= time) {
				float x = curves[index - 2], y = curves[index - 1];
				return y + (time - x) / (curves[index] - x) * (curves[index + 1] - y);
			}
		}
		float x = curves[end - 2], y = curves[end - 1];
		return y + (1f - y) * (time - x) / (times[frame + 1] - x);
	}

	/** Finds a frame by time, or returns -1 when no frame matches. */
	public int findFrame(Animation.Timeline timeline, float time) {
		if (timeline == null || !Float.isFinite(time)) return -1;
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			if (sameFrameTime(frameTime(timeline, frame), time)) return frame;
		}
		return -1;
	}

	/** 设置某帧值（不改变帧数） */
	public void setFrame(Animation.Timeline tl, int frame, float time, float v1, float v2) {
		if (tl instanceof BoneTimeline2) {
			((BoneTimeline2) tl).setFrame(frame, time, v1, v2);
		} else {
			((CurveTimeline1) tl).setFrame(frame, time, v1);
		}
	}

	/** 曲线类型：0 linear, 1 stepped */
	public int curveType(Animation.Timeline tl, int frame) {
		if (tl instanceof CurveTimeline) return ((CurveTimeline) tl).getCurveType(frame);
		return 0;
	}

	public void setCurve(Animation.Timeline tl, int frame, boolean stepped) {
		if (tl instanceof CurveTimeline && frame >= 0 && frame < tl.getFrameCount() - 1) {
			requireCurveTimelineEditable(tl);
			int before = ((CurveTimeline)tl).getCurveType(frame);
			if (stepped) ((CurveTimeline) tl).setStepped(frame);
			else ((CurveTimeline) tl).setLinear(frame);
			int after = ((CurveTimeline)tl).getCurveType(frame);
			if (before != after) doc.markDirty();
		}
	}

	/** Applies Linear or Stepped to selected Graph channels without changing unselected channel curves. */
	public Animation.Timeline setCurveModeChannels(Animation animation, Animation.Timeline timeline,
		int[] frames, int[] channels, boolean stepped) {
		if (animation == null || timeline == null || frames == null || channels == null
			|| frames.length != channels.length || !animation.getTimelines().contains(timeline, true))
			throw new IllegalArgumentException("Graph 曲线选择无效");
		if (!(timeline instanceof CurveTimeline curve))
			throw new IllegalArgumentException("当前轨道不支持曲线");
		requireCurveTimelineEditable(timeline);
		int channelCount = curveChannelCount(timeline);
		boolean[][] selected = new boolean[timeline.getFrameCount()][channelCount];
		for (int index = 0; index < frames.length; index++) {
			int frame = frames[index], channel = channels[index];
			if (frame < 0 || frame >= timeline.getFrameCount() - 1
				|| channel < 0 || channel >= channelCount)
				throw new IllegalArgumentException("Graph 曲线选择包含无效关键点");
			selected[frame][channel] = true;
		}

		boolean needsAxisSeparation = false;
		for (int frame = 0; frame < selected.length - 1; frame++) {
			int selectedCount = selectedChannelCount(selected[frame]);
			if (selectedCount == 0 || selectedCount == channelCount) continue;
			if (stepped || curve.getCurveType(frame) == CurveTimeline.STEPPED)
				needsAxisSeparation = true;
		}

		if (needsAxisSeparation) {
			AxisGroup group = combinedAxisGroup(timeline);
			if (group == null || channelCount != 2 || !(timeline instanceof BoneTimeline bone))
				throw new IllegalStateException("该组合轨道的 Stepped 是所有通道共享的，无法只修改一个通道");
			int primaryChannel = 0;
			for (int index = 0; index < channels.length; index++) {
				if (frames[index] < timeline.getFrameCount() - 1) {
					primaryChannel = channels[index];
					break;
				}
			}
			int boneIndex = bone.getBoneIndex();
			separateAxisTimeline(animation, group, boneIndex);
			Prop[] props = axisProps(group);
			Animation.Timeline[] axes = {
				findTimeline(animation, props[1], boneIndex),
				findTimeline(animation, props[2], boneIndex)
			};
			if (axes[0] == null || axes[1] == null)
				throw new IllegalStateException("组合轨道自动分离失败");
			for (int frame = 0; frame < selected.length - 1; frame++) {
				for (int channel = 0; channel < 2; channel++) {
					if (selected[frame][channel]) setCurve(axes[channel], frame, stepped);
				}
			}
			return axes[Math.max(0, Math.min(primaryChannel, 1))];
		}

		Animation.Timeline live = timeline;
		for (int frame = 0; frame < selected.length - 1; frame++) {
			int selectedCount = selectedChannelCount(selected[frame]);
			if (selectedCount == 0) continue;
			if (selectedCount == channelCount) {
				setCurve(live, frame, stepped);
				continue;
			}
			CurveTimeline liveCurve = (CurveTimeline)live;
			if (liveCurve.getCurveType(frame) < CurveTimeline.BEZIER) continue;
			for (int channel = 0; channel < channelCount; channel++) {
				if (!selected[frame][channel]) continue;
				live = setBezierCurveChannel(animation, live, frame, channel,
					1f / 3f, 1f / 3f, 2f / 3f, 2f / 3f);
			}
		}
		return live;
	}

	private AxisGroup combinedAxisGroup(Animation.Timeline timeline) {
		Prop prop = propOf(timeline);
		return prop == Prop.TRANSLATE ? AxisGroup.TRANSLATE
			: prop == Prop.SCALE ? AxisGroup.SCALE
			: prop == Prop.SHEAR ? AxisGroup.SHEAR : null;
	}

	private static int selectedChannelCount(boolean[] selected) {
		int count = 0;
		for (boolean value : selected) if (value) count++;
		return count;
	}

	public boolean hasBezier(Animation.Timeline timeline) {
		if (!(timeline instanceof CurveTimeline curve)) return false;
		for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
			if (curve.getCurveType(frame) >= CurveTimeline.BEZIER) return true;
		}
		return false;
	}

	/** Immutable outgoing interpolation data for copying a key between compatible tracks. */
	public static final class CurveSnapshot {
		private final int frameEntries;
		private final int channelCount;
		private final int type;
		private final float[] startValues;
		private final float[] endValues;
		private final float[] samples;

		private CurveSnapshot(int frameEntries, int channelCount, int type, float[] startValues, float[] endValues,
			float[] samples) {
			this.frameEntries = frameEntries;
			this.channelCount = channelCount;
			this.type = type;
			this.startValues = startValues;
			this.endValues = endValues;
			this.samples = samples;
		}
	}

	/** Normalized cubic Bezier handles where both key endpoints are (0,0) and (1,1). */
	public static final class BezierCurve {
		private final float cx1, cy1, cx2, cy2;

		private BezierCurve(float cx1, float cy1, float cx2, float cy2) {
			this.cx1 = cx1;
			this.cy1 = cy1;
			this.cx2 = cx2;
			this.cy2 = cy2;
		}

		public float cx1() { return cx1; }
		public float cy1() { return cy1; }
		public float cx2() { return cx2; }
		public float cy2() { return cy2; }
	}

	/** Graph handles use normalized time and absolute values, including curves with equal endpoint values. */
	public record BezierHandles(float cx1, float value1, float cx2, float value2) {}

	public CurveSnapshot captureCurve(Animation.Timeline timeline, int frame) {
		if (!(timeline instanceof CurveTimeline curve) || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("无法复制无效的关键帧曲线");
		int entries = timeline.getFrameEntries();
		int channels = curveChannelCount(timeline);
		int type = sourceCurveType(timeline, frame);
		if (type < CurveTimeline.BEZIER)
			return new CurveSnapshot(entries, channels, type, null, null, null);

		validateCurveData(timeline);
		int firstBlock = type - CurveTimeline.BEZIER;
		int sampleCount = channels * CurveTimeline.BEZIER_SIZE;
		float[] frames = timeline.getFrames();
		int start = frame * entries;
		if (timeline instanceof DeformTimeline)
			return new CurveSnapshot(entries, channels, type, new float[] {frames[start], 0f},
				new float[] {frames[start + entries], 1f},
				java.util.Arrays.copyOfRange(curveData(curve), firstBlock, firstBlock + sampleCount));
		return new CurveSnapshot(entries, channels, type,
			java.util.Arrays.copyOfRange(frames, start, start + channels + 1),
			java.util.Arrays.copyOfRange(frames, start + entries, start + entries + channels + 1),
			java.util.Arrays.copyOfRange(curveData(curve), firstBlock, firstBlock + sampleCount));
	}

	/** Returns one channel's normalized cubic handles, or null when the segment is not Bezier. */
	public BezierCurve bezierCurve(Animation.Timeline timeline, int frame, int channel) {
		BezierHandles handles = bezierHandles(timeline, frame, channel);
		if (handles == null) return null;
		float value1 = curveFrameValue(timeline, frame, channel);
		float value2 = curveFrameValue(timeline, frame + 1, channel);
		float delta = value2 - value1;
		return new BezierCurve(handles.cx1(), delta == 0f ? 0f : (handles.value1() - value1) / delta,
			handles.cx2(), delta == 0f ? 1f : (handles.value2() - value1) / delta);
	}

	/** Returns one channel's handles without dividing values by the endpoint delta. */
	public BezierHandles bezierHandles(Animation.Timeline timeline, int frame, int channel) {
		if (!(timeline instanceof CurveTimeline curve) || frame < 0 || frame >= timeline.getFrameCount() - 1)
			throw new IllegalArgumentException("关键帧没有可编辑的贝塞尔曲线");
		int channels = curveChannelCount(timeline);
		if (channel < 0 || channel >= channels) throw new IllegalArgumentException("贝塞尔曲线通道无效");
		int type = curve.getCurveType(frame);
		if (type < CurveTimeline.BEZIER) return null;

		validateCurveData(timeline);
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		int firstFrame = frame * entries;
		int nextFrame = firstFrame + entries;
		float time1 = frames[firstFrame], time2 = frames[nextFrame];
		float duration = time2 - time1;
		if (!(duration > 0f) || !Float.isFinite(duration))
			throw new IllegalStateException("贝塞尔曲线的关键帧时间必须递增");

		int block = type - CurveTimeline.BEZIER + channel * CurveTimeline.BEZIER_SIZE;
		float[] curves = curveData(curve);
		float[] x = solveBezierControls(time1, time2, curves, block);
		float value1 = timeline instanceof DeformTimeline ? 0f : frames[firstFrame + channel + 1];
		float value2 = timeline instanceof DeformTimeline ? 1f : frames[nextFrame + channel + 1];
		float[] y = solveBezierControls(value1, value2, curves, block + 1);
		float graphOffset = timeline instanceof DeformTimeline ? frame : 0f;
		return new BezierHandles(clampUnit((x[0] - time1) / duration), y[0] + graphOffset,
			clampUnit((x[1] - time1) / duration), y[1] + graphOffset);
	}

	/** Replaces one key's outgoing interpolation with the same normalized Bezier shape on every channel. */
	public Animation.Timeline setBezierCurve(Animation animation, Animation.Timeline timeline, int frame,
		float cx1, float cy1, float cx2, float cy2) {
		return setBezierCurve(animation, timeline, frame, -1, cx1, cy1, cx2, cy2);
	}

	/** Replaces one channel while preserving every other channel's outgoing Bezier samples. */
	public Animation.Timeline setBezierCurveChannel(Animation animation, Animation.Timeline timeline, int frame,
		int channel, float cx1, float cy1, float cx2, float cy2) {
		return setBezierCurve(animation, timeline, frame, channel, cx1, cy1, cx2, cy2);
	}

	private Animation.Timeline setBezierCurve(Animation animation, Animation.Timeline timeline, int frame,
		int editedChannel, float cx1, float cy1, float cx2, float cy2) {
		return setBezierCurve(animation, timeline, frame, editedChannel, cx1, cy1, cx2, cy2, false);
	}

	/** Edits Graph handles with normalized time and absolute values, preserving all unedited channels. */
	public Animation.Timeline setBezierHandlesChannel(Animation animation, Animation.Timeline timeline, int frame,
		int channel, float cx1, float value1, float cx2, float value2) {
		if (channel < 0) throw new IllegalArgumentException("贝塞尔曲线通道无效");
		return setBezierCurve(animation, timeline, frame, channel, cx1, value1, cx2, value2, true);
	}

	private Animation.Timeline setBezierCurve(Animation animation, Animation.Timeline timeline, int frame,
		int editedChannel, float cx1, float cy1, float cx2, float cy2, boolean absoluteValues) {
		if (animation == null || timeline == null || !animation.getTimelines().contains(timeline, true))
			throw new IllegalArgumentException("目标关键帧曲线无效");
		if (!(timeline instanceof CurveTimeline sourceCurve))
			throw new IllegalArgumentException("目标轨道不支持贝塞尔曲线");
		requireCurveTimelineEditable(timeline);
		if (frame < 0 || frame >= timeline.getFrameCount() - 1)
			throw new IllegalArgumentException("末帧没有可编辑的插值曲线");
		validateBezierControls(cx1, cy1, cx2, cy2);
		validateCurveData(timeline);

		int channels = curveChannelCount(timeline);
		if (editedChannel < -1 || editedChannel >= channels)
			throw new IllegalArgumentException("贝塞尔曲线通道无效");
		if (sourceCurve.getCurveType(frame) >= CurveTimeline.BEZIER) {
			boolean unchanged = editedChannel >= 0;
			int firstChannel = editedChannel >= 0 ? editedChannel : 0;
			int lastChannel = editedChannel >= 0 ? editedChannel + 1 : channels;
			for (int channel = firstChannel; channel < lastChannel; channel++) {
				BezierHandles current = bezierHandles(timeline, frame, channel);
				float value1 = curveFrameValue(timeline, frame, channel);
				float delta = curveFrameValue(timeline, frame + 1, channel) - value1;
				float controlValue1 = absoluteValues ? cy1 : value1 + delta * cy1;
				float controlValue2 = absoluteValues ? cy2 : value1 + delta * cy2;
				boolean channelUnchanged = nearlyEqual(current.cx1(), cx1) && nearlyEqual(current.value1(), controlValue1)
					&& nearlyEqual(current.cx2(), cx2) && nearlyEqual(current.value2(), controlValue2);
				unchanged = editedChannel >= 0 ? channelUnchanged : channel == firstChannel
					? channelUnchanged : unchanged && channelUnchanged;
			}
			if (unchanged) return timeline;
		}

		int bezierCount = channels;
		for (int index = 0; index < timeline.getFrameCount() - 1; index++) {
			if (index != frame && sourceCurve.getCurveType(index) >= CurveTimeline.BEZIER) bezierCount += channels;
		}
		Animation.Timeline rebuilt = newTimelineLike(timeline, timeline.getFrameCount(), bezierCount);
		for (int index = 0; index < timeline.getFrameCount(); index++)
			copyEditableFrame(timeline, index, rebuilt, index);

		CurveTimeline targetCurve = (CurveTimeline)rebuilt;
		float[] sourceData = curveData(sourceCurve);
		float[] targetData = curveData(targetCurve);
		float[] targetFrames = rebuilt.getFrames();
		int entries = rebuilt.getFrameEntries();
		int targetBezier = 0;
		for (int index = 0; index < rebuilt.getFrameCount() - 1; index++) {
			if (index == frame) {
				int firstFrame = index * entries;
				int nextFrame = firstFrame + entries;
				float time1 = targetFrames[firstFrame], time2 = targetFrames[nextFrame];
				int sourceType = sourceCurve.getCurveType(index);
				int targetBlock = rebuilt.getFrameCount() + targetBezier * CurveTimeline.BEZIER_SIZE;
				targetData[index] = CurveTimeline.BEZIER + targetBlock;
				for (int channel = 0; channel < channels; channel++) {
					if (editedChannel >= 0 && channel != editedChannel && sourceType >= CurveTimeline.BEZIER) {
						// A channel may return to its starting value while its handles still describe an excursion.
						// Normalizing by the endpoint delta cannot preserve that shape (or arbitrary runtime samples).
						System.arraycopy(sourceData, sourceType - CurveTimeline.BEZIER + channel * CurveTimeline.BEZIER_SIZE,
							targetData, targetBlock + channel * CurveTimeline.BEZIER_SIZE, CurveTimeline.BEZIER_SIZE);
						continue;
					}
					BezierCurve handles = editedChannel < 0 || channel == editedChannel
						? new BezierCurve(cx1, cy1, cx2, cy2)
						: new BezierCurve(1f / 3f, 1f / 3f, 2f / 3f, 2f / 3f);
					float controlTime1 = time1 + (time2 - time1) * handles.cx1;
					float controlTime2 = time1 + (time2 - time1) * handles.cx2;
					float value1 = timeline instanceof DeformTimeline ? 0f : targetFrames[firstFrame + channel + 1];
					float value2 = timeline instanceof DeformTimeline ? 1f : targetFrames[nextFrame + channel + 1];
					boolean editedAbsolute = absoluteValues && (editedChannel < 0 || channel == editedChannel);
					float graphOffset = timeline instanceof DeformTimeline ? index : 0f;
					targetCurve.setBezier(targetBezier + channel, index, channel, time1, value1,
						controlTime1, editedAbsolute ? handles.cy1 - graphOffset : value1 + (value2 - value1) * handles.cy1,
						controlTime2, editedAbsolute ? handles.cy2 - graphOffset : value1 + (value2 - value1) * handles.cy2,
						time2, value2);
				}
				targetBezier += channels;
				continue;
			}

			int type = sourceCurve.getCurveType(index);
			if (type == CurveTimeline.STEPPED) {
				targetCurve.setStepped(index);
			} else if (type < CurveTimeline.BEZIER) {
				targetCurve.setLinear(index);
			} else {
				int sourceBlock = type - CurveTimeline.BEZIER;
				int targetBlock = rebuilt.getFrameCount() + targetBezier * CurveTimeline.BEZIER_SIZE;
				targetData[index] = CurveTimeline.BEZIER + targetBlock;
				System.arraycopy(sourceData, sourceBlock, targetData, targetBlock,
					channels * CurveTimeline.BEZIER_SIZE);
				targetBezier += channels;
			}
		}
		targetCurve.shrink(targetBezier);
		if (timelinesEqual(timeline, rebuilt)) return timeline;
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return rebuilt;
	}

	private static void validateBezierControls(float cx1, float cy1, float cx2, float cy2) {
		if (!Float.isFinite(cx1) || !Float.isFinite(cy1) || !Float.isFinite(cx2) || !Float.isFinite(cy2))
			throw new IllegalArgumentException("贝塞尔控制点必须是有限数值");
		if (cx1 < 0f || cx1 > 1f || cx2 < 0f || cx2 > 1f)
			throw new IllegalArgumentException("贝塞尔 X 控制点必须在 0 到 1 之间");
	}

	private static float[] solveBezierControls(float start, float end, float[] samples, int offset) {
		double aa = 0d, ab = 0d, bb = 0d, ar = 0d, br = 0d;
		for (int index = 0; index < CurveTimeline.BEZIER_SIZE / 2; index++) {
			double t = (index + 1) / 10d, inverse = 1d - t;
			double a = 3d * inverse * inverse * t;
			double b = 3d * inverse * t * t;
			double remainder = samples[offset + index * 2]
				- inverse * inverse * inverse * start - t * t * t * end;
			aa += a * a;
			ab += a * b;
			bb += b * b;
			ar += a * remainder;
			br += b * remainder;
		}
		double determinant = aa * bb - ab * ab;
		float first = (float)((ar * bb - br * ab) / determinant);
		float second = (float)((br * aa - ar * ab) / determinant);
		if (!Float.isFinite(first) || !Float.isFinite(second))
			throw new IllegalStateException("贝塞尔控制点不是有限数值");
		return new float[] {first, second};
	}

	private static float clampUnit(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	private static boolean nearlyEqual(float left, float right) {
		return Math.abs(left - right) <= 0.00001f;
	}

	private static boolean sameFrameTime(float left, float right) {
		return Float.compare(left, right) == 0;
	}

	/** Applies copied interpolation to a key and returns the live timeline (which may be rebuilt). */
	public Animation.Timeline applyCurveSnapshot(Animation animation, Animation.Timeline timeline,
		int frame, CurveSnapshot snapshot) {
		if (animation == null || timeline == null || snapshot == null
			|| !animation.getTimelines().contains(timeline, true))
			throw new IllegalArgumentException("目标关键帧曲线无效");
		if (!(timeline instanceof CurveTimeline curve) || frame < 0 || frame >= timeline.getFrameCount())
			throw new IllegalArgumentException("目标关键帧不支持曲线");
		requireCurveTimelineEditable(timeline);
		int channels = curveChannelCount(timeline);
		if (timeline.getFrameEntries() != snapshot.frameEntries || channels != snapshot.channelCount)
			throw new IllegalArgumentException("复制的曲线通道与目标轨道不兼容");
		if (frame >= timeline.getFrameCount() - 1) return timeline;
		if (snapshot.type < CurveTimeline.BEZIER) {
			setCurve(timeline, frame, snapshot.type == CurveTimeline.STEPPED);
			return timeline;
		}

		validateCurveData(timeline);
		if (snapshot.startValues == null || snapshot.endValues == null || snapshot.samples == null
			|| snapshot.startValues.length != channels + 1 || snapshot.endValues.length != channels + 1
			|| snapshot.samples.length != channels * CurveTimeline.BEZIER_SIZE)
			throw new IllegalArgumentException("复制的贝塞尔曲线数据无效");
		Animation.Timeline rebuilt = newTimelineLike(timeline, timeline.getFrameCount(),
			bezierCapacity(timeline) + channels);
		for (int index = 0; index < timeline.getFrameCount(); index++)
			copyEditableFrame(timeline, index, rebuilt, index);

		float[] sourceCurves = curveData(curve);
		float[] targetCurves = curveData((CurveTimeline)rebuilt);
		System.arraycopy(sourceCurves, 0, targetCurves, 0, sourceCurves.length);
		int targetBlock = sourceCurves.length;
		targetCurves[frame] = CurveTimeline.BEZIER + targetBlock;
		int entries = timeline.getFrameEntries();
		int targetStart = frame * entries;
		int targetEnd = targetStart + entries;
		float[] targetFrames = timeline.getFrames();
		double sourceDuration = (double)snapshot.endValues[0] - snapshot.startValues[0];
		double targetDuration = (double)targetFrames[targetEnd] - targetFrames[targetStart];
		if (!(sourceDuration > 0d) || !(targetDuration > 0d)
			|| !Double.isFinite(sourceDuration) || !Double.isFinite(targetDuration))
			throw new IllegalArgumentException("复制曲线的关键帧时间必须严格递增");
		for (int channel = 0; channel < channels; channel++) {
			int sourceBlock = channel * CurveTimeline.BEZIER_SIZE;
			int destinationBlock = targetBlock + channel * CurveTimeline.BEZIER_SIZE;
			float sourceValue1 = snapshot.startValues[channel + 1];
			float sourceValue2 = snapshot.endValues[channel + 1];
			float targetValue1 = timeline instanceof DeformTimeline ? 0f : targetFrames[targetStart + channel + 1];
			float targetValue2 = timeline instanceof DeformTimeline ? 1f : targetFrames[targetEnd + channel + 1];
			double sourceDelta = (double)sourceValue2 - sourceValue1;
			double targetDelta = (double)targetValue2 - targetValue1;
			for (int point = 0; point < CurveTimeline.BEZIER_SIZE / 2; point++) {
				double u = (point + 1) * 0.1d;
				int offset = point * 2;
				double normalizedTime = (snapshot.samples[sourceBlock + offset] - snapshot.startValues[0])
					/ sourceDuration;
				targetCurves[destinationBlock + offset] = finiteCurveSample(
					targetFrames[targetStart] + normalizedTime * targetDuration);
				double mappedValue;
				if (sourceDelta == targetDelta) {
					// Identical endpoint deltas need only a value offset. In particular, equal endpoints
					// do not imply a flat Bezier: preserve any returning motion between those keys.
					mappedValue = targetValue1 + ((double)snapshot.samples[sourceBlock + offset + 1] - sourceValue1);
				} else if (targetDelta == 0d) {
					mappedValue = targetValue1;
				} else if (sourceDelta != 0d) {
					double normalizedValue = (snapshot.samples[sourceBlock + offset + 1] - sourceValue1)
						/ sourceDelta;
					mappedValue = targetValue1 + normalizedValue * targetDelta;
				} else {
					// A flat source has no value scale. Keep its X timing and use the existing 0/1 handle convention.
					double normalizedValue = u * u * (3d - 2d * u);
					mappedValue = targetValue1 + normalizedValue * targetDelta;
				}
				targetCurves[destinationBlock + offset + 1] = finiteCurveSample(mappedValue);
			}
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return rebuilt;
	}

	private static float finiteCurveSample(double value) {
		float sample = (float)value;
		if (!Double.isFinite(value) || !Float.isFinite(sample))
			throw new IllegalArgumentException("复制曲线生成了无效的贝塞尔采样点");
		return sample;
	}

	/** Converts a live local pose to the values stored by Spine bone timelines. */
	public float[] valuesForPose(Bone bone, Prop prop) {
		if (bone == null) return new float[] {0f, 0f};
		var pose = bone.getPose();
		var setup = bone.getData().getSetupPose();
		return switch (prop) {
		case ROTATE -> new float[] {pose.getRotation() - setup.getRotation(), 0f};
		case TRANSLATE_X -> new float[] {pose.getX() - setup.getX(), 0f};
		case TRANSLATE_Y -> new float[] {pose.getY() - setup.getY(), 0f};
		case TRANSLATE -> new float[] {pose.getX() - setup.getX(), pose.getY() - setup.getY()};
		case SCALE_X -> new float[] {scaleRatio(pose.getScaleX(), setup.getScaleX()), 0f};
		case SCALE_Y -> new float[] {scaleRatio(pose.getScaleY(), setup.getScaleY()), 0f};
		case SCALE -> new float[] {
			scaleRatio(pose.getScaleX(), setup.getScaleX()),
			scaleRatio(pose.getScaleY(), setup.getScaleY())
		};
		case SHEAR_X -> new float[] {pose.getShearX() - setup.getShearX(), 0f};
		case SHEAR_Y -> new float[] {pose.getShearY() - setup.getShearY(), 0f};
		case SHEAR -> new float[] {
			pose.getShearX() - setup.getShearX(),
			pose.getShearY() - setup.getShearY()
		};
		};
	}

	/** Returns the non-overlapping timeline properties needed to key a complete local bone pose. */
	public Array<Prop> poseKeyProps(Animation animation, int boneIndex) {
		return poseKeyProps(animation, boneIndex, false, false, false);
	}

	public Array<Prop> poseKeyProps(Animation animation, int boneIndex,
		boolean separateTranslate, boolean separateShear) {
		return poseKeyProps(animation, boneIndex, separateTranslate, false, separateShear);
	}

	public Array<Prop> poseKeyProps(Animation animation, int boneIndex,
		boolean separateTranslate, boolean separateScale, boolean separateShear) {
		Array<Prop> props = new Array<>();
		if (animation == null || doc.data == null || boneIndex < 0 || boneIndex >= doc.data.getBones().size) return props;
		props.add(Prop.ROTATE);
		addPoseAxisProps(props, animation, boneIndex, Prop.TRANSLATE, Prop.TRANSLATE_X, Prop.TRANSLATE_Y,
			separateTranslate);
		addPoseAxisProps(props, animation, boneIndex, Prop.SCALE, Prop.SCALE_X, Prop.SCALE_Y, separateScale);
		addPoseAxisProps(props, animation, boneIndex, Prop.SHEAR, Prop.SHEAR_X, Prop.SHEAR_Y,
			separateShear);
		return props;
	}

	/** Returns the storage timelines affected by one completed transform edit. */
	public Array<Prop> transformKeyProps(Animation animation, int boneIndex, Prop editedProp,
		boolean separateTranslate, boolean separateScale, boolean separateShear) {
		Array<Prop> props = new Array<>();
		if (editedProp == null) return props;
		Array<Prop> poseProps = poseKeyProps(animation, boneIndex,
			separateTranslate, separateScale, separateShear);
		if (editedProp == Prop.ROTATE) {
			if (poseProps.contains(Prop.ROTATE, true)) props.add(Prop.ROTATE);
			return props;
		}

		Prop[] group = axisProps(axisGroup(editedProp));
		boolean editsBothAxes = editedProp == group[0];
		for (Prop candidate : poseProps) {
			if (candidate != group[0] && candidate != group[1] && candidate != group[2]) continue;
			if (candidate == group[0] || editsBothAxes || candidate == editedProp) props.add(candidate);
		}
		return props;
	}

	private static AxisGroup axisGroup(Prop prop) {
		return switch (prop) {
		case TRANSLATE, TRANSLATE_X, TRANSLATE_Y -> AxisGroup.TRANSLATE;
		case SCALE, SCALE_X, SCALE_Y -> AxisGroup.SCALE;
		case SHEAR, SHEAR_X, SHEAR_Y -> AxisGroup.SHEAR;
		case ROTATE -> throw new IllegalArgumentException("旋转不属于 XY 轨道组");
		};
	}

	private void addPoseAxisProps(Array<Prop> props, Animation animation, int boneIndex,
		Prop combined, Prop xAxis, Prop yAxis, boolean preferSeparated) {
		boolean hasCombined = findTimeline(animation, combined, boneIndex) != null;
		boolean hasX = findTimeline(animation, xAxis, boneIndex) != null;
		boolean hasY = findTimeline(animation, yAxis, boneIndex) != null;
		if (hasCombined || (!hasX && !hasY && !preferSeparated)) props.add(combined);
		if (!hasCombined && !hasX && !hasY && preferSeparated) {
			props.add(xAxis);
			props.add(yAxis);
		}
		if (!hasCombined && (hasX || hasY)) {
			props.add(xAxis);
			props.add(yAxis);
		} else if (hasCombined) {
			if (hasX) props.add(xAxis);
			if (hasY) props.add(yAxis);
		}
	}

	/** Keys rotation, translation, scale, and shear from the current local pose as one atomic model operation. */
	public void keyBonePose(Animation animation, Bone bone, float time) {
		keyBonePose(animation, bone, time, false, false, false);
	}

	public void keyBonePose(Animation animation, Bone bone, float time,
		boolean separateTranslate, boolean separateShear) {
		keyBonePose(animation, bone, time, separateTranslate, false, separateShear);
	}

	public void keyBonePose(Animation animation, Bone bone, float time,
		boolean separateTranslate, boolean separateScale, boolean separateShear) {
		keyBoneProps(animation, bone, time, poseKeyProps(animation,
			bone == null ? -1 : bone.getData().getIndex(), separateTranslate, separateScale, separateShear));
	}

	/** Keys only the channel or axis group changed by one viewport or inspector edit. */
	public void keyBoneTransform(Animation animation, Bone bone, float time, Prop editedProp,
		boolean separateTranslate, boolean separateScale, boolean separateShear) {
		if (editedProp == null) throw new IllegalArgumentException("自动关键帧属性不能为空");
		Array<Prop> props = transformKeyProps(animation, bone == null ? -1 : bone.getData().getIndex(), editedProp,
			separateTranslate, separateScale, separateShear);
		keyBoneProps(animation, bone, time, props);
	}

	private void keyBoneProps(Animation animation, Bone bone, float time, Array<Prop> props) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画无效");
		if (bone == null || doc.skeleton == null || !doc.skeleton.getBones().contains(bone, true))
			throw new IllegalArgumentException("骨骼无效");
		if (!Float.isFinite(time) || time < 0f) throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
		if (props == null || props.isEmpty()) throw new IllegalArgumentException("没有可记录的骨骼变换通道");

		int boneIndex = bone.getData().getIndex();
		float[][] values = new float[props.size][];
		for (int index = 0; index < props.size; index++) {
			Prop prop = props.get(index);
			validateCurveData(findTimeline(animation, prop, boneIndex));
			values[index] = valuesForPose(bone, prop);
			requireValidKeyframe(prop, time, values[index][0], values[index][1]);
		}

		for (int index = 0; index < props.size; index++) {
			Prop prop = props.get(index);
			Animation.Timeline timeline = ensureTimeline(animation, prop, boneIndex);
			if (timeline == null) throw new IllegalStateException("无法创建 " + prop + " 轨道");
			addKeyframe(animation, prop, boneIndex, time, values[index][0], values[index][1]);
		}
	}

	private static float scaleRatio(float value, float setup) {
		return Math.abs(setup) < 0.000001f ? 1f : value / setup;
	}

	// ================= 关键帧增删（重建 timeline） =================

	/**
	 * 在指定动画中为骨骼属性插入一个时间线（若同属性已存在则返回已有）。
	 * 返回 timeline（新建或既有）。
	 */
	public Animation.Timeline ensureTimeline(Animation anim, Prop prop, int boneIndex) {
		if (anim == null || doc.data == null || boneIndex < 0 || boneIndex >= doc.data.getBones().size) return null;
		Animation.Timeline existing = findTimeline(anim, prop, boneIndex);
		if (existing != null) return existing;
		Array<Animation.Timeline> list = anim.getTimelines();
		Animation.Timeline created = newTimeline(prop, boneIndex, 1);
		if (created == null) return null;
		// 初始帧: time=0, 值=当前 setup pose
		setTimelineInitial(created, boneIndex);
		list.add(created);
		refreshAnimation(anim);
		doc.markDirty();
		return created;
	}

	private void setTimelineInitial(Animation.Timeline created, int boneIndex) {
		if (created instanceof CurveTimeline1) {
			CurveTimeline1 c1 = (CurveTimeline1) created;
			Prop prop = propOf(created);
			c1.setFrame(0, 0, prop == Prop.SCALE_X || prop == Prop.SCALE_Y ? 1f : 0f);
		} else if (created instanceof BoneTimeline2) {
			BoneTimeline2 c2 = (BoneTimeline2) created;
			if (created instanceof TranslateTimeline || created instanceof ShearTimeline)
				c2.setFrame(0, 0, 0f, 0f);
			else if (created instanceof ScaleTimeline) c2.setFrame(0, 0, 1f, 1f);
		}
	}

	private Animation.Timeline newTimeline(Prop prop, int boneIndex, int frames) {
		return newTimeline(prop, boneIndex, frames, 0);
	}

	private Animation.Timeline newTimeline(Prop prop, int boneIndex, int frames, int bezierCount) {
		switch (prop) {
		case ROTATE: return new RotateTimeline(frames, bezierCount, boneIndex);
		case TRANSLATE_X: return new com.esotericsoftware.spine.Animation.TranslateXTimeline(frames, bezierCount, boneIndex);
		case TRANSLATE_Y: return new com.esotericsoftware.spine.Animation.TranslateYTimeline(frames, bezierCount, boneIndex);
		case TRANSLATE: return new TranslateTimeline(frames, bezierCount, boneIndex);
		case SCALE_X: return new com.esotericsoftware.spine.Animation.ScaleXTimeline(frames, bezierCount, boneIndex);
		case SCALE_Y: return new com.esotericsoftware.spine.Animation.ScaleYTimeline(frames, bezierCount, boneIndex);
		case SCALE: return new ScaleTimeline(frames, bezierCount, boneIndex);
		case SHEAR_X: return new ShearXTimeline(frames, bezierCount, boneIndex);
		case SHEAR_Y: return new ShearYTimeline(frames, bezierCount, boneIndex);
		case SHEAR: return new ShearTimeline(frames, bezierCount, boneIndex);
		}
		return null;
	}

	private enum SnapshotKind {
		BONE, INHERIT, ATTACHMENT, COLOR, RGB, ALPHA, TWO_COLOR, IK, TRANSFORM,
		PATH_POSITION, PATH_SPACING, PATH_MIX,
		PHYSICS_RESET, PHYSICS_INERTIA, PHYSICS_STRENGTH, PHYSICS_DAMPING, PHYSICS_MASS,
		PHYSICS_WIND, PHYSICS_GRAVITY, PHYSICS_MIX, SLIDER_TIME, SLIDER_MIX,
		DEFORM, SEQUENCE, DRAW_ORDER, DRAW_ORDER_FOLDER, EVENT
	}

	/** Immutable copy of one editable timeline target and related Slider loop state. */
	public static final class TimelineSnapshot {
		private final Animation animation;
		private final SnapshotKind kind;
		private final Prop prop;
		private final int targetIndex;
		private final Attachment targetAttachment;
		private final int[] targetSlots;
		private final int timelineIndex;
		private final Timeline timeline;
		private final SliderData[] sliderReferences;
		private final boolean[] sliderLoops;

		private TimelineSnapshot(Animation animation, SnapshotKind kind, Prop prop, int targetIndex,
			Attachment targetAttachment, int[] targetSlots, int timelineIndex, Timeline timeline,
			SliderData[] sliderReferences, boolean[] sliderLoops) {
			this.animation = animation;
			this.kind = kind;
			this.prop = prop;
			this.targetIndex = targetIndex;
			this.targetAttachment = targetAttachment;
			this.targetSlots = targetSlots == null ? null : targetSlots.clone();
			this.timelineIndex = timelineIndex;
			this.timeline = timeline;
			this.sliderReferences = sliderReferences.clone();
			this.sliderLoops = sliderLoops.clone();
		}

		public boolean sameAs(TimelineSnapshot other) {
			return other != null && animation == other.animation && kind == other.kind && prop == other.prop
				&& targetIndex == other.targetIndex && targetAttachment == other.targetAttachment
				&& java.util.Arrays.equals(targetSlots, other.targetSlots)
				&& timelineIndex == other.timelineIndex
				&& timelinesEqual(timeline, other.timeline)
				&& java.util.Arrays.equals(sliderReferences, other.sliderReferences)
				&& java.util.Arrays.equals(sliderLoops, other.sliderLoops);
		}
	}

	private static final class CopiedKeyframe {
		private final TimelineSnapshot target;
		private final int sourceFrame;
		private final float offset;
		private final CurveSnapshot curve;

		private CopiedKeyframe(TimelineSnapshot target, int sourceFrame, float offset, CurveSnapshot curve) {
			this.target = target;
			this.sourceFrame = sourceFrame;
			this.offset = offset;
			this.curve = curve;
		}
	}

	/** Immutable, typed copy of keys from one or more timeline targets. */
	public static final class KeyframeBlock {
		private final SkeletonData skeletonData;
		private final CopiedKeyframe[] keys;
		private final float duration;

		private KeyframeBlock(SkeletonData skeletonData, CopiedKeyframe[] keys, float duration) {
			this.skeletonData = skeletonData;
			this.keys = keys;
			this.duration = duration;
		}

		public int size() { return keys.length; }

		public float duration() { return duration; }

		public float offsetAt(int index) {
			if (index < 0 || index >= keys.length) throw new IndexOutOfBoundsException(index);
			return keys[index].offset;
		}
	}

	/** Summary returned after a conservative animation cleanup. */
	public record CleanupResult(int removedKeyframes, int changedTimelines) {
		public boolean changed() { return removedKeyframes > 0; }
	}

	public static final class AxisConversionSnapshot {
		private final Animation animation;
		private final AxisGroup group;
		private final int boneIndex;
		private final String boneName;
		private final TimelineSnapshot[] timelines;
		private final boolean separatedPreferred;
		private final SkeletonDocument.AxisCombineState combineState;
		private final Map<String, SkeletonDocument.GraphViewState> graphStates;

		private AxisConversionSnapshot(Animation animation, AxisGroup group, int boneIndex, String boneName,
			TimelineSnapshot[] timelines, boolean separatedPreferred,
			SkeletonDocument.AxisCombineState combineState,
			Map<String, SkeletonDocument.GraphViewState> graphStates) {
			this.animation = animation;
			this.group = group;
			this.boneIndex = boneIndex;
			this.boneName = boneName;
			this.timelines = timelines.clone();
			this.separatedPreferred = separatedPreferred;
			this.combineState = combineState;
			this.graphStates = Map.copyOf(graphStates);
		}
	}

	public TimelineSnapshot snapshotBoneTimeline(Animation animation, Prop prop, int boneIndex) {
		return snapshotTimeline(animation, SnapshotKind.BONE, prop, boneIndex,
			findTimeline(animation, prop, boneIndex));
	}

	public TimelineSnapshot snapshotInheritTimeline(Animation animation, int boneIndex) {
		return snapshotTimeline(animation, SnapshotKind.INHERIT, null, boneIndex,
			findInheritTimeline(animation, boneIndex));
	}

	public TimelineSnapshot snapshotAttachmentTimeline(Animation animation, int slotIndex) {
		return snapshotTimeline(animation, SnapshotKind.ATTACHMENT, null, slotIndex,
			findAttachmentTimeline(animation, slotIndex));
	}

	public TimelineSnapshot snapshotColorTimeline(Animation animation, int slotIndex) {
		return snapshotTimeline(animation, SnapshotKind.COLOR, null, slotIndex,
			findColorTimeline(animation, slotIndex));
	}

	public TimelineSnapshot snapshotRgbTimeline(Animation animation, int slotIndex) {
		return snapshotTimeline(animation, SnapshotKind.RGB, null, slotIndex,
			findRgbTimeline(animation, slotIndex));
	}

	public TimelineSnapshot snapshotAlphaTimeline(Animation animation, int slotIndex) {
		return snapshotTimeline(animation, SnapshotKind.ALPHA, null, slotIndex,
			findAlphaTimeline(animation, slotIndex));
	}

	public TimelineSnapshot snapshotTwoColorTimeline(Animation animation, int slotIndex) {
		return snapshotTimeline(animation, SnapshotKind.TWO_COLOR, null, slotIndex,
			findTwoColorTimeline(animation, slotIndex));
	}

	public TimelineSnapshot snapshotIkTimeline(Animation animation, int constraintIndex) {
		return snapshotTimeline(animation, SnapshotKind.IK, null, constraintIndex,
			findIkTimeline(animation, constraintIndex));
	}

	public TimelineSnapshot snapshotTransformTimeline(Animation animation, int constraintIndex) {
		return snapshotTimeline(animation, SnapshotKind.TRANSFORM, null, constraintIndex,
			findTransformTimeline(animation, constraintIndex));
	}

	public TimelineSnapshot snapshotPathTimeline(Animation animation, int constraintIndex, PathProp prop) {
		if (prop == null) throw new IllegalArgumentException("Path 轨道属性不能为空");
		SnapshotKind kind = switch (prop) {
		case POSITION -> SnapshotKind.PATH_POSITION;
		case SPACING -> SnapshotKind.PATH_SPACING;
		case MIX -> SnapshotKind.PATH_MIX;
		};
		return snapshotTimeline(animation, kind, null, constraintIndex,
			findPathTimeline(animation, constraintIndex, prop));
	}

	public TimelineSnapshot snapshotPhysicsTimeline(Animation animation, int constraintIndex, PhysicsProp prop) {
		if (prop == null) throw new IllegalArgumentException("Physics 轨道属性不能为空");
		SnapshotKind kind = switch (prop) {
		case RESET -> SnapshotKind.PHYSICS_RESET;
		case INERTIA -> SnapshotKind.PHYSICS_INERTIA;
		case STRENGTH -> SnapshotKind.PHYSICS_STRENGTH;
		case DAMPING -> SnapshotKind.PHYSICS_DAMPING;
		case MASS -> SnapshotKind.PHYSICS_MASS;
		case WIND -> SnapshotKind.PHYSICS_WIND;
		case GRAVITY -> SnapshotKind.PHYSICS_GRAVITY;
		case MIX -> SnapshotKind.PHYSICS_MIX;
		};
		return snapshotTimeline(animation, kind, null, constraintIndex,
			findPhysicsTimeline(animation, constraintIndex, prop));
	}

	public TimelineSnapshot snapshotSliderTimeline(Animation animation, int constraintIndex, SliderProp prop) {
		if (prop == null) throw new IllegalArgumentException("Slider 轨道属性不能为空");
		SnapshotKind kind = prop == SliderProp.TIME ? SnapshotKind.SLIDER_TIME : SnapshotKind.SLIDER_MIX;
		return snapshotTimeline(animation, kind, null, constraintIndex,
			findSliderTimeline(animation, constraintIndex, prop));
	}

	public TimelineSnapshot snapshotDeformTimeline(Animation animation, int slotIndex,
		VertexAttachment attachment) {
		return snapshotTimeline(animation, SnapshotKind.DEFORM, null, slotIndex, attachment,
			findDeformTimeline(animation, slotIndex, attachment));
	}

	public TimelineSnapshot snapshotSequenceTimeline(Animation animation, int slotIndex, Attachment attachment) {
		return snapshotTimeline(animation, SnapshotKind.SEQUENCE, null, slotIndex, attachment,
			findSequenceTimeline(animation, slotIndex, attachment));
	}

	public TimelineSnapshot snapshotDrawOrderTimeline(Animation animation) {
		return snapshotTimeline(animation, SnapshotKind.DRAW_ORDER, null, -1, findDrawOrderTimeline(animation));
	}

	public TimelineSnapshot snapshotDrawOrderFolderTimeline(Animation animation,
		DrawOrderFolderTimeline timeline) {
		if (timeline == null || animation == null || !animation.getTimelines().contains(timeline, true))
			throw new IllegalArgumentException("局部绘制顺序轨道无效");
		return snapshotTimeline(animation, SnapshotKind.DRAW_ORDER_FOLDER, null, -1, null,
			timeline.getSlots(), timeline);
	}

	public TimelineSnapshot snapshotEventTimeline(Animation animation) {
		return snapshotTimeline(animation, SnapshotKind.EVENT, null, -1, findEventTimeline(animation));
	}

	/** Captures any timeline type supported by the editor, including its target identity. */
	public TimelineSnapshot snapshotTimeline(Animation animation, Timeline timeline) {
		if (animation == null || timeline == null || !animation.getTimelines().contains(timeline, true))
			throw new IllegalArgumentException("目标时间线无效");
		if (timeline instanceof DrawOrderFolderTimeline folder)
			return snapshotDrawOrderFolderTimeline(animation, folder);
		if (timeline instanceof DrawOrderTimeline) return snapshotDrawOrderTimeline(animation);
		if (timeline instanceof EventTimeline) return snapshotEventTimeline(animation);
		if (timeline instanceof InheritTimeline inherit)
			return snapshotInheritTimeline(animation, inherit.getBoneIndex());
		if (timeline instanceof AttachmentTimeline attachment)
			return snapshotAttachmentTimeline(animation, attachment.getSlotIndex());
		if (timeline instanceof SequenceTimeline sequence)
			return snapshotSequenceTimeline(animation, sequence.getSlotIndex(), sequence.getAttachment());
		if (timeline instanceof DeformTimeline deform)
			return snapshotDeformTimeline(animation, deform.getSlotIndex(), deform.getAttachment());
		if (timeline instanceof IkConstraintTimeline ik)
			return snapshotIkTimeline(animation, ik.getConstraintIndex());
		if (timeline instanceof TransformConstraintTimeline transform)
			return snapshotTransformTimeline(animation, transform.getConstraintIndex());
		if (timeline instanceof PathConstraintPositionTimeline path)
			return snapshotPathTimeline(animation, path.getConstraintIndex(), PathProp.POSITION);
		if (timeline instanceof PathConstraintSpacingTimeline path)
			return snapshotPathTimeline(animation, path.getConstraintIndex(), PathProp.SPACING);
		if (timeline instanceof PathConstraintMixTimeline path)
			return snapshotPathTimeline(animation, path.getConstraintIndex(), PathProp.MIX);
		PhysicsProp physicsProp = physicsPropOf(timeline);
		if (physicsProp != null && timeline instanceof ConstraintTimeline physics)
			return snapshotPhysicsTimeline(animation, physics.getConstraintIndex(), physicsProp);
		SliderProp sliderProp = sliderPropOf(timeline);
		if (sliderProp != null && timeline instanceof ConstraintTimeline slider)
			return snapshotSliderTimeline(animation, slider.getConstraintIndex(), sliderProp);
		if (isTwoColorTimeline(timeline))
			return snapshotTwoColorTimeline(animation, ((SlotCurveTimeline)timeline).getSlotIndex());
		if (timeline instanceof RGBATimeline color)
			return snapshotColorTimeline(animation, color.getSlotIndex());
		if (timeline instanceof RGBTimeline color)
			return snapshotRgbTimeline(animation, color.getSlotIndex());
		if (timeline instanceof AlphaTimeline alpha)
			return snapshotAlphaTimeline(animation, alpha.getSlotIndex());
		if (timeline instanceof BoneTimeline bone) {
			Prop prop = propOf(timeline);
			if (prop != null) return snapshotBoneTimeline(animation, prop, bone.getBoneIndex());
		}
		throw new IllegalArgumentException("暂不支持时间线类型：" + timeline.getClass().getSimpleName());
	}

	public TimelineSnapshot[] snapshotAnimationTimelines(Animation animation) {
		if (animation == null) throw new IllegalArgumentException("动画不能为空");
		TimelineSnapshot[] snapshots = new TimelineSnapshot[animation.getTimelines().size];
		for (int index = 0; index < snapshots.length; index++)
			snapshots[index] = snapshotTimeline(animation, animation.getTimelines().get(index));
		return snapshots;
	}

	/** Copies a selection while preserving target tracks, relative timing, values, and outgoing curves. */
	public KeyframeBlock copyKeyframes(Animation animation, Timeline[] timelines, int[] frames) {
		if (animation == null || timelines == null || frames == null || timelines.length == 0
			|| timelines.length != frames.length)
			throw new IllegalArgumentException("关键帧选择无效");
		IdentityHashMap<Timeline, TimelineSnapshot> snapshots = new IdentityHashMap<>();
		IdentityHashMap<Timeline, boolean[]> selectedFrames = new IdentityHashMap<>();
		float earliest = Float.POSITIVE_INFINITY, latest = 0f;
		for (int index = 0; index < timelines.length; index++) {
			Timeline timeline = timelines[index];
			int frame = frames[index];
			if (timeline == null || !animation.getTimelines().contains(timeline, true)
				|| frame < 0 || frame >= timeline.getFrameCount())
				throw new IllegalArgumentException("关键帧选择在复制前已变化");
			boolean[] selected = selectedFrames.computeIfAbsent(timeline,
				ignored -> new boolean[timeline.getFrameCount()]);
			if (selected[frame]) throw new IllegalArgumentException("关键帧选择包含重复项");
			selected[frame] = true;
			float time = frameTime(timeline, frame);
			if (!Float.isFinite(time) || time < 0f) throw new IllegalArgumentException("关键帧时间无效");
			earliest = Math.min(earliest, time);
			latest = Math.max(latest, time);
			snapshots.computeIfAbsent(timeline, ignored -> snapshotTimeline(animation, timeline));
		}

		CopiedKeyframe[] copied = new CopiedKeyframe[timelines.length];
		for (int index = 0; index < timelines.length; index++) {
			Timeline timeline = timelines[index];
			int frame = frames[index];
			CurveSnapshot curve = timeline instanceof CurveTimeline ? captureCurve(timeline, frame) : null;
			copied[index] = new CopiedKeyframe(snapshots.get(timeline), frame,
				frameTime(timeline, frame) - earliest, curve);
		}
		return new KeyframeBlock(doc.data, copied, latest - earliest);
	}

	public boolean canPasteKeyframes(KeyframeBlock block) {
		return block != null && block.keys.length > 0 && block.skeletonData == doc.data;
	}

	/** Snapshots every target that a block paste may change, suitable for one undo transaction. */
	public TimelineSnapshot[] snapshotKeyframeBlockTargets(Animation animation, KeyframeBlock block) {
		requirePasteBlock(animation, block);
		ArrayList<TimelineSnapshot> snapshots = new ArrayList<>();
		for (CopiedKeyframe key : block.keys) {
			boolean found = false;
			for (TimelineSnapshot snapshot : snapshots) {
				if (sameSnapshotTarget(snapshot, key.target)) { found = true; break; }
			}
			if (found) continue;
			Timeline current = findSnapshotTimeline(animation, key.target);
			snapshots.add(snapshotTimeline(animation, key.target.kind, key.target.prop, key.target.targetIndex,
				key.target.targetAttachment, key.target.targetSlots, current));
		}
		return snapshots.toArray(new TimelineSnapshot[0]);
	}

	/**
	 * Pastes all copied keys as one model mutation. Non-event target collisions reject the entire block;
	 * event timelines retain Spine's supported same-time event ordering.
	 */
	public int pasteKeyframes(Animation animation, KeyframeBlock block, float startTime) {
		requirePasteBlock(animation, block);
		if (!Float.isFinite(startTime) || startTime < 0f)
			throw new IllegalArgumentException("粘贴起始时间必须是非负有限数值");

		ArrayList<PasteGroup> groups = new ArrayList<>();
		for (CopiedKeyframe key : block.keys) {
			float time = startTime + key.offset;
			if (!Float.isFinite(time)) throw new IllegalArgumentException("粘贴后的关键帧时间超出范围");
			PasteGroup group = null;
			for (PasteGroup candidate : groups) {
				if (sameSnapshotTarget(candidate.target, key.target)) { group = candidate; break; }
			}
			if (group == null) {
				group = new PasteGroup(key.target, findSnapshotTimeline(animation, key.target));
				groups.add(group);
			}
			group.frames.add(new TransferredFrame(key.target.timeline, key.sourceFrame, time, key.curve,
				true, key.sourceFrame));
		}

		for (PasteGroup group : groups) group.build(this);
		Array<Timeline> current = animation.getTimelines();
		Array<Timeline> rebuilt = new Array<>(true, current.size + groups.size(), Timeline[]::new);
		rebuilt.addAll(current);
		for (PasteGroup group : groups) {
			if (group.current == null) {
				rebuilt.add(group.rebuilt);
			} else {
				int index = rebuilt.indexOf(group.current, true);
				if (index < 0) throw new IllegalStateException("目标时间线在粘贴前已变化");
				rebuilt.set(index, group.rebuilt);
			}
		}

		animation.setTimelines(rebuilt, new IntArray(animation.getBones()));
		for (PasteGroup group : groups)
			if (group.current != null) doc.migrateGraphViewState(animation.getName(), group.current, group.rebuilt);
		refreshAnimation(animation);
		doc.markDirty();
		return block.keys.length;
	}

	private void requirePasteBlock(Animation animation, KeyframeBlock block) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("粘贴目标动画无效");
		if (!canPasteKeyframes(block)) throw new IllegalArgumentException("复制的关键帧不属于当前骨架");
	}

	private static boolean sameSnapshotTarget(TimelineSnapshot first, TimelineSnapshot second) {
		return first.kind == second.kind && first.prop == second.prop && first.targetIndex == second.targetIndex
			&& first.targetAttachment == second.targetAttachment
			&& java.util.Arrays.equals(first.targetSlots, second.targetSlots);
	}

	private static final class TransferredFrame {
		private final Timeline source;
		private final int sourceFrame;
		private final float time;
		private final CurveSnapshot curve;
		private final boolean pasted;
		private final int order;

		private TransferredFrame(Timeline source, int sourceFrame, float time, CurveSnapshot curve,
			boolean pasted, int order) {
			this.source = source;
			this.sourceFrame = sourceFrame;
			this.time = time;
			this.curve = curve;
			this.pasted = pasted;
			this.order = order;
		}
	}

	private static final class PasteGroup {
		private final TimelineSnapshot target;
		private final Timeline current;
		private final ArrayList<TransferredFrame> frames = new ArrayList<>();
		private Timeline rebuilt;

		private PasteGroup(TimelineSnapshot target, Timeline current) {
			this.target = target;
			this.current = current;
		}

		private void build(AnimationEditor editor) {
			Timeline template = current == null ? target.timeline : current;
			if (template == null) throw new IllegalStateException("复制的目标时间线不存在");
			if (current != null && current.getClass() != target.timeline.getClass())
				throw new IllegalStateException("目标时间线类型已变化");
			if (current != null) {
				for (int frame = 0; frame < current.getFrameCount(); frame++) {
					CurveSnapshot curve = current instanceof CurveTimeline ? editor.captureCurve(current, frame) : null;
					frames.add(new TransferredFrame(current, frame, editor.frameTime(current, frame), curve,
						false, frame));
				}
			}
			frames.sort((first, second) -> {
				int time = Float.compare(first.time, second.time);
				if (time != 0) return time;
				if (first.pasted != second.pasted) return first.pasted ? 1 : -1;
				return Integer.compare(first.order, second.order);
			});
			if (!(template instanceof EventTimeline)) {
				for (int index = 1; index < frames.size(); index++)
					if (sameFrameTime(frames.get(index - 1).time, frames.get(index).time))
						throw new IllegalArgumentException("至少一条目标轨道在粘贴时间已有关键帧");
			}

			int bezierCount = 0;
			if (template instanceof CurveTimeline) {
				int channels = curveChannelCount(template);
				for (int frame = 0; frame < frames.size() - 1; frame++) {
					CurveSnapshot curve = frames.get(frame).curve;
					if (curve != null && curve.type >= CurveTimeline.BEZIER) bezierCount += channels;
				}
			}
			rebuilt = editor.newTimelineLike(template, frames.size(), bezierCount);
			CurveSnapshot[] curves = new CurveSnapshot[frames.size()];
			for (int frame = 0; frame < frames.size(); frame++) {
				TransferredFrame source = frames.get(frame);
				editor.copyEditableFrameAtTime(source.source, source.sourceFrame, rebuilt, frame, source.time);
				curves[frame] = source.curve;
			}
			applyCurveSnapshots(rebuilt, curves);
		}
	}

	private TimelineSnapshot snapshotTimeline(Animation animation, SnapshotKind kind, Prop prop,
		int targetIndex, Timeline timeline) {
		return snapshotTimeline(animation, kind, prop, targetIndex, null, timeline);
	}

	private TimelineSnapshot snapshotTimeline(Animation animation, SnapshotKind kind, Prop prop,
		int targetIndex, Attachment targetAttachment, Timeline timeline) {
		return snapshotTimeline(animation, kind, prop, targetIndex, targetAttachment, null, timeline);
	}

	private TimelineSnapshot snapshotTimeline(Animation animation, SnapshotKind kind, Prop prop,
		int targetIndex, Attachment targetAttachment, int[] targetSlots, Timeline timeline) {
		if (animation == null) throw new IllegalArgumentException("动画不能为空");
		int timelineIndex = timeline == null ? animation.getTimelines().size : animation.getTimelines().indexOf(timeline, true);
		ArrayList<SliderData> references = new ArrayList<>();
		if (doc.data != null) {
			for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof SliderData slider && slider.getAnimation() == animation)
					references.add(slider);
		}
		SliderData[] sliders = references.toArray(new SliderData[0]);
		boolean[] loops = new boolean[sliders.length];
		for (int index = 0; index < sliders.length; index++) loops[index] = sliders[index].getLoop();
		return new TimelineSnapshot(animation, kind, prop, targetIndex, targetAttachment, targetSlots, timelineIndex,
			timeline == null ? null : cloneTimeline(timeline), sliders, loops);
	}

	public TimelineSnapshot snapshotCurrent(TimelineSnapshot reference) {
		if (reference == null) throw new IllegalArgumentException("参考快照不能为空");
		return snapshotTimeline(reference.animation, reference.kind, reference.prop, reference.targetIndex,
			reference.targetAttachment, reference.targetSlots, findSnapshotTimeline(reference));
	}

	/** Restores one target without replacing unrelated timelines. */
	public void restoreTimeline(TimelineSnapshot snapshot) {
		if (snapshot == null) throw new IllegalArgumentException("时间线快照不能为空");
		Timeline current = findSnapshotTimeline(snapshot);
		Array<Timeline> timelines = snapshot.animation.getTimelines();
		Timeline restored = snapshot.timeline == null ? null : cloneTimeline(snapshot.timeline);
		if (current != null) {
			timelines.removeValue(current, true);
			if (restored == null) doc.removeTimelineEditorState(snapshot.animation.getName(), current);
			else doc.migrateGraphViewState(snapshot.animation.getName(), current, restored);
		}
		if (snapshot.timeline != null) {
			timelines.insert(Math.max(0, Math.min(snapshot.timelineIndex, timelines.size)), restored);
		}
		refreshAnimation(snapshot.animation);
		restoreSliderLoopStates(snapshot);
		doc.markDirty();
	}

	private void restoreSliderLoopStates(TimelineSnapshot snapshot) {
		boolean canLoop = snapshot.animation.getDuration() > 0f;
		for (int index = 0; index < snapshot.sliderReferences.length; index++) {
			SliderData slider = snapshot.sliderReferences[index];
			if (slider.getAnimation() == snapshot.animation)
				slider.setLoop(canLoop && snapshot.sliderLoops[index]);
		}
	}

	/** Restores a related group of timeline snapshots in their original order. */
	public void restoreTimelines(TimelineSnapshot... snapshots) {
		if (snapshots == null) throw new IllegalArgumentException("时间线快照组不能为空");
		for (TimelineSnapshot snapshot : snapshots) restoreTimeline(snapshot);
	}

	private Timeline findSnapshotTimeline(TimelineSnapshot snapshot) {
		return findSnapshotTimeline(snapshot.animation, snapshot);
	}

	private Timeline findSnapshotTimeline(Animation animation, TimelineSnapshot snapshot) {
		return switch (snapshot.kind) {
		case BONE -> findTimeline(animation, snapshot.prop, snapshot.targetIndex);
		case INHERIT -> findInheritTimeline(animation, snapshot.targetIndex);
		case ATTACHMENT -> findAttachmentTimeline(animation, snapshot.targetIndex);
		case COLOR -> findColorTimeline(animation, snapshot.targetIndex);
		case RGB -> findRgbTimeline(animation, snapshot.targetIndex);
		case ALPHA -> findAlphaTimeline(animation, snapshot.targetIndex);
		case TWO_COLOR -> findTwoColorTimeline(animation, snapshot.targetIndex);
		case IK -> findIkTimeline(animation, snapshot.targetIndex);
		case TRANSFORM -> findTransformTimeline(animation, snapshot.targetIndex);
		case PATH_POSITION -> findPathTimeline(animation, snapshot.targetIndex, PathProp.POSITION);
		case PATH_SPACING -> findPathTimeline(animation, snapshot.targetIndex, PathProp.SPACING);
		case PATH_MIX -> findPathTimeline(animation, snapshot.targetIndex, PathProp.MIX);
		case PHYSICS_RESET -> findPhysicsTimeline(animation, snapshot.targetIndex, PhysicsProp.RESET);
		case PHYSICS_INERTIA -> findPhysicsTimeline(animation, snapshot.targetIndex, PhysicsProp.INERTIA);
		case PHYSICS_STRENGTH -> findPhysicsTimeline(animation, snapshot.targetIndex, PhysicsProp.STRENGTH);
		case PHYSICS_DAMPING -> findPhysicsTimeline(animation, snapshot.targetIndex, PhysicsProp.DAMPING);
		case PHYSICS_MASS -> findPhysicsTimeline(animation, snapshot.targetIndex, PhysicsProp.MASS);
		case PHYSICS_WIND -> findPhysicsTimeline(animation, snapshot.targetIndex, PhysicsProp.WIND);
		case PHYSICS_GRAVITY -> findPhysicsTimeline(animation, snapshot.targetIndex, PhysicsProp.GRAVITY);
		case PHYSICS_MIX -> findPhysicsTimeline(animation, snapshot.targetIndex, PhysicsProp.MIX);
		case SLIDER_TIME -> findSliderTimeline(animation, snapshot.targetIndex, SliderProp.TIME);
		case SLIDER_MIX -> findSliderTimeline(animation, snapshot.targetIndex, SliderProp.MIX);
		case DEFORM -> findDeformTimeline(animation, snapshot.targetIndex,
			(VertexAttachment)snapshot.targetAttachment);
		case SEQUENCE -> findSequenceTimeline(animation, snapshot.targetIndex, snapshot.targetAttachment);
		case DRAW_ORDER -> findDrawOrderTimeline(animation);
		case DRAW_ORDER_FOLDER -> findDrawOrderFolderTimeline(animation, snapshot.targetSlots);
		case EVENT -> findEventTimeline(animation);
		};
	}

	Timeline cloneTimeline(Timeline source) {
		return cloneTimeline(source, null, null, null, null);
	}

	/** Copies an attachment-specific track to an independent attachment in a composed skin. */
	Timeline cloneAttachmentTimeline(Timeline source, Attachment attachment, int slotIndex) {
		if (!(source instanceof DeformTimeline || source instanceof SequenceTimeline))
			throw new IllegalArgumentException("Only Deform and Sequence tracks have attachment ownership");
		return cloneTimeline(source, null, attachment, slotIndex);
	}

	private Timeline cloneTimeline(Timeline source, Integer constraintIndexOverride) {
		return cloneTimeline(source, constraintIndexOverride, null, null, null);
	}

	private Timeline cloneTimeline(Timeline source, Integer constraintIndexOverride,
		Attachment attachmentOverride) {
		return cloneTimeline(source, constraintIndexOverride, attachmentOverride, null, null);
	}

	/** Clones a timeline while optionally replacing its slot index. Slot indices are immutable in
	 * Spine's runtime timeline classes, so structural slot reordering uses this path for every
	 * affected animation rather than mutating private arrays. */
	private Timeline cloneTimeline(Timeline source, Integer constraintIndexOverride,
		Attachment attachmentOverride, Integer slotIndexOverride) {
		return cloneTimeline(source, constraintIndexOverride, attachmentOverride, slotIndexOverride, null);
	}

	private Timeline cloneTimeline(Timeline source, Integer constraintIndexOverride,
		Attachment attachmentOverride, Integer slotIndexOverride, Integer boneIndexOverride) {
		if (source instanceof DrawOrderFolderTimeline drawOrderFolder) {
			int[] slots = remapSlotIndices(drawOrderFolder.getSlots(), slotIndexOverride);
			DrawOrderFolderTimeline copy = new DrawOrderFolderTimeline(
				drawOrderFolder.getFrameCount(), slots, doc.data.getSlots().size);
			for (int frame = 0; frame < drawOrderFolder.getFrameCount(); frame++) {
				int[] order = drawOrderFolder.getDrawOrders()[frame];
				copy.setFrame(frame, frameTime(drawOrderFolder, frame),
					order == null ? null : remapSlotIndices(order, slotIndexOverride));
			}
			return copy;
		}
		if (source instanceof DrawOrderTimeline drawOrder) {
			DrawOrderTimeline copy = new DrawOrderTimeline(drawOrder.getFrameCount());
			for (int frame = 0; frame < drawOrder.getFrameCount(); frame++)
				copy.setFrame(frame, frameTime(drawOrder, frame),
					remapSlotIndices(drawOrderAt(drawOrder, frame), slotIndexOverride));
			return copy;
		}
		if (source instanceof EventTimeline events) {
			EventTimeline copy = new EventTimeline(events.getFrameCount());
			for (int frame = 0; frame < events.getFrameCount(); frame++)
				copy.setFrame(frame, copyEvent(eventAt(events, frame), eventAt(events, frame).getTime()));
			return copy;
		}
		if (source instanceof AttachmentTimeline attachment) {
			AttachmentTimeline copy = new AttachmentTimeline(attachment.getFrameCount(),
				slotIndex(slotIndexOverride, attachment.getSlotIndex()));
			for (int frame = 0; frame < attachment.getFrameCount(); frame++)
				copy.setFrame(frame, attachment.getFrames()[frame], attachment.getAttachmentNames()[frame]);
			return copy;
		}
		if (source instanceof InheritTimeline inherit) {
			InheritTimeline copy = new InheritTimeline(inherit.getFrameCount(),
				boneIndex(boneIndexOverride, inherit.getBoneIndex()));
			for (int frame = 0; frame < inherit.getFrameCount(); frame++)
				copy.setFrame(frame, frameTime(inherit, frame), inheritAt(inherit, frame));
			return copy;
		}
		if (source instanceof SequenceTimeline sequence) {
			SequenceTimeline copy = new SequenceTimeline(sequence.getFrameCount(),
				slotIndex(slotIndexOverride, sequence.getSlotIndex()),
				attachmentOverride == null ? sequence.getAttachment() : attachmentOverride);
			for (int frame = 0; frame < sequence.getFrameCount(); frame++)
				copySequenceFrame(sequence, frame, copy, frame);
			return copy;
		}
		if (source instanceof PhysicsConstraintResetTimeline reset) {
			PhysicsConstraintResetTimeline copy = new PhysicsConstraintResetTimeline(
				reset.getFrameCount(), constraintIndex(constraintIndexOverride, reset.getConstraintIndex()));
			for (int frame = 0; frame < reset.getFrameCount(); frame++)
				copy.setFrame(frame, frameTime(reset, frame));
			return copy;
		}

		int bezierCount = source instanceof CurveTimeline curve
			? Math.max(0, (curveData(curve).length - source.getFrameCount()) / CurveTimeline.BEZIER_SIZE) : 0;
		Timeline copy;
		if (source instanceof DeformTimeline deform) {
			VertexAttachment target = attachmentOverride == null ? deform.getAttachment()
				: (VertexAttachment)attachmentOverride;
			DeformTimeline deformCopy = new DeformTimeline(deform.getFrameCount(), bezierCount,
				slotIndex(slotIndexOverride, deform.getSlotIndex()), target);
			for (int frame = 0; frame < deform.getFrameCount(); frame++)
				deformCopy.setFrame(frame, frameTime(deform, frame), deform.getVertices()[frame].clone());
			copy = deformCopy;
		} else if (source instanceof IkConstraintTimeline ik) {
			IkConstraintTimeline ikCopy = new IkConstraintTimeline(ik.getFrameCount(), bezierCount,
				constraintIndex(constraintIndexOverride, ik.getConstraintIndex()));
			for (int frame = 0; frame < ik.getFrameCount(); frame++) copyIkFrame(ik, frame, ikCopy, frame);
			copy = ikCopy;
		} else if (source instanceof TransformConstraintTimeline transform) {
			TransformConstraintTimeline transformCopy = new TransformConstraintTimeline(
				transform.getFrameCount(), bezierCount,
				constraintIndex(constraintIndexOverride, transform.getConstraintIndex()));
			for (int frame = 0; frame < transform.getFrameCount(); frame++)
				copyTransformFrame(transform, frame, transformCopy, frame);
			copy = transformCopy;
		} else if (source instanceof PhysicsConstraintTimeline physics) {
			PhysicsProp physicsProp = physicsPropOf(physics);
			Timeline physicsCopy = newPhysicsValueTimeline(physicsProp, physics.getFrameCount(), bezierCount,
				constraintIndex(constraintIndexOverride, physics.getConstraintIndex()));
			for (int frame = 0; frame < physics.getFrameCount(); frame++)
				copyPhysicsValueFrame(physics, frame, physicsCopy, frame);
			copy = physicsCopy;
		} else if (source instanceof PathConstraintPositionTimeline position) {
			PathConstraintPositionTimeline positionCopy = new PathConstraintPositionTimeline(
				position.getFrameCount(), bezierCount,
				constraintIndex(constraintIndexOverride, position.getConstraintIndex()));
			for (int frame = 0; frame < position.getFrameCount(); frame++)
				copyPathValueFrame(position, frame, positionCopy, frame);
			copy = positionCopy;
		} else if (source instanceof PathConstraintSpacingTimeline spacing) {
			PathConstraintSpacingTimeline spacingCopy = new PathConstraintSpacingTimeline(
				spacing.getFrameCount(), bezierCount,
				constraintIndex(constraintIndexOverride, spacing.getConstraintIndex()));
			for (int frame = 0; frame < spacing.getFrameCount(); frame++)
				copyPathValueFrame(spacing, frame, spacingCopy, frame);
			copy = spacingCopy;
		} else if (source instanceof PathConstraintMixTimeline mix) {
			PathConstraintMixTimeline mixCopy = new PathConstraintMixTimeline(
				mix.getFrameCount(), bezierCount,
				constraintIndex(constraintIndexOverride, mix.getConstraintIndex()));
			for (int frame = 0; frame < mix.getFrameCount(); frame++)
				copyPathMixFrame(mix, frame, mixCopy, frame);
			copy = mixCopy;
		} else if (source instanceof SliderTimeline slider) {
			SliderTimeline sliderCopy = new SliderTimeline(
				slider.getFrameCount(), bezierCount,
				constraintIndex(constraintIndexOverride, slider.getConstraintIndex()));
			for (int frame = 0; frame < slider.getFrameCount(); frame++)
				sliderCopy.setFrame(frame, frameTime(slider, frame), frameValue(slider, frame, 0));
			copy = sliderCopy;
		} else if (source instanceof SliderMixTimeline sliderMix) {
			SliderMixTimeline sliderMixCopy = new SliderMixTimeline(
				sliderMix.getFrameCount(), bezierCount,
				constraintIndex(constraintIndexOverride, sliderMix.getConstraintIndex()));
			for (int frame = 0; frame < sliderMix.getFrameCount(); frame++)
				sliderMixCopy.setFrame(frame, frameTime(sliderMix, frame), frameValue(sliderMix, frame, 0));
			copy = sliderMixCopy;
		} else if (isTwoColorTimeline(source)) {
			SlotCurveTimeline twoColor = (SlotCurveTimeline)source;
			SlotCurveTimeline colorCopy = newTwoColorTimeline(source instanceof RGBA2Timeline,
				source.getFrameCount(), bezierCount, slotIndex(slotIndexOverride, twoColor.getSlotIndex()));
			for (int frame = 0; frame < source.getFrameCount(); frame++)
				copyTwoColorFrame(source, frame, colorCopy, frame);
			copy = colorCopy;
		} else if (source instanceof RGBATimeline color) {
			RGBATimeline colorCopy = new RGBATimeline(color.getFrameCount(), bezierCount,
				slotIndex(slotIndexOverride, color.getSlotIndex()));
			for (int frame = 0; frame < color.getFrameCount(); frame++) copyColorFrame(color, frame, colorCopy, frame);
			copy = colorCopy;
		} else if (source instanceof RGBTimeline color) {
			RGBTimeline colorCopy = new RGBTimeline(color.getFrameCount(), bezierCount,
				slotIndex(slotIndexOverride, color.getSlotIndex()));
			for (int frame = 0; frame < color.getFrameCount(); frame++) copyRgbFrame(color, frame, colorCopy, frame);
			copy = colorCopy;
		} else if (source instanceof AlphaTimeline alpha) {
			AlphaTimeline alphaCopy = new AlphaTimeline(alpha.getFrameCount(), bezierCount,
				slotIndex(slotIndexOverride, alpha.getSlotIndex()));
			for (int frame = 0; frame < alpha.getFrameCount(); frame++) copyAlphaFrame(alpha, frame, alphaCopy, frame);
			copy = alphaCopy;
		} else if (source instanceof BoneTimeline boneTimeline) {
			Prop prop = propOf(source);
			if (prop == null) throw new IllegalStateException("暂不支持复制轨道 " + source.getClass().getSimpleName());
			copy = newTimeline(prop, boneIndex(boneIndexOverride, boneTimeline.getBoneIndex()),
				source.getFrameCount(), bezierCount);
			for (int frame = 0; frame < source.getFrameCount(); frame++)
				setFrame(copy, frame, frameTime(source, frame), frameValue(source, frame, 0), frameValue(source, frame, 1));
		} else {
			throw new IllegalStateException("暂不支持复制轨道 " + source.getClass().getSimpleName());
		}

		if (source instanceof CurveTimeline sourceCurve && copy instanceof CurveTimeline targetCurve) {
			float[] sourceCurves = curveData(sourceCurve);
			float[] targetCurves = curveData(targetCurve);
			System.arraycopy(sourceCurves, 0, targetCurves, 0, Math.min(sourceCurves.length, targetCurves.length));
		}
		return copy;
	}

	private static int constraintIndex(Integer override, int original) {
		return override == null ? original : override;
	}

	private static int slotIndex(Integer override, int original) {
		return override == null ? original : override;
	}

	private static int boneIndex(Integer override, int original) {
		return override == null ? original : override;
	}

	private static int[] remapSlotIndices(int[] values, Integer ignored) {
		return values == null ? null : values.clone();
	}

	/** Remaps an array through the old-index to new-index table. The overload above intentionally
	 * keeps cloneTimeline's null override path allocation-free for ordinary snapshots. */
	private static int[] remapSlotIndices(int[] values, int[] oldToNew) {
		if (values == null) return null;
		int[] copy = values.clone();
		if (oldToNew == null) return copy;
		for (int index = 0; index < copy.length; index++) {
			int old = copy[index];
			if (old >= 0 && old < oldToNew.length) copy[index] = oldToNew[old];
		}
		return copy;
	}

	/** Rebuilds every timeline whose slot index or draw-order payload references a reordered slot. */
	public void remapSlotTimelines(int[] oldToNew) {
		if (doc.data == null || oldToNew == null || oldToNew.length != doc.data.getSlots().size)
			throw new IllegalArgumentException("槽索引映射与当前项目不匹配");
		remapStructureTimelines(null, oldToNew, doc.data.getSlots().size);
	}

	/** Prepares all replacement tracks before mutating animations; -1 denotes a removed index. */
	void remapStructureTimelines(int[] boneMap, int[] slotMap, int slotCount) {
		ArrayList<Runnable> commits = new ArrayList<>();
		for (Animation animation : doc.data.getAnimations()) {
			Array<Timeline> timelines = animation.getTimelines();
			Array<Timeline> rebuilt = new Array<>(true, timelines.size, Timeline[]::new);
			IdentityHashMap<Timeline, Timeline> replacements = new IdentityHashMap<>();
			for (Timeline timeline : timelines) {
				Timeline replacement = timeline;
				if (slotMap != null && timeline instanceof DrawOrderFolderTimeline folder) {
					int[] slots = retainedIndices(folder.getSlots(), slotMap);
					Arrays.sort(slots);
					if (slots.length == 0) replacement = null;
					else {
						int[] localMap = new int[folder.getSlots().length];
						for (int i = 0; i < localMap.length; i++) {
							int mapped = mappedIndex(folder.getSlots()[i], slotMap);
							localMap[i] = mapped < 0 ? -1 : Arrays.binarySearch(slots, mapped);
						}
						DrawOrderFolderTimeline copy = new DrawOrderFolderTimeline(folder.getFrameCount(), slots, slotCount);
						for (int frame = 0; frame < folder.getFrameCount(); frame++)
							copy.setFrame(frame, frameTime(folder, frame), retainedIndices(folder.getDrawOrders()[frame], localMap));
						replacement = copy;
					}
				} else if (slotMap != null && timeline instanceof DrawOrderTimeline order) {
					DrawOrderTimeline copy = new DrawOrderTimeline(order.getFrameCount());
					for (int frame = 0; frame < order.getFrameCount(); frame++)
						copy.setFrame(frame, frameTime(order, frame), retainedIndices(drawOrderAt(order, frame), slotMap));
					replacement = copy;
				} else if (slotMap != null && timeline instanceof Animation.SlotTimeline slotTimeline) {
					int mapped = mappedIndex(slotTimeline.getSlotIndex(), slotMap);
					if (mapped < 0) throw new IllegalStateException("删除的槽仍被动画轨道引用");
					if (mapped != slotTimeline.getSlotIndex()) replacement = cloneTimeline(timeline, null, null, mapped);
				} else if (boneMap != null && timeline instanceof BoneTimeline boneTimeline) {
					int mapped = mappedIndex(boneTimeline.getBoneIndex(), boneMap);
					if (mapped < 0) throw new IllegalStateException("删除的骨骼仍被动画轨道引用");
					if (mapped != boneTimeline.getBoneIndex()) replacement = cloneTimeline(timeline, null, null, null, mapped);
				}
				if (replacement != null) rebuilt.add(replacement);
				if (replacement != timeline) replacements.put(timeline, replacement);
			}
			IntArray active = boneMap == null ? new IntArray(animation.getBones())
				: new IntArray(retainedIndices(animation.getBones().toArray(), boneMap));
			commits.add(() -> {
				doc.migrateGraphViewStates(animation.getName(), replacements);
				animation.setTimelines(rebuilt, active);
				if (rebuilt.size != timelines.size) refreshAnimationDuration(animation);
			});
		}
		for (Runnable commit : commits) commit.run();
		doc.markDirty();
	}

	private static int mappedIndex(int index, int[] map) {
		if (index < 0 || index >= map.length) throw new IllegalStateException("动画轨道包含无效结构索引");
		return map[index];
	}

	private static int[] retainedIndices(int[] indices, int[] map) {
		if (indices == null) return null;
		IntArray retained = new IntArray(indices.length);
		for (int index : indices) {
			int mapped = mappedIndex(index, map);
			if (mapped >= 0) retained.add(mapped);
		}
		return retained.toArray();
	}

	/** Rebuilds later constraint timelines before one entry is removed from the unified constraint list. */
	public void reindexConstraintTimelinesAfterRemoval(int removedIndex) {
		if (doc.data == null || removedIndex < 0 || removedIndex >= doc.data.getConstraints().size)
			throw new IllegalArgumentException("The removed constraint index is invalid");

		ArrayList<Animation> changedAnimations = new ArrayList<>();
		ArrayList<Array<Timeline>> rebuiltAnimations = new ArrayList<>();
		for (Animation animation : doc.data.getAnimations()) {
			Array<Timeline> current = animation.getTimelines();
			Array<Timeline> rebuilt = null;
			IdentityHashMap<Timeline, Timeline> graphMigrations = new IdentityHashMap<>();
			for (int timelineIndex = 0; timelineIndex < current.size; timelineIndex++) {
				Timeline timeline = current.get(timelineIndex);
				if (!(timeline instanceof ConstraintTimeline constraint)) continue;
				int targetIndex = constraint.getConstraintIndex();
				if (targetIndex == removedIndex)
					throw new IllegalStateException("A constraint timeline still targets the removed index");
				if (targetIndex < 0 || targetIndex < removedIndex) continue;
				if (rebuilt == null) rebuilt = new Array<>(current);
				Timeline replacement = cloneTimeline(timeline, targetIndex - 1);
				graphMigrations.put(timeline, replacement);
				rebuilt.set(timelineIndex, replacement);
			}
			if (rebuilt != null) {
				doc.migrateGraphViewStates(animation.getName(), graphMigrations);
				changedAnimations.add(animation);
				rebuiltAnimations.add(rebuilt);
			}
		}

		for (int index = 0; index < changedAnimations.size(); index++) {
			Animation animation = changedAnimations.get(index);
			animation.setTimelines(rebuiltAnimations.get(index), new IntArray(animation.getBones()));
		}
	}

	/** Rebuilds every indexed constraint timeline using an old-index to new-index permutation. */
	public void remapConstraintTimelines(int[] oldToNew) {
		if (doc.data == null || oldToNew == null || oldToNew.length != doc.data.getConstraints().size)
			throw new IllegalArgumentException("The constraint index mapping is invalid");
		boolean[] destinations = new boolean[oldToNew.length];
		for (int oldIndex = 0; oldIndex < oldToNew.length; oldIndex++) {
			int newIndex = oldToNew[oldIndex];
			if (newIndex < 0 || newIndex >= oldToNew.length || destinations[newIndex])
				throw new IllegalArgumentException("The constraint index mapping must be a permutation");
			destinations[newIndex] = true;
		}

		ArrayList<Animation> changedAnimations = new ArrayList<>();
		ArrayList<Array<Timeline>> rebuiltAnimations = new ArrayList<>();
		for (Animation animation : doc.data.getAnimations()) {
			Array<Timeline> current = animation.getTimelines();
			Array<Timeline> rebuilt = null;
			IdentityHashMap<Timeline, Timeline> graphMigrations = new IdentityHashMap<>();
			for (int timelineIndex = 0; timelineIndex < current.size; timelineIndex++) {
				Timeline timeline = current.get(timelineIndex);
				if (!(timeline instanceof ConstraintTimeline constraint)) continue;
				int oldIndex = constraint.getConstraintIndex();
				if (oldIndex < 0) {
					if (!(timeline instanceof PhysicsConstraintTimeline)
						&& !(timeline instanceof PhysicsConstraintResetTimeline))
						throw new IllegalStateException("Only global Physics timelines may use a negative constraint index");
					continue;
				}
				if (oldIndex >= oldToNew.length)
					throw new IllegalStateException("A constraint timeline has an invalid target index");
				int newIndex = oldToNew[oldIndex];
				if (newIndex == oldIndex) continue;
				if (rebuilt == null) rebuilt = new Array<>(current);
				Timeline replacement = cloneTimeline(timeline, newIndex);
				graphMigrations.put(timeline, replacement);
				rebuilt.set(timelineIndex, replacement);
			}
			if (rebuilt != null) {
				doc.migrateGraphViewStates(animation.getName(), graphMigrations);
				changedAnimations.add(animation);
				rebuiltAnimations.add(rebuilt);
			}
		}

		for (int index = 0; index < changedAnimations.size(); index++) {
			Animation animation = changedAnimations.get(index);
			animation.setTimelines(rebuiltAnimations.get(index), new IntArray(animation.getBones()));
		}
	}

	/** Rebinds attachment-specific timelines when an immutable attachment object must be replaced. */
	public void replaceAttachmentTimelineTarget(Attachment source, Attachment replacement) {
		if (source == null || replacement == null || source == replacement)
			throw new IllegalArgumentException("Attachment timeline replacement is invalid");

		ArrayList<Animation> changedAnimations = new ArrayList<>();
		ArrayList<Array<Timeline>> rebuiltAnimations = new ArrayList<>();
		for (Animation animation : doc.data.getAnimations()) {
			Array<Timeline> current = animation.getTimelines();
			Array<Timeline> rebuilt = null;
			IdentityHashMap<Timeline, Timeline> graphMigrations = new IdentityHashMap<>();
			for (int timelineIndex = 0; timelineIndex < current.size; timelineIndex++) {
				Timeline timeline = current.get(timelineIndex);
				boolean targetsSource = timeline instanceof DeformTimeline deform
					&& deform.getAttachment() == source
					|| timeline instanceof SequenceTimeline sequence && sequence.getAttachment() == source;
				if (!targetsSource) continue;
				if (timeline instanceof DeformTimeline && !(replacement instanceof VertexAttachment))
					throw new IllegalArgumentException("A Deform timeline requires a vertex attachment replacement");
				if (rebuilt == null) rebuilt = new Array<>(current);
				Timeline rebound = cloneTimeline(timeline, null, replacement);
				graphMigrations.put(timeline, rebound);
				rebuilt.set(timelineIndex, rebound);
			}
			if (rebuilt != null) {
				doc.migrateGraphViewStates(animation.getName(), graphMigrations);
				changedAnimations.add(animation);
				rebuiltAnimations.add(rebuilt);
			}
		}

		for (int index = 0; index < changedAnimations.size(); index++) {
			Animation animation = changedAnimations.get(index);
			animation.setTimelines(rebuiltAnimations.get(index), new IntArray(animation.getBones()));
		}
	}

	private Timeline newTimelineLike(Timeline source, int frameCount, int bezierCount) {
		if (source instanceof DrawOrderFolderTimeline folder)
			return new DrawOrderFolderTimeline(frameCount, folder.getSlots().clone(), doc.data.getSlots().size);
		if (source instanceof DrawOrderTimeline) return new DrawOrderTimeline(frameCount);
		if (source instanceof EventTimeline) return new EventTimeline(frameCount);
		if (source instanceof AttachmentTimeline attachment)
			return new AttachmentTimeline(frameCount, attachment.getSlotIndex());
		if (source instanceof InheritTimeline inherit)
			return new InheritTimeline(frameCount, inherit.getBoneIndex());
		if (source instanceof SequenceTimeline sequence)
			return new SequenceTimeline(frameCount, sequence.getSlotIndex(), sequence.getAttachment());
		if (source instanceof PhysicsConstraintResetTimeline reset)
			return new PhysicsConstraintResetTimeline(frameCount, reset.getConstraintIndex());
		if (source instanceof DeformTimeline deform)
			return new DeformTimeline(frameCount, bezierCount, deform.getSlotIndex(), deform.getAttachment());
		if (source instanceof IkConstraintTimeline ik)
			return new IkConstraintTimeline(frameCount, bezierCount, ik.getConstraintIndex());
		if (source instanceof TransformConstraintTimeline transform)
			return new TransformConstraintTimeline(frameCount, bezierCount, transform.getConstraintIndex());
		if (source instanceof PhysicsConstraintTimeline physics)
			return newPhysicsValueTimeline(physicsPropOf(physics), frameCount, bezierCount,
				physics.getConstraintIndex());
		if (source instanceof PathConstraintPositionTimeline position)
			return new PathConstraintPositionTimeline(frameCount, bezierCount, position.getConstraintIndex());
		if (source instanceof PathConstraintSpacingTimeline spacing)
			return new PathConstraintSpacingTimeline(frameCount, bezierCount, spacing.getConstraintIndex());
		if (source instanceof PathConstraintMixTimeline mix)
			return new PathConstraintMixTimeline(frameCount, bezierCount, mix.getConstraintIndex());
		if (source instanceof SliderTimeline slider)
			return new SliderTimeline(frameCount, bezierCount, slider.getConstraintIndex());
		if (source instanceof SliderMixTimeline sliderMix)
			return new SliderMixTimeline(frameCount, bezierCount, sliderMix.getConstraintIndex());
		if (isTwoColorTimeline(source))
			return newTwoColorTimeline(source instanceof RGBA2Timeline, frameCount, bezierCount,
				((SlotCurveTimeline)source).getSlotIndex());
		if (source instanceof RGBATimeline color)
			return new RGBATimeline(frameCount, bezierCount, color.getSlotIndex());
		if (source instanceof RGBTimeline color)
			return new RGBTimeline(frameCount, bezierCount, color.getSlotIndex());
		if (source instanceof AlphaTimeline alpha)
			return new AlphaTimeline(frameCount, bezierCount, alpha.getSlotIndex());
		if (source instanceof BoneTimeline boneTimeline) {
			Prop prop = propOf(source);
			if (prop != null) return newTimeline(prop, boneTimeline.getBoneIndex(), frameCount, bezierCount);
		}
		throw new IllegalArgumentException("不支持复制该轨道的曲线：" + source.getClass().getSimpleName());
	}

	private void copyEditableFrame(Timeline source, int sourceFrame, Timeline target, int targetFrame) {
		if (source instanceof DrawOrderFolderTimeline folder
			&& target instanceof DrawOrderFolderTimeline folderTarget) {
			int[] order = drawOrderFolderAt(folder, sourceFrame);
			folderTarget.setFrame(targetFrame, frameTime(folder, sourceFrame), order == null ? null : order.clone());
		} else if (source instanceof DrawOrderTimeline order && target instanceof DrawOrderTimeline orderTarget) {
			int[] value = drawOrderAt(order, sourceFrame);
			orderTarget.setFrame(targetFrame, frameTime(order, sourceFrame), value == null ? null : value.clone());
		} else if (source instanceof EventTimeline events && target instanceof EventTimeline eventTarget) {
			Event event = eventAt(events, sourceFrame);
			eventTarget.setFrame(targetFrame, copyEvent(event, event.getTime()));
		} else if (source instanceof AttachmentTimeline attachment
			&& target instanceof AttachmentTimeline attachmentTarget) {
			attachmentTarget.setFrame(targetFrame, frameTime(attachment, sourceFrame),
				attachment.getAttachmentNames()[sourceFrame]);
		} else if (source instanceof InheritTimeline inherit && target instanceof InheritTimeline inheritTarget) {
			inheritTarget.setFrame(targetFrame, frameTime(inherit, sourceFrame), inheritAt(inherit, sourceFrame));
		} else if (source instanceof SequenceTimeline sequence && target instanceof SequenceTimeline sequenceTarget) {
			copySequenceFrame(sequence, sourceFrame, sequenceTarget, targetFrame);
		} else if (source instanceof PhysicsConstraintResetTimeline reset
			&& target instanceof PhysicsConstraintResetTimeline resetTarget) {
			resetTarget.setFrame(targetFrame, frameTime(reset, sourceFrame));
		} else if (source instanceof DeformTimeline deform && target instanceof DeformTimeline deformTarget) {
			deformTarget.setFrame(targetFrame, frameTime(deform, sourceFrame), deform.getVertices()[sourceFrame].clone());
		} else if (source instanceof IkConstraintTimeline ik && target instanceof IkConstraintTimeline ikTarget) {
			copyIkFrame(ik, sourceFrame, ikTarget, targetFrame);
		} else if (source instanceof TransformConstraintTimeline transform
			&& target instanceof TransformConstraintTimeline transformTarget) {
			copyTransformFrame(transform, sourceFrame, transformTarget, targetFrame);
		} else if (source instanceof PhysicsConstraintTimeline physics
			&& target instanceof PhysicsConstraintTimeline) {
			copyPhysicsValueFrame(physics, sourceFrame, target, targetFrame);
		} else if ((source instanceof PathConstraintPositionTimeline
			|| source instanceof PathConstraintSpacingTimeline)
			&& (target instanceof PathConstraintPositionTimeline
				|| target instanceof PathConstraintSpacingTimeline)) {
			copyPathValueFrame(source, sourceFrame, target, targetFrame);
		} else if (source instanceof PathConstraintMixTimeline mix
			&& target instanceof PathConstraintMixTimeline mixTarget) {
			copyPathMixFrame(mix, sourceFrame, mixTarget, targetFrame);
		} else if (sliderPropOf(source) != null && sliderPropOf(source) == sliderPropOf(target)) {
			copySliderFrame(source, sourceFrame, target, targetFrame);
		} else if (isTwoColorTimeline(source)) {
			copyTwoColorFrame(source, sourceFrame, target, targetFrame);
		} else if (source instanceof RGBATimeline color && target instanceof RGBATimeline colorTarget) {
			copyColorFrame(color, sourceFrame, colorTarget, targetFrame);
		} else if (source instanceof RGBTimeline color && target instanceof RGBTimeline colorTarget) {
			copyRgbFrame(color, sourceFrame, colorTarget, targetFrame);
		} else if (source instanceof AlphaTimeline alpha && target instanceof AlphaTimeline alphaTarget) {
			copyAlphaFrame(alpha, sourceFrame, alphaTarget, targetFrame);
		} else if (source instanceof BoneTimeline && target instanceof BoneTimeline) {
			setFrame(target, targetFrame, frameTime(source, sourceFrame), frameValue(source, sourceFrame, 0),
				frameValue(source, sourceFrame, 1));
		} else {
			throw new IllegalArgumentException("曲线轨道类型不兼容");
		}
	}

	private void copyEditableFrameAtTime(Timeline source, int sourceFrame, Timeline target, int targetFrame,
		float time) {
		copyEditableFrame(source, sourceFrame, target, targetFrame);
		if (target instanceof EventTimeline events) {
			Event event = eventAt(events, targetFrame);
			events.setFrame(targetFrame, copyEvent(event, time));
		} else {
			target.getFrames()[targetFrame * target.getFrameEntries()] = time;
		}
	}

	private static boolean timelinesEqual(Timeline left, Timeline right) {
		if (left == right) return true;
		if (left == null || right == null || left.getClass() != right.getClass()) return false;
		if (!java.util.Arrays.equals(left.getFrames(), right.getFrames())) return false;
		if (left instanceof DrawOrderTimeline leftOrder && right instanceof DrawOrderTimeline rightOrder)
			return java.util.Arrays.deepEquals(leftOrder.getDrawOrders(), rightOrder.getDrawOrders());
		if (left instanceof DrawOrderFolderTimeline leftFolder
			&& right instanceof DrawOrderFolderTimeline rightFolder)
			return java.util.Arrays.equals(leftFolder.getSlots(), rightFolder.getSlots())
				&& java.util.Arrays.deepEquals(leftFolder.getDrawOrders(), rightFolder.getDrawOrders());
		if (left instanceof AttachmentTimeline leftAttachment && right instanceof AttachmentTimeline rightAttachment)
			return java.util.Arrays.equals(leftAttachment.getAttachmentNames(), rightAttachment.getAttachmentNames());
		if (left instanceof EventTimeline leftEvents && right instanceof EventTimeline rightEvents) {
			for (int frame = 0; frame < leftEvents.getFrameCount(); frame++) {
				if (!eventsEqual(leftEvents.getEvents()[frame], rightEvents.getEvents()[frame])) return false;
			}
		}
		if (left instanceof DeformTimeline leftDeform && right instanceof DeformTimeline rightDeform)
			return leftDeform.getAttachment() == rightDeform.getAttachment()
				&& leftDeform.getSlotIndex() == rightDeform.getSlotIndex()
				&& java.util.Arrays.deepEquals(leftDeform.getVertices(), rightDeform.getVertices())
				&& (!(left instanceof CurveTimeline leftCurve) || !(right instanceof CurveTimeline rightCurve)
					|| java.util.Arrays.equals(curveData(leftCurve), curveData(rightCurve)));
		if (left instanceof SequenceTimeline leftSequence && right instanceof SequenceTimeline rightSequence)
			return leftSequence.getAttachment() == rightSequence.getAttachment()
				&& leftSequence.getSlotIndex() == rightSequence.getSlotIndex();
		if (left instanceof CurveTimeline leftCurve && right instanceof CurveTimeline rightCurve)
			return java.util.Arrays.equals(curveData(leftCurve), curveData(rightCurve));
		return true;
	}

	private static boolean eventsEqual(Event left, Event right) {
		return left == right || left != null && right != null && left.getData() == right.getData()
			&& Float.compare(left.getTime(), right.getTime()) == 0 && left.getInt() == right.getInt()
			&& Float.compare(left.getFloat(), right.getFloat()) == 0
			&& java.util.Objects.equals(left.getString(), right.getString())
			&& Float.compare(left.getVolume(), right.getVolume()) == 0
			&& Float.compare(left.getBalance(), right.getBalance()) == 0;
	}

	private static Field findCurvesField() {
		try {
			Field field = CurveTimeline.class.getDeclaredField("curves");
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException error) {
			throw new ExceptionInInitializerError(error);
		}
	}

	private static float[] curveData(CurveTimeline timeline) {
		try {
			return (float[])CURVES_FIELD.get(timeline);
		} catch (IllegalAccessException error) {
			throw new IllegalStateException("无法读取 Spine 曲线数据", error);
		}
	}

	/**
	 * 在动画中为骨骼属性增加一个关键帧（time 已存在的帧则更新值）。
	 * 需要重建 timeline（final frames）。
	 */
	public void addKeyframe(Animation anim, Prop prop, int boneIndex, float time, float value1, float value2) {
		requireValidKeyframe(prop, time, value1, value2);
		Animation.Timeline tl = findTimeline(anim, prop, boneIndex);
		if (tl == null) return;
		int old = frameCount(tl);
		for (int i = 0; i < old; i++) {
			if (sameFrameTime(frameTime(tl, i), time)) {
				boolean same = Float.compare(frameTime(tl, i), time) == 0
					&& Float.compare(frameValue(tl, i, 0), value1) == 0
					&& (!(tl instanceof BoneTimeline2) || Float.compare(frameValue(tl, i, 1), value2) == 0);
				if (same) return;
				if (!hasBezier(tl)) {
					setFrame(tl, i, time, value1, value2);
					refreshAnimation(anim);
					doc.markDirty();
					return;
				}
				int[] sourceFrames = new int[old];
				int[] curveTypes = new int[old];
				Animation.Timeline rebuilt = newTimeline(prop, boneIndex, old, bezierCapacity(tl));
				for (int frame = 0; frame < old; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(tl, frame);
					if (frame == i) setFrame(rebuilt, frame, time, value1, value2);
					else setFrame(rebuilt, frame, frameTime(tl, frame), frameValue(tl, frame, 0), frameValue(tl, frame, 1));
				}
				copyCurves(tl, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(anim, tl, rebuilt);
				doc.markDirty();
				return;
			}
		}
		int newCount = old + 1;
		// 重建：找到插入点
		int ins = 0;
		while (ins < old && frameTime(tl, ins) < time) ins++;
		Animation.Timeline rebuilt = newTimeline(prop, boneIndex, newCount, bezierCapacity(tl));
		int[] sourceFrames = new int[newCount];
		int[] curveTypes = new int[newCount];
		java.util.Arrays.fill(sourceFrames, -1);
		// 复制旧帧（重排）
		int src = 0;
		boolean inserted = false;
		for (int dst = 0; dst < newCount; dst++) {
			if (!inserted && dst == ins) {
				setFrame(rebuilt, dst, time, value1, value2);
				inserted = true;
			} else {
				setFrame(rebuilt, dst, frameTime(tl, src), frameValue(tl, src, 0), frameValue(tl, src, 1));
				sourceFrames[dst] = src;
				curveTypes[dst] = sourceCurveType(tl, src);
				src++;
			}
		}
		copyCurves(tl, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(anim, tl, rebuilt);
		doc.markDirty();
	}

	/**
	 * Atomically edits an existing keyframe and returns its new sorted index.
	 * A move to an occupied time is rejected instead of silently replacing data.
	 */
	public int editKeyframe(Animation anim, Prop prop, int boneIndex, float oldTime,
		float newTime, float value1, float value2, boolean stepped) {
		requireValidKeyframe(prop, newTime, value1, value2);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		Animation.Timeline timeline = findTimeline(anim, prop, boneIndex);
		if (timeline == null) throw new IllegalArgumentException("找不到要编辑的关键帧轨道");

		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");
		}

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		float[] values1 = new float[count];
		float[] values2 = new float[count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			values1[frame] = frameValue(timeline, frame, 0);
			values2[frame] = frameValue(timeline, frame, 1);
			curves[frame] = frame < count - 1 ? curveType(timeline, frame) : CurveTimeline.LINEAR;
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		values1[editedFrame] = value1;
		values2[editedFrame] = value2;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;

		// Keep each key's outgoing linear/stepped interpolation attached while sorting.
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(values1, j, j - 1);
				swap(values2, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}

		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++) {
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| Float.compare(values1[frame], frameValue(timeline, frame, 0)) != 0
				|| Float.compare(values2[frame], frameValue(timeline, frame, 1)) != 0
				|| (frame < count - 1 && curves[frame] != curveType(timeline, frame));
		}
		if (!changed) return newFrame;

		Animation.Timeline rebuilt = newTimeline(prop, boneIndex, count, bezierCapacity(timeline));
		for (int frame = 0; frame < count; frame++) {
			setFrame(rebuilt, frame, times[frame], values1[frame], values2[frame]);
		}
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(anim, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	private void requireValidKeyframe(Prop prop, float time, float value1, float value2) {
		if (prop == null) throw new IllegalArgumentException("关键帧属性不能为空");
		if (!Float.isFinite(time) || time < 0f) throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
		if (!Float.isFinite(value1) || !Float.isFinite(value2))
			throw new IllegalArgumentException("关键帧数值必须是有限数值");
	}

	private static void swap(float[] values, int a, int b) {
		float value = values[a];
		values[a] = values[b];
		values[b] = value;
	}

	private static void swap(int[] values, int a, int b) {
		int value = values[a];
		values[a] = values[b];
		values[b] = value;
	}

	private static void swap(int[][] values, int a, int b) {
		int[] value = values[a];
		values[a] = values[b];
		values[b] = value;
	}

	private static void swap(String[] values, int a, int b) {
		String value = values[a];
		values[a] = values[b];
		values[b] = value;
	}

	private static void swap(BoneData.Inherit[] values, int a, int b) {
		BoneData.Inherit value = values[a];
		values[a] = values[b];
		values[b] = value;
	}

	private static void swap(IkKeyframe[] values, int a, int b) {
		IkKeyframe value = values[a];
		values[a] = values[b];
		values[b] = value;
	}

	private static void swap(TransformKeyframe[] values, int a, int b) {
		TransformKeyframe value = values[a];
		values[a] = values[b];
		values[b] = value;
	}

	private static void swap(PathMixKeyframe[] values, int a, int b) {
		PathMixKeyframe value = values[a];
		values[a] = values[b];
		values[b] = value;
	}

	private static void swap(float[][] values, int a, int b) {
		float[] value = values[a];
		values[a] = values[b];
		values[b] = value;
	}

	private static boolean allFinite(float... values) {
		for (float value : values) if (!Float.isFinite(value)) return false;
		return true;
	}

	// ================= 骨骼变换继承关键帧 =================

	/** Adds or replaces a discrete transform-inheritance key and returns its sorted frame index. */
	public int addInheritKeyframe(Animation animation, int boneIndex, float time, BoneData.Inherit inherit) {
		requireValidBoneEdit(animation, boneIndex, time, inherit);
		InheritTimeline timeline = findInheritTimeline(animation, boneIndex);
		if (timeline == null) {
			InheritTimeline created = new InheritTimeline(1, boneIndex);
			created.setFrame(0, time, inherit);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (inheritAt(timeline, existing) == inherit) return existing;
			timeline.setFrame(existing, time, inherit);
			refreshAnimation(animation);
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		InheritTimeline rebuilt = new InheritTimeline(oldCount + 1, boneIndex);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) rebuilt.setFrame(target, time, inherit);
			else {
				rebuilt.setFrame(target, frameTime(timeline, source), inheritAt(timeline, source));
				source++;
			}
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	/** Atomically changes an inheritance key's time and mode. */
	public int editInheritKeyframe(Animation animation, int boneIndex, float oldTime,
		float newTime, BoneData.Inherit inherit) {
		requireValidBoneEdit(animation, boneIndex, newTime, inherit);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		InheritTimeline timeline = findInheritTimeline(animation, boneIndex);
		if (timeline == null) throw new IllegalArgumentException("找不到继承关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的继承关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");
		}

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		BoneData.Inherit[] modes = new BoneData.Inherit[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			modes[frame] = inheritAt(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		modes[editedFrame] = inherit;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(modes, j, j - 1);
				swap(identities, j, j - 1);
			}
		}

		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| modes[frame] != inheritAt(timeline, frame);
		if (!changed) return newFrame;

		InheritTimeline rebuilt = new InheritTimeline(count, boneIndex);
		for (int frame = 0; frame < count; frame++) rebuilt.setFrame(frame, times[frame], modes[frame]);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeInheritKeyframe(Animation animation, int boneIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		InheritTimeline timeline = findInheritTimeline(animation, boneIndex);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}

		InheritTimeline rebuilt = new InheritTimeline(timeline.getFrameCount() - 1, boneIndex);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			rebuilt.setFrame(target, frameTime(timeline, source), inheritAt(timeline, source));
			source++;
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void requireValidBoneEdit(Animation animation, int boneIndex, float time, BoneData.Inherit inherit) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画数据无效");
		if (boneIndex < 0 || boneIndex >= doc.data.getBones().size)
			throw new IllegalArgumentException("关键帧引用的骨骼不存在");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
		if (inherit == null) throw new IllegalArgumentException("继承模式不能为空");
	}

	// ================= 槽附件关键帧 =================

	/** Adds or replaces a discrete attachment key and returns its sorted frame index. */
	public int addAttachmentKeyframe(Animation animation, int slotIndex, float time, String attachmentName) {
		requireValidSlotEdit(animation, slotIndex, time);
		String name = normalizeAttachmentName(attachmentName);
		AttachmentTimeline timeline = findAttachmentTimeline(animation, slotIndex);
		if (timeline == null) {
			AttachmentTimeline created = new AttachmentTimeline(1, slotIndex);
			created.setFrame(0, time, name);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (java.util.Objects.equals(timeline.getAttachmentNames()[existing], name)) return existing;
			timeline.setFrame(existing, time, name);
			refreshAnimation(animation);
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		AttachmentTimeline rebuilt = new AttachmentTimeline(oldCount + 1, slotIndex);
		int source = 0;
		for (int target = 0; target < oldCount + 1; target++) {
			if (target == insert) rebuilt.setFrame(target, time, name);
			else {
				rebuilt.setFrame(target, frameTime(timeline, source), timeline.getAttachmentNames()[source]);
				source++;
			}
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	/** Atomically changes an attachment key's time and value. */
	public int editAttachmentKeyframe(Animation animation, int slotIndex, float oldTime,
		float newTime, String attachmentName) {
		requireValidSlotEdit(animation, slotIndex, newTime);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		AttachmentTimeline timeline = findAttachmentTimeline(animation, slotIndex);
		if (timeline == null) throw new IllegalArgumentException("找不到附件关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的附件关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");
		}

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		String[] names = new String[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			names[frame] = timeline.getAttachmentNames()[frame];
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		names[editedFrame] = normalizeAttachmentName(attachmentName);
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(names, j, j - 1);
				swap(identities, j, j - 1);
			}
		}

		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++) {
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| !java.util.Objects.equals(names[frame], timeline.getAttachmentNames()[frame]);
		}
		if (!changed) return newFrame;

		AttachmentTimeline rebuilt = new AttachmentTimeline(count, slotIndex);
		for (int frame = 0; frame < count; frame++) rebuilt.setFrame(frame, times[frame], names[frame]);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeAttachmentKeyframe(Animation animation, int slotIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		AttachmentTimeline timeline = findAttachmentTimeline(animation, slotIndex);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}

		AttachmentTimeline rebuilt = new AttachmentTimeline(timeline.getFrameCount() - 1, slotIndex);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			rebuilt.setFrame(target, frameTime(timeline, source), timeline.getAttachmentNames()[source]);
			source++;
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void requireValidSlotEdit(Animation animation, int slotIndex, float time) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画数据无效");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
		SlotData slot = null;
		for (SlotData candidate : doc.data.getSlots()) {
			if (candidate.getIndex() == slotIndex) { slot = candidate; break; }
		}
		if (slot == null) throw new IllegalArgumentException("关键帧引用的槽不存在");
	}

	private static String normalizeAttachmentName(String attachmentName) {
		return attachmentName == null || attachmentName.isBlank() ? null : attachmentName;
	}

	// ================= 槽颜色关键帧 =================

	public int addColorKeyframe(Animation animation, int slotIndex, float time,
		float red, float green, float blue, float alpha) {
		requireValidSlotEdit(animation, slotIndex, time);
		requireValidColor(red, green, blue, alpha);
		if (findRgbTimeline(animation, slotIndex) != null || findAlphaTimeline(animation, slotIndex) != null)
			throw new IllegalStateException("该槽已有拆分的 RGB 或透明度轨道，不能创建 RGBA 轨道");
		if (findTwoColorTimeline(animation, slotIndex) != null)
			throw new IllegalStateException("该槽已有双色染色轨道，不能同时创建普通颜色轨道");
		RGBATimeline timeline = findColorTimeline(animation, slotIndex);
		if (timeline == null) {
			RGBATimeline created = new RGBATimeline(1, 0, slotIndex);
			created.setFrame(0, time, red, green, blue, alpha);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (sameColor(timeline, existing, red, green, blue, alpha)) return existing;
			if (!hasBezier(timeline)) {
				setColorFrame(timeline, existing, time, red, green, blue, alpha);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				RGBATimeline rebuilt = new RGBATimeline(count, bezierCapacity(timeline), slotIndex);
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) setColorFrame(rebuilt, frame, time, red, green, blue, alpha);
					else copyColorFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		RGBATimeline rebuilt = new RGBATimeline(oldCount + 1, bezierCapacity(timeline), slotIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) {
				setColorFrame(rebuilt, target, time, red, green, blue, alpha);
			} else {
				copyColorFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editColorKeyframe(Animation animation, int slotIndex, float oldTime, float newTime,
		float red, float green, float blue, float alpha, boolean stepped) {
		requireValidSlotEdit(animation, slotIndex, newTime);
		requireValidColor(red, green, blue, alpha);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		RGBATimeline timeline = findColorTimeline(animation, slotIndex);
		if (timeline == null) throw new IllegalArgumentException("找不到颜色关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的颜色关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");
		}

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		float[][] colors = new float[4][count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			for (int channel = 0; channel < 4; channel++) colors[channel][frame] = colorValue(timeline, frame, channel);
			curves[frame] = frame < count - 1 ? curveType(timeline, frame) : CurveTimeline.LINEAR;
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		colors[0][editedFrame] = red;
		colors[1][editedFrame] = green;
		colors[2][editedFrame] = blue;
		colors[3][editedFrame] = alpha;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				for (float[] channel : colors) swap(channel, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}

		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++) {
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| (frame < count - 1 && curves[frame] != curveType(timeline, frame));
			for (int channel = 0; channel < 4 && !changed; channel++)
				changed = Float.compare(colors[channel][frame], colorValue(timeline, frame, channel)) != 0;
		}
		if (!changed) return newFrame;

		RGBATimeline rebuilt = new RGBATimeline(count, bezierCapacity(timeline), slotIndex);
		for (int frame = 0; frame < count; frame++) {
			setColorFrame(rebuilt, frame, times[frame], colors[0][frame], colors[1][frame],
				colors[2][frame], colors[3][frame]);
		}
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeColorKeyframe(Animation animation, int slotIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		RGBATimeline timeline = findColorTimeline(animation, slotIndex);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}

		RGBATimeline rebuilt = new RGBATimeline(timeline.getFrameCount() - 1, bezierCapacity(timeline), slotIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyColorFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private static void setColorFrame(RGBATimeline timeline, int frame, float time,
		float red, float green, float blue, float alpha) {
		timeline.setFrame(frame, time, red, green, blue, alpha);
	}

	private void copyColorFrame(RGBATimeline source, int sourceFrame, RGBATimeline target, int targetFrame) {
		setColorFrame(target, targetFrame, frameTime(source, sourceFrame), colorValue(source, sourceFrame, 0),
			colorValue(source, sourceFrame, 1), colorValue(source, sourceFrame, 2), colorValue(source, sourceFrame, 3));
	}

	private boolean sameColor(RGBATimeline timeline, int frame, float red, float green, float blue, float alpha) {
		return Float.compare(colorValue(timeline, frame, 0), red) == 0
			&& Float.compare(colorValue(timeline, frame, 1), green) == 0
			&& Float.compare(colorValue(timeline, frame, 2), blue) == 0
			&& Float.compare(colorValue(timeline, frame, 3), alpha) == 0;
	}

	private static void requireValidColor(float red, float green, float blue, float alpha) {
		for (float channel : new float[] {red, green, blue, alpha}) {
			if (!Float.isFinite(channel) || channel < 0f || channel > 1f)
				throw new IllegalArgumentException("颜色通道必须是 0 到 1 的有限数值");
		}
	}

	// ================= 槽 RGB 与透明度关键帧 =================

	public int addRgbKeyframe(Animation animation, int slotIndex, float time,
		float red, float green, float blue) {
		requireValidSlotEdit(animation, slotIndex, time);
		requireValidRgb(red, green, blue);
		if (findColorTimeline(animation, slotIndex) != null || findTwoColorTimeline(animation, slotIndex) != null)
			throw new IllegalStateException("该槽已有占用 RGB 的颜色轨道");
		RGBTimeline timeline = findRgbTimeline(animation, slotIndex);
		if (timeline == null) {
			RGBTimeline created = new RGBTimeline(1, 0, slotIndex);
			created.setFrame(0, time, red, green, blue);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (sameRgb(timeline, existing, red, green, blue)) return existing;
			if (!hasBezier(timeline)) {
				timeline.setFrame(existing, time, red, green, blue);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				RGBTimeline rebuilt = new RGBTimeline(count, bezierCapacity(timeline), slotIndex);
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) rebuilt.setFrame(frame, time, red, green, blue);
					else copyRgbFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		RGBTimeline rebuilt = new RGBTimeline(oldCount + 1, bezierCapacity(timeline), slotIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) rebuilt.setFrame(target, time, red, green, blue);
			else {
				copyRgbFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editRgbKeyframe(Animation animation, int slotIndex, float oldTime, float newTime,
		float red, float green, float blue, boolean stepped) {
		requireValidSlotEdit(animation, slotIndex, newTime);
		requireValidRgb(red, green, blue);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		RGBTimeline timeline = findRgbTimeline(animation, slotIndex);
		if (timeline == null) throw new IllegalArgumentException("找不到 RGB 关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的 RGB 关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		float[][] colors = new float[3][count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			for (int channel = 0; channel < colors.length; channel++)
				colors[channel][frame] = rgbValue(timeline, frame, channel);
			curves[frame] = frame < count - 1 ? curveType(timeline, frame) : CurveTimeline.LINEAR;
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		colors[0][editedFrame] = red;
		colors[1][editedFrame] = green;
		colors[2][editedFrame] = blue;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				for (float[] channel : colors) swap(channel, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}

		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++) {
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| (frame < count - 1 && curves[frame] != curveType(timeline, frame));
			for (int channel = 0; channel < colors.length && !changed; channel++)
				changed = Float.compare(colors[channel][frame], rgbValue(timeline, frame, channel)) != 0;
		}
		if (!changed) return newFrame;

		RGBTimeline rebuilt = new RGBTimeline(count, bezierCapacity(timeline), slotIndex);
		for (int frame = 0; frame < count; frame++)
			rebuilt.setFrame(frame, times[frame], colors[0][frame], colors[1][frame], colors[2][frame]);
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeRgbKeyframe(Animation animation, int slotIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		RGBTimeline timeline = findRgbTimeline(animation, slotIndex);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		RGBTimeline rebuilt = new RGBTimeline(timeline.getFrameCount() - 1, bezierCapacity(timeline), slotIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyRgbFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	public int addAlphaKeyframe(Animation animation, int slotIndex, float time, float alpha) {
		requireValidSlotEdit(animation, slotIndex, time);
		requireValidAlpha(alpha);
		if (findColorTimeline(animation, slotIndex) != null
			|| findTwoColorTimeline(animation, slotIndex) instanceof RGBA2Timeline)
			throw new IllegalStateException("该槽已有占用透明度的颜色轨道");
		AlphaTimeline timeline = findAlphaTimeline(animation, slotIndex);
		if (timeline == null) {
			AlphaTimeline created = new AlphaTimeline(1, 0, slotIndex);
			created.setFrame(0, time, alpha);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (Float.compare(alphaValue(timeline, existing), alpha) == 0) return existing;
			if (!hasBezier(timeline)) {
				timeline.setFrame(existing, time, alpha);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				AlphaTimeline rebuilt = new AlphaTimeline(count, bezierCapacity(timeline), slotIndex);
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) rebuilt.setFrame(frame, time, alpha);
					else copyAlphaFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		AlphaTimeline rebuilt = new AlphaTimeline(oldCount + 1, bezierCapacity(timeline), slotIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) rebuilt.setFrame(target, time, alpha);
			else {
				copyAlphaFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editAlphaKeyframe(Animation animation, int slotIndex, float oldTime, float newTime,
		float alpha, boolean stepped) {
		requireValidSlotEdit(animation, slotIndex, newTime);
		requireValidAlpha(alpha);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		AlphaTimeline timeline = findAlphaTimeline(animation, slotIndex);
		if (timeline == null) throw new IllegalArgumentException("找不到透明度关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的透明度关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		float[] values = new float[count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			values[frame] = alphaValue(timeline, frame);
			curves[frame] = frame < count - 1 ? curveType(timeline, frame) : CurveTimeline.LINEAR;
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		values[editedFrame] = alpha;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(values, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}

		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| Float.compare(values[frame], alphaValue(timeline, frame)) != 0
				|| (frame < count - 1 && curves[frame] != curveType(timeline, frame));
		if (!changed) return newFrame;

		AlphaTimeline rebuilt = new AlphaTimeline(count, bezierCapacity(timeline), slotIndex);
		for (int frame = 0; frame < count; frame++) rebuilt.setFrame(frame, times[frame], values[frame]);
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeAlphaKeyframe(Animation animation, int slotIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		AlphaTimeline timeline = findAlphaTimeline(animation, slotIndex);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		AlphaTimeline rebuilt = new AlphaTimeline(timeline.getFrameCount() - 1, bezierCapacity(timeline), slotIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyAlphaFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void copyRgbFrame(RGBTimeline source, int sourceFrame, RGBTimeline target, int targetFrame) {
		target.setFrame(targetFrame, frameTime(source, sourceFrame), rgbValue(source, sourceFrame, 0),
			rgbValue(source, sourceFrame, 1), rgbValue(source, sourceFrame, 2));
	}

	private void copyAlphaFrame(AlphaTimeline source, int sourceFrame, AlphaTimeline target, int targetFrame) {
		target.setFrame(targetFrame, frameTime(source, sourceFrame), alphaValue(source, sourceFrame));
	}

	private boolean sameRgb(RGBTimeline timeline, int frame, float red, float green, float blue) {
		return Float.compare(rgbValue(timeline, frame, 0), red) == 0
			&& Float.compare(rgbValue(timeline, frame, 1), green) == 0
			&& Float.compare(rgbValue(timeline, frame, 2), blue) == 0;
	}

	private static void requireValidAlpha(float alpha) {
		if (!Float.isFinite(alpha) || alpha < 0f || alpha > 1f)
			throw new IllegalArgumentException("透明度必须是 0 到 1 的有限数值");
	}

	// ================= 槽双色染色关键帧 =================

	public int addTwoColorKeyframe(Animation animation, int slotIndex, float time,
		float lightRed, float lightGreen, float lightBlue, float lightAlpha,
		float darkRed, float darkGreen, float darkBlue, boolean includeAlpha) {
		SlotData slot = requireValidTwoColorEdit(animation, slotIndex, time);
		requireValidColor(lightRed, lightGreen, lightBlue, lightAlpha);
		requireValidRgb(darkRed, darkGreen, darkBlue);
		if (findColorTimeline(animation, slotIndex) != null || findRgbTimeline(animation, slotIndex) != null)
			throw new IllegalStateException("该槽已有普通颜色轨道，不能同时创建双色染色轨道");
		if (includeAlpha && findAlphaTimeline(animation, slotIndex) != null)
			throw new IllegalStateException("该槽已有透明度轨道，不能创建包含透明度的双色轨道");
		SlotCurveTimeline timeline = findTwoColorTimeline(animation, slotIndex);
		if (timeline == null) {
			SlotCurveTimeline created = newTwoColorTimeline(includeAlpha, 1, 0, slot.getIndex());
			setTwoColorFrame(created, 0, time, lightRed, lightGreen, lightBlue, lightAlpha,
				darkRed, darkGreen, darkBlue);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}
		if (!twoColorHasAlpha(timeline)) lightAlpha = 1f;
		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (sameTwoColor(timeline, existing, lightRed, lightGreen, lightBlue, lightAlpha,
				darkRed, darkGreen, darkBlue)) return existing;
			if (!hasBezier(timeline)) {
				setTwoColorFrame(timeline, existing, time, lightRed, lightGreen, lightBlue, lightAlpha,
					darkRed, darkGreen, darkBlue);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				SlotCurveTimeline rebuilt = newTwoColorTimeline(twoColorHasAlpha(timeline), count,
					bezierCapacity(timeline), slotIndex);
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing)
						setTwoColorFrame(rebuilt, frame, time, lightRed, lightGreen, lightBlue, lightAlpha,
							darkRed, darkGreen, darkBlue);
					else copyTwoColorFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		SlotCurveTimeline rebuilt = newTwoColorTimeline(twoColorHasAlpha(timeline), oldCount + 1,
			bezierCapacity(timeline), slotIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) {
				setTwoColorFrame(rebuilt, target, time, lightRed, lightGreen, lightBlue, lightAlpha,
					darkRed, darkGreen, darkBlue);
			} else {
				copyTwoColorFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editTwoColorKeyframe(Animation animation, int slotIndex, float oldTime, float newTime,
		float lightRed, float lightGreen, float lightBlue, float lightAlpha,
		float darkRed, float darkGreen, float darkBlue, boolean stepped) {
		requireValidTwoColorEdit(animation, slotIndex, newTime);
		requireValidColor(lightRed, lightGreen, lightBlue, lightAlpha);
		requireValidRgb(darkRed, darkGreen, darkBlue);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		SlotCurveTimeline timeline = findTwoColorTimeline(animation, slotIndex);
		if (timeline == null) throw new IllegalArgumentException("找不到双色染色关键帧轨道");
		if (!twoColorHasAlpha(timeline)) lightAlpha = 1f;
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的双色染色关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");
		}

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		float[][] colors = new float[7][count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			for (int channel = 0; channel < colors.length; channel++)
				colors[channel][frame] = twoColorValue(timeline, frame, channel);
			curves[frame] = frame < count - 1 ? curveType(timeline, frame) : CurveTimeline.LINEAR;
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		float[] edited = {lightRed, lightGreen, lightBlue, lightAlpha, darkRed, darkGreen, darkBlue};
		for (int channel = 0; channel < colors.length; channel++) colors[channel][editedFrame] = edited[channel];
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				for (float[] channel : colors) swap(channel, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}

		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++) {
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| (frame < count - 1 && curves[frame] != curveType(timeline, frame));
			for (int channel = 0; channel < colors.length && !changed; channel++)
				changed = Float.compare(colors[channel][frame], twoColorValue(timeline, frame, channel)) != 0;
		}
		if (!changed) return newFrame;

		SlotCurveTimeline rebuilt = newTwoColorTimeline(twoColorHasAlpha(timeline), count,
			bezierCapacity(timeline), slotIndex);
		for (int frame = 0; frame < count; frame++) {
			setTwoColorFrame(rebuilt, frame, times[frame], colors[0][frame], colors[1][frame], colors[2][frame],
				colors[3][frame], colors[4][frame], colors[5][frame], colors[6][frame]);
		}
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeTwoColorKeyframe(Animation animation, int slotIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		SlotCurveTimeline timeline = findTwoColorTimeline(animation, slotIndex);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}

		SlotCurveTimeline rebuilt = newTwoColorTimeline(twoColorHasAlpha(timeline),
			timeline.getFrameCount() - 1, bezierCapacity(timeline), slotIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyTwoColorFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private static SlotCurveTimeline newTwoColorTimeline(boolean includeAlpha, int frameCount,
		int bezierCount, int slotIndex) {
		return includeAlpha ? new RGBA2Timeline(frameCount, bezierCount, slotIndex)
			: new RGB2Timeline(frameCount, bezierCount, slotIndex);
	}

	private static void setTwoColorFrame(Timeline timeline, int frame, float time,
		float lightRed, float lightGreen, float lightBlue, float lightAlpha,
		float darkRed, float darkGreen, float darkBlue) {
		if (timeline instanceof RGBA2Timeline rgba2) {
			rgba2.setFrame(frame, time, lightRed, lightGreen, lightBlue, lightAlpha, darkRed, darkGreen, darkBlue);
		} else if (timeline instanceof RGB2Timeline rgb2) {
			rgb2.setFrame(frame, time, lightRed, lightGreen, lightBlue, darkRed, darkGreen, darkBlue);
		} else {
			throw new IllegalArgumentException("不是双色染色轨道");
		}
	}

	private void copyTwoColorFrame(Timeline source, int sourceFrame, Timeline target, int targetFrame) {
		setTwoColorFrame(target, targetFrame, frameTime(source, sourceFrame),
			twoColorValue(source, sourceFrame, 0), twoColorValue(source, sourceFrame, 1),
			twoColorValue(source, sourceFrame, 2), twoColorValue(source, sourceFrame, 3),
			twoColorValue(source, sourceFrame, 4), twoColorValue(source, sourceFrame, 5),
			twoColorValue(source, sourceFrame, 6));
	}

	private boolean sameTwoColor(Timeline timeline, int frame, float... values) {
		for (int channel = 0; channel < values.length; channel++)
			if (Float.compare(twoColorValue(timeline, frame, channel), values[channel]) != 0) return false;
		return true;
	}

	private SlotData requireValidTwoColorEdit(Animation animation, int slotIndex, float time) {
		requireValidSlotEdit(animation, slotIndex, time);
		for (SlotData slot : doc.data.getSlots()) {
			if (slot.getIndex() != slotIndex) continue;
			if (slot.getSetupPose().getDarkColor() == null)
				throw new IllegalStateException("该槽尚未启用双色染色");
			return slot;
		}
		throw new IllegalArgumentException("关键帧引用的槽不存在");
	}

	private static void requireValidRgb(float red, float green, float blue) {
		for (float channel : new float[] {red, green, blue}) {
			if (!Float.isFinite(channel) || channel < 0f || channel > 1f)
				throw new IllegalArgumentException("RGB 通道必须是 0 到 1 的有限数值");
		}
	}

	// ================= IK 约束关键帧 =================

	public int addIkKeyframe(Animation animation, int constraintIndex, float time, IkKeyframe value) {
		requireValidIkEdit(animation, constraintIndex, time, value);
		IkConstraintTimeline timeline = findIkTimeline(animation, constraintIndex);
		if (timeline == null) {
			IkConstraintTimeline created = new IkConstraintTimeline(1, 0, constraintIndex);
			setIkFrame(created, 0, time, value);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (sameIk(ikAt(timeline, existing), value)) return existing;
			if (!hasBezier(timeline)) {
				setIkFrame(timeline, existing, time, value);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				IkConstraintTimeline rebuilt = new IkConstraintTimeline(count, bezierCapacity(timeline), constraintIndex);
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) setIkFrame(rebuilt, frame, time, value);
					else copyIkFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		IkConstraintTimeline rebuilt = new IkConstraintTimeline(oldCount + 1, bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) setIkFrame(rebuilt, target, time, value);
			else {
				copyIkFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editIkKeyframe(Animation animation, int constraintIndex, float oldTime, float newTime,
		IkKeyframe value, boolean stepped) {
		requireValidIkEdit(animation, constraintIndex, newTime, value);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		IkConstraintTimeline timeline = findIkTimeline(animation, constraintIndex);
		if (timeline == null) throw new IllegalArgumentException("找不到 IK 关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的 IK 关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");
		}

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		IkKeyframe[] values = new IkKeyframe[count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			values[frame] = ikAt(timeline, frame);
			curves[frame] = sourceCurveType(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		values[editedFrame] = value;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(values, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}

		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++) {
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| !sameIk(values[frame], ikAt(timeline, frame))
				|| frame < count - 1 && curves[frame] != curveType(timeline, frame);
		}
		if (!changed) return newFrame;

		IkConstraintTimeline rebuilt = new IkConstraintTimeline(count, bezierCapacity(timeline), constraintIndex);
		for (int frame = 0; frame < count; frame++) setIkFrame(rebuilt, frame, times[frame], values[frame]);
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeIkKeyframe(Animation animation, int constraintIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		IkConstraintTimeline timeline = findIkTimeline(animation, constraintIndex);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}

		IkConstraintTimeline rebuilt = new IkConstraintTimeline(timeline.getFrameCount() - 1,
			bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyIkFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void requireValidIkEdit(Animation animation, int constraintIndex, float time, IkKeyframe value) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画数据无效");
		if (constraintIndex < 0 || constraintIndex >= doc.data.getConstraints().size
			|| !(doc.data.getConstraints().get(constraintIndex) instanceof IkConstraintData))
			throw new IllegalArgumentException("关键帧引用的 IK 约束不存在");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
		if (value == null) throw new IllegalArgumentException("IK 关键帧值不能为空");
		if (!Float.isFinite(value.mix()) || value.mix() < 0f || value.mix() > 1f)
			throw new IllegalArgumentException("IK 混合必须是 0 到 1 的有限数值");
		if (!Float.isFinite(value.softness()) || value.softness() < 0f)
			throw new IllegalArgumentException("IK 柔度必须是不小于 0 的有限数值");
		if (value.bendDirection() != -1 && value.bendDirection() != 1)
			throw new IllegalArgumentException("IK 弯曲方向必须是 -1 或 1");
	}

	private static boolean sameIk(IkKeyframe left, IkKeyframe right) {
		return left != null && right != null && Float.compare(left.mix(), right.mix()) == 0
			&& Float.compare(left.softness(), right.softness()) == 0
			&& left.bendDirection() == right.bendDirection() && left.compress() == right.compress()
			&& left.stretch() == right.stretch();
	}

	private static void setIkFrame(IkConstraintTimeline timeline, int frame, float time, IkKeyframe value) {
		timeline.setFrame(frame, time, value.mix(), value.softness(), value.bendDirection(),
			value.compress(), value.stretch());
	}

	private void copyIkFrame(IkConstraintTimeline source, int sourceFrame,
		IkConstraintTimeline target, int targetFrame) {
		setIkFrame(target, targetFrame, frameTime(source, sourceFrame), ikAt(source, sourceFrame));
	}

	// ================= Transform constraint keyframes =================

	public int addTransformKeyframe(Animation animation, int constraintIndex, float time,
		TransformKeyframe value) {
		requireValidTransformEdit(animation, constraintIndex, time, value);
		TransformConstraintTimeline timeline = findTransformTimeline(animation, constraintIndex);
		if (timeline == null) {
			TransformConstraintTimeline created = new TransformConstraintTimeline(1, 0, constraintIndex);
			setTransformFrame(created, 0, time, value);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (sameTransform(transformAt(timeline, existing), value)) return existing;
			if (!hasBezier(timeline)) {
				setTransformFrame(timeline, existing, time, value);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				TransformConstraintTimeline rebuilt = new TransformConstraintTimeline(count,
					bezierCapacity(timeline), constraintIndex);
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) setTransformFrame(rebuilt, frame, time, value);
					else copyTransformFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		TransformConstraintTimeline rebuilt = new TransformConstraintTimeline(oldCount + 1,
			bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) setTransformFrame(rebuilt, target, time, value);
			else {
				copyTransformFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editTransformKeyframe(Animation animation, int constraintIndex, float oldTime, float newTime,
		TransformKeyframe value, boolean stepped) {
		requireValidTransformEdit(animation, constraintIndex, newTime, value);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("Original key time must be finite");
		TransformConstraintTimeline timeline = findTransformTimeline(animation, constraintIndex);
		if (timeline == null) throw new IllegalArgumentException("Transform keyframe timeline was not found");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("Transform keyframe was not found");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("The target time already has a keyframe");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		TransformKeyframe[] values = new TransformKeyframe[count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			values[frame] = transformAt(timeline, frame);
			curves[frame] = sourceCurveType(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		values[editedFrame] = value;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(values, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| !sameTransform(values[frame], transformAt(timeline, frame))
				|| frame < count - 1 && curves[frame] != curveType(timeline, frame);
		if (!changed) return newFrame;

		TransformConstraintTimeline rebuilt = new TransformConstraintTimeline(count,
			bezierCapacity(timeline), constraintIndex);
		for (int frame = 0; frame < count; frame++) setTransformFrame(rebuilt, frame, times[frame], values[frame]);
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeTransformKeyframe(Animation animation, int constraintIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("Keyframe time must be finite");
		TransformConstraintTimeline timeline = findTransformTimeline(animation, constraintIndex);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		TransformConstraintTimeline rebuilt = new TransformConstraintTimeline(timeline.getFrameCount() - 1,
			bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyTransformFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void requireValidTransformEdit(Animation animation, int constraintIndex, float time,
		TransformKeyframe value) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("Animation data is invalid");
		if (constraintIndex < 0 || constraintIndex >= doc.data.getConstraints().size
			|| !(doc.data.getConstraints().get(constraintIndex) instanceof TransformConstraintData))
			throw new IllegalArgumentException("The keyframe references a missing Transform constraint");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("Keyframe time must be a finite number not less than zero");
		if (value == null) throw new IllegalArgumentException("Transform keyframe value cannot be null");
		if (!allFinite(value.mixRotate(), value.mixX(), value.mixY(), value.mixScaleX(),
			value.mixScaleY(), value.mixShearY()))
			throw new IllegalArgumentException("Transform keyframe mixes must be finite numbers");
	}

	private static boolean sameTransform(TransformKeyframe left, TransformKeyframe right) {
		return left != null && right != null
			&& Float.compare(left.mixRotate(), right.mixRotate()) == 0
			&& Float.compare(left.mixX(), right.mixX()) == 0
			&& Float.compare(left.mixY(), right.mixY()) == 0
			&& Float.compare(left.mixScaleX(), right.mixScaleX()) == 0
			&& Float.compare(left.mixScaleY(), right.mixScaleY()) == 0
			&& Float.compare(left.mixShearY(), right.mixShearY()) == 0;
	}

	private static void setTransformFrame(TransformConstraintTimeline timeline, int frame, float time,
		TransformKeyframe value) {
		timeline.setFrame(frame, time, value.mixRotate(), value.mixX(), value.mixY(), value.mixScaleX(),
			value.mixScaleY(), value.mixShearY());
	}

	private void copyTransformFrame(TransformConstraintTimeline source, int sourceFrame,
		TransformConstraintTimeline target, int targetFrame) {
		setTransformFrame(target, targetFrame, frameTime(source, sourceFrame), transformAt(source, sourceFrame));
	}

	// ================= Path constraint keyframes =================

	public int addPathValueKeyframe(Animation animation, int constraintIndex, PathProp prop,
		float time, float value) {
		requireValidPathValueEdit(animation, constraintIndex, prop, time, value);
		Timeline timeline = findPathTimeline(animation, constraintIndex, prop);
		if (timeline == null) {
			Timeline created = newPathValueTimeline(prop, 1, 0, constraintIndex);
			setPathValueFrame(created, 0, time, value);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (Float.compare(pathValueAt(timeline, existing), value) == 0) return existing;
			if (!hasBezier(timeline)) {
				setPathValueFrame(timeline, existing, time, value);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				Timeline rebuilt = newPathValueTimeline(prop, count, bezierCapacity(timeline), constraintIndex);
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) setPathValueFrame(rebuilt, frame, time, value);
					else copyPathValueFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		Timeline rebuilt = newPathValueTimeline(prop, oldCount + 1, bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) setPathValueFrame(rebuilt, target, time, value);
			else {
				copyPathValueFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editPathValueKeyframe(Animation animation, int constraintIndex, PathProp prop,
		float oldTime, float newTime, float value, boolean stepped) {
		requireValidPathValueEdit(animation, constraintIndex, prop, newTime, value);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		Timeline timeline = findPathTimeline(animation, constraintIndex, prop);
		if (timeline == null) throw new IllegalArgumentException("找不到 Path 数值关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的 Path 数值关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		float[] values = new float[count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			values[frame] = pathValueAt(timeline, frame);
			curves[frame] = sourceCurveType(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		values[editedFrame] = value;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(values, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| Float.compare(values[frame], pathValueAt(timeline, frame)) != 0
				|| frame < count - 1 && curves[frame] != curveType(timeline, frame);
		if (!changed) return newFrame;

		Timeline rebuilt = newPathValueTimeline(prop, count, bezierCapacity(timeline), constraintIndex);
		for (int frame = 0; frame < count; frame++) setPathValueFrame(rebuilt, frame, times[frame], values[frame]);
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removePathValueKeyframe(Animation animation, int constraintIndex, PathProp prop, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		if (prop == null || prop == PathProp.MIX) throw new IllegalArgumentException("Path 数值轨道属性无效");
		Timeline timeline = findPathTimeline(animation, constraintIndex, prop);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		Timeline rebuilt = newPathValueTimeline(prop, timeline.getFrameCount() - 1,
			bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyPathValueFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	public int addPathMixKeyframe(Animation animation, int constraintIndex, float time, PathMixKeyframe value) {
		requireValidPathMixEdit(animation, constraintIndex, time, value);
		PathConstraintMixTimeline timeline = (PathConstraintMixTimeline)findPathTimeline(
			animation, constraintIndex, PathProp.MIX);
		if (timeline == null) {
			PathConstraintMixTimeline created = new PathConstraintMixTimeline(1, 0, constraintIndex);
			setPathMixFrame(created, 0, time, value);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (samePathMix(pathMixAt(timeline, existing), value)) return existing;
			if (!hasBezier(timeline)) {
				setPathMixFrame(timeline, existing, time, value);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				PathConstraintMixTimeline rebuilt = new PathConstraintMixTimeline(count,
					bezierCapacity(timeline), constraintIndex);
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) setPathMixFrame(rebuilt, frame, time, value);
					else copyPathMixFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		PathConstraintMixTimeline rebuilt = new PathConstraintMixTimeline(oldCount + 1,
			bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) setPathMixFrame(rebuilt, target, time, value);
			else {
				copyPathMixFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editPathMixKeyframe(Animation animation, int constraintIndex, float oldTime, float newTime,
		PathMixKeyframe value, boolean stepped) {
		requireValidPathMixEdit(animation, constraintIndex, newTime, value);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		PathConstraintMixTimeline timeline = (PathConstraintMixTimeline)findPathTimeline(
			animation, constraintIndex, PathProp.MIX);
		if (timeline == null) throw new IllegalArgumentException("找不到 Path 混合关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的 Path 混合关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		PathMixKeyframe[] values = new PathMixKeyframe[count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			values[frame] = pathMixAt(timeline, frame);
			curves[frame] = sourceCurveType(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		values[editedFrame] = value;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(values, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| !samePathMix(values[frame], pathMixAt(timeline, frame))
				|| frame < count - 1 && curves[frame] != curveType(timeline, frame);
		if (!changed) return newFrame;

		PathConstraintMixTimeline rebuilt = new PathConstraintMixTimeline(count,
			bezierCapacity(timeline), constraintIndex);
		for (int frame = 0; frame < count; frame++) setPathMixFrame(rebuilt, frame, times[frame], values[frame]);
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removePathMixKeyframe(Animation animation, int constraintIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		PathConstraintMixTimeline timeline = (PathConstraintMixTimeline)findPathTimeline(
			animation, constraintIndex, PathProp.MIX);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		PathConstraintMixTimeline rebuilt = new PathConstraintMixTimeline(timeline.getFrameCount() - 1,
			bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyPathMixFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void requireValidPathValueEdit(Animation animation, int constraintIndex, PathProp prop,
		float time, float value) {
		requireValidPathTarget(animation, constraintIndex, time);
		if (prop == null || prop == PathProp.MIX) throw new IllegalArgumentException("Path 数值轨道属性无效");
		if (!Float.isFinite(value)) throw new IllegalArgumentException("Path 关键帧数值必须是有限数值");
	}

	private void requireValidPathMixEdit(Animation animation, int constraintIndex, float time,
		PathMixKeyframe value) {
		requireValidPathTarget(animation, constraintIndex, time);
		if (value == null) throw new IllegalArgumentException("Path 混合关键帧值不能为空");
		if (!allFinite(value.mixRotate(), value.mixX(), value.mixY()))
			throw new IllegalArgumentException("Path 混合关键帧必须是有限数值");
	}

	private void requireValidPathTarget(Animation animation, int constraintIndex, float time) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画数据无效");
		if (constraintIndex < 0 || constraintIndex >= doc.data.getConstraints().size
			|| !(doc.data.getConstraints().get(constraintIndex) instanceof PathConstraintData))
			throw new IllegalArgumentException("关键帧引用的 Path 约束不存在");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
	}

	private static Timeline newPathValueTimeline(PathProp prop, int frameCount, int bezierCount,
		int constraintIndex) {
		if (prop == PathProp.POSITION)
			return new PathConstraintPositionTimeline(frameCount, bezierCount, constraintIndex);
		if (prop == PathProp.SPACING)
			return new PathConstraintSpacingTimeline(frameCount, bezierCount, constraintIndex);
		throw new IllegalArgumentException("Path 数值轨道属性无效");
	}

	private static void setPathValueFrame(Timeline timeline, int frame, float time, float value) {
		if (!(timeline instanceof PathConstraintPositionTimeline
			|| timeline instanceof PathConstraintSpacingTimeline))
			throw new IllegalArgumentException("Path 数值轨道类型无效");
		((CurveTimeline1)timeline).setFrame(frame, time, value);
	}

	private void copyPathValueFrame(Timeline source, int sourceFrame, Timeline target, int targetFrame) {
		setPathValueFrame(target, targetFrame, frameTime(source, sourceFrame), pathValueAt(source, sourceFrame));
	}

	private static boolean samePathMix(PathMixKeyframe left, PathMixKeyframe right) {
		return left != null && right != null
			&& Float.compare(left.mixRotate(), right.mixRotate()) == 0
			&& Float.compare(left.mixX(), right.mixX()) == 0
			&& Float.compare(left.mixY(), right.mixY()) == 0;
	}

	private static void setPathMixFrame(PathConstraintMixTimeline timeline, int frame, float time,
		PathMixKeyframe value) {
		timeline.setFrame(frame, time, value.mixRotate(), value.mixX(), value.mixY());
	}

	private void copyPathMixFrame(PathConstraintMixTimeline source, int sourceFrame,
		PathConstraintMixTimeline target, int targetFrame) {
		setPathMixFrame(target, targetFrame, frameTime(source, sourceFrame), pathMixAt(source, sourceFrame));
	}

	// ================= Physics constraint keyframes =================

	public int addPhysicsValueKeyframe(Animation animation, int constraintIndex, PhysicsProp prop,
		float time, float value) {
		requireValidPhysicsValueEdit(animation, constraintIndex, prop, time, value);
		Timeline timeline = findPhysicsTimeline(animation, constraintIndex, prop);
		if (timeline == null) {
			Timeline created = newPhysicsValueTimeline(prop, 1, 0, constraintIndex);
			setPhysicsValueFrame(created, 0, time, value);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (Float.compare(physicsValueAt(timeline, existing), value) == 0) return existing;
			if (!hasBezier(timeline)) {
				setPhysicsValueFrame(timeline, existing, time, value);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				Timeline rebuilt = newPhysicsValueTimeline(prop, count, bezierCapacity(timeline), constraintIndex);
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) setPhysicsValueFrame(rebuilt, frame, time, value);
					else copyPhysicsValueFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		Timeline rebuilt = newPhysicsValueTimeline(prop, oldCount + 1, bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) setPhysicsValueFrame(rebuilt, target, time, value);
			else {
				copyPhysicsValueFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editPhysicsValueKeyframe(Animation animation, int constraintIndex, PhysicsProp prop,
		float oldTime, float newTime, float value, boolean stepped) {
		requireValidPhysicsValueEdit(animation, constraintIndex, prop, newTime, value);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		Timeline timeline = findPhysicsTimeline(animation, constraintIndex, prop);
		if (timeline == null) throw new IllegalArgumentException("找不到 Physics 数值关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的 Physics 数值关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		float[] values = new float[count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			values[frame] = physicsValueAt(timeline, frame);
			curves[frame] = sourceCurveType(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		values[editedFrame] = value;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(values, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| Float.compare(values[frame], physicsValueAt(timeline, frame)) != 0
				|| frame < count - 1 && curves[frame] != curveType(timeline, frame);
		if (!changed) return newFrame;

		Timeline rebuilt = newPhysicsValueTimeline(prop, count, bezierCapacity(timeline), constraintIndex);
		for (int frame = 0; frame < count; frame++) setPhysicsValueFrame(rebuilt, frame, times[frame], values[frame]);
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removePhysicsValueKeyframe(Animation animation, int constraintIndex, PhysicsProp prop,
		float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		if (prop == null || prop == PhysicsProp.RESET) throw new IllegalArgumentException("Physics 数值轨道属性无效");
		Timeline timeline = findPhysicsTimeline(animation, constraintIndex, prop);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		Timeline rebuilt = newPhysicsValueTimeline(prop, timeline.getFrameCount() - 1,
			bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyPhysicsValueFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	public int addPhysicsResetKeyframe(Animation animation, int constraintIndex, float time) {
		requireValidPhysicsTarget(animation, constraintIndex, PhysicsProp.RESET, time);
		PhysicsConstraintResetTimeline timeline = (PhysicsConstraintResetTimeline)findPhysicsTimeline(
			animation, constraintIndex, PhysicsProp.RESET);
		if (timeline != null) {
			int existing = findFrame(timeline, time);
			if (existing >= 0) return existing;
		}
		int oldCount = timeline == null ? 0 : timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		PhysicsConstraintResetTimeline rebuilt = new PhysicsConstraintResetTimeline(oldCount + 1, constraintIndex);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) rebuilt.setFrame(target, time);
			else rebuilt.setFrame(target, frameTime(timeline, source++));
		}
		if (timeline == null) animation.getTimelines().add(rebuilt);
		else replaceTimeline(animation, timeline, rebuilt);
		refreshAnimation(animation);
		doc.markDirty();
		return insert;
	}

	public int editPhysicsResetKeyframe(Animation animation, int constraintIndex, float oldTime, float newTime) {
		requireValidPhysicsTarget(animation, constraintIndex, PhysicsProp.RESET, newTime);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		PhysicsConstraintResetTimeline timeline = (PhysicsConstraintResetTimeline)findPhysicsTimeline(
			animation, constraintIndex, PhysicsProp.RESET);
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的 Physics Reset 关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");
		if (Float.compare(oldTime, newTime) == 0) return editedFrame;
		int count = timeline.getFrameCount();
		float[] times = timeline.getFrames().clone();
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) identities[frame] = frame;
		times[editedFrame] = newTime;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(identities, j, j - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		PhysicsConstraintResetTimeline rebuilt = new PhysicsConstraintResetTimeline(count, constraintIndex);
		for (int frame = 0; frame < count; frame++) rebuilt.setFrame(frame, times[frame]);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removePhysicsResetKeyframe(Animation animation, int constraintIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		PhysicsConstraintResetTimeline timeline = (PhysicsConstraintResetTimeline)findPhysicsTimeline(
			animation, constraintIndex, PhysicsProp.RESET);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		PhysicsConstraintResetTimeline rebuilt = new PhysicsConstraintResetTimeline(
			timeline.getFrameCount() - 1, constraintIndex);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			rebuilt.setFrame(target, frameTime(timeline, source++));
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void requireValidPhysicsValueEdit(Animation animation, int constraintIndex, PhysicsProp prop,
		float time, float value) {
		if (prop == null || prop == PhysicsProp.RESET) throw new IllegalArgumentException("Physics 数值轨道属性无效");
		requireValidPhysicsTarget(animation, constraintIndex, prop, time);
		if (!Float.isFinite(value)) throw new IllegalArgumentException("Physics 关键帧数值必须是有限数值");
		if (prop == PhysicsProp.MASS && value <= 0f)
			throw new IllegalArgumentException("Physics 质量必须大于 0");
	}

	private void requireValidPhysicsTarget(Animation animation, int constraintIndex, PhysicsProp prop, float time) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画数据无效");
		if (prop == null) throw new IllegalArgumentException("Physics 轨道属性无效");
		boolean validTarget = false;
		if (constraintIndex == -1) {
			if (prop == PhysicsProp.RESET) {
				for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
					if (constraint instanceof PhysicsConstraintData) { validTarget = true; break; }
			} else {
				validTarget = hasGlobalPhysicsProperty(doc.data, prop);
			}
		} else if (constraintIndex >= 0 && constraintIndex < doc.data.getConstraints().size) {
			validTarget = doc.data.getConstraints().get(constraintIndex) instanceof PhysicsConstraintData;
		}
		if (!validTarget) {
			if (constraintIndex == -1 && prop != PhysicsProp.RESET)
				throw new IllegalArgumentException("没有 Physics 约束启用该属性的 Global 标志");
			throw new IllegalArgumentException("关键帧引用的 Physics 约束不存在");
		}
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
	}

	private static Timeline newPhysicsValueTimeline(PhysicsProp prop, int frameCount, int bezierCount,
		int constraintIndex) {
		if (prop == null || prop == PhysicsProp.RESET) throw new IllegalArgumentException("Physics 数值轨道属性无效");
		return switch (prop) {
		case INERTIA -> new PhysicsConstraintInertiaTimeline(frameCount, bezierCount, constraintIndex);
		case STRENGTH -> new PhysicsConstraintStrengthTimeline(frameCount, bezierCount, constraintIndex);
		case DAMPING -> new PhysicsConstraintDampingTimeline(frameCount, bezierCount, constraintIndex);
		case MASS -> new PhysicsConstraintMassTimeline(frameCount, bezierCount, constraintIndex);
		case WIND -> new PhysicsConstraintWindTimeline(frameCount, bezierCount, constraintIndex);
		case GRAVITY -> new PhysicsConstraintGravityTimeline(frameCount, bezierCount, constraintIndex);
		case MIX -> new PhysicsConstraintMixTimeline(frameCount, bezierCount, constraintIndex);
		case RESET -> throw new IllegalArgumentException("Physics Reset 不是数值轨道");
		};
	}

	private static void setPhysicsValueFrame(Timeline timeline, int frame, float time, float value) {
		if (!(timeline instanceof PhysicsConstraintTimeline))
			throw new IllegalArgumentException("Physics 数值轨道类型无效");
		((CurveTimeline1)timeline).setFrame(frame, time, value);
	}

	private void copyPhysicsValueFrame(Timeline source, int sourceFrame, Timeline target, int targetFrame) {
		if (physicsPropOf(source) != physicsPropOf(target) || physicsPropOf(source) == PhysicsProp.RESET)
			throw new IllegalArgumentException("Physics 数值轨道类型不兼容");
		setPhysicsValueFrame(target, targetFrame, frameTime(source, sourceFrame),
			physicsValueAt(source, sourceFrame));
	}

	// ================= Slider constraint keyframes =================

	public int addSliderKeyframe(Animation animation, int constraintIndex, SliderProp prop,
		float time, float value) {
		requireValidSliderEdit(animation, constraintIndex, prop, time, value);
		Timeline timeline = findSliderTimeline(animation, constraintIndex, prop);
		if (timeline == null) {
			Timeline created = newSliderTimeline(prop, 1, 0, constraintIndex);
			setSliderFrame(created, 0, time, value);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (Float.compare(sliderValueAt(timeline, existing), value) == 0) return existing;
			if (!hasBezier(timeline)) {
				setSliderFrame(timeline, existing, time, value);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				Timeline rebuilt = newSliderTimeline(prop, count, bezierCapacity(timeline), constraintIndex);
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) setSliderFrame(rebuilt, frame, time, value);
					else copySliderFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		Timeline rebuilt = newSliderTimeline(prop, oldCount + 1, bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) setSliderFrame(rebuilt, target, time, value);
			else {
				copySliderFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editSliderKeyframe(Animation animation, int constraintIndex, SliderProp prop,
		float oldTime, float newTime, float value, boolean stepped) {
		requireValidSliderEdit(animation, constraintIndex, prop, newTime, value);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		Timeline timeline = findSliderTimeline(animation, constraintIndex, prop);
		if (timeline == null) throw new IllegalArgumentException("找不到 Slider 关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的 Slider 关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		float[] values = new float[count];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			values[frame] = sliderValueAt(timeline, frame);
			curves[frame] = sourceCurveType(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		values[editedFrame] = value;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(values, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| Float.compare(values[frame], sliderValueAt(timeline, frame)) != 0
				|| frame < count - 1 && curves[frame] != curveType(timeline, frame);
		if (!changed) return newFrame;

		Timeline rebuilt = newSliderTimeline(prop, count, bezierCapacity(timeline), constraintIndex);
		for (int frame = 0; frame < count; frame++) setSliderFrame(rebuilt, frame, times[frame], values[frame]);
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeSliderKeyframe(Animation animation, int constraintIndex, SliderProp prop, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		if (prop == null) throw new IllegalArgumentException("Slider 轨道属性无效");
		Timeline timeline = findSliderTimeline(animation, constraintIndex, prop);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		Timeline rebuilt = newSliderTimeline(prop, timeline.getFrameCount() - 1,
			bezierCapacity(timeline), constraintIndex);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copySliderFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void requireValidSliderEdit(Animation animation, int constraintIndex, SliderProp prop,
		float time, float value) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画数据无效");
		if (constraintIndex < 0 || constraintIndex >= doc.data.getConstraints().size
			|| !(doc.data.getConstraints().get(constraintIndex) instanceof SliderData slider))
			throw new IllegalArgumentException("关键帧引用的 Slider 约束不存在");
		if (prop == null) throw new IllegalArgumentException("Slider 轨道属性无效");
		if (prop == SliderProp.TIME && slider.getBone() != null)
			throw new IllegalArgumentException("骨骼属性驱动的 Slider 不能编辑时间轨道");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
		if (!Float.isFinite(value)) throw new IllegalArgumentException("Slider 关键帧数值必须是有限数值");
	}

	private void requireCurveTimelineEditable(Timeline timeline) {
		if (!(timeline instanceof SliderTimeline) || doc.data == null) return;
		int constraintIndex = ((ConstraintTimeline)timeline).getConstraintIndex();
		if (constraintIndex >= 0 && constraintIndex < doc.data.getConstraints().size
			&& doc.data.getConstraints().get(constraintIndex) instanceof SliderData slider
			&& slider.getBone() != null)
			throw new IllegalArgumentException("骨骼属性驱动的 Slider 不能编辑时间轨道");
	}

	private static Timeline newSliderTimeline(SliderProp prop, int frameCount, int bezierCount,
		int constraintIndex) {
		if (prop == null) throw new IllegalArgumentException("Slider 轨道属性无效");
		return prop == SliderProp.TIME ? new SliderTimeline(frameCount, bezierCount, constraintIndex)
			: new SliderMixTimeline(frameCount, bezierCount, constraintIndex);
	}

	private static void setSliderFrame(Timeline timeline, int frame, float time, float value) {
		if (!(timeline instanceof SliderTimeline || timeline instanceof SliderMixTimeline))
			throw new IllegalArgumentException("Slider 轨道类型无效");
		((CurveTimeline1)timeline).setFrame(frame, time, value);
	}

	private void copySliderFrame(Timeline source, int sourceFrame, Timeline target, int targetFrame) {
		if (sliderPropOf(source) == null || sliderPropOf(source) != sliderPropOf(target))
			throw new IllegalArgumentException("Slider 轨道类型不兼容");
		setSliderFrame(target, targetFrame, frameTime(source, sourceFrame), sliderValueAt(source, sourceFrame));
	}

	// ================= 绘制顺序关键帧 =================

	// ================= Sequence keyframes =================

	/** Adds or replaces one instant Sequence key and returns its sorted frame index. */
	public int addSequenceKeyframe(Animation animation, int slotIndex, Attachment attachment,
		float time, SequenceKeyframe value) {
		requireValidSequenceEdit(animation, slotIndex, attachment, time, value);
		SequenceTimeline timeline = findSequenceTimeline(animation, slotIndex, attachment);
		if (timeline == null) {
			SequenceTimeline created = new SequenceTimeline(1, slotIndex, attachment);
			setSequenceFrame(created, 0, time, value);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (Float.compare(frameTime(timeline, existing), time) == 0
				&& sequenceAt(timeline, existing).equals(value)) return existing;
			setSequenceFrame(timeline, existing, time, value);
			refreshAnimation(animation);
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		SequenceTimeline rebuilt = new SequenceTimeline(oldCount + 1, slotIndex, attachment);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) setSequenceFrame(rebuilt, target, time, value);
			else copySequenceFrame(timeline, source++, rebuilt, target);
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	/** Atomically changes one Sequence key. A move to an occupied time is rejected. */
	public int editSequenceKeyframe(Animation animation, int slotIndex, Attachment attachment,
		float oldTime, float newTime, SequenceKeyframe value) {
		requireValidSequenceEdit(animation, slotIndex, attachment, newTime, value);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		SequenceTimeline timeline = findSequenceTimeline(animation, slotIndex, attachment);
		if (timeline == null) throw new IllegalArgumentException("Sequence 关键帧轨道不存在");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("Sequence 关键帧不存在");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		SequenceKeyframe[] values = new SequenceKeyframe[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			values[frame] = sequenceAt(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		values[editedFrame] = value;
		for (int index = 1; index < count; index++) {
			for (int current = index; current > 0 && times[current] < times[current - 1]; current--) {
				swap(times, current, current - 1);
				SequenceKeyframe swap = values[current];
				values[current] = values[current - 1];
				values[current - 1] = swap;
				swap(identities, current, current - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| !values[frame].equals(sequenceAt(timeline, frame));
		if (!changed) return newFrame;

		SequenceTimeline rebuilt = new SequenceTimeline(count, slotIndex, attachment);
		for (int frame = 0; frame < count; frame++) setSequenceFrame(rebuilt, frame, times[frame], values[frame]);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeSequenceKeyframe(Animation animation, int slotIndex, Attachment attachment, float time) {
		validateSequenceTarget(animation, slotIndex, attachment);
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		SequenceTimeline timeline = findSequenceTimeline(animation, slotIndex, attachment);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}

		SequenceTimeline rebuilt = new SequenceTimeline(timeline.getFrameCount() - 1, slotIndex, attachment);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copySequenceFrame(timeline, source++, rebuilt, target);
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void requireValidSequenceEdit(Animation animation, int slotIndex, Attachment attachment,
		float time, SequenceKeyframe value) {
		validateSequenceTarget(animation, slotIndex, attachment);
		if (!ownsAttachmentTimeline(attachment))
			throw new IllegalArgumentException("继承时间线的 Linked Mesh 不能创建直属 Sequence 轨道");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
		if (value == null || value.mode() == null)
			throw new IllegalArgumentException("Sequence 关键帧模式不能为空");
		if (value.index() < 0 || value.index() > 0xfffff)
			throw new IllegalArgumentException("Sequence 起始索引必须介于 0 和 1048575");
		if (!Float.isFinite(value.delay()) || value.mode() != SequenceMode.hold && value.delay() <= 0f)
			throw new IllegalArgumentException("Sequence 非保持模式的帧间隔必须大于 0");
	}

	private void validateSequenceTarget(Animation animation, int slotIndex, Attachment attachment) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画数据无效");
		if (slotIndex < 0 || slotIndex >= doc.data.getSlots().size)
			throw new IllegalArgumentException("Sequence 槽索引无效");
		if (!(attachment instanceof HasSequence hasSequence) || hasSequence.getSequence() == null
			|| hasSequence.getSequence().getRegions() == null || hasSequence.getSequence().getRegions().length == 0)
			throw new IllegalArgumentException("Sequence 附件数据无效");
		boolean ownedBySlot = false;
		for (Skin skin : doc.data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (entry.getSlotIndex() == slotIndex && entry.getAttachment() == attachment) {
					ownedBySlot = true;
					break;
				}
			}
			if (ownedBySlot) break;
		}
		Skin defaultSkin = doc.data.getDefaultSkin();
		if (!ownedBySlot && defaultSkin != null && !doc.data.getSkins().contains(defaultSkin, true)) {
			for (Skin.SkinEntry entry : defaultSkin.getAttachments()) {
				if (entry.getSlotIndex() == slotIndex && entry.getAttachment() == attachment) {
					ownedBySlot = true;
					break;
				}
			}
		}
		if (!ownedBySlot) throw new IllegalArgumentException("Sequence 附件不属于目标槽");
	}

	private void setSequenceFrame(SequenceTimeline timeline, int frame, float time, SequenceKeyframe value) {
		timeline.setFrame(frame, time, value.mode(), value.index(), value.delay());
	}

	private void copySequenceFrame(SequenceTimeline source, int sourceFrame,
		SequenceTimeline target, int targetFrame) {
		setSequenceFrame(target, targetFrame, frameTime(source, sourceFrame), sequenceAt(source, sourceFrame));
	}

	// ================= Deform keyframes =================

	/** Adds or replaces one setup-relative Deform key and returns its sorted frame index. */
	public int addDeformKeyframe(Animation animation, int slotIndex, VertexAttachment attachment,
		float time, float[] offsets) {
		float[] stored = requireValidDeformEdit(animation, slotIndex, attachment, time, offsets);
		DeformTimeline timeline = findDeformTimeline(animation, slotIndex, attachment);
		if (timeline == null) {
			DeformTimeline created = new DeformTimeline(1, 0, slotIndex, attachment);
			created.setFrame(0, time, stored);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (Float.compare(frameTime(timeline, existing), time) == 0
				&& java.util.Arrays.equals(timeline.getVertices()[existing], stored)) return existing;
			if (!hasBezier(timeline)) {
				timeline.setFrame(existing, time, stored);
				refreshAnimation(animation);
			} else {
				int count = timeline.getFrameCount();
				DeformTimeline rebuilt = new DeformTimeline(count, bezierCapacity(timeline), slotIndex, attachment);
				int[] sourceFrames = new int[count];
				int[] curveTypes = new int[count];
				for (int frame = 0; frame < count; frame++) {
					sourceFrames[frame] = frame;
					curveTypes[frame] = sourceCurveType(timeline, frame);
					if (frame == existing) rebuilt.setFrame(frame, time, stored);
					else copyDeformFrame(timeline, frame, rebuilt, frame);
				}
				copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
				replaceTimeline(animation, timeline, rebuilt);
			}
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		DeformTimeline rebuilt = new DeformTimeline(oldCount + 1, bezierCapacity(timeline), slotIndex, attachment);
		int[] sourceFrames = new int[oldCount + 1];
		int[] curveTypes = new int[oldCount + 1];
		java.util.Arrays.fill(sourceFrames, -1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) rebuilt.setFrame(target, time, stored);
			else {
				copyDeformFrame(timeline, source, rebuilt, target);
				sourceFrames[target] = source;
				curveTypes[target] = sourceCurveType(timeline, source);
				source++;
			}
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	/** Atomically edits one Deform key. A move to an occupied time is rejected. */
	public int editDeformKeyframe(Animation animation, int slotIndex, VertexAttachment attachment,
		float oldTime, float newTime, float[] offsets, boolean stepped) {
		float[] stored = requireValidDeformEdit(animation, slotIndex, attachment, newTime, offsets);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("Original key time must be finite");
		DeformTimeline timeline = findDeformTimeline(animation, slotIndex, attachment);
		if (timeline == null) throw new IllegalArgumentException("Deform keyframe timeline was not found");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("Deform keyframe was not found");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("The target time already has a keyframe");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		float[][] vertices = new float[count][];
		int[] curves = new int[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			vertices[frame] = timeline.getVertices()[frame].clone();
			curves[frame] = sourceCurveType(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		vertices[editedFrame] = stored;
		if (stepped) curves[editedFrame] = CurveTimeline.STEPPED;
		else if (curves[editedFrame] < CurveTimeline.BEZIER) curves[editedFrame] = CurveTimeline.LINEAR;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(vertices, j, j - 1);
				swap(curves, j, j - 1);
				swap(identities, j, j - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| !java.util.Arrays.equals(vertices[frame], timeline.getVertices()[frame])
				|| frame < count - 1 && curves[frame] != curveType(timeline, frame);
		if (!changed) return newFrame;

		DeformTimeline rebuilt = new DeformTimeline(count, bezierCapacity(timeline), slotIndex, attachment);
		for (int frame = 0; frame < count; frame++) rebuilt.setFrame(frame, times[frame], vertices[frame]);
		copyCurves(timeline, rebuilt, identities, curves);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeDeformKeyframe(Animation animation, int slotIndex, VertexAttachment attachment,
		float time) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("Animation data is invalid");
		validateDeformTarget(slotIndex, attachment);
		if (!Float.isFinite(time)) throw new IllegalArgumentException("Keyframe time must be finite");
		DeformTimeline timeline = findDeformTimeline(animation, slotIndex, attachment);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		DeformTimeline rebuilt = new DeformTimeline(timeline.getFrameCount() - 1,
			bezierCapacity(timeline), slotIndex, attachment);
		int[] sourceFrames = new int[rebuilt.getFrameCount()];
		int[] curveTypes = new int[rebuilt.getFrameCount()];
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			copyDeformFrame(timeline, source, rebuilt, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			source++;
		}
		copyCurves(timeline, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private float[] requireValidDeformEdit(Animation animation, int slotIndex, VertexAttachment attachment,
		float time, float[] offsets) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("Animation data is invalid");
		validateDeformTarget(slotIndex, attachment);
		if (!ownsAttachmentTimeline(attachment))
			throw new IllegalArgumentException("Inherited Linked Mesh cannot own a direct Deform timeline");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("Keyframe time must be a finite number not less than zero");
		int length = deformLength(attachment);
		if (offsets == null || offsets.length != length)
			throw new IllegalArgumentException("Deform offset count must match the attachment");
		float[] stored = offsets.clone();
		for (float value : stored)
			if (!Float.isFinite(value)) throw new IllegalArgumentException("Deform offsets must be finite numbers");
		if (attachment.getBones() == null) {
			float[] setup = attachment.getVertices();
			for (int index = 0; index < stored.length; index++) stored[index] += setup[index];
		}
		return stored;
	}

	private void validateDeformTarget(int slotIndex, VertexAttachment attachment) {
		if (doc.data == null || attachment == null) throw new IllegalArgumentException("Deform attachment is invalid");
		if (slotIndex < 0 || slotIndex >= doc.data.getSlots().size)
			throw new IllegalArgumentException("Deform slot is invalid");
		if (!attachmentOwnedBySlot(slotIndex, attachment, false))
			throw new IllegalArgumentException("Deform attachment is not available in the target slot");
		validateDeformGeometry(attachment);
	}

	private void validateDisplayedDeformTarget(int slotIndex, VertexAttachment attachment) {
		if (doc.data == null || attachment == null) throw new IllegalArgumentException("Deform attachment is invalid");
		if (slotIndex < 0 || slotIndex >= doc.data.getSlots().size)
			throw new IllegalArgumentException("Deform slot is invalid");
		if (!attachmentOwnedBySlot(slotIndex, attachment, true))
			throw new IllegalArgumentException("Deform attachment is not available in the displayed slot");
		validateDeformGeometry(attachment);
	}

	private boolean attachmentOwnedBySlot(int slotIndex, VertexAttachment attachment, boolean timelineMatch) {
		for (Skin skin : doc.data.getSkins())
			if (attachmentOwnedBySlot(skin, slotIndex, attachment, timelineMatch)) return true;
		Skin defaultSkin = doc.data.getDefaultSkin();
		return defaultSkin != null && !doc.data.getSkins().contains(defaultSkin, true)
			&& attachmentOwnedBySlot(defaultSkin, slotIndex, attachment, timelineMatch);
	}

	private static boolean attachmentOwnedBySlot(Skin skin, int slotIndex, VertexAttachment attachment,
		boolean timelineMatch) {
		for (Skin.SkinEntry entry : skin.getAttachments()) {
			if (entry.getSlotIndex() != slotIndex || !(entry.getAttachment() instanceof VertexAttachment vertex)) continue;
			if (timelineMatch ? vertex.getTimelineAttachment() == attachment : vertex == attachment) return true;
		}
		return false;
	}

	private int findAttachmentSlot(VertexAttachment attachment) {
		for (Skin skin : doc.data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments())
				if (entry.getAttachment() == attachment) return entry.getSlotIndex();
		}
		Skin defaultSkin = doc.data.getDefaultSkin();
		if (defaultSkin != null && !doc.data.getSkins().contains(defaultSkin, true))
			for (Skin.SkinEntry entry : defaultSkin.getAttachments())
				if (entry.getAttachment() == attachment) return entry.getSlotIndex();
		return -1;
	}

	private void validateDeformGeometry(VertexAttachment attachment) {
		float[] vertices = attachment.getVertices();
		int worldLength = attachment.getWorldVerticesLength();
		if (vertices == null || worldLength <= 0 || (worldLength & 1) != 0)
			throw new IllegalArgumentException("Deform attachment vertex data is invalid");
		int[] bones = attachment.getBones();
		if (bones == null) {
			if (vertices.length != worldLength)
				throw new IllegalArgumentException("Unweighted attachment vertex data is incomplete");
			for (float value : vertices)
				if (!Float.isFinite(value))
					throw new IllegalArgumentException("Unweighted attachment vertices must be finite numbers");
			return;
		}
		int boneCursor = 0, valueCursor = 0, logicalVertices = 0;
		while (boneCursor < bones.length) {
			int influenceCount = bones[boneCursor++];
			if (influenceCount <= 0 || boneCursor + influenceCount > bones.length
				|| valueCursor + influenceCount * 3 > vertices.length)
				throw new IllegalArgumentException("Weighted attachment vertex data is incomplete");
			java.util.HashSet<Integer> seenBones = new java.util.HashSet<>();
			float weightSum = 0f;
			for (int influence = 0; influence < influenceCount; influence++) {
				int boneIndex = bones[boneCursor++];
				if (boneIndex < 0 || boneIndex >= doc.data.getBones().size)
					throw new IllegalArgumentException("Weighted attachment references a missing bone");
				if (!seenBones.add(boneIndex))
					throw new IllegalArgumentException("A weighted vertex cannot reference the same bone twice");
				float x = vertices[valueCursor++], y = vertices[valueCursor++], weight = vertices[valueCursor++];
				if (!allFinite(x, y, weight) || weight < 0f)
					throw new IllegalArgumentException("Weighted attachment values are invalid");
				weightSum += weight;
			}
			if (!(weightSum > 0f))
				throw new IllegalArgumentException("Each weighted vertex must have a positive total weight");
			logicalVertices++;
		}
		if (boneCursor != bones.length || valueCursor != vertices.length || logicalVertices * 2 != worldLength)
			throw new IllegalArgumentException("Weighted attachment vertex data is incomplete");
	}

	private static void copyDeformFrame(DeformTimeline source, int sourceFrame,
		DeformTimeline target, int targetFrame) {
		target.setFrame(targetFrame, source.getFrames()[sourceFrame], source.getVertices()[sourceFrame].clone());
	}

	public int addDrawOrderKeyframe(Animation animation, float time, int[] drawOrder) {
		int[] normalized = requireValidDrawOrderEdit(animation, time, drawOrder);
		DrawOrderTimeline timeline = findDrawOrderTimeline(animation);
		if (timeline == null) {
			DrawOrderTimeline created = new DrawOrderTimeline(1);
			created.setFrame(0, time, normalized);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (java.util.Arrays.equals(timeline.getDrawOrders()[existing], normalized)) return existing;
			timeline.setFrame(existing, time, normalized);
			refreshAnimation(animation);
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount();
		int insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		DrawOrderTimeline rebuilt = new DrawOrderTimeline(oldCount + 1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) rebuilt.setFrame(target, time, normalized);
			else {
				rebuilt.setFrame(target, frameTime(timeline, source), drawOrderAt(timeline, source));
				source++;
			}
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editDrawOrderKeyframe(Animation animation, float oldTime, float newTime, int[] drawOrder) {
		int[] normalized = requireValidDrawOrderEdit(animation, newTime, drawOrder);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		DrawOrderTimeline timeline = findDrawOrderTimeline(animation);
		if (timeline == null) throw new IllegalArgumentException("找不到绘制顺序关键帧轨道");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到要编辑的绘制顺序关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");
		}

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		int[][] orders = new int[count][];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			orders[frame] = drawOrderAt(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		orders[editedFrame] = normalized;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(orders, j, j - 1);
				swap(identities, j, j - 1);
			}
		}

		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++) {
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| !java.util.Arrays.equals(orders[frame], timeline.getDrawOrders()[frame]);
		}
		if (!changed) return newFrame;

		DrawOrderTimeline rebuilt = new DrawOrderTimeline(count);
		for (int frame = 0; frame < count; frame++) rebuilt.setFrame(frame, times[frame], orders[frame]);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeDrawOrderKeyframe(Animation animation, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		DrawOrderTimeline timeline = findDrawOrderTimeline(animation);
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		DrawOrderTimeline rebuilt = new DrawOrderTimeline(timeline.getFrameCount() - 1);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			rebuilt.setFrame(target, frameTime(timeline, source), drawOrderAt(timeline, source));
			source++;
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	public int addDrawOrderFolderKeyframe(Animation animation, DrawOrderFolderTimeline timeline,
		float time, int[] drawOrder) {
		int[] normalized = requireValidDrawOrderFolderEdit(animation, timeline, time, drawOrder);
		int existing = findFrame(timeline, time);
		if (existing >= 0) {
			if (java.util.Arrays.equals(timeline.getDrawOrders()[existing], normalized)) return existing;
			timeline.setFrame(existing, time, normalized);
			refreshAnimation(animation);
			doc.markDirty();
			return existing;
		}

		int oldCount = timeline.getFrameCount(), insert = 0;
		while (insert < oldCount && frameTime(timeline, insert) < time) insert++;
		DrawOrderFolderTimeline rebuilt = new DrawOrderFolderTimeline(oldCount + 1,
			timeline.getSlots().clone(), doc.data.getSlots().size);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (target == insert) rebuilt.setFrame(target, time, normalized);
			else {
				rebuilt.setFrame(target, frameTime(timeline, source), drawOrderFolderAt(timeline, source));
				source++;
			}
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	public int editDrawOrderFolderKeyframe(Animation animation, DrawOrderFolderTimeline timeline,
		float oldTime, float newTime, int[] drawOrder) {
		int[] normalized = requireValidDrawOrderFolderEdit(animation, timeline, newTime, drawOrder);
		if (!Float.isFinite(oldTime)) throw new IllegalArgumentException("原关键帧时间必须是有限数值");
		int editedFrame = findFrame(timeline, oldTime);
		if (editedFrame < 0) throw new IllegalArgumentException("找不到局部绘制顺序关键帧");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++)
			if (frame != editedFrame && sameFrameTime(frameTime(timeline, frame), newTime))
				throw new IllegalArgumentException("目标时间已有关键帧");

		int count = timeline.getFrameCount();
		float[] times = new float[count];
		int[][] orders = new int[count][];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			times[frame] = frameTime(timeline, frame);
			orders[frame] = drawOrderFolderAt(timeline, frame);
			identities[frame] = frame;
		}
		times[editedFrame] = newTime;
		orders[editedFrame] = normalized;
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && times[j] < times[j - 1]; j--) {
				swap(times, j, j - 1);
				swap(orders, j, j - 1);
				swap(identities, j, j - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != editedFrame) newFrame++;
		boolean changed = false;
		for (int frame = 0; frame < count && !changed; frame++)
			changed = Float.compare(times[frame], frameTime(timeline, frame)) != 0
				|| !java.util.Arrays.equals(orders[frame], timeline.getDrawOrders()[frame]);
		if (!changed) return newFrame;

		DrawOrderFolderTimeline rebuilt = new DrawOrderFolderTimeline(count,
			timeline.getSlots().clone(), doc.data.getSlots().size);
		for (int frame = 0; frame < count; frame++) rebuilt.setFrame(frame, times[frame], orders[frame]);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeDrawOrderFolderKeyframe(Animation animation, DrawOrderFolderTimeline timeline,
		float time) {
		requireValidDrawOrderFolderEdit(animation, timeline, Math.max(0f, time), null);
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		int remove = findFrame(timeline, time);
		if (remove < 0) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		DrawOrderFolderTimeline rebuilt = new DrawOrderFolderTimeline(timeline.getFrameCount() - 1,
			timeline.getSlots().clone(), doc.data.getSlots().size);
		int source = 0;
		for (int target = 0; target < rebuilt.getFrameCount(); target++) {
			if (source == remove) source++;
			rebuilt.setFrame(target, frameTime(timeline, source), drawOrderFolderAt(timeline, source));
			source++;
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private int[] requireValidDrawOrderFolderEdit(Animation animation, DrawOrderFolderTimeline timeline,
		float time, int[] drawOrder) {
		if (animation == null || timeline == null || doc.data == null
			|| !doc.data.getAnimations().contains(animation, true)
			|| !animation.getTimelines().contains(timeline, true))
			throw new IllegalArgumentException("局部绘制顺序轨道无效");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
		int[] slots = timeline.getSlots();
		if (slots == null || slots.length == 0)
			throw new IllegalArgumentException("局部绘制顺序没有槽");
		boolean[] slotSeen = new boolean[doc.data.getSlots().size];
		for (int slot : slots) {
			if (slot < 0 || slot >= slotSeen.length || slotSeen[slot])
				throw new IllegalArgumentException("局部绘制顺序槽集合无效");
			slotSeen[slot] = true;
		}
		if (drawOrder == null) return null;
		if (drawOrder.length != slots.length)
			throw new IllegalArgumentException("局部绘制顺序必须包含全部 " + slots.length + " 个槽");
		boolean[] seen = new boolean[slots.length];
		boolean setupOrder = true;
		for (int position = 0; position < drawOrder.length; position++) {
			int localIndex = drawOrder[position];
			if (localIndex < 0 || localIndex >= slots.length || seen[localIndex])
				throw new IllegalArgumentException("局部绘制顺序必须是 0 到 " + (slots.length - 1) + " 的排列");
			seen[localIndex] = true;
			setupOrder &= localIndex == position;
		}
		return setupOrder ? null : drawOrder.clone();
	}

	private int[] requireValidDrawOrderEdit(Animation animation, float time, int[] drawOrder) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画数据无效");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("关键帧时间必须是不小于 0 的有限数值");
		if (drawOrder == null) return null;
		int slotCount = doc.data.getSlots().size;
		if (drawOrder.length != slotCount)
			throw new IllegalArgumentException("绘制顺序必须包含全部 " + slotCount + " 个槽");
		boolean[] seen = new boolean[slotCount];
		boolean setupOrder = true;
		for (int position = 0; position < drawOrder.length; position++) {
			int slotIndex = drawOrder[position];
			if (slotIndex < 0 || slotIndex >= slotCount)
				throw new IllegalArgumentException("绘制顺序包含不存在的槽索引 " + slotIndex);
			if (seen[slotIndex]) throw new IllegalArgumentException("绘制顺序包含重复槽索引 " + slotIndex);
			seen[slotIndex] = true;
			if (slotIndex != position) setupOrder = false;
		}
		return setupOrder ? null : drawOrder.clone();
	}

	// ================= 事件定义与事件关键帧 =================

	public EventData addEventDefinition(String requestedName) {
		if (doc.data == null) throw new IllegalStateException("没有可编辑的骨架数据");
		EventData definition = new EventData(uniqueEventName(requestedName));
		definition.getSetupPose().setString("");
		doc.data.getEvents().add(definition);
		doc.markDirty();
		return definition;
	}

	/** Atomically replaces a definition and every event key that references it. */
	public EventData updateEventDefinition(EventData current, String requestedName, int intValue,
		float floatValue, String stringValue, String audioPath, float volume, float balance) {
		requireEventDefinition(current);
		String name = normalizeEventName(requestedName);
		for (EventData definition : doc.data.getEvents()) {
			if (definition != current && definition.getName().equals(name))
				throw new IllegalArgumentException("事件名称已存在：" + name);
		}
		requireEventPayload(floatValue, stringValue, volume, balance);
		String audio = audioPath == null || audioPath.isBlank() ? null : audioPath;
		Event setup = current.getSetupPose();
		if (current.getName().equals(name) && java.util.Objects.equals(current.getAudioPath(), audio)
			&& setup.getInt() == intValue && Float.compare(setup.getFloat(), floatValue) == 0
			&& java.util.Objects.equals(setup.getString(), stringValue)
			&& Float.compare(setup.getVolume(), audio == null ? 0f : volume) == 0
			&& Float.compare(setup.getBalance(), audio == null ? 0f : balance) == 0) return current;

		EventData replacement = new EventData(name);
		Event replacementSetup = replacement.getSetupPose();
		replacementSetup.setInt(intValue);
		replacementSetup.setFloat(floatValue);
		replacementSetup.setString(stringValue);
		if (audio != null) {
			replacement.setAudioPath(audio);
			replacementSetup.setVolume(volume);
			replacementSetup.setBalance(balance);
		}

		int definitionIndex = doc.data.getEvents().indexOf(current, true);
		doc.data.getEvents().set(definitionIndex, replacement);
		for (Animation animation : doc.data.getAnimations()) {
			EventTimeline timeline = findEventTimeline(animation);
			if (timeline == null) continue;
			boolean referenced = false;
			for (Event event : timeline.getEvents()) {
				if (event != null && event.getData() == current) { referenced = true; break; }
			}
			if (!referenced) continue;
			EventTimeline rebuilt = new EventTimeline(timeline.getFrameCount());
			for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
				Event source = eventAt(timeline, frame);
				rebuilt.setFrame(frame, source.getData() == current
					? copyEvent(source, source.getTime(), replacement) : copyEvent(source, source.getTime()));
			}
			replaceTimeline(animation, timeline, rebuilt);
		}
		doc.markDirty();
		return replacement;
	}

	public String getEventDeleteBlockReason(EventData definition) {
		if (doc.data == null || definition == null || !doc.data.getEvents().contains(definition, true))
			return "事件定义无效";
		for (Animation animation : doc.data.getAnimations()) {
			EventTimeline timeline = findEventTimeline(animation);
			if (timeline == null) continue;
			for (Event event : timeline.getEvents()) {
				if (event != null && event.getData() == definition)
					return "仍被动画 " + animation.getName() + " 引用";
			}
		}
		return null;
	}

	public boolean removeEventDefinition(EventData definition) {
		if (getEventDeleteBlockReason(definition) != null) return false;
		if (!doc.data.getEvents().removeValue(definition, true)) return false;
		doc.markDirty();
		return true;
	}

	public int addEventKeyframe(Animation animation, float time, EventData definition, int intValue,
		float floatValue, String stringValue, float volume, float balance) {
		requireValidEventEdit(animation, time, definition, floatValue, stringValue, volume, balance);
		EventTimeline timeline = findEventTimeline(animation);
		Event added = newEvent(time, definition, intValue, floatValue, stringValue, volume, balance);
		if (timeline == null) {
			EventTimeline created = new EventTimeline(1);
			created.setFrame(0, added);
			animation.getTimelines().add(created);
			refreshAnimation(animation);
			doc.markDirty();
			return 0;
		}

		int count = timeline.getFrameCount();
		int insert = 0;
		while (insert < count && eventAt(timeline, insert).getTime() <= time) insert++;
		EventTimeline rebuilt = new EventTimeline(count + 1);
		int source = 0;
		for (int target = 0; target < count + 1; target++) {
			if (target == insert) rebuilt.setFrame(target, added);
			else {
				Event event = eventAt(timeline, source++);
				rebuilt.setFrame(target, copyEvent(event, event.getTime()));
			}
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return insert;
	}

	/** Edits one event by exact frame index so same-time events remain independently addressable. */
	public int editEventKeyframe(Animation animation, int frameIndex, float newTime, EventData definition,
		int intValue, float floatValue, String stringValue, float volume, float balance) {
		requireValidEventEdit(animation, newTime, definition, floatValue, stringValue, volume, balance);
		EventTimeline timeline = findEventTimeline(animation);
		Event original = eventAt(timeline, frameIndex);
		Event edited = newEvent(newTime, definition, intValue, floatValue, stringValue, volume, balance);
		if (eventsEqual(original, edited)) return frameIndex;

		int count = timeline.getFrameCount();
		Event[] events = new Event[count];
		int[] identities = new int[count];
		for (int frame = 0; frame < count; frame++) {
			Event source = eventAt(timeline, frame);
			events[frame] = frame == frameIndex ? edited : copyEvent(source, source.getTime());
			identities[frame] = frame;
		}
		for (int i = 1; i < count; i++) {
			for (int j = i; j > 0 && events[j].getTime() < events[j - 1].getTime(); j--) {
				Event event = events[j]; events[j] = events[j - 1]; events[j - 1] = event;
				swap(identities, j, j - 1);
			}
		}
		int newFrame = 0;
		while (identities[newFrame] != frameIndex) newFrame++;
		EventTimeline rebuilt = new EventTimeline(count);
		for (int frame = 0; frame < count; frame++) rebuilt.setFrame(frame, events[frame]);
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return newFrame;
	}

	public boolean removeEventKeyframe(Animation animation, int frameIndex) {
		EventTimeline timeline = findEventTimeline(animation);
		if (timeline == null || frameIndex < 0 || frameIndex >= timeline.getFrameCount()) return false;
		if (timeline.getFrameCount() == 1) {
			removeTimeline(animation, timeline);
			doc.markDirty();
			return true;
		}
		EventTimeline rebuilt = new EventTimeline(timeline.getFrameCount() - 1);
		int target = 0;
		for (int source = 0; source < timeline.getFrameCount(); source++) {
			if (source == frameIndex) continue;
			Event event = eventAt(timeline, source);
			rebuilt.setFrame(target++, copyEvent(event, event.getTime()));
		}
		replaceTimeline(animation, timeline, rebuilt);
		doc.markDirty();
		return true;
	}

	private void requireValidEventEdit(Animation animation, float time, EventData definition,
		float floatValue, String stringValue, float volume, float balance) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("动画数据无效");
		if (!Float.isFinite(time) || time < 0f)
			throw new IllegalArgumentException("事件时间必须是不小于 0 的有限数值");
		requireEventDefinition(definition);
		requireEventPayload(floatValue, stringValue, volume, balance);
	}

	private void requireEventDefinition(EventData definition) {
		if (doc.data == null || definition == null || !doc.data.getEvents().contains(definition, true))
			throw new IllegalArgumentException("事件定义无效");
	}

	private static void requireEventPayload(float floatValue, String stringValue, float volume, float balance) {
		if (!Float.isFinite(floatValue) || !Float.isFinite(volume) || !Float.isFinite(balance))
			throw new IllegalArgumentException("事件数值必须是有限数值");
		if (stringValue == null) throw new IllegalArgumentException("事件字符串不能为 null");
	}

	private static Event newEvent(float time, EventData definition, int intValue, float floatValue,
		String stringValue, float volume, float balance) {
		Event event = new Event(time, definition);
		event.setInt(intValue);
		event.setFloat(floatValue);
		event.setString(stringValue);
		event.setVolume(volume);
		event.setBalance(balance);
		return event;
	}

	private static Event copyEvent(Event source, float time) {
		return copyEvent(source, time, source.getData());
	}

	private static Event copyEvent(Event source, float time, EventData definition) {
		if (source == null) throw new IllegalStateException("事件关键帧数据为空");
		return newEvent(time, definition, source.getInt(), source.getFloat(), source.getString(),
			source.getVolume(), source.getBalance());
	}

	private String uniqueEventName(String requestedName) {
		String base = requestedName == null || requestedName.isBlank() ? "event" : requestedName.trim();
		if (doc.data.findEvent(base) == null) return base;
		int suffix = 2;
		while (doc.data.findEvent(base + suffix) != null) suffix++;
		return base + suffix;
	}

	private static String normalizeEventName(String requestedName) {
		if (requestedName == null || requestedName.isBlank())
			throw new IllegalArgumentException("事件名称不能为空");
		return requestedName.trim();
	}

	/**
	 * 移除某帧（同时间线的后继帧时间不变，仅删除条目）。
	 */
	public void removeKeyframe(Animation anim, Prop prop, int boneIndex, float time) {
		if (!Float.isFinite(time)) throw new IllegalArgumentException("关键帧时间必须是有限数值");
		Animation.Timeline tl = findTimeline(anim, prop, boneIndex);
		if (tl == null) return;
		int old = frameCount(tl);
		if (old <= 0) return;
		// 至少保留一帧
		int removeIdx = -1;
		for (int i = 0; i < old; i++) {
			if (sameFrameTime(frameTime(tl, i), time)) { removeIdx = i; break; }
		}
		if (removeIdx < 0) return;
		if (old == 1) {
			removeTimeline(anim, tl);
			doc.markDirty();
			return;
		}
		int newCount = old - 1;
		Animation.Timeline rebuilt = newTimeline(prop, boneIndex, newCount, bezierCapacity(tl));
		int[] sourceFrames = new int[newCount];
		int[] curveTypes = new int[newCount];
		int src = 0;
		for (int dst = 0; dst < newCount; dst++) {
			if (src == removeIdx) src++;
			setFrame(rebuilt, dst, frameTime(tl, src), frameValue(tl, src, 0), frameValue(tl, src, 1));
			sourceFrames[dst] = src;
			curveTypes[dst] = sourceCurveType(tl, src);
			src++;
		}
		copyCurves(tl, rebuilt, sourceFrames, curveTypes);
		replaceTimeline(anim, tl, rebuilt);
		doc.markDirty();
	}

	private static int sourceCurveType(Animation.Timeline timeline, int frame) {
		if (!(timeline instanceof CurveTimeline curve) || frame < 0 || frame >= timeline.getFrameCount() - 1)
			return CurveTimeline.LINEAR;
		return curve.getCurveType(frame);
	}

	private static int bezierCapacity(Animation.Timeline timeline) {
		if (!(timeline instanceof CurveTimeline curve)) return 0;
		return Math.max(0, (curveData(curve).length - timeline.getFrameCount()) / CurveTimeline.BEZIER_SIZE);
	}

	private static void applyCurveSnapshots(Animation.Timeline target, CurveSnapshot[] snapshots) {
		if (!(target instanceof CurveTimeline curve)) return;
		if (snapshots == null || snapshots.length != target.getFrameCount())
			throw new IllegalArgumentException("曲线快照数量与关键帧数量不一致");
		int channels = curveChannelCount(target);
		int entries = target.getFrameEntries();
		float[] targetFrames = target.getFrames();
		float[] targetCurves = curveData(curve);
		int targetBlock = target.getFrameCount();
		for (int frame = 0; frame < target.getFrameCount() - 1; frame++) {
			CurveSnapshot snapshot = snapshots[frame];
			if (snapshot == null || snapshot.type < CurveTimeline.BEZIER) {
				if (snapshot != null && snapshot.type == CurveTimeline.STEPPED) curve.setStepped(frame);
				else curve.setLinear(frame);
				continue;
			}
			if (snapshot.frameEntries != entries || snapshot.channelCount != channels
				|| snapshot.startValues == null || snapshot.endValues == null || snapshot.samples == null
				|| snapshot.startValues.length != channels + 1 || snapshot.endValues.length != channels + 1
				|| snapshot.samples.length != channels * CurveTimeline.BEZIER_SIZE)
				throw new IllegalArgumentException("复制的贝塞尔曲线数据与目标轨道不兼容");
			int required = snapshot.samples.length;
			if (targetBlock + required > targetCurves.length)
				throw new IllegalStateException("粘贴后的贝塞尔曲线容量不足");
			targetCurves[frame] = CurveTimeline.BEZIER + targetBlock;
			int targetStart = frame * entries;
			int targetEnd = targetStart + entries;
			double sourceDuration = (double)snapshot.endValues[0] - snapshot.startValues[0];
			double targetDuration = (double)targetFrames[targetEnd] - targetFrames[targetStart];
			if (!(sourceDuration > 0d) || !(targetDuration > 0d)
				|| !Double.isFinite(sourceDuration) || !Double.isFinite(targetDuration))
				throw new IllegalArgumentException("复制曲线的关键帧时间必须严格递增");
			for (int channel = 0; channel < channels; channel++) {
				int sourceBlock = channel * CurveTimeline.BEZIER_SIZE;
				int destinationBlock = targetBlock + sourceBlock;
				float sourceValue1 = snapshot.startValues[channel + 1];
				float sourceValue2 = snapshot.endValues[channel + 1];
				float targetValue1 = target instanceof DeformTimeline ? 0f : targetFrames[targetStart + channel + 1];
				float targetValue2 = target instanceof DeformTimeline ? 1f : targetFrames[targetEnd + channel + 1];
				double sourceDelta = (double)sourceValue2 - sourceValue1;
				double targetDelta = (double)targetValue2 - targetValue1;
				for (int point = 0; point < CurveTimeline.BEZIER_SIZE / 2; point++) {
					double u = (point + 1) * 0.1d;
					int offset = point * 2;
					double normalizedTime = (snapshot.samples[sourceBlock + offset] - snapshot.startValues[0])
						/ sourceDuration;
					targetCurves[destinationBlock + offset] = finiteCurveSample(
						targetFrames[targetStart] + normalizedTime * targetDuration);
					double mappedValue;
					if (sourceDelta == targetDelta)
						mappedValue = targetValue1 + ((double)snapshot.samples[sourceBlock + offset + 1] - sourceValue1);
					else if (targetDelta == 0d) mappedValue = targetValue1;
					else if (sourceDelta != 0d) {
						double normalizedValue = (snapshot.samples[sourceBlock + offset + 1] - sourceValue1)
							/ sourceDelta;
						mappedValue = targetValue1 + normalizedValue * targetDelta;
					} else {
						double normalizedValue = u * u * (3d - 2d * u);
						mappedValue = targetValue1 + normalizedValue * targetDelta;
					}
					targetCurves[destinationBlock + offset + 1] = finiteCurveSample(mappedValue);
				}
			}
			targetBlock += required;
		}
		curve.shrink((targetBlock - target.getFrameCount()) / CurveTimeline.BEZIER_SIZE);
	}

	public static int curveChannelCount(Animation.Timeline timeline) {
		return timeline instanceof DeformTimeline ? 1
			: timeline instanceof IkConstraintTimeline ? 2 : timeline.getFrameEntries() - 1;
	}

	private static void validateCurveData(Animation.Timeline timeline) {
		if (!(timeline instanceof CurveTimeline curve)) return;
		float[] data = curveData(curve);
		int required = curveChannelCount(timeline) * CurveTimeline.BEZIER_SIZE;
		for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
			int type = curve.getCurveType(frame);
			if (type < CurveTimeline.BEZIER) continue;
			int start = type - CurveTimeline.BEZIER;
			if (start < timeline.getFrameCount() || start + required > data.length)
				throw new IllegalStateException("Spine 贝塞尔曲线数据不完整");
		}
	}

	/** Preserves each source key's outgoing curve while frames are rebuilt or reordered. */
	private static void copyCurves(Animation.Timeline source, Animation.Timeline target,
		int[] sourceFrames, int[] curveTypes) {
		if (!(source instanceof CurveTimeline sourceCurve) || !(target instanceof CurveTimeline targetCurve)) return;
		validateCurveData(source);
		if (source.getFrameEntries() != target.getFrameEntries())
			throw new IllegalArgumentException("曲线轨道的帧布局不一致");
		if (sourceFrames.length != target.getFrameCount() || curveTypes.length != target.getFrameCount())
			throw new IllegalArgumentException("曲线映射与目标帧数不一致");
		if (source instanceof DeformTimeline) {
			copyDeformCurves(sourceCurve, targetCurve, source, target, sourceFrames, curveTypes);
			return;
		}

		float[] sourceData = curveData(sourceCurve);
		float[] targetData = curveData(targetCurve);
		float[] sourceValues = source.getFrames();
		float[] targetValues = target.getFrames();
		int entries = source.getFrameEntries();
		int channels = curveChannelCount(source);
		int targetBezier = target.getFrameCount();

		for (int targetFrame = 0; targetFrame < target.getFrameCount() - 1; targetFrame++) {
			int type = curveTypes[targetFrame];
			if (type == CurveTimeline.STEPPED) {
				targetCurve.setStepped(targetFrame);
				continue;
			}
			if (type < CurveTimeline.BEZIER) {
				targetCurve.setLinear(targetFrame);
				continue;
			}

			int sourceFrame = sourceFrames[targetFrame];
			if (sourceFrame < 0 || sourceFrame >= source.getFrameCount() - 1) {
				targetCurve.setLinear(targetFrame);
				continue;
			}
			int sourceType = sourceCurve.getCurveType(sourceFrame);
			if (sourceType < CurveTimeline.BEZIER) {
				targetCurve.setLinear(targetFrame);
				continue;
			}

			int sourceBezier = sourceType - CurveTimeline.BEZIER;
			int required = channels * CurveTimeline.BEZIER_SIZE;
			if (sourceBezier + required > sourceData.length || targetBezier + required > targetData.length)
				throw new IllegalStateException("Spine 贝塞尔曲线数据不完整");
			targetData[targetFrame] = CurveTimeline.BEZIER + targetBezier;

			int sourceBase = sourceFrame * entries;
			int sourceNext = sourceBase + entries;
			int targetBase = targetFrame * entries;
			int targetNext = targetBase + entries;
			for (int channel = 0; channel < channels; channel++) {
				int sourceBlock = sourceBezier + channel * CurveTimeline.BEZIER_SIZE;
				int targetBlock = targetBezier + channel * CurveTimeline.BEZIER_SIZE;
				for (int point = 0; point < CurveTimeline.BEZIER_SIZE / 2; point++) {
					float u = (point + 1) * 0.1f;
					int offset = point * 2;
					targetData[targetBlock + offset] = sourceData[sourceBlock + offset]
						+ (targetValues[targetBase] - sourceValues[sourceBase]) * (1f - u)
						+ (targetValues[targetNext] - sourceValues[sourceNext]) * u;
					targetData[targetBlock + offset + 1] = sourceData[sourceBlock + offset + 1]
						+ (targetValues[targetBase + channel + 1] - sourceValues[sourceBase + channel + 1]) * (1f - u)
						+ (targetValues[targetNext + channel + 1] - sourceValues[sourceNext + channel + 1]) * u;
				}
			}
			targetBezier += required;
		}
		targetCurve.shrink((targetBezier - target.getFrameCount()) / CurveTimeline.BEZIER_SIZE);
	}

	private static void copyDeformCurves(CurveTimeline sourceCurve, CurveTimeline targetCurve,
		Animation.Timeline source, Animation.Timeline target, int[] sourceFrames, int[] curveTypes) {
		float[] sourceData = curveData(sourceCurve);
		float[] sourceTimes = source.getFrames();
		float[] targetTimes = target.getFrames();
		int targetBezier = 0;
		for (int targetFrame = 0; targetFrame < target.getFrameCount() - 1; targetFrame++) {
			int type = curveTypes[targetFrame];
			if (type == CurveTimeline.STEPPED) {
				targetCurve.setStepped(targetFrame);
				continue;
			}
			if (type < CurveTimeline.BEZIER) {
				targetCurve.setLinear(targetFrame);
				continue;
			}
			int sourceFrame = sourceFrames[targetFrame];
			if (sourceFrame < 0 || sourceFrame >= source.getFrameCount() - 1) continue;
			int sourceBlock = sourceCurve.getCurveType(sourceFrame) - CurveTimeline.BEZIER;
			if (sourceBlock < source.getFrameCount()
				|| sourceBlock + CurveTimeline.BEZIER_SIZE > sourceData.length) continue;
			float sourceTime1 = sourceTimes[sourceFrame], sourceTime2 = sourceTimes[sourceFrame + 1];
			float sourceDuration = sourceTime2 - sourceTime1;
			float targetTime1 = targetTimes[targetFrame], targetTime2 = targetTimes[targetFrame + 1];
			float targetDuration = targetTime2 - targetTime1;
			if (!(sourceDuration > 0f) || !(targetDuration > 0f)) continue;
			float[] sourceX = solveBezierControls(sourceTime1, sourceTime2, sourceData, sourceBlock);
			float[] sourceY = solveBezierControls(0f, 1f, sourceData, sourceBlock + 1);
			float cx1 = (sourceX[0] - sourceTime1) / sourceDuration;
			float cx2 = (sourceX[1] - sourceTime1) / sourceDuration;
			targetCurve.setBezier(targetBezier++, targetFrame, 0, targetTime1, 0f,
				targetTime1 + targetDuration * cx1, sourceY[0],
				targetTime1 + targetDuration * cx2, sourceY[1], targetTime2, 1f);
		}
		targetCurve.shrink(targetBezier);
	}

	private void replaceTimeline(Animation anim, Animation.Timeline old, Animation.Timeline rebuilt) {
		Array<Animation.Timeline> list = anim.getTimelines();
		for (int i = 0; i < list.size; i++) {
			if (list.get(i) == old) {
				list.set(i, rebuilt);
				doc.migrateGraphViewState(anim.getName(), old, rebuilt);
				break;
			}
		}
		refreshAnimation(anim);
	}

	private void removeTimeline(Animation animation, Animation.Timeline timeline) {
		if (animation == null || timeline == null) return;
		if (!animation.getTimelines().removeValue(timeline, true)) return;
		doc.removeTimelineEditorState(animation.getName(), timeline);
		refreshAnimation(animation);
	}

	/** 时间线变化后重算 propertyIDs 并刷新动画时长 */
	public void refreshAnimation(Animation anim) {
		if (anim == null) return;
		Array<Animation.Timeline> list = anim.getTimelines();
		// 收集骨骼索引
		IntArray bones = new IntArray();
		for (Animation.Timeline tl : list) {
			if (tl instanceof BoneTimeline) {
				int bi = ((BoneTimeline) tl).getBoneIndex();
				if (!bones.contains(bi)) bones.add(bi);
			}
		}
		anim.setTimelines(list, bones);
		refreshAnimationDuration(anim);
	}

	private void refreshAnimationDuration(Animation anim) {
		// 时长 = 最晚帧时间
		float dur = 0;
		for (Animation.Timeline tl : anim.getTimelines()) {
			if (tl.getFrameCount() > 0) dur = Math.max(dur, tl.getFrames()[tl.getFrames().length - tl.getFrameEntries()]);
		}
		anim.setDuration(dur);
		disableInvalidLoopingSliderReferences(anim, dur);
		syncTrackEntryDurations(anim, dur);
	}

	private void disableInvalidLoopingSliderReferences(Animation animation, float duration) {
		if (duration > 0f || doc.data == null) return;
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint instanceof SliderData slider && slider.getAnimation() == animation && slider.getLoop())
				slider.setLoop(false);
	}

	private void syncTrackEntryDurations(Animation animation, float duration) {
		if (doc.animationState == null) return;
		for (AnimationState.TrackEntry root : doc.animationState.getTracks()) {
			for (AnimationState.TrackEntry entry = root; entry != null; entry = entry.getNext()) {
				if (entry.getAnimation() == animation)
					entry.setAnimationEnd(Math.max(entry.getAnimationStart(), duration));
			}
		}
	}

	/**
	 * Removes only redundant keys on fully linear continuous timelines. Discrete timelines,
	 * stepped/Bezier segments, and explicit equal-value holds are left untouched.
	 */
	public CleanupResult cleanupAnimation(Animation animation, float tolerance) {
		if (animation == null || doc.data == null || !doc.data.getAnimations().contains(animation, true))
			throw new IllegalArgumentException("清理目标动画无效");
		if (!Float.isFinite(tolerance) || tolerance < 0f)
			throw new IllegalArgumentException("清理容差必须是非负有限数值");
		Array<Timeline> timelines = animation.getTimelines();
		IdentityHashMap<Timeline, Timeline> replacements = new IdentityHashMap<>();
		int removed = 0;
		for (Timeline timeline : timelines) {
			Timeline cleaned = cleanedTimeline(timeline, tolerance);
			if (cleaned == timeline) continue;
			replacements.put(timeline, cleaned);
			removed += timeline.getFrameCount() - cleaned.getFrameCount();
		}
		if (replacements.isEmpty()) return new CleanupResult(0, 0);

		Array<Timeline> rebuilt = new Array<>(true, timelines.size, Timeline[]::new);
		for (Timeline timeline : timelines) rebuilt.add(replacements.getOrDefault(timeline, timeline));
		animation.setTimelines(rebuilt, new IntArray(animation.getBones()));
		for (Map.Entry<Timeline, Timeline> replacement : replacements.entrySet())
			doc.migrateGraphViewState(animation.getName(), replacement.getKey(), replacement.getValue());
		refreshAnimation(animation);
		doc.markDirty();
		return new CleanupResult(removed, replacements.size());
	}

	private Timeline cleanedTimeline(Timeline timeline, float tolerance) {
		if (!(timeline instanceof CurveTimeline curve) || timeline.getFrameCount() < 3) return timeline;
		boolean[] remove = new boolean[timeline.getFrameCount()];
		int removed = 0;
		for (int frame = 1; frame < timeline.getFrameCount() - 1; frame++) {
			if (curve.getCurveType(frame - 1) != CurveTimeline.LINEAR
				|| curve.getCurveType(frame) != CurveTimeline.LINEAR) continue;
			if (!isRedundantLinearFrame(timeline, frame, tolerance)) continue;
			remove[frame] = true;
			removed++;
		}
		if (removed == 0) return timeline;
		int keep = timeline.getFrameCount() - removed;
		Timeline cleaned = newTimelineLike(timeline, keep, bezierCapacity(timeline));
		int[] sourceFrames = new int[keep];
		int[] curveTypes = new int[keep];
		int target = 0;
		for (int source = 0; source < timeline.getFrameCount(); source++) {
			if (remove[source]) continue;
			copyEditableFrame(timeline, source, cleaned, target);
			sourceFrames[target] = source;
			curveTypes[target] = sourceCurveType(timeline, source);
			target++;
		}
		copyCurves(timeline, cleaned, sourceFrames, curveTypes);
		return cleaned;
	}

	private boolean isRedundantLinearFrame(Timeline timeline, int frame, float tolerance) {
		float previousTime = frameTime(timeline, frame - 1);
		float time = frameTime(timeline, frame);
		float nextTime = frameTime(timeline, frame + 1);
		float duration = nextTime - previousTime;
		if (!(duration > 0f) || !(time > previousTime) || !(time < nextTime)) return false;
		float alpha = (time - previousTime) / duration;
		boolean allEqual = true;
		if (timeline instanceof DeformTimeline deform) {
			float[] previous = deform.getVertices()[frame - 1];
			float[] current = deform.getVertices()[frame];
			float[] next = deform.getVertices()[frame + 1];
			if (previous.length != current.length || current.length != next.length) return false;
			for (int index = 0; index < current.length; index++) {
				if (!withinTolerance(current[index], previous[index] + (next[index] - previous[index]) * alpha,
					tolerance)) return false;
				allEqual &= withinTolerance(previous[index], current[index], tolerance)
					&& withinTolerance(current[index], next[index], tolerance);
			}
		} else {
			float[] values = timeline.getFrames();
			int entries = timeline.getFrameEntries();
			int previous = (frame - 1) * entries, current = frame * entries, next = (frame + 1) * entries;
			for (int entry = 1; entry < entries; entry++) {
				float expected = values[previous + entry]
					+ (values[next + entry] - values[previous + entry]) * alpha;
				if (!withinTolerance(values[current + entry], expected, tolerance)) return false;
				allEqual &= withinTolerance(values[previous + entry], values[current + entry], tolerance)
					&& withinTolerance(values[current + entry], values[next + entry], tolerance);
			}
		}
		return !allEqual;
	}

	private static boolean withinTolerance(float actual, float expected, float tolerance) {
		return Float.isFinite(actual) && Float.isFinite(expected) && Math.abs(actual - expected) <= tolerance;
	}

	/**
	 * Imports one animation by strict name mapping. Every referenced bone, slot, constraint, event,
	 * and attachment is validated before the destination animation is added.
	 */
	public Animation importAnimation(SkeletonData sourceData, Animation sourceAnimation) {
		AnimationImportResult result = importAnimations(sourceData, List.of(sourceAnimation),
			AnimationImportConflict.RENAME);
		return result.importedAnimations().get(0);
	}

	/** Validates every selected source before committing the batch as one document edit. */
	public AnimationImportResult importAnimations(SkeletonData sourceData,
		Iterable<Animation> sourceAnimations, AnimationImportConflict conflict) {
		if (sourceData == null || sourceAnimations == null || conflict == null)
			throw new IllegalArgumentException("批量动画导入参数不完整");
		if (doc.data == null) throw new IllegalStateException("当前骨架数据为空");
		ArrayList<Animation> requested = new ArrayList<>();
		Set<Animation> identities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		Set<String> sourceNames = new LinkedHashSet<>();
		for (Animation source : sourceAnimations) {
			if (source == null || !sourceData.getAnimations().contains(source, true) || !identities.add(source))
				throw new IllegalArgumentException("导入源动画无效或重复");
			if (!sourceNames.add(source.getName()))
				throw new IllegalArgumentException("导入源包含同名动画：" + source.getName());
			requested.add(source);
		}
		if (requested.isEmpty()) throw new IllegalArgumentException("没有选择要导入的动画");

		ArrayList<AnimationImportChange> prepared = new ArrayList<>();
		Set<String> reservedNames = new LinkedHashSet<>();
		for (Animation existing : doc.data.getAnimations()) reservedNames.add(existing.getName());
		int skipped = 0;
		int appended = 0;
		for (Animation source : requested) {
			Animation previous = doc.data.findAnimation(source.getName());
			if (previous != null && conflict == AnimationImportConflict.SKIP) {
				skipped++;
				continue;
			}
			String name = conflict == AnimationImportConflict.RENAME
				? uniqueAnimationName(source.getName(), reservedNames) : source.getName();
			if (conflict != AnimationImportConflict.REPLACE) previous = null;
			Array<Timeline> importedTimelines = new Array<>(true, source.getTimelines().size, Timeline[]::new);
			for (Timeline timeline : source.getTimelines()) importedTimelines.add(importTimeline(sourceData, timeline));
			Animation imported = new Animation(name);
			imported.setTimelines(importedTimelines, new IntArray());
			imported.getColor().set(source.getColor());
			refreshAnimation(imported);
			int index = previous == null ? doc.data.getAnimations().size + appended++
				: doc.data.getAnimations().indexOf(previous, true);
			prepared.add(new AnimationImportChange(previous, imported, index));
			reservedNames.add(name);
		}
		if (!prepared.isEmpty()) {
			applyAnimationImport(prepared, true);
			doc.markDirty();
		}
		return new AnimationImportResult(prepared, skipped);
	}

	/** Replays or reverts an import result after validating the complete expected state. */
	public void restoreAnimationImport(AnimationImportResult result, boolean imported) {
		if (doc.data == null || result == null) throw new IllegalArgumentException("动画导入历史无效");
		List<AnimationImportChange> changes = result.changes();
		for (AnimationImportChange change : changes) {
			Animation expected = imported ? change.previous() : change.imported();
			if (expected == null) {
				if (doc.data.findAnimation(change.imported().getName()) != null)
					throw new IllegalStateException("动画名称已被占用：" + change.imported().getName());
			} else if (!doc.data.getAnimations().contains(expected, true)) {
				throw new IllegalStateException("动画导入历史与当前项目不一致：" + expected.getName());
			}
		}
		applyAnimationImport(changes, imported);
		doc.markDirty();
	}

	private void applyAnimationImport(List<AnimationImportChange> changes, boolean forward) {
		String currentName = doc.animationState == null ? "" : doc.getCurrentAnimationName();
		float currentTime = doc.animationState == null ? 0f : doc.getCurrentTime();
		if (forward) {
			for (AnimationImportChange change : changes) applyAnimationImportChange(change, true);
		} else {
			for (int index = changes.size() - 1; index >= 0; index--)
				applyAnimationImportChange(changes.get(index), false);
		}
		if (doc.animationState != null) {
			Animation current = currentName.isEmpty() ? null : doc.data.findAnimation(currentName);
			if (current == null && !doc.data.getAnimations().isEmpty()) current = doc.data.getAnimations().first();
			if (current == null) doc.clearAnimation();
			else {
				AnimationState.TrackEntry track = doc.animationState.setAnimation(0, current, doc.shouldLoopTrack());
				if (track != null) track.setTrackTime(currentTime);
			}
		}
	}

	private void applyAnimationImportChange(AnimationImportChange change, boolean forward) {
		Array<Animation> animations = doc.data.getAnimations();
		Animation remove = forward ? change.previous() : change.imported();
		Animation add = forward ? change.imported() : change.previous();
		if (remove == null) {
			int index = Math.max(0, Math.min(change.index(), animations.size));
			animations.insert(index, add);
			return;
		}
		int index = animations.indexOf(remove, true);
		if (index < 0) throw new IllegalStateException("找不到待替换动画：" + remove.getName());
		if (add == null) animations.removeIndex(index);
		else animations.set(index, add);
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint instanceof SliderData slider && slider.getAnimation() == remove)
				slider.setAnimation(add);
	}

	private Timeline importTimeline(SkeletonData sourceData, Timeline source) {
		if (source instanceof DrawOrderFolderTimeline folder)
			return importDrawOrderFolder(sourceData, folder);
		if (source instanceof DrawOrderTimeline order) return importDrawOrder(sourceData, order);
		if (source instanceof EventTimeline events) return importEvents(events);

		Integer constraintIndex = null, slotIndex = null, boneIndex = null;
		Attachment attachment = null;
		if (source instanceof BoneTimeline bone) {
			boneIndex = mappedBoneIndex(sourceData, bone.getBoneIndex());
		} else if (source instanceof InheritTimeline inherit) {
			boneIndex = mappedBoneIndex(sourceData, inherit.getBoneIndex());
		} else if (source instanceof ConstraintTimeline constraint) {
			constraintIndex = mappedConstraintIndex(sourceData, constraint.getConstraintIndex(), source);
		}
		if (source instanceof AttachmentTimeline attachmentTimeline) {
			slotIndex = mappedSlotIndex(sourceData, attachmentTimeline.getSlotIndex());
			requireAttachmentNames(attachmentTimeline, slotIndex);
		} else if (source instanceof SequenceTimeline sequence) {
			slotIndex = mappedSlotIndex(sourceData, sequence.getSlotIndex());
			attachment = mappedAttachment(sourceData, sequence.getSlotIndex(), sequence.getAttachment(), slotIndex);
			if (!(attachment instanceof HasSequence hasSequence) || hasSequence.getSequence() == null)
				throw new IllegalArgumentException("目标附件缺少 Sequence：" + attachment.getName());
		} else if (source instanceof DeformTimeline deform) {
			slotIndex = mappedSlotIndex(sourceData, deform.getSlotIndex());
			attachment = mappedAttachment(sourceData, deform.getSlotIndex(), deform.getAttachment(), slotIndex);
			if (!(attachment instanceof VertexAttachment target))
				throw new IllegalArgumentException("目标附件不支持 Deform：" + attachment.getName());
			requireCompatibleDeform(sourceData, deform.getAttachment(), target);
		} else if (source instanceof SlotCurveTimeline slotTimeline) {
			slotIndex = mappedSlotIndex(sourceData, slotTimeline.getSlotIndex());
		}
		return cloneTimeline(source, constraintIndex, attachment, slotIndex, boneIndex);
	}

	private int mappedBoneIndex(SkeletonData sourceData, int sourceIndex) {
		if (sourceIndex < 0 || sourceIndex >= sourceData.getBones().size)
			throw new IllegalArgumentException("源动画引用了无效骨骼索引 " + sourceIndex);
		String name = sourceData.getBones().get(sourceIndex).getName();
		BoneData target = doc.data.findBone(name);
		if (target == null) throw new IllegalArgumentException("当前骨架缺少骨骼：" + name);
		return target.getIndex();
	}

	private int mappedSlotIndex(SkeletonData sourceData, int sourceIndex) {
		if (sourceIndex < 0 || sourceIndex >= sourceData.getSlots().size)
			throw new IllegalArgumentException("源动画引用了无效槽索引 " + sourceIndex);
		String name = sourceData.getSlots().get(sourceIndex).getName();
		SlotData target = doc.data.findSlot(name);
		if (target == null) throw new IllegalArgumentException("当前骨架缺少槽：" + name);
		return target.getIndex();
	}

	private int mappedConstraintIndex(SkeletonData sourceData, int sourceIndex, Timeline timeline) {
		if (sourceIndex == -1 && (timeline instanceof PhysicsConstraintTimeline
			|| timeline instanceof PhysicsConstraintResetTimeline)) return -1;
		if (sourceIndex < 0 || sourceIndex >= sourceData.getConstraints().size)
			throw new IllegalArgumentException("源动画引用了无效约束索引 " + sourceIndex);
		ConstraintData<?, ?> sourceConstraint = sourceData.getConstraints().get(sourceIndex);
		for (int index = 0; index < doc.data.getConstraints().size; index++) {
			ConstraintData<?, ?> target = doc.data.getConstraints().get(index);
			if (target.getClass() == sourceConstraint.getClass()
				&& target.getName().equals(sourceConstraint.getName())) return index;
		}
		throw new IllegalArgumentException("当前骨架缺少同类型约束：" + sourceConstraint.getName());
	}

	private void requireAttachmentNames(AttachmentTimeline timeline, int targetSlotIndex) {
		for (String name : timeline.getAttachmentNames()) {
			if (name == null) continue;
			boolean found = false;
			for (Skin skin : doc.data.getSkins()) {
				if (skin.getAttachment(targetSlotIndex, name) != null) { found = true; break; }
			}
			if (!found) throw new IllegalArgumentException("目标槽缺少附件：" + name);
		}
	}

	private Attachment mappedAttachment(SkeletonData sourceData, int sourceSlotIndex,
		Attachment sourceAttachment, int targetSlotIndex) {
		for (Skin sourceSkin : sourceData.getSkins()) {
			for (Skin.SkinEntry entry : sourceSkin.getAttachments()) {
				Attachment candidate = entry.getAttachment();
				if (entry.getSlotIndex() != sourceSlotIndex || candidate != sourceAttachment
					&& candidate.getTimelineAttachment() != sourceAttachment) continue;
				Skin targetSkin = doc.data.findSkin(sourceSkin.getName());
				if (targetSkin == null)
					throw new IllegalArgumentException("当前骨架缺少皮肤：" + sourceSkin.getName());
				Attachment target = targetSkin.getAttachment(targetSlotIndex, entry.getPlaceholder());
				if (target == null)
					throw new IllegalArgumentException("当前骨架缺少附件：" + sourceSkin.getName() + " / "
						+ entry.getPlaceholder());
				return target.getTimelineAttachment();
			}
		}
		throw new IllegalArgumentException("无法定位源动画附件：" + sourceAttachment.getName());
	}

	private void requireCompatibleDeform(SkeletonData sourceData, VertexAttachment source,
		VertexAttachment target) {
		if (source.getWorldVerticesLength() != target.getWorldVerticesLength()
			|| source.getVertices().length != target.getVertices().length)
			throw new IllegalArgumentException("Deform 附件拓扑不一致：" + source.getName());
		int[] sourceBones = source.getBones(), targetBones = target.getBones();
		if (sourceBones == null || targetBones == null) {
			if (sourceBones != targetBones)
				throw new IllegalArgumentException("Deform 附件权重类型不一致：" + source.getName());
			return;
		}
		if (sourceBones.length != targetBones.length)
			throw incompatibleDeformWeights(source);
		int sourceCursor = 0, targetCursor = 0;
		int vertexCount = source.getWorldVerticesLength() / 2;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			if (sourceCursor >= sourceBones.length || targetCursor >= targetBones.length)
				throw incompatibleDeformWeights(source);
			int sourceCount = sourceBones[sourceCursor++];
			int targetCount = targetBones[targetCursor++];
			if (sourceCount <= 0 || sourceCount != targetCount
				|| sourceCount > sourceBones.length - sourceCursor
				|| targetCount > targetBones.length - targetCursor)
				throw incompatibleDeformWeights(source);
			for (int index = 0; index < sourceCount; index++, sourceCursor++, targetCursor++)
				if (mappedBoneIndex(sourceData, sourceBones[sourceCursor]) != targetBones[targetCursor])
					throw new IllegalArgumentException("Deform 附件权重骨骼不一致：" + source.getName());
		}
		if (sourceCursor != sourceBones.length || targetCursor != targetBones.length)
			throw incompatibleDeformWeights(source);
	}

	private static IllegalArgumentException incompatibleDeformWeights(VertexAttachment attachment) {
		return new IllegalArgumentException("Deform 附件权重拓扑不一致：" + attachment.getName());
	}

	private Timeline importEvents(EventTimeline source) {
		EventTimeline imported = new EventTimeline(source.getFrameCount());
		for (int frame = 0; frame < source.getFrameCount(); frame++) {
			Event event = eventAt(source, frame);
			EventData definition = doc.data.findEvent(event.getData().getName());
			if (definition == null)
				throw new IllegalArgumentException("当前骨架缺少事件定义：" + event.getData().getName());
			imported.setFrame(frame, copyEvent(event, event.getTime(), definition));
		}
		return imported;
	}

	private DrawOrderTimeline importDrawOrder(SkeletonData sourceData, DrawOrderTimeline source) {
		int[] slotMap = completeSlotMap(sourceData);
		DrawOrderTimeline imported = new DrawOrderTimeline(source.getFrameCount());
		for (int frame = 0; frame < source.getFrameCount(); frame++)
			imported.setFrame(frame, frameTime(source, frame), remapSlotIndices(drawOrderAt(source, frame), slotMap));
		return imported;
	}

	private DrawOrderFolderTimeline importDrawOrderFolder(SkeletonData sourceData,
		DrawOrderFolderTimeline source) {
		int[] mappedSlots = new int[source.getSlots().length];
		for (int index = 0; index < mappedSlots.length; index++)
			mappedSlots[index] = mappedSlotIndex(sourceData, source.getSlots()[index]);
		DrawOrderFolderTimeline imported = new DrawOrderFolderTimeline(source.getFrameCount(), mappedSlots,
			doc.data.getSlots().size);
		for (int frame = 0; frame < source.getFrameCount(); frame++)
			imported.setFrame(frame, frameTime(source, frame), drawOrderFolderAt(source, frame));
		return imported;
	}

	private int[] completeSlotMap(SkeletonData sourceData) {
		if (sourceData.getSlots().size != doc.data.getSlots().size)
			throw new IllegalArgumentException("绘制顺序动画要求源骨架与当前骨架拥有相同的槽集合");
		int[] map = new int[sourceData.getSlots().size];
		for (int index = 0; index < map.length; index++) map[index] = mappedSlotIndex(sourceData, index);
		return map;
	}

	// ================= 动画管理 =================

	public Animation addAnimation(String name) {
		if (doc.data == null) return null;
		String unique = uniqueAnimationName(name);
		Animation anim = new Animation(unique);
		Array<Timeline> empty = new Array<>(true, 0, Timeline[]::new);
		anim.setTimelines(empty, new IntArray());
		anim.setDuration(1f);
		doc.data.getAnimations().add(anim);
		doc.markDirty();
		return anim;
	}

	/** Creates an independent copy of an animation containing supported editable timelines and exact curve data. */
	public Animation duplicateAnimation(Animation source) {
		if (doc.data == null || source == null || !doc.data.getAnimations().contains(source, true))
			throw new IllegalArgumentException("要复制的动画无效");
		Array<Timeline> copiedTimelines = new Array<>(true, source.getTimelines().size, Timeline[]::new);
		for (Timeline timeline : source.getTimelines()) copiedTimelines.add(cloneTimeline(timeline));

		Animation duplicate = new Animation(uniqueAnimationName(source.getName() + " copy"));
		duplicate.setTimelines(copiedTimelines, new IntArray(source.getBones()));
		duplicate.setDuration(source.getDuration());
		duplicate.getColor().set(source.getColor());
		doc.data.getAnimations().add(duplicate);
		doc.markDirty();
		return duplicate;
	}

	public boolean removeAnimation(Animation anim) {
		if (doc.data == null || anim == null) return false;
		if (getAnimationDeleteBlockReason(anim) != null) return false;
		int removedIndex = doc.data.getAnimations().indexOf(anim, true);
		boolean current = doc.animationState != null && doc.getCurrentAnimationName().equals(anim.getName());
		if (!doc.data.getAnimations().removeValue(anim, true)) return false;
		doc.removeEditorAnimationState(anim.getName());
		doc.markDirty();
		if (current) {
			doc.animationState.clearTracks();
			if (doc.data.getAnimations().isEmpty()) {
				doc.clearAnimation();
			} else {
				int replacementIndex = Math.min(removedIndex, doc.data.getAnimations().size - 1);
				doc.setAnimation(doc.data.getAnimations().get(replacementIndex).getName());
			}
		}
		return true;
	}

	/** Returns why an animation cannot be removed, or null when removal is safe. */
	public String getAnimationDeleteBlockReason(Animation anim) {
		if (doc.data == null || anim == null || !doc.data.getAnimations().contains(anim, true))
			return "动画数据无效";
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
			if (constraint instanceof SliderData slider && slider.getAnimation() == anim)
				return "动画仍被滑块约束 " + slider.getName() + " 引用";
		}
		return null;
	}

	public String renameAnimation(Animation anim, String newName) {
		if (anim == null || newName == null || newName.trim().isEmpty()) return null;
		if (doc.data == null || !doc.data.getAnimations().contains(anim, true)) return null;
		if (anim.getName().equals(newName.trim())) return anim.getName();
		String oldName = anim.getName();
		String unique = uniqueAnimationName(newName);
		// Animation.name 是 final？检查——源码里 name 为 final，故须重建替换
		replaceAnimationWithName(anim, unique);
		doc.renameEditorAnimationState(oldName, unique);
		doc.markDirty();
		return unique;
	}

	private void replaceAnimationWithName(Animation orig, String newName) {
		Array<Animation> list = doc.data.getAnimations();
		boolean current = doc.animationState != null && doc.getCurrentAnimationName().equals(orig.getName());
		float keepTime = current ? doc.getCurrentTime() : 0f;
		Animation rebuilt = new Animation(newName);
		rebuilt.setTimelines(orig.getTimelines(), orig.getBones());
		rebuilt.setDuration(orig.getDuration());
		rebuilt.getColor().set(orig.getColor());
		for (int i = 0; i < list.size; i++) {
			if (list.get(i) == orig) {
				list.set(i, rebuilt);
				break;
			}
		}
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
			if (constraint instanceof SliderData slider && slider.getAnimation() == orig)
				slider.setAnimation(rebuilt);
		}
		if (current) {
			AnimationState.TrackEntry track = doc.animationState.setAnimation(0, rebuilt, doc.shouldLoopTrack());
			if (track != null) track.setTrackTime(keepTime);
		}
	}

	private String uniqueAnimationName(String base) {
		return uniqueAnimationName(base, Set.of());
	}

	private String uniqueAnimationName(String base, Set<String> reserved) {
		String name = base == null || base.trim().isEmpty() ? "Animation" : base.trim();
		if (findAnimationData(name) == null && !reserved.contains(name)) return name;
		int n = 2;
		while (findAnimationData(name + n) != null || reserved.contains(name + n)) n++;
		return name + n;
	}

	private Animation findAnimationData(String name) {
		if (doc.data == null) return null;
		return doc.data.findAnimation(name);
	}
}
