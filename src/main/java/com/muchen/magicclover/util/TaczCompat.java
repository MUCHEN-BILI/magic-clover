package com.muchen.magicclover.util;

import com.muchen.magicclover.MagicCloverMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TACZ 兼容层（全反射实现，不强依赖）。
 *
 * <p>TACZ 的枪械 / 弹药 / 配件 / 弹药盒都共用同一个「外壳物品」，具体型号存在数据组件里。
 * 所以单纯从物品注册表随机抽取，只能抽到空壳（黑紫方块）或工作台之类的工具。
 * 这里通过 TACZ 自己的 API 枚举各类型 id，再用对应的 Builder 构建带正确组件的成品。
 */
public final class TaczCompat {
	private TaczCompat() {}

	private static final String API = "com.tacz.guns.api.TimelessAPI";
	private static final String GUN_BUILDER = "com.tacz.guns.api.item.builder.GunItemBuilder";
	private static final String AMMO_BUILDER = "com.tacz.guns.api.item.builder.AmmoItemBuilder";
	private static final String ATTACHMENT_BUILDER = "com.tacz.guns.api.item.builder.AttachmentItemBuilder";
	private static final String I_AMMO_BOX = "com.tacz.guns.api.item.IAmmoBox";

	/** TACZ 里作为普通物品刷出来的方块类物品（工作台、标靶、石像、标靶车） */
	private static final String[] PLAIN_ITEMS = {
			"tacz:gun_smith_table", "tacz:target", "tacz:statue", "tacz:target_minecart"
	};

	/** -1 未检测，0 不可用，1 可用 */
	private static int state = -1;

	private static List<Identifier> gunIds = List.of();
	private static List<Identifier> ammoIds = List.of();
	private static List<Identifier> attachmentIds = List.of();

	public static boolean isAvailable() {
		if (state == -1) detect();
		return state == 1;
	}

	private static void detect() {
		try {
			Class.forName(API);
			Class.forName(GUN_BUILDER);
			state = 1;
		} catch (Throwable t) {
			state = 0;
			MagicCloverMod.LOGGER.info("TACZ 未安装或版本不兼容，模组草将使用普通模组物品池");
		}
	}

	// ===== id 枚举 =====

	private static List<Identifier> collect(String apiMethod, List<Identifier> cache, String label) {
		if (!cache.isEmpty()) return cache;
		List<Identifier> out = new ArrayList<>();
		try {
			Class<?> api = Class.forName(API);
			Object result = api.getMethod(apiMethod).invoke(null);
			if (result instanceof Iterable<?> iterable) {
				for (Object entry : iterable) {
					if (entry instanceof Map.Entry<?, ?> e && e.getKey() instanceof Identifier id) {
						out.add(id);
					}
				}
			}
		} catch (Throwable t) {
			MagicCloverMod.LOGGER.warn("枚举 TACZ {} 失败: {}", label, t.toString());
		}
		out.sort(Comparator.comparing(Identifier::toString));
		if (!out.isEmpty()) {
			MagicCloverMod.LOGGER.info("TACZ {} 池就绪: {} 种", label, out.size());
		}
		return out;
	}

	public static List<Identifier> gunIds() {
		if (!isAvailable()) return List.of();
		gunIds = collect("getAllCommonGunIndex", gunIds, "枪械");
		return gunIds;
	}

	public static List<Identifier> ammoIds() {
		if (!isAvailable()) return List.of();
		ammoIds = collect("getAllCommonAmmoIndex", ammoIds, "弹药");
		return ammoIds;
	}

	public static List<Identifier> attachmentIds() {
		if (!isAvailable()) return List.of();
		attachmentIds = collect("getAllCommonAttachmentIndex", attachmentIds, "配件");
		return attachmentIds;
	}

	private static <T> T randomOf(List<T> list) {
		if (list.isEmpty()) return null;
		return list.get((int) (Math.random() * list.size()));
	}

	// ===== 成品构建 =====

	private static ItemStack item(String id) {
		Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
		return opt.map(ItemStack::new).orElse(null);
	}

	/** 一把真枪。 */
	public static ItemStack createGun(Identifier gunId) {
		if (!isAvailable() || gunId == null) return null;
		try {
			Class<?> c = Class.forName(GUN_BUILDER);
			Object b = c.getMethod("create").invoke(null);
			b = c.getMethod("setId", Identifier.class).invoke(b, gunId);
			b = c.getMethod("setCount", int.class).invoke(b, 1);
			Object stack;
			try {
				stack = c.getMethod("forceBuild").invoke(b);
			} catch (Throwable ignored) {
				stack = c.getMethod("build").invoke(b);
			}
			if (stack instanceof ItemStack s && !s.isEmpty()) return s;
		} catch (Throwable t) {
			MagicCloverMod.LOGGER.debug("构建枪械失败 {}: {}", gunId, t.toString());
		}
		return null;
	}

