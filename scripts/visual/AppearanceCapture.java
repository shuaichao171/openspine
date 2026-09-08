package com.openspine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.openspine.ui.SettingsDialog;
import com.openspine.ui.TimelinePanel;
import com.openspine.ui.UiAppearance.Mode;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.RECT;
import org.lwjgl.system.windows.User32;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;

/** Manual visual regression capture for the appearance settings page. */
public final class AppearanceCapture {
	private static final class CaptureApp extends EditorApp {
		private final String lightPath;
		private final String darkPath;
		private final boolean showSettings;
		private final boolean captureTitleBar;
		private final boolean hoverWindowControls;
		private final boolean hoverCloseControl;
		private final boolean revealFpsControl;
		private final boolean showGraph;
		private final boolean showTimelineCurve;
		private final boolean exerciseDopesheetWheel;
		private final boolean compactDopesheet;
		private final boolean exerciseTimelineScroll;
		private final boolean hoverInputField;
		private final boolean waitForIdleStatus;
		private final boolean exerciseStatusToast;
		private int frame;

		CaptureApp(String lightPath, String darkPath, boolean showSettings, boolean captureTitleBar,
			boolean hoverWindowControls, boolean hoverCloseControl, boolean revealFpsControl,
			boolean showGraph, boolean showTimelineCurve, boolean exerciseDopesheetWheel, boolean compactDopesheet,
			boolean exerciseTimelineScroll, boolean hoverInputField, boolean waitForIdleStatus,
			boolean exerciseStatusToast) {
			this.lightPath = lightPath;
			this.darkPath = darkPath;
			this.showSettings = showSettings;
			this.captureTitleBar = captureTitleBar;
			this.hoverWindowControls = hoverWindowControls;
			this.hoverCloseControl = hoverCloseControl;
			this.revealFpsControl = revealFpsControl;
			this.showGraph = showGraph;
			this.showTimelineCurve = showTimelineCurve;
			this.exerciseDopesheetWheel = exerciseDopesheetWheel;
			this.compactDopesheet = compactDopesheet;
			this.exerciseTimelineScroll = exerciseTimelineScroll;
			this.hoverInputField = hoverInputField;
			this.waitForIdleStatus = waitForIdleStatus;
			this.exerciseStatusToast = exerciseStatusToast;
		}

		@Override public void create() {
			super.create();
			invoke(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {Mode.class}, Mode.LIGHT);
			if (showSettings) invoke(EditorApp.class, this, "showSettings", new Class<?>[0]);
			boolean showDopesheet = exerciseDopesheetWheel || compactDopesheet || exerciseTimelineScroll;
			if (showGraph || showTimelineCurve || showDopesheet) {
				TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
				com.openspine.model.SkeletonDocument document =
					(com.openspine.model.SkeletonDocument)field(EditorApp.class, this, "doc");
				com.esotericsoftware.spine.Animation preview = null;
				for (com.esotericsoftware.spine.Animation animation : document.data.getAnimations())
					if (preview == null || animation.getTimelines().size > preview.getTimelines().size) preview = animation;
				if (preview != null) document.setAnimation(preview.getName());
				timeline.rebuild();
				if (showGraph) prepareGraphCapture(timeline);
				else invoke(TimelinePanel.class, timeline, "setTimelineView",
					new Class<?>[] {boolean.class}, showTimelineCurve);
			}
		}

		private void prepareGraphCapture(TimelinePanel timeline) {
			invoke(TimelinePanel.class, timeline, "showGraphEditor", new Class<?>[0]);
			Object workspace = field(TimelinePanel.class, timeline, "graphWorkspace");
			Object overview = field(workspace.getClass(), workspace, "overview");
			invoke(overview.getClass(), overview, "openSelectedTrack", new Class<?>[0]);
			Object detailEditor = field(workspace.getClass(), workspace, "detailEditor");
			if (detailEditor == null) throw new IllegalStateException("Graph detail editor did not open");
			invoke(detailEditor.getClass(), detailEditor, "setCurveMode", new Class<?>[] {int.class}, 4);
			invoke(overview.getClass(), overview, "openSelectedTrack", new Class<?>[0]);
			detailEditor = field(workspace.getClass(), workspace, "detailEditor");
			if (detailEditor == null) throw new IllegalStateException("Graph detail editor did not reopen");
			Object graph = field(detailEditor.getClass(), detailEditor, "graph");
			invoke(graph.getClass(), graph, "frameAll", new Class<?>[0]);
		}

