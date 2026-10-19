package cn.genlite.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectorStacksTest {
    @Test void normalStacksSplitAt64InsteadOfAggregatingOneIcon() {
        var visible=CollectorStacks.visible(Map.of("minecraft:cobblestone",130));
        assertEquals(List.of(64,64,2),visible.stream().map(CollectorStacks.Stack::amount).toList());
    }
    @Test void storageHas27StacksAndNeverUsesCenterForAControl() {
        var visible=CollectorStacks.visible(Map.of("minecraft:cobblestone",27*64));
        assertEquals(27,visible.size());assertEquals(64,visible.get(13).amount());
        assertTrue(visible.stream().allMatch(s -> s.amount()>0 && s.amount()<=64));
    }
    @Test void legacyOverflowIsNotDeletedAndIsRevealedAfterWithdrawal() throws Exception {
        var rules=TestRules.load();Machine m=TestRules.machine("resource_collector",1,0).contents(0,0,0,Map.of("minecraft:cobblestone",30*64));
        var d=WorldState.empty().draft();d.machines.put(m.id(),m);StateValidation.check(d.freeze(),rules);
        assertEquals(27,CollectorStacks.visible(m.inventory()).size());assertTrue(rules.capacityFor(m.inventory(),"minecraft:cobblestone",27)<0);
        var action=new ContainerStock(rules,(home,user)->true);
        action.reserve(d,TestRules.PLAYER,new ContainerStock.Source(ContainerStock.Kind.COLLECTOR,m.id(),"minecraft:cobblestone"),64);
        assertEquals(29*64,d.machines.get(m.id()).inventory().get("minecraft:cobblestone"));
        assertEquals(27,CollectorStacks.visible(d.machines.get(m.id()).inventory()).size());
    }
    @Test void extractingOneDisplayedStackDoesNotRemoveTheNextStack() throws Exception {
        var rules=TestRules.load();Machine m=TestRules.machine("resource_collector",1,0).contents(0,0,0,Map.of("minecraft:cobblestone",128));
        var d=WorldState.empty().draft();d.machines.put(m.id(),m);
        var parcel=new ContainerStock(rules,(home,user)->true).reserve(d,TestRules.PLAYER,new ContainerStock.Source(ContainerStock.Kind.COLLECTOR,m.id(),"minecraft:cobblestone"),CollectorStacks.visible(m.inventory()).getFirst().amount());
        assertEquals(64,parcel.amount());assertEquals(64,d.machines.get(m.id()).inventory().get("minecraft:cobblestone"));
    }
}
