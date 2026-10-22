package xyz.neonetwork.neocobbleleague;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import xyz.neonetwork.neocobbleleague.common.commands.MainCommand;
import xyz.neonetwork.neocobbleleague.events.commands.EventCommand;

@Mod(NeoConstants.MODID)
public class NeoCobbleLeague {
    public NeoCobbleLeague(IEventBus eventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        // Main Command
        MainCommand.register(event.getDispatcher());

        // Event Commands
        EventCommand.register(event.getDispatcher());
    }
}
