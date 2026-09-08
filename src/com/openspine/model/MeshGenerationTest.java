package com.openspine.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.io.SkeletonBinaryExporter;
import com.openspine.io.SkeletonExporter;
import com.openspine.io.SkeletonLoader;
import com.openspine.model.SkeletonEditor.MeshGeometryState;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

/** Headless checks of image coverage, topology, and setup-space binding invariants. */
public final class MeshGenerationTest {
	public static void main(String[] args) throws Exception {
		tracesVisiblePixelsWithoutChangingTextureMapping();
		tracingProtectsEditedShapesAtAnySetupTranslation();
		tracesConcavePixelsAndPreservesTheirMapping();
		simplifiesWithinTheRequestedBoundaryTolerance();
		rejectsUnsupportedOutlineTopologyAndVertexLimits();
		generatedOutlineUsesNormalValidationAndUndo();
		subdivisionPreservesExistingMesh();
		automaticWeightsPreserveSetupAndLocks();
		System.out.println("ALL MESH GENERATION TESTS PASSED");
	}

	private static void tracingProtectsEditedShapesAtAnySetupTranslation() {
		MeshGeometryState source = quad();
		int[] alpha = concaveMask(8);
		for (float offset : new float[] {0f, 10_000f, 1_000_000f, -1_000_000f}) {
			float[] positions = source.vertices(), uvs = source.regionUVs();
			for (int i = 0; i < positions.length; i += 2) {
				positions[i] = (float)(offset + 91.25d * uvs[i] + 27.375d * uvs[i + 1]);
				positions[i + 1] = (float)(-offset - 35.625d * uvs[i] + 86.125d * uvs[i + 1]);
			}
			MeshGeometryState translated = geometry(source, null, positions);
			MeshGeometryState divided = MeshGeneration.subdivide(translated, 2);
			for (MeshGeometryState valid : new MeshGeometryState[] {translated, divided}) {
				MeshGeometryState trace = MeshGeneration.traceConcave(valid, 8, 8, alpha, 16, 0f, 64);
				float[] generated = trace.vertices(), traceUVs = trace.regionUVs();
				for (int i = 0; i < generated.length; i += 2) {
					double expectedX = offset + 91.25d * traceUVs[i] + 27.375d * traceUVs[i + 1];
					double expectedY = -offset - 35.625d * traceUVs[i] + 86.125d * traceUVs[i + 1];
					double tolerance = Math.max(0.005d, 2d * Math.ulp(offset));
					check(Math.abs(expectedX - generated[i]) <= tolerance
						&& Math.abs(expectedY - generated[i + 1]) <= tolerance,
						"tracing a translated, rotated and subdivided mesh preserves its image mapping at " + offset);
				}
			}
			positions[6] += 20f;
			MeshGeometryState edited = geometry(source, null, positions);
			throwsArgument(() -> MeshGeneration.traceConvex(edited, 8, 8, alpha, 16),
				"convex tracing cannot erase a twenty-pixel shape edit at setup translation " + offset);
			throwsArgument(() -> MeshGeneration.traceConcave(edited, 8, 8, alpha, 16, 0f, 64),
				"concave tracing cannot erase a twenty-pixel shape edit at setup translation " + offset);
			check(Arrays.equals(positions, edited.vertices()), "rejected tracing keeps the edited mesh intact");
		}
		float[] invalid = source.vertices();
		invalid[6] = Float.NaN;
		throwsArgument(() -> MeshGeneration.affineTransform(source.regionUVs(), invalid),
			"a non-finite vertex outside the fitted basis is rejected");
		float[] invalidUVs = source.regionUVs();
		invalidUVs[6] = Float.NaN;
		throwsArgument(() -> MeshGeneration.affineTransform(invalidUVs, source.vertices()),
			"a non-finite UV outside the fitted basis is rejected");
		float[] closeUVs = {0f, 0f, 0.000001f, 0f, 1f, 1f, 0f, 1f};
		float[] rounded = {1_000_000f, 1_000_000f, 1_000_000f, 1_000_000f,
			1_000_100f, 1_000_100f, 1_000_000f, 1_000_100f};
		float[] stable = MeshGeneration.affineTransform(closeUVs, rounded);
		near(100f, stable[0], "closely spaced first vertices do not determine an unstable UV basis");
		near(100f, stable[4], "the wide UV basis preserves both mapped axes");
	}

