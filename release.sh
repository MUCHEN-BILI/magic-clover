#!/usr/bin/env bash
# 一键发布：改版本号 → 构建 → 部署到测试环境 → 生成 changelog → 提交 → 提醒文档同步
#
# 用法：
#   ./release.sh 1.5.2 "修了口袋排序的边界问题"        # 正式发布
#   ./release.sh 1.6.0 "新增草的合成升级" --dry-run    # 只打印步骤，不改任何东西
#
# 选项：
#   --dry-run     只演示，不改文件、不构建
#   --no-build    跳过构建（jar 已经手动构建过）
#   --no-commit   不自动 git commit
#   --allow-same  版本号已经手动改过了，允许继续（默认会报"版本号没变"）
#
# 环境变量：
#   MODS_DIR      测试环境 mods 目录，默认见下方
set -euo pipefail
cd "$(dirname "$0")"

MODS_DIR="${MODS_DIR:-D:/新建文件夹/profiles/1.21.11-Fabric 0.20.1/mods}"

VERSION=""
NOTE=""
DRY=0
SKIP_BUILD=0
NO_COMMIT=0
ALLOW_SAME=0

usage() {
	sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--dry-run)   DRY=1; shift ;;
		--no-build)  SKIP_BUILD=1; shift ;;
		--no-commit) NO_COMMIT=1; shift ;;
		--allow-same) ALLOW_SAME=1; shift ;;
		-h|--help)   usage; exit 0 ;;
		-*)          echo "未知选项：$1" >&2; usage; exit 1 ;;
		*)
			if [[ -z "$VERSION" ]]; then VERSION="$1"; else NOTE="${NOTE:+$NOTE }$1"; fi
			shift ;;
	esac
done

if [[ -z "$VERSION" ]]; then
	echo "错误：没有给版本号。例：./release.sh 1.5.2 \"修了 xxx\"" >&2
	exit 1
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	echo "错误：版本号必须是 x.y.z 格式，收到：$VERSION" >&2
	exit 1
fi

OLD_VERSION="$(grep -E '^version=' gradle.properties | head -1 | cut -d= -f2 | tr -d '\r')"
if [[ -z "$OLD_VERSION" ]]; then
	echo "错误：gradle.properties 里找不到 version=" >&2
	exit 1
fi
if [[ "$OLD_VERSION" == "$VERSION" ]] && [[ $ALLOW_SAME -eq 0 ]]; then
	echo "错误：gradle.properties 里已经是 $VERSION 了，版本号没变。" >&2
	exit 1
fi

# 判断这次是补丁版还是功能版，决定要不要动文档
old_major="${OLD_VERSION%%.*}"; rest="${OLD_VERSION#*.}"; old_minor="${rest%%.*}"
new_major="${VERSION%%.*}"; rest="${VERSION#*.}"; new_minor="${rest%%.*}"
if [[ "$new_major" != "$old_major" ]]; then
	BUMP="大版本"
elif [[ "$new_minor" != "$old_minor" ]]; then
	BUMP="功能版本"
else
	BUMP="补丁版本"
fi

JAR="build/libs/magic-clover-${VERSION}.jar"
CHANGELOG="build/changelog-${VERSION}.md"

echo "=============================================="
echo " 发布 magic-clover ${OLD_VERSION} → ${VERSION}（${BUMP}）"
[[ -n "$NOTE" ]] && echo " 说明：${NOTE}"
[[ $DRY -eq 1 ]] && echo " 模式：DRY RUN（不会修改任何文件）"
echo "=============================================="
echo

step() { echo "▶ $*"; }

# 1. 版本号
step "1/6 更新 gradle.properties 的 version 字段"
if [[ $DRY -eq 1 ]]; then
	echo "   [dry] version=${OLD_VERSION} → version=${VERSION}"
else
	python -c "import re,sys;p='gradle.properties';s=open(p,newline='').read();open(p,'w',newline='').write(re.sub(r'(?m)^version=.*\$','version=${VERSION}',s))"
	echo "   version=${VERSION}"
