package com.muchen.magicclover.items;

import com.muchen.magicclover.config.ModConfig;
import com.muchen.magicclover.util.Fx;
import com.muchen.magicclover.util.LootEngine;
import com.muchen.magicclover.util.Msg;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;

import java.util.List;
import java.util.function.Consumer;

/**
 * 枯叶幸运草：赌徒草。
 *
 * <p>大概率倒霉（扣血 / 负面 buff / 掉一件物品到地上），小概率血赚。
 * 所有惩罚都有下限：扣血至少留 1 心，掉落的物品不会销毁。</p>
 */
public class WitheredCloverItem extends Item {
	private static final List<Holder<MobEffect>> BAD_EFFECTS = List.of(
			MobEffects.WEAKNESS, MobEffects.SLOWNESS, MobEffects.HUNGER, MobEffects.BLINDNESS,
			MobEffects.WITHER, MobEffects.POISON);

	private static final Item[] SMALL_LOOT = {
			Items.COAL, Items.RAW_IRON, Items.BREAD, Items.APPLE, Items.LEATHER,
			Items.STRING, Items.FEATHER, Items.WHEAT, Items.IRON_INGOT, Items.OAK_PLANKS
	};

	private static final Item[] JACKPOT = {
			Items.NETHER_STAR, Items.ELYTRA, Items.TOTEM_OF_UNDYING, Items.BEACON,
			Items.HEART_OF_THE_SEA, Items.NETHERITE_INGOT, Items.DIAMOND_BLOCK,
			Items.ENCHANTED_BOOK
	};

