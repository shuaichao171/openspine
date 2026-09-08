package com.openspine.mcp;

import static com.openspine.mcp.McpImages.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.io.PreviewResourceGuard;
import com.openspine.io.SkeletonBinaryExporter;
import com.openspine.mcp.McpVisualTools.PreviewSettings;
import com.openspine.model.SkeletonDocument;

/** Headless contracts by default; --render <directory> additionally verifies the actual GL worker. */
public final class McpVisualToolsTest {
	private static final String PROJECT = """
		{"skeleton":{"spine":"4.3.0","images":"images/"},
		 "bones":[{"name":"root"},{"name":"body","parent":"root"}],
		 "slots":[{"name":"body","bone":"body","attachment":"body"}],
		 "skins":{"default":{"name":"default","attachments":{"body":{"body":{"path":"body","width":96,"height":128}}}}},
		 "animations":{"move":{"bones":{"body":{"rotate":[{"time":0,"value":0},{"time":1,"value":45}],
		 "translate":[{"time":0,"x":0},{"time":1,"x":40}]}}}}}
		""";

	public static void main(String[] args) throws Exception {
		if (args.length == 2 && args[0].equals("--render")) {
			Path output = Path.of(args[1]).toAbsolutePath().normalize();
			Files.createDirectories(output);
			testRendering(output.toRealPath());
			System.out.println("OK: MCP rendered PNGs verified at " + output);
			return;
		}
		Path root = Files.createTempDirectory("openspine-visual-test-").toRealPath();
		try {
			createFixture(root);
			testSettings();
			testResources(root);
			testCapture(root);
			System.out.println("OK: MCP visual tool contracts passed");
		} finally {
			try (var files = Files.walk(root)) {
				for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
			}
		}
	}

	private static void testSettings() throws Exception {
		PreviewSettings defaults = PreviewSettings.parse(json("{\"path\":\"hero.json\"}"));
		check(defaults.frames() == 1 && defaults.width() == 512, "preview defaults");
		for (String extra : List.of("\"width\":0", "\"width\":512.5", "\"width\":true", "\"time\":-1",
			"\"time\":1e999", "\"frame_count\":9", "\"frame_count\":2", "\"transparent\":1",
			"\"background\":\"red\"", "\"atlas\":\"../secret.atlas\"", "\"skin\":null",
			"\"width\":64,\"padding\":32", "\"width\":2048,\"height\":2048,\"frame_count\":2,\"animation\":\"move\"")) {
			expectFailure(() -> PreviewSettings.parse(json("{\"path\":\"hero.json\"," + extra + "}")), extra);
		}
		check(Arrays.equals(new int[] {1280, 720}, fit(1920, 1080, 1280, 960)), "screenshot preserves aspect ratio");
		check(Arrays.equals(new int[] {640, 480}, fit(640, 480, 1280, 960)), "screenshot never upscales");
	}

