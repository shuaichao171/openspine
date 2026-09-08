package com.openspine.model;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;

/** Headless regression tests for guarded structural bone deletion. */
public final class SkeletonEditorTest {
	public static void main(String[] args) {
		addsInitializedBone();
		updatesBoneSetupPoseSafely();
		managesIkConstraintSetupSafely();
		managesTransformConstraintSetupSafely();
		managesPathConstraintSetupSafely();
		managesPhysicsConstraintSetupSafely();
		managesSliderConstraintSetupSafely();
		reordersMixedConstraintsAndRemapsTimelines();
		deletesMiddleConstraintAndReindexesTimelines();
		addsInitializedSlot();
		updatesSlotPropertiesSafely();
		twoColorTimelinesProtectSetupDarkColor();
		updatesRegionAttachmentSafely();
		managesImageSequenceSetupSafely();
		createsAndEditsMeshSafely();
		createsLinkedMeshesSafely();
		updatesLinkedMeshSourceWithDeformSafely();
		preservesDeformOffsetsAcrossSetupMeshEdits();
		remapsWeightedDeformInfluenceOrderSafely();
		paintsMeshWeightsSafely();
		previewsMeshWeightsWithLinkedMeshesSafely();
		smoothsMeshWeightsAlongTopology();
		editsMeshTopologySafely();
		migratesMeshTopologyWithDeformSafely();
		weldsMeshVerticesAndCopiesWeights();
		reordersMeshHullWithDeformSafely();
		reordersWeightedMeshHullWithDeformSafely();
		createsAndEditsStandardAttachmentsSafely();
		convertsWeightedMeshToSlotSpace();
		renamesSlotAndRemapsReferences();
		deletesOnlyUnreferencedTailSlot();
		rejectsReferencedBoneWithoutDataLoss();
		deletesUnreferencedTailBone();
		renameKeepsSlotBindingAndExportName();
		renamePreservesConstraintRelationSelection();
		copiesAndPastesBoneSubtree();
		pastesCompleteBoneProperties();
		preservesEditorSelectionAcrossRebuild();
		setsAndClearsSlotAttachment();
		createsRegionAttachmentInSelectedSkin();
		rebindsReplacementAtlasAtomically();
		protectsAdvancedAttachmentReferences();
		copiesImplicitDefaultSkin();
		managesSkinLifecycle();
		preservesPausedSlotTimelineAcrossSkinLifecycle();
		System.out.println("ALL SKELETON EDITOR TESTS PASSED");
	}

