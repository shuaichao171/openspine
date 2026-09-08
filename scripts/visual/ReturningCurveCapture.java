package com.openspine.ui;

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
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
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

/** Exercises real Scene2D curve listeners in a hidden, isolated editor and exports its framebuffer. */
public final class ReturningCurveCapture {
	private static final Class<?>[] NONE = new Class<?>[0];
	private static final Class<?>[] SEGMENT = {int.class, int.class};
	private static final class CaptureApp extends EditorApp {
		private final Path output;
		private SkeletonDocument doc;
		private AnimationEditor editor;
		private EditHistory history;
		private TimelinePanel panel;
		private Animation animation;
		private Actor inline, graph;
		private InputListener dragListener;
		private Actor dragActor;
		private float[] dragPoint;
		private int[][] baseline, edited;
		private int frames;
		private boolean stopped;
		private Throwable failure;
		CaptureApp(Path home, Path output) { super(null, home); this.output = output; }
		@Override public void create() {
			try {
				super.create();
				call(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				doc = (SkeletonDocument)read(EditorApp.class, this, "doc");
				editor = new AnimationEditor(doc);
				history = (EditHistory)read(EditorApp.class, this, "history");
				panel = (TimelinePanel)read(EditorApp.class, this, "timelinePanel");
				doc.setPlaying(false);
				doc.data.getAnimations().clear();
				animation = editor.addAnimation("returning-X-independent-Y");
				Animation.TranslateTimeline curve = new Animation.TranslateTimeline(2, 2, 0);
				curve.setFrame(0, 0f, 4f, 3f);
				curve.setFrame(1, 1f, 4f, 20f);
				curve.setBezier(0, 0, 0, 0f, 4f, 0.2f, 40f, 0.7f, -8f, 1f, 4f);
				curve.setBezier(1, 0, 1, 0f, 3f, 0.3f, 3f, 0.8f, 20f, 1f, 20f);
				animation.setTimelines(new Array<>(new Animation.Timeline[] {curve}), new IntArray());
				doc.animationState = doc.newAnimationState();
				doc.setAnimation(animation.getName()); doc.scrubTo(0f);
				doc.setSelectedBone(doc.skeleton.getRootBone());
				panel.rebuild();
				inline = (Actor)read(TimelinePanel.class, panel, "canvas");
				((SplitPane)read(EditorApp.class, this, "workspace")).setSplitAmount(0.32f);
				selectInline();
				call(TimelinePanel.class, panel, "showGraphEditor", NONE);
				baseline = samples();
				require(baseline[0][35] != Float.floatToIntBits(4f), "Fixture X must leave and return to its endpoint value");
				history.clear(); history.markSaved();
			} catch (Throwable error) { stop(error); }
		}
		@Override public void render() {
			if (stopped) return;
			try {
				super.render();
				frames++;
				if (frames == 8) {
					selectInline();
					verifyInlineControls();
				}
				if (frames == 12) {
					capture("inline-before");
					float[] point = (float[])call(inline.getClass(), inline, "curveHandlePoint",
						new Class<?>[] {Animation.Timeline.class, int.class, int.class, boolean.class}, timeline(), 0, 1, true);
					beginDrag(inline, point, "curveDragHandle", 2);
				}
				if (frames == 15) { capture("inline-preview"); finishDrag(); verifyHistory("Inline Graph"); }
				if (frames == 19) {
					capture("inline-redone");
					require(history.undo(), "Restore fixture before standalone Graph");
					history.clear(); history.markSaved();
					selectInline();
					call(TimelinePanel.class, panel, "showGraphDetails",
						new Class<?>[] {Animation.class, Animation.Timeline.class, int.class}, animation, timeline(), 0);
				}
				if (frames == 23) {
					graph = findGraph(panel.getStage().getRoot());
					require(graph != null, "Standalone Graph did not open");
					call(graph.getClass(), graph, "select", new Class<?>[] {int.class, int.class, boolean.class}, 0, 0, true);
					call(graph.getClass(), graph, "frameAll", NONE);
					float[] handles = (float[])call(graph.getClass(), graph, "handlesForSegment", SEGMENT, 0, 0);
					near(40f, handles[1], "Standalone outgoing X handle retains absolute value");
					near(-8f, handles[3], "Standalone incoming X handle retains absolute value");
					require((float)read(graph.getClass(), graph, "valueMin") < -8f
						&& (float)read(graph.getClass(), graph, "valueMax") > 40f, "Frame all includes both returning handles");
				}
				if (frames == 27) {
					capture("standalone-before");
					float[] point = (float[])call(graph.getClass(), graph, "handlePoint",
						new Class<?>[] {int.class, int.class, boolean.class}, 0, 1, true);
					beginDrag(graph, point, "dragMode", 4);
				}
				if (frames == 30) { capture("standalone-preview"); finishDrag(); verifyHistory("Standalone Graph"); }
				if (frames == 34) {
					capture("standalone-redone");
					System.out.println("PASS: inline and standalone returning X curve render; real listeners drag the X handle; Y samples stay bit-exact; both edits undo and redo exactly.");
					stop(null);
				}
			} catch (Throwable error) { stop(error); }
		}
		private Animation.Timeline timeline() { return editor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE, 0); }
		private void selectInline() {
			Object track = call(inline.getClass(), inline, "trackForTimeline", new Class<?>[] {Animation.Timeline.class}, timeline());
			require(track != null, "Translate track did not resolve");
			call(inline.getClass(), inline, "selectGraphKeyframe", new Class<?>[] {track.getClass(), int.class, boolean.class}, track, 0, false);
		}
		private void verifyInlineControls() {
			float[] handles = (float[])call(inline.getClass(), inline, "curveHandles",
				new Class<?>[] {Animation.Timeline.class, int.class, int.class}, timeline(), 0, 0);
			require(handles != null, "Inline returning curve was flattened");
			near(40f, handles[1], "Inline outgoing X handle retains absolute value");
			near(-8f, handles[3], "Inline incoming X handle retains absolute value");
		}
		private void beginDrag(Actor actor, float[] point, String flag, int mode) {
			require(point != null && Float.isFinite(point[0]) && Float.isFinite(point[1]), "Missing displayed curve handle");
			dragActor = actor;
			InputEvent event = event(actor);
			for (boolean zeroMove : new boolean[] {false, true}) {
				long revision = doc.revision;
				InputListener click = startHandle(actor, point, event);
				if (zeroMove) click.touchDragged(event, point[0], point[1], 0);
				click.touchUp(event, point[0], point[1], 0, Input.Buttons.LEFT);
				require(doc.revision == revision && !history.canUndo() && !history.canRedo()
					&& Arrays.deepEquals(baseline, samples()), "Click or zero-distance drag must not rewrite the curve");
			}
			dragListener = startHandle(actor, point, event);
			require(dragListener != null && (int)read(actor.getClass(), actor, flag) == mode, "Displayed X handle did not start its drag");
			dragPoint = new float[] {point[0] + 18f, point[1] - 28f};
			dragListener.touchDragged(event, dragPoint[0], dragPoint[1], 0);
			require(Arrays.deepEquals(baseline, samples()), "Handle preview should not publish author data before release");
		}
		private InputListener startHandle(Actor actor, float[] point, InputEvent event) {
			for (var listener : actor.getListeners()) if (listener instanceof InputListener input
				&& input.touchDown(event, point[0], point[1], 0, Input.Buttons.LEFT)) return input;
			throw new AssertionError("Graph has no handle input listener");
		}
		private void finishDrag() {
			dragListener.touchUp(event(dragActor), dragPoint[0], dragPoint[1], 0, Input.Buttons.LEFT);
			dragListener = null;
		}
		private static InputEvent event(Actor actor) {
			InputEvent event = new InputEvent(); event.setStage(actor.getStage()); event.setTarget(actor); return event;
		}
		private void verifyHistory(String label) {
			edited = samples();
			require(!Arrays.equals(baseline[0], edited[0]), label + " did not change X");
			require(Arrays.equals(baseline[1], edited[1]), label + " changed unselected Y samples");
			require(history.undo() && !history.canUndo(), label + " must have one undo entry");
			require(Arrays.deepEquals(baseline, samples()), label + " undo failed to restore exact curves");
			require(history.redo(), label + " cannot redo");
			require(Arrays.deepEquals(edited, samples()), label + " redo failed to restore exact curves");
		}
		private int[][] samples() {
			Animation.Timeline timeline = timeline();
			Animation.CurveTimeline curve = (Animation.CurveTimeline)timeline;
			int[][] result = new int[2][99];
			for (int channel = 0; channel < 2; channel++) for (int sample = 1; sample < 100; sample++) {
				float value = curve.getBezierValue(sample / 100f, 0, channel + 1,
					curve.getCurveType(0) - Animation.CurveTimeline.BEZIER + channel * Animation.CurveTimeline.BEZIER_SIZE);
				require(Float.isFinite(value), "Graph sample must remain finite");
				result[channel][sample - 1] = Float.floatToIntBits(value);
			}
			return result;
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
		Path home = Files.createTempDirectory(output, ".curve-home-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		CaptureApp app = new CaptureApp(home, output);
		try {
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setTitle("OpenSpine Hidden Returning Curve Verification"); config.setInitialVisible(false);
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
		if (app.failure != null) throw new AssertionError("Hidden returning curve verification failed", app.failure);
	}
	private static Actor findGraph(Actor actor) {
		if (actor.getClass().getName().endsWith("GraphEditorDialog$GraphCanvas")) return actor;
		if (actor instanceof Group group) for (Actor child : group.getChildren()) {
			Actor result = findGraph(child); if (result != null) return result;
		}
		return null;
	}
	private static Object read(Class<?> type, Object target, String name) {
		try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static Object call(Class<?> type, Object target, String name, Class<?>[] types, Object... values) {
		try { Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, values); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static void near(float expected, float actual, String message) { require(Math.abs(expected - actual) < 0.002f, message + ": " + actual); }
	private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
