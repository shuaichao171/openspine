package com.openspine.mcp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.openspine.mcp.SpineProjectTools.ToolFailure;
import com.openspine.BuildInfo;

/** Dual-era MCP server using newline-delimited JSON-RPC over stdio. */
public final class McpServer {
	public static final String LATEST_PROTOCOL_VERSION = "2026-07-28";
	private static final Set<String> LEGACY_PROTOCOL_VERSIONS = Set.of(
		"2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25");
	private static final String LATEST_LEGACY_PROTOCOL_VERSION = "2025-11-25";
	private static final String SERVER_NAME = "openspine";
	private static final long TOOL_LIST_TTL_MS = 300_000L;
	private static final int MAX_PENDING_TOOL_CALLS = 16;
	private static final int MAX_DUPLICATED_TOOL_TEXT_BYTES = 256 * 1024;
	// A 16 MiB project can nearly double when embedded as an escaped JSON string.
	static final int MAX_JSON_RPC_MESSAGE_BYTES = 34 * 1024 * 1024;

	private final SpineProjectTools tools;
	private final McpVisualTools visuals;
	private final BufferedInputStream input;
	private final PrintWriter output;
	private final PrintStream errorOutput;
	private final String serverVersion;
	private final int maxMessageBytes;
	private final ThreadPoolExecutor toolExecutor;
	private final Map<String, PendingToolCall> pendingToolCalls = new ConcurrentHashMap<>();
	private boolean legacyInitialized;

	public McpServer(Path root, String serverVersion, InputStream input, OutputStream output,
		PrintStream errorOutput) throws IOException {
		this(root, serverVersion, input, output, errorOutput, MAX_JSON_RPC_MESSAGE_BYTES);
	}

