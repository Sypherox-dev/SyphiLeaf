package org.dreeam.leaf.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dreeam.leaf.config.modules.opt.HighDensityArenaOptimization;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Ultra-high-throughput Zero-Copy Packet Broadcaster for 100+ player high-density environments.
 * Decouples packet serialization and dispatch from the Main Thread.
 */
public final class AsyncPacketBroadcaster {

    private static final ExecutorService BROADCAST_POOL = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 4),
            Thread.ofPlatform().name("Leaf-Packet-Broadcaster-", 0).daemon(true).factory()
    );

    private AsyncPacketBroadcaster() {}

    /**
     * Broadcasts a packet asynchronously to multiple target players.
     * When async broadcasting is enabled, offloads serialization and iteration to the background worker pool.
     */
    public static void broadcast(
            @NotNull Packet<?> packet,
            @NotNull Collection<ServerPlayer> targets
    ) {
        if (targets.isEmpty()) return;

        if (!HighDensityArenaOptimization.asyncBroadcasterEnabled) {
            for (ServerPlayer player : targets) {
                if (player != null && player.connection != null) {
                    player.connection.send(packet);
                }
            }
            return;
        }

        BROADCAST_POOL.execute(() -> {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) {
                fallbackBroadcast(packet, targets);
                return;
            }

            RegistryAccess registryAccess = server.registryAccess();
            var boundProtocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registryAccess));
            var codec = boundProtocol.codec();

            // 1. Packet exakt einmal in ByteBuf serialisieren
            ByteBuf rawBuffer = ByteBufAllocator.DEFAULT.ioBuffer();
            try {
                RegistryFriendlyByteBuf friendlyBuf = new RegistryFriendlyByteBuf(rawBuffer, registryAccess);
                codec.encode(friendlyBuf, (Packet<ClientGamePacketListener>) packet);

                int readableBytes = rawBuffer.readableBytes();
                if (readableBytes <= 0) {
                    fallbackBroadcast(packet, targets);
                    return;
                }

                // 2. Broadcast via retainedDuplicate() an alle Zielkanäle
                for (ServerPlayer player : targets) {
                    if (player == null || player.connection == null) continue;
                    Connection connection = player.connection.connection;
                    if (connection == null || !connection.isConnected()) continue;

                    Channel channel = connection.channel;
                    if (channel == null || !channel.isActive()) continue;

                    ByteBuf duplicate = rawBuffer.retainedDuplicate();
                    channel.eventLoop().execute(() -> {
                        try {
                            ChannelHandlerContext compressCtx = channel.pipeline().context("compress");
                            ChannelHandlerContext prependerCtx = channel.pipeline().context("prepender");
                            ChannelHandlerContext targetCtx = compressCtx != null ? compressCtx : prependerCtx;

                            if (targetCtx != null) {
                                targetCtx.write(duplicate, channel.voidPromise());
                            } else {
                                channel.write(duplicate, channel.voidPromise());
                            }
                        } catch (Throwable t) {
                            duplicate.release();
                        }
                    });
                }

                // 3. Batched Flush auf den Ziel-EventLoops
                for (ServerPlayer player : targets) {
                    if (player == null || player.connection == null) continue;
                    Connection connection = player.connection.connection;
                    if (connection == null || !connection.isConnected()) continue;

                    Channel channel = connection.channel;
                    if (channel != null && channel.isActive()) {
                        channel.eventLoop().execute(channel::flush);
                    }
                }
            } catch (Throwable t) {
                // Bei dynamischen Sonderpaketen sicherer Fallback
                fallbackBroadcast(packet, targets);
            } finally {
                rawBuffer.release();
            }
        });
    }

    private static void fallbackBroadcast(Packet<?> packet, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            if (player != null && player.connection != null) {
                player.connection.send(packet);
            }
        }
    }
}
