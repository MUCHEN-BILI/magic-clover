package com.muchen.magicclover.items;

import com.muchen.magicclover.util.Msg;
import com.muchen.magicclover.event.EventSystem;
import com.muchen.magicclover.util.Fx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * 生物幸运草：随机生成一只原版生物（不分等级，避免模组生物崩坏）。
 */
public class MobCloverItem extends Item {
	// 只放原版生物，排序：友好/中性为主，少量敌对增加趣味
	private static final EntityType<?>[] MOB_TYPES = {
			EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN,
			EntityType.RABBIT, EntityType.WOLF, EntityType.CAT, EntityType.HORSE,
			EntityType.DONKEY, EntityType.LLAMA, EntityType.FOX,
			EntityType.BEE, EntityType.PANDA, EntityType.OCELOT, EntityType.GOAT,
			EntityType.CAMEL, EntityType.FROG, EntityType.TADPOLE, EntityType.SNIFFER,
			EntityType.ARMADILLO, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM,
			EntityType.VILLAGER, EntityType.BAT, EntityType.ENDERMAN, EntityType.SILVERFISH,
			EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER
	};

	public MobCloverItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) return InteractionResult.PASS;

		ServerLevel serverLevel = (ServerLevel) level;
		ItemStack stack = player.getItemInHand(hand);

		boolean eventTriggered = EventSystem.tryTrigger(serverLevel, player);

		EntityType<?> type = MOB_TYPES[(int) (Math.random() * MOB_TYPES.length)];
		Entity entity = type.create(serverLevel, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
		if (entity != null) {
			entity.setPos(player.getX() + 1, player.getY(), player.getZ() + 1);
			serverLevel.addFreshEntity(entity);
			if (!eventTriggered) {
				String name = entity.getName().getString();
				Msg.send(player, Component.literal("§a[生物幸运草] §f召唤了 §7" + name));
			}
		}
		Fx.cloverUse(serverLevel, player, hand, 6);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		tooltipComponents.accept(Component.literal("§7右键召唤 §a随机原版生物"));
		tooltipComponents.accept(Component.literal("§7仅原版生物，不含模组生物"));
	}
}
