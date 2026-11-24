package com.muchen.magicclover.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 使用反馈与事件演出：挥手动作 + 分档音效 + 粒子特效。
 * 开草是瞬间完成的，没有这些反馈会显得很"空"。
 */
public final class Fx {
	private Fx() {}

	/**
	 * 开草反馈。tier 越大越隆重：低档轻快提示音 + 绿色粒子，高档钟声 + 图腾光效。
	 */
	public static void cloverUse(ServerLevel level, Player player, InteractionHand hand, int tier) {
		player.swing(hand, true);

		double x = player.getX();
		double y = player.getY() + 1.0;
		double z = player.getZ();

		SoundEvent sound;
		float pitch;
		if (tier <= 2) {
			sound = SoundEvents.ITEM_PICKUP;
			pitch = 1.0f + tier * 0.08f;
		} else if (tier <= 5) {
			sound = SoundEvents.EXPERIENCE_ORB_PICKUP;
			pitch = 1.0f + (tier - 3) * 0.1f;
		} else if (tier <= 8) {
			sound = SoundEvents.AMETHYST_BLOCK_CHIME;
			pitch = 1.0f + (tier - 6) * 0.12f;
		} else {
			sound = SoundEvents.PLAYER_LEVELUP;
			pitch = 1.0f;
		}
		level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, 0.7f, pitch);

		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z,
				8 + tier * 3, 0.5, 0.6, 0.5, 0.03);
		if (tier >= 5) {
			level.sendParticles(ParticleTypes.END_ROD, x, y, z, 12, 0.45, 0.6, 0.45, 0.04);
		}
		if (tier >= 7) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 16, 0.55, 0.7, 0.55, 0.12);
			// 高阶草额外来一圈上升光柱
			shockwave(level, x, y - 0.8, z, ParticleTypes.END_ROD, 18, 1.2);
		}
	}

	/** 抽到好东西时的"血赚"反馈：更亮的声音 + 图腾/烟花粒子。 */
	public static void jackpot(ServerLevel level, Player player, InteractionHand hand) {
		player.swing(hand, true);
		double x = player.getX();
		double y = player.getY() + 1.0;
		double z = player.getZ();

		level.playSound(null, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
				SoundSource.PLAYERS, 0.8f, 1.0f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 36, 0.7, 0.9, 0.7, 0.28);
		level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 26, 0.6, 0.7, 0.6, 0.14);
		shockwave(level, x, y - 0.8, z, ParticleTypes.HAPPY_VILLAGER, 24, 1.5);
	}

	/** 事件触发时的粒子爆发（整体升级：粒子更多、范围更大、附带地面冲击波）。 */
	public static void burst(ServerLevel level, Player player, ParticleOptions particle, int count) {
		double x = player.getX();
		double y = player.getY() + 1.0;
		double z = player.getZ();
		level.sendParticles(particle, x, y, z, count, 1.6, 1.3, 1.6, 0.18);
		shockwave(level, x, y - 0.9, z, particle, Math.max(12, count / 3), 1.4);
	}

	/** 在某个坐标撒粒子。 */
	public static void at(ServerLevel level, double x, double y, double z,
						  ParticleOptions particle, int count, double spread) {
		level.sendParticles(particle, x, y, z, count, spread, spread, spread, 0.06);
	}

	/** 地面环形冲击波：从脚下向外扩散一圈粒子。 */
	public static void shockwave(ServerLevel level, double x, double y, double z,
								 ParticleOptions particle, int count, double radius) {
		for (int i = 0; i < count; i++) {
			double angle = (double) i / count * Math.PI * 2.0;
			level.sendParticles(particle,
					x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius,
					2, 0.12, 0.25, 0.12, 0.02);
		}
	}

	/** 冲天光柱：从脚下往上升的一串粒子，用于重大事件。 */
	public static void pillar(ServerLevel level, Player player, ParticleOptions particle, int height) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		for (int i = 0; i < height * 3; i++) {
			level.sendParticles(particle,
					x + (Math.random() - 0.5) * 1.2,
					y + Math.random() * height,
					z + (Math.random() - 0.5) * 1.2,
					1, 0.0, 0.0, 0.0, 0.02);
		}
	}

	/**
	 * 重大事件的完整演出：图腾爆发 + 烟花齐射 + 冲天光柱 + 环形冲击波 + 胜利音效。
	 */
	public static void fanfare(ServerLevel level, Player player) {
		double x = player.getX();
		double y = player.getY() + 1.0;
		double z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
				SoundSource.PLAYERS, 1.0f, 1.0f);
		level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST,
				SoundSource.PLAYERS, 0.8f, 1.0f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 60, 1.0, 1.4, 1.0, 0.3);
		level.sendParticles(ParticleTypes.FIREWORK, x, y + 2, z, 40, 1.2, 1.5, 1.2, 0.15);
		pillar(level, player, ParticleTypes.END_ROD, 16);
		shockwave(level, x, y - 0.9, z, ParticleTypes.HAPPY_VILLAGER, 36, 2.0);
	}

	/** 天降物品统一生成：更高、带轻微旋转、落地前拖尾粒子。返回生成的掉落物，便于后续加标记。 */
	public static ItemEntity dropItem(ServerLevel level, double x, double y, double z, ItemStack stack, int pickUpDelay) {
		ItemEntity entity = new ItemEntity(level, x, y, z, stack);
		entity.setDeltaMovement((Math.random() - 0.5) * 0.12, -0.04, (Math.random() - 0.5) * 0.12);
		entity.setPickUpDelay(pickUpDelay);
		level.addFreshEntity(entity);
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 3, 0.15, 0.2, 0.15, 0.01);
		return entity;
	}
}
