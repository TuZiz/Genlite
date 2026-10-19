package cn.genlite.domain;

import cn.genlite.storage.AtomicStateFile;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class OutputRoutingTest {
    @TempDir Path directory;
    final Rules rules;
    OutputRoutingTest() throws Exception {rules=new Rules(TestRules.load().types(),9);}
    private WorldState.Draft state(Machine... machines) {
        var map=new HashMap<UUID,Machine>();for(var m:machines) map.put(m.id(),m);
        return new WorldState(1,map,Map.of()).draft();
    }
    @Test void defaultProductionBecomesPhysicalDropAndDeductsFuelOnce() {
        var m=TestRules.machine("cobblestone_generator",1,8);assertEquals(OutputMode.DROP,m.mode());
        var produced=Production.generate(m,rules);var d=state(produced);
        Production.transfer(d,rules,Set.of(m.id()));
        var drop=d.parcels.values().iterator().next();assertEquals(m.position(),drop.dropPosition());
        assertNull(drop.shellSource());assertEquals("minecraft:cobblestone",drop.item());assertEquals(1,drop.amount());
        assertEquals(0,d.machines.get(m.id()).buffer());assertEquals(7,d.machines.get(m.id()).fuel());
        Production.transfer(d,rules,Set.of(m.id()));assertEquals(1,d.parcels.size());
    }
    @Test void menuCyclesThreeModesAndOtherSettingsKeepWarehouseMode() {
        var m=TestRules.machine("iron_generator",1,8);
        var c=Machine.create(TestRules.pos(1,64,0),m.home(),m.owner(),"resource_collector",1);
        m=m.settings(1,false,false,false,c.id());var d=state(m,c);
        var actions=new MachineActions(rules,(h,p) -> true);
        actions.toggle(d,m.id(),m.owner(),"mode");assertEquals(OutputMode.COLLECTOR,d.machines.get(m.id()).mode());
        actions.toggle(d,m.id(),m.owner(),"mode");assertEquals(OutputMode.WAREHOUSE,d.machines.get(m.id()).mode());
        actions.toggle(d,m.id(),m.owner(),"pause");assertEquals(OutputMode.WAREHOUSE,d.machines.get(m.id()).mode());
        actions.toggle(d,m.id(),m.owner(),"mode");assertEquals(OutputMode.DROP,d.machines.get(m.id()).mode());
    }
    @Test void switchingRequiresAnExistingCollectorInRangeAndNeverChangesModeOnRejection() {
        var generator=TestRules.machine("iron_generator",1,8);
        var collector=Machine.create(TestRules.pos(1,64,0),generator.home(),generator.owner(),"resource_collector",1);
        var actions=new MachineActions(rules,(h,p) -> true);
        for (OutputMode mode:OutputMode.values()) {
            var m=generator.output(mode);var d=state(m);
            assertEquals("collector-required",assertThrows(IllegalArgumentException.class,
                    () -> actions.toggle(d,m.id(),m.owner(),"mode")).getMessage());
            assertEquals(mode,d.machines.get(m.id()).mode());
        }
        var bound=generator.settings(1,false,false,false,collector.id());
        for (Machine target:List.of(collector.retirement(false),
                Machine.create(TestRules.pos(1,64,0),generator.home(),generator.owner(),"iron_generator",1),
                Machine.create(TestRules.pos(40,64,0),generator.home(),generator.owner(),"resource_collector",1))) {
            var m=bound.settings(1,false,false,false,target.id());var d=state(m,target);
            assertEquals("collector-required",assertThrows(IllegalArgumentException.class,
                    () -> actions.toggle(d,m.id(),m.owner(),"mode")).getMessage());
            assertEquals(OutputMode.DROP,d.machines.get(m.id()).mode());
        }
        var d=state(bound);
        assertThrows(IllegalArgumentException.class,() -> actions.toggle(d,bound.id(),bound.owner(),"mode"));
    }
    @Test void warehouseRoutesWithoutWorldItemsOrCollectorAndIsolatesIslands() {
        var a=TestRules.machine("iron_generator",1,8).contents(8,0,5,Map.of()).output(OutputMode.WAREHOUSE);
        var b=Machine.create(TestRules.pos(2,64,0),UUID.randomUUID(),TestRules.PLAYER,"iron_generator",1)
                .contents(8,0,7,Map.of()).output(OutputMode.WAREHOUSE);
        var d=state(a,b);Production.transfer(d,rules,Set.of(a.id(),b.id()));
        assertEquals(BigInteger.valueOf(5),d.warehouses.get(a.home()).get("minecraft:iron_ingot"));assertEquals(BigInteger.valueOf(7),d.warehouses.get(b.home()).get("minecraft:iron_ingot"));
        assertTrue(d.parcels.isEmpty());assertEquals(0,d.machines.get(a.id()).buffer());
    }
    @Test void competingWarehouseTransfersAccumulateBeyondNormalStacks() {
        var a=TestRules.machine("cobblestone_generator",1,8).contents(8,0,4,Map.of()).output(OutputMode.WAREHOUSE);
        var b=Machine.create(TestRules.pos(1,64,0),a.home(),a.owner(),a.type(),1).contents(8,0,4,Map.of()).output(OutputMode.WAREHOUSE);
        var d=state(a,b);d.warehouses.put(a.home(),Map.of("minecraft:cobblestone",BigInteger.valueOf(9*64-3)));
        Production.transfer(d,rules,Set.of(a.id(),b.id()));assertEquals(BigInteger.valueOf(9*64+5),d.warehouses.get(a.home()).get("minecraft:cobblestone"));
        assertEquals(0,d.machines.get(a.id()).buffer()+d.machines.get(b.id()).buffer());
        assertEquals("running",Production.status(d.machines.get(a.id()),rules,d.freeze(),Set.of(a.id(),b.id())));
    }
    @Test void delayedDropBacklogIsBoundedAndStopsProduction() {
        var m=TestRules.machine("cobblestone_generator",1,8);var d=state(m);
        for(int n=0;n<8;n++) {var p=Parcel.production(m,"minecraft:cobblestone",1);d.parcels.put(p.id(),p);}
        d.machines.put(m.id(),m.contents(8,0,3,Map.of()));Production.transfer(d,rules,Set.of(m.id()));
        assertEquals(8,d.parcels.size());assertEquals(3,d.machines.get(m.id()).buffer());
        assertEquals("paused",Production.status(m,rules,d.freeze(),Set.of(m.id())));
    }
    @Test void warehouseWithdrawalRechecksMembershipAndCannotDuplicate() {
        var home=UUID.randomUUID();var player=UUID.randomUUID();var d=WorldState.empty().draft();
        d.warehouses.put(home,Map.of("minecraft:raw_iron",BigInteger.valueOf(5)));
        var actions=new ContainerStock(rules,(h,p) -> h.equals(home)&&p.equals(player));
        var source=new ContainerStock.Source(ContainerStock.Kind.WAREHOUSE,home,"minecraft:raw_iron");
        assertThrows(IllegalArgumentException.class,() -> actions.reserve(d,UUID.randomUUID(),source,64));
        actions.reserve(d,player,source,64);assertTrue(d.warehouses.get(home).isEmpty());
        assertEquals(5,d.parcels.values().iterator().next().amount());
        assertThrows(IllegalArgumentException.class,() -> actions.reserve(d,player,source,64));
        assertEquals(1,d.parcels.size());
    }
    @Test void legacyConnectedStateMigratesToCollectorOrDropWithoutLosingContents() throws Exception {
        var a=TestRules.machine("iron_generator",3,71).contents(71,0,9,Map.of());
        var b=Machine.create(TestRules.pos(1,64,0),a.home(),a.owner(),a.type(),2).output(OutputMode.COLLECTOR);
        var json=new Gson().toJsonTree(new WorldState(1,Map.of(a.id(),a,b.id(),b),Map.of())).getAsJsonObject();
        json.remove("warehouses");
        for(var entry:json.getAsJsonObject("machines").entrySet()) entry.getValue().getAsJsonObject().remove("outputMode");
        Path path=directory.resolve("machines.json");Files.writeString(path,json.toString());
        var file=new AtomicStateFile(path);var state=file.read();StateValidation.check(state,rules);
        assertEquals(OutputMode.DROP,state.machines().get(a.id()).mode());assertEquals(OutputMode.COLLECTOR,state.machines().get(b.id()).mode());
        assertEquals(71,state.machines().get(a.id()).fuel());assertEquals(9,state.machines().get(a.id()).buffer());assertTrue(state.warehouses().isEmpty());
    }
    @Test void warehouseAndOutputModeSurviveAtomicRoundTrip() throws Exception {
        var m=TestRules.machine("iron_generator",3,71).output(OutputMode.WAREHOUSE);var d=state(m);
        d.warehouses.put(m.home(),Map.of("minecraft:raw_iron",BigInteger.valueOf(23)));var file=new AtomicStateFile(directory.resolve("machines.json"));
        file.write(d.freeze());var saved=file.read();assertEquals(d.freeze(),saved);
        assertEquals(OutputMode.WAREHOUSE,saved.machines().get(m.id()).mode());assertEquals(BigInteger.valueOf(23),saved.warehouses().get(m.home()).get("minecraft:raw_iron"));
    }
}
