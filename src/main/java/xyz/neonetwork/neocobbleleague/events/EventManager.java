package xyz.neonetwork.neocobbleleague.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import xyz.neonetwork.neocobbleleague.NeoConstants;
import xyz.neonetwork.neocobbleleague.common.utils.ConfigFile;
import xyz.neonetwork.neocobbleleague.events.enums.EventEndReason;
import xyz.neonetwork.neocobbleleague.events.enums.EventPhase;
import xyz.neonetwork.neocobbleleague.events.enums.EventStartResult;
import xyz.neonetwork.neocobbleleague.events.json.EventDefinitionJson;
import xyz.neonetwork.neocobbleleague.events.records.SpawnPoint;
import xyz.neonetwork.neocobbleleague.events.specs.TrainerSpec;
import xyz.neonetwork.neocobblelib.cobblemon.CobblemonTrainerManager;

import java.util.*;

public class EventManager {

    private static volatile Map<String, EventDefinition> definitions = Map.of();

    private static final Map<UUID, EventInstance> active = new LinkedHashMap<>();
    private static final Map<String, Long> cooldownEnds = new HashMap<>();

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

        EventInstance instance = new EventInstance(definition, spawnPoint.get(), server.overworld().getRandom());
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

    private static boolean advanceEvent(MinecraftServer server, EventInstance instance) {
        if (!instance.advance()) {
            return false;
        }

        EventDefinition definition = instance.getDefinition();

        switch (instance.getPhase()) {
            case ANNOUNCED -> broadcast(server, definition.getAnnounceMessage(), instance, "someone");
            case ACTIVE -> broadcast(server, definition.getSpawnMessage(), instance, "someone");
            default -> { }
        }

        return true;
    }

    private static boolean endEvent(MinecraftServer server, EventInstance instance, EventEndReason reason) {
        if (!instance.end(reason)) {
            return false;
        }

        active.remove(instance.getInstanceId());

        instance.getDefinition().getEndMessage(reason)
                .ifPresent(message -> broadcast(server, message, instance, "someone"));

        if (reason != EventEndReason.CANCELLED && reason != EventEndReason.SPAWN_FAILED && instance.getDefinition().getCooldownTicks() > 0) {
            cooldownEnds.put(instance.getDefinitionId(),
                    server.overworld().getGameTime() + instance.getDefinition().getCooldownTicks());
        }

        // TODO: Kill npc if remaining

        return true;
    }

    private static Optional<SpawnPoint> pickSpawnPoint(MinecraftServer server, EventDefinition definition, RandomSource random) {
        List<SpawnPoint> candidates = new ArrayList<>(definition.getSpawnPoints());
        Collections.shuffle(candidates, new Random(random.nextLong()));

        for (SpawnPoint point : candidates) {
            Optional<ServerLevel> level = SpawnChecks.resolveLevel(server, point);

            if (level.isPresent() && SpawnChecks.isUsable(level.get(), point)) {
                return Optional.of(point);
            }

            NeoConstants.LOGGER.warn("Event '{}': spawn point {} is not usable, trying another.", definition.getId(), point);
        }

        return Optional.empty();
    }

    public static void tick(MinecraftServer server) {
        if (active.isEmpty()) {
            return;
        }

        for (EventInstance instance : new ArrayList<>(active.values())) {
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
