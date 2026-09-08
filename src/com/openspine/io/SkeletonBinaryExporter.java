package com.openspine.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.DataOutput;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Animation.AlphaTimeline;
import com.esotericsoftware.spine.Animation.AttachmentTimeline;
import com.esotericsoftware.spine.Animation.BoneTimeline;
import com.esotericsoftware.spine.Animation.BoneTimeline2;
import com.esotericsoftware.spine.Animation.ConstraintTimeline;
import com.esotericsoftware.spine.Animation.CurveTimeline;
import com.esotericsoftware.spine.Animation.DeformTimeline;
import com.esotericsoftware.spine.Animation.DrawOrderFolderTimeline;
import com.esotericsoftware.spine.Animation.DrawOrderTimeline;
import com.esotericsoftware.spine.Animation.EventTimeline;
import com.esotericsoftware.spine.Animation.IkConstraintTimeline;
import com.esotericsoftware.spine.Animation.InheritTimeline;
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
import com.esotericsoftware.spine.Animation.PhysicsConstraintWindTimeline;
import com.esotericsoftware.spine.Animation.RGB2Timeline;
import com.esotericsoftware.spine.Animation.RGBA2Timeline;
import com.esotericsoftware.spine.Animation.RGBATimeline;
import com.esotericsoftware.spine.Animation.RGBTimeline;
import com.esotericsoftware.spine.Animation.RotateTimeline;
import com.esotericsoftware.spine.Animation.ScaleTimeline;
import com.esotericsoftware.spine.Animation.ScaleXTimeline;
import com.esotericsoftware.spine.Animation.ScaleYTimeline;
import com.esotericsoftware.spine.Animation.SequenceTimeline;
import com.esotericsoftware.spine.Animation.ShearTimeline;
import com.esotericsoftware.spine.Animation.ShearXTimeline;
import com.esotericsoftware.spine.Animation.ShearYTimeline;
import com.esotericsoftware.spine.Animation.SliderMixTimeline;
import com.esotericsoftware.spine.Animation.SliderTimeline;
import com.esotericsoftware.spine.Animation.Timeline;
import com.esotericsoftware.spine.Animation.TransformConstraintTimeline;
import com.esotericsoftware.spine.Animation.TranslateTimeline;
import com.esotericsoftware.spine.Animation.TranslateXTimeline;
import com.esotericsoftware.spine.Animation.TranslateYTimeline;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.IkConstraintPose;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PathConstraintPose;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintPose;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SliderPose;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
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
import com.esotericsoftware.spine.TransformConstraintPose;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.VertexAttachment;
import com.openspine.model.SkeletonDocument;

/** Writes Spine 4.3 binary skeleton data compatible with {@code SkeletonBinary}. */
public final class SkeletonBinaryExporter {
	private static final int DEFAULT_SKIN_COLOR = Color.rgba8888(new Skin("default").getColor());
	private static final int CONSTRAINT_IK = 0;
	private static final int CONSTRAINT_PATH = 1;
	private static final int CONSTRAINT_TRANSFORM = 2;
	private static final int CONSTRAINT_PHYSICS = 3;
	private static final int CONSTRAINT_SLIDER = 4;

	private static final int TYPE_REGION = 0;
	private static final int TYPE_BOUNDING_BOX = 1;
	private static final int TYPE_MESH = 2;
	private static final int TYPE_LINKED_MESH = 3;
	private static final int TYPE_PATH = 4;
	private static final int TYPE_POINT = 5;
	private static final int TYPE_CLIPPING = 6;

	private final SkeletonData data;
	private final DataOutput output;
	private final StringTable strings = new StringTable();
	private final ArrayList<Skin> skins = new ArrayList<>();
	private final IdentityHashMap<Skin, Integer> skinIndices = new IdentityHashMap<>();

	private SkeletonBinaryExporter(SkeletonData data, OutputStream stream) {
		this.data = data;
		this.output = new DataOutput(stream);
		collectSkins();
		collectStrings();
	}

	public static byte[] toBytes(SkeletonDocument document) throws IOException {
		return toBytes(document, new CancellationToken());
	}

	public static byte[] toBytes(SkeletonDocument document, CancellationToken cancellation) throws IOException {
		CancellationToken token = requireCancellation(cancellation);
		token.checkCancelled();
		if (document == null || document.data == null)
			throw new IllegalArgumentException("No skeleton data is available to export");
		// The JSON serializer performs the complete finite-value and vertex encoding preflight.
		// Keep binary output from accepting malformed data that JSON export would reject.
		SkeletonExporter.preflightForBinary(document, token);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(32768);
		new SkeletonBinaryExporter(document.data, bytes).writeSkeleton();
		token.checkCancelled();
		return bytes.toByteArray();
	}

	public static void write(SkeletonDocument document, OutputStream stream) throws IOException {
		if (stream == null) throw new IllegalArgumentException("Output stream is required");
		stream.write(toBytes(document));
	}

	/** Writes the complete binary document before replacing the destination. */
	public static void writeAtomic(SkeletonDocument document, Path target) throws IOException {
		writeAtomic(document, target, new CancellationToken());
	}

