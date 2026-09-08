package com.openspine.view;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.EditorApp;
import com.openspine.io.AutoSaveStore;
import com.openspine.io.SkeletonExporter;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.ui.PopupMenu;
import com.openspine.ui.SettingsDialog;
import com.openspine.ui.UiAppearance;

/** Hidden GL and process-local Scene2D verification with isolated preferences and autosaves. */
public final class PixelGridCapture {
	private static final Class<?>[] NONE = new Class<?>[0];
	private static final class CaptureApp extends EditorApp {
		private final Path output;
		private final boolean restoreOnly;
		private Throwable failure;
		private int frames;
		private boolean stopped;
		private SkeletonDocument doc;
		private EditorViewport viewport;
		private EditHistory history;
		private Texture pixelsTexture;
		private float centerX, centerY;
		private byte[] baseline;
		private String authorJson;
		CaptureApp(Path home, Path output, boolean restoreOnly) { super(null, home); this.output = output; this.restoreOnly = restoreOnly; }
		@Override public void create() {
			try {
				super.create();
				doc = (SkeletonDocument)read(EditorApp.class, this, "doc");
				viewport = (EditorViewport)read(EditorApp.class, this, "viewport");
				history = (EditHistory)read(EditorApp.class, this, "history");
				if (restoreOnly) { require(viewport.isPixelGridVisible(), "Project pixel grid preference was not restored"); return; }
				call(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				doc.setPlaying(false); doc.setAnimation("idle"); doc.scrubTo(0f);
				Pixmap pixels = new Pixmap(24, 16, Pixmap.Format.RGBA8888);
				try {
					for (int y = 0; y < 16; y++) for (int x = 0; x < 24; x++) {
						boolean eye = y > 4 && y < 8 && (x > 4 && x < 9 || x > 14 && x < 19);
						pixels.setColor(eye ? 0xF4B24CFF : ((x / 4 + y / 4) % 2 == 0 ? 0x26395FFF : 0x3C578AFF));
						pixels.drawPixel(x, y);
					}
					pixelsTexture = new Texture(pixels);
				} finally { pixels.dispose(); }
				pixelsTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
				Sequence sequence = new Sequence(1, false); sequence.getRegions()[0] = new TextureRegion(pixelsTexture);
				RegionAttachment region = new RegionAttachment("pixel-check", sequence);
				region.setWidth(24f); region.setHeight(16f); region.setScaleX(1f); region.setScaleY(1f);
				region.setPath("pixel-check"); region.updateSequence();
				Slot slot = doc.skeleton.findSlot("torso");
				doc.data.getDefaultSkin().setAttachment(slot.getData().getIndex(), "torso", region);
				for (Slot other : doc.skeleton.getSlots()) other.getData().setVisible(other == slot);
				doc.skeleton.setupPose(); doc.skeleton.updateWorldTransform(Physics.pose);
				centerX = slot.getBone().getAppliedPose().getWorldX() + 0.25f;
				centerY = slot.getBone().getAppliedPose().getWorldY() + 0.25f;
				doc.setSelectedBone(slot.getBone()); history.clear(); history.markSaved();
				viewport.setGridVisible(false); viewport.setBonesVisible(false); viewport.setSelectionHidden(true);
				viewport.setPixelGridVisible(false); viewport.setViewportFlipped(false);
				EditorAppState.tool = EditorTool.SELECT;
				call(EditorApp.class, this, "syncCommandState", NONE);
				authorJson = SkeletonExporter.toJson(doc);
			} catch (Throwable error) { stop(error); }
		}
		@Override public void render() {
			if (stopped) return;
			try {
				super.render(); ++frames;
				if (restoreOnly) { if (frames == 8) { capture("restored"); stop(null); } return; }
				if (frames == 8) setCamera(centerX, centerY, 0.05f);
				if (frames == 12) { baseline = viewportPixels(); capture("grid-off"); menuToggle(); }
				if (frames == 16) {
					require(viewport.isPixelGridVisible() && viewport.isPixelGridActive(), "Menu did not activate project pixel grid");
					verifyPixelRaster(baseline, viewportPixels()); capture("grid-on");
					shortcut(); require(!viewport.isPixelGridVisible(), "Shift+P did not disable pixel grid");
					viewport.setViewportFlipped(true);
				}
				if (frames == 20) { baseline = viewportPixels(); shortcut(); }
				if (frames == 24) {
					require(viewport.isPixelGridActive(), "Shift+P did not restore pixel grid");
					verifyPixelRaster(baseline, viewportPixels()); capture("grid-flipped");
					setCamera(centerX, centerY, 0.5f);
				}
				if (frames == 28) {
					require(viewport.isPixelGridVisible() && !viewport.isPixelGridActive(), "Zoomed-out pixel grid was not suppressed");
					baseline = viewportPixels(); capture("zoomed-out"); viewport.setPixelGridVisible(false);
				}
				if (frames == 32) {
					requireSameViewportContent(baseline, viewportPixels());
					viewport.setGridVisible(true); viewport.setPixelGridVisible(true); setCamera(10_000_000_000f, -10_000_000_000f, 1f);
				}
				if (frames == 36) {
					capture("large-coordinate-background"); setCamera(10_000_000_000f, -10_000_000_000f, 0.05f);
				}
				if (frames == 40) {
					require(viewport.isPixelGridActive(), "Representable large-coordinate pixel grid was lost");
					capture("large-coordinate-pixels"); viewport.setViewportFlipped(false); setCamera(centerX, centerY, 0.05f);
				}
				if (frames == 44) { capture("both-grids"); showSettings(); }
				if (frames == 48) {
					capture("settings"); ScrollPane scroll = settingsScroll();
					clickActor(toggleForLabel((Table)scroll.getActor(), "项目像素网格"));
					require(!viewport.isPixelGridVisible() && viewport.isGridVisible(), "Settings confused project grid and background grid");
					clickActor(toggleForLabel((Table)settingsScroll().getActor(), "项目像素网格"));
					require(viewport.isPixelGridVisible() && viewport.isGridVisible(), "Settings did not restore project grid");
					call(SettingsDialog.class, read(EditorApp.class, this, "settingsDialog"), "close", NONE);
					viewport.setBonesVisible(true); viewport.setShowInactiveSkinBones(true); viewport.setViewportFlipped(true);
					viewport.setZoomLocked(true); viewport.setPixelSnappingEnabled(true); viewport.setConstraintsEnabled(false);
					((SplitPane)read(EditorApp.class, this, "navigationSplit")).setSplitAmount(0.3f);
					((SplitPane)read(EditorApp.class, this, "inspectorSplit")).setSplitAmount(0.5f);
				}
				if (frames == 56) {
					verifyStatus(); capture("narrow-status");
					require(authorJson.equals(SkeletonExporter.toJson(doc)), "Pixel grid changed authored export data");
					require(!history.canUndo() && !history.canRedo() && !doc.dirty, "Pixel grid created an edit");
					require(pixelsTexture.getMinFilter() == Texture.TextureFilter.Nearest && pixelsTexture.getMagFilter() == Texture.TextureFilter.Nearest,
						"Pixel grid changed texture filtering");
					call(EditorApp.class, this, "saveWorkspacePreferences", NONE);
					System.out.println("PASS: exact framebuffer grid raster on image/flip; zoom suppression; large-coordinate rendering; menu/Shift+P/settings; independent background grid; wrapped status input pass-through; authored JSON/history/texture unchanged.");
					stop(null);
				}
			} catch (Throwable error) { stop(error); }
		}
		private void verifyPixelRaster(byte[] before, byte[] after) {
			int width = pixelWidth(), height = pixelHeight();
			boolean[] columns = new boolean[width], rows = new boolean[height];
			ViewportGrid.Plan plan = ViewportGrid.plan(camera().position.x, camera().position.y, camera().zoom,
				viewport.getWidth(), viewport.getHeight(), width, height, 1d, viewport.isViewportFlipped(), 8f);
			for (int index = 0; index < plan.x().count(); index++) { int x = (int)Math.floor(plan.x().pixelPosition(index)); if (x >= 0 && x < width) columns[x] = true; }
			for (int index = 0; index < plan.y().count(); index++) { int y = (int)Math.floor(plan.y().pixelPosition(index)); if (y >= 0 && y < height) rows[y] = true; }
			int changed = 0, unchanged = 0;
			// The center crop lies within the opaque image, away from UI and image borders.
			for (int y = height / 2 - 70; y < height / 2 + 70; y++)
				for (int x = width / 2 - 120; x < width / 2 + 120; x++) {
					int at = (y * width + x) * 4;
					boolean differs = before[at] != after[at] || before[at + 1] != after[at + 1] || before[at + 2] != after[at + 2];
					require(differs == (columns[x] || rows[y]), "Grid raster missed a project boundary or painted between lines at " + x + "," + y);
					require(after[at + 3] == before[at + 3], "Pixel overlay changed framebuffer alpha");
					if (differs) changed++; else unchanged++;
				}
			require(changed > 2000 && unchanged > 20000, "Grid is missing or covers too much of the image");
		}
		private void requireSameViewportContent(byte[] before, byte[] after) {
			int width = pixelWidth(), height = pixelHeight();
			for (int y = 0; y < height - 70; y++) for (int x = 0; x < width; x++) {
				int at = (y * width + x) * 4;
				require(before[at] == after[at] && before[at + 1] == after[at + 1] && before[at + 2] == after[at + 2],
					"Zoomed-out pixel grid changed the rendered viewport");
			}
		}
		private void verifyStatus() {
			Table status = (Table)read(EditorApp.class, this, "viewportStatus");
			Label label = (Label)read(EditorApp.class, this, "viewportStatusLabel");
			require(status.isVisible() && label.getHeight() > 28f, "Narrow viewport status did not wrap");
			Vector2 point = status.localToStageCoordinates(new Vector2(status.getWidth() / 2f, status.getHeight() / 2f));
			require(viewport.getStage().hit(point.x, point.y, true) == viewport, "Wrapped status intercepts viewport input");
			point = viewport.getStage().stageToScreenCoordinates(point);
			viewport.getStage().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.MIDDLE);
			require((boolean)read(EditorViewport.class, viewport, "draggingView"), "Wrapped status blocks middle-button pan");
			viewport.getStage().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.MIDDLE);
		}
		private void shortcut() {
			Input previous = Gdx.input;
			try {
				Gdx.input = (Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class}, (proxy, method, values) -> {
					if (method.getName().equals("isKeyPressed")) return (int)values[0] == Input.Keys.SHIFT_LEFT;
					return method.invoke(previous, values);
				});
				viewport.getStage().setKeyboardFocus(viewport);
				require(viewport.getStage().keyDown(Input.Keys.P), "Shift+P was not routed through Stage");
			} finally { Gdx.input = previous; }
		}
		private void menuToggle() {
			call(EditorApp.class, this, "showInterfaceMenu", NONE);
			PopupMenu menu = (PopupMenu)read(EditorApp.class, this, "interfaceMenu"); menu.validate();
			for (Actor child : menu.getChildren()) if (child instanceof PopupMenu.MenuItem item && item.getLabel().equals("项目像素网格")) { clickActor(item); return; }
			throw new AssertionError("Project pixel grid menu item missing");
		}
		private void clickActor(Actor actor) {
			Vector2 point = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f));
			point = viewport.getStage().stageToScreenCoordinates(point);
			viewport.getStage().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
			viewport.getStage().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
		}
		private void showSettings() {
			call(EditorApp.class, this, "showSettings", NONE);
			call(SettingsDialog.class, read(EditorApp.class, this, "settingsDialog"), "selectPage", new Class<?>[] {SettingsDialog.Page.class}, SettingsDialog.Page.VIEWPORT);
		}
		private ScrollPane settingsScroll() {
			Container<?> content = (Container<?>)read(SettingsDialog.class, read(EditorApp.class, this, "settingsDialog"), "content");
			content.validate(); ScrollPane scroll = (ScrollPane)content.getActor(); scroll.validate(); return scroll;
		}
		private TextButton toggleForLabel(Table table, String label) {
			for (int index = 0; index + 1 < table.getChildren().size; index++)
				if (table.getChildren().get(index) instanceof Label text && text.getText().toString().equals(label)
					&& table.getChildren().get(index + 1) instanceof TextButton button) return button;
			throw new AssertionError("Settings toggle missing: " + label);
		}
		private OrthographicCamera camera() { return (OrthographicCamera)read(EditorViewport.class, viewport, "camera"); }
		private void setCamera(float x, float y, float zoom) { camera().position.set(x, y, 0f); camera().zoom = zoom; }
		private int pixelWidth() { return Math.round(viewport.getWidth() * (float)read(EditorViewport.class, viewport, "screenPixelScaleX")); }
		private int pixelHeight() { return Math.round(viewport.getHeight() * (float)read(EditorViewport.class, viewport, "screenPixelScaleY")); }
		private byte[] viewportPixels() {
			return ScreenUtils.getFrameBufferPixels(Math.round((float)read(EditorViewport.class, viewport, "screenPixelOriginX")),
				Math.round((float)read(EditorViewport.class, viewport, "screenPixelOriginY")), pixelWidth(), pixelHeight(), false);
		}
		private void capture(String name) {
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try { BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(output.resolve(width + "x" + height + "-" + name + ".png").toFile()), pixmap);
			} finally { pixmap.dispose(); }
		}
		@Override public void dispose() { try { super.dispose(); } finally { if (pixelsTexture != null) pixelsTexture.dispose(); } }
		private void stop(Throwable error) { if (stopped) return; failure = error; stopped = true; Gdx.app.exit(); }
	}
	public static void main(String[] args) throws Exception {
		Path output = Files.createDirectories(Path.of(args[2]).toAbsolutePath()).toRealPath();
		Path home = Files.createTempDirectory(output, ".pixel-grid-home-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		try {
			for (boolean restoreOnly : new boolean[] {false, true}) {
				CaptureApp app = new CaptureApp(home, output, restoreOnly);
				Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
				config.setTitle("OpenSpine Hidden Pixel Grid Verification"); config.setInitialVisible(false);
				config.setWindowedMode(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
				config.setResizable(false); config.disableAudio(true); config.setIdleFPS(60);
				config.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
				new Lwjgl3Application(app, config);
				if (app.failure != null) throw new AssertionError("Hidden pixel grid verification failed", app.failure);
			}
			System.out.println("PASS: project pixel grid preference restored in a second isolated editor.");
		} finally {
			System.setProperty("user.home", oldHome);
			if (oldAutosave == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY); else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, oldAutosave);
			require(home.getParent().equals(output), "Cleanup escaped its output directory");
			try (var paths = Files.walk(home)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
		}
	}
	private static Object read(Class<?> type, Object target, String name) {
		try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static Object call(Class<?> type, Object target, String name, Class<?>[] types, Object... values) {
		try { Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, values); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
