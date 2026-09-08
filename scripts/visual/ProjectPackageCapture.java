package com.openspine.io;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.openspine.EditorApp;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;
import com.openspine.ui.UiAppearance;
import com.openspine.view.EditorViewport;

/** Real-texture project ZIP round trip in a hidden editor with independent preferences. */
public final class ProjectPackageCapture {
	private static final String PAGE_NAME = "\u4eba\u7269/\u56fe\u96c6\u9875[1].png";
	private static final String REGION_NAME = "\u56fe\u5757";
	private static final class CaptureApp extends EditorApp {
		private final Path home, output;
		private final boolean checkReopen;
		private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
		private Throwable failure;
		private boolean exiting;
		private int frames;
		private Path manifest;

		CaptureApp(Path home, Path output, boolean checkReopen) {
			super(null, home);
			this.home = home;
			this.output = output;
			this.checkReopen = checkReopen;
		}

		@Override public void create() {
			try {
				super.create();
				invoke("setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				Files.createDirectories(output);
				Path first = fixture(home.resolve("source-a"), 0xffe52d3e, -85f);
				Path second = fixture(home.resolve("source-b"), 0xff74d739, 85f);
				try (SkeletonProject source = new SkeletonProject(new SkeletonDocument())) {
					source.replaceAll(List.of(load(first, "Red rig", 0.35f, 0.5f, false),
						load(second, "Green rig", 1.2f, 1.5f, true)));
					source.switchTo(1);
					Path zip = output.resolve("textured-project.zip");
					ProjectPackager.ProjectPackageResult result = ProjectPackager.write(source, true, zip);
					require(result.skeletonCount() == 2 && result.fileCount() == 10, "Unexpected package resource count: " + result);
					Path extracted = home.resolve("extracted");
					extract(zip, extracted);
					manifest = extracted.resolve(result.manifestEntry());
				}
				require(Boolean.TRUE.equals(invoke("loadFile", new Class<?>[] {String.class}, manifest.toString())),
					"Editor did not open the extracted manifest");
				verifyState(1, true, new float[] {0.35f, 1.2f}, new float[] {0.5f, 1.5f}, new boolean[] {false, true});
			} catch (Throwable error) { stop(error); }
		}

		private SkeletonProject.LoadedSkeleton load(Path source, String name, float time, float speed, boolean loop) {
			SkeletonDocument document = new SkeletonDocument();
			try {
				SkeletonLoader.loadRuntime(document, new FileHandle(source.toFile()), new FileHandle(source.getParent().toFile()), "sprite.atlas");
				document.animationState = document.newAnimationState();
				document.setAnimation("move");
				document.setLooping(loop);
				document.playbackSpeed = speed;
				document.scrubTo(time);
				document.setSelectedBone(document.skeleton.findBone("body"));
				return new SkeletonProject.LoadedSkeleton(name, document);
			} catch (Throwable error) { document.dispose(); throw error; }
		}

		private void verifyState(int active, boolean showAll, float[] times, float[] speeds, boolean[] loops) {
			SkeletonProject project = (SkeletonProject)field("skeletonProject");
			require(project.size() == 2, "Package lost a skeleton");
			require(project.activeIndex() == active, "Package did not restore the active skeleton");
			require((Boolean)field("showAllSkeletons") == showAll, "Package did not restore show-all state");
			require(project.nameAt(0).equals("Red rig") && project.nameAt(1).equals("Green rig"), "Package changed tab names");
			List<SkeletonDocument> documents = project.documents();
			for (int index = 0; index < documents.size(); index++) {
				SkeletonDocument document = documents.get(index);
				require("move".equals(document.getCurrentAnimationName()), "Animation selection was lost: " + index);
				require(Math.abs(document.getCurrentTime() - times[index]) < 0.001f, "Time was lost: " + index + "=" + document.getCurrentTime());
				require(Math.abs(document.playbackSpeed - speeds[index]) < 0.001f, "Playback speed was lost: " + index);
				require(document.looping == loops[index] && !document.playing, "Playback mode was lost: " + index);
				require(document.getSelectedBone() != null && document.getSelectedBone().getData().getName().equals("body"),
					"Bone selection was lost: " + index);
				require(document.atlas.findRegion(REGION_NAME).getRegionWidth() == 64, "Real atlas region was not loaded");
				require(Path.of(document.filePath).startsWith(manifest.getParent()), "Package used a source outside extraction");
			}
			require(documents.get(0).atlas.findRegion(REGION_NAME).getTexture()
				!= documents.get(1).atlas.findRegion(REGION_NAME).getTexture(), "Same-name textures collided");
		}

		private void reopenWithNewState() throws Exception {
			SkeletonProject project = (SkeletonProject)field("skeletonProject");
			invoke("switchProjectSkeleton", new Class<?>[] {int.class}, 0);
			Field showAll = EditorApp.class.getDeclaredField("showAllSkeletons");
			showAll.setAccessible(true);
			showAll.setBoolean(this, false);
			List<SkeletonDocument> documents = project.documents();
			for (int index = 0; index < documents.size(); index++) {
				SkeletonDocument document = documents.get(index);
				document.scrubTo(index == 0 ? 0.7f : 1.6f);
				document.playbackSpeed = index == 0 ? 2f : 0.75f;
				document.setLooping(index == 0);
			}
			for (SkeletonDocument document : documents)
				ProjectStateStore.write(Path.of(document.filePath), project, false);
			require(Boolean.TRUE.equals(invoke("loadFile", new Class<?>[] {String.class}, manifest.toString())),
				"Editor did not reopen the extracted manifest");
			verifyState(0, false, new float[] {0.7f, 1.6f}, new float[] {2f, 0.75f}, new boolean[] {true, false});
		}

		@Override public void render() {
			if (exiting) return;
			try {
				super.render();
				if (System.nanoTime() > deadline) throw new AssertionError("Hidden package capture timed out");
				if (++frames != 40) return;
				capture();
				if (checkReopen) reopenWithNewState();
				System.out.println("OK: project package, isolated same-name textures, editor load, restored state, real pixels"
					+ (checkReopen ? ", and updated sidecar reopen" : ""));
				stop(null);
			} catch (Throwable error) { stop(error); }
		}

		private void capture() throws Exception {
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			EditorViewport viewport = (EditorViewport)field("viewport");
			Vector2 origin = viewport.localToStageCoordinates(new Vector2());
			int red = 0, green = 0;
			for (int y = height - (int)(origin.y + viewport.getHeight()); y < height - (int)origin.y; y++) {
				for (int x = (int)origin.x; x < (int)(origin.x + viewport.getWidth()); x++) {
					int offset = (y * width + x) * 4;
					int r = pixels[offset] & 255, g = pixels[offset + 1] & 255, b = pixels[offset + 2] & 255;
					if (r > 170 && g < 100 && b < 130) red++;
					if (r > 65 && r < 155 && g > 160 && b < 110) green++;
				}
			}
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try {
				BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(output.resolve("project-package-1280.png").toFile()), pixmap);
			} finally { pixmap.dispose(); }
			require(red > 500 && green > 500, "Both textures must render in viewport: red=" + red + ", green=" + green);
			Files.writeString(output.resolve("render-check.txt"), "viewport_red_pixels=" + red + "\nviewport_green_pixels=" + green
				+ "\ninitial_active=1\ninitial_show_all=true\ninitial_times=0.35,1.2\n", StandardCharsets.UTF_8);
		}

