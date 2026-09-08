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
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skin;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonEditor;

import java.util.function.BiConsumer;

/** Modal editor for Spine 4.3 Physics constraint setup data and skin activation. */
final class PhysicsConstraintDialog {
	static final String NEW_PHYSICS = "（新建 Physics）";

	record Draft(String name, String bone, boolean skinRequired, String scaleYMode,
		String x, String y, String rotate, String scaleX, String shearX, String limit, String fps,
		String inertia, String strength, String damping, String mass, String wind, String gravity, String mix,
		boolean inertiaGlobal, boolean strengthGlobal, boolean dampingGlobal, boolean massGlobal,
		boolean windGlobal, boolean gravityGlobal, boolean mixGlobal, boolean skinActive) {}

	private final Stage stage;
	private final SkeletonDocument doc;
	private final UiKit kit;
	private final SkeletonEditor editor;
	private final EditHistory history;
	private final Runnable onChanged;
	private final BiConsumer<String, Boolean> onStatus;
	private final Actor previousKeyboardFocus;
	private final Window window;
	private final SelectBox<String> constraintBox, boneBox, scaleYBox;
	private final TextField nameField, xField, yField, rotateField, scaleXField, shearXField, limitField, fpsField,
		inertiaField, strengthField, dampingField, massField, windField, gravityField, mixField;
	private final TextButton newButton, deleteButton, simulateButton, deterministicButton,
		skinRequiredButton, skinActiveButton, applyButton;
	private final ConstraintOrderControls.Controls orderControls;
	private final TextButton inertiaGlobalButton, strengthGlobalButton, dampingGlobalButton, massGlobalButton,
		windGlobalButton, gravityGlobalButton, mixGlobalButton;
	private final Label currentSkinLabel, errorLabel;
	private PhysicsConstraintData editingConstraint;
	private Draft baseline;
	private boolean refreshing;
	private boolean closed;

	static void show(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		if (stage == null || doc == null || doc.data == null || doc.data.getBones().isEmpty()) return;
		new PhysicsConstraintDialog(stage, doc, kit, editor, history, onChanged, onStatus).open();
	}

