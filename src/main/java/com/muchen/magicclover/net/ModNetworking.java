package com.muchen.magicclover.net;

import com.muchen.magicclover.pouch.PouchOpener;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** 网络包注册（双端都要跑这一段，客户端才能收到类型定义）。 */
public final class ModNetworking {
	private ModNetworking() {}

	public static void registerPayloads() {
		PayloadTypeRegistry.playC2S().register(OpenPouchPayload.TYPE, OpenPouchPayload.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(OpenPouchPayload.TYPE, (payload, context) -> {
			var player = context.player();
			var server = player.level().getServer();
			if (server != null) server.execute(() -> PouchOpener.open(player));
		});
	}
}
