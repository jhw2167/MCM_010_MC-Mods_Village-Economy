package com.holybuckets.villageecon.core.trade;

import com.holybuckets.villageecon.core.model.ResourceLedger;
import com.holybuckets.villageecon.core.model.VillageEconomy;

/**
 * Class: Post
 * Description: A single buy or sell offer posted to a Market for one tickTrade.
 * Each village may post at most one offer per resource per tickTrade, and each
 * village exclusively buys OR sells a given resource in a single theoretical trade.
 *
 * demandReserve is derived from the true demand d, modulated by the village's rolled
 * agreeableness for this tickTrade (1 = accepts any still-profitable trade,
 * 0 = only trades at full expected profit d).
 */
public class Post {

    public static final String CLASS_ID = "021";

    private final VillageEconomy village;
    private final int demand;           //d - true marginal demand (profit expectation) per unit
    private final int quantityDemand;   //units the village wants to move this trade
    private final int demandReserve;    //reservation price per unit after agreeableness modulation
    private final ResourceLedger ledger;//ledger this post trades against (theoLedger for tick trades)

    public Post(VillageEconomy village, int demand, int quantityDemand, int demandReserve, ResourceLedger ledger) {
        this.village = village;
        this.demand = demand;
        this.quantityDemand = Math.max(0, quantityDemand);
        this.demandReserve = demandReserve;
        this.ledger = ledger;
    }

    public VillageEconomy getVillage() { return village; }

    public int getDemand() { return demand; }

    public int getQuantityDemand() { return quantityDemand; }

    public int getDemandReserve() { return demandReserve; }

    public ResourceLedger getLedger() { return ledger; }
}
