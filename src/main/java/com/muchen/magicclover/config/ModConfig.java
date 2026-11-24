package com.muchen.magicclover.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.muchen.magicclover.MagicCloverMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置，保存在 config/magicclover.json。
 */
public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance;

	/** 打草掉落普通幸运草的概率 */
	public float grassDropChance = 0.08f;
	/** 结构箱子里刷出幸运草的概率（0~1）。结构本身不常见，默认给到 35% 让探索能明显感知到。 */
	public float structureCloverChance = 0.35f;
	/** 平时开草触发事件的概率 */
	public double eventChance = 0.02;
	/** 高级事件药水 buff 期间的事件概率 */
	public double eventChanceBuffed = 0.5;
	/** 高级事件药水 buff 持续时间（秒） */
	public int buffDuration = 120;
	/** 低级幸运药剂 buff 期间的事件概率（前期就能拿到） */
	public double lesserEventChance = 0.2;
	/** 低级幸运药剂 buff 持续时间（秒） */
	public int lesserBuffDuration = 60;
	/** 是否保留原版苦力怕惩罚（已弱化，默认关闭） */
	public boolean creeperPunishment = false;
	/** 模组物品池是否启用 */
	public boolean enableModCloverPool = true;
	/**
	 * 模组草开出 TACZ 成品的概率（需要装 TACZ）。
	 * 成品按权重混合：弹药 34% / 枪械 28% / 配件 18% / 弹药盒 10% / 工作台类 10%。
	 * 其余情况走普通模组物品池。
	 */
	public double modCloverTaczChance = 0.9;
	/**
	 * 这些命名空间的物品不进入模组草随机池。
	 * 默认排除 tacz——它的物品大多是工作台/标靶/空枪这类工具，单独适配成枪械更合理。
	 */
	public java.util.List<String> modItemBlacklistNamespaces = new java.util.ArrayList<>(java.util.List.of("tacz"));
	/** 自定义排除的物品 id（形如 "modid:item_name"），用于屏蔽显示异常的物品 */
	public java.util.List<String> modItemBlacklist = new java.util.ArrayList<>();
	/** 特殊事件触发时是否在屏幕中央显示大字 */
	public boolean eventTitle = true;
	/**
	 * 参与型事件（寻宝竞速 / 命运抉择）的目标物是否加指引：
	 * 原版「发光」白色轮廓（穿墙可见）+ 头顶竖直光柱。关掉后奖励就是普通掉落物。
	 */
	public boolean eventItemMarker = true;

	public static ModConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("magicclover.json");
		if (Files.exists(path)) {
			try {
				instance = GSON.fromJson(Files.readString(path), ModConfig.class);
				if (instance == null) instance = new ModConfig();
				// 回写一次，把新增配置项补进旧文件，方便玩家手动调
				save();
			} catch (IOException e) {
				MagicCloverMod.LOGGER.error("Failed to load config", e);
				instance = new ModConfig();
			}
		} else {
			instance = new ModConfig();
			save();
		}
	}

	public static void save() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("magicclover.json");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(get()));
		} catch (IOException e) {
			MagicCloverMod.LOGGER.error("Failed to save config", e);
		}
	}
}
