package com.openspine.view;

import com.openspine.ui.I18n;

/** 编辑器工具模式 */
public enum EditorTool {
	SELECT("选择", "Select"),
	ROTATE("旋转", "Rotate"),
	MOVE("移动", "Move"),
	SCALE("缩放", "Scale"),
	SHEAR("倾斜", "Shear"),
	MESH("顶点", "Vertex"),
	DEFORM("变形", "Deform"),
	WEIGHT("权重", "Weight");

	public final String label;
	private final String english;

	EditorTool(String label, String english) {
		this.label = label;
		this.english = english;
	}

	public String localizedLabel() {
		return I18n.t(label, english);
	}
}