		private Object field(String name) {
			try {
				Field field = EditorApp.class.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(this);
			} catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
		}

		private Object invoke(String name, Class<?>[] types, Object... values) {
			try {
				Method method = EditorApp.class.getDeclaredMethod(name, types);
				method.setAccessible(true);
				return method.invoke(this, values);
			} catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
		}

		private void stop(Throwable error) { failure = error; exiting = true; Gdx.app.exit(); }
	}

	private static Path fixture(Path directory, int color, float x) throws Exception {
		Files.createDirectories(directory);
		BufferedImage image = new BufferedImage(64, 96, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 96; y++) for (int px = 0; px < 64; px++) {
			boolean border = px < 3 || px > 60 || y < 3 || y > 92;
			boolean detail = px > 15 && px < 48 && (y > 20 && y < 28 || y > 65 && y < 73);
			image.setRGB(px, y, border ? 0xff202020 : detail ? 0xffffffff : color);
		}
		Path imageFile = directory.resolve(PAGE_NAME);
		Files.createDirectories(imageFile.getParent());
		ImageIO.write(image, "PNG", imageFile.toFile());
		Files.writeString(directory.resolve("sprite.atlas"), """
			%s
			size: 64,96
			format: RGBA8888
			filter: Linear,Linear
			repeat: none
			%s
			  rotate: false
			  xy: 0,0
			  size: 64,96
			  orig: 64,96
			  offset: 0,0
			  index: -1
			""".formatted(PAGE_NAME, REGION_NAME), StandardCharsets.UTF_8);
		Path json = directory.resolve("rig.json");
		Files.writeString(json, """
			{
			  "skeleton":{"spine":"4.3.0","images":"./"},
			  "bones":[{"name":"root","x":%s},{"name":"body","parent":"root","length":65}],
			  "slots":[{"name":"body","bone":"body","attachment":"sprite"}],
			  "skins":{"default":{"name":"default","attachments":{"body":{"sprite":{"path":"%s","width":64,"height":96}}}}},
			  "animations":{"move":{"bones":{"body":{"translate":[{"time":0,"x":0,"y":0},{"time":1,"x":30,"y":15},{"time":2,"x":0,"y":0}]}}}}
			}
			""".formatted(Float.toString(x), REGION_NAME), StandardCharsets.UTF_8);
		return json;
	}

	private static void extract(Path zip, Path directory) throws Exception {
		Files.createDirectories(directory);
		try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
			for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
				Path target = directory.resolve(entry.getName()).normalize();
				require(target.startsWith(directory), "ZIP entry escaped extraction root");
				Files.createDirectories(entry.isDirectory() ? target : target.getParent());
				if (!entry.isDirectory()) Files.copy(input, target);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Expected output directory and optional initial-only");
		Path home = Files.createTempDirectory("openspine-package-capture-").toRealPath();
		String previousHome = System.getProperty("user.home");
		String previousAutosave = System.getProperty(com.openspine.io.AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(com.openspine.io.AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		CaptureApp app = new CaptureApp(home, Path.of(args[0]).toAbsolutePath(), args.length == 1);
		try {
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setTitle("OpenSpine Hidden Project Package Verification");
			config.setInitialVisible(false);
			config.setWindowedMode(1280, 800);
			config.setResizable(false);
			config.setForegroundFPS(60);
			config.setIdleFPS(60);
			config.disableAudio(true);
			config.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
			new Lwjgl3Application(app, config);
		} finally {
			System.setProperty("user.home", previousHome);
			if (previousAutosave == null) System.clearProperty(com.openspine.io.AutoSaveStore.DIRECTORY_PROPERTY);
			else System.setProperty(com.openspine.io.AutoSaveStore.DIRECTORY_PROPERTY, previousAutosave);
			try (var paths = Files.walk(home)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		if (app.failure != null) throw new AssertionError("Hidden project package check failed", app.failure);
	}

	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
	private ProjectPackageCapture() {}
}
