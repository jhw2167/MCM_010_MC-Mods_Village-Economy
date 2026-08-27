package com.holybuckets.villageecon.config;

import com.holybuckets.villageecon.Constants;
import net.blay09.mods.balm.api.config.reflection.Comment;
import net.blay09.mods.balm.api.config.reflection.Config;
import net.blay09.mods.balm.api.config.reflection.NestedType;

import java.util.List;

/**
 * Class: VillageEconConfig
 * Description: Balm (toml) configuration for HBs Village Economy.
 * Holds general mod settings, the path to the player-editable JSON economy config,
 * and the default values applied when JSON fields are missing or invalid.
 *
 * NOTE: This is not the JSON config for the economy itself (resources, personalities,
 * cycle modifiers); that JSON belongs in the file specified by 'villageEconomyConfig'.
 */
@Config(Constants.MOD_ID)
public class VillageEconConfig {

    //** DEFAULTS **//

    public static final String DEF_VILLAGE_ECONOMY_CONFIG_PATH = "config/HBVillageEconomyConfig.json";

    /** Maximum level a village may reach, used to bound level-indexed arrays **/
    public static final int MAX_VILLAGE_LEVEL = 10;

    public static final float DEF_GROWTH_FACTOR = 2f;
    public static final float DEF_DEMAND_DAMPENING_FACTOR = 2f;
    public static final float DEF_GLOBAL_INTEREST_RATE = 1.1f;
    public static final int DEF_BASIC_RESOURCE_START_LEVEL = 5;
    public static final int DEF_LUXURY_RESOURCE_START_LEVEL = 8;
    public static final int DEF_ASSIGNED_LUXURY_RESOURCE_COUNT = 2;
    public static final int DEF_CYCLE_LENGTH_DAYS = 16;
    public static final float DEF_MARKUP = 1f;
    public static final float DEF_INTEREST_MODIFIER = 1f;
    public static final float DEF_RESOURCE_MODIFIER = 1f;
    public static final int DEF_WEIGHT = 10;
    public static final float DEF_AGREEABLENESS = 0.5f;


    //** CONFIG FIELDS **//

    @Comment("devMode==true enables extra logging and debug commands for testing village economies")
    public boolean devMode = false;

    @Comment("File path to the .json file where the economy is configured: resources (staples, basics, luxuries), village personality modifiers and cycle modifiers")
    public String villageEconomyConfig = DEF_VILLAGE_ECONOMY_CONFIG_PATH;

    @Comment("Structures that will be treated as villages and assigned a VillageEconomy when loaded. Add any vanilla or modded structure id here to include it in the economy")
    @NestedType(String.class)
    public List<String> villageStructures = List.of(
        "minecraft:village_plains",
        "minecraft:village_desert",
        "minecraft:village_savanna",
        "minecraft:village_snowy",
        "minecraft:village_taiga"
    );

    public static class DefaultEconomyConfigs {

        @Comment("Default growthFactor: 2. Economic growth factor, scales the bonus reward granted to each village per cycle it meets its quota. At least 1, less than 10. Overridden by the value in the economy JSON config")
        public float growthFactor = DEF_GROWTH_FACTOR;

        @Comment("Default demandDampeningFactor: 2. Rate at which demand falls off as a village's needs are met. Overridden by the value in the economy JSON config")
        public float demandDampeningFactor = DEF_DEMAND_DAMPENING_FACTOR;

        @Comment("Default globalInterestRate: 1.1. Base daily interest rate applied to each village's reserve currency, before personality modifiers. Overridden by the value in the economy JSON config")
        public float globalInterestRate = DEF_GLOBAL_INTEREST_RATE;

        @Comment("Default basicResourceStartLevel: 5. Village level at which villages begin producing basic resources. Overridden by the value in the economy JSON config")
        public int basicResourceStartLevel = DEF_BASIC_RESOURCE_START_LEVEL;

        @Comment("Default luxuryResourceStartLevel: 8. Village level at which villages begin producing their assigned luxury resources. Overridden by the value in the economy JSON config")
        public int luxuryResourceStartLevel = DEF_LUXURY_RESOURCE_START_LEVEL;

        @Comment("Default assignedLuxuryResourceCount: 2. Number of luxury resources assigned to each village from the weighted luxury pool. Overridden by the value in the economy JSON config")
        public int assignedLuxuryResourceCount = DEF_ASSIGNED_LUXURY_RESOURCE_COUNT;

        @Comment("Default cycleLengthDays: 16. Number of in-game days per economic cycle. Interest accrues daily; quotas, rewards and cycle modifiers are evaluated at each cycle end")
        public int cycleLengthDays = DEF_CYCLE_LENGTH_DAYS;

        @Comment("Default markup: 1. Markup rate applied when a village sells goods to a player, before personality modifiers")
        public float markup = DEF_MARKUP;
    }

    public DefaultEconomyConfigs defaultEconomyConfigs = new DefaultEconomyConfigs();

    public static class TradeConfigs {

        @Comment("N - number of most recent trades used in the volume-weighted moving average that sets each resource's market rate")
        public int marketRateTradeWindow = 16;

        @Comment("Small random chance (0 to 1) that a matched trade simply doesn't occur during haggling")
        public float tradeFallthroughChance = 0.05f;

        @Comment("Standard deviation of the normal distribution used to roll a village's agreeableness each trade tick, centered on its personality's agreeableness")
        public float agreeablenessStdDev = 0.15f;

        @Comment("Chunk radius per village level in which a buying village can reach sellers: radius = villageLevel * this value")
        public int buyRadiusPerLevelChunks = 32;
    }

    public TradeConfigs tradeConfigs = new TradeConfigs();
}
