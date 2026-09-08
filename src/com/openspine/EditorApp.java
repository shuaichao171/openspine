package com.openspine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor.SystemCursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.Attachment;
import com.openspine.audio.AudioEventPlayer;
import com.openspine.KeyBindings.EditorAction;
import com.openspine.io.ApngAnimationExporter;
import com.openspine.io.AviAnimationExporter;
import com.openspine.io.AtlasPacker;
import com.openspine.io.AtlasAlphaModes;
import com.openspine.io.AutoSaveStore;
import com.openspine.io.CancellationToken;
import com.openspine.io.ExampleSkeleton;
import com.openspine.io.ExportPresetStore;
import com.openspine.io.FfmpegAnimationExporter;
import com.openspine.io.GifAnimationExporter;
import com.openspine.io.HtmlExporter;
import com.openspine.io.ImageReload;
import com.openspine.io.IncrementalAnimationRenderer;
import com.openspine.io.JpegAnimationExporter;
import com.openspine.io.JpegFrameExporter;
import com.openspine.io.PngAnimationExporter;
import com.openspine.io.PngFrameExporter;
import com.openspine.io.PsdFrameExporter;
import com.openspine.io.PsdImporter;
import com.openspine.io.PsdLayerInstaller;
import com.openspine.io.ProjectPackager;
import com.openspine.io.ProjectPackageManifest;
import com.openspine.io.NativeProjectStore;
import com.openspine.io.ProjectDiagnostics;
import com.openspine.io.ProjectStateStore;
import com.openspine.io.SkeletonBinaryExporter;
import com.openspine.io.SkeletonExporter;
import com.openspine.io.SkeletonLoader;
import com.openspine.io.SpineProjectExporter;
import com.openspine.io.SpineProjectImporter;
import com.openspine.io.SpineVersionPolicy;
import com.openspine.io.WebpFrameExporter;
import com.openspine.model.AnimationEditor;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonProject;
import com.openspine.ui.AttachmentPanel;
import com.openspine.ui.BoneTreePanel;
import com.openspine.ui.I18n;
import com.openspine.ui.I18n.Language;
import com.openspine.ui.PlaybackPanel;
import com.openspine.ui.PopupMenu;
import com.openspine.ui.PropertiesPanel;
import com.openspine.ui.SettingsDialog;
import com.openspine.ui.TimelinePanel;
import com.openspine.ui.UiAppearance;
import com.openspine.ui.UiAppearance.Mode;
import com.openspine.ui.UiAppearance.Palette;
import com.openspine.ui.UiAppearance.Theme;
import com.openspine.ui.UiColors;
import com.openspine.ui.UiDialogs;
import com.openspine.ui.UiKit;
import com.openspine.ui.UiNumbers;
import com.openspine.view.EditorAppState;
import com.openspine.view.EditorTool;
import com.openspine.view.EditorViewport;

/** Main desktop editor shell and document workflow coordinator. */
public class EditorApp extends ApplicationAdapter {
	private static final String PREFERENCES_NAME = "OpenSpine.workspace";
	private static final String PREF_VERSION = "version";
	private static final int PREFERENCES_VERSION = 1;
	private static final String PREF_LANGUAGE = "interface.language";
	private static final String PREF_APPEARANCE_MODE = "appearance.mode";
	private static final String PREF_LIGHT_THEME = "appearance.lightTheme";
	private static final String PREF_DARK_THEME = "appearance.darkTheme";
	private static final String PREF_WORKSPACE_SPLIT = "workspace.split";
	private static final String PREF_NAVIGATION_SPLIT = "workspace.navigation.split";
	private static final String PREF_INSPECTOR_SPLIT = "workspace.inspector.split";
	private static final String PREF_HIERARCHY_VISIBLE = "workspace.hierarchy.visible";
	private static final String PREF_INSPECTOR_VISIBLE = "workspace.inspector.visible";
	private static final String PREF_ANIMATION_VISIBLE = "workspace.animation.visible";
	private static final String PREF_TIMELINE_ZOOM = "timeline.zoom";
	private static final String PREF_TRACK_FILTER = "timeline.filter";
	private static final String PREF_INSPECTOR_ATTACHMENTS = "inspector.attachments";
	private static final String PREF_GRID_VISIBLE = "viewport.grid";
	private static final String PREF_PIXEL_GRID_VISIBLE = "viewport.pixelGrid";
	private static final String PREF_BONES_VISIBLE = "viewport.bones";
	private static final String PREF_SELECTION_HIDDEN = "viewport.selectionHidden";
	private static final String PREF_INACTIVE_BONES = "viewport.inactiveSkinBones";
	private static final String PREF_GHOSTING_VISIBLE = "viewport.ghosting";
	private static final String PREF_GHOST_BONES_VISIBLE = "viewport.ghosting.bones";
	private static final String PREF_TEXTURE_SMOOTHING = "viewport.textureSmoothing";
	private static final String PREF_VIEWPORT_FLIPPED = "viewport.flipped";
	private static final String PREF_ZOOM_LOCKED = "viewport.zoomLocked";
	private static final String PREF_PIXEL_SNAPPING = "viewport.pixelSnapping";
	private static final String PREF_EVENT_AUDIO_ENABLED = "audio.events.enabled";
	private static final String PREF_PNG_SCALE = "export.png.scale";
	private static final String PREF_PNG_PADDING = "export.png.padding";
	private static final String PREF_PNG_COMPRESSION = "export.png.compression";
	private static final String PREF_PNG_TRANSPARENT = "export.png.transparent";
	private static final String PREF_PNG_PREMULTIPLIED = "export.png.premultipliedAlpha";
	private static final String PREF_JPEG_SCALE = "export.jpeg.scale";
	private static final String PREF_JPEG_PADDING = "export.jpeg.padding";
	private static final String PREF_JPEG_QUALITY = "export.jpeg.quality";
	private static final String PREF_PNG_SEQUENCE_FPS = "export.pngSequence.fps";
	private static final String PREF_PNG_SEQUENCE_WARM_UP = "export.pngSequence.warmUp";
	private static final String PREF_PNG_SEQUENCE_SCALE = "export.pngSequence.scale";
	private static final String PREF_PNG_SEQUENCE_PADDING = "export.pngSequence.padding";
	private static final String PREF_PNG_SEQUENCE_COMPRESSION = "export.pngSequence.compression";
	private static final String PREF_PNG_SEQUENCE_TRANSPARENT = "export.pngSequence.transparent";
	private static final String PREF_PNG_SEQUENCE_PREMULTIPLIED = "export.pngSequence.premultipliedAlpha";
	private static final String PREF_PNG_SEQUENCE_INCLUDE_LAST_FRAME = "export.pngSequence.includeLastFrame";
	private static final String PREF_JPEG_SEQUENCE_FPS = "export.jpegSequence.fps";
	private static final String PREF_JPEG_SEQUENCE_WARM_UP = "export.jpegSequence.warmUp";
	private static final String PREF_JPEG_SEQUENCE_SCALE = "export.jpegSequence.scale";
	private static final String PREF_JPEG_SEQUENCE_PADDING = "export.jpegSequence.padding";
	private static final String PREF_JPEG_SEQUENCE_QUALITY = "export.jpegSequence.quality";
	private static final String PREF_JPEG_SEQUENCE_INCLUDE_LAST_FRAME = "export.jpegSequence.includeLastFrame";
	private static final String PREF_GIF_FPS = "export.gif.fps";
	private static final String PREF_GIF_WARM_UP = "export.gif.warmUp";
	private static final String PREF_GIF_SCALE = "export.gif.scale";
	private static final String PREF_GIF_PADDING = "export.gif.padding";
	private static final String PREF_GIF_TRANSPARENT = "export.gif.transparent";
	private static final String PREF_GIF_REPEAT = "export.gif.repeat";
	private static final String PREF_GIF_ALPHA_THRESHOLD = "export.gif.alphaThreshold";
	private static final String PREF_GIF_INCLUDE_LAST_FRAME = "export.gif.includeLastFrame";
	private static final String PREF_APNG_FPS = "export.apng.fps";
	private static final String PREF_APNG_WARM_UP = "export.apng.warmUp";
	private static final String PREF_APNG_SCALE = "export.apng.scale";
	private static final String PREF_APNG_PADDING = "export.apng.padding";
	private static final String PREF_APNG_TRANSPARENT = "export.apng.transparent";
	private static final String PREF_APNG_REPEAT = "export.apng.repeat";
	private static final String PREF_APNG_COMPRESSION = "export.apng.compression";
	private static final String PREF_APNG_INCLUDE_LAST_FRAME = "export.apng.includeLastFrame";
	private static final String PREF_AVI_FPS = "export.avi.fps";
	private static final String PREF_AVI_WARM_UP = "export.avi.warmUp";
	private static final String PREF_AVI_SCALE = "export.avi.scale";
	private static final String PREF_AVI_PADDING = "export.avi.padding";
	private static final String PREF_AVI_QUALITY = "export.avi.quality";
	private static final String PREF_AVI_INCLUDE_LAST_FRAME = "export.avi.includeLastFrame";
	private static final String PREF_VIDEO_FPS = "export.video.fps";
	private static final String PREF_VIDEO_WARM_UP = "export.video.warmUp";
	private static final String PREF_VIDEO_SCALE = "export.video.scale";
	private static final String PREF_VIDEO_PADDING = "export.video.padding";
	private static final String PREF_VIDEO_TRANSPARENT = "export.video.transparent";
	private static final String PREF_VIDEO_BACKGROUND = "export.video.background";
	private static final String PREF_VIDEO_PROFILE = "export.video.profile";
	private static final String PREF_VIDEO_RESOLUTION = "export.video.resolution";
	private static final String PREF_VIDEO_AUDIO = "export.video.audio";
	private static final String PREF_VIDEO_INCLUDE_LAST_FRAME = "export.video.includeLastFrame";
	private static final String PREF_EXPORT_PRESETS = "export.presets";
	private static final String PREF_FFMPEG_PATH = "export.ffmpeg.path";
	private static final String PREF_ATLAS_PAGE_WIDTH = "atlas.pageWidth";
	private static final String PREF_ATLAS_PAGE_HEIGHT = "atlas.pageHeight";
	private static final String PREF_ATLAS_PADDING = "atlas.padding";
	private static final String PREF_ATLAS_DUPLICATE_BORDER = "atlas.duplicateBorder";
	private static final String PREF_ATLAS_MIN_FILTER = "atlas.minFilter";
	private static final String PREF_ATLAS_MAG_FILTER = "atlas.magFilter";
	private static final String PREF_ATLAS_REPEAT = "atlas.repeat";
	private static final String PREF_ATLAS_STRIP_WHITESPACE = "atlas.stripWhitespace";
	private static final String PREF_ATLAS_SCALE = "atlas.scale";
	private static final String PREF_ATLAS_PREMULTIPLIED_ALPHA = "atlas.premultipliedAlpha";
	private static final String PREF_ATLAS_ALIAS = "atlas.alias";
	private static final String PREF_PSD_IGNORE_HIDDEN = "psd.ignoreHiddenLayers";
	private static final List<String> EXPORT_PREFERENCE_KEYS = List.of(
		PREF_PNG_SCALE, PREF_PNG_PADDING, PREF_PNG_COMPRESSION, PREF_PNG_TRANSPARENT,
		PREF_PNG_PREMULTIPLIED, PREF_JPEG_SCALE, PREF_JPEG_PADDING, PREF_JPEG_QUALITY,
		PREF_PNG_SEQUENCE_FPS, PREF_PNG_SEQUENCE_WARM_UP, PREF_PNG_SEQUENCE_SCALE,
		PREF_PNG_SEQUENCE_PADDING, PREF_PNG_SEQUENCE_COMPRESSION, PREF_PNG_SEQUENCE_TRANSPARENT,
		PREF_PNG_SEQUENCE_PREMULTIPLIED, PREF_PNG_SEQUENCE_INCLUDE_LAST_FRAME,
		PREF_JPEG_SEQUENCE_FPS, PREF_JPEG_SEQUENCE_WARM_UP, PREF_JPEG_SEQUENCE_SCALE,
		PREF_JPEG_SEQUENCE_PADDING, PREF_JPEG_SEQUENCE_QUALITY, PREF_JPEG_SEQUENCE_INCLUDE_LAST_FRAME,
		PREF_GIF_FPS, PREF_GIF_WARM_UP, PREF_GIF_SCALE, PREF_GIF_PADDING, PREF_GIF_TRANSPARENT,
		PREF_GIF_REPEAT, PREF_GIF_ALPHA_THRESHOLD, PREF_GIF_INCLUDE_LAST_FRAME,
		PREF_APNG_FPS, PREF_APNG_WARM_UP, PREF_APNG_SCALE, PREF_APNG_PADDING, PREF_APNG_TRANSPARENT,
		PREF_APNG_REPEAT, PREF_APNG_COMPRESSION, PREF_APNG_INCLUDE_LAST_FRAME,
		PREF_AVI_FPS, PREF_AVI_WARM_UP, PREF_AVI_SCALE, PREF_AVI_PADDING, PREF_AVI_QUALITY,
		PREF_AVI_INCLUDE_LAST_FRAME, PREF_VIDEO_FPS, PREF_VIDEO_WARM_UP, PREF_VIDEO_SCALE,
		PREF_VIDEO_PADDING, PREF_VIDEO_TRANSPARENT, PREF_VIDEO_BACKGROUND, PREF_VIDEO_PROFILE,
		PREF_VIDEO_RESOLUTION, PREF_VIDEO_AUDIO, PREF_VIDEO_INCLUDE_LAST_FRAME);
	private static final String PREF_LAST_OPEN_DIRECTORY = "files.lastOpenDirectory";
	private static final String PREF_LAST_SAVE_DIRECTORY = "files.lastSaveDirectory";
	private static final String PREF_RECENT_PROJECTS = "files.recentProjects";
	private static final int MAX_RECENT_PROJECTS = 8;
	private static final long AUTOSAVE_INTERVAL_MILLIS = 60_000L;
	static final float DEFAULT_WORKSPACE_SPLIT = 0.66f;
	static final float MIN_WORKSPACE_SPLIT = 0.30f;
	static final float MAX_WORKSPACE_SPLIT = 0.90f;
	static final float DEFAULT_NAVIGATION_SPLIT = 0.165f;
	static final float MIN_NAVIGATION_SPLIT = 0.08f;
	static final float MAX_NAVIGATION_SPLIT = 0.45f;
	static final float DEFAULT_INSPECTOR_SPLIT = 0.76f;
	static final float MIN_INSPECTOR_SPLIT = 0.45f;
	static final float MAX_INSPECTOR_SPLIT = 0.92f;
	static final float IMPORT_DIALOG_CONTENT_WIDTH = 400f;
	static final float IMPORT_PROGRESS_HEIGHT = 6f;
	static final float PROJECT_MENU_DISMISS_DELAY_SECONDS = 0.12f;
	static final float TOOLBAR_TOOL_GROUP_GAP = 52f;
	static final float CUSTOM_TITLE_BAR_HEIGHT = 30f;
	static final float WINDOW_CONTROL_WIDTH = 46f;
	static final float TITLE_BAR_DRAG_THRESHOLD = 3f;
	static final long TITLE_BAR_DOUBLE_CLICK_MILLIS = 420L;
	static final boolean DEFAULT_SHOW_ALL_SKELETONS = false;
	static final float WORKSPACE_TOOLBAR_HEIGHT = 38f;
	static final float STATUS_TOAST_MAX_WIDTH = 560f;
	static final float STATUS_TOAST_SIDE_MARGIN = 16f;
	static final float STATUS_TOAST_WIDTH_SCALE = 1.2f;
	static final float STATUS_TOAST_HEIGHT_SCALE = 1.15f;
	static final long STATUS_TOAST_DURATION_MILLIS = 4000L;

	private final SkeletonDocument doc = new SkeletonDocument();
	private final SkeletonProject skeletonProject = new SkeletonProject(doc);
	private final List<EditHistory> documentHistories = new ArrayList<>();
	private EditHistory history = new EditHistory(doc);
	private final KeyBindings keyBindings = new KeyBindings();
	private final Runnable languageListener = this::refreshInterfaceLanguage;
	private final String initialPath;
	private final Path mcpCaptureRoot;
	private com.openspine.mcp.EditorCaptureBridge mcpCaptureBridge;
	private final EnumMap<EditorTool, TextButton> toolButtons = new EnumMap<>(EditorTool.class);
	private final EnumMap<WorkspacePanel, TextButton> workspaceRestoreButtons =
		new EnumMap<>(WorkspacePanel.class);
	private final EnumSet<WorkspacePanel> collapsedWorkspacePanels = EnumSet.noneOf(WorkspacePanel.class);

	private Stage stage;
	private UiKit kit;
	private Mode appearanceMode = Mode.SYSTEM;
	private Theme lightTheme = Theme.JADE;
	private Theme darkTheme = Theme.JADE;
	private boolean systemDark = true;
	private long nativeWindowHandle;
	private Texture titleBarIconTexture;
	private SkeletonEditor editor;
	private Path nativeProjectPath;
	private NativeProjectStore.FileStamp nativeProjectStamp;
	private static final String RECOVERED_NATIVE_PROJECT = "new:native-project-recovery";
	private EditorViewport viewport;
	private BoneTreePanel treePanel;
	private PropertiesPanel propsPanel;
	private AttachmentPanel attachPanel;
	private PlaybackPanel playbackPanel;
	private TimelinePanel timelinePanel;
	private AudioEventPlayer audioEventPlayer;
	private Preferences preferences;
	private SplitPane workspace;
	private SplitPane navigationSplit;
	private SplitPane inspectorSplit;
	private Container<Actor> workspaceHost;
	private Actor hierarchyPanelHost;
	private Actor viewportPanelHost;
	private Actor inspectorPanelHost;
	private Actor animationPanelHost;
	private float workspaceSplitAmount = DEFAULT_WORKSPACE_SPLIT;
	private float navigationSplitAmount = DEFAULT_NAVIGATION_SPLIT;
	private float inspectorSplitAmount = DEFAULT_INSPECTOR_SPLIT;
	private boolean hierarchyVisible = true;
	private boolean inspectorVisible = true;
	private boolean animationVisible = true;
	private boolean workspaceCollapsed;
	private SystemCursor currentWorkspaceCursor = SystemCursor.Arrow;
	private TextButton projectMenuBtn, openBtn, saveBtn, saveAsBtn, poseBtn, undoBtn, redoBtn,
		propertiesTabBtn, attachmentsTabBtn, allSkeletonsBtn, collapseWorkspaceBtn, interfaceMenuBtn;
	private Button deleteSkeletonBtn;
	private Table workspaceRestoreBar;
	private Label skeletonHeaderLabel;
	private Label titleBarLabel;
	private Table windowControlBar;
	private SelectBox<String> skeletonSelect;
	private boolean refreshingSkeletonSelect;
	private boolean showAllSkeletons = DEFAULT_SHOW_ALL_SKELETONS;
	private PopupMenu projectMenu;
	private PopupMenu interfaceMenu;
	private Timer.Task projectMenuDismissTask;
	private boolean projectMenuButtonHovered;
	private boolean projectMenuHovered;
	private Table inspectorHost;
	private boolean attachmentsInspectorSelected;
	private boolean restoreSavedWorkspace;
	private Table statusToast;
	private Label statusLabel;
	private Table viewportStatus;
	private Label viewportStatusLabel;
	private boolean statusError;
	private String transientStatus;
	private long transientStatusUntil;
	private boolean exitConfirmed;
	private boolean exitPromptOpen;
	private boolean fileDialogOpen;
	private boolean spineImportInProgress;
	private long spineImportGeneration;
	private Thread spineImportThread;
	private long animationImportGeneration;
	private Thread animationImportThread;
	private boolean animationImportInProgress;
	private Window spineImportProgress;
	private Actor spineImportPreviousFocus;
	private long documentIoGeneration;
	private Thread documentIoThread;
	private CancellationToken documentIoCancellation;
	private volatile PsdImporter.ImportResult pendingPsdImport;
	private Window documentIoProgress;
	private Actor documentIoPreviousFocus;
	private Label documentIoProgressStatus;
	private TextButton documentIoCancelButton;
	private IncrementalAnimationRenderer animationRenderer;
	private CancellationToken animationRenderCancellation;
	private Consumer<IncrementalAnimationRenderer> animationRenderCompletion;
	private Window animationRenderProgress;
	private Actor animationRenderPreviousFocus;
	private Label animationRenderProgressStatus;
	private DeterminateProgressIndicator animationRenderProgressBar;
	private TextButton animationRenderCancelButton;
	private boolean disposed;
	private boolean titleInitialized;
	private boolean lastTitleDirty;
	private String lastTitleDocument;
	private boolean spineRebuildWarningAcknowledged;
	private boolean suppressHistoryRefresh;
	private boolean selectingFromHierarchyTree;
	private String lastOpenDirectory;
	private String lastSaveDirectory;
	private final List<String> recentProjects = new ArrayList<>();
	private SettingsDialog settingsDialog;
	private Thread autosaveThread;
	private boolean autosaveInProgress;
	private long nextAutosaveAt;
	private long lastAutosaveSignature = Long.MIN_VALUE;
	private long versionWarningRevision = Long.MIN_VALUE;
	private SkeletonDocument versionWarningDocument;

	public EditorApp() { this(null); }

	public EditorApp(String initialPath) {
		this(initialPath, null);
	}

	public EditorApp(String initialPath, Path mcpCaptureRoot) {
		this.initialPath = initialPath;
		this.mcpCaptureRoot = mcpCaptureRoot;
	}

	@Override
	public void create() {
		preferences = Gdx.app.getPreferences(PREFERENCES_NAME);
		FfmpegAnimationExporter.setConfiguredExecutable(preferences.getString(PREF_FFMPEG_PATH, ""));
		I18n.setLanguage(Language.fromCode(preferences.getString(PREF_LANGUAGE, Language.CHINESE.code),
			Language.CHINESE));
		keyBindings.loadFrom(key -> preferences.getString(key, ""));
		I18n.addListener(languageListener);
		restoreSavedWorkspace = preferences.getInteger(PREF_VERSION, 0) == PREFERENCES_VERSION;
		lastOpenDirectory = preferences.getString(PREF_LAST_OPEN_DIRECTORY, "");
		lastSaveDirectory = preferences.getString(PREF_LAST_SAVE_DIRECTORY, "");
		recentProjects.addAll(decodeRecentProjects(preferences.getString(PREF_RECENT_PROJECTS, "")));
		recentProjects.removeIf(path -> !new File(path).isFile());
		appearanceMode = Mode.fromKey(preferences.getString(PREF_APPEARANCE_MODE, Mode.SYSTEM.key));
		lightTheme = Theme.fromKey(preferences.getString(PREF_LIGHT_THEME, Theme.JADE.key));
		darkTheme = Theme.fromKey(preferences.getString(PREF_DARK_THEME, Theme.JADE.key));
		systemDark = UiAppearance.detectSystemDark();
		kit = new UiKit(resolvedAppearance());
		applyNativeTitleBar();
		stage = new HoverScrollStage(new ScreenViewport());
		kit.installTextFieldHover(stage);
		Gdx.input.setInputProcessor(stage);

		editor = new SkeletonEditor(doc);
		audioEventPlayer = new AudioEventPlayer(this::setStatus);
		audioEventPlayer.setEnabled(!restoreSavedWorkspace
			|| preferences.getBoolean(PREF_EVENT_AUDIO_ENABLED, true));
		viewport = new EditorViewport(doc);
		viewport.setOverlayFont(kit.font);
		viewport.applyAppearance(kit.palette());
		viewport.setVisibleDocuments(() -> showAllSkeletons ? skeletonProject.documents() : List.of(doc));
		treePanel = new BoneTreePanel(doc, kit, editor);
		propsPanel = new PropertiesPanel(doc, kit, editor);
		attachPanel = new AttachmentPanel(doc, kit, editor);
		playbackPanel = new PlaybackPanel(doc, kit);
		timelinePanel = new TimelinePanel(doc, kit, new AnimationEditor(doc), editor, playbackPanel);
		viewport.setGridVisible(restoreSavedWorkspace
			? preferences.getBoolean(PREF_GRID_VISIBLE, viewport.isGridVisible()) : viewport.isGridVisible());
		viewport.setBonesVisible(restoreSavedWorkspace
			? preferences.getBoolean(PREF_BONES_VISIBLE, viewport.isBonesVisible()) : viewport.isBonesVisible());
		viewport.setSelectionHidden(restoreSavedWorkspace && preferences.getBoolean(PREF_SELECTION_HIDDEN, false));
		viewport.setShowInactiveSkinBones(restoreSavedWorkspace && preferences.getBoolean(PREF_INACTIVE_BONES, false));
		viewport.setPixelGridVisible(restoreSavedWorkspace && preferences.getBoolean(PREF_PIXEL_GRID_VISIBLE, false));
		viewport.setGhostingVisible(restoreSavedWorkspace
			? preferences.getBoolean(PREF_GHOSTING_VISIBLE, viewport.isGhostingVisible()) : viewport.isGhostingVisible());
		viewport.setGhostBonesVisible(restoreSavedWorkspace
			? preferences.getBoolean(PREF_GHOST_BONES_VISIBLE, viewport.isGhostBonesVisible())
			: viewport.isGhostBonesVisible());
		viewport.setTextureSmoothing(restoreSavedWorkspace
			? preferences.getBoolean(PREF_TEXTURE_SMOOTHING, true) : true);
		viewport.setViewportFlipped(restoreSavedWorkspace
			&& preferences.getBoolean(PREF_VIEWPORT_FLIPPED, false));
		viewport.setZoomLocked(restoreSavedWorkspace
			&& preferences.getBoolean(PREF_ZOOM_LOCKED, false));
		viewport.setPixelSnappingEnabled(restoreSavedWorkspace
			&& preferences.getBoolean(PREF_PIXEL_SNAPPING, false));
		timelinePanel.restoreViewSettings(restoreSavedWorkspace
			? preferences.getFloat(PREF_TIMELINE_ZOOM, TimelinePanel.DEFAULT_ZOOM) : TimelinePanel.DEFAULT_ZOOM,
			restoreSavedWorkspace ? preferences.getString(PREF_TRACK_FILTER, TimelinePanel.FILTER_ALL)
				: TimelinePanel.FILTER_ALL);

		wireInteractions();
		buildLayout();
		installShortcuts();
		refreshInterfaceLanguage();

		boolean initialLoadSucceeded = initialPath != null && !initialPath.isBlank() && loadFile(initialPath);
		if (shouldLoadExampleAtStartup(initialPath, initialLoadSucceeded)) {
			boolean fallback = initialPath != null && !initialPath.isBlank();
			if (loadExampleNow() && fallback) setStatus("启动文件打开失败，已加载内置示例", true);
		}
		nextAutosaveAt = TimeUtils.millis() + AUTOSAVE_INTERVAL_MILLIS;
		Gdx.app.postRunnable(this::showAutosaveRecoveryPrompt);
		Gdx.app.postRunnable(this::applyNativeTitleBar);
		if (mcpCaptureRoot != null) {
			try { mcpCaptureBridge = new com.openspine.mcp.EditorCaptureBridge(mcpCaptureRoot); }
			catch (IOException error) {
				Gdx.app.error("OpenSpine MCP capture", error.getMessage());
				setStatus("MCP 截图服务启动失败：" + error.getMessage(), true);
			}
		}
	}

	private void wireInteractions() {
		bindHistory(history);
		viewport.setOnSelectionChanged(this::viewportSelectionChanged);
		viewport.setOnAttachmentSelected(this::viewportAttachmentSelected);
		viewport.setOnPoseEdited(this::syncCommandState);
		viewport.setOnBonesTransformFinished(timelinePanel::autoKeyBoneTransforms);
		viewport.setOnStatus(this::setStatus);
		propsPanel.setOnPoseEdited(this::syncCommandState);
		propsPanel.setOnDataChanged(this::documentStructureChanged);
		propsPanel.setOnReversibleDataChanged(this::documentStructureChangedPreservingHistory);
		propsPanel.setOnBonesTransformFinished(timelinePanel::autoKeyBoneTransforms);
		propsPanel.setOnCoordinateSpaceChanged(viewport::setCoordinateSpace);
		propsPanel.setOnStatus(this::setStatus);
		treePanel.setOnDataChanged(this::documentStructureChanged);
		treePanel.setOnReversibleDataChanged(this::documentStructureChangedPreservingHistory);
		treePanel.setOnSelectionChanged(this::boneTreeSelectionChanged);
		treePanel.setOnStatus(this::setStatus);
		attachPanel.setOnDataChanged(this::attachmentChanged);
		attachPanel.setOnReversibleDataChanged(this::documentStructureChangedPreservingHistory);
		attachPanel.setOnSkinChanged(this::skinSelectionChanged);
		attachPanel.setOnAttachmentSelectionChanged(viewport::selectAttachment);
		attachPanel.setOnStatus(this::setStatus);
		timelinePanel.setOnDataChanged(this::timelineChanged);
		timelinePanel.setOnSelectionChanged(this::refreshSelection);
		timelinePanel.setOnAnimationChanged(() -> {
			playbackPanel.refreshFromDocument();
			syncCommandState();
		});
		timelinePanel.setOnProjectSettingsChanged(() -> {
			playbackPanel.refreshFromDocument();
			syncCommandState();
		});
		timelinePanel.setOnConstraintChanged(this::constraintChanged);
		timelinePanel.setOnTimeChanged(this::modeChanged);
		timelinePanel.setOnStatus(this::setStatus);
		playbackPanel.setOnAnimationChanged(() -> {
			timelinePanel.rebuild();
			syncCommandState();
		});
		playbackPanel.setOnModeChanged(this::modeChanged);
		playbackPanel.setOnSkinChanged(this::skinSelectionChanged);
		playbackPanel.setOnStatus(this::setStatus);
	}

	private void buildLayout() {
		Table root = new Table();
		root.setFillParent(true);
		if (WindowsTitleBar.usesCustomFrame())
			root.add(buildCustomTitleBar()).growX().height(CUSTOM_TITLE_BAR_HEIGHT).row();

		statusLabel = kit.controlLabel("");
		statusLabel.setAlignment(Align.center);
		statusLabel.setEllipsis(true);
		statusToast = new Table();
		statusToast.setTouchable(Touchable.disabled);
		statusToast.add(statusLabel).grow().minWidth(0f).minHeight(0f).center();
		statusToast.setVisible(false);
		Table toolbar = new Table();
		toolbar.setBackground(kit.panelBg);
		toolbar.pad(4);
		projectMenuBtn = button(I18n.t("项目", "Project"), this::toggleProjectMenu);
		projectMenuBtn.addListener(new InputListener() {
			@Override public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
				if (pointer != -1) return;
				projectMenuButtonHovered = true;
				cancelProjectMenuDismiss();
				showProjectMenu();
			}

			@Override public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
				if (pointer != -1) return;
				projectMenuButtonHovered = false;
				scheduleProjectMenuDismiss();
			}
		});
		toolbar.add(projectMenuBtn).height(28).minWidth(58).padRight(8);
		openBtn = button(I18n.t("打开", "Open"), this::requestOpen);
		saveBtn = button(I18n.t("保存", "Save"), () -> save());
		saveAsBtn = button(I18n.t("另存为", "Save As"), () -> saveAs());
		toolbar.add(openBtn).height(28).minWidth(52).padRight(3);
		toolbar.add(saveBtn).height(28).minWidth(52).padRight(3);
		toolbar.add(saveAsBtn).height(28).minWidth(64).padRight(10);

		undoBtn = button(I18n.t("撤销", "Undo"), this::undo);
		redoBtn = button(I18n.t("重做", "Redo"), this::redo);
		toolbar.add(undoBtn).height(28).minWidth(52).padRight(3);
		toolbar.add(redoBtn).height(28).minWidth(52).padRight(10);
		toolbar.add().width(TOOLBAR_TOOL_GROUP_GAP);

		for (EditorTool tool : EditorTool.values()) {
			TextButton button = button(toolButtonLabel(tool, keyBindings), () -> setTool(tool));
			toolButtons.put(tool, button);
			toolbar.add(button).height(28).minWidth(52).padRight(3);
		}

		poseBtn = button(I18n.t("姿态", "Pose"), this::togglePoseMode);
		toolbar.add(poseBtn).height(28).minWidth(58).padLeft(6);
		toolbar.add().growX().minWidth(0f);
		workspaceRestoreBar = new Table();
		for (WorkspacePanel panel : WorkspacePanel.values()) {
			TextButton restoreButton = button(panel.localizedRestoreLabel(),
				() -> setWorkspacePanelVisible(panel, true));
			workspaceRestoreButtons.put(panel, restoreButton);
		}
		toolbar.add(workspaceRestoreBar).height(28).minWidth(0f);
		collapseWorkspaceBtn = button(workspaceCollapseButtonLabel(false), this::toggleWorkspacePanels);
		toolbar.add(collapseWorkspaceBtn).height(28).minWidth(58).padLeft(3).padRight(3);
		interfaceMenuBtn = button(I18n.t("界面", "Interface"), this::toggleInterfaceMenu);
		toolbar.add(interfaceMenuBtn).height(28).minWidth(58);

		Table right = new Table();
		right.top().left();
		right.setBackground(kit.scrollBg);
		Table inspectorTabs = new Table();
		propertiesTabBtn = button(I18n.t("骨骼属性", "Bone Properties"), () -> selectInspector(false));
		attachmentsTabBtn = button(I18n.t("附件皮肤", "Attachments & Skins"), () -> selectInspector(true));
		inspectorTabs.add(propertiesTabBtn).growX().minWidth(0f).height(28).padRight(2);
		inspectorTabs.add(attachmentsTabBtn).growX().minWidth(0f).height(28);
		right.add(inspectorTabs).growX().minWidth(0f).pad(4).row();
		inspectorHost = new Table();
		selectInspector(restoreSavedWorkspace && preferences.getBoolean(PREF_INSPECTOR_ATTACHMENTS, false));
		ScrollPane inspectorScroll = new ScrollPane(inspectorHost, kit.scrollStyle);
		inspectorScroll.setFadeScrollBars(false);
		inspectorScroll.setScrollingDisabled(false, false);
		right.add(inspectorScroll).grow().minWidth(0f).minHeight(0f).row();

		skeletonSelect = kit.selectBox();
		skeletonSelect.setAlignment(Align.left);
		skeletonSelect.getList().setAlignment(Align.left);
		skeletonSelect.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshingSkeletonSelect) switchSkeleton(skeletonSelect.getSelectedIndex());
			}
		});
		allSkeletonsBtn = button(I18n.t("全部", "All"), this::toggleAllSkeletons);
		deleteSkeletonBtn = windowControlButton(kit.windowCloseIcon, kit.windowCloseActiveIcon,
			kit.windowCloseButton, this::requestDeleteCurrentSkeleton);
		Table skeletonHeader = new Table();
		skeletonHeader.setBackground(kit.panelBg);
		skeletonHeaderLabel = kit.controlLabel(I18n.t("骨架", "Skeleton"));
		skeletonHeader.add(skeletonHeaderLabel).left().padLeft(8f).padRight(6f);
		skeletonHeader.add(skeletonSelect).growX().minWidth(0f).height(28f).pad(4f, 0f, 4f, 4f);
		skeletonHeader.add(deleteSkeletonBtn).size(28f).padRight(3f);
		skeletonHeader.add(allSkeletonsBtn).width(48f).height(28f).padRight(4f);
		Table hierarchy = new Table();
		hierarchy.add(skeletonHeader).growX().height(36f).row();
		hierarchy.add(treePanel).grow().minWidth(0f).minHeight(0f);
		Container<Table> treeHost = new Container<>(hierarchy);
		treeHost.fill().minWidth(0f).minHeight(0f);
		viewportStatusLabel = new Label("", kit.labelStyle);
		viewportStatusLabel.setWrap(true);
		viewportStatus = new Table();
		viewportStatus.setTouchable(Touchable.disabled);
		viewportStatus.setBackground(kit.panelBg);
		viewportStatus.add(viewportStatusLabel).growX().minWidth(0f).pad(4f, 7f, 4f, 7f);
		Table viewportOverlay = new Table();
		viewportOverlay.setTouchable(Touchable.disabled);
		viewportOverlay.top().left();
		viewportOverlay.add(viewportStatus).growX().minWidth(0f).pad(5f).top();
		Container<Actor> viewportHost = new Container<>(new Stack(viewport, viewportOverlay));
		viewportHost.fill().minWidth(0f).minHeight(0f);
		Container<Table> inspectorHostContainer = new Container<>(right);
		inspectorHostContainer.fill().minWidth(0f).minHeight(0f);
		hierarchyPanelHost = treeHost;
		viewportPanelHost = viewportHost;
		inspectorPanelHost = inspectorHostContainer;
		workspaceSplitAmount = normalizeWorkspaceSplit(restoreSavedWorkspace
			? preferences.getFloat(PREF_WORKSPACE_SPLIT, DEFAULT_WORKSPACE_SPLIT) : DEFAULT_WORKSPACE_SPLIT);
		navigationSplitAmount = normalizeNavigationSplit(restoreSavedWorkspace
			? preferences.getFloat(PREF_NAVIGATION_SPLIT, DEFAULT_NAVIGATION_SPLIT) : DEFAULT_NAVIGATION_SPLIT);
		inspectorSplitAmount = normalizeInspectorSplit(restoreSavedWorkspace
			? preferences.getFloat(PREF_INSPECTOR_SPLIT, DEFAULT_INSPECTOR_SPLIT) : DEFAULT_INSPECTOR_SPLIT);
		hierarchyVisible = !restoreSavedWorkspace || preferences.getBoolean(PREF_HIERARCHY_VISIBLE, true);
		inspectorVisible = !restoreSavedWorkspace || preferences.getBoolean(PREF_INSPECTOR_VISIBLE, true);
		animationVisible = !restoreSavedWorkspace || preferences.getBoolean(PREF_ANIMATION_VISIBLE, true);
		if (!anyWorkspacePanelVisible(hierarchyVisible, inspectorVisible, animationVisible)) {
			workspaceCollapsed = true;
			collapsedWorkspacePanels.addAll(EnumSet.allOf(WorkspacePanel.class));
		}

		Table animationArea = new Table();
		animationArea.add(timelinePanel).grow().minWidth(0f).minHeight(0f);
		Container<Table> animationHost = new Container<>(animationArea);
		animationHost.fill().minWidth(0f).minHeight(0f);
		animationPanelHost = animationHost;
		workspaceHost = new Container<>();
		workspaceHost.fill().minWidth(0f).minHeight(0f);
		rebuildWorkspaceLayout();

		root.add(toolbar).growX().height(WORKSPACE_TOOLBAR_HEIGHT).row();
		root.add(workspaceHost).grow().minWidth(0f).minHeight(0f);
		stage.addActor(root);
		stage.addActor(statusToast);
		layoutStatusToast();
		syncCommandState();
	}

	private Table buildCustomTitleBar() {
		Table titleBar = new Table();
		titleBar.setBackground(kit.scrollBg);
		titleBar.left();
		try {
			titleBarIconTexture = new Texture(Gdx.files.classpath("assets/icons/openspine-24.png"));
			Image icon = new Image(titleBarIconTexture);
			icon.setScaling(Scaling.fit);
			titleBar.add(icon).size(18f).padLeft(8f).padRight(7f);
		} catch (RuntimeException error) {
			if (Gdx.app != null) Gdx.app.error("OpenSpine", "无法加载窗口标题栏图标", error);
			titleBar.add().width(8f);
		}
		titleBarLabel = kit.controlLabel(windowTitle(documentDisplayName(doc.savePath, doc.filePath), false));
		titleBarLabel.setAlignment(Align.left);
		titleBarLabel.setEllipsis(true);
		titleBar.add(titleBarLabel).growX().minWidth(0f).left();

		windowControlBar = new Table();
		Button minimize = windowControlButton(kit.windowMinimizeIcon, kit.windowMinimizeIcon,
			kit.windowControlButton,
			() -> WindowsTitleBar.minimize(nativeWindowHandle));
		Button maximize = windowControlButton(kit.windowMaximizeIcon, kit.windowMaximizeIcon,
			kit.windowControlButton,
			() -> WindowsTitleBar.toggleMaximized(nativeWindowHandle));
		Button close = windowControlButton(kit.windowCloseIcon, kit.windowCloseActiveIcon,
			kit.windowCloseButton, () -> {
			if (closeRequested()) Gdx.app.exit();
		});
		windowControlBar.add(minimize).width(WINDOW_CONTROL_WIDTH).growY();
		windowControlBar.add(maximize).width(WINDOW_CONTROL_WIDTH).growY();
		windowControlBar.add(close).width(WINDOW_CONTROL_WIDTH).growY();
		titleBar.add(windowControlBar).width(WINDOW_CONTROL_WIDTH * 3f).growY();
		installTitleBarDrag(titleBar);
		return titleBar;
	}

	private Button windowControlButton(Drawable icon, Drawable activeIcon, ButtonStyle style,
		Runnable action) {
		Button button = UiKit.asActionButton(new ImageButton(
			UiKit.windowControlImageStyle(style, icon, activeIcon)));
		button.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
		});
		return button;
	}

	private void installTitleBarDrag(Table titleBar) {
		titleBar.addListener(new InputListener() {
			private float downX, downY, lastClickX, lastClickY;
			private long lastClickAt;
			private boolean moving;

			@Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				if (button != Input.Buttons.LEFT || isDescendantOf(event.getTarget(), windowControlBar)) return false;
				moving = false;
				long now = TimeUtils.millis();
				float dx = event.getStageX() - lastClickX;
				float dy = event.getStageY() - lastClickY;
				if (lastClickAt != 0L && now - lastClickAt <= TITLE_BAR_DOUBLE_CLICK_MILLIS
					&& dx * dx + dy * dy <= 64f) {
					lastClickAt = 0L;
					WindowsTitleBar.toggleMaximized(nativeWindowHandle);
					return true;
				}
				downX = x;
				downY = y;
				lastClickX = event.getStageX();
				lastClickY = event.getStageY();
				lastClickAt = now;
				return true;
			}

			@Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
				if (moving || Math.abs(x - downX) < TITLE_BAR_DRAG_THRESHOLD
					&& Math.abs(y - downY) < TITLE_BAR_DRAG_THRESHOLD) return;
				lastClickAt = 0L;
				moving = WindowsTitleBar.beginMove(nativeWindowHandle);
			}

			@Override public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
				moving = false;
			}
		});
	}

	static boolean isDescendantOf(Actor actor, Actor ancestor) {
		if (ancestor == null) return false;
		for (Actor current = actor; current != null; current = current.getParent())
			if (current == ancestor) return true;
		return false;
	}

	private void bindHistory(EditHistory nextHistory) {
		if (nextHistory == null) throw new IllegalArgumentException("撤销历史不能为空");
		history = nextHistory;
		history.setOnChanged(() -> {
			if (suppressHistoryRefresh) return;
			treePanel.rebuildIfSkeletonChanged();
			timelinePanel.rebuild();
			refreshSelection();
		});
		if (viewport != null) viewport.setHistory(history);
		if (propsPanel != null) propsPanel.setHistory(history);
		if (treePanel != null) treePanel.setHistory(history);
		if (timelinePanel != null) timelinePanel.setHistory(history);
		if (attachPanel != null) attachPanel.setHistory(history);
		if (playbackPanel != null) playbackPanel.setHistory(history);
	}

	private void resetDocumentHistories() {
		documentHistories.clear();
		for (SkeletonDocument document : skeletonProject.documents())
			documentHistories.add(new EditHistory(doc, document.dirty));
		bindActiveHistory();
	}

	private void bindActiveHistory() {
		int index = skeletonProject.activeIndex();
		if (index < 0 || index >= documentHistories.size()) {
			bindHistory(new EditHistory(doc));
			return;
		}
		bindHistory(documentHistories.get(index));
	}

	private void replaceProjectSkeletons(List<SkeletonProject.LoadedSkeleton> skeletons) {
		skeletonProject.replaceAll(skeletons);
		nativeProjectPath = null;
		nativeProjectStamp = null;
		resetDocumentHistories();
	}

	private int addProjectSkeleton(SkeletonProject.LoadedSkeleton loaded, boolean activate) {
		boolean initiallyDirty = loaded.document().dirty;
		int index = skeletonProject.add(loaded, activate);
		documentHistories.add(index, new EditHistory(doc, initiallyDirty));
		bindActiveHistory();
		return index;
	}

	private boolean switchProjectSkeleton(int index) {
		if (!skeletonProject.switchTo(index)) return false;
		bindActiveHistory();
		return true;
	}

	private boolean removeProjectSkeleton(int index) {
		if (!skeletonProject.removeAt(index)) return false;
		documentHistories.remove(index);
		bindActiveHistory();
		return true;
	}

	private void switchSkeleton(int index) {
		if (refreshingSkeletonSelect || index < 0 || index >= skeletonProject.size()
			|| index == skeletonProject.activeIndex()) return;
		if (fileDialogOpen) {
			setStatus("请先关闭文件选择窗口", false);
			refreshSkeletonSelector();
			return;
		}
		if (documentIoInProgress()) {
			setStatus("请先等待当前保存或打包任务结束", false);
			refreshSkeletonSelector();
			return;
		}
		finishWorkspaceTextEdit();
		treePanel.persistViewState();
		timelinePanel.persistViewState();
		if (audioEventPlayer != null) audioEventPlayer.stop();
		switchProjectSkeleton(index);
		viewport.selectAttachment(null, null);
		rebuildPanels();
		viewport.refreshTextureFiltering();
		stage.act(0f);
		Gdx.app.postRunnable(viewport::frameAll);
		setStatus("当前骨架：" + skeletonProject.activeName(), false);
		syncCommandState();
	}

	private void toggleAllSkeletons() {
		if (skeletonProject.size() <= 1) return;
		showAllSkeletons = !showAllSkeletons;
		viewport.refreshTextureFiltering();
		Gdx.app.postRunnable(viewport::frameAll);
		setStatus(showAllSkeletons ? "已显示工程中的全部骨架" : "仅显示当前骨架", false);
		syncCommandState();
	}

	private void showProjectSkeletons() {
		if (documentIoInProgress() || fileDialogOpen) {
			setStatus("请先等待当前文件任务结束", false);
			return;
		}
		finishWorkspaceTextEdit();
		com.openspine.ui.ProjectSkeletonDialog.show(stage, kit, skeletonProject, this::moveProjectSkeleton,
			this::syncCommandState);
	}

	private void moveProjectSkeleton(int from, int to) {
		if (!skeletonProject.move(from, to)) return;
		EditHistory moved = documentHistories.remove(from);
		documentHistories.add(to, moved);
		bindActiveHistory();
	}

	private void refreshSkeletonSelector() {
		if (skeletonSelect != null) {
			refreshingSkeletonSelect = true;
			try {
				Array<String> items = new Array<>();
				for (SkeletonProject.SkeletonInfo skeleton : skeletonProject.skeletons())
					items.add((skeleton.dirty() ? "* " : "") + skeleton.name());
				skeletonSelect.setItems(items);
				if (skeletonProject.activeIndex() >= 0)
					skeletonSelect.setSelectedIndex(skeletonProject.activeIndex());
				skeletonSelect.setDisabled(skeletonProject.size() <= 1);
			} finally {
				refreshingSkeletonSelect = false;
			}
		}
		if (allSkeletonsBtn != null) {
			allSkeletonsBtn.setDisabled(skeletonProject.size() <= 1);
			allSkeletonsBtn.setStyle(showAllSkeletons && skeletonProject.size() > 1
				? kit.activeToolButton : kit.toolButton);
		}
		if (deleteSkeletonBtn != null)
			deleteSkeletonBtn.setDisabled(!skeletonProject.canRemove());
	}

	private void requestDeleteCurrentSkeleton() {
		int index = skeletonProject.activeIndex();
		if (index >= 0) requestDeleteSkeleton(index);
	}

	private void requestDeleteSkeleton(int index) {
		if (index < 0 || index >= skeletonProject.size()) return;
		if (!skeletonProject.canRemove()) {
			setStatus("至少需要保留一个骨架标签", false);
			return;
		}
		if (documentIoInProgress() || fileDialogOpen) {
			setStatus("请先等待当前文件任务结束", false);
			return;
		}
		finishWorkspaceTextEdit();
		if (index != skeletonProject.activeIndex()) switchSkeleton(index);
		SkeletonProject.SkeletonInfo info = skeletonProject.skeletons().get(index);
		if (!info.dirty()) {
			deleteSkeletonNow(index);
			return;
		}
		UiDialogs.unsavedChanges(stage, kit, "删除骨架",
			"骨架“" + info.name() + "”有未保存的修改。", "不保存",
			() -> save(() -> deleteSkeletonNow(index),
				() -> setStatus("保存未完成，已保留当前骨架", true)),
			() -> deleteSkeletonNow(index),
			() -> { });
	}

	private void deleteSkeletonNow(int index) {
		if (index < 0 || index >= skeletonProject.size() || !skeletonProject.canRemove()) return;
		if (removeProjectSkeleton(index)) {
			showAllSkeletons = false;
			saveCurrentSpineProjectState();
			rebuildPanels();
			viewport.refreshTextureFiltering();
			Gdx.app.postRunnable(viewport::frameAll);
			refreshSkeletonSelector();
			setStatus("已删除骨架", false);
			syncCommandState();
		}
	}

	private void selectInspector(boolean attachments) {
		if (inspectorHost == null) return;
		attachmentsInspectorSelected = attachments;
		inspectorHost.clearChildren();
		inspectorHost.add(attachments ? attachPanel : propsPanel).growX().top();
		propertiesTabBtn.setStyle(attachments ? kit.toolButton : kit.activeToolButton);
		attachmentsTabBtn.setStyle(attachments ? kit.activeToolButton : kit.toolButton);
		if (attachments) attachPanel.refresh();
		else propsPanel.refresh();
	}

	private TextButton button(String text, Runnable action) {
		TextButton button = UiKit.asActionButton(new TextButton(text, kit.toolButton));
		button.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
		});
		return button;
	}

	private void installShortcuts() {
		stage.addCaptureListener(new InputListener() {
			@Override public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
				if (fileDialogOpen) {
					event.stop();
					return true;
				}
				if (hasModalWindow() || !HoverScrollStage.targetIsWithin(event.getTarget(), viewport)
					|| !viewport.zoomFromStageScroll(event.getStageX(), event.getStageY(), amountY))
					return false;
				event.stop();
				return true;
			}

			@Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				if (fileDialogOpen) {
					event.stop();
					return true;
				}
				Actor focus = stage.getKeyboardFocus();
				if (focus instanceof TextField && pointerLeavesFocus(focus, event.getTarget()))
					stage.setKeyboardFocus(null);
				return false;
			}

			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (fileDialogOpen) {
					event.stop();
					return true;
				}
				Actor focus = stage.getKeyboardFocus();
				if (!globalShortcutsAllowed(hasModalWindow(), focus instanceof PopupMenu)) return false;
				boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
					|| Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
				boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
					|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
				boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
					|| Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
				if (alt) return false;
				KeyBindings.EditorAction action = keyBindings.match(keycode, ctrl, shift);
				if (focus instanceof TextField
					&& !KeyBindings.allowedWithTextFocus(action, new KeyBindings.Combo(keycode, ctrl, shift)))
					return false;
				return executeShortcut(action);
			}
		});
	}

	private boolean executeShortcut(KeyBindings.EditorAction action) {
		if (action == null) return false;
		switch (action) {
		case OPEN -> requestOpen();
		case SAVE -> save();
		case SAVE_AS -> saveAs();
		case RELOAD_IMAGES -> reloadImages();
		case UNDO -> undo();
		case REDO -> redo();
		case RECORD_POSE -> timelinePanel.keySelectedPose();
		case TOOL_POSE -> togglePoseMode();
		case TOOL_SELECT -> setTool(EditorTool.SELECT);
		case TOOL_MOVE -> setTool(EditorTool.MOVE);
		case TOOL_ROTATE -> setTool(EditorTool.ROTATE);
		case TOOL_SCALE -> setTool(EditorTool.SCALE);
		case TOOL_SHEAR -> setTool(EditorTool.SHEAR);
		case TOOL_MESH -> setTool(EditorTool.MESH);
		case TOOL_DEFORM -> setTool(EditorTool.DEFORM);
		case TOOL_WEIGHT -> setTool(EditorTool.WEIGHT);
		case FOCUS_SELECTED -> viewport.frameSelected();
		case FRAME_ALL -> viewport.frameAll();
		case TOGGLE_GRID -> viewport.setGridVisible(!viewport.isGridVisible());
		case TOGGLE_PIXEL_GRID -> togglePixelGrid();
		case TOGGLE_BONES -> viewport.setBonesVisible(!viewport.isBonesVisible());
		case TOGGLE_GHOSTING -> viewport.setGhostingVisible(!viewport.isGhostingVisible());
		case TOGGLE_SELECTION_VISIBILITY -> toggleSelectionVisibility();
		case TOGGLE_INACTIVE_BONES -> toggleInactiveSkinBones();
		case TOGGLE_VIEWPORT_FLIP -> toggleViewportFlip();
		case TOGGLE_ZOOM_LOCK -> toggleViewportZoomLock();
		case TOGGLE_PIXEL_SNAPPING -> togglePixelSnapping();
		case TOGGLE_CONSTRAINTS -> toggleAllConstraints();
		case SHOW_ALL_BONES -> treePanel.showAll();
		case PLAY_PAUSE -> playbackPanel.togglePlay();
		case STEP_BACKWARD -> playbackPanel.stepFrame(-1);
		case STEP_FORWARD -> playbackPanel.stepFrame(1);
		}
		syncCommandState();
		return true;
	}

	private boolean hasModalWindow() {
		for (Actor actor : stage.getActors()) {
			if (actor instanceof Window window && window.isVisible() && window.isModal()) return true;
		}
		return false;
	}

	enum HistoryAction { NONE, UNDO, REDO }
	enum SaveAction { NONE, SAVE, SAVE_AS }
	private enum WorkspacePanel {
		HIERARCHY("骨架层级", "Hierarchy", "层级", "Tree"),
		INSPECTOR("属性与附件", "Properties & Attachments", "属性", "Props"),
		ANIMATION("时间轴与播放", "Timeline & Playback", "时间轴", "Timeline");

		final String label;
		final String english;
		final String restoreLabel;
		final String restoreEnglish;
		WorkspacePanel(String label, String english, String restoreLabel, String restoreEnglish) {
			this.label = label;
			this.english = english;
			this.restoreLabel = restoreLabel;
			this.restoreEnglish = restoreEnglish;
		}

		String localizedLabel() { return I18n.t(label, english); }
		String localizedRestoreLabel() { return I18n.t(restoreLabel, restoreEnglish); }
	}
	enum ProjectCommand {
		NEW_PROJECT("新建项目", "New Project"), OPEN_PROJECT("打开项目", "Open Project"),
		RECENT_PROJECTS("最近项目", "Recent Projects"), SAVE_PROJECT("保存项目", "Save Project"),
		SAVE_PROJECT_AS("项目另存为", "Save Project As"), PACKAGE_PROJECT("打包项目", "Package Project"),
		NEW_SKELETON("新建骨架", "New Skeleton"), MANAGE_SKELETONS("管理骨架", "Manage Skeletons"),
		IMPORT_PROJECT("导入项目", "Import Project"),
		IMPORT_ANIMATIONS("导入动画", "Import Animation"),
		IMPORT_IMAGES("导入图片并打包图集", "Import Images and Pack Atlas"),
		IMPORT_PSD("导入分层 PSD", "Import Layered PSD"),
		RELOAD_IMAGES("重新加载图片", "Reload Images"),
		DIAGNOSTICS("项目诊断", "Project Diagnostics"),
		EXPORT_PROJECT("导出项目", "Export Project"), SETTINGS("设置", "Settings");

		final String label;
		private final String english;

		ProjectCommand(String label, String english) {
			this.label = label;
			this.english = english;
		}

		String localizedLabel() { return I18n.t(label, english); }
	}

	static HistoryAction historyAction(boolean textFieldFocused, int keycode, boolean shift) {
		if (textFieldFocused) return HistoryAction.NONE;
		if (keycode == Input.Keys.Y || keycode == Input.Keys.Z && shift) return HistoryAction.REDO;
		return keycode == Input.Keys.Z ? HistoryAction.UNDO : HistoryAction.NONE;
	}

	static SaveAction saveAction(int keycode, boolean shift) {
		if (keycode != Input.Keys.S) return SaveAction.NONE;
		return shift ? SaveAction.SAVE_AS : SaveAction.SAVE;
	}

	static boolean globalShortcutsAllowed(boolean modalOpen, boolean popupFocused) {
		return !modalOpen && !popupFocused;
	}

	static boolean pointerLeavesFocus(Actor keyboardFocus, Actor pointerTarget) {
		return keyboardFocus != null && !UiDialogs.isWithin(pointerTarget, keyboardFocus);
	}

	static boolean shouldFinishWorkspaceTextEdit(boolean textFieldFocused, boolean modalOpen) {
		return textFieldFocused && !modalOpen;
	}

	static List<String> decodeRecentProjects(String encoded) {
		List<String> paths = new ArrayList<>();
		if (encoded == null || encoded.isBlank()) return paths;
		for (String line : encoded.split("\\R")) {
			String path = line.trim();
			if (!path.isEmpty() && !paths.contains(path)) paths.add(path);
			if (paths.size() == MAX_RECENT_PROJECTS) break;
		}
		return paths;
	}

	static String encodeRecentProjects(List<String> paths) {
		if (paths == null || paths.isEmpty()) return "";
		StringBuilder encoded = new StringBuilder();
		List<String> added = new ArrayList<>();
		for (String path : paths) {
			if (path == null || path.isBlank()) continue;
			String normalized = path.trim();
			if (added.contains(normalized)) continue;
			if (encoded.length() > 0) encoded.append('\n');
			encoded.append(normalized);
			added.add(normalized);
			if (added.size() == MAX_RECENT_PROJECTS) break;
		}
		return encoded.toString();
	}

	static List<String> updateRecentProjects(List<String> current, String path) {
		List<String> updated = new ArrayList<>();
		if (path != null && !path.isBlank()) updated.add(path.trim());
		if (current != null) {
			for (String existing : current) {
				if (existing == null || existing.isBlank() || updated.contains(existing.trim())) continue;
				updated.add(existing.trim());
				if (updated.size() == MAX_RECENT_PROJECTS) break;
			}
		}
		return updated;
	}

	static String safeProjectFileStem(String name) {
		if (name == null) return "skeleton";
		String safe = name.trim().replaceAll("[\\x00-\\x1f<>:\"/\\\\|?*]", "_")
			.replaceAll("[. ]+$", "");
		return safe.isEmpty() ? "skeleton" : safe;
	}

	static String suggestedSavePath(String savePath, String sourcePath) {
		return suggestedSavePath(savePath, sourcePath, null);
	}

	static String suggestedSavePath(String savePath, String sourcePath, String lastSaveDirectory) {
		if (savePath != null && !savePath.isBlank()) return savePath;
		if (sourcePath != null && sourcePath.startsWith("new:")) {
			File directory = DesktopLauncher.existingDirectory(lastSaveDirectory, DesktopLauncher.defaultDirectory());
			return new File(directory == null ? new File("assets") : directory,
				safeProjectFileStem(sourcePath.substring("new:".length())) + ".json").getPath();
		}
		if (sourcePath != null && !sourcePath.isBlank() && !sourcePath.startsWith("builtin:")) {
			File source = new File(sourcePath);
			String name = source.getName();
			String extension = name.toLowerCase(Locale.ROOT).endsWith(".skel") ? ".skel" : ".json";
			int dot = name.lastIndexOf('.');
			if (dot > 0) name = name.substring(0, dot);
			File parent = source.getAbsoluteFile().getParentFile();
			return new File(parent == null ? new File(".") : parent, name + extension).getPath();
		}
		File directory = DesktopLauncher.existingDirectory(lastSaveDirectory, DesktopLauncher.defaultDirectory());
		return new File(directory == null ? new File("assets") : directory, "skeleton.json").getPath();
	}

	static String documentDisplayName(String savePath, String sourcePath) {
		String displayPath = savePath != null && !savePath.isBlank() ? savePath : sourcePath;
		if (displayPath == null || displayPath.isBlank()) return "未加载";
		if (displayPath.startsWith("new:")) return displayPath.substring("new:".length());
		return displayPath.startsWith("builtin:") ? I18n.t("内置示例", "Built-in Example")
			: new File(displayPath).getName();
	}

	static String suggestedPackagePath(String savePath, String sourcePath, String lastSaveDirectory) {
		String displayName = documentDisplayName(savePath, sourcePath);
		int dot = displayName.lastIndexOf('.');
		if (dot > 0) displayName = displayName.substring(0, dot);
		File source = savePath != null && !savePath.isBlank() ? new File(savePath)
			: sourcePath != null && !sourcePath.isBlank() && !sourcePath.startsWith("builtin:")
				&& !sourcePath.startsWith("new:") ? new File(sourcePath) : null;
		File directory = source == null ? null : source.getAbsoluteFile().getParentFile();
		if (directory == null || !directory.isDirectory())
			directory = DesktopLauncher.existingDirectory(lastSaveDirectory, DesktopLauncher.defaultDirectory());
		return new File(directory == null ? new File("assets") : directory,
			safeProjectFileStem(displayName) + "-project.zip").getPath();
	}

	static String suggestedSpineProjectPath(String savePath, String sourcePath, String lastSaveDirectory) {
		File dataFile = new File(suggestedSavePath(savePath, sourcePath, lastSaveDirectory)).getAbsoluteFile();
		String name = dataFile.getName();
		int dot = name.lastIndexOf('.');
		if (dot > 0) name = name.substring(0, dot);
		File directory = dataFile.getParentFile();
		return new File(directory == null ? new File(".") : directory,
			safeProjectFileStem(name) + "-rebuilt.spine").getPath();
	}

	static String suggestedPngPath(String savePath, String sourcePath, String lastSaveDirectory) {
		return suggestedImagePath(savePath, sourcePath, lastSaveDirectory, ".png");
	}

	static String suggestedJpegPath(String savePath, String sourcePath, String lastSaveDirectory) {
		return suggestedImagePath(savePath, sourcePath, lastSaveDirectory, ".jpg");
	}

	static String suggestedGifPath(String savePath, String sourcePath, String lastSaveDirectory) {
		return suggestedImagePath(savePath, sourcePath, lastSaveDirectory, ".gif");
	}

	static String suggestedApngPath(String savePath, String sourcePath, String lastSaveDirectory) {
		return suggestedImagePath(savePath, sourcePath, lastSaveDirectory, ".apng");
	}

	static String suggestedAviPath(String savePath, String sourcePath, String lastSaveDirectory) {
		return suggestedImagePath(savePath, sourcePath, lastSaveDirectory, ".avi");
	}

	private static String suggestedImagePath(String savePath, String sourcePath, String lastSaveDirectory,
		String extension) {
		File dataSuggestion = new File(suggestedSavePath(savePath, sourcePath, lastSaveDirectory)).getAbsoluteFile();
		String name = dataSuggestion.getName();
		int dot = name.lastIndexOf('.');
		if (dot > 0) name = name.substring(0, dot);
		File parent = dataSuggestion.getParentFile();
		return new File(parent == null ? new File(".") : parent, safeProjectFileStem(name) + extension).getPath();
	}

	static PngFrameExporter.Settings pngExportSettings(String scalePercentText, String paddingText,
		boolean transparent, boolean premultipliedAlpha) {
		return pngExportSettings(scalePercentText, paddingText,
			Integer.toString(PngFrameExporter.DEFAULT_COMPRESSION), transparent, premultipliedAlpha);
	}

	static PngFrameExporter.Settings pngExportSettings(String scalePercentText, String paddingText,
		String compressionText, boolean transparent, boolean premultipliedAlpha) {
		return new PngFrameExporter.Settings(imageExportScale(scalePercentText), imageExportPadding(paddingText),
			parseRequiredInt(compressionText, "压缩级别"), transparent, transparent && premultipliedAlpha);
	}

	static JpegFrameExporter.Settings jpegExportSettings(String scalePercentText, String paddingText,
		String qualityText) {
		if (qualityText == null || qualityText.isBlank()) throw new NumberFormatException("质量不能为空");
		return new JpegFrameExporter.Settings(imageExportScale(scalePercentText), imageExportPadding(paddingText),
			Integer.parseInt(qualityText.trim()));
	}

	static PngAnimationExporter.Settings pngSequenceExportSettings(String fpsText, String startFrameText,
		String endFrameText, String scalePercentText, String paddingText, boolean transparent,
		boolean premultipliedAlpha) {
		return pngSequenceExportSettings(fpsText, startFrameText, endFrameText, "0", scalePercentText,
			paddingText, transparent, premultipliedAlpha);
	}

	static PngAnimationExporter.Settings pngSequenceExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		boolean transparent, boolean premultipliedAlpha) {
		return pngSequenceExportSettings(fpsText, startFrameText, endFrameText, warmUpText,
			scalePercentText, paddingText, Integer.toString(PngFrameExporter.DEFAULT_COMPRESSION),
			transparent, premultipliedAlpha);
	}

	static PngAnimationExporter.Settings pngSequenceExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		String compressionText, boolean transparent, boolean premultipliedAlpha) {
		return pngSequenceExportSettings(fpsText, startFrameText, endFrameText, warmUpText,
			scalePercentText, paddingText, compressionText, transparent, premultipliedAlpha, true);
	}

	static PngAnimationExporter.Settings pngSequenceExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		String compressionText, boolean transparent, boolean premultipliedAlpha, boolean includeLastFrame) {
		return new PngAnimationExporter.Settings(UiNumbers.parseFinite(fpsText),
			parseRequiredInt(startFrameText, "起始帧"), parseRequiredInt(endFrameText, "结束帧"),
			parseRequiredInt(warmUpText, "预热次数"), imageExportScale(scalePercentText),
			imageExportPadding(paddingText), parseRequiredInt(compressionText, "压缩级别"), transparent,
			transparent && premultipliedAlpha, includeLastFrame);
	}

	static JpegAnimationExporter.Settings jpegSequenceExportSettings(String fpsText, String startFrameText,
		String endFrameText, String scalePercentText, String paddingText, String qualityText) {
		return jpegSequenceExportSettings(fpsText, startFrameText, endFrameText, "0", scalePercentText,
			paddingText, qualityText);
	}

	static JpegAnimationExporter.Settings jpegSequenceExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		String qualityText) {
		return jpegSequenceExportSettings(fpsText, startFrameText, endFrameText, warmUpText,
			scalePercentText, paddingText, qualityText, true);
	}

	static JpegAnimationExporter.Settings jpegSequenceExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		String qualityText, boolean includeLastFrame) {
		return new JpegAnimationExporter.Settings(UiNumbers.parseFinite(fpsText),
			parseRequiredInt(startFrameText, "起始帧"), parseRequiredInt(endFrameText, "结束帧"),
			parseRequiredInt(warmUpText, "预热次数"), imageExportScale(scalePercentText),
			imageExportPadding(paddingText),
			parseRequiredInt(qualityText, "质量"), includeLastFrame);
	}

	static GifAnimationExporter.Settings gifExportSettings(String fpsText, String startFrameText,
		String endFrameText, String scalePercentText, String paddingText, boolean transparent,
		boolean infiniteLoop, String repeatText, String alphaThresholdText) {
		return gifExportSettings(fpsText, startFrameText, endFrameText, "0", scalePercentText,
			paddingText, transparent, infiniteLoop, repeatText, alphaThresholdText);
	}

	static GifAnimationExporter.Settings gifExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		boolean transparent, boolean infiniteLoop, String repeatText, String alphaThresholdText) {
		return gifExportSettings(fpsText, startFrameText, endFrameText, warmUpText, scalePercentText,
			paddingText, transparent, infiniteLoop, repeatText, alphaThresholdText, true);
	}

	static GifAnimationExporter.Settings gifExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		boolean transparent, boolean infiniteLoop, String repeatText, String alphaThresholdText,
		boolean includeLastFrame) {
		float fps = UiNumbers.parseFinite(fpsText);
		int startFrame = parseRequiredInt(startFrameText, "起始帧");
		int endFrame = parseRequiredInt(endFrameText, "结束帧");
		int warmUp = parseRequiredInt(warmUpText, "预热次数");
		int repeat = infiniteLoop ? 0 : parseRequiredInt(repeatText, "循环次数");
		int alphaThreshold = transparent ? parseRequiredInt(alphaThresholdText, "透明阈值")
			: GifAnimationExporter.DEFAULT_ALPHA_THRESHOLD;
		return new GifAnimationExporter.Settings(fps, startFrame, endFrame, warmUp,
			imageExportScale(scalePercentText), imageExportPadding(paddingText), transparent,
				repeat, alphaThreshold, includeLastFrame);
	}

	static ApngAnimationExporter.Settings apngExportSettings(String fpsText, String startFrameText,
		String endFrameText, String scalePercentText, String paddingText, boolean transparent,
		boolean infiniteLoop, String repeatText, String compressionText) {
		return apngExportSettings(fpsText, startFrameText, endFrameText, "0", scalePercentText,
			paddingText, transparent, infiniteLoop, repeatText, compressionText);
	}

	static ApngAnimationExporter.Settings apngExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		boolean transparent, boolean infiniteLoop, String repeatText, String compressionText) {
		return apngExportSettings(fpsText, startFrameText, endFrameText, warmUpText, scalePercentText,
			paddingText, transparent, infiniteLoop, repeatText, compressionText, true);
	}

	static ApngAnimationExporter.Settings apngExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		boolean transparent, boolean infiniteLoop, String repeatText, String compressionText,
		boolean includeLastFrame) {
		float fps = UiNumbers.parseFinite(fpsText);
		int startFrame = parseRequiredInt(startFrameText, "起始帧");
		int endFrame = parseRequiredInt(endFrameText, "结束帧");
		int warmUp = parseRequiredInt(warmUpText, "预热次数");
		int repeat = infiniteLoop ? 0 : parseRequiredInt(repeatText, "播放次数");
		int compression = parseRequiredInt(compressionText, "压缩级别");
		return new ApngAnimationExporter.Settings(fps, startFrame, endFrame, warmUp,
			imageExportScale(scalePercentText), imageExportPadding(paddingText), transparent,
			repeat, compression, includeLastFrame);
	}

	static AviAnimationExporter.Settings aviExportSettings(String fpsText, String startFrameText,
		String endFrameText, String scalePercentText, String paddingText, String qualityText) {
		return aviExportSettings(fpsText, startFrameText, endFrameText, "0", scalePercentText,
			paddingText, qualityText);
	}

	static AviAnimationExporter.Settings aviExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		String qualityText) {
		return aviExportSettings(fpsText, startFrameText, endFrameText, warmUpText,
			scalePercentText, paddingText, qualityText, true);
	}

	static AviAnimationExporter.Settings aviExportSettings(String fpsText, String startFrameText,
		String endFrameText, String warmUpText, String scalePercentText, String paddingText,
		String qualityText, boolean includeLastFrame) {
		float fps = UiNumbers.parseFinite(fpsText);
		int startFrame = parseRequiredInt(startFrameText, "起始帧");
		int endFrame = parseRequiredInt(endFrameText, "结束帧");
		int warmUp = parseRequiredInt(warmUpText, "预热次数");
		int quality = parseRequiredInt(qualityText, "质量");
		return new AviAnimationExporter.Settings(fps, startFrame, endFrame, warmUp,
			imageExportScale(scalePercentText), imageExportPadding(paddingText), quality, includeLastFrame);
	}

	record VideoExportOptions(PngAnimationExporter.Settings frames,
		FfmpegAnimationExporter.Settings encoding, boolean includeAudio, int backgroundRgba) {}
	record VideoExportResult(int width, int height, int frameCount, int audioCueCount) {}

	static VideoExportOptions videoExportSettings(FfmpegAnimationExporter.Format format,
		String fpsText, String startFrameText, String endFrameText, String warmUpText,
		String scalePercentText, String paddingText, boolean transparent, String backgroundText,
		FfmpegAnimationExporter.Profile profile, int resolutionIndex, boolean includeAudio,
		boolean includeLastFrame) {
		float fps = UiNumbers.parseFinite(fpsText);
		int background = transparent ? PngFrameExporter.DEFAULT_BACKGROUND_RGBA
			: Color.rgba8888(UiColors.parseRgb(backgroundText));
		PngAnimationExporter.Settings frames = new PngAnimationExporter.Settings(fps,
			parseRequiredInt(startFrameText, "起始帧"), parseRequiredInt(endFrameText, "结束帧"),
			parseRequiredInt(warmUpText, "预热次数"), imageExportScale(scalePercentText),
			imageExportPadding(paddingText), 1, transparent, false, includeLastFrame);
		int[] maximum = videoMaximumResolution(resolutionIndex);
		FfmpegAnimationExporter.Settings encoding = new FfmpegAnimationExporter.Settings(fps,
			format, profile, transparent, maximum[0], maximum[1]);
		return new VideoExportOptions(frames, encoding, includeAudio, background);
	}

	static int[] videoMaximumResolution(int index) {
		return switch (index) {
		case 0 -> new int[] {0, 0};
		case 1 -> new int[] {1280, 720};
		case 2 -> new int[] {1920, 1080};
		case 3 -> new int[] {3840, 2160};
		default -> throw new IllegalArgumentException("视频最大分辨率选项无效");
		};
	}

	static String ffmpegFormatLabel(FfmpegAnimationExporter.Format format) {
		return switch (format) {
		case WEBM -> "WebM";
		case MOV -> "MOV";
		case AWEBP -> "AWEBP";
		};
	}

	static boolean animationHasAudioEvents(Animation animation) {
		if (animation == null) return false;
		for (Animation.Timeline timeline : animation.getTimelines()) {
			if (!(timeline instanceof Animation.EventTimeline events)) continue;
			for (var event : events.getEvents())
				if (event != null && event.getData() != null && event.getData().getAudioPath() != null
					&& !event.getData().getAudioPath().isBlank()) return true;
		}
		return false;
	}

	static FfmpegAnimationExporter.Profile videoProfile(String label) {
		for (FfmpegAnimationExporter.Profile profile : FfmpegAnimationExporter.Profile.values())
			if (profile.label().equals(label)) return profile;
		throw new IllegalArgumentException("视频编码档位无效");
	}

	private static int parseRequiredInt(String text, String fieldName) {
		if (text == null || text.isBlank()) throw new NumberFormatException(fieldName + "不能为空");
		return Integer.parseInt(text.trim());
	}

	private static float imageExportScale(String scalePercentText) {
		float percent = UiNumbers.parseFinite(scalePercentText);
		if (percent < PngFrameExporter.MIN_SCALE * 100f || percent > PngFrameExporter.MAX_SCALE * 100f)
			throw new IllegalArgumentException("缩放必须在 1% 到 6400% 之间");
		return percent / 100f;
	}

	private static int imageExportPadding(String paddingText) {
		if (paddingText == null || paddingText.isBlank()) throw new NumberFormatException("留白不能为空");
		return Integer.parseInt(paddingText.trim());
	}

	static String windowTitle(String documentName, boolean dirty) {
		return (dirty ? "* " : "") + documentName + I18n.t(
			" — OpenSpine 骨骼动画编辑器", " — OpenSpine Skeleton Animation Editor");
	}

	static boolean shouldDismissProjectMenu(boolean buttonHovered, boolean menuHovered) {
		return !buttonHovered && !menuHovered;
	}

	static boolean shouldLoadExampleAtStartup(String path, boolean fileLoadSucceeded) {
		return path == null || path.isBlank() || !fileLoadSucceeded;
	}

	static float normalizeWorkspaceSplit(float split) {
		if (!Float.isFinite(split)) return DEFAULT_WORKSPACE_SPLIT;
		return Math.max(MIN_WORKSPACE_SPLIT, Math.min(MAX_WORKSPACE_SPLIT, split));
	}

	static float normalizeNavigationSplit(float split) {
		if (!Float.isFinite(split)) return DEFAULT_NAVIGATION_SPLIT;
		return Math.max(MIN_NAVIGATION_SPLIT, Math.min(MAX_NAVIGATION_SPLIT, split));
	}

	static float normalizeInspectorSplit(float split) {
		if (!Float.isFinite(split)) return DEFAULT_INSPECTOR_SPLIT;
		return Math.max(MIN_INSPECTOR_SPLIT, Math.min(MAX_INSPECTOR_SPLIT, split));
	}

	static boolean anyWorkspacePanelVisible(boolean hierarchy, boolean inspector, boolean animation) {
		return hierarchy || inspector || animation;
	}

	static String workspaceCollapseButtonLabel(boolean collapsed) {
		return collapsed ? I18n.t("放出", "Show") : I18n.t("收起", "Hide");
	}

	static SystemCursor workspaceCursor(boolean blocked, boolean workspaceHandle,
		boolean navigationHandle, boolean inspectorHandle) {
		if (blocked) return SystemCursor.Arrow;
		if (workspaceHandle) return SystemCursor.VerticalResize;
		return navigationHandle || inspectorHandle ? SystemCursor.HorizontalResize : SystemCursor.Arrow;
	}

	private void toggleProjectMenu() {
		if (projectMenu != null && projectMenu.getStage() == stage) projectMenu.dismiss();
		else {
			if (interfaceMenu != null) interfaceMenu.dismiss();
			showProjectMenu();
		}
	}

	private void showProjectMenu() {
		if (stage == null || projectMenuBtn == null || projectMenuBtn.getStage() != stage || hasModalWindow()) return;
		if (interfaceMenu != null) interfaceMenu.dismiss();
		if (projectMenu != null && projectMenu.getStage() == stage) return;
		Vector2 anchor = projectMenuBtn.localToStageCoordinates(new Vector2(0f, 0f));
		PopupMenu menu = new PopupMenu(kit, anchor.x, anchor.y, stage);
		projectMenu = menu;
		projectMenuBtn.setStyle(kit.activeToolButton);
		for (ProjectCommand command : ProjectCommand.values()) {
			if (command == ProjectCommand.SAVE_PROJECT || command == ProjectCommand.NEW_SKELETON
				|| command == ProjectCommand.SETTINGS) menu.addSeparator();
			menu.addItem(command.localizedLabel()).setOnClick(() -> executeProjectCommand(command));
		}
		menu.setOnMouseHover(() -> {
			projectMenuHovered = true;
			cancelProjectMenuDismiss();
		}, () -> {
			projectMenuHovered = false;
			scheduleProjectMenuDismiss();
		});
		menu.setOnClose(() -> {
			if (projectMenu == menu) projectMenu = null;
			projectMenuHovered = false;
			cancelProjectMenuDismiss();
			if (projectMenuBtn != null) projectMenuBtn.setStyle(kit.toolButton);
		});
	}

	private void scheduleProjectMenuDismiss() {
		cancelProjectMenuDismiss();
		if (projectMenu == null || projectMenu.getStage() != stage) return;
		projectMenuDismissTask = new Timer.Task() {
			@Override public void run() {
				projectMenuDismissTask = null;
				if (shouldDismissProjectMenu(projectMenuButtonHovered, projectMenuHovered)
					&& projectMenu != null) projectMenu.dismiss();
			}
		};
		Timer.schedule(projectMenuDismissTask, PROJECT_MENU_DISMISS_DELAY_SECONDS);
	}

	private void cancelProjectMenuDismiss() {
		if (projectMenuDismissTask == null) return;
		projectMenuDismissTask.cancel();
		projectMenuDismissTask = null;
	}

	private void toggleInterfaceMenu() {
		if (interfaceMenu != null && interfaceMenu.getStage() == stage) {
			interfaceMenu.dismiss();
			return;
		}
		if (projectMenu != null) projectMenu.dismiss();
		showInterfaceMenu();
	}

	private void showInterfaceMenu() {
		if (stage == null || interfaceMenuBtn == null || interfaceMenuBtn.getStage() != stage || hasModalWindow()) return;
		Vector2 anchor = interfaceMenuBtn.localToStageCoordinates(new Vector2(0f, 0f));
		PopupMenu menu = new PopupMenu(kit, anchor.x, anchor.y, stage);
		interfaceMenu = menu;
		interfaceMenuBtn.setStyle(kit.activeToolButton);
		menu.addItem(I18n.t("聚焦选中", "Focus Selection")).setOnClick(() -> viewport.frameSelected());
		menu.addItem(I18n.t("显示全部", "Frame All")).setOnClick(() -> viewport.frameAll());
		menu.addSeparator();
		menu.addItem(I18n.t("网格", "Grid"), viewport.isGridVisible()).setOnClick(() -> {
			viewport.setGridVisible(!viewport.isGridVisible());
			syncCommandState();
		});
		menu.addItem(I18n.t("骨骼", "Bones"), viewport.isBonesVisible()).setOnClick(() -> {
			viewport.setBonesVisible(!viewport.isBonesVisible());
			syncCommandState();
		});
		menu.addItem(I18n.t("项目像素网格", "Project Pixel Grid"), viewport.isPixelGridVisible())
			.setOnClick(this::togglePixelGrid);
		menu.addItem(I18n.t("隐藏视区选择", "Hide Viewport Selection"), viewport.isSelectionHidden())
			.setOnClick(this::toggleSelectionVisibility);
		menu.addItem(I18n.t("非活动皮肤骨骼", "Inactive Skin Bones"), viewport.isShowInactiveSkinBones())
			.setOnClick(this::toggleInactiveSkinBones);
		menu.addItem(I18n.t("残影", "Ghosting"), viewport.isGhostingVisible()).setOnClick(() -> {
			viewport.setGhostingVisible(!viewport.isGhostingVisible());
			syncCommandState();
		});
		menu.addItem(I18n.t("图片平滑", "Texture Smoothing"),
			viewport.isTextureSmoothingEnabled()).setOnClick(() -> {
			viewport.setTextureSmoothing(!viewport.isTextureSmoothingEnabled());
			setStatus(viewport.isTextureSmoothingEnabled()
				? I18n.t("图片平滑预览已开启", "Smooth texture preview enabled")
				: I18n.t("图片像素预览已开启", "Pixel texture preview enabled"), false);
			syncCommandState();
		});
		menu.addItem(I18n.t("翻转视区", "Flip Viewport"), viewport.isViewportFlipped())
			.setOnClick(this::toggleViewportFlip);
		menu.addItem(I18n.t("锁定视区缩放", "Lock Viewport Zoom"), viewport.isZoomLocked())
			.setOnClick(this::toggleViewportZoomLock);
		menu.addItem(I18n.t("像素吸附", "Pixel Snapping"), viewport.isPixelSnappingEnabled())
			.setOnClick(this::togglePixelSnapping);
		menu.addItem(I18n.t("禁用全部约束", "Disable All Constraints"), !viewport.areConstraintsEnabled())
			.setOnClick(this::toggleAllConstraints);
		menu.addSeparator();
		menu.addItem(WorkspacePanel.HIERARCHY.localizedLabel(), hierarchyVisible)
			.setOnClick(() -> setWorkspacePanelVisible(WorkspacePanel.HIERARCHY, !hierarchyVisible));
		menu.addItem(WorkspacePanel.INSPECTOR.localizedLabel(), inspectorVisible)
			.setOnClick(() -> setWorkspacePanelVisible(WorkspacePanel.INSPECTOR, !inspectorVisible));
		menu.addItem(WorkspacePanel.ANIMATION.localizedLabel(), animationVisible)
			.setOnClick(() -> setWorkspacePanelVisible(WorkspacePanel.ANIMATION, !animationVisible));
		menu.addSeparator();
		menu.addItem(I18n.t("重置界面布局", "Reset Workspace Layout")).setOnClick(this::resetWorkspaceLayout);
		menu.setOnClose(() -> {
			if (interfaceMenu == menu) interfaceMenu = null;
			if (interfaceMenuBtn != null) interfaceMenuBtn.setStyle(kit.toolButton);
		});
	}

	private void toggleViewportFlip() {
		viewport.setViewportFlipped(!viewport.isViewportFlipped());
		setStatus(viewport.isViewportFlipped()
			? I18n.t("视区已翻转", "Viewport flipped")
			: I18n.t("视区翻转已关闭", "Viewport flip disabled"), false);
	}

	private void toggleSelectionVisibility() {
		viewport.setSelectionHidden(!viewport.isSelectionHidden());
		setStatus(viewport.isSelectionHidden()
			? I18n.t("视区选择已隐藏", "Viewport selection hidden")
			: I18n.t("视区选择已显示", "Viewport selection shown"), false);
		syncCommandState();
	}

	private void togglePixelGrid() {
		viewport.setPixelGridVisible(!viewport.isPixelGridVisible());
		setStatus(viewport.isPixelGridVisible()
			? I18n.t("项目像素网格已开启，放大后显示", "Project pixel grid enabled; zoom in to see it")
			: I18n.t("项目像素网格已关闭", "Project pixel grid disabled"), false);
		syncCommandState();
	}

	private void toggleInactiveSkinBones() {
		viewport.setShowInactiveSkinBones(!viewport.isShowInactiveSkinBones());
		setStatus(viewport.isShowInactiveSkinBones()
			? I18n.t("非活动皮肤骨骼参考已开启", "Inactive skin bone reference enabled")
			: I18n.t("非活动皮肤骨骼参考已关闭", "Inactive skin bone reference disabled"), false);
		syncCommandState();
	}

	private void toggleViewportZoomLock() {
		viewport.setZoomLocked(!viewport.isZoomLocked());
		setStatus(viewport.isZoomLocked()
			? I18n.t("视区缩放已锁定", "Viewport zoom locked")
			: I18n.t("视区缩放已解锁", "Viewport zoom unlocked"), false);
	}

	private void togglePixelSnapping() {
		viewport.setPixelSnappingEnabled(!viewport.isPixelSnappingEnabled());
		setStatus(viewport.isPixelSnappingEnabled()
			? I18n.t("像素吸附已开启", "Pixel snapping enabled")
			: I18n.t("像素吸附已关闭", "Pixel snapping disabled"), false);
	}

	private void toggleAllConstraints() {
		viewport.setConstraintsEnabled(!viewport.areConstraintsEnabled());
		setStatus(viewport.areConstraintsEnabled()
			? I18n.t("全部约束已启用", "All constraints enabled")
			: I18n.t("全部约束已禁用", "All constraints disabled"), false);
	}

	private void setWorkspacePanelVisible(WorkspacePanel panel, boolean visible) {
		setWorkspacePanelVisibilityFlag(panel, visible);
		if (workspaceCollapsed) {
			if (visible) collapsedWorkspacePanels.remove(panel);
			else collapsedWorkspacePanels.add(panel);
			if (collapsedWorkspacePanels.isEmpty()) workspaceCollapsed = false;
		} else if (!anyWorkspacePanelVisible(hierarchyVisible, inspectorVisible, animationVisible)) {
			workspaceCollapsed = true;
			collapsedWorkspacePanels.addAll(EnumSet.allOf(WorkspacePanel.class));
		}
		rebuildWorkspaceLayout();
		setStatus(panel.localizedLabel() + (visible
			? I18n.t("已显示", " shown") : I18n.t("已隐藏", " hidden")), false);
		saveWorkspacePreferences();
	}

	private void toggleWorkspacePanels() {
		captureWorkspaceSplitAmounts();
		if (workspaceCollapsed) {
			for (WorkspacePanel panel : collapsedWorkspacePanels) setWorkspacePanelVisibilityFlag(panel, true);
			collapsedWorkspacePanels.clear();
			workspaceCollapsed = false;
		} else {
			collapsedWorkspacePanels.clear();
			for (WorkspacePanel panel : WorkspacePanel.values()) {
				if (isWorkspacePanelVisible(panel)) collapsedWorkspacePanels.add(panel);
			}
			for (WorkspacePanel panel : collapsedWorkspacePanels) setWorkspacePanelVisibilityFlag(panel, false);
			workspaceCollapsed = true;
		}
		rebuildWorkspaceLayout();
		setStatus(workspaceCollapsed
			? I18n.t("所有界面已收起", "All panels hidden")
			: I18n.t("收起的界面已放出", "Collapsed panels restored"), false);
		saveWorkspacePreferences();
	}

	private void setWorkspacePanelVisibilityFlag(WorkspacePanel panel, boolean visible) {
		switch (panel) {
		case HIERARCHY -> hierarchyVisible = visible;
		case INSPECTOR -> inspectorVisible = visible;
		case ANIMATION -> animationVisible = visible;
		}
	}

	private void refreshWorkspaceRestoreButtons() {
		if (workspaceRestoreBar == null) return;
		workspaceRestoreBar.clear();
		for (WorkspacePanel panel : WorkspacePanel.values()) {
			TextButton restoreButton = workspaceRestoreButtons.get(panel);
			if (restoreButton == null) continue;
			restoreButton.setText(panel.localizedRestoreLabel());
			if (!isWorkspacePanelVisible(panel))
				workspaceRestoreBar.add(restoreButton).height(28).minWidth(52).padRight(3);
		}
		if (collapseWorkspaceBtn != null) {
			collapseWorkspaceBtn.setText(workspaceCollapseButtonLabel(workspaceCollapsed));
			collapseWorkspaceBtn.setDisabled(false);
		}
		workspaceRestoreBar.invalidateHierarchy();
	}

	private boolean isWorkspacePanelVisible(WorkspacePanel panel) {
		return switch (panel) {
		case HIERARCHY -> hierarchyVisible;
		case INSPECTOR -> inspectorVisible;
		case ANIMATION -> animationVisible;
		};
	}

	private void resetWorkspaceLayout() {
		captureWorkspaceSplitAmounts();
		hierarchyVisible = true;
		inspectorVisible = true;
		animationVisible = true;
		workspaceCollapsed = false;
		collapsedWorkspacePanels.clear();
		workspaceSplitAmount = DEFAULT_WORKSPACE_SPLIT;
		navigationSplitAmount = DEFAULT_NAVIGATION_SPLIT;
		inspectorSplitAmount = DEFAULT_INSPECTOR_SPLIT;
		workspace = null;
		navigationSplit = null;
		inspectorSplit = null;
		rebuildWorkspaceLayout();
		setStatus(I18n.t("界面布局已重置", "Workspace layout reset"), false);
		saveWorkspacePreferences();
	}

	private void rebuildWorkspaceLayout() {
		if (workspaceHost == null || viewportPanelHost == null) return;
		captureWorkspaceSplitAmounts();
		workspaceHost.setActor(null);

		Actor center = viewportPanelHost;
		if (inspectorVisible) {
			inspectorSplit = new SplitPane(center, inspectorPanelHost, false, kit.splitStyle);
			inspectorSplit.setMinSplitAmount(MIN_INSPECTOR_SPLIT);
			inspectorSplit.setMaxSplitAmount(MAX_INSPECTOR_SPLIT);
			inspectorSplit.setSplitAmount(inspectorSplitAmount);
			center = inspectorSplit;
		} else {
			inspectorSplit = null;
		}

		Actor body = center;
		if (hierarchyVisible) {
			navigationSplit = new SplitPane(hierarchyPanelHost, center, false, kit.splitStyle);
			navigationSplit.setMinSplitAmount(MIN_NAVIGATION_SPLIT);
			navigationSplit.setMaxSplitAmount(MAX_NAVIGATION_SPLIT);
			navigationSplit.setSplitAmount(navigationSplitAmount);
			body = navigationSplit;
		} else {
			navigationSplit = null;
		}

		Actor content = body;
		if (animationVisible) {
			workspace = new SplitPane(body, animationPanelHost, true, kit.splitStyle);
			workspace.setMinSplitAmount(MIN_WORKSPACE_SPLIT);
			workspace.setMaxSplitAmount(MAX_WORKSPACE_SPLIT);
			workspace.setSplitAmount(workspaceSplitAmount);
			content = workspace;
		} else {
			workspace = null;
		}
		workspaceHost.setActor(content);
		workspaceHost.invalidateHierarchy();
		refreshWorkspaceRestoreButtons();
		updateWorkspaceCursor(true);
	}

	private void captureWorkspaceSplitAmounts() {
		if (workspace != null) workspaceSplitAmount = normalizeWorkspaceSplit(workspace.getSplitAmount());
		if (navigationSplit != null)
			navigationSplitAmount = normalizeNavigationSplit(navigationSplit.getSplitAmount());
		if (inspectorSplit != null)
			inspectorSplitAmount = normalizeInspectorSplit(inspectorSplit.getSplitAmount());
	}

	private void updateWorkspaceCursor(boolean blocked) {
		if (Gdx.graphics == null) return;
		SystemCursor cursor = workspaceCursor(blocked,
			workspace != null && workspace.isCursorOverHandle(),
			navigationSplit != null && navigationSplit.isCursorOverHandle(),
			inspectorSplit != null && inspectorSplit.isCursorOverHandle());
		if (cursor == currentWorkspaceCursor) return;
		currentWorkspaceCursor = cursor;
		Gdx.graphics.setSystemCursor(cursor);
	}

	private void executeProjectCommand(ProjectCommand command) {
		switch (command) {
		case NEW_PROJECT -> showNewDocumentDialog("新建项目", "project", false);
		case OPEN_PROJECT -> requestOpen();
		case IMPORT_PROJECT -> requestSpineImport();
		case IMPORT_ANIMATIONS -> requestAnimationImport();
		case IMPORT_IMAGES -> importImagesAndPackAtlas();
		case IMPORT_PSD -> importPsdAndPackAtlas();
		case RELOAD_IMAGES -> reloadImages();
		case DIAGNOSTICS -> showProjectDiagnostics();
		case RECENT_PROJECTS -> showRecentProjects();
		case SAVE_PROJECT -> saveNativeProject(false, null, null);
		case SAVE_PROJECT_AS -> saveNativeProject(true, null, null);
		case PACKAGE_PROJECT -> packageProject();
		case NEW_SKELETON -> showNewDocumentDialog("新建骨架", "skeleton", true);
		case MANAGE_SKELETONS -> showProjectSkeletons();
		case EXPORT_PROJECT -> exportProject();
		case SETTINGS -> showSettings();
		}
	}

	private void showProjectDiagnostics() {
		finishWorkspaceTextEdit();
		ProjectDiagnostics.Report report = ProjectDiagnostics.inspect(doc);
		Actor previousFocus = stage.getKeyboardFocus();
		Window window = kit.window("项目诊断");
		window.setModal(true);
		kit.padWindow(window, 10f);
		Label summary = new Label("错误 " + report.errors() + " · 警告 " + report.warnings()
			+ " · 共 " + report.issues().size() + " 项", kit.labelStyle);
		summary.setColor(report.errors() > 0 ? kit.palette().errorText()
			: report.warnings() > 0 ? kit.palette().accent() : kit.palette().text());
		window.add(summary).width(540f).left().pad(4f).row();
		Table issues = new Table();
		issues.top().left();
		for (ProjectDiagnostics.Issue issue : report.issues()) {
			String prefix = switch (issue.severity()) {
			case ERROR -> "[错误] ";
			case WARNING -> "[警告] ";
			case INFO -> "[信息] ";
			};
			Label label = new Label(prefix + (issue.fixable() ? "[可修复] " : "") + issue.message(), kit.dialogBodyStyle);
			label.setWrap(true);
			if (issue.severity() == ProjectDiagnostics.Severity.ERROR) label.setColor(kit.palette().errorText());
			else if (issue.severity() == ProjectDiagnostics.Severity.WARNING) label.setColor(kit.palette().accent());
			issues.add(label).width(520f).growX().left().pad(4f, 3f, 7f, 3f).row();
		}
		ScrollPane scroll = new ScrollPane(issues, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		window.add(scroll).width(540f).height(330f).grow().pad(4f).row();
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousFocus);
		TextButton done = button("关闭", close);
		TextButton fixAll = button("修复全部 (" + report.fixable() + ")", () -> {
			List<String> preview = ProjectDiagnostics.previewFixes(doc);
			if (preview.isEmpty()) return;
			UiDialogs.confirm(stage, kit, "修复项目问题", String.join("\n", preview), "修复", () -> {
				ProjectDiagnostics.FixResult result = ProjectDiagnostics.fixAll(doc);
				if (!result.changed()) return;
				history.record("修复项目诊断问题",
					() -> ProjectDiagnostics.restoreFixes(doc, result.before()),
					() -> ProjectDiagnostics.restoreFixes(doc, result.after()));
				close.run();
				setStatus("已修复 " + result.actions().size() + " 个项目问题", false);
				showProjectDiagnostics();
			});
		});
		fixAll.setDisabled(report.fixable() == 0);
		Table actions = new Table();
		actions.add().growX();
		actions.add(fixAll).width(126f).height(30f).padRight(7f);
		actions.add(done).width(86f).height(30f);
		window.add(actions).growX().pad(6f, 4f, 2f, 4f);
		addModalKeyActions(window, close, close);
		showCentered(window);
	}

	private void setTool(EditorTool tool) {
		EditorAppState.tool = tool;
		syncCommandState();
	}

	private void togglePoseMode() {
		doc.setPoseMode(!doc.poseMode);
		propsPanel.refresh();
		attachPanel.refresh();
		syncCommandState();
	}

	private void modeChanged() {
		propsPanel.refresh();
		attachPanel.refresh();
		syncCommandState();
	}

	private void undo() {
		try {
			if (history.undo()) setStatus("已撤销：" + history.redoLabel(), false);
		} catch (RuntimeException error) {
			history.clear();
			showError("撤销失败，历史已清空", error);
		}
	}

	private void redo() {
		try {
			if (history.redo()) setStatus("已重做：" + history.undoLabel(), false);
		} catch (RuntimeException error) {
			history.clear();
			showError("重做失败，历史已清空", error);
		}
	}

	private void showNewDocumentDialog(String title, String initialName, boolean addToProject) {
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window(I18n.t(title));
		window.setModal(true);
		kit.padWindow(window, 10f);
		TextField nameField = new TextField(initialName, kit.textFieldStyle);
		nameField.setMaxLength(64);
		TextButton create = new TextButton(I18n.t("创建", "Create"), kit.accentButton);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);
		Runnable submit = () -> {
			String name = nameField.getText() == null ? "" : nameField.getText().trim();
			if (name.isEmpty()) return;
			close.run();
			if (addToProject) createBlankProject(name, true);
			else runAfterUnsavedConfirmation(() -> createBlankProject(name, false));
		};
		nameField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				create.setDisabled(nameField.getText() == null || nameField.getText().trim().isEmpty());
			}
		});
		create.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { if (!create.isDisabled()) submit.run(); }
		});
		TextButton cancel = button(I18n.t("取消", "Cancel"), close);
		addModalKeyActions(window, submit, close);
		window.row();
		window.add(new Label(I18n.t("名称", "Name"), kit.labelStyle)).left().pad(6, 6, 2, 6).row();
		window.add(nameField).width(320).height(28).pad(2, 6, 8, 6).row();
		Table buttons = new Table();
		buttons.add(create).width(86).height(28).padRight(6);
		buttons.add(cancel).width(86).height(28);
		window.add(buttons).right().pad(6);
		showCentered(window);
		stage.setKeyboardFocus(nameField);
		nameField.selectAll();
	}

	private void createBlankProject(String name, boolean addToProject) {
		SkeletonDocument candidate = null;
		try {
			candidate = blankProjectDocument(name);
			if (addToProject && skeletonProject.size() > 0) {
				if (treePanel != null) treePanel.persistViewState();
				if (timelinePanel != null) timelinePanel.persistViewState();
				addProjectSkeleton(new SkeletonProject.LoadedSkeleton(name, candidate), true);
				candidate = null;
				clearHistoryAndRebuildPanels();
				viewport.refreshTextureFiltering();
				history.markUnsaved();
				Gdx.app.postRunnable(viewport::frameAll);
				setStatus("已添加骨架 " + name, false);
			} else {
				saveCurrentSpineProjectState();
				replaceProjectSkeletons(List.of(new SkeletonProject.LoadedSkeleton(name, candidate)));
				candidate = null;
				showAllSkeletons = DEFAULT_SHOW_ALL_SKELETONS;
				finishLoad("已新建 " + name);
				history.markUnsaved();
			}
			syncCommandState();
		} catch (RuntimeException error) {
			if (candidate != null) candidate.dispose();
			showError("新建项目失败", error);
		}
	}

	static SkeletonDocument blankProjectDocument(String name) {
		if (name == null || name.isBlank()) throw new IllegalArgumentException("项目名称不能为空");
		String normalizedName = name.trim();
		SkeletonData data = new SkeletonData();
		data.setName(normalizedName);
		data.setVersion("4.3.0");
		data.setFps(30f);
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		root.setVisible(true);
		data.getBones().add(root);
		Skin defaultSkin = new Skin("default");
		data.getSkins().add(defaultSkin);
		data.setDefaultSkin(defaultSkin);
		Skeleton skeleton = new Skeleton(data);
		skeleton.setSkin(defaultSkin);
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(new TextureAtlas(), data, skeleton, "new:" + normalizedName, "", null);
		initAnimationState(document);
		return document;
	}

	private void requestOpen() {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		showOpenFileDialog();
	}

	private void requestSpineImport() {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		runAfterUnsavedConfirmation(this::showSpineImportDialog);
	}

	private void requestAnimationImport() {
		if (doc.data == null) {
			setStatus("请先打开或新建目标骨架", true);
			return;
		}
		if (documentIoInProgress() || spineImportInProgress) {
			setStatus("请先等待当前保存、打包或 Spine 导入任务结束", false);
			return;
		}
		if (fileDialogOpen || animationImportInProgress) {
			setStatus("动画文件选择或读取正在进行", false);
			return;
		}
		fileDialogOpen = true;
		setStatus("正在选择动画 Runtime 数据...", false);
		try {
			DesktopLauncher.chooseAnimationFileAsync(lastOpenDirectory, this::animationFileDialogCompleted);
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("动画文件对话框失败", error);
		}
	}

	private void animationFileDialogCompleted(String path, Throwable error) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("动画文件对话框失败", error);
				return;
			}
			if (path == null || path.isBlank()) return;
			beginAnimationSourceLoad(path);
		});
	}

	private void beginAnimationSourceLoad(String path) {
		long generation = ++animationImportGeneration;
		animationImportInProgress = true;
		setStatus("正在读取动画数据；旧版本会通过官方 Spine 转换，原文件不会修改", false);
		Thread worker = new Thread(() -> {
			SkeletonData source = null;
			Throwable failure = null;
			try {
				FileHandle file = new FileHandle(new File(path));
				String version = SkeletonLoader.declaredRuntimeVersion(file);
				if (version != null && !version.isBlank() && SpineVersionPolicy.requiresConversion(version)) {
					try (SpineProjectImporter.PreparedProject prepared =
						SpineProjectImporter.prepareRuntimeConversion(file)) {
						source = SkeletonLoader.loadAnimationSource(
							new FileHandle(prepared.skeletonFile().toFile()));
					}
				} else {
					source = SkeletonLoader.loadAnimationSource(file);
				}
			} catch (Throwable caught) {
				failure = caught;
			}
			SkeletonData result = source;
			Throwable completedFailure = failure;
			if (!postToRenderThread(() -> animationSourceLoaded(generation, path, result, completedFailure)))
				animationImportInProgress = false;
		}, "OpenSpine-animation-import");
		worker.setDaemon(true);
		animationImportThread = worker;
		try {
			worker.start();
		} catch (RuntimeException error) {
			animationImportThread = null;
			animationImportInProgress = false;
			showError("无法启动动画数据读取", error);
		}
	}

	private void animationSourceLoaded(long generation, String path, SkeletonData source, Throwable failure) {
		if (generation != animationImportGeneration || disposed) return;
		animationImportInProgress = false;
		animationImportThread = null;
		if (failure != null) {
			showError("读取动画数据失败", failure);
			return;
		}
		if (source == null || source.getAnimations().isEmpty()) {
			showMessage("导入动画", "所选文件不包含动画");
			return;
		}
		rememberDirectory(path, false);
		showAnimationImportSelection(path, source);
	}

	private void showAnimationImportSelection(String path, SkeletonData source) {
		Actor previousFocus = stage.getKeyboardFocus();
		Window window = kit.window("导入动画");
		window.setModal(true);
		kit.padWindow(window, 14f);
		Label message = new Label("从 " + new File(path).getName() + " 导入到当前骨架。所有目标按名称严格匹配。",
			kit.dialogBodyStyle);
		message.setWrap(true);
		SelectBox<String> choices = kit.selectBox();
		Array<String> names = new Array<>();
		names.add("全部动画（" + source.getAnimations().size + "）");
		for (Animation animation : source.getAnimations()) names.add(animation.getName());
		choices.setItems(names);
		SelectBox<String> conflicts = kit.selectBox();
		conflicts.setItems("同名时重命名", "同名时替换", "同名时跳过");
		TextButton confirm = new TextButton("导入", kit.accentButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousFocus);
		};
		Runnable accept = () -> {
			if (closed[0] || choices.getSelectedIndex() < 0) return;
			List<Animation> selected = new ArrayList<>();
			if (choices.getSelectedIndex() == 0) {
				for (Animation animation : source.getAnimations()) selected.add(animation);
			} else selected.add(source.getAnimations().get(choices.getSelectedIndex() - 1));
			AnimationEditor.AnimationImportConflict conflict = switch (conflicts.getSelectedIndex()) {
			case 1 -> AnimationEditor.AnimationImportConflict.REPLACE;
			case 2 -> AnimationEditor.AnimationImportConflict.SKIP;
			default -> AnimationEditor.AnimationImportConflict.RENAME;
			};
			try {
				AnimationEditor.AnimationImportResult result = timelinePanel.importAnimations(source, selected, conflict);
				close.run();
				setStatus("已导入 " + result.changes().size() + " 个动画"
					+ (result.replaced() == 0 ? "" : "，替换 " + result.replaced() + " 个")
					+ (result.skipped() == 0 ? "" : "，跳过 " + result.skipped() + " 个"), false);
			} catch (RuntimeException importError) {
				setStatus("无法导入动画：" + importError.getMessage(), true);
			}
		};
		confirm.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { accept.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (keycode == Input.Keys.ESCAPE) { close.run(); return true; }
				if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
					accept.run(); return true;
				}
				return false;
			}
		});
		float width = Math.min(520f, stage.getWidth() - 64f);
		window.add(message).width(width).left().row();
		Table selection = new Table();
		selection.add(new Label("范围", kit.labelStyle)).width(64f).left();
		selection.add(choices).growX().height(30f).row();
		selection.add(new Label("冲突", kit.labelStyle)).width(64f).left().padTop(7f);
		selection.add(conflicts).growX().height(30f).padTop(7f).row();
		window.add(selection).width(width).growX().padTop(12f).row();
		Table actions = new Table();
		actions.add().growX();
		actions.add(cancel).width(84f).height(30f).padRight(8f);
		actions.add(confirm).width(96f).height(30f);
		window.add(actions).growX().padTop(14f).row();
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(choices);
	}

	private void showOpenFileDialog() {
		if (fileDialogOpen) return;
		fileDialogOpen = true;
		setStatus("正在选择骨架文件...", false);
		try {
			DesktopLauncher.chooseFileAsync(lastOpenDirectory, this::openFileDialogCompleted);
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("打开文件对话框失败", error);
		}
	}

	private void showSpineImportDialog() {
		if (fileDialogOpen) return;
		fileDialogOpen = true;
		setStatus("正在选择 Spine 工程...", false);
		try {
			DesktopLauncher.chooseSpineProjectAsync(lastOpenDirectory, this::openFileDialogCompleted);
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("打开文件对话框失败", error);
		}
	}

	private void openFileDialogCompleted(String path, Throwable error) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("打开文件对话框失败", error);
				return;
			}
			if (path != null && !path.isEmpty()) openSelectedPath(path);
		});
	}

	private void openSelectedPath(String path) {
		if (shouldConfirmBeforeOpen(path, hasPersistentDocument()))
			runAfterUnsavedConfirmation(() -> loadFile(path));
		else loadFile(path);
	}

	static boolean shouldConfirmBeforeOpen(String path, boolean hasPersistentDocument) {
		return SkeletonLoader.isSpineProjectFileName(path) || isProjectPackagePath(path) || !hasPersistentDocument;
	}

	private static boolean isProjectPackagePath(String path) {
		if (path == null || path.isBlank()) return false;
		try {
			Path file = Path.of(path).getFileName();
			return file != null && ProjectPackageManifest.isManifestFileName(file.toString());
		} catch (RuntimeException invalidPath) { return false; }
	}

	private void showRecentProjects() {
		if (recentProjects.isEmpty()) {
			showMessage("最近项目", "暂无最近打开的项目");
			return;
		}
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("最近项目");
		window.setModal(true);
		kit.padWindow(window, 8f);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);
		for (String path : List.copyOf(recentProjects)) {
			TextButton recent = button(recentProjectLabel(path), () -> {
				close.run();
				if (!new File(path).isFile()) {
					recentProjects.remove(path);
					setStatus("最近项目不存在：" + path, true);
					return;
				}
				openSelectedPath(path);
			});
			window.row();
			window.add(recent).width(520).height(30).pad(2).left();
		}
		TextButton cancel = button("关闭", close);
		addModalKeyActions(window, () -> {}, close);
		window.row();
		window.add(cancel).width(86).height(28).right().pad(8, 2, 2, 2);
		showCentered(window);
	}

	static String recentProjectLabel(String path) {
		if (path == null || path.isBlank()) return "未知项目";
		String normalized = path.trim();
		if (normalized.length() <= 64) return normalized;
		return "..." + normalized.substring(normalized.length() - 61);
	}

	/** Native window close callback. Active dialogs and dirty documents keep the application open. */
	public boolean closeRequested() {
		finishWorkspaceTextEdit();
		if (fileDialogOpen) {
			if (Gdx.app != null)
				Gdx.app.postRunnable(() -> setStatus("请先关闭文件选择窗口", false));
			return false;
		}
		if (documentIoInProgress()) {
			if (Gdx.app != null) Gdx.app.postRunnable(() -> cancelDocumentIo(true));
			return false;
		}
		if (animationRenderer != null) {
			if (Gdx.app != null) Gdx.app.postRunnable(() -> cancelAnimationRender(true));
			return false;
		}
		if (exitConfirmed || !skeletonProject.isDirty()) return true;
		if (!exitPromptOpen && Gdx.app != null) {
			exitPromptOpen = true;
			Gdx.app.postRunnable(this::showExitPrompt);
		}
		return false;
	}

	/** Native file-drop callback. The first supported project or skeleton file is opened. */
	public void filesDropped(String[] files) {
		String path = firstSupportedDrop(files);
		postToRenderThread(() -> {
			if (fileDialogOpen) {
				setStatus("请先关闭文件选择窗口", false);
				return;
			}
			if (path == null) {
				setStatus("仅支持拖放 .spine、.json 或 .skel 文件", true);
				return;
			}
			openSelectedPath(path);
		});
	}

	static String firstSupportedDrop(String[] files) {
		if (files == null) return null;
		for (String file : files) {
			if (file == null || file.isBlank()) continue;
			if (SkeletonLoader.isSupportedFileName(file)) return file;
		}
		return null;
	}

	private void runAfterUnsavedConfirmation(Runnable action) {
		finishWorkspaceTextEdit();
		if (!skeletonProject.isDirty()) { action.run(); return; }
		showUnsavedProjectPrompt("未保存的修改", action, null);
	}

	private void showUnsavedProjectPrompt(String title, Runnable action, Runnable stayAction) {
		if (usesNativeProjectWorkflow()) {
			showUnsavedChangesPrompt(title, "工程中的骨架或成员顺序有未保存的修改。是否保存整个工程？", action, stayAction);
			return;
		}
		int dirtyIndex = skeletonProject.firstDirtyIndex();
		if (dirtyIndex < 0) { action.run(); return; }
		if (dirtyIndex != skeletonProject.activeIndex()) switchSkeleton(dirtyIndex);
		String name = skeletonProject.activeName();
		String message = skeletonProject.size() > 1
			? "骨架“" + name + "”有未保存的修改。保存后将继续检查工程中的其他骨架。"
			: "是否先保存对当前骨架的修改？";
		showUnsavedChangesPrompt(title, message, action, stayAction);
	}

	private void finishWorkspaceTextEdit() {
		if (stage == null) return;
		Actor focus = stage.getKeyboardFocus();
		if (shouldFinishWorkspaceTextEdit(focus instanceof TextField, hasModalWindow()))
			stage.setKeyboardFocus(null);
	}

	private boolean loadExampleNow() {
		SkeletonDocument character = new SkeletonDocument();
		SkeletonDocument robot = new SkeletonDocument();
		try {
			SkeletonLoader.loadFromJsonString(character, ExampleSkeleton.JSON, ExampleSkeleton.createAtlas());
			bindExampleResources(character, false);
			initAnimationState(character);
			SkeletonLoader.loadFromJsonString(robot, ExampleSkeleton.ROBOT_JSON, ExampleSkeleton.createRobotAtlas());
			bindExampleResources(robot, true);
			initAnimationState(robot);
			saveCurrentSpineProjectState();
			replaceProjectSkeletons(List.of(
				new SkeletonProject.LoadedSkeleton(I18n.t("示例角色", "Example Character"), character),
				new SkeletonProject.LoadedSkeleton(I18n.t("示例机器人", "Example Robot"), robot)));
			character = null;
			robot = null;
			showAllSkeletons = DEFAULT_SHOW_ALL_SKELETONS;
			finishLoad(I18n.t("已加载内置示例（2 个骨架）", "Built-in example loaded (2 skeletons)"));
			return true;
		} catch (IOException | RuntimeException e) {
			if (character != null) character.dispose();
			if (robot != null) robot.dispose();
			showError("加载示例失败", e);
			return false;
		}
	}

	private static void bindExampleResources(SkeletonDocument document, boolean alternate) throws IOException {
		ExampleSkeleton.PreparedResources resources = ExampleSkeleton.prepareResources(alternate);
		document.resourceDirectory = resources.directory().toString();
		document.atlasFileName = resources.atlasFile().getFileName().toString();
		document.ownSourceResources(resources::close);
	}

	private boolean loadFile(String path) {
		if (documentIoInProgress()) {
			setStatus("请先等待保存或打包任务结束", false);
			return false;
		}
		if (spineImportInProgress) {
			setStatus("已有 Spine 工程正在导入", false);
			return false;
		}
		int duplicate = skeletonProject.indexOfSourcePath(path);
		if (duplicate >= 0) {
			switchSkeleton(duplicate);
			setStatus("该文件已经打开，已切换到对应标签", false);
			return true;
		}
		saveCurrentSpineProjectState();
		if (isProjectPackagePath(path)) return loadProjectPackage(path);
		if (SkeletonLoader.isSpineProjectFileName(path)) return beginSpineProjectLoad(path);
		FileHandle file = Gdx.files.absolute(path);
		try {
			String version = SkeletonLoader.declaredRuntimeVersion(file);
			if (version != null && !version.isBlank() && SpineVersionPolicy.requiresConversion(version))
				return beginRuntimeConversionLoad(path, version);
		} catch (RuntimeException error) {
			showError("打开骨架失败", error);
			return false;
		}
		List<String> atlasCandidates = SkeletonLoader.atlasCandidates(file);
		if (atlasCandidates.size() > 1) {
			showAtlasSelection(path, atlasCandidates);
			return true;
		}
		return loadRuntimeFile(path, atlasCandidates.isEmpty() ? null : atlasCandidates.get(0));
	}

	private boolean loadProjectPackage(String path) {
		if (disposed || documentIoInProgress()) return false;
		Path manifest;
		try {
			manifest = Path.of(path).toAbsolutePath().normalize();
		} catch (RuntimeException error) {
			showError("打开打包项目失败", error);
			return false;
		}
		long generation = ++documentIoGeneration;
		CancellationToken cancellation = new CancellationToken();
		List<ProjectSaveState> previousProject = projectSaveState(skeletonProject);
		documentIoCancellation = cancellation;
		showDocumentIoProgress("打开 OpenSpine 工程", "正在检查工程与图片、音频资源", manifest);
		setStatus("正在后台准备工程 " + manifest.getFileName(), false);
		Thread worker = new Thread(() -> {
			ProjectPackageManifest.PreparedPackage prepared = null;
			Throwable failure = null;
			try { prepared = ProjectPackageManifest.prepare(manifest, cancellation); }
			catch (Throwable error) { failure = error; }
			ProjectPackageManifest.PreparedPackage result = prepared;
			Throwable completedFailure = failure;
			// Preparation owns only Java metadata: a shutdown or undelivered callback cannot leak GL/files.
			postToRenderThread(() -> projectPackagePrepared(generation, cancellation, previousProject,
				result, completedFailure));
		}, "openspine-project-open");
		worker.setDaemon(true);
		documentIoThread = worker;
		try {
			worker.start();
			return true;
		} catch (RuntimeException error) {
			documentIoThread = null;
			documentIoCancellation = null;
			hideDocumentIoProgress();
			showError("无法启动后台工程检查", error);
			return false;
		}
	}

	private void projectPackagePrepared(long generation, CancellationToken cancellation, List<ProjectSaveState> previousProject,
		ProjectPackageManifest.PreparedPackage prepared, Throwable preparationFailure) {
		if (generation != documentIoGeneration || cancellation != documentIoCancellation || disposed) return;
		ProjectPackageManifest.LoadedPackage loaded = null;
		boolean adopted = false;
		Throwable failure = null;
		boolean cancelled = false;
		try {
			// Cancellation after worker completion must still prevent the first texture allocation.
			cancellation.checkCancelled();
			if (preparationFailure != null) throw preparationFailure;
			if (!previousProject.equals(projectSaveState(skeletonProject)))
				throw new IOException("检查期间当前工程已改变，已保留当前工程，请重新打开");
			if (documentIoProgressStatus != null) documentIoProgressStatus.setText("正在加载工程纹理");
			loaded = ProjectPackageManifest.load(prepared, cancellation);
			cancellation.checkCancelled();
			if (!previousProject.equals(projectSaveState(skeletonProject)))
				throw new IOException("打开期间当前工程已改变，已保留当前工程，请重新打开");
			replaceProjectSkeletons(loaded.skeletons());
			adopted = true;
			nativeProjectPath = prepared.manifestPath();
			nativeProjectStamp = prepared.stamp();
			if (loaded.activeIndex() != skeletonProject.activeIndex()) switchProjectSkeleton(loaded.activeIndex());
			showAllSkeletons = loaded.showAll();
			String path = prepared.manifestPath().toString();
			restoreProjectState(path);
			rememberDirectory(path, false);
			rememberRecentProject(path);
		} catch (Throwable error) {
			cancelled = error instanceof CancellationException || (!adopted && cancellation.isCancellationRequested());
			failure = error;
		} finally {
			if (!adopted && loaded != null)
				for (var item : loaded.skeletons()) item.document().dispose();
			documentIoThread = null;
			documentIoCancellation = null;
			hideDocumentIoProgress();
		}
		if (!adopted && doc.data == null) loadExampleNow();
		if (cancelled) setStatus("已取消打开工程", false);
		else if (failure != null) showError("打开打包项目失败", failure);
		else finishLoad("已打开 OpenSpine 工程（" + skeletonProject.size() + " 个骨架）");
	}

	private boolean loadRuntimeFile(String path) {
		return loadRuntimeFile(path, null);
	}

	private boolean loadRuntimeFile(String path, String preferredAtlasFileName) {
		int duplicate = skeletonProject.indexOfSourcePath(path);
		if (duplicate >= 0) {
			switchSkeleton(duplicate);
			setStatus("该文件已经打开，已切换到对应标签", false);
			return true;
		}
		SkeletonDocument candidate = new SkeletonDocument();
		try {
			FileHandle file = Gdx.files.absolute(path);
			SkeletonLoader.loadRuntime(candidate, file, file.parent(), preferredAtlasFileName);
			initAnimationState(candidate);
			String skeletonName = file.nameWithoutExtension();
			boolean append = hasPersistentDocument();
			if (append) {
				if (treePanel != null) treePanel.persistViewState();
				if (timelinePanel != null) timelinePanel.persistViewState();
				addProjectSkeleton(new SkeletonProject.LoadedSkeleton(skeletonName, candidate), true);
				candidate = null;
			} else {
				replaceProjectSkeletons(List.of(new SkeletonProject.LoadedSkeleton(skeletonName, candidate)));
				candidate = null;
			}
			showAllSkeletons = DEFAULT_SHOW_ALL_SKELETONS;
			ProjectStateStore.LoadResult stateResult = restoreProjectState(path);
			// A standalone sidecar may remember index 0. Keep the newly opened tab active.
			if (append && skeletonProject.activeIndex() != skeletonProject.size() - 1)
				switchProjectSkeleton(skeletonProject.size() - 1);
			rememberDirectory(path, false);
			rememberRecentProject(path);
			String stateNote = stateResult == ProjectStateStore.LoadResult.RESTORED ? " · 已恢复 OpenSpine 状态"
				: stateResult == ProjectStateStore.LoadResult.SOURCE_CHANGED ? " · 已忽略过期 OpenSpine 状态" : "";
			finishLoad((append ? "已添加 " : "已打开 ") + file.name() + stateNote);
			return true;
		} catch (RuntimeException e) {
			if (candidate != null) candidate.dispose();
			showError("打开骨架失败", e);
			return false;
		}
	}

	private boolean beginRuntimeConversionLoad(String path, String sourceVersion) {
		long generation = ++spineImportGeneration;
		spineImportInProgress = true;
		showSpineImportProgress(path, "转换 Runtime 数据", "正在通过官方 Spine 转换 " + sourceVersion + " → 4.3");
		setStatus("正在后台转换 " + new File(path).getName() + "；原文件不会修改", false);

		Thread worker = new Thread(() -> {
			SpineProjectImporter.PreparedProject prepared = null;
			Throwable failure = null;
			try {
				prepared = SpineProjectImporter.prepareRuntimeConversion(new FileHandle(new File(path)));
			} catch (RuntimeException error) {
				failure = error;
			}
			SpineProjectImporter.PreparedProject result = prepared;
			Throwable error = failure;
			if (!postToRenderThread(() -> runtimeConversionPrepared(generation, path, sourceVersion, result, error)))
				if (result != null) result.close();
		}, "openspine-runtime-convert");
		worker.setDaemon(true);
		spineImportThread = worker;
		try {
			worker.start();
			return true;
		} catch (RuntimeException error) {
			spineImportThread = null;
			spineImportInProgress = false;
			hideSpineImportProgress();
			showError("无法启动 Runtime 转换", error);
			return false;
		}
	}

	private void runtimeConversionPrepared(long generation, String path, String sourceVersion,
		SpineProjectImporter.PreparedProject prepared, Throwable failure) {
		if (generation != spineImportGeneration || disposed) {
			if (prepared != null) prepared.close();
			return;
		}
		spineImportInProgress = false;
		spineImportThread = null;
		hideSpineImportProgress();
		if (failure != null) {
			boolean fallback = doc.data == null && loadExampleNow();
			showError("转换 Runtime 数据失败", failure);
			if (fallback) setStatus("Runtime 转换失败，已加载内置示例", true);
			return;
		}
		installConvertedRuntime(path, sourceVersion, prepared);
	}

	private void installConvertedRuntime(String path, String sourceVersion,
		SpineProjectImporter.PreparedProject prepared) {
		SpineProjectImporter.LoadedSkeleton loaded = null;
		SkeletonDocument candidate = null;
		boolean installed = false;
		try {
			loaded = SpineProjectImporter.loadSelectedPrepared(prepared);
			candidate = loaded.document();
			initAnimationState(candidate);
			boolean append = hasPersistentDocument();
			String skeletonName = new File(path).getName();
			int extension = skeletonName.lastIndexOf('.');
			if (extension > 0) skeletonName = skeletonName.substring(0, extension);
			if (append) {
				if (treePanel != null) treePanel.persistViewState();
				if (timelinePanel != null) timelinePanel.persistViewState();
				addProjectSkeleton(new SkeletonProject.LoadedSkeleton(skeletonName, candidate), true);
			} else {
				replaceProjectSkeletons(List.of(new SkeletonProject.LoadedSkeleton(skeletonName, candidate)));
			}
			candidate = null;
			installed = true;
			showAllSkeletons = DEFAULT_SHOW_ALL_SKELETONS;
			ProjectStateStore.LoadResult stateResult = restoreProjectState(path);
			if (append && skeletonProject.activeIndex() != skeletonProject.size() - 1)
				switchProjectSkeleton(skeletonProject.size() - 1);
			rememberDirectory(path, false);
			rememberRecentProject(path);
			String stateNote = stateResult == ProjectStateStore.LoadResult.RESTORED ? " · 已恢复 OpenSpine 状态"
				: stateResult == ProjectStateStore.LoadResult.SOURCE_CHANGED ? " · 已忽略过期 OpenSpine 状态" : "";
			finishLoad((append ? "已转换并添加 " : "已转换并打开 ") + new File(path).getName()
				+ "（" + sourceVersion + " → 4.3；原文件未修改" + stateNote + "）");
		} catch (RuntimeException error) {
			if (!installed) {
				if (candidate != null) candidate.dispose();
				else if (prepared != null) prepared.close();
			}
			boolean fallback = !installed && doc.data == null && loadExampleNow();
			showError("打开转换后的 Runtime 数据失败", error);
			if (fallback) setStatus("Runtime 转换失败，已加载内置示例", true);
		}
	}

	private void showAtlasSelection(String path, List<String> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			showError("打开骨架失败", new IllegalStateException("没有可选择的图集文件"));
			return;
		}
		Actor previousFocus = stage.getKeyboardFocus();
		Window window = kit.window("选择图集");
		window.setModal(true);
		window.setMovable(true);
		kit.padWindow(window, 14f);
		Label message = new Label("检测到多个候选图集，请选择与当前骨架对应的图集：", kit.dialogBodyStyle);
		message.setWrap(true);
		SelectBox<String> choices = kit.selectBox();
		Array<String> labels = new Array<>();
		for (String candidate : candidates) labels.add(candidate);
		choices.setItems(labels);
		TextButton confirm = new TextButton("打开所选", kit.accentButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		boolean[] closed = {false};
		Runnable cancelAction = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousFocus);
			setStatus("已取消图集选择", false);
		};
		Runnable confirmAction = () -> {
			if (closed[0]) return;
			int index = choices.getSelectedIndex();
			if (index < 0 || index >= candidates.size()) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousFocus);
			loadRuntimeFile(path, candidates.get(index));
		};
		confirm.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { confirmAction.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { cancelAction.run(); }
		});
		window.add(message).width(Math.min(520f, stage.getWidth() - 64f)).left().row();
		window.add(choices).growX().height(30f).padTop(12f).row();
		Table actions = new Table();
		actions.add().growX();
		actions.add(cancel).width(84f).height(30f).padRight(8f);
		actions.add(confirm).width(96f).height(30f);
		window.add(actions).growX().padTop(14f).row();
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (keycode == Input.Keys.ESCAPE) { cancelAction.run(); return true; }
				if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
					confirmAction.run(); return true;
				}
				return false;
			}
		});
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(choices);
	}

	/** Returns true once the current workspace represents a saved external document. */
	private boolean hasPersistentDocument() {
		for (SkeletonDocument document : skeletonProject.documents()) {
			if (isPersistentDocumentPath(document.savePath) || isPersistentDocumentPath(document.filePath)) return true;
		}
		return false;
	}

	private static boolean isPersistentDocumentPath(String path) {
		if (path == null || path.isBlank() || path.startsWith("builtin:") || path.startsWith("new:")) return false;
		try {
			Path source = Path.of(path).toAbsolutePath().normalize();
			return Files.isRegularFile(source) && SkeletonLoader.isSupportedFileName(source.toString());
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private boolean beginSpineProjectLoad(String path) {
		long generation = ++spineImportGeneration;
		spineImportInProgress = true;
		showSpineImportProgress(path);
		setStatus("正在后台转换 " + new File(path).getName(), false);

		Thread worker = new Thread(() -> {
			SpineProjectImporter.PreparedProject prepared = null;
			Throwable failure = null;
			try {
				prepared = SpineProjectImporter.prepare(new FileHandle(new File(path)));
			} catch (RuntimeException error) {
				failure = error;
			}
			SpineProjectImporter.PreparedProject result = prepared;
			Throwable error = failure;
			if (!postToRenderThread(() -> spineProjectPrepared(generation, path, result, error))) {
				// The application shut down before the import completed; release the prepared project.
				if (result != null) result.close();
			}
		}, "openspine-spine-import");
		worker.setDaemon(true);
		spineImportThread = worker;
		try {
			worker.start();
			return true;
		} catch (RuntimeException error) {
			spineImportThread = null;
			spineImportInProgress = false;
			hideSpineImportProgress();
			showError("无法启动 Spine 工程导入", error);
			return false;
		}
	}

	private void spineProjectPrepared(long generation, String path,
		SpineProjectImporter.PreparedProject prepared, Throwable failure) {
		if (generation != spineImportGeneration || disposed) {
			if (prepared != null) prepared.close();
			return;
		}
		spineImportInProgress = false;
		spineImportThread = null;
		hideSpineImportProgress();
		if (failure != null) {
			boolean fallback = doc.data == null && loadExampleNow();
			showError("导入 Spine 工程失败", failure);
			if (fallback) setStatus("Spine 工程导入失败，已加载内置示例", true);
			return;
		}
		if (prepared.requiresRuntimeSelection()) {
			showSpineRuntimeSelection(path, prepared);
			return;
		}
		installPreparedSpineProject(path, prepared);
	}

	private void installPreparedSpineProject(String path, SpineProjectImporter.PreparedProject prepared) {
		List<SpineProjectImporter.LoadedSkeleton> imported = null;
		boolean installed = false;
		try {
			imported = SpineProjectImporter.loadAllPrepared(prepared);
			List<SkeletonProject.LoadedSkeleton> skeletons = new ArrayList<>(imported.size());
			for (SpineProjectImporter.LoadedSkeleton loaded : imported) {
				initAnimationState(loaded.document());
				skeletons.add(new SkeletonProject.LoadedSkeleton(loaded.name(), loaded.document()));
			}
			replaceProjectSkeletons(skeletons);
			installed = true;
			showAllSkeletons = DEFAULT_SHOW_ALL_SKELETONS;
			ProjectStateStore.LoadResult stateResult = restoreProjectState(path);
			rememberDirectory(path, false);
			rememberRecentProject(path);
			String stateNote = stateResult == ProjectStateStore.LoadResult.RESTORED ? " · 已恢复 OpenSpine 状态"
				: stateResult == ProjectStateStore.LoadResult.SOURCE_CHANGED ? " · 已忽略过期 OpenSpine 状态" : "";
			finishLoad("已导入 " + new File(path).getName() + "（" + skeletons.size() + " 个骨架" + stateNote + "）");
		} catch (RuntimeException error) {
			if (!installed) {
				if (imported != null)
					for (SpineProjectImporter.LoadedSkeleton loaded : imported) loaded.document().dispose();
				if (prepared != null) prepared.close();
			}
			boolean fallback = !installed && doc.data == null && loadExampleNow();
			showError("导入 Spine 工程失败", error);
			if (fallback) setStatus("Spine 工程导入失败，已加载内置示例", true);
		}
	}

	private void showSpineRuntimeSelection(String path, SpineProjectImporter.PreparedProject prepared) {
		List<SpineProjectImporter.RuntimeCandidate> candidates = prepared.runtimeCandidates();
		if (candidates.isEmpty()) {
			prepared.close();
			showError("导入 Spine 工程失败", new IllegalStateException("没有可选择的 Runtime 骨架文件"));
			return;
		}
		Actor previousFocus = stage.getKeyboardFocus();
		Window window = kit.window("选择 Runtime 骨架");
		window.setModal(true);
		window.setMovable(true);
		kit.padWindow(window, 14f);
		Label message = new Label("Spine CLI 当前不可用，请确认要关联的同目录 Runtime 文件：", kit.dialogBodyStyle);
		message.setWrap(true);
		SelectBox<String> choices = kit.selectBox();
		Array<String> labels = new Array<>();
		for (SpineProjectImporter.RuntimeCandidate candidate : candidates)
			labels.add(candidate.name() + "  ·  " + candidate.sourcePath().getParent());
		choices.setItems(labels);
		TextButton confirm = new TextButton("导入所选", kit.accentButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		boolean[] closed = {false};
		Runnable cancelAction = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousFocus);
			prepared.close();
			setStatus("已取消 Spine Runtime 候选选择", false);
		};
		Runnable confirmAction = () -> {
			if (closed[0]) return;
			int index = choices.getSelectedIndex();
			if (index < 0 || index >= candidates.size()) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousFocus);
			try {
				prepared.selectRuntime(index);
				installPreparedSpineProject(path, prepared);
			} catch (RuntimeException error) {
				prepared.close();
				showError("导入 Spine 工程失败", error);
			}
		};
		confirm.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { confirmAction.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { cancelAction.run(); }
		});
		window.add(message).width(Math.min(520f, stage.getWidth() - 64f)).left().row();
		window.add(choices).growX().height(30f).padTop(12f).row();
		Table actions = new Table();
		actions.add().growX();
		actions.add(cancel).width(84f).height(30f).padRight(8f);
		actions.add(confirm).width(96f).height(30f);
		window.add(actions).growX().padTop(14f).row();
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (keycode == Input.Keys.ESCAPE) { cancelAction.run(); return true; }
				if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
					confirmAction.run(); return true;
				}
				return false;
			}
		});
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(choices);
	}

	private void showSpineImportProgress(String path) {
		showSpineImportProgress(path, "导入工程", "正在转换工程文件");
	}

	private void showSpineImportProgress(String path, String title, String message) {
		spineImportPreviousFocus = stage.getKeyboardFocus();
		Window window = kit.window(title);
		spineImportProgress = window;
		window.setModal(true);
		window.getTitleLabel().setAlignment(Align.left);
		window.getTitleTable().pad(5, 20, 5, 20);
		window.pad(40, 20, 18, 20);

		Label status = new Label(message, kit.labelStyle);
		status.setAlignment(Align.left);
		status.setColor(0.91f, 0.93f, 0.96f, 1f);
		Label fileName = new Label(new File(path).getName(), kit.labelStyle);
		fileName.setAlignment(Align.left);
		fileName.setColor(0.58f, 0.64f, 0.72f, 1f);
		fileName.setEllipsis(true);
		Actor progress = new IndeterminateProgressIndicator(kit.downDark, kit.accent);
		TextButton cancel = button("取消", () -> cancelSpineProjectLoad(true));
		addModalKeyActions(window, () -> {}, () -> cancelSpineProjectLoad(true));

		window.add(status).width(IMPORT_DIALOG_CONTENT_WIDTH).height(24).left().row();
		window.add(fileName).width(IMPORT_DIALOG_CONTENT_WIDTH).minWidth(0).height(22).left().padTop(2).row();
		window.add(progress).width(IMPORT_DIALOG_CONTENT_WIDTH).height(IMPORT_PROGRESS_HEIGHT).padTop(14).row();
		Table actions = new Table();
		actions.add().growX();
		actions.add(cancel).width(86).height(30);
		window.add(actions).width(IMPORT_DIALOG_CONTENT_WIDTH).padTop(16);
		showCentered(window);
	}

	private static final class IndeterminateProgressIndicator extends Actor {
		private final Drawable track;
		private final Drawable segment;
		private float phase = 0.18f;

		IndeterminateProgressIndicator(Drawable track, Drawable segment) {
			this.track = track;
			this.segment = segment;
		}

		@Override public void act(float delta) {
			super.act(delta);
			phase = (phase + Math.max(0f, delta) * 0.72f) % 1f;
		}

		@Override public void draw(Batch batch, float parentAlpha) {
			float width = getWidth();
			float height = getHeight();
			if (width <= 0f || height <= 0f) return;
			float oldR = batch.getColor().r;
			float oldG = batch.getColor().g;
			float oldB = batch.getColor().b;
			float oldA = batch.getColor().a;
			batch.setColor(oldR, oldG, oldB, oldA * parentAlpha);
			track.draw(batch, getX(), getY(), width, height);

			float segmentWidth = Math.max(64f, width * 0.26f);
			float rawStart = getX() - segmentWidth + (width + segmentWidth) * phase;
			float clippedStart = Math.max(getX(), rawStart);
			float clippedEnd = Math.min(getX() + width, rawStart + segmentWidth);
			if (clippedEnd > clippedStart)
				segment.draw(batch, clippedStart, getY(), clippedEnd - clippedStart, height);
			batch.setColor(oldR, oldG, oldB, oldA);
		}
	}

	private static final class DeterminateProgressIndicator extends Actor {
		private final Drawable track, fill;
		private float progress;

		DeterminateProgressIndicator(Drawable track, Drawable fill) {
			this.track = track;
			this.fill = fill;
		}

		void setProgress(float progress) {
			this.progress = Math.max(0f, Math.min(1f, progress));
		}

		@Override public void draw(Batch batch, float parentAlpha) {
			track.draw(batch, getX(), getY(), getWidth(), getHeight());
			float width = getWidth() * progress;
			if (width > 0f) fill.draw(batch, getX(), getY(), width, getHeight());
		}
	}

	private void hideSpineImportProgress() {
		Window window = spineImportProgress;
		Actor previousFocus = spineImportPreviousFocus;
		spineImportProgress = null;
		spineImportPreviousFocus = null;
		if (window != null) UiDialogs.closeModal(stage, window, previousFocus);
	}

	private void cancelSpineProjectLoad(boolean reportStatus) {
		if (!spineImportInProgress) return;
		spineImportInProgress = false;
		spineImportGeneration++;
		Thread worker = spineImportThread;
		spineImportThread = null;
		if (worker != null) worker.interrupt();
		hideSpineImportProgress();
		if (reportStatus) {
			if (doc.data == null) loadExampleNow();
			setStatus("已取消 Spine 工程导入", false);
		}
	}

	private void cancelAnimationImport() {
		animationImportInProgress = false;
		animationImportGeneration++;
		Thread worker = animationImportThread;
		animationImportThread = null;
		if (worker != null) worker.interrupt();
	}

	private void save() {
		save(null, null);
	}

	private void saveNativeProject(boolean choosePath, Runnable onSuccess, Runnable onIncomplete) {
		finishWorkspaceTextEdit();
		if (doc.data == null || skeletonProject.size() == 0) {
			setStatus("没有可保存的工程", true);
			runCallback(onIncomplete);
			return;
		}
		if (documentIoInProgress() || fileDialogOpen) {
			setStatus("请先等待当前文件任务结束", false);
			runCallback(onIncomplete);
			return;
		}
		if (!choosePath && nativeProjectPath != null) {
			saveNativeProjectTo(nativeProjectPath, onSuccess, onIncomplete);
			return;
		}
		Path suggestion = nativeProjectPath;
		if (suggestion == null) {
			String runtimeSuggestion = suggestedSavePath(doc.savePath, doc.filePath, lastSaveDirectory);
			Path runtime = Path.of(runtimeSuggestion).toAbsolutePath().normalize();
			String fileName = runtime.getFileName().toString();
			int extension = fileName.lastIndexOf('.');
			if (extension > 0) fileName = fileName.substring(0, extension);
			suggestion = runtime.resolveSibling(fileName + ".openspine-project.json");
		}
		fileDialogOpen = true;
		try {
			DesktopLauncher.chooseNativeProjectFileAsync(suggestion.toString(), (path, error) -> postToRenderThread(() -> {
				fileDialogOpen = false;
				if (disposed) return;
				if (error != null) {
					showError("工程保存对话框失败", error);
					runCallback(onIncomplete);
				} else if (path == null || path.isBlank()) runCallback(onIncomplete);
				else saveNativeProjectTo(Path.of(path), onSuccess, onIncomplete);
			}));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("工程保存对话框失败", error);
			runCallback(onIncomplete);
		}
	}

	private void saveNativeProjectTo(Path requested, Runnable onSuccess, Runnable onIncomplete) {
		try {
			Path target = requested.toAbsolutePath().normalize();
			if (isManagedGenerationPath(target)) throw new IllegalArgumentException("请将工程保存在资源目录之外");
			NativeProjectStore.FileStamp expected = target.equals(nativeProjectPath)
				? nativeProjectStamp : null;
			if (treePanel != null) treePanel.persistViewState();
			if (timelinePanel != null) timelinePanel.persistViewState();
			List<ProjectSaveState> captured = projectSaveState(skeletonProject);
			beginDocumentIo("保存工程", "保存整个工程", "正在保存全部骨架和资源", target,
				token -> NativeProjectStore.save(skeletonProject, showAllSkeletons, target,
					expected == null ? NativeProjectStore.inspect(target, token) : expected, token), result -> {
					List<SkeletonDocument> documents = skeletonProject.documents();
					nativeProjectPath = result.manifestPath();
					nativeProjectStamp = result.stamp();
					if (!captured.equals(projectSaveState(skeletonProject))) {
						setStatus("工程快照已保存，但随后又有修改，请再次保存", true);
						runCallback(onIncomplete);
						return;
					}
					for (int index = 0; index < documents.size(); index++) {
						SkeletonDocument document = documents.get(index);
						var saved = result.skeletons().get(index);
						document.filePath = saved.runtime().toString();
						document.savePath = saved.runtime().toString();
						document.resourceDirectory = saved.runtime().getParent().toString();
						document.atlasFileName = saved.atlas().isBlank() ? null : saved.atlas();
						document.markSaved();
						documentHistories.get(index).markSavedState();
					}
					skeletonProject.markStructureSaved();
					rememberDirectory(target.toString(), true);
					rememberRecentProject(target.toString());
					saveCurrentSpineProjectState();
					setStatus("已保存整个工程（" + documents.size() + " 个骨架）：" + target.getFileName(), false);
					syncCommandState();
					runCallback(onSuccess);
				}, onIncomplete);
		} catch (RuntimeException error) {
			showError("保存工程失败", error);
			runCallback(onIncomplete);
		}
	}

	static boolean isManagedGenerationPath(Path path) {
		if (path == null) return false;
		for (Path part : path.toAbsolutePath().normalize())
			if (NativeProjectStore.GENERATION_DIRECTORY.equalsIgnoreCase(part.toString())) return true;
		return false;
	}

	record ProjectSaveState(SkeletonData identity, String name, long revision) {}
	static List<ProjectSaveState> projectSaveState(SkeletonProject project) {
		List<ProjectSaveState> result = new ArrayList<>();
		List<SkeletonDocument> documents = project.documents();
		for (int index = 0; index < documents.size(); index++) {
			SkeletonDocument document = documents.get(index);
			result.add(new ProjectSaveState(document.data, project.nameAt(index), document.revision));
		}
		return List.copyOf(result);
	}

	private void save(Runnable onSuccess, Runnable onIncomplete) {
		finishWorkspaceTextEdit();
		if (usesNativeProjectWorkflow()) {
			saveNativeProject(false, onSuccess, onIncomplete);
			return;
		}
		if (doc.data == null) {
			setStatus("没有可保存的骨架数据", true);
			runCallback(onIncomplete);
			return;
		}
		if (documentIoInProgress()) {
			setStatus("已有保存或打包任务正在执行", false);
			runCallback(onIncomplete);
			return;
		}
		String sourceVersion = doc.data.getVersion();
		if (SpineVersionPolicy.wouldOverwriteIncompatibleSource(sourceVersion, doc.filePath,
			doc.savePath)) {
			UiDialogs.confirm(stage, kit, "不能覆盖原始数据",
				"当前数据版本为 " + sourceVersionLabel(sourceVersion)
					+ "，OpenSpine 输出会转换为 Spine 4.3.0，不能直接覆盖原始文件。请选择新路径另存副本。",
				"另存副本", () -> {
					versionWarningDocument = doc;
					versionWarningRevision = doc.revision;
					saveAs(onSuccess, onIncomplete);
				}, onIncomplete == null ? () -> { } : onIncomplete);
			return;
		}
		if (!ensureVersionCompatibility(() -> save(onSuccess, onIncomplete), onIncomplete)) return;
		if (doc.savePath == null || doc.savePath.isBlank()) {
			saveAs(onSuccess, onIncomplete);
			return;
		}
		saveTo(doc.savePath, onSuccess, onIncomplete);
	}

	private void saveAs() {
		saveAs(null, null);
	}

	private void saveAs(Runnable onSuccess, Runnable onIncomplete) {
		finishWorkspaceTextEdit();
		if (doc.data == null) {
			setStatus("没有可保存的骨架数据", true);
			runCallback(onIncomplete);
			return;
		}
		if (documentIoInProgress()) {
			setStatus("已有保存或打包任务正在执行", false);
			runCallback(onIncomplete);
			return;
		}
		if (!ensureVersionCompatibility(() -> saveAs(onSuccess, onIncomplete), onIncomplete)) return;
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			runCallback(onIncomplete);
			return;
		}
		fileDialogOpen = true;
		String baseSuggestion = suggestedSavePath(doc.savePath, doc.filePath, lastSaveDirectory);
		if (nativeProjectPath != null)
			baseSuggestion = nativeProjectPath.resolveSibling(Path.of(baseSuggestion).getFileName()).toString();
		String suggested = SpineVersionPolicy.wouldOverwriteIncompatibleSource(doc.data.getVersion(),
			doc.filePath, baseSuggestion) ? SpineVersionPolicy.conversionCopyPath(baseSuggestion) : baseSuggestion;
		try {
			DesktopLauncher.chooseSaveFileAsync(suggested,
				(path, error) -> saveDialogCompleted(path, error, onSuccess, onIncomplete));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("另存为对话框失败", error);
			runCallback(onIncomplete);
		}
	}

	/** Warns once per document revision before converting data outside the supported 4.3 format family. */
	private boolean ensureVersionCompatibility(Runnable proceed, Runnable onIncomplete) {
		if (doc.data == null || versionWarningDocument == doc && versionWarningRevision == doc.revision) return true;
		String source = doc.data.getVersion();
		if (!SpineVersionPolicy.requiresConversion(source)) return true;
		UiDialogs.confirm(stage, kit, "版本兼容提示",
			"当前数据版本为 " + sourceVersionLabel(source)
				+ "。输出将显式转换为 Spine 4.3.0，可能改变或舍弃不兼容字段；原始文件不能作为目标。",
			"继续转换", () -> {
				versionWarningDocument = doc;
				versionWarningRevision = doc.revision;
				proceed.run();
			}, onIncomplete == null ? () -> { } : onIncomplete);
		return false;
	}

	private static String sourceVersionLabel(String version) {
		return version == null || version.isBlank() ? "未声明" : version.trim();
	}

	private void saveDialogCompleted(String path, Throwable error, Runnable onSuccess, Runnable onIncomplete) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("另存为对话框失败", error);
				runCallback(onIncomplete);
				return;
			}
			if (path == null || path.isBlank()) {
				runCallback(onIncomplete);
				return;
			}
			saveTo(path, onSuccess, onIncomplete);
		});
	}

	private void saveTo(String path, Runnable onSuccess, Runnable onIncomplete) {
		Path target;
		try {
			target = Path.of(path).toAbsolutePath().normalize();
		} catch (RuntimeException error) {
			showError("保存失败", error);
			runCallback(onIncomplete);
			return;
		}
		if (isProjectPackagePath(target.toString()) || isManagedGenerationPath(target)) {
			showError("无法导出骨架", new IllegalArgumentException("请选择工程资源目录之外的新 .json 或 .skel 文件"));
			runCallback(onIncomplete);
			return;
		}
		if (SpineVersionPolicy.wouldOverwriteIncompatibleSource(doc.data.getVersion(), doc.filePath,
			target.toString())) {
			UiDialogs.confirm(stage, kit, "已阻止覆盖源文件",
				"该路径是原始的非 4.3 数据文件。请选择其他路径保存转换后的 4.3.0 副本。",
				"重新选择", () -> saveAs(onSuccess, onIncomplete),
				onIncomplete == null ? () -> { } : onIncomplete);
			return;
		}
		long revision = doc.revision;
		String skeletonName = skeletonProject.activeName();
		beginDocumentIo("保存", "保存骨架", "正在写入骨架数据", target, token -> {
			writeDocument(target, token);
			return null;
		}, ignored -> {
			if (doc.revision != revision) {
				setStatus("文件已写入，但文档在保存期间发生了更改", true);
				runCallback(onIncomplete);
				return;
			}
			if (usesNativeProjectWorkflow()) {
				rememberDirectory(target.toString(), true);
				setStatus("已导出骨架副本：" + target.getFileName() + "；工程保存路径保持不变", false);
				runCallback(onSuccess);
				return;
			}
			doc.savePath = target.toString();
			rememberDirectory(target.toString(), true);
			rememberRecentProject(target.toString());
			history.markSaved();
			saveCurrentSpineProjectState();
			setStatus("已保存骨架 " + skeletonName + "：" + target.getFileName(), false);
			syncCommandState();
			runCallback(onSuccess);
		}, onIncomplete);
	}

	private void writeDocument(Path target) throws IOException {
		writeDocument(target, new CancellationToken());
	}

	private void writeDocument(Path target, CancellationToken cancellation) throws IOException {
		if (isProjectPackagePath(target.toString()) || isManagedGenerationPath(target))
			throw new IllegalArgumentException("请选择工程资源目录之外的新 .json 或 .skel 文件");
		String lower = target.getFileName().toString().toLowerCase(Locale.ROOT);
		if (lower.endsWith(".skel")) SkeletonBinaryExporter.writeAtomic(doc, target, cancellation);
		else if (lower.endsWith(".json")) SkeletonExporter.writeAtomic(doc, target, cancellation);
		else throw new IllegalArgumentException("Save path must end with .json or .skel");
	}

	@FunctionalInterface
	private interface DocumentIoWork<T> {
		T run(CancellationToken cancellation) throws IOException;
	}

	private boolean documentIoInProgress() {
		return documentIoCancellation != null;
	}

	private <T> void beginDocumentIo(String operation, String title, String status, Path target,
		DocumentIoWork<T> work, Consumer<T> onSuccess, Runnable onIncomplete) {
		if (documentIoInProgress()) {
			setStatus("已有保存或打包任务正在执行", false);
			runCallback(onIncomplete);
			return;
		}

		long generation = ++documentIoGeneration;
		CancellationToken cancellation = new CancellationToken();
		documentIoCancellation = cancellation;
		showDocumentIoProgress(title, status, target);
		setStatus("正在后台" + operation + " " + target.getFileName(), false);

		Thread worker = new Thread(() -> {
			T result = null;
			Throwable failure = null;
			try {
				result = work.run(cancellation);
			} catch (Throwable error) {
				failure = error;
			}
			T completedResult = result;
			Throwable completedFailure = failure;
			boolean cancelled = failure instanceof CancellationException
				|| (failure != null && cancellation.isCancellationRequested());
			// After shutdown there is no UI left to notify; work() already cleaned up its temporary files.
			postToRenderThread(() -> documentIoCompleted(generation, operation, completedResult,
				completedFailure, cancelled, onSuccess, onIncomplete));
		}, "openspine-document-" + (operation.equals("保存") ? "save" : "package"));
		worker.setDaemon(true);
		documentIoThread = worker;
		try {
			worker.start();
		} catch (RuntimeException error) {
			documentIoThread = null;
			documentIoCancellation = null;
			hideDocumentIoProgress();
			showError("无法启动后台" + operation, error);
			runCallback(onIncomplete);
		}
	}

	private <T> void documentIoCompleted(long generation, String operation, T result, Throwable failure,
		boolean cancelled, Consumer<T> onSuccess, Runnable onIncomplete) {
		if (generation != documentIoGeneration) return;
		documentIoThread = null;
		documentIoCancellation = null;
		hideDocumentIoProgress();
		if (disposed) return;
		if (cancelled) {
			setStatus("已取消" + operation, false);
			runCallback(onIncomplete);
			return;
		}
		if (failure != null) {
			showError(operation + "失败", failure);
			runCallback(onIncomplete);
			return;
		}
		onSuccess.accept(result);
	}

	private void showDocumentIoProgress(String title, String status, Path target) {
		documentIoPreviousFocus = stage.getKeyboardFocus();
		Window window = kit.window(title);
		documentIoProgress = window;
		window.setModal(true);
		window.getTitleLabel().setAlignment(Align.left);
		window.getTitleTable().pad(5, 20, 5, 20);
		window.pad(40, 20, 18, 20);

		Label statusLabel = new Label(status, kit.labelStyle);
		documentIoProgressStatus = statusLabel;
		statusLabel.setAlignment(Align.left);
		statusLabel.setColor(0.91f, 0.93f, 0.96f, 1f);
		Label fileName = new Label(target.getFileName().toString(), kit.labelStyle);
		fileName.setAlignment(Align.left);
		fileName.setColor(0.58f, 0.64f, 0.72f, 1f);
		fileName.setEllipsis(true);
		Actor progress = new IndeterminateProgressIndicator(kit.downDark, kit.accent);
		TextButton cancel = button("取消", () -> cancelDocumentIo(true));
		documentIoCancelButton = cancel;
		addModalKeyActions(window, () -> {}, () -> cancelDocumentIo(true));

		window.add(statusLabel).width(IMPORT_DIALOG_CONTENT_WIDTH).height(24).left().row();
		window.add(fileName).width(IMPORT_DIALOG_CONTENT_WIDTH).minWidth(0).height(22).left().padTop(2).row();
		window.add(progress).width(IMPORT_DIALOG_CONTENT_WIDTH).height(IMPORT_PROGRESS_HEIGHT).padTop(14).row();
		Table actions = new Table();
		actions.add().growX();
		actions.add(cancel).width(86).height(30);
		window.add(actions).width(IMPORT_DIALOG_CONTENT_WIDTH).padTop(16);
		showCentered(window);
	}

	private void cancelDocumentIo(boolean reportStatus) {
		CancellationToken cancellation = documentIoCancellation;
		if (cancellation == null || cancellation.isCancellationRequested()) return;
		cancellation.cancel();
		Thread worker = documentIoThread;
		if (worker != null) worker.interrupt();
		if (documentIoProgressStatus != null) documentIoProgressStatus.setText("正在取消并清理临时文件");
		if (documentIoCancelButton != null) documentIoCancelButton.setDisabled(true);
		if (reportStatus) setStatus("正在取消后台文件操作", false);
	}

	private void hideDocumentIoProgress() {
		Window window = documentIoProgress;
		Actor previousFocus = documentIoPreviousFocus;
		documentIoProgress = null;
		documentIoPreviousFocus = null;
		documentIoProgressStatus = null;
		documentIoCancelButton = null;
		if (window != null) UiDialogs.closeModal(stage, window, previousFocus);
	}

	private void beginAnimationRender(String title, Path target, Skeleton source, Animation animation,
		float fps, int warmUp, int startFrame, int lastFrame, PngFrameExporter.Settings settings,
		boolean sourcePremultipliedAlpha, Consumer<IncrementalAnimationRenderer> completion) {
		if (animationRenderer != null || documentIoInProgress()) {
			setStatus("已有导出、保存或打包任务正在执行", false);
			return;
		}
		CancellationToken cancellation = new CancellationToken();
		try {
			animationRenderer = new IncrementalAnimationRenderer(source, animation, fps, warmUp,
				startFrame, lastFrame, settings, sourcePremultipliedAlpha, cancellation);
			animationRenderCancellation = cancellation;
			animationRenderCompletion = completion;
			showAnimationRenderProgress(title, target);
			setStatus("正在逐帧渲染 " + target.getFileName(), false);
		} catch (IOException | RuntimeException error) {
			closeAnimationRenderer(animationRenderer);
			clearAnimationRenderState();
			showError(title + "失败", error);
		}
	}

	private void advanceAnimationRender() {
		IncrementalAnimationRenderer renderer = animationRenderer;
		if (renderer == null) return;
		try {
			renderer.advance();
			if (animationRenderProgressBar != null)
				animationRenderProgressBar.setProgress(renderer.progress());
			if (animationRenderProgressStatus != null) {
				String phase = renderer.phase() == IncrementalAnimationRenderer.Phase.MEASURING
					? "正在测量画布" : "正在渲染帧";
				animationRenderProgressStatus.setText(phase + " · " + renderer.completedSteps()
					+ " / " + renderer.totalSteps());
			}
			if (!renderer.isComplete()) return;
			Consumer<IncrementalAnimationRenderer> completion = animationRenderCompletion;
			clearAnimationRenderState();
			hideAnimationRenderProgress();
			try {
				if (completion == null) closeAnimationRenderer(renderer);
				else completion.accept(renderer);
			} catch (RuntimeException error) {
				closeAnimationRenderer(renderer);
				showError("动画导出失败", error);
			}
		} catch (CancellationException cancelled) {
			closeAnimationRenderer(renderer);
			clearAnimationRenderState();
			hideAnimationRenderProgress();
			setStatus("已取消动画导出", false);
		} catch (IOException | RuntimeException error) {
			closeAnimationRenderer(renderer);
			clearAnimationRenderState();
			hideAnimationRenderProgress();
			showError("动画帧渲染失败", error);
		}
	}

	private void showAnimationRenderProgress(String title, Path target) {
		animationRenderPreviousFocus = stage.getKeyboardFocus();
		Window window = kit.window(title);
		animationRenderProgress = window;
		window.setModal(true);
		window.getTitleLabel().setAlignment(Align.left);
		window.getTitleTable().pad(5, 20, 5, 20);
		window.pad(40, 20, 18, 20);
		animationRenderProgressStatus = new Label("正在准备动画帧", kit.labelStyle);
		animationRenderProgressStatus.setAlignment(Align.left);
		Label fileName = new Label(target.getFileName().toString(), kit.labelStyle);
		fileName.setColor(0.58f, 0.64f, 0.72f, 1f);
		fileName.setEllipsis(true);
		animationRenderProgressBar = new DeterminateProgressIndicator(kit.downDark, kit.accent);
		animationRenderCancelButton = button("取消", () -> cancelAnimationRender(true));
		addModalKeyActions(window, () -> {}, () -> cancelAnimationRender(true));
		window.add(animationRenderProgressStatus).width(IMPORT_DIALOG_CONTENT_WIDTH).height(24).left().row();
		window.add(fileName).width(IMPORT_DIALOG_CONTENT_WIDTH).height(22).left().padTop(2).row();
		window.add(animationRenderProgressBar).width(IMPORT_DIALOG_CONTENT_WIDTH)
			.height(IMPORT_PROGRESS_HEIGHT).padTop(14).row();
		Table actions = new Table();
		actions.add().growX();
		actions.add(animationRenderCancelButton).width(86).height(30);
		window.add(actions).width(IMPORT_DIALOG_CONTENT_WIDTH).padTop(16);
		showCentered(window);
	}

	private void cancelAnimationRender(boolean reportStatus) {
		CancellationToken cancellation = animationRenderCancellation;
		if (cancellation == null || cancellation.isCancellationRequested()) return;
		cancellation.cancel();
		if (animationRenderCancelButton != null) animationRenderCancelButton.setDisabled(true);
		if (animationRenderProgressStatus != null) animationRenderProgressStatus.setText("正在取消并清理临时帧");
		if (reportStatus) setStatus("正在取消动画导出", false);
	}

	private void hideAnimationRenderProgress() {
		Window window = animationRenderProgress;
		Actor previousFocus = animationRenderPreviousFocus;
		animationRenderProgress = null;
		animationRenderPreviousFocus = null;
		animationRenderProgressStatus = null;
		animationRenderProgressBar = null;
		animationRenderCancelButton = null;
		if (window != null) UiDialogs.closeModal(stage, window, previousFocus);
	}

	private void clearAnimationRenderState() {
		animationRenderer = null;
		animationRenderCancellation = null;
		animationRenderCompletion = null;
	}

	private static void closeAnimationRenderer(IncrementalAnimationRenderer renderer) {
		if (renderer == null) return;
		try { renderer.close(); }
		catch (IOException error) {
			if (Gdx.app != null) Gdx.app.error("OpenSpine", "无法清理动画暂存帧", error);
		}
	}

	private static void runCallback(Runnable callback) {
		if (callback != null) callback.run();
	}

	/** Posts to the GL thread from any thread. Returns false when the application has already shut down;
	 * capturing the instance avoids a shutdown race against callers reading {@code Gdx.app} twice. */
	private static boolean postToRenderThread(Runnable runnable) {
		Application app = Gdx.app;
		if (app == null) return false;
		app.postRunnable(runnable);
		return true;
	}

	private void exportProject() {
		finishWorkspaceTextEdit();
		if (doc.data == null) {
			setStatus("没有可导出的骨架数据", true);
			return;
		}
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("导出项目");
		window.setModal(true);
		kit.padWindow(window, 10f);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);
		TextButton spineProject = button("重建 Spine 工程 (.spine)", () -> {
			close.run();
			rebuildSpineProject();
		});
		TextButton skeletonData = button("骨架数据 (.json / .skel)", () -> {
			close.run();
			exportSkeletonData();
		});
		TextButton pngFrame = button("PNG 当前帧", () -> {
			close.run();
			showPngExportSettings();
		});
		TextButton jpegFrame = button("JPEG 当前帧", () -> {
			close.run();
			showJpegExportSettings();
		});
		TextButton webpFrame = button("WebP 当前帧", () -> {
			close.run();
			showWebpExport();
		});
		TextButton psdFrame = button("扁平 PSD 当前帧", () -> {
			close.run();
			showPsdExport();
		});
		TextButton htmlProject = button("HTML 动画播放器", () -> {
			close.run();
			showHtmlExport();
		});
		TextButton pngSequence = button("PNG 动画序列", () -> {
			close.run();
			showPngSequenceExportSettings();
		});
		TextButton jpegSequence = button("JPEG 动画序列", () -> {
			close.run();
			showJpegSequenceExportSettings();
		});
		TextButton gifAnimation = button("GIF 动画", () -> {
			close.run();
			showGifExportSettings();
		});
		TextButton apngAnimation = button("APNG 动画", () -> {
			close.run();
			showApngExportSettings();
		});
		TextButton animatedWebp = button("AWEBP 动画 (ffmpeg)", () -> {
			close.run();
			showVideoExport(FfmpegAnimationExporter.Format.AWEBP);
		});
		TextButton aviAnimation = button("AVI 动画", () -> {
			close.run();
			showAviExportSettings();
		});
		TextButton webmAnimation = button("WebM 动画 (ffmpeg)", () -> {
			close.run();
			showVideoExport(FfmpegAnimationExporter.Format.WEBM);
		});
		TextButton movAnimation = button("MOV 动画 (ffmpeg)", () -> {
			close.run();
			showVideoExport(FfmpegAnimationExporter.Format.MOV);
		});
		boolean ffmpegAvailable = FfmpegAnimationExporter.isAvailable();
		webpFrame.setDisabled(!WebpFrameExporter.isAvailable());
		animatedWebp.setDisabled(!ffmpegAvailable);
		webmAnimation.setDisabled(!ffmpegAvailable);
		movAnimation.setDisabled(!ffmpegAvailable);
		TextButton cancel = button("取消", close);
		addModalKeyActions(window, () -> {}, close);
		window.row();
		window.add(spineProject).width(260).height(30).pad(5).row();
		window.add(skeletonData).width(260).height(30).pad(5).row();
		window.add(pngFrame).width(260).height(30).pad(5).row();
		window.add(jpegFrame).width(260).height(30).pad(5).row();
		window.add(webpFrame).width(260).height(30).pad(5).row();
		window.add(psdFrame).width(260).height(30).pad(5).row();
		window.add(htmlProject).width(260).height(30).pad(5).row();
		window.add(pngSequence).width(260).height(30).pad(5).row();
		window.add(jpegSequence).width(260).height(30).pad(5).row();
		window.add(gifAnimation).width(260).height(30).pad(5).row();
		window.add(apngAnimation).width(260).height(30).pad(5).row();
		window.add(animatedWebp).width(260).height(30).pad(5).row();
		window.add(aviAnimation).width(260).height(30).pad(5).row();
		window.add(webmAnimation).width(260).height(30).pad(5).row();
		window.add(movAnimation).width(260).height(30).pad(5).row();
		window.add(cancel).width(86).height(28).right().pad(8, 5, 3, 5);
		showCentered(window);
	}

	private void rebuildSpineProject() {
		if (documentIoInProgress()) {
			setStatus("已有文件任务正在执行", false);
			return;
		}
		if (!ensureVersionCompatibility(this::rebuildSpineProject, null)) return;
		if (!spineRebuildWarningAcknowledged) {
			UiDialogs.confirm(stage, kit, "重建 Spine 工程",
				"此操作通过官方 Spine CLI 从当前可表达的 4.3 runtime 数据创建新工程，不是无损回写。原工程的文件夹、布局/相机、Graph 选择、导出设置、撤销历史、未导出的编辑器元数据和缺失的原始图片不会恢复。请保留原 .spine 文件。",
				"继续重建", () -> {
					spineRebuildWarningAcknowledged = true;
					rebuildSpineProject();
				}, () -> setStatus("已取消 Spine 工程重建", false));
			return;
		}
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		fileDialogOpen = true;
		String suggested = suggestedSpineProjectPath(doc.savePath, doc.filePath, lastSaveDirectory);
		try {
			DesktopLauncher.chooseSpineProjectSaveFileAsync(suggested, this::spineProjectDialogCompleted);
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("Spine 工程文件对话框失败", error);
		}
	}

	private void spineProjectDialogCompleted(String path, Throwable error) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("Spine 工程文件对话框失败", error);
				return;
			}
			if (path == null || path.isBlank()) return;
			Path target;
			try {
				target = Path.of(path).toAbsolutePath().normalize();
				if (isManagedGenerationPath(target)) throw new IllegalArgumentException("请选择工程资源目录之外的新 .spine 文件");
			} catch (RuntimeException invalidPath) {
				showError("重建 Spine 工程失败", invalidPath);
				return;
			}
			List<SkeletonDocument> documents = skeletonProject.documents();
			if (documents.stream().anyMatch(document -> SpineVersionPolicy.samePersistentPath(
				document.filePath, target.toString()))) {
				UiDialogs.confirm(stage, kit, "已阻止覆盖原 Spine 工程",
					"重建不是无损回写，不能覆盖作为导入源的 .spine 文件。请选择新的 .spine 路径。",
					"重新选择", this::rebuildSpineProject,
					() -> setStatus("已取消 Spine 工程重建", false));
				return;
			}
			List<SpineProjectExporter.ProjectSkeleton> skeletons = new ArrayList<>(documents.size());
			List<ProjectSaveState> captured = projectSaveState(skeletonProject);
			for (int index = 0; index < documents.size(); index++)
				skeletons.add(new SpineProjectExporter.ProjectSkeleton(skeletonProject.nameAt(index),
					documents.get(index)));
			beginDocumentIo("重建", "重建 Spine 工程", "正在通过官方 Spine 导入骨架数据", target,
				token -> SpineProjectExporter.write(skeletons, target, token), result -> {
					rememberDirectory(target.toString(), true);
					if (!captured.equals(projectSaveState(skeletonProject))) {
						setStatus("Spine 工程副本已写入，但当前编辑内容已改变，请再次保存", true);
						return;
					}
					if (nativeProjectPath == null) {
						for (SkeletonDocument document : skeletonProject.documents()) {
							document.filePath = target.toString();
							document.savePath = null;
							document.markSaved();
						}
						for (EditHistory documentHistory : documentHistories) documentHistory.markSavedState();
						skeletonProject.markStructureSaved();
					}
					rememberRecentProject(target.toString());
					try {
						if (treePanel != null) treePanel.persistViewState();
						if (timelinePanel != null) timelinePanel.persistViewState();
						ProjectStateStore.write(target, skeletonProject, showAllSkeletons);
						setStatus("已重建 Spine 工程并保存 OpenSpine 状态：" + target.getFileName()
							+ "（" + result.skeletonCount() + " 个骨架）", false);
					} catch (IOException | RuntimeException sidecarError) {
						logProjectStateError("重建工程成功，但写入 OpenSpine 状态失败", sidecarError);
						setStatus("已重建 Spine 工程，但 Sidecar 状态写入失败："
							+ target.getFileName(), true);
					}
				}, null);
		});
	}

	private void exportSkeletonData() {
		if (!ensureVersionCompatibility(this::exportSkeletonData, null)) return;
		String suggested = suggestedSavePath(null, doc.filePath, lastSaveDirectory);
		int dot = suggested.lastIndexOf('.');
		if (dot > 0) suggested = suggested.substring(0, dot) + "-export" + suggested.substring(dot);
		try {
			String path = DesktopLauncher.chooseSaveFile(suggested);
			if (path == null) return;
			Path target = Path.of(path).toAbsolutePath().normalize();
			if (SpineVersionPolicy.wouldOverwriteIncompatibleSource(doc.data.getVersion(), doc.filePath,
				target.toString())) {
				UiDialogs.confirm(stage, kit, "已阻止覆盖源文件",
					"该路径是原始的非 4.3 数据文件。请选择其他路径导出转换后的 4.3.0 副本。",
					"重新选择", this::exportSkeletonData);
				return;
			}
			writeDocument(target);
			rememberDirectory(target.toString(), true);
			setStatus("已导出 " + target.getFileName(), false);
		} catch (IOException | RuntimeException error) {
			showError("导出项目失败", error);
		}
	}

	private void showPngExportSettings() {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		try {
			PngFrameExporter.measure(doc.skeleton);
		} catch (RuntimeException error) {
			showError("无法导出 PNG", error);
			return;
		}

		float savedScale = preferences.getFloat(PREF_PNG_SCALE, 1f);
		if (!Float.isFinite(savedScale) || savedScale < PngFrameExporter.MIN_SCALE
			|| savedScale > PngFrameExporter.MAX_SCALE) savedScale = 1f;
		int savedPadding = preferences.getInteger(PREF_PNG_PADDING, 0);
		if (savedPadding < 0 || savedPadding > PngFrameExporter.MAX_PADDING) savedPadding = 0;
		int savedCompression = preferences.getInteger(PREF_PNG_COMPRESSION,
			PngFrameExporter.DEFAULT_COMPRESSION);
		if (savedCompression < PngFrameExporter.MIN_COMPRESSION
			|| savedCompression > PngFrameExporter.MAX_COMPRESSION)
			savedCompression = PngFrameExporter.DEFAULT_COMPRESSION;
		boolean[] transparent = {preferences.getBoolean(PREF_PNG_TRANSPARENT, true)};
		boolean[] premultiplied = {preferences.getBoolean(PREF_PNG_PREMULTIPLIED, false)};

		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("导出 PNG 当前帧");
		window.setModal(true);
		kit.padWindow(window, 10f);
		TextField scaleField = new TextField(UiNumbers.format(savedScale * 100f), kit.textFieldStyle);
		TextField paddingField = new TextField(Integer.toString(savedPadding), kit.textFieldStyle);
		TextField compressionField = new TextField(Integer.toString(savedCompression), kit.textFieldStyle);
		TextButton transparentButton = new TextButton("", kit.toolButton);
		TextButton premultipliedButton = new TextButton("", kit.toolButton);
		Label sizeLabel = new Label("", kit.labelStyle);
		TextButton export = new TextButton("导出", kit.accentButton);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);

		Runnable refresh = () -> {
			refreshPngToggle(transparentButton, transparent[0]);
			refreshPngToggle(premultipliedButton, transparent[0] && premultiplied[0]);
			premultipliedButton.setDisabled(!transparent[0]);
			try {
				PngFrameExporter.Settings settings = pngExportSettings(scaleField.getText(), paddingField.getText(),
					compressionField.getText(), transparent[0], premultiplied[0]);
				PngFrameExporter.Bounds bounds = PngFrameExporter.measure(doc.skeleton);
				PngFrameExporter.OutputSize size = PngFrameExporter.outputSize(bounds, settings, Integer.MAX_VALUE);
				sizeLabel.setText("输出尺寸：" + size.width() + " × " + size.height() + " px");
				setValidationLabel(sizeLabel, false);
				export.setDisabled(false);
			} catch (RuntimeException error) {
				String message = error.getMessage();
				sizeLabel.setText(message == null || message.isBlank() ? "PNG 设置无效" : message);
				setValidationLabel(sizeLabel, true);
				export.setDisabled(true);
			}
		};

		ChangeListener fieldListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refresh.run(); }
		};
		scaleField.addListener(fieldListener);
		paddingField.addListener(fieldListener);
		compressionField.addListener(fieldListener);
		transparentButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				transparent[0] = !transparent[0];
				refresh.run();
			}
		});
		premultipliedButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (premultipliedButton.isDisabled()) return;
				premultiplied[0] = !premultiplied[0];
				refresh.run();
			}
		});
		Runnable submit = () -> {
			if (export.isDisabled()) return;
			try {
				PngFrameExporter.Settings settings = pngExportSettings(scaleField.getText(), paddingField.getText(),
					compressionField.getText(), transparent[0], premultiplied[0]);
				PngFrameExporter.outputSize(PngFrameExporter.measure(doc.skeleton), settings, Integer.MAX_VALUE);
				Skeleton snapshot = new Skeleton(doc.skeleton);
				snapshot.updateWorldTransform(Physics.none);
				long revision = doc.revision;
				TextureAtlas atlas = doc.atlas;
				boolean sourcePremultipliedAlpha = doc.premultipliedAlpha;
				preferences.putFloat(PREF_PNG_SCALE, settings.scale());
				preferences.putInteger(PREF_PNG_PADDING, settings.padding());
				preferences.putInteger(PREF_PNG_COMPRESSION, settings.compression());
				preferences.putBoolean(PREF_PNG_TRANSPARENT, settings.transparent());
				preferences.putBoolean(PREF_PNG_PREMULTIPLIED, settings.premultipliedAlpha());
				preferences.flush();
				close.run();
				beginPngExport(snapshot, atlas, revision, sourcePremultipliedAlpha, settings);
			} catch (RuntimeException error) {
				refresh.run();
			}
		};
		export.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		TextButton cancel = button("取消", close);
		addModalKeyActions(window, submit, close);

		window.row();
		window.add(new Label("缩放", kit.labelStyle)).right().pad(5);
		Table scaleRow = new Table();
		scaleRow.add(scaleField).width(120).height(28);
		scaleRow.add(new Label("%", kit.labelStyle)).padLeft(5);
		window.add(scaleRow).left().pad(5).row();
		window.add(new Label("留白", kit.labelStyle)).right().pad(5);
		Table paddingRow = new Table();
		paddingRow.add(paddingField).width(120).height(28);
		paddingRow.add(new Label("px", kit.labelStyle)).padLeft(5);
		window.add(paddingRow).left().pad(5).row();
		window.add(new Label("压缩级别", kit.labelStyle)).right().pad(5);
		Table compressionRow = new Table();
		compressionRow.add(compressionField).width(120).height(28);
		compressionRow.add(new Label("0-9", kit.labelStyle)).padLeft(5);
		window.add(compressionRow).left().pad(5).row();
		window.add(new Label("透明背景", kit.labelStyle)).right().pad(5);
		window.add(transparentButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("预乘 Alpha", kit.labelStyle)).right().pad(5);
		window.add(premultipliedButton).width(110).height(28).left().pad(5).row();
		window.add(sizeLabel).colspan(2).left().pad(10, 5, 6, 5).row();
		Table actions = new Table();
		actions.add(cancel).width(86).height(28).padRight(6);
		actions.add(export).width(86).height(28);
		window.add(actions).colspan(2).right().pad(6, 5, 3, 5);
		refresh.run();
		showCentered(window);
		stage.setKeyboardFocus(scaleField);
		scaleField.selectAll();
	}

	private void refreshPngToggle(TextButton button, boolean enabled) {
		button.setText(enabled ? "开启" : "关闭");
		button.setStyle(enabled ? kit.activeToolButton : kit.toolButton);
	}

	private void beginPngExport(Skeleton snapshot, TextureAtlas atlas, long revision,
		boolean sourcePremultipliedAlpha, PngFrameExporter.Settings settings) {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		fileDialogOpen = true;
		setStatus("正在选择 PNG 导出位置...", false);
		String suggested = suggestedPngPath(doc.savePath, doc.filePath, lastSaveDirectory);
		try {
			DesktopLauncher.choosePngFileAsync(suggested,
				(path, error) -> pngExportDialogCompleted(path, error, snapshot, atlas, revision,
					sourcePremultipliedAlpha, settings));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("PNG 文件对话框失败", error);
		}
	}

	private void pngExportDialogCompleted(String path, Throwable error, Skeleton snapshot, TextureAtlas atlas,
		long revision, boolean sourcePremultipliedAlpha, PngFrameExporter.Settings settings) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("PNG 文件对话框失败", error);
				return;
			}
			if (path == null || path.isBlank()) return;
			if (doc.revision != revision || doc.atlas != atlas) {
				setStatus("文档在选择导出位置时已更改，请重新导出 PNG", true);
				return;
			}
			try {
				Path target = Path.of(path).toAbsolutePath().normalize();
				PngFrameExporter.ExportResult result = PngFrameExporter.write(snapshot, target, settings,
					sourcePremultipliedAlpha);
				rememberDirectory(target.toString(), true);
				setStatus("已导出 " + target.getFileName() + "（" + result.width() + " × " + result.height() + " px）",
					false);
			} catch (IOException | RuntimeException exportError) {
				showError("PNG 导出失败", exportError);
			}
		});
	}

	private void showJpegExportSettings() {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		try {
			PngFrameExporter.measure(doc.skeleton);
		} catch (RuntimeException error) {
			showError("无法导出 JPEG", error);
			return;
		}

		float savedScale = preferences.getFloat(PREF_JPEG_SCALE, 1f);
		if (!Float.isFinite(savedScale) || savedScale < PngFrameExporter.MIN_SCALE
			|| savedScale > PngFrameExporter.MAX_SCALE) savedScale = 1f;
		int savedPadding = preferences.getInteger(PREF_JPEG_PADDING, 0);
		if (savedPadding < 0 || savedPadding > PngFrameExporter.MAX_PADDING) savedPadding = 0;
		int savedQuality = preferences.getInteger(PREF_JPEG_QUALITY, JpegFrameExporter.DEFAULT_QUALITY);
		if (savedQuality < JpegFrameExporter.MIN_QUALITY
			|| savedQuality > JpegFrameExporter.MAX_QUALITY) savedQuality = JpegFrameExporter.DEFAULT_QUALITY;

		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("导出 JPEG 当前帧");
		window.setModal(true);
		kit.padWindow(window, 10f);
		TextField scaleField = new TextField(UiNumbers.format(savedScale * 100f), kit.textFieldStyle);
		TextField paddingField = new TextField(Integer.toString(savedPadding), kit.textFieldStyle);
		TextField qualityField = new TextField(Integer.toString(savedQuality), kit.textFieldStyle);
		Label sizeLabel = new Label("", kit.labelStyle);
		TextButton export = new TextButton("导出", kit.accentButton);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);

		Runnable refresh = () -> {
			try {
				JpegFrameExporter.Settings settings = jpegExportSettings(scaleField.getText(), paddingField.getText(),
					qualityField.getText());
				PngFrameExporter.Bounds bounds = PngFrameExporter.measure(doc.skeleton);
				PngFrameExporter.OutputSize size = JpegFrameExporter.outputSize(bounds, settings, Integer.MAX_VALUE);
				sizeLabel.setText("输出尺寸：" + size.width() + " × " + size.height() + " px");
				setValidationLabel(sizeLabel, false);
				export.setDisabled(false);
			} catch (RuntimeException error) {
				String message = error.getMessage();
				sizeLabel.setText(message == null || message.isBlank() ? "JPEG 设置无效" : message);
				setValidationLabel(sizeLabel, true);
				export.setDisabled(true);
			}
		};

		ChangeListener fieldListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refresh.run(); }
		};
		scaleField.addListener(fieldListener);
		paddingField.addListener(fieldListener);
		qualityField.addListener(fieldListener);

		Runnable submit = () -> {
			if (export.isDisabled()) return;
			try {
				JpegFrameExporter.Settings settings = jpegExportSettings(scaleField.getText(), paddingField.getText(),
					qualityField.getText());
				JpegFrameExporter.outputSize(PngFrameExporter.measure(doc.skeleton), settings, Integer.MAX_VALUE);
				Skeleton snapshot = new Skeleton(doc.skeleton);
				snapshot.updateWorldTransform(Physics.none);
				long revision = doc.revision;
				TextureAtlas atlas = doc.atlas;
				boolean sourcePremultipliedAlpha = doc.premultipliedAlpha;
				preferences.putFloat(PREF_JPEG_SCALE, settings.scale());
				preferences.putInteger(PREF_JPEG_PADDING, settings.padding());
				preferences.putInteger(PREF_JPEG_QUALITY, settings.quality());
				preferences.flush();
				close.run();
				beginJpegExport(snapshot, atlas, revision, sourcePremultipliedAlpha, settings);
			} catch (RuntimeException error) {
				refresh.run();
			}
		};
		export.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		TextButton cancel = button("取消", close);
		addModalKeyActions(window, submit, close);

		window.row();
		window.add(new Label("缩放", kit.labelStyle)).right().pad(5);
		Table scaleRow = new Table();
		scaleRow.add(scaleField).width(120).height(28);
		scaleRow.add(new Label("%", kit.labelStyle)).padLeft(5);
		window.add(scaleRow).left().pad(5).row();
		window.add(new Label("留白", kit.labelStyle)).right().pad(5);
		Table paddingRow = new Table();
		paddingRow.add(paddingField).width(120).height(28);
		paddingRow.add(new Label("px", kit.labelStyle)).padLeft(5);
		window.add(paddingRow).left().pad(5).row();
		window.add(new Label("质量", kit.labelStyle)).right().pad(5);
		Table qualityRow = new Table();
		qualityRow.add(qualityField).width(120).height(28);
		qualityRow.add(new Label("%", kit.labelStyle)).padLeft(5);
		window.add(qualityRow).left().pad(5).row();
		window.add(sizeLabel).colspan(2).left().pad(10, 5, 6, 5).row();
		Table actions = new Table();
		actions.add(cancel).width(86).height(28).padRight(6);
		actions.add(export).width(86).height(28);
		window.add(actions).colspan(2).right().pad(6, 5, 3, 5);
		refresh.run();
		showCentered(window);
		stage.setKeyboardFocus(scaleField);
		scaleField.selectAll();
	}

	private void beginJpegExport(Skeleton snapshot, TextureAtlas atlas, long revision,
		boolean sourcePremultipliedAlpha, JpegFrameExporter.Settings settings) {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		fileDialogOpen = true;
		setStatus("正在选择 JPEG 导出位置...", false);
		String suggested = suggestedJpegPath(doc.savePath, doc.filePath, lastSaveDirectory);
		try {
			DesktopLauncher.chooseJpegFileAsync(suggested,
				(path, error) -> jpegExportDialogCompleted(path, error, snapshot, atlas, revision,
					sourcePremultipliedAlpha, settings));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("JPEG 文件对话框失败", error);
		}
	}

	private void jpegExportDialogCompleted(String path, Throwable error, Skeleton snapshot, TextureAtlas atlas,
		long revision, boolean sourcePremultipliedAlpha, JpegFrameExporter.Settings settings) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("JPEG 文件对话框失败", error);
				return;
			}
			if (path == null || path.isBlank()) return;
			if (doc.revision != revision || doc.atlas != atlas) {
				setStatus("文档在选择导出位置时已更改，请重新导出 JPEG", true);
				return;
			}
			try {
				Path target = Path.of(path).toAbsolutePath().normalize();
				JpegFrameExporter.ExportResult result = JpegFrameExporter.write(snapshot, target, settings,
					sourcePremultipliedAlpha);
				rememberDirectory(target.toString(), true);
				setStatus("已导出 " + target.getFileName() + "（" + result.width() + " × " + result.height() + " px）",
					false);
			} catch (IOException | RuntimeException exportError) {
				showError("JPEG 导出失败", exportError);
			}
		});
	}

	private enum ImageSequenceFormat {
		PNG, JPEG
	}

	private void showPngSequenceExportSettings() {
		showImageSequenceExportSettings(ImageSequenceFormat.PNG);
	}

	private void showJpegSequenceExportSettings() {
		showImageSequenceExportSettings(ImageSequenceFormat.JPEG);
	}

	private void showImageSequenceExportSettings(ImageSequenceFormat format) {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		String formatName = format.name();
		String animationName = doc.getCurrentAnimationName();
		Animation animation = doc.data == null || animationName.isBlank()
			? null : doc.data.findAnimation(animationName);
		if (doc.skeleton == null || animation == null) {
			setStatus("请先选择要导出的动画", true);
			return;
		}

		float projectFps = doc.data.getFps();
		if (!Float.isFinite(projectFps) || projectFps < PngAnimationExporter.MIN_FPS) projectFps = 30f;
		projectFps = Math.min(projectFps, PngAnimationExporter.MAX_FPS);
		String fpsPreference = format == ImageSequenceFormat.PNG
			? PREF_PNG_SEQUENCE_FPS : PREF_JPEG_SEQUENCE_FPS;
		float savedFps = preferences.getFloat(fpsPreference, projectFps);
		if (!Float.isFinite(savedFps) || savedFps < PngAnimationExporter.MIN_FPS
			|| savedFps > PngAnimationExporter.MAX_FPS) savedFps = projectFps;
		String warmUpPreference = format == ImageSequenceFormat.PNG
			? PREF_PNG_SEQUENCE_WARM_UP : PREF_JPEG_SEQUENCE_WARM_UP;
		int savedWarmUp = preferences.getInteger(warmUpPreference, 0);
		if (savedWarmUp < 0 || savedWarmUp > PngAnimationExporter.MAX_WARM_UP) savedWarmUp = 0;
		String scalePreference = format == ImageSequenceFormat.PNG
			? PREF_PNG_SEQUENCE_SCALE : PREF_JPEG_SEQUENCE_SCALE;
		float savedScale = preferences.getFloat(scalePreference, 1f);
		if (!Float.isFinite(savedScale) || savedScale < PngFrameExporter.MIN_SCALE
			|| savedScale > PngFrameExporter.MAX_SCALE) savedScale = 1f;
		String paddingPreference = format == ImageSequenceFormat.PNG
			? PREF_PNG_SEQUENCE_PADDING : PREF_JPEG_SEQUENCE_PADDING;
		int savedPadding = preferences.getInteger(paddingPreference, 0);
		if (savedPadding < 0 || savedPadding > PngFrameExporter.MAX_PADDING) savedPadding = 0;
		int savedCompression = preferences.getInteger(PREF_PNG_SEQUENCE_COMPRESSION,
			PngFrameExporter.DEFAULT_COMPRESSION);
		if (savedCompression < PngFrameExporter.MIN_COMPRESSION
			|| savedCompression > PngFrameExporter.MAX_COMPRESSION)
			savedCompression = PngFrameExporter.DEFAULT_COMPRESSION;
		int savedQuality = preferences.getInteger(PREF_JPEG_SEQUENCE_QUALITY,
			JpegFrameExporter.DEFAULT_QUALITY);
		if (savedQuality < JpegFrameExporter.MIN_QUALITY
			|| savedQuality > JpegFrameExporter.MAX_QUALITY)
			savedQuality = JpegFrameExporter.DEFAULT_QUALITY;
		boolean[] transparent = {preferences.getBoolean(PREF_PNG_SEQUENCE_TRANSPARENT, true)};
		boolean[] premultiplied = {preferences.getBoolean(PREF_PNG_SEQUENCE_PREMULTIPLIED, false)};
		String includeLastFramePreference = format == ImageSequenceFormat.PNG
			? PREF_PNG_SEQUENCE_INCLUDE_LAST_FRAME : PREF_JPEG_SEQUENCE_INCLUDE_LAST_FRAME;
		boolean[] includeLastFrame = {preferences.getBoolean(includeLastFramePreference, false)};
		int lastAnimationFrame;
		try {
			lastAnimationFrame = format == ImageSequenceFormat.PNG
				? PngAnimationExporter.maxAnimationFrame(animation, savedFps)
				: JpegAnimationExporter.maxAnimationFrame(animation, savedFps);
		} catch (RuntimeException error) {
			showError("无法导出 " + formatName + " 序列", error);
			return;
		}
		if (lastAnimationFrame == 0) includeLastFrame[0] = true;
		int defaultEndFrame = Math.min(lastAnimationFrame, Math.min(PngAnimationExporter.MAX_FRAME_INDEX,
			includeLastFrame[0] ? PngAnimationExporter.MAX_FRAMES - 1 : PngAnimationExporter.MAX_FRAMES));

		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("导出 " + formatName + " 动画序列");
		window.setModal(true);
		kit.padWindow(window, 10f);
		TextField fpsField = new TextField(UiNumbers.format(savedFps), kit.textFieldStyle);
		TextField startFrameField = new TextField("0", kit.textFieldStyle);
		TextField endFrameField = new TextField(Integer.toString(defaultEndFrame), kit.textFieldStyle);
		TextField warmUpField = new TextField(Integer.toString(savedWarmUp), kit.textFieldStyle);
		TextField scaleField = new TextField(UiNumbers.format(savedScale * 100f), kit.textFieldStyle);
		TextField paddingField = new TextField(Integer.toString(savedPadding), kit.textFieldStyle);
		TextField compressionField = new TextField(Integer.toString(savedCompression), kit.textFieldStyle);
		TextField qualityField = new TextField(Integer.toString(savedQuality), kit.textFieldStyle);
		TextButton transparentButton = new TextButton("", kit.toolButton);
		TextButton premultipliedButton = new TextButton("", kit.toolButton);
		TextButton includeLastFrameButton = new TextButton("", kit.toolButton);
		Label summaryLabel = new Label("", kit.labelStyle);
		TextButton export = new TextButton("导出", kit.accentButton);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);

		Runnable refresh = () -> {
			refreshPngToggle(includeLastFrameButton, includeLastFrame[0]);
			if (format == ImageSequenceFormat.PNG) {
				refreshPngToggle(transparentButton, transparent[0]);
				refreshPngToggle(premultipliedButton, transparent[0] && premultiplied[0]);
				premultipliedButton.setDisabled(!transparent[0]);
			}
			try {
				int frameCount;
				int animationEnd;
				int warmUp;
				if (format == ImageSequenceFormat.PNG) {
					PngAnimationExporter.Settings settings = pngSequenceExportSettings(fpsField.getText(),
						startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
						scaleField.getText(), paddingField.getText(), compressionField.getText(),
						transparent[0], premultiplied[0], includeLastFrame[0]);
					frameCount = settings.frameCount();
					warmUp = settings.warmUp();
					animationEnd = PngAnimationExporter.maxAnimationFrame(animation, settings.fps());
					if (settings.endFrame() > animationEnd)
						throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				} else {
					JpegAnimationExporter.Settings settings = jpegSequenceExportSettings(fpsField.getText(),
						startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
						scaleField.getText(), paddingField.getText(), qualityField.getText(), includeLastFrame[0]);
					frameCount = settings.frameCount();
					warmUp = settings.warmUp();
					animationEnd = JpegAnimationExporter.maxAnimationFrame(animation, settings.fps());
					if (settings.endFrame() > animationEnd)
						throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				}
				summaryLabel.setText("动画：" + animationName + " · " + frameCount
					+ " 帧 · 预热 " + warmUp + " 次 · 末帧 " + animationEnd);
				setValidationLabel(summaryLabel, false);
				export.setDisabled(false);
			} catch (RuntimeException error) {
				String message = error.getMessage();
				summaryLabel.setText(message == null || message.isBlank()
					? formatName + " 序列设置无效" : message);
				setValidationLabel(summaryLabel, true);
				export.setDisabled(true);
			}
		};

		ChangeListener fieldListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refresh.run(); }
		};
		fpsField.addListener(fieldListener);
		startFrameField.addListener(fieldListener);
		endFrameField.addListener(fieldListener);
		warmUpField.addListener(fieldListener);
		scaleField.addListener(fieldListener);
		paddingField.addListener(fieldListener);
		if (format == ImageSequenceFormat.PNG) compressionField.addListener(fieldListener);
		if (format == ImageSequenceFormat.JPEG) qualityField.addListener(fieldListener);
		transparentButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				transparent[0] = !transparent[0];
				refresh.run();
			}
		});
		premultipliedButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (premultipliedButton.isDisabled()) return;
				premultiplied[0] = !premultiplied[0];
				refresh.run();
			}
		});
		includeLastFrameButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				includeLastFrame[0] = !includeLastFrame[0];
				refresh.run();
			}
		});

		Runnable submit = () -> {
			if (export.isDisabled()) return;
			try {
				Skeleton snapshot = new Skeleton(doc.skeleton);
				snapshot.updateWorldTransform(Physics.none);
				long revision = doc.revision;
				TextureAtlas atlas = doc.atlas;
				boolean sourcePremultipliedAlpha = doc.premultipliedAlpha;
				if (format == ImageSequenceFormat.PNG) {
					PngAnimationExporter.Settings settings = pngSequenceExportSettings(fpsField.getText(),
						startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
						scaleField.getText(), paddingField.getText(), compressionField.getText(),
						transparent[0], premultiplied[0], includeLastFrame[0]);
					int animationEnd = PngAnimationExporter.maxAnimationFrame(animation, settings.fps());
					if (settings.endFrame() > animationEnd)
						throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
					preferences.putFloat(PREF_PNG_SEQUENCE_FPS, settings.fps());
					preferences.putInteger(PREF_PNG_SEQUENCE_WARM_UP, settings.warmUp());
					preferences.putFloat(PREF_PNG_SEQUENCE_SCALE, settings.scale());
					preferences.putInteger(PREF_PNG_SEQUENCE_PADDING, settings.padding());
					preferences.putInteger(PREF_PNG_SEQUENCE_COMPRESSION, settings.compression());
					preferences.putBoolean(PREF_PNG_SEQUENCE_TRANSPARENT, settings.transparent());
					preferences.putBoolean(PREF_PNG_SEQUENCE_PREMULTIPLIED,
						settings.premultipliedAlpha());
					preferences.putBoolean(PREF_PNG_SEQUENCE_INCLUDE_LAST_FRAME, settings.includeLastFrame());
					preferences.flush();
					close.run();
					beginPngSequenceExport(snapshot, animationName, atlas, revision,
						sourcePremultipliedAlpha, settings);
				} else {
					JpegAnimationExporter.Settings settings = jpegSequenceExportSettings(fpsField.getText(),
						startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
						scaleField.getText(), paddingField.getText(), qualityField.getText(), includeLastFrame[0]);
					int animationEnd = JpegAnimationExporter.maxAnimationFrame(animation, settings.fps());
					if (settings.endFrame() > animationEnd)
						throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
					preferences.putFloat(PREF_JPEG_SEQUENCE_FPS, settings.fps());
					preferences.putInteger(PREF_JPEG_SEQUENCE_WARM_UP, settings.warmUp());
					preferences.putFloat(PREF_JPEG_SEQUENCE_SCALE, settings.scale());
					preferences.putInteger(PREF_JPEG_SEQUENCE_PADDING, settings.padding());
					preferences.putInteger(PREF_JPEG_SEQUENCE_QUALITY, settings.quality());
					preferences.putBoolean(PREF_JPEG_SEQUENCE_INCLUDE_LAST_FRAME, settings.includeLastFrame());
					preferences.flush();
					close.run();
					beginJpegSequenceExport(snapshot, animationName, atlas, revision,
						sourcePremultipliedAlpha, settings);
				}
			} catch (RuntimeException error) {
				refresh.run();
			}
		};
		export.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		TextButton cancel = button("取消", close);
		addModalKeyActions(window, submit, close);

		window.row();
		window.add(new Label("帧率", kit.labelStyle)).right().pad(5);
		Table fpsRow = new Table();
		fpsRow.add(fpsField).width(120).height(28);
		fpsRow.add(new Label("FPS", kit.labelStyle)).padLeft(5);
		window.add(fpsRow).left().pad(5).row();
		window.add(new Label("帧范围", kit.labelStyle)).right().pad(5);
		Table rangeRow = new Table();
		rangeRow.add(startFrameField).width(70).height(28);
		rangeRow.add(new Label("至", kit.labelStyle)).pad(0, 5, 0, 5);
		rangeRow.add(endFrameField).width(70).height(28);
		window.add(rangeRow).left().pad(5).row();
		window.add(new Label("包含末帧", kit.labelStyle)).right().pad(5);
		window.add(includeLastFrameButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("预热次数", kit.labelStyle)).right().pad(5);
		window.add(warmUpField).width(120).height(28).left().pad(5).row();
		window.add(new Label("缩放", kit.labelStyle)).right().pad(5);
		Table scaleRow = new Table();
		scaleRow.add(scaleField).width(120).height(28);
		scaleRow.add(new Label("%", kit.labelStyle)).padLeft(5);
		window.add(scaleRow).left().pad(5).row();
		window.add(new Label("留白", kit.labelStyle)).right().pad(5);
		Table paddingRow = new Table();
		paddingRow.add(paddingField).width(120).height(28);
		paddingRow.add(new Label("px", kit.labelStyle)).padLeft(5);
		window.add(paddingRow).left().pad(5).row();
		if (format == ImageSequenceFormat.PNG) {
			window.add(new Label("压缩级别", kit.labelStyle)).right().pad(5);
			Table pngCompressionRow = new Table();
			pngCompressionRow.add(compressionField).width(120).height(28);
			pngCompressionRow.add(new Label("0-9", kit.labelStyle)).padLeft(5);
			window.add(pngCompressionRow).left().pad(5).row();
			window.add(new Label("透明背景", kit.labelStyle)).right().pad(5);
			window.add(transparentButton).width(110).height(28).left().pad(5).row();
			window.add(new Label("预乘 Alpha", kit.labelStyle)).right().pad(5);
			window.add(premultipliedButton).width(110).height(28).left().pad(5).row();
		} else {
			window.add(new Label("质量", kit.labelStyle)).right().pad(5);
			Table qualityRow = new Table();
			qualityRow.add(qualityField).width(120).height(28);
			qualityRow.add(new Label("%", kit.labelStyle)).padLeft(5);
			window.add(qualityRow).left().pad(5).row();
		}
		window.add(summaryLabel).colspan(2).left().pad(10, 5, 6, 5).row();
		Table actions = new Table();
		actions.add(cancel).width(86).height(28).padRight(6);
		actions.add(export).width(86).height(28);
		window.add(actions).colspan(2).right().pad(6, 5, 3, 5);
		refresh.run();
		showCentered(window);
		stage.setKeyboardFocus(fpsField);
		fpsField.selectAll();
	}

	private void beginPngSequenceExport(Skeleton snapshot, String animationName, TextureAtlas atlas,
		long revision, boolean sourcePremultipliedAlpha, PngAnimationExporter.Settings settings) {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		fileDialogOpen = true;
		setStatus("正在选择 PNG 序列导出位置...", false);
		String suggested = suggestedPngPath(doc.savePath, doc.filePath, lastSaveDirectory);
		try {
			DesktopLauncher.choosePngSequenceFileAsync(suggested, settings.startFrame(), settings.lastExportFrame(),
				(selection, error) -> pngSequenceExportDialogCompleted(selection, error, snapshot,
					animationName, atlas, revision, sourcePremultipliedAlpha, settings));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("PNG 序列文件对话框失败", error);
		}
	}

	private void pngSequenceExportDialogCompleted(DesktopLauncher.SequenceFileSelection selection,
		Throwable error, Skeleton snapshot, String animationName, TextureAtlas atlas, long revision,
		boolean sourcePremultipliedAlpha, PngAnimationExporter.Settings settings) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("PNG 序列文件对话框失败", error);
				return;
			}
			if (selection == null) return;
			if (doc.revision != revision || doc.atlas != atlas) {
				setStatus("文档在选择导出位置时已更改，请重新导出 PNG 序列", true);
				return;
			}
			try {
				Animation animation = snapshot.getData().findAnimation(animationName);
				if (animation == null) throw new IllegalStateException("导出动画已不存在：" + animationName);
				Path target = Path.of(selection.path()).toAbsolutePath().normalize();
				PngFrameExporter.Settings renderSettings = new PngFrameExporter.Settings(settings.scale(),
					settings.padding(), settings.compression(), settings.transparent(), settings.premultipliedAlpha());
				beginAnimationRender("导出 PNG 动画序列", target, snapshot, animation, settings.fps(),
					settings.warmUp(), settings.startFrame(), settings.lastExportFrame(), renderSettings,
					sourcePremultipliedAlpha, renderer -> {
						List<Path> targets = PngAnimationExporter.sequenceTargets(target,
							settings.startFrame(), settings.lastExportFrame());
						beginDocumentIo("提交 PNG 序列", "导出 PNG 动画序列", "正在原子提交全部帧", target,
							token -> {
								try {
									token.commit(() -> PngAnimationExporter.commitStaged(targets,
										renderer.frames(), selection.replaceExisting()));
									return new PngAnimationExporter.ExportResult(targets, renderer.size().width(),
										renderer.size().height(), renderer.frameCount(), renderer.bounds());
								} finally { closeAnimationRenderer(renderer); }
							}, result -> {
								rememberDirectory(target.toString(), true);
								Path first = result.paths().get(0).getFileName();
								Path last = result.paths().get(result.paths().size() - 1).getFileName();
								setStatus("已导出 PNG 序列（" + result.width() + " × " + result.height()
									+ " px · " + result.frameCount() + " 帧 · " + first + " 至 " + last + "）", false);
							}, () -> closeAnimationRenderer(renderer));
					});
			} catch (RuntimeException exportError) {
				showError("PNG 序列导出失败", exportError);
			}
		});
	}

	private void beginJpegSequenceExport(Skeleton snapshot, String animationName, TextureAtlas atlas,
		long revision, boolean sourcePremultipliedAlpha, JpegAnimationExporter.Settings settings) {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		fileDialogOpen = true;
		setStatus("正在选择 JPEG 序列导出位置...", false);
		String suggested = suggestedJpegPath(doc.savePath, doc.filePath, lastSaveDirectory);
		try {
			DesktopLauncher.chooseJpegSequenceFileAsync(suggested, settings.startFrame(), settings.lastExportFrame(),
				(selection, error) -> jpegSequenceExportDialogCompleted(selection, error, snapshot,
					animationName, atlas, revision, sourcePremultipliedAlpha, settings));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("JPEG 序列文件对话框失败", error);
		}
	}

	private void jpegSequenceExportDialogCompleted(DesktopLauncher.SequenceFileSelection selection,
		Throwable error, Skeleton snapshot, String animationName, TextureAtlas atlas, long revision,
		boolean sourcePremultipliedAlpha, JpegAnimationExporter.Settings settings) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("JPEG 序列文件对话框失败", error);
				return;
			}
			if (selection == null) return;
			if (doc.revision != revision || doc.atlas != atlas) {
				setStatus("文档在选择导出位置时已更改，请重新导出 JPEG 序列", true);
				return;
			}
			try {
				Animation animation = snapshot.getData().findAnimation(animationName);
				if (animation == null) throw new IllegalStateException("导出动画已不存在：" + animationName);
				Path target = Path.of(selection.path()).toAbsolutePath().normalize();
				PngFrameExporter.Settings renderSettings = new PngFrameExporter.Settings(settings.scale(),
					settings.padding(), 1, false, false);
				beginAnimationRender("导出 JPEG 动画序列", target, snapshot, animation, settings.fps(),
					settings.warmUp(), settings.startFrame(), settings.lastExportFrame(), renderSettings,
					sourcePremultipliedAlpha, renderer -> {
						List<Path> targets = JpegAnimationExporter.sequenceTargets(target,
							settings.startFrame(), settings.lastExportFrame());
						List<Path> staged = new ArrayList<>(targets.size());
						for (Path output : targets)
							staged.add(renderer.directory().resolve("jpeg").resolve(output.getFileName()));
						beginDocumentIo("编码 JPEG 序列", "导出 JPEG 动画序列", "正在后台编码并提交全部帧", target,
							token -> {
								try {
									JpegAnimationExporter.encodeRenderedPngs(renderer.frames(), staged,
										settings.quality(), token);
									token.commit(() -> JpegAnimationExporter.commitStaged(targets,
										staged, selection.replaceExisting()));
									return new JpegAnimationExporter.ExportResult(targets, renderer.size().width(),
										renderer.size().height(), renderer.frameCount(), renderer.bounds());
								} finally { closeAnimationRenderer(renderer); }
							}, result -> {
								rememberDirectory(target.toString(), true);
								Path first = result.paths().get(0).getFileName();
								Path last = result.paths().get(result.paths().size() - 1).getFileName();
								setStatus("已导出 JPEG 序列（" + result.width() + " × " + result.height()
									+ " px · " + result.frameCount() + " 帧 · " + first + " 至 " + last + "）", false);
							}, () -> closeAnimationRenderer(renderer));
					});
			} catch (RuntimeException exportError) {
				showError("JPEG 序列导出失败", exportError);
			}
		});
	}

	private void showGifExportSettings() {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		String animationName = doc.getCurrentAnimationName();
		Animation animation = doc.data == null || animationName.isBlank()
			? null : doc.data.findAnimation(animationName);
		if (doc.skeleton == null || animation == null) {
			setStatus("请先选择要导出的动画", true);
			return;
		}

		float projectFps = doc.data.getFps();
		if (!Float.isFinite(projectFps) || projectFps < GifAnimationExporter.MIN_FPS) projectFps = 30f;
		projectFps = Math.min(projectFps, GifAnimationExporter.MAX_FPS);
		float savedFps = preferences.getFloat(PREF_GIF_FPS, projectFps);
		if (!Float.isFinite(savedFps) || savedFps < GifAnimationExporter.MIN_FPS
			|| savedFps > GifAnimationExporter.MAX_FPS) savedFps = projectFps;
		int savedWarmUp = preferences.getInteger(PREF_GIF_WARM_UP, 0);
		if (savedWarmUp < 0 || savedWarmUp > GifAnimationExporter.MAX_WARM_UP) savedWarmUp = 0;
		float savedScale = preferences.getFloat(PREF_GIF_SCALE, 1f);
		if (!Float.isFinite(savedScale) || savedScale < PngFrameExporter.MIN_SCALE
			|| savedScale > PngFrameExporter.MAX_SCALE) savedScale = 1f;
		int savedPadding = preferences.getInteger(PREF_GIF_PADDING, 0);
		if (savedPadding < 0 || savedPadding > PngFrameExporter.MAX_PADDING) savedPadding = 0;
		int savedRepeat = preferences.getInteger(PREF_GIF_REPEAT, 0);
		if (savedRepeat < 0 || savedRepeat > GifAnimationExporter.MAX_REPEAT) savedRepeat = 0;
		int savedAlphaThreshold = preferences.getInteger(PREF_GIF_ALPHA_THRESHOLD,
			GifAnimationExporter.DEFAULT_ALPHA_THRESHOLD);
		if (savedAlphaThreshold < 0 || savedAlphaThreshold > 255)
			savedAlphaThreshold = GifAnimationExporter.DEFAULT_ALPHA_THRESHOLD;
		boolean[] transparent = {preferences.getBoolean(PREF_GIF_TRANSPARENT, true)};
		boolean[] infiniteLoop = {savedRepeat == 0};
		boolean[] includeLastFrame = {preferences.getBoolean(PREF_GIF_INCLUDE_LAST_FRAME, false)};
		int lastAnimationFrame;
		try {
			lastAnimationFrame = GifAnimationExporter.maxAnimationFrame(animation, savedFps);
		} catch (RuntimeException error) {
			showError("无法导出 GIF", error);
			return;
		}
		if (lastAnimationFrame == 0) includeLastFrame[0] = true;
		int defaultEndFrame = Math.min(lastAnimationFrame, Math.min(GifAnimationExporter.MAX_FRAME_INDEX,
			includeLastFrame[0] ? GifAnimationExporter.MAX_FRAMES - 1 : GifAnimationExporter.MAX_FRAMES));

		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("导出 GIF 动画");
		window.setModal(true);
		kit.padWindow(window, 10f);
		TextField fpsField = new TextField(UiNumbers.format(savedFps), kit.textFieldStyle);
		TextField startFrameField = new TextField("0", kit.textFieldStyle);
		TextField endFrameField = new TextField(Integer.toString(defaultEndFrame), kit.textFieldStyle);
		TextField warmUpField = new TextField(Integer.toString(savedWarmUp), kit.textFieldStyle);
		TextField scaleField = new TextField(UiNumbers.format(savedScale * 100f), kit.textFieldStyle);
		TextField paddingField = new TextField(Integer.toString(savedPadding), kit.textFieldStyle);
		TextField repeatField = new TextField(Integer.toString(Math.max(1, savedRepeat)), kit.textFieldStyle);
		TextField alphaThresholdField = new TextField(Integer.toString(savedAlphaThreshold), kit.textFieldStyle);
		TextButton transparentButton = new TextButton("", kit.toolButton);
		TextButton infiniteLoopButton = new TextButton("", kit.toolButton);
		TextButton includeLastFrameButton = new TextButton("", kit.toolButton);
		Label summaryLabel = new Label("", kit.labelStyle);
		TextButton export = new TextButton("导出", kit.accentButton);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);

		Runnable refresh = () -> {
			refreshPngToggle(transparentButton, transparent[0]);
			refreshPngToggle(infiniteLoopButton, infiniteLoop[0]);
			refreshPngToggle(includeLastFrameButton, includeLastFrame[0]);
			alphaThresholdField.setDisabled(!transparent[0]);
			repeatField.setDisabled(infiniteLoop[0]);
			try {
				GifAnimationExporter.Settings settings = gifExportSettings(fpsField.getText(),
					startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
					scaleField.getText(), paddingField.getText(), transparent[0], infiniteLoop[0], repeatField.getText(),
					alphaThresholdField.getText(), includeLastFrame[0]);
				int animationEnd = GifAnimationExporter.maxAnimationFrame(animation, settings.fps());
				if (settings.endFrame() > animationEnd)
					throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				summaryLabel.setText("动画：" + animationName + " · " + settings.frameCount()
					+ " 帧 · 预热 " + settings.warmUp() + " 次 · 末帧 " + animationEnd);
				setValidationLabel(summaryLabel, false);
				export.setDisabled(false);
			} catch (RuntimeException error) {
				String message = error.getMessage();
				summaryLabel.setText(message == null || message.isBlank() ? "GIF 设置无效" : message);
				setValidationLabel(summaryLabel, true);
				export.setDisabled(true);
			}
		};

		ChangeListener fieldListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refresh.run(); }
		};
		fpsField.addListener(fieldListener);
		startFrameField.addListener(fieldListener);
		endFrameField.addListener(fieldListener);
		warmUpField.addListener(fieldListener);
		scaleField.addListener(fieldListener);
		paddingField.addListener(fieldListener);
		repeatField.addListener(fieldListener);
		alphaThresholdField.addListener(fieldListener);
		transparentButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				transparent[0] = !transparent[0];
				refresh.run();
			}
		});
		infiniteLoopButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				infiniteLoop[0] = !infiniteLoop[0];
				refresh.run();
			}
		});
		includeLastFrameButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				includeLastFrame[0] = !includeLastFrame[0];
				refresh.run();
			}
		});

		Runnable submit = () -> {
			if (export.isDisabled()) return;
			try {
				GifAnimationExporter.Settings settings = gifExportSettings(fpsField.getText(),
					startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
					scaleField.getText(), paddingField.getText(), transparent[0], infiniteLoop[0], repeatField.getText(),
					alphaThresholdField.getText(), includeLastFrame[0]);
				int animationEnd = GifAnimationExporter.maxAnimationFrame(animation, settings.fps());
				if (settings.endFrame() > animationEnd)
					throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				Skeleton snapshot = new Skeleton(doc.skeleton);
				snapshot.updateWorldTransform(Physics.none);
				long revision = doc.revision;
				TextureAtlas atlas = doc.atlas;
				boolean sourcePremultipliedAlpha = doc.premultipliedAlpha;
				preferences.putFloat(PREF_GIF_FPS, settings.fps());
				preferences.putInteger(PREF_GIF_WARM_UP, settings.warmUp());
				preferences.putFloat(PREF_GIF_SCALE, settings.scale());
				preferences.putInteger(PREF_GIF_PADDING, settings.padding());
				preferences.putBoolean(PREF_GIF_TRANSPARENT, settings.transparent());
				preferences.putInteger(PREF_GIF_REPEAT, settings.repeat());
				preferences.putInteger(PREF_GIF_ALPHA_THRESHOLD, settings.alphaThreshold());
				preferences.putBoolean(PREF_GIF_INCLUDE_LAST_FRAME, settings.includeLastFrame());
				preferences.flush();
				close.run();
				beginGifExport(snapshot, animationName, atlas, revision,
					sourcePremultipliedAlpha, settings);
			} catch (RuntimeException error) {
				refresh.run();
			}
		};
		export.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		TextButton cancel = button("取消", close);
		addModalKeyActions(window, submit, close);

		window.row();
		window.add(new Label("帧率", kit.labelStyle)).right().pad(5);
		Table fpsRow = new Table();
		fpsRow.add(fpsField).width(120).height(28);
		fpsRow.add(new Label("FPS", kit.labelStyle)).padLeft(5);
		window.add(fpsRow).left().pad(5).row();
		window.add(new Label("帧范围", kit.labelStyle)).right().pad(5);
		Table rangeRow = new Table();
		rangeRow.add(startFrameField).width(70).height(28);
		rangeRow.add(new Label("至", kit.labelStyle)).pad(0, 5, 0, 5);
		rangeRow.add(endFrameField).width(70).height(28);
		window.add(rangeRow).left().pad(5).row();
		window.add(new Label("包含末帧", kit.labelStyle)).right().pad(5);
		window.add(includeLastFrameButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("预热次数", kit.labelStyle)).right().pad(5);
		window.add(warmUpField).width(120).height(28).left().pad(5).row();
		window.add(new Label("缩放", kit.labelStyle)).right().pad(5);
		Table scaleRow = new Table();
		scaleRow.add(scaleField).width(120).height(28);
		scaleRow.add(new Label("%", kit.labelStyle)).padLeft(5);
		window.add(scaleRow).left().pad(5).row();
		window.add(new Label("留白", kit.labelStyle)).right().pad(5);
		Table paddingRow = new Table();
		paddingRow.add(paddingField).width(120).height(28);
		paddingRow.add(new Label("px", kit.labelStyle)).padLeft(5);
		window.add(paddingRow).left().pad(5).row();
		window.add(new Label("透明背景", kit.labelStyle)).right().pad(5);
		window.add(transparentButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("透明阈值", kit.labelStyle)).right().pad(5);
		window.add(alphaThresholdField).width(120).height(28).left().pad(5).row();
		window.add(new Label("无限循环", kit.labelStyle)).right().pad(5);
		window.add(infiniteLoopButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("循环次数", kit.labelStyle)).right().pad(5);
		window.add(repeatField).width(120).height(28).left().pad(5).row();
		window.add(summaryLabel).colspan(2).left().pad(10, 5, 6, 5).row();
		Table actions = new Table();
		actions.add(cancel).width(86).height(28).padRight(6);
		actions.add(export).width(86).height(28);
		window.add(actions).colspan(2).right().pad(6, 5, 3, 5);
		refresh.run();
		showCentered(window);
		stage.setKeyboardFocus(fpsField);
		fpsField.selectAll();
	}

	private void beginGifExport(Skeleton snapshot, String animationName, TextureAtlas atlas, long revision,
		boolean sourcePremultipliedAlpha, GifAnimationExporter.Settings settings) {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		fileDialogOpen = true;
		setStatus("正在选择 GIF 导出位置...", false);
		String suggested = suggestedGifPath(doc.savePath, doc.filePath, lastSaveDirectory);
		try {
			DesktopLauncher.chooseGifFileAsync(suggested,
				(path, error) -> gifExportDialogCompleted(path, error, snapshot, animationName,
					atlas, revision, sourcePremultipliedAlpha, settings));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("GIF 文件对话框失败", error);
		}
	}

	private void gifExportDialogCompleted(String path, Throwable error, Skeleton snapshot, String animationName,
		TextureAtlas atlas, long revision, boolean sourcePremultipliedAlpha,
		GifAnimationExporter.Settings settings) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("GIF 文件对话框失败", error);
				return;
			}
			if (path == null || path.isBlank()) return;
			if (doc.revision != revision || doc.atlas != atlas) {
				setStatus("文档在选择导出位置时已更改，请重新导出 GIF", true);
				return;
			}
			try {
				Animation animation = snapshot.getData().findAnimation(animationName);
				if (animation == null) throw new IllegalStateException("导出动画已不存在：" + animationName);
				Path target = Path.of(path).toAbsolutePath().normalize();
				PngFrameExporter.Settings renderSettings = new PngFrameExporter.Settings(settings.scale(),
					settings.padding(), 1, settings.transparent(), false);
				beginAnimationRender("导出 GIF 动画", target, snapshot, animation, settings.fps(),
					settings.warmUp(), settings.startFrame(), settings.lastExportFrame(), renderSettings,
					sourcePremultipliedAlpha, renderer -> beginDocumentIo("编码 GIF", "导出 GIF 动画",
						"正在后台编码动画", target, token -> {
							try {
								GifAnimationExporter.encodeRenderedPngs(renderer.frames(), target, settings, token);
								return new GifAnimationExporter.ExportResult(target, renderer.size().width(),
									renderer.size().height(), renderer.frameCount(), renderer.bounds());
							} finally { closeAnimationRenderer(renderer); }
						}, result -> {
							rememberDirectory(target.toString(), true);
							setStatus("已导出 " + target.getFileName() + "（" + result.width() + " × "
								+ result.height() + " px · " + result.frameCount() + " 帧）", false);
						}, () -> closeAnimationRenderer(renderer)));
			} catch (RuntimeException exportError) {
				showError("GIF 导出失败", exportError);
			}
		});
	}

	private void showApngExportSettings() {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		String animationName = doc.getCurrentAnimationName();
		Animation animation = doc.data == null || animationName.isBlank()
			? null : doc.data.findAnimation(animationName);
		if (doc.skeleton == null || animation == null) {
			setStatus("请先选择要导出的动画", true);
			return;
		}

		float projectFps = doc.data.getFps();
		if (!Float.isFinite(projectFps) || projectFps < ApngAnimationExporter.MIN_FPS) projectFps = 30f;
		projectFps = Math.min(projectFps, ApngAnimationExporter.MAX_FPS);
		float savedFps = preferences.getFloat(PREF_APNG_FPS, projectFps);
		if (!Float.isFinite(savedFps) || savedFps < ApngAnimationExporter.MIN_FPS
			|| savedFps > ApngAnimationExporter.MAX_FPS) savedFps = projectFps;
		int savedWarmUp = preferences.getInteger(PREF_APNG_WARM_UP, 0);
		if (savedWarmUp < 0 || savedWarmUp > ApngAnimationExporter.MAX_WARM_UP) savedWarmUp = 0;
		float savedScale = preferences.getFloat(PREF_APNG_SCALE, 1f);
		if (!Float.isFinite(savedScale) || savedScale < PngFrameExporter.MIN_SCALE
			|| savedScale > PngFrameExporter.MAX_SCALE) savedScale = 1f;
		int savedPadding = preferences.getInteger(PREF_APNG_PADDING, 0);
		if (savedPadding < 0 || savedPadding > PngFrameExporter.MAX_PADDING) savedPadding = 0;
		int savedRepeat = preferences.getInteger(PREF_APNG_REPEAT, 0);
		if (savedRepeat < 0 || savedRepeat > ApngAnimationExporter.MAX_REPEAT) savedRepeat = 0;
		int savedCompression = preferences.getInteger(PREF_APNG_COMPRESSION,
			ApngAnimationExporter.DEFAULT_COMPRESSION);
		if (savedCompression < ApngAnimationExporter.MIN_COMPRESSION
			|| savedCompression > ApngAnimationExporter.MAX_COMPRESSION)
			savedCompression = ApngAnimationExporter.DEFAULT_COMPRESSION;
		boolean[] transparent = {preferences.getBoolean(PREF_APNG_TRANSPARENT, true)};
		boolean[] infiniteLoop = {savedRepeat == 0};
		boolean[] includeLastFrame = {preferences.getBoolean(PREF_APNG_INCLUDE_LAST_FRAME, false)};
		int lastAnimationFrame;
		try {
			lastAnimationFrame = ApngAnimationExporter.maxAnimationFrame(animation, savedFps);
		} catch (RuntimeException error) {
			showError("无法导出 APNG", error);
			return;
		}
		if (lastAnimationFrame == 0) includeLastFrame[0] = true;
		int defaultEndFrame = Math.min(lastAnimationFrame, Math.min(ApngAnimationExporter.MAX_FRAME_INDEX,
			includeLastFrame[0] ? ApngAnimationExporter.MAX_FRAMES - 1 : ApngAnimationExporter.MAX_FRAMES));

		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("导出 APNG 动画");
		window.setModal(true);
		kit.padWindow(window, 10f);
		TextField fpsField = new TextField(UiNumbers.format(savedFps), kit.textFieldStyle);
		TextField startFrameField = new TextField("0", kit.textFieldStyle);
		TextField endFrameField = new TextField(Integer.toString(defaultEndFrame), kit.textFieldStyle);
		TextField warmUpField = new TextField(Integer.toString(savedWarmUp), kit.textFieldStyle);
		TextField scaleField = new TextField(UiNumbers.format(savedScale * 100f), kit.textFieldStyle);
		TextField paddingField = new TextField(Integer.toString(savedPadding), kit.textFieldStyle);
		TextField repeatField = new TextField(Integer.toString(Math.max(1, savedRepeat)), kit.textFieldStyle);
		TextField compressionField = new TextField(Integer.toString(savedCompression), kit.textFieldStyle);
		TextButton transparentButton = new TextButton("", kit.toolButton);
		TextButton infiniteLoopButton = new TextButton("", kit.toolButton);
		TextButton includeLastFrameButton = new TextButton("", kit.toolButton);
		Label summaryLabel = new Label("", kit.labelStyle);
		TextButton export = new TextButton("导出", kit.accentButton);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);

		Runnable refresh = () -> {
			refreshPngToggle(transparentButton, transparent[0]);
			refreshPngToggle(infiniteLoopButton, infiniteLoop[0]);
			refreshPngToggle(includeLastFrameButton, includeLastFrame[0]);
			repeatField.setDisabled(infiniteLoop[0]);
			try {
				ApngAnimationExporter.Settings settings = apngExportSettings(fpsField.getText(),
					startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
					scaleField.getText(), paddingField.getText(), transparent[0], infiniteLoop[0], repeatField.getText(),
					compressionField.getText(), includeLastFrame[0]);
				int animationEnd = ApngAnimationExporter.maxAnimationFrame(animation, settings.fps());
				if (settings.endFrame() > animationEnd)
					throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				summaryLabel.setText("动画：" + animationName + " · " + settings.frameCount()
					+ " 帧 · 预热 " + settings.warmUp() + " 次 · 末帧 " + animationEnd);
				setValidationLabel(summaryLabel, false);
				export.setDisabled(false);
			} catch (RuntimeException error) {
				String message = error.getMessage();
				summaryLabel.setText(message == null || message.isBlank() ? "APNG 设置无效" : message);
				setValidationLabel(summaryLabel, true);
				export.setDisabled(true);
			}
		};

		ChangeListener fieldListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refresh.run(); }
		};
		fpsField.addListener(fieldListener);
		startFrameField.addListener(fieldListener);
		endFrameField.addListener(fieldListener);
		warmUpField.addListener(fieldListener);
		scaleField.addListener(fieldListener);
		paddingField.addListener(fieldListener);
		repeatField.addListener(fieldListener);
		compressionField.addListener(fieldListener);
		transparentButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				transparent[0] = !transparent[0];
				refresh.run();
			}
		});
		infiniteLoopButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				infiniteLoop[0] = !infiniteLoop[0];
				refresh.run();
			}
		});
		includeLastFrameButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				includeLastFrame[0] = !includeLastFrame[0];
				refresh.run();
			}
		});

		Runnable submit = () -> {
			if (export.isDisabled()) return;
			try {
				ApngAnimationExporter.Settings settings = apngExportSettings(fpsField.getText(),
					startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
					scaleField.getText(), paddingField.getText(), transparent[0], infiniteLoop[0], repeatField.getText(),
					compressionField.getText(), includeLastFrame[0]);
				int animationEnd = ApngAnimationExporter.maxAnimationFrame(animation, settings.fps());
				if (settings.endFrame() > animationEnd)
					throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				Skeleton snapshot = new Skeleton(doc.skeleton);
				snapshot.updateWorldTransform(Physics.none);
				long revision = doc.revision;
				TextureAtlas atlas = doc.atlas;
				boolean sourcePremultipliedAlpha = doc.premultipliedAlpha;
				preferences.putFloat(PREF_APNG_FPS, settings.fps());
				preferences.putInteger(PREF_APNG_WARM_UP, settings.warmUp());
				preferences.putFloat(PREF_APNG_SCALE, settings.scale());
				preferences.putInteger(PREF_APNG_PADDING, settings.padding());
				preferences.putBoolean(PREF_APNG_TRANSPARENT, settings.transparent());
				preferences.putInteger(PREF_APNG_REPEAT, settings.repeat());
				preferences.putInteger(PREF_APNG_COMPRESSION, settings.compression());
				preferences.putBoolean(PREF_APNG_INCLUDE_LAST_FRAME, settings.includeLastFrame());
				preferences.flush();
				close.run();
				beginApngExport(snapshot, animationName, atlas, revision,
					sourcePremultipliedAlpha, settings);
			} catch (RuntimeException error) {
				refresh.run();
			}
		};
		export.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		TextButton cancel = button("取消", close);
		addModalKeyActions(window, submit, close);

		window.row();
		window.add(new Label("帧率", kit.labelStyle)).right().pad(5);
		Table fpsRow = new Table();
		fpsRow.add(fpsField).width(120).height(28);
		fpsRow.add(new Label("FPS", kit.labelStyle)).padLeft(5);
		window.add(fpsRow).left().pad(5).row();
		window.add(new Label("帧范围", kit.labelStyle)).right().pad(5);
		Table rangeRow = new Table();
		rangeRow.add(startFrameField).width(70).height(28);
		rangeRow.add(new Label("至", kit.labelStyle)).pad(0, 5, 0, 5);
		rangeRow.add(endFrameField).width(70).height(28);
		window.add(rangeRow).left().pad(5).row();
		window.add(new Label("预热次数", kit.labelStyle)).right().pad(5);
		window.add(warmUpField).width(120).height(28).left().pad(5).row();
		window.add(new Label("缩放", kit.labelStyle)).right().pad(5);
		Table scaleRow = new Table();
		scaleRow.add(scaleField).width(120).height(28);
		scaleRow.add(new Label("%", kit.labelStyle)).padLeft(5);
		window.add(scaleRow).left().pad(5).row();
		window.add(new Label("留白", kit.labelStyle)).right().pad(5);
		Table paddingRow = new Table();
		paddingRow.add(paddingField).width(120).height(28);
		paddingRow.add(new Label("px", kit.labelStyle)).padLeft(5);
		window.add(paddingRow).left().pad(5).row();
		window.add(new Label("透明背景", kit.labelStyle)).right().pad(5);
		window.add(transparentButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("无限循环", kit.labelStyle)).right().pad(5);
		window.add(infiniteLoopButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("播放次数", kit.labelStyle)).right().pad(5);
		window.add(repeatField).width(120).height(28).left().pad(5).row();
		window.add(new Label("包含末帧", kit.labelStyle)).right().pad(5);
		window.add(includeLastFrameButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("压缩级别", kit.labelStyle)).right().pad(5);
		Table compressionRow = new Table();
		compressionRow.add(compressionField).width(120).height(28);
		compressionRow.add(new Label("0-9", kit.labelStyle)).padLeft(5);
		window.add(compressionRow).left().pad(5).row();
		window.add(summaryLabel).colspan(2).left().pad(10, 5, 6, 5).row();
		Table actions = new Table();
		actions.add(cancel).width(86).height(28).padRight(6);
		actions.add(export).width(86).height(28);
		window.add(actions).colspan(2).right().pad(6, 5, 3, 5);
		refresh.run();
		showCentered(window);
		stage.setKeyboardFocus(fpsField);
		fpsField.selectAll();
	}

	private void beginApngExport(Skeleton snapshot, String animationName, TextureAtlas atlas, long revision,
		boolean sourcePremultipliedAlpha, ApngAnimationExporter.Settings settings) {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		fileDialogOpen = true;
		setStatus("正在选择 APNG 导出位置...", false);
		String suggested = suggestedApngPath(doc.savePath, doc.filePath, lastSaveDirectory);
		try {
			DesktopLauncher.chooseApngFileAsync(suggested,
				(path, error) -> apngExportDialogCompleted(path, error, snapshot, animationName,
					atlas, revision, sourcePremultipliedAlpha, settings));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("APNG 文件对话框失败", error);
		}
	}

	private void apngExportDialogCompleted(String path, Throwable error, Skeleton snapshot, String animationName,
		TextureAtlas atlas, long revision, boolean sourcePremultipliedAlpha,
		ApngAnimationExporter.Settings settings) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("APNG 文件对话框失败", error);
				return;
			}
			if (path == null || path.isBlank()) return;
			if (doc.revision != revision || doc.atlas != atlas) {
				setStatus("文档在选择导出位置时已更改，请重新导出 APNG", true);
				return;
			}
			try {
				Animation animation = snapshot.getData().findAnimation(animationName);
				if (animation == null) throw new IllegalStateException("导出动画已不存在：" + animationName);
				Path target = Path.of(path).toAbsolutePath().normalize();
				PngFrameExporter.Settings renderSettings = new PngFrameExporter.Settings(settings.scale(),
					settings.padding(), 1, settings.transparent(), false);
				beginAnimationRender("导出 APNG 动画", target, snapshot, animation, settings.fps(),
					settings.warmUp(), settings.startFrame(), settings.lastExportFrame(), renderSettings,
					sourcePremultipliedAlpha, renderer -> beginDocumentIo("编码 APNG", "导出 APNG 动画",
						"正在后台编码动画", target, token -> {
							try {
								ApngAnimationExporter.encodeRenderedPngs(renderer.frames(), target, settings, token);
								return new ApngAnimationExporter.ExportResult(target, renderer.size().width(),
									renderer.size().height(), renderer.frameCount(), renderer.bounds());
							} finally { closeAnimationRenderer(renderer); }
						}, result -> {
							rememberDirectory(target.toString(), true);
							setStatus("已导出 " + target.getFileName() + "（" + result.width() + " × "
								+ result.height() + " px · " + result.frameCount() + " 帧）", false);
						}, () -> closeAnimationRenderer(renderer)));
			} catch (RuntimeException exportError) {
				showError("APNG 导出失败", exportError);
			}
		});
	}

	private void showAviExportSettings() {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		String animationName = doc.getCurrentAnimationName();
		Animation animation = doc.data == null || animationName.isBlank()
			? null : doc.data.findAnimation(animationName);
		if (doc.skeleton == null || animation == null) {
			setStatus("请先选择要导出的动画", true);
			return;
		}

		float projectFps = doc.data.getFps();
		if (!Float.isFinite(projectFps) || projectFps < AviAnimationExporter.MIN_FPS) projectFps = 30f;
		projectFps = Math.min(projectFps, AviAnimationExporter.MAX_FPS);
		float savedFps = preferences.getFloat(PREF_AVI_FPS, projectFps);
		if (!Float.isFinite(savedFps) || savedFps < AviAnimationExporter.MIN_FPS
			|| savedFps > AviAnimationExporter.MAX_FPS) savedFps = projectFps;
		int savedWarmUp = preferences.getInteger(PREF_AVI_WARM_UP, 0);
		if (savedWarmUp < 0 || savedWarmUp > AviAnimationExporter.MAX_WARM_UP) savedWarmUp = 0;
		float savedScale = preferences.getFloat(PREF_AVI_SCALE, 1f);
		if (!Float.isFinite(savedScale) || savedScale < PngFrameExporter.MIN_SCALE
			|| savedScale > PngFrameExporter.MAX_SCALE) savedScale = 1f;
		int savedPadding = preferences.getInteger(PREF_AVI_PADDING, 0);
		if (savedPadding < 0 || savedPadding > PngFrameExporter.MAX_PADDING) savedPadding = 0;
		int savedQuality = preferences.getInteger(PREF_AVI_QUALITY, JpegFrameExporter.DEFAULT_QUALITY);
		if (savedQuality < JpegFrameExporter.MIN_QUALITY || savedQuality > JpegFrameExporter.MAX_QUALITY)
			savedQuality = JpegFrameExporter.DEFAULT_QUALITY;
		boolean[] includeLastFrame = {preferences.getBoolean(PREF_AVI_INCLUDE_LAST_FRAME, false)};
		int lastAnimationFrame;
		try {
			lastAnimationFrame = AviAnimationExporter.maxAnimationFrame(animation, savedFps);
		} catch (RuntimeException error) {
			showError("无法导出 AVI", error);
			return;
		}
		if (lastAnimationFrame == 0) includeLastFrame[0] = true;
		int defaultEndFrame = Math.min(lastAnimationFrame, Math.min(AviAnimationExporter.MAX_FRAME_INDEX,
			includeLastFrame[0] ? AviAnimationExporter.MAX_FRAMES - 1 : AviAnimationExporter.MAX_FRAMES));

		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("导出 AVI 动画");
		window.setModal(true);
		kit.padWindow(window, 10f);
		TextField fpsField = new TextField(UiNumbers.format(savedFps), kit.textFieldStyle);
		TextField startFrameField = new TextField("0", kit.textFieldStyle);
		TextField endFrameField = new TextField(Integer.toString(defaultEndFrame), kit.textFieldStyle);
		TextField warmUpField = new TextField(Integer.toString(savedWarmUp), kit.textFieldStyle);
		TextField scaleField = new TextField(UiNumbers.format(savedScale * 100f), kit.textFieldStyle);
		TextField paddingField = new TextField(Integer.toString(savedPadding), kit.textFieldStyle);
		TextField qualityField = new TextField(Integer.toString(savedQuality), kit.textFieldStyle);
		TextButton includeLastFrameButton = new TextButton("", kit.toolButton);
		Label summaryLabel = new Label("", kit.labelStyle);
		TextButton export = new TextButton("导出", kit.accentButton);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);

		Runnable refresh = () -> {
			refreshPngToggle(includeLastFrameButton, includeLastFrame[0]);
			try {
				AviAnimationExporter.Settings settings = aviExportSettings(fpsField.getText(),
					startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
					scaleField.getText(), paddingField.getText(), qualityField.getText(), includeLastFrame[0]);
				int animationEnd = AviAnimationExporter.maxAnimationFrame(animation, settings.fps());
				if (settings.endFrame() > animationEnd)
					throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				summaryLabel.setText("动画：" + animationName + " · " + settings.frameCount()
					+ " 帧 · 预热 " + settings.warmUp() + " 次 · MJPEG · 末帧 " + animationEnd);
				setValidationLabel(summaryLabel, false);
				export.setDisabled(false);
			} catch (RuntimeException error) {
				String message = error.getMessage();
				summaryLabel.setText(message == null || message.isBlank() ? "AVI 设置无效" : message);
				setValidationLabel(summaryLabel, true);
				export.setDisabled(true);
			}
		};

		ChangeListener fieldListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refresh.run(); }
		};
		fpsField.addListener(fieldListener);
		startFrameField.addListener(fieldListener);
		endFrameField.addListener(fieldListener);
		warmUpField.addListener(fieldListener);
		scaleField.addListener(fieldListener);
		paddingField.addListener(fieldListener);
		qualityField.addListener(fieldListener);
		includeLastFrameButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				includeLastFrame[0] = !includeLastFrame[0];
				refresh.run();
			}
		});

		Runnable submit = () -> {
			if (export.isDisabled()) return;
			try {
				AviAnimationExporter.Settings settings = aviExportSettings(fpsField.getText(),
					startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
					scaleField.getText(), paddingField.getText(), qualityField.getText(), includeLastFrame[0]);
				int animationEnd = AviAnimationExporter.maxAnimationFrame(animation, settings.fps());
				if (settings.endFrame() > animationEnd)
					throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				Skeleton snapshot = new Skeleton(doc.skeleton);
				snapshot.updateWorldTransform(Physics.none);
				long revision = doc.revision;
				TextureAtlas atlas = doc.atlas;
				boolean sourcePremultipliedAlpha = doc.premultipliedAlpha;
				preferences.putFloat(PREF_AVI_FPS, settings.fps());
				preferences.putInteger(PREF_AVI_WARM_UP, settings.warmUp());
				preferences.putFloat(PREF_AVI_SCALE, settings.scale());
				preferences.putInteger(PREF_AVI_PADDING, settings.padding());
				preferences.putInteger(PREF_AVI_QUALITY, settings.quality());
				preferences.putBoolean(PREF_AVI_INCLUDE_LAST_FRAME, settings.includeLastFrame());
				preferences.flush();
				close.run();
				beginAviExport(snapshot, animationName, atlas, revision,
					sourcePremultipliedAlpha, settings);
			} catch (RuntimeException error) {
				refresh.run();
			}
		};
		export.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		TextButton cancel = button("取消", close);
		addModalKeyActions(window, submit, close);

		window.row();
		window.add(new Label("编码", kit.labelStyle)).right().pad(5);
		window.add(new Label("MJPEG", kit.labelStyle)).left().pad(5).row();
		window.add(new Label("帧率", kit.labelStyle)).right().pad(5);
		Table fpsRow = new Table();
		fpsRow.add(fpsField).width(120).height(28);
		fpsRow.add(new Label("FPS", kit.labelStyle)).padLeft(5);
		window.add(fpsRow).left().pad(5).row();
		window.add(new Label("帧范围", kit.labelStyle)).right().pad(5);
		Table rangeRow = new Table();
		rangeRow.add(startFrameField).width(70).height(28);
		rangeRow.add(new Label("至", kit.labelStyle)).pad(0, 5, 0, 5);
		rangeRow.add(endFrameField).width(70).height(28);
		window.add(rangeRow).left().pad(5).row();
		window.add(new Label("包含末帧", kit.labelStyle)).right().pad(5);
		window.add(includeLastFrameButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("预热次数", kit.labelStyle)).right().pad(5);
		window.add(warmUpField).width(120).height(28).left().pad(5).row();
		window.add(new Label("缩放", kit.labelStyle)).right().pad(5);
		Table scaleRow = new Table();
		scaleRow.add(scaleField).width(120).height(28);
		scaleRow.add(new Label("%", kit.labelStyle)).padLeft(5);
		window.add(scaleRow).left().pad(5).row();
		window.add(new Label("留白", kit.labelStyle)).right().pad(5);
		Table paddingRow = new Table();
		paddingRow.add(paddingField).width(120).height(28);
		paddingRow.add(new Label("px", kit.labelStyle)).padLeft(5);
		window.add(paddingRow).left().pad(5).row();
		window.add(new Label("质量", kit.labelStyle)).right().pad(5);
		Table qualityRow = new Table();
		qualityRow.add(qualityField).width(120).height(28);
		qualityRow.add(new Label("%", kit.labelStyle)).padLeft(5);
		window.add(qualityRow).left().pad(5).row();
		window.add(summaryLabel).colspan(2).left().pad(10, 5, 6, 5).row();
		Table actions = new Table();
		actions.add(cancel).width(86).height(28).padRight(6);
		actions.add(export).width(86).height(28);
		window.add(actions).colspan(2).right().pad(6, 5, 3, 5);
		refresh.run();
		showCentered(window);
		stage.setKeyboardFocus(fpsField);
		fpsField.selectAll();
	}

	private void beginAviExport(Skeleton snapshot, String animationName, TextureAtlas atlas, long revision,
		boolean sourcePremultipliedAlpha, AviAnimationExporter.Settings settings) {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		fileDialogOpen = true;
		setStatus("正在选择 AVI 导出位置...", false);
		String suggested = suggestedAviPath(doc.savePath, doc.filePath, lastSaveDirectory);
		try {
			DesktopLauncher.chooseAviFileAsync(suggested,
				(path, error) -> aviExportDialogCompleted(path, error, snapshot, animationName,
					atlas, revision, sourcePremultipliedAlpha, settings));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("AVI 文件对话框失败", error);
		}
	}

	private void aviExportDialogCompleted(String path, Throwable error, Skeleton snapshot, String animationName,
		TextureAtlas atlas, long revision, boolean sourcePremultipliedAlpha,
		AviAnimationExporter.Settings settings) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("AVI 文件对话框失败", error);
				return;
			}
			if (path == null || path.isBlank()) return;
			if (doc.revision != revision || doc.atlas != atlas) {
				setStatus("文档在选择导出位置时已更改，请重新导出 AVI", true);
				return;
			}
			try {
				Animation animation = snapshot.getData().findAnimation(animationName);
				if (animation == null) throw new IllegalStateException("导出动画已不存在：" + animationName);
				Path target = Path.of(path).toAbsolutePath().normalize();
				PngFrameExporter.Settings renderSettings = new PngFrameExporter.Settings(settings.scale(),
					settings.padding(), 1, false, false);
				beginAnimationRender("导出 AVI 动画", target, snapshot, animation, settings.fps(),
					settings.warmUp(), settings.startFrame(), settings.lastExportFrame(), renderSettings,
					sourcePremultipliedAlpha, renderer -> beginDocumentIo("编码 AVI", "导出 AVI 动画",
						"正在后台编码 MJPEG", target, token -> {
							try {
								AviAnimationExporter.encodeRenderedPngs(renderer.frames(), target, settings, token);
								return new AviAnimationExporter.ExportResult(target, renderer.size().width(),
									renderer.size().height(), renderer.frameCount(), renderer.bounds());
							} finally { closeAnimationRenderer(renderer); }
						}, result -> {
							rememberDirectory(target.toString(), true);
							setStatus("已导出 " + target.getFileName() + "（" + result.width() + " × "
								+ result.height() + " px · " + result.frameCount() + " 帧 · MJPEG）", false);
						}, () -> closeAnimationRenderer(renderer)));
			} catch (RuntimeException exportError) {
				showError("AVI 导出失败", exportError);
			}
		});
	}

	private void packageProject() {
		finishWorkspaceTextEdit();
		if (doc.data == null) {
			setStatus("没有可打包的项目数据", true);
			return;
		}
		if (documentIoInProgress()) {
			setStatus("已有保存或打包任务正在执行", false);
			return;
		}
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		saveCurrentSpineProjectState();
		fileDialogOpen = true;
		String suggested = suggestedPackagePath(doc.savePath, doc.filePath, lastSaveDirectory);
		try {
			DesktopLauncher.choosePackageFileAsync(suggested, this::packageDialogCompleted);
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("打包文件对话框失败", error);
		}
	}

	private void showWebpExport() {
		if (!WebpFrameExporter.isAvailable()) {
			showError("WebP 导出不可用", new IllegalStateException("当前 Java 环境没有 WebP ImageIO 编码器"));
			return;
		}
		long revision = doc.revision;
		String suggested = suggestedPngPath(doc.savePath, doc.filePath, lastSaveDirectory)
			.replaceFirst("(?i)\\.png$", ".webp");
		fileDialogOpen = true;
		DesktopLauncher.chooseWebpFileAsync(suggested, (path, error) -> postToRenderThread(() -> {
			fileDialogOpen = false;
			if (error != null) { showError("WebP 文件对话框失败", error); return; }
			if (path == null || path.isBlank()) return;
			if (revision != doc.revision) { setStatus("文档已更改，请重新导出 WebP", true); return; }
			try {
				WebpFrameExporter.ExportResult result = WebpFrameExporter.write(doc.skeleton,
					Path.of(path), new PngFrameExporter.Settings(1f, 0, 6, true, false), doc.premultipliedAlpha);
				rememberDirectory(path, true);
				setStatus("已导出 " + Path.of(path).getFileName() + "（" + result.width() + " × "
					+ result.height() + " px）", false);
			} catch (IOException | RuntimeException exportError) { showError("WebP 导出失败", exportError); }
		}));
	}

	private void showPsdExport() {
		long revision = doc.revision;
		String suggested = suggestedPngPath(doc.savePath, doc.filePath, lastSaveDirectory)
			.replaceFirst("(?i)\\.png$", ".psd");
		fileDialogOpen = true;
		DesktopLauncher.choosePsdFileAsync(suggested, (path, error) -> postToRenderThread(() -> {
			fileDialogOpen = false;
			if (error != null) { showError("PSD 文件对话框失败", error); return; }
			if (path == null || path.isBlank()) return;
			if (revision != doc.revision) { setStatus("文档已更改，请重新导出 PSD", true); return; }
			try {
				PsdFrameExporter.ExportResult result = PsdFrameExporter.write(doc.skeleton, Path.of(path),
					new PngFrameExporter.Settings(1f, 0, 6, true, false), doc.premultipliedAlpha);
				rememberDirectory(path, true);
				setStatus("已导出 " + Path.of(path).getFileName() + "（" + result.width() + " × "
					+ result.height() + " px）", false);
			} catch (IOException | RuntimeException exportError) { showError("PSD 导出失败", exportError); }
		}));
	}

	private void showHtmlExport() {
		String suggested = suggestedSavePath(doc.savePath, doc.filePath, lastSaveDirectory)
			.replaceFirst("(?i)\\.(json|skel)$", ".html");
		fileDialogOpen = true;
		DesktopLauncher.chooseHtmlFileAsync(suggested, (path, error) -> postToRenderThread(() -> {
			fileDialogOpen = false;
			if (error != null) { showError("HTML 文件对话框失败", error); return; }
			if (path == null || path.isBlank()) return;
			try {
				Path target = HtmlExporter.write(doc, Path.of(path));
				rememberDirectory(path, true);
				setStatus("已导出 HTML 动画播放器：" + target.getFileName(), false);
			} catch (IOException | RuntimeException exportError) { showError("HTML 动画播放器导出失败", exportError); }
		}));
	}

	private void showVideoExport(FfmpegAnimationExporter.Format format) {
		String formatLabel = ffmpegFormatLabel(format);
		if (!FfmpegAnimationExporter.isAvailable()) {
			showError(formatLabel + " 导出不可用", new IllegalStateException("未找到 ffmpeg，请设置 OPENSPINE_FFMPEG 或将 ffmpeg 加入 PATH"));
			return;
		}
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		String animationName = doc.getCurrentAnimationName();
		Animation animation = doc.data == null || animationName.isBlank()
			? null : doc.data.findAnimation(animationName);
		if (doc.skeleton == null || animation == null) {
			setStatus("请先选择要导出的动画", true);
			return;
		}

		float projectFps = doc.data.getFps();
		if (!Float.isFinite(projectFps) || projectFps < 1f || projectFps > 100f) projectFps = 30f;
		float savedFps = preferences.getFloat(PREF_VIDEO_FPS, projectFps);
		if (!Float.isFinite(savedFps) || savedFps < 1f || savedFps > 100f) savedFps = projectFps;
		int savedWarmUp = preferences.getInteger(PREF_VIDEO_WARM_UP, 0);
		if (savedWarmUp < 0 || savedWarmUp > PngAnimationExporter.MAX_WARM_UP) savedWarmUp = 0;
		float savedScale = preferences.getFloat(PREF_VIDEO_SCALE, 1f);
		if (!Float.isFinite(savedScale) || savedScale < PngFrameExporter.MIN_SCALE
			|| savedScale > PngFrameExporter.MAX_SCALE) savedScale = 1f;
		int savedPadding = preferences.getInteger(PREF_VIDEO_PADDING, 0);
		if (savedPadding < 0 || savedPadding > PngFrameExporter.MAX_PADDING) savedPadding = 0;
		boolean[] transparent = {preferences.getBoolean(PREF_VIDEO_TRANSPARENT, true)};
		String savedBackground = preferences.getString(PREF_VIDEO_BACKGROUND, "#131418");
		try { UiColors.parseRgb(savedBackground); }
		catch (RuntimeException ignored) { savedBackground = "#131418"; }
		FfmpegAnimationExporter.Profile savedProfile;
		try {
			savedProfile = FfmpegAnimationExporter.Profile.valueOf(preferences.getString(
				PREF_VIDEO_PROFILE, FfmpegAnimationExporter.Profile.BALANCED.name()));
		} catch (RuntimeException ignored) { savedProfile = FfmpegAnimationExporter.Profile.BALANCED; }
		int savedResolution = preferences.getInteger(PREF_VIDEO_RESOLUTION, 0);
		if (savedResolution < 0 || savedResolution > 3) savedResolution = 0;
		boolean hasAudio = format != FfmpegAnimationExporter.Format.AWEBP && animationHasAudioEvents(animation);
		boolean[] includeAudio = {hasAudio && preferences.getBoolean(PREF_VIDEO_AUDIO, true)};
		boolean[] includeLastFrame = {preferences.getBoolean(PREF_VIDEO_INCLUDE_LAST_FRAME, false)};
		int lastAnimationFrame;
		try { lastAnimationFrame = PngAnimationExporter.maxAnimationFrame(animation, savedFps); }
		catch (RuntimeException error) { showError("无法导出 " + formatLabel, error); return; }
		if (lastAnimationFrame == 0) includeLastFrame[0] = true;
		int defaultEndFrame = Math.min(lastAnimationFrame, Math.min(PngAnimationExporter.MAX_FRAME_INDEX,
			includeLastFrame[0] ? PngAnimationExporter.MAX_FRAMES - 1 : PngAnimationExporter.MAX_FRAMES));

		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = kit.window("导出 " + formatLabel + " 动画");
		window.setModal(true);
		kit.padWindow(window, 10f);
		TextField fpsField = new TextField(UiNumbers.format(savedFps), kit.textFieldStyle);
		TextField startFrameField = new TextField("0", kit.textFieldStyle);
		TextField endFrameField = new TextField(Integer.toString(defaultEndFrame), kit.textFieldStyle);
		TextField warmUpField = new TextField(Integer.toString(savedWarmUp), kit.textFieldStyle);
		TextField scaleField = new TextField(UiNumbers.format(savedScale * 100f), kit.textFieldStyle);
		TextField paddingField = new TextField(Integer.toString(savedPadding), kit.textFieldStyle);
		TextField backgroundField = new TextField(savedBackground, kit.textFieldStyle);
		backgroundField.setMaxLength(7);
		SelectBox<String> profileBox = kit.selectBox();
		profileBox.setItems(FfmpegAnimationExporter.Profile.FAST.label(),
			FfmpegAnimationExporter.Profile.BALANCED.label(), FfmpegAnimationExporter.Profile.QUALITY.label());
		profileBox.setSelected(savedProfile.label());
		SelectBox<String> resolutionBox = kit.selectBox();
		resolutionBox.setItems("原始尺寸", "最高 1280 × 720", "最高 1920 × 1080", "最高 3840 × 2160");
		resolutionBox.setSelectedIndex(savedResolution);
		TextButton transparentButton = new TextButton("", kit.toolButton);
		TextButton includeAudioButton = new TextButton("", kit.toolButton);
		TextButton includeLastFrameButton = new TextButton("", kit.toolButton);
		Image backgroundSwatch = new Image(kit.white);
		Label summaryLabel = new Label("", kit.labelStyle);
		TextButton export = new TextButton("导出", kit.accentButton);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousKeyboardFocus);

		Runnable refresh = () -> {
			refreshPngToggle(transparentButton, transparent[0]);
			refreshPngToggle(includeAudioButton, includeAudio[0]);
			refreshPngToggle(includeLastFrameButton, includeLastFrame[0]);
			backgroundField.setDisabled(transparent[0]);
			includeAudioButton.setDisabled(!hasAudio);
			try {
				backgroundSwatch.setColor(UiColors.parseRgb(backgroundField.getText()));
				VideoExportOptions settings = videoExportSettings(format, fpsField.getText(),
					startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
					scaleField.getText(), paddingField.getText(), transparent[0], backgroundField.getText(),
					videoProfile(profileBox.getSelected()), resolutionBox.getSelectedIndex(),
					includeAudio[0], includeLastFrame[0]);
				int animationEnd = PngAnimationExporter.maxAnimationFrame(animation, settings.frames().fps());
				if (settings.frames().endFrame() > animationEnd)
					throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				summaryLabel.setText("动画：" + animationName + " · " + settings.frames().frameCount()
					+ " 帧 · " + settings.encoding().profile().label()
					+ (settings.includeAudio() ? " · 事件音频" : "") + " · 末帧 " + animationEnd);
				setValidationLabel(summaryLabel, false);
				export.setDisabled(false);
			} catch (RuntimeException error) {
				String message = error.getMessage();
					summaryLabel.setText(message == null || message.isBlank() ? formatLabel + " 设置无效" : message);
				setValidationLabel(summaryLabel, true);
				export.setDisabled(true);
			}
		};

		ChangeListener fieldListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refresh.run(); }
		};
		fpsField.addListener(fieldListener);
		startFrameField.addListener(fieldListener);
		endFrameField.addListener(fieldListener);
		warmUpField.addListener(fieldListener);
		scaleField.addListener(fieldListener);
		paddingField.addListener(fieldListener);
		backgroundField.addListener(fieldListener);
		profileBox.addListener(fieldListener);
		resolutionBox.addListener(fieldListener);
		transparentButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				transparent[0] = !transparent[0];
				refresh.run();
			}
		});
		includeAudioButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (hasAudio) includeAudio[0] = !includeAudio[0];
				refresh.run();
			}
		});
		includeLastFrameButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				includeLastFrame[0] = !includeLastFrame[0];
				refresh.run();
			}
		});

		Runnable submit = () -> {
			if (export.isDisabled()) return;
			try {
				VideoExportOptions settings = videoExportSettings(format, fpsField.getText(),
					startFrameField.getText(), endFrameField.getText(), warmUpField.getText(),
					scaleField.getText(), paddingField.getText(), transparent[0], backgroundField.getText(),
					videoProfile(profileBox.getSelected()), resolutionBox.getSelectedIndex(),
					includeAudio[0], includeLastFrame[0]);
				int animationEnd = PngAnimationExporter.maxAnimationFrame(animation, settings.frames().fps());
				if (settings.frames().endFrame() > animationEnd)
					throw new IllegalArgumentException("结束帧不能超过动画末帧 " + animationEnd);
				Skeleton snapshot = new Skeleton(doc.skeleton);
				snapshot.updateWorldTransform(Physics.none);
				long revision = doc.revision;
				TextureAtlas atlas = doc.atlas;
				boolean sourcePremultipliedAlpha = doc.premultipliedAlpha;
				String savePath = doc.savePath;
				String filePath = doc.filePath;
				String projectAudioPath = doc.data.getAudioPath();
				preferences.putFloat(PREF_VIDEO_FPS, settings.frames().fps());
				preferences.putInteger(PREF_VIDEO_WARM_UP, settings.frames().warmUp());
				preferences.putFloat(PREF_VIDEO_SCALE, settings.frames().scale());
				preferences.putInteger(PREF_VIDEO_PADDING, settings.frames().padding());
				preferences.putBoolean(PREF_VIDEO_TRANSPARENT, settings.frames().transparent());
				preferences.putString(PREF_VIDEO_BACKGROUND, backgroundField.getText().trim());
				preferences.putString(PREF_VIDEO_PROFILE, settings.encoding().profile().name());
				preferences.putInteger(PREF_VIDEO_RESOLUTION, resolutionBox.getSelectedIndex());
				preferences.putBoolean(PREF_VIDEO_AUDIO, settings.includeAudio());
				preferences.putBoolean(PREF_VIDEO_INCLUDE_LAST_FRAME, settings.frames().includeLastFrame());
				preferences.flush();
				close.run();
				beginVideoExport(snapshot, animationName, atlas, revision, sourcePremultipliedAlpha,
					settings, savePath, filePath, projectAudioPath);
			} catch (RuntimeException error) { refresh.run(); }
		};
		export.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		TextButton cancel = button("取消", close);
		addModalKeyActions(window, submit, close);

		window.row();
		window.add(new Label("帧率", kit.labelStyle)).right().pad(5);
		Table fpsRow = new Table();
		fpsRow.add(fpsField).width(120).height(28);
		fpsRow.add(new Label("FPS", kit.labelStyle)).padLeft(5);
		window.add(fpsRow).left().pad(5).row();
		window.add(new Label("帧范围", kit.labelStyle)).right().pad(5);
		Table rangeRow = new Table();
		rangeRow.add(startFrameField).width(70).height(28);
		rangeRow.add(new Label("至", kit.labelStyle)).pad(0, 5, 0, 5);
		rangeRow.add(endFrameField).width(70).height(28);
		window.add(rangeRow).left().pad(5).row();
		window.add(new Label("包含末帧", kit.labelStyle)).right().pad(5);
		window.add(includeLastFrameButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("预热次数", kit.labelStyle)).right().pad(5);
		window.add(warmUpField).width(120).height(28).left().pad(5).row();
		window.add(new Label("缩放", kit.labelStyle)).right().pad(5);
		Table scaleRow = new Table();
		scaleRow.add(scaleField).width(120).height(28);
		scaleRow.add(new Label("%", kit.labelStyle)).padLeft(5);
		window.add(scaleRow).left().pad(5).row();
		window.add(new Label("留白", kit.labelStyle)).right().pad(5);
		Table paddingRow = new Table();
		paddingRow.add(paddingField).width(120).height(28);
		paddingRow.add(new Label("px", kit.labelStyle)).padLeft(5);
		window.add(paddingRow).left().pad(5).row();
		window.add(new Label("最大分辨率", kit.labelStyle)).right().pad(5);
		window.add(resolutionBox).width(190).height(28).left().pad(5).row();
		window.add(new Label("透明背景", kit.labelStyle)).right().pad(5);
		window.add(transparentButton).width(110).height(28).left().pad(5).row();
		window.add(new Label("背景色", kit.labelStyle)).right().pad(5);
		Table backgroundRow = new Table();
		backgroundRow.add(backgroundSwatch).width(24).height(24).padRight(6);
		backgroundRow.add(backgroundField).width(120).height(28);
		window.add(backgroundRow).left().pad(5).row();
		window.add(new Label("编码档位", kit.labelStyle)).right().pad(5);
		window.add(profileBox).width(150).height(28).left().pad(5).row();
		window.add(new Label("事件音频", kit.labelStyle)).right().pad(5);
		window.add(includeAudioButton).width(110).height(28).left().pad(5).row();
		window.add(summaryLabel).colspan(2).left().pad(10, 5, 6, 5).row();
		Table actions = new Table();
		actions.add(cancel).width(86).height(28).padRight(6);
		actions.add(export).width(86).height(28);
		window.add(actions).colspan(2).right().pad(6, 5, 3, 5);
		refresh.run();
		showCentered(window);
		stage.setKeyboardFocus(fpsField);
		fpsField.selectAll();
	}

	private void beginVideoExport(Skeleton snapshot, String animationName, TextureAtlas atlas, long revision,
		boolean sourcePremultipliedAlpha, VideoExportOptions settings, String savePath, String filePath,
		String projectAudioPath) {
		if (fileDialogOpen) {
			setStatus("文件选择窗口已经打开", false);
			return;
		}
		FfmpegAnimationExporter.Format format = settings.encoding().format();
		String extension = switch (format) {
		case WEBM -> ".webm";
		case MOV -> ".mov";
		case AWEBP -> ".webp";
		};
		String suggested = suggestedAviPath(doc.savePath, doc.filePath, lastSaveDirectory)
			.replaceFirst("(?i)\\.avi$", extension);
		fileDialogOpen = true;
		try {
			java.util.function.BiConsumer<String, Throwable> completion = (path, error) ->
				videoDialogCompleted(path, error, snapshot, animationName, atlas, revision,
					sourcePremultipliedAlpha, settings, savePath, filePath, projectAudioPath);
			if (format == FfmpegAnimationExporter.Format.WEBM)
				DesktopLauncher.chooseWebmFileAsync(suggested, completion);
			else if (format == FfmpegAnimationExporter.Format.MOV)
				DesktopLauncher.chooseMovFileAsync(suggested, completion);
			else DesktopLauncher.chooseAnimatedWebpFileAsync(suggested, completion);
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError(ffmpegFormatLabel(format) + " 文件对话框失败", error);
		}
	}

	private void videoDialogCompleted(String path, Throwable error, Skeleton snapshot, String animationName,
		TextureAtlas atlas, long revision, boolean sourcePremultipliedAlpha, VideoExportOptions settings,
		String savePath, String filePath, String projectAudioPath) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) { showError(ffmpegFormatLabel(settings.encoding().format()) + " 文件对话框失败", error); return; }
			if (path == null || path.isBlank()) return;
			if (doc.revision != revision || doc.atlas != atlas) {
				setStatus("文档在选择导出位置时已更改，请重新导出 "
					+ ffmpegFormatLabel(settings.encoding().format()), true);
				return;
			}
			Path target = Path.of(path).toAbsolutePath().normalize();
			try {
				Animation animation = snapshot.getData().findAnimation(animationName);
				if (animation == null) throw new IllegalStateException("导出动画已不存在：" + animationName);
				List<FfmpegAnimationExporter.AudioCue> audioCues = settings.includeAudio()
					? FfmpegAnimationExporter.collectAudioCues(animation, settings.frames().startFrame(),
						settings.frames().lastExportFrame(), settings.frames().fps(), savePath, filePath,
						projectAudioPath)
					: List.of();
				PngFrameExporter.Settings renderSettings = new PngFrameExporter.Settings(settings.frames().scale(),
					settings.frames().padding(), 1, settings.frames().transparent(), false,
					settings.backgroundRgba());
				FfmpegAnimationExporter.Format format = settings.encoding().format();
				String formatLabel = ffmpegFormatLabel(format);
				beginAnimationRender("导出 " + formatLabel + " 动画", target, snapshot, animation,
					settings.frames().fps(), settings.frames().warmUp(), settings.frames().startFrame(),
					settings.frames().lastExportFrame(), renderSettings, sourcePremultipliedAlpha,
					renderer -> beginDocumentIo("编码 " + formatLabel, "导出 " + formatLabel + " 动画",
						"正在通过 ffmpeg 编码 " + formatLabel, target,
						token -> {
							try {
								FfmpegAnimationExporter.encode(renderer.frames(), settings.encoding(),
									audioCues, target, token);
								return new VideoExportResult(renderer.size().width(), renderer.size().height(),
									renderer.frameCount(), audioCues.size());
							} finally { closeAnimationRenderer(renderer); }
						}, result -> {
							rememberDirectory(path, true);
							setStatus("已导出 " + target.getFileName() + "（" + result.width() + " × "
								+ result.height() + " px · " + result.frameCount() + " 帧"
								+ (result.audioCueCount() > 0 ? " · " + result.audioCueCount() + " 个音频事件" : "")
								+ "）", false);
						}, () -> closeAnimationRenderer(renderer)));
			} catch (RuntimeException exportError) {
				showError(ffmpegFormatLabel(settings.encoding().format()) + " 导出失败", exportError);
			}
		});
	}

	private void reloadImages() {
		if (disposed) return;
		if (documentIoInProgress() || fileDialogOpen || spineImportInProgress || animationRenderer != null
			|| hasModalWindow()) {
			setStatus("请先完成当前文件任务或关闭对话框", false);
			return;
		}
		if (viewport.hasActiveGesture()) {
			setStatus("请先结束视区拖动，再重新加载图片", false);
			return;
		}
		finishWorkspaceTextEdit();
		try (ImageReload.Prepared prepared = ImageReload.prepare(doc)) {
			adoptReloadedImages(prepared);
			setStatus("已重新加载图片：" + prepared.sourceDescription(), false);
			syncCommandState();
		} catch (RuntimeException error) {
			showError("重新加载图片失败", error);
		}
	}

	/** Refreshes resources in place, preserving authored data, live poses and undo/redo history. */
	private void adoptReloadedImages(ImageReload.Prepared prepared) {
		TextureAtlas previous = doc.atlas;
		SkeletonEditor.AtlasRegionState previousRegions = editor.captureAtlasRegionState();
		String previousFileName = doc.atlasFileName, previousResourceDirectory = doc.resourceDirectory;
		boolean previousPremultipliedAlpha = doc.premultipliedAlpha;
		try {
			if (!editor.rebindAtlasRegions(prepared.atlas()))
				throw new IllegalStateException("无法重新绑定图片附件区域");
			doc.atlas = prepared.atlas();
			doc.atlasFileName = prepared.atlasFileName();
			doc.resourceDirectory = prepared.resourceDirectory();
			doc.premultipliedAlpha = prepared.premultipliedAlpha();
			viewport.refreshTextureFiltering();
		} catch (RuntimeException | Error failure) {
			doc.atlas = previous;
			doc.atlasFileName = previousFileName;
			doc.resourceDirectory = previousResourceDirectory;
			doc.premultipliedAlpha = previousPremultipliedAlpha;
			try {
				if (!editor.restoreAtlasRegions(previousRegions))
					throw new IllegalStateException("无法恢复原图片附件区域");
			} catch (RuntimeException | Error rollbackFailure) {
				failure.addSuppressed(rollbackFailure);
			}
			throw failure;
		}
		// Existing history may restore an older atlas or attachments referencing either version.
		doc.retainAtlasForHistory(previous);
		doc.retainAtlasForHistory(prepared.atlas());
		prepared.release();
	}

	private void importImagesAndPackAtlas() {
		if (doc.data == null) {
			setStatus("请先打开或新建一个项目", true);
			return;
		}
		if (documentIoInProgress() || fileDialogOpen) {
			setStatus("已有文件任务或文件窗口正在执行", false);
			return;
		}
		fileDialogOpen = true;
		try {
		DesktopLauncher.chooseImageFilesAsync(lastOpenDirectory, (paths, error) -> {
			postToRenderThread(() -> {
				fileDialogOpen = false;
				if (disposed) return;
				if (error != null) { showError("导入图片对话框失败", error); return; }
				if (paths == null || paths.isEmpty()) return;
				showAtlasPackingSettings(paths);
			});
		});
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("导入图片对话框失败", error);
		}
	}

	private void importPsdAndPackAtlas() {
		if (doc.data == null || doc.data.getBones().isEmpty()) {
			setStatus("请先打开或新建一个包含根骨骼的项目", true);
			return;
		}
		if (documentIoInProgress() || fileDialogOpen) {
			setStatus("已有文件任务或文件窗口正在执行", false);
			return;
		}
		fileDialogOpen = true;
		try {
			DesktopLauncher.choosePsdImportFileAsync(lastOpenDirectory, (path, error) -> postToRenderThread(() -> {
				fileDialogOpen = false;
				if (disposed) return;
				if (error != null) { showError("PSD 导入对话框失败", error); return; }
				if (path == null || path.isBlank()) return;
				Path source;
				try { source = Path.of(path).toAbsolutePath().normalize(); }
				catch (RuntimeException invalidPath) { showError("PSD 路径无效", invalidPath); return; }
				List<String> reservedRegions = new ArrayList<>();
				if (doc.atlas != null) for (TextureAtlas.AtlasRegion region : doc.atlas.getRegions())
					reservedRegions.add(region.name);
				beginDocumentIo("解析 PSD", "导入分层 PSD", "正在读取图层并提取 PNG", source,
					token -> {
						PsdImporter.ImportResult result = PsdImporter.extract(source, token, reservedRegions);
						pendingPsdImport = result;
						return result;
					}, result -> {
						rememberDirectory(source.toString(), false);
						List<String> images = result.layers().stream()
							.map(layer -> layer.png().toString()).toList();
						setStatus("已提取 " + images.size() + " 个 PSD 图层，等待打包图集", false);
						showAtlasPackingSettings(images, result);
					}, null);
			}));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			showError("PSD 导入对话框失败", error);
		}
	}

	private AtlasPacker.Settings atlasSettingsFromPreferences() {
		int width = preferences.getInteger(PREF_ATLAS_PAGE_WIDTH, 2048);
		int height = preferences.getInteger(PREF_ATLAS_PAGE_HEIGHT, 2048);
		int padding = preferences.getInteger(PREF_ATLAS_PADDING, 2);
		boolean duplicate = preferences.getBoolean(PREF_ATLAS_DUPLICATE_BORDER, true);
		AtlasPacker.Settings.Filter min = enumValue(AtlasPacker.Settings.Filter.class,
			preferences.getString(PREF_ATLAS_MIN_FILTER, AtlasPacker.Settings.Filter.Linear.toString()),
			AtlasPacker.Settings.Filter.Linear);
		AtlasPacker.Settings.Filter mag = enumValue(AtlasPacker.Settings.Filter.class,
			preferences.getString(PREF_ATLAS_MAG_FILTER, AtlasPacker.Settings.Filter.Linear.toString()),
			AtlasPacker.Settings.Filter.Linear);
		AtlasPacker.Settings.Repeat repeat = enumValue(AtlasPacker.Settings.Repeat.class,
			preferences.getString(PREF_ATLAS_REPEAT, AtlasPacker.Settings.Repeat.NONE.toString()),
			AtlasPacker.Settings.Repeat.NONE);
		boolean stripWhitespace = preferences.getBoolean(PREF_ATLAS_STRIP_WHITESPACE, false);
		float scale = preferences.getFloat(PREF_ATLAS_SCALE, 1f);
		boolean premultipliedAlpha = preferences.getBoolean(PREF_ATLAS_PREMULTIPLIED_ALPHA, false);
		boolean alias = preferences.getBoolean(PREF_ATLAS_ALIAS, false);
		try { return new AtlasPacker.Settings(width, height, padding, duplicate, min, mag, repeat,
			stripWhitespace, scale, premultipliedAlpha, alias); }
		catch (RuntimeException invalid) { return AtlasPacker.Settings.defaults(); }
	}

	private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
		if (value == null) return fallback;
		for (T candidate : type.getEnumConstants()) if (candidate.toString().equals(value)) return candidate;
		return fallback;
	}

	private void showAtlasPackingSettings(List<String> paths) {
		showAtlasPackingSettings(paths, null);
	}

	private void showAtlasPackingSettings(List<String> paths, PsdImporter.ImportResult psdImport) {
		if (paths == null || paths.isEmpty()) return;
		AtlasPacker.Settings saved = atlasSettingsFromPreferences();
		Actor previousFocus = stage.getKeyboardFocus();
		Window window = kit.window("图集设置");
		window.setModal(true);
		kit.padWindow(window, 12f);
		TextField widthField = new TextField(Integer.toString(saved.pageWidth()), kit.textFieldStyle);
		TextField heightField = new TextField(Integer.toString(saved.pageHeight()), kit.textFieldStyle);
		TextField paddingField = new TextField(Integer.toString(saved.padding()), kit.textFieldStyle);
		TextField scaleField = new TextField(Integer.toString(Math.round(saved.scale() * 100f)), kit.textFieldStyle);
		TextButton duplicateButton = new TextButton("复制边界像素", saved.duplicateBorder() ? kit.activeToolButton : kit.toolButton);
		duplicateButton.setChecked(saved.duplicateBorder());
		TextButton stripWhitespaceButton = new TextButton("裁剪透明空白",
			saved.stripWhitespace() ? kit.activeToolButton : kit.toolButton);
		stripWhitespaceButton.setChecked(saved.stripWhitespace());
		TextButton premultipliedButton = new TextButton("预乘 Alpha",
			saved.premultipliedAlpha() ? kit.activeToolButton : kit.toolButton);
		premultipliedButton.setChecked(saved.premultipliedAlpha());
		TextButton aliasButton = new TextButton("合并重复图片", saved.alias() ? kit.activeToolButton : kit.toolButton);
		aliasButton.setChecked(saved.alias());
		TextButton ignoreHiddenButton = new TextButton("忽略隐藏图层",
			psdImport != null && preferences.getBoolean(PREF_PSD_IGNORE_HIDDEN, false)
				? kit.activeToolButton : kit.toolButton);
		ignoreHiddenButton.setChecked(psdImport != null
			&& preferences.getBoolean(PREF_PSD_IGNORE_HIDDEN, false));
		SelectBox<String> minFilter = kit.selectBox();
		SelectBox<String> magFilter = kit.selectBox();
		SelectBox<String> repeat = kit.selectBox();
		Array<String> filters = new Array<>();
		for (AtlasPacker.Settings.Filter filter : AtlasPacker.Settings.Filter.values()) filters.add(filter.toString());
		Array<String> repeats = new Array<>();
		for (AtlasPacker.Settings.Repeat value : AtlasPacker.Settings.Repeat.values()) repeats.add(value.toString());
		minFilter.setItems(filters);
		magFilter.setItems(filters);
		repeat.setItems(repeats);
		minFilter.setSelected(saved.minFilter().toString());
		magFilter.setSelected(saved.magFilter().toString());
		repeat.setSelected(saved.repeat().toString());
		Label status = new Label("", kit.dialogBodyStyle);
		status.setWrap(true);
		TextButton apply = new TextButton("继续", kit.accentButton);
		TextButton cancel = new TextButton("取消", kit.toolButton);
		Runnable close = () -> UiDialogs.closeModal(stage, window, previousFocus);
		Runnable cancelImport = () -> {
			close.run();
			if (psdImport != null) cleanupPsdImport(psdImport);
		};
		Runnable refreshToggle = () -> {
			duplicateButton.setText(duplicateButton.isChecked() ? "复制边界像素：开" : "复制边界像素：关");
			duplicateButton.setStyle(duplicateButton.isChecked() ? kit.activeToolButton : kit.toolButton);
			stripWhitespaceButton.setText(stripWhitespaceButton.isChecked() ? "裁剪透明空白：开" : "裁剪透明空白：关");
			stripWhitespaceButton.setStyle(stripWhitespaceButton.isChecked() ? kit.activeToolButton : kit.toolButton);
			premultipliedButton.setText(premultipliedButton.isChecked() ? "预乘 Alpha：开" : "预乘 Alpha：关");
			premultipliedButton.setStyle(premultipliedButton.isChecked() ? kit.activeToolButton : kit.toolButton);
			aliasButton.setText(aliasButton.isChecked() ? "合并重复图片：开" : "合并重复图片：关");
			aliasButton.setStyle(aliasButton.isChecked() ? kit.activeToolButton : kit.toolButton);
			ignoreHiddenButton.setText(ignoreHiddenButton.isChecked() ? "忽略隐藏图层：开" : "忽略隐藏图层：关");
			ignoreHiddenButton.setStyle(ignoreHiddenButton.isChecked() ? kit.activeToolButton : kit.toolButton);
		};
		ChangeListener toggleListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refreshToggle.run(); }
		};
		duplicateButton.addListener(toggleListener);
		stripWhitespaceButton.addListener(toggleListener);
		premultipliedButton.addListener(toggleListener);
		aliasButton.addListener(toggleListener);
		ignoreHiddenButton.addListener(toggleListener);
		refreshToggle.run();
		java.util.function.Supplier<AtlasPacker.Settings> readSettings = () -> {
			try {
				return new AtlasPacker.Settings(Integer.parseInt(widthField.getText().trim()),
					Integer.parseInt(heightField.getText().trim()), Integer.parseInt(paddingField.getText().trim()),
					duplicateButton.isChecked(), enumValue(AtlasPacker.Settings.Filter.class, minFilter.getSelected(),
						AtlasPacker.Settings.Filter.Linear), enumValue(AtlasPacker.Settings.Filter.class, magFilter.getSelected(),
						AtlasPacker.Settings.Filter.Linear), enumValue(AtlasPacker.Settings.Repeat.class, repeat.getSelected(),
						AtlasPacker.Settings.Repeat.NONE), stripWhitespaceButton.isChecked(),
					Float.parseFloat(scaleField.getText().trim()) / 100f, premultipliedButton.isChecked(),
					aliasButton.isChecked());
			} catch (RuntimeException error) { throw new IllegalArgumentException("图集设置无效：" + error.getMessage(), error); }
		};
		Runnable validate = () -> {
			try {
				readSettings.get();
				long imageCount = psdImport != null && ignoreHiddenButton.isChecked()
					? psdImport.layers().stream().filter(PsdImporter.ImportedLayer::visible).count() : paths.size();
				if (imageCount == 0) throw new IllegalArgumentException("忽略隐藏图层后没有可导入的图层");
				String warningText = psdImport == null || psdImport.warnings().isEmpty() ? ""
					: "\n警告：" + String.join("\n", psdImport.warnings());
				status.setText(imageCount + " 张图片待打包" + warningText);
				status.setColor(psdImport != null && !psdImport.warnings().isEmpty()
					? kit.palette().errorText() : kit.palette().mutedText());
				apply.setDisabled(false);
			}
			catch (RuntimeException error) { status.setText(error.getMessage()); status.setColor(kit.palette().errorText()); apply.setDisabled(true); }
		};
		ignoreHiddenButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { validate.run(); }
		});
		ChangeListener fieldListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { validate.run(); }
		};
		widthField.addListener(fieldListener); heightField.addListener(fieldListener); paddingField.addListener(fieldListener);
		scaleField.addListener(fieldListener);
		minFilter.addListener(fieldListener); magFilter.addListener(fieldListener); repeat.addListener(fieldListener);
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (apply.isDisabled()) return;
				AtlasPacker.Settings settings;
				try { settings = readSettings.get(); } catch (RuntimeException error) { validate.run(); return; }
				preferences.putInteger(PREF_ATLAS_PAGE_WIDTH, settings.pageWidth());
				preferences.putInteger(PREF_ATLAS_PAGE_HEIGHT, settings.pageHeight());
				preferences.putInteger(PREF_ATLAS_PADDING, settings.padding());
				preferences.putBoolean(PREF_ATLAS_DUPLICATE_BORDER, settings.duplicateBorder());
				preferences.putString(PREF_ATLAS_MIN_FILTER, settings.minFilter().toString());
				preferences.putString(PREF_ATLAS_MAG_FILTER, settings.magFilter().toString());
				preferences.putString(PREF_ATLAS_REPEAT, settings.repeat().toString());
				preferences.putBoolean(PREF_ATLAS_STRIP_WHITESPACE, settings.stripWhitespace());
				preferences.putFloat(PREF_ATLAS_SCALE, settings.scale());
				preferences.putBoolean(PREF_ATLAS_PREMULTIPLIED_ALPHA, settings.premultipliedAlpha());
				preferences.putBoolean(PREF_ATLAS_ALIAS, settings.alias());
				if (psdImport != null) preferences.putBoolean(PREF_PSD_IGNORE_HIDDEN, ignoreHiddenButton.isChecked());
				preferences.flush();
				PsdImporter.ImportResult selectedPsd = psdImport != null && ignoreHiddenButton.isChecked()
					? psdImport.ignoringHiddenLayers() : psdImport;
				List<String> selectedPaths = selectedPsd == null ? paths
					: selectedPsd.layers().stream().map(layer -> layer.png().toString()).toList();
				if (selectedPaths.isEmpty()) { validate.run(); return; }
				close.run();
				beginAtlasPacking(selectedPaths, settings, selectedPsd);
			}
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { cancelImport.run(); }
		});
		addModalKeyActions(window, () -> { if (!apply.isDisabled()) apply.fire(new ChangeListener.ChangeEvent()); }, cancelImport);
		window.add(new Label("页面宽度", kit.labelStyle)).right().pad(5f);
		window.add(widthField).width(120f).height(28f).left().pad(5f).row();
		window.add(new Label("页面高度", kit.labelStyle)).right().pad(5f);
		window.add(heightField).width(120f).height(28f).left().pad(5f).row();
		window.add(new Label("边距", kit.labelStyle)).right().pad(5f);
		window.add(paddingField).width(120f).height(28f).left().pad(5f).row();
		window.add(new Label("边界", kit.labelStyle)).right().pad(5f);
		window.add(duplicateButton).width(190f).height(28f).left().pad(5f).row();
		window.add(new Label("透明空白", kit.labelStyle)).right().pad(5f);
		window.add(stripWhitespaceButton).width(190f).height(28f).left().pad(5f).row();
		window.add(new Label("缩放", kit.labelStyle)).right().pad(5f);
		Table scaleRow = new Table();
		scaleRow.add(scaleField).width(154f).height(28f);
		scaleRow.add(new Label("%", kit.labelStyle)).width(30f).padLeft(6f);
		window.add(scaleRow).width(190f).left().pad(5f).row();
		window.add(new Label("Alpha", kit.labelStyle)).right().pad(5f);
		window.add(premultipliedButton).width(190f).height(28f).left().pad(5f).row();
		window.add(new Label("重复图片", kit.labelStyle)).right().pad(5f);
		window.add(aliasButton).width(190f).height(28f).left().pad(5f).row();
		if (psdImport != null) {
			window.add(new Label("PSD 图层", kit.labelStyle)).right().pad(5f);
			window.add(ignoreHiddenButton).width(190f).height(28f).left().pad(5f).row();
		}
		window.add(new Label("最小过滤", kit.labelStyle)).right().pad(5f);
		window.add(minFilter).width(190f).height(28f).left().pad(5f).row();
		window.add(new Label("最大过滤", kit.labelStyle)).right().pad(5f);
		window.add(magFilter).width(190f).height(28f).left().pad(5f).row();
		window.add(new Label("重复", kit.labelStyle)).right().pad(5f);
		window.add(repeat).width(190f).height(28f).left().pad(5f).row();
		window.add(status).colspan(2).width(400f).left().pad(8f, 5f, 5f, 5f).row();
		Table actions = new Table();
		actions.add().growX();
		actions.add(cancel).width(86f).height(28f).padRight(6f);
		actions.add(apply).width(86f).height(28f);
		window.add(actions).colspan(2).growX().pad(5f);
		validate.run();
		showCentered(window);
		stage.setKeyboardFocus(widthField);
		widthField.selectAll();
	}

	private void beginAtlasPacking(List<String> paths, AtlasPacker.Settings settings) {
		beginAtlasPacking(paths, settings, null);
	}

	private void beginAtlasPacking(List<String> paths, AtlasPacker.Settings settings,
		PsdImporter.ImportResult psdImport) {
		Path retainedAtlas;
		try {
			retainedAtlas = psdImport != null && doc.atlas != null && doc.atlas.getRegions().notEmpty()
				? currentAtlasFile() : null;
		} catch (RuntimeException error) {
			cleanupPsdImport(psdImport);
			showError("无法合并当前图集", error);
			return;
		}
		File outputDirectory = atlasOutputDirectory(lastSaveDirectory, paths.get(0),
			psdImport == null ? null : psdImport.source());
		String atlasName = psdImport == null ? "images.atlas"
			: psdImport.source().getFileName().toString().replaceFirst("(?i)\\.psd$", "") + ".atlas";
		String suggested = new File(outputDirectory, atlasName).getAbsolutePath();
		fileDialogOpen = true;
		try {
		DesktopLauncher.chooseAtlasSaveFileAsync(suggested, (atlasPath, saveError) -> postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) { cleanupPsdImport(psdImport); return; }
			if (saveError != null) {
				cleanupPsdImport(psdImport);
				showError("图集保存对话框失败", saveError);
				return;
			}
			if (atlasPath == null || atlasPath.isBlank()) { cleanupPsdImport(psdImport); return; }
			Path target;
			try { target = Path.of(atlasPath).toAbsolutePath().normalize(); }
			catch (RuntimeException invalidPath) {
				cleanupPsdImport(psdImport);
				showError("图集保存路径无效", invalidPath);
				return;
			}
			beginDocumentIo("图集", "导入图片", "正在打包图片图集", target,
				token -> retainedAtlas == null
					? AtlasPacker.pack(paths.stream().map(Path::of).toList(), target, settings, token)
					: AtlasPacker.packIncludingAtlas(paths.stream().map(Path::of).toList(), retainedAtlas,
						target, settings, token),
				result -> {
					cleanupPsdImport(psdImport);
					rememberDirectory(target.toString(), true);
					TextureAtlas imported = null;
					try {
						imported = AtlasAlphaModes.load(new FileHandle(result.atlas().toFile()));
						TextureAtlas packedAtlas = imported;
						boolean hasExisting = psdImport == null
							&& doc.atlas != null && doc.atlas.getRegions().notEmpty();
						Runnable keepExisting = () -> {
							packedAtlas.dispose();
							setStatus("已生成图集 " + result.atlas().getFileName() + "，保留当前项目图集", false);
						};
						Runnable replace = () -> {
							PsdLayerInstaller.Transaction psdLayers = null;
							AtlasAdoption adoption = null;
							try {
								if (psdImport != null)
									psdLayers = PsdLayerInstaller.prepare(doc, editor, psdImport, packedAtlas);
								adoption = adoptImportedAtlas(packedAtlas, result);
								if (psdLayers != null) {
									psdLayers.apply();
									adoption.retainForHistory();
								} else {
									adoption.retainForHistory();
									history.record("替换图集", adoption::undo, adoption::redo);
								}
							} catch (RuntimeException installError) {
								if (psdLayers != null && psdLayers.isApplied()) {
									try { psdLayers.undo(); }
									catch (RuntimeException rollbackError) { installError.addSuppressed(rollbackError); }
								}
								if (adoption != null) {
									try { adoption.rollback(); }
									catch (RuntimeException rollbackError) { installError.addSuppressed(rollbackError); }
								}
								packedAtlas.dispose();
								showError(psdImport == null ? "图集替换失败" : "PSD 导入失败", installError);
								return;
							}
							if (psdLayers != null) installPsdLayers(psdImport, psdLayers, adoption);
							else setStatus("已生成并载入图集 " + result.atlas().getFileName() + "（"
								+ result.regionCount() + " 个区域）", false);
						};
						if (hasExisting) UiDialogs.confirm(stage, kit, "替换当前图集",
							"当前项目已有图集。替换后附件会按新的图集区域重新解析。", "替换", replace, keepExisting);
						else replace.run();
					} catch (RuntimeException loadError) {
						if (imported != null) imported.dispose();
						showError("图集载入失败", loadError);
					}
				}, () -> cleanupPsdImport(psdImport));
		}));
		} catch (RuntimeException error) {
			fileDialogOpen = false;
			cleanupPsdImport(psdImport);
			showError("图集保存对话框失败", error);
		}
	}

	private void cleanupPsdImport(PsdImporter.ImportResult imported) {
		if (imported == null) return;
		PsdImporter.cleanup(imported);
		PsdImporter.ImportResult pending = pendingPsdImport;
		if (pending != null && pending.directory().equals(imported.directory())) {
			PsdImporter.cleanup(pending);
			pendingPsdImport = null;
		}
	}

	/** Keep PSD atlas output beside its source, outside the disposable extracted-layer directory. */
	static File atlasOutputDirectory(String savedDirectory, String firstImage, Path psdSource) {
		String source = psdSource == null ? firstImage : psdSource.toString();
		File sourceDirectory = DesktopLauncher.existingDirectory(source, new File("."));
		return DesktopLauncher.existingDirectory(savedDirectory, sourceDirectory);
	}

	private Path currentAtlasFile() {
		if (doc.resourceDirectory == null || doc.resourceDirectory.isBlank()
			|| doc.atlasFileName == null || doc.atlasFileName.isBlank())
			throw new IllegalStateException("当前图集缺少可读取的资源路径");
		Path atlas = Path.of(doc.resourceDirectory).resolve(doc.atlasFileName).toAbsolutePath().normalize();
		if (!Files.isRegularFile(atlas)) throw new IllegalStateException("当前图集文件不存在：" + atlas);
		return atlas;
	}

	private void installPsdLayers(PsdImporter.ImportResult imported,
		PsdLayerInstaller.Transaction transaction, AtlasAdoption adoption) {
		if (imported == null || transaction == null || !transaction.isApplied() || adoption == null) return;
		SkeletonDocument.PsdSource previousSource = doc.psdSource();
		SkeletonDocument.PsdSource installedSource = transaction.sourceMetadata(imported);
		doc.setPsdSource(installedSource);
		PsdImportHistory importHistory = new PsdImportHistory(transaction, adoption,
			previousSource, installedSource);
		history.record("导入 PSD", importHistory::undo, importHistory::redo);
		rebuildPanels();
		Gdx.app.postRunnable(viewport::frameAll);
		setStatus("已导入 PSD：" + transaction.layerCount() + " 个图层、"
			+ imported.canvasWidth() + " × " + imported.canvasHeight() + " px", false);
		syncCommandState();
	}

	/** Installs a newly packed atlas and rebuilds attachments against its regions. */
	private AtlasAdoption adoptImportedAtlas(TextureAtlas imported, AtlasPacker.PackResult result) {
		if (imported == null || result == null) throw new IllegalArgumentException("待载入图集数据不完整");
		String blockReason = editor.getAtlasReplacementBlockReason(imported);
		if (blockReason != null) throw new IllegalStateException(blockReason);
		TextureAtlas previous = doc.atlas;
		AtlasAdoption adoption = new AtlasAdoption(previous, imported, editor.captureAtlasRegionState(),
			doc.atlasFileName, doc.resourceDirectory, doc.premultipliedAlpha,
			result.atlas().getFileName().toString(), result.atlas().getParent().toString(),
			result.premultipliedAlpha(), doc.dirty, doc.revision);
		try {
			if (!editor.rebindAtlasRegions(imported)) throw new IllegalStateException("无法重新绑定替换图集的附件区域");
			adoption.importedRegions = editor.captureAtlasRegionState();
			doc.atlas = imported;
			doc.atlasFileName = adoption.importedFileName;
			doc.resourceDirectory = adoption.importedResourceDirectory;
			doc.premultipliedAlpha = adoption.importedPremultipliedAlpha;
			doc.markDirty();
			editor.rebuildSkeleton();
			return adoption;
		} catch (RuntimeException failure) {
			try { adoption.rollback(); }
			catch (RuntimeException rollbackError) { failure.addSuppressed(rollbackError); }
			throw failure;
		}
	}

	private final class AtlasAdoption {
		private final TextureAtlas previous;
		private final TextureAtlas imported;
		private final SkeletonEditor.AtlasRegionState previousRegions;
		private SkeletonEditor.AtlasRegionState importedRegions;
		private final String previousFileName;
		private final String previousResourceDirectory;
		private final boolean previousPremultipliedAlpha;
		private final String importedFileName;
		private final String importedResourceDirectory;
		private final boolean importedPremultipliedAlpha;
		private final boolean previousDirty;
		private final long previousRevision;
		private boolean active = true;
		private boolean reversible;
		private boolean importedActive = true;

		private AtlasAdoption(TextureAtlas previous, TextureAtlas imported,
			SkeletonEditor.AtlasRegionState previousRegions, String previousFileName,
			String previousResourceDirectory, boolean previousPremultipliedAlpha,
			String importedFileName, String importedResourceDirectory, boolean importedPremultipliedAlpha,
			boolean previousDirty, long previousRevision) {
			this.previous = previous;
			this.imported = imported;
			this.previousRegions = previousRegions;
			this.previousFileName = previousFileName;
			this.previousResourceDirectory = previousResourceDirectory;
			this.previousPremultipliedAlpha = previousPremultipliedAlpha;
			this.importedFileName = importedFileName;
			this.importedResourceDirectory = importedResourceDirectory;
			this.importedPremultipliedAlpha = importedPremultipliedAlpha;
			this.previousDirty = previousDirty;
			this.previousRevision = previousRevision;
		}

		private void retainForHistory() {
			if (!active || importedRegions == null)
				throw new IllegalStateException("图集替换尚未完成，无法记录撤销");
			// PSD layers are added after atlas adoption. Include them before sealing the history snapshot,
			// so redo also restores their regions after a later image reload changes these attachments.
			importedRegions = editor.captureAtlasRegionState();
			doc.retainAtlasForHistory(previous);
			doc.retainAtlasForHistory(imported);
			active = false;
			reversible = true;
		}

		private void rollback() {
			if (!active) return;
			switchTo(previous, previousRegions, previousFileName, previousResourceDirectory,
				previousPremultipliedAlpha);
			doc.dirty = previousDirty;
			doc.revision = previousRevision;
			active = false;
			importedActive = false;
		}

		private void undo() {
			if (!reversible || !importedActive) throw new IllegalStateException("图集替换当前不可撤销");
			switchTo(previous, previousRegions, previousFileName, previousResourceDirectory,
				previousPremultipliedAlpha);
			importedActive = false;
		}

		private void redo() {
			if (!reversible || importedActive) throw new IllegalStateException("图集替换当前不可重做");
			switchTo(imported, importedRegions, importedFileName, importedResourceDirectory,
				importedPremultipliedAlpha);
			importedActive = true;
		}

		private void switchTo(TextureAtlas target, SkeletonEditor.AtlasRegionState regions,
			String fileName, String resourceDirectory, boolean premultipliedAlpha) {
			TextureAtlas currentAtlas = doc.atlas;
			SkeletonEditor.AtlasRegionState currentRegions = editor.captureAtlasRegionState();
			String currentFileName = doc.atlasFileName;
			String currentResourceDirectory = doc.resourceDirectory;
			boolean currentPremultipliedAlpha = doc.premultipliedAlpha;
			boolean dirty = doc.dirty;
			long revision = doc.revision;
			try {
				doc.atlas = target;
				doc.atlasFileName = fileName;
				doc.resourceDirectory = resourceDirectory;
				doc.premultipliedAlpha = premultipliedAlpha;
				if (!editor.restoreAtlasRegions(regions))
					throw new IllegalStateException("无法恢复图集附件区域");
				editor.rebuildSkeleton();
				doc.markDirty();
			} catch (RuntimeException failure) {
				doc.atlas = currentAtlas;
				doc.atlasFileName = currentFileName;
				doc.resourceDirectory = currentResourceDirectory;
				doc.premultipliedAlpha = currentPremultipliedAlpha;
				try {
					if (!editor.restoreAtlasRegions(currentRegions))
						throw new IllegalStateException("无法回滚图集附件区域");
					editor.rebuildSkeleton();
				} catch (RuntimeException rollbackError) {
					failure.addSuppressed(rollbackError);
				} finally {
					doc.dirty = dirty;
					doc.revision = revision;
				}
				throw failure;
			}
		}
	}

	private final class PsdImportHistory {
		private final PsdLayerInstaller.Transaction layers;
		private final AtlasAdoption atlas;
		private final SkeletonDocument.PsdSource previousSource;
		private final SkeletonDocument.PsdSource importedSource;

		private PsdImportHistory(PsdLayerInstaller.Transaction layers, AtlasAdoption atlas,
			SkeletonDocument.PsdSource previousSource, SkeletonDocument.PsdSource importedSource) {
			this.layers = layers;
			this.atlas = atlas;
			this.previousSource = previousSource;
			this.importedSource = importedSource;
		}

		private void undo() {
			layers.undo();
			try {
				atlas.undo();
				doc.restorePsdSource(previousSource);
			} catch (RuntimeException failure) {
				try { layers.apply(); }
				catch (RuntimeException rollbackError) { failure.addSuppressed(rollbackError); }
				throw failure;
			}
		}

		private void redo() {
			atlas.redo();
			try {
				layers.apply();
				doc.restorePsdSource(importedSource);
			} catch (RuntimeException failure) {
				try { atlas.undo(); }
				catch (RuntimeException rollbackError) { failure.addSuppressed(rollbackError); }
				throw failure;
			}
		}
	}

	private void packageDialogCompleted(String path, Throwable error) {
		postToRenderThread(() -> {
			fileDialogOpen = false;
			if (disposed) return;
			if (error != null) {
				showError("打包文件对话框失败", error);
				return;
			}
			if (path == null || path.isBlank()) return;
			Path target;
			try {
				target = Path.of(path).toAbsolutePath().normalize();
			} catch (RuntimeException invalidPath) {
				showError("打包项目失败", invalidPath);
				return;
			}
			if (treePanel != null) treePanel.persistViewState();
			if (timelinePanel != null) timelinePanel.persistViewState();
			beginDocumentIo("打包", "打包项目", "正在收集并压缩项目文件", target,
				token -> ProjectPackager.write(skeletonProject, showAllSkeletons, target, token), result -> {
					rememberDirectory(target.toString(), true);
					setStatus("项目已打包：" + target.getFileName() + "（" + result.fileCount() + " 个文件）", false);
				}, null);
		});
	}

	private static void initAnimationState(SkeletonDocument target) {
		target.animationState = target.newAnimationState();
		target.skeleton.setPosition(0, 0);
		Skin previewSkin = initialPreviewSkin(target.data);
		if (target.skeleton.getSkin() == null && previewSkin != null) {
			target.skeleton.setSkin(previewSkin);
			target.skeleton.setupPoseSlots();
		}
		if (target.data.getAnimations().notEmpty())
			target.setAnimation(target.data.getAnimations().first().getName());
		else target.setPlaying(false);
	}

	static Skin initialPreviewSkin(SkeletonData data) {
		if (data == null || data.getSkins().isEmpty()) return null;
		Skin best = data.getDefaultSkin() != null ? data.getDefaultSkin() : data.getSkins().first();
		int bestScore = skinActivationScore(best);
		for (Skin skin : data.getSkins()) {
			int score = skinActivationScore(skin);
			if (score > bestScore) {
				best = skin;
				bestScore = score;
			}
		}
		return best;
	}

	private static int skinActivationScore(Skin skin) {
		return skin == null ? -1 : skin.getBones().size + skin.getConstraints().size;
	}

	private void finishLoad(String message) {
		bindActiveHistory();
		clearHistoryAndRebuildPanels();
		viewport.refreshTextureFiltering();
		stage.act(0f);
		Gdx.app.postRunnable(viewport::frameAll);
		refreshSkeletonSelector();
		setStatus(message, false);
		syncCommandState();
	}

	private void rememberDirectory(String path, boolean saveDirectory) {
		File directory = DesktopLauncher.existingDirectory(path, null);
		if (directory == null) return;
		if (saveDirectory) lastSaveDirectory = directory.getAbsolutePath();
		else lastOpenDirectory = directory.getAbsolutePath();
	}

	private void rememberRecentProject(String path) {
		if (path == null || path.isBlank()) return;
		String normalized = Path.of(path).toAbsolutePath().normalize().toString();
		List<String> updated = updateRecentProjects(recentProjects, normalized);
		recentProjects.clear();
		recentProjects.addAll(updated);
		if (preferences != null) {
			preferences.putString(PREF_RECENT_PROJECTS, encodeRecentProjects(recentProjects));
			preferences.flush();
		}
	}

	private void documentStructureChanged() {
		clearHistoryAndRebuildPanels();
		setStatus("骨架结构已修改", false);
		syncCommandState();
	}

	private void documentStructureChangedPreservingHistory() {
		rebuildPanels();
		setStatus("骨架结构已修改，可撤销", false);
		syncCommandState();
	}

	private void attachmentChanged() {
		clearHistoryAndRebuildPanels();
		setStatus("槽、附件或皮肤已修改", false);
		syncCommandState();
	}

	private void constraintChanged() {
		treePanel.rebuild();
		propsPanel.refresh();
		attachPanel.refresh();
		playbackPanel.rebuild();
		timelinePanel.rebuild();
		setStatus("约束已修改", false);
		syncCommandState();
	}

	private void clearHistoryAndRebuildPanels() {
		boolean previousSuppression = suppressHistoryRefresh;
		suppressHistoryRefresh = true;
		try {
			history.clear();
			rebuildPanels();
		} finally {
			suppressHistoryRefresh = previousSuppression;
		}
	}

	private void rebuildPanels() {
		treePanel.rebuild();
		propsPanel.refresh();
		attachPanel.refresh();
		playbackPanel.rebuild();
		timelinePanel.rebuild();
	}

	private void skinSelectionChanged() {
		treePanel.rebuild();
		propsPanel.refresh();
		attachPanel.refresh();
		playbackPanel.refreshFromDocument();
		timelinePanel.rebuild();
		setStatus("已更新当前皮肤", false);
		syncCommandState();
	}

	private void timelineChanged() {
		playbackPanel.rebuild();
		setStatus("动画关键帧已修改", false);
		syncCommandState();
	}

	private void refreshSelection() {
		treePanel.syncSelection();
		propsPanel.refresh();
		attachPanel.refresh();
		timelinePanel.refreshSelection();
		syncCommandState();
	}

	private void viewportSelectionChanged() {
		if (!viewport.hasAttachmentSelection()) treePanel.selectBone(doc.getSelectedBone());
		refreshSelection();
	}

	private void boneTreeSelectionChanged() {
		Slot slot = treePanel.getSelectedSlot();
		selectingFromHierarchyTree = true;
		try {
			if (slot == null) {
				viewport.selectAttachmentForBone(doc.getSelectedBone());
			} else {
				Attachment attachment = treePanel.getSelectedAttachment();
				viewport.selectAttachment(slot.getData(), attachment);
				attachPanel.selectTreeSlot(slot, attachment);
			}
		} finally {
			selectingFromHierarchyTree = false;
		}
		refreshSelection();
	}

	private void viewportAttachmentSelected(Slot slot, Attachment attachment) {
		attachPanel.selectViewportAttachment(slot, attachment);
		if (shouldMirrorViewportAttachmentInTree(selectingFromHierarchyTree))
			treePanel.selectAttachment(slot, attachment);
	}

	static boolean shouldMirrorViewportAttachmentInTree(boolean selectingFromHierarchyTree) {
		return !selectingFromHierarchyTree;
	}

	private void syncCommandState() {
		for (var entry : toolButtons.entrySet()) {
			String label = toolButtonLabel(entry.getKey(), keyBindings);
			if (!label.contentEquals(entry.getValue().getText())) entry.getValue().setText(label);
			entry.getValue().setStyle(entry.getKey() == EditorAppState.tool ? kit.activeToolButton : kit.toolButton);
		}
		if (poseBtn != null) {
			poseBtn.setText(poseButtonLabel(keyBindings));
			poseBtn.setStyle(doc.poseMode ? kit.activeToolButton : kit.toolButton);
		}
		if (undoBtn != null) undoBtn.setDisabled(!history.canUndo());
		if (redoBtn != null) redoBtn.setDisabled(!history.canRedo());
		refreshSkeletonSelector();
		playbackPanel.refreshFromDocument();
		updateStatusBar();
	}

	private void refreshInterfaceLanguage() {
		if (projectMenu != null) projectMenu.dismiss();
		if (interfaceMenu != null) interfaceMenu.dismiss();
		if (projectMenuBtn != null) projectMenuBtn.setText(I18n.t("项目", "Project"));
		if (openBtn != null) openBtn.setText(I18n.t("打开", "Open"));
		if (saveBtn != null) saveBtn.setText(I18n.t("保存", "Save"));
		if (saveAsBtn != null) saveAsBtn.setText(I18n.t("另存为", "Save As"));
		if (undoBtn != null) undoBtn.setText(I18n.t("撤销", "Undo"));
		if (redoBtn != null) redoBtn.setText(I18n.t("重做", "Redo"));
		if (poseBtn != null) poseBtn.setText(poseButtonLabel(keyBindings));
		refreshWorkspaceRestoreButtons();
		if (interfaceMenuBtn != null) interfaceMenuBtn.setText(I18n.t("界面", "Interface"));
		if (propertiesTabBtn != null) propertiesTabBtn.setText(I18n.t("骨骼属性", "Bone Properties"));
		if (attachmentsTabBtn != null)
			attachmentsTabBtn.setText(I18n.t("附件皮肤", "Attachments & Skins"));
		if (allSkeletonsBtn != null) allSkeletonsBtn.setText(I18n.t("全部", "All"));
		if (skeletonHeaderLabel != null) skeletonHeaderLabel.setText(I18n.t("骨架", "Skeleton"));
		for (var entry : toolButtons.entrySet())
			entry.getValue().setText(toolButtonLabel(entry.getKey(), keyBindings));
		if (treePanel != null) treePanel.refreshLanguage();
		if (propsPanel != null) propsPanel.refreshLanguage();
		if (attachPanel != null) attachPanel.refreshLanguage();
		if (timelinePanel != null) timelinePanel.refreshLanguage();
		if (playbackPanel != null) playbackPanel.refreshLanguage();
		if (stage != null) I18n.localize(stage.getRoot());
		transientStatus = null;
		titleInitialized = false;
		if (statusLabel != null) syncCommandState();
	}

	static String toolButtonLabel(EditorTool tool, KeyBindings bindings) {
		if (tool == null) return "";
		EditorAction action = switch (tool) {
		case ROTATE -> EditorAction.TOOL_ROTATE;
		case MOVE -> EditorAction.TOOL_MOVE;
		case SCALE -> EditorAction.TOOL_SCALE;
		case SHEAR -> EditorAction.TOOL_SHEAR;
		case WEIGHT -> EditorAction.TOOL_WEIGHT;
		default -> null;
		};
		if (action == null || bindings == null || bindings.combo(action) == null) return tool.localizedLabel();
		return bindings.combo(action).display() + " " + tool.localizedLabel();
	}

	static String poseButtonLabel(KeyBindings bindings) {
		KeyBindings.Combo combo = bindings == null ? null : bindings.combo(EditorAction.TOOL_POSE);
		return combo == null ? I18n.t("姿态", "Pose")
			: combo.display() + " " + I18n.t("姿态", "Pose");
	}

	private void setStatus(String message, boolean error) {
		transientStatus = message;
		transientStatusUntil = TimeUtils.millis() + STATUS_TOAST_DURATION_MILLIS;
		statusError = error;
		if (statusLabel != null && statusToast != null) {
			applyStatusStyle();
			statusLabel.setText(message);
			layoutStatusToast();
			statusToast.clearActions();
			statusToast.getColor().a = 0f;
			statusToast.setVisible(true);
			statusToast.toFront();
			statusToast.addAction(Actions.fadeIn(0.12f));
		}
	}

	private void updateStatusBar() {
		updateWindowTitle();
		updateViewportStatus();
		if (statusLabel == null || statusToast == null) return;
		if (transientStatusVisible(transientStatus, transientStatusUntil, TimeUtils.millis())) return;
		transientStatus = null;
		statusError = false;
		statusLabel.setText("");
		statusToast.clearActions();
		statusToast.setVisible(false);
		statusToast.getColor().a = 1f;
	}

	private void updateViewportStatus() {
		if (viewportStatus == null || viewportStatusLabel == null || viewport == null) return;
		List<String> states = new ArrayList<>();
		if (!viewport.areConstraintsEnabled()) states.add(I18n.t("当前骨架约束已禁用", "Current skeleton constraints disabled"));
		if (viewport.isSelectionHidden()) states.add(I18n.t("选择已隐藏", "Selection hidden")
			+ " (" + keyBindings.combo(EditorAction.TOGGLE_SELECTION_VISIBILITY).display() + ")");
		if (viewport.isBonesVisible() && viewport.isShowInactiveSkinBones())
			states.add(I18n.t("非活动骨骼：设置姿态参考", "Inactive bones: setup pose reference"));
		if (viewport.isViewportFlipped()) states.add(I18n.t("视区翻转", "Viewport flipped"));
		if (viewport.isZoomLocked()) states.add(I18n.t("缩放锁定", "Zoom locked"));
		if (viewport.isPixelSnappingEnabled()) states.add(I18n.t("像素吸附", "Pixel snapping"));
		if (viewport.isPixelGridVisible()) states.add(viewport.isPixelGridActive()
			? I18n.t("项目像素网格", "Project pixel grid")
			: I18n.t("项目像素网格：放大后显示", "Project pixel grid: zoom in to see it"));
		viewportStatusLabel.setText(String.join(" · ", states));
		viewportStatus.setVisible(!states.isEmpty());
	}

	static boolean transientStatusVisible(String message, long expiresAt, long now) {
		return message != null && now < expiresAt;
	}

	static float statusToastX(float stageWidth, float toastWidth) {
		return Math.max(0f, (stageWidth - toastWidth) * 0.5f);
	}

	static float statusToastY(float stageHeight, float toastHeight, boolean customFrame) {
		float hostHeight = customFrame ? CUSTOM_TITLE_BAR_HEIGHT : WORKSPACE_TOOLBAR_HEIGHT;
		return Math.max(0f, stageHeight - (hostHeight + toastHeight) * 0.5f);
	}

	static float statusToastWidth(float textWidth, float availableWidth) {
		float maximum = Math.min(STATUS_TOAST_MAX_WIDTH, Math.max(0f, availableWidth));
		return Math.min(Math.max(0f, textWidth) * STATUS_TOAST_WIDTH_SCALE, maximum);
	}

	static float statusToastHeight(float textHeight) {
		return Math.max(0f, textHeight) * STATUS_TOAST_HEIGHT_SCALE;
	}

	private void layoutStatusToast() {
		if (statusToast == null || stage == null) return;
		float availableWidth = Math.max(1f, stage.getWidth() - STATUS_TOAST_SIDE_MARGIN * 2f);
		statusToast.setSize(statusToastWidth(statusLabel.getPrefWidth(), availableWidth),
			statusToastHeight(statusLabel.getPrefHeight()));
		statusToast.invalidate();
		statusToast.validate();
		statusToast.setPosition(statusToastX(stage.getWidth(), statusToast.getWidth()),
			statusToastY(stage.getHeight(), statusToast.getHeight(), WindowsTitleBar.usesCustomFrame()));
	}

	private void updateWindowTitle() {
		if (Gdx.graphics == null) return;
		String documentName = nativeProjectPath != null ? nativeProjectPath.getFileName() + " · " + skeletonProject.activeName()
			: skeletonProject.size() > 1
			? documentDisplayName(null, doc.filePath) + " · " + skeletonProject.activeName()
			: documentDisplayName(doc.savePath, doc.filePath);
		boolean projectDirty = skeletonProject.isDirty();
		String title = windowTitle(documentName, projectDirty);
		if (titleBarLabel != null && !title.contentEquals(titleBarLabel.getText())) titleBarLabel.setText(title);
		if (titleInitialized && lastTitleDirty == projectDirty && documentName.equals(lastTitleDocument)) return;
		lastTitleDirty = projectDirty;
		lastTitleDocument = documentName;
		titleInitialized = true;
		Gdx.graphics.setTitle(title);
	}

	private void showSettings() {
		if (settingsDialog != null && settingsDialog.isShowing()) return;
		settingsDialog = new SettingsDialog(kit, stage, keyBindings, new SettingsDialog.Host() {
			@Override public Mode appearanceMode() { return EditorApp.this.appearanceMode; }
			@Override public void setAppearanceMode(Mode mode) { EditorApp.this.setAppearanceMode(mode); }
			@Override public boolean resolvedDarkAppearance() { return resolvedAppearance().dark(); }
			@Override public Theme appearanceTheme(boolean dark) { return dark ? darkTheme : lightTheme; }
			@Override public void setAppearanceTheme(boolean dark, Theme theme) {
				EditorApp.this.setAppearanceTheme(dark, theme);
			}
			@Override public boolean isGridVisible() { return viewport.isGridVisible(); }
			@Override public void setGridVisible(boolean visible) { viewport.setGridVisible(visible); }
			@Override public boolean isBonesVisible() { return viewport.isBonesVisible(); }
			@Override public void setBonesVisible(boolean visible) { viewport.setBonesVisible(visible); }
			@Override public boolean isGhostingVisible() { return viewport.isGhostingVisible(); }
			@Override public void setGhostingVisible(boolean visible) { viewport.setGhostingVisible(visible); }
			@Override public boolean isGhostBonesVisible() { return viewport.isGhostBonesVisible(); }
			@Override public void setGhostBonesVisible(boolean visible) { viewport.setGhostBonesVisible(visible); }
			@Override public boolean isTextureSmoothing() { return viewport.isTextureSmoothingEnabled(); }
			@Override public void setTextureSmoothing(boolean smooth) { viewport.setTextureSmoothing(smooth); }
			@Override public boolean isViewportFlipped() { return viewport.isViewportFlipped(); }
			@Override public boolean isSelectionHidden() { return viewport.isSelectionHidden(); }
			@Override public void setSelectionHidden(boolean hidden) { viewport.setSelectionHidden(hidden); }
			@Override public boolean isShowInactiveSkinBones() { return viewport.isShowInactiveSkinBones(); }
			@Override public void setShowInactiveSkinBones(boolean visible) { viewport.setShowInactiveSkinBones(visible); }
			@Override public boolean isPixelGridVisible() { return viewport.isPixelGridVisible(); }
			@Override public void setPixelGridVisible(boolean visible) { viewport.setPixelGridVisible(visible); }
			@Override public void setViewportFlipped(boolean flipped) { viewport.setViewportFlipped(flipped); }
			@Override public boolean isZoomLocked() { return viewport.isZoomLocked(); }
			@Override public void setZoomLocked(boolean locked) { viewport.setZoomLocked(locked); }
			@Override public boolean isPixelSnappingEnabled() { return viewport.isPixelSnappingEnabled(); }
			@Override public void setPixelSnappingEnabled(boolean enabled) {
				viewport.setPixelSnappingEnabled(enabled);
			}
			@Override public boolean areConstraintsEnabled() { return viewport.areConstraintsEnabled(); }
			@Override public void setConstraintsEnabled(boolean enabled) { viewport.setConstraintsEnabled(enabled); }
			@Override public boolean isEventAudioEnabled() { return audioEventPlayer.isEnabled(); }
			@Override public void setEventAudioEnabled(boolean enabled) { audioEventPlayer.setEnabled(enabled); }
			@Override public SettingsDialog.ProjectSettings projectSettings() {
				if (doc.data == null) return new SettingsDialog.ProjectSettings("", "", 0f, 0f, 0f, 0f, 100f, 30f);
				return new SettingsDialog.ProjectSettings(doc.data.getImagesPath(), doc.data.getAudioPath(),
					doc.data.getX(), doc.data.getY(), doc.data.getWidth(), doc.data.getHeight(),
					doc.data.getReferenceScale(), doc.data.getFps());
			}
			@Override public boolean updateProjectSettings(SettingsDialog.ProjectSettings settings) {
				if (doc.data == null || settings == null) return false;
				if (settings.width() < 0f || settings.height() < 0f || settings.referenceScale() <= 0f
					|| settings.fps() <= 0f || !Float.isFinite(settings.x()) || !Float.isFinite(settings.y())
					|| !Float.isFinite(settings.width()) || !Float.isFinite(settings.height())
					|| !Float.isFinite(settings.referenceScale()) || !Float.isFinite(settings.fps())) return false;
				SettingsDialog.ProjectSettings before = projectSettings();
				if (before.equals(settings)) return false;
				applyProjectSettings(settings);
				history.record("编辑项目设置", () -> applyProjectSettings(before),
					() -> applyProjectSettings(settings));
				return true;
			}
			@Override public String[] animationNames() {
				if (doc.data == null) return new String[0];
				String[] names = new String[doc.data.getAnimations().size];
				for (int index = 0; index < names.length; index++)
					names[index] = doc.data.getAnimations().get(index).getName();
				return names;
			}
			@Override public float defaultAnimationMix() { return doc.defaultAnimationMix(); }
			@Override public List<SettingsDialog.AnimationMix> animationMixes() {
				ArrayList<SettingsDialog.AnimationMix> mixes = new ArrayList<>();
				for (SkeletonDocument.AnimationMix mix : doc.animationMixes())
					mixes.add(new SettingsDialog.AnimationMix(mix.from(), mix.to(), mix.duration()));
				return List.copyOf(mixes);
			}
			@Override public boolean updateAnimationMixSettings(float defaultMix,
				List<SettingsDialog.AnimationMix> settings) {
				ArrayList<SkeletonDocument.AnimationMix> requested = new ArrayList<>();
				if (settings != null) for (SettingsDialog.AnimationMix mix : settings)
					requested.add(new SkeletonDocument.AnimationMix(mix.from(), mix.to(), mix.duration()));
				float beforeDefault = doc.defaultAnimationMix();
				List<SkeletonDocument.AnimationMix> before = doc.animationMixes();
				if (!doc.setAnimationMixSettings(defaultMix, requested)) return false;
				List<SkeletonDocument.AnimationMix> after = doc.animationMixes();
				history.record("编辑动画混合",
					() -> doc.restoreAnimationMixSettings(beforeDefault, before),
					() -> doc.restoreAnimationMixSettings(defaultMix, after));
				return true;
			}
			@Override public String spineVersionSummary() {
				String source = doc.data == null || doc.data.getVersion() == null || doc.data.getVersion().isBlank()
					? "未知" : doc.data.getVersion();
				return source + " -> " + SkeletonExporter.EXPORT_VERSION;
			}
			@Override public SettingsDialog.ExportDependencies exportDependencies() {
				return EditorApp.this.exportDependencies();
			}
			@Override public boolean setFfmpegExecutable(String path) {
				return EditorApp.this.setFfmpegExecutable(path);
			}
			@Override public List<String> exportPresetNames() {
				return EditorApp.this.exportPresetNames();
			}
			@Override public boolean saveExportPreset(String name) {
				return EditorApp.this.saveExportPreset(name);
			}
			@Override public boolean loadExportPreset(String name) {
				return EditorApp.this.loadExportPreset(name);
			}
			@Override public boolean deleteExportPreset(String name) {
				return EditorApp.this.deleteExportPreset(name);
			}
			@Override public void resetLayout() { resetWorkspaceLayout(); }
			@Override public void syncCommandState() { EditorApp.this.syncCommandState(); }
			@Override public void settingsClosed() { saveWorkspacePreferences(); }
		});
		settingsDialog.show();
	}

	private void applyProjectSettings(SettingsDialog.ProjectSettings settings) {
		if (doc.data == null || settings == null) return;
		doc.data.setImagesPath(settings.imagesPath().isBlank() ? null : settings.imagesPath());
		doc.data.setAudioPath(settings.audioPath().isBlank() ? null : settings.audioPath());
		doc.data.setX(settings.x());
		doc.data.setY(settings.y());
		doc.data.setWidth(settings.width());
		doc.data.setHeight(settings.height());
		doc.data.setReferenceScale(settings.referenceScale());
		doc.data.setFps(settings.fps());
		doc.markDirty();
		playbackPanel.rebuild();
	}

	private SettingsDialog.ExportDependencies exportDependencies() {
		boolean webp = WebpFrameExporter.isAvailable();
		boolean ffmpeg = FfmpegAnimationExporter.isAvailable();
		return new SettingsDialog.ExportDependencies(webp,
			webp ? "ImageIO WebP writer" : "未检测到 WebP ImageIO 编码器", ffmpeg,
			FfmpegAnimationExporter.executable());
	}

	private boolean setFfmpegExecutable(String path) {
		String normalized = path == null ? "" : path.trim();
		FfmpegAnimationExporter.setConfiguredExecutable(normalized);
		if (!normalized.isEmpty() && !FfmpegAnimationExporter.isAvailable()) {
			FfmpegAnimationExporter.setConfiguredExecutable(preferences.getString(PREF_FFMPEG_PATH, ""));
			return false;
		}
		if (normalized.isEmpty()) preferences.remove(PREF_FFMPEG_PATH);
		else preferences.putString(PREF_FFMPEG_PATH, normalized);
		preferences.flush();
		return true;
	}

	private Map<String, Map<String, String>> readExportPresets() {
		if (preferences == null) return new LinkedHashMap<>();
		return ExportPresetStore.decode(preferences.getString(PREF_EXPORT_PRESETS, ""));
	}

	private void writeExportPresets(Map<String, Map<String, String>> presets) {
		if (preferences == null) return;
		preferences.putString(PREF_EXPORT_PRESETS, ExportPresetStore.encode(presets));
		preferences.flush();
	}

	private List<String> exportPresetNames() {
		return List.copyOf(readExportPresets().keySet());
	}

	private boolean saveExportPreset(String requestedName) {
		String name = ExportPresetStore.normalizeName(requestedName);
		if (name == null || preferences == null) return false;
		Map<String, String> values = exportPreferenceSnapshot();
		Map<String, Map<String, String>> presets = readExportPresets();
		presets.put(name, Map.copyOf(values));
		writeExportPresets(presets);
		return true;
	}

	private Map<String, String> exportPreferenceSnapshot() {
		Map<String, String> values = new LinkedHashMap<>();
		Map<String, ?> stored = preferences.get();
		for (String key : EXPORT_PREFERENCE_KEYS) {
			Object value = stored.get(key);
			if (value != null) values.put(key, value.toString());
		}
		float projectFps = doc != null && doc.data != null && Float.isFinite(doc.data.getFps())
			? Math.max(1f, Math.min(100f, doc.data.getFps())) : 30f;
		putDefault(values, PREF_PNG_SCALE, 1f);
		putDefault(values, PREF_PNG_PADDING, 0);
		putDefault(values, PREF_PNG_COMPRESSION, PngFrameExporter.DEFAULT_COMPRESSION);
		putDefault(values, PREF_PNG_TRANSPARENT, true);
		putDefault(values, PREF_PNG_PREMULTIPLIED, false);
		putDefault(values, PREF_JPEG_SCALE, 1f);
		putDefault(values, PREF_JPEG_PADDING, 0);
		putDefault(values, PREF_JPEG_QUALITY, JpegFrameExporter.DEFAULT_QUALITY);
		putDefault(values, PREF_PNG_SEQUENCE_FPS, projectFps);
		putDefault(values, PREF_PNG_SEQUENCE_WARM_UP, 0);
		putDefault(values, PREF_PNG_SEQUENCE_SCALE, 1f);
		putDefault(values, PREF_PNG_SEQUENCE_PADDING, 0);
		putDefault(values, PREF_PNG_SEQUENCE_COMPRESSION, PngFrameExporter.DEFAULT_COMPRESSION);
		putDefault(values, PREF_PNG_SEQUENCE_TRANSPARENT, true);
		putDefault(values, PREF_PNG_SEQUENCE_PREMULTIPLIED, false);
		putDefault(values, PREF_PNG_SEQUENCE_INCLUDE_LAST_FRAME, false);
		putDefault(values, PREF_JPEG_SEQUENCE_FPS, projectFps);
		putDefault(values, PREF_JPEG_SEQUENCE_WARM_UP, 0);
		putDefault(values, PREF_JPEG_SEQUENCE_SCALE, 1f);
		putDefault(values, PREF_JPEG_SEQUENCE_PADDING, 0);
		putDefault(values, PREF_JPEG_SEQUENCE_QUALITY, JpegFrameExporter.DEFAULT_QUALITY);
		putDefault(values, PREF_JPEG_SEQUENCE_INCLUDE_LAST_FRAME, false);
		putDefault(values, PREF_GIF_FPS, projectFps);
		putDefault(values, PREF_GIF_WARM_UP, 0);
		putDefault(values, PREF_GIF_SCALE, 1f);
		putDefault(values, PREF_GIF_PADDING, 0);
		putDefault(values, PREF_GIF_TRANSPARENT, true);
		putDefault(values, PREF_GIF_REPEAT, 0);
		putDefault(values, PREF_GIF_ALPHA_THRESHOLD, GifAnimationExporter.DEFAULT_ALPHA_THRESHOLD);
		putDefault(values, PREF_GIF_INCLUDE_LAST_FRAME, false);
		putDefault(values, PREF_APNG_FPS, projectFps);
		putDefault(values, PREF_APNG_WARM_UP, 0);
		putDefault(values, PREF_APNG_SCALE, 1f);
		putDefault(values, PREF_APNG_PADDING, 0);
		putDefault(values, PREF_APNG_TRANSPARENT, true);
		putDefault(values, PREF_APNG_REPEAT, 0);
		putDefault(values, PREF_APNG_COMPRESSION, ApngAnimationExporter.DEFAULT_COMPRESSION);
		putDefault(values, PREF_APNG_INCLUDE_LAST_FRAME, false);
		putDefault(values, PREF_AVI_FPS, projectFps);
		putDefault(values, PREF_AVI_WARM_UP, 0);
		putDefault(values, PREF_AVI_SCALE, 1f);
		putDefault(values, PREF_AVI_PADDING, 0);
		putDefault(values, PREF_AVI_QUALITY, JpegFrameExporter.DEFAULT_QUALITY);
		putDefault(values, PREF_AVI_INCLUDE_LAST_FRAME, false);
		return values;
	}

	private static void putDefault(Map<String, String> values, String key, Object value) {
		values.putIfAbsent(key, String.valueOf(value));
	}

	private boolean loadExportPreset(String requestedName) {
		if (preferences == null) return false;
		String name = ExportPresetStore.normalizeName(requestedName);
		if (name == null) return false;
		Map<String, String> values = readExportPresets().get(name);
		if (values == null) return false;
		for (String key : EXPORT_PREFERENCE_KEYS) preferences.remove(key);
		for (Map.Entry<String, String> value : values.entrySet())
			if (EXPORT_PREFERENCE_KEYS.contains(value.getKey())) preferences.putString(value.getKey(), value.getValue());
		preferences.flush();
		return true;
	}

	private boolean deleteExportPreset(String requestedName) {
		if (preferences == null) return false;
		String name = ExportPresetStore.normalizeName(requestedName);
		if (name == null) return false;
		Map<String, Map<String, String>> presets = readExportPresets();
		if (presets.remove(name) == null) return false;
		writeExportPresets(presets);
		return true;
	}

	private Palette resolvedAppearance() {
		return UiAppearance.resolve(appearanceMode, systemDark, lightTheme, darkTheme);
	}

	private void setAppearanceMode(Mode mode) {
		Mode resolved = mode == null ? Mode.SYSTEM : mode;
		if (appearanceMode == resolved) return;
		appearanceMode = resolved;
		if (appearanceMode == Mode.SYSTEM) systemDark = UiAppearance.detectSystemDark();
		applyAppearance();
	}

	private void setAppearanceTheme(boolean dark, Theme theme) {
		Theme resolved = theme == null ? Theme.JADE : theme;
		if (dark) {
			if (darkTheme == resolved) return;
			darkTheme = resolved;
		} else {
			if (lightTheme == resolved) return;
			lightTheme = resolved;
		}
		applyAppearance();
	}

	private void applyAppearance() {
		if (kit == null) return;
		Palette palette = resolvedAppearance();
		kit.applyAppearance(palette);
		if (viewport != null) viewport.applyAppearance(palette);
		if (stage != null) {
			for (Actor actor : stage.getActors())
				if (actor instanceof Window window) kit.refreshWindowAppearance(window);
		}
		applyStatusStyle();
		applyNativeTitleBar();
		persistAppearancePreferences();
	}

	private void persistAppearancePreferences() {
		if (preferences == null) return;
		try {
			preferences.putString(PREF_APPEARANCE_MODE, appearanceMode.key);
			preferences.putString(PREF_LIGHT_THEME, lightTheme.key);
			preferences.putString(PREF_DARK_THEME, darkTheme.key);
			preferences.flush();
		} catch (RuntimeException error) {
			if (Gdx.app != null) Gdx.app.error("OpenSpine", "无法保存外观设置", error);
		}
	}

	private void applyStatusStyle() {
		if (statusLabel == null || statusToast == null || kit == null) return;
		Label.LabelStyle style = new Label.LabelStyle(kit.labelStyle);
		style.fontColor = new Color(Color.WHITE);
		statusLabel.setStyle(style);
		statusLabel.setColor(Color.WHITE);
		statusToast.setBackground(statusError ? kit.danger : kit.accent);
	}

	private void setValidationLabel(Label label, boolean error) {
		if (label == null) return;
		label.setStyle(error ? kit.errorLabelStyle : kit.dialogBodyStyle);
		label.setColor(Color.WHITE);
	}

	private void applyNativeTitleBar() {
		if (nativeWindowHandle == 0L || kit == null) return;
		Palette palette = kit.palette();
		Color background = palette.dark() ? palette.panel() : palette.surface();
		WindowsTitleBar.apply(nativeWindowHandle, palette.dark(), background, palette.border(), palette.text());
	}

	void setNativeWindowHandle(long windowHandle) {
		nativeWindowHandle = windowHandle;
		WindowsTitleBar.installCustomFrame(windowHandle);
		if (kit == null) WindowsTitleBar.applyDark(windowHandle); else applyNativeTitleBar();
	}

	void refreshSystemAppearance() {
		if (kit == null) return;
		if (appearanceMode == Mode.SYSTEM) {
			boolean detected = UiAppearance.detectSystemDark();
			if (systemDark != detected) {
				systemDark = detected;
				applyAppearance();
				return;
			}
		}
		applyNativeTitleBar();
	}

	private void showExitPrompt() {
		if (stage == null) {
			exitPromptOpen = false;
			return;
		}
		showUnsavedProjectPrompt("退出 OpenSpine", () -> {
			exitPromptOpen = false;
			exitConfirmed = true;
			Gdx.app.exit();
		}, () -> exitPromptOpen = false);
	}

	private void showError(String title, Throwable error) {
		String detail = error.getMessage();
		if (detail == null || detail.isBlank()) detail = error.getClass().getSimpleName();
		Gdx.app.error("OpenSpine", title + ": " + detail, error);
		setStatus(title + "：" + detail, true);
		UiDialogs.message(stage, kit, title, detail, UiDialogs.Tone.ERROR);
	}

	private void showMessage(String title, String message) {
		UiDialogs.message(stage, kit, title, message, UiDialogs.Tone.INFO);
	}

	private void showUnsavedChangesPrompt(String title, String message, Runnable action, Runnable stayAction) {
		Runnable saveChoice = () -> {
			save(() -> {
				if (skeletonProject.isDirty()) showUnsavedProjectPrompt(title, action, stayAction);
				else action.run();
			}, stayAction);
		};
		UiDialogs.unsavedChanges(stage, kit, title, message,
			skeletonProject.size() > 1 ? "全部不保存" : "不保存",
			saveChoice, action, () -> { if (stayAction != null) stayAction.run(); });
	}

	private void maybeAutosave() {
		long now = TimeUtils.millis();
		if (now < nextAutosaveAt || autosaveInProgress || disposed || !skeletonProject.isDirty()
			|| documentIoInProgress() || animationRenderer != null || fileDialogOpen || hasModalWindow()) return;
		nextAutosaveAt = now + AUTOSAVE_INTERVAL_MILLIS;
		List<AutoSaveStore.SnapshotDocument> documents = new ArrayList<>();
		String editorStateJson;
		try {
			treePanel.persistViewState();
			timelinePanel.persistViewState();
			List<SkeletonDocument> projectDocuments = skeletonProject.documents();
			boolean nativeRecovery = usesNativeProjectWorkflow();
			for (int index = 0; index < projectDocuments.size(); index++) {
				SkeletonDocument document = projectDocuments.get(index);
				documents.add(new AutoSaveStore.SnapshotDocument(skeletonProject.nameAt(index),
					SkeletonExporter.toJson(document), nativeRecovery ? RECOVERED_NATIVE_PROJECT : document.filePath,
					nativeRecovery ? null : document.savePath,
					document.resourceDirectory, document.atlasFileName));
			}
			editorStateJson = ProjectStateStore.snapshotEditorState(skeletonProject, showAllSkeletons);
		} catch (IOException | RuntimeException error) {
			Gdx.app.error("OpenSpine", "自动保存序列化失败", error);
			return;
		}
		long signature = autosaveSignature(editorStateJson);
		if (signature == lastAutosaveSignature) return;
		AutoSaveStore.Snapshot snapshot = new AutoSaveStore.Snapshot(documents,
			skeletonProject.activeIndex(), showAllSkeletons, editorStateJson);
		autosaveInProgress = true;
		Thread worker = new Thread(() -> {
			Throwable failure = null;
			try { AutoSaveStore.write(snapshot); }
			catch (Throwable error) { failure = error; }
			Throwable completedFailure = failure;
			postToRenderThread(() -> {
				autosaveInProgress = false;
				autosaveThread = null;
				if (completedFailure == null) lastAutosaveSignature = signature;
				else if (!disposed) Gdx.app.error("OpenSpine", "自动保存写入失败", completedFailure);
			});
		}, "openspine-autosave");
		worker.setDaemon(true);
		autosaveThread = worker;
		try { worker.start(); }
		catch (RuntimeException error) {
			autosaveThread = null;
			autosaveInProgress = false;
			Gdx.app.error("OpenSpine", "无法启动自动保存", error);
		}
	}

	private long autosaveSignature(String editorStateJson) {
		long signature = 1125899906842597L;
		for (SkeletonDocument document : skeletonProject.documents()) {
			signature = signature * 31L + document.revision;
			signature = signature * 31L + (document.dirty ? 1L : 0L);
		}
		signature = signature * 31L + (editorStateJson == null ? 0 : editorStateJson.hashCode());
		return signature;
	}

	private void showAutosaveRecoveryPrompt() {
		if (disposed || stage == null || hasModalWindow()) return;
		AutoSaveStore.Session session;
		try { session = AutoSaveStore.latest(); }
		catch (IOException error) {
			Gdx.app.error("OpenSpine", "读取自动保存失败", error);
			return;
		}
		if (session == null) return;
		if (!autosaveMayBeNewer(session)) {
			try { AutoSaveStore.discardAll(); }
			catch (IOException error) { Gdx.app.error("OpenSpine", "清理过期自动保存失败", error); }
			return;
		}
		Actor previousFocus = stage.getKeyboardFocus();
		Window window = kit.window("恢复自动保存");
		window.setModal(true);
		kit.padWindow(window, 12f);
		Label message = new Label("检测到上次异常结束前保存的 " + session.documents().size()
			+ " 个骨架快照。", kit.dialogBodyStyle);
		message.setWrap(true);
		window.add(message).width(430f).left().pad(4f, 4f, 14f, 4f).row();
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previousFocus);
		};
		Runnable recover = () -> {
			close.run();
			recoverAutosave(session);
		};
		Runnable discard = () -> {
			close.run();
			try {
				AutoSaveStore.discardAll();
				setStatus("已丢弃自动保存快照", false);
			} catch (IOException error) { showError("无法丢弃自动保存", error); }
		};
		TextButton later = button("稍后", close);
		TextButton discardButton = new TextButton("丢弃", kit.dangerButton);
		discardButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { discard.run(); }
		});
		TextButton recoverButton = new TextButton("恢复", kit.accentButton);
		recoverButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { recover.run(); }
		});
		Table actions = new Table();
		actions.add(later).width(86f).height(30f).padRight(6f);
		actions.add(discardButton).width(86f).height(30f).padRight(6f);
		actions.add(recoverButton).width(86f).height(30f);
		window.add(actions).right().pad(4f);
		addModalKeyActions(window, recover, close);
		showCentered(window);
	}

	private boolean autosaveMayBeNewer(AutoSaveStore.Session session) {
		for (AutoSaveStore.StoredDocument stored : session.documents()) {
			String path = stored.savePath().isBlank() ? stored.filePath() : stored.savePath();
			if (!isPersistentDocumentPath(path)) return true;
			try {
				Path file = Path.of(path).toAbsolutePath().normalize();
				if (!Files.isRegularFile(file)
					|| Files.getLastModifiedTime(file).toMillis() < session.createdAt()) return true;
			} catch (IOException | RuntimeException error) { return true; }
		}
		return false;
	}

	private void recoverAutosave(AutoSaveStore.Session session) {
		List<SkeletonProject.LoadedSkeleton> recovered = new ArrayList<>();
		boolean installed = false;
		try {
			for (AutoSaveStore.StoredDocument stored : session.documents()) {
				SkeletonDocument candidate = new SkeletonDocument();
				try {
					SkeletonLoader.loadAutosaveSnapshot(candidate, new FileHandle(stored.jsonPath().toFile()),
						stored.filePath(), stored.savePath(), stored.resourceDirectory(), stored.atlasFileName());
					initAnimationState(candidate);
					candidate.markDirty();
					recovered.add(new SkeletonProject.LoadedSkeleton(stored.name(), candidate));
				} catch (RuntimeException error) {
					candidate.dispose();
					throw error;
				}
			}
			replaceProjectSkeletons(recovered);
			installed = true;
			boolean editorStateRestored = false;
			if (session.editorStatePath() != null) {
				try {
					ProjectStateStore.LoadOutcome outcome = ProjectStateStore.restoreEditorState(
						session.editorStatePath(), skeletonProject, session.showAll());
					showAllSkeletons = outcome.showAllSkeletons();
					editorStateRestored = outcome.result() == ProjectStateStore.LoadResult.RESTORED;
				} catch (IOException | RuntimeException error) {
					Gdx.app.error("OpenSpine", "恢复自动保存编辑器状态失败", error);
				}
			}
			if (!editorStateRestored) {
				if (session.activeIndex() != skeletonProject.activeIndex())
					switchProjectSkeleton(session.activeIndex());
				showAllSkeletons = session.showAll();
			}
			finishLoad("已恢复自动保存快照；请检查后保存项目");
			try { AutoSaveStore.delete(session); }
			catch (IOException error) { Gdx.app.error("OpenSpine", "恢复后清理自动保存失败", error); }
			lastAutosaveSignature = Long.MIN_VALUE;
		} catch (RuntimeException error) {
			if (!installed) for (SkeletonProject.LoadedSkeleton loaded : recovered) loaded.document().dispose();
			showError("恢复自动保存失败", error);
		}
	}

	private void stopAutosaveAndDiscard() {
		Thread worker = autosaveThread;
		if (worker != null) {
			worker.interrupt();
			try { worker.join(5000L); }
			catch (InterruptedException error) { Thread.currentThread().interrupt(); }
		}
		try { AutoSaveStore.discardAll(); }
		catch (IOException error) {
			if (Gdx.app != null) Gdx.app.error("OpenSpine", "清理自动保存失败", error);
		}
	}

	private void showCentered(Window window) {
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(window);
	}

	private static void addModalKeyActions(Window window, Runnable confirmAction, Runnable cancelAction) {
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (UiDialogs.keyAction(keycode)) {
				case CONFIRM -> { confirmAction.run(); yield true; }
				case CANCEL -> { cancelAction.run(); yield true; }
				case NONE -> false;
				};
			}
		});
	}

	@Override
	public void resize(int width, int height) {
		stage.getViewport().update(width, height, true);
		layoutStatusToast();
	}

	@Override
	public void render() {
		float delta = Math.min(0.1f, Gdx.graphics.getDeltaTime());
		Color background = kit.palette().appBackground();
		Gdx.gl.glClearColor(background.r, background.g, background.b, background.a);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		advanceAnimationRender();
		maybeAutosave();
		audioEventPlayer.beginPlaybackFrame(doc);
		try {
			doc.applyAnimation(delta);
			skeletonProject.applyInactiveAnimations(delta);
		} finally {
			audioEventPlayer.endPlaybackFrame(doc);
		}
		playbackPanel.update();
		audioEventPlayer.sync(doc);
		propsPanel.refresh();
		updateStatusBar();
		stage.act(delta);
		updateWorkspaceCursor(hasModalWindow() || interfaceMenu != null || projectMenu != null);
		stage.draw();
		treePanel.syncSelection();
		if (mcpCaptureBridge != null) mcpCaptureBridge.poll(doc, skeletonProject.documents());
	}

	@Override
	public void pause() {
		if (audioEventPlayer != null) audioEventPlayer.stop();
		saveCurrentSpineProjectState();
		saveWorkspacePreferences();
	}

	@Override
	public void dispose() {
		disposed = true;
		if (mcpCaptureBridge != null) {
			try { mcpCaptureBridge.close(); }
			catch (IOException error) { Gdx.app.error("OpenSpine MCP capture", error.getMessage()); }
		}
		I18n.removeListener(languageListener);
		cancelProjectMenuDismiss();
		if (interfaceMenu != null) interfaceMenu.dismiss();
		cancelSpineProjectLoad(false);
		cancelAnimationImport();
		cancelDocumentIo(false);
		awaitDocumentIoShutdown();
		// Extraction may have succeeded just before shutdown, with its UI callback still queued.
		cleanupPsdImport(pendingPsdImport);
		cancelAnimationRender(false);
		closeAnimationRenderer(animationRenderer);
		clearAnimationRenderState();
		stopAutosaveAndDiscard();
		saveCurrentSpineProjectState();
		saveWorkspacePreferences();
		if (audioEventPlayer != null) audioEventPlayer.dispose();
		viewport.dispose();
		timelinePanel.dispose();
		skeletonProject.close();
		stage.dispose();
		if (titleBarIconTexture != null) titleBarIconTexture.dispose();
		kit.dispose();
		WindowsTitleBar.uninstallCustomFrame(nativeWindowHandle);
	}

	private ProjectStateStore.LoadResult restoreProjectState(String path) {
		try {
			ProjectStateStore.LoadOutcome outcome = isProjectPackagePath(path)
				? ProjectStateStore.loadViewState(Path.of(path), skeletonProject, showAllSkeletons)
				: ProjectStateStore.load(Path.of(path), skeletonProject, showAllSkeletons);
			if (outcome.result() == ProjectStateStore.LoadResult.RESTORED)
				showAllSkeletons = outcome.showAllSkeletons();
			return outcome.result();
		} catch (IOException | RuntimeException error) {
			logProjectStateError("读取 OpenSpine 工程状态失败", error);
			return ProjectStateStore.LoadResult.NOT_FOUND;
		}
	}

	private void saveCurrentSpineProjectState() {
		if (nativeProjectPath == null && usesNativeProjectWorkflow()) return;
		Path source = currentProjectStatePath();
		if (source == null || skeletonProject.size() == 0) return;
		try {
			if (treePanel != null) treePanel.persistViewState();
			if (timelinePanel != null) timelinePanel.persistViewState();
			if (nativeProjectPath != null) ProjectStateStore.writeViewState(source, skeletonProject, showAllSkeletons);
			else ProjectStateStore.write(source, skeletonProject, showAllSkeletons);
		} catch (IOException | RuntimeException error) {
			logProjectStateError("写入 OpenSpine 工程状态失败", error);
		}
	}

	private Path currentProjectStatePath() {
		if (nativeProjectPath != null) return nativeProjectPath;
		if (doc.savePath != null && !doc.savePath.isBlank()) {
			try {
				Path saved = Path.of(doc.savePath).toAbsolutePath().normalize();
				if (SkeletonLoader.isSupportedFileName(saved.toString()) && Files.isRegularFile(saved)) return saved;
			} catch (RuntimeException ignored) {
			}
		}
		for (SkeletonDocument document : skeletonProject.documents()) {
			if (document.filePath == null || document.filePath.isBlank()) continue;
			try {
				Path source = Path.of(document.filePath).toAbsolutePath().normalize();
				if (SkeletonLoader.isSupportedFileName(source.toString()) && Files.isRegularFile(source)) return source;
			} catch (RuntimeException ignored) {
			}
		}
		return null;
	}

	private boolean usesNativeProjectWorkflow() {
		return nativeProjectPath != null || requiresProjectRecovery(skeletonProject);
	}

	static boolean requiresProjectRecovery(SkeletonProject project) {
		return project.hasStructureChanges()
			|| project.documents().stream().anyMatch(document -> RECOVERED_NATIVE_PROJECT.equals(document.filePath));
	}

	private void logProjectStateError(String message, Throwable error) {
		if (Gdx.app != null) Gdx.app.error("OpenSpine", message, error);
		else System.err.println(message + ": " + error.getMessage());
	}

	private void awaitDocumentIoShutdown() {
		Thread worker = documentIoThread;
		if (worker == null || worker == Thread.currentThread()) return;
		boolean interrupted = false;
		while (worker.isAlive()) {
			try {
				worker.join();
			} catch (InterruptedException ignored) {
				interrupted = true;
				CancellationToken cancellation = documentIoCancellation;
				if (cancellation != null) cancellation.cancel();
				worker.interrupt();
			}
		}
		documentIoThread = null;
		documentIoCancellation = null;
		if (interrupted) Thread.currentThread().interrupt();
	}

	private void saveWorkspacePreferences() {
		if (preferences == null) return;
		try {
			captureWorkspaceSplitAmounts();
			preferences.putInteger(PREF_VERSION, PREFERENCES_VERSION);
			preferences.putString(PREF_LANGUAGE, I18n.language().code);
			preferences.putString(PREF_APPEARANCE_MODE, appearanceMode.key);
			preferences.putString(PREF_LIGHT_THEME, lightTheme.key);
			preferences.putString(PREF_DARK_THEME, darkTheme.key);
			preferences.putFloat(PREF_WORKSPACE_SPLIT, workspaceSplitAmount);
			preferences.putFloat(PREF_NAVIGATION_SPLIT, navigationSplitAmount);
			preferences.putFloat(PREF_INSPECTOR_SPLIT, inspectorSplitAmount);
			preferences.putBoolean(PREF_HIERARCHY_VISIBLE, hierarchyVisible);
			preferences.putBoolean(PREF_INSPECTOR_VISIBLE, inspectorVisible);
			preferences.putBoolean(PREF_ANIMATION_VISIBLE, animationVisible);
			if (timelinePanel != null) {
				preferences.putFloat(PREF_TIMELINE_ZOOM, timelinePanel.getZoom());
				preferences.putString(PREF_TRACK_FILTER, timelinePanel.getTrackFilterKey());
			}
			preferences.putBoolean(PREF_INSPECTOR_ATTACHMENTS, attachmentsInspectorSelected);
			if (lastOpenDirectory != null && !lastOpenDirectory.isBlank())
				preferences.putString(PREF_LAST_OPEN_DIRECTORY, lastOpenDirectory);
			if (lastSaveDirectory != null && !lastSaveDirectory.isBlank())
				preferences.putString(PREF_LAST_SAVE_DIRECTORY, lastSaveDirectory);
			preferences.putString(PREF_RECENT_PROJECTS, encodeRecentProjects(recentProjects));
			if (audioEventPlayer != null)
				preferences.putBoolean(PREF_EVENT_AUDIO_ENABLED, audioEventPlayer.isEnabled());
			if (viewport != null) {
				preferences.putBoolean(PREF_GRID_VISIBLE, viewport.isGridVisible());
				preferences.putBoolean(PREF_PIXEL_GRID_VISIBLE, viewport.isPixelGridVisible());
				preferences.putBoolean(PREF_BONES_VISIBLE, viewport.isBonesVisible());
				preferences.putBoolean(PREF_SELECTION_HIDDEN, viewport.isSelectionHidden());
				preferences.putBoolean(PREF_INACTIVE_BONES, viewport.isShowInactiveSkinBones());
				preferences.putBoolean(PREF_GHOSTING_VISIBLE, viewport.isGhostingVisible());
				preferences.putBoolean(PREF_GHOST_BONES_VISIBLE, viewport.isGhostBonesVisible());
				preferences.putBoolean(PREF_TEXTURE_SMOOTHING, viewport.isTextureSmoothingEnabled());
				preferences.putBoolean(PREF_VIEWPORT_FLIPPED, viewport.isViewportFlipped());
				preferences.putBoolean(PREF_ZOOM_LOCKED, viewport.isZoomLocked());
				preferences.putBoolean(PREF_PIXEL_SNAPPING, viewport.isPixelSnappingEnabled());
			}
			for (var entry : keyBindings.toStored().entrySet())
				preferences.putString(entry.getKey(), entry.getValue());
			preferences.flush();
		} catch (RuntimeException error) {
			if (Gdx.app != null) Gdx.app.error("OpenSpine", "无法保存工作区设置", error);
		}
	}
}
