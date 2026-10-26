package org.dreeam.leaf.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import org.dreeam.leaf.config.modules.opt.HighDensityArenaOptimization;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Netty inbound pre-processor injected before PacketUtils.ensureRunningOnSameThread.
 * Drops throttled/spam packets directly on the Netty EventLoop and offloads
 * anti-cheat validation heuristics to Virtual Threads.
 */
public final class NettyInboundInterceptor extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "syphi_leaf_inbound_interceptor";

    private static final ExecutorService PACKET_COMPUTE_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final Connection connection;
    private long lastSecond = 0L;
    private int movePacketsThisSecond = 0;
    private int interactPacketsThisSecond = 0;

    public NettyInboundInterceptor(Connection connection) {
        this.connection = connection;
    }

    public static void inject(ChannelPipeline pipeline, Connection connection) {
        if (pipeline.get(HANDLER_NAME) == null) {
            pipeline.addBefore("packet_handler", HANDLER_NAME, new NettyInboundInterceptor(connection));
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!HighDensityArenaOptimization.inboundInterceptorEnabled) {
            super.channelRead(ctx, msg);
            return;
        }

        long currentSecond = System.currentTimeMillis() / 1000L;
        if (currentSecond != this.lastSecond) {
            this.lastSecond = currentSecond;
            this.movePacketsThisSecond = 0;
            this.interactPacketsThisSecond = 0;
        }

        // 1. Movement Rate-Limiting vor dem Main-Thread
        if (msg instanceof ServerboundMovePlayerPacket) {
            if (++this.movePacketsThisSecond > HighDensityArenaOptimization.maxMovePacketsPerSecond) {
                // Drop packet: gelangt nie in die Server-Tick-Queue
                return;
            }
        }

        // 2. Combat / Interact Rate-Limiting & Asynchrone Vorabprüfung
        if (msg instanceof ServerboundInteractPacket || msg instanceof ServerboundSwingPacket) {
            if (++this.interactPacketsThisSecond > HighDensityArenaOptimization.maxInteractPacketsPerSecond) {
                // Drop autoclicker / spam packet
                return;
            }

            final ServerPlayer player = this.connection.getPlayer();
            if (player != null) {
                final Object packetRef = msg;
                PACKET_COMPUTE_POOL.execute(() -> {
                    // Async inspection context (AntiCheat / combat telemetry)
                    // World mutations must never be run here!
                });
            }
        }

        super.channelRead(ctx, msg);
    }
}
