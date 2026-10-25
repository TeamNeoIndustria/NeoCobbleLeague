package xyz.neonetwork.neocobbleleague.events;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xyz.neonetwork.neocobbleleague.NeoConstants;
import xyz.neonetwork.neocobbleleague.events.enums.EventEndReason;
import xyz.neonetwork.neocobblelib.battles.events.TrainerBattleEndEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class EventListener {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EventManager.tick(event.getServer());
        EventScheduler.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EventManager.cancelAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EventManager.clear();
        EventScheduler.reset();
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
            EventManager.findByUuid(npc.getUUID())
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

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();
        Entity.RemovalReason reason = entity.getRemovalReason();

        if (reason == null || !reason.shouldDestroy()) {
            return;
        }

        EventManager.onEntityRemoved(entity.getUUID(), event.getLevel().getGameTime());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (EventManager.isOrphan(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onWildBattleVictory(BattleVictoryEvent event) {
        if (event.getWasWildCapture()) {
            return;
        }

        ServerPlayer winner = event.getWinners().stream()
                .filter(PlayerBattleActor.class::isInstance)
                .map(actor -> ((PlayerBattleActor) actor).getEntity())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (winner == null) {
            return;
        }

        for (BattleActor loser : event.getLosers()) {
            if (loser.getType() != ActorType.WILD) {
                continue;
            }

            List<UUID> candidates = new ArrayList<>();
            candidates.add(loser.getUuid());
            loser.getPokemonList().forEach(battlePokemon -> candidates.add(battlePokemon.getEffectedPokemon().getUuid()));

            for (UUID candidate : candidates) {
                if (EventManager.resolve(winner.server, candidate, winner, EventEndReason.DEFEATED)) {
                    return;
                }
            }
        }
    }

    public static void onPokemonCaptured(PokemonCapturedEvent event) {
        ServerPlayer player = event.getPlayer();

        EventManager.resolve(player.server, event.getPokemon().getUuid(), player, EventEndReason.CAPTURED);
    }

    private static boolean isEventNPC(NPCEntity npc) {
        return npc.getTags().stream().anyMatch(tag -> tag.startsWith("neocobbleleague_event:"));
    }
}
