package xyz.neonetwork.neocobbleleague.events;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import xyz.neonetwork.neocobbleleague.NeoConstants;
import xyz.neonetwork.neocobbleleague.common.utils.ConfigFile;
import xyz.neonetwork.neocobbleleague.events.enums.EventEndReason;
import xyz.neonetwork.neocobbleleague.events.enums.EventPhase;
import xyz.neonetwork.neocobbleleague.events.enums.EventStartResult;
import xyz.neonetwork.neocobbleleague.events.json.EventDefinitionJson;
import xyz.neonetwork.neocobbleleague.events.records.SpawnPoint;
import xyz.neonetwork.neocobbleleague.events.specs.PokemonSpec;
import xyz.neonetwork.neocobbleleague.events.specs.TrainerSpec;
import xyz.neonetwork.neocobblelib.cobblemon.CobblemonPresetTrainers;
import xyz.neonetwork.neocobblelib.cobblemon.CobblemonTrainerManager;
import xyz.neonetwork.neocobblelib.cobblemon.CobblemonUtils;
import xyz.neonetwork.neocobblelib.utils.NPCUtils;

import java.util.*;

public class EventManager {

    private static final String ENTITY_TAG_PREFIX = "neocobbleleague_event:";
    private static final Map<UUID, EventInstance> active = new LinkedHashMap<>();
    private static final Map<String, Long> cooldownEnds = new HashMap<>();
    private static final long DESPAWN_GRACE_PERIOD = 100;

    private static volatile Map<String, EventDefinition> definitions = Map.of();

    public static void loadDefinitions(ConfigFile file) {
        JsonObject root = file.getConfig();

        if (root == null) {
            NeoConstants.LOGGER.error("Events config is not loaded. Keeping previous definitions.");
            return;
        }

        JsonElement eventsElement = root.get("Events");

        if (eventsElement == null || !eventsElement.isJsonArray()) {
            NeoConstants.LOGGER.error("Events config has no events inside. Keeping previous definitions.");
            return;
        }

        JsonArray eventsArray = eventsElement.getAsJsonArray();
        Map<String, EventDefinition> loadedEvents = new LinkedHashMap<>();
        int skipped = 0;

        for (int i = 0; i < eventsArray.size(); i++) {
            JsonElement element = eventsArray.get(i);

            if (!element.isJsonObject()) {
                NeoConstants.LOGGER.warn("Events[{}] is not an object, skipping.", i);
                skipped++;
                continue;
            }

            Optional<EventDefinition> parsed = EventDefinitionJson.fromJson(element.getAsJsonObject(), i);

            if (parsed.isEmpty()) {
                skipped++;
                continue;
            }

            EventDefinition definition = parsed.get();

            if (loadedEvents.containsKey(definition.getId())) {
                NeoConstants.LOGGER.warn("Duplicate event id '{}', keeping the first.", definition.getId());
                skipped++;
                continue;
            }

            if (definition.getSpec() instanceof TrainerSpec trainerSpec
                && CobblemonTrainerManager.getTrainer(trainerSpec.trainerName()) == null) {
                NeoConstants.LOGGER.error("Event '{}' uses unknown trainer '{}', skipping.", definition.getId(), trainerSpec.trainerName());
                skipped++;
                continue;
            }

            loadedEvents.put(definition.getId(), definition);
        }

        definitions = Collections.unmodifiableMap(loadedEvents);

        NeoConstants.LOGGER.info("Loaded {} event definition(s), skipped {}.", loadedEvents.size(), skipped);
    }

    public static Optional<EventDefinition> getDefinition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public static Collection<EventDefinition> getDefinitions() {
        return definitions.values();
    }

    public static EventStartResult startEvent(MinecraftServer server, String definitionId, boolean forced) {
        EventDefinition definition = definitions.get(definitionId);

        if (definition == null) {
            return EventStartResult.UNKNOWN;
        }

        if (findActive(definitionId).isPresent()) {
            return EventStartResult.ALREADY_ACTIVE;
        }

        if (!forced) {
            if (!definition.isEnabled()) {
                return EventStartResult.DISABLED;
            }

            if (getCooldownRemaining(server, definitionId) > 0) {
                return EventStartResult.ON_COOLDOWN;
            }
        }

        RandomSource random = server.overworld().getRandom();
        Optional<SpawnPoint> spawnPoint = pickSpawnPoint(server, definition, random);

        if (spawnPoint.isEmpty()) {
            return EventStartResult.NO_VALID_SPAWN;
        }

        EventInstance instance = new EventInstance(definition, spawnPoint.get(), random);
        active.put(instance.getInstanceId(), instance);

        advanceEvent(server, instance);

        return EventStartResult.STARTED;
    }

