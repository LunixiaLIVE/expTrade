package net.lunix.exptrade;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ExpTradeCommon {

    public static final String MOD_ID = "exptrade";
    public static final Logger LOGGER = LoggerFactory.getLogger("expTrade");

    /** Loader config directory, supplied by the platform entrypoint at mod construction. */
    public static Path CONFIG_DIR;

    /** Called once by each platform entrypoint with the loader's config directory. */
    public static void init(Path configDir) {
        CONFIG_DIR = configDir;
        LOGGER.info("expTrade initialized");
    }

    // ── Handlers invoked by platform-native event wiring ──

    public static void onServerStarted(MinecraftServer server) {
        ModConfig.load();
        PlayerDataStore.load();
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        TradeCommands.register(dispatcher);
    }

    public static void onServerTick(MinecraftServer server) {
        TradeManager.tick(server);
    }
}
