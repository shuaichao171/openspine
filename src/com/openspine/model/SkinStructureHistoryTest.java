package com.openspine.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.files.FileHandle;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.io.SkeletonBinaryExporter;
import com.openspine.io.SkeletonExporter;
import com.openspine.io.SkeletonLoader;

/** Skin lifecycle commands must retain identities required by preceding and subsequent history. */
public final class SkinStructureHistoryTest {
	public static void main(String[] args) throws Exception {
		renamesPreserveOrderedPreviewLocksAndBothFormats();
		createCopyRenameDeleteRestoreTheSameInstances();
		copiedAttachmentsAndActivationsSurviveUndoRedo();
		deleteRestoresSetupPlaceholderAndInheritedTimelineActivation();
		deleteRestoresSourcesDetachedWithTheirLinkedMeshes();
		protectedAndNoopEditsAreAtomic();
		System.out.println("ALL SKIN STRUCTURE HISTORY TESTS PASSED");
	}

	private static void renamesPreserveOrderedPreviewLocksAndBothFormats() throws Exception {
		SkeletonDocument doc = PermanentSkinCompositionTest.document(true);
		try {
			SkeletonEditor editor = new SkeletonEditor(doc);
			EditHistory history = new EditHistory(doc);
			Skin base = doc.data.findSkin("base"), overlay = doc.data.findSkin("overlay");
			MeshAttachment source = (MeshAttachment)base.getAttachment(0, "image");
			Animation.DeformTimeline originalTrack = new AnimationEditor(doc).findDeformTimeline(doc.data.findAnimation("motion"), 0, source);
			Set<SkeletonDocument.MeshWeightLock> locks = Set.of(new SkeletonDocument.MeshWeightLock(2, 0));
			String originalKey = SkeletonDocument.meshWeightStateKey("base", "slot0", "image");
			String renamedKey = SkeletonDocument.meshWeightStateKey("基础 / 重命名", "slot0", "image");
			String originalTrackKey = String.join("\u001f", "deform", "slot0", "image", source.getName(), "base");
			String renamedTrackKey = String.join("\u001f", "deform", "slot0", "image", source.getName(), "基础 / 重命名");
			doc.setTimelineViewState(new SkeletonDocument.TimelineViewState("motion", true, 0, originalTrackKey, 24f, 36f,
				true, new String[] {originalTrackKey}, new float[] {0.8f}, new int[] {0}));
			doc.setGraphWorkspaceState(new SkeletonDocument.GraphWorkspaceState(true, "motion", "all", true,
				originalTrackKey, 0.5f, 12f, 23f, 30f, 40f, 600f, 400f));
			doc.setMeshWeightLocks(originalKey, locks); doc.scrubTo(0.65f); doc.setPlaying(false); history.markSaved();
			String originalJson = SkeletonExporter.toJson(doc);
			SkeletonEditor.SkinStructureEdit rename = editor.renameSkinEdit(base, " 基础 / 重命名 "); record(history, rename);
			Skin renamed = rename.skin();
			check(doc.getPreviewSkinNames().equals(List.of("基础 / 重命名", "overlay")) && doc.getEditingSkin() == overlay,
				"renaming a lower layer preserves the top editing skin and exact order");
			check(renamed.getAttachment(0, "image") == source && renamed.getBones().get(0) == base.getBones().get(0)
				&& renamed.getConstraints().get(0) == base.getConstraints().get(0), "rename keeps attachment, bone and constraint references");
			check(doc.meshWeightLocks(originalKey).isEmpty() && doc.meshWeightLocks(renamedKey).equals(locks), "rename migrates mesh lock key");
			check(doc.timelineViewState().selectedTrackKeys()[0].equals(renamedTrackKey)
				&& doc.timelineViewState().curveTrackKey().equals(renamedTrackKey)
				&& doc.graphWorkspaceState().detailTrackKey().equals(renamedTrackKey), "rename migrates attachment track side-state skin token");
			check(doc.timelineViewState().selectedTimes()[0] == 0.8f && doc.timelineViewState().scrollX() == 24f
				&& doc.graphWorkspaceState().scrollY() == 23f, "skin token migration preserves time and view positions");
			check(new AnimationEditor(doc).findDeformTimeline(doc.data.findAnimation("motion"), 0, source) == originalTrack, "rename retains original timeline identity");
			check(Math.abs(doc.getCurrentTime() - 0.65f) < 0.0001f && !doc.playing, "rename keeps paused cursor");
			Path directory = Files.createTempDirectory("openspine-skin-rename-");
			Path json = directory.resolve("renamed.json"), binary = directory.resolve("renamed.skel");
			try {
				SkeletonExporter.writeAtomic(doc, json); SkeletonBinaryExporter.writeAtomic(doc, binary);
				for (Path path : List.of(json, binary)) {
					var loaded = SkeletonLoader.loadAnimationSource(new FileHandle(path.toFile()));
					Skin loadedSkin = loaded.findSkin("基础 / 重命名");
					check(loadedSkin != null && loaded.findSkin("base") == null, "renamed skin roundtrips in " + path.getFileName());
					MeshAttachment loadedSource = (MeshAttachment)loadedSkin.getAttachment(0, "image");
					MeshAttachment loadedLinked = (MeshAttachment)loaded.findSkin("overlay").getAttachment(1, "image");
					check(loadedLinked.getSourceMesh() == loadedSource && loadedLinked.getTimelineAttachment() == loadedSource,
						"cross-skin linked mesh resolves renamed source in " + path.getFileName());
					check(loadedSkin.getBones().size == 1 && loadedSkin.getConstraints().size == 1, "activation metadata roundtrips");
					boolean found = false;
					for (Animation.Timeline track : loaded.findAnimation("motion").getTimelines())
						if (track instanceof Animation.DeformTimeline deform && deform.getAttachment() == loadedSource) found = true;
					check(found, "renamed source Deform ownership roundtrips");
				}
			} finally { Files.deleteIfExists(json); Files.deleteIfExists(binary); Files.deleteIfExists(directory); }
			check(history.undo() && doc.data.findSkin("base") == base, "undo restores exact before-instance");
			check(doc.getPreviewSkinNames().equals(List.of("base", "overlay")) && doc.meshWeightLocks(originalKey).equals(locks)
				&& doc.meshWeightLocks(renamedKey).isEmpty(), "undo restores layers and original lock key");
			check(doc.timelineViewState().selectedTrackKeys()[0].equals(originalTrackKey)
				&& doc.graphWorkspaceState().detailTrackKey().equals(originalTrackKey), "rename undo remaps persistent selection back to original skin");
			check(originalJson.equals(SkeletonExporter.toJson(doc)) && !doc.dirty, "rename undo restores authoring JSON and savepoint");
			check(history.redo() && doc.data.findSkin("基础 / 重命名") == renamed, "redo restores exact after-instance");
			check(doc.timelineViewState().curveTrackKey().equals(renamedTrackKey), "rename redo remaps persistent selection to retained after-instance");
			SkeletonEditor.SkinStructureEdit top = editor.renameSkinEdit(overlay, "top"); record(history, top);
			check(doc.getPreviewSkinNames().equals(List.of("基础 / 重命名", "top")) && doc.getEditingSkin() == top.skin(), "top-layer rename preserves editing target");
			check(history.undo() && history.undo() && doc.data.findSkin("overlay") == overlay && doc.data.findSkin("base") == base,
				"successive layer renames restore both original objects");
			check(history.redo() && history.redo() && doc.getEditingSkin() == top.skin(), "successive layer renames redo coherently");
		} finally { doc.dispose(); }
	}

