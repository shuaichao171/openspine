package com.openspine.ui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Hidden isolated GL verification: actual dialog buttons, atomic failure, apply/undo/redo, framebuffer capture. */
public final class ConstraintClipboardCapture {
	private static final String[] TYPES = {"Ik", "Transform", "Path", "Physics", "Slider"};
	private static final class Capture extends ApplicationAdapter {
		final Path output;
		Stage stage;
		UiKit kit;
		SkeletonDocument doc;
		SkeletonEditor editor;
		EditHistory history;
		Object dialog;
		int frames, index;
		Throwable failure;
		boolean stopped;
		Capture(Path output) { this.output = output; }
		@Override public void create() {
			try {
				stage = new Stage(new ScreenViewport());
				kit = new UiKit(UiAppearance.palette(false, UiAppearance.Theme.JADE));
				prepare();
			} catch (Throwable error) { stop(error); }
		}
		private String mixField() { return index == 1 || index == 2 ? "mixRotateField" : "mixField"; }
		private String mixAccessor() { return index == 1 || index == 2 ? "mixRotate" : "mix"; }
		private void prepare() throws Exception {
			doc = fixture(); editor = new SkeletonEditor(doc); history = new EditHistory(doc);
			BoneData child = doc.data.findBone("child"), root = doc.data.findBone("root");
			Skin skin = doc.data.getDefaultSkin();
			switch (index) {
			case 0 -> editor.addIkConstraint(new SkeletonEditor.IkConstraintSetup("destination", child, null, root,
				true, ScaleYMode.none, 1, 0, 1, false, false), skin, true);
			case 1 -> editor.addTransformConstraint(new SkeletonEditor.TransformConstraintSetup("destination", List.of(child),
				root, true, false, false, false, false, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1,
				SkeletonEditor.defaultTransformProperties()), skin, true);
			case 2 -> editor.addPathConstraint(new SkeletonEditor.PathConstraintSetup("destination", List.of(child),
				doc.data.findSlot("path-slot"), true, PathConstraintData.PositionMode.percent,
				PathConstraintData.SpacingMode.length, PathConstraintData.RotateMode.tangent, 0, 0, 0, 1, 1, 1), skin, true);
			case 3 -> editor.addPhysicsConstraint(new SkeletonEditor.PhysicsConstraintSetup("destination", child,
				true, ScaleYMode.none, 0, 0, 0, 0, 0, 5000, 60, 0.5f, 100, 0.85f, 1, 0, 0, 1,
				false, false, false, false, false, false, false), skin, true);
			case 4 -> editor.addSliderConstraint(new SkeletonEditor.SliderConstraintSetup("destination",
				doc.data.findAnimation("driven"), true, false, false, null, null, 0, 0, 1, 0, false,
				0.1234567f, 1), skin, true);
			}
			require(doc.data.getConstraints().size == 1, "fixture must create the target constraint");
			history.clear(); history.markSaved();
			Class<?> type = Class.forName("com.openspine.ui." + TYPES[index] + "ConstraintDialog");
			Constructor<?> constructor = type.getDeclaredConstructor(Stage.class, SkeletonDocument.class, UiKit.class,
				SkeletonEditor.class, EditHistory.class, Runnable.class, BiConsumer.class);
			constructor.setAccessible(true);
			dialog = constructor.newInstance(stage, doc, kit, editor, history, null, null);
			invoke(dialog, "open");
			((TextField)field(dialog, "nameField")).setText("source-name");
			((TextField)field(dialog, mixField())).setText("0.375");
			((TextButton)field(dialog, "skinActiveButton")).setChecked(false);
			click("复制设置");
			require(((Label)field(dialog, "errorLabel")).getText().length() == 0, "copy validates the draft");
			((TextField)field(dialog, mixField())).setText(".e");
			click("复制设置");
			require(((Label)field(dialog, "errorLabel")).getText().length() > 0, "invalid draft copy is rejected");
			invoke(dialog, "loadSelection");
			click("粘贴设置");
			require(((TextField)field(dialog, "nameField")).getText().equals("destination"), "paste retains target name");
			require(((TextButton)field(dialog, "skinActiveButton")).isChecked(), "paste retains target skin activation");
			require(((TextField)field(dialog, mixField())).getText().equals("0.375"), "paste fills copied values");
			require(!doc.dirty && !history.canUndo(), "copy and paste do not change history or dirty state");
			require(modelMix() == 1f, "paste does not change setup data");
			require(!((TextButton)field(dialog, "applyButton")).isDisabled(), "paste enables apply");
		}
		@Override public void render() {
			if (stopped) return;
			try {
				ScreenUtils.clear(0.9f, 0.92f, 0.92f, 1);
				stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f)); stage.draw();
				if (++frames % 8 != 0) return;
				Window window = (Window)field(dialog, "window");
				require(window.getX() >= 0 && window.getY() >= 0
					&& window.getRight() <= stage.getWidth() && window.getTop() <= stage.getHeight(), "dialog fits viewport");
				capture(TYPES[index].toLowerCase() + "-pasted-draft");
				click("应用");
				require(modelMix() == 0.375f && history.canUndo() && doc.dirty, "apply updates the model with undo history");
				if (index == 4) require(SkeletonEditor.sliderConstraintSetup(
					(com.esotericsoftware.spine.SliderData)doc.data.getConstraints().first()).time() == 0.1234567f,
					"copy and apply preserve Slider seconds exactly at 24 FPS");
				require(history.undo() && modelMix() == 1f, "undo restores original setup");
				require(!history.canUndo() && !doc.dirty, "apply creates exactly one undo step");
				require(history.redo() && modelMix() == 0.375f, "redo restores pasted setup");
				invoke(dialog, "loadSelection");
				Object before = invoke(dialog, "currentDraft");
				BoneData unknown = new BoneData(0, "missing-bone", null);
				ConstraintClipboard.SESSION.copy(new SkeletonEditor.IkConstraintSetup("bad", unknown, null,
					doc.data.findBone("root"), false, ScaleYMode.none, 0.1f, 0, 1, false, false));
				click("粘贴设置");
				require(before.equals(invoke(dialog, "currentDraft")), "failed paste leaves the complete draft unchanged");
				String error = ((Label)field(dialog, "errorLabel")).getText().toString();
				require(error.contains(index == 0 ? "missing-bone" : "不能粘贴到"), "paste explains missing reference or type mismatch");
				require(modelMix() == 0.375f, "failed paste leaves setup unchanged");
				invoke(dialog, "close");
				require(stage.getActors().isEmpty(), "close removes the task's dialog");
				doc.dispose(); doc = null;
				if (++index == TYPES.length) stop(null); else prepare();
			} catch (Throwable error) { stop(error); }
		}
		private float modelMix() throws Exception {
			String method = TYPES[index].substring(0, 1).toLowerCase() + TYPES[index].substring(1) + "ConstraintSetup";
			Object constraint = doc.data.getConstraints().first();
			Object setup = SkeletonEditor.class.getMethod(method, constraint.getClass()).invoke(null, constraint);
			return (float)setup.getClass().getMethod(mixAccessor()).invoke(setup);
		}
		private void click(String label) { button((Group)field(dialog, "window"), label).fire(new ChangeEvent()); }
		private void capture(String name) {
			int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			try {
				BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
				PixmapIO.writePNG(new FileHandle(output.resolve(width + "x" + height + "-" + name + ".png").toFile()), pixmap);
			} finally { pixmap.dispose(); }
		}
		private void stop(Throwable error) { failure = error; stopped = true; Gdx.app.exit(); }
		@Override public void dispose() {
			if (dialog != null) invoke(dialog, "close");
			if (stage != null) stage.dispose();
			if (kit != null) kit.dispose();
			if (doc != null) doc.dispose();
		}
	}
	private static SkeletonDocument fixture() {
		SkeletonDocument doc = new SkeletonDocument();
		doc.data = new SkeletonData(); doc.data.setFps(24f);
		BoneData root = new BoneData(0, "root", null), child = new BoneData(1, "child", root);
		root.getSetupPose().setInherit(BoneData.Inherit.normal); child.getSetupPose().setInherit(BoneData.Inherit.normal);
		doc.data.getBones().addAll(root, child);
		SlotData slot = new SlotData(0, "path-slot", root);
		slot.setBlendMode(com.esotericsoftware.spine.BlendMode.normal); doc.data.getSlots().add(slot);
		Skin skin = new Skin("default"); doc.data.getSkins().add(skin); doc.data.setDefaultSkin(skin);
		Animation animation = new Animation("driven");
		animation.setTimelines(new Array<>(true, 0, Animation.Timeline[]::new), new IntArray());
		animation.setDuration(2f); doc.data.getAnimations().add(animation);
		doc.skeleton = new Skeleton(doc.data); doc.skeleton.setSkin(skin);
		doc.animationState = doc.newAnimationState();
		return doc;
	}
	public static void main(String[] args) throws Exception {
		Path output = Files.createDirectories(Path.of(args[2]).toAbsolutePath());
		Capture app = new Capture(output);
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("OpenSpine Hidden Constraint Clipboard Verification");
		config.setInitialVisible(false); config.disableAudio(true); config.setResizable(false);
		config.setWindowedMode(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		config.setForegroundFPS(60); config.setIdleFPS(60);
		new Lwjgl3Application(app, config);
		if (app.failure != null) throw new AssertionError("Constraint clipboard UI verification failed", app.failure);
		System.out.println("ALL FIVE CONSTRAINT CLIPBOARD UI CHECKS PASSED");
	}
	private static TextButton button(Group group, String label) {
		for (Actor child : group.getChildren()) {
			if (child instanceof TextButton button && button.getText().toString().equals(label)) return button;
			if (child instanceof Group nested) { TextButton result = button(nested, label); if (result != null) return result; }
		}
		return null;
	}
	private static Object field(Object target, String name) {
		try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static Object invoke(Object target, String name) {
		try { Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target); }
		catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
