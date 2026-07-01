package net.lunix.exptrade.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.lunix.exptrade.ExpTradeCommon;
import net.lunix.exptrade.PlayerDataStore;
import net.minecraft.server.level.ServerPlayer;

public class ExpTradeFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ExpTradeCommon.init(FabricLoader.getInstance().getConfigDir());

        ServerLifecycleEvents.SERVER_STARTED.register(ExpTradeCommon::onServerStarted);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ExpTradeCommon.registerCommands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(ExpTradeCommon::onServerTick);

        // Legacy NBT threshold migration — reads old attachment data and moves to PlayerDataStore
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            int legacyThreshold = player.getAttachedOrElse(PlayerAttachments.THRESHOLD, 0);
            if (legacyThreshold != 0 && PlayerDataStore.getThreshold(player.getUUID()) == 0) {
                ExpTradeCommon.LOGGER.info("Migrating legacy threshold ({}) for player {}",
                        legacyThreshold, player.getName().getString());
                PlayerDataStore.setThreshold(player.getUUID(), legacyThreshold);
            }
            player.setAttached(PlayerAttachments.THRESHOLD, null);
        });
    }
}
