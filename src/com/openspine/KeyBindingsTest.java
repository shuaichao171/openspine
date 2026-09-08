package com.openspine;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Input;
import com.openspine.KeyBindings.BindResult;
import com.openspine.KeyBindings.Combo;
import com.openspine.KeyBindings.EditorAction;
import com.openspine.ui.I18n;
import com.openspine.ui.I18n.Language;

/** Headless regression tests for configurable editor shortcuts. */
public final class KeyBindingsTest {
	public static void main(String[] args) {
		testsComboEncoding();
		testsDefaultsAndLabels();
		testsMatching();
		testsBinding();
		testsTextFocusPolicy();
		testsStorageRoundTrip();
		testsLegacyTransformDefaultMigration();
		testsLegacyOfficialDefaultMigration();
		testsNewActionsPreserveLegacyCustomBindings();
		testsSelfHealingLoad();
		testsStoredSwapsAndConflictChains();
		System.out.println("ALL KEY BINDINGS TESTS PASSED");
	}

	private static void testsComboEncoding() {
		Combo combo = new Combo(Input.Keys.S, true, true);
		assertEquals("Ctrl+Shift+S", combo.display(), "modifier display order is Ctrl then Shift");
		assertEquals("Space", new Combo(Input.Keys.SPACE, false, false).display(), "plain keys show their name");
		assertEquals(combo, Combo.decode(combo.encode()), "encode/decode round-trips");
		assertEquals(new Combo(Input.Keys.HOME, false, false),
			Combo.decode(new Combo(Input.Keys.HOME, false, false).encode()), "plain combos round-trip");
		for (String malformed : new String[] {null, "", "1,0", "1,0,notakey", "2,0,31", "1,x,31",
			"1,0,-1", "1,0,0", "1,0,99999", "1,0,31,4"})
			assertEquals(null, Combo.decode(malformed), "malformed stored combo is rejected: " + malformed);
	}

	private static void testsDefaultsAndLabels() {
		KeyBindings bindings = new KeyBindings();
		for (EditorAction action : EditorAction.values()) {
			assertEquals(action.defaultCombo, bindings.combo(action), "defaults are installed: " + action);
			assertTrue(bindings.isDefault(action), "fresh bindings report defaults: " + action);
			assertTrue(!KeyBindings.isReserved(action.defaultCombo),
				"default combos are never reserved: " + action);
		}
		assertEquals("Ctrl+O", bindings.combo(EditorAction.OPEN).display(), "open defaults to Ctrl+O");
		assertEquals("Ctrl+Shift+S", bindings.combo(EditorAction.SAVE_AS).display(),
			"save-as defaults to Ctrl+Shift+S");
		assertEquals("V", bindings.combo(EditorAction.TOOL_MOVE).display(),
			"the move tool follows Spine's plain V default");
		assertEquals("C", bindings.combo(EditorAction.TOOL_ROTATE).display(),
			"the rotate tool follows Spine's plain C default");
		assertEquals("Z", bindings.combo(EditorAction.TOOL_SHEAR).display(),
			"the shear tool defaults to plain Z");
		assertEquals("X", bindings.combo(EditorAction.TOOL_SCALE).display(),
			"the scale tool follows Spine's plain X default");
		assertEquals("B", bindings.combo(EditorAction.TOOL_POSE).display(),
			"Pose mode follows Spine's plain B default");
		assertEquals("G", bindings.combo(EditorAction.TOOL_WEIGHT).display(),
			"the weight tool follows Spine's plain G default");
		assertEquals("Shift+G", bindings.combo(EditorAction.TOGGLE_GRID).display(),
			"the grid toggle no longer conflicts with the weight tool");
		assertEquals("Shift+B", bindings.combo(EditorAction.TOGGLE_BONES).display(),
			"the bone toggle no longer conflicts with Pose mode");
		assertEquals("Shift+F", bindings.combo(EditorAction.TOGGLE_VIEWPORT_FLIP).display(),
			"viewport flip has a non-conflicting view shortcut");
		assertEquals("Shift+L", bindings.combo(EditorAction.TOGGLE_ZOOM_LOCK).display(),
			"viewport zoom lock has a non-conflicting view shortcut");
		assertEquals("Shift+I", bindings.combo(EditorAction.TOGGLE_PIXEL_SNAPPING).display(),
			"pixel snapping has a non-conflicting view shortcut");
		assertEquals("Shift+C", bindings.combo(EditorAction.TOGGLE_CONSTRAINTS).display(),
			"the all-constraints switch has a non-conflicting view shortcut");
		I18n.setLanguage(Language.ENGLISH);
		try {
			assertEquals("Play / Pause", EditorAction.PLAY_PAUSE.label(), "action labels translate");
			assertEquals("Playback", EditorAction.PLAY_PAUSE.category.label(), "category labels translate");
		} finally {
			I18n.setLanguage(Language.CHINESE);
		}
		assertEquals("播放/暂停", EditorAction.PLAY_PAUSE.label(), "action labels return to Chinese");
		assertEquals("keys.play_pause", EditorAction.PLAY_PAUSE.preferenceKey(), "preference keys are stable");
	}

