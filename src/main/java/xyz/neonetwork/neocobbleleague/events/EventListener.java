package xyz.neonetwork.neocobbleleague.events;

import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xyz.neonetwork.neocobbleleague.NeoConstants;
import xyz.neonetwork.neocobbleleague.events.enums.EventEndReason;
import xyz.neonetwork.neocobblelib.battles.events.TrainerBattleEndEvent;

public class EventListener {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EventManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EventManager.cancelAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EventManager.clear();
    }

    @SubscribeEvent
    public static void onNPCInteract(PlayerInteractEvent.EntityInteract event) {
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

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        BattleStartResult result = BattleBuilder.INSTANCE.pvn(player, npc);

        result.ifSuccessful(battle -> {
            EventManager.findByEntity(npc.getUUID())
                    .ifPresent(instance -> instance.addParticipant(player.getUUID()));
            return null;
        });

        result.ifErrored(error -> {
            NeoConstants.LOGGER.error("Could not start trainer battle for {}: {}",
                    player.getGameProfile().getName(), error.getErrors());
            return null;
        });
    }

    @SubscribeEvent
    public static void onBattleFinish(TrainerBattleEndEvent event) {
        NPCEntity npcEntity = event.getNpc();

        if (!isEventNPC(npcEntity)) {
            return;
        }

        ServerPlayer player = event.getPlayer();

        if (event.getOutcome() == TrainerBattleEndEvent.Outcome.PLAYER_WON) {
            EventManager.resolve(player.server, npcEntity.getUUID(), player, EventEndReason.DEFEATED);
        } else if (npcEntity.getParty() != null) {
            npcEntity.getParty().forEach(Pokemon::heal);
        }
    }

    private static boolean isEventNPC(NPCEntity npc) {
        return npc.getTags().stream().anyMatch(tag -> tag.startsWith("neocobbleleague_event:"));
    }
}
