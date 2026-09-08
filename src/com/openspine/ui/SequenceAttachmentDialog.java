package com.openspine.ui;

import java.util.function.BiConsumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.HasSequence;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonEditor.SequenceAttachmentState;

/** Edits atlas-backed image-sequence setup for Region and Mesh attachments. */
final class SequenceAttachmentDialog {
	private final Stage stage;
	private final SkeletonEditor editor;
	private final EditHistory history;
	private final Runnable onChanged;
	private final BiConsumer<String, Boolean> onStatus;
	private final Actor previousKeyboardFocus;
	private final Window window;
	private final TextField pathField, countField, startField, digitsField, setupField;
	private final Label previewLabel, errorLabel;
	private final TextButton applyButton;
	private Attachment attachment;
	private SequenceAttachmentState baseline;
	private boolean refreshing;
	private boolean closed;

	static void show(Stage stage, UiKit kit, SkeletonDocument doc, SkeletonEditor editor, Attachment attachment,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		if (stage == null || kit == null || doc == null || editor == null
			|| !(attachment instanceof RegionAttachment || attachment instanceof MeshAttachment)) return;
		new SequenceAttachmentDialog(stage, kit, editor, attachment, history, onChanged, onStatus).open();
	}

	private SequenceAttachmentDialog(Stage stage, UiKit kit, SkeletonEditor editor, Attachment attachment,
		EditHistory history, Runnable onChanged, BiConsumer<String, Boolean> onStatus) {
		this.stage = stage;
		this.editor = editor;
		this.attachment = attachment;
		this.history = history;
		this.onChanged = onChanged;
		this.onStatus = onStatus;
		previousKeyboardFocus = stage.getKeyboardFocus();
		baseline = SkeletonEditor.sequenceAttachmentState(attachment);

		window = kit.window("图片序列");
		window.setModal(true);
		kit.padWindow(window, 10f);
		window.add(kit.controlLabel("附件")).right().padRight(6);
		window.add(kit.controlLabel(attachment.getName())).width(260).left().row();

		pathField = field(kit, false);
		pathField.setMessageText("例如 images/walk_");
		countField = field(kit, true);
		startField = field(kit, true);
		digitsField = field(kit, true);
		setupField = field(kit, true);
		addRow(kit, "基础路径", pathField);
		addRow(kit, "帧数", countField);
		addRow(kit, "起始编号", startField);
		addRow(kit, "补零位数", digitsField);
		addRow(kit, "Setup 索引", setupField);

		previewLabel = new Label("", kit.labelStyle);
		previewLabel.setWrap(true);
		window.add(previewLabel).colspan(2).width(360).left().padTop(7).row();
		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).colspan(2).width(360).left().padTop(4).row();

		applyButton = new TextButton("应用", kit.accentButton);
		TextButton closeButton = new TextButton("关闭", kit.toolButton);
		Table actions = new Table();
		actions.add(applyButton).width(84).height(28).padRight(6);
		actions.add(closeButton).width(84).height(28);
		window.add(actions).colspan(2).right().padTop(8);

