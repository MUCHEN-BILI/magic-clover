package com.muchen.magicclover.data;

import com.mojang.serialization.Codec;
import com.muchen.magicclover.MagicCloverMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 幸运口袋：每个玩家一个「溢出仓库」。
 *
 * <p>只在背包放不下时由模组自动存入，玩家不能手动往里塞。
 * 存放在主世界存档数据里，跟玩家绑定，不占背包。
 */
public class PouchData extends SavedData {
	/** 每页 9x6 = 54 格 */
	public static final int PAGE_SIZE = 54;
	/** 最多 54 页 */
	public static final int MAX_PAGES = 54;
	public static final int CAPACITY = PAGE_SIZE * MAX_PAGES;

	private static final Codec<Map<UUID, List<ItemStack>>> CONTENT_CODEC =
			Codec.unboundedMap(
					Codec.STRING.xmap(UUID::fromString, UUID::toString),
					ItemStack.OPTIONAL_CODEC.listOf());

	public static final Codec<PouchData> CODEC =
			CONTENT_CODEC.xmap(PouchData::new, data -> data.contents);

	public static final SavedDataType<PouchData> TYPE = new SavedDataType<>(
			"magicclover_pouch", PouchData::new, CODEC, DataFixTypes.LEVEL);

	private final Map<UUID, List<ItemStack>> contents = new HashMap<>();

	public PouchData() {
	}

	private PouchData(Map<UUID, List<ItemStack>> loaded) {
		// 反序列化出来的列表往往是不可变的（ImmutableList），
		// 直接 add 会抛 UnsupportedOperationException，这里统一转成可变列表
		loaded.forEach((uuid, list) -> contents.put(uuid, new ArrayList<>(list)));
	}

	public static PouchData get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private List<ItemStack> list(UUID owner) {
		List<ItemStack> l = contents.get(owner);
		if (l == null) {
			l = new ArrayList<>();
			contents.put(owner, l);
			return l;
		}
		// 兜底：任何不可变实现都换成 ArrayList，避免 add 抛异常
		if (!(l instanceof ArrayList)) {
			l = new ArrayList<>(l);
			contents.put(owner, l);
		}
		return l;
	}

	public int size(UUID owner) {
		return list(owner).size();
	}

	public int pagesUsed(UUID owner) {
		int n = list(owner).size();
		return Math.max(1, (n + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	public ItemStack get(UUID owner, int index) {
		List<ItemStack> l = list(owner);
		if (index < 0 || index >= l.size()) return ItemStack.EMPTY;
		ItemStack s = l.get(index);
		return s == null ? ItemStack.EMPTY : s;
	}

	public void set(UUID owner, int index, ItemStack stack) {
		List<ItemStack> l = list(owner);
		while (l.size() <= index) l.add(ItemStack.EMPTY);
		l.set(index, stack == null ? ItemStack.EMPTY : stack);
		setDirty();
	}

	/**
	 * 存入物品，返回「实际存进去的数量」。
	 * 先尝试并到已有的同类堆里，再占用空格。
	 */
	public int store(UUID owner, ItemStack stack) {
		if (stack.isEmpty()) return 0;
		List<ItemStack> l = list(owner);
		int remaining = stack.getCount();
		int max = Math.max(1, Math.min(stack.getMaxStackSize(), 64));

		// 先砍掉尾部空格，避免「第 54 格之后」这种坑
		compact(l);

		// 1) 并堆
		for (int i = 0; i < l.size() && remaining > 0; i++) {
			ItemStack slot = l.get(i);
			if (slot.isEmpty()) continue;
			if (!ItemStack.isSameItemSameComponents(slot, stack)) continue;
			int room = max - slot.getCount();
			if (room <= 0) continue;
			int move = Math.min(room, remaining);
			slot.setCount(slot.getCount() + move);
			remaining -= move;
		}

		// 2) 填中间的空格（玩家取走东西后会留下空位）
		for (int i = 0; i < l.size() && remaining > 0; i++) {
			if (!l.get(i).isEmpty()) continue;
			int move = Math.min(max, remaining);
			ItemStack copy = stack.copy();
			copy.setCount(move);
			l.set(i, copy);
			remaining -= move;
		}

		// 3) 还不够才往后追加
		while (remaining > 0 && l.size() < CAPACITY) {
			int move = Math.min(max, remaining);
			ItemStack copy = stack.copy();
			copy.setCount(move);
			l.add(copy);
			remaining -= move;
		}

		int stored = stack.getCount() - remaining;
		if (stored > 0) setDirty();
		MagicCloverMod.LOGGER.info("Pouch store: {} x{} (剩余未存 {})", stack.getItem(), stored, remaining);
		return stored;
	}

	/** 去掉尾部的空格，保证新物品总是从前往后填。 */
	private void compact(List<ItemStack> l) {
		while (!l.isEmpty() && l.get(l.size() - 1).isEmpty()) {
			l.remove(l.size() - 1);
		}
	}

	/** 直接移除某格（供容器取出时回写）。 */
	public void clearSlot(UUID owner, int index) {
		List<ItemStack> l = list(owner);
		if (index >= 0 && index < l.size()) {
			l.set(index, ItemStack.EMPTY);
			setDirty();
		}
	}

	/** 当前页是否有内容（用于界面提示）。 */
	public boolean isPageEmpty(UUID owner, int page) {
		List<ItemStack> l = list(owner);
		int from = page * PAGE_SIZE;
		int to = Math.min(l.size(), from + PAGE_SIZE);
		for (int i = from; i < to; i++) {
			if (!l.get(i).isEmpty()) return false;
		}
		return true;
	}

	@SuppressWarnings("unused")
	private static boolean sameStack(ItemStack a, ItemStack b) {
		return ItemStack.isSameItemSameComponents(a, b);
	}

	@SuppressWarnings("unused")
	private static int maxCount(ItemStack s) {
		return s.getOrDefault(DataComponents.MAX_STACK_SIZE, 64);
	}
}
