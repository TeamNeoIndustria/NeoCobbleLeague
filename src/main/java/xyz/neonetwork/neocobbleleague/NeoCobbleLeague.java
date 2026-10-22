package xyz.neonetwork.neocobbleleague;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import xyz.neonetwork.neocobbleleague.common.commands.MainCommand;
import xyz.neonetwork.neocobbleleague.common.utils.ConfigFile;
import xyz.neonetwork.neocobbleleague.events.commands.EventCommand;

@Mod(NeoConstants.MODID)
public class NeoCobbleLeague {
    public NeoCobbleLeague(IEventBus eventBus, ModContainer modContainer) {
        // Registering
        NeoForge.EVENT_BUS.register(this);

        // Configs
        NeoConstants.EVENTS_CONFIG = new ConfigFile("events.json");

        // Loading
        NeoConstants.EVENTS_CONFIG.loadConfig();
    }

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        // Main Command
        MainCommand.register(event.getDispatcher());

        // Event Commands
        EventCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // load Events
    }
}
