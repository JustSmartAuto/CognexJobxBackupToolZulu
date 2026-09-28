---
name: jobx-translate-en
description: Make an English version of a Cognex .jobx/.cxdx job via the two-phase i18n CLI (extract strings, translate, re-apply) with trained cells and data blobs byte-identical. Use when the user asks to translate or localize a jobx/cxdx for overseas delivery. Do not use for camera operations or xlsx export.
---

# Jobx 中译英交付（i18n 两阶段 CLI）

把中文开发者编写的 `.jobx` / `.cxdx` 翻译成英文版，供交付国外用户。使用本仓库 fat jar 的
`i18n-extract` → 人工/LLM 填译文 → `i18n-apply` 两阶段命令。**不需要相机，纯离线。**

## 保证（可向用户复述）

- cell[5] `saved` 训练状态从不读取或修改，逐字节保留（PatMax / Caliper / CalibrateGrid 等训练型工具无需重新训练）
- `data/*` 训练数据块、`JobValidationSet/` 等其他所有 TAR 条目逐字节复制
- 只替换四类文本：单元格 `name`、`comment`、字符串型 `value`、表达式中的中文文本
  （双引号字面量内容，以及 `'中文` 形式的整格文本常量；公式、单元格引用、函数名不动）
- AcqSettings / JobSettings / sheet 名不触碰；输出文件重算 HMAC-SHA256 `.sig`
- 输出：`{stem}_en_yyyyMMdd_HHmmss.jobx`（或 `.cxdx`），默认在源文件同目录

## 前置：定位 jar

优先用项目根目录最新的 `jobx文件备份助手_*.jar`；若不存在或版本过旧（无 i18n 子命令），
用项目内 JDK17 构建：PowerShell 执行 `$env:JAVA_HOME="$PWD\.tools\jdk17"; .\gradlew.bat build`，
然后用 `build/libs/` 下最新的 `cognex-jobx-backup-1.0.0-all_*.jar`。i18n 子命令不依赖 QuickJS，JDK 8+ 可运行。

## 步骤

### 1. 抽取

```powershell
java -jar <jar> i18n-extract "<源文件.jobx|.cxdx>" --out "<stem>.i18n.json"
```

输出 JSON：`strings[]` 每条含 `zh`（原文）、`en`（空，待填）、`occurrences`、`contexts`
（出处 sheet/location/field，field 为 name/comment/value/expr/metadata）。
控制台会打印单元格总数、带 saved 训练态单元格数、data/* 块数——把这三个数记下来，交付前核对。

### 2. 翻译（你是译者）

逐条填 `strings[].en`，规则：

- 工业视觉 HMI 风格，简洁一致：标定=Calibration、训练=Train、搜索区域=Search Region、
  合格/NG、空料=Empty Material、点胶=Glue 等；同一术语全文统一
- **必须原样保留**格式占位符（`%f` `%.1f` `%1$d` `\n` 等）、数字、单位、空格结构；
  例如 `角度: %.1f` → `Angle: %.1f`
- 表达式字面量：只译引号内文本，引号外的一切（函数名、单元格引用、运算符）不得改动；
  译文不要引入双引号；单引号文本常量（`'中文`）译后缀、保留前导 `'`
- 拿不准含义（行业黑话、缩写）就把 `en` 留空——留空等于保留中文，apply 阶段会在
  「仍有中文」清单里报告，可二次补译；不要编造
- sheet 名（如 `Inspection`）不翻译；`value` 为 `{"$type":"Error",...}` 等运行时缓存对象
  里的中文无需处理（相机首次运行即重算），apply 后这类残留属正常

### 3. 回写

```powershell
java -jar <jar> i18n-apply "<源文件>" --map "<stem>.i18n.json>" --out "<输出目录>"
# 可选：--name <前缀> 改文件名前缀；--no-sig 不写签名（默认写）
```

退出码 0 = 成功且可译字段无中文残留；1 = 有未译中文（会列出前 50 条）或执行失败；2 = 参数错误。

### 4. 验证与交付

- 核对 apply 输出：替换计数、`带 saved 单元格 N 个` 与第 1 步数字一致、
  `data/* 块 N 个逐字节复制`、`签名 已重算 (HMAC-SHA256)`
- 用解析器快速冒烟：`java -jar <jar> parse "<输出_en_*.jobx>"`，确认 cells 数与源文件一致
- 用 `parse` 导出的 xlsx 抽查若干中文单元格已变英文（名称列、批注列）
- 向用户交付 `{stem}_en_<时间戳>.jobx`；映射 JSON 可保留在同目录便于补译后重新生成

## 注意

- 不要手工解压/改 TAR；所有修改只走 i18n-apply，否则签名失效且可能破坏训练块引用
- 训练数据块内部若内嵌中文区域名（如 EditRegion），按需求原样保留，不做二进制翻译
- 多个文件批量处理时，每个文件独立 extract/apply；映射文件不跨作业复用（上下文可能不同）
