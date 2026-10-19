package cn.genlite.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuSession implements InventoryHolder {
    public final UUID player, machine;
    public final String menu;
    public final Map<Integer,String> actions=new HashMap<>(), stock=new HashMap<>();
    private Inventory inventory;
    public MenuSession(UUID player,UUID machine,String menu) {this.player=player;this.machine=machine;this.menu=menu;}
    public void inventory(Inventory inventory) {this.inventory=inventory;}
    @Override public Inventory getInventory() {return inventory;}
}
