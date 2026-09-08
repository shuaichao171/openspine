package com.openspine.mcp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.AttachmentLoader;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.PointAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.io.SkeletonStructureValidator;

/** Headless Spine project operations exposed by the MCP server. */
final class SpineProjectTools {
	static final long MAX_PROJECT_BYTES = 16L * 1024L * 1024L;
	static final long MAX_INLINE_PROJECT_BYTES = 512L * 1024L;
	static final int DEFAULT_PROJECT_CHUNK_CHARS = 65_536;
	static final int MAX_PROJECT_CHUNK_CHARS = 262_144;
	private static final int MAX_DISCOVERY_ENTRIES = 100_000;
	private static final long MAX_DISCOVERY_VALIDATION_BYTES = 128L * 1024L * 1024L;

	private final Path root;

	SpineProjectTools(Path root) throws IOException {
		if (root == null) throw new IllegalArgumentException("MCP root is required.");
		Path candidate = root.toAbsolutePath().normalize();
		if (!Files.isDirectory(candidate))
			throw new IOException("MCP root is not a directory: " + candidate);
		this.root = candidate.toRealPath();
	}

	Path root() {
		return root;
	}

	JsonValue listProjects(String requestedDirectory, int maxDepth, int offset, int limit) throws ToolFailure {
		Path directory = resolveReadableDirectory(requestedDirectory);
		List<Path> candidates = new ArrayList<>();
		int visited = 0;
		try (Stream<Path> paths = Files.walk(directory, maxDepth + 1)) {
			for (var iterator = paths.iterator(); iterator.hasNext();) {
				checkCancelled();
				Path path = iterator.next();
				if (++visited > MAX_DISCOVERY_ENTRIES)
					throw failure("Project discovery exceeded " + MAX_DISCOVERY_ENTRIES
						+ " filesystem entries; choose a narrower path or depth.");
				if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && isProject(path)) candidates.add(path);
			}
		} catch (IOException | UncheckedIOException error) {
			throw failure("Unable to list projects: " + message(error), error);
		}

