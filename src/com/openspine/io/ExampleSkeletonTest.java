package com.openspine.io;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonJson;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;

/** Headless checks of portable source pixels, atlas metadata and complete built-in project packaging. */
public final class ExampleSkeletonTest {
	private static final String[] NAMES = {"torso", "head", "arm", "forearm", "leg", "shin"};
	private static final int[] WIDTHS = {240, 256, 80, 64, 96, 80}, HEIGHTS = {480, 256, 280, 240, 320, 280};

	public static void main(String[] args) throws Exception {
		GdxNativesLoader.load();
		Path targetDirectory = Files.createTempDirectory("openspine-example-save-test-");
		Path characterDirectory, robotDirectory;
		try (var character = ExampleSkeleton.prepareResources(false); var robot = ExampleSkeleton.prepareResources(true)) {
			characterDirectory = character.directory();
			robotDirectory = robot.directory();
			check(!characterDirectory.equals(robotDirectory), "the two built-ins own independent source directories");
			checkSources(character);
			checkSources(robot);
			for (String name : NAMES) {
				BufferedImage first = ImageIO.read(characterDirectory.resolve(name + ".png").toFile());
				BufferedImage second = ImageIO.read(robotDirectory.resolve(name + ".png").toFile());
				check(first.getRGB(first.getWidth() / 2, first.getHeight() / 2)
					!= second.getRGB(second.getWidth() / 2, second.getHeight() / 2), "built-in palettes stay distinct: " + name);
			}
			try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
				project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("Character", document(false, character)),
					new SkeletonProject.LoadedSkeleton("Robot", document(true, robot))));
				Path manifest = targetDirectory.resolve(ProjectPackageManifest.FILE_NAME);
				NativeProjectStore.SaveResult saved = NativeProjectStore.save(project, true, manifest,
					NativeProjectStore.inspect(manifest), new CancellationToken());
				for (int index = 0; index < 2; index++) {
					var runtime = saved.skeletons().get(index);
					var loaded = SkeletonLoader.loadAnimationSource(new FileHandle(runtime.runtime().toFile()));
					check(loaded.getBones().size == project.documents().get(index).data.getBones().size
						&& loaded.getAnimations().size == project.documents().get(index).data.getAnimations().size,
						"complete built-in animation data survives native save and the official runtime reader");
					Path original = index == 0 ? characterDirectory : robotDirectory;
					for (String name : NAMES)
						check(Arrays.equals(Files.readAllBytes(original.resolve(name + ".png")),
							Files.readAllBytes(runtime.runtime().getParent().resolve(name + ".png"))),
							"native save includes unmodified source image bytes: " + index + "/" + name);
					check(Arrays.equals(Files.readAllBytes(original.resolve("example.atlas")),
						Files.readAllBytes(runtime.runtime().getParent().resolve(runtime.atlas()))),
						"native save retains all atlas settings");
				}
			}
		} finally {
			try (var paths = Files.walk(targetDirectory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		check(!Files.exists(characterDirectory) && !Files.exists(robotDirectory), "closing sources cleans both unique directories");
		System.out.println("ALL EXAMPLE SKELETON TESTS PASSED");
	}

	private static void checkSources(ExampleSkeleton.PreparedResources resources) throws Exception {
		TextureAtlasData atlas = new TextureAtlasData(new FileHandle(resources.atlasFile().toFile()),
			new FileHandle(resources.directory().toFile()), false);
		check(atlas.getPages().size == 6 && atlas.getRegions().size == 6, "each built-in has six complete pages and regions");
		for (int index = 0; index < NAMES.length; index++) {
			var region = atlas.getRegions().get(index);
			check(region.name.equals(NAMES[index]) && region.width == WIDTHS[index] && region.height == HEIGHTS[index]
				&& region.originalWidth == WIDTHS[index] && region.originalHeight == HEIGHTS[index]
				&& region.left == 0 && region.top == 0 && region.offsetX == 0f && region.offsetY == 0f
				&& !region.rotate && !region.flip && region.index == -1, "region size, full-image UV and orientation match: " + region.name);
			var page = region.page;
			check(page.useMipMaps && page.minFilter == TextureFilter.MipMapLinearLinear && page.magFilter == TextureFilter.Linear
				&& page.uWrap == TextureWrap.ClampToEdge && page.vWrap == TextureWrap.ClampToEdge && !page.pma,
				"disk atlas keeps the in-memory sampling and alpha settings");
			BufferedImage image = ImageIO.read(page.textureFile.file());
			check(image != null && image.getWidth() == WIDTHS[index] && image.getHeight() == HEIGHTS[index],
				"PNG keeps the existing four-times-resolution source dimensions");
			check((image.getRGB(WIDTHS[index] / 2, HEIGHTS[index] / 2) >>> 24) == 255, "shape centers stay opaque");
			check((image.getRGB(0, 0) >>> 24) == (index == 1 ? 0 : 255), "circle transparency and rectangle borders stay intact");
		}
	}

	private static SkeletonDocument document(boolean alternate, ExampleSkeleton.PreparedResources resources) {
		var data = new SkeletonJson(new SmokeTest.StubLoader()).readSkeletonData(new JsonReader().parse(
			alternate ? ExampleSkeleton.ROBOT_JSON : ExampleSkeleton.JSON));
		SkeletonDocument document = new SkeletonDocument();
		document.replaceContent(new TextureAtlas(), data, new Skeleton(data), "builtin:example", "", null);
		document.resourceDirectory = resources.directory().toString();
		document.atlasFileName = resources.atlasFile().getFileName().toString();
		document.animationState = document.newAnimationState();
		return document;
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
