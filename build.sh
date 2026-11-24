#!/usr/bin/env bash
# 编译脚本：解决 libraries.minecraft.net 不可达问题（走 local-maven + init 脚本镜像）
set -o pipefail
cd "$(dirname "$0")" || exit 1

export JAVA_HOME="/c/Users/Administrator/AppData/Roaming/.minecraft/runtime/java-runtime-epsilon"
export PATH="$JAVA_HOME/bin:$PATH"

GRADLE="/c/Users/Administrator/WorkBuddy/2026-11-09-22-37-49/.buildtools/gradle-9.9.0/bin/gradle"
GH="/c/Users/Administrator/WorkBuddy/2026-11-09-22-37-49/.buildtools/.gradle-home"

TASK="${1:-compileJava}"
LOG="${2:-/tmp/mc-build.log}"

"$GRADLE" --no-daemon --gradle-user-home "$GH" --init-script init-mirror.gradle "$TASK" > "$LOG" 2>&1
CODE=$?
echo "EXIT=$CODE"
grep -E "BUILD (SUCCESSFUL|FAILED)" "$LOG"
grep -E "错误:|error:" "$LOG" | head -60
exit $CODE