		candidates.sort(Comparator
			.comparing((Path path) -> relativePath(path).toLowerCase(Locale.ROOT))
			.thenComparing(this::relativePath));
		List<Path> projects = new ArrayList<>();
		long validationBytes = 0;
		int skippedInvalid = 0;
		for (Path candidate : candidates) {
			checkCancelled();
			long size;
			try {
				size = Files.size(candidate);
			} catch (IOException error) {
				throw failure("Unable to inspect discovered project: " + candidate + ": " + message(error), error);
			}
			if (size > MAX_PROJECT_BYTES) {
				skippedInvalid++;
				continue;
			}
			if (validationBytes > MAX_DISCOVERY_VALIDATION_BYTES - size)
				throw failure("Project discovery validation exceeds " + MAX_DISCOVERY_VALIDATION_BYTES
					+ " bytes; choose a narrower path or depth.");
			validationBytes += size;
			try {
				byte[] bytes = readBytes(realPathWithinRoot(candidate));
				parseProject(candidate, bytes);
				projects.add(candidate);
			} catch (ToolFailure invalidProject) {
				checkCancelled();
				skippedInvalid++;
			}
		}
		int from = Math.min(offset, projects.size());
		int to = Math.min(projects.size(), from + limit);
		JsonValue items = array();
		for (int index = from; index < to; index++) {
			checkCancelled();
			Path path = projects.get(index);
			try {
				JsonValue item = object();
				put(item, "path", relativePath(path));
				put(item, "format", format(path));
				put(item, "bytes", Files.size(path));
				put(item, "last_modified", Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)
					.toInstant().toString());
				items.addChild(item);
			} catch (IOException error) {
				throw failure("Unable to inspect discovered project: " + path + ": " + message(error), error);
			}
		}

		JsonValue result = object();
		put(result, "directory", relativePath(directory));
		put(result, "offset", offset);
		put(result, "total", projects.size());
		put(result, "candidate_count", candidates.size());
		put(result, "skipped_invalid", skippedInvalid);
		put(result, "validation_bytes", validationBytes);
		put(result, "projects", items);
		if (to < projects.size()) put(result, "next_offset", to);
		return result;
	}

	JsonValue inspectProject(String requestedPath) throws ToolFailure {
		checkCancelled();
		Path path = resolveReadableProject(requestedPath, false);
		byte[] bytes = readBytes(path);
		ParsedProject project = parseProject(path, bytes);
		checkCancelled();
		return summary(project.data(), path, sha256(bytes), format(path));
	}

	JsonValue readProject(String requestedPath) throws ToolFailure {
		Path path = resolveReadableProject(requestedPath, true);
		byte[] bytes = readBytes(path);
		if (bytes.length > MAX_INLINE_PROJECT_BYTES)
			throw failure("Project exceeds the inline read limit of " + MAX_INLINE_PROJECT_BYTES
				+ " bytes; use openspine_read_project_chunk.");
		String json = decodeJson(bytes);
		ParsedProject project = parseJson(json);
		JsonValue result = object();
		put(result, "path", path.toString());
		put(result, "sha256", sha256(bytes));
		put(result, "json", json);
		put(result, "summary", summary(project.data(), path, sha256(bytes), "spine-json"));
		return result;
	}

	JsonValue readProjectChunk(String requestedPath, int offsetChars, int maxChars) throws ToolFailure {
		Path path = resolveReadableProject(requestedPath, true);
		byte[] bytes = readBytes(path);
		String json = decodeJson(bytes);
		ParsedProject project = parseJson(json);
		int totalChars = json.codePointCount(0, json.length());
		if (offsetChars > totalChars)
			throw failure("offset_chars exceeds the project length of " + totalChars + " characters.");
		int returnedChars = Math.min(maxChars, totalChars - offsetChars);
		int start = json.offsetByCodePoints(0, offsetChars);
		int end = json.offsetByCodePoints(start, returnedChars);
		int nextOffset = offsetChars + returnedChars;
		String sha = sha256(bytes);

		JsonValue result = object();
		put(result, "path", path.toString());
		put(result, "sha256", sha);
		put(result, "offset_chars", offsetChars);
		put(result, "returned_chars", returnedChars);
		put(result, "total_chars", totalChars);
		put(result, "complete", nextOffset == totalChars);
		if (nextOffset < totalChars) put(result, "next_offset_chars", nextOffset);
		put(result, "json_chunk", json.substring(start, end));
		put(result, "summary", summary(project.data(), path, sha, "spine-json"));
		return result;
	}

	JsonValue validateProject(String requestedPath, String json) throws ToolFailure {
		checkCancelled();
		if ((requestedPath == null) == (json == null))
			throw new IllegalArgumentException("Provide exactly one of path or json.");
		JsonValue result = object();
		put(result, "valid", true);
		putValidationScope(result);
		if (requestedPath != null) {
			Path path = resolveReadableProject(requestedPath, false);
			byte[] bytes = readBytes(path);
			ParsedProject project = parseProject(path, bytes);
			put(result, "summary", summary(project.data(), path, sha256(bytes), format(path)));
			putWarnings(result, project.warnings());
		} else {
			ParsedProject project = parseJson(json);
			put(result, "summary", summary(project.data(), null,
				sha256(json.getBytes(StandardCharsets.UTF_8)), "spine-json"));
			putWarnings(result, project.warnings());
		}
		return result;
	}

	JsonValue writeProject(String requestedPath, String json, String expectedSha256,
		boolean overwrite, boolean createBackup) throws ToolFailure {
		checkCancelled();
		if (json == null) throw new IllegalArgumentException("json is required.");
		Path target = resolveWritableJson(requestedPath);
		ParsedProject project = parseJson(json);
		String formatted = project.root().prettyPrint(OutputType.json, 120) + "\n";
		byte[] bytes = formatted.getBytes(StandardCharsets.UTF_8);
		checkSize(bytes.length);
		checkCancelled();

		boolean existed = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
		if (existed) {
			if (Files.isSymbolicLink(target)) throw failure("Destination must not be a symbolic link: " + target);
			Path realTarget = realPathWithinRoot(target);
			if (!Files.isRegularFile(realTarget, LinkOption.NOFOLLOW_LINKS))
				throw failure("Destination is not a file: " + target);
			target = realTarget;
			String currentSha = sha256(readBytes(realTarget));
			if (expectedSha256 == null && !overwrite)
				throw failure("Destination exists. Read it first and pass expected_sha256, or explicitly set overwrite=true.");
			if (expectedSha256 != null && !currentSha.equalsIgnoreCase(expectedSha256))
				throw failure("Destination changed since it was read (SHA-256 mismatch). Read it again before writing.");
		} else if (expectedSha256 != null) {
			throw failure("Destination does not exist, so expected_sha256 cannot be satisfied.");
		}

		Path backup = existed && createBackup
			? target.resolveSibling(target.getFileName() + ".bak") : null;
		if (backup != null && Files.isSymbolicLink(backup))
			throw failure("Backup destination must not be a symbolic link: " + backup);
		Path guardedTarget = target;
		Path guardedBackup = backup;
		try {
			atomicWrite(target, bytes, existed, () -> {
				byte[] previousBytes = verifyDestinationBeforeWrite(
					guardedTarget, existed, expectedSha256);
				if (guardedBackup == null) return;
				atomicWrite(guardedBackup, previousBytes, true, null);
				verifyDestinationBeforeWrite(guardedTarget, true, sha256(previousBytes));
			});
		} catch (FileAlreadyExistsException error) {
			throw failure("Destination was created by another process before the write completed: " + target, error);
		} catch (IOException error) {
			throw failure("Unable to write project: " + message(error), error);
		}

		String writtenSha = sha256(bytes);
		JsonValue result = object();
		put(result, "created", !existed);
		put(result, "path", target.toString());
		put(result, "sha256", writtenSha);
		if (backup != null) put(result, "backup_path", backup.toString());
		put(result, "summary", summary(project.data(), target, writtenSha, "spine-json"));
		putWarnings(result, project.warnings());
		return result;
	}

	private byte[] verifyDestinationBeforeWrite(Path target, boolean existed, String expectedSha256)
		throws ToolFailure {
		if (!existed) {
			if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
				throw failure("Destination was created by another process before the write completed: " + target);
			return null;
		}
		if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
			throw failure("Destination changed before the write completed: " + target);
		byte[] currentBytes = readBytes(target);
		if (expectedSha256 != null && !sha256(currentBytes).equalsIgnoreCase(expectedSha256))
			throw failure("Destination changed since it was read (SHA-256 mismatch). Read it again before writing.");
		return currentBytes;
	}

	private ParsedProject parseProject(Path path, byte[] bytes) throws ToolFailure {
		if (isJson(path)) return parseJson(decodeJson(bytes));
		checkCancelled();
		try {
			ByteArrayInputStream input = new ByteArrayInputStream(bytes);
			SkeletonBinary loader = new SkeletonBinary(new HeadlessAttachmentLoader());
			loader.setScale(1f);
			SkeletonData data = loader.readSkeletonData(input);
			List<String> warnings = SkeletonStructureValidator.validate(data);
			checkCancelled();
			return new ParsedProject(data, null, warnings);
		} catch (RuntimeException error) {
			throw failure("Invalid Spine binary project: " + message(error), error);
		}
	}

	private ParsedProject parseJson(String json) throws ToolFailure {
		if (json == null) throw new IllegalArgumentException("json is required.");
		checkSize(json.getBytes(StandardCharsets.UTF_8).length);
		checkCancelled();
		try {
			StrictJson.validate(json);
			checkCancelled();
			JsonValue root = new JsonReader().parse(json);
			if (root == null || !root.isObject()) throw new IllegalArgumentException("JSON root must be an object.");
			validateFiniteJsonNumbers(root);
			SkeletonStructureValidator.validateJson(root);
			SkeletonJson loader = new SkeletonJson(new HeadlessAttachmentLoader());
			loader.setScale(1f);
			SkeletonData data = loader.readSkeletonData(root);
			List<String> warnings = SkeletonStructureValidator.validate(data);
			checkCancelled();
			return new ParsedProject(data, root, warnings);
		} catch (RuntimeException error) {
			throw failure("Invalid Spine JSON project: " + message(error), error);
		}
	}

	private JsonValue summary(SkeletonData data, Path path, String sha, String format) throws ToolFailure {
		checkCancelled();
		JsonValue result = object();
		putValidationScope(result);
		if (path != null) put(result, "path", path.toString());
		put(result, "format", format);
		put(result, "sha256", sha);
		put(result, "spine_version", data.getVersion());
		put(result, "skeleton_hash", data.getHash());
		putFinite(result, "fps", data.getFps());
		put(result, "images_path", data.getImagesPath());
		put(result, "audio_path", data.getAudioPath());

		JsonValue bounds = object();
		putFinite(bounds, "x", data.getX());
		putFinite(bounds, "y", data.getY());
		putFinite(bounds, "width", data.getWidth());
		putFinite(bounds, "height", data.getHeight());
		put(result, "bounds", bounds);

		int attachmentCount = 0;
		for (Skin skin : data.getSkins()) {
			checkCancelled();
			attachmentCount += skin.getAttachments().size;
		}
		int timelineCount = 0;
		for (Animation animation : data.getAnimations()) {
			checkCancelled();
			timelineCount += animation.getTimelines().size;
		}
		JsonValue counts = object();
		put(counts, "bones", data.getBones().size);
		put(counts, "slots", data.getSlots().size);
		put(counts, "skins", data.getSkins().size);
		put(counts, "attachments", attachmentCount);
		put(counts, "constraints", data.getConstraints().size);
		put(counts, "events", data.getEvents().size);
		put(counts, "animations", data.getAnimations().size);
		put(counts, "timelines", timelineCount);
		put(result, "counts", counts);

		JsonValue bones = array();
		data.getBones().forEach(bone -> bones.addChild(new JsonValue(bone.getName())));
		put(result, "bones", bones);
		JsonValue slots = array();
		data.getSlots().forEach(slot -> slots.addChild(new JsonValue(slot.getName())));
		put(result, "slots", slots);
		JsonValue skins = array();
		data.getSkins().forEach(skin -> skins.addChild(new JsonValue(skin.getName())));
		put(result, "skins", skins);

		JsonValue constraints = array();
		for (ConstraintData<?, ?> constraint : data.getConstraints()) {
			JsonValue item = object();
			put(item, "name", constraint.getName());
			put(item, "type", constraintType(constraint));
			constraints.addChild(item);
		}
		put(result, "constraints", constraints);

		JsonValue animations = array();
		for (Animation animation : data.getAnimations()) {
			JsonValue item = object();
			put(item, "name", animation.getName());
			putFinite(item, "duration", animation.getDuration());
			put(item, "timelines", animation.getTimelines().size);
			animations.addChild(item);
		}
		put(result, "animations", animations);
		return result;
	}

	private static void putValidationScope(JsonValue result) {
		put(result, "structure_valid", true);
		put(result, "textures_checked", false);
		put(result, "render_checked", false);
	}

	private static void putWarnings(JsonValue result, List<String> warnings) {
		JsonValue values = array();
		for (String warning : warnings) values.addChild(new JsonValue(warning));
		put(result, "warnings", values);
	}

	private static void validateFiniteJsonNumbers(JsonValue root) throws ToolFailure {
		ArrayDeque<JsonValue> pending = new ArrayDeque<>();
		pending.push(root);
		while (!pending.isEmpty()) {
			checkCancelled();
			JsonValue value = pending.pop();
			if (value.isNumber() && !Double.isFinite(value.asDouble()))
				throw new IllegalArgumentException("Spine JSON numbers must be finite.");
			for (JsonValue child = value.child; child != null; child = child.next) pending.push(child);
		}
	}

	private Path resolveReadableProject(String requestedPath, boolean jsonOnly) throws ToolFailure {
		Path path = resolve(requestedPath);
		String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (jsonOnly ? !lower.endsWith(".json") : !(lower.endsWith(".json") || lower.endsWith(".skel")))
			throw failure(jsonOnly ? "Expected a .json Spine project." : "Expected a .json or .skel Spine project.");
		Path real = realPathWithinRoot(path);
		if (!Files.isRegularFile(real)) throw failure("Project file not found: " + path);
		return real;
	}

	private Path resolveReadableDirectory(String requestedPath) throws ToolFailure {
		Path path = resolve(requestedPath);
		Path real = realPathWithinRoot(path);
		if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS))
			throw failure("Project directory not found: " + path);
		return real;
	}

	private Path resolveWritableJson(String requestedPath) throws ToolFailure {
		Path path = resolve(requestedPath);
		if (!isJson(path)) throw failure("MCP writes Spine JSON only; destination must end in .json.");
		if (!path.startsWith(root)) throw failure("Path is outside the configured MCP root.");
		Path parent = path.getParent();
		if (parent == null) throw failure("Destination must have a parent directory.");
		try {
			Path realParent = parent.toRealPath();
			if (!realParent.startsWith(root)) throw failure("Destination is outside the configured MCP root.");
			if (!Files.isDirectory(realParent)) throw failure("Destination parent is not a directory.");
			Path resolved = realParent.resolve(path.getFileName()).normalize();
			if (Files.exists(resolved)) realPathWithinRoot(resolved);
			return resolved;
		} catch (IOException error) {
			throw failure("Destination parent does not exist: " + parent, error);
		}
	}

	private Path resolve(String requestedPath) throws ToolFailure {
		if (requestedPath == null || requestedPath.isBlank()) throw new IllegalArgumentException("path is required.");
		if (requestedPath.indexOf('\0') >= 0) throw failure("Path contains a NUL character.");
		try {
			Path path = Path.of(requestedPath);
			return (path.isAbsolute() ? path : root.resolve(path)).toAbsolutePath().normalize();
		} catch (RuntimeException error) {
			throw failure("Invalid path: " + requestedPath, error);
		}
	}

	private Path realPathWithinRoot(Path path) throws ToolFailure {
		try {
			Path real = path.toRealPath();
			if (!real.startsWith(root)) throw failure("Path is outside the configured MCP root.");
			return real;
		} catch (IOException error) {
			throw failure("Project file not found: " + path, error);
		}
	}

	private byte[] readBytes(Path path) throws ToolFailure {
		try {
			checkCancelled();
			long size = Files.size(path);
			checkSize(size);
			byte[] bytes = Files.readAllBytes(path);
			checkCancelled();
			checkSize(bytes.length);
			return bytes;
		} catch (IOException error) {
			throw failure("Unable to read project: " + message(error), error);
		}
	}

	private static String decodeJson(byte[] bytes) throws ToolFailure {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException error) {
			throw failure("Spine JSON must be valid UTF-8.", error);
		}
	}

	private void checkSize(long size) throws ToolFailure {
		if (size > MAX_PROJECT_BYTES)
			throw failure("Project exceeds the MCP limit of " + MAX_PROJECT_BYTES + " bytes.");
	}

	private static void atomicWrite(Path target, byte[] bytes, boolean replaceExisting, WriteGuard guard)
		throws IOException, ToolFailure {
		Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName() + ".", ".tmp");
		try {
			checkCancelled();
			Files.write(temporary, bytes);
			checkCancelled();
			if (guard != null) guard.check();
			checkCancelled();
			if (replaceExisting) {
				try {
					Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException ignored) {
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} else {
				Files.move(temporary, target);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	@FunctionalInterface
	private interface WriteGuard {
		void check() throws IOException, ToolFailure;
	}

	private static boolean isJson(Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
	}

	private static boolean isProject(Path path) {
		String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return lower.endsWith(".json") || lower.endsWith(".skel");
	}

	private String relativePath(Path path) {
		if (root.equals(path)) return ".";
		return root.relativize(path).toString().replace('\\', '/');
	}

	private static String format(Path path) {
		return isJson(path) ? "spine-json" : "spine-binary";
	}

	private static String constraintType(ConstraintData<?, ?> constraint) {
		String name = constraint.getClass().getSimpleName();
		return name.endsWith("ConstraintData") ? name.substring(0, name.length() - "ConstraintData".length()) : name;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable.", impossible);
		}
	}

	static String message(Throwable error) {
		StringBuilder result = new StringBuilder();
		var seen = new IdentityHashMap<Throwable, Boolean>();
		String previous = null;
		int depth = 0;
		while (error != null && depth < 8 && seen.put(error, Boolean.TRUE) == null) {
			String detail = error.getMessage();
			if (detail == null || detail.isBlank()) detail = error.getClass().getSimpleName();
			if (detail.length() > 448) detail = detail.substring(0, 445) + "...";
			detail = detail.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
			if (!detail.equals(previous)) {
				if (result.length() != 0) result.append(" -> ").append(error.getClass().getSimpleName()).append(": ");
				result.append(detail);
				previous = detail;
			}
			error = error.getCause();
			depth++;
		}
		if (error != null) result.append(" -> [additional causes omitted]");
		return result.length() <= 4096 ? result.toString() : result.substring(0, 4093) + "...";
	}

	private static JsonValue object() {
		return new JsonValue(JsonValue.ValueType.object);
	}

	private static JsonValue array() {
		return new JsonValue(JsonValue.ValueType.array);
	}

	private static void put(JsonValue object, String name, JsonValue value) {
		object.addChild(name, value == null ? new JsonValue(JsonValue.ValueType.nullValue) : value);
	}

	private static void put(JsonValue object, String name, String value) {
		put(object, name, value == null ? null : new JsonValue(value));
	}

	private static void put(JsonValue object, String name, int value) {
		put(object, name, new JsonValue((long)value));
	}

	private static void put(JsonValue object, String name, long value) {
		put(object, name, new JsonValue(value));
	}

	private static void put(JsonValue object, String name, float value) {
		put(object, name, new JsonValue((double)value));
	}

	private static void putFinite(JsonValue object, String name, float value) throws ToolFailure {
		if (!Float.isFinite(value)) throw failure("Invalid Spine project: " + name + " must be finite.");
		put(object, name, value);
	}

	private static void put(JsonValue object, String name, boolean value) {
		put(object, name, new JsonValue(value));
	}

	private static ToolFailure failure(String message) {
		return new ToolFailure(message, null);
	}

	private static ToolFailure failure(String message, Throwable cause) {
		return new ToolFailure(message, cause);
	}

	private static void checkCancelled() throws ToolFailure {
		if (Thread.currentThread().isInterrupted()) throw failure("Tool call cancelled.");
	}

	private record ParsedProject(SkeletonData data, JsonValue root, List<String> warnings) {}

	static final class ToolFailure extends Exception {
		private static final long serialVersionUID = 1L;

		ToolFailure(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private static final class HeadlessAttachmentLoader implements AttachmentLoader {
		@Override
		public RegionAttachment newRegionAttachment(Skin skin, String placeholder, String name, String path,
			Sequence sequence) {
			RegionAttachment attachment = new RegionAttachment(name, sequence);
			attachment.setPath(path);
			return attachment;
		}

		@Override
		public MeshAttachment newMeshAttachment(Skin skin, String placeholder, String name, String path,
			Sequence sequence) {
			MeshAttachment attachment = new MeshAttachment(name, sequence);
			attachment.setPath(path);
			return attachment;
		}

		@Override
		public BoundingBoxAttachment newBoundingBoxAttachment(Skin skin, String placeholder, String name) {
			return new BoundingBoxAttachment(name);
		}

		@Override
		public ClippingAttachment newClippingAttachment(Skin skin, String placeholder, String name) {
			return new ClippingAttachment(name);
		}

		@Override
		public PathAttachment newPathAttachment(Skin skin, String placeholder, String name) {
			return new PathAttachment(name);
		}

		@Override
		public PointAttachment newPointAttachment(Skin skin, String placeholder, String name) {
			return new PointAttachment(name);
		}
	}
}
