package com.openspine.io;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic, dependency-free serialization for named export preference snapshots. */
public final class ExportPresetStore {
	private static final String VERSION = "openspine-export-presets-v1";

	public static String encode(Map<String, ? extends Map<String, String>> presets) {
		StringBuilder result = new StringBuilder(VERSION);
		if (presets == null || presets.isEmpty()) return result.toString();
		for (Map.Entry<String, ? extends Map<String, String>> preset : presets.entrySet()) {
			String name = normalizeName(preset.getKey());
			if (name == null) continue;
			result.append('\n').append(token(name));
			Map<String, String> values = preset.getValue();
			if (values == null) continue;
			for (Map.Entry<String, String> value : values.entrySet()) {
				if (value.getKey() == null || value.getKey().isBlank()) continue;
				result.append('|').append(token(value.getKey())).append(':')
					.append(token(value.getValue() == null ? "" : value.getValue()));
			}
		}
		return result.toString();
	}

	public static LinkedHashMap<String, Map<String, String>> decode(String encoded) {
		LinkedHashMap<String, Map<String, String>> result = new LinkedHashMap<>();
		if (encoded == null || encoded.isBlank()) return result;
		String[] lines = encoded.split("\\R", -1);
		if (lines.length == 0 || !VERSION.equals(lines[0])) return result;
		for (int lineIndex = 1; lineIndex < lines.length; lineIndex++) {
			String line = lines[lineIndex];
			if (line.isBlank()) continue;
			String[] fields = line.split("\\|", -1);
			String name = normalizeName(decodeToken(fields[0]));
			if (name == null || result.containsKey(name)) continue;
			LinkedHashMap<String, String> values = new LinkedHashMap<>();
			for (int fieldIndex = 1; fieldIndex < fields.length; fieldIndex++) {
				int separator = fields[fieldIndex].indexOf(':');
				if (separator <= 0) continue;
				String key = decodeToken(fields[fieldIndex].substring(0, separator));
				String value = decodeToken(fields[fieldIndex].substring(separator + 1));
				if (key != null && !key.isBlank() && value != null) values.put(key, value);
			}
			result.put(name, Collections.unmodifiableMap(new LinkedHashMap<>(values)));
		}
		return result;
	}

	public static String normalizeName(String name) {
		if (name == null) return null;
		String normalized = name.trim();
		return normalized.isEmpty() || normalized.length() > 64 ? null : normalized;
	}

	private static String token(String value) {
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String decodeToken(String value) {
		try {
			return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException error) {
			return null;
		}
	}

	private ExportPresetStore() {}
}
