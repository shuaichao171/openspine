package com.openspine.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;

/**
 * 轻量弹出菜单：在给定屏幕坐标显示选项菜单，点击菜单项或外部自动关闭。
 * 每个实例只显示一次（隐藏后自行移除监听器）。
 */
public class PopupMenu extends Table {
	private final UiKit kit;
	private final Stage stage;
	private final InputListener dismissListener;
	private final Array<MenuItem> items = new Array<>();
	private final float anchorX, anchorY;
	private final Actor previousKeyboardFocus;
	private Runnable onClose;
	private Runnable onMouseEnter;
	private Runnable onMouseExit;
	private boolean dismissed;
	private int selectedIndex = -1;

	enum KeyAction { NONE, PREVIOUS, NEXT, ACTIVATE, CANCEL }

	static KeyAction keyAction(int keycode) {
		if (keycode == Input.Keys.UP) return KeyAction.PREVIOUS;
		if (keycode == Input.Keys.DOWN) return KeyAction.NEXT;
		if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) return KeyAction.ACTIVATE;
		return keycode == Input.Keys.ESCAPE ? KeyAction.CANCEL : KeyAction.NONE;
	}

	static int moveSelection(int current, int delta, int count) {
		if (count <= 0) return -1;
		if (current < 0 || current >= count) return delta < 0 ? count - 1 : 0;
		return Math.floorMod(current + delta, count);
	}

	public PopupMenu(UiKit kit, float screenX, float screenY, Stage stage) {
		this.kit = kit;
		this.stage = stage;
		this.anchorX = screenX;
		this.anchorY = screenY;
		this.previousKeyboardFocus = stage.getKeyboardFocus();
		setBackground(kit.downDark);
		setTouchable(Touchable.enabled);
		pad(4);

		dismissListener = new InputListener() {
			@Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				Actor target = event.getTarget();
				if (!isDescendant(target) && !isSelf(target)) {
					dismiss();
					event.stop();
				}
				return false;
			}
		};
		addListener(new InputListener() {
			@Override public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
				if (pointer == -1 && onMouseEnter != null) onMouseEnter.run();
			}

			@Override public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
				if (pointer == -1 && onMouseExit != null) onMouseExit.run();
			}

			@Override public boolean keyDown(InputEvent event, int keycode) {
				switch (keyAction(keycode)) {
				case PREVIOUS: selectIndex(moveSelection(selectedIndex, -1, items.size)); break;
				case NEXT: selectIndex(moveSelection(selectedIndex, 1, items.size)); break;
				case ACTIVATE: activateSelected(); break;
				case CANCEL: dismiss(); break;
				case NONE: return false;
				}
				return true;
			}
		});
		stage.addCaptureListener(dismissListener);
		stage.addActor(this);
		stage.setKeyboardFocus(this);
		pack();
		reposition();
	}

	/** 添加菜单项并返回，可在其上挂回调 */
	public MenuItem addItem(String text) {
		return addItem(text, false);
	}

	/** 添加可保持高亮状态的菜单项，用于表示已启用的开关。 */
	public MenuItem addItem(String text, boolean active) {
		int index = items.size;
		MenuItem item = new MenuItem(text, active, kit, this::dismiss, () -> selectIndex(index));
		items.add(item);
		if (selectedIndex < 0) selectIndex(0);
		row();
		add(item).growX().left().pad(1);
		pack();
		reposition();
		return item;
	}

	public void addSeparator() {
		Table sep = new Table();
		sep.setBackground(kit.hoverDark);
		row();
		add(sep).growX().height(1).pad(3, 0, 3, 0);
		pack();
		reposition();
	}

	/** 菜单项点击统一回调入口（各 item 命中时触发） */
	public PopupMenu onItemClick(java.util.function.Consumer<String> action) {
		for (MenuItem item : items) {
			item.onClick = () -> {
				action.accept(item.getLabel());
			};
		}
		return this;
	}

	public void setOnClose(Runnable onClose) {
		this.onClose = onClose;
	}

	public void setOnMouseHover(Runnable onEnter, Runnable onExit) {
		onMouseEnter = onEnter;
		onMouseExit = onExit;
	}

	/** 关闭并移除监听器与自身 */
	public void dismiss() {
		if (dismissed) return;
		dismissed = true;
		boolean restoreFocus = stage.getKeyboardFocus() == this;
		stage.removeCaptureListener(dismissListener);
		remove();
		if (restoreFocus) {
			stage.setKeyboardFocus(previousKeyboardFocus != null && previousKeyboardFocus.getStage() == stage
				? previousKeyboardFocus : null);
		}
		if (onClose != null) onClose.run();
	}

	private void selectIndex(int index) {
		if (index < 0 || index >= items.size) return;
		selectedIndex = index;
		for (int i = 0; i < items.size; i++) items.get(i).setHighlighted(i == selectedIndex);
	}

	private void activateSelected() {
		if (selectedIndex >= 0 && selectedIndex < items.size) items.get(selectedIndex).activate();
	}

	private boolean isSelf(Actor a) {
		return a == this;
	}

	private boolean isDescendant(Actor a) {
		for (Actor it = a; it != null; it = it.getParent())
			if (it == this) return true;
		return false;
	}

	private void reposition() {
		setPosition(clampX(anchorX), clampY(anchorY));
	}

	private float clampX(float x) {
		float w = getPrefWidth();
		return Math.max(4f, Math.min(x, Math.max(4f, stage.getWidth() - w - 4f)));
	}

	private float clampY(float y) {
		float h = getPrefHeight();
		return Math.max(4f, Math.min(y - h, Math.max(4f, stage.getHeight() - h - 4f)));
	}

	/** 单个菜单项 */
	public static class MenuItem extends Table {
		enum Tone { NORMAL, ACTIVE, SELECTED }

		private final String label;
		private final boolean active;
		private final UiKit kit;
		private final Runnable dismiss;
		private Runnable onClick;

		public MenuItem(String text, boolean active, UiKit kit, Runnable dismiss, Runnable onHover) {
			this.label = text;
			this.active = active;
			this.kit = kit;
			this.dismiss = dismiss;
			setTouchable(Touchable.enabled);
			Label l = new Label(text, kit.labelStyle);
			l.setColor(0.85f, 0.87f, 0.92f, 1f);
			add(l).left().pad(4, 8, 4, 8);
			setHighlighted(false);
			addListener(new ClickListener() {
				@Override
				public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
					onHover.run();
				}
				@Override
				public boolean mouseMoved(InputEvent event, float x, float y) {
					// Keep the item highlight in sync even when the pointer moves between
					// child actors inside the menu row.
					onHover.run();
					return true;
				}
				@Override
				public void clicked(InputEvent event, float x, float y) {
					activate();
				}
			});
		}

		void setHighlighted(boolean highlighted) {
			setBackground(switch (tone(active, highlighted)) {
			case SELECTED -> kit.accent;
			case ACTIVE -> kit.highlight;
			case NORMAL -> kit.downDark;
			});
		}

		static Tone tone(boolean active, boolean highlighted) {
			return highlighted ? Tone.SELECTED : active ? Tone.ACTIVE : Tone.NORMAL;
		}

		void activate() {
			if (onClick == null) return;
			Runnable action = onClick;
			dismiss.run();
			action.run();
		}

		public String getLabel() {
			return label;
		}

		public void setOnClick(Runnable r) {
			this.onClick = r;
		}
	}
}
