package org.dreeam.leaf.world;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.dreeam.leaf.config.modules.opt.HighDensityArenaOptimization;

/**
 * High-performance O(1) entity push collision culler for high-density arenas.
 * Eliminates O(n^2) bounding-box iterations when entity density spikes.
 */
public final class DenseCollisionCuller {

    private DenseCollisionCuller() {}

    /**
     * Determines whether entity-to-entity push physics should be calculated.
     * Returns true to execute vanilla push physics, or false to cull immediately.
     */
    public static boolean shouldProcessPush(Entity source, Entity target) {
        if (!HighDensityArenaOptimization.cullCollisions) {
            return true;
        }

        // 1. Skip player-to-player push collision calculations in combat arenas
        if (source instanceof Player && target instanceof Player) {
            return false;
        }

        // 2. O(1) Chunk entity density check via Moonrise entity slices
        if (source.level() instanceof ServerLevel serverLevel) {
            int chunkX = SectionPos.blockToSectionCoord(source.getBlockX());
            int chunkZ = SectionPos.blockToSectionCoord(source.getBlockZ());

            ServerEntityLookup lookup = (ServerEntityLookup) serverLevel.moonrise$getEntityLookup();
            if (lookup != null) {
                ChunkEntitySlices slices = lookup.getChunk(chunkX, chunkZ);
                if (slices != null && slices.getEntityCount() > HighDensityArenaOptimization.collisionDensityThreshold) {
                    return false;
                }
            }
        }

        return true;
    }
}