	public WitheredCloverItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) return InteractionResult.PASS;
		ServerLevel serverLevel = (ServerLevel) level;
		stackConsume(player, hand);

		ModConfig cfg = ModConfig.get();
		double roll = Math.random();
		double jackpotChance = clamp(cfg.witheredJackpotChance, 0.0, 0.5);
		double goodChance = clamp(cfg.witheredGoodChance, 0.0, 1.0 - jackpotChance);

		if (roll < jackpotChance) {
			jackpot(serverLevel, player, hand);
		} else if (roll < jackpotChance + goodChance) {
			smallWin(serverLevel, player, hand);
		} else {
			badLuck(serverLevel, player, hand);
		}
		return InteractionResult.SUCCESS;
	}

	/** 血赚：一件真正值钱的东西 + 全套庆祝特效。 */
	private void jackpot(ServerLevel level, Player player, InteractionHand hand) {
		Item prize = JACKPOT[(int) (Math.random() * JACKPOT.length)];
		ItemStack stack = new ItemStack(prize);
		if (prize == Items.ENCHANTED_BOOK) {
			LootEngine.enchantRandomly(level, stack);
		}
		LootEngine.giveOrStore(level, player, stack);
		Fx.jackpot(level, player, hand);
		Msg.send(player, Component.translatable("msg.magicclover.withered.jackpot",
				stack.getDisplayName()));
	}

	/** 小赚：一组基础材料。 */
	private void smallWin(ServerLevel level, Player player, InteractionHand hand) {
		Item item = SMALL_LOOT[(int) (Math.random() * SMALL_LOOT.length)];
		ItemStack stack = new ItemStack(item, 2 + (int) (Math.random() * 7));
		LootEngine.giveOrStore(level, player, stack);
		Fx.burst(level, player, ParticleTypes.HAPPY_VILLAGER, 14);
		Msg.send(player, Component.translatable("msg.magicclover.withered.small",
				stack.getDisplayName(), stack.getCount()));
	}

	/** 倒霉：三种惩罚里随机一个，都留后路。 */
	private void badLuck(ServerLevel level, Player player, InteractionHand hand) {
		switch ((int) (Math.random() * 5)) {
			case 0 -> hurtButKeepOneHeart(player);
			case 1 -> applyBadEffect(player);
			case 2 -> dropRandomItem(level, player);
			case 3 -> loseExperience(player);
			default -> damageRandomGear(player);
		}
		Fx.burst(level, player, ParticleTypes.SMOKE, 18);
	}

	/** 扣 1~3 颗心，但至少留 1 心——绝不直接杀死玩家。 */
	private void hurtButKeepOneHeart(Player player) {
		float hearts = 2.0f + (float) Math.random() * 3.0f; // 2~5 = 4~10 点伤害
		float damage = Math.min(hearts * 2.0f, Math.max(0.0f, player.getHealth() - 1.0f));
		if (damage > 0.0f) {
			player.hurt(player.damageSources().generic(), damage);
		}
		Msg.send(player, Component.translatable("msg.magicclover.withered.bad.damage",
				(int) (damage / 2.0f)));
	}

	/** 30~60 秒的一级负面 buff。 */
	private void applyBadEffect(Player player) {
		Holder<MobEffect> effect = BAD_EFFECTS.get((int) (Math.random() * BAD_EFFECTS.size()));
		int seconds = 60 + (int) (Math.random() * 61);
		player.addEffect(new MobEffectInstance(effect, seconds * 20, 0));
		Msg.send(player, Component.translatable("msg.magicclover.withered.bad.effect",
				Component.translatable(effect.value().getDescriptionId()), seconds));
	}

	/** 随机掉一件背包物品到地上——是惩罚，但不是销毁。 */
	private void dropRandomItem(ServerLevel level, Player player) {
		var inventory = player.getInventory();
		int slot = (int) (Math.random() * inventory.getContainerSize());
		ItemStack stack = inventory.getItem(slot);
		if (stack.isEmpty() || stack.getItem() == this) {
			Msg.send(player, Component.translatable("msg.magicclover.withered.bad.nothing"));
			return;
		}
		inventory.setItem(slot, ItemStack.EMPTY);
		Fx.dropItem(level, player.getX(), player.getY() + 0.5, player.getZ(), stack, 20);
		Msg.send(player, Component.translatable("msg.magicclover.withered.bad.drop",
				stack.getDisplayName()));
	}

	/**
	 * 按当前等级扣 20%~35% 经验，至少扣 1 级。
	 * 不清空——后期攒几百级一次归零太劝退，留个"肉疼但不毁灭"的度。
	 */
	private void loseExperience(Player player) {
		int levels = player.experienceLevel;
		if (levels <= 0) {
			Msg.send(player, Component.translatable("msg.magicclover.withered.bad.nothing"));
			return;
		}
		int lost = Math.max(1, (int) Math.ceil(levels * (0.20 + Math.random() * 0.15)));
		int remain = Math.max(0, levels - lost);
		player.experienceLevel = remain;
		player.totalExperience = Math.max(0, (int) (player.totalExperience * ((double) remain / levels)));
		player.experienceProgress = remain == 0 ? 0.0f : player.experienceProgress;
		Msg.send(player, Component.translatable("msg.magicclover.withered.bad.xp", lost));
	}

	/**
	 * 随机一件装备掉 25%~50% 耐久——不销毁，装备党也能接受。
	 * 只处理有耐久的物品，消耗品走另一个分支。
	 */
	private void damageRandomGear(Player player) {
		var inventory = player.getInventory();
		java.util.List<Integer> candidates = new java.util.ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.isDamageableItem() && stack.getItem() != this) {
				candidates.add(i);
			}
		}
		if (candidates.isEmpty()) {
			Msg.send(player, Component.translatable("msg.magicclover.withered.bad.nothing"));
			return;
		}
		int slot = candidates.get((int) (Math.random() * candidates.size()));
		ItemStack stack = inventory.getItem(slot);
		int amount = Math.max(1, (int) (stack.getMaxDamage() * (0.15 + Math.random() * 0.15)));
		int damage = Math.min(stack.getMaxDamage() - stack.getDamageValue(), amount);
		if (damage > 0) {
			stack.setDamageValue(stack.getDamageValue() + damage);
			Msg.send(player, Component.translatable("msg.magicclover.withered.bad.gear",
					stack.getDisplayName(), damage));
		} else {
			Msg.send(player, Component.translatable("msg.magicclover.withered.bad.nothing"));
		}
	}

	private void stackConsume(Player player, InteractionHand hand) {
		player.getItemInHand(hand).consume(1, player);
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context,
								TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(Component.translatable("tooltip.magicclover.withered.desc"));
		tooltip.accept(Component.translatable("tooltip.magicclover.withered.detail"));
	}
}
