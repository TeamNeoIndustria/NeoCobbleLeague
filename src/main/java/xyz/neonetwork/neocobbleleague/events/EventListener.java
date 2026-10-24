package xyz.neonetwork.neocobbleleague.events;

import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xyz.neonetwork.neocobblelib.NeoConstants;
import xyz.neonetwork.neocobblelib.battles.events.TrainerBattleEndEvent;

public class EventListener {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EventManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EventManager.cancelAll(event.getServer());
        EventManager.clear();
    }

    @SubscribeEvent
    public static void onNPCInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof NPCEntity npc)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isEventNPC(npc)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        BattleBuilder.INSTANCE.pvn(player, npc).ifErrored(error -> {
            NeoConstants.LOGGER.error("Could not start trainer battle for {}: {}",
                    player.getGameProfile().getName(), error.getErrors());
            return null;
        });
    }

    @SubscribeEvent
    public static void onBattleFinish(TrainerBattleEndEvent event) {
        NeoConstants.LOGGER.info("Battle Finished. {} {}", event.getOutcome(), event.getPlayer().getDisplayName().getString());
        event.getPlayer().sendSystemMessage(Component.literal("Battled Finished"));

        NPCEntity npcEntity = event.getNpc();

        if (isEventNPC(npcEntity) && npcEntity.getParty() != null) {
            npcEntity.getParty().forEach(Pokemon::heal);
        }
    }

    private static boolean isEventNPC(NPCEntity npc) {
        return npc.getTags().stream().anyMatch(tag -> tag.startsWith("neocobbleleague_event:"));
    }
}
