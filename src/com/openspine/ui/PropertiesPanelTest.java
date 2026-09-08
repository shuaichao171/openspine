package com.openspine.ui;

import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.openspine.model.EditHistory;
import com.openspine.model.AnimationEditor;
import com.openspine.model.BoneSelectionTransform;
import com.openspine.model.BoneCoordinateSpace;
import com.openspine.model.BoneSelectionTransformTest;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import java.util.List;

/** Headless regression tests for bone property field validation. */
public final class PropertiesPanelTest {
	public static void main(String[] args) {
		appliesInspectorValuesToTheWholeSelection();
		appliesExactInspectorValuesAfterChildFirstSelection();
		worldCoordinatesKeyBothLocalAxes();
		localCoordinatesUseIncrementalScaledOwnAxes();
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.X) == AnimationEditor.Prop.TRANSLATE_X,
			"the X inspector Auto Keys translation X");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.Y) == AnimationEditor.Prop.TRANSLATE_Y,
			"the Y inspector Auto Keys translation Y");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.ROTATION) == AnimationEditor.Prop.ROTATE,
			"the rotation inspector Auto Keys rotation");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.SCALE_X) == AnimationEditor.Prop.SCALE_X,
			"the scale X inspector Auto Keys scale X");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.SCALE_Y) == AnimationEditor.Prop.SCALE_Y,
			"the scale Y inspector Auto Keys scale Y");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.SHEAR_X) == AnimationEditor.Prop.SHEAR_X,
			"the shear X inspector Auto Keys shear X");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.SHEAR_Y) == AnimationEditor.Prop.SHEAR_Y,
			"the shear Y inspector Auto Keys shear Y");
		assertBits(-12.5f, PropertiesPanel.parsePoseValue(" -1.25e1 ", "X"),
			"scientific notation remains editable");
		assertBits(Float.MAX_VALUE, PropertiesPanel.parsePoseValue(Float.toString(Float.MAX_VALUE), "缩放 X"),
			"maximum finite float remains editable");
		assertRejected("", "旋转");
		assertRejected("-", "Y");
		assertRejected("NaN", "剪切 X");
		assertRejected("Infinity", "缩放 Y");
		assertRejected("1e999", "X");
		assertBits(125.5f, PropertiesPanel.parseSetupLength("125.5"), "finite nonnegative bone length");
		assertLengthRejected("-1", "negative bone length");
		assertLengthRejected("NaN", "non-finite bone length");
		preservesDirtyLengthDraftAcrossRefreshes();
		mapsEveryBoneInheritMode();
		preservesDirtySkinRequiredDraftAcrossRefreshes();
		recordsBoneSetupHistoryWithoutLosingThePreviewPose();
		recordsEditorOnlyBoneStateHistory();
		System.out.println("ALL PROPERTIES PANEL TESTS PASSED");
	}

	private static void appliesInspectorValuesToTheWholeSelection() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		var primary = doc.skeleton.findBone("a");
		var second = doc.skeleton.findBone("b");
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		assertTrue(PropertiesPanel.applySelectionValue(edit, PropertiesPanel.Value.ROTATION, 25f),
			"the inspector applies a relative rotation to the full selection");
		assertBits(25f, primary.getPose().getRotation(), "primary field value");
		assertBits(5f, second.getPose().getRotation(), "secondary receives the same rotation delta");
		PropertiesPanel.applySelectionValue(edit, PropertiesPanel.Value.ROTATION, 35f);
		assertBits(15f, second.getPose().getRotation(), "slider samples use their original gesture pose");
		edit.restore();
		assertBits(-5f, second.getPose().getRotation(), "cancel restores secondary inspector targets");
	}

	private static void appliesExactInspectorValuesAfterChildFirstSelection() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		var parent = doc.skeleton.findBone("a");
		var child = doc.skeleton.findBone("child");
		doc.setSelectedBones(List.of(child, parent));
		assertTrue(doc.getSelectedBone() == parent, "the inspector displays the directly transformed ancestor");
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		assertTrue(PropertiesPanel.applySelectionValue(edit, PropertiesPanel.Value.ROTATION, 17f),
			"child-first multi-selection accepts an exact inspector rotation");
		assertBits(17f, doc.getSelectedBone().getPose().getRotation(), "refresh retains the requested primary field value");
		assertBits(7f, child.getPose().getRotation(), "the child inherits rotation without a second local edit");
		edit.restore();
		assertBits(15f, parent.getPose().getRotation(), "cancel restores the promoted primary");
		assertTrue(doc.getSelectedBones().size() == 2, "property editing preserves both selected bones");
	}

	private static void worldCoordinatesKeyBothLocalAxes() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		var bone = doc.skeleton.findBone("b");
		doc.setSelectedBone(bone);
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		var position = edit.primaryPosition(BoneCoordinateSpace.WORLD);
		assertTrue(PropertiesPanel.applySelectionValue(edit, PropertiesPanel.Value.X, position.x + 12f, BoneCoordinateSpace.WORLD),
			"world X field edits the authored world position");
		assertTrue(Math.abs(bone.getAppliedPose().getWorldX() - position.x - 12f) < 0.0001f,
			"world field value matches the resulting world position");
		assertTrue(Math.abs(bone.getAppliedPose().getWorldY() - position.y) < 0.0001f,
			"world X preserves world Y even below a rotated parent");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.X, BoneCoordinateSpace.WORLD, 1) == AnimationEditor.Prop.TRANSLATE,
			"world fields key both authored translation channels");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.X, BoneCoordinateSpace.PARENT, 1) == AnimationEditor.Prop.TRANSLATE_X,
			"parent X fields preserve axis-separated keying");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.X, BoneCoordinateSpace.PARENT, 2) == AnimationEditor.Prop.TRANSLATE,
			"multiple parent spaces key both translation channels");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.ROTATION, BoneCoordinateSpace.WORLD, 2) == AnimationEditor.Prop.ROTATE,
			"move coordinate mode does not change rotation keying");
	}

	private static void recordsEditorOnlyBoneStateHistory() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.setVisible(true);
		data.getBones().add(root);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		BoneData runtimeRoot = doc.skeleton.getRootBone().getData();
		EditHistory history = new EditHistory(doc);
		doc.markSaved();
		SkeletonDocument.BoneEditorState before = doc.boneEditorState(runtimeRoot);
		assertTrue(before.selectable() && !before.nameVisible(),
			"bone editor defaults match Spine's selectable/name-hidden defaults");
		assertTrue(doc.setBoneSelectable(runtimeRoot, false), "selectable flag is editable");
		assertTrue(doc.setBoneNameVisible(runtimeRoot, true), "name visibility flag is editable");
		SkeletonDocument.BoneEditorState after = doc.boneEditorState(runtimeRoot);
		history.record("编辑骨骼视口状态", () -> doc.applyBoneEditorStates(List.of(before)),
			() -> doc.applyBoneEditorStates(List.of(after)));
		assertTrue(!doc.isBoneSelectable(runtimeRoot) && doc.isBoneNameVisible(runtimeRoot),
			"editor-only state remains applied after recording history");
		assertTrue(history.undo(), "editor-only state can be undone");
		assertTrue(doc.isBoneSelectable(runtimeRoot) && !doc.isBoneNameVisible(runtimeRoot),
			"undo restores both selectable and name visibility flags");
		assertTrue(history.redo(), "editor-only state can be redone");
		assertTrue(!doc.isBoneSelectable(runtimeRoot) && doc.isBoneNameVisible(runtimeRoot),
			"redo restores both selectable and name visibility flags");
	}

	private static void localCoordinatesUseIncrementalScaledOwnAxes() {
		SkeletonDocument doc = BoneSelectionTransformTest.document();
		var bone = doc.skeleton.findBone("a");
		var child = doc.skeleton.findBone("child");
		doc.setSelectedBones(List.of(child, bone));
		float beforeX = bone.getAppliedPose().getWorldX(), beforeY = bone.getAppliedPose().getWorldY();
		float axisX = bone.getAppliedPose().getA(), axisY = bone.getAppliedPose().getC();
		BoneSelectionTransform edit = BoneSelectionTransform.capture(doc);
		PropertiesPanel.applySelectionValue(edit, PropertiesPanel.Value.X, 12f, BoneCoordinateSpace.LOCAL);
		PropertiesPanel.applySelectionValue(edit, PropertiesPanel.Value.X, -3f, BoneCoordinateSpace.LOCAL);
		assertTrue(Math.abs(bone.getAppliedPose().getWorldX() - beforeX + axisX * 3f) < 0.0001f
			&& Math.abs(bone.getAppliedPose().getWorldY() - beforeY + axisY * 3f) < 0.0001f,
			"local inspector values are signed increments from the gesture origin including own scale");
		assertBits(5f, child.getPose().getX(), "local inspector does not move a selected child twice");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.X, BoneCoordinateSpace.LOCAL, 1) == AnimationEditor.Prop.TRANSLATE
			&& PropertiesPanel.autoKeyProp(PropertiesPanel.Value.Y, BoneCoordinateSpace.LOCAL, 1) == AnimationEditor.Prop.TRANSLATE,
			"either local axis keys both parent-coordinate translation channels");
		assertTrue(PropertiesPanel.autoKeyProp(PropertiesPanel.Value.ROTATION, BoneCoordinateSpace.LOCAL, 1) == AnimationEditor.Prop.ROTATE,
			"local move axes do not alter rotation semantics");
	}

	private static void preservesDirtySkinRequiredDraftAcrossRefreshes() {
		BoneData first = new BoneData(0, "first", null);
		BoneData second = new BoneData(1, "second", first);
		assertTrue(PropertiesPanel.preserveSkinRequiredDraft(first, first, true, true),
			"an unapplied skin-required draft survives repeated refreshes for the same bone");
		assertTrue(!PropertiesPanel.preserveSkinRequiredDraft(first, first, false, true),
			"a clean draft resynchronizes from the model");
		assertTrue(!PropertiesPanel.preserveSkinRequiredDraft(first, second, true, true),
			"selecting another bone discards the previous bone draft");
		first.setSkinRequired(true);
		assertTrue(!PropertiesPanel.preserveSkinRequiredDraft(first, first, true, true),
			"a draft resynchronizes once the model has accepted its value");
	}

	private static void preservesDirtyLengthDraftAcrossRefreshes() {
		BoneData first = new BoneData(0, "first", null);
		BoneData second = new BoneData(1, "second", first);
		assertTrue(PropertiesPanel.preserveLengthDraft(first, first, true),
			"a dirty length draft survives repeated refreshes for the same bone");
		assertTrue(!PropertiesPanel.preserveLengthDraft(first, first, false),
			"a clean length draft resynchronizes from the model");
		assertTrue(!PropertiesPanel.preserveLengthDraft(first, second, true),
			"selecting another bone discards the previous length draft");
	}

	private static void mapsEveryBoneInheritMode() {
		for (Inherit inherit : Inherit.values()) {
			String label = PropertiesPanel.inheritLabel(inherit);
			assertTrue(label != null && !label.isBlank(), "inherit mode has a visible label: " + inherit);
			assertTrue(PropertiesPanel.inheritMode(label) == inherit,
				"inherit label maps back to the runtime value: " + inherit);
		}
		assertTrue(PropertiesPanel.inheritMode("未知") == null, "unknown inherit labels are rejected");
	}

	private static void recordsBoneSetupHistoryWithoutLosingThePreviewPose() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setScale(1f, 1f);
		root.getSetupPose().setInherit(Inherit.normal);
		data.getBones().add(root);
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.setSelectedBone(doc.skeleton.getRootBone());
		doc.setPoseMode(true);
		assertTrue(PropertiesPanel.setupDraftChanged(doc.getSelectedBone(), doc.poseMode, "0", true),
			"a skin-required-only draft enables the Setup command");
		doc.getSelectedBone().getPose().setX(18f);
		SkeletonEditor editor = new SkeletonEditor(doc);
		EditHistory history = new EditHistory(doc);
		SkeletonEditor.BoneSetupState before = SkeletonEditor.boneSetupState(root);
		doc.getSelectedBone().getPose().setInherit(Inherit.noScale);
		SkeletonEditor.BoneSetupState after = new SkeletonEditor.BoneSetupState(
			SkeletonEditor.bonePoseState(doc.getSelectedBone().getPose()), 64f, true);

		assertTrue(PropertiesPanel.setupDraftChanged(doc.getSelectedBone(), doc.poseMode, "64", true),
			"a changed temporary pose enables the Setup command");
		assertTrue(editor.updateBoneSetup(root, after), "runtime pose and length can be written to Setup");
		PropertiesPanel.recordBoneSetupChange(history, editor, root, before, after);
		assertTrue(!PropertiesPanel.setupDraftChanged(doc.getSelectedBone(), doc.poseMode, "64.0", true),
			"matching Setup disables the command after apply");
		assertTrue(history.undo(), "Setup write can be undone");
		assertBits(0f, root.getSetupPose().getX(), "undo restores previous Setup data");
		assertBits(0f, root.getLength(), "undo restores previous bone length");
		assertTrue(!root.getSkinRequired(), "undo restores previous skin-required state");
		assertTrue(root.getSetupPose().getInherit() == Inherit.normal, "undo restores previous inherit mode");
		assertBits(18f, doc.getSelectedBone().getPose().getX(), "undo preserves the pre-command preview pose");
		assertTrue(PropertiesPanel.setupDraftChanged(doc.getSelectedBone(), doc.poseMode, "64", true),
			"restored Setup differs from the retained preview pose");
		assertTrue(history.redo(), "Setup write can be redone");
		assertBits(18f, root.getSetupPose().getX(), "redo restores the written Setup data");
		assertBits(64f, root.getLength(), "redo restores the written bone length");
		assertTrue(root.getSkinRequired(), "redo restores the written skin-required state");
		assertTrue(root.getSetupPose().getInherit() == Inherit.noScale, "redo restores the written inherit mode");
		assertTrue(doc.dirty, "redone Setup write remains an unsaved document edit");
	}

	private static void assertBits(float expected, float actual, String message) {
		if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertRejected(String text, String label) {
		try {
			PropertiesPanel.parsePoseValue(text, label);
			throw new AssertionError(label + " should reject " + text);
		} catch (IllegalArgumentException expected) {
			String message = label + " 必须是有限数值";
			if (!message.equals(expected.getMessage()))
				throw new AssertionError("unexpected validation message: " + expected.getMessage());
		}
	}

	private static void assertLengthRejected(String text, String message) {
		try {
			PropertiesPanel.parseSetupLength(text);
			throw new AssertionError(message + " should be rejected");
		} catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private PropertiesPanelTest() {}
}
