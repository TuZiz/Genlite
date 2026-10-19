package cn.genlite.platform;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Use only on the player/region thread; never match translated names. */
public final class CraftItems {
    private final cn.genlite.display.MachineText text;
    private final java.util.function.Supplier<cn.genlite.domain.WorldState> state;
    private final NamespacedKey levelKey;
    private final NamespacedKey sourceKey, tokenKey;
    public CraftItems(JavaPlugin plugin,cn.genlite.display.MachineText text,java.util.function.Supplier<cn.genlite.domain.WorldState> state) {
        this.text=text;
        this.state=state;
        levelKey=new NamespacedKey(plugin,"machine-level");
        sourceKey=new NamespacedKey(plugin,"shell-source"); tokenKey=new NamespacedKey(plugin,"shell-token");
    }
    public java.util.UUID source(ItemStack item) { return credentials(item).source(); }
    public java.util.UUID token(ItemStack item) { return credentials(item).token(); }
    public cn.genlite.domain.ShellCredentials credentials(ItemStack item) {
        if(item==null || item.isEmpty()) return new cn.genlite.domain.ShellCredentials(false,null,null);
        var data=item.getPersistentDataContainer();
        boolean present=data.has(sourceKey) || data.has(tokenKey);
        String source=data.has(sourceKey,PersistentDataType.STRING)?data.get(sourceKey,PersistentDataType.STRING):null;
        String token=data.has(tokenKey,PersistentDataType.STRING)?data.get(tokenKey,PersistentDataType.STRING):null;
        return cn.genlite.domain.ShellCredentials.parse(present,source,token);
    }
    public ItemStack parcel(Player player,cn.genlite.domain.Parcel parcel) {
        ItemStack item=build(player,parcel.item(),parcel.amount(),parcel.machineLevel());
        if (parcel.shellSource()!=null) item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(sourceKey,PersistentDataType.STRING,parcel.shellSource().toString());
            meta.getPersistentDataContainer().set(tokenKey,PersistentDataType.STRING,parcel.shellToken().toString());
        });
        return parcel.shellSource()==null?item:decorate(item);
    }
    public String id(ItemStack stack) {
        if (stack==null || stack.isEmpty()) return "minecraft:air";
        var custom=CraftEngineItems.getCustomItemId(stack);
        return custom==null ? stack.getType().getKey().toString() : custom.toString();
    }
    public int level(ItemStack stack) {
        if (stack==null) return 1;
        int level=stack.getPersistentDataContainer().getOrDefault(levelKey,PersistentDataType.INTEGER,1);
        return Math.clamp(level,1,5);
    }
    public ItemStack build(Player player,String id,int amount,int level) {
        ItemStack item;
        if (id.startsWith("genlite:")) {
            var definition=CraftEngineItems.byId(id);
            if (definition==null) throw new IllegalStateException("CraftEngine missing item "+id);
            item=player==null ? definition.buildBukkitItem() : definition.buildBukkitItem(player);
        } else {
            Material material=Material.matchMaterial(id);
            if (material==null || material.isAir()) throw new IllegalArgumentException("Unknown material "+id);
            item=new ItemStack(material);
        }
        item.setAmount(amount);
        if (level>0) item.editMeta(meta -> meta.getPersistentDataContainer().set(levelKey,PersistentDataType.INTEGER,level));
        return decorate(item);
    }
    public ItemStack decorate(ItemStack item) {
        String id=id(item);
        if(!id.startsWith("genlite:")) return item;
        boolean machine=text.display(id.substring(8))!=null;
        if(!machine && !id.equals("genlite:binding_tool")) return item;
        var definition=CraftEngineItems.byId(id);
        Material material=definition==null?null:Material.matchMaterial(definition.material().toString());
        // Paper withType creates a replacement and preserves count/meta, including CE identity and shell PDC.
        ItemStack result=material!=null && !material.isAir() && item.getType()!=material?item.withType(material):item;
        if(!machine) return result;
        String type=id.substring(8);int level=level(result);
        var lore=text.lore(type,level,source(result),token(result),state.get());
        result.editMeta(meta -> {meta.itemName(text.name(type,level));meta.lore(lore);});
        return result;
    }
}
