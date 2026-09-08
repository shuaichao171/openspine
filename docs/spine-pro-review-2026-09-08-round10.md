# Spine Pro 对照审查：第十轮

日期：2026-09-08。延续[第九轮](spine-pro-review-2026-09-08-round9.md)，重点检查 PSD 导入文件安全、Graph 作者数据与视区网格。

## 对照依据

参考仍为本机 `spine_jar/Spine Pro/Pro_4.3.23.jar`，49,768,720 字节，SHA-256：

`00f922595d3b9eb62e14b71a3d2f00fc6c492b7de57b20fe7dfe1207a8abd8b5`

只读取 `editor/languages/chinese-sc` 公开语言资源，未启动参考软件。确认 PSD 设置/来源路径/移除、隐藏图层、图片提取目录等入口，以及以下像素相关文案：

- `Highlight image pixels that don't match screen pixels at 100% zoom`
- `Pixel grid limits skeleton rendering to 2048x2048.`
- `When translating, region attachments snap to screen pixels`

这些文案说明官方像素辅助还包含匹配高亮及特殊渲染处理。本轮增加的是“项目像素网格”坐标参考，不将它表述为官方像素功能的完整复刻。缺陷修复均依据 OpenSpine 的具体失败用例。

## 本轮新增及修复

| 问题与触发 | 修改后的行为 |
| --- | --- |
| 缺少像素级坐标参考 | 新增“项目像素网格”，菜单、设置及默认 Shift+P 切换并记忆偏好；世界坐标每 1 单位一条边界，叠加在图片上，独立于普通背景网格 |
| 缩小时细网格拥挤 | 两轴间距都达到 8 个实际帧缓冲像素才显示，计入 DPI；状态提示说明放大后显示。不改变图片过滤、作者数据、导出或编辑历史 |
| 极大坐标卡住渲染 | 原背景网格在相机 X=1e10 时，float 加 40 不再前进，导致循环不结束；改为 double 索引和帧缓冲坐标绘制，每轴最多 4096 条线，超出可表示范围时不绘制 |
| PSD 成功打包后立即加载失败 | 默认图集位置曾落在提取目录；成功回调清理整个目录，连刚生成的 atlas 和 PNG 页面一起删除。现在默认使用源 PSD 父目录（已有保存位置优先），清理只移除本次创建且未改变的提取 PNG |
| PSD 清理误删后来文件 | 记录提取目录和文件身份、大小、修改时间与内容摘要；生成的图集、用户新增文件/子目录、改动的 PNG 均保留，只删除空目录。手工构造结果、重复清理或旧目录复用不能取得其他文件的删除权限 |
| PSD 退出清理遗漏 | 成功提取的结果由编辑器追踪；退出先取消并等待文件线程，再清理尚未应用的结果，覆盖成功回调尚未处理以及打包设置仍打开的状态 |
| PSD 事务使用过期骨骼 | 准备图层后若换了 SkeletonData 或移除了挂载骨骼，拒绝安装；撤销及来源映射也检查所属骨架，避免将旧骨骼引用写入新骨架 |
| Graph 仅改值也改了时间 | 实际复现：锁定时间后输入数值偏移，0.1234567 秒被吸附为 0.13333334 秒。现在只对实际请求改变的时间吸附和约束，时间锁定、零偏移、零距离及“重设值”保留原子帧 |
| Graph 撤销后无法继续编辑 | 历史恢复替换 Timeline 对象，窗口曾继续引用旧对象；现在按轨道类型及属性标识寻找唯一当前轨道，保留选择与取景并刷新显示，可在同一窗口继续编辑 |
| Graph 编辑中外部修改 | 外部撤销或其他入口替换轨道后取消过期拖动预览，旧鼠标释放不会覆盖恢复的数据；删除目标、目标歧义或切换骨架时关闭失效窗口 |
| Graph 批量重定时边界 | 首尾约束同时校验，避免批量预览跨越可用区间后产生负时间或改变未移动的锚点 |

像素网格绘制使用实际帧缓冲的一像素宽矩形，支持平移、负坐标与水平翻转；单独设置 Alpha 混合，保持原画面的透明度。坐标网格不会判断一张经过旋转、缩放的图片中每个纹理像素的边界。

PSD 自动同步仍未加入：已有映射只包含像素图层索引与名称，缺少稳定的 PSD 图层 ID，无法可靠区分重排、同名及删除图层。本轮优先修复文件丢失和无效模型引用。

## 验证

