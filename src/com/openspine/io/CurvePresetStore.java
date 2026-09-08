package com.openspine.io;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent normalized cubic handles for named Graph curve presets. */
public final class CurvePresetStore {
	public record Curve(float cx1, float cy1, float cx2, float cy2) {
		public Curve {
			if (!Float.isFinite(cx1) || !Float.isFinite(cy1) || !Float.isFinite(cx2) || !Float.isFinite(cy2)
				|| cx1 < 0f || cx1 > 1f || cx2 < 0f || cx2 > 1f)
				throw new IllegalArgumentException("曲线预设控制点无效");
		}
	}

	public static String encode(Map<String, Curve> presets) {
		LinkedHashMap<String, Map<String, String>> values = new LinkedHashMap<>();
		if (presets != null) for (Map.Entry<String, Curve> entry : presets.entrySet()) {
			String name = ExportPresetStore.normalizeName(entry.getKey());
			Curve curve = entry.getValue();
			if (name == null || curve == null) continue;
			values.put(name, Map.of("cx1", Float.toString(curve.cx1()), "cy1", Float.toString(curve.cy1()),
				"cx2", Float.toString(curve.cx2()), "cy2", Float.toString(curve.cy2())));
		}
		return ExportPresetStore.encode(values);
	}

	public static LinkedHashMap<String, Curve> decode(String encoded) {
		LinkedHashMap<String, Curve> result = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> entry : ExportPresetStore.decode(encoded).entrySet()) {
			try {
				Map<String, String> value = entry.getValue();
				result.put(entry.getKey(), new Curve(Float.parseFloat(value.get("cx1")),
					Float.parseFloat(value.get("cy1")), Float.parseFloat(value.get("cx2")),
					Float.parseFloat(value.get("cy2"))));
			} catch (RuntimeException ignored) { }
		}
		return result;
	}

	public static LinkedHashMap<String, Curve> defaults() {
		LinkedHashMap<String, Curve> result = new LinkedHashMap<>();
		result.put("缓入缓出", new Curve(0.42f, 0f, 0.58f, 1f));
		result.put("缓入", new Curve(0.42f, 0f, 1f, 1f));
		result.put("缓出", new Curve(0f, 0f, 0.58f, 1f));
		return result;
	}

	private CurvePresetStore() {}
}
