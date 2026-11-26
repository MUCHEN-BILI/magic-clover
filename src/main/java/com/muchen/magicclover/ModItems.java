package com.muchen.magicclover;

import com.muchen.magicclover.items.*;
import com.muchen.magicclover.util.CloverTier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.function.Function;

public final class ModItems {
	private ModItems() {}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MagicCloverMod.MOD_ID, name));
		T item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	// ===== 原版材质等级幸运草（只开原版物品）=====
	public static final Item BASIC_CLOVER = register("basic_clover", p -> new CloverItem(p, CloverTier.BASIC), new Item.Properties().stacksTo(64));
	public static final Item WOOD_CLOVER = register("wood_clover", p -> new CloverItem(p, CloverTier.WOOD), new Item.Properties().stacksTo(64));
	public static final Item COAL_CLOVER = register("coal_clover", p -> new CloverItem(p, CloverTier.COAL), new Item.Properties().stacksTo(64));
	public static final Item COPPER_CLOVER = register("copper_clover", p -> new CloverItem(p, CloverTier.COPPER), new Item.Properties().stacksTo(64));
	public static final Item LAPIS_CLOVER = register("lapis_clover", p -> new CloverItem(p, CloverTier.LAPIS), new Item.Properties().stacksTo(64));
	public static final Item IRON_CLOVER = register("iron_clover", p -> new CloverItem(p, CloverTier.IRON), new Item.Properties().stacksTo(64));
	public static final Item GOLD_CLOVER = register("gold_clover", p -> new CloverItem(p, CloverTier.GOLD), new Item.Properties().stacksTo(64));
	public static final Item DIAMOND_CLOVER = register("diamond_clover", p -> new CloverItem(p, CloverTier.DIAMOND), new Item.Properties().stacksTo(64));
	public static final Item EMERALD_CLOVER = register("emerald_clover", p -> new CloverItem(p, CloverTier.EMERALD), new Item.Properties().stacksTo(64));
	public static final Item NETHERITE_CLOVER = register("netherite_clover", p -> new CloverItem(p, CloverTier.NETHERITE), new Item.Properties().stacksTo(64));

	// ===== 特殊幸运草 =====
	public static final Item MOD_CLOVER = register("mod_clover", ModCloverItem::new, new Item.Properties().stacksTo(64));
	public static final Item MOB_CLOVER = register("mob_clover", MobCloverItem::new, new Item.Properties().stacksTo(64));
	/** 枯叶幸运草：打落叶堆掉落，赌博型负面草 */
	public static final Item WITHERED_CLOVER = register("withered_clover", WitheredCloverItem::new,
			new Item.Properties().stacksTo(64));

	// ===== 功能物品 =====
	public static final Item CLOVER_BUNDLE = register("clover_bundle", CloverBundleItem::new, new Item.Properties().stacksTo(16));
	/** 低级幸运药剂：前期可用，无光晕 */
	public static final Item LESSER_EVENT_POTION = register("lesser_event_potion",
			p -> new EventPotionItem(p, false), new Item.Properties().stacksTo(16));
	/** 高级事件药水：带附魔光晕（纯视觉） */
	public static final Item EVENT_POTION = register("event_potion",
			p -> new EventPotionItem(p, true), new Item.Properties().stacksTo(16));

	public static final Item[] ALL_ITEMS = {
			BASIC_CLOVER, WOOD_CLOVER, COAL_CLOVER, COPPER_CLOVER, LAPIS_CLOVER, IRON_CLOVER,
			GOLD_CLOVER, DIAMOND_CLOVER, EMERALD_CLOVER, NETHERITE_CLOVER,
			MOD_CLOVER, MOB_CLOVER, WITHERED_CLOVER, CLOVER_BUNDLE, LESSER_EVENT_POTION, EVENT_POTION
	};

	public static void initialize() {
		MagicCloverMod.LOGGER.info("Registered {} items", ALL_ITEMS.length);
	}

	/** 按等级取对应的幸运草物品。 */
	public static Item forTier(CloverTier tier) {
		return switch (tier) {
			case BASIC -> BASIC_CLOVER;
			case WOOD -> WOOD_CLOVER;
			case COAL -> COAL_CLOVER;
			case COPPER -> COPPER_CLOVER;
			case LAPIS -> LAPIS_CLOVER;
			case IRON -> IRON_CLOVER;
			case GOLD -> GOLD_CLOVER;
			case DIAMOND -> DIAMOND_CLOVER;
			case EMERALD -> EMERALD_CLOVER;
			case NETHERITE -> NETHERITE_CLOVER;
		};
	}
}
