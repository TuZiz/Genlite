package cn.genlite.domain;

import java.util.UUID;

/** Split recoverable escrow into backpack-sized, durably marked batches. No manual claim screen is required. */
public final class PendingParcels {
    private PendingParcels() {}
    public static Parcel reserve(WorldState.Draft draft,UUID id,int limit) {
        Parcel p=draft.parcels.get(id);
        if(p==null || !p.phase().equals("WAITING") || p.dropPosition()!=null || limit<=0) throw new IllegalArgumentException("busy");
        int amount=Math.min(limit,p.amount());
        Parcel delivery;
        if(amount==p.amount()) delivery=p.sending();
        else {
            draft.parcels.put(id,p.amount(p.amount()-amount));
            delivery=new Parcel(UUID.randomUUID(),p.player(),p.item(),amount,p.machineLevel(),"SENDING",p.shellSource(),p.shellToken(),null);
        }
        draft.parcels.put(delivery.id(),delivery);return delivery;
    }
}
