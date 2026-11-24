package com.muchen.magicclover.pouch;

import com.muchen.magicclover.data.PouchData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * 幸运口袋界面：借用原版 6 行箱子布局（54 格 / 页），支持左右翻页。
 *
 * <p>菜单类型直接用原版的 {@code GENERIC_9x6}，这样客户端一定会弹出原版箱子界面；
 * 客户端再把它替换成带箭头按钮的自定义界面（见 PouchScreen）。
 * 完全不依赖自定义界面注册，避免「菜单开了但看不见」。
 */
public class PouchMenu extends ChestMenu {
	private final PouchContainer pouch;
	private int page;

	private PouchMenu(int syncId, Inventory inventory, PouchContainer container) {
		super(MenuType.GENERIC_9x6, syncId, inventory, container, 6);
		this.pouch = container;
		this.page = container.page();
	}

	public static PouchMenu create(int syncId, Inventory inventory, int page) {
		return new PouchMenu(syncId, inventory, new PouchContainer(inventory.player, page));
	}

	public int page() {
		return page;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		int next = switch (id) {
			case 0 -> page - 1;
			case 1 -> page + 1;
			default -> page;
		};
		next = Math.max(0, Math.min(PouchData.MAX_PAGES - 1, next));
		if (next == page) return false;

		page = next;
		pouch.setPage(next);
		broadcastChanges();
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		pouch.setChanged();
	}

	/**
	 * 口袋只能取、不能放。
	 *
	 * <p>不能只在点击入口判断——原版有些合并分支不走 mayPlace 检查（右键拆分放入就是），
	 * 所以这里改成「先让原版处理，事后对比快照把多出来的回滚并退还」。
	 * 这样不管原版内部走哪条路径都堵得住，取东西也完全不受影响。
	 */
	@Override
	public void clicked(int slotId, int button, ClickType type, Player player) {
		ItemStack[] before = new ItemStack[PouchData.PAGE_SIZE];
		for (int i = 0; i < before.length; i++) {
			before[i] = pouch.getItem(i).copy();
		}

		super.clicked(slotId, button, type, player);

		boolean reverted = false;
		for (int i = 0; i < before.length; i++) {
			ItemStack now = pouch.getItem(i);
			ItemStack old = before[i];

			boolean grew = now.getCount() > old.getCount();
			boolean typeChanged = !now.isEmpty() && !ItemStack.isSameItemSameComponents(now, old);
			if (!grew && !typeChanged) continue;

			// 多出来的部分整格回滚，并退还到玩家背包（背包也满就掉在脚下）
			ItemStack extra = now.copy();
			if (!typeChanged) {
				extra.setCount(now.getCount() - old.getCount());
			}
			pouch.setItem(i, old);

			ItemStack rest = com.muchen.magicclover.util.LootEngine.addToMainInventory(player, extra);
			if (!rest.isEmpty()) player.drop(rest, false);
			reverted = true;
		}

		if (reverted) {
			this.broadcastChanges();
		}
	}
}
