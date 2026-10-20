package cn.genlite.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FuelStockTest {
    final Rules rules=TestRules.load();
    final ContainerStock stock=new ContainerStock(rules,(h,p) -> h.equals(TestRules.HOME)&&p.equals(TestRules.PLAYER));
    FuelStockTest() throws Exception {}
    ContainerStock.Source source(Machine m) {return new ContainerStock.Source(ContainerStock.Kind.FUEL,m.id(),rules.fuelItem(m));}
    WorldState.Draft draft(Machine m) {return new WorldState(1,Map.of(m.id(),m),Map.of()).draft();}
    @Test void depositConvertsWholeFuelToEnergyAndWithdrawKeepsConsumedRemainder() {
        var m=TestRules.machine("cobblestone_generator",1,3);var d=draft(m);
        stock.deposit(d,TestRules.PLAYER,source(m),5);
        assertEquals(3+5*rules.fuelUnits(m),d.machines.get(m.id()).fuel());
        var parcel=stock.reserve(d,TestRules.PLAYER,source(m),64);
        assertEquals(5,parcel.amount());assertEquals(3,d.machines.get(m.id()).fuel());
        stock.restore(d,source(m),parcel);stock.restore(d,source(m),parcel);
        assertEquals(3+5*rules.fuelUnits(m),d.machines.get(m.id()).fuel());
        StateValidation.check(d.freeze(),rules);
    }
    @Test void partiallyConsumedFuelLimitsCapacityWithoutRoundingUp() {
        var m=TestRules.machine("cobblestone_generator",1,rules.level(TestRules.machine("cobblestone_generator",1,0)).fuelItems()*8-9);
        assertEquals(1,rules.fuelSpace(m));var d=draft(m);
        stock.deposit(d,TestRules.PLAYER,source(m),1);
        assertEquals(0,rules.fuelSpace(d.machines.get(m.id())));
        var before=d.freeze();assertThrows(IllegalArgumentException.class,() -> stock.deposit(d,TestRules.PLAYER,source(m),1));
        assertEquals(before,d.freeze());
    }
    @Test void rejectsWrongFuelNonPositiveAmountsAndPermissionChangesWithoutMutation() {
        var m=TestRules.machine("iron_generator",1,0);var d=draft(m);var before=d.freeze();
        assertThrows(IllegalArgumentException.class,() -> stock.deposit(d,TestRules.PLAYER,new ContainerStock.Source(ContainerStock.Kind.FUEL,m.id(),"minecraft:coal"),1));
        assertThrows(IllegalArgumentException.class,() -> stock.deposit(d,TestRules.PLAYER,source(m),0));
        assertThrows(IllegalArgumentException.class,() -> stock.deposit(d,TestRules.PLAYER,source(m),-1));
        assertThrows(IllegalArgumentException.class,() -> stock.deposit(d,UUID.randomUUID(),source(m),1));
        assertEquals(before,d.freeze());
    }
    @Test void staleCapacitySecondDepositIsRejectedAndDoesNotOverfill() {
        var m=TestRules.machine("timber_generator",1,0);var d=draft(m);int capacity=rules.fuelSpace(m);
        stock.deposit(d,TestRules.PLAYER,source(m),capacity);var before=d.freeze();
        assertThrows(IllegalArgumentException.class,() -> stock.deposit(d,TestRules.PLAYER,source(m),capacity));assertEquals(before,d.freeze());
    }
    @Test void removedGeneratorAndCollectorCannotAcceptFuel() {
        var m=TestRules.machine("cobblestone_generator",1,0).retirement(false);var d=draft(m);var before=d.freeze();
        assertThrows(IllegalArgumentException.class,() -> stock.deposit(d,TestRules.PLAYER,source(m),1));assertEquals(before,d.freeze());
        var c=TestRules.machine("resource_collector",1,0);d.machines.put(c.id(),c);
        assertEquals(0,rules.fuelSpace(c));
        assertThrows(IllegalArgumentException.class,() -> stock.deposit(d,TestRules.PLAYER,new ContainerStock.Source(ContainerStock.Kind.FUEL,c.id(),"genlite:fuel"),1));
    }
}
