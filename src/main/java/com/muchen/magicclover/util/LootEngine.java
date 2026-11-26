package com.muchen.magicclover.util;

import com.muchen.magicclover.MagicCloverMod;
import com.muchen.magicclover.data.PouchData;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 抽取引擎：负责从各物品池随机抽取并生成掉落物。
 */
public final class LootEngine {
	private LootEngine() {}

	// 原版物品池按等级分层。等级越高，池子越"高级"。
	private static final List<Item>[] VANILLA_POOLS = buildVanillaPools();

	// 模组物品池（缓存，等概率抽取）
	private static final List<Item> modItemCache = new ArrayList<>();
	private static boolean modCacheBuilt = false;

	@SuppressWarnings("unchecked")
	private static List<Item>[] buildVanillaPools() {
		List<Item>[] pools = new List[10];

		// 低档：材料与基础物资。原铁只出现在前三档，之后退场。
		pools[0] = List.of(Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.WHEAT_SEEDS,
				Items.APPLE, Items.FEATHER, Items.LEATHER, Items.STRING, Items.FLINT, Items.BREAD,
				Items.OAK_PLANKS, Items.STICK, Items.TORCH, Items.COAL, Items.RAW_IRON, Items.RAW_COPPER,
				Items.SUGAR_CANE, Items.CLAY_BALL, Items.OAK_SAPLING, Items.SAND, Items.GRAVEL);

		pools[1] = List.of(Items.OAK_PLANKS, Items.STICK, Items.TORCH, Items.COAL, Items.RAW_IRON,
				Items.RAW_COPPER, Items.CHARCOAL, Items.WHEAT, Items.CARROT, Items.POTATO,
				Items.OAK_DOOR, Items.CHEST, Items.CRAFTING_TABLE, Items.FURNACE, Items.GLASS, Items.SAND,
				Items.BARREL, Items.COMPOSTER, Items.REDSTONE, Items.LEVER, Items.CAMPFIRE);

		pools[2] = List.of(Items.COAL, Items.RAW_COPPER, Items.RAW_IRON, Items.CHARCOAL, Items.CRAFTING_TABLE,
				Items.FURNACE, Items.IRON_INGOT, Items.COPPER_INGOT, Items.LANTERN, Items.BUCKET,
				Items.SHEARS, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.BOOK, Items.PAPER,
				Items.WHEAT, Items.BREAD, Items.CAKE, Items.FLINT_AND_STEEL, Items.SOUL_TORCH, Items.HOPPER);

		pools[3] = List.of(Items.IRON_INGOT, Items.COPPER_INGOT, Items.LANTERN,
				Items.BUCKET, Items.SHEARS, Items.RAIL, Items.MINECART, Items.IRON_HELMET,
				Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_SWORD,
				Items.IRON_PICKAXE, Items.CLOCK, Items.COMPASS, Items.BOOKSHELF,
				Items.COPPER_BLOCK, Items.BELL, Items.SPYGLASS, Items.POLISHED_ANDESITE);

		// 青金石：附魔主题 + 探索物资
		pools[4] = List.of(Items.LAPIS_LAZULI, Items.LAPIS_BLOCK, Items.IRON_INGOT,
				Items.COPPER_INGOT, Items.LANTERN, Items.COMPASS, Items.CLOCK, Items.BOOK,
				Items.BOOKSHELF, Items.PAPER, Items.BUCKET, Items.SHEARS, Items.CHEST,
				Items.GOLDEN_APPLE, Items.ENDER_PEARL,
				Items.SEA_LANTERN, Items.PRISMARINE, Items.ENCHANTING_TABLE, Items.AMETHYST_SHARD);

		// 铁 / 金档：信标不再铺满全档，只留后期
		pools[5] = List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.BUCKET, Items.MINECART,
				Items.IRON_PICKAXE, Items.IRON_SWORD, Items.IRON_HELMET, Items.IRON_CHESTPLATE,
				Items.GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.GLISTERING_MELON_SLICE,
				Items.ENCHANTING_TABLE, Items.HOPPER, Items.PISTON, Items.OBSERVER,
				Items.DISPENSER, Items.SPYGLASS, Items.IRON_BLOCK, Items.ANVIL, Items.CAULDRON);