	/** Writes atomically and leaves the destination unchanged when cancellation wins before commit. */
	public static void writeAtomic(SkeletonDocument document, Path target, CancellationToken cancellation)
		throws IOException {
		if (target == null) throw new IllegalArgumentException("Save path is required");
		CancellationToken token = requireCancellation(cancellation);
		byte[] bytes = toBytes(document, token);
		Path absoluteTarget = target.toAbsolutePath().normalize();
		Path directory = absoluteTarget.getParent();
		if (directory == null || !Files.isDirectory(directory))
			throw new IOException("Save directory does not exist: " + directory);
		String name = absoluteTarget.getFileName().toString();
		Path temporary = Files.createTempFile(directory, "." + name + ".", ".tmp");
		try {
			try (OutputStream stream = Files.newOutputStream(temporary)) {
				for (int offset = 0; offset < bytes.length; offset += 65536) {
					token.checkCancelled();
					stream.write(bytes, offset, Math.min(65536, bytes.length - offset));
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

	private void writeSkeleton() throws IOException {
		output.writeLong(parseHash(data.getHash()));
		writeString(output, SkeletonExporter.EXPORT_VERSION);
		output.writeFloat(data.getX());
		output.writeFloat(data.getY());
		output.writeFloat(data.getWidth());
		output.writeFloat(data.getHeight());
		output.writeFloat(data.getReferenceScale());
		output.writeBoolean(true);
		output.writeFloat(data.getFps());
		writeString(output, data.getImagesPath());
		writeString(output, data.getAudioPath());

		strings.write(output);
		writeBones();
		writeSlots();
		writeConstraints();
		writeSkins();
		writeEvents();
		writeAnimations();
		writeSliderAnimationIndices();
		output.flush();
	}

	private static long parseHash(String hash) {
		if (hash == null || hash.isBlank()) return 0L;
		try {
			return Long.parseLong(hash);
		} catch (NumberFormatException notDecimal) {
			try {
				byte[] bytes = Base64.getDecoder().decode(hash);
				if (bytes.length != Long.BYTES) throw new IllegalArgumentException();
				long value = 0L;
				for (byte item : bytes) value = value << 8 | item & 0xffL;
				return value;
			} catch (IllegalArgumentException notBase64Long) {
				throw new IllegalArgumentException("Binary skeleton hash must be a decimal or Base64 64-bit value: "
					+ hash, notDecimal);
			}
		}
	}

	private void collectSkins() {
		Skin defaultSkin = data.getDefaultSkin();
		if (defaultSkin != null) addSkin(defaultSkin);
		for (Skin skin : data.getSkins())
			if (skin != defaultSkin) addSkin(skin);
	}

	private void addSkin(Skin skin) {
		if (skinIndices.containsKey(skin)) return;
		skinIndices.put(skin, skins.size());
		skins.add(skin);
	}

	private void collectStrings() {
		for (SlotData slot : data.getSlots()) strings.add(slot.getAttachmentName());
		for (Skin skin : skins) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				String placeholder = entry.getPlaceholder();
				Attachment attachment = entry.getAttachment();
				strings.add(placeholder);
				if (!attachment.getName().equals(placeholder)) strings.add(attachment.getName());
				if (attachment instanceof RegionAttachment region) {
					if (!same(region.getPath(), region.getName())) strings.add(region.getPath());
				} else if (attachment instanceof MeshAttachment mesh) {
					if (!same(mesh.getPath(), mesh.getName())) strings.add(mesh.getPath());
					if (mesh.getSourceMesh() != null) {
						AttachmentLocation source = findAttachment(mesh.getSourceMesh(), -1);
						if (source == null)
							throw new IllegalArgumentException("Linked mesh source is not present in any skin: " + mesh.getName());
						strings.add(source.placeholder());
					}
				}
			}
		}
		for (Animation animation : data.getAnimations()) {
			for (Timeline timeline : animation.getTimelines()) {
				if (timeline instanceof AttachmentTimeline attachments)
					for (String name : attachments.getAttachmentNames()) strings.add(name);
				if (timeline instanceof DeformTimeline deform) {
					AttachmentLocation location = findAttachment(deform.getAttachment(), deform.getSlotIndex());
					if (location != null) strings.add(location.placeholder());
				} else if (timeline instanceof SequenceTimeline sequence) {
					AttachmentLocation location = findAttachment(sequence.getAttachment(), sequence.getSlotIndex());
					if (location != null) strings.add(location.placeholder());
				}
			}
		}
	}

	private void writeBones() throws IOException {
		output.writeInt(data.getBones().size, true);
		for (BoneData bone : data.getBones()) {
			writeString(output, bone.getName());
			if (bone.getIndex() != 0) output.writeInt(bone.getParent().getIndex(), true);
			var setup = bone.getSetupPose();
			output.writeFloat(setup.getRotation());
			output.writeFloat(setup.getX());
			output.writeFloat(setup.getY());
			output.writeFloat(setup.getScaleX());
			output.writeFloat(setup.getScaleY());
			output.writeFloat(setup.getShearX());
			output.writeFloat(setup.getShearY());
			output.writeByte(setup.getInherit().ordinal());
			output.writeFloat(bone.getLength());
			output.writeBoolean(bone.getSkinRequired());
			output.writeInt(Color.rgba8888(bone.getColor()));
			writeString(output, bone.getIcon());
			output.writeFloat(bone.getIconSize());
			output.writeFloat(bone.getIconRotation());
			output.writeBoolean(bone.getVisible());
		}
	}

	private void writeSlots() throws IOException {
		output.writeInt(data.getSlots().size, true);
		for (SlotData slot : data.getSlots()) {
			writeString(output, slot.getName());
			output.writeInt(slot.getBoneData().getIndex(), true);
			Color light = slot.getSetupPose().getColor();
			Color dark = slot.getSetupPose().getDarkColor();
			output.writeInt(Color.rgba8888(light));
			output.writeInt(dark == null ? -1 : Color.rgb888(dark));
			strings.writeRef(output, slot.getAttachmentName());
			output.writeInt(slot.getBlendMode().ordinal(), true);
			output.writeBoolean(slot.getVisible());
		}
	}

	private void writeConstraints() throws IOException {
		output.writeInt(data.getConstraints().size, true);
		for (ConstraintData<?, ?> constraint : data.getConstraints()) {
			writeString(output, constraint.getName());
			if (constraint instanceof IkConstraintData ik) writeIkConstraint(ik);
			else if (constraint instanceof TransformConstraintData transform) writeTransformConstraint(transform);
			else if (constraint instanceof PathConstraintData path) writePathConstraint(path);
			else if (constraint instanceof PhysicsConstraintData physics) writePhysicsConstraint(physics);
			else if (constraint instanceof SliderData slider) writeSliderConstraint(slider);
			else throw new IllegalArgumentException("Unsupported constraint: " + constraint.getClass().getName());
		}
	}

	private void writeIkConstraint(IkConstraintData ik) throws IOException {
		output.writeByte(CONSTRAINT_IK);
		writeBoneIndices(ik.getBones());
		output.writeInt(ik.getTarget().getIndex(), true);
		IkConstraintPose setup = ik.getSetupPose();
		int flags = ik.getSkinRequired() ? 1 : 0;
		if (ik.getScaleYMode() != ConstraintData.ScaleYMode.none) flags |= 2;
		if (setup.getBendDirection() < 0) flags |= 4;
		if (setup.getCompress()) flags |= 8;
		if (setup.getStretch()) flags |= 16;
		if (setup.getMix() != 0f) flags |= 32;
		if (setup.getMix() != 0f && setup.getMix() != 1f) flags |= 64;
		if (setup.getSoftness() != 0f) flags |= 128;
		output.writeByte(flags);
		if ((flags & 2) != 0) output.writeByte(ik.getScaleYMode().ordinal());
		if ((flags & 64) != 0) output.writeFloat(setup.getMix());
		if ((flags & 128) != 0) output.writeFloat(setup.getSoftness());
	}

	private void writeTransformConstraint(TransformConstraintData transform) throws IOException {
		output.writeByte(CONSTRAINT_TRANSFORM);
		writeBoneIndices(transform.getBones());
		output.writeInt(transform.getSource().getIndex(), true);
		int propertyCount = transform.getProperties().size;
		if (propertyCount > 7) throw new IllegalArgumentException("Transform constraint has more than 7 source properties");
		int flags = (transform.getSkinRequired() ? 1 : 0)
			| (transform.getLocalSource() ? 2 : 0)
			| (transform.getLocalTarget() ? 4 : 0)
			| (transform.getAdditive() ? 8 : 0)
			| (transform.getClamp() ? 16 : 0)
			| (propertyCount << 5);
		output.writeByte(flags);
		for (FromProperty from : transform.getProperties()) {
			output.writeByte(propertyType(from));
			output.writeFloat(from.offset);
			if (from.to.size > 255) throw new IllegalArgumentException("Transform property has more than 255 targets");
			output.writeByte(from.to.size);
			for (ToProperty to : from.to) {
				output.writeByte(propertyType(to));
				output.writeFloat(to.offset);
				output.writeFloat(to.max);
				output.writeFloat(to.scale);
			}
		}
		float[] offsets = {transform.getOffsetRotation(), transform.getOffsetX(), transform.getOffsetY(),
			transform.getOffsetScaleX(), transform.getOffsetScaleY(), transform.getOffsetShearY()};
		flags = nonzeroFlags(offsets);
		output.writeByte(flags);
		writeFlaggedFloats(flags, offsets);
		TransformConstraintPose setup = transform.getSetupPose();
		float[] mixes = {setup.getMixRotate(), setup.getMixX(), setup.getMixY(), setup.getMixScaleX(),
			setup.getMixScaleY(), setup.getMixShearY()};
		flags = nonzeroFlags(mixes);
		output.writeByte(flags);
		writeFlaggedFloats(flags, mixes);
	}

	private void writePathConstraint(PathConstraintData path) throws IOException {
		output.writeByte(CONSTRAINT_PATH);
		writeBoneIndices(path.getBones());
		output.writeInt(path.getSlot().getIndex(), true);
		int flags = (path.getSkinRequired() ? 1 : 0)
			| (path.getPositionMode().ordinal() << 1)
			| (path.getSpacingMode().ordinal() << 2)
			| (path.getRotateMode().ordinal() << 4);
		if (path.getOffsetRotation() != 0f) flags |= 128;
		output.writeByte(flags);
		if ((flags & 128) != 0) output.writeFloat(path.getOffsetRotation());
		PathConstraintPose setup = path.getSetupPose();
		output.writeFloat(setup.getPosition());
		output.writeFloat(setup.getSpacing());
		output.writeFloat(setup.getMixRotate());
		output.writeFloat(setup.getMixX());
		output.writeFloat(setup.getMixY());
	}

	private void writePhysicsConstraint(PhysicsConstraintData physics) throws IOException {
		output.writeByte(CONSTRAINT_PHYSICS);
		output.writeInt(physics.getBone().getIndex(), true);
		PhysicsConstraintPose setup = physics.getSetupPose();
		float encodedScaleX = encodePhysicsScaleX(physics);
		int flags = physics.getSkinRequired() ? 1 : 0;
		if (physics.getX() != 0f) flags |= 2;
		if (physics.getY() != 0f) flags |= 4;
		if (physics.getRotate() != 0f) flags |= 8;
		if (physics.getScaleX() != 0f || physics.getScaleYMode() != ConstraintData.ScaleYMode.none) flags |= 16;
		if (physics.getShearX() != 0f) flags |= 32;
		if (physics.getLimit() != 5000f) flags |= 64;
		if (setup.getMassInverse() != 1f) flags |= 128;
		output.writeByte(flags);
		if ((flags & 2) != 0) output.writeFloat(physics.getX());
		if ((flags & 4) != 0) output.writeFloat(physics.getY());
		if ((flags & 8) != 0) output.writeFloat(physics.getRotate());
		if ((flags & 16) != 0) {
			output.writeFloat(encodedScaleX);
		}
		if ((flags & 32) != 0) output.writeFloat(physics.getShearX());
		if ((flags & 64) != 0) output.writeFloat(physics.getLimit());
		float inverseStep = 1f / physics.getStep();
		int fps = Math.round(inverseStep);
		if (fps < 1 || fps > 255 || Math.abs(fps - inverseStep) > 0.0001f)
			throw new IllegalArgumentException("Physics step must be an exact reciprocal in the range 1..255");
		output.writeByte(fps);
		output.writeFloat(setup.getInertia());
		output.writeFloat(setup.getStrength());
		output.writeFloat(setup.getDamping());
		if ((flags & 128) != 0) output.writeFloat(setup.getMassInverse());
		output.writeFloat(setup.getWind());
		output.writeFloat(setup.getGravity());
		flags = (physics.getInertiaGlobal() ? 1 : 0)
			| (physics.getStrengthGlobal() ? 2 : 0)
			| (physics.getDampingGlobal() ? 4 : 0)
			| (physics.getMassGlobal() ? 8 : 0)
			| (physics.getWindGlobal() ? 16 : 0)
			| (physics.getGravityGlobal() ? 32 : 0)
			| (physics.getMixGlobal() ? 64 : 0);
		if (setup.getMix() != 1f) flags |= 128;
		output.writeByte(flags);
		if ((flags & 128) != 0) output.writeFloat(setup.getMix());
	}

	/** Spine binary embeds the Scale Y mode in disjoint ranges of the Scale X float. */
	static float encodePhysicsScaleX(PhysicsConstraintData physics) {
		if (physics == null || physics.getScaleYMode() == null)
			throw new IllegalArgumentException("Physics constraint Scale Y mode is required");
		float scaleX = physics.getScaleX();
		if (!Float.isFinite(scaleX))
			throw new IllegalArgumentException("Physics constraint Scale X must be finite");

		float encoded = switch (physics.getScaleYMode()) {
		case none -> scaleX;
		case uniform -> -1f - scaleX;
		case volume -> -2f - scaleX;
		};
		boolean representable = switch (physics.getScaleYMode()) {
		case none -> encoded >= 0f;
		case uniform -> encoded < 0f && encoded >= -2f;
		case volume -> encoded < -2f;
		};
		if (!Float.isFinite(encoded) || !representable)
			throw new IllegalArgumentException("Physics constraint " + physics.getName() + " Scale X " + scaleX
				+ " with Scale Y mode " + physics.getScaleYMode()
				+ " cannot be represented by the Spine 4.3 binary mode encoding");
		return encoded;
	}

	private void writeSliderConstraint(SliderData slider) throws IOException {
		output.writeByte(CONSTRAINT_SLIDER);
		boolean mapped = slider.getBone() != null && slider.getProperty() != null;
		SliderPose setup = slider.getSetupPose();
		float primary = mapped ? slider.getMax() : setup.getTime();
		int flags = (slider.getSkinRequired() ? 1 : 0)
			| (slider.getLoop() ? 2 : 0)
			| (slider.getAdditive() ? 4 : 0)
			| (primary != 0f ? 8 : 0)
			| (setup.getMix() != 0f ? 16 : 0)
			| (setup.getMix() != 0f && setup.getMix() != 1f ? 32 : 0)
			| (mapped ? 64 : 0)
			| (mapped && slider.getLocal() ? 128 : 0);
		output.writeByte(flags);
		if ((flags & 8) != 0) output.writeFloat(primary);
		if ((flags & 32) != 0) output.writeFloat(setup.getMix());
		if (mapped) {
			output.writeInt(slider.getBone().getIndex(), true);
			output.writeFloat(slider.getProperty().offset);
			output.writeByte(propertyType(slider.getProperty()));
			output.writeFloat(slider.getOffset());
			output.writeFloat(slider.getScale());
		}
	}

	private void writeBoneIndices(Array<BoneData> bones) throws IOException {
		output.writeInt(bones.size, true);
		for (BoneData bone : bones) output.writeInt(bone.getIndex(), true);
	}

	private static int nonzeroFlags(float[] values) {
		int flags = 0;
		for (int i = 0; i < values.length; i++) if (values[i] != 0f) flags |= 1 << i;
		return flags;
	}

	private void writeFlaggedFloats(int flags, float[] values) throws IOException {
		for (int i = 0; i < values.length; i++) if ((flags & 1 << i) != 0) output.writeFloat(values[i]);
	}

	private static int propertyType(Object property) {
		if (property instanceof FromRotate || property instanceof ToRotate) return 0;
		if (property instanceof FromX || property instanceof ToX) return 1;
		if (property instanceof FromY || property instanceof ToY) return 2;
		if (property instanceof FromScaleX || property instanceof ToScaleX) return 3;
		if (property instanceof FromScaleY || property instanceof ToScaleY) return 4;
		if (property instanceof FromShearY || property instanceof ToShearY) return 5;
		throw new IllegalArgumentException("Unsupported transform property: " + property.getClass().getName());
	}

	private void writeSkins() throws IOException {
		Skin defaultSkin = data.getDefaultSkin();
		if (defaultSkin != null) {
			if (defaultSkin.getAttachments().isEmpty())
				throw new IllegalArgumentException("Binary format cannot represent an empty default skin");
			if (defaultSkin.getBones().notEmpty() || defaultSkin.getConstraints().notEmpty())
				throw new IllegalArgumentException(
					"Binary format cannot store bone or constraint activation on the default skin");
			if (Color.rgba8888(defaultSkin.getColor()) != DEFAULT_SKIN_COLOR)
				throw new IllegalArgumentException("Binary format cannot store a color on the default skin");
		}
		if (defaultSkin == null) output.writeInt(0, true);
		else writeSkin(defaultSkin, true);
		int otherCount = skins.size() - (defaultSkin == null ? 0 : 1);
		output.writeInt(otherCount, true);
		for (Skin skin : skins) if (skin != defaultSkin) writeSkin(skin, false);
	}

	private void writeSkin(Skin skin, boolean defaultSkin) throws IOException {
		LinkedHashMap<Integer, List<Skin.SkinEntry>> bySlot = groupSkinEntries(skin);
		if (!defaultSkin) {
			writeString(output, skin.getName());
			output.writeInt(Color.rgba8888(skin.getColor()));
			writeBoneIndices(skin.getBones());
			output.writeInt(skin.getConstraints().size, true);
			for (ConstraintData<?, ?> constraint : skin.getConstraints())
				output.writeInt(indexOfConstraint(constraint), true);
		}
		output.writeInt(bySlot.size(), true);
		for (Map.Entry<Integer, List<Skin.SkinEntry>> slot : bySlot.entrySet()) {
			output.writeInt(slot.getKey(), true);
			output.writeInt(slot.getValue().size(), true);
			for (Skin.SkinEntry entry : slot.getValue()) {
				strings.writeRef(output, entry.getPlaceholder());
				writeAttachment(entry.getAttachment(), entry.getPlaceholder());
			}
		}
	}

	private static LinkedHashMap<Integer, List<Skin.SkinEntry>> groupSkinEntries(Skin skin) {
		LinkedHashMap<Integer, List<Skin.SkinEntry>> grouped = new LinkedHashMap<>();
		for (Skin.SkinEntry entry : skin.getAttachments())
			grouped.computeIfAbsent(entry.getSlotIndex(), ignored -> new ArrayList<>()).add(entry);
		return grouped;
	}

	private void writeAttachment(Attachment attachment, String placeholder) throws IOException {
		boolean customName = !attachment.getName().equals(placeholder);
		if (attachment instanceof RegionAttachment region) {
			int flags = TYPE_REGION | (customName ? 8 : 0);
			boolean customPath = !same(region.getPath(), region.getName());
			if (customPath) flags |= 16;
			if (!isWhite(region.getColor())) flags |= 32;
			if (region.getSequence().hasPathSuffix()) flags |= 64;
			if (region.getRotation() != 0f) flags |= 128;
			output.writeByte(flags);
			if (customName) strings.writeRef(output, region.getName());
			if (customPath) strings.writeRef(output, region.getPath());
			if ((flags & 32) != 0) output.writeInt(Color.rgba8888(region.getColor()));
			writeSequence(region.getSequence());
			if ((flags & 128) != 0) output.writeFloat(region.getRotation());
			output.writeFloat(region.getX());
			output.writeFloat(region.getY());
			output.writeFloat(region.getScaleX());
			output.writeFloat(region.getScaleY());
			output.writeFloat(region.getWidth());
			output.writeFloat(region.getHeight());
			return;
		}
		if (attachment instanceof MeshAttachment mesh) {
			writeMesh(mesh, placeholder, customName);
			return;
		}
		if (attachment instanceof BoundingBoxAttachment box) {
			int flags = TYPE_BOUNDING_BOX | (customName ? 8 : 0) | (box.getBones() != null ? 16 : 0);
			output.writeByte(flags);
			if (customName) strings.writeRef(output, box.getName());
			writeVertices(box);
			output.writeInt(Color.rgba8888(box.getColor()));
			return;
		}
		if (attachment instanceof PathAttachment path) {
			int flags = TYPE_PATH | (customName ? 8 : 0) | (path.getClosed() ? 16 : 0)
				| (path.getConstantSpeed() ? 32 : 0) | (path.getBones() != null ? 64 : 0);
			output.writeByte(flags);
			if (customName) strings.writeRef(output, path.getName());
			writeVertices(path);
			for (float length : path.getLengths()) output.writeFloat(length);
			output.writeInt(Color.rgba8888(path.getColor()));
			return;
		}
		if (attachment instanceof PointAttachment point) {
			output.writeByte(TYPE_POINT | (customName ? 8 : 0));
			if (customName) strings.writeRef(output, point.getName());
			output.writeFloat(point.getRotation());
			output.writeFloat(point.getX());
			output.writeFloat(point.getY());
			output.writeInt(Color.rgba8888(point.getColor()));
			return;
		}
		if (attachment instanceof ClippingAttachment clipping) {
			if (clipping.getEndSlot() == null)
				throw new IllegalArgumentException("Clipping attachment requires an end slot in binary format: "
					+ clipping.getName());
			int flags = TYPE_CLIPPING | (customName ? 8 : 0) | (clipping.getBones() != null ? 16 : 0)
				| (clipping.getConvex() ? 32 : 0) | (clipping.getInverse() ? 64 : 0);
			output.writeByte(flags);
			if (customName) strings.writeRef(output, clipping.getName());
			output.writeInt(clipping.getEndSlot().getIndex(), true);
			writeVertices(clipping);
			output.writeInt(Color.rgba8888(clipping.getColor()));
			return;
		}
		throw new IllegalArgumentException("Unsupported attachment: " + attachment.getClass().getName());
	}

	private void writeMesh(MeshAttachment mesh, String placeholder, boolean customName) throws IOException {
		boolean linked = mesh.getSourceMesh() != null;
		if (linked && mesh.getTimelineSlots().length != 0)
			throw new IllegalArgumentException("Binary format cannot store timeline slots on Linked Mesh: "
				+ mesh.getName());
		boolean customPath = !same(mesh.getPath(), mesh.getName());
		int flags = (linked ? TYPE_LINKED_MESH : TYPE_MESH) | (customName ? 8 : 0)
			| (customPath ? 16 : 0) | (!isWhite(mesh.getColor()) ? 32 : 0)
			| (mesh.getSequence().hasPathSuffix() ? 64 : 0);
		if (linked ? mesh.getTimelineAttachment() != mesh : mesh.getBones() != null) flags |= 128;
		output.writeByte(flags);
		if (customName) strings.writeRef(output, mesh.getName());
		if (customPath) strings.writeRef(output, mesh.getPath());
		if ((flags & 32) != 0) output.writeInt(Color.rgba8888(mesh.getColor()));
		writeSequence(mesh.getSequence());
		if (linked) {
			AttachmentLocation source = findAttachment(mesh.getSourceMesh(), -1);
			if (source == null) throw new IllegalArgumentException("Linked mesh source is missing: " + mesh.getName());
			output.writeInt(source.slotIndex(), true);
			output.writeInt(skinIndices.get(source.skin()), true);
			strings.writeRef(output, source.placeholder());
			output.writeFloat(mesh.getWidth());
			output.writeFloat(mesh.getHeight());
			return;
		}
		output.writeInt(mesh.getHullLength() >> 1, true);
		writeVertices(mesh);
		for (float uv : mesh.getRegionUVs()) output.writeFloat(uv);
		int triangleCount = (mesh.getWorldVerticesLength() - (mesh.getHullLength() >> 1) - 2) * 3;
		if (triangleCount < 0 || mesh.getTriangles().length != triangleCount)
			throw new IllegalArgumentException("Mesh " + mesh.getName() + " has " + mesh.getTriangles().length
				+ " triangle indices, but Spine binary requires " + Math.max(0, triangleCount));
		for (short triangle : mesh.getTriangles()) output.writeInt(triangle & 0xffff, true);
		int[] timelineSlots = mesh.getTimelineSlots();
		output.writeInt(timelineSlots == null ? 0 : timelineSlots.length, true);
		if (timelineSlots != null) for (int slot : timelineSlots) output.writeInt(slot, true);
		short[] edges = mesh.getEdges();
		output.writeInt(edges == null ? 0 : edges.length, true);
		if (edges != null) for (short edge : edges) output.writeInt(edge & 0xffff, true);
		output.writeFloat(mesh.getWidth());
		output.writeFloat(mesh.getHeight());
	}

	private void writeSequence(Sequence sequence) throws IOException {
		if (!sequence.hasPathSuffix()) return;
		if (sequence.getRegions().length == 0)
			throw new IllegalArgumentException("Sequence must contain at least one region");
		output.writeInt(sequence.getRegions().length, true);
		output.writeInt(sequence.getStart(), true);
		output.writeInt(sequence.getDigits(), true);
		output.writeInt(sequence.getSetupIndex(), true);
	}

	private void writeVertices(VertexAttachment attachment) throws IOException {
		output.writeInt(attachment.getWorldVerticesLength() >> 1, true);
		int[] bones = attachment.getBones();
		float[] vertices = attachment.getVertices();
		if (bones == null) {
			for (float vertex : vertices) output.writeFloat(vertex);
			return;
		}
		output.writeInt(bones.length, true);
		int vertex = 0;
		for (int bone = 0; bone < bones.length;) {
			int count = bones[bone++];
			output.writeInt(count, true);
			for (int influence = 0; influence < count; influence++) {
				output.writeInt(bones[bone++], true);
				output.writeFloat(vertices[vertex++]);
				output.writeFloat(vertices[vertex++]);
				output.writeFloat(vertices[vertex++]);
			}
		}
	}

	private void writeEvents() throws IOException {
		output.writeInt(data.getEvents().size, true);
		for (EventData eventData : data.getEvents()) {
			Event setup = eventData.getSetupPose();
			writeString(output, eventData.getName());
			output.writeInt(setup.getInt(), false);
			output.writeFloat(setup.getFloat());
			writeString(output, setup.getString());
			writeString(output, eventData.getAudioPath());
			if (eventData.getAudioPath() != null) {
				output.writeFloat(setup.getVolume());
				output.writeFloat(setup.getBalance());
			}
		}
	}

	private void writeAnimations() throws IOException {
		output.writeInt(data.getAnimations().size, true);
		for (Animation animation : data.getAnimations()) {
			writeString(output, animation.getName());
			writeAnimation(animation);
		}
	}

	private void writeAnimation(Animation animation) throws IOException {
		Array<Timeline> timelines = animation.getTimelines();
		output.writeInt(timelines.size, true);
		writeSlotTimelines(timelines);
		writeBoneTimelines(timelines);
		writeIkTimelines(timelines);
		writeTransformTimelines(timelines);
		writePathTimelines(timelines);
		writePhysicsTimelines(timelines);
		writeSliderTimelines(timelines);
		writeAttachmentTimelines(timelines);
		writeDrawOrderTimelines(timelines);
		writeEventTimeline(timelines);
		output.writeInt(Color.rgba8888(animation.getColor()));
	}

	private void writeSlotTimelines(Array<Timeline> timelines) throws IOException {
		LinkedHashMap<Integer, List<Timeline>> grouped = new LinkedHashMap<>();
		for (Timeline timeline : timelines) {
			if (slotTimelineType(timeline) >= 0) {
				int slot = ((Animation.SlotTimeline)timeline).getSlotIndex();
				grouped.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(timeline);
			}
		}
		output.writeInt(grouped.size(), true);
		for (Map.Entry<Integer, List<Timeline>> group : grouped.entrySet()) {
			output.writeInt(group.getKey(), true);
			output.writeInt(group.getValue().size(), true);
			for (Timeline timeline : group.getValue()) writeSlotTimeline(timeline);
		}
	}

	private void writeSlotTimeline(Timeline timeline) throws IOException {
		int type = slotTimelineType(timeline);
		output.writeByte(type);
		output.writeInt(timeline.getFrameCount(), true);
		if (timeline instanceof AttachmentTimeline attachments) {
			for (int frame = 0; frame < attachments.getFrameCount(); frame++) {
				output.writeFloat(attachments.getFrames()[frame]);
				strings.writeRef(output, attachments.getAttachmentNames()[frame]);
			}
			return;
		}
		CurveTimeline curve = (CurveTimeline)timeline;
		int channels = timeline.getFrameEntries() - 1;
		output.writeInt(bezierCount(curve, channels), true);
		writeColorFrames(curve, channels);
	}

	private void writeColorFrames(CurveTimeline timeline, int channels) throws IOException {
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			output.writeFloat(frames[base]);
			for (int channel = 0; channel < channels; channel++) output.writeByte(colorByte(frames[base + 1 + channel]));
			if (frame > 0) writeCurve(timeline, frame - 1, channels, false);
		}
	}

	private static int slotTimelineType(Timeline timeline) {
		if (timeline instanceof AttachmentTimeline) return 0;
		if (timeline instanceof RGBATimeline) return 1;
		if (timeline instanceof RGBTimeline) return 2;
		if (timeline instanceof RGBA2Timeline) return 3;
		if (timeline instanceof RGB2Timeline) return 4;
		if (timeline instanceof AlphaTimeline) return 5;
		return -1;
	}

	private void writeBoneTimelines(Array<Timeline> timelines) throws IOException {
		LinkedHashMap<Integer, List<Timeline>> grouped = new LinkedHashMap<>();
		for (Timeline timeline : timelines) {
			if (timeline instanceof BoneTimeline bone)
				grouped.computeIfAbsent(bone.getBoneIndex(), ignored -> new ArrayList<>()).add(timeline);
		}
		output.writeInt(grouped.size(), true);
		for (Map.Entry<Integer, List<Timeline>> group : grouped.entrySet()) {
			output.writeInt(group.getKey(), true);
			output.writeInt(group.getValue().size(), true);
			for (Timeline timeline : group.getValue()) writeBoneTimeline(timeline);
		}
	}

	private void writeBoneTimeline(Timeline timeline) throws IOException {
		int type = boneTimelineType(timeline);
		output.writeByte(type);
		output.writeInt(timeline.getFrameCount(), true);
		if (timeline instanceof InheritTimeline inherit) {
			float[] frames = inherit.getFrames();
			int entries = inherit.getFrameEntries();
			for (int frame = 0; frame < inherit.getFrameCount(); frame++) {
				int base = frame * entries;
				output.writeFloat(frames[base]);
				output.writeByte((int)frames[base + 1]);
			}
			return;
		}
		CurveTimeline curve = (CurveTimeline)timeline;
		int channels = timeline instanceof BoneTimeline2 ? 2 : 1;
		output.writeInt(bezierCount(curve, channels), true);
		writeFloatFrames(curve, channels);
	}

	private static int boneTimelineType(Timeline timeline) {
		if (timeline instanceof RotateTimeline) return 0;
		if (timeline instanceof TranslateTimeline) return 1;
		if (timeline instanceof TranslateXTimeline) return 2;
		if (timeline instanceof TranslateYTimeline) return 3;
		if (timeline instanceof ScaleTimeline) return 4;
		if (timeline instanceof ScaleXTimeline) return 5;
		if (timeline instanceof ScaleYTimeline) return 6;
		if (timeline instanceof ShearTimeline) return 7;
		if (timeline instanceof ShearXTimeline) return 8;
		if (timeline instanceof ShearYTimeline) return 9;
		if (timeline instanceof InheritTimeline) return 10;
		throw new IllegalArgumentException("Unsupported bone timeline: " + timeline.getClass().getName());
	}

	private void writeIkTimelines(Array<Timeline> timelines) throws IOException {
		List<IkConstraintTimeline> found = new ArrayList<>();
		for (Timeline timeline : timelines) if (timeline instanceof IkConstraintTimeline ik) found.add(ik);
		output.writeInt(found.size(), true);
		for (IkConstraintTimeline timeline : found) {
			output.writeInt(timeline.getConstraintIndex(), true);
			output.writeInt(timeline.getFrameCount(), true);
			output.writeInt(bezierCount(timeline, 2), true);
			float[] frames = timeline.getFrames();
			int entries = timeline.getFrameEntries();
			for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
				int base = frame * entries;
				float mix = frames[base + 1];
				float softness = frames[base + 2];
				int flags = mix != 0f ? 1 : 0;
				if (mix != 0f && mix != 1f) flags |= 2;
				if (softness != 0f) flags |= 4;
				if (frames[base + 3] > 0f) flags |= 8;
				if (frames[base + 4] != 0f) flags |= 16;
				if (frames[base + 5] != 0f) flags |= 32;
				if (frame > 0) {
					int curveType = timeline.getCurveType(frame - 1);
					if (curveType == CurveTimeline.STEPPED) flags |= 64;
					else if (curveType >= CurveTimeline.BEZIER) flags |= 128;
				}
				output.writeByte(flags);
				output.writeFloat(frames[base]);
				if ((flags & 2) != 0) output.writeFloat(mix);
				if ((flags & 4) != 0) output.writeFloat(softness);
				if ((flags & 128) != 0) writeBezierControls(timeline, frame - 1, 2, false);
			}
		}
	}

