package cn.genlite.domain;

import java.util.HashSet;
import java.util.Set;

public final class StateValidation {
    private StateValidation() {}
    public static void check(WorldState state, Rules rules) {
        Set<String> positions=new HashSet<>();
        state.machines().forEach((id,m) -> {
            if(RetiredSmelters.retired(m.type()) && m.removed()) {
                if(!id.equals(m.id()) || m.fuel()<0 || m.input()<0 || m.buffer()<0) throw new IllegalStateException("Invalid retired smelter "+id);
                return;
            }
            if (!id.equals(m.id()) || !rules.types().containsKey(m.type()) || !rules.types().get(m.type()).levels().containsKey(m.level())
                    || m.fuel()<0 || m.input()<0 || m.buffer()<0 || m.revision()<1 || m.acknowledged()>m.revision()
                    || m.inventory().values().stream().anyMatch(n -> n<=0)
                    || rules.usedSlots(m.inventory())>(m.collectorType()?54:rules.level(m).collectorSlots())) throw new IllegalStateException("Invalid machine "+id);
            if ((!m.removed() || !m.cleared()) && !positions.add(m.position().key())) throw new IllegalStateException("Duplicate position "+m.position());
            if (m.inventory().keySet().stream().anyMatch(key -> !key.equals("minecraft:raw_iron") && rules.types().values().stream().noneMatch(t -> key.equals(t.output()))))
                throw new IllegalStateException("Unknown collector output "+id);
        });
        state.parcels().forEach((id,p) -> {
            if (!id.equals(p.id()) || p.amount()<=0 || !Set.of("WAITING","SENDING").contains(p.phase())) throw new IllegalStateException("Invalid parcel "+id);
        });
        state.warehouses().forEach((home,stock) -> {
            if(stock.values().stream().anyMatch(n -> n.signum()<=0) || stock.keySet().stream().anyMatch(key ->
                    !key.matches("(minecraft|genlite):[a-z0-9_]+"))) throw new IllegalStateException("Invalid warehouse "+home);
        });
    }
}
