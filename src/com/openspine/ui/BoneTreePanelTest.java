package com.openspine.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;

/** Headless regression tests for bone-tree keyboard routing. */
public final class BoneTreePanelTest {
	public static void main(String[] args) {
		routesWheelByModifier();
		assertEquals(BoneTreePanel.KeyAction.PREVIOUS, BoneTreePanel.keyAction(Input.Keys.UP), "Up selects previous");
		assertEquals(BoneTreePanel.KeyAction.NEXT, BoneTreePanel.keyAction(Input.Keys.DOWN), "Down selects next");
		assertEquals(BoneTreePanel.KeyAction.COLLAPSE_OR_PARENT, BoneTreePanel.keyAction(Input.Keys.LEFT),
			"Left collapses or selects parent");
		assertEquals(BoneTreePanel.KeyAction.EXPAND_OR_CHILD, BoneTreePanel.keyAction(Input.Keys.RIGHT),
			"Right expands or selects child");
		assertEquals(BoneTreePanel.KeyAction.RENAME, BoneTreePanel.keyAction(Input.Keys.F2), "F2 renames");
		assertEquals(BoneTreePanel.KeyAction.DELETE, BoneTreePanel.keyAction(Input.Keys.DEL), "Delete is routed");
		assertEquals(BoneTreePanel.KeyAction.CLEAR_FILTER, BoneTreePanel.keyAction(Input.Keys.ESCAPE),
			"Escape clears the filter");
		assertEquals(BoneTreePanel.KeyAction.NONE, BoneTreePanel.keyAction(Input.Keys.SPACE),
			"unrelated keys are ignored");
		assertEquals(BoneTreePanel.SearchKeyAction.CLEAR_FILTER,
			BoneTreePanel.searchKeyAction(Input.Keys.ESCAPE, true), "Escape clears a populated bone filter");
		assertEquals(BoneTreePanel.SearchKeyAction.FOCUS_TREE,
			BoneTreePanel.searchKeyAction(Input.Keys.ESCAPE, false), "Escape leaves an empty bone filter");
		assertEquals(BoneTreePanel.SearchKeyAction.FOCUS_TREE,
			BoneTreePanel.searchKeyAction(Input.Keys.ENTER, true), "Enter returns focus to the bone tree");
		assertEquals(BoneTreePanel.SearchKeyAction.FOCUS_TREE,
			BoneTreePanel.searchKeyAction(Input.Keys.NUMPAD_ENTER, true), "numpad Enter returns focus to the bone tree");
		assertEquals(BoneTreePanel.SearchKeyAction.SELECT_NEXT,
			BoneTreePanel.searchKeyAction(Input.Keys.DOWN, true), "Down enters tree navigation at the next node");
		assertEquals(BoneTreePanel.SearchKeyAction.NONE,
			BoneTreePanel.searchKeyAction(Input.Keys.A, true), "typing remains in the bone filter");
		assertEquals(-1, BoneTreePanel.moveSelection(0, 1, 0), "empty tree has no selection");
		assertEquals(0, BoneTreePanel.moveSelection(-1, 1, 3), "Down starts at first node");
		assertEquals(2, BoneTreePanel.moveSelection(-1, -1, 3), "Up starts at last node");
		assertEquals(2, BoneTreePanel.moveSelection(2, 1, 3), "Down stops at the last node");
		assertEquals(0, BoneTreePanel.moveSelection(0, -1, 3), "Up stops at the first node");
		assertTrue(BoneTreePanel.shouldExpandNode(true, true, true),
			"filtered trees expand known nodes so matches remain visible");
		assertTrue(!BoneTreePanel.shouldExpandNode(false, true, true),
			"known nodes defer to their saved expanded-value snapshot");
		assertTrue(BoneTreePanel.shouldExpandNode(false, true, false),
			"new hierarchy nodes expand after a structural rebuild");
		assertTrue(BoneTreePanel.shouldExpandNode(false, false, false),
			"a newly loaded tree starts expanded");
		assertTrue(BoneTreePanel.isVisibilityColumnHit(0f), "visibility column includes its left edge");
		assertTrue(BoneTreePanel.isVisibilityColumnHit(BoneTreePanel.VISIBILITY_COLUMN_WIDTH - 0.01f),
			"visibility column includes positions before its right edge");
		assertTrue(!BoneTreePanel.isVisibilityColumnHit(BoneTreePanel.VISIBILITY_COLUMN_WIDTH),
			"visibility column excludes its right edge");
		assertTrue(!BoneTreePanel.isVisibilityColumnHit(-1f), "visibility column rejects negative positions");
		SkeletonDocument visibilityDoc = new SkeletonDocument();
		BoneData visibilityData = new BoneData(0, "visibility", null);
		visibilityData.setVisible(true);
		EditHistory visibilityHistory = new EditHistory(visibilityDoc);
		assertTrue(BoneTreePanel.setBoneVisible(visibilityDoc, visibilityHistory, visibilityData, false),
			"visibility edit is applied");
		assertTrue(!visibilityData.getVisible() && visibilityDoc.dirty,
			"visibility edit hides the bone and marks the document dirty");
		assertTrue(visibilityHistory.undo() && visibilityData.getVisible() && !visibilityDoc.dirty,
			"visibility undo restores the bone and saved document state");
		assertTrue(visibilityHistory.redo() && !visibilityData.getVisible() && visibilityDoc.dirty,
			"visibility redo reapplies the hidden state");
		assertTrue(!BoneTreePanel.setBoneVisible(visibilityDoc, visibilityHistory, visibilityData, false),
			"requesting the current visibility does not create another edit");
		assertTrue(visibilityHistory.undo() && visibilityData.getVisible(),
			"a no-op visibility request leaves the previous undo entry intact");
		SkeletonDocument directDoc = new SkeletonDocument();
		BoneData directData = new BoneData(0, "direct", null);
		directData.setVisible(true);
		assertTrue(BoneTreePanel.setBoneVisible(directDoc, null, directData, false) && directDoc.dirty,
			"visibility changes without history still mark the document dirty");
		SkeletonDocument slotDoc = new SkeletonDocument();
		SlotData slotVisibility = new SlotData(0, "slot-visibility", directData);
		slotVisibility.setVisible(true);
		EditHistory slotHistory = new EditHistory(slotDoc);
		assertTrue(BoneTreePanel.setSlotVisible(slotDoc, slotHistory, slotVisibility, false),
			"slot visibility edit is applied");
		assertTrue(!slotVisibility.getVisible() && slotHistory.undo() && slotVisibility.getVisible(),
			"slot visibility is reversible");

		SkeletonData subtreeData = new SkeletonData();
		BoneData subtreeRoot = new BoneData(0, "subtree-root", null);
		BoneData subtreeChild = new BoneData(1, "subtree-child", subtreeRoot);
		BoneData separateRoot = new BoneData(2, "separate-root", null);
		subtreeRoot.setVisible(true);
		subtreeChild.setVisible(true);
		separateRoot.setVisible(true);
		subtreeData.getBones().add(subtreeRoot);
		subtreeData.getBones().add(subtreeChild);
		subtreeData.getBones().add(separateRoot);
		SlotData childSlot = new SlotData(0, "child-slot", subtreeChild);
		SlotData separateSlot = new SlotData(1, "separate-slot", separateRoot);
		childSlot.setVisible(true);
		separateSlot.setVisible(true);
		subtreeData.getSlots().add(childSlot);
		subtreeData.getSlots().add(separateSlot);
		SkeletonDocument subtreeDoc = new SkeletonDocument();
		EditHistory subtreeHistory = new EditHistory(subtreeDoc);
		assertTrue(BoneTreePanel.setBoneSubtreeVisible(subtreeDoc, subtreeHistory, subtreeData,
			subtreeRoot, false), "right-click visibility hides a complete bone subtree");
		assertTrue(!subtreeRoot.getVisible() && !subtreeChild.getVisible() && !childSlot.getVisible(),
			"bone subtree visibility includes child bones and their slots");
		assertTrue(separateRoot.getVisible() && separateSlot.getVisible(),
			"bone subtree visibility does not affect unrelated roots");
		assertTrue(subtreeHistory.undo() && subtreeRoot.getVisible() && subtreeChild.getVisible()
			&& childSlot.getVisible(), "subtree visibility undo restores every affected item");
		assertTrue(subtreeHistory.redo() && !subtreeRoot.getVisible() && !subtreeChild.getVisible()
			&& !childSlot.getVisible(), "subtree visibility redo reapplies every affected item");
		assertTrue(BoneTreePanel.setAllVisible(subtreeDoc, subtreeHistory, subtreeData),
			"show-all restores hidden bones and slots");
		assertTrue(subtreeRoot.getVisible() && subtreeChild.getVisible() && childSlot.getVisible(),
			"show-all matches Spine's Ctrl+H visibility behavior");
		SkeletonData data = new SkeletonData();
		BoneData rootData = new BoneData(0, "root", null);
		BoneData armData = new BoneData(1, "arm", rootData);
		BoneData handData = new BoneData(2, "hand", armData);
		data.getBones().add(rootData);
		data.getBones().add(armData);
		data.getBones().add(handData);
		Array<Bone> bones = new Skeleton(data).getBones();
		Skeleton firstSkeleton = new Skeleton(data);
		Skeleton replacementSkeleton = new Skeleton(data);
		assertTrue(!BoneTreePanel.shouldRebuildForSkeleton(firstSkeleton, firstSkeleton),
			"pose-only history keeps tree nodes bound to the same runtime skeleton");
		assertTrue(BoneTreePanel.shouldRebuildForSkeleton(firstSkeleton, replacementSkeleton),
			"data edits that replace the runtime skeleton rebuild stale tree nodes");
		java.util.Set<Bone> handPath = BoneTreePanel.visibleBones(bones, "HAND");
		assertEquals(3, handPath.size(), "a descendant match retains its complete ancestor path");
		assertTrue(handPath.contains(bones.get(0)) && handPath.contains(bones.get(2)),
			"case-insensitive filtering includes both the match and root context");
		assertEquals(0, BoneTreePanel.visibleBones(bones, "missing").size(),
			"an unmatched query produces an empty tree");
		assertEquals(3, BoneTreePanel.visibleBones(bones, "").size(),
			"an empty query keeps every bone");
		IkConstraintData ik = new IkConstraintData("arm-ik");
		ik.getBones().add(armData);
		ik.getBones().add(handData);
		ik.setTarget(rootData);
		assertEquals(handData, BoneTreePanel.constraintBone(ik, data),
			"IK tree selection highlights the final constrained bone");
		assertEquals("IK", BoneTreePanel.constraintType(ik), "IK constraints use their Spine type label");
		Array<BoneTreePanel.ConstraintRelation> ikRelations = BoneTreePanel.constraintRelations(ik);
		assertEquals(3, ikRelations.size, "IK relationship tree lists every constrained bone and target");
		assertTrue(ikRelations.get(0).label().contains("arm")
			&& ikRelations.get(2).label().contains("root"),
			"IK relationship labels distinguish constrained and target bones");
		TransformConstraintData transform = new TransformConstraintData("copy");
		transform.getBones().add(armData);
		transform.setSource(rootData);
		assertEquals(armData, BoneTreePanel.constraintBone(transform, data),
			"Transform tree selection highlights its constrained bone");
		assertEquals(2, BoneTreePanel.constraintRelations(transform).size,
			"Transform relationship tree includes target and source bones");
		PhysicsConstraintData physics = new PhysicsConstraintData("spring");
		physics.setBone(handData);
		assertEquals(handData, BoneTreePanel.constraintBone(physics, data),
			"Physics tree selection highlights its simulated bone");
		SliderData slider = new SliderData("time-slider");
		assertEquals(rootData, BoneTreePanel.constraintBone(slider, data),
			"time-driven Slider constraints fall back to the root bone for viewport selection");
		assertEquals(0, BoneTreePanel.constraintRelations(slider).size,
			"time-only Slider has no invented property-source relationship");
		Animation driven = new Animation("driven");
		IntArray drivenBones = new IntArray();
		drivenBones.add(handData.getIndex());
		driven.setTimelines(Array.of(Animation.Timeline[]::new), drivenBones);
		slider.setAnimation(driven);
		Array<BoneTreePanel.ConstraintRelation> sliderRelations =
			BoneTreePanel.constraintRelations(slider, data);
		assertEquals(2, sliderRelations.size,
			"Slider relationship tree includes its target animation and affected bones");
		assertTrue(sliderRelations.get(0).label().contains("driven")
			&& sliderRelations.get(1).label().contains("hand"),
			"Slider relationship labels expose the target animation dependency");
		System.out.println("ALL BONE TREE PANEL TESTS PASSED");
	}

	private static void routesWheelByModifier() {
		assertEquals(BoneTreePanel.WheelAxis.VERTICAL, BoneTreePanel.wheelAxis(false),
			"plain wheel input scrolls the hierarchy vertically");
		assertEquals(BoneTreePanel.WheelAxis.HORIZONTAL, BoneTreePanel.wheelAxis(true),
			"Shift-wheel input scrolls the hierarchy horizontally");
		assertNear(2f, BoneTreePanel.wheelAmount(7f, 2f),
			"vertical wheel input takes priority when both axes are reported");
		assertNear(7f, BoneTreePanel.wheelAmount(7f, 0f),
			"horizontal-only wheel devices remain supported");
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private BoneTreePanelTest() {}
}
