package com.openspine.mcp;

import static com.openspine.mcp.McpImages.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.JsonValue;
import com.openspine.model.SkeletonDocument;

/** Opt-in, same-user file bridge. Only the owning editor can capture its GL framebuffer. */
public final class EditorCaptureBridge implements AutoCloseable {
	private final Path root;
	private final Path session;
	private final long pid = ProcessHandle.current().pid();
	private final String processStart = ProcessHandle.current().info().startInstant().orElseThrow().toString();
	private String publishedPath;
	private long nextPoll;
	private long nextCleanup;
	private boolean closed;

	public EditorCaptureBridge(Path root) throws IOException {
		this.root = root.toRealPath();
		if (!Files.isDirectory(this.root)) throw new IOException("MCP capture root is not a directory.");
		Path registry = registry(this.root);
		Files.createDirectories(registry);
		if (!registry.toRealPath().equals(registry)) throw new IOException("MCP capture registry must not be a symbolic link.");
		session = Files.createTempDirectory(registry, "editor-" + pid + "-").toRealPath();
		try { publish(null); }
		catch (IOException error) { close(); throw error; }
	}

	/** Called only after stage.draw(), without changing focus, selection, or the playback head. */
	public void poll(SkeletonDocument active, List<SkeletonDocument> documents) {
		if (closed || System.nanoTime() < nextPoll) return;
		nextPoll = System.nanoTime() + 100_000_000L;
		try {
			List<Path> paths = new ArrayList<>();
			for (SkeletonDocument document : documents) paths.add(projectPath(document));
			processRequests(projectPath(active), paths, (width, height) -> capture(active, width, height));
		} catch (IOException | RuntimeException error) {
			Gdx.app.error("OpenSpine MCP capture", "Screenshot bridge stopped: " + error.getMessage());
			try { close(); } catch (IOException ignored) { }
		}
	}

	@FunctionalInterface interface Capture {
		Result get(int maxWidth, int maxHeight) throws IOException;
	}

	void processRequests(Path active, List<Path> documents, Capture capture) throws IOException {
		if (closed) return;
		publish(active);
		long now = System.currentTimeMillis();
		List<Path> requests;
		try (var files = Files.list(session)) {
			requests = files.filter(path -> path.getFileName().toString().matches("[0-9a-f-]{36}\\.request\\.json"))
				.limit(32).toList();
		}
		for (Path requestFile : requests) {
			String id = requestFile.getFileName().toString().substring(0, 36);
			Path responseFile = session.resolve(id + ".response.json");
			Path pngFile = session.resolve(id + ".png");
			JsonValue result;
			try {
				JsonValue request = readJson(requestFile);
				long age = now - request.getLong("created_at");
				if (age < 0 || age > 15_000) throw new IOException("Screenshot request expired.");
				Path requested = Path.of(request.getString("path")).toRealPath();
				if (active == null || !requested.equals(active) || !requested.startsWith(root))
					throw new IOException("The editor's active project no longer matches the screenshot request.");
				for (Path path : documents) {
					if (path == null || !path.startsWith(root))
						throw new IOException("Screenshot blocked: an open document has no saved path inside the capture root.");
				}
				int width = McpVisualTools.integer(request, "max_width", 1280, 64, MAX_DIMENSION);
				int height = McpVisualTools.integer(request, "max_height", 960, 64, MAX_DIMENSION);
				Result image = capture.get(width, height);
				if (!Files.exists(requestFile)) continue;
				Files.write(pngFile, image.images().get(0), StandardOpenOption.CREATE_NEW);
				result = image.metadata();
				put(result, "kind", "editor_screenshot");
				put(result, "path", active.toString());
				put(result, "editor_pid", pid);
				put(result, "captured_at", Instant.now().toString());
			} catch (IOException | RuntimeException error) {
				result = object();
				put(result, "error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
			}
			if (Files.exists(requestFile)) writeJson(responseFile, result);
			else Files.deleteIfExists(pngFile);
			Files.deleteIfExists(requestFile);
		}
		if (now >= nextCleanup) {
			nextCleanup = now + 30_000;
			try (var files = Files.list(session)) {
				for (Path path : files.toList()) {
					try {
						if (!path.getFileName().toString().equals("session.json")
							&& now - Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis() > 60_000)
							Files.deleteIfExists(path);
					} catch (java.nio.file.NoSuchFileException alreadyConsumed) { }
				}
			}
		}
	}

	private Result capture(SkeletonDocument document, int maxWidth, int maxHeight) throws IOException {
		int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
		if (width <= 0 || height <= 0 || (long)width * height > 32L * 1024 * 1024)
			throw new IOException("Editor framebuffer is unavailable or too large to capture.");
		Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, width, height);
		try {
			byte[] png = encode(pixels, true, maxWidth, maxHeight);
			int[] size = fit(width, height, maxWidth, maxHeight);
			JsonValue metadata = object();
			put(metadata, "width", size[0]);
			put(metadata, "height", size[1]);
			put(metadata, "animation", document.getCurrentAnimationName());
			put(metadata, "time", document.getCurrentTime());
			metadata.addChild("unsaved_changes", new JsonValue(document.dirty));
			return new Result(metadata, List.of(png));
		} finally { pixels.dispose(); }
	}

