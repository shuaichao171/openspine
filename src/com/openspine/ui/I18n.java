package com.openspine.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;

/** Runtime language selection and shared translations for editor interface text. */
public final class I18n {
	/** Interface languages supported by the editor. */
	public enum Language {
		CHINESE("zh", "中文"), ENGLISH("en", "English");

		public final String code;
		public final String displayName;

		Language(String code, String displayName) {
			this.code = code;
			this.displayName = displayName;
		}

		/** Parses a stored language code; unknown or blank codes fall back to the supplied default. */
		public static Language fromCode(String code, Language fallback) {
			if (code == null || code.isBlank()) return fallback;
			String normalized = code.trim().toLowerCase(Locale.ROOT);
			for (Language language : values()) {
				if (language.code.equals(normalized)) return language;
			}
			return fallback;
		}
	}

	private static volatile Language current = Language.CHINESE;
	private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private static final Map<String, String> ENGLISH = new LinkedHashMap<>();
	private static final Map<String, String> CHINESE = new LinkedHashMap<>();

	static {
		add("项目", "Project");
		add("新建项目", "New Project");
		add("打开项目", "Open Project");
		add("最近项目", "Recent Projects");
		add("保存项目", "Save Project");
		add("项目另存为", "Save Project As");
		add("打包项目", "Package Project");
		add("新建骨架", "New Skeleton");
		add("导入项目", "Import Project");
		add("导入动画", "Import Animation");
		add("导出项目", "Export Project");
		add("打开", "Open");
		add("保存", "Save");
		add("另存为", "Save As");
		add("撤销", "Undo");
		add("重做", "Redo");
		add("选择", "Select");
		add("移动", "Move");
		add("旋转", "Rotate");
		add("缩放", "Scale");
		add("缩放 X", "Scale X");
		add("缩放 Y", "Scale Y");
		add("剪切 X", "Shear X");
		add("剪切 Y", "Shear Y");
		add("反射", "Reflection");
		add("顶点", "Vertex");
		add("变形", "Deform");
		add("权重", "Weight");
		add("姿态", "Pose");
		add("界面", "Interface");
		add("骨骼属性", "Bone Properties");
		add("附件皮肤", "Attachments & Skins");
		add("骨架", "Skeleton");
		add("全部", "All");
		add("骨架层级", "Hierarchy");
		add("筛选骨骼、槽或附件", "Filter bones, slots, or attachments");
		add("搜索骨架", "Search skeleton");
		add("未选择", "No selection");
		add("重置", "Reset");
		add("清理", "Clean");
		add("清理动画", "Clean Animation");
		add("设为 Setup", "Set as Setup");
		add("长度", "Length");
		add("继承", "Inherit");
		add("继承项", "Inherited");
		add("皮肤", "Skin");
		add("不要求", "Not required");
		add("要求", "Required");
		add("必需", "Required");
		add("仅平移", "Translation Only");
		add("无旋转或反射", "No Rotation or Reflection");
		add("无缩放", "No Scale");
		add("无缩放或反射", "No Scale or Reflection");
		add("附件与皮肤", "Attachments & Skins");
		add("附件 & 皮肤", "Attachments & Skins");
		add("关联槽", "Related Slots");
		add("+ 槽", "+ Slot");
		add("槽名称", "Slot Name");
		add("删除…", "Delete...");
		add("插槽", "Slot");
		add("槽属性", "Slot Properties");
		add("颜色", "Color");
		add("双色染色", "Two-color Tint");
		add("暗色", "Dark Color");
		add("混合", "Blend");
		add("正常", "Normal");
		add("叠加", "Additive");
		add("正片叠底", "Multiply");
		add("滤色", "Screen");
		add("可见", "Visible");
		add("隐藏", "Hidden");
		add("图片", "Icon");
		add("图标旋转", "Icon Rotation");
		add("图标名称（可选）", "Icon name (optional)");
		add("应用外观", "Apply Appearance");
		add("新建...", "New...");
		add("父级", "Parent");
		add("拆分", "Split");
		add("皮肤...", "Skin...");
		add("骨骼名称", "Bone Name");
		add("视口", "Viewport");
		add("可见性", "Visibility");
		add("应用槽属性", "Apply Slot Properties");
		add("附件", "Attachment");
		add("创建 / 编辑附件", "Create / Edit Attachment");
		add("类型", "Type");
		add("源网格", "Source Mesh");
		add("继承 Deform", "Inherit Deform");
		add("附件名称", "Attachment Name");
		add("添加 / 替换", "Add / Replace");
		add("编辑网格", "Edit Mesh");
		add("图片序列…", "Image Sequence...");
		add("删除当前", "Delete Current");
		add("应用到附件", "Apply to Attachment");
		add("染色", "Tint");
		add("宽", "Width");
		add("高", "Height");
		add("皮肤名称", "Skin Name");
		add("+ 空白", "+ Blank");
		add("动画", "Animation");
		add("+ 新建", "+ New");
		add("复制", "Duplicate");
		add("重命名", "Rename");
		add("删除", "Delete");
		add("事件定义", "Event Definitions");
		add("IK 约束", "IK Constraints");
		add("平移 XY", "Translate XY");
		add("缩放 XY", "Scale XY");
		add("剪切 XY", "Shear XY");
		add("轨道", "Tracks");
		add("全部轨道", "All Tracks");
		add("选中骨骼", "Selected Bone");
		add("已有关键帧", "Keyed Only");
		add("视图", "View");
		add("摄影表", "Dopesheet");
		add("关键帧", "Keyframes");
		add("自动关键帧", "Auto Key");
		add("记录姿态", "Record Pose");
		add("曲线", "Graph");
		add("XY 分离", "Separate XY");
		add("时间缩放", "Time Zoom");
		add("搜索骨骼、槽或全局轨道", "Search bones, slots, or global tracks");
		add("未选择关键帧", "No keyframe selected");
		add("时间", "Time");
		add("数值", "Value");
		add("线性", "Linear");
		add("步进", "Stepped");
		add("贝塞尔曲线", "Bezier Curve");
		add("Graph 编辑器", "Graph Editor");
		add("下方曲线", "Graph Below");
		add("离散切换", "Discrete");
		add("顶点偏移", "Vertex Offsets");
		add("编辑...", "Edit...");
		add("事件", "Event");
		add("载荷…", "Payload...");
		add("播放", "Play");
		add("暂停", "Pause");
		add("复位", "Reset");
		add("循环", "Loop");
		add("循环 ✓", "Loop ✓");
		add("上一帧", "Previous Frame");
		add("下一帧", "Next Frame");
		add("帧", "Frame");
		add("设置", "Settings");
		add("确定", "OK");
		add("取消", "Cancel");
		add("完成", "Done");
		add("名称", "Name");
		add("应用", "Apply");
		add("创建", "Create");
		add("关闭", "Close");
		add("开启", "On");
		add("平滑", "Smooth");
		add("像素", "Pixel");
		add("恢复默认布局", "Reset Default Layout");
	}

