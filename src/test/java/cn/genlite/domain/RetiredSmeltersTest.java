package cn.genlite.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RetiredSmeltersTest {
    @Test void retirementIsIdempotentAndRefundsOnlyAfterPhysicalCleanupOnce() throws Exception {
        var m=Machine.create(TestRules.pos(0,64,0),TestRules.HOME,TestRules.PLAYER,"basic_smelter",3).contents(19,7,11,Map.of());
        var original=new WorldState(1,Map.of(m.id(),m),Map.of());var migrated=RetiredSmelters.migrate(original);
        assertTrue(migrated.machines().get(m.id()).removed());assertFalse(migrated.machines().get(m.id()).cleared());
        assertEquals(migrated,RetiredSmelters.migrate(migrated));assertTrue(migrated.parcels().isEmpty());
        var rules=TestRules.load();StateValidation.check(migrated,rules);assertEquals(0,rules.value(migrated.machines().get(m.id())));
        var d=migrated.draft();var actions=new MachineActions(rules,(h,p) -> true);actions.cleared(d,m.id());
        assertEquals(4,d.parcels.size());assertEquals(2,d.parcels.values().stream().filter(p -> p.item().equals("genlite:fuel")).findFirst().orElseThrow().amount());
        assertTrue(d.parcels.values().stream().anyMatch(p -> p.item().equals("minecraft:furnace")));
        assertTrue(d.parcels.values().stream().anyMatch(p -> p.item().equals("minecraft:raw_iron") && p.amount()==7));
        assertEquals(19,d.machines.get(m.id()).fuel());actions.cleared(d,m.id());assertEquals(4,d.parcels.size());
    }
    @Test void activeDefaultsHaveNoSmelterAndGeneratorsUpgradeWithDedicatedUnits() throws Exception {
        var rules=TestRules.load();assertEquals(4,rules.types().size());assertFalse(rules.types().containsKey("basic_smelter"));
        assertEquals("minecraft:iron_ingot",rules.types().get("iron_generator").output());
        for(var entry:rules.types().entrySet()) for(var costs:entry.getValue().upgrades().values()) {
            if(entry.getKey().equals("resource_collector")) assertTrue(costs.keySet().stream().allMatch(id -> id.startsWith("minecraft:")));
            else assertEquals(Set.of("genlite:basic_upgrade_unit"),costs.keySet());
        }
    }
}
