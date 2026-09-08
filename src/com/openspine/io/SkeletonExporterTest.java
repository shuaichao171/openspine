package com.openspine.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.JsonReader;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.TransformConstraintData.FromRotate;
import com.esotericsoftware.spine.TransformConstraintData.FromScaleX;
import com.esotericsoftware.spine.TransformConstraintData.FromScaleY;
import com.esotericsoftware.spine.TransformConstraintData.FromShearY;
import com.esotericsoftware.spine.TransformConstraintData.FromX;
import com.esotericsoftware.spine.TransformConstraintData.FromY;
import com.esotericsoftware.spine.TransformConstraintData.ToRotate;
import com.esotericsoftware.spine.TransformConstraintData.ToScaleX;
import com.esotericsoftware.spine.TransformConstraintData.ToScaleY;
import com.esotericsoftware.spine.TransformConstraintData.ToShearY;
import com.esotericsoftware.spine.TransformConstraintData.ToX;
import com.esotericsoftware.spine.TransformConstraintData.ToY;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.Sequence.SequenceMode;
import com.esotericsoftware.spine.attachments.VertexAttachment;
import com.openspine.model.AnimationEditor;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Regression tests for JSON escaping and loss-prevention checks. */
public final class SkeletonExporterTest {
	public static void main(String[] args) throws Exception {
		roundTripsEscapedNamesAndColors();
		roundTripsAttachmentsAndVertices();
		roundTripsRegionConversionWithSequenceTimeline();
		roundTripsEditorCreatedLinkedMesh();
		roundTripsIndependentLinkedMeshDeformAfterSourceWeightEdit();
		preservesDeformOffsetsAfterSetupAttachmentEdits();
		roundTripsWeightedDeformAfterInfluenceReorder();
		roundTripsLinkedMeshSourceChangeWithIndependentDeform();
		roundTripsSequenceIdentityReplacementWithMeshReferences();
		roundTripsDeformAndSequenceTimelines();
		roundTripsBezierCurves();
		roundTripsAsynchronousCombinedAxes();
		roundTripsSplitColorTimelines();
		roundTripsIkConstraints();
		roundTripsTransformConstraints();
		roundTripsPathAndPhysicsConstraints();
		roundTripsSliderConstraints();
		roundTripsDrawOrderTimelines();
		roundTripsEvents();
		roundTripsSkeletonStructure();
		declaresActualExportFormatVersion();
		rejectsInvalidSkeletonStructure();
		rejectsUnsupportedOrInvalidData();
		rejectsInvalidPathAndPhysicsData();
		atomicallyReplacesFilesWithoutLeavingTemporaryData();
		System.out.println("ALL SKELETON EXPORTER TESTS PASSED");
	}

	private static void declaresActualExportFormatVersion() {
		SkeletonData data = baseData("root", "child");
		data.setVersion("4.2.99");
		String json = SkeletonExporter.toJson(document(data));
		String exported = new JsonReader().parse(json).get("skeleton").getString("spine");
		assertTrue(SkeletonExporter.EXPORT_VERSION.equals(exported),
			"JSON declares the format version produced by OpenSpine");
		assertTrue("4.2.99".equals(data.getVersion()),
			"export does not mutate loaded version metadata in the editor model");
	}

