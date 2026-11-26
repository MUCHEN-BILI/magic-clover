package com.muchen.magicclover.jei;

import com.muchen.magicclover.MagicCloverMod;
import com.muchen.magicclover.ModItems;
import com.muchen.magicclover.util.CloverTier;
import com.muchen.magicclover.util.LootEngine;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 集成：给每一档幸运草加一页「可能开出什么」。
 *
 * <p>不复制掉落表——页面直接读 {@link LootEngine#getPool(CloverTier)}，
 * 以后改了真实掉落池，JEI 页面自动跟着变。</p>
 *
 * <p>抽取是严格等概率的，所以每项概率就是 1/N；页面底部写明 N 和份数。</p>
 */
@JeiPlugin
public class CloverJeiPlugin implements IModPlugin {
	public static final RecipeType<CloverLootRecipe> TYPE =
			new RecipeType<>(MagicCloverMod.id("clover_loot"), CloverLootRecipe.class);

	private static final int WIDTH = 178;
	private static final int HEIGHT = 132;

	/** 一页 = 一档草。 */
	public record CloverLootRecipe(CloverTier tier) {}

	@Override
	public Identifier getPluginUid() {
		return MagicCloverMod.id("jei_plugin");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		MagicCloverMod.LOGGER.info("[JEI] 注册幸运草掉落分类");
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(new CloverLootCategory(gui));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		MagicCloverMod.LOGGER.info("[JEI] 注册 {} 条幸运草掉落配方", CloverTier.values().length);
		List<CloverLootRecipe> recipes = new ArrayList<>();
		for (CloverTier tier : CloverTier.values()) {
			recipes.add(new CloverLootRecipe(tier));
		}
		registration.addRecipes(TYPE, recipes);

		// 同时给每档草挂一个 JEI「信息」页。
		// 自定义配方类型的索引在某些版本下不会自动关联物品，信息页是 JEI 官方保证生效的路径。
		for (CloverTier tier : CloverTier.values()) {
			List<Item> pool = LootEngine.getPool(tier);
			StringBuilder sb = new StringBuilder();
			for (Item item : pool) {
				if (sb.length() > 0) sb.append(" · ");
				sb.append(item.getName(new net.minecraft.world.item.ItemStack(item)).getString());
			}
			registration.addItemStackInfo(
					new ItemStack(itemFor(tier)),
					Component.translatable("jei.magicclover.clover_loot.title"),
					Component.translatable("jei.magicclover.clover_loot.chance", pool.size()),
					Component.translatable("jei.magicclover.clover_loot.rolls", tier.getRolls()),
					Component.literal("§7" + sb)
			);
		}
		MagicCloverMod.LOGGER.info("[JEI] 已给 10 档幸运草挂上信息页");
	}

	/** 把等级对应到注册物品，供输入槽显示。 */
	private static Item itemFor(CloverTier tier) {
		return switch (tier) {
			case BASIC -> ModItems.BASIC_CLOVER;
			case WOOD -> ModItems.WOOD_CLOVER;
			case COAL -> ModItems.COAL_CLOVER;
			case COPPER -> ModItems.COPPER_CLOVER;
			case LAPIS -> ModItems.LAPIS_CLOVER;
			case IRON -> ModItems.IRON_CLOVER;
			case GOLD -> ModItems.GOLD_CLOVER;
			case DIAMOND -> ModItems.DIAMOND_CLOVER;
			case EMERALD -> ModItems.EMERALD_CLOVER;
			case NETHERITE -> ModItems.NETHERITE_CLOVER;
		};
	}

	private static String itemName(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	private final class CloverLootCategory implements mezz.jei.api.recipe.category.IRecipeCategory<CloverLootRecipe> {
		private final IGuiHelper gui;
		private final mezz.jei.api.gui.drawable.IDrawable background;
		private final mezz.jei.api.gui.drawable.IDrawable icon;

		CloverLootCategory(IGuiHelper gui) {
			this.gui = gui;
			this.background = gui.createDrawable(
					MagicCloverMod.id("textures/jei/clover_loot.png"), 0, 0, WIDTH, HEIGHT);
			this.icon = gui.createDrawable(
					MagicCloverMod.id("textures/jei/clover_icon.png"), 0, 0, 16, 16);
		}

		@Override
		public mezz.jei.api.recipe.types.IRecipeType<CloverLootRecipe> getRecipeType() {
			return TYPE;
		}

		@Override
		public Component getTitle() {
			return Component.translatable("jei.magicclover.clover_loot.title");
		}

		@Override
		public int getWidth() { return WIDTH; }

		@Override
		public int getHeight() { return HEIGHT; }

		@Override
		public mezz.jei.api.gui.drawable.IDrawable getIcon() { return icon; }

		@Override
		public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder,
							  CloverLootRecipe recipe,
							  mezz.jei.api.recipe.IFocusGroup focus) {
			CloverTier tier = recipe.tier();
			List<Item> pool = LootEngine.getPool(tier);

			// 左边：这棵草本身
			builder.addSlot(RecipeIngredientRole.INPUT, 8, 8)
					.add(new ItemStack(itemFor(tier)));

			// 右边：整池物品，每格一个
			int cols = 7;
			for (int i = 0; i < pool.size(); i++) {
				int x = 72 + (i % cols) * 15;
				int y = 8 + (i / cols) * 15;
				builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
						.add(new ItemStack(pool.get(i)));
			}
		}

		@Override
		public void draw(CloverLootRecipe recipe,
						 mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
						 net.minecraft.client.gui.GuiGraphics guiGraphics,
						 double mouseX, double mouseY) {
			CloverTier tier = recipe.tier();
			List<Item> pool = LootEngine.getPool(tier);
			int stars = tier.getStars();
			String starText = "§6" + "★".repeat(stars) + "§8" + "★".repeat(5 - stars);

			// 标题：星级 + 等级名
			guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
					Component.literal(starText + " §f" + tier.getName()), 8, 64, 0xFFFFFF, false);

			// 规则说明：等概率 + 份数
			guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
					Component.translatable("jei.magicclover.clover_loot.chance", pool.size()),
					8, 78, 0xAAAAAA, false);
			guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
					Component.translatable("jei.magicclover.clover_loot.rolls", tier.getRolls()),
					8, 90, 0xAAAAAA, false);
			guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
					Component.translatable("jei.magicclover.clover_loot.hint"),
					8, 102, 0x777777, false);
		}

		@Override
		public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip,
							   CloverLootRecipe recipe,
							   mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
							   double mouseX, double mouseY) {
			// 悬停到右下角说明区时补一行，告诉玩家"这是概率不是保证"
			if (mouseX >= 8 && mouseX <= 170 && mouseY >= 76 && mouseY <= 112) {
				tooltip.add(Component.translatable("jei.magicclover.clover_loot.tooltip"));
			}
		}
	}
}
