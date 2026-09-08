package com.openspine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Routes wheel events through the actor currently under the pointer.
 * <p>
 * Unlike the default {@link Stage} behavior, wheel events are dispatched by pointer position instead of the
 * stage scroll focus. Scroll focus is intentionally never set, so {@link Stage#getScrollFocus()} stays
 * {@code null} and must not be used for wheel routing while this stage is active. When no actor is under
 * the pointer, events fall back to the stage root so global scroll listeners still run. */
final class HoverScrollStage extends Stage {
	private final Vector2 scrollPointer = new Vector2();

	HoverScrollStage(Viewport viewport) {
		super(viewport);
	}

	@Override
	public boolean scrolled(float amountX, float amountY) {
		if (Gdx.input == null) return super.scrolled(amountX, amountY);
		scrollPointer.set(Gdx.input.getX(), Gdx.input.getY());
		screenToStageCoordinates(scrollPointer);
		Actor hovered = scrollTargetAt(getRoot(), scrollPointer.x, scrollPointer.y);
		if (hovered == null) hovered = getRoot();
		InputEvent event = pools.obtain(InputEvent.class);
		try {
			return fireScrollEvent(hovered, event, this, scrollPointer.x, scrollPointer.y, amountX, amountY);
		} finally {
			pools.free(event);
		}
	}

	static boolean fireScrollEvent(Actor target, InputEvent event, Stage stage,
		float stageX, float stageY, float amountX, float amountY) {
		if (target == null || event == null) return false;
		event.setType(InputEvent.Type.scrolled);
		event.setStage(stage);
		event.setStageX(stageX);
		event.setStageY(stageY);
		event.setScrollAmountX(amountX);
		event.setScrollAmountY(amountY);
		target.fire(event);
		return event.isHandled();
	}

	static Actor scrollTargetAt(Group root, float stageX, float stageY) {
		if (root == null || !Float.isFinite(stageX) || !Float.isFinite(stageY)) return null;
		return root.hit(stageX, stageY, true);
	}

	static boolean targetIsWithin(Actor target, Actor owner) {
		return target != null && owner != null && (target == owner || target.isDescendantOf(owner));
	}
}
