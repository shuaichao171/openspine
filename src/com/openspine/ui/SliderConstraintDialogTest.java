package com.openspine.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.JsonReader;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Constraint;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.openspine.io.HtmlExporter;
import com.openspine.io.ProjectStateStore;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonProject;

/** Headless checks for semantic Slider constraint draft comparison. */
public final class SliderConstraintDialogTest {
	public static void main(String[] args) throws Exception {
		selectsSafeAnimationDefaults();
		preservesSecondsWhenFrameDisplayRounds();
		SliderConstraintDialog.Draft property = SliderConstraintDialog.sliderDraft(
			" slider ", " driven ", " 骨骼属性驱动 ", " child ", " X ", "99", ".6",
			"-5", "2", ".25", "120", true, true, true, true, true);
		assertEquals(property, SliderConstraintDialog.sliderDraft(
			"slider", "driven", SliderConstraintDialog.MODE_PROPERTY, "child", "X", "-500", "0.60",
			"-5.0", "2e0", "0.250", "1.2e2", true, true, true, true, true),
			"property mode ignores setup time and normalizes equivalent numeric notation");
		assertNotEquals(property, SliderConstraintDialog.sliderDraft(
			"slider", "driven", SliderConstraintDialog.MODE_PROPERTY, "child", "Y", "99", ".6",
			"-5", "2", ".25", "120", true, true, true, true, true),
			"property selection changes are detected");

		SliderConstraintDialog.Draft time = SliderConstraintDialog.sliderDraft(
			"time", "driven", SliderConstraintDialog.MODE_TIME, "child", "X", ".35", ".8",
			"-5", "2", ".25", "120", false, false, true, false, false);
		assertEquals(time, SliderConstraintDialog.sliderDraft(
			"time", "driven", SliderConstraintDialog.MODE_TIME, "root", "剪切 Y", "0.350", ".80",
			"20", "30", "40", "50", false, false, false, false, false),
			"time mode ignores disabled property mapping controls");
		assertNotEquals(time, SliderConstraintDialog.sliderDraft(
			"time", "driven", SliderConstraintDialog.MODE_TIME, "root", "剪切 Y", ".4", ".8",
			"20", "30", "40", "50", false, false, false, false, false),
			"setup time changes are detected in time mode");
		assertNotEquals(time, property, "drive mode changes are detected");

		for (SkeletonEditor.TransformProperty transformProperty : SkeletonEditor.TransformProperty.values()) {
			String label = SliderConstraintDialog.propertyLabel(transformProperty);
			assertEquals(transformProperty, SliderConstraintDialog.transformProperty(label),
				"Slider property labels round-trip for " + transformProperty);
		}

		SliderConstraintDialog.RangeMapping range = SliderConstraintDialog.rangeMapping(-5f, 2f, 0.25f, 120f, 30f);
		assertNear(-5f, range.sourceMin(), "range source minimum");
		assertNear(120f, range.sourceMax(), "range source maximum");
		assertNear(60f, range.frameMin(), "runtime seconds become project frames");
		assertNear(997.5f, range.frameMax(), "runtime scale becomes the mapped end frame");
		SliderConstraintDialog.RuntimeMapping runtime = SliderConstraintDialog.runtimeMapping(range, 30f);
		assertNear(-5f, runtime.sourceOffset(), "source minimum round-trip");
		assertNear(2f, runtime.targetOffset(), "target time round-trip");
		assertNear(0.25f, runtime.scale(), "mapping scale round-trip");
		assertNear(120f, runtime.max(), "source maximum round-trip");
		assertThrows(() -> SliderConstraintDialog.runtimeMapping(
			new SliderConstraintDialog.RangeMapping(4f, 4f, 0f, 10f), 30f),
			"a zero source range cannot be converted");
		previewsCompleteRuntimeSetupWithoutMutatingProjectData();
		keepsDeterministicPhysicsSeekAliveDuringRuntimePreview();
		preservesAuthoringSkinsDuringRuntimePreview();
		System.out.println("ALL SLIDER CONSTRAINT DIALOG TESTS PASSED");
	}

