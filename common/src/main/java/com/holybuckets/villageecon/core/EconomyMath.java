package com.holybuckets.villageecon.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.VillageEconConfig;
import com.holybuckets.villageecon.config.VillageEconomyJsonConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;

/**
 * Helper functions and resources for mananging trades and the economy
 */
public class EconomyMath {

    public static final String CLASS_ID = "017";
    private static GeneralConfig CONFIG;

    private static EconomyMath INSTANCE;
    private static float growthPool;
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
     */
    public static float quotaFraction(int level, int supply,  EconomyResource resource) {
        if(level >= VillageEconConfig.MAX_VILLAGE_LEVEL ) return 0f;
        float resourceQuota = quota(level, resource);
        if(resourceQuota <= 0f) return 0f;
        return Math.min(1f, supply / resourceQuota);
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

        float lostQuotaBonus = (overSupply >= 1 && s > overSupply ? 1 : 0) * -s* growthRewardPerResource();;
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
        float gainedQuotaBonus = (overSupply > 0 ? 0 : 1) * s * growthRewardPerResource() * weightedQuotaFrct;
        return lostInterest + (float) gainedMarketValue + gainedQuotaBonus;
    }

    public static void snapshotGrowthPool() {
        growthPool = MarketState.totalCurrency() * ModConfig.getDefaults().growthFactor;
        LoggerProject.logInfo(CLASS_ID + "001", "Growth pool snapshot: " + growthPool);
    }

    public static float getGrowthPool() {
        return growthPool;
    }

    public static float growthRewardPerResource() {
        if (INSTANCE == null) return 0f;
        int totalResources = INSTANCE.market.getTotalResources();
        if (totalResources <= 0) return 0f;
        return growthPool / totalResources;
    }

    public static boolean canDrawFromPool(float amount) {
        return amount <= growthPool;
    }

    public static float drawFromPool(float amount) {
        float drawn = Math.max(0f, Math.min(amount, growthPool));
        growthPool -= drawn;
        return drawn;
    }

    public static void addToPool(float amount) {
        if (amount <= 0f) return;
        growthPool += amount;
    }

    public static void clearGrowthPool() {
        growthPool = 0f;
    }
}