	private static void addsInitializedBone() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		BoneData added = editor.addBone("added", doc.data.findBone("child"));
		assertTrue(added != null, "new bone should be created");
		assertTrue(added.getSetupPose().getInherit() == Inherit.normal, "new bone inherit default");
		assertNear(1f, added.getSetupPose().getScaleX(), "new bone scale x");
		assertNear(1f, added.getSetupPose().getScaleY(), "new bone scale y");
		assertTrue(added.getVisible(), "new bone is visible");
		doc.skeleton.updateWorldTransform(com.esotericsoftware.spine.Physics.update);
		assertTrue(doc.skeleton.findBone("added") != null, "new runtime bone exists");
	}

	private static void updatesBoneSetupPoseSafely() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		BoneData childData = doc.data.findBone("child");
		var child = doc.skeleton.findBone("child");
		doc.setSelectedBone(child);
		doc.setPoseMode(true);
		child.getPose().setPosition(12f, -7f);
		child.getPose().setRotation(35f);
		child.getPose().setScale(1.25f, 0.75f);
		child.getPose().setShearX(4f);
		child.getPose().setShearY(-6f);
		SkeletonEditor.BonePoseState pose = SkeletonEditor.bonePoseState(child.getPose());
		SkeletonEditor.BoneSetupState setup = new SkeletonEditor.BoneSetupState(pose, 42f, true);
		long revision = doc.revision;

		assertTrue(editor.updateBoneSetup(childData, setup), "changed bone setup should be accepted");
		assertTrue(SkeletonEditor.bonePoseState(childData.getSetupPose()).equals(pose),
			"bone setup data matches the captured runtime pose");
		assertNear(42f, childData.getLength(), "bone setup length");
		assertTrue(childData.getSkinRequired(), "bone setup skin-required state");
		assertTrue(SkeletonEditor.bonePoseState(doc.skeleton.findBone("child").getPose()).equals(pose),
			"runtime pose remains visually unchanged after rebuilding");
		assertTrue(doc.dirty && doc.revision == revision + 1, "setup change marks the document once");
		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"x\": 12") && json.contains("\"length\": 42")
			&& json.contains("\"skin\": true"),
			"written setup pose is present in exported Spine JSON");
		assertTrue(!editor.updateBoneSetup(childData, setup), "unchanged bone setup is a no-op");

		SkeletonEditor.BonePoseState invalid = new SkeletonEditor.BonePoseState(Float.NaN, 0f, 0f,
			1f, 1f, 0f, 0f, Inherit.normal);
		assertTrue(!editor.updateBoneSetup(childData, new SkeletonEditor.BoneSetupState(invalid, 99f, false)),
			"non-finite setup pose is rejected");
		assertTrue(childData.getLength() == 42f && childData.getSkinRequired(),
			"rejected setup leaves length and skin-required data unchanged");
		assertTrue(!editor.updateBoneSetup(childData, new SkeletonEditor.BoneSetupState(pose, -1f, false)),
			"negative setup length is rejected");
		assertTrue(childData.getLength() == 42f && childData.getSkinRequired(),
			"negative length rejection remains atomic");
		BoneData foreign = new BoneData(1, "foreign", doc.data.getBones().first());
		assertTrue(!editor.updateBoneSetup(foreign, setup), "bone outside the document is rejected");
	}

	private static void preservesEditorSelectionAcrossRebuild() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		SlotData slot = new SlotData(0, "selected-slot", doc.data.findBone("child"));
		slot.setBlendMode(BlendMode.normal);
		slot.setAttachmentName("region");
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		skin.setAttachment(0, "region", new RegionAttachment("region", new Sequence(1, false)));
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		IkConstraintData ik = new IkConstraintData("selection-ik");
		ik.getBones().add(doc.data.findBone("child"));
		ik.setTarget(doc.data.findBone("root"));
		doc.data.getConstraints().add(ik);
		editor.rebuildSkeleton();

		doc.setSlotSelection(doc.skeleton.findSlot("selected-slot"), "region", "region");
		editor.rebuildSkeleton();
		assertTrue(doc.getSelectionKind() == SkeletonDocument.SelectionKind.ATTACHMENT
			&& "selected-slot".equals(doc.getSelectedSlotName())
			&& "region".equals(doc.getSelectedAttachmentPlaceholder())
			&& "region".equals(doc.getSelectedAttachmentName()),
			"runtime rebuild preserves the exact slot attachment selection");

		doc.setConstraintRelationSelection(ik, "target:root", "root");
		editor.rebuildSkeleton();
		assertTrue(doc.getSelectionKind() == SkeletonDocument.SelectionKind.CONSTRAINT
			&& doc.getSelectedConstraint() == ik
			&& "target:root".equals(doc.getSelectedConstraintRelation())
			&& "root".equals(doc.getSelectedBone().getData().getName()),
			"runtime rebuild preserves the exact constraint relation node and related bone");
	}

	private static void addsInitializedSlot() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		BoneData child = doc.data.findBone("child");
		SlotData first = editor.addSlot("child.slot", child);
		assertTrue(first != null, "new slot should be created");
		assertEquals(0, first.getIndex(), "first slot index");
		assertTrue(first.getBoneData() == child, "new slot binds selected bone");
		assertTrue(first.getBlendMode() == BlendMode.normal, "new slot blend default");
		assertTrue(first.getVisible(), "new slot is visible");
		assertTrue(doc.skeleton.findSlot("child.slot") != null, "new runtime slot exists");
		SlotData second = editor.addSlot("child.slot", child);
		assertTrue(second != null && "child.slot2".equals(second.getName()), "slot names remain unique");
		assertEquals(1, second.getIndex(), "appended slot index");
		assertTrue(doc.dirty, "adding a slot marks document dirty");
		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"name\": \"child.slot2\""), "new slot is exportable");
	}

	private static void managesIkConstraintSetupSafely() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.findBone("root");
		BoneData child = doc.data.findBone("child");
		BoneData grandchild = new BoneData(2, "grandchild", child);
		grandchild.getSetupPose().setInherit(Inherit.normal);
		doc.data.getBones().add(grandchild);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);
		SkeletonEditor editor = new SkeletonEditor(doc);

		SkeletonEditor.IkConstraintSetup setup = new SkeletonEditor.IkConstraintSetup("arm-ik", child, null,
			root, true, ScaleYMode.volume, 0.75f, 3f, -1, true, true);
		assertTrue(editor.validateIkConstraintSetup(null, setup, skin, true) == null,
			"valid one-bone IK setup passes validation");
		long revision = doc.revision;
		IkConstraintData created = editor.addIkConstraint(setup, skin, true);
		assertTrue(created != null && doc.data.getConstraints().first() == created,
			"IK setup is appended to the unified constraint list");
		assertTrue(skin.getConstraints().contains(created, true), "new skin-required IK is active in the chosen skin");
		assertTrue(doc.skeleton.findConstraint("arm-ik", com.esotericsoftware.spine.IkConstraint.class) != null,
			"runtime skeleton is rebuilt with the new IK constraint");
		assertTrue(doc.dirty && doc.revision == revision + 1, "adding IK setup marks the document once");
		assertTrue(SkeletonEditor.ikConstraintSetup(created).equals(setup), "all IK setup fields are retained");

		AnimationEditor animations = new AnimationEditor(doc);
		Animation animation = animations.addAnimation("ik-edit");
		animations.addIkKeyframe(animation, 0, 0f,
			new AnimationEditor.IkKeyframe(0.5f, 1f, 1, false, true));
		doc.setConstraintSelection(created);
		doc.setTimelineViewState(new SkeletonDocument.TimelineViewState("ik-edit", true, 0,
			"ik\u001farm-ik", 3f, 5f, true, new String[] {"ik\u001farm-ik"},
			new float[] {0f}, new int[] {0}));
		SkeletonEditor.IkConstraintSetup renamedSetup = new SkeletonEditor.IkConstraintSetup("arm-ik-renamed",
			child, grandchild, root, true, ScaleYMode.uniform, 0.5f, 2f, 1, false, true);
		IkConstraintData updated = editor.updateIkConstraint(created, renamedSetup, skin, false);
		assertTrue(updated != null && updated != created && doc.data.getConstraints().first() == updated,
			"IK setup update replaces immutable-name data at the same index");
		assertTrue(!skin.getConstraints().contains(updated, true), "current-skin activation can be disabled");
		assertTrue(((Animation.IkConstraintTimeline)animation.getTimelines().first()).getConstraintIndex() == 0,
			"IK animation index remains valid after setup replacement");
		assertTrue(doc.skeleton.findConstraint("arm-ik-renamed", com.esotericsoftware.spine.IkConstraint.class) != null,
			"runtime skeleton uses the updated IK name");
		assertTrue(doc.getSelectedConstraint() == updated
			&& doc.timelineViewState().curveTrackKey().equals("ik\u001farm-ik-renamed")
			&& doc.timelineViewState().selectedTrackKeys()[0].equals("ik\u001farm-ik-renamed"),
			"renaming a selected constraint preserves its selection and Timeline identities");
		assertTrue(editor.updateIkConstraint(updated, renamedSetup, skin, false) == updated,
			"equivalent IK setup update is a no-op");

		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"name\": \"arm-ik-renamed\"") && json.contains("\"scaleY\": \"uniform\"")
			&& json.contains("\"bones\": [\"child\", \"grandchild\"]"),
			"managed IK setup exports through standard Spine JSON");
		assertTrue(editor.getIkConstraintDeleteBlockReason(updated).contains("动画"),
			"IK animation timeline blocks destructive setup deletion");
		animation.getTimelines().clear();
		assertTrue(editor.getIkConstraintDeleteBlockReason(updated) == null,
			"unreferenced tail IK becomes deletable");
		assertTrue(editor.deleteIkConstraint(updated) && doc.data.getConstraints().isEmpty(),
			"unreferenced tail IK deletion succeeds");
		assertTrue(doc.skeleton.findConstraint("arm-ik-renamed", com.esotericsoftware.spine.IkConstraint.class) == null,
			"runtime IK is removed after deletion");
		assertTrue(doc.getSelectedConstraint() == null
			&& doc.timelineViewState().curveTrackKey().isEmpty()
			&& doc.timelineViewState().selectedTrackKeys().length == 0,
			"deleting a selected constraint clears its selection and Timeline identities");

		SkeletonEditor.IkConstraintSetup cyclic = new SkeletonEditor.IkConstraintSetup("cyclic", root, null,
			child, false, ScaleYMode.none, 1f, 0f, 1, false, false);
		assertTrue(editor.validateIkConstraintSetup(null, cyclic, null, false).contains("子树"),
			"target descendants are rejected before runtime sorting");
		SkeletonEditor.IkConstraintSetup invalidMix = new SkeletonEditor.IkConstraintSetup("invalid", child, null,
			root, false, ScaleYMode.none, 2f, 0f, 1, false, false);
		assertTrue(editor.validateIkConstraintSetup(null, invalidMix, null, false).contains("0 到 1"),
			"out-of-range IK mix is rejected atomically");
		assertTrue(editor.validateIkConstraintSetup(null, setup, skin, true) == null,
			"deleted IK name can be reused");
		assertTrue(editor.validateIkConstraintSetup(null,
			new SkeletonEditor.IkConstraintSetup("inactive", child, null, root, false, ScaleYMode.none,
				1f, 0f, 1, false, false), skin, true).contains("皮肤必需"),
			"non-skin-required IK cannot create a misleading skin activation reference");
	}

	private static void managesTransformConstraintSetupSafely() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.findBone("root");
		BoneData child = doc.data.findBone("child");
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);
		SkeletonEditor editor = new SkeletonEditor(doc);
		java.util.List<SkeletonEditor.TransformFromPropertySetup> properties = java.util.List.of(
			new SkeletonEditor.TransformFromPropertySetup(SkeletonEditor.TransformProperty.ROTATE, 2f,
				java.util.List.of(
					new SkeletonEditor.TransformToPropertySetup(SkeletonEditor.TransformProperty.ROTATE,
						-1f, 8f, 0.5f),
					new SkeletonEditor.TransformToPropertySetup(SkeletonEditor.TransformProperty.X,
						3f, 12f, -0.25f),
					new SkeletonEditor.TransformToPropertySetup(SkeletonEditor.TransformProperty.Y,
						4f, 16f, 0.75f),
					new SkeletonEditor.TransformToPropertySetup(SkeletonEditor.TransformProperty.SHEAR_Y,
						5f, 20f, 1.25f))),
			new SkeletonEditor.TransformFromPropertySetup(SkeletonEditor.TransformProperty.SCALE_X, -2f,
				java.util.List.of(
					new SkeletonEditor.TransformToPropertySetup(SkeletonEditor.TransformProperty.SCALE_X,
						0.1f, 2f, 1.5f),
					new SkeletonEditor.TransformToPropertySetup(SkeletonEditor.TransformProperty.SCALE_Y,
						-0.2f, 3f, 2f))));

		SkeletonEditor.TransformConstraintSetup setup = new SkeletonEditor.TransformConstraintSetup(
			"follow", java.util.List.of(child), root, true, true, false, true, true,
			10f, 2f, -3f, 0.2f, -0.1f, 5f, 0.25f, 0.35f, 0.45f, 0.55f, 0.65f, 0.75f,
			properties);
		assertTrue(editor.validateTransformConstraintSetup(null, setup, skin, true) == null,
			"valid Transform setup passes validation");
		long revision = doc.revision;
		TransformConstraintData created = editor.addTransformConstraint(setup, skin, true);
		assertTrue(created != null && doc.data.getConstraints().first() == created,
			"Transform setup is appended to the unified constraint list");
		assertTrue(skin.getConstraints().contains(created, true),
			"new skin-required Transform constraint is active in the chosen skin");
		assertTrue(doc.skeleton.findConstraint("follow", com.esotericsoftware.spine.TransformConstraint.class) != null,
			"runtime skeleton is rebuilt with the new Transform constraint");
		assertTrue(doc.dirty && doc.revision == revision + 1,
			"adding Transform setup marks the document once");
		assertTrue(SkeletonEditor.transformConstraintSetup(created).equals(setup),
			"all Transform setup fields and property mappings are retained");
		assertTrue(created.getProperties().first().to.size == 4,
			"one Transform source property retains all mapped targets");

		Animation animation = new Animation("transform-edit");
		Animation.TransformConstraintTimeline timeline = new Animation.TransformConstraintTimeline(1, 0, 0);
		timeline.setFrame(0, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(timeline);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);

		SkeletonEditor.TransformConstraintSetup renamedSetup = new SkeletonEditor.TransformConstraintSetup(
			"follow-renamed", java.util.List.of(child), root, true, false, true, false, false,
			-7f, 4f, 6f, -0.2f, 0.3f, -9f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f,
			setup.properties());
		TransformConstraintData updated = editor.updateTransformConstraint(created, renamedSetup, skin, false);
		assertTrue(updated != null && updated != created && doc.data.getConstraints().first() == updated,
			"Transform setup update replaces immutable-name data at the same index");
		assertTrue(!skin.getConstraints().contains(updated, true),
			"current-skin Transform activation can be disabled");
		assertTrue(((Animation.TransformConstraintTimeline)animation.getTimelines().first()).getConstraintIndex() == 0,
			"Transform animation index remains valid after setup replacement");
		assertTrue(editor.updateTransformConstraint(updated, renamedSetup, skin, false) == updated,
			"equivalent Transform setup update is a no-op");
		assertTrue(SkeletonEditor.transformConstraintSetup(updated).properties().equals(setup.properties()),
			"editing Transform setup preserves advanced property mappings");

		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"name\": \"follow-renamed\"")
			&& json.contains("\"type\": \"transform\""),
			"managed Transform setup exports through standard Spine JSON");
		assertTrue(editor.getTransformConstraintDeleteBlockReason(updated).contains("Animation"),
			"Transform animation timeline blocks destructive setup deletion");
		animation.getTimelines().clear();
		assertTrue(editor.getTransformConstraintDeleteBlockReason(updated) == null,
			"unreferenced tail Transform constraint becomes deletable");
		assertTrue(editor.deleteTransformConstraint(updated) && doc.data.getConstraints().isEmpty(),
			"unreferenced tail Transform deletion succeeds");

		SkeletonEditor.TransformConstraintSetup cyclic = new SkeletonEditor.TransformConstraintSetup(
			"cyclic", java.util.List.of(root), child, false, false, false, false, false,
			0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
			SkeletonEditor.defaultTransformProperties());
		assertTrue(editor.validateTransformConstraintSetup(null, cyclic, null, false).contains("subtree"),
			"Transform dependency cycles are rejected before runtime sorting");
		SkeletonEditor.TransformConstraintSetup invalidMix = new SkeletonEditor.TransformConstraintSetup(
			"invalid", java.util.List.of(child), root, false, false, false, false, false,
			0f, 0f, 0f, 0f, 0f, 0f, Float.NaN, 0f, 0f, 0f, 0f, 0f,
			SkeletonEditor.defaultTransformProperties());
		assertTrue(editor.validateTransformConstraintSetup(null, invalidMix, null, false).contains("finite"),
			"non-finite Transform values are rejected atomically");
	}

	private static void managesPhysicsConstraintSetupSafely() {
		SkeletonDocument doc = createDocument();
		BoneData child = doc.data.findBone("child");
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);
		SkeletonEditor editor = new SkeletonEditor(doc);

		SkeletonEditor.PhysicsConstraintSetup setup = new SkeletonEditor.PhysicsConstraintSetup(
			"swing", child, true, ScaleYMode.volume, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 4200f, 75,
			0.35f, 80f, 0.72f, 2.5f, -3f, 6f, 0.8f,
			true, false, true, false, true, false, true);
		assertTrue(editor.validatePhysicsConstraintSetup(null, setup, skin, true) == null,
			"valid Physics setup passes validation");
		long revision = doc.revision;
		PhysicsConstraintData created = editor.addPhysicsConstraint(setup, skin, true);
		assertTrue(created != null && doc.data.getConstraints().first() == created,
			"Physics setup is appended to the unified constraint list");
		assertTrue(skin.getConstraints().contains(created, true),
			"new skin-required Physics constraint is active in the chosen skin");
		assertTrue(doc.skeleton.findConstraint("swing", com.esotericsoftware.spine.PhysicsConstraint.class) != null,
			"runtime skeleton is rebuilt with the new Physics constraint");
		assertTrue(doc.dirty && doc.revision == revision + 1,
			"adding Physics setup marks the document once");
		assertTrue(SkeletonEditor.physicsConstraintSetup(created).equals(setup),
			"all Physics setup values and global flags are retained");

		Animation animation = new Animation("physics-edit");
		Animation.PhysicsConstraintInertiaTimeline timeline =
			new Animation.PhysicsConstraintInertiaTimeline(1, 0, 0);
		timeline.setFrame(0, 0f, 0.5f);
		Animation.PhysicsConstraintInertiaTimeline globalInertia =
			new Animation.PhysicsConstraintInertiaTimeline(1, 0, -1);
		globalInertia.setFrame(0, 0f, 0.25f);
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(timeline, globalInertia);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);

		SkeletonEditor.PhysicsConstraintSetup renamedSetup = new SkeletonEditor.PhysicsConstraintSetup(
			"swing-renamed", child, true, ScaleYMode.uniform, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 3500f, 90,
			0.45f, 95f, 0.8f, 3f, 4f, -7f, 0.65f,
			false, true, false, true, false, true, false);
		long beforeRejectedUpdate = doc.revision;
		assertTrue(editor.validatePhysicsConstraintSetup(created, renamedSetup, skin, false).contains("全局"),
			"closing the last Global flag used by an animation is rejected");
		assertTrue(editor.updatePhysicsConstraint(created, renamedSetup, skin, false) == null
			&& doc.data.getConstraints().first() == created && doc.revision == beforeRejectedUpdate,
			"rejected Physics setup update is atomic");
		timelines.removeValue(globalInertia, true);
		animation.setTimelines(timelines, new IntArray());
		PhysicsConstraintData updated = editor.updatePhysicsConstraint(created, renamedSetup, skin, false);
		assertTrue(updated != null && updated != created && doc.data.getConstraints().first() == updated,
			"Physics setup update replaces immutable-name data at the same index");
		assertTrue(!skin.getConstraints().contains(updated, true),
			"current-skin Physics activation can be disabled");
		assertTrue(timeline.getConstraintIndex() == 0,
			"Physics animation index remains valid after setup replacement");
		assertTrue(doc.skeleton.findConstraint("swing-renamed", com.esotericsoftware.spine.PhysicsConstraint.class) != null,
			"runtime skeleton uses the updated Physics name");
		assertTrue(editor.updatePhysicsConstraint(updated, renamedSetup, skin, false) == updated,
			"equivalent Physics setup update is a no-op");

		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"name\": \"swing-renamed\"")
			&& json.contains("\"type\": \"physics\"") && json.contains("\"fps\": 90"),
			"managed Physics setup and timeline export through standard Spine JSON");
		assertTrue(editor.getPhysicsConstraintDeleteBlockReason(updated).contains("动画"),
			"Physics animation timeline blocks destructive setup deletion");
		timelines.clear();
		Animation.PhysicsConstraintGravityTimeline globalGravity =
			new Animation.PhysicsConstraintGravityTimeline(1, 0, -1);
		globalGravity.setFrame(0, 0f, -5f);
		Animation.PhysicsConstraintResetTimeline globalReset =
			new Animation.PhysicsConstraintResetTimeline(1, -1);
		globalReset.setFrame(0, 0.5f);
		timelines.add(globalGravity, globalReset);
		animation.setTimelines(timelines, new IntArray());
		assertTrue(editor.getPhysicsConstraintDeleteBlockReason(updated).contains("最后一个有效目标"),
			"global Physics value timeline blocks deletion of its last enabled target");
		timelines.removeValue(globalGravity, true);
		animation.setTimelines(timelines, new IntArray());
		assertTrue(editor.getPhysicsConstraintDeleteBlockReason(updated).contains("重置"),
			"global Physics reset timeline blocks deletion of the last Physics constraint");
		timelines.clear();
		animation.setTimelines(timelines, new IntArray());
		assertTrue(editor.getPhysicsConstraintDeleteBlockReason(updated) == null,
			"unreferenced tail Physics constraint becomes deletable");
		assertTrue(editor.deletePhysicsConstraint(updated) && doc.data.getConstraints().isEmpty(),
			"unreferenced tail Physics deletion succeeds");

		SkeletonEditor.PhysicsConstraintSetup invalidFps = new SkeletonEditor.PhysicsConstraintSetup(
			"bad-fps", child, false, ScaleYMode.none, 0f, 0f, 0f, 0f, 0f, 5000f, 0,
			0.5f, 100f, 0.85f, 1f, 0f, 0f, 1f, false, false, false, false, false, false, false);
		assertTrue(editor.validatePhysicsConstraintSetup(null, invalidFps, null, false).contains("FPS"),
			"non-positive Physics FPS is rejected atomically");
		SkeletonEditor.PhysicsConstraintSetup invalidMass = new SkeletonEditor.PhysicsConstraintSetup(
			"bad-mass", child, false, ScaleYMode.none, 0f, 0f, 0f, 0f, 0f, 5000f, 60,
			0.5f, 100f, 0.85f, 0f, 0f, 0f, 1f, false, false, false, false, false, false, false);
		assertTrue(editor.validatePhysicsConstraintSetup(null, invalidMass, null, false).contains("质量"),
			"non-positive Physics mass is rejected atomically");
		SkeletonEditor.PhysicsConstraintSetup inactiveSkin = new SkeletonEditor.PhysicsConstraintSetup(
			"inactive", child, false, ScaleYMode.none, 0f, 0f, 0f, 0f, 0f, 5000f, 60,
			0.5f, 100f, 0.85f, 1f, 0f, 0f, 1f, false, false, false, false, false, false, false);
		assertTrue(editor.validatePhysicsConstraintSetup(null, inactiveSkin, skin, true).contains("皮肤必需"),
			"non-skin-required Physics cannot create a misleading skin activation reference");
	}

	private static void managesSliderConstraintSetupSafely() {
		SkeletonDocument doc = createDocument();
		BoneData child = doc.data.findBone("child");
		Animation driven = new Animation("driven");
		driven.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		driven.setDuration(2f);
		doc.data.getAnimations().add(driven);
		Skin firstSkin = new Skin("default");
		Skin secondSkin = new Skin("slider-skin-b");
		doc.data.getSkins().add(firstSkin, secondSkin);
		doc.data.setDefaultSkin(firstSkin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(firstSkin);
		SkeletonEditor editor = new SkeletonEditor(doc);

		SkeletonEditor.SliderConstraintSetup propertySetup = new SkeletonEditor.SliderConstraintSetup(
			"property-slider", driven, true, true, true, child, SkeletonEditor.TransformProperty.X,
			-5f, 2f, 0.25f, 120f, true, 99f, 0.6f);
		assertTrue(editor.validateSliderConstraintSetup(null, propertySetup, firstSkin, true) == null,
			"valid property-driven Slider setup passes validation");
		long revision = doc.revision;
		SliderData created = editor.addSliderConstraint(propertySetup, firstSkin, true);
		assertTrue(created != null && doc.data.getConstraints().first() == created,
			"Slider setup is appended to the unified constraint list");
		assertTrue(firstSkin.getConstraints().contains(created, true),
			"new skin-required Slider constraint is active in the chosen skin");
		assertTrue(doc.skeleton.findConstraint("property-slider", com.esotericsoftware.spine.Slider.class) != null,
			"runtime skeleton is rebuilt with the new Slider constraint");
		assertTrue(doc.dirty && doc.revision == revision + 1,
			"adding Slider setup marks the document once");
		SkeletonEditor.SliderConstraintSetup stored = SkeletonEditor.sliderConstraintSetup(created);
		assertTrue(stored.animation() == driven && stored.bone() == child
			&& stored.property() == SkeletonEditor.TransformProperty.X,
			"Slider animation, bone, and property references are retained");
		assertNear(-5f, stored.sourceOffset(), "Slider source offset");
		assertNear(2f, stored.targetOffset(), "Slider target offset");
		assertNear(0.25f, stored.scale(), "Slider property scale");
		assertNear(120f, stored.max(), "Slider property maximum");
		assertNear(0f, stored.time(), "property-driven Slider canonicalizes the unused setup time");

		Animation controller = new Animation("controller");
		Animation.SliderTimeline timeTimeline = new Animation.SliderTimeline(1, 0, 0);
		timeTimeline.setFrame(0, 0f, 0.4f);
		Animation.SliderMixTimeline mixTimeline = new Animation.SliderMixTimeline(1, 0, 0);
		mixTimeline.setFrame(0, 0f, 0.75f);
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(timeTimeline, mixTimeline);
		controller.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(controller);

		SkeletonEditor.SliderConstraintSetup timeSetup = new SkeletonEditor.SliderConstraintSetup(
			"time-slider", driven, true, false, false, null, null,
			0f, 0f, 1f, 0f, false, 0.35f, 0.8f);
		SliderData updated = editor.updateSliderConstraint(created, timeSetup, secondSkin, true);
		assertTrue(updated != null && updated != created && doc.data.getConstraints().first() == updated,
			"Slider setup update replaces immutable-name data at the same index");
		assertTrue(firstSkin.getConstraints().contains(updated, true)
			&& secondSkin.getConstraints().contains(updated, true),
			"Slider replacement migrates every skin reference and activates the selected skin");
		assertTrue(timeTimeline.getConstraintIndex() == 0 && mixTimeline.getConstraintIndex() == 0,
			"Slider animation indices remain valid after setup replacement");
		stored = SkeletonEditor.sliderConstraintSetup(updated);
		assertTrue(stored.bone() == null && stored.property() == null && !stored.local(),
			"time-driven Slider clears property-only runtime data");
		assertNear(0.35f, stored.time(), "time-driven Slider retains setup time");
		assertNear(0.8f, stored.mix(), "Slider setup mix is retained");
		assertTrue(editor.updateSliderConstraint(updated, timeSetup, secondSkin, true) == updated,
			"equivalent Slider setup update is a no-op");

		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"name\": \"time-slider\"")
			&& json.contains("\"type\": \"slider\"") && json.contains("\"time\": 0.35"),
			"managed Slider setup exports through standard Spine JSON");
		com.esotericsoftware.spine.SkeletonJson loader = new com.esotericsoftware.spine.SkeletonJson(
			new com.badlogic.gdx.graphics.g2d.TextureAtlas());
		SkeletonData loaded = loader.readSkeletonData(new com.badlogic.gdx.utils.JsonReader().parse(json));
		SliderData loadedSlider = loaded.findConstraint("time-slider", SliderData.class);
		assertTrue(loadedSlider != null && loadedSlider.getAnimation() == loaded.findAnimation("driven"),
			"managed Slider animation reference round trips through Spine JSON");
		assertNear(0.35f, loadedSlider.getSetupPose().getTime(), "managed Slider setup time round trip");

		assertTrue(editor.getSliderConstraintDeleteBlockReason(updated).contains("皮肤"),
			"skin activation blocks destructive Slider deletion");
		SkeletonEditor.SliderConstraintSetup unskinned = new SkeletonEditor.SliderConstraintSetup(
			"time-slider", driven, false, false, false, null, null,
			0f, 0f, 1f, 0f, false, 0.35f, 0.8f);
		updated = editor.updateSliderConstraint(updated, unskinned, secondSkin, false);
		assertTrue(firstSkin.getConstraints().isEmpty() && secondSkin.getConstraints().isEmpty(),
			"disabling skin-required removes all Slider skin activations");
		assertTrue(editor.getSliderConstraintDeleteBlockReason(updated).contains("动画"),
			"Slider time and mix timelines block destructive setup deletion");
		controller.getTimelines().clear();
		assertTrue(editor.getSliderConstraintDeleteBlockReason(updated) == null,
			"unreferenced tail Slider constraint becomes deletable");
		assertTrue(editor.deleteSliderConstraint(updated) && doc.data.getConstraints().isEmpty(),
			"unreferenced tail Slider deletion succeeds");

		SkeletonEditor.SliderConstraintSetup invalidAnimation = new SkeletonEditor.SliderConstraintSetup(
			"invalid-animation", new Animation("foreign"), false, false, false, null, null,
			0f, 0f, 1f, 0f, false, 0f, 1f);
		assertTrue(editor.validateSliderConstraintSetup(null, invalidAnimation, null, false).contains("动画"),
			"foreign Slider animation is rejected atomically");
		Animation zeroDuration = new Animation("zero-duration");
		zeroDuration.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		doc.data.getAnimations().add(zeroDuration);
		SkeletonEditor.SliderConstraintSetup zeroDurationLoop = new SkeletonEditor.SliderConstraintSetup(
			"zero-duration-loop", zeroDuration, false, false, true, null, null,
			0f, 0f, 1f, 0f, false, 0f, 1f);
		assertTrue(editor.validateSliderConstraintSetup(null, zeroDurationLoop, null, false).contains("时长"),
			"looping Slider rejects a zero-duration target before runtime modulo can produce NaN");
		SkeletonEditor.SliderConstraintSetup missingProperty = new SkeletonEditor.SliderConstraintSetup(
			"missing-property", driven, false, false, false, child, null,
			0f, 0f, 1f, 0f, false, 0f, 1f);
		assertTrue(editor.validateSliderConstraintSetup(null, missingProperty, null, false).contains("属性"),
			"property-driven Slider requires a transform property");
		SkeletonEditor.SliderConstraintSetup invalidNumber = new SkeletonEditor.SliderConstraintSetup(
			"invalid-number", driven, false, false, false, null, null,
			0f, 0f, 1f, 0f, false, Float.NaN, 1f);
		assertTrue(editor.validateSliderConstraintSetup(null, invalidNumber, null, false).contains("有限"),
			"non-finite Slider values are rejected atomically");
		SkeletonEditor.SliderConstraintSetup inactiveSkin = new SkeletonEditor.SliderConstraintSetup(
			"inactive", driven, false, false, false, null, null,
			0f, 0f, 1f, 0f, false, 0f, 1f);
		assertTrue(editor.validateSliderConstraintSetup(null, inactiveSkin, firstSkin, true).contains("皮肤必需"),
			"non-skin-required Slider cannot create a misleading skin activation reference");
	}

	private static void deletesMiddleConstraintAndReindexesTimelines() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.findBone("root");
		BoneData child = doc.data.findBone("child");
		SlotData pathSlot = new SlotData(0, "constraint-path", root);
		pathSlot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(pathSlot);
		Animation driven = new Animation("slider-driven");
		driven.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		doc.data.getAnimations().add(driven);
		doc.skeleton = new Skeleton(doc.data);
		SkeletonEditor editor = new SkeletonEditor(doc);

		IkConstraintData removed = editor.addIkConstraint(new SkeletonEditor.IkConstraintSetup(
			"remove-first", child, null, root, false, ScaleYMode.none, 1f, 0f, 1, false, false), null, false);
		IkConstraintData ik = editor.addIkConstraint(new SkeletonEditor.IkConstraintSetup(
			"keep-ik", child, null, root, false, ScaleYMode.none, 0.8f, 0f, 1, false, false), null, false);
		TransformConstraintData transform = editor.addTransformConstraint(new SkeletonEditor.TransformConstraintSetup(
			"keep-transform", java.util.List.of(child), root, false, false, false, false, false,
			0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
			SkeletonEditor.defaultTransformProperties()), null, false);
		PathConstraintData path = editor.addPathConstraint(new SkeletonEditor.PathConstraintSetup(
			"keep-path", java.util.List.of(child), pathSlot, false, PathConstraintData.PositionMode.fixed,
			PathConstraintData.SpacingMode.fixed, PathConstraintData.RotateMode.chain,
			0f, 0f, 0f, 1f, 1f, 1f), null, false);
		PhysicsConstraintData physics = editor.addPhysicsConstraint(new SkeletonEditor.PhysicsConstraintSetup(
			"keep-physics", child, false, ScaleYMode.none, 0f, 0f, 0f, 0f, 0f, 5000f, 60,
			0.5f, 100f, 0.85f, 1f, 0f, 0f, 1f,
			false, false, false, false, false, false, false), null, false);
		SliderData slider = editor.addSliderConstraint(new SkeletonEditor.SliderConstraintSetup(
			"keep-slider", driven, false, false, false, null, null,
			0f, 0f, 1f, 0f, false, 0.25f, 1f), null, false);
		assertTrue(removed != null && ik != null && transform != null && path != null && physics != null && slider != null,
			"mixed constraints are valid before middle deletion");

		Array<Animation.Timeline> timelines = new Array<>(true, 9, Animation.Timeline[]::new);
		Animation.IkConstraintTimeline ikTimeline = new Animation.IkConstraintTimeline(2, 0, 1);
		ikTimeline.setFrame(0, 0f, 0.4f, 0f, 1, false, false);
		ikTimeline.setFrame(1, 1f, 0.9f, 0f, -1, false, true);
		ikTimeline.setStepped(0);
		timelines.add(ikTimeline);
		Animation.TransformConstraintTimeline transformTimeline = new Animation.TransformConstraintTimeline(2, 0, 2);
		transformTimeline.setFrame(0, 0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f);
		transformTimeline.setFrame(1, 1f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f, 0.1f);
		transformTimeline.setStepped(0);
		timelines.add(transformTimeline);
		Animation.PathConstraintPositionTimeline position = new Animation.PathConstraintPositionTimeline(2, 0, 3);
		position.setFrame(0, 0f, 2f);
		position.setFrame(1, 1f, 8f);
		position.setStepped(0);
		timelines.add(position);
		Animation.PathConstraintSpacingTimeline spacing = new Animation.PathConstraintSpacingTimeline(2, 0, 3);
		spacing.setFrame(0, 0f, 3f);
		spacing.setFrame(1, 1f, 9f);
		spacing.setStepped(0);
		timelines.add(spacing);
		Animation.PathConstraintMixTimeline pathMix = new Animation.PathConstraintMixTimeline(2, 0, 3);
		pathMix.setFrame(0, 0f, 0.2f, 0.3f, 0.4f);
		pathMix.setFrame(1, 1f, 0.7f, 0.8f, 0.9f);
		pathMix.setStepped(0);
		timelines.add(pathMix);
		Animation.PhysicsConstraintInertiaTimeline inertia = new Animation.PhysicsConstraintInertiaTimeline(2, 0, 4);
		inertia.setFrame(0, 0f, 0.25f);
		inertia.setFrame(1, 1f, 0.75f);
		inertia.setStepped(0);
		timelines.add(inertia);
		Animation.PhysicsConstraintResetTimeline reset = new Animation.PhysicsConstraintResetTimeline(2, 4);
		reset.setFrame(0, 0.2f);
		reset.setFrame(1, 0.8f);
		timelines.add(reset);
		Animation.SliderTimeline sliderTime = new Animation.SliderTimeline(2, 0, 5);
		sliderTime.setFrame(0, 0f, 0.1f);
		sliderTime.setFrame(1, 1f, 0.9f);
		sliderTime.setStepped(0);
		timelines.add(sliderTime);
		Animation.SliderMixTimeline sliderMix = new Animation.SliderMixTimeline(2, 0, 5);
		sliderMix.setFrame(0, 0f, 0.3f);
		sliderMix.setFrame(1, 1f, 0.7f);
		sliderMix.setStepped(0);
		timelines.add(sliderMix);
		Animation controller = new Animation("constraint-controller");
		controller.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(controller);

		Animation.Timeline[] originals = new Animation.Timeline[timelines.size];
		for (int index = 0; index < timelines.size; index++) originals[index] = timelines.get(index);
		long revision = doc.revision;
		assertTrue(editor.getIkConstraintDeleteBlockReason(removed) == null,
			"an unreferenced non-tail constraint is deletable");
		assertTrue(editor.deleteIkConstraint(removed), "middle constraint deletion succeeds");
		assertTrue(doc.data.getConstraints().size == 5 && doc.data.getConstraints().get(0) == ik
			&& doc.data.getConstraints().get(4) == slider,
			"later constraint data shifts left without changing identity");
		assertTrue(doc.revision == revision + 1, "middle deletion marks one document revision");
		for (int index = 0; index < originals.length; index++) {
			Animation.Timeline shifted = controller.getTimelines().get(index);
			assertTrue(shifted != originals[index], "shifted constraint timeline is rebuilt at row " + index);
			assertTrue(((Animation.ConstraintTimeline)shifted).getConstraintIndex()
				== ((Animation.ConstraintTimeline)originals[index]).getConstraintIndex() - 1,
				"shifted constraint timeline targets the same logical constraint at row " + index);
			assertTrue(java.util.Arrays.equals(originals[index].getFrames(), shifted.getFrames()),
				"shifted constraint timeline preserves frame values at row " + index);
			if (originals[index] instanceof Animation.CurveTimeline sourceCurve
				&& shifted instanceof Animation.CurveTimeline shiftedCurve)
				assertTrue(sourceCurve.getCurveType(0) == shiftedCurve.getCurveType(0),
					"shifted constraint timeline preserves curves at row " + index);
		}
		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		SkeletonData loaded = new com.esotericsoftware.spine.SkeletonJson(
			new com.badlogic.gdx.graphics.g2d.TextureAtlas()).readSkeletonData(
				new com.badlogic.gdx.utils.JsonReader().parse(json));
		assertTrue(loaded.findConstraint("keep-ik", IkConstraintData.class) != null
			&& loaded.findConstraint("keep-slider", SliderData.class) != null,
			"reindexed mixed constraints round trip through official Spine JSON");
	}

	private static void reordersMixedConstraintsAndRemapsTimelines() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.findBone("root");
		BoneData child = doc.data.findBone("child");
		SlotData pathSlot = new SlotData(0, "ordered-path", root);
		pathSlot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(pathSlot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		Animation driven = new Animation("ordered-driven");
		driven.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		doc.data.getAnimations().add(driven);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);
		SkeletonEditor editor = new SkeletonEditor(doc);

		IkConstraintData ik = editor.addIkConstraint(new SkeletonEditor.IkConstraintSetup(
			"ordered-ik", child, null, root, false, ScaleYMode.none, 0.8f, 0f, 1, false, false), null, false);
		TransformConstraintData transform = editor.addTransformConstraint(new SkeletonEditor.TransformConstraintSetup(
			"ordered-transform", java.util.List.of(child), root, true, false, false, false, false,
			0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
			SkeletonEditor.defaultTransformProperties()), skin, true);
		PathConstraintData path = editor.addPathConstraint(new SkeletonEditor.PathConstraintSetup(
			"ordered-path", java.util.List.of(child), pathSlot, false, PathConstraintData.PositionMode.fixed,
			PathConstraintData.SpacingMode.fixed, PathConstraintData.RotateMode.chain,
			0f, 0f, 0f, 1f, 1f, 1f), null, false);
		PhysicsConstraintData physics = editor.addPhysicsConstraint(new SkeletonEditor.PhysicsConstraintSetup(
			"ordered-physics", child, false, ScaleYMode.none, 0f, 0f, 0f, 0f, 0f, 5000f, 60,
			0.5f, 100f, 0.85f, 1f, 0f, 0f, 1f,
			false, false, false, false, false, true, false), null, false);
		SliderData slider = editor.addSliderConstraint(new SkeletonEditor.SliderConstraintSetup(
			"ordered-slider", driven, true, false, false, null, null,
			0f, 0f, 1f, 0f, false, 0.25f, 1f), skin, true);
		assertTrue(ik != null && transform != null && path != null && physics != null && slider != null,
			"all mixed constraint types exist before reordering");

		Array<Animation.Timeline> timelines = new Array<>(true, 6, Animation.Timeline[]::new);
		Animation.IkConstraintTimeline ikTimeline = new Animation.IkConstraintTimeline(2, 0, 0);
		ikTimeline.setFrame(0, 0f, 0.4f, 0f, 1, false, false);
		ikTimeline.setFrame(1, 1f, 0.9f, 0f, -1, false, true);
		ikTimeline.setStepped(0);
		timelines.add(ikTimeline);
		Animation.TransformConstraintTimeline transformTimeline = new Animation.TransformConstraintTimeline(2, 0, 1);
		transformTimeline.setFrame(0, 0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f);
		transformTimeline.setFrame(1, 1f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f, 0.1f);
		transformTimeline.setStepped(0);
		timelines.add(transformTimeline);
		Animation.PathConstraintMixTimeline pathTimeline = new Animation.PathConstraintMixTimeline(2, 0, 2);
		pathTimeline.setFrame(0, 0f, 0.2f, 0.3f, 0.4f);
		pathTimeline.setFrame(1, 1f, 0.7f, 0.8f, 0.9f);
		pathTimeline.setStepped(0);
		timelines.add(pathTimeline);
		Animation.PhysicsConstraintInertiaTimeline physicsTimeline =
			new Animation.PhysicsConstraintInertiaTimeline(2, 0, 3);
		physicsTimeline.setFrame(0, 0f, 0.25f);
		physicsTimeline.setFrame(1, 1f, 0.75f);
		physicsTimeline.setStepped(0);
		timelines.add(physicsTimeline);
		Animation.SliderTimeline sliderTimeline = new Animation.SliderTimeline(2, 0, 4);
		sliderTimeline.setFrame(0, 0f, 0.1f);
		sliderTimeline.setFrame(1, 1f, 0.9f);
		sliderTimeline.setStepped(0);
		timelines.add(sliderTimeline);
		Animation.PhysicsConstraintGravityTimeline globalPhysics =
			new Animation.PhysicsConstraintGravityTimeline(2, 0, -1);
		globalPhysics.setFrame(0, 0f, -2f);
		globalPhysics.setFrame(1, 1f, 5f);
		globalPhysics.setStepped(0);
		timelines.add(globalPhysics);
		Animation controller = new Animation("ordered-controller");
		controller.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(controller);

		Animation.Timeline[] originals = timelines.toArray(Animation.Timeline[]::new);
		long revision = doc.revision;
		assertTrue(editor.moveConstraint(slider, 1), "a Slider can move across other constraint types");
		assertTrue(doc.data.getConstraints().get(0) == ik && doc.data.getConstraints().get(1) == slider
			&& doc.data.getConstraints().get(2) == transform && doc.data.getConstraints().get(3) == path
			&& doc.data.getConstraints().get(4) == physics,
			"unified constraint execution order reflects the requested move");
		assertTrue(doc.revision == revision + 1, "constraint reordering marks one document revision");
		assertTrue(skin.getConstraints().contains(transform, true) && skin.getConstraints().contains(slider, true),
			"constraint reordering preserves skin activation object references");

		int[] expectedIndices = {0, 2, 3, 4, 1, -1};
		for (int index = 0; index < originals.length; index++) {
			Animation.Timeline reordered = controller.getTimelines().get(index);
			int oldIndex = ((Animation.ConstraintTimeline)originals[index]).getConstraintIndex();
			assertTrue(((Animation.ConstraintTimeline)reordered).getConstraintIndex() == expectedIndices[index],
				"constraint timeline follows its logical target at row " + index);
			assertTrue((oldIndex == expectedIndices[index]) == (reordered == originals[index]),
				"only timelines with changed indices are rebuilt at row " + index);
			assertTrue(java.util.Arrays.equals(originals[index].getFrames(), reordered.getFrames()),
				"constraint timeline frame values survive reordering at row " + index);
			if (originals[index] instanceof Animation.CurveTimeline sourceCurve
				&& reordered instanceof Animation.CurveTimeline reorderedCurve)
				assertTrue(sourceCurve.getCurveType(0) == reorderedCurve.getCurveType(0),
					"constraint timeline curve survives reordering at row " + index);
		}
		assertTrue(!editor.moveConstraint(slider, 1), "moving a constraint to its current index is a no-op");
		assertThrows(IllegalArgumentException.class, () -> editor.moveConstraint(slider, 5),
			"constraint reordering rejects an out-of-range target atomically");

		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		SkeletonData loaded = new com.esotericsoftware.spine.SkeletonJson(
			new com.badlogic.gdx.graphics.g2d.TextureAtlas()).readSkeletonData(
				new com.badlogic.gdx.utils.JsonReader().parse(json));
		assertTrue("ordered-slider".equals(loaded.getConstraints().get(1).getName())
			&& "ordered-transform".equals(loaded.getConstraints().get(2).getName()),
			"reordered mixed constraint sequence round trips through official Spine JSON");
		Animation loadedController = loaded.findAnimation("ordered-controller");
		assertTrue(findTimeline(loadedController, Animation.TransformConstraintTimeline.class).getConstraintIndex() == 2
			&& findTimeline(loadedController, Animation.SliderTimeline.class).getConstraintIndex() == 1
			&& findTimeline(loadedController, Animation.PhysicsConstraintGravityTimeline.class).getConstraintIndex() == -1,
			"official JSON reload resolves reordered and global constraint timeline targets");
	}

	private static void managesPathConstraintSetupSafely() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.findBone("root");
		BoneData child = doc.data.findBone("child");
		SlotData slot = new SlotData(0, "path-slot", root);
		slot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);
		SkeletonEditor editor = new SkeletonEditor(doc);

		SkeletonEditor.PathConstraintSetup setup = new SkeletonEditor.PathConstraintSetup(
			"follow-path", java.util.List.of(child), slot, true, PathConstraintData.PositionMode.fixed,
			PathConstraintData.SpacingMode.proportional, PathConstraintData.RotateMode.chainScale,
			15f, 20f, 0.25f, 0.7f, 0.5f, 0.35f);
		assertTrue(editor.validatePathConstraintSetup(null, setup, skin, true) == null,
			"valid Path setup passes validation");
		long revision = doc.revision;
		PathConstraintData created = editor.addPathConstraint(setup, skin, true);
		assertTrue(created != null && doc.data.getConstraints().first() == created,
			"Path setup is appended to the unified constraint list");
		assertTrue(skin.getConstraints().contains(created, true),
			"new skin-required Path constraint is active in the chosen skin");
		assertTrue(doc.skeleton.findConstraint("follow-path", com.esotericsoftware.spine.PathConstraint.class) != null,
			"runtime skeleton is rebuilt with the new Path constraint");
		assertTrue(doc.dirty && doc.revision == revision + 1,
			"adding Path setup marks the document once");
		assertTrue(SkeletonEditor.pathConstraintSetup(created).equals(setup),
			"all Path setup values and modes are retained");

		Animation animation = new Animation("path-edit");
		Animation.PathConstraintMixTimeline timeline = new Animation.PathConstraintMixTimeline(1, 0, 0);
		timeline.setFrame(0, 0f, 0.4f, 0.5f, 0.6f);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(timeline);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);

		SkeletonEditor.PathConstraintSetup renamedSetup = new SkeletonEditor.PathConstraintSetup(
			"follow-path-renamed", java.util.List.of(child), slot, true, PathConstraintData.PositionMode.percent,
			PathConstraintData.SpacingMode.fixed, PathConstraintData.RotateMode.chain,
			-8f, 0.6f, 12f, 0.9f, 0.8f, 0.65f);
		PathConstraintData updated = editor.updatePathConstraint(created, renamedSetup, skin, false);
		assertTrue(updated != null && updated != created && doc.data.getConstraints().first() == updated,
			"Path setup update replaces immutable-name data at the same index");
		assertTrue(!skin.getConstraints().contains(updated, true),
			"current-skin Path activation can be disabled");
		assertTrue(timeline.getConstraintIndex() == 0,
			"Path animation index remains valid after setup replacement");
		assertTrue(doc.skeleton.findConstraint("follow-path-renamed", com.esotericsoftware.spine.PathConstraint.class) != null,
			"runtime skeleton uses the updated Path name");
		assertTrue(editor.updatePathConstraint(updated, renamedSetup, skin, false) == updated,
			"equivalent Path setup update is a no-op");

		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"name\": \"follow-path-renamed\"")
			&& json.contains("\"type\": \"path\"") && json.contains("\"spacingMode\": \"fixed\""),
			"managed Path setup and timeline export through standard Spine JSON");
		assertTrue(editor.getPathConstraintDeleteBlockReason(updated).contains("动画"),
			"Path animation timeline blocks destructive setup deletion");
		animation.getTimelines().clear();
		assertTrue(editor.getPathConstraintDeleteBlockReason(updated) == null,
			"unreferenced tail Path constraint becomes deletable");
		assertTrue(editor.deletePathConstraint(updated) && doc.data.getConstraints().isEmpty(),
			"unreferenced tail Path deletion succeeds");

		SkeletonEditor.PathConstraintSetup cyclic = new SkeletonEditor.PathConstraintSetup(
			"cyclic", java.util.List.of(root), slot, false, PathConstraintData.PositionMode.percent,
			PathConstraintData.SpacingMode.length, PathConstraintData.RotateMode.tangent,
			0f, 0f, 0f, 1f, 1f, 1f);
		assertTrue(editor.validatePathConstraintSetup(null, cyclic, null, false).contains("子树"),
			"Path target slot dependency cycles are rejected");
		SkeletonEditor.PathConstraintSetup invalid = new SkeletonEditor.PathConstraintSetup(
			"invalid", java.util.List.of(child), slot, false, PathConstraintData.PositionMode.percent,
			PathConstraintData.SpacingMode.length, PathConstraintData.RotateMode.tangent,
			0f, Float.NaN, 0f, 1f, 1f, 1f);
		assertTrue(editor.validatePathConstraintSetup(null, invalid, null, false).contains("有限"),
			"non-finite Path setup is rejected atomically");
	}

	private static void updatesSlotPropertiesSafely() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		SlotData slot = editor.addSlot("child-slot", doc.data.findBone("child"));
		doc.dirty = false;
		long revision = doc.revision;

		Color color = new Color(0.2f, 0.4f, 0.6f, 0.8f);
		Color darkColor = new Color(0.1f, 0.3f, 0.5f, 0.25f);
		assertTrue(editor.updateSlotProperties(slot, color, darkColor, BlendMode.multiply, false),
			"changed slot properties should be accepted");
		assertNear(0.2f, slot.getSetupPose().getColor().r, "slot setup red");
		assertNear(0.4f, slot.getSetupPose().getColor().g, "slot setup green");
		assertNear(0.6f, slot.getSetupPose().getColor().b, "slot setup blue");
		assertNear(0.8f, slot.getSetupPose().getColor().a, "slot setup alpha");
		assertNear(0.1f, slot.getSetupPose().getDarkColor().r, "slot setup dark red");
		assertNear(0.3f, slot.getSetupPose().getDarkColor().g, "slot setup dark green");
		assertNear(0.5f, slot.getSetupPose().getDarkColor().b, "slot setup dark blue");
		assertNear(1f, slot.getSetupPose().getDarkColor().a, "unused dark alpha is normalized");
		assertTrue(slot.getBlendMode() == BlendMode.multiply, "slot blend is updated");
		assertTrue(!slot.getVisible(), "slot visibility is updated");
		assertTrue(doc.dirty && doc.revision == revision + 1, "slot changes mark the document once");
		assertNear(0.2f, doc.skeleton.findSlot("child-slot").getPose().getColor().r,
			"runtime slot is rebuilt from setup color");
		assertNear(0.3f, doc.skeleton.findSlot("child-slot").getPose().getDarkColor().g,
			"runtime slot enables two color tinting");

		doc.dirty = false;
		revision = doc.revision;
		assertTrue(!editor.updateSlotProperties(slot, new Color(color), new Color(darkColor), BlendMode.multiply, false),
			"unchanged slot properties should be a no-op");
		assertTrue(!doc.dirty && doc.revision == revision, "a no-op must not mark the document dirty");

		Color invalid = new Color(color);
		invalid.r = Float.NaN;
		assertTrue(!editor.updateSlotProperties(slot, invalid, darkColor, BlendMode.normal, true),
			"non-finite slot colors should be rejected");
		Color invalidDark = new Color(darkColor);
		invalidDark.g = 2f;
		assertTrue(!editor.updateSlotProperties(slot, Color.WHITE, invalidDark, BlendMode.normal, true),
			"out-of-range dark colors should reject the complete slot edit");
		SlotData foreign = new SlotData(0, "foreign", doc.data.findBone("child"));
		assertTrue(!editor.updateSlotProperties(foreign, Color.WHITE, null, BlendMode.normal, true),
			"slots outside the document should be rejected");
		assertTrue(!doc.dirty && doc.revision == revision, "rejected slot updates must not mutate the document");
		assertNear(0.2f, slot.getSetupPose().getColor().r, "rejected edit keeps light color");
		assertNear(0.3f, slot.getSetupPose().getDarkColor().g, "rejected edit keeps dark color");
		assertTrue(slot.getBlendMode() == BlendMode.multiply && !slot.getVisible(),
			"rejected edit keeps other slot properties");

		assertTrue(editor.updateSlotProperties(slot, color, null, BlendMode.multiply, false),
			"two color tinting can be disabled");
		assertTrue(slot.getSetupPose().getDarkColor() == null, "setup dark color is cleared");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getDarkColor() == null,
			"runtime two color tinting is disabled after rebuild");
		assertTrue(doc.revision == revision + 1, "disabling two color tinting marks the document once");
	}

	private static void twoColorTimelinesProtectSetupDarkColor() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		SlotData slot = editor.addSlot("two-color-slot", doc.data.findBone("child"));
		Color light = new Color(0.2f, 0.4f, 0.6f, 0.8f);
		Color dark = new Color(0.1f, 0.2f, 0.3f, 1f);
		assertTrue(editor.updateSlotProperties(slot, light, dark, BlendMode.multiply, false),
			"two color setup should be enabled before keying");

		AnimationEditor animations = new AnimationEditor(doc);
		var rgb2Animation = animations.addAnimation("rgb2-blocker");
		animations.addTwoColorKeyframe(rgb2Animation, slot.getIndex(), 0f,
			1f, 0.8f, 0.6f, 1f, 0.1f, 0.2f, 0.3f, false);
		String blockReason = editor.getSlotDarkColorDisableBlockReason(slot);
		assertTrue(blockReason != null && blockReason.contains("rgb2-blocker") && blockReason.contains("RGB2"),
			"RGB2 track reports the animation that blocks disabling the setup dark color");
		doc.dirty = false;
		long revision = doc.revision;
		assertTrue(!editor.updateSlotProperties(slot, Color.WHITE, null, BlendMode.normal, true),
			"RGB2 track blocks the complete slot edit when it would disable two color setup");
		assertColor(light, slot.getSetupPose().getColor(), "blocked slot light color");
		assertNear(0.2f, slot.getSetupPose().getDarkColor().g, "blocked slot dark color");
		assertTrue(slot.getBlendMode() == BlendMode.multiply && !slot.getVisible(),
			"blocked slot edit preserves blend mode and visibility");
		assertTrue(!doc.dirty && doc.revision == revision, "blocked slot edit is mutation-free");

		assertTrue(animations.removeTwoColorKeyframe(rgb2Animation, slot.getIndex(), 0f),
			"removing the final RGB2 key removes its track");
		assertTrue(editor.getSlotDarkColorDisableBlockReason(slot) == null,
			"slot setup dark color can be disabled after its RGB2 track is removed");
		assertTrue(editor.updateSlotProperties(slot, Color.WHITE, null, BlendMode.normal, true),
			"slot setup dark color is disabled after removing the RGB2 track");
		assertTrue(slot.getSetupPose().getDarkColor() == null
			&& doc.skeleton.findSlot(slot.getName()).getPose().getDarkColor() == null,
			"setup and runtime dark colors are cleared together");

		assertTrue(editor.updateSlotProperties(slot, Color.WHITE, Color.BLACK, BlendMode.normal, true),
			"two color setup can be enabled again");
		var rgba2Animation = animations.addAnimation("rgba2-blocker");
		animations.addTwoColorKeyframe(rgba2Animation, slot.getIndex(), 0f,
			1f, 1f, 1f, 0.75f, 0f, 0f, 0f, true);
		blockReason = editor.getSlotDarkColorDisableBlockReason(slot);
		assertTrue(blockReason != null && blockReason.contains("rgba2-blocker") && blockReason.contains("RGBA2"),
			"RGBA2 track also blocks disabling the setup dark color");
	}

	private static void updatesRegionAttachmentSafely() {
		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "child-slot", doc.data.findBone("child"));
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		RegionAttachment region = new RegionAttachment("region", sequence);
		region.setWidth(10f);
		region.setHeight(20f);
		region.updateSequence();
		skin.setAttachment(0, "region", region);
		slot.setAttachmentName("region");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);
		doc.skeleton.setupPoseSlots();
		SkeletonEditor editor = new SkeletonEditor(doc);
		doc.dirty = false;
		long revision = doc.revision;

		Color tint = new Color(0.2f, 0.4f, 0.6f, 0.8f);
		assertTrue(editor.updateRegionAttachment(region, tint, 6000f, -6000f, 25f, -25f, 4000f, 4096f, 3072f),
			"changed region properties should be accepted");
		assertColor(tint, region.getColor(), "region tint");
		assertNear(6000f, region.getX(), "region x outside inspector slider range");
		assertNear(-6000f, region.getY(), "region y outside inspector slider range");
		assertNear(25f, region.getScaleX(), "region scale x outside inspector slider range");
		assertNear(-25f, region.getScaleY(), "region scale y outside inspector slider range");
		assertNear(4000f, region.getRotation(), "region rotation outside inspector slider range");
		assertNear(4096f, region.getWidth(), "region width outside inspector slider range");
		assertNear(3072f, region.getHeight(), "region height outside inspector slider range");
		assertTrue(doc.dirty && doc.revision == revision + 1, "region changes mark the document once");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == region,
			"runtime skeleton keeps the edited attachment after rebuild");

		doc.dirty = false;
		revision = doc.revision;
		assertTrue(!editor.updateRegionAttachment(region, new Color(tint),
			6000f, -6000f, 25f, -25f, 4000f, 4096f, 3072f),
			"unchanged region properties should be a no-op");
		assertTrue(!doc.dirty && doc.revision == revision,
			"an unchanged region update must not mark the document dirty");

		Color invalidColor = new Color(tint);
		invalidColor.a = Float.NaN;
		assertTrue(!editor.updateRegionAttachment(region, invalidColor, 1f, 1f, 1f, 1f, 0f, 10f, 10f),
			"non-finite region tint should be rejected");
		assertTrue(!editor.updateRegionAttachment(region, tint, Float.NaN, 1f, 1f, 1f, 0f, 10f, 10f),
			"non-finite region properties should be rejected");
		assertTrue(!editor.updateRegionAttachment(region, tint, 1f, 1f, 1f, 1f, 0f, 0f, 10f),
			"non-positive region width should be rejected");
		assertTrue(!editor.updateRegionAttachment(region, tint, 1f, 1f, 1f, 1f, 0f, 10f, -1f),
			"non-positive region height should be rejected");
		RegionAttachment foreign = new RegionAttachment("foreign", new Sequence(1, false));
		assertTrue(!editor.updateRegionAttachment(foreign, tint, 1f, 2f, 1f, 1f, 0f, 10f, 20f),
			"attachments outside the document should be rejected");
		assertNear(6000f, region.getX(), "rejected updates preserve region x");
		assertNear(4096f, region.getWidth(), "rejected updates preserve region width");
		assertColor(tint, region.getColor(), "rejected updates preserve region tint");
		assertNear(0f, foreign.getX(), "rejected foreign attachment remains unchanged");
		assertTrue(!doc.dirty && doc.revision == revision,
			"rejected region updates must not mutate the document");
	}

	private static void managesImageSequenceSetupSafely() {
		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "sequence-slot", doc.data.findBone("child"));
		SlotData linkedSlot = new SlotData(1, "sequence-linked-slot", doc.data.findBone("child"));
		SlotData inheritedSlot = new SlotData(2, "sequence-inherited-slot", doc.data.findBone("child"));
		doc.data.getSlots().addAll(slot, linkedSlot, inheritedSlot);
		Skin firstSkin = new Skin("default");
		Skin secondSkin = new Skin("shared");
		doc.data.getSkins().addAll(firstSkin, secondSkin);
		doc.data.setDefaultSkin(firstSkin);
		TextureRegion setupTexture = new TextureRegion();
		Sequence single = new Sequence(1, false);
		single.getRegions()[0] = setupTexture;
		RegionAttachment original = new RegionAttachment("sequence", single);
		original.setPath("setup");
		original.setWidth(40f);
		original.setHeight(20f);
		original.updateSequence();
		firstSkin.setAttachment(0, "sequence", original);
		secondSkin.setAttachment(0, "sequence-copy", original);
		slot.setAttachmentName("sequence");
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(firstSkin);

		TextureRegion[] frames = {new TextureRegion(), new TextureRegion(), new TextureRegion(), new TextureRegion()};
		SkeletonEditor editor = new SkeletonEditor(doc) {
			@Override protected TextureRegion findRegion(String name) {
				if (name == null || !name.startsWith("walk")) return null;
				try {
					int number = Integer.parseInt(name.substring(4));
					return number >= 1 && number <= frames.length ? frames[number - 1] : null;
				} catch (NumberFormatException ignored) {
					return null;
				}
			}
		};
		SkeletonEditor.SequenceAttachmentState setup =
			new SkeletonEditor.SequenceAttachmentState("walk", 3, 1, 2, 1);
		assertTrue(editor.validateSequenceAttachment(original, setup) == null,
			"a complete atlas-backed Sequence setup is valid");
		RegionAttachment replacement = (RegionAttachment)editor.updateSequenceAttachment(original, setup);
		assertTrue(replacement != null && replacement != original,
			"enabling path suffixes replaces the immutable one-frame Sequence attachment");
		assertTrue(firstSkin.getAttachment(0, "sequence") == replacement
			&& secondSkin.getAttachment(0, "sequence-copy") == replacement,
			"every skin owner of a shared attachment is replaced together");
		assertTrue(replacement.getSequence().hasPathSuffix() && replacement.getSequence().getRegions().length == 3,
			"replacement stores the requested Sequence size and suffix mode");
		assertTrue(replacement.getSequence().getRegions()[0] == frames[0]
			&& replacement.getSequence().getRegions()[2] == frames[2],
			"Sequence frame paths resolve to exact atlas regions");
		assertTrue(replacement.getSequence().getSetupIndex() == 1
			&& replacement.getSequence().getDigits() == 2,
			"Sequence setup index and zero padding are preserved");
		assertNear(40f, replacement.getWidth(), "Region width survives Sequence replacement");
		assertNear(20f, replacement.getHeight(), "Region height survives Sequence replacement");

		Animation.SequenceTimeline timeline = new Animation.SequenceTimeline(1, 0, replacement);
		timeline.setFrame(0, 0f, Sequence.SequenceMode.loop, 0, 0.1f);
		Animation animation = new Animation("sequence-animation");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(timeline);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		long revision = doc.revision;
		SkeletonEditor.SequenceAttachmentState shifted =
			new SkeletonEditor.SequenceAttachmentState("walk", 3, 2, 2, 2);
		assertTrue(editor.updateSequenceAttachment(replacement, shifted) == replacement,
			"same-size Sequence edits preserve attachment identity");
		assertTrue(timeline.getAttachment() == replacement,
			"same-size Sequence edits preserve existing animation targets");
		assertTrue(replacement.getSequence().getRegions()[0] == frames[1]
			&& replacement.getSequence().getRegions()[2] == frames[3],
			"same-size edits refresh every atlas frame");
		assertTrue(doc.revision == revision + 1, "same-size Sequence edits mark one document revision");

		revision = doc.revision;
		SkeletonEditor.SequenceAttachmentState expandedState =
			new SkeletonEditor.SequenceAttachmentState("walk", 4, 1, 2, 0);
		assertTrue(editor.validateSequenceAttachment(replacement, expandedState) == null,
			"a referenced Sequence can change size when its timeline target is migrated");
		RegionAttachment expanded = (RegionAttachment)editor.updateSequenceAttachment(replacement, expandedState);
		Animation.SequenceTimeline expandedTimeline =
			(Animation.SequenceTimeline)animation.getTimelines().first();
		assertTrue(expanded != null && expanded != replacement && expandedTimeline != timeline,
			"changing Sequence size replaces both attachment identity and immutable timeline target");
		assertTrue(firstSkin.getAttachment(0, "sequence") == expanded
			&& secondSkin.getAttachment(0, "sequence-copy") == expanded,
			"referenced Sequence replacement updates every owner");
		assertTrue(expandedTimeline.getAttachment() == expanded
			&& java.util.Arrays.equals(timeline.getFrames(), expandedTimeline.getFrames()),
			"Sequence timeline frames survive attachment replacement");
		assertTrue(expanded.getSequence().getRegions().length == 4 && doc.revision == revision + 1,
			"referenced Sequence replacement applies once and marks one revision");
		assertTrue(editor.validateSequenceAttachment(expanded,
			new SkeletonEditor.SequenceAttachmentState("missing", 3, 1, 2, 0)).contains("找不到"),
			"missing atlas frames produce a precise validation error");

		Sequence meshSingle = new Sequence(1, false);
		meshSingle.getRegions()[0] = setupTexture;
		MeshAttachment mesh = new MeshAttachment("sequence-mesh", meshSingle);
		mesh.setPath("setup");
		mesh.setWorldVerticesLength(6);
		mesh.setVertices(new float[] {-2f, -1f, 2f, -1f, 0f, 3f});
		mesh.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0.5f, 1f});
		mesh.setTriangles(new short[] {0, 1, 2});
		mesh.setHullLength(6);
		mesh.setEdges(new short[] {0, 1, 1, 2, 2, 0});
		mesh.setWidth(32f);
		mesh.setHeight(16f);
		mesh.updateSequence();
		firstSkin.setAttachment(0, "sequence-mesh", mesh);

		Sequence independentSequence = new Sequence(1, false);
		independentSequence.getRegions()[0] = setupTexture;
		MeshAttachment independent = new MeshAttachment("sequence-independent", independentSequence);
		independent.setPath("setup");
		independent.setSourceMesh(mesh);
		independent.setTimelineAttachment(independent);
		independent.updateSequence();
		secondSkin.setAttachment(1, "sequence-independent", independent);
		Sequence inheritedSequence = new Sequence(1, false);
		inheritedSequence.getRegions()[0] = setupTexture;
		MeshAttachment inherited = new MeshAttachment("sequence-inherited", inheritedSequence);
		inherited.setPath("setup");
		inherited.setSourceMesh(mesh);
		inherited.setTimelineAttachment(mesh);
		inherited.updateSequence();
		secondSkin.setAttachment(2, "sequence-inherited", inherited);
		mesh.setTimelineSlots(new int[] {2});

		Animation.DeformTimeline meshDeform = new Animation.DeformTimeline(2, 0, 0, mesh);
		meshDeform.setFrame(0, 0.25f, new float[] {-1f, -1f, 3f, -1f, 1f, 3f});
		meshDeform.setFrame(1, 0.75f, new float[] {-2f, -1f, 2f, -1f, 0f, 4f});
		meshDeform.setStepped(0);
		Animation.SequenceTimeline meshSequence = new Animation.SequenceTimeline(1, 0, mesh);
		meshSequence.setFrame(0, 0.5f, Sequence.SequenceMode.loop, 0, 0.1f);
		Animation.DeformTimeline independentDeform = new Animation.DeformTimeline(2, 0, 1, independent);
		independentDeform.setFrame(0, 0.35f, new float[] {-2f, 0f, 2f, 0f, 0f, 4f});
		independentDeform.setFrame(1, 0.85f, new float[] {-1f, -2f, 4f, 0f, 1f, 2f});
		independentDeform.setStepped(0);
		Animation.SequenceTimeline independentSequenceTimeline = new Animation.SequenceTimeline(1, 1, independent);
		independentSequenceTimeline.setFrame(0, 0.6f, Sequence.SequenceMode.pingpong, 0, 0.2f);
		Animation meshAnimation = new Animation("sequence-mesh-animation");
		Array<Animation.Timeline> meshTimelines = new Array<>(true, 4, Animation.Timeline[]::new);
		meshTimelines.add(meshDeform, meshSequence, independentDeform, independentSequenceTimeline);
		meshAnimation.setTimelines(meshTimelines, new IntArray());
		doc.data.getAnimations().add(meshAnimation);

		MeshAttachment meshReplacement = (MeshAttachment)editor.updateSequenceAttachment(mesh,
			new SkeletonEditor.SequenceAttachmentState("walk", 2, 1, 2, 0));
		assertTrue(meshReplacement != null && meshReplacement != mesh,
			"a referenced source Mesh can replace its immutable one-frame Sequence");
		assertTrue(java.util.Arrays.equals(mesh.getVertices(), meshReplacement.getVertices())
			&& java.util.Arrays.equals(mesh.getRegionUVs(), meshReplacement.getRegionUVs())
			&& java.util.Arrays.equals(mesh.getTriangles(), meshReplacement.getTriangles()),
			"Mesh Sequence replacement preserves topology and UV data");
		assertTrue(meshReplacement.getSequence().getRegions().length == 2
			&& meshReplacement.getSequence().getRegions()[1] == frames[1],
			"Mesh replacement receives every requested atlas frame");
		assertTrue(independent.getSourceMesh() == meshReplacement
			&& independent.getTimelineAttachment() == independent,
			"independent Linked Mesh follows the replacement source without losing timeline ownership");
		assertTrue(inherited.getSourceMesh() == meshReplacement
			&& inherited.getTimelineAttachment() == meshReplacement,
			"inherited Linked Mesh follows both replacement source and timeline ownership");
		assertTrue(java.util.Arrays.equals(meshReplacement.getTimelineSlots(), new int[] {2}),
			"replacement source preserves cross-slot timeline activation");
		AnimationEditor animationEditor = new AnimationEditor(doc);
		Animation.DeformTimeline migratedMeshDeform =
			animationEditor.findDeformTimeline(meshAnimation, 0, meshReplacement);
		Animation.SequenceTimeline migratedMeshSequence =
			animationEditor.findSequenceTimeline(meshAnimation, 0, meshReplacement);
		assertTrue(migratedMeshDeform != null && migratedMeshDeform != meshDeform
			&& java.util.Arrays.equals(migratedMeshDeform.getVertices()[0], meshDeform.getVertices()[0])
			&& java.util.Arrays.equals(migratedMeshDeform.getVertices()[1], meshDeform.getVertices()[1])
			&& migratedMeshDeform.getCurveType(0) == Animation.CurveTimeline.STEPPED,
			"source Mesh Deform frames migrate to the replacement identity");
		assertTrue(migratedMeshSequence != null && migratedMeshSequence != meshSequence
			&& java.util.Arrays.equals(migratedMeshSequence.getFrames(), meshSequence.getFrames()),
			"source Mesh Sequence frames migrate to the replacement identity");
		assertTrue(animationEditor.findDeformTimeline(meshAnimation, 1, independent) == independentDeform,
			"source replacement does not disturb independent Linked Mesh tracks");

		MeshAttachment independentReplacement = (MeshAttachment)editor.updateSequenceAttachment(independent,
			new SkeletonEditor.SequenceAttachmentState("walk", 3, 1, 2, 1));
		assertTrue(independentReplacement != null && independentReplacement != independent
			&& independentReplacement.getTimelineAttachment() == independentReplacement,
			"Sequence replacement preserves an independent Linked Mesh self timeline target");
		assertTrue(independentReplacement.getSourceMesh() == meshReplacement
			&& secondSkin.getAttachment(1, "sequence-independent") == independentReplacement,
			"independent Linked Mesh replacement preserves its source and owner");
		Animation.DeformTimeline migratedIndependentDeform =
			animationEditor.findDeformTimeline(meshAnimation, 1, independentReplacement);
		Animation.SequenceTimeline migratedIndependentSequence =
			animationEditor.findSequenceTimeline(meshAnimation, 1, independentReplacement);
		assertTrue(migratedIndependentDeform != null && migratedIndependentDeform != independentDeform
			&& java.util.Arrays.equals(migratedIndependentDeform.getVertices()[0], independentDeform.getVertices()[0])
			&& java.util.Arrays.equals(migratedIndependentDeform.getVertices()[1], independentDeform.getVertices()[1])
			&& migratedIndependentDeform.getCurveType(0) == Animation.CurveTimeline.STEPPED,
			"independent Linked Mesh Deform follows Sequence identity replacement");
		assertTrue(migratedIndependentSequence != null
			&& migratedIndependentSequence.getAttachment() == independentReplacement,
			"independent Linked Mesh Sequence timeline follows replacement identity");

		MeshAttachment inheritedReplacement = (MeshAttachment)editor.updateSequenceAttachment(inherited,
			new SkeletonEditor.SequenceAttachmentState("walk", 4, 1, 2, 0));
		assertTrue(inheritedReplacement != null && inheritedReplacement.getSourceMesh() == meshReplacement
			&& inheritedReplacement.getTimelineAttachment() == meshReplacement,
			"Sequence replacement preserves inherited Linked Mesh source timeline ownership");
	}

	private static void assertColor(Color expected, Color actual, String message) {
		assertNear(expected.r, actual.r, message + " red");
		assertNear(expected.g, actual.g, message + " green");
		assertNear(expected.b, actual.b, message + " blue");
		assertNear(expected.a, actual.a, message + " alpha");
	}

	private static void createsAndEditsMeshSafely() {
		SkeletonDocument doc = createDocument();
		doc.data.findBone("root").getSetupPose().setScale(1f, 1f);
		doc.data.findBone("child").getSetupPose().setScale(1f, 1f);
		doc.skeleton = new Skeleton(doc.data);
		SlotData slot = new SlotData(0, "mesh-slot", doc.data.findBone("child"));
		slot.setBlendMode(BlendMode.normal);
		slot.setAttachmentName("body");
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		Skin linkedSkin = new Skin("linked");
		doc.data.getSkins().addAll(skin, linkedSkin);
		doc.data.setDefaultSkin(skin);

		TextureRegion texture = new TextureRegion();
		Sequence regionSequence = new Sequence(1, false);
		regionSequence.getRegions()[0] = texture;
		RegionAttachment region = new RegionAttachment("body", regionSequence);
		region.setPath("body");
		region.setX(12f);
		region.setY(-7f);
		region.setScaleX(1.5f);
		region.setScaleY(0.75f);
		region.setRotation(30f);
		region.setWidth(80f);
		region.setHeight(40f);
		region.getColor().set(0.2f, 0.4f, 0.6f, 0.8f);
		region.updateSequence();
		float[] expectedVertices = regionSequence.getOffsets(0).clone();
		skin.setAttachment(0, "body", region);
		Animation.SequenceTimeline regionTimeline = new Animation.SequenceTimeline(1, 0, region);
		regionTimeline.setFrame(0, 0.25f, Sequence.SequenceMode.hold, 0, 0f);
		Animation regionAnimation = new Animation("region-to-mesh");
		Array<Animation.Timeline> regionTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		regionTimelines.add(regionTimeline);
		regionAnimation.setTimelines(regionTimelines, new IntArray());
		doc.data.getAnimations().add(regionAnimation);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);

		SkeletonEditor editor = new SkeletonEditor(doc) {
			@Override protected TextureRegion findRegion(String name) {
				return "body".equals(name) ? texture : null;
			}
		};
		linkedSkin.setAttachment(0, "shared-body", region);
		String sharedReason = editor.getMeshReplacementBlockReason(skin, slot, "body");
		assertTrue(sharedReason != null && sharedReason.contains("共享 Region")
			&& editor.setSlotMeshAttachment(skin, slot, "body", "body") == null,
			"a referenced shared Region cannot silently lose animation on one owner during conversion");
		linkedSkin.removeAttachment(0, "shared-body");
		assertTrue(editor.getMeshReplacementBlockReason(skin, slot, "body") == null,
			"a sole Region owner can migrate its Sequence track during conversion");
		MeshAttachment mesh = editor.setSlotMeshAttachment(skin, slot, "body", "body");
		assertTrue(mesh != null && skin.getAttachment(0, "body") == mesh,
			"a Region can be converted to an independent Mesh");
		assertTrue(mesh.getSourceMesh() == null && mesh.getBones() == null,
			"a converted Mesh starts as independent and unweighted");
		assertArrayNear(expectedVertices, mesh.getVertices(), "Region conversion preserves the visible local quad");
		assertColor(region.getColor(), mesh.getColor(), "Region conversion preserves tint");
		assertNear(80f, mesh.getWidth(), "Region conversion preserves width");
		assertNear(40f, mesh.getHeight(), "Region conversion preserves height");
		assertEquals(8, mesh.getWorldVerticesLength(), "converted Mesh world vertex length");
		assertEquals(8, mesh.getHullLength(), "converted Mesh hull length");
		Animation.SequenceTimeline migratedRegionTimeline =
			new AnimationEditor(doc).findSequenceTimeline(regionAnimation, 0, mesh);
		assertTrue(migratedRegionTimeline != null && migratedRegionTimeline != regionTimeline
			&& java.util.Arrays.equals(migratedRegionTimeline.getFrames(), regionTimeline.getFrames()),
			"Region Sequence track follows the converted Mesh identity without frame loss");
		SkeletonEditor.MeshWeightPaintContext paintContext = editor.prepareMeshWeightPaint(mesh, slot,
			doc.data.findBone("root"));
		assertEquals(4, paintContext.vertexCount(), "weight paint context covers every Mesh vertex");
		assertEquals(1, paintContext.slotBoneIndex(), "weight paint context records the rigid slot bone");
		assertEquals(0, paintContext.targetBoneIndex(), "weight paint context records the selected target bone");
		assertArrayNear(expectedVertices, paintContext.targetLocalVertices(),
			"weight paint context converts setup vertices into target-bone local coordinates");

		SkeletonEditor.MeshGeometryState snapshot = SkeletonEditor.meshGeometryState(mesh);
		float originalX = mesh.getVertices()[0];
		float[] leakedVertices = snapshot.vertices();
		leakedVertices[0] += 999f;
		assertNear(originalX, mesh.getVertices()[0], "Mesh snapshots cannot mutate attachment arrays");

		MeshAttachment linked = mesh.newLinkedMesh();
		linkedSkin.setAttachment(0, "linked", linked);
		float[] editedVertices = snapshot.vertices();
		editedVertices[0] += 5f;
		SkeletonEditor.MeshGeometryState edited = new SkeletonEditor.MeshGeometryState(snapshot.color(),
			snapshot.path(), snapshot.bones(), editedVertices, snapshot.regionUVs(), snapshot.triangles(),
			snapshot.hullLength(), snapshot.edges(), snapshot.width(), snapshot.height());
		long revision = doc.revision;
		assertTrue(editor.updateMeshGeometry(mesh, edited), "valid Mesh geometry can be updated atomically");
		assertNear(originalX + 5f, mesh.getVertices()[0], "Mesh vertex edit is applied");
		assertTrue(linked.getVertices() == mesh.getVertices() && linked.getTriangles() == mesh.getTriangles(),
			"linked meshes are rebound to changed source arrays");
		assertTrue(doc.revision == revision + 1, "Mesh geometry edit marks the document once");
		SkeletonEditor.MeshGeometryState noHull = new SkeletonEditor.MeshGeometryState(edited.color(),
			edited.path(), edited.bones(), edited.vertices(), edited.regionUVs(), edited.triangles(),
			0, edited.edges(), edited.width(), edited.height());
		assertTrue(editor.validateMeshGeometry(mesh, noHull) == null,
			"a Spine Mesh with an omitted hull remains valid for geometry editing");
		assertTrue(editor.updateMeshGeometry(mesh, noHull) && mesh.getHullLength() == 0
			&& linked.getHullLength() == 0,
			"hull-free geometry updates the source and its Linked Mesh in place");
		assertTrue(editor.updateMeshGeometry(mesh, edited), "a hull can be restored after editing a hull-free Mesh");

		SkeletonEditor.MeshGeometryState invalid = new SkeletonEditor.MeshGeometryState(edited.color(),
			edited.path(), edited.bones(), edited.vertices(), edited.regionUVs(), new short[] {0, 0, 1},
			edited.hullLength(), edited.edges(), edited.width(), edited.height());
		revision = doc.revision;
		assertTrue(editor.validateMeshGeometry(mesh, invalid).contains("重复顶点"),
			"invalid triangle data reports an exact reason");
		assertTrue(!editor.updateMeshGeometry(mesh, invalid) && doc.revision == revision,
			"invalid triangle data changes nothing");

		int[] weightedBones = {1, 0, 1, 0, 1, 1, 1, 1};
		float[] weightedVertices = {
			editedVertices[0], editedVertices[1], 1f, editedVertices[2], editedVertices[3], 1f,
			editedVertices[4], editedVertices[5], 1f, editedVertices[6], editedVertices[7], 1f
		};
		SkeletonEditor.MeshGeometryState weighted = new SkeletonEditor.MeshGeometryState(edited.color(),
			edited.path(), weightedBones, weightedVertices, edited.regionUVs(), edited.triangles(),
			edited.hullLength(), edited.edges(), edited.width(), edited.height());
		assertTrue(editor.updateMeshGeometry(mesh, weighted), "Mesh vertices can be bound to document bones");
		assertTrue(mesh.getBones() != null && mesh.getVertices().length == 12,
			"weighted Mesh stores one XYZ-weight triplet per influence");
		assertTrue(linked.getBones() == mesh.getBones(), "linked meshes share updated weight bindings");

		linked.setTimelineAttachment(linked);
		Animation.DeformTimeline linkedLayoutDeform = new Animation.DeformTimeline(1, 0, 0, linked);
		linkedLayoutDeform.setFrame(0, 0f, new float[8]);
		Animation linkedLayoutAnimation = new Animation("linked-layout-deform");
		Array<Animation.Timeline> linkedLayoutTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		linkedLayoutTimelines.add(linkedLayoutDeform);
		linkedLayoutAnimation.setTimelines(linkedLayoutTimelines, new IntArray());
		doc.data.getAnimations().add(linkedLayoutAnimation);
		SkeletonEditor.MeshGeometryState insertedWithLinkedDeform = SkeletonEditor.insertMeshVertex(
			SkeletonEditor.meshGeometryState(mesh), 0, 0.25f, 0.25f, 0.5f);
		String linkedBlockReason = editor.validateMeshGeometry(mesh, insertedWithLinkedDeform);
		assertTrue(linkedBlockReason != null && linkedBlockReason.contains("linked-layout-deform")
			&& linkedBlockReason.contains("Deform"),
			"an independent Linked Mesh Deform blocks source topology changes");
		float[] expandedWeights = {
			weightedVertices[0], weightedVertices[1], 0.5f,
			weightedVertices[0], weightedVertices[1], 0.5f,
			weightedVertices[3], weightedVertices[4], weightedVertices[5],
			weightedVertices[6], weightedVertices[7], weightedVertices[8],
			weightedVertices[9], weightedVertices[10], weightedVertices[11]
		};
		SkeletonEditor.MeshGeometryState expandedInfluences = new SkeletonEditor.MeshGeometryState(weighted.color(),
			weighted.path(), new int[] {2, 0, 1, 1, 0, 1, 1, 1, 1}, expandedWeights,
			weighted.regionUVs(), weighted.triangles(), weighted.hullLength(), weighted.edges(),
			weighted.width(), weighted.height());
		linkedBlockReason = editor.validateMeshGeometry(mesh, expandedInfluences);
		assertTrue(linkedBlockReason != null && linkedBlockReason.contains("Deform"),
			"an independent Linked Mesh Deform blocks source influence-layout changes");
		revision = doc.revision;
		assertTrue(!editor.updateMeshGeometry(mesh, expandedInfluences) && doc.revision == revision,
			"a blocked Linked Mesh Deform layout change is atomic");
		assertTrue(mesh.getVertices().length == 12 && linked.getVertices() == mesh.getVertices()
			&& linkedLayoutDeform.getVertices()[0].length == 8,
			"blocked source edits preserve source, Linked Mesh, and Deform data");
		doc.data.getAnimations().removeValue(linkedLayoutAnimation, true);
		linked.setTimelineAttachment(mesh);

		Animation.DeformTimeline deform = new Animation.DeformTimeline(1, 0, 0, mesh);
		deform.setFrame(0, 0f, new float[8]);
		Animation animation = new Animation("weighted-deform");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(deform);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		String blockReason = editor.validateMeshGeometry(mesh, edited);
		assertTrue(blockReason != null && blockReason.contains("Deform") && blockReason.contains("权重模式"),
			"an existing Deform track blocks changing weighted mode");
		revision = doc.revision;
		assertTrue(!editor.updateMeshGeometry(mesh, edited) && doc.revision == revision,
			"a Deform-incompatible Mesh edit changes nothing");
		assertTrue(mesh.getBones() != null && mesh.getVertices().length == 12,
			"blocked Mesh edits preserve weighted geometry");
		SkeletonEditor.MeshGeometryState inserted = SkeletonEditor.insertMeshVertex(
			SkeletonEditor.meshGeometryState(mesh), 0, 0.25f, 0.25f, 0.5f);
		assertTrue(editor.validateMeshGeometry(mesh, inserted).contains("Deform 顶点数量"),
			"topology edits that change weighted Deform length are blocked before mutation");
		SkeletonEditor.MeshGeometryState complete = SkeletonEditor.meshGeometryState(mesh);
		short[] completeTriangles = complete.triangles();
		SkeletonEditor.MeshGeometryState hole = new SkeletonEditor.MeshGeometryState(complete.color(),
			complete.path(), complete.bones(), complete.vertices(), complete.regionUVs(),
			new short[] {completeTriangles[0], completeTriangles[1], completeTriangles[2]},
			complete.hullLength(), complete.edges(), complete.width(), complete.height());
		assertTrue(editor.updateMeshGeometry(mesh, hole),
			"an incomplete topology remains editable while a face is being repaired");
		SkeletonEditor.MeshGeometryState addedFace = SkeletonEditor.addMeshTriangle(
			SkeletonEditor.meshGeometryState(mesh), completeTriangles[3], completeTriangles[4], completeTriangles[5]);
		assertTrue(editor.validateMeshGeometry(mesh, addedFace) == null,
			"repairing a face without changing vertices remains compatible with existing Deform frames");
		int triangleValues = mesh.getTriangles().length;
		assertTrue(editor.updateMeshGeometry(mesh, addedFace)
			&& mesh.getTriangles().length == triangleValues + 3,
			"a Deform-compatible topology edit is applied through the managed Mesh path");
		short[] excessTriangles = java.util.Arrays.copyOf(addedFace.triangles(), addedFace.triangles().length + 3);
		excessTriangles[excessTriangles.length - 3] = 0;
		excessTriangles[excessTriangles.length - 2] = 1;
		excessTriangles[excessTriangles.length - 1] = 3;
		SkeletonEditor.MeshGeometryState excessFaces = new SkeletonEditor.MeshGeometryState(addedFace.color(),
			addedFace.path(), addedFace.bones(), addedFace.vertices(), addedFace.regionUVs(), excessTriangles,
			addedFace.hullLength(), addedFace.edges(), addedFace.width(), addedFace.height());
		assertTrue(editor.validateMeshGeometry(mesh, excessFaces).contains("Spine 格式上限"),
			"manual geometry editing cannot bypass Spine's implicit triangle count");
		String topologyJson = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(topologyJson.contains("\"triangles\"") && topologyJson.contains("weighted-deform"),
			"edited topology and its existing Deform animation remain exportable together");
	}

	private static void createsLinkedMeshesSafely() {
		SkeletonDocument doc = createDocument();
		SlotData sourceSlot = new SlotData(0, "source-slot", doc.data.findBone("child"));
		SlotData targetSlot = new SlotData(1, "target-slot", doc.data.findBone("child"));
		sourceSlot.setBlendMode(BlendMode.normal);
		targetSlot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().addAll(sourceSlot, targetSlot);
		Skin sourceSkin = new Skin("default");
		Skin linkedSkin = new Skin("linked");
		doc.data.getSkins().addAll(sourceSkin, linkedSkin);
		doc.data.setDefaultSkin(sourceSkin);
		TextureRegion sourceTexture = new TextureRegion();
		TextureRegion variantTexture = new TextureRegion();
		Sequence sourceSequence = new Sequence(1, false);
		sourceSequence.getRegions()[0] = sourceTexture;
		MeshAttachment source = new MeshAttachment("source", sourceSequence);
		source.setPath("source");
		source.setWorldVerticesLength(8);
		source.setVertices(new float[] {-10f, -10f, -10f, 10f, 10f, 10f, 10f, -10f});
		source.setRegionUVs(new float[] {0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f});
		source.setTriangles(new short[] {0, 1, 2, 2, 3, 0});
		source.setHullLength(8);
		source.setWidth(20f);
		source.setHeight(20f);
		source.updateSequence();
		sourceSkin.setAttachment(sourceSlot.getIndex(), "source", source);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(sourceSkin);

		SkeletonEditor editor = new SkeletonEditor(doc) {
			@Override protected TextureRegion findRegion(String name) {
				return "variant".equals(name) ? variantTexture : "source".equals(name) ? sourceTexture : null;
			}
		};
		Array<SkeletonEditor.MeshAttachmentSource> sources = editor.meshAttachmentSources();
		assertEquals(1, sources.size, "one source entry is listed for one mesh identity");
		SkeletonEditor.MeshAttachmentSource sourceRef = sources.first();
		assertTrue(sourceRef.skin() == sourceSkin && sourceRef.slot() == sourceSlot
			&& sourceRef.mesh() == source && "source".equals(sourceRef.placeholder()),
			"Linked Mesh source keeps its canonical skin entry");
		assertTrue(editor.getLinkedMeshCreationBlockReason(linkedSkin, targetSlot, "variant", "variant",
			sourceRef) == null, "valid Linked Mesh creation is accepted");
		MeshAttachment linked = editor.createLinkedMeshAttachment(linkedSkin, targetSlot, "variant", "variant",
			sourceRef, true);
		assertTrue(linked != null && linked.getSourceMesh() == source,
			"created Linked Mesh references the selected source");
		assertTrue(linked.getVertices() == source.getVertices() && linked.getTriangles() == source.getTriangles(),
			"created Linked Mesh shares source geometry arrays");
		assertTrue(linked.getTimelineAttachment() == source,
			"created Linked Mesh inherits source Deform timelines");
		assertIntArray(new int[] {targetSlot.getIndex()}, source.getTimelineSlots(),
			"cross-slot timeline inheritance registers the target slot once");
		assertTrue(linked.getSequence().getRegions()[0] == variantTexture && "variant".equals(linked.getPath()),
			"created Linked Mesh can use a different atlas region");
		long unchangedRevision = doc.revision;
		assertTrue(editor.updateLinkedMeshAttachment(linkedSkin, targetSlot, "variant", "variant", sourceRef,
			true) == linked, "reapplying Linked Mesh settings keeps the existing attachment identity");
		assertTrue(unchangedRevision == doc.revision,
			"reapplying identical Linked Mesh settings is a clean no-op");
		assertTrue(editor.updateLinkedMeshAttachment(linkedSkin, targetSlot, "variant", "source", sourceRef,
			false) == linked, "an existing Linked Mesh can be updated in place");
		assertTrue(linked.getTimelineAttachment() == linked && linked.getSequence().getRegions()[0] == sourceTexture
			&& "source".equals(linked.getPath()),
			"Linked Mesh update changes texture and switches to independent Deform without replacing the object");
		assertEquals(0, source.getTimelineSlots().length,
			"switching an existing Linked Mesh to independent Deform releases its cross-slot registration");
		assertTrue(editor.updateLinkedMeshAttachment(linkedSkin, targetSlot, "variant", "variant", sourceRef,
			true) == linked, "an existing Linked Mesh can restore inherited Deform");
		assertIntArray(new int[] {targetSlot.getIndex()}, source.getTimelineSlots(),
			"restoring inherited Deform registers the cross-slot target again");
		assertTrue(editor.getAttachmentDeleteBlockReason(sourceSkin, sourceSlot, "source").contains("Linked Mesh"),
			"source deletion remains protected while a Linked Mesh references it");
		assertTrue(editor.getLinkedMeshCreationBlockReason(linkedSkin, targetSlot, "variant", "variant",
			sourceRef).contains("已有附件"), "Linked Mesh creation never silently replaces an attachment");
		MeshAttachment sibling = editor.createLinkedMeshAttachment(linkedSkin, targetSlot, "variant-2", "variant",
			sourceRef, true);
		assertTrue(sibling != null && sibling.getTimelineAttachment() == source,
			"multiple Linked Meshes may inherit the same source in one target slot");
		assertIntArray(new int[] {targetSlot.getIndex()}, source.getTimelineSlots(),
			"multiple same-slot inheritors register the target slot only once");
		assertTrue(editor.removeSkinAttachment(linkedSkin, targetSlot, "variant"),
			"one of multiple same-slot Linked Meshes can be removed");
		assertIntArray(new int[] {targetSlot.getIndex()}, source.getTimelineSlots(),
			"removing one same-slot inheritor keeps the source timeline slot registered");
		assertTrue(editor.getAttachmentDeleteBlockReason(sourceSkin, sourceSlot, "source").contains("Linked Mesh"),
			"source deletion stays protected while another same-slot Linked Mesh remains");
		assertTrue(editor.removeSkinAttachment(linkedSkin, targetSlot, "variant-2"),
			"the final same-slot Linked Mesh can be removed");
		assertEquals(0, source.getTimelineSlots().length,
			"removing the last cross-slot Linked Mesh releases its timeline slot");
		assertTrue(editor.getAttachmentDeleteBlockReason(sourceSkin, sourceSlot, "source") == null,
			"source can be removed after its last Linked Mesh is gone");
		MeshAttachment independent = editor.createLinkedMeshAttachment(linkedSkin, targetSlot, "independent",
			"variant", sourceRef, false);
		assertTrue(independent != null && independent.getTimelineAttachment() == independent,
			"Linked Mesh can keep an independent Deform timeline");
		assertEquals(0, source.getTimelineSlots().length,
			"independent Deform mode does not register a source timeline slot");
		assertTrue(editor.removeSkinAttachment(linkedSkin, targetSlot, "independent"),
			"independent Linked Mesh can be removed");
		assertTrue(editor.createLinkedMeshAttachment(linkedSkin, targetSlot, "skin-linked", "variant",
			sourceRef, true) != null, "inherited Linked Mesh can be recreated before skin removal");
		assertTrue(editor.removeSkin(linkedSkin), "skin containing an unreferenced Linked Mesh can be removed");
		assertEquals(0, source.getTimelineSlots().length,
			"removing a Linked Mesh skin releases its cross-slot timeline reference");
	}

	private static void updatesLinkedMeshSourceWithDeformSafely() {
		SkeletonDocument doc = createDocument();
		SlotData firstSlot = new SlotData(0, "first-source-slot", doc.data.findBone("root"));
		SlotData secondSlot = new SlotData(1, "second-source-slot", doc.data.findBone("root"));
		SlotData targetSlot = new SlotData(2, "linked-slot", doc.data.findBone("child"));
		firstSlot.setBlendMode(BlendMode.normal);
		secondSlot.setBlendMode(BlendMode.normal);
		targetSlot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().addAll(firstSlot, secondSlot, targetSlot);
		Skin sourceSkin = new Skin("default");
		Skin linkedSkin = new Skin("linked");
		doc.data.getSkins().addAll(sourceSkin, linkedSkin);
		doc.data.setDefaultSkin(sourceSkin);
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
		sourceSkin.setAttachment(0, "first", first);
		sourceSkin.setAttachment(1, "second", second);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(sourceSkin);

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
		SkeletonEditor.MeshAttachmentSource firstRef = null, secondRef = null;
		for (SkeletonEditor.MeshAttachmentSource source : editor.meshAttachmentSources()) {
			if (source.mesh() == first) firstRef = source;
			if (source.mesh() == second) secondRef = source;
		}
		assertTrue(firstRef != null && secondRef != null, "both independent Mesh sources are selectable");
		MeshAttachment linked = editor.createLinkedMeshAttachment(linkedSkin, targetSlot, "linked", "variant",
			firstRef, false);
		assertTrue(linked != null && linked.getTimelineAttachment() == linked,
			"test Linked Mesh starts with independent attachment timelines");

		float[] offsets = {1f, -2f, 3f, -4f, 5f, -6f};
		float[] stored = first.getVertices().clone();
		for (int value = 0; value < stored.length; value++) stored[value] += offsets[value];
		Animation.DeformTimeline deform = new Animation.DeformTimeline(1, 0, targetSlot.getIndex(), linked);
		deform.setFrame(0, 0.5f, stored);
		Animation animation = new Animation("linked-source-change");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(deform);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		AnimationEditor animationEditor = new AnimationEditor(doc);

		assertTrue(editor.getLinkedMeshUpdateBlockReason(linkedSkin, targetSlot, "linked", "variant",
			secondRef, false) == null, "compatible source geometry can replace an independent Linked Mesh source");
		assertTrue(editor.updateLinkedMeshAttachment(linkedSkin, targetSlot, "linked", "variant",
			secondRef, false) == linked, "independent Linked Mesh can switch to another compatible source");
		assertTrue(linked.getSourceMesh() == second && linked.getVertices() == second.getVertices(),
			"Linked Mesh source switch rebinds all shared geometry");
		assertArrayNear(offsets, animationEditor.deformOffsetsAt(deform, 0),
			"unweighted independent Linked Mesh source switch preserves Deform offsets");

		String reason = editor.getLinkedMeshUpdateBlockReason(linkedSkin, targetSlot, "linked", "variant",
			secondRef, true);
		assertTrue(reason != null && reason.contains("Deform") && reason.contains("直属轨道"),
			"switching to inherited timelines is blocked while a direct Deform track targets the Linked Mesh");
		assertTrue(editor.updateLinkedMeshAttachment(linkedSkin, targetSlot, "linked", "variant",
			secondRef, true) == null && linked.getTimelineAttachment() == linked,
			"blocked inheritance switch leaves Linked Mesh timeline ownership unchanged");

		Animation.SequenceTimeline sequenceTimeline = new Animation.SequenceTimeline(1,
			targetSlot.getIndex(), linked);
		sequenceTimeline.setFrame(0, 0f, Sequence.SequenceMode.hold, 0, 0f);
		Array<Animation.Timeline> sequenceTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		sequenceTimelines.add(sequenceTimeline);
		animation.setTimelines(sequenceTimelines, new IntArray());
		reason = editor.getLinkedMeshUpdateBlockReason(linkedSkin, targetSlot, "linked", "variant",
			secondRef, true);
		assertTrue(reason != null && reason.contains("Sequence") && reason.contains("直属轨道"),
			"switching to inherited timelines is blocked while a direct Sequence track targets the Linked Mesh");

		animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		assertTrue(editor.updateLinkedMeshAttachment(linkedSkin, targetSlot, "linked", "variant",
			secondRef, true) == linked && linked.getTimelineAttachment() == second,
			"Linked Mesh can switch to inherited timelines after direct attachment tracks are removed");
		assertIntArray(new int[] {targetSlot.getIndex()}, second.getTimelineSlots(),
			"inherited source switch registers the target slot on the new source");
	}

	private static void preservesDeformOffsetsAcrossSetupMeshEdits() {
		SkeletonDocument doc = createDocument();
		SlotData sourceSlot = new SlotData(0, "source-slot", doc.data.findBone("root"));
		SlotData linkedSlot = new SlotData(1, "linked-slot", doc.data.findBone("child"));
		sourceSlot.setBlendMode(BlendMode.normal);
		linkedSlot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().addAll(sourceSlot, linkedSlot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment source = new MeshAttachment("source", sequence);
		source.setPath("source");
		source.setWorldVerticesLength(8);
		source.setVertices(new float[] {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f});
		source.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f});
		source.setTriangles(new short[] {0, 1, 2, 0, 2, 3});
		source.setHullLength(8);
		source.setWidth(10f);
		source.setHeight(10f);
		source.updateSequence();
		skin.setAttachment(0, "source", source);
		MeshAttachment linked = source.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		skin.setAttachment(1, "linked", linked);

		float[] sourceOffsets = {1f, -2f, 3f, -4f, 5f, -6f, 7f, -8f};
		float[] linkedOffsets = {-8f, 7f, -6f, 5f, -4f, 3f, -2f, 1f};
		float[] sourceStored = source.getVertices().clone();
		float[] linkedStored = source.getVertices().clone();
		for (int value = 0; value < sourceStored.length; value++) {
			sourceStored[value] += sourceOffsets[value];
			linkedStored[value] += linkedOffsets[value];
		}
		Animation.DeformTimeline sourceDeform = new Animation.DeformTimeline(1, 0, 0, source);
		sourceDeform.setFrame(0, 0.25f, sourceStored);
		Animation.DeformTimeline linkedDeform = new Animation.DeformTimeline(1, 0, 1, linked);
		linkedDeform.setFrame(0, 0.5f, linkedStored);
		Animation animation = new Animation("setup-edit-deform");
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(sourceDeform);
		timelines.add(linkedDeform);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);

		SkeletonEditor editor = new SkeletonEditor(doc);
		AnimationEditor animationEditor = new AnimationEditor(doc);
		SkeletonEditor.MeshGeometryState original = SkeletonEditor.meshGeometryState(source);
		float[] movedVertices = original.vertices();
		for (int value = 0; value < movedVertices.length; value++) movedVertices[value] += value - 3f;
		SkeletonEditor.MeshGeometryState moved = new SkeletonEditor.MeshGeometryState(original.color(),
			original.path(), null, movedVertices, original.regionUVs(), original.triangles(), original.hullLength(),
			original.edges(), original.width(), original.height());
		assertTrue(editor.updateMeshGeometry(source, moved),
			"editing unweighted Mesh setup vertices succeeds with existing source and Linked Mesh Deform tracks");
		assertArrayNear(sourceOffsets, animationEditor.deformOffsetsAt(sourceDeform, 0),
			"source Deform offsets remain unchanged after setup geometry editing");
		assertArrayNear(linkedOffsets, animationEditor.deformOffsetsAt(linkedDeform, 0),
			"independent Linked Mesh Deform offsets remain unchanged after source setup editing");

		SkeletonEditor.MeshGeometryState previewBefore = SkeletonEditor.meshGeometryState(source);
		source.getVertices()[0] += 7f;
		source.getVertices()[1] -= 3f;
		assertTrue(editor.commitMeshGeometryPreview(source, previewBefore),
			"viewport-style in-place Mesh preview is committed through managed Deform migration");
		assertArrayNear(sourceOffsets, animationEditor.deformOffsetsAt(sourceDeform, 0),
			"viewport Mesh drag preserves source Deform offsets");
		assertArrayNear(linkedOffsets, animationEditor.deformOffsetsAt(linkedDeform, 0),
			"viewport Mesh drag preserves independent Linked Mesh Deform offsets");
		assertTrue(linked.getVertices() == source.getVertices(),
			"viewport preview commit rebinds Linked Mesh geometry arrays");
		SkeletonEditor.MeshGeometryState validPreview = SkeletonEditor.meshGeometryState(source);
		source.getVertices()[0] = Float.NaN;
		assertThrows(IllegalArgumentException.class,
			() -> editor.commitMeshGeometryPreview(source, validPreview),
			"an invalid viewport Mesh preview is rejected");
		assertTrue(SkeletonEditor.meshGeometryState(source).sameContent(validPreview),
			"a rejected viewport Mesh preview restores the complete source geometry");
		assertTrue(linked.getVertices() == source.getVertices(),
			"a rejected viewport Mesh preview restores Linked Mesh array sharing");
		assertArrayNear(sourceOffsets, animationEditor.deformOffsetsAt(sourceDeform, 0),
			"a rejected viewport Mesh preview leaves source Deform offsets unchanged");
		assertArrayNear(linkedOffsets, animationEditor.deformOffsetsAt(linkedDeform, 0),
			"a rejected viewport Mesh preview leaves Linked Mesh Deform offsets unchanged");

		assertTrue(editor.updateMeshGeometry(source, original),
			"restoring the original setup Mesh geometry remains compatible with Deform tracks");
		assertArrayNear(sourceOffsets, animationEditor.deformOffsetsAt(sourceDeform, 0),
			"setup geometry undo preserves source Deform offsets");
		assertArrayNear(linkedOffsets, animationEditor.deformOffsetsAt(linkedDeform, 0),
			"setup geometry undo preserves Linked Mesh Deform offsets");
	}

	private static void remapsWeightedDeformInfluenceOrderSafely() {
		SkeletonDocument doc = createDocument();
		BoneData third = new BoneData(2, "third", doc.data.findBone("root"));
		third.getSetupPose().setInherit(Inherit.normal);
		doc.data.getBones().add(third);
		SlotData sourceSlot = new SlotData(0, "source-slot", doc.data.findBone("root"));
		SlotData linkedSlot = new SlotData(1, "linked-slot", doc.data.findBone("child"));
		sourceSlot.setBlendMode(BlendMode.normal);
		linkedSlot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().addAll(sourceSlot, linkedSlot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
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
		source.setBones(oldBones.clone());
		source.setVertices(oldVertices.clone());
		source.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0.5f, 1f});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.setWidth(10f);
		source.setHeight(10f);
		source.updateSequence();
		skin.setAttachment(0, "weighted", source);
		MeshAttachment linked = source.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		skin.setAttachment(1, "linked", linked);
		BoundingBoxAttachment box = new BoundingBoxAttachment("weighted-box");
		box.setWorldVerticesLength(6);
		box.setBones(oldBones.clone());
		box.setVertices(oldVertices.clone());
		skin.setAttachment(0, "weighted-box", box);

		float[] sourceFrame = {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f};
		float[] linkedFrame = {11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f, 20f};
		float[] boxFrame = {21f, 22f, 23f, 24f, 25f, 26f, 27f, 28f, 29f, 30f};
		Animation.DeformTimeline sourceDeform = new Animation.DeformTimeline(1, 0, 0, source);
		sourceDeform.setFrame(0, 0.25f, sourceFrame.clone());
		Animation.DeformTimeline linkedDeform = new Animation.DeformTimeline(1, 0, 1, linked);
		linkedDeform.setFrame(0, 0.5f, linkedFrame.clone());
		Animation.DeformTimeline boxDeform = new Animation.DeformTimeline(1, 0, 0, box);
		boxDeform.setFrame(0, 0.75f, boxFrame.clone());
		Animation animation = new Animation("weighted-layout");
		Array<Animation.Timeline> timelines = new Array<>(true, 3, Animation.Timeline[]::new);
		timelines.add(sourceDeform);
		timelines.add(linkedDeform);
		timelines.add(boxDeform);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);
		SkeletonEditor editor = new SkeletonEditor(doc);

		SkeletonEditor.MeshGeometryState original = SkeletonEditor.meshGeometryState(source);
		SkeletonEditor.MeshGeometryState reordered = new SkeletonEditor.MeshGeometryState(original.color(),
			original.path(), reorderedBones, reorderedVertices, original.regionUVs(), original.triangles(),
			original.hullLength(), original.edges(), original.width(), original.height());
		assertTrue(editor.validateMeshGeometry(source, reordered) == null && editor.updateMeshGeometry(source, reordered),
			"weighted Mesh influence order can change when every logical vertex keeps the same bone set");
		assertArrayNear(new float[] {3f, 4f, 1f, 2f, 7f, 8f, 5f, 6f, 9f, 10f},
			sourceDeform.getVertices()[0], "source Mesh Deform pairs follow their original bones");
		assertArrayNear(new float[] {13f, 14f, 11f, 12f, 17f, 18f, 15f, 16f, 19f, 20f},
			linkedDeform.getVertices()[0], "independent Linked Mesh Deform pairs follow shared bone layout");
		assertTrue(linked.getBones() == source.getBones(), "Linked Mesh remains bound to reordered source bones");

		SkeletonEditor.StandardAttachmentState boxBefore = SkeletonEditor.standardAttachmentState(box);
		SkeletonEditor.StandardAttachmentState boxReordered = new SkeletonEditor.StandardAttachmentState(
			SkeletonEditor.StandardAttachmentType.BOUNDING_BOX, boxBefore.color(), 3, reorderedBones,
			reorderedVertices, false, false, null, 0f, 0f, 0f, null, false, false);
		assertTrue(editor.validateStandardAttachment(box, boxReordered) == null
			&& editor.updateStandardAttachment(box, boxReordered),
			"weighted standard attachment influence order can be changed losslessly");
		assertArrayNear(new float[] {23f, 24f, 21f, 22f, 27f, 28f, 25f, 26f, 29f, 30f},
			boxDeform.getVertices()[0], "standard attachment Deform pairs follow their original bones");

		int[] incompatibleBones = {2, 1, 2, 2, 0, 1, 1, 1};
		SkeletonEditor.MeshGeometryState incompatible = new SkeletonEditor.MeshGeometryState(reordered.color(),
			reordered.path(), incompatibleBones, reordered.vertices(), reordered.regionUVs(), reordered.triangles(),
			reordered.hullLength(), reordered.edges(), reordered.width(), reordered.height());
		String reason = editor.validateMeshGeometry(source, incompatible);
		assertTrue(reason != null && reason.contains("Deform") && reason.contains("骨骼影响集合"),
			"same-length weighted layout changes are blocked when one vertex changes its bone set");
		float[] sourceBeforeFailure = sourceDeform.getVertices()[0].clone();
		assertTrue(!editor.updateMeshGeometry(source, incompatible),
			"an incompatible weighted influence set is rejected atomically");
		assertArrayNear(sourceBeforeFailure, sourceDeform.getVertices()[0],
			"rejected weighted layout changes leave Deform data unchanged");

		SkeletonEditor.StandardAttachmentState incompatibleBox = new SkeletonEditor.StandardAttachmentState(
			SkeletonEditor.StandardAttachmentType.BOUNDING_BOX, boxReordered.color(), 3, incompatibleBones,
			boxReordered.vertices(), false, false, null, 0f, 0f, 0f, null, false, false);
		reason = editor.validateStandardAttachment(box, incompatibleBox);
		assertTrue(reason != null && reason.contains("Deform") && reason.contains("骨骼影响集合"),
			"standard attachment Deform blocks an incompatible same-length influence set");

		assertTrue(editor.updateMeshGeometry(source, original),
			"weighted Mesh influence order can be restored through the same managed path");
		assertArrayNear(sourceFrame, sourceDeform.getVertices()[0],
			"restoring the weighted Mesh layout restores source Deform pair order");
		assertArrayNear(linkedFrame, linkedDeform.getVertices()[0],
			"restoring the weighted Mesh layout restores Linked Mesh Deform pair order");
	}

	private static void paintsMeshWeightsSafely() {
		SkeletonEditor.MeshGeometryState unweighted = new SkeletonEditor.MeshGeometryState(Color.WHITE,
			"mesh", null, new float[] {0f, 0f, 2f, 0f, 0f, 2f},
			new float[] {0f, 0f, 1f, 0f, 0f, 1f}, new short[] {0, 1, 2}, 6, null, 2f, 2f);
		SkeletonEditor.MeshWeightPaintContext context = new SkeletonEditor.MeshWeightPaintContext(0, 1,
			new float[] {10f, 20f, 12f, 20f, 10f, 22f});
		SkeletonEditor.MeshGeometryState painted = SkeletonEditor.paintMeshWeights(unweighted, context,
			new float[] {0.5f, 0f, 0f});
		assertIntArray(new int[] {2, 0, 1, 1, 0, 1, 0}, painted.bones(),
			"painting an unweighted Mesh binds untouched vertices rigidly and adds the target influence");
		assertArrayNear(new float[] {0f, 0f, 0.5f, 10f, 20f, 0.5f,
			2f, 0f, 1f, 0f, 2f, 1f}, painted.vertices(),
			"new influences use target-bone setup coordinates and normalized weights");

		SkeletonEditor.MeshGeometryState increased = SkeletonEditor.paintMeshWeights(painted, context,
			new float[] {0.5f, 0f, 0f});
		assertNear(0.25f, increased.vertices()[2], "repeated positive painting scales other influences");
		assertNear(0.75f, increased.vertices()[5], "repeated positive painting moves target weight toward one");
		SkeletonEditor.MeshGeometryState decreased = SkeletonEditor.paintMeshWeights(increased, context,
			new float[] {-0.5f, 0f, 0f});
		assertNear(0.625f, decreased.vertices()[2], "negative painting redistributes weight to other bones");
		assertNear(0.375f, decreased.vertices()[5], "negative painting moves target weight toward zero");
		java.util.Set<SkeletonDocument.MeshWeightLock> lockedTarget = java.util.Set.of(
			new SkeletonDocument.MeshWeightLock(0, 1));
		assertTrue(SkeletonEditor.paintMeshWeights(decreased, context,
			new float[] {-1f, 0f, 0f}, lockedTarget).sameContent(decreased),
			"weight painting cannot directly change a locked target influence");
		java.util.Set<SkeletonDocument.MeshWeightLock> lockedOther = java.util.Set.of(
			new SkeletonDocument.MeshWeightLock(0, 0));
		SkeletonEditor.MeshGeometryState lockedRedistribution = SkeletonEditor.paintMeshWeights(
			decreased, context, new float[] {1f, 0f, 0f}, lockedOther);
		assertNear(decreased.vertices()[2], lockedRedistribution.vertices()[2],
			"weight painting does not indirectly renormalize a locked influence");

		assertTrue(SkeletonEditor.paintMeshWeights(unweighted, context,
			new float[] {-1f, 0f, 0f}).sameContent(unweighted),
			"subtracting a missing influence leaves an unweighted Mesh unchanged");
		SkeletonEditor.MeshWeightPaintContext rigidContext = new SkeletonEditor.MeshWeightPaintContext(0, 0,
			new float[] {0f, 0f, 2f, 0f, 0f, 2f});
		assertTrue(SkeletonEditor.paintMeshWeights(unweighted, rigidContext,
			new float[] {1f, 1f, 1f}).sameContent(unweighted),
			"painting the rigid slot bone at full weight is a no-op");
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonEditor.paintMeshWeights(unweighted, context, new float[] {Float.NaN, 0f, 0f}),
			"non-finite brush amounts are rejected before changing encoded data");

		MeshAttachment runtimeMesh = new MeshAttachment("painted", new Sequence(1, false));
		runtimeMesh.setWorldVerticesLength(6);
		runtimeMesh.setBones(decreased.bones());
		runtimeMesh.setVertices(decreased.vertices());
		float[] targetWeights = SkeletonEditor.meshWeightsForBone(runtimeMesh, 1, 0);
		assertArrayNear(new float[] {0.375f, 0f, 0f}, targetWeights,
			"viewport weight values are normalized from Spine's compact encoding");
	}

	private static void previewsMeshWeightsWithLinkedMeshesSafely() {
		SkeletonDocument doc = createDocument();
		SlotData sourceSlot = new SlotData(0, "source-slot", doc.data.findBone("root"));
		SlotData linkedSlot = new SlotData(1, "linked-slot", doc.data.findBone("child"));
		doc.data.getSlots().addAll(sourceSlot, linkedSlot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);

		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment source = new MeshAttachment("weighted-source", sequence);
		source.setPath("weighted-source");
		source.setWorldVerticesLength(6);
		source.setBones(new int[] {2, 0, 1, 1, 0, 1, 0});
		source.setVertices(new float[] {0f, 0f, 0.5f, 0f, 0f, 0.5f,
			2f, 0f, 1f, 0f, 2f, 1f});
		source.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0f, 1f});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		source.setWidth(2f);
		source.setHeight(2f);
		source.updateSequence();
		skin.setAttachment(0, "source", source);
		MeshAttachment linked = source.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		skin.setAttachment(1, "linked", linked);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);

		SkeletonEditor editor = new SkeletonEditor(doc);
		SkeletonEditor.MeshGeometryState before = SkeletonEditor.meshGeometryState(source);
		float[] previewVertices = before.vertices();
		previewVertices[2] = 0.25f;
		previewVertices[5] = 0.75f;
		SkeletonEditor.MeshGeometryState preview = new SkeletonEditor.MeshGeometryState(before.color(),
			before.path(), before.bones(), previewVertices, before.regionUVs(), before.triangles(),
			before.hullLength(), before.edges(), before.width(), before.height());
		doc.dirty = false;
		long revision = doc.revision;

		assertTrue(editor.applyMeshGeometryPreview(source, preview),
			"weight preview applies changed source weights");
		assertNear(0.25f, source.getVertices()[2], "weight preview updates the source Mesh");
		assertTrue(linked.getBones() == source.getBones() && linked.getVertices() == source.getVertices(),
			"weight preview immediately rebinds Linked Mesh weight arrays");
		assertTrue(!doc.dirty && doc.revision == revision,
			"temporary weight preview does not mark the document dirty");

		assertTrue(editor.applyMeshGeometryPreview(source, before),
			"weight preview can restore the original source state");
		assertTrue(linked.getBones() == source.getBones() && linked.getVertices() == source.getVertices(),
			"weight preview restore keeps Linked Mesh arrays shared");
		assertTrue(SkeletonEditor.meshGeometryState(source).sameContent(before),
			"weight preview restore recovers the complete source state");
		assertTrue(!doc.dirty && doc.revision == revision,
			"weight preview restore remains outside document history");

		float[] invalidVertices = before.vertices();
		invalidVertices[2] = Float.NaN;
		SkeletonEditor.MeshGeometryState invalid = new SkeletonEditor.MeshGeometryState(before.color(),
			before.path(), before.bones(), invalidVertices, before.regionUVs(), before.triangles(),
			before.hullLength(), before.edges(), before.width(), before.height());
		assertThrows(IllegalArgumentException.class,
			() -> editor.applyMeshGeometryPreview(source, invalid),
			"an invalid weight preview is rejected before mutation");
		assertTrue(SkeletonEditor.meshGeometryState(source).sameContent(before),
			"a rejected weight preview preserves the source Mesh");
		assertTrue(linked.getBones() == source.getBones() && linked.getVertices() == source.getVertices(),
			"a rejected weight preview preserves Linked Mesh array sharing");
		assertTrue(!doc.dirty && doc.revision == revision,
			"a rejected weight preview does not alter document history");
	}

	private static void smoothsMeshWeightsAlongTopology() {
		int[] bones = {
			2, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1
		};
		float[] vertices = {
			0f, 0f, 1f, 0f, 0f, 0f,
			1f, 0f, 0f, 1f, 0f, 1f,
			1f, 1f, 0f, 1f, 1f, 1f,
			0f, 1f, 1f, 0f, 1f, 0f
		};
		SkeletonEditor.MeshGeometryState weighted = new SkeletonEditor.MeshGeometryState(Color.WHITE,
			"smooth", bones, vertices, new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f},
			new short[] {0, 1, 2, 0, 2, 3}, 8, null, 1f, 1f);

		SkeletonEditor.MeshGeometryState smoothed = SkeletonEditor.smoothMeshWeights(weighted, 0.5f, 1);
		assertIntArray(bones, smoothed.bones(), "weight smoothing preserves compact bone structure");
		assertNear(2f / 3f, smoothed.vertices()[2], "corner 0 bone-0 weight follows three neighbors");
		assertNear(1f / 3f, smoothed.vertices()[5], "corner 0 bone-1 weight is complementary");
		assertNear(0.25f, smoothed.vertices()[8], "corner 1 bone-0 weight follows two neighbors");
		assertNear(0.75f, smoothed.vertices()[11], "corner 1 bone-1 weight is complementary");
		assertNear(1f / 3f, smoothed.vertices()[14], "corner 2 bone-0 weight follows three neighbors");
		assertNear(2f / 3f, smoothed.vertices()[17], "corner 2 bone-1 weight is complementary");
		assertNear(0.75f, smoothed.vertices()[20], "corner 3 bone-0 weight follows two neighbors");
		assertNear(0.25f, smoothed.vertices()[23], "corner 3 bone-1 weight is complementary");
		for (int vertex = 0; vertex < 4; vertex++) {
			assertNear(vertices[vertex * 6], smoothed.vertices()[vertex * 6],
				"weight smoothing preserves first influence local X");
			assertNear(vertices[vertex * 6 + 4], smoothed.vertices()[vertex * 6 + 4],
				"weight smoothing preserves second influence local Y");
			assertNear(1f, smoothed.vertices()[vertex * 6 + 2] + smoothed.vertices()[vertex * 6 + 5],
				"weight smoothing keeps every vertex normalized");
		}
		SkeletonEditor.MeshGeometryState repeated = SkeletonEditor.smoothMeshWeights(weighted, 0.5f, 5);
		assertTrue(Math.abs(repeated.vertices()[2] - 0.5f) < Math.abs(smoothed.vertices()[2] - 0.5f),
			"additional smoothing iterations continue reducing weight discontinuity");
		assertTrue(SkeletonEditor.smoothMeshWeights(weighted, 0f, 1).sameContent(weighted),
			"zero smoothing strength is a clean no-op");
		java.util.Set<SkeletonDocument.MeshWeightLock> locks = java.util.Set.of(
			new SkeletonDocument.MeshWeightLock(0, 0),
			new SkeletonDocument.MeshWeightLock(1, 1));
		SkeletonEditor.MeshGeometryState lockedSmooth = SkeletonEditor.smoothMeshWeights(
			weighted, 1f, 3, locks);
		assertNear(vertices[2], lockedSmooth.vertices()[2],
			"weight smoothing preserves a locked bone-0 influence exactly");
		assertNear(vertices[11], lockedSmooth.vertices()[11],
			"weight smoothing preserves a locked bone-1 influence exactly");
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonEditor.smoothMeshWeights(weighted, Float.NaN, 1),
			"non-finite smoothing strength is rejected");
		SkeletonEditor.MeshGeometryState invalidTopology = new SkeletonEditor.MeshGeometryState(Color.WHITE,
			"invalid", bones, vertices, weighted.regionUVs(), new short[] {0, 1, 4}, 8, null, 1f, 1f);
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonEditor.smoothMeshWeights(invalidTopology, 0.5f, 1),
			"weight smoothing rejects out-of-range topology indices");
	}

	private static void editsMeshTopologySafely() {
		SkeletonEditor.MeshGeometryState square = new SkeletonEditor.MeshGeometryState(Color.WHITE, "mesh", null,
			new float[] {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f},
			new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f},
			new short[] {0, 1, 2, 0, 2, 3}, 8,
			new short[] {0, 1, 1, 2, 2, 3, 3, 0}, 10f, 10f);
		SkeletonEditor.MeshGeometryState inserted = SkeletonEditor.insertMeshVertex(square, 0,
			0.25f, 0.25f, 0.5f);
		assertEquals(5, inserted.vertexCount(), "triangle insertion appends one logical vertex");
		assertArrayNear(new float[] {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f, 7.5f, 5f},
			inserted.vertices(), "unweighted insertion interpolates local geometry");
		assertArrayNear(new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0.75f, 0.5f},
			inserted.regionUVs(), "triangle insertion interpolates UV coordinates");
		assertShortArray(new short[] {0, 1, 4, 1, 2, 4, 2, 0, 4, 0, 2, 3}, inserted.triangles(),
			"triangle insertion preserves winding while splitting one face into three");
		assertEquals(8, inserted.hullLength(), "internal triangle insertion preserves the configured hull");
		assertShortArray(new short[] {0, 1, 1, 2, 2, 3, 3, 0, 0, 4, 1, 4, 2, 4}, inserted.edges(),
			"triangle insertion extends explicit editor edge metadata");

		SkeletonEditor.MeshGeometryState removedInternal = SkeletonEditor.removeMeshVertices(inserted,
			new int[] {4, 4});
		assertEquals(4, removedInternal.vertexCount(), "duplicate delete indices remove a vertex only once");
		assertShortArray(new short[] {0, 2, 3}, removedInternal.triangles(),
			"deleting an internal vertex removes all incident faces without corrupting remaining indices");
		SkeletonEditor.MeshGeometryState rebuilt = SkeletonEditor.addMeshTriangle(removedInternal, 0, 1, 2);
		assertShortArray(new short[] {0, 2, 3, 0, 1, 2}, rebuilt.triangles(),
			"three selected vertices can close a topology hole");
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonEditor.addMeshTriangle(rebuilt, 2, 0, 1),
			"an existing face cannot be added again with a different vertex order");
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonEditor.addMeshTriangle(rebuilt, 0, 1, 3),
			"a complete square cannot exceed Spine's implicit triangle count");

		SkeletonEditor.MeshGeometryState removedHull = SkeletonEditor.removeMeshVertices(square, new int[] {1});
		assertArrayNear(new float[] {0f, 0f, 10f, 10f, 0f, 10f}, removedHull.vertices(),
			"hull deletion compacts vertex coordinates in source order");
		assertShortArray(new short[] {0, 1, 2}, removedHull.triangles(),
			"hull deletion remaps surviving triangle indices");
		assertShortArray(new short[] {1, 2, 2, 0}, removedHull.edges(),
			"hull deletion removes incident edges and remaps survivors");
		assertEquals(6, removedHull.hullLength(), "hull deletion updates Spine's hull value count");

		SkeletonEditor.MeshGeometryState weighted = new SkeletonEditor.MeshGeometryState(Color.WHITE, "weighted",
			new int[] {1, 0, 2, 0, 1, 1, 1},
			new float[] {0f, 0f, 1f, 10f, 0f, 0.5f, 0f, 0f, 0.5f, 0f, 10f, 1f},
			new float[] {0f, 0f, 1f, 0f, 0f, 1f}, new short[] {0, 1, 2}, 6, null, 10f, 10f);
		SkeletonEditor.MeshGeometryState weightedInsert = SkeletonEditor.insertMeshVertex(weighted, 0,
			1f / 3f, 1f / 3f, 1f / 3f);
		assertIntArray(new int[] {1, 0, 2, 0, 1, 1, 1, 2, 0, 1}, weightedInsert.bones(),
			"weighted insertion merges the triangle's bone influence union");
		float[] weightedVertices = weightedInsert.vertices();
		assertNear(3.333333f, weightedVertices[12], "weighted insertion interpolates bone-0 local X");
		assertNear(0.5f, weightedVertices[14], "weighted insertion normalizes bone-0 weight");
		assertNear(6.666667f, weightedVertices[16], "weighted insertion interpolates bone-1 local Y");
		assertNear(0.5f, weightedVertices[17], "weighted insertion normalizes bone-1 weight");
	}

	private static void migratesMeshTopologyWithDeformSafely() {
		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "mesh-slot", doc.data.findBone("root"));
		slot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment mesh = new MeshAttachment("source", sequence);
		mesh.setPath("mesh");
		mesh.setVertices(new float[] {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f});
		mesh.setWorldVerticesLength(8);
		mesh.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f});
		mesh.setTriangles(new short[] {0, 1, 2, 0, 2, 3});
		mesh.setHullLength(8);
		mesh.setWidth(10f);
		mesh.setHeight(10f);
		mesh.updateSequence();
		skin.setAttachment(0, "source", mesh);
		MeshAttachment linked = mesh.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		skin.setAttachment(0, "linked", linked);

		float[] sourceOffsets = {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f};
		float[] linkedOffsets = {10f, 11f, 12f, 13f, 14f, 15f, 16f, 17f};
		Animation.DeformTimeline sourceDeform = new Animation.DeformTimeline(1, 0, 0, mesh);
		sourceDeform.setFrame(0, 0.25f, sourceOffsets.clone());
		Animation.DeformTimeline linkedDeform = new Animation.DeformTimeline(1, 0, 0, linked);
		linkedDeform.setFrame(0, 0.5f, linkedOffsets.clone());
		Animation animation = new Animation("topology-deform");
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(sourceDeform);
		timelines.add(linkedDeform);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);

		SkeletonEditor editor = new SkeletonEditor(doc);
		SkeletonEditor.MeshGeometryState originalGeometry = SkeletonEditor.meshGeometryState(mesh);
		SkeletonEditor.MeshTopologySnapshot before = editor.snapshotMeshTopology(mesh);
		assertTrue(editor.insertMeshVertexWithDeform(mesh, 0, 0.2f, 0.3f, 0.5f),
			"a Mesh vertex can be inserted while source and independent Linked Mesh Deform tracks exist");
		assertArrayNear(new float[] {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 2.6f, 3.6f},
			sourceDeform.getVertices()[0], "source Deform insertion uses barycentric interpolation");
		assertArrayNear(new float[] {10f, 11f, 12f, 13f, 14f, 15f, 16f, 17f, 12.6f, 13.6f},
			linkedDeform.getVertices()[0], "independent Linked Mesh Deform is migrated with shared topology");
		SkeletonEditor.MeshTopologySnapshot after = editor.snapshotMeshTopology(mesh);
		SkeletonEditor.MeshGeometryState insertedGeometry = SkeletonEditor.meshGeometryState(mesh);
		float[] insertedSourceOffsets = sourceDeform.getVertices()[0].clone();
		float[] insertedLinkedOffsets = linkedDeform.getVertices()[0].clone();

		editor.restoreMeshTopology(before);
		assertTrue(SkeletonEditor.meshGeometryState(mesh).sameContent(originalGeometry),
			"topology undo restores every source Mesh array");
		assertArrayNear(sourceOffsets, sourceDeform.getVertices()[0],
			"topology undo restores source Deform values exactly");
		assertArrayNear(linkedOffsets, linkedDeform.getVertices()[0],
			"topology undo restores independent Linked Mesh Deform values exactly");
		editor.restoreMeshTopology(after);
		assertTrue(SkeletonEditor.meshGeometryState(mesh).sameContent(insertedGeometry),
			"topology redo restores every inserted Mesh array");
		assertArrayNear(insertedSourceOffsets, sourceDeform.getVertices()[0],
			"topology redo restores migrated source Deform values exactly");
		assertArrayNear(insertedLinkedOffsets, linkedDeform.getVertices()[0],
			"topology redo restores migrated Linked Mesh Deform values exactly");

		assertTrue(editor.removeMeshVerticesWithDeform(mesh, new int[] {4}),
			"an inserted internal vertex can be deleted with active Deform tracks");
		assertArrayNear(sourceOffsets, sourceDeform.getVertices()[0],
			"unweighted vertex deletion removes the matching source Deform XY pair");
		assertArrayNear(linkedOffsets, linkedDeform.getVertices()[0],
			"unweighted vertex deletion removes the matching Linked Mesh Deform XY pair");

		SkeletonDocument weightedDoc = createDocument();
		SlotData weightedSlot = new SlotData(0, "weighted-slot", weightedDoc.data.findBone("root"));
		weightedSlot.setBlendMode(BlendMode.normal);
		weightedDoc.data.getSlots().add(weightedSlot);
		Skin weightedSkin = new Skin("default");
		weightedDoc.data.getSkins().add(weightedSkin);
		weightedDoc.data.setDefaultSkin(weightedSkin);
		Sequence weightedSequence = new Sequence(1, false);
		weightedSequence.getRegions()[0] = new TextureRegion();
		MeshAttachment weighted = new MeshAttachment("weighted", weightedSequence);
		weighted.setPath("weighted");
		weighted.setBones(new int[] {1, 0, 2, 0, 1, 1, 1});
		weighted.setVertices(new float[] {0f, 0f, 1f, 10f, 0f, 0.5f, 0f, 0f, 0.5f, 0f, 10f, 1f});
		weighted.setWorldVerticesLength(6);
		weighted.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0f, 1f});
		weighted.setTriangles(new short[] {0, 1, 2});
		weighted.setHullLength(6);
		weighted.setWidth(10f);
		weighted.setHeight(10f);
		weighted.updateSequence();
		weightedSkin.setAttachment(0, "weighted", weighted);
		Animation.DeformTimeline weightedDeform = new Animation.DeformTimeline(1, 0, 0, weighted);
		weightedDeform.setFrame(0, 0f, new float[] {2f, 4f, 6f, 8f, 10f, 12f, 14f, 16f});
		Animation weightedAnimation = new Animation("weighted-deform");
		Array<Animation.Timeline> weightedTimelines = new Array<>(true, 1, Animation.Timeline[]::new);
		weightedTimelines.add(weightedDeform);
		weightedAnimation.setTimelines(weightedTimelines, new IntArray());
		weightedDoc.data.getAnimations().add(weightedAnimation);
		weightedDoc.skeleton = new Skeleton(weightedDoc.data);
		weightedDoc.skeleton.setSkin(weightedSkin);

		SkeletonEditor weightedEditor = new SkeletonEditor(weightedDoc);
		assertTrue(weightedEditor.insertMeshVertexWithDeform(weighted, 0,
			1f / 3f, 1f / 3f, 1f / 3f), "weighted Mesh topology insertion migrates Deform influence blocks");
		assertArrayNear(new float[] {2f, 4f, 6f, 8f, 10f, 12f, 14f, 16f,
			3.333333f, 5.333333f, 12.666667f, 14.666667f}, weightedDeform.getVertices()[0],
			"weighted insertion interpolates one Deform XY pair for each new bone influence");
	}

	private static void weldsMeshVerticesAndCopiesWeights() {
		SkeletonEditor.MeshGeometryState duplicate = new SkeletonEditor.MeshGeometryState(Color.WHITE, "weld",
			null, new float[] {0f, 0f, 10f, 0f, 10f, 10f, 0f, 0f, 5f, 5f},
			new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 0.5f, 0.5f},
			new short[] {0, 1, 4, 3, 4, 2}, 8,
			new short[] {0, 1, 1, 2, 2, 3, 3, 0, 0, 3}, 10f, 10f);
		SkeletonEditor.MeshWeldResult staticWeld = SkeletonEditor.weldMeshVertices(duplicate,
			duplicate.vertices(), 0f);
		assertEquals(1, staticWeld.weldedVertices(), "one exact duplicate is welded");
		assertIntArray(new int[] {0, 1, 2, 0, 3}, staticWeld.oldToNew(),
			"weld exposes the old-to-new vertex map");
		assertIntArray(new int[] {0, 1, 2, 4}, staticWeld.newToOld(),
			"weld keeps the first coincident vertex as representative");
		assertShortArray(new short[] {0, 1, 3, 0, 3, 2}, staticWeld.geometry().triangles(),
			"weld remaps surviving triangles");
		assertShortArray(new short[] {0, 1, 1, 2, 2, 0}, staticWeld.geometry().edges(),
			"weld removes collapsed and duplicate edges");
		assertEquals(6, staticWeld.geometry().hullLength(),
			"weld removes duplicate hull vertices from the hull length");

		SkeletonDocument weldDoc = createDocument();
		SlotData weldSlot = new SlotData(0, "weld-slot", weldDoc.data.findBone("root"));
		weldSlot.setBlendMode(BlendMode.normal);
		weldDoc.data.getSlots().add(weldSlot);
		Skin weldSkin = new Skin("default");
		weldDoc.data.getSkins().add(weldSkin);
		weldDoc.data.setDefaultSkin(weldSkin);
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment mesh = new MeshAttachment("weld", sequence);
		mesh.setPath("weld");
		mesh.setVertices(duplicate.vertices());
		mesh.setWorldVerticesLength(10);
		mesh.setRegionUVs(duplicate.regionUVs());
		mesh.setTriangles(duplicate.triangles());
		mesh.setHullLength(duplicate.hullLength());
		mesh.setEdges(duplicate.edges());
		mesh.setWidth(10f);
		mesh.setHeight(10f);
		mesh.updateSequence();
		weldSkin.setAttachment(0, "weld", mesh);
		MeshAttachment linked = mesh.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		weldSkin.setAttachment(0, "linked", linked);
		Animation.DeformTimeline directDeform = new Animation.DeformTimeline(1, 0, 0, mesh);
		directDeform.setFrame(0, 0f, new float[] {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f});
		Animation.DeformTimeline linkedDeform = new Animation.DeformTimeline(1, 0, 0, linked);
		linkedDeform.setFrame(0, 0f, new float[] {10f, 11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f});
		Animation deformAnimation = new Animation("weld-deform");
		Array<Animation.Timeline> deformTimelines = new Array<>(true, 2, Animation.Timeline[]::new);
		deformTimelines.add(directDeform, linkedDeform);
		deformAnimation.setTimelines(deformTimelines, new IntArray());
		weldDoc.data.getAnimations().add(deformAnimation);
		weldDoc.skeleton = new Skeleton(weldDoc.data);
		weldDoc.skeleton.setSkin(weldSkin);
		SkeletonEditor weldEditor = new SkeletonEditor(weldDoc);
		SkeletonEditor.MeshTopologySnapshot before = weldEditor.snapshotMeshTopology(mesh);
		SkeletonEditor.MeshWeldResult applied = weldEditor.weldMeshVerticesWithDeform(mesh, weldSlot, 0f);
		assertEquals(1, applied.weldedVertices(), "document weld applies the topology result");
		assertArrayNear(new float[] {0f, 1f, 2f, 3f, 4f, 5f, 8f, 9f},
			directDeform.getVertices()[0], "source Mesh Deform keeps representative vertex pairs");
		assertArrayNear(new float[] {10f, 11f, 12f, 13f, 14f, 15f, 18f, 19f},
			linkedDeform.getVertices()[0], "independent Linked Mesh Deform follows the weld");
		SkeletonEditor.MeshTopologySnapshot after = weldEditor.snapshotMeshTopology(mesh);
		weldEditor.restoreMeshTopology(before);
		assertEquals(5, SkeletonEditor.meshGeometryState(mesh).vertexCount(),
			"weld undo restores all vertices");
		assertEquals(10, directDeform.getVertices()[0].length,
			"weld undo restores Deform layout");
		weldEditor.restoreMeshTopology(after);
		assertEquals(4, SkeletonEditor.meshGeometryState(mesh).vertexCount(),
			"weld redo restores compact topology");

		SkeletonDocument copyDoc = createDocument();
		copyDoc.data.findBone("root").getSetupPose().setScale(1f, 1f);
		copyDoc.data.findBone("child").getSetupPose().setX(10f);
		copyDoc.data.findBone("child").getSetupPose().setScale(1f, 1f);
		SlotData targetSlot = new SlotData(0, "target-slot", copyDoc.data.findBone("child"));
		targetSlot.setBlendMode(BlendMode.normal);
		copyDoc.data.getSlots().add(targetSlot);
		copyDoc.skeleton = new Skeleton(copyDoc.data);
		SkeletonEditor copyEditor = new SkeletonEditor(copyDoc);
		SkeletonEditor.MeshGeometryState target = new SkeletonEditor.MeshGeometryState(Color.WHITE, "target",
			null, new float[] {1f, 2f, 3f, 4f}, new float[] {0.9f, 0.9f, 0.1f, 0.1f},
			new short[0], 4, null, 1f, 1f);
		SkeletonEditor.MeshGeometryState source = new SkeletonEditor.MeshGeometryState(Color.WHITE, "source",
			new int[] {1, 0, 1, 1}, new float[] {0f, 0f, 1f, 0f, 0f, 1f},
			new float[] {0f, 0f, 1f, 1f}, new short[0], 4, null, 1f, 1f);
		SkeletonEditor.MeshGeometryState copied = copyEditor.copyMeshWeights(target, targetSlot, source);
		assertIntArray(new int[] {1, 1, 1, 0}, copied.bones(),
			"nearest UV chooses the source influence for each target vertex");
		assertArrayNear(new float[] {1f, 2f, 1f, 13f, 4f, 1f}, copied.vertices(),
			"copied influences are expressed in each source bone's setup space");
		assertArrayNear(target.vertices(), copyEditor.unweightMeshVertices(copied.bones(), copied.vertices(),
			copied.vertexCount(), targetSlot), "copying weights preserves target setup positions");
	}

	private static void reordersMeshHullWithDeformSafely() {
		assertIntArray(new int[] {3, 1, 4, 2}, SkeletonEditor.convexMeshHull(new float[] {
			5f, 5f, 10f, 0f, 0f, 10f, 0f, 0f, 10f, 10f, 10f, 10f
		}), "automatic hull excludes an internal point and a duplicate outer point deterministically");
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonEditor.convexMeshHull(new float[] {0f, 0f, 5f, 0f, 10f, 0f}),
			"automatic hull rejects collinear geometry");

		SkeletonEditor.MeshGeometryState weighted = new SkeletonEditor.MeshGeometryState(Color.WHITE, "weighted",
			new int[] {1, 0, 2, 0, 1, 1, 1, 1, 0},
			new float[] {0f, 0f, 1f, 10f, 0f, 0.4f, 11f, 1f, 0.6f,
				20f, 0f, 1f, 30f, 0f, 1f},
			new float[] {0f, 0f, 0.3f, 0f, 0.6f, 0f, 1f, 0f},
			new short[] {0, 1, 2, 0, 2, 3}, 8, new short[] {0, 1, 1, 2, 2, 3, 3, 0}, 30f, 10f);
		SkeletonEditor.MeshVertexOrder weightedOrder = SkeletonEditor.meshHullVertexOrder(4,
			new int[] {2, 0, 3});
		SkeletonEditor.MeshGeometryState reorderedWeighted = SkeletonEditor.reorderMeshVertices(weighted,
			weightedOrder);
		assertIntArray(new int[] {1, 1, 1, 0, 1, 0, 2, 0, 1}, reorderedWeighted.bones(),
			"weighted hull reordering moves complete influence blocks");
		assertArrayNear(new float[] {20f, 0f, 1f, 0f, 0f, 1f, 30f, 0f, 1f,
			10f, 0f, 0.4f, 11f, 1f, 0.6f}, reorderedWeighted.vertices(),
			"weighted hull reordering preserves each influence's local coordinates and weight");
		assertEquals(6, reorderedWeighted.hullLength(), "requested weighted hull size is applied");
		assertThrows(IllegalArgumentException.class,
			() -> SkeletonEditor.meshHullVertexOrder(4, new int[] {0, 1, 1}),
			"duplicate hull indices are rejected");

		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "mesh-slot", doc.data.findBone("root"));
		slot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment mesh = new MeshAttachment("source", sequence);
		mesh.setPath("mesh");
		mesh.setBones(null);
		mesh.setVertices(new float[] {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f, 5f, 5f});
		mesh.setWorldVerticesLength(10);
		mesh.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0.5f, 0.5f});
		mesh.setTriangles(new short[] {0, 1, 4, 1, 2, 4, 2, 3, 4, 3, 0, 4});
		mesh.setHullLength(8);
		mesh.setEdges(new short[] {0, 1, 1, 2, 2, 3, 3, 0});
		mesh.setWidth(10f);
		mesh.setHeight(10f);
		mesh.updateSequence();
		skin.setAttachment(0, "source", mesh);
		MeshAttachment linked = mesh.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		skin.setAttachment(0, "linked", linked);

		Animation.DeformTimeline sourceDeform = new Animation.DeformTimeline(1, 0, 0, mesh);
		sourceDeform.setFrame(0, 0f, new float[] {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f});
		Animation.DeformTimeline linkedDeform = new Animation.DeformTimeline(1, 0, 0, linked);
		linkedDeform.setFrame(0, 0f,
			new float[] {10f, 11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f});
		Animation animation = new Animation("hull-deform");
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(sourceDeform);
		timelines.add(linkedDeform);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);

		SkeletonEditor editor = new SkeletonEditor(doc);
		SkeletonEditor.MeshGeometryState before = SkeletonEditor.meshGeometryState(mesh);
		SkeletonEditor.MeshVertexOrder forward = SkeletonEditor.meshHullVertexOrder(5,
			new int[] {3, 0, 1});
		SkeletonEditor.MeshVertexOrder reverse = forward.inverse(before.hullLength() / 2);
		long revision = doc.revision;
		assertTrue(editor.applyMeshVertexOrder(mesh, forward), "independent Mesh hull can be reordered atomically");
		assertArrayNear(new float[] {0f, 10f, 0f, 0f, 10f, 0f, 10f, 10f, 5f, 5f}, mesh.getVertices(),
			"hull operation places requested vertices first and keeps internal vertices stable");
		assertShortArray(new short[] {1, 2, 4, 2, 3, 4, 3, 0, 4, 0, 1, 4}, mesh.getTriangles(),
			"hull operation remaps every triangle index");
		assertShortArray(new short[] {1, 2, 2, 3, 3, 0, 0, 1}, mesh.getEdges(),
			"hull operation remaps editor edge metadata");
		assertEquals(6, mesh.getHullLength(), "hull operation stores the requested Spine value count");
		assertTrue(linked.getVertices() == mesh.getVertices() && linked.getTriangles() == mesh.getTriangles(),
			"linked meshes remain bound to the reordered source arrays");
		assertArrayNear(new float[] {6f, 7f, 0f, 1f, 2f, 3f, 4f, 5f, 8f, 9f},
			sourceDeform.getVertices()[0], "source Deform frame follows the logical vertices");
		assertArrayNear(new float[] {16f, 17f, 10f, 11f, 12f, 13f, 14f, 15f, 18f, 19f},
			linkedDeform.getVertices()[0], "independent linked-mesh Deform frame follows shared geometry");
		assertTrue(doc.revision == revision + 1, "hull reordering marks the document once");

		assertTrue(editor.applyMeshVertexOrder(mesh, reverse), "inverse hull order restores the original state");
		assertTrue(SkeletonEditor.meshGeometryState(mesh).sameContent(before),
			"inverse hull order restores all source Mesh arrays and hull length");
		assertArrayNear(new float[] {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f},
			sourceDeform.getVertices()[0], "inverse hull order restores Deform data exactly");

		SkeletonEditor.MeshGeometryState restored = SkeletonEditor.meshGeometryState(mesh);
		float[] sourceFrameBeforeFailure = sourceDeform.getVertices()[0].clone();
		linkedDeform.getVertices()[0] = null;
		revision = doc.revision;
		assertThrows(IllegalArgumentException.class, () -> editor.applyMeshVertexOrder(mesh, forward),
			"an incomplete linked-mesh Deform frame rejects hull reordering");
		assertTrue(SkeletonEditor.meshGeometryState(mesh).sameContent(restored),
			"rejected hull reordering leaves every Mesh array unchanged");
		assertArrayNear(sourceFrameBeforeFailure, sourceDeform.getVertices()[0],
			"rejected hull reordering does not partially remap earlier Deform timelines");
		assertTrue(doc.revision == revision, "rejected hull reordering does not dirty the document");
	}

	private static void reordersWeightedMeshHullWithDeformSafely() {
		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "weighted-hull-slot", doc.data.findBone("root"));
		slot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		MeshAttachment mesh = new MeshAttachment("weighted-hull", sequence);
		mesh.setPath("weighted-hull");
		mesh.setBones(new int[] {1, 0, 2, 0, 1, 1, 1, 1, 0});
		mesh.setVertices(new float[] {0f, 0f, 1f, 10f, 0f, 0.4f, 11f, 1f, 0.6f,
			20f, 0f, 1f, 30f, 0f, 1f});
		mesh.setWorldVerticesLength(8);
		mesh.setRegionUVs(new float[] {0f, 0f, 0.3f, 0f, 0.6f, 0f, 1f, 0f});
		mesh.setTriangles(new short[] {0, 1, 2, 0, 2, 3});
		mesh.setHullLength(8);
		mesh.setEdges(new short[] {0, 1, 1, 2, 2, 3, 3, 0});
		mesh.setWidth(30f);
		mesh.setHeight(10f);
		mesh.updateSequence();
		skin.setAttachment(0, "weighted-hull", mesh);
		MeshAttachment linked = mesh.newLinkedMesh();
		linked.setTimelineAttachment(linked);
		skin.setAttachment(0, "weighted-linked", linked);

		float[] sourceValues = {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f};
		float[] linkedValues = {10f, 11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f};
		Animation.DeformTimeline sourceDeform = new Animation.DeformTimeline(1, 0, 0, mesh);
		sourceDeform.setFrame(0, 0f, sourceValues.clone());
		Animation.DeformTimeline linkedDeform = new Animation.DeformTimeline(1, 0, 0, linked);
		linkedDeform.setFrame(0, 0f, linkedValues.clone());
		Animation animation = new Animation("weighted-hull-deform");
		Array<Animation.Timeline> timelines = new Array<>(true, 2, Animation.Timeline[]::new);
		timelines.add(sourceDeform, linkedDeform);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);

		SkeletonEditor editor = new SkeletonEditor(doc);
		SkeletonEditor.MeshGeometryState before = SkeletonEditor.meshGeometryState(mesh);
		SkeletonEditor.MeshVertexOrder forward = SkeletonEditor.meshHullVertexOrder(4, new int[] {2, 0, 3});
		SkeletonEditor.MeshVertexOrder reverse = forward.inverse(before.hullLength() / 2);
		assertTrue(editor.applyMeshVertexOrder(mesh, forward),
			"weighted Mesh hull reordering accepts vertices with different influence counts");
		assertArrayNear(new float[] {6f, 7f, 0f, 1f, 8f, 9f, 2f, 3f, 4f, 5f},
			sourceDeform.getVertices()[0], "weighted source Deform moves complete influence blocks");
		assertArrayNear(new float[] {16f, 17f, 10f, 11f, 18f, 19f, 12f, 13f, 14f, 15f},
			linkedDeform.getVertices()[0], "weighted Linked Mesh Deform moves complete influence blocks");
		assertTrue(linked.getBones() == mesh.getBones() && linked.getVertices() == mesh.getVertices(),
			"weighted Linked Mesh remains bound to reordered source geometry");

		assertTrue(editor.applyMeshVertexOrder(mesh, reverse), "weighted Mesh hull order can be undone losslessly");
		assertTrue(SkeletonEditor.meshGeometryState(mesh).sameContent(before),
			"weighted inverse hull order restores all source geometry");
		assertArrayNear(sourceValues, sourceDeform.getVertices()[0],
			"weighted inverse hull order restores source Deform");
		assertArrayNear(linkedValues, linkedDeform.getVertices()[0],
			"weighted inverse hull order restores Linked Mesh Deform");
	}

	private static void createsAndEditsStandardAttachmentsSafely() {
		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "standard-slot", doc.data.findBone("child"));
		slot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);
		SkeletonEditor editor = new SkeletonEditor(doc);

		BoundingBoxAttachment box = (BoundingBoxAttachment)editor.createStandardAttachment(skin, slot, "box",
			SkeletonEditor.StandardAttachmentType.BOUNDING_BOX);
		PathAttachment path = (PathAttachment)editor.createStandardAttachment(skin, slot, "path",
			SkeletonEditor.StandardAttachmentType.PATH);
		PointAttachment point = (PointAttachment)editor.createStandardAttachment(skin, slot, "point",
			SkeletonEditor.StandardAttachmentType.POINT);
		ClippingAttachment clipping = (ClippingAttachment)editor.createStandardAttachment(skin, slot, "clip",
			SkeletonEditor.StandardAttachmentType.CLIPPING);
		assertTrue(box != null && path != null && point != null && clipping != null,
			"all standard non-image attachments can be created");
		assertTrue(box.getWorldVerticesLength() == 8 && path.getWorldVerticesLength() == 12
			&& clipping.getWorldVerticesLength() == 8, "created vertex attachments have usable default geometry");
		assertTrue(path.getLengths()[0] < path.getLengths()[1],
			"created Path has increasing cumulative curve lengths");
		assertTrue(clipping.getEndSlot() == doc.data.getSlots().peek(),
			"created Clipping has a binary-exportable default end slot");
		assertTrue(editor.createStandardAttachment(skin, slot, "path",
			SkeletonEditor.StandardAttachmentType.PATH) == null,
			"standard attachment creation never overwrites an existing skin entry");

		var pathState = new SkeletonEditor.StandardAttachmentState(
			SkeletonEditor.StandardAttachmentType.PATH, new Color(0.9f, 0.3f, 0.2f, 0.8f), 6, null,
			new float[] {-90f, 0f, -70f, 10f, -25f, 20f, 25f, -20f, 70f, -10f, 90f, 0f},
			true, false, new float[] {80f, 160f}, 0f, 0f, 0f, null, false, false);
		assertTrue(editor.validateStandardAttachment(path, pathState) == null
			&& editor.updateStandardAttachment(path, pathState), "Path geometry and runtime options can be edited");
		assertTrue(path.getClosed() && !path.getConstantSpeed() && path.getLengths()[1] == 160f,
			"Path edit applies closed, constant-speed, and cumulative length data");

		var pointState = new SkeletonEditor.StandardAttachmentState(
			SkeletonEditor.StandardAttachmentType.POINT, new Color(0.8f, 0.9f, 0.1f, 1f), 0, null, null,
			false, false, null, 12f, -7f, 35f, null, false, false);
		var pointBefore = SkeletonEditor.standardAttachmentState(point);
		EditHistory history = new EditHistory(doc);
		assertTrue(editor.updateStandardAttachment(point, pointState), "Point position and rotation can be edited");
		var pointAfter = SkeletonEditor.standardAttachmentState(point);
		history.record("编辑 Point 附件",
			() -> restoreStandardAttachment(editor, point, pointBefore),
			() -> restoreStandardAttachment(editor, point, pointAfter));
		assertNear(12f, point.getX(), "Point X edit");
		assertNear(35f, point.getRotation(), "Point rotation edit");
		assertTrue(history.undo(), "standard attachment edits can be undone");
		assertNear(0f, point.getX(), "Point undo restores X");
		assertTrue(history.redo(), "standard attachment edits can be redone");
		assertNear(12f, point.getX(), "Point redo restores X");

		var clippingState = new SkeletonEditor.StandardAttachmentState(
			SkeletonEditor.StandardAttachmentType.CLIPPING, new Color(0.2f, 0.3f, 0.8f, 0.7f), 3, null,
			new float[] {-30f, -30f, 40f, -30f, 0f, 50f}, false, false, null,
			0f, 0f, 0f, slot, true, true);
		assertTrue(editor.updateStandardAttachment(clipping, clippingState),
			"Clipping polygon, end slot, convex, and inverse options can be edited");
		assertTrue(clipping.getEndSlot() == slot && clipping.getConvex() && clipping.getInverse(),
			"Clipping edit applies all runtime options");

		Animation.DeformTimeline deform = new Animation.DeformTimeline(1, 0, slot.getIndex(), box);
		deform.setFrame(0, 0f, box.getVertices().clone());
		Animation animation = new Animation("box-deform");
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(deform);
		animation.setTimelines(timelines, new IntArray());
		doc.data.getAnimations().add(animation);
		AnimationEditor animationEditor = new AnimationEditor(doc);
		float[] boxOffsets = animationEditor.deformOffsetsAt(deform, 0);
		var boxBefore = SkeletonEditor.standardAttachmentState(box);
		float[] movedBoxVertices = boxBefore.vertices();
		for (int value = 0; value < movedBoxVertices.length; value++) movedBoxVertices[value] += value + 1f;
		var movedBox = new SkeletonEditor.StandardAttachmentState(
			SkeletonEditor.StandardAttachmentType.BOUNDING_BOX, boxBefore.color(), boxBefore.vertexCount(), null,
			movedBoxVertices, false, false, null, 0f, 0f, 0f, null, false, false);
		assertTrue(editor.updateStandardAttachment(box, movedBox),
			"unweighted Bounding Box setup vertices can move with an existing Deform track");
		assertArrayNear(boxOffsets, animationEditor.deformOffsetsAt(deform, 0),
			"Bounding Box setup editing preserves JSON-visible Deform offsets");
		var weightedBox = new SkeletonEditor.StandardAttachmentState(
			SkeletonEditor.StandardAttachmentType.BOUNDING_BOX, box.getColor(), 4,
			new int[] {1, 0, 1, 0, 1, 0, 1, 0},
			new float[] {-50f, -50f, 1f, 50f, -50f, 1f, 50f, 50f, 1f, -50f, 50f, 1f},
			false, false, null, 0f, 0f, 0f, null, false, false);
		String deformReason = editor.validateStandardAttachment(box, weightedBox);
		assertTrue(deformReason != null && deformReason.contains("Deform"),
			"existing Deform timelines prevent an incompatible standard attachment weight-mode change");
		assertTrue(!editor.updateStandardAttachment(box, weightedBox) && box.getBones() == null,
			"blocked standard attachment edits preserve the original geometry");

		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"type\": \"boundingbox\"") && json.contains("\"type\": \"path\"")
			&& json.contains("\"type\": \"point\"") && json.contains("\"type\": \"clipping\""),
			"created and edited standard attachments remain exportable");
	}

	private static void restoreStandardAttachment(SkeletonEditor editor,
		com.esotericsoftware.spine.attachments.Attachment attachment,
		SkeletonEditor.StandardAttachmentState state) {
		if (!editor.updateStandardAttachment(attachment, state))
			throw new IllegalStateException("standard attachment history restore failed");
	}

	private static void convertsWeightedMeshToSlotSpace() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.findBone("root");
		BoneData child = doc.data.findBone("child");
		root.getSetupPose().setScale(1f, 1f);
		child.getSetupPose().setScale(1f, 1f);
		child.getSetupPose().setX(10f);
		SlotData slot = new SlotData(0, "weighted-slot", root);
		slot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		doc.skeleton = new Skeleton(doc.data);

		SkeletonEditor editor = new SkeletonEditor(doc);
		int[] bones = {2, root.getIndex(), child.getIndex()};
		float[] weighted = {2f, 0f, 0.5f, 0f, 0f, 0.5f};
		float[] local = editor.unweightMeshVertices(bones, weighted, 1, slot);
		assertNear(6f, local[0], "unweighting preserves the weighted setup world X in slot space");
		assertNear(0f, local[1], "unweighting preserves the weighted setup world Y in slot space");
		assertThrows(IllegalArgumentException.class,
			() -> editor.unweightMeshVertices(new int[] {2, 0}, weighted, 1, slot),
			"unweighting rejects incomplete influence encoding");
	}

	private static void renamesSlotAndRemapsReferences() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		SlotData clipSlot = editor.addSlot("clip-slot", doc.data.findBone("child"));
		SlotData target = editor.addSlot("target-slot", doc.data.findBone("child"));
		target.getSetupPose().getColor().set(0.25f, 0.5f, 0.75f, 0.9f);
		setDarkColor(target, new Color(0.1f, 0.2f, 0.3f, 1f));
		target.setBlendMode(BlendMode.screen);
		target.setVisible(false);

		Skin skin = new Skin("default");
		ClippingAttachment clipping = new ClippingAttachment("clip");
		clipping.setEndSlot(target);
		skin.setAttachment(clipSlot.getIndex(), "clip", clipping);
		RegionAttachment region = new RegionAttachment("region", new Sequence(1, false));
		skin.setAttachment(target.getIndex(), "region", region);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);

		PathConstraintData path = new PathConstraintData("path");
		path.getBones().add(doc.data.findBone("child"));
		path.setSlot(target);
		path.setPositionMode(PathConstraintData.PositionMode.fixed);
		path.setSpacingMode(PathConstraintData.SpacingMode.fixed);
		path.setRotateMode(PathConstraintData.RotateMode.chain);
		doc.data.getConstraints().add(path);
		editor.rebuildSkeleton();
		doc.setSlotSelection(doc.skeleton.findSlot("target-slot"), "", "");
		doc.setTimelineViewState(new SkeletonDocument.TimelineViewState("slot-edit", true, 0,
			"slot\u001ftarget-slot\u001fCOLOR", 2f, 4f, true,
			new String[] {"slot\u001ftarget-slot\u001fCOLOR",
				"sequence\u001ftarget-slot\u001fdefault\u001fregion",
				"deform\u001ftarget-slot\u001fdefault\u001fregion"},
			new float[] {0f, 0.5f, 1f}, new int[] {0, 0, 0}));

		assertEquals("renamed-slot", editor.renameSlot(target, "renamed-slot"), "slot rename result");
		SlotData renamed = doc.data.findSlot("renamed-slot");
		assertTrue(renamed != null && renamed.getIndex() == 1, "renamed slot keeps its index");
		assertTrue(path.getSlot() == renamed, "path constraint follows renamed slot data");
		assertTrue(clipping.getEndSlot() == renamed, "clipping end slot follows renamed slot data");
		assertTrue(skin.getAttachment(renamed.getIndex(), "region") == region,
			"skin attachments remain at the same slot index");
		assertTrue(renamed.getBlendMode() == BlendMode.screen && !renamed.getVisible(),
			"renamed slot keeps setup properties");
		assertNear(0.2f, renamed.getSetupPose().getDarkColor().g, "renamed slot keeps dark color");
		assertTrue(doc.skeleton.findSlot("renamed-slot") != null, "runtime skeleton uses renamed slot");
		assertTrue(doc.getSelectionKind() == SkeletonDocument.SelectionKind.SLOT
			&& doc.getSelectedSlotName().equals("renamed-slot"),
			"renaming a selected slot preserves its editor selection");
		for (String key : doc.timelineViewState().selectedTrackKeys())
			assertTrue(key.contains("\u001frenamed-slot\u001f"),
				"slot rename migrates every Timeline identity: " + key);
		assertEquals("clip-slot2", editor.renameSlot(renamed, "clip-slot"),
			"slot rename resolves duplicate names");
		assertTrue(doc.getSelectedSlotName().equals("clip-slot2")
			&& doc.timelineViewState().curveTrackKey().equals("slot\u001fclip-slot2\u001fCOLOR"),
			"repeated slot renames keep the active selection and curve track synchronized");
	}

	private static void deletesOnlyUnreferencedTailSlot() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		SlotData first = editor.addSlot("first-slot", doc.data.findBone("child"));
		SlotData tail = editor.addSlot("tail-slot", doc.data.findBone("child"));
		assertTrue(editor.getSlotDeleteBlockReason(first) == null,
			"an unreferenced non-tail slot can be removed by structural remapping");

		AnimationEditor animations = new AnimationEditor(doc);
		var animation = animations.addAnimation("slot-animation");
		animations.addAttachmentKeyframe(animation, tail.getIndex(), 0f, null);
		assertTrue(editor.getSlotDeleteBlockReason(tail).contains("动画"),
			"animated slot deletion is blocked");
		doc.data.getAnimations().clear();

		Skin skin = new Skin("default");
		skin.setAttachment(tail.getIndex(), "region", new RegionAttachment("region", new Sequence(1, false)));
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		assertTrue(editor.getSlotDeleteBlockReason(tail).contains("皮肤"),
			"slot attachment blocks deletion");
		skin.removeAttachment(tail.getIndex(), "region");

		PathConstraintData path = new PathConstraintData("path");
		path.setSlot(tail);
		doc.data.getConstraints().add(path);
		assertTrue(editor.getSlotDeleteBlockReason(tail).contains("路径约束"),
			"path constraint blocks slot deletion");
		doc.data.getConstraints().clear();

		ClippingAttachment clipping = new ClippingAttachment("clip");
		clipping.setEndSlot(tail);
		skin.setAttachment(first.getIndex(), "clip", clipping);
		assertTrue(editor.getSlotDeleteBlockReason(tail).contains("裁剪附件"),
			"clipping end slot blocks deletion");
		skin.removeAttachment(first.getIndex(), "clip");

		doc.setSlotSelection(doc.skeleton.findSlot("tail-slot"), "", "");
		doc.setTimelineViewState(new SkeletonDocument.TimelineViewState("slot-edit", true, 0,
			"slot\u001ftail-slot\u001fCOLOR", 2f, 4f, true,
			new String[] {"slot\u001ftail-slot\u001fCOLOR", "bone\u001froot\u001fROTATE"},
			new float[] {0.25f, 0.5f}, new int[] {0, 0}));
		assertTrue(editor.getSlotDeleteBlockReason(tail) == null, "unreferenced tail slot can be deleted");
		assertTrue(editor.deleteSlot(tail), "tail slot deletion succeeds");
		assertEquals(1, doc.data.getSlots().size, "tail slot is removed");
		assertTrue(doc.skeleton.findSlot("tail-slot") == null, "runtime tail slot is removed");
		assertTrue(doc.getSelectionKind() == SkeletonDocument.SelectionKind.BONE
			&& doc.getSelectedSlotName().isEmpty()
			&& doc.timelineViewState().curveTrackKey().isEmpty()
			&& java.util.Arrays.equals(doc.timelineViewState().selectedTrackKeys(),
				new String[] {"bone\u001froot\u001fROTATE"}),
			"deleting a selected slot clears stale selection and preserves unrelated Timeline state");
	}

	private static void rejectsReferencedBoneWithoutDataLoss() {
		SkeletonDocument doc = createDocument();
		BoneData child = doc.data.findBone("child");
		doc.data.getSlots().add(new SlotData(0, "child-slot", child));
		SkeletonEditor editor = new SkeletonEditor(doc);
		String reason = editor.getDeleteBlockReason(child);
		assertTrue(reason != null && reason.contains("槽位"), "slot reference must block deletion");
		assertTrue(!editor.deleteBone(child), "referenced bone must not be deleted");
		assertEquals(2, doc.data.getBones().size, "blocked deletion keeps bones");
		assertEquals(1, doc.data.getSlots().size, "blocked deletion keeps slots");

		doc.data.getSlots().clear();
		AnimationEditor animations = new AnimationEditor(doc);
		var animation = animations.addAnimation("child-animation");
		animations.ensureTimeline(animation, AnimationEditor.Prop.ROTATE, child.getIndex());
		animations.addKeyframe(animation, AnimationEditor.Prop.ROTATE, child.getIndex(), 0f, 10f, 0f);
		reason = editor.getDeleteBlockReason(child);
		assertTrue(reason != null && reason.contains("动画"), "animation reference must block deletion");
		assertTrue(!editor.deleteBone(child), "animated bone must not be deleted");
		assertEquals(1, doc.data.getAnimations().size, "blocked deletion keeps animations");
	}

	private static void deletesUnreferencedTailBone() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		BoneData child = doc.data.findBone("child");
		assertTrue(editor.addBone("grandchild", child) != null, "nested tail bone is created");
		doc.setAxisSeparationPreferred("walk", "root", "TRANSLATE", true);
		doc.setAxisSeparationPreferred("walk", "child", "SCALE", true);
		doc.setAxisSeparationPreferred("walk", "grandchild", "SHEAR", true);
		doc.setTimelineViewState(new SkeletonDocument.TimelineViewState("walk", true, 0,
			"bone\u001fchild\u001fSCALE", 12f, 8f, true,
			new String[] {"bone\u001froot\u001fROTATE", "bone\u001fgrandchild\u001fSHEAR"},
			new float[] {0.25f, 0.5f}, new int[] {0, 0}));
		assertTrue(editor.getDeleteBlockReason(child) == null, "unreferenced tail bone should be deletable");
		assertTrue(editor.deleteBone(child), "nested tail subtree deletion should succeed");
		assertEquals(1, doc.data.getBones().size, "entire nested tail subtree is removed");
		assertTrue(doc.data.findBone("root") != null, "root remains valid");
		assertTrue(doc.isAxisSeparationPreferred("walk", "root", "TRANSLATE")
			&& !doc.isAxisSeparationPreferred("walk", "child", "SCALE")
			&& !doc.isAxisSeparationPreferred("walk", "grandchild", "SHEAR"),
			"subtree deletion removes only deleted bones' axis separation preferences");
		assertTrue(doc.timelineViewState().curveTrackKey().isEmpty()
			&& doc.timelineViewState().selectedTrackKeys().length == 1
			&& doc.timelineViewState().selectedTrackKeys()[0].equals("bone\u001froot\u001fROTATE")
			&& doc.timelineViewState().selectedTimes()[0] == 0.25f,
			"subtree deletion removes deleted bone tracks while retaining valid Timeline state");
	}

	private static void renameKeepsSlotBindingAndExportName() {
		SkeletonDocument doc = createDocument();
		BoneData child = doc.data.findBone("child");
		BoneData grandchild = new BoneData(2, "grandchild", child);
		grandchild.getSetupPose().setScale(1f, 1f);
		grandchild.getSetupPose().setInherit(Inherit.normal);
		grandchild.setVisible(true);
		doc.data.getBones().add(grandchild);
		SlotData slot = new SlotData(0, "child-slot", child);
		slot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("optional");
		skin.getBones().add(child);
		doc.data.getSkins().add(skin);
		IkConstraintData ik = new IkConstraintData("child-ik");
		ik.getBones().add(child);
		ik.setTarget(doc.data.findBone("root"));
		doc.data.getConstraints().add(ik);
		doc.setSelectedBone(doc.skeleton.findBone("child"));
		doc.setPoseMode(true);
		doc.getSelectedBone().getPose().setX(37f);
		SkeletonEditor editor = new SkeletonEditor(doc);
		assertTrue("renamed".equals(editor.renameBone(child, "renamed")), "rename should succeed");
		BoneData renamed = doc.data.findBone("renamed");
		assertTrue(renamed != null, "renamed bone exists");
		assertTrue(doc.data.findBone("grandchild").getParent() == renamed, "descendant uses rebuilt parent");
		assertTrue(doc.data.getSlots().first().getBoneData() == renamed, "slot references renamed bone data");
		assertTrue(skin.getBones().first() == renamed, "skin references renamed bone data");
		assertTrue(ik.getBones().first() == renamed, "constraint references renamed bone data");
		assertTrue(editor.slotsForBone(renamed).size == 1, "slot remains associated after rename");
		assertTrue(doc.getSelectedBone() != null && "renamed".equals(doc.getSelectedBone().getData().getName()),
			"renamed bone remains selected");
		assertNear(37f, doc.getSelectedBone().getPose().getX(), "rename preserves live pose");
		doc.data.getConstraints().clear();
		skin.getBones().clear();
		String json = com.openspine.io.SkeletonExporter.toJson(doc);
		assertTrue(json.contains("\"bone\": \"renamed\""), "slot exports the current bone name");
		assertTrue(!json.contains("\"bone\": \"child\""), "slot must not export the stale bone name");
	}

	private static void renamePreservesConstraintRelationSelection() {
		SkeletonDocument doc = createDocument();
		BoneData root = doc.data.findBone("root");
		BoneData child = doc.data.findBone("child");
		IkConstraintData ik = new IkConstraintData("aim");
		ik.getBones().add(child);
		ik.setTarget(root);
		doc.data.getConstraints().add(ik);
		SkeletonEditor editor = new SkeletonEditor(doc);
		editor.rebuildSkeleton();

		doc.setConstraintRelationSelection(ik, "target:root", "root");
		assertTrue("renamed-root".equals(editor.renameBone(root, "renamed-root")),
			"constraint target bone rename should succeed");
		assertTrue(doc.getSelectionKind() == SkeletonDocument.SelectionKind.CONSTRAINT
			&& doc.getSelectedConstraint() == ik
			&& doc.getSelectedConstraintRelation().equals("target:renamed-root")
			&& doc.getSelectedBone() == doc.skeleton.findBone("renamed-root"),
			"renaming a constraint target preserves its exact relation selection");

		child = doc.data.findBone("child");
		doc.setConstraintRelationSelection(ik, "constrained:child", "child");
		assertTrue("renamed-child".equals(editor.renameBone(child, "renamed-child")),
			"constrained bone rename should succeed");
		assertTrue(doc.getSelectionKind() == SkeletonDocument.SelectionKind.CONSTRAINT
			&& doc.getSelectedConstraint() == ik
			&& doc.getSelectedConstraintRelation().equals("constrained:renamed-child")
			&& doc.getSelectedBone() == doc.skeleton.findBone("renamed-child"),
			"renaming a constrained bone preserves its exact relation selection");
	}

	private static void copiesAndPastesBoneSubtree() {
		SkeletonDocument doc = createDocument();
		SkeletonEditor editor = new SkeletonEditor(doc);
		BoneData root = doc.data.findBone("root");
		BoneData child = doc.data.findBone("child");
		BoneData grandchild = editor.addBone("grandchild", child);
		child.getSetupPose().setX(12f);
		child.getSetupPose().setScale(1.5f, 2.5f);
		child.getSetupPose().setInherit(Inherit.noRotationOrReflection);
		child.setLength(24f);
		child.setSkinRequired(true);
		child.setIcon("child-icon");
		child.setIconSize(1.4f);
		child.setIconRotation(18f);
		child.getColor().set(0.2f, 0.4f, 0.6f, 0.8f);
		grandchild.getSetupPose().setY(9f);
		grandchild.setLength(7f);
		grandchild.setVisible(false);

		AnimationEditor animations = new AnimationEditor(doc);
		var animation = animations.addAnimation("existing-index");
		animations.ensureTimeline(animation, AnimationEditor.Prop.ROTATE, child.getIndex());
		assertEquals(2, editor.copyBoneSubtree(child), "subtree copy includes all descendants");
		assertTrue(editor.hasSubtreeClipboard() && editor.getSubtreeClipboardSize() == 2,
			"subtree clipboard reports its immutable snapshot size");

		child.getSetupPose().setX(99f);
		grandchild.setLength(99f);
		BoneData conflictingName = editor.addBone("child2", root);
		int beforePaste = doc.data.getBones().size;
		BoneData pastedRoot = editor.pasteBoneSubtree(root);
		assertTrue(pastedRoot != null && "child3".equals(pastedRoot.getName()),
			"pasted subtree root receives a unique name after existing conflicts");
		assertEquals(beforePaste + 2, doc.data.getBones().size, "subtree paste appends every copied bone atomically");
		BoneData pastedChild = doc.data.findBone("grandchild2");
		assertTrue(pastedRoot.getParent() == root, "pasted root binds the requested parent");
		assertTrue(pastedChild != null && pastedChild.getParent() == pastedRoot,
			"pasted descendant binds the newly created parent");
		assertNear(12f, pastedRoot.getSetupPose().getX(), "subtree snapshot is independent from later source edits");
		assertNear(1.5f, pastedRoot.getSetupPose().getScaleX(), "subtree paste preserves setup scale");
		assertTrue(pastedRoot.getSetupPose().getInherit() == Inherit.noRotationOrReflection,
			"subtree paste preserves inherit mode");
		assertNear(24f, pastedRoot.getLength(), "subtree paste preserves length");
		assertTrue(pastedRoot.getSkinRequired(), "subtree paste preserves skin-required state");
		assertEquals("child-icon", pastedRoot.getIcon(), "subtree paste preserves icon");
		assertNear(1.4f, pastedRoot.getIconSize(), "subtree paste preserves icon size");
		assertNear(18f, pastedRoot.getIconRotation(), "subtree paste preserves icon rotation");
		assertNear(0.6f, pastedRoot.getColor().b, "subtree paste preserves editor color");
		assertNear(7f, pastedChild.getLength(), "descendant snapshot is independent from later source edits");
		assertTrue(!pastedChild.getVisible(), "subtree paste preserves descendant visibility");
		assertTrue(doc.skeleton.findBone("child3") != null && doc.skeleton.findBone("grandchild2") != null,
			"runtime skeleton contains the pasted hierarchy");
		assertTrue(doc.getSelectedBone() != null && doc.getSelectedBone().getData() == pastedRoot,
			"pasted subtree root becomes selected");
		Animation.Timeline existingTimeline = animations.findTimeline(animation, AnimationEditor.Prop.ROTATE, 1);
		assertTrue(existingTimeline != null && ((Animation.BoneTimeline)existingTimeline).getBoneIndex() == 1,
			"appending a subtree does not move existing animation bone indices");
		for (int index = 0; index < doc.data.getBones().size; index++)
			assertEquals(index, doc.data.getBones().get(index).getIndex(), "appended subtree index " + index);

		int beforeInvalidPaste = doc.data.getBones().size;
		BoneData external = new BoneData(0, "external", null);
		assertTrue(editor.pasteBoneSubtree(external) == null, "subtree paste rejects a parent outside the document");
		assertEquals(beforeInvalidPaste, doc.data.getBones().size, "rejected subtree paste leaves the document unchanged");
		assertTrue(conflictingName.getIndex() < pastedRoot.getIndex(), "existing appended bones retain their indices");
	}

	private static void pastesCompleteBoneProperties() {
		SkeletonDocument doc = createDocument();
		BoneData source = doc.data.findBone("root");
		BoneData target = doc.data.findBone("child");
		source.setSkinRequired(true);
		source.setIcon(null);
		source.setLength(42f);
		source.getSetupPose().setScale(2f, 3f);
		target.setSkinRequired(false);
		target.setIcon("old-icon");
		SkeletonEditor editor = new SkeletonEditor(doc);
		editor.copyBone(source);
		assertTrue(editor.pasteBoneOnto(target), "paste should succeed for a document bone");
		assertTrue(target.getSkinRequired(), "paste preserves skin-required state");
		assertTrue(target.getIcon() == null, "paste clears an icon absent from the source");
		assertNear(42f, target.getLength(), "paste preserves bone length");
		assertNear(2f, target.getSetupPose().getScaleX(), "paste preserves setup scale x");
		assertNear(3f, target.getSetupPose().getScaleY(), "paste preserves setup scale y");
		assertTrue(doc.dirty, "paste marks the document dirty");
	}

	private static void setsAndClearsSlotAttachment() {
		SkeletonDocument doc = createDocument();
		SlotData slotData = new SlotData(0, "child-slot", doc.data.findBone("child"));
		doc.data.getSlots().add(slotData);
		Skin skin = new Skin("default");
		RegionAttachment region = new RegionAttachment("region", new Sequence(1, false));
		skin.setAttachment(0, "region", region);
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);
		doc.skeleton = new Skeleton(doc.data);

		SkeletonEditor editor = new SkeletonEditor(doc);
		assertTrue(editor.setSlotAttachment(slotData, "region"), "available attachment can be selected");
		assertTrue("region".equals(slotData.getAttachmentName()), "setup attachment name is updated");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == region,
			"runtime slot uses selected attachment");
		doc.dirty = false;
		long unchangedRevision = doc.revision;
		assertTrue(!editor.setSlotAttachment(slotData, "region"), "reselecting the setup attachment is a no-op");
		assertTrue(!doc.dirty && doc.revision == unchangedRevision,
			"unchanged setup attachment does not dirty the document");
		assertTrue(!editor.setSlotAttachment(slotData, "missing"), "unknown attachment is rejected");
		assertTrue("region".equals(slotData.getAttachmentName()), "rejected selection keeps current attachment");
		assertTrue(editor.setSlotAttachment(slotData, null), "attachment can be cleared");
		assertTrue(slotData.getAttachmentName() == null, "setup attachment is cleared");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == null,
			"runtime attachment is cleared");

		RegionAttachment setup = new RegionAttachment("setup", new Sequence(1, false));
		RegionAttachment animated = new RegionAttachment("animated", new Sequence(1, false));
		skin.setAttachment(0, "setup", setup);
		skin.setAttachment(0, "animated", animated);
		slotData.setAttachmentName("setup");
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(skin);
		doc.skeleton.setupPoseSlots();
		doc.animationState = new AnimationState(new AnimationStateData(doc.data));
		AnimationEditor animationEditor = new AnimationEditor(doc);
		var animation = animationEditor.addAnimation("attachment-override");
		animationEditor.addAttachmentKeyframe(animation, 0, 0f, "setup");
		animationEditor.addAttachmentKeyframe(animation, 0, 0.5f, "animated");
		animationEditor.addAttachmentKeyframe(animation, 0, 1f, "animated");
		assertTrue(doc.setAnimation(animation.getName()) && doc.scrubTo(0.75f),
			"attachment animation can be paused at its animated key");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == animated,
			"attachment timeline drives the paused runtime slot");
		assertTrue(editor.setSlotAttachment(slotData, "region"), "setup attachment can change under a paused timeline");
		assertTrue("region".equals(slotData.getAttachmentName()), "setup attachment data is changed");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == animated,
			"setup edit reapplies the paused attachment timeline");
	}

	private static void createsRegionAttachmentInSelectedSkin() {
		SkeletonDocument doc = createDocument();
		SlotData slotData = new SlotData(0, "child-slot", doc.data.findBone("child"));
		doc.data.getSlots().add(slotData);
		Skin defaultSkin = new Skin("default");
		Skin selectedSkin = new Skin("selected");
		Skin inactiveSkin = new Skin("inactive");
		doc.data.getSkins().add(defaultSkin);
		doc.data.getSkins().add(selectedSkin);
		doc.data.getSkins().add(inactiveSkin);
		doc.data.setDefaultSkin(defaultSkin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(selectedSkin);

		TextureRegion region = new TextureRegion();
		SkeletonEditor editor = new SkeletonEditor(doc) {
			@Override protected TextureRegion findRegion(String name) {
				return "body".equals(name) ? region : null;
			}
		};
		RegionAttachment defaultAttachment = editor.setSlotRegionAttachment(defaultSkin, slotData, "bodyAttachment", "body");
		assertTrue(defaultAttachment != null, "default region attachment is created");
		RegionAttachment attachment = editor.setSlotRegionAttachment(selectedSkin, slotData, "bodyAttachment", "body");
		assertTrue(attachment != null, "region attachment is created");
		RegionAttachment inactiveAttachment = editor.setSlotRegionAttachment(inactiveSkin, slotData, "bodyAttachment", "body");
		assertTrue(inactiveAttachment != null, "inactive skin attachment is created");
		assertTrue(selectedSkin.getAttachment(0, "bodyAttachment") == attachment,
			"attachment is written to selected skin");
		assertTrue(defaultSkin.getAttachment(0, "bodyAttachment") == defaultAttachment,
			"default skin attachment remains independent");
		assertEquals("bodyAttachment", slotData.getAttachmentName(), "new attachment becomes setup attachment");
		assertTrue(doc.skeleton.getSkin() == selectedSkin, "selected skin survives skeleton rebuild");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == attachment,
			"runtime slot uses created attachment");
		doc.dirty = false;
		long revision = doc.revision;
		assertTrue(editor.setSlotRegionAttachment(selectedSkin, slotData, "bodyAttachment", "body") == attachment,
			"reapplying the same atlas region returns the existing attachment");
		assertTrue(!doc.dirty && doc.revision == revision,
			"reapplying the same atlas region is a clean no-op");
		assertTrue(editor.removeSkinAttachment(selectedSkin, slotData, "bodyAttachment"),
			"selected skin attachment can be removed");
		assertTrue(selectedSkin.getAttachment(0, "bodyAttachment") == null, "selected skin override is removed");
		assertEquals("bodyAttachment", slotData.getAttachmentName(), "setup name remains for default fallback");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == defaultAttachment,
			"runtime slot falls back to default attachment");
		assertTrue(editor.removeSkinAttachment(defaultSkin, slotData, "bodyAttachment"),
			"default attachment can be removed explicitly");
		assertEquals("bodyAttachment", slotData.getAttachmentName(),
			"setup attachment remains while an inactive skin still owns the placeholder");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == null,
			"runtime slot clears when the active skin has no attachment or default fallback");
		assertTrue(doc.setSkin("inactive"), "inactive skin can be selected after other overrides are removed");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == inactiveAttachment,
			"preserved setup name resolves the attachment when its skin becomes active");
		assertTrue(editor.removeSkinAttachment(inactiveSkin, slotData, "bodyAttachment"),
			"last remaining skin attachment can be removed");
		assertTrue(slotData.getAttachmentName() == null, "setup attachment clears after the final owner is removed");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == null,
			"runtime slot remains clear after the final attachment is removed");
		assertTrue(doc.dirty, "creating an attachment marks document dirty");
	}

	private static void rebindsReplacementAtlasAtomically() {
		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "atlas-slot", doc.data.findBone("child"));
		slot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(slot);
		Skin skin = new Skin("default");
		doc.data.getSkins().add(skin);
		doc.data.setDefaultSkin(skin);

		Sequence imageSequence = new Sequence(2, true);
		imageSequence.setStart(1);
		imageSequence.setDigits(2);
		TextureRegion oldFrame1 = new TextureRegion();
		TextureRegion oldFrame2 = new TextureRegion();
		imageSequence.getRegions()[0] = oldFrame1;
		imageSequence.getRegions()[1] = oldFrame2;
		RegionAttachment region = new RegionAttachment("walk", imageSequence);
		region.setPath("walk");
		region.setWidth(10f);
		region.setHeight(10f);
		region.updateSequence();
		skin.setAttachment(0, "walk", region);

		Sequence meshSequence = new Sequence(1, false);
		TextureRegion oldMeshRegion = new TextureRegion();
		meshSequence.getRegions()[0] = oldMeshRegion;
		MeshAttachment mesh = new MeshAttachment("mesh", meshSequence);
		mesh.setPath("mesh");
		mesh.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0f, 1f});
		mesh.updateSequence();
		skin.setAttachment(0, "mesh", mesh);

		SkeletonEditor editor = new SkeletonEditor(doc);
		SkeletonEditor.AtlasRegionState originalRegions = editor.captureAtlasRegionState();
		TextureAtlas incomplete = new TextureAtlas();
		TextureRegion nextFrame1 = addAtlasRegion(incomplete, "walk01");
		addAtlasRegion(incomplete, "mesh");
		String reason = editor.getAtlasReplacementBlockReason(incomplete);
		assertTrue(reason != null && reason.contains("walk02"),
			"replacement atlas reports the exact missing sequence frame");
		assertTrue(!editor.rebindAtlasRegions(incomplete),
			"an incomplete replacement atlas is rejected");
		assertTrue(imageSequence.getRegions()[0] == oldFrame1
			&& imageSequence.getRegions()[1] == oldFrame2
			&& meshSequence.getRegions()[0] == oldMeshRegion,
			"rejected replacement leaves every attachment region unchanged");

		TextureAtlas complete = new TextureAtlas();
		TextureRegion completeFrame1 = addAtlasRegion(complete, "walk01");
		TextureRegion completeFrame2 = addAtlasRegion(complete, "walk02");
		TextureRegion completeMesh = addAtlasRegion(complete, "mesh");
		assertTrue(editor.getAtlasReplacementBlockReason(complete) == null,
			"complete replacement atlas passes validation");
		assertTrue(editor.rebindAtlasRegions(complete),
			"complete replacement atlas rebinds all image attachments");
		assertTrue(imageSequence.getRegions()[0] == completeFrame1
			&& imageSequence.getRegions()[1] == completeFrame2
			&& meshSequence.getRegions()[0] == completeMesh,
			"Region sequence frames and Mesh regions all use the replacement atlas");
		assertTrue(imageSequence.getRegions()[0] != nextFrame1,
			"a previously rejected atlas never leaks into the applied bindings");
		assertTrue(editor.restoreAtlasRegions(originalRegions)
			&& imageSequence.getRegions()[0] == oldFrame1
			&& imageSequence.getRegions()[1] == oldFrame2
			&& meshSequence.getRegions()[0] == oldMeshRegion,
			"captured atlas bindings restore every original TextureRegion identity");
		incomplete.dispose();
		complete.dispose();
	}

	private static TextureRegion addAtlasRegion(TextureAtlas atlas, String name) {
		return atlas.addRegion(name, new TextureRegion(new HeadlessTexture(), 0, 0, 1, 1));
	}

	private static final class HeadlessTexture extends Texture {
		HeadlessTexture() { super(); }
		@Override public int getWidth() { return 1; }
		@Override public int getHeight() { return 1; }
		@Override public void dispose() {}
	}

	private static void protectsAdvancedAttachmentReferences() {
		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "advanced-slot", doc.data.findBone("child"));
		slot.setBlendMode(BlendMode.normal);
		doc.data.getSlots().add(slot);
		Skin defaultSkin = new Skin("default");
		Skin sourceSkin = new Skin("source-skin");
		Skin linkedSkin = new Skin("linked-skin");
		Skin sequenceSkin = new Skin("sequence-skin");
		doc.data.getSkins().addAll(defaultSkin, sourceSkin, linkedSkin, sequenceSkin);
		doc.data.setDefaultSkin(defaultSkin);

		MeshAttachment source = new MeshAttachment("source", new Sequence(1, false));
		source.setPath("source");
		source.setWorldVerticesLength(6);
		source.setVertices(new float[] {-1f, -1f, 1f, -1f, 0f, 1f});
		source.setRegionUVs(new float[] {0f, 0f, 1f, 0f, 0.5f, 1f});
		source.setTriangles(new short[] {0, 1, 2});
		source.setHullLength(6);
		sourceSkin.setAttachment(0, "source", source);
		MeshAttachment linked = source.newLinkedMesh();
		linkedSkin.setAttachment(0, "linked", linked);

		Sequence imageSequence = new Sequence(3, true);
		RegionAttachment sequenceRegion = new RegionAttachment("sequence", imageSequence);
		sequenceRegion.setPath("sequence");
		sequenceRegion.setWidth(10f);
		sequenceRegion.setHeight(10f);
		sequenceSkin.setAttachment(0, "sequence", sequenceRegion);

		Animation.DeformTimeline deform = new Animation.DeformTimeline(1, 0, 0, source);
		deform.setFrame(0, 0f, source.getVertices().clone());
		Animation deformAnimation = new Animation("deform-reference");
		com.badlogic.gdx.utils.Array<Animation.Timeline> deformTimelines =
			new com.badlogic.gdx.utils.Array<>(true, 1, Animation.Timeline[]::new);
		deformTimelines.add(deform);
		deformAnimation.setTimelines(deformTimelines, new com.badlogic.gdx.utils.IntArray());
		doc.data.getAnimations().add(deformAnimation);

		Animation.SequenceTimeline sequenceTimeline = new Animation.SequenceTimeline(1, 0, sequenceRegion);
		sequenceTimeline.setFrame(0, 0f, Sequence.SequenceMode.loop, 0, 0.1f);
		Animation sequenceAnimation = new Animation("sequence-reference");
		com.badlogic.gdx.utils.Array<Animation.Timeline> sequenceTimelines =
			new com.badlogic.gdx.utils.Array<>(true, 1, Animation.Timeline[]::new);
		sequenceTimelines.add(sequenceTimeline);
		sequenceAnimation.setTimelines(sequenceTimelines, new com.badlogic.gdx.utils.IntArray());
		doc.data.getAnimations().add(sequenceAnimation);

		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(sourceSkin);
		SkeletonEditor editor = new SkeletonEditor(doc);
		long revision = doc.revision;
		assertTrue(editor.getAttachmentDeleteBlockReason(sourceSkin, slot, "source").contains("Deform"),
			"Deform timeline reports a precise attachment deletion block");
		assertTrue(!editor.removeSkinAttachment(sourceSkin, slot, "source"),
			"Deform target attachment cannot be removed");
		assertTrue(sourceSkin.getAttachment(0, "source") == source && doc.revision == revision,
			"blocked Deform target deletion changes no data");

		doc.data.getAnimations().removeValue(deformAnimation, true);
		assertTrue(editor.getAttachmentDeleteBlockReason(sourceSkin, slot, "source").contains("Linked Mesh"),
			"linked mesh reports a precise source deletion block");
		assertTrue(!editor.removeSkinAttachment(sourceSkin, slot, "source"),
			"linked mesh source cannot be removed");
		assertTrue(editor.getRegionReplacementBlockReason(sourceSkin, slot, "source").contains("MeshAttachment"),
			"the Region action identifies a mesh replacement hazard");
		assertTrue(editor.setSlotRegionAttachment(sourceSkin, slot, "source", "anything") == null
			&& sourceSkin.getAttachment(0, "source") == source,
			"the Region action cannot overwrite a mesh attachment");

		assertTrue(editor.removeSkinAttachment(linkedSkin, slot, "linked"),
			"an unreferenced linked mesh can be removed first");
		assertTrue(editor.removeSkinAttachment(sourceSkin, slot, "source"),
			"the source mesh can be removed after its references are cleared");

		assertTrue(editor.getAttachmentDeleteBlockReason(sequenceSkin, slot, "sequence").contains("Sequence"),
			"Sequence timeline reports a precise attachment deletion block");
		assertTrue(editor.getSkinDeleteBlockReason(sequenceSkin).contains("Sequence"),
			"a skin owning a Sequence target cannot be deleted");
		assertTrue(!editor.removeSkin(sequenceSkin) && sequenceSkin.getAttachment(0, "sequence") == sequenceRegion,
			"blocked Sequence skin deletion changes no data");
		long beforeDuplicate = doc.revision;
		assertTrue(editor.getSkinDuplicateBlockReason(sequenceSkin).contains("不能为复制的非 Mesh 附件"),
			"non-mesh attachment timelines explain why skin duplication is unsafe");
		assertTrue(editor.duplicateSkin(sequenceSkin, "unsafe-copy") == null
			&& doc.data.findSkin("unsafe-copy") == null && doc.revision == beforeDuplicate,
			"unsafe skin duplication changes no data");
		assertTrue(editor.getRegionReplacementBlockReason(sequenceSkin, slot, "sequence").contains("多帧 Sequence"),
			"single-region replacement cannot flatten an image sequence");

		Skin sharedOwner = new Skin("shared-owner");
		sharedOwner.setAttachment(0, "sequence-copy", sequenceRegion);
		doc.data.getSkins().add(sharedOwner);
		assertTrue(editor.getAttachmentDeleteBlockReason(sequenceSkin, slot, "sequence") == null,
			"one shared attachment owner can be removed while another remains");
		assertTrue(editor.removeSkinAttachment(sequenceSkin, slot, "sequence"),
			"removing one shared owner preserves the referenced attachment");
		assertTrue(sharedOwner.getAttachment(0, "sequence-copy") == sequenceRegion,
			"the remaining owner retains attachment identity");
	}

	private static void copiesImplicitDefaultSkin() {
		SkeletonDocument doc = createDocument();
		SlotData slotData = new SlotData(0, "child-slot", doc.data.findBone("child"));
		doc.data.getSlots().add(slotData);
		Skin defaultSkin = new Skin("default");
		defaultSkin.setAttachment(0, "region", new RegionAttachment("region", new Sequence(1, false)));
		doc.data.getSkins().add(defaultSkin);
		doc.data.setDefaultSkin(defaultSkin);
		doc.skeleton = new Skeleton(doc.data);
		assertTrue(doc.skeleton.getSkin() == null, "runtime starts with implicit default skin");

		SkeletonEditor editor = new SkeletonEditor(doc);
		editor.rebuildSkeleton();
		assertTrue(doc.skeleton.getSkin() == defaultSkin, "rebuild activates the displayed default skin");
		Skin copy = editor.addSkin("copy", true);
		assertTrue(copy != null && copy.getAttachment(0, "region") != null,
			"new skin copies the active default skin attachments");
		assertTrue(doc.skeleton.getSkin() == copy, "newly copied skin becomes active");
	}

	private static void managesSkinLifecycle() {
		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "child-slot", doc.data.findBone("child"));
		slot.setBlendMode(BlendMode.normal);
		slot.setAttachmentName("region");
		doc.data.getSlots().add(slot);
		Skin defaultSkin = new Skin("default");
		Skin alternate = new Skin("alternate");
		RegionAttachment region = new RegionAttachment("region", new Sequence(1, false));
		region.setPath("region");
		alternate.setAttachment(0, "region", region);
		alternate.getColor().set(0.2f, 0.3f, 0.4f, 0.5f);
		doc.data.getSkins().add(defaultSkin);
		doc.data.getSkins().add(alternate);
		doc.data.setDefaultSkin(defaultSkin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(alternate);
		SkeletonEditor editor = new SkeletonEditor(doc);

		assertTrue(editor.renameSkin(defaultSkin, "renamed-default") == null,
			"default skin rename is rejected");
		doc.dirty = false;
		assertEquals("renamed", editor.renameSkin(alternate, "renamed"), "skin rename result");
		Skin renamed = doc.data.findSkin("renamed");
		assertTrue(renamed != null && doc.data.findSkin("alternate") == null, "skin data is replaced by name");
		assertTrue(renamed.getAttachment(0, "region") == region, "skin rename preserves attachment identity");
		assertNear(0.3f, renamed.getColor().g, "skin rename preserves color");
		assertTrue(doc.skeleton.getSkin() == renamed, "renamed active skin remains selected");
		assertTrue(doc.data.getDefaultSkin() == defaultSkin, "default skin remains unchanged");
		assertTrue(doc.dirty, "skin rename marks the document dirty");

		Skin blank = editor.addSkin("blank", false);
		assertTrue(blank != null && blank.getAttachments().isEmpty(), "blank skin has no attachments");
		doc.skeleton.setSkin(renamed);
		doc.skeleton.setupPoseSlots();
		Skin copy = editor.duplicateSkin(renamed, "renamed");
		assertTrue(copy != null && "renamed2".equals(copy.getName()), "copied skin name is unique");
		assertTrue(copy.getAttachment(0, "region") != null, "copied skin contains current attachments");
		assertTrue(copy.getAttachment(0, "region") != region, "copied skin attachments are independent");
		assertNear(0.3f, copy.getColor().g, "copied skin preserves color");
		assertTrue(editor.removeSkin(copy), "non-default copied skin can be removed");
		assertEquals("region", slot.getAttachmentName(),
			"deleting one skin preserves setup attachment names owned by another skin");
		assertTrue(editor.removeSkin(renamed), "last non-default attachment owner can be removed");
		assertTrue(slot.getAttachmentName() == null,
			"deleting the final skin owner clears its now-dangling setup attachment name");
		assertTrue(!editor.removeSkin(defaultSkin), "default skin cannot be removed");
	}

	private static void preservesPausedSlotTimelineAcrossSkinLifecycle() {
		SkeletonDocument doc = createDocument();
		SlotData slot = new SlotData(0, "child-slot", doc.data.findBone("child"));
		slot.setAttachmentName("setup");
		doc.data.getSlots().add(slot);
		Skin defaultSkin = new Skin("default");
		Skin activeSkin = new Skin("active");
		RegionAttachment setup = new RegionAttachment("setup", new Sequence(1, false));
		RegionAttachment animated = new RegionAttachment("animated", new Sequence(1, false));
		activeSkin.setAttachment(0, "setup", setup);
		activeSkin.setAttachment(0, "animated", animated);
		doc.data.getSkins().add(defaultSkin);
		doc.data.getSkins().add(activeSkin);
		doc.data.setDefaultSkin(defaultSkin);
		doc.skeleton = new Skeleton(doc.data);
		doc.skeleton.setSkin(activeSkin);
		doc.skeleton.setupPoseSlots();
		doc.animationState = new AnimationState(new AnimationStateData(doc.data));

		AnimationEditor animationEditor = new AnimationEditor(doc);
		var animation = animationEditor.addAnimation("skin-lifecycle");
		animationEditor.addAttachmentKeyframe(animation, 0, 0f, "setup");
		animationEditor.addAttachmentKeyframe(animation, 0, 0.5f, "animated");
		animationEditor.addAttachmentKeyframe(animation, 0, 1f, "animated");
		assertTrue(doc.setAnimation(animation.getName()) && doc.scrubTo(0.75f),
			"skin lifecycle test pauses on the animated attachment key");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == animated,
			"paused attachment timeline is active before skin edits");

		SkeletonEditor editor = new SkeletonEditor(doc);
		assertEquals("renamed", editor.renameSkin(activeSkin, "renamed"), "active skin rename result");
		Skin renamed = doc.data.findSkin("renamed");
		assertTrue(doc.skeleton.getSkin() == renamed, "renamed skin remains active");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == animated,
			"active skin rename preserves the paused attachment timeline");
		assertNear(0.75f, doc.getCurrentTime(), "active skin rename preserves paused time");
		assertTrue(!doc.playing, "active skin rename preserves paused state");

		Skin copy = editor.duplicateSkin(renamed, "copy");
		RegionAttachment copiedAnimated = (RegionAttachment)copy.getAttachment(0, "animated");
		assertTrue(doc.skeleton.getSkin() == copy, "duplicated skin becomes active");
		assertTrue(doc.skeleton.findSlot("child-slot").getPose().getAttachment() == copiedAnimated,
			"skin duplication reapplies the paused attachment timeline in the copy");
		assertNear(0.75f, doc.getCurrentTime(), "skin duplication preserves paused time");
		assertTrue(!doc.playing, "skin duplication preserves paused state");
	}

	private static SkeletonDocument createDocument() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		root.getSetupPose().setInherit(Inherit.normal);
		child.getSetupPose().setInherit(Inherit.normal);
		data.getBones().add(root);
		data.getBones().add(child);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.setSelectedBone(doc.skeleton.getRootBone());
		return doc;
	}

	private static void setDarkColor(SlotData slot, Color color) {
		try {
			java.lang.reflect.Field field = slot.getSetupPose().getClass().getDeclaredField("darkColor");
			field.setAccessible(true);
			field.set(slot.getSetupPose(), new Color(color));
		} catch (ReflectiveOperationException error) {
			throw new AssertionError("unable to initialize slot dark color", error);
		}
	}

	private static <T extends Animation.Timeline> T findTimeline(Animation animation, Class<T> type) {
		if (animation == null) return null;
		for (Animation.Timeline timeline : animation.getTimelines())
			if (type.isInstance(timeline)) return type.cast(timeline);
		return null;
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertThrows(Class<? extends Throwable> type, Runnable action, String message) {
		try {
			action.run();
		} catch (Throwable error) {
			if (type.isInstance(error)) return;
			throw new AssertionError(message + ": wrong exception " + error, error);
		}
		throw new AssertionError(message + ": no exception");
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertArrayNear(float[] expected, float[] actual, String message) {
		if (expected == null || actual == null || expected.length != actual.length)
			throw new AssertionError(message + ": array lengths differ");
		for (int index = 0; index < expected.length; index++)
			assertNear(expected[index], actual[index], message + " at " + index);
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

	private SkeletonEditorTest() {}
}
