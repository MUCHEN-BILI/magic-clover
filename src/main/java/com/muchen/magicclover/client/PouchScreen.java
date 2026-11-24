package com.muchen.magicclover.client;

import com.muchen.magicclover.data.PouchData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 幸运口袋界面：原版 6 行箱子底图 + 右上角翻页箭头 + 顶部搜索框。
 *
 * <p>只做客户端显示，菜单类型仍是原版箱子，所以不需要注册自定义界面。
 * 翻页通过原版菜单按钮通道（handleInventoryButtonClick）通知服务端。
 * 搜索只做客户端高亮/淡化，不改服务端数据，也不影响点击取物。
 */
public class PouchScreen extends AbstractContainerScreen<ChestMenu> {
	private static final Identifier TEXTURE =
			Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

	/** 客户端显示用的页码（服务端权威，这里只负责显示与乐观更新） */
	private static int clientPage = 0;

	private Button prevButton;
	private Button nextButton;
	private EditBox searchBox;
	private String filter = "";

	public PouchScreen(ChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 222;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	public static void resetPage() {
		clientPage = 0;
	}

	@Override
	protected void init() {
		super.init();
		int y = this.topPos + 5;
		this.prevButton = this.addRenderableWidget(Button.builder(
						Component.literal("\u25c0"),
						b -> changePage(0))
				.bounds(this.leftPos + this.imageWidth - 46, y, 20, 12)
				.build());
		this.nextButton = this.addRenderableWidget(Button.builder(
						Component.literal("\u25b6"),
						b -> changePage(1))
				.bounds(this.leftPos + this.imageWidth - 24, y, 20, 12)
				.build());

		// 搜索框占用顶部标题位置；输入后高亮匹配物品、淡化其它
		this.searchBox = new EditBox(this.font, this.leftPos + 6, this.topPos + 2, 96, 14,
				Component.literal("搜索口袋..."));
		this.searchBox.setMaxLength(64);
		this.searchBox.setResponder(s -> this.filter = s.trim().toLowerCase(java.util.Locale.ROOT));
		this.addRenderableWidget(this.searchBox);

		refreshButtons();
	}

	private void changePage(int buttonId) {
		int delta = buttonId == 0 ? -1 : 1;
		int next = Math.max(0, Math.min(PouchData.MAX_PAGES - 1, clientPage + delta));
		if (next == clientPage) return;

		clientPage = next;
		refreshButtons();
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
		}
	}

	/** 滚轮翻页：只在口袋界面区域内响应，搜索框和界面外不抢。 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.searchBox != null && this.searchBox.isMouseOver(mouseX, mouseY)) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		if (mouseX < this.leftPos || mouseX > this.leftPos + this.imageWidth
				|| mouseY < this.topPos || mouseY > this.topPos + this.imageHeight
				|| verticalAmount == 0) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		changePage(verticalAmount < 0 ? 1 : 0);
		return true;
	}

	private void refreshButtons() {
		if (this.prevButton != null) this.prevButton.active = clientPage > 0;
		if (this.nextButton != null) this.nextButton.active = clientPage < PouchData.MAX_PAGES - 1;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
				this.leftPos, this.topPos, 0.0f, 0.0f,
				this.imageWidth, this.imageHeight, 256, 256);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		// 顶部让给搜索框，不画原版容器标题
		String pageText = (clientPage + 1) + "/" + PouchData.MAX_PAGES;
		int w = this.font.width(pageText);
		graphics.drawString(this.font, pageText,
				this.imageWidth - 52 - w, 7, 0xFF404040, false);
	}

	/** 搜索时只高亮口袋格（index < 54），玩家背包不受影响。 */
	@Override
	protected void renderSlot(GuiGraphics graphics, Slot slot, int mouseX, int mouseY) {
		super.renderSlot(graphics, slot, mouseX, mouseY);
		if (this.filter.isEmpty() || slot.index >= PouchData.PAGE_SIZE) return;

		ItemStack stack = slot.getItem();
		boolean match = !stack.isEmpty()
				&& stack.getDisplayName().getString().toLowerCase(java.util.Locale.ROOT).contains(this.filter);
		// 原版在 renderSlots 前已 translate(leftPos,topPos)，slot.x/y 就是最终坐标，
		// 不能再加 leftPos/topPos，否则绿框会整体偏移一个界面左上角的距离
		int x = slot.x;
		int y = slot.y;
		if (match) {
			graphics.renderOutline(x, y, 16, 16, 0xFF55FF55);
		} else {
			graphics.fill(x, y, x + 16, y + 16, 0x90000000);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		refreshButtons();
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
