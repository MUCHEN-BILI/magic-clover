package com.muchen.magicclover.event;

import com.muchen.magicclover.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枯叶草的「高空竞技场」参与型事件。
 *
 * <p>流程：记录玩家原位置 → 在头顶高空搭一座 11×11 平台 → 传送上去 →
 * 60 秒内走到发光终点即免罚，超时/坠落则轻罚 → 拆掉平台并送回原位置。</p>
 *
 * <p>只使用已加载区块附近的空位；平台方块坐标全部记账，结束时逐格清除。</p>
 */
public final class ArenaSystem {
	private ArenaSystem() {}

	private static final int SIZE = 11;
	private static final int SECONDS = 60;
	private static final Set<Relative> NO_RELATIVE = Set.of();

	private record Session(ServerLevel level, UUID playerId, BlockPos home, BlockPos origin,
						   BlockPos goal, List<BlockPos> placed, ItemEntity marker,
						   long deadline) {}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	private static boolean registered;

	public static void register() {
		if (registered) return;
		registered = true;
		ServerTickEvents.END_WORLD_TICK.register(ArenaSystem::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
	}

	/** 尝试开启竞技场；找不到空位或玩家已在比赛中则返回 false。 */
	public static boolean tryStart(ServerLevel level, ServerPlayer player) {
		if (SESSIONS.containsKey(player.getUUID())) return false;

		BlockPos home = player.blockPosition();
		int[][] offsets = {{0,0},{24,0},{-24,0},{0,24},{0,-24},{48,0},{-48,0},{0,48},{0,-48}};
		BlockPos origin = null;
		for (int[] off : offsets) {
			int cx = (home.getX() + off[0]) >> 4;
			int cz = (home.getZ() + off[1]) >> 4;
			if (!level.hasChunk(cx, cz)) continue;
			origin = findSpace(level, home.getX() + off[0], home.getZ() + off[1], home.getY());
			if (origin != null) break;
		}
		if (origin == null) return false;

		List<BlockPos> placed = new ArrayList<>();
		buildPlatform(level, origin, placed);

		BlockPos goal = new BlockPos(origin.getX() + 4, origin.getY() + 1, origin.getZ() + 4);
		ItemStack markerStack = new ItemStack(Items.SEA_LANTERN);
		markerStack.setCount(1);
		ItemEntity marker = new ItemEntity(level, goal.getX() + 0.5, goal.getY() + 0.9, goal.getZ() + 0.5, markerStack);
		marker.setUnlimitedLifetime();
		marker.setInvulnerable(true);
		level.addFreshEntity(marker);
		EventMarkers.mark(marker, SECONDS);

		long deadline = level.getGameTime() + SECONDS * 20L;
		SESSIONS.put(player.getUUID(),
				new Session(level, player.getUUID(), home, origin, goal, placed, marker, deadline));

		player.teleportTo(level, origin.getX() + 0.5, origin.getY() + 1.05, origin.getZ() + 0.5,
				NO_RELATIVE, player.getYRot(), player.getXRot(), false);
		Msg.send(player, Component.translatable("msg.magicclover.withered.arena.start", SECONDS));
		return true;
	}

	/** 从高空往下找一块 11×11×4 的净空。 */
	private static BlockPos findSpace(ServerLevel level, int x, int z, int playerY) {
		int max = Math.min(level.getMaxY() - 8, 312);
		int min = Math.max(210, playerY + 60);
		if (min > max) min = max - 8;
		for (int y = max; y >= min; y--) {
			boolean clear = true;
			for (int dx = -SIZE / 2 - 1; dx <= SIZE / 2 + 1 && clear; dx++) {
				for (int dz = -SIZE / 2 - 1; dz <= SIZE / 2 + 1; dz++) {
					for (int h = 0; h < 4; h++) {
						if (!level.getBlockState(new BlockPos(x + dx, y + h, z + dz)).isAir()) {
							clear = false;
							break;
						}
					}
					if (!clear) break;
				}
			}
			if (clear) return new BlockPos(x, y, z);
		}
		return null;
	}

	private static void buildPlatform(ServerLevel level, BlockPos o, List<BlockPos> placed) {
		int r = SIZE / 2;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				BlockPos p = new BlockPos(o.getX() + dx, o.getY(), o.getZ() + dz);
				level.setBlock(p, Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
				placed.add(p);
				boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
				if (edge) {
					for (int h = 1; h <= 2; h++) {
						BlockPos wall = p.above(h);
						level.setBlock(wall, Blocks.IRON_BARS.defaultBlockState(), 2);
						placed.add(wall);
					}
				}
			}
		}
		BlockPos goal = new BlockPos(o.getX() + r - 1, o.getY() + 1, o.getZ() + r - 1);
		level.setBlock(goal, Blocks.SEA_LANTERN.defaultBlockState(), 2);
		placed.add(goal);
		for (int[] c : new int[][]{{-4,-4},{4,-4},{4,4}}) {
			BlockPos lamp = new BlockPos(o.getX() + c[0], o.getY() + 1, o.getZ() + c[1]);
			level.setBlock(lamp, Blocks.SEA_LANTERN.defaultBlockState(), 2);
			placed.add(lamp);
		}
	}

