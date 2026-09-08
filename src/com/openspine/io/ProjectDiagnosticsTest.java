package com.openspine.io;

import java.nio.file.Files;
import java.nio.file.Path;

import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.openspine.model.SkeletonDocument;

/** Headless coverage for project preflight diagnostics. */
public final class ProjectDiagnosticsTest {
	public static void main(String[] args) throws Exception {
		Path directory = Files.createTempDirectory("openspine-diagnostics-");
		try {
			SkeletonData data = new SkeletonData();
			data.setName("diagnostic");
			data.setVersion("4.2.99");
			data.setAudioPath("audio");
			BoneData root = new BoneData(0, "root", null);
			root.getSetupPose().setInherit(BoneData.Inherit.normal);
			data.getBones().add(root);
			EventData event = new EventData("step");
			event.setAudioPath("missing.wav");
			data.getEvents().add(event);
			SkeletonDocument document = new SkeletonDocument();
			document.data = data;
			document.skeleton = new Skeleton(data);
			document.filePath = directory.resolve("hero.json").toString();
			document.savePath = document.filePath;
			ProjectDiagnostics.Report report = ProjectDiagnostics.inspect(document);
			assertContains(report, "输入版本为 4.2.99", "version mismatch is reported");
			assertContains(report, "覆盖源文件的数据损失风险", "unsafe source overwrite is reported");
			assertSeverity(report, "覆盖源文件的数据损失风险", ProjectDiagnostics.Severity.ERROR,
				"unsafe source overwrite blocks a clean diagnostic report");
			assertContains(report, "音频文件不存在", "missing event audio is reported");
			if (report.fixable() != 2)
				throw new AssertionError("version and unsafe save target must both be fixable: " + report.issues());
			java.util.List<String> preview = ProjectDiagnostics.previewFixes(document);
			if (preview.size() != 2 || preview.stream().noneMatch(action -> action.contains("4.3.0"))
				|| preview.stream().noneMatch(action -> action.contains("保存")))
				throw new AssertionError("fix preview must describe both deterministic changes: " + preview);
			long revision = document.revision;
			ProjectDiagnostics.FixResult fixed = ProjectDiagnostics.fixAll(document);
			if (!fixed.changed() || fixed.actions().size() != 2 || !"4.3.0".equals(data.getVersion())
				|| document.savePath != null || document.revision != revision + 1)
				throw new AssertionError("fixAll must atomically convert the version and clear the unsafe target");
			assertNotContains(ProjectDiagnostics.inspect(document), "覆盖源文件的数据损失风险",
				"fixed document no longer has an unsafe overwrite target");
			ProjectDiagnostics.restoreFixes(document, fixed.before());
			if (!"4.2.99".equals(data.getVersion()) || !document.filePath.equals(document.savePath))
				throw new AssertionError("undo state must restore the original version and save target");
			ProjectDiagnostics.restoreFixes(document, fixed.after());
			if (!"4.3.0".equals(data.getVersion()) || document.savePath != null)
				throw new AssertionError("redo state must restore the complete fixed state");

			data.setVersion("4.2.99");
			document.savePath = directory.resolve("hero-4.3.json").toString();
			ProjectDiagnostics.Report copyReport = ProjectDiagnostics.inspect(document);
			assertNotContains(copyReport, "覆盖源文件的数据损失风险",
				"a distinct conversion target does not report an overwrite");
			ProjectDiagnostics.FixResult conversionOnly = ProjectDiagnostics.fixAll(document);
			if (conversionOnly.actions().size() != 1 || !document.savePath.endsWith("hero-4.3.json"))
				throw new AssertionError("safe conversion targets must remain associated");

			data.setVersion("4.3.0");
			ProjectDiagnostics.FixResult noOp = ProjectDiagnostics.fixAll(document);
			if (noOp.changed() || !noOp.actions().isEmpty())
				throw new AssertionError("a clean document must not produce a repair transaction");

			data.setVersion(null);
			document.savePath = document.filePath;
			ProjectDiagnostics.Report unknownReport = ProjectDiagnostics.inspect(document);
			assertContains(unknownReport, "没有声明 Spine 版本", "unknown versions require conversion");
			assertContains(unknownReport, "覆盖源文件的数据损失风险",
				"unknown versions receive the same overwrite protection");
			System.out.println("PROJECT DIAGNOSTICS TEST PASSED");
		} finally { Files.deleteIfExists(directory); }
	}

	private static void assertContains(ProjectDiagnostics.Report report, String text, String message) {
		if (report.issues().stream().noneMatch(issue -> issue.message().contains(text)))
			throw new AssertionError(message + ": " + report.issues());
	}

	private static void assertNotContains(ProjectDiagnostics.Report report, String text, String message) {
		if (report.issues().stream().anyMatch(issue -> issue.message().contains(text)))
			throw new AssertionError(message + ": " + report.issues());
	}

	private static void assertSeverity(ProjectDiagnostics.Report report, String text,
		ProjectDiagnostics.Severity severity, String message) {
		if (report.issues().stream().noneMatch(issue -> issue.severity() == severity
			&& issue.message().contains(text))) throw new AssertionError(message + ": " + report.issues());
	}

	private ProjectDiagnosticsTest() {}
}
