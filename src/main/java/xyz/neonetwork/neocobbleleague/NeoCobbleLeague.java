package xyz.neonetwork.neocobbleleague;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import xyz.neonetwork.neocobbleleague.common.commands.MainCommand;
import xyz.neonetwork.neocobbleleague.common.utils.ConfigFile;
import xyz.neonetwork.neocobbleleague.events.EventDefinition;
import xyz.neonetwork.neocobbleleague.events.EventListener;
import xyz.neonetwork.neocobbleleague.events.EventManager;
import xyz.neonetwork.neocobbleleague.events.commands.EventCommand;

@Mod(NeoConstants.MODID)
public class NeoCobbleLeague {
    public NeoCobbleLeague(IEventBus eventBus, ModContainer modContainer) {
        // Registering
        NeoForge.EVENT_BUS.register(this);

        // Configs
        NeoConstants.EVENTS_CONFIG = new ConfigFile("events.json");

        // Loading
        boolean success = NeoConstants.EVENTS_CONFIG.loadConfig();

        if (!success) {
            NeoConstants.LOGGER.error("Error loading Events Config.");
        }

        // Events
        NeoForge.EVENT_BUS.register(EventListener.class);

        // Cobblemon Events
        CobblemonEvents.BATTLE_VICTORY.subscribe(EventListener::onWildBattleVictory);
        CobblemonEvents.POKEMON_CAPTURED.subscribe(EventListener::onPokemonCaptured);
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
        EventManager.loadDefinitions(NeoConstants.EVENTS_CONFIG);

        for (EventDefinition definition : EventManager.getDefinitions()) {
            NeoConstants.LOGGER.info("[CHECKING] Definition in map: {}", definition.getId());
        }
    }
}
