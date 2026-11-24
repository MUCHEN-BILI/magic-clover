package com.muchen.magicclover.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.muchen.magicclover.net.OpenPouchPayload;
import com.muchen.magicclover.pouch.PouchOpener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import org.lwjgl.glfw.GLFW;

/** 客户端：P 键开袋、把原版箱子界面替换成带箭头的口袋界面。 */
public class MagicCloverClient implements ClientModInitializer {
	public static KeyMapping pouchKey;

	@Override
	public void onInitializeClient() {
		pouchKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.magicclover.pouch",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (pouchKey.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new OpenPouchPayload());
				}
			}

			// 服务端用原版箱子菜单打开口袋，客户端这边换成带翻页按钮的界面
			if (client.player != null
					&& client.screen instanceof ContainerScreen containerScreen
					&& PouchOpener.TITLE.equals(containerScreen.getTitle().getString())
					&& client.player.containerMenu instanceof ChestMenu chestMenu) {
				PouchScreen.resetPage();
				client.setScreen(new PouchScreen(chestMenu, client.player.getInventory(),
						containerScreen.getTitle()));
			}
		});
	}
}
