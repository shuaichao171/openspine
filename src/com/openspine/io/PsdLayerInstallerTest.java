package com.openspine.io;

import java.nio.file.Path;
import java.util.List;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Headless coverage for atomic, undoable PSD layer installation. */
public final class PsdLayerInstallerTest {
	public static void main(String[] args) {
		preservesSourceIndicesWhenHiddenLayersAreIgnored();
		preservesEarlierHistoryAndSupportsUndoRedo();
		rollsBackEveryMutationWhenRebuildFails();
		rejectsInstallationIntoAReplacedDocument();
		System.out.println("ALL PSD LAYER INSTALLER TESTS PASSED");
	}

	private static void rejectsInstallationIntoAReplacedDocument() {
		SkeletonDocument doc = document();
		TextureAtlas atlas = new TextureAtlas();
		addAtlasRegion(atlas, "top"); addAtlasRegion(atlas, "bottom");
		PsdLayerInstaller.Transaction transaction = PsdLayerInstaller.prepare(doc, new SkeletonEditor(doc),
			importResult(), atlas);
		SkeletonDocument replacement = document();
		doc.data = replacement.data; doc.skeleton = replacement.skeleton;
		assertThrows(transaction::apply, "a prepared PSD import cannot be applied to another skeleton");
		assertTrue(doc.data.getSlots().isEmpty() && doc.data.getSkins().isEmpty() && !transaction.isApplied(),
			"rejecting a stale import leaves the replacement skeleton untouched");
		PsdLayerInstaller.Transaction changedRoot = PsdLayerInstaller.prepare(doc, new SkeletonEditor(doc),
			importResult(), atlas);
		BoneData root = new BoneData(0, "replacement-root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		doc.data.getBones().set(0, root); doc.skeleton = new Skeleton(doc.data);
		assertThrows(changedRoot::apply, "a prepared import cannot retain a bone removed from the same skeleton");
		assertTrue(doc.data.getSlots().isEmpty() && doc.data.getSkins().isEmpty() && !changedRoot.isApplied(),
			"rejecting a stale mounting bone does not install foreign bone references");
		atlas.dispose();
	}

	private static void preservesSourceIndicesWhenHiddenLayersAreIgnored() {
		PsdImporter.ImportResult filtered = importResult().ignoringHiddenLayers();
		assertTrue(filtered.hiddenLayersIgnored() && filtered.layers().size() == 1
			&& filtered.layers().get(0).sourceIndex() == 7,
			"filtering hidden layers preserves the original PSD layer identity");
	}

	private static void preservesEarlierHistoryAndSupportsUndoRedo() {
		SkeletonDocument doc = document();
		TextureAtlas atlas = new TextureAtlas();
		TextureRegion topRegion = addAtlasRegion(atlas, "top");
		addAtlasRegion(atlas, "bottom");
		SkeletonEditor editor = new SkeletonEditor(doc);
		EditHistory history = new EditHistory(doc);
		int[] priorValue = {1};
		history.record("导入前编辑", () -> priorValue[0] = 0, () -> priorValue[0] = 1);

		PsdLayerInstaller.Transaction transaction = PsdLayerInstaller.prepare(doc, editor,
			importResult(), atlas);
		transaction.apply();
		history.record("导入 PSD", transaction::undo, transaction::apply);
		assertTrue(doc.data.getSlots().size == 2, "both PSD layers are installed in one transaction");
		assertEquals("Bottom", doc.data.getSlots().get(0).getName(),
			"bottom PSD layer is first in the setup draw order");
		assertEquals("Top", doc.data.getSlots().get(1).getName(),
			"top PSD layer is last in the setup draw order");
		assertTrue(!doc.data.getSlots().get(0).getVisible() && doc.data.getSlots().get(1).getVisible(),
			"effective PSD visibility is preserved on the installed slots");
		RegionAttachment top = (RegionAttachment)doc.data.getDefaultSkin().getAttachment(1, "top");
		assertTrue(top != null && top.getSequence().getRegions()[0] == topRegion,
			"installed attachment uses the packed atlas region");
		assertTrue(top.getX() == 0f && top.getY() == 0f && top.getWidth() == 20f && top.getHeight() == 10f,
			"PSD canvas coordinates become Region setup coordinates");
		SkeletonDocument.PsdSource source = transaction.sourceMetadata(importResult());
		assertTrue(!source.ignoreHiddenLayers() && source.layers().size() == 2,
			"the installed batch exposes complete PSD source metadata");
		assertTrue(source.layers().get(0).sourceIndex() == 3
			&& source.layers().get(0).slotName().equals("Bottom")
			&& source.layers().get(1).sourceIndex() == 7
			&& source.layers().get(1).slotName().equals("Top"),
			"source indices remain attached to their slots in PSD bottom-to-top order");

		assertTrue(history.undo(), "PSD import is undoable");
		assertTrue(doc.data.getSlots().isEmpty() && doc.data.getSkins().isEmpty()
			&& doc.data.getDefaultSkin() == null, "undo removes all imported layers and the generated default skin");
		assertEquals("导入前编辑", history.undoLabel(),
			"undoing PSD import preserves the preceding history entry");
		assertTrue(priorValue[0] == 1, "undoing PSD import does not undo earlier edits");
		assertTrue(history.redo() && doc.data.getSlots().size == 2,
			"redo reinstalls the exact PSD layer batch");
		atlas.dispose();
	}

	private static void rollsBackEveryMutationWhenRebuildFails() {
		SkeletonDocument doc = document();
		TextureAtlas atlas = new TextureAtlas();
		addAtlasRegion(atlas, "top");
		addAtlasRegion(atlas, "bottom");
		boolean initialDirty = doc.dirty;
		long initialRevision = doc.revision;
		SkeletonEditor failingEditor = new SkeletonEditor(doc) {
			@Override public void rebuildSkeleton() { throw new IllegalStateException("simulated rebuild failure"); }
		};
		PsdLayerInstaller.Transaction transaction = PsdLayerInstaller.prepare(doc, failingEditor,
			importResult(), atlas);
		assertThrows(transaction::apply, "a rebuild failure must fail the PSD transaction");
		assertTrue(!transaction.isApplied() && doc.data.getSlots().isEmpty()
			&& doc.data.getSkins().isEmpty() && doc.data.getDefaultSkin() == null,
			"a failed PSD transaction restores slots, attachments, skins, and default skin");
		assertTrue(doc.dirty == initialDirty && doc.revision == initialRevision,
			"a failed PSD transaction restores dirty and revision state");
		atlas.dispose();
	}

	private static PsdImporter.ImportResult importResult() {
		return new PsdImporter.ImportResult(Path.of("sample.psd"), Path.of("layers"), 20, 10, List.of(
			new PsdImporter.ImportedLayer("Bottom", "bottom", 2, 1, 4, 3, false, 255, Path.of("bottom.png"), 3),
			new PsdImporter.ImportedLayer("Top", "top", 0, 0, 20, 10, true, 255, Path.of("top.png"), 7)));
	}

	private static SkeletonDocument document() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		return doc;
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

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (RuntimeException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private PsdLayerInstallerTest() {}
}
