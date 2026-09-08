package com.openspine.ui;

import java.util.function.BiFunction;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

/** Reparenting options make the distinction between local values and setup world preservation explicit. */
final class BoneParentDialog {
	static void show(Stage stage, UiKit kit, String initial, BiFunction<String, Boolean, String> apply) {
		Actor previous = stage.getKeyboardFocus();
		Window window = kit.window(I18n.t("设置父级", "Set parent"));
		window.setModal(true);
		kit.padWindow(window, 12f);
		TextField parent = new TextField(initial, kit.textFieldStyle);
		parent.setName("parentBoneName");
		parent.setMaxLength(64);
		TextButton preserve = new TextButton(I18n.t("保持 Setup 世界姿态", "Keep Setup world transform"), kit.toolButton);
		preserve.setName("keepSetupWorld");
		Label explanation = kit.controlLabel("");
		explanation.setWrap(true);
		Label error = kit.controlLabel("");
		error.setName("parentError");
		error.setColor(1f, 0.45f, 0.38f, 1f);
		error.setWrap(true);
		Runnable update = () -> {
			preserve.setStyle(preserve.isChecked() ? kit.activeToolButton : kit.toolButton);
			explanation.setText(preserve.isChecked()
				? I18n.t("仅补偿 Setup 姿态。含约束或相关骨骼动画时不可用。", "Compensates Setup only. Unavailable with constraints or animation on related bones.")
				: I18n.t("保留本地数值；更换父级可能改变世界姿态。", "Keeps local values. Changing the parent may change the world transform."));
			window.pack();
			window.setPosition((stage.getWidth() - window.getWidth()) / 2f, (stage.getHeight() - window.getHeight()) / 2f);
		};
		preserve.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { update.run(); }
		});
		TextButton cancel = new TextButton(I18n.t("取消", "Cancel"), kit.toolButton);
		TextButton confirm = new TextButton(I18n.t("确定", "OK"), kit.accentButton);
		confirm.setName("applyParent");
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			UiDialogs.closeModal(stage, window, previous);
		};
		Runnable submit = () -> {
			if (closed[0]) return;
			String name = parent.getText().trim();
			try {
				String failure = name.isEmpty() ? I18n.t("请输入父级骨骼名称", "Enter the parent bone name") : apply.apply(name, preserve.isChecked());
				if (failure == null) close.run();
				else { error.setText(failure); update.run(); }
			} catch (RuntimeException failure) {
				error.setText(failure.getMessage() == null ? I18n.t("无法修改骨骼父级", "Unable to change parent") : failure.getMessage());
				update.run();
			}
		};
		confirm.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
		});
		window.add(kit.controlLabel(I18n.t("父级骨骼", "Parent bone"))).left().row();
		window.add(parent).width(390f).height(28f).padTop(5f).row();
		window.add(preserve).width(390f).height(28f).padTop(10f).row();
		window.add(explanation).width(390f).left().padTop(7f).row();
		window.add(error).width(390f).left().padTop(5f).row();
		Table actions = new Table();
		actions.add(cancel).width(84f).height(28f).padRight(6f);
		actions.add(confirm).width(84f).height(28f);
		window.add(actions).right().padTop(10f);
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (keycode == Input.Keys.ESCAPE) { close.run(); return true; }
				if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) { submit.run(); return true; }
				return false;
			}
		});
		update.run();
		stage.addActor(window);
		stage.setKeyboardFocus(parent);
		parent.selectAll();
	}
	private BoneParentDialog() {}
}
