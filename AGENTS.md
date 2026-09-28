# AGENTS.md — Cognex Jobx 工具箱 (CognexJobxBackupTool)

> 项目说明（功能特性、技术栈、构建运行、代码结构、安全注意事项等面向用户的内容）统一维护于 [README.md](README.md)，本文件仅记录开发代理执行任务时需要遵守的内部规范与红线。

## 本机构建命令

机器默认 JDK 25，Gradle 8.4 不支持；必须用项目内便携 JDK 17（`.tools/jdk17`，不入库）：

- PowerShell：`$env:JAVA_HOME="<项目根>\.tools\jdk17"; .\gradlew.bat build`
- Git Bash：`JAVA_HOME="$PWD/.tools/jdk17" ./build-with-timestamp.sh`

`build` 自动触发 `fatJar`，产物在 `build/libs/`：`cognex-jobx-backup-1.0.0-all_<时间戳>.jar`（fat jar，约 24 MB）+ `cognex-jobx-backup-1.0.0.jar`（瘦 jar）。`build.gradle` 已为 `JavaCompile` 显式设置 `options.encoding = 'UTF-8'`（源码 UTF-8；默认 GBK 的 Windows 上不加会编译失败）。

发布：`build-with-timestamp.sh` 复制最新 fat jar 为根目录 `jobx文件备份助手_<时间戳>.jar`；`bundle-exe-with-timestamp.sh` 生成单文件 exe（内嵌 JRE 25 MSI + fat jar 的 WinForms 启动器）。`launcher/build-launcher.sh` 为兼容转发。launch4j 不可用：其生成的 exe 依赖已存在 JRE，无法在裸机上自我安装 JDK。

## 关键行为与约定

- 配置文件一律写入 **jar 所在目录**（`CodeSource` 定位）：备份 `backup-config.json`、导出 `export-config.json`、编辑器 `config.json`；留空的备份目录默认 jar 目录下 `backups/`，导出默认 `exports/<yyyyMMddHHmmss>/`（批量导出为 `exports/相机名称/<yyyyMMddHHmmss>/`）。
- FTP 客户端固定 UTF-8 控制编码 + autodetectUTF8，登录后 `OPTS UTF8 ON`；连接超时 10s、数据超时 30s；被动模式 + 二进制。FTPS 为显式 TLS，登录后 `PBSZ 0` / `PROT P`；"信任所有证书"默认开启；备份工具添加相机对话框默认勾选"使用 FTPS"。
- CogSocket：`ws://ip:port/ws`（新固件端口 80，旧固件 8087，模拟器随机端口）；握手固定流程 GET `system/paths/hmi` → `{root}/info` → openSession（cellNames `A0:Z599`）→ login → ready。相机在线时 loadJob/改单元格会失败，必须先 softOnline=false 且无 Explorer 编辑器附着。
- 所有相机/网络操作放后台线程，日志经 `SwingUtilities.invokeLater` 回写界面。
- **Jobx 解析器（离线）**：纯本地 TAR + 4 字节 XOR（`0x72 0x9B 0x0F 0x2E`，Cognex 官方 `DeobfuscateBytes` 硬编码常量）解析，不连相机；`.jobx` 走 `Job.json.Sheets.<name>`（inline `Byte[]` 或 `FileRef → sheets/<hash>`），`.cxdx` 走 `snippet.json`（同 XOR）；cell 数组 12 元素官方语义 `[location, expression, condition, value, name, saved, cellStyle, graphicsStyle, comment, input, output, ipProtected]`；`*.sig` 条目直接跳过，`data/*` 条目按需读入（单条上限 16 MB 防 OOM）。cell[5] `saved` 字节流若为图像（PNG/JPEG/BMP/WMF）则嵌入 xlsx 单元格；cell[6] `cellStyle` 为 CSS 风格串，含 `.cell { background-color:rgba(R,G,B,A); color:rgba(...); }` 块，导出 xlsx 时取首个 `.cell` 块的 `background-color`/`color` 应用为单元格背景与文本颜色（alpha 通道忽略，POI 颜色无 alpha）。详见 [JobxBinaryStructure.md](JobxBinaryStructure.md) §6.4。
- **Java 8 兼容红线**：source/target 1.8 且 JRE 8+ 要能启动；除 QuickJS 库自身外，业务代码禁止用 Java 9+ API（历史上已把 `CompletableFuture.failedFuture`、`orTimeout` 替换为 Java 8 实现）。POI 5.2.5 注意：`setPrintArea(5 参数)` 在 Workbook 上、纵向用 `PrintSetup.setLandscape(false)`（无 PORTRAIT 常量）；`XSSFFont.setColor(XSSFColor)` 而非 `Font.setColor(short)`；取 Font 用 `wb.getFontAt(style.getFontIndex())`。
- **QuickJS 脚本引擎**（`cn.net.zhijian.quickjs:0.2.2`，本地 jar `libs/`）运行时需要 JDK 19+（库内调用 `Thread.threadId()`）；低版本 JDK 下脚本功能给出友好错误，不影响其他功能。QuickJS 上下文为**延迟初始化**（首次运行脚本时才加载本地库），改动时不要恢复成构造即初始化。
- 代码注释中文 + 英文混合，UI 文案为中文；修改时保持该风格。
- 单模块 Gradle，无 Spring；全部逻辑在 Swing EDT 与后台线程直接实现。
- 源项目只读：`../CognexJobxExportToolGo`、`../CognexJobxExportToolZulu` 在同级目录，不要修改。

## 测试

- **没有正式的单元测试框架**（无 src/test，build.gradle 无 test 依赖）。
- 验证方式：`gradlew build` 编译通过；用 JDK 17 与 JDK 25 分别 `java -jar` 冒烟（四标签窗口能起来；JDK25 下 QuickJS 求值可用，JDK17 下脚本功能应只给友好报错而不崩溃）；相机相关端到端功能需要实机。解析器可用本仓库 `jobx/` 目录下的样本（`ExampleHmiSpreadsheetCells.jobx`、`天窗程序模板.jobx`、`Xavier标准作业模块.cxdx`）做离线冒烟。
- 项目根目录的 `jobx文件备份助手_*.jar` 发布文件及 `kimi-export-*.md` 会话导出，不属于源码，勿当作构建产物处理。
