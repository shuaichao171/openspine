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
import com.esotericsoftware.spine.ConstraintData.ScaleYMode;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.Skin;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonEditor;

import java.util.function.BiConsumer;

/** Modal editor for Spine 4.3 IK setup data and current-skin activation. */
final class IkConstraintDialog {
	static final String NEW_IK = "（新建 IK）";
	static final String NO_SECOND_BONE = "（单骨骼）";

	record Draft(String name, String firstBone, String secondBone, String target, boolean skinRequired,
		String scaleYMode, String mix, String softness, String bend, boolean compress, boolean stretch,
		boolean skinActive) {}

	private final Stage stage;
	private final SkeletonDocument doc;
	private final UiKit kit;
	private final SkeletonEditor editor;
	private final EditHistory history;
	private final Runnable onChanged;
	private final BiConsumer<String, Boolean> onStatus;
	private final Actor previousKeyboardFocus;
	private final Window window;
	private final SelectBox<String> constraintBox, firstBoneBox, secondBoneBox, targetBox, scaleYBox, bendBox;
	private final TextField nameField, mixField, softnessField;
	private final TextButton newButton, deleteButton, skinRequiredButton, skinActiveButton,
		compressButton, stretchButton, applyButton;
	private final ConstraintOrderControls.Controls orderControls;
	private final Label currentSkinLabel, errorLabel;
	private IkConstraintData editingConstraint;
	private Draft baseline;
	private boolean refreshing;
	private boolean closed;

	static void show(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		if (stage == null || doc == null || doc.data == null || doc.data.getBones().isEmpty()) return;
		new IkConstraintDialog(stage, doc, kit, editor, history, onChanged, onStatus).open();
	}

