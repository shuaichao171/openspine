package com.openspine.mcp;

import static com.openspine.mcp.McpImages.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.badlogic.gdx.utils.JsonValue;
import com.openspine.io.SkeletonLoader;
import com.openspine.mcp.SpineProjectTools.ToolFailure;

/** Image operations keep GL and editor state out of the stdio server process. */
final class McpVisualTools {
	private final SpineProjectTools projects;

	McpVisualTools(SpineProjectTools projects) { this.projects = projects; }

	Result preview(JsonValue arguments) throws IOException, ToolFailure, InterruptedException {
		PreviewSettings.parse(arguments);
		JsonValue summary = projects.inspectProject(text(arguments, "path", null));
		Path directory = Files.createTempDirectory("openspine-mcp-preview-").toRealPath();
		Process process = null;
		try {
			JsonValue request = object();
			put(request, "root", projects.root().toString());
			request.addChild("arguments", new com.badlogic.gdx.utils.JsonReader().parse(arguments.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json)));
			JsonValue fingerprint = object();
			put(fingerprint, "sha256", summary.getString("sha256"));
			request.addChild("summary", fingerprint);
			writeJson(directory.resolve("request.json"), request);
			List<String> command = workerCommand();
			command.add(directory.toString());
			process = new ProcessBuilder(command).redirectErrorStream(true)
				.redirectOutput(directory.resolve("worker.log").toFile()).start();
			if (!process.waitFor(45, TimeUnit.SECONDS)) throw new IOException("Preview rendering timed out after 45 seconds.");
			Path response = directory.resolve("result.json");
			if (!Files.isRegularFile(response))
				throw new IOException("Preview renderer could not start or exited unexpectedly (code " + process.exitValue()
					+ "). A working OpenGL driver is required.");
			JsonValue metadata = readJson(response);
			if (metadata.has("error")) throw new IOException(metadata.getString("error"));
			if (process.exitValue() != 0) throw new IOException("Preview renderer failed (code " + process.exitValue() + ").");
			int count = metadata.getInt("frame_count");
			if (count < 1 || count > 8) throw new IOException("Invalid preview frame count.");
			List<byte[]> images = new ArrayList<>();
			int bytes = 0;
			for (int index = 0; index < count; index++) {
				byte[] png = readLimited(directory.resolve("frame-" + index + ".png"), MAX_PNG_BYTES - bytes);
				bytes += png.length;
				images.add(png);
			}
			return new Result(metadata, List.copyOf(images));
		} finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
				process.onExit().join();
			}
			try (var files = Files.list(directory)) {
				for (Path file : files.toList()) Files.deleteIfExists(file);
			}
			Files.deleteIfExists(directory);
		}
	}

	Result screenshot(JsonValue arguments) throws IOException, InterruptedException {
		String requested = text(arguments, "path", null);
		if (requested == null || !SkeletonLoader.isSupportedFileName(requested))
			throw new IllegalArgumentException("path must name the active .json, .skel or .spine project.");
		Path path = projects.root().resolve(requested).normalize().toRealPath();
		if (!path.startsWith(projects.root()) || !Files.isRegularFile(path))
			throw new IOException("Screenshot project is outside the configured MCP root or is not a file.");
		int width = integer(arguments, "max_width", 1280, 64, MAX_DIMENSION);
		int height = integer(arguments, "max_height", 960, 64, MAX_DIMENSION);
		int pid = integer(arguments, "editor_pid", 0, 1, Integer.MAX_VALUE);
		return EditorCaptureBridge.request(projects.root(), path, pid, width, height);
	}

	static List<String> workerCommand() throws IOException {
		String appPath = System.getProperty("jpackage.app-path");
		if (appPath != null && !appPath.isBlank()) {
			Path launcher = Path.of(appPath).resolveSibling("OpenSpinePreview.exe");
			if (!Files.isRegularFile(launcher)) throw new IOException("OpenSpinePreview.exe is missing; update the complete application package.");
			return new ArrayList<>(List.of(launcher.toString()));
		}
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
		if (!Files.isRegularFile(java)) throw new IOException("The preview Java runtime was not found.");
		return new ArrayList<>(List.of(java.toString(), "-Xmx512m", "--add-opens=java.base/java.lang=ALL-UNNAMED",
			"-cp", System.getProperty("java.class.path"), McpRenderWorker.class.getName()));
	}

	record PreviewSettings(String path, String animation, String skin, String atlas, float time, Float endTime,
		int frames, int width, int height, int padding, boolean transparent, int background) {
		static PreviewSettings parse(JsonValue args) {
			String path = text(args, "path", null);
			if (path == null) throw new IllegalArgumentException("path is required.");
			String animation = text(args, "animation", null), skin = text(args, "skin", null);
			String atlas = text(args, "atlas", null);
			if (atlas != null && (!atlas.toLowerCase(Locale.ROOT).endsWith(".atlas")
				|| atlas.contains("/") || atlas.contains("\\") || atlas.contains(":")))
				throw new IllegalArgumentException("atlas must be the name of a sibling .atlas file.");
			float time = number(args, "time", 0);
			Float end = args.has("end_time") ? number(args, "end_time", 0) : null;
			int frames = integer(args, "frame_count", 1, 1, 8);
			int width = integer(args, "width", 512, 64, MAX_DIMENSION);
			int height = integer(args, "height", 512, 64, MAX_DIMENSION);
			int padding = integer(args, "padding", 24, 0, 256);
			if ((long)width * height * frames > MAX_PIXELS) throw new IllegalArgumentException("Preview exceeds the total 4-megapixel budget; reduce dimensions or frame_count.");
			if (padding * 2 >= Math.min(width, height)) throw new IllegalArgumentException("padding leaves no room for the skeleton.");
			if (animation == null && (time != 0 || end != null || frames != 1))
				throw new IllegalArgumentException("animation is required for timed or multi-frame previews.");
			if (end != null && (end < time || frames == 1))
				throw new IllegalArgumentException("end_time must be >= time and requires frame_count > 1.");
			JsonValue transparent = args.get("transparent");
			if (transparent != null && !transparent.isBoolean()) throw new IllegalArgumentException("transparent must be a boolean.");
			String background = text(args, "background", "#24262b");
			if (!background.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("background must be #RRGGBB.");
			return new PreviewSettings(path, animation, skin, atlas, time, end, frames, width, height, padding,
				transparent != null && transparent.asBoolean(), (int)(Long.parseLong(background.substring(1), 16) << 8) | 255);
		}
	}

	static String text(JsonValue args, String name, String fallback) {
		JsonValue value = args.get(name);
		if (value == null) return fallback;
		if (!value.isString() || value.asString().isBlank() || value.asString().length() > 4096)
			throw new IllegalArgumentException(name + " must be a nonempty string of at most 4096 characters.");
		return value.asString();
	}

	static int integer(JsonValue args, String name, int fallback, int min, int max) {
		JsonValue value = args.get(name);
		if (value == null) return fallback;
		if (!value.isNumber()) throw new IllegalArgumentException(name + " must be an integer.");
		double number = value.asDouble();
		if (!Double.isFinite(number) || number != Math.rint(number) || number < min || number > max)
			throw new IllegalArgumentException(name + " must be an integer between " + min + " and " + max + ".");
		return (int)number;
	}

	private static float number(JsonValue args, String name, float fallback) {
		JsonValue value = args.get(name);
		if (value == null) return fallback;
		if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0 || value.asDouble() > 60)
			throw new IllegalArgumentException(name + " must be a finite time in seconds between 0 and 60.");
		return value.asFloat();
	}
}
