package com.openspine.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonEditor.StandardAttachmentType;

/** Headless regression tests for atomic region-attachment numeric parsing. */
public final class AttachmentPanelTest {
	public static void main(String[] args) {
		Color[] slotColors = AttachmentPanel.parseSlotColors("#33669980", true, "#123ABC");
		assertNear(0x80 / 255f, slotColors[0].a, "slot light color alpha");
		assertNear(0x3A / 255f, slotColors[1].g, "slot dark color green");
		Color[] singleColor = AttachmentPanel.parseSlotColors("#ABCDEF", false, "invalid dark color");
		assertNear(1f, singleColor[0].a, "single-color slot alpha");
		if (singleColor[1] != null) throw new AssertionError("disabled dark color must be ignored");
		assertThrows(() -> AttachmentPanel.parseSlotColors("#12345", false, "#000000"),
			"invalid slot light color is rejected");
		assertThrows(() -> AttachmentPanel.parseSlotColors("#FFFFFF", true, "#12345"),
			"invalid enabled dark color is rejected");
		float[] values = AttachmentPanel.parseRegionValues("6000", "-6000", "25", "-25", "4e3", "4096", "3072");
		assertNear(6000f, values[0], "region X");
		assertNear(-6000f, values[1], "region Y");
		assertNear(4000f, values[4], "region rotation");
		assertNear(4096f, values[5], "region width");
		assertThrows(() -> AttachmentPanel.parseRegionValues("1", "2", "NaN", "1", "0", "10", "20"),
			"a non-finite component rejects the complete region edit");
		assertThrows(() -> AttachmentPanel.parseRegionValues("1", "2", "1", "1", "0", "10"),
			"an incomplete region edit is rejected");
		assertMessage(() -> AttachmentPanel.parseRegionValues("1", "2", "NaN", "1", "0", "10", "20"),
			"缩放 X 必须是有限数值", "invalid fields identify the exact component");
		assertTrue(!AttachmentPanel.nameDraftChanged("slot", " slot "),
			"trimmed current name does not enable rename");
		assertTrue(AttachmentPanel.nameDraftChanged("slot", "slot2"), "a different nonblank name enables rename");
		assertTrue(!AttachmentPanel.nameDraftChanged("slot", "  "), "blank names do not enable rename");
		AttachmentPanel.SlotDraft slotBaseline = AttachmentPanel.slotDraft("#FFFFFFFF", false,
			"#000000", "正常", true);
		assertEquals(slotBaseline, AttachmentPanel.slotDraft("#ffffff", false, "invalid ignored dark", "正常", true),
			"equivalent color notation and disabled dark color are the same slot draft");
		assertTrue(!slotBaseline.equals(AttachmentPanel.slotDraft("#FFFFFFFF", false, "#000000", "正常", false)),
			"visibility changes the slot draft");
		assertTrue(!slotBaseline.equals(AttachmentPanel.slotDraft("invalid", false, "#000000", "正常", true)),
			"invalid edited color remains a changed draft that can report validation");
		AttachmentPanel.RegionDraft regionBaseline = AttachmentPanel.regionDraft("#FFFFFFFF",
			"1", "2", "1", "1", "0", "10", "20");
		assertEquals(regionBaseline, AttachmentPanel.regionDraft("#ffffff", "1e0", "2.0", "1", "1", "-0", "10", "20"),
			"semantically equivalent region values do not enable Apply");
		assertTrue(!regionBaseline.equals(AttachmentPanel.regionDraft("#FFFFFFFF",
			"1", "2", "1", "1", "0", "11", "20")), "a changed region dimension enables Apply");
		assertEquals(StandardAttachmentType.BOUNDING_BOX,
			AttachmentPanel.selectedStandardAttachmentType("Bounding Box"), "Bounding Box creation type mapping");
		assertEquals(StandardAttachmentType.PATH, AttachmentPanel.selectedStandardAttachmentType("Path"),
			"Path creation type mapping");
		assertEquals(StandardAttachmentType.POINT, AttachmentPanel.selectedStandardAttachmentType("Point"),
			"Point creation type mapping");
		assertEquals(StandardAttachmentType.CLIPPING, AttachmentPanel.selectedStandardAttachmentType("Clipping"),
			"Clipping creation type mapping");
		assertTrue(AttachmentPanel.selectedStandardAttachmentType("Mesh") == null,
			"image attachment types remain on their dedicated creation path");
		Skin sourceSkin = new Skin("outfit");
		BoneData sourceBone = new BoneData(0, "root", null);
		SlotData sourceSlot = new SlotData(0, "body-slot", sourceBone);
		MeshAttachment sourceMesh = new MeshAttachment("body", new Sequence(1, false));
		SkeletonEditor.MeshAttachmentSource source = new SkeletonEditor.MeshAttachmentSource(
			sourceSkin, sourceSlot, "body-mesh", sourceMesh);
		assertEquals("outfit · body-slot · body-mesh", AttachmentPanel.linkedMeshSourceLabel(source),
			"Linked Mesh sources have stable skin, slot, and placeholder labels");
		MeshAttachment linkedMesh = new MeshAttachment("linked", new Sequence(1, false));
		linkedMesh.setSourceMesh(sourceMesh);
		assertTrue(AttachmentPanel.editsCurrentLinkedMesh("Linked Mesh", linkedMesh, "linked", " linked ", true),
			"the selected owned Linked Mesh uses the in-place update action");
		assertTrue(!AttachmentPanel.editsCurrentLinkedMesh("Linked Mesh", linkedMesh, "linked", "copy", true),
			"a different placeholder continues to create a new Linked Mesh");
		assertTrue(!AttachmentPanel.editsCurrentLinkedMesh("Linked Mesh", linkedMesh, "linked", "linked", false),
			"a fallback attachment from another skin is never edited through the current skin");
		SkeletonEditor.SequenceAttachmentState sequenceState = SequenceAttachmentDialog.parseState(
			" images/walk_ ", "3", "7", "3", "1");
		assertEquals("images/walk_", sequenceState.path(), "Sequence paths are trimmed");
		assertEquals(3, sequenceState.count(), "Sequence frame count parser");
		assertEquals("images/walk_007", SequenceAttachmentDialog.framePath(sequenceState, 0),
			"Sequence preview applies start and zero padding to the first frame");
		assertEquals("images/walk_009", SequenceAttachmentDialog.framePath(sequenceState, 2),
			"Sequence preview applies start and zero padding to the last frame");
		assertThrows(() -> SequenceAttachmentDialog.parseState("walk", "3.5", "1", "2", "0"),
			"Sequence setup requires exact integer fields");
		assertThrows(() -> SequenceAttachmentDialog.framePath(sequenceState, 3),
			"Sequence preview rejects an out-of-range frame");
		viewportAttachmentPlaceholderUsesTheRenderedAttachment();
		propertyEditsAreUndoable();
		System.out.println("ALL ATTACHMENT PANEL TESTS PASSED");
	}