	private static void testsMatching() {
		KeyBindings bindings = new KeyBindings();
		assertEquals(EditorAction.SAVE, bindings.match(Input.Keys.S, true, false), "Ctrl+S saves");
		assertEquals(EditorAction.SAVE_AS, bindings.match(Input.Keys.S, true, true), "Ctrl+Shift+S saves as");
		assertEquals(EditorAction.TOOL_SELECT, bindings.match(Input.Keys.Q, false, false), "Q selects");
		assertEquals(EditorAction.TOOL_MOVE, bindings.match(Input.Keys.V, false, false),
			"plain V selects the move tool");
		assertEquals(EditorAction.TOOL_ROTATE, bindings.match(Input.Keys.C, false, false),
			"plain C selects the rotate tool");
		assertEquals(EditorAction.TOOL_SHEAR, bindings.match(Input.Keys.Z, false, false),
			"plain Z selects the shear tool");
		assertEquals(EditorAction.TOOL_SCALE, bindings.match(Input.Keys.X, false, false),
			"plain X selects the scale tool");
		assertEquals(EditorAction.TOOL_POSE, bindings.match(Input.Keys.B, false, false),
			"plain B toggles Pose mode");
		assertEquals(EditorAction.TOOL_WEIGHT, bindings.match(Input.Keys.G, false, false),
			"plain G selects the weight tool");
		assertEquals(EditorAction.TOGGLE_VIEWPORT_FLIP, bindings.match(Input.Keys.F, false, true),
			"Shift+F flips the viewport");
		assertEquals(EditorAction.TOGGLE_ZOOM_LOCK, bindings.match(Input.Keys.L, false, true),
			"Shift+L locks viewport zoom");
		assertEquals(EditorAction.TOGGLE_PIXEL_SNAPPING, bindings.match(Input.Keys.I, false, true),
			"Shift+I toggles pixel snapping");
		assertEquals(EditorAction.TOGGLE_CONSTRAINTS, bindings.match(Input.Keys.C, false, true),
			"Shift+C toggles all constraints");
		assertEquals(EditorAction.TOGGLE_SELECTION_VISIBILITY, bindings.match(Input.Keys.H, false, true),
			"Shift+H hides viewport selection without claiming plain H ghosting");
		assertEquals(EditorAction.TOGGLE_INACTIVE_BONES, bindings.match(Input.Keys.J, false, true),
			"Shift+J toggles inactive skin bone references");
		assertEquals(null, bindings.match(Input.Keys.Q, true, false), "modifiers must match exactly");
		assertEquals(EditorAction.REDO, bindings.match(Input.Keys.Z, true, true),
			"legacy Ctrl+Shift+Z redo alias stays active");
		assertEquals(EditorAction.UNDO, bindings.match(Input.Keys.Z, true, false), "Ctrl+Z undoes");

		BindResult claimed = bindings.bind(EditorAction.FRAME_ALL, new Combo(Input.Keys.Z, true, true));
		assertTrue(claimed.bound(), "explicit bindings may claim the legacy alias combo");
		assertEquals(EditorAction.FRAME_ALL, bindings.match(Input.Keys.Z, true, true),
			"explicit bindings override the legacy redo alias");
	}

