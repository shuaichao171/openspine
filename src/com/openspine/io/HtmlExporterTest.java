package com.openspine.io;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;

import javax.imageio.ImageIO;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonJson;
import com.openspine.model.SkeletonDocument;

/** Headless regression tests for the Spine 4.3 HTML export contract and embedded resources. */
public final class HtmlExporterTest {
	private static final String PAGE_NAME = "人物/hero[1]+$.png";
	private static final String REGION_NAME = "人物/hero[1]+$";
	private static final String FIXTURE = """
		{
		  "skeleton": {"spine": "4.3.0", "images": "images", "fps": 30},
		  "bones": [{"name": "root"}, {"name": "body", "parent": "root"}],
		  "slots": [{"name": "body", "bone": "body", "attachment": "body"}],
		  "skins": [
		    {"name": "default", "attachments": {"body": {"body": {"path": "人物/hero[1]+$", "width": 80, "height": 120}}}},
		    {"name": "皮肤(夜)", "attachments": {"body": {"body": {"path": "人物/hero[1]+$", "width": 80, "height": 120, "color": "55ddffff"}}}}
		  ],
		  "animations": {
		    "idle": {"bones": {"body": {"rotate": [{"value": 0}, {"time": 1, "value": 0}]}}},
		    "移动[测试]": {"bones": {"body": {"rotate": [{"value": -35}, {"time": 0.5, "value": 35}, {"time": 1, "value": -35}]}}}
		  }
		}
		""";

	public static void main(String[] args) throws Exception {
		exportsTexturelessPlayerWithUsefulError();
		embedsAtlasAndTexturePages();
		embedsLooseImagesAndKeepsRegionPaths();
		rejectsAtlasPagesOutsideResourceDirectory();
		rejectsMissingPagesWithoutReplacingExistingExport();
		escapesEmbeddedDataAndPreservesSetupOnlyProjects();
		System.out.println("HTML EXPORT TEST PASSED");
	}

	private static void exportsTexturelessPlayerWithUsefulError() throws Exception {
		Path directory = Files.createTempDirectory("openspine-html-empty-");
		try {
			SkeletonDocument document = document();
			Path target = directory.resolve("empty.html");
			assertEquals(target, HtmlExporter.write(document, target), "export path is normalized");
			String html = Files.readString(target);
			assertOfflineRuntime(html);
			JsonValue payload = payload(html);
			assertTrue(payload.getString("warning").contains("没有可嵌入"), "missing resources give a visible error");
			assertTrue(html.contains("error:(_player,message)=>fail(message)") && html.contains("catch(error){fail(error)"),
				"both asynchronous asset failures and synchronous player errors stay visible");
			assertTrue(!html.contains("URL.createObjectURL") && !html.contains("jsonUrl"),
				"the export does not rely on removed config fields or extensionless Blob URLs");
			retainFixture("missing-resources.html", html);
		} finally { deleteTree(directory); }
	}

	private static void embedsAtlasAndTexturePages() throws Exception {
		Path directory = Files.createTempDirectory("openspine-html-atlas-");
		try {
			Path image = directory.resolve(PAGE_NAME);
			writeImage(image);
			String atlas = atlas(PAGE_NAME, REGION_NAME).replace("\n", "\r\n");
			Files.writeString(directory.resolve("hero.atlas"), atlas);
			SkeletonDocument document = document();
			document.resourceDirectory = directory.toString();
			document.atlasFileName = "hero.atlas";
			document.animationState = document.newAnimationState();
			document.animationState.setAnimation(0, "移动[测试]", true);
			document.skeleton.setSkin(document.data.findSkin("皮肤(夜)"));
			Path output = directory.resolve("player.html");
			HtmlExporter.write(document, output);
			String html = Files.readString(output);
			JsonValue payload = payload(html);
			JsonValue config = payload.get("config");
			assertEquals("", payload.getString("warning"), "external atlas is playable");
			assertEquals("移动[测试]", config.getString("animation"), "current animation is preserved");
			assertEquals("皮肤(夜)", config.getString("skin"), "current skin is preserved");
			assertPlayerResources(config, atlas, PAGE_NAME, Files.readAllBytes(image));
			retainFixture("external-atlas.html", html);
			assertTrue(document.setPreviewSkins(java.util.List.of("default", "皮肤(夜)")), "composition is accepted");
			HtmlExporter.write(document, output);
			String combinedHtml = Files.readString(output);
			JsonValue combinedConfig = payload(combinedHtml).get("config");
			assertTrue(combinedConfig.get("skin").isArray(), "multiple skins use the public player array config");
			assertEquals("default", combinedConfig.get("skin").getString(0), "base layer retains its order");
			assertEquals("皮肤(夜)", combinedConfig.get("skin").getString(1), "last layer overrides the base");
			assertPlayerResources(combinedConfig, atlas, PAGE_NAME, Files.readAllBytes(image));
			retainFixture("skin-combination.html", combinedHtml);
		} finally { deleteTree(directory); }
	}

