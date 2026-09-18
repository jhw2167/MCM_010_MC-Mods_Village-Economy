package com.holybuckets.villageecon.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.VillageEconomyJsonConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.trade.Bazaar;


public class MarketState {

    public static final String CLASS_ID = "016";

    private static MarketState INSTANCE;

    private VillageManager manager;
    private Bazaar bazaar;
    private ModConfig modConfig;
    private VillageEconomyJsonConfig eConfig;

    private MarketState(VillageManager manager, Bazaar bazaar) {
        this.manager = manager;
        this.bazaar = bazaar;
        this.modConfig = ModConfig.getInstance();
        this.eConfig = modConfig.getEconomyConfig();
    }

    static void init(VillageManager manager, Bazaar bazaar) {
            INSTANCE = new MarketState(manager, bazaar);
            EconomyMath.init(INSTANCE);
    }

    public static float marketRate(String resourceId) {
        EconomyResource r = INSTANCE.eConfig.getResource(resourceId);
        return marketRate(r);
    }

    public static float marketRate(EconomyResource resource) {
        return INSTANCE.bazaar.getMarketRate(resource.getItem());
    }

    public static float totalCurrency() {
        float sum = 0;
        for (Mayor mayor : INSTANCE.manager.getMayorList()) {
            sum += mayor.getTotalCurrency();
        }
        return sum;
    }

    public float getMarketRate(EconomyResource resource) {
        return bazaar.getMarketRate(resource.getItem());
    }

    public int getTotalResources() {
        return manager.getAllTradedResources().size();
    }

    public int getTotalResourceTrades() {
        return manager.countTradedResources();
    }
}