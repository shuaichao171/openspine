package com.openspine.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;

/**
 * 内置示例骨架：程序化生成一个简单人形（图集纹理 + Spine JSON 文本）。
 * 这样无需任何外部资源，软件开箱即可演示。包含三个动画：idle / wave / walk。
 */
public class ExampleSkeleton {
	static final int TEXTURE_SCALE = 4;
	private record RegionSpec(String name, int width, int height, boolean circle, float r, float g, float b) {}
	private static final RegionSpec[] CHARACTER_REGIONS = {
		new RegionSpec("torso", 60, 120, false, 0.30f, 0.45f, 0.90f),
		new RegionSpec("head", 64, 64, true, 0.90f, 0.55f, 0.25f),
		new RegionSpec("arm", 20, 70, false, 0.55f, 0.70f, 0.95f),
		new RegionSpec("forearm", 16, 60, false, 0.65f, 0.78f, 1.00f),
		new RegionSpec("leg", 24, 80, false, 0.55f, 0.35f, 0.75f),
		new RegionSpec("shin", 20, 70, false, 0.65f, 0.45f, 0.85f)
	};
	private static final RegionSpec[] ROBOT_REGIONS = {
		new RegionSpec("torso", 60, 120, false, 0.18f, 0.72f, 0.58f),
		new RegionSpec("head", 64, 64, true, 0.96f, 0.76f, 0.20f),
		new RegionSpec("arm", 20, 70, false, 0.20f, 0.82f, 0.68f),
		new RegionSpec("forearm", 16, 60, false, 0.95f, 0.38f, 0.32f),
		new RegionSpec("leg", 24, 80, false, 0.16f, 0.56f, 0.50f),
		new RegionSpec("shin", 20, 70, false, 0.95f, 0.62f, 0.20f)
	};

	public static TextureAtlas createAtlas() {
		return createAtlas(CHARACTER_REGIONS);
	}

	/** 第二个内置骨架使用独立纹理，避免两个示例看起来只是同一骨架的副本。 */
	public static TextureAtlas createRobotAtlas() {
		return createAtlas(ROBOT_REGIONS);
	}

	private static TextureAtlas createAtlas(RegionSpec[] regions) {
		TextureAtlas atlas = new TextureAtlas();
		try {
			for (RegionSpec region : regions) {
				Pixmap pixmap = createPixmap(region);
				Texture texture = filteredTexture(pixmap);
				atlas.addRegion(region.name(), texture, 0, 0,
					scaledTextureSize(region.width()), scaledTextureSize(region.height()));
			}
			return atlas;
		} catch (RuntimeException | Error failure) {
			atlas.dispose();
			throw failure;
		}
	}

	/** Writes the same procedural pixels as the in-memory atlas without reading GPU textures. */
	public static PreparedResources prepareResources(boolean alternate) throws IOException {
		PreparedResources resources = new PreparedResources(Files.createTempDirectory("openspine-example-"));
		try {
			StringBuilder descriptor = new StringBuilder();
			for (RegionSpec region : alternate ? ROBOT_REGIONS : CHARACTER_REGIONS) {
				Pixmap pixmap = createPixmap(region);
				try {
					PixmapIO.writePNG(new FileHandle(resources.directory.resolve(region.name() + ".png").toFile()), pixmap);
				} finally { pixmap.dispose(); }
				int width = scaledTextureSize(region.width()), height = scaledTextureSize(region.height());
				descriptor.append(region.name()).append(".png\nsize: ").append(width).append(", ").append(height)
					.append("\nformat: RGBA8888\nfilter: MipMapLinearLinear, Linear\nrepeat: none\npma: false\n")
					.append(region.name()).append("\nrotate: false\nxy: 0, 0\nsize: ").append(width).append(", ").append(height)
					.append("\norig: ").append(width).append(", ").append(height)
					.append("\noffset: 0, 0\nindex: -1\n\n");
			}
			Files.writeString(resources.atlasFile(), descriptor, StandardCharsets.UTF_8);
			return resources;
		} catch (IOException | RuntimeException | Error failure) {
			resources.close();
			throw failure;
		}
	}

	/** Owns only the unique temporary source directory created by prepareResources. */
	public static final class PreparedResources implements AutoCloseable {
		private final Path directory;
		private PreparedResources(Path directory) { this.directory = directory; }
		public Path directory() { return directory; }
		public Path atlasFile() { return directory.resolve("example.atlas"); }
		@Override public void close() {
			for (RegionSpec region : CHARACTER_REGIONS) deleteOwnedFile(directory.resolve(region.name() + ".png"));
			deleteOwnedFile(atlasFile());
			deleteOwnedFile(directory);
		}
		private static void deleteOwnedFile(Path path) {
			try { Files.deleteIfExists(path); }
			catch (IOException ignored) { path.toFile().deleteOnExit(); }
		}
	}

