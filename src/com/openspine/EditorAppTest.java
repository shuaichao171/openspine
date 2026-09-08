package com.openspine;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;

/** Headless regression tests for desktop shell input routing. */
public final class EditorAppTest {
	public static void main(String[] args) throws Exception {
		testsAtlasOutputDirectory();
		assertTrue(OpenSpine.VIEWPORT_MSAA_SAMPLES == 4, "desktop rendering requests 4x MSAA");
		assertNear(400f, EditorApp.IMPORT_DIALOG_CONTENT_WIDTH,
			"the import dialog keeps a stable readable content width");
		assertNear(6f, EditorApp.IMPORT_PROGRESS_HEIGHT,
			"the import progress indicator keeps a stable compact height");
		assertNear(0.12f, EditorApp.PROJECT_MENU_DISMISS_DELAY_SECONDS,
			"the project menu close delay allows movement from its button into the menu");
		assertNear(52f, EditorApp.TOOLBAR_TOOL_GROUP_GAP,
			"editing tools start one standard button width after undo and redo");
		assertTrue(EditorApp.shouldDismissProjectMenu(false, false),
			"the project menu closes after the pointer leaves both hover regions");
		assertTrue(!EditorApp.shouldDismissProjectMenu(true, false),
			"hovering the project button keeps its menu open");
		assertTrue(!EditorApp.shouldDismissProjectMenu(false, true),
			"hovering the project menu keeps it open");
		assertEquals(null, EditorApp.firstSupportedDrop(null), "null drop");
		assertEquals(null, EditorApp.firstSupportedDrop(new String[0]), "empty drop");
		assertEquals("C:\\work\\hero.JSON",
			EditorApp.firstSupportedDrop(new String[] {"notes.txt", "C:\\work\\hero.JSON"}),
			"first supported JSON drop");
		assertEquals("C:\\work\\hero.SPINE",
			EditorApp.firstSupportedDrop(new String[] {"notes.txt", "C:\\work\\hero.SPINE", "later.json"}),
			"Spine editor projects can be dropped into the window");
		assertEquals("hero.skel", EditorApp.firstSupportedDrop(new String[] {null, "hero.skel", "later.json"}),
			"first supported skeleton drop");
		assertEquals(null, EditorApp.firstSupportedDrop(new String[] {"hero.atlas", "hero.png"}),
			"unsupported assets are ignored");
		assertTrue(!EditorApp.shouldConfirmBeforeOpen("hero.json", true),
			"runtime files append without prompting when a persistent document is already open");
		assertTrue(EditorApp.shouldConfirmBeforeOpen("hero.json", false),
			"runtime files confirm before replacing an unsaved new document");
		assertTrue(EditorApp.shouldConfirmBeforeOpen("hero.spine", true),
			"Spine project imports confirm because they replace the current project");
		assertTrue(EditorApp.shouldConfirmBeforeOpen("C:\\work\\openspine-project.json", true),
			"a packaged workspace checks unsaved documents before replacing the whole project");
		assertEquals(EditorApp.HistoryAction.UNDO, EditorApp.historyAction(false, Input.Keys.Z, false),
			"Ctrl+Z routes to document undo outside text fields");
		assertEquals(EditorApp.HistoryAction.REDO, EditorApp.historyAction(false, Input.Keys.Z, true),
			"Ctrl+Shift+Z routes to document redo");
		assertEquals(EditorApp.HistoryAction.REDO, EditorApp.historyAction(false, Input.Keys.Y, false),
			"Ctrl+Y routes to document redo");
		assertEquals(EditorApp.HistoryAction.NONE, EditorApp.historyAction(true, Input.Keys.Z, false),
			"text field keeps its own undo shortcut");
		assertEquals(EditorApp.HistoryAction.NONE, EditorApp.historyAction(true, Input.Keys.Y, false),
			"text field keeps its own redo shortcut");
		assertEquals(EditorApp.SaveAction.SAVE, EditorApp.saveAction(Input.Keys.S, false),
			"Ctrl+S routes to Save");
		assertEquals(EditorApp.SaveAction.SAVE_AS, EditorApp.saveAction(Input.Keys.S, true),
			"Ctrl+Shift+S routes to Save As");
		assertEquals(EditorApp.SaveAction.NONE, EditorApp.saveAction(Input.Keys.O, false),
			"unrelated shortcut does not save");
		assertTrue(EditorApp.globalShortcutsAllowed(false, false),
			"global shortcuts work in the editor workspace");
		assertTrue(!EditorApp.globalShortcutsAllowed(true, false),
			"modal windows block global shortcuts");
		assertTrue(!EditorApp.globalShortcutsAllowed(false, true),
			"focused context menus block global shortcuts");
		assertTrue(!EditorApp.DEFAULT_SHOW_ALL_SKELETONS,
			"new multi-skeleton projects show only the active skeleton by default");
		assertNear(490f, EditorApp.statusToastX(1280f, 300f),
			"operation feedback is centered horizontally");
		assertNear(727f, EditorApp.statusToastY(760f, 36f, true),
			"custom-frame feedback is vertically centered in the software title bar");
		assertNear(723f, EditorApp.statusToastY(760f, 36f, false),
			"native-frame feedback is vertically centered in the top workspace toolbar");
		assertNear(240f, EditorApp.statusToastWidth(200f, 1200f),
			"feedback width is 1.2 times its complete text width");
		assertNear(23f, EditorApp.statusToastHeight(20f),
			"feedback height is 1.15 times its text height");
		assertNear(180f, EditorApp.statusToastWidth(200f, 180f),
			"feedback width remains inside a narrow software window");
		assertTrue(EditorApp.transientStatusVisible("已保存", 5000L, 4999L),
			"recent operation feedback remains visible in the floating toast");
		assertTrue(!EditorApp.transientStatusVisible("已保存", 5000L, 5000L),
			"operation feedback disappears without leaving a permanent bottom bar");
		assertTrue(!EditorApp.transientStatusVisible(null, 5000L, 4000L),
			"an empty status never reserves workspace space");
		assertTrue(EditorApp.shouldMirrorViewportAttachmentInTree(false),
			"direct viewport attachment hits are mirrored in the hierarchy tree");
		assertTrue(!EditorApp.shouldMirrorViewportAttachmentInTree(true),
			"attachment callbacks caused by a tree selection keep the selected hierarchy node");
		Group textInput = new Group();
		Actor inputChild = new Actor();
		textInput.addActor(inputChild);
		assertTrue(!EditorApp.pointerLeavesFocus(textInput, textInput),
			"clicking the focused input keeps focus");
		assertTrue(!EditorApp.pointerLeavesFocus(textInput, inputChild),
			"clicking inside the focused input keeps focus");
		assertTrue(EditorApp.pointerLeavesFocus(textInput, new Actor()),
			"clicking another control leaves the old input focus");
		assertTrue(EditorApp.pointerLeavesFocus(textInput, null),
			"a pointer target outside the scene leaves the old input focus");
		assertTrue(!EditorApp.pointerLeavesFocus(null, new Actor()),
			"missing keyboard focus requires no cleanup");
		assertTrue(EditorApp.shouldFinishWorkspaceTextEdit(true, false),
			"document actions finish a focused workspace text edit before inspecting dirty state");
		assertTrue(!EditorApp.shouldFinishWorkspaceTextEdit(false, false),
			"document actions leave non-text workspace focus unchanged");
		assertTrue(!EditorApp.shouldFinishWorkspaceTextEdit(true, true),
			"document actions do not interrupt text entry inside a modal window");
		String[] projectMenuLabels = java.util.Arrays.stream(EditorApp.ProjectCommand.values())
			.map(command -> command.label).toArray(String[]::new);
		assertEquals(java.util.List.of("新建项目", "打开项目", "最近项目", "保存项目", "项目另存为", "打包项目",
			"新建骨架", "管理骨架", "导入项目", "导入动画", "导入图片并打包图集", "导入分层 PSD", "重新加载图片", "项目诊断", "导出项目", "设置"),
			java.util.List.of(projectMenuLabels),
			"the project menu exposes every requested command in order");
		assertEquals(java.util.List.of(
			com.openspine.view.EditorTool.SELECT,
			com.openspine.view.EditorTool.ROTATE,
			com.openspine.view.EditorTool.MOVE,
			com.openspine.view.EditorTool.SCALE,
			com.openspine.view.EditorTool.SHEAR,
			com.openspine.view.EditorTool.MESH,
			com.openspine.view.EditorTool.DEFORM,
			com.openspine.view.EditorTool.WEIGHT),
			java.util.List.of(com.openspine.view.EditorTool.values()),
			"the shear tool appears immediately after scale");
		assertEquals("倾斜", com.openspine.view.EditorTool.SHEAR.localizedLabel(),
			"the shear tool exposes its Chinese label");
		KeyBindings toolBindings = new KeyBindings();
		assertEquals("C 旋转", EditorApp.toolButtonLabel(com.openspine.view.EditorTool.ROTATE, toolBindings),
			"the rotate toolbar button shows Spine's shortcut");
		assertEquals("V 移动", EditorApp.toolButtonLabel(com.openspine.view.EditorTool.MOVE, toolBindings),
			"the move toolbar button shows Spine's shortcut");
		assertEquals("X 缩放", EditorApp.toolButtonLabel(com.openspine.view.EditorTool.SCALE, toolBindings),
			"the scale toolbar button shows Spine's shortcut");
		assertEquals("Z 倾斜", EditorApp.toolButtonLabel(com.openspine.view.EditorTool.SHEAR, toolBindings),
			"the shear toolbar button shows Spine's shortcut");
		assertEquals("G 权重", EditorApp.toolButtonLabel(com.openspine.view.EditorTool.WEIGHT, toolBindings),
			"the weight toolbar button shows Spine's shortcut");
		assertEquals("B 姿态", EditorApp.poseButtonLabel(toolBindings),
			"the Pose button shows Spine's shortcut");
		toolBindings.bind(KeyBindings.EditorAction.TOOL_ROTATE,
			new KeyBindings.Combo(com.badlogic.gdx.Input.Keys.J, false, false));
		assertEquals("J 旋转", EditorApp.toolButtonLabel(com.openspine.view.EditorTool.ROTATE, toolBindings),
			"the toolbar follows customized shortcuts");
		com.openspine.ui.I18n.setLanguage(com.openspine.ui.I18n.Language.ENGLISH);
		try {
			assertEquals("New Project", EditorApp.ProjectCommand.NEW_PROJECT.localizedLabel(),
				"project commands expose English labels");
			assertEquals("Vertex", com.openspine.view.EditorTool.MESH.localizedLabel(),
				"editor tools expose English labels");
			assertEquals("Shear", com.openspine.view.EditorTool.SHEAR.localizedLabel(),
				"the shear tool exposes its English label");
			assertEquals("J Rotate", EditorApp.toolButtonLabel(com.openspine.view.EditorTool.ROTATE, toolBindings),
				"shortcut labels follow the interface language");
		} finally {
			com.openspine.ui.I18n.setLanguage(com.openspine.ui.I18n.Language.CHINESE);
		}
		java.util.List<String> recent = EditorApp.updateRecentProjects(
			java.util.List.of("B.json", "A.json"), "A.json");
		assertEquals(java.util.List.of("A.json", "B.json"), recent,
			"opening a recent project moves it to the front without duplication");
		assertEquals(recent, EditorApp.decodeRecentProjects(EditorApp.encodeRecentProjects(recent)),
			"recent project preferences round trip");
		assertEquals("hero_project", EditorApp.safeProjectFileStem("hero:project"),
			"new project names are converted to valid Windows file names");
		com.openspine.model.SkeletonDocument blankProject = EditorApp.blankProjectDocument("blank-project");
		assertEquals("blank-project", blankProject.data.getName(), "new projects preserve their name");
		assertEquals("root", blankProject.skeleton.getRootBone().getData().getName(),
			"new projects contain an editable root bone");
		assertTrue(blankProject.skeleton.getRootBone().getData().getVisible(),
			"new project root bones are visible in the viewport");
		assertTrue(com.openspine.io.SkeletonExporter.toJson(blankProject).contains("\"root\""),
			"new projects can be exported immediately");
		blankProject.dispose();
		assertEquals("existing.json", EditorApp.suggestedSavePath("existing.json", "source.skel"),
			"associated save path is preferred");
		assertTrue(EditorApp.suggestedSavePath(null, "source.skel").endsWith("source.skel"),
			"binary source keeps its binary format");
		assertTrue(EditorApp.suggestedSavePath(null, "builtin:example").endsWith("skeleton.json"),
			"built-in document has a useful JSON name");
		String assetsDirectory = new java.io.File("assets").getAbsolutePath();
		assertEquals(new java.io.File(assetsDirectory, "skeleton.json").getPath(),
			EditorApp.suggestedSavePath(null, "builtin:example", assetsDirectory),
			"built-in document uses the most recent save directory");
		assertEquals(new java.io.File(assetsDirectory, "new_project.json").getPath(),
			EditorApp.suggestedSavePath(null, "new:new:project", assetsDirectory),
			"a new project receives a safe JSON suggestion");
		assertTrue(EditorApp.suggestedPackagePath(null, "new:hero", assetsDirectory).endsWith("hero-project.zip"),
			"a new project receives a useful package name");
		assertTrue(EditorApp.suggestedSpineProjectPath(null, "new:hero", assetsDirectory)
			.endsWith("hero-rebuilt.spine"), "a rebuilt Spine project uses a distinct destination name");
		assertEquals(new java.io.File(assetsDirectory, "skeleton.png").getPath(),
			EditorApp.suggestedPngPath(null, "builtin:example", assetsDirectory),
			"the PNG export uses the recent save directory and project name");
		assertTrue(EditorApp.suggestedPngPath("C:\\work\\saved.JSON", "source.skel", assetsDirectory)
			.endsWith("saved.png"), "an associated save path supplies the PNG file stem");
		assertEquals(new java.io.File(assetsDirectory, "skeleton.jpg").getPath(),
			EditorApp.suggestedJpegPath(null, "builtin:example", assetsDirectory),
			"the JPEG export uses the recent save directory and project name");
		assertEquals(new java.io.File(assetsDirectory, "skeleton.gif").getPath(),
			EditorApp.suggestedGifPath(null, "builtin:example", assetsDirectory),
			"the GIF export uses the recent save directory and project name");
		assertEquals(new java.io.File(assetsDirectory, "skeleton.apng").getPath(),
			EditorApp.suggestedApngPath(null, "builtin:example", assetsDirectory),
			"the APNG export uses the recent save directory and project name");
		assertEquals(new java.io.File(assetsDirectory, "skeleton.avi").getPath(),
			EditorApp.suggestedAviPath(null, "builtin:example", assetsDirectory),
			"the AVI export uses the recent save directory and project name");
		com.openspine.io.PngFrameExporter.Settings png =
			EditorApp.pngExportSettings("150", "8", "9", true, false);
		assertNear(1.5f, png.scale(), "PNG scale percentages are converted to factors");
		assertTrue(png.padding() == 8 && png.compression() == 9,
			"PNG padding and compression are parsed as integers");
		assertTrue(png.transparent() && !png.premultipliedAlpha(),
			"transparent straight-alpha export settings are preserved");
		assertTrue(!EditorApp.pngExportSettings("100", "0", false, true).premultipliedAlpha(),
			"opaque PNG output cannot request premultiplied alpha");
		assertThrows(() -> EditorApp.pngExportSettings("0", "0", true, false),
			"PNG scale below the supported range is rejected");
		assertThrows(() -> EditorApp.pngExportSettings("100", "-1", true, false),
			"negative PNG padding is rejected");
		assertThrows(() -> EditorApp.pngExportSettings("100", "pixels", true, false),
			"non-numeric PNG padding is rejected");
		assertThrows(() -> EditorApp.pngExportSettings("100", "0", "10", true, false),
			"PNG compression above nine is rejected");
		com.openspine.io.JpegFrameExporter.Settings jpeg =
			EditorApp.jpegExportSettings("125", "6", "87");
		assertNear(1.25f, jpeg.scale(), "JPEG scale percentages are converted to factors");
		assertTrue(jpeg.padding() == 6 && jpeg.quality() == 87,
			"JPEG padding and integer quality are preserved");
		assertThrows(() -> EditorApp.jpegExportSettings("100", "0", "101"),
			"JPEG quality above the supported range is rejected");
		assertThrows(() -> EditorApp.jpegExportSettings("100", "0", "high"),
			"non-numeric JPEG quality is rejected");
		com.openspine.io.PngAnimationExporter.Settings pngSequence =
			EditorApp.pngSequenceExportSettings("24", "2", "12", "2", "150", "5", "9", true, true);
		assertNear(24f, pngSequence.fps(), "PNG sequence FPS is parsed as a finite value");
		assertTrue(pngSequence.startFrame() == 2 && pngSequence.endFrame() == 12
			&& pngSequence.frameCount() == 11, "PNG sequence frame range remains inclusive");
		assertTrue(pngSequence.warmUp() == 2 && pngSequence.compression() == 9
			&& pngSequence.transparent() && pngSequence.premultipliedAlpha()
			&& pngSequence.padding() == 5, "PNG sequence alpha and padding settings are preserved");
		assertTrue(EditorApp.pngSequenceExportSettings(
			"24", "2", "12", "0", "100", "0", "6", true, false, false).frameCount() == 10,
			"PNG sequence settings can omit the last frame");
		assertTrue(!EditorApp.pngSequenceExportSettings("30", "0", "1", "100", "0", false, true)
			.premultipliedAlpha(), "opaque PNG sequences cannot request premultiplied alpha");
		assertThrows(() -> EditorApp.pngSequenceExportSettings("101", "0", "10", "100", "0",
			true, false), "PNG sequence FPS above 100 is rejected");
		assertThrows(() -> EditorApp.pngSequenceExportSettings("30", "0", "10", "101", "100", "0",
			true, false), "PNG sequence warm-up above 100 is rejected");
		assertThrows(() -> EditorApp.pngSequenceExportSettings("30", "0", "10", "0", "100", "0", "10",
			true, false), "PNG sequence compression above nine is rejected");
		com.openspine.io.JpegAnimationExporter.Settings jpegSequence =
			EditorApp.jpegSequenceExportSettings("25", "3", "8", "3", "125", "4", "86");
		assertNear(25f, jpegSequence.fps(), "JPEG sequence FPS is parsed as a finite value");
		assertTrue(jpegSequence.startFrame() == 3 && jpegSequence.endFrame() == 8
			&& jpegSequence.frameCount() == 6, "JPEG sequence frame range remains inclusive");
		assertTrue(jpegSequence.warmUp() == 3 && jpegSequence.quality() == 86 && jpegSequence.padding() == 4,
			"JPEG sequence quality and padding settings are preserved");
		assertTrue(!EditorApp.jpegSequenceExportSettings(
			"25", "3", "8", "0", "100", "0", "86", false).includeLastFrame(),
			"JPEG sequence settings preserve the last-frame toggle");
		assertThrows(() -> EditorApp.jpegSequenceExportSettings("30", "0", "10", "100", "0", "101"),
			"JPEG sequence quality above 100 is rejected");
		com.openspine.io.GifAnimationExporter.Settings gif = EditorApp.gifExportSettings(
			"24", "2", "12", "2", "150", "5", true, false, "3", "96");
		assertNear(24f, gif.fps(), "GIF FPS is parsed as a finite value");
		assertTrue(gif.startFrame() == 2 && gif.endFrame() == 12 && gif.frameCount() == 11,
			"GIF frame range remains inclusive");
		assertTrue(gif.warmUp() == 2 && gif.repeat() == 3 && gif.alphaThreshold() == 96 && gif.transparent(),
			"GIF loop and transparency settings are preserved");
		assertTrue(EditorApp.gifExportSettings(
			"24", "2", "12", "0", "100", "0", true, true, "1", "128", false).frameCount() == 10,
			"GIF settings can omit the last frame");
		com.openspine.io.GifAnimationExporter.Settings infiniteGif = EditorApp.gifExportSettings(
			"30", "0", "10", "100", "0", false, true, "invalid", "invalid");
		assertTrue(infiniteGif.repeat() == 0 && infiniteGif.alphaThreshold()
			== com.openspine.io.GifAnimationExporter.DEFAULT_ALPHA_THRESHOLD,
			"disabled GIF fields do not reject opaque infinite-loop output");
		assertThrows(() -> EditorApp.gifExportSettings("101", "0", "10", "100", "0",
			true, true, "1", "128"), "GIF FPS above 100 is rejected");
		assertThrows(() -> EditorApp.gifExportSettings("30", "start", "10", "100", "0",
			true, true, "1", "128"), "non-numeric GIF frame ranges are rejected");
		com.openspine.io.ApngAnimationExporter.Settings apng = EditorApp.apngExportSettings(
			"24", "2", "12", "4", "150", "5", true, false, "3", "9");
		assertNear(24f, apng.fps(), "APNG FPS is parsed as a finite value");
		assertTrue(apng.startFrame() == 2 && apng.endFrame() == 12 && apng.frameCount() == 11,
			"APNG frame range remains inclusive");
		assertTrue(apng.warmUp() == 4 && apng.repeat() == 3 && apng.compression() == 9 && apng.transparent(),
			"APNG loop, compression, and transparency settings are preserved");
		assertTrue(EditorApp.apngExportSettings(
			"24", "2", "12", "0", "100", "0", true, true, "1", "6", false).frameCount() == 10,
			"APNG settings can omit the last frame");
		com.openspine.io.ApngAnimationExporter.Settings infiniteApng = EditorApp.apngExportSettings(
			"30", "0", "10", "100", "0", false, true, "invalid", "6");
		assertTrue(infiniteApng.repeat() == 0,
			"disabled APNG play-count field does not reject infinite-loop output");
		assertThrows(() -> EditorApp.apngExportSettings("101", "0", "10", "100", "0",
			true, true, "1", "6"), "APNG FPS above 100 is rejected");
		assertThrows(() -> EditorApp.apngExportSettings("30", "0", "10", "100", "0",
			true, true, "1", "10"), "APNG compression above nine is rejected");
		com.openspine.io.AviAnimationExporter.Settings avi = EditorApp.aviExportSettings(
			"24", "2", "12", "5", "150", "5", "92");
		assertNear(24f, avi.fps(), "AVI FPS is parsed as a finite value");
		assertTrue(avi.startFrame() == 2 && avi.endFrame() == 12 && avi.frameCount() == 11,
			"AVI frame range remains inclusive");
		assertTrue(avi.warmUp() == 5 && avi.quality() == 92 && avi.padding() == 5,
			"AVI quality and padding settings are preserved");
		assertTrue(EditorApp.aviExportSettings(
			"24", "2", "12", "0", "100", "0", "92", false).frameCount() == 10,
			"AVI settings can omit the last frame");
		assertThrows(() -> EditorApp.aviExportSettings("101", "0", "10", "100", "0", "90"),
			"AVI FPS above 100 is rejected");
		assertThrows(() -> EditorApp.aviExportSettings("30", "0", "10", "100", "0", "101"),
			"AVI quality above 100 is rejected");
		EditorApp.VideoExportOptions video = EditorApp.videoExportSettings(
			com.openspine.io.FfmpegAnimationExporter.Format.MOV, "30", "5", "20", "2",
			"125", "8", false, "#123456", com.openspine.io.FfmpegAnimationExporter.Profile.QUALITY,
			2, true, false);
		assertTrue(video.frames().startFrame() == 5 && video.frames().endFrame() == 20
			&& video.frames().frameCount() == 15, "video settings can omit the duplicated final frame");
		assertTrue(video.frames().warmUp() == 2 && video.frames().padding() == 8,
			"video warm-up and padding settings are preserved");
		assertTrue(video.backgroundRgba() == 0x123456ff && !video.frames().transparent(),
			"opaque video preserves its selected RGB background");
		assertTrue(video.encoding().maximumWidth() == 1920 && video.encoding().maximumHeight() == 1080,
			"video resolution presets become ffmpeg maximum dimensions");
		assertEquals(com.openspine.io.FfmpegAnimationExporter.Profile.QUALITY,
			EditorApp.videoProfile("高质量"), "video profile labels map to stable encoding profiles");
		EditorApp.VideoExportOptions alphaVideo = EditorApp.videoExportSettings(
			com.openspine.io.FfmpegAnimationExporter.Format.WEBM, "24", "0", "1", "0",
			"100", "0", true, "invalid", com.openspine.io.FfmpegAnimationExporter.Profile.BALANCED,
			0, false, true);
		assertTrue(alphaVideo.frames().transparent()
			&& alphaVideo.backgroundRgba() == com.openspine.io.PngFrameExporter.DEFAULT_BACKGROUND_RGBA,
			"transparent video does not require or apply an opaque background color");
		assertThrows(() -> EditorApp.videoExportSettings(
			com.openspine.io.FfmpegAnimationExporter.Format.WEBM, "24", "0", "1", "0",
			"100", "0", false, "#123456", com.openspine.io.FfmpegAnimationExporter.Profile.BALANCED,
			4, false, true), "unknown video resolution presets are rejected");
		assertEquals("saved.json", EditorApp.documentDisplayName("C:\\work\\saved.json", "source.skel"),
			"associated save name is shown");
		assertEquals("source.skel", EditorApp.documentDisplayName(null, "C:\\work\\source.skel"),
			"source name is shown before Save As");
		assertEquals("内置示例", EditorApp.documentDisplayName(null, "builtin:example"),
			"built-in document display name");
		assertEquals("* hero.json — OpenSpine 骨骼动画编辑器", EditorApp.windowTitle("hero.json", true),
			"dirty document title");
		assertEquals("hero.json — OpenSpine 骨骼动画编辑器", EditorApp.windowTitle("hero.json", false),
			"saved document title");
		com.badlogic.gdx.scenes.scene2d.Group windowControls = new com.badlogic.gdx.scenes.scene2d.Group();
		com.badlogic.gdx.scenes.scene2d.Actor windowControlIcon = new com.badlogic.gdx.scenes.scene2d.Actor();
		windowControls.addActor(windowControlIcon);
		assertTrue(EditorApp.isDescendantOf(windowControlIcon, windowControls),
			"window control children do not start title-bar drags");
		assertTrue(!EditorApp.isDescendantOf(new com.badlogic.gdx.scenes.scene2d.Actor(), windowControls),
			"unrelated title-bar content remains draggable");
		assertTrue(EditorApp.shouldLoadExampleAtStartup(null, false),
			"missing startup path loads the example");
		assertTrue(EditorApp.shouldLoadExampleAtStartup("   ", false),
			"blank startup path loads the example");
		assertTrue(EditorApp.shouldLoadExampleAtStartup("missing.json", false),
			"failed startup file falls back to the example");
		assertTrue(!EditorApp.shouldLoadExampleAtStartup("hero.json", true),
			"successfully loaded startup file is preserved");
		com.esotericsoftware.spine.SkeletonData previewData = new com.esotericsoftware.spine.SkeletonData();
		com.esotericsoftware.spine.BoneData previewRoot = new com.esotericsoftware.spine.BoneData(0, "root", null);
		previewData.getBones().add(previewRoot);
		com.esotericsoftware.spine.Skin previewDefault = new com.esotericsoftware.spine.Skin("default");
		com.esotericsoftware.spine.Skin attachmentOnly = new com.esotericsoftware.spine.Skin("alternate");
		previewData.getSkins().addAll(previewDefault, attachmentOnly);
		previewData.setDefaultSkin(previewDefault);
		assertEquals(previewDefault, EditorApp.initialPreviewSkin(previewData),
			"ordinary attachment skins do not replace the default preview skin");
		com.esotericsoftware.spine.Skin activateAll = new com.esotericsoftware.spine.Skin("all");
		activateAll.getBones().add(previewRoot);
		previewData.getSkins().add(activateAll);
		assertEquals(activateAll, EditorApp.initialPreviewSkin(previewData),
			"a skin that activates required content is selected for the initial preview");
		assertNear(EditorApp.DEFAULT_WORKSPACE_SPLIT, EditorApp.normalizeWorkspaceSplit(Float.NaN),
			"invalid workspace split restores the default");
		assertNear(EditorApp.MIN_WORKSPACE_SPLIT, EditorApp.normalizeWorkspaceSplit(0.1f),
			"workspace split is clamped to the usable minimum");
		assertNear(EditorApp.MAX_WORKSPACE_SPLIT, EditorApp.normalizeWorkspaceSplit(0.95f),
			"workspace split is clamped to the usable maximum");
		assertNear(0.7f, EditorApp.normalizeWorkspaceSplit(0.7f),
			"valid workspace split is preserved");
		assertNear(EditorApp.DEFAULT_NAVIGATION_SPLIT, EditorApp.normalizeNavigationSplit(Float.POSITIVE_INFINITY),
			"invalid navigation split restores the default");
		assertNear(EditorApp.MIN_NAVIGATION_SPLIT, EditorApp.normalizeNavigationSplit(0f),
			"navigation split is clamped to the usable minimum");
		assertNear(EditorApp.MAX_NAVIGATION_SPLIT, EditorApp.normalizeNavigationSplit(1f),
			"navigation split is clamped to the usable maximum");
		assertNear(0.2f, EditorApp.normalizeNavigationSplit(0.2f),
			"valid navigation split is preserved");
		assertNear(EditorApp.DEFAULT_INSPECTOR_SPLIT, EditorApp.normalizeInspectorSplit(Float.NEGATIVE_INFINITY),
			"invalid inspector split restores the default");
		assertNear(EditorApp.MIN_INSPECTOR_SPLIT, EditorApp.normalizeInspectorSplit(0f),
			"inspector split is clamped to the usable minimum");
		assertNear(EditorApp.MAX_INSPECTOR_SPLIT, EditorApp.normalizeInspectorSplit(1f),
			"inspector split is clamped to the usable maximum");
		assertNear(0.7f, EditorApp.normalizeInspectorSplit(0.7f),
			"valid inspector split is preserved");
		assertTrue(EditorApp.anyWorkspacePanelVisible(true, false, false),
			"a visible hierarchy keeps the collapse-all action available");
		assertTrue(EditorApp.anyWorkspacePanelVisible(false, true, false),
			"a visible inspector keeps the collapse-all action available");
		assertTrue(EditorApp.anyWorkspacePanelVisible(false, false, true),
			"a visible timeline keeps the collapse-all action available");
		assertTrue(!EditorApp.anyWorkspacePanelVisible(false, false, false),
			"a fully collapsed layout is detected for the expand action");
		assertEquals("收起", EditorApp.workspaceCollapseButtonLabel(false),
			"the workspace toggle initially offers to collapse panels");
		assertEquals("放出", EditorApp.workspaceCollapseButtonLabel(true),
			"the workspace toggle offers to restore collapsed panels");
		assertEquals(com.badlogic.gdx.graphics.Cursor.SystemCursor.VerticalResize,
			EditorApp.workspaceCursor(false, true, false, false),
			"timeline handles use the vertical resize cursor");
		assertEquals(com.badlogic.gdx.graphics.Cursor.SystemCursor.HorizontalResize,
			EditorApp.workspaceCursor(false, false, true, false),
			"side-panel handles use the horizontal resize cursor");
		assertEquals(com.badlogic.gdx.graphics.Cursor.SystemCursor.Arrow,
			EditorApp.workspaceCursor(true, true, true, true),
			"menus and dialogs suppress resize cursors");
		System.out.println("ALL EDITOR APP TESTS PASSED");
	}

