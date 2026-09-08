package com.openspine.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.EditorApp;
import com.openspine.io.AutoSaveStore;
import com.openspine.model.EditHistory;
import com.openspine.model.AnimationEditor;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Final-bin UI integration checks; the GL window stays hidden and uses isolated preferences/autosave. */
public final class PermanentSkinCapture {
	private static final String BASE = "基础服装 / Deep Teal Jacket";
	private static final String OVERLAY = "装备 / Field Equipment";
	private static final String LONG_NAME = "永久皮肤组合 / Extended Expedition Collection With Independent Deform And Sequence Animation";
	private static final String MOTION = "皮肤组合动画验证 / Preserve inherited Deform and Sequence";
	private static final Class<?>[] NONE = new Class<?>[0];

	private static final class CaptureApp extends EditorApp {
		private final Path output;
		private final String dimensions;
		private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
		private final StringBuilder evidence = new StringBuilder("PASS\n");
		private Throwable failure;
		private boolean exiting;
		private int frames, phase;
		private Stage stage;
		private SkeletonDocument doc;
		private EditHistory history;
		private AttachmentPanel attachments;
		private PlaybackPanel playback;
		private TimelinePanel timeline;
		private Window dialog;
		private Animation motion;
		private Animation.DeformTimeline deform;
		private float[] originalDeform;
		private int slotIndex;
		private Skin first;
		private Skin renamed, blank, blankCopy, renamedCopy;
		private MeshAttachment renamedMesh;

		CaptureApp(Path output, int width, int height) { super(null); this.output = output; dimensions = width + "x" + height; }

