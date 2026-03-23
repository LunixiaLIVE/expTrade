package net.lunix.exptrade;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class TradeManager {

    /**
     * Represents a pending trade in either direction.
     *
     * @param initiatorId  UUID of the player who created this trade (offer or request)
     * @param giverId      UUID of the player who will lose XP
     * @param receiverId   UUID of the player who will gain XP
     * @param responderId  UUID of the player who must accept or decline
     * @param levels       number of levels to transfer (ignored when all=true)
     * @param all          if true, transfer all XP from giver
     * @param expiresAt    System.currentTimeMillis() expiry timestamp
     */
    private record PendingTrade(
            UUID initiatorId,
            UUID giverId,
            UUID receiverId,
            UUID responderId,
            int levels,
            boolean all,
            long expiresAt
    ) {}

    // Keyed by responderId — one pending response per player at a time
    private static final Map<UUID, PendingTrade> pendingTrades = new HashMap<>();

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

    /**
     * Raw XP cost of 'levels' levels from a player's current level.
     * E.g. at level 30, giving 5 levels costs the XP difference between level 30 and level 25.
     */
    public static int rawXpCost(int currentLevel, int levels) {
        return totalXpForLevel(currentLevel) - totalXpForLevel(currentLevel - levels);
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    private static boolean validateGiver(ServerPlayerEntity giver, int levels, boolean all) {
        int threshold = giver.getAttachedOrElse(PlayerAttachments.THRESHOLD, 0);

        if (all) {
            if (giver.experienceLevel <= 0 && giver.experienceProgress == 0f) {
                giver.sendMessage(Text.literal("§cYou have no XP to give."), false);
                return false;
            }
            return true;
        }

        if (giver.experienceLevel < levels) {
            giver.sendMessage(Text.literal("§cYou only have §e" + giver.experienceLevel
                    + " §clevels (need §e" + levels + "§c)."), false);
            return false;
        }
        if (giver.experienceLevel - levels < threshold) {
            giver.sendMessage(Text.literal("§cThat would put you below your threshold of §e"
                    + threshold + " §clevels."), false);
            return false;
        }
        return true;
    }

    private static boolean revalidateGiver(ServerPlayerEntity giver, ServerPlayerEntity receiver,
                                           int levels, boolean all) {
        int threshold = giver.getAttachedOrElse(PlayerAttachments.THRESHOLD, 0);

        if (all) {
            if (giver.experienceLevel <= 0 && giver.experienceProgress == 0f) {
                notifyBoth(giver, receiver,
                        "§cTrade failed: you have no XP.",
                        "§cTrade failed: the giver no longer has any XP.");
                return false;
            }
            return true;
        }

        if (giver.experienceLevel < levels || giver.experienceLevel - levels < threshold) {
            notifyBoth(giver, receiver,
                    "§cTrade failed: you no longer have enough levels.",
                    "§cTrade failed: the giver no longer has enough levels.");
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Trade execution
    // -------------------------------------------------------------------------

    private static void executeTrade(ServerPlayerEntity giver, ServerPlayerEntity receiver,
                                     int levels, boolean all) {
        if (all) {
            int totalXp = giver.totalExperience;
            giver.addExperienceLevels(-giver.experienceLevel);
            giver.experienceProgress = 0f;
            giver.totalExperience = 0;
            receiver.addExperience(totalXp);
            notifyBoth(giver, receiver,
                    "§aYou gave all your XP to §e" + receiver.getName().getString() + "§a.",
                    "§aYou received all XP from §e" + giver.getName().getString() + "§a.");
        } else {
            int rawXp = rawXpCost(giver.experienceLevel, levels);
            giver.addExperienceLevels(-levels);
            receiver.addExperience(rawXp);
            notifyBoth(giver, receiver,
                    "§aYou gave §e" + levels + " §alevel(s) (§e" + rawXp + " §araw XP) to §e"
                            + receiver.getName().getString() + "§a.",
                    "§aYou received §e" + rawXp + " §araw XP (from §e" + levels + " §agiver level(s)) from §e"
                            + giver.getName().getString() + "§a.");
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Giver initiates: offers XP to receiver.
     * Receiver must accept/decline.
     */
    public static void offer(ServerPlayerEntity giver, ServerPlayerEntity receiver, int levels, boolean all) {
        if (giver.getUuid().equals(receiver.getUuid())) {
            giver.sendMessage(Text.literal("§cYou cannot trade with yourself."), false);
            return;
        }
        if (!validateGiver(giver, levels, all)) return;

        // Cancel any existing trade this player already initiated
        pendingTrades.entrySet().removeIf(e -> e.getValue().initiatorId().equals(giver.getUuid()));

        long expiresAt = System.currentTimeMillis() + (ModConfig.get().timeoutSeconds * 1000L);
        pendingTrades.put(receiver.getUuid(), new PendingTrade(
                giver.getUuid(), giver.getUuid(), receiver.getUuid(), receiver.getUuid(),
                levels, all, expiresAt));

        String desc = all ? "all their XP" : levels + " level(s) ("
                + rawXpCost(giver.experienceLevel, levels) + " raw XP)";
        giver.sendMessage(Text.literal("§aOffer sent to §e" + receiver.getName().getString()
                + "§a. They have §e" + ModConfig.get().timeoutSeconds + " §aseconds to respond."), false);
        receiver.sendMessage(Text.literal("§e" + giver.getName().getString()
                + " §awants to give you §e" + desc
                + "§a. Use §f/exptrade accept §aor §f/exptrade decline§a."), false);
    }

    /**
     * Receiver initiates: requests XP from target (the giver).
     * Target must accept/decline.
     */
    public static void request(ServerPlayerEntity requester, ServerPlayerEntity target, int levels, boolean all) {
        if (requester.getUuid().equals(target.getUuid())) {
            requester.sendMessage(Text.literal("§cYou cannot request from yourself."), false);
            return;
        }

        // Pre-validate that the target would even have enough (advisory — re-checked on accept)
        if (!all && target.experienceLevel < levels) {
            requester.sendMessage(Text.literal("§c" + target.getName().getString()
                    + " only has §e" + target.experienceLevel + " §clevels."), false);
            return;
        }

        // Cancel any existing trade this player already initiated
        pendingTrades.entrySet().removeIf(e -> e.getValue().initiatorId().equals(requester.getUuid()));

        long expiresAt = System.currentTimeMillis() + (ModConfig.get().timeoutSeconds * 1000L);
        pendingTrades.put(target.getUuid(), new PendingTrade(
                requester.getUuid(), target.getUuid(), requester.getUuid(), target.getUuid(),
                levels, all, expiresAt));

        String desc = all ? "all your XP" : levels + " level(s) (~"
                + rawXpCost(target.experienceLevel, levels) + " raw XP at your current level)";
        requester.sendMessage(Text.literal("§aRequest sent to §e" + target.getName().getString()
                + "§a. They have §e" + ModConfig.get().timeoutSeconds + " §aseconds to respond."), false);
        target.sendMessage(Text.literal("§e" + requester.getName().getString()
                + " §ais requesting §e" + desc
                + " §afrom you. Use §f/exptrade accept §aor §f/exptrade decline§a."), false);
    }

    /**
     * The responder accepts whatever trade is pending with them.
     */
    public static void accept(ServerPlayerEntity responder, MinecraftServer server) {
        PendingTrade trade = pendingTrades.get(responder.getUuid());
        if (trade == null) {
            responder.sendMessage(Text.literal("§cYou have no pending trade to respond to."), false);
            return;
        }

        ServerPlayerEntity giver = server.getPlayerManager().getPlayer(trade.giverId());
        ServerPlayerEntity receiver = server.getPlayerManager().getPlayer(trade.receiverId());

        if (giver == null || receiver == null) {
            responder.sendMessage(Text.literal("§cTrade cancelled: a player involved is no longer online."), false);
            pendingTrades.remove(responder.getUuid());
            return;
        }

        if (!revalidateGiver(giver, receiver, trade.levels(), trade.all())) {
            pendingTrades.remove(responder.getUuid());
            return;
        }

        executeTrade(giver, receiver, trade.levels(), trade.all());
        pendingTrades.remove(responder.getUuid());
    }

    /**
     * The responder declines whatever trade is pending with them.
     */
    public static void decline(ServerPlayerEntity responder, MinecraftServer server) {
        PendingTrade trade = pendingTrades.remove(responder.getUuid());
        if (trade == null) {
            responder.sendMessage(Text.literal("§cYou have no pending trade to respond to."), false);
            return;
        }
        responder.sendMessage(Text.literal("§cTrade declined."), false);
        ServerPlayerEntity initiator = server.getPlayerManager().getPlayer(trade.initiatorId());
        if (initiator != null) {
            initiator.sendMessage(Text.literal("§e" + responder.getName().getString()
                    + " §cdeclined your trade."), false);
        }
    }

    /**
     * The initiator cancels their pending outgoing trade.
     */
    public static void cancel(ServerPlayerEntity initiator) {
        UUID initiatorId = initiator.getUuid();
        UUID responderUuid = null;
        for (Map.Entry<UUID, PendingTrade> entry : pendingTrades.entrySet()) {
            if (entry.getValue().initiatorId().equals(initiatorId)) {
                responderUuid = entry.getKey();
                break;
            }
        }
        if (responderUuid == null) {
            initiator.sendMessage(Text.literal("§cYou have no pending outgoing trade to cancel."), false);
            return;
        }
        pendingTrades.remove(responderUuid);
        initiator.sendMessage(Text.literal("§aTrade cancelled."), false);
    }

    /**
     * Called every server tick to expire stale trades.
     */
    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingTrade>> iter = pendingTrades.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, PendingTrade> entry = iter.next();
            if (now > entry.getValue().expiresAt()) {
                PendingTrade trade = entry.getValue();
                iter.remove();
                ServerPlayerEntity initiator = server.getPlayerManager().getPlayer(trade.initiatorId());
                ServerPlayerEntity responder = server.getPlayerManager().getPlayer(trade.responderId());
                String responderName = responder != null ? responder.getName().getString() : "the player";
                String initiatorName = initiator != null ? initiator.getName().getString() : "a player";
                if (initiator != null)
                    initiator.sendMessage(Text.literal("§cYour trade with §e" + responderName + " §chas expired."), false);
                if (responder != null)
                    responder.sendMessage(Text.literal("§cThe trade from §e" + initiatorName + " §chas expired."), false);
            }
        }
    }

    private static void notifyBoth(ServerPlayerEntity a, ServerPlayerEntity b, String msgA, String msgB) {
        a.sendMessage(Text.literal(msgA), false);
        b.sendMessage(Text.literal(msgB), false);
    }
}
