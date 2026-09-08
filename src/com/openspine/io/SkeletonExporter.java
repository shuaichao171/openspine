package com.openspine.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.IkConstraintPose;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PathConstraintPose;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintPose;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SliderPose;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.TransformConstraintPose;
import com.esotericsoftware.spine.Animation.BoneTimeline;
import com.esotericsoftware.spine.Animation.BoneTimeline1;
import com.esotericsoftware.spine.Animation.BoneTimeline2;
import com.esotericsoftware.spine.Animation.AttachmentTimeline;
import com.esotericsoftware.spine.Animation.CurveTimeline;
import com.esotericsoftware.spine.Animation.CurveTimeline1;
import com.esotericsoftware.spine.Animation.DeformTimeline;
import com.esotericsoftware.spine.Animation.DrawOrderFolderTimeline;
import com.esotericsoftware.spine.Animation.DrawOrderTimeline;
import com.esotericsoftware.spine.Animation.EventTimeline;
import com.esotericsoftware.spine.Animation.InheritTimeline;
import com.esotericsoftware.spine.Animation.IkConstraintTimeline;
import com.esotericsoftware.spine.Animation.ConstraintTimeline;
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
import com.esotericsoftware.spine.Animation.AlphaTimeline;
import com.esotericsoftware.spine.Animation.RGBATimeline;
import com.esotericsoftware.spine.Animation.RGB2Timeline;
import com.esotericsoftware.spine.Animation.RGBTimeline;
import com.esotericsoftware.spine.Animation.RGBA2Timeline;
import com.esotericsoftware.spine.Animation.RotateTimeline;
import com.esotericsoftware.spine.Animation.ScaleTimeline;
import com.esotericsoftware.spine.Animation.SequenceTimeline;
import com.esotericsoftware.spine.Animation.ShearTimeline;
import com.esotericsoftware.spine.Animation.ShearXTimeline;
import com.esotericsoftware.spine.Animation.ShearYTimeline;
import com.esotericsoftware.spine.Animation.SliderMixTimeline;
import com.esotericsoftware.spine.Animation.SliderTimeline;
import com.esotericsoftware.spine.Animation.TranslateTimeline;
import com.esotericsoftware.spine.Animation.TransformConstraintTimeline;
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
import com.esotericsoftware.spine.attachments.Sequence.SequenceMode;
import com.esotericsoftware.spine.attachments.VertexAttachment;

import com.openspine.model.AnimationEditor;
import com.openspine.model.SkeletonDocument;

/**
 * 导出 doc.data 为 Spine 4.3 JSON 格式（与 spine-libgdx SkeletonJson.readSkeletonData 对称）。
 * 零 GL 依赖，可在无头环境（SmokeTest）验证：导出的 JSON 应能被 SkeletonJson 重新加载且等价。
 */
public class SkeletonExporter {
	public static final String EXPORT_VERSION = "4.3.0";
	private static final Field CURVES_FIELD = findCurvesField();

	/** 导出 doc 为 JSON 字符串 */
	public static String toJson(SkeletonDocument doc) {
		return toJson(doc, new CancellationToken());
	}

	public static String toJson(SkeletonDocument doc, CancellationToken cancellation) {
		return serialize(doc, true, requireCancellation(cancellation));
	}

	/** Runs the common serializer checks without imposing JSON-only representation limits. */
	static void preflightForBinary(SkeletonDocument doc) {
		preflightForBinary(doc, new CancellationToken());
	}

	static void preflightForBinary(SkeletonDocument doc, CancellationToken cancellation) {
		serialize(doc, false, requireCancellation(cancellation));
	}

	private static String serialize(SkeletonDocument doc, boolean validateJsonRepresentation,
		CancellationToken cancellation) {
		cancellation.checkCancelled();
		if (doc == null || doc.data == null) throw new IllegalArgumentException("没有可导出的骨架数据");
		validateSupported(doc.data, validateJsonRepresentation);
		cancellation.checkCancelled();
		StringBuilder sb = new StringBuilder(16384);
		writeSkeleton(sb, doc.data);
		cancellation.checkCancelled();
		return sb.toString();
	}

	/** 导出 doc 为 JSON 并写入文件 */
	public static void write(SkeletonDocument doc, Writer writer) throws IOException {
		writer.write(toJson(doc));
	}

	/** Writes a complete JSON document before atomically replacing the destination when supported. */
	public static void writeAtomic(SkeletonDocument doc, Path target) throws IOException {
		writeAtomic(doc, target, new CancellationToken());
	}

