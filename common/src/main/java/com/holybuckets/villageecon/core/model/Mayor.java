package com.holybuckets.villageecon.core.model;

import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.CycleModifier;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.config.model.VillagePersonality;
import com.holybuckets.villageecon.core.EconomyMath;
import com.holybuckets.villageecon.core.MarketState;
import com.holybuckets.villageecon.core.TradeEngine;
import com.holybuckets.villageecon.core.trade.Bazaar;
import com.holybuckets.villageecon.core.trade.Post;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Class: Mayor
 * Description: Wraps the Mayor Villager entity and holds most of the DYNAMIC data about
 * a village. The parent VillageEconomy only holds immutable data that cannot be lost
 * even if the mayor dies.
 *
 * Ledgers:
 *  - staticLedger: true current value of resources owned by the village
 *  - theoLedger: theoretical values of all village resources after speculative trades,
 *    updated every trade sequence. Rectified with staticLedger each cycle.
 */
public class Mayor {

    public static final String CLASS_ID = "015";
    private static final Random RANDOM = new Random();

    private final VillageEconomy village;       //parent, immutable village data
    private UUID villagerId;                    //UUID of the Mayor Villager entity

    //Working copies passed down from VillageEconomy on mayor creation
    private int villageLevel;
    private VillagePersonality personalityModifier;
    private VillagePersonality biomeModifier;

    //Dynamic economy state
    private final ResourceLedger staticLedger;
    private final ResourceLedger theoLedger;
    private CycleModifier currentCycleModifier;
    private final Map<String, Float> demand = new LinkedHashMap<>();  //d_ij - marginal demand per resource this tick


    //** Constructors **//

    public Mayor(VillageEconomy village, @Nullable UUID villagerId,
        int villageLevel, VillagePersonality personalityModifier, VillagePersonality biomeModifier)
    {
        this.village = village;
        this.villagerId = villagerId;
        this.villageLevel = villageLevel;
        this.personalityModifier = personalityModifier;
        this.biomeModifier = biomeModifier;
        this.staticLedger = new ResourceLedger();
        this.theoLedger = new ResourceLedger();
        this.currentCycleModifier = ModConfig.getInstance().getEconomyConfig()
            .getCycleModifier(CycleModifier.NONE_ID);
    }


    //** Getters / Setters **//

    public UUID getVillagerId() { return villagerId; }

    public void setVillagerId(UUID villagerId) { this.villagerId = villagerId; }

    public int getVillageLevel() { return villageLevel; }

    public void setVillageLevel(int villageLevel) { this.villageLevel = villageLevel; }

    public ResourceLedger getStaticLedger() { return staticLedger; }

    public ResourceLedger getTheoLedger() { return theoLedger; }

    public CycleModifier getCurrentCycleModifier() { return currentCycleModifier; }

    public Map<String, Float> getDemand() { return demand; }

    /** Resolves the live Mayor Villager entity by UUID, or null if dead / unloaded **/
    @Nullable
    public Villager getVillager() {
        if (villagerId == null) return null;
        ServerLevel level = village.getLevel();
        if (level == null) return null;
        Entity e = level.getEntity(villagerId);
        return (e instanceof Villager v) ? v : null;
    }


    //** Modifiers **//

    /**
     * Effective favoribility b_j (0..2) for a resource.
     * personality, biome and cycle modifiers are each neutral at 1 and ADDITIVE:
     * b = personality + biome + cycle - 2, clamped to [0, 2]
     */
    public float favoribility(String resourceId) {
        float b = personalityModifier.getDemandModifier(resourceId)
            + biomeModifier.getDemandModifier(resourceId)
            + currentCycleModifier.getDemandModifier(resourceId)
            - 2f;
        return Math.max(0f, Math.min(2f, b));
    }

    /** Effective production modifier for a resource; additive like favoribility, min 0 **/
    public float productionModifier(String resourceId) {
        float m = personalityModifier.getProductionModifier(resourceId)
            + biomeModifier.getProductionModifier(resourceId)
            + currentCycleModifier.getProductionModifier(resourceId)
            - 2f;
        return Math.max(0f, m);
    }

    /** Daily interest rate: globalInterestRate scaled by the personality's interest modifier **/
    public float interestRate() {
        return ModConfig.getInstance().getEconomyConfig().getGlobalInterestRate()
            * personalityModifier.getInterestModifier();
    }