		pools[6] = List.of(Items.GOLD_INGOT, Items.GOLDEN_APPLE, Items.ENCHANTING_TABLE,
				Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_SWORD,
				Items.DIAMOND, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.GHAST_TEAR,
				Items.NETHER_WART, Items.MAGMA_CREAM, Items.ENDER_EYE,
				Items.GOLD_BLOCK, Items.GOLDEN_BOOTS, Items.GOLDEN_LEGGINGS,
				Items.FIREWORK_ROCKET, Items.TNT, Items.REDSTONE_BLOCK);

		// 钻石档：鞘翅/图腾/海洋之心独占，龙蛋已移除
		pools[7] = List.of(Items.DIAMOND, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE,
				Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS,
				Items.DIAMOND_BOOTS, Items.DIAMOND_BLOCK, Items.ENDER_EYE, Items.ELYTRA,
				Items.END_ROD, Items.PURPUR_BLOCK, Items.SHULKER_BOX, Items.TOTEM_OF_UNDYING,
				Items.HEART_OF_THE_SEA, Items.SEA_LANTERN, Items.BEACON,
				Items.ENCHANTED_BOOK, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL,
				Items.DIAMOND_HORSE_ARMOR, Items.OBSIDIAN, Items.ENDER_CHEST);

		// 绿宝石档：下界之星、潮涌核心、回响碎片
		pools[8] = List.of(Items.EMERALD, Items.EMERALD_BLOCK, Items.DIAMOND_BLOCK,
				Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_CHESTPLATE,
				Items.TOTEM_OF_UNDYING, Items.NETHER_STAR, Items.END_CRYSTAL,
				Items.ENCHANTED_BOOK, Items.EXPERIENCE_BOTTLE, Items.GOLDEN_APPLE,
				Items.ENCHANTING_TABLE, Items.BEACON, Items.CONDUIT,
				Items.ECHO_SHARD, Items.AMETHYST_SHARD, Items.SHULKER_SHELL,
				Items.NAUTILUS_SHELL, Items.SLIME_BLOCK, Items.OBSIDIAN, Items.ENDER_CHEST);