	private static void roundTripsAttachmentsAndVertices() {
		SkeletonData data = baseData("root", "child");
		for (int index = 0; index < 3; index++) {
			SlotData slot = new SlotData(index, "attachment-slot-" + index, data.getBones().get(index == 0 ? 0 : 1));
			slot.setBlendMode(BlendMode.normal);
			data.getSlots().add(slot);
		}

		Skin defaultSkin = new Skin("default");
		Sequence regionSequence = sequence(3, 7, 2, 1);
		RegionAttachment region = new RegionAttachment("region-actual", regionSequence);
		region.setPath("images/region-sequence");
		region.setX(3f);
		region.setY(-4f);
		region.setScaleX(1.25f);
		region.setScaleY(0.75f);
		region.setRotation(18f);
		region.setWidth(48f);
		region.setHeight(24f);
		region.getColor().set(0.2f, 0.4f, 0.6f, 0.8f);
		defaultSkin.setAttachment(0, "region-placeholder", region);

		MeshAttachment source = new MeshAttachment("source-actual", sequence(2, 10, 3, 1));
		source.setPath("images/source-mesh");
		source.getColor().set(0.8f, 0.6f, 0.4f, 0.9f);
		source.setWidth(80f);
		source.setHeight(50f);
		source.setWorldVerticesLength(8);
		source.setVertices(new float[] {-20f, -10f, 20f, -10f, 20f, 10f, -20f, 10f});
		source.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f});
		source.setTriangles(new short[] {0, 1, 2, 2, 3, 0});
		source.setHullLength(6);
		source.setEdges(new short[] {0, 1, 1, 2, 2, 3});
		source.setTimelineSlots(new int[] {2});
		defaultSkin.setAttachment(0, "source-placeholder", source);

		MeshAttachment weighted = new MeshAttachment("weighted-actual", new Sequence(1, false));
		weighted.setPath("images/weighted-mesh");
		weighted.setWidth(25f);
		weighted.setHeight(15f);
		weighted.setWorldVerticesLength(4);
		weighted.setBones(new int[] {2, 0, 1, 1, 1});
		weighted.setVertices(new float[] {-2f, 1f, 0.25f, 3f, 4f, 0.75f, 6f, -5f, 1f});
		weighted.setRegionUVs(new float[] {0f, 0f, 1f, 1f});
		weighted.setTriangles(new short[] {0, 1, 0});
		weighted.setHullLength(4);
		defaultSkin.setAttachment(1, "weighted-placeholder", weighted);

		MeshAttachment hullFree = new MeshAttachment("hull-free-actual", new Sequence(1, false));
		hullFree.setPath("images/hull-free-mesh");
		hullFree.setWidth(30f);
		hullFree.setHeight(20f);
		hullFree.setWorldVerticesLength(8);
		hullFree.setVertices(new float[] {-15f, -10f, 15f, -10f, 15f, 10f, -15f, 10f});
		hullFree.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f});
		hullFree.setTriangles(new short[] {0, 1, 2, 2, 3, 0});
		defaultSkin.setAttachment(2, "hull-free-placeholder", hullFree);

		BoundingBoxAttachment box = new BoundingBoxAttachment("box-actual");
		box.setWorldVerticesLength(6);
		box.setBones(new int[] {1, 0, 1, 1, 1, 0});
		box.setVertices(new float[] {-2f, -1f, 1f, 3f, -1f, 1f, 0f, 4f, 1f});
		box.getColor().set(0.3f, 0.7f, 0.2f, 1f);
		defaultSkin.setAttachment(1, "box-placeholder", box);

		PathAttachment path = new PathAttachment("path-actual");
		path.setClosed(true);
		path.setConstantSpeed(false);
		path.setWorldVerticesLength(12);
		path.setBones(new int[] {1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1});
		path.setVertices(new float[] {0f, 0f, 1f, 2f, 1f, 1f, 4f, 1f, 1f,
			6f, 0f, 1f, 8f, -1f, 1f, 10f, 0f, 1f});
		path.setLengths(new float[] {6f, 12f});
		path.getColor().set(1f, 0.4f, 0.1f, 0.75f);
		defaultSkin.setAttachment(1, "path-placeholder", path);

		PointAttachment point = new PointAttachment("point-actual");
		point.setX(12f);
		point.setY(-7f);
		point.setRotation(35f);
		point.getColor().set(0.9f, 0.8f, 0.1f, 1f);
		defaultSkin.setAttachment(2, "point-placeholder", point);

		ClippingAttachment clipping = new ClippingAttachment("clip-actual");
		clipping.setEndSlot(data.getSlots().get(2));
		clipping.setConvex(true);
		clipping.setInverse(true);
		clipping.setWorldVerticesLength(6);
		clipping.setBones(new int[] {1, 0, 1, 1, 1, 0});
		clipping.setVertices(new float[] {-3f, -3f, 1f, 4f, -3f, 1f, 0f, 5f, 1f});
		clipping.getColor().set(0.2f, 0.3f, 0.8f, 0.7f);
		defaultSkin.setAttachment(2, "clip-placeholder", clipping);

		Skin linkedSkin = new Skin("linked-skin");
		MeshAttachment linked = new MeshAttachment("linked-actual", new Sequence(1, false));
		linked.setPath("images/linked-variant");
		linked.setSourceMesh(source);
		linked.setTimelineAttachment(source);
		linkedSkin.setAttachment(2, "linked-placeholder", linked);
		MeshAttachment independent = new MeshAttachment("linked-independent", new Sequence(1, false));
		independent.setPath("images/linked-independent");
		independent.setSourceMesh(source);
		independent.setTimelineAttachment(independent);
		linkedSkin.setAttachment(1, "independent-placeholder", independent);

		data.getSkins().add(defaultSkin);
		data.getSkins().add(linkedSkin);
		data.setDefaultSkin(defaultSkin);

		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"type\": \"mesh\""), "mesh type is exported");
		assertTrue(json.contains("\"type\": \"linkedmesh\""), "linked mesh type is exported");
		assertTrue(json.contains("\"sequence\": { \"count\": 3"), "region sequence is exported");
		assertTrue(json.contains("\"timelines\": false"), "independent linked mesh timeline mode is exported");

		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		Skin loadedDefault = loaded.findSkin("default");
		Skin loadedLinkedSkin = loaded.findSkin("linked-skin");
		assertTrue(loadedDefault != null && loadedLinkedSkin != null, "attachment skins round trip");

		RegionAttachment loadedRegion = (RegionAttachment)loadedDefault.getAttachment(0, "region-placeholder");
		assertTrue("region-actual".equals(loadedRegion.getName()), "attachment name round trip");
		assertTrue("images/region-sequence".equals(loadedRegion.getPath()), "region path round trip");
		assertSequence(regionSequence, loadedRegion.getSequence(), "region sequence");
		assertNear(region.getX(), loadedRegion.getX(), "region X");
		assertNear(region.getScaleY(), loadedRegion.getScaleY(), "region scale Y");
		assertColor(region.getColor(), loadedRegion.getColor(), "region attachment color");

		MeshAttachment loadedSource = (MeshAttachment)loadedDefault.getAttachment(0, "source-placeholder");
		assertTrue(loadedSource.getSourceMesh() == null, "ordinary mesh remains independent");
		assertFloatArray(source.getRegionUVs(), loadedSource.getRegionUVs(), "mesh UVs");
		assertFloatArray(source.getVertices(), loadedSource.getVertices(), "mesh vertices");
		assertShortArray(source.getTriangles(), loadedSource.getTriangles(), "mesh triangles");
		assertShortArray(source.getEdges(), loadedSource.getEdges(), "mesh edges");
		assertTrue(source.getHullLength() == loadedSource.getHullLength(), "mesh hull round trip");
		assertNear(source.getWidth(), loadedSource.getWidth(), "mesh width");
		assertNear(source.getHeight(), loadedSource.getHeight(), "mesh height");
		assertSequence(source.getSequence(), loadedSource.getSequence(), "mesh sequence");
		assertIntArray(source.getTimelineSlots(), loadedSource.getTimelineSlots(), "mesh timeline slots");

		MeshAttachment loadedWeighted = (MeshAttachment)loadedDefault.getAttachment(1, "weighted-placeholder");
		assertIntArray(weighted.getBones(), loadedWeighted.getBones(), "weighted mesh bones");
		assertFloatArray(weighted.getVertices(), loadedWeighted.getVertices(), "weighted mesh values");
		assertTrue(weighted.getWorldVerticesLength() == loadedWeighted.getWorldVerticesLength(),
			"weighted mesh world vertex length");

		MeshAttachment loadedHullFree = (MeshAttachment)loadedDefault.getAttachment(2, "hull-free-placeholder");
		assertTrue(loadedHullFree.getHullLength() == 0, "mesh with omitted hull retains the legal zero value");
		assertFloatArray(hullFree.getVertices(), loadedHullFree.getVertices(), "hull-free mesh vertices");
		assertFloatArray(hullFree.getRegionUVs(), loadedHullFree.getRegionUVs(), "hull-free mesh UVs");
		assertShortArray(hullFree.getTriangles(), loadedHullFree.getTriangles(), "hull-free mesh triangles");

		MeshAttachment loadedLinked = (MeshAttachment)loadedLinkedSkin.getAttachment(2, "linked-placeholder");
		assertTrue(loadedLinked.getSourceMesh() == loadedSource, "cross-skin linked mesh source");
		assertTrue(loadedLinked.getTimelineAttachment() == loadedSource, "linked mesh inherits source timelines");
		assertTrue("linked-actual".equals(loadedLinked.getName()), "linked mesh name round trip");
		assertTrue("images/linked-variant".equals(loadedLinked.getPath()), "linked mesh path round trip");
		MeshAttachment loadedIndependent = (MeshAttachment)loadedLinkedSkin.getAttachment(1, "independent-placeholder");
		assertTrue(loadedIndependent.getSourceMesh() == loadedSource, "independent linked mesh source");
		assertTrue(loadedIndependent.getTimelineAttachment() == loadedIndependent,
			"linked mesh can disable timeline inheritance");

		BoundingBoxAttachment loadedBox = (BoundingBoxAttachment)loadedDefault.getAttachment(1, "box-placeholder");
		assertIntArray(box.getBones(), loadedBox.getBones(), "weighted bounding box bones");
		assertFloatArray(box.getVertices(), loadedBox.getVertices(), "bounding box vertices");
		assertColor(box.getColor(), loadedBox.getColor(), "bounding box color");
		PathAttachment loadedPath = (PathAttachment)loadedDefault.getAttachment(1, "path-placeholder");
		assertTrue(loadedPath.getClosed() && !loadedPath.getConstantSpeed(), "path flags round trip");
		assertIntArray(path.getBones(), loadedPath.getBones(), "weighted path bones");
		assertFloatArray(path.getVertices(), loadedPath.getVertices(), "path vertices");
		assertFloatArray(path.getLengths(), loadedPath.getLengths(), "path lengths");
		PointAttachment loadedPoint = (PointAttachment)loadedDefault.getAttachment(2, "point-placeholder");
		assertNear(point.getX(), loadedPoint.getX(), "point X");
		assertNear(point.getRotation(), loadedPoint.getRotation(), "point rotation");
		assertColor(point.getColor(), loadedPoint.getColor(), "point color");
		ClippingAttachment loadedClip = (ClippingAttachment)loadedDefault.getAttachment(2, "clip-placeholder");
		assertTrue(loadedClip.getEndSlot() == loaded.getSlots().get(2), "clipping end slot round trip");
		assertTrue(loadedClip.getConvex() && loadedClip.getInverse(), "clipping flags round trip");
		assertIntArray(clipping.getBones(), loadedClip.getBones(), "weighted clipping bones");
		assertFloatArray(clipping.getVertices(), loadedClip.getVertices(), "clipping vertices");
		assertColor(clipping.getColor(), loadedClip.getColor(), "clipping color");
	}

	private static void roundTripsRegionConversionWithSequenceTimeline() {
		SkeletonData data = baseData("root", "child");
		SlotData slot = new SlotData(0, "region-conversion-slot", data.getBones().get(1));
		slot.setBlendMode(BlendMode.normal);
		slot.setAttachmentName("body");
		data.getSlots().add(slot);
		Skin skin = new Skin("default");
		TextureRegion texture = new TextureRegion();
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = texture;
		RegionAttachment region = new RegionAttachment("body", sequence);
		region.setPath("body");
		region.setX(5f);
		region.setY(-3f);
		region.setScaleX(1.2f);
		region.setScaleY(0.8f);
		region.setRotation(20f);
		region.setWidth(64f);
		region.setHeight(48f);
		region.updateSequence();
		skin.setAttachment(0, "body", region);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);
		Animation.SequenceTimeline sequenceTimeline = new Animation.SequenceTimeline(2, 0, region);
		sequenceTimeline.setFrame(0, 0.1f, SequenceMode.hold, 0, 0f);
		sequenceTimeline.setFrame(1, 0.7f, SequenceMode.loop, 0, 0.2f);
		setOnlyAnimation(data, "region-conversion", sequenceTimeline);

		SkeletonDocument doc = document(data);
		SkeletonEditor editor = new SkeletonEditor(doc) {
			@Override protected TextureRegion findRegion(String name) {
				return "body".equals(name) ? texture : null;
			}
		};
		MeshAttachment converted = editor.setSlotMeshAttachment(skin, slot, "body", "body");
		assertTrue(converted != null && skin.getAttachment(0, "body") == converted,
			"managed Region conversion creates the replacement Mesh before export");
		Animation.SequenceTimeline migrated = findSequenceTimeline(data.findAnimation("region-conversion"), converted);
		assertTrue(migrated != null && migrated != sequenceTimeline,
			"managed Region conversion migrates the Sequence timeline target");

		String json = SkeletonExporter.toJson(doc);
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		MeshAttachment loadedMesh = (MeshAttachment)loaded.findSkin("default").getAttachment(0, "body");
		Animation.SequenceTimeline loadedTimeline =
			findSequenceTimeline(loaded.findAnimation("region-conversion"), loadedMesh);
		assertTrue(loadedMesh != null && loadedMesh.getSourceMesh() == null,
			"converted Region reloads as an independent Mesh");
		assertTrue(loadedTimeline != null, "converted Mesh Sequence timeline survives official reload");
		assertFloatArray(sequenceTimeline.getFrames(), loadedTimeline.getFrames(),
			"converted Mesh Sequence frames survive official reload");
	}

	private static void roundTripsEditorCreatedLinkedMesh() {
		SkeletonData data = baseData("root", "child");
		SlotData sourceSlot = new SlotData(0, "source-slot", data.getBones().first());
		SlotData targetSlot = new SlotData(1, "target-slot", data.getBones().get(1));
		sourceSlot.setBlendMode(BlendMode.normal);
		targetSlot.setBlendMode(BlendMode.normal);
		data.getSlots().addAll(sourceSlot, targetSlot);
		Skin defaultSkin = new Skin("default");
		Skin linkedSkin = new Skin("linked");
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment source = new MeshAttachment("source", sequence);
		source.setPath("source");
		source.setWorldVerticesLength(6);
		source.setVertices(new float[] {-5f, -5f, 5f, -5f, 0f, 5f});
		source.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 0.5f, 0f});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.updateSequence();
		defaultSkin.setAttachment(sourceSlot.getIndex(), "source", source);
		data.getSkins().addAll(defaultSkin, linkedSkin);
		data.setDefaultSkin(defaultSkin);
		SkeletonDocument doc = document(data);
		TextureRegion variant = new TextureRegion();
		SkeletonEditor editor = new SkeletonEditor(doc) {
			@Override protected TextureRegion findRegion(String name) {
				return "variant".equals(name) ? variant : null;
			}
		};
		MeshAttachment linked = editor.createLinkedMeshAttachment(linkedSkin, targetSlot, "linked", "variant",
			editor.meshAttachmentSources().first(), true);
		assertTrue(linked != null, "editor creates an exportable Linked Mesh");
		AnimationEditor animationEditor = new AnimationEditor(doc);
		Animation deformAnimation = animationEditor.addAnimation("linked-deform");
		AnimationEditor.DeformTarget deformTarget = animationEditor.resolveDeformTarget(
			targetSlot.getIndex(), linked);
		assertTrue(deformTarget.slotIndex() == sourceSlot.getIndex() && deformTarget.attachment() == source,
			"inherited Linked Mesh Deform edits resolve to the source Mesh slot");
		float[] offsets = {1f, 0f, 0f, 2f, -1f, 0f};
		animationEditor.addDeformKeyframe(deformAnimation, deformTarget.slotIndex(),
			deformTarget.attachment(), 0.5f, offsets);

		String json = SkeletonExporter.toJson(doc);
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		MeshAttachment loadedSource = (MeshAttachment)loaded.findSkin("default").getAttachment(0, "source");
		MeshAttachment loadedLinked = (MeshAttachment)loaded.findSkin("linked").getAttachment(1, "linked");
		assertTrue(loadedLinked.getSourceMesh() == loadedSource,
			"editor-created Linked Mesh source survives JSON round trip");
		assertTrue(loadedLinked.getTimelineAttachment() == loadedSource,
			"editor-created Linked Mesh Deform inheritance survives JSON round trip");
		assertTrue("variant".equals(loadedLinked.getPath()),
			"editor-created Linked Mesh texture path survives JSON round trip");
		assertTrue(java.util.Arrays.equals(new int[] {1}, loadedSource.getTimelineSlots()),
			"cross-slot timeline activation is reconstructed after JSON round trip");
		Animation loadedDeformAnimation = loaded.findAnimation("linked-deform");
		Animation.DeformTimeline loadedDeform = findDeformTimeline(loadedDeformAnimation, loadedSource);
		assertTrue(loadedDeform != null && loadedDeform.getSlotIndex() == sourceSlot.getIndex(),
			"Linked Mesh viewport Deform is stored on the source slot and remains addressable after reload");
		assertFloatArray(new float[] {-4f, -5f, 5f, -3f, -1f, 5f}, loadedDeform.getVertices()[0],
			"cross-slot Linked Mesh Deform values survive JSON round trip");
	}

	private static void roundTripsIndependentLinkedMeshDeformAfterSourceWeightEdit() {
		SkeletonData data = baseData("root", "child");
		SlotData sourceSlot = new SlotData(0, "source-slot", data.getBones().first());
		SlotData linkedSlot = new SlotData(1, "linked-slot", data.getBones().get(1));
		sourceSlot.setBlendMode(BlendMode.normal);
		linkedSlot.setBlendMode(BlendMode.normal);
		sourceSlot.setAttachmentName("source");
		linkedSlot.setAttachmentName("linked");
		data.getSlots().addAll(sourceSlot, linkedSlot);
		Skin defaultSkin = new Skin("default");
		Skin linkedSkin = new Skin("linked-skin");
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment source = new MeshAttachment("source", sequence);
		source.setPath("source");
		source.setWorldVerticesLength(6);
		source.setBones(new int[] {2, 0, 1, 2, 0, 1, 2, 0, 1});
		source.setVertices(new float[] {
			-5f, -5f, 0.25f, -5f, -5f, 0.75f,
			5f, -5f, 0.5f, 5f, -5f, 0.5f,
			0f, 5f, 0.75f, 0f, 5f, 0.25f
		});
		source.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 0.5f, 0f});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.setWidth(10f);
		source.setHeight(10f);
		source.updateSequence();
		defaultSkin.setAttachment(sourceSlot.getIndex(), "source", source);
		MeshAttachment linked = source.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		linkedSkin.setAttachment(linkedSlot.getIndex(), "linked", linked);
		data.getSkins().addAll(defaultSkin, linkedSkin);
		data.setDefaultSkin(defaultSkin);

		float[] offsets = {1f, -1f, 2f, -2f, 3f, -3f, 4f, -4f, 5f, -5f, 6f, -6f};
		Animation.DeformTimeline deform = new Animation.DeformTimeline(1, 0, linkedSlot.getIndex(), linked);
		deform.setFrame(0, 0.5f, offsets.clone());
		Animation animation = new Animation("independent-linked-deform");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(deform);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		SkeletonDocument doc = document(data);
		SkeletonEditor editor = new SkeletonEditor(doc);
		SkeletonEditor.MeshGeometryState before = SkeletonEditor.meshGeometryState(source);
		float[] editedWeights = before.vertices();
		editedWeights[2] = 0.4f;
		editedWeights[5] = 0.6f;
		SkeletonEditor.MeshGeometryState edited = new SkeletonEditor.MeshGeometryState(before.color(), before.path(),
			before.bones(), editedWeights, before.regionUVs(), before.triangles(), before.hullLength(), before.edges(),
			before.width(), before.height());
		assertTrue(editor.updateMeshGeometry(source, edited),
			"source weight values can change when the independent Linked Mesh Deform layout stays compatible");
		assertTrue(linked.getVertices() == source.getVertices() && linked.getBones() == source.getBones(),
			"compatible source weight edits refresh Linked Mesh shared geometry");
		assertTrue(editor.insertMeshVertexWithDeform(source, 0, 1f / 3f, 1f / 3f, 1f / 3f),
			"source topology can grow while an independent Linked Mesh Deform track exists");
		float[] migratedOffsets = {1f, -1f, 2f, -2f, 3f, -3f, 4f, -4f, 5f, -5f, 6f, -6f,
			3.424242f, -3.424242f, 3.481481f, -3.481481f};
		assertFloatArray(migratedOffsets, deform.getVertices()[0],
			"weighted Deform offsets migrate before JSON export");

		String json = SkeletonExporter.toJson(doc);
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		MeshAttachment loadedSource = (MeshAttachment)loaded.findSkin("default").getAttachment(0, "source");
		MeshAttachment loadedLinked = (MeshAttachment)loaded.findSkin("linked-skin").getAttachment(1, "linked");
		assertTrue(loadedLinked.getSourceMesh() == loadedSource && loadedLinked.getTimelineAttachment() == loadedLinked,
			"independent Linked Mesh Deform ownership survives source weight editing and JSON reload");
		assertTrue(loadedSource.getWorldVerticesLength() == 8 && loadedSource.getBones().length == 12,
			"inserted weighted vertex influence layout survives JSON reload");
		assertNear(0.4f, loadedSource.getVertices()[2], "edited source weight A");
		assertNear(0.6f, loadedSource.getVertices()[5], "edited source weight B");
		Animation.DeformTimeline loadedDeform = findDeformTimeline(
			loaded.findAnimation("independent-linked-deform"), loadedLinked);
		assertTrue(loadedDeform != null && loadedDeform.getSlotIndex() == linkedSlot.getIndex(),
			"independent Linked Mesh Deform remains attached to its own slot");
		assertFloatArray(migratedOffsets, loadedDeform.getVertices()[0],
			"migrated independent Linked Mesh Deform offsets survive source topology editing");
	}

	private static void preservesDeformOffsetsAfterSetupAttachmentEdits() {
		SkeletonData data = baseData("root", "child");
		SlotData sourceSlot = new SlotData(0, "source-slot", data.getBones().first());
		SlotData linkedSlot = new SlotData(1, "linked-slot", data.getBones().get(1));
		sourceSlot.setBlendMode(BlendMode.normal);
		linkedSlot.setBlendMode(BlendMode.normal);
		data.getSlots().addAll(sourceSlot, linkedSlot);
		Skin defaultSkin = new Skin("default");
		Skin linkedSkin = new Skin("linked-skin");
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment source = new MeshAttachment("source", sequence);
		source.setPath("source");
		source.setWorldVerticesLength(6);
		source.setVertices(new float[] {-5f, -5f, 5f, -5f, 0f, 5f});
		source.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 0.5f, 0f});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.setWidth(10f);
		source.setHeight(10f);
		source.updateSequence();
		defaultSkin.setAttachment(0, "source", source);
		MeshAttachment linked = source.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		linkedSkin.setAttachment(1, "linked", linked);
		BoundingBoxAttachment box = new BoundingBoxAttachment("box");
		box.setWorldVerticesLength(6);
		box.setVertices(new float[] {-4f, -3f, 4f, -3f, 0f, 6f});
		defaultSkin.setAttachment(0, "box", box);
		data.getSkins().addAll(defaultSkin, linkedSkin);
		data.setDefaultSkin(defaultSkin);

		float[] sourceOffsets = {1f, -2f, 3f, -4f, 5f, -6f};
		float[] linkedOffsets = {-6f, 5f, -4f, 3f, -2f, 1f};
		float[] boxOffsets = {0.5f, -1f, 1.5f, -2f, 2.5f, -3f};
		float[] sourceStored = source.getVertices().clone();
		float[] linkedStored = source.getVertices().clone();
		float[] boxStored = box.getVertices().clone();
		for (int value = 0; value < sourceStored.length; value++) {
			sourceStored[value] += sourceOffsets[value];
			linkedStored[value] += linkedOffsets[value];
			boxStored[value] += boxOffsets[value];
		}
		Animation.DeformTimeline sourceDeform = new Animation.DeformTimeline(1, 0, 0, source);
		sourceDeform.setFrame(0, 0.25f, sourceStored);
		Animation.DeformTimeline linkedDeform = new Animation.DeformTimeline(1, 0, 1, linked);
		linkedDeform.setFrame(0, 0.5f, linkedStored);
		Animation.DeformTimeline boxDeform = new Animation.DeformTimeline(1, 0, 0, box);
		boxDeform.setFrame(0, 0.75f, boxStored);
		Animation animation = new Animation("setup-rebase");
		Array<Animation.Timeline> timelines = new Array<>(true, 3, Animation.Timeline[]::new);
		timelines.add(sourceDeform);
		timelines.add(linkedDeform);
		timelines.add(boxDeform);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		SkeletonDocument doc = document(data);
		SkeletonEditor editor = new SkeletonEditor(doc);
		SkeletonEditor.MeshGeometryState meshBefore = SkeletonEditor.meshGeometryState(source);
		float[] movedMeshVertices = meshBefore.vertices();
		for (int value = 0; value < movedMeshVertices.length; value++) movedMeshVertices[value] += value + 1f;
		SkeletonEditor.MeshGeometryState movedMesh = new SkeletonEditor.MeshGeometryState(meshBefore.color(),
			meshBefore.path(), null, movedMeshVertices, meshBefore.regionUVs(), meshBefore.triangles(),
			meshBefore.hullLength(), meshBefore.edges(), meshBefore.width(), meshBefore.height());
		assertTrue(editor.updateMeshGeometry(source, movedMesh),
			"setup Mesh geometry edit rebases all source and independent Linked Mesh Deform frames");
		SkeletonEditor.StandardAttachmentState boxBefore = SkeletonEditor.standardAttachmentState(box);
		float[] movedBoxVertices = boxBefore.vertices();
		for (int value = 0; value < movedBoxVertices.length; value++) movedBoxVertices[value] -= value + 0.5f;
		SkeletonEditor.StandardAttachmentState movedBox = new SkeletonEditor.StandardAttachmentState(
			SkeletonEditor.StandardAttachmentType.BOUNDING_BOX, boxBefore.color(), 3, null, movedBoxVertices,
			false, false, null, 0f, 0f, 0f, null, false, false);
		assertTrue(editor.updateStandardAttachment(box, movedBox),
			"setup Bounding Box geometry edit rebases its Deform frame");

		String json = SkeletonExporter.toJson(doc);
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		MeshAttachment loadedSource = (MeshAttachment)loaded.findSkin("default").getAttachment(0, "source");
		MeshAttachment loadedLinked = (MeshAttachment)loaded.findSkin("linked-skin").getAttachment(1, "linked");
		BoundingBoxAttachment loadedBox = (BoundingBoxAttachment)loaded.findSkin("default").getAttachment(0, "box");
		Animation loadedAnimation = loaded.findAnimation("setup-rebase");
		Animation.DeformTimeline loadedSourceDeform = findDeformTimeline(loadedAnimation, loadedSource);
		Animation.DeformTimeline loadedLinkedDeform = findDeformTimeline(loadedAnimation, loadedLinked);
		Animation.DeformTimeline loadedBoxDeform = findDeformTimeline(loadedAnimation, loadedBox);
		assertTrue(loadedSourceDeform != null && loadedLinkedDeform != null && loadedBoxDeform != null,
			"all rebased Deform tracks remain addressable after official JSON reload");
		float[] expectedSourceStored = loadedSource.getVertices().clone();
		float[] expectedLinkedStored = loadedSource.getVertices().clone();
		float[] expectedBoxStored = loadedBox.getVertices().clone();
		for (int value = 0; value < expectedSourceStored.length; value++) {
			expectedSourceStored[value] += sourceOffsets[value];
			expectedLinkedStored[value] += linkedOffsets[value];
			expectedBoxStored[value] += boxOffsets[value];
		}
		assertFloatArray(expectedSourceStored, loadedSourceDeform.getVertices()[0],
			"source Mesh Deform offsets remain unchanged through setup edit and JSON round trip");
		assertFloatArray(expectedLinkedStored, loadedLinkedDeform.getVertices()[0],
			"independent Linked Mesh Deform offsets remain unchanged through setup edit and JSON round trip");
		assertFloatArray(expectedBoxStored, loadedBoxDeform.getVertices()[0],
			"Bounding Box Deform offsets remain unchanged through setup edit and JSON round trip");
	}

	private static void roundTripsWeightedDeformAfterInfluenceReorder() {
		SkeletonData data = baseData("root", "child");
		BoneData third = new BoneData(2, "third", data.getBones().first());
		third.getSetupPose().setInherit(Inherit.normal);
		data.getBones().add(third);
		SlotData sourceSlot = new SlotData(0, "source-slot", data.getBones().first());
		SlotData linkedSlot = new SlotData(1, "linked-slot", data.getBones().get(1));
		sourceSlot.setBlendMode(BlendMode.normal);
		linkedSlot.setBlendMode(BlendMode.normal);
		data.getSlots().addAll(sourceSlot, linkedSlot);
		Skin defaultSkin = new Skin("default");
		Skin linkedSkin = new Skin("linked-skin");
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		int[] oldBones = {2, 0, 1, 2, 1, 0, 1, 1};
		float[] oldVertices = {0f, 0f, 0.25f, 1f, 1f, 0.75f,
			2f, 2f, 0.4f, 3f, 3f, 0.6f, 4f, 4f, 1f};
		int[] reorderedBones = {2, 1, 0, 2, 0, 1, 1, 1};
		float[] reorderedVertices = {1f, 1f, 0.75f, 0f, 0f, 0.25f,
			3f, 3f, 0.6f, 2f, 2f, 0.4f, 4f, 4f, 1f};
		MeshAttachment source = new MeshAttachment("weighted", sequence);
		source.setPath("weighted");
		source.setWorldVerticesLength(6);
		source.setBones(oldBones);
		source.setVertices(oldVertices);
		source.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0.5f, 1f});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.setWidth(10f);
		source.setHeight(10f);
		source.updateSequence();
		defaultSkin.setAttachment(0, "weighted", source);
		MeshAttachment linked = source.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		linkedSkin.setAttachment(1, "linked", linked);
		data.getSkins().addAll(defaultSkin, linkedSkin);
		data.setDefaultSkin(defaultSkin);

		Animation.DeformTimeline sourceDeform = new Animation.DeformTimeline(1, 0, 0, source);
		sourceDeform.setFrame(0, 0.25f, new float[] {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f});
		Animation.DeformTimeline linkedDeform = new Animation.DeformTimeline(1, 0, 1, linked);
		linkedDeform.setFrame(0, 0.5f, new float[] {11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f, 20f});
		Animation animation = new Animation("weighted-order");
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(sourceDeform);
		timelines.add(linkedDeform);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		SkeletonDocument doc = document(data);
		SkeletonEditor editor = new SkeletonEditor(doc);
		SkeletonEditor.MeshGeometryState before = SkeletonEditor.meshGeometryState(source);
		SkeletonEditor.MeshGeometryState reordered = new SkeletonEditor.MeshGeometryState(before.color(),
			before.path(), reorderedBones, reorderedVertices, before.regionUVs(), before.triangles(),
			before.hullLength(), before.edges(), before.width(), before.height());
		assertTrue(editor.updateMeshGeometry(source, reordered),
			"weighted influence order is migrated before export");
		float[] expectedSource = {3f, 4f, 1f, 2f, 7f, 8f, 5f, 6f, 9f, 10f};
		float[] expectedLinked = {13f, 14f, 11f, 12f, 17f, 18f, 15f, 16f, 19f, 20f};
		assertFloatArray(expectedSource, sourceDeform.getVertices()[0],
			"source weighted Deform is remapped before export");
		assertFloatArray(expectedLinked, linkedDeform.getVertices()[0],
			"independent Linked Mesh weighted Deform is remapped before export");

		String json = SkeletonExporter.toJson(doc);
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		MeshAttachment loadedSource = (MeshAttachment)loaded.findSkin("default").getAttachment(0, "weighted");
		MeshAttachment loadedLinked = (MeshAttachment)loaded.findSkin("linked-skin").getAttachment(1, "linked");
		assertIntArray(reorderedBones, loadedSource.getBones(),
			"reordered weighted influence encoding survives official JSON reload");
		assertTrue(loadedLinked.getSourceMesh() == loadedSource && loadedLinked.getTimelineAttachment() == loadedLinked,
			"independent Linked Mesh ownership survives weighted influence reordering");
		Animation loadedAnimation = loaded.findAnimation("weighted-order");
		Animation.DeformTimeline loadedSourceDeform = findDeformTimeline(loadedAnimation, loadedSource);
		Animation.DeformTimeline loadedLinkedDeform = findDeformTimeline(loadedAnimation, loadedLinked);
		assertTrue(loadedSourceDeform != null && loadedLinkedDeform != null,
			"both reordered weighted Deform tracks remain addressable after reload");
		assertFloatArray(expectedSource, loadedSourceDeform.getVertices()[0],
			"source weighted Deform bone mapping survives JSON round trip");
		assertFloatArray(expectedLinked, loadedLinkedDeform.getVertices()[0],
			"Linked Mesh weighted Deform bone mapping survives JSON round trip");
	}

	private static void roundTripsLinkedMeshSourceChangeWithIndependentDeform() {
		SkeletonData data = baseData("root", "child");
		SlotData firstSlot = new SlotData(0, "first-slot", data.getBones().first());
		SlotData secondSlot = new SlotData(1, "second-slot", data.getBones().first());
		SlotData linkedSlot = new SlotData(2, "linked-slot", data.getBones().get(1));
		firstSlot.setBlendMode(BlendMode.normal);
		secondSlot.setBlendMode(BlendMode.normal);
		linkedSlot.setBlendMode(BlendMode.normal);
		data.getSlots().addAll(firstSlot, secondSlot, linkedSlot);
		Skin defaultSkin = new Skin("default");
		Skin linkedSkin = new Skin("linked-skin");
		TextureRegion firstTexture = new TextureRegion();
		TextureRegion secondTexture = new TextureRegion();
		TextureRegion variantTexture = new TextureRegion();
		Sequence firstSequence = new Sequence(1, false);
		Sequence secondSequence = new Sequence(1, false);
		firstSequence.getRegions()[0] = firstTexture;
		secondSequence.getRegions()[0] = secondTexture;
		MeshAttachment first = new MeshAttachment("first", firstSequence);
		first.setPath("first");
		first.setWorldVerticesLength(6);
		first.setVertices(new float[] {-5f, -5f, 5f, -5f, 0f, 5f});
		first.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 0.5f, 0f});
		first.setTriangles(new short[] {0, 1, 2});
		first.setHullLength(6);
		first.setWidth(10f);
		first.setHeight(10f);
		first.updateSequence();
		MeshAttachment second = new MeshAttachment("second", secondSequence);
		second.setPath("second");
		second.setWorldVerticesLength(6);
		second.setVertices(new float[] {20f, 10f, 40f, 10f, 30f, 35f});
		second.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 0.5f, 0f});
		second.setTriangles(new short[] {0, 1, 2});
		second.setHullLength(6);
		second.setWidth(20f);
		second.setHeight(25f);
		second.updateSequence();
		defaultSkin.setAttachment(0, "first", first);
		defaultSkin.setAttachment(1, "second", second);
		Sequence linkedSequence = new Sequence(1, false);
		linkedSequence.getRegions()[0] = variantTexture;
		MeshAttachment linked = new MeshAttachment("linked", linkedSequence);
		linked.setPath("variant");
		linked.setSourceMesh(first);
		linked.setTimelineAttachment(linked);
		linked.updateSequence();
		linkedSkin.setAttachment(2, "linked", linked);
		data.getSkins().addAll(defaultSkin, linkedSkin);
		data.setDefaultSkin(defaultSkin);

		float[] offsets = {1f, -2f, 3f, -4f, 5f, -6f};
		float[] stored = first.getVertices().clone();
		for (int value = 0; value < stored.length; value++) stored[value] += offsets[value];
		Animation.DeformTimeline deform = new Animation.DeformTimeline(1, 0, 2, linked);
		deform.setFrame(0, 0.5f, stored);
		setOnlyAnimation(data, "linked-source-change", deform);
		SkeletonDocument doc = document(data);
		SkeletonEditor editor = new SkeletonEditor(doc) {
			@Override protected TextureRegion findRegion(String name) {
				return switch (name) {
				case "first" -> firstTexture;
				case "second" -> secondTexture;
				case "variant" -> variantTexture;
				default -> null;
				};
			}
		};
		SkeletonEditor.MeshAttachmentSource secondRef = null;
		for (SkeletonEditor.MeshAttachmentSource source : editor.meshAttachmentSources())
			if (source.mesh() == second) secondRef = source;
		assertTrue(secondRef != null && editor.updateLinkedMeshAttachment(linkedSkin, linkedSlot, "linked",
			"variant", secondRef, false) == linked,
			"independent Linked Mesh source is changed through the managed editor path");
		float[] expectedStored = second.getVertices().clone();
		for (int value = 0; value < expectedStored.length; value++) expectedStored[value] += offsets[value];
		assertFloatArray(expectedStored, deform.getVertices()[0],
			"source change rebases independent Linked Mesh Deform before export");

		String json = SkeletonExporter.toJson(doc);
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		MeshAttachment loadedSecond = (MeshAttachment)loaded.findSkin("default").getAttachment(1, "second");
		MeshAttachment loadedLinked = (MeshAttachment)loaded.findSkin("linked-skin").getAttachment(2, "linked");
		assertTrue(loadedLinked.getSourceMesh() == loadedSecond && loadedLinked.getTimelineAttachment() == loadedLinked,
			"changed source and independent timeline ownership survive official JSON reload");
		Animation.DeformTimeline loadedDeform = findDeformTimeline(
			loaded.findAnimation("linked-source-change"), loadedLinked);
		assertTrue(loadedDeform != null && loadedDeform.getSlotIndex() == 2,
			"source-changed independent Deform remains attached to the Linked Mesh slot");
		assertFloatArray(expectedStored, loadedDeform.getVertices()[0],
			"rebased independent Deform offsets survive JSON round trip");

		linked.setTimelineAttachment(second);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"an inherited Linked Mesh cannot export a direct Deform track that will never activate");
		Animation.SequenceTimeline sequenceTimeline = new Animation.SequenceTimeline(1, 2, linked);
		sequenceTimeline.setFrame(0, 0f, SequenceMode.hold, 0, 0f);
		setOnlyAnimation(data, "invalid-linked-sequence", sequenceTimeline);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"an inherited Linked Mesh cannot export a direct Sequence track that will never activate");
	}

	private static void roundTripsSequenceIdentityReplacementWithMeshReferences() {
		SkeletonData data = baseData("root", "child");
		for (int index = 0; index < 3; index++) {
			SlotData slot = new SlotData(index, "sequence-identity-slot-" + index,
				data.getBones().get(index == 0 ? 0 : 1));
			slot.setBlendMode(BlendMode.normal);
			data.getSlots().add(slot);
		}
		Skin defaultSkin = new Skin("default");
		Skin variants = new Skin("sequence-variants");
		TextureRegion setupTexture = new TextureRegion();
		TextureRegion[] frames = {new TextureRegion(), new TextureRegion(), new TextureRegion()};
		Sequence sourceSequence = new Sequence(1, false);
		sourceSequence.getRegions()[0] = setupTexture;
		MeshAttachment source = new MeshAttachment("sequence-source", sourceSequence);
		source.setPath("setup");
		source.setWorldVerticesLength(6);
		source.setVertices(new float[] {-4f, -3f, 4f, -3f, 0f, 5f});
		source.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 0.5f, 0f});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.setTimelineSlots(new int[] {2});
		source.updateSequence();
		defaultSkin.setAttachment(0, "source", source);

		Sequence independentSequence = new Sequence(1, false);
		independentSequence.getRegions()[0] = setupTexture;
		MeshAttachment independent = new MeshAttachment("sequence-independent", independentSequence);
		independent.setPath("setup");
		independent.setSourceMesh(source);
		independent.setTimelineAttachment(independent);
		independent.updateSequence();
		variants.setAttachment(1, "independent", independent);
		Sequence inheritedSequence = new Sequence(1, false);
		inheritedSequence.getRegions()[0] = setupTexture;
		MeshAttachment inherited = new MeshAttachment("sequence-inherited", inheritedSequence);
		inherited.setPath("setup");
		inherited.setSourceMesh(source);
		inherited.setTimelineAttachment(source);
		inherited.updateSequence();
		variants.setAttachment(2, "inherited", inherited);
		data.getSkins().addAll(defaultSkin, variants);
		data.setDefaultSkin(defaultSkin);

		Animation.DeformTimeline sourceDeform = new Animation.DeformTimeline(1, 0, 0, source);
		float[] sourceStored = {-3f, -5f, 6f, -2f, 2f, 8f};
		sourceDeform.setFrame(0, 0.25f, sourceStored);
		Animation.SequenceTimeline sourceTimeline = new Animation.SequenceTimeline(1, 0, source);
		sourceTimeline.setFrame(0, 0.4f, SequenceMode.loop, 1, 0.15f);
		Animation.DeformTimeline independentDeform = new Animation.DeformTimeline(1, 0, 1, independent);
		float[] independentStored = {-5f, -2f, 3f, -1f, -1f, 7f};
		independentDeform.setFrame(0, 0.35f, independentStored);
		Animation.SequenceTimeline independentTimeline = new Animation.SequenceTimeline(1, 1, independent);
		independentTimeline.setFrame(0, 0.55f, SequenceMode.pingpong, 0, 0.2f);
		Animation animation = new Animation("sequence-identity-animation");
		Array<Animation.Timeline> timelines = new Array<>(true, 4, Animation.Timeline[]::new);
		timelines.add(sourceDeform, sourceTimeline, independentDeform, independentTimeline);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		SkeletonDocument doc = document(data);
		SkeletonEditor editor = new SkeletonEditor(doc) {
			@Override protected TextureRegion findRegion(String name) {
				if (name == null || !name.startsWith("walk")) return null;
				try {
					int frame = Integer.parseInt(name.substring(4));
					return frame >= 1 && frame <= frames.length ? frames[frame - 1] : null;
				} catch (NumberFormatException ignored) {
					return null;
				}
			}
		};
		MeshAttachment sourceReplacement = (MeshAttachment)editor.updateSequenceAttachment(source,
			new SkeletonEditor.SequenceAttachmentState("walk", 3, 1, 2, 1));
		assertTrue(sourceReplacement != null && independent.getSourceMesh() == sourceReplacement
			&& inherited.getTimelineAttachment() == sourceReplacement,
			"source Sequence replacement migrates Linked Mesh references before export");
		MeshAttachment independentReplacement = (MeshAttachment)editor.updateSequenceAttachment(independent,
			new SkeletonEditor.SequenceAttachmentState("walk", 2, 1, 2, 0));
		assertTrue(independentReplacement != null
			&& independentReplacement.getTimelineAttachment() == independentReplacement,
			"independent Linked Mesh remains self-owned after Sequence replacement");

		String json = SkeletonExporter.toJson(doc);
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		MeshAttachment loadedSource = (MeshAttachment)loaded.findSkin("default").getAttachment(0, "source");
		MeshAttachment loadedIndependent =
			(MeshAttachment)loaded.findSkin("sequence-variants").getAttachment(1, "independent");
		MeshAttachment loadedInherited =
			(MeshAttachment)loaded.findSkin("sequence-variants").getAttachment(2, "inherited");
		assertSequence(sourceReplacement.getSequence(), loadedSource.getSequence(),
			"source replacement Sequence round trip");
		assertSequence(independentReplacement.getSequence(), loadedIndependent.getSequence(),
			"independent replacement Sequence round trip");
		assertTrue(loadedIndependent.getSourceMesh() == loadedSource
			&& loadedIndependent.getTimelineAttachment() == loadedIndependent,
			"independent Linked Mesh source and timeline ownership survive official reload");
		assertTrue(loadedInherited.getSourceMesh() == loadedSource
			&& loadedInherited.getTimelineAttachment() == loadedSource,
			"inherited Linked Mesh source and timeline ownership survive official reload");
		Animation loadedAnimation = loaded.findAnimation(animation.getName());
		Animation.DeformTimeline loadedSourceDeform = findDeformTimeline(loadedAnimation, loadedSource);
		Animation.SequenceTimeline loadedSourceTimeline = findSequenceTimeline(loadedAnimation, loadedSource);
		Animation.DeformTimeline loadedIndependentDeform = findDeformTimeline(loadedAnimation, loadedIndependent);
		Animation.SequenceTimeline loadedIndependentTimeline = findSequenceTimeline(loadedAnimation, loadedIndependent);
		assertTrue(loadedSourceDeform != null && loadedSourceTimeline != null
			&& loadedIndependentDeform != null && loadedIndependentTimeline != null,
			"all attachment-specific timelines remain addressable after official reload");
		assertFloatArray(sourceStored, loadedSourceDeform.getVertices()[0],
			"source Deform survives Sequence identity replacement");
		assertFloatArray(independentStored, loadedIndependentDeform.getVertices()[0],
			"independent Deform survives Sequence identity replacement");
		assertFloatArray(sourceTimeline.getFrames(), loadedSourceTimeline.getFrames(),
			"source Sequence timeline survives identity replacement");
		assertFloatArray(independentTimeline.getFrames(), loadedIndependentTimeline.getFrames(),
			"independent Sequence timeline survives identity replacement");
	}

	private static void roundTripsDeformAndSequenceTimelines() {
		SkeletonData data = baseData("root", "child");
		for (int index = 0; index < 2; index++) {
			SlotData slot = new SlotData(index, "timeline-slot-" + index, data.getBones().get(index));
			slot.setBlendMode(BlendMode.normal);
			data.getSlots().add(slot);
		}

		Skin skin = new Skin("timeline-skin");
		RegionAttachment region = new RegionAttachment("region-sequence", sequence(4, 1, 2, 0));
		region.setPath("images/region-frame");
		region.setWidth(32f);
		region.setHeight(24f);
		skin.setAttachment(0, "region-sequence", region);

		MeshAttachment unweighted = new MeshAttachment("unweighted", sequence(3, 5, 2, 1));
		unweighted.setPath("images/unweighted-frame");
		unweighted.setWorldVerticesLength(8);
		unweighted.setVertices(new float[] {-3f, -2f, 3f, -2f, 3f, 2f, -3f, 2f});
		unweighted.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f});
		unweighted.setTriangles(new short[] {0, 1, 2, 2, 3, 0});
		unweighted.setHullLength(8);
		skin.setAttachment(0, "unweighted", unweighted);

		MeshAttachment weighted = new MeshAttachment("weighted", new Sequence(1, false));
		weighted.setPath("images/weighted");
		weighted.setWorldVerticesLength(4);
		weighted.setBones(new int[] {2, 0, 1, 1, 1});
		weighted.setVertices(new float[] {-2f, 1f, 0.25f, 3f, 4f, 0.75f, 6f, -5f, 1f});
		weighted.setRegionUVs(new float[] {0f, 0f, 1f, 1f});
		weighted.setTriangles(new short[] {0, 1, 0});
		weighted.setHullLength(4);
		skin.setAttachment(1, "weighted", weighted);
		data.getSkins().add(skin);

		Animation.DeformTimeline unweightedDeform = new Animation.DeformTimeline(3, 1, 0, unweighted);
		float[] unweightedSetup = unweighted.getVertices().clone();
		float[] unweightedSparse = {-3f, -2f, 3f, -2f, 5f, 4f, -3f, 2f};
		float[] unweightedFinal = {-1f, -2f, 3f, -2f, 3f, 2f, -3f, 2f};
		unweightedDeform.setFrame(0, 0f, unweightedSetup);
		unweightedDeform.setFrame(1, 0.75f, unweightedSparse);
		unweightedDeform.setFrame(2, 1.4f, unweightedFinal);
		unweightedDeform.setBezier(0, 0, 0, 0f, 0f, 0.18f, 0.12f, 0.61f, 0.86f, 0.75f, 1f);
		unweightedDeform.setStepped(1);

		Animation.DeformTimeline weightedDeform = new Animation.DeformTimeline(2, 0, 1, weighted);
		float[] weightedSetup = new float[6];
		float[] weightedChanged = {0f, 1.5f, -2f, 0f, 0f, 0.5f};
		weightedDeform.setFrame(0, 0.1f, weightedSetup);
		weightedDeform.setFrame(1, 1.1f, weightedChanged);

		Animation.SequenceTimeline regionSequence = new Animation.SequenceTimeline(2, 0, region);
		regionSequence.setFrame(0, 0f, SequenceMode.hold, 1, 0f);
		regionSequence.setFrame(1, 0.5f, SequenceMode.loop, 0, 0.125f);
		Animation.SequenceTimeline meshSequence = new Animation.SequenceTimeline(2, 0, unweighted);
		meshSequence.setFrame(0, 0.2f, SequenceMode.onceReverse, 0, 0.2f);
		meshSequence.setFrame(1, 0.9f, SequenceMode.pingpongReverse, 1, 0.15f);

		Animation animation = new Animation("attachment-timelines");
		Array<Animation.Timeline> timelines = new Array<>(true, 4, Animation.Timeline[]::new);
		timelines.addAll(unweightedDeform, weightedDeform, regionSequence, meshSequence);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"attachments\": {"), "attachment animation section is exported");
		assertTrue(json.contains("\"offset\": 4, \"vertices\": [2, 2]"),
			"unweighted deform is stored as sparse setup offsets");
		assertTrue(json.contains("\"mode\": \"pingpongReverse\""), "sequence mode is exported");
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		Animation loadedAnimation = loaded.findAnimation(animation.getName());
		Skin loadedSkin = loaded.findSkin(skin.getName());
		MeshAttachment loadedUnweighted = (MeshAttachment)loadedSkin.getAttachment(0, "unweighted");
		MeshAttachment loadedWeighted = (MeshAttachment)loadedSkin.getAttachment(1, "weighted");
		RegionAttachment loadedRegion = (RegionAttachment)loadedSkin.getAttachment(0, "region-sequence");

		Animation.DeformTimeline loadedUnweightedDeform = findDeformTimeline(loadedAnimation, loadedUnweighted);
		Animation.DeformTimeline loadedWeightedDeform = findDeformTimeline(loadedAnimation, loadedWeighted);
		assertTrue(loadedUnweightedDeform != null && loadedWeightedDeform != null,
			"weighted and unweighted deform timelines round trip");
		assertFloatArray(unweightedSetup, loadedUnweightedDeform.getVertices()[0], "unweighted setup deform");
		assertFloatArray(unweightedSparse, loadedUnweightedDeform.getVertices()[1], "unweighted sparse deform");
		assertFloatArray(unweightedFinal, loadedUnweightedDeform.getVertices()[2], "unweighted final deform");
		assertFloatArray(weightedSetup, loadedWeightedDeform.getVertices()[0], "weighted zero deform");
		assertFloatArray(weightedChanged, loadedWeightedDeform.getVertices()[1], "weighted changed deform");
		assertDeformCurveSamples(unweightedDeform, loadedUnweightedDeform, "deform Bezier");
		assertTrue(loadedUnweightedDeform.getCurveType(1) == Animation.CurveTimeline.STEPPED,
			"deform stepped curve round trip");

		Animation.SequenceTimeline loadedRegionSequence = findSequenceTimeline(loadedAnimation, loadedRegion);
		Animation.SequenceTimeline loadedMeshSequence = findSequenceTimeline(loadedAnimation, loadedUnweighted);
		assertTrue(loadedRegionSequence != null && loadedMeshSequence != null,
			"region and mesh sequence timelines round trip");
		assertFloatArray(regionSequence.getFrames(), loadedRegionSequence.getFrames(), "region sequence frames");
		assertFloatArray(meshSequence.getFrames(), loadedMeshSequence.getFrames(), "mesh sequence frames");

		Animation.DeformTimeline invalidLength = new Animation.DeformTimeline(1, 0, 0, unweighted);
		invalidLength.setFrame(0, 0f, new float[] {1f, 2f});
		timelines.add(invalidLength);
		animation.setTimelines(timelines, new IntArray());
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(document(data)),
			"deform vertex length must match its attachment");
		timelines.pop();
		animation.setTimelines(timelines, new IntArray());
		float validMode = regionSequence.getFrames()[1];
		regionSequence.getFrames()[1] = 15f;
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(document(data)),
			"invalid sequence mode is rejected");
		regionSequence.getFrames()[1] = validMode;

		Skin inheritedSkin = new Skin("inherited-region-timeline");
		inheritedSkin.setAttachment(0, "region-copy", region.copy());
		data.getSkins().add(inheritedSkin);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(document(data)),
			"non-mesh attachment timeline inheritance cannot be silently flattened");
		data.getSkins().removeValue(inheritedSkin, true);
	}

	private static void roundTripsIkConstraints() {
		SkeletonData data = baseData("root", "arm");
		IkConstraintData ik = new IkConstraintData("arm-ik");
		ik.getBones().add(data.getBones().get(1));
		ik.setTarget(data.getBones().first());
		ik.setScaleYMode(ScaleYMode.volume);
		ik.setSkinRequired(true);
		ik.getSetupPose().setMix(0.75f);
		ik.getSetupPose().setSoftness(3f);
		ik.getSetupPose().setBendDirection(-1);
		ik.getSetupPose().setCompress(true);
		ik.getSetupPose().setStretch(true);
		data.getConstraints().add(ik);

		Skin skin = new Skin("ik-skin");
		skin.getBones().add(data.getBones().get(1));
		skin.getConstraints().add(ik);
		data.getSkins().add(skin);

		Animation.IkConstraintTimeline timeline = new Animation.IkConstraintTimeline(2, 2, 0);
		timeline.setFrame(0, 0f, 0.2f, 1f, 1, false, true);
		timeline.setFrame(1, 1.2f, 0.9f, 5f, -1, true, false);
		setBezierChannels(timeline, 1.2f, new float[] {0.2f, 1f}, new float[] {0.9f, 5f});
		Animation animation = new Animation("ik-animation");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(timeline);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"constraints\": ["), "IK setup constraint is exported");
		assertTrue(json.contains("\"type\": \"ik\""), "IK constraint type is exported");
		assertTrue(json.contains("\"ik\": [\"arm-ik\"]"), "skin IK activation is exported");
		assertTrue(json.contains("\"ik\": {"), "IK animation section is exported");

		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		IkConstraintData loadedIk = loaded.findConstraint("arm-ik", IkConstraintData.class);
		assertTrue(loadedIk != null, "IK constraint round trip");
		assertTrue(loadedIk.getBones().size == 1 && "arm".equals(loadedIk.getBones().first().getName()),
			"IK constrained bone round trip");
		assertTrue("root".equals(loadedIk.getTarget().getName()), "IK target round trip");
		assertTrue(loadedIk.getScaleYMode() == ScaleYMode.volume, "IK scale Y mode round trip");
		assertTrue(loadedIk.getSkinRequired(), "IK skin-required flag round trip");
		assertNear(0.75f, loadedIk.getSetupPose().getMix(), "IK setup mix");
		assertNear(3f, loadedIk.getSetupPose().getSoftness(), "IK setup softness");
		assertTrue(loadedIk.getSetupPose().getBendDirection() == -1
			&& loadedIk.getSetupPose().getCompress() && loadedIk.getSetupPose().getStretch(),
			"IK setup discrete values round trip");
		Skin loadedSkin = loaded.findSkin("ik-skin");
		assertTrue(loadedSkin != null && loadedSkin.getBones().contains(loaded.getBones().get(1), true),
			"skin bone activation round trip");
		assertTrue(loadedSkin.getConstraints().contains(loadedIk, true), "skin IK activation round trip");

		Animation.IkConstraintTimeline loadedTimeline = findTimeline(loaded.findAnimation("ik-animation"),
			Animation.IkConstraintTimeline.class);
		assertTrue(loadedTimeline != null && loadedTimeline.getConstraintIndex() == 0,
			"IK timeline target round trip");
		assertNear(0.9f, loadedTimeline.getFrames()[7], "IK key mix round trip");
		assertNear(5f, loadedTimeline.getFrames()[8], "IK key softness round trip");
		assertTrue(loadedTimeline.getFrames()[9] == -1f && loadedTimeline.getFrames()[10] == 1f
			&& loadedTimeline.getFrames()[11] == 0f, "IK key discrete values round trip");
		assertCurveSamples(timeline, loadedTimeline, 2, "IK Bezier");
	}

	private static void roundTripsTransformConstraints() {
		SkeletonData data = baseData("root", "arm");
		TransformConstraintData transform = new TransformConstraintData("arm-transform");
		transform.getBones().add(data.getBones().get(1));
		transform.setSource(data.getBones().first());
		transform.setSkinRequired(true);
		transform.setLocalSource(true);
		transform.setLocalTarget(true);
		transform.setAdditive(true);
		transform.setClamp(true);
		transform.setOffsetRotation(11f);
		transform.setOffsetX(2f);
		transform.setOffsetY(-3f);
		transform.setOffsetScaleX(0.2f);
		transform.setOffsetScaleY(-0.1f);
		transform.setOffsetShearY(7f);

		FromRotate rotate = new FromRotate();
		rotate.offset = 4f;
		ToX rotateToX = new ToX();
		rotateToX.offset = 5f;
		rotateToX.max = 9f;
		rotateToX.scale = 0.5f;
		rotate.to.add(rotateToX);
		ToShearY rotateToShearY = new ToShearY();
		rotateToShearY.offset = -6f;
		rotateToShearY.max = 12f;
		rotateToShearY.scale = -0.25f;
		rotate.to.add(rotateToShearY);
		transform.getProperties().add(rotate);
		FromX x = new FromX();
		x.to.add(defaultTo(new ToRotate()));
		transform.getProperties().add(x);
		FromY y = new FromY();
		y.to.add(defaultTo(new ToY()));
		transform.getProperties().add(y);
		FromScaleX scaleX = new FromScaleX();
		scaleX.to.add(defaultTo(new ToScaleX()));
		transform.getProperties().add(scaleX);
		FromScaleY scaleY = new FromScaleY();
		scaleY.to.add(defaultTo(new ToScaleY()));
		transform.getProperties().add(scaleY);
		FromShearY shearY = new FromShearY();
		shearY.to.add(defaultTo(new ToShearY()));
		transform.getProperties().add(shearY);

		transform.getSetupPose().setMixRotate(0.25f);
		transform.getSetupPose().setMixX(0.4f);
		transform.getSetupPose().setMixY(0.55f);
		transform.getSetupPose().setMixScaleX(0.65f);
		transform.getSetupPose().setMixScaleY(0.75f);
		transform.getSetupPose().setMixShearY(0.85f);
		data.getConstraints().add(transform);

		Skin skin = new Skin("transform-skin");
		skin.getConstraints().add(transform);
		data.getSkins().add(skin);

		Animation.TransformConstraintTimeline timeline = new Animation.TransformConstraintTimeline(2, 6, 0);
		float[] first = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f};
		float[] second = {0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f};
		timeline.setFrame(0, 0f, first[0], first[1], first[2], first[3], first[4], first[5]);
		timeline.setFrame(1, 1.2f, second[0], second[1], second[2], second[3], second[4], second[5]);
		setBezierChannels(timeline, 1.2f, first, second);
		Animation animation = new Animation("transform-animation");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(timeline);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"type\": \"transform\""), "Transform setup is exported");
		assertTrue(json.contains("\"transform\": [\"arm-transform\"]"),
			"skin Transform activation is exported in its own section");
		assertTrue(json.contains("\"properties\": {") && json.contains("\"to\": {"),
			"Transform property mappings are exported");

		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		TransformConstraintData loadedTransform = loaded.findConstraint("arm-transform", TransformConstraintData.class);
		assertTrue(loadedTransform != null, "Transform constraint round trip");
		assertTrue(loadedTransform.getBones().size == 1
			&& "arm".equals(loadedTransform.getBones().first().getName()), "Transform constrained bone");
		assertTrue("root".equals(loadedTransform.getSource().getName()), "Transform source bone");
		assertTrue(loadedTransform.getLocalSource() && loadedTransform.getLocalTarget()
			&& loadedTransform.getAdditive() && loadedTransform.getClamp(), "Transform mode flags");
		assertNear(11f, loadedTransform.getOffsetRotation(), "Transform rotation offset");
		assertNear(2f, loadedTransform.getOffsetX(), "Transform X offset");
		assertNear(-3f, loadedTransform.getOffsetY(), "Transform Y offset");
		assertNear(0.2f, loadedTransform.getOffsetScaleX(), "Transform scale X offset");
		assertNear(-0.1f, loadedTransform.getOffsetScaleY(), "Transform scale Y offset");
		assertNear(7f, loadedTransform.getOffsetShearY(), "Transform shear Y offset");
		assertTrue(loadedTransform.getProperties().size == 6, "all Transform source mappings round trip");
		assertTrue(loadedTransform.getProperties().first() instanceof FromRotate
			&& loadedTransform.getProperties().first().to.first() instanceof ToX,
			"cross-property Transform mapping round trip");
		assertTrue(loadedTransform.getProperties().first().to.size == 2
			&& loadedTransform.getProperties().first().to.get(1) instanceof ToShearY,
			"multiple targets for one Transform source round trip");
		assertNear(4f, loadedTransform.getProperties().first().offset, "Transform source property offset");
		assertNear(5f, loadedTransform.getProperties().first().to.first().offset, "Transform target offset");
		assertNear(9f, loadedTransform.getProperties().first().to.first().max, "Transform target max");
		assertNear(0.5f, loadedTransform.getProperties().first().to.first().scale, "Transform target scale");
		assertNear(-6f, loadedTransform.getProperties().first().to.get(1).offset,
			"second Transform target offset");
		assertNear(12f, loadedTransform.getProperties().first().to.get(1).max,
			"second Transform target max");
		assertNear(-0.25f, loadedTransform.getProperties().first().to.get(1).scale,
			"second Transform target scale");
		assertNear(0.25f, loadedTransform.getSetupPose().getMixRotate(), "Transform setup rotate mix");
		assertNear(0.4f, loadedTransform.getSetupPose().getMixX(), "Transform setup X mix");
		assertNear(0.55f, loadedTransform.getSetupPose().getMixY(), "Transform setup Y mix");
		assertNear(0.65f, loadedTransform.getSetupPose().getMixScaleX(), "Transform setup scale X mix");
		assertNear(0.75f, loadedTransform.getSetupPose().getMixScaleY(), "Transform setup scale Y mix");
		assertNear(0.85f, loadedTransform.getSetupPose().getMixShearY(), "Transform setup shear Y mix");
		assertTrue(loaded.findSkin("transform-skin").getConstraints().contains(loadedTransform, true),
			"skin Transform activation round trip");

		Animation.TransformConstraintTimeline loadedTimeline = findTimeline(
			loaded.findAnimation("transform-animation"), Animation.TransformConstraintTimeline.class);
		assertTrue(loadedTimeline != null && loadedTimeline.getConstraintIndex() == 0,
			"Transform timeline target round trip");
		for (int channel = 0; channel < 6; channel++)
			assertNear(second[channel], loadedTimeline.getFrames()[8 + channel],
				"Transform timeline channel " + channel);
		assertCurveSamples(timeline, loadedTimeline, 6, "Transform Bezier");
	}

	private static <T extends TransformConstraintData.ToProperty> T defaultTo(T property) {
		property.max = 1f;
		property.scale = 1f;
		return property;
	}

	private static void roundTripsSliderConstraints() {
		SkeletonData data = baseData("root", "slider-bone");
		Animation driven = new Animation("slider-driven");
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(2, 0, 1);
		rotate.setFrame(0, 0f, 0f);
		rotate.setFrame(1, 1f, 45f);
		Array<Animation.Timeline> drivenTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		drivenTimelines.add(rotate);
		IntArray drivenBones = new IntArray();
		drivenBones.add(1);
		driven.setTimelines(drivenTimelines, drivenBones);
		driven.setDuration(1f);
		data.getAnimations().add(driven);

		SliderData propertySlider = new SliderData("property-slider");
		propertySlider.setAnimation(driven);
		propertySlider.setSkinRequired(true);
		propertySlider.setAdditive(true);
		propertySlider.setLoop(true);
		propertySlider.setBone(data.getBones().get(1));
		FromX property = new FromX();
		property.offset = -5f;
		propertySlider.setProperty(property);
		propertySlider.setOffset(2f);
		propertySlider.setScale(0.25f);
		propertySlider.setMax(120f);
		propertySlider.setLocal(true);
		propertySlider.getSetupPose().setMix(0.6f);
		data.getConstraints().add(propertySlider);

		SliderData timeSlider = new SliderData("time-slider");
		timeSlider.setAnimation(driven);
		timeSlider.getSetupPose().setTime(0.35f);
		timeSlider.getSetupPose().setMix(0.8f);
		data.getConstraints().add(timeSlider);

		Skin skin = new Skin("slider-skin");
		skin.getConstraints().add(propertySlider);
		data.getSkins().add(skin);

		Animation.SliderTimeline time = new Animation.SliderTimeline(2, 1, 0);
		time.setFrame(0, 0f, 0.1f);
		time.setFrame(1, 1f, 0.9f);
		setBezierChannels(time, 1f, new float[] {0.1f}, new float[] {0.9f});
		Animation.SliderMixTimeline mix = new Animation.SliderMixTimeline(2, 0, 0);
		mix.setFrame(0, 0f, 0.2f);
		mix.setFrame(1, 1f, 0.75f);
		mix.setStepped(0);
		Animation controller = new Animation("slider-controller");
		Array<Animation.Timeline> controllerTimelines = new Array<>(true, 2, Animation.Timeline[]::new);
		controllerTimelines.add(time, mix);
		controller.setTimelines(controllerTimelines, new IntArray());
		data.getAnimations().add(controller);

		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"type\": \"slider\"") && json.contains("\"slider\": [\"property-slider\"]"),
			"Slider setup and skin activation are exported");
		assertTrue(json.contains("\"slider\": {") && json.contains("\"time\": [")
			&& json.contains("\"mix\": ["), "Slider timelines are exported");

		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		SliderData loadedProperty = loaded.findConstraint("property-slider", SliderData.class);
		assertTrue(loadedProperty != null && loadedProperty.getAnimation() == loaded.findAnimation("slider-driven"),
			"Slider animation reference round trip");
		assertTrue(loadedProperty.getSkinRequired() && loadedProperty.getAdditive() && loadedProperty.getLoop(),
			"Slider flags round trip");
		assertTrue("slider-bone".equals(loadedProperty.getBone().getName())
			&& loadedProperty.getProperty() instanceof FromX, "Slider bone property round trip");
		assertNear(-5f, loadedProperty.getProperty().offset, "Slider source offset");
		assertNear(2f, loadedProperty.getOffset(), "Slider target offset");
		assertNear(0.25f, loadedProperty.getScale(), "Slider scale");
		assertNear(120f, loadedProperty.getMax(), "Slider maximum");
		assertTrue(loadedProperty.getLocal(), "Slider local flag");
		assertNear(0.6f, loadedProperty.getSetupPose().getMix(), "Slider setup mix");
		SliderData loadedTimeSlider = loaded.findConstraint("time-slider", SliderData.class);
		assertTrue(loadedTimeSlider != null && loadedTimeSlider.getBone() == null,
			"time-driven Slider mode round trip");
		assertNear(0.35f, loadedTimeSlider.getSetupPose().getTime(), "Slider setup time");
		assertTrue(loaded.findSkin("slider-skin").getConstraints().contains(loadedProperty, true),
			"Slider skin activation round trip");

		Animation loadedController = loaded.findAnimation("slider-controller");
		Animation.SliderTimeline loadedTime = findConstraintTimeline(
			loadedController, Animation.SliderTimeline.class, 0);
		Animation.SliderMixTimeline loadedMix = findConstraintTimeline(
			loadedController, Animation.SliderMixTimeline.class, 0);
		assertTrue(loadedTime != null && loadedMix != null, "Slider time and mix timelines round trip");
		assertNear(0.9f, loadedTime.getFrames()[3], "Slider time key value");
		assertNear(0.75f, loadedMix.getFrames()[3], "Slider mix key value");
		assertCurveSamples(time, loadedTime, "Slider time Bezier");
		assertTrue(loadedMix.getCurveType(0) == Animation.CurveTimeline.STEPPED,
			"Slider mix stepped curve round trip");
	}

	private static void roundTripsPathAndPhysicsConstraints() {
		SkeletonData data = baseData("root", "tip");
		SlotData pathSlot = new SlotData(0, "motion-path", data.getBones().first());
		pathSlot.setBlendMode(BlendMode.normal);
		data.getSlots().add(pathSlot);

		PathConstraintData path = new PathConstraintData("follow-path");
		path.getBones().add(data.getBones().get(1));
		path.setSlot(pathSlot);
		path.setSkinRequired(true);
		path.setPositionMode(PathConstraintData.PositionMode.fixed);
		path.setSpacingMode(PathConstraintData.SpacingMode.proportional);
		path.setRotateMode(PathConstraintData.RotateMode.chainScale);
		path.setOffsetRotation(17f);
		path.getSetupPose().setPosition(23f);
		path.getSetupPose().setSpacing(0.35f);
		path.getSetupPose().setMixRotate(0.65f);
		path.getSetupPose().setMixX(0.45f);
		path.getSetupPose().setMixY(0.25f);
		data.getConstraints().add(path);

		PhysicsConstraintData physics = new PhysicsConstraintData("swing-physics");
		physics.setBone(data.getBones().get(1));
		physics.setSkinRequired(true);
		physics.setX(0.2f);
		physics.setY(0.3f);
		physics.setRotate(0.4f);
		physics.setScaleX(0.5f);
		physics.setScaleYMode(ScaleYMode.volume);
		physics.setShearX(0.6f);
		physics.setLimit(4321f);
		physics.setStep(1f / 75f);
		physics.getSetupPose().setInertia(0.35f);
		physics.getSetupPose().setStrength(84f);
		physics.getSetupPose().setDamping(0.72f);
		physics.getSetupPose().setMassInverse(0.4f);
		physics.getSetupPose().setWind(-3f);
		physics.getSetupPose().setGravity(6.5f);
		physics.getSetupPose().setMix(0.8f);
		physics.setInertiaGlobal(true);
		physics.setDampingGlobal(true);
		physics.setMassGlobal(true);
		physics.setGravityGlobal(true);
		data.getConstraints().add(physics);

		Skin skin = new Skin("constraint-skin");
		skin.getConstraints().add(path);
		skin.getConstraints().add(physics);
		data.getSkins().add(skin);

		Animation.PathConstraintPositionTimeline position =
			new Animation.PathConstraintPositionTimeline(2, 1, 0);
		position.setFrame(0, 0f, 4f);
		position.setFrame(1, 1.2f, 28f);
		setBezierChannels(position, 1.2f, new float[] {4f}, new float[] {28f});
		Animation.PathConstraintSpacingTimeline spacing =
			new Animation.PathConstraintSpacingTimeline(2, 0, 0);
		spacing.setFrame(0, 0.1f, 0.2f);
		spacing.setFrame(1, 1.1f, 0.8f);
		spacing.setStepped(0);
		Animation.PathConstraintMixTimeline pathMix = new Animation.PathConstraintMixTimeline(2, 3, 0);
		float[] pathMixStart = {0.1f, 0.3f, 0.5f};
		float[] pathMixEnd = {0.9f, 0.7f, 0.4f};
		pathMix.setFrame(0, 0f, pathMixStart[0], pathMixStart[1], pathMixStart[2]);
		pathMix.setFrame(1, 1.2f, pathMixEnd[0], pathMixEnd[1], pathMixEnd[2]);
		setBezierChannels(pathMix, 1.2f, pathMixStart, pathMixEnd);

		Animation.PhysicsConstraintInertiaTimeline inertia =
			new Animation.PhysicsConstraintInertiaTimeline(2, 1, 1);
		inertia.setFrame(0, 0f, 0.2f);
		inertia.setFrame(1, 1.2f, 0.85f);
		setBezierChannels(inertia, 1.2f, new float[] {0.2f}, new float[] {0.85f});
		Animation.PhysicsConstraintStrengthTimeline strength =
			new Animation.PhysicsConstraintStrengthTimeline(2, 0, 1);
		strength.setFrame(0, 0f, 60f);
		strength.setFrame(1, 1.2f, 120f);
		strength.setStepped(0);
		Animation.PhysicsConstraintDampingTimeline damping =
			new Animation.PhysicsConstraintDampingTimeline(2, 0, 1);
		damping.setFrame(0, 0f, 0.6f);
		damping.setFrame(1, 1.2f, 0.9f);
		Animation.PhysicsConstraintMassTimeline mass = new Animation.PhysicsConstraintMassTimeline(2, 0, 1);
		mass.setFrame(0, 0f, 2.5f);
		mass.setFrame(1, 1.2f, 3.25f);
		Animation.PhysicsConstraintWindTimeline wind = new Animation.PhysicsConstraintWindTimeline(2, 0, 1);
		wind.setFrame(0, 0f, -2f);
		wind.setFrame(1, 1.2f, 5f);
		Animation.PhysicsConstraintGravityTimeline gravity =
			new Animation.PhysicsConstraintGravityTimeline(2, 0, 1);
		gravity.setFrame(0, 0f, 7f);
		gravity.setFrame(1, 1.2f, -4f);
		Animation.PhysicsConstraintMixTimeline physicsMix =
			new Animation.PhysicsConstraintMixTimeline(2, 0, 1);
		physicsMix.setFrame(0, 0f, 0.25f);
		physicsMix.setFrame(1, 1.2f, 1f);
		Animation.PhysicsConstraintResetTimeline reset = new Animation.PhysicsConstraintResetTimeline(2, 1);
		reset.setFrame(0, 0.4f);
		reset.setFrame(1, 1.05f);
		Animation.PhysicsConstraintGravityTimeline globalGravity =
			new Animation.PhysicsConstraintGravityTimeline(2, 0, -1);
		globalGravity.setFrame(0, 0f, 1f);
		globalGravity.setFrame(1, 1.2f, 3f);
		Animation.PhysicsConstraintResetTimeline globalReset = new Animation.PhysicsConstraintResetTimeline(1, -1);
		globalReset.setFrame(0, 0.75f);

		Animation animation = new Animation("constraint-animation");
		Array<Animation.Timeline> timelines = new Array<>(true, 13, Animation.Timeline[]::new);
		timelines.addAll(position, spacing, pathMix, inertia, strength, damping, mass, wind, gravity,
			physicsMix, reset, globalGravity, globalReset);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		physics.setGravityGlobal(false);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(document(data)),
			"global Physics value timeline must have a constraint with the matching Global flag");
		physics.setGravityGlobal(true);
		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"type\": \"path\"") && json.contains("\"type\": \"physics\""),
			"Path and Physics setup constraints are exported");
		assertTrue(json.contains("\"path\": [\"follow-path\"]")
			&& json.contains("\"physics\": [\"swing-physics\"]"),
			"Path and Physics skin activation is exported");
		assertTrue(json.contains("\"path\": {") && json.contains("\"physics\": {")
			&& json.contains("\"\": {"), "Path, local Physics, and global Physics timelines are exported");

		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		PathConstraintData loadedPath = loaded.findConstraint("follow-path", PathConstraintData.class);
		assertTrue(loadedPath != null && loadedPath.getBones().size == 1
			&& "tip".equals(loadedPath.getBones().first().getName()), "Path constrained bones round trip");
		assertTrue("motion-path".equals(loadedPath.getSlot().getName()), "Path target slot round trip");
		assertTrue(loadedPath.getPositionMode() == PathConstraintData.PositionMode.fixed
			&& loadedPath.getSpacingMode() == PathConstraintData.SpacingMode.proportional
			&& loadedPath.getRotateMode() == PathConstraintData.RotateMode.chainScale, "Path modes round trip");
		assertNear(17f, loadedPath.getOffsetRotation(), "Path rotation offset");
		assertNear(23f, loadedPath.getSetupPose().getPosition(), "Path setup position");
		assertNear(0.35f, loadedPath.getSetupPose().getSpacing(), "Path setup spacing");
		assertNear(0.65f, loadedPath.getSetupPose().getMixRotate(), "Path setup rotate mix");
		assertNear(0.45f, loadedPath.getSetupPose().getMixX(), "Path setup X mix");
		assertNear(0.25f, loadedPath.getSetupPose().getMixY(), "Path setup Y mix");

		PhysicsConstraintData loadedPhysics = loaded.findConstraint("swing-physics", PhysicsConstraintData.class);
		assertTrue(loadedPhysics != null && "tip".equals(loadedPhysics.getBone().getName()),
			"Physics target bone round trip");
		assertNear(0.2f, loadedPhysics.getX(), "Physics X influence");
		assertNear(0.3f, loadedPhysics.getY(), "Physics Y influence");
		assertNear(0.4f, loadedPhysics.getRotate(), "Physics rotation influence");
		assertNear(0.5f, loadedPhysics.getScaleX(), "Physics scale X influence");
		assertTrue(loadedPhysics.getScaleYMode() == ScaleYMode.volume, "Physics scale Y mode");
		assertNear(0.6f, loadedPhysics.getShearX(), "Physics shear X influence");
		assertNear(4321f, loadedPhysics.getLimit(), "Physics movement limit");
		assertNear(1f / 75f, loadedPhysics.getStep(), "Physics step/FPS round trip");
		assertNear(0.35f, loadedPhysics.getSetupPose().getInertia(), "Physics setup inertia");
		assertNear(84f, loadedPhysics.getSetupPose().getStrength(), "Physics setup strength");
		assertNear(0.72f, loadedPhysics.getSetupPose().getDamping(), "Physics setup damping");
		assertNear(0.4f, loadedPhysics.getSetupPose().getMassInverse(), "Physics setup mass");
		assertNear(-3f, loadedPhysics.getSetupPose().getWind(), "Physics setup wind");
		assertNear(6.5f, loadedPhysics.getSetupPose().getGravity(), "Physics setup gravity");
		assertNear(0.8f, loadedPhysics.getSetupPose().getMix(), "Physics setup mix");
		assertTrue(loadedPhysics.getInertiaGlobal() && loadedPhysics.getDampingGlobal()
			&& loadedPhysics.getMassGlobal() && loadedPhysics.getGravityGlobal(), "Physics global flags round trip");
		assertTrue(loaded.findSkin("constraint-skin").getConstraints().contains(loadedPath, true)
			&& loaded.findSkin("constraint-skin").getConstraints().contains(loadedPhysics, true),
			"Path and Physics skin activation round trip");

		Animation loadedAnimation = loaded.findAnimation("constraint-animation");
		Animation.PathConstraintPositionTimeline loadedPosition = findConstraintTimeline(loadedAnimation,
			Animation.PathConstraintPositionTimeline.class, 0);
		Animation.PathConstraintSpacingTimeline loadedSpacing = findConstraintTimeline(loadedAnimation,
			Animation.PathConstraintSpacingTimeline.class, 0);
		Animation.PathConstraintMixTimeline loadedPathMix = findConstraintTimeline(loadedAnimation,
			Animation.PathConstraintMixTimeline.class, 0);
		assertTrue(loadedPosition != null && loadedSpacing != null && loadedPathMix != null,
			"all Path timeline properties round trip");
		assertCurveSamples(position, loadedPosition, 1, "Path position Bezier");
		assertTrue(loadedSpacing.getCurveType(0) == Animation.CurveTimeline.STEPPED,
			"Path spacing stepped curve round trip");
		assertCurveSamples(pathMix, loadedPathMix, 3, "Path mix Bezier");

		assertConstraintTimelineValue(loadedAnimation, Animation.PhysicsConstraintInertiaTimeline.class, 1,
			1, 0.85f, "Physics inertia timeline");
		assertConstraintTimelineValue(loadedAnimation, Animation.PhysicsConstraintStrengthTimeline.class, 1,
			1, 120f, "Physics strength timeline");
		assertConstraintTimelineValue(loadedAnimation, Animation.PhysicsConstraintDampingTimeline.class, 1,
			1, 0.9f, "Physics damping timeline");
		assertConstraintTimelineValue(loadedAnimation, Animation.PhysicsConstraintMassTimeline.class, 1,
			1, 3.25f, "Physics mass timeline");
		assertConstraintTimelineValue(loadedAnimation, Animation.PhysicsConstraintWindTimeline.class, 1,
			1, 5f, "Physics wind timeline");
		assertConstraintTimelineValue(loadedAnimation, Animation.PhysicsConstraintGravityTimeline.class, 1,
			1, -4f, "Physics gravity timeline");
		assertConstraintTimelineValue(loadedAnimation, Animation.PhysicsConstraintMixTimeline.class, 1,
			1, 1f, "Physics mix timeline");
		Animation.PhysicsConstraintInertiaTimeline loadedInertia = findConstraintTimeline(loadedAnimation,
			Animation.PhysicsConstraintInertiaTimeline.class, 1);
		assertCurveSamples(inertia, loadedInertia, 1, "Physics inertia Bezier");
		Animation.PhysicsConstraintResetTimeline loadedReset = findConstraintTimeline(loadedAnimation,
			Animation.PhysicsConstraintResetTimeline.class, 1);
		assertTrue(loadedReset != null && loadedReset.getFrameCount() == 2, "local Physics reset timeline");
		assertNear(1.05f, loadedReset.getFrames()[1], "local Physics reset time");
		assertConstraintTimelineValue(loadedAnimation, Animation.PhysicsConstraintGravityTimeline.class, -1,
			1, 3f, "global Physics gravity timeline");
		Animation.PhysicsConstraintResetTimeline loadedGlobalReset = findConstraintTimeline(loadedAnimation,
			Animation.PhysicsConstraintResetTimeline.class, -1);
		assertTrue(loadedGlobalReset != null, "global Physics reset timeline");
		assertNear(0.75f, loadedGlobalReset.getFrames()[0], "global Physics reset time");
	}

	private static void roundTripsEvents() {
		SkeletonData data = baseData("root", "child");
		EventData footstep = new EventData("foot\"step");
		footstep.getSetupPose().setInt(1);
		footstep.getSetupPose().setFloat(0.5f);
		footstep.getSetupPose().setString("left");
		footstep.setAudioPath("audio\\step\".wav");
		footstep.getSetupPose().setVolume(0.85f);
		footstep.getSetupPose().setBalance(-0.15f);
		data.getEvents().add(footstep);

		EventData marker = new EventData("marker");
		marker.getSetupPose().setFloat(2f);
		marker.getSetupPose().setString("");
		data.getEvents().add(marker);

		Event first = new Event(0.25f, footstep);
		first.setInt(2);
		first.setFloat(0.75f);
		first.setString("right\nside");
		first.setVolume(0.8f);
		first.setBalance(-0.2f);
		Event second = new Event(0.25f, marker);
		second.setInt(7);
		second.setFloat(-1.25f);
		second.setString("same-time");
		Event third = new Event(1.5f, footstep);
		third.setInt(footstep.getSetupPose().getInt());
		third.setFloat(footstep.getSetupPose().getFloat());
		third.setString(footstep.getSetupPose().getString());
		third.setVolume(footstep.getSetupPose().getVolume());
		third.setBalance(footstep.getSetupPose().getBalance());

		Animation.EventTimeline eventTimeline = new Animation.EventTimeline(3);
		eventTimeline.setFrame(0, first);
		eventTimeline.setFrame(1, second);
		eventTimeline.setFrame(2, third);
		Animation animation = new Animation("event-animation");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(eventTimeline);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"events\": {"), "event definitions are exported");
		assertTrue(json.contains("\"events\": ["), "event timeline is exported");
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		EventData loadedFootstep = loaded.findEvent(footstep.getName());
		assertTrue(loadedFootstep != null, "audio event definition");
		assertTrue(footstep.getAudioPath().equals(loadedFootstep.getAudioPath()), "event audio path");
		assertEventValues(footstep.getSetupPose(), loadedFootstep.getSetupPose(), "event setup values");
		EventData loadedMarker = loaded.findEvent(marker.getName());
		assertTrue(loadedMarker != null && loadedMarker.getAudioPath() == null, "non-audio event definition");
		assertEventValues(marker.getSetupPose(), loadedMarker.getSetupPose(), "marker setup values");

		Animation.EventTimeline loadedTimeline = findTimeline(loaded.findAnimation(animation.getName()),
			Animation.EventTimeline.class);
		assertTrue(loadedTimeline != null && loadedTimeline.getFrameCount() == 3, "event timeline frame count");
		assertEventValues(first, loadedTimeline.getEvents()[0], "first event key");
		assertEventValues(second, loadedTimeline.getEvents()[1], "same-time event key");
		assertEventValues(third, loadedTimeline.getEvents()[2], "default event key");
		assertNear(0.25f, loadedTimeline.getEvents()[0].getTime(), "first event time");
		assertNear(0.25f, loadedTimeline.getEvents()[1].getTime(), "same event time");
		assertNear(1.5f, loadedTimeline.getEvents()[2].getTime(), "last event time");
	}

	private static void roundTripsBezierCurves() {
		SkeletonData data = baseData("root", "child");
		SlotData rgbaSlot = addTwoColorSlot(data, "rgba-slot");
		SlotData rgba2Slot = addTwoColorSlot(data, "rgba2-slot");
		SlotData rgb2Slot = addTwoColorSlot(data, "rgb2-slot");

		Animation animation = new Animation("bezier-round-trip");
		Array<Animation.Timeline> timelines = new Array<>(true, 5, Animation.Timeline[]::new);
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(2, 1, 0);
		rotate.setFrame(0, 0f, 0f);
		rotate.setFrame(1, 1.2f, 30f);
		setBezierChannels(rotate, 1.2f, new float[] {0f}, new float[] {30f});
		timelines.add(rotate);

		Animation.TranslateTimeline translate = new Animation.TranslateTimeline(2, 2, 1);
		translate.setFrame(0, 0f, -4f, 8f);
		translate.setFrame(1, 1.2f, 12f, -6f);
		setBezierChannels(translate, 1.2f, new float[] {-4f, 8f}, new float[] {12f, -6f});
		timelines.add(translate);

		Animation.RGBATimeline rgba = new Animation.RGBATimeline(2, 4, rgbaSlot.getIndex());
		float[] rgbaStart = {1f, 0f, 1f, 1f};
		float[] rgbaEnd = {0f, 1f, 0f, 0f};
		rgba.setFrame(0, 0f, rgbaStart[0], rgbaStart[1], rgbaStart[2], rgbaStart[3]);
		rgba.setFrame(1, 1.2f, rgbaEnd[0], rgbaEnd[1], rgbaEnd[2], rgbaEnd[3]);
		setBezierChannels(rgba, 1.2f, rgbaStart, rgbaEnd);
		timelines.add(rgba);

		Animation.RGBA2Timeline rgba2 = new Animation.RGBA2Timeline(2, 7, rgba2Slot.getIndex());
		float[] rgba2Start = {1f, 0f, 1f, 1f, 0f, 1f, 0f};
		float[] rgba2End = {0f, 1f, 0f, 0f, 1f, 0f, 1f};
		rgba2.setFrame(0, 0f, rgba2Start[0], rgba2Start[1], rgba2Start[2], rgba2Start[3],
			rgba2Start[4], rgba2Start[5], rgba2Start[6]);
		rgba2.setFrame(1, 1.2f, rgba2End[0], rgba2End[1], rgba2End[2], rgba2End[3],
			rgba2End[4], rgba2End[5], rgba2End[6]);
		setBezierChannels(rgba2, 1.2f, rgba2Start, rgba2End);
		timelines.add(rgba2);

		Animation.RGB2Timeline rgb2 = new Animation.RGB2Timeline(2, 6, rgb2Slot.getIndex());
		float[] rgb2Start = {1f, 0f, 1f, 0f, 1f, 0f};
		float[] rgb2End = {0f, 1f, 0f, 1f, 0f, 1f};
		rgb2.setFrame(0, 0f, rgb2Start[0], rgb2Start[1], rgb2Start[2],
			rgb2Start[3], rgb2Start[4], rgb2Start[5]);
		rgb2.setFrame(1, 1.2f, rgb2End[0], rgb2End[1], rgb2End[2],
			rgb2End[3], rgb2End[4], rgb2End[5]);
		setBezierChannels(rgb2, 1.2f, rgb2Start, rgb2End);
		timelines.add(rgb2);

		IntArray bones = new IntArray();
		bones.add(0);
		bones.add(1);
		animation.setTimelines(timelines, bones);
		data.getAnimations().add(animation);

		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"curve\": ["), "Bezier controls are exported as numeric arrays");
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		Animation loadedAnimation = loaded.findAnimation(animation.getName());
		assertCurveSamples(rotate, findTimeline(loadedAnimation, Animation.RotateTimeline.class), "rotate Bezier");
		assertCurveSamples(translate, findTimeline(loadedAnimation, Animation.TranslateTimeline.class), "translate Bezier");
		assertCurveSamples(rgba, findTimeline(loadedAnimation, Animation.RGBATimeline.class), "RGBA Bezier");
		assertCurveSamples(rgba2, findTimeline(loadedAnimation, Animation.RGBA2Timeline.class), "RGBA2 Bezier");
		assertCurveSamples(rgb2, findTimeline(loadedAnimation, Animation.RGB2Timeline.class), "RGB2 Bezier");
	}

	private static void roundTripsAsynchronousCombinedAxes() {
		SkeletonData data = baseData("root", "child");
		SkeletonDocument doc = document(data);
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("asynchronous-axis-round-trip");
		editor.ensureTimeline(animation, AnimationEditor.Prop.SCALE, 0);
		editor.addKeyframe(animation, AnimationEditor.Prop.SCALE, 0, 1f, 2f, 0.5f);
		editor.addKeyframe(animation, AnimationEditor.Prop.SCALE, 0, 2f, 0.75f, 1.8f);
		Animation.Timeline combined = editor.findTimeline(animation, AnimationEditor.Prop.SCALE, 0);
		combined = editor.setBezierCurveChannel(animation, combined, 0, 0, 0.18f, -0.2f, 0.72f, 1.15f);
		combined = editor.setBezierCurveChannel(animation, combined, 0, 1, 0.32f, 0.25f, 0.81f, 0.88f);
		combined = editor.setBezierCurveChannel(animation, combined, 1, 0, 0.24f, 0.1f, 0.65f, 0.9f);
		combined = editor.setBezierCurveChannel(animation, combined, 1, 1, 0.42f, -0.1f, 0.77f, 1.2f);
		assertTrue(editor.separateAxisTimeline(animation, AnimationEditor.AxisGroup.SCALE, 0),
			"Scale timeline can be separated before asynchronous edits");
		Animation.Timeline x = editor.findTimeline(animation, AnimationEditor.Prop.SCALE_X, 0);
		Animation.Timeline y = editor.findTimeline(animation, AnimationEditor.Prop.SCALE_Y, 0);
		x = editor.editCurveKey(animation, x, 1, 0, 0.75f, editor.curveFrameValue(x, 1, 0));
		y = editor.editCurveKey(animation, y, 1, 0, 1.25f, editor.curveFrameValue(y, 1, 0));
		assertTrue(editor.combineAxisTimelines(animation, AnimationEditor.AxisGroup.SCALE, 0),
			"asynchronous axes can be combined before export");
		Animation.CurveTimeline expected = (Animation.CurveTimeline)editor.findTimeline(
			animation, AnimationEditor.Prop.SCALE, 0);

		String json = SkeletonExporter.toJson(doc);
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader())
			.readSkeletonData(new JsonReader().parse(json));
		Animation.CurveTimeline actual = findTimeline(loaded.findAnimation(animation.getName()),
			Animation.ScaleTimeline.class);
		assertTimelineCurveValues(expected, actual, 2,
			"asynchronous combined Scale JSON round trip");
	}

	private static void roundTripsSplitColorTimelines() {
		SkeletonData data = baseData("root", "child");
		SlotData slot = new SlotData(0, "split-color-slot", data.getBones().get(1));
		slot.setBlendMode(BlendMode.normal);
		data.getSlots().add(slot);

		Animation.RGBTimeline rgb = new Animation.RGBTimeline(2, 0, slot.getIndex());
		rgb.setFrame(0, 0.1f, 0.2f, 0.4f, 0.6f);
		rgb.setFrame(1, 0.9f, 0.8f, 0.6f, 0.4f);
		rgb.setStepped(0);
		Animation.AlphaTimeline alpha = new Animation.AlphaTimeline(2, 1, slot.getIndex());
		alpha.setFrame(0, 0.2f, 1f);
		alpha.setFrame(1, 1.1f, 0.25f);
		alpha.setBezier(0, 0, 0, 0.2f, 1f, 0.35f, 0.75f, 0.9f, 0.4f, 1.1f, 0.25f);

		Animation animation = new Animation("split-color-round-trip");
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(rgb);
		timelines.add(alpha);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"rgb\": ["), "RGB timeline is exported");
		assertTrue(json.contains("\"alpha\": ["), "alpha timeline is exported");
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		Animation loadedAnimation = loaded.findAnimation(animation.getName());
		Animation.RGBTimeline loadedRgb = findTimeline(loadedAnimation, Animation.RGBTimeline.class);
		Animation.AlphaTimeline loadedAlpha = findTimeline(loadedAnimation, Animation.AlphaTimeline.class);
		assertTrue(loadedRgb != null, "RGB timeline round trip");
		assertTrue(loadedRgb.getCurveType(0) == Animation.CurveTimeline.STEPPED, "RGB stepped curve round trip");
		assertTimelineRgb(loadedRgb, 0, 0.1f, 0.2f, 0.4f, 0.6f);
		assertTimelineRgb(loadedRgb, 1, 0.9f, 0.8f, 0.6f, 0.4f);
		assertTrue(loadedAlpha != null, "alpha timeline round trip");
		assertNear(0.2f, loadedAlpha.getFrames()[0], "alpha first time");
		assertNear(1f, loadedAlpha.getFrames()[1], "alpha first value");
		assertNear(1.1f, loadedAlpha.getFrames()[2], "alpha second time");
		assertNear(0.25f, loadedAlpha.getFrames()[3], "alpha second value");
		assertCurveSamples(alpha, loadedAlpha, "alpha Bezier");
	}

	private static void roundTripsDrawOrderTimelines() {
		SkeletonData data = baseData("root", "child");
		for (int index = 0; index < 4; index++) {
			SlotData slot = new SlotData(index, "draw-slot-" + index, data.getBones().get(index % 2));
			slot.setBlendMode(BlendMode.normal);
			data.getSlots().add(slot);
		}
		Animation.DrawOrderTimeline drawOrder = new Animation.DrawOrderTimeline(2);
		drawOrder.setFrame(0, 0.1f, null);
		drawOrder.setFrame(1, 0.75f, new int[] {2, 0, 3, 1});
		Animation.DrawOrderFolderTimeline drawOrderFolder = new Animation.DrawOrderFolderTimeline(
			2, new int[] {0, 2, 3}, data.getSlots().size);
		drawOrderFolder.setFrame(0, 0.2f, null);
		drawOrderFolder.setFrame(1, 0.8f, new int[] {2, 0, 1});
		Animation animation = new Animation("draw-order-round-trip");
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(drawOrder, drawOrderFolder);
		animation.setTimelines(timelines, new IntArray());
		data.getAnimations().add(animation);

		String json = SkeletonExporter.toJson(document(data));
		assertTrue(json.contains("\"drawOrder\": ["), "draw order timeline is exported");
		assertTrue(json.contains("\"slot\": \"draw-slot-1\", \"offset\": 2"),
			"draw order positive offset is exported");
		assertTrue(json.contains("\"slot\": \"draw-slot-2\", \"offset\": -2"),
			"draw order negative offset is exported");
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		Animation.DrawOrderTimeline loadedTimeline = findTimeline(loaded.findAnimation(animation.getName()),
			Animation.DrawOrderTimeline.class);
		assertTrue(loadedTimeline != null, "draw order timeline round trip");
		assertNear(0.1f, loadedTimeline.getFrames()[0], "setup draw order time");
		assertTrue(loadedTimeline.getDrawOrders()[0] == null, "setup draw order round trip");
		assertNear(0.75f, loadedTimeline.getFrames()[1], "changed draw order time");
		assertTrue(java.util.Arrays.equals(new int[] {2, 0, 3, 1}, loadedTimeline.getDrawOrders()[1]),
			"changed draw order permutation round trip");
		Animation.DrawOrderFolderTimeline loadedFolder = findTimeline(
			loaded.findAnimation(animation.getName()), Animation.DrawOrderFolderTimeline.class);
		assertTrue(loadedFolder != null, "draw order folder timeline round trip");
		assertIntArray(new int[] {0, 2, 3}, loadedFolder.getSlots(), "draw order folder slots");
		assertNear(0.2f, loadedFolder.getFrames()[0], "setup draw order folder time");
		assertTrue(loadedFolder.getDrawOrders()[0] == null, "setup draw order folder round trip");
		assertIntArray(new int[] {2, 0, 1}, loadedFolder.getDrawOrders()[1],
			"changed draw order folder permutation round trip");

		Animation duplicate = new Animation("duplicate-draw-order");
		Animation.DrawOrderTimeline duplicateFirst = new Animation.DrawOrderTimeline(1);
		duplicateFirst.setFrame(0, 0f, null);
		Animation.DrawOrderTimeline duplicateSecond = new Animation.DrawOrderTimeline(1);
		duplicateSecond.setFrame(0, 0.5f, new int[] {1, 0, 2, 3});
		Array<Animation.Timeline> duplicateTimelines = new Array<>(true, 2, Animation.Timeline[]::new);
		duplicateTimelines.add(duplicateFirst);
		duplicateTimelines.add(duplicateSecond);
		duplicate.setTimelines(duplicateTimelines, new IntArray());
		data.getAnimations().add(duplicate);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(document(data)),
			"export rejects multiple draw order timelines in one animation");
		data.getAnimations().removeValue(duplicate, true);

		Animation invalidFolder = new Animation("invalid-draw-order-folder");
		Animation.DrawOrderFolderTimeline invalidFolderTimeline = new Animation.DrawOrderFolderTimeline(
			1, new int[] {0, 0}, data.getSlots().size);
		invalidFolderTimeline.setFrame(0, 0f, new int[] {0, 1});
		Array<Animation.Timeline> invalidFolderTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		invalidFolderTimelines.add(invalidFolderTimeline);
		invalidFolder.setTimelines(invalidFolderTimelines, new IntArray());
		data.getAnimations().add(invalidFolder);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(document(data)),
			"export rejects duplicate draw order folder slots");
		data.getAnimations().removeValue(invalidFolder, true);

		Animation invalid = new Animation("invalid-draw-order");
		Animation.DrawOrderTimeline invalidTimeline = new Animation.DrawOrderTimeline(1);
		invalidTimeline.setFrame(0, 0f, new int[] {0, 0, 2, 3});
		Array<Animation.Timeline> invalidTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		invalidTimelines.add(invalidTimeline);
		invalid.setTimelines(invalidTimelines, new IntArray());
		data.getAnimations().add(invalid);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(document(data)),
			"export rejects duplicate draw order slot indices");
	}

	private static SlotData addTwoColorSlot(SkeletonData data, String name) {
		SlotData slot = new SlotData(data.getSlots().size, name, data.getBones().first());
		slot.setBlendMode(BlendMode.normal);
		setDarkColor(slot, Color.BLACK);
		data.getSlots().add(slot);
		return slot;
	}

	private static void setBezierChannels(Animation.CurveTimeline timeline, float endTime,
		float[] start, float[] end) {
		for (int channel = 0; channel < start.length; channel++) {
			float cx1 = 0.15f + channel * 0.025f;
			float cx2 = 0.82f + channel * 0.02f;
			float cy1 = start[channel] + (channel % 2 == 0 ? -0.25f : 0.2f);
			float cy2 = end[channel] + (channel % 2 == 0 ? 0.3f : -0.15f);
			timeline.setBezier(channel, 0, channel, 0f, start[channel], cx1, cy1,
				cx2, cy2, endTime, end[channel]);
		}
	}

	private static void assertCurveSamples(Animation.CurveTimeline expected, Animation.CurveTimeline actual,
		String message) {
		assertCurveSamples(expected, actual, expected.getFrameEntries() - 1, message);
	}

	private static void assertTimelineCurveValues(Animation.CurveTimeline expected,
		Animation.CurveTimeline actual, int channels, String message) {
		assertTrue(actual != null && expected.getFrameCount() == actual.getFrameCount(),
			message + " frame count");
		for (int frame = 0; frame < expected.getFrameCount() - 1; frame++) {
			float start = expected.getFrames()[frame * expected.getFrameEntries()];
			float end = expected.getFrames()[(frame + 1) * expected.getFrameEntries()];
			for (int sample = 1; sample <= 9; sample++) {
				float time = start + (end - start) * sample / 10f;
				for (int channel = 0; channel < channels; channel++)
					assertNear(curveValueAt(expected, time, channel), curveValueAt(actual, time, channel),
						message + " frame " + frame + " sample " + sample + " channel " + channel);
			}
		}
	}

	private static float curveValueAt(Animation.CurveTimeline timeline, float time, int channel) {
		int entries = timeline.getFrameEntries();
		for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
			int base = frame * entries, next = base + entries;
			float time1 = timeline.getFrames()[base], time2 = timeline.getFrames()[next];
			if (time > time2) continue;
			float value1 = timeline.getFrames()[base + channel + 1];
			float value2 = timeline.getFrames()[next + channel + 1];
			int type = timeline.getCurveType(frame);
			if (type == Animation.CurveTimeline.STEPPED) return value1;
			if (type < Animation.CurveTimeline.BEZIER)
				return value1 + (value2 - value1) * (time - time1) / (time2 - time1);
			return timeline.getBezierValue(time, base, channel + 1,
				type - Animation.CurveTimeline.BEZIER + channel * Animation.CurveTimeline.BEZIER_SIZE);
		}
		return timeline.getFrames()[(timeline.getFrameCount() - 1) * entries + channel + 1];
	}

	private static void assertCurveSamples(Animation.CurveTimeline expected, Animation.CurveTimeline actual,
		int channels, String message) {
		assertTrue(actual != null, message + " timeline");
		assertTrue(expected.getFrameEntries() == actual.getFrameEntries(), message + " frame layout");
		int expectedType = expected.getCurveType(0);
		int actualType = actual.getCurveType(0);
		assertTrue(expectedType >= Animation.CurveTimeline.BEZIER
			&& actualType >= Animation.CurveTimeline.BEZIER, message + " curve type");
		int entries = expected.getFrameEntries();
		for (int sample = 1; sample <= 9; sample++) {
			float time = 1.2f * sample / 10f;
			for (int channel = 0; channel < channels; channel++) {
				float expectedValue = expected.getBezierValue(time, 0, channel + 1,
					expectedType - Animation.CurveTimeline.BEZIER + channel * Animation.CurveTimeline.BEZIER_SIZE);
				float actualValue = actual.getBezierValue(time, 0, channel + 1,
					actualType - Animation.CurveTimeline.BEZIER + channel * Animation.CurveTimeline.BEZIER_SIZE);
				assertNear(expectedValue, actualValue, message + " sample " + sample + " channel " + channel);
			}
		}
	}

	private static void assertDeformCurveSamples(Animation.DeformTimeline expected,
		Animation.DeformTimeline actual, String message) {
		assertTrue(actual != null, message + " timeline");
		int expectedBlock = expected.getCurveType(0) - Animation.CurveTimeline.BEZIER;
		int actualBlock = actual.getCurveType(0) - Animation.CurveTimeline.BEZIER;
		assertTrue(expectedBlock >= 0 && actualBlock >= 0, message + " curve type");
		float[] expectedCurves = curveData(expected);
		float[] actualCurves = curveData(actual);
		for (int index = 0; index < Animation.CurveTimeline.BEZIER_SIZE; index++)
			assertNear(expectedCurves[expectedBlock + index], actualCurves[actualBlock + index],
				message + " sample " + index);
	}

	private static float[] curveData(Animation.CurveTimeline timeline) {
		try {
			java.lang.reflect.Field field = Animation.CurveTimeline.class.getDeclaredField("curves");
			field.setAccessible(true);
			return (float[])field.get(timeline);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("cannot inspect curve samples", error);
		}
	}

	private static void atomicallyReplacesFilesWithoutLeavingTemporaryData() throws Exception {
		Path directory = Files.createTempDirectory("openspine-save-");
		Path target = directory.resolve("hero.json");
		try {
			Files.writeString(target, "original", StandardCharsets.UTF_8);
			SkeletonData data = baseData("root", "child");
			SkeletonDocument doc = document(data);
			String expected = SkeletonExporter.toJson(doc);
			SkeletonExporter.writeAtomic(doc, target);
			assertTrue(expected.equals(Files.readString(target, StandardCharsets.UTF_8)),
				"atomic save replaces existing content");
			assertTemporaryFiles(directory, target, 0, "successful save cleans temporary file");

			CancellationToken cancelled = new CancellationToken() {
				private int checkpoints;

				@Override public void checkCancelled() {
					super.checkCancelled();
					if (++checkpoints == 4) {
						cancel();
						super.checkCancelled();
					}
				}
			};
			try {
				SkeletonExporter.writeAtomic(doc, target, cancelled);
				throw new AssertionError("cancelled save should stop before replacing the target");
			} catch (java.util.concurrent.CancellationException expectedCancellation) {
				// Expected.
			}
			assertTrue(expected.equals(Files.readString(target, StandardCharsets.UTF_8)),
				"cancelled save preserves existing content");
			assertTemporaryFiles(directory, target, 0, "cancelled save leaves no temporary file");

			data.getConstraints().add(new SliderData("invalid-slider"));
			try {
				SkeletonExporter.writeAtomic(doc, target);
				throw new AssertionError("invalid save should fail");
			} catch (IllegalArgumentException expectedFailure) {
				// Export validation runs before the destination or a temporary file is touched.
			}
			assertTrue(expected.equals(Files.readString(target, StandardCharsets.UTF_8)),
				"failed save preserves existing content");
			assertTemporaryFiles(directory, target, 0, "failed save leaves no temporary file");
		} finally {
			try (var children = Files.list(directory)) {
				for (Path child : children.toList()) Files.deleteIfExists(child);
			}
			Files.deleteIfExists(directory);
		}
	}

	private static void assertTemporaryFiles(Path directory, Path target, long expected, String message)
		throws Exception {
		try (var children = Files.list(directory)) {
			long count = children.filter(path -> !path.equals(target)).count();
			if (count != expected)
				throw new AssertionError(message + ": expected " + expected + ", got " + count);
		}
	}

	private static void roundTripsEscapedNamesAndColors() {
		String rootName = "root\"\\\nline";
		String childName = "child\tname";
		String slotName = "slot\"\\name";
		String skinName = "skin\nname";
		String placeholder = "attachment\"name";
		String path = "images\\part\"01";

		SkeletonData data = baseData(rootName, childName);
		BoneData root = data.getBones().first();
		root.getColor().set(0.15f, 0.35f, 0.55f, 0.75f);
		root.setIcon("icons\\bone\"root");
		root.setIconSize(1.4f);
		root.setIconRotation(-12f);
		root.setVisible(false);
		data.setFps(23.976f);
		SlotData slot = new SlotData(0, slotName, data.getBones().get(1));
		slot.setBlendMode(BlendMode.multiply);
		slot.setVisible(false);
		slot.setAttachmentName(placeholder);
		slot.getSetupPose().getColor().set(0.2f, 0.4f, 0.6f, 0.8f);
		setDarkColor(slot, new Color(0x12 / 255f, 0x3A / 255f, 0xBC / 255f, 0.25f));
		data.getSlots().add(slot);
		SlotData rgba2Slot = new SlotData(1, "rgba2-slot", data.getBones().get(1));
		rgba2Slot.setBlendMode(BlendMode.normal);
		setDarkColor(rgba2Slot, Color.BLACK);
		data.getSlots().add(rgba2Slot);
		SlotData rgb2Slot = new SlotData(2, "rgb2-slot", data.getBones().get(1));
		rgb2Slot.setBlendMode(BlendMode.normal);
		setDarkColor(rgb2Slot, Color.BLACK);
		data.getSlots().add(rgb2Slot);

		Skin skin = new Skin(skinName);
		skin.getColor().set(0.8f, 0.25f, 0.45f, 0.65f);
		RegionAttachment region = new RegionAttachment(placeholder,
			new com.esotericsoftware.spine.attachments.Sequence(1, false));
		region.setPath(path);
		region.setWidth(40f);
		region.setHeight(20f);
		region.getColor().set(0.7f, 0.5f, 0.3f, 0.9f);
		skin.setAttachment(0, placeholder, region);
		data.getSkins().add(skin);

		Animation animation = new Animation("anim\"\\\nname");
		animation.getColor().set(0.3f, 0.7f, 0.2f, 0.85f);
		Array<Animation.Timeline> timelines = new Array<>(true, 9, Animation.Timeline[]::new);
		var translateX = new Animation.TranslateXTimeline(2, 0, 1);
		translateX.setFrame(0, 0f, 0f);
		translateX.setFrame(1, 1f, 12f);
		var translateY = new Animation.TranslateYTimeline(2, 0, 1);
		translateY.setFrame(0, 0f, 0f);
		translateY.setFrame(1, 1f, -8f);
		var scaleX = new Animation.ScaleXTimeline(2, 0, 1);
		scaleX.setFrame(0, 0f, 1f);
		scaleX.setFrame(1, 1f, 1.5f);
		var scaleY = new Animation.ScaleYTimeline(2, 0, 1);
		scaleY.setFrame(0, 0f, 1f);
		scaleY.setFrame(1, 1f, 0.75f);
		var shear = new Animation.ShearTimeline(2, 0, 1);
		shear.setFrame(0, 0f, 0f, 0f);
		shear.setFrame(1, 1f, 7f, -3f);
		shear.setStepped(0);
		var shearX = new Animation.ShearXTimeline(2, 0, 1);
		shearX.setFrame(0, 0f, 0f);
		shearX.setFrame(1, 1f, 4f);
		var shearY = new Animation.ShearYTimeline(2, 0, 1);
		shearY.setFrame(0, 0f, 0f);
		shearY.setFrame(1, 1f, -6f);
		var inherit = new Animation.InheritTimeline(2, 1);
		inherit.setFrame(0, 0f, Inherit.normal);
		inherit.setFrame(1, 0.65f, Inherit.noScaleOrReflection);
		var attachments = new Animation.AttachmentTimeline(2, 0);
		attachments.setFrame(0, 0f, placeholder);
		attachments.setFrame(1, 0.5f, null);
		var rgba = new Animation.RGBATimeline(2, 0, 0);
		rgba.setFrame(0, 0f, 1f, 0.8f, 0.6f, 1f);
		rgba.setFrame(1, 0.75f, 0.2f, 0.4f, 0.6f, 0.8f);
		rgba.setStepped(0);
		var rgba2 = new Animation.RGBA2Timeline(2, 0, 1);
		rgba2.setFrame(0, 0f, 1f, 0.8f, 0.6f, 1f, 0.1f, 0.2f, 0.3f);
		rgba2.setFrame(1, 0.8f, 0.2f, 0.4f, 0.6f, 0.8f, 0.8f, 0.6f, 0.4f);
		rgba2.setStepped(0);
		var rgb2 = new Animation.RGB2Timeline(2, 0, 2);
		rgb2.setFrame(0, 0f, 0.9f, 0.8f, 0.7f, 0.1f, 0.2f, 0.3f);
		rgb2.setFrame(1, 0.9f, 0.2f, 0.4f, 0.6f, 0.8f, 0.6f, 0.4f);
		timelines.addAll(translateX, translateY, scaleX, scaleY, shear, shearX, shearY, inherit,
			attachments, rgba, rgba2, rgb2);
		IntArray animatedBones = new IntArray();
		animatedBones.add(1);
		animation.setTimelines(timelines, animatedBones);
		animation.setDuration(1f);
		data.getAnimations().add(animation);

		SkeletonDocument doc = document(data);
		String json = SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"dark\": \"123abc\""), "slot dark color exports as six RGB digits");
		assertTrue(json.contains("\"rgba2\": ["), "RGBA2 timeline is exported");
		assertTrue(json.contains("\"rgb2\": ["), "RGB2 timeline is exported");
		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(json));
		assertTrue(loaded.findBone(rootName) != null, "escaped root name");
		BoneData loadedRoot = loaded.findBone(rootName);
		assertColor(root.getColor(), loadedRoot.getColor(), "bone color");
		assertTrue(root.getIcon().equals(loadedRoot.getIcon()), "bone icon");
		assertNear(root.getIconSize(), loadedRoot.getIconSize(), "bone icon size");
		assertNear(root.getIconRotation(), loadedRoot.getIconRotation(), "bone icon rotation");
		assertTrue(!loadedRoot.getVisible(), "bone visibility");
		assertNear(23.976f, loaded.getFps(), "project FPS");
		assertTrue(loaded.findBone(childName) != null, "escaped child name");
		SlotData loadedSlot = loaded.findSlot(slotName);
		assertTrue(loadedSlot != null, "escaped slot name");
		assertColor(slot.getSetupPose().getColor(), loadedSlot.getSetupPose().getColor(), "slot color");
		assertNear(slot.getSetupPose().getDarkColor().r, loadedSlot.getSetupPose().getDarkColor().r, "slot dark red");
		assertNear(slot.getSetupPose().getDarkColor().g, loadedSlot.getSetupPose().getDarkColor().g, "slot dark green");
		assertNear(slot.getSetupPose().getDarkColor().b, loadedSlot.getSetupPose().getDarkColor().b, "slot dark blue");
		assertNear(1f, loadedSlot.getSetupPose().getDarkColor().a, "slot dark alpha defaults to one");
		assertTrue(loadedSlot.getBlendMode() == BlendMode.multiply, "slot blend mode");
		assertTrue(!loadedSlot.getVisible(), "slot visibility");
		Skin loadedSkin = loaded.findSkin(skinName);
		assertTrue(loadedSkin != null, "escaped skin name");
		assertColor(skin.getColor(), loadedSkin.getColor(), "skin color");
		RegionAttachment loadedRegion = (RegionAttachment)loadedSkin.getAttachment(0, placeholder);
		assertTrue(loadedRegion != null && path.equals(loadedRegion.getPath()), "escaped attachment path");
		assertColor(region.getColor(), loadedRegion.getColor(), "region color");
		Animation loadedAnimation = loaded.findAnimation(animation.getName());
		assertTrue(loadedAnimation != null, "escaped animation name");
		assertColor(animation.getColor(), loadedAnimation.getColor(), "animation color");
		assertTimelineValue(loadedAnimation, Animation.TranslateXTimeline.class, 12f, "translate X");
		assertTimelineValue(loadedAnimation, Animation.TranslateYTimeline.class, -8f, "translate Y");
		assertTimelineValue(loadedAnimation, Animation.ScaleXTimeline.class, 1.5f, "scale X");
		assertTimelineValue(loadedAnimation, Animation.ScaleYTimeline.class, 0.75f, "scale Y");
		Animation.ShearTimeline loadedShear = findTimeline(loadedAnimation, Animation.ShearTimeline.class);
		assertTimeline2(loadedShear, 1, 1f, 7f, -3f, "shear");
		assertTrue(loadedShear.getCurveType(0) == Animation.CurveTimeline.STEPPED, "shear stepped curve");
		assertTimelineValue(loadedAnimation, Animation.ShearXTimeline.class, 4f, "shear X");
		assertTimelineValue(loadedAnimation, Animation.ShearYTimeline.class, -6f, "shear Y");
		Animation.InheritTimeline loadedInherit = findTimeline(loadedAnimation, Animation.InheritTimeline.class);
		assertTrue(loadedInherit != null, "inherit timeline");
		assertNear(0.65f, loadedInherit.getFrames()[2], "inherit timeline time");
		assertTrue((int)loadedInherit.getFrames()[3] == Inherit.noScaleOrReflection.ordinal(),
			"inherit timeline mode");
		Animation.AttachmentTimeline loadedAttachments = findTimeline(loadedAnimation, Animation.AttachmentTimeline.class);
		assertTrue(loadedAttachments != null, "attachment timeline");
		assertTrue(placeholder.equals(loadedAttachments.getAttachmentNames()[0]), "attachment timeline name");
		assertTrue(loadedAttachments.getAttachmentNames()[1] == null, "attachment timeline clear frame");
		Animation.RGBATimeline loadedColor = findTimeline(loadedAnimation, Animation.RGBATimeline.class);
		assertTrue(loadedColor != null, "RGBA timeline");
		assertTrue(loadedColor.getCurveType(0) == Animation.CurveTimeline.STEPPED, "RGBA stepped curve");
		assertTimelineColor(loadedColor, 1, 0.75f, 0.2f, 0.4f, 0.6f, 0.8f);
		Animation.RGBA2Timeline loadedRgba2 = findTimeline(loadedAnimation, Animation.RGBA2Timeline.class);
		assertTrue(loadedRgba2 != null, "RGBA2 timeline");
		assertTrue(loadedRgba2.getCurveType(0) == Animation.CurveTimeline.STEPPED, "RGBA2 stepped curve");
		assertTwoColorTimeline(loadedRgba2, 1, 0.8f,
			0.2f, 0.4f, 0.6f, 0.8f, 0.8f, 0.6f, 0.4f);
		Animation.RGB2Timeline loadedRgb2 = findTimeline(loadedAnimation, Animation.RGB2Timeline.class);
		assertTrue(loadedRgb2 != null, "RGB2 timeline");
		assertTwoColorTimeline(loadedRgb2, 1, 0.9f,
			0.2f, 0.4f, 0.6f, Float.NaN, 0.8f, 0.6f, 0.4f);
	}

	private static void roundTripsSkeletonStructure() {
		SkeletonData data = baseData("root", "middle");
		BoneData tip = new BoneData(2, "tip", data.getBones().get(1));
		tip.getSetupPose().setInherit(Inherit.normal);
		data.getBones().add(tip);
		SlotData rootSlot = new SlotData(0, "root-slot", data.getBones().first());
		SlotData tipSlot = new SlotData(1, "tip-slot", tip);
		rootSlot.setBlendMode(BlendMode.normal);
		tipSlot.setBlendMode(BlendMode.normal);
		data.getSlots().addAll(rootSlot, tipSlot);
		Skin defaultSkin = new Skin("default");
		Skin alternateSkin = new Skin("alternate");
		data.getSkins().addAll(defaultSkin, alternateSkin);
		data.setDefaultSkin(defaultSkin);
		data.getAnimations().add(emptyAnimation("idle"), emptyAnimation("walk"));
		data.setX(-12f);
		data.setY(8f);
		data.setWidth(320f);
		data.setHeight(180f);
		data.setReferenceScale(75f);

		SkeletonData loaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(
			new JsonReader().parse(SkeletonExporter.toJson(document(data))));
		assertTrue(loaded.getBones().size == 3 && loaded.findBone("tip").getParent() == loaded.findBone("middle"),
			"bone order and parent references round trip");
		assertTrue(loaded.findSlot("tip-slot").getBoneData() == loaded.findBone("tip"),
			"slot bone references round trip");
		assertTrue(loaded.getDefaultSkin() == loaded.findSkin("default") && loaded.findSkin("alternate") != null,
			"default and alternate skins round trip");
		assertTrue(loaded.findAnimation("idle") != null && loaded.findAnimation("walk") != null,
			"unique animation names round trip");
		assertNear(75f, loaded.getReferenceScale(), "reference scale");
	}

	private static void rejectsInvalidSkeletonStructure() {
		SkeletonData data = baseData("bone", "bone");
		assertStructureRejected(data, "duplicate bone names");

		data = new SkeletonData();
		data.getBones().add(new BoneData(1, "root", null));
		assertStructureRejected(data, "bone index must match array position");

		data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData foreignParent = new BoneData(0, "foreign", null);
		BoneData child = new BoneData(1, "child", foreignParent);
		root.getSetupPose().setInherit(Inherit.normal);
		child.getSetupPose().setInherit(Inherit.normal);
		data.getBones().add(root, child);
		assertStructureRejected(data, "bone parent must belong to the skeleton");

		data = new SkeletonData();
		root = new BoneData(0, "root", null);
		BoneData laterParent = new BoneData(2, "later", root);
		child = new BoneData(1, "child", laterParent);
		root.getSetupPose().setInherit(Inherit.normal);
		child.getSetupPose().setInherit(Inherit.normal);
		laterParent.getSetupPose().setInherit(Inherit.normal);
		data.getBones().add(root, child, laterParent);
		assertStructureRejected(data, "bone parent must precede its child");

		data = baseData("root", "child");
		data.getBones().add(null);
		assertStructureRejected(data, "null bone data");

		data = new SkeletonData();
		data.getBones().add(new BoneData(0, "", null));
		assertStructureRejected(data, "empty bone name");

		data = new SkeletonData();
		data.getBones().add(new BoneData(0, "root", null));
		assertStructureRejected(data, "bone setup inherit mode cannot be null");

		data = baseData("root", "child");
		SlotData firstSlot = new SlotData(0, "slot", data.getBones().first());
		SlotData duplicateSlot = new SlotData(1, "slot", data.getBones().get(1));
		firstSlot.setBlendMode(BlendMode.normal);
		duplicateSlot.setBlendMode(BlendMode.normal);
		data.getSlots().add(firstSlot, duplicateSlot);
		assertStructureRejected(data, "duplicate slot names");

		data = baseData("root", "child");
		SlotData wrongIndexSlot = new SlotData(3, "slot", data.getBones().first());
		wrongIndexSlot.setBlendMode(BlendMode.normal);
		data.getSlots().add(wrongIndexSlot);
		assertStructureRejected(data, "slot index must match array position");

		data = baseData("root", "child");
		SlotData foreignBoneSlot = new SlotData(0, "slot", new BoneData(0, "foreign", null));
		foreignBoneSlot.setBlendMode(BlendMode.normal);
		data.getSlots().add(foreignBoneSlot);
		assertStructureRejected(data, "slot bone must belong to the skeleton");

		data = baseData("root", "child");
		data.getSlots().add(null);
		assertStructureRejected(data, "null slot data");

		data = baseData("root", "child");
		SlotData emptySlot = new SlotData(0, "", data.getBones().first());
		emptySlot.setBlendMode(BlendMode.normal);
		data.getSlots().add(emptySlot);
		assertStructureRejected(data, "empty slot name");

		data = baseData("root", "child");
		data.getSkins().add(new Skin("skin"), new Skin("skin"));
		assertStructureRejected(data, "duplicate skin names");

		data = baseData("root", "child");
		data.getSkins().add(null);
		assertStructureRejected(data, "null skin data");

		data = baseData("root", "child");
		data.getSkins().add(new Skin(""));
		assertStructureRejected(data, "empty skin name");

		data = baseData("root", "child");
		data.setDefaultSkin(new Skin("default"));
		assertStructureRejected(data, "default skin must belong to the skeleton");

		data = baseData("root", "child");
		Skin nonDefault = new Skin("alternate");
		data.getSkins().add(nonDefault);
		data.setDefaultSkin(nonDefault);
		assertStructureRejected(data, "default skin name must be representable by Spine JSON");

		data = baseData("root", "child");
		data.getSkins().add(new Skin("default"));
		assertStructureRejected(data, "named default skin must match SkeletonData.defaultSkin");

		data = baseData("root", "child");
		data.getAnimations().add(emptyAnimation("idle"), emptyAnimation("idle"));
		assertStructureRejected(data, "duplicate animation names");

		data = baseData("root", "child");
		data.getAnimations().add(null);
		assertStructureRejected(data, "null animation data");

		data = baseData("root", "child");
		data.getAnimations().add(new Animation("uninitialized"));
		assertStructureRejected(data, "animation timeline collection must be initialized");

		data = baseData("root", "child");
		Animation nullTimeline = emptyAnimation("null-timeline");
		nullTimeline.getTimelines().add(null);
		data.getAnimations().add(nullTimeline);
		assertStructureRejected(data, "animation cannot contain a null timeline");

		data = baseData("root", "child");
		data.getAnimations().add(new Animation(""));
		assertStructureRejected(data, "empty animation name");

		data = baseData("root", "child");
		data.setReferenceScale(0f);
		assertStructureRejected(data, "reference scale must be positive");

		data = baseData("root", "child");
		data.setWidth(Float.NaN);
		assertStructureRejected(data, "skeleton bounds must be finite");
	}

	private static void assertStructureRejected(SkeletonData data, String message) {
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		try {
			SkeletonExporter.toJson(doc);
		} catch (IllegalArgumentException | IllegalStateException expected) {
			return;
		} catch (Throwable error) {
			throw new AssertionError(message + ": unexpected exception " + error, error);
		}
		throw new AssertionError(message + ": expected export rejection");
	}

	private static void rejectsUnsupportedOrInvalidData() {
		SkeletonData data = baseData("root", "child");
		SkeletonDocument doc = document(data);
		data.setFps(0f);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc), "invalid project FPS");
		data.setFps(30f);
		data.getBones().first().getSetupPose().setX(Float.NaN);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc), "non-finite value");
		data.getBones().first().getSetupPose().setX(0f);
		SlotData slot = new SlotData(0, "slot", data.getBones().first());
		data.getSlots().add(slot);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc), "missing slot blend mode");
		slot.setBlendMode(BlendMode.normal);
		slot.getSetupPose().getColor().r = Float.NaN;
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc), "non-finite slot color");
		slot.getSetupPose().getColor().set(Color.WHITE);
		setDarkColor(slot, Color.BLACK);
		slot.getSetupPose().getDarkColor().r = Float.POSITIVE_INFINITY;
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc), "non-finite slot dark color");
		setDarkColor(slot, null);

		Skin skin = new Skin("default");
		skin.setAttachment(0, "box", new BoundingBoxAttachment("box"));
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc), "unsupported attachment");
		data.getSkins().clear();
		data.setDefaultSkin(null);

		data.getConstraints().add(new SliderData("slider"));
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc),
			"Slider constraint requires an animation reference");
		data.getConstraints().clear();
		Animation zeroDuration = new Animation("zero-slider-target");
		zeroDuration.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		data.getAnimations().add(zeroDuration);
		SliderData zeroDurationLoop = new SliderData("zero-duration-loop");
		zeroDurationLoop.setAnimation(zeroDuration);
		zeroDurationLoop.setLoop(true);
		data.getConstraints().add(zeroDurationLoop);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc),
			"export rejects a looping Slider whose target duration would produce runtime NaN");
		data.getConstraints().clear();
		data.getAnimations().clear();

		Animation missingDark = new Animation("missing-dark");
		var rgb2 = new Animation.RGB2Timeline(1, 0, slot.getIndex());
		rgb2.setFrame(0, 0f, 1f, 1f, 1f, 0f, 0f, 0f);
		Array<Animation.Timeline> twoColorTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		twoColorTimelines.add(rgb2);
		missingDark.setTimelines(twoColorTimelines, new IntArray());
		data.getAnimations().add(missingDark);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"two color timeline requires setup dark color");
		setDarkColor(slot, Color.BLACK);
		assertTrue(SkeletonExporter.toJson(doc).contains("\"rgb2\": ["),
			"the same RGB2 timeline exports after enabling setup dark color");
		data.getAnimations().clear();
		setDarkColor(slot, null);

		Animation missingSlot = new Animation("missing-slot");
		var attachment = new Animation.AttachmentTimeline(1, 4);
		attachment.setFrame(0, 0f, null);
		Array<Animation.Timeline> missingSlotTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		missingSlotTimelines.add(attachment);
		missingSlot.setTimelines(missingSlotTimelines, new IntArray());
		data.getAnimations().add(missingSlot);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"slot timeline target index must exist");
		data.getAnimations().clear();

		Animation missingBone = new Animation("missing-bone");
		var invalidRotate = new Animation.RotateTimeline(1, 0, 4);
		invalidRotate.setFrame(0, 0f, 0f);
		Array<Animation.Timeline> missingBoneTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		missingBoneTimelines.add(invalidRotate);
		missingBone.setTimelines(missingBoneTimelines, new IntArray());
		data.getAnimations().add(missingBone);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"bone timeline target index must exist");
		data.getAnimations().clear();

		EventData validEvent = new EventData("valid-event");
		validEvent.getSetupPose().setString("");
		data.getEvents().add(validEvent);
		validEvent.getSetupPose().setFloat(Float.NaN);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc),
			"event setup values must be finite");
		validEvent.getSetupPose().setFloat(0f);

		EventData missingEvent = new EventData("missing-event");
		missingEvent.getSetupPose().setString("");
		Event invalidReference = new Event(0f, missingEvent);
		invalidReference.setString("");
		Animation.EventTimeline invalidEvents = new Animation.EventTimeline(1);
		invalidEvents.setFrame(0, invalidReference);
		Animation invalidEventAnimation = new Animation("invalid-event-reference");
		Array<Animation.Timeline> invalidEventTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		invalidEventTimelines.add(invalidEvents);
		invalidEventAnimation.setTimelines(invalidEventTimelines, new IntArray());
		data.getAnimations().add(invalidEventAnimation);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"event timeline definitions must belong to the skeleton");
		data.getAnimations().clear();

		Event invalidTime = new Event(Float.NaN, validEvent);
		invalidTime.setString("");
		Animation.EventTimeline invalidTimeTimeline = new Animation.EventTimeline(1);
		invalidTimeTimeline.setFrame(0, invalidTime);
		Animation invalidTimeAnimation = new Animation("invalid-event-time");
		Array<Animation.Timeline> invalidTimeTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		invalidTimeTimelines.add(invalidTimeTimeline);
		invalidTimeAnimation.setTimelines(invalidTimeTimelines, new IntArray());
		data.getAnimations().add(invalidTimeAnimation);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc),
			"event timeline times must be finite");
		data.getAnimations().clear();

	}

	private static void rejectsInvalidPathAndPhysicsData() {
		SkeletonData data = baseData("root", "tip");
		SlotData slot = new SlotData(0, "path-slot", data.getBones().first());
		slot.setBlendMode(BlendMode.normal);
		data.getSlots().add(slot);
		PathConstraintData path = new PathConstraintData("path");
		path.getBones().add(data.getBones().get(1));
		path.setSlot(slot);
		path.setPositionMode(PathConstraintData.PositionMode.percent);
		path.setSpacingMode(PathConstraintData.SpacingMode.length);
		path.setRotateMode(PathConstraintData.RotateMode.tangent);
		path.getSetupPose().setMixRotate(1f);
		path.getSetupPose().setMixX(1f);
		path.getSetupPose().setMixY(1f);
		data.getConstraints().add(path);
		PhysicsConstraintData physics = new PhysicsConstraintData("physics");
		physics.setBone(data.getBones().get(1));
		physics.setStep(1f / 60f);
		physics.setLimit(5000f);
		physics.getSetupPose().setInertia(0.5f);
		physics.getSetupPose().setStrength(100f);
		physics.getSetupPose().setDamping(0.85f);
		physics.getSetupPose().setMassInverse(1f);
		physics.getSetupPose().setMix(1f);
		data.getConstraints().add(physics);
		SkeletonDocument doc = document(data);
		assertTrue(SkeletonExporter.toJson(doc).contains("\"type\": \"physics\""),
			"valid Path and Physics constraints export before invalid mutations");

		physics.setStep(1f / 59.5f);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"Physics step must be representable by integer FPS");
		physics.setStep(1f / 60f);
		physics.getSetupPose().setMassInverse(0f);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc),
			"Physics setup mass must be greater than zero");
		physics.getSetupPose().setMassInverse(Float.MIN_VALUE);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc),
			"Physics setup mass must remain finite after inversion");
		physics.getSetupPose().setMassInverse(1f);
		path.getSetupPose().setSpacing(Float.NaN);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc),
			"Path setup values must be finite");
		path.getSetupPose().setSpacing(0f);

		Animation.PathConstraintPositionTimeline wrongPathTarget =
			new Animation.PathConstraintPositionTimeline(1, 0, 1);
		wrongPathTarget.setFrame(0, 0f, 0f);
		setOnlyAnimation(data, "wrong-path-target", wrongPathTarget);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"Path timeline target must reference a Path constraint");

		Animation.PhysicsConstraintInertiaTimeline wrongPhysicsTarget =
			new Animation.PhysicsConstraintInertiaTimeline(1, 0, 0);
		wrongPhysicsTarget.setFrame(0, 0f, 0.5f);
		setOnlyAnimation(data, "wrong-physics-target", wrongPhysicsTarget);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"Physics timeline target must reference a Physics constraint or -1");

		Animation.PathConstraintPositionTimeline duplicateFirst =
			new Animation.PathConstraintPositionTimeline(1, 0, 0);
		duplicateFirst.setFrame(0, 0f, 1f);
		Animation.PathConstraintPositionTimeline duplicateSecond =
			new Animation.PathConstraintPositionTimeline(1, 0, 0);
		duplicateSecond.setFrame(0, 0.5f, 2f);
		setOnlyAnimation(data, "duplicate-path", duplicateFirst, duplicateSecond);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"duplicate Path property timelines are rejected");

		Animation.PhysicsConstraintResetTimeline empty = new Animation.PhysicsConstraintResetTimeline(0, 1);
		setOnlyAnimation(data, "empty-physics", empty);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"empty Physics timelines are rejected");

		Animation.PathConstraintSpacingTimeline unordered =
			new Animation.PathConstraintSpacingTimeline(2, 0, 0);
		unordered.setFrame(0, 0.5f, 1f);
		unordered.setFrame(1, 0.5f, 2f);
		setOnlyAnimation(data, "unordered-path", unordered);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"Path key times must be strictly increasing");

		Animation.PhysicsConstraintGravityTimeline nonFinite =
			new Animation.PhysicsConstraintGravityTimeline(1, 0, 1);
		nonFinite.setFrame(0, 0f, Float.NaN);
		setOnlyAnimation(data, "non-finite-physics", nonFinite);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc),
			"Physics key values must be finite");

		Animation.PhysicsConstraintMassTimeline zeroMass =
			new Animation.PhysicsConstraintMassTimeline(1, 0, 1);
		zeroMass.setFrame(0, 0f, 0f);
		setOnlyAnimation(data, "zero-physics-mass", zeroMass);
		assertThrows(IllegalArgumentException.class, () -> SkeletonExporter.toJson(doc),
			"Physics mass key values must be greater than zero");

		Animation.PhysicsConstraintResetTimeline duplicateReset =
			new Animation.PhysicsConstraintResetTimeline(2, -1);
		duplicateReset.setFrame(0, 0.25f);
		duplicateReset.setFrame(1, 0.25f);
		setOnlyAnimation(data, "unordered-reset", duplicateReset);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"Physics reset times must be strictly increasing");

		data.getConstraints().removeValue(physics, true);
		Animation.PhysicsConstraintResetTimeline targetlessReset =
			new Animation.PhysicsConstraintResetTimeline(1, -1);
		targetlessReset.setFrame(0, 0.25f);
		setOnlyAnimation(data, "targetless-global-reset", targetlessReset);
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(doc),
			"global Physics reset must have at least one Physics constraint target");
		data.getAnimations().clear();
	}

	private static void setOnlyAnimation(SkeletonData data, String name, Animation.Timeline... timelines) {
		data.getAnimations().clear();
		Animation animation = new Animation(name);
		Array<Animation.Timeline> values = new Array<>(true, timelines.length, Animation.Timeline[]::new);
		values.addAll(timelines);
		animation.setTimelines(values, new IntArray());
		data.getAnimations().add(animation);
	}

	private static Animation emptyAnimation(String name) {
		Animation animation = new Animation(name);
		animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		return animation;
	}

	private static SkeletonData baseData(String rootName, String childName) {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, rootName, null);
		BoneData child = new BoneData(1, childName, root);
		root.getSetupPose().setInherit(Inherit.normal);
		child.getSetupPose().setInherit(Inherit.onlyTranslation);
		data.getBones().add(root);
		data.getBones().add(child);
		return data;
	}

	private static SkeletonDocument document(SkeletonData data) {
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		return doc;
	}

	private static Sequence sequence(int count, int start, int digits, int setup) {
		Sequence sequence = new Sequence(count, true);
		sequence.setStart(start);
		sequence.setDigits(digits);
		sequence.setSetupIndex(setup);
		return sequence;
	}

	private static void assertSequence(Sequence expected, Sequence actual, String message) {
		assertTrue(actual != null, message + " exists");
		assertTrue(expected.hasPathSuffix() == actual.hasPathSuffix(), message + " path suffix");
		assertTrue(expected.getRegions().length == actual.getRegions().length, message + " count");
		assertTrue(expected.getStart() == actual.getStart(), message + " start");
		assertTrue(expected.getDigits() == actual.getDigits(), message + " digits");
		assertTrue(expected.getSetupIndex() == actual.getSetupIndex(), message + " setup");
	}

	private static void assertFloatArray(float[] expected, float[] actual, String message) {
		assertTrue(expected != null && actual != null && expected.length == actual.length,
			message + " length");
		for (int index = 0; index < expected.length; index++)
			assertNear(expected[index], actual[index], message + "[" + index + "]");
	}

	private static void assertIntArray(int[] expected, int[] actual, String message) {
		if (!java.util.Arrays.equals(expected, actual))
			throw new AssertionError(message + ": expected " + java.util.Arrays.toString(expected)
				+ ", got " + java.util.Arrays.toString(actual));
	}

	private static void assertShortArray(short[] expected, short[] actual, String message) {
		if (!java.util.Arrays.equals(expected, actual))
			throw new AssertionError(message + ": expected " + java.util.Arrays.toString(expected)
				+ ", got " + java.util.Arrays.toString(actual));
	}

	private static void assertColor(Color expected, Color actual, String message) {
		if (actual == null || !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEventValues(Event expected, Event actual, String message) {
		assertTrue(actual != null, message + " event");
		assertTrue(expected.getData().getName().equals(actual.getData().getName()), message + " name");
		assertTrue(expected.getInt() == actual.getInt(), message + " int");
		assertNear(expected.getFloat(), actual.getFloat(), message + " float");
		assertTrue(expected.getString().equals(actual.getString()), message + " string");
		if (expected.getData().getAudioPath() != null) {
			assertNear(expected.getVolume(), actual.getVolume(), message + " volume");
			assertNear(expected.getBalance(), actual.getBalance(), message + " balance");
		}
	}

	private static void setDarkColor(SlotData slot, Color color) {
		try {
			java.lang.reflect.Field field = slot.getSetupPose().getClass().getDeclaredField("darkColor");
			field.setAccessible(true);
			field.set(slot.getSetupPose(), color == null ? null : new Color(color));
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("unable to initialize slot dark color", error);
		}
	}

	private static void assertTimelineValue(Animation animation, Class<?> type, float expected, String message) {
		for (Animation.Timeline timeline : animation.getTimelines()) {
			if (timeline.getClass() != type) continue;
			float actual = timeline.getFrames()[timeline.getFrameEntries() + 1];
			if (Math.abs(expected - actual) > 0.0001f)
				throw new AssertionError(message + ": expected " + expected + ", got " + actual);
			return;
		}
		throw new AssertionError(message + ": timeline was not loaded");
	}

	private static void assertTimelineColor(Animation.RGBATimeline timeline, int frame, float time,
		float red, float green, float blue, float alpha) {
		int base = frame * timeline.getFrameEntries();
		float[] frames = timeline.getFrames();
		assertNear(time, frames[base], "RGBA time");
		assertNear(red, frames[base + 1], "RGBA red");
		assertNear(green, frames[base + 2], "RGBA green");
		assertNear(blue, frames[base + 3], "RGBA blue");
		assertNear(alpha, frames[base + 4], "RGBA alpha");
	}

	private static void assertTwoColorTimeline(Animation.Timeline timeline, int frame, float time,
		float lightRed, float lightGreen, float lightBlue, float lightAlpha,
		float darkRed, float darkGreen, float darkBlue) {
		int base = frame * timeline.getFrameEntries();
		float[] frames = timeline.getFrames();
		assertNear(time, frames[base], "two color time");
		assertNear(lightRed, frames[base + 1], "two color light red");
		assertNear(lightGreen, frames[base + 2], "two color light green");
		assertNear(lightBlue, frames[base + 3], "two color light blue");
		int darkOffset;
		if (timeline instanceof Animation.RGBA2Timeline) {
			assertNear(lightAlpha, frames[base + 4], "two color light alpha");
			darkOffset = 5;
		} else {
			darkOffset = 4;
		}
		assertNear(darkRed, frames[base + darkOffset], "two color dark red");
		assertNear(darkGreen, frames[base + darkOffset + 1], "two color dark green");
		assertNear(darkBlue, frames[base + darkOffset + 2], "two color dark blue");
	}

	private static void assertTimeline2(Animation.BoneTimeline2 timeline, int frame, float time,
		float value1, float value2, String message) {
		assertTrue(timeline != null, message + " timeline");
		int base = frame * timeline.getFrameEntries();
		float[] frames = timeline.getFrames();
		assertNear(time, frames[base], message + " time");
		assertNear(value1, frames[base + 1], message + " value 1");
		assertNear(value2, frames[base + 2], message + " value 2");
	}

	private static void assertTimelineRgb(Animation.RGBTimeline timeline, int frame, float time,
		float red, float green, float blue) {
		int base = frame * timeline.getFrameEntries();
		float[] frames = timeline.getFrames();
		assertNear(time, frames[base], "RGB time");
		assertNear(red, frames[base + 1], "RGB red");
		assertNear(green, frames[base + 2], "RGB green");
		assertNear(blue, frames[base + 3], "RGB blue");
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static <T extends Animation.Timeline> T findTimeline(Animation animation, Class<T> type) {
		for (Animation.Timeline timeline : animation.getTimelines()) {
			if (timeline.getClass() == type) return type.cast(timeline);
		}
		return null;
	}

	private static <T extends Animation.Timeline> T findConstraintTimeline(Animation animation, Class<T> type,
		int constraintIndex) {
		for (Animation.Timeline timeline : animation.getTimelines()) {
			if (timeline.getClass() == type && timeline instanceof Animation.ConstraintTimeline constraint
				&& constraint.getConstraintIndex() == constraintIndex) return type.cast(timeline);
		}
		return null;
	}

	private static <T extends Animation.Timeline> void assertConstraintTimelineValue(Animation animation,
		Class<T> type, int constraintIndex, int frame, float expected, String message) {
		T timeline = findConstraintTimeline(animation, type, constraintIndex);
		assertTrue(timeline != null, message + " exists");
		int base = frame * timeline.getFrameEntries();
		assertNear(expected, timeline.getFrames()[base + 1], message + " value");
	}

	private static Animation.DeformTimeline findDeformTimeline(Animation animation, VertexAttachment attachment) {
		for (Animation.Timeline timeline : animation.getTimelines())
			if (timeline instanceof Animation.DeformTimeline deform && deform.getAttachment() == attachment) return deform;
		return null;
	}

	private static Animation.SequenceTimeline findSequenceTimeline(Animation animation,
		com.esotericsoftware.spine.attachments.Attachment attachment) {
		for (Animation.Timeline timeline : animation.getTimelines())
			if (timeline instanceof Animation.SequenceTimeline sequence && sequence.getAttachment() == attachment) return sequence;
		return null;
	}

	private static void assertThrows(Class<? extends Throwable> type, Runnable action, String message) {
		try {
			action.run();
		} catch (Throwable error) {
			if (type.isInstance(error)) return;
			throw new AssertionError(message + ": wrong exception " + error, error);
		}
		throw new AssertionError(message + ": expected " + type.getSimpleName());
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private SkeletonExporterTest() {}
}