		ChangeListener draftListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshing) refreshDraftState();
			}
		};
		pathField.addListener(draftListener);
		countField.addListener(draftListener);
		startField.addListener(draftListener);
		digitsField.addListener(draftListener);
		setupField.addListener(draftListener);
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

	private static TextField field(UiKit kit, boolean integer) {
		TextField field = new TextField("", kit.textFieldStyle);
		if (integer) field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-');
		return field;
	}

	private void addRow(UiKit kit, String label, TextField field) {
		window.add(kit.controlLabel(label)).right().padRight(6).padTop(5);
		window.add(field).width(260).height(26).left().padTop(5).row();
	}

	private void open() {
		loadState(baseline);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(pathField);
		pathField.selectAll();
	}

	private void loadState(SequenceAttachmentState state) {
		refreshing = true;
		try {
			baseline = state;
			pathField.setText(state.path() == null ? "" : state.path());
			countField.setText(Integer.toString(state.count()));
			startField.setText(Integer.toString(state.start()));
			digitsField.setText(Integer.toString(state.digits()));
			setupField.setText(Integer.toString(state.setupIndex()));
			errorLabel.setText("");
		} finally {
			refreshing = false;
		}
		refreshDraftState();
	}

	static SequenceAttachmentState parseState(String path, String count, String start, String digits, String setup) {
		String normalizedPath = path == null ? "" : path.trim();
		return new SequenceAttachmentState(normalizedPath, parseInteger(count, "帧数"),
			parseInteger(start, "起始编号"), parseInteger(digits, "补零位数"),
			parseInteger(setup, "Setup 索引"));
	}

	private static int parseInteger(String value, String label) {
		try {
			return Integer.parseInt(value == null ? "" : value.trim());
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(label + " 必须是整数", error);
		}
	}

	static String framePath(SequenceAttachmentState state, int index) {
		if (state == null || state.path() == null || state.count() < 1 || index < 0 || index >= state.count())
			throw new IllegalArgumentException("Sequence 帧索引无效");
		Sequence sequence = new Sequence(state.count(), true);
		sequence.setStart(state.start());
		sequence.setDigits(state.digits());
		return sequence.getPath(state.path(), index);
	}

	private SequenceAttachmentState draftState() {
		return parseState(pathField.getText(), countField.getText(), startField.getText(), digitsField.getText(),
			setupField.getText());
	}

	private void refreshDraftState() {
		try {
			SequenceAttachmentState draft = draftState();
			String reason = editor.validateSequenceAttachment(attachment, draft);
			applyButton.setDisabled(baseline.sameContent(draft));
			if (draft.count() > 0 && draft.count() <= 10000 && draft.digits() >= 0 && draft.digits() <= 64) {
				String first = framePath(draft, 0);
				String last = framePath(draft, draft.count() - 1);
				previewLabel.setText(draft.count() == 1 ? "图片：" + first
					: "首帧：" + first + "\n末帧：" + last);
			} else {
				previewLabel.setText("Sequence 参数待校验");
			}
			errorLabel.setText(reason == null || baseline.sameContent(draft) ? "" : reason);
		} catch (RuntimeException error) {
			applyButton.setDisabled(false);
			previewLabel.setText("Sequence 参数待校验");
			errorLabel.setText("");
		}
	}

	private void submit() {
		SequenceAttachmentState draft;
		try {
			draft = draftState();
		} catch (RuntimeException error) {
			showError(error.getMessage());
			return;
		}
		String reason = editor.validateSequenceAttachment(attachment, draft);
		if (reason != null) {
			showError(reason);
			return;
		}
		if (baseline.sameContent(draft)) {
			applyButton.setDisabled(true);
			return;
		}

		Attachment beforeAttachment = attachment;
		SequenceAttachmentState before = SkeletonEditor.sequenceAttachmentState(beforeAttachment);
		long revision = editor.doc.revision;
		Attachment updated = editor.updateSequenceAttachment(beforeAttachment, draft);
		if (updated == null) {
			showError("Sequence 状态已变化，请重新打开编辑器");
			return;
		}
		if (editor.doc.revision == revision) {
			applyButton.setDisabled(true);
			return;
		}

		attachment = updated;
		SequenceAttachmentState after = SkeletonEditor.sequenceAttachmentState(updated);
		if (updated == beforeAttachment && history != null) {
			history.record("编辑图片序列", () -> restore(updated, before), () -> restore(updated, after));
		} else if (onChanged != null) {
			onChanged.run();
		}
		loadState(after);
		if (onStatus != null) onStatus.accept("图片序列已应用", false);
	}

	private void restore(Attachment target, SequenceAttachmentState state) {
		if (editor.updateSequenceAttachment(target, state) != target)
			throw new IllegalStateException("无法恢复图片序列：" + target.getName());
	}

	private void showError(String message) {
		errorLabel.setText(message == null ? "Sequence 数据无效" : message);
	}

	private void close() {
		if (closed) return;
		closed = true;
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}
}
