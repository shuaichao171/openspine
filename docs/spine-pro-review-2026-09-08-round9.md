# Spine Pro 对照审查：第九轮

日期：2026-09-08。延续[第八轮](spine-pro-review-2026-09-08-round8.md)，补齐视区显示开关、约束设置复制粘贴，并修复交互与跨类型编辑历史问题。

## 对照依据

实际参考文件为 `spine_jar/Spine Pro/Pro_4.3.23.jar`，49,768,720 字节，SHA-256：

`00f922595d3b9eb62e14b71a3d2f00fc6c492b7de57b20fe7dfe1207a8abd8b5`

本轮读取参考 JAR 的 `editor/languages/chinese-sc` 语言资源，确认 `Hide viewport selection`、`Show inactive skin bones`、`Inactive skin bones will also be shown in the viewport`、`Constraint settings copied.` 和 `Constraint settings pasted.`。这些是公开功能证据，不代表已验证官方内部实现或数值一致性。未启动参考软件。

## 功能与修复

| 项目 | 修改后的行为 |
| --- | --- |
| 隐藏视区选择 | 菜单、设置及默认 Shift+H 切换；保留实际选择，隐藏附件轮廓、选中着色和变换 Gizmo；切换前取消骨骼、Mesh、Deform、权重手势。隐藏手柄和空白处不会拖动选中对象；可继续点选实际可见对象 |
| 非活动皮肤骨骼 | 菜单、设置及默认 Shift+J 切换；使用独立参考骨架，将非活动分支的 Setup 姿态放到活动祖先当前受约束世界矩阵下，避免沿用上一皮肤的过期动画姿态；默认灰色，不参与视区拾取或拖动 |
| 特殊视区状态提示 | 视区顶部持续显示当前骨架约束禁用、隐藏选择、非活动骨骼参考、翻转、缩放锁定和像素吸附状态；文本随宽度换行且鼠标事件透传，普通状态下不显示 |
| 五类约束设置复制/粘贴 | IK、Transform、Path、Physics、Slider 均可复制已验证的当前草稿；会话内同类粘贴，按名称重新绑定目标文档的骨骼、槽和动画；先验证整组设置，失败时不改变草稿 |
| 骨骼点击不准确 | 长度线段、镜像/缩放后的线段及菱形图标按实际显示几何计算命中；移除未绘制的旧十字命中区，避免拦截空白点击 |
| 皮肤重做引用失效约束 | 创建皮肤约束后重命名或复制皮肤，连续撤销/重做会创建新的约束对象；恢复皮肤前按类型与名称重新绑定当前约束。缺少或歧义依赖时先拒绝，避免留下无法导出的作者数据 |
| Slider 时间精度漂移 | 秒数显示为项目帧数再保存时，不再因 float 往返换算改变未编辑的原秒值；复制到不同 FPS 项目仍保留实际秒值和驱动映射 |
| 设置页高度不足 | 视口设置采用竖向滚动，1280×720 下可访问底部开关；切换开关重建页面时保留滚动位置 |

隐藏选择只控制选择装饰；Mesh/Deform 顶点和权重热力内容仍用于操作，其中选中点的特殊高亮隐藏。非活动骨骼仅作为当前编辑骨架的只读参考，取景使用相同参考位置；不会修改真实骨架的 Pose、AppliedPose、皮肤激活、约束求值缓存或作者数据。关闭骨骼显示时不绘制该参考。

约束剪贴板只保存不可变数值与引用名称，不持有源文档的运行时骨骼、槽、动画对象。粘贴保留目标名称及当前皮肤激活；复制设置关闭“皮肤必需”时清除激活。粘贴不提前写入项目，点击“应用”后产生一次历史记录，直接关闭可放弃草稿。跨类型、缺失引用及无效设置均会说明失败原因。

## 验证

统一构建成功，**88 组 Java 回归测试全部通过**。完整日志：`build/review-9-tests.txt`。新增 `ConstraintClipboardTest`、`InactiveSkinBonesTest`、`SkinConstraintHistoryTest`，扩展视区、快捷键与 Slider 对话框测试。

皮肤/约束历史失败用例先在旧实现复现，再验证修复：覆盖重命名和复制、三轮完整撤销/重做、导出 JSON 完全恢复、保存点恢复及缺少依赖时历史和作者数据不变。

最终构建输入 SHA-256：

`9ffb14e4e7efa5cfef2995c862045cd66570cb06f87a4dd0c35d48b3e66a5694`

统一构建后执行两组隐藏 GL 验证，调用隔离进程内部的实际 Scene2D 输入处理与按钮，导出实际帧缓冲截图：

- 视区：1280×720、1920×1080；四类编辑手势取消、隐藏 Gizmo 与空白拖动禁用、非活动参考不改变真实姿态且不可拾取、菜单与实际 Stage 路由的 Shift+H/J、设置滚动保持、状态提示输入透传，以及第二个独立编辑器实例恢复开关偏好。16 张截图在 `artifacts/spine-pro-review-9/viewport/`，日志 `build/review-9-viewport-ui.txt`，脚本 `scripts/visual/ViewportVisibilityCapture.java`。
- 约束：1280×720、1600×1000；五类对话框的复制/粘贴/应用按钮、单步撤销/重做、保留目标名与皮肤激活、粘贴不改 Setup/dirty/history、无效复制不覆盖剪贴板、错误粘贴的原子拒绝，以及 24 FPS 下 `0.1234567f` 秒精确保留。10 张截图在 `artifacts/spine-pro-review-9/constraints/`，日志 `build/review-9-constraints-ui.txt`，脚本 `scripts/visual/ConstraintClipboardCapture.java`。

已阅图确认新控件、参考骨骼、隐藏选择效果与状态条布局。验证进程自动退出，视区验证的独立用户/偏好/自动保存目录已清理；没有操作用户已有窗口。

## 可运行版本

程序：`publish-candidate/spine-pro-review-9/OpenSpine/OpenSpine.exe`，自带 Java。版本指纹为 **0.2.91+9ffb14e4e7ef**。

压缩包：`publish-candidate/spine-pro-review-9/OpenSpine-0.2.91-win64.zip`，59,443,557 字节（约 56.69 MiB），SHA-256：

`259cbcf34b2af84c0c021820129df272a89cd34a9efcbf98aa3029a449d9d657`

已核对发布 JAR 的命令行版本、压缩包与 `.sha256` 文件一致，以及发布重建与完整回归使用相同输入指纹。发布脚本检查测试类没有进入程序 JAR；本次不重复执行已通过的 88 组测试。发布日志：`build/review-9-release.txt`。

## 尚需补齐

| 项目 | 当前边界 |
| --- | --- |
| PSD 同步 | 来源映射已存在，按图层映射更新图片/附件的同步流程仍未实现 |
| 像素网格 | 已有背景网格和 Region 像素吸附，尚无专门像素网格 |
| 通用补偿 | Setup 改父级补偿已有受限实现；全动画补偿和更多旋转/缩放坐标系仍需设计 |
| 复杂网格拓扑 | 自动轮廓支持单一无孔轮廓；孔洞、多岛和点接触拓扑仍需完善 |
| 官方数值对照 | Physics/Slider 仍需真实官方编辑器逐帧 golden trace 验证 |

本轮完成上述可验证的功能和修复，不声称已与 Spine Pro 完全一致。
