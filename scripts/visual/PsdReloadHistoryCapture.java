package com.openspine;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.HasSequence;
import com.openspine.io.AtlasAlphaModes;
import com.openspine.io.AtlasPacker;
import com.openspine.io.AutoSaveStore;
import com.openspine.io.PsdImporter;
import com.openspine.io.PsdLayerInstaller;
import com.openspine.io.SkeletonExporter;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/**
 * Hidden GL test of the real editor's PSD installation, image reload, and import undo/redo hooks.
 * PNGs, synthetic PSD layer metadata, preferences and autosave all live in a task-owned temporary directory.
 * Compile against current bin, then run com.openspine.PsdReloadHistoryCapture output-directory.
 * Optional second argument "simulate-old" replaces the sealed snapshot with the old pre-layer snapshot;
 * this fault-injection mode must fail the same final resource-identity assertion as the original defect.
 */
public final class PsdReloadHistoryCapture {
	private static final Class<?>[] NONE = {};
	private static final class App extends EditorApp {
		final Path home;
		final boolean simulateOld;
		Throwable failure;
		boolean stopped;
		App(Path home, boolean simulateOld) { super(null, home); this.home = home; this.simulateOld = simulateOld; }
		@Override public void create() {
			try {
				super.create();
				SkeletonDocument doc = (SkeletonDocument)read(EditorApp.class, this, "doc");
				SkeletonEditor editor = (SkeletonEditor)read(EditorApp.class, this, "editor");
				SkeletonData data = new SkeletonData(); data.setName("psd-history"); data.setVersion("4.3.0");
				BoneData root = new BoneData(0, "root", null); root.getSetupPose().setInherit(BoneData.Inherit.normal);
				data.getBones().add(root);
				TextureAtlas original = new TextureAtlas();
				Path source = home.resolve("fixture.json");
				doc.replaceContent(original, data, new Skeleton(data), source.toString(), null, source.toString());
				Files.writeString(source, SkeletonExporter.toJson(doc));
				call(EditorApp.class, this, "finishLoad", new Class<?>[] {String.class}, "PSD history fixture");
				EditHistory history = (EditHistory)read(EditorApp.class, this, "history"); history.markSaved();
				Path images = Files.createDirectory(home.resolve("images"));
				Path bottom = images.resolve("bottom.png"), top = images.resolve("top.png");
				writeImage(bottom, 0xff339966); writeImage(top, 0xffcc3311);
				AtlasPacker.PackResult packed = AtlasPacker.pack(List.of(bottom, top), home.resolve("fixture.atlas"), 64, 2);
				TextureAtlas imported = AtlasAlphaModes.load(new FileHandle(packed.atlas().toFile()));
				PsdImporter.ImportResult psd = new PsdImporter.ImportResult(home.resolve("fixture.psd"), images, 20, 20, List.of(
					new PsdImporter.ImportedLayer("Bottom", "bottom", 0, 0, 20, 20, true, 255, bottom, 0),
					new PsdImporter.ImportedLayer("Top", "top", 3, 4, 20, 20, true, 255, top, 1)));
				PsdLayerInstaller.Transaction layers = PsdLayerInstaller.prepare(doc, editor, psd, imported);
				Object adoption = call(EditorApp.class, this, "adoptImportedAtlas",
					new Class<?>[] {TextureAtlas.class, AtlasPacker.PackResult.class}, imported, packed);
				Object beforeLayers = read(adoption.getClass(), adoption, "importedRegions");
				layers.apply();
				call(adoption.getClass(), adoption, "retainForHistory", NONE);
				if (simulateOld) {
					Field snapshot = adoption.getClass().getDeclaredField("importedRegions"); snapshot.setAccessible(true);
					snapshot.set(adoption, beforeLayers);
					System.out.println("FAULT INJECTION: restored the original pre-PSD-layer history snapshot.");
				}
				call(EditorApp.class, this, "installPsdLayers",
					new Class<?>[] {PsdImporter.ImportResult.class, PsdLayerInstaller.Transaction.class, adoption.getClass()},
					psd, layers, adoption);
				require(doc.atlas == imported && doc.data.getSlots().size == 2, "PSD installation creates two slots and adopts atlas B");
				assertBindings(doc, "initial PSD import");
				String authored = SkeletonExporter.toJson(doc);
				Skeleton liveSkeleton = doc.skeleton; SkeletonData liveData = doc.data;
				long revision = doc.revision; boolean dirty = doc.dirty;
				String undo = history.undoLabel(), redo = history.redoLabel();
				writeImage(bottom, 0xff6633cc); writeImage(top, 0xff33ccdd);
				AtlasPacker.pack(List.of(bottom, top), packed.atlas(), 64, 2);
				call(EditorApp.class, this, "reloadImages", NONE);
				TextureAtlas refreshed = doc.atlas;
				require(refreshed != imported && refreshed != original, "reload creates fresh atlas C");
				assertBindings(doc, "image reload");
				require(authored.equals(SkeletonExporter.toJson(doc)) && doc.data == liveData && doc.skeleton == liveSkeleton
					&& doc.revision == revision && doc.dirty == dirty && java.util.Objects.equals(undo, history.undoLabel())
					&& java.util.Objects.equals(redo, history.redoLabel()), "reload preserves author data, live skeleton and history");
				require(history.undo() && doc.atlas == original && doc.data.getSlots().isEmpty(),
					"undo PSD import restores atlas A and removes imported slots");
				require(history.redo() && doc.atlas == imported && doc.data.getSlots().size == 2,
					"redo PSD import restores atlas B and its slots");
				assertBindings(doc, "PSD redo after image reload");
				require(authored.equals(SkeletonExporter.toJson(doc)), "PSD undo/redo preserves all authored attachment setup");
				require(history.undo() && history.redo(), "PSD import remains repeatedly reversible after reload");
				assertBindings(doc, "second PSD redo");
				System.out.println("PASS: PSD import -> image reload -> undo -> redo restores every attachment to the active historical atlas.");
				stop(null);
			} catch (Throwable error) { stop(error); }
		}
		@Override public void render() { if (!stopped) super.render(); }
		private void stop(Throwable error) { failure = error; stopped = true; Gdx.app.exit(); }
	}