	private I18n() {}

	public static Language language() {
		return current;
	}

	/** Switches the interface language and notifies listeners once when the language actually changes. */
	public static void setLanguage(Language language) {
		if (language == null) throw new IllegalArgumentException("language is required");
		if (current == language) return;
		current = language;
		for (Runnable listener : listeners) listener.run();
	}

	/** Returns the text for the active language. English falls back to Chinese while untranslated. */
	public static String t(String chinese, String english) {
		if (current == Language.ENGLISH && english != null && !english.isBlank()) return english;
		return chinese;
	}

	/** Matches either localized representation, useful while controls are being relabeled. */
	public static boolean matches(String value, String chinese, String english) {
		return value != null && (value.equals(chinese) || value.equals(english));
	}

	/** Translates a shared exact interface label in either direction. Unknown text is left intact. */
	public static String t(String text) {
		if (text == null || text.isEmpty()) return text;
		return current == Language.ENGLISH ? ENGLISH.getOrDefault(text, text)
			: CHINESE.getOrDefault(text, text);
	}

	/** Applies shared exact translations to labels and text-field hints in an existing actor tree. */
	public static void localize(Actor actor) {
		if (actor == null) return;
		if (actor instanceof Label label) label.setText(t(label.getText().toString()));
		if (actor instanceof TextField field) field.setMessageText(t(field.getMessageText()));
		if (actor instanceof Group group)
			for (Actor child : group.getChildren()) localize(child);
	}

	/** Registers a relabel callback fired after every language change. */
	public static void addListener(Runnable listener) {
		if (listener == null) throw new IllegalArgumentException("listener is required");
		listeners.add(listener);
	}

	public static void removeListener(Runnable listener) {
		listeners.remove(listener);
	}

	private static void add(String chinese, String english) {
		ENGLISH.put(chinese, english);
		CHINESE.putIfAbsent(english, chinese);
	}
}