	private static void embedsLooseImagesAndKeepsRegionPaths() throws Exception {
		Path directory = Files.createTempDirectory("openspine-html-loose-");
		try {
			Path image = directory.resolve("images").resolve(PAGE_NAME);
			writeImage(image);
			SkeletonDocument document = document();
			document.resourceDirectory = directory.toString();
			Path output = directory.resolve("player.html");
			HtmlExporter.write(document, output);
			String html = Files.readString(output);
			JsonValue exported = payload(html);
			JsonValue config = exported.get("config");
			assertEquals("", exported.getString("warning"), "loose images are playable without an existing atlas");
			String atlas = decodeText(config.get("rawDataURIs").getString(config.getString("atlas")));
			Path generatedAtlas = directory.resolve("generated.atlas");
			Files.writeString(generatedAtlas, atlas);
			FileHandle atlasFile = new FileHandle(generatedAtlas.toFile()) {
				@Override public java.io.BufferedReader reader(int bufferSize) {
					return reader(bufferSize, StandardCharsets.UTF_8.name());
				}
			};
			TextureAtlasData parsed = new TextureAtlasData(atlasFile,
				new FileHandle(directory.toFile()), false);
			assertEquals(1, parsed.getPages().size, "one image creates one complete atlas page");
			assertEquals(REGION_NAME, parsed.getRegions().first().name, "relative names match the loose-image loader");
			assertEquals(80, parsed.getRegions().first().width, "source width is retained");
			assertEquals(120, parsed.getRegions().first().height, "source height is retained");
			assertPlayerResources(config, atlas, "image-0.png", Files.readAllBytes(image));
			retainFixture("loose-images.html", html);
		} finally { deleteTree(directory); }
	}

	private static void rejectsAtlasPagesOutsideResourceDirectory() throws Exception {
		Path directory = Files.createTempDirectory("openspine-html-path-");
		try {
			Path resources = Files.createDirectory(directory.resolve("resources"));
			writeImage(directory.resolve("outside.png"));
			Files.writeString(resources.resolve("unsafe.atlas"), atlas("../outside.png", REGION_NAME));
			SkeletonDocument document = document();
			document.resourceDirectory = resources.toString();
			document.atlasFileName = "unsafe.atlas";
			try { HtmlExporter.write(document, directory.resolve("unsafe.html")); }
			catch (java.io.IOException expected) {
				assertTrue(expected.getMessage().contains("超出资源目录"), "path escape reports a useful error");
				return;
			}
			throw new AssertionError("atlas page paths outside the resource directory must be rejected");
		} finally { deleteTree(directory); }
	}

	private static void rejectsMissingPagesWithoutReplacingExistingExport() throws Exception {
		Path directory = Files.createTempDirectory("openspine-html-missing-");
		try {
			Files.writeString(directory.resolve("missing.atlas"), atlas("missing.png", REGION_NAME));
			SkeletonDocument document = document();
			document.resourceDirectory = directory.toString();
			document.atlasFileName = "missing.atlas";
			Path target = directory.resolve("keep.html");
			Files.writeString(target, "existing export");
			try { HtmlExporter.write(document, target); }
			catch (java.io.IOException expected) {
				assertEquals("existing export", Files.readString(target), "asset failure preserves the existing output");
				return;
			}
			throw new AssertionError("missing atlas images must fail the export");
		} finally { deleteTree(directory); }
	}

	private static void escapesEmbeddedDataAndPreservesSetupOnlyProjects() throws Exception {
		Path directory = Files.createTempDirectory("openspine-html-data-");
		try {
			SkeletonDocument document = document();
			document.data.setName("作品</script><script>throw Error('injected')</script>");
			writeImage(directory.resolve(PAGE_NAME));
			Files.writeString(directory.resolve("hero.atlas"), atlas(PAGE_NAME, REGION_NAME));
			document.resourceDirectory = directory.toString();
			document.atlasFileName = "hero.atlas";
			document.animationState = document.newAnimationState();
			document.animationState.setAnimation(0, "idle", true);
			document.data.getAnimations().clear();
			Path target = directory.resolve("setup.html");
			HtmlExporter.write(document, target);
			String html = Files.readString(target);
			assertTrue(!html.contains("<script>throw Error('injected')"), "project metadata cannot inject script elements");
			assertTrue(payload(html).get("config").get("animation") == null, "setup-only exports omit nonexistent animations");
			retainFixture("setup-only.html", html);
		} finally { deleteTree(directory); }
	}

