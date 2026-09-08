package com.openspine.io;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.JsonReader;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.TransformConstraintData.FromRotate;
import com.esotericsoftware.spine.TransformConstraintData.FromX;
import com.esotericsoftware.spine.TransformConstraintData.ToShearY;
import com.esotericsoftware.spine.TransformConstraintData.ToX;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.Sequence.SequenceMode;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Headless compatibility tests for Spine 4.3 binary export. */
public final class SkeletonBinaryExporterTest {
	private static final int RICH_TIMELINE_COUNT = 37;

	public static void main(String[] args) throws Exception {
		roundTripsExampleThroughOfficialReader();
		roundTripsRichAttachments();
		roundTripsWeightedStandardAttachmentsAndDeform();
		roundTripsMeshTimelineSlotsAndIndependentLinkedDeform();
		roundTripsRepairedMeshTopologyAndDeform();
		roundTripsRichConstraintSetup();
		roundTripsRepresentablePhysicsScaleModes();
		roundTripsReorderedMixedConstraints();
		roundTripsRichEventSetup();
		roundTripsRichTimelinePrefixes();
		roundTripsRichSkeletonData();
		roundTripsUnicodeAndEditorMetadata();
		normalizesVersionAndPreflightsJsonValues();
		rejectsPhysicsScaleModeCollisions();
		rejectsUnrepresentableDefaultSkinData();
		rejectsSharedReferencedAttachmentIdentity();
		rejectsNestedAndCyclicLinkedMeshSources();
		atomicallyReplacesBinaryFiles();
		System.out.println("ALL SKELETON BINARY EXPORTER TESTS PASSED");
	}

