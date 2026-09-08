package com.openspine.mcp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.openspine.mcp.SpineProjectTools.ToolFailure;

/** Headless regression tests for MCP framing and protected project operations. */
public final class McpServerTest {
	private static final String MINIMAL_PROJECT = """
		{
		  "skeleton": {"hash": "", "spine": "4.3.23", "x": 0, "y": 0, "width": 0, "height": 0},
		  "bones": [{"name": "root"}]
		}
		""";

	public static void main(String[] args) throws Exception {
		Path root = Files.createTempDirectory("openspine-mcp-test-").toRealPath();
		try {
			testsStrictJson();
			testsLauncherArguments(root);
			testsProjectTools(root);
			testsJsonRpcHandling(root);
			testsModernProtocol(root);
			testsCancellation(root);
			testsRequestIdKeys();
		} finally {
			try (var paths = Files.walk(root)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		System.out.println("ALL MCP SERVER TESTS PASSED");
	}

	private static void testsStrictJson() {
		for (String valid : List.of(
			"null", "true", "false", "0", "-0", "-12.34e+5", "\ufeff{}",
			"{\"array\":[null,true,false,0,\"text\\n\",{\"unicode\":\"\\uD834\\uDD1E\"}]}"))
			StrictJson.validate(valid);
		assertTrue(StrictJson.validate("{\"id\":" + Long.MAX_VALUE + "}").requestIdNumberSupported(),
			"signed long request ids remain exact");
		assertTrue(StrictJson.validate("{\"id\":7.0}").requestIdNumberSupported(),
			"small integral decimal request ids remain supported");
		assertTrue(StrictJson.validate("{\"id\":\"9223372036854775808\"}").requestIdNumberSupported(),
			"large digit strings remain valid string request ids");
		assertTrue(!StrictJson.validate("{\"id\":9223372036854775808}").requestIdNumberSupported(),
			"numeric request ids outside signed long range are rejected before lenient parsing");
		assertTrue(!StrictJson.validate("{\"id\":9223372036854775807.0}").requestIdNumberSupported(),
			"large decimal request ids that lose double precision are rejected");

		for (String invalid : List.of(
			"", "{name:\"value\"}", "{'name':'value'}", "{\"a\":1,}", "[1,]", "01", "+1", ".5",
			"1.", "1e", "true false", "/* comment */ null", "{\"a\":1,\"\\u0061\":2}",
			"\"bad\\xescape\"", "\"\\uD800\"", "\"\\uDC00\""))
			assertStrictJsonRejected(invalid, "invalid JSON syntax must be rejected: " + invalid);
		assertStrictJsonRejected("[".repeat(514) + "0" + "]".repeat(514),
			"excessively nested JSON must be rejected");
	}

	private static void testsLauncherArguments(Path root) {
		String[] forwarded = McpLauncher.withMcpMode(new String[] {"--root", root.toString()});
		assertEquals(3, forwarded.length, "MCP launcher preserves user arguments");
		assertEquals("--mcp", forwarded[0], "MCP launcher always selects MCP mode first");
		assertEquals("--root", forwarded[1], "MCP launcher forwards options after MCP mode");
	}

	private static void testsProjectTools(Path root) throws Exception {
		SpineProjectTools tools = new SpineProjectTools(root);
		JsonValue validation = tools.validateProject(null, MINIMAL_PROJECT);
		assertTrue(validation.getBoolean("valid"), "in-memory project validates");
		assertTrue(validation.getBoolean("structure_valid") && !validation.getBoolean("textures_checked")
			&& !validation.getBoolean("render_checked"), "validation explicitly distinguishes structure from visual checks");
		assertEquals(1, validation.get("summary").get("counts").getInt("bones"),
			"validation returns the parsed bone count");
		assertToolFailure(() -> tools.validateProject(null,
			MINIMAL_PROJECT.replace("\"width\": 0", "\"width\": 1e999")),
			"non-finite project numbers are rejected");
		assertToolFailure(() -> tools.validateProject(null, "{bones:[{name:\"root\"}]}"),
			"non-standard JSON syntax is rejected");
		assertToolFailure(() -> tools.validateProject(null, "{\"name\":\"not a Spine project\"}"),
			"ordinary JSON without a root bone is rejected");

		Files.createDirectories(root.resolve("projects"));
		JsonValue created = tools.writeProject("projects/hero.json", MINIMAL_PROJECT, null, false, false);
		assertTrue(created.getBoolean("created"), "new projects can be written below the configured root");
		String firstSha = created.getString("sha256");
		JsonValue read = tools.readProject("projects/hero.json");
		assertEquals(firstSha, read.getString("sha256"), "read returns the exact stored content hash");
		assertTrue(read.getString("json").contains("\"root\""), "read returns the complete JSON project");
		String supplementaryCharacter = new String(Character.toChars(0x1f600));
		String unicodeProject = MINIMAL_PROJECT.replace("\"root\"", "\"root" + supplementaryCharacter + "\"");
		Files.writeString(root.resolve("projects/unicode.json"), unicodeProject, StandardCharsets.UTF_8);
		StringBuilder reconstructed = new StringBuilder();
		int offset = 0;
		do {
			JsonValue chunk = tools.readProjectChunk("projects/unicode.json", offset, 17);
			reconstructed.append(chunk.getString("json_chunk"));
			offset += chunk.getInt("returned_chars");
			if (chunk.getBoolean("complete")) break;
			assertEquals(offset, chunk.getInt("next_offset_chars"), "chunk cursors advance by Unicode characters");
		} while (true);
		assertEquals(unicodeProject, reconstructed.toString(), "chunked reads preserve supplementary Unicode characters");

		String base = MINIMAL_PROJECT.stripTrailing();
		String largeProject = base.substring(0, base.length() - 1) + ",\"padding\":\""
			+ "x".repeat(2 * 1024 * 1024) + "\"}";
		Files.writeString(root.resolve("projects/large.json"), largeProject, StandardCharsets.UTF_8);
		assertToolFailure(() -> tools.readProject("projects/large.json"),
			"large projects must use bounded chunked reads");
		JsonValue firstLargeChunk = tools.readProjectChunk("projects/large.json", 0, 32);
		assertEquals(32, firstLargeChunk.getInt("returned_chars"), "large project chunks honor their character limit");
		assertTrue(!firstLargeChunk.getBoolean("complete"), "large project chunks expose a continuation cursor");
		Files.writeString(root.resolve("projects/config.json"), "{\"name\":\"not a Spine project\"}",
			StandardCharsets.UTF_8);

		JsonValue listed = tools.listProjects("projects", 0, 0, 2);
		assertEquals(3, listed.getInt("total"), "project discovery includes only runtime-valid projects");
		assertEquals(4, listed.getInt("candidate_count"), "project discovery reports extension candidates");
		assertEquals(1, listed.getInt("skipped_invalid"), "ordinary JSON files are excluded from project results");
		assertEquals(2, listed.get("projects").size, "project discovery applies its page limit");
		assertEquals(2, listed.getInt("next_offset"), "project discovery returns a stable next offset");
		assertTrue(!hasProject(listed.get("projects"), "config.json"),
			"project discovery never labels ordinary JSON as a Spine project");
		assertToolFailure(() -> tools.listProjects("../", 1, 0, 10),
			"project discovery cannot escape the configured root");
		byte[] previousBytes = Files.readAllBytes(root.resolve("projects/hero.json"));

		JsonValue replaced = tools.writeProject("projects/hero.json", MINIMAL_PROJECT, firstSha, false, true);
		assertTrue(!replaced.getBoolean("created"), "matching hashes allow protected replacement");
		assertTrue(Files.isRegularFile(root.resolve("projects/hero.json.bak")),
			"requested backups are created beside the project");
		assertTrue(java.util.Arrays.equals(previousBytes, Files.readAllBytes(root.resolve("projects/hero.json.bak"))),
			"backups contain the exact project version that was replaced");
		assertToolFailure(() -> tools.writeProject("projects/hero.json", MINIMAL_PROJECT,
			"0000000000000000000000000000000000000000000000000000000000000000", false, false),
			"stale hashes reject replacement");
		assertToolFailure(() -> tools.writeProject("../outside.json", MINIMAL_PROJECT, null, false, false),
			"writes cannot escape the configured root");
		Files.write(root.resolve("invalid-utf8.json"), new byte[] {'{', '"', 'x', '"', ':', '"', (byte)0xc3, '(', '"', '}'});
		assertToolFailure(() -> tools.readProject("invalid-utf8.json"), "malformed UTF-8 is rejected");
	}

	private static void testsJsonRpcHandling(Path root) throws Exception {
		String requests = String.join("\n",
			"{jsonrpc:\"2.0\",id:0,method:\"ping\"}",
			"{\"jsonrpc\":\"2.0\"}",
			"{\"jsonrpc\":\"2.0\",\"id\":true,\"method\":\"ping\"}",
			"{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}",
			"{\"jsonrpc\":\"2.0\",\"id\":1.5,\"method\":\"ping\"}",
			"{\"jsonrpc\":\"2.0\",\"id\":\"scalar-params\",\"method\":\"ping\",\"params\":true}",
			"{\"jsonrpc\":\"2.0\",\"id\":\"array-params\",\"method\":\"ping\",\"params\":[]}",
			"{\"jsonrpc\":\"2.0\",\"id\":\"pre-init-ping\",\"method\":\"ping\"}",
			"{\"jsonrpc\":\"2.0\",\"id\":\"bad-init\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\"}}",
			"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}",
			"{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"ignored-notification\"}}",
			"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}",
			"{\"jsonrpc\":\"2.0\",\"id\":1e999,\"method\":\"ping\"}",
			"{\"jsonrpc\":\"2.0\",\"id\":9223372036854775808,\"method\":\"ping\"}",
			"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}") + "\n";
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream errors = new ByteArrayOutputStream();
		McpServer server = new McpServer(root, "test", new ByteArrayInputStream(requests.getBytes(StandardCharsets.UTF_8)),
			output, new PrintStream(errors, true, StandardCharsets.UTF_8));
		server.run();

		List<String> responses = output.toString(StandardCharsets.UTF_8).lines().toList();
		assertEquals(14, responses.size(), "valid notifications produce no response");
		JsonReader reader = new JsonReader();
		JsonValue nonStandardJson = reader.parse(responses.get(0));
		assertEquals(-32700, nonStandardJson.get("error").getInt("code"),
			"non-standard JSON syntax receives a parse error");
		JsonValue invalid = reader.parse(responses.get(1));
		assertEquals(-32600, invalid.get("error").getInt("code"),
			"malformed request objects receive Invalid Request instead of being dropped");
		assertTrue(invalid.get("id").isNull(), "invalid request responses use a null id");
		JsonValue invalidId = reader.parse(responses.get(2));
		assertEquals(-32600, invalidId.get("error").getInt("code"), "boolean request ids are rejected");
		assertTrue(invalidId.get("id").isNull(), "invalid request ids are never reflected in error responses");
		JsonValue nullId = reader.parse(responses.get(3));
		assertEquals(-32600, nullId.get("error").getInt("code"), "null request ids are rejected without crashing");
		assertTrue(nullId.get("id").isNull(), "null request id errors use a null response id");
		JsonValue fractionalId = reader.parse(responses.get(4));
		assertEquals(-32600, fractionalId.get("error").getInt("code"), "fractional request ids are rejected");
		JsonValue scalarParams = reader.parse(responses.get(5));
		assertEquals(-32600, scalarParams.get("error").getInt("code"),
			"JSON-RPC params must be structured values");
		assertEquals("scalar-params", scalarParams.getString("id"),
			"structurally valid request ids are preserved in errors");
		JsonValue arrayParams = reader.parse(responses.get(6));
		assertEquals(-32600, arrayParams.get("error").getInt("code"), "MCP params arrays are rejected");
		assertEquals("array-params", arrayParams.getString("id"), "array-param errors preserve valid ids");
		JsonValue preInitPing = reader.parse(responses.get(7));
		assertEquals("pre-init-ping", preInitPing.getString("id"), "ping is available before initialization");
		JsonValue badInitialize = reader.parse(responses.get(8));
		assertEquals(-32602, badInitialize.get("error").getInt("code"),
			"initialize requires client capabilities and identity");
		JsonValue initialized = reader.parse(responses.get(9));
		assertEquals(1, initialized.getInt("id"), "initialize response preserves its request id");
		assertTrue(initialized.get("result").get("serverInfo").get("build").has("source_sha256"),
			"MCP clients can identify the exact compiled inputs");
		JsonValue ping = reader.parse(responses.get(10));
		assertEquals(2, ping.getInt("id"), "subsequent requests remain synchronized after a notification");
		JsonValue nonFiniteId = reader.parse(responses.get(11));
		assertEquals(-32600, nonFiniteId.get("error").getInt("code"), "non-finite request ids are rejected");
		assertTrue(nonFiniteId.get("id").isNull(), "non-finite ids cannot corrupt JSON-RPC output");
		JsonValue oversizedId = reader.parse(responses.get(12));
		assertEquals(-32600, oversizedId.get("error").getInt("code"),
			"out-of-range numeric request ids are rejected instead of changing type");
		assertTrue(oversizedId.get("id").isNull(), "out-of-range numeric id errors use a null response id");
		JsonValue finalPing = reader.parse(responses.get(13));
		assertEquals(3, finalPing.getInt("id"), "the server continues after every malformed envelope");
		assertEquals("", errors.toString(StandardCharsets.UTF_8), "expected requests do not write diagnostics");

		testsInputLimits(root);
	}

	private static void testsInputLimits(Path root) throws Exception {
		String validPing = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}\n";
		ByteArrayOutputStream oversizedInput = new ByteArrayOutputStream();
		oversizedInput.writeBytes(" ".repeat(129).getBytes(StandardCharsets.UTF_8));
		oversizedInput.write('\n');
		oversizedInput.writeBytes(validPing.getBytes(StandardCharsets.UTF_8));
		List<JsonValue> oversizedResponses = runServer(root, oversizedInput.toByteArray(), 128);
		assertEquals(-32600, oversizedResponses.get(0).get("error").getInt("code"),
			"oversized messages are rejected without unbounded buffering");
		assertEquals(7, oversizedResponses.get(1).getInt("id"),
			"the server recovers after draining an oversized message");

		ByteArrayOutputStream malformedInput = new ByteArrayOutputStream();
		malformedInput.writeBytes(new byte[] {'{', '"', 'x', '"', ':', '"', (byte)0xc3, '(', '"', '}', '\n'});
		malformedInput.writeBytes(validPing.getBytes(StandardCharsets.UTF_8));
		List<JsonValue> malformedResponses = runServer(root, malformedInput.toByteArray(), 1024);
		assertEquals(-32700, malformedResponses.get(0).get("error").getInt("code"),
			"malformed UTF-8 receives a parse error");
		assertEquals(7, malformedResponses.get(1).getInt("id"),
			"the server recovers after malformed UTF-8");
	}

	private static void testsModernProtocol(Path root) throws Exception {
		String meta = "\"_meta\":{"
			+ "\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
			+ "\"io.modelcontextprotocol/clientCapabilities\":{}}";
		String requests = String.join("\n",
			"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{" + meta + "}}",
			"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{" + meta + "}}",
			"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{" + meta
				+ ",\"name\":\"openspine_inspect_project\",\"arguments\":{\"path\":\"projects/hero.json\"}}}",
			"{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\",\"params\":{\"_meta\":{"
				+ "\"io.modelcontextprotocol/protocolVersion\":\"1900-01-01\","
				+ "\"io.modelcontextprotocol/clientCapabilities\":{}}}}",
			"{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{" + meta
				+ ",\"name\":\"openspine_inspect_project\",\"arguments\":{\"path\":\"projects/hero.json\","
				+ "\"unexpected\":true}}}",
			"{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/list\",\"params\":{" + meta
				+ ",\"cursor\":\"never-issued\"}}",
			"{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/list\",\"params\":{\"_meta\":{"
				+ "\"io.modelcontextprotocol/clientCapabilities\":{}}}}",
			"{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/list\",\"params\":{\"_meta\":{"
				+ "\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
				+ "\"io.modelcontextprotocol/clientCapabilities\":{},"
				+ "\"io.modelcontextprotocol/clientInfo\":\"invalid\"}}}") + "\n";
		List<JsonValue> responses = runServer(root, requests);
		assertEquals(8, responses.size(), "modern requests each receive a response");
		Map<Integer, JsonValue> byId = responsesByIntegerId(responses);

		JsonValue discover = byId.get(1).get("result");
		assertEquals("complete", discover.getString("resultType"), "modern discovery is a complete result");
		assertEquals("2026-07-28", discover.get("supportedVersions").getString(0),
			"modern discovery advertises the latest version first");
		assertTrue(discover.has("ttlMs") && discover.has("cacheScope"),
			"modern discovery includes cache metadata");

		JsonValue listed = byId.get(2).get("result");
		assertEquals(8, listed.get("tools").size, "modern clients discover all tools");
		assertTrue(hasTool(listed.get("tools"), "openspine_list_projects")
			&& hasTool(listed.get("tools"), "openspine_read_project_chunk"),
			"modern discovery includes project listing and bounded chunk reads");
		assertTrue(hasTool(listed.get("tools"), "openspine_render_preview")
			&& hasTool(listed.get("tools"), "openspine_capture_screenshot"),
			"modern discovery includes both image tools");
		assertTrue(allToolsHaveOutputSchemas(listed.get("tools")),
			"every structured tool result advertises an output schema");
		assertTrue(listed.get("_meta").has("io.modelcontextprotocol/serverInfo"),
			"modern results identify the server");

		JsonValue called = byId.get(3).get("result");
		assertTrue(!called.getBoolean("isError"), "modern inspect tool succeeds");
		assertEquals(1, called.get("structuredContent").get("counts").getInt("bones"),
			"modern tool calls return structured content");

		JsonValue unsupported = byId.get(4).get("error");
		assertEquals(-32022, unsupported.getInt("code"), "unsupported modern versions use the standard error");
		assertEquals("2026-07-28", unsupported.get("data").get("supported").getString(0),
			"version errors advertise a supported retry target");
		assertEquals(5, unsupported.get("data").get("supported").size,
			"version errors advertise every version reported by discovery");
		assertEquals(-32602, byId.get(5).get("error").getInt("code"),
			"tool arguments are validated against additionalProperties=false");
		assertEquals(-32602, byId.get(6).get("error").getInt("code"),
			"unissued pagination cursors are rejected");
		assertEquals(-32602, byId.get(7).get("error").getInt("code"),
			"partial modern metadata cannot fall back to legacy semantics");
		assertEquals(-32602, byId.get(8).get("error").getInt("code"),
			"present client identity metadata is structurally validated");
	}

	private static void testsCancellation(Path root) throws Exception {
		String meta = "\"_meta\":{"
			+ "\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
			+ "\"io.modelcontextprotocol/clientCapabilities\":{}}";
		String requests = String.join("\n",
			"{\"jsonrpc\":\"2.0\",\"id\":101,\"method\":\"tools/call\",\"params\":{" + meta
				+ ",\"name\":\"openspine_read_project_chunk\",\"arguments\":{\"path\":\"projects/large.json\","
				+ "\"max_chars\":1}}}",
			"{\"jsonrpc\":\"2.0\",\"id\":102,\"method\":\"tools/call\",\"params\":{" + meta
				+ ",\"name\":\"openspine_read_project_chunk\",\"arguments\":{\"path\":\"projects/large.json\","
				+ "\"max_chars\":1}}}",
			"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":102.0,"
				+ "\"reason\":\"test cancellation\"}}",
			"{\"jsonrpc\":\"2.0\",\"id\":103,\"method\":\"ping\",\"params\":{" + meta + "}}") + "\n";
		List<JsonValue> responses = runServer(root, requests);
		Map<Integer, JsonValue> byId = responsesByIntegerId(responses);
		assertEquals(2, responses.size(), "cancelled tool calls do not produce responses");
		assertTrue(byId.containsKey(101), "the preceding tool call completes normally");
		assertTrue(!byId.containsKey(102), "the queued tool call is cancelled by a decimal spelling of its id");
		assertTrue(byId.containsKey(103), "the server remains responsive while a tool call is running");
	}

	private static void testsRequestIdKeys() {
		JsonReader reader = new JsonReader();
		assertEquals(McpServer.requestIdKey(reader.parse("7")), McpServer.requestIdKey(reader.parse("7.0")),
			"integer and decimal spellings of one numeric id share a key");
		assertEquals("n:" + Long.MAX_VALUE, McpServer.requestIdKey(reader.parse(Long.toString(Long.MAX_VALUE))),
			"long ids keep exact keys");
		assertTrue(!McpServer.requestIdKey(reader.parse("\"7\"")).equals(McpServer.requestIdKey(reader.parse("7"))),
			"string ids never collide with numeric ids");
		assertTrue(!McpServer.requestIdKey(reader.parse("\"n:7\"")).equals(McpServer.requestIdKey(reader.parse("7"))),
			"string ids that mimic numeric keys stay distinct");
	}

	private static List<JsonValue> runServer(Path root, String requests) throws Exception {
		return runServer(root, requests.getBytes(StandardCharsets.UTF_8), McpServer.MAX_JSON_RPC_MESSAGE_BYTES);
	}

	private static List<JsonValue> runServer(Path root, byte[] requests, int maxMessageBytes) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream errors = new ByteArrayOutputStream();
		McpServer server = new McpServer(root, "test",
			new ByteArrayInputStream(requests), output,
			new PrintStream(errors, true, StandardCharsets.UTF_8), maxMessageBytes);
		server.run();
		assertEquals("", errors.toString(StandardCharsets.UTF_8), "valid modern requests do not write diagnostics");
		JsonReader reader = new JsonReader();
		return output.toString(StandardCharsets.UTF_8).lines().map(reader::parse).toList();
	}

