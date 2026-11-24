package com.muchen.magicclover.event;

import com.muchen.magicclover.config.ModConfig;
import com.muchen.magicclover.util.Fx;
import com.muchen.magicclover.util.Msg;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特殊事件系统。平时低概率触发，事件药水 buff 期间大幅提高概率。
 * 触发时屏幕中央显示大字 + 粒子爆发，天降类事件从高空密集抛洒。
 */
public final class EventSystem {
	private EventSystem() {}

	private static final List<String> EVENT_NAMES = List.of(
			"矿石雨", "经验爆发", "食物雨", "附魔书雨", "幸运祝福",
			"生物伙伴", "时空跳跃", "烟花秀", "金苹果雨", "钻石雨",
			"幸运喷泉", "流浪商人", "铁傀儡护卫", "蜂群祝福", "乐音时刻", "附魔装备雨",
			"寻宝竞速", "命运抉择"
	);

	/** 重大事件用完整演出（光柱+ fireworks+ 冲击波），普通事件维持轻量演出 */
	private static final java.util.Set<Integer> GRAND_EVENTS = java.util.Set.of(9, 15);

	// ===== 事件药水 buff =====
	/** buff 状态：到期时间 + 该等级对应的事件概率 */
	private record Buff(long until, double chance) {}
	private static final Map<UUID, Buff> BUFFS = new ConcurrentHashMap<>();

	/** 当前 buff 的事件概率；没有 buff 或已过期返回 -1 */
	private static double activeChance(Player player) {
		Buff buff = BUFFS.get(player.getUUID());
		if (buff == null) return -1;
		if (player.level().getGameTime() >= buff.until()) {
			BUFFS.remove(player.getUUID());
			return -1;
		}
		return buff.chance();
	}

	public static boolean hasBuff(Player player) {
		return activeChance(player) >= 0;
	}

	/** 喝药水：seconds 秒，期间事件概率为 chance */
	public static void applyBuff(Player player, ServerLevel level, int seconds, double chance) {
		BUFFS.put(player.getUUID(), new Buff(level.getGameTime() + seconds * 20L, chance));
	}

	public static boolean tryTrigger(ServerLevel level, Player player) {
		double active = activeChance(player);
		double chance = active >= 0 ? active : ModConfig.get().eventChance;
		if (Math.random() >= chance) return false;
		triggerRandom(level, player);
		return true;
	}

	public static void triggerRandom(ServerLevel level, Player player) {
		trigger(level, player, (int) (Math.random() * EVENT_NAMES.size()));
	}

	public static void trigger(ServerLevel level, Player player, int index) {
		int i = Math.max(0, Math.min(index, EVENT_NAMES.size() - 1));
		String name = EVENT_NAMES.get(i);

		if (ModConfig.get().eventTitle) {
			showTitle(player, "§6§l" + name, "§e幸运事件已触发");
		}
		Msg.send(player, Component.literal("§6[幸运事件] §e" + name + "§f！"));

		switch (i) {
			case 0 -> oreRain(level, player);
			case 1 -> experienceBurst(level, player);
			case 2 -> foodRain(level, player);
			case 3 -> enchantedBookRain(level, player);
			case 4 -> luckBlessing(level, player);
			case 5 -> mobCompanion(level, player);
			case 6 -> timeJump(level, player);
			case 7 -> fireworks(level, player);
			case 8 -> goldenAppleRain(level, player);
			case 9 -> diamondRain(level, player);
			case 10 -> luckFountain(level, player);
			case 11 -> wanderingTrader(level, player);
			case 12 -> ironGolemGuard(level, player);
			case 13 -> beeBlessing(level, player);
			case 14 -> musicMoment(level, player);
			case 15 -> enchantedGearRain(level, player);
			case 16 -> treasureRush(level, player);
			case 17 -> fateChoice(level, player);
			default -> oreRain(level, player);
		}

		// 重大事件追加一层完整演出，让「哇」的瞬间更明显
		if (GRAND_EVENTS.contains(i)) Fx.fanfare(level, player);
	}