	private static void assertOfflineRuntime(String html) throws Exception {
		assertTrue(!html.contains("https://unpkg.com"), "offline pages do not depend on a CDN");
		assertTrue(html.contains("name=\"spine-player-version\" content=\"4.3.5\""), "runtime is pinned to the tested version");
		for (String extension : new String[] {"js", "css"}) {
			String marker = extension.equals("js") ? "src=\"data:text/javascript;base64," : "href=\"data:text/css;base64,";
			int start = html.indexOf(marker);
			assertTrue(start >= 0, "player " + extension + " is embedded");
			start += marker.length();
			byte[] embedded = Base64.getDecoder().decode(html.substring(start, html.indexOf('"', start)));
			try (var source = HtmlExporter.class.getResourceAsStream("/assets/spine-player/4.3.5/spine-player.min." + extension)) {
				assertTrue(source != null && java.util.Arrays.equals(source.readAllBytes(), embedded),
					"unmodified official " + extension + " bytes are retained");
			}
		}
		String licenseMarker = "<template id=\"spine-player-license\"><pre>";
		int start = html.indexOf(licenseMarker);
		assertTrue(start >= 0, "runtime license is embedded in the standalone page");
		start += licenseMarker.length();
		try (var source = HtmlExporter.class.getResourceAsStream("/assets/spine-player/4.3.5/LICENSE")) {
			String license = new String(source.readAllBytes(), StandardCharsets.UTF_8);
			String embedded = html.substring(start, html.indexOf("</pre></template>", start))
				.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
			assertEquals(license, embedded,
				"standalone pages redistribute the full runtime license");
		}
	}

	private static SkeletonDocument document() {
		SkeletonDocument document = new SkeletonDocument();
		document.data = new SkeletonJson(new ExportFileTest.StubLoader()).readSkeletonData(new JsonReader().parse(FIXTURE));
		document.skeleton = new Skeleton(document.data);
		return document;
	}

	private static void writeImage(Path target) throws Exception {
		Files.createDirectories(target.getParent());
		BufferedImage image = new BufferedImage(80, 120, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 120; y++) for (int x = 0; x < 80; x++)
			image.setRGB(x, y, x < 12 || y < 12 ? 0xffffdd33 : 0xffff5566);
		ImageIO.write(image, "png", target.toFile());
	}

	private static String atlas(String page, String region) {
		return page + "\nsize: 80,120\nformat: RGBA8888\nfilter: Linear,Linear\nrepeat: none\npma: false\n"
			+ region + "\nrotate: false\nxy: 0,0\nsize: 80,120\norig: 80,120\noffset: 0,0\nindex: -1\n";
	}

	private static JsonValue payload(String html) {
		String marker = "<script id=\"openspine-data\" type=\"application/json\">";
		int start = html.indexOf(marker);
		assertTrue(start >= 0, "structured player configuration is embedded");
		start += marker.length();
		return new JsonReader().parse(html.substring(start, html.indexOf("</script>", start)));
	}

	private static void assertPlayerResources(JsonValue config, String atlas, String pageName, byte[] pixels) {
		String skeletonName = config.getString("skeleton");
		String atlasName = config.getString("atlas");
		assertTrue(skeletonName.endsWith(".json") && atlasName.endsWith(".atlas"),
			"Spine 4.3 determines the skeleton format from the virtual filename");
		JsonValue resources = config.get("rawDataURIs");
		JsonValue skeleton = new JsonReader().parse(decodeText(resources.getString(skeletonName)));
		assertEquals("4.3.0", skeleton.get("skeleton").getString("spine"), "runtime JSON is embedded as UTF-8");
		assertTrue(skeleton.get("skins").isArray(), "Spine Player reads skins from the standard array representation");
		assertEquals(atlas, decodeText(resources.getString(atlasName)), "atlas contents and line endings are preserved");
		String pageKey = atlasName.substring(0, atlasName.lastIndexOf('/') + 1) + pageName;
		assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(pixels), resources.getString(pageKey),
			"page data uses the exact atlas-relative path, including Unicode and regex characters");
	}

	private static String decodeText(String uri) {
		return new String(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)), StandardCharsets.UTF_8);
	}

	private static void retainFixture(String name, String html) throws Exception {
		String output = System.getProperty("openspine.test.htmlOutput");
		if (output == null || output.isBlank()) return;
		Path directory = Path.of(output).toAbsolutePath().normalize();
		Files.createDirectories(directory);
		Files.writeString(directory.resolve(name), html, StandardCharsets.UTF_8);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!java.util.Objects.equals(expected, actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void deleteTree(Path directory) throws Exception {
		if (directory == null || !Files.exists(directory)) return;
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private HtmlExporterTest() {}
}
