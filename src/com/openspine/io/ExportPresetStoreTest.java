package com.openspine.io;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Headless regression coverage for named export preference snapshots. */
public final class ExportPresetStoreTest {
	public static void main(String[] args) {
		LinkedHashMap<String, Map<String, String>> source = new LinkedHashMap<>();
		source.put("透明 PNG", Map.of("export.png.scale", "1.25", "export.png.transparent", "true"));
		source.put("视频 | 测试", Map.of("export.avi.quality", "92", "empty", ""));
		String encoded = ExportPresetStore.encode(source);
		Map<String, Map<String, String>> decoded = ExportPresetStore.decode(encoded);
		assertEquals(source, decoded, "preset values survive encoding and decoding");
		assertEquals(List.of("透明 PNG", "视频 | 测试"), List.copyOf(decoded.keySet()),
			"preset order is stable");
		assertEquals(null, ExportPresetStore.normalizeName(" "), "blank names are rejected");
		assertEquals(null, ExportPresetStore.normalizeName("x".repeat(65)), "overlong names are rejected");
		assertTrue(ExportPresetStore.decode("invalid").isEmpty(), "unknown preset versions are ignored");
		System.out.println("ALL EXPORT PRESET STORE TESTS PASSED");
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private ExportPresetStoreTest() {}
}
