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
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
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

/** Reproducible real Graph authoring checks in an isolated, hidden GL process. */
public final class GraphAuthoringCapture {
	private static final Class<?>[] NONE = {};
	private static final float[] TIMES = {0.1234567f, 0.5000123f, 1.234567f};
	private static final class CaptureApp extends EditorApp {
		private final Path output;
		private final String scenario;
		private SkeletonDocument doc;
		private AnimationEditor editor;
		private EditHistory history;
		private TimelinePanel panel;
		private Animation animation;
		private Actor graph;
		private Object dialog;
		private InputListener pendingDrag;
		private InputEvent pendingEvent;
		private float pendingX, pendingY;
		private long cancelledRevision;
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
				editor = new AnimationEditor(doc);
				doc.setPlaying(false); doc.data.getAnimations().clear(); doc.data.setFps(30f);
				animation = editor.addAnimation("精确子帧 · 保持窗口连续编辑");
				Animation.TranslateTimeline timeline = new Animation.TranslateTimeline(3, 0, 0);
				for (int frame = 0; frame < TIMES.length; frame++) timeline.setFrame(frame, TIMES[frame], frame * 10f, frame * 5f);
				animation.setTimelines(new Array<>(new Animation.Timeline[] {timeline}), new IntArray());
				editor.refreshAnimation(animation);
				doc.animationState = doc.newAnimationState(); doc.setAnimation(animation.getName()); doc.scrubTo(TIMES[0]);
				doc.setSelectedBone(doc.skeleton.getRootBone()); panel.rebuild();
				call(TimelinePanel.class, panel, "showGraphDetails",
					new Class<?>[] {Animation.class, Animation.Timeline.class, int.class}, animation, timeline, 0);
				graph = findGraph(panel.getStage().getRoot()); require(graph != null, "Graph did not open");
				dialog = read(graph.getClass(), graph, "this$0");
				history.clear(); history.markSaved();
			} catch (Throwable error) { stop(error); }
		}
		@Override public void render() {
			if (stopped) return;
			try {
				super.render(); frames++;
				if (frames == 8) {
					capture("before");
					if (scenario.equals("history")) initialHistoryEdit(); else verifyUnrequestedTimes();
				}
				if (frames == 12 && scenario.equals("history")) {
					require(read(graph.getClass(), graph, "timeline") == timeline(), "Undo must refresh the open Graph to the live timeline");
					call(graph.getClass(), graph, "commitKey", new Class<?>[] {float.class, float.class, String.class}, TIMES[0], 7f, "撤销后继续编辑");
					require(editor.curveFrameValue(timeline(), 0, 0) == 7f, "Open Graph cannot edit after undo");
					require(history.canUndo() && !history.canRedo(), "New Graph edit must replace redo history");
					require(history.undo(), "Undo second Graph edit");
					require(history.redo(), "Redo second Graph edit");
				}
				if (frames == 16) {
					if (scenario.equals("history")) {
						require(read(graph.getClass(), graph, "timeline") == timeline(), "Redo must refresh the existing Graph");
						require(editor.curveFrameValue((Animation.Timeline)read(graph.getClass(), graph, "timeline"), 0, 0) == 7f,
							"Graph must display redone values");
					}
					capture("after");
					if (scenario.equals("history")) {
						pendingX = (float)call(graph.getClass(), graph, "localX", new Class<?>[] {float.class}, TIMES[0]);
						pendingY = (float)call(graph.getClass(), graph, "localY", new Class<?>[] {float.class}, 7f);
						pendingEvent = new InputEvent(); pendingEvent.setStage(graph.getStage()); pendingEvent.setTarget(graph);
						for (var listener : graph.getListeners()) if (listener instanceof InputListener candidate
							&& candidate.touchDown(pendingEvent, pendingX, pendingY, 0, Input.Buttons.LEFT)) { pendingDrag = candidate; break; }
						require(pendingDrag != null, "Pending Graph drag must begin");
						pendingDrag.touchDragged(pendingEvent, pendingX + 20f, pendingY + 15f, 0);
						require(history.undo(), "Undo during Graph preview");
						cancelledRevision = doc.revision;
					} else finish();
				}
				if (frames == 20 && scenario.equals("history")) {
					require((int)read(graph.getClass(), graph, "dragMode") == 0, "External undo cancels an obsolete Graph preview");
					pendingDrag.touchUp(pendingEvent, pendingX + 20f, pendingY + 15f, 0, Input.Buttons.LEFT);
					require(doc.revision == cancelledRevision && !history.canUndo() && history.canRedo(),
						"Releasing the obsolete preview must not overwrite undo or discard redo");
					editor.editCurveKey(animation, timeline(), 2, 1, TIMES[2], 42f);
				}
				if (frames == 24 && scenario.equals("history")) {
					require(read(graph.getClass(), graph, "timeline") == timeline()
						&& editor.curveFrameValue(timeline(), 2, 1) == 42f, "Open Graph sees edits from another authoring surface");
					button((Actor)read(dialog.getClass(), dialog, "root"), "全览").fire(new ChangeEvent());
				}
				if (frames == 25 && scenario.equals("history")) {
					capture("external-edit");
					editor.removeCurveKeys(animation, timeline(), new boolean[] {true, true, true});
				}
				if (frames == 28 && scenario.equals("history")) {
					require(graph.getStage() == null, "Removing the target closes its Graph instead of editing detached author data");
					finish();
				}
			} catch (Throwable error) { stop(error); }
		}
		private void verifyUnrequestedTimes() {
			call(graph.getClass(), graph, "selectAll", NONE);
			Actor controls = (Actor)read(dialog.getClass(), dialog, "root");
			setToggle(controls, "吸附", true);
			setToggle(controls, "X", false);
			setFields("ignored locked value", "2.5"); applyFields();
			assertTimes("Locked X plus value-only field edit must preserve every original subframe");
			require(editor.curveFrameValue(timeline(), 0, 0) == 2.5f, "Value-only edit must change requested values");
			require(history.undo() && !history.canUndo() && !doc.dirty, "Value-only batch is one undo entry and restores save point");
			// Keep the live Graph target synchronized explicitly here, isolating the snapping case from the history case.
			write(graph.getClass(), graph, "timeline", timeline());
			call(graph.getClass(), graph, "selectAll", NONE);
			setToggle(controls, "X", true);
			long revision = doc.revision;
			setFields("0", "0"); applyFields();
			assertTimes("Zero offsets must not round subframes");
			require(doc.revision == revision && !history.canUndo() && history.canRedo(), "Zero offsets must preserve revision and redo");
			((SelectBox<?>)read(dialog.getClass(), dialog, "transformBox")).setSelectedIndex(2);
			call(graph.getClass(), graph, "selectAll", NONE);
			float x = (float)call(graph.getClass(), graph, "localX", new Class<?>[] {float.class}, TIMES[1]);
			float y = (float)call(graph.getClass(), graph, "localY", new Class<?>[] {float.class}, 10f);
			InputEvent event = new InputEvent(); event.setStage(graph.getStage()); event.setTarget(graph);
			InputListener input = null;
			for (var listener : graph.getListeners()) if (listener instanceof InputListener candidate
				&& candidate.touchDown(event, x, y, 0, Input.Buttons.LEFT)) { input = candidate; break; }
			require(input != null, "Graph key did not receive pointer press");
			long beforeClick = doc.revision;
			input.touchDragged(event, x, y, 0);
			input.touchUp(event, x, y, 0, Input.Buttons.LEFT);
			require(doc.revision == beforeClick && !history.canUndo() && history.canRedo(),
				"Zero-distance key drag must preserve subframes and redo");
			input.touchDown(event, x, y, 0, Input.Buttons.LEFT);
			input.touchDragged(event, x, y + 25f, 0);
			input.touchUp(event, x, y + 25f, 0, Input.Buttons.LEFT);
			assertTimes("Value-scale drag must not round subframes");
			require(editor.curveFrameValue(timeline(), 1, 0) != 10f, "Value-scale pointer drag changes the requested values");
		}
		private void initialHistoryEdit() {
			call(graph.getClass(), graph, "select", new Class<?>[] {int.class, int.class, boolean.class}, 0, 0, true);
			call(graph.getClass(), graph, "commitKey", new Class<?>[] {float.class, float.class, String.class}, TIMES[0], 3f, "首次曲线编辑");
			require(editor.curveFrameValue(timeline(), 0, 0) == 3f && history.undo(), "Initial Graph edit and undo");
		}
		private void setFields(String time, String value) {
			((TextField)read(dialog.getClass(), dialog, "timeField")).setText(time);
			((TextField)read(dialog.getClass(), dialog, "valueField")).setText(value);
		}
		private void applyFields() { ((TextButton)read(dialog.getClass(), dialog, "applyButton")).fire(new ChangeEvent()); }
		private void assertTimes(String message) {
			for (int frame = 0; frame < TIMES.length; frame++) require(Float.floatToIntBits(TIMES[frame])
				== Float.floatToIntBits(editor.frameTime(timeline(), frame)), message + ": frame " + frame
				+ " expected " + TIMES[frame] + " got " + editor.frameTime(timeline(), frame));
		}
		private Animation.Timeline timeline() { return editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0); }
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
		private void finish() {
			System.out.println("PASS Graph authoring " + scenario + ": exact subframes, live history, preview cancellation, and target lifecycle verified.");
			stop(null);
		}
	}
	public static void main(String[] args) throws Exception {
		Path output = Files.createDirectories(Path.of(args[2]).toAbsolutePath()).toRealPath();
		Path taskHome = Files.createTempDirectory(output, ".graph-home-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", taskHome.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, taskHome.resolve("autosave").toString());
		CaptureApp app = new CaptureApp(taskHome, output, args.length > 3 ? args[3] : "subframes");
		try {
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setTitle("OpenSpine Hidden Graph Authoring Verification"); config.setInitialVisible(false);
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
		if (app.failure != null) throw new AssertionError("Hidden Graph authoring verification failed", app.failure);
	}
	private static Actor findGraph(Actor actor) {
		if (actor.getClass().getName().endsWith("GraphEditorDialog$GraphCanvas")) return actor;
		if (actor instanceof Group group) for (Actor child : group.getChildren()) { Actor result = findGraph(child); if (result != null) return result; }
		return null;
	}
	private static TextButton button(Actor actor, String text) {
		if (actor instanceof TextButton button && text.contentEquals(button.getText())) return button;
		if (actor instanceof Group group) for (Actor child : group.getChildren()) {
			TextButton found = button(child, text); if (found != null) return found;
		}
		return null;
	}
	private static void setToggle(Actor actor, String text, boolean checked) {
		TextButton button = button(actor, text); require(button != null, "Missing Graph toggle " + text);
		button.setChecked(checked); button.fire(new ChangeEvent());
	}
	private static Object read(Class<?> type, Object target, String name) {
		try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static void write(Class<?> type, Object target, String name, Object value) {
		try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static Object call(Class<?> type, Object target, String name, Class<?>[] types, Object... values) {
		try { Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, values); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
