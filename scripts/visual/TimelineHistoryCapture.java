package com.openspine.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Animation;
import com.openspine.EditorApp;
import com.openspine.io.AutoSaveStore;
import com.openspine.model.AnimationEditor;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;

/** Hidden GL regression for history changes during Dopesheet key or inline curve handle drags. */
public final class TimelineHistoryCapture {
	private static final Class<?>[] NONE = {};
	private static final class CaptureApp extends EditorApp {
		private final Path output;
		private final String scenario;
		private SkeletonDocument doc;
		private AnimationEditor editor;
		private EditHistory history;
		private TimelinePanel panel;
		private Animation animation;
		private Actor canvas, surface;
		private InputListener pointer;
		private InputEvent event;
		private float startX, startY, endX;
		private long restoredRevision;
		private float dragTime = 0.5f;
		private int frames;
		private Throwable failure;
		private boolean stopped;
		CaptureApp(Path home, Path output, String scenario) { super(null, home); this.output = output; this.scenario = scenario; }
		@Override public void create() {
			try {
				super.create();
				call(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				doc = (SkeletonDocument)read(EditorApp.class, this, "doc");
				history = (EditHistory)read(EditorApp.class, this, "history");
				panel = (TimelinePanel)read(EditorApp.class, this, "timelinePanel");
				canvas = (Actor)read(TimelinePanel.class, panel, "canvas");
				editor = new AnimationEditor(doc);
				doc.setPlaying(false); doc.data.getAnimations().clear(); doc.data.setFps(30f);
				animation = editor.addAnimation("拖动期间撤销 · 保留重做");
				Animation.TranslateTimeline timeline = new Animation.TranslateTimeline(3, 0, 0);
				timeline.setFrame(0, 0f, 0f, 0f); timeline.setFrame(1, 0.5f, 10f, 5f); timeline.setFrame(2, 1.2f, 20f, 10f);
				animation.setTimelines(new Array<>(new Animation.Timeline[] {timeline}), new IntArray());
				editor.refreshAnimation(animation);
				if (handles()) editor.setBezierHandlesChannel(animation, timeline, 1, 0, 0.2f, 19f, 0.8f, 18f);
				doc.animationState = doc.newAnimationState(); doc.setAnimation(animation.getName());
				doc.setSelectedBone(doc.skeleton.getRootBone()); panel.rebuild();
				((SplitPane)read(EditorApp.class, this, "workspace")).setSplitAmount(0.32f);
				history.clear(); history.markSaved();
				var before = call(TimelinePanel.class, panel, "snapshotSelected",
					new Class<?>[] {Animation.class, Animation.Timeline.class}, animation, timeline());
				editor.editCurveKey(animation, timeline(), 1, 0, 0.5f, 17f);
				call(TimelinePanel.class, panel, "recordTimelineChange",
					new Class<?>[] {String.class, AnimationEditor.TimelineSnapshot.class}, "值编辑", before);
				call(TimelinePanel.class, panel, "setTimelineView", new Class<?>[] {boolean.class}, handles());
				if (handles()) call(canvas.getClass(), canvas, "selectCurveKey",
					new Class<?>[] {Animation.Timeline.class, int.class, int.class}, timeline(), 1, 0);
				surface = canvas;
			} catch (Throwable error) { stop(error); }
		}
		@Override public void render() {
			if (stopped) return;
			try {
				super.render(); frames++;
				if (frames == 8) {
					capture("before"); beginDrag();
					pointer.touchDragged(event, endX, startY, 0);
					if (handles()) require((int)read(canvas.getClass(), canvas, "curveDragHandle") == 2,
						"Pointer must start an outgoing handle drag");
					else {
						require((boolean)read(canvas.getClass(), canvas, "draggingKeys"), "Pointer must start a key drag");
						require((float)read(canvas.getClass(), canvas, "dragPreviewDelta") > 0f, "Drag preview must move the key");
					}
					if (external()) editor.editCurveKey(animation, timeline(), 2, 1, 1.2f, 42f);
					else require(history.undo(), "Undo the earlier value edit during pointer preview");
					restoredRevision = doc.revision;
					// Exercise the input guard before act/rebuild has a chance to cancel the external edit's preview.
					if (external()) pointer.touchUp(event, endX, startY, 0, Input.Buttons.LEFT);
				}
				if (frames == 12) {
					require(!(boolean)read(canvas.getClass(), canvas, "draggingKeys")
						&& (int)read(canvas.getClass(), canvas, "curveDragHandle") == 0,
						"Document changes must cancel key and handle previews before the next pointer event");
					pointer.touchDragged(event, endX + 10f, startY, 0);
					pointer.touchUp(event, endX + 10f, startY, 0, Input.Buttons.LEFT);
					require(doc.revision == restoredRevision && history.canUndo() == external() && history.canRedo() != external(),
						"Stale pointer release overwrote undo: revision " + restoredRevision + " -> " + doc.revision
						+ ", redo=" + history.canRedo() + ", middle time=" + editor.frameTime(timeline(), 1));
					require(editor.frameTime(timeline(), 1) == 0.5f && editor.curveFrameValue(timeline(), 1, 0) == (external() ? 17f : 10f),
						"The original key time and the live value remain intact");
					if (external()) {
						require(editor.curveFrameValue(timeline(), 2, 1) == 42f, "External edits remain intact");
						// Deliver the external author's usual UI refresh after exercising release without it.
						panel.rebuild();
					}
					else require(history.redo() && editor.curveFrameValue(timeline(), 1, 0) == 17f, "Redo is retained and restores edited value");
				}
				if (frames == 16) {
					capture("after");
					dragTime = 0f;
					long before = doc.revision;
					beginDrag(); pointer.touchDragged(event, endX, startY + 10f, 0);
					pointer.touchUp(event, endX, startY + 10f, 0, Input.Buttons.LEFT);
					require(doc.revision > before && history.canUndo(), "A fresh drag after redo must still edit normally");
					require(history.undo() && editor.frameTime(timeline(), 0) == 0f
						&& editor.curveFrameValue(timeline(), 1, 0) == 17f, "Fresh drag has its own undo entry");
					if (external()) require(editor.curveFrameValue(timeline(), 2, 1) == 42f, "Undoing a fresh drag preserves external edits");
					System.out.println("PASS " + scenario + ": stale pointer preview cancels; live data and history retained; fresh drag and undo succeed.");
					stop(null);
				}
			} catch (Throwable error) { stop(error); }
		}
		private void beginDrag() {
			if (handles()) {
				float[] point = (float[])call(canvas.getClass(), canvas, "curveHandlePoint",
					new Class<?>[] {Animation.Timeline.class, int.class, int.class, boolean.class}, timeline(), 1, 1, true);
				require(point != null, "Outgoing curve handle must be visible");
				startX = point[0]; startY = point[1]; endX = startX + 20f;
			} else {
				Object track = call(canvas.getClass(), canvas, "trackForTimeline", new Class<?>[] {Animation.Timeline.class}, timeline());
				int row = (int)call(canvas.getClass(), canvas, "visibleTimelineRow", new Class<?>[] {track.getClass()}, track);
				float height = (float)call(canvas.getClass(), canvas, "rowHeight", NONE);
				float ruler = ((Number)read(canvas.getClass(), canvas, "RULER_H")).floatValue();
				startY = canvas.getHeight() - ruler - row * height - height / 2;
				startX = (float)call(canvas.getClass(), canvas, "xAtTime", new Class<?>[] {float.class}, dragTime);
				endX = (float)call(canvas.getClass(), canvas, "xAtTime", new Class<?>[] {float.class}, dragTime + 0.2f);
			}
			event = new InputEvent(); event.setStage(surface.getStage()); event.setTarget(surface);
			for (var listener : surface.getListeners()) if (listener instanceof InputListener candidate
				&& candidate.touchDown(event, startX, startY, 0, Input.Buttons.LEFT)) { pointer = candidate; break; }
			require(pointer != null, "No pointer listener received the key press");
		}
		private Animation.Timeline timeline() { return editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0); }
		private boolean handles() { return scenario.startsWith("handle"); }
		private boolean external() { return scenario.endsWith("external"); }
		private void capture(String name) {
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try {
				BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(output.resolve(width + "x" + height + "-" + scenario + "-" + name + ".png").toFile()), pixmap);
			} finally { pixmap.dispose(); }
		}
		private void stop(Throwable error) { if (stopped) return; failure = error; stopped = true; Gdx.app.exit(); }
	}
	public static void main(String[] args) throws Exception {
		Path output = Files.createDirectories(Path.of(args[2]).toAbsolutePath()).toRealPath();
		Path taskHome = Files.createTempDirectory(output, ".timeline-home-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", taskHome.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, taskHome.resolve("autosave").toString());
		CaptureApp app = new CaptureApp(taskHome, output, args.length > 3 ? args[3] : "dopesheet");
		try {
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setTitle("OpenSpine Hidden Timeline History Verification"); config.setInitialVisible(false);
			config.setWindowedMode(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
			config.setResizable(false); config.disableAudio(true); config.setIdleFPS(60);
			config.setPreferencesConfig(taskHome.toString(), com.badlogic.gdx.Files.FileType.Absolute);
			new Lwjgl3Application(app, config);
		} finally {
			System.setProperty("user.home", oldHome);
			if (oldAutosave == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY);
			else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, oldAutosave);
			require(taskHome.getParent().equals(output), "Cleanup escaped output");
			try (var paths = Files.walk(taskHome)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
		}
		if (app.failure != null) throw new AssertionError("Hidden timeline verification failed", app.failure);
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
