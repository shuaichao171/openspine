# Spine Pro 对照审查：第十一轮

日期：2026-09-08。延续[第十轮](spine-pro-review-2026-09-08-round10.md)，检查外部图片刷新、PSD 合成顺序与隐藏组，以及时间线拖动和撤销的交互。

## 对照依据

参考仍为本机 `spine_jar/Spine Pro/Pro_4.3.23.jar`，49,768,720 字节，SHA-256：

`00f922595d3b9eb62e14b71a3d2f00fc6c492b7de57b20fe7dfe1207a8abd8b5`

只读取 `editor/languages/chinese-sc` 语言资源，未启动参考软件。确认了 `Reload`、缺失图片、在树中选择图片，以及外部文件修改和保存保护等文案。这些文案不足以证明官方“重新加载图片”的具体流程或状态保留规则；本轮刷新功能按 OpenSpine 的资源模型实现，不声称与官方流程完全一致。

PSD 正确性另通过公开 psd-tools 的 `group.psd`、`hidden-groups.psd` 样本与其内嵌合成图检查。它们用于证明 OpenSpine 的图层解码与合成结果，不作为 Spine Pro 的界面或数值证据。

## 新增及修复

| 问题与触发 | 修改后的行为 |
| --- | --- |
| 外部图片修改后缺少刷新入口 | 新增“项目 → 重新加载图片”，默认 `Ctrl+R`，支持快捷键自定义及旧偏好迁移 |
| 刷新可能丢失未保存编辑或当前姿态 | 从当前文档的图集或图片目录加载全新候选资源，原地更新附件区域；不重读骨架文件，不重建 Skeleton，不改变作者数据、播放位置、选择、dirty、revision 或撤销/重做栈 |
| 图集缺失时误用其他图片 | 当前明确指定的 atlas 优先，缺失时整次拒绝；没有指定图集时使用当前已编辑的图片路径，保留图片和音频的资源基目录 |
| 缺图或坏图导致刷新一半 | 先检查所有皮肤的 Region、Mesh 及 Sequence 每帧；失败释放候选，保留原图集和附件区域；采纳发生异常时恢复原区域与资源元数据 |
| PSD 导入后刷新，再撤销/重做时混用图集 | 原先导入历史只在增加 PSD 附件前捕获区域，重做会留下刷新后的部分附件。现在在整个导入成功后保存完整区域快照，重做恢复同一版图集的全部附件 |
| PSD 背景盖住前景 | PSD 文件记录与 Spine Setup 槽都按从底到顶排列，安装不再反转顺序 |
| 隐藏 PSD 组的子层错误显示 | 解析 `lsct` / `lsdk` 嵌套组标记，像素层继承所有父组的有效可见性；“忽略隐藏图层”使用继承后的状态，保留来源索引 |
| PSD 组头被误当图片或结构损坏 | 组标记不生成像素附件；缺少配对标记、无效组类型会拒绝导入。折叠组仍可见，区别于隐藏组 |
| 时间线拖动期间撤销，松手再次改动 | 摄影表关键帧与内嵌曲线把手在轨道重建、文档版本、Skeleton 或动画变化后取消旧拖动预览；后续移动和释放不覆盖恢复的数据，也不清掉重做记录 |

图片刷新期间不会插入作者历史记录。旧历史可能仍引用此前的图集或附件，因此刷新前后的图集均由文档保留至关闭；多次刷新大图集会增加显存占用。资源解码和上传在 GL 线程完成，大量图片可能短暂停顿。刷新不取消原生工程的外部修改保护，直接改动 `.openspine-generations` 中的资源后，普通保存仍可能拒绝，可另存为新工程。

PSD 组按扁平像素层列表导入。组部分不透明度不能准确地用逐子层乘 Alpha 代替，因此本轮只增加明确提示，未伪造组混合效果；蒙版、剪贴、特殊混合和完整同步仍有差异。

## 验证

统一构建后的 **90 组 Java 回归测试全部通过**，日志 `build/review-11-tests.txt`。新增 `ImageReloadTest`，覆盖来源路径、当前作者路径优先级、明确指定 atlas 的缺失保护、无来源/空目录/非法路径的状态保持，以及候选资源所有权；扩展 PSD 嵌套组、畸形组、槽顺序和新快捷键迁移回归。

最终测试构建输入 SHA-256：

`0d4406d70a6593a1d1f3b6cd65421ba878f4f257cc98b2bee9dd57f9967ff489`

PSD 独立验证也使用同一 `bin` 构建：

