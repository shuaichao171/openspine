package com.openspine.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Rotating crash-recovery snapshots stored outside project directories. */
public final class AutoSaveStore {
	public static final String DIRECTORY_PROPERTY = "openspine.autosave.dir";
	private static final int FORMAT_VERSION = 2;
	private static final int MAX_SESSIONS = 3;
	private static final int MAX_DOCUMENTS = 100;
	private static final int MAX_EDITOR_STATE_BYTES = 8 * 1024 * 1024;

	public record SnapshotDocument(String name, String json, String filePath, String savePath,
		String resourceDirectory, String atlasFileName) {
		public SnapshotDocument {
			name = clean(name);
			if (json == null || json.isBlank()) throw new IllegalArgumentException("自动保存 JSON 不能为空");
			filePath = clean(filePath);
			savePath = clean(savePath);
			resourceDirectory = clean(resourceDirectory);
			atlasFileName = clean(atlasFileName);
		}
	}

	public record Snapshot(List<SnapshotDocument> documents, int activeIndex, boolean showAll,
		String editorStateJson) {
		public Snapshot {
			if (documents == null || documents.isEmpty() || documents.size() > MAX_DOCUMENTS)
				throw new IllegalArgumentException("自动保存文档数量无效");
			documents = List.copyOf(documents);
			if (activeIndex < 0 || activeIndex >= documents.size())
				throw new IllegalArgumentException("自动保存活动文档索引无效");
			if (editorStateJson == null || editorStateJson.isBlank())
				throw new IllegalArgumentException("自动保存编辑器状态不能为空");
			if (editorStateJson.getBytes(StandardCharsets.UTF_8).length > MAX_EDITOR_STATE_BYTES)
				throw new IllegalArgumentException("自动保存编辑器状态过大");
		}
	}

	public record StoredDocument(String name, Path jsonPath, String filePath, String savePath,
		String resourceDirectory, String atlasFileName) {}
	public record Session(Path directory, long createdAt, List<StoredDocument> documents,
		int activeIndex, boolean showAll, Path editorStatePath) {}