	private static void tracesVisiblePixelsWithoutChangingTextureMapping() {
		MeshGeometryState source = quad();
		int[] alpha = new int[16];
		alpha[5] = alpha[6] = alpha[9] = alpha[10] = 255;
		alpha[0] = 8;
		MeshGeometryState traced = MeshGeneration.traceConvex(source, 4, 4, alpha, 16);
		near(2500f, area(traced), "transparent margins are excluded from the generated outline");
		for (int index = 0; index < traced.vertices().length; index += 2) {
			near(100f * traced.regionUVs()[index], traced.vertices()[index], "trace keeps the original texture X mapping");
			near(100f - 100f * traced.regionUVs()[index + 1], traced.vertices()[index + 1],
				"trace keeps the inverted texture Y mapping");
		}
		check(area(MeshGeneration.traceConvex(source, 4, 4, alpha, 1)) > area(traced),
			"the Alpha threshold changes which pixels are included");
		check(traced.hullLength() == traced.vertices().length && traced.triangles().length == 6,
			"the generated outline has a complete triangulation and hull: " + Arrays.toString(traced.regionUVs()));
		throwsArgument(() -> MeshGeneration.traceConvex(source, 4, 4, new int[16], 16),
			"transparent images cannot replace the mesh with empty geometry");
		float[] modified = source.vertices();
		modified[4] += 5f;
		MeshGeometryState nonAffine = geometry(source, null, modified);
		throwsArgument(() -> MeshGeneration.traceConvex(nonAffine, 4, 4, alpha, 16),
			"already deformed setup geometry is protected from an affine reconstruction");
		float[] rotated = MeshGeneration.affineTransform(source.regionUVs(),
			new float[] {20f, 10f, 20f, 110f, -80f, 110f, -80f, 10f});
		near(-100f, rotated[1], "rotated image-to-setup mapping retains rotation");
		near(100f, rotated[3], "rotated image-to-setup mapping retains both axes");
	}

	private static void tracesConcavePixelsAndPreservesTheirMapping() {
		MeshGeometryState source = quad();
		int[] alpha = concaveMask(8);
		MeshGeometryState outline = MeshGeneration.traceConcave(source, 8, 8, alpha, 16, 0f, 64);
		near(3125f, area(outline), "the L-shaped trace excludes the transparent concavity");
		check(area(MeshGeneration.traceConvex(source, 8, 8, alpha, 16)) > area(outline),
			"the retained convex mode intentionally covers the concavity");
		check(outline.vertexCount() == 6 && outline.triangles().length == 12,
			"collinear pixel boundaries simplify to six L-shaped corners");
		assertPixelCoverage(outline, alpha, 8, 8, 16);
		for (int i = 0; i < outline.vertices().length; i += 2) {
			near(outline.regionUVs()[i] * 100f, outline.vertices()[i], "concave trace preserves X mapping");
			near(100f - outline.regionUVs()[i + 1] * 100f, outline.vertices()[i + 1],
				"concave trace preserves inverted Y mapping");
		}
		MeshGeometryState reflected = geometry(source, null, new float[] {20f, 10f, 20f, -90f, -80f, -90f, -80f, 10f});
		MeshGeometryState rotated = MeshGeneration.traceConcave(reflected, 8, 8, alpha, 16, 0f, 64);
		for (int i = 0; i < rotated.vertices().length; i += 2) {
			near(20f - rotated.regionUVs()[i + 1] * 100f, rotated.vertices()[i], "rotation and reflection preserve X");
			near(10f - rotated.regionUVs()[i] * 100f, rotated.vertices()[i + 1], "rotation and reflection preserve Y");
		}
		check(source.sameContent(quad()), "tracing only creates a draft and never mutates the source");
		int[] onePixel = new int[9];
		onePixel[4] = 255;
		MeshGeometryState tiny = MeshGeneration.traceConcave(source, 3, 3, onePixel, 16, 8f, 32);
		check(tiny.vertexCount() >= 3 && area(tiny) > 0f, "large tolerance cannot erase a one-pixel region");
	}

