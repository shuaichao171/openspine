package com.openspine.model;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BonePose;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.TransformConstraintPose;
import com.esotericsoftware.spine.TransformConstraintData.FromProperty;
import com.esotericsoftware.spine.TransformConstraintData.FromRotate;
import com.esotericsoftware.spine.TransformConstraintData.FromScaleX;
import com.esotericsoftware.spine.TransformConstraintData.FromScaleY;
import com.esotericsoftware.spine.TransformConstraintData.FromShearY;
import com.esotericsoftware.spine.TransformConstraintData.FromX;
import com.esotericsoftware.spine.TransformConstraintData.FromY;
import com.esotericsoftware.spine.TransformConstraintData.ToProperty;
import com.esotericsoftware.spine.TransformConstraintData.ToRotate;
import com.esotericsoftware.spine.TransformConstraintData.ToScaleX;
import com.esotericsoftware.spine.TransformConstraintData.ToScaleY;
import com.esotericsoftware.spine.TransformConstraintData.ToShearY;
import com.esotericsoftware.spine.TransformConstraintData.ToX;
import com.esotericsoftware.spine.TransformConstraintData.ToY;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.HasSequence;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.VertexAttachment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据层编辑器：封装对 SkeletonData 的增删改骨架/槽/命名/复制粘贴。
 *
 * 关键约束（来自 spine-libgdx 4.3.5 源码调研）：
 * - BoneData.index 与 parent 均为 final，Skeleton 构造按 bones[parent.index] 取父骨骼，
 *   且约定"父在前"。因此任何改变连接关系/顺序的操作，都要把整个 bones 数组
 *   拓扑重排并重建（新 index = 数组位置）。
 * - BoneData.name 为 final：重命名即重建对应 BoneData。
 * - SkeletonData.bones / slots / skins / animations 是可变 Array。
 */
public class SkeletonEditor {
	public final SkeletonDocument doc;
	public record BonePoseState(float x, float y, float rotation, float scaleX, float scaleY,
		float shearX, float shearY, BoneData.Inherit inherit) {}
	public record BoneSetupState(BonePoseState pose, float length, boolean skinRequired) {}
	public record BoneAppearanceState(Color color, String icon, float iconSize, float iconRotation, boolean visible) {
		public BoneAppearanceState {
			color = color == null ? new Color(Color.WHITE) : new Color(color);
			icon = icon == null ? "" : icon;
		}
		@Override public Color color() { return new Color(color); }
	}
	public record IkConstraintSetup(String name, BoneData firstBone, BoneData secondBone, BoneData target,
		boolean skinRequired, ScaleYMode scaleYMode, float mix, float softness, int bendDirection,
		boolean compress, boolean stretch) {}
	public enum TransformProperty { ROTATE, X, Y, SCALE_X, SCALE_Y, SHEAR_Y }
	public record TransformToPropertySetup(TransformProperty property, float offset, float max, float scale) {}
	public record TransformFromPropertySetup(TransformProperty property, float offset,
		List<TransformToPropertySetup> targets) {
		public TransformFromPropertySetup {
			targets = targets == null ? List.of()
				: java.util.Collections.unmodifiableList(new ArrayList<>(targets));
		}
	}
	public record TransformConstraintSetup(String name, List<BoneData> bones, BoneData source,
		boolean skinRequired, boolean localSource, boolean localTarget, boolean additive, boolean clamp,
		float offsetRotation, float offsetX, float offsetY, float offsetScaleX, float offsetScaleY,
		float offsetShearY, float mixRotate, float mixX, float mixY, float mixScaleX, float mixScaleY,
		float mixShearY, List<TransformFromPropertySetup> properties) {
		public TransformConstraintSetup {
			bones = bones == null ? List.of()
				: java.util.Collections.unmodifiableList(new ArrayList<>(bones));
			properties = properties == null ? List.of()
				: java.util.Collections.unmodifiableList(new ArrayList<>(properties));
		}
	}
	public record PathConstraintSetup(String name, List<BoneData> bones, SlotData slot, boolean skinRequired,
		PathConstraintData.PositionMode positionMode, PathConstraintData.SpacingMode spacingMode,
		PathConstraintData.RotateMode rotateMode, float offsetRotation, float position, float spacing,
		float mixRotate, float mixX, float mixY) {
		public PathConstraintSetup {
			bones = bones == null ? List.of()
				: java.util.Collections.unmodifiableList(new ArrayList<>(bones));
		}
	}
	public record PhysicsConstraintSetup(String name, BoneData bone, boolean skinRequired, ScaleYMode scaleYMode,
		float x, float y, float rotate, float scaleX, float shearX, float limit, int fps,
		float inertia, float strength, float damping, float mass, float wind, float gravity, float mix,
		boolean inertiaGlobal, boolean strengthGlobal, boolean dampingGlobal, boolean massGlobal,
		boolean windGlobal, boolean gravityGlobal, boolean mixGlobal) {}
	public record SliderConstraintSetup(String name, Animation animation, boolean skinRequired,
		boolean additive, boolean loop, BoneData bone, TransformProperty property, float sourceOffset,
		float targetOffset, float scale, float max, boolean local, float time, float mix) {}
	public enum StandardAttachmentType { BOUNDING_BOX, PATH, POINT, CLIPPING }
	/** Editable image-sequence setup shared by Region and Mesh attachments. */
	public record SequenceAttachmentState(String path, int count, int start, int digits, int setupIndex) {
		public boolean sameContent(SequenceAttachmentState other) {
			return other != null && java.util.Objects.equals(path, other.path) && count == other.count
				&& start == other.start && digits == other.digits && setupIndex == other.setupIndex;
		}
	}
	private record AttachmentOwner(Skin skin, int slotIndex, String placeholder) {}
	private record AtlasRegionBinding(HasSequence attachment, TextureRegion[] previous,
		TextureRegion[] replacement) {}
	public static final class AtlasRegionState {
		private final List<AtlasRegionBinding> bindings;
		private AtlasRegionState(List<AtlasRegionBinding> bindings) { this.bindings = List.copyOf(bindings); }
	}
	/** Complete editable state for a non-image standard attachment. */
	public record StandardAttachmentState(StandardAttachmentType type, Color color, int vertexCount,
		int[] bones, float[] vertices, boolean closed, boolean constantSpeed, float[] lengths,
		float x, float y, float rotation, SlotData endSlot, boolean convex, boolean inverse) {
		public StandardAttachmentState {
			color = color == null ? null : new Color(color);
			bones = bones == null ? null : bones.clone();
			vertices = vertices == null ? null : vertices.clone();
			lengths = lengths == null ? null : lengths.clone();
		}

		@Override public Color color() { return color == null ? null : new Color(color); }
		@Override public int[] bones() { return bones == null ? null : bones.clone(); }
		@Override public float[] vertices() { return vertices == null ? null : vertices.clone(); }
		@Override public float[] lengths() { return lengths == null ? null : lengths.clone(); }
		public boolean weighted() { return bones != null; }
		public boolean sameContent(StandardAttachmentState other) {
			return other != null && type == other.type && sameColor(color, other.color)
				&& vertexCount == other.vertexCount && Arrays.equals(bones, other.bones)
				&& Arrays.equals(vertices, other.vertices) && closed == other.closed
				&& constantSpeed == other.constantSpeed && Arrays.equals(lengths, other.lengths)
				&& Float.compare(x, other.x) == 0 && Float.compare(y, other.y) == 0
				&& Float.compare(rotation, other.rotation) == 0 && endSlot == other.endSlot
				&& convex == other.convex && inverse == other.inverse;
		}
	}
	/** Complete editable state for an independent mesh. Arrays and colors are copied at the API boundary. */
	public record MeshGeometryState(Color color, String path, int[] bones, float[] vertices,
		float[] regionUVs, short[] triangles, int hullLength, short[] edges, float width, float height) {
		public MeshGeometryState {
			color = color == null ? null : new Color(color);
			bones = bones == null ? null : bones.clone();
			vertices = vertices == null ? null : vertices.clone();
			regionUVs = regionUVs == null ? null : regionUVs.clone();
			triangles = triangles == null ? null : triangles.clone();
			edges = edges == null ? null : edges.clone();
		}

		@Override public Color color() { return color == null ? null : new Color(color); }
		@Override public int[] bones() { return bones == null ? null : bones.clone(); }
		@Override public float[] vertices() { return vertices == null ? null : vertices.clone(); }
		@Override public float[] regionUVs() { return regionUVs == null ? null : regionUVs.clone(); }
		@Override public short[] triangles() { return triangles == null ? null : triangles.clone(); }
		@Override public short[] edges() { return edges == null ? null : edges.clone(); }
		public boolean weighted() { return bones != null; }
		public int vertexCount() { return regionUVs == null ? 0 : regionUVs.length / 2; }
		public boolean sameContent(MeshGeometryState other) {
			return other != null && sameColor(color, other.color)
				&& java.util.Objects.equals(path, other.path)
				&& Arrays.equals(bones, other.bones) && Arrays.equals(vertices, other.vertices)
				&& Arrays.equals(regionUVs, other.regionUVs) && Arrays.equals(triangles, other.triangles)
				&& hullLength == other.hullLength && Arrays.equals(edges, other.edges)
				&& Float.compare(width, other.width) == 0 && Float.compare(height, other.height) == 0;
		}
	}
	public record MeshWeldResult(MeshGeometryState geometry, int[] oldToNew, int[] newToOld,
		int weldedVertices) {
		public MeshWeldResult {
			if (geometry == null || oldToNew == null || newToOld == null || weldedVertices < 0
				|| oldToNew.length - newToOld.length != weldedVertices)
				throw new IllegalArgumentException("Mesh 焊接结果无效");
			oldToNew = oldToNew.clone();
			newToOld = newToOld.clone();
		}
		@Override public int[] oldToNew() { return oldToNew.clone(); }
		@Override public int[] newToOld() { return newToOld.clone(); }
	}
	/** Canonical skin entry used as a Linked Mesh source. One shared attachment is listed only once. */
	public record MeshAttachmentSource(Skin skin, SlotData slot, String placeholder, MeshAttachment mesh) {}
	/** A reversible logical-vertex permutation. Each new index points to its previous index. */
	public record MeshVertexOrder(int[] newToOld, int[] oldToNew, int hullVertexCount) {
		public MeshVertexOrder {
			if (newToOld == null || oldToNew == null || newToOld.length != oldToNew.length
				|| newToOld.length < 3 || hullVertexCount < 3 || hullVertexCount > newToOld.length)
				throw new IllegalArgumentException("Mesh 顶点排列无效");
			newToOld = newToOld.clone();
			oldToNew = oldToNew.clone();
			boolean[] seen = new boolean[newToOld.length];
			for (int newIndex = 0; newIndex < newToOld.length; newIndex++) {
				int oldIndex = newToOld[newIndex];
				if (oldIndex < 0 || oldIndex >= newToOld.length || seen[oldIndex]
					|| oldToNew[oldIndex] != newIndex)
					throw new IllegalArgumentException("Mesh 顶点排列不是完整置换");
				seen[oldIndex] = true;
			}
		}

		@Override public int[] newToOld() { return newToOld.clone(); }
		@Override public int[] oldToNew() { return oldToNew.clone(); }
		public int vertexCount() { return newToOld.length; }
		public MeshVertexOrder inverse(int previousHullVertexCount) {
			return new MeshVertexOrder(oldToNew, newToOld, previousHullVertexCount);
		}
		public boolean isIdentity(int previousHullVertexCount) {
			if (hullVertexCount != previousHullVertexCount) return false;
			for (int index = 0; index < newToOld.length; index++)
				if (newToOld[index] != index) return false;
			return true;
		}
	}
	/** Immutable setup-space data reused by every dab in one viewport weight-paint stroke. */
	public record MeshWeightPaintContext(int slotBoneIndex, int targetBoneIndex, float[] targetLocalVertices) {
		public MeshWeightPaintContext {
			if (slotBoneIndex < 0 || targetBoneIndex < 0 || targetLocalVertices == null
				|| (targetLocalVertices.length & 1) != 0 || !areFinite(targetLocalVertices))
				throw new IllegalArgumentException("Mesh 权重笔刷上下文无效");
			targetLocalVertices = targetLocalVertices.clone();
		}

		@Override public float[] targetLocalVertices() { return targetLocalVertices.clone(); }
		public int vertexCount() { return targetLocalVertices.length / 2; }
	}
	private record MeshDeformState(Animation.DeformTimeline timeline, float[] times, float[][] vertices) {}
	/** Immutable source geometry and every Deform frame sharing it, used for atomic topology undo/redo. */
	public static final class MeshTopologySnapshot {
		private final MeshAttachment mesh;
		private final MeshGeometryState geometry;
		private final List<MeshDeformState> deforms;

		private MeshTopologySnapshot(MeshAttachment mesh, MeshGeometryState geometry,
			List<MeshDeformState> deforms) {
			this.mesh = mesh;
			this.geometry = geometry;
			this.deforms = List.copyOf(deforms);
		}

		public MeshGeometryState geometry() { return geometry; }
	}
	private record MeshWeightInfluence(int boneIndex, float x, float y, float weight) {}
	private record MeshWeightEncoding(int[] bones, float[] vertices) {}
	private static final class MeshWeightAccumulator {
		float weight, weightedX, weightedY;
	}
	/** 复制/粘贴缓存 */
	private BoneData clipboard;
	private BoneSubtreeSnapshot subtreeClipboard;

	public SkeletonEditor(SkeletonDocument doc) {
		this.doc = doc;
	}

	// ================= 重建 =================

	/**
	 * 用当前 doc.data 重建 Skeleton 并复位动画/皮肤。
	 * 在修改 data 的任何操作之后调用。
	 */
	public void rebuildSkeleton() {
		if (doc.data == null) return;
		String keepAnim = doc.getCurrentAnimationName();
		float keepTime = doc.getCurrentTime();
		List<String> keepSkins = doc.getPreviewSkinNames();
		String keepSelection = doc.getSelectedBone() == null ? "" : doc.getSelectedBone().getData().getName();
		List<String> keepSelectedBones = doc.getSelectedBones().stream().map(bone -> bone.getData().getName()).toList();
		String keepSelectionKind = doc.getSelectionKind().name();
		ConstraintData<?, ?> keepConstraint = doc.getSelectedConstraint();
		String keepConstraintType = keepConstraint == null ? "" : keepConstraint.getClass().getSimpleName();
		String keepConstraintName = keepConstraint == null ? "" : keepConstraint.getName();
		String keepConstraintRelation = doc.getSelectedConstraintRelation();
		String keepSlot = doc.getSelectedSlotName();
		String keepPlaceholder = doc.getSelectedAttachmentPlaceholder();
		String keepAttachment = doc.getSelectedAttachmentName();
		java.util.HashMap<String, com.esotericsoftware.spine.BonePose> keepPose = new java.util.HashMap<>();
		if (doc.poseMode && doc.skeleton != null) {
			for (Bone bone : doc.skeleton.getBones()) {
				com.esotericsoftware.spine.BonePose pose = new com.esotericsoftware.spine.BonePose();
				pose.set(bone.getPose());
				keepPose.put(bone.getData().getName(), pose);
			}
		}

		doc.skeleton = new Skeleton(doc.data);
		doc.restorePreviewSkinBindings(keepSkins);
		doc.skeleton.setupPose();
		if (!keepPose.isEmpty()) {
			for (Bone bone : doc.skeleton.getBones()) {
				com.esotericsoftware.spine.BonePose pose = keepPose.get(bone.getData().getName());
				if (pose != null) bone.getPose().set(pose);
			}
		}

		doc.animationState = doc.newAnimationState();
		if (!keepAnim.isEmpty() && doc.data.findAnimation(keepAnim) != null) {
			AnimationState.TrackEntry tr = doc.animationState.setAnimation(0, keepAnim, doc.shouldLoopTrack());
			if (tr != null) tr.setTrackTime(keepTime);
		}
		if (!doc.poseMode && doc.animationState.getTrack(0) != null) {
			doc.animationState.apply(doc.skeleton);
		}
		doc.updateWorldTransform(doc.poseMode ? Physics.pose : Physics.reset);
		doc.restoreEditorSelection(keepSelectionKind, keepSelection, keepConstraintType, keepConstraintName,
			keepConstraintRelation, keepSlot, keepPlaceholder, keepAttachment);
		if (doc.getSelectionKind() == SkeletonDocument.SelectionKind.BONE && keepSelectedBones.size() > 1) {
			List<Bone> selected = new ArrayList<>();
			for (String name : keepSelectedBones) {
				Bone bone = doc.skeleton.findBone(name);
				if (bone != null) selected.add(bone);
			}
			doc.setSelectedBones(selected);
		}
	}

	public static BonePoseState bonePoseState(BonePose pose) {
		if (pose == null) throw new IllegalArgumentException("骨骼姿态不能为空");
		return new BonePoseState(pose.getX(), pose.getY(), pose.getRotation(), pose.getScaleX(),
			pose.getScaleY(), pose.getShearX(), pose.getShearY(), pose.getInherit());
	}

	public static BoneSetupState boneSetupState(BoneData bone) {
		if (bone == null) throw new IllegalArgumentException("骨骼数据不能为空");
		return new BoneSetupState(bonePoseState(bone.getSetupPose()), bone.getLength(), bone.getSkinRequired());
	}

	public static BoneAppearanceState boneAppearanceState(BoneData bone) {
		if (bone == null) throw new IllegalArgumentException("骨骼数据不能为空");
		return new BoneAppearanceState(bone.getColor(), bone.getIcon(), bone.getIconSize(),
			bone.getIconRotation(), bone.getVisible());
	}

	/** Applies editor-only bone display metadata without changing the runtime pose. */
	public boolean updateBoneAppearance(BoneData target, BoneAppearanceState state) {
		return updateBoneAppearance(target == null ? List.of() : List.of(target), state);
	}

	/** Applies one appearance edit atomically to all selected bones. */
	public boolean updateBoneAppearance(Iterable<BoneData> targets, BoneAppearanceState state) {
		if (doc.data == null || targets == null || state == null
			|| !areFinite(state.iconSize(), state.iconRotation()) || state.iconSize() < 0f) return false;
		Color color = state.color();
		if (color == null || !areFinite(color.r, color.g, color.b, color.a)) return false;
		ArrayList<BoneData> valid = new ArrayList<>();
		for (BoneData target : targets)
			if (target != null && doc.data.getBones().contains(target, true) && !valid.contains(target)) valid.add(target);
		if (valid.isEmpty()) return false;
		boolean changed = false;
		for (BoneData target : valid) {
			BoneAppearanceState before = boneAppearanceState(target);
			Color oldColor = before.color();
			if (oldColor.equals(color) && java.util.Objects.equals(before.icon(), state.icon())
				&& before.iconSize() == state.iconSize() && before.iconRotation() == state.iconRotation()
				&& before.visible() == state.visible()) continue;
			target.getColor().set(color);
			target.setIcon(state.icon().isBlank() ? null : state.icon().trim());
			target.setIconSize(state.iconSize());
			target.setIconRotation(state.iconRotation());
			target.setVisible(state.visible());
			changed = true;
		}
		if (!changed) return false;
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	/** Writes validated setup data to one bone and rebuilds runtime state. */
	public boolean updateBoneSetup(BoneData target, BoneSetupState state) {
		BonePoseState pose = state == null ? null : state.pose();
		if (doc.data == null || target == null || pose == null
			|| !doc.data.getBones().contains(target, true) || target.getIndex() < 0
			|| target.getIndex() >= doc.data.getBones().size
			|| doc.data.getBones().get(target.getIndex()) != target || pose.inherit() == null
			|| !areFinite(pose.x(), pose.y(), pose.rotation(), pose.scaleX(), pose.scaleY(),
				pose.shearX(), pose.shearY(), state.length()) || state.length() < 0f) return false;
		if (boneSetupState(target).equals(state)) return false;

		applyBonePoseState(target.getSetupPose(), pose);
		target.setLength(state.length());
		target.setSkinRequired(state.skinRequired());
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	/** Writes only a local pose while preserving the current setup length. */
	public boolean updateBoneSetupPose(BoneData target, BonePoseState state) {
		return target != null && updateBoneSetup(target,
			new BoneSetupState(state, target.getLength(), target.getSkinRequired()));
	}

	private static void applyBonePoseState(BonePose pose, BonePoseState state) {
		pose.setPosition(state.x(), state.y());
		pose.setRotation(state.rotation());
		pose.setScale(state.scaleX(), state.scaleY());
		pose.setShearX(state.shearX());
		pose.setShearY(state.shearY());
		pose.setInherit(state.inherit());
	}

	// ================= IK 约束 Setup =================

	public static IkConstraintSetup ikConstraintSetup(IkConstraintData constraint) {
		if (constraint == null) throw new IllegalArgumentException("IK 约束不能为空");
		Array<BoneData> bones = constraint.getBones();
		BoneData first = bones.size > 0 ? bones.get(0) : null;
		BoneData second = bones.size > 1 ? bones.get(1) : null;
		var pose = constraint.getSetupPose();
		return new IkConstraintSetup(constraint.getName(), first, second, constraint.getTarget(),
			constraint.getSkinRequired(), constraint.getScaleYMode(), pose.getMix(), pose.getSoftness(),
			pose.getBendDirection(), pose.getCompress(), pose.getStretch());
	}

	/** Returns null when the complete IK setup can be applied atomically. */
	public String validateIkConstraintSetup(IkConstraintData existing, IkConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (doc.data == null) return "当前项目没有骨架数据";
		if (existing != null && !doc.data.getConstraints().contains(existing, true))
			return "IK 约束已不在当前项目中";
		if (setup == null) return "IK Setup 数据不能为空";
		String name = setup.name() == null ? "" : setup.name().trim();
		if (name.isEmpty()) return "IK 约束名称不能为空";
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
			if (constraint != existing && constraint.getName().equals(name)) return "约束名称已存在";
		}
		if (!validBoneReference(setup.firstBone())) return "第一个受约束骨骼无效";
		if (setup.secondBone() != null) {
			if (!validBoneReference(setup.secondBone())) return "第二个受约束骨骼无效";
			if (setup.secondBone() == setup.firstBone()) return "两个受约束骨骼不能相同";
			if (setup.secondBone().getParent() != setup.firstBone()) return "第二个骨骼必须是第一个骨骼的直接子骨骼";
		}
		if (!validBoneReference(setup.target())) return "IK 目标骨骼无效";
		if (setup.target() == setup.firstBone() || setup.target() == setup.secondBone())
			return "IK 目标不能同时是受约束骨骼";
		for (BoneData ancestor = setup.target(); ancestor != null; ancestor = ancestor.getParent()) {
			if (ancestor == setup.firstBone() || ancestor == setup.secondBone())
				return "IK 目标不能位于受约束骨骼的子树中";
		}
		if (setup.scaleYMode() == null) return "缩放 Y 模式无效";
		if (!Float.isFinite(setup.mix()) || setup.mix() < 0f || setup.mix() > 1f)
			return "IK 混合必须是 0 到 1 的有限数值";
		if (!Float.isFinite(setup.softness()) || setup.softness() < 0f)
			return "IK 柔度必须是不小于 0 的有限数值";
		if (setup.bendDirection() != -1 && setup.bendDirection() != 1)
			return "IK 弯曲方向必须是正向或反向";
		if (activationSkin != null && !doc.data.getSkins().contains(activationSkin, true))
			return "预览皮肤已不在当前项目中";
		if (activeInSkin && (!setup.skinRequired() || activationSkin == null))
			return "只有皮肤必需的 IK 约束才能在当前皮肤中激活";
		return null;
	}

	public IkConstraintData addIkConstraint(IkConstraintSetup setup, Skin activationSkin, boolean activeInSkin) {
		if (validateIkConstraintSetup(null, setup, activationSkin, activeInSkin) != null) return null;
		IkConstraintData created = createIkConstraint(setup);
		doc.data.getConstraints().add(created);
		if (activeInSkin) activationSkin.getConstraints().add(created);
		doc.markDirty();
		rebuildSkeleton();
		return created;
	}

	/** Replaces one immutable-name IK data object while preserving its unified constraint index. */
	public IkConstraintData updateIkConstraint(IkConstraintData existing, IkConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (validateIkConstraintSetup(existing, setup, activationSkin, activeInSkin) != null) return null;
		boolean sameSetup = ikConstraintSetup(existing).equals(normalizeIkSetup(setup));
		boolean currentActive = isIkConstraintActiveInSkin(existing, activationSkin);
		boolean hasRedundantActivation = !setup.skinRequired() && anySkinContains(existing);
		if (sameSetup && currentActive == activeInSkin && !hasRedundantActivation) return existing;

		int constraintIndex = doc.data.getConstraints().indexOf(existing, true);
		IkConstraintData replacement = createIkConstraint(setup);
		doc.data.getConstraints().set(constraintIndex, replacement);
		for (Skin skin : doc.data.getSkins()) {
			for (int index = skin.getConstraints().size - 1; index >= 0; index--) {
				if (skin.getConstraints().get(index) != existing) continue;
				if (setup.skinRequired()) skin.getConstraints().set(index, replacement);
				else skin.getConstraints().removeIndex(index);
			}
		}
		if (setup.skinRequired() && activationSkin != null) {
			while (activationSkin.getConstraints().removeValue(replacement, true)) { }
			if (activeInSkin) activationSkin.getConstraints().add(replacement);
		}
		doc.replaceEditorConstraintState(existing, replacement);
		doc.markDirty();
		rebuildSkeleton();
		return replacement;
	}

	public boolean isIkConstraintActiveInSkin(IkConstraintData constraint, Skin skin) {
		return constraint != null && skin != null && skin.getConstraints().contains(constraint, true);
	}

	/** Returns null only when removing this IK cannot invalidate a direct skin or animation reference. */
	public String getIkConstraintDeleteBlockReason(IkConstraintData constraint) {
		if (doc.data == null || constraint == null) return "IK 约束数据无效";
		int index = doc.data.getConstraints().indexOf(constraint, true);
		if (index < 0) return "IK 约束已不在当前项目中";
		for (Skin skin : doc.data.getSkins()) {
			if (skin.getConstraints().contains(constraint, true)) return "皮肤 " + skin.getName() + " 仍激活该 IK 约束";
		}
		for (Animation animation : doc.data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (timeline instanceof Animation.ConstraintTimeline target && target.getConstraintIndex() == index)
					return "动画 " + animation.getName() + " 仍包含该 IK 约束的时间线";
			}
		}
		return null;
	}

	public boolean deleteIkConstraint(IkConstraintData constraint) {
		if (getIkConstraintDeleteBlockReason(constraint) != null) return false;
		return deleteConstraintData(constraint);
	}

	private boolean validBoneReference(BoneData bone) {
		return bone != null && doc.data.getBones().contains(bone, true) && bone.getIndex() >= 0
			&& bone.getIndex() < doc.data.getBones().size && doc.data.getBones().get(bone.getIndex()) == bone;
	}

	private boolean anySkinContains(ConstraintData<?, ?> constraint) {
		for (Skin skin : doc.data.getSkins())
			if (skin.getConstraints().contains(constraint, true)) return true;
		return false;
	}

	/** Returns every skin that currently activates the constraint, preserving project skin order. */
	public List<Skin> constraintActivationSkins(ConstraintData<?, ?> constraint) {
		if (doc.data == null || constraint == null || !doc.data.getConstraints().contains(constraint, true))
			return List.of();
		List<Skin> result = new ArrayList<>();
		for (Skin skin : doc.data.getSkins())
			if (skin.getConstraints().contains(constraint, true)) result.add(skin);
		return java.util.Collections.unmodifiableList(result);
	}

	/** Replaces all skin activation references for a constraint after an undo/redo setup restore. */
	public boolean setConstraintActivationSkins(ConstraintData<?, ?> constraint, List<Skin> activeSkins) {
		if (doc.data == null || constraint == null || activeSkins == null
			|| !doc.data.getConstraints().contains(constraint, true)) return false;
		java.util.IdentityHashMap<Skin, Boolean> requested = new java.util.IdentityHashMap<>();
		for (Skin skin : activeSkins) {
			if (skin == null || !doc.data.getSkins().contains(skin, true)
				|| requested.put(skin, Boolean.TRUE) != null) return false;
		}
		if (!constraint.getSkinRequired() && !requested.isEmpty()) return false;
		boolean changed = false;
		for (Skin skin : doc.data.getSkins()) {
			boolean shouldContain = requested.containsKey(skin);
			boolean contains = skin.getConstraints().contains(constraint, true);
			if (contains == shouldContain) continue;
			changed = true;
			while (skin.getConstraints().removeValue(constraint, true)) { }
			if (shouldContain) skin.getConstraints().add(constraint);
		}
		if (!changed) return false;
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	private static IkConstraintSetup normalizeIkSetup(IkConstraintSetup setup) {
		return new IkConstraintSetup(setup.name().trim(), setup.firstBone(), setup.secondBone(), setup.target(),
			setup.skinRequired(), setup.scaleYMode(), setup.mix(), setup.softness(), setup.bendDirection(),
			setup.compress(), setup.stretch());
	}

	private static IkConstraintData createIkConstraint(IkConstraintSetup rawSetup) {
		IkConstraintSetup setup = normalizeIkSetup(rawSetup);
		IkConstraintData created = new IkConstraintData(setup.name());
		created.getBones().add(setup.firstBone());
		if (setup.secondBone() != null) created.getBones().add(setup.secondBone());
		created.setTarget(setup.target());
		created.setSkinRequired(setup.skinRequired());
		created.setScaleYMode(setup.scaleYMode());
		var pose = created.getSetupPose();
		pose.setMix(setup.mix());
		pose.setSoftness(setup.softness());
		pose.setBendDirection(setup.bendDirection());
		pose.setCompress(setup.compress());
		pose.setStretch(setup.stretch());
		return created;
	}

	// ================= Transform constraint setup =================

	public static TransformConstraintSetup transformConstraintSetup(TransformConstraintData constraint) {
		if (constraint == null) throw new IllegalArgumentException("Transform constraint cannot be null");
		TransformConstraintPose pose = constraint.getSetupPose();
		List<TransformFromPropertySetup> properties = new ArrayList<>();
		for (FromProperty from : constraint.getProperties()) {
			List<TransformToPropertySetup> targets = new ArrayList<>();
			for (ToProperty to : from.to)
				targets.add(new TransformToPropertySetup(transformProperty(to), to.offset, to.max, to.scale));
			properties.add(new TransformFromPropertySetup(transformProperty(from), from.offset, targets));
		}
		List<BoneData> bones = new ArrayList<>();
		for (BoneData bone : constraint.getBones()) bones.add(bone);
		return new TransformConstraintSetup(constraint.getName(), bones, constraint.getSource(),
			constraint.getSkinRequired(), constraint.getLocalSource(), constraint.getLocalTarget(),
			constraint.getAdditive(), constraint.getClamp(), constraint.getOffsetRotation(),
			constraint.getOffsetX(), constraint.getOffsetY(), constraint.getOffsetScaleX(),
			constraint.getOffsetScaleY(), constraint.getOffsetShearY(), pose.getMixRotate(), pose.getMixX(),
			pose.getMixY(), pose.getMixScaleX(), pose.getMixScaleY(), pose.getMixShearY(), properties);
	}

	/** Standard same-channel mappings used for constraints created in OpenSpine. */
	public static List<TransformFromPropertySetup> defaultTransformProperties() {
		List<TransformFromPropertySetup> properties = new ArrayList<>();
		for (TransformProperty property : TransformProperty.values()) {
			TransformToPropertySetup target = new TransformToPropertySetup(property, 0f, 1f, 1f);
			properties.add(new TransformFromPropertySetup(property, 0f, List.of(target)));
		}
		return List.copyOf(properties);
	}

	/** Returns null when the complete Transform setup can be applied atomically. */
	public String validateTransformConstraintSetup(TransformConstraintData existing,
		TransformConstraintSetup setup, Skin activationSkin, boolean activeInSkin) {
		if (doc.data == null) return "The current project has no skeleton data";
		if (existing != null && !doc.data.getConstraints().contains(existing, true))
			return "The Transform constraint is no longer in this project";
		if (setup == null) return "Transform setup data cannot be null";
		String name = setup.name() == null ? "" : setup.name().trim();
		if (name.isEmpty()) return "Transform constraint name cannot be empty";
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint != existing && constraint.getName().equals(name)) return "Constraint name already exists";
		if (setup.bones().isEmpty()) return "Select at least one constrained bone";
		Set<BoneData> bones = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (BoneData bone : setup.bones()) {
			if (!validBoneReference(bone)) return "A constrained bone is invalid";
			if (!bones.add(bone)) return "A constrained bone is selected more than once";
		}
		if (!validBoneReference(setup.source())) return "The source bone is invalid";
		for (BoneData ancestor = setup.source(); ancestor != null; ancestor = ancestor.getParent())
			if (bones.contains(ancestor)) return "The source bone cannot be inside a constrained bone subtree";
		if (!areFinite(setup.offsetRotation(), setup.offsetX(), setup.offsetY(), setup.offsetScaleX(),
			setup.offsetScaleY(), setup.offsetShearY(), setup.mixRotate(), setup.mixX(), setup.mixY(),
			setup.mixScaleX(), setup.mixScaleY(), setup.mixShearY()))
			return "Transform offsets and mixes must be finite numbers";
		Set<TransformProperty> fromProperties = new HashSet<>();
		Set<TransformProperty> mappedTargets = new HashSet<>();
		for (TransformFromPropertySetup from : setup.properties()) {
			if (from == null || from.property() == null) return "A Transform source property is invalid";
			if (!fromProperties.add(from.property())) return "A Transform source property is duplicated";
			if (!Float.isFinite(from.offset())) return "Transform property values must be finite numbers";
			if (from.targets().isEmpty()) return "Each Transform source property needs a target";
			Set<TransformProperty> targets = new HashSet<>();
			for (TransformToPropertySetup to : from.targets()) {
				if (to == null || to.property() == null) return "A Transform target property is invalid";
				if (!targets.add(to.property())) return "A Transform target property is duplicated";
				mappedTargets.add(to.property());
				if (!areFinite(to.offset(), to.max(), to.scale()))
					return "Transform property values must be finite numbers";
			}
		}
		if (!mappedTargets.contains(TransformProperty.ROTATE) && setup.mixRotate() != 0f
			|| !mappedTargets.contains(TransformProperty.X) && setup.mixX() != 0f
			|| !mappedTargets.contains(TransformProperty.Y) && setup.mixY() != 0f
			|| !mappedTargets.contains(TransformProperty.SCALE_X) && setup.mixScaleX() != 0f
			|| !mappedTargets.contains(TransformProperty.SCALE_Y) && setup.mixScaleY() != 0f
			|| !mappedTargets.contains(TransformProperty.SHEAR_Y) && setup.mixShearY() != 0f)
			return "A nonzero Transform mix must have a matching target property";
		if (activationSkin != null && !doc.data.getSkins().contains(activationSkin, true))
			return "The preview skin is no longer in this project";
		if (activeInSkin && (!setup.skinRequired() || activationSkin == null))
			return "Only a skin-required Transform constraint can be active in the current skin";
		return null;
	}

