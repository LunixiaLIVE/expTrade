package net.lunix.exptrade;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

public class TradeQueueListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeQueueGui gui)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        int guiSize = gui.getInventory().getSize();
        if (slot < 0 || slot >= guiSize) return;

        int row = slot / 9;
        int col = slot % 9;
        List<TradeQueueGui.TradeRow> trades = gui.getTrades();
        if (row >= trades.size()) return;

        if (col == 7) {
            TradeManager.accept(player, trades.get(row).initiatorId());
        } else if (col == 8) {
            TradeManager.decline(player, trades.get(row).initiatorId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TradeQueueGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TradeQueueGui gui) {
            gui.cancelUpdates();
        }
    }
}