	private static void viewportAttachmentPlaceholderUsesTheRenderedAttachment() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		SlotData slotData = new SlotData(0, "slot", root);
		data.getSlots().add(slotData);
		Skin skin = new Skin("default");
		RegionAttachment first = region("first");
		RegionAttachment rendered = region("rendered-name-differs-from-placeholder");
		skin.setAttachment(0, "first-placeholder", first);
		skin.setAttachment(0, "rendered-placeholder", rendered);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.skeleton.setSkin(skin);
		Slot slot = doc.skeleton.getSlots().first();
		assertEquals("rendered-placeholder", AttachmentPanel.attachmentPlaceholder(doc, slot, rendered),
			"viewport selection resolves the skin placeholder for the rendered attachment instance");
		assertTrue(AttachmentPanel.attachmentPlaceholder(doc, slot, region("missing")) == null,
			"attachments outside the active and default skins are not guessed by object name");
	}

	private static RegionAttachment region(String name) {
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		RegionAttachment region = new RegionAttachment(name, sequence);
		region.setWidth(10f);
		region.setHeight(10f);
		region.updateSequence();
		return region;
	}

	private static void propertyEditsAreUndoable() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		data.getBones().add(root);
		SlotData slot = new SlotData(0, "slot", root);
		data.getSlots().add(slot);
		Skin skin = new Skin("default");
		Sequence sequence = new Sequence(1, false);
		sequence.getRegions()[0] = new TextureRegion();
		RegionAttachment region = new RegionAttachment("region", sequence);
		region.setWidth(10f);
		region.setHeight(20f);
		region.updateSequence();
		skin.setAttachment(slot.getIndex(), "region", region);
		slot.setAttachmentName("region");
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.skeleton.setSkin(skin);
		doc.skeleton.setupPoseSlots();
		SkeletonEditor editor = new SkeletonEditor(doc);
		EditHistory history = new EditHistory(doc);

		AttachmentPanel.SlotPropertyState slotBefore = AttachmentPanel.slotPropertyState(slot);
		Color light = new Color(0.2f, 0.4f, 0.6f, 0.8f);
		Color dark = new Color(0.1f, 0.2f, 0.3f, 1f);
		assertTrue(editor.updateSlotProperties(slot, light, dark, BlendMode.multiply, false),
			"slot property edit succeeds before recording history");
		AttachmentPanel.recordSlotPropertyChange(history, editor, slot, slotBefore);
		assertEquals("编辑槽属性", history.undoLabel(), "slot property edit receives an undo label");
		assertTrue(history.undo(), "slot property edit can be undone");
		assertNear(1f, slot.getSetupPose().getColor().r, "slot undo restores light color");
		assertTrue(slot.getSetupPose().getDarkColor() == null && slot.getBlendMode() == BlendMode.normal
			&& slot.getVisible(), "slot undo restores two-color, blend, and visibility state");
		assertTrue(!doc.dirty, "slot undo returns to the initial savepoint");
		assertTrue(history.redo(), "slot property edit can be redone");
		assertNear(0.4f, slot.getSetupPose().getColor().g, "slot redo restores light color");
		assertTrue(slot.getSetupPose().getDarkColor() != null && slot.getBlendMode() == BlendMode.multiply
			&& !slot.getVisible(), "slot redo restores the complete edited state");

		AttachmentPanel.RegionPropertyState regionBefore = AttachmentPanel.regionPropertyState(region);
		assertTrue(editor.updateRegionAttachment(region, new Color(0.3f, 0.5f, 0.7f, 0.9f),
			5f, -6f, 1.5f, -2f, 45f, 30f, 40f), "region property edit succeeds before recording history");
		AttachmentPanel.recordRegionPropertyChange(history, editor, region, regionBefore);
		assertEquals("编辑附件属性", history.undoLabel(), "region property edit receives an undo label");
		assertTrue(history.undo(), "region property edit can be undone");
		assertNear(0f, region.getX(), "region undo restores X");
		assertNear(10f, region.getWidth(), "region undo restores width");
		assertNear(1f, region.getColor().r, "region undo restores tint");
		assertTrue(history.redo(), "region property edit can be redone");
		assertNear(5f, region.getX(), "region redo restores X");
		assertNear(40f, region.getHeight(), "region redo restores height");
		assertNear(0.7f, region.getColor().b, "region redo restores tint");
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
			throw new AssertionError(message + ": expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void assertMessage(Runnable action, String expectedMessage, String message) {
		try {
			action.run();
			throw new AssertionError(message + ": expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			if (!expectedMessage.equals(expected.getMessage()))
				throw new AssertionError(message + ": expected " + expectedMessage + ", got " + expected.getMessage());
		}
	}

	private AttachmentPanelTest() {}
}
