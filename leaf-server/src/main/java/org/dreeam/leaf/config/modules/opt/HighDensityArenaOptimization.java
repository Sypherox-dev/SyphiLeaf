package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class HighDensityArenaOptimization extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".high-density-arena";
    }

    public static boolean enabled = true;
    public static boolean cullCollisions = true;
    public static int collisionDensityThreshold = 15;
    public static boolean asyncBroadcasterEnabled = true;
    public static int asyncBroadcasterThreads = 2;
    public static boolean inboundInterceptorEnabled = true;
    public static int maxMovePacketsPerSecond = 35;
    public static int maxInteractPacketsPerSecond = 40;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Ultra-low latency optimizations for 100+ player high-density arenas.
                Includes O(1) collision culling, Netty inbound pre-filtering, and zero-copy packet broadcasting.""",
            """
                针对百人高密度竞技场/团战场景的超低延迟优化。
                包含 O(1) 实体推挤裁剪、Netty 入站前置过滤与零拷贝广播。""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        cullCollisions = config.getBoolean(getBasePath() + ".cull-collisions", cullCollisions, config.pickStringRegionBased("""
                Bypass O(n^2) player-to-player collision physics calculations when local chunk density exceeds threshold.""",
            """
                当区块实体密度超过阈值时跳过玩家间 O(n^2) 的推挤物理碰撞检测。"""));
        collisionDensityThreshold = config.getInt(getBasePath() + ".collision-density-threshold", collisionDensityThreshold, config.pickStringRegionBased("""
                Entity count threshold per chunk to trigger push collision culling.""",
            """
                触发碰撞裁剪的单区块实体数量阈值。"""));

        asyncBroadcasterEnabled = config.getBoolean(getBasePath() + ".async-broadcaster.enabled", asyncBroadcasterEnabled, config.pickStringRegionBased("""
                Enables single-serialization ByteBuf packet broadcasting for particles, sounds, and combat metadata.""",
            """
                为粒子、音效和实体数据启用单次序列化 ByteBuf 零拷贝广播。"""));
        asyncBroadcasterThreads = config.getInt(getBasePath() + ".async-broadcaster.threads", asyncBroadcasterThreads, config.pickStringRegionBased("""
                Thread count for the async broadcast worker pool.""",
            """
                异步广播工作线程池线程数。"""));

        inboundInterceptorEnabled = config.getBoolean(getBasePath() + ".inbound-interceptor.enabled", inboundInterceptorEnabled, config.pickStringRegionBased("""
                Injects an inbound packet pre-processor into Netty pipeline to drop spam movements and run async AntiCheat heuristics.""",
            """
                在 Netty 流水线中注入入站前置处理器，丢弃异常移动刷包并异步执行反作弊检测。"""));
        maxMovePacketsPerSecond = config.getInt(getBasePath() + ".inbound-interceptor.max-move-packets-per-second", maxMovePacketsPerSecond);
        maxInteractPacketsPerSecond = config.getInt(getBasePath() + ".inbound-interceptor.max-interact-packets-per-second", maxInteractPacketsPerSecond);
    }
}
