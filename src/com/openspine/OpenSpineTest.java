package com.openspine;

import java.nio.file.Path;

/** Headless command-line parsing checks. */
public final class OpenSpineTest {
	public static void main(String[] args) {
		for (String icon : new String[] {
			"assets/icons/openspine-16.png",
			"assets/icons/openspine-24.png",
			"assets/icons/openspine-32.png",
			"assets/icons/openspine-48.png",
			"assets/icons/openspine-64.png",
			"assets/icons/openspine-128.png",
			"assets/icons/openspine-256.png"
		}) {
			assertTrue(OpenSpine.class.getClassLoader().getResource(icon) != null,
				"window icon is available on the runtime classpath: " + icon);
		}
		OpenSpine.CommandLine help = OpenSpine.parseArguments(new String[] {"--help"});
		assertTrue(help.help() && !help.mcp(), "--help selects help mode");
		OpenSpine.CommandLine mcp = OpenSpine.parseArguments(new String[] {"--mcp", "--root", "."});
		assertTrue(mcp.mcp() && mcp.root().equals(Path.of("").toAbsolutePath().normalize()),
			"--mcp resolves its root");
		OpenSpine.CommandLine file = OpenSpine.parseArguments(new String[] {"--", "-project.json"});
		assertTrue("-project.json".equals(file.initialPath()), "-- permits a leading-dash file name");
		assertTrue(OpenSpine.helpText().contains("openspine_write_project")
			&& OpenSpine.helpText().contains("--root"), "help documents MCP tools and access root");
		assertThrows(() -> OpenSpine.parseArguments(new String[] {"--root", "."}),
			"--root without MCP is rejected");
		assertThrows(() -> OpenSpine.parseArguments(new String[] {"--mcp"}),
			"MCP without an explicit root is rejected");
		OpenSpine.CommandLine capture = OpenSpine.parseArguments(new String[] {"--mcp-capture-root", ".", "hero.json"});
		assertTrue(!capture.mcp() && capture.captureRoot().equals(Path.of("").toAbsolutePath().normalize())
			&& "hero.json".equals(capture.initialPath()), "capture is explicitly enabled in editor mode");
		assertTrue(file.captureRoot() == null, "ordinary editors do not expose screenshot capture");
		assertThrows(() -> OpenSpine.parseArguments(new String[] {"--mcp", "--root", ".", "--mcp-capture-root", "."}),
			"capture cannot be enabled in the stdio process");
		assertThrows(() -> OpenSpine.parseArguments(new String[] {"--mcp-capture-root"}), "capture requires its root");
		assertThrows(() -> OpenSpine.parseArguments(new String[] {"--unknown"}),
			"unknown options are rejected");
		assertThrows(() -> OpenSpine.parseArguments(new String[] {"a.json", "b.json"}),
			"multiple startup files are rejected");
		System.out.println("OK: OpenSpine CLI tests passed");
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private OpenSpineTest() {}
}
