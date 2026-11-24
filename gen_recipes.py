import json
import os

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "src", "main", "resources", "data", "magicclover", "recipe")
os.makedirs(BASE, exist_ok=True)

def write(name, obj):
    with open(os.path.join(BASE, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)

# 包裹合成：6 个普通草 + 3 个皮革（摆成环形）
write("clover_bundle", {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {
        "C": {"item": "magicclover:basic_clover"},
        "L": {"item": "minecraft:leather"}
    },
    "pattern": ["CLC", "CLC", "C C"],
    "result": {"count": 1, "id": "magicclover:clover_bundle"}
})

# 定向合成：8 个矿物围一圈 + 中间 1 个普通草
tiers = {
    "wood": "minecraft:oak_log",
    "coal": "minecraft:coal",
    "copper": "minecraft:copper_ingot",
    "iron": "minecraft:iron_ingot",
    "gold": "minecraft:gold_ingot",
    "diamond": "minecraft:diamond",
    "emerald": "minecraft:emerald",
    "netherite": "minecraft:netherite_ingot",
}
for tier, material in tiers.items():
    write(tier + "_clover", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {
            "M": {"item": material},
            "C": {"item": "magicclover:basic_clover"}
        },
        "pattern": ["MMM", "MCM", "MMM"],
        "result": {"count": 1, "id": "magicclover:" + tier + "_clover"}
    })

# 模组幸运草：8 个末影珍珠 + 中间 1 个普通草
write("mod_clover", {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {
        "E": {"item": "minecraft:ender_pearl"},
        "C": {"item": "magicclover:basic_clover"}
    },
    "pattern": ["EEE", "ECE", "EEE"],
    "result": {"count": 1, "id": "magicclover:mod_clover"}
})

# 事件药水：中间普通草 + 周围 4 个烈焰粉 + 4 个金粒
write("event_potion", {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {
        "B": {"item": "minecraft:blaze_powder"},
        "G": {"item": "minecraft:gold_nugget"},
        "C": {"item": "magicclover:basic_clover"}
    },
    "pattern": ["BGB", "GCG", "BGB"],
    "result": {"count": 1, "id": "magicclover:event_potion"}
})

print("Generated", len(os.listdir(BASE)), "recipes")