统一构建后的 **89 组 Java 回归测试全部通过**，日志 `build/review-10-tests.txt`。新增 `ViewportGridTest`，扩展 PSD 解析/安装、Graph、主应用默认输出路径及新快捷键的旧偏好迁移回归。

PSD 误删图集与跨骨架安装均先在旧实现复现，再验证修复。真实执行 PSD 提取 → 图集打包 → 清理后，重新读取 atlas 描述和解码 PNG 页面成功。额外验证了新增文件、同大小同修改时间但内容已变的 PNG、目录替换和旧结果重复清理。

隐藏 GL 验证使用本次任务的独立进程、用户目录、偏好和自动保存目录，不控制用户已有窗口：

- Graph：1280×720、1920×1080，两种场景共 10 张截图；实际按钮/输入框/下拉框和指针事件验证子帧精度、保存点、撤销后继续编辑、重做、拖动中撤销、外部修改和删除目标。最终截图在 `artifacts/spine-pro-review-10/graph/`；脚本 `scripts/visual/GraphAuthoringCapture.java`；日志 `build/review-10-graph-720-ui.txt` 和 `build/review-10-graph-1080-ui.txt`。
- PSD 退出：真实编辑器分别在提取结果待处理、打包设置打开时退出，验证提取目录清理且源 PSD 保留。脚本 `scripts/visual/PsdImportLifecycleCapture.java`，日志 `build/review-10-psd-lifecycle.txt`。活跃文件线程与退出的顺序通过代码中的取消/等待检查，此脚本没有模拟每一种线程竞态。
- 像素网格：1280×720、1920×1080，最终发布构建两种尺寸均通过；脚本 `scripts/visual/PixelGridCapture.java` 验证实际图像上的一像素网格、Alpha 保持、翻转、缩小时与关闭网格相同、极大坐标出帧，以及菜单/快捷键/设置/偏好恢复和窄状态条换行与输入透传。20 张截图在 `artifacts/spine-pro-review-10/viewport/`；日志 `build/review-10-viewport-720-ui.txt` 和 `build/review-10-viewport-1080-ui.txt`。

最后一次像素网格 Alpha 混合修正晚于全量回归；发布时重新构建，针对受影响的视区测试及两种分辨率的隐藏 GL 复查，不重复无关的已通过测试。Graph 最终验证曾发现脚本未发送开关 ChangeEvent，修正验证脚本后全部通过；该脚本修正没有改变产品源码。

发布构建的 `EditorViewportTest`、`ViewportGridTest`、`InactiveSkinBonesTest` 均再次通过，日志 `build/review-10-final-viewport-tests.txt`。已检查最终截图中的网格、缩放、状态换行与 Graph 曲线。验证进程全部退出，无测试用户目录残留，`git diff --check` 通过。

## 可运行版本

程序：`publish-candidate/spine-pro-review-10/OpenSpine/OpenSpine.exe`，自带 Java。已读取发布 JAR 的命令行版本，版本指纹 **0.2.91+6721f5ceaf16**；最终构建输入 SHA-256：

`6721f5ceaf16a564984cb4158bcc567a3f28bda3a381d5e7882811c8fd31a046`

完整 89 组回归对应 Alpha 修正前的输入指纹 `f32ffef565fb8cdb62a394d39afa78c43f3e22d05f181e5b1bb7f04cfdb9b0e3`，最终版本的差异已按上述范围复查。

压缩包：`publish-candidate/spine-pro-review-10/OpenSpine-0.2.91-win64.zip`，59,453,234 字节（约 56.70 MiB）。已核对 ZIP 与旁边 `.sha256` 文件一致：

`2588edc953cae84ae58fbf8e000e90ee5bb402bb4ddf76986de83caa4c0a8313`

发布脚本检查测试类未进入程序 JAR。构建日志：`build/review-10-release.txt`。

## 尚需补齐

| 功能 | 当前边界 |
| --- | --- |
| PSD 同步 | 需补充稳定图层身份、差异预览与安全更新流程 |
| 官方像素辅助 | 本轮只有项目坐标网格；尚无 100% 像素不匹配高亮及官方特殊像素渲染行为 |
| 通用补偿 | 仍需全动画补偿，以及更多旋转/缩放坐标模式 |
| 复杂网格拓扑 | 自动轮廓仍限单一无孔轮廓，孔洞/多岛/点接触待完善 |
| 官方数值一致性 | Physics/Slider 仍需真实官方编辑器逐帧 golden trace |

本轮不声称已与 Spine Pro 完全一致。
