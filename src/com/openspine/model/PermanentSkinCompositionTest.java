package com.openspine.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.HasSequence;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.Sequence.SequenceMode;
import com.esotericsoftware.spine.attachments.VertexAttachment;
import com.openspine.io.SkeletonBinaryExporter;
import com.openspine.io.SkeletonExporter;
import com.openspine.io.SkeletonLoader;

/** Permanent combinations preserve rendered geometry and attachment animation through both Spine formats. */
public final class PermanentSkinCompositionTest {
	private static final float[] TIMES = {0f, 0.15f, 0.3f, 0.65f, 0.9f, 1.2f};
	public static void main(String[] args) throws Exception {
		independentSnapshotMatchesPreviewAndBothFormats(false);
		independentSnapshotMatchesPreviewAndBothFormats(true);
		invalidDraftsAreAtomic();
		System.out.println("ALL PERMANENT SKIN COMPOSITION TESTS PASSED");
	}

	private static void independentSnapshotMatchesPreviewAndBothFormats(boolean weighted) throws Exception {
		SkeletonDocument doc = document(weighted);
		try {
			String original = SkeletonExporter.toJson(doc);
			Skin base = doc.data.findSkin("base"), overlay = doc.data.findSkin("overlay");
			MeshAttachment source = (MeshAttachment)base.getAttachment(0, "image");
			MeshAttachment inherited = (MeshAttachment)overlay.getAttachment(1, "image");
			MeshAttachment independent = (MeshAttachment)overlay.getAttachment(2, "image");
			Animation animation = doc.data.findAnimation("motion");
			List<Animation.Timeline> originalTracks = new ArrayList<>();
			for (Animation.Timeline track : animation.getTimelines()) originalTracks.add(track);
			List<List<Frame>> expected = samples(doc);
			doc.scrubTo(0.65f);
			doc.setPlaying(false);
			String lockKey = SkeletonDocument.meshWeightStateKey("overlay", "slot2", "image");
			Set<SkeletonDocument.MeshWeightLock> locks = Set.of(new SkeletonDocument.MeshWeightLock(1, 0));
			doc.setMeshWeightLocks(lockKey, locks);
			SkeletonEditor editor = new SkeletonEditor(doc);
			String reason = editor.getSkinCompositionBlockReason(List.of("base", "overlay"), " saved ");
			check(reason == null, "valid rich draft: " + reason);
			SkeletonEditor.SkinCompositionEdit edit = editor.createSkinComposition(List.of("base", "overlay"), " saved ");
			Skin saved = edit.skin();
			check(saved.getName().equals("saved") && doc.getPreviewSkinNames().equals(List.of("saved")), "saved skin selected and trimmed");
			near(0.65f, doc.getCurrentTime(), "paused cursor retained");
			check(!doc.playing, "paused playback retained");
			check(saved.getBones().size == 2 && saved.getConstraints().size == 1, "explicit skin activations unioned without duplicates");
			check(!saved.getBones().contains(doc.data.findBone("default-only"), true), "implicit fallback does not activate default-only bones");
			check(saved.getAttachment(4, "image").getName().equals("default-fallback"), "implicit default attachment included");
			check(saved.getAttachment(5, "image").getName().equals("overlay-winner"), "later matching slot/placeholder wins");
			for (int slot = 0; slot < 3; slot++) {
				MeshAttachment copy = (MeshAttachment)saved.getAttachment(slot, "image");
				check(copy.getSourceMesh() == null && copy.getTimelineAttachment() == copy && copy.getTimelineSlots().length == 0,
					"mesh clone owns geometry and animation at slot " + slot);
				check(copy.getVertices() != source.getVertices() && copy.getRegionUVs() != source.getRegionUVs()
					&& copy.getTriangles() != source.getTriangles(), "all geometry independently copied");
			}
			check(inherited.getSourceMesh() == source && inherited.getTimelineAttachment() == source
				&& independent.getSourceMesh() == source && independent.getTimelineAttachment() == independent,
				"source linked ownership is untouched");
			check(Arrays.equals(source.getTimelineSlots(), new int[] {1}), "source cross-slot activation untouched");
			check(doc.meshWeightLocks(SkeletonDocument.meshWeightStateKey("saved", "slot2", "image")).equals(locks), "mesh locks copied");
			check(animation.getTimelines().size == originalTracks.size() + 8, "only winning effective attachment tracks copied");
			for (int i = 0; i < originalTracks.size(); i++) check(animation.getTimelines().get(i) == originalTracks.get(i), "source tracks retain identity");
			compare(expected, samples(doc), "saved snapshot");
			Path directory = Files.createTempDirectory("openspine-permanent-skin-");
			Path json = directory.resolve("saved.json"), binary = directory.resolve("saved.skel");
			try {
				SkeletonExporter.writeAtomic(doc, json);
				SkeletonBinaryExporter.writeAtomic(doc, binary);
				for (Path path : List.of(json, binary)) {
					SkeletonDocument loaded = loaded(SkeletonLoader.loadAnimationSource(new FileHandle(path.toFile())));
					try {
						check(loaded.setSkin("saved"), "saved skin is available in " + path.getFileName());
						loaded.setAnimation("motion");
						compare(expected, samples(loaded), path.getFileName().toString());
						Skin roundtrip = loaded.data.findSkin("saved");
						check(roundtrip.getBones().size == 2 && roundtrip.getConstraints().size == 1, "activation union roundtrip");
						for (int slot = 0; slot < 4; slot++) {
							Sequence sequence = ((HasSequence)roundtrip.getAttachment(slot, "image")).getSequence();
							check(sequence.getRegions().length == 3 && sequence.getStart() == 7 && sequence.getDigits() == 2
								&& sequence.getSetupIndex() == 1, "Sequence settings roundtrip");
						}
					} finally { loaded.dispose(); }
				}
			} finally { Files.deleteIfExists(json); Files.deleteIfExists(binary); Files.deleteIfExists(directory); }
			edit.undo();
			check(doc.getPreviewSkinNames().equals(List.of("base", "overlay")) && doc.data.findSkin("saved") == null,
				"undo removes composed skin and restores preview");
			check(original.equals(SkeletonExporter.toJson(doc)), "undo returns exact source authoring JSON");
			check(doc.meshWeightLocks(SkeletonDocument.meshWeightStateKey("saved", "slot2", "image")).isEmpty(), "undo removes copied locks");
			edit.redo();
			check(doc.data.findSkin("saved") == saved, "redo retains generated skin identity");
			compare(expected, samples(doc), "redo snapshot");
			MeshAttachment copy = (MeshAttachment)saved.getAttachment(0, "image");
			float copyVertex = copy.getVertices()[0];
			source.getVertices()[0] += 14f;
			source.getSequence().setStart(99);
			AnimationEditor ae = new AnimationEditor(doc);
			Animation.DeformTimeline sourceTrack = ae.findDeformTimeline(animation, 0, source);
			Animation.DeformTimeline copiedTrack = ae.findDeformTimeline(animation, 0, copy);
			float copyFrame = copiedTrack.getVertices()[1][0];
			sourceTrack.getVertices()[1][0] += 30f;
			near(copyVertex, copy.getVertices()[0], "source geometry changes do not reach snapshot");
			near(copyFrame, copiedTrack.getVertices()[1][0], "source deform changes do not reach snapshot");
			check(copy.getSequence().getStart() == 7 && copy.getSequence() != source.getSequence(), "Sequence settings independently owned");
		} finally { doc.dispose(); }
	}

