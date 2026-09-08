package com.openspine.ui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.EditorApp;
import com.openspine.io.AutoSaveStore;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.BoneCoordinateSpace;
import com.openspine.model.AnimationEditor;
import com.openspine.view.EditorViewport;
import com.openspine.view.EditorAppState;
import com.openspine.view.EditorTool;

/** Isolated framebuffer checks that never show or operate a user's application window. */
public final class SelectionMeshCapture {

	private static final class CaptureApp extends EditorApp {
		private final String mode;
		private final Path output;
		private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		private Throwable failure;
		private boolean exiting;
		private int frames;
		private TextTooltip inspectedTooltip;

		CaptureApp(String mode, Path output, Path home) {
			super(null, home);
			this.mode = mode;
			this.output = output;
		}

		@Override public void create() {
			try {
				super.create();
				invoke(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				SkeletonDocument doc = (SkeletonDocument)field(EditorApp.class, this, "doc");
				doc.setPlaying(false);
				doc.setAnimation("idle");
				doc.scrubTo(0.5f);
				doc.setSelectedBones(List.of(doc.skeleton.findBone("armL"), doc.skeleton.findBone("forearmL"),
					doc.skeleton.findBone("armR")));
				invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
				if (mode.equals("selection")) prepareSelection(doc);
				else if (mode.equals("track-skins")) prepareSkinTracks(doc);
				else if (mode.equals("coordinates-local")) prepareLocalCoordinates(doc);
				else if (mode.startsWith("coordinates-")) prepareCoordinates(doc);
				else if (mode.equals("skins")) prepareSkins(doc);
				else if (mode.startsWith("parent")) prepareParent(doc);
				else prepareMesh(doc);
			} catch (Throwable error) { stop(error); }
		}

		private void prepareParent(SkeletonDocument doc) {
			Stage stage = (Stage)field(EditorApp.class, this, "stage");
			PropertiesPanel properties = (PropertiesPanel)field(EditorApp.class, this, "propsPanel");
			SkeletonEditor editor = (SkeletonEditor)field(EditorApp.class, this, "editor");
			EditHistory history = (EditHistory)field(EditorApp.class, this, "history");
			doc.data.getAnimations().clear();
			editor.rebuildSkeleton();
			doc.skeleton.setupPoseBones();
			doc.skeleton.updateWorldTransform(Physics.pose);
			doc.setPoseMode(true);
			doc.setSelectedBone(doc.skeleton.findBone("armL"));
			invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
			float x = doc.getSelectedBone().getAppliedPose().getWorldX(), y = doc.getSelectedBone().getAppliedPose().getWorldY();
			for (boolean wasDirty : new boolean[] {false, true}) {
				doc.dirty = wasDirty;
				history.clear();
				Window dialog = openParent(properties, stage);
				((TextButton)dialog.findActor("applyParent")).fire(new ChangeEvent());
				require(dialog.getParent() == null && doc.data.findBone("armL").getParent() == doc.data.findBone("shoulderR"),
					"Parent dialog did not apply compensated reparenting");
				require(Math.abs(doc.getSelectedBone().getAppliedPose().getWorldX() - x) < 0.001f
					&& Math.abs(doc.getSelectedBone().getAppliedPose().getWorldY() - y) < 0.001f, "Parent dialog moved the setup pose");
				require(history.undo() && doc.dirty == wasDirty && !history.canUndo(), "Parent dialog undo lost its original savepoint");
				require(history.redo() && doc.data.findBone("armL").getParent() == doc.data.findBone("shoulderR"), "Parent dialog redo failed");
				require(history.undo(), "Parent dialog fixture rollback failed");
			}
			history.clear();
			if (mode.equals("parent-error")) {
				AnimationEditor animationEditor = new AnimationEditor(doc);
				var animation = animationEditor.addAnimation("protected-animation");
				animationEditor.ensureTimeline(animation, AnimationEditor.Prop.ROTATE, doc.data.findBone("armL").getIndex());
			}
			Window dialog = openParent(properties, stage);
			if (mode.equals("parent-error")) {
				((TextButton)dialog.findActor("applyParent")).fire(new ChangeEvent());
				require(dialog.getParent() != null && ((Label)dialog.findActor("parentError")).getText().toString().contains("动画"),
					"Unsupported animation must remain in dialog with a clear reason");
				require(!history.canUndo() && doc.data.findBone("armL").getParent() == doc.data.findBone("shoulderL"),
					"Rejected parent change left history or structural changes");
			}
		}

		private void prepareSkinTracks(SkeletonDocument doc) {
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			SkeletonEditor editor = (SkeletonEditor)field(EditorApp.class, this, "editor");
			EditHistory history = (EditHistory)field(EditorApp.class, this, "history");
			Stage stage = (Stage)field(EditorApp.class, this, "stage");
			Object canvas = field(TimelinePanel.class, timeline, "canvas");
			((com.badlogic.gdx.scenes.scene2d.ui.SplitPane)field(EditorApp.class, this, "workspace")).setSplitAmount(0.48f);
			for (Actor actor : stage.getActors()) if (actor instanceof com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup group) group.validate();
			Class<?> canvasType = canvas.getClass();
			SlotData slot = doc.data.findSlot("torso");
			MeshAttachment original = trackMesh(doc), combined = trackMesh(doc);
			String firstSkin = "Original 原始套装";
			String secondSkin = "Combined 组合套装 / Expedition Collection With A Long Descriptive Name";
			Skin originalSkin = new Skin(firstSkin), combination = new Skin(secondSkin), inherited = new Skin("Inherited 继承显示");
			originalSkin.setAttachment(slot.getIndex(), "torso", original);
			combination.setAttachment(slot.getIndex(), "torso", combined);
			inherited.setAttachment(slot.getIndex(), "torso", original.newLinkedMesh());
			doc.data.getSkins().addAll(originalSkin, combination, inherited);
			doc.data.getAnimations().clear();
			editor.rebuildSkeleton();
			AnimationEditor animationEditor = new AnimationEditor(doc);
			var animation = animationEditor.addAnimation("同名附件轨道");
			for (MeshAttachment mesh : List.of(original, combined)) {
				animationEditor.addDeformKeyframe(animation, slot.getIndex(), mesh, 0f, new float[8]);
				float[] offsets = new float[8]; offsets[0] = mesh == original ? 2f : 4f;
				animationEditor.addDeformKeyframe(animation, slot.getIndex(), mesh, 1f, offsets);
				animationEditor.addSequenceKeyframe(animation, slot.getIndex(), mesh, 0f,
					new AnimationEditor.SequenceKeyframe(com.esotericsoftware.spine.attachments.Sequence.SequenceMode.hold, 0, 0.2f));
				animationEditor.addSequenceKeyframe(animation, slot.getIndex(), mesh, 1f,
					new AnimationEditor.SequenceKeyframe(com.esotericsoftware.spine.attachments.Sequence.SequenceMode.hold, 1, 0.2f));
			}
			doc.animationState = doc.newAnimationState(); doc.setAnimation(animation.getName()); doc.scrubTo(0.5f);
			require(doc.setPreviewSkins(List.of(secondSkin)), "Unable to set combination preview skin");
			doc.setSelectedBone(doc.skeleton.findBone(slot.getBoneData().getName()));
			invoke(TimelinePanel.class, timeline, "setTimelineView", new Class<?>[] {boolean.class}, false);
			TextField search = (TextField)field(TimelinePanel.class, timeline, "trackSearchField");
			searchTracks(search, "Combined");
			com.badlogic.gdx.utils.Array<?> tracks = (com.badlogic.gdx.utils.Array<?>)field(canvasType, canvas, "tracks");
			require(tracks.size == 2, "Combined skin search must expose exactly its Deform and Sequence tracks: " + tracks.size);
			Object secondDeform = attachmentTrack(tracks, combined, "deformAttachment"), secondSequence = attachmentTrack(tracks, combined, "sequenceAttachment");
			Class<?> trackType = secondSequence.getClass();
			String full = (String)invoke(canvasType, canvas, "trackLabel", new Class<?>[] {trackType}, secondSequence);
			require(full.contains(secondSkin) && full.contains("torso") && full.contains("Sequence"), "Full label lost attachment ownership");
			invoke(canvasType, canvas, "selectTrackKeyframe", new Class<?>[] {trackType, int.class, float.class}, secondSequence, 0, 0f);
			timeline.persistViewState();
			SkeletonDocument.TimelineViewState saved = doc.timelineViewState();
			require(saved.selectedTrackKeys().length == 1 && saved.selectedTrackKeys()[0].endsWith("\u001f" + secondSkin), "Saved selection omits its skin");
			searchTracks(search, "套装");
			tracks = (com.badlogic.gdx.utils.Array<?>)field(canvasType, canvas, "tracks");
			require(tracks.size == 4, "Same-named attachments must expose four distinct owner tracks");
			Object firstDeform = attachmentTrack(tracks, original, "deformAttachment"), firstSequence = attachmentTrack(tracks, original, "sequenceAttachment");
			String firstKey = (String)invoke(canvasType, canvas, "trackStateKey", new Class<?>[] {trackType}, firstSequence);
			require(!firstKey.equals(saved.selectedTrackKeys()[0]), "Cross-skin state keys collide");
			invoke(canvasType, canvas, "clearSelection", new Class<?>[0]);
			invoke(canvasType, canvas, "restoreViewState", new Class<?>[] {SkeletonDocument.TimelineViewState.class}, saved);
			require(invoke(canvasType, canvas, "selectedTimeline", new Class<?>[0]) == animationEditor.findSequenceTimeline(animation, slot.getIndex(), combined), "Restoring a same-named selection chose the wrong skin");
			String legacy = firstKey.substring(0, firstKey.lastIndexOf('\u001f'));
			require(invoke(canvasType, canvas, "findTrackByStateKey", new Class<?>[] {String.class}, legacy) == null, "Ambiguous old state must not select an arbitrary skin");
			searchTracks(search, "Inherited");
			tracks = (com.badlogic.gdx.utils.Array<?>)field(canvasType, canvas, "tracks");
			require(tracks.size == 2 && attachmentTrack(tracks, original, "deformAttachment") != null, "Inherited skin search must resolve the true owner");
			searchTracks(search, "套装");
			invoke(canvasType, canvas, "selectTrackKeyframe", new Class<?>[] {trackType, int.class, float.class}, firstSequence, 0, 0f);
			require(doc.getCurrentSkinName().equals(secondSkin), "Timeline selection changed the user's preview skin");
			history.clear();
			((TextField)field(TimelinePanel.class, timeline, "keySequenceIndexField")).setText("1");
			((TextButton)field(TimelinePanel.class, timeline, "applyKeyBtn")).fire(new ChangeEvent());
			require(animationEditor.sequenceAt(animationEditor.findSequenceTimeline(animation, slot.getIndex(), original), 0).index() == 1
				&& animationEditor.sequenceAt(animationEditor.findSequenceTimeline(animation, slot.getIndex(), combined), 0).index() == 0,
				"Sequence Apply edited the wrong same-named attachment");
			require(history.undo() && !history.canUndo() && history.redo() && history.undo(), "Qualified Sequence edit is not one undo/redo command");
			invoke(canvasType, canvas, "selectTrackKeyframe", new Class<?>[] {trackType, int.class, float.class}, firstDeform, 0, 0f);
			((float[])field(TimelinePanel.class, timeline, "deformDraftOffsets"))[0] = 11f;
			((TextButton)field(TimelinePanel.class, timeline, "applyKeyBtn")).fire(new ChangeEvent());
			require(animationEditor.deformOffsetsAt(animationEditor.findDeformTimeline(animation, slot.getIndex(), original), 0)[0] == 11f
				&& animationEditor.deformOffsetsAt(animationEditor.findDeformTimeline(animation, slot.getIndex(), combined), 0)[0] == 0f,
				"Deform Apply edited the wrong same-named attachment");
			require(history.undo() && !history.canUndo() && history.redo() && history.undo(), "Qualified Deform edit is not one undo/redo command");
			require(doc.getCurrentSkinName().equals(secondSkin), "Editing inherited owners must preserve the preview skin");
			doc.scrubTo(0.5f); doc.setPlaying(false);
			invoke(canvasType, canvas, "selectTrackKeyframe", new Class<?>[] {trackType, int.class, float.class}, secondDeform, 0, 0f);
			invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
			Label selection = (Label)field(TimelinePanel.class, timeline, "keySelectionLabel");
			require(selection.getText().toString().contains(secondSkin), "Inspector label lost the selected skin");
			for (var listener : selection.getListeners()) if (listener instanceof TextTooltip tooltip) inspectedTooltip = tooltip;
			require(inspectedTooltip != null, "Ellipsized attachment selection needs a full-name tooltip");
			inspectedTooltip.setInstant(true);
			InputEvent enter = new InputEvent(); enter.setStage(stage); enter.setListenerActor(selection);
			inspectedTooltip.enter(enter, selection.getWidth() / 2f, selection.getHeight() / 2f, -1, null);
		}

		private Object attachmentTrack(com.badlogic.gdx.utils.Array<?> tracks, MeshAttachment mesh, String fieldName) {
			for (Object track : tracks) if (field(track.getClass(), track, fieldName) == mesh) return track;
			throw new AssertionError("Missing attachment track " + fieldName);
		}

		private void searchTracks(TextField search, String query) {
			search.setText(query); search.fire(new ChangeEvent());
		}

		private MeshAttachment trackMesh(SkeletonDocument doc) {
			Sequence sequence = new Sequence(2, true);
			sequence.getRegions()[0] = doc.atlas.findRegion("torso"); sequence.getRegions()[1] = doc.atlas.findRegion("torso");
			MeshAttachment mesh = new MeshAttachment("torso", sequence); mesh.setPath("torso");
			mesh.setVertices(new float[] {-30f, -30f, 30f, -30f, 30f, 90f, -30f, 90f});
			mesh.setWorldVerticesLength(8); mesh.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 0f, 0f, 0f, 0f});
			mesh.setTriangles(new short[] {0, 1, 2, 0, 2, 3}); mesh.setHullLength(8);
			mesh.setWidth(60f); mesh.setHeight(120f); mesh.updateSequence();
			return mesh;
		}

