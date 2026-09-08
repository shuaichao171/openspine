package com.openspine.model;

import java.util.concurrent.atomic.AtomicInteger;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;

/** Headless regression tests for project-level document settings. */
public final class SkeletonDocumentTest {
	public static void main(String[] args) {
		startsPaused();
		startsWithCurveTimelineView();
		startsWithoutABoneSelection();
		restoresOnlyExplicitBoneSelections();
		preservesPrimaryFirstMultiBoneSelectionOrder();
		promotesCoveredPrimaryWithoutLosingTheSelection();
		constraintSelectionDoesNotReuseStaleBone();
		rejectsStaleConstraintRelations();
		sliderConstraintSelectionIncludesTargetAnimationDependencies();
		migratesEditorStateAcrossRenames();
		tracksManagedPsdSourceAcrossRenamesAndReplacement();
		migratesSlotAndConstraintEditorState();
		migratesConstraintRelationIdentitiesAcrossRenames();
		removesDeletedEditorState();
		tracksAssociatedSavePath();
		releasesOwnedSourceResources();
		releasesAtlasesRetainedForResourceUndo();
		adoptsPreparedLoadedContent();
		managesProjectFps();
		appliesAnimationMixSettings();
		scrubsPlaybackForEditing();
		suppressesSliderForItsActiveAnimation();
		disablesAllConstraintsWithoutChangingAuthoredData();
		clearsStalePoseBeforeDiscontinuousAnimationApplication();
		usesPhysicsModesForPlaybackAndDiscontinuousChanges();
		fastForwardsPhysicsJumpsDeterministically();
		boundsLongPhysicsFastForwards();
		reappliesPausedSlotAnimationWhenSkinChanges();
		updatesBonePoseOnlyWhenChanged();
		System.out.println("ALL SKELETON DOCUMENT TESTS PASSED");
	}

	private static void tracksManagedPsdSourceAcrossRenamesAndReplacement() {
		SkeletonDocument document = new SkeletonDocument();
		document.setPsdSource(new SkeletonDocument.PsdSource("layout.psd", true, 100, 50,
			java.util.List.of(new SkeletonDocument.PsdLayerMapping(3, "Head", "head-region",
				"default", "head", "head-region", true))));
		document.renameEditorSlotState("head", "face");
		document.renameEditorSkinState("default", "base");
		SkeletonDocument.PsdLayerMapping mapping = document.psdSource().layers().get(0);
		assertTrue(mapping.slotName().equals("face") && mapping.skinName().equals("base"),
			"PSD synchronization targets follow slot and skin renames");
		document.replaceContent(null, new SkeletonData(), null, "other.json", null, "other.json");
		assertTrue(document.psdSource() == null, "loading unrelated content clears stale PSD metadata");
	}

	private static void startsPaused() {
		SkeletonDocument document = new SkeletonDocument();
		assertTrue(!document.playing, "new documents start paused");

		document.setPlaying(true);
		document.replaceContent(null, new SkeletonData(), null, "loaded.json", null, "loaded.json");
		assertTrue(!document.playing, "loaded content starts paused");
	}

	private static void releasesAtlasesRetainedForResourceUndo() {
		TrackingAtlas previous = new TrackingAtlas();
		TrackingAtlas current = new TrackingAtlas();
		SkeletonDocument document = new SkeletonDocument();
		document.atlas = current;
		document.retainAtlasForHistory(previous);
		document.retainAtlasForHistory(current);
		document.retainAtlasForHistory(previous);
		document.dispose();
		assertTrue(previous.disposals == 1 && current.disposals == 1,
			"active and historical atlases are each disposed exactly once with the document");
	}

	private static void appliesAnimationMixSettings() {
		SkeletonData data = new SkeletonData();
		data.getBones().add(new BoneData(0, "root", null));
		Animation idle = new Animation("idle");
		idle.setTimelines(new Array<>(Animation.Timeline[]::new), new IntArray());
		idle.setDuration(1f);
		Animation walk = new Animation("walk");
		walk.setTimelines(new Array<>(Animation.Timeline[]::new), new IntArray());
		walk.setDuration(1f);
		data.getAnimations().add(idle);
		data.getAnimations().add(walk);
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(null, data, new Skeleton(data), "mix.json", null, "mix.json");
		document.animationState = document.newAnimationState();
		assertTrue(document.setAnimation("idle"), "source animation can be selected");
		assertTrue(document.setAnimationMixSettings(0.1f, java.util.List.of(
			new SkeletonDocument.AnimationMix("idle", "walk", 0.45f))),
			"animation mix settings can be changed");
		assertTrue(document.setAnimation("walk"), "target animation can be selected");
		assertNear(0.45f, document.animationState.getTrack(0).getMixDuration(),
			"animation switching uses the configured pair mix");
		assertNear(0.1f, document.animationState.getData().getDefaultMix(),
			"the runtime state uses the configured default mix");
		document.animationState.getTrack(0).setTrackTime(0.63f);
		assertTrue(document.setAnimationMixSettings(0.1f, java.util.List.of()),
			"a configured pair mix can be removed");
		assertTrue(document.getCurrentAnimationName().equals("walk"),
			"rebuilding mix data preserves the active animation");
		assertNear(0.63f, document.animationState.getTrack(0).getTrackTime(),
			"rebuilding mix data preserves the active track time");
		assertTrue(document.setAnimation("idle") && document.setAnimation("walk"),
			"the pair can be played after its override is removed");
		assertNear(0.1f, document.animationState.getTrack(0).getMixDuration(),
			"a removed pair uses the current default mix instead of a stale override");
		assertTrue(document.setAnimationMixSettings(0.25f, java.util.List.of()),
			"the default mix can be changed after removing an override");
		assertTrue(document.setAnimation("idle") && document.setAnimation("walk"),
			"the pair can be replayed after changing the default mix");
		assertNear(0.25f, document.animationState.getTrack(0).getMixDuration(),
			"removed pair overrides continue to follow later default mix changes");
		boolean rejected = false;
		try {
			document.setAnimationMixSettings(-1f, java.util.List.of());
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		assertTrue(rejected, "negative animation mix durations are rejected");
	}

	private static void startsWithCurveTimelineView() {
		assertTrue(SkeletonDocument.TimelineViewState.empty().curveView(),
			"empty timeline view state defaults to curve view");
	}

	private static void startsWithoutABoneSelection() {
		SkeletonDocument document = new SkeletonDocument();
		assertTrue(document.getSelectedBone() == null, "new documents start without a bone selection");

		SkeletonData data = new SkeletonData();
		data.getBones().add(new BoneData(0, "root", null));
		document.replaceContent(null, data, new Skeleton(data), "loaded.json", null, "loaded.json");
		assertTrue(document.getSelectedBone() == null, "loaded content starts without selecting root");
	}

	private static void restoresOnlyExplicitBoneSelections() {
		SkeletonData data = new SkeletonData();
		data.getBones().add(new BoneData(0, "root", null));
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(null, data, new Skeleton(data), "loaded.json", null, "loaded.json");

		document.restoreEditorSelection("BONE", "", "", "", "", "", "");
		assertTrue(document.getSelectedBone() == null, "an empty saved selection remains unselected");
		document.restoreEditorSelection("BONE", "missing", "", "", "", "", "");
		assertTrue(document.getSelectedBone() == null, "a stale saved selection does not fall back to root");
		document.restoreEditorSelection("BONE", "root", "", "", "", "", "");
		assertTrue(document.getSelectedBone() == document.skeleton.getRootBone(),
			"an explicitly saved root selection is restored");
	}

	private static void preservesPrimaryFirstMultiBoneSelectionOrder() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		BoneData sibling = new BoneData(2, "sibling", root);
		data.getBones().addAll(root, child, sibling);
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(null, data, new Skeleton(data), "loaded.json", null, "loaded.json");
		Bone childRuntime = document.skeleton.findBone("child");
		Bone siblingRuntime = document.skeleton.findBone("sibling");
		document.setSelectedBones(java.util.List.of(siblingRuntime, childRuntime));
		java.util.Iterator<Bone> selected = document.getSelectedBones().iterator();
		assertTrue(selected.hasNext() && selected.next() == siblingRuntime,
			"the first multi-selection remains the primary inspector bone");
		assertTrue(selected.hasNext() && selected.next() == childRuntime,
			"additional multi-selection bones retain hierarchy selection order");
		assertTrue(!selected.hasNext(), "multi-selection does not expose stale bones");
	}

