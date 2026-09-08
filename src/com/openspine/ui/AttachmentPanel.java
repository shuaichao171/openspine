package com.openspine.ui;

import com.badlogic.gdx.graphics.Color;
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
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.openspine.model.EditHistory;
import com.openspine.model.AnimationEditor;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonEditor.StandardAttachmentType;

/**
 * 附件与皮肤管理面板：选中骨骼时显示其关联槽的附件选择/编辑，
 * 以及皮肤列表（新建/删除/切换）。
 */
public class AttachmentPanel extends Table {
	private static final String NONE_ATTACHMENT = "（无附件）";
	private static final String[] REGION_VALUE_LABELS = {"X", "Y", "缩放 X", "缩放 Y", "旋转", "宽", "高"};
	record SlotDraft(String color, boolean twoColor, String darkColor, String blendMode, boolean visible) {}
	record RegionDraft(String color, String x, String y, String scaleX, String scaleY, String rotation,
		String width, String height) {}
	record SlotPropertyState(Color color, Color darkColor, BlendMode blendMode, boolean visible) {}
	record RegionPropertyState(Color color, float x, float y, float scaleX, float scaleY, float rotation,
		float width, float height) {}

	private final SkeletonDocument doc;
	private final SkeletonEditor editor;
	private final UiKit kit;

	private final Label slotTitle;
	private final SelectBox<String> slotBox;
	private final TextButton newSlotBtn, moveSlotUpBtn, moveSlotDownBtn;
	private final TextField slotNameField;
	private final TextButton renameSlotBtn, deleteSlotBtn;
	private final TextField slotColorField;
	private final Table slotColorSwatch;
	private final TextButton slotTwoColorBtn;
	private final TextField slotDarkColorField;
	private final Table slotDarkColorSwatch;
	private final SelectBox<String> slotBlendBox;
	private final TextButton slotVisibleBtn, applySlotBtn;
	private final SelectBox<String> attachBox;
	private final SelectBox<String> regionBox;
	private final SelectBox<String> linkedMeshSourceBox;
	private final SelectBox<String> attachmentTypeBox;
	private final TextField placeholderField;
	private final TextButton addRegionBtn, editMeshBtn, sequenceBtn, removeRegionBtn, linkedMeshTimelinesBtn;
	private final TextField regionColorField;
	private final Table regionColorSwatch;
	private final FloatRow xRow, yRow, sxRow, syRow, rRow, wRow, hRow;
	private final TextButton applyRegionBtn;
	private final Label skinTitle;
	private final SelectBox<String> skinBox;
	private final TextField skinNameField;
	private final TextButton addSkinBtn, duplicateSkinBtn, renameSkinBtn, delSkinBtn;
	private final TextButton skinBonesBtn;
	private final TextButton skinCombinationBtn;

	private boolean updating;
	private EditHistory history;
	private Runnable onDataChanged;
	private Runnable onReversibleDataChanged;
	private Runnable onSkinChanged;
	private java.util.function.BiConsumer<SlotData, Attachment> onAttachmentSelectionChanged;
	private java.util.function.BiConsumer<String, Boolean> onStatus;
	private String pendingSlotSelection;
	private String pendingAttachmentSelection;
	private boolean pendingSlotVisible = true;
	private boolean pendingSlotTwoColor;
	private SlotDraft slotBaseline;
	private RegionDraft regionBaseline;
	private final Array<SkeletonEditor.MeshAttachmentSource> linkedMeshSources = new Array<>();
	private boolean pendingLinkedMeshTimelines = true;
	private final AnimationEditor animationEditor;

