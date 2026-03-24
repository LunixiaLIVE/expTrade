package net.lunix.exptrade;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

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
            UUID giverId,
            UUID receiverId,
            UUID responderId,
            int amount,
            boolean isExp,
            boolean all,
            long expiresAt
    ) {}

    private record PendingAdminTransfer(UUID fromId, UUID toId, int amount, boolean isExp, boolean all) {}

    // Keyed by responderId — one pending response per player at a time
    private static final Map<UUID, PendingTrade> pendingTrades = new HashMap<>();

    // Keyed by admin UUID — one pending confirmation per admin at a time
    private static final Map<UUID, PendingAdminTransfer> pendingAdminTransfers = new HashMap<>();

    // -------------------------------------------------------------------------
    // XP math
    // -------------------------------------------------------------------------

    /** XP required to go from level N to level N+1. */
    private static int xpPerLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    /** Total XP to reach level N from 0 (whole levels, no fractional progress). */
    private static int totalXpForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    /** Highest whole level reachable with the given total raw XP. */
    private static int levelForTotalXp(int totalXp) {
        int level = 0;
        while (totalXpForLevel(level + 1) <= totalXp) level++;
        return level;
    }

    /** Raw XP cost of N levels starting from currentLevel. */
    public static int rawXpCost(int currentLevel, int levels) {
        return totalXpForLevel(currentLevel) - totalXpForLevel(currentLevel - levels);
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    private static boolean validateGiver(ServerPlayer giver, int amount, boolean isExp, boolean all) {
        int threshold = PlayerDataStore.getThreshold(giver.getUUID());

        if (all) {
            if (giver.totalExperience <= 0) {
                giver.sendSystemMessage(Component.literal("§cYou have no XP to give."));
                return false;
            }
            return true;
        }

        if (isExp) {
            if (giver.totalExperience < amount) {
                giver.sendSystemMessage(Component.literal("§cYou only have §e" + giver.totalExperience
                        + " §craw XP (need §e" + amount + "§c)."));
                return false;
            }
            if (levelForTotalXp(giver.totalExperience - amount) < threshold) {
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
            if (giver.totalExperience <= 0) {
                notifyBoth(giver, receiver,
                        "§cTrade failed: you have no XP.",
                        "§cTrade failed: the giver no longer has any XP.");
                return false;
            }
            return true;
        }

        if (isExp) {
            if (giver.totalExperience < amount || levelForTotalXp(giver.totalExperience - amount) < threshold) {
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
            int totalXp = giver.totalExperience;
            giver.giveExperienceLevels(-giver.experienceLevel);
            giver.experienceProgress = 0f;
            giver.totalExperience = 0;
            receiver.giveExperiencePoints(totalXp);
            notifyBoth(giver, receiver,
                    "§aYou gave all your XP to §e" + receiver.getName().getString() + "§a.",
                    "§aYou received all XP from §e" + giver.getName().getString() + "§a.");

        } else if (isExp) {
            int newTotal = giver.totalExperience - amount;
            int newLevel = levelForTotalXp(newTotal);
            giver.giveExperienceLevels(newLevel - giver.experienceLevel);
            int progressXp = newTotal - totalXpForLevel(newLevel);
            giver.experienceProgress = newLevel >= 0 ? progressXp / (float) xpPerLevel(newLevel) : 0f;
            giver.totalExperience = newTotal;
            receiver.giveExperiencePoints(amount);
            notifyBoth(giver, receiver,
                    "§aYou gave §e" + amount + " §araw XP to §e" + receiver.getName().getString() + "§a.",
                    "§aYou received §e" + amount + " §araw XP from §e" + giver.getName().getString() + "§a.");

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
                             int amount, boolean isExp, boolean all) {
        if (giver.getUUID().equals(receiver.getUUID())) {
            giver.sendSystemMessage(Component.literal("§cYou cannot trade with yourself."));
            return;
        }
        if (!validateGiver(giver, amount, isExp, all)) return;

        pendingTrades.entrySet().removeIf(e -> e.getValue().initiatorId().equals(giver.getUUID()));

        long expiresAt = System.currentTimeMillis() + (ModConfig.get().timeoutSeconds * 1000L);
        pendingTrades.put(receiver.getUUID(), new PendingTrade(
                giver.getUUID(), giver.getUUID(), receiver.getUUID(), receiver.getUUID(),
                amount, isExp, all, expiresAt));

        String desc = tradeDesc(giver, amount, isExp, all);
        MutableComponent balanceInfo = buildBalanceInfo(giver, receiver, amount, isExp, all);
        giver.sendSystemMessage(Component.literal("§aOffer sent to §e" + receiver.getName().getString()
                + "§a. They have §e" + ModConfig.get().timeoutSeconds + " §aseconds to respond. ")
                .append(cancelButton()));
        receiver.sendSystemMessage(buildResponderMessage("§e" + giver.getName().getString()
                + " §awants to give you §e" + desc + "§a.\n", balanceInfo));
    }

    public static void request(ServerPlayer requester, ServerPlayer target,
                               int amount, boolean isExp, boolean all) {
        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(Component.literal("§cYou cannot request from yourself."));
            return;
        }

        // Advisory pre-check on the target (re-validated on accept)
        if (!all) {
            if (isExp && target.totalExperience < amount) {
                requester.sendSystemMessage(Component.literal("§c" + target.getName().getString()
                        + " only has §e" + target.totalExperience + " §craw XP."));
                return;
            } else if (!isExp && target.experienceLevel < amount) {
                requester.sendSystemMessage(Component.literal("§c" + target.getName().getString()
                        + " only has §e" + target.experienceLevel + " §clevels."));
                return;
            }
        }

        pendingTrades.entrySet().removeIf(e -> e.getValue().initiatorId().equals(requester.getUUID()));

        long expiresAt = System.currentTimeMillis() + (ModConfig.get().timeoutSeconds * 1000L);
        pendingTrades.put(target.getUUID(), new PendingTrade(
                requester.getUUID(), target.getUUID(), requester.getUUID(), target.getUUID(),
                amount, isExp, all, expiresAt));

        String desc = tradeDesc(target, amount, isExp, all);
        MutableComponent balanceInfo = buildBalanceInfo(target, requester, amount, isExp, all);
        requester.sendSystemMessage(Component.literal("§aRequest sent to §e" + target.getName().getString()
                + "§a. They have §e" + ModConfig.get().timeoutSeconds + " §aseconds to respond. ")
                .append(cancelButton()));
        target.sendSystemMessage(buildResponderMessage("§e" + requester.getName().getString()
                + " §ais requesting §e" + desc + " §afrom you.\n", balanceInfo));
    }

    public static void accept(ServerPlayer responder, MinecraftServer server) {
        PendingTrade trade = pendingTrades.get(responder.getUUID());
        if (trade == null) {
            responder.sendSystemMessage(Component.literal("§cYou have no pending trade to respond to."));
            return;
        }

        ServerPlayer giver = server.getPlayerList().getPlayer(trade.giverId());
        ServerPlayer receiver = server.getPlayerList().getPlayer(trade.receiverId());

        if (giver == null || receiver == null) {
            responder.sendSystemMessage(Component.literal("§cTrade cancelled: a player involved is no longer online."));
            pendingTrades.remove(responder.getUUID());
            return;
        }

        if (!revalidateGiver(giver, receiver, trade.amount(), trade.isExp(), trade.all())) {
            pendingTrades.remove(responder.getUUID());
            return;
        }

        executeTrade(giver, receiver, trade.amount(), trade.isExp(), trade.all());
        pendingTrades.remove(responder.getUUID());
    }

    public static void decline(ServerPlayer responder, MinecraftServer server) {
        PendingTrade trade = pendingTrades.remove(responder.getUUID());
        if (trade == null) {
            responder.sendSystemMessage(Component.literal("§cYou have no pending trade to respond to."));
            return;
        }
        responder.sendSystemMessage(Component.literal("§cTrade declined."));
        ServerPlayer initiator = server.getPlayerList().getPlayer(trade.initiatorId());
        if (initiator != null) {
            initiator.sendSystemMessage(Component.literal("§e" + responder.getName().getString()
                    + " §cdeclined your trade."));
        }
    }

    public static void cancel(ServerPlayer initiator) {
        UUID initiatorId = initiator.getUUID();
        UUID responderUuid = null;
        for (Map.Entry<UUID, PendingTrade> entry : pendingTrades.entrySet()) {
            if (entry.getValue().initiatorId().equals(initiatorId)) {
                responderUuid = entry.getKey();
                break;
            }
        }
        if (responderUuid == null) {
            initiator.sendSystemMessage(Component.literal("§cYou have no pending outgoing trade to cancel."));
            return;
        }
        pendingTrades.remove(responderUuid);
        initiator.sendSystemMessage(Component.literal("§aTrade cancelled."));
    }

    public static void adminTransfer(ServerPlayer from, ServerPlayer to,
                                     int amount, boolean isExp, boolean all,
                                     CommandSourceStack source) {
        if (from.getUUID().equals(to.getUUID())) {
            source.sendFailure(Component.literal("§cCannot transfer to the same player."));
            return;
        }
        if (!validateGiver(from, amount, isExp, all)) {
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
        if (!validateGiver(from, pending.amount(), pending.isExp(), pending.all())) {
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
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingTrade>> iter = pendingTrades.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, PendingTrade> entry = iter.next();
            if (now > entry.getValue().expiresAt()) {
                PendingTrade trade = entry.getValue();
                iter.remove();
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
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void notifyBoth(ServerPlayer a, ServerPlayer b, String msgA, String msgB) {
        a.sendSystemMessage(Component.literal(msgA));
        b.sendSystemMessage(Component.literal(msgB));
    }

    /** Human-readable description of a trade amount from the giver's perspective. */
    private static String tradeDesc(ServerPlayer giver, int amount, boolean isExp, boolean all) {
        if (all) return "all XP";
        if (isExp) return amount + " raw XP";
        return amount + " level(s) (" + rawXpCost(giver.experienceLevel, amount) + " raw XP)";
    }

    /** Human-readable description for admin transfer prompts. */
    private static String adminTransferDesc(int amount, boolean isExp, boolean all) {
        if (all) return "all XP";
        if (isExp) return amount + " raw XP";
        return amount + " level(s)";
    }

    /**
     * Builds a two-line before/after balance preview for both players.
     * Receiver estimate is marked with ~ since their level depends on incoming raw XP.
     */
    private static MutableComponent buildBalanceInfo(ServerPlayer giver, ServerPlayer receiver,
                                                      int amount, boolean isExp, boolean all) {
        int giverLvlBefore = giver.experienceLevel;
        int giverXpBefore  = giver.totalExperience;
        int recLvlBefore   = receiver.experienceLevel;
        int recXpBefore    = receiver.totalExperience;

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
                .append(Component.literal("[Accept]")
                        .withStyle(s -> s
                                .withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent.RunCommand("/exptrade accept"))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("Click to accept this trade")))
                        )
                )
                .append(Component.literal(" "))
                .append(Component.literal("[Decline]")
                        .withStyle(s -> s
                                .withColor(ChatFormatting.RED)
                                .withBold(true)
                                .withClickEvent(new ClickEvent.RunCommand("/exptrade decline"))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("Click to decline this trade")))
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
