package com.openspine.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.esotericsoftware.spine.Animation;
import com.openspine.model.SkeletonDocument;

/** Exports an offline Spine page with embedded player, project data and texture pages. */
public final class HtmlExporter {
	private record PlayerAssets(String atlas, Map<String, String> pages, String warning) {}
	private static final String RESOURCE_PREFIX = "openspine-export/";
	static final String PLAYER_VERSION = "4.3.5";
	private static final String PLAYER_ROOT = "/assets/spine-player/" + PLAYER_VERSION + "/";

	public static Path write(SkeletonDocument document, Path requestedTarget) throws IOException {
		if (document == null || document.data == null) throw new IllegalArgumentException("没有可导出的项目数据");
		if (requestedTarget == null) throw new IllegalArgumentException("HTML 目标路径不能为空");
		Path target = requestedTarget.toAbsolutePath().normalize();
		if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".html"))
			throw new IllegalArgumentException("HTML 目标路径必须以 .html 结尾");
		String json = playerJson(document);
		PlayerAssets assets = playerAssets(document);
		String animation = document.getCurrentAnimationName();
		if (animation.isBlank() || document.data.findAnimation(animation) == null) {
			animation = "";
			for (Animation candidate : document.data.getAnimations()) { animation = candidate.getName(); break; }
		}
		List<String> skins = document.getPreviewSkinNames().stream()
			.filter(name -> document.data.findSkin(name) != null).toList();
		String html = playerHtml(document.data.getName(), json, assets, animation, skins);
		Path parent = target.getParent();
		if (parent == null) throw new IOException("HTML 目标目录不存在");
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			Files.writeString(temporary, html, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		} finally { Files.deleteIfExists(temporary); }
		return target;
	}

	private static String playerJson(SkeletonDocument document) {
		return new JsonReader().parse(SkeletonExporter.toJson(document)).toJson(OutputType.json);
	}

	private static PlayerAssets playerAssets(SkeletonDocument document) throws IOException {
		Path atlasPath = ProjectPackager.externalAtlasPath(document);
		if (atlasPath == null || !Files.isRegularFile(atlasPath))
			return looseImageAssets(document);
		TextureAtlasData data = AtlasFiles.readData(new FileHandle(atlasPath.toFile()),
			new FileHandle(atlasPath.getParent().toFile()), false);
		Path resourceRoot = atlasPath.getParent().toRealPath();
		LinkedHashMap<String, String> pages = new LinkedHashMap<>();
		for (Page page : data.getPages()) {
			if (page.textureFile == null || !page.textureFile.exists())
				throw new IOException("图集图片不存在：" + page.name);
			Path path = page.textureFile.file().toPath().toRealPath();
			if (!path.startsWith(resourceRoot)) throw new IOException("图集图片超出资源目录：" + page.name);
			if (pages.put(page.name, imageDataUri(path)) != null)
				throw new IOException("图集包含重复图片页：" + page.name);
		}
		return new PlayerAssets(Files.readString(atlasPath, StandardCharsets.UTF_8), Map.copyOf(pages), "");
	}

	private static PlayerAssets looseImageAssets(SkeletonDocument document) throws IOException {
		String images = document.data.getImagesPath();
		Path base = null;
		if (document.resourceDirectory != null && !document.resourceDirectory.isBlank())
			base = Path.of(document.resourceDirectory).toAbsolutePath().normalize();
		else if (document.filePath != null && !document.filePath.isBlank()
			&& !document.filePath.startsWith("builtin:") && !document.filePath.startsWith("new:"))
			base = Path.of(document.filePath).toAbsolutePath().normalize().getParent();
		if (images == null || images.isBlank() || (base == null && !Path.of(images).isAbsolute()))
			return missingAssets();
		Path configured = Path.of(images.trim());
		Path directory = (configured.isAbsolute() ? configured : base.resolve(configured)).normalize();
		if (!Files.isDirectory(directory)) return missingAssets();
		Path resourceRoot = directory.toRealPath();
		List<Path> imagesToEmbed;
		try (var files = Files.walk(directory)) {
			imagesToEmbed = files.filter(Files::isRegularFile)
				.filter(file -> AtlasPacker.isSupportedImage(file.getFileName().toString().toLowerCase(Locale.ROOT)))
				.sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER)).toList();
		}
		if (imagesToEmbed.isEmpty()) return missingAssets();
		StringBuilder atlas = new StringBuilder();
		Map<String, String> pages = new LinkedHashMap<>();
		HashSet<String> regionNames = new HashSet<>();
		for (Path image : imagesToEmbed) {
			if (!image.toRealPath().startsWith(resourceRoot))
				throw new IOException("图片超出资源目录：" + image);
			String region = SkeletonLoader.looseRegionName(directory, image);
			if (!regionNames.add(region)) throw new IOException("图片区域名称重复：" + region);
			if (region.indexOf('\n') >= 0 || region.indexOf('\r') >= 0)
				throw new IOException("图片区域名称不能包含换行：" + region);
			int width, height;
			try (var input = ImageIO.createImageInputStream(image.toFile())) {
				if (input == null) throw new IOException("无法读取图片：" + image);
				var readers = ImageIO.getImageReaders(input);
				if (!readers.hasNext()) throw new IOException("无法读取图片：" + image);
				var reader = readers.next();
				try {
					reader.setInput(input);
					width = reader.getWidth(0);
					height = reader.getHeight(0);
				} finally { reader.dispose(); }
			}
			String page = "image-" + pages.size() + "." + extension(image);
			if (!pages.isEmpty()) atlas.append('\n');
			// A full-image page preserves loose-image region coordinates without repacking or GL access.
			atlas.append(page).append("\nsize: ").append(width).append(',').append(height)
				.append("\nformat: RGBA8888\nfilter: Linear,Linear\nrepeat: none\npma: false\n")
				.append(region).append("\nrotate: false\nxy: 0,0\nsize: ").append(width).append(',').append(height)
				.append("\norig: ").append(width).append(',').append(height).append("\noffset: 0,0\nindex: -1\n");
			pages.put(page, imageDataUri(image));
		}
		return new PlayerAssets(atlas.toString(), pages, "");
	}

	private static PlayerAssets missingAssets() {
		return new PlayerAssets("", Map.of(), "当前项目没有可嵌入的外部 .atlas 或原始图片资源");
	}

	private static String imageDataUri(Path image) throws IOException {
		String mime = switch (extension(image)) {
		case "jpg", "jpeg" -> "image/jpeg";
		case "webp" -> "image/webp";
		default -> "image/png";
		};
		return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(image));
	}

	private static String extension(Path image) {
		String name = image.getFileName().toString();
		return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
	}

	private static String playerHtml(String name, String json, PlayerAssets assets, String animation, List<String> skins)
		throws IOException {
		String playerScript = bundledResource("spine-player.min.js");
		String playerStyle = bundledResource("spine-player.min.css");
		String playerLicense = bundledResource("LICENSE");
		JsonValue payload = new JsonValue(JsonValue.ValueType.object);
		payload.addChild("warning", new JsonValue(assets.warning()));
		JsonValue config = new JsonValue(JsonValue.ValueType.object);
		payload.addChild("config", config);
		config.addChild("skeleton", new JsonValue(RESOURCE_PREFIX + "skeleton.json"));
		config.addChild("atlas", new JsonValue(RESOURCE_PREFIX + "skeleton.atlas"));
		config.addChild("showControls", new JsonValue(true));
		config.addChild("backgroundColor", new JsonValue("202124"));
		if (!animation.isBlank()) config.addChild("animation", new JsonValue(animation));
		if (skins.size() == 1) config.addChild("skin", new JsonValue(skins.get(0)));
		else if (!skins.isEmpty()) {
			JsonValue layers = new JsonValue(JsonValue.ValueType.array);
			for (String skin : skins) layers.addChild(new JsonValue(skin));
			config.addChild("skin", layers);
		}
		JsonValue resources = new JsonValue(JsonValue.ValueType.object);
		config.addChild("rawDataURIs", resources);
		resources.addChild(RESOURCE_PREFIX + "skeleton.json", new JsonValue("data:application/json;base64," + base64(json)));
		resources.addChild(RESOURCE_PREFIX + "skeleton.atlas", new JsonValue("data:text/plain;base64," + base64(assets.atlas())));
		for (Map.Entry<String, String> page : assets.pages().entrySet())
			resources.addChild(RESOURCE_PREFIX + page.getKey(), new JsonValue(page.getValue()));
		String payloadJson = payload.toJson(OutputType.json).replace("<", "\\u003c");
		return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
			+ "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"
			+ escape(name) + "</title><meta name=\"spine-player-version\" content=\"" + PLAYER_VERSION + "\">"
			+ "<link id=\"spine-player-style\" rel=\"stylesheet\" href=\"data:text/css;base64," + base64(playerStyle) + "\">"
			+ "<style>html,body,#player{width:100%;height:100%;margin:0;overflow:hidden;background:#202124}"
			+ "#status{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;color:#f1f3f4;background:#202124;"
			+ "font:14px system-ui;padding:24px;text-align:center;white-space:pre-wrap;overflow-wrap:anywhere;overflow:auto}"
			+ "#status[hidden]{display:none}</style></head><body><div id=\"player\"></div>"
			+ "<div id=\"status\" role=\"status\" data-state=\"loading\">正在加载动画...</div>"
			+ "<template id=\"spine-player-license\"><pre>" + escape(playerLicense) + "</pre></template>"
			+ "<script id=\"openspine-data\" type=\"application/json\">" + payloadJson + "</script>"
			+ "<script id=\"spine-player-runtime\" src=\"data:text/javascript;base64," + base64(playerScript) + "\"></script><script>"
			+ "(()=>{const status=document.getElementById('status');const fail=error=>{"
			+ "const message=error&&error.message?error.message:String(error);"
			+ "if(status.dataset.state==='error'&&message==='Script error.')return;status.hidden=false;"
			+ "status.dataset.state='error';status.textContent='动画加载失败：'+message;};"
			+ "window.addEventListener('error',event=>fail(event.error||event.message));"
			+ "window.addEventListener('unhandledrejection',event=>fail(event.reason));"
			+ "try{const payload=JSON.parse(document.getElementById('openspine-data').textContent);"
			+ "if(payload.warning){fail(payload.warning);return;}"
			+ "if(!window.spine||!spine.SpinePlayer){fail('内嵌播放器无法加载，请重新导出 HTML');return;}"
			// The default 4.3 downloader returns atob's byte string for embedded text instead of decoding UTF-8.
			+ "class Utf8Downloader extends spine.Downloader{dataUriToString(uri){"
			+ "if(!uri.includes(';base64,'))return super.dataUriToString(uri);"
			+ "return new TextDecoder('utf-8',{fatal:true}).decode(Uint8Array.from(atob(uri.slice(uri.indexOf(',')+1)),"
			+ "character=>character.charCodeAt(0)));}}"
			+ "new spine.SpinePlayer('player',{...payload.config,downloader:new Utf8Downloader(),"
			+ "success:()=>{status.dataset.state='ready';status.hidden=true;},"
			+ "error:(_player,message)=>fail(message)});}catch(error){fail(error);}})();</script></body></html>";
	}

	private static String bundledResource(String name) throws IOException {
		try (var input = HtmlExporter.class.getResourceAsStream(PLAYER_ROOT + name)) {
			if (input == null) throw new IOException("缺少离线播放器资源：" + name + "，请重新安装完整版本");
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String base64(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String escape(String value) {
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;");
	}

	private HtmlExporter() {}
}
