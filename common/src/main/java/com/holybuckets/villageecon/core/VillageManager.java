package com.holybuckets.villageecon.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.HBUtil.LevelUtil;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.datastore.WorldSaveData;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.DatastoreSaveEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.StructureLoadedEvent;
import com.holybuckets.foundation.event.custom.TickType;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.foundation.structure.StructureInfo;
import com.holybuckets.villageecon.Constants;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.VillageEconomyChunk;
import com.holybuckets.villageecon.core.trade.Bazaar;
import com.holybuckets.villageecon.core.trade.Market;
import com.holybuckets.villageecon.menu.MayorTradeMenu;
import com.holybuckets.villageecon.menu.MayorTradeOffer;
import com.holybuckets.villageecon.networking.LedgerSalesSync;
import com.holybuckets.villageecon.entity.MayorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manager class for all virtual villages, tethered to overworld for now
 * VillageManager has access to all VillageChunks and Mayors
 *
 * Mayors are virtual instance which process daily trades,
 * chunks are only used for initializing and saving static data too like village level.
 *
 * Mayors save their data to MayorEntities.
 */
public class VillageManager {

    public static final String CLASS_ID = "012";

    private static final String KEY_DAY_OF_CYCLE = "dayOfCycle";
    private static final String KEY_CYCLE_INDEX = "cycleIndex";
    private static final String KEY_MAYORS_TO_RELOAD = "mayorsToReload";
    private static final String KEY_VILLAGE_CHUNKS = "villageChunks";

    //** STATICS
    /** One manager instance per level **/
    static final Map<LevelAccessor, VillageManager> MANAGERS = new HashMap<>();
    static ModConfig MOD_CONFIG;
    static GeneralConfig GENERAL_CONFIG;
    public static Random RANDOM;

    //** VARIABLES
    private final ServerLevel level;
    private final Map<ChunkPos, VillageEconomyChunk> villages;
    private final ManagedChunkUtility chunkUtil;

    /** Mayors held in RAM for the life of the server, keyed by village chunk **/
    private final Map<ChunkPos, Mayor> mayors;

    /** Villages whose mayor died; a new mayor spawns on the next cycle **/
    private final Set<ChunkPos> villagesWithDeadOrLostMayors;

    /** Every known village chunk, persisted so mayors can be rehydrated on restart **/
    private final Map<MayorEntity, BlockPos> mayorEntities;
    private final Set<ChunkPos> persistedMayorChunkpos;

    private boolean initialSyncDone;
    private int syncIndex;

    private int dayOfCycle;
    private long cycleIndex;

    //** CONSTRUCTORS
    private VillageManager(ServerLevel level) {
        this.level = level;
        this.villages = new HashMap<>();
        this.mayors = new HashMap<>();
        this.mayorEntities = new HashMap<>();
        this.villagesWithDeadOrLostMayors = new LinkedHashSet<>();
        this.persistedMayorChunkpos = new LinkedHashSet<>();
        this.chunkUtil = ManagedChunkUtility.getInstance(level);
        this.dayOfCycle = 0;
        this.cycleIndex = 0;
        MANAGERS.put(level, this);
        LoggerProject.logInit("012000", VillageManager.class.getName());
    }


    //** GETTERS **//

    /** Manager for the given level **/
    @Nullable
    public static VillageManager get(LevelAccessor level) {
        if (level == null) return null;
        return MANAGERS.get(level);
    }

    /** Convenience accessor for the overworld manager **/
    @Nullable
    public static VillageManager getInstance() {
        if (GENERAL_CONFIG == null) return null;
        return MANAGERS.get(GENERAL_CONFIG.OVERWORLD);
    }

    private static Set<Entity.RemovalReason> DEATHS = Set.of(
        Entity.RemovalReason.DISCARDED,
        Entity.RemovalReason.KILLED
    );
    public static void mayorEntityRemoved(Level level, String villageChunkId, Entity.RemovalReason reason, MayorEntity mayorEntity)
    {
        if(!DEATHS.contains(reason)) return;
        if (level == null || villageChunkId == null) return;
        VillageManager manager = get(level);
        if (manager == null) return;

        ChunkPos pos = ChunkUtil.getChunkPos(villageChunkId);
        Mayor mayor = manager.mayors.get(pos);
        if (mayor != null && mayor.getEntity()==mayorEntity) {
            mayor.setDead();
            manager.villagesWithDeadOrLostMayors.add(pos);
        }
    }

    public ServerLevel getLevel() {
        return level;
    }

    public Map<ChunkPos, VillageEconomyChunk> getVillages() {
        return Collections.unmodifiableMap(villages);
    }

