package com.holybuckets.villageecon.core.trade;

import com.holybuckets.villageecon.core.model.Mayor;
import net.minecraft.world.item.Item;

/**
 * Class: Sale
 * Description: Immutable record of one completed (theoretical) trade between two villages.
 * Saved to the global sale log in the Bazaar and applied to each village's ledger
 * via ResourceLedger::logTrade. Also feeds the moving-average market rate.
 */
public class Sale {

    public static final String CLASS_ID = "022";

    private final Mayor seller;
    private final Mayor buyer;
    private final int salePrice;        //per unit
    private final int saleQuantity;
    private final long saleTime;        //game time (ticks) the sale occurred
    private final Item resource;
    private final String resourceId;    //economy config resource id, for ledger keys

    public Sale(Mayor seller, Mayor buyer, int salePrice, int saleQuantity,
                long saleTime, Item resource, String resourceId) {
        this.seller = seller;
        this.buyer = buyer;
        this.salePrice = salePrice;
        this.saleQuantity = saleQuantity;
        this.saleTime = saleTime;
        this.resource = resource;
        this.resourceId = resourceId;
    }

    public Mayor getSeller() { return seller; }

    public Mayor getBuyer() { return buyer; }

    public int getSalePrice() { return salePrice; }

    public int getSaleQuantity() { return saleQuantity; }

    public long getSaleTime() { return saleTime; }

    public Item getResource() { return resource; }

    public String getResourceId() { return resourceId; }

    @Override
    public String toString() {
        return String.format("Sale[%s x%d @ %d, %s -> %s, t=%d]",
            resourceId, saleQuantity, salePrice,
            seller != null ? seller.getVillageChunkId() : "?",
            buyer != null ? buyer.getVillageChunkId() : "?", saleTime);
    }
}