	public TransformConstraintData addTransformConstraint(TransformConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (validateTransformConstraintSetup(null, setup, activationSkin, activeInSkin) != null) return null;
		TransformConstraintData created = createTransformConstraint(setup);
		doc.data.getConstraints().add(created);
		if (activeInSkin) activationSkin.getConstraints().add(created);
		doc.markDirty();
		rebuildSkeleton();
		return created;
	}

	/** Replaces immutable-name data while preserving the unified constraint index. */
	public TransformConstraintData updateTransformConstraint(TransformConstraintData existing,
		TransformConstraintSetup setup, Skin activationSkin, boolean activeInSkin) {
		if (validateTransformConstraintSetup(existing, setup, activationSkin, activeInSkin) != null) return null;
		TransformConstraintSetup normalized = normalizeTransformSetup(setup);
		boolean sameSetup = transformConstraintSetup(existing).equals(normalized);
		boolean currentActive = isTransformConstraintActiveInSkin(existing, activationSkin);
		boolean hasRedundantActivation = !setup.skinRequired() && anySkinContains(existing);
		if (sameSetup && currentActive == activeInSkin && !hasRedundantActivation) return existing;

		int constraintIndex = doc.data.getConstraints().indexOf(existing, true);
		TransformConstraintData replacement = createTransformConstraint(normalized);
		doc.data.getConstraints().set(constraintIndex, replacement);
		for (Skin skin : doc.data.getSkins()) {
			for (int index = skin.getConstraints().size - 1; index >= 0; index--) {
				if (skin.getConstraints().get(index) != existing) continue;
				if (setup.skinRequired()) skin.getConstraints().set(index, replacement);
				else skin.getConstraints().removeIndex(index);
			}
		}
		if (setup.skinRequired() && activationSkin != null) {
			while (activationSkin.getConstraints().removeValue(replacement, true)) { }
			if (activeInSkin) activationSkin.getConstraints().add(replacement);
		}
		doc.replaceEditorConstraintState(existing, replacement);
		doc.markDirty();
		rebuildSkeleton();
		return replacement;
	}

	public boolean isTransformConstraintActiveInSkin(TransformConstraintData constraint, Skin skin) {
		return constraint != null && skin != null && skin.getConstraints().contains(constraint, true);
	}

	public String getTransformConstraintDeleteBlockReason(TransformConstraintData constraint) {
		if (doc.data == null || constraint == null) return "Transform constraint data is invalid";
		int index = doc.data.getConstraints().indexOf(constraint, true);
		if (index < 0) return "The Transform constraint is no longer in this project";
		for (Skin skin : doc.data.getSkins())
			if (skin.getConstraints().contains(constraint, true))
				return "Skin " + skin.getName() + " still activates this Transform constraint";
		for (Animation animation : doc.data.getAnimations())
			for (Animation.Timeline timeline : animation.getTimelines())
				if (timeline instanceof Animation.ConstraintTimeline target && target.getConstraintIndex() == index)
					return "Animation " + animation.getName() + " still contains this Transform timeline";
		return null;
	}

	public boolean deleteTransformConstraint(TransformConstraintData constraint) {
		if (getTransformConstraintDeleteBlockReason(constraint) != null) return false;
		return deleteConstraintData(constraint);
	}

	private static TransformConstraintSetup normalizeTransformSetup(TransformConstraintSetup setup) {
		return new TransformConstraintSetup(setup.name().trim(), setup.bones(), setup.source(), setup.skinRequired(),
			setup.localSource(), setup.localTarget(), setup.additive(), setup.clamp(), setup.offsetRotation(),
			setup.offsetX(), setup.offsetY(), setup.offsetScaleX(), setup.offsetScaleY(), setup.offsetShearY(),
			setup.mixRotate(), setup.mixX(), setup.mixY(), setup.mixScaleX(), setup.mixScaleY(),
			setup.mixShearY(), setup.properties());
	}

	private static TransformConstraintData createTransformConstraint(TransformConstraintSetup rawSetup) {
		TransformConstraintSetup setup = normalizeTransformSetup(rawSetup);
		TransformConstraintData created = new TransformConstraintData(setup.name());
		for (BoneData bone : setup.bones()) created.getBones().add(bone);
		created.setSource(setup.source());
		created.setSkinRequired(setup.skinRequired());
		created.setLocalSource(setup.localSource());
		created.setLocalTarget(setup.localTarget());
		created.setAdditive(setup.additive());
		created.setClamp(setup.clamp());
		created.setOffsetRotation(setup.offsetRotation());
		created.setOffsetX(setup.offsetX());
		created.setOffsetY(setup.offsetY());
		created.setOffsetScaleX(setup.offsetScaleX());
		created.setOffsetScaleY(setup.offsetScaleY());
		created.setOffsetShearY(setup.offsetShearY());
		for (TransformFromPropertySetup fromSetup : setup.properties()) {
			FromProperty from = createFromProperty(fromSetup.property());
			from.offset = fromSetup.offset();
			for (TransformToPropertySetup toSetup : fromSetup.targets()) {
				ToProperty to = createToProperty(toSetup.property());
				to.offset = toSetup.offset();
				to.max = toSetup.max();
				to.scale = toSetup.scale();
				from.to.add(to);
			}
			created.getProperties().add(from);
		}
		TransformConstraintPose pose = created.getSetupPose();
		pose.setMixRotate(setup.mixRotate());
		pose.setMixX(setup.mixX());
		pose.setMixY(setup.mixY());
		pose.setMixScaleX(setup.mixScaleX());
		pose.setMixScaleY(setup.mixScaleY());
		pose.setMixShearY(setup.mixShearY());
		return created;
	}

	// ================= Path constraint setup =================

	public static PathConstraintSetup pathConstraintSetup(PathConstraintData constraint) {
		if (constraint == null) throw new IllegalArgumentException("Path constraint cannot be null");
		List<BoneData> bones = new ArrayList<>();
		for (BoneData bone : constraint.getBones()) bones.add(bone);
		var pose = constraint.getSetupPose();
		return new PathConstraintSetup(constraint.getName(), bones, constraint.getSlot(), constraint.getSkinRequired(),
			constraint.getPositionMode(), constraint.getSpacingMode(), constraint.getRotateMode(),
			constraint.getOffsetRotation(), pose.getPosition(), pose.getSpacing(), pose.getMixRotate(),
			pose.getMixX(), pose.getMixY());
	}

