package com.openspine.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.openspine.io.SkeletonStructureValidator;

/** Regression for empty timelines and the runtime cause information returned to MCP clients. */
public final class McpAnimationValidationTest {
	private static final String BASE = "{\"skeleton\":{\"spine\":\"4.3.0\"},\"bones\":[{\"name\":\"root\"}]}";

	public static void main(String[] args) throws Exception {
		Path root = Files.createTempDirectory("openspine-animation-validation-").toRealPath();
		try {
			testEmptyTimelines(root);
			testValidEmptyValues(root);
			testRuntimeCauses(root);
			testBoundedCauses();
			System.out.println("OK: empty timeline paths, omitted fields, legal empty frame values and bounded runtime causes passed");
		} finally {
			try (var paths = Files.walk(root)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	private static void testEmptyTimelines(Path root) throws Exception {
		for (Case sample : List.of(
			new Case("{\"events\":[]}", "events"),
			new Case("{\"drawOrder\":[]}", "drawOrder"),
			new Case("{\"bones\":{\"root\":{\"rotate\":[]}}}", "bones.root.rotate"),
			new Case("{\"slots\":{\"body\":{\"attachment\":[]}}}", "slots.body.attachment"),
			new Case("{\"ik\":{\"leg\":[]}}", "ik.leg"),
			new Case("{\"transform\":{\"follow\":[]}}", "transform.follow"),
			new Case("{\"path\":{\"follow\":{\"position\":[]}}}", "path.follow.position"),
			new Case("{\"physics\":{\"spring\":{\"reset\":[]}}}", "physics.spring.reset"),
			new Case("{\"slider\":{\"blend\":{\"value\":[]}}}", "slider.blend.value"),
			new Case("{\"attachments\":{\"default\":{\"body\":{\"quad\":{\"deform\":[]}}}}}", "attachments.default.body.quad.deform"),
			new Case("{\"attachments\":{\"default\":{\"body\":{\"quad\":{\"sequence\":[]}}}}}", "attachments.default.body.quad.sequence"),
			new Case("{\"drawOrderFolder\":{\"body\":{\"slots\":[],\"keys\":[]}}}", "drawOrderFolder.body.keys"))) {
			String message = error(validate(root, project("idle", sample.animation())));
			check(message.contains("animations.idle." + sample.path()) && message.contains("at least one keyframe"),
				"empty timeline identifies its full path: " + message);
		}
		for (String malformed : List.of("null", "{}", "42", "[null]", "[[]]")) {
			String message = error(validate(root, project("idle", "{\"events\":" + malformed + "}")));
			check(message.contains("animations.idle.events") && message.contains("keyframe"), "malformed timeline has context");
		}
		String input = project("idle", "{\"events\":[]}");
		Files.writeString(root.resolve("empty-events.json"), input);
		JsonValue path = new JsonReader().parse("{\"path\":\"empty-events.json\"}");
		for (String name : List.of("openspine_validate_project", "openspine_read_project", "openspine_inspect_project", "openspine_render_preview")) {
			String message = error(McpVisualToolsTest.call(root, name, path));
			check(message.contains("animations.idle.events"), "disk entry point retains the offending path: " + name);
		}
		JsonValue write = new JsonReader().parse("{\"path\":\"new.json\"}");
		write.addChild("json", new JsonValue(input));
		check(error(McpVisualToolsTest.call(root, "openspine_write_project", write)).contains("animations.idle.events"), "write is prevalidated");
		check(!Files.exists(root.resolve("new.json")), "invalid timeline is not written");
		check(Files.readString(root.resolve("empty-events.json")).equals(input), "preflight does not normalize or modify files");
	}

	private static void testValidEmptyValues(Path root) throws Exception {
		for (String animation : List.of("{}", "{\"bones\":{},\"slots\":{},\"attachments\":{},\"physics\":{}}",
			"{\"bones\":{\"root\":{\"rotate\":[{\"time\":0,\"value\":5}]}}}",
			"{\"drawOrder\":[{\"time\":0,\"offsets\":[]}]}")) {
			JsonValue result = validate(root, project("idle", animation));
			check(!result.getBoolean("isError") && result.get("structuredContent").getBoolean("valid"), "omitted or populated timeline accepted: " + result);
		}
		JsonValue deform = new JsonReader().parse(project("idle", "{\"attachments\":{\"default\":{\"body\":{\"quad\":{\"deform\":[{\"vertices\":[]}]}}}}}"));
		String original = deform.toJson(OutputType.json);
		SkeletonStructureValidator.validateJson(deform);
		check(original.equals(deform.toJson(OutputType.json)), "empty deform vertices remain untouched by preflight");
		JsonValue folder = new JsonReader().parse(project("idle", "{\"drawOrderFolder\":{\"body\":{\"slots\":[],\"keys\":[{\"offsets\":[]}]}}}"));
		SkeletonStructureValidator.validateJson(folder);
		check(error(validate(root, project("idle.front", "{\"events\":[]}"))).contains("\"idle.front\".events"), "dotted animation name is quoted in the path");
	}

	private static void testRuntimeCauses(Path root) throws Exception {
		String message = error(validate(root, project("idle_front", "{\"bones\":{\"missing_bone\":{\"rotate\":[{\"value\":5}]}}}")));
		check(message.contains("idle_front") && message.contains("missing_bone") && message.contains("Bone not found"), "runtime animation and root cause survive wrappers: " + message);
		message = error(validate(root, project("idle_front", "{\"events\":[{\"name\":\"missing_event\"}]}")));
		check(message.contains("idle_front") && message.contains("missing_event") && message.contains("Event not found"), "runtime event cause survives wrappers: " + message);
	}

	private static void testBoundedCauses() {
		RuntimeException first = new RuntimeException("outer\ncontext");
		RuntimeException second = new IllegalStateException("inner\tcontext");
		first.initCause(second);
		second.initCause(first);
		String message = SpineProjectTools.message(first);
		check(message.contains("outer context") && message.contains("inner context") && message.contains("omitted"), "cyclic causes terminate with context");
		check(!message.contains("\n") && !message.contains("\t"), "cause text is compact");
		message = SpineProjectTools.message(new RuntimeException("x".repeat(20000), new IllegalArgumentException("root reason")));
		check(message.length() <= 4096 && message.contains("root reason"), "long wrapper cannot crowd out the root cause");
		Throwable deep = new IllegalArgumentException("root");
		for (int index = 0; index < 20; index++) deep = new RuntimeException("layer " + index, deep);
		message = SpineProjectTools.message(deep);
		check(message.length() <= 4096 && message.contains("omitted"), "cause depth is bounded");
		check(SpineProjectTools.message(new IllegalArgumentException()).contains("IllegalArgumentException"), "missing cause message uses its type");
	}

	private static String project(String name, String animation) {
		JsonValue data = new JsonReader().parse(BASE);
		JsonValue animations = new JsonValue(JsonValue.ValueType.object);
		animations.addChild(name, new JsonReader().parse(animation));
		data.addChild("animations", animations);
		return data.toJson(OutputType.json);
	}
	private static JsonValue validate(Path root, String input) throws Exception {
		JsonValue arguments = new JsonValue(JsonValue.ValueType.object);
		arguments.addChild("json", new JsonValue(input));
		return McpVisualToolsTest.call(root, "openspine_validate_project", arguments);
	}
	private static String error(JsonValue result) {
		check(result.getBoolean("isError"), "expected tool error: " + result);
		return result.get("content").get(0).getString("text");
	}
	private record Case(String animation, String path) {}
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private McpAnimationValidationTest() {}
}
