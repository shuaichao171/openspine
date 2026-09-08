package com.openspine.ui;

import java.util.function.BiConsumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.openspine.model.SkeletonProject;

/** Project membership controls; each applied name or order change participates in project dirty state. */
public final class ProjectSkeletonDialog {
	private final Stage stage;
	private final UiKit kit;
	private final SkeletonProject project;
	private final BiConsumer<Integer, Integer> move;
	private final Runnable changed;
	private final Window window;
	private final Actor previousFocus;
	private final Table rows = new Table();
	private final Label status;

	public static void show(Stage stage, UiKit kit, SkeletonProject project, BiConsumer<Integer, Integer> move,
		Runnable changed) {
		if (stage == null || project.size() == 0) return;
		new ProjectSkeletonDialog(stage, kit, project, move, changed).open();
	}

	private ProjectSkeletonDialog(Stage stage, UiKit kit, SkeletonProject project,
		BiConsumer<Integer, Integer> move, Runnable changed) {
		this.stage = stage;
		this.kit = kit;
		this.project = project;
		this.move = move;
		this.changed = changed;
		previousFocus = stage.getKeyboardFocus();
		window = kit.window(I18n.t("管理骨架", "Manage Skeletons"));
		window.setModal(true);
		kit.padWindow(window, 12f);
		ScrollPane scroll = new ScrollPane(rows, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		scroll.setScrollingDisabled(true, false);
		window.add(scroll).width(490f).height(Math.min(340f, Math.max(90f, project.size() * 36f))).row();
		status = kit.controlLabel("");
		status.setWrap(true);
		window.add(status).growX().minWidth(0f).height(42f).left().padTop(6f).row();
		window.add(button(I18n.t("关闭", "Close"), this::close)).width(80f).height(28f).right();
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (keycode != Input.Keys.ESCAPE) return false;
				close();
				return true;
			}
		});
		rebuildRows();
	}

	private void rebuildRows() {
		rows.clearChildren();
		for (int index = 0; index < project.size(); index++) {
			int rowIndex = index;
			TextField name = new TextField(project.nameAt(index), kit.textFieldStyle);
			Runnable rename = () -> {
				try {
					if (project.rename(rowIndex, name.getText())) changed.run();
					name.setText(project.nameAt(rowIndex));
					status.setText(I18n.t("名称已应用", "Name applied"));
				} catch (IllegalArgumentException error) { status.setText(error.getMessage()); }
			};
			name.setTextFieldListener((field, typed) -> { if (typed == '\r' || typed == '\n') rename.run(); });
			rows.add(kit.controlLabel(Integer.toString(index + 1))).width(28f).left();
			rows.add(name).growX().minWidth(0f).height(29f).padRight(6f);
			rows.add(button(I18n.t("重命名", "Rename"), rename)).width(70f).height(28f).padRight(6f);
			TextButton up = button("^", () -> reorder(rowIndex, rowIndex - 1));
			TextButton down = button("v", () -> reorder(rowIndex, rowIndex + 1));
			up.setDisabled(index == 0);
			down.setDisabled(index == project.size() - 1);
			rows.add(up).width(28f).height(28f).padRight(4f);
			rows.add(down).width(28f).height(28f).padBottom(5f).row();
		}
	}

	private void reorder(int from, int to) {
		move.accept(from, to);
		changed.run();
		stage.setKeyboardFocus(window);
		rebuildRows();
		status.setText(I18n.t("顺序已应用", "Order applied"));
	}

	private TextButton button(String text, Runnable action) {
		TextButton button = UiKit.asActionButton(new TextButton(text, kit.toolButton));
		button.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
		});
		return button;
	}

	private void open() {
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f, (stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(window);
	}
	private void close() { UiDialogs.closeModal(stage, window, previousFocus); }
}
