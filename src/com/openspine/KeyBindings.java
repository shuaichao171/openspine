package com.openspine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.Function;

import com.badlogic.gdx.Input;
import com.openspine.ui.I18n;

/** Configurable global editor shortcuts. Bindings map one {@link Combo} to each {@link EditorAction};
 * contextual keys used by text fields, trees, and the timeline are reserved and cannot be bound. */
public final class KeyBindings {
	/** A key combination: a primary keycode plus Ctrl/Shift modifier requirements. */
	public record Combo(int keycode, boolean ctrl, boolean shift) {
		private static final String CTRL_FLAG = "1";

		/** Human-readable form such as {@code Ctrl+Shift+S}; key names use libGDX's English names. */
		public String display() {
			StringBuilder text = new StringBuilder();
			if (ctrl) text.append("Ctrl+");
			if (shift) text.append("Shift+");
			String name = Input.Keys.toString(keycode);
			text.append(name == null || name.isBlank() ? "#" + keycode : name);
			return text.toString();
		}

		/** Stable storage form {@code ctrl,shift,keycode}, e.g. {@code 1,0,47}. */
		public String encode() {
			return (ctrl ? CTRL_FLAG : "0") + "," + (shift ? CTRL_FLAG : "0") + "," + keycode;
		}

		/** Parses {@link #encode} output; malformed or out-of-range values return null. */
		public static Combo decode(String stored) {
			if (stored == null) return null;
			String[] parts = stored.trim().split(",");
			if (parts.length != 3) return null;
			boolean ctrl, shift;
			int keycode;
			if (parts[0].equals(CTRL_FLAG)) ctrl = true;
			else if (parts[0].equals("0")) ctrl = false;
			else return null;
			if (parts[1].equals(CTRL_FLAG)) shift = true;
			else if (parts[1].equals("0")) shift = false;
			else return null;
			try {
				keycode = Integer.parseInt(parts[2]);
			} catch (NumberFormatException malformed) {
				return null;
			}
			if (keycode <= Input.Keys.UNKNOWN || keycode > Input.Keys.MAX_KEYCODE) return null;
			return new Combo(keycode, ctrl, shift);
		}
	}

	/** Settings-page grouping for actions. */
	public enum Category {
		FILE("文件", "File"), EDIT("编辑", "Edit"), TOOL("工具", "Tools"),
		VIEW("视图", "View"), PLAYBACK("播放", "Playback");

		private final String chinese;
		private final String english;

		Category(String chinese, String english) {
			this.chinese = chinese;
			this.english = english;
		}

		public String label() {
			return I18n.t(chinese, english);
		}
	}

	/** Rebindable global editor actions with their default combos. */
	public enum EditorAction {
		OPEN(Category.FILE, "打开", "Open", ctrl(Input.Keys.O)),
		SAVE(Category.FILE, "保存", "Save", ctrl(Input.Keys.S)),
		SAVE_AS(Category.FILE, "另存为", "Save As", ctrlShift(Input.Keys.S)),
		RELOAD_IMAGES(Category.FILE, "重新加载图片", "Reload Images", ctrl(Input.Keys.R)),
		UNDO(Category.EDIT, "撤销", "Undo", ctrl(Input.Keys.Z)),
		REDO(Category.EDIT, "重做", "Redo", ctrl(Input.Keys.Y)),
		RECORD_POSE(Category.EDIT, "记录选中骨骼姿态", "Key Selected Pose", plain(Input.Keys.K)),
		TOOL_POSE(Category.TOOL, "姿态模式", "Pose Mode", plain(Input.Keys.B)),
		TOOL_SELECT(Category.TOOL, "选择工具", "Select Tool", plain(Input.Keys.Q)),
		TOOL_MOVE(Category.TOOL, "移动工具", "Move Tool", plain(Input.Keys.V)),
		TOOL_ROTATE(Category.TOOL, "旋转工具", "Rotate Tool", plain(Input.Keys.C)),
		TOOL_SCALE(Category.TOOL, "缩放工具", "Scale Tool", plain(Input.Keys.X)),
		TOOL_SHEAR(Category.TOOL, "倾斜工具", "Shear Tool", plain(Input.Keys.Z)),
		TOOL_MESH(Category.TOOL, "顶点工具", "Vertex Tool", plain(Input.Keys.T)),
		TOOL_DEFORM(Category.TOOL, "变形工具", "Deform Tool", plain(Input.Keys.Y)),
		TOOL_WEIGHT(Category.TOOL, "权重工具", "Weight Tool", plain(Input.Keys.G)),
		FOCUS_SELECTED(Category.VIEW, "聚焦选中骨骼", "Focus Selection", plain(Input.Keys.F)),
		FRAME_ALL(Category.VIEW, "显示全部内容", "Frame All", plain(Input.Keys.HOME)),
		TOGGLE_GRID(Category.VIEW, "切换网格", "Toggle Grid", shift(Input.Keys.G)),
		TOGGLE_PIXEL_GRID(Category.VIEW, "项目像素网格", "Project Pixel Grid", shift(Input.Keys.P)),
		TOGGLE_BONES(Category.VIEW, "切换骨骼显示", "Toggle Bones", shift(Input.Keys.B)),
		TOGGLE_GHOSTING(Category.VIEW, "切换动画残影", "Toggle Ghosting", plain(Input.Keys.H)),
		TOGGLE_SELECTION_VISIBILITY(Category.VIEW, "隐藏视区选择", "Hide Viewport Selection", shift(Input.Keys.H)),
		TOGGLE_INACTIVE_BONES(Category.VIEW, "显示非活动皮肤骨骼", "Show Inactive Skin Bones", shift(Input.Keys.J)),
		TOGGLE_VIEWPORT_FLIP(Category.VIEW, "翻转视区", "Flip Viewport", shift(Input.Keys.F)),
		TOGGLE_ZOOM_LOCK(Category.VIEW, "锁定视区缩放", "Lock Viewport Zoom", shift(Input.Keys.L)),
		TOGGLE_PIXEL_SNAPPING(Category.VIEW, "切换像素吸附", "Toggle Pixel Snapping", shift(Input.Keys.I)),
		TOGGLE_CONSTRAINTS(Category.VIEW, "启用/禁用全部约束", "Enable / Disable All Constraints",
			shift(Input.Keys.C)),
		SHOW_ALL_BONES(Category.VIEW, "显示全部骨骼和槽", "Show All Bones & Slots", ctrl(Input.Keys.H)),
		PLAY_PAUSE(Category.PLAYBACK, "播放/暂停", "Play / Pause", plain(Input.Keys.SPACE)),
		STEP_BACKWARD(Category.PLAYBACK, "后退一帧", "Previous Frame", plain(Input.Keys.COMMA)),
		STEP_FORWARD(Category.PLAYBACK, "前进一帧", "Next Frame", plain(Input.Keys.PERIOD));

