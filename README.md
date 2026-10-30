# Cognex Jobx Language Server Protocol (LSP)

Cognex In-Sight 电子表格（Spreadsheet / Jobx）脚本语言服务器，为 `.jobx` / `.cxdx` 等电子表格脚本文件提供编辑器智能支持。

## 功能特性

- **语法高亮**：针对电子表格脚本的语法着色
- **代码补全**：基于 In-Sight 电子表格函数 API 的函数名、参数补全
- **悬停提示**：函数签名与参数说明（中英文）
- **跳转定义 / 查找引用**：脚本函数与单元格引用导航
- **诊断**：脚本语法与常见用法错误检查
- **离线可用**：不依赖 Cognex 软件环境，纯本地解析

## 构建与运行

要求: **JDK 25**+, 使用 Gradle Wrapper 构建 fat jar:

```bash
./gradlew shadowJar
# 产物: build/libs/cognex-jobx-lsp-server-1.0.0-all.jar
```

运行(基于 stdio 的 LSP 通信):

```bash
java -jar build/libs/cognex-jobx-lsp-server-1.0.0-all.jar
```

编辑器(VS Code / Vim / Emacs 等)配置为启动上述命令即获得语言支持。冒烟测试:

```bash
python tools/lsp_smoke_test.py
```

## LSP 功能

- **补全**: 333 个电子表格函数、脚本 API（生命周期方法 / gr 绘制方法 / 数据结构方法 / process / console / fs / cognex Shape）、ECMAScript 5 关键字、代码片段
- **悬停**: 函数签名与中文说明(Markdown)
- **签名帮助**: 函数参数提示
- **诊断**: 括号/引号/注释配平、缺少必需的 `run` 方法、未知的 `gr.*` 绘制方法、无法解析的 `require` 目标
- 符号数据来自 `src/main/resources/functions.json`（由 `tools/extract_functions.py` 从 `CognexInsightFunctionAPI.md` 生成）与 `src/main/resources/scriptApi.json`

## 参考文档

本仓库包含以下基于 In-Sight 26.1.0 帮助文档整理的中文参考资料：

- [`COGNEX_INSIGHT_PROGRAMMING.md`](COGNEX_INSIGHT_PROGRAMMING.md) — Cognex In-Sight Spreadsheet Script 编程指南（ECMAScript 5），涵盖脚本基本结构、生命周期方法、图形绘制、模块系统、文件系统、Shape 对象及超时/内存限制
- [`CognexInsightFunctionAPI.md`](CognexInsightFunctionAPI.md) — Cognex In-Sight 3D 相机电子表格函数参考，按类别收录 2D/通用函数（VisionTools，290 页）及 3D 视觉函数的完整参数说明

## 分支

本仓库通过 Git 分支组织不同子项目，`jobx-lsp-server` 分支为语言服务器（LSP）项目：

- `jobx-lsp-server`：Jobx 语言服务器
- `parser-feature`：Jobx 解析器（离线 `.jobx`/`.cxdx` 转 `.xlsx`）

```bash
git clone -b jobx-lsp-server https://github.com/JustSmartAuto/CognexJobxBackupToolZulu.git
```

## 许可证

本项目基于 [MIT 许可证](LICENSE) 开源，版权所有 (c) 2026 JustSmartAuto。
