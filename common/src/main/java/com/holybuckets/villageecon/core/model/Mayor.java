package com.holybuckets.villageecon.core.model;

import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.VillageEconConfig;
import com.holybuckets.villageecon.config.VillageEconomyJsonConfig;
import com.holybuckets.villageecon.config.model.CycleModifier;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.config.model.EconomyResource.ResourceType;
import com.holybuckets.villageecon.config.model.VillagePersonality;
import com.holybuckets.villageecon.core.EconomyMath;
import com.holybuckets.villageecon.core.MarketState;
import com.holybuckets.villageecon.core.TradeEngine;
import com.holybuckets.villageecon.core.VillageManager;
import com.holybuckets.villageecon.core.trade.Bazaar;
import com.holybuckets.villageecon.core.trade.Post;
import com.holybuckets.villageecon.entity.MayorEntity;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.*;

/**
    Mayor class handles all dynamic transactions for the village,
    the data is written to its compound tag

    The Mayor object is held in RAM by the VillageManager for the lifetime of the
    server, so a village keeps trading while its entity is unloaded. The entity is
    attached and detached as it loads; it persists this object to its compound tag.
**/
public class Mayor {

    public static final String CLASS_ID = "015";

    //Buffer below which a trade is not worth posting, in units of currency
    public static final float NEGLIGIBLE_DEMAND = 1f;

    private final ServerLevel level;
    private MayorEntity entity;
    private UUID villagerId;
    private boolean alive = true;
    private CompoundTag cachedNbt;

    private String villageChunkId;
    private int villageLevel;
    private String personalityModifierId;
    private String biomeModifierId;
    private List<String> luxuryResourceIds = new ArrayList<>();
    private static ModConfig modConfig;

    private VillagePersonality personalityModifier = VillageEconomyChunk.NEUTRAL;
    private VillagePersonality biomeModifier = VillageEconomyChunk.NEUTRAL;

    //Dynamic economy state
    private final ResourceLedger staticLedger = new ResourceLedger();
    private final ResourceLedger theoLedger = new ResourceLedger();
    private CycleModifier currentCycleModifier;
    private final Map<String, Float> demand = new LinkedHashMap<>();  //d_ij - marginal demand per resource this tick
    private final Set<EconomyResource> tradedResources = new HashSet<>();

    //** Constructors **//
    private Mayor(ServerLevel level) {
        this.level = level;
    }

    public Mayor(ServerLevel level, CompoundTag tag) {
        this(level);
        deserializeNBT(tag);
        this.addTradedResources();
    }

    public Mayor(ServerLevel level, VillageEconomyChunk village) {
        this(level, (CompoundTag) null);
        this.villageChunkId = village.getId();
        this.villageLevel = village.getVillageLevel();
        this.personalityModifierId = village.getPersonalityModifier().getId();
        this.biomeModifierId = village.getBiomeModifier().getId();
        this.luxuryResourceIds = new ArrayList<>(village.getLuxuryResourceIds());
        hydrateModifiers();

        modConfig = ModConfig.getInstance();
        float startingReserve = modConfig.getStartingReserveCurrency(villageLevel);
        this.staticLedger.setCurrency(startingReserve);
        this.theoLedger.setCurrency(startingReserve);

        this.cycleProcess();
    }


    @Nullable
    private static VillageEconomyJsonConfig config() {
        ModConfig c = ModConfig.getInstance();
        return (c != null) ? c.getEconomyConfig() : null;
    }

    private static final CycleModifier NO_CYCLE_MODIFIER = new CycleModifier(CycleModifier.NONE_ID);



    private CycleModifier cycleModifier() {
        if (currentCycleModifier != null) return currentCycleModifier;
        VillageEconomyJsonConfig c = config();
        if (c != null) {
            CycleModifier m = c.getCycleModifier(CycleModifier.NONE_ID);
            if (m != null) return this.currentCycleModifier = m;
        }
        return NO_CYCLE_MODIFIER;
    }

    private void hydrateModifiers() {
        VillageEconomyJsonConfig c = config();
        if (c == null) return;

        VillagePersonality p = c.getPersonality(personalityModifierId);
        this.personalityModifier = (p != null) ? p : VillageEconomyChunk.NEUTRAL;

        VillagePersonality b = c.getPersonality(biomeModifierId);
        this.biomeModifier = (b != null) ? b : VillageEconomyChunk.NEUTRAL;
    }


