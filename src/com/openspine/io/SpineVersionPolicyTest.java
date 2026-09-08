package com.openspine.io;

import java.nio.file.Path;
import java.util.Locale;

/** Headless coverage for source-version conversion and overwrite protection. */
public final class SpineVersionPolicyTest {
	public static void main(String[] args) {
		assertFalse(SpineVersionPolicy.requiresConversion("4.3"), "4.3 is supported");
		assertFalse(SpineVersionPolicy.requiresConversion(" 4.3.23 "), "4.3.x is supported");
		assertTrue(SpineVersionPolicy.requiresConversion("4.2.99"), "older data requires conversion");
		assertTrue(SpineVersionPolicy.requiresConversion("4.30.0"), "4.30 is not the 4.3 family");
		assertTrue(SpineVersionPolicy.requiresConversion(null), "unknown data requires conversion");
		assertTrue(SpineVersionPolicy.requiresConversion("  "), "blank data requires conversion");

		String direct = Path.of("build", "version-policy", "hero.json").toString();
		String normalized = Path.of("build", "version-policy", "..", "version-policy", "hero.json").toString();
		assertTrue(SpineVersionPolicy.samePersistentPath(direct, normalized),
			"absolute normalized paths compare equally");
		assertFalse(SpineVersionPolicy.samePersistentPath("builtin:example", "builtin:example"),
			"built-in IDs are not filesystem paths");
		assertFalse(SpineVersionPolicy.samePersistentPath("new:hero", "new:hero"),
			"unsaved document IDs are not filesystem paths");
		assertFalse(SpineVersionPolicy.samePersistentPath(new String(new char[] {0}), direct),
			"invalid paths are rejected without throwing");
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
			assertTrue(SpineVersionPolicy.samePersistentPath(direct.toUpperCase(Locale.ROOT), direct),
				"Windows paths compare case-insensitively");

		assertTrue(SpineVersionPolicy.wouldOverwriteIncompatibleSource("4.2.99", direct, normalized),
			"incompatible data cannot overwrite its source");
		assertFalse(SpineVersionPolicy.wouldOverwriteIncompatibleSource("4.3.23", direct, normalized),
			"compatible data can use its associated source path");
		assertFalse(SpineVersionPolicy.wouldOverwriteIncompatibleSource("4.2.99", direct,
			Path.of("build", "version-policy", "hero-copy.json").toString()),
			"incompatible data can be converted to a distinct copy");
		assertTrue(SpineVersionPolicy.conversionCopyPath(direct).endsWith("hero-4.3.json"),
			"conversion copy suggestions preserve the data extension");
		assertTrue(SpineVersionPolicy.conversionCopyPath("hero").endsWith("hero-4.3"),
			"extensionless copy suggestions remain usable");
		assertTrue("builtin:example".equals(SpineVersionPolicy.conversionCopyPath("builtin:example")),
			"built-in IDs are not rewritten as file names");
		System.out.println("SPINE VERSION POLICY TEST PASSED");
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertFalse(boolean value, String message) {
		assertTrue(!value, message);
	}

	private SpineVersionPolicyTest() {}
}
