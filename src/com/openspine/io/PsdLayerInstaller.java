package com.openspine.io;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SlotData;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.SkeletonEditor;

/** Prepares and atomically applies PSD layers as slots and Region attachments. */
public final class PsdLayerInstaller {
	private record LayerEntry(SlotData slot, String placeholder, RegionAttachment attachment,
		PsdImporter.ImportedLayer source, int sourceIndex) {}

	public static Transaction prepare(SkeletonDocument doc, SkeletonEditor editor,
		PsdImporter.ImportResult imported, TextureAtlas atlas) {
		if (doc == null || editor == null || editor.doc != doc || imported == null || atlas == null)
			throw new IllegalArgumentException("PSD 图层安装参数不完整");
		if (doc.data == null || doc.data.getBones().isEmpty())
			throw new IllegalStateException("当前项目没有可挂载 PSD 图层的根骨骼");
		if (imported.layers().isEmpty()) throw new IllegalArgumentException("PSD 没有可安装的图层");

		Skin originalDefault = doc.data.getDefaultSkin();
		Skin targetSkin = originalDefault;
		boolean addSkin = false;
		if (targetSkin == null) {
			targetSkin = doc.data.findSkin("default");
			if (targetSkin == null) {
				targetSkin = new Skin("default");
				addSkin = true;
			}
		}

		BoneData root = doc.data.getBones().first();
		int baseSlotCount = doc.data.getSlots().size;
		Set<String> reservedNames = new HashSet<>();
		for (SlotData slot : doc.data.getSlots()) reservedNames.add(slot.getName());
		List<LayerEntry> entries = new ArrayList<>(imported.layers().size());
		// Both PSD layer records and Spine's setup slots are ordered from bottom to top.
		for (int index = 0; index < imported.layers().size(); index++) {
			PsdImporter.ImportedLayer layer = imported.layers().get(index);
			TextureRegion atlasRegion = atlas.findRegion(layer.regionName());
			if (atlasRegion == null)
				throw new IllegalStateException("图集中缺少 PSD 图层区域：" + layer.regionName());
			String slotName = uniqueName(layer.name(), reservedNames);
			SlotData slot = new SlotData(baseSlotCount + entries.size(), slotName, root);
			slot.setBlendMode(BlendMode.normal);
			slot.setVisible(layer.visible());
			slot.setAttachmentName(layer.regionName());

			Sequence sequence = new Sequence(1, false);
			sequence.getRegions()[0] = atlasRegion;
			RegionAttachment attachment = new RegionAttachment(layer.regionName(), sequence);
			attachment.setPath(layer.regionName());
			attachment.getColor().set(Color.WHITE);
			attachment.setX(layer.left() + layer.width() * 0.5f - imported.canvasWidth() * 0.5f);
			attachment.setY(imported.canvasHeight() * 0.5f - layer.top() - layer.height() * 0.5f);
			attachment.setScaleX(1f);
			attachment.setScaleY(1f);
			attachment.setRotation(0f);
			attachment.setWidth(layer.width());
			attachment.setHeight(layer.height());
			attachment.updateSequence();
			entries.add(new LayerEntry(slot, layer.regionName(), attachment, layer,
				layer.sourceIndex() >= 0 ? layer.sourceIndex() : index));
		}
		return new Transaction(doc, editor, originalDefault, targetSkin, addSkin,
			baseSlotCount, List.copyOf(entries));
	}

	private static String uniqueName(String requested, Set<String> reserved) {
		String base = requested == null || requested.trim().isEmpty() ? "slot" : requested.trim();
		if (reserved.add(base)) return base;
		for (int suffix = 2;; suffix++)
			if (reserved.add(base + suffix)) return base + suffix;
	}

	public static final class Transaction {
		private final SkeletonDocument doc;
		private final SkeletonData identity;
		private final SkeletonEditor editor;
		private final Skin originalDefault;
		private final Skin targetSkin;
		private final boolean addSkin;
		private final int baseSlotCount;
		private final List<LayerEntry> entries;
		private boolean applied;

		private Transaction(SkeletonDocument doc, SkeletonEditor editor, Skin originalDefault,
			Skin targetSkin, boolean addSkin, int baseSlotCount, List<LayerEntry> entries) {
			this.doc = doc;
			this.identity = doc.data;
			this.editor = editor;
			this.originalDefault = originalDefault;
			this.targetSkin = targetSkin;
			this.addSkin = addSkin;
			this.baseSlotCount = baseSlotCount;
			this.entries = entries;
		}

		public int layerCount() {
			return entries.size();
		}

		public boolean isApplied() {
			return applied;
		}

		/** Captures the stable source-to-authoring mapping after the slots have been installed. */
		public SkeletonDocument.PsdSource sourceMetadata(PsdImporter.ImportResult imported) {
			if (!applied || imported == null) throw new IllegalStateException("PSD 图层尚未完成安装");
			validateDocumentIdentity();
			List<SkeletonDocument.PsdLayerMapping> mappings = new ArrayList<>(entries.size());
			for (LayerEntry entry : entries) mappings.add(new SkeletonDocument.PsdLayerMapping(
				entry.sourceIndex(), entry.source().name(), entry.source().regionName(), targetSkin.getName(),
				entry.slot().getName(), entry.placeholder(), entry.source().visible()));
			return new SkeletonDocument.PsdSource(imported.source().toAbsolutePath().normalize().toString(),
				imported.hiddenLayersIgnored(), imported.canvasWidth(), imported.canvasHeight(), mappings);
		}

