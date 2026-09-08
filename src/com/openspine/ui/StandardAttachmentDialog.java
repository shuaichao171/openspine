package com.openspine.ui;

import java.util.function.BiConsumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.Attachment;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonEditor.StandardAttachmentState;
import com.openspine.model.SkeletonEditor.StandardAttachmentType;

/** Type-aware editor for Bounding Box, Path, Point, and Clipping attachments. */
final class StandardAttachmentDialog {
	private static final String END_DRAWING = "（直到绘制结束）";

	private final Stage stage;
	private final UiKit kit;
	private final SkeletonDocument doc;
	private final SkeletonEditor editor;
	private final Attachment attachment;
	private final EditHistory history;
	private final Runnable onChanged;
	private final BiConsumer<String, Boolean> onStatus;
	private final Actor previousKeyboardFocus;
	private final Window window;
	private final StandardAttachmentType type;
	private final TextField colorField;
	private TextField vertexCountField, xField, yField, rotationField;
	private TextArea bonesArea, verticesArea, lengthsArea;
	private SelectBox<String> endSlotBox;
	private TextButton closedButton, constantSpeedButton, convexButton, inverseButton;
	private final Label summaryLabel, errorLabel;
	private final TextButton applyButton;
	private StandardAttachmentState baseline;
	private boolean closedValue, constantSpeedValue, convexValue, inverseValue;
	private boolean refreshing;
	private boolean closed;

	static void show(Stage stage, UiKit kit, SkeletonDocument doc, SkeletonEditor editor,
		Attachment attachment, EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		if (stage == null || kit == null || doc == null || editor == null || attachment == null) return;
		new StandardAttachmentDialog(stage, kit, doc, editor, attachment, history, onChanged, onStatus).open();
	}

