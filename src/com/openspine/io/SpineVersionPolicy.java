package com.openspine.io;

import java.nio.file.Path;
import java.util.Locale;

/** Centralizes the data-version conversion and source-file overwrite policy. */
public final class SpineVersionPolicy {
	private static final boolean WINDOWS = System.getProperty("os.name", "")
		.toLowerCase(Locale.ROOT).contains("win");

	/** OpenSpine writes Spine 4.3 data, so every other or unknown source version requires conversion. */
	public static boolean requiresConversion(String version) {
		if (version == null) return true;
		String normalized = version.trim();
		return !(normalized.equals("4.3") || normalized.startsWith("4.3."));
	}

	/** Compares real filesystem paths without treating built-in or unsaved document IDs as files. */
	public static boolean samePersistentPath(String left, String right) {
		if (!isPersistentPath(left) || !isPersistentPath(right)) return false;
		try {
			String normalizedLeft = Path.of(left).toAbsolutePath().normalize().toString();
			String normalizedRight = Path.of(right).toAbsolutePath().normalize().toString();
			return WINDOWS ? normalizedLeft.equalsIgnoreCase(normalizedRight)
				: normalizedLeft.equals(normalizedRight);
		} catch (RuntimeException error) {
			return false;
		}
	}

	public static boolean wouldOverwriteIncompatibleSource(String version, String sourcePath,
		String targetPath) {
		return requiresConversion(version) && samePersistentPath(sourcePath, targetPath);
	}

	/** Suggests a sibling file that makes a 4.3 conversion explicit and preserves the source. */
	public static String conversionCopyPath(String sourcePath) {
		if (!isPersistentPath(sourcePath)) return sourcePath;
		try {
			Path source = Path.of(sourcePath);
			Path fileName = source.getFileName();
			if (fileName == null) return sourcePath;
			String name = fileName.toString();
			int extension = name.lastIndexOf('.');
			String converted = extension > 0 ? name.substring(0, extension) + "-4.3" + name.substring(extension)
				: name + "-4.3";
			return source.resolveSibling(converted).toString();
		} catch (RuntimeException error) {
			return sourcePath;
		}
	}

	private static boolean isPersistentPath(String value) {
		if (value == null || value.isBlank()) return false;
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return !normalized.startsWith("builtin:") && !normalized.startsWith("new:");
	}

	private SpineVersionPolicy() {}
}
