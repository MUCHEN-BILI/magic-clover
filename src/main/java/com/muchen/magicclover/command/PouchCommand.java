package com.muchen.magicclover.command;

import com.muchen.magicclover.data.PouchData;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 调试命令，用来验证口袋链路（不需要玩家手动折腾背包）。
 *
 * /cloverpouch info       查看口袋已用格数与前几格内容
 * /cloverpouch fillinv    把背包空格填满（方便测试「背包满」的情况）
 * /cloverpouch fillpouch  直接往口袋里塞 3 组泥土（验证 数据→界面 链路）
 */
public final class PouchCommand {
	private PouchCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("cloverpouch")
						.requires(source -> true)
						.then(Commands.literal("info").executes(PouchCommand::info))
						.then(Commands.literal("fillinv").executes(PouchCommand::fillInv))
						.then(Commands.literal("fillpouch").executes(PouchCommand::fillPouch))
						.then(Commands.literal("overflow").executes(PouchCommand::overflow))
						.then(Commands.literal("event")
								.then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
										.executes(PouchCommand::event)))));
	}

	/**
	 * /cloverpouch event <事件id 或序号>  直接触发一次事件，用于测试。
	 * 例：/cloverpouch event fate_choice  或  /cloverpouch event 17
	 */
	private static int event(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		String arg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");

		java.util.List<String> keys = com.muchen.magicclover.event.EventSystem.getEventKeys();
		int index = keys.indexOf(arg);
		if (index < 0) {
			try {
				index = Integer.parseInt(arg);
			} catch (NumberFormatException e) {
				index = -1;
			}
		}
		if (index < 0 || index >= keys.size()) {
			final String hint = String.join(", ", keys);
			ctx.getSource().sendFailure(Component.literal("未知事件。可用：0~" + (keys.size() - 1) + " 或 " + hint));
			return 0;
		}

		com.muchen.magicclover.event.EventSystem.trigger(level, player, index);
		final int shown = index;
		ctx.getSource().sendSuccess(() -> Component.literal("已触发事件 #" + shown + " (" + keys.get(shown) + ")"), false);
		return 1;
	}

	private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		PouchData data = PouchData.get(level);
		int used = data.size(player.getUUID());

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(used, 5); i++) {
			ItemStack s = data.get(player.getUUID(), i);
			sb.append(i).append(':').append(s.isEmpty() ? "空" : s.getItem() + "x" + s.getCount()).append("  ");
		}

		ctx.getSource().sendSuccess(() -> Component.literal(
				"§6[口袋] §f已用 §e" + used + "§7/" + PouchData.CAPACITY + " §7格　前5格: §f" + sb), false);
		return 1;
	}

	private static int fillInv(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int filled = 0;
		for (int i = 0; i < 36; i++) {
			if (player.getInventory().getItem(i).isEmpty()) {
				player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
				filled++;
			}
		}
		int n = filled;
		ctx.getSource().sendSuccess(() -> Component.literal("§6[口袋] §f已填满 §e" + n + " §f个背包空格"), false);
		return 1;
	}

	private static int fillPouch(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		PouchData data = PouchData.get(level);
		int total = 0;
		for (int i = 0; i < 3; i++) {
			total += data.store(player.getUUID(), new ItemStack(Items.COBBLESTONE, 64));
		}
		int stored = total;
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§6[口袋] §f直接存入 §e" + stored + " §f个圆石，现在按 §eP §f看看第 1 页"), false);
		return 1;
	}

	/** 一键测「背包满 → 自动入袋」：先把背包填满，再真实调用一次发放逻辑。 */
	private static int overflow(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();

		int filled = 0;
		for (int i = 0; i < 36; i++) {
			if (player.getInventory().getItem(i).isEmpty()) {
				player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
				filled++;
			}
		}

		int result = com.muchen.magicclover.util.LootEngine.giveOrStore(
				level, player, new ItemStack(Items.DIAMOND, 5));

		String where = switch (result) {
			case com.muchen.magicclover.util.LootEngine.GAVE_INVENTORY -> "§a背包";
			case com.muchen.magicclover.util.LootEngine.GAVE_POUCH -> "§e幸运口袋（按 P 查看）";
			default -> "§c地上";
		};
		int used = PouchData.get(level).size(player.getUUID());

		int f = filled;
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§6[口袋] §f填满 " + f + " 个空格后发放 5 颗钻石 → 去向: " + where
						+ "；口袋当前已用 §e" + used + " §f格"), false);
		return 1;
	}
}
