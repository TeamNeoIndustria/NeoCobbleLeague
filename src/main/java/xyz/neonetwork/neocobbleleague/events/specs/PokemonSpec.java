package xyz.neonetwork.neocobbleleague.events.specs;

import xyz.neonetwork.neocobbleleague.events.records.LevelRange;
import xyz.neonetwork.neocobblelib.cobblemon.CobblemonSpecies;

public record PokemonSpec(CobblemonSpecies species, LevelRange levelRange) implements EventSpec {
}
