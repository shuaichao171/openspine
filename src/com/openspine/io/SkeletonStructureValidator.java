package com.openspine.io;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.utils.JsonValue;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.VertexAttachment;

/** Checks render-facing geometry and animation input without touching textures, GL, or project data. */
public final class SkeletonStructureValidator {
	private static final double WEIGHT_TOLERANCE = 0.001;
	private static final int MAX_WARNINGS = 64;

	/** Must run before SkeletonJson narrows triangle indices to shorts and bone indices to ints. */
	public static void validateJson(JsonValue root) {
		validateAnimationTimelines(root);
		JsonValue skins = root.get("skins"), bones = root.get("bones");
		if (skins == null) return;
		int boneCount = bones == null ? 0 : bones.size;
		for (JsonValue skin = skins.child; skin != null; skin = skin.next) {
			JsonValue attachments = skin.get("attachments");
			if (attachments == null) continue;
			for (JsonValue slot = attachments.child; slot != null; slot = slot.next) {
				for (JsonValue attachment = slot.child; attachment != null; attachment = attachment.next) {
					cancelled();
					String label = "Attachment " + skin.name + "/" + slot.name + "/" + attachment.name;
					String type = attachment.getString("type", "region");
					if ("mesh".equals(type)) {
						JsonValue uvs = array(attachment.get("uvs"), label + " uvs");
						if (uvs.size < 6 || (uvs.size & 1) != 0 || uvs.size > 131072)
							fail(label, "uvs must contain 3 to 65536 coordinate pairs");
						for (JsonValue uv = uvs.child; uv != null; uv = uv.next) number(uv, label + " uvs");
						int count = uvs.size / 2;
						indices(attachment.get("triangles"), count, 3, label + " triangles", true);
						if (attachment.has("edges")) indices(attachment.get("edges"), count, 2, label + " edges", false);
						if (attachment.has("hull")) {
							int hull = integer(attachment.get("hull"), 0, count, label + " hull");
							if (hull == 1 || hull == 2) fail(label, "hull must be zero or contain at least 3 vertices");
						}
						jsonVertices(attachment.get("vertices"), count, boneCount, label);
					} else if ("boundingbox".equals(type) || "clipping".equals(type) || "path".equals(type)) {
						int count = integer(attachment.get("vertexCount"), 0, 65536, label + " vertexCount");
						jsonVertices(attachment.get("vertices"), count, boneCount, label);
					}
				}
			}
		}
	}

	private static void validateAnimationTimelines(JsonValue root) {
		JsonValue animations = root.get("animations");
		if (animations == null) return;
		if (!animations.isObject()) fail(jsonPath(animations), "expected an animation object");
		for (JsonValue animation = animations.child; animation != null; animation = animation.next) {
			cancelled();
			if (!animation.isObject()) fail(jsonPath(animation), "expected an animation object");
			for (String type : List.of("events", "drawOrder")) timelines(animation.get(type), 0);
			for (String type : List.of("ik", "transform")) timelines(animation.get(type), 1);
			for (String type : List.of("bones", "slots", "path", "physics", "slider")) timelines(animation.get(type), 2);
			timelines(animation.get("attachments"), 4);
			JsonValue folders = animation.get("drawOrderFolder");
			if (folders == null) continue;
			if (!folders.isObject()) fail(jsonPath(folders), "expected a timeline object");
			for (JsonValue folder = folders.child; folder != null; folder = folder.next) {
				cancelled();
				if (!folder.isObject()) fail(jsonPath(folder), "expected a timeline object");
				timelines(folder.get("keys"), 0);
			}
		}
	}

	/** Stop at the keyframe array: vertices, offsets and other frame values may legitimately be empty. */
	private static void timelines(JsonValue value, int nesting) {
		if (value == null) return;
		cancelled();
		if (nesting == 0) {
			if (!value.isArray()) fail(jsonPath(value), "expected a keyframe array");
			if (value.size == 0) fail(jsonPath(value), "must contain at least one keyframe; omit the timeline when unused");
			for (JsonValue frame = value.child; frame != null; frame = frame.next) {
				cancelled();
				if (!frame.isObject()) fail(jsonPath(frame), "expected a keyframe object");
			}
			return;
		}
		if (!value.isObject()) fail(jsonPath(value), "expected a timeline object");
		for (JsonValue child = value.child; child != null; child = child.next) timelines(child, nesting - 1);
	}

	private static String jsonPath(JsonValue value) {
		String path = value.trace();
		return path.startsWith("{}.") ? path.substring(3) : path;
	}

	public static List<String> validate(SkeletonData data) {
		if (data == null || data.getBones().isEmpty()) throw new IllegalArgumentException("Spine projects must contain at least one bone.");
		List<String> warnings = new ArrayList<>();
		for (var skin : data.getSkins()) for (var entry : skin.getAttachments()) {
			cancelled();
			if (!(entry.getAttachment() instanceof VertexAttachment attachment)) continue;
			String label = "Attachment " + skin.getName() + "/" + entry.getPlaceholder();
			int length = attachment.getWorldVerticesLength();
			if (length < 0 || (length & 1) != 0 || length > 131072) fail(label, "invalid world vertex coordinate count");
			int count = length / 2;
			vertices(attachment, count, data.getBones().size, label, warnings);
			if (attachment instanceof MeshAttachment mesh) {
				float[] uvs = mesh.getRegionUVs();
				if (count < 3 || uvs == null || uvs.length != length) fail(label, "UV and vertex counts do not match");
				finite(uvs, label + " uvs");
				short[] triangles = mesh.getTriangles();
				if (triangles == null || triangles.length == 0 || triangles.length % 3 != 0) fail(label, "triangles must contain complete triples");
				indices(triangles, count, label + " triangles");
				int hull = mesh.getHullLength();
				if (hull < 0 || (hull & 1) != 0 || hull > length || hull > 0 && hull < 6) fail(label, "hull is outside the vertex range");
				short[] edges = mesh.getEdges();
				if (edges != null) {
					if (edges.length % 2 != 0) fail(label, "edges must contain complete pairs");
					indices(edges, count, label + " edges");
				}
				if (!Float.isFinite(mesh.getWidth()) || !Float.isFinite(mesh.getHeight()) || mesh.getWidth() < 0 || mesh.getHeight() < 0)
					fail(label, "mesh dimensions must be finite and nonnegative");
			}
		}
		return List.copyOf(warnings);
	}