	private static void simplifiesWithinTheRequestedBoundaryTolerance() {
		int size = 64;
		int[] alpha = new int[size * size];
		for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
			if (Math.hypot(x - 31.5, y - 31.5) < 26d && !(x >= 32 && y >= 23 && y <= 40))
				alpha[y * size + x] = 255;
		MeshGeometryState exact = MeshGeneration.traceConcave(quad(), size, size, alpha, 16, 0f, 4096);
		MeshGeometryState reduced = MeshGeneration.traceConcave(quad(), size, size, alpha, 16, 1f, 64);
		check(reduced.vertexCount() < exact.vertexCount() && reduced.vertexCount() <= 64,
			"pixel tolerance reduces a curved concave silhouette to the requested vertex budget");
		Polygon original = pixelPolygon(exact, size, size), simplified = pixelPolygon(reduced, size, size);
		check(simplified.isValid() && simplified.getNumInteriorRing() == 0,
			"simplification keeps one valid polygon without holes");
		double deviation = DiscreteHausdorffDistance.distance(original.getBoundary(), simplified.getBoundary(), 0.25d);
		check(deviation <= 1.0001d, "simplified boundary remains within the one-pixel tolerance: " + deviation);
		check(original.symDifference(simplified).getArea() <= original.getLength() * 1.1d,
			"changed area remains bounded by the silhouette perimeter and tolerance");
		check(!simplified.covers(new GeometryFactory().createPoint(new Coordinate(50, 32))),
			"simplification preserves the deep concave opening");
		int[] noisy = new int[32 * 32];
		for (int y = 8; y < 24; y++) for (int x = 6; x < 26; x++) noisy[y * 32 + x] = 255;
		for (int x = 6; x < 26; x += 2) noisy[7 * 32 + x] = 255;
		MeshGeometryState noiseExact = MeshGeneration.traceConcave(quad(), 32, 32, noisy, 16, 0f, 128);
		MeshGeometryState noiseReduced = MeshGeneration.traceConcave(quad(), 32, 32, noisy, 16, 1f, 32);
		check(noiseReduced.vertexCount() < noiseExact.vertexCount(), "connected one-pixel edge noise can be simplified");
	}

	private static void rejectsUnsupportedOutlineTopologyAndVertexLimits() {
		MeshGeometryState source = quad();
		int[] hole = new int[25];
		Arrays.fill(hole, 255);
		hole[12] = 0;
		throwsMessage(() -> MeshGeneration.traceConcave(source, 5, 5, hole, 16, 8f, 32), "孔洞",
			"holes are rejected before simplification could fill them");
		int[] islands = new int[25];
		islands[6] = islands[18] = 255;
		throwsMessage(() -> MeshGeneration.traceConcave(source, 5, 5, islands, 16, 8f, 32), "分离区域",
			"separate islands are not silently merged");
		int[] contact = {255, 0, 0, 255};
		throwsArgument(() -> MeshGeneration.traceConcave(source, 2, 2, contact, 16, 0f, 32),
			"point-contact components are not emitted as a self-touching mesh");
		int[] visible = concaveMask(8);
		throwsMessage(() -> MeshGeneration.traceConcave(source, 8, 8, visible, 16, 0f, 4), "上限",
			"vertex limits reject the whole generated draft instead of truncating the boundary");
		throwsArgument(() -> MeshGeneration.traceConcave(source, 8, 8, new int[64], 16, 0f, 64),
			"transparent images cannot replace the current mesh");
		visible[0] = 8;
		check(MeshGeneration.traceConcave(source, 8, 8, visible, 16, 0f, 64).vertexCount() == 6,
			"the alpha threshold removes isolated low-alpha noise");
		throwsArgument(() -> MeshGeneration.traceConcave(source, 8, 8, visible, 1, 0f, 64),
			"including isolated noise reports the unsupported topology");
		throwsArgument(() -> MeshGeneration.traceConcave(source, 8, 8, visible, 16, Float.NaN, 64),
			"non-finite simplification values are rejected");
		throwsArgument(() -> MeshGeneration.traceConcave(source, 8, 8, visible, 16, 0f, 4097),
			"the hard generation limit cannot be bypassed");
		int[] noisyMask = new int[512 * 512];
		for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
			if (((x + y) & 1) == 0) noisyMask[y * 512 + x] = 255;
		throwsMessage(() -> MeshGeneration.traceConcave(source, 512, 512, noisyMask, 16, 0f, 4096), "过于复杂",
			"high-noise images stop at the alpha-run budget before expensive boolean geometry");
		MeshGeometryState collapsed = geometry(source, null, new float[8]);
		throwsArgument(() -> MeshGeneration.traceConcave(collapsed, 8, 8, visible, 16, 0f, 64),
			"a collapsed setup mapping cannot generate degenerate triangles");
		MeshGeometryState weighted = MeshGeneration.autoWeight(source, setup(), 0, new int[] {1}, 1, 2f, Set.of());
		throwsArgument(() -> MeshGeneration.traceConcave(weighted, 8, 8, visible, 16, 0f, 64),
			"tracing never replaces existing weighted coordinates");
		check(source.sameContent(quad()), "all rejected traces leave the original draft intact");
	}

