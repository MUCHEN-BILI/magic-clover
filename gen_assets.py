import json
import os
from PIL import Image, ImageDraw

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "src", "main", "resources", "assets", "magicclover")

ITEMS = {
    "basic_clover": "#3fa83f",
    "wood_clover": "#8b5a2b",
    "coal_clover": "#2b2b2b",
    "copper_clover": "#b87333",
    "iron_clover": "#c0c0c0",
    "gold_clover": "#ffd700",
    "diamond_clover": "#4ce0e0",
    "emerald_clover": "#2ecc71",
    "netherite_clover": "#4a3f4f",
    "mod_clover": "#a855f7",
    "mob_clover": "#f39c12",
    "clover_bundle": "#c19a6b",
    "event_potion": "#e879f9",
}

def hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def make_clover_texture(path, color):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r, g, b = hex_to_rgb(color)
    dark = (max(0, r-60), max(0, g-60), max(0, b-60), 255)
    light = (min(255, r+40), min(255, g+40), min(255, b+40), 255)
    # 四片叶子
    for (cx, cy) in [(6, 6), (10, 6), (6, 10), (10, 10)]:
        d.ellipse([cx-3, cy-3, cx+2, cy+2], fill=(r, g, b, 255))
        d.ellipse([cx-2, cy-2, cx, cy], fill=light)
    # 茎
    d.line([8, 8, 8, 14], fill=dark, width=1)
    img.save(path)

def make_bundle_texture(path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    brown = (193, 154, 107, 255)
    dark = (120, 90, 60, 255)
    d.rectangle([3, 5, 12, 13], fill=brown, outline=dark)
    d.line([3, 8, 12, 8], fill=dark, width=1)
    d.line([8, 5, 8, 13], fill=dark, width=1)
    d.rectangle([6, 2, 9, 5], fill=(80, 200, 90, 255))
    img.save(path)

def make_potion_texture(path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([6, 1, 9, 4], fill=(180, 180, 180, 255))
    d.rectangle([5, 4, 10, 14], fill=(210, 210, 220, 120), outline=(120, 120, 130, 255))
    d.rectangle([6, 7, 9, 13], fill=(232, 121, 249, 255))
    img.save(path)

def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)

for name, color in ITEMS.items():
    # 模型
    write_json(os.path.join(BASE, "models", "item", name + ".json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "magicclover:item/" + name}
    })
    # 客户端物品定义 (1.21.4+)
    write_json(os.path.join(BASE, "items", name + ".json"), {
        "model": {"type": "minecraft:model", "model": "magicclover:item/" + name}
    })
    # 贴图
    tex_path = os.path.join(BASE, "textures", "item", name + ".png")
    if name == "clover_bundle":
        make_bundle_texture(tex_path)
    elif name == "event_potion":
        make_potion_texture(tex_path)
    else:
        make_clover_texture(tex_path, color)

# 图标
icon = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
d = ImageDraw.Draw(icon)
for (cx, cy) in [(24, 24), (40, 24), (24, 40), (40, 40)]:
    d.ellipse([cx-11, cy-11, cx+10, cy+10], fill=(63, 168, 63, 255))
    d.ellipse([cx-7, cy-7, cx-1, cy-1], fill=(120, 220, 120, 255))
d.line([32, 32, 32, 56], fill=(30, 100, 30, 255), width=3)
icon.save(os.path.join(BASE, "icon.png"))

print("Generated assets for", len(ITEMS), "items")
