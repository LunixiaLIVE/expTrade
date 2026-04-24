package net.lunix.exptrade;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TradeManager {

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

    private static final Map<UUID, List<PendingTrade>> pendingTrades = new HashMap<>();
    private static final Map<UUID, PendingAdminTransfer> pendingAdminTransfers = new HashMap<>();
    private static BukkitTask tickTask;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public static void startTicking(ExpTradePaper plugin) {
        tickTask = new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public static void stopTicking() {
        if (tickTask != null) tickTask.cancel();
    }

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

    private static int actualCurrentXp(Player player) {
        int level = player.getLevel();
        int base = totalXpForLevel(level);
        int progress = Math.round(player.getExp() * xpPerLevel(Math.max(level, 0)));
        return base + progress;
    }

    private static void setXp(Player player, int totalXp) {
        totalXp = Math.max(0, totalXp);
        int level = levelForTotalXp(totalXp);
        int xpInLevel = totalXp - totalXpForLevel(level);
        player.setLevel(level);
        player.setExp(level == 0 ? 0f : (float) xpInLevel / xpPerLevel(level));
        player.setTotalExperience(totalXp);
    }

    // -------------------------------------------------------------------------
    // Queue helpers
    // -------------------------------------------------------------------------

    private static UUID findResponderForInitiator(UUID initiatorId) {
        for (Map.Entry<UUID, List<PendingTrade>> entry : pendingTrades.entrySet()) {
            for (PendingTrade trade : entry.getValue()) {
                if (trade.initiatorId().equals(initiatorId)) return entry.getKey();
            }
        }
        return null;
    }

    private static PendingTrade findTradeFrom(UUID responderId, UUID initiatorId) {
        List<PendingTrade> queue = pendingTrades.get(responderId);
        if (queue == null) return null;
        return queue.stream().filter(t -> t.initiatorId().equals(initiatorId)).findFirst().orElse(null);
    }

    private static boolean removeTrade(UUID responderId, PendingTrade trade) {
        List<PendingTrade> queue = pendingTrades.get(responderId);
        if (queue == null) return false;
        boolean removed = queue.remove(trade);
        if (queue.isEmpty()) pendingTrades.remove(responderId);
        return removed;
    }

    private static void notifyQueueRemaining(Player responder) {
        List<PendingTrade> queue = pendingTrades.get(responder.getUniqueId());
        if (queue != null && !queue.isEmpty()) {
            responder.sendMessage("§7You have §e" + queue.size() + " §7more pending trade(s). Type §e/exptrade pending§7 to view them.");
        }
    }

    public static boolean hasPendingTrades(UUID playerId) {
        List<PendingTrade> queue = pendingTrades.get(playerId);
        return queue != null && !queue.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private static boolean validateGiver(Player giver, int amount, boolean isExp, boolean all) {
        return validateGiver(giver, amount, isExp, all, true);
    }

    private static boolean validateGiver(Player giver, int amount, boolean isExp, boolean all, boolean checkThreshold) {
        int threshold = checkThreshold ? PlayerDataStore.getThreshold(giver.getUniqueId()) : 0;

        if (all) {
            if (actualCurrentXp(giver) <= 0) {
                giver.sendMessage("§cYou have no XP to give.");
                return false;
            }
            return true;
        }

        if (isExp) {
            int currentXp = actualCurrentXp(giver);
            if (currentXp < amount) {
                giver.sendMessage("§cYou don't have enough XP (have §e" + currentXp + "§c, need §e" + amount + "§c).");
                return false;
            }
            if (checkThreshold && threshold > 0) {
                int remainingXp = currentXp - amount;
                int remainingLevel = levelForTotalXp(remainingXp);
                if (remainingLevel < threshold) {
                    giver.sendMessage("§cThis trade would leave you below your §e" + threshold + "§c-level threshold.");
                    return false;
                }
            }
        } else {
            if (giver.getLevel() < amount) {
                giver.sendMessage("§cYou don't have enough levels (have §e" + giver.getLevel() + "§c, need §e" + amount + "§c).");
                return false;
            }
            if (checkThreshold && threshold > 0 && (giver.getLevel() - amount) < threshold) {
                giver.sendMessage("§cThis trade would leave you below your §e" + threshold + "§c-level threshold.");
                return false;
            }
        }
        return true;
    }

    private static boolean revalidateGiver(Player giver, Player receiver, int amount, boolean isExp, boolean all) {
        if (!validateGiver(giver, amount, isExp, all)) {
            giver.sendMessage("§cYour trade was cancelled: you no longer have enough XP.");
            receiver.sendMessage("§cThe trade from §e" + giver.getName() + " §cwas cancelled: they no longer have enough XP.");
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Trade execution
    // -------------------------------------------------------------------------

    private static void executeTrade(Player giver, Player receiver, int amount, boolean isExp, boolean all) {
        int xpToTransfer = all ? actualCurrentXp(giver)
                : isExp ? amount
                : rawXpCost(giver.getLevel(), amount);

        int giverXpBefore = actualCurrentXp(giver);
        int recXpBefore   = actualCurrentXp(receiver);

        setXp(giver, giverXpBefore - xpToTransfer);
        setXp(receiver, recXpBefore + xpToTransfer);

        String desc = all ? "all XP" : isExp ? xpToTransfer + " raw XP" : amount + " level(s)";
        giver.sendMessage("§aYou gave §e" + desc + " §ato §e" + receiver.getName() + "§a."
                + " §7(§e" + giver.getLevel() + " lvl§7 remaining)");
        receiver.sendMessage("§aYou received §e" + desc + " §afrom §e" + giver.getName() + "§a."
                + " §7(§e" + receiver.getLevel() + " lvl§7 now)");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static void offer(Player giver, Player receiver, int amount, boolean isExp, boolean all) {
        if (giver.getUniqueId().equals(receiver.getUniqueId())) {
            giver.sendMessage("§cYou cannot trade with yourself.");
            return;
        }
        if (!validateGiver(giver, amount, isExp, all)) return;

        List<PendingTrade> queue = pendingTrades.get(receiver.getUniqueId());
        if (queue != null && queue.size() >= ModConfig.get().maxQueueSize) {
            giver.sendMessage("§c" + receiver.getName() + "'s trade queue is full. Try again later.");
            return;
        }

        long expiresAt = System.currentTimeMillis() + (ModConfig.get().timeoutSeconds * 1000L);
        pendingTrades.computeIfAbsent(receiver.getUniqueId(), k -> new ArrayList<>())
                .add(new PendingTrade(giver.getUniqueId(), giver.getName(), giver.getUniqueId(),
                        receiver.getUniqueId(), receiver.getUniqueId(), amount, isExp, all, expiresAt));

        String desc = tradeDesc(giver, amount, isExp, all);
        String balanceInfo = buildBalanceInfo(giver, receiver, amount, isExp, all);
        giver.sendMessage("§aOffer sent to §e" + receiver.getName() + "§a: §e" + desc
                + "§a. They have §e" + ModConfig.get().timeoutSeconds + " §aseconds to respond. "
                + "§7[type /exptrade cancel to cancel]\n" + balanceInfo);
        receiver.sendMessage("§e" + giver.getName() + " §awants to give you §e" + desc + "§a.\n"
                + balanceInfo + "§7Type §e/exptrade pending§7 to view your trade queue.");
    }

    public static void request(Player requester, Player target, int amount, boolean isExp, boolean all) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            requester.sendMessage("§cYou cannot request from yourself.");
            return;
        }

        int storedAmount = amount;
        boolean storedIsExp = isExp;
        if (!isExp && !all) {
            storedAmount = rawXpCost(requester.getLevel() + amount, amount);
            storedIsExp = true;
        }

        if (!all) {
            int targetCurrentXp = actualCurrentXp(target);
            if (storedIsExp && targetCurrentXp < storedAmount) {
                requester.sendMessage("§c" + target.getName()
                        + " doesn't have enough XP for that (needs §e" + storedAmount + " §craw XP).");
                return;
            }
        }

        List<PendingTrade> queue = pendingTrades.get(target.getUniqueId());
        if (queue != null && queue.size() >= ModConfig.get().maxQueueSize) {
            requester.sendMessage("§c" + target.getName() + "'s trade queue is full. Try again later.");
            return;
        }

        long expiresAt = System.currentTimeMillis() + (ModConfig.get().timeoutSeconds * 1000L);
        pendingTrades.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>())
                .add(new PendingTrade(requester.getUniqueId(), requester.getName(), target.getUniqueId(),
                        requester.getUniqueId(), target.getUniqueId(), storedAmount, storedIsExp, all, expiresAt));

        String desc = isExp || all ? tradeDesc(target, storedAmount, storedIsExp, all)
                : amount + " level(s) (§e" + storedAmount + " §araw XP, based on your level)";
        String balanceInfo = buildBalanceInfo(target, requester, storedAmount, storedIsExp, all);
        requester.sendMessage("§aRequest sent to §e" + target.getName() + "§a: §e" + desc
                + "§a. They have §e" + ModConfig.get().timeoutSeconds + " §aseconds to respond. "
                + "§7[type /exptrade cancel to cancel]\n" + balanceInfo);
        target.sendMessage("§e" + requester.getName() + " §ais requesting §e" + desc + " §afrom you.\n"
                + balanceInfo + "§7Type §e/exptrade pending§7 to view your trade queue.");
    }

    public static void accept(Player responder, UUID fromPlayerId) {
        List<PendingTrade> queue = pendingTrades.get(responder.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            responder.sendMessage("§cYou have no pending trades.");
            return;
        }

        PendingTrade trade;
        if (fromPlayerId != null) {
            trade = findTradeFrom(responder.getUniqueId(), fromPlayerId);
            if (trade == null) {
                responder.sendMessage("§cNo pending trade from that player.");
                return;
            }
        } else {
            trade = queue.get(0);
        }

        Player giver    = Bukkit.getPlayer(trade.giverId());
        Player receiver = Bukkit.getPlayer(trade.receiverId());

        if (giver == null || receiver == null) {
            responder.sendMessage("§cTrade cancelled: a player involved is no longer online.");
            removeTrade(responder.getUniqueId(), trade);
            refreshOrClose(responder);
            return;
        }

        if (!revalidateGiver(giver, receiver, trade.amount(), trade.isExp(), trade.all())) {
            removeTrade(responder.getUniqueId(), trade);
            refreshOrClose(responder);
            return;
        }

        executeTrade(giver, receiver, trade.amount(), trade.isExp(), trade.all());
        removeTrade(responder.getUniqueId(), trade);
        refreshOrClose(responder);
    }

    public static void decline(Player responder, UUID fromPlayerId) {
        List<PendingTrade> queue = pendingTrades.get(responder.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            responder.sendMessage("§cYou have no pending trades.");
            return;
        }

        PendingTrade trade;
        if (fromPlayerId != null) {
            trade = findTradeFrom(responder.getUniqueId(), fromPlayerId);
            if (trade == null) {
                responder.sendMessage("§cNo pending trade from that player.");
                return;
            }
        } else {
            trade = queue.get(0);
        }

        removeTrade(responder.getUniqueId(), trade);
        responder.sendMessage("§cTrade declined.");

        Player initiator = Bukkit.getPlayer(trade.initiatorId());
        if (initiator != null) {
            initiator.sendMessage("§e" + responder.getName() + " §cdeclined your trade.");
        }
        refreshOrClose(responder);
    }

    public static void cancel(Player initiator) {
        UUID responderId = findResponderForInitiator(initiator.getUniqueId());
        if (responderId == null) {
            initiator.sendMessage("§cYou have no pending outgoing trade to cancel.");
            return;
        }

        List<PendingTrade> queue = pendingTrades.get(responderId);
        if (queue != null) {
            queue.removeIf(t -> t.initiatorId().equals(initiator.getUniqueId()));
            if (queue.isEmpty()) pendingTrades.remove(responderId);
        }

        Player responder = Bukkit.getPlayer(responderId);
        if (responder != null) {
            responder.sendMessage("§e" + initiator.getName() + " §7cancelled their trade offer to you.");
        }
        initiator.sendMessage("§aTrade cancelled.");
    }

    public static void openQueueGui(Player player) {
        List<PendingTrade> queue = pendingTrades.get(player.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            player.sendMessage("§7You have no pending trades.");
            return;
        }
        List<TradeQueueGui.TradeRow> rows = new ArrayList<>();
        for (PendingTrade t : queue) {
            rows.add(new TradeQueueGui.TradeRow(
                    t.initiatorId(),
                    t.initiatorName(),
                    t.giverId().equals(t.initiatorId()),
                    tradeDescStatic(t.amount(), t.isExp(), t.all()),
                    t.expiresAt()
            ));
        }
        new TradeQueueGui(player, rows, ExpTradePaper.getInstance()).open();
    }

    private static void refreshOrClose(Player responder) {
        if (hasPendingTrades(responder.getUniqueId())) {
            openQueueGui(responder);
        } else {
            responder.closeInventory();
        }
    }

    public static void adminTransfer(Player from, Player to, int amount, boolean isExp, boolean all,
                                     org.bukkit.command.CommandSender source) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            source.sendMessage("§cCannot transfer to the same player.");
            return;
        }
        if (!validateGiver(from, amount, isExp, all, false)) {
            source.sendMessage("§c" + from.getName() + " does not have enough XP for that transfer.");
            return;
        }
        if (!(source instanceof Player admin)) {
            source.sendMessage("§cThis command must be run by a player.");
            return;
        }
        pendingAdminTransfers.put(admin.getUniqueId(),
                new PendingAdminTransfer(from.getUniqueId(), to.getUniqueId(), amount, isExp, all));
        String desc = tradeDescStatic(amount, isExp, all);
        String balanceInfo = buildBalanceInfo(from, to, amount, isExp, all);
        source.sendMessage("§eTransfer §e" + desc + " §efrom §e" + from.getName()
                + " §eto §e" + to.getName() + "§e.\n" + balanceInfo
                + "§7Type §e/exptrade admin confirm§7 to confirm or §e/exptrade admin cancel§7 to abort.");
    }

    public static void adminConfirm(org.bukkit.command.CommandSender source) {
        if (!(source instanceof Player admin)) {
            source.sendMessage("§cThis command must be run by a player.");
            return;
        }
        PendingAdminTransfer pending = pendingAdminTransfers.remove(admin.getUniqueId());
        if (pending == null) {
            source.sendMessage("§cYou have no pending admin transfer to confirm.");
            return;
        }
        Player from = Bukkit.getPlayer(pending.fromId());
        Player to   = Bukkit.getPlayer(pending.toId());
        if (from == null || to == null) {
            source.sendMessage("§cTransfer cancelled: a player involved is no longer online.");
            return;
        }
        if (!validateGiver(from, pending.amount(), pending.isExp(), pending.all(), false)) {
            source.sendMessage("§c" + from.getName() + " no longer has enough XP for that transfer.");
            return;
        }
        executeTrade(from, to, pending.amount(), pending.isExp(), pending.all());
        source.sendMessage("§aTransferred §e" + tradeDescStatic(pending.amount(), pending.isExp(), pending.all())
                + " §afrom §e" + from.getName() + " §ato §e" + to.getName() + "§a.");
    }

    public static void adminCancel(org.bukkit.command.CommandSender source) {
        if (!(source instanceof Player admin)) {
            source.sendMessage("§cThis command must be run by a player.");
            return;
        }
        if (pendingAdminTransfers.remove(admin.getUniqueId()) == null) {
            source.sendMessage("§cYou have no pending admin transfer to cancel.");
            return;
        }
        source.sendMessage("§aAdmin transfer cancelled.");
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    private static void tick() {
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
                    Player initiator = Bukkit.getPlayer(trade.initiatorId());
                    Player responder = Bukkit.getPlayer(trade.responderId());
                    String responderName = responder != null ? responder.getName() : "the player";
                    String initiatorName = trade.initiatorName();
                    if (initiator != null)
                        initiator.sendMessage("§cYour trade with §e" + responderName + " §chas expired.");
                    if (responder != null)
                        responder.sendMessage("§cThe trade from §e" + initiatorName + " §chas expired.");
                }
            }
            if (entry.getValue().isEmpty()) mapIter.remove();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String tradeDesc(Player giver, int amount, boolean isExp, boolean all) {
        if (all) return "all XP";
        if (isExp) return amount + " raw XP";
        return amount + " level(s) (" + rawXpCost(giver.getLevel(), amount) + " raw XP)";
    }

    private static String tradeDescStatic(int amount, boolean isExp, boolean all) {
        if (all) return "all XP";
        if (isExp) return amount + " raw XP";
        return amount + " level(s)";
    }

    private static String buildBalanceInfo(Player giver, Player receiver, int amount, boolean isExp, boolean all) {
        int giverLvlBefore = giver.getLevel();
        int giverXpBefore  = actualCurrentXp(giver);
        int recLvlBefore   = receiver.getLevel();
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

        return "§7" + giver.getName() + ": §e" + giverLvlBefore + " lvl §7(§e" + giverXpBefore + " xp§7)"
                + " §7→ §e" + giverLvlAfter + " lvl §7(§e" + giverXpAfter + " xp§7)\n"
                + "§7" + receiver.getName() + ": §e" + recLvlBefore + " lvl §7(§e" + recXpBefore + " xp§7)"
                + " §7→ §e~" + recLvlAfter + " lvl §7(§e~" + recXpAfter + " xp§7)\n";
    }
}
