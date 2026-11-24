package com.muchen.magicclover.event;

import com.muchen.magicclover.MagicCloverMod;
import com.muchen.magicclover.ModItems;
import com.muchen.magicclover.config.ModConfig;
import com.muchen.magicclover.util.CloverTier;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * 往原版结构箱子里塞幸运草，作为探索奖励。
 * 结构越高级，箱子里的草等级越高；概率由 config/magicclover.json 的 structureCloverChance 控制。
 */
public final class StructureLoot {
	private StructureLoot() {}

	/** 权重基准，方便把 0~1 的概率换算成整数权重 */
	private static final int TOTAL_WEIGHT = 100;

	public static void register() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!source.isBuiltin()) return;

			String path = key.identifier().getPath();
			if (!path.startsWith("chests/")) return;

			CloverTier tier = tierFor(path);
			if (tier == null) return;

			// 每次注入时读配置，玩家改 json 后重进游戏即可生效
			float chance = ModConfig.get().structureCloverChance;
			if (chance <= 0.0f) return;
			chance = Math.min(1.0f, chance);

			int cloverWeight = Math.max(1, Math.round(chance * TOTAL_WEIGHT));
			int emptyWeight = Math.max(0, TOTAL_WEIGHT - cloverWeight);

			LootPool.Builder pool = new LootPool.Builder()
					.setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(ModItems.forTier(tier)).setWeight(cloverWeight));

			if (emptyWeight > 0) {
				pool.add(EmptyLootItem.emptyItem().setWeight(emptyWeight));
			}

			tableBuilder.withPool(pool);
			MagicCloverMod.LOGGER.info("[结构战利品] 注入 {}: {}草 ({}%)",
					key.identifier(), tier.getName(), cloverWeight);
		});
	}

	private static CloverTier tierFor(String path) {
		// 顶级结构
		if (path.contains("ancient_city")) return CloverTier.NETHERITE;
		if (path.contains("end_city")) return CloverTier.EMERALD;
		if (path.contains("bastion") || path.contains("fortress")) return CloverTier.NETHERITE;
		if (path.contains("stronghold") || path.contains("woodland") || path.contains("monument")) {
			return CloverTier.DIAMOND;
		}
		if (path.contains("mansion") || path.contains("trial_chamber")) return CloverTier.DIAMOND;

		// 中级结构
		if (path.contains("dungeon") || path.contains("mineshaft") || path.contains("shipwreck")
				|| path.contains("ruined_portal") || path.contains("jungle_temple") || path.contains("igloo")) {
			return CloverTier.IRON;
		}

		// 青金石主题：沙漠神殿和村庄神殿出青金石草
		if (path.contains("desert_pyramid") || path.contains("village_temple")) {
			return CloverTier.LAPIS;
		}

		// 村庄 / 前哨：基础档
		if (path.contains("village") || path.contains("pillager") || path.contains("igloo")) {
			return CloverTier.COAL;
		}

		return CloverTier.WOOD;
	}
}
