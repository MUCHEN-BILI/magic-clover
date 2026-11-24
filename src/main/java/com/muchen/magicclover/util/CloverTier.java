package com.muchen.magicclover.util;

/**
 * 幸运草等级。等级越高，掉落的原版物品池越"高级"。
 *
 * <p>rolls（开出份数）和 stars（星级）是显式数值，不随 level 推导。
 * 这样在等级序列中间插入新等级（如青金石）时，已有等级的实际收益不会被动改变。
 */
public enum CloverTier {
	//              level  name        rolls  stars
	BASIC     (0, "basic",     1,     1),
	WOOD      (1, "wood",      1,     1),
	COAL      (2, "coal",      1,     2),
	COPPER    (3, "copper",    2,     2),
	LAPIS     (4, "lapis",     2,     2),
	IRON      (5, "iron",      2,     3),
	GOLD      (6, "gold",      2,     3),
	DIAMOND   (7, "diamond",   3,     4),
	EMERALD   (8, "emerald",   3,     4),
	NETHERITE (9, "netherite", 3,     5);

	private final int level;
	private final String name;
	private final int rolls;
	private final int stars;

	CloverTier(int level, String name, int rolls, int stars) {
		this.level = level;
		this.name = name;
		this.rolls = rolls;
		this.stars = stars;
	}

	public int getLevel() { return level; }
	public String getName() { return name; }
	/** 开出份数 */
	public int getRolls() { return rolls; }
	/** 提示里显示的星级 */
	public int getStars() { return stars; }

	public static CloverTier fromName(String name) {
		for (CloverTier tier : values()) {
			if (tier.name.equalsIgnoreCase(name)) return tier;
		}
		return BASIC;
	}
}
