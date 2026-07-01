package net.lunix.exptrade;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.*;
import java.util.stream.Collectors;

public class TradeManager {

    /**
     * @param initiatorId  UUID of the player who created this trade
     * @param giverId      UUID of the player who will lose XP
     * @param receiverId   UUID of the player who will gain XP
     * @param responderId  UUID of the player who must accept or decline
     * @param amount       levels or raw XP to transfer (ignored when all=true)
     * @param isExp        if true, amount is raw XP; if false, amount is levels
     * @param all          if true, transfer all XP from giver
     * @param expiresAt    System.currentTimeMillis() expiry timestamp
     */
    private record PendingTrade(
            UUID initiatorId,
            String initiatorName,
            UUID giverId,
            UUID receiverId,
            UUID responderId,
            int amount,
            boolean isExp,
            boolean all,
            long expiresAt
    ) {}

    private record PendingAdminTransfer(UUID fromId, UUID toId, int amount, boolean isExp, boolean all) {}

    // Per-player incoming trade queue, keyed by responderId
    private static final Map<UUID, List<PendingTrade>> pendingTrades = new HashMap<>();

    // Keyed by admin UUID — one pending confirmation per admin at a time
    private static final Map<UUID, PendingAdminTransfer> pendingAdminTransfers = new HashMap<>();

    // -------------------------------------------------------------------------
    // XP math
    // -------------------------------------------------------------------------

    private static int xpPerLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    private static int totalXpForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    private static int levelForTotalXp(int totalXp) {
        int level = 0;
        while (totalXpForLevel(level + 1) <= totalXp) level++;
        return level;
    }

    public static int rawXpCost(int currentLevel, int levels) {
        return totalXpForLevel(currentLevel) - totalXpForLevel(currentLevel - levels);
    }

    /**
     * Actual current XP derived from experienceLevel + experienceProgress.
     * player.totalExperience is cumulative and does not decrease when XP is spent
     * on enchanting/anvils (those use giveExperienceLevels which bypasses it).
     */
    private static int actualCurrentXp(ServerPlayer player) {
        int level = player.experienceLevel;
        int base = totalXpForLevel(level);
        int progress = Math.round(player.experienceProgress * xpPerLevel(Math.max(level, 0)));
        return base + progress;
    }

    // -------------------------------------------------------------------------
    // Queue helpers
    // -------------------------------------------------------------------------

    /** Returns the responder UUID whose queue contains an outgoing trade from this initiator, or null. */
    private static UUID findResponderForInitiator(UUID initiatorId) {
        for (Map.Entry<UUID, List<PendingTrade>> entry : pendingTrades.entrySet()) {
            for (PendingTrade trade : entry.getValue()) {
                if (trade.initiatorId().equals(initiatorId)) return entry.getKey();
            }
        }
        return null;
    }

    /** Returns the first trade in the responder's queue from a specific initiator, or null. */
    private static PendingTrade findTradeFrom(UUID responderId, UUID initiatorId) {
        List<PendingTrade> queue = pendingTrades.get(responderId);
        if (queue == null) return null;
        return queue.stream().filter(t -> t.initiatorId().equals(initiatorId)).findFirst().orElse(null);
    }

    /** Removes a trade from a responder's queue. Cleans up the map if queue becomes empty. */
    private static boolean removeTrade(UUID responderId, PendingTrade trade) {
        List<PendingTrade> queue = pendingTrades.get(responderId);
        if (queue == null) return false;
        boolean removed = queue.remove(trade);
        if (queue.isEmpty()) pendingTrades.remove(responderId);
        return removed;
    }

    /**
     * Cancels any existing outgoing trade from this initiator.
     * Notifies the responder their queued trade was cancelled.
     */
    private static void cancelOutgoing(ServerPlayer initiator, MinecraftServer server) {
        UUID oldResponderId = findResponderForInitiator(initiator.getUUID());
        if (oldResponderId == null) return;

        List<PendingTrade> queue = pendingTrades.get(oldResponderId);
        if (queue == null) return;
        queue.removeIf(t -> t.initiatorId().equals(initiator.getUUID()));
        if (queue.isEmpty()) pendingTrades.remove(oldResponderId);

        if (server != null) {
            ServerPlayer oldResponder = server.getPlayerList().getPlayer(oldResponderId);
            if (oldResponder != null) {
                oldResponder.sendSystemMessage(Component.literal(
                        "§e" + initiator.getName().getString() + " §7cancelled their trade offer to you."));
            }
        }
    }

