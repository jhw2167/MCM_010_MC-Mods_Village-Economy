package com.holybuckets.villageecon.config;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.model.CycleModifier;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.config.model.EconomyResource.ResourceType;
import com.holybuckets.villageecon.config.model.VillagePersonality;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

/**
 * Class: ModConfig
 * Description: Singleton configuration for the HBs Village Economy mod.
 *
 * On server start, loads the economy JSON config and hydrates all resource entries
 * against the item registry. Any resources whose items fail to resolve are pruned
 * and logged, and modifier maps referencing unknown resources are warned about.
 */
public class ModConfig {

    private static final String CLASS_ID = "011";
    private static ModConfig INSTANCE;

    private VillageEconomyJsonConfig economyConfig;
    private final Set<ResourceLocation> villageStructures = new HashSet<>();

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        return INSTANCE;
    }

    private ModConfig() { }

    public static void init(EventRegistrar registrar) {
        INSTANCE = ModConfig.getInstance();
        registrar.registerOnBeforeServerStarted(ModConfig::onBeforeServerStarted, EventPriority.High);
        registrar.registerOnServerStopped(ModConfig::onServerStopped, EventPriority.Low);
    }


    //** Balm config access **//
    public static VillageEconConfig getBalmConfig() {
        VillageEconConfig config = Balm.getConfig().getActiveConfig(VillageEconConfig.class);
        return (config != null) ? config : new VillageEconConfig();
    }

    public static VillageEconConfig.DefaultEconomyConfigs getDefaults() {
        return getBalmConfig().defaultEconomyConfigs;
    }


    //** Economy config access **//
    public VillageEconomyJsonConfig getEconomyConfig() {
        return economyConfig;
    }

    public Collection<EconomyResource> getResources(ResourceType type) {
        return economyConfig.getResources(type);
    }

    public List<EconomyResource> getAllResources() {
        return economyConfig.getAllResources();
    }

    @Nullable
    public EconomyResource getResource(String resourceId) {
        return economyConfig.getResource(resourceId);
    }

    public Collection<VillagePersonality> getPersonalities() {
        return economyConfig.getPersonalities();
    }

    public Collection<CycleModifier> getCycleModifiers() {
        return economyConfig.getCycleModifiers();
    }

    public List<VillagePersonality> getPersonalitiesForBiome(ResourceLocation biome) {
        return economyConfig.getPersonalities().stream()
            .filter(p -> p.allowsBiome(biome)).toList();
    }

    public List<EconomyResource> getLuxuriesForBiome(ResourceLocation biome) {
        return economyConfig.getResources(ResourceType.LUXURY).stream()
            .filter(r -> r.allowsBiome(biome)).toList();
    }

    public Set<ResourceLocation> getVillageStructures() {
        return Collections.unmodifiableSet(villageStructures);
    }

    public boolean isVillageStructure(ResourceLocation structureLoc) {
        if (structureLoc == null) return false;
        return villageStructures.contains(structureLoc);
    }


    //** Loading and hydration **//

    private void onBeforeServerStarted()
    {
        VillageEconConfig activeConfig = getBalmConfig();
        String configPath = activeConfig.villageEconomyConfig;

        File configFile        = new File(configPath);
        File defaultConfigFile = new File(VillageEconomyJsonConfig.DEF_CONFIG_FILE_PATH);

        LoggerProject.logInfo("011000",
            "Loading village economy config from: " + configFile.getAbsolutePath());

        String json = HBUtil.FileIO.loadJsonConfigs(
            configFile,
            defaultConfigFile,
            VillageEconomyJsonConfig.buildDefaultConfig()
        );

        try {
            this.economyConfig = new VillageEconomyJsonConfig(json);
        } catch (RuntimeException e) {
            String msg = String.format("Failed to parse user village economy config JSON: %s. Error:\n %s.\n\n Default configs will be applied",
                configFile.getAbsolutePath(), e.getCause());
            LoggerProject.logError("011001", msg);
            this.economyConfig = new VillageEconomyJsonConfig(
                VillageEconomyJsonConfig.buildDefaultConfig().serialize()
            );
        }

        hydrateResources();
        validateModifierReferences();
        validateLuxuryPool();
        loadVillageStructures(activeConfig);

        LoggerProject.logInfo("011002",
            "Village economy config loaded: " + economyConfig.getAllResources().size() + " resource(s), "
            + economyConfig.getPersonalities().size() + " personality modifier(s), "
            + economyConfig.getCycleModifiers().size() + " cycle modifier(s)");
    }

    /** Resolve item / tag strings against registries and prune resources with invalid items **/
    private void hydrateResources()
    {
        List<String> toRemove = new ArrayList<>();
        for (EconomyResource resource : economyConfig.getAllResources())
        {
            resource.hydrate();
            if (!resource.isValid()) {
                LoggerProject.logError("011003",
                    "Economy resource '" + resource.getResourceId()
                    + "': item not found in registry, removing resource");
                toRemove.add(resource.getResourceId());
            }
        }
        toRemove.forEach(economyConfig::removeResource);
    }

    /** Warn about personality / cycle modifier entries that reference unknown resources **/
    private void validateModifierReferences()
    {
        for (VillagePersonality p : economyConfig.getPersonalities()) {
            warnUnknownKeys(p.getResourceDemandModifiers().keySet(), "personality '" + p.getId() + "' resourceDemandModifiers");
            warnUnknownKeys(p.getResourceProductionModifiers().keySet(), "personality '" + p.getId() + "' resourceProductionModifiers");
        }
        for (CycleModifier c : economyConfig.getCycleModifiers()) {
            warnUnknownKeys(c.getResourceDemandModifiers().keySet(), "cycle modifier '" + c.getId() + "' resourceDemandModifiers");
            warnUnknownKeys(c.getResourceProductionModifiers().keySet(), "cycle modifier '" + c.getId() + "' resourceProductionModifiers");
        }
    }

    private void warnUnknownKeys(Set<String> keys, String owner) {
        for (String key : keys) {
            if (!economyConfig.hasResource(key)) {
                LoggerProject.logWarning("011004",
                    owner + " references unknown resource '" + key + "'; the modifier will have no effect");
            }
        }
    }

    /** Ensure the luxury pool can satisfy the assigned luxury count **/
    private void validateLuxuryPool()
    {
        int poolSize = economyConfig.getResources(ResourceType.LUXURY).size();
        int assigned = economyConfig.getAssignedLuxuryResourceCount();
        if (assigned > poolSize) {
            LoggerProject.logWarning("011005",
                "assignedLuxuryResourceCount (" + assigned + ") exceeds the luxury pool size ("
                + poolSize + "); villages will be assigned at most " + poolSize + " luxuries");
        }
    }

    /** Parse the configured village structure ids into resource locations **/
    private void loadVillageStructures(VillageEconConfig activeConfig)
    {
        villageStructures.clear();
        for (String structId : activeConfig.villageStructures) {
            try {
                villageStructures.add(new ResourceLocation(structId));
            } catch (Exception e) {
                LoggerProject.logWarning("011006",
                    "Invalid village structure id in config: '" + structId + "'. " + e.getMessage());
            }
        }
    }

    private void onServerStopped() {
        INSTANCE = null;
    }

    // -----------------------------------------------------------------------
    // Static event adapters
    // -----------------------------------------------------------------------

    private static void onBeforeServerStarted(ServerStartingEvent event) {
        getInstance().onBeforeServerStarted();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        getInstance().onServerStopped();
    }
}
