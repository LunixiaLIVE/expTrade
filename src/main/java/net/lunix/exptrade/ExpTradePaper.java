package net.lunix.exptrade;

import org.bukkit.plugin.java.JavaPlugin;

public class ExpTradePaper extends JavaPlugin {

    private static ExpTradePaper instance;

    public static ExpTradePaper getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        ModConfig.load(getDataFolder());
        PlayerDataStore.load(getDataFolder());
        TradeManager.startTicking(this);
        getCommand("exptrade").setExecutor(new TradeCommands());
        getServer().getPluginManager().registerEvents(new TradeQueueListener(), this);
        getLogger().info("expTrade enabled.");
    }

    @Override
    public void onDisable() {
        TradeManager.stopTicking();
        PlayerDataStore.save();
        getLogger().info("expTrade disabled.");
    }
}