    /** Price when this village sells the resource to a player: D_j * markup **/
    public float playerPrice(String resourceId) {
        return MarketState.marketRate(resourceId) * personalityModifier.getMarkup();
    }


    //** PROCESSES **//

    /**
     * tickProcess (tickTrade) - every 120 ticks demand is recalculated and buy/sell
     * offers are posted to the Bazaar; theoretical trades settle against the theoLedger
     * when the VillageManager flushes the markets.
     */
    public void tickProcess() {
        recalculateDemand();
        submitTradeOffers();
    }

    /**
     * Recalculates marginal demand d_ij for each active resource:
     * d_ij = del(C)*I + (1 - z*q_j)*(del(s)*b*D) + del(s)*r/q
     * Light form here: dampened market value of one more unit plus its average quota bonus.
     */
    private void recalculateDemand()
    {
        float z = ModConfig.getInstance().getEconomyConfig().getDemandDampeningFactor();
        demand.clear();
        for (EconomyResource resource : village.getActiveResources())
        {
            String id = resource.getResourceId();
            int supply = theoLedger.get(id);
            float q = EconomyMath.quotaFraction(supply, resource.productionAt(villageLevel + 1));
            float b = favoribility(id);
            float D = MarketState.marketRate(id);

            //(1 - z*q) * b * D : market value of one more unit, dampened as needs are met
            float d = (1f - z * q) * b * D;

            //+ r/q : average quota bonus per unit  //TODO: EconomyMath.marginalDemand once MarketState is real
            demand.put(id, d);
        }
    }

    /**
     * Posts one buy OR sell offer per active resource to the Bazaar - whichever side
     * is more profitable. The reservation price (demandReserve) is derived from the
     * true demand d, modulated by the agreeableness rolled for this tickTrade.
     */
    private void submitTradeOffers()
    {
        for (EconomyResource resource : village.getActiveResources())
        {
            String id = resource.getResourceId();
            Item item = resource.getItem();
            if (item == null) continue;

            float d = demand.getOrDefault(id, 0f);      //true value of one unit to this village
            float D = MarketState.marketRate(id);       //current market rate
            float a = rollAgreeableness(RANDOM);        //TODO: thread a world-seeded rng from VillageManager

            float buyProfit = d - D;    //profit per unit if we buy at market rate
            float sellProfit = D - d;   //profit per unit if we sell at market rate
            if (buyProfit <= 0 && sellProfit <= 0) continue;

            //TODO: derive quantity from demand strength and stock; one stack for now
            int quantity = 16;

            if (buyProfit >= sellProfit)
            {
                //a=1: pay up to the full value d; a=0: only trade at full expected profit (pay ~0)
                int reserveBuy = Math.round(a * d);
                Post post = new Post(village, Math.round(d), quantity, reserveBuy, theoLedger);
                Bazaar.buyOffer(village.getLevel(), item, post);
            }
            else
            {
                if (theoLedger.get(id) < quantity) continue;    //can't sell what we don't hold
                //a=1: sell at cost basis d; a=0: demand full expected profit on top (2d)
                int reserveSell = Math.round(d * (2f - a));
                Post post = new Post(village, Math.round(d), quantity, reserveSell, theoLedger);
                Bazaar.sellOffer(village.getLevel(), item, post);
            }
        }
    }

    /**
     * Rolls this tickTrade's agreeableness: a normal distribution centered on the
     * personality's configured agreeableness, clamped to [0, 1].
     * Takes the rng as a parameter so a seeded Random can be threaded in (see
     * OreClusterCalculator::calculateClusterLocations for the pattern).
     */
    private float rollAgreeableness(Random rng) {
        double sigma = ModConfig.getBalmConfig().tradeConfigs.agreeablenessStdDev;
        double rolled = rng.nextGaussian() * sigma + personalityModifier.getAgreeableness();
        return (float) Math.max(0d, Math.min(1d, rolled));
    }

    /**
     * dailyProcess - each day new resources and interest are granted to the village
     * derived from their cyclic constants. Added to the STATIC ledger.
     */
    public void dailyProcess()
    {
        for (EconomyResource resource : village.getActiveResources()) {
            String id = resource.getResourceId();
            int produced = Math.round(resource.productionAt(villageLevel) * productionModifier(id));
            staticLedger.add(id, produced);
        }

        //Interest accrues daily on reserve currency
        staticLedger.addCurrency(staticLedger.getCurrency() * (interestRate() - 1f));
    }

