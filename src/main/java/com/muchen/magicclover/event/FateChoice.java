package com.muchen.magicclover.event;

import com.muchen.magicclover.MagicCloverMod;
import com.muchen.magicclover.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 命运抉择：在玩家附近摆一排「箱子 — 告示牌 — 箱子」，三个方块紧挨着。
 *
 * <p>为什么用箱子而不是掉落物：掉落物会暴露答案（下界合金和腐肉外观完全不同），
 * 两个一模一样的箱子不会。开一个（或砸一个），另一个立刻塌缩，强制二选一。</p>
 *
 * <p>为什么用战利品表而不是直接塞物品：带战利品表的箱子在第一次被打开前容器是空的，
 * 物品开箱那一刻才 roll。WTHIT / Jade / TOP 这类信息模组瞄上去只能看到空箱子。</p>
 *
 * <p>选址是硬性要求：三个方块必须在同一高度、横向相邻、都是空气、下方都托得住。
 * 找不到就一直往外扩，直到 {@link #MAX_RADIUS}。</p>
 */
public final class FateChoice {
	private FateChoice() {}

	/** 最远搜索半径（格）。再远就不找了，回退掉落。 */
	private static final int MAX_RADIUS = 96;
	/** 会话超过这个时间还没被选择就自动清理（2 分钟）。 */
	private static final long TIMEOUT_TICKS = 20L * 120L;

	/** 稳妥：钻石 ×6（表名故意中性，防止信息模组暴露哪边是赌） */
	private static final ResourceKey<LootTable> TABLE_A =
			ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MagicCloverMod.MOD_ID, "chests/fate_a"));
	/** 赌博：50% 下界合金 ×3 / 50% 腐肉 ×4 */
	private static final ResourceKey<LootTable> TABLE_B =
			ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MagicCloverMod.MOD_ID, "chests/fate_b"));
	/** 空表：塌缩前替换掉，保证任何清理路径都不会吐出物品 */
	private static final ResourceKey<LootTable> TABLE_EMPTY =
			ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MagicCloverMod.MOD_ID, "chests/empty"));

	private record Session(ServerLevel level, UUID playerId,
						   BlockPos safePos, BlockPos gamblePos, BlockPos signPos, long expire) {}

	/** 一排三个方块：左箱子、中间告示牌、右箱子。 */
	private record Layout(BlockPos chestA, BlockPos sign, BlockPos chestB) {}

	private static final List<Session> SESSIONS = new ArrayList<>();
	private static boolean registered;

	public static void register() {
		if (registered) return;
		registered = true;
		ServerTickEvents.END_WORLD_TICK.register(FateChoice::tick);
		// 砸箱子也算做出选择
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level) {
				onBlockBroken(level, pos);
			}
		});
	}

	/** 尝试摆出一排箱子。成功返回 true；返回 false 时调用方应回退到掉落物。 */
	public static boolean start(ServerLevel level, Player player) {
		Layout layout = findLayout(level, player);
		if (layout == null) return false;

		if (!placeChest(level, layout.chestA(), player, TABLE_A)) return false;
		if (!placeChest(level, layout.chestB(), player, TABLE_B)) {
			clearChest(level, layout.chestA());
			level.removeBlock(layout.chestA(), false);
			return false;
		}
		BlockPos signPos = placeSign(level, player, layout.sign());
		if (signPos == null) signPos = layout.sign();

		SESSIONS.add(new Session(level, player.getUUID(),
				layout.chestA().immutable(), layout.chestB().immutable(), signPos.immutable(),
				level.getGameTime() + TIMEOUT_TICKS));
		return true;
	}

	// ===== 选址：硬性要求三连排 =====

	private static Layout findLayout(ServerLevel level, Player player) {
		// 横排方向取玩家朝向的右手边，这样告示牌正对玩家
		Direction side = player.getDirection().getClockWise();
		BlockPos origin = player.blockPosition();

		for (int r = 3; r <= MAX_RADIUS; r++) {
			int samples = Math.max(12, Math.min(72, r * 4));
			for (int i = 0; i < samples; i++) {
				double angle = (Math.PI * 2.0 * i) / samples + r * 0.618;
				int cx = origin.getX() + (int) Math.round(Math.cos(angle) * r);
				int cz = origin.getZ() + (int) Math.round(Math.sin(angle) * r);

				int cy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
				BlockPos center = new BlockPos(cx, cy, cz);

				if (center.distSqr(origin) < 9) continue;      // 别贴脸生成
				if (!canPlaceAt(level, player, center)) continue;

				BlockPos a = center.relative(side);
				BlockPos b = center.relative(side.getOpposite());
				// 两侧必须在同一高度且同样可放置，否则这一排作废，继续往外找
				if (!canPlaceAt(level, player, a)) continue;
				if (!canPlaceAt(level, player, b)) continue;

				return new Layout(a, center, b);
			}
		}
		return null;
	}

	/** 这一格能不能放方块：空气 + 上方也是空气 + 下方托得住（固体或水面）。 */
	private static boolean canPlaceAt(ServerLevel level, Player player, BlockPos pos) {
		if (!level.getBlockState(pos).isAir()) return false;
		if (!level.getBlockState(pos.above()).isAir()) return false;

		BlockPos below = pos.below();
		BlockState support = level.getBlockState(below);
		boolean standable = support.isFaceSturdy(level, below, Direction.UP) || support.is(Blocks.WATER);
		if (!standable) return false;

		return level.mayInteract(player, pos);
	}

	// ===== 摆放 =====

	private static boolean placeChest(ServerLevel level, BlockPos pos, Player player, ResourceKey<LootTable> table) {
		if (!level.getBlockState(pos).isAir()) return false;
		BlockState chest = Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, player.getDirection().getOpposite());
		if (!level.setBlock(pos, chest, 3)) return false;
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity be) {
			// 只挂战利品表，不放物品：没打开之前容器是空的
			be.setLootTable(table);
			be.setLootTableSeed(level.getRandom().nextLong());
			be.setChanged();
		}
		return true;
	}

	private static BlockPos placeSign(ServerLevel level, Player player, BlockPos pos) {
		if (!level.getBlockState(pos).isAir()) return null;

		int rotation = Math.round((180.0f + player.getYRot()) * 16.0f / 360.0f) & 15;
		BlockState sign = Blocks.OAK_SIGN.defaultBlockState()
				.setValue(StandingSignBlock.ROTATION, rotation);
		if (!level.setBlock(pos, sign, 3)) return null;

		if (level.getBlockEntity(pos) instanceof SignBlockEntity sbe) {
			Component line = Component.translatable("msg.magicclover.event.fate_choice.sign");
			Component[] front = {line, Component.empty(), Component.empty(), Component.empty()};
			Component[] back = {Component.empty(), Component.empty(), Component.empty(), Component.empty()};
			sbe.setText(new SignText(front, back, DyeColor.BLACK, false), true);
			sbe.setChanged();
		}
		return pos;
	}

	// ===== 轮询与结算 =====

	private static void tick(ServerLevel level) {
		if (SESSIONS.isEmpty()) return;
		long now = level.getGameTime();
		Iterator<Session> it = SESSIONS.iterator();
		while (it.hasNext()) {
			Session s = it.next();
			if (s.level() != level) continue;

			if (now > s.expire()) {
				cleanUp(level, s);
				it.remove();
				continue;
			}
			if (now % 10L != 0L) continue;

			boolean safeOpen = openCount(level, s.safePos()) > 0;
			boolean gambleOpen = openCount(level, s.gamblePos()) > 0;
			if (safeOpen == gambleOpen) continue;

			BlockPos other = safeOpen ? s.gamblePos() : s.safePos();
			collapse(level, other, s.signPos());
			announce(level, s);
			it.remove();
		}
	}

	private static int openCount(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity)) return 0;
		return ChestBlockEntity.getOpenCount(level, pos);
	}

	private static void onBlockBroken(ServerLevel level, BlockPos pos) {
		if (SESSIONS.isEmpty()) return;
		Iterator<Session> it = SESSIONS.iterator();
		while (it.hasNext()) {
			Session s = it.next();
			if (s.level() != level) continue;
			if (pos.equals(s.safePos())) {
				collapse(level, s.gamblePos(), s.signPos());
				announce(level, s);
				it.remove();
				return;
			}
			if (pos.equals(s.gamblePos())) {
				collapse(level, s.safePos(), s.signPos());
				announce(level, s);
				it.remove();
				return;
			}
		}
	}

	/** 另一个箱子塌缩：粒子往里收 + 音效 + 清空后移除方块。 */
	private static void collapse(ServerLevel level, BlockPos pos, BlockPos signPos) {
		Vec3 center = Vec3.atCenterOf(pos);
		for (int i = 0; i < 26; i++) {
			double a = Math.random() * Math.PI * 2.0;
			double r = 1.2 + Math.random() * 0.8;
			level.sendParticles(ParticleTypes.END_ROD,
					center.x + Math.cos(a) * r, center.y - 0.4 + Math.random() * 1.0, center.z + Math.sin(a) * r,
					1, -Math.cos(a) * 0.12, 0.06, -Math.sin(a) * 0.12, 0.02);
		}
		level.playSound(null, center.x, center.y, center.z,
				SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 0.9f, 0.7f);

		clearChest(level, pos);
		level.removeBlock(pos, false);
		if (signPos != null) level.removeBlock(signPos, false);
	}

	private static void announce(ServerLevel level, Session s) {
		Player player = level.getServer().getPlayerList().getPlayer(s.playerId());
		if (player != null) {
			Msg.send(player, Component.translatable("msg.magicclover.event.fate_choice.chosen"));
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
		}
	}

	/** 超时清场：把没被选的箱子一起收掉，避免地上留垃圾。 */
	private static void cleanUp(ServerLevel level, Session s) {
		if (openCount(level, s.safePos()) == 0) {
			clearChest(level, s.safePos());
			level.removeBlock(s.safePos(), false);
		}
		if (openCount(level, s.gamblePos()) == 0) {
			clearChest(level, s.gamblePos());
			level.removeBlock(s.gamblePos(), false);
		}
		if (s.signPos() != null) level.removeBlock(s.signPos(), false);
	}

	/**
	 * 拆箱子前的清场：战利品表换成空表，再清空已有物品。
	 * 双保险——不管是 removeBlock 触发容器掉落，还是别的路径读内容，都拿不到东西。
	 */
	private static void clearChest(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity be) {
			be.setLootTable(TABLE_EMPTY);
			be.setLootTableSeed(0L);
			be.clearContent();
			be.setChanged();
		}
	}
}
