"""把 textures_incoming/ 里画好的贴图同步到资源目录。

用法：
  1. 把画好的 16x16 PNG 放进 textures_incoming/
  2. 文件名用物品 id，例如：
       basic_clover.png   普通幸运草
       wood_clover.png    木头幸运草
       coal_clover.png    煤炭幸运草
       copper_clover.png  铜幸运草
       iron_clover.png    铁幸运草
       gold_clover.png    黄金幸运草
       diamond_clover.png 钻石幸运草
       emerald_clover.png 绿宝石幸运草
       netherite_clover.png 下界合金幸运草
       mod_clover.png     模组幸运草
       mob_clover.png     生物幸运草
       clover_bundle.png  随机幸运草包裹
       event_potion.png   事件药水
  3. 运行： python apply_textures.py

脚本会校验尺寸（必须是 16x16），然后拷贝到
src/main/resources/assets/magicclover/textures/item/
"""
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "textures_incoming")
DST = os.path.join(ROOT, "src/main/resources/assets/magicclover/textures/item")

VALID = {
    "basic_clover", "wood_clover", "coal_clover", "copper_clover", "iron_clover",
    "gold_clover", "diamond_clover", "emerald_clover", "netherite_clover",
    "mod_clover", "mob_clover", "clover_bundle", "event_potion",
}


def main():
    os.makedirs(SRC, exist_ok=True)
    os.makedirs(DST, exist_ok=True)

    files = [f for f in os.listdir(SRC) if f.lower().endswith(".png")]
    if not files:
        print("textures_incoming/ 里没有 PNG 文件。")
        print("可用的文件名（物品 id）：")
        for v in sorted(VALID):
            print("   ", v + ".png")
        return

    applied, skipped = 0, 0
    applied_names = []
    for f in sorted(files):
        name = os.path.splitext(f)[0].strip().lower().replace(" ", "_")
        if name not in VALID:
            print(f"[跳过] {f} —— 文件名不是已知物品 id")
            skipped += 1
            continue
        path = os.path.join(SRC, f)
        try:
            im = Image.open(path)
            if im.size != (16, 16):
                print(f"[跳过] {f} —— 尺寸 {im.size}，需要 16x16")
                skipped += 1
                continue
            im.convert("RGBA").save(os.path.join(DST, name + ".png"))
            print(f"[应用] {f} -> {name}.png")
            applied += 1
            applied_names.append(name)
        except Exception as e:
            print(f"[失败] {f}: {e}")
            skipped += 1

    # 记录手绘图清单，防止 gen_textures.py 覆盖（即使是 --force）
    if applied_names:
        record = os.path.join(SRC, ".applied.txt")
        existing = set()
        if os.path.exists(record):
            existing = {l.strip() for l in open(record, encoding="utf-8") if l.strip()}
        existing.update(applied_names)
        with open(record, "w", encoding="utf-8") as fh:
            fh.write("\n".join(sorted(existing)) + "\n")

    print(f"\n完成：应用 {applied} 张，跳过 {skipped} 张")


if __name__ == "__main__":
    main()