		public void apply() {
			if (applied) throw new IllegalStateException("PSD 图层已经安装");
			validateApplyState();
			boolean dirty = doc.dirty;
			long revision = doc.revision;
			int addedSlots = 0;
			boolean addedSkin = false;
			try {
				if (addSkin) {
					doc.data.getSkins().add(targetSkin);
					addedSkin = true;
				}
				if (originalDefault == null) doc.data.setDefaultSkin(targetSkin);
				for (LayerEntry entry : entries) {
					doc.data.getSlots().add(entry.slot());
					targetSkin.setAttachment(entry.slot().getIndex(), entry.placeholder(), entry.attachment());
					addedSlots++;
				}
				doc.markDirty();
				editor.rebuildSkeleton();
				applied = true;
			} catch (RuntimeException failure) {
				for (int index = addedSlots - 1; index >= 0; index--) {
					LayerEntry entry = entries.get(index);
					targetSkin.removeAttachment(entry.slot().getIndex(), entry.placeholder());
					doc.data.getSlots().removeValue(entry.slot(), true);
				}
				if (originalDefault == null) doc.data.setDefaultSkin(null);
				if (addedSkin) doc.data.getSkins().removeValue(targetSkin, true);
				restoreDocumentState(dirty, revision, failure);
				throw failure;
			}
		}

		public void undo() {
			if (!applied) throw new IllegalStateException("PSD 图层尚未安装");
			validateUndoState();
			boolean dirty = doc.dirty;
			long revision = doc.revision;
			try {
				for (int index = entries.size() - 1; index >= 0; index--) {
					LayerEntry entry = entries.get(index);
					targetSkin.removeAttachment(entry.slot().getIndex(), entry.placeholder());
					doc.data.getSlots().removeIndex(entry.slot().getIndex());
				}
				if (originalDefault == null) doc.data.setDefaultSkin(null);
				if (addSkin) doc.data.getSkins().removeValue(targetSkin, true);
				doc.markDirty();
				editor.rebuildSkeleton();
				applied = false;
			} catch (RuntimeException failure) {
				restoreAppliedData();
				restoreDocumentState(dirty, revision, failure);
				throw failure;
			}
		}

		private void validateApplyState() {
			validateDocumentIdentity();
			if (doc.data.getSlots().size != baseSlotCount)
				throw new IllegalStateException("槽结构已改变，无法重做 PSD 导入");
			if (doc.data.getDefaultSkin() != originalDefault)
				throw new IllegalStateException("默认皮肤已改变，无法重做 PSD 导入");
			if (addSkin ? doc.data.getSkins().contains(targetSkin, true)
				: !doc.data.getSkins().contains(targetSkin, true))
				throw new IllegalStateException("PSD 目标皮肤状态已改变");
			for (LayerEntry entry : entries) {
				if (!doc.data.getBones().contains(entry.slot().getBoneData(), true))
					throw new IllegalStateException("PSD 挂载骨骼已改变，无法安装图层");
				if (doc.data.findSlot(entry.slot().getName()) != null)
					throw new IllegalStateException("槽名称已被占用：" + entry.slot().getName());
			}
		}

		private void validateUndoState() {
			validateDocumentIdentity();
			if (doc.data.getSlots().size != baseSlotCount + entries.size())
				throw new IllegalStateException("PSD 槽后方已有新槽，无法安全撤销导入");
			if (doc.data.getDefaultSkin() != targetSkin || !doc.data.getSkins().contains(targetSkin, true))
				throw new IllegalStateException("PSD 目标皮肤已改变，无法安全撤销导入");
			for (int index = 0; index < entries.size(); index++) {
				LayerEntry entry = entries.get(index);
				if (doc.data.getSlots().get(baseSlotCount + index) != entry.slot()
					|| targetSkin.getAttachment(entry.slot().getIndex(), entry.placeholder()) != entry.attachment())
					throw new IllegalStateException("PSD 图层已被修改，无法安全撤销导入");
			}
		}

		private void validateDocumentIdentity() {
			if (doc.data != identity) throw new IllegalStateException("PSD 导入所属骨架已改变");
		}

		private void restoreAppliedData() {
			if (addSkin && !doc.data.getSkins().contains(targetSkin, true)) doc.data.getSkins().add(targetSkin);
			doc.data.setDefaultSkin(targetSkin);
			for (LayerEntry entry : entries) {
				if (!doc.data.getSlots().contains(entry.slot(), true)) doc.data.getSlots().add(entry.slot());
				targetSkin.setAttachment(entry.slot().getIndex(), entry.placeholder(), entry.attachment());
			}
		}

		private void restoreDocumentState(boolean dirty, long revision, RuntimeException failure) {
			try {
				editor.rebuildSkeleton();
			} catch (RuntimeException rollbackFailure) {
				failure.addSuppressed(rollbackFailure);
			} finally {
				doc.dirty = dirty;
				doc.revision = revision;
			}
		}
	}

	private PsdLayerInstaller() {}
}
