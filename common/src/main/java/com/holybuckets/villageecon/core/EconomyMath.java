package com.holybuckets.villageecon.core;

/**
 * Class: EconomyMath
 * Description: PLACEHOLDER. Stateless static functions implementing the economy equations
 * (see math.txt). No Minecraft imports so it can be unit tested with plain JUnit.
 *
 * Symbols: L level, Rho base production, C reserve currency, I interest, S supply,
 * B favoribility (0..2), D market rate, Q quota fractions, Z demand dampening, R growth reward.
 */
public class EconomyMath {

    public static final String CLASS_ID = "017";

    private EconomyMath() { }

    /**
     * Fraction of resource quota reached: q_j = s_j / (2 * rho_{j, L+1})
     * @param supply s_j - current supply of the resource
     * @param nextLevelProduction rho_{j, L+1} - base production of the resource at the next village level
     */
    public static float quotaFraction(int supply, int nextLevelProduction) {
        if (nextLevelProduction <= 0) return 0f;
        return supply / (2f * nextLevelProduction);
    }

    /**
     * Profit of a particular resource during a particular cycle:
     * P_j = C*I + (1 - z*q_j)*(s*b*D)_j + floor(q_j)*r_j
     * TODO: implement when MarketState provides real market rates
     */
    public static float profit(float currency, float interestRate, float dampening,
        float quotaFraction, int supply, float favoribility, float marketRate, float growthReward) {
        //TODO
        return 0f;
    }

    /**
     * Marginal demand village i places on one more unit of resource j:
     * d_ij = del(C)*I + (1 - z*q_j)*(del(s)*b*D) + del(s)*r/q
     * Accounts for interest delta, market value delta, and average (non-floored)
     * quota bonus per unit.
     * TODO: implement when MarketState provides real market rates
     */
    public static float marginalDemand(float interestRate, float dampening, float quotaFraction,
        float favoribility, float marketRate, float growthReward) {
        //TODO
        return 0f;
    }

    /**
     * Economic growth factor reward: R_ij = SUM(C)*(growthFactor) / (V*T)
     * @param totalCurrency SUM(C) over all villages
     * @param villageCount V - total villages discovered so far
     * @param resourcesTraded T - total resources each village is trading
     */
    public static float growthReward(float totalCurrency, float growthFactor, int villageCount, int resourcesTraded) {
        if (villageCount <= 0 || resourcesTraded <= 0) return 0f;
        return totalCurrency * growthFactor / (villageCount * resourcesTraded);
    }
}
