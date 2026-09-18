package com.holybuckets.villageecon.config;

import com.google.gson.*;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.modelInterface.IStringSerializable;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.model.CycleModifier;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.config.model.EconomyResource.ResourceType;
import com.holybuckets.villageecon.config.model.VillagePersonality;

import javax.annotation.Nullable;
import java.util.*;

public class VillageEconomyJsonConfig implements IStringSerializable {

    public static final String CLASS_ID = "010";
    public static final String DEF_CONFIG_FILE_PATH = VillageEconConfig.DEF_VILLAGE_ECONOMY_CONFIG_PATH;

    //Global economy scalars
    private float growthFactor;             //economic growth factor R scaling, at least 1, less than 10
    private float demandDampeningFactor;    //Z - rate at which demand falls off when village needs are met
    private float globalInterestRate;       //I - base interest rate for all villages, before modifiers

    //Resources section scalars
    private int basicResourceStartLevel;    //village level at which basic resources are produced
    private int luxuryResourceStartLevel;   //village level at which luxury resources are produced
    private int assignedLuxuryResourceCount;//number of luxuries assigned to each village

    private final Map<String, EconomyResource> staples = new LinkedHashMap<>();
    private final Map<String, EconomyResource> basics = new LinkedHashMap<>();
    private final Map<String, EconomyResource> luxuries = new LinkedHashMap<>();
    private final Map<String, VillagePersonality> personalityModifiers = new LinkedHashMap<>();
    private final Map<String, CycleModifier> cycleModifiers = new LinkedHashMap<>();


    //** Constructors **//
    public VillageEconomyJsonConfig() {
        VillageEconConfig.DefaultEconomyConfigs defaults = ModConfig.getDefaults();
        this.growthFactor = defaults.growthFactor;
        this.demandDampeningFactor = defaults.demandDampeningFactor;
        this.globalInterestRate = defaults.globalInterestRate;
        this.basicResourceStartLevel = defaults.basicResourceStartLevel;
        this.luxuryResourceStartLevel = defaults.luxuryResourceStartLevel;
        this.assignedLuxuryResourceCount = defaults.assignedLuxuryResourceCount;
    }

    public VillageEconomyJsonConfig(String jsonString) {
        this();
        deserialize(jsonString);
    }


    //** Getters **//
    public float getGrowthFactor() { return growthFactor; }

    public float getDemandDampeningFactor() { return demandDampeningFactor; }

    public float getGlobalInterestRate() { return globalInterestRate; }

    public int getBasicResourceStartLevel() { return basicResourceStartLevel; }

    public int getLuxuryResourceStartLevel() { return luxuryResourceStartLevel; }

    public int getAssignedLuxuryResourceCount() { return assignedLuxuryResourceCount; }

    public Collection<EconomyResource> getResources(ResourceType type) {
        return switch (type) {
            case STAPLE -> Collections.unmodifiableCollection(staples.values());
            case BASIC -> Collections.unmodifiableCollection(basics.values());
            case LUXURY -> Collections.unmodifiableCollection(luxuries.values());
        };
    }

    /** All resources across the three pools, in definition order **/
    public List<EconomyResource> getAllResources() {
        List<EconomyResource> all = new ArrayList<>();
        all.addAll(staples.values());
        all.addAll(basics.values());
        all.addAll(luxuries.values());
        return all;
    }

    @Nullable
    public EconomyResource getResource(String resourceId) {
        if (staples.containsKey(resourceId)) return staples.get(resourceId);
        if (basics.containsKey(resourceId)) return basics.get(resourceId);
        return luxuries.get(resourceId);
    }

    public boolean hasResource(String resourceId) {
        return getResource(resourceId) != null;
    }

    public Collection<VillagePersonality> getPersonalities() {
        return Collections.unmodifiableCollection(personalityModifiers.values());
    }

    @Nullable
    public VillagePersonality getPersonality(String id) {
        return personalityModifiers.get(id);
    }

    public Collection<CycleModifier> getCycleModifiers() {
        return Collections.unmodifiableCollection(cycleModifiers.values());
    }

    @Nullable
    public CycleModifier getCycleModifier(String id) {
        return cycleModifiers.get(id);
    }

