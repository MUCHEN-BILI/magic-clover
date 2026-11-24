package com.muchen.magicclover.data;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记录「玩家亲手放置过的方块」坐标。
 *
 * <p>原版没有「这个方块是谁放的」这种标记，所以想实现「只有自然生成的方块才掉幸运草」
 * 就必须自己记账：玩家放置时记下坐标，破坏时查表，命中就说明是玩家放的，不给掉落。
 *
 * <p>只记录我们关心的方块类型（原木 / 各种矿 / 远古残骸），量很小。
 */
public class PlacedBlocksData extends SavedData {
	private static final Codec<Map<String, List<Long>>> CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf());

	public static final Codec<PlacedBlocksData> DATA_CODEC =
			CODEC.xmap(PlacedBlocksData::new, data -> data.serialize());

	public static final SavedDataType<PlacedBlocksData> TYPE = new SavedDataType<>(
			"magicclover_placed", PlacedBlocksData::new, DATA_CODEC, DataFixTypes.LEVEL);

	/** 维度 id -> 被玩家放置过的方块坐标 */
	private final Map<String, Set<Long>> placed = new HashMap<>();

	public PlacedBlocksData() {
	}

	private PlacedBlocksData(Map<String, List<Long>> loaded) {
		loaded.forEach((dim, longs) -> placed.put(dim, new HashSet<>(longs)));
	}

	private Map<String, List<Long>> serialize() {
		Map<String, List<Long>> out = new HashMap<>();
		placed.forEach((dim, set) -> out.put(dim, List.copyOf(set)));
		return out;
	}

	public static PlacedBlocksData get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private static String dimKey(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	public void mark(ServerLevel level, BlockPos pos) {
		placed.computeIfAbsent(dimKey(level), k -> new HashSet<>()).add(pos.asLong());
		setDirty();
	}

	/** 查询并顺带移除（方块被破坏后记录就没用了）。 */
	public boolean consume(ServerLevel level, BlockPos pos) {
		Set<Long> set = placed.get(dimKey(level));
		if (set == null) return false;
		boolean removed = set.remove(pos.asLong());
		if (removed) setDirty();
		return removed;
	}

	public boolean isPlaced(ServerLevel level, BlockPos pos) {
		Set<Long> set = placed.get(dimKey(level));
		return set != null && set.contains(pos.asLong());
	}

	public int total() {
		int n = 0;
		for (Set<Long> s : placed.values()) n += s.size();
		return n;
	}
}
