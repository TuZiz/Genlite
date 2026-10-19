package cn.genlite.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionTest {
    final Rules rules;
    ProductionTest() throws Exception {rules=TestRules.load();}
    @Test void progressionAndFuelFollowActualOutput() {
        int[] seconds={10,8,8,6,5},counts={1,1,2,2,2};
        for(int n=1;n<=5;n++) {
            Machine m=TestRules.machine("cobblestone_generator",n,100);
            Machine next=Production.generate(m,rules);
            assertEquals(seconds[n-1],rules.level(m).seconds());
            assertEquals(counts[n-1],next.buffer());assertEquals(100-counts[n-1],next.fuel());
            assertTrue(60.0/rules.level(m).seconds()*rules.level(m).count()<=24);
        }
    }
    @Test void noFuelNoInputAndFullBufferNeverConsume() {
        Machine m=TestRules.machine("iron_generator",3,3);
        assertEquals(m,Production.generate(m,rules));
        Machine full=TestRules.machine("cobblestone_generator",1,100).contents(100,0,64,Map.of());
        assertEquals(full,Production.generate(full,rules));
        Machine working=TestRules.machine("iron_generator",3,100);
        Machine next=Production.generate(working,rules);assertEquals(96,next.fuel());assertEquals(2,next.buffer());

    }
    @Test void preciseEightByEightBoundariesAndIslandIsolation() {
        Machine collector=TestRules.machine("resource_collector",1,0);
        Machine left=Machine.create(TestRules.pos(-4,68,-4),TestRules.HOME,TestRules.PLAYER,"cobblestone_generator",1);
        assertTrue(rules.inRange(left,collector));
        assertFalse(rules.inRange(Machine.create(TestRules.pos(4,64,0),TestRules.HOME,TestRules.PLAYER,"cobblestone_generator",1),collector));
        assertFalse(rules.inRange(Machine.create(TestRules.pos(0,69,0),TestRules.HOME,TestRules.PLAYER,"cobblestone_generator",1),collector));
        assertFalse(rules.inRange(Machine.create(TestRules.pos(0,64,0),UUID.randomUUID(),TestRules.PLAYER,"cobblestone_generator",1),collector));
    }
    @Test void onlyBoundAndConnectedMachinesTransferAndNeverOverflowSlots() {
        Machine c=TestRules.machine("resource_collector",1,0).contents(0,0,0,Map.of("minecraft:cobblestone",27*64-1));
        Machine g=Machine.create(TestRules.pos(1,64,0),TestRules.HOME,TestRules.PLAYER,"cobblestone_generator",1).contents(100,0,10,Map.of());
        WorldState.Draft d=new WorldState(1,Map.of(g.id(),g,c.id(),c),Map.of()).draft();
        Set<UUID> active=Set.of(g.id(),c.id());
        // Default is now physical drops; only COLLECTOR routes to the bound collector.
        g=g.output(OutputMode.WAREHOUSE);d.machines.put(g.id(),g);
        Production.transfer(d,rules,active);assertEquals(0,d.machines.get(g.id()).buffer());assertEquals(27*64-1,d.machines.get(c.id()).inventory().get("minecraft:cobblestone"));
        g=g.contents(100,0,10,Map.of()).output(OutputMode.COLLECTOR);d.machines.put(g.id(),g);
        g=g.settings(1,false,false,true,c.id());d.machines.put(g.id(),g);
        Production.transfer(d,rules,active);
        assertEquals(9,d.machines.get(g.id()).buffer());assertEquals(27*64,d.machines.get(c.id()).inventory().get("minecraft:cobblestone"));
        assertEquals("paused",Production.status(d.machines.get(g.id()),rules,d.machines,active));
        assertEquals(27,rules.usedSlots(d.machines.get(c.id()).inventory()));
    }
    @Test void twoGeneratorsCompeteInOneAtomicDomainTransaction() {
        Machine c=TestRules.machine("resource_collector",1,0).contents(0,0,0,Map.of("minecraft:cobblestone",27*64-3));
        Machine a=Machine.create(TestRules.pos(1,64,0),TestRules.HOME,TestRules.PLAYER,"cobblestone_generator",1).contents(0,0,4,Map.of());
        Machine b=Machine.create(TestRules.pos(2,64,0),TestRules.HOME,TestRules.PLAYER,"cobblestone_generator",1).contents(0,0,4,Map.of());
        a=a.settings(1,false,false,true,c.id());b=b.settings(1,false,false,true,c.id());
        var draft=new WorldState(1,Map.of(a.id(),a,b.id(),b,c.id(),c),Map.of()).draft();
        Production.transfer(draft,rules,Set.of(a.id(),b.id(),c.id()));
        assertEquals(5,draft.machines.get(a.id()).buffer()+draft.machines.get(b.id()).buffer());
        assertEquals(27*64,draft.machines.get(c.id()).inventory().get("minecraft:cobblestone"));
    }
    @Test void facilityValuesUseThirtyPercentAndLevelMultipliers() {
        assertEquals(6,rules.value(TestRules.machine("cobblestone_generator",1,0)));
        assertEquals(9,rules.value(TestRules.machine("timber_generator",1,0)));
        assertEquals(21,rules.value(TestRules.machine("iron_generator",1,0)));
        assertEquals(18,rules.value(TestRules.machine("resource_collector",1,0)));
        assertEquals(84,rules.value(TestRules.machine("iron_generator",5,0)));
        assertEquals(0,rules.value(TestRules.machine("iron_generator",5,0).retirement(false)));
    }
}
