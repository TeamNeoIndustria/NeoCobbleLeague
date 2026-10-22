package xyz.neonetwork.neocobbleleague.common.utils;
import com.google.gson.*;
import org.apache.commons.io.IOUtils;
import xyz.neonetwork.neocobblelib.NeoConstants;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ConfigFile {

    private final String path;
    private final String fileName;
    private File file;

    private JsonObject jsonObj;

    public ConfigFile(String name) {
        if (!name.endsWith(".json")) {
            name = name + ".json";
        }

        this.path = "./config/NeoCobbleLeague";
        this.fileName = name;
        this.file = new File(this.path + "/" + this.fileName);

        checkDir();
        checkFile();
    }

    public void checkDir() {
        File dir = new File(this.path);

        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public void checkFile() {
        if (file.exists()) {
            return;
        }

        try {
            file.createNewFile();
        } catch (IOException exception) {
            NeoConstants.LOGGER.error("Error creating config file.");
            exception.printStackTrace();
        }
    }

    public void loadConfig() {
        this.file = new File(this.path + "/" + this.fileName);

        if (file.length() == 0) {
            switch (this.fileName) {
                case "events.json" -> setEventsConfig();
            }
        }

        try (FileInputStream inputStream = new FileInputStream(this.path + "/" + this.fileName)) {
            JsonObject newObj = null;

            try {
                newObj = JsonParser.parseString(IOUtils.toString(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (JsonParseException | IllegalStateException exception) {
                NeoConstants.LOGGER.error("Error parsing Json file: {}", this.fileName);
                NeoConstants.LOGGER.error(exception.getMessage());
            }

            if (newObj == null) {
                return;
            }

            this.jsonObj = newObj;
        } catch (IOException exception) {
            NeoConstants.LOGGER.error("Error loading config file. Continuing to create new...");
            NeoConstants.LOGGER.error(exception.getMessage());
        }
    }

    public void setEventsConfig() {
        JsonObject configObject = new JsonObject();
        JsonArray eventArray = new JsonArray();

        configObject.addProperty("_comment", "Times are in seconds. Use & for colour codes. {name} = event display name, {player} = the player involved. Type is POKEMON (needs Species + LevelRange) or TRAINER (needs TrainerName, from NeoCobbleLib's trainers.json). Events are ignored while Enabled is false.");

        // ---------- Pokemon Event ----------
        JsonObject pokemonEvent = new JsonObject();
        pokemonEvent.addProperty("Id", "example_articuno");
        pokemonEvent.addProperty("DisplayName", "&bArticuno");
        pokemonEvent.addProperty("Enabled", false);
        pokemonEvent.addProperty("Type", "POKEMON");

        // Timings
        pokemonEvent.addProperty("CountdownSeconds", 120);
        pokemonEvent.addProperty("LifetimeSeconds", 900);
        pokemonEvent.addProperty("CooldownSeconds", 3600);
        pokemonEvent.addProperty("Weight", 1);

        // Pokemon specific
        pokemonEvent.addProperty("Species", "articuno");

        JsonArray levelRange = new JsonArray();
        levelRange.add(60);
        levelRange.add(70);
        pokemonEvent.add("LevelRange", levelRange);

        // Spawns
        pokemonEvent.addProperty("SpawnMode", "FIXED");

        JsonArray pokemonSpawns = new JsonArray();
        pokemonSpawns.add(createSpawnPoint("minecraft:overworld", 0.5, 100.0, 0.5));
        pokemonEvent.add("SpawnPoints", pokemonSpawns);

        // Messages
        JsonObject pokemonMessages = new JsonObject();
        pokemonMessages.addProperty("Announce", "&e{name} has been spotted nearby!");
        pokemonMessages.addProperty("Spawn", "&c{name} has appeared!");

        JsonObject pokemonEnd = new JsonObject();
        pokemonEnd.addProperty("CAPTURED", "&a{player} caught {name}!");
        pokemonEnd.addProperty("DEFEATED", "&a{player} defeated {name}!");
        pokemonEnd.addProperty("DESPAWNED", "&7{name} wandered off.");
        pokemonEnd.addProperty("TIMED_OUT", "&7{name} disappeared into the distance.");

        pokemonMessages.add("End", pokemonEnd);
        pokemonEvent.add("Messages", pokemonMessages);

        eventArray.add(pokemonEvent);

        // ---------- Trainer Event ----------
        JsonObject trainerEvent = new JsonObject();
        trainerEvent.addProperty("Id", "example_brock");
        trainerEvent.addProperty("DisplayName", "&6Gym Leader Brock");
        trainerEvent.addProperty("Enabled", false);
        trainerEvent.addProperty("Type", "TRAINER");

        // Timings
        trainerEvent.addProperty("CountdownSeconds", 60);
        trainerEvent.addProperty("LifetimeSeconds", 600);
        trainerEvent.addProperty("CooldownSeconds", 1800);
        trainerEvent.addProperty("Weight", 1);

        // Trainer specific (must match a Name in NeoCobbleLib's trainers.json)
        trainerEvent.addProperty("TrainerName", "Brock");

        // Spawns
        trainerEvent.addProperty("SpawnMode", "FIXED");

        JsonArray trainerSpawns = new JsonArray();
        trainerSpawns.add(createSpawnPoint("minecraft:overworld", 10.5, 100.0, 10.5));
        trainerEvent.add("SpawnPoints", trainerSpawns);

        // Messages (no CAPTURED for trainers)
        JsonObject trainerMessages = new JsonObject();
        trainerMessages.addProperty("Announce", "&e{name} is challenging all comers!");
        trainerMessages.addProperty("Spawn", "&c{name} is waiting for a challenger!");

        JsonObject trainerEnd = new JsonObject();
        trainerEnd.addProperty("DEFEATED", "&a{player} defeated {name}!");
        trainerEnd.addProperty("DESPAWNED", "&7{name} has left.");
        trainerEnd.addProperty("TIMED_OUT", "&7{name} grew tired of waiting.");

        trainerMessages.add("End", trainerEnd);
        trainerEvent.add("Messages", trainerMessages);

        eventArray.add(trainerEvent);

        configObject.add("Events", eventArray);

        pushToFile(configObject);
    }

    private JsonObject createSpawnPoint(String dimension, double x, double y, double z) {
        JsonObject point = new JsonObject();
        point.addProperty("Dimension", dimension);
        point.addProperty("X", x);
        point.addProperty("Y", y);
        point.addProperty("Z", z);

        return point;
    }

    public void pushToFile(JsonObject objectToPush) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (FileWriter writer = new FileWriter(this.path + "/" + this.fileName, StandardCharsets.UTF_8)) {
            gson.toJson(objectToPush, writer);
        } catch (IOException exception) {
            NeoConstants.LOGGER.error("Failed to write {} file defaults. \n{}", this.fileName, exception);
        }
    }

    public JsonObject getConfig() {
        if (jsonObj == null) {
            NeoConstants.LOGGER.error("Config was not loaded before getting.");

            return null;
        }

        return jsonObj;
    }

    public JsonObject get(String path) {
        if (jsonObj == null) {
            NeoConstants.LOGGER.error("Config was not loaded before grabbing data.");

            return null;
        }

        return jsonObj.getAsJsonObject(path);
    }

    public JsonElement getElement(String path) {
        if (jsonObj == null) {
            NeoConstants.LOGGER.error("Config was not loaded before grabbing data.");

            return null;
        }

        return jsonObj.get(path);
    }

    public boolean isModEnabled() {
        if (jsonObj == null) {
            NeoConstants.LOGGER.error("Config was not loaded before grabbing data.");

            return true;
        }

        return jsonObj.get("Enabled").getAsBoolean();
    }

}