    /** Removes a resource by id from whichever pool holds it. Used during registry resolution pruning **/
    public void removeResource(String resourceId) {
        staples.remove(resourceId);
        basics.remove(resourceId);
        luxuries.remove(resourceId);
    }


    //** SERIALIZERS **//

    @Override
    public String serialize()
    {
        JsonObject root = new JsonObject();
        root.addProperty("growthFactor", growthFactor);
        root.addProperty("demandDampeningFactor", demandDampeningFactor);
        root.addProperty("globalInterestRate", globalInterestRate);

        JsonObject resources = new JsonObject();
        resources.addProperty("basicResourceStartLevel", basicResourceStartLevel);
        resources.addProperty("luxuryResourceStartLevel", luxuryResourceStartLevel);
        resources.addProperty("assignedLuxuryResourceCount", assignedLuxuryResourceCount);
        resources.add("staples", serializeResourcePool(staples));
        resources.add("basics", serializeResourcePool(basics));
        resources.add("luxuries", serializeResourcePool(luxuries));
        root.add("resources", resources);

        JsonArray personalities = new JsonArray();
        personalityModifiers.values().forEach(p -> personalities.add(p.serialize()));
        root.add("personalityModifiers", personalities);

        JsonArray cycles = new JsonArray();
        cycleModifiers.values().forEach(c -> cycles.add(c.serialize()));
        root.add("cycleModifiers", cycles);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(root);
    }

    private static JsonArray serializeResourcePool(Map<String, EconomyResource> pool) {
        JsonArray arr = new JsonArray();
        pool.values().forEach(r -> arr.add(r.serialize()));
        return arr;
    }

    @Override
    public void deserialize(String jsonString) throws RuntimeException
    {
        if (jsonString == null || jsonString.isBlank()) return;

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(jsonString);
            if (!parsed.isJsonObject())
                throw new JsonParseException("Expected a JSON object at the root of the config");
            root = parsed.getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON format for VillageEconomyJsonConfig", e);
        }

