package com.openspine.audio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.Event;
import com.openspine.model.SkeletonDocument;

/** Plays audio referenced by Spine event timelines without allowing editor scrubs to emit sound. */
public final class AudioEventPlayer implements Disposable {
	interface AudioClip {
		void play(float volume, float pitch, float pan);
		void stop();
		void dispose();
	}

	@FunctionalInterface
	interface AudioClipLoader {
		AudioClip load(Path path);
	}

	private final AudioClipLoader loader;
	private final BiConsumer<String, Boolean> status;
	private final Map<Path, AudioClip> clips = new HashMap<>();
	private final Set<Path> warnedPaths = new HashSet<>();
	private final Set<String> warnedMessages = new HashSet<>();
	private final AnimationState.AnimationStateAdapter listener = new AnimationState.AnimationStateAdapter() {
		@Override public void event(AnimationState.TrackEntry entry, Event event) {
			playEvent(event);
		}
	};

	private SkeletonDocument document;
	private Object documentData;
	private AnimationState animationState;
	private Animation animation;
	private String savePath;
	private String filePath;
	private String projectAudioPath;
	private boolean enabled = true;
	private boolean playbackFrame;
	private boolean playedThisFrame;
	private boolean wasActivelyPlaying;
	private boolean disposed;

	public AudioEventPlayer(BiConsumer<String, Boolean> status) {
		this(path -> {
			Sound sound = Gdx.audio.newSound(new FileHandle(path.toFile()));
			return new AudioClip() {
				@Override public void play(float volume, float pitch, float pan) {
					sound.play(volume, pitch, pan);
				}
				@Override public void stop() { sound.stop(); }
				@Override public void dispose() { sound.dispose(); }
			};
		}, status);
	}

	AudioEventPlayer(AudioClipLoader loader, BiConsumer<String, Boolean> status) {
		this.loader = Objects.requireNonNull(loader, "loader");
		this.status = status;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (!enabled) stop();
	}

	/** Opens the only interval in which AnimationState event callbacks may play audio. */
	public void beginPlaybackFrame(SkeletonDocument document) {
		playedThisFrame = false;
		sync(document);
		playbackFrame = isActivelyPlaying(document);
	}

	public void endPlaybackFrame(SkeletonDocument document) {
		playbackFrame = false;
		sync(document);
	}

	/** Synchronizes listener ownership and stops audio after pause, animation switch, or document replacement. */
	public void sync(SkeletonDocument nextDocument) {
		if (disposed) return;
		if (resourcesChanged(nextDocument)) {
			unbindState();
			clearResources();
			document = nextDocument;
			documentData = nextDocument == null ? null : nextDocument.data;
			savePath = nextDocument == null ? null : nextDocument.savePath;
			filePath = nextDocument == null ? null : nextDocument.filePath;
			projectAudioPath = nextDocument == null || nextDocument.data == null
				? null : nextDocument.data.getAudioPath();
		}

		AnimationState nextState = nextDocument == null ? null : nextDocument.animationState;
		if (animationState != nextState) {
			unbindState();
			stopAll();
			animationState = nextState;
			if (animationState != null) animationState.addListener(listener);
		}

		Animation nextAnimation = currentAnimation(nextState);
		if (animation != nextAnimation) {
			stopAll();
			animation = nextAnimation;
		}

		boolean active = isActivelyPlaying(nextDocument);
		// Let an event on the final non-looping frame finish naturally after automatic playback completion.
		if (wasActivelyPlaying && !active && !playedThisFrame) stopAll();
		wasActivelyPlaying = active;
	}

	public void stop() {
		playbackFrame = false;
		playedThisFrame = false;
		wasActivelyPlaying = false;
		stopAll();
	}

	@Override
	public void dispose() {
		if (disposed) return;
		disposed = true;
		playbackFrame = false;
		playedThisFrame = false;
		unbindState();
		clearResources();
		document = null;
		documentData = null;
	}