	private static Pixmap createPixmap(RegionSpec region) {
		int width = scaledTextureSize(region.width()), height = scaledTextureSize(region.height());
		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		pixmap.setColor(region.r(), region.g(), region.b(), 1f);
		if (region.circle()) {
			int center = width / 2, radius = width / 2 - TEXTURE_SCALE;
			pixmap.fillCircle(center, center, radius);
			pixmap.setColor(1f, 1f, 1f, 0.9f);
			for (int inset = 0; inset < TEXTURE_SCALE; inset++) pixmap.drawCircle(center, center, radius - inset);
		} else {
			pixmap.fillRectangle(0, 0, width, height);
			pixmap.setColor(1f, 1f, 1f, 0.9f);
			for (int inset = 0; inset < TEXTURE_SCALE; inset++)
				pixmap.drawRectangle(inset, inset, width - inset * 2, height - inset * 2);
		}
		return pixmap;
	}

	static int scaledTextureSize(int designSize) {
		if (designSize <= 0 || designSize > Integer.MAX_VALUE / TEXTURE_SCALE)
			throw new IllegalArgumentException("Built-in texture size is invalid: " + designSize);
		return designSize * TEXTURE_SCALE;
	}

	private static Texture filteredTexture(Pixmap pixmap) {
		Texture texture = new Texture(new PixmapTextureData(pixmap, Pixmap.Format.RGBA8888, true, true));
		texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
		return texture;
	}

