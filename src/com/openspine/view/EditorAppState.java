package com.openspine.view;

/** 全局编辑状态（工具模式等） */
public final class EditorAppState {
	public static volatile EditorTool tool = EditorTool.ROTATE;

	private EditorAppState() {}
}