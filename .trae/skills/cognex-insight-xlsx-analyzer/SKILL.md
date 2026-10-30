---
name: "cognex-insight-xlsx-analyzer"
description: "Parses Cognex In-Sight smart-camera job exports (.jobx or exported .xlsx '单元格' sheet, Spreadsheet jobs) and generates detailed JS pseudocode (.js), explanation doc (.md), and a dependency-free single-file HTML (.html, images/SVG base64-embedded) for customers/debuggers. Invoke when user asks to analyze/convert/interpret a Cognex In-Sight camera program, or asks how a measurement/judgment chain works."
---

# Cognex In-Sight xlsx/jobx Analyzer（康耐视智能相机表格作业分析）

将 **Cognex In-Sight**（如 IS8912CX，固件 25.x）电子表格作业（Spreadsheet）导出的
**xlsx（或 .jobx 导出的 xlsx）** 解析为三份交付物，写入 xlsx 同目录：

1. **`<同名>.js`** —— JS 伪代码（每个用户格 = 一个变量，表达式即右侧公式）
2. **`<同名>.md`** —— 程序说明文档（概览表 / 流程图 / 测量明细 / PLC 接口 / 风险点）
3. **`<同名>-singlefile.html`** —— 自包含交互式详解页（所有图片/SVG 以 base64 内嵌，
   可直接存档发客户，离线打开）；若目录有实拍 jpg/png/svg 叠图则自动收编进画廊

> ⚠️ 平台是 **Cognex In-Sight**，不是 VisionMaster，文案中不要写错。

## 触发场景
- 用户给出 `xlsx_out\<工位>\<名字>.xlsx`（或 .jobx 导出件）要求"分析/讲解程序"
- 用户要求"生成 js + md + html（singlefile）"三件套
- 用户询问某个测量项/通讯/判定链的工作原理，或报告相机误判需排查逻辑

## xlsx 结构约定
- 目标 sheet 名：**单元格**；列：A=单元格地址("A3")，B=名称，C=值，D=表达式，E=注释
- 字符串字面量在 C 列存为 `"文本"`（带引号），在 D 列存为 `'文本`（前缀单引号）
- 每个逻辑格在其下方 ~26 物理行重复 → 去重 key 用 `地址|表达式`
- 段落标题（"1、以太网/IP模块通讯" 等）出现在 C 列，格式 `"^\d+[、.]…"`

## 工作流程
1. **段落扫描**：`python scripts/dump_xlsx.py <xlsx>` → 得到 sheet 行号段落表
2. **分块解析**：按段落区段循环 `python scripts/dump_expr.py <xlsx> <start> <end>`
   - 需要看原始行（含显示格/绘图格）时用 `dump_rows.py <xlsx> <start> <end> [maxcol]`
   - 每块输出重定向到临时文件阅读（中文输出用 UTF-8）
3. **识别关键链**：
   - PLC 通讯：`FormatInputBuffer`/`ReadUserDataBuffer`/`FormatOutputBuffer`/`WriteResultsBuffer`
   - 条件号分支：`GetBufferData` → 条件号 → `If(自动/手动…)` 三个程序段 11/21/31
   - 定位链：`TrainPatMaxRedLine` ↔ `FindPatMaxRedLine` → `CircleFromNPoints`
   - 测量链：`FindLine`/`DetectBlobs`/`FindBlobs` → `PointToPoint`/`LineToLine`/`PointToLine`
   - 平行度：`LineToLine` → `GetAngle` → `Abs` / `180-Abs` 归一化 → `InRange(0, 上限)`
   - 判定：`InRange` → `And` → `If(Switch(条件号,…),1,2)` 总结果
   - 隐患：**ErrFree** 把 Error 变 0（可能吞掉失败判 OK）
