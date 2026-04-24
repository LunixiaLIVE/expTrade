package net.lunix.exptrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

public class TradeQueueGui implements InventoryHolder {

    public record TradeRow(UUID initiatorId, String initiatorName, boolean isOffer,
                           String description, long expiresAt) {}

    private final Inventory inventory;
    private final List<TradeRow> trades;
    private final Player viewer;
    private final int rows;
    private BukkitTask updateTask;

    public TradeQueueGui(Player viewer, List<TradeRow> trades, Plugin plugin) {
        this.viewer = viewer;
        this.trades = trades;
        this.rows   = Math.min(Math.max(trades.size(), 1), 6);
        this.inventory = Bukkit.createInventory(this, rows * 9,
                "§8Trade Queue (" + trades.size() + "/" + ModConfig.get().maxQueueSize + ")");
        populate();
        scheduleUpdates(plugin);
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public List<TradeRow> getTrades() { return trades; }

    public void open() { viewer.openInventory(inventory); }

    public void cancelUpdates() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
    }

    private void scheduleUpdates(Plugin plugin) {
        updateTask = new BukkitRunnable() {
            @Override public void run() {
                if (!viewer.isOnline()
                        || !(viewer.getOpenInventory().getTopInventory().getHolder() instanceof TradeQueueGui)) {
                    cancel();
                    return;
                }
                updateClocks();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void updateClocks() {
        for (int r = 0; r < Math.min(trades.size(), rows); r++) {
            long remainingSecs = Math.max(0, (trades.get(r).expiresAt() - System.currentTimeMillis()) / 1000);
            ItemStack clock = new ItemStack(Material.CLOCK);
            ItemMeta meta = clock.getItemMeta();
            meta.setDisplayName("§e" + remainingSecs + "s remaining");
            clock.setItemMeta(meta);
            inventory.setItem(r * 9, clock);
        }
    }

    private void populate() {
        for (int r = 0; r < rows; r++) {
            if (r >= trades.size()) continue;
            TradeRow trade = trades.get(r);

            // Slot 0: Clock — TTL countdown
            long remainingSecs = Math.max(0, (trade.expiresAt() - System.currentTimeMillis()) / 1000);
            ItemStack clock = new ItemStack(Material.CLOCK);
            ItemMeta clockMeta = clock.getItemMeta();
            clockMeta.setDisplayName("§e" + remainingSecs + "s remaining");
            clock.setItemMeta(clockMeta);
            inventory.setItem(r * 9, clock);

            // Slot 1: Player head
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(trade.initiatorId()));
            skullMeta.setDisplayName("§e" + trade.initiatorName());
            head.setItemMeta(skullMeta);
            inventory.setItem(r * 9 + 1, head);

            // Slot 2: Bucket — empty = request, water = offer
            ItemStack bucket = trade.isOffer()
                    ? new ItemStack(Material.WATER_BUCKET)
                    : new ItemStack(Material.BUCKET);
            ItemMeta bucketMeta = bucket.getItemMeta();
            bucketMeta.setDisplayName(trade.isOffer() ? "§bOffer" : "§9Request");
            bucket.setItemMeta(bucketMeta);
            inventory.setItem(r * 9 + 2, bucket);

            // Slot 3: Paper — trade details
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta paperMeta = paper.getItemMeta();
            paperMeta.setDisplayName("§eTrade Details");
            paperMeta.setLore(List.of(
                    "§7From: §e" + trade.initiatorName(),
                    "§7Type: §e" + (trade.isOffer() ? "Offer" : "Request"),
                    "§7Amount: §f" + trade.description()
            ));
            paper.setItemMeta(paperMeta);
            inventory.setItem(r * 9 + 3, paper);

            // Slots 4–6: intentionally empty

            // Slot 7: Recovery compass — accept
            ItemStack accept = new ItemStack(Material.RECOVERY_COMPASS);
            ItemMeta acceptMeta = accept.getItemMeta();
            acceptMeta.setDisplayName("§a§lAccept");
            accept.setItemMeta(acceptMeta);
            inventory.setItem(r * 9 + 7, accept);

            // Slot 8: Compass — decline
            ItemStack decline = new ItemStack(Material.COMPASS);
            ItemMeta declineMeta = decline.getItemMeta();
            declineMeta.setDisplayName("§c§lDecline");
            decline.setItemMeta(declineMeta);
            inventory.setItem(r * 9 + 8, decline);
        }
    }
}