	private void writeTransformTimelines(Array<Timeline> timelines) throws IOException {
		List<TransformConstraintTimeline> found = new ArrayList<>();
		for (Timeline timeline : timelines)
			if (timeline instanceof TransformConstraintTimeline transform) found.add(transform);
		output.writeInt(found.size(), true);
		for (TransformConstraintTimeline timeline : found) {
			output.writeInt(timeline.getConstraintIndex(), true);
			output.writeInt(timeline.getFrameCount(), true);
			output.writeInt(bezierCount(timeline, 6), true);
			writeFloatFrames(timeline, 6);
		}
	}

	private void writePathTimelines(Array<Timeline> timelines) throws IOException {
		LinkedHashMap<Integer, List<Timeline>> grouped = groupConstraintTimelines(timelines, TimelineKind.PATH);
		output.writeInt(grouped.size(), true);
		for (Map.Entry<Integer, List<Timeline>> group : grouped.entrySet()) {
			output.writeInt(group.getKey(), true);
			output.writeInt(group.getValue().size(), true);
			for (Timeline timeline : group.getValue()) {
				int type;
				int channels;
				if (timeline instanceof PathConstraintPositionTimeline) { type = 0; channels = 1; }
				else if (timeline instanceof PathConstraintSpacingTimeline) { type = 1; channels = 1; }
				else { type = 2; channels = 3; }
				CurveTimeline curve = (CurveTimeline)timeline;
				output.writeByte(type);
				output.writeInt(timeline.getFrameCount(), true);
				output.writeInt(bezierCount(curve, channels), true);
				writeFloatFrames(curve, channels);
			}
		}
	}

