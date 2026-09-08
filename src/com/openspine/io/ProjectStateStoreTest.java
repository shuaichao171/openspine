package com.openspine.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Headless regression tests for read-only .spine companion editor state. */
public final class ProjectStateStoreTest {
	public static void main(String[] args) throws Exception {
		Path directory = Files.createTempDirectory("openspine-project-state-");
		try {
			Path source = directory.resolve("hero.spine");
			Files.writeString(source, "official project bytes", StandardCharsets.UTF_8);
			FileTime sourceTime = Files.getLastModifiedTime(source);
			SkeletonDocument active = new SkeletonDocument();
			SkeletonProject project = new SkeletonProject(active);
			project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("hero", document(source))));

			active.setAnimation("walk");
			active.setSkin("alt");
			active.seek(0.75f);
			active.setPlaying(false);
			active.setLooping(false);
			active.playbackSpeed = 1.65f;
			active.setAnimationMixSettings(0.2f, List.of(
				new SkeletonDocument.AnimationMix("idle", "walk", 0.45f),
				new SkeletonDocument.AnimationMix("walk", "idle", 0.15f)));
			active.setPhysicsDeterministic(true);
			active.setConstraintRelationSelection(active.data.findConstraint("aim", IkConstraintData.class),
				"target:root", "root");
			active.setAxisSeparationPreferred("walk", "child", "TRANSLATE", true);
			active.setAxisCombineState(new SkeletonDocument.AxisCombineState("walk", "child", "SCALE", true,
				new float[] {0f, 1f, 0.75f, 2f}, new float[] {0f, 0f},
				new float[] {0f, 1f, 1.25f, 0.5f}, new float[] {0f, 0f},
				new float[] {0f, 1f, 1f, 0.75f, 2f, 0.8f}, new float[] {0f, 0f}));
			active.setGraphViewState("walk|Translate|1", new SkeletonDocument.GraphViewState(
				0.2f, 1.8f, -3f, 5f, 1, 3L, true,
				new float[] {0.25f, 1.5f}, new int[] {0, 1}, false, true, false, true, true, 2));
			active.setGraphViewState("walk|Rotate|1", new SkeletonDocument.GraphViewState(
				0f, 2f, -20f, 20f, 0, 1L, true, new float[0], new int[0]));
			String weightKey = SkeletonDocument.meshWeightStateKey("default", "body", "body-region");
			active.setMeshWeightLocks(weightKey, java.util.Set.of(
				new SkeletonDocument.MeshWeightLock(3, 0), new SkeletonDocument.MeshWeightLock(3, 1)));
			active.setTreeViewState(new SkeletonDocument.TreeViewState("aim", 18f, 42f,
				java.util.Set.of("group:constraints", "constraint:IK:aim"),
				java.util.Set.of("group:constraints", "constraint:IK:aim",
					"constraint-relation:IK:aim:Target · root")));
			active.setTimelineViewState(new SkeletonDocument.TimelineViewState("walk", true, 1,
				"bone\u001fchild\u001fTRANSLATE", 135f, 57f, true,
				new String[] {"bone\u001fchild\u001fTRANSLATE", "event"},
				new float[] {0.25f, 1.5f}, new int[] {0, 2}));
			active.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(true, "walk", "child",
				true, "bone\u001fchild\u001fTRANSLATE", 0.47f, 83f, 126f,
				44f, 52f, 1180f, 690f));
			Path psd = directory.resolve("hero-layout.psd");
			Files.write(psd, new byte[] {8, 66, 80, 83});
			active.setPsdSource(new SkeletonDocument.PsdSource(psd.toString(), true, 2048, 1024, List.of(
				new SkeletonDocument.PsdLayerMapping(4, "Body", "body-region", "default",
					"body", "body-region", true))));
			ProjectStateStore.write(source, project, false);

			Path sidecar = ProjectStateStore.sidecarPath(source);
			String stableWrite = Files.readString(sidecar, StandardCharsets.UTF_8);
			ProjectStateStore.write(source, project, false);
			assertEquals(stableWrite, Files.readString(sidecar, StandardCharsets.UTF_8),
				"writing unchanged editor state produces deterministic Sidecar bytes");
			assertTrue(Files.isRegularFile(sidecar), "the companion state is written beside the source");
			assertEquals("hero.spine.openspine.json", sidecar.getFileName().toString(),
				"the source extension remains visible in the sidecar name");
			assertEquals("official project bytes", Files.readString(source, StandardCharsets.UTF_8),
				"writing editor state never modifies the official project");
			com.badlogic.gdx.utils.JsonValue unsafePortable = new com.badlogic.gdx.utils.JsonReader().parse(stableWrite);
			unsafePortable.get("skeletons").get(0).get("psdSource").get("path").set("../outside.psd");
			Files.writeString(sidecar, unsafePortable.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json),
				StandardCharsets.UTF_8);
			active.restorePsdSource(null);
			assertEquals(ProjectStateStore.LoadResult.RESTORED,
				ProjectStateStore.loadPortable(source, project, false).result(),
				"an unsafe optional packaged PSD path does not reject the remaining editor state");
			assertTrue(active.psdSource() == null, "packaged PSD paths cannot escape their extracted directory");
			Files.writeString(sidecar, stableWrite, StandardCharsets.UTF_8);
			assertEquals(ProjectStateStore.LoadResult.RESTORED, ProjectStateStore.load(source, project),
				"ordinary local state restores its absolute PSD source after portable-path validation");

			Path autosaveState = directory.resolve("autosave-editor-state.json");
			Files.writeString(autosaveState, ProjectStateStore.snapshotEditorState(project, false),
				StandardCharsets.UTF_8);
			active.restoreAnimationMixSettings(0f, List.of());
			active.restoreGraphViewStates(java.util.Map.of());
			active.setTimelineViewState(SkeletonDocument.TimelineViewState.empty());
			active.restorePsdSource(null);
			ProjectStateStore.LoadOutcome autosaveLoad = ProjectStateStore.restoreEditorState(
				autosaveState, project, true);
			assertEquals(ProjectStateStore.LoadResult.RESTORED, autosaveLoad.result(),
				"source-independent autosave editor state is restored");
			assertTrue(!autosaveLoad.showAllSkeletons(), "autosave restores project visibility state");
			assertNear(0.2f, active.defaultAnimationMix(), "autosave restores animation mix settings");
			assertEquals(2, active.graphViewStates().size(), "autosave restores Graph states");
			assertTrue(active.timelineViewState().curveView(), "autosave restores the curve workspace state");
			assertTrue(active.psdSource() != null && active.psdSource().layers().get(0).sourceIndex() == 4,
				"autosave restores the managed PSD source and layer mapping");

			active.setAnimation("idle");
			active.setSkin("default");
			active.seek(0f);
			active.setPlaying(true);
			active.setLooping(true);
			active.playbackSpeed = 1f;
			active.restoreAnimationMixSettings(0f, List.of());
			active.setPhysicsDeterministic(false);
			active.setSelectedBone(active.skeleton.getRootBone());
			active.restoreAxisSeparationPreferences(List.of());
			active.restoreAxisCombineStates(List.of());
			active.restoreGraphViewStates(java.util.Map.of());
			active.restoreMeshWeightLocks(java.util.Map.of());
			active.setTreeViewState(SkeletonDocument.TreeViewState.empty());
			active.setTimelineViewState(SkeletonDocument.TimelineViewState.empty());
			active.setGraphWorkspaceState(SkeletonDocument.GraphWorkspaceState.empty());
			ProjectStateStore.LoadOutcome initialLoad = ProjectStateStore.load(source, project, true);
			assertEquals(ProjectStateStore.LoadResult.RESTORED, initialLoad.result(),
				"matching source state is restored");
			assertTrue(!initialLoad.showAllSkeletons(),
				"the project-level all-skeletons visibility switch is restored");
			assertEquals("walk", active.getCurrentAnimationName(), "the active animation is restored");
			assertNear(0.75f, active.getCurrentTime(), "the playhead is restored");
			assertEquals("alt", active.getCurrentSkinName(), "the preview skin is restored");
			assertTrue(!active.playing && !active.looping, "playback switches are restored");
			assertNear(1.65f, active.playbackSpeed, "playback speed is restored");
			assertNear(0.2f, active.defaultAnimationMix(), "default animation mix is restored");
			assertEquals(2, active.animationMixes().size(), "animation-pair mixes are restored");
			assertNear(0.45f, active.animationState.getData().getMix(active.data.findAnimation("idle"),
				active.data.findAnimation("walk")), "restored animation mix is applied to the runtime preview");
			assertTrue(active.isPhysicsDeterministic(), "Physics deterministic preview is restored");
			assertEquals(SkeletonDocument.SelectionKind.CONSTRAINT, active.getSelectionKind(),
				"constraint selection kind is restored");
			assertEquals("aim", active.getSelectedConstraint().getName(), "constraint selection is restored");
			assertEquals("target:root", active.getSelectedConstraintRelation(),
				"the exact constraint relation node is restored");
			assertTrue(active.isSelected(active.skeleton.findBone("root"))
				&& active.isSelected(active.skeleton.findBone("child")),
				"all bones related to the selected constraint are highlighted");
			assertTrue(active.isAxisSeparationPreferred("walk", "child", "TRANSLATE"),
				"XY Separate editor preference is restored");
			SkeletonDocument.AxisCombineState restoredAxis = active.axisCombineState("walk", "child", "SCALE");
			assertTrue(restoredAxis != null && restoredAxis.xBeforeY()
				&& java.util.Arrays.equals(new float[] {0f, 1f, 0.75f, 2f}, restoredAxis.xFrames())
				&& java.util.Arrays.equals(new float[] {0f, 1f, 1f, 0.75f, 2f, 0.8f},
					restoredAxis.combinedFrames()),
				"reversible axis structure round trips through the v9 sidecar");
			assertEquals(2, active.graphViewStates().size(), "Graph view states are restored");
			SkeletonDocument.GraphViewState restoredGraph = active.graphViewState("walk|Translate|1");
			assertTrue(restoredGraph.selectionStored()
				&& java.util.Arrays.equals(new float[] {0.25f, 1.5f}, restoredGraph.selectedTimes())
				&& java.util.Arrays.equals(new int[] {0, 1}, restoredGraph.selectedChannels()),
				"Graph multi-channel key selection round trips through the sidecar");
			assertTrue(!restoredGraph.editTime() && restoredGraph.editValue() && !restoredGraph.snap()
				&& restoredGraph.autoFrame() && restoredGraph.separateHandles()
				&& restoredGraph.transformMode() == 2,
				"Graph editing switches round trip through the sidecar");
			SkeletonDocument.GraphViewState emptyGraph = active.graphViewState("walk|Rotate|1");
			assertTrue(emptyGraph.selectionStored() && emptyGraph.selectedTimes().length == 0,
				"an explicit blank Graph selection does not fall back to a key after reload");
			assertEquals(2, active.meshWeightLocks(weightKey).size(),
				"Mesh influence locks round trip through the sidecar");
			SkeletonDocument.TreeViewState restoredTree = active.treeViewState();
			assertEquals("aim", restoredTree.filter(), "hierarchy filter is restored");
			assertNear(18f, restoredTree.scrollX(), "hierarchy horizontal scroll is restored");
			assertNear(42f, restoredTree.scrollY(), "hierarchy vertical scroll is restored");
			assertTrue(restoredTree.expandedValues().contains("constraint:IK:aim")
				&& restoredTree.treeValues().stream().anyMatch(value -> value.startsWith("constraint-relation:")),
				"constraint and relation hierarchy values are restored");
			SkeletonDocument.TimelineViewState restoredTimeline = active.timelineViewState();
			assertTrue(restoredTimeline.curveView() && restoredTimeline.curveSelectedChannel() == 1,
				"main timeline keyframe/curve view context is restored");
			assertNear(135f, restoredTimeline.scrollX(), "main timeline horizontal scroll is restored");
			assertNear(57f, restoredTimeline.scrollY(), "main timeline vertical scroll is restored");
			assertTrue(restoredTimeline.selectionStored()
				&& java.util.Arrays.equals(new String[] {"bone\u001fchild\u001fTRANSLATE", "event"},
					restoredTimeline.selectedTrackKeys())
				&& java.util.Arrays.equals(new float[] {0.25f, 1.5f}, restoredTimeline.selectedTimes())
				&& java.util.Arrays.equals(new int[] {0, 2}, restoredTimeline.selectedEventOccurrences()),
				"main timeline primary track and multiple selected keys round trip through the sidecar");
			SkeletonDocument.GraphWorkspaceState restoredWorkspace = active.graphWorkspaceState();
			assertTrue(restoredWorkspace.open() && restoredWorkspace.animation().equals("walk")
				&& restoredWorkspace.filter().equals("child") && restoredWorkspace.locked()
				&& restoredWorkspace.detailTrackKey().equals("bone\u001fchild\u001fTRANSLATE"),
				"Graph workspace identity and controls round trip through the sidecar");
			assertNear(0.47f, restoredWorkspace.splitAmount(), "Graph workspace split is restored");
			assertNear(83f, restoredWorkspace.scrollX(), "Graph workspace horizontal scroll is restored");
			assertNear(126f, restoredWorkspace.scrollY(), "Graph workspace vertical scroll is restored");
			assertNear(1180f, restoredWorkspace.width(), "Graph workspace width is restored");
			SkeletonDocument.PsdSource restoredPsd = active.psdSource();
			assertTrue(restoredPsd != null && restoredPsd.ignoreHiddenLayers()
				&& Path.of(restoredPsd.sourcePath()).equals(psd.toAbsolutePath().normalize())
				&& restoredPsd.layers().get(0).sourceIndex() == 4
				&& restoredPsd.layers().get(0).slotName().equals("body"),
				"PSD source settings and source-to-slot mapping round trip through the v12 sidecar");

			String malformedState = stableWrite
				.replaceFirst("\"selectedChannels\"\\s*:\\s*\\[\\s*0\\s*,\\s*1\\s*\\]",
					"\"selectedChannels\": [0]")
				.replaceFirst("\"selectedEventOccurrences\"\\s*:\\s*\\[\\s*0\\s*,\\s*2\\s*\\]",
					"\"selectedEventOccurrences\": [0]")
				.replaceFirst("\"vertex\"\\s*:\\s*3", "\"vertex\": 99");
			assertTrue(!malformedState.equals(stableWrite), "the malformed-state fixture changes the sidecar");
			Files.writeString(sidecar, malformedState, StandardCharsets.UTF_8);
			active.restoreGraphViewStates(java.util.Map.of());
			active.restoreMeshWeightLocks(java.util.Map.of());
			active.setTimelineViewState(SkeletonDocument.TimelineViewState.empty());
			assertEquals(ProjectStateStore.LoadResult.RESTORED, ProjectStateStore.load(source, project),
				"invalid optional editor entries do not reject otherwise valid state");
			assertTrue(active.graphViewState("walk|Translate|1") == null
				&& active.graphViewState("walk|Rotate|1") != null,
				"a Graph entry with mismatched selection arrays is skipped independently");
			assertEquals(1, active.meshWeightLocks(weightKey).size(),
				"out-of-range Mesh locks are discarded while valid locks remain");
			assertTrue(active.timelineViewState().selectionStored()
				&& active.timelineViewState().selectedTrackKeys().length == 0
				&& active.timelineViewState().curveView(),
				"a malformed main timeline selection is skipped without losing the remaining view context");
			Files.writeString(sidecar, stableWrite, StandardCharsets.UTF_8);
			assertEquals(ProjectStateStore.LoadResult.RESTORED, ProjectStateStore.load(source, project),
				"the valid fixture is restored after malformed-state checks");

			active.setSlotSelection(active.skeleton.findSlot("body"), "body", "body-region");
			ProjectStateStore.write(source, project);
			active.setSelectedBone(active.skeleton.getRootBone());
			assertEquals(ProjectStateStore.LoadResult.RESTORED, ProjectStateStore.load(source, project),
				"attachment selection state can be reloaded");
			assertEquals(SkeletonDocument.SelectionKind.ATTACHMENT, active.getSelectionKind(),
				"attachment selection kind is restored");
			assertEquals("body", active.getSelectedSlotName(), "selected slot name is restored");
			assertEquals("body-region", active.getSelectedAttachmentName(),
				"selected attachment identity is restored");

			active.setAnimation("walk");
			active.setPlaying(false);
			active.setLooping(true);
			active.seek(2f);
			ProjectStateStore.write(source, project);
			active.setPlaying(true);
			active.seek(0.25f);
			assertEquals(ProjectStateStore.LoadResult.RESTORED, ProjectStateStore.load(source, project),
				"paused looping end-frame state can be reloaded");
			assertTrue(!active.playing && active.looping, "paused looping playback switches are restored");
			assertNear(2f, active.getCurrentTime(),
				"paused looping end frame is restored exactly instead of wrapping to zero");

			active.setPlaying(true);
			ProjectStateStore.write(source, project);
			active.setPlaying(false);
			active.seek(0.25f);
			assertEquals(ProjectStateStore.LoadResult.RESTORED, ProjectStateStore.load(source, project),
				"playing looping end-frame state can be reloaded");
			assertTrue(active.playing && active.looping, "playing looping playback switches are restored");
			assertNear(2f, active.getCurrentTime(),
				"a playing looping end frame remains visible until the first real playback tick");
			active.applyAnimation(1f / 30f);
			assertTrue(active.getCurrentTime() > 0f && active.getCurrentTime() < 0.1f,
				"the first real playback tick advances across the loop boundary normally");

			active.clearAnimation();
			ProjectStateStore.write(source, project);
			active.setAnimation("idle");
			assertEquals(ProjectStateStore.LoadResult.RESTORED, ProjectStateStore.load(source, project),
				"an explicitly blank animation selection can be reloaded");
			assertTrue(active.getCurrentAnimationName().isEmpty() && !active.shouldAutoSelectAnimation(),
				"Sidecar v7 restores no animation instead of selecting the first animation");

			String version7 = Files.readString(sidecar, StandardCharsets.UTF_8);
			String version2 = version7.replaceFirst("\"version\"\\s*:\\s*7", "\"version\": 2")
				.replaceFirst("\\s*\"showAllSkeletons\"\\s*:\\s*(?:true|false)\\s*,", "")
				.replaceAll("(?s),\\s*\"selectionStored\"\\s*:\\s*(?:true|false)\\s*,"
					+ "\\s*\"selectedTimes\"\\s*:\\s*\\[[^\\]]*\\]\\s*,"
					+ "\\s*\"selectedChannels\"\\s*:\\s*\\[[^\\]]*\\]", "")
				.replaceAll("(?s),\\s*\"timeline\"\\s*:\\s*\\{.*?\\n\\s*\\}", "");
			Files.writeString(sidecar, version2, StandardCharsets.UTF_8);
			active.restoreGraphViewStates(java.util.Map.of());
			ProjectStateStore.LoadOutcome legacyLoad = ProjectStateStore.load(source, project, false);
			assertEquals(ProjectStateStore.LoadResult.RESTORED, legacyLoad.result(),
				"version 2 sidecars without Graph selection fields remain loadable");
			assertTrue(legacyLoad.showAllSkeletons(),
				"legacy sidecars without project visibility state use the historical show-all default");
			restoredGraph = active.graphViewState("walk|Translate|1");
			assertTrue(restoredGraph != null && !restoredGraph.selectionStored()
				&& restoredGraph.selectedTimes().length == 0,
				"legacy Graph state migrates to the old single-key fallback semantics");
			assertTrue(!active.timelineViewState().selectionStored(),
				"legacy sidecars without main timeline context use a safe empty state");
			ProjectStateStore.write(source, project);

			Files.setLastModifiedTime(source, FileTime.fromMillis(sourceTime.toMillis() + 2000L));
			active.restoreAxisSeparationPreferences(List.of());
			assertEquals(ProjectStateStore.LoadResult.RESTORED, ProjectStateStore.load(source, project),
				"identical source content survives copied or extracted timestamp changes");
			assertTrue(active.isAxisSeparationPreferred("walk", "child", "TRANSLATE"),
				"content-verified state is fully applied");

			FileTime unchangedTime = Files.getLastModifiedTime(source);
			Files.writeString(source, "official project byteS", StandardCharsets.UTF_8);
			Files.setLastModifiedTime(source, unchangedTime);
			active.restoreAxisSeparationPreferences(List.of());
			assertEquals(ProjectStateStore.LoadResult.SOURCE_CHANGED, ProjectStateStore.load(source, project),
				"same-size source changes are rejected even when the timestamp is preserved");

			Files.writeString(source, "changed official project bytes", StandardCharsets.UTF_8);
			active.restoreAxisSeparationPreferences(List.of());
			assertEquals(ProjectStateStore.LoadResult.SOURCE_CHANGED, ProjectStateStore.load(source, project),
				"stale state is rejected after the source changes");
			assertTrue(!active.isAxisSeparationPreferred("walk", "child", "TRANSLATE"),
				"stale state is not partially applied");

			Path runtimeSource = directory.resolve("hero-runtime.json");
			Files.writeString(runtimeSource, "{}", StandardCharsets.UTF_8);
			active.setAxisSeparationPreferred("walk", "child", "SHEAR", true);
			ProjectStateStore.write(runtimeSource, project);
			assertEquals("hero-runtime.json.openspine.json",
				ProjectStateStore.sidecarPath(runtimeSource).getFileName().toString(),
				"runtime projects keep their source extension in the Sidecar name");
			active.restoreAxisSeparationPreferences(List.of());
			assertEquals(ProjectStateStore.LoadResult.RESTORED,
				ProjectStateStore.load(runtimeSource, project),
				"runtime JSON projects restore OpenSpine-only editor state");
			assertTrue(active.isAxisSeparationPreferred("walk", "child", "SHEAR"),
				"runtime project Sidecars preserve reversible axis editor state");
			project.close();
			System.out.println("ALL PROJECT STATE STORE TESTS PASSED");
		} finally {
			try (var paths = Files.walk(directory)) {
				paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (Exception ignored) {}
				});
			}
		}
	}

	private static SkeletonDocument document(Path source) {
		SkeletonData data = new SkeletonData();
		data.setName("hero");
		data.setFps(30f);
		BoneData root = new BoneData(0, "root", null);
		BoneData child = new BoneData(1, "child", root);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		child.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root);
		data.getBones().add(child);
		data.getSlots().add(new SlotData(0, "body", child));
		IkConstraintData aim = new IkConstraintData("aim");
		aim.getBones().add(child);
		aim.setTarget(root);
		data.getConstraints().add(aim);
		Skin defaultSkin = new Skin("default");
		Skin alt = new Skin("alt");
		MeshAttachment body = new MeshAttachment("body-region", new Sequence(1, false));
		body.setWorldVerticesLength(8);
		body.setVertices(new float[8]);
		defaultSkin.setAttachment(0, "body-region", body);
		data.getSkins().add(defaultSkin);
		data.getSkins().add(alt);
		data.setDefaultSkin(defaultSkin);
		Animation idle = new Animation("idle");
		idle.setTimelines(Array.of(Animation.Timeline[]::new), new com.badlogic.gdx.utils.IntArray());
		idle.setDuration(1f);
		Animation walk = new Animation("walk");
		walk.setTimelines(Array.of(Animation.Timeline[]::new), new com.badlogic.gdx.utils.IntArray());
		walk.setDuration(2f);
		data.getAnimations().add(idle);
		data.getAnimations().add(walk);
		Skeleton skeleton = new Skeleton(data);
		skeleton.setSkin(defaultSkin);
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(new TextureAtlas(), data, skeleton, source.toString(), "", null);
		document.animationState = new AnimationState(new AnimationStateData(data));
		document.setAnimation("idle");
		return document;
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private ProjectStateStoreTest() {}
}
