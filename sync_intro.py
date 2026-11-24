#!/usr/bin/env python3
"""
同步模组介绍文（幸运四叶草模组介绍.html）。

用途：
  python sync_intro.py            # 检查 + 自动对齐版本号，并报告概率差异
  python sync_intro.py --apply    # 同时把概率表格也按项目默认值改写（谨慎）

背景：介绍文是发给玩家看的独立 HTML，图片已 base64 内嵌。
每次模组更新后跑一下这个脚本，避免文章里的版本号/概率和实际模组对不上。

数据源：
  版本号  -> gradle.properties
  概率    -> src/main/java/.../config/ModConfig.java 里的字段默认值
  掉落率  -> MiningDrops.java / LootModifiers.java / StructureLoot.java
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ARTICLE = ROOT / "幸运四叶草模组介绍.html"
GRADLE = ROOT / "magic-clover" / "gradle.properties"
CONFIG = (ROOT / "magic-clover" / "src" / "main" / "java" / "com" / "muchen"
          / "magicclover" / "config" / "ModConfig.java")
MINING = (ROOT / "magic-clover" / "src" / "main" / "java" / "com" / "muchen"
          / "magicclover" / "event" / "MiningDrops.java")


def field_default(src_text: str, field: str):
    m = re.search(r"public\s+(?:float|double)\s+" + re.escape(field) + r"\s*=\s*([\d.]+)f?\s*;",
                  src_text)
    return float(m.group(1)) if m else None


def main():
    apply = "--apply" in sys.argv

    if not ARTICLE.exists():
        print(f"找不到介绍文: {ARTICLE}")
        return 1

    html = ARTICLE.read_text(encoding="utf-8")

    # ---- 1. 版本号：自动对齐 ----
    version = re.search(r"^version=(.+)$", GRADLE.read_text(encoding="utf-8"), re.M).group(1).strip()
    found = set(re.findall(r"v(\d+\.\d+\.\d+)", html))
    print(f"项目版本: {version}")
    print(f"文章版本: {', '.join(sorted(found)) or '(未找到)'}")

    if found and found != {version}:
        if apply or True:  # 版本号是唯一明确的，总是自动对齐
            for old in found:
                html = html.replace(f"v{old}", f"v{version}")
            ARTICLE.write_text(html, encoding="utf-8")
            print(f"  已自动对齐 -> v{version}")
    elif not found:
        print("  警告: 文章里没找到版本号标记")

    # ---- 2. 概率：报告差异 ----
    cfg_text = CONFIG.read_text(encoding="utf-8")
    checks = [
        ("打草掉率", "grassDropChance", "8%"),
        ("结构箱子", "structureCloverChance", "35%"),
        ("事件概率", "eventChance", "2%"),
        ("高级药水", "eventChanceBuffed", "50%"),
        ("低级药剂", "lesserEventChance", "20%"),
    ]
    print("\n概率检查（文章 vs 项目默认值）:")
    for label, field, article_value in checks:
        val = field_default(cfg_text, field)
        expected = f"{round(val * 100)}%" if val is not None else "?"
        status = "OK" if expected == article_value else "需同步"
        print(f"  {label:8} 文章={article_value:5} 项目={expected:5}  [{status}]")
        if status == "需同步" and apply:
            html = html.replace(article_value, expected)
            print("    -> 已改写")

    # ---- 3. 贴图内嵌检查 ----
    raw_paths = len(re.findall(r'src="[A-Za-z]:/', html))
    embedded = len(re.findall(r"data:image/png;base64", html))
    print(f"\n贴图: 内嵌 {embedded} 张, 未内嵌 {raw_paths} 张")
    if raw_paths:
        print("  提醒: 有本地路径图片，发给别人会裂开，需要重新内嵌")

    if apply:
        ARTICLE.write_text(html, encoding="utf-8")
        print("\n已保存修改")

    return 0


if __name__ == "__main__":
    sys.exit(main())
