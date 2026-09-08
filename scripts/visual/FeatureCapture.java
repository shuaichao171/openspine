package com.openspine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.openspine.io.FfmpegAnimationExporter;
import com.openspine.ui.SettingsDialog;
import com.openspine.ui.UiAppearance.Mode;

/** Manual visual regression capture for project diagnostics and export workflows. */
public final class FeatureCapture {
	private static final class CaptureApp extends EditorApp {
		private final String mode;
		private final String output;
		private int frame;

		CaptureApp(String mode, String output) {
			this.mode = mode;
			this.output = output;
		}

		@Override public void create() {
			super.create();
			invoke(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {Mode.class}, Mode.LIGHT);
			switch (mode) {
			case "export-menu" -> invoke(EditorApp.class, this, "exportProject", new Class<?>[0]);
			case "diagnostics" -> invoke(EditorApp.class, this, "showProjectDiagnostics", new Class<?>[0]);
			case "settings-export" -> {
				invoke(EditorApp.class, this, "showSettings", new Class<?>[0]);
				SettingsDialog dialog = (SettingsDialog)field(EditorApp.class, this, "settingsDialog");
				invoke(SettingsDialog.class, dialog, "selectPage",
					new Class<?>[] {SettingsDialog.Page.class}, SettingsDialog.Page.EXPORT);
			}
			case "video-settings" -> {
				FfmpegAnimationExporter.setConfiguredExecutable(
					Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
				invoke(EditorApp.class, this, "showVideoExport",
					new Class<?>[] {FfmpegAnimationExporter.Format.class}, FfmpegAnimationExporter.Format.WEBM);
			}
			case "progress" -> invoke(EditorApp.class, this, "showAnimationRenderProgress",
				new Class<?>[] {String.class, Path.class}, "导出 WebM 动画", Path.of("walk.webm"));
			default -> throw new IllegalArgumentException("Unknown capture mode: " + mode);
			}
		}

		@Override public void render() {
			super.render();
			if (++frame != 45) return;
			int width = Gdx.graphics.getBackBufferWidth();
			int height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try {
				BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(output), pixmap);
			} finally { pixmap.dispose(); }
			Gdx.app.exit();
		}

		@Override public void dispose() {
			FfmpegAnimationExporter.setConfiguredExecutable("");
			super.dispose();
		}
	}

	public static void main(String[] args) {
		if (args.length != 2) throw new IllegalArgumentException("Expected capture mode and output path");
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("OpenSpine Feature Visual Check");
		config.setWindowedMode(1280, 760);
		config.setResizable(false);
		config.setDecorated(!WindowsTitleBar.usesCustomFrame());
		config.setForegroundFPS(60);
		CaptureApp app = new CaptureApp(args[0], args[1]);
		config.setWindowListener(new Lwjgl3WindowAdapter() {
			@Override public void created(com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window window) {
				app.setNativeWindowHandle(window.getWindowHandle());
			}
		});
		new Lwjgl3Application(app, config);
	}

	private static Object field(Class<?> type, Object target, String name) {
		try {
			Field field = type.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Unable to read " + name, error);
		}
	}

	private static Object invoke(Class<?> type, Object target, String name, Class<?>[] parameterTypes,
		Object... arguments) {
		try {
			Method method = type.getDeclaredMethod(name, parameterTypes);
			method.setAccessible(true);
			return method.invoke(target, arguments);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Unable to invoke " + name, error);
		}
	}

	private FeatureCapture() {}
}
