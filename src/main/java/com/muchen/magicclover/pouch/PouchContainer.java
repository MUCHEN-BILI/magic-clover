package com.muchen.magicclover.pouch;

import com.muchen.magicclover.data.PouchData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 一页口袋的容器视图。
 *
 * <p>服务端：读写 {@link PouchData}（玩家真正存东西的地方）。
 * 客户端：只有本地缓存，内容由原版的槽位同步包填进来。
 */
public class PouchContainer implements Container {
	private final NonNullList<ItemStack> items = NonNullList.withSize(PouchData.PAGE_SIZE, ItemStack.EMPTY);
	private final PouchData data;
	private final UUID owner;
	private int page;

	public PouchContainer(Player player, int page) {
		this.page = page;
		if (player != null && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			this.data = PouchData.get(serverLevel);
			this.owner = player.getUUID();
		} else {
			this.data = null;
			this.owner = null;
		}
		load();
	}

	public int page() {
		return page;
	}

	/** 翻页：把新一页的内容读进本地缓存。 */
	public void setPage(int newPage) {
		this.page = newPage;
		load();
	}

	private void load() {
		if (data == null || owner == null) return;
		for (int i = 0; i < PouchData.PAGE_SIZE; i++) {
			ItemStack s = data.get(owner, page * PouchData.PAGE_SIZE + i);
			items.set(i, s == null ? ItemStack.EMPTY : s);
		}
		com.muchen.magicclover.MagicCloverMod.LOGGER.info(
				"[pouch] 载入第 {} 页，前3格: {} | {} | {} (口袋共 {} 格)",
				page + 1, items.get(0).getItem(), items.get(1).getItem(), items.get(2).getItem(),
				data.size(owner));
	}

	@Override
	public int getContainerSize() {
		return PouchData.PAGE_SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack s : items) {
			if (!s.isEmpty()) return false;
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack stack = getItem(slot);
		if (stack.isEmpty()) return ItemStack.EMPTY;
		ItemStack split = stack.split(amount);
		if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
		writeBack(slot, items.get(slot));
		return split;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack stack = getItem(slot);
		items.set(slot, ItemStack.EMPTY);
		writeBack(slot, ItemStack.EMPTY);
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < 0 || slot >= items.size()) return;
		items.set(slot, stack);
		writeBack(slot, stack);
	}

	private void writeBack(int slot, ItemStack stack) {
		if (data == null || owner == null) return;
		data.set(owner, page * PouchData.PAGE_SIZE + slot, stack);
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	/**
	 * 只能取，不能放。
	 * 口袋里的东西只由模组在背包满时自动存入，玩家手动塞进去会打乱存储顺序。
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		com.muchen.magicclover.MagicCloverMod.LOGGER.info(
				"[pouch] 拦截手动放入 slot={} item={} (服务端容器生效中)", slot, stack.getItem());
		return false;
	}

	@Override
	public void setChanged() {
		if (data != null) data.setDirty();
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < items.size(); i++) {
			setItem(i, ItemStack.EMPTY);
		}
	}
}
