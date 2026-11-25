package com.muchen.magicclover.items;

import com.muchen.magicclover.util.Msg;
import com.muchen.magicclover.event.EventSystem;
import com.muchen.magicclover.util.CloverTier;
import com.muchen.magicclover.util.Fx;
import com.muchen.magicclover.util.LootEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * 原版材质等级幸运草：右键开出对应等级的原版物品池，小概率触发特殊事件。
 */
public class CloverItem extends Item {
	private final CloverTier tier;

	public CloverItem(Properties properties) {
		this(properties, CloverTier.BASIC);
	}

	public CloverItem(Properties properties, CloverTier tier) {
		super(properties);
		this.tier = tier;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) return InteractionResult.PASS;

		ServerLevel serverLevel = (ServerLevel) level;
		ItemStack stack = player.getItemInHand(hand);

		// 触发特殊事件（概率由配置与 buff 决定）
		boolean eventTriggered = EventSystem.tryTrigger(serverLevel, player);

		// 等级越高，掉落份数越多（普通 1 份，钻石以上 3 份）
		int copies = tier.getRolls();
		// 每份都是独立抽取，可能是不同物品 —— 按名字聚合，别再显示成「同一个 ×N」
		java.util.LinkedHashMap<String, Integer> lootNames = new java.util.LinkedHashMap<>();
		int toInv = 0;
		int toPouch = 0;
		int toGround = 0;
		for (int i = 0; i < copies; i++) {
			ItemStack loot = LootEngine.pickVanilla(tier);
			lootNames.merge(loot.getDisplayName().getString(), 1, Integer::sum);
			int result = LootEngine.giveOrStore(serverLevel, player, loot);
			if (result == LootEngine.GAVE_INVENTORY) toInv++;
			else if (result == LootEngine.GAVE_POUCH) toPouch++;
			else toGround++;
		}

		// 使用反馈：挥手 + 分档音效 + 粒子
		Fx.cloverUse(serverLevel, player, hand, tier.getLevel());

		if (!eventTriggered && !lootNames.isEmpty()) {
			// 把具体开出什么、多少件、分别去了哪里一次说清楚
			java.util.List<Component> parts = new java.util.ArrayList<>();
			if (toInv > 0) parts.add(Component.translatable("msg.magicclover.dest.inv", toInv));
			if (toPouch > 0) parts.add(Component.translatable("msg.magicclover.dest.pouch", toPouch));
			if (toGround > 0) parts.add(Component.translatable("msg.magicclover.dest.ground", toGround));

			MutableComponent lootComp = Component.empty();
			boolean firstItem = true;
			for (var e : lootNames.entrySet()) {
				if (!firstItem) lootComp.append(Component.literal(" §7、§f"));
				firstItem = false;
				lootComp.append(Component.literal("§7" + e.getKey()));
				if (e.getValue() > 1) lootComp.append(Component.literal(" §7×" + e.getValue()));
			}

			MutableComponent destComp = Component.empty();
			for (int i = 0; i < parts.size(); i++) {
				if (i > 0) destComp.append(Component.literal("、"));
				destComp.append(parts.get(i));
			}

			MutableComponent text = Component.translatable("msg.magicclover.clover.opened", lootComp, destComp);
			if (toPouch > 0) text.append(Component.translatable("msg.magicclover.pouch_hint"));
			Msg.send(player, text);
		}
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		int stars = tier.getStars();
		tooltipComponents.accept(Component.literal("§6" + "★".repeat(stars) + "§8" + "★".repeat(5 - stars)));
		tooltipComponents.accept(Component.translatable("tooltip.magicclover.clover.rolls", tier.getRolls()));
		tooltipComponents.accept(Component.translatable("tooltip.magicclover.clover.usage"));
	}

	public CloverTier getTier() { return tier; }
}
