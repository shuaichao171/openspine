package com.openspine.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.esotericsoftware.spine.ConstraintData;
import com.openspine.model.EditHistory;
import com.openspine.model.SkeletonEditor;

/** Shared controls for Spine's unified cross-type constraint execution order. */
final class ConstraintOrderControls {
	record Controls(Label position, TextButton up, TextButton down) {}

	static Controls add(Table table, UiKit kit, Runnable moveUp, Runnable moveDown) {
		Label position = kit.controlLabel("-");
		TextButton up = new TextButton("^", kit.toolButton);
		TextButton down = new TextButton("v", kit.toolButton);
		up.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { moveUp.run(); }
		});
		down.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { moveDown.run(); }
		});
		table.add(position).width(42).right().padLeft(6).padRight(3);
		table.add(up).width(32).height(27).padRight(3);
		table.add(down).width(32).height(27);
		return new Controls(position, up, down);
	}

	static void refresh(Controls controls, SkeletonEditor editor,
		ConstraintData<?, ?> constraint, boolean draftClean) {
		int index = editor.constraintIndex(constraint);
		int count = editor.doc.data == null ? 0 : editor.doc.data.getConstraints().size;
		controls.position().setText(index < 0 ? "-" : (index + 1) + "/" + count);
		controls.up().setDisabled(!draftClean || index <= 0);
		controls.down().setDisabled(!draftClean || index < 0 || index >= count - 1);
	}

	static boolean move(EditHistory history, SkeletonEditor editor, ConstraintData<?, ?> constraint,
		int delta, String label) {
		int beforeIndex = editor.constraintIndex(constraint);
		int afterIndex = beforeIndex + delta;
		if (beforeIndex < 0 || editor.doc.data == null
			|| afterIndex < 0 || afterIndex >= editor.doc.data.getConstraints().size) return false;
		if (!editor.moveConstraint(constraint, afterIndex)) return false;
		ConstraintHistory.recordMoved(history, editor, label, constraint, beforeIndex, afterIndex);
		return true;
	}

	private ConstraintOrderControls() { }
}
