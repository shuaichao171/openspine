package com.openspine.ui;

import java.util.function.BiConsumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Constraint;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SliderData;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonEditor;

/** Visual editor for Spine 4.3 Slider constraint setup data. */
final class SliderConstraintDialog {
	static final String MODE_TIME = "时间驱动";
	static final String MODE_PROPERTY = "骨骼属性驱动";
	private static final String NEW_SLIDER = "＋ 新建 Slider";

	record Draft(String name, String animation, String mode, String bone, String property,
		String time, String mix, String sourceOffset, String targetOffset, String scale, String max,
		boolean additive, boolean loop, boolean local, boolean skinRequired, boolean skinActive) {}
	record RangeMapping(float sourceMin, float sourceMax, float frameMin, float frameMax) {}
	record RuntimeMapping(float sourceOffset, float targetOffset, float scale, float max) {}
	static final class RuntimePreview {
		final com.esotericsoftware.spine.Skeleton skeleton;
		final int index;
		final Constraint<?, ?, ?> original;
		final com.esotericsoftware.spine.Slider preview;
		final Skin originalSkin;
		final Skin previewSkin;

		RuntimePreview(com.esotericsoftware.spine.Skeleton skeleton, int index,
			Constraint<?, ?, ?> original, com.esotericsoftware.spine.Slider preview,
			Skin originalSkin, Skin previewSkin) {
			this.skeleton = skeleton;
			this.index = index;
			this.original = original;
			this.preview = preview;
			this.originalSkin = originalSkin;
			this.previewSkin = previewSkin;
		}

		void restore() {
			@SuppressWarnings("rawtypes")
			Array<Constraint> constraints = skeleton.getConstraints();
			int liveIndex = constraints.indexOf(preview, true);
			if (liveIndex >= 0) constraints.removeIndex(liveIndex);
			if (original != null && !constraints.contains(original, true))
				constraints.insert(Math.min(index, constraints.size), original);
			if (previewSkin != null && skeleton.getSkin() == previewSkin) skeleton.setSkin(originalSkin);
			skeleton.updateCache();
		}
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
	private final SelectBox<String> constraintBox, animationBox, modeBox, boneBox, propertyBox;
	private final TextField nameField, timeField, mixField, sourceOffsetField, targetOffsetField,
		scaleField, maxField;
	private final Slider frameSlider, mixSlider;
	private final TextButton newButton, deleteButton, additiveButton, loopButton, localButton,
		skinRequiredButton, skinActiveButton, applyButton;
	private final ConstraintOrderControls.Controls orderControls;
	private final Label currentSkinLabel, errorLabel;
	private SliderData editingConstraint;
	private Draft baseline;
	private SkeletonEditor.SliderConstraintSetup loadedSetup;
	private RangeMapping loadedRange;
	private RuntimePreview runtimePreview;
	private boolean refreshing;
	private boolean closed;

	static void show(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		if (stage == null || doc == null || doc.data == null || doc.data.getAnimations().isEmpty()) return;
		new SliderConstraintDialog(stage, doc, kit, editor, history, onChanged, onStatus).open();
	}

