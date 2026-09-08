package com.openspine.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonEditor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Modal editor for Spine 4.3 Path constraint setup data. */
final class PathConstraintDialog {
	static final String NEW_PATH = "（新建 Path）";

	record Draft(String name, String bones, String slot, boolean skinRequired, boolean skinActive,
		String positionMode, String spacingMode, String rotateMode, String offsetRotation,
		String position, String spacing, String mixRotate, String mixX, String mixY) {}

	private final Stage stage;
	private final SkeletonDocument doc;
	private final UiKit kit;
	private final SkeletonEditor editor;
	private final EditHistory history;
	private final Runnable onChanged;
	private final BiConsumer<String, Boolean> onStatus;
	private final Actor previousKeyboardFocus;
	private final Window window;
	private final SelectBox<String> constraintBox, slotBox, positionModeBox, spacingModeBox, rotateModeBox;
	private final TextField nameField, bonesField, offsetRotationField, positionField, spacingField,
		mixRotateField, mixXField, mixYField;
	private final TextButton newButton, deleteButton, skinRequiredButton, skinActiveButton, applyButton;
	private final ConstraintOrderControls.Controls orderControls;
	private final Label currentSkinLabel, errorLabel;
	private PathConstraintData editingConstraint;
	private Draft baseline;
	private boolean refreshing;
	private boolean closed;

	static void show(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		if (stage == null || doc == null || doc.data == null || doc.data.getBones().isEmpty()
			|| doc.data.getSlots().isEmpty()) return;
		new PathConstraintDialog(stage, doc, kit, editor, history, onChanged, onStatus).open();
	}

