package cn.genlite.domain;

import java.util.Map;

public record Rules(Map<String, Type> types, int warehouseSlots) {
    public Rules(Map<String,Type> types) {this(types,54);}
    public record Level(int seconds, int count, int fuelItems, int bufferItems, double valueMultiplier, int collectorSlots) {}
    public record Fuel(String item, int units) {}
    public record Range(int minOffset, int maxOffset, int height) {}
    public record Type(String input, String output, int fuelPerItem, long baseValue,
                       Map<Integer,Level> levels, Map<Integer,Map<String,Integer>> upgrades, Fuel fuel, Range range) {
        public Type {
            levels=Map.copyOf(levels);
            upgrades=upgrades.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,e -> Map.copyOf(e.getValue())));
        }
    }
    public Rules { types = Map.copyOf(types); }
    public Level level(Machine m) { return types.get(m.type()).levels().get(m.level()); }
    public Map<String,Integer> upgrade(Machine m) { return types.get(m.type()).upgrades().get(m.level()+1); }
    public String fuelItem(Machine m) { return types.get(m.type()).fuel().item(); }
    public int fuelUnits(Machine m) { return m.collectorType()?0:types.get(m.type()).fuel().units(); }
    /** Whole fuel items that fit; a partly consumed item still occupies its remaining energy. */
    public int fuelSpace(Machine m) {
        return m.collectorType()?0:(int)Math.max(0,((long)level(m).fuelItems()*fuelUnits(m)-m.fuel())/fuelUnits(m));
    }
    public long value(Machine m) { return m.removed() ? 0 : Math.round(types.get(m.type()).baseValue * level(m).valueMultiplier); }
    public boolean inRange(Machine generator, Machine collector) {
        Position a = generator.position(), b = collector.position();
        Range range=types.get(collector.type()).range();
        long dx = (long)a.x()-b.x(), dz = (long)a.z()-b.z();
        return generator.home().equals(collector.home()) && a.world().equals(b.world())
                && dx >= range.minOffset && dx <= range.maxOffset && dz >= range.minOffset && dz <= range.maxOffset
                && Math.abs((long)a.y()-b.y()) <= range.height;
    }
    public int usedSlots(Map<String, Integer> inventory) {
        return inventory.values().stream().mapToInt(n -> (int)(((long)n+63)/64)).sum();
    }
    public int capacityFor(Map<String, Integer> inventory, String item,int slots) {
        int current = inventory.getOrDefault(item, 0);
        int partial = current % 64 == 0 ? 0 : 64-current%64;
        return (slots-usedSlots(inventory))*64+partial;
    }
}
