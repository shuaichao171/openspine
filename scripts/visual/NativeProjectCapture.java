package com.openspine.io;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.SkeletonJson;
import com.openspine.EditorApp;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonProject;
import com.openspine.ui.UiAppearance;
import com.openspine.view.EditorViewport;

/** Asynchronous EditorApp native-save round trips, with real textures and hidden UI capture. */
public final class NativeProjectCapture {
	private static final String PAGE = "人物/图集页[1].png", REGION = "图片";
	private static final Class<?>[] NONE = new Class<?>[0];
	private static final Class<?>[] CALLBACKS = {Runnable.class, Runnable.class};

	private static final class CaptureApp extends EditorApp {
		private final Path home, output;
		private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(55);
		private final String dimensions;
		private final boolean progressOnly;
		private volatile Throwable failure;
		private volatile boolean exiting;
		private int phase, stableFrames;
		private boolean callbackComplete, callbackSuccess;
		private Path manifest, blueSource, copy, conflictRuntime;
		private byte[] conflictBefore, manifestBeforeCopy;
		private Path associationBeforeCopy;
		private List<String> documentPathsBeforeCopy;
		private List<Long> historyStatesBeforeCopy;
		private float discardedWidth, discardedMix;
		private Map<Path, byte[]> firstGeneration, originalSources;
		private Window management;
		private Runnable shutdownOpenCallback;
		private boolean shutdownCallbackChecked;
		private Runnable progressOpenCallback;

		CaptureApp(Path home, Path output, int width, int height, boolean progressOnly) {
			super(null, home);
			this.home = home;
			this.output = output;
			dimensions = width + "x" + height;
			this.progressOnly = progressOnly;
		}

		@Override public void create() {
			try {
				super.create();
				invoke("setAppearanceMode", new Class<?>[] {UiAppearance.Mode.class}, UiAppearance.Mode.LIGHT);
				if (progressOnly) {
					manifest = home.resolve("大型动画工程.openspine-project.json");
					NativeProjectStore.save(project(), false, manifest, NativeProjectStore.inspect(manifest), new CancellationToken());
					progressOpenCallback = pendingOpen(manifest);
					return;
				}
				Path first = fixture(home.resolve("source-a"), 0xffe52d3e, -85f);
				Path second = fixture(home.resolve("source-b"), 0xff74d739, 85f);
				blueSource = fixture(home.resolve("source-c"), 0xff397fe0, -120f);
				originalSources = sourceBytes(List.of(first, second, blueSource));
				invoke("replaceProjectSkeletons", new Class<?>[] {List.class}, List.of(load(first, "角色主体", 0.35f, 0.5f)));
				invoke("addProjectSkeleton", new Class<?>[] {SkeletonProject.LoadedSkeleton.class, boolean.class},
					load(second, "装备", 1.2f, 1.5f), false);
				invoke("finishLoad", new Class<?>[] {String.class}, "隐藏原生工程集成验证");
				editActiveX(-95f);
				invoke("switchProjectSkeleton", new Class<?>[] {int.class}, 1);
				editActiveX(95f);
				invoke("switchProjectSkeleton", new Class<?>[] {int.class}, 0);
				setField("showAllSkeletons", true);
				manifest = output.resolve("完整动画.openspine-project.json");
				require(project().hasStructureChanges() && field("nativeProjectPath") == null,
					"Autosave fixture must have unsaved membership and no native association");
				invoke("saveCurrentSpineProjectState", NONE);
				for (Path path : List.of(first, second)) require(!Files.exists(ProjectStateStore.sidecarPath(path)),
					"Unsaved project membership leaked into a standalone source sidecar");
				setField("nextAutosaveAt", 0L);
				setField("lastAutosaveSignature", Long.MIN_VALUE);
				invoke("maybeAutosave", NONE);
				phase = 0;
			} catch (Throwable error) { stop(error); }
		}

