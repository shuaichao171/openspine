package com.openspine.view;

/** Plans bounded grid lines in framebuffer space, avoiding float world-coordinate accumulation. */
final class ViewportGrid {
	static final int MAX_LINES_PER_AXIS = 4096;
	static final float PIXEL_GRID_MIN_SPACING = 8f;
	private static final Axis EMPTY_AXIS = new Axis(0d, 1d, 0, 0d, 1d, 0, false);
	static final Plan EMPTY = new Plan(EMPTY_AXIS, EMPTY_AXIS);

	record Axis(double firstIndex, double worldStep, int count, double camera, double worldPerPixel,
		int pixelExtent, boolean flipped) {
		float pixelPosition(int index) {
			if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
			double offset = ((firstIndex + index) * worldStep - camera) / worldPerPixel;
			return (float)(pixelExtent * 0.5d + (flipped ? -offset : offset));
		}
		boolean isOrigin(int index) { return firstIndex + index == 0d; }
	}
	record Plan(Axis x, Axis y) {
		boolean visible() { return x.count() > 0 && y.count() > 0; }
	}

	static Plan plan(float cameraX, float cameraY, float zoom, float width, float height,
		int pixelWidth, int pixelHeight, double worldStep, boolean flipped, float minimumSpacing) {
		if (!Float.isFinite(zoom) || zoom <= 0f || !Float.isFinite(width) || width <= 0f
			|| !Float.isFinite(height) || height <= 0f || pixelWidth <= 0 || pixelHeight <= 0
			|| !Double.isFinite(worldStep) || worldStep <= 0d || !Float.isFinite(minimumSpacing)
			|| minimumSpacing < 0f) return EMPTY;
		double xWorldPerPixel = (double)width * zoom / pixelWidth;
		double yWorldPerPixel = (double)height * zoom / pixelHeight;
		if (worldStep / xWorldPerPixel < minimumSpacing || worldStep / yWorldPerPixel < minimumSpacing) return EMPTY;
		return new Plan(axis(cameraX, xWorldPerPixel, pixelWidth, worldStep, flipped),
			axis(cameraY, yWorldPerPixel, pixelHeight, worldStep, false));
	}

	private static Axis axis(float camera, double worldPerPixel, int pixelExtent, double step, boolean flipped) {
		if (!Float.isFinite(camera) || !Double.isFinite(worldPerPixel) || worldPerPixel <= 0d) return EMPTY_AXIS;
		double half = worldPerPixel * pixelExtent * 0.5d;
		double first = Math.ceil((camera - half) / step), last = Math.floor((camera + half) / step);
		// Beyond exact integer indices, adding one can stop advancing even in double precision.
		if (!Double.isFinite(first) || !Double.isFinite(last) || Math.ulp(first) > 1d || Math.ulp(last) > 1d) return EMPTY_AXIS;
		double count = last - first + 1d;
		if (count <= 0d || count > MAX_LINES_PER_AXIS) return EMPTY_AXIS;
		return new Axis(first, step, (int)count, camera, worldPerPixel, pixelExtent, flipped);
	}

	static double backgroundStep(float zoom) {
		if (!Float.isFinite(zoom) || zoom <= 0f) return Double.NaN;
		double step = 40d;
		// A finite positive float needs fewer than 256 binary scaling steps.
		for (int iteration = 0; iteration < 256 && step / zoom < 22d; iteration++) step *= 2d;
		for (int iteration = 0; iteration < 256 && step / zoom > 66d; iteration++) step *= 0.5d;
		return step;
	}
}