	/** Returns null when the complete Path setup can be applied atomically. */
	public String validatePathConstraintSetup(PathConstraintData existing, PathConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (doc.data == null) return "当前项目没有骨架数据";
		if (existing != null && !doc.data.getConstraints().contains(existing, true))
			return "Path 约束已不在当前项目中";
		if (setup == null) return "Path Setup 数据不能为空";
		String name = setup.name() == null ? "" : setup.name().trim();
		if (name.isEmpty()) return "Path 约束名称不能为空";
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint != existing && constraint.getName().equals(name)) return "约束名称已存在";
		if (setup.bones().isEmpty()) return "Path 至少需要一个受约束骨骼";
		Set<BoneData> bones = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (BoneData bone : setup.bones()) {
			if (!validBoneReference(bone)) return "Path 受约束骨骼无效";
			if (!bones.add(bone)) return "Path 受约束骨骼不能重复";
		}
		if (setup.slot() == null || !doc.data.getSlots().contains(setup.slot(), true)
			|| setup.slot().getIndex() < 0 || setup.slot().getIndex() >= doc.data.getSlots().size
			|| doc.data.getSlots().get(setup.slot().getIndex()) != setup.slot())
			return "Path 目标槽无效";
		for (BoneData ancestor = setup.slot().getBoneData(); ancestor != null; ancestor = ancestor.getParent())
			if (bones.contains(ancestor)) return "Path 目标槽骨骼不能位于受约束骨骼子树中";
		if (setup.positionMode() == null || setup.spacingMode() == null || setup.rotateMode() == null)
			return "Path 约束模式无效";
		if (!areFinite(setup.offsetRotation(), setup.position(), setup.spacing(), setup.mixRotate(),
			setup.mixX(), setup.mixY()))
			return "Path Setup 参数必须是有限数值";
		if (activationSkin != null && !doc.data.getSkins().contains(activationSkin, true))
			return "预览皮肤已不在当前项目中";
		if (activeInSkin && (!setup.skinRequired() || activationSkin == null))
			return "只有皮肤必需的 Path 约束才能在当前皮肤中激活";
		return null;
	}

	public PathConstraintData addPathConstraint(PathConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (validatePathConstraintSetup(null, setup, activationSkin, activeInSkin) != null) return null;
		PathConstraintData created = createPathConstraint(setup);
		doc.data.getConstraints().add(created);
		if (activeInSkin) activationSkin.getConstraints().add(created);
		doc.markDirty();
		rebuildSkeleton();
		return created;
	}

	/** Replaces immutable-name data while preserving the unified constraint index. */
	public PathConstraintData updatePathConstraint(PathConstraintData existing, PathConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (validatePathConstraintSetup(existing, setup, activationSkin, activeInSkin) != null) return null;
		PathConstraintSetup normalized = normalizePathSetup(setup);
		boolean sameSetup = pathConstraintSetup(existing).equals(normalized);
		boolean currentActive = isPathConstraintActiveInSkin(existing, activationSkin);
		boolean hasRedundantActivation = !setup.skinRequired() && anySkinContains(existing);
		if (sameSetup && currentActive == activeInSkin && !hasRedundantActivation) return existing;

		int constraintIndex = doc.data.getConstraints().indexOf(existing, true);
		PathConstraintData replacement = createPathConstraint(normalized);
		doc.data.getConstraints().set(constraintIndex, replacement);
		for (Skin skin : doc.data.getSkins()) {
			for (int index = skin.getConstraints().size - 1; index >= 0; index--) {
				if (skin.getConstraints().get(index) != existing) continue;
				if (setup.skinRequired()) skin.getConstraints().set(index, replacement);
				else skin.getConstraints().removeIndex(index);
			}
		}
		if (setup.skinRequired() && activationSkin != null) {
			while (activationSkin.getConstraints().removeValue(replacement, true)) { }
			if (activeInSkin) activationSkin.getConstraints().add(replacement);
		}
		doc.replaceEditorConstraintState(existing, replacement);
		doc.markDirty();
		rebuildSkeleton();
		return replacement;
	}

	public boolean isPathConstraintActiveInSkin(PathConstraintData constraint, Skin skin) {
		return constraint != null && skin != null && skin.getConstraints().contains(constraint, true);
	}

	public String getPathConstraintDeleteBlockReason(PathConstraintData constraint) {
		if (doc.data == null || constraint == null) return "Path 约束数据无效";
		int index = doc.data.getConstraints().indexOf(constraint, true);
		if (index < 0) return "Path 约束已不在当前项目中";
		for (Skin skin : doc.data.getSkins())
			if (skin.getConstraints().contains(constraint, true))
				return "皮肤 " + skin.getName() + " 仍激活该 Path 约束";
		for (Animation animation : doc.data.getAnimations())
			for (Animation.Timeline timeline : animation.getTimelines())
				if (timeline instanceof Animation.ConstraintTimeline target && target.getConstraintIndex() == index)
					return "动画 " + animation.getName() + " 仍包含该 Path 约束的时间线";
		return null;
	}

	public boolean deletePathConstraint(PathConstraintData constraint) {
		if (getPathConstraintDeleteBlockReason(constraint) != null) return false;
		return deleteConstraintData(constraint);
	}

	private static PathConstraintSetup normalizePathSetup(PathConstraintSetup setup) {
		return new PathConstraintSetup(setup.name().trim(), setup.bones(), setup.slot(), setup.skinRequired(),
			setup.positionMode(), setup.spacingMode(), setup.rotateMode(), setup.offsetRotation(), setup.position(),
			setup.spacing(), setup.mixRotate(), setup.mixX(), setup.mixY());
	}

	private static PathConstraintData createPathConstraint(PathConstraintSetup rawSetup) {
		PathConstraintSetup setup = normalizePathSetup(rawSetup);
		PathConstraintData created = new PathConstraintData(setup.name());
		for (BoneData bone : setup.bones()) created.getBones().add(bone);
		created.setSlot(setup.slot());
		created.setSkinRequired(setup.skinRequired());
		created.setPositionMode(setup.positionMode());
		created.setSpacingMode(setup.spacingMode());
		created.setRotateMode(setup.rotateMode());
		created.setOffsetRotation(setup.offsetRotation());
		var pose = created.getSetupPose();
		pose.setPosition(setup.position());
		pose.setSpacing(setup.spacing());
		pose.setMixRotate(setup.mixRotate());
		pose.setMixX(setup.mixX());
		pose.setMixY(setup.mixY());
		return created;
	}

	// ================= Physics constraint setup =================

	public static PhysicsConstraintSetup physicsConstraintSetup(PhysicsConstraintData constraint) {
		if (constraint == null) throw new IllegalArgumentException("Physics constraint cannot be null");
		float step = constraint.getStep();
		float reciprocal = 1f / step;
		int fps = Math.round(reciprocal);
		if (!Float.isFinite(step) || step <= 0f || !Float.isFinite(reciprocal) || fps <= 0
			|| Float.compare(step, 1f / fps) != 0)
			throw new IllegalArgumentException("Physics constraint step is not a positive integer FPS");
		var pose = constraint.getSetupPose();
		float mass = 1f / pose.getMassInverse();
		return new PhysicsConstraintSetup(constraint.getName(), constraint.getBone(), constraint.getSkinRequired(),
			constraint.getScaleYMode(), constraint.getX(), constraint.getY(), constraint.getRotate(),
			constraint.getScaleX(), constraint.getShearX(), constraint.getLimit(), fps, pose.getInertia(),
			pose.getStrength(), pose.getDamping(), mass, pose.getWind(), pose.getGravity(), pose.getMix(),
			constraint.getInertiaGlobal(), constraint.getStrengthGlobal(), constraint.getDampingGlobal(),
			constraint.getMassGlobal(), constraint.getWindGlobal(), constraint.getGravityGlobal(),
			constraint.getMixGlobal());
	}

	/** Returns null when the complete Physics setup can be applied atomically. */
	public String validatePhysicsConstraintSetup(PhysicsConstraintData existing, PhysicsConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (doc.data == null) return "当前项目没有骨架数据";
		if (existing != null && !doc.data.getConstraints().contains(existing, true))
			return "Physics 约束已不在当前项目中";
		if (setup == null) return "Physics Setup 数据不能为空";
		String name = setup.name() == null ? "" : setup.name().trim();
		if (name.isEmpty()) return "Physics 约束名称不能为空";
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint != existing && constraint.getName().equals(name)) return "约束名称已存在";
		if (!validBoneReference(setup.bone())) return "Physics 目标骨骼无效";
		if (setup.scaleYMode() == null) return "Physics 缩放 Y 模式无效";
		if (!areFinite(setup.x(), setup.y(), setup.rotate(), setup.scaleX(), setup.shearX(), setup.limit(),
			setup.inertia(), setup.strength(), setup.damping(), setup.mass(), setup.wind(), setup.gravity(), setup.mix()))
			return "Physics Setup 参数必须是有限数值";
		if (setup.limit() < 0f) return "Physics 位移限制不能小于 0";
		if (setup.fps() <= 0) return "Physics FPS 必须是正整数";
		if (setup.mass() <= 0f || !Float.isFinite(1f / setup.mass()))
			return "Physics 质量必须是大于 0 的有限数值";
		if (activationSkin != null && !doc.data.getSkins().contains(activationSkin, true))
			return "预览皮肤已不在当前项目中";
		if (activeInSkin && (!setup.skinRequired() || activationSkin == null))
			return "只有皮肤必需的 Physics 约束才能在当前皮肤中激活";
		String globalTimelineBlock = getPhysicsGlobalFlagDisableBlockReason(existing, setup);
		if (globalTimelineBlock != null) return globalTimelineBlock;
		return null;
	}

	public PhysicsConstraintData addPhysicsConstraint(PhysicsConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (validatePhysicsConstraintSetup(null, setup, activationSkin, activeInSkin) != null) return null;
		PhysicsConstraintData created = createPhysicsConstraint(setup);
		doc.data.getConstraints().add(created);
		if (activeInSkin) activationSkin.getConstraints().add(created);
		doc.markDirty();
		rebuildSkeleton();
		return created;
	}

	/** Replaces immutable-name data while preserving the unified constraint index. */
	public PhysicsConstraintData updatePhysicsConstraint(PhysicsConstraintData existing,
		PhysicsConstraintSetup setup, Skin activationSkin, boolean activeInSkin) {
		if (validatePhysicsConstraintSetup(existing, setup, activationSkin, activeInSkin) != null) return null;
		PhysicsConstraintSetup normalized = normalizePhysicsSetup(setup);
		boolean sameSetup = physicsConstraintSetup(existing).equals(normalized);
		boolean currentActive = isPhysicsConstraintActiveInSkin(existing, activationSkin);
		boolean hasRedundantActivation = !setup.skinRequired() && anySkinContains(existing);
		if (sameSetup && currentActive == activeInSkin && !hasRedundantActivation) return existing;

		int constraintIndex = doc.data.getConstraints().indexOf(existing, true);
		PhysicsConstraintData replacement = createPhysicsConstraint(normalized);
		doc.data.getConstraints().set(constraintIndex, replacement);
		for (Skin skin : doc.data.getSkins()) {
			for (int index = skin.getConstraints().size - 1; index >= 0; index--) {
				if (skin.getConstraints().get(index) != existing) continue;
				if (setup.skinRequired()) skin.getConstraints().set(index, replacement);
				else skin.getConstraints().removeIndex(index);
			}
		}
		if (setup.skinRequired() && activationSkin != null) {
			while (activationSkin.getConstraints().removeValue(replacement, true)) { }
			if (activeInSkin) activationSkin.getConstraints().add(replacement);
		}
		doc.replaceEditorConstraintState(existing, replacement);
		doc.markDirty();
		rebuildSkeleton();
		return replacement;
	}

	public boolean isPhysicsConstraintActiveInSkin(PhysicsConstraintData constraint, Skin skin) {
		return constraint != null && skin != null && skin.getConstraints().contains(constraint, true);
	}

	public String getPhysicsConstraintDeleteBlockReason(PhysicsConstraintData constraint) {
		if (doc.data == null || constraint == null) return "Physics 约束数据无效";
		int index = doc.data.getConstraints().indexOf(constraint, true);
		if (index < 0) return "Physics 约束已不在当前项目中";
		for (Skin skin : doc.data.getSkins())
			if (skin.getConstraints().contains(constraint, true))
				return "皮肤 " + skin.getName() + " 仍激活该 Physics 约束";
		for (Animation animation : doc.data.getAnimations())
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (timeline instanceof Animation.ConstraintTimeline target && target.getConstraintIndex() == index)
					return "动画 " + animation.getName() + " 仍包含该 Physics 约束的时间线";
				if (!(timeline instanceof Animation.ConstraintTimeline target)
					|| target.getConstraintIndex() != -1) continue;
				AnimationEditor.PhysicsProp prop = AnimationEditor.physicsPropertyOf(timeline);
				if (prop == AnimationEditor.PhysicsProp.RESET && !hasOtherPhysicsConstraint(constraint))
					return "动画 " + animation.getName() + " 仍包含全局 Physics 重置轨道";
				if (prop != null && prop != AnimationEditor.PhysicsProp.RESET
					&& AnimationEditor.physicsPropertyIsGlobal(constraint, prop)
					&& !hasOtherGlobalPhysicsProperty(constraint, prop))
					return "动画 " + animation.getName() + " 仍包含全局 Physics " + prop
						+ " 轨道，不能删除最后一个有效目标";
			}
		return null;
	}

	public boolean deletePhysicsConstraint(PhysicsConstraintData constraint) {
		if (getPhysicsConstraintDeleteBlockReason(constraint) != null) return false;
		return deleteConstraintData(constraint);
	}

	private String getPhysicsGlobalFlagDisableBlockReason(PhysicsConstraintData existing,
		PhysicsConstraintSetup setup) {
		if (existing == null || setup == null) return null;
		for (AnimationEditor.PhysicsProp prop : AnimationEditor.PhysicsProp.values()) {
			if (prop == AnimationEditor.PhysicsProp.RESET
				|| !AnimationEditor.physicsPropertyIsGlobal(existing, prop)
				|| physicsSetupPropertyIsGlobal(setup, prop)
				|| hasOtherGlobalPhysicsProperty(existing, prop)) continue;
			for (Animation animation : doc.data.getAnimations())
				for (Animation.Timeline timeline : animation.getTimelines())
					if (timeline instanceof Animation.ConstraintTimeline target
						&& target.getConstraintIndex() == -1
						&& AnimationEditor.physicsPropertyOf(timeline) == prop)
						return "动画 " + animation.getName() + " 仍包含全局 Physics " + prop
							+ " 轨道，不能关闭最后一个对应的 Global 标志";
		}
		return null;
	}

	private boolean hasOtherPhysicsConstraint(PhysicsConstraintData excluded) {
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint != excluded && constraint instanceof PhysicsConstraintData) return true;
		return false;
	}

	private boolean hasOtherGlobalPhysicsProperty(PhysicsConstraintData excluded,
		AnimationEditor.PhysicsProp prop) {
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint != excluded && constraint instanceof PhysicsConstraintData physics
				&& AnimationEditor.physicsPropertyIsGlobal(physics, prop)) return true;
		return false;
	}

	private static boolean physicsSetupPropertyIsGlobal(PhysicsConstraintSetup setup,
		AnimationEditor.PhysicsProp prop) {
		return switch (prop) {
		case INERTIA -> setup.inertiaGlobal();
		case STRENGTH -> setup.strengthGlobal();
		case DAMPING -> setup.dampingGlobal();
		case MASS -> setup.massGlobal();
		case WIND -> setup.windGlobal();
		case GRAVITY -> setup.gravityGlobal();
		case MIX -> setup.mixGlobal();
		case RESET -> false;
		};
	}

	private static PhysicsConstraintSetup normalizePhysicsSetup(PhysicsConstraintSetup setup) {
		return new PhysicsConstraintSetup(setup.name().trim(), setup.bone(), setup.skinRequired(), setup.scaleYMode(),
			setup.x(), setup.y(), setup.rotate(), setup.scaleX(), setup.shearX(), setup.limit(), setup.fps(),
			setup.inertia(), setup.strength(), setup.damping(), setup.mass(), setup.wind(), setup.gravity(), setup.mix(),
			setup.inertiaGlobal(), setup.strengthGlobal(), setup.dampingGlobal(), setup.massGlobal(),
			setup.windGlobal(), setup.gravityGlobal(), setup.mixGlobal());
	}

	private static PhysicsConstraintData createPhysicsConstraint(PhysicsConstraintSetup rawSetup) {
		PhysicsConstraintSetup setup = normalizePhysicsSetup(rawSetup);
		PhysicsConstraintData created = new PhysicsConstraintData(setup.name());
		created.setBone(setup.bone());
		created.setSkinRequired(setup.skinRequired());
		created.setScaleYMode(setup.scaleYMode());
		created.setX(setup.x());
		created.setY(setup.y());
		created.setRotate(setup.rotate());
		created.setScaleX(setup.scaleX());
		created.setShearX(setup.shearX());
		created.setLimit(setup.limit());
		created.setStep(1f / setup.fps());
		created.setInertiaGlobal(setup.inertiaGlobal());
		created.setStrengthGlobal(setup.strengthGlobal());
		created.setDampingGlobal(setup.dampingGlobal());
		created.setMassGlobal(setup.massGlobal());
		created.setWindGlobal(setup.windGlobal());
		created.setGravityGlobal(setup.gravityGlobal());
		created.setMixGlobal(setup.mixGlobal());
		var pose = created.getSetupPose();
		pose.setInertia(setup.inertia());
		pose.setStrength(setup.strength());
		pose.setDamping(setup.damping());
		pose.setMassInverse(1f / setup.mass());
		pose.setWind(setup.wind());
		pose.setGravity(setup.gravity());
		pose.setMix(setup.mix());
		return created;
	}

	// ================= Slider constraint setup =================

	public static SliderConstraintSetup sliderConstraintSetup(SliderData constraint) {
		if (constraint == null) throw new IllegalArgumentException("Slider constraint cannot be null");
		FromProperty from = constraint.getProperty();
		boolean propertyDriven = constraint.getBone() != null;
		TransformProperty property = propertyDriven && from != null ? transformProperty(from) : null;
		var pose = constraint.getSetupPose();
		return new SliderConstraintSetup(constraint.getName(), constraint.getAnimation(),
			constraint.getSkinRequired(), constraint.getAdditive(), constraint.getLoop(), constraint.getBone(),
			property, propertyDriven && from != null ? from.offset : 0f,
			propertyDriven ? constraint.getOffset() : 0f, propertyDriven ? constraint.getScale() : 1f,
			propertyDriven ? constraint.getMax() : 0f, propertyDriven && constraint.getLocal(),
			propertyDriven ? 0f : pose.getTime(), pose.getMix());
	}

	/** Returns null when the complete Slider setup can be applied atomically. */
	public String validateSliderConstraintSetup(SliderData existing, SliderConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (doc.data == null) return "当前项目没有骨架数据";
		if (existing != null && !doc.data.getConstraints().contains(existing, true))
			return "Slider 约束已不在当前项目中";
		if (setup == null) return "Slider Setup 数据不能为空";
		String name = setup.name() == null ? "" : setup.name().trim();
		if (name.isEmpty()) return "Slider 约束名称不能为空";
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint != existing && constraint.getName().equals(name)) return "约束名称已存在";
		if (setup.animation() == null || !doc.data.getAnimations().contains(setup.animation(), true))
			return "Slider 目标动画无效";
		if (setup.animation().getTimelines() == null || setup.animation().getBones() == null)
			return "Slider 目标动画数据未初始化";
		if (setup.loop() && !(setup.animation().getDuration() > 0f))
			return "循环 Slider 的目标动画时长必须大于零";
		if (setup.bone() == null) {
			if (setup.property() != null) return "时间驱动 Slider 不能设置骨骼属性";
		} else {
			if (!validBoneReference(setup.bone())) return "Slider 驱动骨骼无效";
			if (setup.property() == null) return "骨骼属性驱动 Slider 必须选择属性";
		}
		if (!areFinite(setup.sourceOffset(), setup.targetOffset(), setup.scale(), setup.max(),
			setup.time(), setup.mix())) return "Slider Setup 参数必须是有限数值";
		if (activationSkin != null && !doc.data.getSkins().contains(activationSkin, true))
			return "预览皮肤已不在当前项目中";
		if (activeInSkin && (!setup.skinRequired() || activationSkin == null))
			return "只有皮肤必需的 Slider 约束才能在当前皮肤中激活";
		return null;
	}

	public SliderData addSliderConstraint(SliderConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (validateSliderConstraintSetup(null, setup, activationSkin, activeInSkin) != null) return null;
		SliderData created = sliderConstraintData(setup);
		doc.data.getConstraints().add(created);
		if (activeInSkin) activationSkin.getConstraints().add(created);
		doc.markDirty();
		rebuildSkeleton();
		return created;
	}

	/** Replaces immutable-name data while preserving the unified constraint index. */
	public SliderData updateSliderConstraint(SliderData existing, SliderConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (validateSliderConstraintSetup(existing, setup, activationSkin, activeInSkin) != null) return null;
		SliderConstraintSetup normalized = normalizeSliderSetup(setup);
		boolean sameSetup = sliderConstraintSetup(existing).equals(normalized);
		boolean currentActive = isSliderConstraintActiveInSkin(existing, activationSkin);
		boolean hasRedundantActivation = !setup.skinRequired() && anySkinContains(existing);
		if (sameSetup && currentActive == activeInSkin && !hasRedundantActivation) return existing;

		int constraintIndex = doc.data.getConstraints().indexOf(existing, true);
		SliderData replacement = sliderConstraintData(normalized);
		doc.data.getConstraints().set(constraintIndex, replacement);
		for (Skin skin : doc.data.getSkins()) {
			for (int index = skin.getConstraints().size - 1; index >= 0; index--) {
				if (skin.getConstraints().get(index) != existing) continue;
				if (setup.skinRequired()) skin.getConstraints().set(index, replacement);
				else skin.getConstraints().removeIndex(index);
			}
		}
		if (setup.skinRequired() && activationSkin != null) {
			while (activationSkin.getConstraints().removeValue(replacement, true)) { }
			if (activeInSkin) activationSkin.getConstraints().add(replacement);
		}
		doc.replaceEditorConstraintState(existing, replacement);
		doc.markDirty();
		rebuildSkeleton();
		return replacement;
	}

	public boolean isSliderConstraintActiveInSkin(SliderData constraint, Skin skin) {
		return constraint != null && skin != null && skin.getConstraints().contains(constraint, true);
	}

	public String getSliderConstraintDeleteBlockReason(SliderData constraint) {
		if (doc.data == null || constraint == null) return "Slider 约束数据无效";
		int index = doc.data.getConstraints().indexOf(constraint, true);
		if (index < 0) return "Slider 约束已不在当前项目中";
		for (Skin skin : doc.data.getSkins())
			if (skin.getConstraints().contains(constraint, true))
				return "皮肤 " + skin.getName() + " 仍激活该 Slider 约束";
		for (Animation animation : doc.data.getAnimations())
			for (Animation.Timeline timeline : animation.getTimelines())
				if (timeline instanceof Animation.ConstraintTimeline target && target.getConstraintIndex() == index)
					return "动画 " + animation.getName() + " 仍包含该 Slider 约束的时间线";
		return null;
	}

	public boolean deleteSliderConstraint(SliderData constraint) {
		if (getSliderConstraintDeleteBlockReason(constraint) != null) return false;
		return deleteConstraintData(constraint);
	}

	/** Moves one entry in Spine's unified constraint execution order and preserves all timeline targets. */
	public boolean moveConstraint(ConstraintData<?, ?> constraint, int newIndex) {
		if (doc.data == null || constraint == null) return false;
		int oldIndex = doc.data.getConstraints().indexOf(constraint, true);
		if (oldIndex < 0) return false;
		int constraintCount = doc.data.getConstraints().size;
		if (newIndex < 0 || newIndex >= constraintCount)
			throw new IllegalArgumentException("Constraint order target is outside the unified constraint list");
		if (newIndex == oldIndex) return false;

		int[] oldToNew = new int[constraintCount];
		for (int index = 0; index < oldToNew.length; index++) oldToNew[index] = index;
		oldToNew[oldIndex] = newIndex;
		if (oldIndex < newIndex) {
			for (int index = oldIndex + 1; index <= newIndex; index++) oldToNew[index] = index - 1;
		} else {
			for (int index = newIndex; index < oldIndex; index++) oldToNew[index] = index + 1;
		}

		new AnimationEditor(doc).remapConstraintTimelines(oldToNew);
		doc.data.getConstraints().removeIndex(oldIndex);
		doc.data.getConstraints().insert(newIndex, constraint);
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	public int constraintIndex(ConstraintData<?, ?> constraint) {
		return doc.data == null || constraint == null ? -1
			: doc.data.getConstraints().indexOf(constraint, true);
	}

	private boolean deleteConstraintData(ConstraintData<?, ?> constraint) {
		int index = doc.data.getConstraints().indexOf(constraint, true);
		if (index < 0) return false;
		new AnimationEditor(doc).reindexConstraintTimelinesAfterRemoval(index);
		doc.data.getConstraints().removeIndex(index);
		doc.removeEditorConstraintState(constraint);
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	private static SliderConstraintSetup normalizeSliderSetup(SliderConstraintSetup setup) {
		boolean propertyDriven = setup.bone() != null;
		return new SliderConstraintSetup(setup.name().trim(), setup.animation(), setup.skinRequired(),
			setup.additive(), setup.loop(), setup.bone(), propertyDriven ? setup.property() : null,
			propertyDriven ? setup.sourceOffset() : 0f, propertyDriven ? setup.targetOffset() : 0f,
			propertyDriven ? setup.scale() : 1f, propertyDriven ? setup.max() : 0f,
			propertyDriven && setup.local(), propertyDriven ? 0f : setup.time(), setup.mix());
	}

	public static SliderData sliderConstraintData(SliderConstraintSetup rawSetup) {
		if (rawSetup == null) throw new IllegalArgumentException("Slider Setup 数据不能为空");
		SliderConstraintSetup setup = normalizeSliderSetup(rawSetup);
		SliderData created = new SliderData(setup.name());
		created.setAnimation(setup.animation());
		created.setSkinRequired(setup.skinRequired());
		created.setAdditive(setup.additive());
		created.setLoop(setup.loop());
		created.setBone(setup.bone());
		if (setup.property() != null) {
			FromProperty property = createFromProperty(setup.property());
			property.offset = setup.sourceOffset();
			created.setProperty(property);
		}
		created.setOffset(setup.targetOffset());
		created.setScale(setup.scale());
		created.setMax(setup.max());
		created.setLocal(setup.local());
		created.getSetupPose().setTime(setup.time());
		created.getSetupPose().setMix(setup.mix());
		return created;
	}

	private static TransformProperty transformProperty(FromProperty property) {
		if (property instanceof FromRotate) return TransformProperty.ROTATE;
		if (property instanceof FromX) return TransformProperty.X;
		if (property instanceof FromY) return TransformProperty.Y;
		if (property instanceof FromScaleX) return TransformProperty.SCALE_X;
		if (property instanceof FromScaleY) return TransformProperty.SCALE_Y;
		if (property instanceof FromShearY) return TransformProperty.SHEAR_Y;
		throw new IllegalArgumentException("Unknown Transform source property: " + property.getClass().getName());
	}

	private static TransformProperty transformProperty(ToProperty property) {
		if (property instanceof ToRotate) return TransformProperty.ROTATE;
		if (property instanceof ToX) return TransformProperty.X;
		if (property instanceof ToY) return TransformProperty.Y;
		if (property instanceof ToScaleX) return TransformProperty.SCALE_X;
		if (property instanceof ToScaleY) return TransformProperty.SCALE_Y;
		if (property instanceof ToShearY) return TransformProperty.SHEAR_Y;
		throw new IllegalArgumentException("Unknown Transform target property: " + property.getClass().getName());
	}

	private static FromProperty createFromProperty(TransformProperty property) {
		return switch (property) {
		case ROTATE -> new FromRotate();
		case X -> new FromX();
		case Y -> new FromY();
		case SCALE_X -> new FromScaleX();
		case SCALE_Y -> new FromScaleY();
		case SHEAR_Y -> new FromShearY();
		};
	}

	private static ToProperty createToProperty(TransformProperty property) {
		return switch (property) {
		case ROTATE -> new ToRotate();
		case X -> new ToX();
		case Y -> new ToY();
		case SCALE_X -> new ToScaleX();
		case SCALE_Y -> new ToScaleY();
		case SHEAR_Y -> new ToShearY();
		};
	}

	// ================= 新增 =================

	/** 新建骨骼：作为 parent 的子骨骼追加到数组末尾（父必在前，满足 index 约束） */
	public BoneData addBone(String name, BoneData parent) {
		if (doc.data == null) return null;
		if (parent == null && doc.data.getBones().notEmpty()) return null;
		String unique = uniqueBoneName(name);
		BoneData newData = new BoneData(doc.data.getBones().size, unique, parent);
		newData.getSetupPose().setScale(1f, 1f);
		newData.getSetupPose().setInherit(BoneData.Inherit.normal);
		newData.setVisible(true);
		doc.data.getBones().add(newData);
		doc.markDirty();
		rebuildSkeleton();
		for (Bone b : doc.skeleton.getBones()) {
			if (b.getData() == newData) {
				doc.setSelectedBone(b);
				break;
			}
		}
		return newData;
	}

	/** Reparents a bone and restores parent-before-child order with all indexed references remapped. */
	public boolean setBoneParent(BoneData target, BoneData parent) {
		return setBoneParent(target, parent, false);
	}

	/** Optionally compensates the setup transform; animation compensation is intentionally unsupported. */
	public boolean setBoneParent(BoneData target, BoneData parent, boolean keepSetupWorld) {
		if (getBoneParentBlockReason(target, parent, keepSetupWorld) != null) return false;
		Array<BoneData> ordered = reparentedBoneOrder(target, parent);
		SetupReparenting.Plan compensation = keepSetupWorld ? SetupReparenting.plan(doc, target, parent, ordered) : null;
		SkeletonStructureSnapshot before = SkeletonStructureSnapshot.capture(this);
		try {
			applyBoneOrder(ordered, target, parent);
			if (compensation != null) {
				applyBonePoseState(doc.data.findBone(target.getName()).getSetupPose(), compensation.pose());
				SetupReparenting.verify(doc.data, compensation);
				if (doc.poseMode && doc.skeleton != null)
					applyBonePoseState(doc.skeleton.findBone(target.getName()).getPose(), compensation.pose());
			}
			doc.markDirty();
			rebuildSkeleton();
			return true;
		} catch (RuntimeException failure) {
			before.rollback();
			throw failure;
		}
	}

	private Array<BoneData> reparentedBoneOrder(BoneData target, BoneData parent) {
		IdentityHashMap<BoneData, List<BoneData>> children = new IdentityHashMap<>();
		java.util.PriorityQueue<BoneData> ready = new java.util.PriorityQueue<>(java.util.Comparator.comparingInt(BoneData::getIndex));
		for (BoneData bone : doc.data.getBones()) {
			BoneData owner = bone == target ? parent : bone.getParent();
			if (owner == null) ready.add(bone);
			else children.computeIfAbsent(owner, unused -> new ArrayList<>()).add(bone);
		}
		Array<BoneData> ordered = new Array<>();
		while (!ready.isEmpty()) {
			BoneData bone = ready.remove();
			ordered.add(bone);
			ready.addAll(children.getOrDefault(bone, List.of()));
		}
		if (ordered.size != doc.data.getBones().size) throw new IllegalStateException("骨骼层级包含循环");
		return ordered;
	}

	public String getBoneParentBlockReason(BoneData target, BoneData parent, boolean keepSetupWorld) {
		String reason = getBoneParentBlockReason(target, parent);
		return reason != null || !keepSetupWorld ? reason : SetupReparenting.blockReason(doc, target, parent);
	}

	public String getBoneParentBlockReason(BoneData target, BoneData parent) {
		if (doc.data == null || target == null || parent == null
			|| !doc.data.getBones().contains(target, true) || !doc.data.getBones().contains(parent, true))
			return "骨骼或父级已不在当前项目中";
		if (target.getParent() == null) return "根骨骼不能更改父级";
		if (target.getParent() == parent) return "该骨骼已属于所选父级";
		for (BoneData ancestor = parent; ancestor != null; ancestor = ancestor.getParent())
			if (ancestor == target) return "骨骼父级不能位于自身子树中";
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
			Array<BoneData> constrained;
			BoneData source;
			if (constraint instanceof IkConstraintData ik) {
				constrained = ik.getBones();
				source = ik.getTarget();
				if (constrained.size == 2) {
					BoneData second = constrained.get(1);
					if ((second == target ? parent : second.getParent()) != constrained.first())
						return "更改父级会断开 IK 约束 " + ik.getName() + " 的双骨链";
				}
			} else if (constraint instanceof TransformConstraintData transform) {
				constrained = transform.getBones();
				source = transform.getSource();
			} else if (constraint instanceof PathConstraintData path) {
				constrained = path.getBones();
				source = path.getSlot() == null ? null : path.getSlot().getBoneData();
			} else continue;
			// Follow the proposed hierarchy without changing immutable BoneData references.
			for (BoneData ancestor = source; ancestor != null; ancestor = ancestor == target ? parent : ancestor.getParent())
				if (constrained.contains(ancestor, true))
					return "更改父级会使约束 " + constraint.getName() + " 的目标位于受约束骨骼子树中";
		}
		return null;
	}

	/** Splits a bone into a nested chain appended to the skeleton. Existing children remain untouched. */
	public int splitBone(BoneData target, int segments) {
		if (doc.data == null || target == null || segments < 2 || segments > 64
			|| !doc.data.getBones().contains(target, true)) return 0;
		float length = target.getLength();
		if (!Float.isFinite(length) || length <= 0f) return 0;
		float segmentLength = length / segments;
		target.setLength(segmentLength);
		BoneData parent = target;
		for (int segment = 1; segment < segments; segment++) {
			BoneData created = new BoneData(doc.data.getBones().size, uniqueBoneName(target.getName() + "_split" + segment), parent);
			created.setLength(segmentLength);
			created.getSetupPose().setX(segmentLength);
			created.getSetupPose().setScale(1f, 1f);
			created.setVisible(true);
			doc.data.getBones().add(created);
			parent = created;
		}
		doc.markDirty();
		rebuildSkeleton();
		return segments;
	}

	// ================= 删除 =================

	/** Deletes an unreferenced subtree at any position and remaps surviving bone indices. */
	public boolean deleteBone(BoneData target) {
		if (getDeleteBlockReason(target) != null) return false;
		SkeletonStructureSnapshot before = SkeletonStructureSnapshot.capture(this);
		try {
			Array<BoneData> dead = new Array<>();
			collectSubtree(doc.data.getBones(), target, dead);
			Array<BoneData> retained = new Array<>();
			for (BoneData bone : doc.data.getBones()) if (!dead.contains(bone, true)) retained.add(bone);
			applyBoneOrder(retained, null, null);
			for (BoneData removed : dead) doc.removeEditorBoneState(removed.getName());
			doc.markDirty();
			rebuildSkeleton();
			return true;
		} catch (RuntimeException failure) {
			before.rollback();
			throw failure;
		}
	}

	private void applyBoneOrder(Array<BoneData> ordered, BoneData reparented, BoneData newParent) {
		int[] indices = new int[doc.data.getBones().size];
		Arrays.fill(indices, -1);
		IdentityHashMap<BoneData, BoneData> mapped = new IdentityHashMap<>();
		Array<BoneData> rebuilt = new Array<>();
		for (BoneData old : ordered) {
			BoneData parent = old == reparented ? newParent : old.getParent();
			BoneData mappedParent = parent == null ? null : mapped.get(parent);
			BoneData replacement = old.getIndex() == rebuilt.size && old.getParent() == mappedParent
				? old : new BoneData(rebuilt.size, old.getName(), mappedParent);
			if (parent != null && replacement.getParent() == null)
				throw new IllegalStateException("骨骼父级必须在子骨骼之前");
			copyBoneProps(old, replacement);
			replacement.getSetupPose().set(old.getSetupPose());
			indices[old.getIndex()] = rebuilt.size;
			mapped.put(old, replacement);
			rebuilt.add(replacement);
		}
		new AnimationEditor(doc).remapStructureTimelines(indices, null, doc.data.getSlots().size);
		IdentityHashMap<VertexAttachment, Boolean> seen = new IdentityHashMap<>();
		IdentityHashMap<int[], int[]> sharedWeights = new IdentityHashMap<>();
		for (Skin skin : doc.data.getSkins()) for (Skin.SkinEntry entry : skin.getAttachments()) {
			if (!(entry.getAttachment() instanceof VertexAttachment vertex)
				|| vertex.getBones() == null || seen.put(vertex, true) != null) continue;
			int[] source = vertex.getBones();
			int[] weights = sharedWeights.get(source);
			if (weights != null) {
				vertex.setBones(weights);
				continue;
			}
			weights = source.clone();
			for (int cursor = 0; cursor < weights.length;) {
				int count = weights[cursor++];
				if (count <= 0 || count > weights.length - cursor) throw new IllegalStateException("附件权重编码无效");
				for (int i = 0; i < count; i++, cursor++) {
					int index = weights[cursor];
					if (index < 0 || index >= indices.length || indices[index] < 0)
						throw new IllegalStateException("附件仍引用已删除骨骼");
					weights[cursor] = indices[index];
				}
			}
			sharedWeights.put(source, weights);
			vertex.setBones(weights);
		}
		Map<String, Set<SkeletonDocument.MeshWeightLock>> locks = new LinkedHashMap<>();
		for (var entry : doc.meshWeightLockStates().entrySet()) {
			Set<SkeletonDocument.MeshWeightLock> remapped = new LinkedHashSet<>();
			for (var lock : entry.getValue()) if (lock.boneIndex() >= 0 && lock.boneIndex() < indices.length
				&& indices[lock.boneIndex()] >= 0)
				remapped.add(new SkeletonDocument.MeshWeightLock(lock.vertex(), indices[lock.boneIndex()]));
			if (!remapped.isEmpty()) locks.put(entry.getKey(), remapped);
		}
		doc.restoreMeshWeightLocks(locks);
		doc.data.getBones().clear();
		doc.data.getBones().addAll(rebuilt);
		remapBoneReferences(mapped);
	}

	/** 返回无法安全删除的原因；null 表示可以删除。 */
	public String getDeleteBlockReason(BoneData target) {
		if (doc.data == null || target == null) return "骨骼数据无效";
		Array<BoneData> bones = doc.data.getBones();
		int first = bones.indexOf(target, true);
		if (first < 0 || target.getIndex() != first) return "骨骼索引与当前项目不一致";
		if (target.getParent() == null) return "根骨骼不能删除";

		Array<BoneData> dead = new Array<>();
		collectSubtree(bones, target, dead);
		for (SlotData slot : doc.data.getSlots()) {
			if (isDeadReference(slot.getBoneData(), dead, first))
				return "该骨骼子树仍绑定槽位 " + slot.getName();
		}
		for (Skin skin : doc.data.getSkins()) {
			for (BoneData bone : skin.getBones()) {
				if (isDeadReference(bone, dead, first)) return "该骨骼子树仍被皮肤 " + skin.getName() + " 引用";
			}
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (entry.getAttachment() instanceof VertexAttachment vertex && weightedAttachmentUses(vertex, dead))
					return "加权附件 " + entry.getPlaceholder() + " 仍引用该骨骼子树";
			}
		}
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
			if (constraintUses(constraint, dead, first)) return "约束 " + constraint.getName() + " 仍引用该骨骼子树";
		}
		for (Animation animation : doc.data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (timeline instanceof Animation.BoneTimeline boneTimeline
					&& containsBoneIndex(dead, boneTimeline.getBoneIndex()))
					return "动画 " + animation.getName() + " 仍包含该骨骼子树的时间线";
			}
		}
		return null;
	}

	private static boolean isDeadReference(BoneData bone, Array<BoneData> dead, int first) {
		return bone != null && dead.contains(bone, true);
	}

	private static boolean containsBoneIndex(Array<BoneData> bones, int index) {
		for (BoneData bone : bones) if (bone.getIndex() == index) return true;
		return false;
	}

	private static boolean weightedAttachmentUses(VertexAttachment attachment, Array<BoneData> dead) {
		int[] bones = attachment.getBones();
		if (bones == null) return false;
		for (int i = 0; i < bones.length;) {
			int count = bones[i++];
			for (int n = 0; n < count && i < bones.length; n++) {
				if (containsBoneIndex(dead, bones[i++])) return true;
			}
		}
		return false;
	}

	private static boolean constraintUses(ConstraintData<?, ?> constraint, Array<BoneData> dead, int first) {
		if (constraint instanceof IkConstraintData ik) {
			if (isDeadReference(ik.getTarget(), dead, first)) return true;
			for (BoneData bone : ik.getBones()) if (isDeadReference(bone, dead, first)) return true;
		} else if (constraint instanceof TransformConstraintData transform) {
			if (isDeadReference(transform.getSource(), dead, first)) return true;
			for (BoneData bone : transform.getBones()) if (isDeadReference(bone, dead, first)) return true;
		} else if (constraint instanceof PathConstraintData path) {
			for (BoneData bone : path.getBones()) if (isDeadReference(bone, dead, first)) return true;
			if (path.getSlot() != null && isDeadReference(path.getSlot().getBoneData(), dead, first)) return true;
		} else if (constraint instanceof PhysicsConstraintData physics) {
			return isDeadReference(physics.getBone(), dead, first);
		} else if (constraint instanceof SliderData slider) {
			return isDeadReference(slider.getBone(), dead, first);
		}
		return false;
	}

	private static void collectSubtree(Array<BoneData> bones, BoneData root, Array<BoneData> out) {
		out.add(root);
		for (int index = 0; index < bones.size; index++) {
			BoneData b = bones.get(index);
			if (b.getParent() == root) collectSubtree(bones, b, out);
		}
	}

	// ================= 重命名 =================

	/** 重命名骨骼（新建 BoneData 替换，保留父子与 pose）。返回新名称或 null。 */
	public String renameBone(BoneData target, String newName) {
		if (doc.data == null || target == null || newName == null || newName.trim().isEmpty()) return null;
		if (target.getName().equals(newName.trim())) return target.getName();
		String oldName = target.getName();
		String unique = uniqueBoneName(newName);
		Array<BoneData> bones = doc.data.getBones();
		if (!bones.contains(target, true)) return null;
		String keepSelection = doc.getSelectedBone() == null ? "" : doc.getSelectedBone().getData().getName();
		String keepSelectionKind = doc.getSelectionKind().name();
		ConstraintData<?, ?> keepConstraint = doc.getSelectedConstraint();
		String keepConstraintType = keepConstraint == null ? "" : keepConstraint.getClass().getSimpleName();
		String keepConstraintName = keepConstraint == null ? "" : keepConstraint.getName();
		String keepSlot = doc.getSelectedSlotName();
		String keepPlaceholder = doc.getSelectedAttachmentPlaceholder();
		String keepAttachment = doc.getSelectedAttachmentName();
		com.esotericsoftware.spine.BonePose renamedPose = null;
		if (doc.poseMode && doc.skeleton != null) {
			Bone runtimeBone = doc.skeleton.findBone(target.getName());
			if (runtimeBone != null) {
				renamedPose = new com.esotericsoftware.spine.BonePose();
				renamedPose.set(runtimeBone.getPose());
			}
		}

		IdentityHashMap<BoneData, BoneData> boneMap = new IdentityHashMap<>();
		for (int i = 0; i < bones.size; i++) {
			BoneData old = bones.get(i);
			BoneData mappedParent = boneMap.get(old.getParent());
			if (old != target && mappedParent == null) continue;

			BoneData rebuilt = new BoneData(old.getIndex(), old == target ? unique : old.getName(),
				mappedParent == null ? old.getParent() : mappedParent);
			copyBoneProps(old, rebuilt);
			rebuilt.getSetupPose().set(old.getSetupPose());
			boneMap.put(old, rebuilt);
			bones.set(i, rebuilt);
		}
		remapBoneReferences(boneMap);
		doc.renameEditorBoneState(oldName, unique);
		String keepConstraintRelation = doc.getSelectedConstraintRelation();
		doc.markDirty();
		rebuildSkeleton();
		String restoredSelection = keepSelection.equals(oldName) ? unique : keepSelection;
		doc.restoreEditorSelection(keepSelectionKind, restoredSelection, keepConstraintType, keepConstraintName,
			keepConstraintRelation, keepSlot, keepPlaceholder, keepAttachment);
		Bone renamedBone = doc.skeleton.findBone(unique);
		if (renamedPose != null && renamedBone != null) {
			renamedBone.getPose().set(renamedPose);
			doc.updateWorldTransform(Physics.pose);
		}
		return unique;
	}

	private void remapBoneReferences(IdentityHashMap<BoneData, BoneData> boneMap) {
		IdentityHashMap<SlotData, SlotData> slotMap = new IdentityHashMap<>();
		Array<SlotData> slots = doc.data.getSlots();
		for (int i = 0; i < slots.size; i++) {
			SlotData old = slots.get(i);
			BoneData bone = boneMap.get(old.getBoneData());
			if (bone == null || bone == old.getBoneData()) continue;

			SlotData rebuilt = copySlotData(old, old.getName(), bone);
			slotMap.put(old, rebuilt);
			slots.set(i, rebuilt);
		}

		for (Skin skin : doc.data.getSkins()) {
			Array<BoneData> skinBones = skin.getBones();
			for (int i = 0; i < skinBones.size; i++) {
				BoneData mapped = boneMap.get(skinBones.get(i));
				if (mapped != null) skinBones.set(i, mapped);
			}
		}

		for (ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
			if (constraint instanceof IkConstraintData ik) {
				remapBones(ik.getBones(), boneMap);
				BoneData target = boneMap.get(ik.getTarget());
				if (target != null) ik.setTarget(target);
			} else if (constraint instanceof TransformConstraintData transform) {
				remapBones(transform.getBones(), boneMap);
				BoneData source = boneMap.get(transform.getSource());
				if (source != null) transform.setSource(source);
			} else if (constraint instanceof PathConstraintData path) {
				remapBones(path.getBones(), boneMap);
				SlotData slot = slotMap.get(path.getSlot());
				if (slot != null) path.setSlot(slot);
			} else if (constraint instanceof PhysicsConstraintData physics) {
				BoneData bone = boneMap.get(physics.getBone());
				if (bone != null) physics.setBone(bone);
			} else if (constraint instanceof SliderData slider) {
				BoneData bone = boneMap.get(slider.getBone());
				if (bone != null) slider.setBone(bone);
			}
		}
		remapClippingEndSlots(slotMap);
	}

	private void remapClippingEndSlots(IdentityHashMap<SlotData, SlotData> slotMap) {
		if (slotMap.isEmpty()) return;
		for (Skin skin : doc.data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (!(entry.getAttachment() instanceof ClippingAttachment clipping)) continue;
				SlotData endSlot = slotMap.get(clipping.getEndSlot());
				if (endSlot != null) clipping.setEndSlot(endSlot);
			}
		}
	}

	private static SlotData copySlotData(SlotData source, String name, BoneData bone) {
		SlotData copy = new SlotData(source.getIndex(), name, bone);
		copy.getSetupPose().set(source.getSetupPose());
		Color darkColor = source.getSetupPose().getDarkColor();
		if (darkColor != null) setSlotDarkColor(copy, darkColor);
		copy.setSkinRequired(source.getSkinRequired());
		copy.setAttachmentName(source.getAttachmentName());
		if (source.getBlendMode() != null) copy.setBlendMode(source.getBlendMode());
		copy.setVisible(source.getVisible());
		return copy;
	}

	private static void setSlotDarkColor(SlotData slot, Color darkColor) {
		try {
			java.lang.reflect.Field field = slot.getSetupPose().getClass().getDeclaredField("darkColor");
			field.setAccessible(true);
			field.set(slot.getSetupPose(), darkColor == null ? null : new Color(darkColor.r, darkColor.g, darkColor.b, 1f));
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("无法更新槽的双色染色数据", error);
		}
	}

	private static void remapBones(Array<BoneData> bones, IdentityHashMap<BoneData, BoneData> boneMap) {
		for (int i = 0; i < bones.size; i++) {
			BoneData mapped = boneMap.get(bones.get(i));
			if (mapped != null) bones.set(i, mapped);
		}
	}

	// ================= 复制 / 粘贴 =================

	public void copyBone(BoneData source) {
		if (source == null) return;
		clipboard = new BoneData(source, null); // 复制构造保留 setup pose + length
		copyBoneProps(source, clipboard); // 仅补充 color/icon 等
	}

	public boolean pasteBoneOnto(BoneData target) {
		if (doc.data == null || target == null || clipboard == null || !doc.data.getBones().contains(target, true)) return false;
		target.getSetupPose().set(clipboard.getSetupPose());
		target.setSkinRequired(clipboard.getSkinRequired());
		target.setLength(clipboard.getLength());
		target.getColor().set(clipboard.getColor());
		target.setIcon(clipboard.getIcon());
		target.setIconSize(clipboard.getIconSize());
		target.setIconRotation(clipboard.getIconRotation());
		target.setVisible(clipboard.getVisible());
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	public boolean hasClipboard() {
		return clipboard != null;
	}

	/** Copies a bone hierarchy without retaining references to the source document. Returns the copied bone count. */
	public int copyBoneSubtree(BoneData source) {
		if (doc.data == null || source == null || !doc.data.getBones().contains(source, true)) return 0;
		Array<BoneData> subtree = new Array<>();
		collectSubtree(doc.data.getBones(), source, subtree);
		IdentityHashMap<BoneData, Integer> indices = new IdentityHashMap<>();
		for (int index = 0; index < subtree.size; index++) indices.put(subtree.get(index), index);
		ArrayList<BoneSnapshot> snapshots = new ArrayList<>(subtree.size);
		for (BoneData bone : subtree) {
			Integer parent = indices.get(bone.getParent());
			snapshots.add(new BoneSnapshot(bone, parent == null ? -1 : parent));
		}
		subtreeClipboard = new BoneSubtreeSnapshot(snapshots);
		return snapshots.size();
	}

	/** Appends the copied hierarchy below parent and returns the new subtree root. */
	public BoneData pasteBoneSubtree(BoneData parent) {
		if (doc.data == null || parent == null || subtreeClipboard == null) return null;
		Array<BoneData> bones = doc.data.getBones();
		int parentIndex = bones.indexOf(parent, true);
		if (parentIndex < 0 || parent.getIndex() != parentIndex) return null;

		Set<String> reservedNames = new HashSet<>();
		for (BoneData bone : bones) reservedNames.add(bone.getName());
		ArrayList<BoneData> created = new ArrayList<>(subtreeClipboard.bones.size());
		for (int index = 0; index < subtreeClipboard.bones.size(); index++) {
			BoneSnapshot snapshot = subtreeClipboard.bones.get(index);
			BoneData mappedParent;
			if (snapshot.parentIndex < 0) {
				mappedParent = parent;
			} else {
				if (snapshot.parentIndex >= index) return null;
				mappedParent = created.get(snapshot.parentIndex);
			}
			String name = uniqueBoneName(snapshot.name, reservedNames);
			BoneData bone = new BoneData(bones.size + index, name, mappedParent);
			snapshot.applyTo(bone);
			created.add(bone);
		}
		if (created.isEmpty()) return null;
		for (BoneData bone : created) bones.add(bone);
		doc.markDirty();
		rebuildSkeleton();
		BoneData root = created.get(0);
		doc.setSelectedBone(doc.skeleton.findBone(root.getName()));
		return root;
	}

	public boolean hasSubtreeClipboard() {
		return subtreeClipboard != null && !subtreeClipboard.bones.isEmpty();
	}

	public int getSubtreeClipboardSize() {
		return subtreeClipboard == null ? 0 : subtreeClipboard.bones.size();
	}

	private static final class BoneSubtreeSnapshot {
		final List<BoneSnapshot> bones;

		BoneSubtreeSnapshot(List<BoneSnapshot> bones) {
			this.bones = List.copyOf(bones);
		}
	}

	private static final class BoneSnapshot {
		final String name;
		final int parentIndex;
		final com.esotericsoftware.spine.BonePose setupPose;
		final boolean skinRequired, visible;
		final float length, red, green, blue, alpha, iconSize, iconRotation;
		final String icon;

		BoneSnapshot(BoneData source, int parentIndex) {
			this.name = source.getName();
			this.parentIndex = parentIndex;
			this.setupPose = new com.esotericsoftware.spine.BonePose();
			this.setupPose.set(source.getSetupPose());
			this.skinRequired = source.getSkinRequired();
			this.visible = source.getVisible();
			this.length = source.getLength();
			this.red = source.getColor().r;
			this.green = source.getColor().g;
			this.blue = source.getColor().b;
			this.alpha = source.getColor().a;
			this.icon = source.getIcon();
			this.iconSize = source.getIconSize();
			this.iconRotation = source.getIconRotation();
		}

		void applyTo(BoneData target) {
			target.getSetupPose().set(setupPose);
			target.setSkinRequired(skinRequired);
			target.setVisible(visible);
			target.setLength(length);
			target.getColor().set(red, green, blue, alpha);
			target.setIcon(icon);
			target.setIconSize(iconSize);
			target.setIconRotation(iconRotation);
		}
	}

	// ================= 辅助 =================

	private static void copyBoneProps(BoneData from, BoneData to) {
		to.setSkinRequired(from.getSkinRequired());
		to.setLength(from.getLength());
		if (from.getIcon() != null) to.setIcon(from.getIcon());
		to.setIconSize(from.getIconSize());
		to.setIconRotation(from.getIconRotation());
		to.setVisible(from.getVisible());
		to.getColor().set(from.getColor());
	}

	private String uniqueBoneName(String base) {
		String name = base == null || base.trim().isEmpty() ? "Bone" : base.trim();
		if (findBoneData(name) == null) return name;
		int n = 2;
		while (findBoneData(name + n) != null) n++;
		return name + n;
	}

	private static String uniqueBoneName(String base, Set<String> reserved) {
		String name = base == null || base.trim().isEmpty() ? "Bone" : base.trim();
		if (reserved.add(name)) return name;
		int suffix = 2;
		while (!reserved.add(name + suffix)) suffix++;
		return name + suffix;
	}

	private BoneData findBoneData(String name) {
		if (doc.data == null) return null;
		return doc.data.findBone(name);
	}
	// ================= 附件与皮肤 =================

	/** 返回直接绑定到指定骨骼的所有槽，供附件面板使用。 */
	public com.badlogic.gdx.utils.Array<com.esotericsoftware.spine.SlotData> slotsForBone(com.esotericsoftware.spine.BoneData bone) {
		com.badlogic.gdx.utils.Array<com.esotericsoftware.spine.SlotData> out = new com.badlogic.gdx.utils.Array<>();
		if (doc.data == null || bone == null || !doc.data.getBones().contains(bone, true)) return out;
		for (com.esotericsoftware.spine.SlotData slot : doc.data.getSlots()) {
			if (slot.getBoneData() == bone) out.add(slot);
		}
		return out;
	}

	/** 在绘制顺序末尾为指定骨骼追加一个已初始化槽。 */
	public com.esotericsoftware.spine.SlotData addSlot(String name, com.esotericsoftware.spine.BoneData bone) {
		if (doc.data == null || bone == null || !doc.data.getBones().contains(bone, true)) return null;
		String unique = uniqueSlotName(name);
		com.esotericsoftware.spine.SlotData slot = new com.esotericsoftware.spine.SlotData(
			doc.data.getSlots().size, unique, bone);
		slot.setBlendMode(com.esotericsoftware.spine.BlendMode.normal);
		slot.setVisible(true);
		doc.data.getSlots().add(slot);
		doc.markDirty();
		rebuildSkeleton();
		return slot;
	}

	/** Moves a setup slot with its skin, constraint, and animation references as one reversible operation. */
	public int[] moveSlot(SlotData target, int direction) {
		if (doc.data == null || target == null || (direction != -1 && direction != 1)) return null;
		Array<SlotData> slots = doc.data.getSlots();
		int from = slots.indexOf(target, true);
		int to = from + direction;
		if (from < 0 || to < 0 || to >= slots.size || target.getIndex() != from) return null;
		SkeletonStructureSnapshot before = SkeletonStructureSnapshot.capture(this);
		try {
			return reorderSlot(from, to);
		} catch (RuntimeException failure) {
			before.rollback();
			throw failure;
		}
	}

	private int[] reorderSlot(int from, int to) {
		Array<SlotData> slots = doc.data.getSlots();
		Array<SlotData> reordered = new Array<>(slots.size);
		for (int index = 0; index < slots.size; index++) reordered.add(slots.get(index));
		SlotData moved = reordered.removeIndex(from);
		reordered.insert(to, moved);
		int[] oldToNew = new int[slots.size];
		for (int index = 0; index < reordered.size; index++) oldToNew[reordered.get(index).getIndex()] = index;

		IdentityHashMap<SlotData, SlotData> slotMap = new IdentityHashMap<>();
		for (int index = 0; index < reordered.size; index++) {
			SlotData old = reordered.get(index);
			// SlotData.index is final, so the constructor index is the source of truth after a reorder.
			SlotData replacement = new SlotData(index, old.getName(), old.getBoneData());
			replacement.getSetupPose().set(old.getSetupPose());
			Color dark = old.getSetupPose().getDarkColor();
			if (dark != null) setSlotDarkColor(replacement, dark);
			replacement.setSkinRequired(old.getSkinRequired());
			replacement.setAttachmentName(old.getAttachmentName());
			if (old.getBlendMode() != null) replacement.setBlendMode(old.getBlendMode());
			replacement.setVisible(old.getVisible());
			slotMap.put(old, replacement);
			reordered.set(index, replacement);
		}

		new AnimationEditor(doc).remapStructureTimelines(null, oldToNew, slots.size);
		for (Skin skin : doc.data.getSkins()) {
			Array<Skin.SkinEntry> entries = new Array<>();
			for (Skin.SkinEntry entry : skin.getAttachments()) entries.add(entry);
			for (Skin.SkinEntry entry : entries) skin.removeAttachment(entry.getSlotIndex(), entry.getPlaceholder());
			for (Skin.SkinEntry entry : entries) {
				int oldIndex = entry.getSlotIndex();
				if (oldIndex >= 0 && oldIndex < oldToNew.length)
					skin.setAttachment(oldToNew[oldIndex], entry.getPlaceholder(), entry.getAttachment());
			}
		}
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint instanceof PathConstraintData path) {
				SlotData replacement = slotMap.get(path.getSlot());
				if (replacement != null) path.setSlot(replacement);
			}
		remapClippingEndSlots(slotMap);
		remapAttachmentTimelineSlots(oldToNew);
		slots.clear();
		slots.addAll(reordered);
		doc.markDirty();
		rebuildSkeleton();
		return oldToNew;
	}

	/** Enables or disables one skin-required bone for a skin. */
	public boolean setSkinBoneActive(Skin skin, BoneData bone, boolean active) {
		if (doc.data == null || skin == null || bone == null || !doc.data.getSkins().contains(skin, true)
			|| !doc.data.getBones().contains(bone, true) || !bone.getSkinRequired()) return false;
		Array<BoneData> bones = skin.getBones();
		int index = bones.indexOf(bone, true);
		if (active) {
			if (index >= 0) return false;
			bones.add(bone);
		} else {
			if (index < 0) return false;
			bones.removeIndex(index);
		}
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	public boolean isSkinBoneActive(Skin skin, BoneData bone) {
		return skin != null && bone != null && skin.getBones().contains(bone, true);
	}

	/** Renames a slot without changing its index or index-based skin and animation data. */
	public String renameSlot(SlotData target, String newName) {
		if (doc.data == null || target == null || newName == null || newName.trim().isEmpty()) return null;
		Array<SlotData> slots = doc.data.getSlots();
		int position = slots.indexOf(target, true);
		if (position < 0 || target.getIndex() != position) return null;
		String requested = newName.trim();
		if (target.getName().equals(requested)) return target.getName();
		String unique = uniqueSlotName(requested);
		String oldName = target.getName();
		SlotData renamed = copySlotData(target, unique, target.getBoneData());
		slots.set(position, renamed);

		for (ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
			if (constraint instanceof PathConstraintData path && path.getSlot() == target) path.setSlot(renamed);
		}
		IdentityHashMap<SlotData, SlotData> slotMap = new IdentityHashMap<>();
		slotMap.put(target, renamed);
		remapClippingEndSlots(slotMap);
		doc.renameMeshWeightStateToken(1, oldName, unique);
		doc.renameEditorSlotState(oldName, unique);
		doc.markDirty();
		rebuildSkeleton();
		return unique;
	}

	/** Returns null when the slot can be removed while remapping surviving references. */
	public String getSlotDeleteBlockReason(SlotData target) {
		if (doc.data == null || target == null) return "槽数据无效";
		Array<SlotData> slots = doc.data.getSlots();
		int position = slots.indexOf(target, true);
		if (position < 0 || target.getIndex() != position) return "槽索引与当前项目不一致";

		Array<Skin.SkinEntry> entries = new Array<>();
		for (Skin skin : doc.data.getSkins()) {
			entries.clear();
			skin.getAttachments(position, entries);
			if (entries.notEmpty()) return "皮肤 " + skin.getName() + " 仍包含该槽的附件";
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (entry.getAttachment() instanceof ClippingAttachment clipping && clipping.getEndSlot() == target)
					return "裁剪附件 " + entry.getPlaceholder() + " 仍以该槽作为结束槽";
			}
		}
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints()) {
			if (constraint instanceof PathConstraintData path && path.getSlot() == target)
				return "路径约束 " + path.getName() + " 仍引用该槽";
		}
		for (Animation animation : doc.data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (timeline instanceof Animation.SlotTimeline slotTimeline && slotTimeline.getSlotIndex() == position)
					return "动画 " + animation.getName() + " 仍包含该槽的时间线";
			}
		}
		return null;
	}

	public boolean deleteSlot(SlotData target) {
		if (getSlotDeleteBlockReason(target) != null) return false;
		SkeletonStructureSnapshot before = SkeletonStructureSnapshot.capture(this);
		try {
			Array<SlotData> slots = doc.data.getSlots();
			int[] indices = new int[slots.size];
			IdentityHashMap<SlotData, SlotData> mapped = new IdentityHashMap<>();
			Array<SlotData> retained = new Array<>();
			for (SlotData old : slots) {
				if (old == target) { indices[old.getIndex()] = -1; continue; }
				SlotData replacement = old.getIndex() == retained.size ? old
					: new SlotData(retained.size, old.getName(), old.getBoneData());
				replacement.getSetupPose().set(old.getSetupPose());
				setSlotDarkColor(replacement, old.getSetupPose().getDarkColor());
				replacement.setSkinRequired(old.getSkinRequired());
				replacement.setAttachmentName(old.getAttachmentName());
				if (old.getBlendMode() != null) replacement.setBlendMode(old.getBlendMode());
				replacement.setVisible(old.getVisible());
				indices[old.getIndex()] = retained.size;
				mapped.put(old, replacement);
				retained.add(replacement);
			}
			new AnimationEditor(doc).remapStructureTimelines(null, indices, retained.size);
			for (Skin skin : doc.data.getSkins()) {
				Array<Skin.SkinEntry> entries = new Array<>();
				for (Skin.SkinEntry entry : skin.getAttachments()) entries.add(entry);
				for (Skin.SkinEntry entry : entries) skin.removeAttachment(entry.getSlotIndex(), entry.getPlaceholder());
				for (Skin.SkinEntry entry : entries)
					skin.setAttachment(indices[entry.getSlotIndex()], entry.getPlaceholder(), entry.getAttachment());
			}
			for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof PathConstraintData path) path.setSlot(mapped.get(path.getSlot()));
			remapClippingEndSlots(mapped);
			remapAttachmentTimelineSlots(indices);
			slots.clear();
			slots.addAll(retained);
			doc.removeMeshWeightStatesForToken(1, target.getName());
			doc.removeEditorSlotState(target.getName());
			doc.markDirty();
			rebuildSkeleton();
			return true;
		} catch (RuntimeException failure) {
			before.rollback();
			throw failure;
		}
	}

	private void remapAttachmentTimelineSlots(int[] oldToNew) {
		IdentityHashMap<Attachment, Boolean> seen = new IdentityHashMap<>();
		for (Skin skin : doc.data.getSkins()) for (Skin.SkinEntry entry : skin.getAttachments()) {
			Attachment attachment = entry.getAttachment();
			if (seen.put(attachment, true) != null) continue;
			int[] source = attachment.getTimelineSlots();
			if (source == null || source.length == 0) continue;
			int[] mapped = Arrays.stream(source).map(index -> {
				if (index < 0 || index >= oldToNew.length)
					throw new IllegalArgumentException("Invalid attachment timeline slot: " + index);
				return oldToNew[index];
			}).filter(index -> index >= 0).distinct().sorted().toArray();
			attachment.setTimelineSlots(mapped);
		}
	}

	/** Updates exportable setup properties for a slot and synchronizes the runtime skeleton. */
	public boolean updateSlotProperties(com.esotericsoftware.spine.SlotData slot, Color color, Color darkColor,
		BlendMode blendMode, boolean visible) {
		if (doc.data == null || slot == null || color == null || blendMode == null
			|| !doc.data.getSlots().contains(slot, true) || !isUnitColor(color)
			|| (darkColor != null && !isUnitRgb(darkColor))) return false;
		Color setupColor = slot.getSetupPose().getColor();
		Color setupDarkColor = slot.getSetupPose().getDarkColor();
		if (setupDarkColor != null && darkColor == null && getSlotDarkColorDisableBlockReason(slot) != null)
			return false;
		boolean sameDarkColor = setupDarkColor == null ? darkColor == null
			: darkColor != null && sameRgb(setupDarkColor, darkColor);
		if (sameColor(setupColor, color) && sameDarkColor
			&& slot.getBlendMode() == blendMode && slot.getVisible() == visible)
			return false;
		if (!sameDarkColor) setSlotDarkColor(slot, darkColor);
		setupColor.set(color);
		slot.setBlendMode(blendMode);
		slot.setVisible(visible);
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	/** Returns the first animation that requires this slot's setup dark color, or null when it can be disabled. */
	public String getSlotDarkColorDisableBlockReason(SlotData slot) {
		if (doc.data == null || slot == null || !doc.data.getSlots().contains(slot, true)) return null;
		for (Animation animation : doc.data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (timeline instanceof Animation.RGB2Timeline color && color.getSlotIndex() == slot.getIndex())
					return "动画 " + animation.getName() + " 包含 RGB2 双色轨道";
				if (timeline instanceof Animation.RGBA2Timeline color && color.getSlotIndex() == slot.getIndex())
					return "动画 " + animation.getName() + " 包含 RGBA2 双色轨道";
			}
		}
		return null;
	}

	private static boolean isUnitColor(Color color) {
		return Float.isFinite(color.r) && Float.isFinite(color.g) && Float.isFinite(color.b) && Float.isFinite(color.a)
			&& color.r >= 0f && color.r <= 1f && color.g >= 0f && color.g <= 1f
			&& color.b >= 0f && color.b <= 1f && color.a >= 0f && color.a <= 1f;
	}

	private static boolean isUnitRgb(Color color) {
		return Float.isFinite(color.r) && Float.isFinite(color.g) && Float.isFinite(color.b)
			&& color.r >= 0f && color.r <= 1f && color.g >= 0f && color.g <= 1f
			&& color.b >= 0f && color.b <= 1f;
	}

	private static boolean sameColor(Color a, Color b) {
		return Float.compare(a.r, b.r) == 0 && Float.compare(a.g, b.g) == 0
			&& Float.compare(a.b, b.b) == 0 && Float.compare(a.a, b.a) == 0;
	}

	private static boolean sameRgb(Color a, Color b) {
		return Float.compare(a.r, b.r) == 0 && Float.compare(a.g, b.g) == 0
			&& Float.compare(a.b, b.b) == 0;
	}

	/** 列出某槽在当前皮肤（含默认皮肤）中可用的所有附件占位名 */
	public com.badlogic.gdx.utils.Array<String> attachmentNames(int slotIndex, com.badlogic.gdx.utils.Array<com.esotericsoftware.spine.Skin.SkinEntry> scratch) {
		com.badlogic.gdx.utils.Array<String> out = new com.badlogic.gdx.utils.Array<>();
		if (doc.data == null) return out;
		com.badlogic.gdx.utils.Array<com.esotericsoftware.spine.Skin.SkinEntry> entries = scratch != null ? scratch : new com.badlogic.gdx.utils.Array<>();
		entries.clear();
		com.esotericsoftware.spine.Skin defaultSkin = doc.data.getDefaultSkin();
		if (defaultSkin != null) {
			defaultSkin.getAttachments(slotIndex, entries);
			for (com.esotericsoftware.spine.Skin.SkinEntry e : entries)
				if (!out.contains(e.getPlaceholder(), false)) out.add(e.getPlaceholder());
		}
		com.esotericsoftware.spine.Skin skin = doc.skeleton == null ? null : doc.skeleton.getSkin();
		if (skin != null && skin != defaultSkin) {
			entries.clear();
			skin.getAttachments(slotIndex, entries);
			for (com.esotericsoftware.spine.Skin.SkinEntry e : entries)
				if (!out.contains(e.getPlaceholder(), false)) out.add(e.getPlaceholder());
		}
		return out;
	}

	/** 当前皮肤中某槽的附件（含默认皮肤回退） */
	public com.esotericsoftware.spine.attachments.Attachment currentAttachment(int slotIndex, String placeholder) {
		if (doc.data == null || doc.skeleton == null || placeholder == null) return null;
		com.esotericsoftware.spine.Skin skin = doc.skeleton == null ? null : doc.skeleton.getSkin();
		com.esotericsoftware.spine.attachments.Attachment a = null;
		if (skin != null) a = skin.getAttachment(slotIndex, placeholder);
		if (a == null) {
			com.esotericsoftware.spine.Skin ds = doc.data.getDefaultSkin();
			if (ds != null) a = ds.getAttachment(slotIndex, placeholder);
		}
		if (a == null) a = doc.skeleton.getAttachment(slotIndex, placeholder);
		return a;
	}

	/** 设置槽的 setup attachment 并同步运行时姿态。placeholder 为 null 时清空附件。 */
	public boolean setSlotAttachment(com.esotericsoftware.spine.SlotData slotData, String placeholder) {
		if (doc.data == null || doc.skeleton == null || slotData == null
			|| !doc.data.getSlots().contains(slotData, true)) return false;
		if (java.util.Objects.equals(slotData.getAttachmentName(), placeholder)) return false;
		com.esotericsoftware.spine.attachments.Attachment attachment = null;
		if (placeholder != null) {
			if (placeholder.isBlank()) return false;
			attachment = currentAttachment(slotData.getIndex(), placeholder);
			if (attachment == null) return false;
		}
		com.esotericsoftware.spine.Slot slot = doc.skeleton.findSlot(slotData.getName());
		if (slot == null) return false;
		slotData.setAttachmentName(placeholder);
		slot.getPose().setAttachment(attachment);
		doc.markDirty();
		doc.refreshCurrentAnimationPose();
		return true;
	}

	/** 返回 atlas 中可用于区域附件的区域名，保持 atlas 顺序并去重。 */
	public com.badlogic.gdx.utils.Array<String> atlasRegionNames() {
		com.badlogic.gdx.utils.Array<String> names = new com.badlogic.gdx.utils.Array<>();
		if (doc.atlas == null) return names;
		for (com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion region : doc.atlas.getRegions()) {
			if (region.name != null && !region.name.isBlank() && !names.contains(region.name, false))
				names.add(region.name);
		}
		return names;
	}

	public boolean skinHasAttachment(com.esotericsoftware.spine.Skin skin, int slotIndex, String placeholder) {
		return skin != null && placeholder != null && skin.getAttachment(slotIndex, placeholder) != null;
	}

	/** Returns null when removing this skin entry would leave no dangling attachment-specific references. */
	public String getAttachmentDeleteBlockReason(Skin skin, SlotData slot, String placeholder) {
		if (doc.data == null || skin == null || slot == null || placeholder == null
			|| !doc.data.getSkins().contains(skin, true) || !doc.data.getSlots().contains(slot, true))
			return "附件目标无效";
		Attachment attachment = skin.getAttachment(slot.getIndex(), placeholder);
		if (attachment == null) return "当前皮肤没有该附件覆盖";
		if (hasOtherAttachmentOwner(attachment, skin, slot.getIndex(), placeholder)) return null;
		return getAttachmentReferenceBlockReason(attachment, null);
	}

	/** 删除指定皮肤自己的附件覆盖；同名默认附件仍可作为回退。 */
	public boolean removeSkinAttachment(com.esotericsoftware.spine.Skin skin,
		com.esotericsoftware.spine.SlotData slot, String placeholder) {
		if (doc.skeleton == null || getAttachmentDeleteBlockReason(skin, slot, placeholder) != null) return false;
		Attachment removed = skin.getAttachment(slot.getIndex(), placeholder);
		skin.removeAttachment(slot.getIndex(), placeholder);
		doc.removeMeshWeightState(skin.getName(), slot.getName(), placeholder);
		releaseInheritedTimelineSlot(removed, slot.getIndex());
		if (placeholder.equals(slot.getAttachmentName())
			&& !hasAttachmentInAnySkin(slot.getIndex(), placeholder)) slot.setAttachmentName(null);
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	private boolean hasAttachmentInAnySkin(int slotIndex, String placeholder) {
		if (doc.data == null || placeholder == null) return false;
		for (Skin candidate : doc.data.getSkins())
			if (candidate.getAttachment(slotIndex, placeholder) != null) return true;
		return false;
	}

	private boolean hasOtherAttachmentOwner(Attachment attachment, Skin ignoredSkin, int ignoredSlot,
		String ignoredPlaceholder) {
		for (Skin skin : doc.data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (skin == ignoredSkin && entry.getSlotIndex() == ignoredSlot
					&& entry.getPlaceholder().equals(ignoredPlaceholder)) continue;
				if (entry.getAttachment() == attachment) return true;
			}
		}
		return false;
	}

	private boolean hasAttachmentOwnerOutsideSkin(Attachment attachment, Skin ignoredSkin) {
		for (Skin skin : doc.data.getSkins()) {
			if (skin == ignoredSkin) continue;
			for (Skin.SkinEntry entry : skin.getAttachments())
				if (entry.getAttachment() == attachment) return true;
		}
		return false;
	}

	private String getAttachmentReferenceBlockReason(Attachment attachment, Skin ignoredReferenceSkin) {
		String timelineReason = getAttachmentTimelineReferenceReason(attachment);
		if (timelineReason != null) return timelineReason;
		for (Skin skin : doc.data.getSkins()) {
			if (skin == ignoredReferenceSkin) continue;
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (entry.getAttachment() instanceof MeshAttachment mesh && mesh.getSourceMesh() == attachment)
					return "皮肤 " + skin.getName() + " 的 Linked Mesh " + entry.getPlaceholder() + " 仍引用该附件";
			}
		}
		return null;
	}

	private String getAttachmentTimelineReferenceReason(Attachment attachment) {
		for (Animation animation : doc.data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (timeline instanceof Animation.DeformTimeline deform && deform.getAttachment() == attachment)
					return "动画 " + animation.getName() + " 的 Deform 轨道仍引用该附件";
				if (timeline instanceof Animation.SequenceTimeline sequence && sequence.getAttachment() == attachment)
					return "动画 " + animation.getName() + " 的 Sequence 轨道仍引用该附件";
			}
		}
		return null;
	}

	// ================= 皮肤管理 =================

	private record SkinStructureState(Array<Skin> skins, List<String> preview,
		Map<String, Set<SkeletonDocument.MeshWeightLock>> locks, Map<SlotData, String> setupAttachments,
		Map<Attachment, int[]> timelineSlots) {}

	/** Keeps the exact before/after Skin instances so older history can resolve its original objects again.
	 * Runtime Skin names are immutable; renaming swaps a pair of stable instances with shared attachments. */
	public final class SkinStructureEdit {
		private final SkeletonData identity = doc.data;
		private final Skin skin;
		private final SkinStructureState before, after;
		private final String renamedFrom;
		private final Set<String> changedLocks = new HashSet<>();
		private final Set<SlotData> changedSetup = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		private final Set<Attachment> changedTimelineSlots = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		private boolean applied = true;

		private SkinStructureEdit(Skin skin, SkinStructureState before, SkinStructureState after, String renamedFrom) {
			this.skin = skin; this.before = before; this.after = after; this.renamedFrom = renamedFrom;
			changedLocks.addAll(before.locks().keySet()); changedLocks.addAll(after.locks().keySet());
			changedLocks.removeIf(key -> java.util.Objects.equals(before.locks().get(key), after.locks().get(key)));
			for (var entry : before.setupAttachments().entrySet())
				if (after.setupAttachments().containsKey(entry.getKey())
					&& !java.util.Objects.equals(entry.getValue(), after.setupAttachments().get(entry.getKey()))) changedSetup.add(entry.getKey());
			for (var entry : before.timelineSlots().entrySet())
				if (after.timelineSlots().containsKey(entry.getKey())
					&& !Arrays.equals(entry.getValue(), after.timelineSlots().get(entry.getKey()))) changedTimelineSlots.add(entry.getKey());
		}

		public Skin skin() { return skin; }
		public void undo() { restore(false); }
		public void redo() { restore(true); }

		private void restore(boolean forward) {
			if (identity != doc.data) throw new IllegalStateException("皮肤编辑所属骨架已改变");
			if (forward == applied) return;
			SkinStructureState expected = forward ? before : after, target = forward ? after : before;
			if (doc.data.getSkins().size != expected.skins().size) throw new IllegalStateException("皮肤列表已改变，无法恢复该编辑");
			for (int i = 0; i < expected.skins().size; i++)
				if (doc.data.getSkins().get(i) != expected.skins().get(i)) throw new IllegalStateException("皮肤引用已改变，无法恢复该编辑");
			restoreRetainedSkinConstraints(target.skins());
			doc.data.getSkins().clear(); doc.data.getSkins().addAll(target.skins());
			for (String key : changedLocks) doc.setMeshWeightLocks(key, target.locks().get(key));
			for (SlotData slot : changedSetup) slot.setAttachmentName(target.setupAttachments().get(slot));
			for (Attachment attachment : changedTimelineSlots) {
				int[] slots = target.timelineSlots().get(attachment);
				attachment.setTimelineSlots(slots == null ? null : slots.clone());
			}
			if (renamedFrom != null) doc.renameEditorSkinState(forward ? renamedFrom : skin.getName(),
				forward ? skin.getName() : renamedFrom);
			applied = forward;
			doc.markDirty(); rebuildSkeleton(); doc.restorePreviewSkins(target.preview());
		}
	}

	/** Earlier constraint history may recreate an object while a renamed/copied skin is outside the live list. */
	private void restoreRetainedSkinConstraints(Array<Skin> skins) {
		Map<Skin, List<ConstraintData<?, ?>>> replacements = new IdentityHashMap<>();
		for (Skin skin : skins) {
			List<ConstraintData<?, ?>> resolved = new ArrayList<>();
			boolean changed = false;
			for (ConstraintData<?, ?> old : skin.getConstraints()) {
				ConstraintData<?, ?> current = old;
				if (!doc.data.getConstraints().contains(old, true)) {
					current = null;
					for (ConstraintData<?, ?> candidate : doc.data.getConstraints()) {
						if (candidate.getClass() != old.getClass() || !candidate.getName().equals(old.getName())) continue;
						if (current != null) throw new IllegalStateException("无法恢复皮肤约束：名称不唯一 " + old.getName());
						current = candidate;
					}
					if (current == null) throw new IllegalStateException("无法恢复皮肤约束：约束不存在 " + old.getName());
					changed = true;
				}
				resolved.add(current);
			}
			if (changed) replacements.put(skin, resolved);
		}
		// Resolve every reference first: a rejected restore must not alter any retained or live skin.
		for (var entry : replacements.entrySet()) {
			entry.getKey().getConstraints().clear();
			for (ConstraintData<?, ?> constraint : entry.getValue()) entry.getKey().getConstraints().add(constraint);
		}
	}

	private SkinStructureState skinStructureState() {
		return skinStructureState(Set.of());
	}

	private SkinStructureState skinStructureState(Set<Attachment> retainedAttachments) {
		Map<SlotData, String> setup = new IdentityHashMap<>();
		for (SlotData slot : doc.data.getSlots()) setup.put(slot, slot.getAttachmentName());
		Map<Attachment, int[]> slots = new IdentityHashMap<>();
		for (Skin skin : doc.data.getSkins()) for (Skin.SkinEntry entry : skin.getAttachments()) {
			Attachment attachment = entry.getAttachment(), owner = attachment.getTimelineAttachment();
			int[] value = attachment.getTimelineSlots(); slots.put(attachment, value == null ? null : value.clone());
			if (owner != null) { value = owner.getTimelineSlots(); slots.put(owner, value == null ? null : value.clone()); }
		}
		// Deleting a skin can update timelineSlots on a source removed in the same skin.
		for (Attachment attachment : retainedAttachments) {
			int[] value = attachment.getTimelineSlots(); slots.put(attachment, value == null ? null : value.clone());
		}
		return new SkinStructureState(new Array<>(doc.data.getSkins()), doc.getPreviewSkinNames(),
			doc.meshWeightLockStates(), setup, slots);
	}

	private SkinStructureEdit skinStructureEdit(java.util.function.Supplier<Skin> mutation) {
		return skinStructureEdit(mutation, null);
	}

	private SkinStructureEdit skinStructureEdit(java.util.function.Supplier<Skin> mutation, String renamedFrom) {
		if (doc.data == null) return null;
		SkinStructureState before = skinStructureState();
		Skin result = mutation.get();
		return result == null ? null : new SkinStructureEdit(result, before,
			skinStructureState(before.timelineSlots().keySet()), renamedFrom);
	}

	/** Applies a rename and returns its history command; no-op or invalid requests do not create history. */
	public SkinStructureEdit renameSkinEdit(Skin target, String name) {
		if (target == null || name == null || target.getName().equals(name.trim())) return null;
		return skinStructureEdit(() -> {
			String renamed = renameSkin(target, name);
			return renamed == null ? null : doc.data.findSkin(renamed);
		}, target.getName());
	}

	public SkinStructureEdit addSkinEdit(String name, boolean copyCurrent) {
		return skinStructureEdit(() -> addSkin(name, copyCurrent));
	}

	public SkinStructureEdit duplicateSkinEdit(Skin source, String name) {
		return skinStructureEdit(() -> duplicateSkin(source, name));
	}

	public SkinStructureEdit removeSkinEdit(Skin skin) {
		return skinStructureEdit(() -> removeSkin(skin) ? skin : null);
	}

	private record SkinCompositionKey(int slot, String placeholder) {}
	private record SkinCompositionTracks(Animation animation, Array<Animation.Timeline> before,
		Array<Animation.Timeline> after, com.badlogic.gdx.utils.IntArray bones) {}

	/** A permanent composition owns its attachments and copied tracks; source skins remain independent. */
	public final class SkinCompositionEdit {
		private final SkeletonData identity = doc.data;
		private final Skin skin;
		private final List<String> previousPreview = doc.getPreviewSkinNames();
		private final List<SkinCompositionTracks> tracks;
		private final Map<String, Set<SkeletonDocument.MeshWeightLock>> locks;
		private boolean applied;

		private SkinCompositionEdit(Skin skin, List<SkinCompositionTracks> tracks,
			Map<String, Set<SkeletonDocument.MeshWeightLock>> locks) {
			this.skin = skin;
			this.tracks = tracks;
			this.locks = locks;
		}

		public Skin skin() { return skin; }
		public void undo() { restore(false); }
		public void redo() { restore(true); }

		private void restore(boolean add) {
			if (identity != doc.data) throw new IllegalStateException("皮肤组合所属骨架已改变");
			if (add == applied) return;
			if (add) {
				if (doc.data.findSkin(skin.getName()) != null) throw new IllegalStateException("组合皮肤名称已被占用");
				doc.data.getSkins().add(skin);
				for (var entry : locks.entrySet()) doc.setMeshWeightLocks(entry.getKey(), entry.getValue());
			} else {
				doc.data.getSkins().removeValue(skin, true);
				doc.removeMeshWeightStatesForToken(0, skin.getName());
			}
			for (SkinCompositionTracks item : tracks)
				item.animation().setTimelines(new Array<>(add ? item.after() : item.before()),
					new com.badlogic.gdx.utils.IntArray(item.bones()));
			applied = add;
			doc.markDirty();
			rebuildSkeleton();
			doc.restorePreviewSkins(add ? List.of(skin.getName()) : previousPreview);
		}
	}

	/** Validates a draft without changing preview, skins, attachments, or animation tracks. */
	public String getSkinCompositionBlockReason(List<String> layers, String name) {
		if (doc.data == null || doc.skeleton == null) return "没有可组合的骨架";
		if (name == null || name.trim().isEmpty()) return "请输入新皮肤名称";
		for (Skin skin : doc.data.getSkins())
			if (skin.getName().equalsIgnoreCase(name.trim())) return "皮肤名称已存在";
		if ("default".equalsIgnoreCase(name.trim())) return "不能将组合保存为默认皮肤";
		if (layers == null || layers.isEmpty()) return "请至少选择一个皮肤";
		Set<String> unique = new HashSet<>();
		for (String layer : layers)
			if (layer == null || doc.data.findSkin(layer) == null || !unique.add(layer)) return "组合包含缺失或重复的皮肤";
		for (AttachmentOwner owner : composedAttachments(layers).values()) {
			if (owner.slotIndex() < 0 || owner.slotIndex() >= doc.data.getSlots().size) return "皮肤附件槽索引无效";
			Attachment attachment = owner.skin().getAttachment(owner.slotIndex(), owner.placeholder());
			if (!(attachment instanceof RegionAttachment || attachment instanceof MeshAttachment
				|| attachment instanceof BoundingBoxAttachment || attachment instanceof ClippingAttachment
				|| attachment instanceof PathAttachment || attachment instanceof PointAttachment)) return "组合包含不支持的附件类型";
			if (!(attachment instanceof MeshAttachment) && attachment.getTimelineAttachment() != null
				&& attachment.getTimelineAttachment() != attachment) return "非 Mesh 附件继承其他附件的轨道，无法安全固化";
			if (attachment instanceof MeshAttachment mesh) {
				if (mesh.getSourceMesh() != null && mesh.getSourceMesh().getSourceMesh() != null)
					return "组合包含嵌套或循环的 Linked Mesh";
				String reason = validateMeshGeometry(mesh, meshGeometryState(mesh), true, false);
				if (reason != null) return "组合网格无效：" + reason;
				if (mesh.getTriangles().length != (mesh.getWorldVerticesLength() - mesh.getHullLength() / 2 - 2) * 3)
					return "组合网格需要完整三角化，才能同时保存为 JSON 和 SKEL";
			}
		}
		return null;
	}

	/** Materializes default fallback plus ordered layers, cloning effective Deform/Sequence ownership per winning entry. */
	public SkinCompositionEdit createSkinComposition(List<String> layers, String name) {
		String reason = getSkinCompositionBlockReason(layers, name);
		if (reason != null) throw new IllegalArgumentException(reason);
		Skin combined = new Skin(name.trim());
		for (String layer : layers) {
			Skin source = doc.data.findSkin(layer);
			for (BoneData bone : source.getBones()) if (!combined.getBones().contains(bone, true)) combined.getBones().add(bone);
			for (var constraint : source.getConstraints())
				if (!combined.getConstraints().contains(constraint, true)) combined.getConstraints().add(constraint);
		}
		combined.getColor().set(doc.data.findSkin(layers.get(layers.size() - 1)).getColor());
		Map<Animation, Array<Animation.Timeline>> additions = new LinkedHashMap<>();
		Map<String, Set<SkeletonDocument.MeshWeightLock>> locks = new LinkedHashMap<>();
		AnimationEditor animationEditor = new AnimationEditor(doc);
		for (AttachmentOwner owner : composedAttachments(layers).values()) {
			Attachment source = owner.skin().getAttachment(owner.slotIndex(), owner.placeholder());
			Attachment copy = independentSkinAttachment(source);
			combined.setAttachment(owner.slotIndex(), owner.placeholder(), copy);
			Attachment timelineOwner = source.getTimelineAttachment();
			for (Animation animation : doc.data.getAnimations()) for (Animation.Timeline timeline : animation.getTimelines()) {
				Attachment target = timeline instanceof Animation.DeformTimeline deform ? deform.getAttachment()
					: timeline instanceof Animation.SequenceTimeline sequence ? sequence.getAttachment() : null;
				if (target == null || target != timelineOwner) continue;
				int timelineSlot = ((Animation.SlotTimeline)timeline).getSlotIndex();
				if (timelineSlot != owner.slotIndex() && !containsSlot(target.getTimelineSlots(), owner.slotIndex())) continue;
				if (timeline instanceof Animation.DeformTimeline && !(copy instanceof VertexAttachment))
					throw new IllegalArgumentException("组合包含无法匹配附件的 Deform 轨道");
				if (timeline instanceof Animation.SequenceTimeline && !(copy instanceof HasSequence))
					throw new IllegalArgumentException("组合包含无法匹配附件的 Sequence 轨道");
				additions.computeIfAbsent(animation, ignored -> new Array<>())
					.add(animationEditor.cloneAttachmentTimeline(timeline, copy, owner.slotIndex()));
			}
			if (copy instanceof MeshAttachment) {
				String slot = doc.data.getSlots().get(owner.slotIndex()).getName();
				Set<SkeletonDocument.MeshWeightLock> sourceLocks = doc.meshWeightLocks(
					SkeletonDocument.meshWeightStateKey(owner.skin().getName(), slot, owner.placeholder()));
				if (!sourceLocks.isEmpty()) locks.put(SkeletonDocument.meshWeightStateKey(combined.getName(), slot, owner.placeholder()), sourceLocks);
			}
		}
		List<SkinCompositionTracks> tracks = new ArrayList<>();
		for (var entry : additions.entrySet()) {
			Array<Animation.Timeline> before = new Array<>(entry.getKey().getTimelines()), after = new Array<>(before);
			after.addAll(entry.getValue());
			tracks.add(new SkinCompositionTracks(entry.getKey(), before, after,
				new com.badlogic.gdx.utils.IntArray(entry.getKey().getBones())));
		}
		SkinCompositionEdit edit = new SkinCompositionEdit(combined, tracks, locks);
		edit.redo();
		return edit;
	}

	private Map<SkinCompositionKey, AttachmentOwner> composedAttachments(List<String> layers) {
		Map<SkinCompositionKey, AttachmentOwner> entries = new LinkedHashMap<>();
		List<Skin> skins = new ArrayList<>();
		if (doc.data.getDefaultSkin() != null) skins.add(doc.data.getDefaultSkin());
		for (String name : layers) skins.add(doc.data.findSkin(name));
		for (Skin skin : skins) for (Skin.SkinEntry entry : skin.getAttachments())
			entries.put(new SkinCompositionKey(entry.getSlotIndex(), entry.getPlaceholder()),
				new AttachmentOwner(skin, entry.getSlotIndex(), entry.getPlaceholder()));
		return entries;
	}

	private static boolean containsSlot(int[] slots, int slot) {
		if (slots != null) for (int candidate : slots) if (candidate == slot) return true;
		return false;
	}

	private static Attachment independentSkinAttachment(Attachment source) {
		Attachment copy = source.copy();
		if (source instanceof MeshAttachment mesh) {
			MeshAttachment target = (MeshAttachment)copy;
			target.setSourceMesh(null);
			target.setBones(mesh.getBones() == null ? null : mesh.getBones().clone());
			target.setVertices(mesh.getVertices().clone());
			target.setWorldVerticesLength(mesh.getWorldVerticesLength());
			target.setRegionUVs(mesh.getRegionUVs().clone());
			target.setTriangles(mesh.getTriangles().clone());
			target.setHullLength(mesh.getHullLength());
			target.setEdges(mesh.getEdges() == null ? null : mesh.getEdges().clone());
			target.setWidth(mesh.getWidth());
			target.setHeight(mesh.getHeight());
			target.updateSequence();
		} else if (copy instanceof RegionAttachment region) region.updateSequence();
		copy.setTimelineAttachment(copy);
		copy.setTimelineSlots(new int[0]);
		return copy;
	}

	/** Renames a non-default skin while preserving its attachments and active preview state. */
	public String renameSkin(Skin target, String newName) {
		if (doc.data == null || target == null || newName == null || newName.trim().isEmpty()
			|| target == doc.data.getDefaultSkin() || !doc.data.getSkins().contains(target, true)) return null;
		String requested = newName.trim();
		if (target.getName().equals(requested)) return target.getName();
		String unique = uniqueSkinName(requested);
		String oldName = target.getName();
		Skin renamed = new Skin(unique);
		renamed.addSkin(target);
		renamed.getColor().set(target.getColor());
		List<String> previewSkins = doc.getPreviewSkinNames().stream()
			.map(name -> name.equals(oldName) ? unique : name).toList();
		int index = doc.data.getSkins().indexOf(target, true);
		doc.data.getSkins().set(index, renamed);
		doc.renameMeshWeightStateToken(0, oldName, unique);
		doc.renameEditorSkinState(oldName, unique);
		doc.markDirty();
		rebuildSkeleton();
		doc.restorePreviewSkins(previewSkins);
		return unique;
	}

	/** 新建皮肤：复制当前皮肤内容（可选）并加入 data.skins。返回新 Skin */
	public com.esotericsoftware.spine.Skin addSkin(String name, boolean copyCurrent) {
		Skin source = copyCurrent ? doc.getEditingSkin() : null;
		if (getSkinDuplicateBlockReason(source) != null) return null;
		return createSkin(name, source);
	}

	public Skin duplicateSkin(Skin source, String name) {
		if (doc.data == null || source == null || !doc.data.getSkins().contains(source, true)) return null;
		if (getSkinDuplicateBlockReason(source) != null) return null;
		return createSkin(name, source);
	}

	/** Non-mesh attachment timeline inheritance created by Skin.copySkin cannot be represented by Spine JSON. */
	public String getSkinDuplicateBlockReason(Skin source) {
		if (source == null) return null;
		if (doc.data == null || !doc.data.getSkins().contains(source, true)) return "皮肤数据无效";
		for (Skin.SkinEntry entry : source.getAttachments()) {
			if (entry.getAttachment() instanceof MeshAttachment) continue;
			String reason = getAttachmentTimelineReferenceReason(entry.getAttachment());
			if (reason != null)
				return reason + "；当前版本不能为复制的非 Mesh 附件自动建立独立轨道";
		}
		return null;
	}

	private Skin createSkin(String name, Skin source) {
		if (doc.data == null) return null;
		String unique = uniqueSkinName(name);
		Skin newSkin = new Skin(unique);
		if (source != null) {
			newSkin.copySkin(source);
			newSkin.getColor().set(source.getColor());
		}
		doc.data.getSkins().add(newSkin);
		doc.markDirty();
		rebuildSkeleton();
		doc.setSkin(newSkin.getName());
		return newSkin;
	}

	/** 删除皮肤（default 不可删）。返回是否成功 */
	public boolean removeSkin(com.esotericsoftware.spine.Skin skin) {
		if (getSkinDeleteBlockReason(skin) != null) return false;
		com.esotericsoftware.spine.Skin ds = doc.data.getDefaultSkin();
		doc.removeMeshWeightStatesForToken(0, skin.getName());
		if (!doc.data.getSkins().removeValue(skin, true)) return false;
		for (Skin.SkinEntry entry : skin.getAttachments()) {
			releaseInheritedTimelineSlot(entry.getAttachment(), entry.getSlotIndex());
			int slotIndex = entry.getSlotIndex();
			if (slotIndex < 0 || slotIndex >= doc.data.getSlots().size) continue;
			SlotData slot = doc.data.getSlots().get(slotIndex);
			String placeholder = entry.getPlaceholder();
			if (placeholder != null && placeholder.equals(slot.getAttachmentName())
				&& !hasAttachmentInAnySkin(slotIndex, placeholder)) slot.setAttachmentName(null);
		}
		if (doc.skeleton != null && doc.skeleton.getSkin() == skin) {
			if (ds != null) doc.skeleton.setSkin(ds);
		}
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	/** Returns null when the skin can be removed without orphaning attachment-specific timelines or linked meshes. */
	public String getSkinDeleteBlockReason(Skin skin) {
		if (doc.data == null || skin == null || !doc.data.getSkins().contains(skin, true)) return "皮肤数据无效";
		if (skin == doc.data.getDefaultSkin()) return "默认皮肤不能删除";
		for (Skin.SkinEntry entry : skin.getAttachments()) {
			Attachment attachment = entry.getAttachment();
			if (hasAttachmentOwnerOutsideSkin(attachment, skin)) continue;
			String reason = getAttachmentReferenceBlockReason(attachment, skin);
			if (reason != null) return reason;
		}
		return null;
	}

	/** 为指定槽（默认皮肤中）设置/替换区域附件，或创建新的区域附件。 */
	public com.esotericsoftware.spine.attachments.RegionAttachment setSlotRegionAttachment(com.esotericsoftware.spine.SlotData slot, String placeholder,
		String path) {
		return setSlotRegionAttachment(doc.data == null ? null : doc.data.getDefaultSkin(), slot, placeholder, path);
	}

	/** 在指定皮肤中创建/替换区域附件，并把它设为槽的 setup attachment。 */
	public com.esotericsoftware.spine.attachments.RegionAttachment setSlotRegionAttachment(
		com.esotericsoftware.spine.Skin skin, com.esotericsoftware.spine.SlotData slot, String placeholder, String path) {
		if (doc.data == null || skin == null || slot == null || placeholder == null || placeholder.isBlank()
			|| !doc.data.getSkins().contains(skin, true) || !doc.data.getSlots().contains(slot, true)) return null;
		com.esotericsoftware.spine.attachments.Attachment current = skin.getAttachment(slot.getIndex(), placeholder);
		if (getRegionReplacementBlockReason(current) != null) return null;
		com.esotericsoftware.spine.attachments.RegionAttachment existing = current instanceof com.esotericsoftware.spine.attachments.RegionAttachment
			? (com.esotericsoftware.spine.attachments.RegionAttachment) current : null;
		String resolvedPath = path == null || path.isBlank() ? placeholder : path;
		com.badlogic.gdx.graphics.g2d.TextureRegion region = findRegion(resolvedPath);
		if (region == null) return null;
		if (existing != null && java.util.Objects.equals(existing.getPath(), resolvedPath)
			&& existing.getSequence().getRegions()[0] == region
			&& java.util.Objects.equals(slot.getAttachmentName(), placeholder)) return existing;
		com.esotericsoftware.spine.attachments.RegionAttachment att;
		if (existing != null) {
			att = existing;
			att.setPath(resolvedPath);
			if (region != null) {
				att.getSequence().getRegions()[0] = region;
				att.updateSequence();
			}
		} else {
			com.esotericsoftware.spine.attachments.Sequence sequence =
				new com.esotericsoftware.spine.attachments.Sequence(1, false);
			att = new com.esotericsoftware.spine.attachments.RegionAttachment(placeholder, sequence);
			// 尝试从图集找纹理尺寸
			sequence.getRegions()[0] = region;
			att.setWidth(region.getRegionWidth());
			att.setHeight(region.getRegionHeight());
			att.setPath(resolvedPath);
			att.updateSequence();
		}
		skin.setAttachment(slot.getIndex(), placeholder, att);
		slot.setAttachmentName(placeholder);
		doc.markDirty();
		rebuildSkeleton();
		return att;
	}

	/** Returns null when the region action can create or update the target without discarding advanced attachment data. */
	public String getRegionReplacementBlockReason(Skin skin, SlotData slot, String placeholder) {
		if (doc.data == null || skin == null || slot == null || placeholder == null
			|| !doc.data.getSkins().contains(skin, true) || !doc.data.getSlots().contains(slot, true))
			return "附件目标无效";
		return getRegionReplacementBlockReason(skin.getAttachment(slot.getIndex(), placeholder));
	}

	private static String getRegionReplacementBlockReason(Attachment current) {
		if (current == null) return null;
		if (!(current instanceof RegionAttachment region))
			return "当前占位是 " + current.getClass().getSimpleName() + "，请先确认删除后再创建 Region";
		if (region.getSequence() == null) return "当前 Region 的图片序列数据无效";
		if (region.getSequence().hasPathSuffix() || region.getSequence().getRegions().length != 1)
			return "当前 Region 使用多帧 Sequence，不能用单图区域直接替换";
		return null;
	}

	protected com.badlogic.gdx.graphics.g2d.TextureRegion findRegion(String name) {
		if (doc.atlas != null) return doc.atlas.findRegion(name);
		return null;
	}

	/** Returns null when every image attachment can be resolved completely from the replacement atlas. */
	public String getAtlasReplacementBlockReason(TextureAtlas atlas) {
		try {
			collectAtlasRegionBindings(atlas);
			return null;
		} catch (IllegalArgumentException error) {
			return error.getMessage();
		}
	}

	/** Atomically rebinds all Region and Mesh sequence frames to regions from the supplied atlas. */
	public boolean rebindAtlasRegions(TextureAtlas atlas) {
		List<AtlasRegionBinding> bindings;
		try {
			bindings = collectAtlasRegionBindings(atlas);
		} catch (IllegalArgumentException error) {
			return false;
		}
		return applyAtlasRegionBindings(bindings, true);
	}

	/** Captures the exact TextureRegion identities so an atlas adoption can be rolled back. */
	public AtlasRegionState captureAtlasRegionState() {
		if (doc.data == null) throw new IllegalStateException("当前骨架数据无效");
		IdentityHashMap<HasSequence, Boolean> seen = new IdentityHashMap<>();
		List<AtlasRegionBinding> bindings = new ArrayList<>();
		for (Skin skin : doc.data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (!(entry.getAttachment() instanceof HasSequence attachment)
					|| seen.put(attachment, Boolean.TRUE) != null) continue;
				Sequence sequence = attachment.getSequence();
				if (sequence == null || sequence.getRegions() == null || sequence.getRegions().length == 0)
					throw new IllegalStateException("附件 " + entry.getPlaceholder() + " 的图片序列数据无效");
				TextureRegion[] regions = sequence.getRegions().clone();
				bindings.add(new AtlasRegionBinding(attachment, regions, regions));
			}
		}
		return new AtlasRegionState(bindings);
	}

	public boolean restoreAtlasRegions(AtlasRegionState state) {
		return state != null && applyAtlasRegionBindings(state.bindings, false);
	}

	private boolean applyAtlasRegionBindings(List<AtlasRegionBinding> bindings, boolean replacements) {
		List<AtlasRegionBinding> rollback = new ArrayList<>(bindings.size());
		for (AtlasRegionBinding binding : bindings) {
			Sequence sequence = binding.attachment().getSequence();
			TextureRegion[] source = replacements ? binding.replacement() : binding.previous();
			if (sequence == null || sequence.getRegions() == null
				|| sequence.getRegions().length != source.length) return false;
			TextureRegion[] current = sequence.getRegions().clone();
			rollback.add(new AtlasRegionBinding(binding.attachment(), current, current));
		}
		try {
			for (AtlasRegionBinding binding : bindings) {
				TextureRegion[] regions = binding.attachment().getSequence().getRegions();
				TextureRegion[] source = replacements ? binding.replacement() : binding.previous();
				System.arraycopy(source, 0, regions, 0, regions.length);
				binding.attachment().updateSequence();
			}
			return true;
		} catch (RuntimeException failure) {
			for (AtlasRegionBinding binding : rollback) {
				TextureRegion[] regions = binding.attachment().getSequence().getRegions();
				System.arraycopy(binding.previous(), 0, regions, 0, regions.length);
				try {
					binding.attachment().updateSequence();
				} catch (RuntimeException rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
				}
			}
			return false;
		}
	}

	private List<AtlasRegionBinding> collectAtlasRegionBindings(TextureAtlas atlas) {
		if (atlas == null) throw new IllegalArgumentException("替换图集不能为空");
		if (doc.data == null) throw new IllegalArgumentException("当前骨架数据无效");
		IdentityHashMap<HasSequence, Boolean> seen = new IdentityHashMap<>();
		List<AtlasRegionBinding> bindings = new ArrayList<>();
		for (Skin skin : doc.data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (!(entry.getAttachment() instanceof HasSequence attachment)
					|| seen.put(attachment, Boolean.TRUE) != null) continue;
				Sequence sequence = attachment.getSequence();
				String path = attachment.getPath();
				if (sequence == null || sequence.getRegions() == null || sequence.getRegions().length == 0)
					throw new IllegalArgumentException("附件 " + entry.getPlaceholder() + " 的图片序列数据无效");
				if (path == null || path.isBlank())
					throw new IllegalArgumentException("附件 " + entry.getPlaceholder() + " 缺少图集区域路径");
				TextureRegion[] replacement = new TextureRegion[sequence.getRegions().length];
				for (int index = 0; index < replacement.length; index++) {
					String regionName = sequence.getPath(path, index);
					replacement[index] = atlas.findRegion(regionName);
					if (replacement[index] == null)
						throw new IllegalArgumentException("替换图集缺少附件 " + entry.getPlaceholder()
							+ " 所需区域：" + regionName);
				}
				bindings.add(new AtlasRegionBinding(attachment, sequence.getRegions().clone(), replacement));
			}
		}
		return bindings;
	}

	/** Returns a detached snapshot suitable for validation, editing, and undo history. */
	public static MeshGeometryState meshGeometryState(MeshAttachment mesh) {
		if (mesh == null) throw new IllegalArgumentException("Mesh 附件不能为空");
		return new MeshGeometryState(mesh.getColor(), mesh.getPath(), mesh.getBones(), mesh.getVertices(),
			mesh.getRegionUVs(), mesh.getTriangles(), mesh.getHullLength(), mesh.getEdges(),
			mesh.getWidth(), mesh.getHeight());
	}

	/** Stable editor-state identity for an owned Mesh attachment. */
	public String meshWeightStateKey(MeshAttachment mesh, SlotData slot) {
		if (doc.data == null || mesh == null || slot == null) return "";
		String selected = doc.getSelectedAttachmentPlaceholder();
		if (selected != null && !selected.isBlank())
			for (Skin skin : doc.data.getSkins())
				if (skin.getAttachment(slot.getIndex(), selected) == mesh)
					return SkeletonDocument.meshWeightStateKey(skin.getName(), slot.getName(), selected);
		for (Skin skin : doc.data.getSkins())
			for (Skin.SkinEntry entry : skin.getAttachments())
				if (entry.getSlotIndex() == slot.getIndex() && entry.getAttachment() == mesh)
					return SkeletonDocument.meshWeightStateKey(skin.getName(), slot.getName(), entry.getPlaceholder());
		return SkeletonDocument.meshWeightStateKey("", slot.getName(), mesh.getName());
	}

	/** Captures each setup vertex in the selected target bone's local space for stable weight painting. */
	public MeshWeightPaintContext prepareMeshWeightPaint(MeshAttachment mesh, SlotData slotData,
		BoneData targetBone) {
		if (doc.data == null || mesh == null || slotData == null || targetBone == null
			|| !containsAttachment(mesh) || mesh.getSourceMesh() != null
			|| !doc.data.getSlots().contains(slotData, true) || !doc.data.getBones().contains(targetBone, true))
			throw new IllegalArgumentException("Mesh 权重笔刷目标无效");
		MeshGeometryState state = meshGeometryState(mesh);
		String reason = validateMeshGeometry(mesh, state);
		if (reason != null) throw new IllegalArgumentException(reason);

		Skeleton setupSkeleton = new Skeleton(doc.data);
		Skin owner = findAttachmentSkin(mesh, slotData.getIndex());
		if (owner == null && doc.skeleton != null) owner = doc.skeleton.getSkin();
		if (owner == null) owner = doc.data.getDefaultSkin();
		if (owner != null) {
			setupSkeleton.setSkin(owner);
			setupSkeleton.setupPoseSlots();
		}
		setupSkeleton.updateWorldTransform(Physics.pose);
		Slot setupSlot = setupSkeleton.getSlots().get(slotData.getIndex());
		int valueCount = mesh.getWorldVerticesLength();
		float[] world = new float[valueCount];
		if (mesh.getBones() != null) {
			mesh.computeWorldVertices(setupSkeleton, setupSlot, 0, valueCount, world, 0, 2);
		} else {
			float[] vertices = mesh.getVertices();
			BonePose slotPose = setupSlot.getBone().getAppliedPose();
			for (int value = 0; value < valueCount; value += 2) {
				float x = vertices[value], y = vertices[value + 1];
				world[value] = x * slotPose.getA() + y * slotPose.getB() + slotPose.getWorldX();
				world[value + 1] = x * slotPose.getC() + y * slotPose.getD() + slotPose.getWorldY();
			}
		}

		BonePose targetPose = setupSkeleton.getBones().get(targetBone.getIndex()).getAppliedPose();
		float[] targetLocal = new float[valueCount];
		Vector2 point = new Vector2();
		for (int value = 0; value < valueCount; value += 2) {
			point.set(world[value], world[value + 1]);
			targetPose.worldToLocal(point);
			if (!Float.isFinite(point.x) || !Float.isFinite(point.y))
				throw new IllegalArgumentException("目标骨骼的 Setup 变换不可逆");
			targetLocal[value] = point.x;
			targetLocal[value + 1] = point.y;
		}
		return new MeshWeightPaintContext(slotData.getBoneData().getIndex(), targetBone.getIndex(), targetLocal);
	}

	/**
	 * Applies signed per-vertex brush amounts. Positive values move the target weight toward one,
	 * negative values move it toward zero, and every edited vertex remains normalized.
	 */
	public static MeshGeometryState paintMeshWeights(MeshGeometryState state,
		MeshWeightPaintContext context, float[] amounts) {
		return paintMeshWeights(state, context, amounts, Set.of());
	}

	public static MeshGeometryState paintMeshWeights(MeshGeometryState state,
		MeshWeightPaintContext context, float[] amounts, Set<SkeletonDocument.MeshWeightLock> locks) {
		if (state == null || context == null || amounts == null || state.regionUVs == null
			|| state.vertices == null || state.vertexCount() != context.vertexCount()
			|| amounts.length != context.vertexCount())
			throw new IllegalArgumentException("Mesh 权重笔刷数据不完整");
		for (float amount : amounts)
			if (!Float.isFinite(amount)) throw new IllegalArgumentException("Mesh 权重笔刷强度必须是有限数值");

		ArrayList<ArrayList<MeshWeightInfluence>> decoded = decodeMeshWeights(state, context.slotBoneIndex);
		boolean wasWeighted = state.bones != null;
		boolean changed = false;
		for (int vertex = 0; vertex < decoded.size(); vertex++) {
			float amount = Math.max(-1f, Math.min(1f, amounts[vertex]));
			if (amount == 0f) continue;
			ArrayList<MeshWeightInfluence> influences = decoded.get(vertex);
			float lockedSum = 0f, unlockedSum = 0f;
			int unlockedCount = 0;
			int target = -1;
			for (int index = 0; index < influences.size(); index++) {
				MeshWeightInfluence influence = influences.get(index);
				if (!Float.isFinite(influence.weight) || influence.weight < 0f)
					throw new IllegalArgumentException("Mesh 权重数据无效");
				if (meshWeightLocked(locks, vertex, influence.boneIndex)) lockedSum += influence.weight;
				else { unlockedSum += influence.weight; unlockedCount++; }
				if (influence.boneIndex == context.targetBoneIndex) target = index;
			}
			if (!Float.isFinite(lockedSum) || lockedSum > 1.000001f)
				throw new IllegalArgumentException("Mesh 锁定权重总和不能超过 1");
			float available = Math.max(0f, 1f - lockedSum);
			if (unlockedCount == 0 && Math.abs(lockedSum - 1f) > 0.000001f)
				throw new IllegalArgumentException("Mesh 全部权重锁定时总和必须等于 1");
			for (int index = 0; index < influences.size(); index++) {
				MeshWeightInfluence influence = influences.get(index);
				if (meshWeightLocked(locks, vertex, influence.boneIndex)) continue;
				float normalized = unlockedSum > 0f ? influence.weight / unlockedSum * available
					: available / Math.max(1, unlockedCount);
				influences.set(index, new MeshWeightInfluence(influence.boneIndex, influence.x, influence.y,
					normalized));
			}

			if (target >= 0 && meshWeightLocked(locks, vertex, context.targetBoneIndex)) continue;
			if (target < 0) {
				if (amount < 0f) continue;
				int value = vertex * 2;
				influences.add(new MeshWeightInfluence(context.targetBoneIndex,
					context.targetLocalVertices[value], context.targetLocalVertices[value + 1], 0f));
				target = influences.size() - 1;
			}
			MeshWeightInfluence targetInfluence = influences.get(target);
			float oldWeight = targetInfluence.weight;
			float newWeight = amount > 0f ? oldWeight + amount * (available - oldWeight)
				: oldWeight * (1f + amount);
			newWeight = Math.max(0f, Math.min(available, newWeight));
			int otherUnlockedCount = 0;
			for (MeshWeightInfluence influence : influences)
				if (influence.boneIndex != context.targetBoneIndex
					&& !meshWeightLocked(locks, vertex, influence.boneIndex)) otherUnlockedCount++;
			if (otherUnlockedCount == 0) newWeight = available;
			if (Float.compare(oldWeight, newWeight) == 0) continue;

			float oldOtherWeight = available - oldWeight;
			float newOtherWeight = available - newWeight;
			if (oldOtherWeight > 0.000001f) {
				float scale = newOtherWeight / oldOtherWeight;
				for (int index = 0; index < influences.size(); index++) {
					if (index == target) continue;
					MeshWeightInfluence influence = influences.get(index);
					if (meshWeightLocked(locks, vertex, influence.boneIndex)) continue;
					influences.set(index, new MeshWeightInfluence(influence.boneIndex, influence.x, influence.y,
						influence.weight * scale));
				}
			} else if (newOtherWeight > 0f && otherUnlockedCount > 0) {
				float share = newOtherWeight / otherUnlockedCount;
				for (int index = 0; index < influences.size(); index++) {
					if (index == target) continue;
					MeshWeightInfluence influence = influences.get(index);
					if (meshWeightLocked(locks, vertex, influence.boneIndex)) continue;
					influences.set(index, new MeshWeightInfluence(influence.boneIndex, influence.x, influence.y, share));
				}
			}
			influences.set(target, new MeshWeightInfluence(targetInfluence.boneIndex,
				targetInfluence.x, targetInfluence.y, newWeight));
			changed = true;
		}
		if (!changed) return state;

		int boneLength = 0, vertexLength = 0;
		for (ArrayList<MeshWeightInfluence> influences : decoded) {
			boneLength += influences.size() + 1;
			vertexLength += influences.size() * 3;
		}
		int[] bones = new int[boneLength];
		float[] vertices = new float[vertexLength];
		int boneCursor = 0, valueCursor = 0;
		for (ArrayList<MeshWeightInfluence> influences : decoded) {
			bones[boneCursor++] = influences.size();
			for (MeshWeightInfluence influence : influences) {
				bones[boneCursor++] = influence.boneIndex;
				vertices[valueCursor++] = influence.x;
				vertices[valueCursor++] = influence.y;
				vertices[valueCursor++] = influence.weight;
			}
		}
		if (!wasWeighted && context.slotBoneIndex == context.targetBoneIndex) return state;
		return new MeshGeometryState(state.color, state.path, bones, vertices, state.regionUVs,
			state.triangles, state.hullLength, state.edges, state.width, state.height);
	}

	/**
	 * Smooths existing bone weights across triangle adjacency without adding or removing influences.
	 * Local coordinates and compact encoding structure remain unchanged, so Deform vertex layouts stay compatible.
	 */
	public static MeshGeometryState smoothMeshWeights(MeshGeometryState state, float strength, int iterations) {
		return smoothMeshWeights(state, strength, iterations, Set.of());
	}

	public static MeshGeometryState smoothMeshWeights(MeshGeometryState state, float strength, int iterations,
		Set<SkeletonDocument.MeshWeightLock> locks) {
		if (state == null || state.bones == null || state.vertices == null || state.regionUVs == null)
			throw new IllegalArgumentException("Mesh 尚未绑定权重");
		if (!Float.isFinite(strength) || strength < 0f || strength > 1f)
			throw new IllegalArgumentException("Mesh 权重平滑强度必须在 0 到 1 之间");
		if (iterations < 1 || iterations > 100)
			throw new IllegalArgumentException("Mesh 权重平滑次数必须在 1 到 100 之间");
		if (strength == 0f) return state;
		int vertexCount = state.vertexCount();
		if (vertexCount == 0 || state.triangles == null || state.triangles.length == 0
			|| state.triangles.length % 3 != 0)
			throw new IllegalArgumentException("Mesh 权重平滑需要有效三角形拓扑");

		ArrayList<Set<Integer>> adjacency = new ArrayList<>(vertexCount);
		for (int vertex = 0; vertex < vertexCount; vertex++) adjacency.add(new LinkedHashSet<>());
		for (int triangle = 0; triangle < state.triangles.length; triangle += 3) {
			int a = Short.toUnsignedInt(state.triangles[triangle]);
			int b = Short.toUnsignedInt(state.triangles[triangle + 1]);
			int c = Short.toUnsignedInt(state.triangles[triangle + 2]);
			if (a >= vertexCount || b >= vertexCount || c >= vertexCount || a == b || b == c || c == a)
				throw new IllegalArgumentException("Mesh 权重平滑拓扑包含无效三角形");
			connectMeshVertices(adjacency, a, b);
			connectMeshVertices(adjacency, b, c);
			connectMeshVertices(adjacency, c, a);
		}

		ArrayList<ArrayList<MeshWeightInfluence>> current = decodeMeshWeights(state, 0);
		for (int iteration = 0; iteration < iterations; iteration++) {
			ArrayList<ArrayList<MeshWeightInfluence>> next = new ArrayList<>(vertexCount);
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				ArrayList<MeshWeightInfluence> source = normalizeMeshInfluences(current.get(vertex));
				Set<Integer> neighbors = adjacency.get(vertex);
				if (source.size() == 1 || neighbors.isEmpty()) {
					next.add(source);
					continue;
				}
				ArrayList<MeshWeightInfluence> smoothed = new ArrayList<>(source.size());
				float lockedSum = 0f, unlockedSum = 0f;
				for (MeshWeightInfluence influence : source) {
					if (meshWeightLocked(locks, vertex, influence.boneIndex)) {
						smoothed.add(influence);
						lockedSum += influence.weight;
						continue;
					}
					float neighborWeight = 0f;
					for (int neighbor : neighbors)
						neighborWeight += normalizedMeshWeight(current.get(neighbor), influence.boneIndex);
					neighborWeight /= neighbors.size();
					float weight = influence.weight + (neighborWeight - influence.weight) * strength;
					if (!Float.isFinite(weight) || weight < 0f)
						throw new IllegalArgumentException("Mesh 权重平滑结果无效");
					smoothed.add(new MeshWeightInfluence(influence.boneIndex, influence.x, influence.y, weight));
					unlockedSum += weight;
				}
				if (!Float.isFinite(lockedSum) || lockedSum > 1.000001f) {
					throw new IllegalArgumentException("Mesh 锁定权重总和不能超过 1");
				}
				float available = Math.max(0f, 1f - lockedSum);
				if (!(unlockedSum > 0.000001f) || !Float.isFinite(unlockedSum)) {
					next.add(source);
					continue;
				}
				for (int influenceIndex = 0; influenceIndex < smoothed.size(); influenceIndex++) {
					MeshWeightInfluence influence = smoothed.get(influenceIndex);
					if (meshWeightLocked(locks, vertex, influence.boneIndex)) continue;
					smoothed.set(influenceIndex, new MeshWeightInfluence(influence.boneIndex,
						influence.x, influence.y, influence.weight / unlockedSum * available));
				}
				next.add(smoothed);
			}
			current = next;
		}

		MeshWeightEncoding encoding = encodeMeshWeights(current);
		if (Arrays.equals(state.bones, encoding.bones) && Arrays.equals(state.vertices, encoding.vertices)) return state;
		return new MeshGeometryState(state.color, state.path, encoding.bones, encoding.vertices, state.regionUVs,
			state.triangles, state.hullLength, state.edges, state.width, state.height);
	}

	private static boolean meshWeightLocked(Set<SkeletonDocument.MeshWeightLock> locks,
		int vertex, int boneIndex) {
		return locks != null && locks.contains(new SkeletonDocument.MeshWeightLock(vertex, boneIndex));
	}

	/** Builds a complete permutation with the requested hull vertices first and all remaining vertices stable. */
	public static MeshVertexOrder meshHullVertexOrder(int vertexCount, int[] hullVertices) {
		if (vertexCount < 3 || vertexCount > 65536 || hullVertices == null
			|| hullVertices.length < 3 || hullVertices.length > vertexCount)
			throw new IllegalArgumentException("Mesh 外壳必须包含 3 个到全部顶点");
		boolean[] used = new boolean[vertexCount];
		int[] newToOld = new int[vertexCount];
		int cursor = 0;
		for (int vertex : hullVertices) {
			if (vertex < 0 || vertex >= vertexCount)
				throw new IllegalArgumentException("Mesh 外壳引用了不存在的顶点：" + vertex);
			if (used[vertex]) throw new IllegalArgumentException("Mesh 外壳不能重复引用顶点：" + vertex);
			used[vertex] = true;
			newToOld[cursor++] = vertex;
		}
		for (int vertex = 0; vertex < vertexCount; vertex++)
			if (!used[vertex]) newToOld[cursor++] = vertex;
		int[] oldToNew = new int[vertexCount];
		for (int newIndex = 0; newIndex < vertexCount; newIndex++) oldToNew[newToOld[newIndex]] = newIndex;
		return new MeshVertexOrder(newToOld, oldToNew, hullVertices.length);
	}

	/** Returns the counterclockwise convex hull of finite XY pairs, excluding duplicate and collinear interior points. */
	public static int[] convexMeshHull(float[] vertices) {
		if (vertices == null || vertices.length < 6 || (vertices.length & 1) != 0 || !areFinite(vertices))
			throw new IllegalArgumentException("Mesh 自动外壳需要至少 3 个有效顶点");
		int vertexCount = vertices.length / 2;
		Integer[] sorted = new Integer[vertexCount];
		for (int vertex = 0; vertex < vertexCount; vertex++) sorted[vertex] = vertex;
		Arrays.sort(sorted, (first, second) -> {
			int compareX = Float.compare(vertices[first * 2], vertices[second * 2]);
			if (compareX != 0) return compareX;
			int compareY = Float.compare(vertices[first * 2 + 1], vertices[second * 2 + 1]);
			return compareY != 0 ? compareY : Integer.compare(first, second);
		});
		int[] unique = new int[vertexCount];
		int uniqueCount = 0;
		for (int vertex : sorted) {
			if (uniqueCount > 0) {
				int previous = unique[uniqueCount - 1];
				if (Float.compare(vertices[previous * 2], vertices[vertex * 2]) == 0
					&& Float.compare(vertices[previous * 2 + 1], vertices[vertex * 2 + 1]) == 0) continue;
			}
			unique[uniqueCount++] = vertex;
		}
		if (uniqueCount < 3) throw new IllegalArgumentException("Mesh 自动外壳至少需要 3 个不同位置的顶点");

		int[] hull = new int[uniqueCount * 2];
		int count = 0;
		for (int index = 0; index < uniqueCount; index++) {
			int vertex = unique[index];
			while (count >= 2 && meshCross(vertices, hull[count - 2], hull[count - 1], vertex) <= 0d) count--;
			hull[count++] = vertex;
		}
		int lowerCount = count;
		for (int index = uniqueCount - 2; index >= 0; index--) {
			int vertex = unique[index];
			while (count > lowerCount && meshCross(vertices, hull[count - 2], hull[count - 1], vertex) <= 0d) count--;
			hull[count++] = vertex;
		}
		count--;
		if (count < 3) throw new IllegalArgumentException("Mesh 顶点共线，无法自动生成外壳");
		return Arrays.copyOf(hull, count);
	}

	private static double meshCross(float[] vertices, int origin, int first, int second) {
		double ax = vertices[first * 2] - vertices[origin * 2];
		double ay = vertices[first * 2 + 1] - vertices[origin * 2 + 1];
		double bx = vertices[second * 2] - vertices[origin * 2];
		double by = vertices[second * 2 + 1] - vertices[origin * 2 + 1];
		return ax * by - ay * bx;
	}

	/** Reorders every per-vertex array and remaps topology indices without mutating the source snapshot. */
	public static MeshGeometryState reorderMeshVertices(MeshGeometryState state, MeshVertexOrder order) {
		if (state == null || order == null || state.regionUVs == null || state.vertices == null
			|| state.triangles == null || state.triangles.length == 0
			|| state.vertexCount() != order.vertexCount())
			throw new IllegalArgumentException("Mesh 顶点重排数据不完整");
		int vertexCount = state.vertexCount();
		float[] uvs = reorderVertexPairs(state.regionUVs, order.newToOld, vertexCount);
		int[] bones = null;
		float[] vertices;
		if (state.bones == null) {
			vertices = reorderVertexPairs(state.vertices, order.newToOld, vertexCount);
		} else {
			ArrayList<ArrayList<MeshWeightInfluence>> decoded = decodeMeshWeights(state, 0);
			ArrayList<ArrayList<MeshWeightInfluence>> reordered = new ArrayList<>(vertexCount);
			for (int newIndex = 0; newIndex < vertexCount; newIndex++)
				reordered.add(decoded.get(order.newToOld[newIndex]));
			MeshWeightEncoding encoding = encodeMeshWeights(reordered);
			bones = encoding.bones;
			vertices = encoding.vertices;
		}
		short[] triangles = remapMeshIndices(state.triangles, order.oldToNew, vertexCount, 3,
			"Mesh 三角形索引无效");
		short[] edges = state.edges == null ? null : remapMeshIndices(state.edges, order.oldToNew,
			vertexCount, 2, "Mesh 边索引无效");
		return new MeshGeometryState(state.color, state.path, bones, vertices, uvs, triangles,
			order.hullVertexCount * 2, edges, state.width, state.height);
	}

	private static float[] reorderVertexPairs(float[] values, int[] newToOld, int vertexCount) {
		if (values == null || values.length != vertexCount * 2)
			throw new IllegalArgumentException("Mesh 逐顶点数据长度不完整");
		float[] result = new float[values.length];
		for (int newIndex = 0; newIndex < vertexCount; newIndex++) {
			int source = newToOld[newIndex] * 2;
			result[newIndex * 2] = values[source];
			result[newIndex * 2 + 1] = values[source + 1];
		}
		return result;
	}

	private static short[] remapMeshIndices(short[] indices, int[] oldToNew, int vertexCount,
		int groupSize, String error) {
		if (indices == null || indices.length % groupSize != 0)
			throw new IllegalArgumentException(error);
		short[] result = new short[indices.length];
		for (int index = 0; index < indices.length; index++) {
			int oldIndex = Short.toUnsignedInt(indices[index]);
			if (oldIndex >= vertexCount) throw new IllegalArgumentException(error);
			result[index] = (short)oldToNew[oldIndex];
		}
		return result;
	}

	private static void connectMeshVertices(ArrayList<Set<Integer>> adjacency, int first, int second) {
		adjacency.get(first).add(second);
		adjacency.get(second).add(first);
	}

	private static ArrayList<MeshWeightInfluence> normalizeMeshInfluences(
		List<MeshWeightInfluence> influences) {
		float sum = 0f;
		for (MeshWeightInfluence influence : influences) {
			if (!Float.isFinite(influence.weight) || influence.weight < 0f)
				throw new IllegalArgumentException("Mesh 权重数据无效");
			sum += influence.weight;
		}
		if (!(sum > 0f) || !Float.isFinite(sum))
			throw new IllegalArgumentException("Mesh 顶点权重总和无效");
		ArrayList<MeshWeightInfluence> normalized = new ArrayList<>(influences.size());
		for (MeshWeightInfluence influence : influences)
			normalized.add(new MeshWeightInfluence(influence.boneIndex, influence.x, influence.y,
				influence.weight / sum));
		return normalized;
	}

	private static float normalizedMeshWeight(List<MeshWeightInfluence> influences, int boneIndex) {
		float sum = 0f, selected = 0f;
		for (MeshWeightInfluence influence : influences) {
			if (!Float.isFinite(influence.weight) || influence.weight < 0f)
				throw new IllegalArgumentException("Mesh 权重数据无效");
			sum += influence.weight;
			if (influence.boneIndex == boneIndex) selected = influence.weight;
		}
		if (!(sum > 0f) || !Float.isFinite(sum))
			throw new IllegalArgumentException("Mesh 顶点权重总和无效");
		return selected / sum;
	}

	/** Appends one logical vertex and replaces the hit triangle with three triangles of identical winding. */
	public static MeshGeometryState insertMeshVertex(MeshGeometryState state, int triangleOffset,
		float weightA, float weightB, float weightC) {
		if (state == null || state.regionUVs == null || state.vertices == null || state.triangles == null
			|| triangleOffset < 0 || triangleOffset % 3 != 0 || triangleOffset + 2 >= state.triangles.length
			|| !areFinite(weightA, weightB, weightC) || weightA < -0.0001f || weightB < -0.0001f
			|| weightC < -0.0001f)
			throw new IllegalArgumentException("Mesh 插点数据无效");
		int vertexCount = state.vertexCount();
		if (vertexCount < 3 || vertexCount >= 65536)
			throw new IllegalArgumentException("Mesh 顶点数量无法继续增加");
		int a = Short.toUnsignedInt(state.triangles[triangleOffset]);
		int b = Short.toUnsignedInt(state.triangles[triangleOffset + 1]);
		int c = Short.toUnsignedInt(state.triangles[triangleOffset + 2]);
		if (a >= vertexCount || b >= vertexCount || c >= vertexCount || a == b || b == c || c == a)
			throw new IllegalArgumentException("Mesh 插点三角形无效");
		weightA = Math.max(0f, weightA);
		weightB = Math.max(0f, weightB);
		weightC = Math.max(0f, weightC);
		float sum = weightA + weightB + weightC;
		if (!(sum > 0f) || !Float.isFinite(sum)) throw new IllegalArgumentException("Mesh 插点重心坐标无效");
		weightA /= sum;
		weightB /= sum;
		weightC /= sum;
		if (weightA > 1.0001f || weightB > 1.0001f || weightC > 1.0001f)
			throw new IllegalArgumentException("Mesh 插点必须位于三角形内部");

		float[] uvs = Arrays.copyOf(state.regionUVs, state.regionUVs.length + 2);
		uvs[state.regionUVs.length] = barycentricValue(state.regionUVs, a, b, c, weightA, weightB, weightC, 0);
		uvs[state.regionUVs.length + 1] = barycentricValue(state.regionUVs, a, b, c,
			weightA, weightB, weightC, 1);
		int[] bones = null;
		float[] vertices;
		if (state.bones == null) {
			vertices = Arrays.copyOf(state.vertices, state.vertices.length + 2);
			vertices[state.vertices.length] = barycentricValue(state.vertices, a, b, c,
				weightA, weightB, weightC, 0);
			vertices[state.vertices.length + 1] = barycentricValue(state.vertices, a, b, c,
				weightA, weightB, weightC, 1);
		} else {
			ArrayList<ArrayList<MeshWeightInfluence>> decoded = decodeMeshWeights(state, 0);
			LinkedHashMap<Integer, MeshWeightAccumulator> accumulators = new LinkedHashMap<>();
			int[] triangleVertices = {a, b, c};
			float[] triangleWeights = {weightA, weightB, weightC};
			for (int corner = 0; corner < 3; corner++) {
				ArrayList<MeshWeightInfluence> influences = decoded.get(triangleVertices[corner]);
				float influenceSum = 0f;
				for (MeshWeightInfluence influence : influences) influenceSum += influence.weight;
				if (!(influenceSum > 0f) || !Float.isFinite(influenceSum))
					throw new IllegalArgumentException("Mesh 三角形包含无效权重");
				for (MeshWeightInfluence influence : influences) {
					float contribution = triangleWeights[corner] * influence.weight / influenceSum;
					if (!(contribution > 0f)) continue;
					MeshWeightAccumulator accumulator = accumulators.computeIfAbsent(influence.boneIndex,
						ignored -> new MeshWeightAccumulator());
					accumulator.weight += contribution;
					accumulator.weightedX += influence.x * contribution;
					accumulator.weightedY += influence.y * contribution;
				}
			}
			ArrayList<MeshWeightInfluence> inserted = new ArrayList<>(accumulators.size());
			for (Map.Entry<Integer, MeshWeightAccumulator> entry : accumulators.entrySet()) {
				MeshWeightAccumulator accumulator = entry.getValue();
				if (!(accumulator.weight > 0.000001f)) continue;
				inserted.add(new MeshWeightInfluence(entry.getKey(),
					accumulator.weightedX / accumulator.weight,
					accumulator.weightedY / accumulator.weight, accumulator.weight));
			}
			if (inserted.isEmpty()) throw new IllegalArgumentException("Mesh 插点未生成有效权重");
			decoded.add(inserted);
			MeshWeightEncoding encoding = encodeMeshWeights(decoded);
			bones = encoding.bones;
			vertices = encoding.vertices;
		}

		short insertedIndex = (short)vertexCount;
		short[] triangles = new short[state.triangles.length + 6];
		System.arraycopy(state.triangles, 0, triangles, 0, triangleOffset);
		int output = triangleOffset;
		triangles[output++] = (short)a; triangles[output++] = (short)b; triangles[output++] = insertedIndex;
		triangles[output++] = (short)b; triangles[output++] = (short)c; triangles[output++] = insertedIndex;
		triangles[output++] = (short)c; triangles[output++] = (short)a; triangles[output++] = insertedIndex;
		System.arraycopy(state.triangles, triangleOffset + 3, triangles, output,
			state.triangles.length - triangleOffset - 3);
		short[] edges = appendMeshEdges(state.edges, a, b, c, vertexCount);
		return new MeshGeometryState(state.color, state.path, bones, vertices, uvs, triangles,
			state.hullLength, edges, state.width, state.height);
	}

	/** Removes logical vertices and every incident triangle/edge, then compacts all Spine indices. */
	public static MeshGeometryState removeMeshVertices(MeshGeometryState state, int[] removedVertices) {
		if (state == null || state.regionUVs == null || state.vertices == null || state.triangles == null
			|| removedVertices == null) throw new IllegalArgumentException("Mesh 删点数据无效");
		int vertexCount = state.vertexCount();
		boolean[] removed = new boolean[vertexCount];
		int removedCount = 0, removedHullCount = 0;
		int hullVertices = state.hullLength / 2;
		for (int vertex : removedVertices) {
			if (vertex < 0 || vertex >= vertexCount) throw new IllegalArgumentException("Mesh 删点索引无效");
			if (removed[vertex]) continue;
			removed[vertex] = true;
			removedCount++;
			if (vertex < hullVertices) removedHullCount++;
		}
		if (removedCount == 0) return state;
		if (vertexCount - removedCount < 3 || hullVertices - removedHullCount < 3)
			throw new IllegalArgumentException("Mesh 删除后至少需要 3 个顶点和 3 个外壳顶点");
		int[] remap = new int[vertexCount];
		int nextVertex = 0;
		for (int vertex = 0; vertex < vertexCount; vertex++) remap[vertex] = removed[vertex] ? -1 : nextVertex++;

		ArrayList<Short> keptTriangles = new ArrayList<>(state.triangles.length);
		for (int index = 0; index + 2 < state.triangles.length; index += 3) {
			int a = Short.toUnsignedInt(state.triangles[index]);
			int b = Short.toUnsignedInt(state.triangles[index + 1]);
			int c = Short.toUnsignedInt(state.triangles[index + 2]);
			if (a >= vertexCount || b >= vertexCount || c >= vertexCount)
				throw new IllegalArgumentException("Mesh 三角形索引无效");
			if (removed[a] || removed[b] || removed[c]) continue;
			keptTriangles.add((short)remap[a]);
			keptTriangles.add((short)remap[b]);
			keptTriangles.add((short)remap[c]);
		}
		if (keptTriangles.isEmpty()) throw new IllegalArgumentException("Mesh 删除后不能没有三角形");
		short[] triangles = toShortArray(keptTriangles);
		float[] uvs = removeVertexPairs(state.regionUVs, removed, removedCount);
		int[] bones = null;
		float[] vertices;
		if (state.bones == null) {
			vertices = removeVertexPairs(state.vertices, removed, removedCount);
		} else {
			ArrayList<ArrayList<MeshWeightInfluence>> decoded = decodeMeshWeights(state, 0);
			for (int vertex = vertexCount - 1; vertex >= 0; vertex--)
				if (removed[vertex]) decoded.remove(vertex);
			MeshWeightEncoding encoding = encodeMeshWeights(decoded);
			bones = encoding.bones;
			vertices = encoding.vertices;
		}
		short[] edges = removeAndRemapMeshEdges(state.edges, removed, remap, vertexCount);
		return new MeshGeometryState(state.color, state.path, bones, vertices, uvs, triangles,
			state.hullLength - removedHullCount * 2, edges, state.width, state.height);
	}

	/** Merges setup-space coincident vertices and remaps topology to the first matching vertex. */
	public static MeshWeldResult weldMeshVertices(MeshGeometryState state, float[] setupVertices,
		float tolerance) {
		if (state == null || setupVertices == null || setupVertices.length != state.vertexCount() * 2
			|| !areFinite(setupVertices) || !Float.isFinite(tolerance) || tolerance < 0f)
			throw new IllegalArgumentException("Mesh 焊接参数无效");
		int vertexCount = state.vertexCount();
		int[] oldToNew = new int[vertexCount];
		ArrayList<Integer> representatives = new ArrayList<>();
		float tolerance2 = tolerance * tolerance;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int mapped = -1;
			for (int candidate = 0; candidate < representatives.size(); candidate++) {
				int representative = representatives.get(candidate);
				float dx = setupVertices[vertex * 2] - setupVertices[representative * 2];
				float dy = setupVertices[vertex * 2 + 1] - setupVertices[representative * 2 + 1];
				if (dx * dx + dy * dy <= tolerance2) { mapped = candidate; break; }
			}
			if (mapped < 0) {
				mapped = representatives.size();
				representatives.add(vertex);
			}
			oldToNew[vertex] = mapped;
		}
		int[] newToOld = representatives.stream().mapToInt(Integer::intValue).toArray();
		int welded = vertexCount - newToOld.length;
		if (welded == 0) return new MeshWeldResult(state, oldToNew, newToOld, 0);
		int oldHull = state.hullLength / 2, newHull = 0;
		boolean[] countedHull = new boolean[newToOld.length];
		for (int vertex = 0; vertex < oldHull; vertex++)
			if (!countedHull[oldToNew[vertex]]) { countedHull[oldToNew[vertex]] = true; newHull++; }
		if (newToOld.length < 3 || newHull < 3) throw new IllegalArgumentException("焊接后至少需要 3 个顶点和 3 个外壳顶点");

		ArrayList<Short> triangles = new ArrayList<>();
		for (int index = 0; index + 2 < state.triangles.length; index += 3) {
			int a = oldToNew[Short.toUnsignedInt(state.triangles[index])];
			int b = oldToNew[Short.toUnsignedInt(state.triangles[index + 1])];
			int c = oldToNew[Short.toUnsignedInt(state.triangles[index + 2])];
			if (a == b || b == c || c == a) continue;
			boolean duplicate = false;
			for (int existing = 0; existing + 2 < triangles.size(); existing += 3)
				if (sameTriangle(a, b, c, Short.toUnsignedInt(triangles.get(existing)),
					Short.toUnsignedInt(triangles.get(existing + 1)),
					Short.toUnsignedInt(triangles.get(existing + 2)))) { duplicate = true; break; }
			if (!duplicate) { triangles.add((short)a); triangles.add((short)b); triangles.add((short)c); }
		}
		if (triangles.isEmpty()) throw new IllegalArgumentException("焊接会移除全部 Mesh 三角形");
		float[] uvs = new float[newToOld.length * 2];
		for (int vertex = 0; vertex < newToOld.length; vertex++)
			System.arraycopy(state.regionUVs, newToOld[vertex] * 2, uvs, vertex * 2, 2);
		int[] bones = null;
		float[] vertices;
		if (state.bones == null) {
			vertices = new float[newToOld.length * 2];
			for (int vertex = 0; vertex < newToOld.length; vertex++)
				System.arraycopy(state.vertices, newToOld[vertex] * 2, vertices, vertex * 2, 2);
		} else {
			ArrayList<ArrayList<MeshWeightInfluence>> source = decodeMeshWeights(state, 0);
			ArrayList<ArrayList<MeshWeightInfluence>> selected = new ArrayList<>(newToOld.length);
			for (int oldVertex : newToOld) selected.add(new ArrayList<>(source.get(oldVertex)));
			MeshWeightEncoding encoding = encodeMeshWeights(selected);
			bones = encoding.bones;
			vertices = encoding.vertices;
		}
		short[] edges = remapWeldEdges(state.edges, oldToNew, vertexCount);
		MeshGeometryState geometry = new MeshGeometryState(state.color, state.path, bones, vertices, uvs,
			toShortArray(triangles), newHull * 2, edges, state.width, state.height);
		return new MeshWeldResult(geometry, oldToNew, newToOld, welded);
	}

	private static short[] remapWeldEdges(short[] edges, int[] oldToNew, int vertexCount) {
		if (edges == null) return null;
		LinkedHashSet<Long> seen = new LinkedHashSet<>();
		ArrayList<Short> result = new ArrayList<>();
		for (int index = 0; index + 1 < edges.length; index += 2) {
			int oldA = Short.toUnsignedInt(edges[index]), oldB = Short.toUnsignedInt(edges[index + 1]);
			if (oldA >= vertexCount || oldB >= vertexCount) throw new IllegalArgumentException("Mesh 边索引无效");
			int a = oldToNew[oldA], b = oldToNew[oldB];
			if (a == b) continue;
			long key = ((long)Math.min(a, b) << 32) | Integer.toUnsignedLong(Math.max(a, b));
			if (seen.add(key)) { result.add((short)a); result.add((short)b); }
		}
		return toShortArray(result);
	}

	/** Adds a face between three existing vertices while preserving all vertex and Deform data. */
	public static MeshGeometryState addMeshTriangle(MeshGeometryState state, int a, int b, int c) {
		if (state == null || state.triangles == null || state.regionUVs == null)
			throw new IllegalArgumentException("Mesh 建面数据无效");
		int vertexCount = state.vertexCount();
		if (a < 0 || b < 0 || c < 0 || a >= vertexCount || b >= vertexCount || c >= vertexCount
			|| a == b || b == c || c == a) throw new IllegalArgumentException("Mesh 建面顶点无效");
		for (int index = 0; index + 2 < state.triangles.length; index += 3) {
			int existingA = Short.toUnsignedInt(state.triangles[index]);
			int existingB = Short.toUnsignedInt(state.triangles[index + 1]);
			int existingC = Short.toUnsignedInt(state.triangles[index + 2]);
			if (sameTriangle(a, b, c, existingA, existingB, existingC))
				throw new IllegalArgumentException("所选顶点已经组成三角形");
		}
		if (state.triangles.length + 3 > spineMeshTriangleIndexCount(state))
			throw new IllegalArgumentException("Mesh 已达到 Spine 格式允许的三角形数量上限");
		short[] triangles = Arrays.copyOf(state.triangles, state.triangles.length + 3);
		triangles[state.triangles.length] = (short)a;
		triangles[state.triangles.length + 1] = (short)b;
		triangles[state.triangles.length + 2] = (short)c;
		return new MeshGeometryState(state.color, state.path, state.bones, state.vertices, state.regionUVs,
			triangles, state.hullLength, state.edges, state.width, state.height);
	}

	private static float barycentricValue(float[] values, int a, int b, int c,
		float weightA, float weightB, float weightC, int component) {
		int valueA = a * 2 + component, valueB = b * 2 + component, valueC = c * 2 + component;
		if (valueA < 0 || valueB < 0 || valueC < 0 || valueA >= values.length
			|| valueB >= values.length || valueC >= values.length)
			throw new IllegalArgumentException("Mesh 插点顶点数据不完整");
		float result = values[valueA] * weightA + values[valueB] * weightB + values[valueC] * weightC;
		if (!Float.isFinite(result)) throw new IllegalArgumentException("Mesh 插点结果不是有限数值");
		return result;
	}

	private static MeshWeightEncoding encodeMeshWeights(List<? extends List<MeshWeightInfluence>> decoded) {
		int boneLength = 0, valueLength = 0;
		for (List<MeshWeightInfluence> influences : decoded) {
			if (influences == null || influences.isEmpty())
				throw new IllegalArgumentException("Mesh 每个顶点至少需要一个骨骼影响");
			boneLength += influences.size() + 1;
			valueLength += influences.size() * 3;
		}
		int[] bones = new int[boneLength];
		float[] vertices = new float[valueLength];
		int boneCursor = 0, valueCursor = 0;
		for (List<MeshWeightInfluence> influences : decoded) {
			bones[boneCursor++] = influences.size();
			for (MeshWeightInfluence influence : influences) {
				bones[boneCursor++] = influence.boneIndex;
				vertices[valueCursor++] = influence.x;
				vertices[valueCursor++] = influence.y;
				vertices[valueCursor++] = influence.weight;
			}
		}
		return new MeshWeightEncoding(bones, vertices);
	}

	private static short[] appendMeshEdges(short[] edges, int a, int b, int c, int inserted) {
		if (edges == null) return null;
		if ((edges.length & 1) != 0) throw new IllegalArgumentException("Mesh 边索引不完整");
		ArrayList<Short> result = new ArrayList<>(edges.length + 6);
		for (short edge : edges) result.add(edge);
		appendMeshEdge(result, a, inserted);
		appendMeshEdge(result, b, inserted);
		appendMeshEdge(result, c, inserted);
		return toShortArray(result);
	}

	private static void appendMeshEdge(ArrayList<Short> edges, int a, int b) {
		for (int index = 0; index + 1 < edges.size(); index += 2) {
			int existingA = Short.toUnsignedInt(edges.get(index));
			int existingB = Short.toUnsignedInt(edges.get(index + 1));
			if (existingA == a && existingB == b || existingA == b && existingB == a) return;
		}
		edges.add((short)a);
		edges.add((short)b);
	}

	private static short[] removeAndRemapMeshEdges(short[] edges, boolean[] removed, int[] remap,
		int vertexCount) {
		if (edges == null) return null;
		if ((edges.length & 1) != 0) throw new IllegalArgumentException("Mesh 边索引不完整");
		ArrayList<Short> result = new ArrayList<>(edges.length);
		for (int index = 0; index < edges.length; index += 2) {
			int a = Short.toUnsignedInt(edges[index]);
			int b = Short.toUnsignedInt(edges[index + 1]);
			if (a >= vertexCount || b >= vertexCount) throw new IllegalArgumentException("Mesh 边索引无效");
			if (removed[a] || removed[b]) continue;
			result.add((short)remap[a]);
			result.add((short)remap[b]);
		}
		return toShortArray(result);
	}

	private static float[] removeVertexPairs(float[] values, boolean[] removed, int removedCount) {
		if (values == null || values.length != removed.length * 2)
			throw new IllegalArgumentException("Mesh 顶点坐标对不完整");
		float[] result = new float[values.length - removedCount * 2];
		int output = 0;
		for (int vertex = 0; vertex < removed.length; vertex++) {
			if (removed[vertex]) continue;
			result[output++] = values[vertex * 2];
			result[output++] = values[vertex * 2 + 1];
		}
		return result;
	}

	private static short[] toShortArray(List<Short> values) {
		short[] result = new short[values.size()];
		for (int index = 0; index < result.length; index++) result[index] = values.get(index);
		return result;
	}

	private static boolean sameTriangle(int a, int b, int c, int x, int y, int z) {
		return (a == x || a == y || a == z) && (b == x || b == y || b == z)
			&& (c == x || c == y || c == z);
	}

	/** Returns normalized target-bone weights for viewport visualization. */
	public static float[] meshWeightsForBone(MeshAttachment mesh, int targetBoneIndex, int rigidBoneIndex) {
		if (mesh == null || targetBoneIndex < 0 || rigidBoneIndex < 0)
			throw new IllegalArgumentException("Mesh 权重显示目标无效");
		int vertexCount = mesh.getWorldVerticesLength() / 2;
		if (mesh.getWorldVerticesLength() < 0 || mesh.getWorldVerticesLength() % 2 != 0)
			throw new IllegalArgumentException("Mesh 顶点数量无效");
		if (mesh.getBones() == null) {
			float[] result = new float[vertexCount];
			if (targetBoneIndex == rigidBoneIndex) Arrays.fill(result, 1f);
			return result;
		}
		return meshWeightsForBone(mesh.getBones(), mesh.getVertices(), vertexCount, targetBoneIndex);
	}

	private static float[] meshWeightsForBone(int[] bones, float[] vertices, int vertexCount, int targetBoneIndex) {
		if (bones == null || vertices == null || vertexCount < 0)
			throw new IllegalArgumentException("Mesh 权重数据不完整");
		float[] result = new float[vertexCount];
		int boneCursor = 0, valueCursor = 0;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			if (boneCursor >= bones.length) throw new IllegalArgumentException("Mesh 权重骨骼编码不完整");
			int influenceCount = bones[boneCursor++];
			if (influenceCount <= 0 || boneCursor + influenceCount > bones.length
				|| valueCursor + influenceCount * 3 > vertices.length)
				throw new IllegalArgumentException("Mesh 顶点影响数据不完整");
			float sum = 0f, selected = 0f;
			for (int influence = 0; influence < influenceCount; influence++) {
				int boneIndex = bones[boneCursor++];
				float weight = vertices[valueCursor + 2];
				if (!Float.isFinite(weight) || weight < 0f) throw new IllegalArgumentException("Mesh 权重数据无效");
				sum += weight;
				if (boneIndex == targetBoneIndex) selected += weight;
				valueCursor += 3;
			}
			if (!(sum > 0f) || !Float.isFinite(sum)) throw new IllegalArgumentException("Mesh 顶点权重总和无效");
			result[vertex] = Math.max(0f, Math.min(1f, selected / sum));
		}
		if (boneCursor != bones.length || valueCursor != vertices.length)
			throw new IllegalArgumentException("Mesh 权重包含多余数据");
		return result;
	}

	private static ArrayList<ArrayList<MeshWeightInfluence>> decodeMeshWeights(MeshGeometryState state,
		int rigidBoneIndex) {
		int vertexCount = state.vertexCount();
		ArrayList<ArrayList<MeshWeightInfluence>> result = new ArrayList<>(vertexCount);
		if (state.bones == null) {
			if (state.vertices.length != vertexCount * 2)
				throw new IllegalArgumentException("非加权 Mesh 顶点数据不完整");
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				ArrayList<MeshWeightInfluence> influences = new ArrayList<>(2);
				influences.add(new MeshWeightInfluence(rigidBoneIndex, state.vertices[vertex * 2],
					state.vertices[vertex * 2 + 1], 1f));
				result.add(influences);
			}
			return result;
		}
		int boneCursor = 0, valueCursor = 0;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			if (boneCursor >= state.bones.length) throw new IllegalArgumentException("Mesh 权重骨骼编码不完整");
			int influenceCount = state.bones[boneCursor++];
			if (influenceCount <= 0 || boneCursor + influenceCount > state.bones.length
				|| valueCursor + influenceCount * 3 > state.vertices.length)
				throw new IllegalArgumentException("Mesh 顶点影响数据不完整");
			ArrayList<MeshWeightInfluence> influences = new ArrayList<>(influenceCount + 1);
			Set<Integer> seen = new HashSet<>();
			for (int influence = 0; influence < influenceCount; influence++) {
				int boneIndex = state.bones[boneCursor++];
				if (!seen.add(boneIndex)) throw new IllegalArgumentException("Mesh 顶点重复绑定同一骨骼");
				influences.add(new MeshWeightInfluence(boneIndex, state.vertices[valueCursor],
					state.vertices[valueCursor + 1], state.vertices[valueCursor + 2]));
				valueCursor += 3;
			}
			result.add(influences);
		}
		if (boneCursor != state.bones.length || valueCursor != state.vertices.length)
			throw new IllegalArgumentException("Mesh 权重包含多余数据");
		return result;
	}

	private Skin findAttachmentSkin(Attachment attachment, int slotIndex) {
		if (doc.data == null || attachment == null || slotIndex < 0) return null;
		for (Skin skin : doc.data.getSkins()) {
			Array<Skin.SkinEntry> entries = new Array<>();
			skin.getAttachments(slotIndex, entries);
			for (Skin.SkinEntry entry : entries)
				if (entry.getAttachment() == attachment) return skin;
		}
		return null;
	}

	/** Converts weighted setup geometry to slot-bone local coordinates without changing its setup appearance. */
	public float[] unweightMeshVertices(int[] bones, float[] vertices, int vertexCount, SlotData slotData) {
		if (doc.data == null || slotData == null || !doc.data.getSlots().contains(slotData, true))
			throw new IllegalArgumentException("Mesh 槽数据无效");
		String reason = validateMeshVertices(bones, vertices, vertexCount);
		if (bones == null || reason != null)
			throw new IllegalArgumentException(reason == null ? "Mesh 尚未加权" : reason);

		Skeleton setupSkeleton = new Skeleton(doc.data);
		Skin activeSkin = findAttachmentSkinForSlot(slotData.getIndex());
		if (activeSkin == null) activeSkin = doc.data.getDefaultSkin();
		if (activeSkin != null) {
			setupSkeleton.setSkin(activeSkin);
			setupSkeleton.setupPoseSlots();
		}
		setupSkeleton.updateWorldTransform(Physics.pose);
		Slot setupSlot = setupSkeleton.getSlots().get(slotData.getIndex());
		BonePose slotPose = setupSlot.getBone().getAppliedPose();
		float[] result = new float[vertexCount * 2];
		int boneCursor = 0, valueCursor = 0;
		Vector2 point = new Vector2();
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int influenceCount = bones[boneCursor++];
			float worldX = 0f, worldY = 0f;
			for (int influence = 0; influence < influenceCount; influence++) {
				BonePose bone = setupSkeleton.getBones().get(bones[boneCursor++]).getAppliedPose();
				float x = vertices[valueCursor++];
				float y = vertices[valueCursor++];
				float weight = vertices[valueCursor++];
				worldX += (x * bone.getA() + y * bone.getB() + bone.getWorldX()) * weight;
				worldY += (x * bone.getC() + y * bone.getD() + bone.getWorldY()) * weight;
			}
			point.set(worldX, worldY);
			slotPose.worldToLocal(point);
			if (!Float.isFinite(point.x) || !Float.isFinite(point.y))
				throw new IllegalArgumentException("Mesh 槽骨骼变换不可逆");
			result[vertex * 2] = point.x;
			result[vertex * 2 + 1] = point.y;
		}
		return result;
	}

	private Skin findAttachmentSkinForSlot(int slotIndex) {
		for (Skin skin : doc.data.getSkins()) {
			Array<Skin.SkinEntry> entries = new Array<>();
			skin.getAttachments(slotIndex, entries);
			if (entries.notEmpty()) return skin;
		}
		return null;
	}

	/** Returns null when a Region or empty placeholder can be converted to an independent one-frame mesh. */
	public String getMeshReplacementBlockReason(Skin skin, SlotData slot, String placeholder) {
		if (doc.data == null || skin == null || slot == null || placeholder == null
			|| !doc.data.getSkins().contains(skin, true) || !doc.data.getSlots().contains(slot, true))
			return "附件目标无效";
		Attachment current = skin.getAttachment(slot.getIndex(), placeholder);
		if (current == null) return null;
		if (current instanceof RegionAttachment region) {
			String reason = getRegionReplacementBlockReason(region);
			if (reason != null) return reason.replace("Region", "Mesh");
			if (hasOtherAttachmentOwner(region, skin, slot.getIndex(), placeholder)) {
				reason = getAttachmentReferenceBlockReason(region, null);
				if (reason != null)
					return "共享 Region 仍被其他皮肤或槽使用，不能只转换当前 owner：" + reason;
			}
			return null;
		}
		if (current instanceof MeshAttachment mesh) {
			if (mesh.getSourceMesh() != null) return "当前占位是 Linked Mesh，不能改为独立 Mesh";
			if (mesh.getSequence() == null) return "当前 Mesh 的图片序列数据无效";
			if (mesh.getSequence().hasPathSuffix() || mesh.getSequence().getRegions().length != 1)
				return "当前 Mesh 使用多帧 Sequence，不能用单图区域直接替换";
			return null;
		}
		return "当前占位是 " + current.getClass().getSimpleName() + "，请先确认删除后再创建 Mesh";
	}

	/** Creates a rectangular mesh, converts a Region without changing its visible quad, or changes a mesh texture. */
	public MeshAttachment setSlotMeshAttachment(Skin skin, SlotData slot, String placeholder, String path) {
		if (doc.data == null || skin == null || slot == null || placeholder == null || placeholder.isBlank()
			|| !doc.data.getSkins().contains(skin, true) || !doc.data.getSlots().contains(slot, true)
			|| getMeshReplacementBlockReason(skin, slot, placeholder) != null) return null;
		String resolvedPath = path == null || path.isBlank() ? placeholder : path;
		TextureRegion texture = findRegion(resolvedPath);
		if (texture == null) return null;
		Attachment current = skin.getAttachment(slot.getIndex(), placeholder);
		if (current instanceof MeshAttachment mesh) {
			if (java.util.Objects.equals(mesh.getPath(), resolvedPath)
				&& mesh.getSequence().getRegions()[0] == texture
				&& java.util.Objects.equals(slot.getAttachmentName(), placeholder)) return mesh;
			mesh.setPath(resolvedPath);
			mesh.getSequence().getRegions()[0] = texture;
			mesh.updateSequence();
			slot.setAttachmentName(placeholder);
			doc.markDirty();
			rebuildSkeleton();
			return mesh;
		}

		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = texture;
		MeshAttachment mesh = new MeshAttachment(placeholder, sequence);
		mesh.setPath(resolvedPath);
		mesh.setBones(null);
		mesh.setTriangles(new short[] {0, 1, 2, 2, 3, 0});
		mesh.setHullLength(8);
		mesh.setEdges(new short[] {0, 1, 1, 2, 2, 3, 3, 0});
		if (current instanceof RegionAttachment region) {
			float[] offsets = region.getSequence().getOffsets(region.getSequence().getSetupIndex());
			float[] actualUvs = region.getSequence().getUVs(region.getSequence().getSetupIndex());
			mesh.setVertices(offsets.clone());
			mesh.setRegionUVs(toRegionUVs(texture, actualUvs));
			mesh.getColor().set(region.getColor());
			mesh.setWidth(region.getWidth());
			mesh.setHeight(region.getHeight());
			Attachment timelineAttachment = region.getTimelineAttachment();
			mesh.setTimelineAttachment(timelineAttachment == region ? mesh : timelineAttachment);
			int[] timelineSlots = region.getTimelineSlots();
			mesh.setTimelineSlots(timelineSlots == null ? null : timelineSlots.clone());
		} else {
			float width = texture.getRegionWidth(), height = texture.getRegionHeight();
			if (!(width > 0f) || !(height > 0f)) return null;
			mesh.setVertices(new float[] {-width / 2f, -height / 2f, -width / 2f, height / 2f,
				width / 2f, height / 2f, width / 2f, -height / 2f});
			mesh.setRegionUVs(new float[] {0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f});
			mesh.setWidth(width);
			mesh.setHeight(height);
		}
		mesh.setWorldVerticesLength(8);
		mesh.updateSequence();
		if (current instanceof RegionAttachment region
			&& !hasOtherAttachmentOwner(region, skin, slot.getIndex(), placeholder)) {
			if (!replaceAttachmentOwners(region, mesh)) return null;
		} else {
			skin.setAttachment(slot.getIndex(), placeholder, mesh);
		}
		slot.setAttachmentName(placeholder);
		doc.markDirty();
		rebuildSkeleton();
		return mesh;
	}

	/** Lists each mesh attachment identity once, using the same stable skin order as JSON export. */
	public Array<MeshAttachmentSource> meshAttachmentSources() {
		Array<MeshAttachmentSource> sources = new Array<>();
		if (doc.data == null) return sources;
		IdentityHashMap<MeshAttachment, Boolean> seen = new IdentityHashMap<>();
		for (Skin skin : doc.data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (!(entry.getAttachment() instanceof MeshAttachment mesh) || mesh.getSourceMesh() != null
					|| seen.put(mesh, Boolean.TRUE) != null)
					continue;
				int slotIndex = entry.getSlotIndex();
				if (slotIndex < 0 || slotIndex >= doc.data.getSlots().size) continue;
				sources.add(new MeshAttachmentSource(skin, doc.data.getSlots().get(slotIndex),
					entry.getPlaceholder(), mesh));
			}
		}
		return sources;
	}

	/** Returns null when a new Linked Mesh can be created without replacing existing attachment data. */
	public String getLinkedMeshCreationBlockReason(Skin skin, SlotData slot, String placeholder, String path,
		MeshAttachmentSource source) {
		if (doc.data == null || skin == null || slot == null || placeholder == null || placeholder.isBlank()
			|| !doc.data.getSkins().contains(skin, true) || !doc.data.getSlots().contains(slot, true))
			return "附件目标无效";
		if (skin.getAttachment(slot.getIndex(), placeholder) != null)
			return "当前占位已有附件，请先确认删除后再创建 Linked Mesh";
		if (!isCurrentMeshSource(source)) return "源网格不存在或位置已变化";
		MeshAttachment geometry = independentMeshSource(source.mesh());
		if (geometry == null) return "源网格引用形成循环";
		String geometryReason = validateMeshGeometry(geometry, meshGeometryState(geometry));
		if (geometryReason != null) return "源网格数据无效：" + geometryReason;
		String resolvedPath = path == null || path.isBlank() ? placeholder : path;
		if (findRegion(resolvedPath) == null) return "图集区域不存在：" + resolvedPath;
		return null;
	}

	/** Creates a one-frame Linked Mesh and optionally inherits the selected source's Deform timelines. */
	public MeshAttachment createLinkedMeshAttachment(Skin skin, SlotData slot, String placeholder, String path,
		MeshAttachmentSource source, boolean inheritTimelines) {
		if (getLinkedMeshCreationBlockReason(skin, slot, placeholder, path, source) != null) return null;
		String resolvedPath = path == null || path.isBlank() ? placeholder : path;
		TextureRegion texture = findRegion(resolvedPath);
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = texture;
		MeshAttachment linked = new MeshAttachment(placeholder, sequence);
		linked.setPath(resolvedPath);
		linked.setSourceMesh(source.mesh());
		linked.setTimelineAttachment(inheritTimelines ? source.mesh() : linked);
		linked.updateSequence();
		skin.setAttachment(slot.getIndex(), placeholder, linked);
		slot.setAttachmentName(placeholder);
		if (inheritTimelines && source.slot().getIndex() != slot.getIndex())
			addTimelineSlot(source.mesh(), slot.getIndex());
		doc.markDirty();
		rebuildSkeleton();
		return linked;
	}

	/** Returns null when an existing Linked Mesh can be updated in place without invalidating timeline data. */
	public String getLinkedMeshUpdateBlockReason(Skin skin, SlotData slot, String placeholder, String path,
		MeshAttachmentSource source, boolean inheritTimelines) {
		if (doc.data == null || skin == null || slot == null || placeholder == null || placeholder.isBlank()
			|| !doc.data.getSkins().contains(skin, true) || !doc.data.getSlots().contains(slot, true))
			return "附件目标无效";
		Attachment attachment = skin.getAttachment(slot.getIndex(), placeholder);
		if (!(attachment instanceof MeshAttachment linked) || linked.getSourceMesh() == null)
			return "当前占位不是 Linked Mesh";
		if (!isCurrentMeshSource(source)) return "源网格不存在或位置已变化";
		MeshAttachment geometry = independentMeshSource(source.mesh());
		if (geometry == null) return "源网格引用形成循环";
		MeshGeometryState geometryState = meshGeometryState(geometry);
		String geometryReason = validateMeshGeometry(geometry, geometryState);
		if (geometryReason != null) return "源网格数据无效：" + geometryReason;
		if (inheritTimelines) {
			String timelineReason = getAttachmentTimelineReferenceReason(linked);
			if (timelineReason != null) return "切换到继承 Deform 前需要删除直属轨道：" + timelineReason;
		}
		String deformReason = validateMeshDeformCompatibility(linked, geometryState, true);
		if (deformReason != null) return deformReason;
		Sequence sequence = linked.getSequence();
		if (sequence == null || sequence.hasPathSuffix() || sequence.getRegions().length != 1)
			return "当前 Linked Mesh 使用图片 Sequence，请在 Sequence 编辑器中修改图片";
		String resolvedPath = path == null || path.isBlank() ? placeholder : path;
		if (findRegion(resolvedPath) == null) return "图集区域不存在：" + resolvedPath;
		return null;
	}

	/** Updates an existing Linked Mesh without replacing its identity or breaking attachment timelines. */
	public MeshAttachment updateLinkedMeshAttachment(Skin skin, SlotData slot, String placeholder, String path,
		MeshAttachmentSource source, boolean inheritTimelines) {
		if (getLinkedMeshUpdateBlockReason(skin, slot, placeholder, path, source, inheritTimelines) != null) return null;
		MeshAttachment linked = (MeshAttachment)skin.getAttachment(slot.getIndex(), placeholder);
		String resolvedPath = path == null || path.isBlank() ? placeholder : path;
		TextureRegion texture = findRegion(resolvedPath);
		Attachment timelineAttachment = inheritTimelines ? source.mesh() : linked;
		if (linked.getSourceMesh() == source.mesh() && linked.getTimelineAttachment() == timelineAttachment
			&& java.util.Objects.equals(linked.getPath(), resolvedPath)
			&& linked.getSequence().getRegions()[0] == texture
			&& java.util.Objects.equals(slot.getAttachmentName(), placeholder)) return linked;

		List<MeshDeformState> migrated = null;
		if (!inheritTimelines) {
			MeshGeometryState before = meshGeometryState(linked);
			MeshGeometryState after = meshGeometryState(source.mesh());
			try {
				List<MeshDeformState> deforms = captureAttachmentDeforms(linked, deformLength(before));
				if (!deforms.isEmpty()) {
					if (before.bones == null && after.bones == null
						&& !Arrays.equals(before.vertices, after.vertices))
						migrated = rebaseUnweightedDeforms(deforms, before.vertices, after.vertices);
					else if (before.bones != null && after.bones != null
						&& !Arrays.equals(before.bones, after.bones)) {
						int[] remap = weightedDeformRemap(before.bones, after.bones, before.vertexCount());
						if (remap == null) return null;
						migrated = remapWeightedDeforms(deforms, remap);
					}
				}
			} catch (IllegalArgumentException invalidDeform) {
				return null;
			}
		}

		Attachment oldTimelineSource = linked.getTimelineAttachment();
		linked.setPath(resolvedPath);
		linked.getSequence().getRegions()[0] = texture;
		linked.setSourceMesh(source.mesh());
		linked.setTimelineAttachment(timelineAttachment);
		linked.updateSequence();
		if (migrated != null) applyDeformFrames(migrated);
		slot.setAttachmentName(placeholder);
		releaseTimelineSlotIfUnused(oldTimelineSource, slot.getIndex());
		if (inheritTimelines && source.slot().getIndex() != slot.getIndex())
			addTimelineSlot(source.mesh(), slot.getIndex());
		doc.markDirty();
		rebuildSkeleton();
		return linked;
	}

	private boolean isCurrentMeshSource(MeshAttachmentSource source) {
		if (source == null || source.skin() == null || source.slot() == null || source.placeholder() == null
			|| source.mesh() == null || doc.data == null) return false;
		Array<MeshAttachmentSource> current = meshAttachmentSources();
		for (MeshAttachmentSource candidate : current)
			if (candidate.mesh() == source.mesh() && candidate.skin() == source.skin()
				&& candidate.slot() == source.slot() && candidate.placeholder().equals(source.placeholder())) return true;
		return false;
	}

	private static MeshAttachment independentMeshSource(MeshAttachment source) {
		IdentityHashMap<MeshAttachment, Boolean> seen = new IdentityHashMap<>();
		MeshAttachment current = source;
		while (current != null && current.getSourceMesh() != null) {
			if (seen.put(current, Boolean.TRUE) != null) return null;
			current = current.getSourceMesh();
		}
		return current;
	}

	private static void addTimelineSlot(Attachment source, int slotIndex) {
		int[] slots = source.getTimelineSlots();
		for (int existing : slots) if (existing == slotIndex) return;
		int[] updated = Arrays.copyOf(slots, slots.length + 1);
		updated[slots.length] = slotIndex;
		source.setTimelineSlots(updated);
	}

	private void releaseInheritedTimelineSlot(Attachment removed, int slotIndex) {
		if (removed == null) return;
		Attachment source = removed.getTimelineAttachment();
		if (source == removed) return;
		releaseTimelineSlotIfUnused(source, slotIndex);
	}

	private void releaseTimelineSlotIfUnused(Attachment source, int slotIndex) {
		if (source == null || attachmentUsesTimelineSourceInSlot(source, slotIndex)) return;
		MeshAttachmentSource location = source instanceof MeshAttachment mesh ? meshSource(mesh) : null;
		if (location != null && location.slot().getIndex() == slotIndex) return;
		int[] slots = source.getTimelineSlots();
		int count = 0;
		for (int existing : slots) if (existing != slotIndex) count++;
		if (count == slots.length) return;
		int[] updated = new int[count];
		int target = 0;
		for (int existing : slots) if (existing != slotIndex) updated[target++] = existing;
		source.setTimelineSlots(updated);
	}

	private boolean attachmentUsesTimelineSourceInSlot(Attachment source, int slotIndex) {
		if (doc.data == null) return false;
		for (Skin skin : doc.data.getSkins()) {
			Array<Skin.SkinEntry> entries = new Array<>();
			skin.getAttachments(slotIndex, entries);
			for (Skin.SkinEntry entry : entries)
				if (entry.getAttachment().getTimelineAttachment() == source) return true;
		}
		return false;
	}

	private MeshAttachmentSource meshSource(MeshAttachment mesh) {
		for (MeshAttachmentSource source : meshAttachmentSources()) if (source.mesh() == mesh) return source;
		return null;
	}

	private static float[] toRegionUVs(TextureRegion texture, float[] actualUvs) {
		float u, v, width, height;
		int degrees = 0;
		if (texture instanceof AtlasRegion region) {
			u = region.getU();
			v = region.getV();
			float textureWidth = region.getTexture().getWidth();
			float textureHeight = region.getTexture().getHeight();
			degrees = region.degrees;
			switch (degrees) {
			case 90 -> {
				u -= (region.originalHeight - region.offsetY - region.packedWidth) / textureWidth;
				v -= (region.originalWidth - region.offsetX - region.packedHeight) / textureHeight;
				width = region.originalHeight / textureWidth;
				height = region.originalWidth / textureHeight;
			}
			case 180 -> {
				u -= (region.originalWidth - region.offsetX - region.packedWidth) / textureWidth;
				v -= region.offsetY / textureHeight;
				width = region.originalWidth / textureWidth;
				height = region.originalHeight / textureHeight;
			}
			case 270 -> {
				u -= region.offsetY / textureWidth;
				v -= region.offsetX / textureHeight;
				width = region.originalHeight / textureWidth;
				height = region.originalWidth / textureHeight;
			}
			default -> {
				u -= region.offsetX / textureWidth;
				v -= (region.originalHeight - region.offsetY - region.packedHeight) / textureHeight;
				width = region.originalWidth / textureWidth;
				height = region.originalHeight / textureHeight;
			}
			}
		} else {
			u = texture.getU();
			v = texture.getV();
			width = texture.getU2() - u;
			height = texture.getV2() - v;
		}
		if (width == 0f || height == 0f) return new float[] {0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f};
		float[] result = new float[actualUvs.length];
		for (int index = 0; index < actualUvs.length; index += 2) {
			float x = (actualUvs[index] - u) / width;
			float y = (actualUvs[index + 1] - v) / height;
			switch (degrees) {
			case 90 -> { result[index] = 1f - y; result[index + 1] = x; }
			case 180 -> { result[index] = 1f - x; result[index + 1] = 1f - y; }
			case 270 -> { result[index] = y; result[index + 1] = 1f - x; }
			default -> { result[index] = x; result[index + 1] = y; }
			}
		}
		return result;
	}

	/** Returns null when the complete mesh state can be applied atomically. */
	public String validateMeshGeometry(MeshAttachment mesh, MeshGeometryState state) {
		return validateMeshGeometry(mesh, state, true);
	}

	private String validateMeshGeometry(MeshAttachment mesh, MeshGeometryState state, boolean validateDeforms) {
		return validateMeshGeometry(mesh, state, validateDeforms, true);
	}

	private String validateMeshGeometry(MeshAttachment mesh, MeshGeometryState state, boolean validateDeforms,
		boolean requireIndependent) {
		if (doc.data == null || mesh == null || !containsAttachment(mesh)) return "Mesh 附件不在当前项目中";
		if (requireIndependent && mesh.getSourceMesh() != null) return "Linked Mesh 共享源网格几何，不能单独编辑";
		if (state == null || state.color() == null) return "Mesh 几何数据不完整";
		if (!isUnitColor(state.color())) return "Mesh 颜色必须是 0 到 1 的有限数值";
		if (state.path() == null || state.path().isBlank()) return "Mesh 图片路径不能为空";
		float[] uvs = state.regionUVs();
		float[] vertices = state.vertices();
		short[] triangles = state.triangles();
		short[] edges = state.edges();
		int[] bones = state.bones();
		if (uvs == null || (uvs.length & 1) != 0 || uvs.length < 6) return "Mesh UV 必须包含至少 3 个坐标对";
		if (!areFinite(uvs)) return "Mesh UV 必须是有限数值";
		int vertexCount = uvs.length / 2;
		if (vertexCount > 65536) return "Mesh 顶点数量不能超过 65536";
		String vertexReason = validateMeshVertices(bones, vertices, vertexCount);
		if (vertexReason != null) return vertexReason;
		if (triangles == null || triangles.length == 0 || triangles.length % 3 != 0)
			return "Mesh 三角形索引必须按每 3 个一组提供";
		for (int index = 0; index < triangles.length; index += 3) {
			int a = Short.toUnsignedInt(triangles[index]);
			int b = Short.toUnsignedInt(triangles[index + 1]);
			int c = Short.toUnsignedInt(triangles[index + 2]);
			if (a >= vertexCount || b >= vertexCount || c >= vertexCount)
				return "Mesh 三角形引用了不存在的顶点";
			if (a == b || b == c || c == a) return "Mesh 三角形不能包含重复顶点";
		}
		if ((state.hullLength() & 1) != 0 || state.hullLength() < 0 || state.hullLength() > vertexCount * 2
			|| state.hullLength() > 0 && state.hullLength() < 6)
			return "Mesh hull 必须为 0，或包含 3 个到全部顶点";
		int binaryTriangleIndexCount = spineMeshTriangleIndexCount(state);
		if (triangles.length > binaryTriangleIndexCount)
			return "Mesh 三角形索引超过 Spine 格式上限：当前 " + triangles.length
				+ "，最多 " + binaryTriangleIndexCount;
		if (edges != null) {
			if ((edges.length & 1) != 0) return "Mesh 边索引必须按每 2 个一组提供";
			for (int index = 0; index < edges.length; index += 2) {
				int a = Short.toUnsignedInt(edges[index]);
				int b = Short.toUnsignedInt(edges[index + 1]);
				if (a >= vertexCount || b >= vertexCount) return "Mesh 边引用了不存在的顶点";
				if (a == b) return "Mesh 边的两个顶点不能相同";
			}
		}
		if (!areFinite(state.width(), state.height()) || state.width() < 0f || state.height() < 0f)
			return "Mesh 宽度和高度必须是不小于 0 的有限数值";
		if (!java.util.Objects.equals(mesh.getPath(), state.path())) {
			if (mesh.getSequence() == null || mesh.getSequence().hasPathSuffix()
				|| mesh.getSequence().getRegions().length != 1)
				return "多帧 Mesh 不能在几何编辑中修改图片路径";
			if (findRegion(state.path()) == null) return "图集中找不到 Mesh 图片区域：" + state.path();
		}
		if (validateDeforms) {
			String deformReason = validateMeshDeformCompatibility(mesh, state);
			if (deformReason != null) return deformReason;
		}
		return null;
	}

	private static int spineMeshTriangleIndexCount(MeshGeometryState state) {
		return (state.vertexCount() * 2 - state.hullLength() / 2 - 2) * 3;
	}

	private String validateMeshVertices(int[] bones, float[] vertices, int vertexCount) {
		if (vertices == null) return "Mesh 顶点数据不能为空";
		if (!areFinite(vertices)) return "Mesh 顶点和权重必须是有限数值";
		if (bones == null)
			return vertices.length == vertexCount * 2 ? null : "非加权 Mesh 每个顶点必须有 X、Y 两个数值";
		int boneCursor = 0, valueCursor = 0;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			if (boneCursor >= bones.length) return "加权 Mesh 的骨骼数据不完整";
			int influenceCount = bones[boneCursor++];
			if (influenceCount <= 0) return "每个加权顶点必须绑定至少一个骨骼";
			Set<Integer> seen = new HashSet<>();
			float weightSum = 0f;
			for (int influence = 0; influence < influenceCount; influence++) {
				if (boneCursor >= bones.length || valueCursor + 2 >= vertices.length)
					return "加权 Mesh 的顶点影响数据不完整";
				int boneIndex = bones[boneCursor++];
				if (boneIndex < 0 || boneIndex >= doc.data.getBones().size)
					return "加权 Mesh 引用了不存在的骨骼索引 " + boneIndex;
				if (!seen.add(boneIndex)) return "同一顶点不能重复绑定同一骨骼";
				float weight = vertices[valueCursor + 2];
				if (weight < 0f) return "Mesh 权重不能小于 0";
				weightSum += weight;
				valueCursor += 3;
			}
			if (!(weightSum > 0f)) return "每个加权顶点的权重总和必须大于 0";
		}
		if (boneCursor != bones.length || valueCursor != vertices.length)
			return "加权 Mesh 包含多余的骨骼或顶点数据";
		return null;
	}

	private String validateMeshDeformCompatibility(MeshAttachment mesh, MeshGeometryState state) {
		return validateMeshDeformCompatibility(mesh, state, true);
	}

	private String validateMeshDeformCompatibility(MeshAttachment mesh, MeshGeometryState state,
		boolean allowInfluenceReorder) {
		int newDeformLength = state.bones() == null ? state.vertices().length : state.vertices().length / 3 * 2;
		boolean weightedChanged = (mesh.getBones() == null) != (state.bones() == null);
		boolean influenceOrderChanged = !weightedChanged && mesh.getBones() != null
			&& !Arrays.equals(mesh.getBones(), state.bones());
		int[] influenceRemap = null;
		for (Animation animation : doc.data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (!(timeline instanceof Animation.DeformTimeline deform)
					|| !sharesMeshGeometry(deform.getAttachment(), mesh)) continue;
				if (weightedChanged) return "动画 " + animation.getName() + " 的 Deform 轨道阻止切换 Mesh 权重模式";
				for (float[] frameVertices : deform.getVertices())
					if (frameVertices == null || frameVertices.length != newDeformLength)
						return "动画 " + animation.getName() + " 的 Deform 顶点数量与新几何不兼容";
				if (influenceOrderChanged) {
					if (!allowInfluenceReorder)
						return "动画 " + animation.getName() + " 的 Deform 轨道阻止更换 Linked Mesh 权重布局";
					if (influenceRemap == null)
						influenceRemap = weightedDeformRemap(mesh.getBones(), state.bones(), state.vertexCount());
					if (influenceRemap == null)
						return "动画 " + animation.getName()
							+ " 的 Deform 轨道要求每个 Mesh 顶点保留相同的骨骼影响集合";
				}
			}
		}
		return null;
	}

	/**
	 * Applies a logical-vertex permutation atomically, including every Deform target sharing this mesh geometry.
	 * The inverse {@link MeshVertexOrder} can be passed back to provide lossless undo.
	 */
	public boolean applyMeshVertexOrder(MeshAttachment mesh, MeshVertexOrder order) {
		if (doc.data == null || mesh == null || order == null || !containsAttachment(mesh)
			|| mesh.getSourceMesh() != null) return false;
		MeshGeometryState current = meshGeometryState(mesh);
		String currentReason = validateMeshGeometry(mesh, current);
		if (currentReason != null) throw new IllegalArgumentException(currentReason);
		if (current.vertexCount() != order.vertexCount())
			throw new IllegalArgumentException("Mesh 顶点排列数量与当前网格不一致");
		if (order.isIdentity(current.hullLength() / 2)) return false;
		MeshGeometryState reordered = reorderMeshVertices(current, order);
		String reason = validateMeshGeometry(mesh, reordered, false);
		if (reason != null) throw new IllegalArgumentException(reason);

		ArrayList<Animation.DeformTimeline> deformTimelines = new ArrayList<>();
		ArrayList<float[][]> deformFrames = new ArrayList<>();
		for (Animation animation : doc.data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (!(timeline instanceof Animation.DeformTimeline deform)
					|| !sharesMeshGeometry(deform.getAttachment(), mesh)) continue;
				float[][] frames = deform.getVertices();
				float[][] remapped = new float[frames.length][];
				for (int frame = 0; frame < frames.length; frame++) {
					if (frames[frame] == null || frames[frame].length != deformLength(current)
						|| !areFinite(frames[frame]))
						throw new IllegalArgumentException("动画 " + animation.getName()
							+ " 的 Deform 顶点数据不完整");
					remapped[frame] = reorderDeformVertices(current, frames[frame], order);
				}
				deformTimelines.add(deform);
				deformFrames.add(remapped);
			}
		}

		for (int timelineIndex = 0; timelineIndex < deformTimelines.size(); timelineIndex++) {
			Animation.DeformTimeline deform = deformTimelines.get(timelineIndex);
			float[][] frames = deformFrames.get(timelineIndex);
			for (int frame = 0; frame < frames.length; frame++)
				deform.setFrame(frame, deform.getFrames()[frame], frames[frame]);
		}
		applyMeshGeometryData(mesh, reordered);
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	private static boolean sharesMeshGeometry(VertexAttachment attachment, MeshAttachment source) {
		return attachment == source || attachment instanceof MeshAttachment mesh && mesh.getSourceMesh() == source;
	}

	/** Captures source geometry and all direct or independent Linked Mesh Deform frames that share it. */
	public MeshTopologySnapshot snapshotMeshTopology(MeshAttachment mesh) {
		if (doc.data == null || mesh == null || mesh.getSourceMesh() != null || !containsAttachment(mesh))
			throw new IllegalArgumentException("Mesh topology target is invalid");
		MeshGeometryState geometry = meshGeometryState(mesh);
		String reason = validateMeshGeometry(mesh, geometry);
		if (reason != null) throw new IllegalArgumentException(reason);
		return new MeshTopologySnapshot(mesh, geometry, captureMeshDeforms(mesh, deformLength(geometry)));
	}

	/** Inserts one vertex and migrates every shared Deform frame using the same barycentric interpolation. */
	public boolean insertMeshVertexWithDeform(MeshAttachment mesh, int triangleOffset,
		float weightA, float weightB, float weightC) {
		MeshTopologySnapshot before = snapshotMeshTopology(mesh);
		MeshGeometryState geometry = before.geometry;
		MeshGeometryState inserted = insertMeshVertex(geometry, triangleOffset, weightA, weightB, weightC);
		if (inserted.sameContent(geometry)) return false;
		String reason = validateMeshGeometry(mesh, inserted, false);
		if (reason != null) throw new IllegalArgumentException(reason);

		float[] barycentric = normalizedBarycentricWeights(weightA, weightB, weightC);
		int a = Short.toUnsignedInt(geometry.triangles()[triangleOffset]);
		int b = Short.toUnsignedInt(geometry.triangles()[triangleOffset + 1]);
		int c = Short.toUnsignedInt(geometry.triangles()[triangleOffset + 2]);
		List<MeshDeformState> migrated = new ArrayList<>(before.deforms.size());
		for (MeshDeformState deform : before.deforms) {
			float[][] vertices = new float[deform.vertices.length][];
			for (int frame = 0; frame < vertices.length; frame++)
				vertices[frame] = insertDeformVertex(geometry, inserted, deform.vertices[frame],
					a, b, c, barycentric);
			migrated.add(new MeshDeformState(deform.timeline, deform.times.clone(), vertices));
		}
		applyMeshTopologyState(mesh, inserted, migrated);
		return true;
	}

	/** Removes logical vertices and matching Deform value blocks from every attachment sharing the source geometry. */
	public boolean removeMeshVerticesWithDeform(MeshAttachment mesh, int[] removedVertices) {
		MeshTopologySnapshot before = snapshotMeshTopology(mesh);
		MeshGeometryState geometry = before.geometry;
		MeshGeometryState removed = removeMeshVertices(geometry, removedVertices);
		if (removed.sameContent(geometry)) return false;
		String reason = validateMeshGeometry(mesh, removed, false);
		if (reason != null) throw new IllegalArgumentException(reason);

		boolean[] removedFlags = new boolean[geometry.vertexCount()];
		int removedCount = 0;
		for (int vertex : removedVertices) {
			if (!removedFlags[vertex]) {
				removedFlags[vertex] = true;
				removedCount++;
			}
		}
		List<MeshDeformState> migrated = new ArrayList<>(before.deforms.size());
		for (MeshDeformState deform : before.deforms) {
			float[][] vertices = new float[deform.vertices.length][];
			for (int frame = 0; frame < vertices.length; frame++)
				vertices[frame] = removeDeformVertices(geometry, removed, deform.vertices[frame],
					removedFlags, removedCount);
			migrated.add(new MeshDeformState(deform.timeline, deform.times.clone(), vertices));
		}
		applyMeshTopologyState(mesh, removed, migrated);
		return true;
	}

	/** Welds coincident vertices and migrates every Deform frame to the surviving representatives. */
	public MeshWeldResult weldMeshVerticesWithDeform(MeshAttachment mesh, SlotData slot, float tolerance) {
		MeshTopologySnapshot before = snapshotMeshTopology(mesh);
		MeshGeometryState geometry = before.geometry;
		float[] setupVertices = geometry.weighted()
			? unweightMeshVertices(geometry.bones, geometry.vertices, geometry.vertexCount(), slot)
			: geometry.vertices;
		MeshWeldResult result = weldMeshVertices(geometry, setupVertices, tolerance);
		if (result.weldedVertices == 0) return result;
		String reason = validateMeshGeometry(mesh, result.geometry, false);
		if (reason != null) throw new IllegalArgumentException(reason);
		List<MeshDeformState> migrated = new ArrayList<>(before.deforms.size());
		for (MeshDeformState deform : before.deforms) {
			float[][] vertices = new float[deform.vertices.length][];
			for (int frame = 0; frame < vertices.length; frame++)
				vertices[frame] = selectWeldedDeformVertices(geometry, result.geometry,
					deform.vertices[frame], result.newToOld);
			migrated.add(new MeshDeformState(deform.timeline, deform.times.clone(), vertices));
		}
		applyMeshTopologyState(mesh, result.geometry, migrated);
		return result;
	}

	/** Restores a previously captured topology snapshot, including all Deform frames, for undo/redo. */
	public void restoreMeshTopology(MeshTopologySnapshot snapshot) {
		if (snapshot == null || snapshot.mesh == null || snapshot.mesh.getSourceMesh() != null
			|| !containsAttachment(snapshot.mesh))
			throw new IllegalArgumentException("Mesh topology snapshot is no longer valid");
		String reason = validateMeshGeometry(snapshot.mesh, snapshot.geometry, false);
		if (reason != null) throw new IllegalArgumentException(reason);
		applyMeshTopologyState(snapshot.mesh, snapshot.geometry, snapshot.deforms);
	}

	private List<MeshDeformState> captureMeshDeforms(MeshAttachment mesh, int expectedLength) {
		return captureDeforms(mesh, expectedLength, true);
	}

	private List<MeshDeformState> captureAttachmentDeforms(VertexAttachment attachment, int expectedLength) {
		return captureDeforms(attachment, expectedLength, false);
	}

	private List<MeshDeformState> captureDeforms(VertexAttachment attachment, int expectedLength,
		boolean includeSharedMeshGeometry) {
		List<MeshDeformState> result = new ArrayList<>();
		for (Animation animation : doc.data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (!(timeline instanceof Animation.DeformTimeline deform)) continue;
				boolean matches = includeSharedMeshGeometry && attachment instanceof MeshAttachment mesh
					? sharesMeshGeometry(deform.getAttachment(), mesh)
					: deform.getAttachment() == attachment;
				if (!matches) continue;
				float[] times = deform.getFrames();
				float[][] sourceVertices = deform.getVertices();
				if (times == null || sourceVertices == null || times.length != deform.getFrameCount()
					|| sourceVertices.length != deform.getFrameCount())
					throw new IllegalArgumentException("Animation " + animation.getName()
						+ " has incomplete Deform frame data");
				float[][] vertices = new float[sourceVertices.length][];
				for (int frame = 0; frame < sourceVertices.length; frame++) {
					if (!Float.isFinite(times[frame]) || times[frame] < 0f || sourceVertices[frame] == null
						|| sourceVertices[frame].length != expectedLength || !areFinite(sourceVertices[frame]))
						throw new IllegalArgumentException("Animation " + animation.getName()
							+ " has invalid Deform data at frame " + frame);
					vertices[frame] = sourceVertices[frame].clone();
				}
				result.add(new MeshDeformState(deform, times.clone(), vertices));
			}
		}
		return result;
	}

	/** Keeps JSON-visible offsets unchanged when unweighted setup vertices move. */
	private static List<MeshDeformState> rebaseUnweightedDeforms(List<MeshDeformState> deforms,
		float[] oldSetup, float[] newSetup) {
		if (deforms == null || oldSetup == null || newSetup == null || oldSetup.length != newSetup.length
			|| !areFinite(oldSetup) || !areFinite(newSetup))
			throw new IllegalArgumentException("Unweighted Deform setup geometry is invalid");
		List<MeshDeformState> rebased = new ArrayList<>(deforms.size());
		for (MeshDeformState deform : deforms) {
			float[][] vertices = new float[deform.vertices.length][];
			for (int frame = 0; frame < vertices.length; frame++) {
				float[] source = deform.vertices[frame];
				if (source == null || source.length != oldSetup.length || !areFinite(source))
					throw new IllegalArgumentException("Unweighted Deform frame data is invalid");
				float[] target = new float[source.length];
				for (int value = 0; value < source.length; value++) {
					target[value] = source[value] - oldSetup[value] + newSetup[value];
					if (!Float.isFinite(target[value]))
						throw new IllegalArgumentException("Unweighted Deform rebase produced a non-finite value");
				}
				vertices[frame] = target;
			}
			rebased.add(new MeshDeformState(deform.timeline, deform.times.clone(), vertices));
		}
		return rebased;
	}

	/** Maps each new weighted Deform pair to the same logical vertex and bone in the previous layout. */
	private static int[] weightedDeformRemap(int[] oldBones, int[] newBones, int vertexCount) {
		if (oldBones == null || newBones == null || vertexCount < 0) return null;
		int oldCursor = 0, newCursor = 0, oldPair = 0, newPair = 0;
		int[] remap = new int[Math.max(0, newBones.length - vertexCount)];
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			if (oldCursor >= oldBones.length || newCursor >= newBones.length) return null;
			int oldCount = oldBones[oldCursor++], newCount = newBones[newCursor++];
			if (oldCount <= 0 || oldCount != newCount || oldCursor + oldCount > oldBones.length
				|| newCursor + newCount > newBones.length || newPair + newCount > remap.length) return null;
			Set<Integer> oldSeen = new HashSet<>(), newSeen = new HashSet<>();
			for (int influence = 0; influence < oldCount; influence++)
				if (!oldSeen.add(oldBones[oldCursor + influence])) return null;
			for (int newInfluence = 0; newInfluence < newCount; newInfluence++) {
				int boneIndex = newBones[newCursor + newInfluence];
				if (!newSeen.add(boneIndex)) return null;
				int oldInfluence = -1;
				for (int candidate = 0; candidate < oldCount; candidate++) {
					if (oldBones[oldCursor + candidate] == boneIndex) {
						oldInfluence = candidate;
						break;
					}
				}
				if (oldInfluence < 0) return null;
				remap[newPair++] = oldPair + oldInfluence;
			}
			oldCursor += oldCount;
			newCursor += newCount;
			oldPair += oldCount;
		}
		return oldCursor == oldBones.length && newCursor == newBones.length && newPair == remap.length
			? remap : null;
	}

	private static List<MeshDeformState> remapWeightedDeforms(List<MeshDeformState> deforms, int[] remap) {
		if (deforms == null || remap == null) throw new IllegalArgumentException("Weighted Deform layout is invalid");
		List<MeshDeformState> migrated = new ArrayList<>(deforms.size());
		for (MeshDeformState deform : deforms) {
			float[][] vertices = new float[deform.vertices.length][];
			for (int frame = 0; frame < vertices.length; frame++) {
				float[] source = deform.vertices[frame];
				if (source == null || source.length != remap.length * 2 || !areFinite(source))
					throw new IllegalArgumentException("Weighted Deform frame data is invalid");
				float[] target = new float[source.length];
				for (int newPair = 0; newPair < remap.length; newPair++) {
					int oldPair = remap[newPair];
					if (oldPair < 0 || oldPair >= remap.length)
						throw new IllegalArgumentException("Weighted Deform influence mapping is invalid");
					target[newPair * 2] = source[oldPair * 2];
					target[newPair * 2 + 1] = source[oldPair * 2 + 1];
				}
				vertices[frame] = target;
			}
			migrated.add(new MeshDeformState(deform.timeline, deform.times.clone(), vertices));
		}
		return migrated;
	}

	private static void applyDeformFrames(List<MeshDeformState> deforms) {
		for (MeshDeformState deform : deforms)
			for (int frame = 0; frame < deform.vertices.length; frame++)
				deform.timeline.setFrame(frame, deform.times[frame], deform.vertices[frame].clone());
	}

	private void applyMeshTopologyState(MeshAttachment mesh, MeshGeometryState geometry,
		List<MeshDeformState> deforms) {
		int expectedLength = deformLength(geometry);
		List<Animation.DeformTimeline> current = new ArrayList<>();
		for (Animation animation : doc.data.getAnimations())
			for (Animation.Timeline timeline : animation.getTimelines())
				if (timeline instanceof Animation.DeformTimeline deform
					&& sharesMeshGeometry(deform.getAttachment(), mesh)) current.add(deform);
		if (deforms == null || current.size() != deforms.size())
			throw new IllegalStateException("Mesh Deform tracks changed after the topology snapshot");
		IdentityHashMap<Animation.DeformTimeline, Boolean> seen = new IdentityHashMap<>();
		for (MeshDeformState deform : deforms) {
			if (deform == null || !current.contains(deform.timeline) || seen.put(deform.timeline, Boolean.TRUE) != null
				|| deform.times == null || deform.vertices == null
				|| deform.times.length != deform.timeline.getFrameCount()
				|| deform.vertices.length != deform.timeline.getFrameCount())
				throw new IllegalStateException("Mesh Deform topology snapshot is incomplete");
			for (int frame = 0; frame < deform.vertices.length; frame++)
				if (!Float.isFinite(deform.times[frame]) || deform.times[frame] < 0f
					|| deform.vertices[frame] == null || deform.vertices[frame].length != expectedLength
					|| !areFinite(deform.vertices[frame]))
					throw new IllegalStateException("Mesh Deform topology snapshot has invalid frame data");
		}

		applyMeshGeometryData(mesh, geometry);
		applyDeformFrames(deforms);
		doc.markDirty();
		rebuildSkeleton();
	}

	private static int deformLength(MeshGeometryState geometry) {
		return geometry.bones == null ? geometry.vertices.length : geometry.vertices.length / 3 * 2;
	}

	private static float[] reorderDeformVertices(MeshGeometryState geometry, float[] values,
		MeshVertexOrder order) {
		if (geometry == null || values == null || order == null
			|| geometry.vertexCount() != order.vertexCount() || values.length != deformLength(geometry))
			throw new IllegalArgumentException("Mesh Deform 顶点重排数据不完整");
		if (geometry.bones == null)
			return reorderVertexPairs(values, order.newToOld, geometry.vertexCount());

		int[][] offsets = weightedDeformOffsets(geometry);
		float[] result = new float[values.length];
		int output = 0;
		for (int newVertex = 0; newVertex < order.vertexCount(); newVertex++) {
			int oldVertex = order.newToOld[newVertex];
			for (int source : offsets[oldVertex]) {
				result[output++] = values[source];
				result[output++] = values[source + 1];
			}
		}
		if (output != result.length)
			throw new IllegalArgumentException("Mesh 加权 Deform 顶点重排数据不完整");
		return result;
	}

	private static float[] normalizedBarycentricWeights(float weightA, float weightB, float weightC) {
		weightA = Math.max(0f, weightA);
		weightB = Math.max(0f, weightB);
		weightC = Math.max(0f, weightC);
		float sum = weightA + weightB + weightC;
		if (!(sum > 0f) || !Float.isFinite(sum))
			throw new IllegalArgumentException("Mesh insertion barycentric coordinates are invalid");
		return new float[] {weightA / sum, weightB / sum, weightC / sum};
	}

	private static float[] insertDeformVertex(MeshGeometryState before, MeshGeometryState after,
		float[] frame, int a, int b, int c, float[] weights) {
		int newLength = deformLength(after);
		if (frame == null || frame.length != deformLength(before) || !areFinite(frame))
			throw new IllegalArgumentException("Mesh Deform insertion source is invalid");
		float[] result = Arrays.copyOf(frame, newLength);
		if (before.bones == null) {
			result[frame.length] = barycentricValue(frame, a, b, c, weights[0], weights[1], weights[2], 0);
			result[frame.length + 1] = barycentricValue(frame, a, b, c, weights[0], weights[1], weights[2], 1);
			return result;
		}

		ArrayList<ArrayList<MeshWeightInfluence>> beforeInfluences = decodeMeshWeights(before, 0);
		ArrayList<ArrayList<MeshWeightInfluence>> afterInfluences = decodeMeshWeights(after, 0);
		int[][] offsets = weightedDeformOffsets(before);
		int[] corners = {a, b, c};
		ArrayList<MeshWeightInfluence> inserted = afterInfluences.get(after.vertexCount() - 1);
		for (int insertedIndex = 0; insertedIndex < inserted.size(); insertedIndex++) {
			int boneIndex = inserted.get(insertedIndex).boneIndex;
			float sum = 0f, x = 0f, y = 0f;
			for (int corner = 0; corner < corners.length; corner++) {
				ArrayList<MeshWeightInfluence> influences = beforeInfluences.get(corners[corner]);
				float influenceSum = 0f;
				for (MeshWeightInfluence influence : influences) influenceSum += influence.weight;
				for (int influenceIndex = 0; influenceIndex < influences.size(); influenceIndex++) {
					MeshWeightInfluence influence = influences.get(influenceIndex);
					if (influence.boneIndex != boneIndex) continue;
					float contribution = weights[corner] * influence.weight / influenceSum;
					int value = offsets[corners[corner]][influenceIndex];
					sum += contribution;
					x += frame[value] * contribution;
					y += frame[value + 1] * contribution;
				}
			}
			if (!(sum > 0.000001f) || !Float.isFinite(sum))
				throw new IllegalArgumentException("Mesh weighted Deform insertion cannot resolve an influence");
			int output = frame.length + insertedIndex * 2;
			result[output] = x / sum;
			result[output + 1] = y / sum;
		}
		return result;
	}

	private static int[][] weightedDeformOffsets(MeshGeometryState geometry) {
		int[][] result = new int[geometry.vertexCount()][];
		int boneCursor = 0, deformCursor = 0;
		for (int vertex = 0; vertex < result.length; vertex++) {
			int influenceCount = geometry.bones[boneCursor++];
			result[vertex] = new int[influenceCount];
			for (int influence = 0; influence < influenceCount; influence++) {
				boneCursor++;
				result[vertex][influence] = deformCursor;
				deformCursor += 2;
			}
		}
		return result;
	}

	private static float[] removeDeformVertices(MeshGeometryState before, MeshGeometryState after,
		float[] frame, boolean[] removed, int removedCount) {
		if (frame == null || frame.length != deformLength(before) || !areFinite(frame))
			throw new IllegalArgumentException("Mesh Deform removal source is invalid");
		if (before.bones == null) return removeVertexPairs(frame, removed, removedCount);
		float[] result = new float[deformLength(after)];
		int boneCursor = 0, input = 0, output = 0;
		for (int vertex = 0; vertex < before.vertexCount(); vertex++) {
			int influenceCount = before.bones[boneCursor++];
			boneCursor += influenceCount;
			int valueCount = influenceCount * 2;
			if (!removed[vertex]) {
				System.arraycopy(frame, input, result, output, valueCount);
				output += valueCount;
			}
			input += valueCount;
		}
		if (input != frame.length || output != result.length)
			throw new IllegalArgumentException("Mesh weighted Deform removal layout is invalid");
		return result;
	}

	private static float[] selectWeldedDeformVertices(MeshGeometryState before, MeshGeometryState after,
		float[] frame, int[] newToOld) {
		if (frame == null || frame.length != deformLength(before) || newToOld == null
			|| newToOld.length != after.vertexCount() || !areFinite(frame))
			throw new IllegalArgumentException("Mesh 焊接 Deform 数据无效");
		if (before.bones == null) {
			float[] result = new float[newToOld.length * 2];
			for (int vertex = 0; vertex < newToOld.length; vertex++)
				System.arraycopy(frame, newToOld[vertex] * 2, result, vertex * 2, 2);
			return result;
		}
		int[][] offsets = weightedDeformOffsets(before);
		float[] result = new float[deformLength(after)];
		int output = 0;
		for (int oldVertex : newToOld) for (int source : offsets[oldVertex]) {
			result[output++] = frame[source];
			result[output++] = frame[source + 1];
		}
		if (output != result.length) throw new IllegalArgumentException("Mesh 焊接权重布局不一致");
		return result;
	}

	/** Copies normalized bone weights by nearest UV while preserving the target setup position. */
	public MeshGeometryState copyMeshWeights(MeshGeometryState target, SlotData targetSlot,
		MeshGeometryState source) {
		if (doc.data == null || target == null || source == null || targetSlot == null
			|| !doc.data.getSlots().contains(targetSlot, true) || !source.weighted()
			|| target.vertexCount() == 0 || source.vertexCount() == 0
			|| target.regionUVs == null || source.regionUVs == null
			|| (target.regionUVs.length & 1) != 0 || (source.regionUVs.length & 1) != 0
			|| !areFinite(target.regionUVs) || !areFinite(source.regionUVs))
			throw new IllegalArgumentException("Mesh 权重复制参数无效");
		String targetReason = validateMeshVertices(target.bones, target.vertices, target.vertexCount());
		String sourceReason = validateMeshVertices(source.bones, source.vertices, source.vertexCount());
		if (targetReason != null) throw new IllegalArgumentException(targetReason);
		if (sourceReason != null) throw new IllegalArgumentException(sourceReason);
		float[] targetLocal = target.weighted()
			? unweightMeshVertices(target.bones, target.vertices, target.vertexCount(), targetSlot)
			: target.vertices;
		ArrayList<ArrayList<MeshWeightInfluence>> sourceWeights = decodeMeshWeights(source, 0);
		Skeleton setupSkeleton = new Skeleton(doc.data);
		Skin activeSkin = findAttachmentSkinForSlot(targetSlot.getIndex());
		if (activeSkin == null) activeSkin = doc.data.getDefaultSkin();
		if (activeSkin != null) { setupSkeleton.setSkin(activeSkin); setupSkeleton.setupPoseSlots(); }
		setupSkeleton.updateWorldTransform(Physics.pose);
		BonePose slotPose = setupSkeleton.getSlots().get(targetSlot.getIndex()).getBone().getAppliedPose();
		ArrayList<ArrayList<MeshWeightInfluence>> copied = new ArrayList<>(target.vertexCount());
		Vector2 point = new Vector2();
		for (int vertex = 0; vertex < target.vertexCount(); vertex++) {
			int sourceVertex = nearestUvVertex(target.regionUVs, vertex, source.regionUVs);
			ArrayList<MeshWeightInfluence> reference = sourceWeights.get(sourceVertex);
			float weightSum = 0f;
			for (MeshWeightInfluence influence : reference) weightSum += influence.weight;
			if (!(weightSum > 0f) || !Float.isFinite(weightSum))
				throw new IllegalArgumentException("源 Mesh 权重总和无效");
			float localX = targetLocal[vertex * 2], localY = targetLocal[vertex * 2 + 1];
			float worldX = localX * slotPose.getA() + localY * slotPose.getB() + slotPose.getWorldX();
			float worldY = localX * slotPose.getC() + localY * slotPose.getD() + slotPose.getWorldY();
			ArrayList<MeshWeightInfluence> targetWeights = new ArrayList<>(reference.size());
			for (MeshWeightInfluence influence : reference) {
				point.set(worldX, worldY);
				setupSkeleton.getBones().get(influence.boneIndex).getAppliedPose().worldToLocal(point);
				if (!Float.isFinite(point.x) || !Float.isFinite(point.y))
					throw new IllegalArgumentException("复制权重时骨骼变换不可逆");
				targetWeights.add(new MeshWeightInfluence(influence.boneIndex, point.x, point.y,
					influence.weight / weightSum));
			}
			copied.add(targetWeights);
		}
		MeshWeightEncoding encoding = encodeMeshWeights(copied);
		return new MeshGeometryState(target.color, target.path, encoding.bones, encoding.vertices,
			target.regionUVs, target.triangles, target.hullLength, target.edges, target.width, target.height);
	}

	private static int nearestUvVertex(float[] targetUvs, int targetVertex, float[] sourceUvs) {
		if (targetUvs == null || sourceUvs == null || (sourceUvs.length & 1) != 0)
			throw new IllegalArgumentException("Mesh UV 数据无效");
		float u = targetUvs[targetVertex * 2], v = targetUvs[targetVertex * 2 + 1];
		int nearest = 0;
		float best = Float.POSITIVE_INFINITY;
		for (int source = 0; source < sourceUvs.length / 2; source++) {
			float du = u - sourceUvs[source * 2], dv = v - sourceUvs[source * 2 + 1];
			float distance = du * du + dv * dv;
			if (distance < best) { best = distance; nearest = source; }
		}
		return nearest;
	}

	/** Applies validated data and refreshes all linked meshes that share this source mesh. */
	public boolean updateMeshGeometry(MeshAttachment mesh, MeshGeometryState state) {
		if (validateMeshGeometry(mesh, state) != null || sameMeshGeometry(mesh, state)) return false;
		MeshGeometryState before = meshGeometryState(mesh);
		List<MeshDeformState> rebased = null;
		if (before.bones == null && state.bones == null && !Arrays.equals(before.vertices, state.vertices)) {
			try {
				List<MeshDeformState> deforms = captureMeshDeforms(mesh, before.vertices.length);
				if (!deforms.isEmpty())
					rebased = rebaseUnweightedDeforms(deforms, before.vertices, state.vertices);
			} catch (IllegalArgumentException invalidDeform) {
				return false;
			}
		} else if (before.bones != null && state.bones != null && !Arrays.equals(before.bones, state.bones)) {
			try {
				List<MeshDeformState> deforms = captureMeshDeforms(mesh, before.vertices.length / 3 * 2);
				if (!deforms.isEmpty()) {
					int[] remap = weightedDeformRemap(before.bones, state.bones, before.vertexCount());
					if (remap == null) return false;
					rebased = remapWeightedDeforms(deforms, remap);
				}
			} catch (IllegalArgumentException invalidDeform) {
				return false;
			}
		}
		applyMeshGeometryData(mesh, state);
		if (rebased != null) applyDeformFrames(rebased);
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	/**
	 * Applies a temporary, validated viewport preview without marking the document dirty.
	 * The caller must either restore the previous state or commit through {@link #updateMeshGeometry}.
	 */
	public boolean applyMeshGeometryPreview(MeshAttachment mesh, MeshGeometryState state) {
		String reason = validateMeshGeometry(mesh, state);
		if (reason != null) throw new IllegalArgumentException(reason);
		boolean changed = !sameMeshGeometry(mesh, state);
		applyMeshGeometryData(mesh, state);
		return changed;
	}

	/** Commits geometry that was mutated in place for viewport preview without bypassing Deform migration. */
	public boolean commitMeshGeometryPreview(MeshAttachment mesh, MeshGeometryState before) {
		if (doc.data == null || mesh == null || before == null || mesh.getSourceMesh() != null
			|| !containsAttachment(mesh))
			throw new IllegalArgumentException("Mesh preview target is invalid");
		MeshGeometryState preview = meshGeometryState(mesh);
		if (before.sameContent(preview)) return false;
		String reason = validateMeshGeometry(mesh, preview);
		if (reason != null) {
			applyMeshGeometryData(mesh, before);
			rebuildSkeleton();
			throw new IllegalArgumentException(reason);
		}

		applyMeshGeometryData(mesh, before);
		if (!updateMeshGeometry(mesh, preview)) {
			applyMeshGeometryData(mesh, before);
			rebuildSkeleton();
			throw new IllegalStateException("Mesh preview could not be committed without losing Deform data");
		}
		return true;
	}

	private void applyMeshGeometryData(MeshAttachment mesh, MeshGeometryState state) {
		TextureRegion changedRegion = java.util.Objects.equals(mesh.getPath(), state.path()) ? null : findRegion(state.path());
		mesh.getColor().set(state.color());
		mesh.setPath(state.path());
		mesh.setBones(state.bones());
		mesh.setVertices(state.vertices());
		mesh.setWorldVerticesLength(state.vertexCount() * 2);
		mesh.setRegionUVs(state.regionUVs());
		mesh.setTriangles(state.triangles());
		mesh.setHullLength(state.hullLength());
		mesh.setEdges(state.edges());
		mesh.setWidth(state.width());
		mesh.setHeight(state.height());
		if (changedRegion != null) mesh.getSequence().getRegions()[0] = changedRegion;
		mesh.updateSequence();
		for (Skin skin : doc.data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (!(entry.getAttachment() instanceof MeshAttachment linked) || linked.getSourceMesh() != mesh) continue;
				linked.setSourceMesh(mesh);
				linked.updateSequence();
			}
		}
	}

	private static boolean sameMeshGeometry(MeshAttachment mesh, MeshGeometryState state) {
		return meshGeometryState(mesh).sameContent(state);
	}

	/** Creates a default non-image attachment without overwriting an existing skin entry. */
	public Attachment createStandardAttachment(Skin skin, SlotData slot, String placeholder,
		StandardAttachmentType type) {
		if (getStandardAttachmentCreationBlockReason(skin, slot, placeholder) != null || type == null) return null;
		Attachment attachment;
		switch (type) {
		case BOUNDING_BOX -> {
			BoundingBoxAttachment box = new BoundingBoxAttachment(placeholder);
			setDefaultPolygon(box);
			attachment = box;
		}
		case PATH -> {
			PathAttachment path = new PathAttachment(placeholder);
			path.setClosed(false);
			path.setConstantSpeed(true);
			path.setBones(null);
			path.setVertices(new float[] {-100f, 0f, -75f, 0f, -25f, 0f,
				25f, 0f, 75f, 0f, 100f, 0f});
			path.setWorldVerticesLength(12);
			path.setLengths(new float[] {150f, 300f});
			attachment = path;
		}
		case POINT -> attachment = new PointAttachment(placeholder);
		case CLIPPING -> {
			ClippingAttachment clipping = new ClippingAttachment(placeholder);
			setDefaultPolygon(clipping);
			clipping.setEndSlot(doc.data.getSlots().peek());
			attachment = clipping;
		}
		default -> throw new IllegalStateException("未知标准附件类型：" + type);
		}
		skin.setAttachment(slot.getIndex(), placeholder, attachment);
		slot.setAttachmentName(placeholder);
		doc.markDirty();
		rebuildSkeleton();
		return attachment;
	}

	private static void setDefaultPolygon(VertexAttachment attachment) {
		attachment.setBones(null);
		attachment.setVertices(new float[] {-50f, -50f, 50f, -50f, 50f, 50f, -50f, 50f});
		attachment.setWorldVerticesLength(8);
	}

	public String getStandardAttachmentCreationBlockReason(Skin skin, SlotData slot, String placeholder) {
		if (doc.data == null || skin == null || slot == null || placeholder == null || placeholder.isBlank()
			|| !doc.data.getSkins().contains(skin, true) || !doc.data.getSlots().contains(slot, true))
			return "附件目标无效";
		Attachment current = skin.getAttachment(slot.getIndex(), placeholder);
		return current == null ? null : "当前皮肤的占位名已用于 " + current.getClass().getSimpleName();
	}

	public static StandardAttachmentState standardAttachmentState(Attachment attachment) {
		if (attachment instanceof BoundingBoxAttachment box)
			return new StandardAttachmentState(StandardAttachmentType.BOUNDING_BOX, box.getColor(),
				box.getWorldVerticesLength() / 2, box.getBones(), box.getVertices(), false, false, null,
				0f, 0f, 0f, null, false, false);
		if (attachment instanceof PathAttachment path)
			return new StandardAttachmentState(StandardAttachmentType.PATH, path.getColor(),
				path.getWorldVerticesLength() / 2, path.getBones(), path.getVertices(), path.getClosed(),
				path.getConstantSpeed(), path.getLengths(), 0f, 0f, 0f, null, false, false);
		if (attachment instanceof PointAttachment point)
			return new StandardAttachmentState(StandardAttachmentType.POINT, point.getColor(), 0,
				null, null, false, false, null, point.getX(), point.getY(), point.getRotation(),
				null, false, false);
		if (attachment instanceof ClippingAttachment clipping)
			return new StandardAttachmentState(StandardAttachmentType.CLIPPING, clipping.getColor(),
				clipping.getWorldVerticesLength() / 2, clipping.getBones(), clipping.getVertices(), false,
				false, null, 0f, 0f, 0f, clipping.getEndSlot(), clipping.getConvex(), clipping.getInverse());
		throw new IllegalArgumentException("附件不是可编辑的标准非图片类型");
	}

	/** Returns null when a complete standard attachment state can be applied without invalidating Deform data. */
	public String validateStandardAttachment(Attachment attachment, StandardAttachmentState state) {
		if (doc.data == null || attachment == null || !containsAttachment(attachment))
			return "附件不在当前项目中";
		StandardAttachmentType actualType = standardAttachmentType(attachment);
		if (actualType == null || state == null || state.type() != actualType || state.color() == null)
			return "附件类型或属性数据不完整";
		if (!isUnitColor(state.color())) return "附件颜色必须是 0 到 1 的有限数值";
		if (actualType == StandardAttachmentType.POINT) {
			return areFinite(state.x(), state.y(), state.rotation()) ? null : "Point 坐标和旋转必须是有限数值";
		}

		int vertexCount = state.vertexCount();
		if (vertexCount < 3 || vertexCount > 65536) return "附件顶点数必须在 3 到 65536 之间";
		if (actualType == StandardAttachmentType.PATH && (vertexCount < 6 || vertexCount % 3 != 0))
			return "Path 顶点数必须是不小于 6 的 3 的倍数";
		String vertexReason = validateStandardVertices(state.bones(), state.vertices(), vertexCount);
		if (vertexReason != null) return vertexReason;
		if (actualType == StandardAttachmentType.PATH) {
			float[] lengths = state.lengths();
			if (lengths == null || lengths.length != vertexCount / 3)
				return "Path lengths 数量必须等于顶点数除以 3";
			float previous = 0f;
			for (float length : lengths) {
				if (!Float.isFinite(length) || length <= 0f || length < previous)
					return "Path lengths 必须是递增的正有限数值";
				previous = length;
			}
		}
		if (actualType == StandardAttachmentType.CLIPPING && state.endSlot() != null
			&& !doc.data.getSlots().contains(state.endSlot(), true)) return "Clipping 结束槽不在当前项目中";
		return validateStandardDeformCompatibility((VertexAttachment)attachment, state);
	}

	private String validateStandardVertices(int[] bones, float[] vertices, int vertexCount) {
		if (vertices == null) return "附件顶点数据不能为空";
		if (!areFinite(vertices)) return "附件顶点和权重必须是有限数值";
		if (bones == null)
			return vertices.length == vertexCount * 2 ? null : "非加权附件每个顶点必须有 X、Y 两个数值";
		int boneCursor = 0, valueCursor = 0;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			if (boneCursor >= bones.length) return "加权附件的骨骼数据不完整";
			int influenceCount = bones[boneCursor++];
			if (influenceCount <= 0) return "每个加权顶点必须绑定至少一个骨骼";
			Set<Integer> seen = new HashSet<>();
			float weightSum = 0f;
			for (int influence = 0; influence < influenceCount; influence++) {
				if (boneCursor >= bones.length || valueCursor + 2 >= vertices.length)
					return "加权附件的顶点影响数据不完整";
				int boneIndex = bones[boneCursor++];
				if (boneIndex < 0 || boneIndex >= doc.data.getBones().size)
					return "加权附件引用了不存在的骨骼索引 " + boneIndex;
				if (!seen.add(boneIndex)) return "同一顶点不能重复绑定同一骨骼";
				float weight = vertices[valueCursor + 2];
				if (weight < 0f) return "附件权重不能小于 0";
				weightSum += weight;
				valueCursor += 3;
			}
			if (!(weightSum > 0f)) return "每个加权顶点的权重总和必须大于 0";
		}
		return boneCursor == bones.length && valueCursor == vertices.length
			? null : "加权附件包含多余的骨骼或顶点数据";
	}

	private String validateStandardDeformCompatibility(VertexAttachment attachment,
		StandardAttachmentState state) {
		int newDeformLength = state.bones() == null ? state.vertices().length : state.vertices().length / 3 * 2;
		boolean weightedChanged = (attachment.getBones() == null) != (state.bones() == null);
		boolean influenceOrderChanged = !weightedChanged && attachment.getBones() != null
			&& !Arrays.equals(attachment.getBones(), state.bones());
		int[] influenceRemap = null;
		for (Animation animation : doc.data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (!(timeline instanceof Animation.DeformTimeline deform)
					|| deform.getAttachment() != attachment) continue;
				if (weightedChanged) return "动画 " + animation.getName() + " 的 Deform 轨道阻止切换附件权重模式";
				for (float[] frameVertices : deform.getVertices())
					if (frameVertices == null || frameVertices.length != newDeformLength)
						return "动画 " + animation.getName() + " 的 Deform 顶点数量与新几何不兼容";
				if (influenceOrderChanged) {
					if (influenceRemap == null)
						influenceRemap = weightedDeformRemap(attachment.getBones(), state.bones(), state.vertexCount());
					if (influenceRemap == null)
						return "动画 " + animation.getName()
							+ " 的 Deform 轨道要求每个附件顶点保留相同的骨骼影响集合";
				}
			}
		}
		return null;
	}

	public boolean updateStandardAttachment(Attachment attachment, StandardAttachmentState state) {
		if (validateStandardAttachment(attachment, state) != null) return false;
		StandardAttachmentState before = standardAttachmentState(attachment);
		if (before.sameContent(state)) return false;
		List<MeshDeformState> rebased = null;
		if (attachment instanceof VertexAttachment vertexAttachment && before.bones == null && state.bones == null
			&& !Arrays.equals(before.vertices, state.vertices)) {
			try {
				List<MeshDeformState> deforms = captureAttachmentDeforms(vertexAttachment, before.vertices.length);
				if (!deforms.isEmpty())
					rebased = rebaseUnweightedDeforms(deforms, before.vertices, state.vertices);
			} catch (IllegalArgumentException invalidDeform) {
				return false;
			}
		} else if (attachment instanceof VertexAttachment vertexAttachment && before.bones != null
			&& state.bones != null && !Arrays.equals(before.bones, state.bones)) {
			try {
				List<MeshDeformState> deforms = captureAttachmentDeforms(vertexAttachment,
					before.vertices.length / 3 * 2);
				if (!deforms.isEmpty()) {
					int[] remap = weightedDeformRemap(before.bones, state.bones, before.vertexCount);
					if (remap == null) return false;
					rebased = remapWeightedDeforms(deforms, remap);
				}
			} catch (IllegalArgumentException invalidDeform) {
				return false;
			}
		}
		applyStandardAttachmentData(attachment, state);
		if (rebased != null) applyDeformFrames(rebased);
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	private static void applyStandardAttachmentData(Attachment attachment, StandardAttachmentState state) {
		if (attachment instanceof PointAttachment point) {
			point.getColor().set(state.color());
			point.setX(state.x());
			point.setY(state.y());
			point.setRotation(state.rotation());
		} else {
			VertexAttachment vertexAttachment = (VertexAttachment)attachment;
			vertexAttachment.setBones(state.bones());
			vertexAttachment.setVertices(state.vertices());
			vertexAttachment.setWorldVerticesLength(state.vertexCount() * 2);
			if (attachment instanceof BoundingBoxAttachment box) {
				box.getColor().set(state.color());
			} else if (attachment instanceof PathAttachment path) {
				path.getColor().set(state.color());
				path.setClosed(state.closed());
				path.setConstantSpeed(state.constantSpeed());
				path.setLengths(state.lengths());
			} else if (attachment instanceof ClippingAttachment clipping) {
				clipping.getColor().set(state.color());
				clipping.setEndSlot(state.endSlot());
				clipping.setConvex(state.convex());
				clipping.setInverse(state.inverse());
			}
		}
	}

	private static StandardAttachmentType standardAttachmentType(Attachment attachment) {
		if (attachment instanceof BoundingBoxAttachment) return StandardAttachmentType.BOUNDING_BOX;
		if (attachment instanceof PathAttachment) return StandardAttachmentType.PATH;
		if (attachment instanceof PointAttachment) return StandardAttachmentType.POINT;
		if (attachment instanceof ClippingAttachment) return StandardAttachmentType.CLIPPING;
		return null;
	}

	public static SequenceAttachmentState sequenceAttachmentState(Attachment attachment) {
		if (!(attachment instanceof com.esotericsoftware.spine.attachments.HasSequence hasSequence)
			|| hasSequence.getSequence() == null)
			throw new IllegalArgumentException("附件不是有效的 Region 或 Mesh");
		Sequence sequence = hasSequence.getSequence();
		return new SequenceAttachmentState(hasSequence.getPath(), sequence.getRegions().length,
			sequence.getStart(), sequence.getDigits(), sequence.getSetupIndex());
	}

	/** Returns null when the requested atlas-backed image sequence can be applied without data loss. */
	public String validateSequenceAttachment(Attachment attachment, SequenceAttachmentState state) {
		if (doc.data == null || !(attachment instanceof RegionAttachment || attachment instanceof MeshAttachment)
			|| !containsAttachment(attachment)) return "附件不属于当前项目中的 Region 或 Mesh";
		if (state == null || state.path() == null || state.path().trim().isEmpty()) return "Sequence 基础图片路径不能为空";
		if (state.count() < 1 || state.count() > 10000) return "Sequence 帧数必须介于 1 和 10000";
		if (state.digits() < 0 || state.digits() > 64) return "Sequence 补零位数必须介于 0 和 64";
		if (state.setupIndex() < 0 || state.setupIndex() >= state.count())
			return "Sequence Setup 索引必须落在帧数范围内";
		long finalNumber = (long)state.start() + state.count() - 1L;
		if (finalNumber < Integer.MIN_VALUE || finalNumber > Integer.MAX_VALUE)
			return "Sequence 起始编号与帧数组合超出整数范围";

		Sequence current = ((com.esotericsoftware.spine.attachments.HasSequence)attachment).getSequence();
		if (current == null || current.getRegions() == null || current.getRegions().length == 0)
			return "当前附件的 Sequence 数据无效";
		if (attachment instanceof MeshAttachment mesh && mesh.getRegionUVs() == null)
			return "当前 Mesh 缺少 Sequence 所需的 UV 数据";
		Sequence candidate = sequenceFor(state);
		for (int index = 0; index < state.count(); index++) {
			String path = candidate.getPath(state.path().trim(), index);
			if (findRegion(path) == null) return "图集中找不到 Sequence 第 " + index + " 帧：" + path;
		}
		return null;
	}

	/** Applies a validated sequence setup. The returned attachment may differ when its immutable Sequence size changes. */
	public Attachment updateSequenceAttachment(Attachment attachment, SequenceAttachmentState state) {
		if (validateSequenceAttachment(attachment, state) != null) return null;
		String path = state.path().trim();
		Sequence candidate = sequenceFor(new SequenceAttachmentState(path, state.count(), state.start(),
			state.digits(), state.setupIndex()));
		for (int index = 0; index < state.count(); index++)
			candidate.getRegions()[index] = findRegion(candidate.getPath(path, index));

		var hasSequence = (com.esotericsoftware.spine.attachments.HasSequence)attachment;
		Sequence current = hasSequence.getSequence();
		if (current.hasPathSuffix() && current.getRegions().length == state.count()) {
			if (sameSequenceContent(hasSequence, current, state, candidate.getRegions())) return attachment;
			hasSequence.setPath(path);
			current.setStart(state.start());
			current.setDigits(state.digits());
			current.setSetupIndex(state.setupIndex());
			System.arraycopy(candidate.getRegions(), 0, current.getRegions(), 0, state.count());
			hasSequence.updateSequence();
			doc.markDirty();
			rebuildSkeleton();
			return attachment;
		}

		Attachment replacement = copyAttachmentWithSequence(attachment, candidate, path);
		if (replacement == null || !replaceAttachmentOwners(attachment, replacement)) return null;
		doc.markDirty();
		rebuildSkeleton();
		return replacement;
	}

	private static Sequence sequenceFor(SequenceAttachmentState state) {
		Sequence sequence = new Sequence(state.count(), true);
		sequence.setStart(state.start());
		sequence.setDigits(state.digits());
		sequence.setSetupIndex(state.setupIndex());
		return sequence;
	}

	private static boolean sameSequenceContent(com.esotericsoftware.spine.attachments.HasSequence attachment,
		Sequence sequence, SequenceAttachmentState state, TextureRegion[] regions) {
		if (!java.util.Objects.equals(attachment.getPath(), state.path().trim())
			|| sequence.getStart() != state.start() || sequence.getDigits() != state.digits()
			|| sequence.getSetupIndex() != state.setupIndex()) return false;
		for (int index = 0; index < regions.length; index++)
			if (sequence.getRegions()[index] != regions[index]) return false;
		return true;
	}

	private static Attachment copyAttachmentWithSequence(Attachment attachment, Sequence sequence, String path) {
		Attachment replacement;
		if (attachment instanceof RegionAttachment region) {
			RegionAttachment copy = new RegionAttachment(region.getName(), sequence);
			copy.getColor().set(region.getColor());
			copy.setPath(path);
			copy.setX(region.getX());
			copy.setY(region.getY());
			copy.setScaleX(region.getScaleX());
			copy.setScaleY(region.getScaleY());
			copy.setRotation(region.getRotation());
			copy.setWidth(region.getWidth());
			copy.setHeight(region.getHeight());
			copy.updateSequence();
			replacement = copy;
		} else if (attachment instanceof MeshAttachment mesh) {
			MeshAttachment copy = new MeshAttachment(mesh.getName(), sequence);
			copy.getColor().set(mesh.getColor());
			copy.setPath(path);
			if (mesh.getSourceMesh() == null) {
				copy.setBones(mesh.getBones() == null ? null : mesh.getBones().clone());
				copy.setVertices(mesh.getVertices().clone());
				copy.setWorldVerticesLength(mesh.getWorldVerticesLength());
				copy.setRegionUVs(mesh.getRegionUVs().clone());
				copy.setTriangles(mesh.getTriangles().clone());
				copy.setHullLength(mesh.getHullLength());
				copy.setEdges(mesh.getEdges() == null ? null : mesh.getEdges().clone());
				copy.setWidth(mesh.getWidth());
				copy.setHeight(mesh.getHeight());
			} else {
				copy.setSourceMesh(mesh.getSourceMesh());
			}
			copy.updateSequence();
			replacement = copy;
		} else {
			return null;
		}
		Attachment timelineAttachment = attachment.getTimelineAttachment();
		replacement.setTimelineAttachment(timelineAttachment == attachment ? replacement : timelineAttachment);
		int[] timelineSlots = attachment.getTimelineSlots();
		replacement.setTimelineSlots(timelineSlots == null ? null : timelineSlots.clone());
		return replacement;
	}

	private boolean replaceAttachmentOwners(Attachment source, Attachment replacement) {
		ArrayList<AttachmentOwner> owners = new ArrayList<>();
		for (Skin skin : doc.data.getSkins()) collectAttachmentOwners(skin, source, owners);
		Skin defaultSkin = doc.data.getDefaultSkin();
		if (defaultSkin != null && !doc.data.getSkins().contains(defaultSkin, true))
			collectAttachmentOwners(defaultSkin, source, owners);
		if (owners.isEmpty()) return false;

		new AnimationEditor(doc).replaceAttachmentTimelineTarget(source, replacement);
		for (Skin skin : doc.data.getSkins()) replaceAttachmentReferences(skin, source, replacement);
		if (defaultSkin != null && !doc.data.getSkins().contains(defaultSkin, true))
			replaceAttachmentReferences(defaultSkin, source, replacement);
		for (AttachmentOwner owner : owners)
			owner.skin().setAttachment(owner.slotIndex(), owner.placeholder(), replacement);
		return true;
	}

	private static void replaceAttachmentReferences(Skin skin, Attachment source, Attachment replacement) {
		for (Skin.SkinEntry entry : skin.getAttachments()) {
			Attachment attachment = entry.getAttachment();
			if (attachment == source) continue;
			if (source instanceof MeshAttachment && replacement instanceof MeshAttachment replacementMesh
				&& attachment instanceof MeshAttachment mesh && mesh.getSourceMesh() == source)
				mesh.setSourceMesh(replacementMesh);
			if (attachment.getTimelineAttachment() == source)
				attachment.setTimelineAttachment(replacement);
		}
	}

	private static void collectAttachmentOwners(Skin skin, Attachment source, List<AttachmentOwner> owners) {
		for (Skin.SkinEntry entry : skin.getAttachments())
			if (entry.getAttachment() == source)
				owners.add(new AttachmentOwner(skin, entry.getSlotIndex(), entry.getPlaceholder()));
	}

	/** Updates an existing region attachment owned by this document. */
	public boolean updateRegionAttachment(RegionAttachment attachment, Color color, float x, float y,
		float scaleX, float scaleY, float rotation, float width, float height) {
		if (doc.data == null || attachment == null || color == null || !containsAttachment(attachment)
			|| !isUnitColor(color)
			|| !areFinite(x, y, scaleX, scaleY, rotation, width, height) || width <= 0f || height <= 0f)
			return false;
		if (sameColor(attachment.getColor(), color)
			&& Float.compare(attachment.getX(), x) == 0 && Float.compare(attachment.getY(), y) == 0
			&& Float.compare(attachment.getScaleX(), scaleX) == 0
			&& Float.compare(attachment.getScaleY(), scaleY) == 0
			&& Float.compare(attachment.getRotation(), rotation) == 0
			&& Float.compare(attachment.getWidth(), width) == 0
			&& Float.compare(attachment.getHeight(), height) == 0) return false;

		attachment.getColor().set(color);
		attachment.setX(x);
		attachment.setY(y);
		attachment.setScaleX(scaleX);
		attachment.setScaleY(scaleY);
		attachment.setRotation(rotation);
		attachment.setWidth(width);
		attachment.setHeight(height);
		attachment.updateSequence();
		doc.markDirty();
		rebuildSkeleton();
		return true;
	}

	private boolean containsAttachment(Attachment attachment) {
		for (Skin skin : doc.data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (entry.getAttachment() == attachment) return true;
			}
		}
		Skin defaultSkin = doc.data.getDefaultSkin();
		if (defaultSkin != null && !doc.data.getSkins().contains(defaultSkin, true)) {
			for (Skin.SkinEntry entry : defaultSkin.getAttachments()) {
				if (entry.getAttachment() == attachment) return true;
			}
		}
		return false;
	}

	private static boolean areFinite(float... values) {
		for (float value : values)
			if (!Float.isFinite(value)) return false;
		return true;
	}

	private String uniqueSkinName(String base) {
		String name = base == null || base.trim().isEmpty() ? "Skin" : base.trim();
		if (findSkinData(name) == null) return name;
		int n = 2;
		while (findSkinData(name + n) != null) n++;
		return name + n;
	}

	private String uniqueSlotName(String base) {
		String name = base == null || base.trim().isEmpty() ? "slot" : base.trim();
		if (doc.data.findSlot(name) == null) return name;
		int n = 2;
		while (doc.data.findSlot(name + n) != null) n++;
		return name + n;
	}

	private com.esotericsoftware.spine.Skin findSkinData(String name) {
		if (doc.data == null) return null;
		for (com.esotericsoftware.spine.Skin s : doc.data.getSkins())
			if (s.getName().equals(name)) return s;
		return null;
	}
}
