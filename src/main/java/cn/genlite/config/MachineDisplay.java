package cn.genlite.config;

import java.util.List;

/** Immutable display templates; production rules never depend on translated text. */
public record MachineDisplay(Hologram hologram) {
    public record Hologram(boolean enabled, double height, List<String> lines,double scale) {
        public Hologram { lines=List.copyOf(lines); }
        public Hologram(boolean enabled,double height,List<String> lines) {this(enabled,height,lines,0.65);}
    }
}
