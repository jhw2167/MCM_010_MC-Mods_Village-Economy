package com.holybuckets.villageecon.config.model;

import com.google.gson.JsonObject;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.VillageEconConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class: CycleModifier
 * Description: Represents a single cycle modifier configuration entry.
 * One modifier is drawn (by weight) and assigned to each village at the start of each
 * economic cycle, further modifying the village's production and demand for that cycle.
 *
 * Cycle modifiers are ADDITIVE with personality modifiers, not multiplicative:
 * effective modifier = personalityModifier + (cycleModifier - 1), so the neutral
 * value for any resource is 1. Config authors should always include a "none"
 * modifier with a large weight so villages aren't always afflicted with something.
 */
public class CycleModifier {

    public static final String CLASS_ID = "009";

    public static final String NONE_ID = "none";

    private final String id;                //id is never rendered in game
    private String displayName;             //name shown to players when the modifier is announced
    private int weight;                     //weight against total pool that this modifier is drawn for a cycle
    private final Map<String, Float> resourceDemandModifiers = new LinkedHashMap<>();
    private final Map<String, Float> resourceProductionModifiers = new LinkedHashMap<>();


    //** Constructors **//

    public CycleModifier(String id) {
        this.id = (id == null) ? "" : id.trim();
        this.displayName = this.id;
        this.weight = VillageEconConfig.DEF_WEIGHT;
    }

    public CycleModifier(String id, String displayName, int weight) {
        this(id);
        if (displayName != null && !displayName.isBlank())
            this.displayName = displayName;
        setWeight(weight);
    }


    //** Getters **//

    public String getId() { return id; }

    public String getDisplayName() { return displayName; }

    public int getWeight() { return weight; }

    public boolean isNone() { return NONE_ID.equalsIgnoreCase(id); }

    /** Demand modifier applied this cycle for the given resource; defaults to 1 (neutral) **/
    public float getDemandModifier(String resourceId) {
        return resourceDemandModifiers.getOrDefault(resourceId, 1f);
    }

    /** Production modifier applied this cycle for the given resource; defaults to 1 (neutral) **/
    public float getProductionModifier(String resourceId) {
        return resourceProductionModifiers.getOrDefault(resourceId, 1f);
    }

    public Map<String, Float> getResourceDemandModifiers() { return resourceDemandModifiers; }

    public Map<String, Float> getResourceProductionModifiers() { return resourceProductionModifiers; }


    //** Setters **//

    public void setWeight(Integer weight) {
        if (weight == null || weight < 0) {
            LoggerProject.logWarning(CLASS_ID + "004", "Invalid weight for cycle modifier: " + id
                + ". Using default value of " + VillageEconConfig.DEF_WEIGHT);
            this.weight = VillageEconConfig.DEF_WEIGHT;
            return;
        }
        this.weight = weight;
    }

    public void putDemandModifier(String resourceId, Float value) {
        putModifier(resourceDemandModifiers, resourceId, value, "resourceDemandModifiers");
    }

    public void putProductionModifier(String resourceId, Float value) {
        putModifier(resourceProductionModifiers, resourceId, value, "resourceProductionModifiers");
    }

    private void putModifier(Map<String, Float> target, String resourceId, Float value, String property) {
        if (value == null || value < 0) {
            LoggerProject.logWarning(CLASS_ID + "005", "Invalid value in " + property + " for cycle modifier: " + id
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
        obj.addProperty("displayName", displayName);
        obj.addProperty("weight", weight);
        if (!resourceDemandModifiers.isEmpty())
            obj.add("resourceDemandModifiers", VillagePersonality.serializeModifierMap(resourceDemandModifiers));
        if (!resourceProductionModifiers.isEmpty())
            obj.add("resourceProductionModifiers", VillagePersonality.serializeModifierMap(resourceProductionModifiers));
        return obj;
    }

    public static CycleModifier deserialize(JsonObject obj)
    {
        String id = obj.has("id") ? obj.get("id").getAsString() : "";
        CycleModifier modifier = new CycleModifier(id);

        if (id.isEmpty()) {
            LoggerProject.logError(CLASS_ID + "001", "Cycle modifier entry is missing 'id' property, entry will be pruned");
            return modifier;
        }

        try {
            if (obj.has("displayName"))
                modifier.displayName = obj.get("displayName").getAsString();
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "002", "Error parsing displayName for cycle modifier: " + id + ". " + e.getMessage());
        }

        try {
            if (obj.has("weight"))
                modifier.setWeight(obj.get("weight").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "003", "Error parsing weight for cycle modifier: " + id + ". " + e.getMessage());
        }

        VillagePersonality.deserializeModifierMap(obj, "resourceDemandModifiers", modifier::putDemandModifier, "cycle modifier: " + id);
        VillagePersonality.deserializeModifierMap(obj, "resourceProductionModifiers", modifier::putProductionModifier, "cycle modifier: " + id);

        return modifier;
    }
}