        parseScalars(root);
        parseResources(root);
        parsePersonalities(root);
        parseCycleModifiers(root);
    }

    private void parseScalars(JsonObject root)
    {
        try {
            if (root.has("growthFactor"))
                this.growthFactor = root.get("growthFactor").getAsFloat();
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "001", "Error parsing growthFactor. " + e.getMessage());
        }

        try {
            if (root.has("demandDampeningFactor"))
                this.demandDampeningFactor = root.get("demandDampeningFactor").getAsFloat();
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "002", "Error parsing demandDampeningFactor. " + e.getMessage());
        }

        try {
            if (root.has("globalInterestRate"))
                this.globalInterestRate = root.get("globalInterestRate").getAsFloat();
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "003", "Error parsing globalInterestRate. " + e.getMessage());
        }
    }

    private void parseResources(JsonObject root)
    {
        if (!root.has("resources") || !root.get("resources").isJsonObject()) {
            LoggerProject.logWarning(CLASS_ID + "004", "Economy config has no 'resources' section, default resources will be applied");
            return;
        }
        JsonObject resources = root.getAsJsonObject("resources");

        try {
            if (resources.has("basicResourceStartLevel"))
                this.basicResourceStartLevel = resources.get("basicResourceStartLevel").getAsInt();
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "005", "Error parsing basicResourceStartLevel. " + e.getMessage());
        }

        try {
            if (resources.has("luxuryResourceStartLevel"))
                this.luxuryResourceStartLevel = resources.get("luxuryResourceStartLevel").getAsInt();
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "006", "Error parsing luxuryResourceStartLevel. " + e.getMessage());
        }

        try {
            if (resources.has("assignedLuxuryResourceCount"))
                this.assignedLuxuryResourceCount = resources.get("assignedLuxuryResourceCount").getAsInt();
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "007", "Error parsing assignedLuxuryResourceCount. " + e.getMessage());
        }

        parseResourcePool(resources, "staples", ResourceType.STAPLE, staples);
        parseResourcePool(resources, "basics", ResourceType.BASIC, basics);
        parseResourcePool(resources, "luxuries", ResourceType.LUXURY, luxuries);
    }

    private void parseResourcePool(JsonObject resources, String property, ResourceType type, Map<String, EconomyResource> target)
    {
        target.clear();
        if (!resources.has(property) || !resources.get(property).isJsonArray()) return;

        JsonArray arr = resources.getAsJsonArray(property);
        for (JsonElement element : arr)
        {
            if (!element.isJsonObject()) continue;
            try {
                EconomyResource resource = EconomyResource.deserialize(element.getAsJsonObject(), type);
                if (resource.getResourceId() != null && !resource.getResourceId().isEmpty())
                    target.put(resource.getResourceId(), resource);
            } catch (Exception e) {
                LoggerProject.logError(CLASS_ID + "008", "Error deserializing a resource in '" + property + "'. " + e.getMessage());
            }
        }
    }

    private void parsePersonalities(JsonObject root)
    {
        personalityModifiers.clear();
        if (!root.has("personalityModifiers") || !root.get("personalityModifiers").isJsonArray()) return;

        JsonArray arr = root.getAsJsonArray("personalityModifiers");
        for (JsonElement element : arr)
        {
            if (!element.isJsonObject()) continue;
            try {
                VillagePersonality personality = VillagePersonality.deserialize(element.getAsJsonObject());
                if (personality.getId() != null && !personality.getId().isEmpty())
                    personalityModifiers.put(personality.getId(), personality);
            } catch (Exception e) {
                LoggerProject.logError(CLASS_ID + "009", "Error deserializing a personality modifier. " + e.getMessage());
            }
        }
    }

    private void parseCycleModifiers(JsonObject root)
    {
        cycleModifiers.clear();
        if (root.has("cycleModifiers") && root.get("cycleModifiers").isJsonArray())
        {
            JsonArray arr = root.getAsJsonArray("cycleModifiers");
            for (JsonElement element : arr)
            {
                if (!element.isJsonObject()) continue;
                try {
                    CycleModifier modifier = CycleModifier.deserialize(element.getAsJsonObject());
                    if (modifier.getId() != null && !modifier.getId().isEmpty())
                        cycleModifiers.put(modifier.getId(), modifier);
                } catch (Exception e) {
                    LoggerProject.logError(CLASS_ID + "010", "Error deserializing a cycle modifier. " + e.getMessage());
                }
            }
        }

        //Villages must always be able to draw a harmless modifier
        if (!cycleModifiers.containsKey(CycleModifier.NONE_ID)) {
            LoggerProject.logWarning(CLASS_ID + "011",
                "cycleModifiers is missing the '" + CycleModifier.NONE_ID + "' modifier; adding it with default weight so villages aren't always afflicted with something");
            cycleModifiers.put(CycleModifier.NONE_ID, new CycleModifier(CycleModifier.NONE_ID, CycleModifier.NONE_ID, 50));
        }
    }


    //** DEFAULTS **//

    public static VillageEconomyJsonConfig buildDefaultConfig()
    {
        VillageEconomyJsonConfig config = new VillageEconomyJsonConfig();

        //Staples - bought and sold by villages at all levels
        EconomyResource OAK_LOG = new EconomyResource(ResourceType.STAPLE, "oak_log",
            List.of(16, 16, 16, 24, 32, 48, 80, 160, 320, 320),
            List.of(8, 8, 8, 12, 16, 24, 40, 80, 160, 160));
        OAK_LOG.setUseTagsRaw("#minecraft:logs");

        EconomyResource BREAD = new EconomyResource(ResourceType.STAPLE, "bread",
            List.of(16, 16, 24, 32, 48, 64, 96, 160, 256, 256),
            List.of(12, 12, 16, 24, 32, 48, 64, 96, 128, 128));

        config.staples.put(OAK_LOG.getResourceId(), OAK_LOG);
        config.staples.put(BREAD.getResourceId(), BREAD);

        //Basics - only produced by the village at basicResourceStartLevel and up
        EconomyResource IRON = new EconomyResource(ResourceType.BASIC, "iron_ingot",
            List.of(0, 0, 0, 0, 8, 12, 16, 24, 32, 48),
            List.of(2, 2, 4, 4, 8, 8, 12, 16, 24, 24));
        config.basics.put(IRON.getResourceId(), IRON);

        //Luxuries - only produced at luxuryResourceStartLevel and up, assigned per village by weight
        EconomyResource GOLD = new EconomyResource(ResourceType.LUXURY, "gold_ingot",
            List.of(0, 0, 0, 0, 0, 0, 0, 8, 12, 16),
            List.of(0, 0, 0, 0, 1, 2, 2, 4, 6, 8));
        GOLD.setWeight(20);
        GOLD.getBiomeWhiteList().add(HBUtil.LevelUtil.toBiomeResourceLocation("desert"));
        GOLD.getBiomeWhiteList().add(HBUtil.LevelUtil.toBiomeResourceLocation("badlands"));

        EconomyResource CAKE = new EconomyResource(ResourceType.LUXURY, "cake",
            List.of(0, 0, 0, 0, 0, 0, 0, 4, 8, 12),
            List.of(0, 0, 0, 0, 1, 1, 2, 2, 4, 6));
        CAKE.setWeight(10);
        CAKE.getBiomeBlackList().add(HBUtil.LevelUtil.toBiomeResourceLocation("desert"));
        CAKE.getBiomeBlackList().add(HBUtil.LevelUtil.toBiomeResourceLocation("badlands"));
        CAKE.getBiomeBlackList().add(HBUtil.LevelUtil.toBiomeResourceLocation("snowy_plains"));
        CAKE.getBiomeBlackList().add(HBUtil.LevelUtil.toBiomeResourceLocation("taiga"));

        config.luxuries.put(GOLD.getResourceId(), GOLD);
        config.luxuries.put(CAKE.getResourceId(), CAKE);

        addPersonalities(config);

        return config;
    }


    private static void biomes(VillagePersonality p, String... biomes) {
        for (String biome : biomes)
            p.getBiomeWhiteList().add(HBUtil.LevelUtil.toBiomeResourceLocation(biome));
    }

    //Different classifications of personalities
    // production - only modifies production
    // demand - only modifies demand
    // biome - has demand and production modifiers based on what we would expect for the biome its in, these should be general descriptors like "desert, forest, wet, coastal, barren, cliffside, arable, magical"
    // temperment - list of greedy, generous, industrious, lazy, competitive, wise, foolish, friendly, strong, weak, artistic, musical, workmanlike, noble,
    private static void addPersonalities(VillageEconomyJsonConfig config) {
        addBiomePersonalities(config);
        addTempermentPersonalities(config);
    }

    private static void addBiomePersonalities(VillageEconomyJsonConfig config)
    {
        VillagePersonality DESERT = personality(config, "desert", 1.15f, 1.05f, 0.45f);
        DESERT.putTypeProductionStage(ResourceType.STAPLE, -1);
        DESERT.putTypeDemandStage(ResourceType.STAPLE, 1);
        DESERT.putProductionModifier("bread", 0.6f);
        DESERT.putProductionModifier("oak_log", 0.4f);
        DESERT.putDemandModifier("oak_log", 1.4f);
        biomes(DESERT, "desert", "badlands", "wooded_badlands", "eroded_badlands");

        VillagePersonality FOREST = personality(config, "forest", 0.95f, 1f, 0.55f);
        FOREST.putTypeProductionStage(ResourceType.STAPLE, 1);
        FOREST.putProductionModifier("oak_log", 1.2f);
        FOREST.putDemandModifier("oak_log", 0.6f);
        FOREST.putDemandModifier("iron_ingot", 1.2f);
        biomes(FOREST, "forest", "birch_forest", "old_growth_birch_forest", "dark_forest",
            "flower_forest", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "jungle");

        VillagePersonality WET = personality(config, "wet", 0.9f, 0.95f, 0.6f);
        WET.putTypeProductionStage(ResourceType.BASIC, -1);
        WET.putTypeDemandStage(ResourceType.BASIC, 1);
        WET.putProductionModifier("bread", 1.2f);
        WET.putProductionModifier("oak_log", 1.2f);
        biomes(WET, "swamp", "mangrove_swamp", "river", "frozen_river", "lush_caves");

        VillagePersonality COASTAL = personality(config, "coastal", 1.1f, 1.05f, 0.7f);
        COASTAL.putTypeProductionStage(ResourceType.LUXURY, 1);
        COASTAL.putTypeDemandStage(ResourceType.LUXURY, 1);
        COASTAL.putProductionModifier("bread", 1.1f);
        COASTAL.putDemandModifier("gold_ingot", 1.3f);
        biomes(COASTAL, "beach", "snowy_beach", "stony_shore");

        VillagePersonality BARREN = personality(config, "barren", 1.2f, 1f, 0.4f);
        BARREN.putTypeProductionStage(ResourceType.STAPLE, -1);
        BARREN.putTypeProductionStage(ResourceType.BASIC, 1);
        BARREN.putTypeDemandStage(ResourceType.STAPLE, 1);
        BARREN.putProductionModifier("iron_ingot", 1.2f);
        BARREN.putProductionModifier("bread", 0.6f);
        BARREN.putDemandModifier("bread", 1.4f);
        biomes(BARREN, "stony_peaks", "jagged_peaks", "frozen_peaks", "snowy_slopes",
            "windswept_gravelly_hills", "ice_spikes");

        VillagePersonality CLIFFSIDE = personality(config, "cliffside", 1.3f, 1.1f, 0.3f);
        CLIFFSIDE.putTypeProductionStage(ResourceType.BASIC, 1);
        CLIFFSIDE.putTypeDemandStage(ResourceType.STAPLE, 1);
        biomes(CLIFFSIDE, "windswept_hills", "windswept_forest", "grove", "meadow");

        VillagePersonality ARABLE = personality(config, "arable", 0.9f, 1f, 0.65f);
        ARABLE.putTypeProductionStage(ResourceType.STAPLE, 1);
        ARABLE.putTypeDemandStage(ResourceType.LUXURY, 1);
        ARABLE.putProductionModifier("bread", 1.2f);
        ARABLE.putDemandModifier("bread", 0.5f);
        ARABLE.putDemandModifier("gold_ingot", 1.2f);
        biomes(ARABLE, "plains", "sunflower_plains", "savanna", "savanna_plateau", "cherry_grove");

        VillagePersonality MAGICAL = personality(config, "magical", 1.25f, 1.1f, 0.5f);
        MAGICAL.putTypeProductionStage(ResourceType.LUXURY, 2);
        MAGICAL.putTypeProductionStage(ResourceType.STAPLE, -1);
        MAGICAL.putTypeDemandStage(ResourceType.BASIC, 1);
        MAGICAL.putProductionModifier("cake", 1.2f);
        MAGICAL.putDemandModifier("iron_ingot", 1.3f);
        biomes(MAGICAL, "mushroom_fields", "deep_dark", "dripstone_caves");
    }

    private static void addTempermentPersonalities(VillageEconomyJsonConfig config)
    {
        VillagePersonality GREEDY = personality(config, "greedy", 2f, 1.2f, 0.15f);
        GREEDY.putTypeDemandStage(ResourceType.LUXURY, 1);
        GREEDY.putDemandModifier("gold_ingot", 1.4f);

        VillagePersonality GENEROUS = personality(config, "generous", 0.7f, 0.9f, 0.9f);
        GENEROUS.putTypeDemandStage(ResourceType.LUXURY, -1);
        GENEROUS.putTypeProductionStage(ResourceType.STAPLE, 1);

        VillagePersonality INDUSTRIOUS = personality(config, "industrious", 1f, 1.1f, 0.6f);
        INDUSTRIOUS.putTypeProductionStage(ResourceType.STAPLE, 1);
        INDUSTRIOUS.putTypeProductionStage(ResourceType.BASIC, 1);

        VillagePersonality LAZY = personality(config, "lazy", 1.05f, 0.9f, 0.75f);
        LAZY.putTypeProductionStage(ResourceType.STAPLE, -1);
        LAZY.putTypeProductionStage(ResourceType.BASIC, -1);
        LAZY.putTypeDemandStage(ResourceType.STAPLE, 1);

        VillagePersonality COMPETITIVE = personality(config, "competitive", 1.3f, 1.2f, 0.25f);
        COMPETITIVE.putTypeProductionStage(ResourceType.BASIC, 1);
        COMPETITIVE.putTypeDemandStage(ResourceType.BASIC, 1);
        COMPETITIVE.putTypeDemandStage(ResourceType.LUXURY, 1);

        VillagePersonality WISE = personality(config, "wise", 1.1f, 1.15f, 0.55f);
        WISE.putTypeProductionStage(ResourceType.LUXURY, 1);
        WISE.putTypeDemandStage(ResourceType.STAPLE, -1);

        VillagePersonality FOOLISH = personality(config, "foolish", 0.85f, 0.9f, 0.95f);
        FOOLISH.putTypeProductionStage(ResourceType.BASIC, -1);
        FOOLISH.putTypeDemandStage(ResourceType.LUXURY, 2);

        VillagePersonality FRIENDLY = personality(config, "friendly", 0.8f, 1f, 0.95f);
        FRIENDLY.putTypeProductionStage(ResourceType.STAPLE, 1);

        VillagePersonality STRONG = personality(config, "strong", 1.05f, 1f, 0.5f);
        STRONG.putTypeProductionStage(ResourceType.BASIC, 1);
        STRONG.putProductionModifier("oak_log", 1.2f);

        VillagePersonality WEAK = personality(config, "weak", 0.95f, 0.95f, 0.8f);
        WEAK.putTypeProductionStage(ResourceType.BASIC, -1);
        WEAK.putTypeDemandStage(ResourceType.BASIC, 1);
        WEAK.putDemandModifier("iron_ingot", 1.4f);

        VillagePersonality ARTISTIC = personality(config, "artistic", 1.2f, 1f, 0.6f);
        ARTISTIC.putTypeProductionStage(ResourceType.LUXURY, 2);
        ARTISTIC.putTypeProductionStage(ResourceType.STAPLE, -1);
        ARTISTIC.putTypeDemandStage(ResourceType.LUXURY, 1);
        ARTISTIC.putProductionModifier("cake", 1.2f);

        VillagePersonality MUSICAL = personality(config, "musical", 1.15f, 1f, 0.85f);
        MUSICAL.putTypeProductionStage(ResourceType.LUXURY, 1);
        MUSICAL.putTypeDemandStage(ResourceType.LUXURY, 1);

        VillagePersonality WORKMANLIKE = personality(config, "workmanlike", 1f, 1.05f, 0.7f);
        WORKMANLIKE.putTypeProductionStage(ResourceType.STAPLE, 1);
        WORKMANLIKE.putTypeProductionStage(ResourceType.BASIC, 1);
        WORKMANLIKE.putTypeProductionStage(ResourceType.LUXURY, -1);
        WORKMANLIKE.putTypeDemandStage(ResourceType.LUXURY, -1);

        VillagePersonality NOBLE = personality(config, "noble", 1.45f, 1.1f, 0.35f);
        NOBLE.putTypeProductionStage(ResourceType.LUXURY, 1);
        NOBLE.putTypeDemandStage(ResourceType.LUXURY, 1);
        NOBLE.putProductionModifier("gold_ingot", 1.2f);
        NOBLE.putDemandModifier("gold_ingot", 1.35f);
    }

        private static VillagePersonality personality(VillageEconomyJsonConfig config, String id,
                                                      float markup, float interestModifier, float agreeableness)
        {
            VillagePersonality p = new VillagePersonality(id, markup, interestModifier);
            p.setAgreeableness(agreeableness);
            config.personalityModifiers.put(p.getId(), p);
            return p;
        }


    private static void addCycleModifiers(VillageEconomyJsonConfig config) {

        //Cycle modifiers - 1 drawn per village per cycle, additive with personality
        CycleModifier NONE = new CycleModifier(CycleModifier.NONE_ID, "none", 50);

        CycleModifier DROUGHT = new CycleModifier("drought", "A Drought", 10);
        DROUGHT.putProductionModifier("bread", 0.5f);

        CycleModifier GOLD_RUSH = new CycleModifier("gold_rush", "A Gold Rush", 5);
        GOLD_RUSH.putDemandModifier("gold_ingot", 1.5f);
        GOLD_RUSH.putProductionModifier("gold_ingot", 2f);

        config.cycleModifiers.put(NONE.getId(), NONE);
        config.cycleModifiers.put(DROUGHT.getId(), DROUGHT);
        config.cycleModifiers.put(GOLD_RUSH.getId(), GOLD_RUSH);

    }

}