    //** Getters / Setters **//

    public MayorEntity getEntity() { return entity; }

    public UUID getVillagerId() { return villagerId; }

    public boolean isAlive() { return alive; }

    public boolean isEntityLoaded() { return entity != null && entity.isAlive(); }

    public void attachEntity(MayorEntity entity) {
        this.entity = entity;
        this.villagerId = (entity != null) ? entity.getUUID() : this.villagerId;
        this.alive = true;
    }

    public void detachEntity(MayorEntity entity) {
        if (this.entity == entity) this.entity = null;
    }

    public void setDead() {
        this.alive = false;
        this.entity = null;
    }

    public String getVillageChunkId() { return villageChunkId; }

    public void setVillageChunkId(String villageChunkId) { this.villageChunkId = villageChunkId; }

    public int getVillageLevel() { return villageLevel; }

    public void setVillageLevel(int villageLevel) { this.villageLevel = villageLevel; }

    public ResourceLedger getStaticLedger() { return staticLedger; }

    public ResourceLedger getTheoLedger() { return theoLedger; }

    public CycleModifier getCurrentCycleModifier() { return cycleModifier(); }

    public Map<String, Float> getDemand() { return demand; }

    public VillagePersonality getPersonalityModifier() { return personalityModifier; }

    public VillagePersonality getBiomeModifier() { return biomeModifier; }

    public List<String> getLuxuryResourceIds() { return luxuryResourceIds; }

    public int getQuota(String resourceId) {
        EconomyResource res = ModConfig.getInstance().getResource(resourceId);
        return EconomyMath.quota(villageLevel, res);
    }

    @Nullable
    public ServerLevel getLevel() {
        return level;
    }

    @Nullable
    public ChunkPos getChunkPos() {
        if (villageChunkId == null) return null;
        return ChunkUtil.getChunkPos(villageChunkId);
    }

    public int getBuyRadius() {
        int perLevel = ModConfig.getBalmConfig().tradeConfigs.buyRadiusPerLevelChunks;
        return Math.max(1, villageLevel) * perLevel;
    }

    @Nullable
    public VillageEconomyChunk getVillage() {
        ChunkPos pos = getChunkPos();
        if (pos == null) return null;
        VillageManager manager = VillageManager.get(getLevel());
        return (manager != null) ? manager.getVillage(pos) : null;
    }

    public List<EconomyResource> getActiveResources()
    {
        VillageEconomyJsonConfig cfg = config();
        if (cfg == null) return new ArrayList<>();

        ModConfig c = ModConfig.getInstance();
        List<EconomyResource> active = new ArrayList<>(c.getResources(ResourceType.STAPLE));

        if (villageLevel >= cfg.getBasicResourceStartLevel())
            active.addAll(c.getResources(ResourceType.BASIC));

        if (villageLevel >= cfg.getLuxuryResourceStartLevel()) {
            for (String luxuryId : luxuryResourceIds) {
                EconomyResource luxury = c.getResource(luxuryId);
                if (luxury != null) active.add(luxury);
            }
        }
        return active;
    }

    public float getTotalCurrency() {
        return staticLedger.getCurrency();
    }

    public Set<EconomyResource> getTradedResources() {
        return tradedResources;
    }

    //** Modifiers **//

    private static ResourceType resourceType(String resourceId) {
        EconomyResource resource = ModConfig.getInstance().getResource(resourceId);
        return (resource != null) ? resource.getType() : null;
    }

    public float favoribility(String resourceId) {
        ResourceType type = resourceType(resourceId);
        float b = personalityModifier.getDemandModifier(resourceId, type)
            + biomeModifier.getDemandModifier(resourceId, type)
            + cycleModifier().getDemandModifier(resourceId)
            - 2f;
        return Math.max(0f, Math.min(2f, b));
    }

    public float productionModifier(String resourceId) {
        ResourceType type = resourceType(resourceId);
        float m = personalityModifier.getProductionModifier(resourceId, type)
            + biomeModifier.getProductionModifier(resourceId, type)
            + cycleModifier().getProductionModifier(resourceId)
            - 2f;
        return Math.max(0f, m);
    }

    public float interestRate() {
        return config().getGlobalInterestRate() * personalityModifier.getInterestModifier();
    }

    public float playerPrice(String resourceId) {
        return MarketState.marketRate(resourceId) * personalityModifier.getMarkup();
    }