4. **写 js**：段落注释 + `A1 = 表达式 // 值` 风格，含通讯/段控制/定位/三项检测/输出/存图
5. **写 md**：概览表、流程图、测量项明细表(值+区间+像素精度)、PLC 接口、风险点
6. **写 html**：生成详解页（导航 + 流程 + 各模块表格 + 代码块 + 图片画廊），
   再用 `python scripts/build_singlefile.py <html>` 把引用的本地图片/SVG 全部 base64
   内嵌（SVG 内 `xlink:href` 指向的 jpg 一并内嵌），产物为 `-singlefile.html`

## singlefile HTML 模板要点
- 页眉注明：源 .jobx / Job 版本 / 相机型号（Cognex In-Sight，如 IS8912CX + 固件）/
  工位 / 产品型号 / 条件号与程序段
- 章节：总体流程 → PLC 通讯 → 程序段控制 → 检测模块（定位/检查①②③）→
  ★平行度（或该工位核心测量）详解 → 结果统计/存图/输出 → 实拍图与叠图画廊
- 判定区间用表格列出（测量名 / 公式链 / 区间 / 工程含义）
- 图片画廊收编同目录 `*.jpg`（OK/NG 实拍）与 `*.svg`（In-Sight 叠图导出），
  文件名解读规则（时间_OK/NG_二维码_条件号_计数）写进"存图"一节
- 重打包脚本：`python scripts/build_singlefile.py <html路径>`

## 典型陷阱
- Windows shell：**不要** `python -c` 内联中文 → 必须 `python 脚本.py`；中文输出可能
  GBK 乱码，读写文件显式 `encoding='utf-8'`
- `Range(...)` 括号里的 Excel 引号字符串常量（如 `'11`）即注释值
- 角度折角两种写法：`If(Abs(M)<90,Abs(M),180-Abs(M))`（正确）与
  缺分支的 `180-Abs(M)`（±180° 翻转误判，如 OP70-241 A170 型 Bug）
- 相机可能**不判总结果**（B11 恒 1/A12 注释），判定在 PLC 端，md/html 中要注明
- `GetAngle` 在 FindLine/PointToPoint/LineToLine 中语义方向不同，注释要区分
- SVG 叠图内嵌后其 `xlink:href="xxx.jpg"` 相对引用会失效 → 打包脚本须先替换为
  base64 data URI，再整体 base64 进 `<img>`

## 交付物核对清单
- [ ] `<同名>.js`：与段落顺序一致，包含通讯/段控制/定位/检测/输出/存图
- [ ] `<同名>.md`：概览表、流程图、测量明细(name/值/区间)、**风险点**、PLC 接口映射
- [ ] `<同名>-singlefile.html`：无外部 `src`（grep 校验 `src="(?!data:)` 为 0），
  图片+SVG 全内嵌，页眉平台写 Cognex In-Sight
- [ ] 三者都放在 xlsx 相同文件夹下，命名与 xlsx 去扩展名一致（html 加 `-singlefile`）
- [ ] A9/A12 输出顺序混乱需在 md/html 中显式列出（PLC 端按序解读）

## 参考脚本
| 脚本 | 用途 |
|---|---|
| `scripts/dump_xlsx.py` | 段落标题 + 所在行 |
| `scripts/dump_expr.py` | 行区间内去重后的 `地址 = 值  <-  表达式` |
| `scripts/dump_rows.py` | 行区间原始 5 列文本（截断 150 字符） |
| `scripts/build_singlefile.py` | 把 html 引用的本地 jpg/png/svg 全部 base64 内嵌，生成 singlefile |

## 已知分析产物（D:\JustSmart\261028…SAB\CCD作业-SAB\xlsx_out 下）
- OP70-241（平行度/缺口角）：条件号 11(0-) 与 21(1-) 两个版本，含 singlefile html 范例
  `OP70-241\SAB-OP70-141-相机检测程序详解-singlefile.html`
- OP70-242（点火管缺口 6 段切换）
- OP10-28（9 项尺寸测量，相机不判总结果）
同型 bug 家族：ErrFree 吞错 / 折角分支缺失 / 短基线敏感 / PatMax ±180° 翻转。
