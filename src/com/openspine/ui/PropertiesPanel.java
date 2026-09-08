package com.openspine.ui;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.openspine.model.EditHistory;
import com.openspine.model.AnimationEditor;
import com.openspine.model.BoneSelectionTransform;
import com.openspine.model.BoneCoordinateSpace;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonStructureSnapshot;
import com.openspine.model.SkeletonEditor.BonePoseState;
import com.openspine.model.SkeletonEditor.BoneSetupState;

/** Numeric inspector for the selected bone's authored pose. */
public class PropertiesPanel extends Table {
	private static final String INHERIT_NORMAL = "全部";
	private static final String INHERIT_TRANSLATION = "仅平移";
	private static final String INHERIT_NO_ROTATION = "无旋转或反射";
	private static final String INHERIT_NO_SCALE = "无缩放";
	private static final String INHERIT_NO_SCALE_REFLECTION = "无缩放或反射";

	private final SkeletonDocument doc;
	private final UiKit kit;
	private final SkeletonEditor editor;
	private final Label nameLabel;
	private final TextField boneNameField;
	private final TextButton resetPoseBtn;
	private final TextButton applySetupBtn;
	private final TextButton parentSpaceBtn, worldSpaceBtn, localSpaceBtn;
	private final Label coordinateHelp;
	private final FloatRow xRow, yRow, rotRow, sxRow, syRow, shearXRow, shearYRow;
	private final SetupLengthRow lengthRow;
	private final Label inheritLabel, skinRequiredLabel;
	private final SelectBox<String> inheritBox;
	private final TextButton inheritRotationBtn, inheritScaleBtn, inheritReflectionBtn;
	private final TextButton skinRequiredBtn;
	private final TextField boneColorField, boneIconField, boneIconSizeField, boneIconRotationField;
	private final Slider boneIconSizeSlider;
	private final Table boneColorSwatch;
	private final TextButton boneVisibleBtn, boneSelectableBtn, boneNameVisibleBtn, boneAppearanceApplyBtn;
	private final TextButton newBoneBtn, parentBtn, splitBtn, skinBtn;
	private boolean updating;
	private boolean pendingSkinRequired;
	private BoneData skinRequiredDraftBone;
	private boolean skinRequiredDraftDirty;
	private BoneData lengthDraftBone;
	private boolean lengthDraftDirty;
	private EditHistory history;
	private Runnable onPoseEdited;
	private BiFunction<List<Bone>, AnimationEditor.Prop, Boolean> onBoneTransformFinished;
	private Runnable onDataChanged;
	private Runnable onReversibleDataChanged;
	private Bone activeEditBone;
	private BoneSelectionTransform activeSelectionEdit;
	private Value activeEditValue;
	private boolean activeEditChanged;
	private float activeMoveDelta;
	private BoneCoordinateSpace coordinateSpace = BoneCoordinateSpace.PARENT;
	private BoneCoordinateSpace activeCoordinateSpace = BoneCoordinateSpace.PARENT;
	private Consumer<BoneCoordinateSpace> onCoordinateSpaceChanged;
	private BiConsumer<String, Boolean> onStatus;

