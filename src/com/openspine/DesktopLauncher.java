package com.openspine;

import java.awt.GraphicsEnvironment;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.BiConsumer;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.openspine.io.JpegAnimationExporter;
import com.openspine.io.PngAnimationExporter;

/** Desktop file chooser helpers. Windows open dialogs use the native Explorer-style picker. */
public class DesktopLauncher {
	private static final List<WindowsFileDialog.Filter> OPEN_FILTERS = List.of(
		new WindowsFileDialog.Filter("全部 Spine 文件", "*.spine;*.json;*.skel"),
		new WindowsFileDialog.Filter("Spine 编辑器工程", "*.spine"),
		new WindowsFileDialog.Filter("Spine 骨架 JSON", "*.json"),
		new WindowsFileDialog.Filter("Spine 二进制骨架", "*.skel"));
	private static final List<WindowsFileDialog.Filter> PROJECT_OPEN_FILTERS = List.of(
		new WindowsFileDialog.Filter("Spine 编辑器工程", "*.spine"));
	private static final List<WindowsFileDialog.Filter> ANIMATION_OPEN_FILTERS = List.of(
		new WindowsFileDialog.Filter("Spine Runtime 动画数据", "*.json;*.skel"),
		new WindowsFileDialog.Filter("Spine 骨架 JSON", "*.json"),
		new WindowsFileDialog.Filter("Spine 二进制骨架", "*.skel"));
	private static final List<WindowsFileDialog.Filter> PSD_OPEN_FILTERS = List.of(
		new WindowsFileDialog.Filter("Photoshop 文档", "*.psd"));

	public record SequenceFileSelection(String path, boolean replaceExisting) {
		public SequenceFileSelection {
			if (path == null || path.isBlank())
				throw new IllegalArgumentException("Sequence path is required.");
		}
	}

	@FunctionalInterface
	private interface SequenceTargetResolver {
		List<Path> resolve(Path requestedTarget);
	}

	/** 打开文件对话框选择 .spine/.json/.skel 文件；取消返回 null */
	public static String chooseFile() {
		return chooseFile(null);
	}

	/** Opens the skeleton chooser at the most recently used directory when available. */
	public static String chooseFile(String initialDirectory) {
		return onEventDispatchThread(() -> chooseFileOnEdt(initialDirectory));
	}