    @Nullable
    public VillageEconomyChunk getVillage(ChunkPos pos) {
        return villages.get(pos);
    }

    /** V - total number of villages discovered so far **/
    public int getVillageCount() {
        return villages.size();
    }

    public int getDayOfCycle() {
        return dayOfCycle;
    }

    public long getCycleIndex() {
        return cycleIndex;
    }

    public Set<ChunkPos> getVillagesWithDeadOrLostMayors() {
        return Collections.unmodifiableSet(villagesWithDeadOrLostMayors);
    }

    public Map<ChunkPos, Mayor> getMayors() {
        return mayors;
    }

    public List<Mayor> getMayorList() {
        return new ArrayList<>(mayors.values());
    }

    @Nullable
    public Mayor getMayor(String villageChunkId) {
        ChunkPos pos = ChunkUtil.getChunkPos(villageChunkId);
        return getMayor(pos);
    }

    public Set<EconomyResource> getAllTradedResources() {
        Set<EconomyResource> allResources = new HashSet<>();
        for(Mayor mayor : mayors.values()) {
            allResources.addAll(mayor.getTradedResources());
        }
        return allResources;
    }

    public int countTradedResources() {
        int sum = 0;
        for(Mayor mayor : mayors.values()) {
            sum += mayor.getTradedResources().size();
        }
        return sum;
    }

    @Nullable
    public Mayor getMayor(ChunkPos pos) {
        return mayors.get(pos);
    }

    public static void addVillage(ServerLevel level, VillageEconomyChunk village) {
        VillageManager manager = get(level);
        if (manager == null || village == null) return;
        manager.villages.put(village.getChunkPos(), village);
    }


    //** MAYOR LIFECYCLE **//

    public void resolveMayorEntity(@NotNull String villageChunkId, @Nullable CompoundTag tag, MayorEntity entity)
    {
        ChunkPos pos = ChunkUtil.getChunkPos(villageChunkId);
        Mayor existing = mayors.get(pos);
        persistedMayorChunkpos.remove(pos);
        if (existing != null) {
            existing.attachEntity(entity);
            return;
        }

        Mayor mayor = new Mayor(level, tag);
        mayor.setVillageChunkId(villageChunkId);
        mayor.attachEntity(entity);
        mayors.put(pos, mayor);
    }

    public void registerMayor(Mayor mayor) {
        if (mayor == null || mayor.getChunkPos() == null) return;
        mayors.put(mayor.getChunkPos(), mayor);
        villagesWithDeadOrLostMayors.remove(mayor.getChunkPos());
    }

    public static void markMayorDead(ServerLevel level, ChunkPos pos) {
        VillageManager manager = get(level);
        if (manager == null || pos == null) return;

        Mayor mayor = manager.mayors.get(pos);
        if (mayor != null) mayor.setDead();
        manager.villagesWithDeadOrLostMayors.add(pos);
    }

    public boolean needsMayor(ChunkPos pos) {
        return villagesWithDeadOrLostMayors.contains(pos);
    }


    private void handleChunkLoad(ChunkLoadingEvent.Load event)
    {
        ChunkPos pos = event.getChunkPos();
        if (!villagesWithDeadOrLostMayors.contains(pos)) return;
        respawnMayor(pos);
    }

    private void respawnMayor(ChunkPos pos)
    {
        VillageEconomyChunk village = villages.get(pos);
        if (village == null) return;

        village.createMayor();
        villagesWithDeadOrLostMayors.remove(pos);
        LoggerProject.logInfo("012004", "Respawned mayor for village " + village.getId());
    }


    //** CORE

    /**
     * Checks structure loads for villages and assigns a mayor
     */
    private void handleStructureLoaded(StructureLoadedEvent event)
    {
        StructureInfo info = event.getStructureInfo();
        if (info == null || info.getOrigin() == null) return;
        if (!MOD_CONFIG.isVillageStructure(info.getStructureLocation())) return;

        ChunkPos pos = new ChunkPos(info.getOrigin());
        if (villages.containsKey(pos)) return;

        if (event.isFirstTimeLoaded())
        {
            VillageEconomyChunk village = new VillageEconomyChunk(level, info);
            villages.put(pos, village);
            village.createMayor();
            LoggerProject.logInfo("012001", "New village economy registered at " + pos
                + " for structure " + info.getStructureLocation());
        }
        //else: existing villages are restored from chunk NBT via VillageEconomy::resolveSubData
    }

    private void tickProcess()
    {
        if (!initialSyncDone) {
            syncVillageChunks();
            initialSyncDone = true;
        }

        //calculate demand for all villages, proccess trades by flushing markets
        for (Mayor mayor : mayors.values()) {
            mayor.tickProcess();
        }
        Bazaar bazaar = Bazaar.get(level);
        if (bazaar != null) bazaar.flushMarkets();

        syncOpenTradeScreens(bazaar);
    }