	public PropertiesPanel(SkeletonDocument doc, UiKit kit, SkeletonEditor editor) {
		this.doc = doc;
		this.kit = kit;
		this.editor = editor;
		setBackground(kit.panelBg);
		top();

		Label title = new Label("骨骼属性", kit.labelStyle);
		title.setColor(0.75f, 0.8f, 0.9f, 1f);
		resetPoseBtn = new TextButton("重置", kit.toolButton);
		resetPoseBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { resetSelectedBonePose(); }
		});
		applySetupBtn = new TextButton("设为 Setup", kit.toolButton);
		applySetupBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { applyCurrentPoseAsSetup(); }
		});
		Table header = new Table();
		header.add(title).left().growX().minWidth(0f).padLeft(2f);
		header.add(resetPoseBtn).right().width(58).height(24).pad(2);
		header.add(applySetupBtn).right().width(92).height(24).pad(2);
		add(header).growX().minWidth(0f).colspan(3).pad(4, 4, 2, 2).row();

		nameLabel = new Label("名称", kit.labelStyle);
		nameLabel.setColor(0.5f, 0.9f, 1.0f, 1f);
		boneNameField = new TextField("", kit.textFieldStyle);
		boneNameField.setMaxLength(64);
		boneNameField.setMessageText("骨骼名称");
		boneNameField.addListener(new FocusListener() {
			@Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
				if (!focused && !updating) applyBoneName();
			}
		});
		boneNameField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
					if (applyBoneName() && getStage() != null) getStage().setKeyboardFocus(null);
					return true;
				}
				if (keycode == Input.Keys.ESCAPE) {
					restoreBoneNameField();
					if (getStage() != null) getStage().setKeyboardFocus(null);
					return true;
				}
				return false;
			}
		});
		Table nameRow = new Table();
		nameRow.add(nameLabel).left().width(48).pad(2, 4, 2, 2);
		nameRow.add(boneNameField).growX().width(128f).minWidth(0f).height(24).pad(2);
		add(nameRow).growX().minWidth(0f).colspan(3).padBottom(4).row();

		parentSpaceBtn = new TextButton("父级", kit.activeToolButton);
		worldSpaceBtn = new TextButton("世界", kit.toolButton);
		localSpaceBtn = new TextButton("本地", kit.toolButton);
		parentSpaceBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { if (!updating) setCoordinateSpace(BoneCoordinateSpace.PARENT); }
		});
		worldSpaceBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { if (!updating) setCoordinateSpace(BoneCoordinateSpace.WORLD); }
		});
		localSpaceBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { if (!updating) setCoordinateSpace(BoneCoordinateSpace.LOCAL); }
		});
		Table coordinates = new Table();
		coordinates.add(kit.controlLabel(I18n.t("移动轴", "Axes"))).left().width(48f).padRight(6f);
		coordinates.add(parentSpaceBtn).width(52f).height(24f).padRight(3f);
		coordinates.add(worldSpaceBtn).width(52f).height(24f).padRight(3f);
		coordinates.add(localSpaceBtn).width(52f).height(24f);
		add(coordinates).colspan(3).width(216f).left().pad(2f, 4f, 4f, 4f).row();
		coordinateHelp = kit.controlLabel("");
		coordinateHelp.setWrap(true);
		add(coordinateHelp).colspan(3).width(216f).left().pad(0f, 4f, 4f, 4f).row();

		xRow = new FloatRow("X", -1000f, 1000f, 0.1f, Value.X);
		yRow = new FloatRow("Y", -1000f, 1000f, 0.1f, Value.Y);
		rotRow = new FloatRow("旋转", -180f, 180f, 0.1f, Value.ROTATION);
		sxRow = new FloatRow("缩放 X", -5f, 5f, 0.01f, Value.SCALE_X);
		syRow = new FloatRow("缩放 Y", -5f, 5f, 0.01f, Value.SCALE_Y);
		shearXRow = new FloatRow("剪切 X", -180f, 180f, 0.1f, Value.SHEAR_X);
		shearYRow = new FloatRow("剪切 Y", -180f, 180f, 0.1f, Value.SHEAR_Y);
		lengthRow = new SetupLengthRow("长度", 0f, 2000f);
		inheritLabel = kit.controlLabel("继承");
		inheritBox = kit.selectBox();
		inheritBox.setItems(localizedInheritLabels());
		inheritBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { applyInheritPreview(); }
		});
		inheritRotationBtn = inheritToggle("旋转");
		inheritScaleBtn = inheritToggle("缩放");
		inheritReflectionBtn = inheritToggle("反射");
		ChangeListener inheritListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { applyInheritChecks(); }
		};
		inheritRotationBtn.addListener(inheritListener);
		inheritScaleBtn.addListener(inheritListener);
		inheritReflectionBtn.addListener(inheritListener);
		skinRequiredLabel = kit.controlLabel("皮肤");
		skinRequiredBtn = new TextButton("不要求", kit.toolButton);
		skinRequiredBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating || doc.getSelectedBone() == null || !doc.poseMode) return;
				pendingSkinRequired = !pendingSkinRequired;
				skinRequiredDraftBone = doc.getSelectedBone().getData();
				skinRequiredDraftDirty = pendingSkinRequired != skinRequiredDraftBone.getSkinRequired();
				refreshSkinRequiredButton(true);
				refreshSetupApplyState();
			}
		});

		addRow(xRow);
		addRow(yRow);
		addRow(rotRow);
		addRow(sxRow);
		addRow(syRow);
		addRow(shearXRow);
		addRow(shearYRow);
		add(lengthRow.label).left().pad(2, 4, 2, 2).width(48);
		add(lengthRow.field).pad(2).width(64).height(24);
		add(lengthRow.slider).growX().width(72f).pad(2, 4, 2, 2).minWidth(48f).height(24).row();
		Table inheritModeRow = new Table();
		inheritModeRow.add(inheritLabel).left().width(48).pad(2, 4, 2, 2);
		inheritModeRow.add(inheritBox).growX().width(128f).minWidth(0f).pad(2, 4, 2, 2).height(26);
		add(inheritModeRow).growX().minWidth(0f).colspan(3).row();
		Table inheritFlagsRow = new Table();
		inheritFlagsRow.add(kit.controlLabel("继承项")).left().width(48).pad(2, 4, 2, 2);
		inheritFlagsRow.add(inheritRotationBtn).growX().minWidth(0f).pad(2).height(26);
		inheritFlagsRow.add(inheritScaleBtn).growX().minWidth(0f).pad(2).height(26);
		inheritFlagsRow.add(inheritReflectionBtn).growX().minWidth(0f).pad(2, 4, 2, 2).height(26);
		add(inheritFlagsRow).growX().minWidth(0f).colspan(3).row();
		add(skinRequiredLabel).left().pad(2, 4, 2, 2).width(48);
		add(skinRequiredBtn).growX().pad(2, 4, 2, 2).height(26).colspan(2).row();
		boneColorField = new TextField(UiColors.format(Color.WHITE), kit.textFieldStyle);
		boneColorSwatch = new Table();
		boneColorSwatch.setBackground(kit.white.tint(Color.WHITE));
		boneColorField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				try { boneColorSwatch.setBackground(kit.white.tint(UiColors.parse(boneColorField.getText()))); }
				catch (RuntimeException ignored) { }
			}
		});
		addColorRow("颜色", boneColorField, boneColorSwatch);
		boneIconField = new TextField("", kit.textFieldStyle);
		boneIconField.setMessageText("图标名称（可选）");
		boneIconSizeField = new TextField("100", kit.textFieldStyle);
		boneIconSizeField.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+' || c == '.'
			|| c == 'e' || c == 'E');
		boneIconSizeSlider = new Slider(0f, 300f, 1f, false, kit.sliderStyle);
		boneIconSizeSlider.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				boneIconSizeField.setText(UiNumbers.format(boneIconSizeSlider.getValue()));
			}
		});
		Table iconRow = new Table();
		iconRow.add(kit.controlLabel("图片")).left().width(48).pad(2, 4, 2, 2);
		iconRow.add(boneIconField).growX().width(88f).minWidth(0f).pad(2).height(24);
		iconRow.add(boneIconSizeField).width(42f).minWidth(32f).pad(2).height(24);
		iconRow.add(boneIconSizeSlider).growX().width(68f).minWidth(48f).pad(2, 4, 2, 2).height(24);
		add(iconRow).growX().minWidth(0f).colspan(3).row();
		boneIconRotationField = new TextField("0", kit.textFieldStyle);
		addLabeledField("图标旋转", boneIconRotationField);
		boneVisibleBtn = new TextButton("显示", kit.toolButton);
		boneVisibleBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				boneVisibleBtn.setText(I18n.t(boneVisibleBtn.isChecked() ? "可见" : "隐藏"));
			}
		});
		addLabeledField("可见", boneVisibleBtn);
		boneSelectableBtn = inheritToggle("选择");
		boneNameVisibleBtn = inheritToggle("名称");
		boneSelectableBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				Map<String, SkeletonDocument.BoneEditorState> before = captureBoneEditorStates();
				boolean changed = false;
				for (Bone bone : doc.getSelectedBones())
					changed |= doc.setBoneSelectable(bone.getData(), boneSelectableBtn.isChecked());
				refreshToggleStyles();
				if (changed) {
					recordBoneEditorStateChange("编辑骨骼选择状态", before);
					if (onPoseEdited != null) onPoseEdited.run();
				}
			}
		});
		boneNameVisibleBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				Map<String, SkeletonDocument.BoneEditorState> before = captureBoneEditorStates();
				boolean changed = false;
				for (Bone bone : doc.getSelectedBones())
					changed |= doc.setBoneNameVisible(bone.getData(), boneNameVisibleBtn.isChecked());
				refreshToggleStyles();
				if (changed) {
					recordBoneEditorStateChange("编辑骨骼名称显示", before);
					if (onPoseEdited != null) onPoseEdited.run();
				}
			}
		});
		addLabeledField("视口", boneSelectableBtn);
		addLabeledField("名称", boneNameVisibleBtn);
		boneAppearanceApplyBtn = new TextButton("应用外观", kit.toolButton);
		boneAppearanceApplyBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { applyBoneAppearance(); }
		});
		add(boneAppearanceApplyBtn).right().colspan(3).pad(4).height(26).row();
		newBoneBtn = new TextButton("新建...", kit.toolButton);
		parentBtn = new TextButton("父级", kit.toolButton);
		splitBtn = new TextButton("拆分", kit.toolButton);
		skinBtn = new TextButton("皮肤...", kit.toolButton);
		newBoneBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showNewBoneDialog(); }
		});
		parentBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showParentDialog(); }
		});
		splitBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showSplitDialog(); }
		});
		skinBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { addSelectedBoneToSkin(); }
		});
		Table actionRow = new Table();
		actionRow.add(newBoneBtn).growX().minWidth(0f).height(26).pad(2);
		actionRow.add(parentBtn).growX().minWidth(0f).height(26).pad(2).row();
		actionRow.add(splitBtn).growX().minWidth(0f).height(26).pad(2);
		actionRow.add(skinBtn).growX().minWidth(0f).height(26).pad(2);
		add(actionRow).growX().minWidth(0f).colspan(3).row();
	}

	private void addColorRow(String label, TextField field, Table swatch) {
		Table line = new Table();
		line.add(kit.controlLabel(label)).left().pad(2, 4, 2, 2).width(48);
		line.add(field).growX().width(128f).minWidth(0f).pad(2).height(24);
		line.add(swatch).pad(3).width(24).height(20);
		add(line).growX().minWidth(0f).colspan(3).row();
	}

	private TextButton inheritToggle(String label) {
		TextButton button = new TextButton(label, kit.toolButton);
		button.setProgrammaticChangeEvents(false);
		return button;
	}

	private void addLabeledField(String label, Actor field) {
		add(kit.controlLabel(label)).left().pad(2, 4, 2, 2).width(64);
		add(field).growX().width(128f).minWidth(0f).pad(2).height(24).colspan(2).row();
	}

	public void setHistory(EditHistory history) { this.history = history; }
	public void setOnPoseEdited(Runnable listener) { onPoseEdited = listener; }
	public void setOnBonesTransformFinished(BiFunction<List<Bone>, AnimationEditor.Prop, Boolean> listener) {
		onBoneTransformFinished = listener;
	}
	public void setOnDataChanged(Runnable listener) { onDataChanged = listener; }
	public void setOnReversibleDataChanged(Runnable listener) { onReversibleDataChanged = listener; }
	public void setOnStatus(BiConsumer<String, Boolean> listener) { onStatus = listener; }

	static float parsePoseValue(String text, String label) {
		try {
			return UiNumbers.parseFinite(text);
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(label + " 必须是有限数值", error);
		}
	}

	private void addRow(FloatRow row) {
		add(row.label).left().pad(2, 4, 2, 2).width(48);
		add(row.field).pad(2).width(64).height(24);
		add(row.slider).growX().width(72f).pad(2, 4, 2, 2).minWidth(48f).height(24).row();
	}

	/** Refresh values without disturbing a text field currently being edited. */
	public void refresh() {
		updating = true;
		Bone bone = doc.getSelectedBone();
		nameLabel.setText(I18n.t("名称", "Name"));
		if (getStage() == null || getStage().getKeyboardFocus() != boneNameField)
			boneNameField.setText(bone == null ? "" : bone.getData().getName());
		setEnabled(bone != null);
		if (bone != null) {
			var pose = bone.getPose();
			xRow.set(currentValue(Value.X));
			yRow.set(currentValue(Value.Y));
			rotRow.set(pose.getRotation());
			sxRow.set(pose.getScaleX());
			syRow.set(pose.getScaleY());
			shearXRow.set(pose.getShearX());
			shearYRow.set(pose.getShearY());
			BoneData data = bone.getData();
			boolean preserveLength = doc.poseMode
				&& preserveLengthDraft(lengthDraftBone, data, lengthDraftDirty);
			if (!preserveLength) {
				boolean changedBone = lengthDraftBone != data;
				lengthDraftBone = data;
				lengthDraftDirty = false;
				lengthRow.set(data.getLength(), changedBone || !doc.poseMode);
			}
			inheritBox.setSelected(localizedInheritLabel(pose.getInherit()));
			setInheritChecks(pose.getInherit());
			boneSelectableBtn.setChecked(doc.isBoneSelectable(bone));
			boneNameVisibleBtn.setChecked(doc.isBoneNameVisible(bone));
			refreshToggleStyles();
			if (!preserveSkinRequiredDraft(skinRequiredDraftBone, data,
				skinRequiredDraftDirty, pendingSkinRequired)) {
				pendingSkinRequired = data.getSkinRequired();
				skinRequiredDraftBone = data;
				skinRequiredDraftDirty = false;
			}
			SkeletonEditor.BoneAppearanceState appearance = SkeletonEditor.boneAppearanceState(data);
			boneColorField.setText(UiColors.format(appearance.color()));
			boneColorSwatch.setBackground(kit.white.tint(appearance.color()));
			boneIconField.setText(appearance.icon());
			boneIconSizeField.setText(UiNumbers.format(appearance.iconSize() * 100f));
			setIconSizeSlider(appearance.iconSize() * 100f);
			boneIconRotationField.setText(UiNumbers.format(appearance.iconRotation()));
			boneVisibleBtn.setChecked(appearance.visible());
			boneVisibleBtn.setText(appearance.visible() ? "显示" : "隐藏");
		} else {
			lengthDraftBone = null;
			lengthDraftDirty = false;
			lengthRow.set(0f, true);
			inheritBox.setSelected(localizedInheritLabel(Inherit.normal));
			pendingSkinRequired = false;
			skinRequiredDraftBone = null;
			skinRequiredDraftDirty = false;
			boneColorField.setText(UiColors.format(Color.WHITE));
			boneIconField.setText("");
			boneIconSizeField.setText("100");
			setIconSizeSlider(100f);
			boneIconRotationField.setText("0");
			boneVisibleBtn.setChecked(false);
			boneVisibleBtn.setText("显示");
			boneSelectableBtn.setChecked(true);
			boneNameVisibleBtn.setChecked(false);
			refreshToggleStyles();
		}
		updating = false;
		resetPoseBtn.setDisabled(bone == null);
		refreshSkinRequiredButton(bone != null && doc.poseMode);
		boneAppearanceApplyBtn.setDisabled(bone == null);
		refreshSetupApplyState();
		localizeBoneControls();
		refreshCoordinateControls();
		I18n.localize(this);
	}

	public BoneCoordinateSpace coordinateSpace() { return coordinateSpace; }

	public void setOnCoordinateSpaceChanged(Consumer<BoneCoordinateSpace> listener) { onCoordinateSpaceChanged = listener; }

	public void setCoordinateSpace(BoneCoordinateSpace space) {
		if (space == null || space == coordinateSpace) { refreshCoordinateControls(); return; }
		if (activeSelectionEdit != null) cancelEdit();
		boolean wasUpdating = updating;
		updating = true;
		try {
			if (getStage() != null) getStage().setKeyboardFocus(null);
			coordinateSpace = space;
		} finally { updating = wasUpdating; }
		refresh();
		if (onCoordinateSpaceChanged != null) onCoordinateSpaceChanged.accept(space);
	}

	private void refreshCoordinateControls() {
		boolean wasUpdating = updating;
		updating = true;
		try {
			parentSpaceBtn.setChecked(coordinateSpace == BoneCoordinateSpace.PARENT);
			worldSpaceBtn.setChecked(coordinateSpace == BoneCoordinateSpace.WORLD);
			localSpaceBtn.setChecked(coordinateSpace == BoneCoordinateSpace.LOCAL);
			parentSpaceBtn.setStyle(coordinateSpace == BoneCoordinateSpace.PARENT ? kit.activeToolButton : kit.toolButton);
			worldSpaceBtn.setStyle(coordinateSpace == BoneCoordinateSpace.WORLD ? kit.activeToolButton : kit.toolButton);
			localSpaceBtn.setStyle(coordinateSpace == BoneCoordinateSpace.LOCAL ? kit.activeToolButton : kit.toolButton);
			parentSpaceBtn.setText(I18n.t("父级", "Parent"));
			worldSpaceBtn.setText(I18n.t("世界", "World"));
			localSpaceBtn.setText(I18n.t("本地", "Local"));
			boolean local = coordinateSpace == BoneCoordinateSpace.LOCAL;
			xRow.label.setText(local ? "ΔX" : "X");
			yRow.label.setText(local ? "ΔY" : "Y");
			coordinateHelp.setText(local
				? I18n.t("ΔX/ΔY：本地增量，含缩放。\n完成归零；多选共用主选轴。", "ΔX/ΔY: local units, including scale.\nResets after edit; selection uses primary axes.")
				: coordinateSpace == BoneCoordinateSpace.WORLD
					? I18n.t("X/Y：世界坐标中的位置。", "X/Y: position in world coordinates.")
					: I18n.t("X/Y：父级坐标中的位置。", "X/Y: position in parent coordinates."));
		} finally { updating = wasUpdating; }
	}

	private void localizeBoneControls() {
		resetPoseBtn.setText(I18n.t("重置"));
		applySetupBtn.setText(I18n.t("设为 Setup"));
		inheritRotationBtn.setText(I18n.t("旋转"));
		inheritScaleBtn.setText(I18n.t("缩放"));
		inheritReflectionBtn.setText(I18n.t("反射"));
		boneSelectableBtn.setText(I18n.t("选择"));
		boneNameVisibleBtn.setText(I18n.t("名称"));
		boneVisibleBtn.setText(I18n.t(boneVisibleBtn.isChecked() ? "可见" : "隐藏"));
		skinRequiredBtn.setText(I18n.t(pendingSkinRequired ? "必需" : "不要求"));
		boneAppearanceApplyBtn.setText(I18n.t("应用外观"));
		newBoneBtn.setText(I18n.t("新建..."));
		parentBtn.setText(I18n.t("父级"));
		splitBtn.setText(I18n.t("拆分"));
		skinBtn.setText(I18n.t("皮肤..."));
		boneNameField.setMessageText(I18n.t("骨骼名称"));
		boneIconField.setMessageText(I18n.t("图标名称（可选）"));
	}

	private void restoreBoneNameField() {
		Bone bone = doc.getSelectedBone();
		boolean wasUpdating = updating;
		updating = true;
		try {
			boneNameField.setText(bone == null ? "" : bone.getData().getName());
		} finally {
			updating = wasUpdating;
		}
	}

	private void setIconSizeSlider(float percent) {
		boolean wasUpdating = updating;
		updating = true;
		try {
			boneIconSizeSlider.setValue(Math.max(0f, Math.min(300f,
				Float.isFinite(percent) ? percent : 100f)));
		} finally {
			updating = wasUpdating;
		}
	}

	private boolean applyBoneName() {
		if (updating) return false;
		Bone bone = doc.getSelectedBone();
		if (bone == null) return false;
		String name = boneNameField.getText() == null ? "" : boneNameField.getText().trim();
		if (name.isEmpty()) {
			showStatus("骨骼名称不能为空", true);
			restoreBoneNameField();
			return false;
		}
		String oldName = bone.getData().getName();
		if (oldName.equals(name)) {
			restoreBoneNameField();
			return true;
		}
		String renamed = editor.renameBone(bone.getData(), name);
		if (renamed == null) {
			showStatus("无法重命名骨骼", true);
			restoreBoneNameField();
			return false;
		}
		recordRenamedBone(oldName, renamed);
		notifyDataChanged(true);
		showStatus(oldName.equals(renamed) ? "骨骼名称未变化" : "已重命名骨骼为 " + renamed, false);
		return true;
	}

	private void resetSelectedBonePose() {
		Bone bone = doc.getSelectedBone();
		if (bone == null) return;
		var setup = bone.getData().getSetupPose();
		if (history != null) history.begin("重置骨骼姿态");
		if (!doc.updateBonePose(bone, setup.getX(), setup.getY(), setup.getRotation(),
			setup.getScaleX(), setup.getScaleY(), setup.getShearX(), setup.getShearY())) {
			if (history != null) history.cancel();
			return;
		}
		if (history != null) history.commit();
		if (onPoseEdited != null) onPoseEdited.run();
		showStatus("已重置 " + bone.getData().getName() + " 的姿态", false);
	}

	static boolean preserveSkinRequiredDraft(BoneData draftBone, BoneData selectedBone,
		boolean dirty, boolean draftValue) {
		return selectedBone != null && selectedBone == draftBone && dirty
			&& selectedBone.getSkinRequired() != draftValue;
	}

	static boolean preserveLengthDraft(BoneData draftBone, BoneData selectedBone, boolean dirty) {
		return dirty && selectedBone != null && selectedBone == draftBone;
	}

	static boolean setupDraftChanged(Bone bone, boolean poseMode, String lengthText, boolean skinRequired) {
		if (!poseMode || bone == null) return false;
		boolean poseChanged = !SkeletonEditor.bonePoseState(bone.getPose())
			.equals(SkeletonEditor.bonePoseState(bone.getData().getSetupPose()));
		return poseChanged || bone.getData().getSkinRequired() != skinRequired
			|| !UiDrafts.number(lengthText).equals(UiNumbers.format(bone.getData().getLength()));
	}

	private void refreshSetupApplyState() {
		applySetupBtn.setDisabled(!setupDraftChanged(doc.getSelectedBone(), doc.poseMode,
			lengthRow.field.getText(), pendingSkinRequired));
	}

	private boolean applyCurrentPoseAsSetup() {
		Bone bone = doc.getSelectedBone();
		if (bone == null || !doc.poseMode) return false;
		if (!setupDraftChanged(bone, true, lengthRow.field.getText(), pendingSkinRequired)) {
			showStatus("当前姿态与 Setup 相同", false);
			return true;
		}
		float length;
		try {
			length = parseSetupLength(lengthRow.field.getText());
		} catch (IllegalArgumentException error) {
			showStatus(error.getMessage(), true);
			if (getStage() != null) getStage().setKeyboardFocus(lengthRow.field);
			return false;
		}
		BoneData data = bone.getData();
		BoneSetupState before = SkeletonEditor.boneSetupState(data);
		BoneSetupState after = new BoneSetupState(SkeletonEditor.bonePoseState(bone.getPose()),
			length, pendingSkinRequired);
		if (!editor.updateBoneSetup(data, after)) {
			showStatus("无法更新骨骼 Setup", true);
			return false;
		}
		lengthDraftBone = data;
		lengthDraftDirty = false;
		skinRequiredDraftBone = data;
		skinRequiredDraftDirty = false;
		if (history != null) recordBoneSetupChange(history, editor, data, before, after);
		else {
			refresh();
			if (onPoseEdited != null) onPoseEdited.run();
		}
		showStatus("已将 " + data.getName() + " 的当前姿态设为 Setup", false);
		return true;
	}

	static void recordBoneSetupChange(EditHistory history, SkeletonEditor editor, BoneData bone,
		BoneSetupState before, BoneSetupState after) {
		if (history == null || editor == null || bone == null || before == null || after == null)
			throw new IllegalArgumentException("骨骼 Setup 历史参数不完整");
		history.record("设置骨骼 Setup", () -> restoreBoneSetup(editor, bone, before),
			() -> restoreBoneSetup(editor, bone, after));
	}

	private static void restoreBoneSetup(SkeletonEditor editor, BoneData bone, BoneSetupState state) {
		if (!editor.updateBoneSetup(bone, state))
			throw new IllegalStateException("无法恢复骨骼 Setup：" + bone.getName());
	}

	static float parseSetupLength(String text) {
		float length = parsePoseValue(text, "长度");
		if (length < 0f) throw new IllegalArgumentException("长度不能小于 0");
		return length;
	}

	static String inheritLabel(Inherit inherit) {
		if (inherit == null) return INHERIT_NORMAL;
		return switch (inherit) {
		case normal -> INHERIT_NORMAL;
		case onlyTranslation -> INHERIT_TRANSLATION;
		case noRotationOrReflection -> INHERIT_NO_ROTATION;
		case noScale -> INHERIT_NO_SCALE;
		case noScaleOrReflection -> INHERIT_NO_SCALE_REFLECTION;
		};
	}

	static String localizedInheritLabel(Inherit inherit) {
		return I18n.t(inheritLabel(inherit));
	}

	static String[] localizedInheritLabels() {
		return new String[] {I18n.t(INHERIT_NORMAL), I18n.t(INHERIT_TRANSLATION),
			I18n.t(INHERIT_NO_ROTATION), I18n.t(INHERIT_NO_SCALE), I18n.t(INHERIT_NO_SCALE_REFLECTION)};
	}

	static Inherit inheritMode(String label) {
		if (I18n.matches(label, INHERIT_NORMAL, "All")) return Inherit.normal;
		if (I18n.matches(label, INHERIT_TRANSLATION, "Translation Only"))
			return Inherit.onlyTranslation;
		if (I18n.matches(label, INHERIT_NO_ROTATION, "No Rotation or Reflection"))
			return Inherit.noRotationOrReflection;
		if (I18n.matches(label, INHERIT_NO_SCALE, "No Scale")) return Inherit.noScale;
		if (I18n.matches(label, INHERIT_NO_SCALE_REFLECTION, "No Scale or Reflection"))
			return Inherit.noScaleOrReflection;
		return null;
	}

	/** Refreshes translated labels and localized inherit choices without changing the selected value. */
	public void refreshLanguage() {
		Inherit selected = inheritMode(inheritBox.getSelected());
		updating = true;
		inheritBox.setItems(localizedInheritLabels());
		inheritBox.setSelected(localizedInheritLabel(selected == null ? Inherit.normal : selected));
		updating = false;
		refresh();
		I18n.localize(this);
	}

	private void applyInheritPreview() {
		if (updating) return;
		Bone bone = doc.getSelectedBone();
		Inherit inherit = inheritMode(inheritBox.getSelected());
		if (bone == null || !doc.poseMode || inherit == null) return;
		if (bone.getPose().getInherit() == inherit) {
			refreshSetupApplyState();
			return;
		}
		String lengthDraft = lengthRow.field.getText();
		float lengthSliderDraft = lengthRow.slider.getValue();
		boolean skinRequiredDraft = pendingSkinRequired;
		if (history != null) history.begin("修改骨骼继承模式");
		if (!doc.updateBoneInherit(bone, inherit)) {
			if (history != null) history.cancel();
			return;
		}
		if (history != null && history.commit())
			restoreSetupDraft(lengthDraft, lengthSliderDraft, skinRequiredDraft);
		setInheritChecks(inherit);
		if (onPoseEdited != null) onPoseEdited.run();
		refreshSetupApplyState();
	}

	private void applyInheritChecks() {
		if (updating) return;
		Bone bone = doc.getSelectedBone();
		Inherit inherit = inheritFromChecks(inheritRotationBtn.isChecked(), inheritScaleBtn.isChecked(),
			inheritReflectionBtn.isChecked());
		if (bone == null || !doc.poseMode || inherit == null) {
			refresh();
			return;
		}
		if (bone.getPose().getInherit() == inherit) return;
		String lengthDraft = lengthRow.field.getText();
		float lengthSliderDraft = lengthRow.slider.getValue();
		boolean skinRequiredDraft = pendingSkinRequired;
		if (history != null) history.begin("修改骨骼继承模式");
		if (!doc.updateBoneInherit(bone, inherit)) {
			if (history != null) history.cancel();
			showStatus("该骨骼受 IK 约束，不能关闭必要的继承", true);
			refresh();
			return;
		}
		if (history != null && history.commit()) restoreSetupDraft(lengthDraft, lengthSliderDraft, skinRequiredDraft);
		updating = true;
		try { inheritBox.setSelected(localizedInheritLabel(inherit)); }
		finally { updating = false; }
		if (onPoseEdited != null) onPoseEdited.run();
	}

	private void setInheritChecks(Inherit inherit) {
		boolean[] checks = inheritChecks(inherit);
		inheritRotationBtn.setChecked(checks[0]);
		inheritScaleBtn.setChecked(checks[1]);
		inheritReflectionBtn.setChecked(checks[2]);
		refreshToggleStyles();
	}

	private void refreshToggleStyles() {
		inheritRotationBtn.setStyle(inheritRotationBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
		inheritScaleBtn.setStyle(inheritScaleBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
		inheritReflectionBtn.setStyle(inheritReflectionBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
		boneSelectableBtn.setStyle(boneSelectableBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
		boneNameVisibleBtn.setStyle(boneNameVisibleBtn.isChecked() ? kit.activeToolButton : kit.toolButton);
	}

	private Map<String, SkeletonDocument.BoneEditorState> captureBoneEditorStates() {
		Map<String, SkeletonDocument.BoneEditorState> states = new LinkedHashMap<>();
		for (Bone bone : doc.getSelectedBones()) {
			SkeletonDocument.BoneEditorState state = doc.boneEditorState(bone.getData());
			if (state != null) states.put(state.bone(), state);
		}
		Bone primary = doc.getSelectedBone();
		if (states.isEmpty() && primary != null) {
			SkeletonDocument.BoneEditorState state = doc.boneEditorState(primary.getData());
			if (state != null) states.put(state.bone(), state);
		}
		return states;
	}

	private void recordBoneEditorStateChange(String label,
		Map<String, SkeletonDocument.BoneEditorState> before) {
		if (history == null || before == null || before.isEmpty()) return;
		Map<String, SkeletonDocument.BoneEditorState> after = captureBoneEditorStates();
		if (before.equals(after)) return;
		history.record(label,
			() -> doc.applyBoneEditorStates(before.values()),
			() -> doc.applyBoneEditorStates(after.values()));
	}

	static Inherit inheritForChecks(boolean rotation, boolean scale, boolean reflection) {
		if (rotation && scale && reflection) return Inherit.normal;
		if (!rotation && !scale && !reflection) return Inherit.onlyTranslation;
		if (!rotation && scale && !reflection) return Inherit.noRotationOrReflection;
		if (rotation && !scale && reflection) return Inherit.noScale;
		if (rotation && !scale && !reflection) return Inherit.noScaleOrReflection;
		return null;
	}

	static boolean[] inheritChecks(Inherit inherit) {
		if (inherit == null) inherit = Inherit.normal;
		return switch (inherit) {
		case normal -> new boolean[] {true, true, true};
		case onlyTranslation -> new boolean[] {false, false, false};
		case noRotationOrReflection -> new boolean[] {false, true, false};
		case noScale -> new boolean[] {true, false, true};
		case noScaleOrReflection -> new boolean[] {true, false, false};
		};
	}

	static Inherit inheritFromChecks(boolean rotation, boolean scale, boolean reflection) {
		return inheritForChecks(rotation, scale, reflection);
	}

	private void restoreSetupDraft(String lengthText, float sliderValue, boolean skinRequired) {
		boolean wasUpdating = updating;
		updating = true;
		try {
			lengthRow.field.setText(lengthText);
			lengthRow.slider.setValue(sliderValue);
			pendingSkinRequired = skinRequired;
		} finally {
			updating = wasUpdating;
		}
		syncLengthDraftState();
		refreshSkinRequiredButton(doc.getSelectedBone() != null && doc.poseMode);
	}

	private void syncLengthDraftState() {
		Bone bone = doc.getSelectedBone();
		if (bone == null || !doc.poseMode) {
			lengthDraftBone = null;
			lengthDraftDirty = false;
			return;
		}
		BoneData data = bone.getData();
		if (lengthDraftBone != data) {
			lengthDraftBone = data;
			lengthDraftDirty = false;
			return;
		}
		lengthDraftDirty = !UiDrafts.number(lengthRow.field.getText())
			.equals(UiNumbers.format(data.getLength()));
	}

	private void markLengthDraftChanged() {
		if (updating || !doc.poseMode) return;
		syncLengthDraftState();
	}

	private void refreshSkinRequiredButton(boolean enabled) {
		skinRequiredBtn.setText(I18n.t(pendingSkinRequired ? "必需" : "不要求"));
		skinRequiredBtn.setStyle(enabled && pendingSkinRequired ? kit.activeToolButton : kit.toolButton);
		skinRequiredBtn.setDisabled(!enabled);
	}

	private void setEnabled(boolean enabled) {
		xRow.setEnabled(enabled);
		yRow.setEnabled(enabled);
		rotRow.setEnabled(enabled);
		sxRow.setEnabled(enabled);
		syRow.setEnabled(enabled);
		shearXRow.setEnabled(enabled);
		shearYRow.setEnabled(enabled);
		lengthRow.setEnabled(enabled && doc.poseMode);
		boolean setupEnabled = enabled && doc.poseMode;
		inheritBox.setDisabled(!setupEnabled);
		inheritRotationBtn.setDisabled(!setupEnabled);
		inheritScaleBtn.setDisabled(!setupEnabled);
		inheritReflectionBtn.setDisabled(!setupEnabled);
		inheritLabel.setColor(setupEnabled ? 0.85f : 0.5f, setupEnabled ? 0.87f : 0.52f,
			setupEnabled ? 0.92f : 0.56f, 1f);
		skinRequiredLabel.setColor(setupEnabled ? 0.85f : 0.5f, setupEnabled ? 0.87f : 0.52f,
			setupEnabled ? 0.92f : 0.56f, 1f);
		refreshSkinRequiredButton(setupEnabled);
		boneColorField.setDisabled(!enabled);
		boneNameField.setDisabled(!enabled);
		boneIconField.setDisabled(!enabled);
		boneIconSizeField.setDisabled(!enabled);
		boneIconSizeSlider.setDisabled(!enabled);
		boneIconRotationField.setDisabled(!enabled);
		boneVisibleBtn.setDisabled(!enabled);
		boneSelectableBtn.setDisabled(!enabled);
		boneNameVisibleBtn.setDisabled(!enabled);
		boneAppearanceApplyBtn.setDisabled(!enabled);
		newBoneBtn.setDisabled(!enabled);
		parentBtn.setDisabled(!enabled || doc.getSelectedBone() == null || doc.getSelectedBone().getParent() == null);
		splitBtn.setDisabled(!enabled);
		skinBtn.setDisabled(!enabled);
		resetPoseBtn.setDisabled(!enabled);
	}

	private void applyBoneAppearance() {
		Bone bone = doc.getSelectedBone();
		if (bone == null) return;
		try {
			Color color = UiColors.parse(boneColorField.getText());
			float sizePercent = UiNumbers.parseFinite(boneIconSizeField.getText());
			if (sizePercent < 0f) throw new IllegalArgumentException("图标大小不能小于 0");
			float size = sizePercent / 100f;
			float rotation = UiNumbers.parseFinite(boneIconRotationField.getText());
			ArrayList<BoneData> targets = new ArrayList<>();
			for (Bone selected : doc.getSelectedBones()) targets.add(selected.getData());
			if (targets.isEmpty()) targets.add(bone.getData());
			Map<BoneData, SkeletonEditor.BoneAppearanceState> before = new IdentityHashMap<>();
			for (BoneData target : targets) before.put(target, SkeletonEditor.boneAppearanceState(target));
			SkeletonEditor.BoneAppearanceState after = new SkeletonEditor.BoneAppearanceState(color,
				boneIconField.getText(), size, rotation, boneVisibleBtn.isChecked());
			if (!editor.updateBoneAppearance(targets, after)) return;
			if (history != null) history.record("编辑骨骼外观",
				() -> restoreBoneAppearances(editor, before),
				() -> editor.updateBoneAppearance(targets, after));
			refresh();
			if (onPoseEdited != null) onPoseEdited.run();
		} catch (RuntimeException error) {
			showStatus(error.getMessage() == null ? "骨骼外观参数无效" : error.getMessage(), true);
		}
	}

	private static void restoreBoneAppearances(SkeletonEditor editor,
		Map<BoneData, SkeletonEditor.BoneAppearanceState> states) {
		for (Map.Entry<BoneData, SkeletonEditor.BoneAppearanceState> entry : states.entrySet())
			editor.updateBoneAppearance(entry.getKey(), entry.getValue());
	}

	private void showNewBoneDialog() {
		Bone parent = doc.getSelectedBone();
		if (parent == null || getStage() == null) return;
		UiDialogs.promptName(getStage(), kit, "新建子骨骼", "bone", 64, name -> {
			BoneData created = editor.addBone(name, parent.getData());
			if (created == null) {
				showStatus("无法新建骨骼", true);
				return false;
			}
			recordAddedBone(created.getName(), parent.getData().getName());
			notifyDataChanged(true);
			return true;
		});
	}

	private void showParentDialog() {
		Bone target = doc.getSelectedBone();
		if (target == null || getStage() == null || doc.data == null) return;
		String targetName = target.getData().getName();
		String initial = target.getParent() == null ? "" : target.getParent().getData().getName();
		BoneParentDialog.show(getStage(), kit, initial, (name, keepSetupWorld) -> {
			BoneData currentTarget = doc.data == null ? null : doc.data.findBone(targetName);
			BoneData parent = doc.data == null ? null : doc.data.findBone(name);
			if (parent == null) return "找不到父级骨骼：" + name;
			String blocked = editor.getBoneParentBlockReason(currentTarget, parent, keepSetupWorld);
			if (blocked != null) return blocked;
			SkeletonStructureSnapshot before = SkeletonStructureSnapshot.capture(editor);
			if (!editor.setBoneParent(currentTarget, parent, keepSetupWorld)) return "无法修改骨骼父级";
			if (history != null) {
				SkeletonStructureSnapshot after = SkeletonStructureSnapshot.capture(editor);
				history.record("修改骨骼父级", before::restore, after::restore);
			}
			notifyDataChanged(true);
			return null;
		});
	}

	private void showSplitDialog() {
		Bone target = doc.getSelectedBone();
		if (target == null || getStage() == null) return;
		UiDialogs.promptName(getStage(), kit, "拆分骨骼段数", "2", 3, text -> {
			int segments;
			try { segments = Integer.parseInt(text); }
			catch (NumberFormatException error) {
				showStatus("段数必须是 2 到 64 的整数", true);
				return false;
			}
			if (editor.splitBone(target.getData(), segments) == 0) {
				showStatus("无法拆分：骨骼长度必须大于 0", true);
				return false;
			}
			if (onDataChanged != null) onDataChanged.run();
			return true;
		});
	}

	private void notifyDataChanged(boolean reversible) {
		if (reversible && onReversibleDataChanged != null) onReversibleDataChanged.run();
		else if (onDataChanged != null) onDataChanged.run();
	}

	private void recordAddedBone(String boneName, String parentName) {
		if (history == null) return;
		history.record("新建骨骼", () -> {
			BoneData current = doc.data == null ? null : doc.data.findBone(boneName);
			if (current == null || !editor.deleteBone(current))
				throw new IllegalStateException("无法撤销新建骨骼：" + boneName);
		}, () -> {
			BoneData parent = doc.data == null ? null : doc.data.findBone(parentName);
			if (parent == null || editor.addBone(boneName, parent) == null)
				throw new IllegalStateException("无法重做新建骨骼：" + boneName);
		});
	}

	private void recordRenamedBone(String oldName, String newName) {
		if (history == null) return;
		history.record("重命名骨骼", () -> renameBoneForHistory(newName, oldName),
			() -> renameBoneForHistory(oldName, newName));
	}

	private void renameBoneForHistory(String currentName, String requestedName) {
		BoneData current = doc.data == null ? null : doc.data.findBone(currentName);
		String renamed = current == null ? null : editor.renameBone(current, requestedName);
		if (!requestedName.equals(renamed))
			throw new IllegalStateException("无法恢复骨骼名称：" + requestedName);
	}

	private void addSelectedBoneToSkin() {
		Bone bone = doc.getSelectedBone();
		if (bone == null || doc.data == null) return;
		String skinName = doc.getCurrentSkinName();
		if (skinName == null || skinName.isBlank()) {
			showStatus("请先选择皮肤", true);
			return;
		}
		var skin = doc.data.findSkin(skinName);
		if (skin == null || !editor.setSkinBoneActive(skin, bone.getData(), true)) {
			showStatus("该骨骼已经加入当前皮肤，或当前皮肤无效", false);
			return;
		}
		if (onDataChanged != null) onDataChanged.run();
	}

	private void beginEdit(Value value) {
		if (updating) return;
		if (activeEditBone != null) return;
		try {
			activeSelectionEdit = BoneSelectionTransform.capture(doc);
		} catch (IllegalArgumentException error) {
			showStatus("无法变换骨骼：" + error.getMessage(), true);
			return;
		}
		activeEditBone = activeSelectionEdit == null ? null : activeSelectionEdit.primary();
		activeEditValue = value;
		activeCoordinateSpace = coordinateSpace;
		activeEditChanged = false;
		activeMoveDelta = 0f;
		if (history != null) history.begin("编辑骨骼姿态");
	}

	private void finishEdit() {
		if (updating) return;
		BoneSelectionTransform selection = activeSelectionEdit;
		if (selection != null && !selection.isCurrent()) { cancelEdit(); return; }
		AnimationEditor.Prop editedProp = autoKeyProp(activeEditValue, activeCoordinateSpace,
			selection == null ? 0 : selection.selectedBones().size());
		boolean changed = activeEditChanged && selection != null && selection.hasChanges();
		activeEditBone = null;
		activeSelectionEdit = null;
		activeEditValue = null;
		activeEditChanged = false;
		activeMoveDelta = 0f;
		boolean autoKeyHandled = changed && selection != null && editedProp != null
			&& onBoneTransformFinished != null
			&& Boolean.TRUE.equals(onBoneTransformFinished.apply(selection.transformedBones(), editedProp));
		if (!autoKeyHandled && history != null) history.commit();
		if (coordinateSpace == BoneCoordinateSpace.LOCAL) {
			refresh();
			// Scene2D sends focus-lost before clearing its old keyboard focus reference.
			// Completed increments must reset even while that reference still points at this field.
			xRow.restoreField();
			yRow.restoreField();
		}
	}

	private void cancelEdit() {
		BoneSelectionTransform selection = activeSelectionEdit;
		activeEditBone = null;
		activeSelectionEdit = null;
		activeEditValue = null;
		activeEditChanged = false;
		activeMoveDelta = 0f;
		if (history != null) history.rollbackPending();
		else if (selection != null) selection.restore();
		if (selection != null && onPoseEdited != null) onPoseEdited.run();
	}

	private void showStatus(String message, boolean error) {
		if (onStatus != null) onStatus.accept(message, error);
	}

	private void apply(Value value, float number) {
		if (updating) return;
		Bone bone = doc.getSelectedBone();
		if (bone == null || !Float.isFinite(number)) return;
		if (activeSelectionEdit == null) beginEdit(value);
		if (activeSelectionEdit == null) return;
		if (!activeSelectionEdit.isCurrent()) { cancelEdit(); return; }
		boolean changed = applySelectionValue(activeSelectionEdit, value, number, activeCoordinateSpace);
		if (activeCoordinateSpace == BoneCoordinateSpace.LOCAL && (value == Value.X || value == Value.Y)) activeMoveDelta = number;
		if (changed) {
			if (bone == activeEditBone && value == activeEditValue) activeEditChanged = true;
			if (onPoseEdited != null) onPoseEdited.run();
		}
	}

	static boolean applySelectionValue(BoneSelectionTransform selection, Value value, float number) {
		return applySelectionValue(selection, value, number, BoneCoordinateSpace.PARENT);
	}

	static boolean applySelectionValue(BoneSelectionTransform selection, Value value, float number, BoneCoordinateSpace space) {
		if (selection == null || value == null || !Float.isFinite(number))
			throw new IllegalArgumentException("骨骼属性必须是有限数值");
		if (space == BoneCoordinateSpace.LOCAL && (value == Value.X || value == Value.Y))
			return selection.applyLocalDelta(value == Value.X ? number : 0f, value == Value.Y ? number : 0f);
		if (space == BoneCoordinateSpace.WORLD && (value == Value.X || value == Value.Y)) {
			Vector2 position = selection.primaryPosition(space);
			return selection.applyWorldPosition(value == Value.X ? number : position.x, value == Value.Y ? number : position.y);
		}
		float[] pose = selection.primaryPose();
		pose[value.ordinal()] = number;
		return selection.applyPrimaryPose(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5], pose[6]);
	}

	enum Value { X, Y, ROTATION, SCALE_X, SCALE_Y, SHEAR_X, SHEAR_Y }

	static AnimationEditor.Prop autoKeyProp(Value value) {
		if (value == null) return null;
		return switch (value) {
		case X -> AnimationEditor.Prop.TRANSLATE_X;
		case Y -> AnimationEditor.Prop.TRANSLATE_Y;
		case ROTATION -> AnimationEditor.Prop.ROTATE;
		case SCALE_X -> AnimationEditor.Prop.SCALE_X;
		case SCALE_Y -> AnimationEditor.Prop.SCALE_Y;
		case SHEAR_X -> AnimationEditor.Prop.SHEAR_X;
		case SHEAR_Y -> AnimationEditor.Prop.SHEAR_Y;
		};
	}

	static AnimationEditor.Prop autoKeyProp(Value value, BoneCoordinateSpace space, int selectionSize) {
		if ((value == Value.X || value == Value.Y) && (space != BoneCoordinateSpace.PARENT || selectionSize > 1))
			return AnimationEditor.Prop.TRANSLATE;
		return autoKeyProp(value);
	}

	private float currentValue(Value value) {
		Bone bone = doc.getSelectedBone();
		if (bone == null) return 0f;
		if (coordinateSpace == BoneCoordinateSpace.LOCAL && (value == Value.X || value == Value.Y))
			return activeCoordinateSpace == BoneCoordinateSpace.LOCAL && activeEditValue == value ? activeMoveDelta : 0f;
		var pose = bone.getPose();
		return switch (value) {
		case X -> coordinateSpace.position(doc.skeleton, bone, new Vector2()).x;
		case Y -> coordinateSpace.position(doc.skeleton, bone, new Vector2()).y;
		case ROTATION -> pose.getRotation();
		case SCALE_X -> pose.getScaleX();
		case SCALE_Y -> pose.getScaleY();
		case SHEAR_X -> pose.getShearX();
		case SHEAR_Y -> pose.getShearY();
		};
	}

	private final class FloatRow {
		final Label label;
		final TextField field;
		final Slider slider;
		final float min, max;
		final Value value;
		final String name;

		FloatRow(String name, float min, float max, float step, Value value) {
			this.name = name;
			this.min = min;
			this.max = max;
			this.value = value;
			label = kit.controlLabel(name);
			field = new TextField("0", kit.textFieldStyle);
			field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E');
			slider = new Slider(min, max, step, false, kit.sliderStyle);

			slider.addCaptureListener(new InputListener() {
				@Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
					if (button != Input.Buttons.LEFT) return false;
					if (getStage() != null) getStage().setKeyboardFocus(slider);
					beginEdit(value);
					return true;
				}
				@Override public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
					finishEdit();
				}
			});
			slider.addListener(new InputListener() {
				@Override public boolean keyDown(InputEvent event, int keycode) {
					if (keycode != Input.Keys.ESCAPE || activeEditValue != value) return false;
					cancelEdit();
					restoreField();
					if (getStage() != null) {
						getStage().cancelTouchFocus();
						getStage().setKeyboardFocus(null);
					}
					return true;
				}
			});
			slider.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					if (updating) return;
					float number = slider.getValue();
					field.setText(UiNumbers.format(number));
					try {
						apply(value, number);
					} catch (IllegalArgumentException error) {
						cancelEdit();
						restoreField();
						showStatus(error.getMessage(), true);
					}
				}
			});

			field.addListener(new FocusListener() {
				@Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
					if (updating) return;
					if (focused) beginEdit(value);
					else {
						if (!applyField()) restoreField();
						finishEdit();
					}
				}
			});
			field.addListener(new InputListener() {
				@Override public boolean keyDown(InputEvent event, int keycode) {
					if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
						if (applyField() && getStage() != null) getStage().setKeyboardFocus(null);
						return true;
					}
					if (keycode == Input.Keys.ESCAPE) {
						cancelEdit();
						restoreField();
						showStatus("已取消编辑 " + name, false);
						if (getStage() != null) getStage().setKeyboardFocus(null);
						return true;
					}
					return false;
				}
			});
		}

		boolean applyField() {
			try {
				float number = parsePoseValue(field.getText(), name);
				apply(value, number);
				syncSlider(number);
				field.setText(UiNumbers.format(number));
				return true;
			} catch (IllegalArgumentException error) {
				showStatus(error.getMessage(), true);
				return false;
			}
		}

		void restoreField() {
			float number = currentValue(value);
			syncSlider(number);
			field.setText(UiNumbers.format(number));
		}

		void set(float number) {
			syncSlider(number);
			if (getStage() == null || getStage().getKeyboardFocus() != field) field.setText(UiNumbers.format(number));
		}

		private void syncSlider(float number) {
			boolean wasUpdating = updating;
			updating = true;
			try {
				slider.setValue(clamp(number));
			} finally {
				updating = wasUpdating;
			}
		}

		void setEnabled(boolean enabled) {
			field.setDisabled(!enabled);
			slider.setDisabled(!enabled);
			label.setColor(enabled ? 0.85f : 0.5f, enabled ? 0.87f : 0.52f, enabled ? 0.92f : 0.56f, 1f);
		}

		float clamp(float number) { return Math.max(min, Math.min(max, number)); }
	}

	private final class SetupLengthRow {
		final Label label;
		final TextField field;
		final Slider slider;
		final float min, max;

		SetupLengthRow(String name, float min, float max) {
			this.min = min;
			this.max = max;
			label = kit.controlLabel(name);
			field = new TextField("0", kit.textFieldStyle);
			field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E');
			slider = new Slider(min, max, 0.1f, false, kit.sliderStyle);
			slider.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					if (updating) return;
					field.setText(UiNumbers.format(slider.getValue()));
					markLengthDraftChanged();
					refreshSetupApplyState();
				}
			});
			field.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					if (!updating) {
						markLengthDraftChanged();
						refreshSetupApplyState();
					}
				}
			});
			field.addListener(new FocusListener() {
				@Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
					if (!focused && !updating) normalizeIfValid();
				}
			});
			field.addListener(new InputListener() {
				@Override public boolean keyDown(InputEvent event, int keycode) {
					return switch (UiDialogs.keyAction(keycode)) {
					case CONFIRM -> {
						if ((applySetupBtn.isDisabled() || applyCurrentPoseAsSetup()) && getStage() != null)
							getStage().setKeyboardFocus(null);
						yield true;
					}
					case CANCEL -> {
						restore();
						showStatus("已取消编辑长度", false);
						if (getStage() != null) getStage().setKeyboardFocus(null);
						yield true;
					}
					case NONE -> false;
					};
				}
			});
		}

		void set(float value) {
			set(value, false);
		}

		void set(float value, boolean forceField) {
			syncSlider(value);
			if (forceField || getStage() == null || getStage().getKeyboardFocus() != field)
				field.setText(UiNumbers.format(value));
		}

		void restore() {
			Bone bone = doc.getSelectedBone();
			float value = bone == null ? 0f : bone.getData().getLength();
			lengthDraftBone = bone == null ? null : bone.getData();
			lengthDraftDirty = false;
			syncSlider(value);
			field.setText(UiNumbers.format(value));
			refreshSetupApplyState();
		}

		void normalizeIfValid() {
			try {
				float value = parseSetupLength(field.getText());
				syncSlider(value);
				field.setText(UiNumbers.format(value));
				syncLengthDraftState();
			} catch (IllegalArgumentException ignored) {
				// Keep invalid text visible so applying can report the exact validation error.
			}
		}

		void setEnabled(boolean enabled) {
			field.setDisabled(!enabled);
			slider.setDisabled(!enabled);
			label.setColor(enabled ? 0.85f : 0.5f, enabled ? 0.87f : 0.52f, enabled ? 0.92f : 0.56f, 1f);
		}

		private void syncSlider(float value) {
			boolean wasUpdating = updating;
			updating = true;
			try {
				slider.setValue(Math.max(min, Math.min(max, value)));
			} finally {
				updating = wasUpdating;
			}
		}
	}

}