    /** Notifies a player if they still have queued trades after a transaction. */
    private static void notifyQueueRemaining(ServerPlayer responder) {
        List<PendingTrade> queue = pendingTrades.get(responder.getUUID());
        if (queue != null && !queue.isEmpty()) {
            responder.sendSystemMessage(Component.literal("§7You have §e" + queue.size() + "§7 more pending trade(s). ")
                    .append(Component.literal("[View Queue]")
                            .withStyle(s -> s
                                    .withColor(ChatFormatting.YELLOW)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent.RunCommand("/exptrade pending"))
                                    .withHoverEvent(new HoverEvent.ShowText(
                                            Component.literal("Click to open your trade queue")))
                            )
                    )
            );
        }
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    private static boolean validateGiver(ServerPlayer giver, int amount, boolean isExp, boolean all) {
        return validateGiver(giver, amount, isExp, all, true);
    }

    private static boolean validateGiver(ServerPlayer giver, int amount, boolean isExp, boolean all,
                                         boolean checkThreshold) {
        int threshold = checkThreshold ? PlayerDataStore.getThreshold(giver.getUUID()) : 0;

        if (all) {
            if (actualCurrentXp(giver) <= 0) {
                giver.sendSystemMessage(Component.literal("§cYou have no XP to give."));
                return false;
            }
            return true;
        }

        if (isExp) {
            int currentXp = actualCurrentXp(giver);
            if (currentXp < amount) {
                giver.sendSystemMessage(Component.literal("§cYou only have §e" + currentXp
                        + " §craw XP (need §e" + amount + "§c)."));
                return false;
            }
            if (levelForTotalXp(currentXp - amount) < threshold) {
                giver.sendSystemMessage(Component.literal("§cThat would put you below your threshold of §e"
                        + threshold + " §clevels."));
                return false;
            }
        } else {
            if (giver.experienceLevel < amount) {
                giver.sendSystemMessage(Component.literal("§cYou only have §e" + giver.experienceLevel
                        + " §clevels (need §e" + amount + "§c)."));
                return false;
            }
            if (giver.experienceLevel - amount < threshold) {
                giver.sendSystemMessage(Component.literal("§cThat would put you below your threshold of §e"
                        + threshold + " §clevels."));
                return false;
            }
        }
        return true;
    }