	private static void copiedAttachmentsAndActivationsSurviveUndoRedo() throws Exception {
		SkeletonDocument doc = PermanentSkinCompositionTest.document(false);
		try {
			doc.clearAnimation(); doc.data.getAnimations().clear();
			SkeletonEditor editor = new SkeletonEditor(doc); Skin source = doc.data.findSkin("base");
			MeshAttachment sourceMesh = (MeshAttachment)source.getAttachment(0, "image");
			EditHistory history = new EditHistory(doc); history.markSaved();
			String original = SkeletonExporter.toJson(doc);
			SkeletonEditor.SkinStructureEdit duplicate = editor.duplicateSkinEdit(source, "copied"); record(history, duplicate);
			Skin copy = duplicate.skin(); MeshAttachment copiedMesh = (MeshAttachment)copy.getAttachment(0, "image");
			check(copiedMesh != sourceMesh && copiedMesh.getSourceMesh() == sourceMesh && copiedMesh.getTimelineAttachment() == sourceMesh,
				"ordinary duplication retains existing linked-mesh semantics");
			check(copiedMesh.getSequence() != sourceMesh.getSequence() && copy.getAttachment(3, "image") != source.getAttachment(3, "image"),
				"duplicate owns its Sequence and nonmesh attachment copies");
			check(copy.getBones().size == source.getBones().size && copy.getConstraints().size == source.getConstraints().size,
				"duplicate retains source skin activations");
			check(history.undo() && doc.data.findSkin("copied") == null && original.equals(SkeletonExporter.toJson(doc)), "duplicate undo restores exact source authoring state");
			check(history.redo() && doc.data.findSkin("copied") == copy && copy.getAttachment(0, "image") == copiedMesh,
				"duplicate redo restores the same copied attachment identities");
			SkeletonEditor.SkinStructureEdit delete = editor.removeSkinEdit(copy); record(history, delete);
			check(history.undo() && doc.data.findSkin("copied") == copy, "deleted duplicate restores original copy object");
			check(history.undo() && doc.data.findSkin("copied") == null, "creation history remains usable after deleting and restoring a duplicate");
		} finally { doc.dispose(); }
	}

