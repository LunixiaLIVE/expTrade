package net.lunix.exptrade;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;

public class TradeQueueMenu extends AbstractContainerMenu {

    public record TradeRow(GameProfile initiatorProfile, boolean isOffer,
                           String description, long expiresAt) {
        public java.util.UUID initiatorId() { return initiatorProfile.id(); }
        public String initiatorName() { return initiatorProfile.name(); }
    }

    private static MenuType<?> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private final List<TradeRow> trades;
    private final int rows;
    private final SimpleContainer container;
    private int tickCount = 0;

    public TradeQueueMenu(int containerId, Inventory playerInventory, List<TradeRow> trades) {
        super(menuType(Math.min(Math.max(trades.size(), 1), 6)), containerId);
        this.trades = trades;
        this.rows = Math.min(Math.max(trades.size(), 1), 6);

        this.container = new SimpleContainer(rows * 9);
        populate(container);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                final int index = row * 9 + col;
                addSlot(new Slot(container, index, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                });
            }
        }

        // Player inventory rows
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, rows * 18 + 13 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, rows * 18 + 71));
        }
    }

    private void populate(SimpleContainer container) {
        for (int r = 0; r < rows; r++) {
            if (r >= trades.size()) continue;
            TradeRow trade = trades.get(r);

            // Slot 0: Clock — TTL countdown in seconds
            long remainingSecs = Math.max(0, (trade.expiresAt() - System.currentTimeMillis()) / 1000);
            ItemStack clock = new ItemStack(Items.CLOCK);
            clock.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§e" + remainingSecs + "s remaining").withStyle(s -> s.withItalic(false)));
            container.setItem(r * 9, clock);

            // Slot 1: Player head of the initiator
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(trade.initiatorProfile()));
            head.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§e" + trade.initiatorName()).withStyle(s -> s.withItalic(false)));
            container.setItem(r * 9 + 1, head);

            // Slot 2: Bucket — empty bucket = request, water bucket = offer
            ItemStack bucket = trade.isOffer()
                    ? new ItemStack(Items.WATER_BUCKET)
                    : new ItemStack(Items.BUCKET);
            bucket.set(DataComponents.CUSTOM_NAME,
                    Component.literal(trade.isOffer() ? "§bOffer" : "§9Request").withStyle(s -> s.withItalic(false)));
            container.setItem(r * 9 + 2, bucket);

            // Slot 3: Paper — trade details
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§eTrade Details").withStyle(s -> s.withItalic(false)));
            paper.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("§7From: §e" + trade.initiatorName()).withStyle(s -> s.withItalic(false)),
                    Component.literal("§7Type: §e" + (trade.isOffer() ? "Offer" : "Request")).withStyle(s -> s.withItalic(false)),
                    Component.literal("§7Amount: §f" + trade.description()).withStyle(s -> s.withItalic(false))
            )));
            container.setItem(r * 9 + 3, paper);

            // Slots 4–6: intentionally empty

            // Slot 7: Recovery compass — accept
            ItemStack accept = new ItemStack(Items.RECOVERY_COMPASS);
            accept.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§a§lAccept").withStyle(s -> s.withItalic(false)));
            container.setItem(r * 9 + 7, accept);

            // Slot 8: Compass — decline
            ItemStack decline = new ItemStack(Items.COMPASS);
            decline.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§c§lDecline").withStyle(s -> s.withItalic(false)));
            container.setItem(r * 9 + 8, decline);
        }
    }

    @Override
    public void broadcastChanges() {
        if (++tickCount % 20 == 0) {
            for (int r = 0; r < Math.min(trades.size(), rows); r++) {
                long remainingSecs = Math.max(0, (trades.get(r).expiresAt() - System.currentTimeMillis()) / 1000);
                ItemStack clock = new ItemStack(Items.CLOCK);
                clock.set(DataComponents.CUSTOM_NAME,
                        Component.literal("§e" + remainingSecs + "s remaining").withStyle(s -> s.withItalic(false)));
                container.setItem(r * 9, clock);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (slotId < 0 || slotId >= rows * 9) return;

        int row = slotId / 9;
        int col = slotId % 9;
        if (row >= trades.size()) return;

        MinecraftServer server = sp.level().getServer();
        if (server == null) return;

        if (col == 7) {
            TradeManager.accept(sp, trades.get(row).initiatorId(), server);
            refreshOrClose(sp, server);
        } else if (col == 8) {
            TradeManager.decline(sp, trades.get(row).initiatorId(), server);
            refreshOrClose(sp, server);
        }
        // All other slots: intentionally blocked
    }

    private static void refreshOrClose(ServerPlayer sp, MinecraftServer server) {
        if (TradeManager.hasPendingTrades(sp.getUUID())) {
            TradeManager.openQueueGui(sp, server);
        } else {
            sp.closeContainer();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