    private void syncOpenTradeScreens(Bazaar bazaar)
    {
        if (bazaar == null) return;

        for (ServerPlayer player : level.players())
        {
            if (!(player.containerMenu instanceof MayorTradeMenu menu)) continue;

            MayorTradeOffer offer = menu.getSelected();
            if (offer == null || offer.getItem() == null) continue;

            Market market = bazaar.getMarket(offer.getItem());
            if (market == null) continue;

            LedgerSalesSync message = new LedgerSalesSync(
                offer.getResourceId(), market.rate(), market.getRecentSalePricesRounded());
            HBUtil.NetworkUtil.serverSendToPlayer(player, message);
        }
    }

    //Daily process involves reconciling the ledgers for each village
    public void dailyProcess()
    {
        for (Mayor mayor : mayors.values()) {
            mayor.dailyProcess();
        }


        dayOfCycle++;
        int cycleLength = ModConfig.getDefaults().cycleLengthDays;
        if (dayOfCycle >= cycleLength) {
            cycleProcess();
            dayOfCycle = 0;
            cycleIndex++;
        }
    }

    public void cycleProcess()
    {
        LoggerProject.logInfo("012002", "Processing economy cycle " + cycleIndex
            + " for " + mayors.size() + " village(s)");

        for (Mayor mayor : mayors.values()) {
            mayor.cycleProcess();
        }

        TradeEngine.executeScheduledTrades();
        syncVillageChunks();
    }

    /**
     * Force loads every village chunk so immutable village state stays in step with
     * its mayor, and any village owed a mayor gets one.
     */
    private void syncVillageChunks()
    {
        if( chunkUtil != null) return;
        for(ChunkPos pos : persistedMayorChunkpos) {
            if(chunkUtil.isLoaded(pos)) continue;
            if(HBUtil.ChunkUtil.isChunkForceLoaded(level, pos)) continue;
            HBUtil.ChunkUtil.forceLoadChunk(level, pos, Constants.MOD_ID);
        }
    }


    //** DATA WRITING
    //Keys are namespaced by level id so each level's manager keeps its own state

    private String key(String property) {
        return property + "_" + LevelUtil.toLevelId(level);
    }

    private void load(DataStore ds)
    {
        WorldSaveData worldData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);

        JsonElement dayEl = worldData.get(key(KEY_DAY_OF_CYCLE));
        this.dayOfCycle = (dayEl != null) ? dayEl.getAsInt() : 0;

        JsonElement cycleEl = worldData.get(key(KEY_CYCLE_INDEX));
        this.cycleIndex = (cycleEl != null) ? cycleEl.getAsLong() : 0;

        villagesWithDeadOrLostMayors.clear();
        loadChunkPosSet(worldData, KEY_MAYORS_TO_RELOAD, villagesWithDeadOrLostMayors);

        persistedMayorChunkpos.clear();
        loadChunkPosSet(worldData, KEY_VILLAGE_CHUNKS, persistedMayorChunkpos);

