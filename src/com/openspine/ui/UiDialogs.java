package com.openspine.ui;

import java.util.function.Predicate;

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
import com.badlogic.gdx.utils.Align;

/** Shared modal dialogs with consistent keyboard behavior. */
public final class UiDialogs {
	static final float DIALOG_MAX_CONTENT_WIDTH = 420f;
	static final float DIALOG_STAGE_MARGIN = 24f;
	static final float DIALOG_HORIZONTAL_PADDING = 18f;
	static final float DIALOG_TOP_PADDING = UiKit.DIALOG_TITLE_BAR_HEIGHT;
	static final float DIALOG_BOTTOM_PADDING = 14f;
	static final float DIALOG_BUTTON_WIDTH = 84f;
	static final float DIALOG_BUTTON_HEIGHT = 30f;
	static final float DIALOG_ACTION_GAP = 8f;
	private static final float DIALOG_BODY_BOTTOM_GAP = 16f;
	private static final float DIALOG_FIELD_LABEL_GAP = 6f;
	private static final float DIALOG_FIELD_BOTTOM_GAP = 16f;

	public enum KeyAction { NONE, CONFIRM, CANCEL }
	public enum Tone { INFO, ERROR, DANGER }

	public static KeyAction keyAction(int keycode) {
		if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) return KeyAction.CONFIRM;
		return keycode == Input.Keys.ESCAPE ? KeyAction.CANCEL : KeyAction.NONE;
	}

	static String normalizedName(String value) {
		if (value == null) return null;
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	static float dialogContentWidth(float stageWidth) {
		if (!Float.isFinite(stageWidth) || stageWidth <= 0f) return DIALOG_MAX_CONTENT_WIDTH;
		return Math.min(DIALOG_MAX_CONTENT_WIDTH, Math.max(1f, stageWidth - DIALOG_STAGE_MARGIN * 2f));
	}

	/** Returns whether the actor is the ancestor itself or one of its descendants. */
	public static boolean isWithin(Actor actor, Actor ancestor) {
		if (actor == null || ancestor == null) return false;
		for (Actor current = actor; current != null; current = current.getParent())
			if (current == ancestor) return true;
		return false;
	}

	/** Removes a modal and restores the prior focus only when this modal still owns keyboard focus. */
	public static void closeModal(Stage stage, Window window, Actor previousKeyboardFocus) {
		if (window == null) return;
		Actor currentFocus = stage == null ? null : stage.getKeyboardFocus();
		boolean restore = currentFocus == null || isWithin(currentFocus, window);
		window.remove();
		if (restore && stage != null) {
			Actor target = previousKeyboardFocus != null && previousKeyboardFocus.getStage() == stage
				? previousKeyboardFocus : null;
			stage.setKeyboardFocus(target);
		}
	}

	public static void confirm(Stage stage, UiKit kit, String title, String message, String confirmText,
		Runnable onConfirm) {
		confirm(stage, kit, title, message, confirmText, onConfirm, () -> { });
	}

	/** Shows a destructive confirmation and invokes exactly one of the two outcomes. */
	public static void confirm(Stage stage, UiKit kit, String title, String message, String confirmText,
		Runnable onConfirm, Runnable onCancel) {
		if (stage == null || kit == null || onConfirm == null) return;
		if (onCancel == null) onCancel = () -> { };
		final Runnable cancelAction = onCancel;
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = dialogWindow(kit, I18n.t(title), Tone.DANGER);
		Label label = bodyLabel(kit, I18n.t(message));
		TextButton confirm = new TextButton(I18n.t(confirmText), kit.dangerButton);
		TextButton cancel = new TextButton(I18n.t("取消", "Cancel"), kit.toolButton);
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			closeModal(stage, window, previousKeyboardFocus);
		};
		Runnable accept = () -> {
			if (closed[0]) return;
			close.run();
			onConfirm.run();
		};
		Runnable reject = () -> {
			if (closed[0]) return;
			close.run();
			cancelAction.run();
		};
		confirm.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { accept.run(); }
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { reject.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (keyAction(keycode)) {
				case CONFIRM -> { accept.run(); yield true; }
				case CANCEL -> { reject.run(); yield true; }
				case NONE -> false;
				};
			}
		});
		float contentWidth = dialogContentWidth(stage.getWidth());
		window.add(label).width(contentWidth).left().padBottom(DIALOG_BODY_BOTTOM_GAP).row();
		Table buttons = new Table();
		buttons.add(cancel).width(DIALOG_BUTTON_WIDTH).height(DIALOG_BUTTON_HEIGHT)
			.padRight(DIALOG_ACTION_GAP);
		buttons.add(confirm).width(DIALOG_BUTTON_WIDTH).height(DIALOG_BUTTON_HEIGHT);
		window.add(buttons).right();
		showCentered(stage, window, window);
	}

	static void promptName(Stage stage, UiKit kit, String title, String initialValue, int maxLength,
		Predicate<String> onAccept) {
		if (stage == null || kit == null || onAccept == null) return;
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = dialogWindow(kit, I18n.t(title), Tone.INFO);
		Label fieldLabel = bodyLabel(kit, I18n.t("名称", "Name"));
		TextField field = new TextField(initialValue == null ? "" : initialValue, kit.textFieldStyle);
		if (maxLength > 0) field.setMaxLength(maxLength);
		TextButton apply = new TextButton(I18n.t("确定", "OK"), kit.accentButton);
		TextButton cancel = new TextButton(I18n.t("取消", "Cancel"), kit.toolButton);
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			closeModal(stage, window, previousKeyboardFocus);
		};
		Runnable submit = () -> {
			if (closed[0]) return;
			String name = normalizedName(field.getText());
			if (name != null && onAccept.test(name)) close.run();
		};
		field.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				apply.setDisabled(normalizedName(field.getText()) == null);
			}
		});
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!apply.isDisabled()) submit.run();
			}
		});
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (keyAction(keycode)) {
				case CONFIRM -> { if (!apply.isDisabled()) submit.run(); yield true; }
				case CANCEL -> { close.run(); yield true; }
				case NONE -> false;
				};
			}
		});
		apply.setDisabled(normalizedName(field.getText()) == null);
		float contentWidth = dialogContentWidth(stage.getWidth());
		window.add(fieldLabel).width(contentWidth).left().padBottom(DIALOG_FIELD_LABEL_GAP).row();
		window.add(field).width(contentWidth).height(30f).padBottom(DIALOG_FIELD_BOTTOM_GAP).row();
		Table buttons = new Table();
		buttons.add(cancel).width(DIALOG_BUTTON_WIDTH).height(DIALOG_BUTTON_HEIGHT)
			.padRight(DIALOG_ACTION_GAP);
		buttons.add(apply).width(DIALOG_BUTTON_WIDTH).height(DIALOG_BUTTON_HEIGHT);
		window.add(buttons).right();
		showCentered(stage, window, field);
		field.selectAll();
	}

	public static void message(Stage stage, UiKit kit, String title, String message, Tone tone) {
		if (stage == null || kit == null) return;
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = dialogWindow(kit, I18n.t(title), tone == null ? Tone.INFO : tone);
		Label label = bodyLabel(kit, I18n.t(message));
		boolean[] closed = {false};
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			closeModal(stage, window, previousKeyboardFocus);
		};
		TextButton ok = new TextButton(I18n.t("确定", "OK"), kit.accentButton);
		ok.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close.run(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (keyAction(keycode)) {
				case CONFIRM, CANCEL -> { close.run(); yield true; }
				case NONE -> false;
				};
			}
		});
		window.add(label).width(dialogContentWidth(stage.getWidth())).left()
			.padBottom(DIALOG_BODY_BOTTOM_GAP).row();
		window.add(ok).width(DIALOG_BUTTON_WIDTH).height(DIALOG_BUTTON_HEIGHT).right();
		showCentered(stage, window, window);
	}

	public static void unsavedChanges(Stage stage, UiKit kit, String title, String message, String discardText,
		Runnable onSave, Runnable onDiscard, Runnable onCancel) {
		if (stage == null || kit == null || onSave == null || onDiscard == null || onCancel == null) return;
		Actor previousKeyboardFocus = stage.getKeyboardFocus();
		Window window = dialogWindow(kit, I18n.t(title), Tone.DANGER);
		Label label = bodyLabel(kit, I18n.t(message));
		boolean[] resolved = {false};
		java.util.function.Consumer<Runnable> resolve = action -> {
			if (resolved[0]) return;
			resolved[0] = true;
			closeModal(stage, window, previousKeyboardFocus);
			action.run();
		};
		TextButton discard = actionButton(I18n.t(discardText), kit.dangerButton, () -> resolve.accept(onDiscard));
		TextButton cancel = actionButton(I18n.t("取消", "Cancel"), kit.toolButton, () -> resolve.accept(onCancel));
		TextButton save = actionButton(I18n.t("保存", "Save"), kit.accentButton, () -> resolve.accept(onSave));
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (keyAction(keycode)) {
				case CONFIRM -> { resolve.accept(onSave); yield true; }
				case CANCEL -> { resolve.accept(onCancel); yield true; }
				case NONE -> false;
				};
			}
		});
		window.add(label).width(dialogContentWidth(stage.getWidth())).left()
			.padBottom(DIALOG_BODY_BOTTOM_GAP).row();
		Table buttons = new Table();
		float discardWidth = Math.max(DIALOG_BUTTON_WIDTH, discard.getPrefWidth() + 16f);
		buttons.add(discard).width(discardWidth).height(DIALOG_BUTTON_HEIGHT).padRight(DIALOG_ACTION_GAP);
		buttons.add(cancel).width(DIALOG_BUTTON_WIDTH).height(DIALOG_BUTTON_HEIGHT).padRight(DIALOG_ACTION_GAP);
		buttons.add(save).width(DIALOG_BUTTON_WIDTH).height(DIALOG_BUTTON_HEIGHT);
		window.add(buttons).right();
		showCentered(stage, window, window);
	}

	private static Window dialogWindow(UiKit kit, String title, Tone tone) {
		Window window = kit.window(title == null ? "" : title);
		window.setModal(true);
		window.padTop(DIALOG_TOP_PADDING);
		window.padLeft(DIALOG_HORIZONTAL_PADDING);
		window.padRight(DIALOG_HORIZONTAL_PADDING);
		window.padBottom(DIALOG_BOTTOM_PADDING);
		window.getTitleLabel().setFontScale(UiKit.DIALOG_TITLE_SCALE);
		window.getTitleLabel().setColor(switch (tone) {
		case ERROR -> 1f;
		case DANGER -> 1f;
		case INFO -> 0.94f;
		}, switch (tone) {
		case ERROR, DANGER -> 0.48f;
		case INFO -> 0.95f;
		}, switch (tone) {
		case ERROR, DANGER -> 0.44f;
		case INFO -> 0.98f;
		}, 1f);
		return window;
	}

	private static Label bodyLabel(UiKit kit, String message) {
		Label label = new Label(message == null ? "" : message, kit.dialogBodyStyle);
		label.setWrap(true);
		label.setAlignment(Align.topLeft);
		return label;
	}

	private static TextButton actionButton(String text, com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle style,
		Runnable action) {
		TextButton button = new TextButton(text == null ? "" : text, style);
		button.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
		});
		return button;
	}

	private static void showCentered(Stage stage, Window window, Actor keyboardFocus) {
		window.pack();
		window.setPosition(Math.max(0f, (stage.getWidth() - window.getWidth()) / 2f),
			Math.max(0f, (stage.getHeight() - window.getHeight()) / 2f));
		stage.addActor(window);
		stage.setKeyboardFocus(keyboardFocus);
	}

	private UiDialogs() {}
}
