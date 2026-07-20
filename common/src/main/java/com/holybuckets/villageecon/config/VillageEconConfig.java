package com.holybuckets.villageecon.config;

import com.holybuckets.foundation.HBUtil.Validator.ConfigNumber;
import com.holybuckets.villageecon.Constants;
import net.blay09.mods.balm.api.config.reflection.Comment;
import net.blay09.mods.balm.api.config.reflection.Config;

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

    //** DEFAULTS AND VALIDATION BOUNDS **//

    public static final String DEF_VILLAGE_ECONOMY_CONFIG_PATH = "config/HBVillageEconomyConfig.json";

    /** Maximum level a village may reach, used to bound level-indexed arrays **/
    public static final int MAX_VILLAGE_LEVEL = 10;

    public static final ConfigNumber<Float> DEF_GROWTH_FACTOR = new ConfigNumber<>("growthFactor", 2f, 1f, 10f);
    public static final ConfigNumber<Float> DEF_DEMAND_DAMPENING_FACTOR = new ConfigNumber<>("demandDampeningFactor", 2f, 0f, 10f);
    public static final ConfigNumber<Float> DEF_GLOBAL_INTEREST_RATE = new ConfigNumber<>("globalInterestRate", 1.1f, 1f, 2f);
    public static final ConfigNumber<Integer> DEF_BASIC_RESOURCE_START_LEVEL = new ConfigNumber<>("basicResourceStartLevel", 5, 1, MAX_VILLAGE_LEVEL);
    public static final ConfigNumber<Integer> DEF_LUXURY_RESOURCE_START_LEVEL = new ConfigNumber<>("luxuryResourceStartLevel", 8, 1, MAX_VILLAGE_LEVEL);
    public static final ConfigNumber<Integer> DEF_ASSIGNED_LUXURY_RESOURCE_COUNT = new ConfigNumber<>("assignedLuxuryResourceCount", 2, 0, 8);
    public static final ConfigNumber<Integer> DEF_CYCLE_LENGTH_DAYS = new ConfigNumber<>("cycleLengthDays", 16, 1, 128);
    public static final ConfigNumber<Float> DEF_MARKUP = new ConfigNumber<>("markup", 1f, 0f, 16f);
    public static final ConfigNumber<Float> DEF_INTEREST_MODIFIER = new ConfigNumber<>("interestModifier", 1f, 0f, 16f);
    public static final ConfigNumber<Float> DEF_RESOURCE_MODIFIER = new ConfigNumber<>("resourceModifier", 1f, 0f, 16f);
    public static final ConfigNumber<Integer> DEF_WEIGHT = new ConfigNumber<>("weight", 10, 0, 1000);


    //** CONFIG FIELDS **//

    @Comment("devMode==true enables extra logging and debug commands for testing village economies")
    public boolean devMode = false;

    @Comment("File path to the .json file where the economy is configured: resources (staples, basics, luxuries), village personality modifiers and cycle modifiers")
    public String villageEconomyConfig = DEF_VILLAGE_ECONOMY_CONFIG_PATH;

    public static class DefaultEconomyConfigs {

        @Comment("Default growthFactor: 2. Economic growth factor, scales the bonus reward granted to each village per cycle it meets its quota. At least 1, less than 10. Overridden by the value in the economy JSON config")
        public float growthFactor = DEF_GROWTH_FACTOR.get();

        @Comment("Default demandDampeningFactor: 2. Rate at which demand falls off as a village's needs are met. Overridden by the value in the economy JSON config")
        public float demandDampeningFactor = DEF_DEMAND_DAMPENING_FACTOR.get();

        @Comment("Default globalInterestRate: 1.1. Base daily interest rate applied to each village's reserve currency, before personality modifiers. Overridden by the value in the economy JSON config")
        public float globalInterestRate = DEF_GLOBAL_INTEREST_RATE.get();

        @Comment("Default basicResourceStartLevel: 5. Village level at which villages begin producing basic resources. Overridden by the value in the economy JSON config")
        public int basicResourceStartLevel = DEF_BASIC_RESOURCE_START_LEVEL.get();

        @Comment("Default luxuryResourceStartLevel: 8. Village level at which villages begin producing their assigned luxury resources. Overridden by the value in the economy JSON config")
        public int luxuryResourceStartLevel = DEF_LUXURY_RESOURCE_START_LEVEL.get();

        @Comment("Default assignedLuxuryResourceCount: 2. Number of luxury resources assigned to each village from the weighted luxury pool. Overridden by the value in the economy JSON config")
        public int assignedLuxuryResourceCount = DEF_ASSIGNED_LUXURY_RESOURCE_COUNT.get();

        @Comment("Default cycleLengthDays: 16. Number of in-game days per economic cycle. Interest accrues daily; quotas, rewards and cycle modifiers are evaluated at each cycle end")
        public int cycleLengthDays = DEF_CYCLE_LENGTH_DAYS.get();

        @Comment("Default markup: 1. Markup rate applied when a village sells goods to a player, before personality modifiers")
        public float markup = DEF_MARKUP.get();
    }

    public DefaultEconomyConfigs defaultEconomyConfigs = new DefaultEconomyConfigs();
}
