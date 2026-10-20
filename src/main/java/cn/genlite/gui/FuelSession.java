package cn.genlite.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.*;

/** Player-owned view; no actual items are held in the input slot between transactions. */
final class FuelSession implements InventoryHolder {
    final Player viewer;
    final UUID machine;
    final Map<Integer,String> actions=new HashMap<>();
    Inventory inventory;
    FuelSession(Player viewer,UUID machine) {this.viewer=viewer;this.machine=machine;}
    @Override public Inventory getInventory() {return inventory;}
}
