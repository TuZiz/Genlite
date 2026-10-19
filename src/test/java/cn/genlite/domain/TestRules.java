package cn.genlite.domain;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import java.util.*;

final class TestRules {
    static Rules load() throws Exception {
        return cn.genlite.config.MachineConfigurations.read(Path.of("src/main/resources/genlite")).rules();
    }
    static final UUID WORLD=UUID.randomUUID(),HOME=UUID.randomUUID(),PLAYER=UUID.randomUUID();
    static Position pos(int x,int y,int z) {return new Position(WORLD,"dreamskyblock:test",x,y,z);}
    static Machine machine(String type,int level,int fuel) {return Machine.create(pos(0,64,0),HOME,PLAYER,type,level).contents(fuel,0,0,Map.of());}
}
