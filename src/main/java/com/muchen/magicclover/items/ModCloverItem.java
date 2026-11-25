package com.muchen.magicclover.items;

import com.muchen.magicclover.config.ModConfig;
import com.muchen.magicclover.event.EventSystem;
import com.muchen.magicclover.util.CloverTier;
import com.muchen.magicclover.util.Fx;
import com.muchen.magicclover.util.LootEngine;
import com.muchen.magicclover.util.Msg;
import com.muchen.magicclover.util.TaczCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * 模组幸运草：优先开出真正的模组成品（装了 TACZ 就是枪械），否则从模组物品池等概率抽取。
 */
public class ModCloverItem extends Item {
	public ModCloverItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) return InteractionResult.PASS;

		ServerLevel serverLevel = (ServerLevel) level;
		ItemStack stack = player.getItemInHand(hand);

		boolean eventTriggered = EventSystem.tryTrigger(serverLevel, player);

		ItemStack loot = null;

		// 1) 装了 TACZ 且配置允许：优先给 TACZ 的成品（枪 / 弹药 / 配件 / 弹药盒 / 工作台类）
		if (TaczCompat.isAvailable() && Math.random() < ModConfig.get().modCloverTaczChance) {
			loot = TaczCompat.randomLoot();
		}
		// 2) 否则走普通模组物品池
		if (loot == null) {
			loot = LootEngine.pickModItem();
		}
		// 3) 池子空（比如只装了优化类模组）就兜底给原版好东西，避免白开
		if (loot == null) {
			loot = LootEngine.pickVanilla(CloverTier.IRON);
		}

		if (loot == null || loot.isEmpty()) {
			Msg.send(player, Component.translatable("msg.magicclover.mod_clover.empty"));
			return InteractionResult.PASS;
		}

		LootEngine.giveOrStore(serverLevel, player, loot);
		Fx.cloverUse(serverLevel, player, hand, 9);

		if (!eventTriggered) {
			Msg.send(player, Component.translatable("msg.magicclover.mod_clover.opened", loot.getDisplayName()));
		}
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		tooltipComponents.accept(Component.translatable("tooltip.magicclover.mod_clover.desc"));
		if (TaczCompat.isAvailable()) {
			tooltipComponents.accept(Component.translatable("tooltip.magicclover.mod_clover.tacz"));
		}
		tooltipComponents.accept(Component.translatable("tooltip.magicclover.common.event_chance"));
	}
}
