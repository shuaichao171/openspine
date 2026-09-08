package com.openspine;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.openspine.mcp.McpServer;

/** OpenSpine 桌面入口（LWJGL3 后端） */
public class OpenSpine {
	public static final String VERSION = "0.2.91";
	static final int VIEWPORT_MSAA_SAMPLES = 4;

	public static void main(String[] args) {
		int exitCode = run(args, System.out, System.err);
		if (exitCode != 0) System.exit(exitCode);
	}

	static int run(String[] args, PrintStream output, PrintStream errorOutput) {
		CommandLine command;
		try {
			command = parseArguments(args);
		} catch (IllegalArgumentException error) {
			errorOutput.println("错误: " + error.getMessage());
			errorOutput.println("使用 --help 查看命令说明。");
			return 2;
		}
		if (command.help()) {
			output.print(helpText());
			return 0;
		}
		if (command.version()) {
			output.println("OpenSpine " + BuildInfo.description(VERSION));
			return 0;
		}
		if (command.mcp()) {
			try {
				new McpServer(command.root(), BuildInfo.version(VERSION), System.in, System.out, System.err).run();
				return 0;
			} catch (IOException | RuntimeException error) {
				errorOutput.println("OpenSpine MCP 启动失败: " + safeMessage(error));
				return 1;
			}
		}
		launchEditor(command.initialPath(), command.captureRoot());
		return 0;
	}

	private static void launchEditor(String initialPath, Path captureRoot) {
		Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
		cfg.setTitle("OpenSpine — 骨骼动画编辑器");
		cfg.setWindowedMode(1440, 860);
		cfg.setWindowSizeLimits(1180, 720, -1, -1);
		cfg.setResizable(true);
		cfg.setDecorated(!WindowsTitleBar.usesCustomFrame());
		cfg.setSamples(VIEWPORT_MSAA_SAMPLES);
		cfg.setForegroundFPS(120);
		cfg.setMaximized(true);
		cfg.setWindowIcon(Files.FileType.Classpath,
			"assets/icons/openspine-16.png",
			"assets/icons/openspine-24.png",
			"assets/icons/openspine-32.png",
			"assets/icons/openspine-48.png",
			"assets/icons/openspine-64.png",
			"assets/icons/openspine-128.png",
			"assets/icons/openspine-256.png");
		EditorApp editor = new EditorApp(initialPath, captureRoot);
		cfg.setWindowListener(new Lwjgl3WindowAdapter() {
			@Override public void created(com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window window) {
				DesktopLauncher.installMainWindow(window.getWindowHandle());
				editor.setNativeWindowHandle(window.getWindowHandle());
			}
			@Override public void focusGained() { editor.refreshSystemAppearance(); }
			@Override public boolean closeRequested() { return editor.closeRequested(); }
			@Override public void filesDropped(String[] files) { editor.filesDropped(files); }
		});
		new Lwjgl3Application(editor, cfg);
	}

	static CommandLine parseArguments(String[] args) {
		boolean help = false;
		boolean version = false;
		boolean mcp = false;
		boolean rootSpecified = false;
		Path root = Path.of("").toAbsolutePath().normalize();
		Path captureRoot = null;
		String initialPath = null;
		boolean positionalOnly = false;
		for (int i = 0; args != null && i < args.length; i++) {
			String argument = args[i];
			if (argument == null) throw new IllegalArgumentException("参数不能为 null。");
			if (!positionalOnly && "--".equals(argument)) {
				positionalOnly = true;
				continue;
			}
			if (!positionalOnly && ("--help".equals(argument) || "-h".equals(argument))) {
				help = true;
			} else if (!positionalOnly && "--version".equals(argument)) {
				version = true;
			} else if (!positionalOnly && "--mcp".equals(argument)) {
				mcp = true;
			} else if (!positionalOnly && "--root".equals(argument)) {
				if (++i >= args.length || args[i] == null || args[i].isBlank())
					throw new IllegalArgumentException("--root 需要一个目录参数。");
				root = Path.of(args[i]).toAbsolutePath().normalize();
				rootSpecified = true;
			} else if (!positionalOnly && "--mcp-capture-root".equals(argument)) {
				if (++i >= args.length || args[i] == null || args[i].isBlank())
					throw new IllegalArgumentException("--mcp-capture-root requires a directory.");
				captureRoot = Path.of(args[i]).toAbsolutePath().normalize();
			} else if (!positionalOnly && argument.startsWith("-")) {
				throw new IllegalArgumentException("未知选项: " + argument);
			} else if (initialPath == null) {
				initialPath = argument;
			} else {
				throw new IllegalArgumentException("只能指定一个启动骨架文件。");
			}
		}
		if (!help && !version) {
			if (captureRoot != null && (mcp || !java.nio.file.Files.isDirectory(captureRoot)))
				throw new IllegalArgumentException("--mcp-capture-root requires an existing directory and editor mode (not --mcp).");
			if (mcp && initialPath != null)
				throw new IllegalArgumentException("--mcp 不能与启动骨架文件同时使用。");
			if (mcp && !rootSpecified)
				throw new IllegalArgumentException("--mcp 必须显式指定 --root <目录>。");
			if (!mcp && rootSpecified)
				throw new IllegalArgumentException("--root 只能与 --mcp 一起使用。");
		}
		return new CommandLine(help, version, mcp, root, initialPath, captureRoot);
	}

	static String helpText() {
		return """
			OpenSpine %s - Spine 4.3 骨骼动画编辑器

			用法:
			  run.bat [工程文件.spine|骨架文件.json|骨架文件.skel]
			  run.bat --mcp --root <目录>
			  run.bat --mcp-capture-root <目录> [骨架文件]
			  OpenSpineMCP.exe --root <目录>  (发布包)
			  run.bat --help

			选项:
			  --mcp          以前台 stdio 模式启动 MCP 服务，不打开编辑器窗口。
			  --root <目录>  限制 MCP 可读取和写入的项目根目录；MCP 模式下必须显式指定。
			  --mcp-capture-root <目录>  在编辑器中启用此目录内项目的 MCP 截图，可能包含未保存的画面内容。
			  --help, -h     显示本说明并退出。
			  --version      显示版本号并退出。
			  --             后续参数按启动文件路径处理。

			MCP 工具:
			  openspine_list_projects      发现根目录内的 .json/.skel 项目。
			  openspine_inspect_project   概览并校验 .json/.skel 项目。
			  openspine_read_project      读取完整 JSON、结构摘要和 SHA-256。
			  openspine_read_project_chunk 分块读取较大的 JSON 项目。
			  openspine_validate_project  校验文件或尚未写入的 JSON。
			  openspine_write_project     校验后原子写入 JSON，支持哈希并发保护与备份。
			  openspine_render_preview    渲染已保存项目的 PNG 单帧或多帧预览。
			  openspine_capture_screenshot 截取已显式启用截图的编辑器画面，不抓取桌面。

			MCP 使用换行分隔的 JSON-RPC stdio 传输；stdout 仅输出协议消息，日志写入 stderr。
			""".formatted(BuildInfo.description(VERSION));
	}

	private static String safeMessage(Throwable error) {
		String message = error.getMessage();
		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
	}

	static record CommandLine(boolean help, boolean version, boolean mcp, Path root, String initialPath, Path captureRoot) {}
}
