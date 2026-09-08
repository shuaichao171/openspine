package com.openspine.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.MeshGeneration;
import com.openspine.model.SkeletonEditor.MeshGeometryState;
import com.openspine.model.SkeletonEditor.MeshVertexOrder;

/** Modal editor for independent mesh geometry, topology, UVs, and structured or raw Spine weights. */
final class MeshGeometryDialog {
	private final Stage stage;
	private final UiKit kit;
	private final SkeletonDocument doc;
	private final SkeletonEditor editor;
	private final MeshAttachment mesh;
	private final SlotData slot;
	private final EditHistory history;
	private final Runnable onChanged;
	private final BiConsumer<String, Boolean> onStatus;
	private final String meshWeightStateKey;
	private final Actor previousKeyboardFocus;
	private final Window window;
	private final TextField pathField, colorField, widthField, heightField, hullField, hullOrderField,
		weldToleranceField;
	private final TextArea bonesArea, verticesArea, uvsArea, trianglesArea, edgesArea;
	private final TextButton bindButton, unbindButton, normalizeButton, structuredWeightButton,
		smoothButton, copyWeightButton, weldButton, autoHullButton, reorderHullButton, applyButton;
	private final Slider smoothSlider;
	private final Label smoothValueLabel, summaryLabel, errorLabel;
	private MeshGeometryState baseline;
	private Set<SkeletonDocument.MeshWeightLock> baselineLocks = Set.of(), draftLocks = Set.of();
	private boolean refreshing;
	private boolean closed;
	private record MeshChoice(String label, MeshAttachment mesh) {}

	static void show(Stage stage, UiKit kit, SkeletonDocument doc, SkeletonEditor editor,
		MeshAttachment mesh, SlotData slot, EditHistory history, Runnable onChanged,
		BiConsumer<String, Boolean> onStatus) {
		if (stage == null || kit == null || doc == null || editor == null || mesh == null || slot == null) return;
		new MeshGeometryDialog(stage, kit, doc, editor, mesh, slot, history, onChanged, onStatus).open();
	}