    public static boolean stopEvent(MinecraftServer server, String definitionId) {
        return findActive(definitionId)
                .map(instance -> endEvent(server, instance, EventEndReason.CANCELLED))
                .orElse(false);
    }

    public static boolean skipCountdown(MinecraftServer server, String definitionId) {
        return findActive(definitionId)
                .filter(instance -> instance.getPhase() == EventPhase.ANNOUNCED)
                .map(instance -> advanceEvent(server, instance))
                .orElse(false);
    }

    public static Optional<EventInstance> findActive(String definitionId) {
        return active.values().stream()
                .filter(eventInstance -> eventInstance.getDefinitionId().equals(definitionId))
                .findFirst();
    }

    public static Collection<EventInstance> getActive() {
        return Collections.unmodifiableCollection(active.values());
    }

    public static long getCooldownRemaining(MinecraftServer server, String definitionId) {
        Long end = cooldownEnds.get(definitionId);

        if (end == null) {
            return 0;
        }

        long remaining = end - server.overworld().getGameTime();

        if (remaining <= 0) {
            cooldownEnds.remove(definitionId);
            return 0;
        }

        return remaining;
    }

    public static Optional<EventInstance> findByUuid(UUID entityUuid) {
        return active.values().stream()
                .filter(instance -> instance.matches(entityUuid))
                .findFirst();
    }

    public static boolean resolve(MinecraftServer server, UUID entityUuid, ServerPlayer player, EventEndReason reason) {
        return findByUuid(entityUuid)
                .map(instance -> endEvent(server, instance, reason, player))
                .orElse(false);
    }

    public static boolean isOrphan(Entity entity) {
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(ENTITY_TAG_PREFIX)) {
                continue;
            }