	private static void testsBinding() {
		KeyBindings bindings = new KeyBindings();
		BindResult moved = bindings.bind(EditorAction.TOGGLE_GRID, new Combo(Input.Keys.J, false, false));
		assertTrue(moved.bound(), "free combos bind");
		assertEquals(EditorAction.TOGGLE_GRID, bindings.match(Input.Keys.J, false, false), "new combo matches");
		assertEquals(EditorAction.TOOL_WEIGHT, bindings.match(Input.Keys.G, false, false),
			"rebinding the grid toggle does not affect the weight shortcut");
		assertTrue(!bindings.isDefault(EditorAction.TOGGLE_GRID), "rebound actions report non-default");

		BindResult conflict = bindings.bind(EditorAction.TOGGLE_BONES, new Combo(Input.Keys.J, false, false));
		assertTrue(!conflict.bound() && conflict.conflict() == EditorAction.TOGGLE_GRID,
			"conflicts are rejected and report the owning action");
		assertEquals(new Combo(Input.Keys.B, false, true), bindings.combo(EditorAction.TOGGLE_BONES),
			"rejected binds leave the previous combo");

		BindResult reserved = bindings.bind(EditorAction.TOGGLE_BONES, new Combo(Input.Keys.ESCAPE, false, false));
		assertTrue(!reserved.bound() && reserved.reserved(), "Escape is reserved");
		assertTrue(KeyBindings.isReserved(new Combo(Input.Keys.C, true, false)), "Ctrl+C is reserved");
		assertTrue(!KeyBindings.isReserved(new Combo(Input.Keys.C, false, false)), "plain C is bindable");
		assertTrue(KeyBindings.isReserved(new Combo(Input.Keys.FORWARD_DEL, false, false)), "Delete is reserved");
		assertTrue(KeyBindings.isReserved(new Combo(Input.Keys.LEFT, false, true)),
			"arrow keys are reserved with any modifiers");

		bindings.bind(EditorAction.TOGGLE_GRID, new Combo(Input.Keys.G, false, true));
		assertEquals(EditorAction.TOGGLE_GRID, bindings.match(Input.Keys.G, false, true),
			"Shift-modified combos bind and match");
		bindings.reset(EditorAction.TOGGLE_GRID);
		assertEquals(EditorAction.TOGGLE_GRID, bindings.match(Input.Keys.G, false, true),
			"per-action reset restores the default");
		bindings.resetAll();
		assertEquals(EditorAction.TOGGLE_BONES, bindings.match(Input.Keys.B, false, true),
			"reset-all restores every default");
	}

	private static void testsTextFocusPolicy() {
		assertTrue(KeyBindings.allowedWithTextFocus(EditorAction.SAVE, EditorAction.SAVE.defaultCombo),
			"Ctrl+S runs while a text field has focus");
		assertTrue(!KeyBindings.allowedWithTextFocus(EditorAction.UNDO, EditorAction.UNDO.defaultCombo),
			"Ctrl+Z stays with the focused text field");
		assertTrue(!KeyBindings.allowedWithTextFocus(EditorAction.REDO, EditorAction.REDO.defaultCombo),
			"Ctrl+Y stays with the focused text field");
		assertTrue(!KeyBindings.allowedWithTextFocus(EditorAction.TOOL_SELECT,
			EditorAction.TOOL_SELECT.defaultCombo), "plain keys never run while typing");
		assertTrue(!KeyBindings.allowedWithTextFocus(null, null), "null input is not allowed");
	}

	private static void testsStorageRoundTrip() {
		KeyBindings bindings = new KeyBindings();
		bindings.bind(EditorAction.PLAY_PAUSE, new Combo(Input.Keys.P, false, false));
		bindings.bind(EditorAction.OPEN, new Combo(Input.Keys.O, true, true));
		Map<String, String> stored = bindings.toStored();
		assertEquals(EditorAction.values().length, stored.size(), "every action is persisted");

		KeyBindings reloaded = new KeyBindings();
		reloaded.loadFrom(stored::get);
		for (EditorAction action : EditorAction.values())
			assertEquals(bindings.combo(action), reloaded.combo(action), "storage round-trips: " + action);
	}

