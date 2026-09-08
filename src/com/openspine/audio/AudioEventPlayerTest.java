package com.openspine.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.EventData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.openspine.model.SkeletonDocument;

/** Headless regression tests for Spine event audio playback. */
public final class AudioEventPlayerTest {
	public static void main(String[] args) throws Exception {
		resolvesProjectAudioPaths();
		playsOnlyDuringRealtimePlayback();
		System.out.println("ALL AUDIO EVENT PLAYER TESTS PASSED");
	}

	private static void resolvesProjectAudioPaths() {
		Path project = Path.of("build", "audio-test", "hero.json").toAbsolutePath();
		Path expected = project.getParent().resolve("audio").resolve("step.wav").normalize();
		assertEquals(expected, AudioEventPlayer.resolveAudioPath(project.toString(), null, "audio", "step.wav"),
			"relative event audio uses the saved project and configured audio directory");
		Path original = Path.of("build", "source", "hero.skel").toAbsolutePath();
		Path saveAs = Path.of("build", "saved-copy", "hero.skel").toAbsolutePath();
		assertEquals(original.getParent().resolve("audio").resolve("step.wav").normalize(),
			AudioEventPlayer.resolveAudioPath(saveAs.toString(), original.toString(), "audio", "step.wav"),
			"Save As does not detach audio from the currently loaded project resources");
		assertEquals(original.getParent().resolve("step.wav").normalize(),
			AudioEventPlayer.resolveAudioPath(null, original.toString(), null, "step.wav"),
			"an unsaved binary document resolves audio beside its source");
		Path absoluteAudio = Path.of("build", "shared-audio").toAbsolutePath();
		assertEquals(absoluteAudio.resolve("step.wav").normalize(),
			AudioEventPlayer.resolveAudioPath(null, "builtin:example", absoluteAudio.toString(), "step.wav"),
			"an absolute project audio directory does not require a disk-backed project");
		assertTrue(AudioEventPlayer.resolveAudioPath(null, "builtin:example", null, "step.wav") == null,
			"built-in projects do not guess a relative audio directory");
		assertEquals(saveAs.getParent().resolve("step.wav").normalize(),
			AudioEventPlayer.resolveAudioPath(saveAs.toString(), "new:hero", null, "step.wav"),
			"a newly saved project uses its first real save directory as the resource base");
	}

	private static void playsOnlyDuringRealtimePlayback() throws IOException {
		Path directory = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "audio-events-");
		try {
			Path audioDirectory = Files.createDirectories(directory.resolve("audio"));
			Path soundFile = Files.createFile(audioDirectory.resolve("step.wav"));
			SkeletonDocument doc = audioDocument(directory.resolve("hero.json"), "audio", "step.wav");
			FakeLoader loader = new FakeLoader();
			List<String> status = new ArrayList<>();
			AudioEventPlayer player = new AudioEventPlayer(loader,
				(message, error) -> status.add((error ? "error:" : "info:") + message));
			try {
				player.sync(doc);
				doc.seek(0.2f);
				assertTrue(loader.clip.playCount == 0, "ordinary editor seeks do not play event audio");

				doc.seek(0f);
				doc.setPlaying(true);
				player.beginPlaybackFrame(doc);
				try {
					doc.applyAnimation(0.2f);
				} finally {
					player.endPlaybackFrame(doc);
				}
				assertTrue(loader.loadCount == 1 && loader.loadedPath.equals(soundFile),
					"the first event loads its resolved audio file once");
				assertTrue(loader.clip.playCount == 1, "realtime animation playback emits event audio");
				assertNear(0.7f, loader.clip.volume, "event volume is forwarded");
				assertNear(1.5f, loader.clip.pitch, "playback speed controls audio pitch");
				assertNear(0.25f, loader.clip.pan, "event balance controls audio pan");

				int stopsBeforeAutomaticEnd = loader.clip.stopCount;
				doc.setPlaying(false);
				player.sync(doc);
				assertTrue(loader.clip.stopCount == stopsBeforeAutomaticEnd,
					"automatic completion does not truncate audio triggered on the final frame");
				player.stop();
				assertTrue(loader.clip.stopCount > stopsBeforeAutomaticEnd,
					"an explicit playback stop terminates active event audio");
				doc.seek(0f);
				doc.setPlaying(true);
				player.beginPlaybackFrame(doc);
				try {
					doc.applyAnimation(0.2f);
				} finally {
					player.endPlaybackFrame(doc);
				}
				assertTrue(loader.loadCount == 1 && loader.clip.playCount == 2,
					"later events reuse the cached audio clip");

				player.setEnabled(false);
				doc.seek(0f);
				player.beginPlaybackFrame(doc);
				try {
					doc.applyAnimation(0.2f);
				} finally {
					player.endPlaybackFrame(doc);
				}
				assertTrue(loader.clip.playCount == 2, "disabled event audio remains silent");
				assertTrue(status.isEmpty(), "valid event audio produces no error status");
			} finally {
				player.dispose();
			}
			assertTrue(loader.clip.disposed, "disposing the player releases cached audio clips");
		} finally {
			deleteRecursively(directory);
		}
	}

	private static SkeletonDocument audioDocument(Path projectPath, String projectAudioPath, String eventAudioPath) {
		SkeletonData data = new SkeletonData();
		data.getBones().add(new BoneData(0, "root", null));
		data.setAudioPath(projectAudioPath);
		EventData eventData = new EventData("step");
		eventData.setAudioPath(eventAudioPath);
		data.getEvents().add(eventData);
		Event event = new Event(0.1f, eventData);
		event.setVolume(0.7f);
		event.setBalance(0.25f);
		Animation.EventTimeline eventTimeline = new Animation.EventTimeline(1);
		eventTimeline.setFrame(0, event);
		Array<Animation.Timeline> timelines = new Array<>(true, 1, Animation.Timeline[]::new);
		timelines.add(eventTimeline);
		Animation animation = new Animation("audio");
		animation.setTimelines(timelines, new IntArray());
		animation.setDuration(1f);
		data.getAnimations().add(animation);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.animationState = new AnimationState(new AnimationStateData(data));
		doc.filePath = projectPath.toString();
		doc.playing = false;
		doc.playbackSpeed = 1.5f;
		assertTrue(doc.setAnimation(animation.getName()), "audio test animation can be selected");
		return doc;
	}

	private static void deleteRecursively(Path root) throws IOException {
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private static final class FakeLoader implements AudioEventPlayer.AudioClipLoader {
		final FakeClip clip = new FakeClip();
		Path loadedPath;
		int loadCount;

		@Override public AudioEventPlayer.AudioClip load(Path path) {
			loadedPath = path;
			loadCount++;
			return clip;
		}
	}

	private static final class FakeClip implements AudioEventPlayer.AudioClip {
		int playCount;
		int stopCount;
		float volume;
		float pitch;
		float pan;
		boolean disposed;

		@Override public void play(float volume, float pitch, float pan) {
			this.volume = volume;
			this.pitch = pitch;
			this.pan = pan;
			playCount++;
		}
		@Override public void stop() { stopCount++; }
		@Override public void dispose() { disposed = true; }
	}

	private static void assertEquals(Path expected, Path actual, String message) {
		if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNear(float expected, float actual, String message) {
		if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private AudioEventPlayerTest() {}
}
