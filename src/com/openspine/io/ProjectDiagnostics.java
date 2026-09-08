package com.openspine.io;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.Skin.SkinEntry;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.model.SkeletonDocument;

/** Project preflight plus conservative, reversible fixes for deterministic metadata problems. */
public final class ProjectDiagnostics {
	public enum Severity { ERROR, WARNING, INFO }
	public enum Fix { CONVERT_VERSION, CLEAR_UNSAFE_SAVE_TARGET }
	public record Issue(Severity severity, String message, Fix fix) {
		public Issue(Severity severity, String message) { this(severity, message, null); }
		public boolean fixable() { return fix != null; }
	}
	public record Report(List<Issue> issues) {
		public long errors() { return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count(); }
		public long warnings() { return issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count(); }
		public long fixable() { return issues.stream().filter(Issue::fixable).count(); }
	}
	public record FixState(String version, String savePath) {}
	public record FixResult(FixState before, FixState after, List<String> actions) {
		public FixResult { actions = actions == null ? List.of() : List.copyOf(actions); }
		public boolean changed() { return !actions.isEmpty(); }
	}

	public static Report inspect(SkeletonDocument document) {
		List<Issue> issues = new ArrayList<>();
		if (document == null || document.data == null) {
			issues.add(new Issue(Severity.ERROR, "没有可诊断的骨架数据"));
			return new Report(List.copyOf(issues));
		}

		String version = document.data.getVersion();
		if (version == null || version.isBlank())
			issues.add(new Issue(Severity.WARNING,
				"输入数据没有声明 Spine 版本；保存或导出骨架数据会显式转换为 4.3.0",
				Fix.CONVERT_VERSION));
		else if (SpineVersionPolicy.requiresConversion(version))
			issues.add(new Issue(Severity.WARNING, "输入版本为 " + version
				+ "；保存或导出骨架数据会显式转换为 Spine 4.3.0", Fix.CONVERT_VERSION));
		else issues.add(new Issue(Severity.INFO, "Spine 数据版本：" + version + "；保存目标：4.3.0"));
		if (SpineVersionPolicy.wouldOverwriteIncompatibleSource(version, document.filePath,
			document.savePath))
			issues.add(new Issue(Severity.ERROR,
				"当前保存路径与非 4.3 源文件相同，存在覆盖源文件的数据损失风险；保存时必须另存为副本",
				Fix.CLEAR_UNSAFE_SAVE_TARGET));

		Set<MeshAttachment> meshes = new HashSet<>();
		int visualAttachments = 0;
		for (var skin : document.data.getSkins()) for (SkinEntry entry : skin.getAttachments()) {
			Attachment attachment = entry.getAttachment();
			if (attachment instanceof RegionAttachment region) {
				visualAttachments++;
				checkSequence(issues, skin.getName(), entry.getPlaceholder(), region.getSequence());
			} else if (attachment instanceof MeshAttachment mesh) {
				visualAttachments++;
				meshes.add(mesh);
				checkSequence(issues, skin.getName(), entry.getPlaceholder(), mesh.getSequence());
			}
		}
		if (visualAttachments > 0 && (document.atlas == null || document.atlas.getRegions().size == 0))
			issues.add(new Issue(Severity.ERROR, "项目包含 " + visualAttachments + " 个图片附件，但没有可用图集或 loose 图片"));
		for (MeshAttachment mesh : meshes) {
			MeshAttachment source = mesh.getSourceMesh();
			if (source != null && !meshes.contains(source))
				issues.add(new Issue(Severity.ERROR, "Linked Mesh “" + mesh.getName() + "” 的源网格已失效"));
		}

		for (EventData event : document.data.getEvents()) {
			String audio = event.getAudioPath();
			if (audio == null || audio.isBlank()) continue;
			try {
				Path resolved = resolveAudioPath(document, audio);
				if (resolved == null || !Files.isRegularFile(resolved))
					issues.add(new Issue(Severity.WARNING, "事件 “" + event.getName() + "” 的音频文件不存在：" + audio));
			} catch (InvalidPathException error) {
				issues.add(new Issue(Severity.ERROR, "事件 “" + event.getName() + "” 的音频路径无效：" + audio));
			}
		}

		try { SkeletonExporter.toJson(document); }
		catch (RuntimeException error) {
			String detail = error.getMessage();
			issues.add(new Issue(Severity.ERROR, "保存预检失败：" + (detail == null ? error.getClass().getSimpleName() : detail)));
		}
		if (!WebpFrameExporter.isAvailable())
			issues.add(new Issue(Severity.INFO, "WebP 不可用：未检测到 ImageIO WebP 编码器"));
		if (!FfmpegAnimationExporter.isAvailable())
			issues.add(new Issue(Severity.INFO, "AWEBP/WebM/MOV 不可用：未检测到 ffmpeg"));
		if (issues.stream().noneMatch(issue -> issue.severity() != Severity.INFO))
			issues.add(new Issue(Severity.INFO, "未发现会阻止保存或导出的项目问题"));
		return new Report(List.copyOf(issues));
	}

