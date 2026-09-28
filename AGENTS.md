# AGENTS.md — Cognex Jobx 工具箱 (CognexJobxBackupTool)

## 项目概述

一个基于 Java Swing 的桌面工具（单模块 Gradle 项目，根项目名 `cognex-jobx-backup`，group `com.cognex.backup`），主窗口为 `JTabbedPane` 四个标签页：

1. **Jobx 备份工具**（`com.cognex.backup`）：通过 FTP/FTPS 从 Cognex 视觉相机递归下载 `.jobx` 及 `.jobx.sig`，按 `备份目录/相机名称/yyyyMMddHHmmss/` 时间戳归档。多相机配置管理（增删改，自动保存 JSON）、单个/全部备份、界面实时日志。
2. **Jobx 导出工具**（`com.cognex.export`，移植自 ../CognexJobxExportToolGo）：FTP/FTPS 递归枚举相机 `.jobx` → CogSocket HMI 登录（自动切离线）→ 逐个 loadJob、读全部单元格值+表达式 → 每个 jobx 生成一个 A4 打印友好 xlsx（sheet "单元格" + sheet "位置布局"），结束恢复原作业与在线状态。
3. **Jobx 编辑器**（`com.cognex.insight`，移植自 ../CognexJobxExportToolZulu / java-insight-hmi）：CogSocket HMI 客户端——实时图像、电子表格单元格查看/双击编辑（值/表达式）、手动触发、在线/离线/实时模式、相机信息、XML 导出、QuickJS JavaScript 脚本编辑器、深色/浅色主题、配置持久化。
4. **Jobx 解析器**（`com.cognex.parser`，离线）：拖入或打开 `.jobx` / `.cxdx` 文件 → 纯本地 TAR + XOR 解析（不连相机）→ 提取所有 sheet 的单元格 → 导出 `原文件名_yyyyMMdd_HHmmss.xlsx` 到源文件同目录。复用 Apache POI 生成单 sheet xlsx（列：位置/名称/值/表达式/批注，按位置排序）。

## 技术栈

- Java 8 源码（source/target 均为 1.8）；备份/导出/HMI 编辑功能 JRE 8+ 可运行
- **QuickJS 脚本引擎（`cn.net.zhijian.quickjs:0.2.2`，本地 jar `libs/`）运行时需要 JDK 19+**（库内调用 `Thread.threadId()`）；低版本 JDK 下脚本功能会给出友好错误，不影响其他功能。QuickJS 上下文为**延迟初始化**（首次运行脚本时才加载本地库），改动时不要恢复成构造即初始化
- Swing UI + FlatLaf 3.4.1（FlatLightLaf / FlatDarkLaf）
- Apache Commons Net 3.11.1（FTP/FTPS）、Gson 2.10.1、slf4j-simple 2.0.13
- org.java-websocket:Java-WebSocket 1.5.6（CogSocket HMI）
- org.apache.poi:poi-ooxml 5.2.5（xlsx 导出）
- org.kordamp.ikonli:ikonli-{core,swing,fontawesome5-pack} 12.3.1（按钮/说明文本图标，图标颜色随 UIManager 前景色）
- com.fifesoft:rsyntaxtextarea 3.4.0（JS 脚本编辑器语法高亮）
- QuickJS：`implementation fileTree(dir: 'libs', include: ['*.jar'])`（jar 内含各平台本地库，fatJar 已包含）
- Gradle（带 wrapper：`gradlew.bat` / `./gradlew`）

## 构建与运行

```bash
# Windows
gradlew.bat build
# Linux / macOS
./gradlew build
```

- `build` 会自动触发 `fatJar` 任务，产物在 `build/libs/` 下：
  - `cognex-jobx-backup-1.0.0-all_<时间戳>.jar` — 包含全部依赖的可执行 fat jar（发布用，约 24 MB）
  - `cognex-jobx-backup-1.0.0.jar` — 仅项目代码的瘦 jar
- 一键发布脚本：`./build-with-timestamp.sh` 把最新 fat jar 复制为根目录 `jobx文件备份助手_<时间戳>.jar`
- 单文件 exe 发布：`./bundle-exe-with-timestamp.sh` 生成根目录 `jobx文件备份助手_<时间戳>.exe`（内嵌 OpenJDK JRE 25 MSI + fat jar 的原生 WinForms 启动器，net8.0-windows 自包含单文件，目标机无需预装 .NET/Java；首次运行若无 Java 25 则以管理员权限静默安装 MSI 并设置 JAVA_HOME。`launcher/build-launcher.sh` 为兼容转发）。launch4j 不可用：其生成的 exe 依赖已存在的 JRE，无法在裸机上自我安装 JDK
- 运行：`java -jar jobx文件备份助手_<时间戳>.jar`
- 入口类：`com.cognex.backup.Main`（`build.gradle` 中 `application.mainClass` 与 jar manifest 均已配置）
- `build.gradle` 已为 `JavaCompile` 显式设置 `options.encoding = 'UTF-8'`（源码为 UTF-8；在默认 GBK 的 Windows 上不加会编译失败）。
- 本机只装了 JDK 25（Gradle 8.4 无法在其上运行），项目内放了便携版 JDK 17 于 `.tools/jdk17`，构建时这样用：
  - Git Bash：`JAVA_HOME="$PWD/.tools/jdk17" ./build-with-timestamp.sh`
  - PowerShell：`$env:JAVA_HOME="<项目根>\.tools\jdk17"; .\gradlew.bat build`

