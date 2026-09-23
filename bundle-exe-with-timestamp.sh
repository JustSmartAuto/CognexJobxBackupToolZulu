#!/usr/bin/env bash
# 打包单文件 Windows exe 并以时间戳后缀复制到项目根目录（发布为 jobx文件备份助手_*.exe）
# 内嵌 OpenJDK JRE 25 MSI + 应用 fat jar（模式参考 ../CameraViewerDotnet 的原生启动器）：
#   1. 检测 Java 25+（JAVA_HOME / PATH / 常见安装目录）；
#   2. 缺失则以管理员权限静默安装内嵌的 OpenJDK JRE 25 MSI，并设置 JAVA_HOME；
#   3. 把内嵌 fat jar 释放到 %LOCALAPPDATA%\CognexJobxBackupTool\App 并启动。
# 注意：脚本功能需要 JDK 19+，JRE 25 已满足；启动器为 net8.0-windows 自包含单文件，
# 目标工控机无需预装 .NET/Java。
# 用法: ./bundle-exe-with-timestamp.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PAYLOAD="$ROOT/launcher/Launcher/payload"
MSI_SRC="$ROOT/launcher/OpenJDK25U-jre_x64_windows_hotspot_25.0.4.1_1.msi"
PROJ="$ROOT/launcher/Launcher/CognexJobx.Launcher.csproj"
PUBLISH="$ROOT/launcher/Launcher/bin/publish"
STAMP="$(date +%Y%m%d%H%M%S)"
OUT="$ROOT/jobx文件备份助手_${STAMP}.exe"

echo "==> [1/4] 构建 fat jar..."
(cd "$ROOT" && JAVA_HOME="$ROOT/.tools/jdk17" ./gradlew build --console=plain -q)
JAR="$(ls -t "$ROOT"/build/libs/cognex-jobx-backup-*-all_*.jar | head -1)"
if [ -z "$JAR" ]; then
    echo "ERROR: 未找到构建产物 build/libs/cognex-jobx-backup-*-all_*.jar" >&2
    exit 1
fi
echo "    jar: $JAR"

echo "==> [2/4] 准备内嵌资源 (payload/)..."
mkdir -p "$PAYLOAD"
cp "$JAR" "$PAYLOAD/app.jar"
cp "$MSI_SRC" "$PAYLOAD/zulu-jdk25.msi"   # 资源逻辑名保持不变，MSI 内容以 launcher/ 下实际文件为准

echo "==> [3/4] dotnet publish (net8.0-windows, win-x64, 自包含单文件)..."
rm -rf "$PUBLISH"
dotnet publish "$PROJ" -c Release -r win-x64 --self-contained true \
  -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true \
  -o "$PUBLISH"

echo "==> [4/4] 输出单文件 exe..."
cp "$PUBLISH/jobx文件备份助手.exe" "$OUT"
echo "OK: $OUT"