	private static void preservesSecondsWhenFrameDisplayRounds() {
		float seconds = 0.1234567f;
		SkeletonEditor.SliderConstraintSetup loaded = new SkeletonEditor.SliderConstraintSetup("time",
			null, false, false, false, null, null, 0, 0, 1, 0, false, seconds, 1);
		assertTrue((seconds * 24f) / 24f != seconds, "fixture loses a bit in a normal frame conversion");
		assertEquals(Float.floatToIntBits(seconds), Float.floatToIntBits(SliderConstraintDialog.setupTime(
			UiNumbers.format(seconds * 24f), 24f, loaded)), "unchanged frame display preserves runtime seconds exactly");
		assertNear(0.5f, SliderConstraintDialog.setupTime("12", 24f, loaded), "changed frame display edits setup seconds");
	}

	private static void preservesAuthoringSkinsDuringRuntimePreview() throws Exception {
		for (List<String> layers : List.of(List.of("base"), List.of("base", "overlay"))) {
			SkeletonDocument source = new SkeletonDocument();
			SkeletonData data = source.data = new SkeletonData();
			data.setName("preview");
			data.setVersion("4.3.0");
			BoneData root = new BoneData(0, "root", null);
			root.getSetupPose().setInherit(BoneData.Inherit.normal);
			data.getBones().add(root);
			SlotData slot = new SlotData(0, "body", root);
			slot.setBlendMode(com.esotericsoftware.spine.BlendMode.normal);
			slot.setAttachmentName("base-only");
			data.getSlots().add(slot);
			Skin defaults = new Skin("default"), base = new Skin("base"), overlay = new Skin("overlay");
			BoundingBoxAttachment attachment = new BoundingBoxAttachment("base-only");
			attachment.setVertices(new float[] {0, 0, 10, 0, 0, 10});
			attachment.setWorldVerticesLength(6);
			base.setAttachment(0, "base-only", attachment);
			data.getSkins().addAll(defaults, base, overlay);
			data.setDefaultSkin(defaults);
			Animation animation = new Animation("driven");
			animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
			animation.setDuration(2f);
			data.getAnimations().add(animation);
			source.skeleton = new Skeleton(data);
			source.animationState = source.newAnimationState();
			try (SkeletonProject project = new SkeletonProject(new SkeletonDocument())) {
				project.replaceAll(List.of(new SkeletonProject.LoadedSkeleton("preview", source)));
				SkeletonDocument doc = project.documents().get(0);
				if (layers.size() == 1) doc.skeleton.setSkin(base); // Existing loaders can select a real skin directly.
				else assertTrue(doc.setPreviewSkins(layers), "composition can be selected before preview");
				Skin editing = doc.getEditingSkin();
				Skin original = doc.skeleton.getSkin();
				SkeletonEditor.SliderConstraintSetup setup = new SkeletonEditor.SliderConstraintSetup(
					"slider", animation, true, false, false, null, null,
					0f, 0f, 1f, 0f, false, 0.2f, 0.5f);
				SliderConstraintDialog.RuntimePreview preview = SliderConstraintDialog.installRuntimePreview(
					doc.skeleton, null, setup, editing, true);
				try {
					assertTrue(doc.skeleton.getAttachment(0, "base-only") == attachment,
						"temporary preview retains attachments from every selected layer");
					assertEquals(layers, doc.getPreviewSkinNames(), "temporary preview retains real layer order");
					assertTrue(doc.getEditingSkin() == editing, "temporary preview does not change the authoring target");
					Path runtime = Files.createTempFile("openspine-slider-preview-", ".json");
					try {
						com.openspine.io.SkeletonExporter.writeAtomic(doc, runtime);
						ProjectStateStore.write(runtime, project, false);
						var state = new JsonReader().parse(Files.readString(ProjectStateStore.sidecarPath(runtime)));
						assertEquals(layers, List.of(state.get("skeletons").get(0).get("previewSkins").asStringArray()),
							"sidecar written during preview contains real skin names");
					} finally {
						Files.deleteIfExists(ProjectStateStore.sidecarPath(runtime));
						Files.deleteIfExists(runtime);
					}
					Path output = Files.createTempFile("openspine-slider-preview-", ".html");
					try {
						HtmlExporter.write(doc, output);
						String html = Files.readString(output);
						String marker = "<script id=\"openspine-data\" type=\"application/json\">";
						int start = html.indexOf(marker) + marker.length();
						var config = new JsonReader().parse(html.substring(start, html.indexOf("</script>", start))).get("config");
						List<String> exported = config.get("skin").isArray() ? List.of(config.get("skin").asStringArray())
							: List.of(config.getString("skin"));
						assertEquals(layers, exported, "HTML export during preview contains the real ordered skin layers");
					} finally { Files.deleteIfExists(output); }
				} finally { preview.restore(); }
				assertTrue(doc.skeleton.getSkin() == original, "closing preview restores the exact original skin or composition");
				SliderData created = new SkeletonEditor(doc).addSliderConstraint(setup, doc.getEditingSkin(), true);
				assertTrue(created != null && editing.getConstraints().contains(created, true)
					&& !defaults.getConstraints().contains(created, true), "submission activates the real editing skin");
				assertEquals(layers, doc.getPreviewSkinNames(), "submission rebuild retains the complete combination");
			}
		}
	}

