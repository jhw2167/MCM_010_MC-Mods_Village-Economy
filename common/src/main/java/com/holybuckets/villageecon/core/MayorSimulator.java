package com.holybuckets.villageecon.core;

import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.model.BiasModifier;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.util.LeastSquares;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MayorSimulator {

    public static final String CLASS_ID = "030";

    private static final int CYCLES = 2;
    private static final int SAMPLES = 40;
    private static final int MAX_SAMPLES = 10000;
    private static final float BIAS_STRENGTH = 0.5f;
    private static final float MIN_BIAS = 0.25f;
    private static final float MAX_BIAS = 2.0f;

    private static final int[] FIB = { -21, -13, -8, -5, -3, -2, -1, 0, 1, 2, 3, 5, 8, 13, 21 };

    private final Random RANDOM;

    private final Mayor enterpriser;

    public MayorSimulator(Mayor mayor, Random random) {
        this.enterpriser = mayor;
        this.RANDOM = random;
    }

    public void processEnterpriser()
    {
        var resources = enterpriser.getTradedResources().stream().toList();
        int n = resources.size();
        int samples = Math.min(MAX_SAMPLES, Math.max(SAMPLES, 10 * (2 * n + 1)));

        double[][] x = new double[samples][2 * n];
        double[] y = new double[samples];

        for (int s = 0; s < samples; s++)
        {
            int[] quantities = new int[n];
            for (int r = 0; r < n; r++)
                quantities[r] = FIB[RANDOM.nextInt(FIB.length)];

            //profit
            y[s] = simulate(resources, quantities);

            for (int r = 0; r < n; r++) {
                x[s][2 * r] = quantities[r];
                x[s][2 * r + 1] = quantities[r] * quantities[r];
            }
        }

        double[] beta;
        try {
            beta = LeastSquares.solve(x, y);
        } catch (Exception e) {
            LoggerProject.logWarning("030001", "Could not fit trade surface for "
                + enterpriser.getName() + ". " + e.getMessage());
            return;
        }

        applyResults(resources, enterpriser.getBiasModifier(), beta);
    }

    /** adjusts the simulation against the fibonacci resource quantities before profit calc **/
    private int simulate(List<EconomyResource> resources, int[] quantities)
    {
        enterpriser.initSimulation(CYCLES);

        for (int r = 0; r < resources.size(); r++) {
            EconomyResource resource = resources.get(r);
            int quantity = quantities[r];
            if (quantity == 0) continue;

            for (int c = 0; c < CYCLES; c++)
                enterpriser.adjustSimulation(resource, c, quantity, resourceCost(resource, quantity));
        }

        return profit();
    }

    /** tallies up profit based on sampled trades **/
    private int profit()
    {
        int total = enterpriser.forecastReserve();
        for (EconomyResource r : enterpriser.getTradedResources()) {
            total += enterpriser.forecastResourceWorth(r);
            total += enterpriser.forecastResourceBonus(r);
        }
        return total;
    }

    /** resource cost reserves the integer value for a cost of purchasing the resource at the current market rate. returns a negative **/
    private static int resourceCost(EconomyResource resource, int quantity) {
        float rate = MarketState.marketRate(resource.getResourceId());
        return Math.round(-quantity * rate);
    }

    private void applyResults(List<EconomyResource> resources, BiasModifier modifier, double[] beta)
    {
        modifier.clear();

        for (int r = 0; r < resources.size(); r++)
        {
            EconomyResource resource = resources.get(r);
            double slope = beta[1 + 2 * r];

            float rate = Math.max(0.01f, MarketState.marketRate(resource.getResourceId()));
            double profitRatio = slope / rate;

            float favor = (float) Math.exp(BIAS_STRENGTH * profitRatio);
            modifier.addBias(resource, Math.max(MIN_BIAS, Math.min(MAX_BIAS, favor)));
        }

        //LoggerProject.logDebug("030002", "Trade bias for " + enterpriser.getName() + ": " + modifier);
    }
}
