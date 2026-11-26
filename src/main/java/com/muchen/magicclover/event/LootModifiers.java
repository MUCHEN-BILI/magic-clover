package com.muchen.magicclover.event;

import com.muchen.magicclover.ModItems;
import com.muchen.magicclover.config.ModConfig;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * 修改原版草的战利品表，加入普通幸运草掉落。
 * 用「权重 + 空物品」的方式实现概率，掉率由配置控制。
 */
public final class LootModifiers {
	private LootModifiers() {}

	private static final int TOTAL_WEIGHT = 1000;

	public static void register() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!source.isBuiltin()) return;
			boolean isGrass = isGrassTable(key);
			boolean isLitter = key.identifier().getPath().equals("blocks/leaf_litter");
			if (!isGrass && !isLitter) return;

			float chance = (float) Math.max(0.0, Math.min(1.0,
					isLitter ? ModConfig.get().witheredDropChance : ModConfig.get().grassDropChance));
			if (chance <= 0.0f) return;

			int cloverWeight = Math.max(1, Math.round(chance * TOTAL_WEIGHT));
			int emptyWeight = Math.max(0, TOTAL_WEIGHT - cloverWeight);

			LootPool.Builder pool = new LootPool.Builder()
					.setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(isLitter ? ModItems.WITHERED_CLOVER : ModItems.BASIC_CLOVER)
							.setWeight(cloverWeight));

			if (emptyWeight > 0) {
				pool.add(EmptyLootItem.emptyItem().setWeight(emptyWeight));
			}

			tableBuilder.withPool(pool);
		});
	}

	private static boolean isGrassTable(ResourceKey<LootTable> key) {
		String path = key.identifier().getPath();
		return path.equals("blocks/short_grass")
				|| path.equals("blocks/tall_grass")
				|| path.equals("blocks/fern")
				|| path.equals("blocks/large_fern");
	}
}
