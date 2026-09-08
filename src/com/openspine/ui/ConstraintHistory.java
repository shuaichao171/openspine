package com.openspine.ui;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.esotericsoftware.spine.ConstraintData;
import com.esotericsoftware.spine.Skin;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonEditor;

/** Shared reversible wrapper for the five constraint setup editors. */
final class ConstraintHistory {
	private static final Map<EditHistory, IdentityHashMap<ConstraintData<?, ?>, ConstraintRef<?>>> REFERENCES =
		new WeakHashMap<>();

	interface Operations<C, S> {
		C add(S setup);
		C update(C current, S setup);
		boolean delete(C current);
	}

	static <C extends ConstraintData<?, ?>, S> void recordApplied(EditHistory history,
		SkeletonEditor editor, String label, C before, C after, S beforeSetup, S afterSetup,
		List<Skin> beforeActivations, List<Skin> afterActivations, Operations<C, S> operations) {
		if (history == null || editor == null || after == null || afterSetup == null || operations == null) return;
		ConstraintRef<C> reference = reference(history, before, after);
		if (before == null) {
			history.record(label,
				() -> setCurrent(history, reference,
					removeCreated(editor, reference.current, operations)),
				() -> setCurrent(history, reference,
					addRestored(editor, afterSetup, afterActivations, operations)));
			return;
		}
		if (beforeSetup == null) throw new IllegalArgumentException("Constraint history is missing the previous setup");
		List<Skin> undoSkins = List.copyOf(beforeActivations);
		List<Skin> redoSkins = List.copyOf(afterActivations);
		history.record(label,
			() -> setCurrent(history, reference,
				updateRestored(editor, reference.current, beforeSetup, undoSkins, operations)),
			() -> setCurrent(history, reference,
				updateRestored(editor, reference.current, afterSetup, redoSkins, operations)));
	}

	static void recordMoved(EditHistory history, SkeletonEditor editor, String label,
		ConstraintData<?, ?> constraint, int beforeIndex, int afterIndex) {
		if (history == null || editor == null || constraint == null || beforeIndex == afterIndex) return;
		ConstraintRef<ConstraintData<?, ?>> reference = reference(history, constraint, constraint);
		history.record(label,
			() -> moveRestored(editor, reference.current, beforeIndex),
			() -> moveRestored(editor, reference.current, afterIndex));
	}

	/** Records an already-applied deletion, including unified order and every skin activation. */
	static <C extends ConstraintData<?, ?>, S> void recordDeleted(EditHistory history,
		SkeletonEditor editor, String label, C deleted, S setup, List<Skin> activations,
		int index, Operations<C, S> operations) {
		if (history == null || editor == null || deleted == null || setup == null || operations == null) return;
		if (index < 0) throw new IllegalArgumentException("Constraint history is missing the previous order");
		List<Skin> restoredSkins = List.copyOf(activations);
		ConstraintRef<C> reference = reference(history, deleted, null);
		history.record(label,
			() -> setCurrent(history, reference,
				addRestored(editor, setup, restoredSkins, index, operations)),
			() -> setCurrent(history, reference,
				removeCreated(editor, reference.current, operations)));
	}

	private static <C extends ConstraintData<?, ?>, S> C removeCreated(SkeletonEditor editor,
		C current, Operations<C, S> operations) {
		if (current == null) throw new IllegalStateException("Unable to find the created constraint");
		editor.setConstraintActivationSkins(current, List.of());
		if (!operations.delete(current))
			throw new IllegalStateException("Unable to undo constraint creation");
		return null;
	}

	private static <C extends ConstraintData<?, ?>, S> C addRestored(SkeletonEditor editor, S setup,
		List<Skin> activations, Operations<C, S> operations) {
		C restored = operations.add(setup);
		if (restored == null) throw new IllegalStateException("Unable to redo constraint creation");
		restoreActivations(editor, restored, activations);
		return restored;
	}

	private static <C extends ConstraintData<?, ?>, S> C addRestored(SkeletonEditor editor, S setup,
		List<Skin> activations, int index, Operations<C, S> operations) {
		C restored = addRestored(editor, setup, activations, operations);
		moveRestored(editor, restored, index);
		return restored;
	}

	private static <C extends ConstraintData<?, ?>, S> C updateRestored(SkeletonEditor editor, C current,
		S setup, List<Skin> activations, Operations<C, S> operations) {
		if (current == null) throw new IllegalStateException("Unable to find the edited constraint");
		C restored = operations.update(current, setup);
		if (restored == null) throw new IllegalStateException("Unable to restore constraint setup");
		restoreActivations(editor, restored, activations);
		return restored;
	}

	private static void moveRestored(SkeletonEditor editor, ConstraintData<?, ?> constraint, int index) {
		if (constraint == null) throw new IllegalStateException("Unable to find the reordered constraint");
		if (!editor.moveConstraint(constraint, index) && editor.constraintIndex(constraint) != index)
			throw new IllegalStateException("Unable to restore constraint order");
	}

	private static void restoreActivations(SkeletonEditor editor, ConstraintData<?, ?> constraint,
		List<Skin> activations) {
		editor.setConstraintActivationSkins(constraint, activations);
		if (!sameActivations(editor.constraintActivationSkins(constraint), activations))
			throw new IllegalStateException("Unable to restore constraint skin activation");
	}

	private static boolean sameActivations(List<Skin> first, List<Skin> second) {
		if (first.size() != second.size()) return false;
		for (int index = 0; index < first.size(); index++) if (first.get(index) != second.get(index)) return false;
		return true;
	}

	@SuppressWarnings("unchecked")
	private static <C extends ConstraintData<?, ?>> ConstraintRef<C> reference(EditHistory history,
		C before, C after) {
		IdentityHashMap<ConstraintData<?, ?>, ConstraintRef<?>> references =
			REFERENCES.computeIfAbsent(history, ignored -> new IdentityHashMap<>());
		ConstraintRef<C> reference = before == null ? null : (ConstraintRef<C>)references.get(before);
		if (reference == null && after != null) reference = (ConstraintRef<C>)references.get(after);
		if (reference == null) reference = new ConstraintRef<>();
		setCurrent(history, reference, after);
		return reference;
	}

	private static <C extends ConstraintData<?, ?>> void setCurrent(EditHistory history,
		ConstraintRef<C> reference, C current) {
		IdentityHashMap<ConstraintData<?, ?>, ConstraintRef<?>> references =
			REFERENCES.computeIfAbsent(history, ignored -> new IdentityHashMap<>());
		C previous = reference.current;
		if (previous != null && references.get(previous) == reference) references.remove(previous);
		reference.current = current;
		if (current != null) references.put(current, reference);
	}

	private static final class ConstraintRef<C extends ConstraintData<?, ?>> {
		C current;
	}

	private ConstraintHistory() { }
}