	private PathConstraintDialog(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		this.stage = stage;
		this.doc = doc;
		this.kit = kit;
		this.editor = editor;
		this.history = history;
		this.onChanged = onChanged;
		this.onStatus = onStatus;
		previousKeyboardFocus = stage.getKeyboardFocus();

		window = kit.window("Path 约束 Setup");
		window.setModal(true);
		kit.padWindow(window, 10f);

		constraintBox = kit.selectBox();
		newButton = new TextButton("新建", kit.toolButton);
		deleteButton = new TextButton("删除…", kit.toolButton);
		Table selectorActions = new Table();
		selectorActions.add(newButton).width(68).height(27).padRight(4);
		selectorActions.add(deleteButton).width(68).height(27);
		orderControls = ConstraintOrderControls.add(selectorActions, kit,
			() -> moveConstraint(-1), () -> moveConstraint(1));
		window.add(kit.controlLabel("约束")).right().padRight(7);
		window.add(constraintBox).width(250).padRight(7);
		window.add(selectorActions).row();

		nameField = textField();
		nameField.setMaxLength(120);
		addRow("名称", nameField);
		bonesField = textField();
		addRow("受约束骨骼", bonesField);
		slotBox = kit.selectBox();
		addRow("目标槽", slotBox);

		positionModeBox = kit.selectBox();
		positionModeBox.setItems(positionModeLabels());
		spacingModeBox = kit.selectBox();
		spacingModeBox.setItems(spacingModeLabels());
		rotateModeBox = kit.selectBox();
		rotateModeBox.setItems(rotateModeLabels());
		Table modes = new Table();
		modes.add(kit.controlLabel("位置")).padRight(4);
		modes.add(positionModeBox).width(92).padRight(10);
		modes.add(kit.controlLabel("间距")).padRight(4);
		modes.add(spacingModeBox).width(92).padRight(10);
		modes.add(kit.controlLabel("旋转")).padRight(4);
		modes.add(rotateModeBox).width(92);
		addRow("模式", modes);

		offsetRotationField = numberField();
		positionField = numberField();
		spacingField = numberField();
		addNumberTriple("Setup 数值", "旋转偏移", offsetRotationField,
			"位置", positionField, "间距", spacingField);
		mixRotateField = numberField();
		mixXField = numberField();
		mixYField = numberField();
		addNumberTriple("混合", "旋转", mixRotateField, "X", mixXField, "Y", mixYField);

		skinRequiredButton = toggleButton();
		skinActiveButton = toggleButton();
		currentSkinLabel = kit.controlLabel("");
		currentSkinLabel.setEllipsis(true);
		Table skinControls = new Table();
		skinControls.add(kit.controlLabel("皮肤必需")).padRight(4);
		skinControls.add(skinRequiredButton).width(88).padRight(12);
		skinControls.add(currentSkinLabel).width(120).left().padRight(4);
		skinControls.add(skinActiveButton).width(88);
		addRow("皮肤激活", skinControls);

		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(3).width(540).left().padTop(7).row();
		applyButton = new TextButton("应用", kit.accentButton);
		TextButton closeButton = new TextButton("关闭", kit.toolButton);
		Table buttons = new Table();
		ConstraintClipboard.addButtons(buttons, kit, this::copySettings, this::pasteSettings);
		buttons.add(applyButton).width(82).height(28).padRight(6);
		buttons.add(closeButton).width(82).height(28);
		window.add(buttons).colspan(3).right().padTop(6);

		ChangeListener draftListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (refreshing) return;
				if (actor == skinRequiredButton && !skinRequiredButton.isChecked())
					skinActiveButton.setChecked(false);
				refreshToggleStyles();
				refreshApplyState();
			}
		};
		for (Actor actor : draftActors()) actor.addListener(draftListener);
		constraintBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshing) loadSelection();
			}
		});
		newButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				constraintBox.setSelected(NEW_PATH);
				loadSelection();
				stage.setKeyboardFocus(nameField);
				nameField.selectAll();
			}
		});
		deleteButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { requestDelete(); }
		});
		applyButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { submit(); }
		});
		closeButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { close(); }
		});
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (keycode == Input.Keys.ESCAPE) { close(); return true; }
				if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
					if (!applyButton.isDisabled()) submit();
					return true;
				}
				return false;
			}
		});
	}

	private void open() {
		refreshConstraintItems(null);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(constraintBox);
	}

	private Actor[] draftActors() {
		return new Actor[] {nameField, bonesField, slotBox, positionModeBox, spacingModeBox, rotateModeBox,
			offsetRotationField, positionField, spacingField, mixRotateField, mixXField, mixYField,
			skinRequiredButton, skinActiveButton};
	}

	private void addRow(String label, Actor actor) {
		window.add(kit.controlLabel(label)).right().padRight(7).padTop(5);
		window.add(actor).colspan(2).width(415).left().padTop(5).row();
	}

	private void addNumberTriple(String rowLabel, String firstLabel, TextField first,
		String secondLabel, TextField second, String thirdLabel, TextField third) {
		Table row = new Table();
		row.add(kit.controlLabel(firstLabel)).right().padRight(4);
		row.add(first).width(82).padRight(10);
		row.add(kit.controlLabel(secondLabel)).right().padRight(4);
		row.add(second).width(82).padRight(10);
		row.add(kit.controlLabel(thirdLabel)).right().padRight(4);
		row.add(third).width(82);
		addRow(rowLabel, row);
	}

	private TextField textField() {
		return new TextField("", kit.textFieldStyle);
	}

	private TextField numberField() {
		TextField field = textField();
		field.setMaxLength(24);
		field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+'
			|| c == '.' || c == 'e' || c == 'E');
		return field;
	}

	private TextButton toggleButton() {
		TextButton button = new TextButton("已关闭", kit.toolButton);
		button.setProgrammaticChangeEvents(false);
		return button;
	}

	private void refreshConstraintItems(String requestedName) {
		refreshing = true;
		try {
			Array<String> items = new Array<>();
			items.add(NEW_PATH);
			for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof PathConstraintData) items.add(constraint.getName());
			constraintBox.setItems(items);
			constraintBox.setSelected(requestedName != null && items.contains(requestedName, false)
				? requestedName : items.size > 1 ? items.get(1) : NEW_PATH);
		} finally {
			refreshing = false;
		}
		loadSelection();
	}

	private void loadSelection() {
		refreshing = true;
		try {
			editingConstraint = selectedConstraint();
			slotBox.setItems(slotNames());
			Skin skin = currentSkin();
			currentSkinLabel.setText(skin == null ? "无当前皮肤" : "当前：" + skin.getName());
			if (editingConstraint == null) loadNewDefaults();
			else loadConstraint(editingConstraint, skin);
			deleteButton.setDisabled(editingConstraint == null);
			errorLabel.setText("");
			refreshToggleStyles();
			baseline = editingConstraint == null ? null : currentDraft();
		} finally {
			refreshing = false;
		}
		refreshApplyState();
	}

	private void loadNewDefaults() {
		nameField.setText(uniqueName("path"));
		BoneData constrained = defaultConstrainedBone();
		bonesField.setText(constrained == null ? "" : constrained.getName());
		SlotData slot = defaultSlot(constrained);
		slotBox.setSelected(slot == null ? null : slot.getName());
		positionModeBox.setSelected(positionModeLabel(PathConstraintData.PositionMode.percent));
		spacingModeBox.setSelected(spacingModeLabel(PathConstraintData.SpacingMode.length));
		rotateModeBox.setSelected(rotateModeLabel(PathConstraintData.RotateMode.tangent));
		setNumbers(new float[] {0f, 0f, 0f, 1f, 1f, 1f});
		skinRequiredButton.setChecked(false);
		skinActiveButton.setChecked(false);
	}

	private void loadConstraint(PathConstraintData constraint, Skin skin) {
		loadSetup(SkeletonEditor.pathConstraintSetup(constraint),
			editor.isPathConstraintActiveInSkin(constraint, skin));
	}

	private void loadSetup(SkeletonEditor.PathConstraintSetup setup, boolean active) {
		nameField.setText(setup.name());
		bonesField.setText(joinBoneNames(setup.bones()));
		slotBox.setSelected(setup.slot().getName());
		positionModeBox.setSelected(positionModeLabel(setup.positionMode()));
		spacingModeBox.setSelected(spacingModeLabel(setup.spacingMode()));
		rotateModeBox.setSelected(rotateModeLabel(setup.rotateMode()));
		setNumbers(new float[] {setup.offsetRotation(), setup.position(), setup.spacing(),
			setup.mixRotate(), setup.mixX(), setup.mixY()});
		skinRequiredButton.setChecked(setup.skinRequired());
		skinActiveButton.setChecked(active);
	}

	private void setNumbers(float[] values) {
		TextField[] fields = numberFields();
		for (int index = 0; index < fields.length; index++) fields[index].setText(UiNumbers.format(values[index]));
	}

	private TextField[] numberFields() {
		return new TextField[] {offsetRotationField, positionField, spacingField,
			mixRotateField, mixXField, mixYField};
	}

	private void refreshToggleStyles() {
		refreshToggle(skinRequiredButton);
		refreshToggle(skinActiveButton);
		boolean canActivate = skinRequiredButton.isChecked() && currentSkin() != null;
		skinActiveButton.setDisabled(!canActivate);
		if (!canActivate && currentSkin() == null) skinActiveButton.setText("无皮肤");
	}

	private void refreshToggle(TextButton button) {
		button.setText(button.isChecked() ? "已启用" : "已关闭");
		button.setStyle(button.isChecked() ? kit.activeToolButton : kit.toolButton);
	}

	private void refreshApplyState() {
		Draft current = currentDraft();
		boolean draftClean = current != null && baseline != null && baseline.equals(current);
		applyButton.setDisabled(current == null || draftClean);
		ConstraintOrderControls.refresh(orderControls, editor, editingConstraint, draftClean);
	}

	private Draft currentDraft() {
		return pathDraft(nameField.getText(), bonesField.getText(), slotBox.getSelected(),
			skinRequiredButton.isChecked(), skinActiveButton.isChecked(), positionModeBox.getSelected(),
			spacingModeBox.getSelected(), rotateModeBox.getSelected(), offsetRotationField.getText(),
			positionField.getText(), spacingField.getText(), mixRotateField.getText(),
			mixXField.getText(), mixYField.getText());
	}

	private void copySettings() {
		if (closed) return;
		try {
			SkeletonEditor.PathConstraintSetup setup = setupFromControls();
			String reason = editor.validatePathConstraintSetup(editingConstraint, setup,
				currentSkin(), skinActiveButton.isChecked());
			if (reason != null) throw new IllegalArgumentException(reason);
			ConstraintClipboard.SESSION.copy(setup);
			errorLabel.setText("");
			showStatus("Path 约束设置已复制", false);
		} catch (RuntimeException error) {
			errorLabel.setText(error.getMessage() == null ? "无法复制约束设置" : error.getMessage());
		}
	}

	private void pasteSettings() {
		if (closed) return;
		try {
			SkeletonEditor.PathConstraintSetup setup = ConstraintClipboard.SESSION.pastePath(doc.data,
				nameField.getText());
			boolean active = setup.skinRequired() && skinActiveButton.isChecked() && currentSkin() != null;
			String reason = editor.validatePathConstraintSetup(editingConstraint, setup, currentSkin(), active);
			if (reason != null) throw new IllegalArgumentException("无法粘贴：" + reason + "，草稿未改变");
			refreshing = true;
			try {
				loadSetup(setup, active);
				refreshToggleStyles();
				errorLabel.setText("");
			} finally {
				refreshing = false;
			}
			refreshApplyState();
			showStatus(ConstraintClipboard.PASTED, false);
		} catch (RuntimeException error) {
			errorLabel.setText(error.getMessage() == null ? "无法粘贴约束设置" : error.getMessage());
		}
	}

	private void moveConstraint(int delta) {
		if (editingConstraint == null || baseline == null || !baseline.equals(currentDraft())) return;
		String name = editingConstraint.getName();
		if (!ConstraintOrderControls.move(history, editor, editingConstraint, delta,
			"调整 Path 约束顺序")) return;
		if (onChanged != null) onChanged.run();
		refreshConstraintItems(name);
		showStatus("Path 约束顺序已调整", false);
	}

	private void submit() {
		if (closed) return;
		try {
			Skin skin = currentSkin();
			SkeletonEditor.PathConstraintSetup setup = setupFromControls();
			boolean active = skinActiveButton.isChecked();
			String reason = editor.validatePathConstraintSetup(editingConstraint, setup, skin, active);
			if (reason != null) throw new IllegalArgumentException(reason);
			PathConstraintData before = editingConstraint;
			SkeletonEditor.PathConstraintSetup beforeSetup = before == null ? null
				: SkeletonEditor.pathConstraintSetup(before);
			var beforeActivations = before == null ? java.util.List.<Skin>of()
				: editor.constraintActivationSkins(before);
			PathConstraintData result = before == null
				? editor.addPathConstraint(setup, skin, active)
				: editor.updatePathConstraint(before, setup, skin, active);
			if (result == null) throw new IllegalStateException("Path 约束状态已变化，请重新选择");
			boolean changed = result != before;
			if (changed) ConstraintHistory.recordApplied(history, editor,
				before == null ? "创建 Path 约束" : "编辑 Path 约束", before, result, beforeSetup,
				SkeletonEditor.pathConstraintSetup(result), beforeActivations,
				editor.constraintActivationSkins(result), new ConstraintHistory.Operations<>() {
					@Override public PathConstraintData add(SkeletonEditor.PathConstraintSetup value) {
						return editor.addPathConstraint(value, null, false);
					}
					@Override public PathConstraintData update(PathConstraintData current,
						SkeletonEditor.PathConstraintSetup value) {
						return editor.updatePathConstraint(current, value, null, false);
					}
					@Override public boolean delete(PathConstraintData current) {
						return editor.deletePathConstraint(current);
					}
				});
			if (changed && onChanged != null) onChanged.run();
			refreshConstraintItems(result.getName());
			showStatus(changed ? (before == null ? "Path 约束已创建" : "Path 约束已更新")
				: "Path 约束未变化", false);
		} catch (RuntimeException error) {
			String message = error.getMessage();
			errorLabel.setText(message == null || message.isBlank() ? "无法应用 Path 约束设置" : message);
		}
	}

	private SkeletonEditor.PathConstraintSetup setupFromControls() {
		List<BoneData> bones = parseBones(bonesField.getText());
		SlotData slot = doc.data.findSlot(slotBox.getSelected());
		float[] values = new float[numberFields().length];
		TextField[] fields = numberFields();
		for (int index = 0; index < fields.length; index++)
			values[index] = parseNumber(fields[index].getText(), "Path 数值");
		return new SkeletonEditor.PathConstraintSetup(nameField.getText(), bones, slot,
			skinRequiredButton.isChecked(), positionMode(positionModeBox.getSelected()),
			spacingMode(spacingModeBox.getSelected()), rotateMode(rotateModeBox.getSelected()),
			values[0], values[1], values[2], values[3], values[4], values[5]);
	}

	private List<BoneData> parseBones(String value) {
		List<BoneData> bones = new ArrayList<>();
		for (String token : normalizeBoneList(value).split(",", -1)) {
			String name = token.trim();
			if (!name.isEmpty()) bones.add(doc.data.findBone(name));
		}
		return bones;
	}

	private void requestDelete() {
		PathConstraintData constraint = editingConstraint;
		if (constraint == null) return;
		String reason = editor.getPathConstraintDeleteBlockReason(constraint);
		if (reason != null) { errorLabel.setText(reason); return; }
		UiDialogs.confirm(stage, kit, "删除 Path 约束",
			"删除 Path 约束 " + constraint.getName() + "？"
				+ (history == null ? "此操作无法撤销。" : "可通过撤销恢复。"), "删除", () -> {
				SkeletonEditor.PathConstraintSetup setup = SkeletonEditor.pathConstraintSetup(constraint);
				var activations = editor.constraintActivationSkins(constraint);
				int index = editor.constraintIndex(constraint);
				if (!editor.deletePathConstraint(constraint)) {
					errorLabel.setText("Path 约束已发生变化，无法删除");
					return;
				}
				ConstraintHistory.recordDeleted(history, editor, "删除 Path 约束", constraint, setup,
					activations, index, new ConstraintHistory.Operations<>() {
						@Override public PathConstraintData add(SkeletonEditor.PathConstraintSetup value) {
							return editor.addPathConstraint(value, null, false);
						}
						@Override public PathConstraintData update(PathConstraintData current,
							SkeletonEditor.PathConstraintSetup value) {
							return editor.updatePathConstraint(current, value, null, false);
						}
						@Override public boolean delete(PathConstraintData current) {
							return editor.deletePathConstraint(current);
						}
					});
				if (onChanged != null) onChanged.run();
				refreshConstraintItems(null);
				showStatus("Path 约束已删除", false);
			});
	}

	private void close() {
		if (closed) return;
		closed = true;
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}

	private PathConstraintData selectedConstraint() {
		String name = constraintBox.getSelected();
		if (name == null || NEW_PATH.equals(name)) return null;
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint instanceof PathConstraintData path && path.getName().equals(name)) return path;
		return null;
	}

	private Skin currentSkin() {
		return doc.getEditingSkin();
	}

	private Array<String> slotNames() {
		Array<String> names = new Array<>();
		for (SlotData slot : doc.data.getSlots()) names.add(slot.getName());
		return names;
	}

	private BoneData defaultConstrainedBone() {
		Bone selected = doc.getSelectedBone();
		if (selected != null && defaultSlot(selected.getData()) != null) return selected.getData();
		for (BoneData bone : doc.data.getBones()) if (defaultSlot(bone) != null) return bone;
		return doc.data.getBones().first();
	}

	private SlotData defaultSlot(BoneData constrained) {
		for (SlotData slot : doc.data.getSlots())
			if (!isSlotInBoneSubtree(slot, constrained)) return slot;
		return null;
	}

	static boolean isSlotInBoneSubtree(SlotData slot, BoneData ancestor) {
		if (slot == null || ancestor == null) return false;
		for (BoneData bone = slot.getBoneData(); bone != null; bone = bone.getParent())
			if (bone == ancestor) return true;
		return false;
	}

	private String uniqueName(String base) {
		String candidate = base;
		int suffix = 2;
		while (constraintNameExists(candidate)) candidate = base + suffix++;
		return candidate;
	}

	private boolean constraintNameExists(String name) {
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint.getName().equals(name)) return true;
		return false;
	}

	private void showStatus(String message, boolean error) {
		if (onStatus != null) onStatus.accept(message, error);
	}

	private static float parseNumber(String value, String label) {
		try {
			return UiNumbers.parseFinite(value);
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(label + "必须是有限数值", error);
		}
	}

	private static String joinBoneNames(List<BoneData> bones) {
		StringBuilder text = new StringBuilder();
		for (BoneData bone : bones) {
			if (text.length() > 0) text.append(", ");
			text.append(bone.getName());
		}
		return text.toString();
	}

	private static String normalizeBoneList(String value) {
		if (value == null) return "";
		StringBuilder normalized = new StringBuilder();
		for (String token : value.replace('，', ',').split(",", -1)) {
			String name = token.trim();
			if (name.isEmpty()) continue;
			if (normalized.length() > 0) normalized.append(',');
			normalized.append(name);
		}
		return normalized.toString();
	}

	static Draft pathDraft(String name, String bones, String slot, boolean skinRequired, boolean skinActive,
		String positionMode, String spacingMode, String rotateMode, String offsetRotation,
		String position, String spacing, String mixRotate, String mixX, String mixY) {
		return new Draft(UiDrafts.text(name), normalizeBoneList(bones), UiDrafts.text(slot), skinRequired,
			skinActive, UiDrafts.text(positionMode), UiDrafts.text(spacingMode), UiDrafts.text(rotateMode),
			UiDrafts.number(offsetRotation), UiDrafts.number(position), UiDrafts.number(spacing),
			UiDrafts.number(mixRotate), UiDrafts.number(mixX), UiDrafts.number(mixY));
	}

	static String[] positionModeLabels() { return new String[] {"固定", "百分比"}; }
	static String[] spacingModeLabels() { return new String[] {"长度", "固定", "百分比", "比例"}; }
	static String[] rotateModeLabels() { return new String[] {"切线", "链", "链缩放"}; }

	static String positionModeLabel(PathConstraintData.PositionMode mode) {
		return mode == PathConstraintData.PositionMode.fixed ? "固定" : "百分比";
	}

	static PathConstraintData.PositionMode positionMode(String label) {
		if ("固定".equals(label)) return PathConstraintData.PositionMode.fixed;
		return "百分比".equals(label) ? PathConstraintData.PositionMode.percent : null;
	}

	static String spacingModeLabel(PathConstraintData.SpacingMode mode) {
		if (mode == PathConstraintData.SpacingMode.length) return "长度";
		if (mode == PathConstraintData.SpacingMode.fixed) return "固定";
		if (mode == PathConstraintData.SpacingMode.percent) return "百分比";
		return "比例";
	}

	static PathConstraintData.SpacingMode spacingMode(String label) {
		if ("长度".equals(label)) return PathConstraintData.SpacingMode.length;
		if ("固定".equals(label)) return PathConstraintData.SpacingMode.fixed;
		if ("百分比".equals(label)) return PathConstraintData.SpacingMode.percent;
		return "比例".equals(label) ? PathConstraintData.SpacingMode.proportional : null;
	}

	static String rotateModeLabel(PathConstraintData.RotateMode mode) {
		if (mode == PathConstraintData.RotateMode.tangent) return "切线";
		if (mode == PathConstraintData.RotateMode.chain) return "链";
		return "链缩放";
	}

	static PathConstraintData.RotateMode rotateMode(String label) {
		if ("切线".equals(label)) return PathConstraintData.RotateMode.tangent;
		if ("链".equals(label)) return PathConstraintData.RotateMode.chain;
		return "链缩放".equals(label) ? PathConstraintData.RotateMode.chainScale : null;
	}
}