	public static Session write(Snapshot snapshot) throws IOException {
		if (snapshot == null) throw new IllegalArgumentException("自动保存快照不能为空");
		Path root = rootDirectory();
		Files.createDirectories(root);
		Path staging = Files.createDirectory(root.resolve(".tmp-" + UUID.randomUUID()));
		long createdAt = System.currentTimeMillis();
		try {
			Properties manifest = new Properties();
			manifest.setProperty("version", Integer.toString(FORMAT_VERSION));
			manifest.setProperty("createdAt", Long.toString(createdAt));
			manifest.setProperty("count", Integer.toString(snapshot.documents().size()));
			manifest.setProperty("activeIndex", Integer.toString(snapshot.activeIndex()));
			manifest.setProperty("showAll", Boolean.toString(snapshot.showAll()));
			String editorStateFileName = "editor-state.json";
			Files.writeString(staging.resolve(editorStateFileName), snapshot.editorStateJson(),
				StandardCharsets.UTF_8);
			manifest.setProperty("editorState", editorStateFileName);
			for (int index = 0; index < snapshot.documents().size(); index++) {
				SnapshotDocument document = snapshot.documents().get(index);
				String fileName = index + ".json";
				Files.writeString(staging.resolve(fileName), document.json(), StandardCharsets.UTF_8);
				String prefix = "document." + index + ".";
				manifest.setProperty(prefix + "name", document.name());
				manifest.setProperty(prefix + "file", fileName);
				manifest.setProperty(prefix + "filePath", document.filePath());
				manifest.setProperty(prefix + "savePath", document.savePath());
				manifest.setProperty(prefix + "resourceDirectory", document.resourceDirectory());
				manifest.setProperty(prefix + "atlasFileName", document.atlasFileName());
			}
			try (OutputStream output = Files.newOutputStream(staging.resolve("manifest.properties"))) {
				manifest.store(output, "OpenSpine autosave");
			}
			Path target = root.resolve("session-" + createdAt + "-" + UUID.randomUUID());
			try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE); }
			catch (AtomicMoveNotSupportedException ignored) { Files.move(staging, target); }
			rotate(root);
			return read(target);
		} finally { deleteTree(staging); }
	}

	public static Session latest() throws IOException {
		Path root = rootDirectory();
		if (!Files.isDirectory(root)) return null;
		try (var entries = Files.list(root)) {
			for (Path path : entries.filter(Files::isDirectory)
				.filter(candidate -> candidate.getFileName().toString().startsWith("session-"))
				.sorted(Comparator.comparing(Path::getFileName).reversed()).toList()) {
				try { return read(path); } catch (IOException | RuntimeException ignored) { }
			}
		}
		return null;
	}

	public static void delete(Session session) throws IOException {
		if (session != null) deleteTree(session.directory());
	}

	public static void discardAll() throws IOException {
		Path root = rootDirectory();
		if (!Files.isDirectory(root)) return;
		try (var entries = Files.list(root)) {
			for (Path path : entries.filter(Files::isDirectory)
				.filter(AutoSaveStore::isManagedDirectory).toList()) deleteTree(path);
		}
	}

	static Path rootDirectory() {
		String override = System.getProperty(DIRECTORY_PROPERTY, "").trim();
		if (!override.isEmpty()) return Path.of(override).toAbsolutePath().normalize();
		String local = System.getenv().getOrDefault("LOCALAPPDATA", "").trim();
		if (!local.isEmpty()) return Path.of(local, "OpenSpine", "autosave").toAbsolutePath().normalize();
		return Path.of(System.getProperty("user.home"), ".openspine", "autosave").toAbsolutePath().normalize();
	}

	private static Session read(Path directory) throws IOException {
		Path manifestPath = directory.resolve("manifest.properties");
		Properties manifest = new Properties();
		try (InputStream input = Files.newInputStream(manifestPath)) { manifest.load(input); }
		int version = integer(manifest, "version");
		if (version < 1 || version > FORMAT_VERSION) throw new IOException("不支持的自动保存版本");
		int count = integer(manifest, "count");
		int activeIndex = integer(manifest, "activeIndex");
		if (count <= 0 || count > MAX_DOCUMENTS || activeIndex < 0 || activeIndex >= count)
			throw new IOException("自动保存清单无效");
		List<StoredDocument> documents = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			String prefix = "document." + index + ".";
			Path json = directory.resolve(required(manifest, prefix + "file")).normalize();
			if (!json.getParent().equals(directory.normalize()) || !Files.isRegularFile(json))
				throw new IOException("自动保存文档文件无效");
			documents.add(new StoredDocument(manifest.getProperty(prefix + "name", ""), json,
				manifest.getProperty(prefix + "filePath", ""), manifest.getProperty(prefix + "savePath", ""),
				manifest.getProperty(prefix + "resourceDirectory", ""),
				manifest.getProperty(prefix + "atlasFileName", "")));
		}
		long createdAt;
		try { createdAt = Long.parseLong(required(manifest, "createdAt")); }
		catch (NumberFormatException error) { throw new IOException("自动保存时间无效", error); }
		Path editorStatePath = null;
		String editorStateFile = manifest.getProperty("editorState", "").trim();
		if (!editorStateFile.isEmpty()) {
			editorStatePath = directory.resolve(editorStateFile).normalize();
			if (!editorStatePath.getParent().equals(directory.normalize()) || !Files.isRegularFile(editorStatePath))
				throw new IOException("自动保存编辑器状态文件无效");
		} else if (version >= 2) throw new IOException("自动保存缺少编辑器状态文件");
		return new Session(directory, createdAt, List.copyOf(documents), activeIndex,
			Boolean.parseBoolean(manifest.getProperty("showAll", "false")), editorStatePath);
	}

	private static void rotate(Path root) throws IOException {
		try (var entries = Files.list(root)) {
			List<Path> sessions = entries.filter(Files::isDirectory)
				.filter(path -> path.getFileName().toString().startsWith("session-"))
				.sorted(Comparator.comparing(Path::getFileName).reversed()).toList();
			for (int index = MAX_SESSIONS; index < sessions.size(); index++) deleteTree(sessions.get(index));
		}
	}

	private static boolean isManagedDirectory(Path path) {
		String name = path.getFileName() == null ? "" : path.getFileName().toString();
		return name.startsWith("session-") || name.startsWith(".tmp-");
	}

	private static int integer(Properties properties, String key) throws IOException {
		try { return Integer.parseInt(required(properties, key)); }
		catch (NumberFormatException error) { throw new IOException("自动保存字段无效：" + key, error); }
	}

	private static String required(Properties properties, String key) throws IOException {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) throw new IOException("自动保存缺少字段：" + key);
		return value;
	}

	private static String clean(String value) { return value == null ? "" : value.trim(); }

	private static void deleteTree(Path root) throws IOException {
		if (root == null || !Files.exists(root)) return;
		IOException[] failure = {null};
		try (var paths = Files.walk(root)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try { Files.deleteIfExists(path); }
				catch (IOException error) {
					if (failure[0] == null) failure[0] = error;
					else failure[0].addSuppressed(error);
				}
			});
		}
		if (failure[0] != null) throw failure[0];
	}

	private AutoSaveStore() {}
}
