package com.openspine.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.EditHistory;
import com.esotericsoftware.spine.Skin;

/** Edits ordered preview layers as a draft, keeping authoring skins independent. */
final class SkinCombinationDialog {
	private final Stage stage;
	private final UiKit kit;
	private final SkeletonDocument document;
	private final SkeletonEditor editor;
	private final EditHistory history;
	private final Runnable changed;
	private final Actor previousFocus;
	private final Window window;
	private final Table rows = new Table();
	private final List<String> selected;
	private final Label target;
	private final TextButton apply;
	private final TextButton save;
	private final TextField name;
	private final Label error;
	private final Cell<ScrollPane> skinListCell;
	private final float preferredListHeight;

	static void show(Stage stage, UiKit kit, SkeletonDocument document, Runnable changed) {
		show(stage, kit, document, null, changed);
	}

	static void show(Stage stage, UiKit kit, SkeletonDocument document, EditHistory history, Runnable changed) {
		if (stage == null || document.data == null || document.data.getSkins().isEmpty()) return;
		new SkinCombinationDialog(stage, kit, document, history, changed).open();
	}

	private SkinCombinationDialog(Stage stage, UiKit kit, SkeletonDocument document, EditHistory history, Runnable changed) {
		this.stage = stage;
		this.kit = kit;
		this.document = document;
		this.editor = new SkeletonEditor(document);
		this.history = history;
		this.changed = changed;
		previousFocus = stage.getKeyboardFocus();
		selected = new ArrayList<>(document.getPreviewSkinNames());
		window = kit.window(I18n.t("皮肤组合", "Skin Composition"));
		window.setModal(true);
		kit.padWindow(window, 12f);
		Table header = new Table();
		header.add(kit.controlLabel(I18n.t("预览皮肤", "Preview Skins"))).growX().left();
		header.add(kit.controlLabel(I18n.t("覆盖顺序", "Layer Order"))).width(114f).right();
		window.add(header).growX().padBottom(6f).row();
		ScrollPane scroll = new ScrollPane(rows, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		scroll.setScrollingDisabled(true, false);
		preferredListHeight = Math.min(350f, Math.max(100f, document.data.getSkins().size * 33f));
		skinListCell = window.add(scroll).width(440f).height(preferredListHeight);
		window.row();
		target = kit.controlLabel("");
		target.setEllipsis(true);
		window.add(target).growX().minWidth(0f).left().padTop(8f).row();
		Label explanation = new Label(I18n.t("后面的皮肤覆盖前面的同名附件。保存后生成独立皮肤，并复制附件动画。",
			"Later skins override matching attachments. Saving creates an independent skin and copies attachment animation."), kit.dialogBodyStyle);
		explanation.setWrap(true);
		window.add(explanation).width(440f).left().padTop(7f).row();
		Table permanent = new Table();
		permanent.add(kit.controlLabel(I18n.t("新皮肤名称", "New Skin"))).left().padRight(8f);
		name = new TextField("", kit.textFieldStyle);
		name.setMessageText(I18n.t("输入名称以保存组合", "Name the permanent composition"));
		permanent.add(name).growX().minWidth(0f).height(28f);
		window.add(permanent).growX().padTop(9f).row();
		error = new Label("", kit.errorLabelStyle);
		error.setWrap(true);
		window.add(error).width(440f).minHeight(32f).left().padTop(5f).row();
		Table actions = new Table();
		TextButton cancel = button(I18n.t("取消", "Cancel"), this::close);
		save = button(I18n.t("保存为新皮肤", "Save as New Skin"), this::savePermanent);
		apply = new TextButton(I18n.t("应用", "Apply"), kit.accentButton);
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit(); }
		});
		actions.add(save).width(146f).height(28f).padRight(20f);
		actions.add(cancel).width(80f).height(28f).padRight(7f);
		actions.add(apply).width(80f).height(28f);
		window.add(actions).right().padTop(10f);
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (keycode == Input.Keys.ESCAPE) { close(); return true; }
				if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
					if (stage.getKeyboardFocus() == name) savePermanent(); else submit();
					return true;
				}
				return false;
			}
		});
		name.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { refreshSaveState(); }
		});
		rebuildRows();
	}

	private void rebuildRows() {
		rows.clearChildren();
		List<String> names = new ArrayList<>(selected);
		for (var skin : document.data.getSkins()) if (!names.contains(skin.getName())) names.add(skin.getName());
		for (String name : names) {
			int order = selected.indexOf(name);
			TextButton toggle = button(name, () -> {
				if (!selected.remove(name)) selected.add(name);
				rebuildRows();
			});
			toggle.setStyle(order >= 0 ? kit.activeToolButton : kit.toolButton);
			toggle.getLabel().setEllipsis(true);
			toggle.getLabelCell().minWidth(0f);
			rows.add(toggle).growX().minWidth(0f).height(29f).padBottom(4f);
			rows.add(kit.controlLabel(order < 0 ? "" : Integer.toString(order + 1))).width(32f);
			TextButton up = button("^", () -> move(name, -1));
			TextButton down = button("v", () -> move(name, 1));
			up.setDisabled(order <= 0);
			down.setDisabled(order < 0 || order >= selected.size() - 1);
			up.addListener(new TextTooltip(I18n.t("前移皮肤", "Move Skin Earlier"),
				new TextTooltip.TextTooltipStyle(kit.labelStyle, kit.panelBg)));
			down.addListener(new TextTooltip(I18n.t("后移皮肤", "Move Skin Later"),
				new TextTooltip.TextTooltipStyle(kit.labelStyle, kit.panelBg)));
			rows.add(up).width(30f).height(27f).padRight(4f);
			rows.add(down).width(30f).height(27f).row();
		}
		apply.setDisabled(selected.isEmpty());
		target.setText(I18n.t("编辑皮肤：", "Editing Skin: ")
			+ (selected.isEmpty() ? "" : selected.get(selected.size() - 1)));
		refreshSaveState();
	}

	private void refreshSaveState() {
		String reason = editor.getSkinCompositionBlockReason(selected, name.getText());
		save.setDisabled(history == null || reason != null);
		error.setText(name.getText().isBlank() ? "" : reason == null ? "" : reason);
		reflow();
	}

	private void savePermanent() {
		if (save.isDisabled()) return;
		try {
			savePermanent(document, history, selected, name.getText());
			close();
			if (changed != null) changed.run();
		} catch (IllegalArgumentException | IllegalStateException failure) {
			error.setText(failure.getMessage());
			reflow();
		}
	}

	static Skin savePermanent(SkeletonDocument document, EditHistory history, List<String> selected, String name) {
		if (history == null) throw new IllegalArgumentException("保存皮肤组合需要撤销历史");
		SkeletonEditor.SkinCompositionEdit edit = new SkeletonEditor(document).createSkinComposition(List.copyOf(selected), name);
		history.record(I18n.t("保存皮肤组合", "Save Skin Composition"), edit::undo, edit::redo);
		return edit.skin();
	}

	private TextButton button(String text, Runnable action) {
		TextButton button = UiKit.asActionButton(new TextButton(text, kit.toolButton));
		button.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
		});
		return button;
	}

	private void move(String name, int direction) {
		int index = selected.indexOf(name), next = index + direction;
		if (index < 0 || next < 0 || next >= selected.size()) return;
		java.util.Collections.swap(selected, index, next);
		rebuildRows();
	}

	private void submit() {
		if (!document.setPreviewSkins(selected)) return;
		close();
		if (changed != null) changed.run();
	}

	private void open() {
		reflow();
		stage.addActor(window);
		stage.setKeyboardFocus(window);
	}

	/** A wrapped failure message can grow after opening; preserve room for its action buttons. */
	private void reflow() {
		skinListCell.height(preferredListHeight);
		window.invalidateHierarchy();
		window.pack();
		float overflow = window.getHeight() - Math.max(100f, stage.getHeight() - 28f);
		if (overflow > 0f) {
			skinListCell.height(Math.max(24f, preferredListHeight - overflow));
			window.pack();
		}
		window.setPosition(Math.max(0f, (stage.getWidth() - window.getWidth()) / 2f),
			Math.max(0f, (stage.getHeight() - window.getHeight()) / 2f));
	}

	private void close() { UiDialogs.closeModal(stage, window, previousFocus); }
}
