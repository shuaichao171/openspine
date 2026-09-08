package com.openspine.io;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Headless regression coverage for persistent Graph curve presets. */
public final class CurvePresetStoreTest {
	public static void main(String[] args) {
		LinkedHashMap<String, CurvePresetStore.Curve> source = new LinkedHashMap<>();
		source.put("柔和", new CurvePresetStore.Curve(0.25f, -0.2f, 0.75f, 1.2f));
		source.put("快速", new CurvePresetStore.Curve(0.1f, 0f, 0.9f, 1f));
		String encoded = CurvePresetStore.encode(source);
		Map<String, CurvePresetStore.Curve> decoded = CurvePresetStore.decode(encoded);
		assertEquals(source, decoded, "curve handles survive encoding and decoding");
		assertEquals(List.of("柔和", "快速"), List.copyOf(decoded.keySet()),
			"curve preset order is stable");

		String invalid = ExportPresetStore.encode(Map.of(
			"valid", Map.of("cx1", "0.2", "cy1", "0", "cx2", "0.8", "cy2", "1"),
			"bad-x", Map.of("cx1", "2", "cy1", "0", "cx2", "0.8", "cy2", "1"),
			"bad-number", Map.of("cx1", "x", "cy1", "0", "cx2", "0.8", "cy2", "1")));
		Map<String, CurvePresetStore.Curve> filtered = CurvePresetStore.decode(invalid);
		assertTrue(filtered.size() == 1 && filtered.containsKey("valid"),
			"invalid preset entries are ignored independently");
		assertTrue(CurvePresetStore.decode("not-a-preset").isEmpty(),
			"invalid storage payload is ignored");

		Map<String, CurvePresetStore.Curve> defaults = CurvePresetStore.defaults();
		assertEquals(List.of("缓入缓出", "缓入", "缓出"), List.copyOf(defaults.keySet()),
			"built-in curve presets and their default order are stable");
		assertThrows(() -> new CurvePresetStore.Curve(-0.1f, 0f, 0.8f, 1f),
			"control point X outside the normalized time range is rejected");
		assertThrows(() -> new CurvePresetStore.Curve(0.1f, Float.NaN, 0.8f, 1f),
			"non-finite curve handles are rejected");
		System.out.println("CURVE PRESET STORE TEST PASSED");
	}

	private static void assertThrows(Runnable action, String message) {
		try { action.run(); }
		catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private CurvePresetStoreTest() {}
}