## 代码结构

```
src/main/java/com/cognex/
├── backup/                    # 标签页1：备份工具
│   ├── Main.java              # 入口：FlatLaf + MainFrame
│   ├── config/ConfigManager.java  # backup-config.json（jar 目录，UTF-8）
│   ├── ftp/FtpClient.java     # FTP/FTPS、OPTS UTF8 ON、递归下载 + listJobxFiles()（供导出复用）
│   ├── model/                 # CameraConfig、AppSettings
│   ├── util/AppIcons.java     # 应用图标加载（窗口 setIconImages + 反射调 Java 9 Taskbar）
│   │                          #   Icons.java：ikonli 按钮/说明文本图标助手（颜色随前景色）
│   └── ui/                    # MainFrame（JTabbedPane 容器，关闭时调 EditorTabPanel.shutdown）、
│                              # BackupTabPanel（备份页）、CameraDialog、AboutDialog、UsageDialog
├── export/                    # 标签页2：导出工具（Go 版的 Java 移植）
│   ├── ExportTabPanel.java    # 手动参数表单 + 相机列表批量导出 + 日志，后台线程执行
│   ├── ExportTask.java        # 枚举→HMI→离线→逐个 loadJob/读值/读表达式→xlsx→恢复；内嵌 Params
│   ├── ExportCameraDialog.java# 新增/编辑导出相机对话框
│   ├── config/ExportConfigManager.java  # export-config.json（jar 目录，UTF-8）
│   ├── model/ExportCamera.java# 导出相机配置（含 HMI wsPort）
│   └── XlsxExporter.java      # Apache POI："单元格"+"位置布局"两个 sheet，A4 纵向
├── parser/                    # 标签页4：Jobx 解析器（离线，不连相机）
│   ├── JobxParser.java        # TAR 解析 → Job.json → sheets/<hash>（XOR 0x72 0x9B 0x0F 0x2E）
│   │                          #   或 inline base64 / snippet.json（cxdx）→ cell 12 元素数组
│   ├── ParserXlsxExporter.java  # POI 单 sheet xlsx：位置/名称/值/表达式/批注，按位置排序，
│   │                          #   文件名 {stem}_yyyyMMdd_HHmmss.xlsx，输出到源文件同目录
│   └── ParserTabPanel.java    # 工具栏（打开按钮）+ 拖拽支持 + 日志区，后台线程解析
└── insight/                   # 标签页3：HMI 编辑器（java-insight-hmi 移植，包名保持不变）
    ├── cogsocket/             # CogSocketClient（WebSocket + 30s 超时调度器）、InSightConnection
    │                          #   （connect/login/ready、loadJob、单元格读写、softOnline/liveMode）、
    │                          #   CogSocketMessage
    ├── model/                 # CameraInfo、CellResult、HmiSessionInfo(cellNames A0:Z599)、HmiState
    ├── config/                # AppConfig、InsightConfigManager（config.json 写 jar 目录，避免与备份 ConfigManager 同名）
    ├── script/                # JsScriptEngine（QuickJS 延迟初始化）、SpreadsheetJsApi（JS spreadsheet 对象）
    └── ui/
        ├── dialog/            # CameraInfoDialog、SetCellDialog（Window + ModalityType 构造）
        └── panel/             # EditorTabPanel（编辑器页主体：工具栏+连接+图像+表格+脚本+状态栏）、
                               # ConnectionPanel、ImageDisplayPanel、SpreadsheetPanel、
                               # ScriptEditorPanel、StatusBarPanel

launcher/                      # 单文件 exe 启动器（模式参考 ../CameraViewerDotnet）
├── Launcher/                  # WinForms 启动器（net8.0-windows，自包含单文件发布）
│   ├── Program.cs             #   检测 Java 25→缺失则静默安装内嵌 OpenJDK MSI 并设 JAVA_HOME→
│   │                          #   释放内嵌 fat jar 到 %LOCALAPPDATA%\CognexJobxBackupTool\App 并启动
│   ├── CognexJobx.Launcher.csproj  # payload/ 下的 MSI 与 jar 作为 EmbeddedResource 内嵌
│   ├── app.manifest           # asInvoker；仅安装 JDK 时按需以 runas 提权重启
│   └── assets/app-icon.ico    # 由 assets/app-icon.png 经 make-ico.ps1 生成（16~256 多尺寸）
├── OpenJDK25U-jre_x64_windows_hotspot_25.0.4.1_1.msi  # JRE25 安装包（内嵌进 exe 的资源来源，Temurin）
├── make-ico.ps1               # png → ico
└── build-launcher.sh          # 兼容转发，实际入口为根目录 bundle-exe-with-timestamp.sh
```

