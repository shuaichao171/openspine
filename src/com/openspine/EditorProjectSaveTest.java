package com.openspine;

import java.nio.file.Path;
import java.util.List;

import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Saving a snapshot must not mark later changes, renamed members or replacement documents as saved. */
public final class EditorProjectSaveTest {
	public static void main(String[] args) throws Exception {
		try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
			project.replaceAll(List.of(
				new SkeletonProject.LoadedSkeleton("one", EditorApp.blankProjectDocument("one")),
				new SkeletonProject.LoadedSkeleton("two", EditorApp.blankProjectDocument("two"))));
			var before = EditorApp.projectSaveState(project);
			project.switchTo(1);
			require(!before.equals(EditorApp.projectSaveState(project)), "switching the shared document facade invalidates an in-flight save");
			before = EditorApp.projectSaveState(project);
			project.rename(0, "renamed");
			require(EditorApp.requiresProjectRecovery(project), "unsaved membership requires project recovery before the first native save");
			require(!before.equals(EditorApp.projectSaveState(project)), "same-size renames invalidate the captured save");
			project.rename(0, "one");
			project.move(0, 1);
			require(!before.equals(EditorApp.projectSaveState(project)), "reordering invalidates the captured save");
			project.move(1, 0);
			project.documents().get(0).markDirty();
			require(!before.equals(EditorApp.projectSaveState(project)), "inactive document edits invalidate the captured save");
			var edited = EditorApp.projectSaveState(project);
			project.removeAt(0);
			project.add(new SkeletonProject.LoadedSkeleton("one", EditorApp.blankProjectDocument("one")), false);
			project.move(1, 0);
			require(!edited.equals(EditorApp.projectSaveState(project)), "same-count same-name replacements retain unsaved changes");
		}
		try (SkeletonProject recovered = new SkeletonProject(new SkeletonDocument())) {
			SkeletonDocument document = EditorApp.blankProjectDocument("recovered");
			document.filePath = "new:native-project-recovery";
			recovered.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("recovered", document)));
			require(!recovered.hasStructureChanges() && EditorApp.requiresProjectRecovery(recovered),
				"recovery retains project save routing after replaceAll resets membership history");
		}
		require(EditorApp.isManagedGenerationPath(Path.of("project/.openspine-generations/generation-1/skeleton.json")),
			"runtime export cannot mutate a committed generation");
		require(!EditorApp.isManagedGenerationPath(Path.of("project/skeleton.json")), "standalone exports remain available");
		require(DesktopLauncher.ensureNativeProjectExtension("hero").equals("hero.openspine-project.json"),
			"native projects use an unambiguous file suffix");
		require(DesktopLauncher.ensureNativeProjectExtension("openspine-project.json").equals("openspine-project.json"),
			"legacy extracted project names are retained");
		require(DesktopLauncher.ensureNativeProjectExtension("hero.openspine-project.json").equals("hero.openspine-project.json"),
			"native project suffix is not duplicated");
		java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("openspine-export-guard-");
		try {
			Path target = directory.resolve("hero.openspine-project.json");
			java.nio.file.Files.writeString(target, "saved project");
			var writer = EditorApp.class.getDeclaredMethod("writeDocument", Path.class);
			writer.setAccessible(true);
			try {
				writer.invoke(new EditorApp(), target);
				throw new AssertionError("direct runtime export must reject native manifests");
			} catch (java.lang.reflect.InvocationTargetException expected) {
				require(expected.getCause() instanceof IllegalArgumentException, "direct export is rejected before opening its output");
			}
			require(java.nio.file.Files.readString(target).equals("saved project"), "rejected direct export preserves the native file");
			java.nio.file.Files.delete(target);
		} finally { java.nio.file.Files.deleteIfExists(directory); }
		System.out.println("ALL EDITOR PROJECT SAVE TESTS PASSED");
	}
	private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
