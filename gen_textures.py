"""生成 16x16 像素风物品贴图（占位用，风格对齐手绘三叶草）。

说明：
- 手绘贴图优先。本脚本默认不会覆盖已存在的文件；加 --force 才强制重画。
- 想替换贴图：把 16x16 PNG 以物品 id 命名放进 textures_incoming/，再运行 apply_textures.py。
"""
import math
import os
import sys

from PIL import Image

S = 16
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "src/main/resources/assets/magicclover/textures/item")

# 三叶草：左上 / 右上 / 下方 三片叶 + 茎（贴近手绘造型）
LEAF_CENTERS = [(4.6, 5.2), (11.4, 5.2), (8.0, 10.9)]
LEAF_R = 2.8


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def new_img():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def disc(cx, cy, r):
    return {(x, y) for y in range(S) for x in range(S)
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r}


def outline_px(mask):
    out = set()
    for (x, y) in mask:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < S and 0 <= ny < S and (nx, ny) not in mask:
                out.add((nx, ny))
    return out


def draw_clover(colors, dark, stem, stem_dark):
    """colors: 每片叶子的 (base, light)，长度可为 1（统一）或 3（逐叶）。"""
    im = new_img()
    px = im.load()

    masks = [disc(cx, cy, LEAF_R) for cx, cy in LEAF_CENTERS]
    whole = set().union(*masks)

    # 茎（先画，叶子盖住上部）
    for y in range(11, 16):
        px[7, y] = stem
    for y in range(12, 16):
        px[6, y] = stem_dark

    for (x, y) in outline_px(whole):
        px[x, y] = dark

    if len(colors) == 1:
        colors = colors * len(masks)

    for mask, (cx, cy), (base, light) in zip(masks, LEAF_CENTERS, colors):
        for p in mask:
            px[p] = base
        for p in mask:
            if (p[0] + 0.5 - (cx - 0.8)) ** 2 + (p[1] + 0.5 - (cy - 0.8)) ** 2 <= 1.05 ** 2:
                px[p] = light

    return im


def uniform(base, dark, light, stem, stem_dark):
    return draw_clover([(hexc(base), hexc(light))], hexc(dark), hexc(stem), hexc(stem_dark))


def draw_bundle(base, dark, light, cord):
    im = new_img()
    px = im.load()
    body = set()
    for y in range(7, 15):
        for x in range(2, 14):
            if 3 <= x <= 12 and 7 <= y <= 13:
                body.add((x, y))
            elif x in (2, 13) and 9 <= y <= 12:
                body.add((x, y))
            elif y == 14 and 5 <= x <= 10:
                body.add((x, y))
    for y in range(4, 7):
        for x in range(5, 11):
            body.add((x, y))

    for (x, y) in outline_px(body):
        px[x, y] = dark
    for (x, y) in body:
        px[x, y] = base
    for (x, y) in body:
        if x <= 6 and y <= 11:
            px[x, y] = light
    for x in range(4, 12):
        for y in (6, 7):
            if (x, y) in body:
                px[x, y] = cord
    for (x, y) in ((7, 5), (8, 5)):
        px[x, y] = dark
    for (x, y) in ((7, 9), (8, 9), (7, 10), (8, 10)):
        if (x, y) in body:
            px[x, y] = hexc("4caf50")
    return im


def draw_potion(liquid, liquid_light, glass, cork):
    im = new_img()
    px = im.load()
    body = {p for p in disc(8.0, 10.6, 4.1) if p[1] >= 7}
    neck = {(7, y) for y in range(4, 8)} | {(8, y) for y in range(4, 8)}
    cork_px = {(6, 2), (7, 2), (8, 2), (9, 2), (6, 3), (7, 3), (8, 3), (9, 3)}
    allm = body | neck

    for (x, y) in cork_px:
        px[x, y] = cork
    for (x, y) in outline_px(allm | cork_px):
        px[x, y] = hexc("2b2b3a")
    for (x, y) in allm:
        px[x, y] = glass
    for (x, y) in body:
        if y >= 10:
            px[x, y] = liquid
    for (x, y) in body:
        if y >= 12 and x <= 7:
            px[x, y] = liquid_light
    px[6, 11] = liquid_light
    px[9, 13] = liquid_light
    return im


PALETTE = {
    "basic":     ("#43b649", "#1b5c22", "#79d97c", "#2f7c33", "#1b5c22"),
    "wood":      ("#a97142", "#3d240f", "#d9a873", "#7a4f2a", "#4a2e14"),
    "coal":      ("#565656", "#141414", "#8a8a8a", "#333333", "#141414"),
    "copper":    ("#c9714a", "#4a200f", "#eda883", "#8f4d2e", "#5a2c16"),
    "iron":      ("#cfcfcf", "#4f4f4f", "#f5f5f5", "#9a9a9a", "#5c5c5c"),
    "gold":      ("#f2c744", "#6b4d08", "#ffef9c", "#c9a227", "#7a5c0c"),
    "diamond":   ("#4fd8d8", "#0f5252", "#b3fbfb", "#2fa3a3", "#155c5c"),
    "emerald":   ("#35d16b", "#0b4a22", "#96ffb8", "#1e9c4a", "#0d5226"),
    "netherite": ("#6b5b7b", "#1f1a26", "#ad9fbb", "#4a3f57", "#231c29"),
}


def user_texture_names():
    """读取 apply_textures.py 记录的手绘图清单，这些文件任何情况下都不覆盖。"""
    record = os.path.join(os.path.dirname(os.path.abspath(__file__)), "textures_incoming", ".applied.txt")
    if not os.path.exists(record):
        return set()
    with open(record, encoding="utf-8") as fh:
        return {l.strip() for l in fh if l.strip()}


def save(im, filename, force=False):
    """默认不覆盖已存在的贴图；手绘清单里的图即使 --force 也不覆盖。"""
    path = os.path.join(OUT, filename)
    stem = os.path.splitext(filename)[0]
    if stem in user_texture_names():
        print("[保留手绘]", filename)
        return
    if os.path.exists(path) and not force:
        print("[保留已有]", filename)
        return
    im.save(path)
    print("[生成]", filename)


def main():
    force = "--force" in sys.argv
    os.makedirs(OUT, exist_ok=True)

    for name, args in PALETTE.items():
        save(uniform(*args), f"{name}_clover.png", force)

    # 模组幸运草：三叶异色（金 / 青 / 品红），一眼区分
    save(draw_clover([
        (hexc("#ffc93c"), hexc("#fff0b3")),
        (hexc("#3cc9ff"), hexc("#b3eeff")),
        (hexc("#ff5fd2"), hexc("#ffb8ee")),
    ], hexc("#2a1a3a"), hexc("#8a6bd0"), hexc("#5a3f9a")), "mod_clover.png", force)

    # 生物幸运草：草绿 + 金黄茎
    save(uniform("#7ac943", "#2d4f12", "#b8e878", "#e0b84c", "#a87c22"), "mob_clover.png", force)

    save(draw_bundle(hexc("#9c6b3f"), hexc("#3a2210"), hexc("#c99a68"), hexc("#e0c060")),
         "clover_bundle.png", force)
    save(draw_potion(hexc("#b04fd8"), hexc("#d99bf0"), hexc("#dce7f2"), hexc("#8a5a2b")),
         "event_potion.png", force)

    print("done:", len(os.listdir(OUT)), "个文件")


if __name__ == "__main__":
    main()
