package com.openspine.ui;

/** Headless regression tests for Spine-style Graph axis ticks and labels. */
public final class GraphEditorDialogTest {
	public static void main(String[] args) {
		preservesUneditedSubframeTimes();
		resolvesHistoryReplacementsWithoutCrossingTargets();
		assertNear(30d, GraphEditorDialog.frameAtTime(1d, 30f), "one second at 30 FPS");
		assertNear(30d, GraphEditorDialog.frameAtTime(0.5d, 60f), "half a second at 60 FPS");
		assertNear(30d, GraphEditorDialog.frameAtTime(1d, 0f), "invalid FPS uses the project default");

		assertNear(10d, GraphEditorDialog.niceTickStep(110d, 11), "110 frames use ten-frame ticks");
		assertNear(2d, GraphEditorDialog.niceTickStep(7d, 6), "small spans use two-frame ticks");
		assertNear(0.1d, GraphEditorDialog.niceTickStep(0.7d, 7), "fractional value tick");
		assertNear(1d, GraphEditorDialog.niceTickStep(Double.NaN, 5), "invalid spans are bounded");
		assertNear(-10d, GraphEditorDialog.firstTick(-12d, 5d), "first visible negative tick");
		assertNear(15d, GraphEditorDialog.firstTick(12.1d, 5d), "first visible positive tick");

		assertEquals("30", GraphEditorDialog.formatFrameTick(30d), "frame labels omit seconds");
		assertEquals("1k", GraphEditorDialog.formatValueTick(1000d), "thousands are compact");
		assertEquals("1.5k", GraphEditorDialog.formatValueTick(1500d), "fractional thousands are compact");
		assertEquals("999", GraphEditorDialog.formatValueTick(999d), "sub-thousand values remain exact");
		assertEquals("-1.2k", GraphEditorDialog.formatValueTick(-1200d), "negative values remain signed");
		assertEquals("0.25", GraphEditorDialog.formatValueTick(0.25d), "fractional curve values remain visible");
		assertEquals("armR_hand_IK · 移动",
			GraphEditorDialog.normalizeTrackTitle("3 个关键帧 · armR_hand_IK · 移动"),
			"hierarchy track title omits the selection count");
		assertEquals("曲线轨道", GraphEditorDialog.normalizeTrackTitle(" "),
			"blank track names use a stable fallback");
		assertNear(12.5d, GraphEditorDialog.editableFieldValue(false, "not a number", 12.5f),
			"a locked Graph field ignores invalid text and preserves its value");
		assertNear(-3.25d, GraphEditorDialog.editableFieldValue(true, "-3.25", 12.5f),
			"an unlocked Graph field parses the edited value");
		assertThrows(() -> GraphEditorDialog.editableFieldValue(true, "not a number", 12.5f),
			"an unlocked Graph field still rejects invalid text");
		assertNear(0.25d, GraphEditorDialog.editableHandleCoordinate(false, 0.8f, 0.25f),
			"a locked Graph handle axis preserves its control coordinate");
		assertNear(0.8d, GraphEditorDialog.editableHandleCoordinate(true, 0.8f, 0.25f),
			"an unlocked Graph handle axis follows the pointer");
		assertTrue(GraphEditorDialog.hasOutgoingCurveSelection(1, 0, 2),
			"a selected non-terminal key can store or swap its outgoing curve");
		assertTrue(!GraphEditorDialog.hasOutgoingCurveSelection(0, -1, 2),
			"an empty Graph selection cannot swap the stored curve");
		assertTrue(!GraphEditorDialog.hasOutgoingCurveSelection(1, 1, 2),
			"the terminal key cannot overwrite the stored curve during swap");

		float[] curveBounds = {0f, 1f, 0f, 10f};
		GraphEditorDialog.includeBezierControls(curveBounds, 0f, 0f, 1f, 10f,
			new float[] {0.25f, -10f, 0.75f, 20f});
		assertNear(0d, curveBounds[0], "Bezier controls keep the first key in the time bounds");
		assertNear(1d, curveBounds[1], "Bezier controls keep the last key in the time bounds");
		assertNear(-10d, curveBounds[2], "Graph framing includes an undershooting Bezier handle");
		assertNear(20d, curveBounds[3], "Graph framing includes an overshooting Bezier handle");

		float[] selectedBounds = {1f, 1f, 5f, 5f};
		GraphEditorDialog.includeBezierControl(selectedBounds, 1f, 5f, 3f, 9f,
			new float[] {0.25f, 3f, 0.75f, 11f}, 0);
		assertNear(1.5d, selectedBounds[1], "selection framing includes the outgoing handle time");
		assertNear(3d, selectedBounds[2], "selection framing includes the outgoing handle value");

		float[] denseTimes = {1f, 1.00001f, 1.00002f, 1.00003f};
		GraphEditorDialog.constrainSelectedTimes(denseTimes,
			new boolean[] {false, true, true, false});
		assertFloatEquals(1.00001f, denseTimes[1], "Graph preserves the first legal dense subframe");
		assertFloatEquals(1.00002f, denseTimes[2], "Graph preserves the second legal dense subframe");

		float first = 1f;
		float second = Math.nextUp(first);
		float third = Math.nextUp(second);
		float fourth = Math.nextUp(third);
		float[] collapsedTimes = {first, first, first, fourth};
		GraphEditorDialog.constrainSelectedTimes(collapsedTimes,
			new boolean[] {false, true, true, false});
		assertFloatEquals(second, collapsedTimes[1], "Graph uses the first available float time");
		assertFloatEquals(third, collapsedTimes[2], "Graph uses the next available float time");

		float outgoingY = GraphEditorDialog.outgoingControlValueForSlope(4f, 2f, 8f, 0.25f);
		assertNear(4d, GraphEditorDialog.outgoingSlope(2f, 8f, 0.25f, outgoingY),
			"outgoing tangent preserves the requested slope");
		float incomingY = GraphEditorDialog.incomingControlValueForSlope(-3f, 4f, -6f, 0.75f);
		assertNear(-3d, GraphEditorDialog.incomingSlope(4f, -6f, 0.75f, incomingY),
			"incoming tangent preserves the requested slope");
		float[] returningBounds = {0f, 1f, 4f, 4f};
		GraphEditorDialog.includeBezierControls(returningBounds, 0f, 4f, 1f, 4f,
			new float[] {0.2f, 40f, 0.7f, -8f});
		assertNear(-8d, returningBounds[2], "Graph frames the undershoot when key values match");
		assertNear(40d, returningBounds[3], "Graph frames the overshoot when key values match");
		float joinedSlope = GraphEditorDialog.incomingSlope(1f, 4f, 0.5f, -8f);
		float joinedControl = GraphEditorDialog.outgoingControlValueForSlope(joinedSlope, 2f, 4f, 0.25f);
		assertNear(24d, joinedSlope, "returning curves retain a nonzero incoming slope");
		assertNear(16d, joinedControl, "linked handle retains the slope on a same-value next segment");
		assertNear(joinedSlope, GraphEditorDialog.outgoingSlope(2f, 4f, 0.25f, joinedControl),
			"incoming-to-outgoing linked handles have matching slopes");
		float reverseSlope = GraphEditorDialog.outgoingSlope(2f, 4f, 0.25f, -8f);
		float reverseControl = GraphEditorDialog.incomingControlValueForSlope(reverseSlope, 1f, 4f, 0.5f);
		assertNear(reverseSlope, GraphEditorDialog.incomingSlope(1f, 4f, 0.5f, reverseControl),
			"outgoing-to-incoming linked handles have matching slopes on same-value segments");
		System.out.println("ALL GRAPH EDITOR DIALOG TESTS PASSED");
	}

