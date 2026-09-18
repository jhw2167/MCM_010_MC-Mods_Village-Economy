package com.holybuckets.villageecon.core.trade;

import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.ResourceLedger;

public class Post {

    public static final String CLASS_ID = "021";

    private final Mayor village;            //the mayor posting on behalf of its village
    private final float demand;             //d - expected profit per unit if traded at the market rate
    private final int quantityDemand;       //units the village wants to move this trade
    private final float demandReserve;      //reservation price per unit, derived from the agreeableness roll
    private final ResourceLedger ledger;//ledger this post trades against (theoLedger for tick trades)

    public Post(Mayor village, float demand, int quantityDemand, float demandReserve, ResourceLedger ledger) {
        this.village = village;
        this.demand = demand;
        this.quantityDemand = Math.max(0, quantityDemand);
        this.demandReserve = demandReserve;
        this.ledger = ledger;
    }

    public Mayor getVillage() { return village; }

    public float getDemand() { return demand; }

    public int getQuantityDemand() { return quantityDemand; }

    public float getDemandReserve() { return demandReserve; }

    public ResourceLedger getLedger() { return ledger; }
}
