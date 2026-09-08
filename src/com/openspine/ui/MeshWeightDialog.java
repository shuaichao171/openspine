package com.openspine.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
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
import com.esotericsoftware.spine.BoneData;
import com.openspine.model.SkeletonDocument;

/** Structured per-vertex editor for Spine's compact weighted-vertex encoding. */
final class MeshWeightDialog {
	record Influence(int boneIndex, float x, float y, float weight, boolean locked) {
		Influence(int boneIndex, float x, float y, float weight) {
			this(boneIndex, x, y, weight, false);
		}
	}

	record EncodedWeights(int[] bones, float[] vertices, Set<SkeletonDocument.MeshWeightLock> locks) {
		EncodedWeights(int[] bones, float[] vertices) { this(bones, vertices, Set.of()); }

		EncodedWeights {
			bones = bones == null ? null : bones.clone();
			vertices = vertices == null ? null : vertices.clone();
			locks = locks == null ? Set.of() : Set.copyOf(locks);
		}

		@Override public int[] bones() { return bones == null ? null : bones.clone(); }
		@Override public float[] vertices() { return vertices == null ? null : vertices.clone(); }
		@Override public Set<SkeletonDocument.MeshWeightLock> locks() { return locks; }
	}

	static final class WeightDraft {
		private final ArrayList<ArrayList<Influence>> vertices;
		private final int boneCount;

		private WeightDraft(ArrayList<ArrayList<Influence>> vertices, int boneCount) {
			this.vertices = vertices;
			this.boneCount = boneCount;
		}

		int vertexCount() { return vertices.size(); }
		int influenceCount(int vertex) { return influences(vertex).size(); }
		int boneCount() { return boneCount; }

		List<Influence> influences(int vertex) {
			if (vertex < 0 || vertex >= vertices.size())
				throw new IllegalArgumentException("权重顶点索引无效");
			return List.copyOf(vertices.get(vertex));
		}

		void replace(int vertex, List<Influence> influences) {
			if (vertex < 0 || vertex >= vertices.size())
				throw new IllegalArgumentException("权重顶点索引无效");
			validateInfluences(influences, boneCount);
			vertices.set(vertex, new ArrayList<>(influences));
		}

		void normalize(int vertex) {
			List<Influence> source = influences(vertex);
			float lockedSum = 0f, unlockedSum = 0f;
			int unlockedCount = 0;
			for (Influence influence : source) {
				if (influence.locked()) lockedSum += influence.weight();
				else { unlockedSum += influence.weight(); unlockedCount++; }
			}
			if (!Float.isFinite(lockedSum) || lockedSum > 1.000001f)
				throw new IllegalArgumentException("锁定骨骼的权重总和不能超过 100%");
			if (unlockedCount == 0 && Math.abs(lockedSum - 1f) > 0.000001f)
				throw new IllegalArgumentException("全部骨骼锁定时，权重总和必须等于 100%");
			float remaining = Math.max(0f, 1f - lockedSum);
			if (unlockedCount > 0 && !(unlockedSum > 0f) && remaining > 0f) unlockedSum = 0f;
			ArrayList<Influence> normalized = new ArrayList<>(source.size());
			for (Influence influence : source) {
				float weight = influence.weight();
				if (!influence.locked()) weight = unlockedSum > 0f
					? weight / unlockedSum * remaining : remaining / Math.max(1, unlockedCount);
				normalized.add(new Influence(influence.boneIndex(), influence.x(), influence.y(),
					weight, influence.locked()));
			}
			vertices.set(vertex, normalized);
		}

		void normalizeAll() {
			for (int vertex = 0; vertex < vertexCount(); vertex++) normalize(vertex);
		}
	}

	private record InfluenceRow(SelectBox<String> bone, TextField x, TextField y, TextField weight,
		TextButton locked) {}

