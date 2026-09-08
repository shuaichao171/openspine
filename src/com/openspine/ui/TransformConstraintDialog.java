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
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.TransformConstraintData;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonEditor.TransformFromPropertySetup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Modal editor for Spine 4.3 Transform constraint setup data. */
final class TransformConstraintDialog {
	static final String NEW_TRANSFORM = "（新建 Transform）";

	record Draft(String name, String bones, String source, boolean skinRequired, boolean skinActive,
		boolean localSource, boolean localTarget, boolean additive, boolean clamp, String offsetRotation,
		String offsetX, String offsetY, String offsetScaleX, String offsetScaleY, String offsetShearY,
		String mixRotate, String mixX, String mixY, String mixScaleX, String mixScaleY, String mixShearY,
		List<TransformFromPropertySetup> properties) {
		Draft { properties = properties == null ? List.of() : List.copyOf(properties); }
	}

	private final Stage stage;
	private final SkeletonDocument doc;
	private final UiKit kit;
	private final SkeletonEditor editor;
	private final EditHistory history;
	private final Runnable onChanged;
	private final BiConsumer<String, Boolean> onStatus;
	private final Actor previousKeyboardFocus;
	private final Window window;
	private final SelectBox<String> constraintBox, sourceBox;
	private final TextField nameField, bonesField;
	private final TextField offsetRotationField, offsetXField, offsetYField, offsetScaleXField,
		offsetScaleYField, offsetShearYField;
	private final TextField mixRotateField, mixXField, mixYField, mixScaleXField, mixScaleYField, mixShearYField;
	private final TextButton newButton, deleteButton, skinRequiredButton, skinActiveButton,
		localSourceButton, localTargetButton, additiveButton, clampButton, propertiesButton, applyButton;
	private final ConstraintOrderControls.Controls orderControls;
	private final Label currentSkinLabel, propertiesSummaryLabel, errorLabel;
	private TransformConstraintData editingConstraint;
	private List<TransformFromPropertySetup> propertyDraft = List.of();
	private Draft baseline;
	private boolean refreshing;
	private boolean closed;

	static void show(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		if (stage == null || doc == null || doc.data == null || doc.data.getBones().isEmpty()) return;
		new TransformConstraintDialog(stage, doc, kit, editor, history, onChanged, onStatus).open();
	}