	private static void testResources(Path root) throws Exception {
		PreviewResourceGuard guard = new PreviewResourceGuard(root);
		check(guard.image(root.resolve("images/body.png")).startsWith(root), "authorized image accepted");
		expectFailure(() -> guard.directory(root.getParent()), "directory escape rejected");
		Path outside = Files.createTempFile("openspine-private-", ".png");
		try {
			Files.copy(root.resolve("images/body.png"), outside, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			expectFailure(() -> guard.image(outside), "external image rejected");
			Path link = root.resolve("outside.png");
			boolean linked = false;
			try { Files.createSymbolicLink(link, outside); linked = true; }
			catch (java.nio.file.FileSystemException unsupported) { }
			if (linked) expectFailure(() -> guard.image(link), "symlink escape rejected");
		} finally { Files.deleteIfExists(outside); }
	}

	private static void testCapture(Path root) throws Exception {
		McpVisualTools tools = new McpVisualTools(new SpineProjectTools(root));
		JsonValue args = json("{\"path\":\"preview-source.json\"}");
		expectFailure(() -> tools.screenshot(args), "screenshots require opt-in editor");
		expectFailure(() -> tools.screenshot(json("{\"path\":\"../outside.json\"}")), "screenshot path escape");
		Path project = root.resolve("preview-source.json");
		byte[] expected = Files.readAllBytes(root.resolve("images/body.png"));
		EditorCaptureBridge.Capture capture = (width, height) -> {
			JsonValue metadata = object();
			put(metadata, "width", 96);
			put(metadata, "height", 128);
			metadata.addChild("unsaved_changes", new JsonValue(true));
			return new Result(metadata, List.of(expected));
		};
		var executor = Executors.newSingleThreadExecutor();
		try (EditorCaptureBridge bridge = new EditorCaptureBridge(root)) {
			bridge.processRequests(project, List.of(project), capture);
			var future = executor.submit(() -> call(root, "openspine_capture_screenshot", args));
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (!future.isDone() && System.nanoTime() < deadline) {
				bridge.processRequests(project, List.of(project), capture);
				Thread.sleep(10);
			}
			JsonValue result = future.get(5, TimeUnit.SECONDS);
			check(!result.getBoolean("isError"), "screenshot protocol call succeeds");
			check(result.get("structuredContent").getBoolean("unsaved_changes"), "live state metadata is returned");
			check(result.get("content").size == 2, "text and image blocks are returned");
			JsonValue image = result.get("content").get(1);
			check("image".equals(image.getString("type")) && "image/png".equals(image.getString("mimeType")), "MCP image content type");
			check(Arrays.equals(expected, Base64.getDecoder().decode(image.getString("data"))), "image content preserves PNG bytes");
			var denied = executor.submit(() -> tools.screenshot(args));
			while (!denied.isDone() && System.nanoTime() < deadline) {
				bridge.processRequests(project, List.of(project, root.getParent()), capture);
				Thread.sleep(10);
			}
			expectFailure(() -> denied.get(5, TimeUnit.SECONDS), "outside open document blocks screenshot");
		} finally {
			executor.shutdownNow();
			check(executor.awaitTermination(5, TimeUnit.SECONDS), "capture worker terminated");
		}
		expectFailure(() -> tools.screenshot(args), "closed editor is not discoverable");
	}

	private static void testRendering(Path root) throws Exception {
		createFixture(root);
		byte[] before = Files.readAllBytes(root.resolve("preview-source.json"));
		JsonValue result = call(root, "openspine_render_preview", json("""
			{"path":"preview-source.json","animation":"move","time":0,"end_time":1,
			 "frame_count":3,"width":384,"height":384,"transparent":true}
			"""));
		check(!result.getBoolean("isError"), "render succeeded: " + result);
		JsonValue metadata = result.get("structuredContent");
		check(metadata.getInt("frame_count") == 3, "three sampled frames");
		check(metadata.getBoolean("structure_valid") && metadata.getBoolean("textures_checked")
			&& metadata.getBoolean("render_checked") && "requested_frames".equals(metadata.getString("render_scope")),
			"successful preview reports its limited validation scope");
		byte[] first = null;
		for (int index = 0; index < 3; index++) {
			byte[] png = Base64.getDecoder().decode(result.get("content").get(index + 1).getString("data"));
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
			check(image != null && image.getWidth() == 384 && image.getHeight() == 384, "PNG dimensions");
			check((image.getRGB(0, 0) >>> 24) == 0, "transparent background");
			int visible = 0;
			for (int y = 0; y < 384; y++) for (int x = 0; x < 384; x++) if ((image.getRGB(x, y) >>> 24) > 100) visible++;
			check(visible > 5000 && visible < 384 * 384 / 2, "nonblank, fitted frame");
			Files.write(root.resolve("preview-" + index + ".png"), png);
			if (first == null) first = png;
			else check(!Arrays.equals(first, png), "animation frames change");
		}
		JsonValue setup = call(root, "openspine_render_preview", json("{\"path\":\"preview-source.json\",\"width\":256,\"height\":320}"));
		check(!setup.getBoolean("isError"), "setup preview succeeds");
		byte[] setupPng = Base64.getDecoder().decode(setup.get("content").get(1).getString("data"));
		Files.write(root.resolve("setup-preview.png"), setupPng);
		BufferedImage setupImage = ImageIO.read(new ByteArrayInputStream(setupPng));
		check((setupImage.getRGB(128, 85) & 0x00ffffff) == 0xe24452, "PNG is upright: red panel at the top");
		check((setupImage.getRGB(128, 235) & 0x00ffffff) == 0x20bfa0, "PNG is upright: green panel at the bottom");
		JsonValue binary = call(root, "openspine_render_preview", json("{\"path\":\"preview-source.skel\",\"width\":256,\"height\":320}"));
		check(!binary.getBoolean("isError"), "binary loose-image preview succeeds: " + binary);
		byte[] binaryPng = Base64.getDecoder().decode(binary.get("content").get(1).getString("data"));
		check(Arrays.equals(setupPng, binaryPng), "JSON and loose-image SKEL setup previews are identical");
		Files.write(root.resolve("binary-setup-preview.png"), binaryPng);
		JsonValue invalid = call(root, "openspine_render_preview", json("{\"path\":\"preview-source.json\",\"animation\":\"missing\"}"));
		check(invalid.getBoolean("isError"), "missing animation reports a tool error");
		testRenderedResourceBoundary(root);
		testConcurrentRendering(root);
		check(Arrays.equals(before, Files.readAllBytes(root.resolve("preview-source.json"))), "preview never changes the project");
	}

	private static void testConcurrentRendering(Path root) throws Exception {
		var executor = Executors.newFixedThreadPool(2);
		try {
			List<Callable<JsonValue>> tasks = List.of(
				() -> call(root, "openspine_render_preview", json("{\"path\":\"preview-source.skel\",\"width\":256,\"height\":256,\"transparent\":true}")),
				() -> call(root, "openspine_render_preview", json("{\"path\":\"preview-source.json\",\"animation\":\"move\",\"time\":0.75,\"width\":256,\"height\":256,\"transparent\":true}")));
			var results = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);
			for (int index = 0; index < results.size(); index++) {
				JsonValue result = results.get(index).get();
				check(!result.getBoolean("isError"), "concurrent preview succeeds: " + result);
				byte[] png = Base64.getDecoder().decode(result.get("content").get(1).getString("data"));
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
				check(image != null && image.getWidth() == 256 && image.getHeight() == 256, "concurrent PNG dimensions");
				check((image.getRGB(128, 128) >>> 24) > 100 && (image.getRGB(0, 0) >>> 24) == 0,
					"concurrent preview contains a visible, fitted attachment");
				Files.write(root.resolve("concurrent-preview-" + index + ".png"), png);
			}
		} finally {
			executor.shutdownNow();
			check(executor.awaitTermination(10, TimeUnit.SECONDS), "concurrent render requests terminated");
		}
	}

