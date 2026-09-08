package com.openspine;

import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.openspine.io.AutoSaveStore;
import com.openspine.io.PsdImporter;

/** Exercises real editor disposal with a completed PSD extraction, without touching existing windows. */
public final class PsdImportLifecycleCapture {
	private static final class App extends EditorApp {
		final Path home;
		final boolean settingsOpen;
		PsdImporter.ImportResult imported;
		Throwable failure;
		int frames;
		App(Path home, boolean settingsOpen) { super(null, home); this.home = home; this.settingsOpen = settingsOpen; }
		@Override public void create() {
			try {
				super.create();
				Path psd = home.resolve(settingsOpen ? "settings.psd" : "pending.psd");
				writePsd(psd);
				imported = PsdImporter.extract(psd);
				Field pending = EditorApp.class.getDeclaredField("pendingPsdImport");
				pending.setAccessible(true); pending.set(this, imported);
				if (settingsOpen) {
					Method show = EditorApp.class.getDeclaredMethod("showAtlasPackingSettings", List.class,
						PsdImporter.ImportResult.class);
					show.setAccessible(true);
					show.invoke(this, imported.layers().stream().map(layer -> layer.png().toString()).toList(), imported);
				}
			} catch (Throwable error) { failure = error; Gdx.app.exit(); }
		}
		@Override public void render() {
			if (failure != null) return;
			try { super.render(); if (++frames == 6) Gdx.app.exit(); }
			catch (Throwable error) { failure = error; Gdx.app.exit(); }
		}
	}
	public static void main(String[] args) throws Exception {
		Path output = Files.createDirectories(Path.of(args[0]).toAbsolutePath()).toRealPath();
		Path home = Files.createTempDirectory(output, ".psd-lifecycle-home-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		try {
			for (boolean settingsOpen : new boolean[] {false, true}) {
				App app = new App(home, settingsOpen);
				Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
				config.setTitle("OpenSpine Hidden PSD Lifecycle Verification"); config.setInitialVisible(false);
				config.setWindowedMode(1280, 720); config.setResizable(false); config.disableAudio(true);
				config.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
				new Lwjgl3Application(app, config);
				if (app.failure != null) throw new AssertionError("PSD lifecycle verification failed", app.failure);
				require(app.imported != null && !Files.exists(app.imported.directory()),
					"Closing the editor leaked extracted PSD files; settingsOpen=" + settingsOpen);
				require(Files.isRegularFile(app.imported.source()), "Closing the editor removed the source PSD");
				System.out.println("PASS: editor disposal cleans PSD extraction; settingsOpen=" + settingsOpen);
			}
		} finally {
			System.setProperty("user.home", oldHome);
			if (oldAutosave == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY);
			else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, oldAutosave);
			require(home.getParent().equals(output), "Cleanup escaped the task output directory");
			try (var paths = Files.walk(home)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}
	private static void writePsd(Path path) throws Exception {
		try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
			out.writeBytes("8BPS"); out.writeShort(1); out.write(new byte[6]);
			out.writeShort(4); out.writeInt(1); out.writeInt(1); out.writeShort(8); out.writeShort(3);
			out.writeInt(0); out.writeInt(0); out.writeInt(4); out.writeInt(0); out.writeShort(0);
			out.write(new byte[] {(byte)180, 40, 90, (byte)255});
		}
	}
	private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