		@Override public void render() {
			super.render();
			frame++;
			int lightCaptureFrame = waitForIdleStatus ? 270 : 30;
			int themeSwitchFrame = waitForIdleStatus ? 276 : 36;
			int darkCaptureFrame = waitForIdleStatus ? 296 : 66;
			if (frame == 18 && revealFpsControl) verifyFpsReset();
			if (frame == 18 && exerciseDopesheetWheel) verifyDopesheetWheelZoom();
			if (frame == 18 && compactDopesheet) prepareCompactDopesheet();
			if (frame == 18 && exerciseTimelineScroll) prepareScrolledTimeline();
			if ((frame == 20 || frame == 56) && revealFpsControl) revealFpsControl();
			if ((frame == 20 || frame == 56) && exerciseStatusToast)
				invoke(EditorApp.class, this, "toggleAllSkeletons", new Class<?>[0]);
			if (frame == 20 && hoverInputField) setInputFieldHovered();
			if (frame == 20 && hoverWindowControls) setWindowControlHovered(hoverCloseControl ? 2 : 1, true);
			if (frame == lightCaptureFrame) capture(lightPath);
			if (frame == themeSwitchFrame) {
				if (hoverWindowControls) setWindowControlHovered(hoverCloseControl ? 2 : 1, false);
				invoke(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {Mode.class}, Mode.DARK);
				if (showSettings) {
					SettingsDialog dialog = (SettingsDialog)field(EditorApp.class, this, "settingsDialog");
					invoke(SettingsDialog.class, dialog, "selectPage",
						new Class<?>[] {SettingsDialog.Page.class}, SettingsDialog.Page.APPEARANCE);
				}
			}
			if (frame == 56 && hoverWindowControls) setWindowControlHovered(2, true);
			if (frame == darkCaptureFrame) {
				capture(darkPath);
				Gdx.app.exit();
			}
		}

		private void setWindowControlHovered(int index, boolean hovered) {
			Table controls = (Table)field(EditorApp.class, this, "windowControlBar");
			Actor control = controls.getChildren().get(index);
			InputEvent event = new InputEvent();
			event.setType(hovered ? InputEvent.Type.enter : InputEvent.Type.exit);
			event.setPointer(-1);
			control.fire(event);
		}

		private void setInputFieldHovered() {
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			Actor input = (Actor)field(TimelinePanel.class, timeline, "trackSearchField");
			InputEvent event = new InputEvent();
			event.setType(InputEvent.Type.enter);
			event.setPointer(-1);
			input.fire(event);
		}

		private void revealFpsControl() {
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			Actor current = (Actor)field(TimelinePanel.class, timeline, "fpsResetBtn");
			while (current != null && !(current instanceof ScrollPane)) current = current.getParent();
			if (!(current instanceof ScrollPane toolbar)) throw new IllegalStateException("FPS toolbar scroll not found");
			toolbar.layout();
			toolbar.setScrollPercentX(1f);
			toolbar.updateVisualScroll();
		}

		private void verifyFpsReset() {
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			com.openspine.model.SkeletonDocument document =
				(com.openspine.model.SkeletonDocument)field(TimelinePanel.class, timeline, "doc");
			com.badlogic.gdx.scenes.scene2d.ui.TextField fpsField =
				(com.badlogic.gdx.scenes.scene2d.ui.TextField)field(TimelinePanel.class, timeline, "fpsField");
			Actor reset = (Actor)field(TimelinePanel.class, timeline, "fpsResetBtn");
			if (!document.setProjectFps(60f)) throw new IllegalStateException("Unable to prepare FPS reset check");
			fpsField.setText("60");
			reset.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
			if (Float.compare(document.data.getFps(), TimelinePanel.DEFAULT_PROJECT_FPS) != 0
				|| !"30".equals(fpsField.getText()))
				throw new IllegalStateException("FPS reset button did not restore the default");
		}

		private void verifyDopesheetWheelZoom() {
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			timeline.restoreViewSettings(TimelinePanel.DEFAULT_ZOOM, timeline.getTrackFilterKey());
			ScrollPane timelineScroll = (ScrollPane)field(TimelinePanel.class, timeline, "scroll");
			timelineScroll.layout();
			float initialFrameZoom = (Float)field(TimelinePanel.class, timeline, "timelineZoom");
			float initialVerticalZoom = (Float)field(TimelinePanel.class, timeline, "timelineVerticalZoom");
			float x = Math.max(300f, timelineScroll.getWidth() * 0.55f);
			float y = timelineScroll.getHeight() - 10f;
			for (int index = 0; index < 4; index++)
				invoke(TimelinePanel.class, timeline, "handleTimelineWheel",
					new Class<?>[] {float.class, float.class, float.class, float.class}, x, y, 0f, -1f);
			float frameZoom = (Float)field(TimelinePanel.class, timeline, "timelineZoom");
			float verticalZoom = (Float)field(TimelinePanel.class, timeline, "timelineVerticalZoom");
			if (frameZoom <= initialFrameZoom || Float.compare(verticalZoom, initialVerticalZoom) != 0)
				throw new IllegalStateException("Dopesheet ruler wheel changed more than frame spacing");
		}

