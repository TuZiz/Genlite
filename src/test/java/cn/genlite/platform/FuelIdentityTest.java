package cn.genlite.platform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FuelIdentityTest {
    @Test void ceFuelDoesNotRequireCanonicalDisplayOrGenliteGiveMetadata() {
        assertTrue(CraftItems.fuelIdentityMatches("genlite:fuel","genlite:fuel",false,() -> {
            fail("CE fuel must not be compared by display metadata");return false;
        }));
    }
    @Test void wrongIdsAndCredentialBearingItemsNeverBecomeFuel() {
        assertFalse(CraftItems.fuelIdentityMatches("genlite:fuel","minecraft:coal",false,() -> true));
        assertFalse(CraftItems.fuelIdentityMatches("genlite:fuel","genlite:mana_shard",false,() -> true));
        // Presence also covers malformed and consumed shell credentials.
        assertFalse(CraftItems.fuelIdentityMatches("genlite:fuel","genlite:fuel",true,() -> true));
    }
    @Test void otherConfiguredFuelStillRequiresCanonicalMetadata() {
        assertFalse(CraftItems.fuelIdentityMatches("minecraft:coal","minecraft:coal",false,() -> false));
        assertTrue(CraftItems.fuelIdentityMatches("minecraft:coal","minecraft:coal",false,() -> true));
    }
}
