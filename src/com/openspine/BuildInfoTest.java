package com.openspine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

/** Build identity must survive both source builds and packaged execution. */
public final class BuildInfoTest {
	public static void main(String[] args) {
		if (!BuildInfo.sourceSha256().matches("[0-9a-f]{64}")) throw new AssertionError("Missing source fingerprint");
		Instant.parse(BuildInfo.builtAt());
		if (!Set.of("dirty", "clean", "unknown").contains(BuildInfo.worktreeState())) throw new AssertionError("Invalid Git worktree state");
		String version = BuildInfo.version(OpenSpine.VERSION);
		if (!version.endsWith("+" + BuildInfo.sourceSha256().substring(0, 12))) throw new AssertionError("Version must distinguish compiled inputs");
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
		if (OpenSpine.run(new String[] {"--version"}, output, output) != 0) throw new AssertionError("Version command failed");
		String text = bytes.toString(StandardCharsets.UTF_8);
		if (!text.contains(version) || !text.contains(BuildInfo.builtAt()) || !text.contains(BuildInfo.sourceSha256()))
			throw new AssertionError("Version command must expose build date and full source fingerprint");
		System.out.println("OK: build provenance and version identity passed");
	}
	private BuildInfoTest() {}
}
