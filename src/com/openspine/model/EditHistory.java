package com.openspine.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;

import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.BoneData.Inherit;
import com.esotericsoftware.spine.Physics;
import com.esotericsoftware.spine.Skeleton;

/** Shared undo/redo history for live poses and reversible timeline edits. Structural edits clear this history. */
public final class EditHistory {
	private static final int MAX_ENTRIES = 100;

	private final SkeletonDocument doc;
	private final Deque<Entry> undo = new ArrayDeque<>();
	private final Deque<Entry> redo = new ArrayDeque<>();
	private PoseSnapshot pending;
	private String pendingLabel;
	private boolean pendingPoseMode, pendingPlaying;
	private Runnable onChanged;
	private long nextDocumentState;
	private long currentDocumentState;
	private long savedDocumentState;

	public EditHistory(SkeletonDocument doc) {
		this(doc, doc != null && doc.dirty);
	}

	/** Creates a history for a tab whose content may currently be parked outside the shared UI document. */
	public EditHistory(SkeletonDocument doc, boolean initiallyDirty) {
		if (doc == null) throw new IllegalArgumentException("文档不能为空");
		this.doc = doc;
		savedDocumentState = initiallyDirty ? -1 : currentDocumentState;
	}

	public void setOnChanged(Runnable listener) {
		onChanged = listener;
	}

	public void begin(String label) {
		if (pending != null || doc.skeleton == null) return;
		pending = PoseSnapshot.capture(doc);
		pendingLabel = label == null ? "Edit pose" : label;
		pendingPoseMode = doc.poseMode;
		pendingPlaying = doc.playing;
	}

	public boolean commit() {
		if (pending == null || doc.skeleton == null) return false;
		if (pending.skeleton != doc.skeleton) {
			cancel();
			return false;
		}
		PoseSnapshot after = PoseSnapshot.capture(doc);
		if (pending.sameAs(after)) {
			pending = null;
			pendingLabel = null;
			return false;
		}
		PoseSnapshot before = pending;
		String label = pendingLabel;
		pending = null;
		pendingLabel = null;
		addEntry(new Entry(label, () -> before.apply(doc), () -> after.apply(doc), false));
		return true;
	}

	/** Records an already-applied reversible edit to exportable document data. */
	public void record(String label, Runnable undoAction, Runnable redoAction) {
		if (undoAction == null || redoAction == null)
			throw new IllegalArgumentException("撤销和重做动作不能为空");
		cancel();
		addEntry(new Entry(label == null ? "编辑" : label, undoAction, redoAction, true));
	}

	public void cancel() {
		pending = null;
		pendingLabel = null;
	}

	/** Cancels a live gesture and restores its complete pose and selection. */
	public boolean rollbackPending() {
		PoseSnapshot before = pending;
		boolean poseMode = pendingPoseMode, playing = pendingPlaying;
		cancel();
		if (before == null || before.skeleton != doc.skeleton) return false;
		before.apply(doc);
		doc.poseMode = poseMode;
		doc.playing = playing;
		return true;
	}

	public boolean undo() {
		cancel();
		Entry entry = undo.peekLast();
		if (entry == null) return false;
		entry.undo.run();
		undo.removeLast();
		redo.addLast(entry);
		if (entry.affectsDocument) {
			currentDocumentState = entry.beforeDocumentState;
			syncDocumentDirty();
		}
		fireChanged();
		return true;
	}

	public boolean redo() {
		cancel();
		Entry entry = redo.peekLast();
		if (entry == null) return false;
		entry.redo.run();
		redo.removeLast();
		undo.addLast(entry);
		if (entry.affectsDocument) {
			currentDocumentState = entry.afterDocumentState;
			syncDocumentDirty();
		}
		fireChanged();
		return true;
	}

	public void clear() {
		clear(true);
	}

	/** Clears stale entries when the caller will refresh command state through its data-change callback. */
	public void clearSilently() {
		clear(false);
	}

	private void clear(boolean notify) {
		pending = null;
		pendingLabel = null;
		undo.clear();
		redo.clear();
		currentDocumentState = ++nextDocumentState;
		savedDocumentState = doc.dirty ? -1 : currentDocumentState;
		if (notify) fireChanged();
	}

	/** Marks the current document-history position as the state written to disk. */
	public void markSaved() {
		markSavedState();
		doc.markSaved();
	}

	/** Marks the history position saved when the caller already updated the corresponding parked document. */
	public void markSavedState() {
		savedDocumentState = currentDocumentState;
	}

