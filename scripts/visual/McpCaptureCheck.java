package com.openspine.mcp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.openspine.EditorApp;
import com.openspine.model.SkeletonDocument;

/** Captures only a new isolated test editor, without showing or operating the user's windows. */
public final class McpCaptureCheck {
	public static void main(String[] args) throws Exception {
		if (args.length != 1) throw new IllegalArgumentException("Expected the MCP preview fixture directory.");
		Path root = Path.of(args[0]).toRealPath();
		Path home = Files.createTempDirectory("openspine-capture-check-").toRealPath();
		System.setProperty("user.home", home.toString());
		Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
		configuration.setTitle("OpenSpine MCP Screenshot Verification");
		configuration.setInitialVisible(false);
		configuration.setWindowedMode(1280, 800);
		configuration.setForegroundFPS(60);
		configuration.setIdleFPS(60);
		configuration.disableAudio(true);
		configuration.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
		Throwable[] failure = {null};
		new Lwjgl3Application(new EditorApp(root.resolve("preview-source.json").toString(), root) {
			private int frames;
			private CompletableFuture<JsonValue> request;
			private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);

			@Override public void create() {
				super.create();
				try {
					Field field = EditorApp.class.getDeclaredField("doc");
					field.setAccessible(true);
					SkeletonDocument document = (SkeletonDocument)field.get(this);
					document.setAnimation("move");
					document.scrubTo(0.4f);
					document.dirty = true;
				} catch (Exception error) { failure[0] = error; Gdx.app.exit(); }
			}

			@Override public void render() {
				super.render();
				if (++frames == 30) request = CompletableFuture.supplyAsync(() -> {
					try {
						return McpVisualToolsTest.call(root, "openspine_capture_screenshot", new JsonReader().parse(
							"{\"path\":\"preview-source.json\",\"max_width\":1280,\"max_height\":960}"));
					} catch (Exception error) { throw new RuntimeException(error); }
				});
				if (System.nanoTime() > deadline) {
					failure[0] = new AssertionError("Screenshot integration timed out");
					Gdx.app.exit();
					return;
				}
				if (request == null || !request.isDone()) return;
				try {
					JsonValue result = request.join();
					if (result.getBoolean("isError")) throw new AssertionError(result.toString());
					JsonValue metadata = result.get("structuredContent");
					if (!metadata.getBoolean("unsaved_changes") || Math.abs(metadata.getFloat("time") - 0.4f) > 0.001f)
						throw new AssertionError("Screenshot did not retain the live editor pose and dirty state");
					byte[] png = Base64.getDecoder().decode(result.get("content").get(1).getString("data"));
					BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
					if (image.getWidth() != 1280 || image.getHeight() != 800) throw new AssertionError("Unexpected screenshot dimensions");
					Files.write(root.resolve("editor-screenshot.png"), png);
					Files.writeString(root.resolve("editor-screenshot-metadata.json"), metadata.prettyPrint(com.badlogic.gdx.utils.JsonWriter.OutputType.json, 100));
				} catch (Throwable error) { failure[0] = error; }
				finally { Gdx.app.exit(); }
			}
		}, configuration);
		try (var paths = Files.walk(home)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
		if (failure[0] != null) throw new AssertionError("Editor screenshot check failed", failure[0]);
		System.out.println("OK: actual editor screenshot, live state, and cleanup verified");
	}
	private McpCaptureCheck() {}
}
