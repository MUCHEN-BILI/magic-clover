package com.muchen.magicclover.util;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 消息工具：统一封装服务端向玩家发送聊天栏消息。
 */
public final class Msg {
	private Msg() {}

	public static void send(Player player, Component component) {
		if (player == null) return;
		player.displayClientMessage(component, false);
	}

	public static void send(Player player, String text) {
		send(player, Component.literal(text));
	}
}
