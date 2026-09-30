package com.holybuckets.villageecon.networking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LedgerSalesSync {

    public static final String LOCATION = "ledger_sales_sync";

    private final String resourceId;
    private final float marketRate;
    private final List<Integer> salePrices;
    private final List<Integer> saleQuantities;

    public LedgerSalesSync(String resourceId, float marketRate, List<Integer> salePrices, List<Integer> saleQuantities) {
        this.resourceId = (resourceId == null) ? "" : resourceId;
        this.marketRate = marketRate;
        this.salePrices = (salePrices == null) ? new ArrayList<>() : new ArrayList<>(salePrices);
        this.saleQuantities = (saleQuantities == null) ? new ArrayList<>() : new ArrayList<>(saleQuantities);
    }

    public String getResourceId() { return resourceId; }

    public float getMarketRate() { return marketRate; }

    public List<Integer> getSalePrices() { return Collections.unmodifiableList(salePrices); }

    public List<Integer> getSaleQuantities() { return Collections.unmodifiableList(saleQuantities); }
}