	private final Stage stage;
	private final UiKit kit;
	private final Array<BoneData> bones;
	private final WeightDraft draft;
	private final int defaultBoneIndex;
	private final Consumer<EncodedWeights> onApply;
	private final BiConsumer<String, Boolean> onStatus;
	private final Actor previousKeyboardFocus;
	private final Window window;
	private final SelectBox<String> vertexBox;
	private final Table rowsTable;
	private final Label summaryLabel, errorLabel;
	private final TextButton addButton;
	private final ArrayList<InfluenceRow> rows = new ArrayList<>();
	private int loadedVertex;
	private boolean refreshing;
	private boolean closed;

	static void show(Stage stage, UiKit kit, Array<BoneData> bones, int defaultBoneIndex,
		WeightDraft draft, Consumer<EncodedWeights> onApply, BiConsumer<String, Boolean> onStatus) {
		if (stage == null || kit == null || bones == null || bones.isEmpty() || draft == null || onApply == null)
			return;
		new MeshWeightDialog(stage, kit, bones, defaultBoneIndex, draft, onApply, onStatus).open();
	}

	private MeshWeightDialog(Stage stage, UiKit kit, Array<BoneData> bones, int defaultBoneIndex,
		WeightDraft draft, Consumer<EncodedWeights> onApply, BiConsumer<String, Boolean> onStatus) {
		this.stage = stage;
		this.kit = kit;
		this.bones = new Array<>(bones);
		this.defaultBoneIndex = defaultBoneIndex;
		this.draft = draft;
		this.onApply = onApply;
		this.onStatus = onStatus;
		previousKeyboardFocus = stage.getKeyboardFocus();

		window = kit.window("逐顶点权重");
		window.setModal(true);
		kit.padWindow(window, 10f);

		vertexBox = kit.selectBox();
		Array<String> vertexItems = new Array<>();
		for (int vertex = 0; vertex < draft.vertexCount(); vertex++) vertexItems.add(Integer.toString(vertex));
		vertexBox.setItems(vertexItems);
		vertexBox.setMaxListCount(14);
		summaryLabel = kit.controlLabel("");
		Table selector = new Table();
		selector.add(kit.controlLabel("顶点")).padRight(5);
		selector.add(vertexBox).width(100).height(26).padRight(12);
		selector.add(summaryLabel).growX().left();
		window.add(selector).growX().row();

		rowsTable = new Table();
		rowsTable.top().left();
		ScrollPane scroll = new ScrollPane(rowsTable, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		scroll.setScrollingDisabled(true, false);
		window.add(scroll).width(730).height(250).growX().padTop(7).row();

		addButton = new TextButton("添加影响骨骼", kit.toolButton);
		TextButton normalizeButton = new TextButton("当前归一化", kit.toolButton);
		TextButton normalizeAllButton = new TextButton("全部归一化", kit.toolButton);
		Table editActions = new Table();
		editActions.add(addButton).width(126).height(27).padRight(6);
		editActions.add(normalizeButton).width(112).height(27).padRight(6);
		editActions.add(normalizeAllButton).width(112).height(27);
		window.add(editActions).left().padTop(7).row();

		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(errorLabel).width(720).left().padTop(5).row();

		TextButton applyButton = new TextButton("应用权重", kit.accentButton);
		TextButton closeButton = new TextButton("取消", kit.toolButton);
		Table actions = new Table();
		actions.add(applyButton).width(96).height(28).padRight(6);
		actions.add(closeButton).width(84).height(28);
		window.add(actions).right().padTop(7);

		vertexBox.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { changeVertex(); }
		});
		addButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { addInfluence(); }
		});
		normalizeButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { normalizeCurrent(); }
		});
		normalizeAllButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { normalizeAll(); }
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
		loadedVertex = 0;
		rebuildRows();
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		focusFirstValue();
	}

	private void changeVertex() {
		if (refreshing) return;
		int selected = vertexBox.getSelectedIndex();
		if (selected < 0 || selected == loadedVertex) return;
		if (!commitRows()) {
			refreshing = true;
			vertexBox.setSelectedIndex(loadedVertex);
			refreshing = false;
			return;
		}
		loadedVertex = selected;
		rebuildRows();
	}

	private void rebuildRows() {
		refreshing = true;
		rows.clear();
		rowsTable.clearChildren();
		rowsTable.add(new Label("骨骼", kit.labelStyle)).width(210).left().pad(3);
		rowsTable.add(new Label("X", kit.labelStyle)).width(105).left().pad(3);
		rowsTable.add(new Label("Y", kit.labelStyle)).width(105).left().pad(3);
		rowsTable.add(new Label("权重", kit.labelStyle)).width(105).left().pad(3);
		rowsTable.add(new Label("锁定", kit.labelStyle)).width(64).left().pad(3);
		rowsTable.add().width(70).row();
		List<Influence> influences = draft.influences(loadedVertex);
		for (int index = 0; index < influences.size(); index++) {
			Influence influence = influences.get(index);
			SelectBox<String> boneBox = kit.selectBox();
			Array<String> names = new Array<>();
			for (BoneData bone : bones) names.add(bone.getName());
			boneBox.setItems(names);
			boneBox.setSelectedIndex(influence.boneIndex());
			boneBox.setMaxListCount(14);
			TextField x = numberField(influence.x());
			TextField y = numberField(influence.y());
			TextField weight = numberField(influence.weight());
			TextButton locked = new TextButton("", kit.toolButton);
			locked.setProgrammaticChangeEvents(false);
			locked.setChecked(influence.locked());
			refreshLockButton(locked);
			locked.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { refreshLockButton(locked); }
			});
			TextButton remove = new TextButton("删除", kit.toolButton);
			remove.setDisabled(influences.size() <= 1);
			int rowIndex = index;
			remove.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) { removeInfluence(rowIndex); }
			});
			rows.add(new InfluenceRow(boneBox, x, y, weight, locked));
			rowsTable.add(boneBox).width(210).height(26).pad(3);
			rowsTable.add(x).width(105).height(26).pad(3);
			rowsTable.add(y).width(105).height(26).pad(3);
			rowsTable.add(weight).width(105).height(26).pad(3);
			rowsTable.add(locked).width(58).height(26).pad(3);
			rowsTable.add(remove).width(64).height(26).pad(3).row();
		}
		addButton.setDisabled(influences.size() >= bones.size);
		summaryLabel.setText((loadedVertex + 1) + " / " + draft.vertexCount()
			+ "  ·  " + influences.size() + " 个骨骼影响");
		errorLabel.setText("");
		refreshing = false;
	}

	private TextField numberField(float value) {
		TextField field = new TextField(UiNumbers.format(value), kit.textFieldStyle);
		field.setMaxLength(24);
		field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || c == '-' || c == '+'
			|| c == '.' || c == 'e' || c == 'E');
		return field;
	}

	private void refreshLockButton(TextButton button) {
		button.setText(button.isChecked() ? "已锁" : "未锁");
		button.setStyle(button.isChecked() ? kit.activeToolButton : kit.toolButton);
	}

	private boolean commitRows() {
		try {
			ArrayList<Influence> updated = new ArrayList<>(rows.size());
			for (InfluenceRow row : rows) {
				int boneIndex = row.bone().getSelectedIndex();
				updated.add(new Influence(boneIndex, parseFinite(row.x().getText(), "X"),
					parseFinite(row.y().getText(), "Y"), parseFinite(row.weight().getText(), "权重"),
					row.locked().isChecked()));
			}
			draft.replace(loadedVertex, updated);
			errorLabel.setText("");
			return true;
		} catch (RuntimeException error) {
			showError(error.getMessage());
			return false;
		}
	}

	private void addInfluence() {
		if (!commitRows()) return;
		try {
			List<Influence> current = draft.influences(loadedVertex);
			HashSet<Integer> used = new HashSet<>();
			for (Influence influence : current) used.add(influence.boneIndex());
			int boneIndex = defaultBoneIndex >= 0 && defaultBoneIndex < bones.size
				&& !used.contains(defaultBoneIndex) ? defaultBoneIndex : 0;
			while (boneIndex < bones.size && used.contains(boneIndex)) boneIndex++;
			if (boneIndex >= bones.size) return;
			Influence first = current.get(0);
			ArrayList<Influence> updated = new ArrayList<>(current);
			updated.add(new Influence(boneIndex, first.x(), first.y(), 0f));
			draft.replace(loadedVertex, updated);
			rebuildRows();
		} catch (RuntimeException error) {
			showError(error.getMessage());
		}
	}

	private void removeInfluence(int index) {
		if (!commitRows()) return;
		try {
			List<Influence> current = draft.influences(loadedVertex);
			if (current.size() <= 1 || index < 0 || index >= current.size()) return;
			ArrayList<Influence> updated = new ArrayList<>(current);
			updated.remove(index);
			draft.replace(loadedVertex, updated);
			rebuildRows();
		} catch (RuntimeException error) {
			showError(error.getMessage());
		}
	}

	private void normalizeCurrent() {
		if (!commitRows()) return;
		try {
			draft.normalize(loadedVertex);
			rebuildRows();
		} catch (RuntimeException error) {
			showError(error.getMessage());
		}
	}

	private void normalizeAll() {
		if (!commitRows()) return;
		try {
			draft.normalizeAll();
			rebuildRows();
		} catch (RuntimeException error) {
			showError(error.getMessage());
		}
	}

	private void submit() {
		if (!commitRows()) return;
		try {
			draft.normalizeAll();
			onApply.accept(encode(draft));
			close();
		} catch (RuntimeException error) {
			showError(error.getMessage());
		}
	}

	private void focusFirstValue() {
		if (rows.isEmpty()) stage.setKeyboardFocus(vertexBox);
		else {
			stage.setKeyboardFocus(rows.get(0).weight());
			rows.get(0).weight().selectAll();
		}
	}

	private void showError(String message) {
		String text = message == null ? "Mesh 权重无效" : message;
		errorLabel.setText(text);
		if (onStatus != null) onStatus.accept(text, true);
	}

	private void close() {
		if (closed) return;
		closed = true;
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}

	static WeightDraft decode(int[] bones, float[] values, int vertexCount, int boneCount) {
		return decode(bones, values, vertexCount, boneCount, Set.of());
	}

	static WeightDraft decode(int[] bones, float[] values, int vertexCount, int boneCount,
		Set<SkeletonDocument.MeshWeightLock> locks) {
		if (bones == null || values == null) throw new IllegalArgumentException("Mesh 尚未绑定权重");
		if (vertexCount <= 0 || boneCount <= 0) throw new IllegalArgumentException("Mesh 权重目标无效");
		ArrayList<ArrayList<Influence>> decoded = new ArrayList<>(vertexCount);
		int boneCursor = 0, valueCursor = 0;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			if (boneCursor >= bones.length) throw new IllegalArgumentException("权重骨骼编码不完整");
			int influenceCount = bones[boneCursor++];
			if (influenceCount <= 0 || boneCursor + influenceCount > bones.length
				|| valueCursor + influenceCount * 3 > values.length)
				throw new IllegalArgumentException("顶点权重数据不完整");
			ArrayList<Influence> influences = new ArrayList<>(influenceCount);
			for (int influence = 0; influence < influenceCount; influence++) {
				int boneIndex = bones[boneCursor++];
				boolean locked = locks != null
					&& locks.contains(new SkeletonDocument.MeshWeightLock(vertex, boneIndex));
				influences.add(new Influence(boneIndex, values[valueCursor], values[valueCursor + 1],
					values[valueCursor + 2], locked));
				valueCursor += 3;
			}
			validateInfluences(influences, boneCount);
			decoded.add(influences);
		}
		if (boneCursor != bones.length || valueCursor != values.length)
			throw new IllegalArgumentException("Mesh 权重包含多余数据");
		return new WeightDraft(decoded, boneCount);
	}

	static EncodedWeights encode(WeightDraft draft) {
		if (draft == null || draft.vertexCount() <= 0) throw new IllegalArgumentException("Mesh 权重为空");
		draft.normalizeAll();
		int boneLength = 0, valueLength = 0;
		for (int vertex = 0; vertex < draft.vertexCount(); vertex++) {
			List<Influence> influences = draft.influences(vertex);
			validateInfluences(influences, draft.boneCount());
			boneLength += influences.size() + 1;
			valueLength += influences.size() * 3;
		}
		int[] encodedBones = new int[boneLength];
		float[] encodedValues = new float[valueLength];
		LinkedHashSet<SkeletonDocument.MeshWeightLock> locks = new LinkedHashSet<>();
		int boneCursor = 0, valueCursor = 0;
		for (int vertex = 0; vertex < draft.vertexCount(); vertex++) {
			List<Influence> influences = draft.influences(vertex);
			encodedBones[boneCursor++] = influences.size();
			for (Influence influence : influences) {
				encodedBones[boneCursor++] = influence.boneIndex();
				encodedValues[valueCursor++] = influence.x();
				encodedValues[valueCursor++] = influence.y();
				encodedValues[valueCursor++] = influence.weight();
				if (influence.locked())
					locks.add(new SkeletonDocument.MeshWeightLock(vertex, influence.boneIndex()));
			}
		}
		return new EncodedWeights(encodedBones, encodedValues, locks);
	}

	static EncodedWeights bindToBone(float[] vertices, int vertexCount, int boneIndex, int boneCount) {
		if (vertices == null || vertexCount <= 0 || vertices.length != vertexCount * 2)
			throw new IllegalArgumentException("无权重顶点数量必须与 UV 数量一致");
		if (boneIndex < 0 || boneIndex >= boneCount) throw new IllegalArgumentException("绑定骨骼无效");
		int[] encodedBones = new int[vertexCount * 2];
		float[] encodedValues = new float[vertexCount * 3];
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			encodedBones[vertex * 2] = 1;
			encodedBones[vertex * 2 + 1] = boneIndex;
			encodedValues[vertex * 3] = vertices[vertex * 2];
			encodedValues[vertex * 3 + 1] = vertices[vertex * 2 + 1];
			encodedValues[vertex * 3 + 2] = 1f;
		}
		return new EncodedWeights(encodedBones, encodedValues);
	}

	private static void validateInfluences(List<Influence> influences, int boneCount) {
		if (influences == null || influences.isEmpty())
			throw new IllegalArgumentException("每个顶点至少需要一个影响骨骼");
		HashSet<Integer> seen = new HashSet<>();
		float sum = 0f;
		for (Influence influence : influences) {
			if (influence == null || influence.boneIndex() < 0 || influence.boneIndex() >= boneCount)
				throw new IllegalArgumentException("权重引用了不存在的骨骼");
			if (!seen.add(influence.boneIndex()))
				throw new IllegalArgumentException("同一顶点不能重复引用同一骨骼");
			if (!allFinite(influence.x(), influence.y(), influence.weight()) || influence.weight() < 0f)
				throw new IllegalArgumentException("顶点坐标和权重必须是有效有限数值");
			sum += influence.weight();
		}
		if (!(sum > 0f) || !Float.isFinite(sum))
			throw new IllegalArgumentException("每个顶点的权重总和必须大于 0");
	}

	private static float parseFinite(String text, String label) {
		try {
			return UiNumbers.parseFinite(text);
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(label + " 必须是有限数值", error);
		}
	}

	private static boolean allFinite(float... values) {
		for (float value : values) if (!Float.isFinite(value)) return false;
		return true;
	}
}
