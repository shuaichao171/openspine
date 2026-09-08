package com.openspine.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Animation.CurveTimeline;
import com.esotericsoftware.spine.Animation.DeformTimeline;
import com.openspine.io.CurvePresetStore;
import com.openspine.model.AnimationEditor;
import com.openspine.model.SkeletonDocument;

/** Full-track curve workspace for continuous Spine timelines. */
final class GraphEditorDialog {
	private static final String PREFERENCES_NAME = "OpenSpine.workspace";
	private static final String PREF_CURVE_PRESETS = "graph.curvePresets";
	private static final String PREF_DEFAULT_CURVE = "graph.defaultCurve";
	interface Host {
		AnimationEditor.TimelineSnapshot snapshot(Animation.Timeline timeline);
		AnimationEditor.TimelineSnapshot[] snapshotCurveMode(Animation.Timeline timeline);
		void record(String label, AnimationEditor.TimelineSnapshot before);
		void recordCurveMode(String label, AnimationEditor.TimelineSnapshot[] before);
		void changed();
		void seek(float time);
		void reopen(Animation.Timeline timeline, int frame);
		void status(String message, boolean error);
	}

	private final Stage stage;
	private final UiKit kit;
	private final SkeletonDocument doc;
	private final com.esotericsoftware.spine.Skeleton sourceSkeleton;
	private final AnimationEditor editor;
	private final Animation animation;
	private final Host host;
	private final String[] channelLabels;
	private final String stateKey;
	private final String trackTitle;
	private final Table root;
	private final Window window;
	private final Actor previousKeyboardFocus;
	private final boolean embedded;
	private final Runnable closeCallback;
	private final SelectBox<String> transformBox;
	private final SelectBox<String> presetBox;
	private final TextField timeField;
	private final TextField valueField;
	private final Label selectionLabel;
	private final Label errorLabel;
	private final TextButton applyButton;
	private final GraphCanvas graph;
	private final Button[] channelRows;
	private final Image[] channelEyeIcons;
	private Label keyCountLabel;
	private Label defaultPresetLabel;
	private AnimationEditor.CurveSnapshot storedCurve;
	private boolean closed;

	static void show(Stage stage, UiKit kit, SkeletonDocument doc, AnimationEditor editor,
		Animation animation, Animation.Timeline timeline, int initialFrame, String title,
		String[] channelLabels, Host host) {
		if (stage == null || kit == null || doc == null || editor == null || animation == null
			|| timeline == null || channelLabels == null || channelLabels.length == 0 || host == null) return;
		if (!(timeline instanceof CurveTimeline)) {
			host.status("该轨道没有可在 Graph 中编辑的连续数值", true);
			return;
		}
		new GraphEditorDialog(stage, kit, doc, editor, animation, timeline, initialFrame,
			title, channelLabels, host, false, null).open();
	}

	static GraphEditorDialog embed(Table parent, Stage stage, UiKit kit, SkeletonDocument doc,
		AnimationEditor editor, Animation animation, Animation.Timeline timeline, int initialFrame,
		String title, String[] channelLabels, Host host, Runnable closeCallback) {
		if (parent == null || stage == null || kit == null || doc == null || editor == null
			|| animation == null || timeline == null || channelLabels == null
			|| channelLabels.length == 0 || host == null || !(timeline instanceof CurveTimeline)) return null;
		GraphEditorDialog dialog = new GraphEditorDialog(stage, kit, doc, editor, animation, timeline,
			initialFrame, title, channelLabels, host, true, closeCallback);
		dialog.openEmbedded(parent);
		return dialog;
	}