	private static void testsLegacyTransformDefaultMigration() {
		Map<String, String> stored = new HashMap<>(new KeyBindings().toStored());
		stored.put(EditorAction.TOOL_MOVE.preferenceKey(), new Combo(Input.Keys.W, false, false).encode());
		stored.put(EditorAction.TOOL_ROTATE.preferenceKey(), new Combo(Input.Keys.E, false, false).encode());
		KeyBindings migrated = new KeyBindings();
		migrated.loadFrom(stored::get);
		assertEquals(EditorAction.TOOL_MOVE.defaultCombo, migrated.combo(EditorAction.TOOL_MOVE),
			"the former W move default migrates to Spine's V default");
		assertEquals(EditorAction.TOOL_ROTATE.defaultCombo, migrated.combo(EditorAction.TOOL_ROTATE),
			"the former E rotate default migrates to Spine's C default");

		stored.put(EditorAction.TOOL_ROTATE.preferenceKey(), new Combo(Input.Keys.J, false, false).encode());
		KeyBindings customized = new KeyBindings();
		customized.loadFrom(stored::get);
		assertEquals(new Combo(Input.Keys.W, false, false), customized.combo(EditorAction.TOOL_MOVE),
			"an old-looking move key is preserved when the transform pair was customized");
		assertEquals(new Combo(Input.Keys.J, false, false), customized.combo(EditorAction.TOOL_ROTATE),
			"custom rotate bindings are never replaced by migration");

		stored.put(EditorAction.TOOL_ROTATE.preferenceKey(), new Combo(Input.Keys.E, false, false).encode());
		stored.put(EditorAction.TOOL_SELECT.preferenceKey(), new Combo(Input.Keys.C, false, false).encode());
		KeyBindings occupied = new KeyBindings();
		occupied.loadFrom(stored::get);
		assertEquals(new Combo(Input.Keys.W, false, false), occupied.combo(EditorAction.TOOL_MOVE),
			"legacy defaults stay intact when a new default is already customized");
		assertEquals(new Combo(Input.Keys.E, false, false), occupied.combo(EditorAction.TOOL_ROTATE),
			"migration never displaces a custom owner of C or V");
		assertEquals(new Combo(Input.Keys.C, false, false), occupied.combo(EditorAction.TOOL_SELECT),
			"the existing custom owner of a new default is preserved");
	}

	private static void testsLegacyOfficialDefaultMigration() {
		Map<String, String> stored = new HashMap<>(new KeyBindings().toStored());
		stored.remove(EditorAction.TOOL_POSE.preferenceKey());
		stored.remove(EditorAction.TOGGLE_PIXEL_GRID.preferenceKey());
		stored.put(EditorAction.TOOL_SCALE.preferenceKey(), new Combo(Input.Keys.R, false, false).encode());
		stored.put(EditorAction.TOOL_WEIGHT.preferenceKey(), new Combo(Input.Keys.U, false, false).encode());
		stored.put(EditorAction.TOGGLE_GRID.preferenceKey(), new Combo(Input.Keys.G, false, false).encode());
		stored.put(EditorAction.TOGGLE_BONES.preferenceKey(), new Combo(Input.Keys.B, false, false).encode());
		KeyBindings migrated = new KeyBindings();
		migrated.loadFrom(stored::get);
		assertEquals(EditorAction.TOOL_SCALE.defaultCombo, migrated.combo(EditorAction.TOOL_SCALE),
			"the former R scale default migrates to Spine's X default");
		assertEquals(EditorAction.TOOL_WEIGHT.defaultCombo, migrated.combo(EditorAction.TOOL_WEIGHT),
			"the former U weight default migrates to Spine's G default");
		assertEquals(EditorAction.TOOL_POSE.defaultCombo, migrated.combo(EditorAction.TOOL_POSE),
			"old preferences gain Spine's B Pose shortcut");
		assertEquals(EditorAction.TOGGLE_GRID.defaultCombo, migrated.combo(EditorAction.TOGGLE_GRID),
			"the old G grid toggle moves to Shift+G");
		assertEquals(EditorAction.TOGGLE_BONES.defaultCombo, migrated.combo(EditorAction.TOGGLE_BONES),
			"the old B bone toggle moves to Shift+B");

		stored.put(EditorAction.TOOL_DEFORM.preferenceKey(), new Combo(Input.Keys.X, false, false).encode());
		KeyBindings customized = new KeyBindings();
		customized.loadFrom(stored::get);
		assertEquals(new Combo(Input.Keys.R, false, false), customized.combo(EditorAction.TOOL_SCALE),
			"legacy scale stays on R when a custom action already owns X");
		assertEquals(new Combo(Input.Keys.X, false, false), customized.combo(EditorAction.TOOL_DEFORM),
			"official-default migration never displaces a custom X binding");

		stored = new HashMap<>(new KeyBindings().toStored());
		stored.remove(EditorAction.TOOL_POSE.preferenceKey());
		stored.remove(EditorAction.TOGGLE_PIXEL_GRID.preferenceKey());
		stored.put(EditorAction.TOOL_SCALE.preferenceKey(), new Combo(Input.Keys.R, false, false).encode());
		stored.put(EditorAction.TOOL_WEIGHT.preferenceKey(), new Combo(Input.Keys.U, false, false).encode());
		stored.put(EditorAction.TOGGLE_GRID.preferenceKey(), new Combo(Input.Keys.G, false, false).encode());
		stored.put(EditorAction.TOGGLE_BONES.preferenceKey(), new Combo(Input.Keys.B, false, false).encode());
		Map<EditorAction, Combo> occupiedFallbacks = Map.of(
			EditorAction.OPEN, new Combo(Input.Keys.B, false, true),
			EditorAction.SAVE, new Combo(Input.Keys.P, false, true),
			EditorAction.SAVE_AS, new Combo(Input.Keys.P, false, false),
			EditorAction.UNDO, new Combo(Input.Keys.O, false, true),
			EditorAction.REDO, new Combo(Input.Keys.U, false, true),
			EditorAction.RECORD_POSE, new Combo(Input.Keys.N, false, true));
		for (Map.Entry<EditorAction, Combo> entry : occupiedFallbacks.entrySet())
			stored.put(entry.getKey().preferenceKey(), entry.getValue().encode());
		KeyBindings crowded = new KeyBindings();
		crowded.loadFrom(stored::get);
		for (Map.Entry<EditorAction, Combo> entry : occupiedFallbacks.entrySet())
			assertEquals(entry.getValue(), crowded.combo(entry.getKey()),
				"Pose fallback migration preserves custom binding for " + entry.getKey());
		Combo poseFallback = crowded.combo(EditorAction.TOOL_POSE);
		assertTrue(!occupiedFallbacks.containsValue(poseFallback),
			"Pose migration searches beyond occupied preferred fallback combos");
		assertEquals(null, crowded.conflict(poseFallback, EditorAction.TOOL_POSE),
			"the migrated Pose fallback remains unique");
	}

