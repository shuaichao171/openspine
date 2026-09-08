package com.openspine.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Animation.EventTimeline;
import com.esotericsoftware.spine.Event;
import com.openspine.audio.AudioEventPlayer;

/** Encodes an already rendered frame sequence through ffmpeg for WebM, MOV, or animated WebP output. */
public final class FfmpegAnimationExporter {
	public enum Format { WEBM, MOV, AWEBP }
	public enum Profile {
		FAST("快速"), BALANCED("平衡"), QUALITY("高质量");

		private final String label;
		Profile(String label) { this.label = label; }
		public String label() { return label; }
	}

	public record Settings(float fps, Format format, Profile profile, boolean transparent,
		int maximumWidth, int maximumHeight) {
		public Settings {
			if (!Float.isFinite(fps) || fps < 1f || fps > 100f)
				throw new IllegalArgumentException("视频帧率必须在 1 到 100 FPS 之间");
			if (format == null) throw new IllegalArgumentException("视频格式不能为空");
			if (profile == null) throw new IllegalArgumentException("视频编码档位不能为空");
			if ((maximumWidth == 0) != (maximumHeight == 0))
				throw new IllegalArgumentException("视频最大宽高必须同时设置");
			if (maximumWidth != 0 && (maximumWidth < 2 || maximumHeight < 2
				|| maximumWidth > 16384 || maximumHeight > 16384))
				throw new IllegalArgumentException("视频最大分辨率必须在 2 到 16384 像素之间");
		}

		public Settings(float fps, Format format) {
			this(fps, format, Profile.BALANCED, true, 0, 0);
		}
	}

	public record AudioCue(Path path, double startSeconds, float volume, float balance) {
		public AudioCue {
			if (path == null) throw new IllegalArgumentException("事件音频路径不能为空");
			path = path.toAbsolutePath().normalize();
			if (!Double.isFinite(startSeconds) || startSeconds < 0d)
				throw new IllegalArgumentException("事件音频开始时间无效");
			if (!Float.isFinite(volume) || volume < 0f || volume > 1f)
				throw new IllegalArgumentException("事件音频音量必须在 0 到 1 之间");
			if (!Float.isFinite(balance) || balance < -1f || balance > 1f)
				throw new IllegalArgumentException("事件音频声像必须在 -1 到 1 之间");
		}
	}

	private static volatile String configuredExecutable = "";

	/** Overrides the ffmpeg executable for the current process. A blank value clears it. */
	public static void setConfiguredExecutable(String path) {
		configuredExecutable = path == null ? "" : path.trim();
	}

	public static String executable() {
		String configuredOverride = configuredExecutable;
		if (!configuredOverride.isEmpty()) return configuredOverride;
		String configured = System.getProperty("openspine.ffmpeg", "").trim();
		if (configured.isEmpty()) configured = System.getenv().getOrDefault("OPENSPINE_FFMPEG", "").trim();
		return configured.isEmpty() ? "ffmpeg" : configured;
	}

