package com.muchen.magicclover.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 参与型事件（寻宝竞速 / 命运抉择）目标物的指引标记。
 *
 * <p>问题：这些事件的奖励撒在周围 8~18 格，落在草里、树后、坡下时玩家根本看不见，
 * 只能看到一句「快去捡」，体验很迷茫。</p>
 *
 * <p>做法：
 * <ol>
 *   <li>给掉落物挂上原版「发光（Glowing）」标记——就是光谱箭命中的那个效果，
 *       {@code Entity#setGlowingTag(true)}，渲染出白色轮廓，<b>隔着方块也能看见</b>；</li>
 *   <li>在物品正上方持续撒一串竖直粒子光柱（默认 20 秒），远处就能看出方位。</li>
 * </ol>
 * 发光标记会一直保留到物品被捡走或消失，光柱只持续一小段时间，避免长期满屏闪光。
 */
public final class EventMarkers {
	private EventMarkers() {}

	private record Mark(ItemEntity item, long beamUntil) {}

	private static final List<Mark> MARKS = new ArrayList<>();
	private static boolean registered;

	/** 在模组初始化时调用一次。 */
	public static void register() {
		if (registered) return;
		registered = true;
		ServerTickEvents.END_WORLD_TICK.register(EventMarkers::onWorldTick);
	}

	/**
	 * 标记一个掉落物：永久发光轮廓 + beamSeconds 秒的粒子光柱。
	 */
	public static void mark(ItemEntity item, int beamSeconds) {
		if (item == null) return;
		item.setGlowingTag(true);
		long until = item.level().getGameTime() + beamSeconds * 20L;
		MARKS.add(new Mark(item, until));
		if (!registered) register();
	}

	private static void onWorldTick(ServerLevel level) {
		if (MARKS.isEmpty()) return;
		long now = level.getGameTime();
		Iterator<Mark> it = MARKS.iterator();
		while (it.hasNext()) {
			Mark m = it.next();
			ItemEntity e = m.item();
			// 已被捡走 / 掉进虚空 / 切了维度 —— 直接清掉
			if (e == null || e.isRemoved() || !e.isAlive()) {
				it.remove();
				continue;
			}
			if (e.level() != level) continue; // 别的维度自己会 tick
			if (now >= m.beamUntil()) {
				it.remove();
				continue;
			}
			// 每 10 tick（0.5 秒）往上撒一束粒子，形成竖直光柱
			if (now % 10L == 0L) {
				for (int i = 0; i < 9; i++) {
					level.sendParticles(ParticleTypes.END_ROD,
							e.getX() + (Math.random() - 0.5) * 0.18,
							e.getY() + 0.35 + i * 0.5,
							e.getZ() + (Math.random() - 0.5) * 0.18,
							1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}
	}

	/** 换存档 / 关服时清空，避免残留引用。 */
	public static void clear() {
		MARKS.clear();
	}
}