	private static void generatedOutlineUsesNormalValidationAndUndo() throws Exception {
		SkeletonDocument doc = new SkeletonDocument();
		doc.skeleton = setup();
		doc.data = doc.skeleton.getData();
		doc.data.getSlots().first().setBlendMode(com.esotericsoftware.spine.BlendMode.normal);
		var skin = new com.esotericsoftware.spine.Skin("default");
		doc.data.setDefaultSkin(skin);
		doc.data.getSkins().add(skin);
		MeshAttachment mesh = new MeshAttachment("image", new Sequence(1, false));
		MeshGeometryState source = quad();
		mesh.setPath("image");
		mesh.setVertices(source.vertices());
		mesh.setRegionUVs(source.regionUVs());
		mesh.setWorldVerticesLength(source.vertexCount() * 2);
		mesh.setTriangles(source.triangles());
		mesh.setHullLength(source.hullLength());
		mesh.setEdges(source.edges());
		mesh.setWidth(source.width());
		mesh.setHeight(source.height());
		skin.setAttachment(0, "image", mesh);
		SkeletonEditor editor = new SkeletonEditor(doc);
		EditHistory history = new EditHistory(doc);
		doc.markSaved();
		MeshGeometryState draft = MeshGeneration.traceConcave(source, 8, 8, concaveMask(8), 16, 0f, 64);
		check(editor.validateMeshGeometry(mesh, draft) == null, "a simple concave hull satisfies the runtime/binary mesh contract");
		check(SkeletonEditor.meshGeometryState(mesh).sameContent(source) && !doc.dirty,
			"generating and validating a draft does not publish it");
		check(editor.updateMeshGeometry(mesh, draft), "normal Apply accepts the generated outline");
		history.record("trace", () -> editor.updateMeshGeometry(mesh, source), () -> editor.updateMeshGeometry(mesh, draft));
		check(history.undo() && SkeletonEditor.meshGeometryState(mesh).sameContent(source) && !doc.dirty,
			"one undo restores the original outline and savepoint");
		check(history.redo() && SkeletonEditor.meshGeometryState(mesh).sameContent(draft),
			"redo restores the complete generated topology");
		assertRuntimeRoundTrip(doc, draft);
		MeshGeometryState divided = MeshGeneration.subdivide(draft, 1);
		near(area(draft), area(divided), "subdivision preserves the traced concavity");
		check(Arrays.equals(draft.edges(), Arrays.copyOf(divided.edges(), draft.edges().length))
			&& draft.hullLength() == divided.hullLength(),
			"subdivision retains the entire concave boundary");
		check(editor.updateMeshGeometry(mesh, divided), "the refined concave draft passes normal Apply");
		assertRuntimeRoundTrip(doc, divided);
		MeshGeometryState weighted = MeshGeneration.autoWeight(divided, doc.skeleton, 0, new int[] {1, 2}, 2, 2f, Set.of());
		check(editor.updateMeshGeometry(mesh, weighted), "the bound concave draft passes normal Apply");
		assertRuntimeRoundTrip(doc, weighted);
	}

