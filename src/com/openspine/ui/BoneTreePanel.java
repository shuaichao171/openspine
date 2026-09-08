package com.openspine.ui;

import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Tree;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.IkConstraintData;
import com.esotericsoftware.spine.PathConstraintData;
import com.esotericsoftware.spine.PhysicsConstraintData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.SliderData;
import com.esotericsoftware.spine.TransformConstraintData;
import com.esotericsoftware.spine.attachments.Attachment;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;
import com.openspine.model.EditHistory;

/**
 * 左侧层级树：按骨骼、槽、附件关系展示骨架内容，点击选中并同步到文档。
 * 骨骼支持右键编辑菜单；可见性操作遵循 Spine 的骨骼与槽语义。
 */
public class BoneTreePanel extends Table {
	static final float VISIBILITY_COLUMN_WIDTH = 26f;
	private static final float TREE_LINE_THICKNESS = 1f;
	private static final float TREE_WHEEL_STEP = 66f;
	enum WheelAxis { VERTICAL, HORIZONTAL }
	private final HierarchyTree tree;
	private final SkeletonDocument doc;
	private final SkeletonEditor editor;
	private final Label.LabelStyle nodeLabelStyle;
	private final UiKit kit;
	private final TextField filterField;
	private final ScrollPane treeScroll;
	private final Array<String> savedExpandedValues = new Array<>();
	private final Set<String> savedTreeValues = new HashSet<>();
	private final IdentityHashMap<Bone, BoneNode> nodesByBone = new IdentityHashMap<>();
	private final IdentityHashMap<Slot, BoneNode> nodesBySlot = new IdentityHashMap<>();
	private final IdentityHashMap<ConstraintData<?, ?>, BoneNode> nodesByConstraint = new IdentityHashMap<>();
	private final HashMap<String, BoneNode> nodesByConstraintRelation = new HashMap<>();
	private ConstraintData<?, ?> selectedConstraint;
	private boolean syncing;
	private boolean lastBuildFiltered;
	private SkeletonData expansionDocument;
	private Skeleton treeSkeleton;
	private EditHistory history;
	private java.util.function.BiConsumer<String, Boolean> onStatus;

	/** 数据/结构变更后由外部 UI 刷新（重建树、属性、视口） */
	private Runnable onDataChanged;
	private Runnable onReversibleDataChanged;
	/** 选择变更时由外部 UI 刷新属性（可空） */
	private Runnable onSelectionChanged;

	enum KeyAction { NONE, PREVIOUS, NEXT, COLLAPSE_OR_PARENT, EXPAND_OR_CHILD, RENAME, DELETE, CLEAR_FILTER }
	enum SearchKeyAction { NONE, CLEAR_FILTER, FOCUS_TREE, SELECT_NEXT }

	static KeyAction keyAction(int keycode) {
		if (keycode == Input.Keys.UP) return KeyAction.PREVIOUS;
		if (keycode == Input.Keys.DOWN) return KeyAction.NEXT;
		if (keycode == Input.Keys.LEFT) return KeyAction.COLLAPSE_OR_PARENT;
		if (keycode == Input.Keys.RIGHT) return KeyAction.EXPAND_OR_CHILD;
		if (keycode == Input.Keys.F2) return KeyAction.RENAME;
		if (keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) return KeyAction.DELETE;
		return keycode == Input.Keys.ESCAPE ? KeyAction.CLEAR_FILTER : KeyAction.NONE;
	}

