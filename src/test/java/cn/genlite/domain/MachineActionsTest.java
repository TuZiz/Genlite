package cn.genlite.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineActionsTest {
    final Rules rules;final MachineActions actions;
    MachineActionsTest() throws Exception {rules=TestRules.load();actions=new MachineActions(rules,(h,p) -> h.equals(TestRules.HOME)&&p.equals(TestRules.PLAYER));}
    @Test void packedMachineRetainsFuelAndInventoryAndTokenIsSingleUse() {
        Machine original=TestRules.machine("timber_generator",4,73).contents(73,0,12,Map.of());
        var d=new WorldState(1,Map.of(original.id(),original),Map.of()).draft();
        actions.dismantle(d,original.id(),TestRules.PLAYER);
        Machine packed=d.machines.get(original.id());assertTrue(packed.removed());assertEquals(73,packed.fuel());assertEquals(0,rules.value(packed));
        actions.cleared(d,packed.id());actions.cleared(d,packed.id());assertEquals(1,d.parcels.size());
        Parcel shell=d.parcels.values().iterator().next();assertEquals(packed.shellToken(),shell.shellToken());
        Machine restored=actions.unpack(d,TestRules.pos(2,64,2),TestRules.HOME,TestRules.PLAYER,original.type(),original.id(),shell.shellToken());
        assertEquals(4,restored.level());assertEquals(73,restored.fuel());assertEquals(0,restored.input());assertEquals(12,restored.buffer());
        assertThrows(IllegalArgumentException.class,() -> actions.unpack(d,TestRules.pos(3,64,3),TestRules.HOME,TestRules.PLAYER,original.type(),original.id(),shell.shellToken()));
        assertNull(d.machines.get(original.id()).shellToken());assertEquals(0,d.machines.get(original.id()).fuel());
    }
    @Test void collectorPackingPreservesThreeByNineContents() {
        Machine c=TestRules.machine("resource_collector",1,0).contents(0,0,0,Map.of("minecraft:raw_iron",1000));
        var d=new WorldState(1,Map.of(c.id(),c),Map.of()).draft();actions.dismantle(d,c.id(),TestRules.PLAYER);actions.cleared(d,c.id());
        Machine restored=actions.unpack(d,TestRules.pos(1,64,0),TestRules.HOME,TestRules.PLAYER,c.type(),c.id(),d.machines.get(c.id()).shellToken());
        assertEquals(1000,restored.inventory().get("minecraft:raw_iron"));assertEquals(27,rules.level(restored).collectorSlots());
    }
    @Test void concurrentDismantleRoutesCannotCreateTwoShellsAndNonMembersCannotDismantle() {
        Machine original=TestRules.machine("cobblestone_generator",2,37).contents(37,0,12,Map.of());
        var d=new WorldState(1,Map.of(original.id(),original),Map.of()).draft();
        assertThrows(IllegalArgumentException.class,() -> actions.dismantle(d,original.id(),UUID.randomUUID()));
        assertEquals(original,d.machines.get(original.id()));
        actions.dismantle(d,original.id(),TestRules.PLAYER);
        assertThrows(IllegalArgumentException.class,() -> actions.dismantle(d,original.id(),TestRules.PLAYER));
        actions.cleared(d,original.id());actions.cleared(d,original.id());
        assertEquals(1,d.parcels.size());assertEquals(37,d.machines.get(original.id()).fuel());assertEquals(12,d.machines.get(original.id()).buffer());
    }
    @Test void staleUpgradeAndUnauthorizedUseFailWithoutChangingState() {
        Machine m=TestRules.machine("cobblestone_generator",1,0);
        var d=new WorldState(1,Map.of(m.id(),m),Map.of()).draft();
        assertThrows(IllegalArgumentException.class,() -> actions.upgrade(d,m.id(),UUID.randomUUID(),1));
        assertEquals(m,d.machines.get(m.id()));
        actions.upgrade(d,m.id(),TestRules.PLAYER,1);
        assertEquals(2,d.machines.get(m.id()).level());assertEquals(2,d.machines.get(m.id()).revision());
        assertThrows(IllegalArgumentException.class,() -> actions.upgrade(d,m.id(),TestRules.PLAYER,1));
        assertEquals(2,d.machines.get(m.id()).level());
    }
    @Test void repeatedWithdrawDoesNotDuplicateAndOnlyWholeFuelItemsWithdraw() {
        Machine m=TestRules.machine("cobblestone_generator",1,13).contents(13,0,7,Map.of());
        var d=new WorldState(1,Map.of(m.id(),m),Map.of()).draft();
        actions.withdraw(d,m.id(),TestRules.PLAYER,null,"output");assertEquals(7,d.parcels.values().iterator().next().amount());
        assertThrows(IllegalArgumentException.class,() -> actions.withdraw(d,m.id(),TestRules.PLAYER,null,"output"));
        actions.withdraw(d,m.id(),TestRules.PLAYER,null,"fuel");assertEquals(5,d.machines.get(m.id()).fuel());assertEquals(2,d.parcels.size());
    }
    @Test void bindingFromEveryModeSelectsCollectorAndRoutesBufferedProducts() {
        for(OutputMode mode:OutputMode.values()) {
            Machine a=TestRules.machine("cobblestone_generator",1,13).contents(13,0,7,Map.of()).output(mode);
            Machine c=Machine.create(TestRules.pos(1,64,0),TestRules.HOME,TestRules.PLAYER,"resource_collector",1);
            var d=new WorldState(1,Map.of(a.id(),a,c.id(),c),Map.of()).draft();
            actions.bind(d,a.id(),c.id(),TestRules.PLAYER);
            var bound=d.machines.get(a.id());
            assertTrue(bound.connected());assertEquals(OutputMode.COLLECTOR,bound.mode());assertEquals(c.id(),bound.collector());
            assertEquals(13,bound.fuel());assertEquals(7,bound.buffer());
            Production.transfer(d,rules,Set.of(a.id(),c.id()));
            assertEquals(0,d.machines.get(a.id()).buffer());
            assertEquals(7,d.machines.get(c.id()).inventory().get(rules.types().get(a.type()).output()));
            assertTrue(d.parcels.isEmpty());assertTrue(d.warehouses.isEmpty());
        }
    }
    @Test void rebindSelectsNewCollectorAndFailedBindingPreservesState() {
        Machine a=TestRules.machine("cobblestone_generator",1,0);
        Machine c=Machine.create(TestRules.pos(1,64,0),TestRules.HOME,TestRules.PLAYER,"resource_collector",1);
        Machine next=Machine.create(TestRules.pos(2,64,0),TestRules.HOME,TestRules.PLAYER,"resource_collector",1);
        Machine far=Machine.create(TestRules.pos(1000,64,0),TestRules.HOME,TestRules.PLAYER,"resource_collector",1);
        var d=new WorldState(1,Map.of(a.id(),a,c.id(),c,next.id(),next,far.id(),far),Map.of()).draft();
        actions.bind(d,a.id(),c.id(),TestRules.PLAYER);
        actions.toggle(d,a.id(),TestRules.PLAYER,"mode");
        Machine before=d.machines.get(a.id());
        assertThrows(IllegalArgumentException.class,() -> actions.bind(d,a.id(),far.id(),TestRules.PLAYER));
        assertThrows(IllegalArgumentException.class,() -> actions.bind(d,a.id(),next.id(),UUID.randomUUID()));
        assertEquals(before,d.machines.get(a.id()));
        actions.bind(d,a.id(),next.id(),TestRules.PLAYER);
        assertEquals(next.id(),d.machines.get(a.id()).collector());
        assertEquals(OutputMode.COLLECTOR,d.machines.get(a.id()).mode());
        actions.toggle(d,a.id(),TestRules.PLAYER,"mode");
        actions.bind(d,a.id(),next.id(),TestRules.PLAYER);
        assertEquals(OutputMode.COLLECTOR,d.machines.get(a.id()).mode());
    }
    @Test void creativeFreshTemplateCanPlaceRepeatedlyWithoutCopyingFuelOrStock() {
        var d=WorldState.empty().draft();
        for(int x=0;x<3;x++) {
            Machine placed=actions.place(d,Machine.create(TestRules.pos(x,64,0),TestRules.HOME,TestRules.PLAYER,"cobblestone_generator",1));
            assertEquals(0,placed.fuel());assertEquals(0,placed.buffer());assertTrue(placed.inventory().isEmpty());
        }
        assertEquals(3,d.machines.size());assertTrue(d.parcels.isEmpty());
    }
    @Test void failedPackedPlacementKeepsCredentialAndContentsForRetry() {
        Machine packed=TestRules.machine("timber_generator",3,71).contents(71,0,12,Map.of()).retirement(true);
        Machine occupied=Machine.create(TestRules.pos(2,64,2),TestRules.HOME,TestRules.PLAYER,"iron_generator",1);
        var d=new WorldState(1,Map.of(packed.id(),packed,occupied.id(),occupied),Map.of()).draft();
        assertThrows(IllegalArgumentException.class,() -> actions.unpack(d,occupied.position(),TestRules.HOME,TestRules.PLAYER,packed.type(),packed.id(),packed.shellToken()));
        assertEquals(packed,d.machines.get(packed.id()));assertTrue(d.parcels.isEmpty());
        Machine restored=actions.unpack(d,TestRules.pos(3,64,3),TestRules.HOME,TestRules.PLAYER,packed.type(),packed.id(),packed.shellToken());
        assertEquals(71,restored.fuel());assertEquals(12,restored.buffer());
        assertThrows(IllegalArgumentException.class,() -> actions.unpack(d,TestRules.pos(4,64,4),TestRules.HOME,TestRules.PLAYER,packed.type(),packed.id(),packed.shellToken()));
    }
    @Test void clearReturnsExactlyTheNewOutboxDropForImmediateDispatch() {
        Machine m=TestRules.machine("iron_generator",3,79).contents(79,0,11,Map.of());
        var d=new WorldState(1,Map.of(m.id(),m),Map.of()).draft();
        assertNull(actions.cleared(d,m.id()));assertTrue(d.parcels.isEmpty());
        actions.dismantle(d,m.id(),TestRules.PLAYER);
        Parcel drop=actions.cleared(d,m.id());
        assertNotNull(drop);assertEquals(drop,d.parcels.get(drop.id()));assertEquals(m.id(),drop.shellSource());
        assertEquals(m.position(),drop.dropPosition());assertEquals("WAITING",drop.phase());
        assertNull(actions.cleared(d,m.id()));assertEquals(1,d.parcels.size());
        assertEquals(79,d.machines.get(m.id()).fuel());assertEquals(11,d.machines.get(m.id()).buffer());
    }
}
