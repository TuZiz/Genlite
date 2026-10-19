package cn.genlite.domain;

import cn.genlite.storage.AtomicStateFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectorTransferTest {
    @TempDir Path root;
    final Rules rules;
    CollectorTransferTest() throws Exception {rules=TestRules.load();}
    Machine generator(Machine c) {
        return Machine.create(TestRules.pos(1,64,0),TestRules.HOME,TestRules.PLAYER,"cobblestone_generator",1)
                .contents(0,0,10,Map.of()).settings(1,false,false,true,c.id());
    }
    @Test void partialTransferReportsActualAmountAndDirectionOnlyOnce() {
        Machine c=TestRules.machine("resource_collector",1,0).contents(0,0,0,Map.of("minecraft:cobblestone",27*64-3));
        Machine g=generator(c);var draft=new WorldState(1,Map.of(g.id(),g,c.id(),c),Map.of()).draft();
        var active=Set.of(g.id(),c.id());
        assertEquals(List.of(new CollectorTransfer(g.id(),c.id(),g.position(),c.position(),3)),Production.transfer(draft,rules,active));
        assertEquals(7,draft.machines.get(g.id()).buffer());
        assertTrue(Production.transfer(draft,rules,active).isEmpty());
    }
    @Test void blockedOrOtherRoutesNeverProduceCollectorReceipts() {
        Machine c=TestRules.machine("resource_collector",1,0);Machine g=generator(c);
        for(Machine source:List.of(g.settings(1,true,false,true,c.id()),g.output(OutputMode.DROP),g.output(OutputMode.WAREHOUSE))) {
            var d=new WorldState(1,Map.of(source.id(),source,c.id(),c),Map.of()).draft();
            assertTrue(Production.transfer(d,rules,Set.of(source.id(),c.id())).isEmpty());
        }
        for(Machine target:List.of(c.settings(1,true,false,false,null),c.retirement(false),
                c.contents(0,0,0,Map.of("minecraft:cobblestone",27*64)))) {
            var d=new WorldState(1,Map.of(g.id(),g,target.id(),target),Map.of()).draft();
            assertTrue(Production.transfer(d,rules,Set.of(g.id(),target.id())).isEmpty());
        }
        var d=new WorldState(1,Map.of(g.id(),g,c.id(),c),Map.of()).draft();
        assertTrue(Production.transfer(d,rules,Set.of(g.id())).isEmpty());
        d.machines.remove(c.id());assertTrue(Production.transfer(d,rules,Set.of(g.id())).isEmpty());
    }
    @Test void durableCommitPublishesReceiptsButFailedCommitDoesNot() throws Exception {
        for(boolean fail:List.of(false,true)) {
            Path file=root.resolve(fail?"failed.json":"success.json");
            Machine c=TestRules.machine("resource_collector",1,0);Machine g=generator(c);
            var storage=new AtomicStateFile(file);storage.read();storage.write(new WorldState(1,Map.of(g.id(),g,c.id(),c),Map.of()));
            Logger logger=Logger.getAnonymousLogger();logger.setLevel(Level.OFF);
            MachineEngine engine=new MachineEngine(storage,rules,logger);
            try {
                engine.start().get(5,TimeUnit.SECONDS);engine.lease(g.id(),true);engine.lease(c.id(),true);
                if(fail) Files.createDirectory(file.resolveSibling(file.getFileName()+".tmp"));
                // Run the production tick on its real state/IO owner without waiting for wall-clock production.
                var field=MachineEngine.class.getDeclaredField("worker");field.setAccessible(true);
                var tick=MachineEngine.class.getDeclaredMethod("tick");tick.setAccessible(true);
                ((ScheduledExecutorService)field.get(engine)).submit(() -> {try {tick.invoke(engine);} catch(Exception e) {throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);
                var receipts=engine.drainCollectorTransfers();
                assertEquals(fail?0:1,receipts.size());
                assertEquals(fail?10:0,engine.snapshot().machines().get(g.id()).buffer());
                assertEquals(engine.snapshot(),storage.read());
                assertTrue(engine.drainCollectorTransfers().isEmpty());
                assertEquals(!fail,engine.ready());
            } finally {engine.close();}
            assertTrue(engine.drainCollectorTransfers().isEmpty());
        }
    }
}