	private static void testsNewActionsPreserveLegacyCustomBindings() {
		assertEquals(EditorAction.RELOAD_IMAGES, new KeyBindings().match(Input.Keys.R, true, false),
			"Ctrl+R reloads external images");
		assertEquals(EditorAction.TOGGLE_PIXEL_GRID, new KeyBindings().match(Input.Keys.P, false, true),
			"Shift+P toggles the project pixel grid");
		for (EditorAction added : new EditorAction[] {EditorAction.TOGGLE_SELECTION_VISIBILITY,
			EditorAction.TOGGLE_INACTIVE_BONES, EditorAction.TOGGLE_PIXEL_GRID, EditorAction.RELOAD_IMAGES}) {
			Map<String, String> legacy = new java.util.LinkedHashMap<>(new KeyBindings().toStored());
			legacy.remove(EditorAction.TOGGLE_SELECTION_VISIBILITY.preferenceKey());
			legacy.remove(EditorAction.TOGGLE_INACTIVE_BONES.preferenceKey());
			legacy.remove(EditorAction.TOGGLE_PIXEL_GRID.preferenceKey());
			legacy.remove(EditorAction.RELOAD_IMAGES.preferenceKey());
			legacy.put(EditorAction.SAVE.preferenceKey(), added.defaultCombo.encode());
			KeyBindings restored = new KeyBindings();
			restored.loadFrom(legacy::get);
			assertEquals(added.defaultCombo, restored.combo(EditorAction.SAVE),
				"new actions preserve an existing custom binding");
			for (EditorAction action : EditorAction.values())
				assertEquals(null, restored.conflict(restored.combo(action), action),
					"new shortcut migration remains unique: " + action);
		}
		Map<String, String> stored = new HashMap<>(new KeyBindings().toStored());
		stored.remove(EditorAction.TOGGLE_VIEWPORT_FLIP.preferenceKey());
		stored.remove(EditorAction.TOGGLE_ZOOM_LOCK.preferenceKey());
		stored.remove(EditorAction.TOGGLE_PIXEL_SNAPPING.preferenceKey());
		stored.remove(EditorAction.TOGGLE_CONSTRAINTS.preferenceKey());
		Combo customFlipDefault = EditorAction.TOGGLE_VIEWPORT_FLIP.defaultCombo;
		stored.put(EditorAction.FRAME_ALL.preferenceKey(), customFlipDefault.encode());

		KeyBindings migrated = new KeyBindings();
		migrated.loadFrom(stored::get);
		assertEquals(customFlipDefault, migrated.combo(EditorAction.FRAME_ALL),
			"installing new view actions preserves an older custom binding that uses a new default");
		assertTrue(!customFlipDefault.equals(migrated.combo(EditorAction.TOGGLE_VIEWPORT_FLIP)),
			"the newly introduced action moves to an available fallback instead of displacing the user binding");
		for (EditorAction action : EditorAction.values())
			assertEquals(null, migrated.conflict(migrated.combo(action), action),
				"new-action migration leaves every shortcut unique: " + action);
	}

