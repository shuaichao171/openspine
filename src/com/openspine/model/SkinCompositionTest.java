package com.openspine.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.openspine.io.ProjectStateStore;
import com.openspine.io.SkeletonExporter;

/** Preview combinations must preserve authoring data, animation state, and persistence. */
public final class SkinCompositionTest {
	public static void main(String[] args) throws Exception {
		combinesAttachmentsAndActivationsWithoutChangingSources();
		retainsLayersDuringStructuralEdits();
		persistsLayersAndCompleteBoneSelection();
		System.out.println("ALL SKIN COMPOSITION TESTS PASSED");
	}

	private static void combinesAttachmentsAndActivationsWithoutChangingSources() {
		SkeletonDocument doc = document();
		String original = SkeletonExporter.toJson(doc);
		doc.setAnimation("switch");
		doc.scrubTo(1.2f);
		doc.setPlaying(false);
		check(doc.setPreviewSkins(List.of("base", "overlay")), "composition accepted");
		check(doc.getCurrentSkinName().equals("overlay") && doc.getEditingSkin() == doc.data.findSkin("overlay"),
			"authoring controls target the highest priority real skin");
		check(doc.skeleton.findSlot("body").getAppliedPose().getAttachment()
			== doc.data.findSkin("overlay").getAttachment(0, "alternate"), "later layer wins animated attachment conflict");
		check(doc.skeleton.findSlot("prop").getAppliedPose().getAttachment()
			== doc.data.findSkin("overlay").getAttachment(1, "prop"), "additional layer supplies an independent slot");
		check(doc.skeleton.getSkin().getBones().size == 1 && doc.skeleton.getSkin().getConstraints().size == 1,
			"skin bones and constraints are unioned without duplicates");
		check(doc.skeleton.findBone("arm").isActive(), "required skin bone is active");
		check(!doc.playing && Math.abs(doc.getCurrentTime() - 1.2f) < 0.0001f, "paused time is retained");
		check(original.equals(SkeletonExporter.toJson(doc)), "combination never adds synthetic authoring skins");
		Skin preview = doc.skeleton.getSkin();
		long revision = doc.revision;
		check(!doc.setPreviewSkins(List.of("base", "missing")) && !doc.setPreviewSkins(List.of("base", "base")),
			"missing and duplicate layers rejected");
		check(doc.skeleton.getSkin() == preview && doc.revision == revision, "invalid selection has no side effects");
		check(doc.setSkin("base"), "single skin clears composition");
		check(doc.skeleton.findSlot("prop").getAppliedPose().getAttachment() == null, "removed layer leaves no stale attachment");
		check(doc.getPreviewSkinNames().equals(List.of("base")), "single selection remains compatible");
		doc.dispose();
	}

	private static void retainsLayersDuringStructuralEdits() {
		SkeletonDocument doc = document();
		SkeletonEditor editor = new SkeletonEditor(doc);
		doc.setPreviewSkins(List.of("base", "overlay"));
		editor.addBone("new", doc.data.getBones().first());
		check(doc.getPreviewSkinNames().equals(List.of("base", "overlay")), "rebuild retains ordered layers");
		editor.renameSkin(doc.data.findSkin("base"), "renamed");
		check(doc.getPreviewSkinNames().equals(List.of("renamed", "overlay")), "renaming retains layer position");
		check(editor.removeSkin(doc.data.findSkin("overlay")), "unreferenced preview skin may be removed");
		check(doc.getPreviewSkinNames().equals(List.of("renamed")), "deleting one layer retains the others");
		doc.dispose();
	}

