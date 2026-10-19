package cn.genlite.domain;

import cn.genlite.storage.AtomicStateFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HomeHologramSettingsTest {
    @TempDir Path directory;
    @Test void defaultsEnabledAndSettingsAreIndependentForEachHome() {
        UUID home=UUID.randomUUID(),other=UUID.randomUUID(),owner=UUID.randomUUID();
        var draft=WorldState.empty().draft();
        assertTrue(draft.freeze().hologramsEnabled(home));
        assertFalse(HomeHologramSettings.toggle(draft,home,owner,(h,p) -> true));
        assertFalse(draft.freeze().hologramsEnabled(home));assertTrue(draft.freeze().hologramsEnabled(other));
        assertTrue(HomeHologramSettings.toggle(draft,home,owner,(h,p) -> true));
        assertTrue(draft.hologramVisibility.isEmpty());
    }
    @Test void nonOwnersCannotChangeAnything() {
        var draft=WorldState.empty().draft();
        assertThrows(IllegalStateException.class,() -> HomeHologramSettings.toggle(draft,UUID.randomUUID(),UUID.randomUUID(),(h,p) -> false));
        assertThrows(IllegalStateException.class,() -> HomeHologramSettings.toggle(draft,null,UUID.randomUUID(),(h,p) -> true));
        assertEquals(WorldState.empty(),draft.freeze());
    }
    @Test void savesSurviveRestartAndFutureDraftsWithoutChangingMachineContents() throws Exception {
        var draft=WorldState.empty().draft();UUID home=UUID.randomUUID(),player=UUID.randomUUID();
        var m=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),home,player,"iron_generator",1).contents(71,0,9,Map.of());
        draft.machines.put(m.id(),m);HomeHologramSettings.toggle(draft,home,player,(h,p) -> true);
        var file=new AtomicStateFile(directory.resolve("state.json"));file.write(draft.freeze());
        var restored=file.read();assertFalse(restored.hologramsEnabled(home));assertEquals(m,restored.machines().get(m.id()));
        assertFalse(restored.draft().freeze().hologramsEnabled(home));
    }
    @Test void readsOldSaveWithoutVisibilityFieldAsEnabled() throws Exception {
        Path path=directory.resolve("old.json");Files.writeString(path,"{\"schemaVersion\":1,\"machines\":{},\"parcels\":{},\"warehouses\":{}}");
        assertTrue(new AtomicStateFile(path).read().hologramsEnabled(UUID.randomUUID()));
    }
}