	public static final String JSON = """
		{
		  "skeleton": { "spine": "4.3.0", "images": "img/" },
		  "bones": [
		    { "name": "root" },
		    { "name": "hips",      "parent": "root",  "y": 0 },
		    { "name": "spine",     "parent": "hips",  "y": -10, "length": 50 },
		    { "name": "chest",     "parent": "spine", "y": -60, "length": 35 },
		    { "name": "neck",      "parent": "chest", "y": -30, "length": 15 },
		    { "name": "head",      "parent": "neck",  "y": -20 },
		    { "name": "shoulderL", "parent": "chest", "x": -16, "y": 0 },
		    { "name": "armL",      "parent": "shoulderL", "y": -8, "length": 32 },
		    { "name": "forearmL",  "parent": "armL",      "y": -30, "length": 30 },
		    { "name": "shoulderR", "parent": "chest", "x": 16, "y": 0 },
		    { "name": "armR",      "parent": "shoulderR", "y": -8, "length": 32 },
		    { "name": "forearmR",  "parent": "armR",      "y": -30, "length": 30 },
		    { "name": "thighL",    "parent": "hips", "x": -10, "y": -5, "length": 38 },
		    { "name": "shinL",     "parent": "thighL", "y": -38, "length": 38 },
		    { "name": "thighR",    "parent": "hips", "x": 10, "y": -5, "length": 38 },
		    { "name": "shinR",     "parent": "thighR", "y": -38, "length": 38 }
		  ],
		  "slots": [
		    { "name": "torso",  "bone": "spine",  "attachment": "torso" },
		    { "name": "head",   "bone": "head",   "attachment": "head" },
		    { "name": "armL",   "bone": "armL",   "attachment": "arm" },
		    { "name": "foreL",  "bone": "forearmL", "attachment": "forearm" },
		    { "name": "armR",   "bone": "armR",   "attachment": "arm" },
		    { "name": "foreR",  "bone": "forearmR", "attachment": "forearm" },
		    { "name": "legL",   "bone": "thighL", "attachment": "leg" },
		    { "name": "shinL",  "bone": "shinL",  "attachment": "shin" },
		    { "name": "legR",   "bone": "thighR", "attachment": "leg" },
		    { "name": "shinR",  "bone": "shinR",  "attachment": "shin" }
		  ],
		  "skins": {
		    "default": { "name": "default", "attachments": {
		      "torso": { "torso": { "path": "torso", "x": 0, "y": 30, "width": 60, "height": 120 } },
		      "head":  { "head":  { "path": "head", "width": 64, "height": 64 } },
		      "armL":  { "arm":   { "path": "arm",  "x": 0, "y": -10, "width": 20, "height": 70 } },
		      "foreL": { "forearm": { "path": "forearm", "x": 0, "y": -5, "width": 16, "height": 60 } },
		      "armR":  { "arm":   { "path": "arm",  "x": 0, "y": -10, "width": 20, "height": 70 } },
		      "foreR": { "forearm": { "path": "forearm", "x": 0, "y": -5, "width": 16, "height": 60 } },
		      "legL":  { "leg":   { "path": "leg",  "x": 0, "y": -10, "width": 24, "height": 80 } },
		      "shinL": { "shin":  { "path": "shin", "x": 0, "y": -5, "width": 20, "height": 70 } },
		      "legR":  { "leg":   { "path": "leg",  "x": 0, "y": -10, "width": 24, "height": 80 } },
		      "shinR": { "shin":  { "path": "shin", "x": 0, "y": -5, "width": 20, "height": 70 } }
		    } },
		    "blue": { "name": "blue", "attachments": {
		      "torso": { "torso": { "path": "torso", "x": 0, "y": 30, "width": 60, "height": 120, "color": "003366ff" } },
		      "head":  { "head":  { "path": "head", "width": 64, "height": 64, "color": "3366ccff" } }
		    } }
		  },
		  "animations": {
		    "idle": {
		      "bones": {
		        "armL": { "rotate": [ { "time": 0, "value": 0 }, { "time": 1, "value": 0 } ] },
		        "armR": { "rotate": [ { "time": 0, "value": 0 }, { "time": 1, "value": 0 } ] }
		      }
		    },
		    "wave": {
		      "bones": {
		        "armR": {
		          "rotate": [
		            { "time": 0, "value": 0 },
		            { "time": 0.3, "value": -100, "curve": "stepped" },
		            { "time": 0.6, "value": -140 },
		            { "time": 0.9, "value": -100 },
		            { "time": 1.2, "value": -140 },
		            { "time": 1.5, "value": -100 },
		            { "time": 1.8, "value": 0 }
		          ]
		        },
		        "forearmR": {
		          "rotate": [
		            { "time": 0, "value": 0 },
		            { "time": 0.3, "value": -30, "curve": "stepped" },
		            { "time": 1.2, "value": -30, "curve": "stepped" },
		            { "time": 1.5, "value": -90 },
		            { "time": 1.8, "value": 0 }
		          ]
		        }
		      },
		      "slots": {
		        "head": {
		          "rgba": [
		            { "time": 0, "color": "ffffffff" },
		            { "time": 0.9, "color": "ffd27fff" },
		            { "time": 1.8, "color": "ffffffff" }
		          ]
		        }
		      }
		    },
		    "walk": {
		      "bones": {
		        "thighL": {
		          "rotate": [
		            { "time": 0, "value": -25 },
		            { "time": 0.5, "value": 25 },
		            { "time": 1, "value": -25 }
		          ]
		        },
		        "thighR": {
		          "rotate": [
		            { "time": 0, "value": 25 },
		            { "time": 0.5, "value": -25 },
		            { "time": 1, "value": 25 }
		          ]
		        },
		        "shinL": {
		          "rotate": [
		            { "time": 0, "value": 25 },
		            { "time": 0.5, "value": -10 },
		            { "time": 1, "value": 25 }
		          ]
		        },
		        "shinR": {
		          "rotate": [
		            { "time": 0, "value": -10 },
		            { "time": 0.5, "value": 25 },
		            { "time": 1, "value": -10 }
		          ]
		        },
		        "armL": {
		          "rotate": [
		            { "time": 0, "value": 20 },
		            { "time": 0.5, "value": -20 },
		            { "time": 1, "value": 20 }
		          ]
		        },
		        "armR": {
		          "rotate": [
		            { "time": 0, "value": -20 },
		            { "time": 0.5, "value": 20 },
		            { "time": 1, "value": -20 }
		          ]
		        }
		      }
		    }
		  }
		}
		""";

