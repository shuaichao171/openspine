package com.openspine.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.esotericsoftware.spine.EventData;
import com.openspine.model.SkeletonDocument;

/** Rebuilds a new editable Spine project from OpenSpine runtime skeleton data. */
public final class SpineProjectExporter {
	private static final long IMPORT_TIMEOUT_SECONDS = 180;
	private static final int MAX_LOG_READ_BYTES = 64 * 1024;

	public record ProjectSkeleton(String name, SkeletonDocument document) {}
	public record ExportResult(int skeletonCount) {}

	public static ExportResult write(List<ProjectSkeleton> skeletons, Path target) throws IOException {
		return write(skeletons, target, new CancellationToken());
	}

	/**
	 * Imports every skeleton into a staged project and replaces the destination only after every
	 * official Spine CLI invocation succeeds.
	 */
	public static ExportResult write(List<ProjectSkeleton> skeletons, Path target,
		CancellationToken cancellation) throws IOException {
		if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
		List<ProjectSkeleton> validated = validateSkeletons(skeletons);
		Path absoluteTarget = validateTarget(target);
		Path parent = absoluteTarget.getParent();
		Path cli = SpineProjectImporter.findCli();
		cancellation.checkCancelled();

		Path session = Files.createTempDirectory(parent, ".openspine-spine-rebuild-");
		Path stagedProject = session.resolve(absoluteTarget.getFileName());
		Path assetTarget = companionAssetDirectory(absoluteTarget);
		Path stagedAssets = session.resolve(assetTarget.getFileName());
		try {
			Set<String> usedFileNames = new HashSet<>();
			for (int index = 0; index < validated.size(); index++) {
				cancellation.checkCancelled();
				ProjectSkeleton skeleton = validated.get(index);
				String name = normalizedSkeletonName(skeleton.name(), skeleton.document(), index);
				String fileName = uniqueJsonFileName(name, usedFileNames);
				Path runtimeJson = session.resolve(fileName);
				SkeletonExporter.writeAtomic(skeleton.document(), runtimeJson, cancellation);
				stageResources(skeleton.document(), runtimeJson, stagedAssets,
					assetTarget.getFileName().toString(), index + 1, cancellation);
				Path logFile = session.resolve("spine-import-" + (index + 1) + ".log");
				runImport(cli, runtimeJson, stagedProject, name, logFile, cancellation);
				if (!Files.isRegularFile(stagedProject) || Files.size(stagedProject) == 0)
					throw new RuntimeException("Spine 命令行没有生成可用的 .spine 工程");
			}
			cancellation.checkCancelled();
			cancellation.commit(() -> commitProject(stagedProject, stagedAssets,
				absoluteTarget, assetTarget, session));
			return new ExportResult(validated.size());
		} finally {
			deleteDirectory(session);
		}
	}

