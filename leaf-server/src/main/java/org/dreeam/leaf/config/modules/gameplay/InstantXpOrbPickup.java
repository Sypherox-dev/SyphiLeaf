package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

// Leaf start - Instant XP Orb Pickup
public class InstantXpOrbPickup extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".instant-xp-orb-pickup";
    }

    /**
     * When enabled, sets the player's takeXpDelay to 0 before the pickup check
     * in ExperienceOrb#playerTouch, so orbs are absorbed immediately on contact
     * without the default 2-tick cooldown. This eliminates the window where
     * high-count orb stacks remain alive for extra ticks, reducing entity load.
     *
     * All Bukkit/Paper events (PlayerPickupExperienceEvent, PlayerExpChangeEvent,
     * PlayerItemMendEvent) are still fired as normal -- plugin compatibility is
     * fully preserved.
     */
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath(), enabled, config.pickStringRegionBased(
            "When enabled, removes the default 2-tick XP orb pickup cooldown (takeXpDelay), " +
            "allowing players to instantly absorb experience orbs on contact. " +
            "This reduces the time high-count orb stacks stay alive, lowering entity tick load. " +
            "All Bukkit/Paper events (PlayerPickupExperienceEvent, PlayerExpChangeEvent, " +
            "PlayerItemMendEvent) are still fired correctly.",
            "\u542f\u7528\u540e\uff0c\u79fb\u9664\u9ed8\u8ba4\u7684 2 tick XP \u62fe\u53d6\u51b7\u5374\uff08takeXpDelay\uff09\uff0c" +
            "\u4f7f\u73a9\u5bb6\u63a5\u89e6\u7ecf\u9a8c\u7403\u65f6\u7acb\u5373\u5438\u6536\u7ecf\u9a8c\u3002" +
            "\u6b64\u9009\u9879\u51cf\u5c11\u9ad8\u6570\u91cf\u7ecf\u9a8c\u7403\u5b58\u6d3b\u7684\u65f6\u95f4\uff0c\u964d\u4f4e\u5b9e\u4f53 Tick \u8d1f\u8f7d\u3002" +
            "\u6240\u6709 Bukkit/Paper \u4e8b\u4ef6\u4ecd\u6b63\u5e38\u89e6\u53d1\uff0c\u63d2\u4ef6\u517c\u5bb9\u6027\u5b8c\u5168\u4fdd\u7559\u3002"
        ));
    }
}
// Leaf end - Instant XP Orb Pickup