	/** Opens the skeleton chooser without blocking the caller (notably the LibGDX render thread). */
	public static void chooseFileAsync(String initialDirectory, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseFileOnEdt(initialDirectory, false), completion);
	}

	/** Opens a runtime-data chooser for importing animations into the current skeleton. */
	public static void chooseAnimationFileAsync(String initialDirectory,
		BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseAnimationFileOnEdt(initialDirectory), completion);
	}

	/** Opens a multi-select raster-image chooser for the atlas import workflow. */
	public static void chooseImageFilesAsync(String initialDirectory, BiConsumer<List<String>, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFilesOnEdt(initialDirectory), completion);
	}

	/** Opens a single PSD chooser for importing layered artwork. */
	public static void choosePsdImportFileAsync(String initialDirectory,
		BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> choosePsdImportFileOnEdt(initialDirectory), completion);
	}

	/** @deprecated use {@link #chooseImageFilesAsync(String, BiConsumer)}. */
	@Deprecated
	public static void choosePngFilesAsync(String initialDirectory, BiConsumer<List<String>, Throwable> completion) {
		chooseImageFilesAsync(initialDirectory, completion);
	}

	public static void chooseAtlasSaveFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseAtlasSaveFileOnEdt(suggestedPath), completion);
	}

	private static String chooseAtlasSaveFileOnEdt(String suggestedPath) {
		if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("图集对话框不可用");
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("保存 Spine 图集");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("Spine 图集 (*.atlas)", "atlas"));
		if (suggestedPath != null && !suggestedPath.isBlank()) chooser.setSelectedFile(new File(suggestedPath));
		if (showSwingSaveDialog(chooser) != JFileChooser.APPROVE_OPTION) return null;
		File selected = chooser.getSelectedFile();
		if (selected == null) return null;
		String path = selected.getAbsolutePath();
		String targetPath = path.toLowerCase(Locale.ROOT).endsWith(".atlas") ? path : path + ".atlas";
		File target = new File(targetPath);
		if (target.exists()) {
			int result = showSwingConfirmDialog(
				"文件已存在，是否覆盖？\n" + target.getAbsolutePath(), "确认覆盖",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION) return null;
		}
		return target.getAbsolutePath();
	}

	private static List<String> chooseImageFilesOnEdt(String initialDirectory) {
		if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("图片对话框不可用");
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("导入图片并打包图集");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setMultiSelectionEnabled(true);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("图片 (*.png, *.jpg, *.jpeg)", "png", "jpg", "jpeg"));
		File dir = existingDirectory(initialDirectory, defaultDirectory());
		if (dir != null) chooser.setCurrentDirectory(dir);
		if (showSwingOpenDialog(chooser) != JFileChooser.APPROVE_OPTION) return List.of();
		File[] selected = chooser.getSelectedFiles();
		ArrayList<String> result = new ArrayList<>();
		for (File file : selected) if (file != null && file.isFile()
			&& acceptsImageFileName(file.getName())) result.add(file.getAbsolutePath());
		return List.copyOf(result);
	}

	static boolean acceptsImageFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) return false;
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
	}

	private static String choosePsdImportFileOnEdt(String initialDirectory) {
		if (useNativeWindowsOpenDialog()) {
			File directory = existingDirectory(initialDirectory, defaultDirectory());
			while (true) {
				WindowsFileDialog.Selection selection = WindowsFileDialog.open("导入分层 PSD",
					directory == null ? null : directory.toPath(), PSD_OPEN_FILTERS, 1);
				if (selection == null) return null;
				File selected = new File(selection.path()).getAbsoluteFile();
				if (acceptsPsdFileName(selected.getName())) return selected.getAbsolutePath();
				WindowsFileDialog.warning("不支持的文件类型", "请选择 .psd 文件。");
				File parent = selected.getParentFile();
				if (parent != null && parent.isDirectory()) directory = parent;
			}
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("导入分层 PSD");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("Photoshop 文档 (*.psd)", "psd"));
		File directory = existingDirectory(initialDirectory, defaultDirectory());
		if (directory != null) chooser.setCurrentDirectory(directory);
		if (showSwingOpenDialog(chooser) != JFileChooser.APPROVE_OPTION) return null;
		File selected = chooser.getSelectedFile();
		return selected != null && acceptsPsdFileName(selected.getName()) ? selected.getAbsolutePath() : null;
	}

	static boolean acceptsPsdFileName(String fileName) {
		return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".psd");
	}

	private static String chooseFileOnEdt(String initialDirectory) {
		return chooseFileOnEdt(initialDirectory, false);
	}

	/** Opens a chooser restricted to Spine editor projects for the Import Project command. */
	public static void chooseSpineProjectAsync(String initialDirectory,
		BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseFileOnEdt(initialDirectory, true), completion);
	}

	private static String chooseFileOnEdt(String initialDirectory, boolean projectOnly) {
		if (useNativeWindowsOpenDialog()) return chooseFileWithWindowsDialog(initialDirectory, projectOnly);
		return chooseFileWithSwingDialog(initialDirectory, projectOnly);
	}

	private static String chooseAnimationFileOnEdt(String initialDirectory) {
		if (useNativeWindowsOpenDialog()) {
			File directory = existingDirectory(initialDirectory, defaultDirectory());
			while (true) {
				WindowsFileDialog.Selection selection = WindowsFileDialog.open("导入动画 (.json / .skel)",
					directory == null ? null : directory.toPath(), ANIMATION_OPEN_FILTERS, 1);
				if (selection == null) return null;
				File selected = new File(selection.path()).getAbsoluteFile();
				if (acceptsAnimationFileName(selected.getName())) return selected.getAbsolutePath();
				WindowsFileDialog.warning("不支持的文件类型", "请选择 .json 或 .skel Runtime 数据。");
				File parent = selected.getParentFile();
				if (parent != null && parent.isDirectory()) directory = parent;
			}
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("导入动画 (.json / .skel)");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("Spine Runtime 动画数据 (*.json, *.skel)",
			"json", "skel"));
		File directory = existingDirectory(initialDirectory, defaultDirectory());
		if (directory != null) chooser.setCurrentDirectory(directory);
		if (showSwingOpenDialog(chooser) != JFileChooser.APPROVE_OPTION) return null;
		File selected = chooser.getSelectedFile();
		return selected == null ? null : selected.getAbsolutePath();
	}

	static boolean acceptsAnimationFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) return false;
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.endsWith(".json") || lower.endsWith(".skel");
	}

	private static String chooseFileWithWindowsDialog(String initialDirectory, boolean projectOnly) {
		String title = projectOnly ? "导入 Spine 工程 (.spine)"
			: "打开 Spine 工程或骨架 (.spine / .json / .skel)";
		File directory = existingDirectory(initialDirectory, defaultDirectory());
		List<WindowsFileDialog.Filter> filters = projectOnly ? PROJECT_OPEN_FILTERS : OPEN_FILTERS;
		while (true) {
			WindowsFileDialog.Selection selection = WindowsFileDialog.open(title,
				directory == null ? null : directory.toPath(), filters, 1);
			if (selection == null) return null;
			File selected = new File(selection.path()).getAbsoluteFile();
			if (acceptsOpenFileName(selected.getName(), projectOnly)) return selected.getAbsolutePath();
			WindowsFileDialog.warning("不支持的文件类型",
				projectOnly ? "请选择 .spine 工程文件。" : "请选择 .spine、.json 或 .skel 文件。");
			File parent = selected.getParentFile();
			if (parent != null && parent.isDirectory()) directory = parent;
		}
	}

	private static String chooseFileWithSwingDialog(String initialDirectory, boolean projectOnly) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(projectOnly ? "导入 Spine 工程 (.spine)"
			: "打开 Spine 工程或骨架 (.spine / .json / .skel)");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(!projectOnly);
		FileNameExtensionFilter projectFilter = new FileNameExtensionFilter("Spine 编辑器工程 (*.spine)", "spine");
		FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("Spine 骨架 JSON (*.json)", "json");
		FileNameExtensionFilter skelFilter = new FileNameExtensionFilter("Spine 二进制骨架 (*.skel)", "skel");
		chooser.addChoosableFileFilter(projectFilter);
		if (projectOnly) {
			chooser.setFileFilter(projectFilter);
		} else {
			FileNameExtensionFilter allFilter = new FileNameExtensionFilter(
				"全部 Spine 文件 (*.spine, *.json, *.skel)", "spine", "json", "skel");
			chooser.addChoosableFileFilter(allFilter);
			chooser.addChoosableFileFilter(jsonFilter);
			chooser.addChoosableFileFilter(skelFilter);
			chooser.setFileFilter(allFilter);
		}
		File dir = existingDirectory(initialDirectory, defaultDirectory());
		if (dir != null) chooser.setCurrentDirectory(dir);
		int result = showSwingOpenDialog(chooser);
		if (result == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			return f == null ? null : f.getAbsolutePath();
		}
		return null;
	}

	static boolean acceptsOpenFileName(String fileName, boolean projectOnly) {
		if (fileName == null || fileName.isBlank()) return false;
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.endsWith(".spine") || !projectOnly && (lower.endsWith(".json") || lower.endsWith(".skel"));
	}

	static boolean useNativeWindowsOpenDialog() {
		return !GraphicsEnvironment.isHeadless() && WindowsFileDialog.available()
			&& WindowsFileDialog.apiAvailable();
	}

	/** Associates all native path dialogs with the GLFW editor window. */
	static boolean installMainWindow(long glfwWindowHandle) {
		return WindowsFileDialog.installOwner(glfwWindowHandle);
	}

	/** Save As dialog; returns null when cancelled or an overwrite is declined. */
	public static String chooseSaveFile(String suggestedPath) {
		return onEventDispatchThread(() -> chooseSaveFileOnEdt(suggestedPath));
	}

	/** Opens the skeleton save chooser without blocking the LibGDX render thread. */
	public static void chooseSaveFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseSaveFileOnEdt(suggestedPath), completion);
	}

	/** Saves the complete OpenSpine project, independently of runtime skeleton exports. */
	public static void chooseNativeProjectFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseNativeProjectFileOnEdt(suggestedPath), completion);
	}

	static String ensureNativeProjectExtension(String path) {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("工程路径不能为空");
		File file = new File(path);
		if (com.openspine.io.ProjectPackageManifest.isManifestFileName(file.getName())) return path;
		return path + ".openspine-project.json";
	}

	private static String chooseNativeProjectFileOnEdt(String suggestedPath) {
		if (useNativeWindowsOpenDialog()) {
			WindowsFileDialog.Selection selected = showWindowsSave("保存 OpenSpine 工程", absoluteSuggestion(suggestedPath),
				List.of(new WindowsFileDialog.Filter("OpenSpine 工程", "*.openspine-project.json;openspine-project.json")), 1);
			if (selected == null) return null;
			String path = ensureNativeProjectExtension(selected.path());
			return confirmWindowsOverwrite(new File(path), "工程已存在，是否用当前完整工程替换？") ? path : null;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("保存 OpenSpine 工程");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("OpenSpine 工程 (*.openspine-project.json)", "json"));
		File directory = existingDirectory(suggestedPath, defaultDirectory());
		if (directory != null) chooser.setCurrentDirectory(directory);
		if (suggestedPath != null && !suggestedPath.isBlank()) chooser.setSelectedFile(new File(suggestedPath));
		if (showSwingSaveDialog(chooser) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return null;
		File target = new File(ensureNativeProjectExtension(chooser.getSelectedFile().getAbsolutePath()));
		if (target.exists() && showSwingConfirmDialog("工程已存在，是否用当前完整工程替换？\n" + target,
			"确认覆盖", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return null;
		return target.getAbsolutePath();
	}

	private static String chooseSaveFileOnEdt(String suggestedPath) {
		if (useNativeWindowsOpenDialog()) return chooseSaveFileWithWindowsDialog(suggestedPath);
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("另存 Spine 骨架 JSON");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(true);
		FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("Spine 骨架 JSON (*.json)", "json");
		FileNameExtensionFilter skelFilter = new FileNameExtensionFilter("Spine binary skeleton (*.skel)", "skel");
		chooser.setDialogTitle("另存 Spine 骨架 (.json / .skel)");
		chooser.addChoosableFileFilter(jsonFilter);
		chooser.addChoosableFileFilter(skelFilter);
		File suggested = suggestedPath == null || suggestedPath.isBlank()
			? null : new File(suggestedPath).getAbsoluteFile();
		chooser.setFileFilter(suggested != null
			&& suggested.getName().toLowerCase(Locale.ROOT).endsWith(".skel") ? skelFilter : jsonFilter);
		File directory = existingDirectory(suggestedPath, defaultDirectory());
		if (directory != null) chooser.setCurrentDirectory(directory);
		if (suggested != null) {
			chooser.setSelectedFile(suggested);
		}
		if (showSwingSaveDialog(chooser) == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			if (f == null) return null;
			String extension = chooser.getFileFilter() == skelFilter ? ".skel" : ".json";
			String p = ensureSaveExtension(f.getAbsolutePath(), extension);
			File target = new File(p);
			if (target.exists()) {
				int result = showSwingConfirmDialog(
					"文件已存在，是否覆盖？\n" + target.getAbsolutePath(), "确认覆盖",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (result != JOptionPane.YES_OPTION) return null;
			}
			return p;
		}
		return null;
	}

	private static String chooseSaveFileWithWindowsDialog(String suggestedPath) {
		List<WindowsFileDialog.Filter> filters = List.of(
			new WindowsFileDialog.Filter("Spine 骨架 JSON", "*.json"),
			new WindowsFileDialog.Filter("Spine 二进制骨架", "*.skel"));
		File suggested = absoluteSuggestion(suggestedPath);
		int initialFilter = suggested != null
			&& suggested.getName().toLowerCase(Locale.ROOT).endsWith(".skel") ? 2 : 1;
		WindowsFileDialog.Selection selection = showWindowsSave("另存 Spine 骨架 (.json / .skel)",
			suggested, filters, initialFilter);
		if (selection == null) return null;
		String extension = selection.filterIndex() == 2 ? ".skel" : ".json";
		String path = ensureSaveExtension(selection.path(), extension);
		return confirmWindowsOverwrite(new File(path), "文件已存在，是否覆盖？") ? path : null;
	}

	/** Chooses the destination ZIP used by the project packager. */
	public static String choosePackageFile(String suggestedPath) {
		return onEventDispatchThread(() -> choosePackageFileOnEdt(suggestedPath));
	}

	/** Opens the package destination chooser without blocking the LibGDX render thread. */
	public static void choosePackageFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> choosePackageFileOnEdt(suggestedPath), completion);
	}

	/** Opens the destination chooser for rebuilding an editable Spine project. */
	public static void chooseSpineProjectSaveFileAsync(String suggestedPath,
		BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseSpineProjectSaveFileOnEdt(suggestedPath), completion);
	}

	private static String chooseSpineProjectSaveFileOnEdt(String suggestedPath) {
		if (useNativeWindowsOpenDialog()) {
			File suggested = absoluteSuggestion(suggestedPath);
			WindowsFileDialog.Selection selection = showWindowsSave("重建 Spine 工程", suggested,
				List.of(new WindowsFileDialog.Filter("Spine 编辑器工程", "*.spine")), 1);
			if (selection == null) return null;
			String path = ensureSpineProjectExtension(selection.path());
			return confirmWindowsOverwrite(new File(path), "将用重建的 Spine 工程覆盖现有文件，是否继续？")
				? path : null;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("重建 Spine 工程");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("Spine 编辑器工程 (*.spine)", "spine"));
		File suggested = suggestedPath == null || suggestedPath.isBlank()
			? null : new File(suggestedPath).getAbsoluteFile();
		File directory = existingDirectory(suggestedPath, defaultDirectory());
		if (directory != null) chooser.setCurrentDirectory(directory);
		if (suggested != null) chooser.setSelectedFile(suggested);
		if (showSwingSaveDialog(chooser) != JFileChooser.APPROVE_OPTION) return null;
		File selected = chooser.getSelectedFile();
		if (selected == null) return null;
		File target = new File(ensureSpineProjectExtension(selected.getAbsolutePath()));
		if (target.exists()) {
			int result = showSwingConfirmDialog(
				"将用重建的 Spine 工程覆盖现有文件，是否继续？\n" + target.getAbsolutePath(), "确认覆盖",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION) return null;
		}
		return target.getAbsolutePath();
	}

	private static String choosePackageFileOnEdt(String suggestedPath) {
		if (useNativeWindowsOpenDialog()) {
			File suggested = absoluteSuggestion(suggestedPath);
			WindowsFileDialog.Selection selection = showWindowsSave("打包 OpenSpine 项目", suggested,
				List.of(new WindowsFileDialog.Filter("项目压缩包", "*.zip")), 1);
			if (selection == null) return null;
			String path = ensurePackageExtension(selection.path());
			return confirmWindowsOverwrite(new File(path), "文件已存在，是否覆盖？") ? path : null;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("打包 OpenSpine 项目");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		FileNameExtensionFilter zipFilter = new FileNameExtensionFilter("项目压缩包 (*.zip)", "zip");
		chooser.setFileFilter(zipFilter);
		File suggested = suggestedPath == null || suggestedPath.isBlank()
			? null : new File(suggestedPath).getAbsoluteFile();
		File directory = existingDirectory(suggestedPath, defaultDirectory());
		if (directory != null) chooser.setCurrentDirectory(directory);
		if (suggested != null) chooser.setSelectedFile(suggested);
		if (showSwingSaveDialog(chooser) != JFileChooser.APPROVE_OPTION) return null;
		File selected = chooser.getSelectedFile();
		if (selected == null) return null;
		File target = new File(ensurePackageExtension(selected.getAbsolutePath()));
		if (target.exists()) {
			int result = showSwingConfirmDialog(
				"文件已存在，是否覆盖？\n" + target.getAbsolutePath(), "确认覆盖",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION) return null;
		}
		return target.getAbsolutePath();
	}

	/** Opens the PNG destination chooser without blocking the LibGDX render thread. */
	public static void choosePngFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出当前帧 PNG",
			"PNG 图片 (*.png)", ".png", "png"), completion);
	}

	/** Opens the JPEG destination chooser without blocking the LibGDX render thread. */
	public static void chooseJpegFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出当前帧 JPEG",
			"JPEG 图片 (*.jpg / *.jpeg)", ".jpg", "jpg", "jpeg"), completion);
	}

	public static void chooseWebpFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出当前帧 WebP",
			"WebP 图片 (*.webp)", ".webp", "webp"), completion);
	}

	public static void chooseAnimatedWebpFileAsync(String suggestedPath,
		BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出 AWEBP 动画",
			"动画 WebP (*.webp)", ".webp", "webp"), completion);
	}

	public static void choosePsdFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出扁平 PSD 当前帧",
			"扁平 PSD 图片 (*.psd)", ".psd", "psd"), completion);
	}

	public static void chooseHtmlFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出 HTML 数据查看页",
			"HTML 文件 (*.html)", ".html", "html"), completion);
	}

	public static void chooseWebmFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出 WebM 动画",
			"WebM 视频 (*.webm)", ".webm", "webm"), completion);
	}

	public static void chooseMovFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出 MOV 动画",
			"MOV 视频 (*.mov)", ".mov", "mov"), completion);
	}

	/** Chooses a PNG sequence prefix and confirms replacement for the complete generated file set. */
	public static void choosePngSequenceFileAsync(String suggestedPath, int startFrame, int endFrame,
		BiConsumer<SequenceFileSelection, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageSequenceFileOnEdt(suggestedPath,
			"导出 PNG 动画序列", "PNG 序列 (*.png)", ".png",
			target -> PngAnimationExporter.sequenceTargets(target, startFrame, endFrame), "png"), completion);
	}

	/** Chooses a JPEG sequence prefix and confirms replacement for the complete generated file set. */
	public static void chooseJpegSequenceFileAsync(String suggestedPath, int startFrame, int endFrame,
		BiConsumer<SequenceFileSelection, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageSequenceFileOnEdt(suggestedPath,
			"导出 JPEG 动画序列", "JPEG 序列 (*.jpg / *.jpeg)", ".jpg",
			target -> JpegAnimationExporter.sequenceTargets(target, startFrame, endFrame), "jpg", "jpeg"),
			completion);
	}

	/** Opens the animated GIF destination chooser without blocking the LibGDX render thread. */
	public static void chooseGifFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出 GIF 动画",
			"GIF 动画 (*.gif)", ".gif", "gif"), completion);
	}

	/** Opens the animated PNG destination chooser without blocking the LibGDX render thread. */
	public static void chooseApngFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出 APNG 动画",
			"APNG 动画 (*.apng / *.png)", ".apng", "apng", "png"), completion);
	}

	/** Opens the MJPEG AVI destination chooser without blocking the LibGDX render thread. */
	public static void chooseAviFileAsync(String suggestedPath, BiConsumer<String, Throwable> completion) {
		onEventDispatchThreadAsync(() -> chooseImageFileOnEdt(suggestedPath, "导出 AVI 动画",
			"MJPEG AVI 视频 (*.avi)", ".avi", "avi"), completion);
	}

	private static String chooseImageFileOnEdt(String suggestedPath, String title, String filterDescription,
		String defaultExtension, String... acceptedExtensions) {
		if (useNativeWindowsOpenDialog()) {
			File suggested = absoluteSuggestion(suggestedPath);
			String pattern = extensionPattern(acceptedExtensions);
			WindowsFileDialog.Selection selection = showWindowsSave(title, suggested,
				List.of(new WindowsFileDialog.Filter(filterDescriptionWithoutPattern(filterDescription), pattern)), 1);
			if (selection == null) return null;
			String path = ensureImageExtension(selection.path(), defaultExtension, acceptedExtensions);
			return confirmWindowsOverwrite(new File(path), "文件已存在，是否覆盖？") ? path : null;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter(filterDescription, acceptedExtensions));
		File suggested = suggestedPath == null || suggestedPath.isBlank()
			? null : new File(suggestedPath).getAbsoluteFile();
		File directory = existingDirectory(suggestedPath, defaultDirectory());
		if (directory != null) chooser.setCurrentDirectory(directory);
		if (suggested != null) chooser.setSelectedFile(suggested);
		if (showSwingSaveDialog(chooser) != JFileChooser.APPROVE_OPTION) return null;
		File selected = chooser.getSelectedFile();
		if (selected == null) return null;
		File target = new File(ensureImageExtension(selected.getAbsolutePath(), defaultExtension,
			acceptedExtensions));
		if (target.exists()) {
			int result = showSwingConfirmDialog(
				"文件已存在，是否覆盖？\n" + target.getAbsolutePath(), "确认覆盖",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION) return null;
		}
		return target.getAbsolutePath();
	}

	private static SequenceFileSelection chooseImageSequenceFileOnEdt(String suggestedPath, String title,
		String filterDescription, String defaultExtension, SequenceTargetResolver targetResolver,
		String... acceptedExtensions) {
		if (useNativeWindowsOpenDialog()) {
			File suggested = absoluteSuggestion(suggestedPath);
			WindowsFileDialog.Selection selection = showWindowsSave(title, suggested,
				List.of(new WindowsFileDialog.Filter(filterDescriptionWithoutPattern(filterDescription),
					extensionPattern(acceptedExtensions))), 1);
			if (selection == null) return null;
			Path target = Path.of(ensureImageExtension(selection.path(), defaultExtension, acceptedExtensions));
			List<Path> targets = targetResolver.resolve(target);
			int existing = 0;
			for (Path generated : targets)
				if (java.nio.file.Files.exists(generated, java.nio.file.LinkOption.NOFOLLOW_LINKS)) existing++;
			if (existing == 0) return new SequenceFileSelection(target.toString(), false);
			Path first = targets.get(0).getFileName();
			Path last = targets.get(targets.size() - 1).getFileName();
			boolean replace = WindowsFileDialog.confirm("确认覆盖序列",
				"序列中已有 " + existing + " 个文件，是否覆盖？\n" + first + " 至 " + last);
			return replace ? new SequenceFileSelection(target.toString(), true) : null;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter(filterDescription, acceptedExtensions));
		File suggested = suggestedPath == null || suggestedPath.isBlank()
			? null : new File(suggestedPath).getAbsoluteFile();
		File directory = existingDirectory(suggestedPath, defaultDirectory());
		if (directory != null) chooser.setCurrentDirectory(directory);
		if (suggested != null) chooser.setSelectedFile(suggested);
		if (showSwingSaveDialog(chooser) != JFileChooser.APPROVE_OPTION) return null;
		File selected = chooser.getSelectedFile();
		if (selected == null) return null;
		File target = new File(ensureImageExtension(selected.getAbsolutePath(), defaultExtension,
			acceptedExtensions));
		List<Path> targets = targetResolver.resolve(target.toPath());
		int existing = 0;
		for (Path generated : targets)
			if (java.nio.file.Files.exists(generated, java.nio.file.LinkOption.NOFOLLOW_LINKS)) existing++;
		if (existing == 0) return new SequenceFileSelection(target.getAbsolutePath(), false);
		Path first = targets.get(0).getFileName();
		Path last = targets.get(targets.size() - 1).getFileName();
		int result = showSwingConfirmDialog(
			"序列中已有 " + existing + " 个文件，是否覆盖？\n" + first + " 至 " + last,
			"确认覆盖序列", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (result != JOptionPane.YES_OPTION) return null;
		return new SequenceFileSelection(target.getAbsolutePath(), true);
	}

	private static WindowsFileDialog.Selection showWindowsSave(String title, File suggested,
		List<WindowsFileDialog.Filter> filters, int initialFilter) {
		File directory = existingDirectory(suggested == null ? null : suggested.getPath(), defaultDirectory());
		return WindowsFileDialog.save(title, directory == null ? null : directory.toPath(),
			suggested == null ? null : suggested.getName(), filters, initialFilter);
	}

	private static File absoluteSuggestion(String suggestedPath) {
		return suggestedPath == null || suggestedPath.isBlank()
			? null : new File(suggestedPath).getAbsoluteFile();
	}

	private static boolean confirmWindowsOverwrite(File target, String prompt) {
		return !target.exists() || WindowsFileDialog.confirm("确认覆盖",
			prompt + "\n" + target.getAbsolutePath());
	}

	static String extensionPattern(String... extensions) {
		if (extensions == null || extensions.length == 0)
			throw new IllegalArgumentException("At least one extension is required.");
		StringBuilder pattern = new StringBuilder();
		for (String extension : extensions) {
			if (extension == null || extension.isBlank())
				throw new IllegalArgumentException("File extensions cannot be blank.");
			if (pattern.length() > 0) pattern.append(';');
			pattern.append("*.").append(extension.toLowerCase(Locale.ROOT));
		}
		return pattern.toString();
	}

	static String filterDescriptionWithoutPattern(String description) {
		if (description == null || description.isBlank()) return "文件";
		int pattern = description.indexOf(" (*.");
		return pattern <= 0 ? description : description.substring(0, pattern);
	}

	static String ensureSaveExtension(String path, String selectedExtension) {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("Save path is required.");
		String lower = path.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".json") || lower.endsWith(".skel")) return path;
		return path + (".skel".equalsIgnoreCase(selectedExtension) ? ".skel" : ".json");
	}

	static String ensurePackageExtension(String path) {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("Package path is required.");
		return path.toLowerCase(Locale.ROOT).endsWith(".zip") ? path : path + ".zip";
	}

	static String ensureSpineProjectExtension(String path) {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("Spine project path is required.");
		return path.toLowerCase(Locale.ROOT).endsWith(".spine") ? path : path + ".spine";
	}

	static String ensurePngExtension(String path) {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("PNG path is required.");
		return ensureImageExtension(path, ".png", "png");
	}

	static String ensureJpegExtension(String path) {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("JPEG path is required.");
		return ensureImageExtension(path, ".jpg", "jpg", "jpeg");
	}

	static String ensureGifExtension(String path) {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("GIF path is required.");
		return ensureImageExtension(path, ".gif", "gif");
	}

	static String ensureApngExtension(String path) {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("APNG path is required.");
		return ensureImageExtension(path, ".apng", "apng", "png");
	}

	static String ensureAviExtension(String path) {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("AVI path is required.");
		return ensureImageExtension(path, ".avi", "avi");
	}

	private static String ensureImageExtension(String path, String defaultExtension,
		String... acceptedExtensions) {
		String lower = path.toLowerCase(Locale.ROOT);
		for (String extension : acceptedExtensions)
			if (lower.endsWith("." + extension.toLowerCase(Locale.ROOT))) return path;
		return path + defaultExtension;
	}

	static File existingDirectory(String path, File fallback) {
		if (path != null && !path.isBlank()) {
			File candidate = new File(path).getAbsoluteFile();
			File directory = candidate.isDirectory() ? candidate : candidate.getParentFile();
			if (directory != null && directory.isDirectory()) return directory;
		}
		if (fallback == null) return null;
		File directory = fallback.getAbsoluteFile();
		return directory.isDirectory() ? directory : null;
	}

	static File defaultDirectory() {
		File assets = new File("assets").getAbsoluteFile();
		if (assets.isDirectory()) return assets;
		String homePath = System.getProperty("user.home", "");
		if (!homePath.isBlank()) {
			File home = new File(homePath).getAbsoluteFile();
			File documents = new File(home, "Documents");
			if (documents.isDirectory()) return documents;
			if (home.isDirectory()) return home;
		}
		return null;
	}

	private static int showSwingOpenDialog(JFileChooser chooser) {
		try (SwingDialogOwner owner = new SwingDialogOwner()) {
			return chooser.showOpenDialog(owner.component());
		}
	}

	private static int showSwingSaveDialog(JFileChooser chooser) {
		try (SwingDialogOwner owner = new SwingDialogOwner()) {
			return chooser.showSaveDialog(owner.component());
		}
	}

	private static int showSwingConfirmDialog(Object message, String title, int optionType, int messageType) {
		try (SwingDialogOwner owner = new SwingDialogOwner()) {
			return JOptionPane.showConfirmDialog(owner.component(), message, title, optionType, messageType);
		}
	}

	private static final class SwingDialogOwner implements AutoCloseable {
		private final Frame frame;

		SwingDialogOwner() {
			if (GraphicsEnvironment.isHeadless())
				throw new IllegalStateException("Desktop dialogs are unavailable in a headless environment.");
			frame = new Frame("OpenSpine-dialog-owner-" + Long.toUnsignedString(System.nanoTime()));
			frame.setUndecorated(true);
			frame.setType(Window.Type.UTILITY);
			frame.setSize(1, 1);
			frame.setLocationRelativeTo(null);
			frame.addNotify();
			if (!WindowsFileDialog.attachSwingOwner(frame.getTitle())
				&& Toolkit.getDefaultToolkit().isAlwaysOnTopSupported()) {
				try {
					frame.setAlwaysOnTop(true);
				} catch (SecurityException ignored) {
					// A non-null owned Swing dialog still avoids a taskbar-level orphan window.
				}
			}
		}

		Frame component() {
			return frame;
		}

		@Override public void close() {
			frame.dispose();
		}
	}

	static <T> T onEventDispatchThread(Callable<T> action) {
		if (action == null) throw new IllegalArgumentException("Swing action is required.");
		if (SwingUtilities.isEventDispatchThread()) return callUnchecked(action);
		FutureTask<T> task = new FutureTask<>(action);
		try {
			SwingUtilities.invokeAndWait(task);
			return task.get();
		} catch (InterruptedException error) {
			task.cancel(false);
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for the desktop dialog.", error);
		} catch (InvocationTargetException error) {
			throw propagate(error.getCause());
		} catch (ExecutionException error) {
			throw propagate(error.getCause());
		}
	}

	static <T> void onEventDispatchThreadAsync(Callable<T> action,
		BiConsumer<T, Throwable> completion) {
		if (action == null) throw new IllegalArgumentException("Swing action is required.");
		if (completion == null) throw new IllegalArgumentException("Swing completion is required.");
		SwingUtilities.invokeLater(() -> {
			T result = null;
			Throwable failure = null;
			try {
				result = action.call();
			} catch (Throwable error) {
				failure = error;
			}
			completion.accept(result, failure);
		});
	}

	private static <T> T callUnchecked(Callable<T> action) {
		try {
			return action.call();
		} catch (Exception error) {
			throw propagate(error);
		}
	}

	private static RuntimeException propagate(Throwable error) {
		if (error instanceof RuntimeException runtime) return runtime;
		if (error instanceof Error fatal) throw fatal;
		return new IllegalStateException("Desktop dialog failed.", error);
	}

	private DesktopLauncher() {}
}