	private void playEvent(Event event) {
		if (!playbackFrame || !isActivelyPlaying(document) || event == null || event.getData() == null) return;
		String eventAudioPath = event.getData().getAudioPath();
		if (eventAudioPath == null || eventAudioPath.isBlank()) return;

		Path path;
		try {
			path = resolveAudioPath(savePath, filePath, projectAudioPath, eventAudioPath);
		} catch (InvalidPathException error) {
			reportPathFailure(null, "音频路径无效：" + eventAudioPath);
			return;
		}
		if (path == null) {
			reportPathFailure(null, "音频路径无法相对当前项目解析：" + eventAudioPath);
			return;
		}
		if (!Files.isRegularFile(path)) {
			reportPathFailure(path, "音频文件不存在：" + path);
			return;
		}

		AudioClip clip = clips.get(path);
		try {
			if (clip == null) {
				clip = loader.load(path);
				if (clip == null) throw new IllegalStateException("音频后端未返回声音对象");
				clips.put(path, clip);
			}
			clip.play(clamp(event.getVolume(), 0f, 1f, 1f),
				clamp(document.playbackSpeed, 0.5f, 2f, 1f),
				clamp(event.getBalance(), -1f, 1f, 0f));
			playedThisFrame = true;
			warnedPaths.remove(path);
		} catch (RuntimeException error) {
			clips.remove(path);
			disposeClip(clip);
			reportPathFailure(path, "无法播放音频：" + path + "（" + error.getMessage() + "）");
		}
	}

	public static Path resolveAudioPath(String savePath, String filePath, String projectAudioPath,
		String eventAudioPath) {
		if (eventAudioPath == null || eventAudioPath.isBlank()) return null;
		Path eventPath = portablePath(eventAudioPath);
		if (eventPath.isAbsolute()) return eventPath.toAbsolutePath().normalize();

		Path audioBase = null;
		if (projectAudioPath != null && !projectAudioPath.isBlank()) {
			Path configuredBase = portablePath(projectAudioPath);
			if (configuredBase.isAbsolute()) audioBase = configuredBase;
		}
		if (audioBase == null) {
			String projectPath = usableProjectPath(filePath) ? filePath : usableProjectPath(savePath) ? savePath : null;
			if (projectPath == null) return null;
			Path parent = portablePath(projectPath).toAbsolutePath().normalize().getParent();
			if (parent == null) return null;
			audioBase = parent;
			if (projectAudioPath != null && !projectAudioPath.isBlank())
				audioBase = audioBase.resolve(portablePath(projectAudioPath));
		}
		return audioBase.resolve(eventPath).toAbsolutePath().normalize();
	}

	private boolean resourcesChanged(SkeletonDocument nextDocument) {
		if (document != nextDocument) return true;
		if (nextDocument == null) return documentData != null || savePath != null || filePath != null;
		return documentData != nextDocument.data
			|| !Objects.equals(savePath, nextDocument.savePath)
			|| !Objects.equals(filePath, nextDocument.filePath)
			|| !Objects.equals(projectAudioPath, nextDocument.data == null ? null : nextDocument.data.getAudioPath());
	}

	private boolean isActivelyPlaying(SkeletonDocument candidate) {
		return enabled && candidate != null && candidate.playing && !candidate.poseMode
			&& Float.isFinite(candidate.playbackSpeed) && candidate.playbackSpeed > 0f;
	}

	private static Animation currentAnimation(AnimationState state) {
		AnimationState.TrackEntry track = state == null ? null : state.getTrack(0);
		return track == null ? null : track.getAnimation();
	}

	private void unbindState() {
		if (animationState != null) animationState.removeListener(listener);
		animationState = null;
		animation = null;
	}

	private void clearResources() {
		stopAll();
		for (AudioClip clip : clips.values()) disposeClip(clip);
		clips.clear();
		warnedPaths.clear();
		warnedMessages.clear();
	}

	private void stopAll() {
		for (AudioClip clip : clips.values()) {
			try {
				clip.stop();
			} catch (RuntimeException error) {
				debugAudioFailure("停止音频失败", error);
			}
		}
	}

	private static void disposeClip(AudioClip clip) {
		if (clip == null) return;
		try {
			clip.stop();
		} catch (RuntimeException error) {
			debugAudioFailure("停止音频失败", error);
		}
		try {
			clip.dispose();
		} catch (RuntimeException error) {
			debugAudioFailure("释放音频失败", error);
		}
	}

	/** Audio backend stop/dispose failures never interrupt editing; they are only surfaced for diagnosis. */
	private static void debugAudioFailure(String action, RuntimeException error) {
		if (Gdx.app != null) Gdx.app.debug("AudioEventPlayer", action + ": " + error);
	}

	private void reportPathFailure(Path path, String message) {
		if (status == null) return;
		if (path == null ? !warnedMessages.add(message) : !warnedPaths.add(path)) return;
		status.accept(message, true);
	}

	private static Path portablePath(String value) {
		String normalized = value.replace('\\', File.separatorChar).replace('/', File.separatorChar);
		return Path.of(normalized);
	}

	private static boolean usableProjectPath(String value) {
		return value != null && !value.isBlank() && !value.startsWith("builtin:") && !value.startsWith("new:");
	}

	private static float clamp(float value, float minimum, float maximum, float fallback) {
		return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
	}
}