	/** Marks the document as not yet saved without discarding subsequent undo history. */
	public void markUnsaved() {
		savedDocumentState = -1;
		doc.markDirty();
	}

	public boolean isAtSavedState() {
		return currentDocumentState == savedDocumentState;
	}

	public boolean canUndo() { return !undo.isEmpty(); }
	public boolean canRedo() { return !redo.isEmpty(); }
	public String undoLabel() { return undo.isEmpty() ? "" : undo.peekLast().label; }
	public String redoLabel() { return redo.isEmpty() ? "" : redo.peekLast().label; }

	private void fireChanged() {
		if (onChanged != null) onChanged.run();
	}

	private void addEntry(Entry entry) {
		if (entry.affectsDocument) {
			entry.beforeDocumentState = currentDocumentState;
			entry.afterDocumentState = ++nextDocumentState;
			currentDocumentState = entry.afterDocumentState;
		}
		undo.addLast(entry);
		while (undo.size() > MAX_ENTRIES) undo.removeFirst();
		redo.clear();
		if (entry.affectsDocument) syncDocumentDirty();
		fireChanged();
	}

	private void syncDocumentDirty() {
		doc.setDirtyFromHistory(currentDocumentState != savedDocumentState);
	}

	private static final class Entry {
		final String label;
		final Runnable undo;
		final Runnable redo;
		final boolean affectsDocument;
		long beforeDocumentState;
		long afterDocumentState;

		Entry(String label, Runnable undo, Runnable redo, boolean affectsDocument) {
			this.label = label;
			this.undo = undo;
			this.redo = redo;
			this.affectsDocument = affectsDocument;
		}
	}

	private static final class PoseSnapshot {
		final Skeleton skeleton;
		final String[] names;
		final float[] values;
		final Inherit[] inherits;
		final List<String> selectedNames;

		private PoseSnapshot(Skeleton skeleton, String[] names, float[] values, Inherit[] inherits, List<String> selectedNames) {
			this.skeleton = skeleton;
			this.names = names;
			this.values = values;
			this.inherits = inherits;
			this.selectedNames = selectedNames;
		}

		static PoseSnapshot capture(SkeletonDocument doc) {
			int count = doc.skeleton.getBones().size;
			String[] names = new String[count];
			float[] values = new float[count * 7];
			Inherit[] inherits = new Inherit[count];
			for (int i = 0; i < count; i++) {
				Bone bone = doc.skeleton.getBones().get(i);
				var pose = bone.getPose();
				names[i] = bone.getData().getName();
				int p = i * 7;
				values[p] = pose.getX();
				values[p + 1] = pose.getY();
				values[p + 2] = pose.getRotation();
				values[p + 3] = pose.getScaleX();
				values[p + 4] = pose.getScaleY();
				values[p + 5] = pose.getShearX();
				values[p + 6] = pose.getShearY();
				inherits[i] = pose.getInherit();
			}
			List<String> selected = doc.getSelectedBones().stream().map(bone -> bone.getData().getName()).toList();
			return new PoseSnapshot(doc.skeleton, names, values, inherits, selected);
		}

		void apply(SkeletonDocument doc) {
			if (doc.skeleton == null) return;
			doc.beginPoseEdit();
			for (int i = 0; i < names.length; i++) {
				Bone bone = doc.skeleton.findBone(names[i]);
				if (bone == null) continue;
				var pose = bone.getPose();
				int p = i * 7;
				pose.setX(values[p]);
				pose.setY(values[p + 1]);
				pose.setRotation(values[p + 2]);
				pose.setScaleX(values[p + 3]);
				pose.setScaleY(values[p + 4]);
				pose.setShearX(values[p + 5]);
				pose.setShearY(values[p + 6]);
				pose.setInherit(inherits[i]);
			}
			doc.updateWorldTransform(Physics.pose);
			List<Bone> selected = new ArrayList<>();
			for (String name : selectedNames) {
				Bone bone = doc.skeleton.findBone(name);
				if (bone != null) selected.add(bone);
			}
			doc.setSelectedBones(selected);
			doc.markPoseChanged();
		}

		boolean sameAs(PoseSnapshot other) {
			if (other == null || names.length != other.names.length || values.length != other.values.length
				|| inherits.length != other.inherits.length) return false;
			for (int i = 0; i < names.length; i++) {
				if (!names[i].equals(other.names[i]) || inherits[i] != other.inherits[i]) return false;
			}
			for (int i = 0; i < values.length; i++) {
				if (values[i] != other.values[i]) return false;
			}
			return true;
		}
	}
}
