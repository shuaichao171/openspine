package com.openspine.model;

import java.util.List;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;

/** Regression tests for multi-skeleton project ownership and active-document switching. */
public final class SkeletonProjectTest {
	public static void main(String[] args) {
		SkeletonDocument active = new SkeletonDocument();
		SkeletonProject project = new SkeletonProject(active);
		int[] cleanups = {0};
		SkeletonDocument down = document("down", cleanups);
		SkeletonDocument side = document("side", cleanups);
		project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("hero", down),
			new SkeletonProject.LoadedSkeleton("hero", side)));

		assertEquals(2, project.size(), "all imported skeletons stay in the project");
		assertEquals("hero", project.activeName(), "the first imported skeleton becomes active");
		assertEquals("hero (2)", project.nameAt(1), "duplicate skeleton names remain distinguishable");
		assertEquals("down", active.data.getName(), "the stable UI document receives the active content");
		active.markDirty();
		assertTrue(project.switchTo(1), "switching to another skeleton changes the active document");
		assertEquals("side", active.data.getName(), "the target skeleton content becomes active");
		assertTrue(project.skeletons().get(0).dirty(), "inactive skeletons preserve their dirty state");
		assertTrue(project.isDirty(), "inactive edits keep the whole project dirty");
		assertEquals(0, project.firstDirtyIndex(), "the first dirty skeleton can be selected for saving");
		assertEquals(0, cleanups[0], "switching skeletons does not release project resources");
		assertTrue(project.switchTo(0), "switching back succeeds");
		assertTrue(active.dirty, "the active facade restores the skeleton's dirty state");
		assertEquals(2, project.documents().size(), "all live documents are available to the viewport");

		SkeletonDocument up = document("up", cleanups);
		int upIndex = project.add(new SkeletonProject.LoadedSkeleton("up", up), true);
		assertEquals(2, upIndex, "new skeletons append to the current project");
		assertEquals("up", active.data.getName(), "an appended skeleton can become active immediately");
		assertEquals(3, project.size(), "adding a skeleton does not replace existing skeletons");
		assertThrows(() -> project.add(new SkeletonProject.LoadedSkeleton("duplicate", down), false),
			"a document already owned by the project cannot be added twice");
		assertThrows(() -> project.replaceAll(List.of(
			new SkeletonProject.LoadedSkeleton("active", active))),
			"the stable active facade cannot be adopted as a loaded document");
		assertEquals(3, project.size(), "rejected ownership aliases leave the project intact");

		project.close();
		assertEquals(3, cleanups[0], "closing the project releases every skeleton exactly once");
		assertEquals(0, project.size(), "closing clears the project");

		SkeletonDocument active2 = new SkeletonDocument();
		SkeletonProject tabs = new SkeletonProject(active2);
		tabs.replaceAll(List.of(
			new SkeletonProject.LoadedSkeleton("one", document("one", new int[] {0})),
			new SkeletonProject.LoadedSkeleton("two", document("two", new int[] {0})),
			new SkeletonProject.LoadedSkeleton("three", document("three", new int[] {0}))));
		assertEquals(0, tabs.indexOfSourcePath("project.spine"), "duplicate source paths are detected");
		assertTrue(!tabs.hasStructureChanges(), "loaded membership starts at a saved baseline");
		assertTrue(tabs.rename(0, "主角色"), "project member names can differ from runtime filenames");
		assertTrue(tabs.isDirty() && tabs.firstDirtyIndex() == -1, "a name-only change keeps the project unsaved");
		assertThrows(() -> tabs.rename(0, "TWO"), "member names stay unique without changing another member");
		tabs.rename(0, "one");
		assertTrue(!tabs.hasStructureChanges(), "restoring the original name restores the saved membership");
		assertTrue(tabs.move(2, 0), "tabs can be reordered");
		assertTrue(tabs.hasStructureChanges(), "changing only order requires an engineering project save");
		tabs.markStructureSaved();
		assertTrue(!tabs.isDirty(), "saving membership also clears order-only dirty state");
		assertEquals("one", tabs.activeName(), "reordering retains the active tab identity");
		assertTrue(tabs.removeAt(1), "an inactive tab can be closed");
		assertTrue(tabs.isDirty(), "removing a previously saved member marks the remaining project dirty");
		assertEquals(2, tabs.size(), "closing an inactive tab updates the project size");
		assertTrue(tabs.removeAt(0), "the active tab can be closed");
		assertEquals("two", tabs.activeName(), "closing the active tab selects the adjacent tab");
		assertTrue(!tabs.removeAt(0), "the final tab is retained");
		tabs.close();
		System.out.println("ALL SKELETON PROJECT TESTS PASSED");
	}

	private static SkeletonDocument document(String name, int[] cleanups) {
		SkeletonData data = new SkeletonData();
		data.setName(name);
		data.getBones().add(new BoneData(0, name + "-root", null));
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(new TextureAtlas(), data, new Skeleton(data), "project.spine", "", null);
		document.animationState = new AnimationState(new AnimationStateData(data));
		document.ownSourceResources(() -> cleanups[0]++);
		return document;
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private SkeletonProjectTest() {}
}
