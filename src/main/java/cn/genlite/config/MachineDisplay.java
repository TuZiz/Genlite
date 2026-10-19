package cn.genlite.config;

import java.util.List;

/** Immutable display templates; production rules never depend on translated text. */
public record MachineDisplay(String name, List<String> lore, Hologram hologram) {
    public MachineDisplay { lore=List.copyOf(lore); }
    public record Hologram(boolean enabled, double height, List<String> lines,double scale) {
        public Hologram { lines=List.copyOf(lines); }
        public Hologram(boolean enabled,double height,List<String> lines) {this(enabled,height,lines,0.65);}
    }
}
