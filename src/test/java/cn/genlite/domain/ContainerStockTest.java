package cn.genlite.domain;

import cn.genlite.storage.AtomicStateFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContainerStockTest {
    @TempDir Path directory;
    final UUID home=TestRules.HOME,player=TestRules.PLAYER;
    final Rules rules;
    final ContainerStock stock;
    ContainerStockTest() throws Exception {rules=new Rules(TestRules.load().types(),9);stock=new ContainerStock(rules,(h,p) -> h.equals(home)&&p.equals(player));}
    private ContainerStock.Source warehouse(String item) {return new ContainerStock.Source(ContainerStock.Kind.WAREHOUSE,home,item);}
    @Test void fourClickQuantitiesReserveDirectSendsWithoutAClaimStep() {
        var d=WorldState.empty().draft();BigInteger initial=BigInteger.TEN.pow(50);d.warehouses.put(home,Map.of("minecraft:iron_ingot",initial));
        int total=0;
        for(int requested:new int[]{1,10,64,2304}) {
            Parcel p=stock.reserve(d,player,warehouse("minecraft:iron_ingot"),requested);
            assertEquals(requested,p.amount());assertEquals("SENDING",p.phase());total+=requested;
        }
        assertEquals(initial.subtract(BigInteger.valueOf(total)),d.warehouses.get(home).get("minecraft:iron_ingot"));
    }
    @Test void arbitraryPrecisionCountsSurviveDiskRoundTrip() throws Exception {
        var d=WorldState.empty().draft();BigInteger n=BigInteger.TEN.pow(90).add(BigInteger.valueOf(37));
        d.warehouses.put(home,Map.of("minecraft:cobblestone",n));var file=new AtomicStateFile(directory.resolve("machines.json"));
        file.write(d.freeze());var restored=file.read();assertEquals(n,restored.warehouses().get(home).get("minecraft:cobblestone"));StateValidation.check(restored,rules);
    }
    @Test void oldIntegerWarehouseNumbersReadExactlyWithoutResetting() throws Exception {
        Path file=directory.resolve("machines.json");Files.writeString(file,"{\"schemaVersion\":1,\"machines\":{},\"parcels\":{},\"warehouses\":{\""+home+"\":{\"minecraft:iron_ingot\":2147483647}}}");
        var state=new AtomicStateFile(file).read();assertEquals(new BigInteger("2147483647"),state.warehouses().get(home).get("minecraft:iron_ingot"));
    }
    @Test void unauthorizedRequestsAndZeroBackpackCapacityNeverChangeStock() {
        var d=WorldState.empty().draft();d.warehouses.put(home,Map.of("minecraft:iron_ingot",BigInteger.TEN));var before=d.freeze();
        assertThrows(IllegalArgumentException.class,() -> stock.reserve(d,UUID.randomUUID(),warehouse("minecraft:iron_ingot"),1));
        assertThrows(IllegalArgumentException.class,() -> stock.reserve(d,player,warehouse("minecraft:iron_ingot"),0));assertEquals(before,d.freeze());
    }
    @Test void canceledWarehouseDeliveryReturnsExactlyOnce() {
        var d=WorldState.empty().draft();d.warehouses.put(home,Map.of("minecraft:iron_ingot",BigInteger.valueOf(100)));
        Parcel p=stock.reserve(d,player,warehouse("minecraft:iron_ingot"),10);stock.restore(d,warehouse("minecraft:iron_ingot"),p);stock.restore(d,warehouse("minecraft:iron_ingot"),p);
        assertEquals(BigInteger.valueOf(100),d.warehouses.get(home).get("minecraft:iron_ingot"));assertTrue(d.parcels.isEmpty());
    }
    @Test void collectorSpaceConsumedDuringDeliveryFailureUsesAutomaticEscrow() {
        Machine m=TestRules.machine("resource_collector",1,0).contents(0,0,0,Map.of("minecraft:iron_ingot",64));
        var d=new WorldState(1,Map.of(m.id(),m),Map.of()).draft();var source=new ContainerStock.Source(ContainerStock.Kind.COLLECTOR,m.id(),"minecraft:iron_ingot");
        Parcel p=stock.reserve(d,player,source,64);
        d.machines.put(m.id(),m.contents(0,0,0,Map.of("minecraft:cobblestone",27*64)));stock.restore(d,source,p);stock.restore(d,source,p);
        assertEquals("WAITING",d.parcels.get(p.id()).phase());assertEquals(27*64,d.machines.get(m.id()).inventory().get("minecraft:cobblestone"));
        Parcel retry=PendingParcels.reserve(d,p.id(),10);assertEquals(10,retry.amount());assertEquals("SENDING",retry.phase());assertEquals(54,d.parcels.get(p.id()).amount());
    }
    @Test void warehouseSlotsLimitKindsButNeverExistingStackCounts() {
        var d=WorldState.empty().draft();var types=new HashMap<String,BigInteger>();
        for(int n=0;n<9;n++) types.put("minecraft:resource_"+n,BigInteger.ONE);d.warehouses.put(home,Map.copyOf(types));
        stock.deposit(d,player,warehouse("minecraft:resource_0"),2000);assertEquals(BigInteger.valueOf(2001),d.warehouses.get(home).get("minecraft:resource_0"));
        assertThrows(IllegalArgumentException.class,() -> stock.deposit(d,player,warehouse("minecraft:iron_ingot"),1));
    }
    @Test void pendingEscrowSplitsWithoutDuplicatingItsRemainder() {
        var d=WorldState.empty().draft();var p=Parcel.waiting(player,"minecraft:iron_ingot",200,0);d.parcels.put(p.id(),p);
        Parcel sent=PendingParcels.reserve(d,p.id(),64);assertEquals(136,d.parcels.get(p.id()).amount());assertEquals(64,d.parcels.get(sent.id()).amount());
        assertThrows(IllegalArgumentException.class,() -> PendingParcels.reserve(d,sent.id(),64));
        assertEquals(200,d.parcels.values().stream().mapToInt(Parcel::amount).sum());
    }
}
