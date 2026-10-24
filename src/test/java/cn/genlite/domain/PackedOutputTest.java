package cn.genlite.domain;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PackedOutputTest {
    private final Rules rules=TestRules.load();
    private final MachineActions actions=new MachineActions(rules,(h,p) -> true);
    PackedOutputTest() throws Exception {}
    private Machine restore(WorldState.Draft draft,Machine generator,Position position,UUID home) {
        actions.dismantle(draft,generator.id(),generator.owner());
        actions.cleared(draft,generator.id());
        // Packed state remains authoritative after serialization, not the shell's visible text.
        var gson=new Gson();
        var packed=gson.fromJson(gson.toJson(draft.machines.get(generator.id())),Machine.class);
        draft.machines.put(packed.id(),packed);
        Machine restored=actions.unpack(draft,position,home,generator.owner(),packed.type(),packed.id(),packed.shellToken());
        assertThrows(IllegalArgumentException.class,() -> actions.unpack(draft,TestRules.pos(3,64,3),home,generator.owner(),packed.type(),packed.id(),packed.shellToken()));
        return restored;
    }
    @Test void everyModeAndBindingSurvivePackingAndRestoring() {
        for(OutputMode mode:OutputMode.values()) {
            Machine collector=TestRules.machine("resource_collector",1,0);
            Machine original=TestRules.machine("cobblestone_generator",2,35).contents(35,0,7,Map.of())
                    .settings(2,false,false,false,collector.id()).output(mode);
            var draft=new WorldState(1,Map.of(original.id(),original,collector.id(),collector),Map.of()).draft();
            Machine restored=restore(draft,original,TestRules.pos(1,64,0),original.home());
            assertEquals(mode,restored.mode());assertEquals(mode==OutputMode.COLLECTOR,restored.connected());
            assertEquals(collector.id(),restored.collector());assertEquals(35,restored.fuel());assertEquals(7,restored.buffer());
            if(mode==OutputMode.COLLECTOR) {
                Production.transfer(draft,rules,Set.of(restored.id(),collector.id()));
                assertEquals(7,draft.machines.get(collector.id()).inventory().get("minecraft:cobblestone"));
                assertEquals(0,draft.machines.get(restored.id()).buffer());
            }
        }
    }
    @Test void unavailableCollectorKeepsBindingButCannotReceiveResources() {
        for(String scenario:List.of("range","world","home","removed","missing")) {
            Machine collector=TestRules.machine("resource_collector",1,0);
            Machine original=TestRules.machine("cobblestone_generator",1,8).contents(8,0,7,Map.of())
                    .settings(1,false,false,true,collector.id());
            var draft=new WorldState(1,Map.of(original.id(),original,collector.id(),collector),Map.of()).draft();
            Position pos=scenario.equals("range")?TestRules.pos(1000,64,0):scenario.equals("world")?
                    new Position(UUID.randomUUID(),"other",1,64,0):TestRules.pos(1,64,0);
            Machine restored=restore(draft,original,pos,scenario.equals("home")?UUID.randomUUID():original.home());
            if(scenario.equals("removed")) draft.machines.put(collector.id(),collector.retirement(true));
            if(scenario.equals("missing")) draft.machines.remove(collector.id());
            assertEquals(collector.id(),restored.collector());assertEquals(OutputMode.COLLECTOR,restored.mode());
            Production.transfer(draft,rules,Set.of(restored.id(),collector.id()));
            assertEquals(7,draft.machines.get(restored.id()).buffer());
            assertEquals("paused",Production.status(restored,rules,draft.machines,Set.of(restored.id(),collector.id())));
        }
    }
}