	private static void stageResources(SkeletonDocument document, Path runtimeJson, Path stagedAssets,
		String assetDirectoryName, int skeletonIndex, CancellationToken cancellation) throws IOException {
		String imagesPath = document.data.getImagesPath();
		String audioPath = document.data.getAudioPath();
		boolean hasEventAudio = false;
		for (EventData event : document.data.getEvents())
			hasEventAudio |= event.getAudioPath() != null && !event.getAudioPath().isBlank();
		if ((imagesPath == null || imagesPath.isBlank())
			&& (audioPath == null || audioPath.isBlank()) && !hasEventAudio) return;

		Path sourceBase = resourceBase(document);
		Path skeletonAssets = stagedAssets.resolve("skeleton-" + skeletonIndex);
		String resourcePrefix = assetDirectoryName + "/skeleton-" + skeletonIndex;
		JsonValue root = new JsonReader().parse(Files.readString(runtimeJson, StandardCharsets.UTF_8));
		JsonValue skeleton = root.get("skeleton");
		if (skeleton == null) throw new IOException("Runtime JSON 缺少 skeleton 元数据");

		if (imagesPath != null && !imagesPath.isBlank()) {
			Path source = resolveSource(sourceBase, imagesPath.trim(), "skeleton.images");
			Path destination = skeletonAssets.resolve("images");
			int copied = copyDirectory(source, destination, true, cancellation);
			if (copied == 0) throw new IOException("JSON 图片目录中没有可导入的图片：" + source);
			setJsonString(skeleton, "images", resourcePrefix + "/images");
		}

		Path sourceAudioBase = null;
		Path stagedAudioBase = skeletonAssets.resolve("audio");
		if (audioPath != null && !audioPath.isBlank()) {
			sourceAudioBase = resolveSource(sourceBase, audioPath.trim(), "skeleton.audio");
			Files.createDirectories(stagedAudioBase);
			copyDirectory(sourceAudioBase, stagedAudioBase, false, cancellation);
			setJsonString(skeleton, "audio", resourcePrefix + "/audio");
		} else if (hasEventAudio) {
			if (sourceBase == null) throw new IOException("事件音频缺少可解析的项目资源目录");
			sourceAudioBase = sourceBase;
			setJsonString(skeleton, "audio", resourcePrefix + "/audio");
		}

		JsonValue jsonEvents = root.get("events");
		int externalIndex = 0;
		for (EventData event : document.data.getEvents()) {
			String eventPath = event.getAudioPath();
			if (eventPath == null || eventPath.isBlank()) continue;
			Path configured;
			try {
				configured = Path.of(eventPath);
			} catch (RuntimeException error) {
				throw new IOException("事件音频路径无效：" + eventPath, error);
			}
			Path relative = safeRelative(configured);
			String stagedEventPath;
			Path source;
			if (!configured.isAbsolute() && relative != null) {
				source = sourceAudioBase.resolve(configured).normalize();
				stagedEventPath = relative.toString().replace('\\', '/');
				Path destination = stagedAudioBase.resolve(relative).normalize();
				if (!destination.startsWith(stagedAudioBase))
					throw new IOException("事件音频路径越过 staging 目录：" + eventPath);
				copyRequiredFile(source, destination, cancellation);
			} else {
				source = configured.isAbsolute() ? configured.normalize()
					: sourceAudioBase.resolve(configured).normalize();
				String fileName = source.getFileName() == null ? "audio" : source.getFileName().toString();
				stagedEventPath = "external-" + (++externalIndex) + "/" + fileName;
				copyRequiredFile(source, stagedAudioBase.resolve(stagedEventPath), cancellation);
			}
			JsonValue definition = jsonEvents == null ? null : jsonEvents.get(event.getName());
			if (definition == null) throw new IOException("Runtime JSON 缺少事件定义：" + event.getName());
			setJsonString(definition, "audio", stagedEventPath);
		}

		cancellation.checkCancelled();
		Files.writeString(runtimeJson, root.prettyPrint(OutputType.json, 120) + System.lineSeparator(),
			StandardCharsets.UTF_8);
	}

	static Path companionAssetDirectory(Path target) {
		Path absolute = target.toAbsolutePath().normalize();
		String fileName = absolute.getFileName().toString();
		String stem = fileName.substring(0, fileName.length() - ".spine".length());
		return absolute.resolveSibling(stem + ".openspine-assets");
	}

	static void commitProject(Path stagedProject, Path stagedAssets, Path target,
		Path assetTarget, Path session) throws IOException {
		Path projectBackup = session.resolve("previous-project.spine");
		Path assetBackup = session.resolve("previous-project-assets");
		boolean projectBackedUp = false;
		boolean assetsBackedUp = false;
		boolean assetsInstalled = false;
		boolean projectInstalled = false;
		try {
			if (Files.exists(target)) {
				moveAtomic(target, projectBackup);
				projectBackedUp = true;
			}
			if (Files.exists(assetTarget)) {
				moveAtomic(assetTarget, assetBackup);
				assetsBackedUp = true;
			}
			if (Files.exists(stagedAssets)) {
				moveAtomic(stagedAssets, assetTarget);
				assetsInstalled = true;
			}
			moveAtomic(stagedProject, target);
			projectInstalled = true;
		} catch (IOException failure) {
			IOException rollbackFailure = null;
			try {
				if (projectInstalled) Files.deleteIfExists(target);
				if (projectBackedUp) moveAtomic(projectBackup, target);
			} catch (IOException error) {
				rollbackFailure = error;
			}
			try {
				if (assetsInstalled) deleteDirectoryRequired(assetTarget);
				if (assetsBackedUp) moveAtomic(assetBackup, assetTarget);
			} catch (IOException error) {
				if (rollbackFailure == null) rollbackFailure = error;
				else rollbackFailure.addSuppressed(error);
			}
			if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
			throw failure;
		}
	}

