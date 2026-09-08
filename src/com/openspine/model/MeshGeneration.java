package com.openspine.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.math.ConvexHull;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BonePose;
import com.esotericsoftware.spine.Skeleton;
import com.openspine.model.SkeletonEditor.MeshGeometryState;

/** Conservative mesh generation and setup-space automatic binding. Callers commit through SkeletonEditor validation. */
public final class MeshGeneration {
	private static final int MAX_GENERATED_VERTICES = 4096;
	private record Influence(int bone, float x, float y, float weight) {}
	private record Candidate(int bone, double distance) {}

	public static MeshGeometryState subdivide(MeshGeometryState source, int passes) {
		if (source == null || source.triangles() == null || source.triangles().length == 0 || passes < 1 || passes > 4)
			throw new IllegalArgumentException("细分需要有效网格，级别为 1 到 4");
		long count = source.vertexCount(), triangles = source.triangles().length / 3;
		for (int pass = 0; pass < passes; pass++) {
			count += triangles;
			triangles *= 3;
			if (count > MAX_GENERATED_VERTICES) throw new IllegalArgumentException("生成结果超过 4096 顶点，请降低细分级别");
		}
		MeshGeometryState result = source;
		for (int pass = 0; pass < passes; pass++) {
			int length = result.triangles().length;
			// Insertion expands the face in place; visit backwards so remaining original offsets stay valid.
			for (int offset = length - 3; offset >= 0; offset -= 3)
				result = SkeletonEditor.insertMeshVertex(result, offset, 1f / 3f, 1f / 3f, 1f / 3f);
		}
		return result;
	}

	/** Traces a conservative convex outline around all pixels above threshold; transparent holes stay inside the hull. */
	public static MeshGeometryState traceConvex(MeshGeometryState source, int width, int height,
		int[] alpha, int threshold) {
		validateTraceInput(source, width, height, alpha, threshold);
		float[] transform = affineTransform(source.regionUVs(), source.vertices());
		FloatArray points = new FloatArray();
		for (int y = 0; y < height; y++) {
			int left = width, right = -1;
			for (int x = 0; x < width; x++) if (alpha[y * width + x] >= threshold) {
				left = Math.min(left, x);
				right = x;
			}
			if (right < 0) continue;
			float u0 = left / (float)width, u1 = (right + 1) / (float)width;
			float v0 = y / (float)height, v1 = (y + 1) / (float)height;
			points.addAll(u0, v0, u1, v0);
			points.addAll(u0, v1, u1, v1);
		}
		if (points.isEmpty()) throw new IllegalArgumentException("该阈值下没有可见像素");
		FloatArray hull = new ConvexHull().computePolygon(points, false);
		int size = hull.size;
		if (size >= 4 && hull.get(0) == hull.get(size - 2) && hull.get(1) == hull.get(size - 1)) size -= 2;
		FloatArray corners = new FloatArray();
		for (int index = 0; index < size; index += 2) {
			int before = (index + size - 2) % size, after = (index + 2) % size;
			double cross = (hull.get(index) - hull.get(before)) * (double)(hull.get(after + 1) - hull.get(index + 1))
				- (hull.get(index + 1) - hull.get(before + 1)) * (double)(hull.get(after) - hull.get(index));
			if (Math.abs(cross) > 1e-10) corners.addAll(hull.get(index), hull.get(index + 1));
		}
		size = corners.size;
		if (size < 6 || size / 2 > MAX_GENERATED_VERTICES) throw new IllegalArgumentException("生成的轮廓顶点数量无效");
		float[] uvs = corners.toArray();
		return outlineGeometry(source, uvs, transform, new EarClippingTriangulator().computeTriangles(uvs).toArray());
	}

	/** Traces a single non-self-touching alpha outline without filling its concave regions. Tolerance is in source pixels. */
	public static MeshGeometryState traceConcave(MeshGeometryState source, int width, int height,
		int[] alpha, int threshold, float tolerance, int maxVertices) {
		validateTraceInput(source, width, height, alpha, threshold);
		if (!Float.isFinite(tolerance) || tolerance < 0f || tolerance > 64f
			|| maxVertices < 3 || maxVertices > MAX_GENERATED_VERTICES)
			throw new IllegalArgumentException("轮廓简化容差必须为 0 到 64 像素，顶点上限为 3 到 4096");
		float[] transform = affineTransform(source.regionUVs(), source.vertices());
		float[] uvs = ImageOutline.trace(width, height, alpha, threshold, tolerance, maxVertices);
		return outlineGeometry(source, uvs, transform, ImageOutline.triangulate(uvs));
	}

