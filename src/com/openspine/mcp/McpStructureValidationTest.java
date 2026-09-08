package com.openspine.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.io.SkeletonBinaryExporter;
import com.openspine.model.SkeletonDocument;

/** Reproduces the reported four-vertex/triangle-1000 bug without sending bad geometry to GL. */
public final class McpStructureValidationTest {
	private static final String QUAD = """
		{"skeleton":{"spine":"4.3.0","images":"missing_images/"},
		 "bones":[{"name":"root"},{"name":"child","parent":"root"}],
		 "slots":[{"name":"body","bone":"root","attachment":"quad"}],
		 "skins":{"default":{"name":"default","attachments":{"body":{"quad":{
		 "type":"mesh","path":"missing_texture","width":32,"height":32,
		 "uvs":[0,0,1,0,1,1,0,1],"vertices":[0,32,32,32,32,0,0,0],
		 "triangles":[0,1,2,2,3,0],"hull":4}}}}}}
		""";
	private static final String WEIGHTED = "[1,0,0,32,1, 1,0,32,32,1, 1,0,32,0,1, 1,0,0,0,1]";

	public static void main(String[] args) throws Exception {
		Path root = Files.createTempDirectory("openspine-mesh-validation-").toRealPath();
		try {
			SpineProjectTools tools = new SpineProjectTools(root);
			JsonValue valid = tools.validateProject(null, QUAD);
			check(valid.getBoolean("valid") && valid.getBoolean("structure_valid"), "valid geometry accepted without textures");
			check(!valid.getBoolean("textures_checked") && !valid.getBoolean("render_checked"), "structure-only scope is explicit");
			check(valid.get("warnings").size == 0, "unweighted mesh has no normalization warning");
			check(tools.validateProject(null, mutate("vertices", WEIGHTED)).getBoolean("valid"), "normalized weights accepted");
			for (Case invalid : List.of(
				new Case("triangles", "[0,1,1000,2,3,0]"), new Case("triangles", "[0,1,65536,2,3,0]"),
				new Case("triangles", "[0,1,-1,2,3,0]"), new Case("triangles", "[0,1,2.5,2,3,0]"),
				new Case("triangles", "[0,1,2,2,3]"), new Case("triangles", "[]"), new Case("triangles", "[0,1,\"2\",2,3,0]"),
				new Case("uvs", "[0,0,1]"), new Case("uvs", "[0,0,1,0,1,1]"), new Case("uvs", "[0,0,1e100,0,1,1,0,1]"),
				new Case("hull", "5"), new Case("hull", "-1"), new Case("hull", "2"), new Case("hull", "4294967296"),
				new Case("edges", "[0,1,2]"), new Case("edges", "[0,1000]"), new Case("edges", "[0,65536]"),
				new Case("vertices", "[1,9,0,32,1,1,0,32,32,1,1,0,32,0,1,1,0,0,0,1]"),
				new Case("vertices", "[1,0.5,0,32,1,1,0,32,32,1,1,0,32,0,1,1,0,0,0,1]"),
				new Case("vertices", "[1.5,0,0,32,1,1,0,32,32,1,1,0,32,0,1,1,0,0,0,1]"),
				new Case("vertices", "[1,0,0,32,0,1,0,32,32,1,1,0,32,0,1,1,0,0,0,1]"),
				new Case("vertices", "[1,0,0,32,-1,1,0,32,32,1,1,0,32,0,1,1,0,0,0,1]"),
				new Case("vertices", "[2,0,0,32,0.5,0,0,32,0.5,1,0,32,32,1,1,0,32,0,1,1,0,0,0,1]"),
				new Case("vertices", "[1,0,0,32,1,1,0,32,32,1,1,0,32,0,1,1,0,0,0]"),
				new Case("vertices", "[0,32,32,32,32,0,0,1e100]"))) {
				String input = mutate(invalid.field(), invalid.value());
				expectFailure(() -> tools.validateProject(null, input), "invalid " + invalid.field() + " rejected");
			}
			String unnormalized = mutate("vertices", "[1,0,0,32,0.7,1,0,32,32,1,1,0,32,0,1,1,0,0,0,1]");
			JsonValue warning = tools.validateProject(null, unnormalized);
			check(warning.getBoolean("valid") && warning.get("warnings").size == 1, "non-normalized weights are reported, not rewritten");
			JsonValue linked = new JsonReader().parse(QUAD);
			linked.get("skins").get("default").get("attachments").get("body").addChild("copy",
				new JsonReader().parse("{\"type\":\"linkedmesh\",\"source\":\"quad\",\"skin\":\"default\",\"path\":\"missing_texture\"}"));
			check(tools.validateProject(null, linked.toJson(OutputType.json)).getBoolean("valid"), "linked geometry remains supported");
			String broken = mutate("triangles", "[0,1,1000,2,3,0]");
			JsonValue protocolArguments = new JsonValue(JsonValue.ValueType.object);
			protocolArguments.addChild("json", new JsonValue(broken));
			JsonValue protocol = McpVisualToolsTest.call(root, "openspine_validate_project", protocolArguments);
			check(protocol.getBoolean("isError") && protocol.toString().contains("1000"), "reported triangle-1000 probe fails through JSON-RPC");
			JsonValue stored = tools.writeProject("safe.json", QUAD, null, false, false);
			byte[] before = Files.readAllBytes(root.resolve("safe.json"));
			expectFailure(() -> tools.writeProject("safe.json", broken, stored.getString("sha256"), false, true), "invalid replacement rejected");
			check(Arrays.equals(before, Files.readAllBytes(root.resolve("safe.json"))) && !Files.exists(root.resolve("safe.json.bak")), "failed writes do not change the destination or backup");
			expectFailure(() -> tools.writeProject("new.json", broken, null, false, false), "invalid creation rejected");
			check(!Files.exists(root.resolve("new.json")), "invalid creation leaves no file");
			Files.writeString(root.resolve("broken.json"), broken);
			expectFailure(() -> tools.inspectProject("broken.json"), "inspection rejects unsafe geometry");
			expectFailure(() -> tools.readProject("broken.json"), "read rejects unsafe geometry");
			check(tools.listProjects(".", 0, 0, 100).getInt("skipped_invalid") == 1, "discovery excludes unsafe geometry");
			testBinary(root, tools);
			System.out.println("OK: MCP geometry validation, protected writes, binary indices and validation scope passed");
		} finally {
			try (var files = Files.walk(root)) {
				for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
			}
		}
	}

