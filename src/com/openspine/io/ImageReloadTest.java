package com.openspine.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;

/** Resource provenance and failure checks that do not require OpenGL. */
public final class ImageReloadTest {
	public static void main(String[] args) throws Exception {
		Path temporary = Files.createTempDirectory("openspine-image-reload-").toRealPath();
		try {
			usesCurrentAuthorImagesPath(temporary);
			resolvesSelectedAtlasWithoutChangingResourceBase(temporary);
			rejectsMissingSelectedAtlasInsteadOfFallingBack(temporary);
			supportsAbsoluteSourcesAndOriginalFileFallback(temporary);
			rejectsMissingAndEmptySourcesWithoutChangingDocument(temporary);
			candidateOwnershipTransfersExactlyOnce();
		} finally {
			try (var paths = Files.walk(temporary)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		System.out.println("ALL IMAGE RELOAD TESTS PASSED");
	}

	private static void usesCurrentAuthorImagesPath(Path temporary) throws Exception {
		Path original = temporary.resolve("stale.json");
		Files.writeString(original, "{\"skeleton\":{\"images\":\"old-images\"}}");
		Path resources = Files.createDirectories(temporary.resolve("resources"));
		Path editedImages = Files.createDirectories(resources.resolve("edited-images/角色"));
		try (DocumentFixture fixture = new DocumentFixture()) {
			SkeletonDocument doc = fixture.doc;
			doc.filePath = original.toString();
			doc.resourceDirectory = resources.toString();
			doc.data.setImagesPath("edited-images/角色");
			ImageReload.Source source = ImageReload.source(doc);
			require(source.imagesDirectory().equals(editedImages), "reload uses edited image path and explicit resource base");
			require(source.atlasFile() == null && source.atlasFileName() == null, "loose images do not pick a sibling atlas");
			Files.delete(original);
			require(ImageReload.source(doc).equals(source), "reload does not require the original skeleton JSON to exist");
		}
	}

	private static void resolvesSelectedAtlasWithoutChangingResourceBase(Path temporary) throws Exception {
		Path resourceBase = Files.createDirectories(temporary.resolve("atlas-source"));
		Path atlas = Files.createDirectories(resourceBase.resolve("textures")).resolve("角色.atlas");
		Files.writeString(atlas, "");
		try (DocumentFixture fixture = new DocumentFixture()) {
			SkeletonDocument doc = fixture.doc;
			doc.resourceDirectory = resourceBase.toString();
			doc.atlasFileName = "textures/角色.atlas";
			doc.data.setImagesPath("missing-images");
			ImageReload.Source source = ImageReload.source(doc);
			require(source.atlasFile().equals(atlas), "the selected nested atlas takes precedence");
			require(source.resourceDirectory().equals(doc.resourceDirectory)
				&& source.atlasFileName().equals(doc.atlasFileName), "reload keeps image/audio base and relative atlas path");
		}
	}

	private static void rejectsMissingSelectedAtlasInsteadOfFallingBack(Path temporary) throws Exception {
		Path images = Files.createDirectories(temporary.resolve("valid-loose-images"));
		try (DocumentFixture fixture = new DocumentFixture()) {
			SkeletonDocument doc = fixture.doc;
			doc.resourceDirectory = temporary.toString();
			doc.atlasFileName = "missing-selected.atlas";
			doc.data.setImagesPath(images.toString());
			assertUnchangedFailure(doc, "当前图集文件不存在");
		}
	}

	private static void supportsAbsoluteSourcesAndOriginalFileFallback(Path temporary) throws Exception {
		Path atlas = temporary.resolve("absolute.atlas");
		Files.writeString(atlas, "");
		Path images = Files.createDirectories(temporary.resolve("absolute-images"));
		try (DocumentFixture fixture = new DocumentFixture()) {
			SkeletonDocument doc = fixture.doc;
			doc.atlasFileName = atlas.toString();
			require(ImageReload.source(doc).atlasFile().equals(atlas), "absolute atlas works without a skeleton path");
			doc.atlasFileName = null;
			doc.data.setImagesPath(images.toString());
			require(ImageReload.source(doc).imagesDirectory().equals(images), "absolute images work in an unsaved document");
			doc.data.setImagesPath("absolute-images");
			doc.filePath = temporary.resolve("original.skel").toString();
			doc.savePath = temporary.resolve("unrelated/save.json").toString();
			require(ImageReload.source(doc).imagesDirectory().equals(images), "original file base is used for runtime assets");
			doc.filePath = "builtin:example";
			assertUnchangedFailure(doc, "没有外部资源目录");
		}
	}

	private static void rejectsMissingAndEmptySourcesWithoutChangingDocument(Path temporary) throws Exception {
		try (DocumentFixture fixture = new DocumentFixture()) {
			SkeletonDocument doc = fixture.doc;
			doc.filePath = "builtin:example";
			doc.atlasFileName = "builtin-example";
			assertUnchangedFailure(doc, "没有外部图片来源");
			doc.resourceDirectory = temporary.toString();
			doc.atlasFileName = null;
			doc.data.setImagesPath("missing-images");
			assertUnchangedFailure(doc, "当前图片目录不存在");
			doc.data.setImagesPath(Files.createDirectories(temporary.resolve("empty-images")).toString());
			assertUnchangedFailure(doc, "没有可加载的图片区域");
			doc.data.setImagesPath("invalid\0images");
			assertUnchangedFailure(doc, "图片来源路径无效");
		}
	}

	private static void candidateOwnershipTransfersExactlyOnce() {
		CountingAtlas abandoned = new CountingAtlas();
		ImageReload.Prepared prepared = new ImageReload.Prepared(abandoned, "atlas", "base", false, "source");
		prepared.close(); prepared.close();
		require(abandoned.disposals == 1, "abandoned candidate closes once");
		try { prepared.release(); throw new AssertionError("closed candidate cannot transfer ownership"); }
		catch (IllegalStateException expected) { }
		CountingAtlas adopted = new CountingAtlas();
		try (ImageReload.Prepared accepted = new ImageReload.Prepared(adopted, null, null, false, "source")) {
			require(accepted.release() == adopted, "release hands candidate to the document");
		}
		require(adopted.disposals == 0, "closing a transferred candidate keeps the document's texture alive");
		adopted.dispose();
	}

	private static void assertUnchangedFailure(SkeletonDocument doc, String message) {
		EditHistory history = new EditHistory(doc); history.markSaved();
		String before = SkeletonExporter.toJson(doc);
		TextureAtlas atlas = doc.atlas;
		Skeleton skeleton = doc.skeleton;
		SkeletonData data = doc.data;
		long revision = doc.revision;
		try (ImageReload.Prepared unexpected = ImageReload.prepare(doc)) {
			throw new AssertionError("reload should fail: " + message);
		} catch (IllegalArgumentException expected) {
			require(expected.getMessage().contains(message), "unexpected reload error: " + expected.getMessage());
		}
		require(before.equals(SkeletonExporter.toJson(doc)) && doc.atlas == atlas && doc.skeleton == skeleton
			&& doc.data == data && doc.revision == revision && !doc.dirty && !history.canUndo() && !history.canRedo(),
			"failed reload keeps authored content, resource identities, revision, and history");
	}

	private static final class CountingAtlas extends TextureAtlas {
		int disposals;
		@Override public void dispose() { disposals++; super.dispose(); }
	}

	private static final class DocumentFixture implements AutoCloseable {
		final SkeletonDocument doc = new SkeletonDocument();
		DocumentFixture() {
			doc.data = new SkeletonData();
			doc.data.setVersion("4.3.0");
			BoneData root = new BoneData(0, "root", null);
			root.getSetupPose().setInherit(BoneData.Inherit.normal);
			doc.data.getBones().add(root);
			doc.skeleton = new Skeleton(doc.data);
			doc.atlas = new TextureAtlas();
		}
		@Override public void close() { doc.dispose(); }
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