	private TransformConstraintDialog(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		this.stage = stage;
		this.doc = doc;
		this.kit = kit;
		this.editor = editor;
		this.history = history;
		this.onChanged = onChanged;
		this.onStatus = onStatus;
		previousKeyboardFocus = stage.getKeyboardFocus();

		window = kit.window("Transform 约束 Setup");
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
		sourceBox = kit.selectBox();
		addRow("源骨骼", sourceBox);

		offsetRotationField = numberField();
		offsetXField = numberField();
		offsetYField = numberField();
		offsetScaleXField = numberField();
		offsetScaleYField = numberField();
		offsetShearYField = numberField();
		addNumberPair("偏移", "旋转", offsetRotationField, "X", offsetXField);
		addNumberPair("偏移", "Y", offsetYField, "缩放 X", offsetScaleXField);
		addNumberPair("偏移", "缩放 Y", offsetScaleYField, "错切 Y", offsetShearYField);

		mixRotateField = numberField();
		mixXField = numberField();
		mixYField = numberField();
		mixScaleXField = numberField();
		mixScaleYField = numberField();
		mixShearYField = numberField();
		addNumberPair("混合", "旋转", mixRotateField, "X", mixXField);
		addNumberPair("混合", "Y", mixYField, "缩放 X", mixScaleXField);
		addNumberPair("混合", "缩放 Y", mixScaleYField, "错切 Y", mixShearYField);

		localSourceButton = toggleButton();
		localTargetButton = toggleButton();
		addTogglePair("空间", "局部源", localSourceButton, "局部目标", localTargetButton);
		additiveButton = toggleButton();
		clampButton = toggleButton();
		addTogglePair("解算", "叠加", additiveButton, "限制", clampButton);
		propertiesButton = new TextButton("编辑...", kit.toolButton);
		propertiesSummaryLabel = kit.controlLabel("");
		propertiesSummaryLabel.setEllipsis(true);
		Table propertyControls = new Table();
		propertyControls.add(propertiesButton).width(88).height(27).padRight(8);
		propertyControls.add(propertiesSummaryLabel).width(280).left();
		addRow("属性映射", propertyControls);

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
		window.add(errorLabel).colspan(3).width(520).left().padTop(7).row();
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
				constraintBox.setSelected(NEW_TRANSFORM);
				loadSelection();
				stage.setKeyboardFocus(nameField);
				nameField.selectAll();
			}
		});
		deleteButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { requestDelete(); }
		});
		propertiesButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { editProperties(); }
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
		return new Actor[] {nameField, bonesField, sourceBox, offsetRotationField, offsetXField, offsetYField,
			offsetScaleXField, offsetScaleYField, offsetShearYField, mixRotateField, mixXField, mixYField,
			mixScaleXField, mixScaleYField, mixShearYField, localSourceButton, localTargetButton,
			additiveButton, clampButton, skinRequiredButton, skinActiveButton};
	}

	private void addRow(String label, Actor actor) {
		window.add(kit.controlLabel(label)).right().padRight(7).padTop(5);
		window.add(actor).colspan(2).width(395).left().padTop(5).row();
	}

	private void addNumberPair(String rowLabel, String firstLabel, TextField first,
		String secondLabel, TextField second) {
		Table row = new Table();
		row.add(kit.controlLabel(firstLabel)).width(54).right().padRight(4);
		row.add(first).width(105).padRight(16);
		row.add(kit.controlLabel(secondLabel)).width(64).right().padRight(4);
		row.add(second).width(105);
		addRow(rowLabel, row);
	}

	private void addTogglePair(String rowLabel, String firstLabel, TextButton first,
		String secondLabel, TextButton second) {
		Table row = new Table();
		row.add(kit.controlLabel(firstLabel)).width(70).right().padRight(4);
		row.add(first).width(90).padRight(14);
		row.add(kit.controlLabel(secondLabel)).width(70).right().padRight(4);
		row.add(second).width(90);
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
			items.add(NEW_TRANSFORM);
			for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof TransformConstraintData) items.add(constraint.getName());
			constraintBox.setItems(items);
			constraintBox.setSelected(requestedName != null && items.contains(requestedName, false)
				? requestedName : items.size > 1 ? items.get(1) : NEW_TRANSFORM);
		} finally {
			refreshing = false;
		}
		loadSelection();
	}

	private void loadSelection() {
		refreshing = true;
		try {
			editingConstraint = selectedConstraint();
			sourceBox.setItems(boneNames());
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
		nameField.setText(uniqueName("transform"));
		BoneData constrained = defaultConstrainedBone();
		bonesField.setText(constrained == null ? "" : constrained.getName());
		BoneData source = defaultSource(constrained);
		sourceBox.setSelected(source == null ? null : source.getName());
		setNumbers(new float[] {0f, 0f, 0f, 0f, 0f, 0f}, offsetFields());
		setNumbers(new float[] {1f, 1f, 1f, 1f, 1f, 1f}, mixFields());
		localSourceButton.setChecked(false);
		localTargetButton.setChecked(false);
		additiveButton.setChecked(false);
		clampButton.setChecked(false);
		propertyDraft = SkeletonEditor.defaultTransformProperties();
		refreshPropertiesSummary();
		skinRequiredButton.setChecked(false);
		skinActiveButton.setChecked(false);
	}

	private void loadConstraint(TransformConstraintData constraint, Skin skin) {
		loadSetup(SkeletonEditor.transformConstraintSetup(constraint),
			editor.isTransformConstraintActiveInSkin(constraint, skin));
	}

	private void loadSetup(SkeletonEditor.TransformConstraintSetup setup, boolean active) {
		nameField.setText(setup.name());
		bonesField.setText(joinBoneNames(setup.bones()));
		sourceBox.setSelected(setup.source().getName());
		setNumbers(new float[] {setup.offsetRotation(), setup.offsetX(), setup.offsetY(), setup.offsetScaleX(),
			setup.offsetScaleY(), setup.offsetShearY()}, offsetFields());
		setNumbers(new float[] {setup.mixRotate(), setup.mixX(), setup.mixY(), setup.mixScaleX(),
			setup.mixScaleY(), setup.mixShearY()}, mixFields());
		localSourceButton.setChecked(setup.localSource());
		localTargetButton.setChecked(setup.localTarget());
		additiveButton.setChecked(setup.additive());
		clampButton.setChecked(setup.clamp());
		propertyDraft = List.copyOf(setup.properties());
		refreshPropertiesSummary();
		skinRequiredButton.setChecked(setup.skinRequired());
		skinActiveButton.setChecked(active);
	}

	private TextField[] offsetFields() {
		return new TextField[] {offsetRotationField, offsetXField, offsetYField, offsetScaleXField,
			offsetScaleYField, offsetShearYField};
	}

	private TextField[] mixFields() {
		return new TextField[] {mixRotateField, mixXField, mixYField, mixScaleXField, mixScaleYField, mixShearYField};
	}

	private static void setNumbers(float[] values, TextField[] fields) {
		for (int index = 0; index < fields.length; index++) fields[index].setText(UiNumbers.format(values[index]));
	}

	private void refreshToggleStyles() {
		for (TextButton button : new TextButton[] {localSourceButton, localTargetButton, additiveButton,
			clampButton, skinRequiredButton, skinActiveButton}) refreshToggle(button);
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
		return transformDraft(nameField.getText(), bonesField.getText(), sourceBox.getSelected(),
			skinRequiredButton.isChecked(), skinActiveButton.isChecked(), localSourceButton.isChecked(),
			localTargetButton.isChecked(), additiveButton.isChecked(), clampButton.isChecked(),
			offsetRotationField.getText(), offsetXField.getText(), offsetYField.getText(),
			offsetScaleXField.getText(), offsetScaleYField.getText(), offsetShearYField.getText(),
			mixRotateField.getText(), mixXField.getText(), mixYField.getText(), mixScaleXField.getText(),
			mixScaleYField.getText(), mixShearYField.getText(), propertyDraft);
	}

	private void editProperties() {
		TransformPropertyDialog.show(stage, kit, propertyDraft, properties -> {
			propertyDraft = List.copyOf(properties);
			refreshPropertiesSummary();
			refreshApplyState();
		});
	}

	private void refreshPropertiesSummary() {
		int targets = 0;
		for (TransformFromPropertySetup property : propertyDraft) targets += property.targets().size();
		propertiesSummaryLabel.setText(propertyDraft.size() + " 个来源 · " + targets + " 个映射");
	}

	private void copySettings() {
		if (closed) return;
		try {
			SkeletonEditor.TransformConstraintSetup setup = setupFromControls();
			String reason = editor.validateTransformConstraintSetup(editingConstraint, setup,
				currentSkin(), skinActiveButton.isChecked());
			if (reason != null) throw new IllegalArgumentException(reason);
			ConstraintClipboard.SESSION.copy(setup);
			errorLabel.setText("");
			showStatus("Transform 约束设置已复制", false);
		} catch (RuntimeException error) {
			errorLabel.setText(error.getMessage() == null ? "无法复制约束设置" : error.getMessage());
		}
	}

	private void pasteSettings() {
		if (closed) return;
		try {
			SkeletonEditor.TransformConstraintSetup setup = ConstraintClipboard.SESSION.pasteTransform(doc.data,
				nameField.getText());
			boolean active = setup.skinRequired() && skinActiveButton.isChecked() && currentSkin() != null;
			String reason = editor.validateTransformConstraintSetup(editingConstraint, setup, currentSkin(), active);
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
			"调整 Transform 约束顺序")) return;
		if (onChanged != null) onChanged.run();
		refreshConstraintItems(name);
		showStatus("Transform 约束顺序已调整", false);
	}

	private void submit() {
		if (closed) return;
		try {
			Skin skin = currentSkin();
			SkeletonEditor.TransformConstraintSetup setup = setupFromControls();
			boolean active = skinActiveButton.isChecked();
			String reason = editor.validateTransformConstraintSetup(editingConstraint, setup, skin, active);
			if (reason != null) throw new IllegalArgumentException(reason);
			TransformConstraintData before = editingConstraint;
			SkeletonEditor.TransformConstraintSetup beforeSetup = before == null ? null
				: SkeletonEditor.transformConstraintSetup(before);
			var beforeActivations = before == null ? java.util.List.<Skin>of()
				: editor.constraintActivationSkins(before);
			TransformConstraintData result = before == null
				? editor.addTransformConstraint(setup, skin, active)
				: editor.updateTransformConstraint(before, setup, skin, active);
			if (result == null) throw new IllegalStateException("Transform 约束状态已变化，请重新选择");
			boolean changed = result != before;
			if (changed) ConstraintHistory.recordApplied(history, editor,
				before == null ? "创建 Transform 约束" : "编辑 Transform 约束", before, result, beforeSetup,
				SkeletonEditor.transformConstraintSetup(result), beforeActivations,
				editor.constraintActivationSkins(result), new ConstraintHistory.Operations<>() {
					@Override public TransformConstraintData add(SkeletonEditor.TransformConstraintSetup value) {
						return editor.addTransformConstraint(value, null, false);
					}
					@Override public TransformConstraintData update(TransformConstraintData current,
						SkeletonEditor.TransformConstraintSetup value) {
						return editor.updateTransformConstraint(current, value, null, false);
					}
					@Override public boolean delete(TransformConstraintData current) {
						return editor.deleteTransformConstraint(current);
					}
				});
			if (changed && onChanged != null) onChanged.run();
			refreshConstraintItems(result.getName());
			showStatus(changed ? (before == null ? "Transform 约束已创建" : "Transform 约束已更新")
				: "Transform 约束未变化", false);
		} catch (RuntimeException error) {
			String message = error.getMessage();
			errorLabel.setText(message == null || message.isBlank() ? "无法应用 Transform 约束设置" : message);
		}
	}

	private SkeletonEditor.TransformConstraintSetup setupFromControls() {
		List<BoneData> bones = parseBones(bonesField.getText());
		BoneData source = doc.data.findBone(sourceBox.getSelected());
		float[] offsets = parseFields(offsetFields(), "Transform 偏移");
		float[] mixes = parseFields(mixFields(), "Transform 混合");
		return new SkeletonEditor.TransformConstraintSetup(nameField.getText(), bones, source,
			skinRequiredButton.isChecked(), localSourceButton.isChecked(), localTargetButton.isChecked(),
			additiveButton.isChecked(), clampButton.isChecked(), offsets[0], offsets[1], offsets[2],
			offsets[3], offsets[4], offsets[5], mixes[0], mixes[1], mixes[2], mixes[3], mixes[4], mixes[5],
			propertyDraft);
	}

	private List<BoneData> parseBones(String value) {
		List<BoneData> bones = new ArrayList<>();
		for (String token : normalizeBoneList(value).split(",", -1)) {
			String name = token.trim();
			if (!name.isEmpty()) bones.add(doc.data.findBone(name));
		}
		return bones;
	}

	private static float[] parseFields(TextField[] fields, String label) {
		float[] values = new float[fields.length];
		for (int index = 0; index < fields.length; index++)
			values[index] = parseNumber(fields[index].getText(), label);
		return values;
	}

	private void requestDelete() {
		TransformConstraintData constraint = editingConstraint;
		if (constraint == null) return;
		String reason = editor.getTransformConstraintDeleteBlockReason(constraint);
		if (reason != null) { errorLabel.setText(reason); return; }
		UiDialogs.confirm(stage, kit, "删除 Transform 约束",
			"删除 Transform 约束 " + constraint.getName() + "？"
				+ (history == null ? "此操作无法撤销。" : "可通过撤销恢复。"), "删除", () -> {
				SkeletonEditor.TransformConstraintSetup setup = SkeletonEditor.transformConstraintSetup(constraint);
				var activations = editor.constraintActivationSkins(constraint);
				int index = editor.constraintIndex(constraint);
				if (!editor.deleteTransformConstraint(constraint)) {
					errorLabel.setText("Transform 约束已发生变化，无法删除");
					return;
				}
				ConstraintHistory.recordDeleted(history, editor, "删除 Transform 约束", constraint, setup,
					activations, index, new ConstraintHistory.Operations<>() {
						@Override public TransformConstraintData add(SkeletonEditor.TransformConstraintSetup value) {
							return editor.addTransformConstraint(value, null, false);
						}
						@Override public TransformConstraintData update(TransformConstraintData current,
							SkeletonEditor.TransformConstraintSetup value) {
							return editor.updateTransformConstraint(current, value, null, false);
						}
						@Override public boolean delete(TransformConstraintData current) {
							return editor.deleteTransformConstraint(current);
						}
					});
				if (onChanged != null) onChanged.run();
				refreshConstraintItems(null);
				showStatus("Transform 约束已删除", false);
			});
	}

	private void close() {
		if (closed) return;
		closed = true;
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}

	private TransformConstraintData selectedConstraint() {
		String name = constraintBox.getSelected();
		if (name == null || NEW_TRANSFORM.equals(name)) return null;
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint instanceof TransformConstraintData transform && transform.getName().equals(name))
				return transform;
		return null;
	}

	private Skin currentSkin() {
		return doc.getEditingSkin();
	}

	private Array<String> boneNames() {
		Array<String> names = new Array<>();
		for (BoneData bone : doc.data.getBones()) names.add(bone.getName());
		return names;
	}

	private BoneData defaultConstrainedBone() {
		Bone selected = doc.getSelectedBone();
		if (selected != null && selected.getData().getParent() != null) return selected.getData();
		for (BoneData bone : doc.data.getBones()) if (bone.getParent() != null) return bone;
		return doc.data.getBones().first();
	}

	private BoneData defaultSource(BoneData constrained) {
		if (constrained != null && constrained.getParent() != null) return constrained.getParent();
		for (BoneData bone : doc.data.getBones()) if (bone != constrained) return bone;
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

	static Draft transformDraft(String name, String bones, String source, boolean skinRequired,
		boolean skinActive, boolean localSource, boolean localTarget, boolean additive, boolean clamp,
		String offsetRotation, String offsetX, String offsetY, String offsetScaleX, String offsetScaleY,
		String offsetShearY, String mixRotate, String mixX, String mixY, String mixScaleX,
		String mixScaleY, String mixShearY) {
		return transformDraft(name, bones, source, skinRequired, skinActive, localSource, localTarget,
			additive, clamp, offsetRotation, offsetX, offsetY, offsetScaleX, offsetScaleY, offsetShearY,
			mixRotate, mixX, mixY, mixScaleX, mixScaleY, mixShearY, List.of());
	}

	static Draft transformDraft(String name, String bones, String source, boolean skinRequired,
		boolean skinActive, boolean localSource, boolean localTarget, boolean additive, boolean clamp,
		String offsetRotation, String offsetX, String offsetY, String offsetScaleX, String offsetScaleY,
		String offsetShearY, String mixRotate, String mixX, String mixY, String mixScaleX,
		String mixScaleY, String mixShearY, List<TransformFromPropertySetup> properties) {
		return new Draft(UiDrafts.text(name), normalizeBoneList(bones), UiDrafts.text(source), skinRequired,
			skinActive, localSource, localTarget, additive, clamp, UiDrafts.number(offsetRotation),
			UiDrafts.number(offsetX), UiDrafts.number(offsetY), UiDrafts.number(offsetScaleX),
			UiDrafts.number(offsetScaleY), UiDrafts.number(offsetShearY), UiDrafts.number(mixRotate),
			UiDrafts.number(mixX), UiDrafts.number(mixY), UiDrafts.number(mixScaleX),
			UiDrafts.number(mixScaleY), UiDrafts.number(mixShearY), properties);
	}
}
