package com.holybuckets.villageecon.core.trade;

import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.ResourceLedger;

/**

 * Each village may post at most one offer per resource per tickTrade, and each
 * village exclusively buys OR sells a given resource in a single theoretical trade.

 * We use a nash equillibrium seeking model to move the market (current demand)
 * to match the calculated reserverDemand for each village. A post only includes the
 * seller's (posters) demand
 */
public class Post {

    public static final String CLASS_ID = "021";

    private final Mayor village;            //the mayor posting on behalf of its village
    private final int demand;               //d - true marginal demand (profit expectation) per unit
    private final int quantityDemand;       //units the village wants to move this trade
    private final int demandReserve;            //reservation price per unit after agreeableness modulation
    private final ResourceLedger ledger;//ledger this post trades against (theoLedger for tick trades)

    public Post(Mayor village, int demand, int quantityDemand, int demandReserve, ResourceLedger ledger) {
        this.village = village;
        this.demand = demand;
        this.quantityDemand = Math.max(0, quantityDemand);
        this.demandReserve = demandReserve;
        this.ledger = ledger;
    }

    public Mayor getVillage() { return village; }

    public int getDemand() { return demand; }

    public int getQuantityDemand() { return quantityDemand; }

    public int getDemandReserve() { return demandReserve; }

    public ResourceLedger getLedger() { return ledger; }
}
