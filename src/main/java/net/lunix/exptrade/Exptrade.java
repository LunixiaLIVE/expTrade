package net.lunix.exptrade;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Exptrade implements ModInitializer {

    public static final String MOD_ID = "exptrade";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        PlayerDataStore.load();

        // Migrate legacy NBT threshold data to PlayerDataStore on player join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            int legacyThreshold = player.getAttachedOrElse(PlayerAttachments.THRESHOLD, 0);
            if (legacyThreshold != 0 && PlayerDataStore.getThreshold(player.getUUID()) == 0) {
                LOGGER.info("Migrating legacy threshold ({}) for player {}", legacyThreshold, player.getName().getString());
                PlayerDataStore.setThreshold(player.getUUID(), legacyThreshold);
            }
            // Remove the attachment entirely so no legacy data remains in NBT
            player.setAttached(PlayerAttachments.THRESHOLD, null);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TradeCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(TradeManager::tick);
        LOGGER.info("expTrade initialized");
    }
}