		@Override public void create() {
			try {
				super.create();
				invoke(EditorApp.class, this, "setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				stage = (Stage)field(EditorApp.class, this, "stage");
				doc = (SkeletonDocument)field(EditorApp.class, this, "doc");
				history = (EditHistory)field(EditorApp.class, this, "history");
				attachments = (AttachmentPanel)field(EditorApp.class, this, "attachPanel");
				playback = (PlaybackPanel)field(EditorApp.class, this, "playbackPanel");
				timeline = (TimelinePanel)field(EditorApp.class, this, "timelinePanel");
				fixture();
				invoke(EditorApp.class, this, "clearHistoryAndRebuildPanels", NONE);
				timeline.restoreViewSettings(TimelinePanel.DEFAULT_ZOOM, TimelinePanel.FILTER_KEYED);
				((TextButton)field(TimelinePanel.class, timeline, "keyframeViewBtn")).fire(new ChangeEvent());
				require(field(AttachmentPanel.class, attachments, "history") == history
					&& field(PlaybackPanel.class, playback, "history") == history, "Both actual entry points must use the active shared history");
				openFromAttachments();
				editName(LONG_NAME);
				name().setCursorPosition(0);
				require(!save().isDisabled(), "Long permanent name incorrectly disabled; labels=" + labels(dialog).stream().map(label -> label.getText().toString()).toList()
					+ "; model=" + new SkeletonEditor(doc).getSkinCompositionBlockReason(doc.getPreviewSkinNames(), LONG_NAME));
			} catch (Throwable error) { stop(error); }
		}

		private void fixture() {
			var slot = doc.data.findSlot("torso"); slotIndex = slot.getIndex();
			RegionAttachment original = (RegionAttachment)doc.data.getDefaultSkin().getAttachment(slotIndex, "torso");
			Sequence sequence = new Sequence(1, false); sequence.getRegions()[0] = original.getSequence().getRegions()[0];
			MeshAttachment mesh = new MeshAttachment("torso", sequence); mesh.setPath(original.getPath());
			mesh.setVertices(new float[] {-30, -60, 30, -60, 30, 60, -30, 60}); mesh.setWorldVerticesLength(8);
			mesh.setRegionUVs(new float[] {0, 1, 1, 1, 1, 0, 0, 0}); mesh.setTriangles(new short[] {0, 1, 2, 2, 3, 0});
			mesh.setHullLength(8); mesh.setWidth(60); mesh.setHeight(120); mesh.updateSequence();
			Skin base = new Skin(BASE), overlay = new Skin(OVERLAY); base.setAttachment(slotIndex, "torso", mesh);
			doc.data.getSkins().addAll(base, overlay);
			for (String name : List.of("袖口 / Winter Variant", "手套 / Canvas", "靴子 / Weatherproof", "头盔 / Worklight",
				"背包 / Extended Expedition Collection With A Long Descriptive Name", "围巾 / Red", "腰带 / Utility", "护臂 / Rolled", "标记 / Studio"))
				doc.data.getSkins().add(new Skin(name));
			deform = new Animation.DeformTimeline(2, 1, slotIndex, mesh);
			deform.setFrame(0, 0f, mesh.getVertices().clone()); originalDeform = mesh.getVertices().clone(); originalDeform[4] += 10f;
			deform.setFrame(1, 1f, originalDeform); deform.setBezier(0, 0, 0, 0, 0, 0.2f, 0.1f, 0.8f, 0.9f, 1, 1);
			Animation.SequenceTimeline sequenceTrack = new Animation.SequenceTimeline(2, slotIndex, mesh);
			sequenceTrack.setFrame(0, 0f, Sequence.SequenceMode.hold, 0, 0f);
			sequenceTrack.setFrame(1, 0.5f, Sequence.SequenceMode.loop, 0, 0.125f);
			Array<Animation.Timeline> tracks = new Array<>(true, 2, Animation.Timeline[]::new); tracks.add(deform, sequenceTrack);
			motion = new Animation(MOTION); motion.setTimelines(tracks, new IntArray()); motion.setDuration(1.5f);
			doc.data.getAnimations().clear(); doc.data.getAnimations().add(motion);
			new SkeletonEditor(doc).rebuildSkeleton(); doc.setPreviewSkins(List.of(BASE, OVERLAY));
			doc.setAnimation(MOTION); doc.scrubTo(0.65f); doc.setPlaying(false);
		}

		@Override public void render() {
			if (exiting) return;
			try {
				super.render();
				if (System.nanoTime() > deadline) throw new AssertionError("Capture timed out at phase " + phase);
				if (++frames < 20) return;
				frames = 0;
				switch (phase++) {
				case 0 -> {
					verifyDialog("long-name", false); capture("long-name");
					editName(BASE); require(save().isDisabled(), "Duplicate name must disable permanent save");
					require(labels(dialog).stream().anyMatch(label -> label.getText().toString().equals("皮肤名称已存在")), "Duplicate name reason missing");
				}
				case 1 -> {
					verifyDialog("duplicate-name", false); capture("duplicate-name");
					deform.getVertices()[1] = new float[2]; editName("保存组合 A");
					require(save().isDisabled(), "Incompatible Deform must disable save");
				}
				case 2 -> {
					verifyDialog("multiline-reason", true); capture("multiline-reason");
					deform.getVertices()[1] = originalDeform; editName("保存组合 A ");
					save().fire(new ChangeEvent());
					require(dialog.getParent() == null, "Actual permanent save did not close dialog");
					first = doc.data.findSkin("保存组合 A"); require(first != null, "Attachment entry failed to save permanent skin");
					verifyCopiedTracks(first, 4);
					require(history.undo() && doc.data.findSkin(first.getName()) == null, "Actual save is not undoable");
					verifyPlaybackSkinItems();
					require(history.redo() && doc.data.findSkin(first.getName()) == first, "Actual save redo lost attachment identity");
					verifyCopiedTracks(first, 4);
					showSavedTracks();
				}
				case 3 -> {
					capture("saved-tracks");
					doc.setPreviewSkins(List.of(BASE, OVERLAY)); invoke(EditorApp.class, this, "skinSelectionChanged", NONE);
					playback.skinCombinationBtn.fire(new ChangeEvent()); dialog = currentDialog(); editName("保存组合 B");
					require(!save().isDisabled(), "Playback entry lacks history binding");
					save().fire(new ChangeEvent()); Skin second = doc.data.findSkin("保存组合 B");
					require(second != null && dialog.getParent() == null, "Playback entry save did not publish");
					verifyCopiedTracks(second, 6);
					require(history.undo(), "Playback save undo failed"); verifyPlaybackSkinItems();
					require(history.undo() && motion.getTimelines().size == 2, "Two actual entry point edits must undo in order"); verifyPlaybackSkinItems();
					require(history.redo(), "First entry redo failed"); verifyPlaybackSkinItems();
					require(history.redo() && doc.data.findSkin(second.getName()) == second, "Two entry point edits must redo in order");
					verifyCopiedTracks(second, 6);
					showSavedTracks();
				}
				case 4 -> {
					capture("playback-saved-tracks");
					renameAndEditWithEarlierCompositionHistory();
					showSkinManagement();
				}
				case 5 -> {
					capture("renamed-skin-history");
					panelButton("addSkinBtn").fire(new ChangeEvent()); blank = doc.getEditingSkin();
					require(blank != renamed && "新建皮肤".equals(history.undoLabel()), "New skin UI did not retain reversible history");
					panelButton("duplicateSkinBtn").fire(new ChangeEvent()); blankCopy = doc.getEditingSkin();
					require(blankCopy != blank && "复制皮肤".equals(history.undoLabel()), "Duplicate skin UI did not record history");
					renameFromPanel("可撤销皮肤"); renamedCopy = doc.getEditingSkin();
					panelButton("delSkinBtn").fire(new ChangeEvent()); dialog = titledDialog("删除皮肤");
					require(labels(dialog).stream().anyMatch(label -> label.getText().toString().contains("可通过撤销恢复")),
						"Delete dialog must describe reversible deletion");
				}
				case 6 -> {
					require(dialog.getX() >= 0 && dialog.getY() >= 0 && dialog.getRight() <= stage.getWidth()
						&& dialog.getTop() <= stage.getHeight(), "Delete dialog exceeds stage");
					capture("reversible-skin-delete");
					buttonText(dialog, "删除").fire(new ChangeEvent());
					require(doc.data.findSkin("可撤销皮肤") == null && "删除皮肤".equals(history.undoLabel()), "Delete UI did not record reversible removal");
					require(history.undo() && doc.data.findSkin("可撤销皮肤") == renamedCopy, "Delete undo lost renamed copy identity"); verifyPlaybackSkinItems();
					require(history.undo() && doc.data.findSkin(blankCopy.getName()) == blankCopy, "Rename undo lost copied skin identity"); verifyPlaybackSkinItems();
					require(history.undo() && doc.data.findSkin(blankCopy.getName()) == null, "Duplicate undo did not remove copied skin"); verifyPlaybackSkinItems();
					require(history.undo() && doc.getEditingSkin() == renamed && history.canUndo(), "New skin undo lost earlier renamed composition history"); verifyPlaybackSkinItems();
					require(history.redo() && history.redo() && history.redo() && history.redo(), "Skin lifecycle UI history failed complete redo"); verifyPlaybackSkinItems();
					require(history.undo() && doc.data.findSkin("可撤销皮肤") == renamedCopy, "Final deletion undo must restore the retained instance"); verifyPlaybackSkinItems();
					showSkinManagement();
				}
				case 7 -> {
					capture("skin-delete-undone");
					evidence.append("attachment_entry_actual_save_undo_redo=true\nplayback_entry_actual_save_undo_redo=true\n")
						.append("active_history_binding=true\ncopied_deform_and_sequence_in_timeline_canvas=true\n")
						.append("playback_skin_items_order_and_selection_match_after_each_create_undo_redo=true\n")
						.append("two_entry_ordered_undo_redo=true\nsource_tracks_untouched=true\n")
						.append("actual_rename_then_deform_edit_preserves_composition_history=true\n")
						.append("actual_new_copy_rename_delete_buttons_preserve_history=true\n")
						.append("delete_confirmation_and_recovery=true\nretained_skin_and_attachment_identities=true\n");
					Files.writeString(output.resolve("permanent-skin-" + dimensions + ".txt"), evidence);
					System.out.println("OK: permanent skin UI " + dimensions + "\n" + evidence); stop(null);
				}
				default -> throw new AssertionError("Unexpected phase");
				}
			} catch (Throwable error) { stop(error); }
		}

		private void renameAndEditWithEarlierCompositionHistory() {
			Skin beforeRename = doc.getEditingSkin();
			renameFromPanel("永久组合·重命名"); renamed = doc.getEditingSkin();
			require(renamed != beforeRename && "重命名皮肤".equals(history.undoLabel()), "Actual rename button did not create reversible rename");
			verifyCopiedTracks(renamed, 6);
			renamedMesh = (MeshAttachment)renamed.getAttachment(slotIndex, "torso");
			AnimationEditor editor = new AnimationEditor(doc);
			var before = editor.snapshotDeformTimeline(motion, slotIndex, renamedMesh);
			float[] offsets = new float[8]; offsets[4] = 18f;
			editor.addDeformKeyframe(motion, slotIndex, renamedMesh, 1f, offsets);
			var after = editor.snapshotDeformTimeline(motion, slotIndex, renamedMesh);
			history.record("重命名皮肤的 Deform", () -> editor.restoreTimeline(before), () -> editor.restoreTimeline(after));
			require(history.undo(), "Post-rename Deform undo failed");
			require(history.undo() && doc.data.findSkin(beforeRename.getName()) == beforeRename, "Rename undo did not restore original composition instance"); verifyPlaybackSkinItems();
			require(history.undo() && doc.data.findSkin(beforeRename.getName()) == null, "Earlier second composition undo failed after rename"); verifyPlaybackSkinItems();
			require(history.undo() && doc.data.findSkin(first.getName()) == null && !history.canUndo(), "Earlier first composition undo failed after rename"); verifyPlaybackSkinItems();
			require(history.redo() && history.redo() && history.redo() && history.redo(), "Full composition, rename, track-edit chain failed redo");
			require(doc.getEditingSkin() == renamed && renamed.getAttachment(slotIndex, "torso") == renamedMesh,
				"Complete redo lost retained Skin/Attachment identity");
			require(Math.abs(editor.findDeformTimeline(motion, slotIndex, renamedMesh).getVertices()[1][4] - 48f) < 0.001f,
				"Post-rename Deform value did not survive complete redo");
			verifyCopiedTracks(renamed, 6); showSavedTracks();
		}

		private TextButton panelButton(String fieldName) { return (TextButton)field(AttachmentPanel.class, attachments, fieldName); }
		private void renameFromPanel(String text) {
			TextField input = (TextField)field(AttachmentPanel.class, attachments, "skinNameField");
			input.setText(text); input.fire(new ChangeEvent());
			require(!panelButton("renameSkinBtn").isDisabled(), "Rename action stayed disabled for changed valid name");
			panelButton("renameSkinBtn").fire(new ChangeEvent());
			require(doc.getCurrentSkinName().equals(text), "Rename UI did not select renamed skin"); verifyPlaybackSkinItems();
		}
		private void showSkinManagement() {
			stage.act(0f);
			for (Actor parent = attachments.getParent(); parent != null; parent = parent.getParent()) if (parent instanceof ScrollPane scroll) {
				scroll.layout(); scroll.setScrollPercentY(1f); scroll.setScrollX(0f); scroll.updateVisualScroll(); break;
			}
		}

		private void verifyCopiedTracks(Skin skin, int total) {
			verifyPlaybackSkinItems();
			require(motion.getTimelines().size == total && motion.getTimelines().first() == deform, "Copied tracks or source track identities incorrect");
			MeshAttachment mesh = (MeshAttachment)skin.getAttachment(slotIndex, "torso");
			Object canvas = field(TimelinePanel.class, timeline, "canvas");
			Array<?> tracks = (Array<?>)field(canvas.getClass(), canvas, "tracks");
			boolean foundDeform = false, foundSequence = false;
			for (Object track : tracks) {
				Object data = field(track.getClass(), track, "timeline");
				if (data instanceof Animation.DeformTimeline candidate && candidate.getAttachment() == mesh) foundDeform = true;
				if (data instanceof Animation.SequenceTimeline candidate && candidate.getAttachment() == mesh) foundSequence = true;
			}
			require(foundDeform && foundSequence, "Actual save callback left copied tracks absent from timeline canvas");
			require(doc.getCurrentSkinName().equals(skin.getName()) && Math.abs(doc.getCurrentTime() - 0.65f) < 0.001f && !doc.playing,
				"Save/history changed selection or paused cursor");
		}

		private void verifyPlaybackSkinItems() {
			require(playback.skinBox.getItems().size == doc.data.getSkins().size, "Playback skin item count is stale");
			for (int i = 0; i < doc.data.getSkins().size; i++)
				require(playback.skinBox.getItems().get(i).equals(doc.data.getSkins().get(i).getName()), "Playback item order/name is stale at " + i);
			require(doc.getCurrentSkinName().equals(playback.skinBox.getSelected()), "Playback skin selector differs from current document skin");
		}

		private void showSavedTracks() {
			((SplitPane)field(EditorApp.class, this, "workspace")).setSplitAmount(0.42f);
			stage.act(0f);
			ScrollPane scroll = (ScrollPane)field(TimelinePanel.class, timeline, "scroll");
			scroll.layout(); scroll.setScrollPercentY(1f); scroll.updateVisualScroll();
		}

		private void openFromAttachments() {
			((TextButton)field(EditorApp.class, this, "attachmentsTabBtn")).fire(new ChangeEvent());
			require(attachments.getStage() == stage, "Attachment inspector tab did not attach its controls");
			((TextButton)field(AttachmentPanel.class, attachments, "skinCombinationBtn")).fire(new ChangeEvent()); dialog = currentDialog();
		}
		private Window currentDialog() {
			return titledDialog("皮肤组合");
		}
		private Window titledDialog(String title) {
			for (Actor actor : stage.getActors()) if (actor instanceof Window window && window.getTitleLabel().getText().toString().equals(title)) return window;
			throw new AssertionError("Modal was not opened: " + title);
		}
		private TextButton buttonText(Group root, String text) { return descendants(root).stream().filter(TextButton.class::isInstance).map(TextButton.class::cast)
			.filter(button -> button.getText().toString().equals(text)).findFirst().orElseThrow(); }
		private TextField name() { return descendants(dialog).stream().filter(TextField.class::isInstance).map(TextField.class::cast).findFirst().orElseThrow(); }
		private void editName(String text) { TextField input = name(); input.setText(text); input.fire(new ChangeEvent()); }
		private TextButton save() { return descendants(dialog).stream().filter(TextButton.class::isInstance).map(TextButton.class::cast)
			.filter(button -> button.getText().toString().equals("保存为新皮肤")).findFirst().orElseThrow(); }

		private void verifyDialog(String mode, boolean multiline) {
			require(dialog.getX() >= 0 && dialog.getY() >= 0 && dialog.getRight() <= stage.getWidth() + 1f
				&& dialog.getTop() <= stage.getHeight() + 1f, "Modal exceeds stage for " + mode);
			Rectangle modal = bounds(dialog);
			for (Actor actor : descendants(dialog)) if (actor instanceof TextButton button) {
				Rectangle label = bounds(button.getLabel()), buttonBounds = bounds(button);
				require(button.getLabel().getGlyphLayout().width <= label.width + 1f && label.x >= buttonBounds.x - 1f
					&& label.x + label.width <= buttonBounds.x + buttonBounds.width + 1f, "Button label clipped: " + button.getText());
				if (List.of("保存为新皮肤", "取消", "应用").contains(button.getText().toString()))
					require(modal.contains(buttonBounds), "Action outside modal: " + button.getText());
			}
			if (multiline) {
				Label error = labels(dialog).stream().filter(label -> label.getText().toString().contains("Deform 顶点数量")).findFirst().orElseThrow();
				require(error.getGlyphLayout().height > error.getStyle().font.getLineHeight(), "Rejection fixture did not wrap into multiple lines");
				require(bounds(error).y >= bounds(save()).y + save().getHeight(), "Multiline reason overlaps save button");
				require(error.getGlyphLayout().height <= error.getHeight() + 1f, "Wrapped rejection text clipped vertically");
			}
			evidence.append(mode).append("_modal=").append(modal).append("\n");
		}
		private void capture(String mode) throws Exception {
			Files.createDirectories(output);
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try { BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length); PixmapIO.writePNG(new FileHandle(output.resolve(mode + "-" + dimensions + ".png").toFile()), pixmap); }
			finally { pixmap.dispose(); }
		}
		private void stop(Throwable error) { failure = error; exiting = true; Gdx.app.exit(); }
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 3) throw new IllegalArgumentException("Expected width height output-directory");
		Path captureHome = Files.createTempDirectory("openspine-permanent-skin-ui-").toRealPath();
		String previousHome = System.getProperty("user.home"), previousAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", captureHome.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, captureHome.resolve("autosave").toString());
		int width = Integer.parseInt(args[0]), height = Integer.parseInt(args[1]);
		CaptureApp app = new CaptureApp(Path.of(args[2]).toAbsolutePath(), width, height);
		try {
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setTitle("OpenSpine Hidden Permanent Skin Verification"); config.setInitialVisible(false);
			config.setWindowedMode(width, height); config.setResizable(false); config.setForegroundFPS(60); config.setIdleFPS(60);
			config.disableAudio(true); config.setPreferencesConfig(captureHome.toString(), com.badlogic.gdx.Files.FileType.Absolute);
			new Lwjgl3Application(app, config);
		} finally {
			System.setProperty("user.home", previousHome);
			if (previousAutosave == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY); else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, previousAutosave);
			try (var paths = Files.walk(captureHome)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					require(path.toAbsolutePath().normalize().startsWith(captureHome), "Cleanup escaped isolated home"); Files.deleteIfExists(path);
				}
			}
			require(!Files.exists(captureHome), "Temporary home was not cleaned");
			System.out.println("CLEANED: " + captureHome);
		}
		if (app.failure != null) throw new AssertionError("Hidden skin capture failed", app.failure);
	}

	private static List<Actor> descendants(Group group) {
		List<Actor> result = new ArrayList<>(); for (Actor actor : group.getChildren()) { result.add(actor); if (actor instanceof Group child) result.addAll(descendants(child)); } return result;
	}
	private static List<Label> labels(Group group) { return descendants(group).stream().filter(Label.class::isInstance).map(Label.class::cast).toList(); }
	private static Rectangle bounds(Actor actor) { Vector2 at = actor.localToStageCoordinates(new Vector2()); return new Rectangle(at.x, at.y, actor.getWidth(), actor.getHeight()); }
	private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
	private static Object field(Class<?> type, Object target, String name) {
		try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
		catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot read " + name, failure); }
	}
	private static Object invoke(Class<?> type, Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
		try { Method method = type.getDeclaredMethod(name, parameterTypes); method.setAccessible(true); return method.invoke(target, arguments); }
		catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot invoke " + name, failure); }
	}
	private PermanentSkinCapture() {}
}