	private void writePhysicsTimelines(Array<Timeline> timelines) throws IOException {
		LinkedHashMap<Integer, List<Timeline>> grouped = groupConstraintTimelines(timelines, TimelineKind.PHYSICS);
		output.writeInt(grouped.size(), true);
		for (Map.Entry<Integer, List<Timeline>> group : grouped.entrySet()) {
			output.writeInt(group.getKey() + 1, true);
			output.writeInt(group.getValue().size(), true);
			for (Timeline timeline : group.getValue()) {
				output.writeByte(physicsTimelineType(timeline));
				output.writeInt(timeline.getFrameCount(), true);
				if (timeline instanceof PhysicsConstraintResetTimeline) {
					for (float time : timeline.getFrames()) output.writeFloat(time);
				} else {
					CurveTimeline curve = (CurveTimeline)timeline;
					output.writeInt(bezierCount(curve, 1), true);
					writeFloatFrames(curve, 1);
				}
			}
		}
	}

	private static int physicsTimelineType(Timeline timeline) {
		if (timeline instanceof PhysicsConstraintInertiaTimeline) return 0;
		if (timeline instanceof PhysicsConstraintStrengthTimeline) return 1;
		if (timeline instanceof PhysicsConstraintDampingTimeline) return 2;
		if (timeline instanceof PhysicsConstraintMassTimeline) return 4;
		if (timeline instanceof PhysicsConstraintWindTimeline) return 5;
		if (timeline instanceof PhysicsConstraintGravityTimeline) return 6;
		if (timeline instanceof PhysicsConstraintMixTimeline) return 7;
		if (timeline instanceof PhysicsConstraintResetTimeline) return 8;
		throw new IllegalArgumentException("Unsupported physics timeline: " + timeline.getClass().getName());
	}

