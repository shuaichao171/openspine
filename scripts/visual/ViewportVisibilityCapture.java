package com.openspine.view;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.EditorApp;
import com.openspine.io.AutoSaveStore;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.ui.PopupMenu;
import com.openspine.ui.SettingsDialog;
import com.openspine.ui.UiAppearance;

/** Isolated hidden GL verification. All input goes through this process's Scene2D handlers. */
public final class ViewportVisibilityCapture {
	private static final Class<?>[] NONE = new Class<?>[0], POINT = {float.class, float.class};
	private static final class CaptureApp extends EditorApp {
		private final Path output;
		private final boolean restoreOnly;
		private Throwable failure;
		private int frames;
		private boolean stopped;
		private SkeletonDocument doc;
		private EditorViewport viewport;
		private EditHistory history;
		private MeshAttachment mesh;
		private Slot slot;
		private Bone inactive;
		CaptureApp(Path home, Path output, boolean restoreOnly) { super(null, home); this.output = output; this.restoreOnly = restoreOnly; }
		@Override public void create() {
			try {
				super.create();
				doc = (SkeletonDocument)read(EditorApp.class, this, "doc");
				viewport = (EditorViewport)read(EditorApp.class, this, "viewport");
				history = (EditHistory)read(EditorApp.class, this, "history");
				if (restoreOnly) {
					require(viewport.isSelectionHidden() && viewport.isShowInactiveSkinBones(), "Saved visibility preferences were not restored");
					return;
				}
				call(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				BoneData reference = new BoneData(doc.data.getBones().size, "inactive-reference", doc.data.getBones().first());
				reference.setVisible(true); reference.setSkinRequired(true); reference.setLength(95f);
				reference.getSetupPose().setPosition(-195f, 165f); reference.getSetupPose().setScale(1f, 1f);
				reference.getSetupPose().setRotation(-15f); reference.getSetupPose().setInherit(BoneData.Inherit.normal);
				doc.data.getBones().add(reference);
				BoneData referenceChild = new BoneData(doc.data.getBones().size, "inactive-child", reference);
				referenceChild.setVisible(true); referenceChild.setSkinRequired(true); referenceChild.setLength(55f);
				referenceChild.getSetupPose().setPosition(95f, 0f); referenceChild.getSetupPose().setScale(1f, 1f);
				referenceChild.getSetupPose().setRotation(40f); referenceChild.getSetupPose().setInherit(BoneData.Inherit.normal);
				doc.data.getBones().add(referenceChild);
				doc.skeleton = new Skeleton(doc.data);
				doc.setPlaying(false); doc.setAnimation("idle"); doc.scrubTo(0f);
				slot = doc.skeleton.findSlot("torso");
				Sequence sequence = new Sequence(1, false); sequence.getRegions()[0] = doc.atlas.findRegion("torso");
				mesh = new MeshAttachment("torso", sequence); mesh.setPath("torso");
				mesh.setVertices(new float[] {-60f, -30f, 60f, -30f, 60f, 90f, -60f, 90f});
				mesh.setWorldVerticesLength(8); mesh.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f});
				mesh.setTriangles(new short[] {0, 1, 2, 0, 2, 3}); mesh.setHullLength(8);
				mesh.setWidth(120f); mesh.setHeight(120f); mesh.updateSequence();
				doc.data.getDefaultSkin().setAttachment(slot.getData().getIndex(), "torso", mesh);
				for (Slot other : doc.skeleton.getSlots()) other.getData().setVisible(other == slot);
				doc.skeleton.setupPose(); doc.skeleton.updateWorldTransform(Physics.pose);
				inactive = doc.skeleton.findBone("inactive-reference");
				inactive.getAppliedPose().setWorldX(-1200f); inactive.getAppliedPose().setWorldY(1100f);
				doc.setSelectedBone(slot.getBone()); history.clear(); history.markSaved();
				viewport.setGridVisible(true); viewport.setViewportFlipped(false); viewport.setShowInactiveSkinBones(true);
				call(EditorApp.class, this, "refreshSelection", NONE);
			} catch (Throwable error) { stop(error); }
		}
		@Override public void render() {
			if (stopped) return;
			try {
				super.render(); ++frames;
				if (restoreOnly) { if (frames == 8) { capture("restored"); stop(null); } return; }
				if (frames == 8) viewport.frameAll();
				if (frames == 12) {
					verifyReferences();
					for (EditorTool tool : new EditorTool[] {EditorTool.MOVE, EditorTool.MESH, EditorTool.DEFORM, EditorTool.WEIGHT}) verifyCancellation(tool);
					verifyHiddenPicking();
					viewport.setSelectionHidden(false); viewport.setShowInactiveSkinBones(false);
					EditorAppState.tool = EditorTool.MOVE; doc.setSelectedBone(slot.getBone()); viewport.selectAttachment(slot.getData(), mesh);
					call(EditorApp.class, this, "syncCommandState", NONE);
				}
				if (frames == 16) { capture("selection-visible"); menuToggle("隐藏视区选择"); }
				if (frames == 20) {
					require(viewport.isSelectionHidden(), "Menu did not hide selection"); capture("selection-hidden");
					shortcut(Input.Keys.H); require(!viewport.isSelectionHidden(), "Shift+H did not show selection");
					shortcut(Input.Keys.H); require(viewport.isSelectionHidden(), "Shift+H did not hide selection");
					menuToggle("非活动皮肤骨骼");
					require(viewport.isShowInactiveSkinBones(), "Menu did not enable inactive bones");
					shortcut(Input.Keys.J); require(!viewport.isShowInactiveSkinBones(), "Shift+J did not disable inactive bones");
					shortcut(Input.Keys.J); require(viewport.isShowInactiveSkinBones(), "Shift+J did not enable inactive bones");
				}
				if (frames == 24) {
					capture("inactive-reference"); verifyStatusPassThrough();
					EditorAppState.tool = EditorTool.MESH;
					Vector2 vertex = meshPoint(0); call(EditorViewport.class, viewport, "touchDown", new Class<?>[] {float.class, float.class, int.class}, vertex.x, vertex.y, Input.Buttons.LEFT);
					call(EditorViewport.class, viewport, "touchUp", NONE);
					call(EditorApp.class, this, "syncCommandState", NONE);
				}
				if (frames == 28) { capture("hidden-mesh-tool"); showViewportSettings(); }
				if (frames == 32) {
					capture("settings-top");
					ScrollPane scroll = settingsScroll(); require(scroll.getMaxY() > 0f, "Viewport settings are not scrollable");
					clickActor(toggleForLabel((Table)scroll.getActor(), "隐藏视区选择"));
					require(!viewport.isSelectionHidden(), "Settings did not toggle selection visibility");
					clickActor(toggleForLabel((Table)settingsScroll().getActor(), "隐藏视区选择"));
					clickActor(toggleForLabel((Table)settingsScroll().getActor(), "非活动皮肤骨骼"));
					require(!viewport.isShowInactiveSkinBones(), "Settings did not toggle inactive bones");
					clickActor(toggleForLabel((Table)settingsScroll().getActor(), "非活动皮肤骨骼"));
					scroll = settingsScroll(); scroll.setScrollY(scroll.getMaxY()); scroll.updateVisualScroll();
				}
				if (frames == 36) {
					capture("settings-bottom");
					float scrollY = settingsScroll().getScrollY();
					clickActor(toggleForLabel((Table)settingsScroll().getActor(), "禁用全部约束"));
					require(!viewport.areConstraintsEnabled(), "Scrolled constraint control is unreachable");
					require(Math.abs(settingsScroll().getScrollY() - scrollY) < 1f, "Toggling settings lost scroll position");
					call(SettingsDialog.class, read(EditorApp.class, this, "settingsDialog"), "close", NONE);
					viewport.setViewportFlipped(true); viewport.setZoomLocked(true); viewport.setPixelSnappingEnabled(true);
				}
				if (frames == 40) {
					capture("special-status"); verifyStatusPassThrough();
					call(EditorApp.class, this, "saveWorkspacePreferences", NONE);
					System.out.println("PASS: 4 gesture cancellations; hidden gizmo/blank drag; inactive reference/non-picking; menu/Shift shortcuts/settings; scroll retention; status pass-through.");
					stop(null);
				}
			} catch (Throwable error) { stop(error); }
		}
		private void verifyReferences() {
			float staleX = inactive.getAppliedPose().getWorldX(), staleY = inactive.getAppliedPose().getWorldY();
			Skeleton reference = new InactiveSkinBones().prepare(doc.skeleton);
			Bone displayed = EditorViewport.displayedBone(inactive, reference);
			Vector2 point = localPoint(displayed.getAppliedPose().getWorldX(), displayed.getAppliedPose().getWorldY());
			require(call(EditorViewport.class, viewport, "pickBone", POINT, point.x, point.y) != inactive, "Inactive reference became pickable");
			doc.setSelectedBone(inactive); viewport.frameSelected();
			require(Math.abs(camera().position.x - displayed.getAppliedPose().getWorldX()) < 0.001f, "Framing uses inactive stale world pose");
			require(inactive.getAppliedPose().getWorldX() == staleX && inactive.getAppliedPose().getWorldY() == staleY && !inactive.isActive(), "Reference changed real inactive bone");
			doc.setSelectedBone(slot.getBone()); viewport.frameAll();
		}
		private void verifyCancellation(EditorTool tool) {
			EditorAppState.tool = tool; viewport.setSelectionHidden(false); doc.scrubTo(0f);
			doc.setSelectedBone(tool == EditorTool.WEIGHT ? doc.skeleton.findBone("armL") : slot.getBone());
			SkeletonEditor.MeshGeometryState before = SkeletonEditor.meshGeometryState(mesh);
			float x = slot.getBone().getPose().getX(), y = slot.getBone().getPose().getY();
			Vector2 point = meshPoint(0); if (tool == EditorTool.WEIGHT) point.lerp(meshPoint(2), 0.03f);
			call(EditorViewport.class, viewport, "touchDown", new Class<?>[] {float.class, float.class, int.class}, point.x, point.y, Input.Buttons.LEFT);
			String flag = tool == EditorTool.MOVE ? "dragging" : tool == EditorTool.MESH ? "draggingMeshVertex" : tool == EditorTool.DEFORM ? "draggingDeformVertex" : "paintingWeights";
			require((boolean)read(EditorViewport.class, viewport, flag), tool + " did not start");
			float[] pose = slot.getPose().getDeform().toArray(), applied = slot.getAppliedPose().getDeform().toArray();
			Bone selected = doc.getSelectedBone();
			call(EditorViewport.class, viewport, "touchDragged", POINT, point.x + 12f, point.y + 8f);
			viewport.setSelectionHidden(true);
			require(doc.getSelectedBone() == selected, "Hiding selection cleared the selected bone");
			require(!(boolean)read(EditorViewport.class, viewport, flag), "Hiding selection retained " + flag);
			call(EditorViewport.class, viewport, "touchDragged", POINT, point.x + 80f, point.y + 40f);
			call(EditorViewport.class, viewport, "touchUp", NONE);
			require(before.sameContent(SkeletonEditor.meshGeometryState(mesh)), "Cancellation changed mesh geometry or weights");
			require(slot.getBone().getPose().getX() == x && slot.getBone().getPose().getY() == y, "Cancellation changed original bone pose");
			if (tool == EditorTool.DEFORM) {
				require(Arrays.equals(pose, slot.getPose().getDeform().toArray()), "Deform pose was not restored");
				require(Arrays.equals(applied, slot.getAppliedPose().getDeform().toArray()), "Applied deform was not restored");
			}
			require(!history.canUndo() && !history.canRedo() && !doc.dirty, "Cancellation created an edit");
		}
		private void verifyHiddenPicking() {
			EditorAppState.tool = EditorTool.MOVE; doc.setSelectedBone(slot.getBone()); viewport.setSelectionHidden(false);
			Vector2 axis = localPoint(slot.getBone().getAppliedPose().getWorldX() + 30f * camera().zoom, slot.getBone().getAppliedPose().getWorldY());
			require(call(EditorViewport.class, viewport, "pickTransformAxis", POINT, axis.x, axis.y) == EditorViewport.TransformAxis.X, "Visible gizmo was not pickable");
			viewport.setSelectionHidden(true);
			require(call(EditorViewport.class, viewport, "pickTransformAxis", POINT, axis.x, axis.y) == EditorViewport.TransformAxis.FREE, "Hidden gizmo still intercepts input");
			float x = slot.getBone().getPose().getX(), y = slot.getBone().getPose().getY();
			float blankX = viewport.getWidth() - 24f, blankY = 24f;
			call(EditorViewport.class, viewport, "touchDown", new Class<?>[] {float.class, float.class, int.class}, blankX, blankY, Input.Buttons.LEFT);
			require(doc.getSelectedBone() == slot.getBone(), "Hidden blank click discarded the selection");
			require(!(boolean)read(EditorViewport.class, viewport, "dragging"), "Blank drag edited an invisible selection");
			call(EditorViewport.class, viewport, "touchDragged", POINT, blankX - 60f, blankY + 40f);
			call(EditorViewport.class, viewport, "touchUp", NONE);
			require(slot.getBone().getPose().getX() == x && slot.getBone().getPose().getY() == y, "Hidden blank drag changed pose");
		}
		private void verifyStatusPassThrough() {
			Table status = (Table)read(EditorApp.class, this, "viewportStatus");
			require(status.isVisible(), "Special viewport state has no persistent status");
			Vector2 point = status.localToStageCoordinates(new Vector2(status.getWidth() / 2f, status.getHeight() / 2f));
			require(viewport.getStage().hit(point.x, point.y, true) == viewport, "Viewport status intercepts pointer input");
			Vector2 screen = viewport.getStage().stageToScreenCoordinates(point);
			viewport.getStage().touchDown(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.MIDDLE);
			require((boolean)read(EditorViewport.class, viewport, "draggingView"), "Status did not pass through middle-button pan");
			viewport.getStage().touchUp(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.MIDDLE);
		}
		private void shortcut(int key) {
			Input previous = Gdx.input;
			try {
				Gdx.input = (Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class}, (proxy, method, values) -> {
					if (method.getName().equals("isKeyPressed")) return (int)values[0] == Input.Keys.SHIFT_LEFT;
					return method.invoke(previous, values);
				});
				viewport.getStage().setKeyboardFocus(viewport);
				require(viewport.getStage().keyDown(key), "Global shortcut was not routed by Stage");
			} finally { Gdx.input = previous; }
		}
		private void menuToggle(String label) {
			call(EditorApp.class, this, "showInterfaceMenu", NONE);
			PopupMenu menu = (PopupMenu)read(EditorApp.class, this, "interfaceMenu"); menu.validate();
			for (Actor child : menu.getChildren()) if (child instanceof PopupMenu.MenuItem item && item.getLabel().equals(label)) {
				clickActor(item); return;
			}
			throw new AssertionError("Menu item not found: " + label);
		}
		private void clickActor(Actor actor) {
			Vector2 point = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f));
			point = viewport.getStage().stageToScreenCoordinates(point);
			viewport.getStage().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
			viewport.getStage().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
		}
		private void showViewportSettings() {
			call(EditorApp.class, this, "showSettings", NONE);
			call(SettingsDialog.class, read(EditorApp.class, this, "settingsDialog"), "selectPage", new Class<?>[] {SettingsDialog.Page.class}, SettingsDialog.Page.VIEWPORT);
		}
		private ScrollPane settingsScroll() {
			Object dialog = read(EditorApp.class, this, "settingsDialog");
			Container<?> content = (Container<?>)read(SettingsDialog.class, dialog, "content");
			content.validate(); ScrollPane scroll = (ScrollPane)content.getActor(); scroll.validate(); return scroll;
		}
		private TextButton toggleForLabel(Table table, String label) {
			for (int index = 0; index + 1 < table.getChildren().size; index++)
				if (table.getChildren().get(index) instanceof Label text && text.getText().toString().equals(label)
					&& table.getChildren().get(index + 1) instanceof TextButton button) return button;
			throw new AssertionError("Settings toggle not found: " + label);
		}
		private OrthographicCamera camera() { return (OrthographicCamera)read(EditorViewport.class, viewport, "camera"); }
		private Vector2 localPoint(float worldX, float worldY) {
			float sign = viewport.isViewportFlipped() ? -1f : 1f;
			return new Vector2(viewport.getWidth() / 2f + sign * (worldX - camera().position.x) / camera().zoom,
				viewport.getHeight() / 2f + (worldY - camera().position.y) / camera().zoom);
		}
		private Vector2 meshPoint(int vertex) {
			float[] world = new float[8]; mesh.computeWorldVertices(doc.skeleton, slot, 0, 8, world, 0, 2);
			return localPoint(world[vertex * 2], world[vertex * 2 + 1]);
		}
		private void capture(String name) {
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try { BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(output.resolve(width + "x" + height + "-" + name + ".png").toFile()), pixmap);
			} finally { pixmap.dispose(); }
		}
		private void stop(Throwable error) { if (stopped) return; failure = error; stopped = true; Gdx.app.exit(); }
	}
	public static void main(String[] args) throws Exception {
		Path output = Files.createDirectories(Path.of(args[2]).toAbsolutePath()).toRealPath();
		Path home = Files.createTempDirectory(output, ".visibility-home-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		try {
			for (boolean restoreOnly : new boolean[] {false, true}) {
				CaptureApp app = new CaptureApp(home, output, restoreOnly);
				Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
				config.setTitle("OpenSpine Hidden Visibility Verification"); config.setInitialVisible(false);
				config.setWindowedMode(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
				config.setResizable(false); config.disableAudio(true); config.setIdleFPS(60);
				config.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
				new Lwjgl3Application(app, config);
				if (app.failure != null) throw new AssertionError("Hidden viewport visibility verification failed", app.failure);
			}
			System.out.println("PASS: saved visibility preferences restored in a second isolated editor instance.");
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
	private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