		@Override public void render() {
			if (exiting) return;
			try {
				super.render();
				if (System.nanoTime() > deadline) throw new AssertionError("Hidden native verification timed out at phase " + phase);
				if (progressOnly) {
					if (++stableFrames == 20) {
						verifyOpenProgress();
						capture(output.resolve("native-open-progress-" + dimensions + ".png"), false);
						invoke("cancelDocumentIo", new Class<?>[] {boolean.class}, false);
						progressOpenCallback.run();
						require(!Boolean.TRUE.equals(invoke("documentIoInProgress", NONE)), "Progress capture cancellation did not release IO ownership");
						Files.writeString(output.resolve("native-open-progress-" + dimensions + ".txt"),
							"PASS\nreal_native_open_progress_window=true\nstatus_and_cancel_labels_unclipped=true\nwindow_and_controls_within_stage=true\nheld_completion_cancelled_and_cleaned=true\n");
						System.out.println("OK: actual native-open progress window, labels, cancel layout; " + dimensions);
						stop(null);
					}
					return;
				}
				switch (phase) {
				case 0 -> { if (!(Boolean)field("autosaveInProgress")) afterInitialAutosave(); }
				case 1 -> { if (awaitSave(true)) afterFirstSave(); }
				case 2 -> { if (awaitSave(true)) afterMembershipSave(); }
				case 3 -> { if (awaitSave(true)) afterRuntimeCopy(); }
				case 4 -> { if (awaitSave(false)) afterConflict(); }
				case 5 -> { if (awaitSave(true)) afterRetry(); }
				case 8 -> { if (!Boolean.TRUE.equals(invoke("documentIoInProgress", NONE))) afterMembershipReopen(); }
				case 9 -> { if (!Boolean.TRUE.equals(invoke("documentIoInProgress", NONE))) afterDiscardReopen(); }
				case 6 -> {
					if (++stableFrames == 30) {
						capture(output.resolve("native-workspace-" + dimensions + ".png"), true);
						invoke("showProjectSkeletons", NONE);
						management = currentWindow("管理骨架", "Manage Skeletons");
						stableFrames = 0;
						phase = 7;
					}
				}
				case 7 -> {
					if (++stableFrames == 20) {
						verifyDialog();
						capture(output.resolve("native-project-dialog-" + dimensions + ".png"), false);
						verifyOriginalSources();
						verifyPreparedOpenLifecycle();
						verifyEmptyStartupFailure();
						shutdownOpenCallback = pendingOpen(manifest);
						Files.writeString(output.resolve("native-check-" + dimensions + ".txt"), "PASS\n"
							+ "asynchronous_native_save=true\ninactive_document_edit=true\n"
							+ "unsaved_membership_autosave_and_recovery_markers=true\n"
							+ "dialog_rename_and_reorder=true\nremove_and_add_members=true\n"
							+ "history_saved_points_and_undo_redo=true\nrelease_and_reopen=true\n"
							+ "runtime_copy_preserves_association_and_dirty=true\nexternal_conflict_preserves_dirty=true\n"
							+ "discard_and_reopen_preserves_saved_authoring_values=true\n"
							+ "async_open_cancel_after_prepare=true\nopen_preserves_new_edits_and_replacement=true\n"
							+ "open_detects_late_resource_changes=true\nfailed_multipage_open_deletes_candidate_textures=true\n"
							+ "empty_startup_failure_loads_example=true\n"
							+ "original_sources_and_previous_generation_unchanged=true\n"
							+ "real_unicode_same_name_atlas_pages=true\ndialog_bounds_and_button_labels=true\n",
							StandardCharsets.UTF_8);
						System.out.println("OK: native save, Ctrl+S path, history save points, membership, reopen, Runtime copy, conflict, textures, " + dimensions);
						stop(null);
					}
				}
				default -> throw new AssertionError("Unknown phase " + phase);
				}
			} catch (Throwable error) { stop(error); }
		}

		private void afterInitialAutosave() throws Exception {
			AutoSaveStore.Session session = AutoSaveStore.latest();
			require(session != null && session.documents().size() == 2, "Unsaved membership did not produce a complete autosave");
			String marker = (String)field("RECOVERED_NATIVE_PROJECT");
			for (AutoSaveStore.StoredDocument stored : session.documents()) {
				require(marker.equals(stored.filePath()) && stored.savePath().isBlank(), "Autosave member could overwrite an old runtime instead of saving the project");
				require(Files.isDirectory(Path.of(stored.resourceDirectory())), "Autosave lost external atlas resources");
			}
			invoke("recoverAutosave", new Class<?>[] {AutoSaveStore.Session.class}, session);
			require(project().size() == 2 && project().activeIndex() == 0 && project().isDirty(), "Autosave recovery lost membership, active document or dirty state");
			require(field("nativeProjectPath") == null && Boolean.TRUE.equals(invoke("usesNativeProjectWorkflow", NONE)),
				"Recovered unsaved membership lost full-project save workflow");
			for (SkeletonDocument document : project().documents()) {
				require(marker.equals(document.filePath) && document.savePath == null && document.dirty, "Recovered member lacks safe save marker");
				require(document.atlas.findRegion(REGION) != null, "Recovered member lost real texture");
			}
			require("角色主体".equals(project().nameAt(0)) && "装备".equals(project().nameAt(1)), "Autosave recovery changed names/order");
			require(Math.abs(active().data.getBones().first().getSetupPose().getX() + 95f) < 0.001f, "Autosave recovery lost runtime edits");
			// Undo stacks intentionally restart after recovery; create fresh entries to check native save points.
			editActiveX(-96f);
			editActiveX(-95f);
			invoke("switchProjectSkeleton", new Class<?>[] {int.class}, 1);
			editActiveX(96f);
			editActiveX(95f);
			invoke("switchProjectSkeleton", new Class<?>[] {int.class}, 0);
			startSave(true);
			phase = 1;
		}