        LoggerProject.logDebug("012003", "Loaded cycle state: day " + dayOfCycle
            + " of cycle " + cycleIndex + "; " + villagesWithDeadOrLostMayors.size() + " mayor(s) queued for respawn");
    }

    private void save(DataStore ds)
    {
        WorldSaveData worldData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);
        worldData.addProperty(key(KEY_DAY_OF_CYCLE), new JsonPrimitive(dayOfCycle));
        worldData.addProperty(key(KEY_CYCLE_INDEX), new JsonPrimitive(cycleIndex));

        worldData.addProperty(key(KEY_MAYORS_TO_RELOAD), toChunkIdArray(villagesWithDeadOrLostMayors));

        Set<ChunkPos> known = mayorEntities.values().stream()
            .map(ChunkPos::new)
            .collect(Collectors.toSet());
        worldData.addProperty(key(KEY_VILLAGE_CHUNKS), toChunkIdArray(known));
    }


    private void loadChunkPosSet(WorldSaveData worldData, String property, Set<ChunkPos> target)
        {
            JsonElement el = worldData.get(key(property));
            if (el == null || !el.isJsonArray()) return;

            for (JsonElement entry : el.getAsJsonArray()) {
                try {
                    target.add(ChunkUtil.getChunkPos(entry.getAsString()));
                } catch (Exception e) {
                    LoggerProject.logWarning("012005", "Could not parse chunk id in " + property
                        + ": " + entry + ". " + e.getMessage());
                }
            }
        }

        private static JsonArray toChunkIdArray(Set<ChunkPos> positions)
        {
            JsonArray arr = new JsonArray();
            for (ChunkPos pos : positions)
                arr.add(ChunkUtil.getId(pos));
            return arr;
        }



    //** Statics

    public static void mayorEntityAdded(Level level, String villageChunkId, MayorEntity mayorEntity) {
        VillageManager manager = get(level);
        if (manager == null || mayorEntity == null) return;

        if(villageChunkId == null) return;

        if(manager.mayorEntities.containsKey(mayorEntity)) {
            manager.mayorEntities.put(mayorEntity, mayorEntity.blockPosition());
            Mayor mayor = manager.getMayor(villageChunkId);
            if (mayor != null) mayor.syncData(mayorEntity);
            return;
        }

        manager.resolveMayorEntity(villageChunkId, mayorEntity.getPendingMayorData(), mayorEntity);
        manager.mayorEntities.put(mayorEntity, mayorEntity.blockPosition());
    }

    /**
     * Designates the chunk containing origin as a village chunk and spawns its Mayor.
     * Returns the village, or null if one already exists there or creation failed.
     */
    @Nullable
    public static VillageEconomyChunk designateVillage(ServerLevel level, BlockPos origin)
    {
        VillageManager manager = get(level);
        if (manager == null || origin == null) return null;

        ChunkPos pos = new ChunkPos(origin);
        if (manager.villages.containsKey(pos)) return null;

        VillageEconomyChunk village = new VillageEconomyChunk(level, origin);
        manager.villages.put(pos, village);

        Mayor mayor = village.createMayor();
        if (mayor == null) {
            manager.villages.remove(pos);
            return null;
        }

        manager.villagesWithDeadOrLostMayors.remove(pos);
        LoggerProject.logInfo("012008", "Designated village chunk " + village.getId() + " by command");
        return village;
    }


    //** EVENTS

    public static void init(EventRegistrar reg) {
        reg.registerOnBeforeServerStarted(VillageManager::onServerStart);
        reg.registerOnServerStopped(VillageManager::onServerStopped);
        reg.registerOnLevelLoad(VillageManager::onLevelLoad, EventPriority.High);
        reg.registerOnChunkLoad(VillageManager::onChunkLoad);
        reg.registerOnStructureLoaded(VillageManager::onStructureLoaded);
        reg.registerOnServerTick(TickType.ON_120_TICKS, VillageManager::on120Ticks);
        reg.registerOnDailyTick(GeneralConfig.OVERWORLD_LOC, VillageManager::onDailyTick);
        reg.registerOnDataSave(VillageManager::onDataSave);

        VillageEconomyChunk.registerManagedChunkData();
    }

    private static void onServerStart(ServerStartingEvent event) {
        MANAGERS.clear();
        GENERAL_CONFIG = GeneralConfig.getInstance();
        MOD_CONFIG = ModConfig.getInstance();
        VillageEconomyChunk.MOD_CONFIG = ModConfig.getInstance();
        RANDOM = new Random(GENERAL_CONFIG.getWorldSeed());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        for (VillageManager manager : MANAGERS.values()) {
            manager.villages.clear();
            manager.mayors.clear();
            manager.villagesWithDeadOrLostMayors.clear();
            manager.persistedMayorChunkpos.clear();
        }
        MANAGERS.clear();
    }

    private static void onLevelLoad(LevelLoadingEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel() != GENERAL_CONFIG.OVERWORLD) return;   //TODO: support other dimensions
        if (MANAGERS.containsKey(event.getLevel())) return;

        VillageManager manager = new VillageManager((ServerLevel) event.getLevel());
        manager.load(GeneralConfig.getInstance().getDataStore());
        Bazaar bazaar = new Bazaar((ServerLevel) event.getLevel());
        MarketState.init(manager, bazaar);
    }

    private static void onChunkLoad(ChunkLoadingEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        VillageManager manager = get(event.getLevel());
        if (manager == null) return;
        manager.handleChunkLoad(event);
    }

    private static void onStructureLoaded(StructureLoadedEvent event) {
        for (VillageManager manager : MANAGERS.values())
            manager.handleStructureLoaded(event);
    }

    private static void on120Ticks(ServerTickEvent event) {
        for (VillageManager manager : MANAGERS.values())
            manager.tickProcess();
    }

    private static void onDailyTick(ServerTickEvent.DailyTickEvent event) {
        for (VillageManager manager : MANAGERS.values())
            manager.dailyProcess();
    }

    private static void onDataSave(DatastoreSaveEvent event) {
        for (VillageManager manager : MANAGERS.values())
            manager.save(event.getDataStore());
    }
}
