package net.lunix.exptrade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class TradeCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("exptrade")

                // /exptrade give <player> [levels|exp] [<number>|all]
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("levels")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayer giver = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    TradeManager.offer(giver, target, amount, false, false, ctx.getSource().getServer());
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> {
                                                    ServerPlayer giver = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    TradeManager.offer(giver, target, 0, false, true, ctx.getSource().getServer());
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("exp")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayer giver = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    TradeManager.offer(giver, target, amount, true, false, ctx.getSource().getServer());
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> {
                                                    ServerPlayer giver = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    TradeManager.offer(giver, target, 0, true, true, ctx.getSource().getServer());
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )

                // /exptrade request <player> [levels|exp] [<number>|all]
                .then(Commands.literal("request")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("levels")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayer requester = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    TradeManager.request(requester, target, amount, false, false, ctx.getSource().getServer());
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> {
                                                    ServerPlayer requester = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    TradeManager.request(requester, target, 0, false, true, ctx.getSource().getServer());
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("exp")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayer requester = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    TradeManager.request(requester, target, amount, true, false, ctx.getSource().getServer());
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> {
                                                    ServerPlayer requester = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    TradeManager.request(requester, target, 0, true, true, ctx.getSource().getServer());
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )

                // /exptrade accept [<player>]
                .then(Commands.literal("accept")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            MinecraftServer server = ctx.getSource().getServer();
                            TradeManager.accept(player, null, server);
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerPlayer from = EntityArgument.getPlayer(ctx, "player");
                                    MinecraftServer server = ctx.getSource().getServer();
                                    TradeManager.accept(player, from.getUUID(), server);
                                    return 1;
                                })
                        )
                )

                // /exptrade decline [<player>]
                .then(Commands.literal("decline")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            MinecraftServer server = ctx.getSource().getServer();
                            TradeManager.decline(player, null, server);
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerPlayer from = EntityArgument.getPlayer(ctx, "player");
                                    MinecraftServer server = ctx.getSource().getServer();
                                    TradeManager.decline(player, from.getUUID(), server);
                                    return 1;
                                })
                        )
                )

                // /exptrade cancel
                .then(Commands.literal("cancel")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            TradeManager.cancel(player, ctx.getSource().getServer());
                            return 1;
                        })
                )

                // /exptrade pending
                .then(Commands.literal("pending")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            TradeManager.openQueueGui(player, ctx.getSource().getServer());
                            return 1;
                        })
                )

                // /exptrade threshold <levels>
                .then(Commands.literal("threshold")
                        .then(Commands.argument("levels", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int threshold = IntegerArgumentType.getInteger(ctx, "levels");
                                    PlayerDataStore.setThreshold(player.getUUID(), threshold);
                                    player.sendSystemMessage(Component.literal("§aYour XP trade threshold set to §e"
                                            + threshold + " §alevels."));
                                    return 1;
                                })
                        )
                )

                // /exptrade admin ... (admin only)
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("transfer")
                                .then(Commands.argument("from", EntityArgument.player())
                                        .then(Commands.argument("to", EntityArgument.player())
                                                .then(Commands.literal("levels")
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                .executes(ctx -> {
                                                                    ServerPlayer from = EntityArgument.getPlayer(ctx, "from");
                                                                    ServerPlayer to = EntityArgument.getPlayer(ctx, "to");
                                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                                    TradeManager.adminTransfer(from, to, amount, false, false, ctx.getSource());
                                                                    return 1;
                                                                })
                                                        )
                                                        .then(Commands.literal("all")
                                                                .executes(ctx -> {
                                                                    ServerPlayer from = EntityArgument.getPlayer(ctx, "from");
                                                                    ServerPlayer to = EntityArgument.getPlayer(ctx, "to");
                                                                    TradeManager.adminTransfer(from, to, 0, false, true, ctx.getSource());
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                                .then(Commands.literal("exp")
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                .executes(ctx -> {
                                                                    ServerPlayer from = EntityArgument.getPlayer(ctx, "from");
                                                                    ServerPlayer to = EntityArgument.getPlayer(ctx, "to");
                                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                                    TradeManager.adminTransfer(from, to, amount, true, false, ctx.getSource());
                                                                    return 1;
                                                                })
                                                        )
                                                        .then(Commands.literal("all")
                                                                .executes(ctx -> {
                                                                    ServerPlayer from = EntityArgument.getPlayer(ctx, "from");
                                                                    ServerPlayer to = EntityArgument.getPlayer(ctx, "to");
                                                                    TradeManager.adminTransfer(from, to, 0, true, true, ctx.getSource());
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("confirm")
                                .executes(ctx -> {
                                    TradeManager.adminConfirm(ctx.getSource(), ctx.getSource().getServer());
                                    return 1;
                                })
                        )
                        .then(Commands.literal("cancel")
                                .executes(ctx -> {
                                    TradeManager.adminCancel(ctx.getSource());
                                    return 1;
                                })
                        )
                )

                // /exptrade config ... (admin only)
                .then(Commands.literal("config")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("timeout")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 300))
                                        .executes(ctx -> {
                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            ModConfig.get().timeoutSeconds = seconds;
                                            ModConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "§aTrade timeout set to §e" + seconds + " §aseconds."), true);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("maxqueue")
                                .then(Commands.argument("size", IntegerArgumentType.integer(1, 50))
                                        .executes(ctx -> {
                                            int size = IntegerArgumentType.getInteger(ctx, "size");
                                            ModConfig.get().maxQueueSize = size;
                                            ModConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "§aMax queue size set to §e" + size + "§a."), true);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    ModConfig.load();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "§aexpTrade config reloaded."), true);
                                    return 1;
                                })
                        )
                )
        );
    }
}