            try {
                UUID instanceId = UUID.fromString(tag.substring(ENTITY_TAG_PREFIX.length()));

                if (!active.containsKey(instanceId)) {
                    return true;
                }
            } catch (IllegalArgumentException exception) {
                return true;
            }
        }

        return false;
    }

    public static void cancelAll(MinecraftServer server) {
        for (EventInstance instance : new ArrayList<>(active.values())) {
            endEvent(server, instance, EventEndReason.CANCELLED);
        }
    }

    public static void tick(MinecraftServer server) {
        if (active.isEmpty()) {
            return;
        }

        for (EventInstance instance : new ArrayList<>(active.values())) {
            if (instance.getPhase() == EventPhase.ACTIVE && instance.getEntityGoneAt() >= 0
                    && server.overworld().getGameTime() - instance.getEntityGoneAt() >= DESPAWN_GRACE_PERIOD) {
                endEvent(server, instance, EventEndReason.DESPAWNED);
                continue;
            }

            if (!instance.tick()) {
                continue;
            }

            switch (instance.getPhase()) {
                case ANNOUNCED -> advanceEvent(server, instance);
                case ACTIVE -> endEvent(server, instance, EventEndReason.TIMED_OUT);
                default -> { }
            }
        }
    }

    public static void clear() {
        active.clear();
        cooldownEnds.clear();
    }

    public static void onEntityRemoved(UUID uuid, long gameTime) {
        findByUuid(uuid).ifPresent(instance -> instance.markEntityGone(gameTime));
    }

    public static void stopBattles(MinecraftServer server, EventInstance instance) {
        UUID npcUuid = instance.getEntityUuid().orElse(null);

        if (npcUuid == null) {
            return;
        }

        for (UUID playerUuid : instance.getParticipants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);

            if (player == null) {
                continue;
            }

            PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);

            if (battle == null) {
                continue;
            }

            for (BattleActor actor : battle.getActors()) {
                if (actor instanceof NPCBattleActor npcActor
                        && npcActor.getNpc().getUUID().equals(npcUuid)) {
                    battle.stop();
                    break;
                }
            }
        }
    }

    private static boolean advanceEvent(MinecraftServer server, EventInstance instance) {
        if (!instance.advance()) {
            return false;
        }

        EventDefinition definition = instance.getDefinition();

        switch (instance.getPhase()) {
            case ANNOUNCED -> broadcast(server, definition.getAnnounceMessage(), instance, "someone");
            case ACTIVE -> {
                if (!spawnEntity(server, instance)) {
                    endEvent(server, instance, EventEndReason.SPAWN_FAILED);
                    return true;
                }

                broadcast(server, definition.getSpawnMessage(), instance, "someone");
            }
            default -> { }
        }

        return true;
    }

    private static boolean endEvent(MinecraftServer server, EventInstance instance, EventEndReason reason) {
        return endEvent(server, instance, reason, null);
    }

    private static boolean endEvent(MinecraftServer server, EventInstance instance, EventEndReason reason, ServerPlayer winner) {
        if (!instance.end(reason)) {
            return false;
        }

        active.remove(instance.getInstanceId());

        String playerName = winner != null ? winner.getGameProfile().getName() : "someone";

        instance.getDefinition().getEndMessage(reason)
                .ifPresent(message -> broadcast(server, message, instance, playerName));

        if (reason != EventEndReason.CANCELLED && reason != EventEndReason.SPAWN_FAILED && instance.getDefinition().getCooldownTicks() > 0) {
            cooldownEnds.put(instance.getDefinitionId(),
                    server.overworld().getGameTime() + instance.getDefinition().getCooldownTicks());
        }

        if (reason != EventEndReason.CAPTURED && reason != EventEndReason.DEFEATED) {
            stopBattles(server, instance);
        }

        removeEntity(server, instance);

        if (winner != null) {
            runRewards(server, instance, reason, winner);
        }

        return true;
    }

    private static Optional<SpawnPoint> pickSpawnPoint(MinecraftServer server, EventDefinition definition, RandomSource random) {
        List<SpawnPoint> candidates = new ArrayList<>(definition.getSpawnPoints());
        Collections.shuffle(candidates, new Random(random.nextLong()));

        for (SpawnPoint point : candidates) {
            Optional<String> problem = SpawnChecks.findProblem(server, point);

            if (problem.isEmpty()) {
                return Optional.of(point);
            }

            NeoConstants.LOGGER.warn("Event '{}': spawn point {} is unusable ({}).", definition.getId(), point, problem.get());
        }

        return Optional.empty();
    }

    private static boolean spawnEntity(MinecraftServer server, EventInstance instance) {
        SpawnPoint point = instance.getSpawnPoint();
        Optional<String> problem = SpawnChecks.findProblem(server, point);

        if (problem.isPresent()) {
            NeoConstants.LOGGER.error("Event '{}': spawn point {} no longer usable ({}).",
                    instance.getDefinitionId(), point, problem.get());

            return false;
        }

        ServerLevel level = SpawnChecks.resolveLevel(server, point).orElseThrow();
        String tag = ENTITY_TAG_PREFIX + instance.getInstanceId();

        Optional<? extends Entity> spawned = switch (instance.getDefinition().getSpec()) {
            case PokemonSpec spec -> CobblemonUtils.spawnWildPokemon(level, spec.species(),
                    instance.getRolledLevel().getAsInt(), point.x(), point.y(), point.z(),
                    entity -> entity.addTag(tag));

            case TrainerSpec spec -> {
                CobblemonPresetTrainers trainer = CobblemonTrainerManager.getTrainer(spec.trainerName());

                if (trainer == null) {
                    NeoConstants.LOGGER.error("Event '{}': trainer '{}' not found.", instance.getDefinitionId(), spec.trainerName());
                    yield Optional.empty();
                }

                yield NPCUtils.spawnTrainer(level, trainer, point.x(), point.y(), point.z(), 0F, npc -> {
                    npc.addTag(tag);
                    npc.setInvulnerable(true);
                });
            }
        };

        spawned.ifPresent(entity -> {
            instance.setEntityUuid(entity.getUUID());

            if (entity instanceof PokemonEntity pokemonEntity) {
                instance.setPokemonUuid(pokemonEntity.getPokemon().getUuid());
            }
        });

        return spawned.isPresent();
    }

    private static void removeEntity(MinecraftServer server, EventInstance instance) {
        instance.getEntityUuid().ifPresent(uuid -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(uuid);

                if (entity != null) {
                    entity.discard();
                    return;
                }
            }
        });
    }

    private static void runRewards(MinecraftServer server, EventInstance instance, EventEndReason reason, ServerPlayer winner) {
        List<String> commands = instance.getDefinition().getRewards(reason);

        if (commands.isEmpty()) {
            return;
        }

        String playerName = winner.getGameProfile().getName();
        CommandSourceStack source = server.createCommandSourceStack();

        for (String template : commands) {
            String command = template.replace("{player}", playerName);

            try {
                server.getCommands().performPrefixedCommand(source, command);
            } catch (RuntimeException exception) {
                NeoConstants.LOGGER.error("Event '{}': reward command '{}' failed: {}",
                        instance.getDefinitionId(), command, exception.toString());
            }
        }
    }

    private static void broadcast(MinecraftServer server, String template, EventInstance instance, String playerName) {
        if (template == null || template.isBlank()) {
            return;
        }

        String text = template
                .replace("{name}", instance.getDefinition().getDisplayName())
                .replace("{player}", playerName)
                .replace('&', '§');

        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