	private static void tick(ServerLevel level) {
		if (SESSIONS.isEmpty()) return;
		long now = level.getGameTime();
		Iterator<Session> it = SESSIONS.values().iterator();
		while (it.hasNext()) {
			Session s = it.next();
			if (s.level() != level) continue;
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(s.playerId());
			if (player == null) {
				cleanup(s, false, null);
				it.remove();
				continue;
			}
			if (now >= s.deadline() || player.getY() < s.origin().getY() - 12) {
				cleanup(s, false, player);
				it.remove();
				continue;
			}
			if (player.distanceToSqr(s.goal().getX() + 0.5, s.goal().getY(), s.goal().getZ() + 0.5) < 6.25) {
				cleanup(s, true, player);
				it.remove();
				continue;
			}
			if (now % 10L == 0L) {
				for (int i = 0; i < 8; i++) {
					level.sendParticles(ParticleTypes.END_ROD,
							s.goal().getX() + 0.5, s.goal().getY() + 0.5 + i * 0.6, s.goal().getZ() + 0.5,
							1, 0, 0, 0, 0);
				}
			}
		}
	}

	private static void cleanup(Session s, boolean won, ServerPlayer player) {
		if (s.marker() != null && !s.marker().isRemoved()) {
			s.marker().discard();
		}
		for (BlockPos p : s.placed()) {
			s.level().setBlock(p, Blocks.AIR.defaultBlockState(), 2);
		}
		if (player != null) {
			BlockPos safe = findSafeReturn(s.level(), s.home());
			player.teleportTo(s.level(), safe.getX() + 0.5, safe.getY() + 1.05, safe.getZ() + 0.5,
					NO_RELATIVE, player.getYRot(), player.getXRot(), false);
			if (won) {
				Msg.send(player, Component.translatable("msg.magicclover.withered.arena.win"));
			} else {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30 * 20, 0));
				Msg.send(player, Component.translatable("msg.magicclover.withered.arena.lose"));
			}
		}
	}

	/** 回传点：原位置优先，被占则在附近找一格安全落脚点。 */
	private static BlockPos findSafeReturn(ServerLevel level, BlockPos home) {
		for (int dy = 0; dy <= 3; dy++) {
			BlockPos p = home.above(dy);
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
					&& !level.getBlockState(p.below()).isAir()) {
				return p;
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos p = home.offset(dx, 1, dz);
				if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
						&& !level.getBlockState(p.below()).isAir()) {
					return p;
				}
			}
		}
		return home.above(1);
	}

	private static void clearAll() {
		for (Session s : SESSIONS.values()) cleanup(s, false, null);
		SESSIONS.clear();
	}
}
