package cn.genlite.integration;

import cn.genlite.domain.*;
import java.util.UUID;

public interface IslandAccess {
    UUID homeId(Position position);
    boolean member(UUID home,UUID player);
    default UUID memberHome(UUID player) {return null;}
    boolean active(Machine machine);
    void refresh(MachineEngine engine);
    void clear();
    static IslandAccess unavailable() {
        return new IslandAccess() {
            public UUID homeId(Position p) {return null;}
            public boolean member(UUID h,UUID p) {return false;}
            public boolean active(Machine m) {return false;}
            public void refresh(MachineEngine e) {}
            public void clear() {}
        };
    }
}