		private void afterFirstSave() throws Exception {
			verifySaved(List.of("角色主体", "装备"), new float[] {-95f, 95f}, 0);
			firstGeneration = treeBytes(ProjectPackageManifest.read(manifest).editorState().getParent());
			for (int index = 0; index < 2; index++) {
				invoke("switchProjectSkeleton", new Class<?>[] {int.class}, index);
				verifySavedHistoryUndoRedo();
			}
			invoke("switchProjectSkeleton", new Class<?>[] {int.class}, 0);
			SkeletonDocument inactive = project().documents().get(1);
			inactive.data.getBones().first().getSetupPose().setX(135f);
			inactive.markDirty();
			require(project().documents().get(0) != inactive && inactive.dirty, "Inactive edit must remain parked and dirty");
			invoke("showProjectSkeletons", NONE);
			Window window = currentWindow("管理骨架", "Manage Skeletons");
			List<TextField> fields = actors(window, TextField.class);
			require(fields.size() == 2, "Management dialog lacks both members");
			fields.get(1).setText("装备与特效");
			buttons(window, "重命名", "Rename").get(1).fire(new ChangeEvent());
			require("装备与特效".equals(project().nameAt(1)), "Rename button did not update project membership");
			buttons(window, "^").get(1).fire(new ChangeEvent());
			require("装备与特效".equals(project().nameAt(0)) && project().activeIndex() == 1,
				"Move button did not preserve active identity and update order");
			buttons(window, "关闭", "Close").get(0).fire(new ChangeEvent());
			invoke("removeProjectSkeleton", new Class<?>[] {int.class}, 1);
			invoke("addProjectSkeleton", new Class<?>[] {SkeletonProject.LoadedSkeleton.class, boolean.class},
				load(blueSource, "背景装饰", 0.6f, 0.75f), true);
			editActiveX(-145f);
			require(project().hasStructureChanges(), "Rename, order, remove and add must dirty project structure");
			invoke("rebuildPanels", NONE);
			startSave(false);
			phase = 2;
		}

		private void afterMembershipSave() throws Exception {
			verifySaved(List.of("装备与特效", "背景装饰"), new float[] {135f, -145f}, 1);
			verifySavedHistoryUndoRedo();
			verifyTreeUnchanged(firstGeneration, "Later save changed previous generation");
			// Release the old project and every texture before using the real editor open path.
			invoke("replaceProjectSkeletons", new Class<?>[] {List.class}, List.of(load(blueSource, "Temporary", 0f, 1f)));
			require(field("nativeProjectPath") == null, "Replacing a project must clear its native association");
			require(Boolean.TRUE.equals(invoke("loadFile", new Class<?>[] {String.class}, manifest.toString())), "Editor could not reopen saved manifest");
			require(Boolean.TRUE.equals(invoke("documentIoInProgress", NONE)), "Native open must prepare asynchronously");
			phase = 8;
		}

		private void afterMembershipReopen() throws Exception {
			verifySaved(List.of("装备与特效", "背景装饰"), new float[] {135f, -145f}, 1);
			require(Math.abs(project().documents().get(0).getCurrentTime() - 1.2f) < 0.001f, "Inactive editor time was lost");
			require(Math.abs(active().getCurrentTime() - 0.6f) < 0.001f, "Active editor time was lost");
			editActiveX(-155f);
			associationBeforeCopy = (Path)field("nativeProjectPath");
			documentPathsBeforeCopy = project().documents().stream().map(document -> document.savePath).toList();
			historyStatesBeforeCopy = historyStates();
			manifestBeforeCopy = Files.readAllBytes(manifest);
			copy = output.resolve("runtime-copy.json");
			resetCallback();
			invoke("saveTo", new Class<?>[] {String.class, Runnable.class, Runnable.class}, copy.toString(), success(), incomplete());
			phase = 3;
		}

