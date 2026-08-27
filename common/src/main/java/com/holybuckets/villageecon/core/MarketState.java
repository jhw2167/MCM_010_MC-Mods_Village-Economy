package com.holybuckets.villageecon.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.trade.Bazaar;

/**
 * Class: MarketState
 * Description: Facade over the Bazaar for global market aggregates. Market rates come
 * from each Market's volume-weighted moving average of the last N trades.
 * totalCurrency / V / T counts for growth reward computation remain TODO.
 */
public class MarketState {

    public static final String CLASS_ID = "016";

    /** Market rate D_j for the given resource, from the overworld Bazaar's moving average **/
    public static float marketRate(String resourceId) {
        Bazaar bazaar = Bazaar.get(GeneralConfig.OVERWORLD);
        if (bazaar == null) return 1f;
        EconomyResource resource = ModConfig.getInstance().getResource(resourceId);
        if (resource == null || resource.getItem() == null) return 1f;
        return bazaar.getMarketRate(resource.getItem());
    }

    /** SUM(C) - total reserve currency across all villages. TODO **/
    public static float totalCurrency() {
        //TODO
        return 0f;
    }
}
