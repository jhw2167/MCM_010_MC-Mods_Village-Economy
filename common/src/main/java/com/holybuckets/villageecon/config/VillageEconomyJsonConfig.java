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

/**
 * Class: VillageEconomyJsonConfig
 * Description: Parses and holds the full village economy configuration read from
 * the JSON config file (or the embedded default): global economy scalars, the
 * resource pools (staples, basics, luxuries), village personality modifiers
 * and cycle modifiers.
 *
 * The chief purpose of this class is to support serializing and deserializing the
 * JSON configuration; values should be read from this object at runtime via ModConfig.
 */
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

    /** Ordered maps preserving insertion order from the JSON arrays, keyed by id **/
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

    /** Parse directly from a JSON string **/
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
            if (root.has("growthFactor")) {
                Float v = root.get("growthFactor").getAsFloat();
                if (HBUtil.Validator.validateNumber(v, VillageEconConfig.DEF_GROWTH_FACTOR, "in economy config"))
                    this.growthFactor = v;
            }
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "001", "Error parsing growthFactor. " + e.getMessage());
        }

        try {
            if (root.has("demandDampeningFactor")) {
                Float v = root.get("demandDampeningFactor").getAsFloat();
                if (HBUtil.Validator.validateNumber(v, VillageEconConfig.DEF_DEMAND_DAMPENING_FACTOR, "in economy config"))
                    this.demandDampeningFactor = v;
            }
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "002", "Error parsing demandDampeningFactor. " + e.getMessage());
        }

        try {
            if (root.has("globalInterestRate")) {
                Float v = root.get("globalInterestRate").getAsFloat();
                if (HBUtil.Validator.validateNumber(v, VillageEconConfig.DEF_GLOBAL_INTEREST_RATE, "in economy config"))
                    this.globalInterestRate = v;
            }
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
            if (resources.has("basicResourceStartLevel")) {
                Integer v = resources.get("basicResourceStartLevel").getAsInt();
                if (HBUtil.Validator.validateNumber(v, VillageEconConfig.DEF_BASIC_RESOURCE_START_LEVEL, "in resources section"))
                    this.basicResourceStartLevel = v;
            }
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "005", "Error parsing basicResourceStartLevel. " + e.getMessage());
        }

        try {
            if (resources.has("luxuryResourceStartLevel")) {
                Integer v = resources.get("luxuryResourceStartLevel").getAsInt();
                if (HBUtil.Validator.validateNumber(v, VillageEconConfig.DEF_LUXURY_RESOURCE_START_LEVEL, "in resources section"))
                    this.luxuryResourceStartLevel = v;
            }
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "006", "Error parsing luxuryResourceStartLevel. " + e.getMessage());
        }

        try {
            if (resources.has("assignedLuxuryResourceCount")) {
                Integer v = resources.get("assignedLuxuryResourceCount").getAsInt();
                if (HBUtil.Validator.validateNumber(v, VillageEconConfig.DEF_ASSIGNED_LUXURY_RESOURCE_COUNT, "in resources section"))
                    this.assignedLuxuryResourceCount = v;
            }
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

        //Personalities - at most 1 per village, permanent
        VillagePersonality GREEDY = new VillagePersonality("greedy", 2f, 1.5f);
        GREEDY.putDemandModifier("iron_ingot", 1f);
        GREEDY.putDemandModifier("gold_ingot", 1.2f);
        GREEDY.putProductionModifier("iron_ingot", 0.5f);

        VillagePersonality LIKES_WOOD = new VillagePersonality("likes_wood", 1f, 1f);
        LIKES_WOOD.putDemandModifier("oak_log", 1.5f);
        LIKES_WOOD.putProductionModifier("oak_log", 2f);
        LIKES_WOOD.getBiomeWhiteList().add(HBUtil.LevelUtil.toBiomeResourceLocation("forest"));
        LIKES_WOOD.getBiomeWhiteList().add(HBUtil.LevelUtil.toBiomeResourceLocation("cherry_grove"));
        LIKES_WOOD.getBiomeWhiteList().add(HBUtil.LevelUtil.toBiomeResourceLocation("birch_forest"));

        config.personalityModifiers.put(GREEDY.getId(), GREEDY);
        config.personalityModifiers.put(LIKES_WOOD.getId(), LIKES_WOOD);

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

        return config;
    }
}