	private MeshGeometryDialog(Stage stage, UiKit kit, SkeletonDocument doc, SkeletonEditor editor,
		MeshAttachment mesh, SlotData slot, EditHistory history, Runnable onChanged,
		BiConsumer<String, Boolean> onStatus) {
		this.stage = stage;
		this.kit = kit;
		this.doc = doc;
		this.editor = editor;
		this.mesh = mesh;
		this.slot = slot;
		this.history = history;
		this.onChanged = onChanged;
		this.onStatus = onStatus;
		meshWeightStateKey = editor.meshWeightStateKey(mesh, slot);
		baselineLocks = draftLocks = doc.meshWeightLocks(meshWeightStateKey);
		previousKeyboardFocus = stage.getKeyboardFocus();

		window = kit.window("Mesh 几何与权重");
		window.setModal(true);
		kit.padWindow(window, 10f);

		pathField = new TextField("", kit.textFieldStyle);
		pathField.setMaxLength(512);
		colorField = new TextField("", kit.textFieldStyle);
		colorField.setMaxLength(9);
		widthField = numberField();
		heightField = numberField();
		hullField = integerField();
		hullOrderField = indexListField();
		weldToleranceField = numberField();
		weldToleranceField.setText("0.01");

		Table identity = new Table();
		identity.add(kit.controlLabel("图片")).right().padRight(5);
		identity.add(pathField).width(240).height(26).padRight(12);
		identity.add(kit.controlLabel("颜色")).right().padRight(5);
		identity.add(colorField).width(100).height(26);
		window.add(identity).colspan(2).left().row();

		Table dimensions = new Table();
		dimensions.add(kit.controlLabel("宽")).right().padRight(5);
		dimensions.add(widthField).width(90).height(26).padRight(12);
		dimensions.add(kit.controlLabel("高")).right().padRight(5);
		dimensions.add(heightField).width(90).height(26).padRight(12);
		dimensions.add(kit.controlLabel("Hull 顶点")).right().padRight(5);
		dimensions.add(hullField).width(70).height(26);
		window.add(dimensions).colspan(2).left().padTop(6).row();

		autoHullButton = new TextButton("自动计算外壳", kit.toolButton);
		reorderHullButton = new TextButton("按顺序设置", kit.toolButton);
		Table hullActions = new Table();
		hullActions.add(kit.controlLabel("外壳顶点顺序")).right().padRight(5);
		hullActions.add(hullOrderField).width(320).height(26).padRight(7);
		hullActions.add(autoHullButton).width(112).height(27).padRight(6);
		hullActions.add(reorderHullButton).width(104).height(27);
		window.add(hullActions).colspan(2).left().padTop(6).row();

		verticesArea = area();
		uvsArea = area();
		trianglesArea = area();
		edgesArea = area();
		bonesArea = area();
		Table left = new Table();
		left.add(sectionLabel("顶点 / 权重数据")).left().row();
		left.add(verticesArea).width(350).height(128).growX().row();
		left.add(sectionLabel("UV 坐标")).left().padTop(6).row();
		left.add(uvsArea).width(350).height(92).growX().row();
		Table right = new Table();
		right.add(sectionLabel("三角形索引")).left().row();
		right.add(trianglesArea).width(350).height(100).growX().row();
		right.add(sectionLabel("边索引")).left().padTop(6).row();
		right.add(edgesArea).width(350).height(76).growX().row();
		right.add(sectionLabel("权重骨骼编码（空为无权重）")).left().padTop(6).row();
		right.add(bonesArea).width(350).height(76).growX().row();
		window.add(left).top().padTop(8).padRight(8);
		window.add(right).top().padTop(8).row();

		bindButton = new TextButton("绑定到槽骨骼", kit.toolButton);
		unbindButton = new TextButton("解除权重", kit.toolButton);
		normalizeButton = new TextButton("归一化权重", kit.toolButton);
		structuredWeightButton = new TextButton("逐顶点编辑", kit.toolButton);
		Table weightActions = new Table();
		weightActions.add(bindButton).width(120).height(27).padRight(6);
		weightActions.add(unbindButton).width(100).height(27).padRight(6);
		weightActions.add(normalizeButton).width(120).height(27).padRight(6);
		weightActions.add(structuredWeightButton).width(120).height(27);
		window.add(weightActions).colspan(2).left().padTop(7).row();

		smoothSlider = new Slider(0.1f, 1f, 0.1f, false, kit.sliderStyle);
		smoothSlider.setValue(0.5f);
		smoothValueLabel = kit.controlLabel("50%");
		smoothButton = new TextButton("平滑权重", kit.toolButton);
		copyWeightButton = new TextButton("复制权重", kit.toolButton);
		weldButton = new TextButton("焊接重合点", kit.toolButton);
		Table smoothingActions = new Table();
		smoothingActions.add(kit.controlLabel("平滑强度")).padRight(7);
		smoothingActions.add(smoothSlider).width(180).height(24).padRight(7);
		smoothingActions.add(smoothValueLabel).width(46).left().padRight(8);
		smoothingActions.add(smoothButton).width(104).height(27).padRight(8);
		smoothingActions.add(copyWeightButton).width(96).height(27).padRight(12);
		smoothingActions.add(kit.controlLabel("容差")).padRight(5);
		smoothingActions.add(weldToleranceField).width(70).height(27).padRight(6);
		smoothingActions.add(weldButton).width(108).height(27);
		window.add(smoothingActions).colspan(2).left().padTop(7).row();
		Table generationActions = new Table();
		addGenerationButton(generationActions, "自动权重…", this::showAutoWeights);
		addGenerationButton(generationActions, "图片轮廓…", this::showTrace);
		addGenerationButton(generationActions, "自动细分…", this::showSubdivide);
		window.add(generationActions).colspan(2).left().padTop(7).row();

		summaryLabel = new Label("", kit.labelStyle);
		errorLabel = new Label("", kit.labelStyle);
		errorLabel.setColor(1f, 0.45f, 0.38f, 1f);
		errorLabel.setWrap(true);
		window.add(summaryLabel).colspan(2).left().padTop(7).row();
		window.add(errorLabel).colspan(2).width(710).left().padTop(4).row();

		applyButton = new TextButton("应用", kit.accentButton);
		TextButton closeButton = new TextButton("关闭", kit.toolButton);
		Table actions = new Table();
		actions.add(applyButton).width(84).height(28).padRight(6);
		actions.add(closeButton).width(84).height(28);
		window.add(actions).colspan(2).right().padTop(7);

		ChangeListener draftListener = new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (!refreshing) refreshDraftState();
			}
		};
		for (Actor actor : draftActors()) actor.addListener(draftListener);
		bindButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { bindToSlotBone(); }
		});
		unbindButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { unbindFromBones(); }
		});
		normalizeButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { normalizeWeights(); }
		});
		structuredWeightButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { editStructuredWeights(); }
		});
		smoothSlider.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				smoothValueLabel.setText(Math.round(smoothSlider.getValue() * 100f) + "%");
			}
		});
		smoothButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { smoothWeights(); }
		});
		copyWeightButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { showCopyWeights(); }
		});
		weldButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { weldVertices(); }
		});
		autoHullButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { autoHull(); }
		});
		reorderHullButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { reorderHull(); }
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
					if (stage.getKeyboardFocus() instanceof TextArea) return false;
					if (!applyButton.isDisabled()) submit();
					return true;
				}
				return false;
			}
		});
	}

	private void open() {
		loadState(SkeletonEditor.meshGeometryState(mesh));
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(verticesArea);
	}

	private void addGenerationButton(Table actions, String label, Runnable action) {
		TextButton button = new TextButton(label, kit.toolButton);
		button.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
		});
		actions.add(button).width(120f).height(27f).padRight(7f);
	}

	private void showSubdivide() {
		MeshGenerationDialog.subdivide(stage, kit, passes ->
			applyGeneratedDraft(MeshGeneration.subdivide(draftState(), passes), draftLocks));
	}

	private void showTrace() {
		MeshGenerationDialog.trace(stage, kit, settings -> {
			MeshGeometryState draft = draftState();
			String blockReason = traceBlockReason(doc, mesh, draft, draftLocks);
			if (blockReason != null) throw new IllegalArgumentException(blockReason);
			if (!java.util.Objects.equals(draft.path(), mesh.getPath()))
				throw new IllegalArgumentException("请先应用图片路径，再生成轮廓");
			if (mesh.getSequence() == null || mesh.getSequence().getSetupIndex() < 0
				|| mesh.getSequence().getSetupIndex() >= mesh.getSequence().getRegions().length)
				throw new IllegalArgumentException("Mesh 没有有效的图片帧");
			TextureRegion region = mesh.getSequence().getRegions()[mesh.getSequence().getSetupIndex()];
			if (region == null || region.getTexture() == null) throw new IllegalArgumentException("Mesh 图片不可读取");
			int width = region instanceof AtlasRegion atlas ? atlas.originalWidth : region.getRegionWidth();
			int height = region instanceof AtlasRegion atlas ? atlas.originalHeight : region.getRegionHeight();
			if (width < 1 || height < 1 || (long)width * height > 16L * 1024 * 1024)
				throw new IllegalArgumentException("轮廓生成图片尺寸必须在 1600 万像素以内");
			float[] mapping = MeshGeneration.affineTransform(mesh.getRegionUVs(),
				mesh.getSequence().getUVs(mesh.getSequence().getSetupIndex()));
			TextureData textureData = region.getTexture().getTextureData();
			if (textureData.getType() != TextureData.TextureDataType.Pixmap)
				throw new IllegalArgumentException("该纹理没有可读取的像素数据");
			if (!textureData.isPrepared()) textureData.prepare();
			Pixmap pixels = textureData.consumePixmap();
			try {
				int[] alpha = sampleAlpha(pixels.getWidth(), pixels.getHeight(), region, mapping, width, height,
					(x, y) -> pixels.getPixel(x, y) & 255);
				MeshGeometryState generated = settings.concave()
					? MeshGeneration.traceConcave(draft, width, height, alpha, settings.threshold(),
						settings.tolerance(), settings.maxVertices())
					: MeshGeneration.traceConvex(draft, width, height, alpha, settings.threshold());
				applyGeneratedDraft(generated, Set.of());
			} finally {
				if (textureData.disposePixmap()) pixels.dispose();
			}
		});
	}

	static String traceBlockReason(SkeletonDocument doc, MeshAttachment mesh, MeshGeometryState draft,
		Set<SkeletonDocument.MeshWeightLock> locks) {
		if (doc == null || doc.data == null || mesh == null || draft == null) return "Mesh 几何数据不完整";
		if (mesh.getSourceMesh() != null) return "Linked Mesh 共享源网格几何，不能单独描边";
		if (locks != null && !locks.isEmpty()) return "描边会重建顶点，不能保留锁定权重；请先解除锁定";
		if (draft.weighted()) return "轮廓生成需要未加权 Mesh";
		for (var animation : doc.data.getAnimations()) {
			for (var timeline : animation.getTimelines()) {
				if (!(timeline instanceof com.esotericsoftware.spine.Animation.DeformTimeline deform)) continue;
				var attachment = deform.getAttachment();
				if (attachment == mesh || attachment instanceof MeshAttachment linked && linked.getSourceMesh() == mesh)
					return "动画 " + animation.getName() + " 的 Deform 轨道阻止重新描边；顶点对应关系无法保留";
			}
		}
		return null;
	}

	static int[] sampleAlpha(int pixelWidth, int pixelHeight, TextureRegion region, float[] mapping,
		int width, int height, java.util.function.IntBinaryOperator alphaAt) {
		int[] alpha = new int[width * height];
		float minU = Math.min(region.getU(), region.getU2()), maxU = Math.max(region.getU(), region.getU2());
		float minV = Math.min(region.getV(), region.getV2()), maxV = Math.max(region.getV(), region.getV2());
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			float u = (x + 0.5f) / width, v = (y + 0.5f) / height;
			float imageU = mapping[0] * u + mapping[1] * v + mapping[2];
			float imageV = mapping[3] * u + mapping[4] * v + mapping[5];
			if (imageU < minU || imageU >= maxU || imageV < minV || imageV >= maxV) continue;
			int imageX = (int)(imageU * pixelWidth), imageY = (int)(imageV * pixelHeight);
			if (imageX >= 0 && imageY >= 0 && imageX < pixelWidth && imageY < pixelHeight)
				alpha[y * width + x] = alphaAt.applyAsInt(imageX, imageY);
		}
		return alpha;
	}

	private void showAutoWeights() {
		MeshGenerationDialog.autoWeight(stage, kit, doc, slot.getBoneData().getIndex(), settings -> {
			Skeleton setup = new Skeleton(doc.data);
			Skin skin = doc.data.getDefaultSkin();
			for (Skin candidate : doc.data.getSkins()) {
				com.badlogic.gdx.utils.Array<Skin.SkinEntry> entries = new com.badlogic.gdx.utils.Array<>();
				candidate.getAttachments(slot.getIndex(), entries);
				if (entries.notEmpty()) { skin = candidate; break; }
			}
			if (skin != null) { setup.setSkin(skin); setup.setupPoseSlots(); }
			setup.updateWorldTransform(Physics.pose);
			applyGeneratedDraft(MeshGeneration.autoWeight(draftState(), setup, slot.getIndex(), settings.bones(),
				settings.maxInfluences(), settings.falloff(), draftLocks), draftLocks);
		});
	}

	private void applyGeneratedDraft(MeshGeometryState generated, Set<SkeletonDocument.MeshWeightLock> locks) {
		String reason = editor.validateMeshGeometry(mesh, generated);
		if (reason != null) throw new IllegalArgumentException(reason);
		refreshing = true;
		try {
			bonesArea.setText(format(generated.bones(), 8));
			verticesArea.setText(format(generated.vertices(), 6));
			uvsArea.setText(format(generated.regionUVs(), 6));
			trianglesArea.setText(format(generated.triangles(), 3));
			edgesArea.setText(format(generated.edges(), 8));
			hullField.setText(Integer.toString(generated.hullLength() / 2));
			draftLocks = validLocks(generated, locks);
		} finally { refreshing = false; }
		errorLabel.setText("");
		refreshDraftState();
	}

	private Actor[] draftActors() {
		return new Actor[] {pathField, colorField, widthField, heightField, hullField, bonesArea,
			verticesArea, uvsArea, trianglesArea, edgesArea};
	}

	private Label sectionLabel(String text) {
		Label label = new Label(text, kit.labelStyle);
		label.setColor(0.62f, 0.82f, 0.92f, 1f);
		return label;
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

	private TextField indexListField() {
		TextField field = new TextField("", kit.textFieldStyle);
		field.setMaxLength(524288);
		field.setTextFieldFilter((textField, c) -> Character.isDigit(c) || Character.isWhitespace(c)
			|| c == ',' || c == ';');
		return field;
	}

	private void loadState(MeshGeometryState state) {
		refreshing = true;
		try {
			baseline = state;
			pathField.setText(state.path() == null ? "" : state.path());
			colorField.setText(UiColors.format(state.color()));
			widthField.setText(UiNumbers.format(state.width()));
			heightField.setText(UiNumbers.format(state.height()));
			hullField.setText(Integer.toString(state.hullLength() / 2));
			hullOrderField.setText(sequentialVertexOrder(state.hullLength() / 2));
			bonesArea.setText(format(state.bones(), 8));
			verticesArea.setText(format(state.vertices(), state.weighted() ? 6 : 2));
			uvsArea.setText(format(state.regionUVs(), 2));
			trianglesArea.setText(format(state.triangles(), 3));
			edgesArea.setText(format(state.edges(), 2));
			errorLabel.setText("");
		} finally {
			refreshing = false;
		}
		refreshDraftState();
	}

	private MeshGeometryState draftState() {
		Color color = UiColors.parse(colorField.getText());
		String path = pathField.getText() == null ? "" : pathField.getText().trim();
		float width = parseFinite(widthField.getText(), "宽度");
		float height = parseFinite(heightField.getText(), "高度");
		int hullVertices = parseInt(hullField.getText(), "Hull 顶点数");
		if (hullVertices < 0 || hullVertices > 32768)
			throw new IllegalArgumentException("Hull 顶点数必须在 0 到 32768 之间");
		int[] bones = parseIntArray(bonesArea.getText(), "权重骨骼编码", true);
		float[] vertices = parseFloatArray(verticesArea.getText(), "顶点 / 权重数据", false);
		float[] uvs = parseFloatArray(uvsArea.getText(), "UV 坐标", false);
		short[] triangles = parseIndexArray(trianglesArea.getText(), "三角形索引", false);
		short[] edges = parseIndexArray(edgesArea.getText(), "边索引", true);
		return new MeshGeometryState(color, path, bones, vertices, uvs, triangles,
			hullVertices * 2, edges, width, height);
	}

	private void refreshDraftState() {
		try {
			MeshGeometryState draft = draftState();
			applyButton.setDisabled(baseline != null && baseline.sameContent(draft)
				&& baselineLocks.equals(draftLocks));
			summaryLabel.setText(draft.vertexCount() + " 顶点  ·  " + draft.triangles().length / 3
				+ " 三角形  ·  " + (draft.weighted() ? "已加权" : "无权重"));
			normalizeButton.setDisabled(!draft.weighted());
			structuredWeightButton.setDisabled(!draft.weighted());
			smoothButton.setDisabled(!draft.weighted() || draft.triangles().length == 0);
			unbindButton.setDisabled(!draft.weighted());
			bindButton.setDisabled(draft.weighted());
		} catch (RuntimeException ignored) {
			applyButton.setDisabled(false);
			summaryLabel.setText("Mesh 数据待校验");
			String bones = bonesArea.getText();
			boolean weighted = bones != null && !bones.trim().isEmpty();
			normalizeButton.setDisabled(!weighted);
			structuredWeightButton.setDisabled(!weighted);
			smoothButton.setDisabled(true);
			unbindButton.setDisabled(!weighted);
			bindButton.setDisabled(weighted);
		}
	}

	private void bindToSlotBone() {
		try {
			if (bonesArea.getText() != null && !bonesArea.getText().trim().isEmpty())
				throw new IllegalArgumentException("当前 Mesh 已有权重骨骼编码");
			float[] vertices = parseFloatArray(verticesArea.getText(), "顶点数据", false);
			float[] uvs = parseFloatArray(uvsArea.getText(), "UV 坐标", false);
			if (vertices.length != uvs.length)
				throw new IllegalArgumentException("无权重顶点数量必须与 UV 数量一致");
			int boneCount = doc.data == null ? 0 : doc.data.getBones().size;
			MeshWeightDialog.EncodedWeights bound = MeshWeightDialog.bindToBone(vertices, uvs.length / 2,
				slot.getBoneData().getIndex(), boneCount);
			applyEncodedWeights(bound);
			errorLabel.setText("");
			refreshDraftState();
		} catch (RuntimeException error) {
			refreshing = false;
			showError(error.getMessage());
		}
	}

	private void normalizeWeights() {
		try {
			int[] bones = parseIntArray(bonesArea.getText(), "权重骨骼编码", false);
			float[] vertices = parseFloatArray(verticesArea.getText(), "顶点 / 权重数据", false);
			float[] uvs = parseFloatArray(uvsArea.getText(), "UV 坐标", false);
			int boneCount = doc.data == null ? 0 : doc.data.getBones().size;
			MeshWeightDialog.WeightDraft draft = MeshWeightDialog.decode(bones, vertices,
				uvs.length / 2, boneCount, draftLocks);
			draft.normalizeAll();
			applyEncodedWeights(MeshWeightDialog.encode(draft));
			errorLabel.setText("");
			refreshDraftState();
		} catch (RuntimeException error) {
			refreshing = false;
			showError(error.getMessage());
		}
	}

	private void smoothWeights() {
		try {
			MeshGeometryState draft = draftState();
			MeshGeometryState smoothed = SkeletonEditor.smoothMeshWeights(
				draft, smoothSlider.getValue(), 1, draftLocks);
			if (smoothed.sameContent(draft)) {
				if (onStatus != null) onStatus.accept("Mesh 权重已经平滑", false);
				return;
			}
			applyEncodedWeights(new MeshWeightDialog.EncodedWeights(smoothed.bones(), smoothed.vertices(), draftLocks));
			errorLabel.setText("");
			refreshDraftState();
			if (onStatus != null) onStatus.accept("Mesh 权重已按拓扑平滑", false);
		} catch (RuntimeException error) {
			refreshing = false;
			showError(error.getMessage());
		}
	}

	private List<MeshChoice> weightSources() {
		ArrayList<MeshChoice> result = new ArrayList<>();
		IdentityHashMap<MeshAttachment, Boolean> seen = new IdentityHashMap<>();
		if (doc.data == null) return result;
		MeshAttachment targetSource = mesh.getSourceMesh() == null ? mesh : mesh.getSourceMesh();
		for (Skin skin : doc.data.getSkins()) for (Skin.SkinEntry entry : skin.getAttachments()) {
			if (!(entry.getAttachment() instanceof MeshAttachment candidate)) continue;
			MeshAttachment source = candidate.getSourceMesh() == null ? candidate : candidate.getSourceMesh();
			if (source == targetSource || source.getBones() == null || seen.put(source, Boolean.TRUE) != null) continue;
			result.add(new MeshChoice(skin.getName() + " / " + entry.getPlaceholder(), source));
		}
		return result;
	}

	private void showCopyWeights() {
		List<MeshChoice> choices = weightSources();
		if (choices.isEmpty()) { showError("项目中没有其他已加权 Mesh"); return; }
		Actor previousFocus = stage.getKeyboardFocus();
		Window picker = kit.window("从其他 Mesh 复制权重");
		picker.setModal(true);
		kit.padWindow(picker, 12f);
		SelectBox<String> select = kit.selectBox();
		for (MeshChoice choice : choices) select.getItems().add(choice.label());
		TextButton cancel = new TextButton("取消", kit.toolButton);
		TextButton apply = new TextButton("复制", kit.accentButton);
		Runnable closePicker = () -> UiDialogs.closeModal(stage, picker, previousFocus);
		cancel.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { closePicker.run(); }
		});
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				try {
					MeshGeometryState copied = editor.copyMeshWeights(draftState(), slot,
						SkeletonEditor.meshGeometryState(choices.get(select.getSelectedIndex()).mesh()));
					applyEncodedWeights(new MeshWeightDialog.EncodedWeights(copied.bones(), copied.vertices(), Set.of()));
					refreshDraftState();
					closePicker.run();
					errorLabel.setText("");
					if (onStatus != null) onStatus.accept("权重已复制；应用后写入 Mesh", false);
				} catch (RuntimeException error) { showError(error.getMessage()); }
			}
		});
		picker.add(new Label("来源", kit.labelStyle)).right().padRight(7f);
		picker.add(select).width(300f).height(29f).row();
		Table actions = new Table();
		actions.add().growX();
		actions.add(cancel).width(82f).height(28f).padRight(6f);
		actions.add(apply).width(82f).height(28f);
		picker.add(actions).colspan(2).growX().padTop(12f);
		picker.pack();
		picker.setPosition((stage.getWidth() - picker.getWidth()) / 2f,
			(stage.getHeight() - picker.getHeight()) / 2f);
		stage.addActor(picker);
		stage.setKeyboardFocus(select);
	}

	private void weldVertices() {
		try {
			MeshGeometryState draft = draftState();
			if (baseline == null || !baseline.sameContent(draft))
				throw new IllegalArgumentException("请先应用当前 Mesh 编辑，再焊接顶点");
			float tolerance = UiNumbers.parseFinite(weldToleranceField.getText());
			if (tolerance < 0f) throw new IllegalArgumentException("焊接容差不能小于零");
			SkeletonEditor.MeshTopologySnapshot before = editor.snapshotMeshTopology(mesh);
			Set<SkeletonDocument.MeshWeightLock> beforeLocks = draftLocks;
			SkeletonEditor.MeshWeldResult result = editor.weldMeshVerticesWithDeform(mesh, slot, tolerance);
			if (result.weldedVertices() == 0) {
				if (onStatus != null) onStatus.accept("没有发现容差范围内的重合顶点", false);
				return;
			}
			SkeletonEditor.MeshTopologySnapshot after = editor.snapshotMeshTopology(mesh);
			draftLocks = remapLocks(draftLocks, result.oldToNew());
			doc.setMeshWeightLocks(meshWeightStateKey, draftLocks);
			Set<SkeletonDocument.MeshWeightLock> afterLocks = draftLocks;
			if (history != null) history.record("焊接 Mesh 顶点",
				() -> restoreTopology(before, beforeLocks), () -> restoreTopology(after, afterLocks));
			else if (onChanged != null) onChanged.run();
			loadState(result.geometry());
			baselineLocks = draftLocks = afterLocks;
			if (onStatus != null) onStatus.accept("已焊接 " + result.weldedVertices() + " 个重合顶点，并迁移 Deform", false);
		} catch (RuntimeException error) { showError(error.getMessage()); }
	}

	private void restoreTopology(SkeletonEditor.MeshTopologySnapshot snapshot,
		Set<SkeletonDocument.MeshWeightLock> locks) {
		editor.restoreMeshTopology(snapshot);
		doc.setMeshWeightLocks(meshWeightStateKey, locks);
		baselineLocks = draftLocks = locks;
	}

	private void reorderHull() {
		try {
			MeshGeometryState draft = draftState();
			if (baseline == null || !baseline.sameContent(draft))
				throw new IllegalArgumentException("请先应用当前 Mesh 编辑，再调整外壳顺序");
			int[] hullVertices = parseIntArray(hullOrderField.getText(), "外壳顶点顺序", false);
			MeshVertexOrder forward = SkeletonEditor.meshHullVertexOrder(draft.vertexCount(), hullVertices);
			MeshVertexOrder reverse = forward.inverse(draft.hullLength() / 2);
			if (!editor.applyMeshVertexOrder(mesh, forward)) {
				if (onStatus != null) onStatus.accept("Mesh 外壳顺序未变化", false);
				return;
			}
			Set<SkeletonDocument.MeshWeightLock> previousLocks = draftLocks;
			draftLocks = remapLocks(draftLocks, forward.oldToNew());
			doc.setMeshWeightLocks(meshWeightStateKey, draftLocks);
			if (history != null) {
				Set<SkeletonDocument.MeshWeightLock> reorderedLocks = draftLocks;
				history.record("重排 Mesh 外壳", () -> restoreVertexOrder(reverse, previousLocks),
					() -> restoreVertexOrder(forward, reorderedLocks));
			} else if (onChanged != null) {
				onChanged.run();
			}
			loadState(SkeletonEditor.meshGeometryState(mesh));
			baselineLocks = draftLocks;
			if (onStatus != null) onStatus.accept("Mesh 外壳、拓扑和 Deform 已同步重排", false);
		} catch (RuntimeException error) {
			showError(error.getMessage());
		}
	}

	private void autoHull() {
		try {
			MeshGeometryState draft = draftState();
			if (baseline == null || !baseline.sameContent(draft))
				throw new IllegalArgumentException("请先应用当前 Mesh 编辑，再自动计算外壳");
			float[] setupVertices = draft.weighted()
				? editor.unweightMeshVertices(draft.bones(), draft.vertices(), draft.vertexCount(), slot)
				: draft.vertices();
			hullOrderField.setText(inlineIndices(SkeletonEditor.convexMeshHull(setupVertices)));
			reorderHull();
		} catch (RuntimeException error) {
			showError(error.getMessage());
		}
	}

	private void restoreVertexOrder(MeshVertexOrder order, Set<SkeletonDocument.MeshWeightLock> locks) {
		if (!editor.applyMeshVertexOrder(mesh, order))
			throw new IllegalStateException("无法恢复 Mesh 外壳顺序：" + mesh.getName());
		doc.setMeshWeightLocks(meshWeightStateKey, locks);
		baselineLocks = draftLocks = locks;
	}

	private void unbindFromBones() {
		try {
			int[] bones = parseIntArray(bonesArea.getText(), "权重骨骼编码", false);
			float[] vertices = parseFloatArray(verticesArea.getText(), "顶点 / 权重数据", false);
			float[] uvs = parseFloatArray(uvsArea.getText(), "UV 坐标", false);
			float[] unweighted = editor.unweightMeshVertices(bones, vertices, uvs.length / 2, slot);
			refreshing = true;
			try {
				bonesArea.setText("");
				verticesArea.setText(format(unweighted, 2));
				draftLocks = Set.of();
			} finally {
				refreshing = false;
			}
			errorLabel.setText("");
			refreshDraftState();
		} catch (RuntimeException error) {
			refreshing = false;
			showError(error.getMessage());
		}
	}

	private void editStructuredWeights() {
		try {
			if (doc.data == null || doc.data.getBones().isEmpty())
				throw new IllegalArgumentException("项目没有可用骨骼");
			int[] bones = parseIntArray(bonesArea.getText(), "权重骨骼编码", false);
			float[] vertices = parseFloatArray(verticesArea.getText(), "顶点 / 权重数据", false);
			float[] uvs = parseFloatArray(uvsArea.getText(), "UV 坐标", false);
			MeshWeightDialog.WeightDraft draft = MeshWeightDialog.decode(bones, vertices,
				uvs.length / 2, doc.data.getBones().size, draftLocks);
			MeshWeightDialog.show(stage, kit, doc.data.getBones(), slot.getBoneData().getIndex(), draft,
				encoded -> {
					applyEncodedWeights(encoded);
					errorLabel.setText("");
					refreshDraftState();
				}, onStatus);
		} catch (RuntimeException error) {
			showError(error.getMessage());
		}
	}

	private void applyEncodedWeights(MeshWeightDialog.EncodedWeights encoded) {
		refreshing = true;
		try {
			bonesArea.setText(format(encoded.bones(), 8));
			verticesArea.setText(format(encoded.vertices(), 6));
			draftLocks = encoded.locks();
		} finally {
			refreshing = false;
		}
	}

	private void submit() {
		MeshGeometryState draft;
		try {
			draft = draftState();
		} catch (RuntimeException error) {
			showError(error.getMessage());
			return;
		}
		String reason = editor.validateMeshGeometry(mesh, draft);
		if (reason != null) {
			showError(reason);
			return;
		}
		draftLocks = validLocks(draft, draftLocks);
		if (baseline.sameContent(draft) && baselineLocks.equals(draftLocks)) {
			applyButton.setDisabled(true);
			return;
		}
		MeshGeometryState before = SkeletonEditor.meshGeometryState(mesh);
		Set<SkeletonDocument.MeshWeightLock> beforeLocks = baselineLocks;
		boolean geometryChanged = !baseline.sameContent(draft);
		if (geometryChanged && !editor.updateMeshGeometry(mesh, draft)) {
			showError("Mesh 数据状态已变化，请重新打开编辑器");
			return;
		}
		MeshGeometryState after = SkeletonEditor.meshGeometryState(mesh);
		doc.setMeshWeightLocks(meshWeightStateKey, draftLocks);
		Set<SkeletonDocument.MeshWeightLock> afterLocks = draftLocks;
		if (history != null) history.record("编辑 Mesh 几何",
			() -> restore(before, beforeLocks), () -> restore(after, afterLocks));
		else if (onChanged != null) onChanged.run();
		loadState(after);
		baselineLocks = draftLocks = afterLocks;
		if (onStatus != null) onStatus.accept("Mesh 几何与权重已应用", false);
	}

	private void restore(MeshGeometryState state, Set<SkeletonDocument.MeshWeightLock> locks) {
		if (!SkeletonEditor.meshGeometryState(mesh).sameContent(state) && !editor.updateMeshGeometry(mesh, state))
			throw new IllegalStateException("无法恢复 Mesh 几何：" + mesh.getName());
		doc.setMeshWeightLocks(meshWeightStateKey, locks);
	}

	private static Set<SkeletonDocument.MeshWeightLock> remapLocks(
		Set<SkeletonDocument.MeshWeightLock> locks, int[] oldToNew) {
		LinkedHashSet<SkeletonDocument.MeshWeightLock> remapped = new LinkedHashSet<>();
		for (SkeletonDocument.MeshWeightLock lock : locks)
			if (lock.vertex() < oldToNew.length)
				remapped.add(new SkeletonDocument.MeshWeightLock(oldToNew[lock.vertex()], lock.boneIndex()));
		return Set.copyOf(remapped);
	}

	private static Set<SkeletonDocument.MeshWeightLock> validLocks(MeshGeometryState state,
		Set<SkeletonDocument.MeshWeightLock> locks) {
		if (state.bones() == null || locks.isEmpty()) return Set.of();
		LinkedHashSet<SkeletonDocument.MeshWeightLock> valid = new LinkedHashSet<>();
		int[] bones = state.bones();
		for (int vertex = 0, cursor = 0; vertex < state.vertexCount() && cursor < bones.length; vertex++) {
			int count = bones[cursor++];
			for (int influence = 0; influence < count && cursor < bones.length; influence++) {
				int bone = bones[cursor++];
				SkeletonDocument.MeshWeightLock lock = new SkeletonDocument.MeshWeightLock(vertex, bone);
				if (locks.contains(lock)) valid.add(lock);
			}
		}
		return Set.copyOf(valid);
	}

	private void showError(String message) {
		errorLabel.setText(message == null ? "Mesh 数据无效" : message);
		if (onStatus != null) onStatus.accept(errorLabel.getText().toString(), true);
	}

	private void close() {
		if (closed) return;
		closed = true;
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
	}

	static float[] parseFloatArray(String text, String label, boolean optional) {
		String[] tokens = tokens(text);
		if (tokens.length == 0) {
			if (optional) return new float[0];
			throw new IllegalArgumentException(label + "不能为空");
		}
		float[] values = new float[tokens.length];
		for (int index = 0; index < tokens.length; index++) {
			try {
				values[index] = Float.parseFloat(tokens[index]);
			} catch (NumberFormatException error) {
				throw new IllegalArgumentException(label + "包含无效数值：" + tokens[index], error);
			}
			if (!Float.isFinite(values[index])) throw new IllegalArgumentException(label + "必须是有限数值");
		}
		return values;
	}

	static int[] parseIntArray(String text, String label, boolean optional) {
		String[] tokens = tokens(text);
		if (tokens.length == 0) {
			if (optional) return null;
			throw new IllegalArgumentException(label + "不能为空");
		}
		int[] values = new int[tokens.length];
		for (int index = 0; index < tokens.length; index++) values[index] = parseInt(tokens[index], label);
		return values;
	}

	static short[] parseIndexArray(String text, String label, boolean optional) {
		String[] tokens = tokens(text);
		if (tokens.length == 0) {
			if (optional) return null;
			throw new IllegalArgumentException(label + "不能为空");
		}
		short[] values = new short[tokens.length];
		for (int index = 0; index < tokens.length; index++) {
			int value = parseInt(tokens[index], label);
			if (value < 0 || value > 65535) throw new IllegalArgumentException(label + "必须在 0 到 65535 之间");
			values[index] = (short)value;
		}
		return values;
	}

	private static float parseFinite(String text, String label) {
		try {
			return UiNumbers.parseFinite(text);
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(label + "必须是有限数值", error);
		}
	}

	private static int parseInt(String text, String label) {
		try {
			return Integer.parseInt(text == null ? "" : text.trim());
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(label + "必须是整数", error);
		}
	}

	private static String[] tokens(String text) {
		if (text == null || text.trim().isEmpty()) return new String[0];
		return Arrays.stream(text.trim().split("[\\s,;]+"))
			.filter(token -> !token.isEmpty()).toArray(String[]::new);
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

	private static String format(short[] values, int group) {
		if (values == null || values.length == 0) return "";
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < values.length; index++) {
			if (index > 0) result.append(index % group == 0 ? '\n' : ", ");
			result.append(Short.toUnsignedInt(values[index]));
		}
		return result.toString();
	}

	private static String sequentialVertexOrder(int count) {
		int[] indices = new int[count];
		for (int vertex = 0; vertex < count; vertex++) indices[vertex] = vertex;
		return inlineIndices(indices);
	}

	private static String inlineIndices(int[] indices) {
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < indices.length; index++) {
			if (index > 0) result.append(", ");
			result.append(indices[index]);
		}
		return result.toString();
	}
}
