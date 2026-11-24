package com.muchen.magicclover.pouch;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** 打开口袋界面（走原版箱子菜单，保证客户端一定有界面）。 */
public final class PouchOpener {
	private PouchOpener() {}

	/** 界面标题，客户端靠它识别「这是口袋界面」。 */
	public static final String TITLE = "\u5e78\u8fd0\u53e3\u888b";

	public static void open(ServerPlayer player) {
		com.muchen.magicclover.MagicCloverMod.LOGGER.info("[pouch] 打开口袋界面: {}", player.getName().getString());
		player.openMenu(new SimpleMenuProvider(
				(syncId, inventory, p) -> PouchMenu.create(syncId, inventory, 0),
				Component.literal(TITLE)));
	}
}
