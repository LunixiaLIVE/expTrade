package net.lunix.exptrade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TradeCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("exptrade")
                .then(literal("give")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("levels", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayerEntity giver = ctx.getSource().getPlayerOrThrow();
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            int levels = IntegerArgumentType.getInteger(ctx, "levels");
                                            TradeManager.offer(giver, target, levels, false);
                                            return 1;
                                        })
                                )
                                .then(literal("all")
                                        .executes(ctx -> {
                                            ServerPlayerEntity giver = ctx.getSource().getPlayerOrThrow();
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            TradeManager.offer(giver, target, 0, true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(literal("accept")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            TradeManager.accept(player, ctx.getSource().getServer());
                            return 1;
                        })
                )
                .then(literal("decline")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            TradeManager.decline(player, ctx.getSource().getServer());
                            return 1;
                        })
                )
                .then(literal("cancel")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            TradeManager.cancel(player);
                            return 1;
                        })
                )
                .then(literal("threshold")
                        .then(argument("levels", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    int threshold = IntegerArgumentType.getInteger(ctx, "levels");
                                    player.setAttached(PlayerAttachments.THRESHOLD, threshold);
                                    player.sendMessage(Text.literal("§aYour XP trade threshold set to §e" + threshold + " §alevels."), false);
                                    return 1;
                                })
                        )
                )
                .then(literal("config")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(literal("timeout")
                                .then(argument("seconds", IntegerArgumentType.integer(10, 300))
                                        .executes(ctx -> {
                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            ModConfig.get().timeoutSeconds = seconds;
                                            ModConfig.save();
                                            ctx.getSource().sendFeedback(() -> Text.literal("§aTrade timeout set to §e" + seconds + " §aseconds."), true);
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("reload")
                                .executes(ctx -> {
                                    ModConfig.load();
                                    ctx.getSource().sendFeedback(() -> Text.literal("§aexpTrade config reloaded."), true);
                                    return 1;
                                })
                        )
                )
        );
    }
}