	private static Map<Integer, JsonValue> responsesByIntegerId(List<JsonValue> responses) {
		Map<Integer, JsonValue> byId = new HashMap<>();
		for (JsonValue response : responses) byId.put(response.getInt("id"), response);
		return byId;
	}

	private static boolean hasTool(JsonValue tools, String name) {
		for (JsonValue tool = tools.child; tool != null; tool = tool.next)
			if (name.equals(tool.getString("name"))) return true;
		return false;
	}

	private static boolean hasProject(JsonValue projects, String fileName) {
		for (JsonValue project = projects.child; project != null; project = project.next)
			if (project.getString("path").endsWith(fileName)) return true;
		return false;
	}

	private static boolean allToolsHaveOutputSchemas(JsonValue tools) {
		for (JsonValue tool = tools.child; tool != null; tool = tool.next) {
			JsonValue schema = tool.get("outputSchema");
			if (schema == null || !schema.isObject()) return false;
		}
		return true;
	}

	private static void assertToolFailure(ThrowingRunnable action, String message) throws Exception {
		try {
			action.run();
			throw new AssertionError(message + ": expected ToolFailure");
		} catch (ToolFailure expected) {
			// Expected.
		}
	}

	private static void assertStrictJsonRejected(String json, String message) {
		try {
			StrictJson.validate(json);
			throw new AssertionError(message + ": expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private McpServerTest() {}
}
