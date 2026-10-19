package cn.genlite.platform;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;

/** Region owner only. Stops an unattended pad at 128 nearby matching items instead of unlimited entities. */
public final class GeneratorDropSpace {
    private GeneratorDropSpace() {}
    public static boolean available(Location location,String item,int amount) {
        Material material=Material.matchMaterial(item);
        int count=location.getWorld().getNearbyEntities(location,0.8,1.5,0.8).stream()
                .filter(entity -> entity instanceof Item && org.bukkit.Bukkit.isOwnedByCurrentRegion(entity)).map(entity -> ((Item)entity).getItemStack())
                .filter(stack -> stack.getType()==material).mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
        return count+amount<=128;
    }
}
