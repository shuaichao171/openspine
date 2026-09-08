package com.openspine.mcp;

import static com.openspine.mcp.McpImages.*;

import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.JsonValue;
import com.openspine.io.PngFrameExporter;
import com.openspine.io.PngFrameExporter.Bounds;
import com.openspine.io.PreviewResourceGuard;
import com.openspine.io.SkeletonLoader;
import com.openspine.mcp.McpVisualTools.PreviewSettings;
import com.openspine.model.SkeletonDocument;

/** One-shot, hidden GL process. No editor preferences, audio, or live documents are touched. */
public final class McpRenderWorker {
	public static void main(String[] args) throws Exception {
		if (args.length != 1) throw new IllegalArgumentException("Expected a preview request directory.");
		Path directory = Path.of(args[0]).toRealPath();
		try {
			JsonValue request = readJson(directory.resolve("request.json"));
			Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
			configuration.setTitle("OpenSpine MCP Preview");
			configuration.setInitialVisible(false);
			configuration.setWindowedMode(64, 64);
			configuration.disableAudio(true);
			configuration.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0);
			new Lwjgl3Application(new ApplicationAdapter() {
				@Override public void create() {
					try { McpRenderWorker.render(directory, request); }
					catch (Throwable error) { writeError(directory, error); }
					finally { Gdx.app.exit(); }
				}
			}, configuration);
		} catch (Throwable error) {
			writeError(directory, error);
		}
	}

	private static void render(Path directory, JsonValue request) throws Exception {
		Path root = Path.of(request.getString("root")).toRealPath();
		PreviewSettings settings = PreviewSettings.parse(request.get("arguments"));
		SpineProjectTools projects = new SpineProjectTools(root);
		JsonValue summary = projects.inspectProject(settings.path());
		if (!summary.getString("sha256").equals(request.get("summary").getString("sha256")))
			throw new IllegalArgumentException("Project changed before rendering; request a new preview.");
		FileHandle file = new FileHandle(summary.getString("path"));
		SkeletonDocument document = new SkeletonDocument();
		try {
			SkeletonLoader.loadRuntime(document, file, file.parent(), settings.atlas(), new PreviewResourceGuard(root));
			document.animationState = document.newAnimationState();
			if (settings.skin() != null && !document.setSkin(settings.skin()))
				throw new IllegalArgumentException("Skin not found: " + settings.skin());
			document.setLooping(false);
			document.setPhysicsDeterministic(true);
			float end = settings.time();
			if (settings.animation() != null) {
				if (!document.setAnimation(settings.animation())) throw new IllegalArgumentException("Animation not found: " + settings.animation());
				float duration = document.data.findAnimation(settings.animation()).getDuration();
				if (settings.time() > duration || settings.endTime() != null && settings.endTime() > duration)
					throw new IllegalArgumentException("Preview time exceeds the animation duration of " + duration + " seconds.");
				if (settings.frames() > 1) end = settings.endTime() == null ? Math.min(duration, 60f) : settings.endTime();
			} else document.clearAnimation();
			float[] times = new float[settings.frames()];
			Bounds bounds = null;
			for (int index = 0; index < times.length; index++) {
				times[index] = times.length == 1 ? settings.time() : settings.time() + (end - settings.time()) * index / (times.length - 1);
				seek(document, times[index]);
				Bounds frame = PngFrameExporter.measure(document.skeleton);
				if (bounds == null) bounds = frame;
				else {
					float x = Math.min(bounds.x(), frame.x()), y = Math.min(bounds.y(), frame.y());
					bounds = new Bounds(x, y, Math.max(bounds.x() + bounds.width(), frame.x() + frame.width()) - x,
						Math.max(bounds.y() + bounds.height(), frame.y() + frame.height()) - y);
				}
			}
			if (bounds.width() <= 0 || bounds.height() <= 0) throw new IllegalArgumentException("No visible Region or Mesh attachments to preview.");
			float scale = Math.min((settings.width() - settings.padding() * 2) / bounds.width(),
				(settings.height() - settings.padding() * 2) / bounds.height());
			scale = Math.min(PngFrameExporter.MAX_SCALE, scale);
			if (scale < PngFrameExporter.MIN_SCALE) throw new IllegalArgumentException("Skeleton is too large for this preview size.");
			PngFrameExporter.Settings renderSettings = new PngFrameExporter.Settings(scale, settings.padding(), 6,
				settings.transparent(), false, settings.background());
			PngFrameExporter.OutputSize size = new PngFrameExporter.OutputSize(settings.width(), settings.height());
			JsonValue result = object();
			put(result, "kind", "render_preview");
			result.addChild("structure_valid", new JsonValue(true));
			result.addChild("textures_checked", new JsonValue(true));
			result.addChild("render_checked", new JsonValue(true));
			put(result, "render_scope", "requested_frames");
			put(result, "path", summary.getString("path"));
			put(result, "sha256", summary.getString("sha256"));
			put(result, "animation", settings.animation());
			put(result, "skin", document.getCurrentSkinName());
			put(result, "width", size.width());
			put(result, "height", size.height());
			put(result, "frame_count", times.length);
			JsonValue frames = new JsonValue(JsonValue.ValueType.array);
			int bytes = 0;
			for (int index = 0; index < times.length; index++) {
				seek(document, times[index]);
				Pixmap pixmap = PngFrameExporter.render(document.skeleton, bounds, size, renderSettings, document.premultipliedAlpha);
				try {
					if (settings.transparent()) PngFrameExporter.unpremultiply(pixmap);
					byte[] png = encode(pixmap, true, size.width(), size.height());
					bytes += png.length;
					if (bytes > MAX_PNG_BYTES) throw new IllegalArgumentException("Preview PNGs exceed the 16 MiB response budget.");
					Files.write(directory.resolve("frame-" + index + ".png"), png);
				} finally { pixmap.dispose(); }
				JsonValue frame = object();
				put(frame, "image_index", index);
				put(frame, "time", times[index]);
				frames.addChild(frame);
			}
			result.addChild("frames", frames);
			if (!projects.inspectProject(settings.path()).getString("sha256").equals(summary.getString("sha256")))
				throw new IllegalArgumentException("Project changed during rendering; request a new preview.");
			writeJson(directory.resolve("result.json"), result);
		} finally { document.dispose(); }
	}

	private static void seek(SkeletonDocument document, float time) {
		document.seek(time);
		document.completePendingPhysicsSeek();
	}

	private static void writeError(Path directory, Throwable error) {
		try {
			JsonValue result = object();
			put(result, "error", SpineProjectTools.message(error));
			writeJson(directory.resolve("result.json"), result);
		} catch (Exception failure) { failure.printStackTrace(System.err); }
	}

	private McpRenderWorker() {}
}