	private static Path resourceBase(SkeletonDocument document) throws IOException {
		String configured = document.resourceDirectory;
		if (configured != null && !configured.isBlank()) {
			try {
				return Path.of(configured).toAbsolutePath().normalize();
			} catch (RuntimeException error) {
				throw new IOException("项目资源目录无效：" + configured, error);
			}
		}
		if (document.filePath == null || document.filePath.isBlank()
			|| document.filePath.startsWith("builtin:") || document.filePath.startsWith("new:")) return null;
		try {
			Path source = Path.of(document.filePath).toAbsolutePath().normalize();
			return source.getParent();
		} catch (RuntimeException error) {
			throw new IOException("项目来源路径无效：" + document.filePath, error);
		}
	}

	private static Path resolveSource(Path base, String configured, String label) throws IOException {
		try {
			Path path = Path.of(configured);
			if (!path.isAbsolute()) {
				if (base == null) throw new IOException(label + " 缺少可解析的项目资源目录");
				path = base.resolve(path);
			}
			path = path.toAbsolutePath().normalize();
			if (!Files.isDirectory(path)) throw new IOException(label + " 目录不存在：" + path);
			return path;
		} catch (java.nio.file.InvalidPathException error) {
			throw new IOException(label + " 路径无效：" + configured, error);
		}
	}