    //** PROCESSES **//

    /**
     * Determine demand based on ledger
     */
    public void tickProcess() {
        recalculateDemand();
        submitTradeOffers();
    }

    //Calculates marginal demand for each resource
    private void recalculateDemand()
    {
        demand.clear();
        for (EconomyResource resource : getActiveResources())
        {
            String id = resource.getResourceId();
            int supply = theoLedger.get(id);
            float q = EconomyMath.quotaFraction(villageLevel, supply, resource);
            float b = favoribility(id);

            //this village's current demand for one more unit of resource j
            float d = EconomyMath.margDemBuy(villageLevel, 1, interestRate(), q, b, resource);
            float sell = EconomyMath.margDemSale(villageLevel, 1, interestRate(), q, b, resource);
            float tradeDemand = d;
            if(sell >= 0 && sell > d) tradeDemand = -sell;

            demand.put(id, tradeDemand);
        }
    }


    private void submitTradeOffers()
    {
        ServerLevel level = getLevel();
        if (level == null) return;

        for (EconomyResource resource : getActiveResources())
        {
            String id = resource.getResourceId();
            Item item = resource.getItem();
            if (item == null) continue;

            float d = demand.getOrDefault(id, 0f);
            if (Math.abs(d) < NEGLIGIBLE_DEMAND) continue;

            float D = MarketState.marketRate(id);
            float m = Math.abs(d);
            float dReserve = rollReserveDemand(m, rollAgreeableness());

            int quantity = 1;

            if (d > 0)
            {
                float reserveBuy = Math.max(0f, D + m - dReserve);
                Post post = new Post(this, d, quantity, reserveBuy, theoLedger);
                Bazaar.buyOffer(level, item, post);
            }
            else
            {
                if (theoLedger.get(id) < quantity) continue;
                float reserveSell = Math.max(0f, D - m + dReserve);
                Post post = new Post(this, d, quantity, reserveSell, theoLedger);
                Bazaar.sellOffer(level, item, post);
            }
        }
    }

    private float rollAgreeableness() {
        double gaussian = VillageManager.RANDOM.nextGaussian();
        double sigma = ModConfig.getBalmConfig().tradeConfigs.agreeablenessStdDev;
        double rolled = gaussian*sigma + personalityModifier.getAgreeableness();
        return (float) Math.max(0d, Math.min(1d, rolled));
    }

    private float rollReserveDemand(float m, float a) {
        double mean = m * (1.5d - a);
        double sigma = m / 6d;
        double rolled = VillageManager.RANDOM.nextGaussian() * sigma + mean;
        return (float) Math.max(0d, rolled);
    }

    //Village receives pro-rated portion of cyclic resource amounts each day
    public void dailyProcess()
    {
        for (EconomyResource resource : getActiveResources()) {
            String id = resource.getResourceId();
            int produced = Math.round(resource.productionAt(villageLevel) * productionModifier(id));
            staticLedger.add(id, produced);
        }

        int cycleLength = Math.max(1, ModConfig.getDefaults().cycleLengthDays);
        float dailyRate = (interestRate() - 1f) / cycleLength;
        staticLedger.addCurrency(staticLedger.getCurrency() * dailyRate);
        cachedNbt = serializeNBT();
    }

