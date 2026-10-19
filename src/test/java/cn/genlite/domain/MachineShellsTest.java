package cn.genlite.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineShellsTest {
    @Test void preflightIsReadOnlyAndTransactionStillRejectsReuseAfterSnapshotBecomesStale() throws Exception {
        Machine packed=TestRules.machine("timber_generator",3,71).contents(71,0,12,Map.of()).retirement(true);
        WorldState state=new WorldState(1,Map.of(packed.id(),packed),Map.of());
        assertNull(MachineShells.rejection(state.machines(),packed.type(),packed.id(),packed.shellToken()));
        assertEquals(packed,state.machines().get(packed.id()));
        var actions=new MachineActions(TestRules.load(),(home,user) -> true);var draft=state.draft();
        Machine placed=actions.unpack(draft,TestRules.pos(2,64,2),packed.home(),packed.owner(),packed.type(),packed.id(),packed.shellToken());
        assertEquals(71,placed.fuel());assertEquals(12,placed.buffer());
        assertEquals("already-used",MachineShells.rejection(draft.machines,packed.type(),packed.id(),packed.shellToken()));
        assertThrows(IllegalArgumentException.class,() -> actions.unpack(draft,TestRules.pos(3,64,3),packed.home(),packed.owner(),packed.type(),packed.id(),packed.shellToken()));
        assertEquals(1,draft.machines.values().stream().filter(m -> !m.removed()).count());
        assertTrue(draft.parcels.isEmpty());
    }
    @Test void rejectionExplainsMissingIncompleteMismatchedAndConsumedShells() {
        Machine original=TestRules.machine("cobblestone_generator",1,5),packed=original.retirement(true);
        var stored=Map.of(packed.id(),packed);
        assertEquals("missing-source",MachineShells.rejection(stored,packed.type(),null,packed.shellToken()));
        assertEquals("unknown-source",MachineShells.rejection(stored,packed.type(),UUID.randomUUID(),packed.shellToken()));
        assertEquals("missing-token",MachineShells.rejection(stored,packed.type(),packed.id(),null));
        assertEquals("token-mismatch",MachineShells.rejection(stored,packed.type(),packed.id(),UUID.randomUUID()));
        assertEquals("type-mismatch",MachineShells.rejection(stored,"iron_generator",packed.id(),packed.shellToken()));
        assertEquals("not-dismantled",MachineShells.rejection(Map.of(original.id(),original),original.type(),original.id(),packed.shellToken()));
        Machine clearing=original.retirement(false);
        assertEquals("not-cleared",MachineShells.rejection(Map.of(clearing.id(),clearing),clearing.type(),clearing.id(),clearing.shellToken()));
        assertEquals("already-used",MachineShells.rejection(Map.of(packed.id(),packed.consumeShell()),packed.type(),packed.id(),packed.shellToken()));
        assertEquals("invalid-shell",assertThrows(IllegalArgumentException.class,() -> MachineShells.require(stored,packed.type(),packed.id(),null)).getMessage());
    }
}
