package com.openspine.io;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared file naming and rollback support for multi-file image exports. */
final class FrameSequenceTransaction {
	private static final Pattern NUMBERED_STEM = Pattern.compile("^(.*[_-])(\\d+)$");
	private static final String TEMP_PREFIX = ".openspine-sequence-";

	@FunctionalInterface
	interface FrameWriter {
		void write(int sequenceIndex, Path stagedTarget) throws IOException;
	}

	static List<Path> targets(Path requestedTarget, int startFrame, int endFrame,
		String... acceptedExtensions) {
		if (requestedTarget == null) throw new IllegalArgumentException("序列目标路径不能为空");
		if (startFrame < 0 || endFrame < startFrame)
			throw new IllegalArgumentException("序列帧范围无效");
		if (acceptedExtensions == null || acceptedExtensions.length == 0)
			throw new IllegalArgumentException("序列文件扩展名不能为空");
		Path target = requestedTarget.toAbsolutePath().normalize();
		Path fileNamePath = target.getFileName();
		if (fileNamePath == null) throw new IllegalArgumentException("序列目标路径必须包含文件名");
		String fileName = fileNamePath.toString();
		String lower = fileName.toLowerCase(Locale.ROOT);
		String extension = null;
		for (String accepted : acceptedExtensions) {
			if (accepted == null || accepted.isBlank()) continue;
			String candidate = accepted.startsWith(".") ? accepted : "." + accepted;
			if (lower.endsWith(candidate.toLowerCase(Locale.ROOT))) {
				extension = fileName.substring(fileName.length() - candidate.length());
				break;
			}
		}
		if (extension == null) throw new IllegalArgumentException("序列目标文件扩展名无效：" + fileName);

		String stem = fileName.substring(0, fileName.length() - extension.length());
		if (stem.isBlank()) throw new IllegalArgumentException("序列文件名前缀不能为空");
		int digits = Math.max(4, decimalDigits(endFrame));
		String prefix = stem + "_";
		Matcher numbered = NUMBERED_STEM.matcher(stem);
		if (numbered.matches()) {
			try {
				if (Integer.parseInt(numbered.group(2)) == startFrame) {
					prefix = numbered.group(1);
					digits = Math.max(numbered.group(2).length(), decimalDigits(endFrame));
				}
			} catch (NumberFormatException ignored) {
				// An oversized numeric suffix is part of the prefix rather than a frame template.
			}
		}

		Path parent = target.getParent();
		if (parent == null) throw new IllegalArgumentException("序列目标路径缺少父目录");
		List<Path> result = new ArrayList<>(endFrame - startFrame + 1);
		for (int frame = startFrame; frame <= endFrame; frame++) {
			String number = String.format(Locale.ROOT, "%0" + digits + "d", frame);
			result.add(parent.resolve(prefix + number + extension));
		}
		return List.copyOf(result);
	}

