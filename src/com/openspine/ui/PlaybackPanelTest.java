package com.openspine.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;

/** Headless regression tests for playback control formatting and bounds. */
public final class PlaybackPanelTest {
	public static void main(String[] args) {
		suppressesProgrammaticSliderEvents();
		restoresUncommittedFrameTextOnFocusLoss();
		assertEquals(0, PlaybackPanel.DEFAULT_FRAME, "frame reset returns to the animation start");
		assertNear(46f, PlaybackPanel.FRAME_FIELD_WIDTH, "frame input fits four digits");
		assertNear(1f, PlaybackPanel.DEFAULT_PLAYBACK_SPEED, "normal playback speed remains the reset target");
		assertNear(100f, PlaybackPanel.RATE_CONTROL_SLIDER_WIDTH,
			"speed and timeline zoom sliders retain their stable alignment width");
		assertNear(0f, PlaybackPanel.normalizedPlaybackSpeed(-1f), "negative speed clamps to zero");
		assertNear(1.25f, PlaybackPanel.normalizedPlaybackSpeed(1.25f), "valid speed is preserved");
		assertNear(3f, PlaybackPanel.normalizedPlaybackSpeed(8f), "excessive speed clamps to maximum");
		assertNear(1f, PlaybackPanel.normalizedPlaybackSpeed(Float.NaN), "invalid speed uses normal speed");
		assertEquals("速度: 1.25×", PlaybackPanel.playbackSpeedLabel(1.25f), "locale-stable speed label");
		assertEquals(42, PlaybackPanel.parseFrame(" 42 "), "frame input is trimmed and parsed");
		assertTrue(PlaybackPanel.parseFrame("") == null, "blank frame input is rejected");
		assertTrue(PlaybackPanel.parseFrame("1.5") == null, "fractional frame input is rejected");
		assertTrue(PlaybackPanel.parseFrame("999999999999") == null, "overflowing frame input is rejected");
		assertEquals(30, PlaybackPanel.frameAtTime(1f, 30f), "aligned animation end frame");
		assertEquals(32, PlaybackPanel.frameAtTime(1.05f, 30f), "half-frame animation end rounds stably");
		assertEquals(31, PlaybackPanel.maximumFrame(1.01f, 30f), "partial final frame remains addressable");
		assertEquals(1, PlaybackPanel.maximumFrame(0.01f, 30f), "short animation end remains addressable");
		assertEquals(31, PlaybackPanel.displayFrame(1.01f, 1.01f, 30f), "animation end displays its final frame");
		assertEquals(15, PlaybackPanel.displayFrame(0.5f, 1.01f, 30f), "interior time displays nearest frame");
		assertNear(31f / 30f, PlaybackPanel.timeForFrame(31, 1.05f, 30f), "regular frame time");
		assertNear(1.05f, PlaybackPanel.timeForFrame(32, 1.05f, 30f), "last frame clamps to animation end");
		assertNear(1f / 30f, PlaybackPanel.timeForFrame(1, 1f, 0f), "invalid FPS uses project default");
		System.out.println("ALL PLAYBACK PANEL TESTS PASSED");
	}

	private static void restoresUncommittedFrameTextOnFocusLoss() {
		int[] restores = {0};
		FocusListener listener = PlaybackPanel.restoreFrameTextOnFocusLost(() -> restores[0]++);
		listener.keyboardFocusChanged(new FocusListener.FocusEvent(), new Actor(), true);
		assertEquals(0, restores[0], "gaining frame input focus preserves the editable value");
		listener.keyboardFocusChanged(new FocusListener.FocusEvent(), new Actor(), false);
		assertEquals(1, restores[0], "losing frame input focus restores the document frame");
	}

	private static void suppressesProgrammaticSliderEvents() {
		PlaybackPanel.UpdateGuard updates = new PlaybackPanel.UpdateGuard();
		Slider slider = new Slider(0f, 1f, 0.1f, false, new SliderStyle());
		int[] userChanges = {0};
		slider.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updates.active()) userChanges[0]++;
			}
		});

		updates.run(() -> {
			slider.setValue(0.5f);
			updates.run(() -> slider.setRange(0f, 0.4f));
		});
		assertTrue(userChanges[0] == 0 && !updates.active(),
			"nested programmatic slider synchronization is ignored and restores the guard");
		slider.setValue(0.3f);
		assertTrue(userChanges[0] == 1, "an unguarded slider change is treated as user input");

		try {
			updates.run(() -> { throw new IllegalStateException("expected"); });
		} catch (IllegalStateException expected) {
			assertTrue(!updates.active(), "programmatic update guard is restored after failure");
		}
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(int expected, Integer actual, String message) {
		if (actual == null || expected != actual)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private PlaybackPanelTest() {}
}