	private static void roundTripsUnicodeAndEditorMetadata() throws Exception {
		SkeletonData data = new SkeletonData();
		data.setVersion("4.3.5");
		data.setReferenceScale(100f);
		data.setFps(30f);
		data.setImagesPath("图片/角色\uD83D\uDE00/");
		data.setAudioPath("音频/提示\uD840\uDC00/");

		BoneData root = new BoneData(0, "根骨\uD83D\uDE00", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		root.getColor().set(0.2f, 0.4f, 0.6f, 0.8f);
		root.setIcon("图标/根\uD840\uDC00");
		root.setIconSize(1.5f);
		root.setIconRotation(-18f);
		root.setVisible(false);
		data.getBones().add(root);

		SlotData slot = new SlotData(0, "槽位\uD83D\uDE00", root);
		slot.getSetupPose().getColor().set(0.7f, 0.5f, 0.3f, 0.9f);
		setDarkColor(slot, new Color(0.1f, 0.2f, 0.3f, 1f));
		slot.setAttachmentName("附件\uD83D\uDE00");
		slot.setBlendMode(BlendMode.screen);
		slot.setVisible(false);
		data.getSlots().add(slot);

		Skin defaultSkin = new Skin("default");
		RegionAttachment region = new RegionAttachment("区域\uD83D\uDE00", new Sequence(1, false));
		region.setPath("图片/区域\uD840\uDC00");
		region.setWidth(32f);
		region.setHeight(24f);
		region.getColor().set(0.9f, 0.4f, 0.2f, 0.75f);
		defaultSkin.setAttachment(0, "附件\uD83D\uDE00", region);
		Skin alternate = new Skin("皮肤\uD83D\uDE00");
		alternate.getColor().set(0.25f, 0.5f, 0.75f, 0.6f);
		data.getSkins().add(defaultSkin, alternate);
		data.setDefaultSkin(defaultSkin);

		EventData event = new EventData("事件\uD83D\uDE00");
		event.getSetupPose().setString("载荷\uD840\uDC00");
		event.setAudioPath("音频/点击\uD83D\uDE00.wav");
		event.getSetupPose().setVolume(0.65f);
		event.getSetupPose().setBalance(-0.35f);
		data.getEvents().add(event);
		Animation animation = animation("动画\uD83D\uDE00");
		animation.getColor().set(0.35f, 0.65f, 0.15f, 0.85f);
		data.getAnimations().add(animation);

		SkeletonData loaded = read(SkeletonBinaryExporter.toBytes(document(data)));
		assertTrue(data.getImagesPath().equals(loaded.getImagesPath()), "Unicode images path round trip");
		assertTrue(data.getAudioPath().equals(loaded.getAudioPath()), "Unicode audio path round trip");
		BoneData loadedRoot = loaded.findBone(root.getName());
		assertTrue(loadedRoot != null && root.getIcon().equals(loadedRoot.getIcon()),
			"Unicode bone name and icon round trip");
		assertTrue(Color.rgba8888(root.getColor()) == Color.rgba8888(loadedRoot.getColor()),
			"bone editor color round trip");
		assertNear(root.getIconSize(), loadedRoot.getIconSize(), "bone icon size");
		assertNear(root.getIconRotation(), loadedRoot.getIconRotation(), "bone icon rotation");
		assertTrue(!loadedRoot.getVisible(), "bone visibility round trip");
		SlotData loadedSlot = loaded.findSlot(slot.getName());
		assertTrue(loadedSlot != null && loadedSlot.getBlendMode() == BlendMode.screen && !loadedSlot.getVisible(),
			"Unicode slot and editor flags round trip");
		assertTrue(Color.rgba8888(slot.getSetupPose().getColor())
			== Color.rgba8888(loadedSlot.getSetupPose().getColor()), "slot light color round trip");
		assertTrue(Color.rgb888(slot.getSetupPose().getDarkColor())
			== Color.rgb888(loadedSlot.getSetupPose().getDarkColor()), "slot dark color round trip");
		RegionAttachment loadedRegion = (RegionAttachment)loaded.getDefaultSkin()
			.getAttachment(0, slot.getAttachmentName());
		assertTrue(region.getName().equals(loadedRegion.getName()) && region.getPath().equals(loadedRegion.getPath()),
			"Unicode attachment name and path round trip");
		assertTrue(Color.rgba8888(region.getColor()) == Color.rgba8888(loadedRegion.getColor()),
			"attachment color round trip");
		assertTrue(Color.rgba8888(alternate.getColor())
			== Color.rgba8888(loaded.findSkin(alternate.getName()).getColor()), "skin color round trip");
		EventData loadedEvent = loaded.findEvent(event.getName());
		assertTrue(loadedEvent != null && event.getSetupPose().getString().equals(loadedEvent.getSetupPose().getString())
			&& event.getAudioPath().equals(loadedEvent.getAudioPath()), "Unicode event payload round trip");
		assertNear(event.getSetupPose().getVolume(), loadedEvent.getSetupPose().getVolume(), "event volume");
		assertNear(event.getSetupPose().getBalance(), loadedEvent.getSetupPose().getBalance(), "event balance");
		Animation loadedAnimation = loaded.findAnimation(animation.getName());
		assertTrue(loadedAnimation != null
			&& Color.rgba8888(animation.getColor()) == Color.rgba8888(loadedAnimation.getColor()),
			"Unicode animation name and editor color round trip");
	}

	private static void normalizesVersionAndPreflightsJsonValues() throws Exception {
		SkeletonData data = minimalData();
		data.setVersion("4.2.99");
		SkeletonData loaded = read(SkeletonBinaryExporter.toBytes(document(data)));
		assertTrue(SkeletonExporter.EXPORT_VERSION.equals(loaded.getVersion()),
			"binary header declares the format version produced by OpenSpine");

		data.getBones().first().getSetupPose().setX(Float.NaN);
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonBinaryExporter.toBytes(document(data)),
			"binary export rejects non-finite setup values during preflight");
		data.getBones().first().getSetupPose().setX(0f);

		SlotData slot = new SlotData(0, "box-slot", data.getBones().first());
		slot.setBlendMode(BlendMode.normal);
		data.getSlots().add(slot);
		Skin skin = new Skin("default");
		BoundingBoxAttachment box = new BoundingBoxAttachment("box");
		box.setWorldVerticesLength(4);
		box.setVertices(new float[] {0f, 0f});
		skin.setAttachment(0, "box", box);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);
		assertThrows(IllegalStateException.class,
			() -> SkeletonBinaryExporter.toBytes(document(data)),
			"binary export rejects malformed vertex arrays during preflight");
	}

	private static void roundTripsRichTimelinePrefixes() throws Exception {
		for (int count = 1; count <= RICH_TIMELINE_COUNT; count++) {
			SkeletonData source = richData();
			Animation controller = source.findAnimation("controller");
			Array<Animation.Timeline> subset = new Array<>(true, count, Animation.Timeline[]::new);
			for (int index = 0; index < count; index++) subset.add(controller.getTimelines().get(index));
			controller.setTimelines(subset, new IntArray());
			try {
				read(SkeletonBinaryExporter.toBytes(document(source)));
			} catch (RuntimeException error) {
				throw new AssertionError("rich timeline prefix failed at count " + count + " ("
					+ subset.peek().getClass().getSimpleName() + ")", error);
			}
		}
	}

	private static void roundTripsRichEventSetup() throws Exception {
		SkeletonData source = richData();
		source.getAnimations().truncate(1);
		SkeletonData loaded = read(SkeletonBinaryExporter.toBytes(document(source)));
		assertTrue(loaded.getEvents().size == 1 && loaded.getAnimations().size == 1,
			"rich event setup binary round trip");
	}

	private static void roundTripsRichConstraintSetup() throws Exception {
		SkeletonData source = richData();
		source.getAnimations().truncate(1);
		source.getEvents().clear();
		SkeletonData loaded = read(SkeletonBinaryExporter.toBytes(document(source)));
		assertTrue(loaded.getConstraints().size == 5 && loaded.getAnimations().size == 1,
			"rich constraint setup binary round trip");
		TransformConstraintData transform = loaded.findConstraint("transform", TransformConstraintData.class);
		assertTrue(transform != null && transform.getProperties().size == 1,
			"Transform property source binary round trip");
		assertNear(2f, transform.getProperties().first().offset,
			"Transform source property offset binary round trip");
		assertTrue(transform.getProperties().first().to.size == 2
			&& transform.getProperties().first().to.first() instanceof ToX
			&& transform.getProperties().first().to.get(1) instanceof ToShearY,
			"multiple Transform targets binary round trip");
		assertNear(-3f, transform.getProperties().first().to.get(1).offset,
			"second Transform target offset binary round trip");
		assertNear(20f, transform.getProperties().first().to.get(1).max,
			"second Transform target max binary round trip");
		assertNear(-0.25f, transform.getProperties().first().to.get(1).scale,
			"second Transform target scale binary round trip");
	}

	private static void roundTripsRepresentablePhysicsScaleModes() throws Exception {
		SkeletonData source = richData();
		PhysicsConstraintData physics = source.findConstraint("physics", PhysicsConstraintData.class);
		physics.setScaleYMode(ScaleYMode.none);
		physics.setScaleX(1.5f);
		PhysicsConstraintData loaded = read(SkeletonBinaryExporter.toBytes(document(source)))
			.findConstraint("physics", PhysicsConstraintData.class);
		assertTrue(loaded.getScaleYMode() == ScaleYMode.none,
			"nonnegative Physics Scale X keeps the none mode through binary reload");
		assertNear(1.5f, loaded.getScaleX(), "Physics none-mode Scale X binary round trip");

		physics.setScaleYMode(ScaleYMode.uniform);
		physics.setScaleX(1f);
		loaded = read(SkeletonBinaryExporter.toBytes(document(source)))
			.findConstraint("physics", PhysicsConstraintData.class);
		assertTrue(loaded.getScaleYMode() == ScaleYMode.uniform,
			"Physics uniform mode survives its binary boundary encoding");
		assertNear(1f, loaded.getScaleX(), "Physics uniform-mode Scale X binary round trip");

		physics.setScaleYMode(ScaleYMode.volume);
		physics.setScaleX(1.5f);
		loaded = read(SkeletonBinaryExporter.toBytes(document(source)))
			.findConstraint("physics", PhysicsConstraintData.class);
		assertTrue(loaded.getScaleYMode() == ScaleYMode.volume,
			"Physics volume mode survives binary reload");
		assertNear(1.5f, loaded.getScaleX(), "Physics volume-mode Scale X binary round trip");
	}

	private static void rejectsPhysicsScaleModeCollisions() {
		SkeletonData source = richData();
		PhysicsConstraintData physics = source.findConstraint("physics", PhysicsConstraintData.class);

		physics.setScaleYMode(ScaleYMode.none);
		physics.setScaleX(-0.25f);
		assertThrows(IllegalArgumentException.class, () -> SkeletonBinaryExporter.toBytes(document(source)),
			"binary export rejects a none-mode Scale X that the reader would decode as uniform");

		physics.setScaleYMode(ScaleYMode.uniform);
		physics.setScaleX(1.5f);
		assertThrows(IllegalArgumentException.class, () -> SkeletonBinaryExporter.toBytes(document(source)),
			"binary export rejects a uniform Scale X that the reader would decode as volume");

		physics.setScaleYMode(ScaleYMode.volume);
		physics.setScaleX(0f);
		assertThrows(IllegalArgumentException.class, () -> SkeletonBinaryExporter.toBytes(document(source)),
			"binary export rejects a volume Scale X that the reader would decode as uniform");
	}

	private static void roundTripsReorderedMixedConstraints() throws Exception {
		SkeletonData source = richData();
		SkeletonDocument document = document(source);
		SkeletonEditor editor = new SkeletonEditor(document);
		SliderData slider = source.findConstraint("slider", SliderData.class);
		assertTrue(slider != null && editor.moveConstraint(slider, 1),
			"editor can reorder the rich mixed constraint fixture before binary export");

		SkeletonData loaded = read(SkeletonBinaryExporter.toBytes(document));
		assertTrue("ik".equals(loaded.getConstraints().get(0).getName())
			&& "slider".equals(loaded.getConstraints().get(1).getName())
			&& "transform".equals(loaded.getConstraints().get(2).getName())
			&& "path-follow".equals(loaded.getConstraints().get(3).getName())
			&& "physics".equals(loaded.getConstraints().get(4).getName()),
			"unified mixed constraint execution order binary round trip");
		Animation controller = loaded.findAnimation("controller");
		assertTrue(findConstraintTimeline(controller, Animation.IkConstraintTimeline.class, 0) != null
			&& findConstraintTimeline(controller, Animation.SliderTimeline.class, 1) != null
			&& findConstraintTimeline(controller, Animation.TransformConstraintTimeline.class, 2) != null
			&& findConstraintTimeline(controller, Animation.PathConstraintMixTimeline.class, 3) != null
			&& findConstraintTimeline(controller, Animation.PhysicsConstraintInertiaTimeline.class, 4) != null,
			"binary reload resolves every reordered constraint timeline to its logical target");
		assertTrue(findConstraintTimeline(controller, Animation.PhysicsConstraintGravityTimeline.class, -1) != null,
			"binary reorder keeps global Physics timeline targets unchanged");
		Skin variants = loaded.findSkin("variants");
		assertTrue(variants != null && variants.getConstraints().contains(loaded.getConstraints().get(1), true)
			&& variants.getConstraints().contains(loaded.getConstraints().get(4), true),
			"binary reorder preserves skin activation references across constraint types");
	}

	private static void roundTripsRichAttachments() throws Exception {
		SkeletonData source = richData();
		source.getConstraints().clear();
		source.getAnimations().clear();
		source.getEvents().clear();
		for (Skin skin : source.getSkins()) {
			skin.getBones().clear();
			skin.getConstraints().clear();
		}
		SkeletonData loaded = read(SkeletonBinaryExporter.toBytes(document(source)));
		assertTrue(loaded.findSkin("default").getAttachment(0, "mesh") instanceof MeshAttachment,
			"rich attachment-only binary round trip");
	}

	private static void roundTripsWeightedStandardAttachmentsAndDeform() throws Exception {
		SkeletonData data = new SkeletonData();
		data.setVersion("4.3.5");
		data.setReferenceScale(100f);
		data.setFps(30f);
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		child.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root, child);
		SlotData boxSlot = new SlotData(0, "box-slot", child);
		SlotData pathSlot = new SlotData(1, "path-slot", root);
		SlotData clipSlot = new SlotData(2, "clip-slot", child);
		for (SlotData slot : new SlotData[] {boxSlot, pathSlot, clipSlot}) {
			slot.setBlendMode(BlendMode.normal);
			setDarkColor(slot, Color.BLACK);
		}
		boxSlot.setAttachmentName("weighted-box");
		pathSlot.setAttachmentName("weighted-path");
		clipSlot.setAttachmentName("weighted-clip");
		data.getSlots().add(boxSlot, pathSlot, clipSlot);

		BoundingBoxAttachment box = new BoundingBoxAttachment("weighted-box");
		box.setWorldVerticesLength(6);
		box.setBones(new int[] {2, 0, 1, 1, 1, 1, 0});
		box.setVertices(new float[] {-2f, -2f, 0.25f, -2f, -2f, 0.75f,
			2f, -2f, 1f, 0f, 2f, 1f});
		PathAttachment path = new PathAttachment("weighted-path");
		path.setWorldVerticesLength(12);
		path.setBones(new int[] {1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1});
		path.setVertices(new float[] {0f, 0f, 1f, 1f, 1f, 1f, 2f, 1f, 1f,
			3f, 0f, 1f, 4f, -1f, 1f, 5f, 0f, 1f});
		path.setClosed(true);
		path.setConstantSpeed(false);
		path.setLengths(new float[] {3f, 6f});
		ClippingAttachment clipping = new ClippingAttachment("weighted-clip");
		clipping.setWorldVerticesLength(8);
		clipping.setBones(new int[] {1, 0, 1, 1, 1, 0, 1, 1});
		clipping.setVertices(new float[] {-3f, -3f, 1f, 3f, -3f, 1f,
			3f, 3f, 1f, -3f, 3f, 1f});
		clipping.setEndSlot(clipSlot);
		clipping.setConvex(true);

		Skin skin = new Skin("default");
		skin.setAttachment(0, "weighted-box", box);
		skin.setAttachment(1, "weighted-path", path);
		skin.setAttachment(2, "weighted-clip", clipping);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);

		Animation.DeformTimeline boxDeform = new Animation.DeformTimeline(1, 0, 0, box);
		boxDeform.setFrame(0, 0.25f, new float[] {1f, -1f, 2f, -2f, 3f, -3f, 4f, -4f});
		Animation.DeformTimeline pathDeform = new Animation.DeformTimeline(1, 0, 1, path);
		pathDeform.setFrame(0, 0.5f, new float[] {1f, 2f, 3f, 4f, 5f, 6f,
			7f, 8f, 9f, 10f, 11f, 12f});
		Animation.DeformTimeline clipDeform = new Animation.DeformTimeline(1, 0, 2, clipping);
		clipDeform.setFrame(0, 0.75f, new float[] {-1f, 1f, -2f, 2f, -3f, 3f, -4f, 4f});
		data.getAnimations().add(animation("standard-deform", boxDeform, pathDeform, clipDeform));

		SkeletonData loaded = read(SkeletonBinaryExporter.toBytes(document(data)));
		Skin loadedSkin = loaded.getDefaultSkin();
		BoundingBoxAttachment loadedBox = (BoundingBoxAttachment)loadedSkin.getAttachment(0, "weighted-box");
		PathAttachment loadedPath = (PathAttachment)loadedSkin.getAttachment(1, "weighted-path");
		ClippingAttachment loadedClip = (ClippingAttachment)loadedSkin.getAttachment(2, "weighted-clip");
		assertTrue(java.util.Arrays.equals(box.getBones(), loadedBox.getBones())
			&& java.util.Arrays.equals(box.getVertices(), loadedBox.getVertices()),
			"weighted Bounding Box data round trip");
		assertTrue(java.util.Arrays.equals(path.getBones(), loadedPath.getBones())
			&& java.util.Arrays.equals(path.getVertices(), loadedPath.getVertices())
			&& java.util.Arrays.equals(path.getLengths(), loadedPath.getLengths()),
			"weighted Path data round trip");
		assertTrue(java.util.Arrays.equals(clipping.getBones(), loadedClip.getBones())
			&& java.util.Arrays.equals(clipping.getVertices(), loadedClip.getVertices())
			&& loadedClip.getEndSlot() == loaded.findSlot("clip-slot"),
			"weighted Clipping data round trip");
		Animation loadedAnimation = loaded.findAnimation("standard-deform");
		assertDeformFrame(loadedAnimation, loadedBox, boxDeform.getVertices()[0], "Bounding Box Deform");
		assertDeformFrame(loadedAnimation, loadedPath, pathDeform.getVertices()[0], "Path Deform");
		assertDeformFrame(loadedAnimation, loadedClip, clipDeform.getVertices()[0], "Clipping Deform");
	}

	private static void roundTripsMeshTimelineSlotsAndIndependentLinkedDeform() throws Exception {
		SkeletonData data = minimalData();
		SlotData sourceSlot = new SlotData(0, "source-slot", data.getBones().first());
		SlotData linkedSlot = new SlotData(1, "linked-slot", data.getBones().first());
		sourceSlot.setBlendMode(BlendMode.normal);
		linkedSlot.setBlendMode(BlendMode.normal);
		data.getSlots().add(sourceSlot, linkedSlot);

		Sequence sequence = new Sequence(3, true);
		sequence.setStart(4);
		sequence.setDigits(2);
		sequence.setSetupIndex(1);
		MeshAttachment source = new MeshAttachment("source", sequence);
		source.setPath("images/source");
		source.setWorldVerticesLength(6);
		source.setVertices(new float[] {0f, 0f, 10f, 0f, 0f, 10f});
		source.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0f, 1f});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.setTimelineSlots(new int[] {1});
		source.setWidth(10f);
		source.setHeight(10f);
		source.getColor().set(0.2f, 0.5f, 0.8f, 0.7f);

		Skin defaultSkin = new Skin("default");
		defaultSkin.setAttachment(0, "source", source);
		Skin variants = new Skin("variants");
		MeshAttachment inherited = source.newLinkedMesh();
		inherited.setPath("images/inherited");
		variants.setAttachment(1, "inherited", inherited);
		MeshAttachment independent = source.newLinkedMesh();
		independent.setPath("images/independent");
		independent.setTimelineAttachment(independent);
		independent.getColor().set(0.9f, 0.3f, 0.1f, 0.6f);
		variants.setAttachment(1, "independent", independent);
		data.getSkins().add(defaultSkin, variants);
		data.setDefaultSkin(defaultSkin);

		float[] changed = source.getVertices().clone();
		changed[0] += 3f;
		changed[5] -= 2f;
		Animation.DeformTimeline deform = new Animation.DeformTimeline(1, 0, 1, independent);
		deform.setFrame(0, 0.25f, changed);
		data.getAnimations().add(animation("independent-deform", deform));

		SkeletonData loaded = read(SkeletonBinaryExporter.toBytes(document(data)));
		MeshAttachment loadedSource = (MeshAttachment)loaded.getDefaultSkin().getAttachment(0, "source");
		Skin loadedVariants = loaded.findSkin("variants");
		MeshAttachment loadedInherited = (MeshAttachment)loadedVariants.getAttachment(1, "inherited");
		MeshAttachment loadedIndependent = (MeshAttachment)loadedVariants.getAttachment(1, "independent");
		assertTrue(java.util.Arrays.equals(new int[] {1}, loadedSource.getTimelineSlots()),
			"binary Mesh timeline slots round trip");
		assertTrue(loadedInherited.getSourceMesh() == loadedSource
			&& loadedInherited.getTimelineAttachment() == loadedSource,
			"cross-slot Linked Mesh timeline inheritance round trip");
		assertTrue(loadedIndependent.getSourceMesh() == loadedSource
			&& loadedIndependent.getTimelineAttachment() == loadedIndependent,
			"independent Linked Mesh timeline ownership round trip");
		assertTrue(loadedSource.getSequence().getRegions().length == 3
			&& loadedSource.getSequence().getStart() == 4
			&& loadedSource.getSequence().getDigits() == 2
			&& loadedSource.getSequence().getSetupIndex() == 1,
			"Mesh Sequence setup round trip");
		assertTrue(Color.rgba8888(source.getColor()) == Color.rgba8888(loadedSource.getColor())
			&& Color.rgba8888(independent.getColor()) == Color.rgba8888(loadedIndependent.getColor()),
			"source and Linked Mesh colors round trip");
		Animation.DeformTimeline loadedDeform = findTimeline(loaded.findAnimation("independent-deform"),
			Animation.DeformTimeline.class);
		assertTrue(loadedDeform != null && loadedDeform.getAttachment() == loadedIndependent
			&& java.util.Arrays.equals(changed, loadedDeform.getVertices()[0]),
			"independent Linked Mesh Deform target and values round trip");

		SkeletonData jsonLoaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(
			new JsonReader().parse(SkeletonExporter.toJson(document(data))));
		MeshAttachment jsonSource = (MeshAttachment)jsonLoaded.getDefaultSkin().getAttachment(0, "source");
		assertTrue(java.util.Arrays.equals(new int[] {1}, jsonSource.getTimelineSlots()),
			"JSON reconstructs timeline slots from inherited Linked Mesh entries");

		source.setTimelineSlots(new int[] {0});
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(document(data)),
			"JSON rejects Mesh timeline slots it cannot reconstruct");
		SkeletonData binaryOnly = read(SkeletonBinaryExporter.toBytes(document(data)));
		MeshAttachment binaryOnlySource = (MeshAttachment)binaryOnly.getDefaultSkin().getAttachment(0, "source");
		assertTrue(java.util.Arrays.equals(new int[] {0}, binaryOnlySource.getTimelineSlots()),
			"binary export preserves timeline slots that JSON cannot represent");

		independent.setTimelineSlots(new int[] {0});
		assertThrows(IllegalArgumentException.class, () -> SkeletonBinaryExporter.toBytes(document(data)),
			"binary export rejects timeline slots on a Linked Mesh instead of dropping them");
	}

	private static void roundTripsRepairedMeshTopologyAndDeform() throws Exception {
		SkeletonData data = minimalData();
		SlotData slot = new SlotData(0, "mesh-slot", data.getBones().first());
		slot.setBlendMode(BlendMode.normal);
		slot.setAttachmentName("mesh");
		data.getSlots().add(slot);
		Skin skin = new Skin("default");
		MeshAttachment mesh = new MeshAttachment("mesh", new Sequence(1, false));
		mesh.setPath("mesh");
		mesh.setWorldVerticesLength(10);
		mesh.setVertices(new float[] {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f, 6f, 4f});
		mesh.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0.6f, 0.4f});
		mesh.setTriangles(new short[] {0, 1, 4, 1, 2, 4, 2, 0, 4, 0, 2, 3});
		mesh.setHullLength(8);
		mesh.setWidth(10f);
		mesh.setHeight(10f);
		skin.setAttachment(0, "mesh", mesh);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);

		Animation.DeformTimeline deform = new Animation.DeformTimeline(1, 0, 0, mesh);
		deform.setFrame(0, 0.25f, new float[] {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f});
		data.getAnimations().add(animation("mesh-deform", deform));
		SkeletonDocument document = document(data);
		SkeletonEditor editor = new SkeletonEditor(document);

		assertTrue(editor.removeMeshVerticesWithDeform(mesh, new int[] {4}),
			"internal Mesh vertex removal migrates Deform before topology repair");
		assertTrue(mesh.getTriangles().length == 3 && deform.getVertices()[0].length == 8,
			"vertex removal leaves an editable hole and removes the matching Deform values");
		SkeletonData jsonHole = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(
			new JsonReader().parse(SkeletonExporter.toJson(document)));
		MeshAttachment jsonHoleMesh = (MeshAttachment)jsonHole.getDefaultSkin().getAttachment(0, "mesh");
		assertTrue(jsonHoleMesh.getTriangles().length == 3,
			"JSON preserves an incomplete topology while the user repairs it");
		assertThrows(IllegalArgumentException.class, () -> SkeletonBinaryExporter.toBytes(document),
			"binary export rejects an incomplete topology instead of misreading following fields");

		SkeletonEditor.MeshGeometryState repaired = SkeletonEditor.addMeshTriangle(
			SkeletonEditor.meshGeometryState(mesh), 0, 1, 2);
		assertTrue(editor.updateMeshGeometry(mesh, repaired), "the missing face can be restored without rebasing Deform");
		float[] expectedDeform = {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f};
		assertTrue(java.util.Arrays.equals(expectedDeform, deform.getVertices()[0]),
			"topology repair preserves migrated Deform values");

		SkeletonData binaryLoaded = read(SkeletonBinaryExporter.toBytes(document));
		MeshAttachment binaryMesh = (MeshAttachment)binaryLoaded.getDefaultSkin().getAttachment(0, "mesh");
		assertTrue(java.util.Arrays.equals(mesh.getTriangles(), binaryMesh.getTriangles()),
			"official binary reader preserves repaired Mesh topology");
		assertDeformFrame(binaryLoaded.findAnimation("mesh-deform"), binaryMesh, expectedDeform,
			"repaired Mesh binary Deform");

		SkeletonData jsonLoaded = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(
			new JsonReader().parse(SkeletonExporter.toJson(document)));
		MeshAttachment jsonMesh = (MeshAttachment)jsonLoaded.getDefaultSkin().getAttachment(0, "mesh");
		assertTrue(java.util.Arrays.equals(mesh.getTriangles(), jsonMesh.getTriangles()),
			"official JSON reader preserves repaired Mesh topology");
		assertDeformFrame(jsonLoaded.findAnimation("mesh-deform"), jsonMesh, expectedDeform,
			"repaired Mesh JSON Deform");
	}

	private static void roundTripsRichSkeletonData() throws Exception {
		SkeletonData source = richData();
		SkeletonData loaded = read(SkeletonBinaryExporter.toBytes(document(source)));

		assertTrue(loaded.getBones().size == 2 && loaded.getSlots().size == 2, "rich skeleton structure");
		assertTrue(loaded.getSkins().size == 2, "default and linked skins round trip");
		Skin loadedDefault = loaded.findSkin("default");
		Skin loadedLinkedSkin = loaded.findSkin("variants");
		MeshAttachment loadedMesh = (MeshAttachment)loadedDefault.getAttachment(0, "mesh");
		MeshAttachment loadedWeighted = (MeshAttachment)loadedDefault.getAttachment(0, "weighted");
		MeshAttachment loadedLinked = (MeshAttachment)loadedLinkedSkin.getAttachment(0, "linked");
		RegionAttachment loadedRegion = (RegionAttachment)loadedDefault.getAttachment(0, "region");
		BoundingBoxAttachment loadedBox = (BoundingBoxAttachment)loadedDefault.getAttachment(0, "box");
		PathAttachment loadedPath = (PathAttachment)loadedDefault.getAttachment(1, "path");
		PointAttachment loadedPoint = (PointAttachment)loadedDefault.getAttachment(1, "point");
		ClippingAttachment loadedClip = (ClippingAttachment)loadedDefault.getAttachment(1, "clip");
		assertTrue(loadedMesh.getTriangles().length == 6 && loadedMesh.getHullLength() == 8,
			"mesh topology round trip");
		assertTrue(loadedWeighted.getBones() != null && loadedWeighted.getVertices().length == 9,
			"weighted mesh round trip");
		assertTrue(loadedLinked.getSourceMesh() == loadedMesh
			&& loadedLinked.getTimelineAttachment() == loadedMesh, "linked mesh inheritance round trip");
		assertTrue("region-actual".equals(loadedRegion.getName())
			&& "images/region-frame".equals(loadedRegion.getPath()), "Region name and path round trip");
		assertNear(3f, loadedRegion.getX(), "Region X");
		assertNear(-4f, loadedRegion.getY(), "Region Y");
		assertNear(1.25f, loadedRegion.getScaleX(), "Region scale X");
		assertNear(0.75f, loadedRegion.getScaleY(), "Region scale Y");
		assertNear(18f, loadedRegion.getRotation(), "Region rotation");
		assertNear(10f, loadedRegion.getWidth(), "Region width");
		assertNear(12f, loadedRegion.getHeight(), "Region height");
		assertTrue(loadedRegion.getSequence().getRegions().length == 2
			&& loadedRegion.getSequence().getStart() == 7
			&& loadedRegion.getSequence().getDigits() == 3
			&& loadedRegion.getSequence().getSetupIndex() == 1, "Region Sequence setup round trip");
		assertTrue(Color.rgba8888(loadedRegion.getColor()) == Color.rgba8888(0.3f, 0.6f, 0.9f, 0.8f),
			"Region color round trip");
		assertTrue("box-actual".equals(loadedBox.getName())
			&& Color.rgba8888(loadedBox.getColor()) == Color.rgba8888(0.2f, 0.8f, 0.4f, 0.7f),
			"Bounding Box name and color round trip");
		assertTrue("path-actual".equals(loadedPath.getName()) && loadedPath.getClosed()
			&& !loadedPath.getConstantSpeed() && java.util.Arrays.equals(new float[] {3f, 6f}, loadedPath.getLengths())
			&& Color.rgba8888(loadedPath.getColor()) == Color.rgba8888(0.9f, 0.4f, 0.2f, 0.65f),
			"Path name, options, lengths, and color round trip");
		assertTrue("point-actual".equals(loadedPoint.getName()), "Point name round trip");
		assertNear(2f, loadedPoint.getX(), "Point X");
		assertNear(3f, loadedPoint.getY(), "Point Y");
		assertNear(45f, loadedPoint.getRotation(), "Point rotation");
		assertTrue(Color.rgba8888(loadedPoint.getColor()) == Color.rgba8888(0.7f, 0.3f, 0.9f, 0.6f),
			"Point color round trip");
		assertTrue("clip-actual".equals(loadedClip.getName()) && loadedClip.getEndSlot() == loaded.findSlot("guide")
			&& loadedClip.getConvex() && loadedClip.getInverse()
			&& Color.rgba8888(loadedClip.getColor()) == Color.rgba8888(0.1f, 0.7f, 0.8f, 0.55f),
			"Clipping name, end slot, options, and color round trip");

		assertTrue(loaded.getConstraints().size == 5, "all constraint types round trip");
		assertTrue(loaded.findConstraint("ik", IkConstraintData.class) != null, "IK setup round trip");
		assertTrue(loaded.findConstraint("transform", TransformConstraintData.class) != null,
			"Transform setup round trip");
		assertTrue(loaded.findConstraint("path-follow", PathConstraintData.class) != null,
			"Path setup round trip");
		PhysicsConstraintData physics = loaded.findConstraint("physics", PhysicsConstraintData.class);
		assertTrue(physics != null && physics.getScaleYMode() == ScaleYMode.volume
			&& physics.getGravityGlobal(), "Physics setup and global flags round trip");
		SliderData slider = loaded.findConstraint("slider", SliderData.class);
		assertTrue(slider != null && slider.getAnimation() == loaded.findAnimation("driven")
			&& slider.getProperty() instanceof FromX, "Slider animation and property round trip");

		Animation controller = loaded.findAnimation("controller");
		assertTrue(controller != null && controller.getTimelines().size == RICH_TIMELINE_COUNT,
			"all rich timelines round trip");
		assertRichSlotTimelines(controller);
		assertRichBoneTimelines(controller);
		assertRichPhysicsTimelines(controller);
		Animation.DeformTimeline deform = findTimeline(controller, Animation.DeformTimeline.class);
		Animation.SequenceTimeline sequence = findTimeline(controller, Animation.SequenceTimeline.class);
		assertTrue(deform != null && deform.getAttachment() == loadedMesh, "Deform target round trip");
		assertTrue(sequence != null && sequence.getAttachment() == loadedMesh,
			"Sequence target sharing the Deform attachment round trip");
		assertNear(2f, deform.getVertices()[1][2] - loadedMesh.getVertices()[2], "sparse Deform value");
		Animation.IkConstraintTimeline ikTimeline = findTimeline(controller, Animation.IkConstraintTimeline.class);
		assertTrue(ikTimeline != null && ikTimeline.getCurveType(0) >= Animation.CurveTimeline.BEZIER,
			"IK Bezier curve round trip");
		assertTrue(findConstraintTimeline(controller, Animation.PhysicsConstraintGravityTimeline.class, -1) != null,
			"global Physics timeline round trip");
		assertTrue(findTimeline(controller, Animation.DrawOrderTimeline.class) != null, "draw order round trip");
		Animation.DrawOrderFolderTimeline folder = findTimeline(controller,
			Animation.DrawOrderFolderTimeline.class);
		assertTrue(folder != null && java.util.Arrays.equals(new int[] {0, 1}, folder.getSlots()),
			"draw order folder target slots round trip");
		assertNear(0.7f, folder.getFrames()[1], "draw order folder frame time");
		assertTrue(java.util.Arrays.equals(new int[] {1, 0}, folder.getDrawOrders()[1]),
			"draw order folder permutation round trip");
		assertTrue(findTimeline(controller, Animation.EventTimeline.class) != null, "event timeline round trip");
	}

	private static void rejectsUnrepresentableDefaultSkinData() {
		SkeletonData emptyDefault = minimalData();
		Skin empty = new Skin("default");
		emptyDefault.getSkins().add(empty);
		emptyDefault.setDefaultSkin(empty);
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonBinaryExporter.toBytes(document(emptyDefault)),
			"empty default skin is rejected instead of disappearing after binary reload");

		SkeletonData coloredDefault = richData();
		coloredDefault.getDefaultSkin().getColor().set(Color.CORAL);
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonBinaryExporter.toBytes(document(coloredDefault)),
			"colored default skin is rejected instead of resetting to white after binary reload");
	}

	private static SkeletonData minimalData() {
		SkeletonData data = new SkeletonData();
		data.setVersion("4.3.5");
		data.setReferenceScale(100f);
		data.setFps(30f);
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root);
		return data;
	}

	private static void rejectsSharedReferencedAttachmentIdentity() {
		SkeletonData animated = minimalData();
		SlotData slot = new SlotData(0, "shared-slot", animated.getBones().first());
		slot.setBlendMode(BlendMode.normal);
		animated.getSlots().add(slot);
		Skin defaultSkin = new Skin("default");
		Skin variantSkin = new Skin("variant");
		Sequence sequence = new Sequence(2, true);
		RegionAttachment shared = new RegionAttachment("shared", sequence);
		shared.setPath("shared");
		shared.setWidth(10f);
		shared.setHeight(10f);
		defaultSkin.setAttachment(0, "shared", shared);
		variantSkin.setAttachment(0, "shared-copy", shared);
		animated.getSkins().add(defaultSkin, variantSkin);
		animated.setDefaultSkin(defaultSkin);
		Animation.SequenceTimeline sequenceTimeline = new Animation.SequenceTimeline(1, 0, shared);
		sequenceTimeline.setFrame(0, 0f, SequenceMode.hold, 0, 0f);
		animated.getAnimations().add(animation("shared-sequence", sequenceTimeline));
		assertRejectsBothFormats(animated,
			"shared Sequence target is rejected instead of binding only one skin after reload");

		SkeletonData linkedData = minimalData();
		SlotData sourceSlot = new SlotData(0, "source-slot", linkedData.getBones().first());
		SlotData linkedSlot = new SlotData(1, "linked-slot", linkedData.getBones().first());
		sourceSlot.setBlendMode(BlendMode.normal);
		linkedSlot.setBlendMode(BlendMode.normal);
		linkedData.getSlots().add(sourceSlot, linkedSlot);
		Skin linkedDefault = new Skin("default");
		Skin linkedVariant = new Skin("variant");
		MeshAttachment source = triangleMesh("shared-source");
		linkedDefault.setAttachment(0, "source", source);
		linkedVariant.setAttachment(0, "source-copy", source);
		MeshAttachment linked = source.newLinkedMesh();
		linkedVariant.setAttachment(1, "linked", linked);
		linkedData.getSkins().add(linkedDefault, linkedVariant);
		linkedData.setDefaultSkin(linkedDefault);
		assertRejectsBothFormats(linkedData,
			"shared Linked Mesh source is rejected instead of resolving to only one owner after reload");
	}

	private static void rejectsNestedAndCyclicLinkedMeshSources() {
		SkeletonData nestedData = minimalData();
		SlotData nestedSlot = new SlotData(0, "nested-slot", nestedData.getBones().first());
		nestedSlot.setBlendMode(BlendMode.normal);
		nestedData.getSlots().add(nestedSlot);
		Skin nestedSkin = new Skin("default");
		MeshAttachment source = triangleMesh("source");
		MeshAttachment firstLink = new MeshAttachment("first-link", new Sequence(1, false));
		firstLink.setPath("first-link");
		firstLink.setSourceMesh(source);
		MeshAttachment nestedLink = new MeshAttachment("nested-link", new Sequence(1, false));
		nestedLink.setPath("nested-link");
		nestedLink.setSourceMesh(firstLink);
		nestedSkin.setAttachment(0, "source", source);
		nestedSkin.setAttachment(0, "first-link", firstLink);
		nestedSkin.setAttachment(0, "nested-link", nestedLink);
		nestedData.getSkins().add(nestedSkin);
		nestedData.setDefaultSkin(nestedSkin);
		assertRejectsBothFormats(nestedData,
			"a Linked Mesh source chain is rejected instead of depending on attachment read order");

		SkeletonData cyclicData = minimalData();
		SlotData cyclicSlot = new SlotData(0, "cyclic-slot", cyclicData.getBones().first());
		cyclicSlot.setBlendMode(BlendMode.normal);
		cyclicData.getSlots().add(cyclicSlot);
		Skin cyclicSkin = new Skin("default");
		MeshAttachment first = triangleMesh("cycle-a");
		MeshAttachment second = triangleMesh("cycle-b");
		first.setSourceMesh(second);
		second.setSourceMesh(first);
		cyclicSkin.setAttachment(0, "cycle-a", first);
		cyclicSkin.setAttachment(0, "cycle-b", second);
		cyclicData.getSkins().add(cyclicSkin);
		cyclicData.setDefaultSkin(cyclicSkin);
		assertRejectsBothFormats(cyclicData,
			"a Linked Mesh source cycle is rejected instead of reloading with empty geometry");
	}

	private static MeshAttachment triangleMesh(String name) {
		MeshAttachment mesh = new MeshAttachment(name, new Sequence(1, false));
		mesh.setPath(name);
		mesh.setWorldVerticesLength(6);
		mesh.setVertices(new float[] {0f, 0f, 10f, 0f, 0f, 10f});
		mesh.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0f, 1f});
		mesh.setTriangles(new short[] {0, 1, 2});
		mesh.setHullLength(6);
		mesh.setWidth(10f);
		mesh.setHeight(10f);
		return mesh;
	}

	private static void assertRejectsBothFormats(SkeletonData data, String message) {
		assertThrows(IllegalStateException.class, () -> SkeletonExporter.toJson(document(data)),
			message + " (JSON)");
		assertThrows(IllegalStateException.class, () -> SkeletonBinaryExporter.toBytes(document(data)),
			message + " (.skel)");
	}

	private static void assertRichSlotTimelines(Animation animation) {
		Animation.RGBTimeline rgb = findSlotTimeline(animation, Animation.RGBTimeline.class, 1);
		assertColorFrame(rgb, 1, 1f, "RGB", 0.8f, 0.6f, 0.4f);
		assertTrue(rgb.getCurveType(0) == Animation.CurveTimeline.STEPPED, "RGB stepped curve round trip");
		Animation.AlphaTimeline alpha = findSlotTimeline(animation, Animation.AlphaTimeline.class, 0);
		assertColorFrame(alpha, 1, 1f, "Alpha", 0.4f);
		assertTrue(alpha.getCurveType(0) >= Animation.CurveTimeline.BEZIER, "Alpha Bezier curve round trip");
		Animation.RGB2Timeline rgb2 = findSlotTimeline(animation, Animation.RGB2Timeline.class, 0);
		assertColorFrame(rgb2, 1, 1f, "RGB2", 0.8f, 0.6f, 0.4f, 0.2f, 0.4f, 0.6f);
		assertTrue(rgb2.getCurveType(0) == Animation.CurveTimeline.LINEAR, "RGB2 linear curve round trip");
		Animation.RGBA2Timeline rgba2 = findSlotTimeline(animation, Animation.RGBA2Timeline.class, 1);
		assertColorFrame(rgba2, 1, 1f, "RGBA2", 0.2f, 0.4f, 0.6f, 0.8f, 0.8f, 0.6f, 0.4f);
		assertTrue(rgba2.getCurveType(0) == Animation.CurveTimeline.STEPPED, "RGBA2 stepped curve round trip");
	}

	private static void assertRichBoneTimelines(Animation animation) {
		Animation.TranslateXTimeline translateX = findBoneTimeline(animation, Animation.TranslateXTimeline.class, 1);
		assertFloatFrame(translateX, 1, 1f, "Translate X", 12f);
		Animation.TranslateYTimeline translateY = findBoneTimeline(animation, Animation.TranslateYTimeline.class, 1);
		assertFloatFrame(translateY, 1, 1f, "Translate Y", -8f);
		assertTrue(translateY.getCurveType(0) == Animation.CurveTimeline.STEPPED,
			"Translate Y stepped curve round trip");
		Animation.ScaleTimeline scale = findBoneTimeline(animation, Animation.ScaleTimeline.class, 1);
		assertFloatFrame(scale, 1, 1f, "Scale", 1.5f, 0.75f);
		assertTrue(scale.getCurveType(0) >= Animation.CurveTimeline.BEZIER, "Scale Bezier curve round trip");
		assertFloatFrame(findBoneTimeline(animation, Animation.ScaleXTimeline.class, 1),
			1, 1f, "Scale X", 1.25f);
		assertFloatFrame(findBoneTimeline(animation, Animation.ScaleYTimeline.class, 1),
			1, 1f, "Scale Y", 0.5f);
		Animation.ShearTimeline shear = findBoneTimeline(animation, Animation.ShearTimeline.class, 1);
		assertFloatFrame(shear, 1, 1f, "Shear", 7f, -3f);
		assertTrue(shear.getCurveType(0) == Animation.CurveTimeline.STEPPED, "Shear stepped curve round trip");
		Animation.ShearXTimeline shearX = findBoneTimeline(animation, Animation.ShearXTimeline.class, 1);
		assertFloatFrame(shearX, 1, 1f, "Shear X", 4f);
		assertTrue(shearX.getCurveType(0) >= Animation.CurveTimeline.BEZIER,
			"Shear X Bezier curve round trip");
		assertFloatFrame(findBoneTimeline(animation, Animation.ShearYTimeline.class, 1),
			1, 1f, "Shear Y", -6f);
	}

	private static void assertRichPhysicsTimelines(Animation animation) {
		Animation.PhysicsConstraintStrengthTimeline strength = findConstraintTimeline(animation,
			Animation.PhysicsConstraintStrengthTimeline.class, 3);
		assertFloatFrame(strength, 1, 1f, "Physics strength", 90f);
		assertTrue(strength.getCurveType(0) >= Animation.CurveTimeline.BEZIER,
			"Physics strength Bezier curve round trip");
		Animation.PhysicsConstraintDampingTimeline damping = findConstraintTimeline(animation,
			Animation.PhysicsConstraintDampingTimeline.class, 3);
		assertFloatFrame(damping, 1, 1f, "Physics damping", 0.8f);
		assertTrue(damping.getCurveType(0) == Animation.CurveTimeline.STEPPED,
			"Physics damping stepped curve round trip");
		assertFloatFrame(findConstraintTimeline(animation, Animation.PhysicsConstraintWindTimeline.class, 3),
			1, 1f, "Physics wind", -3f);
		assertFloatFrame(findConstraintTimeline(animation, Animation.PhysicsConstraintMixTimeline.class, 3),
			1, 1f, "Physics mix", 0.6f);
	}

	private static SkeletonData richData() {
		SkeletonData data = new SkeletonData();
		data.setVersion("4.3.5");
		data.setHash("987654321");
		data.setReferenceScale(100f);
		data.setFps(60f);
		data.setImagesPath("images/");
		data.setAudioPath("audio/");
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		child.getSetupPose().setInherit(BoneData.Inherit.onlyTranslation);
		child.getSetupPose().setX(4f);
		child.setLength(20f);
		data.getBones().add(root, child);
		SlotData art = new SlotData(0, "art", child);
		SlotData guide = new SlotData(1, "guide", root);
		art.setBlendMode(BlendMode.normal);
		guide.setBlendMode(BlendMode.additive);
		setDarkColor(art, Color.BLACK);
		setDarkColor(guide, Color.BLACK);
		art.setAttachmentName("mesh");
		guide.setAttachmentName("path");
		data.getSlots().add(art, guide);

		Skin skin = new Skin("default");
		Sequence meshSequence = new Sequence(3, true);
		meshSequence.setStart(2);
		meshSequence.setDigits(2);
		meshSequence.setSetupIndex(1);
		MeshAttachment mesh = new MeshAttachment("mesh-actual", meshSequence);
		mesh.setPath("images/mesh-frame");
		mesh.setWorldVerticesLength(8);
		mesh.setVertices(new float[] {-4f, -3f, 4f, -3f, 4f, 3f, -4f, 3f});
		mesh.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f});
		mesh.setTriangles(new short[] {0, 1, 2, 2, 3, 0});
		mesh.setHullLength(8);
		mesh.setEdges(new short[] {0, 1, 1, 2, 2, 3, 3, 0});
		mesh.setWidth(8f);
		mesh.setHeight(6f);
		mesh.getColor().set(0.8f, 0.6f, 0.4f, 0.9f);
		skin.setAttachment(0, "mesh", mesh);

		MeshAttachment weighted = new MeshAttachment("weighted", new Sequence(1, false));
		weighted.setPath("weighted");
		weighted.setWorldVerticesLength(6);
		weighted.setBones(new int[] {1, 0, 1, 1, 1, 1});
		weighted.setVertices(new float[] {-2f, 0f, 1f, 2f, 0f, 1f, 0f, 3f, 1f});
		weighted.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 0.5f, 0f});
		weighted.setTriangles(new short[] {0, 1, 2});
		weighted.setHullLength(6);
		skin.setAttachment(0, "weighted", weighted);

		Sequence regionSequence = new Sequence(2, true);
		regionSequence.setStart(7);
		regionSequence.setDigits(3);
		regionSequence.setSetupIndex(1);
		RegionAttachment region = new RegionAttachment("region-actual", regionSequence);
		region.setPath("images/region-frame");
		region.setX(3f);
		region.setY(-4f);
		region.setScaleX(1.25f);
		region.setScaleY(0.75f);
		region.setRotation(18f);
		region.setWidth(10f);
		region.setHeight(12f);
		region.getColor().set(0.3f, 0.6f, 0.9f, 0.8f);
		skin.setAttachment(0, "region", region);
		BoundingBoxAttachment box = new BoundingBoxAttachment("box-actual");
		box.setWorldVerticesLength(6);
		box.setVertices(new float[] {-2f, -2f, 2f, -2f, 0f, 2f});
		box.getColor().set(0.2f, 0.8f, 0.4f, 0.7f);
		skin.setAttachment(0, "box", box);
		PathAttachment pathAttachment = new PathAttachment("path-actual");
		pathAttachment.setClosed(true);
		pathAttachment.setConstantSpeed(false);
		pathAttachment.setWorldVerticesLength(12);
		pathAttachment.setVertices(new float[] {0f, 0f, 1f, 1f, 2f, 1f, 3f, 0f, 4f, -1f, 5f, 0f});
		pathAttachment.setLengths(new float[] {3f, 6f});
		pathAttachment.getColor().set(0.9f, 0.4f, 0.2f, 0.65f);
		skin.setAttachment(1, "path", pathAttachment);
		PointAttachment point = new PointAttachment("point-actual");
		point.setX(2f);
		point.setY(3f);
		point.setRotation(45f);
		point.getColor().set(0.7f, 0.3f, 0.9f, 0.6f);
		skin.setAttachment(1, "point", point);
		ClippingAttachment clipping = new ClippingAttachment("clip-actual");
		clipping.setEndSlot(guide);
		clipping.setConvex(true);
		clipping.setInverse(true);
		clipping.setWorldVerticesLength(6);
		clipping.setVertices(new float[] {-3f, -3f, 3f, -3f, 0f, 4f});
		clipping.getColor().set(0.1f, 0.7f, 0.8f, 0.55f);
		skin.setAttachment(1, "clip", clipping);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);

		Skin variants = new Skin("variants");
		MeshAttachment linked = mesh.newLinkedMesh();
		linked.setTimelineAttachment(mesh);
		linked.setPath("images/linked-frame");
		variants.setAttachment(0, "linked", linked);
		data.getSkins().add(variants);

		Animation driven = animation("driven", rotateTimeline(1));
		data.getAnimations().add(driven);
		variants.getBones().add(child);
		addConstraints(data, variants, root, child, guide, driven);
		EventData eventData = new EventData("sound-event");
		eventData.getSetupPose().setInt(3);
		eventData.getSetupPose().setFloat(1.25f);
		eventData.getSetupPose().setString("setup");
		eventData.setAudioPath("click.wav");
		eventData.getSetupPose().setVolume(0.7f);
		eventData.getSetupPose().setBalance(-0.2f);
		data.getEvents().add(eventData);
		data.getAnimations().add(controllerAnimation(mesh, eventData));
		return data;
	}

	private static void addConstraints(SkeletonData data, Skin skin, BoneData root, BoneData child,
		SlotData guide, Animation driven) {
		IkConstraintData ik = new IkConstraintData("ik");
		ik.getBones().add(child);
		ik.setTarget(root);
		ik.setSkinRequired(true);
		ik.setScaleYMode(ScaleYMode.uniform);
		ik.getSetupPose().setMix(0.75f);
		ik.getSetupPose().setSoftness(2f);
		ik.getSetupPose().setBendDirection(-1);
		data.getConstraints().add(ik);

		TransformConstraintData transform = new TransformConstraintData("transform");
		transform.getBones().add(child);
		transform.setSource(root);
		transform.setSkinRequired(true);
		transform.setLocalSource(true);
		transform.setOffsetX(3f);
		FromRotate from = new FromRotate();
		from.offset = 2f;
		ToX to = new ToX();
		to.max = 10f;
		to.scale = 0.5f;
		from.to.add(to);
		ToShearY toShearY = new ToShearY();
		toShearY.offset = -3f;
		toShearY.max = 20f;
		toShearY.scale = -0.25f;
		from.to.add(toShearY);
		transform.getProperties().add(from);
		transform.getSetupPose().setMixX(0.6f);
		transform.getSetupPose().setMixShearY(0.4f);
		data.getConstraints().add(transform);

		PathConstraintData path = new PathConstraintData("path-follow");
		path.getBones().add(child);
		path.setSlot(guide);
		path.setSkinRequired(true);
		path.setPositionMode(PathConstraintData.PositionMode.fixed);
		path.setSpacingMode(PathConstraintData.SpacingMode.proportional);
		path.setRotateMode(PathConstraintData.RotateMode.chainScale);
		path.setOffsetRotation(12f);
		path.getSetupPose().setPosition(4f);
		path.getSetupPose().setSpacing(0.5f);
		path.getSetupPose().setMixRotate(0.8f);
		path.getSetupPose().setMixX(0.7f);
		path.getSetupPose().setMixY(0.6f);
		data.getConstraints().add(path);

		PhysicsConstraintData physics = new PhysicsConstraintData("physics");
		physics.setBone(child);
		physics.setSkinRequired(true);
		physics.setX(0.2f);
		physics.setY(0.3f);
		physics.setRotate(0.4f);
		physics.setScaleX(0.5f);
		physics.setScaleYMode(ScaleYMode.volume);
		physics.setShearX(0.6f);
		physics.setLimit(4000f);
		physics.setStep(1f / 60f);
		physics.getSetupPose().setInertia(0.3f);
		physics.getSetupPose().setStrength(80f);
		physics.getSetupPose().setDamping(0.7f);
		physics.getSetupPose().setMassInverse(0.5f);
		physics.getSetupPose().setWind(-2f);
		physics.getSetupPose().setGravity(5f);
		physics.getSetupPose().setMix(0.9f);
		physics.setGravityGlobal(true);
		data.getConstraints().add(physics);

		SliderData slider = new SliderData("slider");
		slider.setAnimation(driven);
		slider.setSkinRequired(true);
		slider.setBone(child);
		FromX property = new FromX();
		property.offset = -2f;
		slider.setProperty(property);
		slider.setOffset(1f);
		slider.setScale(0.25f);
		slider.setMax(4f);
		slider.setLocal(true);
		slider.getSetupPose().setMix(0.65f);
		data.getConstraints().add(slider);
		skin.getConstraints().addAll(ik, transform, path, physics, slider);
	}

	private static Animation controllerAnimation(MeshAttachment mesh, EventData eventData) {
		Array<Animation.Timeline> timelines = new Array<>(true, RICH_TIMELINE_COUNT, Animation.Timeline[]::new);
		Animation.AttachmentTimeline attachment = new Animation.AttachmentTimeline(2, 0);
		attachment.setFrame(0, 0f, "mesh");
		attachment.setFrame(1, 1f, "region");
		timelines.add(attachment);
		Animation.RGBATimeline rgba = new Animation.RGBATimeline(2, 0, 0);
		rgba.setFrame(0, 0f, 1f, 0.8f, 0.6f, 1f);
		rgba.setFrame(1, 1f, 0.4f, 0.5f, 0.6f, 0.75f);
		rgba.setStepped(0);
		timelines.add(rgba);
		Animation.RGBTimeline rgb = new Animation.RGBTimeline(2, 0, 1);
		rgb.setFrame(0, 0f, 0.2f, 0.4f, 0.6f);
		rgb.setFrame(1, 1f, 0.8f, 0.6f, 0.4f);
		rgb.setStepped(0);
		timelines.add(rgb);
		Animation.AlphaTimeline alpha = new Animation.AlphaTimeline(2, 1, 0);
		alpha.setFrame(0, 0f, 1f);
		alpha.setFrame(1, 1f, 0.4f);
		setBezier(alpha, new float[] {1f}, new float[] {0.4f});
		timelines.add(alpha);
		Animation.RGB2Timeline rgb2 = new Animation.RGB2Timeline(2, 0, 0);
		rgb2.setFrame(0, 0f, 1f, 0.8f, 0.6f, 0f, 0.2f, 0.4f);
		rgb2.setFrame(1, 1f, 0.8f, 0.6f, 0.4f, 0.2f, 0.4f, 0.6f);
		timelines.add(rgb2);
		Animation.RGBA2Timeline rgba2 = new Animation.RGBA2Timeline(2, 0, 1);
		rgba2.setFrame(0, 0f, 1f, 0.8f, 0.6f, 1f, 0f, 0.2f, 0.4f);
		rgba2.setFrame(1, 1f, 0.2f, 0.4f, 0.6f, 0.8f, 0.8f, 0.6f, 0.4f);
		rgba2.setStepped(0);
		timelines.add(rgba2);
		Animation.TranslateTimeline translate = new Animation.TranslateTimeline(2, 2, 1);
		translate.setFrame(0, 0f, 0f, 0f);
		translate.setFrame(1, 1f, 5f, -3f);
		setBezier(translate, new float[] {0f, 0f}, new float[] {5f, -3f});
		timelines.add(translate);
		Animation.TranslateXTimeline translateX = new Animation.TranslateXTimeline(2, 0, 1);
		translateX.setFrame(0, 0f, 0f);
		translateX.setFrame(1, 1f, 12f);
		timelines.add(translateX);
		Animation.TranslateYTimeline translateY = new Animation.TranslateYTimeline(2, 0, 1);
		translateY.setFrame(0, 0f, 0f);
		translateY.setFrame(1, 1f, -8f);
		translateY.setStepped(0);
		timelines.add(translateY);
		Animation.ScaleTimeline scale = new Animation.ScaleTimeline(2, 2, 1);
		scale.setFrame(0, 0f, 1f, 1f);
		scale.setFrame(1, 1f, 1.5f, 0.75f);
		setBezier(scale, new float[] {1f, 1f}, new float[] {1.5f, 0.75f});
		timelines.add(scale);
		Animation.ScaleXTimeline scaleX = new Animation.ScaleXTimeline(2, 0, 1);
		scaleX.setFrame(0, 0f, 1f);
		scaleX.setFrame(1, 1f, 1.25f);
		timelines.add(scaleX);
		Animation.ScaleYTimeline scaleY = new Animation.ScaleYTimeline(2, 0, 1);
		scaleY.setFrame(0, 0f, 1f);
		scaleY.setFrame(1, 1f, 0.5f);
		timelines.add(scaleY);
		Animation.ShearTimeline shear = new Animation.ShearTimeline(2, 0, 1);
		shear.setFrame(0, 0f, 0f, 0f);
		shear.setFrame(1, 1f, 7f, -3f);
		shear.setStepped(0);
		timelines.add(shear);
		Animation.ShearXTimeline shearX = new Animation.ShearXTimeline(2, 1, 1);
		shearX.setFrame(0, 0f, 0f);
		shearX.setFrame(1, 1f, 4f);
		setBezier(shearX, new float[] {0f}, new float[] {4f});
		timelines.add(shearX);
		Animation.ShearYTimeline shearY = new Animation.ShearYTimeline(2, 0, 1);
		shearY.setFrame(0, 0f, 0f);
		shearY.setFrame(1, 1f, -6f);
		timelines.add(shearY);
		Animation.InheritTimeline inherit = new Animation.InheritTimeline(2, 0);
		inherit.setFrame(0, 0f, BoneData.Inherit.normal);
		inherit.setFrame(1, 1f, BoneData.Inherit.onlyTranslation);
		timelines.add(inherit);

		Animation.IkConstraintTimeline ik = new Animation.IkConstraintTimeline(2, 2, 0);
		ik.setFrame(0, 0f, 0.2f, 1f, 1, false, true);
		ik.setFrame(1, 1f, 0.9f, 4f, -1, true, false);
		setBezier(ik, new float[] {0.2f, 1f}, new float[] {0.9f, 4f});
		timelines.add(ik);
		Animation.TransformConstraintTimeline transform = new Animation.TransformConstraintTimeline(2, 0, 1);
		transform.setFrame(0, 0f, 0f, 0.2f, 0f, 0f, 0f, 0f);
		transform.setFrame(1, 1f, 0f, 0.8f, 0f, 0f, 0f, 0f);
		timelines.add(transform);
		Animation.PathConstraintPositionTimeline position = new Animation.PathConstraintPositionTimeline(2, 0, 2);
		position.setFrame(0, 0f, 1f);
		position.setFrame(1, 1f, 6f);
		timelines.add(position);
		Animation.PathConstraintSpacingTimeline spacing = new Animation.PathConstraintSpacingTimeline(2, 0, 2);
		spacing.setFrame(0, 0f, 0.2f);
		spacing.setFrame(1, 1f, 0.8f);
		timelines.add(spacing);
		Animation.PathConstraintMixTimeline pathMix = new Animation.PathConstraintMixTimeline(2, 0, 2);
		pathMix.setFrame(0, 0f, 0.1f, 0.2f, 0.3f);
		pathMix.setFrame(1, 1f, 0.9f, 0.8f, 0.7f);
		timelines.add(pathMix);

		Animation.PhysicsConstraintInertiaTimeline inertia = new Animation.PhysicsConstraintInertiaTimeline(2, 0, 3);
		inertia.setFrame(0, 0f, 0.2f);
		inertia.setFrame(1, 1f, 0.8f);
		timelines.add(inertia);
		Animation.PhysicsConstraintStrengthTimeline strength =
			new Animation.PhysicsConstraintStrengthTimeline(2, 1, 3);
		strength.setFrame(0, 0f, 70f);
		strength.setFrame(1, 1f, 90f);
		setBezier(strength, new float[] {70f}, new float[] {90f});
		timelines.add(strength);
		Animation.PhysicsConstraintDampingTimeline damping =
			new Animation.PhysicsConstraintDampingTimeline(2, 0, 3);
		damping.setFrame(0, 0f, 0.2f);
		damping.setFrame(1, 1f, 0.8f);
		damping.setStepped(0);
		timelines.add(damping);
		Animation.PhysicsConstraintMassTimeline mass = new Animation.PhysicsConstraintMassTimeline(2, 0, 3);
		mass.setFrame(0, 0f, 1f);
		mass.setFrame(1, 1f, 2f);
		timelines.add(mass);
		Animation.PhysicsConstraintWindTimeline wind = new Animation.PhysicsConstraintWindTimeline(2, 0, 3);
		wind.setFrame(0, 0f, -1f);
		wind.setFrame(1, 1f, -3f);
		timelines.add(wind);
		Animation.PhysicsConstraintMixTimeline physicsMix = new Animation.PhysicsConstraintMixTimeline(2, 0, 3);
		physicsMix.setFrame(0, 0f, 0.9f);
		physicsMix.setFrame(1, 1f, 0.6f);
		timelines.add(physicsMix);
		Animation.PhysicsConstraintResetTimeline reset = new Animation.PhysicsConstraintResetTimeline(1, 3);
		reset.setFrame(0, 0.5f);
		timelines.add(reset);
		Animation.PhysicsConstraintGravityTimeline globalGravity =
			new Animation.PhysicsConstraintGravityTimeline(2, 0, -1);
		globalGravity.setFrame(0, 0f, 1f);
		globalGravity.setFrame(1, 1f, 3f);
		timelines.add(globalGravity);
		Animation.PhysicsConstraintResetTimeline globalReset = new Animation.PhysicsConstraintResetTimeline(1, -1);
		globalReset.setFrame(0, 0.75f);
		timelines.add(globalReset);
		Animation.SliderTimeline slider = new Animation.SliderTimeline(2, 0, 4);
		slider.setFrame(0, 0f, 0f);
		slider.setFrame(1, 1f, 1f);
		timelines.add(slider);
		Animation.SliderMixTimeline sliderMix = new Animation.SliderMixTimeline(2, 0, 4);
		sliderMix.setFrame(0, 0f, 0.25f);
		sliderMix.setFrame(1, 1f, 0.75f);
		timelines.add(sliderMix);

		Animation.DeformTimeline deform = new Animation.DeformTimeline(2, 1, 0, mesh);
		deform.setFrame(0, 0f, mesh.getVertices().clone());
		float[] changed = mesh.getVertices().clone();
		changed[2] += 2f;
		changed[5] -= 1f;
		deform.setFrame(1, 1f, changed);
		deform.setBezier(0, 0, 0, 0f, 0f, 0.25f, 0.2f, 0.75f, 0.8f, 1f, 1f);
		timelines.add(deform);
		Animation.SequenceTimeline sequence = new Animation.SequenceTimeline(2, 0, mesh);
		sequence.setFrame(0, 0f, SequenceMode.hold, 1, 0f);
		sequence.setFrame(1, 0.5f, SequenceMode.loop, 0, 0.2f);
		timelines.add(sequence);
		Animation.DrawOrderTimeline drawOrder = new Animation.DrawOrderTimeline(1);
		drawOrder.setFrame(0, 0.4f, new int[] {1, 0});
		timelines.add(drawOrder);
		Animation.DrawOrderFolderTimeline drawOrderFolder =
			new Animation.DrawOrderFolderTimeline(2, new int[] {0, 1}, 2);
		drawOrderFolder.setFrame(0, 0.2f, null);
		drawOrderFolder.setFrame(1, 0.7f, new int[] {1, 0});
		timelines.add(drawOrderFolder);
		Animation.EventTimeline events = new Animation.EventTimeline(1);
		Event event = new Event(0.6f, eventData);
		event.setInt(7);
		event.setFloat(2.5f);
		event.setString("播放");
		event.setVolume(0.8f);
		event.setBalance(0.1f);
		events.setFrame(0, event);
		timelines.add(events);
		Animation animation = new Animation("controller");
		animation.setTimelines(timelines, new IntArray());
		animation.getColor().set(Color.CORAL);
		return animation;
	}

	private static Animation.RotateTimeline rotateTimeline(int bone) {
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(2, 0, bone);
		rotate.setFrame(0, 0f, 0f);
		rotate.setFrame(1, 1f, 45f);
		return rotate;
	}

	private static Animation animation(String name, Animation.Timeline... timelines) {
		Array<Animation.Timeline> values = new Array<>(true, timelines.length, Animation.Timeline[]::new);
		values.addAll(timelines);
		Animation animation = new Animation(name);
		animation.setTimelines(values, new IntArray());
		return animation;
	}

	private static void setBezier(Animation.CurveTimeline timeline, float[] start, float[] end) {
		for (int channel = 0; channel < start.length; channel++)
			timeline.setBezier(channel, 0, channel, 0f, start[channel], 0.25f,
				start[channel] + (end[channel] - start[channel]) * 0.2f, 0.75f,
				start[channel] + (end[channel] - start[channel]) * 0.8f, 1f, end[channel]);
	}

	private static <T extends Animation.Timeline> T findTimeline(Animation animation, Class<T> type) {
		for (Animation.Timeline timeline : animation.getTimelines()) if (type.isInstance(timeline)) return type.cast(timeline);
		return null;
	}

	private static void assertDeformFrame(Animation animation, com.esotericsoftware.spine.attachments.VertexAttachment attachment,
		float[] expected, String message) {
		for (Animation.Timeline timeline : animation.getTimelines()) {
			if (timeline instanceof Animation.DeformTimeline deform && deform.getAttachment() == attachment) {
				assertTrue(deform.getFrameCount() == 1
					&& java.util.Arrays.equals(expected, deform.getVertices()[0]), message + " round trip");
				return;
			}
		}
		throw new AssertionError(message + " timeline is missing");
	}

	private static <T extends Animation.Timeline & Animation.SlotTimeline> T findSlotTimeline(
		Animation animation, Class<T> type, int slotIndex) {
		for (Animation.Timeline timeline : animation.getTimelines())
			if (type.isInstance(timeline) && type.cast(timeline).getSlotIndex() == slotIndex)
				return type.cast(timeline);
		throw new AssertionError(type.getSimpleName() + " for slot " + slotIndex + " is missing");
	}

	private static <T extends Animation.Timeline & Animation.BoneTimeline> T findBoneTimeline(
		Animation animation, Class<T> type, int boneIndex) {
		for (Animation.Timeline timeline : animation.getTimelines())
			if (type.isInstance(timeline) && type.cast(timeline).getBoneIndex() == boneIndex)
				return type.cast(timeline);
		throw new AssertionError(type.getSimpleName() + " for bone " + boneIndex + " is missing");
	}

	private static <T extends Animation.Timeline & Animation.ConstraintTimeline> T findConstraintTimeline(
		Animation animation, Class<T> type, int constraintIndex) {
		for (Animation.Timeline timeline : animation.getTimelines())
			if (type.isInstance(timeline) && type.cast(timeline).getConstraintIndex() == constraintIndex)
				return type.cast(timeline);
		return null;
	}

	private static void assertFloatFrame(Animation.Timeline timeline, int frame, float expectedTime,
		String message, float... expectedValues) {
		assertTrue(timeline != null, message + " timeline exists");
		int base = frame * timeline.getFrameEntries();
		assertNear(expectedTime, timeline.getFrames()[base], message + " frame time");
		for (int channel = 0; channel < expectedValues.length; channel++)
			assertNear(expectedValues[channel], timeline.getFrames()[base + 1 + channel],
				message + " channel " + channel);
	}

	private static void assertColorFrame(Animation.Timeline timeline, int frame, float expectedTime,
		String message, float... expectedValues) {
		assertTrue(timeline != null, message + " timeline exists");
		int base = frame * timeline.getFrameEntries();
		assertNear(expectedTime, timeline.getFrames()[base], message + " frame time");
		for (int channel = 0; channel < expectedValues.length; channel++) {
			float actual = timeline.getFrames()[base + 1 + channel];
			if (Math.abs(expectedValues[channel] - actual) > 1f / 255f + 0.0001f)
				throw new AssertionError(message + " channel " + channel + ": expected "
					+ expectedValues[channel] + ", got " + actual);
		}
	}

	private static void setDarkColor(SlotData slot, Color color) {
		try {
			java.lang.reflect.Field field = slot.getSetupPose().getClass().getDeclaredField("darkColor");
			field.setAccessible(true);
			field.set(slot.getSetupPose(), color == null ? null : new Color(color));
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("Unable to configure test slot dark color", error);
		}
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void roundTripsExampleThroughOfficialReader() throws Exception {
		SkeletonJson json = new SkeletonJson(new SmokeTest.StubLoader());
		SkeletonData source = json.readSkeletonData(new JsonReader().parse(ExampleSkeleton.JSON));
		source.setName("binary-example");
		source.setHash("t1M/zXhhu7c");
		source.setImagesPath("images/");
		SkeletonDocument document = document(source);

		byte[] bytes = SkeletonBinaryExporter.toBytes(document);
		assertTrue(bytes.length > 100, "binary output is nonempty");
		SkeletonData loaded = read(bytes);
		assertTrue("-5236771789981238345".equals(loaded.getHash()), "Base64 skeleton hash bits round trip");
		assertTrue(source.getBones().size == loaded.getBones().size, "bone count round trip");
		assertTrue(source.getSlots().size == loaded.getSlots().size, "slot count round trip");
		assertTrue(source.getSkins().size == loaded.getSkins().size, "skin count round trip");
		assertTrue(source.getAnimations().size == loaded.getAnimations().size, "animation count round trip");
		assertTrue("images/".equals(loaded.getImagesPath()), "images path round trip");
		assertTrue(loaded.findAnimation("walk") != null, "named animation round trip");
		assertTrue(loaded.findSkin("blue") != null, "named skin round trip");
	}

	private static void atomicallyReplacesBinaryFiles() throws Exception {
		SkeletonData source = new SkeletonJson(new SmokeTest.StubLoader())
			.readSkeletonData(new JsonReader().parse(ExampleSkeleton.JSON));
		SkeletonDocument document = document(source);
		Path directory = Files.createTempDirectory("openspine-binary-export-");
		Path target = directory.resolve("sample.skel");
		try {
			Files.writeString(target, "old");
			SkeletonBinaryExporter.writeAtomic(document, target);
			byte[] bytes = Files.readAllBytes(target);
			assertTrue(bytes.length > 100, "atomic write replaces the old file");
			assertTrue(read(bytes).getBones().size == source.getBones().size,
				"atomically written file loads through SkeletonBinary");
			try (var files = Files.list(directory)) {
				assertTrue(files.count() == 1, "atomic write leaves no temporary file");
			}

			CancellationToken cancelled = new CancellationToken() {
				private int checkpoints;

				@Override public void checkCancelled() {
					super.checkCancelled();
					if (++checkpoints == 6) {
						cancel();
						super.checkCancelled();
					}
				}
			};
			try {
				SkeletonBinaryExporter.writeAtomic(document, target, cancelled);
				throw new AssertionError("cancelled binary save should stop before replacing the target");
			} catch (java.util.concurrent.CancellationException expectedCancellation) {
				// Expected.
			}
			assertTrue(java.util.Arrays.equals(bytes, Files.readAllBytes(target)),
				"cancelled binary save preserves existing content");
			try (var files = Files.list(directory)) {
				assertTrue(files.count() == 1, "cancelled binary save leaves no temporary file");
			}
		} finally {
			Files.deleteIfExists(target);
			Files.deleteIfExists(directory);
		}
	}

	private static SkeletonData read(byte[] bytes) {
		SkeletonBinary binary = new SkeletonBinary(new SmokeTest.StubLoader());
		ByteArrayInputStream input = new ByteArrayInputStream(bytes);
		try {
			return binary.readSkeletonData(input);
		} catch (RuntimeException error) {
			throw new IllegalStateException("Binary reader failed at byte " + (bytes.length - input.available())
				+ " of " + bytes.length, error);
		}
	}

	private static SkeletonDocument document(SkeletonData data) {
		SkeletonDocument document = new SkeletonDocument();
		document.data = data;
		document.skeleton = new Skeleton(data);
		return document;
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static <T extends Throwable> void assertThrows(Class<T> type, ThrowingRunnable action,
		String message) {
		try {
			action.run();
		} catch (Throwable error) {
			if (type.isInstance(error)) return;
			throw new AssertionError(message + ": expected " + type.getSimpleName() + ", got "
				+ error.getClass().getSimpleName(), error);
		}
		throw new AssertionError(message + ": expected " + type.getSimpleName());
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private SkeletonBinaryExporterTest() {}
}
