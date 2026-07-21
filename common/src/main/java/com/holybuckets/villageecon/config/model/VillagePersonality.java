package com.holybuckets.villageecon.config.model;

import com.google.gson.JsonObject;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.VillageEconConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class: VillagePersonality
 * Description: Represents a single village personality configuration entry.
 * At most one personality is assigned to a village, and the assignment is permanent.
 * A personality modifies the village's markup when selling to players, its interest
 * rate, and how much it values (demand) and yields (production) each resource.
 *
 * resourceDemandModifiers / resourceProductionModifiers are keyed by resource id
 * (the raw item name used in the resources section) and default to 1.0.
 */
public class VillagePersonality {

    public static final String CLASS_ID = "008";

    private final String id;                //id is never rendered in game
    private float markup;                   //markup rate when village sells goods to player
    private float interestModifier;         //multiplier on the global interest rate
    private final Map<String, Float> resourceDemandModifiers = new LinkedHashMap<>();
    private final Map<String, Float> resourceProductionModifiers = new LinkedHashMap<>();
    private final Set<ResourceLocation> biomeWhiteList = new HashSet<>();
    private final Set<ResourceLocation> biomeBlackList = new HashSet<>();


    //** Constructors **//

    public VillagePersonality(String id) {
        this.id = (id == null) ? "" : id.trim();
        this.markup = VillageEconConfig.DEF_MARKUP;
        this.interestModifier = VillageEconConfig.DEF_INTEREST_MODIFIER;
    }

    public VillagePersonality(String id, float markup, float interestModifier) {
        this(id);
        setMarkup(markup);
        setInterestModifier(interestModifier);
    }


    //** Getters **//

    public String getId() { return id; }

    public float getMarkup() { return markup; }

    public float getInterestModifier() { return interestModifier; }

    /** How much this personality values the given resource on the market; defaults to 1 **/
    public float getDemandModifier(String resourceId) {
        return resourceDemandModifiers.getOrDefault(resourceId, 1f);
    }

    /** How well this personality produces the given resource; defaults to 1 **/
    public float getProductionModifier(String resourceId) {
        return resourceProductionModifiers.getOrDefault(resourceId, 1f);
    }

    public Map<String, Float> getResourceDemandModifiers() { return resourceDemandModifiers; }

    public Map<String, Float> getResourceProductionModifiers() { return resourceProductionModifiers; }

    public Set<ResourceLocation> getBiomeWhiteList() { return biomeWhiteList; }

    public Set<ResourceLocation> getBiomeBlackList() { return biomeBlackList; }

    /** True if this personality may be assigned to a village in the given biome **/
    public boolean allowsBiome(ResourceLocation biome) {
        if (biome == null) return true;
        if (biomeBlackList.contains(biome)) return false;
        return biomeWhiteList.isEmpty() || biomeWhiteList.contains(biome);
    }


    //** Setters **//

    public void setMarkup(Float markup) {
        if (markup == null || markup < 0) {
            LoggerProject.logWarning(CLASS_ID + "005", "Invalid markup for personality: " + id
                + ". Using default value of " + VillageEconConfig.DEF_MARKUP);
            this.markup = VillageEconConfig.DEF_MARKUP;
            return;
        }
        this.markup = markup;
    }

    public void setInterestModifier(Float interestModifier) {
        if (interestModifier == null || interestModifier < 0) {
            LoggerProject.logWarning(CLASS_ID + "006", "Invalid interestModifier for personality: " + id
                + ". Using default value of " + VillageEconConfig.DEF_INTEREST_MODIFIER);
            this.interestModifier = VillageEconConfig.DEF_INTEREST_MODIFIER;
            return;
        }
        this.interestModifier = interestModifier;
    }

    public void putDemandModifier(String resourceId, Float value) {
        putModifier(resourceDemandModifiers, resourceId, value, "resourceDemandModifiers");
    }

    public void putProductionModifier(String resourceId, Float value) {
        putModifier(resourceProductionModifiers, resourceId, value, "resourceProductionModifiers");
    }

    private void putModifier(Map<String, Float> target, String resourceId, Float value, String property) {
        if (value == null || value < 0) {
            LoggerProject.logWarning(CLASS_ID + "007", "Invalid value in " + property + " for personality: " + id
                + ". Using default value of " + VillageEconConfig.DEF_RESOURCE_MODIFIER);
            target.put(resourceId, VillageEconConfig.DEF_RESOURCE_MODIFIER);
            return;
        }
        target.put(resourceId, value);
    }


    //** Serialization **//

    public JsonObject serialize()
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("markup", markup);
        obj.addProperty("interestModifier", interestModifier);

        obj.add("resourceDemandModifiers", serializeModifierMap(resourceDemandModifiers));
        obj.add("resourceProductionModifiers", serializeModifierMap(resourceProductionModifiers));

        if (!biomeWhiteList.isEmpty()) {
            com.google.gson.JsonArray whitelist = new com.google.gson.JsonArray();
            biomeWhiteList.forEach(b -> whitelist.add(b.toString()));
            obj.add("biomeWhiteList", whitelist);
        }
        if (!biomeBlackList.isEmpty()) {
            com.google.gson.JsonArray blacklist = new com.google.gson.JsonArray();
            biomeBlackList.forEach(b -> blacklist.add(b.toString()));
            obj.add("biomeBlackList", blacklist);
        }
        return obj;
    }

    static JsonObject serializeModifierMap(Map<String, Float> map) {
        JsonObject obj = new JsonObject();
        map.forEach(obj::addProperty);
        return obj;
    }

    public static VillagePersonality deserialize(JsonObject obj)
    {
        String id = obj.has("id") ? obj.get("id").getAsString() : "";
        VillagePersonality personality = new VillagePersonality(id);

        if (id.isEmpty()) {
            LoggerProject.logError(CLASS_ID + "001", "Personality entry is missing 'id' property, entry will be pruned");
            return personality;
        }

        try {
            if (obj.has("markup"))
                personality.setMarkup(obj.get("markup").getAsFloat());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "002", "Error parsing markup for personality: " + id + ". " + e.getMessage());
        }

        try {
            if (obj.has("interestModifier"))
                personality.setInterestModifier(obj.get("interestModifier").getAsFloat());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "003", "Error parsing interestModifier for personality: " + id + ". " + e.getMessage());
        }

        deserializeModifierMap(obj, "resourceDemandModifiers", personality::putDemandModifier, id);
        deserializeModifierMap(obj, "resourceProductionModifiers", personality::putProductionModifier, id);

        EconomyResource.parseBiomeList(obj, "biomeWhiteList", personality.biomeWhiteList, "personality: " + id);
        EconomyResource.parseBiomeList(obj, "biomeBlackList", personality.biomeBlackList, "personality: " + id);

        return personality;
    }

    static void deserializeModifierMap(JsonObject obj, String property,
        java.util.function.BiConsumer<String, Float> consumer, String ownerId)
    {
        if (!obj.has(property) || !obj.get(property).isJsonObject()) return;
        JsonObject map = obj.getAsJsonObject(property);
        for (String key : map.keySet()) {
            try {
                consumer.accept(key, map.get(key).getAsFloat());
            } catch (Exception e) {
                LoggerProject.logError(CLASS_ID + "004", "Error parsing " + property + " entry '" + key
                    + "' for: " + ownerId + ". " + e.getMessage());
            }
        }
    }
}