		private Window openParent(PropertiesPanel properties, Stage stage) {
			invoke(PropertiesPanel.class, properties, "showParentDialog", new Class<?>[0]);
			Window dialog = (Window)stage.getActors().peek();
			((TextField)dialog.findActor("parentBoneName")).setText("shoulderR");
			((TextButton)dialog.findActor("keepSetupWorld")).setChecked(true);
			return dialog;
		}

		private void prepareSkins(SkeletonDocument doc) throws ReflectiveOperationException {
			Stage stage = (Stage)field(EditorApp.class, this, "stage");
			UiKit kit = (UiKit)field(EditorApp.class, this, "kit");
			String base = doc.data.getDefaultSkin().getName();
			List<String> available = List.of("Jacket / Deep Teal", "Accessories / Field Kit",
				"Cuffs / Winter Variant", "Gloves / Canvas", "Boots / Weatherproof",
				"Backpack / Extended Expedition Collection With A Long Descriptive Name",
				"Helmet / Worklight", "Scarf / Red", "Belt / Utility", "Sleeves / Rolled", "Patches / Studio");
			for (String name : available) doc.data.getSkins().add(new Skin(name));
			List<String> before = List.of(base, available.get(0), available.get(1));
			require(doc.setPreviewSkins(before), "Unable to prepare preview skin layers");
			invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
			Constructor<SkinCombinationDialog> constructor = SkinCombinationDialog.class.getDeclaredConstructor(
				Stage.class, UiKit.class, SkeletonDocument.class, Runnable.class);
			constructor.setAccessible(true);
			SkinCombinationDialog canceled = constructor.newInstance(stage, kit, doc, (Runnable)() -> {});
			invoke(SkinCombinationDialog.class, canceled, "open", new Class<?>[0]);
			invoke(SkinCombinationDialog.class, canceled, "move", new Class<?>[] {String.class, int.class}, available.get(1), -1);
			require(doc.getPreviewSkinNames().equals(before), "Reordering a skin draft changed the model before Apply");
			invoke(SkinCombinationDialog.class, canceled, "close", new Class<?>[0]);
			require(doc.getPreviewSkinNames().equals(before), "Cancel published a reordered skin draft");
			SkinCombinationDialog applied = constructor.newInstance(stage, kit, doc, (Runnable)() -> {});
			invoke(SkinCombinationDialog.class, applied, "open", new Class<?>[0]);
			invoke(SkinCombinationDialog.class, applied, "move", new Class<?>[] {String.class, int.class}, available.get(1), -1);
			invoke(SkinCombinationDialog.class, applied, "submit", new Class<?>[0]);
			require(doc.getPreviewSkinNames().equals(List.of(base, available.get(1), available.get(0)))
				&& doc.getCurrentSkinName().equals(available.get(0)), "Apply lost skin order or authoring target");
			invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
			SkinCombinationDialog.show(stage, kit, doc, () -> {});
		}

