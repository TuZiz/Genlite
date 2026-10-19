package cn.genlite.domain;

public enum OutputMode {
    DROP, COLLECTOR, WAREHOUSE;
    public OutputMode next() {return values()[(ordinal()+1)%values().length];}
    public String key() {return name().toLowerCase(java.util.Locale.ROOT);}
}