	McpServer(Path root, String serverVersion, InputStream input, OutputStream output,
		PrintStream errorOutput, int maxMessageBytes) throws IOException {
		if (maxMessageBytes <= 0) throw new IllegalArgumentException("Maximum MCP message size must be positive.");
		this.tools = new SpineProjectTools(root);
		this.visuals = new McpVisualTools(tools);
		this.serverVersion = serverVersion == null || serverVersion.isBlank() ? "unknown" : serverVersion;
		this.input = new BufferedInputStream(input);
		this.output = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
		this.errorOutput = errorOutput;
		this.maxMessageBytes = maxMessageBytes;
		this.toolExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(MAX_PENDING_TOOL_CALLS), runnable -> {
				Thread thread = new Thread(runnable, "openspine-mcp-tools");
				thread.setDaemon(true);
				return thread;
			}, new ThreadPoolExecutor.AbortPolicy());
	}

	/** Runs until the client closes stdin. stdout remains reserved for MCP messages. */
	public void run() throws IOException {
		try {
			String line;
			while (true) {
				try {
					line = readMessageLine();
				} catch (MessageTooLargeException tooLarge) {
					send(error(null, -32600, "Invalid Request: message exceeds the configured size limit", null));
					continue;
				} catch (CharacterCodingException malformedUtf8) {
					send(error(null, -32700, "Parse error", null));
					continue;
				}
				if (line == null) break;
				if (line.isBlank()) {
					send(error(null, -32700, "Parse error", null));
					continue;
				}
				JsonValue request;
				try {
					StrictJson.Validation validation = StrictJson.validate(line);
					if (!validation.requestIdNumberSupported()) {
						send(error(null, -32600, "Invalid Request: numeric id is outside the supported exact range", null));
						continue;
					}
					request = new JsonReader().parse(line);
				} catch (RuntimeException parseError) {
					send(error(null, -32700, "Parse error", null));
					continue;
				}
				handle(request);
			}
		} finally {
			shutdownToolExecutor();
		}
	}

	private void handle(JsonValue request) {
		JsonValue requestId = request != null && request.isObject() ? request.get("id") : null;
		JsonValue id = isValidRequestId(requestId) ? requestId : null;
		boolean notification = isNotificationEnvelope(request);
		try {
			validateEnvelope(request);
			String method = request.getString("method");
			if (notification) {
				handleNotification(method, request.get("params"));
				return;
			}
			String requestKey = requestIdKey(id);
			if (pendingToolCalls.containsKey(requestKey))
				throw protocol(-32600, "Request ID is already in use: " + requestKey);
			if ("tools/call".equals(method)) {
				boolean modern = validateRequestEra(method, request.get("params"));
				submitToolCall(id, requestKey, request.get("params"), modern);
				return;
			}
			JsonValue result = dispatch(method, request.get("params"));
			send(success(id, result));
		} catch (ProtocolFailure failure) {
			if (!notification) send(error(id, failure.code, failure.getMessage(), failure.data));
		} catch (Throwable failure) {
			if (errorOutput != null)
				errorOutput.println("OpenSpine MCP request failed: " + safeMessage(failure));
			if (!notification) send(error(id, -32603, "Internal error", null));
		}
	}

	private static boolean isNotificationEnvelope(JsonValue request) {
		if (request == null || !request.isObject() || request.has("id")) return false;
		JsonValue version = request.get("jsonrpc");
		JsonValue method = request.get("method");
		return version != null && version.isString() && "2.0".equals(version.asString())
			&& method != null && method.isString() && !method.asString().isBlank();
	}

	private JsonValue dispatch(String method, JsonValue params) throws ProtocolFailure {
		if ("initialize".equals(method)) return initialize(params);

		boolean modern = validateRequestEra(method, params);
		return switch (method) {
		case "server/discover" -> discover(modern);
		case "tools/list" -> listTools(params, modern);
		case "ping" -> resultBase(modern);
		default -> throw protocol(-32601, "Method not found: " + method);
		};
	}

	private JsonValue initialize(JsonValue params) throws ProtocolFailure {
		JsonValue object = requireObject(params, "initialize params");
		String requested = requireString(object, "protocolVersion");
		requireObject(object.get("capabilities"), "capabilities");
		JsonValue clientInfo = requireObject(object.get("clientInfo"), "clientInfo");
		requireString(clientInfo, "name");
		requireString(clientInfo, "version");
		String selected = LEGACY_PROTOCOL_VERSIONS.contains(requested)
			? requested : LATEST_LEGACY_PROTOCOL_VERSION;
		legacyInitialized = true;
		JsonValue result = object();
		put(result, "protocolVersion", selected);
		put(result, "capabilities", capabilities());
		put(result, "serverInfo", serverInfo());
		put(result, "instructions", instructions());
		return result;
	}

	private JsonValue discover(boolean modern) {
		JsonValue result = cacheableResultBase(modern);
		put(result, "supportedVersions", supportedVersions());
		put(result, "capabilities", capabilities());
		put(result, "instructions", instructions());
		return result;
	}

	private JsonValue listTools(JsonValue params, boolean modern) throws ProtocolFailure {
		if (modern || params != null) {
			JsonValue object = requireObject(params, "tools/list params");
			JsonValue cursor = object.get("cursor");
			if (cursor != null) {
				if (!cursor.isString()) throw protocol(-32602, "cursor must be a string.");
				throw protocol(-32602, "Invalid or expired tools/list cursor.");
			}
		}
		JsonValue result = modern ? cacheableResultBase(true) : object();
		put(result, "tools", toolDefinitions());
		return result;
	}

	private void submitToolCall(JsonValue id, String requestKey, JsonValue params, boolean modern)
		throws ProtocolFailure {
		PendingToolCall pending = new PendingToolCall();
		if (pendingToolCalls.putIfAbsent(requestKey, pending) != null)
			throw protocol(-32600, "Request ID is already in use: " + requestKey);
		try {
			Future<?> future = toolExecutor.submit(() -> executeToolCall(id, requestKey, params, modern, pending));
			pending.future = future;
			if (pending.cancelled.get()) future.cancel(true);
		} catch (RejectedExecutionException busy) {
			pendingToolCalls.remove(requestKey, pending);
			throw protocol(-32603, "MCP tool queue is full; retry the request later.");
		}
	}

	private void executeToolCall(JsonValue id, String requestKey, JsonValue params, boolean modern,
		PendingToolCall pending) {
		try {
			if (pending.cancelled.get()) return;
			JsonValue result = callTool(params, modern);
			if (!pending.cancelled.get()) send(success(id, result));
		} catch (ProtocolFailure failure) {
			if (!pending.cancelled.get()) send(error(id, failure.code, failure.getMessage(), failure.data));
		} catch (Throwable failure) {
			if (!pending.cancelled.get()) {
				if (errorOutput != null)
					errorOutput.println("OpenSpine MCP tool request failed: " + safeMessage(failure));
				send(error(id, -32603, "Internal error", null));
			}
		} finally {
			pendingToolCalls.remove(requestKey, pending);
		}
	}

	private JsonValue callTool(JsonValue params, boolean modern) throws ProtocolFailure {
		JsonValue object = requireObject(params, "tools/call params");
		String name = requireString(object, "name");
		JsonValue arguments = object.get("arguments");
		if (arguments == null) arguments = object();
		if (!arguments.isObject()) throw protocol(-32602, "arguments must be an object.");
		validateToolArgumentNames(name, arguments);
		try {
			if ("openspine_render_preview".equals(name)) return imageResult(visuals.preview(arguments), modern);
			if ("openspine_capture_screenshot".equals(name)) return imageResult(visuals.screenshot(arguments), modern);
			JsonValue data = switch (name) {
			case "openspine_list_projects" -> listProjectsTool(arguments);
			case "openspine_inspect_project" -> tools.inspectProject(requireString(arguments, "path"));
			case "openspine_read_project" -> tools.readProject(requireString(arguments, "path"));
			case "openspine_read_project_chunk" -> readProjectChunkTool(arguments);
			case "openspine_validate_project" -> validateTool(arguments);
			case "openspine_write_project" -> writeTool(arguments);
			default -> throw protocol(-32602, "Unknown tool: " + name);
			};
			return toolResult(data, false, modern);
		} catch (IllegalArgumentException invalidArguments) {
			throw protocol(-32602, invalidArguments.getMessage());
		} catch (ToolFailure failure) {
			return toolError(failure.getMessage(), modern);
		} catch (IOException failure) {
			return toolError(failure.getMessage(), modern);
		} catch (InterruptedException cancelled) {
			Thread.currentThread().interrupt();
			return toolError("Image request cancelled.", modern);
		}
	}

	private static void validateToolArgumentNames(String toolName, JsonValue arguments) throws ProtocolFailure {
		Set<String> allowed = switch (toolName) {
		case "openspine_list_projects" -> Set.of("path", "max_depth", "offset", "limit");
		case "openspine_inspect_project", "openspine_read_project" -> Set.of("path");
		case "openspine_read_project_chunk" -> Set.of("path", "offset_chars", "max_chars");
		case "openspine_validate_project" -> Set.of("path", "json");
		case "openspine_render_preview" -> Set.of("path", "animation", "skin", "atlas", "time", "end_time",
			"frame_count", "width", "height", "padding", "transparent", "background");
		case "openspine_capture_screenshot" -> Set.of("path", "editor_pid", "max_width", "max_height");
		case "openspine_write_project" -> Set.of(
			"path", "json", "expected_sha256", "overwrite", "create_backup");
		default -> throw protocol(-32602, "Unknown tool: " + toolName);
		};
		for (JsonValue argument = arguments.child; argument != null; argument = argument.next) {
			if (!allowed.contains(argument.name))
				throw protocol(-32602, "Unknown argument for " + toolName + ": " + argument.name);
		}
	}

	private JsonValue listProjectsTool(JsonValue arguments) throws ToolFailure, ProtocolFailure {
		String path = optionalString(arguments, "path");
		int maxDepth = optionalInteger(arguments, "max_depth", 4, 0, 16);
		int offset = optionalInteger(arguments, "offset", 0, 0, Integer.MAX_VALUE);
		int limit = optionalInteger(arguments, "limit", 100, 1, 500);
		return tools.listProjects(path == null ? "." : path, maxDepth, offset, limit);
	}

	private JsonValue readProjectChunkTool(JsonValue arguments) throws ToolFailure, ProtocolFailure {
		String path = requireString(arguments, "path");
		int offset = optionalInteger(arguments, "offset_chars", 0, 0, Integer.MAX_VALUE);
		int maxChars = optionalInteger(arguments, "max_chars",
			SpineProjectTools.DEFAULT_PROJECT_CHUNK_CHARS, 1, SpineProjectTools.MAX_PROJECT_CHUNK_CHARS);
		return tools.readProjectChunk(path, offset, maxChars);
	}

	private JsonValue validateTool(JsonValue arguments) throws ToolFailure, ProtocolFailure {
		String path = optionalString(arguments, "path");
		String json = optionalString(arguments, "json");
		if ((path == null) == (json == null))
			throw protocol(-32602, "Provide exactly one of path or json.");
		return tools.validateProject(path, json);
	}

	private JsonValue writeTool(JsonValue arguments) throws ToolFailure, ProtocolFailure {
		String path = requireString(arguments, "path");
		String json = requireString(arguments, "json");
		String expectedSha = optionalString(arguments, "expected_sha256");
		if (expectedSha != null && !expectedSha.matches("(?i)[0-9a-f]{64}"))
			throw protocol(-32602, "expected_sha256 must contain exactly 64 hexadecimal characters.");
		boolean overwrite = optionalBoolean(arguments, "overwrite", false);
		boolean createBackup = optionalBoolean(arguments, "create_backup", false);
		return tools.writeProject(path, json, expectedSha, overwrite, createBackup);
	}

	private boolean validateRequestEra(String method, JsonValue params) throws ProtocolFailure {
		JsonValue meta = params != null && params.isObject() ? params.get("_meta") : null;
		JsonValue versionValue = meta != null && meta.isObject()
			? meta.get("io.modelcontextprotocol/protocolVersion") : null;
		boolean hasModernMetadata = meta != null && meta.isObject() && (versionValue != null
			|| meta.has("io.modelcontextprotocol/clientCapabilities")
			|| meta.has("io.modelcontextprotocol/clientInfo"));
		boolean modern = versionValue != null;
		if (hasModernMetadata && versionValue == null)
			throw protocol(-32602, "Modern MCP requests require protocolVersion metadata.");
		if (modern) {
			if (!versionValue.isString()) throw protocol(-32602, "MCP protocol version must be a string.");
			String requested = versionValue.asString();
			if (!LATEST_PROTOCOL_VERSION.equals(requested)) {
				JsonValue data = object();
				put(data, "supported", supportedVersions());
				put(data, "requested", requested);
				throw new ProtocolFailure(-32022, "Unsupported protocol version", data);
			}
			JsonValue clientCapabilities = meta.get("io.modelcontextprotocol/clientCapabilities");
			if (clientCapabilities == null || !clientCapabilities.isObject())
				throw protocol(-32602, "Modern MCP requests require clientCapabilities metadata.");
			JsonValue clientInfo = meta.get("io.modelcontextprotocol/clientInfo");
			if (clientInfo != null) {
				clientInfo = requireObject(clientInfo, "clientInfo metadata");
				requireString(clientInfo, "name");
				requireString(clientInfo, "version");
			}
		} else if ("server/discover".equals(method)) {
			throw protocol(-32602, "server/discover requires MCP 2026-07-28 request metadata.");
		} else if (!legacyInitialized && !"ping".equals(method)) {
			throw protocol(-32002, "Server is not initialized.");
		}
		return modern;
	}

	private void handleNotification(String method, JsonValue params) {
		if (!"notifications/cancelled".equals(method) || params == null || !params.isObject()) return;
		JsonValue requestId = params.get("requestId");
		if (!isRequestIdValue(requestId)) return;
		String requestKey = requestIdKey(requestId);
		PendingToolCall pending = pendingToolCalls.get(requestKey);
		if (pending == null) return;
		pending.cancel();
		pendingToolCalls.remove(requestKey, pending);
	}

	private JsonValue toolResult(JsonValue data, boolean isError, boolean modern) {
		JsonValue result = resultBase(modern);
		JsonValue content = array();
		JsonValue text = object();
		put(text, "type", "text");
		String serialized = data.toJson(OutputType.json);
		String textValue = !modern
			|| serialized.getBytes(StandardCharsets.UTF_8).length <= MAX_DUPLICATED_TOOL_TEXT_BYTES
			? data.prettyPrint(OutputType.json, 120)
			: "The complete result is available in structuredContent; duplicate text was omitted because it is large.";
		put(text, "text", textValue);
		content.addChild(text);
		put(result, "content", content);
		put(result, "structuredContent", data);
		put(result, "isError", isError);
		return result;
	}

	private JsonValue toolError(String message, boolean modern) {
		JsonValue result = resultBase(modern);
		JsonValue content = array();
		JsonValue text = object();
		put(text, "type", "text");
		put(text, "text", message);
		content.addChild(text);
		put(result, "content", content);
		put(result, "isError", true);
		return result;
	}

	private JsonValue imageResult(McpImages.Result image, boolean modern) {
		JsonValue result = toolResult(image.metadata(), false, modern);
		for (byte[] png : image.images()) {
			JsonValue block = object();
			put(block, "type", "image");
			put(block, "mimeType", "image/png");
			put(block, "data", Base64.getEncoder().encodeToString(png));
			result.get("content").addChild(block);
		}
		return result;
	}

	private JsonValue resultBase(boolean modern) {
		JsonValue result = object();
		if (modern) {
			put(result, "resultType", "complete");
			put(result, "_meta", resultMeta());
		}
		return result;
	}

	private JsonValue cacheableResultBase(boolean modern) {
		JsonValue result = resultBase(modern);
		if (modern) {
			put(result, "ttlMs", TOOL_LIST_TTL_MS);
			put(result, "cacheScope", "public");
		}
		return result;
	}

	private JsonValue capabilities() {
		JsonValue capabilities = object();
		JsonValue tools = object();
		put(tools, "listChanged", false);
		put(capabilities, "tools", tools);
		return capabilities;
	}

	private JsonValue serverInfo() {
		JsonValue info = object();
		put(info, "name", SERVER_NAME);
		put(info, "version", serverVersion);
		put(info, "description", "Spine project inspection, structural validation, protected writing, render previews, and opt-in editor screenshots.");
		JsonValue build = object();
		put(build, "built_at", BuildInfo.builtAt());
		put(build, "source_sha256", BuildInfo.sourceSha256());
		put(build, "git_commit", BuildInfo.gitCommit());
		put(build, "worktree_state", BuildInfo.worktreeState());
		put(info, "build", build);
		return info;
	}

	private JsonValue resultMeta() {
		JsonValue meta = object();
		put(meta, "io.modelcontextprotocol/serverInfo", serverInfo());
		return meta;
	}

	private String instructions() {
		return "Operate only on Spine .json/.skel projects under " + tools.root()
			+ ". Discover paths with openspine_list_projects. Read a JSON file before updating it and pass its SHA-256 "
			+ "to openspine_write_project. Use openspine_read_project_chunk for large JSON files. "
			+ "Use openspine_render_preview to see saved projects as PNG image content, including sampled animation frames. "
			+ "Use openspine_capture_screenshot for the live editor UI only after the editor is started with "
			+ "--mcp-capture-root matching this root. Screenshots may include unsaved content; neither image tool edits projects.";
	}

	private JsonValue toolDefinitions() {
		return new JsonReader().parse("""
			[
			  {
			    "name": "openspine_list_projects",
			    "title": "List OpenSpine projects",
			    "description": "Discover runtime-validated Spine .json and .skel projects below a directory in the configured MCP root. Validation is bounded and results are sorted and paged with an integer offset.",
			    "inputSchema": {
			      "type": "object",
			      "properties": {
			        "path": {"type": "string", "default": ".", "description": "Directory below the configured MCP root."},
			        "max_depth": {"type": "integer", "minimum": 0, "maximum": 16, "default": 4},
			        "offset": {"type": "integer", "minimum": 0, "default": 0},
			        "limit": {"type": "integer", "minimum": 1, "maximum": 500, "default": 100}
			      },
			      "additionalProperties": false
			    },
			    "outputSchema": {
			      "type": "object",
			      "properties": {
			        "directory": {"type": "string"},
			        "offset": {"type": "integer", "minimum": 0},
			        "total": {"type": "integer", "minimum": 0},
			        "candidate_count": {"type": "integer", "minimum": 0},
			        "skipped_invalid": {"type": "integer", "minimum": 0},
			        "validation_bytes": {"type": "integer", "minimum": 0},
			        "projects": {
			          "type": "array",
			          "items": {
			            "type": "object",
			            "properties": {
			              "path": {"type": "string"},
			              "format": {"type": "string", "enum": ["spine-json", "spine-binary"]},
			              "bytes": {"type": "integer", "minimum": 0},
			              "last_modified": {"type": "string"}
			            },
			            "required": ["path", "format", "bytes", "last_modified"],
			            "additionalProperties": false
			          }
			        },
			        "next_offset": {"type": "integer", "minimum": 0}
			      },
			      "required": ["directory", "offset", "total", "candidate_count", "skipped_invalid", "validation_bytes", "projects"],
			      "additionalProperties": false
			    },
			    "annotations": {"readOnlyHint": true, "destructiveHint": false, "idempotentHint": true, "openWorldHint": false}
			  },
			  {
			    "name": "openspine_inspect_project",
			    "title": "Inspect OpenSpine project",
			    "description": "Parse a Spine .json or .skel project with the official 4.3 runtime and return names, counts, bounds, durations, and SHA-256 without loading textures.",
			    "inputSchema": {
			      "type": "object",
			      "properties": {"path": {"type": "string", "description": "Absolute path or a path relative to the configured MCP root."}},
			      "required": ["path"],
			      "additionalProperties": false
			    },
			    "outputSchema": {
			      "type": "object",
			      "properties": {
			        "path": {"type": "string"},
			        "format": {"type": "string", "enum": ["spine-json", "spine-binary"]},
			        "sha256": {"type": "string", "pattern": "^[0-9a-f]{64}$"},
			        "counts": {"type": "object"},
			        "bones": {"type": "array", "items": {"type": "string"}},
			        "slots": {"type": "array", "items": {"type": "string"}},
			        "skins": {"type": "array", "items": {"type": "string"}},
			        "constraints": {"type": "array"},
			        "animations": {"type": "array"}
			      },
			      "required": ["path", "format", "sha256", "counts", "bones", "slots", "skins", "constraints", "animations"],
			      "additionalProperties": true
			    },
			    "annotations": {"readOnlyHint": true, "destructiveHint": false, "idempotentHint": true, "openWorldHint": false}
			  },
			  {
			    "name": "openspine_read_project",
			    "title": "Read OpenSpine JSON project",
			    "description": "Read and validate a Spine JSON project up to the inline response limit. Returns the complete JSON, structural summary, and SHA-256 used for protected writes; use read_project_chunk for larger files.",
			    "inputSchema": {
			      "type": "object",
			      "properties": {"path": {"type": "string", "description": "Absolute path or a path relative to the configured MCP root; must end in .json."}},
			      "required": ["path"],
			      "additionalProperties": false
			    },
			    "outputSchema": {
			      "type": "object",
			      "properties": {
			        "path": {"type": "string"},
			        "sha256": {"type": "string", "pattern": "^[0-9a-f]{64}$"},
			        "json": {"type": "string"},
			        "summary": {"type": "object"}
			      },
			      "required": ["path", "sha256", "json", "summary"],
			      "additionalProperties": false
			    },
			    "annotations": {"readOnlyHint": true, "destructiveHint": false, "idempotentHint": true, "openWorldHint": false}
			  },
			  {
			    "name": "openspine_read_project_chunk",
			    "title": "Read OpenSpine JSON project chunk",
			    "description": "Read and validate a bounded Unicode character range from a Spine JSON project. Returns the full-file SHA-256, summary, range metadata, and JSON text chunk.",
			    "inputSchema": {
			      "type": "object",
			      "properties": {
			        "path": {"type": "string", "description": "JSON project path under the configured MCP root."},
			        "offset_chars": {"type": "integer", "minimum": 0, "default": 0, "description": "Unicode character offset from the start of the file."},
			        "max_chars": {"type": "integer", "minimum": 1, "maximum": 262144, "default": 65536, "description": "Maximum Unicode characters to return."}
			      },
			      "required": ["path"],
			      "additionalProperties": false
			    },
			    "outputSchema": {
			      "type": "object",
			      "properties": {
			        "path": {"type": "string"},
			        "sha256": {"type": "string", "pattern": "^[0-9a-f]{64}$"},
			        "offset_chars": {"type": "integer", "minimum": 0},
			        "returned_chars": {"type": "integer", "minimum": 0},
			        "total_chars": {"type": "integer", "minimum": 0},
			        "complete": {"type": "boolean"},
			        "next_offset_chars": {"type": "integer", "minimum": 0},
			        "json_chunk": {"type": "string"},
			        "summary": {"type": "object"}
			      },
			      "required": ["path", "sha256", "offset_chars", "returned_chars", "total_chars", "complete", "json_chunk", "summary"],
			      "additionalProperties": false
			    },
			    "annotations": {"readOnlyHint": true, "destructiveHint": false, "idempotentHint": true, "openWorldHint": false}
			  },
			  {
			    "name": "openspine_validate_project",
			    "title": "Validate OpenSpine project",
			    "description": "Validate either a project path (.json/.skel) or an in-memory Spine JSON string with the official 4.3 runtime and structural safety checks. Checks triangle/edge indices, UV and vertex counts, hull, weighted bone indices and finite geometry values. Empty animation timelines are rejected with their JSON paths; omit unused timelines. Non-normalized weights produce warnings without changing the data. This is structure-only validation: textures_checked and render_checked are false. Use render_preview to check resources and requested rendered frames. Provide exactly one input.",
			    "inputSchema": {
			      "type": "object",
			      "properties": {
			        "path": {"type": "string", "description": "Project path under the configured MCP root."},
			        "json": {"type": "string", "description": "Complete Spine JSON document to validate without writing."}
			      },
			      "oneOf": [{"required": ["path"]}, {"required": ["json"]}],
			      "additionalProperties": false
			    },
			    "outputSchema": {
			      "type": "object",
			      "properties": {
			        "valid": {"type": "boolean"},
			        "structure_valid": {"type": "boolean"},
			        "textures_checked": {"type": "boolean"},
			        "render_checked": {"type": "boolean"},
			        "warnings": {"type": "array", "items": {"type": "string"}},
			        "summary": {"type": "object"}
			      },
			      "required": ["valid", "structure_valid", "textures_checked", "render_checked", "warnings", "summary"],
			      "additionalProperties": false
			    },
			    "annotations": {"readOnlyHint": true, "destructiveHint": false, "idempotentHint": true, "openWorldHint": false}
			  },
			  {
			    "name": "openspine_write_project",
			    "title": "Write OpenSpine JSON project",
			    "description": "Validate and atomically write a Spine JSON project under the configured MCP root. Existing files require the SHA-256 returned by read_project unless overwrite=true is explicitly supplied.",
			    "inputSchema": {
			      "type": "object",
			      "properties": {
			        "path": {"type": "string", "description": "Destination .json path under the configured MCP root."},
			        "json": {"type": "string", "description": "Complete Spine JSON document."},
			        "expected_sha256": {"type": "string", "pattern": "^[0-9A-Fa-f]{64}$", "description": "SHA-256 from read_project; required for an existing file unless overwrite is true."},
			        "overwrite": {"type": "boolean", "default": false, "description": "Explicitly bypass the read-before-write requirement. A supplied SHA-256 is still checked."},
			        "create_backup": {"type": "boolean", "default": false, "description": "Copy the previous file to <name>.bak before replacement."}
			      },
			      "required": ["path", "json"],
			      "additionalProperties": false
			    },
			    "outputSchema": {
			      "type": "object",
			      "properties": {
			        "created": {"type": "boolean"},
			        "path": {"type": "string"},
			        "sha256": {"type": "string", "pattern": "^[0-9a-f]{64}$"},
			        "backup_path": {"type": "string"},
			        "warnings": {"type": "array", "items": {"type": "string"}},
			        "summary": {"type": "object"}
			      },
			      "required": ["created", "path", "sha256", "summary"],
			      "additionalProperties": false
			    },
			    "annotations": {"readOnlyHint": false, "destructiveHint": true, "idempotentHint": true, "openWorldHint": false}
			  },
			  {
			    "name": "openspine_render_preview",
			    "title": "Render OpenSpine preview",
			    "description": "Render a saved .json/.skel project as inline PNG image content with a hidden OpenGL worker. Requires atlas textures or loose images inside the MCP root. Without animation returns setup pose; with animation returns an exact time or up to 8 evenly spaced frames with shared framing. Does not read or change the live editor. Total output is limited to 4 megapixels and 16 MiB PNG data; rendering times out after 45 seconds.",
			    "inputSchema": {
			      "type": "object",
			      "properties": {
			        "path": {"type": "string", "description": "Saved .json/.skel project path inside the MCP root."},
			        "animation": {"type": "string", "description": "Exact animation name. Omit for setup pose."},
			        "skin": {"type": "string", "description": "Exact skin name. Omit for the default skin."},
			        "atlas": {"type": "string", "description": "Optional sibling .atlas filename, needed when multiple atlases exist."},
			        "time": {"type": "number", "minimum": 0, "maximum": 60, "default": 0, "description": "Time in seconds, or the start of a sampled range."},
			        "end_time": {"type": "number", "minimum": 0, "maximum": 60, "description": "Requires frame_count > 1. Default is animation duration, capped at 60 seconds."},
			        "frame_count": {"type": "integer", "minimum": 1, "maximum": 8, "default": 1},
			        "width": {"type": "integer", "minimum": 64, "maximum": 2048, "default": 512},
			        "height": {"type": "integer", "minimum": 64, "maximum": 2048, "default": 512},
			        "padding": {"type": "integer", "minimum": 0, "maximum": 256, "default": 24},
			        "transparent": {"type": "boolean", "default": false},
			        "background": {"type": "string", "pattern": "^#[0-9a-fA-F]{6}$", "default": "#24262b"}
			      },
			      "required": ["path"],
			      "additionalProperties": false
			    },
			    "outputSchema": {
			      "type": "object",
			      "properties": {
			        "kind": {"type": "string", "enum": ["render_preview"]},
			        "path": {"type": "string"},
			        "sha256": {"type": "string"},
			        "width": {"type": "integer"},
			        "height": {"type": "integer"},
			        "frame_count": {"type": "integer"},
			        "frames": {"type": "array", "description": "Time and zero-based image_index for each image content block."}
			      },
			      "required": ["kind", "path", "sha256", "width", "height", "frame_count", "frames"],
			      "additionalProperties": true
			    },
			    "annotations": {"readOnlyHint": true, "destructiveHint": false, "idempotentHint": true, "openWorldHint": false}
			  },
			  {
			    "name": "openspine_capture_screenshot",
			    "title": "Capture OpenSpine editor",
			    "description": "Return an inline PNG screenshot of the live OpenSpine editor client area, including visible unsaved content. Never captures the desktop or other applications and does not focus or control the editor. Requires an editor launched with --mcp-capture-root matching MCP --root, with the requested project active and all open documents saved inside that root. Times out after 10 seconds when the editor is not rendering. The editor UI may contain other visible text such as recent paths.",
			    "inputSchema": {
			      "type": "object",
			      "properties": {
			        "path": {"type": "string", "description": "Active .json, .skel or .spine project path inside the MCP root."},
			        "editor_pid": {"type": "integer", "minimum": 1, "description": "Select an editor process when multiple instances have this project active."},
			        "max_width": {"type": "integer", "minimum": 64, "maximum": 2048, "default": 1280},
			        "max_height": {"type": "integer", "minimum": 64, "maximum": 2048, "default": 960}
			      },
			      "required": ["path"],
			      "additionalProperties": false
			    },
			    "outputSchema": {
			      "type": "object",
			      "properties": {
			        "kind": {"type": "string", "enum": ["editor_screenshot"]},
			        "path": {"type": "string"},
			        "editor_pid": {"type": "integer"},
			        "captured_at": {"type": "string"},
			        "width": {"type": "integer"},
			        "height": {"type": "integer"},
			        "unsaved_changes": {"type": "boolean"}
			      },
			      "required": ["kind", "path", "editor_pid", "captured_at", "width", "height", "unsaved_changes"],
			      "additionalProperties": true
			    },
			    "annotations": {"readOnlyHint": true, "destructiveHint": false, "idempotentHint": false, "openWorldHint": false}
			  }
			]
			""");
	}

	private static void validateEnvelope(JsonValue request) throws ProtocolFailure {
		if (request == null || !request.isObject()) throw protocol(-32600, "Invalid Request");
		JsonValue version = request.get("jsonrpc");
		JsonValue method = request.get("method");
		if (version == null || !version.isString() || !"2.0".equals(version.asString())
			|| method == null || !method.isString() || method.asString().isBlank())
			throw protocol(-32600, "Invalid Request");
		JsonValue id = request.get("id");
		if (!isValidRequestId(id)) throw protocol(-32600, "Invalid Request");
		JsonValue params = request.get("params");
		if (params != null && !params.isObject())
			throw protocol(-32600, "Invalid Request");
	}

	private static boolean isValidRequestId(JsonValue id) {
		return id == null || isRequestIdValue(id);
	}

	private static boolean isRequestIdValue(JsonValue id) {
		if (id == null || id.isNull()) return false;
		if (id.isString()) return true;
		if (!id.isNumber()) return false;
		double value = id.asDouble();
		return Double.isFinite(value) && value == Math.rint(value);
	}

	/** Normalizes ids for duplicate detection and cancellation lookups: integer and decimal spellings of the
	 * same number (1 vs 1.0) share one key, and string ids can never collide with numeric ids. */
	static String requestIdKey(JsonValue id) {
		if (id.isString()) return "s:" + id.asString();
		if (id.type() == JsonValue.ValueType.longValue) return "n:" + id.asLong();
		return "n:" + new BigDecimal(id.asDouble()).toBigInteger();
	}

	private String readMessageLine() throws IOException, MessageTooLargeException {
		MessageBuffer buffer = new MessageBuffer(maxMessageBytes);
		boolean receivedAnyByte = false;
		boolean tooLarge = false;
		int next;
		while ((next = input.read()) != -1) {
			receivedAnyByte = true;
			if (next == '\n') break;
			if (tooLarge) continue;
			if (buffer.size() == maxMessageBytes) {
				tooLarge = true;
			} else {
				buffer.add((byte)next);
			}
		}
		if (!receivedAnyByte && next == -1) return null;
		if (tooLarge) throw new MessageTooLargeException();
		return buffer.decodeUtf8();
	}

	private synchronized void send(JsonValue response) {
		output.println(response.toJson(OutputType.json));
		output.flush();
	}

	private void shutdownToolExecutor() {
		toolExecutor.shutdown();
		try {
			if (!toolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
				pendingToolCalls.values().forEach(PendingToolCall::cancel);
				toolExecutor.shutdownNow();
				toolExecutor.awaitTermination(5, TimeUnit.SECONDS);
			}
		} catch (InterruptedException interrupted) {
			pendingToolCalls.values().forEach(PendingToolCall::cancel);
			toolExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private static JsonValue success(JsonValue id, JsonValue result) {
		JsonValue response = object();
		put(response, "jsonrpc", "2.0");
		put(response, "id", copyOrNull(id));
		put(response, "result", result);
		return response;
	}

	private static JsonValue error(JsonValue id, int code, String message, JsonValue data) {
		JsonValue response = object();
		put(response, "jsonrpc", "2.0");
		put(response, "id", copyOrNull(id));
		JsonValue error = object();
		put(error, "code", code);
		put(error, "message", message);
		if (data != null) put(error, "data", data);
		put(response, "error", error);
		return response;
	}

	private static JsonValue requireObject(JsonValue value, String label) throws ProtocolFailure {
		if (value == null || !value.isObject()) throw protocol(-32602, label + " must be an object.");
		return value;
	}

	private static String requireString(JsonValue object, String name) throws ProtocolFailure {
		JsonValue value = object.get(name);
		if (value == null || !value.isString() || value.asString().isBlank())
			throw protocol(-32602, name + " must be a non-empty string.");
		return value.asString();
	}

	private static String optionalString(JsonValue object, String name) throws ProtocolFailure {
		JsonValue value = object.get(name);
		if (value == null || value.isNull()) return null;
		if (!value.isString()) throw protocol(-32602, name + " must be a string.");
		return value.asString();
	}

	private static boolean optionalBoolean(JsonValue object, String name, boolean fallback) throws ProtocolFailure {
		JsonValue value = object.get(name);
		if (value == null) return fallback;
		if (!value.isBoolean()) throw protocol(-32602, name + " must be a boolean.");
		return value.asBoolean();
	}

	private static int optionalInteger(JsonValue object, String name, int fallback, int minimum, int maximum)
		throws ProtocolFailure {
		JsonValue value = object.get(name);
		if (value == null) return fallback;
		if (!value.isNumber()) throw protocol(-32602, name + " must be an integer.");
		double numeric = value.asDouble();
		if (!Double.isFinite(numeric) || numeric != Math.rint(numeric) || numeric < minimum || numeric > maximum)
			throw protocol(-32602, name + " must be an integer from " + minimum + " to " + maximum + ".");
		return (int)numeric;
	}

	private static JsonValue copy(JsonValue value) {
		return new JsonReader().parse(value.toJson(OutputType.json));
	}

	private static JsonValue copyOrNull(JsonValue value) {
		return value == null || value.isNull() ? new JsonValue(JsonValue.ValueType.nullValue) : copy(value);
	}

	private static JsonValue supportedVersions() {
		JsonValue versions = array();
		versions.addChild(new JsonValue(LATEST_PROTOCOL_VERSION));
		LEGACY_PROTOCOL_VERSIONS.stream().sorted().forEach(version -> versions.addChild(new JsonValue(version)));
		return versions;
	}

	private static JsonValue object() {
		return new JsonValue(JsonValue.ValueType.object);
	}

	private static JsonValue array() {
		return new JsonValue(JsonValue.ValueType.array);
	}

	private static void put(JsonValue object, String name, JsonValue value) {
		object.addChild(name, value == null ? new JsonValue(JsonValue.ValueType.nullValue) : value);
	}

	private static void put(JsonValue object, String name, String value) {
		put(object, name, value == null ? null : new JsonValue(value));
	}

	private static void put(JsonValue object, String name, boolean value) {
		put(object, name, new JsonValue(value));
	}

	private static void put(JsonValue object, String name, int value) {
		put(object, name, new JsonValue((long)value));
	}

	private static void put(JsonValue object, String name, long value) {
		put(object, name, new JsonValue(value));
	}

	private static ProtocolFailure protocol(int code, String message) {
		return new ProtocolFailure(code, message == null ? "Invalid params" : message, null);
	}

	private static String safeMessage(Throwable failure) {
		String message = failure.getMessage();
		return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
	}

	private static final class MessageBuffer {
		private final int maximumSize;
		private byte[] bytes;
		private int size;

		MessageBuffer(int maximumSize) {
			this.maximumSize = maximumSize;
			bytes = new byte[Math.min(8192, maximumSize)];
		}

		int size() {
			return size;
		}

		void add(byte value) {
			if (size == bytes.length) {
				int expanded = size > maximumSize / 2 ? maximumSize : size * 2;
				bytes = Arrays.copyOf(bytes, Math.max(size + 1, expanded));
			}
			bytes[size++] = value;
		}

		String decodeUtf8() throws CharacterCodingException {
			int length = size > 0 && bytes[size - 1] == '\r' ? size - 1 : size;
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes, 0, length)).toString();
		}
	}

	private static final class MessageTooLargeException extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private static final class PendingToolCall {
		final AtomicBoolean cancelled = new AtomicBoolean();
		volatile Future<?> future;

		void cancel() {
			cancelled.set(true);
			Future<?> active = future;
			if (active != null) active.cancel(true);
		}
	}

	private static final class ProtocolFailure extends Exception {
		private static final long serialVersionUID = 1L;

		final int code;
		final JsonValue data;

		ProtocolFailure(int code, String message, JsonValue data) {
			super(message);
			this.code = code;
			this.data = data;
		}
	}
}
