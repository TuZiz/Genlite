package cn.genlite.domain;

import java.util.UUID;
import java.util.function.BiPredicate;

/** State-executor only; follows DreamSkyblock's equal rights for all home owners. */
public final class HomeHologramSettings {
    private HomeHologramSettings() {}
    public static boolean toggle(WorldState.Draft draft,UUID home,UUID player,BiPredicate<UUID,UUID> owner) {
        if(home==null || !owner.test(home,player)) throw new IllegalStateException("permission");
        boolean enabled=!draft.hologramVisibility.getOrDefault(home,true);
        if(enabled) draft.hologramVisibility.remove(home);else draft.hologramVisibility.put(home,false);
        return enabled;
    }
}
