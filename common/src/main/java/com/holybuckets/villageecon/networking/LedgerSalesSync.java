package com.holybuckets.villageecon.networking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LedgerSalesSync {

    public static final String LOCATION = "ledger_sales_sync";

    private final String resourceId;
    private final float marketRate;
    private final List<Integer> salePrices;

    public LedgerSalesSync(String resourceId, float marketRate, List<Integer> salePrices) {
        this.resourceId = (resourceId == null) ? "" : resourceId;
        this.marketRate = marketRate;
        this.salePrices = (salePrices == null) ? new ArrayList<>() : new ArrayList<>(salePrices);
    }

    public String getResourceId() { return resourceId; }

    public float getMarketRate() { return marketRate; }

    public List<Integer> getSalePrices() { return Collections.unmodifiableList(salePrices); }
}
