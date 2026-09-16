package com.holybuckets.villageecon.core.trade;

import java.util.ArrayDeque;
import java.util.Deque;

/**
    * Determines Market Rate Price of a resource based on the last N trades
 */
public class MarketRate {

    public static final String CLASS_ID = "023";

    private final int window;                       //N - number of trades averaged
    private final Deque<Sample> samples = new ArrayDeque<>();

    private record Sample(float price, int quantity) { }

    public MarketRate(int window) {
        this.window = Math.max(1, window);
    }

    public void seed(float initialRate) {
        samples.clear();
        for (int i = 0; i < window; i++)
            addSample(initialRate, 1);
    }

    public void addSample(float price, int quantity) {
        if (quantity <= 0) return;
        samples.addLast(new Sample(price, quantity));
        while (samples.size() > window)
            samples.removeFirst();
    }

    public void addSample(Sale sale) {
        addSample(sale.getSalePrice(), sale.getSaleQuantity());
    }

    public float rate() {
        float totalValue = 0;
        long totalQuantity = 0;
        for (Sample s : samples) {
            totalValue += s.price() * s.quantity();
            totalQuantity += s.quantity();
        }
        if (totalQuantity <= 0) return 0f;
        return totalValue / totalQuantity;
    }

    public int sampleCount() {
        return samples.size();
    }
}
