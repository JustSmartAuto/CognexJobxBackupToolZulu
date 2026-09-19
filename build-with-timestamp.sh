#!/usr/bin/env bash
# 打包 fatjar 并以时间戳后缀复制到项目根目录（发布为 jobx文件备份助手_*.jar）
# 用法: ./build-with-timestamp.sh
set -euo pipefail

cd "$(dirname "$0")"

./gradlew build --console=plain -q

JAR=$(ls -t build/libs/cognex-jobx-backup-*-all_*.jar | head -1)
if [ -z "$JAR" ]; then
    echo "ERROR: 未找到构建产物 build/libs/cognex-jobx-backup-*-all_*.jar" >&2
    exit 1
fi

TS=$(date +%Y%m%d%H%M%S)
OUT="jobx文件备份助手_${TS}.jar"
cp "$JAR" "$OUT"
echo "OK: $OUT"
