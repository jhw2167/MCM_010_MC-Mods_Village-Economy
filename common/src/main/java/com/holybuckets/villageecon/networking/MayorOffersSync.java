package com.holybuckets.villageecon.networking;

import com.holybuckets.villageecon.menu.MayorTradeOffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MayorOffersSync {

    public static final String LOCATION = "mayor_offers_sync";

    private final List<MayorTradeOffer> offers;
    private final float reserveCurrency;
    private final float projectedCurrency;
    private final String villageName;
    private final float currencyDelta;

    public MayorOffersSync(List<MayorTradeOffer> offers, float reserveCurrency, float projectedCurrency, String villageName, float currencyDelta) {
        this.offers = (offers == null) ? new ArrayList<>() : new ArrayList<>(offers);
        this.reserveCurrency = reserveCurrency;
        this.projectedCurrency = projectedCurrency;
        this.villageName = (villageName == null) ? "" : villageName;
        this.currencyDelta = currencyDelta;
    }

    public List<MayorTradeOffer> getOffers() { return Collections.unmodifiableList(offers); }

    public float getReserveCurrency() { return reserveCurrency; }

    public float getProjectedCurrency() { return projectedCurrency; }

    public String getVillageName() { return villageName; }

    public float getCurrencyDelta() { return currencyDelta; }
}