	/** 屏幕中央大字。 */
	private static void showTitle(Player player, String title, String subtitle) {
		if (!(player instanceof ServerPlayer sp)) return;
		sp.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 12));
		sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
		if (subtitle != null && !subtitle.isEmpty()) {
			sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
		}
	}

	// ===== 天降 =====

	private static void rain(ServerLevel level, Player player, Item item, int count, double radius) {
		rainStacks(level, player, new ItemStack(item), count, radius);
	}

	/**
	 * 天降物品：从更高处（18~30 格）落下，落点带拖尾粒子，整体更有「天降」的画面感。
	 */
	private static void rainStacks(ServerLevel level, Player player, ItemStack template, int count, double radius) {
		for (int n = 0; n < count; n++) {
			double angle = Math.random() * Math.PI * 2.0;
			double dist = radius * Math.sqrt(Math.random());
			double x = player.getX() + Math.cos(angle) * dist;
			double z = player.getZ() + Math.sin(angle) * dist;
			double y = player.getY() + 18.0 + Math.random() * 12.0;
			Fx.dropItem(level, x, y, z, template.copy(), 15);
		}
	}

	private static void oreRain(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.CRIT, 40);
		rain(level, player, Items.RAW_IRON, 14, 11);
		rain(level, player, Items.RAW_COPPER, 10, 11);
		rain(level, player, Items.COAL, 16, 12);
	}

	private static void diamondRain(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.TOTEM_OF_UNDYING, 30);
		rain(level, player, Items.DIAMOND, 10, 8);
	}

	private static void goldenAppleRain(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.HEART, 20);
		rain(level, player, Items.GOLDEN_APPLE, 6, 7);
	}

	private static void foodRain(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.HAPPY_VILLAGER, 25);
		rain(level, player, Items.BREAD, 14, 9);
		rain(level, player, Items.COOKED_BEEF, 10, 9);
		rain(level, player, Items.APPLE, 8, 9);
	}

	/**
	 * 附魔书雨：真书假书混着掉。
	 * 真书带 1~3 条随机附魔；假书就是原版那种「没有附魔内容」的空壳。
	 */
	private static void enchantedBookRain(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.ENCHANT, 50);

		int total = 8;
		int real = 0;
		for (int i = 0; i < total; i++) {
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			if (Math.random() < 0.6) {
				applyEnchantments(level, book, 1 + level.getRandom().nextInt(3), true);
				real++;
			}
			double angle = Math.random() * Math.PI * 2.0;
			double dist = 7 * Math.sqrt(Math.random());
			double x = player.getX() + Math.cos(angle) * dist;
			double z = player.getZ() + Math.sin(angle) * dist;
			double y = player.getY() + 12.0 + Math.random() * 8.0;
			ItemEntity e = new ItemEntity(level, x, y, z, book);
			e.setPickUpDelay(15);
			level.addFreshEntity(e);
		}

		int fake = total - real;
		if (fake > 0) {
			Msg.send(player, Component.literal("§7" + total + " 本书里 §a" + real
					+ " §7本有附魔，§8" + fake + " §7本是空壳——小心别被骗了"));
		} else {
			Msg.send(player, Component.literal("§a这一波全是真货，赚了！"));
		}
	}

	private static void enchantedGearRain(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.ENCHANTED_HIT, 40);
		Item[] pool = {
				Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE,
				Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_HELMET, Items.DIAMOND_BOOTS
		};
		for (int i = 0; i < 5; i++) {
			ItemStack gear = new ItemStack(pool[(int) (Math.random() * pool.length)]);
			applyEnchantments(level, gear, 1 + level.getRandom().nextInt(3), false);
			double angle = Math.random() * Math.PI * 2.0;
			double dist = 6 * Math.sqrt(Math.random());
			ItemEntity e = new ItemEntity(level,
					player.getX() + Math.cos(angle) * dist,
					player.getY() + 12.0 + Math.random() * 6.0,
					player.getZ() + Math.sin(angle) * dist,
					gear);
			e.setPickUpDelay(15);
			level.addFreshEntity(e);
		}
	}

	/** 给物品挂随机附魔。stored=true 用于附魔书，false 用于装备。 */
	private static void applyEnchantments(ServerLevel level, ItemStack stack, int count, boolean stored) {
		try {
			Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			for (int i = 0; i < count; i++) {
				var picked = registry.getRandom(level.getRandom());
				if (picked.isEmpty()) continue;
				Holder<Enchantment> holder = picked.get();
				int max = Math.max(1, holder.value().getMaxLevel());
				mutable.set(holder, 1 + level.getRandom().nextInt(max));
			}
			stack.set(stored ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS,
					mutable.toImmutable());
		} catch (Throwable ignored) {
			// 附魔注册表异常时保持空壳，不影响事件本身
		}
	}

	// ===== 其它 =====

	private static void experienceBurst(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.ENCHANT, 30);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
		player.giveExperiencePoints(120 + (int) (Math.random() * 220));
	}

	private static void luckBlessing(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.HAPPY_VILLAGER, 30);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.LUCK, 5 * 60 * 20, 1));
	}

	private static void mobCompanion(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.HEART, 20);
		EntityType<?>[] types = { EntityType.COW, EntityType.SHEEP, EntityType.PIG,
				EntityType.CHICKEN, EntityType.CAT, EntityType.WOLF, EntityType.RABBIT };
		int count = 2 + (int) (Math.random() * 3);
		for (int i = 0; i < count; i++) {
			EntityType<?> type = types[(int) (Math.random() * types.length)];
			Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (entity != null) {
				entity.setPos(player.getX() + (Math.random() - 0.5) * 4,
						player.getY(),
						player.getZ() + (Math.random() - 0.5) * 4);
				level.addFreshEntity(entity);
			}
		}
	}

	private static void timeJump(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.END_ROD, 30);
		level.setDayTime(level.getDayTime() + 6000);
	}

	private static void fireworks(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.FIREWORK, 25);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0f, 1.0f);
		for (int i = 0; i < 8; i++) {
			FireworkRocketEntity rocket = new FireworkRocketEntity(level,
					player.getX() + (Math.random() - 0.5) * 8,
					player.getY() + 1.5,
					player.getZ() + (Math.random() - 0.5) * 8,
					new ItemStack(Items.FIREWORK_ROCKET));
			level.addFreshEntity(rocket);
		}
	}

	/** 幸运喷泉：经验瓶从脚边向上喷。 */
	private static void luckFountain(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.ENCHANT, 40);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
		for (int i = 0; i < 14; i++) {
			ItemEntity e = new ItemEntity(level,
					player.getX() + (Math.random() - 0.5) * 1.6,
					player.getY() + 0.4,
					player.getZ() + (Math.random() - 0.5) * 1.6,
					new ItemStack(Items.EXPERIENCE_BOTTLE));
			e.setDeltaMovement((Math.random() - 0.5) * 0.55,
					0.9 + Math.random() * 0.7,
					(Math.random() - 0.5) * 0.55);
			e.setPickUpDelay(10);
			level.addFreshEntity(e);
		}
	}

	private static void wanderingTrader(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.HAPPY_VILLAGER, 25);
		Entity trader = EntityType.WANDERING_TRADER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (trader != null) {
			trader.setPos(player.getX() + 1.5, player.getY(), player.getZ() + 1.5);
			level.addFreshEntity(trader);
		}
	}

	private static void ironGolemGuard(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.CRIT, 30);
		Entity golem = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (golem != null) {
			golem.setPos(player.getX() + 1.5, player.getY(), player.getZ() + 1.5);
			level.addFreshEntity(golem);
		}
	}

	private static void beeBlessing(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.HAPPY_VILLAGER, 30);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEE_LOOP, SoundSource.PLAYERS, 0.6f, 1.4f);
		for (int i = 0; i < 3; i++) {
			Entity bee = EntityType.BEE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (bee != null) {
				bee.setPos(player.getX() + (Math.random() - 0.5) * 3,
						player.getY() + 1.0,
						player.getZ() + (Math.random() - 0.5) * 3);
				level.addFreshEntity(bee);
			}
		}
	}

	private static void musicMoment(ServerLevel level, Player player) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
		for (int i = 0; i < 24; i++) {
			double angle = i / 24.0 * Math.PI * 2.0;
			double r = 1.6 + Math.random() * 1.2;
			Fx.at(level,
					player.getX() + Math.cos(angle) * r,
					player.getY() + 0.6 + Math.random() * 1.6,
					player.getZ() + Math.sin(angle) * r,
					ParticleTypes.NOTE, 3, 0.1);
		}
	}

	// ===== 参与型事件 =====

	/**
	 * 参与型事件的掉落物统一投放：加发光轮廓（穿墙可见）+ 头顶光柱，
	 * 解决「提示让捡东西，但根本不知道东西在哪」的问题。
	 */
	private static void dropMarked(ServerLevel level, double x, double y, double z, ItemStack stack, int pickUpDelay) {
		ItemEntity item = Fx.dropItem(level, x, y, z, stack, pickUpDelay);
		if (ModConfig.get().eventItemMarker) {
			EventMarkers.mark(item, 20);
		}
	}

	/**
	 * 寻宝竞速：把好东西撒在玩家周围更大的一圈，得跑过去捡才拿得到。
	 * 每份奖励都带白色发光轮廓和竖直光柱，隔着树和坡也能看到方位。
	 */
	private static void treasureRush(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.HAPPY_VILLAGER, 40);
		Msg.send(player, Component.literal("§6[幸运事件] §f宝藏散落在四周！§e白框 + 光柱 §f就是它们，快跑过去捡！"));
		if (ModConfig.get().eventTitle) {
			showTitle(player, "§a§l寻宝竞速", "§e寻找发光的宝藏 · 6 处");
		}

		ItemStack[] loot = {
				new ItemStack(Items.DIAMOND, 8),
				new ItemStack(Items.EMERALD, 8),
				new ItemStack(Items.GOLD_INGOT, 16),
				new ItemStack(Items.ENCHANTED_BOOK),
				new ItemStack(Items.ENDER_PEARL, 4),
				new ItemStack(Items.IRON_INGOT, 32)
		};
		for (ItemStack stack : loot) {
			double angle = Math.random() * Math.PI * 2.0;
			double dist = 8 + Math.random() * 10;
			double x = player.getX() + Math.cos(angle) * dist;
			double z = player.getZ() + Math.sin(angle) * dist;
			dropMarked(level, x, player.getY() + 14 + Math.random() * 6, z, stack, 5);
			Fx.at(level, x, player.getY() + 0.5, z, ParticleTypes.END_ROD, 12, 0.6);
		}
	}

	/**
	 * 命运抉择：天降两个包裹，一个稳妥（固定好东西），一个赌博（血赚或空手）。
	 * 玩家必须自己跑过去选一个捡——奖励就写在物品名字里，选择权完全在玩家。
	 */
	private static void fateChoice(ServerLevel level, Player player) {
		Fx.burst(level, player, ParticleTypes.ENCHANT, 40);
		if (ModConfig.get().eventTitle) {
			showTitle(player, "§d§l命运抉择", "§e两个发光包裹 · 只能选一个");
		}
		Msg.send(player, Component.literal("§6[幸运事件] §f前方两个 §e发光包裹§f：§a绿名=稳妥 §d紫名=赌博§f，走过去选一个捡！"));

		// 稳妥袋：稳定的中上奖励
		ItemStack safe = new ItemStack(Items.DIAMOND, 6);
		safe.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
				Component.literal("§a[稳妥袋] §f钻石 ×6"));
		// 赌博袋：一半概率血赚、一半概率只有少量
		boolean jackpot = Math.random() < 0.5;
		ItemStack gamble = jackpot
				? new ItemStack(Items.NETHERITE_INGOT, 3)
				: new ItemStack(Items.ROTTEN_FLESH, 4);
		gamble.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
				Component.literal(jackpot ? "§d[赌博袋] §f???" : "§d[赌博袋] §f???"));

		// 左右各放一个，间隔明显，方便玩家二选一
		double a = Math.random() * Math.PI * 2.0;
		double dx = Math.cos(a) * 4;
		double dz = Math.sin(a) * 4;
		dropMarked(level, player.getX() + dx, player.getY() + 12, player.getZ() + dz, safe, 5);
		dropMarked(level, player.getX() - dx, player.getY() + 12, player.getZ() - dz, gamble, 5);
		Fx.at(level, player.getX() + dx, player.getY() + 0.5, player.getZ() + dz, ParticleTypes.HAPPY_VILLAGER, 20, 0.8);
		Fx.at(level, player.getX() - dx, player.getY() + 0.5, player.getZ() - dz, ParticleTypes.ENCHANT, 20, 0.8);
	}

	public static List<String> getEventNames() {
		return new ArrayList<>(EVENT_NAMES);
	}
}
