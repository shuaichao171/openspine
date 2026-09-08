package com.openspine.io;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.ScreenUtils;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Hidden GL check: built-in source atlases and the saved/reopened project must draw identical pixels. */
public final class ExampleResourcesCapture {
	private static final int WIDTH = 900, HEIGHT = 360;
	private static final String[] REGIONS = {"torso", "head", "arm", "forearm", "leg", "shin"};
	private static Throwable failure;

	public static void main(String[] args) throws Exception {
		Path output = Path.of(args.length == 0 ? "artifacts/round3-ui-check/example-resources.png" : args[0]).toAbsolutePath();
		Path temporary = Files.createTempDirectory("openspine-example-render-");
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("OpenSpine hidden built-in resources check");
		config.setWindowedMode(WIDTH, HEIGHT);
		config.setInitialVisible(false);
		config.disableAudio(true);
		config.setForegroundFPS(30);
		try {
			new Lwjgl3Application(new ApplicationAdapter() {
				@Override public void create() {
					try { verify(temporary, output); }
					catch (Throwable error) { failure = error; }
					finally { Gdx.app.exit(); }
				}
			}, config);
		} finally {
			try (var paths = Files.walk(temporary)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		if (failure != null) throw new AssertionError("Hidden built-in resource verification failed", failure);
		System.out.println("OK: both built-ins save/reopen with identical atlas metadata and rendered pixels; hidden window and resources closed");
	}

	private static void verify(Path temporary, Path output) throws Exception {
		try (var character = ExampleSkeleton.prepareResources(false); var robot = ExampleSkeleton.prepareResources(true);
			SkeletonProject source = new SkeletonProject(new SkeletonDocument())) {
			source.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("Character", document(false, character)),
				new SkeletonProject.LoadedSkeleton("Robot", document(true, robot))));
			Path manifest = temporary.resolve(ProjectPackageManifest.FILE_NAME);
			NativeProjectStore.save(source, true, manifest, NativeProjectStore.inspect(manifest), new CancellationToken());
			var reopened = ProjectPackageManifest.load(manifest);
			try {
				BufferedImage comparison = new BufferedImage(WIDTH * 2, (HEIGHT + 32) * 2, BufferedImage.TYPE_INT_ARGB);
				var graphics = comparison.createGraphics();
				try {
					graphics.setColor(new Color(238, 241, 245));
					graphics.fillRect(0, 0, comparison.getWidth(), comparison.getHeight());
					graphics.setFont(new Font("Arial", Font.BOLD, 16));
					for (int index = 0; index < 2; index++) {
						TextureAtlas before = source.documents().get(index).atlas, after = reopened.skeletons().get(index).document().atlas;
						check(before.getRegions().size == after.getRegions().size, "save/reopen keeps every built-in region");
						for (String name : REGIONS) {
							var first = before.findRegion(name); var second = after.findRegion(name);
							check(first.originalWidth == second.originalWidth && first.originalHeight == second.originalHeight
								&& first.packedWidth == second.packedWidth && first.packedHeight == second.packedHeight
								&& first.offsetX == second.offsetX && first.offsetY == second.offsetY && first.rotate == second.rotate
								&& first.getU() == second.getU() && first.getV() == second.getV()
								&& first.getU2() == second.getU2() && first.getV2() == second.getV2()
								&& first.getTexture().getMinFilter() == second.getTexture().getMinFilter()
								&& first.getTexture().getMagFilter() == second.getTexture().getMagFilter(), "atlas mapping/sampling changed: " + name);
						}
						byte[] originalPixels = render(before), savedPixels = render(after);
						check(Arrays.equals(originalPixels, savedPixels), "rendered pixels changed for " + source.nameAt(index));
						int y = index * (HEIGHT + 32);
						graphics.setColor(new Color(30, 40, 55));
						graphics.drawString(source.nameAt(index) + " / original in-memory atlas", 16, y + 22);
						graphics.drawString(source.nameAt(index) + " / native project saved and reopened", WIDTH + 16, y + 22);
						graphics.drawImage(image(originalPixels), 0, y + 32, null);
						graphics.drawImage(image(savedPixels), WIDTH, y + 32, null);
					}
				} finally { graphics.dispose(); }
				Files.createDirectories(output.getParent());
				ImageIO.write(comparison, "png", output.toFile());
			} finally {
				for (var item : reopened.skeletons()) item.document().dispose();
			}
		}
	}

	private static SkeletonDocument document(boolean alternate, ExampleSkeleton.PreparedResources resources) {
		SkeletonDocument document = new SkeletonDocument();
		try {
			SkeletonLoader.loadFromJsonString(document, alternate ? ExampleSkeleton.ROBOT_JSON : ExampleSkeleton.JSON,
				alternate ? ExampleSkeleton.createRobotAtlas() : ExampleSkeleton.createAtlas());
			document.animationState = document.newAnimationState();
			document.resourceDirectory = resources.directory().toString();
			document.atlasFileName = resources.atlasFile().getFileName().toString();
			return document;
		} catch (RuntimeException | Error error) { document.dispose(); throw error; }
	}

	private static byte[] render(TextureAtlas atlas) {
		ScreenUtils.clear(0.075f, 0.09f, 0.12f, 1f);
		SpriteBatch batch = new SpriteBatch();
		try {
			batch.begin();
			for (int index = 0; index < REGIONS.length; index++) {
				var region = atlas.findRegion(REGIONS[index]);
				batch.draw(region, 20 + 140 * index, 100, region.getRegionWidth() * 0.5f, region.getRegionHeight() * 0.5f);
				batch.draw(region, 20 + 140 * index, 20, region.getRegionWidth() * 0.125f, region.getRegionHeight() * 0.125f);
			}
			batch.end();
			return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, true);
		} finally { batch.dispose(); }
	}

	private static BufferedImage image(byte[] rgba) {
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		int[] argb = new int[WIDTH * HEIGHT];
		for (int i = 0; i < argb.length; i++)
			argb[i] = (rgba[i * 4 + 3] & 255) << 24 | (rgba[i * 4] & 255) << 16
				| (rgba[i * 4 + 1] & 255) << 8 | rgba[i * 4 + 2] & 255;
		image.setRGB(0, 0, WIDTH, HEIGHT, argb, 0, WIDTH);
		return image;
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