	private static void validateTraceInput(MeshGeometryState source, int width, int height, int[] alpha, int threshold) {
		if (source == null || source.weighted()) throw new IllegalArgumentException("轮廓生成需要未加权 Mesh");
		if (width < 1 || height < 1 || (long)width * height > 16L * 1024 * 1024
			|| alpha == null || alpha.length != width * height || threshold < 1 || threshold > 255)
			throw new IllegalArgumentException("图片尺寸或 Alpha 阈值无效");
	}

	private static MeshGeometryState outlineGeometry(MeshGeometryState source, float[] uvs, float[] transform, short[] triangles) {
		int size = uvs.length;
		float[] vertices = new float[size];
		for (int vertex = 0; vertex < size; vertex += 2) {
			vertices[vertex] = transform[0] * uvs[vertex] + transform[1] * uvs[vertex + 1] + transform[2];
			vertices[vertex + 1] = transform[3] * uvs[vertex] + transform[4] * uvs[vertex + 1] + transform[5];
			if (!Float.isFinite(vertices[vertex]) || !Float.isFinite(vertices[vertex + 1]))
				throw new IllegalArgumentException("生成的轮廓坐标不是有限数值");
		}
		if (triangles.length != (size / 2 - 2) * 3) throw new IllegalArgumentException("轮廓无法完整三角化");
		double triangleArea = 0d, outlineArea = 0d;
		for (int i = 0; i < size; i += 2) {
			int next = (i + 2) % size;
			outlineArea += (double)uvs[i] * uvs[next + 1] - (double)uvs[next] * uvs[i + 1];
		}
		for (int i = 0; i < triangles.length; i += 3) {
			int a = Short.toUnsignedInt(triangles[i]) * 2;
			int b = Short.toUnsignedInt(triangles[i + 1]) * 2;
			int c = Short.toUnsignedInt(triangles[i + 2]) * 2;
			double cross = triangleCross(uvs, a, b, c);
			if (cross == 0d || triangleCross(vertices, a, b, c) == 0d)
				throw new IllegalArgumentException("轮廓生成了退化三角形，请调整简化容差或 Mesh 缩放");
			triangleArea += Math.abs(cross);
		}
		if (Math.abs(triangleArea - Math.abs(outlineArea)) > Math.max(1e-10, Math.abs(outlineArea) * 1e-5))
			throw new IllegalArgumentException("轮廓三角化面积不一致，请调整简化容差");
		short[] edges = new short[size];
		for (int vertex = 0; vertex < size / 2; vertex++) {
			edges[vertex * 2] = (short)vertex;
			edges[vertex * 2 + 1] = (short)((vertex + 1) % (size / 2));
		}
		return new MeshGeometryState(source.color(), source.path(), null, vertices, uvs, triangles,
			size, edges, source.width(), source.height());
	}

	private static double triangleCross(float[] vertices, int a, int b, int c) {
		return ((double)vertices[b] - vertices[a]) * ((double)vertices[c + 1] - vertices[a + 1])
			- ((double)vertices[c] - vertices[a]) * ((double)vertices[b + 1] - vertices[a + 1]);
	}

