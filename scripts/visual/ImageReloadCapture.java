package com.openspine;

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
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.HasSequence;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.io.AtlasAlphaModes;
import com.openspine.io.AutoSaveStore;
import com.openspine.io.SkeletonExporter;
import com.openspine.io.SkeletonLoader;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.ui.PopupMenu;
import com.openspine.ui.UiAppearance;
import com.openspine.view.EditorViewport;

/** Hidden GL/Scene2D checks: fresh external/loose pixels, author/history preservation, and atomic failures. */
public final class ImageReloadCapture {
	private static final String[] REGIONS = {"角色", "run01", "run02", "mesh", "hidden01", "hidden02"};
	private static final Class<?>[] NONE = new Class<?>[0];
	private static final class App extends EditorApp {
		final Path output, assets;
		final boolean loose;
		SkeletonDocument doc;
		SkeletonEditor editor;
		EditorViewport viewport;
		EditHistory history;
		Snapshot before;
		byte[] originalPixels, reloadedPixels;
		TextureAtlas originalAtlas, reloadedAtlas;
		Throwable failure;
		int frames;
		boolean stopped;
		App(Path home, Path output, boolean loose) {
			super(null, home); this.output = output; this.assets = home.resolve(loose ? "loose" : "atlas"); this.loose = loose;
		}
		@Override public void create() {
			try {
				super.create();
				doc = (SkeletonDocument)read(EditorApp.class, this, "doc");
				editor = (SkeletonEditor)read(EditorApp.class, this, "editor");
				viewport = (EditorViewport)read(EditorApp.class, this, "viewport");
				history = (EditHistory)read(EditorApp.class, this, "history");
				call(this, "setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				writeImages(assets, false, false);
				TextureAtlas atlas = AtlasAlphaModes.load(new FileHandle(assets.resolve("textures/fixture.atlas").toFile()));
				SkeletonData data = fixture(atlas);
				Path source = assets.resolve("fixture.json");
				doc.replaceContent(atlas, data, new Skeleton(data), source.toString(), "textures/fixture.atlas", source.toString());
				doc.resourceDirectory = assets.toString();
				Files.writeString(source, SkeletonExporter.toJson(doc));
				if (loose) {
					Files.delete(assets.resolve("textures/fixture.atlas"));
					SkeletonLoader.load(doc, new FileHandle(source.toFile()));
				}
				doc.animationState = doc.newAnimationState();
				doc.skeleton.setSkin(doc.data.getDefaultSkin());
				doc.skeleton.setupPose(); doc.setAnimation("deform"); doc.setPlaying(false); doc.scrubTo(0.4f);
				doc.setSelectedBone(doc.skeleton.findBone("root"));
				call(this, "finishLoad", new Class<?>[] {String.class}, "图片刷新验证");
				history = (EditHistory)read(EditorApp.class, this, "history");
				history.markSaved();
				if (loose) {
					doc.data.setName("authored"); doc.markDirty();
					history.record("作者名称", () -> doc.data.setName("fixture"), () -> doc.data.setName("authored"));
					doc.data.setName("redo-name"); doc.markDirty();
					history.record("保留重做", () -> doc.data.setName("authored"), () -> doc.data.setName("redo-name"));
					require(history.undo(), "fixture must retain a redo entry");
				}
				viewport.setBonesVisible(false); viewport.setGridVisible(false); viewport.setSelectionHidden(true);
				viewport.selectAttachment(doc.data.findSlot("mesh"), doc.data.getDefaultSkin().getAttachment(2, "mesh"));
				doc.setSlotSelection(doc.skeleton.findSlot("mesh"), "mesh", "mesh");
				require(doc.getSelectionKind() == SkeletonDocument.SelectionKind.ATTACHMENT, "fixture must select a mesh attachment");
				doc.poseMode = true;
				doc.skeleton.findBone("root").getPose().setRotation(7.5f);
				doc.skeleton.findSlot("mesh").getPose().getDeform().clear();
				doc.skeleton.findSlot("mesh").getPose().getDeform().addAll(new float[] {81f, -39f, 155f, -35f, 162f, 41f, 84f, 42f});
				doc.skeleton.updateWorldTransform(com.esotericsoftware.spine.Physics.pose);
				call(this, "syncCommandState", NONE);
			} catch (Throwable error) { stop(error); }
		}
		@Override public void render() {
			if (stopped) return;
			try {
				super.render(); frames++;
				if (frames == 8) {
					before = snapshot(); originalAtlas = doc.atlas; originalPixels = viewportPixels(); capture("before");
					writeImages(assets, true, false);
					Files.writeString(assets.resolve("fixture.json"), "intentionally unreadable author file");
					showReloadMenu();
				}
				if (frames == 10) { capture("menu"); clickReloadItem(); verifyAuthorState(); }
				if (frames == 14) {
					require(doc.atlas != originalAtlas, "reload must install fresh atlas/texture objects");
					reloadedAtlas = doc.atlas; reloadedPixels = viewportPixels();
					require(doc.atlas.findRegion("角色").originalWidth == 64 && doc.atlas.findRegion("角色").originalHeight == 64,
						"fresh pixel dimensions were not loaded");
					require(differentPixels(originalPixels, reloadedPixels) > 1000, "new image colors must reach the framebuffer");
					for (Skin skin : doc.data.getSkins()) for (Skin.SkinEntry entry : skin.getAttachments())
						if (entry.getAttachment() instanceof HasSequence attachment)
							for (int index = 0; index < attachment.getSequence().getRegions().length; index++)
								require(attachment.getSequence().getRegions()[index] == doc.atlas.findRegion(attachment.getSequence().getPath(attachment.getPath(), index)),
									"all active/inactive Region and Mesh sequence frames must be rebound");
					verifyAuthorState(); capture("reloaded");
					for (Texture texture : originalAtlas.getTextures())
						require(texture.getTextureObjectHandle() != 0 && Gdx.gl.glIsTexture(texture.getTextureObjectHandle()), "historical atlas texture was disposed");
					writeImages(assets, true, true);
					shortcut();
					verifyFailedReload("hidden02");
				}
				if (frames == 16) { capture("missing-frame-error"); viewport.getStage().keyDown(Input.Keys.ESCAPE); }
				if (frames == 18) {
					require(Arrays.equals(reloadedPixels, viewportPixels()), "missing sequence frame changed the existing framebuffer");
					capture("missing-frame-kept-image");
					writeImages(assets, true, false);
					Files.writeString(assets.resolve((loose ? "images/" : "textures/") + "角色.png"), "broken image");
					call(this, "reloadImages", NONE); verifyFailedReload(null);
				}
				if (frames == 20) { capture("corrupt-image-error"); viewport.getStage().keyDown(Input.Keys.ESCAPE); }
				if (frames == 22) {
					require(Arrays.equals(reloadedPixels, viewportPixels()), "corrupt image changed the existing framebuffer");
					capture("corrupt-image-kept-image");
					if (loose) {
						require(history.undo() && doc.data.getName().equals("fixture"), "preexisting author undo is preserved");
						require(history.redo() && doc.data.getName().equals("authored"), "preexisting author redo is preserved");
						require(history.redo() && doc.data.getName().equals("redo-name"), "the original redo stack is preserved");
						require(doc.atlas == reloadedAtlas, "author undo/redo must keep current refreshed atlas");
					} else require(!history.canUndo() && !history.canRedo() && !doc.dirty, "clean refresh must remain outside author history");
					System.out.println("PASS: " + (loose ? "loose images" : "external atlas")
						+ " menu refresh updates pixels and dimensions; retains author geometry/deform/selection/time/dirty/revision/history; missing inactive sequence/corrupt image retain old pixels.");
					stop(null);
				}
			} catch (Throwable error) { stop(error); }
		}
		private void verifyAuthorState() {
			require(before.equals(snapshot()), "reload changed author data, preview state, revision, dirty flag, or history\nbefore=" + before + "\nafter=" + snapshot());
		}
		private void verifyFailedReload(String detail) {
			verifyAuthorState(); require(doc.atlas == reloadedAtlas, "failed reload changed atlas identity");
			if (detail != null) {
				String status = ((Label)read(EditorApp.class, this, "statusLabel")).getText().toString();
				require(status.contains(detail), "missing sequence error should identify its exact frame: " + status);
			}
		}
		private Snapshot snapshot() {
			return new Snapshot(SkeletonExporter.toJson(doc), doc.getSelectedBone().getData().getName(), doc.getCurrentTime(),
				doc.dirty, doc.revision, history.undoLabel(), history.redoLabel(), doc.poseMode, doc.playing,
				doc.skeleton, doc.animationState, doc.getSelectedBone(), doc.getSelectionKind().name(), doc.getSelectedSlotName(),
				doc.getSelectedAttachmentPlaceholder(), doc.getSelectedAttachmentName(),
				Arrays.toString(doc.skeleton.findSlot("mesh").getPose().getDeform().toArray()),
				doc.skeleton.findBone("root").getPose().getRotation(),
				read(EditorViewport.class, viewport, "selectedAttachmentIdentity"), read(EditorViewport.class, viewport, "selectedAttachmentSlotData"));
		}
		private void showReloadMenu() { call(this, "showProjectMenu", NONE); ((PopupMenu)read(EditorApp.class, this, "projectMenu")).validate(); }
		private void clickReloadItem() {
			PopupMenu menu = (PopupMenu)read(EditorApp.class, this, "projectMenu");
			for (Actor child : menu.getChildren()) if (child instanceof PopupMenu.MenuItem item && item.getLabel().equals("重新加载图片")) {
				Vector2 point = viewport.getStage().stageToScreenCoordinates(item.localToStageCoordinates(new Vector2(item.getWidth() / 2, item.getHeight() / 2)));
				viewport.getStage().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
				viewport.getStage().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT); return;
			}
			throw new AssertionError("Reload images menu item missing");
		}
		private void shortcut() {
			Input original = Gdx.input;
			try {
				Gdx.input = (Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class}, (proxy, method, args) -> {
					if (method.getName().equals("isKeyPressed")) return (int)args[0] == Input.Keys.CONTROL_LEFT;
					return method.invoke(original, args);
				});
				viewport.getStage().setKeyboardFocus(viewport);
				require(viewport.getStage().keyDown(Input.Keys.R), "Ctrl+R must dispatch through the actual Stage shortcut listener");
			} finally { Gdx.input = original; }
		}
		private byte[] viewportPixels() {
			return ScreenUtils.getFrameBufferPixels(Math.round((float)read(EditorViewport.class, viewport, "screenPixelOriginX")),
				Math.round((float)read(EditorViewport.class, viewport, "screenPixelOriginY")),
				Math.round(viewport.getWidth() * (float)read(EditorViewport.class, viewport, "screenPixelScaleX")),
				Math.round(viewport.getHeight() * (float)read(EditorViewport.class, viewport, "screenPixelScaleY")), false);
		}
		private void capture(String name) {
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] bytes = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try { BufferUtils.copy(bytes, 0, pixmap.getPixels(), bytes.length);
				PixmapIO.writePNG(new FileHandle(output.resolve((loose ? "loose-" : "atlas-") + name + ".png").toFile()), pixmap);
			} finally { pixmap.dispose(); }
		}
		private void stop(Throwable error) { if (stopped) return; failure = error; stopped = true; Gdx.app.exit(); }
	}
	private record Snapshot(String authorJson, String selection, float time, boolean dirty, long revision,
		String undo, String redo, boolean poseMode, boolean playing, Object skeleton, Object animationState,
		Object selectedBone, String selectionKind, String slot, String placeholder, String attachment, String liveDeform, float liveRotation,
		Object viewportAttachment, Object viewportSlot) {}

	public static void main(String[] args) throws Exception {
		Path output = Files.createDirectories(Path.of(args[0]).toAbsolutePath()).toRealPath();
		Path home = Files.createTempDirectory(output, ".image-reload-home-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		try {
			for (boolean loose : new boolean[] {false, true}) {
				App app = new App(home, output, loose);
				Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
				cfg.setTitle("OpenSpine Hidden Image Reload Verification"); cfg.setInitialVisible(false);
				cfg.setWindowedMode(args.length > 1 ? Integer.parseInt(args[1]) : 1280, args.length > 2 ? Integer.parseInt(args[2]) : 720);
				cfg.setResizable(false); cfg.disableAudio(true);
				cfg.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
				new Lwjgl3Application(app, cfg);
				if (app.failure != null) throw new AssertionError("Hidden image reload verification failed", app.failure);
			}
		} finally {
			System.setProperty("user.home", oldHome);
			if (oldAutosave == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY); else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, oldAutosave);
			require(home.getParent().equals(output), "Cleanup escaped task output directory");
			try (var paths = Files.walk(home)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
		}
		System.out.println("PASS: hidden verification processes exited and isolated preferences/autosave/source files were cleaned.");
	}

	private static SkeletonData fixture(TextureAtlas atlas) {
		SkeletonData data = new SkeletonData(); data.setName("fixture"); data.setVersion("4.3.0"); data.setImagesPath("images");
		BoneData root = new BoneData(0, "root", null); root.getSetupPose().setInherit(BoneData.Inherit.normal);
		root.getSetupPose().setScaleX(1f); root.getSetupPose().setScaleY(1f); data.getBones().add(root);
		Skin skin = new Skin("default"), inactive = new Skin("inactive"); data.getSkins().addAll(skin, inactive); data.setDefaultSkin(skin);
		for (String name : new String[] {"region", "sequence", "mesh"}) {
			SlotData slot = new SlotData(data.getSlots().size, name, root); slot.setBlendMode(BlendMode.normal); slot.setAttachmentName(name); data.getSlots().add(slot);
		}
		RegionAttachment region = new RegionAttachment("region", sequence(atlas, "角色", false));
		region.setScaleX(1f); region.setScaleY(1f);
		region.setPath("角色"); region.setX(-115f); region.setY(12f); region.setRotation(11f);
		region.setWidth(83f); region.setHeight(67f); region.updateSequence(); skin.setAttachment(0, "region", region);
		RegionAttachment animated = new RegionAttachment("sequence", sequence(atlas, "run", true));
		animated.setScaleX(1f); animated.setScaleY(1f);
		animated.setPath("run"); animated.setWidth(78f); animated.setHeight(68f); animated.updateSequence(); skin.setAttachment(1, "sequence", animated);
		RegionAttachment hidden = new RegionAttachment("hidden", sequence(atlas, "hidden", true));
		hidden.setScaleX(1f); hidden.setScaleY(1f);
		hidden.setPath("hidden"); hidden.setWidth(42f); hidden.setHeight(42f); hidden.updateSequence(); inactive.setAttachment(1, "hidden", hidden);
		MeshAttachment mesh = new MeshAttachment("mesh", sequence(atlas, "mesh", false)); mesh.setPath("mesh");
		mesh.setVertices(new float[] {80f, -40f, 155f, -40f, 160f, 40f, 85f, 40f}); mesh.setWorldVerticesLength(8);
		mesh.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f}); mesh.setTriangles(new short[] {0, 1, 2, 0, 2, 3});
		mesh.setHullLength(8); mesh.setWidth(80f); mesh.setHeight(80f); mesh.updateSequence(); skin.setAttachment(2, "mesh", mesh);
		Animation.DeformTimeline deform = new Animation.DeformTimeline(2, 0, 2, mesh);
		deform.setFrame(0, 0f, mesh.getVertices().clone());
		deform.setFrame(1, 1f, new float[] {82f, -40f, 155f, -30f, 165f, 40f, 83f, 45f});
		Animation animation = new Animation("deform"); Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(deform); animation.setTimelines(timelines, new IntArray()); animation.setDuration(1f); data.getAnimations().add(animation);
		return data;
	}
	private static Sequence sequence(TextureAtlas atlas, String path, boolean frames) {
		Sequence sequence = new Sequence(frames ? 2 : 1, frames); sequence.setStart(1); sequence.setDigits(2);
		for (int index = 0; index < sequence.getRegions().length; index++) sequence.getRegions()[index] = atlas.findRegion(sequence.getPath(path, index));
		return sequence;
	}
	private static void writeImages(Path assets, boolean updated, boolean omitHidden) throws Exception {
		Path textures = Files.createDirectories(assets.resolve("textures")), images = Files.createDirectories(assets.resolve("images"));
		StringBuilder atlas = new StringBuilder(); int size = updated ? 64 : 32;
		for (int index = 0; index < REGIONS.length; index++) {
			String name = REGIONS[index];
			if (omitHidden && name.equals("hidden02")) { Files.deleteIfExists(images.resolve(name + ".png")); continue; }
			Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
			try {
				pixmap.setColor(updated ? 0xEC9945FF : 0x397FC8FF); pixmap.fill();
				pixmap.setColor(updated ? 0x7A4133FF : 0xB9E3EDFF); pixmap.fillCircle(size / 2, size / 2, size / 4);
				pixmap.setColor(0xFFFFFFFF); pixmap.fillRectangle(size / 4, size / 3, size / 8, size / 8);
				PixmapIO.writePNG(new FileHandle(textures.resolve(name + ".png").toFile()), pixmap);
				PixmapIO.writePNG(new FileHandle(images.resolve(name + ".png").toFile()), pixmap);
			} finally { pixmap.dispose(); }
			atlas.append(name).append(".png\nsize: ").append(size).append(", ").append(size)
				.append("\nformat: RGBA8888\nfilter: Linear, Linear\nrepeat: none\npma: false\n")
				.append(name).append("\nrotate: false\nxy: 0, 0\nsize: ").append(size).append(", ").append(size)
				.append("\norig: ").append(size).append(", ").append(size).append("\noffset: 0, 0\nindex: -1\n\n");
		}
		Files.writeString(textures.resolve("fixture.atlas"), atlas);
	}
	private static long differentPixels(byte[] before, byte[] after) {
		require(before.length == after.length, "viewport dimensions changed during image reload"); long different = 0;
		for (int i = 0; i < before.length; i += 4) if (before[i] != after[i] || before[i + 1] != after[i + 1] || before[i + 2] != after[i + 2]) different++;
		return different;
	}
	private static Object read(Class<?> type, Object target, String name) {
		try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static Object call(Object target, String name, Class<?>[] types, Object... values) {
		try { Method method = EditorApp.class.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, values); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