	private void writeSliderTimelines(Array<Timeline> timelines) throws IOException {
		LinkedHashMap<Integer, List<Timeline>> grouped = groupConstraintTimelines(timelines, TimelineKind.SLIDER);
		output.writeInt(grouped.size(), true);
		for (Map.Entry<Integer, List<Timeline>> group : grouped.entrySet()) {
			output.writeInt(group.getKey(), true);
			output.writeInt(group.getValue().size(), true);
			for (Timeline timeline : group.getValue()) {
				output.writeByte(timeline instanceof SliderTimeline ? 0 : 1);
				output.writeInt(timeline.getFrameCount(), true);
				CurveTimeline curve = (CurveTimeline)timeline;
				output.writeInt(bezierCount(curve, 1), true);
				writeFloatFrames(curve, 1);
			}
		}
	}

	private LinkedHashMap<Integer, List<Timeline>> groupConstraintTimelines(Array<Timeline> timelines,
		TimelineKind kind) {
		LinkedHashMap<Integer, List<Timeline>> grouped = new LinkedHashMap<>();
		for (Timeline timeline : timelines) {
			boolean matches = switch (kind) {
			case PATH -> timeline instanceof PathConstraintPositionTimeline
				|| timeline instanceof PathConstraintSpacingTimeline || timeline instanceof PathConstraintMixTimeline;
			case PHYSICS -> isPhysicsTimeline(timeline);
			case SLIDER -> timeline instanceof SliderTimeline || timeline instanceof SliderMixTimeline;
			};
			if (matches) {
				int index = ((ConstraintTimeline)timeline).getConstraintIndex();
				grouped.computeIfAbsent(index, ignored -> new ArrayList<>()).add(timeline);
			}
		}
		return grouped;
	}