	private static void promotesCoveredPrimaryWithoutLosingTheSelection() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		Bone root = doc.skeleton.getRootBone();
		Bone parent = doc.skeleton.findBone("a"), child = doc.skeleton.findBone("child");
		Bone sibling = doc.skeleton.findBone("b");
		doc.setSelectedBones(java.util.List.of(child, sibling, parent));
		assertTrue(doc.getSelectedBone() == parent
			&& java.util.List.copyOf(doc.getSelectedBones()).equals(java.util.List.of(parent, child, sibling)),
			"a covered primary promotes its selected ancestor while retaining every bone's relative order");
		doc.setSelectedBones(java.util.List.of(child, parent, root, sibling));
		assertTrue(doc.getSelectedBone() == root
			&& java.util.List.copyOf(doc.getSelectedBones()).equals(java.util.List.of(root, child, parent, sibling)),
			"the highest selected ancestor becomes primary even when nearer ancestors are also selected");
		doc.setSelectedBones(java.util.List.of(child, sibling, root));
		assertTrue(doc.getSelectedBone() == root && doc.getSelectedBones().size() == 3,
			"selected ancestors are detected across unselected intermediate parents");
		doc.setSelectedBones(java.util.List.of(sibling, child, parent));
		assertTrue(doc.getSelectedBone() == sibling
			&& java.util.List.copyOf(doc.getSelectedBones()).equals(java.util.List.of(sibling, child, parent)),
			"an independent primary is preserved when only secondary selections overlap");
		doc.setSelectedBones(java.util.List.of());
		assertTrue(doc.getSelectedBone() == null && doc.getSelectedBones().isEmpty(),
			"clearing a normalized selection does not retain the promoted ancestor");
	}

	private static void constraintSelectionDoesNotReuseStaleBone() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root);
		Animation driven = new Animation("driven");
		driven.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		driven.setDuration(1f);
		data.getAnimations().add(driven);
		SliderData slider = new SliderData("time-slider");
		slider.setAnimation(driven);
		data.getConstraints().add(slider);
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(null, data, new Skeleton(data), "loaded.json", null, "loaded.json");
		document.setSelectedBone(document.skeleton.getRootBone());

		document.setConstraintSelection(slider);
		assertTrue(document.getSelectionKind() == SkeletonDocument.SelectionKind.CONSTRAINT,
			"time-driven Slider becomes the active constraint selection");
		assertTrue(document.getSelectedBone() == null && document.getConstraintSelectionBones().isEmpty(),
			"a constraint without related bones clears the stale bone instead of exposing it to editing");
	}

	private static void rejectsStaleConstraintRelations() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		BoneData alternate = new BoneData(2, "alternate", root);
		data.getBones().add(root);
		data.getBones().add(child);
		data.getBones().add(alternate);
		IkConstraintData original = new IkConstraintData("aim");
		original.getBones().add(child);
		original.setTarget(root);
		data.getConstraints().add(original);
		SkeletonDocument document = new SkeletonDocument();
		document.data = data;
		document.skeleton = new Skeleton(data);

		document.setConstraintRelationSelection(original, "target:root", "root");
		assertTrue(document.getSelectedConstraint() == original
			&& document.getSelectedConstraintRelation().equals("target:root")
			&& document.getSelectedBone() == document.skeleton.findBone("root"),
			"a valid constraint relation selects its exact related bone");
		document.setConstraintRelationSelection(original, "target:missing", "root");
		assertTrue(document.getSelectedConstraint() == original
			&& document.getSelectedConstraintRelation().isEmpty(),
			"an unknown relation falls back to the constraint parent selection");

		document.setConstraintRelationSelection(original, "target:root", "root");
		IkConstraintData replacement = new IkConstraintData("aim");
		replacement.getBones().add(child);
		replacement.setTarget(alternate);
		data.getConstraints().set(0, replacement);
		document.replaceEditorConstraintState(original, replacement);
		assertTrue(document.getSelectedConstraint() == replacement
			&& document.getSelectedConstraintRelation().isEmpty(),
			"constraint replacement clears a relation that no longer exists");

		document.restoreEditorSelection("CONSTRAINT", "root", "IkConstraintData", "aim",
			"target:root", "", "", "");
		assertTrue(document.getSelectedConstraint() == replacement
			&& document.getSelectedConstraintRelation().isEmpty(),
			"Sidecar restoration rejects a stale constraint relation");
	}

	private static void sliderConstraintSelectionIncludesTargetAnimationDependencies() {
		SkeletonData data = new SkeletonData();
		BoneData driver = new BoneData(0, "driver", null);
		BoneData animated = new BoneData(1, "animated", driver);
		BoneData slotted = new BoneData(2, "slotted", driver);
		BoneData physicsBone = new BoneData(3, "physics", driver);
		BoneData ikBone = new BoneData(4, "ik", driver);
		BoneData unrelated = new BoneData(5, "unrelated", driver);
		data.getBones().addAll(driver, animated, slotted, physicsBone, ikBone, unrelated);
		data.getSlots().add(new SlotData(0, "slot", slotted));

		PhysicsConstraintData physics = new PhysicsConstraintData("spring");
		physics.setBone(physicsBone);
		IkConstraintData ik = new IkConstraintData("aim");
		ik.getBones().add(ikBone);
		ik.setTarget(driver);
		data.getConstraints().add(physics);
		data.getConstraints().add(ik);

		Animation.RGBATimeline slotTimeline = new Animation.RGBATimeline(1, 0, 0);
		Animation.DrawOrderFolderTimeline folder = new Animation.DrawOrderFolderTimeline(1, new int[] {0}, 1);
		Animation.PhysicsConstraintGravityTimeline globalPhysics =
			new Animation.PhysicsConstraintGravityTimeline(1, 0, -1);
		Animation.IkConstraintTimeline ikTimeline = new Animation.IkConstraintTimeline(1, 0, 1);
		Animation driven = new Animation("driven");
		IntArray animationBones = new IntArray();
		animationBones.add(animated.getIndex());
		Array<Animation.Timeline> timelines = Array.of(Animation.Timeline[]::new);
		timelines.add(slotTimeline);
		timelines.add(folder);
		timelines.add(globalPhysics);
		timelines.add(ikTimeline);
		driven.setTimelines(timelines, animationBones);
		driven.setDuration(1f);
		data.getAnimations().add(driven);
		SliderData slider = new SliderData("controller");
		slider.setBone(driver);
		slider.setAnimation(driven);
		data.getConstraints().add(slider);

		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(null, data, new Skeleton(data), "loaded.json", null, "loaded.json");
		document.setConstraintSelection(slider);
		assertTrue(document.getConstraintSelectionBones().size() == 5,
			"Slider selection includes its driver and every official target-animation dependency");
		for (String name : new String[] {"driver", "animated", "slotted", "physics", "ik"})
			assertTrue(document.isSelected(document.skeleton.findBone(name)),
				"Slider selection includes dependency bone " + name);
		assertTrue(!document.isSelected(document.skeleton.findBone("unrelated")),
			"Slider selection excludes bones outside the target animation dependency graph");
	}

	private static void migratesEditorStateAcrossRenames() {
		SkeletonDocument document = new SkeletonDocument();
		document.setAxisSeparationPreferred("walk", "root", "SCALE", true);
		SkeletonDocument.GraphViewState graph = new SkeletonDocument.GraphViewState(
			0f, 2f, -1f, 1f, 0, 1L, true, new float[] {0.5f}, new int[] {0});
		document.setGraphViewState("walk|example.Timeline|[1]", graph);
		document.setTimelineViewState(new SkeletonDocument.TimelineViewState("walk", true, 0,
			"bone\u001froot\u001fSCALE", 12f, 8f, true,
			new String[] {"bone\u001froot\u001fSCALE"}, new float[] {0.5f}, new int[] {0}));
		document.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(true, "walk", "root",
			true, "bone\u001froot\u001fSCALE", 0.5f, 10f, 20f, 30f, 40f, 900f, 600f));

		document.renameEditorAnimationState("walk", "run");
		assertTrue(!document.isAxisSeparationPreferred("walk", "root", "SCALE")
			&& document.isAxisSeparationPreferred("run", "root", "SCALE"),
			"animation rename migrates axis separation preferences");
		assertTrue(document.graphViewState("walk|example.Timeline|[1]") == null
			&& document.graphViewState("run|example.Timeline|[1]") == graph,
			"animation rename migrates Graph view and key-selection state");
		assertTrue(document.timelineViewState().animation().equals("run"),
			"animation rename migrates the main timeline editing context");
		assertTrue(document.graphWorkspaceState().animation().equals("run"),
			"animation rename migrates the Graph workspace animation identity");

		document.renameEditorBoneState("root", "hip");
		assertTrue(!document.isAxisSeparationPreferred("run", "root", "SCALE")
			&& document.isAxisSeparationPreferred("run", "hip", "SCALE"),
			"bone rename migrates axis separation preferences");
		assertTrue(document.timelineViewState().selectedTrackKeys()[0].equals("bone\u001fhip\u001fSCALE")
			&& document.timelineViewState().curveTrackKey().equals("bone\u001fhip\u001fSCALE"),
			"bone rename migrates main timeline primary and curve-track identities");
		assertTrue(document.graphWorkspaceState().detailTrackKey().equals("bone\u001fhip\u001fSCALE"),
			"bone rename migrates the Graph workspace detail-track identity");
	}

	private static void migratesConstraintRelationIdentitiesAcrossRenames() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		data.getBones().addAll(root, child);
		SlotData slot = new SlotData(0, "old-slot", child);
		data.getSlots().add(slot);
		Animation animation = new Animation("old-animation");
		animation.setTimelines(Array.of(Animation.Timeline[]::new), new IntArray());
		data.getAnimations().add(animation);
		SliderData slider = new SliderData("slider");
		slider.setBone(root);
		slider.setAnimation(animation);
		PathConstraintData path = new PathConstraintData("path");
		path.getBones().add(child);
		path.setSlot(slot);
		data.getConstraints().add(slider);
		data.getConstraints().add(path);

		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(null, data, new Skeleton(data), "loaded.json", null, "loaded.json");
		document.setTreeViewState(new SkeletonDocument.TreeViewState("", 0f, 0f,
			java.util.Set.of("bone:root", "slot:old-slot", "constraint-relation:Slider:slider:animation:old-animation"),
			java.util.Set.of("bone:root", "slot:old-slot", "attachment:old-slot:mesh",
				"constraint-relation:Slider:slider:source:root:time",
				"constraint-relation:Slider:slider:animation:old-animation",
				"constraint-relation:Slider:slider:animation-bone:root",
				"constraint-relation:Path:path:slot:old-slot")));

		document.setConstraintRelationSelection(slider, "animation:old-animation", "child");
		document.renameEditorAnimationState("old-animation", "new-animation");
		assertTrue(document.getSelectedConstraintRelation().equals("animation:new-animation"),
			"Slider animation relation follows an animation rename");

		document.setConstraintRelationSelection(path, "slot:old-slot", "child");
		SlotData renamedSlot = new SlotData(0, "new-slot", child);
		path.setSlot(renamedSlot);
		document.renameEditorSlotState("old-slot", "new-slot");
		assertTrue(document.getSelectedConstraintRelation().equals("slot:new-slot"),
			"Path target-slot relation follows a slot rename");

		document.setConstraintRelationSelection(slider, "source:root:time", "root");
		BoneData renamedRoot = new BoneData(0, "hip", null);
		slider.setBone(renamedRoot);
		document.renameEditorBoneState("root", "hip");
		assertTrue(document.getSelectedConstraintRelation().equals("source:hip:time"),
			"Slider source relation follows a bone rename");
		SkeletonDocument.TreeViewState migrated = document.treeViewState();
		assertTrue(migrated.expandedValues().contains("bone:hip")
			&& migrated.expandedValues().contains("slot:new-slot")
			&& migrated.expandedValues().contains(
				"constraint-relation:Slider:slider:animation:new-animation"),
			"expanded hierarchy IDs follow renamed objects");
		assertTrue(migrated.treeValues().contains("attachment:new-slot:mesh")
			&& migrated.treeValues().contains("constraint-relation:Slider:slider:source:hip:time")
			&& migrated.treeValues().contains("constraint-relation:Slider:slider:animation-bone:hip")
			&& migrated.treeValues().contains("constraint-relation:Path:path:slot:new-slot"),
			"persisted hierarchy relation IDs follow animation, slot, and bone renames");
	}

	private static void migratesSlotAndConstraintEditorState() {
		SkeletonDocument document = new SkeletonDocument();
		String[] keys = {
			"slot\u001fold-slot\u001fATTACHMENT", "sequence\u001fold-slot\u001fskin\u001fregion",
			"deform\u001fold-slot\u001fskin\u001fmesh", "ik\u001fold-ik",
			"transform\u001fold-transform", "path\u001fold-path\u001fMIX",
			"physics\u001fold-physics\u001fMASS", "slider\u001fold-slider\u001fMIX"
		};
		document.setTimelineViewState(new SkeletonDocument.TimelineViewState("walk", true, 0,
			"slider\u001fold-slider\u001fMIX", 4f, 6f, true, keys,
			new float[] {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f}, new int[8]));

		document.renameEditorSlotState("old-slot", "new-slot");
		document.replaceEditorConstraintState(new IkConstraintData("old-ik"), new IkConstraintData("new-ik"));
		document.replaceEditorConstraintState(new TransformConstraintData("old-transform"),
			new TransformConstraintData("new-transform"));
		document.replaceEditorConstraintState(new PathConstraintData("old-path"),
			new PathConstraintData("new-path"));
		document.replaceEditorConstraintState(new PhysicsConstraintData("old-physics"),
			new PhysicsConstraintData("new-physics"));
		document.replaceEditorConstraintState(new SliderData("old-slider"), new SliderData("new-slider"));

		String[] migrated = document.timelineViewState().selectedTrackKeys();
		for (int index = 0; index < migrated.length; index++)
			assertTrue(!migrated[index].contains("old-"),
				"slot and constraint rename removes old Timeline identity at row " + index);
		assertTrue(migrated[0].equals("slot\u001fnew-slot\u001fATTACHMENT")
			&& migrated[1].equals("sequence\u001fnew-slot\u001fskin\u001fregion")
			&& migrated[2].equals("deform\u001fnew-slot\u001fskin\u001fmesh"),
			"slot rename migrates slot, sequence, and deform Timeline identities");
		assertTrue(migrated[3].equals("ik\u001fnew-ik")
			&& migrated[4].equals("transform\u001fnew-transform")
			&& migrated[5].equals("path\u001fnew-path\u001fMIX")
			&& migrated[6].equals("physics\u001fnew-physics\u001fMASS")
			&& migrated[7].equals("slider\u001fnew-slider\u001fMIX")
			&& document.timelineViewState().curveTrackKey().equals("slider\u001fnew-slider\u001fMIX"),
			"all supported constraint renames migrate Timeline selection and curve identities");
	}

	private static void removesDeletedEditorState() {
		SkeletonDocument document = new SkeletonDocument();
		SkeletonDocument.GraphViewState removedGraph = new SkeletonDocument.GraphViewState(
			0f, 2f, -1f, 1f, 0, 1L);
		SkeletonDocument.GraphViewState retainedGraph = new SkeletonDocument.GraphViewState(
			1f, 3f, -2f, 2f, 1, 2L);
		document.setAxisSeparationPreferred("walk", "child", "SCALE", true);
		document.setAxisSeparationPreferred("idle", "root", "TRANSLATE", true);
		document.setGraphViewState("walk|example.Timeline|[1]", removedGraph);
		document.setGraphViewState("idle|example.Timeline|[2]", retainedGraph);
		document.setTimelineViewState(new SkeletonDocument.TimelineViewState("walk", true, 1,
			"bone\u001fchild\u001fSCALE", 12f, 8f, true,
			new String[] {"bone\u001fchild\u001fSCALE", "bone\u001froot\u001fROTATE"},
			new float[] {0.5f, 0.75f}, new int[] {0, 0}));
		document.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(true, "walk", "", false,
			"bone\u001fchild\u001fSCALE", 0.5f, 0f, 0f, 0f, 0f, 900f, 600f));
		document.setTreeViewState(new SkeletonDocument.TreeViewState("", 0f, 0f,
			java.util.Set.of("bone:child"), java.util.Set.of("bone:child",
				"constraint-relation:Slider:slider:source:child:time", "bone:root")));

		document.removeEditorBoneState("child");
		assertTrue(!document.isAxisSeparationPreferred("walk", "child", "SCALE")
			&& document.isAxisSeparationPreferred("idle", "root", "TRANSLATE"),
			"bone deletion removes only matching axis separation preferences");
		assertTrue(document.timelineViewState().curveTrackKey().isEmpty()
			&& document.timelineViewState().selectedTrackKeys().length == 1
			&& document.timelineViewState().selectedTrackKeys()[0].equals("bone\u001froot\u001fROTATE")
			&& document.timelineViewState().selectedTimes()[0] == 0.75f,
			"bone deletion removes only matching Timeline tracks and their selection metadata");
		assertTrue(document.graphWorkspaceState().detailTrackKey().isEmpty()
			&& !document.treeViewState().treeValues().contains("bone:child")
			&& document.treeViewState().treeValues().contains("bone:root"),
			"bone deletion clears only its stale Graph detail and hierarchy identities");

		document.setTimelineViewState(new SkeletonDocument.TimelineViewState("walk", true, 0,
			"sequence\u001ftail-slot\u001fskin\u001fregion", 12f, 8f, true,
			new String[] {"slot\u001ftail-slot\u001fATTACHMENT", "deform\u001ftail-slot\u001fskin\u001fmesh",
				"ik\u001fdead-ik", "path\u001fkeep-path\u001fMIX", "bone\u001froot\u001fROTATE"},
			new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f}, new int[5]));
		document.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(true, "walk", "", false,
			"sequence\u001ftail-slot\u001fskin\u001fregion", 0.5f, 0f, 0f, 0f, 0f, 900f, 600f));
		document.setTreeViewState(new SkeletonDocument.TreeViewState("", 0f, 0f,
			java.util.Set.of("slot:tail-slot", "constraint:IK:dead-ik"),
			java.util.Set.of("slot:tail-slot", "attachment:tail-slot:mesh", "constraint:IK:dead-ik",
				"constraint-relation:IK:dead-ik:target:root", "bone:root")));
		document.removeEditorSlotState("tail-slot");
		document.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(true, "walk", "", false,
			"ik\u001fdead-ik", 0.5f, 0f, 0f, 0f, 0f, 900f, 600f));
		document.removeEditorConstraintState(new IkConstraintData("dead-ik"));
		assertTrue(document.timelineViewState().curveTrackKey().isEmpty()
			&& java.util.Arrays.equals(document.timelineViewState().selectedTrackKeys(),
				new String[] {"path\u001fkeep-path\u001fMIX", "bone\u001froot\u001fROTATE"})
			&& java.util.Arrays.equals(document.timelineViewState().selectedTimes(), new float[] {0.4f, 0.5f}),
			"slot and constraint deletion filter only matching Timeline selection metadata");
		assertTrue(document.graphWorkspaceState().detailTrackKey().isEmpty()
			&& document.treeViewState().treeValues().equals(java.util.Set.of("bone:root")),
			"slot and constraint deletion remove stale Graph detail and hierarchy identities");

		document.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(true, "walk", "", false,
			"", 0.5f, 0f, 0f, 0f, 0f, 900f, 600f));
		document.setTreeViewState(new SkeletonDocument.TreeViewState("", 0f, 0f, java.util.Set.of(),
			java.util.Set.of("constraint-relation:Slider:slider:animation:walk", "bone:root")));
		document.removeEditorAnimationState("walk");
		assertTrue(document.graphViewState("walk|example.Timeline|[1]") == null
			&& document.graphViewState("idle|example.Timeline|[2]") == retainedGraph,
			"animation deletion removes only matching Graph state");
		assertTrue(document.timelineViewState().animation().isEmpty()
			&& document.timelineViewState().curveTrackKey().isEmpty()
			&& document.timelineViewState().selectedTrackKeys().length == 0,
			"animation deletion clears its Timeline editing context");
		assertTrue(!document.graphWorkspaceState().open()
			&& document.treeViewState().treeValues().equals(java.util.Set.of("bone:root")),
			"animation deletion closes its Graph workspace and removes stale hierarchy relations");
	}

	private static void releasesOwnedSourceResources() {
		AtomicInteger replacedCleanup = new AtomicInteger();
		SkeletonDocument document = new SkeletonDocument();
		document.ownSourceResources(replacedCleanup::incrementAndGet);
		document.replaceContent(null, new SkeletonData(), null, "replacement.json", null, "replacement.json");
		assertTrue(replacedCleanup.get() == 1, "replacing a document releases its previous source resources");

		AtomicInteger disposedCleanup = new AtomicInteger();
		document.ownSourceResources(disposedCleanup::incrementAndGet);
		document.dispose();
		document.dispose();
		assertTrue(disposedCleanup.get() == 1, "disposing a document releases source resources exactly once");
	}

	private static void updatesBonePoseOnlyWhenChanged() {
		SkeletonData data = new SkeletonData();
		BoneData rootData = new BoneData(0, "root", null);
		data.getBones().add(rootData);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		var bone = doc.skeleton.getRootBone();
		var pose = bone.getPose();
		doc.setPlaying(true);
		long revision = doc.revision;

		assertTrue(!doc.updateBonePose(bone, pose.getX(), pose.getY(), pose.getRotation(),
			pose.getScaleX(), pose.getScaleY(), pose.getShearX(), pose.getShearY()),
			"unchanged bone pose is a no-op");
		assertTrue(doc.playing && !doc.poseMode && doc.revision == revision,
			"unchanged pose does not pause playback or increment revision");

		assertTrue(doc.updateBonePose(bone, 6000f, -6000f, 4000f, 25f, -25f, 720f, -720f),
			"changed finite bone pose is accepted");
		assertNear(6000f, pose.getX(), "edited bone x");
		assertNear(-6000f, pose.getY(), "edited bone y");
		assertNear(4000f, pose.getRotation(), "edited bone rotation");
		assertNear(25f, pose.getScaleX(), "edited bone scale x");
		assertNear(-25f, pose.getScaleY(), "edited bone scale y");
		assertNear(720f, pose.getShearX(), "edited bone shear x");
		assertNear(-720f, pose.getShearY(), "edited bone shear y");
		assertTrue(!doc.playing && doc.poseMode && doc.revision == revision + 1,
			"changed pose pauses playback and increments revision once");

		revision = doc.revision;
		assertTrue(!doc.updateBonePose(bone, Float.NaN, 0f, 0f, 1f, 1f, 0f, 0f),
			"non-finite bone pose is rejected");
		SkeletonData foreignData = new SkeletonData();
		foreignData.getBones().add(new BoneData(0, "foreign", null));
		var foreignBone = new Skeleton(foreignData).getRootBone();
		assertTrue(!doc.updateBonePose(foreignBone, 1f, 2f, 3f, 1f, 1f, 0f, 0f),
			"bone outside the current skeleton is rejected");
		assertTrue(doc.revision == revision, "rejected bone pose edits do not increment revision");
		assertNear(6000f, pose.getX(), "rejected bone pose edit preserves data");
	}

	private static void reappliesPausedSlotAnimationWhenSkinChanges() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		SlotData slot = new SlotData(0, "slot", root);
		slot.setAttachmentName("setup");
		data.getSlots().add(slot);

		Skin first = new Skin("first");
		RegionAttachment firstSetup = new RegionAttachment("first-setup", new Sequence(1, false));
		RegionAttachment firstAnimated = new RegionAttachment("first-animated", new Sequence(1, false));
		first.setAttachment(0, "setup", firstSetup);
		first.setAttachment(0, "animated", firstAnimated);
		Skin second = new Skin("second");
		RegionAttachment secondSetup = new RegionAttachment("second-setup", new Sequence(1, false));
		RegionAttachment secondAnimated = new RegionAttachment("second-animated", new Sequence(1, false));
		second.setAttachment(0, "setup", secondSetup);
		second.setAttachment(0, "animated", secondAnimated);
		data.getSkins().add(first);
		data.getSkins().add(second);
		data.setDefaultSkin(first);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.skeleton.setSkin(first);
		doc.skeleton.setupPoseSlots();
		doc.animationState = new AnimationState(new AnimationStateData(data));
		AnimationEditor editor = new AnimationEditor(doc);
		Animation animation = editor.addAnimation("skin-switch");
		assertTrue(animation != null, "attachment animation can be created");
		editor.addAttachmentKeyframe(animation, 0, 0f, "setup");
		editor.addAttachmentKeyframe(animation, 0, 0.5f, "animated");
		editor.addAttachmentKeyframe(animation, 0, 1f, "animated");
		doc.dirty = false;

		assertTrue(doc.setAnimation("skin-switch"), "attachment animation can be selected");
		assertTrue(doc.scrubTo(0.75f), "attachment animation can be paused between keys");
		assertTrue(doc.skeleton.findSlot("slot").getPose().getAttachment() == firstAnimated,
			"first skin uses the animated attachment before switching");
		assertTrue(doc.setSkin("second"), "known preview skin can be selected");
		assertTrue(doc.skeleton.findSlot("slot").getPose().getAttachment() == secondAnimated,
			"skin switch reapplies the paused attachment timeline");
		assertTrue(!doc.playing && Math.abs(doc.getCurrentTime() - 0.75f) < 0.0001f,
			"skin switch preserves paused playback time");
		assertTrue(!doc.dirty, "preview skin switching does not dirty the document");
		assertTrue(!doc.setSkin("missing") && doc.skeleton.getSkin() == second,
			"unknown skin is rejected without changing the preview");
	}

	private static void adoptsPreparedLoadedContent() {
		SkeletonData oldData = new SkeletonData();
		SkeletonDocument target = new SkeletonDocument();
		target.data = oldData;
		long revision = target.revision;

		SkeletonData loadedData = new SkeletonData();
		BoneData root = new BoneData(0, "loaded-root", null);
		loadedData.getBones().add(root);
		Skeleton loadedSkeleton = new Skeleton(loadedData);
		SkeletonDocument candidate = new SkeletonDocument();
		candidate.replaceContent(null, loadedData, loadedSkeleton, "loaded.json", "loaded.atlas", "loaded.json");
		candidate.setSelectedBone(loadedSkeleton.getRootBone());
		AtomicInteger sourceCleanup = new AtomicInteger();
		candidate.ownSourceResources(sourceCleanup::incrementAndGet);
		candidate.animationState = new AnimationState(new AnimationStateData(loadedData));
		candidate.playing = false;
		candidate.playbackSpeed = 1.5f;
		candidate.looping = false;
		candidate.maxTime = 2f;
		candidate.premultipliedAlpha = true;

		target.adoptLoadedContentFrom(candidate);
		assertTrue(target.data == loadedData && target.skeleton == loadedSkeleton,
			"prepared skeleton content is adopted");
		assertTrue(target.animationState != null && target.getSelectedBone() == loadedSkeleton.getRootBone(),
			"runtime state and selection are adopted");
		assertTrue("loaded.json".equals(target.filePath) && "loaded.json".equals(target.savePath),
			"source and save paths are adopted");
		assertTrue(!target.playing && target.playbackSpeed == 1.5f && !target.looping && target.maxTime == 2f,
			"playback state is adopted");
		assertTrue(target.premultipliedAlpha, "atlas alpha mode is adopted with the loaded content");
		assertTrue(target.revision == revision + 1, "adoption increments the target revision once");
		assertTrue(candidate.data == null && candidate.skeleton == null && candidate.animationState == null,
			"source ownership is detached");
		assertTrue(!candidate.premultipliedAlpha, "detached source clears its atlas alpha mode");
		candidate.dispose();
		assertTrue(target.data == loadedData && target.skeleton == loadedSkeleton,
			"disposing the detached source does not affect adopted content");
		assertTrue(sourceCleanup.get() == 0, "detached source resources remain owned by the adopted document");

		SkeletonDocument incomplete = new SkeletonDocument();
		try {
			target.adoptLoadedContentFrom(incomplete);
			throw new AssertionError("incomplete candidate should be rejected");
		} catch (IllegalArgumentException expected) {
			assertTrue(target.data == loadedData, "rejected candidate leaves current content intact");
		}
		target.dispose();
		assertTrue(sourceCleanup.get() == 1, "adopted source resources are released with the target document");
	}

	private static void tracksAssociatedSavePath() {
		SkeletonDocument doc = new SkeletonDocument();
		doc.replaceContent(null, new SkeletonData(), null, "hero.json", "hero.atlas", "hero.json");
		assertTrue("hero.json".equals(doc.savePath), "JSON load associates its source path");
		doc.replaceContent(null, new SkeletonData(), null, "hero.skel", "hero.atlas", "hero.skel");
		assertTrue("hero.skel".equals(doc.savePath), "binary load associates its source path");
		doc.replaceContent(null, new SkeletonData(), null, "builtin:example", "builtin-example", null);
		assertTrue(doc.savePath == null, "built-in load has no Save association");
		doc.savePath = "saved.json";
		doc.dispose();
		assertTrue(doc.savePath == null, "dispose clears the Save association");
	}

	private static void managesProjectFps() {
		SkeletonDocument empty = new SkeletonDocument();
		assertTrue(!empty.setProjectFps(24f), "FPS change requires document data");

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = new SkeletonData();
		long initialRevision = doc.revision;
		assertTrue(!doc.setProjectFps(Float.NaN), "non-finite FPS is rejected");
		assertTrue(!doc.setProjectFps(0f), "zero FPS is rejected");
		assertTrue(!doc.setProjectFps(SkeletonDocument.MAX_PROJECT_FPS + 1f), "excessive FPS is rejected");
		assertTrue(!doc.dirty && doc.revision == initialRevision, "invalid FPS does not modify the document");

		assertTrue(doc.setProjectFps(23.976f), "fractional FPS is accepted");
		assertNear(23.976f, doc.data.getFps(), "project FPS value");
		assertTrue(doc.dirty && doc.revision == initialRevision + 1, "FPS change marks the document dirty once");

		doc.markSaved();
		long savedRevision = doc.revision;
		assertTrue(doc.setProjectFps(23.976f), "unchanged valid FPS is accepted");
		assertTrue(!doc.dirty && doc.revision == savedRevision, "unchanged FPS does not dirty the document");
	}

	private static void scrubsPlaybackForEditing() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setRotation(10f);
		data.getBones().add(root);
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(2, 0, 0);
		rotate.setFrame(0, 0f, 0f);
		rotate.setFrame(1, 1f, 40f);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(rotate);
		IntArray bones = new IntArray();
		bones.add(0);
		Animation animation = new Animation("scrub");
		animation.setTimelines(timelines, bones);
		animation.setDuration(1f);
		data.getAnimations().add(animation);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.animationState = new AnimationState(new AnimationStateData(data));
		doc.skeleton.getRootBone().getPose().setRotation(99f);
		assertTrue(doc.setAnimation("scrub"), "scrub animation can be selected");
		assertTrue(!doc.playing, "animation selection preserves the paused state");
		assertNear(10f, doc.skeleton.getRootBone().getPose().getRotation(),
			"animation selection immediately applies its first frame");
		doc.setPoseMode(true);
		doc.playing = true;
		assertTrue(doc.scrubTo(0.5f), "finite editor time can be scrubbed");
		assertTrue(!doc.playing && !doc.poseMode, "scrubbing pauses playback and leaves pose mode");
		assertNear(0.5f, doc.getCurrentTime(), "scrubbed animation time");
		assertNear(30f, doc.skeleton.getRootBone().getPose().getRotation(), "scrubbed animation pose");

		doc.setLooping(true);
		assertTrue(doc.scrubTo(1f), "looping animation can be scrubbed to its exact end");
		assertNear(1f, doc.getCurrentTime(), "paused looping animation preserves its end time");
		assertNear(50f, doc.skeleton.getRootBone().getPose().getRotation(),
			"paused looping animation displays its final pose instead of wrapping to its first frame");
		doc.setLooping(false);
		doc.setLooping(true);
		assertNear(1f, doc.getCurrentTime(), "changing loop mode while paused preserves the exact end time");
		doc.setPlaying(true);
		assertNear(1f, doc.getCurrentTime(),
			"enabling looping playback keeps the exact end visible until a real playback tick");
		doc.setPlaying(false);
		doc.setLooping(false);
		doc.setPlaying(true);
		doc.setLooping(true);
		assertNear(1f, doc.getCurrentTime(),
			"enabling loop mode on a playing end frame also preserves the exact boundary");
		doc.playbackSpeed = 1.5f;
		doc.applyAnimation(0.25f);
		assertNear(0.375f, doc.getCurrentTime(), "resuming from the looping end wraps and advances playback");
		assertNear(0.375f, doc.skeleton.getTime(), "playback advances the skeleton clock used by physics constraints");
		assertNear(25f, doc.skeleton.getRootBone().getPose().getRotation(),
			"resumed looping animation applies the wrapped pose");
		doc.playbackSpeed = 1f;
		doc.applyAnimation(2f);
		doc.setPlaying(false);
		assertNear(0.375f, doc.getCurrentTime(), "pausing after multiple loops preserves the current cycle time");
		assertNear(2.375f, doc.skeleton.getTime(), "the skeleton clock accumulates unwrapped playback time");
		assertNear(25f, doc.skeleton.getRootBone().getPose().getRotation(),
			"pausing after multiple loops preserves the displayed pose");
		float pausedSkeletonTime = doc.skeleton.getTime();
		doc.applyAnimation(0.5f);
		assertNear(pausedSkeletonTime, doc.skeleton.getTime(), "paused playback does not advance physics time");
		doc.setPlaying(true);
		doc.applyAnimation(Float.NaN);
		assertNear(pausedSkeletonTime, doc.skeleton.getTime(), "invalid playback deltas do not corrupt physics time");

		float timeBeforeRejectedScrub = doc.getCurrentTime();
		assertTrue(!doc.scrubTo(Float.NaN), "non-finite scrub time is rejected");
		assertTrue(doc.playing, "rejected scrub leaves playback state unchanged");
		assertNear(timeBeforeRejectedScrub, doc.getCurrentTime(), "rejected scrub leaves time unchanged");
		assertTrue(!new SkeletonDocument().scrubTo(0.5f), "scrubbing requires an active animation");
	}

	private static void suppressesSliderForItsActiveAnimation() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(1, 0, 0);
		rotate.setFrame(0, 0f, 90f);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(rotate);
		IntArray bones = new IntArray();
		bones.add(0);
		Animation animation = new Animation("active-slider-source");
		animation.setTimelines(timelines, bones);
		animation.setDuration(1f);
		data.getAnimations().add(animation);
		SliderData slider = new SliderData("self-reference");
		slider.setAnimation(animation);
		slider.setAdditive(true);
		slider.getSetupPose().setTime(0f);
		slider.getSetupPose().setMix(1f);
		data.getConstraints().add(slider);

		SkeletonDocument document = new SkeletonDocument();
		document.data = data;
		document.skeleton = new Skeleton(data);
		document.animationState = new AnimationState(new AnimationStateData(data));
		assertTrue(document.setAnimation(animation.getName()), "self-referenced Slider animation can be selected");
		assertNear(90f, document.skeleton.getRootBone().getPose().getRotation(),
			"the active animation is applied once and its self-referenced Slider is suppressed");
	}

	private static void disablesAllConstraintsWithoutChangingAuthoredData() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setScale(1f, 1f);
		BoneData constrained = new BoneData(1, "constrained", root);
		constrained.setLength(10f);
		constrained.getSetupPose().setScale(1f, 1f);
		constrained.getSetupPose().setInherit(BoneData.Inherit.normal);
		BoneData target = new BoneData(2, "target", root);
		target.getSetupPose().setPosition(0f, 20f);
		target.getSetupPose().setScale(1f, 1f);
		target.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().addAll(root, constrained, target);
		IkConstraintData ik = new IkConstraintData("aim");
		ik.getBones().add(constrained);
		ik.setTarget(target);
		ik.getSetupPose().setMix(1f);
		data.getConstraints().add(ik);

		SkeletonDocument document = new SkeletonDocument();
		document.data = data;
		document.skeleton = new Skeleton(data);
		document.updateWorldTransform(Physics.pose);
		Object[] updateCache = document.skeleton.getUpdateCache().toArray();
		assertNear(90f, document.skeleton.findBone("constrained").getAppliedPose().getWorldRotationX(),
			"enabled IK aims the constrained bone at its target");

		assertTrue(document.setConstraintsEnabled(false), "all constraints can be disabled for preview");
		assertTrue(!document.areConstraintsEnabled(), "the disabled constraint state is exposed to the viewport");
		assertNear(0f, document.skeleton.findBone("constrained").getAppliedPose().getWorldRotationX(),
			"disabled constraints leave the unconstrained setup transform visible");
		Object[] disabledCache = document.skeleton.getUpdateCache().toArray();
		assertTrue(java.util.Arrays.equals(updateCache, disabledCache),
			"temporarily bypassing constraints restores the runtime update cache exactly");
		assertNear(1f, ik.getSetupPose().getMix(), "preview disabling does not rewrite authored constraint mixes");
		assertTrue(!document.dirty, "the temporary constraint preview switch does not dirty the project");
		assertTrue(!document.setConstraintsEnabled(false), "repeating the same constraint state is a no-op");

		assertTrue(document.setConstraintsEnabled(true), "all constraints can be enabled again");
		assertNear(90f, document.skeleton.findBone("constrained").getAppliedPose().getWorldRotationX(),
			"reenabling constraints restores their evaluated pose");
	}

	private static void usesPhysicsModesForPlaybackAndDiscontinuousChanges() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		Skin first = new Skin("first");
		Skin second = new Skin("second");
		data.getSkins().add(first);
		data.getSkins().add(second);
		data.setDefaultSkin(first);
		Animation animation = new Animation("physics-modes");
		animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		animation.setDuration(1f);
		data.getAnimations().add(animation);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		TrackingSkeleton skeleton = new TrackingSkeleton(data);
		skeleton.setSkin(first);
		doc.skeleton = skeleton;
		doc.animationState = new AnimationState(new AnimationStateData(data));

		assertTrue(doc.setAnimation(animation.getName()), "physics mode test animation can be selected");
		assertTrue(skeleton.lastPhysics == Physics.reset,
			"selecting an animation resets stale physics from the previous preview");
		doc.refreshCurrentAnimationPose();
		assertTrue(skeleton.lastPhysics == Physics.pose,
			"reapplying the same editor time preserves rather than advances the physics pose");
		doc.seek(0.5f);
		assertTrue(skeleton.lastPhysics == Physics.reset,
			"jumping to a different editor time resets non-deterministic physics state");
		doc.resetToSetupPose();
		assertTrue(skeleton.lastPhysics == Physics.reset, "setup pose reset also resets physics offsets");
		doc.setPoseMode(true);
		doc.setPoseMode(false);
		assertTrue(skeleton.lastPhysics == Physics.reset,
			"leaving a freely edited pose resets physics before applying animation");
		assertTrue(doc.setSkin(second.getName()), "physics mode test skin can be selected");
		assertTrue(skeleton.lastPhysics == Physics.reset,
			"changing the active skin resets constraints that may have been activated or removed");
		doc.setPlaying(true);
		doc.applyAnimation(0.1f);
		assertTrue(skeleton.lastPhysics == Physics.update,
			"ordinary elapsed playback updates and applies dynamic physics");
		doc.clearAnimation();
		assertTrue(skeleton.lastPhysics == Physics.reset,
			"clearing animation returns to setup pose without stale physics offsets");
		var pose = skeleton.getRootBone().getPose();
		assertTrue(doc.updateBonePose(skeleton.getRootBone(), pose.getX() + 1f, pose.getY(), pose.getRotation(),
			pose.getScaleX(), pose.getScaleY(), pose.getShearX(), pose.getShearY()),
			"physics mode test can edit a runtime pose");
		assertTrue(skeleton.lastPhysics == Physics.pose,
			"editing a paused pose applies current physics without a zero-time update step");
	}

	private static void fastForwardsPhysicsJumpsDeterministically() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		PhysicsConstraintData physics = new PhysicsConstraintData("spring");
		physics.setBone(root);
		physics.setStep(1f / 120f);
		data.getConstraints().add(physics);
		Animation animation = new Animation("physics-seek");
		animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		animation.setDuration(1f);
		data.getAnimations().add(animation);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		TrackingSkeleton skeleton = new TrackingSkeleton(data);
		doc.skeleton = skeleton;
		doc.animationState = new AnimationState(new AnimationStateData(data));
		assertTrue(doc.setAnimation(animation.getName()), "physics seek animation can be selected");
		int before = skeleton.updateCalls;
		doc.seek(0.5f);
		assertTrue(!doc.isPhysicsDeterministic() && skeleton.updateCalls == before
			&& skeleton.lastPhysics == Physics.reset,
			"non-deterministic discontinuous seeks reset Physics without replaying from frame zero");
		assertTrue(!doc.isPhysicsSeekPending(),
			"non-deterministic discontinuous seeks do not leave a fixed-step replay pending");

		doc.seek(0f);
		assertTrue(doc.setPhysicsDeterministic(true), "deterministic Physics can be enabled");
		before = skeleton.updateCalls;
		doc.seek(0.5f);
		assertTrue(skeleton.lastPhysics == Physics.update && skeleton.updateCalls - before >= 59,
			"deterministic discontinuous seek fast-forwards at the highest Physics update rate");
		assertNear(0.5f, skeleton.getTime(), "fast-forward leaves skeleton clock at the requested time");
		before = skeleton.updateCalls;
		int resetsBefore = skeleton.resetCalls;
		doc.seek(0.503f);
		assertTrue(skeleton.lastPhysics == Physics.update && skeleton.updateCalls == before + 1,
			"a deterministic sub-step remainder advances Physics instead of only posing it");
		assertNear(0.503f, skeleton.getTime(), "sub-step fast-forward leaves the exact requested clock time");
		doc.seek(0.503f);
		assertTrue(skeleton.resetCalls == resetsBefore + 1,
			"a consumed sub-step cursor is rebuilt instead of replaying the same remainder twice");

		assertTrue(doc.setPhysicsSimulationEnabled(false), "Physics Simulate can be disabled");
		assertTrue(!doc.isPhysicsSimulationEnabled() && skeleton.lastPhysics == Physics.reset,
			"disabling simulation clears accumulated Physics state");
		before = skeleton.updateCalls;
		doc.seek(0.75f);
		assertTrue(skeleton.updateCalls == before && skeleton.lastPhysics == Physics.reset,
			"seeking with Simulate disabled does not advance dynamic Physics");

		assertTrue(doc.setPhysicsSimulationEnabled(true), "Physics Simulate can be enabled");
		before = skeleton.updateCalls;
		doc.setPlaying(true);
		doc.applyAnimation(0.1f);
		assertTrue(doc.isPhysicsDeterministic() && skeleton.updateCalls - before > 1,
			"deterministic playback advances the canonical fixed-step Physics state");
	}

	private static void boundsLongPhysicsFastForwards() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		PhysicsConstraintData physics = new PhysicsConstraintData("high-rate-spring");
		physics.setBone(root);
		physics.setStep(0.0001f);
		data.getConstraints().add(physics);
		Animation animation = new Animation("long-physics-seek");
		animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		animation.setDuration(2f);
		data.getAnimations().add(animation);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		TrackingSkeleton skeleton = new TrackingSkeleton(data);
		doc.skeleton = skeleton;
		doc.animationState = new AnimationState(new AnimationStateData(data));
		assertTrue(doc.setAnimation(animation.getName()), "long Physics seek animation can be selected");
		assertTrue(doc.setPhysicsDeterministic(true), "long Physics seek enables deterministic replay");
		doc.seek(2f);
		assertTrue(doc.isPhysicsSeekPending(), "a long high-rate Physics seek is split across UI ticks");
		assertTrue(skeleton.updateCalls > 0 && skeleton.updateCalls < 20_000,
			"the first seek tick is bounded instead of synchronously running every Physics step");
		int ticks = 0;
		while (doc.isPhysicsSeekPending() && ticks++ < 100) doc.applyAnimation(0f);
		assertTrue(!doc.isPhysicsSeekPending(), "paused animation ticks eventually finish the Physics seek");
		assertTrue(skeleton.updateCalls >= 19_990,
			"bounded fast-forward still executes the canonical fixed-step simulation");
		assertNear(2f, skeleton.getTime(), "bounded fast-forward leaves the exact requested clock time");
	}

	private static void clearsStalePoseBeforeDiscontinuousAnimationApplication() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setRotation(10f);
		BoneData child = new BoneData(1, "child", root);
		child.getSetupPose().setRotation(20f);
		child.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root);
		data.getBones().add(child);
		Animation.RotateTimeline rotate = new Animation.RotateTimeline(2, 0, 0);
		rotate.setFrame(0, 0f, 0f);
		rotate.setFrame(1, 1f, 40f);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(rotate);
		IntArray bones = new IntArray();
		bones.add(0);
		Animation animation = new Animation("partial");
		animation.setTimelines(timelines, bones);
		animation.setDuration(1f);
		data.getAnimations().add(animation);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.animationState = new AnimationState(new AnimationStateData(data));
		var childPose = doc.skeleton.findBone("child").getPose();

		childPose.setRotation(80f);
		assertTrue(doc.setAnimation(animation.getName()), "partial animation can be selected");
		assertNear(20f, childPose.getRotation(),
			"animation selection clears stale values for properties without a timeline");

		childPose.setRotation(70f);
		doc.setPoseMode(true);
		doc.setPoseMode(false);
		assertNear(20f, childPose.getRotation(),
			"leaving free pose mode restores unkeyed properties to setup");

		childPose.setRotation(60f);
		doc.seek(0.5f);
		assertNear(20f, childPose.getRotation(), "seeking clears stale unkeyed properties");

		childPose.setRotation(50f);
		assertTrue(doc.refreshCurrentAnimationPose(), "active animation pose can be refreshed");
		assertNear(20f, childPose.getRotation(),
			"same-time refresh clears values left by removed or replaced timelines");
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static final class TrackingSkeleton extends Skeleton {
		Physics lastPhysics;
		int updateCalls;
		int resetCalls;

		TrackingSkeleton(SkeletonData data) {
			super(data);
		}

		@Override public void updateWorldTransform(Physics physics) {
			lastPhysics = physics;
			if (physics == Physics.update) updateCalls++;
			if (physics == Physics.reset) resetCalls++;
			super.updateWorldTransform(physics);
		}
	}

	private static final class TrackingAtlas extends TextureAtlas {
		int disposals;
		@Override public void dispose() { disposals++; }
	}

	private SkeletonDocumentTest() {}
}
