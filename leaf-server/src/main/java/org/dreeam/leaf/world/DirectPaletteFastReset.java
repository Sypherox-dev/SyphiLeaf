package org.dreeam.leaf.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.dreeam.leaf.network.AsyncPacketBroadcaster;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Direct Palette Arena Fast-Reset Engine.
 * Bypasses Level#setBlock, neighbor physics updates, and TileEntity snapshot creation.
 * Writes directly into LevelChunkSection palettes and broadcasts updates via AsyncPacketBroadcaster.
 */
public final class DirectPaletteFastReset {

    private DirectPaletteFastReset() {}

    /**
     * Resets/sets blocks in batch directly to chunk section palettes.
     *
     * @param level The target ServerLevel.
     * @param packedPosToState Map of packed block coordinates (BlockPos#asLong) to new BlockStates.
     */
    public static void setBlocksDirect(
            @NotNull ServerLevel level,
            @NotNull Long2ObjectOpenHashMap<BlockState> packedPosToState
    ) {
        if (packedPosToState.isEmpty()) return;

        // 1. Group changes by SectionPos (packed long)
        Long2ObjectOpenHashMap<Short2ObjectOpenHashMap<BlockState>> sectionBatches = new Long2ObjectOpenHashMap<>();

        var iterator = packedPosToState.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<BlockState> entry = iterator.next();
            long packedPos = entry.getLongKey();
            BlockState state = entry.getValue();

            int x = BlockPos.getX(packedPos);
            int y = BlockPos.getY(packedPos);
            int z = BlockPos.getZ(packedPos);

            int secX = SectionPos.blockToSectionCoord(x);
            int secY = SectionPos.blockToSectionCoord(y);
            int secZ = SectionPos.blockToSectionCoord(z);

            long sectionKey = SectionPos.asLong(secX, secY, secZ);
            Short2ObjectOpenHashMap<BlockState> batch = sectionBatches.computeIfAbsent(
                    sectionKey,
                    k -> new Short2ObjectOpenHashMap<>()
            );

            short localPos = SectionPos.sectionRelativePos(new BlockPos(x, y, z));
            batch.put(localPos, state);
        }

        // 2. Write states directly into LevelChunkSection palettes
        var batchIter = sectionBatches.long2ObjectEntrySet().fastIterator();
        while (batchIter.hasNext()) {
            Long2ObjectMap.Entry<Short2ObjectOpenHashMap<BlockState>> batchEntry = batchIter.next();
            long sectionKey = batchEntry.getLongKey();
            Short2ObjectOpenHashMap<BlockState> sectionChanges = batchEntry.getValue();

            int secX = SectionPos.x(sectionKey);
            int secY = SectionPos.y(sectionKey);
            int secZ = SectionPos.z(sectionKey);

            LevelChunk chunk = level.getChunkSource().getChunk(secX, secZ, false);
            if (chunk == null) continue;

            int sectionIndex = level.getSectionIndexFromSectionY(secY);
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;

            LevelChunkSection section = chunk.getSection(sectionIndex);
            if (section == null) continue;

            var changeIter = sectionChanges.short2ObjectEntrySet().fastIterator();
            while (changeIter.hasNext()) {
                var change = changeIter.next();
                short localPos = change.getShortKey();
                BlockState newState = change.getValue();

                int localX = SectionPos.sectionRelativeX(localPos);
                int localY = SectionPos.sectionRelativeY(localPos);
                int localZ = SectionPos.sectionRelativeZ(localPos);

                section.setBlockState(localX, localY, localZ, newState, false);
            }

            chunk.markUnsaved();

            // 3. Broadcast batch packet to tracking players via AsyncPacketBroadcaster
            SectionPos sectionPos = SectionPos.of(secX, secY, secZ);
            ClientboundSectionBlocksUpdatePacket packet = new ClientboundSectionBlocksUpdatePacket(sectionPos, sectionChanges);
            List<ServerPlayer> watchingPlayers = level.getChunkSource().chunkMap.getPlayers(sectionPos.chunk(), false);

            if (!watchingPlayers.isEmpty()) {
                AsyncPacketBroadcaster.broadcast(packet, watchingPlayers);
            }
        }
    }
}