	private static void assertRuntimeRoundTrip(SkeletonDocument doc, MeshGeometryState expected) throws Exception {
		Path directory = Files.createTempDirectory("openspine-generated-mesh-");
		Path json = directory.resolve("mesh.json"), binary = directory.resolve("mesh.skel");
		try {
			SkeletonExporter.writeAtomic(doc, json);
			SkeletonBinaryExporter.writeAtomic(doc, binary);
			for (Path path : new Path[] {json, binary}) {
				SkeletonData loaded = SkeletonLoader.loadAnimationSource(new FileHandle(path.toFile()));
				MeshAttachment mesh = (MeshAttachment)loaded.getDefaultSkin().getAttachment(0, "image");
				check(mesh != null && SkeletonEditor.meshGeometryState(mesh).sameContent(expected),
					"generated hull, UVs, triangles, edges and weights survive the official reader for " + path.getFileName());
			}
		} finally {
			Files.deleteIfExists(json);
			Files.deleteIfExists(binary);
			Files.deleteIfExists(directory);
		}
	}

	private static int[] concaveMask(int size) {
		int[] alpha = new int[size * size];
		for (int y = 1; y < 7; y++) for (int x = 1; x < 7; x++)
			if (x < 3 || y >= 5) alpha[y * size + x] = 255;
		return alpha;
	}

	private static Polygon pixelPolygon(MeshGeometryState outline, int width, int height) {
		float[] uvs = outline.regionUVs();
		Coordinate[] coordinates = new Coordinate[outline.vertexCount() + 1];
		for (int i = 0; i < outline.vertexCount(); i++)
			coordinates[i] = new Coordinate(uvs[i * 2] * width, uvs[i * 2 + 1] * height);
		coordinates[coordinates.length - 1] = coordinates[0].copy();
		return new GeometryFactory().createPolygon(coordinates);
	}