	private static void createCopyRenameDeleteRestoreTheSameInstances() {
		SkeletonDocument doc = PermanentSkinCompositionTest.document(false);
		try {
			SkeletonEditor editor = new SkeletonEditor(doc); EditHistory history = new EditHistory(doc); history.markSaved();
			List<String> layers = doc.getPreviewSkinNames();
			SkeletonEditor.SkinStructureEdit create = editor.addSkinEdit("blank", false); record(history, create);
			Skin blank = create.skin();
			SkeletonEditor.SkinStructureEdit copy = editor.duplicateSkinEdit(blank, "copy"); record(history, copy);
			Skin duplicate = copy.skin();
			SkeletonEditor.SkinStructureEdit rename = editor.renameSkinEdit(duplicate, "blank"); record(history, rename);
			check(rename.skin().getName().equals("blank2"), "rename retains existing unique-name behavior");
			SkeletonEditor.SkinStructureEdit remove = editor.removeSkinEdit(rename.skin()); record(history, remove);
			check(doc.data.findSkin("blank2") == null, "delete removes current named instance");
			check(history.undo() && doc.data.findSkin("blank2") == rename.skin(), "delete undo restores renamed instance");
			check(history.undo() && doc.data.findSkin("copy") == duplicate, "rename undo restores created copy instance");
			check(history.undo() && doc.data.findSkin("copy") == null && doc.data.findSkin("blank") == blank, "duplicate undo retains preceding new skin");
			check(history.undo() && doc.getPreviewSkinNames().equals(layers) && !doc.dirty, "create undo restores prior preview/savepoint");
			check(history.redo() && doc.data.findSkin("blank") == blank, "create redo reuses original skin");
			check(history.redo() && doc.data.findSkin("copy") == duplicate, "duplicate redo reuses copy");
			check(history.redo() && doc.data.findSkin("blank2") == rename.skin(), "rename redo reuses renamed copy");
			check(history.redo() && doc.data.findSkin("blank2") == null, "delete redo removes same instance");
		} finally { doc.dispose(); }
	}

	private static void deleteRestoresSetupPlaceholderAndInheritedTimelineActivation() throws Exception {
		SkeletonDocument doc = PermanentSkinCompositionTest.document(false);
		try {
			MeshAttachment source = (MeshAttachment)doc.data.findSkin("base").getAttachment(0, "image");
			SlotData loose = new SlotData(6, "loose", doc.data.getBones().first()); loose.setBlendMode(BlendMode.normal); loose.setAttachmentName("loose");
			doc.data.getSlots().add(loose); source.setTimelineSlots(new int[] {1, 6});
			MeshAttachment attachment = new MeshAttachment("loose", new Sequence(1, false)); attachment.setPath("mesh");
			attachment.setSourceMesh(source); attachment.setTimelineAttachment(source);
			Skin removable = new Skin("removable"); removable.setAttachment(6, "loose", attachment); doc.data.getSkins().add(removable);
			SkeletonEditor editor = new SkeletonEditor(doc); editor.rebuildSkeleton(); doc.setPreviewSkins(List.of("base", "overlay", "removable"));
			String key = SkeletonDocument.meshWeightStateKey("removable", "loose", "loose");
			Set<SkeletonDocument.MeshWeightLock> locks = Set.of(new SkeletonDocument.MeshWeightLock(1, 0)); doc.setMeshWeightLocks(key, locks);
			EditHistory history = new EditHistory(doc); history.markSaved();
			String before = SkeletonExporter.toJson(doc);
			SkeletonEditor.SkinStructureEdit remove = editor.removeSkinEdit(removable); record(history, remove);
			check(loose.getAttachmentName() == null && Arrays.equals(source.getTimelineSlots(), new int[] {1}), "delete clears final placeholder and unused cross-slot activation");
			check(doc.meshWeightLocks(key).isEmpty() && doc.getPreviewSkinNames().equals(List.of("base", "overlay")), "delete removes locks and only removed layer");
			check(history.undo() && doc.data.findSkin("removable") == removable && loose.getAttachmentName().equals("loose"), "undo restores membership and setup placeholder");
			check(Arrays.equals(source.getTimelineSlots(), new int[] {1, 6}) && doc.meshWeightLocks(key).equals(locks), "undo restores inherited Deform activation and weight locks");
			check(doc.getPreviewSkinNames().equals(List.of("base", "overlay", "removable")) && doc.getEditingSkin() == removable, "undo restores ordered preview and editing skin");
			check(before.equals(SkeletonExporter.toJson(doc)), "delete undo restores exact exportable authoring state");
			check(history.redo() && loose.getAttachmentName() == null && Arrays.equals(source.getTimelineSlots(), new int[] {1}), "delete redo repeats complete cleanup");
		} finally { doc.dispose(); }
	}