	private static void persistsLayersAndCompleteBoneSelection() throws Exception {
		Path snapshot = Files.createTempFile("openspine-composition-", ".json");
		try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
			project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("composed", document())));
			SkeletonDocument doc = project.documents().get(0);
			doc.setPreviewSkins(List.of("base", "overlay"));
			doc.setSelectedBones(List.of(doc.skeleton.findBone("arm"), doc.skeleton.findBone("target")));
			Files.writeString(snapshot, ProjectStateStore.snapshotEditorState(project, false));
			doc.setSkin("default");
			doc.setSelectedBone(doc.skeleton.getRootBone());
			ProjectStateStore.restoreEditorState(snapshot, project, true);
			check(doc.getPreviewSkinNames().equals(List.of("base", "overlay")), "autosave restores skin layers");
			check(doc.getSelectedBones().stream().map(bone -> bone.getData().getName()).toList()
				.equals(List.of("arm", "target")), "autosave restores complete ordered bone selection");
			SkeletonDocument detached = new SkeletonDocument();
			detached.adoptLoadedContentFrom(doc);
			check(detached.getPreviewSkinNames().equals(List.of("base", "overlay")), "document adoption retains composition");
			doc.dispose();
			check(detached.getEditingSkin() == detached.data.findSkin("overlay"), "source cleanup does not clear adopted layers");
			detached.dispose();
		} finally { Files.deleteIfExists(snapshot); }
	}

	private static SkeletonDocument document() {
		SkeletonDocument doc = new SkeletonDocument();
		SkeletonData data = doc.data = new SkeletonData();
		data.setName("composed");
		data.setVersion("4.3.0");
		BoneData root = new BoneData(0, "root", null);
		BoneData arm = new BoneData(1, "arm", root);
		BoneData target = new BoneData(2, "target", root);
		arm.setSkinRequired(true);
		for (BoneData bone : List.of(root, arm, target)) {
			bone.getSetupPose().setScale(1f, 1f);
			bone.getSetupPose().setInherit(BoneData.Inherit.normal);
			data.getBones().add(bone);
		}
		SlotData body = new SlotData(0, "body", root), prop = new SlotData(1, "prop", arm);
		body.setBlendMode(com.esotericsoftware.spine.BlendMode.normal);
		prop.setBlendMode(com.esotericsoftware.spine.BlendMode.normal);
		body.setAttachmentName("body");
		prop.setAttachmentName("prop");
		data.getSlots().addAll(body, prop);
		Skin defaults = new Skin("default"), base = new Skin("base"), overlay = new Skin("overlay");
		defaults.setAttachment(0, "body", box("default-body"));
		base.setAttachment(0, "body", box("base-body"));
		base.setAttachment(0, "alternate", box("base-alternate"));
		overlay.setAttachment(0, "alternate", box("overlay-alternate"));
		overlay.setAttachment(1, "prop", box("prop"));
		IkConstraintData ik = new IkConstraintData("aim");
		ik.getBones().add(arm);
		ik.setTarget(target);
		ik.setSkinRequired(true);
		ik.getSetupPose().setBendDirection(1);
		data.getConstraints().add(ik);
		for (Skin skin : List.of(base, overlay)) { skin.getBones().add(arm); skin.getConstraints().add(ik); }
		data.getSkins().addAll(defaults, base, overlay);
		data.setDefaultSkin(defaults);
		Animation animation = new Animation("switch");
		Animation.AttachmentTimeline track = new Animation.AttachmentTimeline(2, 0);
		track.setFrame(0, 0f, "body");
		track.setFrame(1, 1f, "alternate");
		Array<Animation.Timeline> tracks = new Array<>(true, 1, Animation.Timeline[]::new);
		tracks.add(track);
		animation.setTimelines(tracks, new IntArray());
		animation.setDuration(2f);
		data.getAnimations().add(animation);
		doc.skeleton = new Skeleton(data);
		doc.animationState = doc.newAnimationState();
		doc.setSkin("default");
		return doc;
	}

	private static BoundingBoxAttachment box(String name) {
		BoundingBoxAttachment box = new BoundingBoxAttachment(name);
		box.setVertices(new float[] {0, 0, 10, 0, 0, 10});
		box.setWorldVerticesLength(6);
		return box;
	}

	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private SkinCompositionTest() {}
}