	public static boolean isAvailable() {
		Process process = null;
		try {
			process = new ProcessBuilder(executable(), "-version")
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
			if (!process.waitFor(5, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (IOException | InterruptedException error) {
			if (error instanceof InterruptedException) Thread.currentThread().interrupt();
			return false;
		} finally {
			if (process != null) process.destroy();
		}
	}

	public static Path encode(List<Path> frames, float fps, Path requestedTarget, Format format)
		throws IOException {
		return encode(frames, new Settings(fps, format), List.of(), requestedTarget,
			new CancellationToken());
	}

	public static Path encode(List<Path> frames, float fps, Path requestedTarget, Format format,
		CancellationToken cancellation) throws IOException {
		return encode(frames, new Settings(fps, format), List.of(), requestedTarget, cancellation);
	}

	public static Path encode(List<Path> frames, Settings settings, List<AudioCue> audioCues,
		Path requestedTarget, CancellationToken cancellation) throws IOException {
		if (frames == null || frames.isEmpty()) throw new IllegalArgumentException("视频至少需要一帧");
		if (settings == null) throw new IllegalArgumentException("视频设置不能为空");
		List<AudioCue> cues = audioCues == null ? List.of() : List.copyOf(audioCues);
		if (settings.format() == Format.AWEBP && !cues.isEmpty())
			throw new IllegalArgumentException("动画 WebP 不支持音频");
		if (requestedTarget == null) throw new IllegalArgumentException("视频目标路径不能为空");
		Path target = requestedTarget.toAbsolutePath().normalize();
		if (target.getFileName() == null) throw new IllegalArgumentException("视频目标路径无效");
		String extension = switch (settings.format()) {
		case WEBM -> ".webm";
		case MOV -> ".mov";
		case AWEBP -> ".webp";
		};
		if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
			throw new IllegalArgumentException("视频目标路径必须以 " + extension + " 结尾");
		Path parent = target.getParent();
		if (parent == null) throw new IOException("视频目标目录不存在");
		Files.createDirectories(parent);
		Path staging = Files.createTempDirectory(parent, ".openspine-video-");
		CancellationToken token = cancellation == null ? new CancellationToken() : cancellation;
		try {
			for (int index = 0; index < frames.size(); index++) {
				token.checkCancelled();
				Path source = frames.get(index);
				if (source == null || !Files.isRegularFile(source)) throw new IOException("视频帧不存在：" + source);
				Files.copy(source, staging.resolve(String.format(Locale.ROOT, "frame_%06d.png", index)),
					StandardCopyOption.REPLACE_EXISTING);
			}
			for (AudioCue cue : cues)
				if (!Files.isRegularFile(cue.path())) throw new IOException("事件音频文件不存在：" + cue.path());
			// Keep the requested suffix so ffmpeg can select the correct muxer for the
			// temporary output. A generic .tmp suffix makes ffmpeg reject the file.
			Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", extension);
			try {
				List<String> command = buildCommand(staging, frames.size(), settings, cues, temporary);
				Path log = staging.resolve("ffmpeg.log");
				Process process;
				try {
					process = new ProcessBuilder(command)
						.redirectErrorStream(true)
						.redirectOutput(log.toFile())
						.start();
				}
				catch (IOException error) { throw new IOException("无法启动 ffmpeg，请安装后重试", error); }
				try {
					long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
					while (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
						token.checkCancelled();
						if (System.nanoTime() >= deadline) {
							process.destroyForcibly();
							throw new IOException("ffmpeg 编码超时");
						}
					}
					int exit = process.exitValue();
					if (exit != 0) {
						String detail = readLogTail(log);
						throw new IOException("ffmpeg 编码失败（退出码 " + exit + "）"
							+ (detail.isBlank() ? "" : "：" + detail));
					}
				} catch (InterruptedException | java.util.concurrent.CancellationException error) {
					process.destroyForcibly();
					if (error instanceof InterruptedException) Thread.currentThread().interrupt();
					if (error instanceof java.util.concurrent.CancellationException cancelled) throw cancelled;
					throw new IOException("视频编码被中断", error);
				}
				token.commit(() -> {
					try {
						Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
							StandardCopyOption.REPLACE_EXISTING);
					} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
						Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
					}
				});
			} finally { Files.deleteIfExists(temporary); }
			return target;
		} finally {
			try (var files = Files.walk(staging)) {
				files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (IOException ignored) { }
				});
			}
		}
	}

	public static List<AudioCue> collectAudioCues(Animation animation, int startFrame, int lastFrame,
		float fps, String savePath, String filePath, String projectAudioPath) {
		if (animation == null) throw new IllegalArgumentException("没有可导出的动画");
		if (!Float.isFinite(fps) || fps < 1f || fps > 100f)
			throw new IllegalArgumentException("视频帧率必须在 1 到 100 FPS 之间");
		if (startFrame < 0 || lastFrame < startFrame)
			throw new IllegalArgumentException("视频帧范围无效");
		double startTime = startFrame / (double)fps;
		double endTime = (lastFrame + 1d) / fps;
		List<AudioCue> result = new ArrayList<>();
		for (Animation.Timeline timeline : animation.getTimelines()) {
			if (!(timeline instanceof EventTimeline events)) continue;
			for (Event event : events.getEvents()) {
				if (event == null || event.getData() == null) continue;
				String eventPath = event.getData().getAudioPath();
				if (eventPath == null || eventPath.isBlank()) continue;
				double eventTime = event.getTime();
				if (!Double.isFinite(eventTime) || eventTime < startTime || eventTime >= endTime) continue;
				Path path = AudioEventPlayer.resolveAudioPath(savePath, filePath, projectAudioPath, eventPath);
				if (path == null)
					throw new IllegalArgumentException("事件音频路径无法相对当前项目解析：" + eventPath);
				if (!Files.isRegularFile(path)) throw new IllegalArgumentException("事件音频文件不存在：" + path);
				result.add(new AudioCue(path, eventTime - startTime, event.getVolume(), event.getBalance()));
			}
		}
		result.sort(Comparator.comparingDouble(AudioCue::startSeconds));
		return List.copyOf(result);
	}

	static List<String> buildCommand(Path staging, int frameCount, Settings settings,
		List<AudioCue> audioCues, Path output) {
		if (staging == null || output == null) throw new IllegalArgumentException("ffmpeg 路径参数不能为空");
		if (frameCount <= 0) throw new IllegalArgumentException("视频至少需要一帧");
		if (settings == null) throw new IllegalArgumentException("视频设置不能为空");
		List<AudioCue> cues = audioCues == null ? List.of() : audioCues;
		List<String> command = new ArrayList<>();
		command.add(executable());
		command.addAll(List.of("-y", "-hide_banner", "-loglevel", "error", "-framerate",
			Float.toString(settings.fps()), "-i", staging.resolve("frame_%06d.png").toString()));
		for (AudioCue cue : cues) command.addAll(List.of("-i", cue.path().toString()));
		command.addAll(List.of("-map", "0:v:0", "-frames:v", Integer.toString(frameCount)));
		List<String> videoFilters = new ArrayList<>();
		if (settings.maximumWidth() > 0) {
			String scale = "scale=w=min(iw\\," + settings.maximumWidth() + "):h=min(ih\\,"
				+ settings.maximumHeight() + "):force_original_aspect_ratio=decrease";
			videoFilters.add(scale);
		}
		if (settings.format() == Format.WEBM)
			videoFilters.add("pad=ceil(iw/2)*2:ceil(ih/2)*2:color="
				+ (settings.transparent() ? "black@0" : "black"));
		if (!videoFilters.isEmpty()) command.addAll(List.of("-vf", String.join(",", videoFilters)));
		if (settings.format() == Format.WEBM) addWebmOptions(command, settings);
		else if (settings.format() == Format.MOV) addMovOptions(command, settings);
		else addAnimatedWebpOptions(command, settings);

		if (settings.format() == Format.AWEBP || cues.isEmpty()) command.add("-an");
		else {
			command.addAll(List.of("-filter_complex", audioFilter(cues), "-map", "[aout]", "-shortest"));
			if (settings.format() == Format.WEBM)
				command.addAll(List.of("-c:a", "libopus", "-b:a", settings.profile() == Profile.QUALITY ? "192k" : "128k"));
			else command.addAll(List.of("-c:a", "aac", "-b:a", settings.profile() == Profile.QUALITY ? "256k" : "192k"));
		}
		command.add(output.toString());
		return List.copyOf(command);
	}

	private static void addWebmOptions(List<String> command, Settings settings) {
		String crf = switch (settings.profile()) {
		case FAST -> "38";
		case BALANCED -> "30";
		case QUALITY -> "22";
		};
		String cpuUsed = switch (settings.profile()) {
		case FAST -> "6";
		case BALANCED -> "3";
		case QUALITY -> "1";
		};
		command.addAll(List.of("-c:v", "libvpx-vp9", "-b:v", "0", "-crf", crf,
			"-deadline", "good", "-cpu-used", cpuUsed, "-pix_fmt",
			settings.transparent() ? "yuva420p" : "yuv420p"));
	}

	private static void addMovOptions(List<String> command, Settings settings) {
		String qscale = switch (settings.profile()) {
		case FAST -> "13";
		case BALANCED -> "9";
		case QUALITY -> "5";
		};
		String profile = settings.transparent() ? "4" : settings.profile() == Profile.FAST ? "2" : "3";
		String pixels = settings.transparent() ? "yuva444p10le" : "yuv444p10le";
		command.addAll(List.of("-c:v", "prores_ks", "-profile:v", profile, "-qscale:v", qscale,
			"-pix_fmt", pixels));
	}

	private static void addAnimatedWebpOptions(List<String> command, Settings settings) {
		String quality = switch (settings.profile()) {
		case FAST -> "60";
		case BALANCED -> "80";
		case QUALITY -> "95";
		};
		String compression = switch (settings.profile()) {
		case FAST -> "3";
		case BALANCED -> "4";
		case QUALITY -> "6";
		};
		command.addAll(List.of("-c:v", "libwebp_anim", "-loop", "0", "-q:v", quality,
			"-compression_level", compression));
	}

	private static String audioFilter(List<AudioCue> cues) {
		StringBuilder filter = new StringBuilder();
		for (int index = 0; index < cues.size(); index++) {
			AudioCue cue = cues.get(index);
			long delay = Math.max(0L, Math.round(cue.startSeconds() * 1000d));
			double left = cue.balance() > 0f ? 1d - cue.balance() : 1d;
			double right = cue.balance() < 0f ? 1d + cue.balance() : 1d;
			filter.append('[').append(index + 1).append(":a]")
				.append("aresample=48000,aformat=channel_layouts=stereo,volume=")
				.append(decimal(cue.volume())).append(",pan=stereo|c0=c0*")
				.append(decimal(left)).append("|c1=c1*").append(decimal(right))
				.append(",adelay=").append(delay).append('|').append(delay)
				.append("[a").append(index).append("];\n");
		}
		for (int index = 0; index < cues.size(); index++) filter.append("[a").append(index).append(']');
		filter.append("amix=inputs=").append(cues.size())
			.append(":duration=longest:normalize=0,alimiter=limit=0.95[aout]");
		return filter.toString();
	}

	private static String decimal(double value) {
		return String.format(Locale.ROOT, "%.6f", value);
	}

	private static String readLogTail(Path log) {
		try {
			if (!Files.isRegularFile(log)) return "";
			String text = Files.readString(log, StandardCharsets.UTF_8).trim().replaceAll("\\s+", " ");
			return text.length() <= 800 ? text : text.substring(text.length() - 800);
		} catch (IOException ignored) { return ""; }
	}

	private FfmpegAnimationExporter() {}
}
