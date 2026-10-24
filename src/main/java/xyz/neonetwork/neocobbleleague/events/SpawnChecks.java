package xyz.neonetwork.neocobbleleague.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import xyz.neonetwork.neocobbleleague.events.records.SpawnPoint;

import java.util.Optional;

public final class SpawnChecks {
    private SpawnChecks() {
    }

    public static Optional<ServerLevel> resolveLevel(MinecraftServer server, SpawnPoint point) {
        ResourceLocation id = ResourceLocation.tryParse(point.dimension());

        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
    }

    public static boolean isUsable(ServerLevel level, SpawnPoint point) {
        BlockPos feet = BlockPos.containing(point.x(), point.y(), point.z());
        BlockPos head = feet.above();

        if (!level.isInWorldBounds(feet) || !level.isInWorldBounds(head)) {
            return false;
        }

        if (!level.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }

        level.getChunkAt(feet); // loads (or generates) the chunk

        if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(head).is(FluidTags.LAVA)) {
            return false;
        }

        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    public static Optional<String> findProblem(MinecraftServer server, SpawnPoint point) {
        Optional<ServerLevel> resolved = resolveLevel(server, point);

        if (resolved.isEmpty()) {
            return Optional.of("dimension '" + point.dimension() + "' does not exist");
        }

        ServerLevel level = resolved.get();
        BlockPos feet = BlockPos.containing(point.x(), point.y(), point.z());
        BlockPos head = feet.above();

        if (!level.isInWorldBounds(feet) || !level.isInWorldBounds(head)) {
            return Optional.of("outside the world height limits");
        }

        if (!level.getWorldBorder().isWithinBounds(feet)) {
            return Optional.of("outside the world border");
        }

        level.getChunkAt(feet);

        if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(head).is(FluidTags.LAVA)) {
            return Optional.of("lava at the spawn position");
        }

        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
            return Optional.of("blocked by solid blocks");
        }

        return Optional.empty();
    }
}