	private static boolean isPhysicsTimeline(Timeline timeline) {
		return timeline instanceof PhysicsConstraintInertiaTimeline
			|| timeline instanceof PhysicsConstraintStrengthTimeline
			|| timeline instanceof PhysicsConstraintDampingTimeline
			|| timeline instanceof PhysicsConstraintMassTimeline
			|| timeline instanceof PhysicsConstraintWindTimeline
			|| timeline instanceof PhysicsConstraintGravityTimeline
			|| timeline instanceof PhysicsConstraintMixTimeline
			|| timeline instanceof PhysicsConstraintResetTimeline;
	}

	private enum TimelineKind { PATH, PHYSICS, SLIDER }

	private void writeAttachmentTimelines(Array<Timeline> timelines) throws IOException {
		LinkedHashMap<Skin, LinkedHashMap<Integer, LinkedHashMap<String, List<Timeline>>>> grouped =
			new LinkedHashMap<>();
		for (Timeline timeline : timelines) {
			AttachmentLocation location;
			if (timeline instanceof DeformTimeline deform)
				location = findAttachment(deform.getAttachment(), deform.getSlotIndex());
			else if (timeline instanceof SequenceTimeline sequence)
				location = findAttachment(sequence.getAttachment(), sequence.getSlotIndex());
			else continue;
			if (location == null) throw new IllegalArgumentException("Animation attachment target is missing");
			grouped.computeIfAbsent(location.skin(), ignored -> new LinkedHashMap<>())
				.computeIfAbsent(location.slotIndex(), ignored -> new LinkedHashMap<>())
				.computeIfAbsent(location.placeholder(), ignored -> new ArrayList<>()).add(timeline);
		}
		output.writeInt(grouped.size(), true);
		for (Map.Entry<Skin, LinkedHashMap<Integer, LinkedHashMap<String, List<Timeline>>>> skin : grouped.entrySet()) {
			output.writeInt(skinIndices.get(skin.getKey()), true);
			output.writeInt(skin.getValue().size(), true);
			for (Map.Entry<Integer, LinkedHashMap<String, List<Timeline>>> slot : skin.getValue().entrySet()) {
				output.writeInt(slot.getKey(), true);
				int timelineCount = 0;
				for (List<Timeline> values : slot.getValue().values()) timelineCount += values.size();
				output.writeInt(timelineCount, true);
				for (Map.Entry<String, List<Timeline>> attachment : slot.getValue().entrySet()) {
					for (Timeline timeline : attachment.getValue()) {
						strings.writeRef(output, attachment.getKey());
						writeAttachmentTimeline(timeline);
					}
				}
			}
		}
	}