		private void afterRuntimeCopy() throws Exception {
			require(Files.isRegularFile(copy), "Runtime copy was not written");
			var exported = new SkeletonJson(active().atlas).readSkeletonData(new FileHandle(copy.toFile()));
			require(Math.abs(exported.getBones().first().getSetupPose().getX() + 155f) < 0.001f, "Runtime copy lacks current edits");
			require(associationBeforeCopy.equals(field("nativeProjectPath")), "Runtime copy changed native association");
			require(documentPathsBeforeCopy.equals(project().documents().stream().map(document -> document.savePath).toList()),
				"Runtime copy changed document save associations");
			require(active().dirty && project().isDirty() && !history().isAtSavedState(), "Runtime copy falsely marked the project saved");
			require(historyStatesBeforeCopy.equals(historyStates()), "Runtime copy altered undo/redo or saved positions");
			require(Arrays.equals(manifestBeforeCopy, Files.readAllBytes(manifest)), "Runtime copy changed the native manifest");
			conflictRuntime = ProjectPackageManifest.read(manifest).skeletons().get(0).runtime();
			conflictBefore = Files.readAllBytes(conflictRuntime);
			Files.writeString(conflictRuntime, "\n ", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
			startSave(false);
			phase = 4;
		}

		private void afterConflict() throws Exception {
			require(active().dirty && project().isDirty() && !history().isAtSavedState(), "Conflicting save cleared unsaved edits");
			require(historyStatesBeforeCopy.equals(historyStates()), "Conflicting save altered history saved positions");
			require(associationBeforeCopy.equals(field("nativeProjectPath")), "Conflict changed native association");
			require(Arrays.equals(manifestBeforeCopy, Files.readAllBytes(manifest)), "Conflict replaced old manifest");
			require(Files.size(conflictRuntime) == conflictBefore.length + 2, "Conflict discarded externally changed bytes");
			Files.write(conflictRuntime, conflictBefore);
			// The expected error dialog belongs exclusively to this hidden verification app.
			for (Actor actor : stage().getActors().toArray()) if (actor instanceof Window) actor.remove();
			stage().setKeyboardFocus(null);
			startSave(false);
			phase = 5;
		}

		private void afterRetry() throws Exception {
			verifySaved(List.of("装备与特效", "背景装饰"), new float[] {135f, -155f}, 1);
			verifySavedHistoryUndoRedo();
			verifyTreeUnchanged(firstGeneration, "Conflict/retry changed older generation");
			discardedWidth = active().data.getWidth();
			discardedMix = active().defaultAnimationMix();
			active().data.setWidth(discardedWidth + 321f);
			active().setAnimationMixSettings(discardedMix + 0.9f, List.of());
			active().markDirty();
			invoke("saveCurrentSpineProjectState", NONE);
			invoke("replaceProjectSkeletons", new Class<?>[] {List.class}, List.of(load(blueSource, "Temporary", 0f, 1f)));
			require(Boolean.TRUE.equals(invoke("loadFile", new Class<?>[] {String.class}, manifest.toString())), "Editor could not reopen after discard");
			phase = 9;
		}

		private void afterDiscardReopen() throws Exception {
			verifySaved(List.of("装备与特效", "背景装饰"), new float[] {135f, -155f}, 1);
			require(Math.abs(active().data.getWidth() - discardedWidth) < 0.001f, "Discarded width resurrected through automatic root sidecar");
			require(Math.abs(active().defaultAnimationMix() - discardedMix) < 0.001f, "Discarded mix resurrected through automatic root sidecar");
			invoke("rebuildPanels", NONE);
			((EditorViewport)field("viewport")).frameAll();
			stableFrames = 0;
			phase = 6;
		}

		private void editActiveX(float value) {
			BoneData root = active().data.getBones().first();
			float before = root.getSetupPose().getX();
			root.getSetupPose().setX(value);
			history().record("验证工程编辑", () -> root.getSetupPose().setX(before), () -> root.getSetupPose().setX(value));
		}

		private void verifySavedHistoryUndoRedo() {
			require(history().isAtSavedState() && !active().dirty, "Save did not set active history saved point");
			require(history().undo() && active().dirty && !history().isAtSavedState(), "Undo after save must dirty the document");
			require(history().redo() && !active().dirty && history().isAtSavedState(), "Redo to saved point must restore clean state");
		}

		private void verifySaved(List<String> names, float[] xValues, int activeIndex) throws Exception {
			require(manifest.equals(field("nativeProjectPath")), "Successful save/open lost native association");
			require(NativeProjectStore.inspect(manifest).equals(field("nativeProjectStamp")), "Editor retained an incorrect native baseline");
			require(project().size() == names.size() && project().activeIndex() == activeIndex, "Project membership/active index mismatch");
			require(!project().isDirty() && !project().hasStructureChanges(), "Native save/open left project dirty");
			require((Boolean)field("showAllSkeletons"), "Show-all preference was lost");
			ProjectPackageManifest.Manifest stored = ProjectPackageManifest.read(manifest);
			for (int index = 0; index < names.size(); index++) {
				SkeletonDocument document = project().documents().get(index);
				require(names.get(index).equals(project().nameAt(index)), "Member name/order mismatch");
				require(Math.abs(document.data.getBones().first().getSetupPose().getX() - xValues[index]) < 0.001f, "Runtime setup value mismatch at " + index);
				require(!document.dirty && histories().get(index).isAtSavedState(), "Native save did not mark every document/history saved");
				require(Path.of(document.savePath).equals(stored.skeletons().get(index).runtime()), "Document association differs from saved generation");
				require(document.atlas.findRegion(REGION) != null && document.atlas.findRegion(REGION).getRegionWidth() == 64, "Disk atlas was not restored");
			}
			require(project().documents().get(0).atlas.findRegion(REGION).getTexture()
				!= project().documents().get(1).atlas.findRegion(REGION).getTexture(), "Duplicate texture names collided");
		}

		private void startSave(boolean first) {
			resetCallback();
			if (first) invoke("saveNativeProjectTo", new Class<?>[] {Path.class, Runnable.class, Runnable.class}, manifest, success(), incomplete());
			else invoke("save", CALLBACKS, success(), incomplete());
		}
		private void resetCallback() { callbackComplete = false; callbackSuccess = false; }
		private Runnable success() { return () -> { callbackComplete = true; callbackSuccess = true; }; }
		private Runnable incomplete() { return () -> { callbackComplete = true; callbackSuccess = false; }; }
		private boolean awaitSave(boolean expected) {
			if (!callbackComplete) return false;
			require(!Boolean.TRUE.equals(invoke("documentIoInProgress", NONE)), "Callback ran before IO state was cleared");
			require(callbackSuccess == expected, "Unexpected save outcome at phase " + phase + ": " + field("statusLabel"));
			return true;
		}

		private void verifyDialog() {
			management.validate();
			require(management.getX() >= 0f && management.getY() >= 0f
				&& management.getRight() <= stage().getWidth() + 1f && management.getTop() <= stage().getHeight() + 1f,
				"Management dialog exceeds visible stage");
			List<TextField> fields = actors(management, TextField.class);
			require(fields.size() == 2 && fields.get(0).getText().equals("装备与特效") && fields.get(1).getText().equals("背景装饰"),
				"Management dialog does not reflect reopened membership");
			for (TextButton button : actors(management, TextButton.class)) {
				button.validate();
				Vector2 point = button.localToStageCoordinates(new Vector2());
				require(point.x >= 0f && point.y >= 0f && point.x + button.getWidth() <= stage().getWidth() + 1f
					&& point.y + button.getHeight() <= stage().getHeight() + 1f, "Management button extends outside stage");
				require(button.getLabel().getGlyphLayout().width <= button.getLabel().getWidth() + 1f, "Management button label is clipped: " + button.getText());
			}
		}

		private void verifyOpenProgress() {
			Window window = (Window)field("documentIoProgress");
			require(window != null && window.isModal(), "Native-open progress modal is missing");
			window.validate();
			require(window.getTitleLabel().getText().toString().equals("打开 OpenSpine 工程"), "Native-open title is incorrect");
			require(window.getX() >= 0f && window.getY() >= 0f && window.getRight() <= stage().getWidth() + 1f
				&& window.getTop() <= stage().getHeight() + 1f, "Native-open progress window exceeds stage");
			Label status = (Label)field("documentIoProgressStatus");
			require(status.getText().toString().equals("正在检查工程与图片、音频资源"), "Resource-check wording is missing");
			TextButton cancel = (TextButton)field("documentIoCancelButton");
			require(cancel.getText().toString().equals("取消") && !cancel.isDisabled(), "Cancel must be visible and enabled");
			for (Label label : List.of(window.getTitleLabel(), status, cancel.getLabel())) {
				label.validate();
				require(label.getGlyphLayout().width <= label.getWidth() + 1f, "Native-open label is clipped: " + label.getText());
			}
			Vector2 position = cancel.localToStageCoordinates(new Vector2());
			require(position.x >= window.getX() && position.y >= window.getY()
				&& position.x + cancel.getWidth() <= window.getRight() + 1f
				&& position.y + cancel.getHeight() <= window.getTop() + 1f, "Cancel extends outside the progress window");
		}

		private void capture(Path file, boolean checkTextures) throws Exception {
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			int distinct = 0;
			for (int offset = 4; offset < pixels.length; offset += 4)
				if (pixels[offset] != pixels[0] || pixels[offset + 1] != pixels[1] || pixels[offset + 2] != pixels[2]) distinct++;
			require(distinct > width * height / 10, "Native capture framebuffer is blank");
			if (checkTextures) {
				EditorViewport viewport = (EditorViewport)field("viewport");
				Vector2 origin = viewport.localToStageCoordinates(new Vector2());
				int green = 0, blue = 0;
				for (int y = Math.max(0, height - (int)(origin.y + viewport.getHeight())); y < Math.min(height, height - (int)origin.y); y++)
					for (int x = Math.max(0, (int)origin.x); x < Math.min(width, (int)(origin.x + viewport.getWidth())); x++) {
						int offset = (y * width + x) * 4;
						int r = pixels[offset] & 255, g = pixels[offset + 1] & 255, b = pixels[offset + 2] & 255;
						if (r > 65 && r < 155 && g > 160 && b < 110) green++;
						if (r < 110 && g > 80 && g < 170 && b > 180) blue++;
					}
				require(green > 200 && blue > 200, "Reopened green and blue textures are not visible: " + green + ", " + blue);
			}
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try {
				BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(file.toFile()), pixmap);
			} finally { pixmap.dispose(); }
		}