	private static void invalidDraftsAreAtomic() {
		SkeletonDocument doc = document(false);
		try {
			SkeletonEditor editor = new SkeletonEditor(doc);
			String before = SkeletonExporter.toJson(doc);
			long revision = doc.revision;
			for (String name : new String[] {"", " ", "base", " BASE ", "default"})
				throwsArgument(() -> editor.createSkinComposition(List.of("base", "overlay"), name));
			for (List<String> layers : List.of(List.<String>of(), List.of("missing"), List.of("base", "base")))
				throwsArgument(() -> editor.createSkinComposition(layers, "saved"));
			check(before.equals(SkeletonExporter.toJson(doc)) && revision == doc.revision, "invalid drafts cannot mutate authoring data");
			MeshAttachment source = (MeshAttachment)doc.data.findSkin("base").getAttachment(0, "image");
			short[] triangles = source.getTriangles(); source.setTriangles(new short[] {0, 1, 2});
			throwsArgument(() -> editor.createSkinComposition(List.of("base", "overlay"), "incomplete"));
			check(doc.data.findSkin("incomplete") == null && doc.revision == revision, "incomplete triangulation rejected before publishing");
			source.setTriangles(triangles);
			Attachment box = doc.data.getDefaultSkin().getAttachment(4, "image");
			box.setTimelineAttachment(source);
			throwsArgument(() -> editor.createSkinComposition(List.of("base", "overlay"), "nonmesh-inheritance"));
			check(doc.revision == revision, "nonmesh track inheritance rejection is atomic");
			box.setTimelineAttachment(box);
			MeshAttachment inherited = (MeshAttachment)doc.data.findSkin("overlay").getAttachment(1, "image");
			MeshAttachment independent = (MeshAttachment)doc.data.findSkin("overlay").getAttachment(2, "image");
			inherited.setSourceMesh(independent);
			throwsArgument(() -> editor.createSkinComposition(List.of("overlay"), "nested"));
			check(doc.data.findSkin("nested") == null && doc.revision == revision, "nested linked mesh rejected atomically");
			inherited.setSourceMesh(independent.getSourceMesh());
			doc.data.getDefaultSkin().getBones().add(doc.data.findBone("default-only"));
			Skin activation = editor.createSkinComposition(List.of("base", "overlay"), "activation").skin();
			check(!activation.getBones().contains(doc.data.findBone("default-only"), true),
				"implicit default fallback must not union default activation metadata");
		} finally { doc.dispose(); }
	}

