package com.openspine.ui;

/** Lossless text formatting for editable float fields. */
public final class UiNumbers {
	private static final float DEFAULT_FPS = 30f;

	public static String format(float value) {
		if (value == 0f) return "0";
		if (Float.isFinite(value) && value == (long)value) return Long.toString((long)value);
		return Float.toString(value);
	}

	/** Parses an editable numeric field without accepting NaN or infinities. */
	public static float parseFinite(String text) {
		if (text == null) throw new NumberFormatException("null");
		float value = Float.parseFloat(text.trim());
		if (!Float.isFinite(value)) throw new NumberFormatException("non-finite value: " + text);
		return value;
	}

	public static float effectiveFps(float fps) {
		return Float.isFinite(fps) && fps > 0f ? fps : DEFAULT_FPS;
	}

	public static float snapTime(float time, float fps) {
		if (!Float.isFinite(time)) return time;
		float resolvedFps = effectiveFps(fps);
		return Math.round(time * resolvedFps) / resolvedFps;
	}

	/** Returns the adjacent project frame, clamped to the animation range. */
	public static float adjacentFrameTime(float time, float duration, float fps, int direction) {
		if (!Float.isFinite(time)) return time;
		float end = Float.isFinite(duration) ? Math.max(0f, duration) : 0f;
		float current = Math.max(0f, Math.min(end, time));
		if (direction == 0 || end == 0f) return current;

		float resolvedFps = effectiveFps(fps);
		float frame = current * resolvedFps;
		float adjacent = direction > 0
			? (float)Math.floor(frame + 0.0001f) + 1f
			: (float)Math.ceil(frame - 0.0001f) - 1f;
		return Math.max(0f, Math.min(end, adjacent / resolvedFps));
	}

	private UiNumbers() {}
}
