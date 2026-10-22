package cn.genlite.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.util.Map;

public final class Messages {
    private final YamlConfiguration language;
    public Messages(YamlConfiguration language) { this.language=language; }
    public Component render(String template,Map<String,Component> values) {
        TagResolver[] resolvers=values.entrySet().stream().map(e -> Placeholder.component(e.getKey(),e.getValue())).toArray(TagResolver[]::new);
        return nonItalic(MiniMessage.miniMessage().deserialize(template,resolvers));
    }
    public static Component nonItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC,false)
                .children(component.children().stream().map(Messages::nonItalic).toList());
    }
    public Component text(String value) { return render(value,Map.of()); }
    public Component key(String key) {
        String value=language.getString(key);
        if (value==null) throw new IllegalStateException("lang/zh_cn.yml:"+key+" missing");
        return text(value);
    }
    public Component label(String id) { return text(java.util.Objects.requireNonNull(language.getString("item-labels."+id),"lang/zh_cn.yml:item-labels."+id)); }
    public Component format(String key,Map<String,Component> values) {return render(java.util.Objects.requireNonNull(language.getString(key),"lang/zh_cn.yml:"+key),values);}
    public void send(Player player,String key) { player.sendMessage(key(key)); }
}
