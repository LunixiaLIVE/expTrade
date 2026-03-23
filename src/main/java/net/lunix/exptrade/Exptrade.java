package net.lunix.exptrade;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Exptrade implements ModInitializer {

    public static final String MOD_ID = "exptrade";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        PlayerAttachments.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TradeCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(TradeManager::tick);
        LOGGER.info("expTrade initialized");
    }
}
