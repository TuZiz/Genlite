package cn.genlite.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.*;

/** Player-owned projection; displayed fuel is backed by durable machine energy, never a second item store. */
final class FuelSession implements InventoryHolder {
    final Player viewer;
    final UUID machine;
    final Map<Integer,String> actions=new HashMap<>();
    final Map<Integer,Integer> counts=new HashMap<>(),fuelSlots=new HashMap<>();
    Inventory inventory;
    FuelSession(Player viewer,UUID machine) {this.viewer=viewer;this.machine=machine;}
    @Override public Inventory getInventory() {return inventory;}
}
