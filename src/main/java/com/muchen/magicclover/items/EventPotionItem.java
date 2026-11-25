package com.muchen.magicclover.items;

import com.muchen.magicclover.config.ModConfig;
import com.muchen.magicclover.event.EventSystem;
import com.muchen.magicclover.util.Msg;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * 事件药水：走原版饮用逻辑（喝的动作 + 原版饮下音效），喝完后获得事件加成。
 *
 * <p>分两档：
 * <ul>
 *   <li>幸运药剂（低级）——前期可用，20% 概率 / 60 秒，无光晕</li>
 *   <li>事件药水（高级）——需烈焰粉，50% 概率 / 120 秒，带附魔光晕作为视觉区分</li>
 * </ul>
 * 光晕由 {@code ENCHANTMENT_GLINT_OVERRIDE} 组件提供，纯视觉，不带任何真实附魔。
 *
 * <p>1.21 之后「喝的时长/动画/持续音效」由 CONSUMABLE 数据组件驱动，
 * 没有这个组件就不会有边喝边响的音效，所以必须在构造时挂上。
 */
public class EventPotionItem extends Item {
	private final boolean advanced;

	public EventPotionItem(Properties properties, boolean advanced) {
		super(withComponents(properties, advanced));
		this.advanced = advanced;
	}

	private static Properties withComponents(Properties properties, boolean advanced) {
		Properties p = properties.component(DataComponents.CONSUMABLE, Consumable.builder()
				.consumeSeconds(1.6f)
				.animation(ItemUseAnimation.DRINK)
				.sound(SoundEvents.GENERIC_DRINK)
				.hasConsumeParticles(true)
				.build());
		if (advanced) {
			// 只加附魔光晕做视觉区分，没有任何实际附魔效果
			p = p.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return p;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 32;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.DRINK;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!level.isClientSide() && entity instanceof Player player && level instanceof ServerLevel serverLevel) {
			ModConfig cfg = ModConfig.get();
			int seconds = advanced ? cfg.buffDuration : cfg.lesserBuffDuration;
			double chance = advanced ? cfg.eventChanceBuffed : cfg.lesserEventChance;
			EventSystem.applyBuff(player, serverLevel, seconds, chance);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.5f, 1.0f);
			Msg.send(player, Component.literal((advanced ? "§d[事件药水] " : "§a[幸运药剂] ")
					+ "§f接下来 §e" + seconds + " §f秒内，开幸运草触发特殊事件的概率提升到 §e"
					+ (int) (chance * 100) + "%"));
		}
		stack.consume(1, entity);
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		ModConfig cfg = ModConfig.get();
		int seconds = advanced ? cfg.buffDuration : cfg.lesserBuffDuration;
		double chance = advanced ? cfg.eventChanceBuffed : cfg.lesserEventChance;
		tooltipComponents.accept(Component.translatable(advanced
				? "tooltip.magicclover.potion.advanced"
				: "tooltip.magicclover.potion.lesser"));
		tooltipComponents.accept(Component.translatable("tooltip.magicclover.potion.duration",
				seconds, (int) (chance * 100)));
		if (!advanced) {
			tooltipComponents.accept(Component.translatable("tooltip.magicclover.potion.lesser_hint"));
		}
		tooltipComponents.accept(Component.translatable("tooltip.magicclover.potion.usage"));
	}

	public boolean isAdvanced() { return advanced; }
}
