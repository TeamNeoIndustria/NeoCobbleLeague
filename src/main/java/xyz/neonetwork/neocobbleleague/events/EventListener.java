package xyz.neonetwork.neocobbleleague.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class EventListener {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EventManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EventManager.clear();
    }
}
