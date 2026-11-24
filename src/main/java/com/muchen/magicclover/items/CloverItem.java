package com.muchen.magicclover.items;

import com.muchen.magicclover.util.Msg;
import com.muchen.magicclover.event.EventSystem;
import com.muchen.magicclover.util.CloverTier;
import com.muchen.magicclover.util.Fx;
import com.muchen.magicclover.util.LootEngine;
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
			java.util.List<String> parts = new java.util.ArrayList<>();
			if (toInv > 0) parts.add("背包 " + toInv + " 件");
			if (toPouch > 0) parts.add("口袋 " + toPouch + " 件");
			if (toGround > 0) parts.add("地上 " + toGround + " 件");

			StringBuilder lootText = new StringBuilder();
			for (var e : lootNames.entrySet()) {
				if (lootText.length() > 0) lootText.append(" §7、§f");
				lootText.append("§7").append(e.getKey());
				if (e.getValue() > 1) lootText.append(" §7×").append(e.getValue());
			}

			String text = "§a[幸运草] §f开出了 §f" + lootText
					+ " §8→ §7" + String.join("、", parts);
			if (toPouch > 0) text += " §8(按 P 看口袋)";
			Msg.send(player, Component.literal(text));
		}
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		int stars = tier.getStars();
		tooltipComponents.accept(Component.literal("§6" + "★".repeat(stars) + "§8" + "★".repeat(5 - stars)));
		tooltipComponents.accept(Component.literal("§7开出 §f" + tier.getRolls() + " §7份原版物品，等级越高越好"));
		tooltipComponents.accept(Component.literal("§8右键使用 · 有几率触发特殊事件"));
	}

	public CloverTier getTier() { return tier; }
}
