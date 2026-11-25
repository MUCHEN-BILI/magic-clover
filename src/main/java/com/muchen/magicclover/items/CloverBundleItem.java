package com.muchen.magicclover.items;

import com.muchen.magicclover.ModItems;
import com.muchen.magicclover.util.Fx;
import com.muchen.magicclover.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * 随机幸运草包裹：右键抽取一个随机幸运草（可能抽回普通草）。
 */
public class CloverBundleItem extends Item {
	// 抽取池：普通草权重最高，等级越高权重越低
	private static final Item[] POOL = {
			ModItems.BASIC_CLOVER, ModItems.BASIC_CLOVER, ModItems.BASIC_CLOVER, ModItems.BASIC_CLOVER,
			ModItems.WOOD_CLOVER, ModItems.WOOD_CLOVER, ModItems.WOOD_CLOVER,
			ModItems.COAL_CLOVER, ModItems.COAL_CLOVER,
			ModItems.COPPER_CLOVER,
			ModItems.LAPIS_CLOVER,
			ModItems.IRON_CLOVER,
			ModItems.GOLD_CLOVER,
			ModItems.DIAMOND_CLOVER,
			ModItems.EMERALD_CLOVER,
			ModItems.NETHERITE_CLOVER,
			ModItems.MOD_CLOVER,
			ModItems.MOB_CLOVER
	};

	// 抽到这些就算「血赚」，给个更醒目的提示
	private static final Item[] JACKPOT = {
			ModItems.DIAMOND_CLOVER, ModItems.EMERALD_CLOVER, ModItems.NETHERITE_CLOVER,
			ModItems.MOD_CLOVER, ModItems.MOB_CLOVER
	};

	public CloverBundleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) return InteractionResult.PASS;

		ItemStack stack = player.getItemInHand(hand);
		Item drawnItem = POOL[(int) (Math.random() * POOL.length)];
		ItemStack drawn = new ItemStack(drawnItem);

		// 注意：Inventory#add 会把传入的 stack 清空，所以名字必须提前取好
		String name = drawn.getDisplayName().getString();
		boolean jackpot = isJackpot(drawnItem);

		if (!player.getInventory().add(drawn)) {
			player.drop(drawn, false);
		}

		if (jackpot) {
			Msg.send(player, Component.translatable("msg.magicclover.bundle.jackpot", name));
			Fx.jackpot((net.minecraft.server.level.ServerLevel) level, player, hand);
		} else {
			Msg.send(player, Component.translatable("msg.magicclover.bundle.opened", name));
			Fx.cloverUse((net.minecraft.server.level.ServerLevel) level, player, hand, 4);
		}

		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	private boolean isJackpot(Item item) {
		for (Item j : JACKPOT) {
			if (j == item) return true;
		}
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		tooltipComponents.accept(Component.translatable("tooltip.magicclover.bundle.desc"));
		tooltipComponents.accept(Component.translatable("tooltip.magicclover.bundle.detail"));
	}
}
