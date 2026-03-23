package net.lunix.exptrade;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class TradeManager {

    private record PendingTrade(UUID from, UUID to, int levels, boolean all, long expiresAt) {}

    // Keyed by receiver UUID — one pending offer per receiver at a time
    private static final Map<UUID, PendingTrade> pendingTrades = new HashMap<>();

    public static void offer(ServerPlayerEntity giver, ServerPlayerEntity receiver, int levels, boolean all) {
        if (giver.getUuid().equals(receiver.getUuid())) {
            giver.sendMessage(Text.literal("§cYou cannot trade with yourself."), false);
            return;
        }

        int threshold = giver.getAttachedOrElse(PlayerAttachments.THRESHOLD, 0);

        if (all) {
            if (giver.experienceLevel <= 0 && giver.experienceProgress == 0f) {
                giver.sendMessage(Text.literal("§cYou have no XP to give."), false);
                return;
            }
        } else {
            if (giver.experienceLevel < levels) {
                giver.sendMessage(Text.literal("§cYou only have §e" + giver.experienceLevel + " §clevels."), false);
                return;
            }
            if (giver.experienceLevel - levels < threshold) {
                giver.sendMessage(Text.literal("§cThat would put you below your threshold of §e" + threshold + " §clevels."), false);
                return;
            }
        }

        // Cancel any existing outgoing offer from this giver
        pendingTrades.entrySet().removeIf(e -> e.getValue().from().equals(giver.getUuid()));

        long expiresAt = System.currentTimeMillis() + (ModConfig.get().timeoutSeconds * 1000L);
        pendingTrades.put(receiver.getUuid(), new PendingTrade(giver.getUuid(), receiver.getUuid(), levels, all, expiresAt));

        String offerDesc = all ? "all their XP" : levels + " level(s)";
        giver.sendMessage(Text.literal("§aTrade offer sent to §e" + receiver.getName().getString()
                + "§a. They have §e" + ModConfig.get().timeoutSeconds + " §aseconds to accept."), false);
        receiver.sendMessage(Text.literal("§e" + giver.getName().getString()
                + " §awants to give you §e" + offerDesc
                + "§a. Use §f/exptrade accept §aor §f/exptrade decline§a."), false);
    }

    public static void accept(ServerPlayerEntity receiver, MinecraftServer server) {
        PendingTrade trade = pendingTrades.get(receiver.getUuid());
        if (trade == null) {
            receiver.sendMessage(Text.literal("§cYou have no pending trade offer."), false);
            return;
        }

        ServerPlayerEntity giver = server.getPlayerManager().getPlayer(trade.from());
        if (giver == null) {
            receiver.sendMessage(Text.literal("§cThe offering player is no longer online. Trade cancelled."), false);
            pendingTrades.remove(receiver.getUuid());
            return;
        }

        int threshold = giver.getAttachedOrElse(PlayerAttachments.THRESHOLD, 0);

        if (trade.all()) {
            if (giver.experienceLevel <= 0 && giver.experienceProgress == 0f) {
                notify(giver, receiver, "§cTrade failed: you have no XP.", "§cTrade failed: the giver no longer has any XP.");
                pendingTrades.remove(receiver.getUuid());
                return;
            }
        } else {
            if (giver.experienceLevel < trade.levels() || giver.experienceLevel - trade.levels() < threshold) {
                notify(giver, receiver,
                        "§cTrade failed: you no longer have enough levels.",
                        "§cTrade failed: the giver no longer has enough levels.");
                pendingTrades.remove(receiver.getUuid());
                return;
            }
        }

        if (trade.all()) {
            int totalXp = giver.totalExperience;
            // Zero out the giver
            giver.addExperienceLevels(-giver.experienceLevel);
            giver.experienceProgress = 0f;
            giver.totalExperience = 0;
            // Give all that XP to the receiver
            receiver.addExperience(totalXp);
            notify(giver, receiver,
                    "§aYou gave all your XP to §e" + receiver.getName().getString() + "§a.",
                    "§aYou received all XP from §e" + giver.getName().getString() + "§a.");
        } else {
            giver.addExperienceLevels(-trade.levels());
            receiver.addExperienceLevels(trade.levels());
            notify(giver, receiver,
                    "§aYou gave §e" + trade.levels() + " §alevel(s) to §e" + receiver.getName().getString() + "§a.",
                    "§aYou received §e" + trade.levels() + " §alevel(s) from §e" + giver.getName().getString() + "§a.");
        }

        pendingTrades.remove(receiver.getUuid());
    }

    public static void decline(ServerPlayerEntity receiver, MinecraftServer server) {
        PendingTrade trade = pendingTrades.remove(receiver.getUuid());
        if (trade == null) {
            receiver.sendMessage(Text.literal("§cYou have no pending trade offer."), false);
            return;
        }
        receiver.sendMessage(Text.literal("§cTrade declined."), false);
        ServerPlayerEntity giver = server.getPlayerManager().getPlayer(trade.from());
        if (giver != null) {
            giver.sendMessage(Text.literal("§e" + receiver.getName().getString() + " §cdeclined your trade offer."), false);
        }
    }

    public static void cancel(ServerPlayerEntity giver) {
        UUID giverUuid = giver.getUuid();
        UUID receiverUuid = null;
        for (Map.Entry<UUID, PendingTrade> entry : pendingTrades.entrySet()) {
            if (entry.getValue().from().equals(giverUuid)) {
                receiverUuid = entry.getKey();
                break;
            }
        }
        if (receiverUuid == null) {
            giver.sendMessage(Text.literal("§cYou have no pending outgoing trade offer."), false);
            return;
        }
        pendingTrades.remove(receiverUuid);
        giver.sendMessage(Text.literal("§aTrade offer cancelled."), false);
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingTrade>> iter = pendingTrades.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, PendingTrade> entry = iter.next();
            if (now > entry.getValue().expiresAt()) {
                PendingTrade trade = entry.getValue();
                iter.remove();
                ServerPlayerEntity giver = server.getPlayerManager().getPlayer(trade.from());
                ServerPlayerEntity receiver = server.getPlayerManager().getPlayer(trade.to());
                String receiverName = receiver != null ? receiver.getName().getString() : "the player";
                String giverName = giver != null ? giver.getName().getString() : "a player";
                if (giver != null)
                    giver.sendMessage(Text.literal("§cYour trade offer to §e" + receiverName + " §chas expired."), false);
                if (receiver != null)
                    receiver.sendMessage(Text.literal("§cThe trade offer from §e" + giverName + " §chas expired."), false);
            }
        }
    }

    private static void notify(ServerPlayerEntity giver, ServerPlayerEntity receiver, String giverMsg, String receiverMsg) {
        giver.sendMessage(Text.literal(giverMsg), false);
        receiver.sendMessage(Text.literal(receiverMsg), false);
    }
}