	public static SkeletonDocument document(boolean weighted) {
		SkeletonData data = new SkeletonData();
		data.setName("skin-composition"); data.setVersion("4.3.0");
		BoneData root = bone(data, "root", null), arm = bone(data, "arm", root), target = bone(data, "target", root);
		BoneData fallback = bone(data, "default-only", root);
		arm.setSkinRequired(true); target.setSkinRequired(true); fallback.setSkinRequired(true);
		for (int index = 0; index < 6; index++) {
			SlotData slot = new SlotData(index, "slot" + index, root);
			slot.setBlendMode(BlendMode.normal); slot.setAttachmentName("image"); data.getSlots().add(slot);
		}
		Skin defaults = new Skin("default"), base = new Skin("base"), overlay = new Skin("overlay");
		data.getSkins().addAll(defaults, base, overlay); data.setDefaultSkin(defaults);
		base.getBones().add(arm); overlay.getBones().addAll(arm, target);
		IkConstraintData ik = new IkConstraintData("aim");
		ik.getBones().add(arm); ik.setTarget(root); ik.setSkinRequired(true); ik.getSetupPose().setBendDirection(1);
		data.getConstraints().add(ik); base.getConstraints().add(ik); overlay.getConstraints().add(ik);
		MeshAttachment source = mesh("source", weighted);
		MeshAttachment inherited = linked("inherited", source, true), independent = linked("independent", source, false);
		source.setTimelineSlots(new int[] {1});
		base.setAttachment(0, "image", source);
		overlay.setAttachment(1, "image", inherited); overlay.setAttachment(2, "image", independent);
		RegionAttachment region = new RegionAttachment("region-sequence", sequence());
		region.setPath("region"); region.setWidth(64f); region.setHeight(32f); region.updateSequence();
		base.setAttachment(3, "image", region);
		BoundingBoxAttachment box = box("default-fallback"); defaults.setAttachment(4, "image", box);
		MeshAttachment loser = mesh("lower-loser", weighted); base.setAttachment(5, "image", loser);
		overlay.setAttachment(5, "image", box("overlay-winner"));
		Array<Animation.Timeline> tracks = new Array<>(true, 10, Animation.Timeline[]::new);
		tracks.add(deform(source, 0, 12f), sequenceTrack(source, 0, false));
		tracks.add(deform(independent, 2, -8f), sequenceTrack(independent, 2, true));
		tracks.add(sequenceTrack(region, 3, true), deform(box, 4, 3f));
		tracks.add(deform(loser, 5, 99f), sequenceTrack(loser, 5, false));
		Animation motion = new Animation("motion"); motion.setTimelines(tracks, new IntArray()); motion.setDuration(1.5f);
		data.getAnimations().add(motion);
		SkeletonDocument doc = loaded(data);
		doc.setPreviewSkins(List.of("base", "overlay")); doc.setAnimation("motion"); doc.setPlaying(false);
		return doc;
	}

