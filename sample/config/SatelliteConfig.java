package com.holybuckets.satellite.config;

import com.holybuckets.satellite.Constants;
import net.blay09.mods.balm.api.config.reflection.Comment;
import net.blay09.mods.balm.api.config.reflection.Config;
import net.blay09.mods.balm.api.config.reflection.NestedType;

import java.util.Set;

@Config(Constants.MOD_ID)
public class SatelliteConfig {

    public static class SatelliteBlockConfig {

        @Comment("Satellite will not operate below this y level")
        public int minSatelliteWorkingHeight = 256;

        @Comment("Satellite travel rate")
        public int satelliteTravelRateChunksPerSecond = 8;

        @NestedType(String.class)
        @Comment("Surface structures the satellite block interface will seek for you. Overworld only. These locations are not validated on the client side.")
        public Set<String> satelliteStructureTargetOptions = Set.of("minecraft:desert_pyramid", "minecraft:jungle_pyramid",
         "minecraft:desert_outpost", "minecraft:pillager_outpost", "minecraft:mansion",
          "minecraft:village_plains", "minecraft:village_desert", "minecraft:village_savanna", "minecraft:village_snowy", "minecraft:village_taiga",
           "minecraft:swamp_hut", "minecraft:igloo");

    }

    public static class SatelliteDisplayUpgrades {

        @Comment("Maximum chunk sections below surface that a satellite can view by default, each section is 16 blocks tall, larger number indicates deeper reach")
        public int maxSatelliteDepthSectionDefault = 4;

        @Comment("Maximum chunk sections below surface that a satellite can view by default, each section is 16 blocks tall, larger number indicates deeper reach")
        public int maxSatelliteDepthSectionUpgraded = 12;

        //@Comment("Maximum number of chunks away from Satellite Block players may view blocks, -1 for no limit")
        //public int satelliteReachDistChunksDefault = -1;

        @Comment("Maximum number of chunks away from launch station satellite may operate, -1 for no limit")
        public int satelliteOperationalDistChunksDefault = 2048;

        @Comment("Maximum number of chunks away from launch station satellite may operate, -1 for no limit")
        public int satelliteOperationalDistChunksUpgraded = 65536;

        @Comment("Pairs ore blocks with chiseled block representations on the Satellite Display. NOTE: the block after '=' MUST be Chiselable with the chisel & bits mod or it will crash the game. Safest to use Vanilla Blocks.")
        @NestedType(String.class)
        public Set<String> oreScannerBlockMappings = Set.of(
                "minecraft:coal_ore=minecraft:coal_block",
                "minecraft:copper_ore=minecraft:copper_block",
                "minecraft:iron_ore=minecraft:iron_block",
                "minecraft:gold_ore=minecraft:gold_block",
                "minecraft:redstone_ore=minecraft:redstone_block",
                "minecraft:lapis_ore=minecraft:lapis_block",
                "minecraft:diamond_ore=minecraft:diamond_block",
                "minecraft:emerald_ore=minecraft:emerald_block",

                "minecraft:deepslate_coal_ore=minecraft:coal_block",
                "minecraft:deepslate_iron_ore=minecraft:iron_block",
                "minecraft:deepslate_gold_ore=minecraft:gold_block",
                "minecraft:deepslate_diamond_ore=minecraft:diamond_block",
                "minecraft:deepslate_emerald_ore=minecraft:emerald_block",
                "minecraft:deepslate_redstone_ore=minecraft:redstone_block",
                "minecraft:deepslate_lapis_ore=minecraft:lapis_block",

                "minecraft:stone=hbs_satellites:holo_air_block",
                "minecraft:deepslate=hbs_satellites:holo_air_block"
        );

    }

    public static class SatelliteDisplayConfig {
        //add playerRange
        @Comment("If there is no player within this range from the satellite controller, the controller turns off")
        public int playerRange = 64;

        @Comment("Display refresh rate in ticks")
        public int displayRefreshRate = 60;

        @Comment("Display refresh rate for chunks currently inhabited by a player in ticks")
        public int displayPlayerRefreshRate = 10;

        @Comment("Entity refresh rate in ticks")
        public int entityRefreshRate = 10;

        @Comment("Controller path refresh rate in ticks")
        public int controllerPathRefreshRate = 200;

        @Comment("Refresh rate for command processing in ticks")
        public int controllerUIRefreshRate = 20;
    }

    public static class EntityPingConfig {
        @NestedType(String.class)
        @Comment("Entity types produce green ping on the satellite display")
        public Set<String> friendlyEntityTypes = Set.of("minecraft:villager", "minecraft:allay");

        @Comment("Registers all mobs defined as hostile as red ping")
        public boolean addAllHostileMobsAsRedPing = true;

        @NestedType(String.class)
        @Comment("Entity types produce red ping on the satellite display") 
        public Set<String> hostileEntityTypes = Set.of("minecraft:zombie", "minecraft:zombie_villager", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider", "minecraft:enderman", "minecraft:wither_skeleton", "minecraft:husk", "minecraft:stray", "minecraft:drowned", "minecraft:phantom", "minecraft:evoker", "minecraft:vindicator", "minecraft:ravager", "minecraft:pillager", "minecraft:witch", "minecraft:blaze", "minecraft:magma_cube", "minecraft:ghast", "minecraft:endermite", "minecraft:silverfish", "minecraft:cave_spider");

        @NestedType(String.class)
        @Comment("Entity types produce a white ping")
        public Set<String> neutralEntityTypes = Set.of("minecraft:iron_golem","minecraft:wandering_trader");

        @NestedType(String.class)
        @Comment("Entity types only produce a white ping when grouped in large quantities")
        public Set<String> herdEntityTypes = Set.of("minecraft:cow", "minecraft:sheep", "minecraft:chicken", "minecraft:pig", "minecraft:horse", "minecraft:donkey", "minecraft:llama", "minecraft:wolf", "minecraft:cat");

        @NestedType(String.class)
        @Comment("Entity types produce blue ping on the satellite display (vehicles and transportation)")
        public Set<String> vehicleEntityTypes = Set.of("minecraft:minecart", "minecraft:chest_minecart", "minecraft:furnace_minecart", "minecraft:hopper_minecart", "minecraft:spawner_minecart", "minecraft:tnt_minecart", "minecraft:boat", "minecraft:chest_boat");

        @Comment("Minimum count of entities to be considered a herd")
        public int minHerdCountThreshold = 5;
    }

    public SatelliteBlockConfig satelliteConfig = new SatelliteBlockConfig();
    public SatelliteDisplayUpgrades displayUpgrades = new SatelliteDisplayUpgrades();
    public SatelliteDisplayConfig displayConfig = new SatelliteDisplayConfig();
    public EntityPingConfig entityPings = new EntityPingConfig();


}