	private static void assertBindings(SkeletonDocument doc, String context) {
		int checked = 0;
		for (Skin skin : doc.data.getSkins()) for (Skin.SkinEntry entry : skin.getAttachments()) {
			if (!(entry.getAttachment() instanceof HasSequence attachment)) continue;
			for (int frame = 0; frame < attachment.getSequence().getRegions().length; frame++) {
				String regionName = attachment.getSequence().getPath(attachment.getPath(), frame);
				require(attachment.getSequence().getRegions()[frame] == doc.atlas.findRegion(regionName),
					context + ": attachment " + entry.getPlaceholder() + " retains a region outside the active atlas");
				checked++;
			}
		}
		require(checked == 2, context + ": both imported image attachments must be checked");
	}

	public static void main(String[] args) throws Exception {
		Path output = Files.createDirectories(Path.of(args[0]).toAbsolutePath()).toRealPath();
		Path home = Files.createTempDirectory(output, ".psd-reload-history-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		App app = new App(home, args.length > 1 && args[1].equals("simulate-old"));
		try {
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setTitle("OpenSpine Hidden PSD Reload History Verification"); config.setInitialVisible(false);
			config.setWindowedMode(1280, 720); config.setResizable(false); config.disableAudio(true);
			config.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
			new Lwjgl3Application(app, config);
		} finally {
			System.setProperty("user.home", oldHome);
			if (oldAutosave == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY);
			else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, oldAutosave);
			require(home.getParent().equals(output), "Cleanup escaped task output directory");
			try (var paths = Files.walk(home)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		if (app.failure != null) throw new AssertionError("PSD reload history verification failed", app.failure);
	}

	private static void writeImage(Path path, int color) throws Exception {
		BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) image.setRGB(x, y, color);
		ImageIO.write(image, "png", path.toFile());
	}
	private static Object read(Class<?> type, Object target, String name) {
		try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static Object call(Class<?> type, Object target, String name, Class<?>[] parameters, Object... args) {
		try { Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method.invoke(target, args); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