	private GraphEditorDialog(Stage stage, UiKit kit, SkeletonDocument doc, AnimationEditor editor,
		Animation animation, Animation.Timeline timeline, int initialFrame, String title,
		String[] channelLabels, Host host, boolean embedded, Runnable closeCallback) {
		this.stage = stage;
		this.kit = kit;
		this.doc = doc;
		sourceSkeleton = doc.skeleton;
		this.editor = editor;
		this.animation = animation;
		this.host = host;
		this.embedded = embedded;
		this.closeCallback = closeCallback;
		this.channelLabels = channelLabels.clone();
		trackTitle = normalizeTrackTitle(title);
		stateKey = viewStateKey(animation, timeline);
		previousKeyboardFocus = stage.getKeyboardFocus();

		if (embedded) {
			window = null;
			root = new Table();
			root.setBackground(kit.panelBg);
			root.pad(8f);
		} else {
			window = kit.window("曲线编辑器 · " + (title == null ? "曲线" : title));
			window.setModal(false);
			window.setMovable(true);
			window.setResizable(true);
			kit.padWindow(window, 10f);
			root = window;
		}

		transformBox = kit.selectBox();
		if (timeline instanceof DeformTimeline) transformBox.setItems("移动", "重定时");
		else transformBox.setItems("移动", "重定时", "重设值");
		presetBox = kit.selectBox();
		timeField = numberField();
		valueField = numberField();
		selectionLabel = new Label("", kit.labelStyle);
		selectionLabel.setEllipsis(true);
		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setEllipsis(true);
		graph = new GraphCanvas(timeline, Math.max(0, Math.min(initialFrame, timeline.getFrameCount() - 1)),
			doc.graphViewState(stateKey));
		channelRows = new Button[this.channelLabels.length];
		channelEyeIcons = new Image[this.channelLabels.length];

		Table top = new Table();
		TextButton frameAll = button("全览", () -> { graph.frameAll(); persistViewState(); });
		TextButton frameSelection = button("选中", () -> { graph.frameSelection(); persistViewState(); });
		transformBox.setSelectedIndex(graph.transformMode);
		TextButton editTime = toggle("X", graph.editTime, checked -> {
			graph.editTime = checked; persistViewState();
		});
		TextButton editValue = toggle("Y", graph.editValue, checked -> {
			graph.editValue = checked; persistViewState();
		});
		editValue.setDisabled(timeline instanceof DeformTimeline);
		TextButton snap = toggle("吸附", graph.snap, checked -> { graph.snap = checked; persistViewState(); });
		TextButton autoFrame = toggle("自动取景", graph.autoFrame, checked -> {
			graph.autoFrame = checked;
			if (checked) graph.frameSelection();
			persistViewState();
		});
		TextButton separateHandles = toggle("分离切线", graph.separateHandles, checked -> {
			graph.separateHandles = checked; persistViewState();
		});
		TextButton linear = button("线性", () -> setCurveMode(0));
		TextButton stepped = button("步进", () -> setCurveMode(1));
		TextButton auto = button("自动", () -> setCurveMode(2));
		TextButton flat = button("平坦", () -> setCurveMode(3));
		TextButton bounce = button("弹性", () -> setCurveMode(4));
		top.add(frameAll).padRight(4);
		top.add(frameSelection).padRight(10);
		top.add(transformBox).width(92).padRight(5);
		top.add(editTime).width(34).padRight(3);
		top.add(editValue).width(34).padRight(3);
		top.add(snap).padRight(3);
		top.add(autoFrame).padRight(3);
		top.add(separateHandles).padRight(8);
		top.add(linear).padRight(4);
		top.add(stepped).padRight(4);
		top.add(auto).padRight(4);
		top.add(flat).padRight(4);
		top.add(bounce).padRight(4);
		top.add().growX();
		ScrollPane topScroll = new ScrollPane(top, kit.scrollStyle);
		topScroll.setScrollingDisabled(false, true);
		topScroll.setFadeScrollBars(false);
		topScroll.setOverscroll(false, false);
		root.add(topScroll).growX().minWidth(0f).height(38f).left().row();

		Table commands = new Table();
		commands.add(button("新增", this::insertKey)).padRight(4);
		commands.add(button("删除", this::deleteKeys)).padRight(8);
		commands.add(button("存储曲线", this::storeCurve)).padRight(4);
		commands.add(button("交换曲线", this::swapCurve)).padRight(4);
		commands.add().growX();
		root.add(commands).growX().left().padTop(5).row();
		Table presets = new Table();
		presets.add(kit.controlLabel("曲线预设")).padRight(6);
		presets.add(presetBox).width(170).height(28).padRight(5);
		presets.add(button("应用", this::applyCurvePreset)).padRight(4);
		presets.add(button("新增", this::addCurvePreset)).padRight(4);
		presets.add(button("重命名", this::renameCurvePreset)).padRight(4);
		presets.add(button("删除", this::deleteCurvePreset)).padRight(4);
		presets.add(button("设为默认", this::setDefaultCurvePreset)).padRight(7);
		defaultPresetLabel = new Label("", kit.dialogBodyStyle);
		presets.add(defaultPresetLabel).growX().left();
		root.add(presets).growX().left().padTop(5).row();
		refreshCurvePresets(null);

		SplitPane graphSplit = new SplitPane(buildHierarchyPanel(), graph, false, kit.splitStyle);
		graphSplit.setSplitAmount(0.22f);
		graphSplit.setMinSplitAmount(0.16f);
		graphSplit.setMaxSplitAmount(0.38f);
		root.add(graphSplit).grow().minSize(620f, 330f).prefSize(760f, 500f).padTop(6).row();

		Table inspector = new Table();
		inspector.add(selectionLabel).width(220).left().padRight(8);
		inspector.add(kit.controlLabel("时间")).padRight(4);
		inspector.add(timeField).width(92).padRight(8);
		inspector.add(kit.controlLabel("数值")).padRight(4);
		inspector.add(valueField).width(104).padRight(6);
		applyButton = button("应用", this::applyFields);
		inspector.add(applyButton).width(72).padRight(8);
		inspector.add(errorLabel).growX().left();
		TextButton close = button(embedded ? "收起" : "关闭", this::close);
		inspector.add(close).width(72);
		root.add(inspector).growX().padTop(7).row();

		transformBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				graph.transformMode = Math.max(0, transformBox.getSelectedIndex());
				persistViewState();
			}
		});
		root.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				if (!syncLiveTimeline()) return false;
				if (keycode == Input.Keys.ESCAPE) { close(); return true; }
				if (keycode == Input.Keys.F) { graph.frameSelection(); return true; }
				if (keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) { deleteKeys(); return true; }
				if (controlPressed() && keycode == Input.Keys.A) { graph.selectAll(); return true; }
				if (controlPressed() && keycode == Input.Keys.C) { storeCurve(); return true; }
				if (controlPressed() && keycode == Input.Keys.V) { pasteCurve(); return true; }
				return false;
			}
		});
		refreshInspector();
	}

	private void open() {
		window.setSize(Math.min(1180f, stage.getWidth() - 24f), Math.min(740f, stage.getHeight() - 24f));
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(graph);
	}

	private void openEmbedded(Table parent) {
		parent.clearChildren();
		parent.add(root).grow().minSize(640f, 360f);
		parent.invalidateHierarchy();
		stage.setKeyboardFocus(graph);
	}

	private TextField numberField() {
		TextField field = new TextField("", kit.textFieldStyle);
		field.setMaxLength(20);
		field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+'
			|| c == '.' || c == 'e' || c == 'E');
		return field;
	}

	private TextButton button(String label, Runnable action) {
		TextButton button = new TextButton(label, kit.toolButton);
		button.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
		});
		return button;
	}

	private TextButton toggle(String label, boolean checked,
		java.util.function.Consumer<Boolean> action) {
		TextButton button = new TextButton(label, kit.toolButton);
		button.setProgrammaticChangeEvents(false);
		button.setChecked(checked);
		button.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { action.accept(button.isChecked()); }
		});
		return button;
	}

	private Actor buildHierarchyPanel() {
		Table content = new Table();
		content.top().left();
		content.pad(6f, 5f, 8f, 5f);

		Table animationRow = new Table();
		animationRow.add(new Image(kit.minusIcon)).size(12f).padRight(6f);
		Label animationLabel = new Label(animation.getName() == null || animation.getName().isBlank()
			? "动画" : animation.getName(), kit.labelStyle);
		animationLabel.setEllipsis(true);
		animationRow.add(animationLabel).growX().left();
		content.add(animationRow).growX().height(30f).row();

		Table trackRow = new Table();
		trackRow.padLeft(17f);
		Image trackSwatch = new Image(kit.white);
		trackSwatch.setColor(0.96f, 0.38f, 0.16f, 1f);
		trackRow.add(trackSwatch).size(8f).padRight(7f);
		Label trackLabel = new Label(trackTitle, kit.labelStyle);
		trackLabel.setEllipsis(true);
		trackRow.add(trackLabel).growX().left().padRight(5f);
		keyCountLabel = new Label("", kit.dialogBodyStyle);
		trackRow.add(keyCountLabel).right();
		content.add(trackRow).growX().height(30f).row();

		Label channelHeading = new Label("数值通道", kit.dialogBodyStyle);
		content.add(channelHeading).growX().left().padLeft(34f).padTop(4f).padBottom(3f).row();
		Button.ButtonStyle eyeStyle = new Button.ButtonStyle();
		eyeStyle.over = kit.hoverDark;
		eyeStyle.down = kit.downDark;
		for (int channel = 0; channel < channelLabels.length; channel++) {
			final int target = channel;
			Button channelRow = new Button(kit.toolButton);
			channelRow.setProgrammaticChangeEvents(false);
			channelRow.pad(0f, 7f, 0f, 7f);
			Image swatch = new Image(kit.white);
			swatch.setColor(graph.channelColor(channel));
			channelRow.add(swatch).size(8f).padRight(7f);
			Label channelLabel = new Label(channelLabels[channel], kit.labelStyle);
			channelLabel.setEllipsis(true);
			channelRow.add(channelLabel).growX().left();
			channelRow.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					if (!graph.isChannelVisible(target)) graph.setChannelVisible(target, true);
					graph.select(graph.selectedFrame, target, true);
				}
			});
			channelRows[channel] = channelRow;

			Image eyeIcon = new Image(graph.isChannelVisible(channel)
				? kit.visibleEyeIcon : kit.hiddenEyeIcon);
			Button eye = new Button(eyeStyle);
			eye.add(eyeIcon).size(14f);
			eye.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					graph.setChannelVisible(target, !graph.isChannelVisible(target));
				}
			});
			channelEyeIcons[channel] = eyeIcon;

			Table line = new Table();
			line.padLeft(27f);
			line.add(channelRow).growX().height(27f).padRight(2f);
			line.add(eye).size(27f);
			content.add(line).growX().height(29f).row();
		}
		content.add().growY();

		ScrollPane scroll = new ScrollPane(content, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		scroll.setScrollingDisabled(true, false);
		Table panel = new Table();
		panel.setBackground(kit.panelBg);
		panel.add(scroll).grow().minWidth(190f).prefWidth(232f);
		refreshChannelRows();
		return panel;
	}

	private void refreshChannelRows() {
		if (keyCountLabel != null)
			keyCountLabel.setText(graph.timeline.getFrameCount() + " K");
		for (int channel = 0; channel < channelRows.length; channel++) {
			Button row = channelRows[channel];
			if (row != null) row.setChecked(channel == graph.selectedChannel);
			Image eye = channelEyeIcons[channel];
			if (eye != null) eye.setDrawable(graph.isChannelVisible(channel)
				? kit.visibleEyeIcon : kit.hiddenEyeIcon);
		}
	}

	private void refreshInspector() {
		refreshChannelRows();
		int count = graph.selectionCount();
		if (count == 0 || graph.selectedFrame < 0 || graph.selectedFrame >= graph.timeline.getFrameCount()) {
			selectionLabel.setText("未选择关键点");
			timeField.setText("");
			valueField.setText("");
			timeField.setDisabled(true);
			valueField.setDisabled(true);
			applyButton.setDisabled(true);
			errorLabel.setText("");
			return;
		}
		timeField.setDisabled(false);
		valueField.setDisabled(graph.timeline instanceof DeformTimeline);
		applyButton.setDisabled(false);
		selectionLabel.setText(count > 1 ? "已选 " + count + " 个关键点 · 输入偏移量"
			: "关键帧 " + (graph.selectedFrame + 1) + " / " + graph.timeline.getFrameCount()
				+ " · " + channelLabels[graph.selectedChannel]);
		timeField.setText(count > 1 ? "0" : UiNumbers.format(editor.frameTime(graph.timeline, graph.selectedFrame)));
		valueField.setText(count > 1 ? "0" : UiNumbers.format(editor.curveFrameValue(graph.timeline,
			graph.selectedFrame, graph.selectedChannel)));
		errorLabel.setText("");
	}

	private void applyFields() {
		if (!syncLiveTimeline()) return;
		try {
			int count = graph.selectionCount();
			if (count == 0) throw new IllegalStateException("请先选择关键点");
			float currentTime = editor.frameTime(graph.timeline, graph.selectedFrame);
			float currentValue = editor.curveFrameValue(graph.timeline,
				graph.selectedFrame, graph.selectedChannel);
			float time = editableFieldValue(graph.editTime, timeField.getText(), count > 1 ? 0f : currentTime);
			float value = editableFieldValue(graph.editValue, valueField.getText(), count > 1 ? 0f : currentValue);
			if (count > 1) graph.transformSelection(time, value, "Graph 批量编辑关键点");
			else graph.commitKey(time, value, "Graph 编辑关键点");
		} catch (RuntimeException error) {
			errorLabel.setText(error.getMessage());
		}
	}

	static float editableFieldValue(boolean editable, String text, float currentValue) {
		return editable ? UiNumbers.parseFinite(text) : currentValue;
	}

	static float editableHandleCoordinate(boolean editable, float candidate, float currentValue) {
		return editable ? candidate : currentValue;
	}

	private void insertKey() {
		graph.insertKey(Math.max(0f, doc.getCurrentTime()), defaultCurvePreset());
	}

	private void deleteKeys() {
		graph.deleteSelection();
	}

	private void storeCurve() {
		if (!syncLiveTimeline()) return;
		if (graph.selectionCount() == 0 || graph.selectedFrame < 0) {
			errorLabel.setText("请先选择关键点");
			return;
		}
		if (!hasOutgoingCurveSelection(graph.selectionCount(), graph.selectedFrame,
			graph.timeline.getFrameCount())) {
			errorLabel.setText("末帧没有可存储的曲线");
			return;
		}
		storedCurve = editor.captureCurve(graph.timeline, graph.selectedFrame);
		errorLabel.setText("已存储当前关键帧的全部通道曲线");
	}

	private void pasteCurve() {
		if (!syncLiveTimeline()) return;
		if (graph.selectionCount() == 0 || graph.selectedFrame < 0) {
			errorLabel.setText("请先选择关键点");
			return;
		}
		if (storedCurve == null) { errorLabel.setText("尚未存储曲线"); return; }
		AnimationEditor.TimelineSnapshot before = host.snapshot(graph.timeline);
		long revision = doc.revision;
		try {
			graph.timeline = editor.applyCurveSnapshot(animation, graph.timeline, graph.selectedFrame, storedCurve);
			if (doc.revision != revision) { host.record("Graph 粘贴曲线", before); host.changed(); }
			graph.invalidateView();
			refreshInspector();
		} catch (RuntimeException error) { errorLabel.setText(error.getMessage()); }
	}

	private void swapCurve() {
		if (!syncLiveTimeline()) return;
		if (storedCurve == null) { storeCurve(); return; }
		if (graph.selectionCount() == 0 || graph.selectedFrame < 0) {
			errorLabel.setText("请先选择关键点");
			return;
		}
		if (!hasOutgoingCurveSelection(graph.selectionCount(), graph.selectedFrame,
			graph.timeline.getFrameCount())) {
			errorLabel.setText("末帧没有可交换的曲线");
			return;
		}
		AnimationEditor.CurveSnapshot current = editor.captureCurve(graph.timeline, graph.selectedFrame);
		pasteCurve();
		if (errorLabel.getText() == null || errorLabel.getText().toString().isEmpty()) storedCurve = current;
	}

	private Preferences curvePreferences() {
		return Gdx.app == null ? null : Gdx.app.getPreferences(PREFERENCES_NAME);
	}

	private LinkedHashMap<String, CurvePresetStore.Curve> curvePresets() {
		Preferences preferences = curvePreferences();
		if (preferences == null || !preferences.contains(PREF_CURVE_PRESETS)) return CurvePresetStore.defaults();
		return CurvePresetStore.decode(preferences.getString(PREF_CURVE_PRESETS, ""));
	}

	private void writeCurvePresets(Map<String, CurvePresetStore.Curve> presets) {
		Preferences preferences = curvePreferences();
		if (preferences == null) return;
		preferences.putString(PREF_CURVE_PRESETS, CurvePresetStore.encode(presets));
		preferences.flush();
	}

	private String defaultCurvePresetName(Map<String, CurvePresetStore.Curve> presets) {
		if (presets.isEmpty()) return "";
		Preferences preferences = curvePreferences();
		String selected = preferences == null ? "" : preferences.getString(PREF_DEFAULT_CURVE, "");
		return presets.containsKey(selected) ? selected : presets.keySet().iterator().next();
	}

	private CurvePresetStore.Curve defaultCurvePreset() {
		LinkedHashMap<String, CurvePresetStore.Curve> presets = curvePresets();
		return presets.get(defaultCurvePresetName(presets));
	}

	private void refreshCurvePresets(String selected) {
		LinkedHashMap<String, CurvePresetStore.Curve> presets = curvePresets();
		Array<String> names = new Array<>();
		for (String name : presets.keySet()) names.add(name);
		if (names.isEmpty()) names.add("（无预设）");
		presetBox.setItems(names);
		if (selected != null && names.contains(selected, false)) presetBox.setSelected(selected);
		String defaultName = defaultCurvePresetName(presets);
		defaultPresetLabel.setText(defaultName.isEmpty() ? "" : "默认：" + defaultName);
	}

	private CurvePresetStore.Curve selectedCurvePreset() {
		return curvePresets().get(presetBox.getSelected());
	}

	private CurvePresetStore.Curve curveFromSelection() {
		if (!syncLiveTimeline()) return null;
		for (int[] point : graph.selectedPoints()) {
			if (point[0] >= graph.timeline.getFrameCount() - 1) continue;
			AnimationEditor.BezierHandles handles = editor.bezierHandles(graph.timeline, point[0], point[1]);
			float start = editor.curveFrameValue(graph.timeline, point[0], point[1]);
			float end = editor.curveFrameValue(graph.timeline, point[0] + 1, point[1]);
			if (handles != null && start == end && (handles.value1() != start || handles.value2() != start))
				throw new IllegalStateException("同端点回弹无法保存为比例预设，请使用“存储曲线”");
			AnimationEditor.BezierCurve curve = editor.bezierCurve(graph.timeline, point[0], point[1]);
			if (curve != null) return new CurvePresetStore.Curve(curve.cx1(), curve.cy1(), curve.cx2(), curve.cy2());
		}
		return null;
	}

	private void addCurvePreset() {
		CurvePresetStore.Curve curve;
		try { curve = curveFromSelection(); }
		catch (RuntimeException error) { errorLabel.setText(error.getMessage()); return; }
		if (curve == null) { errorLabel.setText("请选择一段贝塞尔曲线"); return; }
		UiDialogs.promptName(stage, kit, "新增曲线预设", "新预设", 64, name -> {
			String normalized = com.openspine.io.ExportPresetStore.normalizeName(name);
			if (normalized == null) return false;
			LinkedHashMap<String, CurvePresetStore.Curve> presets = curvePresets();
			if (presets.containsKey(normalized)) { errorLabel.setText("曲线预设名称已存在"); return false; }
			presets.put(normalized, curve);
			writeCurvePresets(presets);
			refreshCurvePresets(normalized);
			errorLabel.setText("");
			return true;
		});
	}

	private void renameCurvePreset() {
		String oldName = presetBox.getSelected();
		if (!curvePresets().containsKey(oldName)) { errorLabel.setText("没有可重命名的曲线预设"); return; }
		UiDialogs.promptName(stage, kit, "重命名曲线预设", oldName, 64, name -> {
			String normalized = com.openspine.io.ExportPresetStore.normalizeName(name);
			if (normalized == null) return false;
			LinkedHashMap<String, CurvePresetStore.Curve> presets = curvePresets();
			if (!normalized.equals(oldName) && presets.containsKey(normalized)) {
				errorLabel.setText("曲线预设名称已存在"); return false;
			}
			CurvePresetStore.Curve curve = presets.remove(oldName);
			presets.put(normalized, curve);
			writeCurvePresets(presets);
			Preferences preferences = curvePreferences();
			if (preferences != null && oldName.equals(preferences.getString(PREF_DEFAULT_CURVE, ""))) {
				preferences.putString(PREF_DEFAULT_CURVE, normalized);
				preferences.flush();
			}
			refreshCurvePresets(normalized);
			errorLabel.setText("");
			return true;
		});
	}

	private void deleteCurvePreset() {
		String name = presetBox.getSelected();
		LinkedHashMap<String, CurvePresetStore.Curve> presets = curvePresets();
		if (presets.remove(name) == null) { errorLabel.setText("没有可删除的曲线预设"); return; }
		writeCurvePresets(presets);
		Preferences preferences = curvePreferences();
		if (preferences != null && name.equals(preferences.getString(PREF_DEFAULT_CURVE, ""))) {
			preferences.remove(PREF_DEFAULT_CURVE);
			preferences.flush();
		}
		refreshCurvePresets(null);
		errorLabel.setText("");
	}

	private void setDefaultCurvePreset() {
		String name = presetBox.getSelected();
		if (!curvePresets().containsKey(name)) { errorLabel.setText("没有可设为默认的曲线预设"); return; }
		Preferences preferences = curvePreferences();
		if (preferences != null) {
			preferences.putString(PREF_DEFAULT_CURVE, name);
			preferences.flush();
		}
		refreshCurvePresets(name);
		errorLabel.setText("");
	}

	private void applyCurvePreset() {
		if (!syncLiveTimeline()) return;
		CurvePresetStore.Curve preset = selectedCurvePreset();
		if (preset == null) { errorLabel.setText("没有可应用的曲线预设"); return; }
		ArrayList<int[]> points = graph.selectedPoints();
		if (points.isEmpty()) { errorLabel.setText("请先选择关键点"); return; }
		AnimationEditor.TimelineSnapshot before = host.snapshot(graph.timeline);
		long revision = doc.revision;
		try {
			for (int[] point : points) {
				if (point[0] >= graph.timeline.getFrameCount() - 1) continue;
				graph.timeline = editor.setBezierCurveChannel(animation, graph.timeline, point[0], point[1],
					preset.cx1(), preset.cy1(), preset.cx2(), preset.cy2());
			}
			if (doc.revision != revision) { host.record("Graph 应用曲线预设", before); host.changed(); }
			graph.invalidateView();
			refreshInspector();
			errorLabel.setText("");
		} catch (RuntimeException error) { errorLabel.setText(error.getMessage()); }
	}

	private static boolean controlPressed() {
		return Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
			|| Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT));
	}

	static boolean hasOutgoingCurveSelection(int selectionCount, int selectedFrame, int frameCount) {
		return selectionCount > 0 && selectedFrame >= 0 && selectedFrame < frameCount - 1;
	}

	static void includeBezierControl(float[] bounds, float time1, float value1, float time2, float value2,
		float[] handles, int controlOffset) {
		if (bounds == null || bounds.length < 4 || handles == null || handles.length < 4
			|| controlOffset != 0 && controlOffset != 2) return;
		float time = time1 + (time2 - time1) * handles[controlOffset];
		float value = handles[controlOffset + 1];
		if (!Float.isFinite(time) || !Float.isFinite(value)) return;
		bounds[0] = Math.min(bounds[0], time);
		bounds[1] = Math.max(bounds[1], time);
		bounds[2] = Math.min(bounds[2], value);
		bounds[3] = Math.max(bounds[3], value);
	}

	static void includeBezierControls(float[] bounds, float time1, float value1, float time2, float value2,
		float[] handles) {
		includeBezierControl(bounds, time1, value1, time2, value2, handles, 0);
		includeBezierControl(bounds, time1, value1, time2, value2, handles, 2);
	}

	private void setCurveMode(int mode) {
		if (!syncLiveTimeline()) return;
		int frame = graph.selectedFrame;
		if (frame < 0 || !graph.hasEditableCurveSelection()) {
			errorLabel.setText("末帧没有向后插值曲线");
			return;
		}
		AnimationEditor.TimelineSnapshot before = host.snapshot(graph.timeline);
		AnimationEditor.TimelineSnapshot[] curveModeBefore = mode == 0 || mode == 1
			? host.snapshotCurveMode(graph.timeline) : null;
		long revision = doc.revision;
		try {
			if (mode == 0 || mode == 1) {
				ArrayList<int[]> points = graph.editableSelectedPoints();
				int[] frames = new int[points.size()], channels = new int[points.size()];
				for (int index = 0; index < points.size(); index++) {
					frames[index] = points.get(index)[0];
					channels[index] = points.get(index)[1];
				}
				Animation.Timeline previousTimeline = graph.timeline;
				graph.timeline = editor.setCurveModeChannels(animation, graph.timeline,
					frames, channels, mode == 1);
				if (graph.timeline != previousTimeline
					&& AnimationEditor.curveChannelCount(graph.timeline) != channelLabels.length) {
					if (doc.revision != revision) {
						host.recordCurveMode("Graph 曲线模式", curveModeBefore);
						host.changed();
					}
					Animation.Timeline separatedTimeline = graph.timeline;
					int separatedFrame = Math.max(0, Math.min(frame,
						separatedTimeline.getFrameCount() - 1));
					close(false);
					host.reopen(separatedTimeline, separatedFrame);
					return;
				}
			} else {
				for (int[] point : graph.selectedPoints()) {
					frame = point[0];
					if (frame >= graph.timeline.getFrameCount() - 1) continue;
					float[] handles = mode == 2 ? autoHandles(frame, point[1])
						: mode == 3 ? new float[] {0.333333f, 0f, 0.666667f, 1f}
						: new float[] {0.22f, -0.28f, 0.68f, 1.34f};
					graph.timeline = editor.setBezierCurveChannel(animation, graph.timeline, frame,
						point[1], handles[0], handles[1], handles[2], handles[3]);
				}
			}
			if (doc.revision != revision) {
				if (mode == 0 || mode == 1) host.recordCurveMode("Graph 曲线模式", curveModeBefore);
				else host.record("Graph 曲线模式", before);
				host.changed();
			}
			graph.invalidateView();
			refreshInspector();
		} catch (RuntimeException error) {
			errorLabel.setText(error.getMessage());
		}
	}

	private float[] autoHandles(int frame, int channel) {
		float t1 = editor.frameTime(graph.timeline, frame);
		float t2 = editor.frameTime(graph.timeline, frame + 1);
		float v1 = editor.curveFrameValue(graph.timeline, frame, channel);
		float v2 = editor.curveFrameValue(graph.timeline, frame + 1, channel);
		float delta = v2 - v1;
		if (Math.abs(delta) < 0.000001f) return new float[] {1f / 3f, 0f, 2f / 3f, 1f};
		float startSlope = frame == 0 ? delta / (t2 - t1)
			: (v2 - editor.curveFrameValue(graph.timeline, frame - 1, channel))
				/ (t2 - editor.frameTime(graph.timeline, frame - 1));
		float endSlope = frame + 1 == graph.timeline.getFrameCount() - 1 ? delta / (t2 - t1)
			: (editor.curveFrameValue(graph.timeline, frame + 2, channel) - v1)
				/ (editor.frameTime(graph.timeline, frame + 2) - t1);
		float cy1 = startSlope * (t2 - t1) / (3f * delta);
		float cy2 = 1f - endSlope * (t2 - t1) / (3f * delta);
		return new float[] {1f / 3f, cy1, 2f / 3f, cy2};
	}

	private void close() { close(true); }

	private void close(boolean saveViewState) {
		if (closed) return;
		closed = true;
		if (saveViewState) persistViewState();
		if (!embedded) {
			UiDialogs.closeModal(stage, window, previousKeyboardFocus);
			return;
		}
		root.remove();
		if (previousKeyboardFocus != null && previousKeyboardFocus.getStage() == stage)
			stage.setKeyboardFocus(previousKeyboardFocus);
		if (closeCallback != null) closeCallback.run();
	}

	void closeEmbedded(boolean saveViewState) { close(saveViewState); }

	private void persistViewState() {
		doc.setGraphViewState(stateKey, graph.viewState());
	}

	/** History restores replace timeline objects. Keep this nonmodal workspace attached to its live track. */
	private boolean syncLiveTimeline() {
		if (closed) return false;
		if (doc.skeleton != sourceSkeleton || doc.data == null || !doc.data.getAnimations().contains(animation, true)) {
			close(false);
			return false;
		}
		Animation.Timeline live = findLiveTimeline(animation, graph.timeline);
		if (live == null || live.getFrameCount() == 0 || AnimationEditor.curveChannelCount(live) != channelLabels.length) {
			close(false);
			return false;
		}
		if (live == graph.timeline) return true;
		SkeletonDocument.GraphViewState view = graph.viewState();
		int previousFrame = graph.selectedFrame;
		graph.dragMode = 0;
		graph.previewTimes = null;
		graph.previewValues = null;
		graph.previewSegment = -1;
		graph.linkedPreviewSegment = -1;
		graph.timeline = live;
		graph.selected = new boolean[live.getFrameCount()][channelLabels.length];
		graph.restoreSelection(view, previousFrame);
		if (graph.selectionCount() == 0 && view.selectedTimes().length > 0)
			graph.resetSelection(Math.max(0, Math.min(previousFrame, live.getFrameCount() - 1)), graph.selectedChannel);
		refreshInspector();
		refreshChannelRows();
		return true;
	}

	static Animation.Timeline findLiveTimeline(Animation animation, Animation.Timeline reference) {
		if (animation == null || reference == null) return null;
		if (animation.getTimelines().contains(reference, true)) return reference;
		Animation.Timeline match = null;
		for (Animation.Timeline candidate : animation.getTimelines()) {
			if (candidate.getClass() != reference.getClass()
				|| !Arrays.equals(candidate.getPropertyIds(), reference.getPropertyIds())) continue;
			if (match != null) return null;
			match = candidate;
		}
		return match;
	}

	static String viewStateKey(Animation animation, Animation.Timeline timeline) {
		if (animation == null || timeline == null) throw new IllegalArgumentException("Graph state target is required");
		return SkeletonDocument.graphViewStateKey(animation.getName(), timeline);
	}

	static String normalizeTrackTitle(String title) {
		if (title == null || title.isBlank()) return "曲线轨道";
		return title.replaceFirst("^\\d+\\s*个关键帧\\s*·\\s*", "").trim();
	}

	static float outgoingSlope(float duration, float startValue, float controlX, float controlValue) {
		float denominator = duration * controlX;
		if (!Float.isFinite(denominator) || Math.abs(denominator) < 0.000001f) return 0f;
		return (controlValue - startValue) / denominator;
	}

	static float incomingSlope(float duration, float endValue, float controlX, float controlValue) {
		float denominator = duration * (1f - controlX);
		if (!Float.isFinite(denominator) || Math.abs(denominator) < 0.000001f) return 0f;
		return (endValue - controlValue) / denominator;
	}

	static float outgoingControlValueForSlope(float slope, float duration, float startValue, float controlX) {
		if (!Float.isFinite(slope) || !Float.isFinite(duration)) return startValue;
		return startValue + slope * duration * controlX;
	}

	static float incomingControlValueForSlope(float slope, float duration, float endValue, float controlX) {
		if (!Float.isFinite(slope) || !Float.isFinite(duration)) return endValue;
		return endValue - slope * duration * (1f - controlX);
	}

	static double frameAtTime(double time, float fps) {
		return time * UiNumbers.effectiveFps(fps);
	}

	static double niceTickStep(double span, int targetTicks) {
		if (!Double.isFinite(span) || span <= 0d || targetTicks <= 0) return 1d;
		double rawStep = span / targetTicks;
		double magnitude = Math.pow(10d, Math.floor(Math.log10(rawStep)));
		if (!Double.isFinite(magnitude) || magnitude <= 0d) return 1d;
		double normalized = rawStep / magnitude;
		double multiplier = normalized <= 1d ? 1d : normalized <= 2d ? 2d : normalized <= 5d ? 5d : 10d;
		double step = multiplier * magnitude;
		return Double.isFinite(step) && step > 0d ? step : 1d;
	}

	static double firstTick(double minimum, double step) {
		if (!Double.isFinite(minimum) || !Double.isFinite(step) || step <= 0d) return 0d;
		return Math.ceil(minimum / step - 0.000000001d) * step;
	}

	static String formatFrameTick(double frame) {
		return formatTickNumber(frame);
	}

	static String formatValueTick(double value) {
		double magnitude = Math.abs(value);
		if (magnitude >= 1_000_000d) return formatTickNumber(value / 1_000_000d) + "m";
		if (magnitude >= 1_000d) return formatTickNumber(value / 1_000d) + "k";
		return formatTickNumber(value);
	}

	private static String formatTickNumber(double value) {
		if (!Double.isFinite(value)) return "";
		if (Math.abs(value) < 0.0000000005d) return "0";
		if (Math.abs(value) < 0.0001d)
			return String.format(Locale.ROOT, "%.2e", value).replace("e+", "e");
		return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP)
			.stripTrailingZeros().toPlainString();
	}

	static void sanitizeEditedTimes(float[] original, float[] times, boolean[] selected, boolean editTime,
		boolean snap, float fps) {
		if (original == null || times == null || selected == null || original.length != times.length
			|| times.length != selected.length) throw new IllegalArgumentException("Graph 时间选择无效");
		if (!editTime) { System.arraycopy(original, 0, times, 0, times.length); return; }
		boolean[] changed = new boolean[times.length];
		for (int frame = 0; frame < times.length; frame++) {
			if (!selected[frame]) { times[frame] = original[frame]; continue; }
			if (Float.compare(original[frame], times[frame]) == 0) continue;
			changed[frame] = true;
			if (snap) times[frame] = UiNumbers.snapTime(times[frame], fps);
			times[frame] = Math.max(0f, times[frame]);
		}
		constrainSelectedTimes(times, changed);
	}

	static void constrainSelectedTimes(float[] times, boolean[] selected) {
		if (times == null || selected == null || times.length != selected.length)
			throw new IllegalArgumentException("Graph 时间选择无效");
		for (int start = 0; start < times.length; ) {
			while (start < times.length && !selected[start]) start++;
			if (start >= times.length) break;
			int end = start;
			while (end + 1 < times.length && selected[end + 1]) end++;
			float lower = start == 0 ? 0f : Math.nextUp(times[start - 1]);
			float upper = end == times.length - 1 ? Float.POSITIVE_INFINITY
				: Math.nextDown(times[end + 1]);
			float shift = times[start] < lower ? lower - times[start]
				: times[end] > upper ? upper - times[end] : 0f;
			for (int frame = start; frame <= end; frame++) times[frame] += shift;
			times[start] = Math.max(times[start], lower);
			for (int frame = start + 1; frame <= end; frame++)
				times[frame] = Math.max(times[frame], Math.nextUp(times[frame - 1]));
			times[end] = Math.min(times[end], upper);
			for (int frame = end - 1; frame >= start; frame--)
				times[frame] = Math.min(times[frame], Math.nextDown(times[frame + 1]));
			start = end + 1;
		}
	}

	private final class GraphCanvas extends Actor {
		private static final float LEFT = 14f, RIGHT = 66f, BOTTOM = 12f, TOP = 34f;
		private static final float HIT_RADIUS = 11f;
		private final BitmapFont font = kit.font;
		private final GlyphLayout axisLayout = new GlyphLayout();
		private final Color oldColor = new Color();
		private final Color oldFontColor = new Color();
		private final boolean[] visible;
		private final Color[] colors = {
			new Color(0.20f, 0.78f, 0.70f, 1f), new Color(0.96f, 0.52f, 0.28f, 1f),
			new Color(0.36f, 0.64f, 0.98f, 1f), new Color(0.92f, 0.75f, 0.26f, 1f),
			new Color(0.82f, 0.42f, 0.68f, 1f), new Color(0.48f, 0.82f, 0.34f, 1f),
			new Color(0.72f, 0.62f, 0.94f, 1f)
		};
		private Animation.Timeline timeline;
		private int selectedFrame;
		private int selectedChannel;
		private boolean[][] selected;
		private float timeMin, timeMax, valueMin, valueMax;
		private int dragMode;
		private float dragStartX, dragStartY, dragTimeMin, dragTimeMax, dragValueMin, dragValueMax;
		private float[] dragTimes, previewTimes;
		private float[][] dragValues, previewValues;
		private float boxEndX, boxEndY;
		private boolean boxAdditive;
		private float previewX1, previewY1, previewX2, previewY2;
		private int previewSegment = -1, linkedPreviewSegment = -1;
		private float linkedPreviewX1, linkedPreviewY1, linkedPreviewX2, linkedPreviewY2;
		private boolean editTime = true, editValue = true, snap = true, autoFrame, separateHandles;
		private int transformMode;

		GraphCanvas(Animation.Timeline timeline, int initialFrame, SkeletonDocument.GraphViewState savedView) {
			this.timeline = timeline;
			selectedFrame = initialFrame;
			visible = new boolean[channelLabels.length];
			java.util.Arrays.fill(visible, true);
			selected = new boolean[timeline.getFrameCount()][channelLabels.length];
			setTouchable(Touchable.enabled);
			frameAll();
			restoreView(savedView);
			if (timeline instanceof DeformTimeline) {
				editValue = false;
				transformMode = Math.min(transformMode, 1);
			}
			restoreSelection(savedView, initialFrame);
			addListener(new InputListener() {
				@Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
					if (!syncLiveTimeline()) return false;
					stage.setKeyboardFocus(GraphCanvas.this);
					dragStartX = x;
					dragStartY = y;
					dragTimeMin = timeMin;
					dragTimeMax = timeMax;
					dragValueMin = valueMin;
					dragValueMax = valueMax;
					if (button == Input.Buttons.LEFT) {
						int handle = nearestHandle(x, y);
						if (handle != 0) { beginHandleDrag(handle); return true; }
						int[] key = nearestKey(x, y);
						if (key != null) {
							selectForPointer(key[0], key[1]);
							beginSelectionDrag();
							dragMode = 1;
							return true;
						}
						boxEndX = x;
						boxEndY = y;
						boxAdditive = controlPressed();
						dragMode = 5;
						return true;
					}
					dragMode = 2;
					return true;
				}

				@Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
					if (!syncLiveTimeline()) return;
					if (dragMode == 1) {
						updateSelectionDrag(x, y);
					} else if (dragMode == 2) {
						float dt = (x - dragStartX) / plotWidth() * (dragTimeMax - dragTimeMin);
						float dv = (y - dragStartY) / plotHeight() * (dragValueMax - dragValueMin);
						timeMin = dragTimeMin - dt;
						timeMax = dragTimeMax - dt;
						valueMin = dragValueMin - dv;
						valueMax = dragValueMax - dv;
						persistViewState();
					} else if (dragMode == 3 || dragMode == 4) {
						updateHandleDrag(x, y);
					} else if (dragMode == 5) {
						boxEndX = x;
						boxEndY = y;
					}
				}

				@Override public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
					if (!syncLiveTimeline()) return;
					int completed = dragMode;
					dragMode = 0;
					if (completed == 1) commitSelectionDrag();
					else if (completed == 3 || completed == 4) commitHandles();
					else if (completed == 5) finishBoxSelection();
				}

				@Override public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
					float factor = amountY > 0f ? 1.14f : 1f / 1.14f;
					boolean horizontal = Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
						|| Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT));
					if (horizontal) zoomTime(toTime(x), factor); else zoomValue(toValue(y), factor);
					persistViewState();
					return true;
				}
			});
		}

		void setChannelVisible(int channel, boolean shown) {
			visible[channel] = shown;
			if (!shown && channel == selectedChannel) {
				for (int candidate = 0; candidate < visible.length; candidate++) {
					if (!visible[candidate]) continue;
					if (selectionCount() == 0) {
						selectedChannel = candidate;
						refreshInspector();
						persistViewState();
					} else {
						select(selectedFrame, candidate, true);
					}
					return;
				}
			}
			refreshChannelRows();
			persistViewState();
		}

		boolean isChannelVisible(int channel) { return visible[channel]; }

		Color channelColor(int channel) { return colors[channel % colors.length]; }

		void select(int frame, int channel, boolean seek) {
			selectedFrame = Math.max(0, Math.min(frame, timeline.getFrameCount() - 1));
			selectedChannel = Math.max(0, Math.min(channel, channelLabels.length - 1));
			clearSelection();
			selected[selectedFrame][selectedChannel] = true;
			refreshInspector();
			if (seek) host.seek(editor.frameTime(timeline, selectedFrame));
			persistViewState();
		}

		private void selectForPointer(int frame, int channel) {
			boolean control = controlPressed();
			boolean shift = Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
				|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
			if (shift) {
				int firstFrame = Math.min(selectedFrame, frame), lastFrame = Math.max(selectedFrame, frame);
				int firstChannel = Math.min(selectedChannel, channel), lastChannel = Math.max(selectedChannel, channel);
				if (!control) clearSelection();
				for (int targetFrame = firstFrame; targetFrame <= lastFrame; targetFrame++)
					for (int targetChannel = firstChannel; targetChannel <= lastChannel; targetChannel++)
						if (visible[targetChannel]) selected[targetFrame][targetChannel] = true;
			} else if (control) {
				selected[frame][channel] = !selected[frame][channel];
				if (selectionCount() == 0) selected[frame][channel] = true;
			} else if (!selected[frame][channel]) {
				clearSelection();
				selected[frame][channel] = true;
			}
			selectedFrame = frame;
			selectedChannel = channel;
			refreshInspector();
			host.seek(editor.frameTime(timeline, frame));
		}

		private void clearSelection() {
			for (boolean[] frame : selected) java.util.Arrays.fill(frame, false);
		}

		int selectionCount() {
			int count = 0;
			for (boolean[] frame : selected)
				for (boolean point : frame) if (point) count++;
			return count;
		}

		boolean hasEditableCurveSelection() {
			for (int[] point : selectedPoints()) if (point[0] < timeline.getFrameCount() - 1) return true;
			return false;
		}

		ArrayList<int[]> selectedPoints() {
			ArrayList<int[]> points = new ArrayList<>();
			for (int frame = 0; frame < selected.length; frame++)
				for (int channel = 0; channel < selected[frame].length; channel++)
					if (selected[frame][channel]) points.add(new int[] {frame, channel});
			return points;
		}

		ArrayList<int[]> editableSelectedPoints() {
			ArrayList<int[]> points = selectedPoints();
			points.removeIf(point -> point[0] >= timeline.getFrameCount() - 1);
			return points;
		}

		void selectAll() {
			for (int frame = 0; frame < selected.length; frame++)
				for (int channel = 0; channel < selected[frame].length; channel++)
					selected[frame][channel] = visible[channel];
			if (selectionCount() == 0) selectedFrame = -1;
			else if (selectedFrame < 0) selectLastPointAsPrimary();
			refreshInspector();
		}

		private boolean frameSelected(int frame) {
			for (boolean point : selected[frame]) if (point) return true;
			return false;
		}

		private void beginSelectionDrag() {
			int frames = timeline.getFrameCount(), channels = channelLabels.length;
			dragTimes = new float[frames];
			previewTimes = new float[frames];
			dragValues = new float[frames][channels];
			previewValues = new float[frames][channels];
			for (int frame = 0; frame < frames; frame++) {
				dragTimes[frame] = previewTimes[frame] = editor.frameTime(timeline, frame);
				for (int channel = 0; channel < channels; channel++)
					dragValues[frame][channel] = previewValues[frame][channel]
					= editor.curveFrameValue(timeline, frame, channel);
			}
		}

		private void updateSelectionDrag(float x, float y) {
			for (int frame = 0; frame < previewTimes.length; frame++) {
				previewTimes[frame] = dragTimes[frame];
				System.arraycopy(dragValues[frame], 0, previewValues[frame], 0, dragValues[frame].length);
			}
			float timeDelta = (x - dragStartX) / plotWidth() * (dragTimeMax - dragTimeMin);
			float valueDelta = (y - dragStartY) / plotHeight() * (dragValueMax - dragValueMin);
			if (transformMode == 0) {
				for (int frame = 0; frame < previewTimes.length; frame++) {
					if (editTime && frameSelected(frame)) previewTimes[frame] = dragTimes[frame] + timeDelta;
					if (editValue)
						for (int channel = 0; channel < previewValues[frame].length; channel++)
							if (selected[frame][channel]) previewValues[frame][channel] += valueDelta;
				}
			} else if (transformMode == 1 && editTime) {
				float anchor = firstSelectedTime();
				float factor = Math.max(0.01f, 1f + (x - dragStartX) / 180f);
				for (int frame = 0; frame < previewTimes.length; frame++)
					if (frameSelected(frame)) previewTimes[frame] = anchor + (dragTimes[frame] - anchor) * factor;
			} else if (transformMode == 2 && editValue) {
				float anchor = firstSelectedValue();
				float factor = Math.max(-10f, Math.min(10f, 1f + (y - dragStartY) / 150f));
				for (int frame = 0; frame < previewValues.length; frame++)
					for (int channel = 0; channel < previewValues[frame].length; channel++)
						if (selected[frame][channel])
							previewValues[frame][channel] = anchor + (dragValues[frame][channel] - anchor) * factor;
			}
			if (editTime) sanitizePreviewTimes();
		}

		private float firstSelectedTime() {
			for (int frame = 0; frame < selected.length; frame++) if (frameSelected(frame)) return dragTimes[frame];
			return 0f;
		}

		private float firstSelectedValue() {
			for (int frame = 0; frame < selected.length; frame++)
				for (int channel = 0; channel < selected[frame].length; channel++)
					if (selected[frame][channel]) return dragValues[frame][channel];
			return 0f;
		}

		private void sanitizePreviewTimes() {
			boolean[] selectedFrames = new boolean[previewTimes.length];
			for (int frame = 0; frame < selectedFrames.length; frame++) selectedFrames[frame] = frameSelected(frame);
			sanitizeEditedTimes(dragTimes, previewTimes, selectedFrames, editTime, snap && !shiftPressed(),
				doc.data == null ? 30f : doc.data.getFps());
		}

		private void commitSelectionDrag() {
			commitBatch(previewTimes, previewValues, transformMode == 1 ? "Graph 重定时"
				: transformMode == 2 ? "Graph 重设值" : "Graph 批量拖动关键点");
		}

		void transformSelection(float timeDelta, float valueDelta, String label) {
			if (!syncLiveTimeline()) return;
			if (selectionCount() == 0) return;
			beginSelectionDrag();
			for (int frame = 0; frame < previewTimes.length; frame++) {
				if (editTime && frameSelected(frame)) previewTimes[frame] += timeDelta;
				if (editValue)
					for (int channel = 0; channel < previewValues[frame].length; channel++)
						if (selected[frame][channel]) previewValues[frame][channel] += valueDelta;
			}
			sanitizePreviewTimes();
			commitBatch(previewTimes, previewValues, label);
		}

		private void commitBatch(float[] times, float[][] values, String label) {
			if (!syncLiveTimeline() || times != previewTimes || values != previewValues) return;
			AnimationEditor.TimelineSnapshot before = host.snapshot(timeline);
			long revision = doc.revision;
			try {
				timeline = editor.editCurveKeys(animation, timeline, times, values);
				if (doc.revision != revision) { host.record(label, before); host.changed(); }
				host.seek(editor.frameTime(timeline, selectedFrame));
				refreshViewAfterEdit();
				refreshInspector();
				errorLabel.setText("");
			} catch (RuntimeException error) { errorLabel.setText(error.getMessage()); }
			previewTimes = null;
			previewValues = null;
		}

		private void finishBoxSelection() {
			if (!boxAdditive) clearSelection();
			for (int frame = 0; frame < timeline.getFrameCount(); frame++)
				for (int channel = 0; channel < channelLabels.length; channel++)
					if (visible[channel] && pointInside(localX(editor.frameTime(timeline, frame)),
						localY(editor.curveFrameValue(timeline, frame, channel)), dragStartX, dragStartY,
						boxEndX, boxEndY)) selected[frame][channel] = true;
			if (selectionCount() == 0) selectedFrame = -1;
			else selectLastPointAsPrimary();
			refreshInspector();
			persistViewState();
		}

		private void selectLastPointAsPrimary() {
			for (int frame = selected.length - 1; frame >= 0; frame--)
				for (int channel = selected[frame].length - 1; channel >= 0; channel--)
					if (selected[frame][channel]) {
						selectedFrame = frame;
						selectedChannel = channel;
						return;
					}
		}

		private boolean pointInside(float x, float y, float x1, float y1, float x2, float y2) {
			return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
				&& y >= Math.min(y1, y2) && y <= Math.max(y1, y2);
		}

		SkeletonDocument.GraphViewState viewState() {
			long mask = 0L;
			for (int channel = 0; channel < visible.length && channel < Long.SIZE; channel++)
				if (visible[channel]) mask |= 1L << channel;
			ArrayList<int[]> points = selectedPoints();
			float[] times = new float[points.size()];
			int[] channels = new int[points.size()];
			for (int index = 0; index < points.size(); index++) {
				int[] point = points.get(index);
				times[index] = editor.frameTime(timeline, point[0]);
				channels[index] = point[1];
			}
			return new SkeletonDocument.GraphViewState(timeMin, timeMax, valueMin, valueMax,
				selectedChannel, mask, true, times, channels, editTime, editValue, snap, autoFrame,
				separateHandles, transformMode);
		}

		private void restoreView(SkeletonDocument.GraphViewState state) {
			if (state == null || !Float.isFinite(state.timeMin()) || !Float.isFinite(state.timeMax())
				|| !Float.isFinite(state.valueMin()) || !Float.isFinite(state.valueMax())
				|| state.timeMax() <= state.timeMin() || state.valueMax() <= state.valueMin()) return;
			timeMin = state.timeMin();
			timeMax = state.timeMax();
			valueMin = state.valueMin();
			valueMax = state.valueMax();
			selectedChannel = Math.max(0, Math.min(state.selectedChannel(), visible.length - 1));
			for (int channel = 0; channel < visible.length && channel < Long.SIZE; channel++)
				visible[channel] = (state.visibleChannels() & 1L << channel) != 0L;
			editTime = state.editTime();
			editValue = state.editValue();
			snap = state.snap();
			autoFrame = state.autoFrame();
			separateHandles = state.separateHandles();
			transformMode = state.transformMode();
		}

		private void restoreSelection(SkeletonDocument.GraphViewState state, int initialFrame) {
			clearSelection();
			if (state == null || !state.selectionStored()) {
				selectedFrame = Math.max(0, Math.min(initialFrame, timeline.getFrameCount() - 1));
				selected[selectedFrame][selectedChannel] = true;
				return;
			}
			float[] times = state.selectedTimes();
			int[] channels = state.selectedChannels();
			for (int index = 0; index < times.length; index++) {
				int frame = exactFrame(times[index]);
				int channel = channels[index];
				if (frame >= 0 && channel >= 0 && channel < channelLabels.length)
					selected[frame][channel] = true;
			}
			if (selectionCount() == 0) selectedFrame = -1;
			else selectLastPointAsPrimary();
		}

		private int exactFrame(float time) {
			for (int frame = 0; frame < timeline.getFrameCount(); frame++)
				if (Float.compare(editor.frameTime(timeline, frame), time) == 0) return frame;
			return -1;
		}

		void commitKey(float time, float value, String label) {
			if (!syncLiveTimeline()) return;
			if (selectionCount() == 0 || selectedFrame < 0) return;
			AnimationEditor.TimelineSnapshot before = host.snapshot(timeline);
			long revision = doc.revision;
			try {
				timeline = editor.editCurveKey(animation, timeline, selectedFrame, selectedChannel, time, value);
				if (doc.revision != revision) {
					host.record(label, before);
					host.changed();
				}
				host.seek(editor.frameTime(timeline, selectedFrame));
				refreshViewAfterEdit();
				refreshInspector();
				errorLabel.setText("");
			} catch (RuntimeException error) {
				errorLabel.setText(error.getMessage());
			}
		}

		void insertKey(float time, CurvePresetStore.Curve defaultCurve) {
			if (!syncLiveTimeline()) return;
			AnimationEditor.TimelineSnapshot before = host.snapshot(timeline);
			long revision = doc.revision;
			try {
				float targetTime = snapTime(time);
				int existing = editor.findFrame(timeline, targetTime);
				if (existing >= 0) {
					select(existing, selectedChannel, true);
					errorLabel.setText("当前时间已有关键帧");
					return;
				}
				float[] values = new float[channelLabels.length];
				for (int channel = 0; channel < values.length; channel++) values[channel] = sampleValue(targetTime, channel);
				timeline = editor.insertCurveKey(animation, timeline, targetTime, values);
				int frame = editor.findFrame(timeline, targetTime);
				if (defaultCurve != null && frame >= 0) {
					int firstSegment = Math.max(0, frame - 1);
					int lastSegment = Math.min(frame, timeline.getFrameCount() - 2);
					for (int segment = firstSegment; segment <= lastSegment; segment++)
						for (int channel = 0; channel < channelLabels.length; channel++)
							timeline = editor.setBezierCurveChannel(animation, timeline, segment, channel,
								defaultCurve.cx1(), defaultCurve.cy1(), defaultCurve.cx2(), defaultCurve.cy2());
				}
				resetSelection(Math.max(0, frame), selectedChannel);
				if (doc.revision != revision) { host.record("Graph 新增关键帧", before); host.changed(); }
				host.seek(editor.frameTime(timeline, selectedFrame));
				refreshViewAfterEdit();
				refreshInspector();
			} catch (RuntimeException error) { errorLabel.setText(error.getMessage()); }
		}

		void deleteSelection() {
			if (!syncLiveTimeline()) return;
			if (selectionCount() == 0) return;
			boolean[] remove = new boolean[timeline.getFrameCount()];
			for (int frame = 0; frame < remove.length; frame++) remove[frame] = frameSelected(frame);
			AnimationEditor.TimelineSnapshot before = host.snapshot(timeline);
			long revision = doc.revision;
			try {
				int target = selectedFrame;
				timeline = editor.removeCurveKeys(animation, timeline, remove);
				if (timeline == null) {
					if (doc.revision != revision) {
						host.record("Graph 删除轨道", before);
						host.changed();
					}
					close(false);
					return;
				}
				resetSelection(Math.min(target, timeline.getFrameCount() - 1), selectedChannel);
				if (doc.revision != revision) { host.record("Graph 删除关键帧", before); host.changed(); }
				host.seek(editor.frameTime(timeline, selectedFrame));
				refreshViewAfterEdit();
				refreshInspector();
			} catch (RuntimeException error) { errorLabel.setText(error.getMessage()); }
		}

		private void resetSelection(int frame, int channel) {
			selected = new boolean[timeline.getFrameCount()][channelLabels.length];
			selectedFrame = Math.max(0, Math.min(frame, timeline.getFrameCount() - 1));
			selectedChannel = Math.max(0, Math.min(channel, channelLabels.length - 1));
			selected[selectedFrame][selectedChannel] = true;
		}

		private float sampleValue(float time, int channel) {
			if (timeline instanceof DeformTimeline) return 0f;
			int count = timeline.getFrameCount(), entries = timeline.getFrameEntries();
			if (time <= editor.frameTime(timeline, 0)) return editor.curveFrameValue(timeline, 0, channel);
			if (time >= editor.frameTime(timeline, count - 1))
				return editor.curveFrameValue(timeline, count - 1, channel);
			CurveTimeline curve = (CurveTimeline)timeline;
			for (int frame = 0; frame < count - 1; frame++) {
				float time1 = editor.frameTime(timeline, frame), time2 = editor.frameTime(timeline, frame + 1);
				if (time > time2) continue;
				float value1 = editor.curveFrameValue(timeline, frame, channel);
				float value2 = editor.curveFrameValue(timeline, frame + 1, channel);
				int type = curve.getCurveType(frame);
				if (type == CurveTimeline.STEPPED) return value1;
				if (type < CurveTimeline.BEZIER)
					return value1 + (value2 - value1) * (time - time1) / (time2 - time1);
				return curve.getBezierValue(time, frame * entries, channel + 1,
					type - CurveTimeline.BEZIER + channel * CurveTimeline.BEZIER_SIZE);
			}
			return editor.curveFrameValue(timeline, count - 1, channel);
		}

		void frameAll() {
			if (timeline == null || timeline.getFrameCount() == 0) return;
			float[] bounds = {editor.frameTime(timeline, 0),
				editor.frameTime(timeline, timeline.getFrameCount() - 1),
				Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
			for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
				for (int channel = 0; channel < channelLabels.length; channel++) {
					if (!visible[channel]) continue;
					float value = editor.curveFrameValue(timeline, frame, channel);
					bounds[2] = Math.min(bounds[2], value);
					bounds[3] = Math.max(bounds[3], value);
				}
			}
			for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
				for (int channel = 0; channel < channelLabels.length; channel++) {
					if (!visible[channel]) continue;
					includeSegmentControls(bounds, frame, channel);
				}
			}
			timeMin = bounds[0];
			timeMax = bounds[1];
			valueMin = bounds[2];
			valueMax = bounds[3];
			if (!Float.isFinite(valueMin)) { valueMin = -1f; valueMax = 1f; }
			float timeSpan = Math.max(1f / Math.max(1f, doc.data == null ? 30f : doc.data.getFps()), timeMax - timeMin);
			float valueSpan = Math.max(0.01f, valueMax - valueMin);
			timeMin = Math.max(0f, timeMin - timeSpan * 0.06f);
			timeMax += timeSpan * 0.06f;
			valueMin -= valueSpan * 0.12f;
			valueMax += valueSpan * 0.12f;
			if (valueMax - valueMin < 0.02f) { valueMin -= 0.5f; valueMax += 0.5f; }
		}

		void frameSelection() {
			if (selectedFrame < 0) return;
			float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
				Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
			for (int frame = 0; frame < selected.length; frame++) {
				for (int channel = 0; channel < selected[frame].length; channel++) {
					if (!selected[frame][channel]) continue;
					float time = editor.frameTime(timeline, frame);
					float value = editor.curveFrameValue(timeline, frame, channel);
					bounds[0] = Math.min(bounds[0], time); bounds[1] = Math.max(bounds[1], time);
					bounds[2] = Math.min(bounds[2], value); bounds[3] = Math.max(bounds[3], value);
					includeSegmentControl(bounds, frame - 1, channel, 2);
					includeSegmentControl(bounds, frame, channel, 0);
				}
			}
			if (!Float.isFinite(bounds[0]) || !Float.isFinite(bounds[2])) return;
			float firstTime = bounds[0], lastTime = bounds[1];
			float firstValue = bounds[2], lastValue = bounds[3];
			float timeSpan = Math.max(1f / Math.max(1f, doc.data == null ? 30f : doc.data.getFps()),
				lastTime - firstTime);
			float valueSpan = Math.max(0.5f, lastValue - firstValue);
			timeMin = Math.max(0f, firstTime - timeSpan * 0.2f);
			timeMax = lastTime + timeSpan * 0.2f;
			valueMin = firstValue - valueSpan * 0.25f;
			valueMax = lastValue + valueSpan * 0.25f;
		}

		void invalidateView() {
			if (selectedFrame >= timeline.getFrameCount()) selectedFrame = timeline.getFrameCount() - 1;
			if (selected.length != timeline.getFrameCount()) resetSelection(selectedFrame, selectedChannel);
			refreshViewAfterEdit();
		}

		private void refreshViewAfterEdit() {
			if (autoFrame) frameSelection();
			persistViewState();
		}

		private void includeSegmentControls(float[] bounds, int segment, int channel) {
			float[] handles = handlesForSegment(segment, channel);
			if (handles == null) return;
			includeBezierControls(bounds, timeAt(segment), valueAt(segment, channel),
				timeAt(segment + 1), valueAt(segment + 1, channel), handles);
		}

		private void includeSegmentControl(float[] bounds, int segment, int channel, int controlOffset) {
			float[] handles = handlesForSegment(segment, channel);
			if (handles == null) return;
			includeBezierControl(bounds, timeAt(segment), valueAt(segment, channel),
				timeAt(segment + 1), valueAt(segment + 1, channel), handles, controlOffset);
		}

		@Override public void act(float delta) {
			super.act(delta);
			syncLiveTimeline();
		}

		@Override public void draw(Batch batch, float parentAlpha) {
			oldColor.set(batch.getColor());
			batch.setColor(0.045f, 0.05f, 0.06f, parentAlpha);
			batch.draw(kit.white.getRegion(), getX(), getY(), getWidth(), getHeight());
			drawGrid(batch, parentAlpha);
			drawPlayheadLine(batch, parentAlpha);
			for (int channel = 0; channel < channelLabels.length; channel++)
				if (visible[channel]) drawChannel(batch, channel, parentAlpha);
			drawSelectionHandles(batch, parentAlpha);
			drawRulerMarkers(batch, parentAlpha);
			if (dragMode == 5) drawSelectionBox(batch, parentAlpha);
			batch.setColor(oldColor);
		}

		private void drawSelectionBox(Batch batch, float alpha) {
			float left = getX() + Math.min(dragStartX, boxEndX);
			float right = getX() + Math.max(dragStartX, boxEndX);
			float bottom = getY() + Math.min(dragStartY, boxEndY);
			float top = getY() + Math.max(dragStartY, boxEndY);
			batch.setColor(0.34f, 0.68f, 0.96f, alpha * 0.9f);
			drawLine(batch, left, bottom, right, bottom, 1f);
			drawLine(batch, right, bottom, right, top, 1f);
			drawLine(batch, right, top, left, top, 1f);
			drawLine(batch, left, top, left, bottom, 1f);
		}

		private void drawPlayheadLine(Batch batch, float alpha) {
			float time = doc.getCurrentTime();
			if (time < timeMin || time > timeMax) return;
			float x = toX(time);
			batch.setColor(0.05f, 0.84f, 0.93f, alpha * 0.9f);
			drawLine(batch, x, graphBottom(), x, graphBottom() + plotHeight(), 1.1f);
		}

		private void drawRulerMarkers(Batch batch, float alpha) {
			float top = graphBottom() + plotHeight();
			batch.setColor(0.96f, 0.29f, 0.12f, alpha);
			for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
				float time = timeAt(frame);
				if (time < timeMin || time > timeMax) continue;
				drawDiamond(batch, toX(time), top + 4.5f, 3.2f);
			}
			float currentTime = doc.getCurrentTime();
			if (currentTime < timeMin || currentTime > timeMax) return;
			float x = toX(currentTime);
			batch.setColor(0.05f, 0.84f, 0.93f, alpha);
			drawDiamond(batch, x, top + 10f, 4f);
			String frameLabel = Long.toString(Math.round(frameAtTime(currentTime,
				UiNumbers.effectiveFps(doc.data == null ? 0f : doc.data.getFps()))));
			axisLayout.setText(font, frameLabel);
			float labelX = Math.max(getX() + 2f, Math.min(x - axisLayout.width / 2f,
				getX() + getWidth() - axisLayout.width - 2f));
			batch.setColor(0.045f, 0.05f, 0.06f, alpha * 0.96f);
			float labelBaseline = getY() + getHeight() - 1f;
			batch.draw(kit.white.getRegion(), labelX - 2f,
				labelBaseline - axisLayout.height - 3f, axisLayout.width + 4f, axisLayout.height + 5f);
			oldFontColor.set(font.getColor());
			font.setColor(0.05f, 0.84f, 0.93f, alpha);
			font.draw(batch, frameLabel, labelX, labelBaseline);
			font.setColor(oldFontColor);
		}

		private void drawDiamond(Batch batch, float centerX, float centerY, float radius) {
			batch.draw(kit.white.getRegion(), centerX - radius, centerY - radius,
				radius, radius, radius * 2f, radius * 2f, 1f, 1f, 45f);
		}

		private void drawGrid(Batch batch, float alpha) {
			float left = graphLeft(), bottom = graphBottom();
			float right = left + plotWidth(), top = bottom + plotHeight();
			float fps = UiNumbers.effectiveFps(doc.data == null ? 0f : doc.data.getFps());
			double frameMin = frameAtTime(timeMin, fps);
			double frameMax = frameAtTime(timeMax, fps);
			double frameStep = Math.max(1d, niceTickStep(frameMax - frameMin,
				Math.max(2, (int)(plotWidth() / 70f))));
			double minorFrameStep = frameStep >= 5d ? Math.max(1d, frameStep / 5d) : 1d;

			batch.setColor(0.13f, 0.15f, 0.17f, alpha);
			if (minorFrameStep < frameStep) {
				double firstMinor = firstTick(frameMin, minorFrameStep);
				for (int index = 0; index < 1000; index++) {
					double frame = firstMinor + index * minorFrameStep;
					if (frame > frameMax + minorFrameStep * 0.000001d) break;
					if (isMajorTick(frame, frameStep)) continue;
					float x = toX((float)(frame / fps));
					drawLine(batch, x, top, x, top + 6f, 0.8f);
				}
			}

			double valueStep = niceTickStep(valueMax - valueMin,
				Math.max(2, (int)(plotHeight() / 58f)));
			double firstValue = firstTick(valueMin, valueStep);
			for (int index = 0; index < 1000; index++) {
				double value = firstValue + index * valueStep;
				if (value > valueMax + valueStep * 0.000001d) break;
				float y = toY((float)value);
				boolean zero = Math.abs(value) <= valueStep * 0.000001d;
				batch.setColor(zero ? 0.42f : 0.18f, zero ? 0.19f : 0.20f,
					zero ? 0.15f : 0.23f, alpha);
				drawLine(batch, left, y, right, y, zero ? 1.2f : 0.7f);
				batch.setColor(0.62f, 0.66f, 0.72f, alpha);
				drawRightAxisLabel(batch, formatValueTick(value), y);
			}

			double firstFrame = firstTick(frameMin, frameStep);
			for (int index = 0; index < 1000; index++) {
				double frame = firstFrame + index * frameStep;
				if (frame > frameMax + frameStep * 0.000001d) break;
				float x = toX((float)(frame / fps));
				batch.setColor(0.18f, 0.20f, 0.23f, alpha);
				drawLine(batch, x, bottom, x, top, 0.7f);
				drawLine(batch, x, top, x, top + 10f, 1f);
				batch.setColor(0.62f, 0.66f, 0.72f, alpha);
				drawCenteredAxisLabel(batch, formatFrameTick(frame), x, top + TOP - 4f);
			}

			batch.setColor(0.25f, 0.28f, 0.31f, alpha);
			drawLine(batch, left, top, right, top, 1f);
			drawLine(batch, right, bottom, right, top, 1f);
		}

		private boolean isMajorTick(double value, double majorStep) {
			double nearest = Math.rint(value / majorStep) * majorStep;
			return Math.abs(value - nearest) <= majorStep * 0.000001d;
		}

		private void drawCenteredAxisLabel(Batch batch, String text, float x, float y) {
			axisLayout.setText(font, text);
			float labelX = Math.max(getX() + 2f,
				Math.min(x - axisLayout.width / 2f, getX() + getWidth() - axisLayout.width - 2f));
			font.draw(batch, text, labelX, y);
		}

		private void drawRightAxisLabel(Batch batch, String text, float y) {
			axisLayout.setText(font, text);
			float labelX = getX() + getWidth() - axisLayout.width - 6f;
			font.draw(batch, text, labelX, y + axisLayout.height / 2f);
		}

		private void drawChannel(Batch batch, int channel, float alpha) {
			Color color = colors[channel % colors.length];
			batch.setColor(color.r, color.g, color.b, alpha);
			for (int frame = 0; frame < timeline.getFrameCount() - 1; frame++) {
				float t1 = timeAt(frame), t2 = timeAt(frame + 1);
				float v1 = valueAt(frame, channel), v2 = valueAt(frame + 1, channel);
				int type = editor.curveType(timeline, frame);
				if (type == CurveTimeline.STEPPED) {
					drawLine(batch, toX(t1), toY(v1), toX(t2), toY(v1), 1.8f);
					drawLine(batch, toX(t2), toY(v1), toX(t2), toY(v2), 1f);
				} else if (type < CurveTimeline.BEZIER) {
					drawLine(batch, toX(t1), toY(v1), toX(t2), toY(v2), 1.8f);
				} else {
					float[] handles = handlesForSegment(frame, channel);
					if (handles == null) {
						drawLine(batch, toX(t1), toY(v1), toX(t2), toY(v2), 1.8f);
						continue;
					}
					float previousX = toX(t1), previousY = toY(v1);
					for (int step = 1; step <= 36; step++) {
						float u = step / 36f;
						float time = cubic(t1, t1 + (t2 - t1) * handles[0],
							t1 + (t2 - t1) * handles[2], t2, u);
						float value = cubic(v1, handles[1], handles[3], v2, u);
						float x = toX(time), y = toY(value);
						drawLine(batch, previousX, previousY, x, y, 1.8f);
						previousX = x;
						previousY = y;
					}
				}
			}
			for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
				boolean pointSelected = selected[frame][channel];
				float size = pointSelected ? 9f : 6f;
				float x = toX(timeAt(frame)), y = toY(valueAt(frame, channel));
				if (pointSelected)
					batch.setColor(0.98f, 0.98f, 1f, alpha);
				batch.draw(kit.white.getRegion(), x - size / 2f, y - size / 2f, size, size);
				batch.setColor(color.r, color.g, color.b, alpha);
			}
		}

		private void drawSelectionHandles(Batch batch, float alpha) {
			if (selectedFrame < 0 || selectedFrame >= timeline.getFrameCount()
				|| !visible[selectedChannel]) return;
			float keyX = toX(timeAt(selectedFrame));
			float keyY = toY(valueAt(selectedFrame, selectedChannel));
			Color color = channelColor(selectedChannel);
			if (isBezierSegment(selectedFrame - 1)) {
				float[] incoming = handlePoint(selectedFrame - 1, 2, false);
				if (incoming != null) {
					batch.setColor(color.r, color.g, color.b, alpha * 0.95f);
					drawDashedLine(batch, keyX, keyY, incoming[0], incoming[1], 5f, 3f, 1.2f);
					drawHandleNode(batch, incoming[0], incoming[1], alpha);
				}
			}
			if (isBezierSegment(selectedFrame)) {
				float[] outgoing = handlePoint(selectedFrame, 1, false);
				if (outgoing != null) {
					batch.setColor(color.r, color.g, color.b, alpha * 0.95f);
					drawDashedLine(batch, keyX, keyY, outgoing[0], outgoing[1], 5f, 3f, 1.2f);
					drawHandleNode(batch, outgoing[0], outgoing[1], alpha);
				}
			}
		}

		private void drawHandleNode(Batch batch, float x, float y, float alpha) {
			batch.setColor(0.08f, 0.88f, 0.94f, alpha);
			batch.draw(kit.white.getRegion(), x - 4.5f, y - 4.5f, 9f, 9f);
			batch.setColor(0.045f, 0.05f, 0.06f, alpha);
			batch.draw(kit.white.getRegion(), x - 2.5f, y - 2.5f, 5f, 5f);
		}

		private int[] nearestKey(float x, float y) {
			float best = HIT_RADIUS * HIT_RADIUS;
			int[] result = null;
			for (int channel = 0; channel < channelLabels.length; channel++) {
				if (!visible[channel]) continue;
				for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
					float dx = x - localX(timeAt(frame));
					float dy = y - localY(valueAt(frame, channel));
					float distance = dx * dx + dy * dy;
					if (distance <= best) { best = distance; result = new int[] {frame, channel}; }
				}
			}
			return result;
		}

		private int nearestHandle(float x, float y) {
			if (selectedFrame < 0 || selectedFrame >= timeline.getFrameCount()
				|| !visible[selectedChannel]) return 0;
			int nearest = 0;
			float best = HIT_RADIUS * HIT_RADIUS;
			if (isBezierSegment(selectedFrame - 1)) {
				float[] incoming = handlePoint(selectedFrame - 1, 2, true);
				if (incoming != null) {
					float distance = square(x - incoming[0]) + square(y - incoming[1]);
					if (distance <= best) { best = distance; nearest = 1; }
				}
			}
			if (isBezierSegment(selectedFrame)) {
				float[] outgoing = handlePoint(selectedFrame, 1, true);
				if (outgoing != null) {
					float distance = square(x - outgoing[0]) + square(y - outgoing[1]);
					if (distance <= best) nearest = 2;
				}
			}
			return nearest;
		}

		private void beginHandleDrag(int handle) {
			if (!editTime && !editValue) return;
			previewSegment = handle == 1 ? selectedFrame - 1 : selectedFrame;
			float[] current = handlesForSegment(previewSegment, selectedChannel);
			if (current == null) return;
			previewX1 = current[0]; previewY1 = current[1];
			previewX2 = current[2]; previewY2 = current[3];
			linkedPreviewSegment = -1;
			if (!separateHandles) {
				int counterpart = handle == 1 ? selectedFrame : selectedFrame - 1;
				float[] linked = isBezierSegment(counterpart)
					? handlesForSegment(counterpart, selectedChannel) : null;
				if (linked != null) {
					linkedPreviewSegment = counterpart;
					linkedPreviewX1 = linked[0]; linkedPreviewY1 = linked[1];
					linkedPreviewX2 = linked[2]; linkedPreviewY2 = linked[3];
				}
			}
			dragMode = handle == 1 ? 3 : 4;
		}

		private void updateHandleDrag(float x, float y) {
			float t1 = timeAt(previewSegment), t2 = timeAt(previewSegment + 1);
			float cx = Math.max(0f, Math.min(1f, (toTime(x) - t1) / Math.max(0.000001f, t2 - t1)));
			float cy = toValue(y);
			if (dragMode == 3) {
				previewX2 = editableHandleCoordinate(editTime, cx, previewX2);
				previewY2 = editableHandleCoordinate(editValue, cy, previewY2);
			} else {
				previewX1 = editableHandleCoordinate(editTime, cx, previewX1);
				previewY1 = editableHandleCoordinate(editValue, cy, previewY1);
			}
			if (editValue) updateLinkedHandleSlope();
		}

		private void updateLinkedHandleSlope() {
			if (linkedPreviewSegment < 0) return;
			if (dragMode == 3) {
				float duration = timeAt(previewSegment + 1) - timeAt(previewSegment);
				float endValue = valueAt(previewSegment + 1, selectedChannel);
				float slope = incomingSlope(duration, endValue, previewX2, previewY2);
				float linkedDuration = timeAt(linkedPreviewSegment + 1) - timeAt(linkedPreviewSegment);
				float linkedStartValue = valueAt(linkedPreviewSegment, selectedChannel);
				linkedPreviewY1 = outgoingControlValueForSlope(slope, linkedDuration, linkedStartValue,
					linkedPreviewX1);
			} else {
				float duration = timeAt(previewSegment + 1) - timeAt(previewSegment);
				float startValue = valueAt(previewSegment, selectedChannel);
				float slope = outgoingSlope(duration, startValue, previewX1, previewY1);
				float linkedDuration = timeAt(linkedPreviewSegment + 1) - timeAt(linkedPreviewSegment);
				float linkedEndValue = valueAt(linkedPreviewSegment + 1, selectedChannel);
				linkedPreviewY2 = incomingControlValueForSlope(slope, linkedDuration, linkedEndValue,
					linkedPreviewX2);
			}
		}

		private void commitHandles() {
			if (!syncLiveTimeline() || previewSegment < 0) return;
			AnimationEditor.TimelineSnapshot before = host.snapshot(timeline);
			long revision = doc.revision;
			try {
				timeline = editor.setBezierHandlesChannel(animation, timeline, previewSegment, selectedChannel,
					previewX1, previewY1, previewX2, previewY2);
				if (linkedPreviewSegment >= 0)
					timeline = editor.setBezierHandlesChannel(animation, timeline, linkedPreviewSegment,
						selectedChannel, linkedPreviewX1, linkedPreviewY1,
						linkedPreviewX2, linkedPreviewY2);
				if (doc.revision != revision) {
					host.record("Graph 编辑关键帧斜率", before);
					host.changed();
				}
				refreshViewAfterEdit();
				refreshInspector();
			} catch (RuntimeException error) {
				errorLabel.setText(error.getMessage());
			} finally {
				previewSegment = -1;
				linkedPreviewSegment = -1;
			}
		}

		private boolean isBezierSegment(int segment) {
			return segment >= 0 && segment < timeline.getFrameCount() - 1
				&& editor.curveType(timeline, segment) >= CurveTimeline.BEZIER;
		}

		private float[] handlesForSegment(int segment, int channel) {
			if (!isBezierSegment(segment)) return null;
			if ((dragMode == 3 || dragMode == 4) && channel == selectedChannel) {
				if (segment == previewSegment)
					return new float[] {previewX1, previewY1, previewX2, previewY2};
				if (segment == linkedPreviewSegment)
					return new float[] {linkedPreviewX1, linkedPreviewY1,
						linkedPreviewX2, linkedPreviewY2};
			}
			try {
				AnimationEditor.BezierHandles curve = editor.bezierHandles(timeline, segment, channel);
				if (curve != null) {
					float[] handles = {curve.cx1(), curve.value1(), curve.cx2(), curve.value2()};
					if (dragMode == 1 && previewTimes != null && previewValues != null
						&& !(timeline instanceof DeformTimeline)) {
						// Match the model's endpoint translation of each Bezier control when previewing key edits.
						float t1 = editor.frameTime(timeline, segment), t2 = editor.frameTime(timeline, segment + 1);
						float dt1 = timeAt(segment) - t1, dt2 = timeAt(segment + 1) - t2;
						float dv1 = valueAt(segment, channel) - editor.curveFrameValue(timeline, segment, channel);
						float dv2 = valueAt(segment + 1, channel) - editor.curveFrameValue(timeline, segment + 1, channel);
						float duration = timeAt(segment + 1) - timeAt(segment);
						handles[0] = (t1 + (t2 - t1) * handles[0] + (2f * dt1 + dt2) / 3f - timeAt(segment)) / duration;
						handles[2] = (t1 + (t2 - t1) * handles[2] + (dt1 + 2f * dt2) / 3f - timeAt(segment)) / duration;
						handles[1] += (2f * dv1 + dv2) / 3f;
						handles[3] += (dv1 + 2f * dv2) / 3f;
					}
					return handles;
				}
			} catch (RuntimeException ignored) {
			}
			return null;
		}

		private float[] handlePoint(int segment, int control, boolean local) {
			float[] handles = handlesForSegment(segment, selectedChannel);
			if (handles == null) return null;
			float t1 = timeAt(segment), t2 = timeAt(segment + 1);
			int offset = control == 1 ? 0 : 2;
			float time = t1 + (t2 - t1) * handles[offset];
			float value = handles[offset + 1];
			return new float[] {local ? localX(time) : toX(time), local ? localY(value) : toY(value)};
		}

		private float timeAt(int frame) {
			return dragMode == 1 && previewTimes != null ? previewTimes[frame] : editor.frameTime(timeline, frame);
		}

		private float valueAt(int frame, int channel) {
			return dragMode == 1 && previewValues != null
				? previewValues[frame][channel] : editor.curveFrameValue(timeline, frame, channel);
		}

		private float snapTime(float time) {
			if (!snap || shiftPressed()) return Math.max(0f, time);
			float fps = Math.max(1f, doc.data == null ? 30f : doc.data.getFps());
			return Math.max(0f, Math.round(time * fps) / fps);
		}

		private boolean shiftPressed() {
			return Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
				|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
		}

		private float clampKeyTime(int frame, float time) {
			if (frame > 0) time = Math.max(time, Math.nextUp(editor.frameTime(timeline, frame - 1)));
			if (frame < timeline.getFrameCount() - 1)
				time = Math.min(time, Math.nextDown(editor.frameTime(timeline, frame + 1)));
			return Math.max(0f, time);
		}

		private void zoomTime(float anchor, float factor) {
			timeMin = anchor + (timeMin - anchor) * factor;
			timeMax = anchor + (timeMax - anchor) * factor;
			if (timeMin < 0f) { timeMax -= timeMin; timeMin = 0f; }
		}

		private void zoomValue(float anchor, float factor) {
			valueMin = anchor + (valueMin - anchor) * factor;
			valueMax = anchor + (valueMax - anchor) * factor;
		}

		private float graphLeft() { return getX() + LEFT; }
		private float graphBottom() { return getY() + BOTTOM; }
		private float plotWidth() { return Math.max(1f, getWidth() - LEFT - RIGHT); }
		private float plotHeight() { return Math.max(1f, getHeight() - BOTTOM - TOP); }
		private float toX(float time) { return graphLeft() + (time - timeMin) / (timeMax - timeMin) * plotWidth(); }
		private float toY(float value) { return graphBottom() + (value - valueMin) / (valueMax - valueMin) * plotHeight(); }
		private float localX(float time) { return LEFT + (time - timeMin) / (timeMax - timeMin) * plotWidth(); }
		private float localY(float value) { return BOTTOM + (value - valueMin) / (valueMax - valueMin) * plotHeight(); }
		private float toTime(float x) { return timeMin + (x - LEFT) / plotWidth() * (timeMax - timeMin); }
		private float toValue(float y) { return valueMin + (y - BOTTOM) / plotHeight() * (valueMax - valueMin); }

		private void drawLine(Batch batch, float x1, float y1, float x2, float y2, float thickness) {
			float dx = x2 - x1, dy = y2 - y1;
			float length = (float)Math.sqrt(dx * dx + dy * dy);
			if (length < 0.0001f) return;
			float angle = (float)Math.toDegrees(Math.atan2(dy, dx));
			batch.draw(kit.white.getRegion(), x1, y1 - thickness / 2f, 0f, thickness / 2f,
				length, thickness, 1f, 1f, angle);
		}

		private void drawDashedLine(Batch batch, float x1, float y1, float x2, float y2,
			float dashLength, float gapLength, float thickness) {
			float dx = x2 - x1, dy = y2 - y1;
			float length = (float)Math.sqrt(dx * dx + dy * dy);
			if (length < 0.0001f) return;
			float step = Math.max(1f, dashLength + gapLength);
			for (float offset = 0f; offset < length; offset += step) {
				float start = offset / length;
				float end = Math.min(length, offset + dashLength) / length;
				drawLine(batch, x1 + dx * start, y1 + dy * start,
					x1 + dx * end, y1 + dy * end, thickness);
			}
		}

		private float cubic(float a, float b, float c, float d, float t) {
			float inverse = 1f - t;
			return inverse * inverse * inverse * a + 3f * inverse * inverse * t * b
				+ 3f * inverse * t * t * c + t * t * t * d;
		}

		private float square(float value) { return value * value; }

	}
}