		public final Category category;
		private final String chinese;
		private final String english;
		public final Combo defaultCombo;

		EditorAction(Category category, String chinese, String english, Combo defaultCombo) {
			this.category = category;
			this.chinese = chinese;
			this.english = english;
			this.defaultCombo = defaultCombo;
		}

		public String label() {
			return I18n.t(chinese, english);
		}

		/** Stable preference key for this action's combo. */
		public String preferenceKey() {
			return "keys." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Keys owned by contextual editing (text fields, tree, timeline, dialogs); never bindable. */
	private static final Set<Integer> RESERVED_KEYCODES = Set.of(
		Input.Keys.ESCAPE, Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER, Input.Keys.TAB,
		Input.Keys.DEL, Input.Keys.FORWARD_DEL, Input.Keys.F2,
		Input.Keys.LEFT, Input.Keys.RIGHT, Input.Keys.UP, Input.Keys.DOWN,
		Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT,
		Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT,
		Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT);
	/** Ctrl combos owned by clipboard and selection editing. */
	private static final Set<Integer> RESERVED_CTRL_KEYCODES = Set.of(
		Input.Keys.C, Input.Keys.V, Input.Keys.X, Input.Keys.A);
	private static final Combo LEGACY_TOOL_MOVE_DEFAULT = plain(Input.Keys.W);
	private static final Combo LEGACY_TOOL_ROTATE_DEFAULT = plain(Input.Keys.E);
	private static final Combo LEGACY_TOOL_SCALE_DEFAULT = plain(Input.Keys.R);
	private static final Combo LEGACY_TOOL_WEIGHT_DEFAULT = plain(Input.Keys.U);
	private static final Combo LEGACY_TOGGLE_GRID_DEFAULT = plain(Input.Keys.G);
	private static final Combo LEGACY_TOGGLE_BONES_DEFAULT = plain(Input.Keys.B);

	private final EnumMap<EditorAction, Combo> combos = new EnumMap<>(EditorAction.class);

	public KeyBindings() {
		resetAll();
	}

	/** Restores every action to its default combo. */
	public void resetAll() {
		for (EditorAction action : EditorAction.values()) combos.put(action, action.defaultCombo);
	}

	public void reset(EditorAction action) {
		combos.put(action, action.defaultCombo);
	}

	public Combo combo(EditorAction action) {
		return combos.get(action);
	}

	public boolean isDefault(EditorAction action) {
		return action.defaultCombo.equals(combos.get(action));
	}

	/** True when the combo is owned by contextual editing and must not be bound. */
	public static boolean isReserved(Combo combo) {
		if (combo == null) return true;
		if (RESERVED_KEYCODES.contains(combo.keycode())) return true;
		return combo.ctrl() && RESERVED_CTRL_KEYCODES.contains(combo.keycode());
	}

	/** Returns the other action already using the combo, or null when the combo is free. */
	public EditorAction conflict(Combo combo, EditorAction exclude) {
		if (combo == null) return null;
		for (Map.Entry<EditorAction, Combo> entry : combos.entrySet()) {
			if (entry.getKey() != exclude && combo.equals(entry.getValue())) return entry.getKey();
		}
		return null;
	}

	/** Binds the action; reserved combos and conflicts are rejected. */
	public BindResult bind(EditorAction action, Combo combo) {
		if (action == null || combo == null) return BindResult.conflictWith(null);
		if (isReserved(combo)) return BindResult.reservedCombo();
		EditorAction conflict = conflict(combo, action);
		if (conflict != null) return BindResult.conflictWith(conflict);
		combos.put(action, combo);
		return BindResult.accepted();
	}

	/** Outcome of a bind attempt. Conflicts report the owning action for the settings page. */
	public record BindResult(boolean bound, boolean reserved, EditorAction conflict) {
		static BindResult accepted() {
			return new BindResult(true, false, null);
		}

		static BindResult reservedCombo() {
			return new BindResult(false, true, null);
		}

		static BindResult conflictWith(EditorAction conflict) {
			return new BindResult(false, false, conflict);
		}
	}

	/** Resolves a pressed key to its action. The legacy Ctrl+Shift+Z redo alias stays active while no
	 * explicit binding claims that combo. */
	public EditorAction match(int keycode, boolean ctrl, boolean shift) {
		Combo pressed = new Combo(keycode, ctrl, shift);
		for (Map.Entry<EditorAction, Combo> entry : combos.entrySet()) {
			if (pressed.equals(entry.getValue())) return entry.getKey();
		}
		if (keycode == Input.Keys.Z && ctrl && shift) return EditorAction.REDO;
		return null;
	}

	/** Only Ctrl-modified actions run while a text field owns the keyboard; undo/redo stay with the
	 * text field so typing history is never hijacked. */
	public static boolean allowedWithTextFocus(EditorAction action, Combo combo) {
		if (action == null || combo == null || !combo.ctrl()) return false;
		return action != EditorAction.UNDO && action != EditorAction.REDO;
	}

	/** Serializes every binding for preference storage, keyed by {@link EditorAction#preferenceKey}. */
	public Map<String, String> toStored() {
		Map<String, String> stored = new LinkedHashMap<>();
		for (EditorAction action : EditorAction.values())
			stored.put(action.preferenceKey(), combos.get(action).encode());
		return stored;
	}

	/** Loads bindings from preference storage. Missing, malformed, reserved, or conflicting entries
	 * keep their defaults. Starting from the complete default map guarantees a unique assignment even
	 * when preference data was edited or partially corrupted outside the application. */
	public void loadFrom(Function<String, String> storage) {
		if (storage == null) return;
		EnumMap<EditorAction, Combo> storedCombos = new EnumMap<>(EditorAction.class);
		EnumSet<EditorAction> missingActions = EnumSet.noneOf(EditorAction.class);
		for (EditorAction action : EditorAction.values()) {
			String raw = storage.apply(action.preferenceKey());
			if (raw == null || raw.isBlank()) missingActions.add(action);
			storedCombos.put(action, Combo.decode(raw));
		}
		boolean newTransformDefaultsAvailable = true;
		for (Map.Entry<EditorAction, Combo> entry : storedCombos.entrySet()) {
			if (entry.getKey() != EditorAction.TOOL_MOVE && entry.getKey() != EditorAction.TOOL_ROTATE
				&& (EditorAction.TOOL_MOVE.defaultCombo.equals(entry.getValue())
					|| EditorAction.TOOL_ROTATE.defaultCombo.equals(entry.getValue()))) {
				newTransformDefaultsAvailable = false;
				break;
			}
		}
		boolean legacyTransformDefaults = LEGACY_TOOL_MOVE_DEFAULT.equals(storedCombos.get(EditorAction.TOOL_MOVE))
			&& LEGACY_TOOL_ROTATE_DEFAULT.equals(storedCombos.get(EditorAction.TOOL_ROTATE))
			&& newTransformDefaultsAvailable;
		if (legacyTransformDefaults) {
			storedCombos.put(EditorAction.TOOL_MOVE, EditorAction.TOOL_MOVE.defaultCombo);
			storedCombos.put(EditorAction.TOOL_ROTATE, EditorAction.TOOL_ROTATE.defaultCombo);
		}
		migrateOfficialToolDefaults(storedCombos);
		installMissingDefaultsWithoutDisplacingCustomBindings(storedCombos, missingActions);
		EnumMap<EditorAction, Combo> desired = new EnumMap<>(EditorAction.class);
		for (EditorAction action : EditorAction.values()) {
			Combo stored = storedCombos.get(action);
			desired.put(action, stored != null && !isReserved(stored) ? stored : action.defaultCombo);
		}
		// Preserve valid swaps and chains. Only duplicated desired combos self-heal to defaults; repeat
		// because one fallback can expose a conflict with a different custom binding.
		boolean changed;
		do {
			changed = false;
			for (EditorAction action : EditorAction.values()) {
				Combo candidate = desired.get(action);
				int owners = 0;
				for (Combo other : desired.values()) if (candidate.equals(other)) owners++;
				if (owners > 1 && !candidate.equals(action.defaultCombo)) {
					desired.put(action, action.defaultCombo);
					changed = true;
				}
			}
		} while (changed);
		combos.clear();
		combos.putAll(desired);
	}

	private static void installMissingDefaultsWithoutDisplacingCustomBindings(
		EnumMap<EditorAction, Combo> stored, EnumSet<EditorAction> missing) {
		for (EditorAction action : missing)
			if (stored.get(action) == null) stored.put(action, action.defaultCombo);
		for (EditorAction action : missing) {
			Combo candidate = stored.get(action);
			if (candidate == null || comboOwners(stored, candidate) <= 1) continue;
			stored.put(action, null);
			stored.put(action, firstFreeFallback(stored));
		}
	}

	private static int comboOwners(EnumMap<EditorAction, Combo> stored, Combo candidate) {
		int owners = 0;
		for (Combo combo : stored.values()) if (candidate.equals(combo)) owners++;
		return owners;
	}

	private static void migrateOfficialToolDefaults(EnumMap<EditorAction, Combo> stored) {
		if (stored.get(EditorAction.TOOL_POSE) != null) return;
		EnumSet<EditorAction> migrating = EnumSet.of(EditorAction.TOOL_POSE);
		if (LEGACY_TOOL_SCALE_DEFAULT.equals(stored.get(EditorAction.TOOL_SCALE)))
			migrating.add(EditorAction.TOOL_SCALE);
		if (LEGACY_TOOL_WEIGHT_DEFAULT.equals(stored.get(EditorAction.TOOL_WEIGHT)))
			migrating.add(EditorAction.TOOL_WEIGHT);
		if (LEGACY_TOGGLE_GRID_DEFAULT.equals(stored.get(EditorAction.TOGGLE_GRID)))
			migrating.add(EditorAction.TOGGLE_GRID);
		if (LEGACY_TOGGLE_BONES_DEFAULT.equals(stored.get(EditorAction.TOGGLE_BONES)))
			migrating.add(EditorAction.TOGGLE_BONES);

		boolean changed;
		do {
			changed = false;
			for (EditorAction action : EnumSet.copyOf(migrating)) {
				Combo target = action.defaultCombo;
				for (Map.Entry<EditorAction, Combo> entry : stored.entrySet()) {
					if (entry.getKey() == action || !target.equals(entry.getValue())) continue;
					if (!migrating.contains(entry.getKey())) {
						migrating.remove(action);
						changed = true;
						break;
					}
				}
			}
		} while (changed);
		for (EditorAction action : migrating) stored.put(action, action.defaultCombo);
		if (!migrating.contains(EditorAction.TOOL_POSE))
			stored.put(EditorAction.TOOL_POSE, firstFreeFallback(stored));
	}

	private static Combo firstFreeFallback(EnumMap<EditorAction, Combo> stored) {
		Combo[] candidates = {shift(Input.Keys.P), plain(Input.Keys.P), shift(Input.Keys.O), shift(Input.Keys.U)};
		for (Combo candidate : candidates) if (!isUsed(stored, candidate)) return candidate;
		for (int keycode = Input.Keys.A; keycode <= Input.Keys.Z; keycode++) {
			Combo candidate = shift(keycode);
			if (!isReserved(candidate) && !isUsed(stored, candidate)) return candidate;
		}
		for (int keycode = Input.Keys.A; keycode <= Input.Keys.Z; keycode++) {
			Combo candidate = plain(keycode);
			if (!isReserved(candidate) && !isUsed(stored, candidate)) return candidate;
		}
		throw new IllegalStateException("No free shortcut is available for Pose mode migration");
	}

	private static boolean isUsed(EnumMap<EditorAction, Combo> stored, Combo candidate) {
		for (Combo combo : stored.values()) if (candidate.equals(combo)) return true;
		return false;
	}

	private static Combo plain(int keycode) {
		return new Combo(keycode, false, false);
	}

	private static Combo ctrl(int keycode) {
		return new Combo(keycode, true, false);
	}

	private static Combo ctrlShift(int keycode) {
		return new Combo(keycode, true, true);
	}

	private static Combo shift(int keycode) {
		return new Combo(keycode, false, true);
	}
}
