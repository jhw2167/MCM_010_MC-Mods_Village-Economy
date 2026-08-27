package com.holybuckets.villageecon.core.trade;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Class: MarketRate
 * Description: Volume-weighted moving average of the last N trades for one resource.
 * N comes from the toml config (marketRateTradeWindow).
 *
 * On server start the initial rate r/q is added N times, so the seed value is
 * slowly phased out as real trades fill the window.
 */
public class MarketRate {

    public static final String CLASS_ID = "023";

    private final int window;                       //N - number of trades averaged
    private final Deque<Sample> samples = new ArrayDeque<>();

    private record Sample(float price, int quantity) { }

    public MarketRate(int window) {
        this.window = Math.max(1, window);
    }

    /** Seeds the calculator with the initial rate, added N times so it phases out **/
    public void seed(float initialRate) {
        samples.clear();
        for (int i = 0; i < window; i++)
            addSample(initialRate, 1);
    }

    /** Records a trade; evicts the oldest sample once the window is full **/
    public void addSample(float price, int quantity) {
        if (quantity <= 0) return;
        samples.addLast(new Sample(price, quantity));
        while (samples.size() > window)
            samples.removeFirst();
    }

    public void addSample(Sale sale) {
        addSample(sale.getSalePrice(), sale.getSaleQuantity());
    }

    /** Current market rate D: volume-weighted average over the window **/
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