	private static void testsSelfHealingLoad() {
		KeyBindings bindings = new KeyBindings();
		Map<String, String> stored = new HashMap<>(bindings.toStored());
		stored.put(EditorAction.SAVE.preferenceKey(), "garbage");
		stored.put(EditorAction.TOGGLE_GRID.preferenceKey(),
			new Combo(Input.Keys.ESCAPE, false, false).encode());
		stored.put(EditorAction.TOGGLE_BONES.preferenceKey(),
			EditorAction.TOOL_SELECT.defaultCombo.encode());
		stored.remove(EditorAction.FRAME_ALL.preferenceKey());
		stored.remove(EditorAction.TOOL_SHEAR.preferenceKey());
		bindings.loadFrom(stored::get);
		assertEquals(EditorAction.SAVE.defaultCombo, bindings.combo(EditorAction.SAVE),
			"malformed entries fall back to the default");
		assertEquals(EditorAction.TOGGLE_GRID.defaultCombo, bindings.combo(EditorAction.TOGGLE_GRID),
			"reserved entries fall back to the default");
		assertEquals(EditorAction.TOGGLE_BONES.defaultCombo, bindings.combo(EditorAction.TOGGLE_BONES),
			"stored combos claimed by an earlier action fall back to the default");
		assertEquals(EditorAction.FRAME_ALL.defaultCombo, bindings.combo(EditorAction.FRAME_ALL),
			"missing entries keep the default");
		assertEquals(EditorAction.TOOL_SHEAR.defaultCombo, bindings.combo(EditorAction.TOOL_SHEAR),
			"legacy settings without a shear binding install its default");
		for (EditorAction action : EditorAction.values())
			assertTrue(bindings.combo(action) != null, "every action stays bound after healing: " + action);
		bindings.loadFrom(null);
		assertEquals(EditorAction.SAVE.defaultCombo, bindings.combo(EditorAction.SAVE),
			"null storage leaves bindings intact");
	}

	private static void testsStoredSwapsAndConflictChains() {
		KeyBindings bindings = new KeyBindings();
		Map<String, String> stored = new HashMap<>(bindings.toStored());
		stored.put(EditorAction.TOOL_SELECT.preferenceKey(), EditorAction.TOOL_MOVE.defaultCombo.encode());
		stored.put(EditorAction.TOOL_MOVE.preferenceKey(), EditorAction.TOOL_SELECT.defaultCombo.encode());
		bindings.loadFrom(stored::get);
		assertEquals(EditorAction.TOOL_MOVE.defaultCombo, bindings.combo(EditorAction.TOOL_SELECT),
			"valid stored swaps survive loading");
		assertEquals(EditorAction.TOOL_SELECT.defaultCombo, bindings.combo(EditorAction.TOOL_MOVE),
			"both sides of a valid stored swap survive loading");

		stored = new HashMap<>(new KeyBindings().toStored());
		stored.put(EditorAction.TOOL_SELECT.preferenceKey(), EditorAction.TOOL_MOVE.defaultCombo.encode());
		stored.put(EditorAction.TOOL_MOVE.preferenceKey(), EditorAction.TOOL_ROTATE.defaultCombo.encode());
		stored.put(EditorAction.TOOL_ROTATE.preferenceKey(), EditorAction.TOOL_ROTATE.defaultCombo.encode());
		bindings.loadFrom(stored::get);
		assertEquals(EditorAction.TOOL_SELECT.defaultCombo, bindings.combo(EditorAction.TOOL_SELECT),
			"cascading conflicts eventually restore the first default");
		assertEquals(EditorAction.TOOL_MOVE.defaultCombo, bindings.combo(EditorAction.TOOL_MOVE),
			"cascading conflicts restore the intermediate default");
		for (EditorAction action : EditorAction.values())
			assertEquals(null, bindings.conflict(bindings.combo(action), action),
				"healed bindings remain unique: " + action);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual == null : expected.equals(actual)) return;
		throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private KeyBindingsTest() {}
}