	private StandardAttachmentDialog(Stage stage, UiKit kit, SkeletonDocument doc, SkeletonEditor editor,
		Attachment attachment, EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		this.stage = stage;
		this.kit = kit;
		this.doc = doc;
		this.editor = editor;
		this.attachment = attachment;
		this.history = history;
		this.onChanged = onChanged;
		this.onStatus = onStatus;
		previousKeyboardFocus = stage.getKeyboardFocus();
		baseline = SkeletonEditor.standardAttachmentState(attachment);
		type = baseline.type();

		window = kit.window(typeLabel(type) + " 附件");
		window.setModal(true);
		kit.padWindow(window, 10f);
		colorField = new TextField("", kit.textFieldStyle);
		colorField.setMaxLength(9);
		Table identity = new Table();
		identity.add(kit.controlLabel("名称")).right().padRight(5);
		identity.add(kit.controlLabel(attachment.getName())).left().width(230).padRight(15);
		identity.add(kit.controlLabel("颜色")).right().padRight(5);
		identity.add(colorField).width(110).height(26);
		window.add(identity).left().row();

		if (type == StandardAttachmentType.POINT) buildPointControls();
		else buildVertexControls();
		if (type == StandardAttachmentType.PATH) buildPathControls();
		if (type == StandardAttachmentType.CLIPPING) buildClippingControls();

		summaryLabel = new Label("", kit.labelStyle);
		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(summaryLabel).left().padTop(7).row();
		window.add(errorLabel).width(620).left().padTop(4).row();

		applyButton = new TextButton("应用", kit.accentButton);
		TextButton closeButton = new TextButton("关闭", kit.toolButton);
		Table actions = new Table();
		actions.add(applyButton).width(84).height(28).padRight(6);
		actions.add(closeButton).width(84).height(28);
		window.add(actions).right().padTop(8);

		ChangeListener draftListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshing) refreshDraftState();
			}
		};
		for (Actor actor : draftActors()) actor.addListener(draftListener);
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
					if (stage.getKeyboardFocus() instanceof TextArea) return false;
					if (!applyButton.isDisabled()) submit();
					return true;
				}
				return false;
			}
		});
	}

	private void buildPointControls() {
		xField = numberField();
		yField = numberField();
		rotationField = numberField();
		Table values = new Table();
		values.add(kit.controlLabel("X")).right().padRight(4);
		values.add(xField).width(100).height(26).padRight(10);
		values.add(kit.controlLabel("Y")).right().padRight(4);
		values.add(yField).width(100).height(26).padRight(10);
		values.add(kit.controlLabel("旋转")).right().padRight(4);
		values.add(rotationField).width(100).height(26);
		window.add(values).left().padTop(8).row();
	}

	private void buildVertexControls() {
		vertexCountField = integerField();
		Table count = new Table();
		count.add(kit.controlLabel("逻辑顶点数")).right().padRight(5);
		count.add(vertexCountField).width(90).height(26);
		window.add(count).left().padTop(8).row();
		bonesArea = area();
		verticesArea = area();
		Table geometry = new Table();
		Table left = new Table();
		left.add(sectionLabel("顶点 / 权重数据")).left().row();
		left.add(verticesArea).width(330).height(150).row();
		Table right = new Table();
		right.add(sectionLabel("权重骨骼编码（空为无权重）")).left().row();
		right.add(bonesArea).width(280).height(150).row();
		geometry.add(left).top().padRight(8);
		geometry.add(right).top();
		window.add(geometry).left().padTop(7).row();
	}

	private void buildPathControls() {
		closedButton = new TextButton("", kit.toolButton);
		constantSpeedButton = new TextButton("", kit.toolButton);
		closedButton.addListener(toggleListener(() -> {
			closedValue = !closedValue;
			refreshToggleButtons();
		}));
		constantSpeedButton.addListener(toggleListener(() -> {
			constantSpeedValue = !constantSpeedValue;
			refreshToggleButtons();
		}));
		Table options = new Table();
		options.add(kit.controlLabel("闭合")).right().padRight(4);
		options.add(closedButton).width(90).height(26).padRight(14);
		options.add(kit.controlLabel("恒定速度")).right().padRight(4);
		options.add(constantSpeedButton).width(90).height(26);
		window.add(options).left().padTop(7).row();
		lengthsArea = area();
		window.add(sectionLabel("累计曲线长度（每个曲线一个值）")).left().padTop(7).row();
		window.add(lengthsArea).width(620).height(72).left().row();
	}

	private void buildClippingControls() {
		endSlotBox = kit.selectBox();
		Array<String> slots = new Array<>();
		slots.add(END_DRAWING);
		if (doc.data != null) for (SlotData slot : doc.data.getSlots()) slots.add(slot.getName());
		endSlotBox.setItems(slots);
		convexButton = new TextButton("", kit.toolButton);
		inverseButton = new TextButton("", kit.toolButton);
		convexButton.addListener(toggleListener(() -> {
			convexValue = !convexValue;
			if (!convexValue) inverseValue = false;
			refreshToggleButtons();
		}));
		inverseButton.addListener(toggleListener(() -> {
			inverseValue = !inverseValue;
			if (inverseValue) convexValue = true;
			refreshToggleButtons();
		}));
		Table options = new Table();
		options.add(kit.controlLabel("结束槽")).right().padRight(4);
		options.add(endSlotBox).width(190).height(26).padRight(12);
		options.add(kit.controlLabel("凸多边形")).right().padRight(4);
		options.add(convexButton).width(80).height(26).padRight(12);
		options.add(kit.controlLabel("反向")).right().padRight(4);
		options.add(inverseButton).width(80).height(26);
		window.add(options).left().padTop(7).row();
	}

	private ChangeListener toggleListener(Runnable action) {
		return new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (refreshing) return;
				action.run();
				refreshDraftState();
			}
		};
	}

	private void open() {
		loadState(baseline);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(type == StandardAttachmentType.POINT ? xField : verticesArea);
	}

	private Actor[] draftActors() {
		Array<Actor> actors = new Array<>();
		actors.add(colorField);
		if (type == StandardAttachmentType.POINT) actors.addAll(xField, yField, rotationField);
		else actors.addAll(vertexCountField, verticesArea, bonesArea);
		if (lengthsArea != null) actors.add(lengthsArea);
		if (endSlotBox != null) actors.add(endSlotBox);
		return actors.toArray(Actor[]::new);
	}

	private void loadState(StandardAttachmentState state) {
		refreshing = true;
		try {
			baseline = state;
			colorField.setText(UiColors.format(state.color()));
			closedValue = state.closed();
			constantSpeedValue = state.constantSpeed();
			convexValue = state.convex();
			inverseValue = state.inverse();
			if (type == StandardAttachmentType.POINT) {
				xField.setText(UiNumbers.format(state.x()));
				yField.setText(UiNumbers.format(state.y()));
				rotationField.setText(UiNumbers.format(state.rotation()));
			} else {
				vertexCountField.setText(Integer.toString(state.vertexCount()));
				verticesArea.setText(format(state.vertices(), state.weighted() ? 6 : 2));
				bonesArea.setText(format(state.bones(), 8));
			}
			if (lengthsArea != null) lengthsArea.setText(format(state.lengths(), 4));
			if (endSlotBox != null)
				endSlotBox.setSelected(state.endSlot() == null ? END_DRAWING : state.endSlot().getName());
			errorLabel.setText("");
			refreshToggleButtons();
		} finally {
			refreshing = false;
		}
		refreshDraftState();
	}

	private StandardAttachmentState draftState() {
		Color color = UiColors.parse(colorField.getText());
		if (type == StandardAttachmentType.POINT) {
			return new StandardAttachmentState(type, color, 0, null, null, false, false, null,
				parseFinite(xField.getText(), "X"), parseFinite(yField.getText(), "Y"),
				parseFinite(rotationField.getText(), "旋转"), null, false, false);
		}
		int vertexCount = parseInt(vertexCountField.getText(), "逻辑顶点数");
		int[] bones = MeshGeometryDialog.parseIntArray(bonesArea.getText(), "权重骨骼编码", true);
		float[] vertices = MeshGeometryDialog.parseFloatArray(verticesArea.getText(), "顶点 / 权重数据", false);
		float[] lengths = lengthsArea == null ? null
			: MeshGeometryDialog.parseFloatArray(lengthsArea.getText(), "累计曲线长度", false);
		SlotData endSlot = null;
		if (endSlotBox != null && !END_DRAWING.equals(endSlotBox.getSelected()))
			endSlot = doc.data == null ? null : doc.data.findSlot(endSlotBox.getSelected());
		return new StandardAttachmentState(type, color, vertexCount, bones, vertices, closedValue,
			constantSpeedValue, lengths, 0f, 0f, 0f, endSlot, convexValue, inverseValue);
	}

	private void refreshDraftState() {
		try {
			StandardAttachmentState draft = draftState();
			String reason = editor.validateStandardAttachment(attachment, draft);
			applyButton.setDisabled(reason == null && baseline.sameContent(draft));
			if (reason != null) summaryLabel.setText(typeLabel(type) + " 数据待校验");
			else if (type == StandardAttachmentType.POINT)
				summaryLabel.setText("Point  ·  X " + UiNumbers.format(draft.x()) + "  ·  Y " + UiNumbers.format(draft.y()));
			else summaryLabel.setText(draft.vertexCount() + " 顶点  ·  " + (draft.weighted() ? "已加权" : "无权重"));
		} catch (RuntimeException ignored) {
			applyButton.setDisabled(false);
			summaryLabel.setText(typeLabel(type) + " 数据待校验");
		}
	}

	private void submit() {
		StandardAttachmentState draft;
		try {
			draft = draftState();
		} catch (RuntimeException error) {
			showError(error.getMessage());
			return;
		}
		String reason = editor.validateStandardAttachment(attachment, draft);
		if (reason != null) {
			showError(reason);
			return;
		}
		if (baseline.sameContent(draft)) {
			applyButton.setDisabled(true);
			return;
		}
		StandardAttachmentState before = SkeletonEditor.standardAttachmentState(attachment);
		if (!editor.updateStandardAttachment(attachment, draft)) {
			showError("附件状态已变化，请重新打开编辑器");
			return;
		}
		StandardAttachmentState after = SkeletonEditor.standardAttachmentState(attachment);
		if (history != null) history.record("编辑 " + typeLabel(type) + " 附件",
			() -> restore(before), () -> restore(after));
		else if (onChanged != null) onChanged.run();
		loadState(after);
		if (onStatus != null) onStatus.accept(typeLabel(type) + " 附件已应用", false);
	}

	private void restore(StandardAttachmentState state) {
		if (!editor.updateStandardAttachment(attachment, state))
			throw new IllegalStateException("无法恢复附件：" + attachment.getName());
	}

	private void refreshToggleButtons() {
		setToggle(closedButton, closedValue);
		setToggle(constantSpeedButton, constantSpeedValue);
		setToggle(convexButton, convexValue);
		setToggle(inverseButton, inverseValue);
	}

	private void setToggle(TextButton button, boolean checked) {
		if (button == null) return;
		button.setText(checked ? "已启用" : "已关闭");
		button.setStyle(checked ? kit.activeToolButton : kit.toolButton);
	}

	private TextArea area() {
		TextArea area = new TextArea("", kit.textFieldStyle);
		area.setPrefRows(4);
		return area;
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

	private Label sectionLabel(String text) {
		Label label = new Label(text, kit.labelStyle);
		label.setColor(0.62f, 0.82f, 0.92f, 1f);
		return label;
	}

	private static float parseFinite(String text, String label) {
		try {
			return UiNumbers.parseFinite(text);
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(label + " 必须是有限数值", error);
		}
	}

	private static int parseInt(String text, String label) {
		try {
			return Integer.parseInt(text == null ? "" : text.trim());
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(label + " 必须是整数", error);
		}
	}

	private static String format(float[] values, int group) {
		if (values == null || values.length == 0) return "";
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < values.length; index++) {
			if (index > 0) result.append(index % group == 0 ? '\n' : ", ");
			result.append(UiNumbers.format(values[index]));
		}
		return result.toString();
	}

	private static String format(int[] values, int group) {
		if (values == null || values.length == 0) return "";
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < values.length; index++) {
			if (index > 0) result.append(index % group == 0 ? '\n' : ", ");
			result.append(values[index]);
		}
		return result.toString();
	}

	static String typeLabel(StandardAttachmentType type) {
		return switch (type) {
		case BOUNDING_BOX -> "Bounding Box";
		case PATH -> "Path";
		case POINT -> "Point";
		case CLIPPING -> "Clipping";
		};
	}

	private void showError(String message) {
		errorLabel.setText(message == null ? "附件数据无效" : message);
		if (onStatus != null) onStatus.accept(errorLabel.getText().toString(), true);
	}

	private void close() {
		if (closed) return;
		closed = true;
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}
}
