package cn.genlite.domain;

import java.util.UUID;

/** Immutable coordinates captured on the owning region thread. */
public record Position(UUID world, String worldKey, int x, int y, int z) {
    public String key() { return world + ":" + x + ":" + y + ":" + z; }
}
