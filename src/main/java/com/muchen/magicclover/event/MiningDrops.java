package com.muchen.magicclover.event;

import com.muchen.magicclover.ModItems;
import com.muchen.magicclover.data.PlacedBlocksData;
import com.muchen.magicclover.util.CloverTier;
import com.muchen.magicclover.util.LootEngine;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * 挖矿/砍树时低概率掉出对应等级的幸运草。
 *
 * <p>只有「自然生成」的方块才掉落——玩家自己放下去的不会再吐草（见 {@link PlacedBlocksData}）。
 */
public final class MiningDrops {
	private MiningDrops() {}

	private record Rule(CloverTier tier, double chance) {}

	private static final Map<Block, Rule> RULES = new HashMap<>();

	public static void register() {
		// 普通矿：2%
		rule(Blocks.COAL_ORE, CloverTier.COAL, 0.02);
		rule(Blocks.DEEPSLATE_COAL_ORE, CloverTier.COAL, 0.02);
		rule(Blocks.COPPER_ORE, CloverTier.COPPER, 0.02);
		rule(Blocks.DEEPSLATE_COPPER_ORE, CloverTier.COPPER, 0.02);
		// 青金石比铁略高一点（原版获取难度更低）
		rule(Blocks.LAPIS_ORE, CloverTier.LAPIS, 0.023);
		rule(Blocks.DEEPSLATE_LAPIS_ORE, CloverTier.LAPIS, 0.023);
		rule(Blocks.IRON_ORE, CloverTier.IRON, 0.02);
		rule(Blocks.DEEPSLATE_IRON_ORE, CloverTier.IRON, 0.02);
		rule(Blocks.GOLD_ORE, CloverTier.GOLD, 0.02);
		rule(Blocks.DEEPSLATE_GOLD_ORE, CloverTier.GOLD, 0.02);
		rule(Blocks.NETHER_GOLD_ORE, CloverTier.GOLD, 0.02);

		// 稀有矿：1.5%，和远古残骸一致
		rule(Blocks.DIAMOND_ORE, CloverTier.DIAMOND, 0.015);
		rule(Blocks.DEEPSLATE_DIAMOND_ORE, CloverTier.DIAMOND, 0.015);
		rule(Blocks.EMERALD_ORE, CloverTier.EMERALD, 0.015);
		rule(Blocks.DEEPSLATE_EMERALD_ORE, CloverTier.EMERALD, 0.015);
		rule(Blocks.ANCIENT_DEBRIS, CloverTier.NETHERITE, 0.015);

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel serverLevel)) return;
			if (player == null || player.isCreative()) return;

			CloverTier tier;
			double chance;

			if (state.is(BlockTags.LOGS)) {
				tier = CloverTier.WOOD;
				chance = 0.008; // 原木量太大，压得很低
			} else {
				Rule rule = RULES.get(state.getBlock());
				if (rule == null) return;
				tier = rule.tier();
				chance = rule.chance();
			}

			// 玩家自己放下去的方块不给（顺带清掉记录）
			if (PlacedBlocksData.get(serverLevel).consume(serverLevel, pos)) return;

			if (Math.random() >= chance) return;

			ItemStack drop = new ItemStack(ModItems.forTier(tier));
			LootEngine.giveOrStore(serverLevel, player, drop);
		});
	}

	private static void rule(Block block, CloverTier tier, double chance) {
		RULES.put(block, new Rule(tier, chance));
	}

	/** 这些方块会被纳入「玩家放置追踪」，避免放了再挖无限刷。 */
	public static boolean isTracked(BlockState state) {
		return state.is(BlockTags.LOGS) || RULES.containsKey(state.getBlock());
	}
}