	/**
	 * 第二个内置示例：更矮宽的机器人，拥有不同的层级、比例、配色、动画和初始位置。
	 * 根骨骼向右偏移，使“显示全部”时两个骨架自然并排。
	 */
	public static final String ROBOT_JSON = """
		{
		  "skeleton": { "spine": "4.3.0", "images": "img/" },
		  "bones": [
		    { "name": "robotRoot", "x": 165 },
		    { "name": "body",         "parent": "robotRoot" },
		    { "name": "screen",       "parent": "body", "y": -82 },
		    { "name": "antenna",      "parent": "screen", "y": -30, "length": 24 },
		    { "name": "leftClamp",    "parent": "body", "x": -48, "y": -18, "rotation": -12, "length": 34 },
		    { "name": "leftTool",     "parent": "leftClamp", "y": -34, "rotation": 18, "length": 27 },
		    { "name": "rightClamp",   "parent": "body", "x": 48, "y": -18, "rotation": 12, "length": 34 },
		    { "name": "rightTool",    "parent": "rightClamp", "y": -34, "rotation": -18, "length": 27 },
		    { "name": "leftWheel",    "parent": "body", "x": -24, "y": 66, "length": 32 },
		    { "name": "leftSupport",  "parent": "leftWheel", "y": -32, "length": 30 },
		    { "name": "rightWheel",   "parent": "body", "x": 24, "y": 66, "length": 32 },
		    { "name": "rightSupport", "parent": "rightWheel", "y": -32, "length": 30 }
		  ],
		  "slots": [
		    { "name": "robotBody",    "bone": "body",         "attachment": "torso" },
		    { "name": "robotScreen",  "bone": "screen",       "attachment": "head" },
		    { "name": "robotAntenna", "bone": "antenna",      "attachment": "forearm" },
		    { "name": "leftClamp",    "bone": "leftClamp",    "attachment": "arm" },
		    { "name": "leftTool",     "bone": "leftTool",     "attachment": "forearm" },
		    { "name": "rightClamp",   "bone": "rightClamp",   "attachment": "arm" },
		    { "name": "rightTool",    "bone": "rightTool",    "attachment": "forearm" },
		    { "name": "leftWheel",    "bone": "leftWheel",    "attachment": "leg" },
		    { "name": "leftSupport",  "bone": "leftSupport",  "attachment": "shin" },
		    { "name": "rightWheel",   "bone": "rightWheel",   "attachment": "leg" },
		    { "name": "rightSupport", "bone": "rightSupport", "attachment": "shin" }
		  ],
		  "skins": {
		    "default": { "name": "default", "attachments": {
		      "robotBody":    { "torso":  { "path": "torso", "y": 24, "width": 82, "height": 94 } },
		      "robotScreen":  { "head":   { "path": "head", "width": 52, "height": 52 } },
		      "robotAntenna": { "forearm": { "path": "forearm", "y": -6, "width": 10, "height": 38 } },
		      "leftClamp":    { "arm":    { "path": "arm", "y": -8, "width": 18, "height": 54 } },
		      "leftTool":     { "forearm": { "path": "forearm", "y": -4, "width": 18, "height": 38 } },
		      "rightClamp":   { "arm":    { "path": "arm", "y": -8, "width": 18, "height": 54 } },
		      "rightTool":    { "forearm": { "path": "forearm", "y": -4, "width": 18, "height": 38 } },
		      "leftWheel":    { "leg":    { "path": "leg", "y": -7, "width": 28, "height": 52 } },
		      "leftSupport":  { "shin":   { "path": "shin", "y": -4, "width": 24, "height": 42 } },
		      "rightWheel":   { "leg":    { "path": "leg", "y": -7, "width": 28, "height": 52 } },
		      "rightSupport": { "shin":   { "path": "shin", "y": -4, "width": 24, "height": 42 } }
		    } }
		  },
		  "animations": {
		    "hover": {
		      "bones": {
		        "body": {
		          "rotate": [
		            { "time": 0, "value": -2 },
		            { "time": 0.5, "value": 2 },
		            { "time": 1, "value": -2 }
		          ]
		        },
		        "leftClamp": {
		          "rotate": [
		            { "time": 0, "value": -8 },
		            { "time": 0.5, "value": 8 },
		            { "time": 1, "value": -8 }
		          ]
		        },
		        "rightClamp": {
		          "rotate": [
		            { "time": 0, "value": 8 },
		            { "time": 0.5, "value": -8 },
		            { "time": 1, "value": 8 }
		          ]
		        }
		      }
		    },
		    "signal": {
		      "bones": {
		        "antenna": {
		          "rotate": [
		            { "time": 0, "value": -20 },
		            { "time": 0.2, "value": 20 },
		            { "time": 0.4, "value": -20 },
		            { "time": 0.6, "value": 20 },
		            { "time": 0.8, "value": 0 }
		          ]
		        },
		        "leftTool": {
		          "rotate": [
		            { "time": 0, "value": 0 },
		            { "time": 0.4, "value": -65 },
		            { "time": 0.8, "value": 0 }
		          ]
		        }
		      }
		    }
		  }
		}
		""";

	private ExampleSkeleton() {}
}
