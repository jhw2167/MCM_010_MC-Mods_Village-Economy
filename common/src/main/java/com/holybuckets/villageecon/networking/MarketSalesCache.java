package com.holybuckets.villageecon.networking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketSalesCache {

    private static final Map<String, List<Integer>> SALES = new HashMap<>();
    private static final Map<String, Float> RATES = new HashMap<>();

    public static void accept(LedgerSalesSync message) {
        if (message == null) return;
        SALES.put(message.getResourceId(), new ArrayList<>(message.getSalePrices()));
        RATES.put(message.getResourceId(), message.getMarketRate());
    }

    public static List<Integer> getSales(String resourceId) {
        List<Integer> sales = SALES.get(resourceId);
        return (sales != null) ? sales : new ArrayList<>();
    }

    public static float getRate(String resourceId, float fallback) {
        Float rate = RATES.get(resourceId);
        return (rate != null) ? rate : fallback;
    }

    public static void clear() {
        SALES.clear();
        RATES.clear();
    }
}
