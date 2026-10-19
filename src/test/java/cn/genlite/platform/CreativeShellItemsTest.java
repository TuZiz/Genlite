package cn.genlite.platform;

import cn.genlite.domain.ShellCredentials;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CreativeShellItemsTest {
    private final UUID source=UUID.randomUUID(),token=UUID.randomUUID();
    private final ShellCredentials empty=new ShellCredentials(false,null,null);
    @Test void movedAndClonedSpentCopiesIncludingOffhandAndCursorAreRemovedOnce() {
        var slots=new ArrayList<>(Collections.nCopies(42,empty));
        var spent=new ShellCredentials(true,source,token);
        slots.set(3,spent);slots.set(40,spent);slots.set(41,spent);
        Set<Integer> cleared=new HashSet<>();
        CreativeShellItems.consume(slots.size(),slots::get,i -> {assertTrue(cleared.add(i));slots.set(i,empty);},source,token);
        assertEquals(Set.of(3,40,41),cleared);
        CreativeShellItems.consume(slots.size(),slots::get,i -> fail("already cleared"),source,token);
    }
    @Test void replacementHandOtherShellFreshTemplateAndMalformedItemAreUntouched() {
        var other=new ShellCredentials(true,UUID.randomUUID(),UUID.randomUUID());
        var nextToken=new ShellCredentials(true,source,UUID.randomUUID());
        var slots=List.of(other,empty,nextToken,ShellCredentials.parse(true,"bad","bad"));
        CreativeShellItems.consume(slots.size(),slots::get,i -> fail("unrelated item changed"),source,token);
    }
}
