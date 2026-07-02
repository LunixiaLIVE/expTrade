package net.lunix.exptrade.neoforge;

import net.lunix.exptrade.ExpTradeCommon;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(ExpTradeCommon.MOD_ID)
public class ExpTradeNeoForge {

    public ExpTradeNeoForge() {
        ExpTradeCommon.init(FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ExpTradeCommon.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ExpTradeCommon.registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        ExpTradeCommon.onServerTick(event.getServer());
    }
}