    /**
     * cycleProcess - the staticLedger is reconciled with the theoLedger.
     * Surpluses (theo > static) are resources that will be traded from other villages to
     * this one; deficits mean this village must schedule outgoing trades.
     * Reward quotas are granted, level ups processed, and new quotas and cycle
     * modifiers for the next cycle are drawn.
     */
    public void cycleProcess()
    {
        //1. Reconcile ledgers and schedule trades
        Map<String, Integer> diff = theoLedger.diff(staticLedger);
        for (Map.Entry<String, Integer> entry : diff.entrySet()) {
            if (entry.getValue() > 0)
                TradeEngine.scheduleIncomingTrade(village, entry.getKey(), entry.getValue());
            else if (entry.getValue() < 0)
                TradeEngine.scheduleOutgoingTrade(village, entry.getKey(), -entry.getValue());
        }

        //2. Grant quota rewards: floored quota fractions only
        boolean allQuotasMet = true;
        for (EconomyResource resource : village.getActiveResources())
        {
            String id = resource.getResourceId();
            float q = EconomyMath.quotaFraction(staticLedger.get(id), resource.productionAt(villageLevel + 1));
            if (q < 1f) { allQuotasMet = false; continue; }

            //TODO: real V and T counts from VillageManager / MarketState
            float reward = EconomyMath.growthReward(
                MarketState.totalCurrency(),
                ModConfig.getInstance().getEconomyConfig().getGrowthFactor(),
                1, 1);
            staticLedger.addCurrency((float) Math.floor(q) * reward);
        }

        //3. Process level ups
        if (allQuotasMet && villageLevel < com.holybuckets.villageecon.config.VillageEconConfig.MAX_VILLAGE_LEVEL) {
            villageLevel++;
            village.setVillageLevel(villageLevel);
            LoggerProject.logInfo(CLASS_ID + "001", "Village " + village.getId() + " leveled up to " + villageLevel);
        }

        //4. Rectify the theoLedger to the (post-trade) static ledger for the new cycle
        theoLedger.clear();
        theoLedger.deserializeNBT(staticLedger.serializeNBT());

        //5. Draw the cycle modifier for the next cycle
        this.currentCycleModifier = drawCycleModifier();
    }

    /** Weighted draw of one cycle modifier from the configured pool **/
    private CycleModifier drawCycleModifier()
    {
        var pool = ModConfig.getInstance().getCycleModifiers();
        int totalWeight = pool.stream().mapToInt(CycleModifier::getWeight).sum();
        if (totalWeight <= 0) return ModConfig.getInstance().getEconomyConfig().getCycleModifier(CycleModifier.NONE_ID);

        int roll = RANDOM.nextInt(totalWeight);
        for (CycleModifier modifier : pool) {
            roll -= modifier.getWeight();
            if (roll < 0) return modifier;
        }
        return ModConfig.getInstance().getEconomyConfig().getCycleModifier(CycleModifier.NONE_ID);
    }


    //** Serialization **//
    //TODO: consider persisting this data on the Villager entity itself instead of the chunk

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (villagerId != null) tag.putUUID("villagerId", villagerId);
        tag.putInt("villageLevel", villageLevel);
        tag.put("staticLedger", staticLedger.serializeNBT());
        tag.put("theoLedger", theoLedger.serializeNBT());
        if (currentCycleModifier != null) tag.putString("currentCycleModifier", currentCycleModifier.getId());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;
        if (tag.hasUUID("villagerId")) this.villagerId = tag.getUUID("villagerId");
        this.villageLevel = tag.getInt("villageLevel");
        staticLedger.deserializeNBT(tag.getCompound("staticLedger"));
        theoLedger.deserializeNBT(tag.getCompound("theoLedger"));
        if (tag.contains("currentCycleModifier")) {
            CycleModifier modifier = ModConfig.getInstance().getEconomyConfig()
                .getCycleModifier(tag.getString("currentCycleModifier"));
            if (modifier != null) this.currentCycleModifier = modifier;
        }
    }
}
