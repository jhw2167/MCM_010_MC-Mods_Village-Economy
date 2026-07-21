package com.holybuckets.villageecon.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.datastore.WorldSaveData;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.DatastoreSaveEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.StructureLoadedEvent;
import com.holybuckets.foundation.event.custom.TickType;
import com.holybuckets.foundation.structure.StructureInfo;
import com.holybuckets.villageecon.Constants;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.core.model.VillageEconomy;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class: VillageManager
 * Description: Manages all VillageEconomy instances for the world and maintains the
 * overall cycle sequence. Initialized with the OVERWORLD level; village economies
 * currently exist in the overworld only.
 *
 * Three tick-based cyclic processes are tracked here and propagated down:
 *  - tickProcess():  every 120 ticks, demand recalculation + speculative trades (Mayor level)
 *  - dailyProcess(): each day, production and interest granted to the static ledger
 *  - cycleProcess(): each cycleLengthDays (16), ledgers reconciled, trades scheduled and
 *    executed, quota rewards granted, level ups and next-cycle modifiers processed
 */
public class VillageManager {

    public static final String CLASS_ID = "012";

    private static final String KEY_DAY_OF_CYCLE = "dayOfCycle";
    private static final String KEY_CYCLE_INDEX = "cycleIndex";

    //** STATICS
    private static VillageManager INSTANCE;
    static ModConfig MOD_CONFIG;
    static GeneralConfig GENERAL_CONFIG;

    //** VARIABLES
    private final ServerLevel overworld;
    private final Map<ChunkPos, VillageEconomy> villages;
    private int dayOfCycle;
    private long cycleIndex;


    //** CONSTRUCTORS
    private VillageManager(ServerLevel overworld) {
        this.overworld = overworld;
        this.villages = new HashMap<>();
        this.dayOfCycle = 0;
        this.cycleIndex = 0;
        LoggerProject.logInit(CLASS_ID + "000", VillageManager.class.getName());
    }


    //** GETTERS **//

    @Nullable
    public static VillageManager getInstance() {
        return INSTANCE;
    }

    public ServerLevel getOverworld() {
        return overworld;
    }

    public Map<ChunkPos, VillageEconomy> getVillages() {
        return Collections.unmodifiableMap(villages);
    }

    @Nullable
    public VillageEconomy getVillage(ChunkPos pos) {
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

    public static void addVillage(ServerLevel level, VillageEconomy village) {
        if (INSTANCE == null || village == null) return;
        INSTANCE.villages.put(village.getChunkPos(), village);
    }


    //** CORE

    /**
     * A configured village structure was loaded by HBs Foundation's structure API.
     * First time loaded: create a fresh VillageEconomy and spawn its Mayor.
     * Otherwise the existing VillageEconomy resolves itself from chunk NBT
     * via the IMangedChunkData pattern.
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
            VillageEconomy village = new VillageEconomy(overworld, info);
            villages.put(pos, village);
            village.createMayor();
            LoggerProject.logInfo(CLASS_ID + "001", "New village economy registered at " + pos
                + " for structure " + info.getStructureLocation());
        }
        //else: existing villages are restored from chunk NBT via VillageEconomy::resolveSubData
    }

    /** every 120 ticks: demand recalculation and speculative trades at the Mayor level **/
    private void tickProcess() {
        villages.values().forEach(VillageEconomy::tickProcess);
    }

    /** each day: production and interest granted to static ledgers; advances the cycle clock **/
    private void dailyProcess()
    {
        villages.values().forEach(VillageEconomy::dailyProcess);

        dayOfCycle++;
        int cycleLength = ModConfig.getDefaults().cycleLengthDays;
        if (dayOfCycle >= cycleLength) {
            cycleProcess();
            dayOfCycle = 0;
            cycleIndex++;
        }
    }

    /** each cycle: reconcile ledgers, schedule and execute trades, rewards, level ups, new modifiers **/
    private void cycleProcess()
    {
        LoggerProject.logInfo(CLASS_ID + "002", "Processing economy cycle " + cycleIndex
            + " for " + villages.size() + " village(s)");

        villages.values().forEach(VillageEconomy::cycleProcess);

        //Match and execute the trades scheduled by each village's cycleProcess
        TradeEngine.executeScheduledTrades();
    }


    //** DATA WRITING

    private void load(DataStore ds)
    {
        WorldSaveData worldData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);

        JsonElement dayEl = worldData.get(KEY_DAY_OF_CYCLE);
        this.dayOfCycle = (dayEl != null) ? dayEl.getAsInt() : 0;

        JsonElement cycleEl = worldData.get(KEY_CYCLE_INDEX);
        this.cycleIndex = (cycleEl != null) ? cycleEl.getAsLong() : 0;

        LoggerProject.logDebug(CLASS_ID + "003", "Loaded cycle state: day " + dayOfCycle
            + " of cycle " + cycleIndex);
    }

    private void save(DataStore ds)
    {
        WorldSaveData worldData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);
        worldData.addProperty(KEY_DAY_OF_CYCLE, new JsonPrimitive(dayOfCycle));
        worldData.addProperty(KEY_CYCLE_INDEX, new JsonPrimitive(cycleIndex));
    }


    //** EVENTS

    public static void init(EventRegistrar reg) {
        reg.registerOnBeforeServerStarted(VillageManager::onServerStart);
        reg.registerOnServerStopped(VillageManager::onServerStopped);
        reg.registerOnLevelLoad(VillageManager::onLevelLoad, EventPriority.High);
        reg.registerOnStructureLoaded(VillageManager::onStructureLoaded);
        reg.registerOnServerTick(TickType.ON_120_TICKS, VillageManager::on120Ticks);
        reg.registerOnDailyTick(GeneralConfig.OVERWORLD_LOC, VillageManager::onDailyTick);
        reg.registerOnDataSave(VillageManager::onDataSave);

        VillageEconomy.registerManagedChunkData();
    }

    private static void onServerStart(ServerStartingEvent event) {
        INSTANCE = null;
        GENERAL_CONFIG = GeneralConfig.getInstance();
        MOD_CONFIG = ModConfig.getInstance();
        VillageEconomy.MOD_CONFIG = ModConfig.getInstance();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        if (INSTANCE != null) INSTANCE.villages.clear();
        INSTANCE = null;
    }

    /** Initialize the manager with the OVERWORLD level once it loads **/
    private static void onLevelLoad(LevelLoadingEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel() != GENERAL_CONFIG.OVERWORLD) return;
        if (INSTANCE != null) return;

        INSTANCE = new VillageManager((ServerLevel) event.getLevel());
        INSTANCE.load(GeneralConfig.getInstance().getDataStore());
    }

    private static void onStructureLoaded(StructureLoadedEvent event) {
        if (INSTANCE == null) return;
        INSTANCE.handleStructureLoaded(event);
    }

    private static void on120Ticks(ServerTickEvent event) {
        if (INSTANCE == null) return;
        INSTANCE.tickProcess();
    }

    private static void onDailyTick(ServerTickEvent.DailyTickEvent event) {
        if (INSTANCE == null) return;
        INSTANCE.dailyProcess();
    }

    private static void onDataSave(DatastoreSaveEvent event) {
        if (INSTANCE == null) return;
        INSTANCE.save(event.getDataStore());
    }
}