	/** 一叠子弹。 */
	public static ItemStack createAmmo(Identifier ammoId, int count) {
		if (!isAvailable() || ammoId == null) return null;
		try {
			Class<?> c = Class.forName(AMMO_BUILDER);
			Object b = c.getMethod("create").invoke(null);
			b = c.getMethod("setId", Identifier.class).invoke(b, ammoId);
			b = c.getMethod("setCount", int.class).invoke(b, count);
			Object stack = c.getMethod("build").invoke(b);
			if (stack instanceof ItemStack s && !s.isEmpty()) return s;
		} catch (Throwable t) {
			MagicCloverMod.LOGGER.debug("构建弹药失败 {}: {}", ammoId, t.toString());
		}
		return null;
	}

	/** 一个配件。 */
	public static ItemStack createAttachment(Identifier attachmentId) {
		if (!isAvailable() || attachmentId == null) return null;
		try {
			Class<?> c = Class.forName(ATTACHMENT_BUILDER);
			Object b = c.getMethod("create").invoke(null);
			b = c.getMethod("setId", Identifier.class).invoke(b, attachmentId);
			b = c.getMethod("setCount", int.class).invoke(b, 1);
			Object stack = c.getMethod("build").invoke(b);
			if (stack instanceof ItemStack s && !s.isEmpty()) return s;
		} catch (Throwable t) {
			MagicCloverMod.LOGGER.debug("构建配件失败 {}: {}", attachmentId, t.toString());
		}
		return null;
	}

	/**
	 * 一个普通弹药盒（铁/金/钻石三档）。
	 * 刻意不构建创造弹药盒——那玩意无限弹药，太超模。
	 */
	public static ItemStack createAmmoBox(int level, Identifier ammoId, int count) {
		if (!isAvailable()) return null;
		ItemStack box = item("tacz:ammo_box");
		if (box == null || box.isEmpty()) return null;
		try {
			Class<?> c = Class.forName(I_AMMO_BOX);
			Object impl = box.getItem();
			if (!c.isInstance(impl)) return null;
			c.getMethod("setAmmoLevel", ItemStack.class, int.class).invoke(impl, box, level);
			if (ammoId != null) {
				c.getMethod("setAmmoId", ItemStack.class, Identifier.class).invoke(impl, box, ammoId);
			}
			c.getMethod("setAmmoCount", ItemStack.class, int.class).invoke(impl, box, count);
			return box;
		} catch (Throwable t) {
			MagicCloverMod.LOGGER.debug("构建弹药盒失败: {}", t.toString());
		}
		return null;
	}

	/**
	 * 随机一件 TACZ 物品（按权重混合：弹药 / 枪 / 配件 / 弹药盒 / 工作台类）。
	 * 全部失败时返回 null。
	 */
	public static ItemStack randomLoot() {
		if (!isAvailable()) return null;

		// 弹药给得最多（不然只有枪没法玩），其次枪，再配件，最后方块类和弹药盒
		int roll = (int) (Math.random() * 100);
		ItemStack result = null;

		if (roll < 34) {
			result = createAmmo(randomOf(ammoIds()), 30 + (int) (Math.random() * 31));
		} else if (roll < 62) {
			result = createGun(randomOf(gunIds()));
		} else if (roll < 80) {
			result = createAttachment(randomOf(attachmentIds()));
		} else if (roll < 90) {
			int level = 1 + (int) (Math.random() * 3);
			result = createAmmoBox(level, randomOf(ammoIds()), 120 + (int) (Math.random() * 120));
		} else {
			List<String> plains = List.of(PLAIN_ITEMS);
			result = item(plains.get((int) (Math.random() * plains.size())));
		}

		// 兜底：上面任意一步失败就换个类别再试
		if (result == null || result.isEmpty()) {
			result = createAmmo(randomOf(ammoIds()), 30);
		}
		if (result == null || result.isEmpty()) {
			result = createGun(randomOf(gunIds()));
		}
		if (result == null || result.isEmpty()) {
			result = createAttachment(randomOf(attachmentIds()));
		}
		return (result == null || result.isEmpty()) ? null : result;
	}

	/** 兼容旧调用：随机一把枪。 */
	public static ItemStack randomGun() {
		for (int attempt = 0; attempt < 3; attempt++) {
			ItemStack stack = createGun(randomOf(gunIds()));
			if (stack != null) return stack;
		}
		return null;
	}
}