		private SkeletonProject.LoadedSkeleton load(Path source, String name, float time, float speed) {
			SkeletonDocument document = new SkeletonDocument();
			try {
				SkeletonLoader.loadRuntime(document, new FileHandle(source.toFile()), new FileHandle(source.getParent().toFile()), "sprite.atlas");
				document.animationState = document.newAnimationState();
				document.setAnimation("move");
				document.setLooping(true);
				document.playbackSpeed = speed;
				document.scrubTo(time);
				document.setSelectedBone(document.skeleton.findBone("body"));
				return new SkeletonProject.LoadedSkeleton(name, document);
			} catch (Throwable error) { document.dispose(); throw error; }
		}
		private void verifyOriginalSources() throws Exception { verifyTreeUnchanged(originalSources, "Native save altered an original source"); }

		/** Capture the actual worker callback, so lifecycle boundaries are deterministic without timing guesses. */
		private Runnable pendingOpen(Path target) throws Exception {
			Application original = Gdx.app;
			AtomicReference<Runnable> callback = new AtomicReference<>();
			Application relay = (Application)Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[] {Application.class},
				(proxy, method, args) -> {
					if (method.getName().equals("postRunnable") && Thread.currentThread().getName().equals("openspine-project-open")) {
						require(callback.compareAndSet(null, (Runnable)args[0]), "Native worker posted multiple completion callbacks");
						return null;
					}
					try { return method.invoke(original, args); }
					catch (InvocationTargetException error) { throw error.getCause(); }
				});
			try {
				Gdx.app = relay;
				require(Boolean.TRUE.equals(invoke("loadFile", new Class<?>[] {String.class}, target.toString())), "Native open was not queued");
				Thread worker = (Thread)field("documentIoThread");
				require(worker != null && worker != Thread.currentThread(), "Native preparation must use a worker");
				worker.join(5000);
				require(!worker.isAlive() && callback.get() != null, "Native preparation did not produce its callback");
				require(Boolean.TRUE.equals(invoke("documentIoInProgress", NONE)), "IO ownership ended before GL acceptance");
				return callback.get();
			} finally { Gdx.app = original; }
		}

