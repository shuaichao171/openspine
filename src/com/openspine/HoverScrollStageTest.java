package com.openspine;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;

/** Headless regression tests for pointer-based wheel routing. */
public final class HoverScrollStageTest {
	public static void main(String[] args) {
		Group root = new Group();
		root.setSize(300f, 200f);
		root.setTouchable(Touchable.childrenOnly);
		Actor leftPanel = actor(0f, 0f, 100f, 200f);
		Actor rightPanel = actor(200f, 0f, 100f, 200f);
		root.addActor(leftPanel);
		root.addActor(rightPanel);

		assertSame(leftPanel, HoverScrollStage.scrollTargetAt(root, 50f, 100f),
			"the wheel targets the panel under the pointer");
		assertSame(rightPanel, HoverScrollStage.scrollTargetAt(root, 250f, 100f),
			"moving the pointer changes the wheel target without a click");
		assertSame(null, HoverScrollStage.scrollTargetAt(root, 150f, 100f),
			"an empty workspace region does not retain an old scroll target");

		Actor overlay = actor(220f, 50f, 60f, 100f);
		root.addActor(overlay);
		assertSame(overlay, HoverScrollStage.scrollTargetAt(root, 250f, 100f),
			"the topmost hovered actor receives the wheel event");
		Group viewport = new Group();
		Actor viewportChild = new Actor();
		viewport.addActor(viewportChild);
		assertTrue(HoverScrollStage.targetIsWithin(viewport, viewport),
			"the viewport itself is a valid viewport wheel target");
		assertTrue(HoverScrollStage.targetIsWithin(viewportChild, viewport),
			"viewport descendants retain viewport wheel handling");
		assertTrue(!HoverScrollStage.targetIsWithin(overlay, viewport),
			"an overlay above the viewport prevents the covered viewport from zooming");
		assertSame(null, HoverScrollStage.scrollTargetAt(root, Float.NaN, 0f),
			"invalid pointer coordinates clear the wheel target");

		Group leftScroll = group(0f, 0f, 100f, 200f);
		Actor leftContent = actor(0f, 0f, 100f, 200f);
		leftScroll.addActor(leftContent);
		Group rightScroll = group(200f, 0f, 100f, 200f);
		Actor rightContent = actor(0f, 0f, 100f, 200f);
		rightScroll.addActor(rightContent);
		Group scrollRoot = group(0f, 0f, 300f, 200f);
		scrollRoot.addActor(leftScroll);
		scrollRoot.addActor(rightScroll);
		int[] scrollCounts = {0, 0};
		leftScroll.addListener(scrollListener(scrollCounts, 0));
		rightScroll.addListener(scrollListener(scrollCounts, 1));
		Actor selectedPanel = leftContent;
		Actor hoveredPanel = HoverScrollStage.scrollTargetAt(scrollRoot, 250f, 100f);
		assertTrue(selectedPanel != hoveredPanel, "the selected and hovered panels differ for this regression case");
		assertTrue(HoverScrollStage.fireScrollEvent(hoveredPanel, new InputEvent(), null,
			250f, 100f, 0f, 1f), "the hovered panel handles the wheel event");
		assertEquals(0, scrollCounts[0], "the previously selected panel does not scroll");
		assertEquals(1, scrollCounts[1], "the panel under the pointer scrolls");
		System.out.println("ALL HOVER SCROLL STAGE TESTS PASSED");
	}

	private static Group group(float x, float y, float width, float height) {
		Group group = new Group();
		group.setBounds(x, y, width, height);
		return group;
	}

	private static Actor actor(float x, float y, float width, float height) {
		Actor actor = new Actor();
		actor.setBounds(x, y, width, height);
		return actor;
	}

	private static InputListener scrollListener(int[] counts, int index) {
		return new InputListener() {
			@Override public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
				counts[index]++;
				return true;
			}
		};
	}

	private static void assertSame(Object expected, Object actual, String message) {
		if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private HoverScrollStageTest() {}
}