	private static SkeletonDocument loaded(SkeletonData data) {
		SkeletonDocument doc = new SkeletonDocument(); doc.data = data; doc.skeleton = new Skeleton(data);
		doc.animationState = doc.newAnimationState(); doc.setSkin("default"); return doc;
	}
	private static BoneData bone(SkeletonData data, String name, BoneData parent) {
		BoneData bone = new BoneData(data.getBones().size, name, parent);
		bone.getSetupPose().setScale(1f, 1f); bone.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(bone); return bone;
	}
	private static Sequence sequence() {
		Sequence sequence = new Sequence(3, true); sequence.setStart(7); sequence.setDigits(2); sequence.setSetupIndex(1); return sequence;
	}
	private static MeshAttachment mesh(String name, boolean weighted) {
		MeshAttachment mesh = new MeshAttachment(name, sequence()); mesh.setPath("mesh");
		float[] vertices = {0, 100, 100, 100, 100, 0, 0, 0};
		if (weighted) {
			mesh.setBones(new int[] {1, 0, 1, 0, 1, 0, 1, 0});
			float[] weights = new float[12];
			for (int i = 0; i < 4; i++) { weights[i * 3] = vertices[i * 2]; weights[i * 3 + 1] = vertices[i * 2 + 1]; weights[i * 3 + 2] = 1f; }
			mesh.setVertices(weights);
		} else mesh.setVertices(vertices);
		mesh.setWorldVerticesLength(8); mesh.setRegionUVs(new float[] {0, 0, 1, 0, 1, 1, 0, 1});
		mesh.setTriangles(new short[] {0, 1, 2, 2, 3, 0}); mesh.setHullLength(8);
		mesh.setEdges(new short[] {0, 1, 1, 2, 2, 3, 3, 0}); mesh.setWidth(100); mesh.setHeight(100); mesh.updateSequence(); return mesh;
	}
	private static MeshAttachment linked(String name, MeshAttachment source, boolean inherit) {
		MeshAttachment mesh = new MeshAttachment(name, sequence()); mesh.setPath("mesh"); mesh.setSourceMesh(source);
		mesh.setTimelineAttachment(inherit ? source : mesh); mesh.updateSequence(); return mesh;
	}
	private static BoundingBoxAttachment box(String name) {
		BoundingBoxAttachment box = new BoundingBoxAttachment(name); box.setWorldVerticesLength(6);
		box.setVertices(new float[] {0, 0, 10, 0, 0, 10}); return box;
	}
	private static Animation.DeformTimeline deform(VertexAttachment attachment, int slot, float delta) {
		Animation.DeformTimeline track = new Animation.DeformTimeline(3, 1, slot, attachment);
		float[] start = attachment.getBones() == null ? attachment.getVertices().clone() : new float[attachment.getWorldVerticesLength()];
		float[] end = start.clone(); end[0] += delta; end[3] -= delta / 2f;
		track.setFrame(0, 0f, start); track.setFrame(1, 0.8f, end); track.setFrame(2, 1.3f, start.clone());
		track.setBezier(0, 0, 0, 0f, 0f, 0.18f, 0.1f, 0.61f, 0.86f, 0.8f, 1f); track.setStepped(1); return track;
	}
	private static Animation.SequenceTimeline sequenceTrack(Attachment attachment, int slot, boolean reverse) {
		Animation.SequenceTimeline track = new Animation.SequenceTimeline(2, slot, attachment);
		track.setFrame(0, 0f, SequenceMode.hold, reverse ? 2 : 1, 0f);
		track.setFrame(1, 0.4f, reverse ? SequenceMode.pingpongReverse : SequenceMode.loop, 0, 0.125f); return track;
	}
	private record Frame(String name, String path, int sequence, float[] deform, float[] world) {}
	private static List<List<Frame>> samples(SkeletonDocument doc) {
		List<List<Frame>> frames = new ArrayList<>();
		for (float time : TIMES) {
			doc.scrubTo(time); List<Frame> slots = new ArrayList<>();
			for (var slot : doc.skeleton.getSlots()) {
				var pose = slot.getAppliedPose(); Attachment attachment = pose.getAttachment(); float[] world = new float[0];
				if (attachment instanceof VertexAttachment vertex) { world = new float[vertex.getWorldVerticesLength()]; vertex.computeWorldVertices(doc.skeleton, slot, 0, world.length, world, 0, 2); }
				slots.add(new Frame(attachment == null ? "" : attachment.getName(), attachment instanceof HasSequence sequence ? sequence.getPath() : "",
					pose.getSequenceIndex(), pose.getDeform().toArray(), world));
			}
			frames.add(slots);
		}
		return frames;
	}
	private static void compare(List<List<Frame>> expected, List<List<Frame>> actual, String context) {
		for (int t = 0; t < TIMES.length; t++) for (int slot = 0; slot < expected.get(t).size(); slot++) {
			Frame a = expected.get(t).get(slot), b = actual.get(t).get(slot); String at = context + " t=" + TIMES[t] + " slot=" + slot;
			check(a.name().equals(b.name()) && a.path().equals(b.path()), at + " attachment identity/path");
			check(a.sequence() == b.sequence(), at + " Sequence index"); arrays(a.deform(), b.deform(), at + " Deform"); arrays(a.world(), b.world(), at + " world geometry");
		}
	}
	private static void arrays(float[] expected, float[] actual, String message) {
		check(expected.length == actual.length, message + " length");
		for (int i = 0; i < expected.length; i++) near(expected[i], actual[i], message + " [" + i + "]");
	}
	private static void near(float expected, float actual, String message) {
		check(Math.abs(expected - actual) < 0.002f, message + ": expected " + expected + ", actual " + actual);
	}
	private static void throwsArgument(Runnable action) {
		try { action.run(); throw new AssertionError("invalid composition accepted"); } catch (IllegalArgumentException expected) {}
	}
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private PermanentSkinCompositionTest() {}
}
