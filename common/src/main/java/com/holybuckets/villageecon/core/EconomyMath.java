package com.holybuckets.villageecon.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.VillageEconConfig;
import com.holybuckets.villageecon.config.VillageEconomyJsonConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;

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
    private static GeneralConfig CONFIG;

    private static EconomyMath INSTANCE;
    private MarketState market;
    private ModConfig modConfig;
    private VillageEconomyJsonConfig eConfig;

    private EconomyMath(MarketState market) {
        this.market = market;
        this.modConfig = ModConfig.getInstance();
        this.eConfig = modConfig.getEconomyConfig();
    }

     static void init(MarketState market) {
            INSTANCE = new EconomyMath(market);
     }

    public static int quota(int level, EconomyResource resource) {
        if(level >= VillageEconConfig.MAX_VILLAGE_LEVEL ) return 0;
        return 2 * resource.productionAt(level + 1);
    }

    /**
     * Fraction of resource quota reached: q_j = s_j / (2 * rho_{j, L+1})
     * @param supply s_j - current supply of the resource
     * @param level L - current village level
     */
    public static float quotaFraction(int level, int supply,  EconomyResource resource) {
        if(level >= VillageEconConfig.MAX_VILLAGE_LEVEL ) return 0f;
        float resourceQuota = quota(level, resource);
        if(resourceQuota <= 0f) return 0f;
        return Math.min(1f, supply / resourceQuota);
    }

    /**
     * Profit of a particular resource during a particular cycle:
     * P_j = C*I + (1 - z*q_j)*(s*b*D)_j + floor(q_j)*r_j
     * TODO: implement when MarketState provides real market rates
     */
    public static float profit(float currency, float interestRate, float quotaFraction,
         int supply, float favoribility, float marketRate, float growthReward) {
        //TODO
        return 0f;
    }

    /**
     * Marginal demand village i places on one more unit of resource j:
     * d_ij = del(C)*I + (1 - z*q_j)*(del(s)*b*D) + del(s)*r/q
     */
    public static float margDemSale(int vLevel, int s, float interest, float quotaFrct,
     float favor, EconomyResource resource, int overSupply) {

        float growthRate = ModConfig.getDefaults().growthFactor;
        float z = ModConfig.getDefaults().demandDampeningFactor;
        float mrkRt = INSTANCE.market.getMarketRate(resource);

        float gainedInterest = s * mrkRt * interest;
        double lostMarketValue =  -Math.exp(-z*quotaFrct) * (s * favor * mrkRt);

        float lostQuotaBonus = (overSupply >= 1 && s > overSupply ? 1 : 0) * -s*growthReward();;
        return gainedInterest + (float) lostMarketValue + lostQuotaBonus;
    }

    public static float margDemBuy(int vLevel, int s, float interest, float quotaFrct,
     float favor, EconomyResource resource, int overSupply) {
        float growthRate = ModConfig.getDefaults().growthFactor;
        float z = ModConfig.getDefaults().demandDampeningFactor;
        float mrkRt = INSTANCE.market.getMarketRate(resource);

        float lostInterest = -s * mrkRt * interest;
        double gainedMarketValue =  Math.exp(-z*quotaFrct) * (s * favor * mrkRt);

        float minQuotaFraction = quotaFraction(vLevel, 1, resource);
        float weightedQuotaFrct = (minQuotaFraction*growthRate + quotaFrct ) / (growthRate + 1);
        float gainedQuotaBonus = (overSupply > 0 ? 0 : 1) * s * growthReward() * weightedQuotaFrct;
        return lostInterest + (float) gainedMarketValue + gainedQuotaBonus;
    }

    public static float growthReward() {
        float totalCurrency = MarketState.totalCurrency();
        float growthFactor = ModConfig.getDefaults().growthFactor;
        int totalResourcesTraded = INSTANCE.market.getTotalResourceTrades();
        if (totalResourcesTraded <= 0) return 0f;
        return (totalCurrency * growthFactor) / totalResourcesTraded;
    }
}