	public static List<String> previewFixes(SkeletonDocument document) {
		Report report = inspect(document);
		List<String> actions = new ArrayList<>();
		if (report.issues().stream().anyMatch(issue -> issue.fix() == Fix.CONVERT_VERSION))
			actions.add("把内存中的数据版本标记为 Spine 4.3.0");
		if (report.issues().stream().anyMatch(issue -> issue.fix() == Fix.CLEAR_UNSAFE_SAVE_TARGET))
			actions.add("解除危险的原文件保存关联，下次保存时要求选择新文件");
		return List.copyOf(actions);
	}

	public static FixResult fixAll(SkeletonDocument document) {
		if (document == null || document.data == null) throw new IllegalArgumentException("没有可修复的骨架数据");
		FixState before = new FixState(document.data.getVersion(), document.savePath);
		List<String> actions = previewFixes(document);
		if (actions.isEmpty()) return new FixResult(before, before, actions);
		String originalVersion = document.data.getVersion();
		boolean unsafeSave = SpineVersionPolicy.wouldOverwriteIncompatibleSource(originalVersion,
			document.filePath, document.savePath);
		if (originalVersion == null || originalVersion.isBlank()
			|| SpineVersionPolicy.requiresConversion(originalVersion)) document.data.setVersion("4.3.0");
		if (unsafeSave) document.savePath = null;
		document.markDirty();
		return new FixResult(before, new FixState(document.data.getVersion(), document.savePath), actions);
	}

	public static void restoreFixes(SkeletonDocument document, FixState state) {
		if (document == null || document.data == null || state == null)
			throw new IllegalArgumentException("诊断修复历史无效");
		document.data.setVersion(state.version());
		document.savePath = state.savePath();
		document.markDirty();
	}

	private static void checkSequence(List<Issue> issues, String skin, String placeholder, Sequence sequence) {
		if (sequence == null) return;
		for (int index = 0; index < sequence.getRegions().length; index++)
			if (sequence.getRegions()[index] == null) {
				issues.add(new Issue(Severity.ERROR, "Sequence 缺失图片：" + skin + " / " + placeholder
					+ "，帧 " + index));
				return;
			}
	}

	private static Path resolveAudioPath(SkeletonDocument document, String eventAudioPath) {
		Path event = portablePath(eventAudioPath);
		if (event.isAbsolute()) return event.toAbsolutePath().normalize();
		String projectAudio = document.data.getAudioPath();
		if (projectAudio != null && !projectAudio.isBlank()) {
			Path configured = portablePath(projectAudio);
			if (configured.isAbsolute()) return configured.resolve(event).toAbsolutePath().normalize();
		}
		String project = usablePath(document.filePath) ? document.filePath
			: usablePath(document.savePath) ? document.savePath : null;
		if (project == null) return null;
		Path base = portablePath(project).toAbsolutePath().normalize().getParent();
		if (base == null) return null;
		if (projectAudio != null && !projectAudio.isBlank()) base = base.resolve(portablePath(projectAudio));
		return base.resolve(event).toAbsolutePath().normalize();
	}

	private static Path portablePath(String value) {
		return Path.of(value.replace('\\', File.separatorChar).replace('/', File.separatorChar));
	}

	private static boolean usablePath(String value) {
		return value != null && !value.isBlank() && !value.startsWith("builtin:") && !value.startsWith("new:");
	}

	private ProjectDiagnostics() {}
}