	private void writeAttachmentTimeline(Timeline timeline) throws IOException {
		output.writeByte(timeline instanceof DeformTimeline ? 0 : 1);
		output.writeInt(timeline.getFrameCount(), true);
		if (timeline instanceof DeformTimeline deform) writeDeformTimeline(deform);
		else writeSequenceTimeline((SequenceTimeline)timeline);
	}

	private void writeDeformTimeline(DeformTimeline timeline) throws IOException {
		output.writeInt(bezierCount(timeline, 1), true);
		float[] times = timeline.getFrames();
		output.writeFloat(times[0]);
		VertexAttachment attachment = timeline.getAttachment();
		boolean weighted = attachment.getBones() != null;
		float[] setupVertices = attachment.getVertices();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			float[] vertices = timeline.getVertices()[frame];
			int first = 0;
			int last = vertices.length - 1;
			while (first <= last && deformValue(vertices, setupVertices, weighted, first) == 0f) first++;
			while (last >= first && deformValue(vertices, setupVertices, weighted, last) == 0f) last--;
			if (first > last) output.writeInt(0, true);
			else {
				output.writeInt(last - first + 1, true);
				output.writeInt(first, true);
				for (int vertex = first; vertex <= last; vertex++)
					output.writeFloat(deformValue(vertices, setupVertices, weighted, vertex));
			}
			if (frame < timeline.getFrameCount() - 1) {
				output.writeFloat(times[frame + 1]);
				writeCurve(timeline, frame, 1, true);
			}
		}
	}

	private static float deformValue(float[] vertices, float[] setup, boolean weighted, int index) {
		return weighted ? vertices[index] : vertices[index] - setup[index];
	}

	private void writeSequenceTimeline(SequenceTimeline timeline) throws IOException {
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			output.writeFloat(frames[base]);
			output.writeInt((int)frames[base + 1]);
			output.writeFloat(frames[base + 2]);
		}
	}

	private void writeDrawOrderTimelines(Array<Timeline> timelines) throws IOException {
		DrawOrderTimeline drawOrder = null;
		List<DrawOrderFolderTimeline> folders = new ArrayList<>();
		for (Timeline timeline : timelines) {
			if (timeline instanceof DrawOrderTimeline found) drawOrder = found;
			else if (timeline instanceof DrawOrderFolderTimeline folder) folders.add(folder);
		}
		if (drawOrder == null) output.writeInt(0, true);
		else {
			output.writeInt(drawOrder.getFrameCount(), true);
			for (int frame = 0; frame < drawOrder.getFrameCount(); frame++) {
				output.writeFloat(drawOrder.getFrames()[frame]);
				writeDrawOrder(drawOrder.getDrawOrders()[frame], data.getSlots().size);
			}
		}
		output.writeInt(folders.size(), true);
		for (DrawOrderFolderTimeline folder : folders) {
			output.writeInt(folder.getSlots().length, true);
			for (int slot : folder.getSlots()) output.writeInt(slot, true);
			output.writeInt(folder.getFrameCount(), true);
			for (int frame = 0; frame < folder.getFrameCount(); frame++) {
				output.writeFloat(folder.getFrames()[frame]);
				writeDrawOrder(folder.getDrawOrders()[frame], folder.getSlots().length);
			}
		}
	}

	private void writeDrawOrder(int[] drawOrder, int count) throws IOException {
		if (drawOrder == null) {
			output.writeInt(0, true);
			return;
		}
		int[] positions = new int[count];
		for (int position = 0; position < count; position++) positions[drawOrder[position]] = position;
		int changed = 0;
		for (int original = 0; original < count; original++) if (positions[original] != original) changed++;
		output.writeInt(changed, true);
		for (int original = 0; original < count; original++) {
			if (positions[original] == original) continue;
			output.writeInt(original, true);
			output.writeInt(positions[original] - original, true);
		}
	}

	private void writeEventTimeline(Array<Timeline> timelines) throws IOException {
		EventTimeline events = null;
		for (Timeline timeline : timelines) if (timeline instanceof EventTimeline found) events = found;
		if (events == null) {
			output.writeInt(0, true);
			return;
		}
		output.writeInt(events.getFrameCount(), true);
		for (Event event : events.getEvents()) {
			output.writeFloat(event.getTime());
			int index = data.getEvents().indexOf(event.getData(), true);
			if (index < 0) throw new IllegalArgumentException("Animation event definition is missing: " + event.getData());
			output.writeInt(index, true);
			output.writeInt(event.getInt(), false);
			output.writeFloat(event.getFloat());
			writeString(output, event.getString());
			if (event.getData().getAudioPath() != null) {
				output.writeFloat(event.getVolume());
				output.writeFloat(event.getBalance());
			}
		}
	}

	private void writeFloatFrames(CurveTimeline timeline, int channels) throws IOException {
		float[] frames = timeline.getFrames();
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
			int base = frame * entries;
			output.writeFloat(frames[base]);
			for (int channel = 0; channel < channels; channel++) output.writeFloat(frames[base + 1 + channel]);
			if (frame > 0) writeCurve(timeline, frame - 1, channels, false);
		}
	}

	private void writeCurve(CurveTimeline timeline, int frame, int channels, boolean deform) throws IOException {
		int type = timeline.getCurveType(frame);
		if (type == CurveTimeline.STEPPED) output.writeByte(1);
		else if (type >= CurveTimeline.BEZIER) {
			output.writeByte(2);
			writeBezierControls(timeline, frame, channels, deform);
		} else output.writeByte(0);
	}

	private void writeBezierControls(CurveTimeline timeline, int frame, int channels, boolean deform) throws IOException {
		float[] controls = deform
			? SkeletonExporter.deformBezierControls((DeformTimeline)timeline, frame)
			: SkeletonExporter.bezierControls(timeline, frame, channels);
		for (float control : controls) output.writeFloat(control);
	}

	private static int bezierCount(CurveTimeline timeline, int channels) {
		int count = 0;
		for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++)
			if (timeline.getCurveType(frame) >= CurveTimeline.BEZIER) count += channels;
		return count;
	}

	private static int colorByte(float value) {
		return Math.round(Math.max(0f, Math.min(1f, value)) * 255f);
	}

	private void writeSliderAnimationIndices() throws IOException {
		for (ConstraintData<?, ?> constraint : data.getConstraints()) {
			if (!(constraint instanceof SliderData slider)) continue;
			int index = data.getAnimations().indexOf(slider.getAnimation(), true);
			if (index < 0) throw new IllegalArgumentException("Slider animation is not in skeleton data: " + slider.getName());
			output.writeInt(index, true);
		}
	}

	private int indexOfConstraint(ConstraintData<?, ?> constraint) {
		int index = data.getConstraints().indexOf(constraint, true);
		if (index < 0) throw new IllegalArgumentException("Skin constraint is not in skeleton data: " + constraint.getName());
		return index;
	}

	private AttachmentLocation findAttachment(Attachment attachment, int requiredSlot) {
		for (Skin skin : skins) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				if ((requiredSlot < 0 || entry.getSlotIndex() == requiredSlot) && entry.getAttachment() == attachment)
					return new AttachmentLocation(skin, entry.getSlotIndex(), entry.getPlaceholder());
			}
		}
		return null;
	}

	private static boolean same(String first, String second) {
		return first == null ? second == null : first.equals(second);
	}

	private static boolean isWhite(Color color) {
		return Color.rgba8888(color) == 0xffffffff;
	}

	/** Spine 4.3 prefixes strings with encoded byte count, unlike libGDX DataOutput's character count. */
	private static void writeString(DataOutput output, String value) throws IOException {
		if (value == null) {
			output.writeInt(0, true);
			return;
		}
		if (value.isEmpty()) {
			output.writeInt(1, true);
			return;
		}
		int byteCount = 0;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			byteCount += character <= 0x7f ? 1 : character <= 0x7ff ? 2 : 3;
		}
		output.writeInt(byteCount + 1, true);
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character <= 0x7f) output.writeByte(character);
			else if (character <= 0x7ff) {
				output.writeByte(0xc0 | character >> 6);
				output.writeByte(0x80 | character & 0x3f);
			} else {
				output.writeByte(0xe0 | character >> 12);
				output.writeByte(0x80 | character >> 6 & 0x3f);
				output.writeByte(0x80 | character & 0x3f);
			}
		}
	}

	private record AttachmentLocation(Skin skin, int slotIndex, String placeholder) {}

	private static final class StringTable {
		private final LinkedHashMap<String, Integer> values = new LinkedHashMap<>();

		void add(String value) {
			if (value != null) values.computeIfAbsent(value, ignored -> values.size());
		}

		void write(DataOutput output) throws IOException {
			output.writeInt(values.size(), true);
			for (String value : values.keySet()) SkeletonBinaryExporter.writeString(output, value);
		}

		void writeRef(DataOutput output, String value) throws IOException {
			if (value == null) {
				output.writeInt(0, true);
				return;
			}
			Integer index = values.get(value);
			if (index == null) throw new IllegalArgumentException("String was not collected before export: " + value);
			output.writeInt(index + 1, true);
		}
	}

	private SkeletonBinaryExporter() {
		throw new AssertionError();
	}
}
