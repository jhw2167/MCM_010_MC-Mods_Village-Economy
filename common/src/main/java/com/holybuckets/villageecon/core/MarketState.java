package com.holybuckets.villageecon.core;

/**
 * Class: MarketState
 * Description: PLACEHOLDER. Global market aggregates across all villages, recomputed
 * each cycle (and on demand). Will provide the market rate D per resource, total
 * currency in circulation, and V / T counts for growth reward computation.
 */
public class MarketState {

    public static final String CLASS_ID = "016";

    /** Market rate D_j for the given resource. TODO: derive from aggregate supply and demand **/
    public static float marketRate(String resourceId) {
        //TODO
        return 1f;
    }

    /** SUM(C) - total reserve currency across all villages. TODO **/
    public static float totalCurrency() {
        //TODO
        return 0f;
    }
}