	private IkConstraintDialog(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		this.stage = stage;
		this.doc = doc;
		this.kit = kit;
		this.editor = editor;
		this.history = history;
		this.onChanged = onChanged;
		this.onStatus = onStatus;
		previousKeyboardFocus = stage.getKeyboardFocus();

		window = kit.window("IK 约束 Setup");
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
		window.add(constraintBox).width(230).padRight(7);
		window.add(selectorActions).row();

		nameField = new TextField("", kit.textFieldStyle);
		nameField.setMaxLength(120);
		addRow("名称", nameField);
		firstBoneBox = kit.selectBox();
		addRow("受约束骨骼", firstBoneBox);
		secondBoneBox = kit.selectBox();
		addRow("第二骨骼", secondBoneBox);
		targetBox = kit.selectBox();
		addRow("目标骨骼", targetBox);

		mixField = numberField();
		softnessField = numberField();
		Table numeric = new Table();
		numeric.add(kit.controlLabel("混合")).padRight(4);
		numeric.add(mixField).width(80).padRight(12);
		numeric.add(kit.controlLabel("柔度")).padRight(4);
		numeric.add(softnessField).width(80);
		addRow("Setup 数值", numeric);

		bendBox = kit.selectBox();
		bendBox.setItems("正向", "反向");
		scaleYBox = kit.selectBox();
		scaleYBox.setItems(scaleYLabels());
		Table modes = new Table();
		modes.add(kit.controlLabel("弯曲")).padRight(4);
		modes.add(bendBox).width(90).padRight(12);
		modes.add(kit.controlLabel("缩放 Y")).padRight(4);
		modes.add(scaleYBox).width(90);
		addRow("解算模式", modes);

		compressButton = toggleButton();
		stretchButton = toggleButton();
		Table reach = new Table();
		reach.add(kit.controlLabel("压缩")).padRight(4);
		reach.add(compressButton).width(90).padRight(12);
		reach.add(kit.controlLabel("拉伸")).padRight(4);
		reach.add(stretchButton).width(90);
		addRow("距离适配", reach);

		skinRequiredButton = toggleButton();
		skinActiveButton = toggleButton();
		currentSkinLabel = kit.controlLabel("");
		currentSkinLabel.setEllipsis(true);
		Table skinControls = new Table();
		skinControls.add(kit.controlLabel("皮肤必需")).padRight(4);
		skinControls.add(skinRequiredButton).width(90).padRight(12);
		skinControls.add(currentSkinLabel).width(110).left().padRight(4);
		skinControls.add(skinActiveButton).width(90);
		addRow("皮肤激活", skinControls);

		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(3).width(500).left().padTop(7).row();
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
		nameField.addListener(draftListener);
		mixField.addListener(draftListener);
		softnessField.addListener(draftListener);
		firstBoneBox.addListener(draftListener);
		secondBoneBox.addListener(draftListener);
		targetBox.addListener(draftListener);
		scaleYBox.addListener(draftListener);
		bendBox.addListener(draftListener);
		compressButton.addListener(draftListener);
		stretchButton.addListener(draftListener);
		skinRequiredButton.addListener(draftListener);
		skinActiveButton.addListener(draftListener);

		constraintBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshing) loadSelection();
			}
		});
		newButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				constraintBox.setSelected(NEW_IK);
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

	private void addRow(String label, Actor actor) {
		window.add(kit.controlLabel(label)).right().padRight(7).padTop(6);
		window.add(actor).colspan(2).width(375).left().padTop(6).row();
	}

	private TextField numberField() {
		TextField field = new TextField("", kit.textFieldStyle);
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
			items.add(NEW_IK);
			for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof IkConstraintData) items.add(constraint.getName());
			constraintBox.setItems(items);
			constraintBox.setSelected(requestedName != null && items.contains(requestedName, false)
				? requestedName : items.size > 1 ? items.get(1) : NEW_IK);
		} finally {
			refreshing = false;
		}
		loadSelection();
	}

	private void loadSelection() {
		refreshing = true;
		try {
			editingConstraint = selectedConstraint();
			Array<String> bones = boneNames(false);
			Array<String> optionalBones = boneNames(true);
			firstBoneBox.setItems(bones);
			secondBoneBox.setItems(optionalBones);
			targetBox.setItems(bones);
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
		nameField.setText(uniqueName("ik"));
		BoneData first = defaultFirstBone();
		firstBoneBox.setSelected(first == null ? null : first.getName());
		secondBoneBox.setSelected(NO_SECOND_BONE);
		BoneData target = defaultTarget(first);
		targetBox.setSelected(target == null ? null : target.getName());
		mixField.setText("1");
		softnessField.setText("0");
		bendBox.setSelected("正向");
		scaleYBox.setSelected(scaleYLabel(ScaleYMode.none));
		compressButton.setChecked(false);
		stretchButton.setChecked(false);
		skinRequiredButton.setChecked(false);
		skinActiveButton.setChecked(false);
	}

	private void loadConstraint(IkConstraintData constraint, Skin skin) {
		loadSetup(SkeletonEditor.ikConstraintSetup(constraint),
			editor.isIkConstraintActiveInSkin(constraint, skin));
	}

	private void loadSetup(SkeletonEditor.IkConstraintSetup setup, boolean active) {
		nameField.setText(setup.name());
		firstBoneBox.setSelected(setup.firstBone().getName());
		secondBoneBox.setSelected(setup.secondBone() == null ? NO_SECOND_BONE : setup.secondBone().getName());
		targetBox.setSelected(setup.target().getName());
		mixField.setText(UiNumbers.format(setup.mix()));
		softnessField.setText(UiNumbers.format(setup.softness()));
		bendBox.setSelected(setup.bendDirection() < 0 ? "反向" : "正向");
		scaleYBox.setSelected(scaleYLabel(setup.scaleYMode()));
		compressButton.setChecked(setup.compress());
		stretchButton.setChecked(setup.stretch());
		skinRequiredButton.setChecked(setup.skinRequired());
		skinActiveButton.setChecked(active);
	}

	private void refreshToggleStyles() {
		refreshToggle(compressButton);
		refreshToggle(stretchButton);
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
		return ikDraft(nameField.getText(), firstBoneBox.getSelected(), secondBoneBox.getSelected(),
			targetBox.getSelected(), skinRequiredButton.isChecked(), scaleYBox.getSelected(), mixField.getText(),
			softnessField.getText(), bendBox.getSelected(), compressButton.isChecked(), stretchButton.isChecked(),
			skinActiveButton.isChecked());
	}

	private void copySettings() {
		if (closed) return;
		try {
			SkeletonEditor.IkConstraintSetup setup = setupFromControls();
			String reason = editor.validateIkConstraintSetup(editingConstraint, setup,
				currentSkin(), skinActiveButton.isChecked());
			if (reason != null) throw new IllegalArgumentException(reason);
			ConstraintClipboard.SESSION.copy(setup);
			errorLabel.setText("");
			showStatus("Ik 约束设置已复制", false);
		} catch (RuntimeException error) {
			errorLabel.setText(error.getMessage() == null ? "无法复制约束设置" : error.getMessage());
		}
	}

	private void pasteSettings() {
		if (closed) return;
		try {
			SkeletonEditor.IkConstraintSetup setup = ConstraintClipboard.SESSION.pasteIk(doc.data,
				nameField.getText());
			boolean active = setup.skinRequired() && skinActiveButton.isChecked() && currentSkin() != null;
			String reason = editor.validateIkConstraintSetup(editingConstraint, setup, currentSkin(), active);
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
		if (!ConstraintOrderControls.move(history, editor, editingConstraint, delta, "调整 IK 约束顺序")) return;
		if (onChanged != null) onChanged.run();
		refreshConstraintItems(name);
		showStatus("IK 约束顺序已调整", false);
	}

	private void submit() {
		if (closed) return;
		try {
			Skin skin = currentSkin();
			SkeletonEditor.IkConstraintSetup setup = setupFromControls();
			boolean active = skinActiveButton.isChecked();
			String reason = editor.validateIkConstraintSetup(editingConstraint, setup, skin, active);
			if (reason != null) throw new IllegalArgumentException(reason);
			IkConstraintData before = editingConstraint;
			SkeletonEditor.IkConstraintSetup beforeSetup = before == null ? null
				: SkeletonEditor.ikConstraintSetup(before);
			var beforeActivations = before == null ? java.util.List.<Skin>of()
				: editor.constraintActivationSkins(before);
			IkConstraintData result = before == null ? editor.addIkConstraint(setup, skin, active)
				: editor.updateIkConstraint(before, setup, skin, active);
			if (result == null) throw new IllegalStateException("IK 约束状态已变化，请重新选择");
			boolean changed = result != before;
			if (changed) ConstraintHistory.recordApplied(history, editor,
				before == null ? "创建 IK 约束" : "编辑 IK 约束", before, result, beforeSetup,
				SkeletonEditor.ikConstraintSetup(result), beforeActivations,
				editor.constraintActivationSkins(result), new ConstraintHistory.Operations<>() {
					@Override public IkConstraintData add(SkeletonEditor.IkConstraintSetup value) {
						return editor.addIkConstraint(value, null, false);
					}
					@Override public IkConstraintData update(IkConstraintData current,
						SkeletonEditor.IkConstraintSetup value) {
						return editor.updateIkConstraint(current, value, null, false);
					}
					@Override public boolean delete(IkConstraintData current) {
						return editor.deleteIkConstraint(current);
					}
				});
			if (changed && onChanged != null) onChanged.run();
			refreshConstraintItems(result.getName());
			showStatus(changed ? (before == null ? "IK 约束已创建" : "IK 约束已更新") : "IK 约束未变化", false);
		} catch (RuntimeException error) {
			String message = error.getMessage();
			errorLabel.setText(message == null || message.isBlank() ? "无法应用 IK 约束设置" : message);
		}
	}

	private SkeletonEditor.IkConstraintSetup setupFromControls() {
		BoneData first = doc.data.findBone(firstBoneBox.getSelected());
		String secondName = secondBoneBox.getSelected();
		BoneData second = NO_SECOND_BONE.equals(secondName) ? null : doc.data.findBone(secondName);
		BoneData target = doc.data.findBone(targetBox.getSelected());
		ScaleYMode scaleY = scaleYMode(scaleYBox.getSelected());
		float mix = parseNumber(mixField.getText(), "IK 混合");
		float softness = parseNumber(softnessField.getText(), "IK 柔度");
		int bend = "反向".equals(bendBox.getSelected()) ? -1 : 1;
		return new SkeletonEditor.IkConstraintSetup(nameField.getText(), first, second, target,
			skinRequiredButton.isChecked(), scaleY, mix, softness, bend,
			compressButton.isChecked(), stretchButton.isChecked());
	}

	private void requestDelete() {
		IkConstraintData constraint = editingConstraint;
		if (constraint == null) return;
		String reason = editor.getIkConstraintDeleteBlockReason(constraint);
		if (reason != null) {
			errorLabel.setText(reason);
			return;
		}
		UiDialogs.confirm(stage, kit, "删除 IK 约束",
			"删除 IK 约束 " + constraint.getName() + "？"
				+ (history == null ? "此操作无法撤销。" : "可通过撤销恢复。"), "删除", () -> {
				SkeletonEditor.IkConstraintSetup setup = SkeletonEditor.ikConstraintSetup(constraint);
				var activations = editor.constraintActivationSkins(constraint);
				int index = editor.constraintIndex(constraint);
				if (!editor.deleteIkConstraint(constraint)) {
					errorLabel.setText("IK 约束已发生变化，无法删除");
					return;
				}
				ConstraintHistory.recordDeleted(history, editor, "删除 IK 约束", constraint, setup,
					activations, index, new ConstraintHistory.Operations<>() {
						@Override public IkConstraintData add(SkeletonEditor.IkConstraintSetup value) {
							return editor.addIkConstraint(value, null, false);
						}
						@Override public IkConstraintData update(IkConstraintData current,
							SkeletonEditor.IkConstraintSetup value) {
							return editor.updateIkConstraint(current, value, null, false);
						}
						@Override public boolean delete(IkConstraintData current) {
							return editor.deleteIkConstraint(current);
						}
					});
				if (onChanged != null) onChanged.run();
				refreshConstraintItems(null);
				showStatus("IK 约束已删除", false);
			});
	}

	private void close() {
		if (closed) return;
		closed = true;
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}

	private IkConstraintData selectedConstraint() {
		String name = constraintBox.getSelected();
		if (name == null || NEW_IK.equals(name)) return null;
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint instanceof IkConstraintData ik && ik.getName().equals(name)) return ik;
		return null;
	}

	private Skin currentSkin() {
		return doc.getEditingSkin();
	}

	private Array<String> boneNames(boolean optional) {
		Array<String> names = new Array<>();
		if (optional) names.add(NO_SECOND_BONE);
		for (BoneData bone : doc.data.getBones()) names.add(bone.getName());
		return names;
	}

	private BoneData defaultFirstBone() {
		Bone selected = doc.getSelectedBone();
		if (selected != null && selected.getData().getParent() != null) return selected.getData();
		for (BoneData bone : doc.data.getBones()) if (bone.getParent() != null) return bone;
		return doc.data.getBones().first();
	}

	private BoneData defaultTarget(BoneData first) {
		if (first != null && first.getParent() != null) return first.getParent();
		for (BoneData bone : doc.data.getBones()) if (bone != first) return bone;
		return null;
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
			throw new IllegalArgumentException(label + " 必须是有限数值", error);
		}
	}

	static Draft ikDraft(String name, String firstBone, String secondBone, String target,
		boolean skinRequired, String scaleYMode, String mix, String softness, String bend,
		boolean compress, boolean stretch, boolean skinActive) {
		return new Draft(UiDrafts.text(name), UiDrafts.text(firstBone), UiDrafts.text(secondBone),
			UiDrafts.text(target), skinRequired, UiDrafts.text(scaleYMode), UiDrafts.number(mix),
			UiDrafts.number(softness), UiDrafts.text(bend), compress, stretch, skinActive);
	}

	static String[] scaleYLabels() {
		return new String[] {"无", "等比", "体积"};
	}

	static String scaleYLabel(ScaleYMode mode) {
		if (mode == ScaleYMode.uniform) return "等比";
		if (mode == ScaleYMode.volume) return "体积";
		return "无";
	}

	static ScaleYMode scaleYMode(String label) {
		if ("等比".equals(label)) return ScaleYMode.uniform;
		if ("体积".equals(label)) return ScaleYMode.volume;
		return "无".equals(label) ? ScaleYMode.none : null;
	}
}