	static SearchKeyAction searchKeyAction(int keycode, boolean hasFilter) {
		if (keycode == Input.Keys.ESCAPE)
			return hasFilter ? SearchKeyAction.CLEAR_FILTER : SearchKeyAction.FOCUS_TREE;
		if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER)
			return SearchKeyAction.FOCUS_TREE;
		return keycode == Input.Keys.DOWN ? SearchKeyAction.SELECT_NEXT : SearchKeyAction.NONE;
	}

	static int moveSelection(int current, int delta, int count) {
		if (count <= 0) return -1;
		if (current < 0 || current >= count) return delta < 0 ? count - 1 : 0;
		return Math.max(0, Math.min(count - 1, current + delta));
	}

	static boolean shouldExpandNode(boolean filtering, boolean hasSavedTree, boolean knownNode) {
		return filtering || !hasSavedTree || !knownNode;
	}

	static boolean isVisibilityColumnHit(float x) {
		return Float.isFinite(x) && x >= 0f && x < VISIBILITY_COLUMN_WIDTH;
	}

	static boolean setBoneVisible(SkeletonDocument doc, EditHistory history, BoneData data, boolean visible) {
		if (doc == null || data == null || data.getVisible() == visible) return false;
		boolean previous = data.getVisible();
		data.setVisible(visible);
		if (history == null) {
			doc.markDirty();
		} else {
			String label = visible ? "显示骨骼" : "隐藏骨骼";
			history.record(label, () -> data.setVisible(previous), () -> data.setVisible(visible));
		}
		return true;
	}

	static boolean setSlotVisible(SkeletonDocument doc, EditHistory history, SlotData data, boolean visible) {
		if (doc == null || data == null || data.getVisible() == visible) return false;
		boolean previous = data.getVisible();
		data.setVisible(visible);
		if (history == null) {
			doc.markDirty();
		} else {
			String label = visible ? "显示槽" : "隐藏槽";
			history.record(label, () -> data.setVisible(previous), () -> data.setVisible(visible));
		}
		return true;
	}

	static boolean setBoneSubtreeVisible(SkeletonDocument doc, EditHistory history, SkeletonData skeletonData,
		BoneData root, boolean visible) {
		if (doc == null || skeletonData == null || root == null || !skeletonData.getBones().contains(root, true))
			return false;
		Array<BoneData> bones = new Array<>();
		for (BoneData bone : skeletonData.getBones())
			if (isDescendantOrSelf(bone, root)) bones.add(bone);
		Array<SlotData> slots = new Array<>();
		for (SlotData slot : skeletonData.getSlots())
			if (bones.contains(slot.getBoneData(), true)) slots.add(slot);
		return setVisibility(doc, history, bones, slots, visible,
			visible ? "显示骨骼子树" : "隐藏骨骼子树");
	}

	static boolean setAllVisible(SkeletonDocument doc, EditHistory history, SkeletonData skeletonData) {
		if (doc == null || skeletonData == null) return false;
		return setVisibility(doc, history, new Array<>(skeletonData.getBones()),
			new Array<>(skeletonData.getSlots()), true, "显示全部骨骼和槽");
	}

	private static boolean setVisibility(SkeletonDocument doc, EditHistory history, Array<BoneData> bones,
		Array<SlotData> slots, boolean visible, String label) {
		boolean[] previousBones = new boolean[bones.size];
		boolean[] previousSlots = new boolean[slots.size];
		boolean changed = false;
		for (int index = 0; index < bones.size; index++) {
			BoneData bone = bones.get(index);
			previousBones[index] = bone.getVisible();
			changed |= bone.getVisible() != visible;
		}
		for (int index = 0; index < slots.size; index++) {
			SlotData slot = slots.get(index);
			previousSlots[index] = slot.getVisible();
			changed |= slot.getVisible() != visible;
		}
		if (!changed) return false;
		applyVisibility(bones, slots, visible);
		if (history == null) {
			doc.markDirty();
		} else {
			history.record(label, () -> restoreVisibility(bones, slots, previousBones, previousSlots),
				() -> applyVisibility(bones, slots, visible));
		}
		return true;
	}

	private static boolean isDescendantOrSelf(BoneData bone, BoneData ancestor) {
		for (BoneData current = bone; current != null; current = current.getParent())
			if (current == ancestor) return true;
		return false;
	}

	private static void applyVisibility(Array<BoneData> bones, Array<SlotData> slots, boolean visible) {
		for (BoneData bone : bones) bone.setVisible(visible);
		for (SlotData slot : slots) slot.setVisible(visible);
	}

	private static void restoreVisibility(Array<BoneData> bones, Array<SlotData> slots,
		boolean[] boneVisibility, boolean[] slotVisibility) {
		for (int index = 0; index < bones.size; index++) bones.get(index).setVisible(boneVisibility[index]);
		for (int index = 0; index < slots.size; index++) slots.get(index).setVisible(slotVisibility[index]);
	}

	/** 骨骼树节点（gdx 1.14.2 的 Node 有三泛型参数 N,V,A） */
	public static class BoneNode extends Tree.Node<BoneNode, String, Actor> {
		public final Bone bone;
		public final Slot slot;
		public final Attachment attachment;
		public final String attachmentPlaceholder;
		public final ConstraintData<?, ?> constraint;
		public final String constraintRelation;
		public final String constraintRelationLabel;
		public final boolean constraintGroup;
		private final Bone constraintBone;
		final String searchText;

		public BoneNode(Bone bone, Actor actor) {
			super(actor);
			this.bone = bone;
			this.slot = null;
			this.attachment = null;
			this.attachmentPlaceholder = null;
			this.constraint = null;
			this.constraintRelation = null;
			this.constraintRelationLabel = null;
			this.constraintGroup = false;
			this.constraintBone = null;
			this.searchText = bone.getData().getName();
		}

		public BoneNode(Slot slot, Actor actor) {
			super(actor);
			this.bone = null;
			this.slot = slot;
			this.attachment = null;
			this.attachmentPlaceholder = null;
			this.constraint = null;
			this.constraintRelation = null;
			this.constraintRelationLabel = null;
			this.constraintGroup = false;
			this.constraintBone = null;
			this.searchText = slot.getData().getName();
		}

		public BoneNode(Slot slot, Attachment attachment, String placeholder, Actor actor) {
			super(actor);
			this.bone = null;
			this.slot = slot;
			this.attachment = attachment;
			this.attachmentPlaceholder = placeholder;
			this.constraint = null;
			this.constraintRelation = null;
			this.constraintRelationLabel = null;
			this.constraintGroup = false;
			this.constraintBone = null;
			this.searchText = placeholder + " " + attachment.getName();
		}

		public BoneNode(ConstraintData<?, ?> constraint, Bone bone, String type, Actor actor) {
			this(constraint, bone, type, null, null, actor);
		}

		public BoneNode(ConstraintData<?, ?> constraint, Bone bone, String type,
			String relation, String relationLabel, Actor actor) {
			super(actor);
			this.bone = null;
			this.slot = null;
			this.attachment = null;
			this.attachmentPlaceholder = null;
			this.constraint = constraint;
			this.constraintRelation = relation;
			this.constraintRelationLabel = relationLabel;
			this.constraintGroup = false;
			this.constraintBone = bone;
			this.searchText = constraint.getName() + " " + type + " 约束 "
				+ (relationLabel == null ? "" : relationLabel);
		}

		public BoneNode(String groupLabel, Actor actor) {
			super(actor);
			this.bone = null;
			this.slot = null;
			this.attachment = null;
			this.attachmentPlaceholder = null;
			this.constraint = null;
			this.constraintRelation = null;
			this.constraintRelationLabel = null;
			this.constraintGroup = true;
			this.constraintBone = null;
			this.searchText = groupLabel;
		}

		Bone owningBone() {
			return bone != null ? bone : slot != null ? slot.getBone() : constraintBone;
		}

		boolean hasVisibilityControl() {
			return bone != null || slot != null && attachment == null;
		}

		boolean isVisible() {
			if (bone != null) return bone.getData().getVisible();
			return slot == null || slot.getData().getVisible();
		}
	}

	private final class HierarchyTree extends Tree<BoneNode, String> {
		HierarchyTree() {
			super(kit.treeStyle);
			setPadding(VISIBILITY_COLUMN_WIDTH, 0f);
		}

		@Override
		public void draw(Batch batch, float parentAlpha) {
			updateNodeColors(getRootNodes());
			super.draw(batch, parentAlpha);
		}

		@Override
		protected float drawIcons(Batch batch, float r, float g, float b, float a, BoneNode parent,
			Array<BoneNode> nodes, float indent, float plusMinusWidth) {
			if (parent != null) return super.drawIcons(batch, r, g, b, a, parent, nodes, indent, plusMinusWidth);
			drawHierarchyLines(batch, nodes, a);
			float result = super.drawIcons(batch, r, g, b, a, parent, nodes, indent, plusMinusWidth);
			drawVisibilityIcons(batch, nodes, r, g, b, a);
			return result;
		}

		private void updateNodeColors(Array<BoneNode> nodes) {
			for (BoneNode node : nodes) {
				if (node.getActor() instanceof Label label) {
					if (node.isVisible() && node.attachment != null) label.setColor(0.68f, 0.72f, 0.78f, 1f);
					else if (node.isVisible() && node.slot != null) label.setColor(0.62f, 0.82f, 0.88f, 1f);
					else if (node.isVisible()) label.setColor(Color.WHITE);
					else if (getSelection().contains(node)) label.setColor(0.80f, 0.84f, 0.90f, 1f);
					else label.setColor(0.55f, 0.58f, 0.64f, 1f);
				}
				if (node.isExpanded()) updateNodeColors(node.getChildren());
			}
		}

		private void drawHierarchyLines(Batch batch, Array<BoneNode> nodes, float alpha) {
			batch.setColor(0.34f, 0.38f, 0.43f, alpha * 0.72f);
			for (BoneNode node : nodes) drawHierarchyLines(batch, node);
			batch.setColor(getColor().r, getColor().g, getColor().b, alpha);
		}

		private void drawHierarchyLines(Batch batch, BoneNode parent) {
			if (!parent.isExpanded() || parent.getChildren().isEmpty()) return;
			Actor parentActor = parent.getActor();
			Array<BoneNode> children = parent.getChildren();
			Actor lastActor = children.peek().getActor();
			if (parentActor == null || lastActor == null) return;
			float treeX = getX();
			float treeY = getY();
			float branchX = Math.round(treeX + parentActor.getX() - getIndentSpacing() * 0.5f);
			float parentY = Math.round(treeY + parentActor.getY() + parent.getHeight() * 0.5f);
			float lastY = Math.round(treeY + lastActor.getY() + children.peek().getHeight() * 0.5f);
			kit.white.draw(batch, branchX, lastY, TREE_LINE_THICKNESS,
				Math.max(TREE_LINE_THICKNESS, parentY - lastY));
			for (BoneNode child : children) {
				Actor childActor = child.getActor();
				if (childActor == null) continue;
				float childY = Math.round(treeY + childActor.getY() + child.getHeight() * 0.5f);
				float endX = Math.round(treeX + childActor.getX() - 2f);
				kit.white.draw(batch, branchX, childY, Math.max(TREE_LINE_THICKNESS, endX - branchX),
					TREE_LINE_THICKNESS);
				drawHierarchyLines(batch, child);
			}
		}

		private void drawVisibilityIcons(Batch batch, Array<BoneNode> nodes,
			float r, float g, float b, float alpha) {
			float iconSize = UiKit.TREE_VISIBILITY_ICON_SIZE;
			float iconX = Math.round(getX() + (VISIBILITY_COLUMN_WIDTH - iconSize) * 0.5f);
			for (BoneNode node : nodes) {
				Actor actor = node.getActor();
				if (actor != null && node.hasVisibilityControl()) {
					boolean visible = node.isVisible();
					float tint = visible ? 0.88f : 0.50f;
					batch.setColor(r * tint, g * tint, b * tint, alpha);
					float iconY = Math.round(getY() + actor.getY() + (node.getHeight() - iconSize) * 0.5f);
					(visible ? kit.visibleEyeIcon : kit.hiddenEyeIcon).draw(batch, iconX, iconY, iconSize, iconSize);
				}
				if (node.isExpanded()) drawVisibilityIcons(batch, node.getChildren(), r, g, b, alpha);
			}
			batch.setColor(r, g, b, alpha);
		}
	}

	public BoneTreePanel(SkeletonDocument doc, UiKit kit, SkeletonEditor editor) {
		this.doc = doc;
		this.kit = kit;
		this.editor = editor;
		setBackground(kit.panelBg);
		this.tree = new HierarchyTree();
		this.nodeLabelStyle = kit.labelStyle;

		Label title = kit.controlLabel("骨架层级");
		title.setColor(0.75f, 0.8f, 0.9f, 1f);
		filterField = new TextField("", kit.textFieldStyle);
		filterField.setMessageText("搜索骨架");
		filterField.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { if (!syncing) rebuild(); }
		});
		filterField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (searchKeyAction(keycode, !filterField.getText().isEmpty())) {
				case CLEAR_FILTER -> { filterField.setText(""); yield true; }
				case FOCUS_TREE -> { focusTree(false); yield true; }
				case SELECT_NEXT -> { focusTree(true); yield true; }
				case NONE -> false;
				};
			}
		});

		treeScroll = new ScrollPane(tree, kit.scrollStyle);
		treeScroll.setFadeScrollBars(false);
		treeScroll.setScrollingDisabled(false, false);
		treeScroll.setForceScroll(false, true);
		treeScroll.setOverscroll(false, false);
		treeScroll.addCaptureListener(new InputListener() {
			@Override public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
				if (!handleTreeWheel(amountX, amountY)) return false;
				event.stop();
				return true;
			}
		});

		Table header = new Table();
		header.add(title).left().padRight(6f);
		header.add(filterField).growX().minWidth(0f).height(26f);
		add(header).growX().minWidth(0f).pad(0f, 5f, 3f, 6f);
		row();
		add(treeScroll).grow().minWidth(0f).minHeight(0f);

		tree.getSelection().setMultiple(true);
		tree.getSelection().setRequired(false);
		tree.addCaptureListener(new InputListener() {
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				if ((button != Input.Buttons.LEFT && button != Input.Buttons.RIGHT)
					|| !isVisibilityColumnHit(x)) return false;
				BoneNode node = locateNode(y);
				if (node == null || !node.hasVisibilityControl()) return false;
				event.stop();
				if (getStage() != null) getStage().setKeyboardFocus(tree);
				toggleVisibility(node, button == Input.Buttons.RIGHT);
				return true;
			}
		});
		tree.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (syncing || tree.getSelection().isEmpty()) return;
				BoneNode node = tree.getSelection().first();
				if (tree.getSelection().size() > 1) {
					com.badlogic.gdx.utils.Array<Bone> bones = new com.badlogic.gdx.utils.Array<>();
					boolean onlyBones = true;
					for (BoneNode selected : tree.getSelection()) {
						if (selected == null || selected.bone == null) { onlyBones = false; break; }
						bones.add(selected.bone);
					}
					if (onlyBones && bones.notEmpty()) {
						selectedConstraint = null;
						doc.setSelectedBones(bones);
						if (onSelectionChanged != null) onSelectionChanged.run();
					}
				} else if (node != null && node.owningBone() != null) {
					selectedConstraint = node.constraint;
					applyDocumentSelection(node);
					if (onSelectionChanged != null) onSelectionChanged.run();
				}
			}
		});

		// 鼠标选择和右键菜单；键盘导航在树获得焦点后生效。
		tree.addListener(new InputListener() {
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				if (button == Input.Buttons.LEFT && getStage() != null) {
					getStage().setKeyboardFocus(tree);
					return false;
				}
				if (button == Input.Buttons.RIGHT) {
					BoneNode node = locateNode(y);
					if (node != null && node.bone != null) {
						showContextMenu(event, node);
						return true;
					}
				}
				return false;
			}

			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleTreeKey(keyAction(keycode));
			}
		});
	}

	static WheelAxis wheelAxis(boolean shift) {
		return shift ? WheelAxis.HORIZONTAL : WheelAxis.VERTICAL;
	}

	static float wheelAmount(float amountX, float amountY) {
		return Math.abs(amountY) > 0.0001f ? amountY : amountX;
	}

	private boolean handleTreeWheel(float amountX, float amountY) {
		float amount = wheelAmount(amountX, amountY);
		if (Math.abs(amount) <= 0.0001f) return false;
		if (wheelAxis(shiftPressed()) == WheelAxis.HORIZONTAL)
			treeScroll.setScrollX(treeScroll.getScrollX() + amount * TREE_WHEEL_STEP);
		else
			treeScroll.setScrollY(treeScroll.getScrollY() + amount * TREE_WHEEL_STEP);
		treeScroll.updateVisualScroll();
		return true;
	}

	private static boolean shiftPressed() {
		return Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
			|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
	}

	private void toggleVisibility(BoneNode node, boolean includeChildren) {
		if (node.bone != null) {
			BoneData data = node.bone.getData();
			boolean visible = !data.getVisible();
			boolean changed = includeChildren
				? setBoneSubtreeVisible(doc, history, doc.data, data, visible)
				: setBoneVisible(doc, history, data, visible);
			if (changed) showStatus(data.getName() + (visible ? " 已显示" : " 已隐藏")
				+ (includeChildren ? "（含子骨骼和槽）" : ""), false);
			return;
		}
		if (node.slot != null) {
			SlotData data = node.slot.getData();
			boolean visible = !data.getVisible();
			if (setSlotVisible(doc, history, data, visible))
				showStatus(data.getName() + (visible ? " 槽已显示" : " 槽已隐藏"), false);
		}
	}

	private boolean handleTreeKey(KeyAction action) {
		BoneNode current = currentNode();
		switch (action) {
		case PREVIOUS: selectRelative(-1); return true;
		case NEXT: selectRelative(1); return true;
		case COLLAPSE_OR_PARENT:
			if (current == null) return false;
			if (current.isExpanded() && current.hasChildren()) current.setExpanded(false);
			else if (current.getParent() != null) selectTreeNode(current.getParent());
			return true;
		case EXPAND_OR_CHILD:
			if (current == null) return false;
			if (current.hasChildren() && !current.isExpanded()) current.setExpanded(true);
			else if (current.isExpanded() && current.hasChildren()) selectTreeNode(current.getChildren().first());
			return true;
		case RENAME:
			if (current == null || current.bone == null) return false;
			showRenameDialog(current.bone.getData());
			return true;
		case DELETE:
			if (current == null || current.bone == null) return false;
			showDeleteDialog(current.bone.getData());
			return true;
		case CLEAR_FILTER:
			if (filterField.getText().isEmpty()) return false;
			filterField.setText("");
			return true;
		case NONE:
		default:
			return false;
		}
	}

	private BoneNode currentNode() {
		return tree.getSelection().isEmpty() ? null : tree.getSelection().first();
	}

	private void selectRelative(int delta) {
		Array<BoneNode> visible = new Array<>();
		collectVisibleNodes(tree.getRootNodes(), visible);
		int current = visible.indexOf(currentNode(), true);
		int next = moveSelection(current, delta, visible.size);
		if (next >= 0) selectTreeNode(visible.get(next));
	}

	private void focusTree(boolean selectNext) {
		if (getStage() != null) getStage().setKeyboardFocus(tree);
		BoneNode current = currentNode();
		if (selectNext || current == null) selectRelative(1);
		else scrollNodeIntoView(current);
	}

	private void collectVisibleNodes(Array<BoneNode> nodes, Array<BoneNode> out) {
		for (BoneNode node : nodes) {
			out.add(node);
			if (node.isExpanded()) collectVisibleNodes(node.getChildren(), out);
		}
	}

	private void selectTreeNode(BoneNode node) {
		if (node == null || node.owningBone() == null) return;
		syncing = true;
		tree.getSelection().set(node);
		syncing = false;
		selectedConstraint = node.constraint;
		applyDocumentSelection(node);
		scrollNodeIntoView(node);
		fireSelectionChanged();
	}

	private void scrollNodeIntoView(BoneNode node) {
		Stage expectedStage = getStage();
		if (node == null || node.getActor() == null || expectedStage == null) return;
		Runnable scrollToNode = () -> {
			Actor actor = node.getActor();
			if (actor == null || actor.getStage() != expectedStage || getStage() != expectedStage) return;
			treeScroll.layout();
			tree.layout();
			treeScroll.scrollTo(0f, actor.getY(), treeScroll.getWidth(), actor.getHeight(), false, true);
			treeScroll.updateVisualScroll();
		};
		if (Gdx.app == null) scrollToNode.run();
		else Gdx.app.postRunnable(scrollToNode);
	}

	/** 把事件坐标转 tree 本地并取节点 */
	private BoneNode locateNode(float y) {
		BoneNode node = tree.getNodeAt(y);
		if (node == null) {
			// 兜底：手动遍历
			node = findNodeAt(tree.getRootNodes(), y);
		}
		return node;
	}

	private BoneNode findNodeAt(Array<?> nodes, float y) {
		for (Object o : nodes) {
			BoneNode node = (BoneNode) o;
			if (node.getActor() != null) {
				float ny = node.getActor().getY();
				float h = node.getActor().getHeight();
				if (y >= ny - h && y <= ny + h) return node;
			}
			if (node.isExpanded() && !node.getChildren().isEmpty()) {
				BoneNode child = findNodeAt(node.getChildren(), y);
				if (child != null) return child;
			}
		}
		return null;
	}

	/** 右键菜单 */
	private void showContextMenu(InputEvent event, BoneNode node) {
		Stage stage = getStage();
		if (stage == null || node.bone == null) return;
		doc.setSelectedBone(node.bone);
		if (onSelectionChanged != null) onSelectionChanged.run();
		syncSelection();

		BoneData data = node.bone.getData();
		final float sx = event.getStageX();
		final float sy = event.getStageY();

		PopupMenu menu = new PopupMenu(kit, sx, sy, stage);
		menu.addItem("新建子骨骼").setOnClick(() -> {
			BoneData created = editor.addBone(data.getName() + ".bone", data);
			if (created != null) {
				recordAddedBone(created.getName(), data.getName());
				fireDataChanged(true);
			} else {
				showStatus("无法新建子骨骼", true);
			}
		});
		menu.addItem("重命名…").setOnClick(() -> showRenameDialog(data));
		menu.addSeparator();
		menu.addItem("删除骨骼…").setOnClick(() -> showDeleteDialog(data));
		menu.addItem("重置姿势").setOnClick(() -> {
			if (history != null) history.begin("重置骨骼姿态");
			boolean changed = resetBonePose(node.bone);
			if (history != null) history.commit();
			if (changed) fireSelectionChanged();
			else showStatus("骨骼姿态未变化", false);
		});
		menu.addSeparator();
		menu.addItem("复制骨骼属性").setOnClick(() -> {
			editor.copyBone(data);
			showStatus("已复制 " + data.getName() + " 的骨骼属性", false);
		});
		if (editor.hasClipboard()) {
			menu.addItem("粘贴属性到此骨骼").setOnClick(() -> {
				if (editor.pasteBoneOnto(data)) {
					fireDataChanged();
					showStatus("骨骼属性已粘贴", false);
				} else {
					showStatus("无法粘贴骨骼属性", true);
				}
			});
		}
		menu.addSeparator();
		menu.addItem("复制骨骼子树").setOnClick(() -> {
			int count = editor.copyBoneSubtree(data);
			if (count > 0) showStatus("已复制 " + count + " 个骨骼", false);
			else showStatus("无法复制骨骼子树", true);
		});
		if (editor.hasSubtreeClipboard()) {
			menu.addItem("粘贴为子骨骼").setOnClick(() -> {
				BoneData pastedRoot = editor.pasteBoneSubtree(data);
				if (pastedRoot != null) {
					int count = editor.getSubtreeClipboardSize();
					fireDataChanged();
					showStatus("已粘贴 " + count + " 个骨骼，新根为 " + pastedRoot.getName(), false);
				} else {
					showStatus("无法粘贴骨骼子树", true);
				}
			});
		}
	}

	private boolean resetBonePose(Bone bone) {
		if (bone == null) return false;
		var setup = bone.getData().getSetupPose();
		return doc.updateBonePose(bone, setup.getX(), setup.getY(), setup.getRotation(),
			setup.getScaleX(), setup.getScaleY(), setup.getShearX(), setup.getShearY());
	}

	private void showDeleteDialog(BoneData data) {
		Stage stage = getStage();
		if (stage == null) return;
		String blockReason = editor.getDeleteBlockReason(data);
		if (blockReason != null) {
			showStatus("无法删除：" + blockReason, true);
			return;
		}
		UiDialogs.confirm(stage, kit, "删除骨骼",
			"删除 " + data.getName() + " 及其子骨骼？", "删除", () -> {
				var before = com.openspine.model.SkeletonStructureSnapshot.capture(editor);
				if (editor.deleteBone(data)) {
					if (history != null) {
						var after = com.openspine.model.SkeletonStructureSnapshot.capture(editor);
						history.record("删除骨骼", before::restore, after::restore);
					}
					fireDataChanged(true);
				} else if (onStatus != null) {
					String reason = editor.getDeleteBlockReason(data);
					showStatus("无法删除：" + (reason == null ? "项目数据已发生变化" : reason), true);
				}
			});
	}

	/** 重命名对话框 */
	private void showRenameDialog(BoneData data) {
		Stage stage = getStage();
		if (stage == null) return;
		UiDialogs.promptName(stage, kit, "重命名骨骼", data.getName(), 64, name -> {
			String oldName = data.getName();
			String renamed = editor.renameBone(data, name);
			if (renamed == null) {
				showStatus("无法重命名骨骼", true);
				return false;
			}
			if (oldName.equals(renamed)) {
				showStatus("骨骼名称未变化", false);
				return true;
			}
			recordRenamedBone(oldName, renamed);
			fireDataChanged(true);
			return true;
		});
	}

	// ================= 外部挂钩 =================

	public void setOnDataChanged(Runnable r) { this.onDataChanged = r; }
	public void setOnReversibleDataChanged(Runnable r) { this.onReversibleDataChanged = r; }
	public void setOnSelectionChanged(Runnable r) { this.onSelectionChanged = r; }
	public void setHistory(EditHistory history) { this.history = history; }
	public void setOnStatus(java.util.function.BiConsumer<String, Boolean> listener) { this.onStatus = listener; }

	private void showStatus(String message, boolean error) {
		if (onStatus != null) onStatus.accept(message, error);
	}

	private void fireDataChanged() {
		if (onDataChanged != null) onDataChanged.run();
		else if (history != null) history.clear();
	}

	private void fireDataChanged(boolean reversible) {
		if (reversible && onReversibleDataChanged != null) onReversibleDataChanged.run();
		else fireDataChanged();
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

	private void fireSelectionChanged() {
		if (onSelectionChanged != null) onSelectionChanged.run();
	}

	private void applyDocumentSelection(BoneNode node) {
		if (node.constraint != null) {
			if (node.constraintRelation == null) doc.setConstraintSelection(node.constraint);
			else {
				Bone relationBone = node.owningBone();
				doc.setConstraintRelationSelection(node.constraint, node.constraintRelation,
					relationBone == null ? "" : relationBone.getData().getName());
			}
		} else if (node.slot != null) {
			doc.setSlotSelection(node.slot, node.attachment == null ? "" : node.attachmentPlaceholder,
				node.attachment == null ? "" : node.attachment.getName());
		} else {
			doc.setSelectedBone(node.owningBone());
		}
	}

	/** 根据文档骨架重建骨架树 */
	public void rebuild() {
		treeSkeleton = doc.skeleton;
		boolean documentChanged = doc.data != expansionDocument;
		SkeletonDocument.TreeViewState restoredTree = documentChanged ? doc.treeViewState() : null;
		if (documentChanged && restoredTree != null) {
			syncing = true;
			filterField.setText(restoredTree.filter());
			syncing = false;
		}
		String filter = filterField.getText() == null ? ""
			: filterField.getText().trim().toLowerCase(java.util.Locale.ROOT);
		boolean filtering = !filter.isEmpty();
		if (documentChanged) {
			expansionDocument = doc.data;
			selectedConstraint = doc.getSelectedConstraint();
			savedExpandedValues.clear();
			savedTreeValues.clear();
			if (restoredTree != null) {
				for (String value : restoredTree.expandedValues()) savedExpandedValues.add(value);
				savedTreeValues.addAll(restoredTree.treeValues());
			}
			lastBuildFiltered = false;
		} else if (!lastBuildFiltered && !tree.getRootNodes().isEmpty()) {
			captureExpansionState();
		}
		syncing = true;
		tree.clearChildren();
		nodesByBone.clear();
		nodesBySlot.clear();
		nodesByConstraint.clear();
		nodesByConstraintRelation.clear();
		if (doc.skeleton == null) {
			syncing = false;
			lastBuildFiltered = filtering;
			return;
		}
		Array<Bone> bones = doc.skeleton.getBones();
		Array<BoneNode> created = new Array<>();
		for (Bone bone : bones) {
			BoneNode node = new BoneNode(bone, new Label(bone.getData().getName(), nodeLabelStyle));
			node.setValue("bone:" + bone.getData().getName());
			created.add(node);
			nodesByBone.put(bone, node);
		}
		for (BoneNode node : created) {
			Bone parent = node.bone.getParent();
			if (parent == null) {
				tree.add(node);
			} else {
				BoneNode parentNode = nodesByBone.get(parent);
				if (parentNode != null) parentNode.add(node);
				else tree.add(node);
			}
		}
		for (Slot slot : doc.skeleton.getSlots()) {
			BoneNode boneNode = nodesByBone.get(slot.getBone());
			if (boneNode == null) continue;
			BoneNode slotNode = new BoneNode(slot, new Label(slot.getData().getName(), nodeLabelStyle));
			slotNode.setValue("slot:" + slot.getData().getName());
			boneNode.add(slotNode);
			created.add(slotNode);
			nodesBySlot.put(slot, slotNode);
			for (String placeholder : editor.attachmentNames(slot.getData().getIndex(), null)) {
				Attachment attachment = editor.currentAttachment(slot.getData().getIndex(), placeholder);
				if (attachment == null) continue;
				BoneNode attachmentNode = new BoneNode(slot, attachment, placeholder,
					new Label(placeholder, nodeLabelStyle));
				attachmentNode.setValue("attachment:" + slot.getData().getName() + ":" + placeholder);
				slotNode.add(attachmentNode);
				created.add(attachmentNode);
			}
		}
		if (doc.data != null && doc.data.getConstraints().notEmpty()) {
			BoneNode group = new BoneNode("约束", new Label(I18n.t("约束  (", "Constraints  (")
				+ doc.data.getConstraints().size + ")",
				nodeLabelStyle));
			group.setValue("group:constraints");
			tree.add(group);
			created.add(group);
			for (int index = 0; index < doc.data.getConstraints().size; index++) {
				ConstraintData<?, ?> constraint = doc.data.getConstraints().get(index);
				String type = constraintType(constraint);
				BoneData dataBone = constraintBone(constraint, doc.data);
				Bone runtimeBone = dataBone == null ? doc.skeleton.getRootBone()
					: doc.skeleton.findBone(dataBone.getName());
				BoneNode node = new BoneNode(constraint, runtimeBone, type,
					new Label(String.format(java.util.Locale.ROOT, "%02d  %s  %s", index + 1, type,
						constraint.getName()), nodeLabelStyle));
				node.setValue("constraint:" + type + ":" + constraint.getName());
				group.add(node);
				created.add(node);
				nodesByConstraint.put(constraint, node);
				for (ConstraintRelation relation : constraintRelations(constraint, doc.data)) {
					Bone relationBone = relation.bone == null ? runtimeBone
						: doc.skeleton.findBone(relation.bone.getName());
					BoneNode relationNode = new BoneNode(constraint, relationBone, type, relation.id,
						relation.label,
						new Label(relation.label, nodeLabelStyle));
					relationNode.setValue("constraint-relation:" + type + ":" + constraint.getName()
						+ ":" + relation.id);
					node.add(relationNode);
					created.add(relationNode);
					nodesByConstraintRelation.put(constraintRelationKey(constraint, relation.id), relationNode);
				}
			}
		}
		if (filtering) {
			for (int index = tree.getRootNodes().size - 1; index >= 0; index--) {
				BoneNode root = tree.getRootNodes().get(index);
				if (!retainMatchingNodes(root, filter)) tree.remove(root);
			}
			nodesByBone.clear();
			nodesBySlot.clear();
			nodesByConstraint.clear();
			nodesByConstraintRelation.clear();
			created.clear();
			collectRetainedNodes(tree.getRootNodes(), created);
			for (BoneNode node : created) {
				if (node.bone != null) nodesByBone.put(node.bone, node);
				if (node.slot != null && node.attachment == null) nodesBySlot.put(node.slot, node);
				if (node.constraint != null && node.constraintRelation == null)
					nodesByConstraint.put(node.constraint, node);
				else if (node.constraint != null)
					nodesByConstraintRelation.put(constraintRelationKey(node.constraint,
						node.constraintRelation), node);
			}
		}
		restoreExpansionState(created, filtering);
		syncing = false;
		BoneNode constraintNode = doc.getSelectedConstraintRelation().isBlank()
			? nodesByConstraint.get(selectedConstraint)
			: nodesByConstraintRelation.get(constraintRelationKey(selectedConstraint,
				doc.getSelectedConstraintRelation()));
		if (constraintNode != null) selectConstraintNode(constraintNode);
		else selectNode(doc.getSelectedBone());
		lastBuildFiltered = filtering;
		if (documentChanged && restoredTree != null) {
			treeScroll.layout();
			treeScroll.setScrollX(restoredTree.scrollX());
			treeScroll.setScrollY(restoredTree.scrollY());
			treeScroll.updateVisualScroll();
		}
	}

	/** Copies the live hierarchy controls into the active document before Sidecar save or document switch. */
	public void persistViewState() {
		Set<String> expanded = new HashSet<>(), values = new HashSet<>();
		if (lastBuildFiltered) {
			for (String value : savedExpandedValues) expanded.add(value);
			values.addAll(savedTreeValues);
		} else {
			Array<String> currentExpanded = new Array<>();
			tree.findExpandedValues(currentExpanded);
			for (String value : currentExpanded) if (value != null) expanded.add(value);
			collectTreeValues(tree.getRootNodes(), values);
		}
		doc.setTreeViewState(new SkeletonDocument.TreeViewState(filterField.getText(),
			treeScroll.getScrollX(), treeScroll.getScrollY(), expanded, values));
	}

	private static boolean retainMatchingNodes(BoneNode node, String filter) {
		for (int index = node.getChildren().size - 1; index >= 0; index--) {
			BoneNode child = node.getChildren().get(index);
			if (!retainMatchingNodes(child, filter)) node.remove(child);
		}
		return node.searchText.toLowerCase(java.util.Locale.ROOT).contains(filter)
			|| !node.getChildren().isEmpty();
	}

	private static void collectRetainedNodes(Array<BoneNode> nodes, Array<BoneNode> out) {
		for (BoneNode node : nodes) {
			out.add(node);
			collectRetainedNodes(node.getChildren(), out);
		}
	}

	/** Rebuilds only when a data edit replaced the runtime skeleton backing existing tree nodes. */
	public void rebuildIfSkeletonChanged() {
		if (shouldRebuildForSkeleton(treeSkeleton, doc.skeleton)) rebuild();
	}

	/** Rebuilds translated structural labels while preserving expansion and selection state. */
	public void refreshLanguage() {
		I18n.localize(this);
		rebuild();
		I18n.localize(this);
	}

	static boolean shouldRebuildForSkeleton(Skeleton boundSkeleton, Skeleton currentSkeleton) {
		return boundSkeleton != currentSkeleton;
	}

	static Set<Bone> visibleBones(Array<Bone> bones, String filter) {
		Set<Bone> visible = new HashSet<>();
		if (bones == null) return visible;
		String query = filter == null ? "" : filter.trim().toLowerCase(java.util.Locale.ROOT);
		if (query.isEmpty()) {
			for (Bone bone : bones) visible.add(bone);
			return visible;
		}
		for (Bone bone : bones) {
			if (!bone.getData().getName().toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
			for (Bone path = bone; path != null && visible.add(path); path = path.getParent()) {
				// Existing ancestors imply the remainder of this path is already present.
			}
		}
		return visible;
	}

	private void captureExpansionState() {
		savedExpandedValues.clear();
		tree.findExpandedValues(savedExpandedValues);
		savedTreeValues.clear();
		collectTreeValues(tree.getRootNodes(), savedTreeValues);
	}

	private void collectTreeValues(Array<BoneNode> nodes, Set<String> values) {
		for (BoneNode node : nodes) {
			if (node.getValue() != null) values.add(node.getValue());
			collectTreeValues(node.getChildren(), values);
		}
	}

	private void restoreExpansionState(Array<BoneNode> nodes, boolean filtering) {
		boolean hasSavedTree = !savedTreeValues.isEmpty();
		for (BoneNode node : nodes) {
			if (node.hasChildren() && shouldExpandNode(filtering, hasSavedTree,
				savedTreeValues.contains(node.getValue()))) node.setExpanded(true);
		}
		if (!filtering && hasSavedTree) tree.restoreExpandedValues(savedExpandedValues);
	}

	private void selectNode(Bone bone) {
		BoneNode node = nodesByBone.get(bone);
		if (node == null) return;
		selectedConstraint = null;
		syncing = true;
		tree.getSelection().set(node);
		syncing = false;
	}

	private void selectNodes(Set<Bone> bones) {
		if (bones == null || bones.isEmpty()) return;
		com.badlogic.gdx.utils.Array<BoneNode> nodes = new com.badlogic.gdx.utils.Array<>();
		for (Bone bone : bones) {
			BoneNode node = nodesByBone.get(bone);
			if (node != null) nodes.add(node);
		}
		if (nodes.isEmpty()) return;
		syncing = true;
		tree.getSelection().setAll(nodes);
		syncing = false;
	}

	/** 从视图外部选中（例如视口点击）时同步高亮 */
	public void syncSelection() {
		if (doc.getSelectionKind() == SkeletonDocument.SelectionKind.CONSTRAINT
			&& doc.getSelectedConstraint() != null) {
			selectedConstraint = doc.getSelectedConstraint();
			BoneNode constraintNode = doc.getSelectedConstraintRelation().isBlank()
				? nodesByConstraint.get(selectedConstraint)
				: nodesByConstraintRelation.get(constraintRelationKey(selectedConstraint,
					doc.getSelectedConstraintRelation()));
			if (constraintNode != null && currentNode() != constraintNode) selectConstraintNode(constraintNode);
			return;
		}
		if (doc.getSelectionKind() == SkeletonDocument.SelectionKind.SLOT
			|| doc.getSelectionKind() == SkeletonDocument.SelectionKind.ATTACHMENT) {
			Slot slot = doc.skeleton == null ? null : doc.skeleton.findSlot(doc.getSelectedSlotName());
			if (slot != null) {
				BoneNode slotNode = nodesBySlot.get(slot);
				BoneNode target = slotNode;
				if (slotNode != null && doc.getSelectionKind() == SkeletonDocument.SelectionKind.ATTACHMENT)
					for (BoneNode child : slotNode.getChildren())
						if (doc.getSelectedAttachmentPlaceholder().equals(child.attachmentPlaceholder)) {
							target = child;
							break;
						}
				if (target != null && currentNode() != target) selectConstraintNode(target);
				return;
			}
		}
		if (doc.getSelectionKind() == SkeletonDocument.SelectionKind.BONE
			&& doc.getSelectedBones().size() > 1) {
			selectNodes(doc.getSelectedBones());
			return;
		}
		BoneNode current = tree.getSelection().isEmpty() ? null : tree.getSelection().first();
		Bone sel = current == null ? null : current.owningBone();
		if (sel != doc.getSelectedBone()) {
			selectedConstraint = null;
			syncing = true;
			tree.getSelection().clear();
			syncing = false;
			findAndSelect(doc.getSelectedBone());
		}
	}

	/** Selects the corresponding slot or attachment node after a viewport hit. */
	public void selectAttachment(Slot slot, Attachment attachment) {
		if (slot == null || slot.getBone() != doc.getSelectedBone()) return;
		selectedConstraint = null;
		BoneNode slotNode = nodesBySlot.get(slot);
		if (slotNode == null) {
			findAndSelect(slot.getBone());
			return;
		}
		BoneNode target = slotNode;
		if (attachment != null) {
			for (BoneNode child : slotNode.getChildren()) {
				if (child.attachment == attachment) {
					target = child;
					break;
				}
			}
		}
		target.expandTo();
		syncing = true;
		tree.getSelection().set(target);
		syncing = false;
		doc.setSlotSelection(slot, target.attachment == null ? "" : target.attachmentPlaceholder,
			target.attachment == null ? "" : target.attachment.getName());
		scrollNodeIntoView(target);
	}

	public Slot getSelectedSlot() {
		BoneNode node = currentNode();
		return node == null ? null : node.slot;
	}

	public Attachment getSelectedAttachment() {
		BoneNode node = currentNode();
		if (node == null || node.slot == null) return null;
		return node.attachment != null ? node.attachment : node.slot.getAppliedPose().getAttachment();
	}

	public ConstraintData<?, ?> getSelectedConstraint() { return selectedConstraint; }

	/** Selects a bone node explicitly after a direct viewport bone hit. */
	public void selectBone(Bone bone) {
		selectedConstraint = null;
		findAndSelect(bone);
	}

	public boolean showAll() {
		boolean changed = setAllVisible(doc, history, doc.data);
		if (changed) showStatus("已显示全部骨骼和槽", false);
		return changed;
	}

	private void findAndSelect(Bone bone) {
		BoneNode node = nodesByBone.get(bone);
		if (node == null) return;
		node.expandTo();
		syncing = true;
		tree.getSelection().set(node);
		syncing = false;
		scrollNodeIntoView(node);
	}

	private void selectConstraintNode(BoneNode node) {
		node.expandTo();
		syncing = true;
		tree.getSelection().set(node);
		syncing = false;
		scrollNodeIntoView(node);
	}

	static String constraintType(ConstraintData<?, ?> constraint) {
		if (constraint instanceof IkConstraintData) return "IK";
		if (constraint instanceof TransformConstraintData) return "Transform";
		if (constraint instanceof PathConstraintData) return "Path";
		if (constraint instanceof PhysicsConstraintData) return "Physics";
		return constraint instanceof SliderData ? "Slider" : "Constraint";
	}

	private static String constraintRelationKey(ConstraintData<?, ?> constraint, String relation) {
		return (constraint == null ? "" : constraint.getClass().getName() + "\u001f" + constraint.getName())
			+ "\u001f" + (relation == null ? "" : relation);
	}

	static BoneData constraintBone(ConstraintData<?, ?> constraint, SkeletonData data) {
		BoneData result = null;
		if (constraint instanceof IkConstraintData ik && ik.getBones().notEmpty()) result = ik.getBones().peek();
		else if (constraint instanceof TransformConstraintData transform && transform.getBones().notEmpty())
			result = transform.getBones().first();
		else if (constraint instanceof PathConstraintData path && path.getBones().notEmpty())
			result = path.getBones().first();
		else if (constraint instanceof PhysicsConstraintData physics) result = physics.getBone();
		else if (constraint instanceof SliderData slider) result = slider.getBone();
		if (result != null) return result;
		return data == null || data.getBones().isEmpty() ? null : data.getBones().first();
	}

	static record ConstraintRelation(String id, String label, BoneData bone) {}

	static Array<ConstraintRelation> constraintRelations(ConstraintData<?, ?> constraint) {
		return constraintRelations(constraint, null);
	}

	static Array<ConstraintRelation> constraintRelations(ConstraintData<?, ?> constraint, SkeletonData data) {
		Array<ConstraintRelation> result = new Array<>();
		if (constraint instanceof IkConstraintData ik) {
			for (BoneData bone : ik.getBones()) result.add(new ConstraintRelation("constrained:" + bone.getName(),
				"受约束骨骼 · " + bone.getName(), bone));
			if (ik.getTarget() != null)
				result.add(new ConstraintRelation("target:" + ik.getTarget().getName(),
					"目标骨骼 · " + ik.getTarget().getName(), ik.getTarget()));
		} else if (constraint instanceof TransformConstraintData transform) {
			for (BoneData bone : transform.getBones())
				result.add(new ConstraintRelation("target:" + bone.getName(), "目标骨骼 · " + bone.getName(), bone));
			if (transform.getSource() != null)
				result.add(new ConstraintRelation("source:" + transform.getSource().getName(),
					"来源骨骼 · " + transform.getSource().getName(), transform.getSource()));
		} else if (constraint instanceof PathConstraintData path) {
			for (BoneData bone : path.getBones()) result.add(new ConstraintRelation("constrained:" + bone.getName(),
				"受约束骨骼 · " + bone.getName(), bone));
			if (path.getSlot() != null)
				result.add(new ConstraintRelation("slot:" + path.getSlot().getName(),
					"目标槽 · " + path.getSlot().getName(), path.getSlot().getBoneData()));
		} else if (constraint instanceof PhysicsConstraintData physics && physics.getBone() != null) {
			result.add(new ConstraintRelation("physics:" + physics.getBone().getName(),
				"模拟骨骼 · " + physics.getBone().getName(), physics.getBone()));
		} else if (constraint instanceof SliderData slider) {
			if (slider.getBone() != null) {
				String property = slider.getProperty() == null ? "时间" : slider.getProperty().toString();
				String propertyId = slider.getProperty() == null ? "time"
					: slider.getProperty().getClass().getSimpleName();
				result.add(new ConstraintRelation("source:" + slider.getBone().getName() + ":" + propertyId,
					"属性来源 · " + slider.getBone().getName() + " / " + property, slider.getBone()));
			}
			if (slider.getAnimation() != null) {
				Array<BoneData> affected = SkeletonDocument.constraintSelectionBoneData(slider, data);
				BoneData target = firstOtherBone(affected, slider.getBone());
				result.add(new ConstraintRelation("animation:" + slider.getAnimation().getName(),
					"目标动画 · " + slider.getAnimation().getName(), target));
				for (BoneData bone : affected)
					if (bone != slider.getBone())
						result.add(new ConstraintRelation("animation-bone:" + bone.getName(),
							"动画影响 · " + bone.getName(), bone));
			}
		}
		return result;
	}

	private static BoneData firstOtherBone(Array<BoneData> bones, BoneData excluded) {
		for (BoneData bone : bones) if (bone != excluded) return bone;
		return excluded;
	}
}