	private static void testsAtlasOutputDirectory() throws Exception {
		java.nio.file.Path root = java.nio.file.Files.createTempDirectory("openspine-atlas-destination-");
		java.nio.file.Path source = java.nio.file.Files.createDirectory(root.resolve("source"));
		java.nio.file.Path layers = java.nio.file.Files.createDirectory(source.resolve("hero_layers"));
		java.nio.file.Path saved = java.nio.file.Files.createDirectory(root.resolve("saved"));
		try {
			String image = layers.resolve("body.png").toString();
			java.nio.file.Path psd = source.resolve("hero.psd");
			assertEquals(source.toFile(), EditorApp.atlasOutputDirectory(null, image, psd),
				"first PSD atlas output is outside the temporary extracted layers");
			assertEquals(source.toFile(), EditorApp.atlasOutputDirectory(" ", image, psd),
				"blank saved directory uses the original PSD location");
			assertEquals(saved.toFile(), EditorApp.atlasOutputDirectory(saved.toString(), image, psd),
				"an existing user-selected save directory keeps priority");
			assertEquals(layers.toFile(), EditorApp.atlasOutputDirectory(null, image, null),
				"ordinary image packing still suggests the source image directory");
			assertEquals(source.toFile(), EditorApp.atlasOutputDirectory(
				root.resolve("missing/subdirectory").toString(), image, psd),
				"a missing saved location falls back to the PSD directory");
		} finally {
			java.nio.file.Files.deleteIfExists(saved);
			java.nio.file.Files.deleteIfExists(layers);
			java.nio.file.Files.deleteIfExists(source);
			java.nio.file.Files.deleteIfExists(root);
		}
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (RuntimeException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private EditorAppTest() {}
}