	/** Returns an affine mapping between UV pairs and XY pairs, rejecting non-affine geometry. */
	public static float[] affineTransform(float[] uvs, float[] positions) {
		if (uvs == null || positions == null || uvs.length != positions.length || uvs.length < 6 || (uvs.length & 1) != 0)
			throw new IllegalArgumentException("Mesh UV 与顶点数据无效");
		double[] extent = new double[2], rounding = new double[2];
		for (int index = 0; index < uvs.length; index++) {
			if (!Float.isFinite(uvs[index]) || !Float.isFinite(positions[index]))
				throw new IllegalArgumentException("Mesh UV 与顶点必须是有限数值");
			int axis = index & 1;
			extent[axis] = Math.max(extent[axis], Math.abs((double)positions[index] - positions[axis]));
			rounding[axis] = Math.max(rounding[axis], Math.ulp(positions[index]) * 2d);
		}
		int b = -1, c = -1;
		double determinant = 0d, distance = 0d;
		// A wide UV basis avoids amplifying rounded coordinates at adjacent outline vertices.
		for (int second = 2; second < uvs.length; second += 2) {
			double du = (double)uvs[second] - uvs[0], dv = (double)uvs[second + 1] - uvs[1];
			double squared = du * du + dv * dv;
			if (squared > distance) { b = second; distance = squared; }
		}
		if (b >= 0) for (int third = 2; third < uvs.length; third += 2) {
			double det = ((double)uvs[b] - uvs[0]) * ((double)uvs[third + 1] - uvs[1])
				- ((double)uvs[third] - uvs[0]) * ((double)uvs[b + 1] - uvs[1]);
			if (Math.abs(det) > Math.max(1e-8, Math.abs(determinant))) { c = third; determinant = det; }
		}
		if (c < 0) throw new IllegalArgumentException("Mesh UV 退化，无法生成轮廓");
		float[] matrix = new float[6];
		for (int axis = 0; axis < 2; axis++) {
			double deltaB = (double)positions[b + axis] - positions[axis], deltaC = (double)positions[c + axis] - positions[axis];
			double x = (deltaB * ((double)uvs[c + 1] - uvs[1]) - deltaC * ((double)uvs[b + 1] - uvs[1])) / determinant;
			double y = (((double)uvs[b] - uvs[0]) * deltaC - ((double)uvs[c] - uvs[0]) * deltaB) / determinant;
			matrix[axis * 3] = (float)x;
			matrix[axis * 3 + 1] = (float)y;
			matrix[axis * 3 + 2] = (float)(positions[axis] - x * uvs[0] - y * uvs[1]);
			// Compare local deltas: a large setup translation must not hide a meaningful shape edit.
			// Permit only the actual float quantization at that position in addition to mesh-scale error.
			double tolerance = Math.max(Math.max(0.001d, extent[axis] * 0.0001d), rounding[axis]);
			for (int index = 0; index < uvs.length; index += 2) {
				double expectedDelta = x * ((double)uvs[index] - uvs[0]) + y * ((double)uvs[index + 1] - uvs[1]);
				double actualDelta = (double)positions[index + axis] - positions[axis];
				float generated = matrix[axis * 3] * uvs[index] + matrix[axis * 3 + 1] * uvs[index + 1] + matrix[axis * 3 + 2];
				if (!Float.isFinite(generated) || Math.abs(expectedDelta - actualDelta) > tolerance
					|| Math.abs((double)generated - positions[index + axis]) > tolerance)
					throw new IllegalArgumentException("已调整形状的 Mesh 无法无损重建图片轮廓，请对 Region 转换的 Mesh 执行轮廓生成");
			}
		}
		return matrix;
	}