	private void publish(Path active) throws IOException {
		String value = active == null ? "" : active.toString();
		if (value.equals(publishedPath)) return;
		JsonValue info = object();
		put(info, "root", root.toString());
		put(info, "pid", pid);
		put(info, "process_start", processStart);
		put(info, "path", value);
		writeJson(session.resolve("session.json"), info);
		publishedPath = value;
	}

	private static Path projectPath(SkeletonDocument document) {
		String value = document.savePath != null ? document.savePath : document.filePath;
		if (value == null || value.startsWith("builtin:") || value.startsWith("new:")) return null;
		try { return Path.of(value).toRealPath(); }
		catch (IOException | RuntimeException ignored) { return null; }
	}

	static Result request(Path root, Path project, long pid, int width, int height) throws IOException, InterruptedException {
		List<Path> matches = new ArrayList<>();
		Path registry = registry(root);
		if (Files.isDirectory(registry) && registry.toRealPath().equals(registry)) {
			try (var entries = Files.list(registry)) {
				for (Path session : entries.limit(256).toList()) {
					if (!Files.isDirectory(session, LinkOption.NOFOLLOW_LINKS)) continue;
					try {
						JsonValue info = readJson(session.resolve("session.json"));
						if (!root.toString().equals(info.getString("root")) || !project.toString().equals(info.getString("path"))) continue;
						long candidate = info.getLong("pid");
						if (pid != 0 && pid != candidate) continue;
						var handle = ProcessHandle.of(candidate);
						if (handle.isEmpty() || !handle.get().isAlive()
							|| !handle.get().info().startInstant().map(Instant::toString).orElse("").equals(info.getString("process_start"))) continue;
						matches.add(session);
					} catch (IOException | RuntimeException ignored) { }
				}
			}
		}
		if (matches.isEmpty()) throw new IOException("No capture-enabled editor has this project active. Start OpenSpine with --mcp-capture-root using the same directory as MCP --root, and open/select this project.");
		if (matches.size() > 1) throw new IOException("Multiple editors have this project active; pass editor_pid to choose one.");
		Path session = matches.get(0);
		String id = UUID.randomUUID().toString();
		Path requestFile = session.resolve(id + ".request.json"), responseFile = session.resolve(id + ".response.json");
		Path imageFile = session.resolve(id + ".png");
		try {
			JsonValue request = object();
			put(request, "path", project.toString());
			put(request, "max_width", width);
			put(request, "max_height", height);
			put(request, "created_at", System.currentTimeMillis());
			writeJson(requestFile, request);
			long deadline = System.nanoTime() + 10_000_000_000L;
			while (!Files.isRegularFile(responseFile)) {
				if (System.nanoTime() >= deadline) throw new IOException("Editor screenshot timed out. The editor must be rendering (not minimized or blocked).");
				Thread.sleep(40);
			}
			JsonValue metadata = readJson(responseFile);
			if (metadata.has("error")) throw new IOException(metadata.getString("error"));
			return new Result(metadata, List.of(readLimited(imageFile, MAX_PNG_BYTES)));
		} finally {
			Files.deleteIfExists(requestFile);
			Files.deleteIfExists(responseFile);
			Files.deleteIfExists(imageFile);
		}
	}

	private static Path registry(Path root) throws IOException {
		try {
			String identity = root.toString();
			if (System.getProperty("os.name", "").startsWith("Windows")) identity = identity.toLowerCase(java.util.Locale.ROOT);
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
			return Path.of(System.getProperty("java.io.tmpdir")).toRealPath().resolve("openspine-mcp-capture").resolve(hash);
		} catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}

	@Override public void close() throws IOException {
		closed = true;
		if (!Files.isDirectory(session, LinkOption.NOFOLLOW_LINKS)) return;
		try (var files = Files.list(session)) {
			for (Path path : files.toList()) Files.deleteIfExists(path);
		}
		Files.deleteIfExists(session);
	}
}
