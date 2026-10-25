package xyz.neonetwork.neocobbleleague.events.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import xyz.neonetwork.neocobbleleague.NeoConstants;
import xyz.neonetwork.neocobbleleague.events.EventDefinition;
import xyz.neonetwork.neocobbleleague.events.enums.EventEndReason;
import xyz.neonetwork.neocobbleleague.events.enums.EventType;
import xyz.neonetwork.neocobbleleague.events.enums.SpawnMode;
import xyz.neonetwork.neocobbleleague.events.records.LevelRange;
import xyz.neonetwork.neocobbleleague.events.records.SpawnPoint;
import xyz.neonetwork.neocobbleleague.events.specs.EventSpec;
import xyz.neonetwork.neocobbleleague.events.specs.PokemonSpec;
import xyz.neonetwork.neocobbleleague.events.specs.TrainerSpec;
import xyz.neonetwork.neocobblelib.cobblemon.CobblemonSpecies;

import java.util.*;
import java.util.stream.Collectors;

public final class EventDefinitionJson {
    public static Optional<EventDefinition> fromJson(JsonObject json, int index) {
        String label = "#" + index;

        try {
            String id = requireString(json, "Id");
            label = id;
            String displayName = optString(json, "DisplayName", id);

            boolean enabled = optBool(json, "Enabled", false);
            EventType type = parseEnum(EventType.class, requireString(json, "Type"), "Type");

            int countdownTicks = readTicks(json, "CountdownSeconds", 120);
            int lifetimeTicks = readTicks(json, "LifetimeSeconds", 900);
            int cooldownTicks = readTicks(json, "CooldownSeconds", 3600);
            int weight = optInt(json, "Weight", 1);

            EventSpec spec = parseSpec(type, json, id);
            SpawnMode spawnMode = parseEnum(SpawnMode.class, optString(json, "SpawnMode", "FIXED"), "SpawnMode");

            if (spawnMode != SpawnMode.FIXED) {
                throw new IllegalArgumentException("SpawnMode " + spawnMode + " is not supported yet");
            }

            List<SpawnPoint> spawnPoints = parseSpawnPoints(json);

            JsonObject messages = has(json, "Messages") ? requireObject(json, "Messages") : new JsonObject();
            String announceMessage = optString(messages, "Announce", "");
            String spawnMessage = optString(messages, "Spawn", "");

            Map<EventEndReason, String> endMessages = parseEndMessages(messages, id);
            Map<EventEndReason, List<String>> rewards = parseRewards(json, id);

            return Optional.of(new EventDefinition(
                    id, displayName, enabled, type,
                    countdownTicks, lifetimeTicks, cooldownTicks, weight,
                    spec, spawnMode, spawnPoints,
                    announceMessage, spawnMessage, endMessages, rewards));
        } catch (IllegalArgumentException exception) {
            NeoConstants.LOGGER.error("Skipping event {}: {}", label, exception.getMessage());
        } catch (RuntimeException exception) {
            NeoConstants.LOGGER.error("Skipping event {}: unexpected {}", label, exception.toString());
        }

        return Optional.empty();
    }

    public static JsonObject toJson(EventDefinition definition) {
        JsonObject json = new JsonObject();

        json.addProperty("Id", definition.getId());
        json.addProperty("DisplayName", definition.getDisplayName());
        json.addProperty("Enabled", definition.isEnabled());
        json.addProperty("Type", definition.getType().name());

        json.addProperty("CountdownSeconds", ticksToSeconds(definition.getCountdownTicks()));
        json.addProperty("LifetimeSeconds", ticksToSeconds(definition.getLifetimeTicks()));
        json.addProperty("CooldownSeconds", ticksToSeconds(definition.getCooldownTicks()));
        json.addProperty("Weight", definition.getWeight());

        switch (definition.getSpec()) {
            case PokemonSpec pokemon -> {
                json.addProperty("Species", pokemon.species().getName());

                JsonArray levelRange = new JsonArray();
                levelRange.add(pokemon.levelRange().min());
                levelRange.add(pokemon.levelRange().max());
                json.add("LevelRange", levelRange);
            }
            case TrainerSpec trainer -> json.addProperty("TrainerName", trainer.trainerName());
        }

        json.addProperty("SpawnMode", definition.getSpawnMode().name());

        JsonArray spawnPoints = new JsonArray();

        for (SpawnPoint point : definition.getSpawnPoints()) {
            JsonObject pointJson = new JsonObject();
            pointJson.addProperty("Dimension", point.dimension());
            pointJson.addProperty("X", point.x());
            pointJson.addProperty("Y", point.y());
            pointJson.addProperty("Z", point.z());
            spawnPoints.add(pointJson);
        }

        json.add("SpawnPoints", spawnPoints);

        JsonObject messages = new JsonObject();
        messages.addProperty("Announce", definition.getAnnounceMessage());
        messages.addProperty("Spawn", definition.getSpawnMessage());

        JsonObject end = new JsonObject();

        for (EventEndReason reason : EventEndReason.values()) {
            definition.getEndMessage(reason).ifPresent(message -> end.addProperty(reason.name(), message));
        }

        messages.add("End", end);
        json.add("Messages", messages);

        JsonObject rewards = new JsonObject();

        for (EventEndReason reason : EventEndReason.values()) {
            List<String> commands = definition.getRewards(reason);

            if (!commands.isEmpty()) {
                JsonArray array = new JsonArray();
                commands.forEach(array::add);
                rewards.add(reason.name(), array);
            }
        }

        json.add("Rewards", rewards);

        return json;
    }

