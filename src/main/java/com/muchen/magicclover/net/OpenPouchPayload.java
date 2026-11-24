package com.muchen.magicclover.net;

import com.muchen.magicclover.MagicCloverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端按下 P 键时，请服务端打开口袋界面。 */
public record OpenPouchPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenPouchPayload> TYPE =
			new CustomPacketPayload.Type<>(MagicCloverMod.id("open_pouch"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenPouchPayload> CODEC =
			StreamCodec.unit(new OpenPouchPayload());

	@Override
	public CustomPacketPayload.Type<OpenPouchPayload> type() {
		return TYPE;
	}
}