	public AttachmentPanel(SkeletonDocument doc, UiKit kit, SkeletonEditor editor) {
		this.doc = doc;
		this.kit = kit;
		this.editor = editor;
		this.animationEditor = new AnimationEditor(doc);
		setBackground(kit.panelBg);

		Label title = new Label("附件 & 皮肤", kit.labelStyle);
		title.setColor(0.75f, 0.8f, 0.9f, 1f);
		add(title).left().pad(6).padTop(10).colspan(3).row();

		slotTitle = new Label("关联槽", kit.labelStyle);
		slotTitle.setColor(0.6f, 0.85f, 0.9f, 1f);
		newSlotBtn = new TextButton("+ 槽", kit.toolButton);
		newSlotBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { addSlotForSelectedBone(); }
		});
		moveSlotUpBtn = new TextButton("上移", kit.toolButton);
		moveSlotUpBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { moveCurrentSlot(-1); }
		});
		moveSlotDownBtn = new TextButton("下移", kit.toolButton);
		moveSlotDownBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { moveCurrentSlot(1); }
		});
		Table slotHeader = new Table();
		slotHeader.add(slotTitle).growX().left().pad(2);
		slotHeader.add(moveSlotUpBtn).right().pad(2).height(24);
		slotHeader.add(moveSlotDownBtn).right().pad(2).height(24);
		slotHeader.add(newSlotBtn).right().pad(2).height(24);
		add(slotHeader).growX().minWidth(0f).colspan(3).row();

		slotBox = kit.selectBox();
		slotBox.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				refreshAttachments();
				notifyAttachmentSelection();
			}
		});
		add(slotBox).growX().pad(2).colspan(3).row();

		slotNameField = new TextField("", kit.textFieldStyle);
		slotNameField.setMessageText("槽名称");
		slotNameField.setMaxLength(64);
		renameSlotBtn = new TextButton("重命名", kit.toolButton);
		renameSlotBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { renameCurrentSlot(); }
		});
		deleteSlotBtn = new TextButton("删除…", kit.toolButton);
		deleteSlotBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showDeleteSlotDialog(); }
		});
		slotNameField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) refreshSlotManagementState();
			}
		});
		slotNameField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleSlotNameKey(keycode);
			}
		});
		Table slotManagement = new Table();
		slotManagement.add(slotNameField).growX().minWidth(0f).pad(2).height(26);
		slotManagement.add(renameSlotBtn).minWidth(68).pad(2).height(26);
		slotManagement.add(deleteSlotBtn).minWidth(68).pad(2).height(26);
		add(slotManagement).growX().minWidth(0f).colspan(3).row();

		Label slotPropertiesLabel = new Label("槽属性", kit.labelStyle);
		slotPropertiesLabel.setColor(0.6f, 0.85f, 0.9f, 1f);
		add(slotPropertiesLabel).left().pad(2).padTop(6).colspan(3).row();

		slotColorSwatch = new Table();
		slotColorSwatch.setBackground(kit.white.tint(Color.WHITE));
		slotColorField = new TextField("#FFFFFFFF", kit.textFieldStyle);
		slotColorField.setMessageText("#RRGGBB 或 #RRGGBBAA");
		slotColorField.setMaxLength(9);
		slotColorField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) {
					refreshSlotColorPreview();
					refreshSlotApplyState();
				}
			}
		});
		slotColorField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleSlotPropertyKey(keycode);
			}
		});
		addColorRow("颜色", slotColorField, slotColorSwatch);

		slotTwoColorBtn = new TextButton("已关闭", kit.toolButton);
		slotTwoColorBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				pendingSlotTwoColor = !pendingSlotTwoColor;
				refreshSlotTwoColorControls(true);
				refreshSlotApplyState();
			}
		});
		addLabeledControlRow("双色染色", slotTwoColorBtn);

		slotDarkColorSwatch = new Table();
		slotDarkColorSwatch.setBackground(kit.downDark);
		slotDarkColorField = new TextField("#000000", kit.textFieldStyle);
		slotDarkColorField.setMessageText("#RRGGBB");
		slotDarkColorField.setMaxLength(7);
		slotDarkColorField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) {
					refreshSlotDarkColorPreview();
					refreshSlotApplyState();
				}
			}
		});
		slotDarkColorField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleSlotPropertyKey(keycode);
			}
		});
		addColorRow("暗色", slotDarkColorField, slotDarkColorSwatch);

		slotBlendBox = kit.selectBox();
		slotBlendBox.setItems(localizedBlendModeLabels());
		slotBlendBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) refreshSlotApplyState();
			}
		});
		addLabeledControlRow("混合", slotBlendBox);

		slotVisibleBtn = new TextButton("可见", kit.activeToolButton);
		slotVisibleBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				pendingSlotVisible = !pendingSlotVisible;
				refreshSlotVisibilityButton(true);
				refreshSlotApplyState();
			}
		});
		applySlotBtn = new TextButton("应用槽属性", kit.accentButton);
		applySlotBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { applySlotProperties(); }
		});
		Table visibilityRow = new Table();
		visibilityRow.add(kit.controlLabel("可见性")).left().pad(2).padLeft(4).width(68);
		visibilityRow.add(slotVisibleBtn).growX().minWidth(0f).pad(2).height(26);
		visibilityRow.add(applySlotBtn).growX().minWidth(0f).pad(2).height(26);
		add(visibilityRow).growX().minWidth(0f).colspan(3).row();

		Label attLabel = new Label("附件", kit.labelStyle);
		attLabel.setColor(0.6f, 0.85f, 0.9f, 1f);
		add(attLabel).left().pad(2).padTop(6).colspan(3).row();

		attachBox = kit.selectBox();
		attachBox.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				boolean changed = applySelectedAttachment();
				refreshRegion();
				if (changed && onDataChanged != null) onDataChanged.run();
				notifyAttachmentSelection();
			}
		});
		add(attachBox).growX().pad(2).colspan(3).row();

		Label regionLabel = new Label("创建 / 编辑附件", kit.labelStyle);
		regionLabel.setColor(0.6f, 0.85f, 0.9f, 1f);
		add(regionLabel).left().pad(2).padTop(6).colspan(3).row();

		attachmentTypeBox = kit.selectBox();
		attachmentTypeBox.setItems("Region", "Mesh", "Linked Mesh", "Bounding Box", "Path", "Point", "Clipping");
		attachmentTypeBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) refreshCreationState();
			}
		});
		addLabeledControlRow("类型", attachmentTypeBox);

		regionBox = kit.selectBox();
		regionBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				if (placeholderField.getText().isBlank() && regionBox.getSelected() != null)
					placeholderField.setText(regionBox.getSelected());
				refreshCreationState();
			}
		});
		add(regionBox).growX().pad(2).colspan(3).row();

		linkedMeshSourceBox = kit.selectBox();
		linkedMeshSourceBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) refreshCreationState();
			}
		});
		addLabeledControlRow("源网格", linkedMeshSourceBox);
		linkedMeshTimelinesBtn = new TextButton("继承 Deform", kit.toolButton);
		linkedMeshTimelinesBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				pendingLinkedMeshTimelines = !pendingLinkedMeshTimelines;
				refreshLinkedMeshTimelinesButton();
			}
		});
		addLabeledControlRow("变形", linkedMeshTimelinesBtn);

		placeholderField = new TextField("", kit.textFieldStyle);
		placeholderField.setMessageText("附件名称");
		placeholderField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) refreshCreationState();
			}
		});
		addRegionBtn = new TextButton("添加 / 替换", kit.toolButton);
		addRegionBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { createOrReplaceAttachment(); }
		});
		editMeshBtn = new TextButton("编辑网格", kit.toolButton);
		editMeshBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { editCurrentAttachment(); }
		});
		sequenceBtn = new TextButton("图片序列…", kit.toolButton);
		sequenceBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { editCurrentSequence(); }
		});
		removeRegionBtn = new TextButton("删除当前", kit.toolButton);
		removeRegionBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { removeCurrentAttachment(); }
		});
		add(placeholderField).growX().pad(2).height(26).colspan(3).row();
		Table regionButtons = new Table();
		regionButtons.add(addRegionBtn).growX().minWidth(0f).pad(2).height(26);
		regionButtons.add(editMeshBtn).growX().minWidth(0f).pad(2).height(26);
		regionButtons.add(removeRegionBtn).growX().minWidth(0f).pad(2).height(26);
		add(regionButtons).growX().colspan(3).row();
		add(sequenceBtn).growX().pad(2).height(26).colspan(3).row();

		regionColorSwatch = new Table();
		regionColorSwatch.setBackground(kit.white.tint(Color.WHITE));
		regionColorField = new TextField("#FFFFFFFF", kit.textFieldStyle);
		regionColorField.setMessageText("#RRGGBB 或 #RRGGBBAA");
		regionColorField.setMaxLength(9);
		regionColorField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) {
					refreshRegionColorPreview();
					refreshRegionApplyState();
				}
			}
		});
		regionColorField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleRegionPropertyKey(keycode);
			}
		});
		addColorRow("染色", regionColorField, regionColorSwatch);

		xRow = new FloatRow("X", -5000, 5000);
		yRow = new FloatRow("Y", -5000, 5000);
		sxRow = new FloatRow("缩放 X", -20, 20);
		syRow = new FloatRow("缩放 Y", -20, 20);
		rRow = new FloatRow("旋转", -3600, 3600);
		wRow = new FloatRow("宽", 1, 2000);
		hRow = new FloatRow("高", 1, 2000);
		addRow(xRow);
		addRow(yRow);
		addRow(sxRow);
		addRow(syRow);
		addRow(rRow);
		addRow(wRow);
		addRow(hRow);

		applyRegionBtn = new TextButton("应用到附件", kit.accentButton);
		applyRegionBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { applyRegionProperties(); }
		});
		add(applyRegionBtn).growX().pad(3).colspan(3).row();

		skinTitle = new Label("皮肤", kit.labelStyle);
		skinTitle.setColor(0.6f, 0.85f, 0.9f, 1f);
		add(skinTitle).left().pad(2).padTop(10).colspan(3).row();

		skinBox = kit.selectBox();
		skinBox.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (updating) return;
				String skinName = skinBox.getSelected();
				if (doc.setSkin(skinName)) {
					if (onSkinChanged != null) onSkinChanged.run();
				}
			}
		});
		skinCombinationBtn = UiKit.asActionButton(new TextButton("组合…", kit.toolButton));
		skinCombinationBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				SkinCombinationDialog.show(getStage(), kit, doc, history, onSkinChanged);
			}
		});
		Table skinSelection = new Table();
		skinSelection.add(skinBox).growX().minWidth(0f).height(26f).padRight(4f);
		skinSelection.add(skinCombinationBtn).width(70f).height(26f);
		add(skinSelection).growX().pad(2).colspan(3).row();

		skinNameField = new TextField("", kit.textFieldStyle);
		skinNameField.setMessageText("皮肤名称");
		skinNameField.setMaxLength(64);
		renameSkinBtn = new TextButton("重命名", kit.toolButton);
		renameSkinBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { renameCurrentSkin(); }
		});
		skinNameField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!updating) refreshSkinManagementState();
			}
		});
		skinNameField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleSkinNameKey(keycode);
			}
		});
		Table skinManagement = new Table();
		skinManagement.add(skinNameField).growX().minWidth(0f).pad(2).height(26);
		skinManagement.add(renameSkinBtn).minWidth(68).pad(2).height(26);
		add(skinManagement).growX().minWidth(0f).colspan(3).row();

		Table skinBtns = new Table();
		addSkinBtn = new TextButton("+ 空白", kit.toolButton);
		addSkinBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { createBlankSkin(); }
		});
		duplicateSkinBtn = new TextButton("复制", kit.toolButton);
		duplicateSkinBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { duplicateCurrentSkin(); }
		});
		delSkinBtn = new TextButton("删除…", kit.toolButton);
		delSkinBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showDeleteSkinDialog(); }
		});
		skinBtns.add(addSkinBtn).growX().pad(2);
		skinBtns.add(duplicateSkinBtn).growX().pad(2);
		skinBtns.add(delSkinBtn).growX().pad(2);
		add(skinBtns).growX().pad(2).colspan(3);
		skinBonesBtn = new TextButton("皮肤骨骼…", kit.toolButton);
		skinBonesBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showSkinBonesDialog(); }
		});
		add(skinBonesBtn).growX().pad(2).colspan(3).row();
	}

	private void addLabeledControlRow(String label, Actor control) {
		Table line = new Table();
		line.add(kit.controlLabel(label)).left().pad(2).padLeft(4).width(68);
		line.add(control).growX().minWidth(0f).pad(2).height(26);
		add(line).growX().minWidth(0f).colspan(3).row();
	}

	private void addColorRow(String label, TextField field, Table swatch) {
		Table line = new Table();
		line.add(kit.controlLabel(label)).left().pad(2).padLeft(4).width(68);
		line.add(field).growX().minWidth(0f).pad(2).height(26);
		line.add(swatch).pad(3).width(24).height(20);
		add(line).growX().minWidth(0f).colspan(3).row();
	}

	private void addRow(FloatRow row) {
		Table line = new Table();
		line.add(row.text).left().pad(2).padLeft(4).width(68);
		line.add(row.field).pad(2).width(64).height(24);
		line.add(row.slider).growX().minWidth(72).padRight(6).height(24);
		add(line).growX().minWidth(0f).colspan(3).row();
	}

	private void refreshAttachments() { refreshAttachments(null); }

	private void refreshAttachments(String preferredAttachment) {
		updating = true;
		SlotData slot = currentSlot();
		Array<String> items = new Array<>();
		if (slot != null) items.add(NONE_ATTACHMENT);
		if (slot != null) items.addAll(editor.attachmentNames(slot.getIndex(), null));
		attachBox.setItems(items);
		if (slot != null && preferredAttachment != null
			&& attachBox.getItems().contains(preferredAttachment, false))
			attachBox.setSelected(preferredAttachment);
		else if (slot != null && slot.getAttachmentName() != null
			&& attachBox.getItems().contains(slot.getAttachmentName(), false))
			attachBox.setSelected(slot.getAttachmentName());
		else if (slot != null)
			attachBox.setSelected(NONE_ATTACHMENT);
		attachBox.setDisabled(slot == null);
		updating = false;
		refreshSlotProperties();
		refreshRegion();
	}

	private void refreshSlotProperties() {
		updating = true;
		SlotData slot = currentSlot();
		boolean enabled = slot != null;
		refreshSlotOrderState();
		if (enabled) {
			slotNameField.setText(slot.getName());
			slotColorField.setText(UiColors.format(slot.getSetupPose().getColor()));
			Color darkColor = slot.getSetupPose().getDarkColor();
			pendingSlotTwoColor = darkColor != null;
			slotDarkColorField.setText(darkColor == null ? "#000000" : UiColors.formatRgb(darkColor));
			BlendMode blendMode = slot.getBlendMode() == null ? BlendMode.normal : slot.getBlendMode();
			slotBlendBox.setSelected(localizedBlendModeLabel(blendMode));
			pendingSlotVisible = slot.getVisible();
		} else {
			slotNameField.setText("");
			slotColorField.setText("");
			slotDarkColorField.setText("");
			pendingSlotTwoColor = false;
			slotBlendBox.setSelected(localizedBlendModeLabel(BlendMode.normal));
			pendingSlotVisible = true;
		}
		refreshSlotManagementState();
		slotColorField.setDisabled(!enabled);
		slotBlendBox.setDisabled(!enabled);
		refreshSlotVisibilityButton(enabled);
		refreshSlotColorPreview();
		refreshSlotTwoColorControls(enabled);
		slotBaseline = enabled ? currentSlotDraft() : null;
		updating = false;
		refreshSlotApplyState();
	}

	private void refreshSlotColorPreview() {
		if (currentSlot() == null) {
			slotColorSwatch.setBackground(kit.downDark);
			return;
		}
		try {
			slotColorSwatch.setBackground(kit.white.tint(UiColors.parse(slotColorField.getText())));
		} catch (IllegalArgumentException ignored) {
			slotColorSwatch.setBackground(kit.white.tint(new Color(0.65f, 0.18f, 0.2f, 1f)));
		}
	}

	private void refreshSlotManagementState() {
		SlotData slot = currentSlot();
		boolean hasSlot = slot != null;
		slotNameField.setDisabled(!hasSlot);
		renameSlotBtn.setDisabled(!hasSlot || !nameDraftChanged(slot.getName(), slotNameField.getText()));
		deleteSlotBtn.setDisabled(!hasSlot);
	}

	private SlotDraft currentSlotDraft() {
		return slotDraft(slotColorField.getText(), pendingSlotTwoColor, slotDarkColorField.getText(),
			slotBlendBox.getSelected(), pendingSlotVisible);
	}

	private void refreshSlotApplyState() {
		applySlotBtn.setDisabled(currentSlot() == null || slotBaseline == null
			|| slotBaseline.equals(currentSlotDraft()));
	}

	private boolean renameCurrentSlot() {
		SlotData slot = currentSlot();
		String requested = slotNameField.getText() == null ? "" : slotNameField.getText().trim();
		if (slot == null) return false;
		if (requested.isEmpty()) {
			showStatus("槽名称不能为空", true);
			return false;
		}
		String renamed = editor.renameSlot(slot, requested);
		if (renamed == null) {
			showStatus("无法重命名当前槽", true);
			return false;
		}
		if (renamed.equals(slot.getName())) {
			showStatus("槽名称未变化", false);
			return true;
		}
		pendingSlotSelection = renamed;
		if (onDataChanged != null) onDataChanged.run();
		return true;
	}

	private void showDeleteSlotDialog() {
		SlotData slot = currentSlot();
		Stage stage = getStage();
		if (slot == null || stage == null) return;
		String blockReason = editor.getSlotDeleteBlockReason(slot);
		if (blockReason != null) {
			showStatus("无法删除槽：" + blockReason, true);
			return;
		}
		UiDialogs.confirm(stage, kit, "删除槽",
			"删除槽 " + slot.getName() + "？", "删除", () -> {
				var before = com.openspine.model.SkeletonStructureSnapshot.capture(editor);
				if (!editor.deleteSlot(slot)) {
					showStatus("无法删除槽：引用状态已变化", true);
					return;
				}
				if (history != null) {
					var after = com.openspine.model.SkeletonStructureSnapshot.capture(editor);
					history.record("删除槽", before::restore, after::restore);
				} else if (onDataChanged != null) onDataChanged.run();
			});
	}

	private void refreshSlotVisibilityButton(boolean enabled) {
		slotVisibleBtn.setText(pendingSlotVisible ? I18n.t("可见", "Visible") : I18n.t("隐藏", "Hidden"));
		slotVisibleBtn.setStyle(enabled && pendingSlotVisible ? kit.activeToolButton : kit.toolButton);
		slotVisibleBtn.setDisabled(!enabled);
	}

	private void refreshSlotTwoColorControls(boolean enabled) {
		slotTwoColorBtn.setText(pendingSlotTwoColor
			? I18n.t("已启用", "Enabled") : I18n.t("已关闭", "Disabled"));
		slotTwoColorBtn.setStyle(enabled && pendingSlotTwoColor ? kit.activeToolButton : kit.toolButton);
		slotTwoColorBtn.setDisabled(!enabled);
		slotDarkColorField.setDisabled(!enabled || !pendingSlotTwoColor);
		refreshSlotDarkColorPreview();
	}

	private void refreshSlotDarkColorPreview() {
		if (currentSlot() == null || !pendingSlotTwoColor) {
			slotDarkColorSwatch.setBackground(kit.downDark);
			return;
		}
		try {
			slotDarkColorSwatch.setBackground(kit.white.tint(UiColors.parseRgb(slotDarkColorField.getText())));
		} catch (IllegalArgumentException ignored) {
			slotDarkColorSwatch.setBackground(kit.white.tint(new Color(0.65f, 0.18f, 0.2f, 1f)));
		}
	}

	private boolean applySlotProperties() {
		SlotData slot = currentSlot();
		if (slot == null) return false;
		SlotPropertyState before = slotPropertyState(slot);
		Color[] colors;
		try {
			colors = parseSlotColors(slotColorField.getText(), pendingSlotTwoColor, slotDarkColorField.getText());
		} catch (IllegalArgumentException error) {
			showStatus(error.getMessage(), true);
			refreshSlotColorPreview();
			refreshSlotDarkColorPreview();
			return false;
		}
		Color color = colors[0];
		Color darkColor = colors[1];
		BlendMode blendMode = selectedBlendMode();
		if (blendMode == null) {
			showStatus("混合模式无效", true);
			return false;
		}
		if (!pendingSlotTwoColor) {
			String blockReason = editor.getSlotDarkColorDisableBlockReason(slot);
			if (blockReason != null) {
				showStatus("无法关闭双色染色：" + blockReason, true);
				return false;
			}
		}
		if (!editor.updateSlotProperties(slot, color, darkColor, blendMode, pendingSlotVisible)) {
			showStatus("槽属性未变化", false);
			return true;
		}
		slotColorField.setText(UiColors.format(color));
		if (darkColor != null) slotDarkColorField.setText(UiColors.formatRgb(darkColor));
		refreshSlotColorPreview();
		refreshSlotDarkColorPreview();
		slotBaseline = currentSlotDraft();
		refreshSlotApplyState();
		if (history != null) recordSlotPropertyChange(history, editor, slot, before);
		else if (onDataChanged != null) onDataChanged.run();
		showStatus("槽属性已应用", false);
		return true;
	}

	static Color[] parseSlotColors(String colorText, boolean twoColor, String darkColorText) {
		Color color = UiColors.parse(colorText);
		Color darkColor = twoColor ? UiColors.parseRgb(darkColorText) : null;
		return new Color[] {color, darkColor};
	}

	static boolean nameDraftChanged(String currentName, String draftName) {
		if (currentName == null || draftName == null) return false;
		String requested = draftName.trim();
		return !requested.isEmpty() && !currentName.equals(requested);
	}

	static SlotDraft slotDraft(String color, boolean twoColor, String darkColor, String blendMode,
		boolean visible) {
		return new SlotDraft(UiDrafts.color(color, false), twoColor,
			twoColor ? UiDrafts.color(darkColor, true) : null, blendMode, visible);
	}

	static RegionDraft regionDraft(String color, String... values) {
		if (values == null || values.length != REGION_VALUE_LABELS.length)
			throw new IllegalArgumentException("附件属性数值不完整");
		return new RegionDraft(UiDrafts.color(color, false), UiDrafts.number(values[0]),
			UiDrafts.number(values[1]), UiDrafts.number(values[2]), UiDrafts.number(values[3]),
			UiDrafts.number(values[4]), UiDrafts.number(values[5]), UiDrafts.number(values[6]));
	}

	static SlotPropertyState slotPropertyState(SlotData slot) {
		if (slot == null) throw new IllegalArgumentException("槽不能为空");
		Color darkColor = slot.getSetupPose().getDarkColor();
		BlendMode blendMode = slot.getBlendMode() == null ? BlendMode.normal : slot.getBlendMode();
		return new SlotPropertyState(new Color(slot.getSetupPose().getColor()),
			darkColor == null ? null : new Color(darkColor), blendMode, slot.getVisible());
	}

	static void recordSlotPropertyChange(EditHistory history, SkeletonEditor editor, SlotData slot,
		SlotPropertyState before) {
		if (history == null || editor == null || slot == null || before == null)
			throw new IllegalArgumentException("槽属性历史参数不完整");
		SlotPropertyState after = slotPropertyState(slot);
		history.record("编辑槽属性", () -> restoreSlotPropertyState(editor, slot, before),
			() -> restoreSlotPropertyState(editor, slot, after));
	}

	private static void restoreSlotPropertyState(SkeletonEditor editor, SlotData slot, SlotPropertyState state) {
		Color darkColor = state.darkColor() == null ? null : new Color(state.darkColor());
		if (!editor.updateSlotProperties(slot, new Color(state.color()), darkColor, state.blendMode(), state.visible()))
			throw new IllegalStateException("无法恢复槽属性：" + slot.getName());
	}

	private static String blendModeLabel(BlendMode blendMode) {
		return switch (blendMode) {
		case additive -> "叠加";
		case multiply -> "正片叠底";
		case screen -> "滤色";
		default -> "正常";
		};
	}

	private static String localizedBlendModeLabel(BlendMode blendMode) {
		return I18n.t(blendModeLabel(blendMode));
	}

	private static String[] localizedBlendModeLabels() {
		return new String[] {I18n.t("正常"), I18n.t("叠加"), I18n.t("正片叠底"), I18n.t("滤色")};
	}

	private BlendMode selectedBlendMode() {
		String selected = slotBlendBox.getSelected();
		if (I18n.matches(selected, "叠加", "Additive")) return BlendMode.additive;
		if (I18n.matches(selected, "正片叠底", "Multiply")) return BlendMode.multiply;
		if (I18n.matches(selected, "滤色", "Screen")) return BlendMode.screen;
		if (I18n.matches(selected, "正常", "Normal")) return BlendMode.normal;
		return null;
	}

	private void refreshRegion() {
		updating = true;
		RegionAttachment att = currentRegion();
		if (att == null) {
			Attachment current = currentAttachment();
			MeshAttachment mesh = current instanceof MeshAttachment ? (MeshAttachment)current : null;
			refreshLinkedMeshSources(mesh == null ? null : mesh.getSourceMesh());
			regionBaseline = null;
			setRegionControlsEnabled(false);
			regionColorField.setText("");
			refreshRegionColorPreview();
			if (mesh != null) {
				boolean linked = mesh.getSourceMesh() != null;
				attachmentTypeBox.setSelected(linked ? "Linked Mesh" : "Mesh");
				pendingLinkedMeshTimelines = linked && mesh.getTimelineAttachment() != mesh;
				placeholderField.setText(attachBox.getSelected());
				if (mesh.getPath() != null && regionBox.getItems().contains(mesh.getPath(), false))
					regionBox.setSelected(mesh.getPath());
			} else if (isStandardAttachment(current)) {
				attachmentTypeBox.setSelected(standardAttachmentTypeLabel(current));
				placeholderField.setText(attachBox.getSelected());
			} else if (placeholderField.getText().isBlank() && regionBox.getSelected() != null) {
				placeholderField.setText(regionBox.getSelected());
			}
			updating = false;
			refreshCreationState();
			return;
		}
		attachmentTypeBox.setSelected("Region");
		refreshLinkedMeshSources(null);
		setRegionControlsEnabled(true);
		placeholderField.setText(attachBox.getSelected());
		regionColorField.setText(UiColors.format(att.getColor()));
		refreshRegionColorPreview();
		if (att.getPath() != null && regionBox.getItems().contains(att.getPath(), false))
			regionBox.setSelected(att.getPath());
		xRow.set(att.getX());
		yRow.set(att.getY());
		sxRow.set(att.getScaleX());
		syRow.set(att.getScaleY());
		rRow.set(att.getRotation());
		wRow.set(att.getWidth());
		hRow.set(att.getHeight());
		regionBaseline = currentRegionDraft();
		updating = false;
		refreshCreationState();
		refreshRegionApplyState();
	}

	private void refreshCreationState() {
		boolean hasTarget = currentSlot() != null && currentSkin() != null;
		boolean hasRegion = regionBox.getSelected() != null && !regionBox.getItems().isEmpty();
		String selectedType = attachmentTypeBox.getSelected();
		boolean linkedMode = "Linked Mesh".equals(selectedType);
		boolean imageMode = "Region".equals(selectedType) || "Mesh".equals(selectedType) || linkedMode;
		String placeholder = placeholderField.getText();
		boolean hasName = placeholder != null && !placeholder.trim().isEmpty()
			&& !NONE_ATTACHMENT.equals(placeholder.trim());
		String selectedAttachment = attachBox.getSelected();
		boolean ownsSelected = hasTarget && selectedAttachment != null && !NONE_ATTACHMENT.equals(selectedAttachment)
			&& editor.skinHasAttachment(currentSkin(), currentSlot().getIndex(), selectedAttachment);
		Attachment current = currentAttachment();
		boolean editingLinked = editsCurrentLinkedMesh(selectedType, current, selectedAttachment, placeholder,
			ownsSelected);
		regionBox.setDisabled(!hasTarget || !imageMode || regionBox.getItems().isEmpty());
		linkedMeshSourceBox.setDisabled(!hasTarget || !linkedMode || linkedMeshSources.isEmpty());
		linkedMeshTimelinesBtn.setDisabled(!hasTarget || !linkedMode || linkedMeshSources.isEmpty());
		refreshLinkedMeshTimelinesButton();
		placeholderField.setDisabled(!hasTarget);
		attachmentTypeBox.setDisabled(!hasTarget);
		addRegionBtn.setText(editingLinked ? "应用 Linked Mesh" : "创建 "
			+ (selectedType == null ? "附件" : selectedType));
		addRegionBtn.setDisabled(!hasTarget || !hasName || imageMode && !hasRegion
			|| linkedMode && selectedLinkedMeshSource() == null);
		boolean editableMesh = current instanceof MeshAttachment mesh && mesh.getSourceMesh() == null;
		boolean editableStandard = isStandardAttachment(current);
		editMeshBtn.setText(editableMesh ? "编辑网格" : "编辑属性");
		editMeshBtn.setDisabled(!editableMesh && !editableStandard);
		sequenceBtn.setDisabled(!(current instanceof RegionAttachment || current instanceof MeshAttachment));
		removeRegionBtn.setDisabled(!ownsSelected);
	}

	private void refreshLinkedMeshSources(MeshAttachment preferredSource) {
		String previousLabel = linkedMeshSourceBox.getSelected();
		linkedMeshSources.clear();
		linkedMeshSources.addAll(editor.meshAttachmentSources());
		Array<String> labels = new Array<>();
		int preferredIndex = -1;
		for (int index = 0; index < linkedMeshSources.size; index++) {
			SkeletonEditor.MeshAttachmentSource source = linkedMeshSources.get(index);
			String label = linkedMeshSourceLabel(source);
			labels.add(label);
			if (source.mesh() == preferredSource || preferredSource == null && label.equals(previousLabel))
				preferredIndex = index;
		}
		linkedMeshSourceBox.setItems(labels);
		if (preferredIndex >= 0) linkedMeshSourceBox.setSelectedIndex(preferredIndex);
		else if (labels.notEmpty()) linkedMeshSourceBox.setSelectedIndex(0);
	}

	private SkeletonEditor.MeshAttachmentSource selectedLinkedMeshSource() {
		int index = linkedMeshSourceBox.getSelectedIndex();
		return index < 0 || index >= linkedMeshSources.size ? null : linkedMeshSources.get(index);
	}

	static String linkedMeshSourceLabel(SkeletonEditor.MeshAttachmentSource source) {
		if (source == null || source.skin() == null || source.slot() == null || source.placeholder() == null)
			return "";
		return source.skin().getName() + " · " + source.slot().getName() + " · " + source.placeholder();
	}

	private void refreshLinkedMeshTimelinesButton() {
		linkedMeshTimelinesBtn.setText(pendingLinkedMeshTimelines ? "继承 Deform" : "独立 Deform");
		linkedMeshTimelinesBtn.setStyle(pendingLinkedMeshTimelines ? kit.activeToolButton : kit.toolButton);
	}

	private boolean applyRegionProperties() {
		RegionAttachment attachment = currentRegion();
		if (attachment == null) return false;
		RegionPropertyState before = regionPropertyState(attachment);
		Color color;
		try {
			color = UiColors.parse(regionColorField.getText());
		} catch (IllegalArgumentException error) {
			showStatus(error.getMessage(), true);
			refreshRegionColorPreview();
			return false;
		}
		float[] values;
		try {
			values = parseRegionValues(xRow.field.getText(), yRow.field.getText(), sxRow.field.getText(),
				syRow.field.getText(), rRow.field.getText(), wRow.field.getText(), hRow.field.getText());
		} catch (IllegalArgumentException error) {
			showStatus(error.getMessage(), true);
			return false;
		}
		if (values[5] <= 0f || values[6] <= 0f) {
			showStatus("附件宽度和高度必须大于 0", true);
			return false;
		}
		xRow.accept(values[0]);
		yRow.accept(values[1]);
		sxRow.accept(values[2]);
		syRow.accept(values[3]);
		rRow.accept(values[4]);
		wRow.accept(values[5]);
		hRow.accept(values[6]);
		regionColorField.setText(UiColors.format(color));
		refreshRegionColorPreview();
		if (!editor.updateRegionAttachment(attachment, color, values[0], values[1], values[2], values[3],
			values[4], values[5], values[6])) {
			showStatus("附件属性未变化", false);
			return true;
		}
		regionBaseline = currentRegionDraft();
		refreshRegionApplyState();
		if (history != null) recordRegionPropertyChange(history, editor, attachment, before);
		else if (onDataChanged != null) onDataChanged.run();
		showStatus("附件属性已应用", false);
		return true;
	}

	private RegionDraft currentRegionDraft() {
		return regionDraft(regionColorField.getText(), xRow.field.getText(), yRow.field.getText(),
			sxRow.field.getText(), syRow.field.getText(), rRow.field.getText(), wRow.field.getText(),
			hRow.field.getText());
	}

	private void refreshRegionApplyState() {
		applyRegionBtn.setDisabled(currentRegion() == null || regionBaseline == null
			|| regionBaseline.equals(currentRegionDraft()));
	}

	static float[] parseRegionValues(String... texts) {
		if (texts == null || texts.length != REGION_VALUE_LABELS.length)
			throw new IllegalArgumentException("附件属性数值不完整");
		float[] values = new float[texts.length];
		for (int index = 0; index < texts.length; index++) {
			try {
				values[index] = UiNumbers.parseFinite(texts[index]);
			} catch (RuntimeException error) {
				throw new IllegalArgumentException(REGION_VALUE_LABELS[index] + " 必须是有限数值", error);
			}
		}
		return values;
	}

	static RegionPropertyState regionPropertyState(RegionAttachment attachment) {
		if (attachment == null) throw new IllegalArgumentException("区域附件不能为空");
		return new RegionPropertyState(new Color(attachment.getColor()), attachment.getX(), attachment.getY(),
			attachment.getScaleX(), attachment.getScaleY(), attachment.getRotation(), attachment.getWidth(),
			attachment.getHeight());
	}

	static void recordRegionPropertyChange(EditHistory history, SkeletonEditor editor,
		RegionAttachment attachment, RegionPropertyState before) {
		if (history == null || editor == null || attachment == null || before == null)
			throw new IllegalArgumentException("附件属性历史参数不完整");
		RegionPropertyState after = regionPropertyState(attachment);
		history.record("编辑附件属性", () -> restoreRegionPropertyState(editor, attachment, before),
			() -> restoreRegionPropertyState(editor, attachment, after));
	}

	private static void restoreRegionPropertyState(SkeletonEditor editor, RegionAttachment attachment,
		RegionPropertyState state) {
		if (!editor.updateRegionAttachment(attachment, new Color(state.color()), state.x(), state.y(),
			state.scaleX(), state.scaleY(), state.rotation(), state.width(), state.height()))
			throw new IllegalStateException("无法恢复附件属性：" + attachment.getName());
	}

	private void refreshRegionColorPreview() {
		if (currentRegion() == null) {
			regionColorSwatch.setBackground(kit.downDark);
			return;
		}
		try {
			regionColorSwatch.setBackground(kit.white.tint(UiColors.parse(regionColorField.getText())));
		} catch (IllegalArgumentException ignored) {
			regionColorSwatch.setBackground(kit.white.tint(new Color(0.65f, 0.18f, 0.2f, 1f)));
		}
	}

	private void createOrReplaceAttachment() {
		SlotData slot = currentSlot();
		Skin skin = currentSkin();
		String region = regionBox.getSelected();
		String placeholder = placeholderField.getText() == null ? "" : placeholderField.getText().trim();
		String selectedType = attachmentTypeBox.getSelected();
		boolean linkedMode = "Linked Mesh".equals(selectedType);
		boolean imageMode = "Region".equals(selectedType) || "Mesh".equals(selectedType) || linkedMode;
		if (slot == null || skin == null || imageMode && region == null || placeholder.isEmpty()
			|| NONE_ATTACHMENT.equals(placeholder)) return;
		boolean meshMode = "Mesh".equals(selectedType);
		SkeletonEditor.MeshAttachmentSource linkedSource = selectedLinkedMeshSource();
		StandardAttachmentType standardType = selectedStandardAttachmentType(selectedType);
		String selectedAttachment = attachBox.getSelected();
		Attachment current = currentAttachment();
		boolean ownsSelected = selectedAttachment != null && !NONE_ATTACHMENT.equals(selectedAttachment)
			&& editor.skinHasAttachment(skin, slot.getIndex(), selectedAttachment);
		boolean updatingLinked = editsCurrentLinkedMesh(selectedType, current, selectedAttachment, placeholder,
			ownsSelected);
		String blockReason = standardType != null
			? editor.getStandardAttachmentCreationBlockReason(skin, slot, placeholder)
			: linkedMode ? updatingLinked
				? editor.getLinkedMeshUpdateBlockReason(skin, slot, placeholder, region, linkedSource,
					pendingLinkedMeshTimelines)
				: editor.getLinkedMeshCreationBlockReason(skin, slot, placeholder, region, linkedSource)
			: meshMode ? editor.getMeshReplacementBlockReason(skin, slot, placeholder)
			: editor.getRegionReplacementBlockReason(skin, slot, placeholder);
		if (blockReason != null) {
			showStatus("无法" + (updatingLinked ? "更新" : "创建") + "附件：" + blockReason, true);
			return;
		}
		long revision = doc.revision;
		Attachment attachment = standardType != null
			? editor.createStandardAttachment(skin, slot, placeholder, standardType)
			: linkedMode ? updatingLinked
				? editor.updateLinkedMeshAttachment(skin, slot, placeholder, region, linkedSource,
					pendingLinkedMeshTimelines)
				: editor.createLinkedMeshAttachment(skin, slot, placeholder, region,
					linkedSource, pendingLinkedMeshTimelines)
			: meshMode ? editor.setSlotMeshAttachment(skin, slot, placeholder, region)
			: editor.setSlotRegionAttachment(skin, slot, placeholder, region);
		if (attachment == null) {
			showStatus("无法" + (updatingLinked ? "更新" : "创建")
				+ "附件：输入数据、图集区域或目标皮肤已失效", true);
			return;
		}
		if (doc.revision == revision) {
			showStatus("附件和图集区域未变化", false);
			return;
		}
		pendingAttachmentSelection = placeholder;
		if (onDataChanged != null) onDataChanged.run();
		showStatus((selectedType == null ? "附件" : selectedType) + (updatingLinked ? " 已更新" : " 已创建"), false);
	}

	private void editCurrentAttachment() {
		Attachment attachment = currentAttachment();
		Stage stage = getStage();
		if (attachment == null || stage == null) return;
		if (attachment instanceof MeshAttachment mesh) {
			SlotData slot = currentSlot();
			if (mesh.getSourceMesh() == null && slot != null)
				MeshGeometryDialog.show(stage, kit, doc, editor, mesh, slot, history, onDataChanged, onStatus);
		} else if (isStandardAttachment(attachment)) {
			StandardAttachmentDialog.show(stage, kit, doc, editor, attachment, history, onDataChanged, onStatus);
		}
	}

	private void editCurrentSequence() {
		Attachment attachment = currentAttachment();
		Stage stage = getStage();
		if (stage == null || !(attachment instanceof RegionAttachment || attachment instanceof MeshAttachment)) return;
		SequenceAttachmentDialog.show(stage, kit, doc, editor, attachment, history, onDataChanged, onStatus);
	}

	private void removeCurrentAttachment() {
		SlotData slot = currentSlot();
		Skin skin = currentSkin();
		String placeholder = attachBox.getSelected();
		if (slot == null || skin == null || placeholder == null || NONE_ATTACHMENT.equals(placeholder)) return;
		String blockReason = editor.getAttachmentDeleteBlockReason(skin, slot, placeholder);
		if (blockReason != null) {
			showStatus("无法删除附件：" + blockReason, true);
			return;
		}
		Stage stage = getStage();
		if (stage == null) return;
		UiDialogs.confirm(stage, kit, "删除附件",
			"从皮肤 " + skin.getName() + " 删除附件 " + placeholder + "？"
				+ (history == null ? "此操作无法撤销。" : "可通过撤销恢复。"), "删除", () -> {
				var before = history == null ? null
					: com.openspine.model.SkeletonStructureSnapshot.capture(editor);
				if (!editor.removeSkinAttachment(skin, slot, placeholder)) {
					String currentReason = editor.getAttachmentDeleteBlockReason(skin, slot, placeholder);
					showStatus("无法删除附件：" + (currentReason == null ? "附件状态已变化" : currentReason), true);
					return;
				}
				if (history != null) {
					var after = com.openspine.model.SkeletonStructureSnapshot.capture(editor);
					history.record("删除附件", before::restore, after::restore);
					if (onReversibleDataChanged != null) onReversibleDataChanged.run();
				} else if (onDataChanged != null) onDataChanged.run();
			});
	}

	private void addSlotForSelectedBone() {
		Bone bone = doc.getSelectedBone();
		if (bone == null) return;
		SlotData slot = editor.addSlot(bone.getData().getName() + ".slot", bone.getData());
		if (slot == null) {
			showStatus("无法为当前骨骼新建槽", true);
			return;
		}
		pendingSlotSelection = slot.getName();
		if (onDataChanged != null) onDataChanged.run();
	}

	private void setRegionControlsEnabled(boolean enabled) {
		regionColorField.setDisabled(!enabled);
		xRow.setEnabled(enabled);
		yRow.setEnabled(enabled);
		sxRow.setEnabled(enabled);
		syRow.setEnabled(enabled);
		rRow.setEnabled(enabled);
		wRow.setEnabled(enabled);
		hRow.setEnabled(enabled);
		applyRegionBtn.setDisabled(true);
	}

	private boolean applySelectedAttachment() {
		SlotData slotData = currentSlot();
		String placeholder = attachBox.getSelected();
		if (slotData == null || placeholder == null) return false;
		return editor.setSlotAttachment(slotData, NONE_ATTACHMENT.equals(placeholder) ? null : placeholder);
	}

	private void notifyAttachmentSelection() {
		if (onAttachmentSelectionChanged != null)
			onAttachmentSelectionChanged.accept(currentSlot(), currentAttachment());
	}

	private SlotData currentSlot() {
		if (slotBox.getSelected() == null) return null;
		return doc.data == null ? null : doc.data.findSlot(slotBox.getSelected());
	}

	/** Prepares the exact runtime attachment hit in the viewport for the next panel refresh. */
	public void selectViewportAttachment(Slot slot, Attachment attachment) {
		if (doc.skeleton == null || slot == null || attachment == null
			|| !doc.skeleton.getSlots().contains(slot, true)) return;
		pendingSlotSelection = slot.getData().getName();
		pendingAttachmentSelection = attachmentPlaceholder(doc, slot, attachment);
	}

	/** Prepares a slot or attachment selected directly in the hierarchy tree. */
	public void selectTreeSlot(Slot slot, Attachment attachment) {
		if (doc.skeleton == null || slot == null || !doc.skeleton.getSlots().contains(slot, true)) return;
		pendingSlotSelection = slot.getData().getName();
		if (attachment == null) {
			pendingAttachmentSelection = NONE_ATTACHMENT;
			return;
		}
		String placeholder = attachmentPlaceholder(doc, slot, attachment);
		pendingAttachmentSelection = placeholder == null ? slot.getData().getAttachmentName() : placeholder;
	}

	static String attachmentPlaceholder(SkeletonDocument doc, Slot slot, Attachment attachment) {
		if (doc == null || doc.data == null || doc.skeleton == null || slot == null || attachment == null) return null;
		String placeholder = attachmentPlaceholder(doc.skeleton.getSkin(), slot.getData().getIndex(), attachment);
		if (placeholder != null) return placeholder;
		Skin defaultSkin = doc.data.getDefaultSkin();
		return defaultSkin == doc.skeleton.getSkin() ? null
			: attachmentPlaceholder(defaultSkin, slot.getData().getIndex(), attachment);
	}

	private static String attachmentPlaceholder(Skin skin, int slotIndex, Attachment attachment) {
		if (skin == null) return null;
		Array<Skin.SkinEntry> entries = new Array<>();
		skin.getAttachments(slotIndex, entries);
		for (Skin.SkinEntry entry : entries) {
			if (entry.getAttachment() == attachment) return entry.getPlaceholder();
		}
		return null;
	}

	private RegionAttachment currentRegion() {
		SlotData slot = currentSlot();
		if (slot == null || attachBox.getSelected() == null || NONE_ATTACHMENT.equals(attachBox.getSelected())) return null;
		var a = editor.currentAttachment(slot.getIndex(), attachBox.getSelected());
		return a instanceof RegionAttachment ? (RegionAttachment) a : null;
	}

	private MeshAttachment currentMesh() {
		Attachment attachment = currentAttachment();
		return attachment instanceof MeshAttachment ? (MeshAttachment)attachment : null;
	}

	private Attachment currentAttachment() {
		SlotData slot = currentSlot();
		if (slot == null || attachBox.getSelected() == null || NONE_ATTACHMENT.equals(attachBox.getSelected())) return null;
		return editor.currentAttachment(slot.getIndex(), attachBox.getSelected());
	}

	private static boolean isStandardAttachment(Attachment attachment) {
		if (attachment == null) return false;
		try {
			SkeletonEditor.standardAttachmentState(attachment);
			return true;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static String standardAttachmentTypeLabel(Attachment attachment) {
		return StandardAttachmentDialog.typeLabel(SkeletonEditor.standardAttachmentState(attachment).type());
	}

	static StandardAttachmentType selectedStandardAttachmentType(String label) {
		if ("Bounding Box".equals(label)) return StandardAttachmentType.BOUNDING_BOX;
		if ("Path".equals(label)) return StandardAttachmentType.PATH;
		if ("Point".equals(label)) return StandardAttachmentType.POINT;
		if ("Clipping".equals(label)) return StandardAttachmentType.CLIPPING;
		return null;
	}

	static boolean editsCurrentLinkedMesh(String selectedType, Attachment current, String selectedAttachment,
		String placeholder, boolean ownsSelected) {
		return "Linked Mesh".equals(selectedType) && current instanceof MeshAttachment mesh
			&& mesh.getSourceMesh() != null && ownsSelected && selectedAttachment != null && placeholder != null
			&& selectedAttachment.equals(placeholder.trim());
	}

	private void createBlankSkin() {
		SkeletonEditor.SkinStructureEdit edit = editor.addSkinEdit("skin", false);
		if (edit == null) {
			showStatus("无法创建空白皮肤", true);
			return;
		}
		skinStructureChanged("新建皮肤", edit);
	}

	private void duplicateCurrentSkin() {
		Skin source = currentSkin();
		if (source == null) return;
		String blockReason = editor.getSkinDuplicateBlockReason(source);
		if (blockReason != null) {
			showStatus("无法复制皮肤：" + blockReason, true);
			return;
		}
		SkeletonEditor.SkinStructureEdit edit = editor.duplicateSkinEdit(source, source.getName() + ".copy");
		if (edit == null) {
			showStatus("无法复制当前皮肤", true);
			return;
		}
		skinStructureChanged("复制皮肤", edit);
	}

	private boolean renameCurrentSkin() {
		Skin skin = currentSkin();
		String requested = skinNameField.getText() == null ? "" : skinNameField.getText().trim();
		if (skin == null) return false;
		if (requested.isEmpty()) {
			showStatus("皮肤名称不能为空", true);
			return false;
		}
		if (requested.equals(skin.getName())) {
			showStatus("皮肤名称未变化", false);
			return true;
		}
		SkeletonEditor.SkinStructureEdit edit = editor.renameSkinEdit(skin, requested);
		if (edit == null) {
			showStatus("默认皮肤不能重命名", true);
			return false;
		}
		skinStructureChanged("重命名皮肤", edit);
		return true;
	}

	private void skinStructureChanged(String label, SkeletonEditor.SkinStructureEdit edit) {
		if (history != null) {
			recordSkinStructureChange(history, label, edit);
			if (onReversibleDataChanged != null) onReversibleDataChanged.run();
			else refresh();
		} else if (onDataChanged != null) onDataChanged.run();
	}

	static void recordSkinStructureChange(EditHistory history, String label, SkeletonEditor.SkinStructureEdit edit) {
		if (history == null || edit == null) throw new IllegalArgumentException("皮肤编辑历史不能为空");
		history.record(label, edit::undo, edit::redo);
	}

	private void refreshSkinManagementState() {
		Skin skin = currentSkin();
		boolean hasSkin = skin != null;
		boolean isDefault = hasSkin && doc.data != null && skin == doc.data.getDefaultSkin();
		skinTitle.setText(isDefault ? "皮肤（当前为默认）" : "皮肤");
		skinNameField.setDisabled(!hasSkin || isDefault);
		renameSkinBtn.setDisabled(!hasSkin || isDefault || !nameDraftChanged(skin.getName(), skinNameField.getText()));
		duplicateSkinBtn.setDisabled(!hasSkin);
		delSkinBtn.setDisabled(!hasSkin || isDefault);
		skinBonesBtn.setDisabled(!hasSkin || !doc.poseMode || doc.data == null || doc.data.getBones().isEmpty());
		refreshSlotOrderState();
	}

	private void refreshSlotOrderState() {
		SlotData slot = currentSlot();
		// The base draw order is Setup data; changing it while an animation is
		// active would make the preview and the editable order disagree.
		boolean enabled = slot != null && doc.data != null && doc.poseMode;
		int index = enabled ? slot.getIndex() : -1;
		moveSlotUpBtn.setDisabled(!enabled || index <= 0);
		moveSlotDownBtn.setDisabled(!enabled || index < 0 || index + 1 >= doc.data.getSlots().size);
	}

	private void moveCurrentSlot(int direction) {
		if (!doc.poseMode) {
			showStatus("请先进入 Setup 模式再调整槽绘制顺序", true);
			return;
		}
		SlotData slot = currentSlot();
		if (slot == null) return;
		String name = slot.getName();
		try {
			var before = com.openspine.model.SkeletonStructureSnapshot.capture(editor);
			int[] map = editor.moveSlot(slot, direction);
			if (map == null) {
				showStatus("无法调整槽绘制顺序", true);
				return;
			}
			pendingSlotSelection = name;
			if (history != null) {
				var after = com.openspine.model.SkeletonStructureSnapshot.capture(editor);
				history.record("调整槽顺序", before::restore, after::restore);
			} else if (onDataChanged != null) onDataChanged.run();
		} catch (RuntimeException error) {
			showStatus("无法调整槽绘制顺序：" + error.getMessage(), true);
		}
	}

	private void showSkinBonesDialog() {
		Stage stage = getStage();
		Skin skin = currentSkin();
		if (stage == null || skin == null || doc.data == null) return;
		if (!doc.poseMode) {
			showStatus("请先进入 Setup 模式再编辑皮肤骨骼", true);
			return;
		}
		Actor previousFocus = stage.getKeyboardFocus();
		com.badlogic.gdx.scenes.scene2d.ui.Window window = kit.window("皮肤骨骼");
		window.setModal(true);
		kit.padWindow(window, 10f);
		Table list = new Table();
		list.top().left();
		Array<TextButton> toggles = new Array<>();
		Array<BoneData> skinBones = new Array<>();
		for (BoneData bone : doc.data.getBones()) {
			if (!bone.getSkinRequired()) continue;
			skinBones.add(bone);
			TextButton toggle = new TextButton(bone.getName(), editor.isSkinBoneActive(skin, bone)
				? kit.activeToolButton : kit.toolButton);
			toggle.setChecked(editor.isSkinBoneActive(skin, bone));
			toggle.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					toggle.setStyle(toggle.isChecked() ? kit.activeToolButton : kit.toolButton);
				}
			});
			toggles.add(toggle);
			list.add(toggle).growX().height(26f).pad(2).row();
		}
		com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(list, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		window.add(scroll).width(280f).height(360f).row();
		Table actions = new Table();
		TextButton cancel = new TextButton("取消", kit.toolButton);
		TextButton apply = new TextButton("应用", kit.accentButton);
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				UiDialogs.closeModal(stage, window, previousFocus);
			}
		});
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				for (int index = 0; index < toggles.size; index++)
					editor.setSkinBoneActive(skin, skinBones.get(index), toggles.get(index).isChecked());
				if (onDataChanged != null) onDataChanged.run();
				UiDialogs.closeModal(stage, window, previousFocus);
			}
		});
		actions.add(cancel).width(80f).height(26f).pad(2);
		actions.add(apply).width(80f).height(26f).pad(2);
		window.add(actions).right().padTop(8f);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(window);
	}

	private void showDeleteSkinDialog() {
		Skin skin = currentSkin();
		Stage stage = getStage();
		if (skin == null || stage == null) return;
		if (doc.data != null && skin == doc.data.getDefaultSkin()) {
			showStatus("默认皮肤不能删除", true);
			return;
		}
		String blockReason = editor.getSkinDeleteBlockReason(skin);
		if (blockReason != null) {
			showStatus("无法删除皮肤：" + blockReason, true);
			return;
		}
		UiDialogs.confirm(stage, kit, "删除皮肤",
			"删除皮肤 " + skin.getName() + "？" + (history == null ? "此操作无法撤销。" : "可通过撤销恢复。"), "删除", () -> {
				SkeletonEditor.SkinStructureEdit edit = editor.removeSkinEdit(skin);
				if (edit == null) {
					String currentReason = editor.getSkinDeleteBlockReason(skin);
					showStatus("无法删除当前皮肤：" + (currentReason == null ? "皮肤状态已变化" : currentReason), true);
					return;
				}
				skinStructureChanged("删除皮肤", edit);
			});
	}

	private Skin currentSkin() {
		if (skinBox.getSelected() == null || doc.data == null) return null;
		return doc.data.findSkin(skinBox.getSelected());
	}

	public void setOnDataChanged(Runnable r) { this.onDataChanged = r; }
	public void setOnReversibleDataChanged(Runnable r) { this.onReversibleDataChanged = r; }
	public void setOnSkinChanged(Runnable r) { this.onSkinChanged = r; }
	public void setHistory(EditHistory history) { this.history = history; }
	public void setOnAttachmentSelectionChanged(java.util.function.BiConsumer<SlotData, Attachment> listener) {
		onAttachmentSelectionChanged = listener;
	}
	public void setOnStatus(java.util.function.BiConsumer<String, Boolean> listener) { this.onStatus = listener; }

	private void showStatus(String message, boolean error) {
		if (onStatus != null) onStatus.accept(message, error);
	}

	private boolean handleSlotNameKey(int keycode) {
		return switch (UiDialogs.keyAction(keycode)) {
		case CONFIRM -> {
			if (renameCurrentSlot()) clearKeyboardFocus();
			yield true;
		}
		case CANCEL -> {
			SlotData slot = currentSlot();
			slotNameField.setText(slot == null ? "" : slot.getName());
			clearKeyboardFocus();
			showStatus("已取消槽重命名", false);
			yield true;
		}
		case NONE -> false;
		};
	}

	private boolean handleSkinNameKey(int keycode) {
		return switch (UiDialogs.keyAction(keycode)) {
		case CONFIRM -> {
			if (renameCurrentSkin()) clearKeyboardFocus();
			yield true;
		}
		case CANCEL -> {
			Skin skin = currentSkin();
			skinNameField.setText(skin == null ? "" : skin.getName());
			clearKeyboardFocus();
			showStatus("已取消皮肤重命名", false);
			yield true;
		}
		case NONE -> false;
		};
	}

	private boolean handleSlotPropertyKey(int keycode) {
		return switch (UiDialogs.keyAction(keycode)) {
		case CONFIRM -> {
			if (applySlotBtn.isDisabled() || applySlotProperties()) clearKeyboardFocus();
			yield true;
		}
		case CANCEL -> {
			clearKeyboardFocus();
			refreshAttachments();
			showStatus("已取消槽属性编辑", false);
			yield true;
		}
		case NONE -> false;
		};
	}

	private boolean handleRegionPropertyKey(int keycode) {
		return switch (UiDialogs.keyAction(keycode)) {
		case CONFIRM -> {
			if (applyRegionBtn.isDisabled() || applyRegionProperties()) clearKeyboardFocus();
			yield true;
		}
		case CANCEL -> {
			clearKeyboardFocus();
			refreshRegion();
			showStatus("已取消附件属性编辑", false);
			yield true;
		}
		case NONE -> false;
		};
	}

	private void clearKeyboardFocus() {
		if (getStage() != null) getStage().setKeyboardFocus(null);
	}

	/** 外部：选中骨骼变化 / 数据变化后刷新整个面板 */
	public void refresh() {
		String displayedSlot = slotBox.getSelected();
		String keepSlot = pendingSlotSelection != null ? pendingSlotSelection : displayedSlot;
		String keepAttachment = pendingAttachmentSelection != null ? pendingAttachmentSelection
			: keepSlot != null && keepSlot.equals(displayedSlot) ? attachBox.getSelected() : null;
		pendingSlotSelection = null;
		pendingAttachmentSelection = null;
		String keepSkin = doc.getCurrentSkinName();
		String keepRegion = regionBox.getSelected();
		updating = true;
		Bone bone = doc.getSelectedBone();
		slotTitle.setText(I18n.t("关联槽", "Related Slots"));
		Array<String> slots = new Array<>();
		if (bone != null) {
			Array<SlotData> slotData = editor.slotsForBone(bone.getData());
			if (slotData.size > 0) {
				slotTitle.setText(I18n.t("关联槽 (", "Related Slots (") + slotData.size + ")");
				for (SlotData s : slotData) slots.add(s.getName());
			}
		}
		slotBox.setItems(slots);
		attachBox.setItems(new Array<>());
		if (keepSlot != null && slots.contains(keepSlot, false)) slotBox.setSelected(keepSlot);
		else if (slots.notEmpty()) slotBox.setSelectedIndex(0);
		slotBox.setDisabled(slots.isEmpty());
		newSlotBtn.setDisabled(doc.data == null || bone == null);
		Array<String> skins = new Array<>();
		if (doc.data != null) {
			for (Skin s : doc.data.getSkins()) skins.add(s.getName());
		}
		skinBox.setItems(skins);
		if (keepSkin != null && !keepSkin.isEmpty() && skins.contains(keepSkin, false)) skinBox.setSelected(keepSkin);
		else if (skins.notEmpty()) skinBox.setSelectedIndex(0);
		Array<String> regions = editor.atlasRegionNames();
		regionBox.setItems(regions);
		if (keepRegion != null && regions.contains(keepRegion, false)) regionBox.setSelected(keepRegion);
		else if (regions.notEmpty()) regionBox.setSelectedIndex(0);
		skinBox.setDisabled(skins.isEmpty());
		skinCombinationBtn.setDisabled(skins.isEmpty());
		skinCombinationBtn.setText(doc.getPreviewSkinNames().size() > 1
			? "组合 " + doc.getPreviewSkinNames().size() : "组合…");
		skinCombinationBtn.setStyle(doc.getPreviewSkinNames().size() > 1 ? kit.activeToolButton : kit.toolButton);
		Skin selectedSkin = currentSkin();
		skinNameField.setText(selectedSkin == null ? "" : selectedSkin.getName());
		addSkinBtn.setDisabled(doc.data == null);
		refreshSkinManagementState();
		updating = false;
		refreshAttachments(keepAttachment);
		refreshCreationState();
		I18n.localize(this);
	}

	/** Relabels persistent controls and translated choice lists after a language change. */
	public void refreshLanguage() {
		BlendMode blendMode = selectedBlendMode();
		updating = true;
		slotBlendBox.setItems(localizedBlendModeLabels());
		slotBlendBox.setSelected(localizedBlendModeLabel(blendMode == null ? BlendMode.normal : blendMode));
		updating = false;
		refresh();
	}

	private class FloatRow {
		final Label text;
		final TextField field;
		final Slider slider;
		final float min, max;
		float lastValidValue;

		FloatRow(String name, float min, float max) {
			this.min = min;
			this.max = max;
			lastValidValue = min > 0f ? min : 0f;
			text = kit.controlLabel(name);
			field = new TextField(UiNumbers.format(lastValidValue), kit.textFieldStyle);
			field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E');
			slider = new Slider(min, max, 0.1f, false, kit.sliderStyle);
			slider.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					if (!updating) {
						lastValidValue = slider.getValue();
						field.setText(UiNumbers.format(lastValidValue));
					}
				}
			});
			field.addListener(new FocusListener() {
				@Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
					if (!focused && !updating) normalizeIfValid();
				}
			});
			field.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					if (!updating) refreshRegionApplyState();
				}
			});
			field.addListener(new InputListener() {
				@Override public boolean keyDown(InputEvent event, int keycode) {
					return handleRegionPropertyKey(keycode);
				}
			});
		}

		void set(float v) {
			if (Float.isFinite(v)) lastValidValue = v;
			float value = lastValidValue;
			syncSlider(value);
			if (getStage() == null || getStage().getKeyboardFocus() != field) field.setText(UiNumbers.format(value));
		}

		void accept(float value) {
			lastValidValue = value;
			syncSlider(value);
			field.setText(UiNumbers.format(value));
		}

		void normalizeIfValid() {
			try {
				accept(UiNumbers.parseFinite(field.getText()));
			} catch (RuntimeException ignored) {
				// Keep invalid text visible so an atomic apply can report and reject it.
			}
		}

		private void syncSlider(float value) {
			boolean wasUpdating = updating;
			updating = true;
			try {
				slider.setValue(clamp(value));
			} finally {
				updating = wasUpdating;
			}
		}

		void setEnabled(boolean enabled) {
			field.setDisabled(!enabled);
			slider.setDisabled(!enabled);
			text.setColor(enabled ? 0.85f : 0.5f, enabled ? 0.87f : 0.52f,
				enabled ? 0.92f : 0.56f, 1f);
		}

		float clamp(float value) { return Math.max(min, Math.min(max, value)); }
	}

}
