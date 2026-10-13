package com.openspine.io;

import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.Skin;
import com.openspine.model.SkeletonDocument;

/** Checks the JSON wire format independently of libGDX's permissive child iteration. */
public final class SkinJsonExportTest {
	private static final String MULTI_SKIN = """
		{
		  "skeleton": {"spine": "4.3.0"},
		  "bones": [{"name":"root"}, {"name":"body","parent":"root"},
		    {"name":"target","parent":"root","x":20}, {"name":"extra","parent":"root","skin":true}],
		  "slots": [{"name":"body","bone":"body","attachment":"quad"}],
		  "constraints": [{"name":"follow","type":"ik","bones":["body"],"target":"target","skin":true}],
		  "skins": [
		    {"name":"皮肤\\\"夜\\\\色","bones":["extra"],"ik":["follow"],"color":"336699ff",
		      "attachments":{"body":{"copy":{"type":"linkedmesh","skin":"default","source":"quad"}}}},
		    {"name":"default","attachments":{"body":{
		      "quad":{"type":"mesh","uvs":[0,0,1,0,1,1,0,1],"triangles":[0,1,2,2,3,0],
		        "vertices":[-5,-5,5,-5,5,5,-5,5],"hull":4,"width":10,"height":10},
		      "sprite":{"width":10,"height":12,"sequence":{"count":2,"start":1,"digits":2}},
		      "point":{"type":"point","x":3,"y":4}
		    }}},
		    {"name":"empty","attachments":{}}
		  ],
		  "animations":{"move":{
		    "bones":{"body":{"rotate":[{"value":0},{"time":1,"value":30}]}},
		    "attachments":{"default":{"body":{
		      "quad":{"deform":[{}, {"time":1,"vertices":[1,2],"offset":0}]},
		      "sprite":{"sequence":[{"mode":"loop","index":0,"delay":0.2}]}
		    }}}
		  }}
		}
		""";

	public static void main(String[] args) throws Exception {
		Path output = args.length == 0 ? null : Path.of(args[0]);
		if (output != null) Files.createDirectories(output);
		verifyExport("no-skins", "{\"bones\":[{\"name\":\"root\"}]}", output);
		verifyExport("empty-default", "{\"bones\":[{\"name\":\"root\"}],\"skins\":[{\"name\":\"default\"}]}", output);
		for (String skinName : new String[] {"default", "named"}) {
			verifyExport("single-" + skinName, """
				{"bones":[{"name":"root"}],"slots":[{"name":"body","bone":"root"}],
				 "skins":[{"name":"%s","attachments":{"body":{"sprite":{"width":10,"height":12}}}}]}
				""".formatted(skinName), output);
		}
		verifyExport("multi-skin", MULTI_SKIN, output);
		// Previously saved OpenSpine JSON must still load and be corrected on export.
		verifyExport("legacy-example", ExampleSkeleton.JSON, output);
		System.out.println("ALL SKIN JSON EXPORT TESTS PASSED");
	}

	private static void verifyExport(String name, String input, Path output) throws Exception {
		SkeletonData original = load(new JsonReader().parse(input));
		SkeletonDocument document = new SkeletonDocument();
		document.data = original;
		String json = SkeletonExporter.toJson(document);
		JsonValue root = new JsonReader().parse(json);
		JsonValue skins = root.require("skins");
		check(skins.isArray(), name + ": skins must be an array, even when empty (libGDX also accepts objects)");
		check(skins.size == original.getSkins().size, name + ": skin count");
		for (int index = 0; index < skins.size; index++) {
			Skin expected = original.getSkins().get(index);
			JsonValue actual = skins.get(index);
			check(actual.isObject() && expected.getName().equals(actual.getString("name")), name + ": skin name/order");
			JsonValue attachments = actual.require("attachments");
			check(attachments.isObject(), name + ": attachments remain a slot-keyed object");
			int count = 0;
			for (JsonValue slot : attachments) count += slot.size;
			check(count == expected.getAttachments().size, name + ": attachment count in " + expected.getName());
			for (Skin.SkinEntry entry : expected.getAttachments()) {
				String slot = original.getSlots().get(entry.getSlotIndex()).getName();
				check(attachments.require(slot).has(entry.getPlaceholder()), name + ": attachment placeholder " + entry.getPlaceholder());
			}
		}
		SkeletonData reloaded = load(root);
		for (Skin expected : original.getSkins()) {
			Skin actual = reloaded.findSkin(expected.getName());
			check(actual != null && actual.getAttachments().size == expected.getAttachments().size,
				name + ": attachments reload for " + expected.getName());
			check(actual.getBones().size == expected.getBones().size && actual.getConstraints().size == expected.getConstraints().size,
				name + ": skin activation metadata reloads");
		}
		document.data = reloaded;
		check(json.equals(SkeletonExporter.toJson(document)), name + ": export/load/export preserves all JSON data");
		if (output != null) Files.writeString(output.resolve(name + ".json"), json);
	}

	private static SkeletonData load(JsonValue root) {
		return new SkeletonJson(new ExportFileTest.StubLoader()).readSkeletonData(root);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