	/** Writes atomically and leaves the destination unchanged when cancellation wins before commit. */
	public static void writeAtomic(SkeletonDocument doc, Path target, CancellationToken cancellation)
		throws IOException {
		if (target == null) throw new IllegalArgumentException("保存路径不能为空");
		CancellationToken token = requireCancellation(cancellation);
		String json = toJson(doc, token);
		Path absoluteTarget = target.toAbsolutePath().normalize();
		Path directory = absoluteTarget.getParent();
		if (directory == null || !Files.isDirectory(directory))
			throw new IOException("保存目录不存在：" + directory);

		String name = absoluteTarget.getFileName().toString();
		Path temporary = Files.createTempFile(directory, "." + name + ".", ".tmp");
		try {
			try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				for (int offset = 0; offset < json.length(); offset += 16384) {
					token.checkCancelled();
					writer.write(json, offset, Math.min(16384, json.length() - offset));
				}
			}
			token.checkCancelled();
			token.commit(() -> moveAtomic(temporary, absoluteTarget));
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static CancellationToken requireCancellation(CancellationToken cancellation) {
		if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
		return cancellation;
	}

	private static void moveAtomic(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException unsupported) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void writeSkeleton(StringBuilder sb, SkeletonData d) {
		sb.append("{\n");

		// skeleton header
		sb.append("\"skeleton\": {\n");
		if (d.getHash() != null) val("hash", d.getHash(), sb);
		val("spine", EXPORT_VERSION, sb);
		if (d.getX() != 0) val("x", d.getX(), sb);
		if (d.getY() != 0) val("y", d.getY(), sb);
		if (d.getWidth() != 0) val("width", d.getWidth(), sb);
		if (d.getHeight() != 0) val("height", d.getHeight(), sb);
		if (d.getReferenceScale() != 100) val("referenceScale", d.getReferenceScale(), sb);
		if (d.getFps() != 30) val("fps", d.getFps(), sb);
		if (d.getImagesPath() != null) val("images", d.getImagesPath(), sb);
		if (d.getAudioPath() != null) val("audio", d.getAudioPath(), sb);
		trimComma(sb);
		sb.append("\n},\n");

		// bones
		sb.append("\"bones\": [\n");
		for (int i = 0; i < d.getBones().size; i++) {
			BoneData b = d.getBones().get(i);
			sb.append("{ \"name\": ");
			quote(b.getName(), sb);
			if (b.getParent() != null) {
				sb.append(", \"parent\": ");
				quote(b.getParent().getName(), sb);
			}
			if (b.getLength() != 0) appendFloat(", \"length\": ", b.getLength(), sb);
			var p = b.getSetupPose();
			if (p.getX() != 0) appendFloat(", \"x\": ", p.getX(), sb);
			if (p.getY() != 0) appendFloat(", \"y\": ", p.getY(), sb);
			if (p.getRotation() != 0) appendFloat(", \"rotation\": ", p.getRotation(), sb);
			if (p.getScaleX() != 1) appendFloat(", \"scaleX\": ", p.getScaleX(), sb);
			if (p.getScaleY() != 1) appendFloat(", \"scaleY\": ", p.getScaleY(), sb);
			if (p.getShearX() != 0) appendFloat(", \"shearX\": ", p.getShearX(), sb);
			if (p.getShearY() != 0) appendFloat(", \"shearY\": ", p.getShearY(), sb);
			if (p.getInherit() != BoneData.Inherit.normal) {
				sb.append(", \"inherit\": ");
				quote(p.getInherit().name(), sb);
			}
			if (b.getSkinRequired()) sb.append(", \"skin\": true");
			sb.append(", \"color\": ");
			quote(rgbaHex(b.getColor()), sb);
			if (b.getIcon() != null) {
				sb.append(", \"icon\": ");
				quote(b.getIcon(), sb);
			}
			if (b.getIconSize() != 1f) appendFloat(", \"iconSize\": ", b.getIconSize(), sb);
			if (b.getIconRotation() != 0f) appendFloat(", \"iconRotation\": ", b.getIconRotation(), sb);
			if (!b.getVisible()) sb.append(", \"visible\": false");
			sb.append(" }");
			if (i < d.getBones().size - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append("],\n");

		// slots
		sb.append("\"slots\": [\n");
		for (int i = 0; i < d.getSlots().size; i++) {
			SlotData s = d.getSlots().get(i);
			int boneIndex = s.getBoneData().getIndex();
			BoneData currentBone = boneIndex >= 0 && boneIndex < d.getBones().size
				? d.getBones().get(boneIndex) : s.getBoneData();
			sb.append("{ \"name\": ");
			quote(s.getName(), sb);
			sb.append(", \"bone\": ");
			quote(currentBone.getName(), sb);
			Color slotColor = s.getSetupPose().getColor();
			if (!isWhite(slotColor)) {
				sb.append(", \"color\": ");
				quote(rgbaHex(slotColor), sb);
			}
			Color darkColor = s.getSetupPose().getDarkColor();
			if (darkColor != null) {
				sb.append(", \"dark\": ");
				quote(rgbHex(darkColor), sb);
			}
			if (s.getAttachmentName() != null) {
				sb.append(", \"attachment\": ");
				quote(s.getAttachmentName(), sb);
			}
			com.esotericsoftware.spine.BlendMode blendMode = s.getBlendMode();
			if (blendMode == null)
				throw new IllegalArgumentException("无法导出未设置混合模式的槽：" + s.getName());
			if (blendMode != com.esotericsoftware.spine.BlendMode.normal)
				sb.append(", \"blend\": \"").append(blendMode.name()).append('"');
			if (!s.getVisible()) sb.append(", \"visible\": false");
			sb.append(" }");
			if (i < d.getSlots().size - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append("],\n");

		if (d.getConstraints().notEmpty()) {
			writeConstraints(sb, d);
			sb.append(",\n");
		}

		// skins —— 4.3 新格式：每个 skin 对象含 name 字段 + attachments
		sb.append("\"skins\": {\n");
		for (int si = 0; si < d.getSkins().size; si++) {
			Skin skin = d.getSkins().get(si);
			quote(skin.getName(), sb);
			sb.append(": {\n\"name\": ");
			quote(skin.getName(), sb);
			if (skin.getBones().notEmpty()) {
				sb.append(",\n\"bones\": [");
				for (int index = 0; index < skin.getBones().size; index++) {
					if (index > 0) sb.append(", ");
					quote(skin.getBones().get(index).getName(), sb);
				}
				sb.append(']');
			}
			if (skin.getConstraints().notEmpty()) {
				writeSkinConstraintNames(sb, skin, IkConstraintData.class, "ik");
				writeSkinConstraintNames(sb, skin, TransformConstraintData.class, "transform");
				writeSkinConstraintNames(sb, skin, PathConstraintData.class, "path");
				writeSkinConstraintNames(sb, skin, PhysicsConstraintData.class, "physics");
				writeSkinConstraintNames(sb, skin, SliderData.class, "slider");
			}
			sb.append(",\n\"color\": ");
			quote(rgbaHex(skin.getColor()), sb);
			sb.append(",\n");
			sb.append("\"attachments\": {\n");
			Array<Skin.SkinEntry> entries = skin.getAttachments();
			// skin 内附件集合
			int written = 0;
			java.util.ArrayList<String> slotsWritten = new java.util.ArrayList<>();
			for (Skin.SkinEntry e : entries) {
				String slotName = slotName(d, e.getSlotIndex());
				if (slotName == null) continue;
				if (!slotsWritten.contains(slotName)) {
					slotsWritten.add(slotName);
				}
			}
			for (int i = 0; i < slotsWritten.size(); i++) {
				String slotName = slotsWritten.get(i);
				quote(slotName, sb);
				sb.append(": {\n");
				boolean firstInSlot = true;
				for (Skin.SkinEntry e : entries) {
					String sn = slotName(d, e.getSlotIndex());
					if (!slotName.equals(sn)) continue;
					if (!firstInSlot) sb.append(",\n");
					firstInSlot = false;
					writeAttachment(sb, d, e);
				}
				sb.append("\n}");
				if (i < slotsWritten.size() - 1) sb.append(',');
				sb.append('\n');
			}
			sb.append("}\n");
			sb.append("}");
			if (si < d.getSkins().size - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append("},\n");

		if (d.getEvents().notEmpty()) {
			writeEventDefinitions(sb, d.getEvents());
			sb.append(",\n");
		}

		// animations
		sb.append("\"animations\": {\n");
		for (int ai = 0; ai < d.getAnimations().size; ai++) {
			Animation a = d.getAnimations().get(ai);
			writeAnimation(sb, a, d);
			if (ai < d.getAnimations().size - 1) sb.append(",\n");
		}
		sb.append("\n}\n");
		sb.append("}\n");
	}

	private static void writeEventDefinitions(StringBuilder sb, Array<EventData> definitions) {
		sb.append("\"events\": {\n");
		for (int index = 0; index < definitions.size; index++) {
			EventData data = definitions.get(index);
			Event setup = data.getSetupPose();
			quote(data.getName(), sb);
			sb.append(": {");
			boolean firstMember = true;
			if (setup.getInt() != 0) {
				firstMember = appendMemberName(sb, "int", firstMember);
				sb.append(setup.getInt());
			}
			if (setup.getFloat() != 0f) {
				firstMember = appendMemberName(sb, "float", firstMember);
				sb.append(fmt(setup.getFloat()));
			}
			if (!setup.getString().isEmpty()) {
				firstMember = appendMemberName(sb, "string", firstMember);
				quote(setup.getString(), sb);
			}
			if (data.getAudioPath() != null) {
				firstMember = appendMemberName(sb, "audio", firstMember);
				quote(data.getAudioPath(), sb);
				if (setup.getVolume() != 1f) {
					firstMember = appendMemberName(sb, "volume", firstMember);
					sb.append(fmt(setup.getVolume()));
				}
				if (setup.getBalance() != 0f) {
					appendMemberName(sb, "balance", firstMember);
					sb.append(fmt(setup.getBalance()));
				}
			}
			sb.append(" }");
			if (index < definitions.size - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append('}');
	}

	private static void writeConstraints(StringBuilder sb, SkeletonData data) {
		sb.append("\"constraints\": [\n");
		for (int index = 0; index < data.getConstraints().size; index++) {
			ConstraintData<?, ?> constraint = data.getConstraints().get(index);
			if (constraint instanceof IkConstraintData ik) writeIkConstraint(sb, ik);
			else if (constraint instanceof TransformConstraintData transform) writeTransformConstraint(sb, transform);
			else if (constraint instanceof PathConstraintData path) writePathConstraint(sb, path);
			else if (constraint instanceof PhysicsConstraintData physics) writePhysicsConstraint(sb, physics);
			else if (constraint instanceof SliderData slider) writeSliderConstraint(sb, slider);
			else throw unsupported("Constraint " + constraint.getName() + " of type "
				+ constraint.getClass().getSimpleName() + " is not supported for export");
			if (index < data.getConstraints().size - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writeIkConstraint(StringBuilder sb, IkConstraintData ik) {
		IkConstraintPose setup = ik.getSetupPose();
		sb.append("{ \"name\": ");
		quote(ik.getName(), sb);
		sb.append(", \"type\": \"ik\", \"bones\": [");
		writeBoneNames(sb, ik.getBones());
		sb.append("], \"target\": ");
		quote(ik.getTarget().getName(), sb);
		if (ik.getSkinRequired()) sb.append(", \"skin\": true");
		if (ik.getScaleYMode() != ConstraintData.ScaleYMode.none) {
			sb.append(", \"scaleY\": ");
			quote(ik.getScaleYMode().name(), sb);
		}
		if (setup.getMix() != 1f) appendFloatMember(sb, "mix", setup.getMix());
		if (setup.getSoftness() != 0f) appendFloatMember(sb, "softness", setup.getSoftness());
		if (setup.getBendDirection() != 1) sb.append(", \"bendPositive\": false");
		if (setup.getCompress()) sb.append(", \"compress\": true");
		if (setup.getStretch()) sb.append(", \"stretch\": true");
		sb.append(" }");
	}

	private static void writeTransformConstraint(StringBuilder sb, TransformConstraintData transform) {
		TransformConstraintPose setup = transform.getSetupPose();
		sb.append("{ \"name\": ");
		quote(transform.getName(), sb);
		sb.append(", \"type\": \"transform\", \"bones\": [");
		writeBoneNames(sb, transform.getBones());
		sb.append("], \"source\": ");
		quote(transform.getSource().getName(), sb);
		if (transform.getSkinRequired()) sb.append(", \"skin\": true");
		if (transform.getLocalSource()) sb.append(", \"localSource\": true");
		if (transform.getLocalTarget()) sb.append(", \"localTarget\": true");
		if (transform.getAdditive()) sb.append(", \"additive\": true");
		if (transform.getClamp()) sb.append(", \"clamp\": true");
		if (transform.getProperties().notEmpty()) writeTransformProperties(sb, transform.getProperties());
		if (transform.getOffsetRotation() != 0f) appendFloatMember(sb, "rotation", transform.getOffsetRotation());
		if (transform.getOffsetX() != 0f) appendFloatMember(sb, "x", transform.getOffsetX());
		if (transform.getOffsetY() != 0f) appendFloatMember(sb, "y", transform.getOffsetY());
		if (transform.getOffsetScaleX() != 0f) appendFloatMember(sb, "scaleX", transform.getOffsetScaleX());
		if (transform.getOffsetScaleY() != 0f) appendFloatMember(sb, "scaleY", transform.getOffsetScaleY());
		if (transform.getOffsetShearY() != 0f) appendFloatMember(sb, "shearY", transform.getOffsetShearY());
		if (hasTransformTarget(transform, ToRotate.class) && setup.getMixRotate() != 1f)
			appendFloatMember(sb, "mixRotate", setup.getMixRotate());
		if (hasTransformTarget(transform, ToX.class) && setup.getMixX() != 1f)
			appendFloatMember(sb, "mixX", setup.getMixX());
		if (hasTransformTarget(transform, ToY.class) && setup.getMixY() != setup.getMixX())
			appendFloatMember(sb, "mixY", setup.getMixY());
		if (hasTransformTarget(transform, ToScaleX.class) && setup.getMixScaleX() != 1f)
			appendFloatMember(sb, "mixScaleX", setup.getMixScaleX());
		if (hasTransformTarget(transform, ToScaleY.class) && setup.getMixScaleY() != setup.getMixScaleX())
			appendFloatMember(sb, "mixScaleY", setup.getMixScaleY());
		if (hasTransformTarget(transform, ToShearY.class) && setup.getMixShearY() != 1f)
			appendFloatMember(sb, "mixShearY", setup.getMixShearY());
		sb.append(" }");
	}

	private static void writePathConstraint(StringBuilder sb, PathConstraintData path) {
		PathConstraintPose setup = path.getSetupPose();
		sb.append("{ \"name\": ");
		quote(path.getName(), sb);
		sb.append(", \"type\": \"path\", \"bones\": [");
		writeBoneNames(sb, path.getBones());
		sb.append("], \"slot\": ");
		quote(path.getSlot().getName(), sb);
		if (path.getSkinRequired()) sb.append(", \"skin\": true");
		if (path.getPositionMode() != PathConstraintData.PositionMode.percent)
			appendStringMember(sb, "positionMode", path.getPositionMode().name());
		if (path.getSpacingMode() != PathConstraintData.SpacingMode.length)
			appendStringMember(sb, "spacingMode", path.getSpacingMode().name());
		if (path.getRotateMode() != PathConstraintData.RotateMode.tangent)
			appendStringMember(sb, "rotateMode", path.getRotateMode().name());
		if (path.getOffsetRotation() != 0f) appendFloatMember(sb, "rotation", path.getOffsetRotation());
		if (setup.getPosition() != 0f) appendFloatMember(sb, "position", setup.getPosition());
		if (setup.getSpacing() != 0f) appendFloatMember(sb, "spacing", setup.getSpacing());
		if (setup.getMixRotate() != 1f) appendFloatMember(sb, "mixRotate", setup.getMixRotate());
		if (setup.getMixX() != 1f) appendFloatMember(sb, "mixX", setup.getMixX());
		if (setup.getMixY() != setup.getMixX()) appendFloatMember(sb, "mixY", setup.getMixY());
		sb.append(" }");
	}

	private static void writePhysicsConstraint(StringBuilder sb, PhysicsConstraintData physics) {
		PhysicsConstraintPose setup = physics.getSetupPose();
		int fps = physicsFps(physics.getStep(), "Physics constraint " + physics.getName());
		float mass = 1f / setup.getMassInverse();
		sb.append("{ \"name\": ");
		quote(physics.getName(), sb);
		sb.append(", \"type\": \"physics\", \"bone\": ");
		quote(physics.getBone().getName(), sb);
		if (physics.getSkinRequired()) sb.append(", \"skin\": true");
		if (physics.getX() != 0f) appendFloatMember(sb, "x", physics.getX());
		if (physics.getY() != 0f) appendFloatMember(sb, "y", physics.getY());
		if (physics.getRotate() != 0f) appendFloatMember(sb, "rotate", physics.getRotate());
		if (physics.getScaleX() != 0f) appendFloatMember(sb, "scaleX", physics.getScaleX());
		if (physics.getScaleYMode() != ConstraintData.ScaleYMode.none)
			appendStringMember(sb, "scaleY", physics.getScaleYMode().name());
		if (physics.getShearX() != 0f) appendFloatMember(sb, "shearX", physics.getShearX());
		if (physics.getLimit() != 5000f) appendFloatMember(sb, "limit", physics.getLimit());
		if (fps != 60) sb.append(", \"fps\": ").append(fps);
		if (setup.getInertia() != 0.5f) appendFloatMember(sb, "inertia", setup.getInertia());
		if (setup.getStrength() != 100f) appendFloatMember(sb, "strength", setup.getStrength());
		if (setup.getDamping() != 0.85f) appendFloatMember(sb, "damping", setup.getDamping());
		if (mass != 1f) appendFloatMember(sb, "mass", mass);
		if (setup.getWind() != 0f) appendFloatMember(sb, "wind", setup.getWind());
		if (setup.getGravity() != 0f) appendFloatMember(sb, "gravity", setup.getGravity());
		if (setup.getMix() != 1f) appendFloatMember(sb, "mix", setup.getMix());
		if (physics.getInertiaGlobal()) sb.append(", \"inertiaGlobal\": true");
		if (physics.getStrengthGlobal()) sb.append(", \"strengthGlobal\": true");
		if (physics.getDampingGlobal()) sb.append(", \"dampingGlobal\": true");
		if (physics.getMassGlobal()) sb.append(", \"massGlobal\": true");
		if (physics.getWindGlobal()) sb.append(", \"windGlobal\": true");
		if (physics.getGravityGlobal()) sb.append(", \"gravityGlobal\": true");
		if (physics.getMixGlobal()) sb.append(", \"mixGlobal\": true");
		sb.append(" }");
	}

	private static void writeSliderConstraint(StringBuilder sb, SliderData slider) {
		SliderPose setup = slider.getSetupPose();
		sb.append("{ \"name\": ");
		quote(slider.getName(), sb);
		sb.append(", \"type\": \"slider\", \"animation\": ");
		quote(slider.getAnimation().getName(), sb);
		if (slider.getSkinRequired()) sb.append(", \"skin\": true");
		if (slider.getAdditive()) sb.append(", \"additive\": true");
		if (slider.getLoop()) sb.append(", \"loop\": true");
		if (setup.getMix() != 1f) appendFloatMember(sb, "mix", setup.getMix());
		if (slider.getBone() == null) {
			if (setup.getTime() != 0f) appendFloatMember(sb, "time", setup.getTime());
		} else {
			sb.append(", \"bone\": ");
			quote(slider.getBone().getName(), sb);
			appendStringMember(sb, "property", transformPropertyName(slider.getProperty()));
			if (slider.getProperty().offset != 0f)
				appendFloatMember(sb, "from", slider.getProperty().offset);
			if (slider.getOffset() != 0f) appendFloatMember(sb, "to", slider.getOffset());
			if (slider.getScale() != 1f) appendFloatMember(sb, "scale", slider.getScale());
			if (slider.getMax() != 0f) appendFloatMember(sb, "max", slider.getMax());
			if (slider.getLocal()) sb.append(", \"local\": true");
		}
		sb.append(" }");
	}

	private static void writeTransformProperties(StringBuilder sb, Array<FromProperty> properties) {
		sb.append(", \"properties\": {");
		for (int fromIndex = 0; fromIndex < properties.size; fromIndex++) {
			FromProperty from = properties.get(fromIndex);
			if (fromIndex > 0) sb.append(", ");
			quote(transformPropertyName(from), sb);
			sb.append(": {");
			boolean first = true;
			if (from.offset != 0f) first = appendFloatField(sb, "offset", from.offset, first);
			if (!first) sb.append(", ");
			sb.append("\"to\": {");
			for (int toIndex = 0; toIndex < from.to.size; toIndex++) {
				ToProperty to = from.to.get(toIndex);
				if (toIndex > 0) sb.append(", ");
				quote(transformPropertyName(to), sb);
				sb.append(": {");
				boolean firstTo = true;
				if (to.offset != 0f) firstTo = appendFloatField(sb, "offset", to.offset, firstTo);
				if (to.max != 1f) firstTo = appendFloatField(sb, "max", to.max, firstTo);
				if (to.scale != 1f) appendFloatField(sb, "scale", to.scale, firstTo);
				sb.append(" }");
			}
			sb.append("} }");
		}
		sb.append('}');
	}

	private static void writeBoneNames(StringBuilder sb, Array<BoneData> bones) {
		for (int bone = 0; bone < bones.size; bone++) {
			if (bone > 0) sb.append(", ");
			quote(bones.get(bone).getName(), sb);
		}
	}

	private static void writeSkinConstraintNames(StringBuilder sb, Skin skin,
		Class<? extends ConstraintData<?, ?>> type, String jsonName) {
		int count = 0;
		for (ConstraintData<?, ?> constraint : skin.getConstraints()) if (type.isInstance(constraint)) count++;
		if (count == 0) return;
		sb.append(",\n");
		quote(jsonName, sb);
		sb.append(": [");
		int written = 0;
		for (ConstraintData<?, ?> constraint : skin.getConstraints()) {
			if (!type.isInstance(constraint)) continue;
			if (written++ > 0) sb.append(", ");
			quote(constraint.getName(), sb);
		}
		sb.append(']');
	}

	private static String transformPropertyName(FromProperty property) {
		if (property instanceof FromRotate) return "rotate";
		if (property instanceof FromX) return "x";
		if (property instanceof FromY) return "y";
		if (property instanceof FromScaleX) return "scaleX";
		if (property instanceof FromScaleY) return "scaleY";
		if (property instanceof FromShearY) return "shearY";
		throw unsupported("Transform constraint contains an unknown source property: "
			+ property.getClass().getSimpleName());
	}

	private static String transformPropertyName(ToProperty property) {
		if (property instanceof ToRotate) return "rotate";
		if (property instanceof ToX) return "x";
		if (property instanceof ToY) return "y";
		if (property instanceof ToScaleX) return "scaleX";
		if (property instanceof ToScaleY) return "scaleY";
		if (property instanceof ToShearY) return "shearY";
		throw unsupported("Transform constraint contains an unknown target property: "
			+ property.getClass().getSimpleName());
	}

	private static boolean hasTransformTarget(TransformConstraintData constraint,
		Class<? extends ToProperty> type) {
		for (FromProperty from : constraint.getProperties())
			for (ToProperty to : from.to) if (type.isInstance(to)) return true;
		return false;
	}

	private static String slotName(SkeletonData d, int idx) {
		for (int i = 0; i < d.getSlots().size; i++) {
			if (d.getSlots().get(i).getIndex() == idx) return d.getSlots().get(i).getName();
		}
		return null;
	}

	private static void writeAttachment(StringBuilder sb, SkeletonData data, Skin.SkinEntry entry) {
		String placeholder = entry.getPlaceholder();
		Attachment attachment = entry.getAttachment();
		quote(placeholder, sb);
		sb.append(": {");
		boolean first = true;
		if (!attachment.getName().equals(placeholder)) {
			first = appendMemberName(sb, "name", first);
			quote(attachment.getName(), sb);
		}

		if (attachment instanceof RegionAttachment region) {
			first = appendStringField(sb, "path", region.getPath(), first);
			first = writeSequence(sb, region.getSequence(), first);
			first = appendOptionalFloatField(sb, "x", region.getX(), 0f, first);
			first = appendOptionalFloatField(sb, "y", region.getY(), 0f, first);
			first = appendOptionalFloatField(sb, "scaleX", region.getScaleX(), 1f, first);
			first = appendOptionalFloatField(sb, "scaleY", region.getScaleY(), 1f, first);
			first = appendOptionalFloatField(sb, "rotation", region.getRotation(), 0f, first);
			first = appendFloatField(sb, "width", region.getWidth(), first);
			first = appendFloatField(sb, "height", region.getHeight(), first);
			if (!isWhite(region.getColor())) first = appendColorField(sb, "color", region.getColor(), first);
		} else if (attachment instanceof BoundingBoxAttachment box) {
			first = appendStringField(sb, "type", "boundingbox", first);
			first = appendIntField(sb, "vertexCount", vertexCount(box), first);
			first = writeVertices(sb, data, box, first);
			first = appendColorField(sb, "color", box.getColor(), first);
		} else if (attachment instanceof MeshAttachment mesh) {
			MeshAttachment source = mesh.getSourceMesh();
			first = appendStringField(sb, "type", source == null ? "mesh" : "linkedmesh", first);
			first = appendStringField(sb, "path", mesh.getPath(), first);
			first = writeSequence(sb, mesh.getSequence(), first);
			if (!isWhite(mesh.getColor())) first = appendColorField(sb, "color", mesh.getColor(), first);
			first = appendOptionalFloatField(sb, "width", mesh.getWidth(), 0f, first);
			first = appendOptionalFloatField(sb, "height", mesh.getHeight(), 0f, first);
			if (source == null) {
				first = appendFloatArrayField(sb, "uvs", mesh.getRegionUVs(), first);
				first = writeVertices(sb, data, mesh, first);
				first = appendShortArrayField(sb, "triangles", mesh.getTriangles(), first);
				first = appendIntField(sb, "hull", mesh.getHullLength() / 2, first);
				if (mesh.getEdges() != null) first = appendShortArrayField(sb, "edges", mesh.getEdges(), first);
			} else {
				AttachmentLocation location = findAttachmentLocation(data, source);
				if (location == null)
					throw unsupported("Linked Mesh " + placeholder + " 的源网格不在任何皮肤中");
				first = appendStringField(sb, "source", location.placeholder(), first);
				first = appendStringField(sb, "skin", location.skin().getName(), first);
				if (location.slotIndex() != entry.getSlotIndex()) {
					String slot = slotName(data, location.slotIndex());
					if (slot == null) throw unsupported("Linked Mesh " + placeholder + " 的源槽索引无效");
					first = appendStringField(sb, "slot", slot, first);
				}
				if (mesh.getTimelineAttachment() == mesh) first = appendBooleanField(sb, "timelines", false, first);
			}
		} else if (attachment instanceof PathAttachment path) {
			first = appendStringField(sb, "type", "path", first);
			first = appendOptionalBooleanField(sb, "closed", path.getClosed(), false, first);
			first = appendOptionalBooleanField(sb, "constantSpeed", path.getConstantSpeed(), true, first);
			first = appendIntField(sb, "vertexCount", vertexCount(path), first);
			first = writeVertices(sb, data, path, first);
			first = appendFloatArrayField(sb, "lengths", path.getLengths(), first);
			first = appendColorField(sb, "color", path.getColor(), first);
		} else if (attachment instanceof PointAttachment point) {
			first = appendStringField(sb, "type", "point", first);
			first = appendOptionalFloatField(sb, "x", point.getX(), 0f, first);
			first = appendOptionalFloatField(sb, "y", point.getY(), 0f, first);
			first = appendOptionalFloatField(sb, "rotation", point.getRotation(), 0f, first);
			first = appendColorField(sb, "color", point.getColor(), first);
		} else if (attachment instanceof ClippingAttachment clipping) {
			first = appendStringField(sb, "type", "clipping", first);
			if (clipping.getEndSlot() != null)
				first = appendStringField(sb, "end", clipping.getEndSlot().getName(), first);
			first = appendOptionalBooleanField(sb, "convex", clipping.getConvex(), false, first);
			first = appendOptionalBooleanField(sb, "inverse", clipping.getInverse(), false, first);
			first = appendIntField(sb, "vertexCount", vertexCount(clipping), first);
			first = writeVertices(sb, data, clipping, first);
			first = appendColorField(sb, "color", clipping.getColor(), first);
		} else {
			throw unsupported("附件 " + placeholder + " 的类型 " + attachment.getClass().getSimpleName() + " 尚不能无损导出");
		}
		sb.append(first ? "}" : " }");
	}

	private static boolean writeSequence(StringBuilder sb, Sequence sequence, boolean first) {
		if (sequence == null || !sequence.hasPathSuffix()) return first;
		first = appendMemberName(sb, "sequence", first);
		sb.append("{ \"count\": ").append(sequence.getRegions().length);
		if (sequence.getStart() != 1) sb.append(", \"start\": ").append(sequence.getStart());
		if (sequence.getDigits() != 0) sb.append(", \"digits\": ").append(sequence.getDigits());
		if (sequence.getSetupIndex() != 0) sb.append(", \"setup\": ").append(sequence.getSetupIndex());
		sb.append(" }");
		return first;
	}

	private static int vertexCount(VertexAttachment attachment) {
		if ((attachment.getWorldVerticesLength() & 1) != 0)
			throw unsupported("顶点附件 " + attachment.getName() + " 的世界顶点长度不是偶数");
		return attachment.getWorldVerticesLength() / 2;
	}

	private static boolean writeVertices(StringBuilder sb, SkeletonData data, VertexAttachment attachment, boolean first) {
		int vertexCount = vertexCount(attachment);
		float[] vertices = attachment.getVertices();
		if (vertices == null) throw unsupported("顶点附件 " + attachment.getName() + " 没有顶点数据");
		int[] bones = attachment.getBones();
		first = appendMemberName(sb, "vertices", first);
		sb.append('[');
		if (bones == null) {
			if (vertices.length != attachment.getWorldVerticesLength())
				throw unsupported("顶点附件 " + attachment.getName() + " 的非加权顶点数量不完整");
			appendFloatArrayValues(sb, vertices);
		} else {
			int boneCursor = 0, vertexCursor = 0;
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				if (boneCursor >= bones.length)
					throw unsupported("顶点附件 " + attachment.getName() + " 的权重骨骼数据不完整");
				int boneCount = bones[boneCursor++];
				if (boneCount <= 0)
					throw unsupported("顶点附件 " + attachment.getName() + " 的单顶点骨骼数量无效");
				if (vertex > 0) sb.append(", ");
				sb.append(boneCount);
				for (int influence = 0; influence < boneCount; influence++) {
					if (boneCursor >= bones.length || vertexCursor + 2 >= vertices.length)
						throw unsupported("顶点附件 " + attachment.getName() + " 的权重数据不完整");
					int boneIndex = bones[boneCursor++];
					if (boneIndex < 0 || boneIndex >= data.getBones().size)
						throw unsupported("顶点附件 " + attachment.getName() + " 引用了不存在的骨骼索引 " + boneIndex);
					sb.append(", ").append(boneIndex);
					sb.append(", ").append(fmt(vertices[vertexCursor++]));
					sb.append(", ").append(fmt(vertices[vertexCursor++]));
					sb.append(", ").append(fmt(vertices[vertexCursor++]));
				}
			}
			if (boneCursor != bones.length || vertexCursor != vertices.length)
				throw unsupported("顶点附件 " + attachment.getName() + " 包含多余的权重数据");
		}
		sb.append(']');
		return first;
	}

	private static AttachmentLocation findAttachmentLocation(SkeletonData data, Attachment attachment) {
		for (int skinIndex = 0; skinIndex < data.getSkins().size; skinIndex++) {
			Skin skin = data.getSkins().get(skinIndex);
			Array<Skin.SkinEntry> entries = skin.getAttachments();
			for (int entryIndex = 0; entryIndex < entries.size; entryIndex++) {
				Skin.SkinEntry entry = entries.get(entryIndex);
				if (entry.getAttachment() == attachment)
					return new AttachmentLocation(skin, entry.getSlotIndex(), entry.getPlaceholder());
			}
		}
		return null;
	}

	private static AttachmentLocation findAttachmentLocation(SkeletonData data, Attachment attachment, int slotIndex) {
		for (int skinIndex = 0; skinIndex < data.getSkins().size; skinIndex++) {
			Skin skin = data.getSkins().get(skinIndex);
			Array<Skin.SkinEntry> entries = skin.getAttachments();
			for (int entryIndex = 0; entryIndex < entries.size; entryIndex++) {
				Skin.SkinEntry entry = entries.get(entryIndex);
				if (entry.getSlotIndex() == slotIndex && entry.getAttachment() == attachment)
					return new AttachmentLocation(skin, slotIndex, entry.getPlaceholder());
			}
		}
		return null;
	}

	private record AttachmentLocation(Skin skin, int slotIndex, String placeholder) {}

	private static void writeAnimation(StringBuilder sb, Animation a, SkeletonData d) {
		quote(a.getName(), sb);
		sb.append(": {\n");
		// 骨骼时间线分组
		sb.append("\"bones\": {\n");
		Array<Animation.Timeline> timelines = a.getTimelines();
		// 找出涉及骨骼的 timeline，按 boneIndex 分组（顺序按骨骼 index）
		java.util.LinkedHashMap<Integer, java.util.ArrayList<Animation.Timeline>> byBone = new java.util.LinkedHashMap<>();
		for (Animation.Timeline tl : timelines) {
			if (tl instanceof BoneTimeline) {
				int bi = ((BoneTimeline) tl).getBoneIndex();
				java.util.ArrayList<Animation.Timeline> list = byBone.get(bi);
				if (list == null) { list = new java.util.ArrayList<>(); byBone.put(bi, list); }
				list.add(tl);
			}
		}
		boolean firstBone = true;
		for (java.util.Map.Entry<Integer, java.util.ArrayList<Animation.Timeline>> e : byBone.entrySet()) {
			BoneData bone = findBone(d, e.getKey());
			if (bone == null) continue;
			if (!firstBone) sb.append(",\n");
			firstBone = false;
			quote(bone.getName(), sb);
			sb.append(": {\n");
			boolean firstTl = true;
			for (Animation.Timeline tl : e.getValue()) {
				if (!firstTl) sb.append(",\n");
				firstTl = false;
				writeBoneTimeline(sb, tl);
			}
			sb.append('}');
		}
		sb.append('\n').append("},\n");

		// 槽时间线：离散附件切换、合并或拆分颜色，以及双色 RGB2/RGBA2。
		sb.append("\"slots\": {\n");
		java.util.LinkedHashMap<Integer, java.util.ArrayList<Animation.Timeline>> bySlot = new java.util.LinkedHashMap<>();
		for (Animation.Timeline timeline : timelines) {
			if (timeline instanceof AttachmentTimeline attachment) {
				bySlot.computeIfAbsent(attachment.getSlotIndex(), ignored -> new java.util.ArrayList<>()).add(attachment);
			} else if (timeline instanceof RGBATimeline color) {
				bySlot.computeIfAbsent(color.getSlotIndex(), ignored -> new java.util.ArrayList<>()).add(color);
			} else if (timeline instanceof RGBTimeline color) {
				bySlot.computeIfAbsent(color.getSlotIndex(), ignored -> new java.util.ArrayList<>()).add(color);
			} else if (timeline instanceof AlphaTimeline alpha) {
				bySlot.computeIfAbsent(alpha.getSlotIndex(), ignored -> new java.util.ArrayList<>()).add(alpha);
			} else if (timeline instanceof RGB2Timeline color) {
				bySlot.computeIfAbsent(color.getSlotIndex(), ignored -> new java.util.ArrayList<>()).add(color);
			} else if (timeline instanceof RGBA2Timeline color) {
				bySlot.computeIfAbsent(color.getSlotIndex(), ignored -> new java.util.ArrayList<>()).add(color);
			}
		}
		boolean firstSlot = true;
		for (java.util.Map.Entry<Integer, java.util.ArrayList<Animation.Timeline>> entry : bySlot.entrySet()) {
			String slotName = slotName(d, entry.getKey());
			if (slotName == null) continue;
			if (!firstSlot) sb.append(",\n");
			firstSlot = false;
			quote(slotName, sb);
			sb.append(": {\n");
			for (int index = 0; index < entry.getValue().size(); index++) {
				if (index > 0) sb.append(",\n");
				Animation.Timeline timeline = entry.getValue().get(index);
				if (timeline instanceof AttachmentTimeline attachment) writeAttachmentTimeline(sb, attachment);
				else if (timeline instanceof RGBATimeline color) writeColorTimeline(sb, color);
				else if (timeline instanceof RGBTimeline color) writeRgbTimeline(sb, color);
				else if (timeline instanceof AlphaTimeline alpha) writeAlphaTimeline(sb, alpha);
				else if (timeline instanceof RGB2Timeline || timeline instanceof RGBA2Timeline)
					writeTwoColorTimeline(sb, timeline);
			}
			sb.append('\n').append('}');
		}
		sb.append('\n').append('}');
		writeAttachmentTimelines(sb, timelines, d);
		java.util.LinkedHashMap<Integer, IkConstraintTimeline> byIk = new java.util.LinkedHashMap<>();
		for (Animation.Timeline timeline : timelines) {
			if (timeline instanceof IkConstraintTimeline ik) byIk.put(ik.getConstraintIndex(), ik);
		}
		if (!byIk.isEmpty()) {
			sb.append(",\n\"ik\": {\n");
			int written = 0;
			for (java.util.Map.Entry<Integer, IkConstraintTimeline> entry : byIk.entrySet()) {
				IkConstraintData constraint = (IkConstraintData)d.getConstraints().get(entry.getKey());
				if (written++ > 0) sb.append(",\n");
				quote(constraint.getName(), sb);
				sb.append(": ");
				writeIkTimeline(sb, entry.getValue());
			}
			sb.append("\n}");
		}
		java.util.LinkedHashMap<Integer, TransformConstraintTimeline> byTransform = new java.util.LinkedHashMap<>();
		for (Animation.Timeline timeline : timelines) {
			if (timeline instanceof TransformConstraintTimeline transform)
				byTransform.put(transform.getConstraintIndex(), transform);
		}
		if (!byTransform.isEmpty()) {
			sb.append(",\n\"transform\": {\n");
			int written = 0;
			for (java.util.Map.Entry<Integer, TransformConstraintTimeline> entry : byTransform.entrySet()) {
				TransformConstraintData constraint = (TransformConstraintData)d.getConstraints().get(entry.getKey());
				if (written++ > 0) sb.append(",\n");
				quote(constraint.getName(), sb);
				sb.append(": ");
				writeTransformTimeline(sb, entry.getValue());
			}
			sb.append("\n}");
		}
		writePathConstraintTimelines(sb, timelines, d);
		writePhysicsConstraintTimelines(sb, timelines, d);
		writeSliderConstraintTimelines(sb, timelines, d);
		DrawOrderTimeline drawOrderTimeline = null;
		java.util.ArrayList<DrawOrderFolderTimeline> drawOrderFolderTimelines = new java.util.ArrayList<>();
		EventTimeline eventTimeline = null;
		for (Animation.Timeline timeline : timelines) {
			if (timeline instanceof DrawOrderTimeline drawOrder) {
				drawOrderTimeline = drawOrder;
			} else if (timeline instanceof DrawOrderFolderTimeline drawOrderFolder) {
				drawOrderFolderTimelines.add(drawOrderFolder);
			} else if (timeline instanceof EventTimeline events) {
				eventTimeline = events;
			}
		}
		if (drawOrderTimeline != null) {
			sb.append(",\n");
			writeDrawOrderTimeline(sb, drawOrderTimeline, d);
		}
		if (!drawOrderFolderTimelines.isEmpty()) {
			sb.append(",\n");
			writeDrawOrderFolderTimelines(sb, drawOrderFolderTimelines, d);
		}
		if (eventTimeline != null) {
			sb.append(",\n");
			writeEventTimeline(sb, eventTimeline);
		}
		if (!isWhite(a.getColor())) {
			sb.append(",\n\"color\": ");
			quote(rgbaHex(a.getColor()), sb);
		} else {
			// Validate the default-valued color too, so malformed data is never silently omitted.
			rgbaHex(a.getColor());
		}
		sb.append('\n').append('}');
	}

	private static void writeAttachmentTimelines(StringBuilder sb, Array<Animation.Timeline> timelines,
		SkeletonData data) {
		java.util.LinkedHashMap<Skin, java.util.LinkedHashMap<Integer,
			java.util.LinkedHashMap<String, java.util.ArrayList<Animation.Timeline>>>> bySkin =
				new java.util.LinkedHashMap<>();
		for (Animation.Timeline timeline : timelines) {
			AttachmentLocation location;
			if (timeline instanceof DeformTimeline deform) {
				location = findAttachmentLocation(data, deform.getAttachment(), deform.getSlotIndex());
			} else if (timeline instanceof SequenceTimeline sequence) {
				location = findAttachmentLocation(data, sequence.getAttachment(), sequence.getSlotIndex());
			} else {
				continue;
			}
			if (location == null) throw unsupported("附件动画轨道的目标附件不存在");
			bySkin.computeIfAbsent(location.skin(), ignored -> new java.util.LinkedHashMap<>())
				.computeIfAbsent(location.slotIndex(), ignored -> new java.util.LinkedHashMap<>())
				.computeIfAbsent(location.placeholder(), ignored -> new java.util.ArrayList<>()).add(timeline);
		}
		if (bySkin.isEmpty()) return;

		sb.append(",\n\"attachments\": {\n");
		int skinIndex = 0;
		for (java.util.Map.Entry<Skin, java.util.LinkedHashMap<Integer,
			java.util.LinkedHashMap<String, java.util.ArrayList<Animation.Timeline>>>> skinEntry : bySkin.entrySet()) {
			if (skinIndex++ > 0) sb.append(",\n");
			quote(skinEntry.getKey().getName(), sb);
			sb.append(": {\n");
			int slotIndex = 0;
			for (java.util.Map.Entry<Integer, java.util.LinkedHashMap<String,
				java.util.ArrayList<Animation.Timeline>>> slotEntry : skinEntry.getValue().entrySet()) {
				if (slotIndex++ > 0) sb.append(",\n");
				quote(data.getSlots().get(slotEntry.getKey()).getName(), sb);
				sb.append(": {\n");
				int attachmentIndex = 0;
				for (java.util.Map.Entry<String, java.util.ArrayList<Animation.Timeline>> attachmentEntry
					: slotEntry.getValue().entrySet()) {
					if (attachmentIndex++ > 0) sb.append(",\n");
					quote(attachmentEntry.getKey(), sb);
					sb.append(": {\n");
					for (int timelineIndex = 0; timelineIndex < attachmentEntry.getValue().size(); timelineIndex++) {
						if (timelineIndex > 0) sb.append(",\n");
						Animation.Timeline timeline = attachmentEntry.getValue().get(timelineIndex);
						if (timeline instanceof DeformTimeline deform) writeDeformTimeline(sb, deform);
						else writeSequenceTimeline(sb, (SequenceTimeline)timeline);
					}
					sb.append("\n}");
				}
				sb.append("\n}");
			}
			sb.append("\n}");
		}
		sb.append("\n}");
	}

	private static void writeDeformTimeline(StringBuilder sb, DeformTimeline timeline) {
		sb.append("\"deform\": [\n");
		VertexAttachment attachment = timeline.getAttachment();
		boolean weighted = attachment.getBones() != null;
		float[] setupVertices = attachment.getVertices();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			float[] vertices = timeline.getVertices()[frame];
			sb.append("{ \"time\": ").append(fmt(timeline.getFrames()[frame]));
			int first = 0, last = vertices.length - 1;
			while (first <= last && deformValue(vertices, setupVertices, weighted, first) == 0f) first++;
			while (last >= first && deformValue(vertices, setupVertices, weighted, last) == 0f) last--;
			if (first <= last) {
				if (first > 0) sb.append(", \"offset\": ").append(first);
				sb.append(", \"vertices\": [");
				for (int index = first; index <= last; index++) {
					if (index > first) sb.append(", ");
					sb.append(fmt(deformValue(vertices, setupVertices, weighted, index)));
				}
				sb.append(']');
			}
			writeDeformCurve(sb, timeline, frame);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static float deformValue(float[] vertices, float[] setupVertices, boolean weighted, int index) {
		return weighted ? vertices[index] : vertices[index] - setupVertices[index];
	}

	private static void writeSequenceTimeline(StringBuilder sb, SequenceTimeline timeline) {
		sb.append("\"sequence\": [\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			int modeAndIndex = (int)frames[base + 1];
			SequenceMode mode = SequenceMode.values[modeAndIndex & 0xf];
			sb.append("{ \"time\": ").append(fmt(frames[base]));
			appendStringMember(sb, "mode", mode.name());
			sb.append(", \"index\": ").append(modeAndIndex >> 4);
			appendFloatMember(sb, "delay", frames[base + 2]);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writeIkTimeline(StringBuilder sb, IkConstraintTimeline timeline) {
		sb.append("[\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			sb.append("{ \"time\": ").append(fmt(frames[base]));
			appendFloatMember(sb, "mix", frames[base + 1]);
			appendFloatMember(sb, "softness", frames[base + 2]);
			sb.append(", \"bendPositive\": ").append(frames[base + 3] > 0f);
			sb.append(", \"compress\": ").append(frames[base + 4] != 0f);
			sb.append(", \"stretch\": ").append(frames[base + 5] != 0f);
			writeCurve(sb, timeline, frame, 2);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writeTransformTimeline(StringBuilder sb, TransformConstraintTimeline timeline) {
		sb.append("[\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			sb.append("{ \"time\": ").append(fmt(frames[base]));
			appendFloatMember(sb, "mixRotate", frames[base + 1]);
			appendFloatMember(sb, "mixX", frames[base + 2]);
			appendFloatMember(sb, "mixY", frames[base + 3]);
			appendFloatMember(sb, "mixScaleX", frames[base + 4]);
			appendFloatMember(sb, "mixScaleY", frames[base + 5]);
			appendFloatMember(sb, "mixShearY", frames[base + 6]);
			writeCurve(sb, timeline, frame, 6);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writePathConstraintTimelines(StringBuilder sb, Array<Animation.Timeline> timelines,
		SkeletonData data) {
		java.util.LinkedHashMap<Integer, java.util.ArrayList<Animation.Timeline>> grouped =
			new java.util.LinkedHashMap<>();
		for (Animation.Timeline timeline : timelines) {
			if (pathTimelineName(timeline) == null) continue;
			int index = ((ConstraintTimeline)timeline).getConstraintIndex();
			grouped.computeIfAbsent(index, ignored -> new java.util.ArrayList<>()).add(timeline);
		}
		if (grouped.isEmpty()) return;
		sb.append(",\n\"path\": {\n");
		int constraintWritten = 0;
		for (java.util.Map.Entry<Integer, java.util.ArrayList<Animation.Timeline>> entry : grouped.entrySet()) {
			if (constraintWritten++ > 0) sb.append(",\n");
			PathConstraintData constraint = (PathConstraintData)data.getConstraints().get(entry.getKey());
			quote(constraint.getName(), sb);
			sb.append(": {\n");
			for (int timelineIndex = 0; timelineIndex < entry.getValue().size(); timelineIndex++) {
				if (timelineIndex > 0) sb.append(",\n");
				Animation.Timeline timeline = entry.getValue().get(timelineIndex);
				String property = pathTimelineName(timeline);
				if (timeline instanceof PathConstraintMixTimeline mix)
					writePathMixTimeline(sb, mix);
				else
					writeConstraintValueTimeline(sb, property, (CurveTimeline)timeline);
			}
			sb.append("\n}");
		}
		sb.append("\n}");
	}

	private static void writePhysicsConstraintTimelines(StringBuilder sb, Array<Animation.Timeline> timelines,
		SkeletonData data) {
		java.util.LinkedHashMap<Integer, java.util.ArrayList<Animation.Timeline>> grouped =
			new java.util.LinkedHashMap<>();
		for (Animation.Timeline timeline : timelines) {
			if (physicsTimelineName(timeline) == null) continue;
			int index = ((ConstraintTimeline)timeline).getConstraintIndex();
			grouped.computeIfAbsent(index, ignored -> new java.util.ArrayList<>()).add(timeline);
		}
		if (grouped.isEmpty()) return;
		sb.append(",\n\"physics\": {\n");
		int constraintWritten = 0;
		for (java.util.Map.Entry<Integer, java.util.ArrayList<Animation.Timeline>> entry : grouped.entrySet()) {
			if (constraintWritten++ > 0) sb.append(",\n");
			String name = entry.getKey() == -1 ? ""
				: ((PhysicsConstraintData)data.getConstraints().get(entry.getKey())).getName();
			quote(name, sb);
			sb.append(": {\n");
			for (int timelineIndex = 0; timelineIndex < entry.getValue().size(); timelineIndex++) {
				if (timelineIndex > 0) sb.append(",\n");
				Animation.Timeline timeline = entry.getValue().get(timelineIndex);
				String property = physicsTimelineName(timeline);
				if (timeline instanceof PhysicsConstraintResetTimeline reset)
					writePhysicsResetTimeline(sb, reset);
				else
					writeConstraintValueTimeline(sb, property, (CurveTimeline)timeline);
			}
			sb.append("\n}");
		}
		sb.append("\n}");
	}

	private static void writeSliderConstraintTimelines(StringBuilder sb, Array<Animation.Timeline> timelines,
		SkeletonData data) {
		java.util.LinkedHashMap<Integer, java.util.ArrayList<Animation.Timeline>> grouped =
			new java.util.LinkedHashMap<>();
		for (Animation.Timeline timeline : timelines) {
			if (sliderTimelineName(timeline) == null) continue;
			int index = ((ConstraintTimeline)timeline).getConstraintIndex();
			grouped.computeIfAbsent(index, ignored -> new java.util.ArrayList<>()).add(timeline);
		}
		if (grouped.isEmpty()) return;
		sb.append(",\n\"slider\": {\n");
		int constraintWritten = 0;
		for (java.util.Map.Entry<Integer, java.util.ArrayList<Animation.Timeline>> entry : grouped.entrySet()) {
			if (constraintWritten++ > 0) sb.append(",\n");
			SliderData constraint = (SliderData)data.getConstraints().get(entry.getKey());
			quote(constraint.getName(), sb);
			sb.append(": {\n");
			for (int timelineIndex = 0; timelineIndex < entry.getValue().size(); timelineIndex++) {
				if (timelineIndex > 0) sb.append(",\n");
				Animation.Timeline timeline = entry.getValue().get(timelineIndex);
				writeConstraintValueTimeline(sb, sliderTimelineName(timeline), (CurveTimeline)timeline);
			}
			sb.append("\n}");
		}
		sb.append("\n}");
	}

	private static void writeConstraintValueTimeline(StringBuilder sb, String property, CurveTimeline timeline) {
		quote(property, sb);
		sb.append(": [\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			sb.append("{ \"time\": ").append(fmt(frames[base]));
			appendFloatMember(sb, "value", frames[base + 1]);
			writeCurve(sb, timeline, frame, 1);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writePathMixTimeline(StringBuilder sb, PathConstraintMixTimeline timeline) {
		sb.append("\"mix\": [\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			sb.append("{ \"time\": ").append(fmt(frames[base]));
			appendFloatMember(sb, "mixRotate", frames[base + 1]);
			appendFloatMember(sb, "mixX", frames[base + 2]);
			appendFloatMember(sb, "mixY", frames[base + 3]);
			writeCurve(sb, timeline, frame, 3);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writePhysicsResetTimeline(StringBuilder sb, PhysicsConstraintResetTimeline timeline) {
		sb.append("\"reset\": [\n");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			sb.append("{ \"time\": ").append(fmt(timeline.getFrames()[frame])).append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static String pathTimelineName(Animation.Timeline timeline) {
		if (timeline instanceof PathConstraintPositionTimeline) return "position";
		if (timeline instanceof PathConstraintSpacingTimeline) return "spacing";
		if (timeline instanceof PathConstraintMixTimeline) return "mix";
		return null;
	}

	private static String physicsTimelineName(Animation.Timeline timeline) {
		if (timeline instanceof PhysicsConstraintResetTimeline) return "reset";
		if (timeline instanceof PhysicsConstraintInertiaTimeline) return "inertia";
		if (timeline instanceof PhysicsConstraintStrengthTimeline) return "strength";
		if (timeline instanceof PhysicsConstraintDampingTimeline) return "damping";
		if (timeline instanceof PhysicsConstraintMassTimeline) return "mass";
		if (timeline instanceof PhysicsConstraintWindTimeline) return "wind";
		if (timeline instanceof PhysicsConstraintGravityTimeline) return "gravity";
		if (timeline instanceof PhysicsConstraintMixTimeline) return "mix";
		return null;
	}

	private static String sliderTimelineName(Animation.Timeline timeline) {
		if (timeline instanceof SliderTimeline) return "time";
		if (timeline instanceof SliderMixTimeline) return "mix";
		return null;
	}

	private static void writeDrawOrderTimeline(StringBuilder sb, DrawOrderTimeline timeline, SkeletonData data) {
		sb.append("\"drawOrder\": [\n");
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			sb.append("{ \"time\": ").append(fmt(timeline.getFrames()[frame]));
			int[] drawOrder = timeline.getDrawOrders()[frame];
			if (drawOrder != null) {
				int[] positions = drawOrderPositions(drawOrder, data.getSlots().size);
				boolean first = true;
				for (int slotIndex = 0; slotIndex < positions.length; slotIndex++) {
					int offset = positions[slotIndex] - slotIndex;
					if (offset == 0) continue;
					sb.append(first ? ", \"offsets\": [" : ", ");
					first = false;
					sb.append("{ \"slot\": ");
					quote(data.getSlots().get(slotIndex).getName(), sb);
					sb.append(", \"offset\": ").append(offset).append(" }");
				}
				if (!first) sb.append(']');
			}
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writeDrawOrderFolderTimelines(StringBuilder sb,
		java.util.ArrayList<DrawOrderFolderTimeline> timelines, SkeletonData data) {
		sb.append("\"drawOrderFolder\": {\n");
		for (int timelineIndex = 0; timelineIndex < timelines.size(); timelineIndex++) {
			if (timelineIndex > 0) sb.append(",\n");
			DrawOrderFolderTimeline timeline = timelines.get(timelineIndex);
			quote("folder" + timelineIndex, sb);
			sb.append(": { \"slots\": [");
			int[] slots = timeline.getSlots();
			for (int slot = 0; slot < slots.length; slot++) {
				if (slot > 0) sb.append(", ");
				quote(data.getSlots().get(slots[slot]).getName(), sb);
			}
			sb.append("], \"keys\": [\n");
			for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
				sb.append("{ \"time\": ").append(fmt(timeline.getFrames()[frame]));
				int[] drawOrder = timeline.getDrawOrders()[frame];
				if (drawOrder != null) {
					int[] positions = drawOrderPositions(drawOrder, slots.length);
					boolean first = true;
					for (int localSlot = 0; localSlot < positions.length; localSlot++) {
						int offset = positions[localSlot] - localSlot;
						if (offset == 0) continue;
						sb.append(first ? ", \"offsets\": [" : ", ");
						first = false;
						sb.append("{ \"slot\": ");
						quote(data.getSlots().get(slots[localSlot]).getName(), sb);
						sb.append(", \"offset\": ").append(offset).append(" }");
					}
					if (!first) sb.append(']');
				}
				sb.append(" }");
				if (frame < timeline.getFrameCount() - 1) sb.append(',');
				sb.append('\n');
			}
			sb.append("] }");
		}
		sb.append("\n}");
	}

	private static int[] drawOrderPositions(int[] drawOrder, int slotCount) {
		if (drawOrder.length != slotCount)
			throw unsupported("绘制顺序关键帧必须包含全部 " + slotCount + " 个槽");
		int[] positions = new int[slotCount];
		java.util.Arrays.fill(positions, -1);
		for (int position = 0; position < drawOrder.length; position++) {
			int slotIndex = drawOrder[position];
			if (slotIndex < 0 || slotIndex >= slotCount)
				throw unsupported("绘制顺序关键帧包含不存在的槽索引 " + slotIndex);
			if (positions[slotIndex] != -1)
				throw unsupported("绘制顺序关键帧包含重复槽索引 " + slotIndex);
			positions[slotIndex] = position;
		}
		return positions;
	}

	private static void writeEventTimeline(StringBuilder sb, EventTimeline timeline) {
		sb.append("\"events\": [\n");
		Event[] events = timeline.getEvents();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			Event event = events[frame];
			Event setup = event.getData().getSetupPose();
			sb.append("{ \"time\": ").append(fmt(event.getTime())).append(", \"name\": ");
			quote(event.getData().getName(), sb);
			if (event.getInt() != setup.getInt()) sb.append(", \"int\": ").append(event.getInt());
			if (event.getFloat() != setup.getFloat()) appendFloatMember(sb, "float", event.getFloat());
			if (!event.getString().equals(setup.getString()))
				appendStringMember(sb, "string", event.getString());
			if (event.getData().getAudioPath() != null) {
				if (event.getVolume() != setup.getVolume()) appendFloatMember(sb, "volume", event.getVolume());
				if (event.getBalance() != setup.getBalance()) appendFloatMember(sb, "balance", event.getBalance());
			}
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writeAttachmentTimeline(StringBuilder sb, AttachmentTimeline timeline) {
		sb.append("\"attachment\": [\n");
		String[] names = timeline.getAttachmentNames();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			sb.append("{ \"time\": ").append(fmt(timeline.getFrames()[frame]));
			if (names[frame] != null) {
				sb.append(", \"name\": ");
				quote(names[frame], sb);
			}
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writeColorTimeline(StringBuilder sb, RGBATimeline timeline) {
		sb.append("\"rgba\": [\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			sb.append("{ \"time\": ").append(fmt(frames[base])).append(", \"color\": ");
			quote(colorHex(frames[base + 1], frames[base + 2], frames[base + 3], frames[base + 4]), sb);
			writeCurve(sb, timeline, frame);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writeRgbTimeline(StringBuilder sb, RGBTimeline timeline) {
		sb.append("\"rgb\": [\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			sb.append("{ \"time\": ").append(fmt(frames[base])).append(", \"color\": ");
			quote(rgbHex(frames[base + 1], frames[base + 2], frames[base + 3]), sb);
			writeCurve(sb, timeline, frame);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writeAlphaTimeline(StringBuilder sb, AlphaTimeline timeline) {
		sb.append("\"alpha\": [\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			sb.append("{ \"time\": ").append(fmt(frames[base]));
			appendFloatMember(sb, "value", frames[base + 1]);
			writeCurve(sb, timeline, frame);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static void writeTwoColorTimeline(StringBuilder sb, Animation.Timeline timeline) {
		boolean hasAlpha = timeline instanceof RGBA2Timeline;
		sb.append(hasAlpha ? "\"rgba2\": [\n" : "\"rgb2\": [\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		int darkOffset = hasAlpha ? 5 : 4;
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			sb.append("{ \"time\": ").append(fmt(frames[base])).append(", \"light\": ");
			quote(hasAlpha
				? colorHex(frames[base + 1], frames[base + 2], frames[base + 3], frames[base + 4])
				: rgbHex(frames[base + 1], frames[base + 2], frames[base + 3]), sb);
			sb.append(", \"dark\": ");
			quote(rgbHex(frames[base + darkOffset], frames[base + darkOffset + 1],
				frames[base + darkOffset + 2]), sb);
			writeCurve(sb, (CurveTimeline)timeline, frame);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	private static BoneData findBone(SkeletonData d, int idx) {
		for (int i = 0; i < d.getBones().size; i++) {
			if (d.getBones().get(i).getIndex() == idx) return d.getBones().get(i);
		}
		return null;
	}

	/** 输出单个骨骼时间线：{"rotate": [...]} 或 {"translate": [...]} 等 */
	private static void writeBoneTimeline(StringBuilder sb, Animation.Timeline tl) {
		if (tl instanceof InheritTimeline inherit) {
			writeInheritTimeline(sb, inherit);
			return;
		}
		String key;
		boolean twoVal = tl instanceof BoneTimeline2;
		if (tl instanceof RotateTimeline) key = "rotate";
		else if (tl instanceof TranslateTimeline) key = "translate";
		else if (tl instanceof com.esotericsoftware.spine.Animation.TranslateXTimeline) key = "translatex";
		else if (tl instanceof com.esotericsoftware.spine.Animation.TranslateYTimeline) key = "translatey";
		else if (tl instanceof ScaleTimeline) key = "scale";
		else if (tl instanceof com.esotericsoftware.spine.Animation.ScaleXTimeline) key = "scalex";
		else if (tl instanceof com.esotericsoftware.spine.Animation.ScaleYTimeline) key = "scaley";
		else if (tl instanceof ShearTimeline) key = "shear";
		else if (tl instanceof ShearXTimeline) key = "shearx";
		else if (tl instanceof ShearYTimeline) key = "sheary";
		else return;

		sb.append('"').append(key).append("\": [\n");
		int n = tl.getFrameCount();
		int e = tl.getFrameEntries();
		float[] frames = tl.getFrames();
		for (int f = 0; f < n; f++) {
			sb.append("{ \"time\": ").append(fmt(frames[f * e]));
			if (twoVal) {
				sb.append(", \"x\": ").append(fmt(frames[f * e + 1]));
				sb.append(", \"y\": ").append(fmt(frames[f * e + 2]));
			} else {
				sb.append(", \"value\": ").append(fmt(frames[f * e + 1]));
			}
			writeCurve(sb, (CurveTimeline)tl, f);
			sb.append(" }");
			if (f < n - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append("]");
	}

	private static void writeInheritTimeline(StringBuilder sb, InheritTimeline timeline) {
		sb.append("\"inherit\": [\n");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		Inherit[] values = Inherit.values();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			float stored = frames[base + 1];
			int ordinal = (int)stored;
			if (!Float.isFinite(stored) || Float.compare(stored, ordinal) != 0
				|| ordinal < 0 || ordinal >= values.length)
				throw new IllegalArgumentException("无法导出无效的骨骼继承模式：" + stored);
			sb.append("{ \"time\": ").append(fmt(frames[base])).append(", \"inherit\": ");
			quote(values[ordinal].name(), sb);
			sb.append(" }");
			if (frame < timeline.getFrameCount() - 1) sb.append(',');
			sb.append('\n');
		}
		sb.append(']');
	}

	// ================= 工具 =================

	private static void val(String k, String v, StringBuilder sb) {
		quote(k, sb);
		sb.append(": ");
		quote(v, sb);
		sb.append(",\n");
	}

	private static void val(String k, float v, StringBuilder sb) {
		sb.append('"').append(k).append("\": ").append(fmt(v)).append(",\n");
	}

	private static void trimComma(StringBuilder sb) {
		if (sb.length() >= 2 && sb.charAt(sb.length() - 2) == ',') {
			sb.deleteCharAt(sb.length() - 2);
		}
	}

	private static void appendFloat(String prefix, float v, StringBuilder sb) {
		sb.append(prefix).append(fmt(v));
	}

	private static void appendFloatMember(StringBuilder sb, String name, float value) {
		sb.append(", \"").append(name).append("\": ").append(fmt(value));
	}

	private static boolean appendMemberName(StringBuilder sb, String name, boolean first) {
		sb.append(first ? " \"" : ", \"").append(name).append("\": ");
		return false;
	}

	private static boolean appendStringField(StringBuilder sb, String name, String value, boolean first) {
		first = appendMemberName(sb, name, first);
		quote(value, sb);
		return first;
	}

	private static boolean appendFloatField(StringBuilder sb, String name, float value, boolean first) {
		first = appendMemberName(sb, name, first);
		sb.append(fmt(value));
		return first;
	}

	private static boolean appendOptionalFloatField(StringBuilder sb, String name, float value, float defaultValue,
		boolean first) {
		return Float.compare(value, defaultValue) == 0 ? first : appendFloatField(sb, name, value, first);
	}

	private static boolean appendIntField(StringBuilder sb, String name, int value, boolean first) {
		first = appendMemberName(sb, name, first);
		sb.append(value);
		return first;
	}

	private static boolean appendBooleanField(StringBuilder sb, String name, boolean value, boolean first) {
		first = appendMemberName(sb, name, first);
		sb.append(value);
		return first;
	}

	private static boolean appendOptionalBooleanField(StringBuilder sb, String name, boolean value,
		boolean defaultValue, boolean first) {
		return value == defaultValue ? first : appendBooleanField(sb, name, value, first);
	}

	private static boolean appendColorField(StringBuilder sb, String name, Color color, boolean first) {
		return appendStringField(sb, name, rgbaHex(color), first);
	}

	private static boolean appendFloatArrayField(StringBuilder sb, String name, float[] values, boolean first) {
		if (values == null) throw unsupported("数组字段 " + name + " 缺失");
		first = appendMemberName(sb, name, first);
		sb.append('[');
		appendFloatArrayValues(sb, values);
		sb.append(']');
		return first;
	}

	private static void appendFloatArrayValues(StringBuilder sb, float[] values) {
		for (int index = 0; index < values.length; index++) {
			if (index > 0) sb.append(", ");
			sb.append(fmt(values[index]));
		}
	}

	private static boolean appendShortArrayField(StringBuilder sb, String name, short[] values, boolean first) {
		if (values == null) throw unsupported("数组字段 " + name + " 缺失");
		first = appendMemberName(sb, name, first);
		sb.append('[');
		for (int index = 0; index < values.length; index++) {
			if (index > 0) sb.append(", ");
			sb.append(values[index] & 0xffff);
		}
		sb.append(']');
		return first;
	}

	private static void appendStringMember(StringBuilder sb, String name, String value) {
		sb.append(", \"").append(name).append("\": ");
		quote(value, sb);
	}

	private static String fmt(float v) {
		if (!Float.isFinite(v)) throw new IllegalArgumentException("无法导出非有限数值：" + v);
		if (v == (long) v) {
			long l = (long) v;
			if (Math.abs(l) < 1e15) {
				String r = Long.toString(l);
				if (v >= 0) return r;
				return r;
			}
		}
		return Float.toString(v);
	}

	private static void writeCurve(StringBuilder sb, CurveTimeline timeline, int frame) {
		writeCurve(sb, timeline, frame, timeline.getFrameEntries() - 1);
	}

	private static void writeCurve(StringBuilder sb, CurveTimeline timeline, int frame, int channels) {
		if (frame < 0 || frame >= timeline.getFrameCount() - 1) return;
		int type = timeline.getCurveType(frame);
		if (type == CurveTimeline.STEPPED) {
			sb.append(", \"curve\": \"stepped\"");
			return;
		}
		if (type < CurveTimeline.BEZIER) return;
		float[] controls = bezierControls(timeline, frame, channels);
		sb.append(", \"curve\": [");
		for (int index = 0; index < controls.length; index++) {
			if (index > 0) sb.append(", ");
			sb.append(fmt(controls[index]));
		}
		sb.append(']');
	}

	private static void writeDeformCurve(StringBuilder sb, DeformTimeline timeline, int frame) {
		if (frame < 0 || frame >= timeline.getFrameCount() - 1) return;
		int type = timeline.getCurveType(frame);
		if (type == CurveTimeline.STEPPED) {
			sb.append(", \"curve\": \"stepped\"");
			return;
		}
		if (type < CurveTimeline.BEZIER) return;
		float[] controls = deformBezierControls(timeline, frame);
		sb.append(", \"curve\": [");
		for (int index = 0; index < controls.length; index++) {
			if (index > 0) sb.append(", ");
			sb.append(fmt(controls[index]));
		}
		sb.append(']');
	}

	/** Recovers cubic control points from Spine's samples at t=0.3 and t=0.7. */
	private static float[] bezierControls(CurveTimeline timeline, int frame) {
		return bezierControls(timeline, frame, timeline.getFrameEntries() - 1);
	}

	static float[] bezierControls(CurveTimeline timeline, int frame, int channels) {
		int type = timeline.getCurveType(frame);
		if (type < CurveTimeline.BEZIER) throw new IllegalArgumentException("指定帧不是贝塞尔曲线");
		int entries = timeline.getFrameEntries();
		if (channels < 1 || channels >= entries) throw new IllegalArgumentException("贝塞尔曲线通道数量无效");
		float[] curves = curveData(timeline);
		int firstBlock = type - CurveTimeline.BEZIER;
		int required = channels * CurveTimeline.BEZIER_SIZE;
		if (firstBlock < timeline.getFrameCount() || firstBlock + required > curves.length)
			throw new IllegalStateException("无法无损导出：Spine 贝塞尔曲线数据不完整");

		float[] frames = timeline.getFrames();
		int firstFrame = frame * entries;
		int nextFrame = firstFrame + entries;
		float[] controls = new float[channels * 4];
		for (int channel = 0; channel < channels; channel++) {
			int block = firstBlock + channel * CurveTimeline.BEZIER_SIZE;
			float[] x = solveControls(frames[firstFrame], frames[nextFrame], curves, block);
			float[] y = solveControls(frames[firstFrame + channel + 1], frames[nextFrame + channel + 1],
				curves, block + 1);
			int target = channel * 4;
			controls[target] = x[0];
			controls[target + 1] = y[0];
			controls[target + 2] = x[1];
			controls[target + 3] = y[1];
		}
		return controls;
	}

	static float[] deformBezierControls(DeformTimeline timeline, int frame) {
		int type = timeline.getCurveType(frame);
		if (type < CurveTimeline.BEZIER) throw new IllegalArgumentException("指定帧不是贝塞尔曲线");
		float[] curves = curveData(timeline);
		int block = type - CurveTimeline.BEZIER;
		if (block < timeline.getFrameCount() || block + CurveTimeline.BEZIER_SIZE > curves.length)
			throw new IllegalStateException("无法无损导出：Spine Deform 贝塞尔曲线数据不完整");
		float[] x = solveControls(timeline.getFrames()[frame], timeline.getFrames()[frame + 1],
			curves, block);
		float[] y = solveControls(0f, 1f, curves, block + 1);
		return new float[] {x[0], y[0], x[1], y[1]};
	}

	private static float[] solveControls(float start, float end, float[] samples, int offset) {
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
			throw new IllegalStateException("无法无损导出：Spine 贝塞尔控制点不是有限数值");
		return new float[] {first, second};
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
			throw new IllegalStateException("无法读取 Spine 贝塞尔曲线数据", error);
		}
	}

	static void validateSupported(SkeletonData data) {
		validateSupported(data, true);
	}

	private static void validateSupported(SkeletonData data, boolean validateJsonRepresentation) {
		validateSkeletonStructure(data);
		if (!SkeletonDocument.isValidProjectFps(data.getFps()))
			throw new IllegalArgumentException("项目 FPS 必须在 1 到 1000 之间");
		validateConstraints(data);
		validateEventDefinitions(data);
		for (Skin skin : data.getSkins()) {
			validateSkinActivation(data, skin);
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				validateAttachment(data, skin, entry);
			}
		}
		validateSharedAttachmentReferences(data);
		validateAttachmentTimelineInheritance(data);
		if (validateJsonRepresentation) validateJsonMeshTimelineSlots(data);
		for (Animation animation : data.getAnimations()) {
			EventTimeline eventTimeline = null;
			DrawOrderTimeline drawOrderTimeline = null;
			boolean[] ikTimelines = new boolean[data.getConstraints().size];
			boolean[] transformTimelines = new boolean[data.getConstraints().size];
			java.util.HashSet<String> pathTimelines = new java.util.HashSet<>();
			java.util.HashSet<String> physicsTimelines = new java.util.HashSet<>();
			java.util.HashSet<String> sliderTimelines = new java.util.HashSet<>();
			java.util.IdentityHashMap<VertexAttachment, Boolean> deformTimelines = new java.util.IdentityHashMap<>();
			java.util.IdentityHashMap<Attachment, Boolean> sequenceTimelines = new java.util.IdentityHashMap<>();
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (!isSupportedTimeline(timeline))
					throw unsupported("动画 " + animation.getName() + " 包含未支持的轨道 "
						+ timeline.getClass().getSimpleName());
				validateTimelineTarget(data, animation, timeline);
				if (timeline instanceof EventTimeline events) {
					if (eventTimeline != null)
						throw unsupported("动画 " + animation.getName() + " 包含多个事件轨道");
					eventTimeline = events;
					validateEventTimeline(data, animation, events);
				}
				if (timeline instanceof DrawOrderTimeline drawOrder) {
					if (drawOrderTimeline != null)
						throw unsupported("动画 " + animation.getName() + " 包含多个绘制顺序轨道");
					drawOrderTimeline = drawOrder;
					validateDrawOrderTimeline(data, animation, drawOrder);
				}
				if (timeline instanceof DrawOrderFolderTimeline drawOrderFolder)
					validateDrawOrderFolderTimeline(data, animation, drawOrderFolder);
				if (timeline instanceof IkConstraintTimeline ik) {
					int index = ik.getConstraintIndex();
					if (ikTimelines[index])
						throw unsupported("动画 " + animation.getName() + " 包含重复的 IK 轨道 "
							+ data.getConstraints().get(index).getName());
					ikTimelines[index] = true;
					validateIkTimeline(animation, ik);
				}
				if (timeline instanceof TransformConstraintTimeline transform) {
					int index = transform.getConstraintIndex();
					if (transformTimelines[index])
						throw unsupported("Animation " + animation.getName()
							+ " contains duplicate Transform timelines for "
							+ data.getConstraints().get(index).getName());
					transformTimelines[index] = true;
					validateTransformTimeline(animation, transform);
				}
				String pathProperty = pathTimelineName(timeline);
				if (pathProperty != null) {
					int index = ((ConstraintTimeline)timeline).getConstraintIndex();
					if (!pathTimelines.add(index + ":" + pathProperty))
						throw unsupported("Animation " + animation.getName() + " contains duplicate Path "
							+ pathProperty + " timelines for " + data.getConstraints().get(index).getName());
					validateConstraintValueTimeline(animation, timeline, "Path " + pathProperty,
						timeline instanceof PathConstraintMixTimeline ? 4 : 2);
				}
				String physicsProperty = physicsTimelineName(timeline);
				if (physicsProperty != null) {
					int index = ((ConstraintTimeline)timeline).getConstraintIndex();
					if (!physicsTimelines.add(index + ":" + physicsProperty))
						throw unsupported("Animation " + animation.getName() + " contains duplicate Physics "
							+ physicsProperty + " timelines for " + physicsConstraintLabel(data, index));
					if (timeline instanceof PhysicsConstraintResetTimeline)
						validateResetTimeline(animation, timeline, "Physics reset");
					else {
						validateConstraintValueTimeline(animation, timeline, "Physics " + physicsProperty, 2);
						if (timeline instanceof PhysicsConstraintMassTimeline) validatePhysicsMassTimeline(animation, timeline);
					}
				}
				String sliderProperty = sliderTimelineName(timeline);
				if (sliderProperty != null) {
					int index = ((ConstraintTimeline)timeline).getConstraintIndex();
					if (!sliderTimelines.add(index + ":" + sliderProperty))
						throw unsupported("Animation " + animation.getName() + " contains duplicate Slider "
							+ sliderProperty + " timelines for " + data.getConstraints().get(index).getName());
					validateConstraintValueTimeline(animation, timeline, "Slider " + sliderProperty, 2);
				}
				if (timeline instanceof DeformTimeline deform) {
					if (deformTimelines.put(deform.getAttachment(), Boolean.TRUE) != null)
						throw unsupported("动画 " + animation.getName() + " 包含同一附件的重复 Deform 轨道");
					validateDeformTimeline(animation, deform);
				}
				if (timeline instanceof SequenceTimeline sequence) {
					if (sequenceTimelines.put(sequence.getAttachment(), Boolean.TRUE) != null)
						throw unsupported("动画 " + animation.getName() + " 包含同一附件的重复 Sequence 轨道");
					validateSequenceTimeline(animation, sequence);
				}
				if (timeline instanceof CurveTimeline curve)
					for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++)
						if (curve.getCurveType(frame) >= CurveTimeline.BEZIER)
							if (timeline instanceof DeformTimeline deform)
								deformBezierControls(deform, frame);
							else
								bezierControls(curve, frame, timeline instanceof IkConstraintTimeline ? 2
									: timeline.getFrameEntries() - 1);
			}
		}
	}

	private static void validateSkeletonStructure(SkeletonData data) {
		if (!allFinite(data.getX(), data.getY(), data.getWidth(), data.getHeight(), data.getReferenceScale()))
			throw new IllegalArgumentException("Skeleton bounds and reference scale must be finite");
		if (data.getReferenceScale() <= 0f)
			throw new IllegalArgumentException("Skeleton reference scale must be greater than zero");

		java.util.HashSet<String> boneNames = new java.util.HashSet<>();
		java.util.IdentityHashMap<BoneData, Integer> boneIndices = new java.util.IdentityHashMap<>();
		for (int index = 0; index < data.getBones().size; index++) {
			BoneData bone = data.getBones().get(index);
			if (bone == null) throw new IllegalArgumentException("Bone data cannot be null at index " + index);
			validateUniqueName("Bone", bone.getName(), index, boneNames);
			if (bone.getIndex() != index)
				throw new IllegalArgumentException("Bone " + bone.getName() + " has index " + bone.getIndex()
					+ " but is stored at index " + index);
			if (bone.getSetupPose().getInherit() == null)
				throw new IllegalArgumentException("Bone " + bone.getName() + " has no setup inherit mode");
			BoneData parent = bone.getParent();
			if (index == 0) {
				if (parent != null) throw new IllegalArgumentException("Root bone cannot have a parent");
			} else {
				Integer parentIndex = parent == null ? null : boneIndices.get(parent);
				if (parentIndex == null)
					throw new IllegalArgumentException("Bone " + bone.getName()
						+ " must reference a parent stored before it");
			}
			boneIndices.put(bone, index);
		}

		java.util.HashSet<String> slotNames = new java.util.HashSet<>();
		for (int index = 0; index < data.getSlots().size; index++) {
			SlotData slot = data.getSlots().get(index);
			if (slot == null) throw new IllegalArgumentException("Slot data cannot be null at index " + index);
			validateUniqueName("Slot", slot.getName(), index, slotNames);
			if (slot.getIndex() != index)
				throw new IllegalArgumentException("Slot " + slot.getName() + " has index " + slot.getIndex()
					+ " but is stored at index " + index);
			if (!boneIndices.containsKey(slot.getBoneData()))
				throw new IllegalArgumentException("Slot " + slot.getName() + " references a missing bone");
		}

		java.util.HashSet<String> skinNames = new java.util.HashSet<>();
		Skin namedDefaultSkin = null;
		for (int index = 0; index < data.getSkins().size; index++) {
			Skin skin = data.getSkins().get(index);
			if (skin == null) throw new IllegalArgumentException("Skin data cannot be null at index " + index);
			validateUniqueName("Skin", skin.getName(), index, skinNames);
			if ("default".equals(skin.getName())) namedDefaultSkin = skin;
		}
		Skin defaultSkin = data.getDefaultSkin();
		if (defaultSkin != null && !data.getSkins().contains(defaultSkin, true))
			throw new IllegalArgumentException("Default skin is not present in the skeleton skin list");
		if (defaultSkin != namedDefaultSkin)
			throw unsupported("the default skin must be the skin named default");

		java.util.HashSet<String> animationNames = new java.util.HashSet<>();
		for (int index = 0; index < data.getAnimations().size; index++) {
			Animation animation = data.getAnimations().get(index);
			if (animation == null)
				throw new IllegalArgumentException("Animation data cannot be null at index " + index);
			validateUniqueName("Animation", animation.getName(), index, animationNames);
			if (animation.getTimelines() == null)
				throw new IllegalArgumentException("Animation " + animation.getName() + " has no timeline collection");
			for (int timelineIndex = 0; timelineIndex < animation.getTimelines().size; timelineIndex++)
				if (animation.getTimelines().get(timelineIndex) == null)
					throw new IllegalArgumentException("Animation " + animation.getName()
						+ " contains a null timeline at index " + timelineIndex);
		}
	}

	private static void validateUniqueName(String kind, String name, int index, java.util.Set<String> names) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException(kind + " name cannot be empty at index " + index);
		if (!names.add(name)) throw new IllegalArgumentException(kind + " name is duplicated: " + name);
	}

	private static void validateAttachmentTimelineInheritance(SkeletonData data) {
		java.util.IdentityHashMap<Attachment, Boolean> targets = new java.util.IdentityHashMap<>();
		for (Animation animation : data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (timeline instanceof DeformTimeline deform) targets.put(deform.getAttachment(), Boolean.TRUE);
				if (timeline instanceof SequenceTimeline sequence) targets.put(sequence.getAttachment(), Boolean.TRUE);
			}
		}
		if (targets.isEmpty()) return;
		for (Skin skin : data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				Attachment attachment = entry.getAttachment();
				if (attachment instanceof MeshAttachment) continue;
				Attachment inherited = attachment.getTimelineAttachment();
				if (inherited != attachment && (targets.containsKey(attachment)
					|| inherited != null && targets.containsKey(inherited)))
					throw unsupported("皮肤 " + skin.getName() + " 的附件 " + entry.getPlaceholder()
						+ " 使用了 JSON 无法表示的附件时间线继承");
			}
		}
	}

	private static void validateSharedAttachmentReferences(SkeletonData data) {
		java.util.IdentityHashMap<Attachment, Boolean> referenced = new java.util.IdentityHashMap<>();
		for (Animation animation : data.getAnimations()) {
			for (Animation.Timeline timeline : animation.getTimelines()) {
				if (timeline instanceof DeformTimeline deform) referenced.put(deform.getAttachment(), Boolean.TRUE);
				if (timeline instanceof SequenceTimeline sequence) referenced.put(sequence.getAttachment(), Boolean.TRUE);
			}
		}
		for (Skin skin : data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				Attachment attachment = entry.getAttachment();
				if (attachment instanceof MeshAttachment mesh && mesh.getSourceMesh() != null)
					referenced.put(mesh.getSourceMesh(), Boolean.TRUE);
				Attachment timelineAttachment = attachment.getTimelineAttachment();
				if (timelineAttachment != null && timelineAttachment != attachment)
					referenced.put(timelineAttachment, Boolean.TRUE);
			}
		}
		if (referenced.isEmpty()) return;

		java.util.IdentityHashMap<Attachment, AttachmentLocation> owners = new java.util.IdentityHashMap<>();
		for (Skin skin : data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				Attachment attachment = entry.getAttachment();
				AttachmentLocation previous = owners.putIfAbsent(attachment,
					new AttachmentLocation(skin, entry.getSlotIndex(), entry.getPlaceholder()));
				if (previous == null || !referenced.containsKey(attachment)) continue;
				throw unsupported("附件 " + attachment.getName() + " 同时由皮肤 " + previous.skin().getName()
					+ " / " + skin.getName() + " 共享，并被附件时间线或 Linked Mesh 引用；保存后无法保持引用身份");
			}
		}
	}

	/** JSON derives these slots from inherited Linked Mesh entries rather than storing the array. */
	private static void validateJsonMeshTimelineSlots(SkeletonData data) {
		java.util.IdentityHashMap<MeshAttachment, java.util.Set<Integer>> expected =
			new java.util.IdentityHashMap<>();
		for (Skin skin : data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (!(entry.getAttachment() instanceof MeshAttachment linked)) continue;
				MeshAttachment source = linked.getSourceMesh();
				if (source == null || linked.getTimelineAttachment() != source) continue;
				AttachmentLocation sourceLocation = findAttachmentLocation(data, source);
				if (sourceLocation != null && sourceLocation.slotIndex() != entry.getSlotIndex())
					expected.computeIfAbsent(source, ignored -> new java.util.HashSet<>())
						.add(entry.getSlotIndex());
			}
		}
		for (Skin skin : data.getSkins()) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if (!(entry.getAttachment() instanceof MeshAttachment mesh)) continue;
				java.util.Set<Integer> derived = expected.get(mesh);
				int[] actual = mesh.getTimelineSlots();
				if (actual.length == (derived == null ? 0 : derived.size())) {
					boolean matches = true;
					if (derived != null) for (int slot : actual) matches &= derived.contains(slot);
					if (matches) continue;
				}
				throw unsupported("网格 " + mesh.getName()
					+ " 的跨槽时间线引用无法由 JSON 中的 Linked Mesh 无损重建");
			}
		}
	}

	private static void validateAttachment(SkeletonData data, Skin skin, Skin.SkinEntry entry) {
		if (entry.getSlotIndex() < 0 || entry.getSlotIndex() >= data.getSlots().size)
			throw unsupported("皮肤 " + skin.getName() + " 的附件槽索引无效：" + entry.getSlotIndex());
		if (entry.getPlaceholder() == null || entry.getPlaceholder().isEmpty())
			throw new IllegalArgumentException("皮肤 " + skin.getName() + " 包含空附件占位名");
		Attachment attachment = entry.getAttachment();
		if (attachment == null)
			throw new IllegalArgumentException("皮肤 " + skin.getName() + " 的附件 " + entry.getPlaceholder() + " 为 null");
		if (attachment.getName() == null || attachment.getName().isEmpty())
			throw new IllegalArgumentException("皮肤 " + skin.getName() + " 包含空附件名称");
		validateAttachmentTimelineMetadata(data, attachment);

		if (attachment instanceof RegionAttachment region) {
			validatePath(region.getPath(), attachment);
			validateSequence(region.getSequence(), attachment);
			return;
		}
		if (attachment instanceof BoundingBoxAttachment) return;
		if (attachment instanceof MeshAttachment mesh) {
			validatePath(mesh.getPath(), attachment);
			validateSequence(mesh.getSequence(), attachment);
			MeshAttachment source = mesh.getSourceMesh();
			if (source == null) {
				if (mesh.getTimelineAttachment() != mesh)
					throw unsupported("普通网格 " + mesh.getName() + " 的时间线附件引用无法用 JSON 表示");
				if (mesh.getRegionUVs() == null || mesh.getRegionUVs().length != mesh.getWorldVerticesLength())
					throw unsupported("网格 " + mesh.getName() + " 的 UV 数量与顶点数量不一致");
				if (mesh.getTriangles() == null)
					throw unsupported("网格 " + mesh.getName() + " 缺少三角形数据");
				if ((mesh.getHullLength() & 1) != 0 || mesh.getHullLength() < 0
					|| mesh.getHullLength() > mesh.getWorldVerticesLength())
					throw unsupported("网格 " + mesh.getName() + " 的 hull 长度无效");
			} else {
				if (source == mesh || findAttachmentLocation(data, source) == null)
					throw unsupported("Linked Mesh " + mesh.getName() + " 的源网格无效");
				if (source.getSourceMesh() != null)
					throw unsupported("Linked Mesh " + mesh.getName()
						+ " 的源网格本身也是 Linked Mesh，保存后无法稳定重建嵌套来源");
				if (mesh.getTimelineAttachment() != mesh && mesh.getTimelineAttachment() != source)
					throw unsupported("Linked Mesh " + mesh.getName() + " 的时间线继承关系无法用 JSON 表示");
			}
			return;
		}
		if (attachment instanceof PathAttachment path) {
			int count = vertexCount(path);
			if (path.getLengths() == null || path.getLengths().length != count / 3)
				throw unsupported("路径附件 " + path.getName() + " 的曲线长度数据不完整");
			return;
		}
		if (attachment instanceof PointAttachment) return;
		if (attachment instanceof ClippingAttachment clipping) {
			if (clipping.getEndSlot() != null && !data.getSlots().contains(clipping.getEndSlot(), true))
				throw unsupported("裁剪附件 " + clipping.getName() + " 引用了不存在的结束槽");
			return;
		}
		throw unsupported("附件 " + entry.getPlaceholder() + " 的类型 "
			+ attachment.getClass().getSimpleName() + " 尚不能无损导出");
	}

	private static void validateAttachmentTimelineMetadata(SkeletonData data, Attachment attachment) {
		int[] timelineSlots = attachment.getTimelineSlots();
		if (timelineSlots == null)
			throw new IllegalArgumentException("附件 " + attachment.getName() + " 的时间线槽列表为 null");
		java.util.HashSet<Integer> seen = new java.util.HashSet<>();
		for (int slot : timelineSlots) {
			if (slot < 0 || slot >= data.getSlots().size)
				throw new IllegalArgumentException("附件 " + attachment.getName() + " 引用了无效的时间线槽索引 " + slot);
			if (!seen.add(slot))
				throw new IllegalArgumentException("附件 " + attachment.getName() + " 重复引用时间线槽索引 " + slot);
		}
		if (!(attachment instanceof MeshAttachment)) {
			if (attachment.getTimelineAttachment() != attachment)
				throw unsupported("附件 " + attachment.getName() + " 的时间线继承关系无法保存");
			if (timelineSlots.length != 0)
				throw unsupported("附件 " + attachment.getName() + " 的跨槽时间线引用无法保存");
		}
	}

	private static void validatePath(String path, Attachment attachment) {
		if (path == null || path.isEmpty())
			throw unsupported("附件 " + attachment.getName() + " 缺少图片路径");
	}

	private static void validateSequence(Sequence sequence, Attachment attachment) {
		if (sequence == null || sequence.getRegions() == null || sequence.getRegions().length == 0)
			throw unsupported("附件 " + attachment.getName() + " 的序列数据无效");
		if (!sequence.hasPathSuffix() && sequence.getRegions().length != 1)
			throw unsupported("附件 " + attachment.getName() + " 的无后缀序列无法用 JSON 表示");
		if (sequence.getDigits() < 0 || sequence.getSetupIndex() < 0
			|| sequence.getSetupIndex() >= sequence.getRegions().length)
			throw unsupported("附件 " + attachment.getName() + " 的序列设置无效");
	}

	private static void validateConstraints(SkeletonData data) {
		java.util.HashSet<String> names = new java.util.HashSet<>();
		for (ConstraintData<?, ?> constraint : data.getConstraints()) {
			if (constraint == null) throw new IllegalArgumentException("Constraint data cannot be null");
			if (constraint.getName() == null || constraint.getName().isEmpty() || !names.add(constraint.getName()))
				throw new IllegalArgumentException("Constraint name is empty or duplicated: " + constraint.getName());
			if (constraint instanceof IkConstraintData ik) validateIkConstraint(data, ik);
			else if (constraint instanceof TransformConstraintData transform)
				validateTransformConstraint(data, transform);
			else if (constraint instanceof PathConstraintData path)
				validatePathConstraint(data, path);
			else if (constraint instanceof PhysicsConstraintData physics)
				validatePhysicsConstraint(data, physics);
			else if (constraint instanceof SliderData slider)
				validateSliderConstraint(data, slider);
			else throw unsupported("Constraint " + constraint.getName() + " of type "
				+ constraint.getClass().getSimpleName() + " is not supported for export");
		}
	}

	private static void validateIkConstraint(SkeletonData data, IkConstraintData ik) {
		if (ik.getBones().size < 1 || ik.getBones().size > 2)
			throw new IllegalArgumentException("IK 约束 " + ik.getName() + " 必须包含 1 或 2 个骨骼");
		for (BoneData bone : ik.getBones())
			if (bone == null || !data.getBones().contains(bone, true))
				throw new IllegalArgumentException("IK 约束 " + ik.getName() + " 引用了不存在的骨骼");
		if (ik.getTarget() == null || !data.getBones().contains(ik.getTarget(), true))
			throw new IllegalArgumentException("IK 约束 " + ik.getName() + " 的目标骨骼无效");
		if (ik.getScaleYMode() == null)
			throw new IllegalArgumentException("IK 约束 " + ik.getName() + " 的 Y 缩放模式无效");
		validateIkValue(ik.getSetupPose(), "IK 约束 " + ik.getName() + " 的 Setup 值");
	}

	private static void validateTransformConstraint(SkeletonData data, TransformConstraintData transform) {
		String context = "Transform constraint " + transform.getName();
		if (transform.getBones().isEmpty())
			throw new IllegalArgumentException(context + " must constrain at least one bone");
		java.util.IdentityHashMap<BoneData, Boolean> seenBones = new java.util.IdentityHashMap<>();
		for (BoneData bone : transform.getBones()) {
			if (bone == null || !data.getBones().contains(bone, true))
				throw new IllegalArgumentException(context + " references a missing constrained bone");
			if (seenBones.put(bone, Boolean.TRUE) != null)
				throw new IllegalArgumentException(context + " contains a duplicate constrained bone");
		}
		if (transform.getSource() == null || !data.getBones().contains(transform.getSource(), true))
			throw new IllegalArgumentException(context + " references a missing source bone");
		if (!allFinite(transform.getOffsetRotation(), transform.getOffsetX(), transform.getOffsetY(),
			transform.getOffsetScaleX(), transform.getOffsetScaleY(), transform.getOffsetShearY()))
			throw new IllegalArgumentException(context + " offsets must be finite");

		java.util.HashSet<String> fromNames = new java.util.HashSet<>();
		for (FromProperty from : transform.getProperties()) {
			if (from == null) throw new IllegalArgumentException(context + " contains a null source property");
			String fromName = transformPropertyName(from);
			if (!fromNames.add(fromName))
				throw unsupported(context + " contains duplicate source property " + fromName);
			if (!Float.isFinite(from.offset))
				throw new IllegalArgumentException(context + " source property values must be finite");
			if (from.to.isEmpty())
				throw unsupported(context + " contains a source property without targets");
			java.util.HashSet<String> toNames = new java.util.HashSet<>();
			for (ToProperty to : from.to) {
				if (to == null) throw new IllegalArgumentException(context + " contains a null target property");
				String toName = transformPropertyName(to);
				if (!toNames.add(toName))
					throw unsupported(context + " maps one source to target " + toName + " more than once");
				if (!allFinite(to.offset, to.max, to.scale))
					throw new IllegalArgumentException(context + " target property values must be finite");
			}
		}
		TransformConstraintPose setup = transform.getSetupPose();
		if (!allFinite(setup.getMixRotate(), setup.getMixX(), setup.getMixY(), setup.getMixScaleX(),
			setup.getMixScaleY(), setup.getMixShearY()))
			throw new IllegalArgumentException(context + " setup mixes must be finite");
		validateAbsentTransformMix(context, hasTransformTarget(transform, ToRotate.class),
			setup.getMixRotate(), "rotation");
		validateAbsentTransformMix(context, hasTransformTarget(transform, ToX.class), setup.getMixX(), "X");
		validateAbsentTransformMix(context, hasTransformTarget(transform, ToY.class), setup.getMixY(), "Y");
		validateAbsentTransformMix(context, hasTransformTarget(transform, ToScaleX.class),
			setup.getMixScaleX(), "scale X");
		validateAbsentTransformMix(context, hasTransformTarget(transform, ToScaleY.class),
			setup.getMixScaleY(), "scale Y");
		validateAbsentTransformMix(context, hasTransformTarget(transform, ToShearY.class),
			setup.getMixShearY(), "shear Y");
	}

	private static void validatePathConstraint(SkeletonData data, PathConstraintData path) {
		String context = "Path constraint " + path.getName();
		if (path.getBones().isEmpty())
			throw new IllegalArgumentException(context + " must constrain at least one bone");
		java.util.IdentityHashMap<BoneData, Boolean> seenBones = new java.util.IdentityHashMap<>();
		for (BoneData bone : path.getBones()) {
			if (bone == null || !data.getBones().contains(bone, true))
				throw new IllegalArgumentException(context + " references a missing constrained bone");
			if (seenBones.put(bone, Boolean.TRUE) != null)
				throw new IllegalArgumentException(context + " contains a duplicate constrained bone");
		}
		if (path.getSlot() == null || !data.getSlots().contains(path.getSlot(), true))
			throw new IllegalArgumentException(context + " references a missing target slot");
		if (path.getPositionMode() == null || path.getSpacingMode() == null || path.getRotateMode() == null)
			throw new IllegalArgumentException(context + " has an invalid mode");
		PathConstraintPose setup = path.getSetupPose();
		if (!allFinite(path.getOffsetRotation(), setup.getPosition(), setup.getSpacing(), setup.getMixRotate(),
			setup.getMixX(), setup.getMixY()))
			throw new IllegalArgumentException(context + " setup values must be finite");
	}

	private static void validatePhysicsConstraint(SkeletonData data, PhysicsConstraintData physics) {
		String context = "Physics constraint " + physics.getName();
		if (physics.getBone() == null || !data.getBones().contains(physics.getBone(), true))
			throw new IllegalArgumentException(context + " references a missing bone");
		if (physics.getScaleYMode() == null)
			throw new IllegalArgumentException(context + " has an invalid scale Y mode");
		if (!allFinite(physics.getX(), physics.getY(), physics.getRotate(), physics.getScaleX(),
			physics.getShearX(), physics.getLimit()))
			throw new IllegalArgumentException(context + " setup values must be finite");
		physicsFps(physics.getStep(), context);
		PhysicsConstraintPose setup = physics.getSetupPose();
		if (!allFinite(setup.getInertia(), setup.getStrength(), setup.getDamping(), setup.getMassInverse(),
			setup.getWind(), setup.getGravity(), setup.getMix()))
			throw new IllegalArgumentException(context + " setup values must be finite");
		float mass = 1f / setup.getMassInverse();
		if (setup.getMassInverse() <= 0f || !Float.isFinite(mass) || mass <= 0f)
			throw new IllegalArgumentException(context + " mass must be finite and greater than zero");
	}

	private static void validateSliderConstraint(SkeletonData data, SliderData slider) {
		String context = "Slider constraint " + slider.getName();
		if (slider.getAnimation() == null || !data.getAnimations().contains(slider.getAnimation(), true))
			throw new IllegalArgumentException(context + " references a missing animation");
		if (slider.getLoop() && !(slider.getAnimation().getDuration() > 0f))
			throw new IllegalArgumentException(context + " loops an animation whose duration is zero");
		SliderPose setup = slider.getSetupPose();
		if (!allFinite(setup.getTime(), setup.getMix(), slider.getOffset(), slider.getScale(), slider.getMax()))
			throw new IllegalArgumentException(context + " setup values must be finite");
		if (slider.getBone() == null) {
			if (slider.getProperty() != null)
				throw unsupported(context + " has a property but no source bone");
		} else {
			if (!data.getBones().contains(slider.getBone(), true))
				throw new IllegalArgumentException(context + " references a missing bone");
			if (slider.getProperty() == null)
				throw new IllegalArgumentException(context + " has a source bone but no property");
			transformPropertyName(slider.getProperty());
			if (!Float.isFinite(slider.getProperty().offset))
				throw new IllegalArgumentException(context + " source property offset must be finite");
		}
	}

	private static int physicsFps(float step, String context) {
		if (!Float.isFinite(step) || step <= 0f)
			throw new IllegalArgumentException(context + " step must be finite and greater than zero");
		float reciprocal = 1f / step;
		if (!Float.isFinite(reciprocal) || reciprocal < 1f || reciprocal > Integer.MAX_VALUE)
			throw unsupported(context + " step cannot be represented by a positive integer FPS");
		int fps = Math.round(reciprocal);
		if (fps <= 0 || Float.compare(step, 1f / fps) != 0)
			throw unsupported(context + " step cannot be represented losslessly by an integer FPS");
		return fps;
	}

	private static void validateAbsentTransformMix(String context, boolean present, float value, String channel) {
		if (!present && value != 0f)
			throw unsupported(context + " has a " + channel + " mix but no property maps to that channel");
	}

	private static void validateSkinActivation(SkeletonData data, Skin skin) {
		for (BoneData bone : skin.getBones())
			if (bone == null || !data.getBones().contains(bone, true))
				throw unsupported("皮肤 " + skin.getName() + " 引用了不存在的骨骼");
		for (ConstraintData<?, ?> constraint : skin.getConstraints()) {
			if (!(constraint instanceof IkConstraintData || constraint instanceof TransformConstraintData
				|| constraint instanceof PathConstraintData || constraint instanceof PhysicsConstraintData
				|| constraint instanceof SliderData)
				|| !data.getConstraints().contains(constraint, true))
				throw unsupported("皮肤 " + skin.getName() + " 包含无效或尚不支持的约束激活数据");
		}
	}

	private static void validateIkTimeline(Animation animation, IkConstraintTimeline timeline) {
		if (timeline.getFrameCount() == 0)
			throw unsupported("动画 " + animation.getName() + " 包含空 IK 轨道");
		float previousTime = -1f;
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * timeline.getFrameEntries();
			float[] values = timeline.getFrames();
			float time = values[base];
			if (!Float.isFinite(time) || time < 0f)
				throw new IllegalArgumentException("动画 " + animation.getName() + " 的 IK 时间无效：" + time);
			if (frame > 0 && time <= previousTime)
				throw unsupported("动画 " + animation.getName() + " 的 IK 关键帧时间未严格递增");
			previousTime = time;
			IkConstraintPose pose = new IkConstraintPose();
			pose.setMix(values[base + 1]);
			pose.setSoftness(values[base + 2]);
			pose.setBendDirection((int)values[base + 3]);
			pose.setCompress(values[base + 4] != 0f);
			pose.setStretch(values[base + 5] != 0f);
			if (Float.compare(values[base + 3], pose.getBendDirection()) != 0
				|| values[base + 4] != 0f && values[base + 4] != 1f
				|| values[base + 5] != 0f && values[base + 5] != 1f)
				throw new IllegalArgumentException("动画 " + animation.getName() + " 的 IK 离散值无效");
			validateIkValue(pose, "动画 " + animation.getName() + " 的 IK 关键帧");
		}
	}

	private static void validateTransformTimeline(Animation animation, TransformConstraintTimeline timeline) {
		if (timeline.getFrameCount() == 0)
			throw unsupported("Animation " + animation.getName() + " contains an empty Transform timeline");
		float previousTime = -1f;
		float[] values = timeline.getFrames();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * timeline.getFrameEntries();
			float time = values[base];
			if (!Float.isFinite(time) || time < 0f)
				throw new IllegalArgumentException("Animation " + animation.getName()
					+ " has an invalid Transform key time: " + time);
			if (frame > 0 && time <= previousTime)
				throw unsupported("Animation " + animation.getName()
					+ " Transform key times are not strictly increasing");
			previousTime = time;
			if (!allFinite(values[base + 1], values[base + 2], values[base + 3], values[base + 4],
				values[base + 5], values[base + 6]))
				throw new IllegalArgumentException("Animation " + animation.getName()
					+ " Transform key mixes must be finite");
		}
	}

	private static void validateConstraintValueTimeline(Animation animation, Animation.Timeline timeline,
		String label, int expectedEntries) {
		if (timeline.getFrameCount() == 0)
			throw unsupported("Animation " + animation.getName() + " contains an empty " + label + " timeline");
		if (timeline.getFrameEntries() != expectedEntries
			|| timeline.getFrames().length != timeline.getFrameCount() * expectedEntries)
			throw unsupported("Animation " + animation.getName() + " has malformed " + label + " frame data");
		float previousTime = -1f;
		float[] values = timeline.getFrames();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * expectedEntries;
			float time = values[base];
			if (!Float.isFinite(time) || time < 0f)
				throw new IllegalArgumentException("Animation " + animation.getName()
					+ " has an invalid " + label + " key time: " + time);
			if (frame > 0 && time <= previousTime)
				throw unsupported("Animation " + animation.getName() + " " + label
					+ " key times are not strictly increasing");
			previousTime = time;
			for (int value = 1; value < expectedEntries; value++)
				if (!Float.isFinite(values[base + value]))
					throw new IllegalArgumentException("Animation " + animation.getName()
						+ " " + label + " key values must be finite");
		}
	}

	private static void validateResetTimeline(Animation animation, Animation.Timeline timeline, String label) {
		if (timeline.getFrameCount() == 0)
			throw unsupported("Animation " + animation.getName() + " contains an empty " + label + " timeline");
		if (timeline.getFrameEntries() != 1 || timeline.getFrames().length != timeline.getFrameCount())
			throw unsupported("Animation " + animation.getName() + " has malformed " + label + " frame data");
		float previousTime = -1f;
		for (float time : timeline.getFrames()) {
			if (!Float.isFinite(time) || time < 0f)
				throw new IllegalArgumentException("Animation " + animation.getName()
					+ " has an invalid " + label + " key time: " + time);
			if (time <= previousTime)
				throw unsupported("Animation " + animation.getName() + " " + label
					+ " key times are not strictly increasing");
			previousTime = time;
		}
	}

	private static void validatePhysicsMassTimeline(Animation animation, Animation.Timeline timeline) {
		float[] frames = timeline.getFrames();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			float mass = frames[frame * timeline.getFrameEntries() + 1];
			if (mass <= 0f)
				throw new IllegalArgumentException("Animation " + animation.getName()
					+ " Physics mass keys must be greater than zero");
		}
	}

	private static void validateDeformTimeline(Animation animation, DeformTimeline timeline) {
		if (timeline.getFrameCount() == 0)
			throw unsupported("动画 " + animation.getName() + " 包含空 Deform 轨道");
		VertexAttachment attachment = timeline.getAttachment();
		float[] setupVertices = attachment.getVertices();
		if (setupVertices == null)
			throw unsupported("动画 " + animation.getName() + " 的 Deform 附件没有顶点数据");
		int deformLength = attachment.getBones() == null ? setupVertices.length : setupVertices.length / 3 * 2;
		float[][] vertices = timeline.getVertices();
		if (vertices == null || vertices.length != timeline.getFrameCount())
			throw unsupported("动画 " + animation.getName() + " 的 Deform 帧数据不完整");
		float previousTime = -1f;
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			float time = timeline.getFrames()[frame];
			if (!Float.isFinite(time) || time < 0f)
				throw new IllegalArgumentException("动画 " + animation.getName() + " 的 Deform 时间无效：" + time);
			if (frame > 0 && time <= previousTime)
				throw unsupported("动画 " + animation.getName() + " 的 Deform 关键帧时间未严格递增");
			previousTime = time;
			float[] frameVertices = vertices[frame];
			if (frameVertices == null || frameVertices.length != deformLength)
				throw unsupported("动画 " + animation.getName() + " 的 Deform 顶点数量与附件不一致");
			for (float value : frameVertices)
				if (!Float.isFinite(value))
					throw new IllegalArgumentException("动画 " + animation.getName() + " 的 Deform 顶点必须为有限数值");
		}
	}

	private static void validateSequenceTimeline(Animation animation, SequenceTimeline timeline) {
		if (timeline.getFrameCount() == 0)
			throw unsupported("动画 " + animation.getName() + " 包含空 Sequence 轨道");
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		float previousTime = -1f;
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			float time = frames[base];
			if (!Float.isFinite(time) || time < 0f)
				throw new IllegalArgumentException("动画 " + animation.getName() + " 的 Sequence 时间无效：" + time);
			if (frame > 0 && time <= previousTime)
				throw unsupported("动画 " + animation.getName() + " 的 Sequence 关键帧时间未严格递增");
			previousTime = time;
			float encodedValue = frames[base + 1];
			int modeAndIndex = (int)encodedValue;
			int mode = modeAndIndex & 0xf;
			int index = modeAndIndex >> 4;
			if (!Float.isFinite(encodedValue) || encodedValue != modeAndIndex
				|| mode < 0 || mode >= SequenceMode.values.length || index < 0)
				throw new IllegalArgumentException("动画 " + animation.getName() + " 的 Sequence 模式或索引无效");
			float delay = frames[base + 2];
			if (!Float.isFinite(delay) || mode != SequenceMode.hold.ordinal() && delay <= 0f)
				throw new IllegalArgumentException("动画 " + animation.getName() + " 的 Sequence 帧间隔无效：" + delay);
		}
	}

	private static void validateIkValue(IkConstraintPose pose, String context) {
		if (!Float.isFinite(pose.getMix()) || pose.getMix() < 0f || pose.getMix() > 1f)
			throw new IllegalArgumentException(context + "的混合必须在 0 到 1 之间");
		if (!Float.isFinite(pose.getSoftness()) || pose.getSoftness() < 0f)
			throw new IllegalArgumentException(context + "的柔度必须是不小于 0 的有限数值");
		if (pose.getBendDirection() != -1 && pose.getBendDirection() != 1)
			throw new IllegalArgumentException(context + "的弯曲方向必须是 -1 或 1");
	}

	private static void validateDrawOrderTimeline(SkeletonData data, Animation animation,
		DrawOrderTimeline timeline) {
		if (timeline.getFrameCount() == 0)
			throw unsupported("动画 " + animation.getName() + " 包含空绘制顺序轨道");
		float previousTime = -1f;
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			float time = timeline.getFrames()[frame];
			if (!Float.isFinite(time) || time < 0f)
				throw new IllegalArgumentException("动画 " + animation.getName() + " 的绘制顺序时间无效：" + time);
			if (frame > 0 && time <= previousTime)
				throw unsupported("动画 " + animation.getName() + " 的绘制顺序关键帧时间未严格递增");
			previousTime = time;
			int[] order = timeline.getDrawOrders()[frame];
			if (order != null) drawOrderPositions(order, data.getSlots().size);
		}
	}

	private static void validateDrawOrderFolderTimeline(SkeletonData data, Animation animation,
		DrawOrderFolderTimeline timeline) {
		if (timeline.getFrameCount() == 0)
			throw unsupported("Animation " + animation.getName() + " contains an empty draw order folder timeline");
		int[] slots = timeline.getSlots();
		if (slots == null || slots.length == 0)
			throw unsupported("Animation " + animation.getName() + " contains a draw order folder without slots");
		boolean[] seen = new boolean[data.getSlots().size];
		for (int slot : slots) {
			if (slot < 0 || slot >= seen.length)
				throw unsupported("Animation " + animation.getName()
					+ " draw order folder references a missing slot index " + slot);
			if (seen[slot])
				throw unsupported("Animation " + animation.getName()
					+ " draw order folder contains duplicate slot index " + slot);
			seen[slot] = true;
		}
		float previousTime = -1f;
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			float time = timeline.getFrames()[frame];
			if (!Float.isFinite(time) || time < 0f)
				throw new IllegalArgumentException("Animation " + animation.getName()
					+ " has an invalid draw order folder key time: " + time);
			if (frame > 0 && time <= previousTime)
				throw unsupported("Animation " + animation.getName()
					+ " draw order folder key times are not strictly increasing");
			previousTime = time;
			int[] order = timeline.getDrawOrders()[frame];
			if (order != null) drawOrderPositions(order, slots.length);
		}
	}

	private static void validateEventDefinitions(SkeletonData data) {
		java.util.HashSet<String> names = new java.util.HashSet<>();
		for (int index = 0; index < data.getEvents().size; index++) {
			EventData definition = data.getEvents().get(index);
			if (definition == null)
				throw new IllegalArgumentException("事件定义不能为 null（索引 " + index + "）");
			String name = definition.getName();
			if (name == null || name.isEmpty())
				throw new IllegalArgumentException("事件名称不能为空（索引 " + index + "）");
			if (!names.add(name))
				throw new IllegalArgumentException("事件名称重复：" + name);
			Event setup = definition.getSetupPose();
			if (setup == null || setup.getData() != definition)
				throw new IllegalArgumentException("事件 " + name + " 的 setup 数据无效");
			validateEventPayload(setup, "事件 " + name + " 的 setup 值");
		}
	}

	private static void validateEventTimeline(SkeletonData data, Animation animation, EventTimeline timeline) {
		if (timeline.getFrameCount() == 0)
			throw unsupported("动画 " + animation.getName() + " 包含空事件轨道");
		Event[] events = timeline.getEvents();
		float[] frames = timeline.getFrames();
		if (events == null || events.length != timeline.getFrameCount() || frames.length != timeline.getFrameCount())
			throw unsupported("动画 " + animation.getName() + " 的事件轨道帧数据不完整");
		float previousTime = -1f;
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			Event event = events[frame];
			if (event == null)
				throw unsupported("动画 " + animation.getName() + " 的事件轨道包含空关键帧");
			float time = event.getTime();
			if (!Float.isFinite(time) || time < 0f)
				throw new IllegalArgumentException("动画 " + animation.getName() + " 的事件时间无效：" + time);
			if (!Float.isFinite(frames[frame]) || Float.compare(frames[frame], time) != 0)
				throw unsupported("动画 " + animation.getName() + " 的事件关键帧时间不一致");
			if (frame > 0 && time < previousTime)
				throw unsupported("动画 " + animation.getName() + " 的事件关键帧未按时间排序");
			previousTime = time;
			EventData definition = event.getData();
			if (definition == null || !data.getEvents().contains(definition, true))
				throw unsupported("动画 " + animation.getName() + " 引用了不存在的事件定义");
			validateEventPayload(event,
				"动画 " + animation.getName() + " 的事件 " + definition.getName() + " 关键帧");
		}
	}

	private static void validateEventPayload(Event event, String context) {
		if (event.getString() == null)
			throw new IllegalArgumentException(context + "的字符串不能为 null");
		if (!Float.isFinite(event.getFloat()))
			throw new IllegalArgumentException(context + "的浮点值必须为有限数");
		if (!Float.isFinite(event.getVolume()))
			throw new IllegalArgumentException(context + "的音量必须为有限数");
		if (!Float.isFinite(event.getBalance()))
			throw new IllegalArgumentException(context + "的声道平衡必须为有限数");
	}

	private static void validateTimelineTarget(SkeletonData data, Animation animation,
		Animation.Timeline timeline) {
		if (timeline instanceof IkConstraintTimeline ik) {
			int index = ik.getConstraintIndex();
			if (index < 0 || index >= data.getConstraints().size
				|| !(data.getConstraints().get(index) instanceof IkConstraintData))
				throw unsupported("动画 " + animation.getName() + " 的 IK 轨道引用了不存在的 IK 约束索引 " + index);
		}
		if (timeline instanceof TransformConstraintTimeline transform) {
			int index = transform.getConstraintIndex();
			if (index < 0 || index >= data.getConstraints().size
				|| !(data.getConstraints().get(index) instanceof TransformConstraintData))
				throw unsupported("Animation " + animation.getName()
					+ " references a missing Transform constraint index " + index);
		}
		if (pathTimelineName(timeline) != null) {
			int index = ((ConstraintTimeline)timeline).getConstraintIndex();
			if (index < 0 || index >= data.getConstraints().size
				|| !(data.getConstraints().get(index) instanceof PathConstraintData))
				throw unsupported("Animation " + animation.getName()
					+ " references a missing Path constraint index " + index);
		}
		if (physicsTimelineName(timeline) != null) {
			int index = ((ConstraintTimeline)timeline).getConstraintIndex();
			if (index < -1 || index >= data.getConstraints().size
				|| index >= 0 && !(data.getConstraints().get(index) instanceof PhysicsConstraintData))
				throw unsupported("Animation " + animation.getName()
					+ " references a missing Physics constraint index " + index);
			if (index == -1) {
				AnimationEditor.PhysicsProp prop = AnimationEditor.physicsPropertyOf(timeline);
				boolean hasEffectiveTarget = prop == AnimationEditor.PhysicsProp.RESET
					? hasPhysicsConstraint(data) : AnimationEditor.hasGlobalPhysicsProperty(data, prop);
				if (!hasEffectiveTarget)
					throw unsupported("Animation " + animation.getName() + " has a global Physics "
						+ physicsTimelineName(timeline) + " timeline with no effective target");
			}
		}
		if (sliderTimelineName(timeline) != null) {
			int index = ((ConstraintTimeline)timeline).getConstraintIndex();
			if (index < 0 || index >= data.getConstraints().size
				|| !(data.getConstraints().get(index) instanceof SliderData))
				throw unsupported("Animation " + animation.getName()
					+ " references a missing Slider constraint index " + index);
		}
		if (timeline instanceof BoneTimeline boneTimeline) {
			int boneIndex = boneTimeline.getBoneIndex();
			if (boneIndex < 0 || boneIndex >= data.getBones().size)
				throw unsupported("动画 " + animation.getName() + " 的 "
					+ timeline.getClass().getSimpleName() + " 轨道引用了不存在的骨骼索引 " + boneIndex);
		}
		if (!(timeline instanceof Animation.SlotTimeline slotTimeline)) return;
		int slotIndex = slotTimeline.getSlotIndex();
		if (slotIndex < 0 || slotIndex >= data.getSlots().size)
			throw unsupported("动画 " + animation.getName() + " 的 "
				+ timeline.getClass().getSimpleName() + " 轨道引用了不存在的槽索引 " + slotIndex);
		if ((timeline instanceof RGB2Timeline || timeline instanceof RGBA2Timeline)
			&& data.getSlots().get(slotIndex).getSetupPose().getDarkColor() == null)
			throw unsupported("动画 " + animation.getName() + " 的 "
				+ timeline.getClass().getSimpleName() + " 轨道引用的槽 "
				+ data.getSlots().get(slotIndex).getName() + " 尚未启用双色染色");
		if (timeline instanceof DeformTimeline deform) {
			VertexAttachment attachment = deform.getAttachment();
			if (findAttachmentLocation(data, attachment, slotIndex) == null)
				throw unsupported("动画 " + animation.getName() + " 的 Deform 轨道目标附件不在指定槽中");
			if (attachment instanceof MeshAttachment mesh && mesh.getSourceMesh() != null
				&& mesh.getTimelineAttachment() != mesh)
				throw unsupported("动画 " + animation.getName()
					+ " 的 Deform 轨道目标是继承时间线的 Linked Mesh，轨道不会生效");
		}
		if (timeline instanceof SequenceTimeline sequence) {
			Attachment attachment = sequence.getAttachment();
			if (!(attachment instanceof HasSequence hasSequence) || hasSequence.getSequence() == null)
				throw unsupported("动画 " + animation.getName() + " 的 Sequence 轨道目标没有图片序列");
			if (findAttachmentLocation(data, attachment, slotIndex) == null)
				throw unsupported("动画 " + animation.getName() + " 的 Sequence 轨道目标附件不在指定槽中");
			if (attachment instanceof MeshAttachment mesh && mesh.getSourceMesh() != null
				&& mesh.getTimelineAttachment() != mesh)
				throw unsupported("动画 " + animation.getName()
					+ " 的 Sequence 轨道目标是继承时间线的 Linked Mesh，轨道不会生效");
		}
	}

	private static boolean hasPhysicsConstraint(SkeletonData data) {
		for (ConstraintData<?, ?> constraint : data.getConstraints())
			if (constraint instanceof PhysicsConstraintData) return true;
		return false;
	}

	private static boolean isSupportedTimeline(Animation.Timeline timeline) {
		return timeline instanceof RotateTimeline || timeline instanceof TranslateTimeline
			|| timeline instanceof com.esotericsoftware.spine.Animation.TranslateXTimeline
			|| timeline instanceof com.esotericsoftware.spine.Animation.TranslateYTimeline
			|| timeline instanceof ScaleTimeline
			|| timeline instanceof com.esotericsoftware.spine.Animation.ScaleXTimeline
			|| timeline instanceof com.esotericsoftware.spine.Animation.ScaleYTimeline
			|| timeline instanceof ShearTimeline
			|| timeline instanceof ShearXTimeline
			|| timeline instanceof ShearYTimeline
			|| timeline instanceof InheritTimeline
			|| timeline instanceof AttachmentTimeline
			|| timeline instanceof RGBATimeline || timeline instanceof RGBTimeline
			|| timeline instanceof AlphaTimeline || timeline instanceof RGB2Timeline
			|| timeline instanceof RGBA2Timeline || timeline instanceof DrawOrderTimeline
			|| timeline instanceof DrawOrderFolderTimeline
			|| timeline instanceof EventTimeline || timeline instanceof IkConstraintTimeline
			|| timeline instanceof TransformConstraintTimeline
			|| pathTimelineName(timeline) != null || physicsTimelineName(timeline) != null
			|| sliderTimelineName(timeline) != null
			|| timeline instanceof DeformTimeline || timeline instanceof SequenceTimeline;
	}

	private static String physicsConstraintLabel(SkeletonData data, int index) {
		return index == -1 ? "all Physics constraints" : data.getConstraints().get(index).getName();
	}

	private static boolean allFinite(float... values) {
		for (float value : values) if (!Float.isFinite(value)) return false;
		return true;
	}

	private static IllegalStateException unsupported(String detail) {
		return new IllegalStateException("无法无损导出：" + detail);
	}

	private static boolean isWhite(Color color) {
		return color != null && color.r == 1f && color.g == 1f && color.b == 1f && color.a == 1f;
	}

	private static String colorHex(float red, float green, float blue, float alpha) {
		for (float channel : new float[] {red, green, blue, alpha}) {
			if (!Float.isFinite(channel) || channel < 0f || channel > 1f)
				throw new IllegalArgumentException("无法导出超出 0 到 1 范围的颜色通道：" + channel);
		}
		return new Color(red, green, blue, alpha).toString();
	}

	private static String rgbHex(float red, float green, float blue) {
		return colorHex(red, green, blue, 1f).substring(0, 6);
	}

	private static String rgbaHex(Color color) {
		if (color == null) throw new IllegalArgumentException("无法导出空颜色");
		return colorHex(color.r, color.g, color.b, color.a);
	}

	private static String rgbHex(Color color) {
		if (color == null) throw new IllegalArgumentException("无法导出空颜色");
		for (float channel : new float[] {color.r, color.g, color.b}) {
			if (!Float.isFinite(channel) || channel < 0f || channel > 1f)
				throw new IllegalArgumentException("无法导出超出 0 到 1 范围的颜色通道：" + channel);
		}
		return new Color(color.r, color.g, color.b, 1f).toString().substring(0, 6);
	}

	private static void quote(String value, StringBuilder sb) {
		if (value == null) {
			sb.append("null");
			return;
		}
		sb.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '"': sb.append("\\\""); break;
			case '\\': sb.append("\\\\"); break;
			case '\b': sb.append("\\b"); break;
			case '\f': sb.append("\\f"); break;
			case '\n': sb.append("\\n"); break;
			case '\r': sb.append("\\r"); break;
			case '\t': sb.append("\\t"); break;
			default:
				if (c < 0x20) {
					sb.append("\\u");
					String hex = Integer.toHexString(c);
					for (int pad = hex.length(); pad < 4; pad++) sb.append('0');
					sb.append(hex);
				} else {
					sb.append(c);
				}
			}
		}
		sb.append('"');
	}
}
