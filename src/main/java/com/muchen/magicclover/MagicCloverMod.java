package com.muchen.magicclover;

import com.muchen.magicclover.command.PouchCommand;
import com.muchen.magicclover.config.ModConfig;
import com.muchen.magicclover.event.EventMarkers;
import com.muchen.magicclover.event.LootModifiers;
import com.muchen.magicclover.event.MiningDrops;
import com.muchen.magicclover.event.PlacedBlocks;
import com.muchen.magicclover.event.StructureLoot;
import com.muchen.magicclover.net.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MagicCloverMod implements ModInitializer {
	public static final String MOD_ID = "magicclover";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Magic Clover Reborn initializing...");

		// 加载配置
		ModConfig.load();

		// 初始化物品
		ModItems.initialize();

		// 幸运口袋：网络包
		ModNetworking.registerPayloads();
		ModNetworking.registerServerReceivers();

		// 注册模组专属创造标签页（不再混在实用工具页底部）
		ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(
				Registries.CREATIVE_MODE_TAB, id("creative_tab"));
		CreativeModeTab tab = FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.BASIC_CLOVER))
				.title(Component.translatable("itemGroup.magicclover"))
				.displayItems((params, output) -> {
					for (Item item : ModItems.ALL_ITEMS) output.accept(item);
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, tab);

		// 打草掉落
		LootModifiers.register();

		// 挖矿 / 砍树掉落 + 玩家放置追踪
		PlacedBlocks.register();
		MiningDrops.register();

		// 调试命令
		PouchCommand.register();

		// 原版结构箱子奖励
		StructureLoot.register();

		// 参与型事件掉落物的发光指引（轮廓 + 光柱）
		EventMarkers.register();

		LOGGER.info("Magic Clover Reborn initialized successfully!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