	private SliderConstraintDialog(Stage stage, SkeletonDocument doc, UiKit kit, SkeletonEditor editor,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		this.stage = stage;
		this.doc = doc;
		this.kit = kit;
		this.editor = editor;
		this.history = history;
		this.onChanged = onChanged;
		this.onStatus = onStatus;
		previousKeyboardFocus = stage.getKeyboardFocus();

		window = kit.window("Slider 约束 Setup");
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

		nameField = new TextField("", kit.textFieldStyle);
		nameField.setMaxLength(120);
		addRow("名称", nameField);
		animationBox = kit.selectBox();
		addRow("目标动画", animationBox);
		modeBox = kit.selectBox();
		modeBox.setItems(MODE_TIME, MODE_PROPERTY);
		addRow("驱动模式", modeBox);

		boneBox = kit.selectBox();
		propertyBox = kit.selectBox();
		propertyBox.setItems(propertyLabels());
		Table propertySource = new Table();
		propertySource.add(kit.controlLabel("骨骼")).padRight(4);
		propertySource.add(boneBox).width(150).padRight(12);
		propertySource.add(kit.controlLabel("属性")).padRight(4);
		propertySource.add(propertyBox).width(120);
		addRow("属性来源", propertySource);

		timeField = numberField();
		mixField = numberField();
		frameSlider = new Slider(0f, 1f, 1f, false, kit.sliderStyle);
		frameSlider.setProgrammaticChangeEvents(false);
		mixSlider = new Slider(0f, 1f, 0.01f, false, kit.sliderStyle);
		mixSlider.setProgrammaticChangeEvents(false);
		Table setupValues = new Table();
		setupValues.add(kit.controlLabel("帧")).padRight(4);
		setupValues.add(timeField).width(72).padRight(6);
		setupValues.add(frameSlider).width(150).padRight(12);
		setupValues.add(kit.controlLabel("混合")).padRight(4);
		setupValues.add(mixField).width(64).padRight(6);
		setupValues.add(mixSlider).width(120);
		addRow("实时预览", setupValues);

		sourceOffsetField = numberField();
		targetOffsetField = numberField();
		scaleField = numberField();
		maxField = numberField();
		Table mappingValues = new Table();
		mappingValues.add(kit.controlLabel("来源最小")).padRight(4);
		mappingValues.add(sourceOffsetField).width(72).padRight(8);
		mappingValues.add(kit.controlLabel("来源最大")).padRight(4);
		mappingValues.add(maxField).width(72).padRight(8);
		mappingValues.add(kit.controlLabel("动画帧")).padRight(4);
		mappingValues.add(targetOffsetField).width(58).padRight(3);
		mappingValues.add(kit.controlLabel("到")).padRight(3);
		mappingValues.add(scaleField).width(58);
		addRow("范围映射", mappingValues);

		additiveButton = toggleButton();
		loopButton = toggleButton();
		localButton = toggleButton();
		Table playbackOptions = new Table();
		playbackOptions.add(kit.controlLabel("叠加")).padRight(4);
		playbackOptions.add(additiveButton).width(90).padRight(12);
		playbackOptions.add(kit.controlLabel("循环")).padRight(4);
		playbackOptions.add(loopButton).width(90).padRight(12);
		playbackOptions.add(kit.controlLabel("局部属性")).padRight(4);
		playbackOptions.add(localButton).width(90);
		addRow("播放选项", playbackOptions);

		skinRequiredButton = toggleButton();
		skinActiveButton = toggleButton();
		currentSkinLabel = kit.controlLabel("");
		currentSkinLabel.setEllipsis(true);
		Table skinControls = new Table();
		skinControls.add(kit.controlLabel("皮肤必需")).padRight(4);
		skinControls.add(skinRequiredButton).width(90).padRight(12);
		skinControls.add(currentSkinLabel).width(130).left().padRight(4);
		skinControls.add(skinActiveButton).width(90);
		addRow("皮肤激活", skinControls);

		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(3).width(590).left().padTop(7).row();
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
				if (actor == animationBox) refreshFrameSliderRange();
				if (actor == timeField) syncSliderFromField(timeField, frameSlider);
				if (actor == mixField) syncSliderFromField(mixField, mixSlider);
				refreshControlStates();
				refreshApplyState();
				previewRuntimePose();
			}
		};
		for (Actor actor : new Actor[] {nameField, animationBox, modeBox, boneBox, propertyBox, timeField,
			mixField, sourceOffsetField, targetOffsetField, scaleField, maxField, additiveButton, loopButton,
			localButton, skinRequiredButton, skinActiveButton}) actor.addListener(draftListener);
		frameSlider.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (refreshing) return;
				setFieldFromSlider(timeField, frameSlider);
				refreshApplyState();
				previewRuntimePose();
			}
		});
		mixSlider.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (refreshing) return;
				setFieldFromSlider(mixField, mixSlider);
				refreshApplyState();
				previewRuntimePose();
			}
		});

		constraintBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshing) loadSelection();
			}
		});
		newButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				constraintBox.setSelected(NEW_SLIDER);
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
		window.add(actor).colspan(2).width(470).left().padTop(6).row();
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
			items.add(NEW_SLIDER);
			for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
				if (constraint instanceof SliderData) items.add(constraint.getName());
			constraintBox.setItems(items);
			constraintBox.setSelected(requestedName != null && items.contains(requestedName, false)
				? requestedName : items.size > 1 ? items.get(1) : NEW_SLIDER);
		} finally {
			refreshing = false;
		}
		loadSelection();
	}

	private void loadSelection() {
		restoreRuntimePreview();
		refreshing = true;
		try {
			editingConstraint = selectedConstraint();
			loadedSetup = null;
			loadedRange = null;
			animationBox.setItems(animationNames());
			boneBox.setItems(boneNames());
			Skin skin = currentSkin();
			currentSkinLabel.setText(skin == null ? "无当前皮肤" : "当前：" + skin.getName());
			if (editingConstraint == null) loadNewDefaults();
			else loadConstraint(editingConstraint, skin);
			deleteButton.setDisabled(editingConstraint == null);
			errorLabel.setText("");
			refreshControlStates();
			refreshFrameSliderRange();
			syncSliderFromField(timeField, frameSlider);
			syncSliderFromField(mixField, mixSlider);
			baseline = editingConstraint == null ? null : currentDraft();
		} finally {
			refreshing = false;
		}
		refreshApplyState();
	}

	private void loadNewDefaults() {
		nameField.setText(uniqueName("slider"));
		String animation = preferredAnimationName(animationBox.getItems(), doc.getCurrentAnimationName());
		if (animation != null) animationBox.setSelected(animation);
		modeBox.setSelected(MODE_TIME);
		BoneData bone = defaultBone();
		boneBox.setSelected(bone == null ? null : bone.getName());
		propertyBox.setSelected(propertyLabel(SkeletonEditor.TransformProperty.ROTATE));
		timeField.setText("0");
		mixField.setText("1");
		sourceOffsetField.setText("0");
		targetOffsetField.setText("0");
		scaleField.setText(UiNumbers.format(animationEndFrame(selectedAnimation())));
		maxField.setText("1");
		additiveButton.setChecked(false);
		loopButton.setChecked(false);
		localButton.setChecked(false);
		skinRequiredButton.setChecked(false);
		skinActiveButton.setChecked(false);
	}

	private void loadConstraint(SliderData constraint, Skin skin) {
		loadSetup(SkeletonEditor.sliderConstraintSetup(constraint),
			editor.isSliderConstraintActiveInSkin(constraint, skin));
	}

	private void loadSetup(SkeletonEditor.SliderConstraintSetup setup, boolean active) {
		loadedSetup = setup;
		nameField.setText(setup.name());
		animationBox.setSelected(setup.animation().getName());
		boolean propertyDriven = setup.bone() != null;
		modeBox.setSelected(propertyDriven ? MODE_PROPERTY : MODE_TIME);
		BoneData defaultBone = defaultBone();
		boneBox.setSelected(propertyDriven ? setup.bone().getName()
			: defaultBone == null ? null : defaultBone.getName());
		propertyBox.setSelected(propertyLabel(propertyDriven ? setup.property()
			: SkeletonEditor.TransformProperty.ROTATE));
		timeField.setText(UiNumbers.format(setup.time() * projectFps()));
		mixField.setText(UiNumbers.format(setup.mix()));
		loadedRange = rangeMapping(setup.sourceOffset(), setup.targetOffset(), setup.scale(),
			setup.max(), projectFps());
		sourceOffsetField.setText(UiNumbers.format(loadedRange.sourceMin()));
		maxField.setText(UiNumbers.format(loadedRange.sourceMax()));
		targetOffsetField.setText(UiNumbers.format(loadedRange.frameMin()));
		scaleField.setText(UiNumbers.format(loadedRange.frameMax()));
		additiveButton.setChecked(setup.additive());
		loopButton.setChecked(setup.loop());
		localButton.setChecked(setup.local());
		skinRequiredButton.setChecked(setup.skinRequired());
		skinActiveButton.setChecked(active);
	}

	private void refreshControlStates() {
		boolean propertyDriven = MODE_PROPERTY.equals(modeBox.getSelected());
		boneBox.setDisabled(!propertyDriven);
		propertyBox.setDisabled(!propertyDriven);
		timeField.setDisabled(propertyDriven);
		frameSlider.setDisabled(propertyDriven);
		sourceOffsetField.setDisabled(!propertyDriven);
		targetOffsetField.setDisabled(!propertyDriven);
		scaleField.setDisabled(!propertyDriven);
		maxField.setDisabled(!propertyDriven);
		mixSlider.setDisabled(false);
		localButton.setDisabled(!propertyDriven);
		refreshToggle(additiveButton);
		refreshToggle(loopButton);
		refreshToggle(localButton);
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

	private void refreshFrameSliderRange() {
		float endFrame = Math.max(1f, animationEndFrame(selectedAnimation()));
		boolean wasRefreshing = refreshing;
		refreshing = true;
		try {
			frameSlider.setRange(0f, endFrame);
			frameSlider.setStepSize(1f);
			try {
				float frame = UiNumbers.parseFinite(timeField.getText());
				frameSlider.setValue(Math.max(0f, Math.min(endFrame, frame)));
			} catch (RuntimeException ignored) {
				// Keep incomplete field text while the user is typing.
			}
		} finally {
			refreshing = wasRefreshing;
		}
	}

	private void syncSliderFromField(TextField field, Slider slider) {
		try {
			float value = UiNumbers.parseFinite(field.getText());
			boolean wasRefreshing = refreshing;
			refreshing = true;
			try {
				slider.setValue(Math.max(slider.getMinValue(), Math.min(slider.getMaxValue(), value)));
			} finally {
				refreshing = wasRefreshing;
			}
		} catch (RuntimeException ignored) {
			// Keep incomplete field text while the user is typing.
		}
	}

	private void setFieldFromSlider(TextField field, Slider slider) {
		boolean wasRefreshing = refreshing;
		refreshing = true;
		try {
			field.setText(UiNumbers.format(slider.getValue()));
		} finally {
			refreshing = wasRefreshing;
		}
	}

	private void refreshApplyState() {
		Draft current = currentDraft();
		boolean draftClean = current != null && baseline != null && baseline.equals(current);
		applyButton.setDisabled(selectedAnimation() == null || current == null || draftClean);
		ConstraintOrderControls.refresh(orderControls, editor, editingConstraint, draftClean);
	}

	private Draft currentDraft() {
		return sliderDraft(nameField.getText(), animationBox.getSelected(), modeBox.getSelected(),
			boneBox.getSelected(), propertyBox.getSelected(), timeField.getText(), mixField.getText(),
			sourceOffsetField.getText(), targetOffsetField.getText(), scaleField.getText(), maxField.getText(),
			additiveButton.isChecked(), loopButton.isChecked(), localButton.isChecked(),
			skinRequiredButton.isChecked(), skinActiveButton.isChecked());
	}

	private void copySettings() {
		if (closed) return;
		try {
			SkeletonEditor.SliderConstraintSetup setup = setupFromControls();
			String reason = editor.validateSliderConstraintSetup(editingConstraint, setup,
				currentSkin(), skinActiveButton.isChecked());
			if (reason != null) throw new IllegalArgumentException(reason);
			ConstraintClipboard.SESSION.copy(setup);
			errorLabel.setText("");
			showStatus("Slider 约束设置已复制", false);
		} catch (RuntimeException error) {
			errorLabel.setText(error.getMessage() == null ? "无法复制约束设置" : error.getMessage());
		}
	}

	private void pasteSettings() {
		if (closed) return;
		try {
			SkeletonEditor.SliderConstraintSetup setup = ConstraintClipboard.SESSION.pasteSlider(doc.data,
				nameField.getText());
			boolean active = setup.skinRequired() && skinActiveButton.isChecked() && currentSkin() != null;
			String reason = editor.validateSliderConstraintSetup(editingConstraint, setup, currentSkin(), active);
			if (reason != null) throw new IllegalArgumentException("无法粘贴：" + reason + "，草稿未改变");
			restoreRuntimePreview();
			refreshing = true;
			try {
				loadSetup(setup, active);
				refreshControlStates();
				refreshFrameSliderRange();
				syncSliderFromField(timeField, frameSlider);
				syncSliderFromField(mixField, mixSlider);
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
			"调整 Slider 约束顺序")) return;
		if (onChanged != null) onChanged.run();
		refreshConstraintItems(name);
		showStatus("Slider 约束顺序已调整", false);
	}

	private void submit() {
		if (closed) return;
		try {
			restoreRuntimePreview();
			Skin skin = currentSkin();
			SkeletonEditor.SliderConstraintSetup setup = setupFromControls();
			boolean active = skinActiveButton.isChecked();
			String reason = editor.validateSliderConstraintSetup(editingConstraint, setup, skin, active);
			if (reason != null) throw new IllegalArgumentException(reason);
			SliderData before = editingConstraint;
			SkeletonEditor.SliderConstraintSetup beforeSetup = before == null ? null
				: SkeletonEditor.sliderConstraintSetup(before);
			var beforeActivations = before == null ? java.util.List.<Skin>of()
				: editor.constraintActivationSkins(before);
			SliderData result = before == null ? editor.addSliderConstraint(setup, skin, active)
				: editor.updateSliderConstraint(before, setup, skin, active);
			if (result == null) throw new IllegalStateException("Slider 约束状态已变化，请重新选择");
			boolean changed = result != before;
			if (changed) ConstraintHistory.recordApplied(history, editor,
				before == null ? "创建 Slider 约束" : "编辑 Slider 约束", before, result, beforeSetup,
				SkeletonEditor.sliderConstraintSetup(result), beforeActivations,
				editor.constraintActivationSkins(result), new ConstraintHistory.Operations<>() {
					@Override public SliderData add(SkeletonEditor.SliderConstraintSetup value) {
						return editor.addSliderConstraint(value, null, false);
					}
					@Override public SliderData update(SliderData current,
						SkeletonEditor.SliderConstraintSetup value) {
						return editor.updateSliderConstraint(current, value, null, false);
					}
					@Override public boolean delete(SliderData current) {
						return editor.deleteSliderConstraint(current);
					}
				});
			if (changed && onChanged != null) onChanged.run();
			refreshConstraintItems(result.getName());
			showStatus(changed ? (before == null ? "Slider 约束已创建" : "Slider 约束已更新")
				: "Slider 约束未变化", false);
		} catch (RuntimeException error) {
			String message = error.getMessage();
			errorLabel.setText(message == null || message.isBlank() ? "无法应用 Slider 约束设置" : message);
		}
	}

	private SkeletonEditor.SliderConstraintSetup setupFromControls() {
		Animation animation = doc.data.findAnimation(animationBox.getSelected());
		boolean propertyDriven = MODE_PROPERTY.equals(modeBox.getSelected());
		BoneData bone = propertyDriven ? doc.data.findBone(boneBox.getSelected()) : null;
		SkeletonEditor.TransformProperty property = propertyDriven ? transformProperty(propertyBox.getSelected()) : null;
		float time = propertyDriven ? 0f : setupTime(timeField.getText(), projectFps(), loadedSetup);
		RuntimeMapping runtimeMapping = new RuntimeMapping(0f, 0f, 1f, 0f);
		if (propertyDriven) {
			RangeMapping displayed = displayedRange();
			if (loadedSetup != null && loadedSetup.bone() != null && sameRange(displayed, loadedRange)) {
				runtimeMapping = new RuntimeMapping(loadedSetup.sourceOffset(), loadedSetup.targetOffset(),
					loadedSetup.scale(), loadedSetup.max());
			} else {
				runtimeMapping = runtimeMapping(displayed, projectFps());
			}
		}
		return new SkeletonEditor.SliderConstraintSetup(nameField.getText(), animation,
			skinRequiredButton.isChecked(), additiveButton.isChecked(), loopButton.isChecked(), bone, property,
			runtimeMapping.sourceOffset(), runtimeMapping.targetOffset(), runtimeMapping.scale(), runtimeMapping.max(),
			propertyDriven && localButton.isChecked(), time,
			parseNumber(mixField.getText(), "Slider 混合"));
	}

	/** Preserve runtime seconds when an unchanged frame display has rounded during the FPS conversion. */
	static float setupTime(String frameText, float fps, SkeletonEditor.SliderConstraintSetup loaded) {
		float frames = parseNumber(frameText, "Slider 帧");
		if (loaded != null && loaded.bone() == null && frames == loaded.time() * fps) return loaded.time();
		return frames / fps;
	}

	private void requestDelete() {
		SliderData constraint = editingConstraint;
		if (constraint == null) return;
		restoreRuntimePreview();
		String reason = editor.getSliderConstraintDeleteBlockReason(constraint);
		if (reason != null) { errorLabel.setText(reason); return; }
		UiDialogs.confirm(stage, kit, "删除 Slider 约束",
			"删除 Slider 约束 " + constraint.getName() + "？"
				+ (history == null ? "此操作无法撤销。" : "可通过撤销恢复。"), "删除", () -> {
				SkeletonEditor.SliderConstraintSetup setup = SkeletonEditor.sliderConstraintSetup(constraint);
				var activations = editor.constraintActivationSkins(constraint);
				int index = editor.constraintIndex(constraint);
				if (!editor.deleteSliderConstraint(constraint)) {
					errorLabel.setText("Slider 约束已发生变化，无法删除");
					return;
				}
				ConstraintHistory.recordDeleted(history, editor, "删除 Slider 约束", constraint, setup,
					activations, index, new ConstraintHistory.Operations<>() {
						@Override public SliderData add(SkeletonEditor.SliderConstraintSetup value) {
							return editor.addSliderConstraint(value, null, false);
						}
						@Override public SliderData update(SliderData current,
							SkeletonEditor.SliderConstraintSetup value) {
							return editor.updateSliderConstraint(current, value, null, false);
						}
						@Override public boolean delete(SliderData current) {
							return editor.deleteSliderConstraint(current);
						}
					});
				if (onChanged != null) onChanged.run();
				refreshConstraintItems(null);
				showStatus("Slider 约束已删除", false);
			});
	}

	private void close() {
		if (closed) return;
		closed = true;
		restoreRuntimePreview();
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}

	private SliderData selectedConstraint() {
		String name = constraintBox.getSelected();
		if (name == null || NEW_SLIDER.equals(name)) return null;
		for (ConstraintData<?, ?> constraint : doc.data.getConstraints())
			if (constraint instanceof SliderData slider && slider.getName().equals(name)) return slider;
		return null;
	}

	private Skin currentSkin() {
		return doc.getEditingSkin();
	}

	private Animation selectedAnimation() {
		return doc.data == null || animationBox.getSelected() == null ? null
			: doc.data.findAnimation(animationBox.getSelected());
	}

	private float projectFps() {
		return UiNumbers.effectiveFps(doc.data == null ? 0f : doc.data.getFps());
	}

	private float animationEndFrame(Animation animation) {
		return animation == null ? 0f : Math.max(0f, animation.getDuration() * projectFps());
	}

	private RangeMapping displayedRange() {
		return new RangeMapping(
			parseNumber(sourceOffsetField.getText(), "Slider 来源最小值"),
			parseNumber(maxField.getText(), "Slider 来源最大值"),
			parseNumber(targetOffsetField.getText(), "Slider 动画起始帧"),
			parseNumber(scaleField.getText(), "Slider 动画结束帧"));
	}

	private void previewRuntimePose() {
		if (refreshing || closed || doc.data == null || doc.skeleton == null) return;
		removeRuntimePreview();
		try {
			Skin skin = currentSkin();
			SkeletonEditor.SliderConstraintSetup setup = setupFromControls();
			boolean active = skinActiveButton.isChecked();
			String reason = editor.validateSliderConstraintSetup(editingConstraint, setup, skin, active);
			if (reason != null) throw new IllegalArgumentException(reason);
			runtimePreview = installRuntimePreview(doc.skeleton, editingConstraint, setup, skin, active);
			refreshRuntimePose(doc);
		} catch (RuntimeException ignored) {
			refreshRuntimePose(doc);
			// Incomplete or temporarily invalid controls show the original runtime pose.
		}
	}

	private void restoreRuntimePreview() {
		if (!removeRuntimePreview()) return;
		refreshRuntimePose(doc);
	}

	private boolean removeRuntimePreview() {
		RuntimePreview preview = runtimePreview;
		runtimePreview = null;
		if (preview == null) return false;
		preview.restore();
		return true;
	}

	static void refreshRuntimePose(SkeletonDocument document) {
		if (document == null || document.skeleton == null) return;
		// Runtime constraints changed, so any cached deterministic simulation is stale.
		document.markPoseChanged();
		if (!document.refreshCurrentAnimationPose()) {
			document.skeleton.setupPose();
			SkeletonDocument.updateWorldTransform(document.skeleton, Physics.pose,
				document.areConstraintsEnabled());
		}
	}

	static RuntimePreview installRuntimePreview(com.esotericsoftware.spine.Skeleton skeleton,
		SliderData existing, SkeletonEditor.SliderConstraintSetup setup,
		Skin activationSkin, boolean activeInSkin) {
		if (skeleton == null || setup == null)
			throw new IllegalArgumentException("Slider runtime preview requires a skeleton and setup");
		@SuppressWarnings("rawtypes")
		Array<Constraint> constraints = skeleton.getConstraints();
		int index = constraints.size;
		Constraint<?, ?, ?> original = null;
		if (existing != null) {
			for (int candidate = 0; candidate < constraints.size; candidate++) {
				Constraint<?, ?, ?> constraint = constraints.get(candidate);
				if (constraint.getData() != existing) continue;
				index = candidate;
				original = constraint;
				break;
			}
			if (original == null) throw new IllegalStateException("Slider runtime constraint was not found");
		}
		SliderData previewData = SkeletonEditor.sliderConstraintData(setup);
		com.esotericsoftware.spine.Slider preview = previewData.create(skeleton);
		Skin originalSkin = skeleton.getSkin();
		Skin previewSkin = null;
		if (previewData.getSkinRequired() && activeInSkin && activationSkin != null) {
			previewSkin = new Skin("OpenSpine Slider preview");
			previewSkin.addSkin(originalSkin == null ? activationSkin : originalSkin);
			previewSkin.getConstraints().add(previewData);
			skeleton.setSkin(previewSkin);
		}
		try {
			if (original == null) constraints.add(preview); else constraints.set(index, preview);
			skeleton.updateCache();
			preview.setupPose();
			return new RuntimePreview(skeleton, index, original, preview, originalSkin, previewSkin);
		} catch (RuntimeException error) {
			constraints.removeValue(preview, true);
			if (original != null && !constraints.contains(original, true))
				constraints.insert(Math.min(index, constraints.size), original);
			if (previewSkin != null && skeleton.getSkin() == previewSkin) skeleton.setSkin(originalSkin);
			skeleton.updateCache();
			throw error;
		}
	}

	private Array<String> animationNames() {
		Array<String> names = new Array<>();
		for (Animation animation : doc.data.getAnimations()) names.add(animation.getName());
		return names;
	}

	static String preferredAnimationName(Array<String> names, String current) {
		if (names == null || names.isEmpty()) return null;
		return current != null && names.contains(current, false) ? current : names.first();
	}

	private Array<String> boneNames() {
		Array<String> names = new Array<>();
		for (BoneData bone : doc.data.getBones()) names.add(bone.getName());
		return names;
	}

	private BoneData defaultBone() {
		Bone selected = doc.getSelectedBone();
		if (selected != null) return selected.getData();
		return doc.data.getBones().isEmpty() ? null : doc.data.getBones().first();
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

	static RangeMapping rangeMapping(float sourceOffset, float targetOffset, float scale, float max, float fps) {
		if (!Float.isFinite(sourceOffset) || !Float.isFinite(targetOffset) || !Float.isFinite(scale)
			|| !Float.isFinite(max) || !Float.isFinite(fps) || fps <= 0f)
			throw new IllegalArgumentException("Slider 范围映射必须使用有限数值和有效 FPS");
		return new RangeMapping(sourceOffset, max, targetOffset * fps,
			(targetOffset + (max - sourceOffset) * scale) * fps);
	}

	static RuntimeMapping runtimeMapping(RangeMapping range, float fps) {
		if (range == null || !Float.isFinite(range.sourceMin()) || !Float.isFinite(range.sourceMax())
			|| !Float.isFinite(range.frameMin()) || !Float.isFinite(range.frameMax())
			|| !Float.isFinite(fps) || fps <= 0f)
			throw new IllegalArgumentException("Slider 范围映射必须使用有限数值和有效 FPS");
		float sourceExtent = range.sourceMax() - range.sourceMin();
		if (sourceExtent == 0f) throw new IllegalArgumentException("Slider 来源最小值和最大值不能相同");
		return new RuntimeMapping(range.sourceMin(), range.frameMin() / fps,
			((range.frameMax() - range.frameMin()) / fps) / sourceExtent, range.sourceMax());
	}

	private static boolean sameRange(RangeMapping first, RangeMapping second) {
		return first != null && second != null
			&& Float.compare(first.sourceMin(), second.sourceMin()) == 0
			&& Float.compare(first.sourceMax(), second.sourceMax()) == 0
			&& Float.compare(first.frameMin(), second.frameMin()) == 0
			&& Float.compare(first.frameMax(), second.frameMax()) == 0;
	}

	static Draft sliderDraft(String name, String animation, String mode, String bone, String property,
		String time, String mix, String sourceOffset, String targetOffset, String scale, String max,
		boolean additive, boolean loop, boolean local, boolean skinRequired, boolean skinActive) {
		String normalizedMode = UiDrafts.text(mode);
		boolean propertyDriven = MODE_PROPERTY.equals(normalizedMode);
		return new Draft(UiDrafts.text(name), UiDrafts.text(animation), normalizedMode,
			propertyDriven ? UiDrafts.text(bone) : "", propertyDriven ? UiDrafts.text(property) : "",
			propertyDriven ? "0" : UiDrafts.number(time), UiDrafts.number(mix),
			propertyDriven ? UiDrafts.number(sourceOffset) : "0",
			propertyDriven ? UiDrafts.number(targetOffset) : "0",
			propertyDriven ? UiDrafts.number(scale) : "1",
			propertyDriven ? UiDrafts.number(max) : "0", additive, loop, propertyDriven && local,
			skinRequired, skinActive);
	}

	static String[] propertyLabels() {
		return new String[] {"旋转", "X", "Y", "缩放 X", "缩放 Y", "剪切 Y"};
	}

	static String propertyLabel(SkeletonEditor.TransformProperty property) {
		if (property == null) return null;
		return switch (property) {
		case ROTATE -> "旋转";
		case X -> "X";
		case Y -> "Y";
		case SCALE_X -> "缩放 X";
		case SCALE_Y -> "缩放 Y";
		case SHEAR_Y -> "剪切 Y";
		};
	}

	static SkeletonEditor.TransformProperty transformProperty(String label) {
		if ("旋转".equals(label)) return SkeletonEditor.TransformProperty.ROTATE;
		if ("X".equals(label)) return SkeletonEditor.TransformProperty.X;
		if ("Y".equals(label)) return SkeletonEditor.TransformProperty.Y;
		if ("缩放 X".equals(label)) return SkeletonEditor.TransformProperty.SCALE_X;
		if ("缩放 Y".equals(label)) return SkeletonEditor.TransformProperty.SCALE_Y;
		return "剪切 Y".equals(label) ? SkeletonEditor.TransformProperty.SHEAR_Y : null;
	}
}
