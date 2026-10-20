package cn.genlite.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FuelStacksTest {
    @Test void storedFuelRemainsVisibleAndProductionConsumesOneWholeItemWithoutRefundingItsRemainder() {
        assertEquals(java.util.List.of(64,6,0),FuelStacks.visible(70*8,8,64,3));
        assertEquals(java.util.List.of(64,5,0),FuelStacks.visible(70*8-1,8,64,3));
        assertEquals(java.util.List.of(0,0,0),FuelStacks.visible(7,8,64,3));
        assertEquals(0,FuelStacks.visible(0,8,64,27).stream().mapToInt(Integer::intValue).sum());
    }
    @Test void stackLimitsAndVisibleSlotsNeverCreateOrDiscardStoredEnergy() {
        var full=FuelStacks.visible(320*8,8,64,27);
        assertEquals(27,full.size());assertEquals(320,full.stream().mapToInt(Integer::intValue).sum());
        assertEquals(java.util.List.of(16,16,3),FuelStacks.visible(35*9,9,16,3));
        assertEquals(java.util.List.of(1,1,1),FuelStacks.visible(40*8,8,1,3));
        assertThrows(IllegalArgumentException.class,() -> FuelStacks.visible(1,0,64,27));
    }
    @Test void emptyCursorRightClickTakesCeilingHalfAndLeftTakesDisplayedStackOnly() {
        assertEquals(32,FuelStacks.withdrawal(64,true));
        assertEquals(3,FuelStacks.withdrawal(5,true));
        assertEquals(1,FuelStacks.withdrawal(1,true));
        assertEquals(0,FuelStacks.withdrawal(0,true));
        assertEquals(5,FuelStacks.withdrawal(5,false));
    }
    @Test void draggingOntoPartiallyFilledStacksCountsOnlyNewFuelNotTheExistingContents() {
        var before=java.util.Map.of(0,40,1,20,2,0);
        assertEquals(16,FuelStacks.dragAddition(before,java.util.Map.of(0,48,1,28)));
        assertEquals(12,FuelStacks.dragAddition(before,java.util.Map.of(0,40,2,12)));
    }
}