		private void prepareCoordinates(SkeletonDocument doc) {
			PropertiesPanel properties = (PropertiesPanel)field(EditorApp.class, this, "propsPanel");
			EditorViewport viewport = (EditorViewport)field(EditorApp.class, this, "viewport");
			Stage stage = (Stage)field(EditorApp.class, this, "stage");
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			EditHistory history = (EditHistory)field(EditorApp.class, this, "history");
			properties.setOnCoordinateSpaceChanged(viewport::setCoordinateSpace);
			EditorAppState.tool = EditorTool.MOVE;
			Bone bone = doc.skeleton.findBone("armL");
			bone.getParent().getPose().setRotation(37f);
			bone.getParent().getData().getSetupPose().setRotation(37f);
			bone.getParent().getPose().setScale(1.7f, 0.8f);
			bone.getParent().getData().getSetupPose().setScale(1.7f, 0.8f);
			doc.skeleton.updateWorldTransform(Physics.pose);
			doc.setSelectedBone(bone);
			invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
			history.clear();
			((TextButton)field(TimelinePanel.class, timeline, "autoKeyBtn")).setChecked(false);
			properties.setCoordinateSpace(BoneCoordinateSpace.WORLD);
			require(viewport.coordinateSpace() == BoneCoordinateSpace.WORLD, "Coordinate callback did not update viewport");
			float x = bone.getAppliedPose().getWorldX(), y = bone.getAppliedPose().getWorldY();
			invoke(PropertiesPanel.class, properties, "beginEdit", new Class<?>[] {PropertiesPanel.Value.class}, PropertiesPanel.Value.X);
			invoke(PropertiesPanel.class, properties, "apply", new Class<?>[] {PropertiesPanel.Value.class, float.class}, PropertiesPanel.Value.X, x + 18f);
			invoke(PropertiesPanel.class, properties, "finishEdit", new Class<?>[0]);
			require(Math.abs(bone.getAppliedPose().getWorldX() - x - 18f) < 0.001f
				&& Math.abs(bone.getAppliedPose().getWorldY() - y) < 0.001f, "World inspector move drifted");
			require(history.undo() && !history.canUndo(), "World inspector move was not one history entry");
			Object row = field(PropertiesPanel.class, properties, "xRow");
			Slider slider = (Slider)field(row.getClass(), row, "slider");
			stage.setKeyboardFocus(slider);
			invoke(PropertiesPanel.class, properties, "beginEdit", new Class<?>[] {PropertiesPanel.Value.class}, PropertiesPanel.Value.X);
			slider.setValue(x + 28f);
			InputEvent escape = new InputEvent();
			escape.setType(InputEvent.Type.keyDown);
			escape.setKeyCode(com.badlogic.gdx.Input.Keys.ESCAPE);
			slider.fire(escape);
			require(Math.abs(bone.getAppliedPose().getWorldX() - x) < 0.001f && !history.canUndo(),
				"Escape did not roll back a slider gesture");
			require(stage.getKeyboardFocus() == null, "Slider did not release keyboard focus after cancel");
			invoke(PropertiesPanel.class, properties, "beginEdit", new Class<?>[] {PropertiesPanel.Value.class}, PropertiesPanel.Value.X);
			invoke(PropertiesPanel.class, properties, "apply", new Class<?>[] {PropertiesPanel.Value.class, float.class}, PropertiesPanel.Value.X, x + 9f);
			properties.setCoordinateSpace(BoneCoordinateSpace.PARENT);
			require(Math.abs(bone.getAppliedPose().getWorldX() - x) < 0.001f && !history.canUndo(),
				"Coordinate switch did not cancel a pending gesture");
			properties.setCoordinateSpace(mode.equals("coordinates-world") ? BoneCoordinateSpace.WORLD : BoneCoordinateSpace.PARENT);
			invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
		}

