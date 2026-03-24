package net.lunix.exptrade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

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
                                                    TradeManager.offer(giver, target, amount, false, false);
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> {
                                                    ServerPlayer giver = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    TradeManager.offer(giver, target, 0, false, true);
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
                                                    TradeManager.offer(giver, target, amount, true, false);
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> {
                                                    ServerPlayer giver = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    TradeManager.offer(giver, target, 0, true, true);
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
                                                    TradeManager.request(requester, target, amount, false, false);
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> {
                                                    ServerPlayer requester = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    TradeManager.request(requester, target, 0, false, true);
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
                                                    TradeManager.request(requester, target, amount, true, false);
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> {
                                                    ServerPlayer requester = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    TradeManager.request(requester, target, 0, true, true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )

                // /exptrade accept
                .then(Commands.literal("accept")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            TradeManager.accept(player, ctx.getSource().getServer());
                            return 1;
                        })
                )

                // /exptrade decline
                .then(Commands.literal("decline")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            TradeManager.decline(player, ctx.getSource().getServer());
                            return 1;
                        })
                )

                // /exptrade cancel
                .then(Commands.literal("cancel")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            TradeManager.cancel(player);
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
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
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
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
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
