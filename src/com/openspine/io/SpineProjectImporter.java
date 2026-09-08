package com.openspine.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.attachments.AtlasAttachmentLoader;
import com.openspine.model.SkeletonDocument;

/** Converts a Spine editor project through the installed Spine CLI, then loads its runtime export. */
public final class SpineProjectImporter {
	public static final String CLI_PROPERTY = "openspine.spine.cli";
	public static final String CLI_ENVIRONMENT = "OPENSPINE_SPINE_CLI";
	private static final long EXPORT_TIMEOUT_SECONDS = 180;
	private static final int MAX_LOG_READ_BYTES = 64 * 1024;
	private static final Set<Path> SESSION_DIRECTORIES = new LinkedHashSet<>();

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			synchronized (SESSION_DIRECTORIES) {
				for (Path directory : SESSION_DIRECTORIES) deleteDirectory(directory);
			}
		}, "openspine-spine-import-cleanup"));
	}

	public static SkeletonDocument load(SkeletonDocument document, FileHandle projectFile) {
		if (document == null) throw new IllegalArgumentException("Document is required");
		PreparedProject prepared = prepare(projectFile);
		try {
			return loadPrepared(document, prepared);
		} catch (RuntimeException error) {
			prepared.close();
			throw error;
		}
	}

	/** Runs the potentially slow Spine CLI conversion without touching LibGDX graphics resources. */
	public static PreparedProject prepare(FileHandle projectFile) {
		if (projectFile == null) throw new IllegalArgumentException("Spine project file is required");
		Path project = projectFile.file().toPath().toAbsolutePath().normalize();
		if (!Files.isRegularFile(project))
			throw new RuntimeException("Spine project file not found: " + project);

		Path sessionDirectory = null;
		try {
			sessionDirectory = Files.createTempDirectory("openspine-spine-import-");
			Path outputDirectory = Files.createDirectory(sessionDirectory.resolve("export"));
			Path logFile = sessionDirectory.resolve("spine-cli.log");
			RuntimeException cliFailure;
			try {
				Path cli = findCli();
				String output = runExport(cli, project, outputDirectory, logFile);
				List<Path> skeletonFiles;
				try {
					skeletonFiles = findSkeletonJsons(outputDirectory, output);
				} catch (IOException error) {
					throw new RuntimeException("无法检查 Spine CLI 导出的骨架数据", error);
				}
				List<PreparedRuntime> runtimes = skeletonFiles.stream()
					.map(file -> new PreparedRuntime(file, file.getParent(), null)).toList();
				return prepared(project, runtimes, sessionDirectory, false);
			} catch (CliUnavailableException error) {
				cliFailure = error;
			}

			PreparedProject fallback = prepareSiblingRuntime(project, sessionDirectory);
			if (fallback != null) return fallback;
			throw cliFailure;
		} catch (IOException error) {
			deleteDirectory(sessionDirectory);
			throw new RuntimeException("无法导入 Spine 工程：" + project, error);
		} catch (RuntimeException error) {
			deleteDirectory(sessionDirectory);
			throw error;
		}
	}

	/** Converts an explicitly versioned legacy runtime skeleton through a temporary official Spine project.
	 * The source file is never modified and remains the document's displayed source path. */
	public static PreparedProject prepareRuntimeConversion(FileHandle runtimeFile) {
		if (runtimeFile == null) throw new IllegalArgumentException("Runtime skeleton file is required");
		Path source = runtimeFile.file().toPath().toAbsolutePath().normalize();
		if (!Files.isRegularFile(source))
			throw new RuntimeException("Skeleton file not found: " + source);
		if (!isRuntimeSkeleton(source))
			throw new IllegalArgumentException("Runtime conversion requires a .json or .skel file");
		String version = SkeletonLoader.declaredRuntimeVersion(new FileHandle(source.toFile()));
		if (version == null || version.isBlank())
			SkeletonLoader.requireSupportedRuntimeVersion(version, isJson(source) ? "JSON" : "SKEL", source.toString());
		if (!SpineVersionPolicy.requiresConversion(version))
			throw new IllegalArgumentException("Spine " + version + " runtime data does not require conversion");

		Path sessionDirectory = null;
		try {
			sessionDirectory = Files.createTempDirectory("openspine-runtime-convert-");
			Path importedProject = sessionDirectory.resolve("converted.spine");
			Path importLog = sessionDirectory.resolve("spine-import.log");
			Path outputDirectory = Files.createDirectory(sessionDirectory.resolve("export"));
			Path exportLog = sessionDirectory.resolve("spine-export.log");
			Path cli = findCli();
			runRuntimeImport(cli, source, importedProject, stem(source.getFileName().toString()), importLog);
			String output = runExport(cli, importedProject, outputDirectory, exportLog);
			List<Path> skeletonFiles = findSkeletonJsons(outputDirectory, output);
			List<PreparedRuntime> runtimes = skeletonFiles.stream()
				.map(file -> new PreparedRuntime(file, file.getParent(), null)).toList();
			return prepared(source, runtimes, sessionDirectory, false);
		} catch (IOException error) {
			deleteDirectory(sessionDirectory);
			throw new RuntimeException("无法转换 Spine Runtime 数据：" + source, error);
		} catch (RuntimeException error) {
			deleteDirectory(sessionDirectory);
			throw error;
		}
	}

	/** Loads a prepared runtime export. This step must run on the LibGDX render thread when textures are used. */
	public static SkeletonDocument loadPrepared(SkeletonDocument document, PreparedProject prepared) {
		if (document == null) throw new IllegalArgumentException("Document is required");
		if (prepared == null) throw new IllegalArgumentException("Prepared Spine project is required");
		PreparedRuntime runtime = prepared.selected();
		try {
			loadPreparedRuntime(document, prepared.projectFile(), runtime);
			document.ownSourceResources(prepared::close);
			return document;
		} catch (RuntimeException error) {
			prepared.close();
			throw error;
		}
	}

	/** Loads every skeleton exported from one Spine project and keeps their shared temporary data alive. */
	public static List<LoadedSkeleton> loadAllPrepared(PreparedProject prepared) {
		if (prepared == null) throw new IllegalArgumentException("Prepared Spine project is required");
		List<PreparedRuntime> runtimes = prepared.runtimesToLoad();
		List<LoadedSkeleton> loaded = new ArrayList<>(runtimes.size());
		try {
			for (PreparedRuntime runtime : runtimes) {
				SkeletonDocument document = new SkeletonDocument();
				loadPreparedRuntime(document, prepared.projectFile(), runtime);
				Path source = runtime.runtimeSource() != null ? runtime.runtimeSource() : runtime.skeletonFile();
				loaded.add(new LoadedSkeleton(stem(source.getFileName().toString()), document));
			}
			SharedPreparedResources resources = new SharedPreparedResources(prepared, loaded.size());
			for (LoadedSkeleton skeleton : loaded) skeleton.document().ownSourceResources(resources::release);
			return List.copyOf(loaded);
		} catch (RuntimeException error) {
			for (LoadedSkeleton skeleton : loaded) skeleton.document().dispose();
			prepared.close();
			throw error;
		}
	}

	/** Loads the explicitly selected fallback candidate and transfers temporary-resource ownership. */
	public static LoadedSkeleton loadSelectedPrepared(PreparedProject prepared) {
		if (prepared == null) throw new IllegalArgumentException("Prepared Spine project is required");
		PreparedRuntime runtime = prepared.selected();
		SkeletonDocument document = new SkeletonDocument();
		try {
			loadPreparedRuntime(document, prepared.projectFile(), runtime);
			Path source = runtime.runtimeSource() != null ? runtime.runtimeSource() : runtime.skeletonFile();
			document.ownSourceResources(prepared::close);
			return new LoadedSkeleton(stem(source.getFileName().toString()), document);
		} catch (RuntimeException error) {
			document.dispose();
			prepared.close();
			throw error;
		}
	}

	private static void loadPreparedRuntime(SkeletonDocument document, Path projectFile, PreparedRuntime runtime) {
		Path skeletonFile = runtime.skeletonFile();
		Path resourceDirectory = runtime.resourceDirectory();
		SkeletonLoader.loadRuntime(document, new FileHandle(skeletonFile.toFile()),
			new FileHandle(resourceDirectory.toFile()));
		if (document.atlasFileName != null && !document.atlasFileName.isBlank()) {
			document.atlasFileName = resourceDirectory.resolve(document.atlasFileName)
				.toAbsolutePath().normalize().toString();
		}
		document.filePath = projectFile.toString();
		document.savePath = null;
	}

	public record LoadedSkeleton(String name, SkeletonDocument document) {}

	/** Owns one temporary CLI export until it is loaded or explicitly discarded. */
	public static final class PreparedProject implements AutoCloseable {
		private final Path projectFile;
		private final List<PreparedRuntime> runtimes;
		private final Path sessionDirectory;
		private final boolean runtimeSelectionRequired;
		private int selectedRuntime;
		private boolean closed;

		private PreparedProject(Path projectFile, List<PreparedRuntime> runtimes, Path sessionDirectory,
			boolean runtimeSelectionRequired) {
			this.projectFile = projectFile;
			this.runtimes = List.copyOf(runtimes);
			this.sessionDirectory = sessionDirectory;
			this.runtimeSelectionRequired = runtimeSelectionRequired;
			selectedRuntime = runtimeSelectionRequired ? -1 : 0;
		}

		public Path projectFile() { return projectFile; }
		public synchronized Path skeletonFile() { return selected().skeletonFile(); }
		public synchronized Path resourceDirectory() { return selected().resourceDirectory(); }
		/** The sibling runtime export used when the official CLI was unavailable, otherwise null. */
		public synchronized Path runtimeSource() { return selected().runtimeSource(); }

		public synchronized boolean requiresRuntimeSelection() {
			ensureOpen();
			return runtimeSelectionRequired && selectedRuntime < 0;
		}

		public synchronized List<RuntimeCandidate> runtimeCandidates() {
			ensureOpen();
			if (!runtimeSelectionRequired || selectedRuntime >= 0) return List.of();
			return runtimes.stream().map(runtime -> {
				Path source = runtime.runtimeSource() != null ? runtime.runtimeSource() : runtime.skeletonFile();
				return new RuntimeCandidate(source.getFileName().toString(), source);
			}).toList();
		}

		public synchronized void selectRuntime(int index) {
			ensureOpen();
			if (index < 0 || index >= runtimes.size())
				throw new IllegalArgumentException("Runtime candidate index is out of range: " + index);
			selectedRuntime = index;
		}

		private PreparedRuntime selected() {
			ensureOpen();
			if (selectedRuntime < 0)
				throw new IllegalStateException("请先选择要导入的 Spine 骨架数据");
			return runtimes.get(selectedRuntime);
		}

		private synchronized List<PreparedRuntime> runtimesToLoad() {
			ensureOpen();
			if (!runtimeSelectionRequired) return runtimes;
			return List.of(selected());
		}

		private synchronized void ensureOpen() {
			if (closed) throw new IllegalStateException("Prepared Spine project has already been discarded");
		}

		@Override public synchronized void close() {
			if (closed) return;
			closed = true;
			synchronized (SESSION_DIRECTORIES) {
				SESSION_DIRECTORIES.remove(sessionDirectory);
			}
			deleteDirectory(sessionDirectory);
		}
	}

	public record RuntimeCandidate(String name, Path sourcePath) {}

	private record PreparedRuntime(Path skeletonFile, Path resourceDirectory, Path runtimeSource) {}

	private static final class SharedPreparedResources {
		private PreparedProject prepared;
		private int remaining;

		SharedPreparedResources(PreparedProject prepared, int remaining) {
			this.prepared = prepared;
			this.remaining = remaining;
		}

		synchronized void release() {
			if (remaining <= 0) return;
			remaining--;
			if (remaining == 0 && prepared != null) {
				PreparedProject owned = prepared;
				prepared = null;
				owned.close();
			}
		}
	}

	private static PreparedProject prepared(Path project, Path skeletonFile, Path resourceDirectory,
		Path runtimeSource, Path sessionDirectory) {
		return prepared(project, List.of(new PreparedRuntime(skeletonFile, resourceDirectory, runtimeSource)),
			sessionDirectory, false);
	}

	private static PreparedProject prepared(Path project, List<PreparedRuntime> runtimes, Path sessionDirectory,
		boolean runtimeSelectionRequired) {
		if (runtimes.isEmpty()) throw new IllegalArgumentException("At least one runtime skeleton is required");
		synchronized (SESSION_DIRECTORIES) {
			SESSION_DIRECTORIES.add(sessionDirectory);
		}
		return new PreparedProject(project, runtimes, sessionDirectory, runtimeSelectionRequired);
	}

	private static PreparedProject prepareSiblingRuntime(Path project, Path sessionDirectory) throws IOException {
		Path parent = project.getParent();
		if (parent == null) return null;
		String projectStem = stem(project.getFileName().toString());
		List<Path> candidates;
		try (Stream<Path> files = Files.list(parent)) {
			candidates = files.filter(Files::isRegularFile)
				.filter(SpineProjectImporter::isRuntimeSkeleton)
				.sorted(Comparator.comparingInt((Path path) -> fallbackRank(path, projectStem))
					.thenComparing((Path path) -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
				.toList();
		}
		if (candidates.isEmpty()) return null;
		List<Path> matchingStem = candidates.stream()
			.filter(path -> stem(path.getFileName().toString()).equalsIgnoreCase(projectStem)).toList();
		boolean explicitSelectionRequired = matchingStem.isEmpty();
		if (!matchingStem.isEmpty()) candidates = matchingStem;

		List<String> failures = new ArrayList<>();
		List<PreparedRuntime> runtimes = new ArrayList<>();
		for (int index = 0; index < candidates.size(); index++) {
			Path candidate = candidates.get(index);
			Path candidateDirectory = Files.createDirectory(sessionDirectory.resolve("fallback-" + index));
			Path preparedFile = candidateDirectory.resolve(candidate.getFileName().toString());
			try {
				Files.copy(candidate, preparedFile);
				validateRuntimeSkeleton(preparedFile);
				runtimes.add(new PreparedRuntime(preparedFile, parent, candidate));
			} catch (IOException | RuntimeException error) {
				failures.add(candidate.getFileName() + "：" + concise(rootMessage(error)));
			}
		}
		if (!runtimes.isEmpty()) return prepared(project, runtimes, sessionDirectory,
			explicitSelectionRequired || runtimes.size() > 1);
		throw new RuntimeException("Spine 命令行导出不可用，并且同目录的 runtime 骨架均无法加载："
			+ String.join("；", failures));
	}

	private static void validateRuntimeSkeleton(Path skeletonFile) {
		TextureAtlas atlas = new TextureAtlas();
		try {
			AtlasAttachmentLoader attachments = new AtlasAttachmentLoader(atlas, true);
			FileHandle file = new FileHandle(skeletonFile.toFile());
			SkeletonData data;
			if (isJson(skeletonFile)) {
				JsonValue source = new JsonReader().parse(file);
				SkeletonLoader.requireSupportedRuntimeVersion(SkeletonLoader.jsonVersion(source), "JSON", file.path());
				SkeletonStructureValidator.validateJson(source);
				data = new SkeletonJson(attachments).readSkeletonData(source);
			} else {
				SkeletonLoader.requireSupportedRuntimeVersion(
					SkeletonLoader.readBinaryVersion(file), "SKEL", file.path());
				data = new SkeletonBinary(attachments).readSkeletonData(file);
			}
			SkeletonLoader.validateSkeletonData(data, skeletonFile.toString());
		} finally {
			atlas.dispose();
		}
	}

	private static boolean isRuntimeSkeleton(Path path) {
		String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return lower.endsWith(".json") || lower.endsWith(".skel");
	}

	private static boolean isJson(Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
	}

	private static int fallbackRank(Path path, String projectStem) {
		String name = path.getFileName().toString();
		boolean exactStem = stem(name).equalsIgnoreCase(projectStem);
		return (exactStem ? 0 : 2) + (isJson(path) ? 0 : 1);
	}

	private static String stem(String name) {
		int dot = name.lastIndexOf('.');
		return dot <= 0 ? name : name.substring(0, dot);
	}

	static Path findCli() {
		String configured = firstNonBlank(System.getProperty(CLI_PROPERTY), System.getenv(CLI_ENVIRONMENT));
		if (configured != null) {
			Path path = normalizedPath(configured);
			if (path != null && Files.isRegularFile(path)) return path;
			throw unavailableFailure("Configured Spine CLI does not exist: " + configured);
		}

		Set<Path> candidates = new LinkedHashSet<>();
		boolean windows = isWindows();
		String[] executableNames = windows ? new String[] {"Spine.com", "Spine.exe"} : new String[] {"Spine"};
		addPathCandidates(candidates, System.getenv("PATH"), executableNames);
		addInstallDirectory(candidates, System.getenv("ProgramFiles"), executableNames);
		addInstallDirectory(candidates, System.getenv("ProgramFiles(x86)"), executableNames);
		addInstallDirectory(candidates, System.getenv("LOCALAPPDATA"), executableNames);

		Path workingDirectory = normalizedPath(System.getProperty("user.dir"));
		for (Path directory = workingDirectory; directory != null; directory = directory.getParent())
			addExecutableCandidates(candidates, directory.resolve("Spine"), executableNames);
		if (windows) {
			for (Path root : Path.of(".").getFileSystem().getRootDirectories()) {
				addExecutableCandidates(candidates, root.resolve("Program Files").resolve("Spine"), executableNames);
				addExecutableCandidates(candidates, root.resolve("Program Files (x86)").resolve("Spine"), executableNames);
			}
		} else {
			addExecutableCandidates(candidates, Path.of("/Applications/Spine/Spine.app/Contents/MacOS"), executableNames);
			addExecutableCandidates(candidates, Path.of("/opt/Spine"), executableNames);
		}

		for (Path candidate : candidates)
			if (Files.isRegularFile(candidate)) return candidate;
		throw unavailableFailure("未找到 Spine 命令行程序，无法执行格式转换。请安装并激活 Spine，或通过环境变量 "
			+ CLI_ENVIRONMENT + " 指定 Spine.com 的完整路径。");
	}

	private static String runExport(Path cli, Path project, Path outputDirectory, Path logFile) {
		return runCli(command(cli, project, outputDirectory), project.getParent(), logFile, "Spine 工程转换");
	}

	private static String runRuntimeImport(Path cli, Path runtimeFile, Path outputProject, String skeletonName,
		Path logFile) {
		String output = runCli(runtimeImportCommand(cli, runtimeFile, outputProject, skeletonName),
			runtimeFile.getParent(), logFile, "Spine Runtime 转换");
		if (!Files.isRegularFile(outputProject))
			throw new RuntimeException("Spine 命令行程序没有生成临时工程：" + concise(output));
		return output;
	}

	private static String runCli(List<String> command, Path workingDirectory, Path logFile, String operation) {
		ProcessBuilder builder = new ProcessBuilder(command);
		if (workingDirectory != null) builder.directory(workingDirectory.toFile());
		builder.redirectErrorStream(true);
		builder.redirectOutput(logFile.toFile());
		Process process;
		try {
			process = builder.start();
		} catch (IOException error) {
			throw unavailableFailure("无法运行 Spine 命令行程序：" + concise(error.getMessage()), error);
		}
		try {
			process.getOutputStream().close();
		} catch (IOException error) {
			destroyProcessTree(process, true);
			throw new RuntimeException("无法关闭 Spine 命令行输入流", error);
		}
		boolean finished = false;
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(EXPORT_TIMEOUT_SECONDS);
		try {
			while (!(finished = process.waitFor(250, TimeUnit.MILLISECONDS))) {
				if (activationRequired(readLog(logFile))) {
					destroyProcessTree(process, true);
					throw activationFailure();
				}
				if (System.nanoTime() >= deadline) break;
			}
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			destroyProcessTree(process, true);
			throw new RuntimeException(operation + "已中断", error);
		} catch (RuntimeException error) {
			destroyProcessTree(process, true);
			throw error;
		}
		if (!finished) {
			destroyProcessTree(process, false);
			try {
				if (!process.waitFor(5, TimeUnit.SECONDS)) destroyProcessTree(process, true);
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				destroyProcessTree(process, true);
			}
			throw new RuntimeException(operation + "超时（超过 " + EXPORT_TIMEOUT_SECONDS + " 秒）");
		}
		String output = readLog(logFile).trim();
		if (process.exitValue() != 0 && activationRequired(output))
			throw activationFailure();
		if (process.exitValue() != 0)
			throw new RuntimeException(operation + "失败（退出码 " + process.exitValue() + "）：" + concise(output));
		return output;
	}

	private static boolean activationRequired(String output) {
		String lower = output == null ? "" : output.toLowerCase(Locale.ROOT);
		return lower.contains("not activated") || lower.contains("activation code")
			|| lower.contains("activate spine") || lower.contains("enter activation");
	}

	private static CliUnavailableException activationFailure() {
		return unavailableFailure("Spine 命令行程序尚未激活，无法执行格式转换。请先激活 Spine。"
			+ "导入 .spine 工程时，也可在工程同目录提供可用的 Spine 4.3 .json/.skel runtime 导出。");
	}

	static String readLog(Path logFile) {
		if (!Files.exists(logFile)) return "";
		try (InputStream input = Files.newInputStream(logFile)) {
			return new String(input.readNBytes(MAX_LOG_READ_BYTES), StandardCharsets.UTF_8);
		} catch (IOException error) {
			throw new RuntimeException("无法读取 Spine CLI 输出日志：" + logFile, error);
		}
	}

	private static void destroyProcessTree(Process process, boolean forcibly) {
		if (process == null) return;
		for (ProcessHandle descendant : process.descendants().toList()) {
			if (forcibly) descendant.destroyForcibly();
			else descendant.destroy();
		}
		if (forcibly) process.destroyForcibly();
		else process.destroy();
	}

	private static List<String> command(Path cli, Path project, Path outputDirectory) {
		List<String> command = new ArrayList<>();
		String name = cli.getFileName().toString().toLowerCase(Locale.ROOT);
		if (isWindows() && (name.endsWith(".cmd") || name.endsWith(".bat"))) {
			command.add(firstNonBlank(System.getenv("ComSpec"), "cmd.exe"));
			command.add("/d");
			command.add("/c");
		}
		command.add(cli.toString());
		command.add("-i");
		command.add(project.toString());
		command.add("-o");
		command.add(outputDirectory.toString());
		command.add("-e");
		command.add("json+pack");
		return command;
	}

	static List<String> runtimeImportCommand(Path cli, Path runtimeFile, Path outputProject, String skeletonName) {
		if (cli == null || runtimeFile == null || outputProject == null)
			throw new IllegalArgumentException("Spine CLI command paths are required");
		List<String> command = new ArrayList<>();
		String executableName = cli.getFileName().toString().toLowerCase(Locale.ROOT);
		if (isWindows() && (executableName.endsWith(".cmd") || executableName.endsWith(".bat"))) {
			command.add(firstNonBlank(System.getenv("ComSpec"), "cmd.exe"));
			command.add("/d");
			command.add("/c");
		}
		command.add(cli.toString());
		command.add("-i");
		command.add(runtimeFile.toString());
		command.add("-o");
		command.add(outputProject.toString());
		command.add("--to");
		command.add(firstNonBlank(skeletonName, "skeleton"));
		command.add("-r");
		return List.copyOf(command);
	}

	static List<Path> findSkeletonJsons(Path outputDirectory, String cliOutput) throws IOException {
		List<Path> skeletonFiles = new ArrayList<>();
		try (Stream<Path> files = Files.walk(outputDirectory)) {
			for (Path file : files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
				.sorted().toList()) {
				try {
					JsonValue root = new JsonReader().parse(Files.readString(file, StandardCharsets.UTF_8));
					JsonValue bones = root.get("bones");
					if (bones != null && bones.isArray()) skeletonFiles.add(file.toAbsolutePath().normalize());
				} catch (RuntimeException ignored) {
					// Export or texture pack settings are JSON too, but are not skeleton data.
				}
			}
		}
		if (!skeletonFiles.isEmpty()) return List.copyOf(skeletonFiles);
		String lowerOutput = cliOutput == null ? "" : cliOutput.toLowerCase(Locale.ROOT);
		if (lowerOutput.contains("not activated") || lowerOutput.contains("activation"))
			throw activationFailure();
		throw new RuntimeException("Spine 命令行程序没有生成可加载的 JSON 骨架数据。" + concise(cliOutput));
	}

	private static void addInstallDirectory(Set<Path> candidates, String baseDirectory, String[] names) {
		Path base = normalizedPath(baseDirectory);
		if (base == null) return;
		addExecutableCandidates(candidates, base.resolve("Spine"), names);
		addExecutableCandidates(candidates, base.resolve("Programs").resolve("Spine"), names);
	}

	private static void addPathCandidates(Set<Path> candidates, String pathValue, String[] names) {
		if (pathValue == null || pathValue.isBlank()) return;
		for (String value : pathValue.split(java.io.File.pathSeparator)) {
			Path directory = normalizedPath(value);
			if (directory != null) addExecutableCandidates(candidates, directory, names);
		}
	}

	private static void addExecutableCandidates(Set<Path> candidates, Path directory, String[] names) {
		if (directory == null) return;
		for (String name : names) candidates.add(directory.resolve(name).toAbsolutePath().normalize());
	}

	private static Path normalizedPath(String value) {
		if (value == null || value.isBlank()) return null;
		String trimmed = value.trim();
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\""))
			trimmed = trimmed.substring(1, trimmed.length() - 1);
		try {
			return Path.of(trimmed).toAbsolutePath().normalize();
		} catch (InvalidPathException ignored) {
			return null;
		}
	}

	private static String firstNonBlank(String... values) {
		for (String value : values)
			if (value != null && !value.isBlank()) return value.trim();
		return null;
	}

	private static String concise(String output) {
		if (output == null || output.isBlank()) return "未提供错误详情。";
		String normalized = output.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
		return normalized.length() <= 1200 ? normalized : normalized.substring(0, 1200) + "...";
	}

	private static String rootMessage(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null && current.getCause() != current) current = current.getCause();
		return firstNonBlank(current.getMessage(), error.getMessage(), error.getClass().getSimpleName());
	}

	private static CliUnavailableException unavailableFailure(String message) {
		return new CliUnavailableException(message, null);
	}

	private static CliUnavailableException unavailableFailure(String message, Throwable cause) {
		return new CliUnavailableException(message, cause);
	}

	private static final class CliUnavailableException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		CliUnavailableException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static void deleteDirectory(Path directory) {
		if (directory == null || !Files.exists(directory)) return;
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
				}
			}
		} catch (IOException ignored) {
		}
	}

	private SpineProjectImporter() {}
}