	private PhysicsConstraintDialog(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		this.stage = stage;
		this.doc = doc;
		this.kit = kit;
		this.editor = editor;
		this.history = history;
		this.onChanged = onChanged;
		this.onStatus = onStatus;
		previousKeyboardFocus = stage.getKeyboardFocus();

		window = kit.window("Physics 约束 Setup");
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

		simulateButton = toggleButton();
		deterministicButton = toggleButton();
		simulateButton.setChecked(doc.isPhysicsSimulationEnabled());
		deterministicButton.setChecked(doc.isPhysicsDeterministic());
		Table previewControls = new Table();
		previewControls.add(kit.controlLabel("Simulate")).padRight(5);
		previewControls.add(simulateButton).width(96).padRight(14);
		previewControls.add(kit.controlLabel("Deterministic")).padRight(5);
		previewControls.add(deterministicButton).width(96);
		addRow("物理预览", previewControls);
		refreshPreviewStyles();
		simulateButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (refreshing) return;
				doc.setPhysicsSimulationEnabled(simulateButton.isChecked());
				refreshPreviewStyles();
				showStatus(simulateButton.isChecked() ? "Physics Simulate 已开启" : "Physics Simulate 已关闭", false);
			}
		});
		deterministicButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (refreshing) return;
				doc.setPhysicsDeterministic(deterministicButton.isChecked());
				refreshPreviewStyles();
				showStatus(deterministicButton.isChecked()
					? "Physics Deterministic 已开启，预览可能变慢"
					: "Physics Deterministic 已关闭", false);
			}
		});

		nameField = new TextField("", kit.textFieldStyle);
		nameField.setMaxLength(120);
		addRow("名称", nameField);
		boneBox = kit.selectBox();
		scaleYBox = kit.selectBox();
		scaleYBox.setItems(IkConstraintDialog.scaleYLabels());
		Table target = new Table();
		target.add(kit.controlLabel("骨骼")).padRight(4);
		target.add(boneBox).width(225).padRight(14);
		target.add(kit.controlLabel("缩放 Y")).padRight(4);
		target.add(scaleYBox).width(90);
		addRow("目标", target);

		xField = numberField();
		yField = numberField();
		rotateField = numberField();
		scaleXField = numberField();
		shearXField = numberField();
		limitField = numberField();
		fpsField = integerField();
		addRow("位置/旋转影响", numericGroup(new String[] {"X", "Y", "旋转"},
			new TextField[] {xField, yField, rotateField}));
		addRow("缩放/剪切影响", numericGroup(new String[] {"缩放 X", "剪切 X"},
			new TextField[] {scaleXField, shearXField}));
		addRow("解算", numericGroup(new String[] {"限制", "FPS"}, new TextField[] {limitField, fpsField}));

		inertiaField = numberField();
		strengthField = numberField();
		dampingField = numberField();
		massField = numberField();
		windField = numberField();
		gravityField = numberField();
		mixField = numberField();
		inertiaGlobalButton = scopeButton();
		strengthGlobalButton = scopeButton();
		dampingGlobalButton = scopeButton();
		massGlobalButton = scopeButton();
		windGlobalButton = scopeButton();
		gravityGlobalButton = scopeButton();
		mixGlobalButton = scopeButton();
		addParameterRow("惯性", inertiaField, inertiaGlobalButton);
		addParameterRow("强度", strengthField, strengthGlobalButton);
		addParameterRow("阻尼", dampingField, dampingGlobalButton);
		addParameterRow("质量", massField, massGlobalButton);
		addParameterRow("风力", windField, windGlobalButton);
		addParameterRow("重力", gravityField, gravityGlobalButton);
		addParameterRow("混合", mixField, mixGlobalButton);

		skinRequiredButton = toggleButton();
		skinActiveButton = toggleButton();
		currentSkinLabel = kit.controlLabel("");
		currentSkinLabel.setEllipsis(true);
		Table skinControls = new Table();
		skinControls.add(kit.controlLabel("皮肤必需")).padRight(4);
		skinControls.add(skinRequiredButton).width(90).padRight(12);
		skinControls.add(currentSkinLabel).width(120).left().padRight(4);
		skinControls.add(skinActiveButton).width(90);
		addRow("皮肤激活", skinControls);

		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(3).width(535).left().padTop(7).row();
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
				constraintBox.setSelected(NEW_PHYSICS);
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
		window.add(kit.controlLabel(label)).right().padRight(7).padTop(5);
		window.add(actor).colspan(2).width(420).left().padTop(5).row();
	}

	private void addParameterRow(String label, TextField field, TextButton scope) {
		Table row = new Table();
		row.add(field).width(130).padRight(12);
		row.add(kit.controlLabel("时间线作用域")).padRight(5);
		row.add(scope).width(90);
		addRow(label, row);
	}

	private Table numericGroup(String[] labels, TextField[] fields) {
		Table row = new Table();
		for (int index = 0; index < labels.length; index++) {
			if (index > 0) row.add().width(10);
			row.add(kit.controlLabel(labels[index])).padRight(4);
			row.add(fields[index]).width(82);
		}
		return row;
	}

	private TextField numberField() {
		TextField field = new TextField("", kit.textFieldStyle);
		field.setMaxLength(24);
		field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+'
			|| c == '.' || c == 'e' || c == 'E');
		return field;
	}

	private TextField integerField() {
		TextField field = new TextField("", kit.textFieldStyle);
		field.setMaxLength(10);
		field.setTextFieldFilter((textField, c) -> Character.isDigit(c));
		return field;
	}

	private TextButton toggleButton() {
		TextButton button = new TextButton("已关闭", kit.toolButton);
		button.setProgrammaticChangeEvents(false);
		return button;
	}

	private TextButton scopeButton() {
		TextButton button = new TextButton("局部", kit.toolButton);
		button.setProgrammaticChangeEvents(false);
		return button;
	}

	private Actor[] draftActors() {
		return new Actor[] {nameField, boneBox, scaleYBox, xField, yField, rotateField, scaleXField, shearXField,
			limitField, fpsField, inertiaField, strengthField, dampingField, massField, windField, gravityField,
			mixField, inertiaGlobalButton, strengthGlobalButton, dampingGlobalButton, massGlobalButton,
			windGlobalButton, gravityGlobalButton, mixGlobalButton, skinRequiredButton, skinActiveButton};
	}

	private void refreshConstraintItems(String requestedName) {
		refreshing = true;
		try {
			Array<String> items = new Array<>();
			items.add(NEW_PHYSICS);
			for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof PhysicsConstraintData) items.add(constraint.getName());
			constraintBox.setItems(items);
			constraintBox.setSelected(requestedName != null && items.contains(requestedName, false)
				? requestedName : items.size > 1 ? items.get(1) : NEW_PHYSICS);
		} finally {
			refreshing = false;
		}
		loadSelection();
	}

	private void loadSelection() {
		refreshing = true;
		try {
			editingConstraint = selectedConstraint();
			boneBox.setItems(boneNames());
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
		nameField.setText(uniqueName("physics"));
		BoneData bone = defaultBone();
		boneBox.setSelected(bone == null ? null : bone.getName());
		scaleYBox.setSelected(IkConstraintDialog.scaleYLabel(ScaleYMode.none));
		setNumbers(new float[] {0f, 0f, 0f, 0f, 0f, 5000f},
			new TextField[] {xField, yField, rotateField, scaleXField, shearXField, limitField});
		fpsField.setText("60");
		setNumbers(new float[] {0.5f, 100f, 0.85f, 1f, 0f, 0f, 1f}, parameterFields());
		for (TextButton button : scopeButtons()) button.setChecked(false);
		skinRequiredButton.setChecked(false);
		skinActiveButton.setChecked(false);
	}

	private void loadConstraint(PhysicsConstraintData constraint, Skin skin) {
		loadSetup(SkeletonEditor.physicsConstraintSetup(constraint),
			editor.isPhysicsConstraintActiveInSkin(constraint, skin));
	}

	private void loadSetup(SkeletonEditor.PhysicsConstraintSetup setup, boolean active) {
		nameField.setText(setup.name());
		boneBox.setSelected(setup.bone().getName());
		scaleYBox.setSelected(IkConstraintDialog.scaleYLabel(setup.scaleYMode()));
		setNumbers(new float[] {setup.x(), setup.y(), setup.rotate(), setup.scaleX(), setup.shearX(), setup.limit()},
			new TextField[] {xField, yField, rotateField, scaleXField, shearXField, limitField});
		fpsField.setText(Integer.toString(setup.fps()));
		setNumbers(new float[] {setup.inertia(), setup.strength(), setup.damping(), setup.mass(), setup.wind(),
			setup.gravity(), setup.mix()}, parameterFields());
		boolean[] globals = {setup.inertiaGlobal(), setup.strengthGlobal(), setup.dampingGlobal(),
			setup.massGlobal(), setup.windGlobal(), setup.gravityGlobal(), setup.mixGlobal()};
		TextButton[] buttons = scopeButtons();
		for (int index = 0; index < buttons.length; index++) buttons[index].setChecked(globals[index]);
		skinRequiredButton.setChecked(setup.skinRequired());
		skinActiveButton.setChecked(active);
	}

	private TextField[] parameterFields() {
		return new TextField[] {inertiaField, strengthField, dampingField, massField, windField, gravityField, mixField};
	}

	private TextButton[] scopeButtons() {
		return new TextButton[] {inertiaGlobalButton, strengthGlobalButton, dampingGlobalButton, massGlobalButton,
			windGlobalButton, gravityGlobalButton, mixGlobalButton};
	}

	private static void setNumbers(float[] values, TextField[] fields) {
		for (int index = 0; index < fields.length; index++) fields[index].setText(UiNumbers.format(values[index]));
	}

	private void refreshToggleStyles() {
		for (TextButton button : scopeButtons()) {
			button.setText(button.isChecked() ? "全局" : "局部");
			button.setStyle(button.isChecked() ? kit.activeToolButton : kit.toolButton);
		}
		refreshToggle(skinRequiredButton);
		refreshToggle(skinActiveButton);
		boolean canActivate = skinRequiredButton.isChecked() && currentSkin() != null;
		skinActiveButton.setDisabled(!canActivate);
		if (!canActivate && currentSkin() == null) skinActiveButton.setText("无皮肤");
	}

	private void refreshPreviewStyles() {
		simulateButton.setText(simulateButton.isChecked() ? "已开启" : "已关闭");
		simulateButton.setStyle(simulateButton.isChecked() ? kit.activeToolButton : kit.toolButton);
		deterministicButton.setText(deterministicButton.isChecked() ? "已开启" : "已关闭");
		deterministicButton.setStyle(deterministicButton.isChecked() ? kit.activeToolButton : kit.toolButton);
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
		return physicsDraft(nameField.getText(), boneBox.getSelected(), skinRequiredButton.isChecked(),
			scaleYBox.getSelected(), xField.getText(), yField.getText(), rotateField.getText(), scaleXField.getText(),
			shearXField.getText(), limitField.getText(), fpsField.getText(), inertiaField.getText(), strengthField.getText(),
			dampingField.getText(), massField.getText(), windField.getText(), gravityField.getText(), mixField.getText(),
			inertiaGlobalButton.isChecked(), strengthGlobalButton.isChecked(), dampingGlobalButton.isChecked(),
			massGlobalButton.isChecked(), windGlobalButton.isChecked(), gravityGlobalButton.isChecked(),
			mixGlobalButton.isChecked(), skinActiveButton.isChecked());
	}

	private void copySettings() {
		if (closed) return;
		try {
			SkeletonEditor.PhysicsConstraintSetup setup = setupFromControls();
			String reason = editor.validatePhysicsConstraintSetup(editingConstraint, setup,
				currentSkin(), skinActiveButton.isChecked());
			if (reason != null) throw new IllegalArgumentException(reason);
			ConstraintClipboard.SESSION.copy(setup);
			errorLabel.setText("");
			showStatus("Physics 约束设置已复制", false);
		} catch (RuntimeException error) {
			errorLabel.setText(error.getMessage() == null ? "无法复制约束设置" : error.getMessage());
		}
	}

	private void pasteSettings() {
		if (closed) return;
		try {
			SkeletonEditor.PhysicsConstraintSetup setup = ConstraintClipboard.SESSION.pastePhysics(doc.data,
				nameField.getText());
			boolean active = setup.skinRequired() && skinActiveButton.isChecked() && currentSkin() != null;
			String reason = editor.validatePhysicsConstraintSetup(editingConstraint, setup, currentSkin(), active);
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
			"调整 Physics 约束顺序")) return;
		if (onChanged != null) onChanged.run();
		refreshConstraintItems(name);
		showStatus("Physics 约束顺序已调整", false);
	}

	private void submit() {
		if (closed) return;
		try {
			Skin skin = currentSkin();
			SkeletonEditor.PhysicsConstraintSetup setup = setupFromControls();
			boolean active = skinActiveButton.isChecked();
			String reason = editor.validatePhysicsConstraintSetup(editingConstraint, setup, skin, active);
			if (reason != null) throw new IllegalArgumentException(reason);
			PhysicsConstraintData before = editingConstraint;
			SkeletonEditor.PhysicsConstraintSetup beforeSetup = before == null ? null
				: SkeletonEditor.physicsConstraintSetup(before);
			var beforeActivations = before == null ? java.util.List.<Skin>of()
				: editor.constraintActivationSkins(before);
			PhysicsConstraintData result = before == null
				? editor.addPhysicsConstraint(setup, skin, active)
				: editor.updatePhysicsConstraint(before, setup, skin, active);
			if (result == null) throw new IllegalStateException("Physics 约束状态已变化，请重新选择");
			boolean changed = result != before;
			if (changed) ConstraintHistory.recordApplied(history, editor,
				before == null ? "创建 Physics 约束" : "编辑 Physics 约束", before, result, beforeSetup,
				SkeletonEditor.physicsConstraintSetup(result), beforeActivations,
				editor.constraintActivationSkins(result), new ConstraintHistory.Operations<>() {
					@Override public PhysicsConstraintData add(SkeletonEditor.PhysicsConstraintSetup value) {
						return editor.addPhysicsConstraint(value, null, false);
					}
					@Override public PhysicsConstraintData update(PhysicsConstraintData current,
						SkeletonEditor.PhysicsConstraintSetup value) {
						return editor.updatePhysicsConstraint(current, value, null, false);
					}
					@Override public boolean delete(PhysicsConstraintData current) {
						return editor.deletePhysicsConstraint(current);
					}
				});
			if (changed && onChanged != null) onChanged.run();
			refreshConstraintItems(result.getName());
			showStatus(changed ? (before == null ? "Physics 约束已创建" : "Physics 约束已更新")
				: "Physics 约束未变化", false);
		} catch (RuntimeException error) {
			String message = error.getMessage();
			errorLabel.setText(message == null || message.isBlank() ? "无法应用 Physics 约束设置" : message);
		}
	}

	private SkeletonEditor.PhysicsConstraintSetup setupFromControls() {
		float[] influences = parseFields(new TextField[] {xField, yField, rotateField, scaleXField, shearXField,
			limitField}, "Physics 影响参数");
		float[] parameters = parseFields(parameterFields(), "Physics 参数");
		return new SkeletonEditor.PhysicsConstraintSetup(nameField.getText(),
			doc.data.findBone(boneBox.getSelected()), skinRequiredButton.isChecked(),
			IkConstraintDialog.scaleYMode(scaleYBox.getSelected()), influences[0], influences[1], influences[2],
			influences[3], influences[4], influences[5], parsePositiveInteger(fpsField.getText(), "Physics FPS"),
			parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6],
			inertiaGlobalButton.isChecked(), strengthGlobalButton.isChecked(), dampingGlobalButton.isChecked(),
			massGlobalButton.isChecked(), windGlobalButton.isChecked(), gravityGlobalButton.isChecked(),
			mixGlobalButton.isChecked());
	}

	private static float[] parseFields(TextField[] fields, String label) {
		float[] values = new float[fields.length];
		for (int index = 0; index < fields.length; index++) values[index] = parseNumber(fields[index].getText(), label);
		return values;
	}

	private void requestDelete() {
		PhysicsConstraintData constraint = editingConstraint;
		if (constraint == null) return;
		String reason = editor.getPhysicsConstraintDeleteBlockReason(constraint);
		if (reason != null) { errorLabel.setText(reason); return; }
		UiDialogs.confirm(stage, kit, "删除 Physics 约束",
			"删除 Physics 约束 " + constraint.getName() + "？"
				+ (history == null ? "此操作无法撤销。" : "可通过撤销恢复。"), "删除", () -> {
				SkeletonEditor.PhysicsConstraintSetup setup = SkeletonEditor.physicsConstraintSetup(constraint);
				var activations = editor.constraintActivationSkins(constraint);
				int index = editor.constraintIndex(constraint);
				if (!editor.deletePhysicsConstraint(constraint)) {
					errorLabel.setText("Physics 约束已发生变化，无法删除");
					return;
				}
				ConstraintHistory.recordDeleted(history, editor, "删除 Physics 约束", constraint, setup,
					activations, index, new ConstraintHistory.Operations<>() {
						@Override public PhysicsConstraintData add(SkeletonEditor.PhysicsConstraintSetup value) {
							return editor.addPhysicsConstraint(value, null, false);
						}
						@Override public PhysicsConstraintData update(PhysicsConstraintData current,
							SkeletonEditor.PhysicsConstraintSetup value) {
							return editor.updatePhysicsConstraint(current, value, null, false);
						}
						@Override public boolean delete(PhysicsConstraintData current) {
							return editor.deletePhysicsConstraint(current);
						}
					});
				if (onChanged != null) onChanged.run();
				refreshConstraintItems(null);
				showStatus("Physics 约束已删除", false);
			});
	}

	private void close() {
		if (closed) return;
		closed = true;
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}

	private PhysicsConstraintData selectedConstraint() {
		String name = constraintBox.getSelected();
		if (name == null || NEW_PHYSICS.equals(name)) return null;
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint instanceof PhysicsConstraintData physics && physics.getName().equals(name)) return physics;
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

	private BoneData defaultBone() {
		Bone selected = doc.getSelectedBone();
		if (selected != null) return selected.getData();
		return doc.data.getBones().first();
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

	private static int parsePositiveInteger(String value, String label) {
		try {
			long parsed = Long.parseLong(value == null ? "" : value.trim());
			if (parsed <= 0 || parsed > Integer.MAX_VALUE) throw new NumberFormatException();
			return (int)parsed;
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(label + "必须是正整数", error);
		}
	}

	static Draft physicsDraft(String name, String bone, boolean skinRequired, String scaleYMode,
		String x, String y, String rotate, String scaleX, String shearX, String limit, String fps,
		String inertia, String strength, String damping, String mass, String wind, String gravity, String mix,
		boolean inertiaGlobal, boolean strengthGlobal, boolean dampingGlobal, boolean massGlobal,
		boolean windGlobal, boolean gravityGlobal, boolean mixGlobal, boolean skinActive) {
		return new Draft(UiDrafts.text(name), UiDrafts.text(bone), skinRequired, UiDrafts.text(scaleYMode),
			UiDrafts.number(x), UiDrafts.number(y), UiDrafts.number(rotate), UiDrafts.number(scaleX),
			UiDrafts.number(shearX), UiDrafts.number(limit), UiDrafts.number(fps), UiDrafts.number(inertia),
			UiDrafts.number(strength), UiDrafts.number(damping), UiDrafts.number(mass), UiDrafts.number(wind),
			UiDrafts.number(gravity), UiDrafts.number(mix), inertiaGlobal, strengthGlobal, dampingGlobal,
			massGlobal, windGlobal, gravityGlobal, mixGlobal, skinActive);
	}
}