	private static void protectedAndNoopEditsAreAtomic() {
		SkeletonDocument doc = PermanentSkinCompositionTest.document(false);
		try {
			SkeletonEditor editor = new SkeletonEditor(doc); String before = SkeletonExporter.toJson(doc); long revision = doc.revision;
			Skin base = doc.data.findSkin("base");
			check(editor.renameSkinEdit(base, " base ") == null && editor.renameSkinEdit(base, " ") == null, "unchanged and blank names do not create commands");
			check(editor.renameSkinEdit(doc.data.getDefaultSkin(), "renamed-default") == null, "default skin rename stays protected");
			check(editor.removeSkinEdit(base) == null && editor.removeSkinEdit(doc.data.findSkin("overlay")) == null, "animated and linked referenced skins stay protected");
			check(editor.removeSkinEdit(doc.data.getDefaultSkin()) == null, "default skin deletion stays protected");
			check(editor.duplicateSkinEdit(base, "unsafe") == null, "nonmesh Sequence inheritance still blocks unsafe legacy duplication");
			check(before.equals(SkeletonExporter.toJson(doc)) && revision == doc.revision, "rejected operations leave model and revision untouched");
		} finally { doc.dispose(); }
	}

	private static void deleteRestoresSourcesDetachedWithTheirLinkedMeshes() {
		SkeletonDocument doc = PermanentSkinCompositionTest.document(false);
		try {
			MeshAttachment original = (MeshAttachment)doc.data.findSkin("base").getAttachment(0, "image");
			MeshAttachment source = (MeshAttachment)original.copy(); source.setTimelineAttachment(source); source.setTimelineSlots(new int[] {1});
			MeshAttachment linked = source.newLinkedMesh();
			Skin removable = new Skin("internal-links"); removable.setAttachment(0, "internal-source", source); removable.setAttachment(1, "internal-linked", linked);
			doc.data.getSkins().add(removable);
			SkeletonEditor editor = new SkeletonEditor(doc); editor.rebuildSkeleton(); doc.setPreviewSkins(List.of("base", "internal-links"));
			String before = SkeletonExporter.toJson(doc);
			SkeletonEditor.SkinStructureEdit remove = editor.removeSkinEdit(removable);
			check(remove != null && source.getTimelineSlots().length == 0, "deleting all internal links updates the detached source activation");
			remove.undo();
			check(doc.data.findSkin("internal-links") == removable && Arrays.equals(source.getTimelineSlots(), new int[] {1}),
				"undo restores timelineSlots even when source and linked attachment were both detached");
			check(before.equals(SkeletonExporter.toJson(doc)), "internal linked delete undo restores exact authoring JSON");
			remove.redo(); check(source.getTimelineSlots().length == 0, "redo updates the same detached source again");
		} finally { doc.dispose(); }
	}

	private static void record(EditHistory history, SkeletonEditor.SkinStructureEdit edit) {
		check(edit != null, "valid edit must return an applied history command"); history.record("skin structure", edit::undo, edit::redo);
	}
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private SkinStructureHistoryTest() {}
}