		// 下界合金档：终局实用品，龙蛋/盾牌/三叉戟已移除
		pools[9] = List.of(Items.NETHERITE_INGOT, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE,
				Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
				Items.NETHERITE_BOOTS, Items.NETHERITE_BLOCK, Items.NETHER_STAR,
				Items.ELYTRA, Items.TOTEM_OF_UNDYING, Items.ENCHANTED_BOOK,
				Items.EXPERIENCE_BOTTLE, Items.BEACON, Items.CONDUIT, Items.END_CRYSTAL,
				Items.HEART_OF_THE_SEA, Items.NETHERITE_SCRAP,
				Items.ANCIENT_DEBRIS, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
				Items.SHULKER_SHELL, Items.OBSIDIAN, Items.ENDER_CHEST, Items.ENDER_EYE);
		return pools;
	}

	/**
	 * 从原版等级池抽取一个物品。
	 */
	// ============ 加权抽取 ============
	// 四档权重：常见 20 / 普通 12 / 稀有 6 / 传说 1。
	// 目的是让"稀有东西真的稀有"——传说物品单件概率压到约 1%，而不是 1/N。
	private static final Set<Item> COMMON_LOOT = Set.of(
			Items.DIRT,
			Items.COBBLESTONE,
			Items.OAK_LOG,
			Items.WHEAT_SEEDS,
			Items.APPLE,
			Items.FEATHER,
			Items.LEATHER,
			Items.STRING,
			Items.FLINT,
			Items.BREAD,
			Items.OAK_PLANKS,
			Items.STICK,
			Items.TORCH,
			Items.SAND,
			Items.GLASS,
			Items.WHEAT,
			Items.CARROT,
			Items.POTATO,
			Items.PAPER,
			Items.BOOK,
			Items.CAKE,
			Items.CHEST,
			Items.FURNACE,
			Items.CRAFTING_TABLE,
			Items.BOOKSHELF,
			Items.RAIL,
			Items.OAK_DOOR,
			Items.BARREL,
			Items.COMPOSTER,
			Items.CLAY_BALL,
			Items.SUGAR_CANE,
			Items.PISTON,
			Items.OBSERVER,
			Items.HOPPER,
			Items.DISPENSER,
			Items.CLOCK,
			Items.COMPASS,
			Items.SPYGLASS,
			Items.ANVIL,
			Items.COAL,
			Items.CHARCOAL,
			Items.RAW_IRON,
			Items.RAW_COPPER,
			Items.COPPER_INGOT,
			Items.OAK_SAPLING,
			Items.GRAVEL,
			Items.LEVER,
			Items.CAMPFIRE,
			Items.FLINT_AND_STEEL,
			Items.SOUL_TORCH,
			Items.MINECART,
			Items.CAULDRON,
			Items.TNT,
			Items.FIREWORK_ROCKET
		);

	private static final Set<Item> RARE_LOOT = Set.of(
			Items.GOLDEN_APPLE,
			Items.GOLDEN_CARROT,
			Items.GLISTERING_MELON_SLICE,
			Items.ENDER_PEARL,
			Items.ENDER_EYE,
			Items.BLAZE_ROD,
			Items.GHAST_TEAR,
			Items.NETHER_WART,
			Items.MAGMA_CREAM,
			Items.END_CRYSTAL,
			Items.BEACON,
			Items.ENCHANTED_BOOK,
			Items.EXPERIENCE_BOTTLE,
			Items.DIAMOND_BLOCK,
			Items.EMERALD_BLOCK,
			Items.SEA_LANTERN,
			Items.PRISMARINE,
			Items.ECHO_SHARD,
			Items.AMETHYST_SHARD,
			Items.DIAMOND,
			Items.EMERALD,
			Items.NETHERITE_SCRAP,
			Items.ANCIENT_DEBRIS,
			Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
			Items.TRIDENT,
			Items.LAPIS_BLOCK,
			Items.ENDER_CHEST,
			Items.SHULKER_BOX,
			Items.SHULKER_SHELL,
			Items.OBSIDIAN,
			Items.NAUTILUS_SHELL,
			Items.SLIME_BLOCK,
			Items.POLISHED_ANDESITE,
			Items.COPPER_BLOCK,
			Items.BELL,
			Items.REDSTONE_BLOCK
		);

	// 注意：龙蛋已移出奖池——一个世界只有一颗，它是通关奖杯，不该从草里随机开出来。
	private static final Set<Item> LEGENDARY_LOOT = Set.of(
			Items.ELYTRA, Items.NETHER_STAR, Items.HEART_OF_THE_SEA,
			Items.CONDUIT, Items.TOTEM_OF_UNDYING);

	/** 低价值物品：抽到时会给一组，而不是孤零零一个。 */
	private static final Set<Item> COUNT_LOOT = Set.of(
			Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.WHEAT_SEEDS, Items.APPLE,
			Items.FEATHER, Items.LEATHER, Items.STRING, Items.FLINT, Items.BREAD,
			Items.OAK_PLANKS, Items.STICK, Items.TORCH, Items.SAND, Items.GRAVEL,
			Items.WHEAT, Items.CARROT, Items.POTATO, Items.PAPER, Items.BOOK, Items.COAL,
			Items.CHARCOAL, Items.RAW_IRON, Items.RAW_COPPER, Items.COPPER_INGOT,
			Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI,
			Items.SUGAR_CANE, Items.CLAY_BALL, Items.OAK_SAPLING, Items.AMETHYST_SHARD,
			Items.END_ROD, Items.PURPUR_BLOCK, Items.PRISMARINE, Items.NETHER_WART,
			Items.BLAZE_ROD, Items.MAGMA_CREAM, Items.EXPERIENCE_BOTTLE);

	/** 真正不值钱的基础方块：给更大的一组（2~16）。 */
	private static final Set<Item> BULK_LOOT = Set.of(
			Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.OAK_PLANKS, Items.STICK,
			Items.TORCH, Items.SAND, Items.GRAVEL, Items.WHEAT_SEEDS, Items.APPLE,
			Items.FEATHER, Items.STRING, Items.FLINT, Items.BREAD, Items.PAPER,
			Items.COAL, Items.CHARCOAL, Items.RAW_IRON, Items.RAW_COPPER);

	/** 物品权重：越常见越容易抽到。 */
	private static int weightOf(Item item) {
		if (LEGENDARY_LOOT.contains(item)) return 1;
		if (RARE_LOOT.contains(item)) return 6;
		if (COMMON_LOOT.contains(item)) return 20;
		return 12;  // 普通资源 / 工具 / 装备
	}

	/**
	 * 给 JEI 插件读取的只读掉落池。直接返回同一份静态列表，
	 * 这样 JEI 页面永远和真实抽取逻辑同步，不会出现"说明写错了"的情况。
	 */
	public static List<Item> getPool(CloverTier tier) {
		return VANILLA_POOLS[Math.min(tier.getLevel(), VANILLA_POOLS.length - 1)];
	}

	public static ItemStack pickVanilla(CloverTier tier, ServerLevel level) {
		List<Item> pool = VANILLA_POOLS[Math.min(tier.getLevel(), VANILLA_POOLS.length - 1)];

		// 按权重抽一个。池子很小（20 件左右），线性扫描足够快。
		int total = 0;
		for (Item candidate : pool) total += weightOf(candidate);
		int ticket = (int) (Math.random() * total);
		Item item = pool.get(pool.size() - 1);
		for (Item candidate : pool) {
			ticket -= weightOf(candidate);
			if (ticket < 0) {
				item = candidate;
				break;
			}
		}

		ItemStack stack = new ItemStack(item);
		// 低价值的可堆叠物品成组掉落：让"泥土、木棍、铁锭"这种单件不像空气
		if (stack.isStackable() && COUNT_LOOT.contains(item)) {
			int max = BULK_LOOT.contains(item) ? 16 : 6;
			stack.setCount(1 + (int) (Math.random() * max));
		}
		// 附魔书不能是空壳：抽一条随机附魔（等级也随机），和原版附魔书一致
		if (item == Items.ENCHANTED_BOOK && level != null) {
			enchantBookRandomly(level, stack);
		}
		return stack;
	}

	private static void enchantBookRandomly(ServerLevel level, ItemStack stack) {
		try {
			Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			var picked = registry.getRandom(level.getRandom());
			if (picked.isEmpty()) return;
			Holder<Enchantment> holder = picked.get();
			int max = Math.max(1, holder.value().getMaxLevel());
			ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			mutable.set(holder, 1 + level.getRandom().nextInt(max));
			stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
		} catch (Throwable ignored) {
			// 注册表异常时退化为普通附魔书，至少不会崩游戏
		}
	}

	/**
	 * 从所有已注册的模组物品中等概率抽取（排除原版与本模组物品）。
	 * 结果可能为 null（若没有模组物品）。
	 */
	public static ItemStack pickModItem() {
		if (!modCacheBuilt) buildModCache();
		if (modItemCache.isEmpty()) return null;
		Item item = modItemCache.get((int) (Math.random() * modItemCache.size()));
		return new ItemStack(item);
	}

	private static void buildModCache() {
		modItemCache.clear();
		com.muchen.magicclover.config.ModConfig cfg = com.muchen.magicclover.config.ModConfig.get();
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (id == null) continue;
			String ns = id.getNamespace();
			// 排除原版、本模组、以及配置里屏蔽的命名空间/物品
			if (ns.equals("minecraft") || ns.equals(MagicCloverMod.MOD_ID)) continue;
			if (cfg.modItemBlacklistNamespaces.contains(ns)) continue;
			if (cfg.modItemBlacklist.contains(id.toString())) continue;
			if (isBanned(item, id)) continue;
			modItemCache.add(item);
		}
		modCacheBuilt = true;
		MagicCloverMod.LOGGER.info("Mod item pool built: {} items", modItemCache.size());
	}

	/** 清空缓存（配置改动后调用）。 */
	public static void invalidateModCache() {
		modCacheBuilt = false;
		modItemCache.clear();
	}

	private static boolean isBanned(Item item, Identifier id) {
		String path = id.getPath();
		// 排除明显的调试/占位/流体图标物品，避免崩档
		if (path.contains("debug") || path.contains("missing") || path.contains("barrier")
				|| path.contains("command_block") || path.contains("structure_block")
				|| path.contains("jigsaw") || path.contains("light") && path.contains("block")
				|| path.contains("item_proxy") || path.contains("fluid")) {
			return true;
		}
		// 排除空气类
		if (item == Items.AIR) return true;
		return false;
	}

	/**
	 * 在玩家面前生成掉落物。
	 */
	public static void spawnLoot(ServerLevel level, Player player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		double x = player.getX();
		double y = player.getY() + 0.5;
		double z = player.getZ();
		ItemEntity entity = new ItemEntity(level, x, y, z, stack);
		entity.setDefaultPickUpDelay();
		level.addFreshEntity(entity);
	}

	/**
	 * 只往「主背包 36 格」里放，副手和盔甲槽不算。
	 *
	 * <p>原版 {@code Inventory#add} 会把物品塞进副手/盔甲槽，导致主背包明明满了却判定成功，
	 * 物品躲进看不见的格子里 —— 这里必须自己实现。
	 *
	 * @return 放不下的剩余部分（原 stack 未被修改）
	 */
	public static ItemStack addToMainInventory(Player player, ItemStack stack) {
		ItemStack rest = stack.copy();
		if (rest.isEmpty()) return rest;

		net.minecraft.world.entity.player.Inventory inv = player.getInventory();

		// 1) 并到已有的同类堆
		for (int i = 0; i < 36 && !rest.isEmpty(); i++) {
			ItemStack slot = inv.getItem(i);
			if (slot.isEmpty()) continue;
			if (!ItemStack.isSameItemSameComponents(slot, rest)) continue;
			int cap = Math.min(slot.getMaxStackSize(), rest.getMaxStackSize());
			int room = cap - slot.getCount();
			if (room <= 0) continue;
			int move = Math.min(room, rest.getCount());
			slot.grow(move);
			rest.shrink(move);
		}

		// 2) 占用空格
		for (int i = 0; i < 36 && !rest.isEmpty(); i++) {
			if (!inv.getItem(i).isEmpty()) continue;
			int move = Math.min(rest.getMaxStackSize(), rest.getCount());
			ItemStack copy = rest.copy();
			copy.setCount(move);
			inv.setItem(i, copy);
			rest.shrink(move);
		}

		inv.setChanged();
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			sp.containerMenu.broadcastChanges();
			sp.inventoryMenu.broadcastChanges();
		}
		return rest;
	}

	/** 发放结果：进了背包 */
	public static final int GAVE_INVENTORY = 0;
	/** 发放结果：进了口袋 */
	public static final int GAVE_POUCH = 1;
	/** 发放结果：掉在地上 */
	public static final int GAVE_GROUND = 2;

	/**
	 * 优先塞进背包；背包满了就自动存进幸运口袋（P 键打开）。
	 * 口袋也满了才会掉在地上。返回物品最终去了哪里。
	 */
	public static int giveOrStore(ServerLevel level, Player player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			MagicCloverMod.LOGGER.error("[pouch] giveOrStore 收到空物品，已忽略");
			return GAVE_INVENTORY;
		}

		ItemStack rest = addToMainInventory(player, stack);
		int invAdded = stack.getCount() - rest.getCount();

		if (rest.isEmpty()) {
			MagicCloverMod.LOGGER.info("[pouch] {} x{} → 背包", stack.getItem(), invAdded);
			return GAVE_INVENTORY;
		}

		int stored = 0;
		try {
			stored = PouchData.get(level).store(player.getUUID(), rest);
		} catch (Throwable t) {
			MagicCloverMod.LOGGER.error("[pouch] 存入口袋失败", t);
		}

		MagicCloverMod.LOGGER.info("[pouch] {} 背包 {}，口袋 {}，剩余 {}",
				stack.getItem(), invAdded, stored, rest.getCount());

		if (stored > 0) {
			rest.shrink(stored);
			if (rest.isEmpty()) return GAVE_POUCH;
		}

		if (!rest.isEmpty()) {
			// 口袋也存不下（或出错）就掉出来，绝不吞物品
			spawnLoot(level, player, rest);
			return GAVE_GROUND;
		}
		return stored > 0 ? GAVE_POUCH : GAVE_INVENTORY;
	}
}