	private static void assertPixelCoverage(MeshGeometryState outline, int[] alpha, int width, int height, int threshold) {
		float[] uvs = outline.regionUVs();
		short[] triangles = outline.triangles();
		GeometryFactory geometry = new GeometryFactory();
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			boolean covered = false;
			var pixel = geometry.createPoint(new Coordinate((x + 0.5d) / width, (y + 0.5d) / height));
			for (int i = 0; i < triangles.length; i += 3) {
				int a = Short.toUnsignedInt(triangles[i]) * 2, b = Short.toUnsignedInt(triangles[i + 1]) * 2;
				int c = Short.toUnsignedInt(triangles[i + 2]) * 2;
				Polygon triangle = geometry.createPolygon(new Coordinate[] {new Coordinate(uvs[a], uvs[a + 1]),
					new Coordinate(uvs[b], uvs[b + 1]), new Coordinate(uvs[c], uvs[c + 1]), new Coordinate(uvs[a], uvs[a + 1])});
				covered |= triangle.covers(pixel);
			}
			check(covered == (alpha[y * width + x] >= threshold), "triangles match pixel coverage at " + x + "," + y);
		}
	}

	private static void subdivisionPreservesExistingMesh() {
		MeshGeometryState source = quad();
		MeshGeometryState once = MeshGeneration.subdivide(source, 1);
		checkFaceCentroids(source.vertices(), source.triangles(), once.vertices(), source.vertexCount());
		MeshGeometryState divided = MeshGeneration.subdivide(source, 2);
		checkFaceCentroids(once.vertices(), once.triangles(), divided.vertices(), once.vertexCount());
		check(divided.vertexCount() == 12 && divided.triangles().length == 54,
			"two subdivision passes split each original face consistently");
		check(Arrays.equals(source.vertices(), Arrays.copyOf(divided.vertices(), source.vertices().length)),
			"existing vertices are unchanged by generation");
		check(Arrays.equals(source.regionUVs(), Arrays.copyOf(divided.regionUVs(), source.regionUVs().length)),
			"existing UVs are unchanged by generation");
		check(Arrays.equals(source.edges(), Arrays.copyOf(divided.edges(), source.edges().length))
			&& divided.hullLength() == source.hullLength(),
			"subdivision keeps constrained edges and the external hull");
		near(area(source), area(divided), "subdivision covers exactly the original visible area");
		check(source.vertexCount() == 4 && source.triangles().length == 6, "generation does not mutate the source");
		throwsArgument(() -> MeshGeneration.subdivide(source, 0), "invalid subdivision levels are rejected");
	}

	private static void automaticWeightsPreserveSetupAndLocks() {
		Skeleton setup = setup();
		MeshGeometryState source = quad();
		MeshGeometryState weighted = MeshGeneration.autoWeight(source, setup, 0, new int[] {1, 2}, 2, 2f, Set.of());
		arraysNear(world(source, setup), world(weighted, setup), "binding preserves every original setup world position");
		check(weighted.bones()[0] == 2, "automatic weights use both selected bones");
		check(weighted.vertices()[2] > weighted.vertices()[5], "the closer bone receives more influence");
		for (int vertex = 0; vertex < 4; vertex++)
			near(1f, weighted.vertices()[vertex * 6 + 2] + weighted.vertices()[vertex * 6 + 5], "weights are normalized");
		MeshGeometryState single = MeshGeneration.autoWeight(source, setup, 0, new int[] {2, 1, 2}, 1, 8f, Set.of());
		check(single.bones().length == 8, "maximum influence count and duplicate bone inputs are handled");
		arraysNear(world(source, setup), world(single, setup), "one-bone binding still preserves setup geometry");
		MeshGeometryState divided = MeshGeneration.subdivide(weighted, 1);
		float[] originalWorld = world(weighted, setup), generatedWorld = world(divided, setup);
		arraysNear(originalWorld, Arrays.copyOf(generatedWorld, originalWorld.length),
			"weighted subdivision preserves all existing world vertices");
		checkFaceCentroids(originalWorld, weighted.triangles(), generatedWorld, weighted.vertexCount());

		// Deliberately use distinct local positions so preserving only the weight would move the vertex.
		float[] offsets = weighted.vertices();
		offsets[0] += 7f;
		offsets[3] -= 3f;
		offsets[2] = 0.3f;
		offsets[5] = 0.7f;
		MeshGeometryState varied = geometry(weighted, weighted.bones(), offsets);
		int lockedBone = varied.bones()[1];
		Set<SkeletonDocument.MeshWeightLock> locks = Set.of(new SkeletonDocument.MeshWeightLock(0, lockedBone));
		MeshGeometryState rebound = MeshGeneration.autoWeight(varied, setup, 0, new int[] {0, 1, 2}, 3, 1f, locks);
		near(varied.vertices()[0], rebound.vertices()[0], "locked local X is retained");
		near(varied.vertices()[1], rebound.vertices()[1], "locked local Y is retained");
		near(varied.vertices()[2], rebound.vertices()[2], "locked weight is retained");
		arraysNear(world(varied, setup), world(rebound, setup), "locked contributions are compensated without moving setup geometry");
		throwsArgument(() -> MeshGeneration.autoWeight(varied, setup, 0, new int[] {1, 2}, 1, 2f, locks),
			"max influence count cannot discard a locked influence or leave a weight deficit");
		throwsArgument(() -> MeshGeneration.autoWeight(source, setup, 0, new int[0], 2, 2f, Set.of()),
			"an empty binding selection is rejected");
	}

	private static void checkFaceCentroids(float[] original, short[] faces, float[] generated, int originalCount) {
		for (int face = 0; face < faces.length; face += 3) {
			int a = Short.toUnsignedInt(faces[face]) * 2;
			int b = Short.toUnsignedInt(faces[face + 1]) * 2;
			int c = Short.toUnsignedInt(faces[face + 2]) * 2;
			float x = (original[a] + original[b] + original[c]) / 3f;
			float y = (original[a + 1] + original[b + 1] + original[c + 1]) / 3f;
			boolean found = false;
			for (int vertex = originalCount * 2; vertex < generated.length; vertex += 2)
				if (Math.abs(x - generated[vertex]) < 0.001f && Math.abs(y - generated[vertex + 1]) < 0.001f)
					found = true;
			check(found, "every original face gets a centroid vertex in each subdivision pass");
		}
	}

	private static Skeleton setup() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		root.getSetupPose().setPosition(15f, 25f);
		root.getSetupPose().setRotation(25f);
		root.getSetupPose().setScale(1.5f, 0.8f);
		BoneData left = new BoneData(1, "left", root);
		left.getSetupPose().setInherit(BoneData.Inherit.normal);
		left.getSetupPose().setScale(1f, 1f);
		left.getSetupPose().setPosition(0f, 100f);
		left.setLength(40f);
		BoneData right = new BoneData(2, "right", root);
		right.getSetupPose().setInherit(BoneData.Inherit.normal);
		right.getSetupPose().setScale(1f, 1f);
		right.getSetupPose().setPosition(100f, 0f);
		right.setLength(0f);
		data.getBones().add(root);
		data.getBones().add(left);
		data.getBones().add(right);
		data.getSlots().add(new SlotData(0, "slot", root));
		Skeleton skeleton = new Skeleton(data);
		skeleton.updateWorldTransform(Physics.pose);
		return skeleton;
	}

	private static MeshGeometryState quad() {
		return new MeshGeometryState(Color.WHITE, "image", null,
			new float[] {0f, 100f, 100f, 100f, 100f, 0f, 0f, 0f},
			new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f},
			new short[] {0, 1, 2, 2, 3, 0}, 8, new short[] {0, 1, 1, 2, 2, 3, 3, 0}, 100f, 100f);
	}

	private static MeshGeometryState geometry(MeshGeometryState source, int[] bones, float[] vertices) {
		return new MeshGeometryState(source.color(), source.path(), bones, vertices, source.regionUVs(),
			source.triangles(), source.hullLength(), source.edges(), source.width(), source.height());
	}

	private static float[] world(MeshGeometryState state, Skeleton skeleton) {
		MeshAttachment mesh = new MeshAttachment("test", new Sequence(1, false));
		mesh.setBones(state.bones());
		mesh.setVertices(state.vertices());
		mesh.setWorldVerticesLength(state.vertexCount() * 2);
		float[] result = new float[state.vertexCount() * 2];
		mesh.computeWorldVertices(skeleton, skeleton.getSlots().first(), 0, result.length, result, 0, 2);
		return result;
	}

	private static float area(MeshGeometryState state) {
		float area = 0f;
		float[] points = state.vertices();
		short[] triangles = state.triangles();
		for (int index = 0; index < triangles.length; index += 3) {
			int a = Short.toUnsignedInt(triangles[index]) * 2, b = Short.toUnsignedInt(triangles[index + 1]) * 2;
			int c = Short.toUnsignedInt(triangles[index + 2]) * 2;
			area += Math.abs((points[b] - points[a]) * (points[c + 1] - points[a + 1])
				- (points[c] - points[a]) * (points[b + 1] - points[a + 1])) * 0.5f;
		}
		return area;
	}

	private static void arraysNear(float[] expected, float[] actual, String message) {
		check(expected.length == actual.length, message + " length");
		for (int index = 0; index < expected.length; index++) near(expected[index], actual[index], message + " at " + index);
	}
	private static void near(float expected, float actual, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > Math.max(0.003f, Math.abs(expected) * 0.0001f))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private static void throwsArgument(Runnable action, String message) {
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message);
	}
	private static void throwsMessage(Runnable action, String expectedMessage, String message) {
		try { action.run(); }
		catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains(expectedMessage), message + ": " + expected.getMessage());
			return;
		}
		throw new AssertionError(message);
	}
}