	private static void testBinary(Path root, SpineProjectTools tools) throws Exception {
		SkeletonData data = new SkeletonData();
		BoneData bone = new BoneData(0, "root", null);
		bone.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(bone);
		SlotData slot = new SlotData(0, "body", bone);
		slot.setBlendMode(com.esotericsoftware.spine.BlendMode.normal);
		data.getSlots().add(slot);
		Skin skin = new Skin("default");
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);
		MeshAttachment mesh = new MeshAttachment("quad", new Sequence(1, false));
		mesh.setPath("missing_texture");
		mesh.setWorldVerticesLength(8);
		mesh.setVertices(new float[] {0,32,32,32,32,0,0,0});
		mesh.setRegionUVs(new float[] {0,0,1,0,1,1,0,1});
		mesh.setTriangles(new short[] {0,1,2,2,3,0});
		mesh.setHullLength(8);
		skin.setAttachment(0, "quad", mesh);
		SkeletonDocument document = new SkeletonDocument();
		document.data = data;
		document.skeleton = new Skeleton(data);
		Files.write(root.resolve("valid.skel"), SkeletonBinaryExporter.toBytes(document));
		check(tools.validateProject("valid.skel", null).getBoolean("valid"), "binary geometry accepted");
		mesh.getTriangles()[2] = 1000;
		Files.write(root.resolve("broken.skel"), SkeletonBinaryExporter.toBytes(document));
		expectFailure(() -> tools.validateProject("broken.skel", null), "binary triangle range checked after parsing");
	}

	private static String mutate(String field, String value) {
		JsonValue root = new JsonReader().parse(QUAD);
		JsonValue mesh = root.get("skins").get("default").get("attachments").get("body").get("quad");
		mesh.remove(field);
		mesh.addChild(field, new JsonReader().parse(value));
		return root.toJson(OutputType.json);
	}
	private record Case(String field, String value) {}
	@FunctionalInterface private interface Action { void run() throws Exception; }
	private static void expectFailure(Action action, String label) throws Exception {
		try { action.run(); } catch (SpineProjectTools.ToolFailure expected) { return; }
		throw new AssertionError(label);
	}
	private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
	private McpStructureValidationTest() {}
}
