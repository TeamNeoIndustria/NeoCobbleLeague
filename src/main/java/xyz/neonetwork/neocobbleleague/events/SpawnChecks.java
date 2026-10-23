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
}