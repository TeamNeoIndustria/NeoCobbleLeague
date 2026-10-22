package xyz.neonetwork.neocobbleleague;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import xyz.neonetwork.neocobbleleague.events.commands.EventCommand;
import xyz.neonetwork.neocobbleleague.events.json.EventDefinitionJson;

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

        // Testing fromJson function (works)
        for (JsonElement jsonElement : NeoConstants.EVENTS_CONFIG.getConfig().getAsJsonArray("Events")) {
            NeoConstants.LOGGER.info(jsonElement.toString());
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            EventDefinitionJson.fromJson(jsonObject, 0).ifPresent(definition -> {
                NeoConstants.LOGGER.info("Loaded definition: {} {}", definition.getId(), definition.getDisplayName());
            });
        }
    }
}