	private static void jsonVertices(JsonValue raw, int count, int boneCount, String label) {
		JsonValue values = array(raw, label + " vertices");
		for (JsonValue value = values.child; value != null; value = value.next) number(value, label + " vertices");
		if (values.size == count * 2) return;
		JsonValue cursor = values.child;
		for (int vertex = 0; vertex < count; vertex++) {
			cancelled();
			int influences = integer(cursor, 1, boneCount, label + " vertex " + vertex + " influence count");
			cursor = cursor.next;
			for (int influence = 0; influence < influences; influence++) {
				integer(cursor, 0, boneCount - 1, label + " vertex " + vertex + " bone index");
				cursor = cursor.next;
				for (int component = 0; component < 3; component++) {
					if (cursor == null) fail(label, "weighted vertex data is incomplete");
					cursor = cursor.next;
				}
			}
		}
		if (cursor != null) fail(label, "weighted vertices contain extra data");
	}

	private static void vertices(VertexAttachment attachment, int count, int boneCount, String label, List<String> warnings) {
		float[] values = attachment.getVertices();
		if (values == null) fail(label, "vertices are missing");
		finite(values, label + " vertices");
		int[] bones = attachment.getBones();
		if (bones == null) {
			if (values.length != count * 2) fail(label, "unweighted vertex and UV counts do not match");
			return;
		}
		int b = 0, v = 0, unnormalized = 0;
		for (int vertex = 0; vertex < count; vertex++) {
			cancelled();
			if (b >= bones.length) fail(label, "weighted bone data is incomplete");
			int influences = bones[b++];
			if (influences < 1 || influences > boneCount || influences > bones.length - b
				|| influences > (values.length - v) / 3) fail(label, "invalid weighted influence count");
			Set<Integer> seen = new HashSet<>();
			double sum = 0;
			for (int influence = 0; influence < influences; influence++) {
				int bone = bones[b++];
				if (bone < 0 || bone >= boneCount) fail(label, "bone index " + bone + " is outside [0, " + (boneCount - 1) + "]");
				if (!seen.add(bone)) fail(label, "a weighted vertex references the same bone twice");
				float weight = values[v + 2];
				if (weight < 0) fail(label, "vertex weights must not be negative");
				sum += weight;
				v += 3;
			}
			if (!(sum > 0) || sum > Float.MAX_VALUE) fail(label, "weight sum must be finite and positive");
			if (Math.abs(sum - 1) > WEIGHT_TOLERANCE) unnormalized++;
		}
		if (b != bones.length || v != values.length) fail(label, "weighted vertices contain extra bone or value data");
		if (unnormalized > 0 && warnings.size() < MAX_WARNINGS)
			warnings.add(label + ": " + unnormalized + " vertices have non-normalized weights (tolerance 0.001). Weights were not modified.");
	}

	private static void indices(JsonValue raw, int count, int group, String label, boolean nonempty) {
		JsonValue values = array(raw, label);
		if (values.size % group != 0 || nonempty && values.size == 0) fail(label, "indices must contain complete groups of " + group);
		int index = 0;
		for (JsonValue value = values.child; value != null; value = value.next)
			integer(value, 0, count - 1, label + "[" + index++ + "]");
	}

	private static void indices(short[] values, int count, String label) {
		for (int index = 0; index < values.length; index++) {
			int value = Short.toUnsignedInt(values[index]);
			if (value >= count) fail(label + "[" + index + "]", "index " + value + " is outside [0, " + (count - 1) + "]");
		}
	}

	private static JsonValue array(JsonValue value, String label) {
		if (value == null || !value.isArray()) fail(label, "expected an array");
		return value;
	}

	private static int integer(JsonValue value, int min, int max, String label) {
		double number = number(value, label);
		if (number != Math.rint(number) || number < min || number > max)
			fail(label, "index/count " + number + " must be an integer in [" + min + ", " + max + "]");
		return (int)number;
	}

	private static double number(JsonValue value, String label) {
		if (value == null || !value.isNumber()) fail(label, "expected a numeric value");
		double number = value.asDouble();
		if (!Double.isFinite(number) || !Float.isFinite((float)number)) fail(label, "value must be finite at runtime float precision");
		return number;
	}

	private static void finite(float[] values, String label) {
		for (float value : values) if (!Float.isFinite(value)) fail(label, "values must be finite");
	}

	private static void cancelled() {
		if (Thread.currentThread().isInterrupted()) throw new IllegalArgumentException("Structure validation cancelled.");
	}
	private static void fail(String label, String detail) { throw new IllegalArgumentException(label + ": " + detail + "."); }
	private SkeletonStructureValidator() {}
}