fi

# 2. 构建
step "2/6 构建 jar"
if [[ $SKIP_BUILD -eq 1 ]]; then
	echo "   已跳过（--no-build）"
elif [[ $DRY -eq 1 ]]; then
	echo "   [dry] ./build.sh build /tmp/mc-build-${VERSION}.log"
else
	./build.sh build "/tmp/mc-build-${VERSION}.log"
	if [[ ! -f "$JAR" ]]; then
		echo "错误：构建结束但没找到 $JAR" >&2
		exit 1
	fi
	echo "   产物：$JAR"
fi

# 3. 部署
step "3/6 部署到测试环境 mods（旧 jar 改名 .disabled）"
if [[ $DRY -eq 1 ]]; then
	echo "   [dry] 把 ${MODS_DIR}/magic-clover-*.jar 改名为 .jar.disabled，再复制 $JAR"
else
	if [[ ! -d "$MODS_DIR" ]]; then
		echo "   警告：找不到测试环境目录 $MODS_DIR，跳过部署"
	else
		shopt -s nullglob
		for f in "$MODS_DIR"/magic-clover-*.jar; do
			mv "$f" "$f.disabled"
			echo "   停用：$(basename "$f")"
		done
		shopt -u nullglob
		cp "$JAR" "$MODS_DIR/"
		echo "   部署：$(basename "$JAR")"
	fi
fi

# 4. changelog 模板
step "4/6 生成 changelog 模板"
if [[ $DRY -eq 1 ]]; then
	echo "   [dry] 写入 $CHANGELOG"
else
	mkdir -p build
	cat > "$CHANGELOG" <<EOF
## ${VERSION}

### 新增

- ${NOTE:-（写这里）}

### 变更

-

### 修复

-
EOF
	echo "   $CHANGELOG"
	echo "   ── 内容 ──"
	sed 's/^/   /' "$CHANGELOG"
fi

# 5. 提交
step "5/6 git 提交"
if [[ $NO_COMMIT -eq 1 ]]; then
	echo "   已跳过（--no-commit）"
elif [[ $DRY -eq 1 ]]; then
	echo "   [dry] git add -A && git commit -m \"release: v${VERSION}\""
else
	git add -A
	git commit -q -m "release: v${VERSION}${NOTE:+ - $NOTE}" || echo "   没有需要提交的改动"
	# GitHub 在本机走代理时偶尔 502 / 连接被掐断，重试三次再放弃
	pushed=0
	for attempt in 1 2 3; do
		if push_out=$(git push origin main 2>&1); then
			echo "$push_out" | tail -2
			pushed=1
			break
		fi
		echo "$push_out" | tail -1
		echo "   ⚠ 第 ${attempt} 次推送失败，5 秒后重试…"
		sleep 5
	done
	if [[ $pushed -eq 1 ]]; then
		echo "   已推送到 GitHub"
	else
		echo "   ⚠ 三次推送都失败，稍后手动 git push origin main"
	fi
fi

# 6. 文档提醒
step "6/6 文档同步提醒"
if [[ "$BUMP" == "补丁版本" ]]; then
	echo "   补丁版本：通常只写 changelog 即可。"
	echo "   例外：改了兼容范围 / 配置项 / 掉落概率的话，介绍文和描述也要跟着改。"
else
	echo "   这次是${BUMP}，建议同步这几处："
	echo "     · 幸运四叶草模组介绍.html（版本号、概率、物品清单）→ 改完跑 python sync_intro.py"
	echo "     · 幸运四叶草-Modrinth描述.md（若玩法有变化）"
	echo "     · Modrinth 项目页：描述正文 + Gallery（有新视觉内容才需要新图）"
fi

echo
echo "=============================================="
echo " 下一步：把 jar 传到 Modrinth 版本页"
echo "   $JAR"
echo " changelog 直接粘 4/6 生成的那段"
echo "=============================================="
