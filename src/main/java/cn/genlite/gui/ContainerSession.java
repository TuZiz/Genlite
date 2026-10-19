package cn.genlite.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.*;

final class ContainerSession implements InventoryHolder {
    final UUID home,player,world;
    org.bukkit.entity.Player viewer;
    final cn.genlite.domain.ContainerStock.Kind kind;
    final Map<Integer,String> stock=new HashMap<>(),actions=new HashMap<>();
    final Map<Integer,Integer> counts=new HashMap<>();
    Inventory inventory;
    ContainerSession(UUID home,UUID player,UUID world,cn.genlite.domain.ContainerStock.Kind kind) {this.home=home;this.player=player;this.world=world;this.kind=kind;}
    public Inventory getInventory() {return inventory;}
}
