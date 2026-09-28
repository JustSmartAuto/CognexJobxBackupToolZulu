#!/usr/bin/env bash
# 打包单文件 Windows exe 并以时间戳后缀复制到项目根目录（发布为 jobx文件备份助手_*.exe）
# 内嵌 OpenJDK JRE 25 MSI + 应用 fat jar（模式参考 ../CameraViewerDotnet 的原生启动器）：
#   1. 检测 Java 25+（JAVA_HOME / PATH / 常见安装目录）；
#   2. 缺失则以管理员权限静默安装内嵌的 OpenJDK JRE 25 MSI，并设置 JAVA_HOME；
#   3. 把内嵌 fat jar 释放到 %LOCALAPPDATA%\CognexJobxBackupTool\App 并启动。
# 注意：脚本功能需要 JDK 19+，JRE 25 已满足；启动器为 net8.0-windows 自包含单文件，
# 目标工控机无需预装 .NET/Java。
#
# Windows 双击运行说明：本脚本随 Git for Windows 安装时关联到 Git Bash，双击即可执行。
# 脚本在交互式终端（双击/mintty）中结束——无论成功失败——都会等待回车再关闭窗口，
# 避免出错时窗口一闪而过；从 CI / 管道等非交互环境调用时不暂停。
#
# 用法: ./bundle-exe-with-timestamp.sh
# 环境变量: ZULU_JRE_MSI=/path/to/x.msi  可指定自定义 JRE 25 MSI 路径
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
LAUNCHER_DIR="$ROOT/launcher"
PAYLOAD="$LAUNCHER_DIR/Launcher/payload"
PROJ="$LAUNCHER_DIR/Launcher/CognexJobx.Launcher.csproj"
PUBLISH="$LAUNCHER_DIR/Launcher/bin/publish"
STAMP="$(date +%Y%m%d%H%M%S)"
OUT="$ROOT/jobx文件备份助手_${STAMP}.exe"

# 双击运行（Git Bash/mintty 分配 tty）时暂停窗口；管道/CI 中非交互直接退出。
# Pause in interactive terminals (double-click) so error text stays visible; no pause in CI.
INTERACTIVE=0
if [[ -t 0 ]]; then INTERACTIVE=1; fi

pause() {
    if [[ "$INTERACTIVE" == "1" ]]; then
        echo
        read -n1 -r -p "按回车键退出 / Press Enter to exit..." _ || true
        echo
    fi
}

# 任何退出路径都暂停一次；非 0 退出码额外打印提示。
# Pause once on every exit path; flag non-zero exits so double-click users see the failure.
trap 'rc=$?
if [[ $rc -ne 0 ]]; then
    echo "" >&2
    echo "ERROR: 打包中断（退出码 $rc），请查看上方错误信息。 / Build aborted (exit $rc)." >&2
fi
pause' EXIT

die() {
    echo "" >&2
    echo "ERROR: $*" >&2
    exit 1
}

echo "==> [0/4] 前置检查 / prerequisites..."

# 1) 便携 JDK 17（Gradle 8.4 不支持机器默认 JDK 25，必须用它构建）
if [[ -f "$ROOT/.tools/jdk17/bin/java.exe" || -x "$ROOT/.tools/jdk17/bin/java" ]]; then
    echo "    JDK17: $ROOT/.tools/jdk17"
else
    die "未找到项目内便携 JDK 17：$ROOT/.tools/jdk17
    机器默认 JDK 25 无法构建 Gradle 8.4。请把便携 JDK 17 放到该目录（不入库）。"
fi

# 2) dotnet SDK（net8.0-windows 自包含发布）
if ! command -v dotnet >/dev/null 2>&1; then
    die "未找到 dotnet 命令。请安装 .NET 8 SDK 后重试：
    https://dotnet.microsoft.com/download/dotnet/8.0"
fi
echo "    dotnet: $(dotnet --version)"

# 3) 启动器工程文件
[[ -f "$PROJ" ]] || die "未找到启动器工程: $PROJ"

# 4) Zulu JRE 25 MSI（启动器内嵌资源）。
#    定位顺序：环境变量 ZULU_JRE_MSI → launcher/ 下 OpenJDK25U 别名文件 →
#    launcher/ 下 Azul 原始文件名 zulu*ca-jre25*win_x64.msi（取修改时间最新）。
MSI_SRC=""
if [[ -n "${ZULU_JRE_MSI:-}" ]]; then
    if [[ -f "$ZULU_JRE_MSI" ]]; then
        MSI_SRC="$ZULU_JRE_MSI"
    else
        die "环境变量 ZULU_JRE_MSI 指向的文件不存在: $ZULU_JRE_MSI"
    fi
else
    MSI_SRC="$( {
        ls -t "$LAUNCHER_DIR"/OpenJDK25U-jre_x64_windows_hotspot_*.msi \
              "$LAUNCHER_DIR"/zulu*ca-jre25*win_x64.msi 2>/dev/null || true
    } | head -1)"
fi
if [[ -z "$MSI_SRC" || ! -f "$MSI_SRC" ]]; then
    die "未找到内嵌用 Zulu JRE 25 (Windows x64) MSI 安装包。
    请将 MSI 放到：$LAUNCHER_DIR/
    下载（任选其一）：
      1. Azul JDK 下载页选择 JRE 25 / Windows / x64 / MSI：
         https://www.azul.com/downloads/?version=java-25-lts&os=windows&architecture=x-86-64-bit&package=jre
      2. 或用 API 取最新直链：
         https://api.azul.com/metadata/v1/zulu/packages/?java_version=25&os=windows&arch=x64&archive_type=msi&java_package_type=jre&latest=true
    也可设置环境变量指定其他位置：ZULU_JRE_MSI=/path/to/jre25.msi"
fi
echo "    JRE25 MSI: $MSI_SRC ($(du -h "$MSI_SRC" | cut -f1))"

echo "==> [1/4] 构建 fat jar..."
(cd "$ROOT" && JAVA_HOME="$ROOT/.tools/jdk17" ./gradlew build --console=plain -q)
JAR="$(ls -t "$ROOT"/build/libs/cognex-jobx-backup-*-all_*.jar | head -1)"
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
    die "未找到构建产物 build/libs/cognex-jobx-backup-*-all_*.jar"
fi
echo "    jar: $JAR"

echo "==> [2/4] 准备内嵌资源 (payload/)..."
mkdir -p "$PAYLOAD"
# 清理上一次的内嵌资源，避免残留旧版本 / drop stale payload files
rm -f "$PAYLOAD/app.jar" "$PAYLOAD/zulu-jdk25.msi"
cp "$JAR" "$PAYLOAD/app.jar"
cp "$MSI_SRC" "$PAYLOAD/zulu-jdk25.msi"   # 资源逻辑名保持 zulu-jdk25.msi，MSI 实际版本以 launcher/ 下文件为准

echo "==> [3/4] dotnet publish (net8.0-windows, win-x64, 自包含单文件)..."
rm -rf "$PUBLISH"
dotnet publish "$PROJ" -c Release -r win-x64 --self-contained true \
    -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true \
    -o "$PUBLISH"

echo "==> [4/4] 输出单文件 exe..."
PUBLISHED_EXE="$PUBLISH/jobx文件备份助手.exe"
[[ -f "$PUBLISHED_EXE" ]] || die "dotnet publish 未生成预期产物: $PUBLISHED_EXE"
cp "$PUBLISHED_EXE" "$OUT"
echo "OK: $OUT ($(du -h "$OUT" | cut -f1))"