		private void prepareLocalCoordinates(SkeletonDocument doc) throws ReflectiveOperationException {
			PropertiesPanel properties = (PropertiesPanel)field(EditorApp.class, this, "propsPanel");
			EditorViewport viewport = (EditorViewport)field(EditorApp.class, this, "viewport");
			Stage stage = (Stage)field(EditorApp.class, this, "stage");
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			EditHistory history = (EditHistory)field(EditorApp.class, this, "history");
			SkeletonEditor editor = (SkeletonEditor)field(EditorApp.class, this, "editor");
			TextButton autoKey = (TextButton)field(TimelinePanel.class, timeline, "autoKeyBtn");
			doc.data.getAnimations().clear();
			var parentPose = doc.data.findBone("shoulderL").getSetupPose();
			parentPose.setRotation(37f); parentPose.setScale(-1.7f, 0.8f);
			var setup = doc.data.findBone("armL").getSetupPose();
			setup.setRotation(-28f); setup.setScale(1.2f, -0.6f); setup.setShearX(9f); setup.setShearY(-12f);
			editor.rebuildSkeleton();
			doc.skeleton.setupPoseBones(); doc.skeleton.updateWorldTransform(Physics.pose); doc.setPoseMode(true);
			Bone primary = doc.skeleton.findBone("armL"), child = doc.skeleton.findBone("forearmL"), other = doc.skeleton.findBone("armR");
			doc.setSelectedBones(List.of(primary, child, other));
			EditorAppState.tool = EditorTool.MOVE;
			properties.setCoordinateSpace(BoneCoordinateSpace.LOCAL);
			require(viewport.coordinateSpace() == BoneCoordinateSpace.LOCAL, "Local axes did not synchronize to viewport");
			invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
			Object xRow = field(PropertiesPanel.class, properties, "xRow");
			TextField input = (TextField)field(xRow.getClass(), xRow, "field");
			Slider slider = (Slider)field(xRow.getClass(), xRow, "slider");
			float startX = primary.getAppliedPose().getWorldX(), startY = primary.getAppliedPose().getWorldY();
			float axisX = primary.getAppliedPose().getA(), axisY = primary.getAppliedPose().getC();
			float childX = child.getPose().getX(), otherX = other.getAppliedPose().getWorldX();
			autoKey.setChecked(false); history.clear();
			stage.setKeyboardFocus(input); input.setText("12"); input.fire(key(com.badlogic.gdx.Input.Keys.ENTER));
			require(Math.abs(primary.getAppliedPose().getWorldX() - startX - 12f * axisX) < 0.001f
				&& Math.abs(primary.getAppliedPose().getWorldY() - startY - 12f * axisY) < 0.001f, "Enter did not apply exactly one local increment");
			require(Math.abs(other.getAppliedPose().getWorldX() - otherX - 12f * axisX) < 0.001f
				&& child.getPose().getX() == childX, "Local UI increment lost group/child semantics");
			require(input.getText().equals("0") && slider.getValue() == 0f, "Local controls must reset after a completed increment: field=" + input.getText() + ", slider=" + slider.getValue());
			require(history.undo() && !history.canUndo() && history.redo() && history.undo(), "Local Enter increment must be one reversible command");
			stage.setKeyboardFocus(slider);
			invoke(PropertiesPanel.class, properties, "beginEdit", new Class<?>[] {PropertiesPanel.Value.class}, PropertiesPanel.Value.X);
			slider.setValue(5f); properties.refresh();
			require(slider.getValue() == 5f && input.getText().equals("5"), "Refresh erased a live local slider delta");
			slider.setValue(8f); slider.fire(key(com.badlogic.gdx.Input.Keys.ESCAPE));
			require(Math.abs(primary.getAppliedPose().getWorldX() - startX) < 0.001f && !history.canUndo()
				&& input.getText().equals("0") && stage.getKeyboardFocus() == null, "Local slider Escape did not fully cancel");
			stage.setKeyboardFocus(input); input.setText("9"); input.fire(key(com.badlogic.gdx.Input.Keys.ESCAPE));
			require(Math.abs(primary.getAppliedPose().getWorldX() - startX) < 0.001f && !history.canUndo(), "Text Escape applied a local increment");
			stage.setKeyboardFocus(input); input.setText("4"); stage.setKeyboardFocus(null);
			require(Math.abs(primary.getAppliedPose().getWorldX() - startX - 4f * axisX) < 0.001f
				&& input.getText().equals("0") && history.undo(), "Local focus loss must apply once then reset");
			invoke(PropertiesPanel.class, properties, "beginEdit", new Class<?>[] {PropertiesPanel.Value.class}, PropertiesPanel.Value.X);
			invoke(PropertiesPanel.class, properties, "apply", new Class<?>[] {PropertiesPanel.Value.class, float.class}, PropertiesPanel.Value.X, 7f);
			properties.setCoordinateSpace(BoneCoordinateSpace.WORLD);
			require(Math.abs(primary.getAppliedPose().getWorldX() - startX) < 0.001f && !history.canUndo(), "Changing coordinate mode did not cancel local preview");
			properties.setCoordinateSpace(BoneCoordinateSpace.LOCAL);
			Class<?> axisType = Class.forName("com.openspine.view.EditorViewport$TransformAxis");
			Object xAxis = java.util.Arrays.stream(axisType.getEnumConstants()).filter(value -> value.toString().equals("X")).findFirst().orElseThrow();
			var camera = (com.badlogic.gdx.graphics.OrthographicCamera)field(EditorViewport.class, viewport, "camera");
			float axisLength = (float)Math.hypot(axisX, axisY), dx = 20f * axisX / axisLength, dy = 20f * axisY / axisLength;
			for (boolean cancel : new boolean[] {true, false}) {
				stage.setKeyboardFocus(viewport);
				invoke(EditorViewport.class, viewport, "beginBoneDrag", new Class<?>[] {Bone.class, float.class, float.class, axisType, boolean.class}, primary, 100f, 100f, xAxis, true);
				invoke(EditorViewport.class, viewport, "applyToolDrag", new Class<?>[] {float.class, float.class}, 100f + dx / camera.zoom, 100f + dy / camera.zoom);
				require(Math.abs(primary.getAppliedPose().getWorldX() - startX - dx) < 0.001f
					&& Math.abs(primary.getAppliedPose().getWorldY() - startY - dy) < 0.001f, "Viewport local axis differs from own bone matrix");
				if (cancel) {
					viewport.fire(key(com.badlogic.gdx.Input.Keys.ESCAPE));
					require(Math.abs(primary.getAppliedPose().getWorldX() - startX) < 0.001f && !history.canUndo(), "Viewport local Escape must roll back");
				} else {
					invoke(EditorViewport.class, viewport, "finishBoneDrag", new Class<?>[0]);
					require(history.undo() && !history.canUndo() && history.redo() && history.undo(), "Viewport local drag must have one undo/redo entry");
				}
			}
			AnimationEditor animationEditor = new AnimationEditor(doc);
			var animation = animationEditor.addAnimation("local-axis-move");
			doc.animationState = doc.newAnimationState(); doc.setAnimation(animation.getName());
			for (Bone bone : List.of(primary, other)) doc.setAxisSeparationPreferred(animation.getName(), bone.getData().getName(), AnimationEditor.AxisGroup.TRANSLATE.name(), true);
			autoKey.setChecked(true); history.clear();
			stage.setKeyboardFocus(input); input.setText("3"); input.fire(key(com.badlogic.gdx.Input.Keys.ENTER));
			for (Bone bone : List.of(primary, other)) {
				require(animationEditor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_X, bone.getData().getIndex()) != null
					&& animationEditor.findTimeline(animation, AnimationEditor.Prop.TRANSLATE_Y, bone.getData().getIndex()) != null, "Local auto-key lost one parent translation axis");
			}
			doc.scrubTo(0f);
			require(Math.abs(primary.getAppliedPose().getWorldX() - startX - 3f * axisX) < 0.001f
				&& Math.abs(primary.getAppliedPose().getWorldY() - startY - 3f * axisY) < 0.001f, "Local auto-key playback lost the movement");
			require(history.undo() && !history.canUndo() && history.redo(), "Local auto-key must be one undoable edit");
			stage.setKeyboardFocus(null);
			invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
		}

		private static InputEvent key(int keycode) {
			InputEvent event = new InputEvent(); event.setType(InputEvent.Type.keyDown); event.setKeyCode(keycode); return event;
		}

		private void prepareSelection(SkeletonDocument doc) {
			PropertiesPanel properties = (PropertiesPanel)field(EditorApp.class, this, "propsPanel");
			TimelinePanel timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
			EditHistory history = (EditHistory)field(EditorApp.class, this, "history");
			history.clear();
			((TextButton)field(TimelinePanel.class, timeline, "autoKeyBtn")).setChecked(true);
			Bone primary = doc.getSelectedBone();
			float before = primary.getPose().getRotation();
			invoke(PropertiesPanel.class, properties, "beginEdit", new Class<?>[] {PropertiesPanel.Value.class}, PropertiesPanel.Value.ROTATION);
			invoke(PropertiesPanel.class, properties, "apply", new Class<?>[] {PropertiesPanel.Value.class, float.class},
				PropertiesPanel.Value.ROTATION, before + 25f);
			invoke(PropertiesPanel.class, properties, "finishEdit", new Class<?>[0]);
			require(doc.getSelectedBones().size() == 3, "Selection lost after property edit");
			require(Math.abs(doc.skeleton.findBone("armL").getPose().getRotation() - 25f) < 0.01f, "Left arm was not transformed");
			require(Math.abs(doc.skeleton.findBone("armR").getPose().getRotation() - 25f) < 0.01f, "Right arm was not transformed");
			require(Math.abs(doc.skeleton.findBone("forearmL").getPose().getRotation()) < 0.01f, "Child received duplicate transform");
			require(history.undo() && !history.canUndo(), "Group edit must have exactly one undo entry");
			require(doc.getSelectedBones().size() == 3 && history.redo(), "Undo/redo lost selection");
			timeline.keySelectedPose();
			require(doc.getSelectedBones().size() == 3, "Manual keying lost selection");
			invoke(TimelinePanel.class, timeline, "setTimelineView", new Class<?>[] {boolean.class}, false);
			invoke(EditorApp.class, this, "refreshSelection", new Class<?>[0]);
		}

		private void prepareMesh(SkeletonDocument doc) throws ReflectiveOperationException {
			Stage stage = (Stage)field(EditorApp.class, this, "stage");
			UiKit kit = (UiKit)field(EditorApp.class, this, "kit");
			SkeletonEditor editor = (SkeletonEditor)field(EditorApp.class, this, "editor");
			EditHistory history = (EditHistory)field(EditorApp.class, this, "history");
			SlotData slot = doc.data.findSlot("torso");
			Sequence sequence = new Sequence(1, false);
			sequence.getRegions()[0] = doc.atlas.findRegion("torso");
			MeshAttachment mesh = new MeshAttachment("torso", sequence);
			mesh.setPath("torso");
			mesh.setVertices(new float[] {-30f, -30f, 30f, -30f, 30f, 90f, -30f, 90f});
			mesh.setWorldVerticesLength(8);
			mesh.setRegionUVs(new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f});
			mesh.setTriangles(new short[] {0, 1, 2, 0, 2, 3});
			mesh.setHullLength(8);
			mesh.setWidth(60f);
			mesh.setHeight(120f);
			mesh.updateSequence();
			doc.data.getDefaultSkin().setAttachment(slot.getIndex(), "torso", mesh);
			doc.skeleton.findSlot("torso").getPose().setAttachment(mesh);
			Constructor<MeshGeometryDialog> constructor = MeshGeometryDialog.class.getDeclaredConstructor(
				Stage.class, UiKit.class, SkeletonDocument.class, SkeletonEditor.class, MeshAttachment.class, SlotData.class,
				EditHistory.class, Runnable.class, BiConsumer.class);
			constructor.setAccessible(true);
			MeshGeometryDialog dialog = constructor.newInstance(stage, kit, doc, editor, mesh, slot, history,
				(Runnable)() -> {}, (BiConsumer<String, Boolean>)(message, error) -> {});
			invoke(MeshGeometryDialog.class, dialog, "open", new Class<?>[0]);
			switch (mode) {
			case "mesh" -> {}
			case "weights" -> invoke(MeshGeometryDialog.class, dialog, "showAutoWeights", new Class<?>[0]);
			case "trace" -> invoke(MeshGeometryDialog.class, dialog, "showTrace", new Class<?>[0]);
			case "subdivide" -> invoke(MeshGeometryDialog.class, dialog, "showSubdivide", new Class<?>[0]);
			default -> throw new IllegalArgumentException("Unknown capture mode: " + mode);
			}
		}

		@Override public void render() {
			if (exiting) return;
			try {
				super.render();
				if (System.nanoTime() > deadline) throw new AssertionError("Screenshot timed out");
				if (++frames < 40) return;
				capture();
				Stage stage = (Stage)field(EditorApp.class, this, "stage");
				StringBuilder dimensions = new StringBuilder("stage=" + stage.getWidth() + "x" + stage.getHeight() + "\n");
				for (Actor actor : stage.getActors()) if (actor instanceof Window window) {
					dimensions.append(window.getTitleLabel().getText()).append(" ").append(window.getX()).append(",")
						.append(window.getY()).append(" ").append(window.getWidth()).append("x").append(window.getHeight()).append("\n");
						require(window.getX() >= 0f && window.getY() >= 0f && window.getRight() <= stage.getWidth() + 1f
							&& window.getTop() <= stage.getHeight() + 1f, "Modal exceeds stage: " + dimensions);
					if (mode.equals("skins")) verifyButtonLabels(window);
				}
				if (mode.startsWith("coordinates-")) verifyCoordinateLayout(stage, dimensions);
				if (mode.equals("track-skins")) {
					require(inspectedTooltip.getContainer().getStage() == stage, "Attachment ownership tooltip did not appear");
					Rectangle bounds = stageBounds(inspectedTooltip.getContainer());
					require(bounds.x >= 0f && bounds.y >= 0f && bounds.x + bounds.width <= stage.getWidth() + 1f
						&& bounds.y + bounds.height <= stage.getHeight() + 1f, "Ownership tooltip exceeds stage");
					dimensions.append("ownershipTooltip=").append(bounds).append('\n').append(inspectedTooltip.getActor().getText()).append('\n');
				}
				Files.writeString(output.resolveSibling(output.getFileName() + ".txt"), dimensions);
				if (mode.equals("track-skins")) {
					inspectedTooltip.getContainer().setVisible(false);
					super.render();
					capture(output.resolveSibling(output.getFileName().toString().replace(".png", "-rows.png")));
				}
				System.out.println("OK: " + mode + " " + dimensions);
				stop(null);
			} catch (Throwable error) { stop(error); }
		}

		private void verifyCoordinateLayout(Stage stage, StringBuilder dimensions) {
			PropertiesPanel properties = (PropertiesPanel)field(EditorApp.class, this, "propsPanel");
			Rectangle parent = stageBounds((Actor)field(PropertiesPanel.class, properties, "parentSpaceBtn"));
			Rectangle world = stageBounds((Actor)field(PropertiesPanel.class, properties, "worldSpaceBtn"));
			Rectangle local = stageBounds((Actor)field(PropertiesPanel.class, properties, "localSpaceBtn"));
			require(!parent.overlaps(world) && !world.overlaps(local) && !parent.overlaps(local), "Coordinate mode controls overlap");
			for (Rectangle bounds : List.of(parent, world, local)) require(bounds.x >= 0f && bounds.x + bounds.width <= stage.getWidth() + 1f, "Coordinate mode exceeds stage");
			verifyButtonLabels(properties);
			for (String name : List.of("xRow", "yRow", "rotRow", "sxRow", "syRow", "shearXRow", "shearYRow")) {
				Object row = field(PropertiesPanel.class, properties, name);
				Rectangle label = stageBounds((Actor)field(row.getClass(), row, "label"));
				Rectangle input = stageBounds((Actor)field(row.getClass(), row, "field"));
				Rectangle slider = stageBounds((Actor)field(row.getClass(), row, "slider"));
				require(!label.overlaps(input) && !input.overlaps(slider) && !label.overlaps(slider),
					"Coordinate property row overlaps: " + name);
				for (Rectangle bounds : List.of(label, input, slider))
					require(bounds.x >= 0f && bounds.y >= 0f && bounds.x + bounds.width <= stage.getWidth() + 1f
						&& bounds.y + bounds.height <= stage.getHeight() + 1f, "Coordinate property exceeds stage: " + name);
				dimensions.append(name).append(" label=").append(label).append(" input=").append(input)
					.append(" slider=").append(slider).append('\n');
			}
		}

		private Rectangle stageBounds(Actor actor) {
			Vector2 position = actor.localToStageCoordinates(new Vector2());
			return new Rectangle(position.x, position.y, actor.getWidth(), actor.getHeight());
		}

		private void verifyButtonLabels(Group group) {
			for (Actor actor : group.getChildren()) {
				if (actor instanceof TextButton button) {
					Rectangle bounds = stageBounds(button), label = stageBounds(button.getLabel());
					require(label.x >= bounds.x - 1f && label.x + label.width <= bounds.x + bounds.width + 1f
						&& button.getLabel().getGlyphLayout().width <= label.width + 1f,
						"Skin label exceeds its button: " + button.getText());
				}
				if (actor instanceof Group child) verifyButtonLabels(child);
			}
		}

		private void capture() throws Exception {
			capture(output);
		}

		private void capture(Path target) throws Exception {
			Files.createDirectories(target.getParent());
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			int distinct = 0;
			for (int i = 4; i < pixels.length; i += 4)
				if (pixels[i] != pixels[0] || pixels[i + 1] != pixels[1] || pixels[i + 2] != pixels[2]) distinct++;
			require(distinct > width * height / 10, "Framebuffer appears blank");
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try {
				BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(target.toFile()), pixmap);
			} finally { pixmap.dispose(); }
		}

		private void stop(Throwable error) {
			failure = error;
			exiting = true;
			Gdx.app.exit();
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 4) throw new IllegalArgumentException("Expected mode, width, height, output PNG");
		Path home = Files.createTempDirectory("openspine-selection-capture-").toRealPath();
		String previousHome = System.getProperty("user.home");
		String previousAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		CaptureApp app = new CaptureApp(args[0], Path.of(args[3]).toAbsolutePath(), home);
		try {
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setTitle("OpenSpine Hidden Selection and Mesh Verification");
			config.setInitialVisible(false);
			config.setWindowedMode(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
			config.setResizable(false);
			config.setForegroundFPS(60);
			config.setIdleFPS(60);
			config.disableAudio(true);
			config.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
			new Lwjgl3Application(app, config);
		} finally {
			System.setProperty("user.home", previousHome);
			if (previousAutosave == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY);
			else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, previousAutosave);
			try (var paths = Files.walk(home)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		if (app.failure != null) throw new AssertionError("Hidden editor capture failed", app.failure);
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static Object field(Class<?> type, Object target, String name) {
		try {
			Field field = type.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException error) { throw new IllegalStateException("Unable to read " + name, error); }
	}

	private static Object invoke(Class<?> type, Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
		try {
			Method method = type.getDeclaredMethod(name, parameterTypes);
			method.setAccessible(true);
			return method.invoke(target, arguments);
		} catch (ReflectiveOperationException error) { throw new IllegalStateException("Unable to invoke " + name, error); }
	}

	private SelectionMeshCapture() {}
}
