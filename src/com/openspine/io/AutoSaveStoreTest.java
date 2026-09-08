package com.openspine.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.badlogic.gdx.files.FileHandle;
import com.openspine.model.SkeletonDocument;

/** Headless coverage for autosave rotation, metadata, and cleanup. */
public final class AutoSaveStoreTest {
	public static void main(String[] args) throws Exception {
		Path root = Files.createTempDirectory("openspine-autosave-test-");
		String previous = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		try {
			System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, root.toString());
			Path unrelated = root.resolve("keep-me.txt");
			Files.writeString(unrelated, "unrelated");
			for (int index = 0; index < 4; index++) {
				AutoSaveStore.SnapshotDocument document = new AutoSaveStore.SnapshotDocument("hero-" + index,
					"{\"skeleton\":{\"spine\":\"4.3.0\"},\"bones\":[{\"name\":\"root\"}]}", "source.json", "saved.json",
					root.toString(), "");
				String editorState = "{\"version\":11,\"activeSkeleton\":0,\"showAllSkeletons\":"
					+ (index % 2 == 0) + ",\"skeletons\":[{\"name\":\"hero-" + index
					+ "\",\"defaultAnimationMix\":0.25}]}";
				AutoSaveStore.write(new AutoSaveStore.Snapshot(List.of(document), 0,
					index % 2 == 0, editorState));
				Thread.sleep(2L);
			}
			AutoSaveStore.Session latest = AutoSaveStore.latest();
			if (latest == null || latest.documents().size() != 1
				|| !latest.documents().get(0).name().startsWith("hero-"))
				throw new AssertionError("latest autosave session metadata is readable");
			AutoSaveStore.StoredDocument stored = latest.documents().get(0);
			SkeletonDocument loaded = SkeletonLoader.loadAutosaveSnapshot(new SkeletonDocument(),
				new FileHandle(stored.jsonPath().toFile()), stored.filePath(), stored.savePath(),
				stored.resourceDirectory(), stored.atlasFileName());
			loaded.animationState = new com.esotericsoftware.spine.AnimationState(
				new com.esotericsoftware.spine.AnimationStateData(loaded.data));
			if (!loaded.dirty || loaded.data.findBone("root") == null)
				throw new AssertionError("autosave JSON restores as a dirty editable document");
			SkeletonDocument active = new SkeletonDocument();
			com.openspine.model.SkeletonProject project = new com.openspine.model.SkeletonProject(active);
			project.replaceAll(List.of(new com.openspine.model.SkeletonProject.LoadedSkeleton(
				latest.documents().get(0).name(), loaded)));
			ProjectStateStore.LoadOutcome state = ProjectStateStore.restoreEditorState(
				latest.editorStatePath(), project, false);
			if (state.result() != ProjectStateStore.LoadResult.RESTORED || state.showAllSkeletons())
				throw new AssertionError("autosave editor state restores independently of source fingerprints");
			project.close();
			try (var sessions = Files.list(root)) {
				long count = sessions.filter(Files::isDirectory)
					.filter(path -> path.getFileName().toString().startsWith("session-")).count();
				if (count != 3L) throw new AssertionError("autosave rotation must retain exactly three sessions: " + count);
			}
			AutoSaveStore.discardAll();
			try (var entries = Files.list(root)) {
				if (!Files.isRegularFile(unrelated) || !"unrelated".equals(Files.readString(unrelated)))
					throw new AssertionError("discardAll must preserve unrelated files");
				long sessions = entries.filter(path -> path.getFileName().toString().startsWith("session-")).count();
				if (sessions != 0L) throw new AssertionError("discardAll removes autosave sessions");
			}
			System.out.println("AUTOSAVE STORE TEST PASSED");
		} finally {
			if (previous == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY);
			else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, previous);
			try (var paths = Files.walk(root)) {
				for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	private AutoSaveStoreTest() {}
}