    /**
     * Each cycle we reconcile the static ledger with the theorhetical ledger which
     * processed all potential trades.
     * - If theoLedger > staticLedger, we schedule incoming trades
     * - If theoLedger < staticLedger, we schedule outgoing trades
     *
     * - reward the village for meeting quotas, and level up if all quotas are met
     * - draw next cycle modifier
     * - Add the next cycle's production to the theoLedger so it can work on trading new resources
     * in the market
     */
    public void cycleProcess()
    {
        //1. Reconcile ledgers and schedule trades
        Map<String, Integer> diff = theoLedger.diff(staticLedger);
        for (Map.Entry<String, Integer> entry : diff.entrySet()) {
            if (entry.getValue() > 0)
                TradeEngine.scheduleIncomingTrade(this, entry.getKey(), entry.getValue());
            else if (entry.getValue() < 0)
                TradeEngine.scheduleOutgoingTrade(this, entry.getKey(), -entry.getValue());
        }

        //2. Grant quota rewards: floored quota fractions only
        boolean allQuotasMet = true;
        for (EconomyResource resource : getActiveResources())
        {
            String id = resource.getResourceId();
            float q = EconomyMath.quotaFraction(villageLevel, staticLedger.get(id), resource);
            if (q < 1f) { allQuotasMet = false; }
        }

        if(allQuotasMet) {
            float reward = EconomyMath.growthReward()*tradedResources.size();
            staticLedger.addCurrency(reward);
        }

        //3. Process level ups
        if (allQuotasMet && villageLevel < VillageEconConfig.MAX_VILLAGE_LEVEL) {
            villageLevel++;
            VillageEconomyChunk village = getVillage();
            if (village != null) village.setVillageLevel(villageLevel);
            LoggerProject.logInfo(CLASS_ID + "001", "Village " + villageChunkId + " leveled up to " + villageLevel);
        }

        //4. Rectify the theoLedger to the (post-trade) static ledger for the new cycle
        theoLedger.clear();
        theoLedger.deserializeNBT(staticLedger.serializeNBT());

        //5. Draw the cycle modifier for the next cycle
        this.currentCycleModifier = drawCycleModifier();

        //6. Add to the theoledger the new cycle's production for each resource
        for (EconomyResource resource : getActiveResources()) {
            String id = resource.getResourceId();
            int produced = Math.round(resource.productionAt(villageLevel) * productionModifier(id));
            theoLedger.add(id, produced);
        }

    }

    private void addTradedResources() {
        for (EconomyResource resource : getActiveResources()) {
            int produced = resource.productionAt(villageLevel);
            if(produced >0) tradedResources.add(resource);
        }
    }

    private CycleModifier drawCycleModifier()
    {
        var pool = ModConfig.getInstance().getCycleModifiers();
        int totalWeight = pool.stream().mapToInt(CycleModifier::getWeight).sum();
        if (totalWeight <= 0) return cycleModifier();

        int roll = VillageManager.RANDOM.nextInt(totalWeight);
        for (CycleModifier modifier : pool) {
            roll -= modifier.getWeight();
            if (roll < 0) return modifier;
        }
        return cycleModifier();
    }


    //** Static
    public static Mayor getMayor(Level level, String villageChunkId) {
        return  getMayor(level, ChunkUtil.getChunkPos(villageChunkId));
    }

    public static Mayor getMayor(Level level, ChunkPos chunkPos) {
        VillageManager manager = VillageManager.get(level);
        if (manager == null) return null;
        return manager.getMayor(chunkPos);
    }

    //** Serialization **//

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (villageChunkId != null) tag.putString("villageChunkId", villageChunkId);
        tag.putBoolean("alive", alive);
        tag.putInt("villageLevel", villageLevel);
        if (personalityModifierId != null) tag.putString("personalityModifier", personalityModifierId);
        if (biomeModifierId != null) tag.putString("biomeModifier", biomeModifierId);
        tag.putString("luxuryResources", String.join(",", luxuryResourceIds));
        tag.put("staticLedger", staticLedger.serializeNBT());
        tag.put("theoLedger", theoLedger.serializeNBT());
        tag.putString("currentCycleModifier", cycleModifier().getId());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;

        if (tag.contains("villageChunkId")) this.villageChunkId = tag.getString("villageChunkId");
        if (tag.contains("alive")) this.alive = tag.getBoolean("alive");
        this.villageLevel = tag.getInt("villageLevel");
        this.personalityModifierId = tag.getString("personalityModifier");
        this.biomeModifierId = tag.getString("biomeModifier");

        this.luxuryResourceIds = new ArrayList<>();
        String luxuries = tag.getString("luxuryResources");
        if (!luxuries.isBlank()) {
            for (String luxuryId : luxuries.split(","))
                luxuryResourceIds.add(luxuryId.trim());
        }

        hydrateModifiers();

        staticLedger.deserializeNBT(tag.getCompound("staticLedger"));
        theoLedger.deserializeNBT(tag.getCompound("theoLedger"));

        if (tag.contains("currentCycleModifier")) {
            VillageEconomyJsonConfig cfg = config();
            CycleModifier modifier = (cfg != null) ? cfg.getCycleModifier(tag.getString("currentCycleModifier")) : null;
            if (modifier != null) this.currentCycleModifier = modifier;
        }
    }

    public void syncData(MayorEntity mayorEntity) {
        if (mayorEntity == null) return;
        mayorEntity.setPendingMayorData(cachedNbt);
    }



}
