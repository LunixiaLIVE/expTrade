package net.lunix.exptrade;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TradeCommands implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendUsage(sender); return true; }

        String sub = args[0].toLowerCase();

        switch (sub) {

            case "give" -> {
                // /exptrade give <player> levels|exp <amount>|all
                Player player = requirePlayer(sender); if (player == null) return true;
                if (args.length < 4) { sender.sendMessage("§cUsage: /exptrade give <player> <levels|exp> <amount|all>"); return true; }
                Player target = requireOnlinePlayer(sender, args[1]); if (target == null) return true;
                parseAndOffer(player, target, args[2], args[3]);
            }

            case "request" -> {
                // /exptrade request <player> levels|exp <amount>|all
                Player player = requirePlayer(sender); if (player == null) return true;
                if (args.length < 4) { sender.sendMessage("§cUsage: /exptrade request <player> <levels|exp> <amount|all>"); return true; }
                Player target = requireOnlinePlayer(sender, args[1]); if (target == null) return true;
                parseAndRequest(player, target, args[2], args[3]);
            }

            case "accept" -> {
                Player player = requirePlayer(sender); if (player == null) return true;
                if (args.length >= 2) {
                    Player from = requireOnlinePlayer(sender, args[1]); if (from == null) return true;
                    TradeManager.accept(player, from.getUniqueId());
                } else {
                    TradeManager.accept(player, null);
                }
            }

            case "decline" -> {
                Player player = requirePlayer(sender); if (player == null) return true;
                if (args.length >= 2) {
                    Player from = requireOnlinePlayer(sender, args[1]); if (from == null) return true;
                    TradeManager.decline(player, from.getUniqueId());
                } else {
                    TradeManager.decline(player, null);
                }
            }

            case "cancel" -> {
                Player player = requirePlayer(sender); if (player == null) return true;
                TradeManager.cancel(player);
            }

            case "pending" -> {
                Player player = requirePlayer(sender); if (player == null) return true;
                TradeManager.openQueueGui(player);
            }

            case "threshold" -> {
                Player player = requirePlayer(sender); if (player == null) return true;
                if (args.length < 2) { sender.sendMessage("§cUsage: /exptrade threshold <levels>"); return true; }
                int threshold = parseInt(sender, args[1], 0, Integer.MAX_VALUE); if (threshold < 0) return true;
                PlayerDataStore.setThreshold(player.getUniqueId(), threshold);
                player.sendMessage("§aYour XP trade threshold set to §e" + threshold + " §alevels.");
            }

            case "admin" -> {
                if (!sender.hasPermission("exptrade.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length < 2) { sendAdminUsage(sender); return true; }
                handleAdmin(sender, args);
            }

            case "config" -> {
                if (!sender.hasPermission("exptrade.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length < 2) { sendConfigUsage(sender); return true; }
                handleConfig(sender, args);
            }

            default -> sendUsage(sender);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Sub-handlers
    // -------------------------------------------------------------------------

    private void parseAndOffer(Player giver, Player target, String typeArg, String amountArg) {
        boolean isExp = parseType(giver, typeArg); if (isExp && typeArg.equalsIgnoreCase("invalid")) return;
        if (amountArg.equalsIgnoreCase("all")) {
            TradeManager.offer(giver, target, 0, isExp, true);
        } else {
            int amount = parseInt(giver, amountArg, 1, Integer.MAX_VALUE); if (amount < 0) return;
            TradeManager.offer(giver, target, amount, isExp, false);
        }
    }

    private void parseAndRequest(Player requester, Player target, String typeArg, String amountArg) {
        boolean isExp = parseType(requester, typeArg); if (isExp && typeArg.equalsIgnoreCase("invalid")) return;
        if (amountArg.equalsIgnoreCase("all")) {
            TradeManager.request(requester, target, 0, isExp, true);
        } else {
            int amount = parseInt(requester, amountArg, 1, Integer.MAX_VALUE); if (amount < 0) return;
            TradeManager.request(requester, target, amount, isExp, false);
        }
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        switch (args[1].toLowerCase()) {
            case "transfer" -> {
                // /exptrade admin transfer <from> <to> levels|exp <amount>|all
                if (args.length < 6) { sendAdminUsage(sender); return; }
                Player from = requireOnlinePlayer(sender, args[2]); if (from == null) return;
                Player to   = requireOnlinePlayer(sender, args[3]); if (to == null) return;
                boolean isExp = args[4].equalsIgnoreCase("exp");
                if (args[5].equalsIgnoreCase("all")) {
                    TradeManager.adminTransfer(from, to, 0, isExp, true, sender);
                } else {
                    int amount = parseInt(sender, args[5], 1, Integer.MAX_VALUE); if (amount < 0) return;
                    TradeManager.adminTransfer(from, to, amount, isExp, false, sender);
                }
            }
            case "confirm" -> TradeManager.adminConfirm(sender);
            case "cancel"  -> TradeManager.adminCancel(sender);
            default        -> sendAdminUsage(sender);
        }
    }

    private void handleConfig(CommandSender sender, String[] args) {
        switch (args[1].toLowerCase()) {
            case "timeout" -> {
                if (args.length < 3) { sendConfigUsage(sender); return; }
                int seconds = parseInt(sender, args[2], 10, 300); if (seconds < 0) return;
                ModConfig.get().timeoutSeconds = seconds;
                ModConfig.save();
                sender.sendMessage("§aTrade timeout set to §e" + seconds + " §aseconds.");
            }
            case "maxqueue" -> {
                if (args.length < 3) { sendConfigUsage(sender); return; }
                int size = parseInt(sender, args[2], 1, 50); if (size < 0) return;
                ModConfig.get().maxQueueSize = size;
                ModConfig.save();
                sender.sendMessage("§aMax queue size set to §e" + size + "§a.");
            }
            case "reload" -> {
                ModConfig.load(ExpTradePaper.getInstance().getDataFolder());
                sender.sendMessage("§aexpTrade config reloaded.");
            }
            default -> sendConfigUsage(sender);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cThis command can only be run by a player."); return null; }
        return p;
    }

    private Player requireOnlinePlayer(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) { sender.sendMessage("§cPlayer '§e" + name + "§c' is not online."); return null; }
        return target;
    }

    /** Returns the parsed int, or -1 on failure (and sends an error). Min of 0 means no lower bound check. */
    private int parseInt(CommandSender sender, String s, int min, int max) {
        try {
            int v = Integer.parseInt(s);
            if (v < min || v > max) { sender.sendMessage("§cValue must be between §e" + min + "§c and §e" + max + "§c."); return -1; }
            return v;
        } catch (NumberFormatException e) {
            sender.sendMessage("§c'§e" + s + "§c' is not a valid number.");
            return -1;
        }
    }

    /** Returns true for exp, false for levels. Sends error and returns true with typeArg="invalid" to signal failure. */
    private boolean parseType(CommandSender sender, String typeArg) {
        if (typeArg.equalsIgnoreCase("levels")) return false;
        if (typeArg.equalsIgnoreCase("exp"))    return true;
        sender.sendMessage("§cType must be §elevels§c or §eexp§c.");
        return true; // caller checks typeArg.equalsIgnoreCase("invalid") — handled via the "invalid" sentinel trick
    }

    private void sendUsage(CommandSender s) {
        s.sendMessage("§eexpTrade commands:");
        s.sendMessage("§7/exptrade give <player> <levels|exp> <amount|all>");
        s.sendMessage("§7/exptrade request <player> <levels|exp> <amount|all>");
        s.sendMessage("§7/exptrade accept [player]");
        s.sendMessage("§7/exptrade decline [player]");
        s.sendMessage("§7/exptrade cancel");
        s.sendMessage("§7/exptrade pending");
        s.sendMessage("§7/exptrade threshold <levels>");
    }

    private void sendAdminUsage(CommandSender s) {
        s.sendMessage("§eAdmin commands:");
        s.sendMessage("§7/exptrade admin transfer <from> <to> <levels|exp> <amount|all>");
        s.sendMessage("§7/exptrade admin confirm");
        s.sendMessage("§7/exptrade admin cancel");
    }

    private void sendConfigUsage(CommandSender s) {
        s.sendMessage("§eConfig commands:");
        s.sendMessage("§7/exptrade config timeout <10-300>");
        s.sendMessage("§7/exptrade config maxqueue <1-50>");
        s.sendMessage("§7/exptrade config reload");
    }
}
