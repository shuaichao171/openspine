package com.openspine.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.openspine.model.SkeletonDocument;

/** Parameter controls shared by mesh generation commands. */
final class MeshGenerationDialog {
	record AutoWeightSettings(int[] bones, int maxInfluences, float falloff) {}
	record TraceSettings(boolean concave, int threshold, float tolerance, int maxVertices) {}

	static void subdivide(Stage stage, UiKit kit, IntConsumer action) {
		Form form = new Form(stage, kit, "自动细分");
		SelectBox<String> passes = kit.selectBox();
		passes.setItems("1", "2", "3", "4");
		form.row("细分级别", passes);
		form.open(() -> action.accept(passes.getSelectedIndex() + 1));
	}

	static void trace(Stage stage, UiKit kit, Consumer<TraceSettings> action) {
		Form form = new Form(stage, kit, "图片轮廓生成");
		SelectBox<String> outline = kit.selectBox();
		outline.setItems("凹轮廓", "凸包外轮廓");
		form.row("轮廓", outline);
		Slider threshold = new Slider(1f, 255f, 1f, false, kit.sliderStyle);
		threshold.setValue(16f);
		form.slider("Alpha 阈值", threshold);
		Slider tolerance = new Slider(0f, 8f, 0.25f, false, kit.sliderStyle);
		tolerance.setValue(1f);
		form.slider("简化容差 (px)", tolerance);
		SelectBox<String> maximum = kit.selectBox();
		maximum.setItems("32", "64", "128", "256", "512", "1024", "2048", "4096");
		maximum.setSelected("256");
		form.row("顶点上限", maximum);
		outline.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				boolean convex = outline.getSelectedIndex() == 1;
				tolerance.setDisabled(convex);
				maximum.setDisabled(convex);
			}
		});
		form.open(() -> action.accept(new TraceSettings(outline.getSelectedIndex() == 0,
			Math.round(threshold.getValue()), tolerance.getValue(), Integer.parseInt(maximum.getSelected()))));
	}

	static void autoWeight(Stage stage, UiKit kit, SkeletonDocument document, int defaultBone,
		Consumer<AutoWeightSettings> action) {
		Form form = new Form(stage, kit, "自动权重");
		SelectBox<String> maximum = kit.selectBox();
		maximum.setItems("1", "2", "3", "4", "5", "6", "7", "8");
		maximum.setSelected("4");
		form.row("最大影响数", maximum);
		Slider falloff = new Slider(0.5f, 8f, 0.5f, false, kit.sliderStyle);
		falloff.setValue(2f);
		form.slider("距离衰减", falloff);
		Table bones = new Table();
		List<TextButton> selected = new ArrayList<>();
		for (BoneData bone : document.data.getBones()) {
			TextButton toggle = new TextButton(bone.getName(), kit.toolButton);
			toggle.getLabel().setEllipsis(true);
			boolean checked = document.getSelectedBones().stream().map(Bone::getData).anyMatch(value -> value == bone)
				|| document.getSelectedBones().isEmpty() && bone.getIndex() == defaultBone;
			toggle.setChecked(checked);
			toggle.setStyle(checked ? kit.activeToolButton : kit.toolButton);
			toggle.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					toggle.setStyle(toggle.isChecked() ? kit.activeToolButton : kit.toolButton);
				}
			});
			selected.add(toggle);
			bones.add(toggle).growX().minWidth(0f).height(27f).padBottom(3f).row();
		}
		ScrollPane scroll = new ScrollPane(bones, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		scroll.setScrollingDisabled(true, false);
		form.window.add(kit.controlLabel("参与骨骼")).left().padTop(8f).row();
		form.window.add(scroll).colspan(2).growX().height(Math.min(240f, selected.size() * 30f)).padTop(4f).row();
		form.open(() -> {
			int[] indices = java.util.stream.IntStream.range(0, selected.size())
				.filter(index -> selected.get(index).isChecked()).toArray();
			if (indices.length == 0) throw new IllegalArgumentException("至少选择一个参与骨骼");
			action.accept(new AutoWeightSettings(indices, maximum.getSelectedIndex() + 1, falloff.getValue()));
		});
	}

	private static final class Form {
		final Stage stage;
		final UiKit kit;
		final Window window;
		final Actor previous;
		final Label error;

		Form(Stage stage, UiKit kit, String title) {
			this.stage = stage;
			this.kit = kit;
			previous = stage.getKeyboardFocus();
			window = kit.window(title);
			window.setModal(true);
			kit.padWindow(window, 12f);
			error = kit.controlLabel("");
			error.setColor(1f, 0.45f, 0.38f, 1f);
			error.setWrap(true);
		}

		void row(String label, Actor actor) {
			window.add(kit.controlLabel(label)).left().padRight(12f).padBottom(7f);
			window.add(actor).width(250f).height(28f).padBottom(7f).row();
		}

		void slider(String label, Slider slider) {
			Table controls = new Table();
			Label value = kit.controlLabel(UiNumbers.format(slider.getValue()));
			slider.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { value.setText(UiNumbers.format(slider.getValue())); }
			});
			controls.add(slider).growX().padRight(8f);
			controls.add(value).width(38f);
			row(label, controls);
		}

		void open(Runnable action) {
			TextButton apply = new TextButton("生成", kit.accentButton);
			TextButton cancel = new TextButton("取消", kit.toolButton);
			Runnable close = () -> UiDialogs.closeModal(stage, window, previous);
			Runnable submit = () -> {
				try { action.run(); close.run(); }
				catch (RuntimeException failure) { error.setText(failure.getMessage()); window.pack(); }
			};
			apply.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
			});
			cancel.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
			});
			window.add(error).colspan(2).width(390f).left().padTop(5f).row();
			Table actions = new Table();
			actions.add(cancel).width(82f).height(28f).padRight(7f);
			actions.add(apply).width(82f).height(28f);
			window.add(actions).colspan(2).right().padTop(10f);
			window.addListener(new InputListener() {
				@Override public boolean keyDown(InputEvent event, int keycode) {
					if (keycode == Input.Keys.ESCAPE) { close.run(); return true; }
					if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) { submit.run(); return true; }
					return false;
				}
			});
			window.pack();
			window.setPosition((stage.getWidth() - window.getWidth()) / 2f, (stage.getHeight() - window.getHeight()) / 2f);
			stage.addActor(window);
			stage.setKeyboardFocus(window);
		}
	}

	private MeshGenerationDialog() {}
}
