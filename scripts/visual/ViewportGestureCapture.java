package com.openspine.view;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.EditorApp;
import com.openspine.io.AutoSaveStore;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.ui.SettingsDialog;
import com.openspine.ui.UiAppearance;

/** Runs input handlers in an isolated hidden editor, exports its framebuffer and exits. */
public final class ViewportGestureCapture {
	private static final Class<?>[] NONE = new Class<?>[0], POINT = {float.class, float.class};
	private static final class CaptureApp extends EditorApp {
		private final Path output;
		private Throwable failure;
		private int frames;
		private boolean stopped;
		private SkeletonDocument doc;
		private EditorViewport viewport;
		private EditHistory history;
		private MeshAttachment mesh;
		private Slot slot;
		CaptureApp(Path home, Path output) { super(null, home); this.output = output; }
		@Override public void create() {
			try {
				super.create();
				call(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				doc = (SkeletonDocument)read(EditorApp.class, this, "doc");
				viewport = (EditorViewport)read(EditorApp.class, this, "viewport");
				history = (EditHistory)read(EditorApp.class, this, "history");
				doc.setPlaying(false); doc.setAnimation("idle"); doc.scrubTo(0f);
				slot = doc.skeleton.findSlot("torso");
				Sequence sequence = new Sequence(1, false);
				sequence.getRegions()[0] = doc.atlas.findRegion("torso");
				mesh = new MeshAttachment("torso", sequence); mesh.setPath("torso");
				mesh.setVertices(new float[] {-60f, -30f, 60f, -30f, 60f, 90f, -60f, 90f});
				mesh.setWorldVerticesLength(8);
				mesh.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f});
				mesh.setTriangles(new short[] {0, 1, 2, 0, 2, 3}); mesh.setHullLength(8);
				mesh.setWidth(120f); mesh.setHeight(120f); mesh.updateSequence();
				doc.data.getDefaultSkin().setAttachment(slot.getData().getIndex(), "torso", mesh);
				for (Slot other : doc.skeleton.getSlots()) other.getData().setVisible(other == slot);
				doc.skeleton.setupPose(); doc.skeleton.updateWorldTransform(Physics.pose);
				doc.setSelectedBone(slot.getBone()); history.clear(); history.markSaved();
				viewport.setGridVisible(true); viewport.setViewportFlipped(false);
				call(EditorApp.class, this, "refreshSelection", NONE);
			} catch (Throwable error) { stop(error); }
		}
		@Override public void render() {
			if (stopped) return;
			try {
				super.render();
				if (++frames == 8) viewport.frameAll();
				if (frames == 12) {
					for (String action : new String[] {"flip", "constraints", "escape"})
						for (EditorTool tool : new EditorTool[] {EditorTool.MOVE, EditorTool.MESH, EditorTool.DEFORM, EditorTool.WEIGHT})
							verify(tool, action);
					EditorAppState.tool = EditorTool.MESH;
					viewport.setViewportFlipped(false); viewport.selectAttachment(slot.getData(), mesh);
				}
				if (frames == 16) { capture("normal"); viewport.setViewportFlipped(true); }
				if (frames == 20) {
					capture("flipped"); call(EditorApp.class, this, "showSettings", NONE);
					Object dialog = read(EditorApp.class, this, "settingsDialog");
					call(SettingsDialog.class, dialog, "selectPage", new Class<?>[] {SettingsDialog.Page.class}, SettingsDialog.Page.VIEWPORT);
				}
				if (frames == 24) {
					capture("settings");
					System.out.println("PASS: twelve bone/Mesh/Deform/weight cancellations; exact pose/geometry/deform/history restoration; three captures.");
					stop(null);
				}
			} catch (Throwable error) { stop(error); }
		}
		private void verify(EditorTool tool, String action) {
			EditorAppState.tool = tool;
			doc.scrubTo(0f);
			doc.setSelectedBone(tool == EditorTool.WEIGHT ? doc.skeleton.findBone("armL") : slot.getBone());
			SkeletonEditor.MeshGeometryState before = SkeletonEditor.meshGeometryState(mesh);
			float boneX = slot.getBone().getPose().getX(), boneY = slot.getBone().getPose().getY();
			Vector2 point = point(0);
			if (tool == EditorTool.WEIGHT) point.lerp(point(2), 0.03f);
			call(EditorViewport.class, viewport, "touchDown", new Class<?>[] {float.class, float.class, int.class},
				point.x, point.y, Input.Buttons.LEFT);
			String flag = tool == EditorTool.MOVE ? "dragging" : tool == EditorTool.MESH ? "draggingMeshVertex"
				: tool == EditorTool.DEFORM ? "draggingDeformVertex" : "paintingWeights";
			require((boolean)read(EditorViewport.class, viewport, flag), tool + " did not start");
			float[] pose = slot.getPose().getDeform().toArray(), applied = slot.getAppliedPose().getDeform().toArray();
			int timelines = doc.data.findAnimation("idle").getTimelines().size;
			call(EditorViewport.class, viewport, "touchDragged", POINT, point.x + 12f, point.y + 8f);
			if (tool == EditorTool.MOVE)
				require(slot.getBone().getPose().getX() != boneX || slot.getBone().getPose().getY() != boneY, "Bone did not move");
			else if (tool == EditorTool.DEFORM)
				require(!Arrays.equals(applied, slot.getAppliedPose().getDeform().toArray()), "Deform did not move");
			else require(!before.sameContent(SkeletonEditor.meshGeometryState(mesh)), tool + " preview did not change");
			switch (action) {
			case "flip" -> viewport.setViewportFlipped(!viewport.isViewportFlipped());
			case "constraints" -> viewport.setConstraintsEnabled(!viewport.areConstraintsEnabled());
			case "escape" -> require(viewport.getStage().keyDown(Input.Keys.ESCAPE), "Stage did not route Escape to the gesture");
			default -> throw new AssertionError(action);
			}
			for (String name : new String[] {"dragging", "draggingMeshVertex", "draggingDeformVertex", "paintingWeights"})
				require(!(boolean)read(EditorViewport.class, viewport, name), "Stale gesture: " + name);
			call(EditorViewport.class, viewport, "touchDragged", POINT, point.x + 80f, point.y + 40f);
			call(EditorViewport.class, viewport, "touchUp", NONE);
			require(before.sameContent(SkeletonEditor.meshGeometryState(mesh)), tool + " changed after " + action);
			require(slot.getBone().getPose().getX() == boneX && slot.getBone().getPose().getY() == boneY, "Original bone pose was not restored");
			if (tool == EditorTool.DEFORM) {
				require(Arrays.equals(pose, slot.getPose().getDeform().toArray()), "Original deform pose was not restored");
				require(Arrays.equals(applied, slot.getAppliedPose().getDeform().toArray()), "Original applied deform was not restored");
				require(timelines == doc.data.findAnimation("idle").getTimelines().size, "Cancellation inserted a Deform key");
			}
			require(!history.canUndo() && !history.canRedo() && !doc.dirty, "Cancellation created an edit");
		}
		private Vector2 point(int vertex) {
			float[] world = new float[8];
			mesh.computeWorldVertices(doc.skeleton, slot, 0, 8, world, 0, 2);
			OrthographicCamera camera = (OrthographicCamera)read(EditorViewport.class, viewport, "camera");
			float sign = viewport.isViewportFlipped() ? -1f : 1f;
			return new Vector2(viewport.getWidth() / 2f + sign * (world[vertex * 2] - camera.position.x) / camera.zoom,
				viewport.getHeight() / 2f + (world[vertex * 2 + 1] - camera.position.y) / camera.zoom);
		}
		private void capture(String name) {
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try {
				BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(output.resolve(width + "x" + height + "-" + name + ".png").toFile()), pixmap);
			} finally { pixmap.dispose(); }
		}
		private void stop(Throwable error) { if (stopped) return; failure = error; stopped = true; Gdx.app.exit(); }
	}
	public static void main(String[] args) throws Exception {
		Path output = Files.createDirectories(Path.of(args[2]).toAbsolutePath()).toRealPath();
		Path home = Files.createTempDirectory(output, ".gesture-home-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		CaptureApp app = new CaptureApp(home, output);
		try {
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setTitle("OpenSpine Hidden Viewport Verification"); config.setInitialVisible(false);
			config.setWindowedMode(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
			config.setResizable(false); config.disableAudio(true); config.setIdleFPS(60);
			config.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
			new Lwjgl3Application(app, config);
		} finally {
			System.setProperty("user.home", oldHome);
			if (oldAutosave == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY);
			else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, oldAutosave);
			require(home.getParent().equals(output), "Cleanup escaped its output directory");
			try (var paths = Files.walk(home)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		if (app.failure != null) throw new AssertionError("Hidden viewport verification failed", app.failure);
	}
	private static Object read(Class<?> type, Object target, String name) {
		try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static Object call(Class<?> type, Object target, String name, Class<?>[] types, Object... values) {
		try { Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, values); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
