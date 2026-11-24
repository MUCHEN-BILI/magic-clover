package com.muchen.magicclover.event;

import com.muchen.magicclover.ModItems;
import com.muchen.magicclover.data.PlacedBlocksData;
import com.muchen.magicclover.util.CloverTier;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 记录「玩家放置过的方块」，用于区分自然生成与人造方块。
 *
 * <p>原版没有方块来源信息，只能在放置时记账、破坏时查账。
 * 只记录关心的方块（原木 / 各种矿 / 远古残骸），体量很小。
 */
public final class PlacedBlocks {
	private PlacedBlocks() {}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
			if (player == null || player.isSpectator()) return InteractionResult.PASS;

			ItemStack held = player.getItemInHand(hand);
			if (!(held.getItem() instanceof BlockItem blockItem)) return InteractionResult.PASS;

			BlockPos target = hit.getBlockPos().relative(hit.getDirection());
			if (!serverLevel.isLoaded(target)) return InteractionResult.PASS;

			BlockState existing = serverLevel.getBlockState(target);
			if (!existing.canBeReplaced()) return InteractionResult.PASS;

			BlockState wouldPlace = blockItem.getBlock().defaultBlockState();
			if (!MiningDrops.isTracked(wouldPlace)) return InteractionResult.PASS;

			// 只记账，不改变原版放置逻辑
			PlacedBlocksData.get(serverLevel).mark(serverLevel, target);
			return InteractionResult.PASS;
		});
	}
}