		private void verifyPreparedOpenLifecycle() throws Exception {
			closeTestDialogs();
			Object identity = active().data;
			Path association = (Path)field("nativeProjectPath");
			int textures = Texture.getNumManagedTextures();
			Runnable cancelled = pendingOpen(manifest);
			invoke("cancelDocumentIo", new Class<?>[] {boolean.class}, false);
			cancelled.run();
			require(active().data == identity && association.equals(field("nativeProjectPath")), "Queued cancellation replaced the old project");
			require(Texture.getNumManagedTextures() == textures, "Queued cancellation created textures");

			Runnable edited = pendingOpen(manifest);
			float savedX = active().data.getBones().first().getSetupPose().getX();
			editActiveX(savedX + 9f);
			long revision = active().revision;
			edited.run();
			require(active().data == identity && active().revision == revision && project().isDirty(), "Open discarded edits made during preparation");
			require(Texture.getNumManagedTextures() == textures, "Rejected changed project allocated candidate textures");
			closeTestDialogs();
			require(history().undo(), "New edit could not be undone after rejecting open");

			Path runtime = ProjectPackageManifest.read(manifest).skeletons().get(0).runtime();
			byte[] runtimeBytes = Files.readAllBytes(runtime);
			Runnable changed = pendingOpen(manifest);
			try {
				Files.writeString(runtime, "\n", StandardOpenOption.APPEND);
				changed.run();
				require(active().data == identity && Texture.getNumManagedTextures() == textures, "Late runtime write was accepted or leaked textures");
			} finally { Files.write(runtime, runtimeBytes); }
			closeTestDialogs();

			Path secondRuntime = ProjectPackageManifest.read(manifest).skeletons().get(1).runtime();
			Path atlas = secondRuntime.getParent().resolve("sprite.atlas");
			byte[] atlasBytes = Files.readAllBytes(atlas);
			Path brokenPage = atlas.resolveSibling("broken-second-page.png");
			try {
				Files.write(brokenPage, new byte[] {1, 2, 3, 4});
				Files.writeString(atlas, "\nbroken-second-page.png\nsize: 1,1\nformat: RGBA8888\nfilter: Linear,Linear\nrepeat: none\n", StandardOpenOption.APPEND);
				Runnable broken = pendingOpen(manifest);
				GL20 originalGl = Gdx.gl, originalGl20 = Gdx.gl20;
				Set<Integer> created = new HashSet<>(), deleted = new HashSet<>();
				GL20 observed = (GL20)Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[] {GL20.class}, (proxy, method, args) -> {
					try {
						Object result = method.invoke(originalGl, args);
						if (method.getName().equals("glGenTexture")) created.add((Integer)result);
						if (method.getName().equals("glDeleteTexture")) deleted.add((Integer)args[0]);
						return result;
					} catch (InvocationTargetException error) { throw error.getCause(); }
				});
				try { Gdx.gl = observed; Gdx.gl20 = observed; broken.run(); }
				finally { Gdx.gl = originalGl; Gdx.gl20 = originalGl20; }
				require(created.size() >= 2 && deleted.containsAll(created), "Failed multi-page open did not delete every candidate GL texture: created=" + created + ", deleted=" + deleted);
				require(active().data == identity && association.equals(field("nativeProjectPath")), "Failed multi-page load replaced the old project");
				require(Texture.getNumManagedTextures() == textures, "Failed multi-page load leaked managed textures");
			} finally { Files.write(atlas, atlasBytes); Files.deleteIfExists(brokenPage); }
			closeTestDialogs();

