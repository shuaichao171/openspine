package com.openspine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Identity of the compiled inputs, independent of the marketing version or checkout at runtime. */
public final class BuildInfo {
	private static final Properties BUILD = read();

	public static String builtAt() { return BUILD.getProperty("built_at", "unknown"); }
	public static String sourceSha256() { return BUILD.getProperty("source_sha256", "unknown"); }
	public static String gitCommit() { return BUILD.getProperty("git_commit", "unknown"); }
	public static String worktreeState() { return BUILD.getProperty("worktree_state", "unknown"); }

	public static String version(String baseVersion) {
		String hash = sourceSha256();
		return baseVersion + "+" + (hash.matches("[0-9a-f]{64}") ? hash.substring(0, 12) : "unidentified");
	}

	public static String description(String baseVersion) {
		return version(baseVersion) + " (built " + builtAt() + "; source " + sourceSha256()
			+ "; git " + gitCommit() + " " + worktreeState() + ")";
	}

	private static Properties read() {
		Properties values = new Properties();
		try (InputStream input = BuildInfo.class.getResourceAsStream("/openspine-build.properties")) {
			if (input != null) values.load(input);
		} catch (IOException ignored) {
			values.clear();
		}
		return values;
	}
	private BuildInfo() {}
}