    private static Number ticksToSeconds(int ticks) {
        // Whole seconds are written as 120, not 120.0
        if (ticks % 20 == 0) {
            return ticks / 20;
        }

        return ticks / 20.0;
    }

    private static boolean has(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull();
    }

    private static JsonElement require(JsonObject obj, String key) {
        if (!has(obj, key)) {
            throw new IllegalArgumentException("Missing required field '" + key + "'");
        }

        return obj.get(key);
    }

    private static String requireString(JsonObject obj, String key) {
        JsonElement element = require(obj, key);

        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Field '" + key + "' must be text");
        }

        return element.getAsString();
    }

    private static String optString(JsonObject obj, String key, String fallback) {
        return has(obj, key) ? requireString(obj, key) : fallback;
    }

    private static double requireDouble(JsonObject obj, String key) {
        JsonElement element = require(obj, key);

        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Field '" + key + "' must be a number");
        }

        double value = element.getAsDouble();

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Field '" + key + "' must be a finite number");
        }

        return value;
    }

    private static double optDouble(JsonObject obj, String key, double fallback) {
        return has(obj, key) ? requireDouble(obj, key) : fallback;
    }

    private static int optInt(JsonObject obj, String key, int fallback) {
        return has(obj, key) ? requireInt(obj, key) : fallback;
    }

    private static int requireInt(JsonObject obj, String key) {
        return intElement(require(obj, key), key);
    }

    private static boolean optBool(JsonObject obj, String key, boolean fallback) {
        if (!has(obj, key)) {
            return fallback;
        }

        JsonElement element = obj.get(key);

        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Field '" + key + "' must be true or false");
        }

        return element.getAsBoolean();
    }

    private static JsonArray requireArray(JsonObject obj, String key) {
        JsonElement element = require(obj, key);

        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Field '" + key + "' must be a list");
        }

        return element.getAsJsonArray();
    }

    private static JsonObject requireObject(JsonObject obj, String key) {
        JsonElement element = require(obj, key);

        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Field '" + key + "' must be an object");
        }

        return element.getAsJsonObject();
    }

    private static int intElement(JsonElement element, String name) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Field '" + name + "' must contain whole numbers");
        }

        double value = element.getAsDouble();

        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Field '" + name + "' must contain whole numbers");
        }

        return (int) value;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String text, String key) {
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            String valid = Arrays.stream(type.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));

            throw new IllegalArgumentException("Unknown " + key + " '" + text + "'. Valid: " + valid);
        }
    }

    private static int readTicks(JsonObject obj, String key, double fallbackSeconds) {
        double seconds = optDouble(obj, key, fallbackSeconds);

        if (seconds < 0) {
            throw new IllegalArgumentException("Field '" + key + "' cannot be negative");
        }

        long ticks = Math.round(seconds * 20);

        if (ticks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Field '" + key + "' is too large");
        }

        return (int) ticks;
    }

    private static EventSpec parseSpec(EventType type, JsonObject json, String id) {
        return switch (type) {
            case POKEMON -> {
                warnIfPresent(json, "TrainerName", id, type);

                String speciesName = requireString(json, "Species");
                CobblemonSpecies species = CobblemonSpecies.fromString(speciesName);

                if (species == null) {
                    throw new IllegalArgumentException("Unknown Species '" + speciesName + "'");
                }

                JsonArray range = requireArray(json, "LevelRange");

                if (range.size() != 2) {
                    throw new IllegalArgumentException("Field 'LevelRange' must be exactly two numbers: [min, max]");
                }

                yield new PokemonSpec(species, new LevelRange(
                        intElement(range.get(0), "LevelRange"),
                        intElement(range.get(1), "LevelRange")));
            }
            case TRAINER -> {
                warnIfPresent(json, "Species", id, type);
                warnIfPresent(json, "LevelRange", id, type);

                yield new TrainerSpec(requireString(json, "TrainerName"));
            }
        };
    }

    private static void warnIfPresent(JsonObject json, String key, String id, EventType type) {
        if (has(json, key)) {
            NeoConstants.LOGGER.warn("Event {}: ignoring '{}' (not used by {} events)", id, key, type);
        }
    }

    private static List<SpawnPoint> parseSpawnPoints(JsonObject json) {
        JsonArray array = requireArray(json, "SpawnPoints");

        if (array.isEmpty()) {
            throw new IllegalArgumentException("Field 'SpawnPoints' needs at least one point");
        }

        List<SpawnPoint> points = new ArrayList<>();

        for (int i = 0; i < array.size(); i++) {
            try {
                JsonElement element = array.get(i);

                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("must be an object");
                }

                JsonObject point = element.getAsJsonObject();
                String dimension = requireString(point, "Dimension");

                if (ResourceLocation.tryParse(dimension) == null) {
                    throw new IllegalArgumentException("invalid Dimension '" + dimension + "'");
                }

                points.add(new SpawnPoint(dimension,
                        requireDouble(point, "X"),
                        requireDouble(point, "Y"),
                        requireDouble(point, "Z")));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("SpawnPoints[" + i + "]: " + exception.getMessage());
            }
        }

        return points;
    }

    private static Map<EventEndReason, String> parseEndMessages(JsonObject messages, String id) {
        Map<EventEndReason, String> endMessages = new EnumMap<>(EventEndReason.class);

        if (!has(messages, "End")) {
            return endMessages;
        }

        JsonObject endObject = requireObject(messages, "End");

        for (Map.Entry<String, JsonElement> entry : endObject.entrySet()) {
            if (entry.getKey().startsWith("_")) {
                continue;
            }

            try {
                EventEndReason reason = parseEnum(EventEndReason.class, entry.getKey(), "End message reason");
                endMessages.put(reason, requireString(endObject, entry.getKey()));
            } catch (IllegalArgumentException exception) {
                NeoConstants.LOGGER.warn("Event {}: ignoring End message: {}", id, exception.getMessage());
            }
        }

        return endMessages;
    }

    private static Map<EventEndReason, List<String>> parseRewards(JsonObject json, String id) {
        Map<EventEndReason, List<String>> rewards = new EnumMap<>(EventEndReason.class);

        if (!has(json, "Rewards")) {
            return rewards;
        }

        JsonObject rewardsObject = requireObject(json, "Rewards");

        for (Map.Entry<String, JsonElement> entry : rewardsObject.entrySet()) {
            if (entry.getKey().startsWith("_")) {
                continue;
            }

            try {
                EventEndReason reason = parseEnum(EventEndReason.class, entry.getKey(), "Rewards");

                if (reason != EventEndReason.CAPTURED && reason != EventEndReason.DEFEATED) {
                    throw new IllegalArgumentException(reason + " has no winning player, so its rewards would never run");
                }

                JsonArray array = requireArray(rewardsObject, entry.getKey());
                List<String> commands = new ArrayList<>();

                for (int i = 0; i < array.size(); i++) {
                    JsonElement element = array.get(i);

                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("Entry " + i + "must be text.");
                    }

                    String command = element.getAsString().trim();

                    if (command.startsWith("/")) {
                        command = command.substring(1);
                    }

                    if (!command.isEmpty()) {
                        commands.add(command);
                    }
                }

                if (!commands.isEmpty()) {
                    rewards.put(reason, commands);
                }
            } catch (IllegalArgumentException exception) {
                NeoConstants.LOGGER.warn("Event {}: ignoring rewards entry '{}': {}", id, entry.getKey(), exception.getMessage());
            }
        }

        return rewards;
    }
}