	private static void testRenderedResourceBoundary(Path root) throws Exception {
		Path outside = Files.createTempDirectory(root.getParent(), "mcp-external-images-").toRealPath();
		Path atlas = root.resolve("preview-check.atlas");
		try {
			Files.copy(root.resolve("images/body.png"), outside.resolve("body.png"));
			JsonValue escape = json(PROJECT);
			escape.get("skeleton").get("images").set(outside.toString());
			Files.writeString(root.resolve("escape.json"), escape.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json));
			JsonValue denied = call(root, "openspine_render_preview", json("{\"path\":\"escape.json\"}"));
			check(denied.getBoolean("isError") && denied.toString().contains("outside the configured MCP root"), "loose image escape rejected by the actual worker");
			String atlasBody = """
				size: 96,128
				format: RGBA8888
				filter: Linear,Linear
				repeat: none
				body
				  rotate: false
				  xy: 0,0
				  size: 96,128
				  orig: 96,128
				  offset: 0,0
				  index: -1
				""";
			Files.writeString(atlas, root.relativize(outside.resolve("body.png")).toString().replace('\\', '/') + "\n" + atlasBody);
			denied = call(root, "openspine_render_preview", json("{\"path\":\"preview-source.json\",\"atlas\":\"preview-check.atlas\"}"));
			check(denied.getBoolean("isError") && denied.toString().contains("outside the configured MCP root"), "atlas page escape rejected by the actual worker");
			Files.writeString(atlas, "images/body.png\n" + atlasBody);
			JsonValue allowed = call(root, "openspine_render_preview", json("{\"path\":\"preview-source.json\",\"atlas\":\"preview-check.atlas\"}"));
			check(!allowed.getBoolean("isError"), "authorized atlas renders successfully");
		} finally {
			Files.deleteIfExists(atlas);
			Files.deleteIfExists(root.resolve("escape.json"));
			Files.deleteIfExists(outside.resolve("body.png"));
			Files.deleteIfExists(outside);
		}
	}

	private static void createFixture(Path root) throws Exception {
		Files.writeString(root.resolve("preview-source.json"), PROJECT);
		Files.createDirectories(root.resolve("images"));
		BufferedImage image = new BufferedImage(96, 128, BufferedImage.TYPE_INT_ARGB);
		var graphics = image.createGraphics();
		try {
			graphics.setColor(new Color(0xe24452));
			graphics.fillRect(0, 0, 96, 64);
			graphics.setColor(new Color(0x20bfa0));
			graphics.fillRect(0, 64, 96, 64);
			graphics.setColor(Color.WHITE);
			graphics.fillRect(12, 10, 16, 20);
		} finally { graphics.dispose(); }
		ImageIO.write(image, "png", root.resolve("images/body.png").toFile());
		SkeletonData data = new SkeletonData();
		data.setImagesPath("images/");
		BoneData bone = new BoneData(0, "root", null);
		bone.getSetupPose().setInherit(BoneData.Inherit.normal);
		bone.getSetupPose().setScaleX(1);
		bone.getSetupPose().setScaleY(1);
		bone.setVisible(true);
		data.getBones().add(bone);
		SlotData slot = new SlotData(0, "body", bone);
		slot.setBlendMode(com.esotericsoftware.spine.BlendMode.normal);
		slot.setAttachmentName("body");
		slot.setVisible(true);
		data.getSlots().add(slot);
		Skin skin = new Skin("default");
		RegionAttachment region = new RegionAttachment("body", new Sequence(1, false));
		region.setPath("body");
		region.setWidth(96);
		region.setHeight(128);
		skin.setAttachment(0, "body", region);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);
		SkeletonDocument document = new SkeletonDocument();
		document.data = data;
		document.skeleton = new Skeleton(data);
		Files.write(root.resolve("preview-source.skel"), SkeletonBinaryExporter.toBytes(document));
	}

	static JsonValue call(Path root, String name, JsonValue arguments) throws Exception {
		JsonValue params = object();
		put(params, "name", name);
		params.addChild("arguments", arguments);
		JsonValue request = object();
		put(request, "jsonrpc", "2.0");
		put(request, "id", 2);
		put(request, "method", "tools/call");
		request.addChild("params", params);
		String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"image-test\",\"version\":\"1\"}}}";
		String messages = initialize + "\n" + request.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json) + "\n";
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		new McpServer(root, "test", new ByteArrayInputStream(messages.getBytes(StandardCharsets.UTF_8)), output, System.err).run();
		for (String line : output.toString(StandardCharsets.UTF_8).lines().toList()) {
			JsonValue response = json(line);
			if (response.getInt("id") == 2) {
				if (response.has("error")) throw new AssertionError(response.toString());
				return response.get("result");
			}
		}
		throw new AssertionError("Missing tool response");
	}

	private static JsonValue json(String value) { return new JsonReader().parse(value); }
	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
	@FunctionalInterface private interface Action { void run() throws Exception; }
	private static void expectFailure(Action action, String message) throws Exception {
		try { action.run(); } catch (Exception expected) { return; }
		throw new AssertionError(message);
	}
	private McpVisualToolsTest() {}
}
