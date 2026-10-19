package cn.genlite.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Player owner thread only. Capacity and actual delivery use the same canonical item metadata. */
final class ItemDelivery {
    private ItemDelivery() {}
    static int cursorCapacity(Player p,ItemStack unit) {
        ItemStack cursor=p.getItemOnCursor();
        return cursor.isEmpty()?unit.getMaxStackSize():cursor.isSimilar(unit)?Math.max(0,unit.getMaxStackSize()-cursor.getAmount()):0;
    }
    static void cursorGive(Player p,ItemStack unit,int amount) {
        if(cursorCapacity(p,unit)<amount) throw new IllegalStateException("inventory-full");
        ItemStack next=unit.clone();next.setAmount(amount+(p.getItemOnCursor().isEmpty()?0:p.getItemOnCursor().getAmount()));p.setItemOnCursor(next);
    }
    static int capacity(Player p,ItemStack unit) {
        int count=0;
        for(ItemStack slot:p.getInventory().getStorageContents()) {
            if(slot==null || slot.isEmpty()) count+=unit.getMaxStackSize();
            else if(slot.isSimilar(unit)) count+=Math.max(0,unit.getMaxStackSize()-slot.getAmount());
        }
        return count;
    }
    static void give(Player p,ItemStack unit,int amount) {
        if(capacity(p,unit)<amount) throw new IllegalStateException("inventory-full");
        while(amount>0) {
            int count=Math.min(unit.getMaxStackSize(),amount);ItemStack batch=unit.clone();batch.setAmount(count);
            if(!p.getInventory().addItem(batch).isEmpty()) throw new IllegalStateException("Unexpected inventory mutation during delivery");
            amount-=count;
        }
    }
}