		private void prepareCompactDopesheet() {
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			timeline.restoreViewSettings(30f, timeline.getTrackFilterKey());
			float frameZoom = (Float)field(TimelinePanel.class, timeline, "timelineZoom");
			float verticalZoom = (Float)field(TimelinePanel.class, timeline, "timelineVerticalZoom");
			if (Float.compare(frameZoom, 30f) != 0 || Float.compare(verticalZoom, 30f) != 0)
				throw new IllegalStateException("Compact dopesheet zoom did not update both axes");
		}

		private void prepareScrolledTimeline() {
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			ScrollPane timelineScroll = (ScrollPane)field(TimelinePanel.class, timeline, "scroll");
			timelineScroll.layout();
			timelineScroll.setScrollY(Math.min(timelineScroll.getMaxY(), 420f));
			timelineScroll.updateVisualScroll();
			if (!(timelineScroll.getVisualScrollY() > 0f))
				throw new IllegalStateException("Timeline did not scroll vertically for sticky ruler capture");
		}

		private void capture(String path) {
			if (captureTitleBar) {
				captureWindowTop(path);
				return;
			}
			int width = Gdx.graphics.getBackBufferWidth();
			int height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try {
				BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(path), pixmap);
			} finally {
				pixmap.dispose();
			}
		}

		private void captureWindowTop(String path) {
			long handle = ((com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics)Gdx.graphics)
				.getWindow().getWindowHandle();
			try (MemoryStack stack = MemoryStack.stackPush()) {
				RECT windowBounds = RECT.calloc(stack);
				long nativeWindow = GLFWNativeWin32.glfwGetWin32Window(handle);
				if (!User32.GetWindowRect(nativeWindow, windowBounds))
					throw new IllegalStateException("Unable to read the window bounds");
				Rectangle bounds = new Rectangle(windowBounds.left(), windowBounds.top(),
					windowBounds.right() - windowBounds.left(),
					Math.min(64, windowBounds.bottom() - windowBounds.top()));
				BufferedImage image = new Robot().createScreenCapture(bounds);
				ImageIO.write(image, "png", new File(path));
			} catch (Exception error) {
				throw new IllegalStateException("Unable to capture the window top", error);
			}
		}
	}

	public static void main(String[] args) {
		if (args.length < 2 || args.length > 3)
			throw new IllegalArgumentException(
				"Expected light and dark screenshot paths, plus an optional capture mode.");
		boolean hoverCloseControl = args.length == 3 && "--titlebar-close-hover".equals(args[2]);
		boolean hoverWindowControls = args.length == 3
			&& ("--titlebar-hover".equals(args[2]) || hoverCloseControl);
		boolean revealFpsControl = args.length == 3 && "--workspace-fps".equals(args[2]);
		boolean showGraph = args.length == 3 && "--graph".equals(args[2]);
		boolean showTimelineCurve = args.length == 3 && "--timeline-curve".equals(args[2]);
		boolean exerciseDopesheetWheel = args.length == 3 && "--dopesheet-wheel".equals(args[2]);
		boolean compactDopesheet = args.length == 3 && "--dopesheet-compact".equals(args[2]);
		boolean exerciseTimelineScroll = args.length == 3 && "--timeline-scroll".equals(args[2]);
		boolean hoverInputField = args.length == 3 && "--input-hover".equals(args[2]);
		boolean waitForIdleStatus = args.length == 3 && "--workspace-idle".equals(args[2]);
		boolean exerciseStatusToast = args.length == 3 && "--status-toast".equals(args[2]);
		boolean captureTitleBar = args.length == 3
			&& ("--titlebar".equals(args[2]) || hoverWindowControls);
		boolean showSettings = args.length < 3
			|| (!"--workspace".equals(args[2]) && !waitForIdleStatus && !revealFpsControl && !showGraph
				&& !showTimelineCurve
				&& !exerciseDopesheetWheel && !compactDopesheet && !exerciseTimelineScroll
				&& !hoverInputField && !exerciseStatusToast && !captureTitleBar);
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("OpenSpine Appearance Visual Check");
		config.setWindowedMode(1280, 760);
		config.setResizable(false);
		config.setDecorated(!WindowsTitleBar.usesCustomFrame());
		config.setForegroundFPS(60);
		CaptureApp app = new CaptureApp(args[0], args[1], showSettings, captureTitleBar,
			 hoverWindowControls, hoverCloseControl, revealFpsControl, showGraph, showTimelineCurve,
			 exerciseDopesheetWheel, compactDopesheet, exerciseTimelineScroll, hoverInputField,
			waitForIdleStatus, exerciseStatusToast);
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

	private AppearanceCapture() {}
}