	private static int copyDirectory(Path source, Path destination, boolean imagesOnly,
		CancellationToken cancellation) throws IOException {
		if (!Files.isDirectory(source)) throw new IOException("资源目录不存在：" + source);
		List<Path> files;
		try (Stream<Path> paths = Files.walk(source)) {
			files = paths.filter(Files::isRegularFile)
				.filter(path -> !imagesOnly || isImage(path))
				.sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER)).toList();
		}
		for (Path file : files) {
			cancellation.checkCancelled();
			Path relative = source.relativize(file).normalize();
			Path target = destination.resolve(relative).normalize();
			if (!target.startsWith(destination)) throw new IOException("资源路径越过 staging 目录：" + file);
			Files.createDirectories(target.getParent());
			Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
		}
		return files.size();
	}

	private static void copyRequiredFile(Path source, Path destination,
		CancellationToken cancellation) throws IOException {
		cancellation.checkCancelled();
		if (!Files.isRegularFile(source)) throw new IOException("事件音频文件不存在：" + source);
		Files.createDirectories(destination.getParent());
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private static boolean isImage(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
			|| name.endsWith(".webp");
	}

	private static Path safeRelative(Path path) {
		if (path == null || path.isAbsolute()) return null;
		Path normalized = path.normalize();
		if (normalized.getNameCount() == 0 || normalized.toString().equals(".")
			|| normalized.startsWith("..")) return null;
		return normalized;
	}

	private static void setJsonString(JsonValue object, String name, String value) {
		JsonValue member = object.get(name);
		if (member == null) object.addChild(name, new JsonValue(value));
		else member.set(value);
	}

	static Path validateTarget(Path target) throws IOException {
		if (target == null) throw new IllegalArgumentException("Spine 工程路径不能为空");
		Path absolute = target.toAbsolutePath().normalize();
		Path fileName = absolute.getFileName();
		if (fileName == null || !fileName.toString().toLowerCase(Locale.ROOT).endsWith(".spine"))
			throw new IllegalArgumentException("重建工程路径必须以 .spine 结尾");
		Path parent = absolute.getParent();
		if (parent == null || !Files.isDirectory(parent))
			throw new IOException("重建工程目录不存在：" + parent);
		return absolute;
	}

	static List<String> command(Path cli, Path runtimeJson, Path outputProject, String skeletonName) {
		if (cli == null || runtimeJson == null || outputProject == null)
			throw new IllegalArgumentException("Spine CLI 命令路径不能为空");
		List<String> command = new ArrayList<>();
		String executableName = cli.getFileName().toString().toLowerCase(Locale.ROOT);
		if (isWindows() && (executableName.endsWith(".cmd") || executableName.endsWith(".bat"))) {
			command.add(firstNonBlank(System.getenv("ComSpec"), "cmd.exe"));
			command.add("/d");
			command.add("/c");
		}
		command.add(cli.toString());
		command.add("-i");
		command.add(runtimeJson.toString());
		command.add("-o");
		command.add(outputProject.toString());
		command.add("--to");
		command.add(skeletonName);
		command.add("-r");
		return List.copyOf(command);
	}

	private static void runImport(Path cli, Path runtimeJson, Path outputProject, String skeletonName,
		Path logFile, CancellationToken cancellation) {
		ProcessBuilder builder = new ProcessBuilder(command(cli, runtimeJson, outputProject, skeletonName));
		builder.directory(runtimeJson.getParent().toFile());
		builder.redirectErrorStream(true);
		builder.redirectOutput(logFile.toFile());
		Process process;
		try {
			process = builder.start();
		} catch (IOException error) {
			throw new RuntimeException("无法运行 Spine 命令行程序：" + concise(error.getMessage()), error);
		}
		try {
			process.getOutputStream().close();
		} catch (IOException error) {
			destroyProcessTree(process, true);
			throw new RuntimeException("无法关闭 Spine 命令行输入流", error);
		}

		boolean finished = false;
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(IMPORT_TIMEOUT_SECONDS);
		try {
			while (!(finished = process.waitFor(250, TimeUnit.MILLISECONDS))) {
				cancellation.checkCancelled();
				String output = readLog(logFile);
				if (activationRequired(output)) {
					destroyProcessTree(process, true);
					throw activationFailure();
				}
				if (System.nanoTime() >= deadline) break;
			}
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			destroyProcessTree(process, true);
			throw new CancellationException("Spine 工程重建已取消");
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
				throw new CancellationException("Spine 工程重建已取消");
			}
			throw new RuntimeException("Spine 工程重建超时（超过 " + IMPORT_TIMEOUT_SECONDS + " 秒）");
		}
		cancellation.checkCancelled();
		String output = readLog(logFile).trim();
		if (process.exitValue() != 0 && activationRequired(output)) throw activationFailure();
		if (process.exitValue() != 0)
			throw new RuntimeException("Spine 工程重建失败（退出码 " + process.exitValue() + "）："
				+ concise(output));
	}

	private static List<ProjectSkeleton> validateSkeletons(List<ProjectSkeleton> skeletons) {
		if (skeletons == null || skeletons.isEmpty())
			throw new IllegalArgumentException("至少需要一个骨架才能重建 Spine 工程");
		Set<SkeletonDocument> documents = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (ProjectSkeleton skeleton : skeletons) {
			if (skeleton == null || skeleton.document() == null || skeleton.document().data == null)
				throw new IllegalArgumentException("待重建的骨架数据不完整");
			if (!documents.add(skeleton.document()))
				throw new IllegalArgumentException("同一个骨架不能重复导入 Spine 工程");
		}
		return List.copyOf(skeletons);
	}

	private static String normalizedSkeletonName(String requested, SkeletonDocument document, int index) {
		String name = requested == null ? "" : requested.trim();
		if (name.isEmpty() && document.data.getName() != null) name = document.data.getName().trim();
		return name.isEmpty() ? "skeleton " + (index + 1) : name;
	}

	private static String uniqueJsonFileName(String skeletonName, Set<String> used) {
		String stem = skeletonName.replaceAll("[\\x00-\\x1f<>:\"/\\\\|?*]", "_")
			.replaceAll("[. ]+$", "").trim();
		if (stem.isEmpty()) stem = "skeleton";
		String candidate = stem + ".json";
		for (int suffix = 2; !used.add(candidate.toLowerCase(Locale.ROOT)); suffix++)
			candidate = stem + "-" + suffix + ".json";
		return candidate;
	}

	private static String readLog(Path logFile) {
		if (!Files.exists(logFile)) return "";
		try (InputStream input = Files.newInputStream(logFile)) {
			return new String(input.readNBytes(MAX_LOG_READ_BYTES), StandardCharsets.UTF_8);
		} catch (IOException error) {
			throw new RuntimeException("无法读取 Spine CLI 输出日志：" + logFile, error);
		}
	}

	private static boolean activationRequired(String output) {
		String lower = output == null ? "" : output.toLowerCase(Locale.ROOT);
		return lower.contains("not activated") || lower.contains("activation code")
			|| lower.contains("activate spine") || lower.contains("enter activation");
	}

	private static RuntimeException activationFailure() {
		return new RuntimeException("Spine 命令行程序尚未激活，无法重建 .spine 工程。请先安装并激活 Spine。");
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

	private static void moveAtomic(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteDirectory(Path directory) {
		if (directory == null || !Files.exists(directory)) return;
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// Temporary rebuild files are best-effort cleanup after success or failure.
		}
	}

	private static void deleteDirectoryRequired(Path directory) throws IOException {
		if (directory == null || !Files.exists(directory)) return;
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
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

	private SpineProjectExporter() {}
}