    private static boolean revalidateGiver(ServerPlayer giver, ServerPlayer receiver,
                                           int amount, boolean isExp, boolean all) {
        int threshold = PlayerDataStore.getThreshold(giver.getUUID());

        if (all) {
            if (actualCurrentXp(giver) <= 0) {
                notifyBoth(giver, receiver,
                        "§cTrade failed: you have no XP.",
                        "§cTrade failed: the giver no longer has any XP.");
                return false;
            }
            return true;
        }

        if (isExp) {
            int currentXp = actualCurrentXp(giver);
            if (currentXp < amount || levelForTotalXp(currentXp - amount) < threshold) {
                notifyBoth(giver, receiver,
                        "§cTrade failed: you no longer have enough XP.",
                        "§cTrade failed: the giver no longer has enough XP.");
                return false;
            }
        } else {
            if (giver.experienceLevel < amount || giver.experienceLevel - amount < threshold) {
                notifyBoth(giver, receiver,
                        "§cTrade failed: you no longer have enough levels.",
                        "§cTrade failed: the giver no longer has enough levels.");
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Trade execution
    // -------------------------------------------------------------------------

    private static void executeTrade(ServerPlayer giver, ServerPlayer receiver,
                                     int amount, boolean isExp, boolean all) {
        if (all) {
            int giverLevels = giver.experienceLevel;
            int totalXp = actualCurrentXp(giver);
            giver.giveExperienceLevels(-giver.experienceLevel);
            giver.experienceProgress = 0f;
            giver.totalExperience = 0;
            receiver.giveExperiencePoints(totalXp);
            notifyBoth(giver, receiver,
                    "§aYou gave all your XP (§e" + giverLevels + " §alevel(s), §e" + totalXp + " §araw XP) to §e" + receiver.getName().getString() + "§a.",
                    "§aYou received all XP (§e" + giverLevels + " §alevel(s), §e" + totalXp + " §araw XP) from §e" + giver.getName().getString() + "§a.");

        } else if (isExp) {
            int newTotal = actualCurrentXp(giver) - amount;
            int newLevel = levelForTotalXp(newTotal);
            int levelsLost = giver.experienceLevel - newLevel;
            giver.giveExperienceLevels(newLevel - giver.experienceLevel);
            int progressXp = newTotal - totalXpForLevel(newLevel);
            giver.experienceProgress = newLevel >= 0 ? progressXp / (float) xpPerLevel(newLevel) : 0f;
            giver.totalExperience = newTotal;
            receiver.giveExperiencePoints(amount);
            notifyBoth(giver, receiver,
                    "§aYou gave §e" + amount + " §araw XP (§e" + levelsLost + " §alevel(s)) to §e" + receiver.getName().getString() + "§a.",
                    "§aYou received §e" + amount + " §araw XP (§e" + levelsLost + " §agiver level(s)) from §e" + giver.getName().getString() + "§a.");

        } else {
            int rawXp = rawXpCost(giver.experienceLevel, amount);
            giver.giveExperienceLevels(-amount);
            receiver.giveExperiencePoints(rawXp);
            notifyBoth(giver, receiver,
                    "§aYou gave §e" + amount + " §alevel(s) (§e" + rawXp + " §araw XP) to §e"
                            + receiver.getName().getString() + "§a.",
                    "§aYou received §e" + rawXp + " §araw XP (§e" + amount + " §agiver level(s)) from §e"
                            + giver.getName().getString() + "§a.");
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static void offer(ServerPlayer giver, ServerPlayer receiver,
                             int amount, boolean isExp, boolean all, MinecraftServer server) {
        if (giver.getUUID().equals(receiver.getUUID())) {
            giver.sendSystemMessage(Component.literal("§cYou cannot trade with yourself."));
            return;
        }
        if (!validateGiver(giver, amount, isExp, all)) return;

        List<PendingTrade> queue = pendingTrades.get(receiver.getUUID());
        if (queue != null && queue.size() >= ModConfig.get().maxQueueSize) {
            giver.sendSystemMessage(Component.literal(
                    "§c" + receiver.getName().getString() + "'s trade queue is full. Try again later."));
            return;
        }

        long expiresAt = System.currentTimeMillis() + (ModConfig.get().timeoutSeconds * 1000L);
        pendingTrades.computeIfAbsent(receiver.getUUID(), k -> new ArrayList<>())
                .add(new PendingTrade(giver.getUUID(), giver.getName().getString(), giver.getUUID(),
                        receiver.getUUID(), receiver.getUUID(), amount, isExp, all, expiresAt));

        String desc = tradeDesc(giver, amount, isExp, all);
        MutableComponent balanceInfo = buildBalanceInfo(giver, receiver, amount, isExp, all);
        giver.sendSystemMessage(Component.literal("§aOffer sent to §e" + receiver.getName().getString()
                + "§a: §e" + desc + "§a. They have §e" + ModConfig.get().timeoutSeconds + " §aseconds to respond.\n")
                .append(balanceInfo)
                .append(cancelButton()));
        receiver.sendSystemMessage(buildResponderMessage("§e" + giver.getName().getString()
                + " §awants to give you §e" + desc + "§a.\n", balanceInfo));
    }

    public static void request(ServerPlayer requester, ServerPlayer target,
                               int amount, boolean isExp, boolean all, MinecraftServer server) {
        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(Component.literal("§cYou cannot request from yourself."));
            return;
        }

        int storedAmount = amount;
        boolean storedIsExp = isExp;
        if (!isExp && !all) {
            storedAmount = rawXpCost(requester.experienceLevel + amount, amount);
            storedIsExp = true;
        }

        if (!all) {
            int targetCurrentXp = actualCurrentXp(target);
            if (storedIsExp && targetCurrentXp < storedAmount) {
                requester.sendSystemMessage(Component.literal("§c" + target.getName().getString()
                        + " doesn't have enough XP for that (needs §e" + storedAmount + " §craw XP)."));
                return;
            }
        }

        List<PendingTrade> queue = pendingTrades.get(target.getUUID());
        if (queue != null && queue.size() >= ModConfig.get().maxQueueSize) {
            requester.sendSystemMessage(Component.literal(
                    "§c" + target.getName().getString() + "'s trade queue is full. Try again later."));
            return;
        }

        long expiresAt = System.currentTimeMillis() + (ModConfig.get().timeoutSeconds * 1000L);
        pendingTrades.computeIfAbsent(target.getUUID(), k -> new ArrayList<>())
                .add(new PendingTrade(requester.getUUID(), requester.getName().getString(), target.getUUID(),
                        requester.getUUID(), target.getUUID(), storedAmount, storedIsExp, all, expiresAt));

        String desc = isExp || all ? tradeDesc(target, storedAmount, storedIsExp, all)
                : amount + " level(s) (§e" + storedAmount + " §araw XP, based on your level)";
        MutableComponent balanceInfo = buildBalanceInfo(target, requester, storedAmount, storedIsExp, all);
        requester.sendSystemMessage(Component.literal("§aRequest sent to §e" + target.getName().getString()
                + "§a: §e" + desc + "§a. They have §e" + ModConfig.get().timeoutSeconds + " §aseconds to respond.\n")
                .append(balanceInfo)
                .append(cancelButton()));
        target.sendSystemMessage(buildResponderMessage("§e" + requester.getName().getString()
                + " §ais requesting §e" + desc + " §afrom you.\n", balanceInfo));
    }

    /** Accept the first trade in queue, or a specific trade from fromPlayerId if provided. */
    public static void accept(ServerPlayer responder, UUID fromPlayerId, MinecraftServer server) {
        List<PendingTrade> queue = pendingTrades.get(responder.getUUID());
        if (queue == null || queue.isEmpty()) {
            responder.sendSystemMessage(Component.literal("§cYou have no pending trades."));
            return;
        }

        PendingTrade trade;
        if (fromPlayerId != null) {
            trade = findTradeFrom(responder.getUUID(), fromPlayerId);
            if (trade == null) {
                responder.sendSystemMessage(Component.literal("§cNo pending trade from that player."));
                return;
            }
        } else {
            trade = queue.get(0);
        }

        ServerPlayer giver = server.getPlayerList().getPlayer(trade.giverId());
        ServerPlayer receiver = server.getPlayerList().getPlayer(trade.receiverId());

        if (giver == null || receiver == null) {
            responder.sendSystemMessage(Component.literal("§cTrade cancelled: a player involved is no longer online."));
            removeTrade(responder.getUUID(), trade);
            notifyQueueRemaining(responder);
            return;
        }

        if (!revalidateGiver(giver, receiver, trade.amount(), trade.isExp(), trade.all())) {
            removeTrade(responder.getUUID(), trade);
            notifyQueueRemaining(responder);
            return;
        }

        executeTrade(giver, receiver, trade.amount(), trade.isExp(), trade.all());
        removeTrade(responder.getUUID(), trade);
        notifyQueueRemaining(responder);
    }

    /** Decline the first trade in queue, or a specific trade from fromPlayerId if provided. */
    public static void decline(ServerPlayer responder, UUID fromPlayerId, MinecraftServer server) {
        List<PendingTrade> queue = pendingTrades.get(responder.getUUID());
        if (queue == null || queue.isEmpty()) {
            responder.sendSystemMessage(Component.literal("§cYou have no pending trades."));
            return;
        }

        PendingTrade trade;
        if (fromPlayerId != null) {
            trade = findTradeFrom(responder.getUUID(), fromPlayerId);
            if (trade == null) {
                responder.sendSystemMessage(Component.literal("§cNo pending trade from that player."));
                return;
            }
        } else {
            trade = queue.get(0);
        }

        removeTrade(responder.getUUID(), trade);
        responder.sendSystemMessage(Component.literal("§cTrade declined."));

        ServerPlayer initiator = server.getPlayerList().getPlayer(trade.initiatorId());
        if (initiator != null) {
            initiator.sendSystemMessage(Component.literal("§e" + responder.getName().getString()
                    + " §cdeclined your trade."));
        }
        notifyQueueRemaining(responder);
    }

    /** Cancel the current outgoing trade from this player. */
    public static void cancel(ServerPlayer initiator, MinecraftServer server) {
        UUID responderId = findResponderForInitiator(initiator.getUUID());
        if (responderId == null) {
            initiator.sendSystemMessage(Component.literal("§cYou have no pending outgoing trade to cancel."));
            return;
        }
        cancelOutgoing(initiator, server);
        initiator.sendSystemMessage(Component.literal("§aTrade cancelled."));
    }

    public static boolean hasPendingTrades(java.util.UUID playerId) {
        List<PendingTrade> queue = pendingTrades.get(playerId);
        return queue != null && !queue.isEmpty();
    }

    /** Show the player their incoming trade queue. */
    public static void openQueueGui(ServerPlayer player, MinecraftServer server) {
        List<PendingTrade> queue = pendingTrades.get(player.getUUID());
        if (queue == null || queue.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7You have no pending trades."));
            return;
        }
        List<TradeQueueMenu.TradeRow> rows = queue.stream()
                .map(t -> {
                    ServerPlayer initiatorPlayer = server.getPlayerList().getPlayer(t.initiatorId());
                    GameProfile profile = initiatorPlayer != null
                            ? initiatorPlayer.getGameProfile()
                            : new GameProfile(t.initiatorId(), t.initiatorName());
                    return new TradeQueueMenu.TradeRow(
                            profile,
                            t.giverId().equals(t.initiatorId()),
                            tradeDescStatic(t.amount(), t.isExp(), t.all()),
                            t.expiresAt()
                    );
                })
                .collect(Collectors.toList());
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new TradeQueueMenu(id, inv, rows),
                Component.literal("§8Trade Queue (" + rows.size() + "/" + ModConfig.get().maxQueueSize + ")")
        ));
    }

    public static void adminTransfer(ServerPlayer from, ServerPlayer to,
                                     int amount, boolean isExp, boolean all,
                                     CommandSourceStack source) {
        if (from.getUUID().equals(to.getUUID())) {
            source.sendFailure(Component.literal("§cCannot transfer to the same player."));
            return;
        }
        // Admin bypasses threshold
        if (!validateGiver(from, amount, isExp, all, false)) {
            source.sendFailure(Component.literal("§c" + from.getName().getString()
                    + " does not have enough XP for that transfer."));
            return;
        }

        UUID adminId = source.getPlayer() != null ? source.getPlayer().getUUID() : null;
        if (adminId == null) {
            source.sendFailure(Component.literal("§cThis command must be run by a player."));
            return;
        }

        pendingAdminTransfers.put(adminId, new PendingAdminTransfer(from.getUUID(), to.getUUID(), amount, isExp, all));

        String desc = adminTransferDesc(amount, isExp, all);
        MutableComponent balanceInfo = buildBalanceInfo(from, to, amount, isExp, all);
        source.sendSuccess(() -> Component.literal("§eTransfer §e" + desc + " §efrom §e"
                + from.getName().getString() + " §eto §e" + to.getName().getString() + "§e.\n")
                .append(balanceInfo)
                .append(Component.literal("[Confirm]")
                        .withStyle(s -> s
                                .withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent.RunCommand("/exptrade admin confirm"))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("Click to confirm this transfer")))
                        )
                )
                .append(Component.literal(" "))
                .append(Component.literal("[Cancel]")
                        .withStyle(s -> s
                                .withColor(ChatFormatting.RED)
                                .withBold(true)
                                .withClickEvent(new ClickEvent.RunCommand("/exptrade admin cancel"))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("Click to cancel this transfer")))
                        )
                ), false);
    }

    public static void adminConfirm(CommandSourceStack source, MinecraftServer server) {
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal("§cThis command must be run by a player."));
            return;
        }
        UUID adminId = source.getPlayer().getUUID();
        PendingAdminTransfer pending = pendingAdminTransfers.remove(adminId);
        if (pending == null) {
            source.sendFailure(Component.literal("§cYou have no pending admin transfer to confirm."));
            return;
        }

        ServerPlayer from = server.getPlayerList().getPlayer(pending.fromId());
        ServerPlayer to = server.getPlayerList().getPlayer(pending.toId());

        if (from == null || to == null) {
            source.sendFailure(Component.literal("§cTransfer cancelled: a player involved is no longer online."));
            return;
        }
        // Admin bypasses threshold on confirm too
        if (!validateGiver(from, pending.amount(), pending.isExp(), pending.all(), false)) {
            source.sendFailure(Component.literal("§c" + from.getName().getString()
                    + " no longer has enough XP for that transfer."));
            return;
        }

        executeTrade(from, to, pending.amount(), pending.isExp(), pending.all());
        String desc = adminTransferDesc(pending.amount(), pending.isExp(), pending.all());
        source.sendSuccess(() -> Component.literal("§aTransferred §e" + desc + " §afrom §e"
                + from.getName().getString() + " §ato §e" + to.getName().getString() + "§a."), true);
    }

    public static void adminCancel(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal("§cThis command must be run by a player."));
            return;
        }
        if (pendingAdminTransfers.remove(source.getPlayer().getUUID()) == null) {
            source.sendFailure(Component.literal("§cYou have no pending admin transfer to cancel."));
            return;
        }
        source.sendSuccess(() -> Component.literal("§aAdmin transfer cancelled."), false);
    }

    public static void tick(MinecraftServer server) {
        if (pendingTrades.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, List<PendingTrade>>> mapIter = pendingTrades.entrySet().iterator();
        while (mapIter.hasNext()) {
            Map.Entry<UUID, List<PendingTrade>> entry = mapIter.next();
            Iterator<PendingTrade> queueIter = entry.getValue().iterator();
            while (queueIter.hasNext()) {
                PendingTrade trade = queueIter.next();
                if (now > trade.expiresAt()) {
                    queueIter.remove();
                    ServerPlayer initiator = server.getPlayerList().getPlayer(trade.initiatorId());
                    ServerPlayer responder = server.getPlayerList().getPlayer(trade.responderId());
                    String responderName = responder != null ? responder.getName().getString() : "the player";
                    String initiatorName = initiator != null ? initiator.getName().getString() : "a player";
                    if (initiator != null)
                        initiator.sendSystemMessage(Component.literal("§cYour trade with §e" + responderName + " §chas expired."));
                    if (responder != null)
                        responder.sendSystemMessage(Component.literal("§cThe trade from §e" + initiatorName + " §chas expired."));
                }
            }
            if (entry.getValue().isEmpty()) mapIter.remove();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void notifyBoth(ServerPlayer a, ServerPlayer b, String msgA, String msgB) {
        a.sendSystemMessage(Component.literal(msgA));
        b.sendSystemMessage(Component.literal(msgB));
    }

    private static String tradeDesc(ServerPlayer giver, int amount, boolean isExp, boolean all) {
        if (all) return "all XP";
        if (isExp) return amount + " raw XP";
        return amount + " level(s) (" + rawXpCost(giver.experienceLevel, amount) + " raw XP)";
    }

    private static String tradeDescStatic(int amount, boolean isExp, boolean all) {
        if (all) return "all XP";
        if (isExp) return amount + " raw XP";
        return amount + " level(s)";
    }

    private static String adminTransferDesc(int amount, boolean isExp, boolean all) {
        if (all) return "all XP";
        if (isExp) return amount + " raw XP";
        return amount + " level(s)";
    }

    private static MutableComponent buildBalanceInfo(ServerPlayer giver, ServerPlayer receiver,
                                                      int amount, boolean isExp, boolean all) {
        int giverLvlBefore = giver.experienceLevel;
        int giverXpBefore  = actualCurrentXp(giver);
        int recLvlBefore   = receiver.experienceLevel;
        int recXpBefore    = actualCurrentXp(receiver);

        int xpTransferred;
        int giverLvlAfter;
        int giverXpAfter;
        if (all) {
            xpTransferred = giverXpBefore;
            giverLvlAfter = 0;
            giverXpAfter  = 0;
        } else if (isExp) {
            xpTransferred = amount;
            giverXpAfter  = giverXpBefore - amount;
            giverLvlAfter = levelForTotalXp(giverXpAfter);
        } else {
            xpTransferred = rawXpCost(giverLvlBefore, amount);
            giverLvlAfter = giverLvlBefore - amount;
            giverXpAfter  = giverXpBefore - xpTransferred;
        }
        int recXpAfter  = recXpBefore + xpTransferred;
        int recLvlAfter = levelForTotalXp(recXpAfter);

        return Component.literal("\n§7" + giver.getName().getString() + ": §e"
                + giverLvlBefore + " lvl §7(§e" + giverXpBefore + " xp§7)"
                + " §7\u2192 §e" + giverLvlAfter + " lvl §7(§e" + giverXpAfter + " xp§7)")
                .append(Component.literal("\n§7" + receiver.getName().getString() + ": §e"
                        + recLvlBefore + " lvl §7(§e" + recXpBefore + " xp§7)"
                        + " §7\u2192 §e~" + recLvlAfter + " lvl §7(§e~" + recXpAfter + " xp§7)\n"));
    }

    private static MutableComponent buildResponderMessage(String context, MutableComponent balanceInfo) {
        return Component.literal(context)
                .append(balanceInfo)
                .append(Component.literal("[View Queue]")
                        .withStyle(s -> s
                                .withColor(ChatFormatting.YELLOW)
                                .withBold(true)
                                .withClickEvent(new ClickEvent.RunCommand("/exptrade pending"))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("Click to open your trade queue")))
                        )
                );
    }

    private static MutableComponent cancelButton() {
        return Component.literal("[Cancel]")
                .withStyle(s -> s
                        .withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent.RunCommand("/exptrade cancel"))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Click to cancel your trade")))
                );
    }
}