	private static void selectsSafeAnimationDefaults() {
		Array<String> names = new Array<>();
		assertTrue(SliderConstraintDialog.preferredAnimationName(names, "") == null,
			"an empty animation list has no default selection");
		assertTrue(SliderConstraintDialog.preferredAnimationName(null, "walk") == null,
			"a missing animation list has no default selection");
		names.add("idle");
		names.add("walk");
		assertEquals("walk", SliderConstraintDialog.preferredAnimationName(names, "walk"),
			"the active animation remains selected when it is available");
		assertEquals("idle", SliderConstraintDialog.preferredAnimationName(names, "missing"),
			"a stale animation selection falls back to the first available animation");
		assertEquals("idle", SliderConstraintDialog.preferredAnimationName(names, null),
			"a missing current animation falls back to the first available animation");
	}

	private static void previewsCompleteRuntimeSetupWithoutMutatingProjectData() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root);
		Animation driven = new Animation("driven");
		driven.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		driven.setDuration(2f);
		data.getAnimations().add(driven);

		SkeletonEditor.SliderConstraintSetup originalSetup = new SkeletonEditor.SliderConstraintSetup(
			"slider", driven, true, false, false, null, null,
			0f, 0f, 1f, 0f, false, 0.2f, 0.5f);
		SliderData existing = SkeletonEditor.sliderConstraintData(originalSetup);
		data.getConstraints().add(existing);
		Skin skin = new Skin("default");
		skin.getConstraints().add(existing);
		data.getSkins().add(skin);
		data.setDefaultSkin(skin);
		Skeleton skeleton = new Skeleton(data);
		skeleton.setSkin(skin);
		skeleton.updateCache();
		Constraint<?, ?, ?> originalRuntime = skeleton.getConstraints().first();

		SkeletonEditor.SliderConstraintSetup propertySetup = new SkeletonEditor.SliderConstraintSetup(
			"slider-preview", driven, true, true, false, root, SkeletonEditor.TransformProperty.X,
			-5f, 2f, 0.25f, 120f, true, 99f, 0.6f);
		SliderConstraintDialog.RuntimePreview preview = SliderConstraintDialog.installRuntimePreview(
			skeleton, existing, propertySetup, skin, true);
		SliderData previewData = preview.preview.getData();
		assertTrue(skeleton.getConstraints().size == 1 && skeleton.getConstraints().first() == preview.preview,
			"editing preview replaces the runtime constraint at the same unified index");
		assertTrue(data.getConstraints().size == 1 && data.getConstraints().first() == existing,
			"runtime preview does not replace project constraint data");
		assertTrue(skin.getConstraints().size == 1 && skin.getConstraints().first() == existing,
			"skin-required preview does not mutate project skin activation data");
		assertTrue(skeleton.getSkin() == preview.previewSkin && skeleton.getSkin() != skin
			&& preview.preview.isActive(), "skin-required preview uses an active runtime-only skin");
		assertTrue(previewData.getAnimation() == driven && previewData.getBone() == root
			&& previewData.getProperty() != null && previewData.getAdditive() && previewData.getLocal(),
			"runtime preview includes animation, bone, property, additive, and local setup");
		assertNear(-5f, previewData.getProperty().offset, "runtime preview source offset");
		assertNear(2f, previewData.getOffset(), "runtime preview target offset");
		assertNear(0.25f, previewData.getScale(), "runtime preview mapping scale");
		assertNear(120f, previewData.getMax(), "runtime preview source maximum");
		assertNear(0.6f, previewData.getSetupPose().getMix(), "runtime preview setup mix");
		preview.restore();
		assertTrue(skeleton.getConstraints().size == 1 && skeleton.getConstraints().first() == originalRuntime,
			"closing the preview restores the exact original runtime constraint");
		assertTrue(skeleton.getSkin() == skin && skin.getConstraints().first() == existing,
			"closing the preview restores the exact original runtime skin");

		SkeletonEditor.SliderConstraintSetup timeSetup = new SkeletonEditor.SliderConstraintSetup(
			"new-time-slider", driven, false, true, true, null, null,
			10f, 20f, 30f, 40f, true, 0.35f, 0.8f);
		preview = SliderConstraintDialog.installRuntimePreview(skeleton, null, timeSetup, skin, false);
		previewData = preview.preview.getData();
		assertTrue(skeleton.getConstraints().size == 2 && data.getConstraints().size == 1,
			"new constraint preview exists only in the runtime list");
		assertTrue(previewData.getLoop() && previewData.getAdditive() && previewData.getBone() == null
			&& previewData.getProperty() == null && !previewData.getLocal(),
			"time-driven preview includes loop and clears disabled property mapping state");
		assertNear(0.35f, previewData.getSetupPose().getTime(), "time-driven runtime preview setup time");
		assertNear(0.8f, previewData.getSetupPose().getMix(), "time-driven runtime preview setup mix");
		preview.restore();
		assertTrue(skeleton.getConstraints().size == 1 && skeleton.getConstraints().first() == originalRuntime,
			"closing a new constraint preview removes the temporary runtime constraint");
	}

	private static void keepsDeterministicPhysicsSeekAliveDuringRuntimePreview() {
		SkeletonData data = new SkeletonData();
		BoneData root = new BoneData(0, "root", null);
		root.getSetupPose().setInherit(BoneData.Inherit.normal);
		data.getBones().add(root);
		PhysicsConstraintData physics = new PhysicsConstraintData("spring");
		physics.setBone(root);
		physics.setStep(0.0001f);
		data.getConstraints().add(physics);

		Animation base = new Animation("base");
		base.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		base.setDuration(2f);
		data.getAnimations().add(base);
		Animation driven = new Animation("driven");
		driven.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		driven.setDuration(1f);
		data.getAnimations().add(driven);

		SkeletonEditor.SliderConstraintSetup setup = new SkeletonEditor.SliderConstraintSetup(
			"slider", driven, false, false, false, null, null,
			0f, 0f, 1f, 0f, false, 0.25f, 1f);
		SliderData existing = SkeletonEditor.sliderConstraintData(setup);
		data.getConstraints().add(existing);

		SkeletonDocument doc = new SkeletonDocument();
		doc.data = data;
		doc.skeleton = new Skeleton(data);
		doc.animationState = new AnimationState(new AnimationStateData(data));
		assertTrue(doc.setAnimation(base.getName()), "runtime preview test animation can be selected");
		assertTrue(doc.setPhysicsDeterministic(true), "runtime preview test enables deterministic Physics");
		doc.seek(2f);
		doc.completePendingPhysicsSeek();
		assertTrue(!doc.isPhysicsSeekPending(), "the baseline deterministic pose is complete");

		SkeletonEditor.SliderConstraintSetup previewSetup = new SkeletonEditor.SliderConstraintSetup(
			"slider", driven, false, false, false, null, null,
			0f, 0f, 1f, 0f, false, 0.5f, 0.6f);
		SliderConstraintDialog.RuntimePreview preview = SliderConstraintDialog.installRuntimePreview(
			doc.skeleton, existing, previewSetup, null, false);
		SliderConstraintDialog.refreshRuntimePose(doc);
		assertTrue(doc.isPhysicsSeekPending(),
			"runtime preview keeps the newly started deterministic replay pending after its first bounded tick");
		int ticks = 0;
		while (doc.isPhysicsSeekPending() && ticks++ < 100) doc.applyAnimation(0f);
		assertTrue(!doc.isPhysicsSeekPending(),
			"later editor ticks complete the deterministic replay started by runtime preview");
		preview.restore();
	}

	private static void assertNear(float expected, float actual, String message) {
		if (Math.abs(expected - actual) > 0.0001f)
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertNotEquals(Object expected, Object actual, String message) {
		if (expected.equals(actual)) throw new AssertionError(message + ": both were " + actual);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private SliderConstraintDialogTest() {}
}
