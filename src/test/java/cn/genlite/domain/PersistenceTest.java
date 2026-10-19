package cn.genlite.domain;

import cn.genlite.storage.AtomicStateFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path root;
    @Test void roundTripRetainsMachineIdentityOutboxAndPackedFuel() throws Exception {
        Rules rules=TestRules.load();Machine m=TestRules.machine("iron_generator",5,99).retirement(true);
        Parcel parcel=Parcel.shell(m);
        WorldState original=new WorldState(1,Map.of(m.id(),m),Map.of(parcel.id(),parcel.sending()));
        AtomicStateFile file=new AtomicStateFile(root.resolve("machines.json"));file.read();file.write(original);
        WorldState restored=file.read();StateValidation.check(restored,rules);assertEquals(original,restored);
        assertEquals("SENDING",restored.parcels().get(parcel.id()).phase());
    }
    @Test void corruptFileAndFutureSchemaNeverResetToEmpty() throws Exception {
        Path file=root.resolve("machines.json");Files.writeString(file,"broken");
        assertThrows(Exception.class,() -> new AtomicStateFile(file).read());assertEquals("broken",Files.readString(file));
        Files.writeString(file,"{\"schemaVersion\":99,\"machines\":{},\"parcels\":{}}");
        assertThrows(Exception.class,() -> new AtomicStateFile(file).read());assertTrue(Files.readString(file).contains("99"));
    }
    @Test void concurrentTransactionsSerializeAndPublishAfterDiskCommit() throws Exception {
        Rules rules=TestRules.load();Path path=root.resolve("machines.json");
        MachineEngine engine=new MachineEngine(new AtomicStateFile(path),rules,Logger.getAnonymousLogger());
        try {
            engine.start().get(5,TimeUnit.SECONDS);
            Machine m=TestRules.machine("cobblestone_generator",1,0);
            engine.transact(d -> {d.machines.put(m.id(),m);return null;}).get(5,TimeUnit.SECONDS);
            List<CompletableFuture<Void>> writes=new ArrayList<>();
            for(int n=0;n<20;n++) writes.add(engine.transact(d -> {Machine old=d.machines.get(m.id());d.machines.put(m.id(),old.contents(old.fuel()+1,0,0,Map.of()));return null;}));
            CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).get(5,TimeUnit.SECONDS);
            assertEquals(20,engine.snapshot().machines().get(m.id()).fuel());
            assertEquals(engine.snapshot(),new AtomicStateFile(path).read());
        } finally {engine.close();}
    }
    @Test void failedSavePreservesOriginalAndFreezesProduction() throws Exception {
        Rules rules=TestRules.load();Path path=root.resolve("machines.json");
        Logger log=Logger.getAnonymousLogger();log.setLevel(java.util.logging.Level.OFF);
        MachineEngine engine=new MachineEngine(new AtomicStateFile(path),rules,log);
        try {
            engine.start().get(5,TimeUnit.SECONDS);Machine m=TestRules.machine("cobblestone_generator",1,8);
            engine.transact(d -> {d.machines.put(m.id(),m);return null;}).get(5,TimeUnit.SECONDS);
            String original=Files.readString(path);Files.createDirectory(root.resolve("machines.json.tmp"));
            assertThrows(ExecutionException.class,() -> engine.transact(d -> {d.machines.put(m.id(),m.contents(16,0,0,Map.of()));return null;}).get(5,TimeUnit.SECONDS));
            assertEquals(original,Files.readString(path));assertEquals(8,engine.snapshot().machines().get(m.id()).fuel());assertFalse(engine.ready());
        } finally {engine.close();}
    }
    @Test void requestsAfterDisableFailAsFuturesWithoutApplyingChanges() throws Exception {
        MachineEngine engine=new MachineEngine(new AtomicStateFile(root.resolve("machines.json")),TestRules.load(),Logger.getAnonymousLogger());
        engine.start().get(5,TimeUnit.SECONDS);engine.close();
        CompletableFuture<Void> result=engine.transact(d -> {fail("disabled mutation must never execute");return null;});
        assertThrows(ExecutionException.class,() -> result.get(5,TimeUnit.SECONDS));
    }
    @Test void retirementCallbacksReturnADurableDropWithoutWaitingForPeriodicProduction() throws Exception {
        Path path=root.resolve("machines.json");Rules rules=TestRules.load();
        MachineEngine engine=new MachineEngine(new AtomicStateFile(path),rules,Logger.getAnonymousLogger());
        var actions=new MachineActions(rules,(h,p) -> true);
        try {
            engine.start().get(5,TimeUnit.SECONDS);
            Machine m=TestRules.machine("resource_collector",2,0).contents(0,0,0,Map.of("minecraft:iron_ingot",129));
            engine.transact(d -> actions.place(d,m)).get(5,TimeUnit.SECONDS);
            Parcel drop=engine.transact(d -> {actions.dismantle(d,m.id(),m.owner());return null;})
                    .thenCompose(v -> engine.transact(d -> actions.cleared(d,m.id()))).get(5,TimeUnit.SECONDS);
            WorldState disk=new AtomicStateFile(path).read();
            assertNotNull(drop);assertEquals(drop,disk.parcels().get(drop.id()));
            assertTrue(disk.machines().get(m.id()).cleared());assertEquals(129,disk.machines().get(m.id()).inventory().get("minecraft:iron_ingot"));
            assertNull(engine.transact(d -> actions.cleared(d,m.id())).get(5,TimeUnit.SECONDS));
            assertEquals(1,engine.snapshot().parcels().size());
        } finally {engine.close();}
    }
    @Test void failedRetirementSaveDoesNotRemoveMachineOrPublishAnOutboxDrop() throws Exception {
        Path path=root.resolve("machines.json");Rules rules=TestRules.load();
        Logger logger=Logger.getAnonymousLogger();logger.setLevel(java.util.logging.Level.OFF);
        MachineEngine engine=new MachineEngine(new AtomicStateFile(path),rules,logger);
        var actions=new MachineActions(rules,(h,p) -> true);
        try {
            engine.start().get(5,TimeUnit.SECONDS);Machine m=TestRules.machine("timber_generator",2,39);
            engine.transact(d -> actions.place(d,m)).get(5,TimeUnit.SECONDS);
            String original=Files.readString(path);Files.createDirectory(root.resolve("machines.json.tmp"));
            var retirement=engine.transact(d -> {actions.dismantle(d,m.id(),m.owner());return null;})
                    .thenCompose(v -> engine.transact(d -> {fail("cleanup must not run after failed retirement commit");return null;}));
            assertThrows(ExecutionException.class,() -> retirement.get(5,TimeUnit.SECONDS));
            assertEquals(m,engine.snapshot().machines().get(m.id()));assertTrue(engine.snapshot().parcels().isEmpty());
            assertEquals(original,Files.readString(path));
        } finally {engine.close();}
    }
}
