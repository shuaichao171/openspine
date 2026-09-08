package com.openspine.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.SkeletonEditor.TransformFromPropertySetup;
import com.openspine.model.SkeletonEditor.TransformProperty;
import com.openspine.model.SkeletonEditor.TransformToPropertySetup;

/** Structured editor for Spine 4.3 Transform source-to-target property mappings. */
final class TransformPropertyDialog {
	record MappingDraft(TransformProperty source, float sourceOffset, TransformProperty target,
		float targetOffset, float max, float scale) {}

	private record MappingRow(SelectBox<String> source, TextField sourceOffset, SelectBox<String> target,
		TextField targetOffset, TextField max, TextField scale) {}

	private static final class SourceMappings {
		final TransformProperty property;
		final float offset;
		final LinkedHashMap<TransformProperty, TransformToPropertySetup> targets = new LinkedHashMap<>();

		SourceMappings(TransformProperty property, float offset) {
			this.property = property;
			this.offset = offset;
		}
	}

	private static final EnumMap<TransformProperty, String> PROPERTY_LABELS = new EnumMap<>(TransformProperty.class);
	static {
		PROPERTY_LABELS.put(TransformProperty.ROTATE, "旋转");
		PROPERTY_LABELS.put(TransformProperty.X, "X");
		PROPERTY_LABELS.put(TransformProperty.Y, "Y");
		PROPERTY_LABELS.put(TransformProperty.SCALE_X, "缩放 X");
		PROPERTY_LABELS.put(TransformProperty.SCALE_Y, "缩放 Y");
		PROPERTY_LABELS.put(TransformProperty.SHEAR_Y, "错切 Y");
	}

	private final Stage stage;
	private final UiKit kit;
	private final Consumer<List<TransformFromPropertySetup>> onApply;
	private final Actor previousKeyboardFocus;
	private final Window window;
	private final Table rowsTable;
	private final Label summaryLabel, errorLabel;
	private final TextButton addButton, applyButton;
	private final ArrayList<MappingRow> rows = new ArrayList<>();
	private final List<TransformFromPropertySetup> baseline;
	private boolean refreshing;
	private boolean closed;

	static void show(Stage stage, UiKit kit, List<TransformFromPropertySetup> properties,
		Consumer<List<TransformFromPropertySetup>> onApply) {
		if (stage == null || kit == null || properties == null || onApply == null) return;
		new TransformPropertyDialog(stage, kit, properties, onApply).open();
	}

