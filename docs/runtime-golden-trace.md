# Physics/Slider official golden trace

这个工作流比较两个不同来源的逐帧结果：

- `official.json` 必须在官方 Spine 4.3 侧采样，作为 golden。
- `actual.json` 由 OpenSpine runtime 生成。

OpenSpine 不会用自身结果生成 expected，也不会把自比较称为官方验证。

正式 official golden 必须包含并通过以下来源校验：

- `"producer": "Spine Editor"`。
- `producerVersion`：执行采样的官方 Spine Editor 版本，例如 `4.3.23`。
- `spineVersion`：输入 runtime 数据声明的 Spine 数据版本。
- `sourceSha256`：被采样 `.json` 或 `.skel` 文件的 64 位十六进制 SHA-256。

`capture --golden` 和 `compare` 会拒绝缺少上述字段、哈希无效或 `producer` 不是
`Spine Editor` 的文件，因此 OpenSpine 的 capture 结果不能冒充官方 golden。

## Capture and compare

```bat
trace.bat capture "D:\project\hero.json" --animation walk --fps 30 --output "D:\trace\actual.json" --golden "D:\trace\official.json"
trace.bat compare "D:\trace\official.json" "D:\trace\actual.json" --abs 0.0001 --rel 0.0001
```

`capture` 未指定 `--duration` 时采样到动画时长；指定非整帧时长时会保留精确末端样本。
差异退出码为 `3`，并输出首个不同帧、该帧时间、通道、期望值、实际值和生效容差。

`.json/.skel` 输入直接使用 runtime 数据，不调用 Spine Editor。`.spine` 输入需要先由官方 Spine CLI
导出 runtime 数据，因此本机 Spine CLI 必须可用且已激活。为排除导入差异，正式 golden 测试建议让
官方侧和 OpenSpine 侧使用同一份官方导出的 runtime JSON、atlas 和图片。

当前仓库只提供官方采样格式、固定输入、OpenSpine actual 捕获和严格比较器；只有在已激活的官方
Spine Editor 中实际运行采样器并提交通过来源校验的 trace 后，才能把对应场景标记为“官方逐帧认证完成”。

`Spine Trial 4.3.06` 可打开 `.spine` 用于人工视觉对照，但其启动器没有 CLI 导入、导出、无界面推进或
逐帧状态采样参数，因此不能直接生成符合上述契约的 machine-readable official golden。正式 golden 仍需
已授权的官方 Spine Editor，以及在官方侧执行并记录这些通道的采样机制。

## Official-side sampling contract

官方侧采样器按 `fixtures/runtime-trace/official-golden-template.json` 写出 UTF-8 JSON。每帧至少记录：

- 每根骨骼 applied world pose：`worldX/worldY/a/b/c/d`。
- 每个 Physics applied pose：`inertia/strength/damping/massInverse/wind/gravity/mix`。
- 每个 Slider applied pose：`time/mix`。

名称用于匹配，数组顺序不参与匹配。`index`、`time`、`animation` 和 `fps` 也会比较。所有数值必须是有限值。
官方侧应从 Setup pose 和 Physics reset 开始，按官方约束执行顺序推进到每个样本时间；不要从上一轮编辑器
预览遗留状态继续采样。

`fixtures/runtime-trace/physics-slider-source.json` 是无图片依赖的固定输入，包含 Physics Reset、Physics
数值轨道和 Slider time/mix。它用于两侧生成各自的 trace，本身不是 golden；只有官方侧采样输出才能命名为
`official.json`。

## Required scenario matrix

每个场景保存独立的 official golden，且至少覆盖：

1. Physics 约束分别使用 30、60 和非默认 step。
2. 目标时间不是 Physics step 整数倍，并包含精确动画末端。
3. Physics Reset timeline 前一帧、Reset 帧和后一帧。
4. 循环播放跨动画末帧到 0 的边界。
5. Slider 驱动含 Physics timeline 的另一个动画。
6. Slider 目标为当前活动动画，验证官方的自引用抑制行为。

Golden 文件应和工程 fixture 一起版本控制。更新 golden 必须说明所用官方 Spine 版本和为什么预期结果变化。
