# JSON skins 导出兼容性修复

核验日期：2026-10-13。

## 已确认的问题

`SkeletonExporter` 声明导出 Spine 4.3 JSON，但将 `skins` 写成按名称索引的对象。
每个值已经包含 `name`、`attachments` 等新格式字段；错误在外层容器。
修复前的 `SkinJsonExportTest` 在 `skins.isArray()` 断言上失败。

libGDX 的 `JsonValue.getChild` 返回首个子节点，对数组和对象都适用。
官方 Java `SkeletonJson` 用它遍历皮肤，因此能读回这种错误导出。
官方 TypeScript 读取器按 `root.skins.length` 遍历，对象型皮肤会被跳过。
本地使用随仓库分发的官方 Spine Player 4.3.5 中的 `SkeletonJson` 已复现：
把内置示例导出的皮肤数组改回对象后，读取成功，但得到零个皮肤、没有默认皮肤。
负例清空了动画，以单独验证皮肤静默丢失；有附件动画引用时也可能抛错，不能一概称为静默失败。

HTML 导出以前自行把皮肤转换成数组，因而它的既有测试没有覆盖普通 JSON 导出的问题。

## 对转述报告的修正与核验范围

- **3.8 已使用数组**，不能将对象型皮肤称为“3.8 容器”，也不能把版本分界写成“4.0+ 才使用数组”。
- 本仓库直接从 `SkeletonData.getSkins()` 序列化，修复不涉及把 Java `Map<String, Skin>` 改成别的内部类型。
- 本地未找到转述中的 `tmp/openspine-skins-bug-report.md`，也没有拿到包含 260 个附件的原始文件或 Rust 解析器。
  因此未独立复核其行号、84 bones / 62 slots 等统计、Rust 报错或“46 symbols / 1 bank”导入结果。
- 缺少原始文件，不能确认“其余部分完全符合 Spine 4.3、没有其他问题”。
  官方 4.3 读取器确实支持带 `type` 的统一 `constraints` 数组，本次保留其序列化方式。

## 修复

- 共享 JSON 导出器输出 `"skins": [{"name": "default", "attachments": {...}}]`。
- 没有皮肤时输出 `[]`；保留皮肤顺序、显式名称、空皮肤、颜色、骨骼/约束引用和附件内容。
- HTML 导出直接使用共享导出器的结果，不再单独修补皮肤容器。
- 旧 OpenSpine 对象型文件仍能读取，重新导出后得到数组；不会自动改写磁盘上的旧文件。

## 可重复验证

```powershell
.\build.bat
java -cp "bin;lib/*" com.openspine.io.SkinJsonExportTest
node scripts/test-json-export.cjs
```

Java 测试明确断言容器类型、顺序、名称和附件数，再检查重新加载、再次导出的完整 JSON 一致性。
测试类会被现有发布脚本自动发现。Node 脚本调用该测试生成导出文件，再用官方 TypeScript 读取器读取，
无需网络、npm 安装或浏览器。它检查皮肤与附件数、默认皮肤选择、皮肤骨骼/约束、Linked Mesh 引用、
Deform/Sequence 时间线和动画时长，并包含把数组故意改回对象的负例。
输出文件位于已忽略的 `build/skin-json-check/`。

覆盖六组输入：无皮肤、空默认皮肤、单个默认皮肤、单个非默认皮肤、含特殊字符名称和空皮肤的多皮肤、
旧格式内置示例。图像附件使用无纹理桩，此项验证不等同于渲染验证或完整 Spine JSON schema 校验。
原有 `runtime-trace` 检查可继续验证动画采样，不能代替这里的 JSON 容器断言。

本次执行结果：完整构建成功，91 个 Java 测试类全部通过，六组 TypeScript 回读及错误容器负例全部通过，
`git diff --check` 通过。多皮肤样例保留 3 个皮肤、4 个附件和 1 个动画；旧格式内置示例重新导出后保留
2 个皮肤、12 个附件和 3 个动画。没有修改 UI，因此未启动编辑器或操作桌面窗口。

## 官方源码依据

- [libGDX 1.14.2 JsonValue.java](https://github.com/libgdx/libgdx/blob/1.14.2/gdx/src/com/badlogic/gdx/utils/JsonValue.java)：`getChild` 返回首个子节点。
- [Spine 4.3 Java SkeletonJson.java](https://github.com/EsotericSoftware/spine-runtimes/blob/4.3/spine-libgdx/spine-libgdx/src/com/esotericsoftware/spine/SkeletonJson.java)：遍历 `root.getChild("skins")`。
- [Spine 4.3 TypeScript SkeletonJson.ts](https://github.com/EsotericSoftware/spine-runtimes/blob/4.3/spine-ts/spine-core/src/SkeletonJson.ts)：使用皮肤数组长度，并支持带 `type` 的约束。
- [Spine 3.8 TypeScript SkeletonJson.ts](https://github.com/EsotericSoftware/spine-runtimes/blob/3.8/spine-ts/core/src/SkeletonJson.ts)：已经按皮肤数组长度遍历。

远程分支可能继续更新；可执行回归测试固定使用本仓库的 4.3.5 运行时。
