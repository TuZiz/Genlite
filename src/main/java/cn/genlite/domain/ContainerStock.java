package cn.genlite.domain;

import java.math.BigInteger;
import java.util.*;
import java.util.function.BiPredicate;

/** Durable direct-withdrawal reservations. ItemStack construction is exclusively the player-thread bridge's job. */
public final class ContainerStock {
    public enum Kind {WAREHOUSE,COLLECTOR,FUEL,OUTPUT}
    public record Source(Kind kind,UUID id,String item) {}
    private final Rules rules;
    private final BiPredicate<UUID,UUID> member;
    private final MachineActions machines;
    public ContainerStock(Rules rules,BiPredicate<UUID,UUID> member) {this.rules=rules;this.member=member;machines=new MachineActions(rules,member);}
    public Parcel reserve(WorldState.Draft d,UUID player,Source source,int limit) {
        if(limit<=0) throw new IllegalArgumentException("inventory-full");
        int amount;
        if(source.kind()==Kind.WAREHOUSE) {
            if(!member.test(source.id(),player)) throw new IllegalArgumentException("permission");
            var stock=new HashMap<>(d.warehouses.getOrDefault(source.id(),Map.of()));
            BigInteger current=stock.getOrDefault(source.item(),BigInteger.ZERO);
            amount=current.min(BigInteger.valueOf(limit)).intValueExact();
            if(amount<=0) throw new IllegalArgumentException("empty");
            BigInteger left=current.subtract(BigInteger.valueOf(amount));
            if(left.signum()==0) stock.remove(source.item());else stock.put(source.item(),left);
            d.warehouses.put(source.id(),Map.copyOf(stock));
        } else {
            Machine m=machines.require(d,source.id(),player);
            if(source.kind()==Kind.FUEL && !source.item().equals(rules.fuelItem(m))) throw new IllegalArgumentException("unsupported-item");
            if(source.kind()==Kind.COLLECTOR && !m.collectorType()) throw new IllegalArgumentException("unsupported-item");
            if(source.kind()==Kind.OUTPUT && !source.item().equals(rules.types().get(m.type()).output())) throw new IllegalArgumentException("unsupported-item");
            int available=switch(source.kind()) {case COLLECTOR -> m.inventory().getOrDefault(source.item(),0);case FUEL -> m.fuel()/rules.fuelUnits(m);case OUTPUT -> m.buffer();default -> 0;};
            amount=Math.min(limit,available);if(amount<=0) throw new IllegalArgumentException("empty");
            if(source.kind()==Kind.COLLECTOR) {
                var stock=new HashMap<>(m.inventory());stock.compute(source.item(),(k,n) -> n==amount?null:n-amount);
                d.machines.put(m.id(),m.contents(m.fuel(),m.input(),m.buffer(),stock));
            } else d.machines.put(m.id(),m.contents(m.fuel()-(source.kind()==Kind.FUEL?amount*rules.fuelUnits(m):0),m.input(),m.buffer()-(source.kind()==Kind.OUTPUT?amount:0),m.inventory()));
        }
        Parcel parcel=Parcel.waiting(player,source.item(),amount,0).sending();d.parcels.put(parcel.id(),parcel);return parcel;
    }
    public void restore(WorldState.Draft d,Source source,Parcel parcel) {
        if(d.parcels.get(parcel.id())==null || !d.parcels.get(parcel.id()).phase().equals("SENDING")) return;
        if(source.kind()==Kind.WAREHOUSE) {
            var stock=new HashMap<>(d.warehouses.getOrDefault(source.id(),Map.of()));stock.merge(source.item(),BigInteger.valueOf(parcel.amount()),BigInteger::add);
            if(stock.size()>rules.warehouseSlots()) {d.parcels.put(parcel.id(),parcel.waiting());return;}
            d.warehouses.put(source.id(),Map.copyOf(stock));d.parcels.remove(parcel.id());return;
        }
        Machine m=d.machines.get(source.id());
        if(m!=null && !m.removed()) {
            if(source.kind()==Kind.COLLECTOR && rules.capacityFor(m.inventory(),source.item(),rules.level(m).collectorSlots())>=parcel.amount()) {
                var stock=new HashMap<>(m.inventory());stock.merge(source.item(),parcel.amount(),Math::addExact);
                d.machines.put(m.id(),m.contents(m.fuel(),m.input(),m.buffer(),stock));d.parcels.remove(parcel.id());return;
            }
            if(source.kind()==Kind.FUEL && (long)m.fuel()+(long)parcel.amount()*rules.fuelUnits(m)<=(long)rules.level(m).fuelItems()*rules.fuelUnits(m)) {
                d.machines.put(m.id(),m.contents(m.fuel()+parcel.amount()*rules.fuelUnits(m),m.input(),m.buffer(),m.inventory()));d.parcels.remove(parcel.id());return;
            }
        }
        // Concurrent production/removal can consume the released space. Keep the items in auto-delivery escrow.
        d.parcels.put(parcel.id(),parcel.waiting());
    }
    public void deposit(WorldState.Draft d,UUID player,Source source,int amount) {
        if(amount<=0) throw new IllegalArgumentException("empty");
        if(!source.item().matches("(minecraft|genlite):[a-z0-9_]+")) throw new IllegalArgumentException("unsupported-item");
        if(source.kind()==Kind.WAREHOUSE) {
            if(!member.test(source.id(),player)) throw new IllegalArgumentException("permission");
            var stock=new HashMap<>(d.warehouses.getOrDefault(source.id(),Map.of()));
            if(!stock.containsKey(source.item()) && stock.size()>=rules.warehouseSlots()) throw new IllegalArgumentException("full");
            stock.merge(source.item(),BigInteger.valueOf(amount),BigInteger::add);d.warehouses.put(source.id(),Map.copyOf(stock));
        } else {
            Machine m=machines.require(d,source.id(),player);
            if(!m.collectorType() || rules.types().values().stream().noneMatch(t -> source.item().equals(t.output()))) throw new IllegalArgumentException("unsupported-item");
            if(rules.capacityFor(m.inventory(),source.item(),rules.level(m).collectorSlots())<amount) throw new IllegalArgumentException("full");
            var stock=new HashMap<>(m.inventory());stock.merge(source.item(),amount,Math::addExact);d.machines.put(m.id(),m.contents(m.fuel(),m.input(),m.buffer(),stock));
        }
    }
}