	private static void preservesUneditedSubframeTimes() {
		float[] original = {0.1234567f, 0.5000123f, 1.234567f};
		boolean[] selected = {true, true, true};
		for (boolean editTime : new boolean[] {false, true}) {
			float[] unchanged = original.clone();
			GraphEditorDialog.sanitizeEditedTimes(original, unchanged, selected, editTime, true, 30f);
			for (int frame = 0; frame < original.length; frame++)
				assertFloatEquals(original[frame], unchanged[frame], "value-only and zero edits preserve subframe " + frame);
		}
		float[] locked = {0.4f, 0.9f, 1.8f};
		GraphEditorDialog.sanitizeEditedTimes(original, locked, selected, false, true, 30f);
		for (int frame = 0; frame < original.length; frame++)
			assertFloatEquals(original[frame], locked[frame], "locked time rejects proposed movement");
		float[] moved = original.clone(); moved[1] += 0.12f;
		GraphEditorDialog.sanitizeEditedTimes(original, moved, selected, true, true, 30f);
		assertFloatEquals(original[0], moved[0], "an unchanged selected anchor is not snapped");
		assertFloatEquals(19f / 30f, moved[1], "an actually moved key still snaps to the project frame");
		assertFloatEquals(original[2], moved[2], "other selected keys retain exact time");
		float[] boundary = original.clone(); boundary[0] = -0.2f; boundary[1] = 2f;
		GraphEditorDialog.sanitizeEditedTimes(original, boundary, selected, true, false, 30f);
		assertTrue(boundary[0] >= 0f && boundary[1] > boundary[0] && boundary[1] < original[2],
			"actual movement still respects zero and untouched neighbor ordering");
	}

	private static void resolvesHistoryReplacementsWithoutCrossingTargets() {
		com.esotericsoftware.spine.Animation animation = new com.esotericsoftware.spine.Animation("live");
		var source = new com.esotericsoftware.spine.Animation.TranslateTimeline(1, 0, 0);
		var restored = new com.esotericsoftware.spine.Animation.TranslateTimeline(1, 0, 0);
		var otherBone = new com.esotericsoftware.spine.Animation.TranslateTimeline(1, 0, 1);
		var otherProperty = new com.esotericsoftware.spine.Animation.ScaleTimeline(1, 0, 0);
		animation.setTimelines(new com.badlogic.gdx.utils.Array<>(new com.esotericsoftware.spine.Animation.Timeline[] {
			otherBone, restored, otherProperty}), new com.badlogic.gdx.utils.IntArray());
		assertTrue(GraphEditorDialog.findLiveTimeline(animation, source) == restored,
			"history replacement resolves by exact track type and runtime properties");
		animation.getTimelines().removeValue(restored, true);
		assertTrue(GraphEditorDialog.findLiveTimeline(animation, source) == null,
			"removed tracks cannot attach to another bone or property");
		animation.getTimelines().add(restored);
		animation.getTimelines().add(new com.esotericsoftware.spine.Animation.TranslateTimeline(1, 0, 0));
		assertTrue(GraphEditorDialog.findLiveTimeline(animation, source) == null,
			"ambiguous duplicate targets are rejected instead of selecting arbitrarily");
		assertTrue(GraphEditorDialog.findLiveTimeline(animation, restored) == restored,
			"an unchanged live timeline retains its exact identity");
	}

	private static void assertNear(double expected, double actual, String message) {
		if (Math.abs(expected - actual) > 0.0000001d)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertFloatEquals(float expected, float actual, String message) {
		if (Float.compare(expected, actual) != 0)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (RuntimeException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private GraphEditorDialogTest() {}
}
