package cn.genlite.domain;

import java.util.Map;
import java.util.UUID;

/** Shared read-only checks; placement preflight never replaces validation inside the durable transaction. */
public final class MachineShells {
    private MachineShells() {}
    public static String rejection(Map<UUID,Machine> machines,String type,UUID source,UUID token) {
        if(source==null) return "missing-source";
        Machine old=machines.get(source);
        if(old==null) return "unknown-source";
        if(!old.removed()) return "not-dismantled";
        if(!old.cleared()) return "not-cleared";
        if(token==null) return "missing-token";
        if(old.shellToken()==null) return "already-used";
        if(!token.equals(old.shellToken())) return "token-mismatch";
        return old.type().equals(type)?null:"type-mismatch";
    }
    public static Machine require(Map<UUID,Machine> machines,String type,UUID source,UUID token) {
        if(rejection(machines,type,source,token)!=null) throw new IllegalArgumentException("invalid-shell");
        return machines.get(source);
    }
}
