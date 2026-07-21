package com.holybuckets.satellite.config;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.satellite.LoggerProject;
import com.holybuckets.satellite.SatelliteMain;
import com.holybuckets.satellite.particle.ModParticles;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.*;

public class ModConfig {
    private static Set<EntityType<?>> friendlyEntities = new HashSet<>();
    private static Set<EntityType<?>> hostileEntities = new HashSet<>();
    private static Set<EntityType<?>> neutralEntities = new HashSet<>();
    private static Set<EntityType<?>> herdEntities = new HashSet<>();
    private static Set<EntityType<?>> vehicleEntities = new HashSet<>();

    private static Set<ResourceLocation> trackedStructures = new HashSet<>();

    private static Map<Block, Block> oreScannerBlockMap = new LinkedHashMap<>();
    public static int MAX_ORE_SCANNER_MAPPINGS = 64;

    public static void init(EventRegistrar reg) {
        reg.registerOnBeforeServerStarted( e -> loadDynamicConfigs(e.getServer().registryAccess()), EventPriority.High);
    }

    public static void onConnectedToServer(Level level) {
        if(GeneralConfig.getInstance().isIntegrated()) return;
        loadDynamicConfigs(level.registryAccess());
    }

    private static void loadDynamicConfigs(RegistryAccess reg)
    {
        //** ENTITES

        friendlyEntities.clear();
        hostileEntities.clear();
        neutralEntities.clear();
        herdEntities.clear();
        vehicleEntities.clear();

        Registry<EntityType<?>> registry = reg.registryOrThrow(Registries.ENTITY_TYPE);
        SatelliteConfig config = SatelliteMain.CONFIG;
        
        // Convert friendly entities
        for (String entityId : config.entityPings.friendlyEntityTypes) {
            addEntity(entityId, registry, friendlyEntities);
        }

        // Convert hostile entities
        for (String entityId : config.entityPings.hostileEntityTypes) {
            addEntity(entityId, registry, hostileEntities);
        }
        // Convert neutral entities
        for (String entityId : config.entityPings.neutralEntityTypes) {
            addEntity(entityId, registry, neutralEntities);
        }
        // Convert herd entities
        for (String entityId : config.entityPings.herdEntityTypes) {
            addEntity(entityId, registry, herdEntities);
        }
        // Convert vehicle entities
        for (String entityId : config.entityPings.vehicleEntityTypes) {
            addEntity(entityId, registry, vehicleEntities);
        }

        //STRUCTURES
        trackedStructures.clear();

        //Registry<StructureType<?>> structureRegistry = reg.registryOrThrow(Registries.STRUCTURE_TYPE);
        for (String structId : config.satelliteConfig.satelliteStructureTargetOptions ) {
            //StructureType<?> type = structureRegistry.get(loc);
            ResourceLocation loc = new ResourceLocation(structId);
            if (loc != null) trackedStructures.add(loc);
            else LoggerProject.logWarning("010001", "Satellite Config: Could not find structure type for id: " + structId);
        }


        //Ores
        oreScannerBlockMap.clear();
        Registry<Block> blockRegistry = reg.registryOrThrow(Registries.BLOCK);
        int i= 0;
        for (String pair : config.displayUpgrades.oreScannerBlockMappings )
        {
            if(i++ >= MAX_ORE_SCANNER_MAPPINGS) break;
            String[] parts = pair.split("=");
            if (parts.length != 2) {
                LoggerProject.logWarning("010002", "Satellite Config: Invalid ore scanner block mapping: " + pair);
                continue;
            }
            Block oreBlock = blockRegistry.get(new ResourceLocation(parts[0]));
            Block storageBlock = blockRegistry.get(new ResourceLocation(parts[1]));
            if (oreBlock == null) {
                LoggerProject.logWarning("010003", "Satellite Config: Could not find ore block for id: " + parts[0]);
                continue;
            }
            if (storageBlock == null) {
                LoggerProject.logWarning("010004", "Satellite Config: Could not find storage block for id: " + parts[1]);
                continue;
            }
            oreScannerBlockMap.put(oreBlock, storageBlock);
        }

    }

    private static void addEntity(String entityId, Registry<EntityType<?>> registry, Set<EntityType<?>> targetSet) {
        EntityType<?>  type = registry.get(new ResourceLocation(entityId));
        if (type != null) targetSet.add(type);
        else LoggerProject.logWarning("010000", "Satellite Config: Could not find entity type for id: " + entityId);
    }

    public static Set<EntityType<?>> getFriendlyEntities() {
        return friendlyEntities;
    }

    public static Set<EntityType<?>> getHostileEntities() {
        return hostileEntities;
    }

    public static Set<EntityType<?>> getNeutralEntities() {
        return neutralEntities;
    }

    public static Set<EntityType<?>> getHerdEntities() {
        return herdEntities;
    }

    public static Set<EntityType<?>> getVehicleEntities() {
        return vehicleEntities;
    }

    public static Set<ResourceLocation> getTrackedStructures() {
        return trackedStructures;
    }

    public static Map<Block, Block> getOreScannerBlocks() {
        return Collections.unmodifiableMap(oreScannerBlockMap);
    }

    public static int getSignalStrength(ParticleOptions particles) {

        if (particles == ModParticles.greenPing.getType()) {
            return 3;
        } else if (particles == ModParticles.basePing.getType()) {
            return 6;
        } else if (particles == ModParticles.bluePing.getType()) {
            return 9;
        } else if (particles == ModParticles.redPing.getType()) {
            return 12;
        }

        return -1;
    }

    public static int getmaxSatelliteDepthSectionDefaults(boolean upgraded) {
        int defaultVal = SatelliteMain.CONFIG.displayUpgrades.maxSatelliteDepthSectionDefault;
        if(defaultVal<1) return Integer.MAX_VALUE;
        if(upgraded) {
            int upgradedVal = SatelliteMain.CONFIG.displayUpgrades.maxSatelliteDepthSectionUpgraded;
            if(upgradedVal<1) return Integer.MAX_VALUE;
            return upgradedVal;
        }
        return defaultVal;
    }

    public static int getSatelliteOperationalDistChunks(boolean upgraded) {
        int defaultVal = SatelliteMain.CONFIG.displayUpgrades.satelliteOperationalDistChunksDefault;
        if(defaultVal<1) return Integer.MAX_VALUE;
        if(upgraded) {
            int upgradedVal = SatelliteMain.CONFIG.displayUpgrades.satelliteOperationalDistChunksUpgraded;
            if(upgradedVal<1) return Integer.MAX_VALUE;
            return upgradedVal;
        }
        return defaultVal;
    }
}