	static int existingTargetCount(List<Path> targets) {
		validateTargets(targets);
		int count = 0;
		for (Path target : targets)
			if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) count++;
		return count;
	}

	static void writeAtomic(List<Path> targets, boolean replaceExisting, FrameWriter writer)
		throws IOException {
		validateTargets(targets);
		if (writer == null) throw new IllegalArgumentException("序列帧写入器不能为空");
		Path parent = targets.get(0).getParent();
		if (parent == null || !Files.isDirectory(parent))
			throw new IOException("序列目标目录不存在：" + parent);
		for (Path target : targets) {
			if (!parent.equals(target.getParent()))
				throw new IllegalArgumentException("序列目标必须位于同一目录");
			if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
			if (!Files.isRegularFile(target)) throw new IOException("序列目标不是普通文件：" + target);
			if (!replaceExisting) throw new FileAlreadyExistsException(target.toString());
		}

		Path transaction = Files.createTempDirectory(parent, TEMP_PREFIX);
		Path stagedDirectory = transaction.resolve("staged");
		Path backupDirectory = transaction.resolve("backup");
		List<Path> staged = new ArrayList<>(targets.size());
		List<Path> backups = new ArrayList<>(targets.size());
		boolean[] installed = new boolean[targets.size()];
		boolean[] backedUp = new boolean[targets.size()];
		Throwable failure = null;
		try {
			Files.createDirectory(stagedDirectory);
			Files.createDirectory(backupDirectory);
			for (int index = 0; index < targets.size(); index++) {
				Path stagedTarget = stagedDirectory.resolve(targets.get(index).getFileName().toString());
				Path backupTarget = backupDirectory.resolve(String.format(Locale.ROOT, "%05d.bak", index));
				staged.add(stagedTarget);
				backups.add(backupTarget);
				writer.write(index, stagedTarget);
				if (!Files.isRegularFile(stagedTarget))
					throw new IOException("序列第 " + index + " 帧没有生成有效文件");
			}

			for (int index = 0; index < targets.size(); index++) {
				Path target = targets.get(index);
				if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
					if (!Files.isRegularFile(target))
						throw new IOException("提交时序列目标不是普通文件：" + target);
					if (!replaceExisting) throw new FileAlreadyExistsException(target.toString());
					Files.move(target, backups.get(index));
					backedUp[index] = true;
				}
				Files.move(staged.get(index), target);
				installed[index] = true;
			}
		} catch (IOException | RuntimeException error) {
			failure = error;
			rollback(targets, backups, installed, backedUp, error);
		}

		boolean recoveryBackupsRemain = failure != null && anyTrue(backedUp);
		try {
			cleanup(transaction, stagedDirectory, backupDirectory, staged, backups,
				!recoveryBackupsRemain);
		} catch (IOException cleanupError) {
			if (failure == null) failure = cleanupError;
			else failure.addSuppressed(cleanupError);
		}
		if (recoveryBackupsRemain)
			failure.addSuppressed(new IOException("原文件备份保留在：" + backupDirectory));
		if (failure instanceof IOException io) throw io;
		if (failure instanceof RuntimeException runtime) throw runtime;
	}

	/** Commits files that were rendered into a staging directory without exposing a partial sequence. */
	static void commitStaged(List<Path> targets, List<Path> staged, boolean replaceExisting)
		throws IOException {
		validateTargets(targets);
		if (staged == null || staged.size() != targets.size())
			throw new IllegalArgumentException("暂存序列数量与目标不一致");
		Path parent = targets.get(0).getParent();
		if (parent == null || !Files.isDirectory(parent)) throw new IOException("序列目标目录不存在：" + parent);
		for (int index = 0; index < targets.size(); index++) {
			Path stagedPath = staged.get(index);
			if (stagedPath == null || !Files.isRegularFile(stagedPath))
				throw new IOException("序列第 " + index + " 帧没有生成有效文件");
			if (!parent.equals(targets.get(index).getParent()))
				throw new IllegalArgumentException("序列目标必须位于同一目录");
			Path target = targets.get(index);
			if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
				if (!Files.isRegularFile(target)) throw new IOException("序列目标不是普通文件：" + target);
				if (!replaceExisting) throw new FileAlreadyExistsException(target.toString());
			}
		}

		Path transaction = Files.createTempDirectory(parent, TEMP_PREFIX + "commit-");
		Path backupDirectory = transaction.resolve("backup");
		Files.createDirectory(backupDirectory);
		List<Path> backups = new ArrayList<>(targets.size());
		for (int index = 0; index < targets.size(); index++)
			backups.add(backupDirectory.resolve(String.format(Locale.ROOT, "%05d.bak", index)));
		boolean[] installed = new boolean[targets.size()];
		boolean[] backedUp = new boolean[targets.size()];
		Throwable failure = null;
		try {
			for (int index = 0; index < targets.size(); index++) {
				Path target = targets.get(index);
				Path backup = backups.get(index);
				if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
					Files.move(target, backup);
					backedUp[index] = true;
				}
				Files.move(staged.get(index), target);
				installed[index] = true;
			}
		} catch (IOException | RuntimeException error) {
			failure = error;
			rollback(targets, backups, installed, backedUp, error);
		}
		boolean recoveryBackupsRemain = failure != null && anyTrue(backedUp);
		try {
			cleanup(transaction, transaction.resolve("staged"), backupDirectory,
				List.of(), backups, !recoveryBackupsRemain);
		} catch (IOException cleanupError) {
			if (failure == null) failure = cleanupError;
			else failure.addSuppressed(cleanupError);
		}
		if (recoveryBackupsRemain) failure.addSuppressed(new IOException("原文件备份保留在：" + backupDirectory));
		if (failure instanceof IOException io) throw io;
		if (failure instanceof RuntimeException runtime) throw runtime;
	}

	private static void rollback(List<Path> targets, List<Path> backups, boolean[] installed,
		boolean[] backedUp, Throwable failure) {
		for (int index = targets.size() - 1; index >= 0; index--) {
			Path target = targets.get(index);
			if (installed[index]) {
				try {
					Files.deleteIfExists(target);
					installed[index] = false;
				} catch (IOException error) {
					failure.addSuppressed(error);
				}
			}
			if (!backedUp[index]) continue;
			try {
				if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
					throw new IOException("无法恢复原文件，目标仍存在：" + target);
				Files.move(backups.get(index), target);
				backedUp[index] = false;
			} catch (IOException error) {
				failure.addSuppressed(error);
			}
		}
	}

	private static void cleanup(Path transaction, Path stagedDirectory, Path backupDirectory,
		List<Path> staged, List<Path> backups, boolean deleteBackups) throws IOException {
		IOException failure = null;
		for (Path path : staged) failure = delete(path, failure);
		if (deleteBackups) for (Path path : backups) failure = delete(path, failure);
		failure = delete(stagedDirectory, failure);
		if (deleteBackups) {
			failure = delete(backupDirectory, failure);
			failure = delete(transaction, failure);
		}
		if (failure != null) throw failure;
	}

	private static IOException delete(Path path, IOException failure) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException error) {
			if (failure == null) failure = error;
			else failure.addSuppressed(error);
		}
		return failure;
	}

	private static void validateTargets(List<Path> targets) {
		if (targets == null || targets.isEmpty()) throw new IllegalArgumentException("序列目标不能为空");
		Set<Path> unique = new HashSet<>();
		for (Path target : targets) {
			if (target == null) throw new IllegalArgumentException("序列目标不能包含空路径");
			Path normalized = target.toAbsolutePath().normalize();
			if (!target.equals(normalized))
				throw new IllegalArgumentException("序列目标必须是规范化绝对路径：" + target);
			if (!unique.add(target)) throw new IllegalArgumentException("序列目标重复：" + target);
		}
	}

	private static int decimalDigits(int value) {
		return Integer.toString(value).length();
	}

	private static boolean anyTrue(boolean[] values) {
		for (boolean value : values) if (value) return true;
		return false;
	}

	private FrameSequenceTransaction() {}
}
