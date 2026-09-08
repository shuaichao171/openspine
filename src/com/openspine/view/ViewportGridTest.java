package com.openspine.view;

/** Grid density, pixel alignment and the large-world-coordinate render hang regression. */
public final class ViewportGridTest {
	public static void main(String[] args) {
		largeCoordinatesCannotStallGridEnumeration();
		pixelThresholdUsesBothFramebufferScales();
		worldPixelBoundariesFollowCameraAndFlip();
		invalidAndOversizedPlansStayBounded();
		System.out.println("ViewportGridTest passed");
	}

	private static void largeCoordinatesCannotStallGridEnumeration() {
		float camera = 10_000_000_000f;
		float oldCursor = (float)Math.floor((camera - 600f) / 40f) * 40f;
		require(oldCursor + 40f == oldCursor, "Regression fixture no longer reproduces the old non-advancing float loop");
		ViewportGrid.Plan plan = ViewportGrid.plan(camera, -camera, 1f, 1200f, 800f, 1200, 800,
			ViewportGrid.backgroundStep(1f), false, 0f);
		require(plan.visible(), "Representable large-coordinate grid was discarded");
		require(plan.x().count() <= 32 && plan.y().count() <= 22, "Large coordinates created excessive lines");
		checkAscending(plan.x(), 40f); checkAscending(plan.y(), 40f);
		ViewportGrid.Plan tiny = ViewportGrid.plan(camera, -camera, 0.05f, 1200f, 800f, 1200, 800, 1d, false, 8f);
		require(tiny.visible(), "Pixel boundaries are representable using camera-relative doubles");
		checkAscending(tiny.x(), 20f); checkAscending(tiny.y(), 20f);
	}

	private static void pixelThresholdUsesBothFramebufferScales() {
		require(!pixels(1f, 1000, 1000).visible(), "Pixel grid crowds the normal zoom viewport");
		require(pixels(0.125f, 1000, 1000).visible(), "Eight framebuffer pixels should show the pixel grid");
		require(!pixels(Math.nextUp(0.125f), 1000, 1000).visible(), "Pixel grid appeared below its exact density threshold");
		require(pixels(0.25f, 2000, 2000).visible(), "High DPI scaling was not included in the threshold");
		require(!pixels(0.25f, 2000, 1000).visible(), "A crowded Y axis must suppress the complete pixel grid");
		require(!pixels(0.25f, 1000, 2000).visible(), "A crowded X axis must suppress the complete pixel grid");
	}

	private static void worldPixelBoundariesFollowCameraAndFlip() {
		ViewportGrid.Plan normal = ViewportGrid.plan(-3.25f, 4.75f, 0.1f, 123f, 77f, 246, 231, 1d, false, 8f);
		ViewportGrid.Plan flipped = ViewportGrid.plan(-3.25f, 4.75f, 0.1f, 123f, 77f, 246, 231, 1d, true, 8f);
		require(normal.visible() && flipped.visible(), "Visible pixel grid was not planned");
		checkAscending(normal.x(), 20f); checkAscending(normal.y(), 30f);
		for (int index = 0; index < normal.x().count(); index++) {
			near(Math.rint(normal.x().firstIndex() + index), normal.x().firstIndex() + index, 0d, "Grid boundary is not a whole project unit");
			near(246f, normal.x().pixelPosition(index) + flipped.x().pixelPosition(index), 0.0001d,
				"Flip does not mirror project pixel boundaries through the viewport center");
		}
		for (int index = 0; index < normal.y().count(); index++)
			near(normal.y().pixelPosition(index), flipped.y().pixelPosition(index), 0d, "Horizontal flip changed Y grid boundaries");
		ViewportGrid.Plan shifted = ViewportGrid.plan(-2.75f, 4.75f, 0.1f, 123f, 77f, 246, 231, 1d, false, 8f);
		int originalZero = (int)-normal.x().firstIndex(), shiftedZero = (int)-shifted.x().firstIndex();
		near(-10f, shifted.x().pixelPosition(shiftedZero) - normal.x().pixelPosition(originalZero), 0.0001d,
			"A half-project-pixel pan must move the grid by ten framebuffer pixels");
	}

	private static void invalidAndOversizedPlansStayBounded() {
		for (float zoom : new float[] {Float.MIN_VALUE, 0.05f, 0.3f, 1f, 8f, Float.MAX_VALUE}) {
			double step = ViewportGrid.backgroundStep(zoom);
			require(Double.isFinite(step) && step / zoom >= 22d && step / zoom <= 66d, "Invalid adaptive background spacing");
			ViewportGrid.Plan plan = ViewportGrid.plan(0f, 0f, zoom, 1920f, 1080f, 1920, 1080, step, false, 0f);
			require(plan.visible() && plan.x().count() < 100 && plan.y().count() < 60, "Background grid lost its density bound");
		}
		for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f})
			require(!pixels(invalid, 1000, 1000).visible(), "Invalid zoom generated pixel lines");
		require(!ViewportGrid.plan(Float.MAX_VALUE, Float.MAX_VALUE, 0.05f, 1000f, 1000f, 1000, 1000, 1d, false, 8f).visible(),
			"Unresolvable world pixel indices generated duplicate lines");
		require(!ViewportGrid.plan(0f, 0f, 1f, 1_000_000f, 1_000_000f, 1_000_000, 1_000_000, 1d, false, 0f).visible(),
			"A malformed oversized viewport bypassed the line count cap");
	}

	private static ViewportGrid.Plan pixels(float zoom, int pixelWidth, int pixelHeight) {
		return ViewportGrid.plan(0f, 0f, zoom, 1000f, 1000f, pixelWidth, pixelHeight, 1d, false, 8f);
	}
	private static void checkAscending(ViewportGrid.Axis axis, float spacing) {
		require(axis.count() > 1, "Grid fixture has too few lines");
		for (int index = 1; index < axis.count(); index++)
			near(spacing, axis.pixelPosition(index) - axis.pixelPosition(index - 1), 0.002d, "Grid enumeration stalled or drifted");
	}
	private static void near(double expected, double actual, double tolerance, String message) {
		if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