			Runnable replaced = pendingOpen(manifest);
			invoke("replaceProjectSkeletons", new Class<?>[] {List.class}, List.of(load(blueSource, "Replacement", 0f, 1f)));
			Object replacement = active().data;
			int replacementTextures = Texture.getNumManagedTextures();
			replaced.run();
			require(active().data == replacement && project().size() == 1 && field("nativeProjectPath") == null,
				"Open discarded a replacement project created during preparation");
			require(Texture.getNumManagedTextures() == replacementTextures, "Rejected replacement allocated textures");
			closeTestDialogs();
			pendingOpen(manifest).run();
			verifySaved(List.of("装备与特效", "背景装饰"), new float[] {135f, -155f}, 1);
		}

		private void verifyEmptyStartupFailure() throws Exception {
			closeTestDialogs();
			project().close();
			setField("nativeProjectPath", null);
			setField("nativeProjectStamp", null);
			require(((SkeletonDocument)field("doc")).data == null, "Startup failure fixture must contain no old document");
			Path missing = home.resolve("missing.openspine-project.json");
			pendingOpen(missing).run();
			require(active().data != null && project().size() == 2, "Failed asynchronous startup did not load the built-in example");
			closeTestDialogs();
		}

		private void closeTestDialogs() {
			for (Actor actor : stage().getActors().toArray()) if (actor instanceof Window) actor.remove();
			stage().setKeyboardFocus(null);
		}

		@Override public void dispose() {
			try {
				super.dispose();
				if (shutdownOpenCallback != null) {
					int textures = Texture.getNumManagedTextures();
					shutdownOpenCallback.run();
					require(Texture.getNumManagedTextures() == textures, "Queued callback allocated textures after dispose");
					shutdownCallbackChecked = true;
					Files.writeString(output.resolve("native-check-" + dimensions + ".txt"), "queued_callback_after_dispose_creates_no_textures=true\n", StandardOpenOption.APPEND);
				}
			} catch (Throwable error) { if (failure == null) failure = error; }
		}
		private SkeletonProject project() { return (SkeletonProject)field("skeletonProject"); }
		private SkeletonDocument active() { return project().documents().get(project().activeIndex()); }
		private EditHistory history() { return (EditHistory)field("history"); }
		@SuppressWarnings("unchecked") private List<EditHistory> histories() { return (List<EditHistory>)field("documentHistories"); }
		private List<Long> historyStates() {
			List<Long> states = new ArrayList<>();
			for (EditHistory history : histories()) {
				states.add((Long)readField(EditHistory.class, history, "currentDocumentState"));
				states.add((Long)readField(EditHistory.class, history, "savedDocumentState"));
			}
			return List.copyOf(states);
		}
		private Stage stage() { return (Stage)field("stage"); }
		private Window currentWindow(String... titles) {
			for (Actor actor : stage().getActors()) if (actor instanceof Window window)
				if (Arrays.asList(titles).contains(window.getTitleLabel().getText().toString())) return window;
			throw new AssertionError("Expected dialog missing: " + Arrays.toString(titles));
		}
		private Object field(String name) { return readField(EditorApp.class, this, name); }
		private void setField(String name, Object value) {
			try { Field field = EditorApp.class.getDeclaredField(name); field.setAccessible(true); field.set(this, value); }
			catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
		}
		private Object invoke(String name, Class<?>[] types, Object... values) {
			try {
				Method method = EditorApp.class.getDeclaredMethod(name, types);
				method.setAccessible(true);
				return method.invoke(this, values);
			} catch (InvocationTargetException error) { throw new IllegalStateException(name, error.getCause()); }
			catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
		}
		private void stop(Throwable error) {
			if (error != null && failure == null) failure = error;
			exiting = true;
			if (Gdx.app != null) Gdx.app.exit();
		}
	}

	private static Path fixture(Path directory, int color, float x) throws Exception {
		Files.createDirectories(directory);
		BufferedImage image = new BufferedImage(64, 96, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 96; y++) for (int px = 0; px < 64; px++) {
			boolean border = px < 3 || px > 60 || y < 3 || y > 92;
			boolean detail = px > 15 && px < 48 && (y > 20 && y < 28 || y > 65 && y < 73);
			image.setRGB(px, y, border ? 0xff202020 : detail ? 0xffffffff : color);
		}
		Path page = directory.resolve(PAGE);
		Files.createDirectories(page.getParent());
		ImageIO.write(image, "PNG", page.toFile());
		Files.writeString(directory.resolve("sprite.atlas"), """
			%s
			size: 64,96
			format: RGBA8888
			filter: Linear,Linear
			repeat: none
			%s
			  rotate: false
			  xy: 0,0
			  size: 64,96
			  orig: 64,96
			  offset: 0,0
			  index: -1
			""".formatted(PAGE, REGION), StandardCharsets.UTF_8);
		Path json = directory.resolve("rig.json");
		Files.writeString(json, """
			{
			  "skeleton":{"spine":"4.3.0","images":"./"},
			  "bones":[{"name":"root","x":%s},{"name":"body","parent":"root","length":65}],
			  "slots":[{"name":"body","bone":"body","attachment":"sprite"}],
			  "skins":{"default":{"name":"default","attachments":{"body":{"sprite":{"path":"%s","width":64,"height":96}}}}},
			  "animations":{"move":{"bones":{"body":{"translate":[{"time":0,"x":0,"y":0},{"time":1,"x":30,"y":15},{"time":2,"x":0,"y":0}]}}}}
			}
			""".formatted(Float.toString(x), REGION), StandardCharsets.UTF_8);
		return json;
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 3 || args.length > 4) throw new IllegalArgumentException("Expected width, height, output directory, optional progress-only");
		boolean progressOnly = args.length == 4 && args[3].equals("progress-only");
		int width = Integer.parseInt(args[0]), height = Integer.parseInt(args[1]);
		Path output = Files.createDirectories(Path.of(args[2]).toAbsolutePath().normalize());
		Path home = Files.createTempDirectory(output, ".native-session-home-").toRealPath();
		String oldHome = System.getProperty("user.home"), oldAutosave = System.getProperty(AutoSaveStore.DIRECTORY_PROPERTY);
		System.setProperty("user.home", home.toString());
		System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, home.resolve("autosave").toString());
		CaptureApp app = new CaptureApp(home, output, width, height, progressOnly);
		Thread watchdog = new Thread(() -> {
			try { Thread.sleep(60000); app.stop(new AssertionError("Hidden native verification exceeded 60 seconds")); }
			catch (InterruptedException finished) { Thread.currentThread().interrupt(); }
		}, "openspine-native-capture-watchdog");
		watchdog.setDaemon(true);
		try {
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setTitle("OpenSpine Hidden Native Project Verification");
			config.setInitialVisible(false);
			config.setWindowedMode(width, height);
			config.setResizable(false);
			config.setForegroundFPS(60);
			config.setIdleFPS(60);
			config.disableAudio(true);
			config.setPreferencesConfig(home.toString(), com.badlogic.gdx.Files.FileType.Absolute);
			watchdog.start();
			new Lwjgl3Application(app, config);
		} finally {
			watchdog.interrupt();
			System.setProperty("user.home", oldHome);
			if (oldAutosave == null) System.clearProperty(AutoSaveStore.DIRECTORY_PROPERTY);
			else System.setProperty(AutoSaveStore.DIRECTORY_PROPERTY, oldAutosave);
			// Cleanup is confined to the unique session directory created above.
			require(home.getParent().equals(output.toRealPath()), "Session cleanup target escaped output directory");
			try (var paths = Files.walk(home)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		if (app.failure != null) throw new AssertionError("Hidden native project verification failed", app.failure);
		if (!progressOnly) require(app.shutdownCallbackChecked, "Queued open callback was not verified after actual disposal");
	}

	private static Map<Path, byte[]> sourceBytes(List<Path> sources) throws Exception {
		Map<Path, byte[]> result = new LinkedHashMap<>();
		for (Path source : sources) result.putAll(treeBytes(source.getParent()));
		return result;
	}
	private static Map<Path, byte[]> treeBytes(Path directory) throws Exception {
		Map<Path, byte[]> result = new LinkedHashMap<>();
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) result.put(path, Files.readAllBytes(path));
		}
		return result;
	}
	private static void verifyTreeUnchanged(Map<Path, byte[]> files, String message) throws Exception {
		for (var file : files.entrySet()) require(Arrays.equals(file.getValue(), Files.readAllBytes(file.getKey())), message + ": " + file.getKey());
	}
	private static List<TextButton> buttons(Group group, String... names) {
		return actors(group, TextButton.class).stream().filter(button -> Arrays.asList(names).contains(button.getText().toString())).toList();
	}
	private static <T extends Actor> List<T> actors(Group group, Class<T> type) {
		List<T> result = new ArrayList<>();
		for (Actor actor : group.getChildren()) {
			if (type.isInstance(actor)) result.add(type.cast(actor));
			if (actor instanceof Group child) result.addAll(actors(child, type));
		}
		return result;
	}
	private static Object readField(Class<?> type, Object target, String name) {
		try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
	private NativeProjectCapture() {}
}