图标资源：

- `assets/app-icon.png`（主图标，200x200 透明底，扁平菠萝）、`assets/app-icon-cute.png`（备用）；根目录 `菠萝*.png` 为原始文件，README logo 引用根目录文件。
- `src/main/resources/icons/app-icon-{16,24,32,48,64,128,200}.png` 由主图标高质量缩放生成，随 jar 打包；窗口/任务栏图标由 `com.cognex.backup.util.AppIcons` 加载，MainFrame 构造时 `AppIcons.applyTo(this)`。Taskbar API（Java 9+）用反射调用以保持 Java 8 兼容。替换图标后需同步重新生成 resources/icons 下的多尺寸文件。

## 关键行为与约定

- 配置文件一律写入 **jar 所在目录**（`CodeSource` 定位）：备份 `backup-config.json`、编辑器 `config.json`；留空的备份目录默认 jar 目录下 `backups/`，导出默认 jar 目录下 `exports/<yyyyMMddHHmmss>/`（批量导出时为 `exports/相机名称/<yyyyMMddHHmmss>/`）。
- FTP 客户端固定 UTF-8 控制编码 + autodetectUTF8，登录后 `OPTS UTF8 ON`；连接超时 10s、数据超时 30s；被动模式 + 二进制。FTPS 为显式 TLS，登录后 `PBSZ 0` / `PROT P`；"信任所有证书"默认开启；备份工具添加相机对话框默认勾选"使用 FTPS"。
- CogSocket：`ws://ip:port/ws`（新固件端口 80，旧固件 8087，模拟器随机端口）；握手固定流程 GET `system/paths/hmi` → `{root}/info` → openSession（cellNames `A0:Z599`）→ login → ready。相机在线时 loadJob/改单元格会失败，必须先 softOnline=false 且无 Explorer 编辑器附着。
- 所有相机/网络操作放后台线程，日志经 `SwingUtilities.invokeLater` 回写界面。
- **Jobx 解析器（离线）**：纯本地 TAR + 4 字节 XOR（`0x72 0x9B 0x0F 0x2E`，Cognex 官方 `DeobfuscateBytes` 硬编码常量）解析，不连相机；`.jobx` 走 `Job.json.Sheets.<name>`（inline `Byte[]` 或 `FileRef → sheets/<hash>`），`.cxdx` 走 `snippet.json`（同 XOR）；cell 数组 12 元素官方语义 `[location, expression, condition, value, name, saved, cellStyle, graphicsStyle, comment, input, output, ipProtected]`（`saved` 等大对象在导出 xlsx 时跳过）；data/* 与 *.sig 条目直接跳过不读入内存。
- **Java 8 兼容红线**：source/target 1.8 且 JRE 8+ 要能启动；除 QuickJS 库自身外，业务代码禁止用 Java 9+ API（历史上已把 `CompletableFuture.failedFuture`、`orTimeout` 替换为 Java 8 实现）。POI 5.2.5 注意：`setPrintArea(5 参数)` 在 Workbook 上、纵向用 `PrintSetup.setLandscape(false)`（无 PORTRAIT 常量）。
- 代码注释中文 + 英文混合，UI 文案为中文；修改时保持该风格。
- 单模块 Gradle，无 Spring；全部逻辑在 Swing EDT 与后台线程直接实现。

## 测试

- **没有正式的单元测试框架**（无 src/test，build.gradle 无 test 依赖）。
- 验证方式：`gradlew build` 编译通过；用 JDK 17 与 JDK 25 分别 `java -jar` 冒烟（三标签窗口能起来；JDK25 下 QuickJS 求值可用，JDK17 下脚本功能应只给友好报错而不崩溃）；相机相关端到端功能需要实机。

## 安全注意事项

- 相机密码以**明文**保存在 `backup-config.json` / `export-config.json` / `config.json` 中，不要提交或外发。
- "信任所有证书"选项绕过 TLS 校验，仅适合内网相机，改动时保留该开关。
- 项目根目录有 `jobx文件备份助手_*.jar` 发布文件及 `kimi-export-*.md` 会话导出，不属于源码，勿当作构建产物处理。