- `scripts/visual/PsdLayerSemanticsCheck.java` 固定两份公开 PSD 的 SHA-256，逐像素对比有效图层合成与文件自带预览。两份修正结果均为 0 像素差异；反转图层顺序分别产生 2288、2170 个不同像素。日志 `build/review-11-psd-semantics.txt`，对照图 `artifacts/spine-pro-review-11/psd/`。脚本源码包含样本来源与复现命令，正常回归使用自包含生成样本，不依赖网络。
- `scripts/visual/PsdReloadHistoryCapture.java` 在隐藏的真实编辑器进程中执行 PSD 安装、重写 PNG、打包、重新加载、两次撤销/重做，确认每个附件帧属于当前历史图集。模拟旧的安装前快照会稳定失败，修复后的完整快照通过。最终日志 `build/review-11-psd-reload-history.txt`；旧快照复现日志 `build/round11-psd-check/reload-history-before.txt`。

隐藏 GL 界面验证均使用本次任务的独立进程、用户目录、偏好和自动保存目录，不控制用户已有窗口：

- 时间线：最终统一 `bin` 优先运行 1280×720、1920×1080，各有摄影表、内嵌曲线把手及两者的直接外部修改场景，共 8 次通过。覆盖拖动期间撤销、外部修改后早于 act/rebuild 的立即释放、后续新拖动与独立撤销。脚本 `scripts/visual/TimelineHistoryCapture.java`，日志 `build/round11-timeline-{1280|1920}-{dopesheet|handle|dopesheet-external|handle-external}.txt`；最终截图复制至 `artifacts/spine-pro-review-11/timeline/`，已检查摄影表和曲线实际效果。

- 图片刷新：同一构建在 1280×720、1920×1080 分别检查外部 atlas 和散图目录，共 28 张最终 PNG。真实菜单及 Stage 快捷键入口更新了纹理和帧缓冲像素；作者 JSON、Skeleton/AnimationState 对象、骨骼姿态、实时 Deform、附件选择、时间、dirty、revision 与原有历史均保持。所有皮肤的 Region/Mesh/Sequence 帧重新绑定，旧纹理仍有效；缺少非活动皮肤的 `hidden02` 或 PNG 损坏时，关闭错误弹窗后的帧缓冲与失败前逐字节一致。脚本 `scripts/visual/ImageReloadCapture.java`，日志 `artifacts/round11-image-check/{1280x720,1920x1080}.log`，最终截图复制至 `artifacts/spine-pro-review-11/images/`。已检查菜单完整可见、刷新颜色和错误提示；日志中的错误栈来自预期负例。

验证脚本使用隐藏窗口和程序内 Scene2D 事件/帧缓冲捕获，未使用 computer-use。测试进程全部退出，测试用户目录、临时来源文件和自动保存目录已清理；`git diff --check` 通过。

## 可运行版本

程序：`publish-candidate/spine-pro-review-11/OpenSpine/OpenSpine.exe`，自带 Java。发布 JAR 的 `--version` 已确认版本为 **0.2.91+0d4406d70a65**。发布构建与全部回归、最终隐藏 GL 验证使用相同输入 SHA-256，因此打包时跳过了已通过的测试，没有改变产品源代码。

压缩包：`publish-candidate/spine-pro-review-11/OpenSpine-0.2.91-win64.zip`，59,460,213 字节（约 56.71 MiB）。已核对 ZIP 与旁边 `.sha256` 文件一致：

`e18ffd4a260f7100e061f6c809507cdacabb9dc4943d61165d4dbc9a807f4320`

发布脚本检查测试类未进入程序 JAR，并完成 MCP 发布验证。构建日志 `build/review-11-build.txt`，发布日志 `build/review-11-release.txt`。

## 尚需补齐

| 功能 | 当前边界 |
| --- | --- |
| PSD 同步及复杂合成 | 稳定图层身份、差异预览、安全更新、组透明度、蒙版及混合仍需完善 |
| 官方像素辅助 | 已有项目坐标网格，尚无 100% 像素不匹配高亮及官方特殊像素渲染行为 |
| 通用补偿 | 仍需全动画补偿，以及更多旋转/缩放坐标模式 |
| 复杂网格拓扑 | 自动轮廓仍限单一无孔轮廓，孔洞/多岛/点接触待完善 |
| 官方数值一致性 | Physics/Slider 仍需真实官方编辑器逐帧 golden trace |

本轮不声称已与 Spine Pro 完全一致。