	private TransformPropertyDialog(Stage stage, UiKit kit, List<TransformFromPropertySetup> properties,
		Consumer<List<TransformFromPropertySetup>> onApply) {
		this.stage = stage;
		this.kit = kit;
		this.onApply = onApply;
		baseline = copyProperties(properties);
		previousKeyboardFocus = stage.getKeyboardFocus();

		window = kit.window("Transform 属性映射");
		window.setModal(true);
		kit.padWindow(window, 10f);

		rowsTable = new Table();
		rowsTable.top().left();
		ScrollPane scroll = new ScrollPane(rowsTable, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		scroll.setScrollingDisabled(true, false);
		window.add(scroll).width(790).height(300).growX().row();

		addButton = new TextButton("添加映射", kit.toolButton);
		TextButton defaultsButton = new TextButton("恢复默认", kit.toolButton);
		summaryLabel = kit.controlLabel("");
		summaryLabel.setEllipsis(true);
		Table edits = new Table();
		edits.add(addButton).width(100).height(27).padRight(6);
		edits.add(defaultsButton).width(100).height(27).padRight(12);
		edits.add(summaryLabel).width(350).left();
		window.add(edits).growX().left().padTop(7).row();

		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).width(780).left().padTop(5).row();

		applyButton = new TextButton("应用映射", kit.accentButton);
		TextButton closeButton = new TextButton("取消", kit.toolButton);
		Table actions = new Table();
		actions.add(applyButton).width(96).height(28).padRight(6);
		actions.add(closeButton).width(84).height(28);
		window.add(actions).right().padTop(7);

		for (MappingDraft mapping : flattenProperties(properties)) rows.add(createRow(mapping));
		addButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { addMapping(); }
		});
		defaultsButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { restoreDefaults(); }
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
				return false;
			}
		});
	}

	private void open() {
		renderRows();
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		if (!rows.isEmpty()) stage.setKeyboardFocus(rows.get(0).source());
	}

	private MappingRow createRow(MappingDraft mapping) {
		SelectBox<String> source = propertyBox(mapping.source());
		TextField sourceOffset = numberField(mapping.sourceOffset());
		SelectBox<String> target = propertyBox(mapping.target());
		TextField targetOffset = numberField(mapping.targetOffset());
		TextField max = numberField(mapping.max());
		TextField scale = numberField(mapping.scale());
		MappingRow row = new MappingRow(source, sourceOffset, target, targetOffset, max, scale);
		ChangeListener listener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshing) refreshValidation();
			}
		};
		for (Actor actor : rowActors(row)) actor.addListener(listener);
		return row;
	}

	private void renderRows() {
		refreshing = true;
		try {
			rowsTable.clearChildren();
			addHeader("来源", 105);
			addHeader("来源偏移", 92);
			addHeader("目标", 105);
			addHeader("目标偏移", 92);
			addHeader("最大值", 84);
			addHeader("缩放", 84);
			rowsTable.add().width(64).row();
			for (MappingRow row : rows) {
				rowsTable.add(row.source()).width(105).height(26).pad(3);
				rowsTable.add(row.sourceOffset()).width(92).height(26).pad(3);
				rowsTable.add(row.target()).width(105).height(26).pad(3);
				rowsTable.add(row.targetOffset()).width(92).height(26).pad(3);
				rowsTable.add(row.max()).width(84).height(26).pad(3);
				rowsTable.add(row.scale()).width(84).height(26).pad(3);
				TextButton remove = new TextButton("删除", kit.toolButton);
				remove.addListener(new ChangeListener() {
					@Override public void changed(ChangeEvent event, Actor actor) {
						rows.remove(row);
						renderRows();
					}
				});
				rowsTable.add(remove).width(64).height(26).pad(3).row();
			}
		} finally {
			refreshing = false;
		}
		refreshValidation();
	}

	private void addHeader(String text, float width) {
		rowsTable.add(new Label(text, kit.labelStyle)).width(width).left().pad(3);
	}

	private void addMapping() {
		MappingDraft next = nextUnusedMapping();
		if (next == null) return;
		rows.add(createRow(next));
		renderRows();
		MappingRow row = rows.get(rows.size() - 1);
		stage.setKeyboardFocus(row.sourceOffset());
		row.sourceOffset().selectAll();
	}

	private MappingDraft nextUnusedMapping() {
		for (TransformProperty source : TransformProperty.values()) {
			for (TransformProperty target : TransformProperty.values()) {
				boolean used = false;
				for (MappingRow row : rows)
					if (property(row.source()) == source && property(row.target()) == target) { used = true; break; }
				if (used) continue;
				float sourceOffset = 0f;
				for (MappingRow row : rows) {
					if (property(row.source()) != source) continue;
					try { sourceOffset = parseNumber(row.sourceOffset().getText(), "来源偏移"); }
					catch (IllegalArgumentException ignored) { }
					break;
				}
				return new MappingDraft(source, sourceOffset, target, 0f, 1f, 1f);
			}
		}
		return null;
	}

	private void restoreDefaults() {
		rows.clear();
		for (MappingDraft mapping : flattenProperties(SkeletonEditor.defaultTransformProperties()))
			rows.add(createRow(mapping));
		renderRows();
	}

	private void refreshValidation() {
		addButton.setDisabled(rows.size() >= TransformProperty.values().length * TransformProperty.values().length);
		try {
			List<TransformFromPropertySetup> properties = propertiesFromRows();
			int targetCount = 0;
			for (TransformFromPropertySetup from : properties) targetCount += from.targets().size();
			summaryLabel.setText(properties.size() + " 个来源 · " + targetCount + " 个映射");
			errorLabel.setText("");
			applyButton.setDisabled(properties.equals(baseline));
		} catch (IllegalArgumentException error) {
			summaryLabel.setText(rows.size() + " 个映射");
			errorLabel.setText(error.getMessage());
			applyButton.setDisabled(true);
		}
	}

	private void submit() {
		try {
			onApply.accept(propertiesFromRows());
			close();
		} catch (IllegalArgumentException error) {
			errorLabel.setText(error.getMessage());
		}
	}

	private List<TransformFromPropertySetup> propertiesFromRows() {
		ArrayList<MappingDraft> mappings = new ArrayList<>(rows.size());
		for (MappingRow row : rows) mappings.add(new MappingDraft(property(row.source()),
			parseNumber(row.sourceOffset().getText(), "来源偏移"), property(row.target()),
			parseNumber(row.targetOffset().getText(), "目标偏移"), parseNumber(row.max().getText(), "最大值"),
			parseNumber(row.scale().getText(), "缩放")));
		return groupMappings(mappings);
	}

	private SelectBox<String> propertyBox(TransformProperty selected) {
		SelectBox<String> box = kit.selectBox();
		Array<String> items = new Array<>();
		for (TransformProperty property : TransformProperty.values()) items.add(PROPERTY_LABELS.get(property));
		box.setItems(items);
		box.setSelected(PROPERTY_LABELS.get(selected));
		return box;
	}

	private TextField numberField(float value) {
		TextField field = new TextField(UiNumbers.format(value), kit.textFieldStyle);
		field.setMaxLength(24);
		field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+'
			|| c == '.' || c == 'e' || c == 'E');
		return field;
	}

	private static Actor[] rowActors(MappingRow row) {
		return new Actor[] {row.source(), row.sourceOffset(), row.target(), row.targetOffset(), row.max(), row.scale()};
	}

	private static TransformProperty property(SelectBox<String> box) {
		String selected = box.getSelected();
		for (Map.Entry<TransformProperty, String> entry : PROPERTY_LABELS.entrySet())
			if (entry.getValue().equals(selected)) return entry.getKey();
		throw new IllegalArgumentException("Transform 属性无效");
	}

	private static float parseNumber(String value, String label) {
		try {
			return UiNumbers.parseFinite(value);
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(label + "必须是有限数值", error);
		}
	}

	private void close() {
		if (closed) return;
		closed = true;
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}

	static List<MappingDraft> flattenProperties(List<TransformFromPropertySetup> properties) {
		if (properties == null) throw new IllegalArgumentException("Transform 属性映射不能为空");
		ArrayList<MappingDraft> mappings = new ArrayList<>();
		for (TransformFromPropertySetup from : properties) {
			if (from == null || from.property() == null || from.targets() == null)
				throw new IllegalArgumentException("Transform 属性映射不完整");
			for (TransformToPropertySetup to : from.targets()) {
				if (to == null || to.property() == null) throw new IllegalArgumentException("Transform 目标属性不完整");
				mappings.add(new MappingDraft(from.property(), from.offset(), to.property(),
					to.offset(), to.max(), to.scale()));
			}
		}
		return List.copyOf(mappings);
	}

	static List<TransformFromPropertySetup> groupMappings(List<MappingDraft> mappings) {
		if (mappings == null) throw new IllegalArgumentException("Transform 属性映射不能为空");
		LinkedHashMap<TransformProperty, SourceMappings> grouped = new LinkedHashMap<>();
		for (MappingDraft mapping : mappings) {
			if (mapping == null || mapping.source() == null || mapping.target() == null)
				throw new IllegalArgumentException("Transform 属性映射不完整");
			if (!finite(mapping.sourceOffset(), mapping.targetOffset(), mapping.max(), mapping.scale()))
				throw new IllegalArgumentException("Transform 属性映射必须使用有限数值");
			SourceMappings source = grouped.get(mapping.source());
			if (source == null) {
				source = new SourceMappings(mapping.source(), mapping.sourceOffset());
				grouped.put(mapping.source(), source);
			} else if (Float.compare(source.offset, mapping.sourceOffset()) != 0) {
				throw new IllegalArgumentException("同一来源属性的来源偏移必须一致");
			}
			TransformToPropertySetup target = new TransformToPropertySetup(mapping.target(),
				mapping.targetOffset(), mapping.max(), mapping.scale());
			if (source.targets.putIfAbsent(mapping.target(), target) != null)
				throw new IllegalArgumentException("同一来源属性不能重复映射到同一目标");
		}
		ArrayList<TransformFromPropertySetup> properties = new ArrayList<>(grouped.size());
		for (SourceMappings source : grouped.values())
			properties.add(new TransformFromPropertySetup(source.property, source.offset,
				List.copyOf(source.targets.values())));
		return List.copyOf(properties);
	}

	private static List<TransformFromPropertySetup> copyProperties(List<TransformFromPropertySetup> properties) {
		return groupMappings(flattenProperties(properties));
	}

	private static boolean finite(float... values) {
		for (float value : values) if (!Float.isFinite(value)) return false;
		return true;
	}
}
