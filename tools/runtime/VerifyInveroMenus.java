import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.lang.reflect.*;
import cc.trixey.invero.taboolib.library.kether.*;
import cc.trixey.invero.taboolib.module.kether.*;
import cc.trixey.invero.core.script.loader.InveroKetherParser;
public class VerifyInveroMenus {
 public static void main(String[] args)throws Exception {
  var registry=new DefaultRegistry(); int registered=0, skipped=0;
  Set<String> reservedCommands=new TreeSet<>();
  for(String f:System.getProperty("java.class.path").split(";")) {
   if(!f.endsWith(".jar") || !Path.of(f).getFileName().toString().startsWith("Invero-"))continue;
   try(var jar=new JarFile(f)) {
    for(var entry:Collections.list(jar.entries())) {
     String name=entry.getName();
     if(!name.startsWith("cc/trixey/invero/core/command/") || !name.endsWith(".class") || name.contains("$"))continue;
     Class<?> type=Class.forName(name.replace('/','.').replace(".class",""),false,VerifyInveroMenus.class.getClassLoader());
     for(var annotation:type.getDeclaredAnnotations()) {
      if(!annotation.annotationType().getSimpleName().equals("CommandHeader"))continue;
      reservedCommands.add(((String)annotation.annotationType().getMethod("name").invoke(annotation)).toLowerCase(Locale.ROOT));
      for(String alias:(String[])annotation.annotationType().getMethod("aliases").invoke(annotation))reservedCommands.add(alias.toLowerCase(Locale.ROOT));
     }
    }
   }
  }
  if(!reservedCommands.contains("menu"))throw new AssertionError("Installed Invero management command not discovered");
  System.out.println("Installed Invero reserved commands: "+reservedCommands);
  for(String f:System.getProperty("java.class.path").split(";")){
   if(!f.endsWith(".jar") || !(f.contains("minecraft-kether")||f.contains("Invero")))continue;
   try(var jar=new JarFile(f)){
    for(var en:Collections.list(jar.entries())){
     String n=en.getName();
     if(!n.endsWith(".class")||(n.contains("$")&&!n.endsWith("$Companion.class"))||!(n.contains("/kether/action/")||n.contains("/core/script/")||n.contains("/common/message/")))continue;
     try {
      Class<?> c=Class.forName(n.replace('/','.').replace(".class",""),false,VerifyInveroMenus.class.getClassLoader());
      for(var m:c.getDeclaredMethods()){
       var a=m.getAnnotation(KetherParser.class); var b=m.getAnnotation(InveroKetherParser.class);
       if(a==null&&b==null)continue;
       try {
        Object instance=Modifier.isStatic(m.getModifiers())?null:(c.getSimpleName().equals("Companion")?c.getEnclosingClass().getField("Companion").get(null):c.getField("INSTANCE").get(null));
        var parser=(QuestActionParser)m.invoke(instance);
        String ns=a==null?"invero":a.namespace();
        for(String key:a==null?b.name():a.value()){registry.registerAction(ns,key,parser);registered++;}
       }catch(Throwable e){skipped++;System.out.println("SKIP "+c.getSimpleName()+"."+m.getName()+": "+e.getCause());}
      }
     }catch(Throwable e){skipped++;}
    }
   }
  }
  QuestService service=(QuestService)Proxy.newProxyInstance(VerifyInveroMenus.class.getClassLoader(),new Class[]{QuestService.class},(proxy,m,a)->{
   if(m.getName().equals("getRegistry"))return registry;
   if(m.getName().equals("getLocalizedText"))return a[0]+" "+Arrays.deepToString(a);
   throw new UnsupportedOperationException(m.getName());
  });
  ServiceHolder.setQuestServiceInstance(service);


  var codec=(kotlinx.serialization190.json.Json)new cc.trixey.invero.core.api.DefaultMenuManager().getJsonSerializer();
  int menus=0,actions=0,nodes=0,scripts=0;var kether=new KetherScriptLoader();
  var js=new org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory().getScriptEngine();
  try(var paths=Files.list(Path.of(args[0]))){
   for(Path path:paths.filter(p->p.toString().endsWith(".yml")).toList()){
    var config=cc.trixey.invero.core.serialize.hocon.PatchedLoader.INSTANCE.loadFromFile(path.toFile(),cc.trixey.invero.taboolib.module.configuration.Type.YAML);
    config.changeType(cc.trixey.invero.taboolib.module.configuration.Type.JSON);
    var tree=(kotlinx.serialization190.json.JsonObject)codec.parseToJsonElement(config.saveToString());
    var transform=cc.trixey.invero.core.serialize.BaseMenuSerializer.class.getDeclaredMethod("transformDeserialize",kotlinx.serialization190.json.JsonElement.class);
    transform.setAccessible(true);
    var normalized=(kotlinx.serialization190.json.JsonObject)transform.invoke(cc.trixey.invero.core.serialize.BaseMenuSerializer.INSTANCE,tree);
    var settings=(kotlinx.serialization190.json.JsonObject)normalized.get("menu");
    if(!settings.get("rows").equals(tree.get("rows")))throw new AssertionError("rows shortcut not applied");
    if(tree.containsKey("bindings")) {
     var bindings=(kotlinx.serialization190.json.JsonObject)tree.get("bindings");
     for(var command:(kotlinx.serialization190.json.JsonArray)bindings.get("command")) {
      String label=((kotlinx.serialization190.json.JsonPrimitive)command).getContent().toLowerCase(Locale.ROOT);
      if(reservedCommands.contains(label))throw new IllegalArgumentException("Binding conflicts with installed Invero command: "+label);
     }
     var activator=new cc.trixey.invero.core.compat.activators.ActivatorCommand().deserialize(bindings.get("command"));
     if(!activator.getCommand().equals(bindings.get("command")))throw new AssertionError("command binding codec");
     System.out.println("PASS command binding codec: "+activator.getCommand());
    }
    var items=(kotlinx.serialization190.json.JsonObject)tree.get("items");
    for(var entry:items.entrySet()){
     var item=(kotlinx.serialization190.json.JsonObject)entry.getValue();
     if(item.containsKey("action")){
      codec.decodeFromJsonElement(cc.trixey.invero.core.serialize.IconHandlerSerializer.INSTANCE,item.get("action"));actions++;
      var action=(kotlinx.serialization190.json.JsonObject)item.get("action");
      for(var click:action.entrySet()){
       String code=((kotlinx.serialization190.json.JsonPrimitive)click.getValue()).getContent();
       kether.load(service,"action"+scripts,("def main = { "+code+" }").getBytes(java.nio.charset.StandardCharsets.UTF_8),List.of("invero"));scripts++;
       var embedded=java.util.regex.Pattern.compile("js \\\"\\\"\\\"(.*?)\\\"\\\"\\\"",java.util.regex.Pattern.DOTALL).matcher(code);
       while(embedded.find()) {
        String script=embedded.group(1);
        ((javax.script.Compilable)js).compile(script);nodes++;
        for(boolean loaded:new boolean[]{true,false}) {
         js.eval("var teleports=0,messages=0,closes=0; var player={closeInventory:function(){closes++;player=null;},teleportAsync:function(p){if(p!=='world-spawn')throw 'bad destination';teleports++;},sendMessage:function(m){messages++;}}; var Java={type:function(name){if(name==='org.bukkit.Bukkit')return {getWorld:function(n){if(n!=='world')throw 'bad world';return "+(loaded?"{getSpawnLocation:function(){return 'world-spawn';}}":"null")+";}};if(name==='net.kyori.adventure.text.minimessage.MiniMessage')return {miniMessage:function(){return {deserialize:function(s){return s;}};}};throw 'unexpected class '+name;}};");
         js.eval(script);
         if(((Number)js.get("teleports")).intValue()!=(loaded?1:0) || ((Number)js.get("messages")).intValue()!=(loaded?0:1) || ((Number)js.get("closes")).intValue()!=1)throw new AssertionError("spawn branch failed");
        }
        System.out.println("PASS embedded JS compile and world-present/missing branch simulation (not server runtime)");
       }
      }
     }
     java.util.List<String> texts=new ArrayList<>();
     if(item.containsKey("name"))texts.add(((kotlinx.serialization190.json.JsonPrimitive)item.get("name")).getContent());
     if(item.containsKey("lore"))for(var line:(kotlinx.serialization190.json.JsonArray)item.get("lore"))texts.add(((kotlinx.serialization190.json.JsonPrimitive)line).getContent());
     for(String text:texts){
      var matcher=java.util.regex.Pattern.compile("\\{\\{(.*?)\\}\\}").matcher(text);
      while(matcher.find()){
       kether.load(service,"inline"+scripts,("def main = { "+matcher.group(1)+" }").getBytes(java.nio.charset.StandardCharsets.UTF_8),List.of("invero"));scripts++;
      }
     }
    }
    if(tree.containsKey("nodes"))for(var entry:((kotlinx.serialization190.json.JsonObject)tree.get("nodes")).entrySet()){
     if(entry.getKey().contains("."))throw new AssertionError("Nested node ID");
     var node=codec.decodeFromJsonElement(cc.trixey.invero.core.serialize.NodeSerializer.INSTANCE,entry.getValue());
     ((javax.script.Compilable)js).compile(node.getValue());nodes++;
    }
    menus++;System.out.println("PASS actual YAML->JSON, action/node codecs, Kether and JS: "+path.getFileName());
   }
  }
  // Verify the exact optional plugin bridge method signatures against the installed dependency.
  Class.forName("cc.trixey.invero.taboolib.common.platform.PlatformFactory").getMethod("getAPI",String.class);
  cc.trixey.invero.common.api.InveroAPI.class.getMethod("getMenuManager");
  cc.trixey.invero.common.api.InveroMenuManager.class.getMethod("getMenu",String.class,boolean.class);
  cc.trixey.invero.common.Menu.class.getMethod("open",org.bukkit.entity.Player.class,Map.class);
  cc.trixey.invero.common.Menu.class.getMethod("close",org.bukkit.entity.Player.class,boolean.class,boolean.class);
  cc.trixey.invero.core.Session.Companion.getClass().getMethod("getSession",cc.trixey.invero.ui.bukkit.PlayerViewer.class);
  org.bukkit.Bukkit.class.getMethod("getWorld",String.class);
  org.bukkit.World.class.getMethod("getSpawnLocation");
  org.bukkit.entity.Player.class.getMethod("teleportAsync",org.bukkit.Location.class);
  System.out.println("RESULT menus="+menus+" actions="+actions+" nodes="+nodes+" scripts="+scripts+"; full BaseMenu/client runtime not executed");
 }
}