	public static MeshGeometryState autoWeight(MeshGeometryState source, Skeleton setup, int slotIndex,
		int[] selectedBones, int maxInfluences, float falloff, Set<SkeletonDocument.MeshWeightLock> locks) {
		if (source == null || setup == null || slotIndex < 0 || slotIndex >= setup.getSlots().size
			|| selectedBones == null || selectedBones.length == 0 || maxInfluences < 1 || maxInfluences > 8
			|| !Float.isFinite(falloff) || falloff < 0.5f || falloff > 8f)
			throw new IllegalArgumentException("自动权重需要选中骨骼，影响数为 1 到 8，衰减为 0.5 到 8");
		Set<Integer> selected = new java.util.TreeSet<>();
		for (int bone : selectedBones) {
			if (bone < 0 || bone >= setup.getBones().size) throw new IllegalArgumentException("自动权重骨骼索引无效");
			selected.add(bone);
		}
		List<List<Influence>> existing = decode(source, setup.getBones().size);
		IntArray encodedBones = new IntArray();
		FloatArray encodedVertices = new FloatArray();
		BonePose slot = setup.getSlots().get(slotIndex).getBone().getAppliedPose();
		float[] plain = source.vertices();
		for (int vertex = 0; vertex < source.vertexCount(); vertex++) {
			Vector2 world = new Vector2();
			if (source.weighted()) {
				for (Influence influence : existing.get(vertex)) {
					Vector2 point = setup.getBones().get(influence.bone()).getAppliedPose().localToWorld(new Vector2(influence.x(), influence.y()));
					world.mulAdd(point, influence.weight());
				}
			} else slot.localToWorld(world.set(plain[vertex * 2], plain[vertex * 2 + 1]));
			List<Influence> result = new ArrayList<>();
			Set<Integer> lockedBones = new HashSet<>();
			float lockedSum = 0f;
			Vector2 lockedWorld = new Vector2();
			for (Influence influence : existing.get(vertex)) {
				if (locks == null || !locks.contains(new SkeletonDocument.MeshWeightLock(vertex, influence.bone()))) continue;
				result.add(influence);
				lockedBones.add(influence.bone());
				lockedSum += influence.weight();
				Vector2 point = setup.getBones().get(influence.bone()).getAppliedPose().localToWorld(new Vector2(influence.x(), influence.y()));
				lockedWorld.mulAdd(point, influence.weight());
			}
			if (lockedSum > 1.000001f || result.size() > maxInfluences)
				throw new IllegalArgumentException("锁定权重或锁定影响数超过自动权重参数");
			float remaining = Math.max(0f, 1f - lockedSum);
			if (remaining > 0.000001f) {
				List<Candidate> candidates = new ArrayList<>();
				for (int index : selected) if (!lockedBones.contains(index)) {
					Bone bone = setup.getBones().get(index);
					BonePose pose = bone.getAppliedPose();
					Vector2 tip = pose.localToWorld(new Vector2(bone.getData().getLength(), 0f));
					candidates.add(new Candidate(index, segmentDistance(world.x, world.y,
						pose.getWorldX(), pose.getWorldY(), tip.x, tip.y)));
				}
				candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparingInt(Candidate::bone));
				int count = Math.min(maxInfluences - result.size(), candidates.size());
				if (count == 0) throw new IllegalArgumentException("没有可分配剩余权重的骨骼，请增加最大影响数或选择未锁定骨骼");
				double[] scores = new double[count];
				double sum = 0d;
				for (int index = 0; index < count; index++) {
					scores[index] = Math.pow((candidates.get(0).distance() + 0.01) / (candidates.get(index).distance() + 0.01), falloff);
					sum += scores[index];
				}
				// Locked influences retain their original coordinates; the remaining contribution preserves the full setup point.
				Vector2 residual = new Vector2(world).sub(lockedWorld).scl(1f / remaining);
				for (int index = 0; index < count; index++) {
					int bone = candidates.get(index).bone();
					Vector2 local = setup.getBones().get(bone).getAppliedPose().worldToLocal(new Vector2(residual));
					if (!Float.isFinite(local.x) || !Float.isFinite(local.y)) throw new IllegalArgumentException("自动权重目标骨骼变换不可逆");
					result.add(new Influence(bone, local.x, local.y, (float)(remaining * scores[index] / sum)));
				}
			}
			encodedBones.add(result.size());
			for (Influence influence : result) {
				encodedBones.add(influence.bone());
				encodedVertices.addAll(influence.x(), influence.y(), influence.weight());
			}
		}
		return new MeshGeometryState(source.color(), source.path(), encodedBones.toArray(), encodedVertices.toArray(),
			source.regionUVs(), source.triangles(), source.hullLength(), source.edges(), source.width(), source.height());
	}

	private static double segmentDistance(double x, double y, double ax, double ay, double bx, double by) {
		double dx = bx - ax, dy = by - ay, length = dx * dx + dy * dy;
		double t = length == 0d ? 0d : Math.max(0d, Math.min(1d, ((x - ax) * dx + (y - ay) * dy) / length));
		return Math.hypot(x - ax - t * dx, y - ay - t * dy);
	}

	private static List<List<Influence>> decode(MeshGeometryState source, int boneCount) {
		List<List<Influence>> result = new ArrayList<>();
		int[] bones = source.bones();
		float[] values = source.vertices();
		int boneCursor = 0, valueCursor = 0;
		for (int vertex = 0; vertex < source.vertexCount(); vertex++) {
			List<Influence> influences = new ArrayList<>();
			if (bones != null) {
				if (boneCursor >= bones.length) throw new IllegalArgumentException("Mesh 权重布局无效");
				int count = bones[boneCursor++];
				if (count < 1 || count > boneCount || boneCursor + count > bones.length || valueCursor + count * 3 > values.length)
					throw new IllegalArgumentException("Mesh 权重布局无效");
				Set<Integer> seen = new HashSet<>();
				for (int index = 0; index < count; index++) {
					int bone = bones[boneCursor++];
					float x = values[valueCursor++], y = values[valueCursor++], weight = values[valueCursor++];
					if (bone < 0 || bone >= boneCount || !seen.add(bone) || !Float.isFinite(x) || !Float.isFinite(y)
						|| !Float.isFinite(weight) || weight < 0f) throw new IllegalArgumentException("Mesh 权重数据无效");
					influences.add(new Influence(bone, x, y, weight));
				}
			}
			result.add(influences);
		}
		if (bones != null && (boneCursor != bones.length || valueCursor != values.length)
			|| bones == null && values.length != source.vertexCount() * 2) throw new IllegalArgumentException("Mesh 顶点数据长度无效");
		return result;
	}

	private MeshGeneration() {}
}
