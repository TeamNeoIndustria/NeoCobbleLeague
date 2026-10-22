package xyz.neonetwork.neocobbleleague.events.records;

import net.minecraft.util.RandomSource;

public record LevelRange(int min, int max) {
    public LevelRange {
        if (min < 1 || max > 100 || min > max) {
            throw new IllegalArgumentException("Invalid level range: " + min + "-" + max);
        }
    }

    public int roll(RandomSource random) {
        return random.nextInt(min, max);
    }
}
