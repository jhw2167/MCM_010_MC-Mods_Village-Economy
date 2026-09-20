package com.holybuckets.villageecon.config.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.VillageEconConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class EconomyResource {

    public static final String CLASS_ID = "007";


    public enum ResourceType { STAPLE, BASIC, LUXURY }

    private final ResourceType type;
    private final String itemIdRaw;
    private String useTagsRaw;
    private int startProduction;            //base production Rho at startProductionAtLevel
    private int startProductionAtLevel;     //village level at which this resource first produces
    private float productionMultiplier;
    private float consumptionFraction;
    private int weight;
    private final Set<ResourceLocation> biomeWhiteList = new HashSet<>();
    private final Set<ResourceLocation> biomeBlackList = new HashSet<>();

    private Item item;                      //hydrated item
    private TagKey<Item> tag;               //hydrated tag


    //** Constructors **//

    public EconomyResource(ResourceType type, String itemIdRaw) {
        this.type = (type == null) ? ResourceType.STAPLE : type;
        this.itemIdRaw = (itemIdRaw == null) ? "" : itemIdRaw.trim();
        this.startProduction = VillageEconConfig.DEF_START_PRODUCTION;
        this.startProductionAtLevel = VillageEconConfig.DEF_START_PRODUCTION_AT_LEVEL;
        this.productionMultiplier = VillageEconConfig.DEF_PRODUCTION_MULTIPLIER;
        this.consumptionFraction = VillageEconConfig.DEF_CONSUMPTION_FRACTION;
        this.weight = VillageEconConfig.DEF_WEIGHT;
    }

    public EconomyResource(ResourceType type, String itemIdRaw, int startProduction,
        int startProductionAtLevel, float productionMultiplier, float consumptionFraction) {
        this(type, itemIdRaw);
        setStartProduction(startProduction);
        setStartProductionAtLevel(startProductionAtLevel);
        setProductionMultiplier(productionMultiplier);
        setConsumptionFraction(consumptionFraction);
    }


    //** Getters **//

    public ResourceType getType() { return type; }


    public String getResourceId() { return itemIdRaw; }

    public String getUseTagsRaw() { return useTagsRaw; }

    @Nullable
    public Item getItem() { return item; }

    @Nullable
    public TagKey<Item> getTag() { return tag; }

    public int getWeight() { return weight; }

    public Set<ResourceLocation> getBiomeWhiteList() { return biomeWhiteList; }

    public Set<ResourceLocation> getBiomeBlackList() { return biomeBlackList; }

    public int getStartProduction() { return startProduction; }

    public int getStartProductionAtLevel() { return startProductionAtLevel; }

    public float getProductionMultiplier() { return productionMultiplier; }

    public float getConsumptionFraction() { return consumptionFraction; }

    public int getStartLevel() {
        return startProductionAtLevel;
    }

    public boolean producesAt(int level) {
        return level >= startProductionAtLevel;
    }

    /** takes village level, NOT village index, so add 1 in argument */
    public int productionAt(int level) {
        if(type==ResourceType.STAPLE) {
            if (level <= startProductionAtLevel)
                return startProduction;
        }
        if (!producesAt(level)) return 0;
        int steps = level - startProductionAtLevel;
        return Math.round(startProduction * (float) Math.pow(productionMultiplier, steps));
    }

    public int consumptionAt(int level) {
        return Math.round(productionAt(level) * consumptionFraction);
    }


    public int netProductionAt(int level) {
        return productionAt(level) - consumptionAt(level);
    }


    //** Setters / mutation used during deserialization **//

    public void setUseTagsRaw(String useTagsRaw) { this.useTagsRaw = useTagsRaw; }

    public void setStartProduction(Integer startProduction) {
        if (startProduction == null || startProduction < 0) {
            LoggerProject.logWarning(CLASS_ID + "010", "Invalid startProduction for resource: " + itemIdRaw
                + ". Using default value of " + VillageEconConfig.DEF_START_PRODUCTION);
            this.startProduction = VillageEconConfig.DEF_START_PRODUCTION;
            return;
        }
        this.startProduction = startProduction;
    }

    public void setStartProductionAtLevel(Integer startProductionAtLevel) {
        if (startProductionAtLevel == null || startProductionAtLevel < 0) {
            LoggerProject.logWarning(CLASS_ID + "014", "Invalid startProductionAtLevel for resource: " + itemIdRaw
                + ". Using default value of " + VillageEconConfig.DEF_START_PRODUCTION_AT_LEVEL);
            this.startProductionAtLevel = VillageEconConfig.DEF_START_PRODUCTION_AT_LEVEL;
            return;
        }
        this.startProductionAtLevel = startProductionAtLevel;
    }

    public void setProductionMultiplier(Float productionMultiplier) {
        if (productionMultiplier == null || productionMultiplier <= 0f) {
            LoggerProject.logWarning(CLASS_ID + "011", "Invalid productionMultiplier for resource: " + itemIdRaw
                + ". Using default value of " + VillageEconConfig.DEF_PRODUCTION_MULTIPLIER);
            this.productionMultiplier = VillageEconConfig.DEF_PRODUCTION_MULTIPLIER;
            return;
        }
        this.productionMultiplier = productionMultiplier;
    }

    public void setConsumptionFraction(Float consumptionFraction) {
        if (consumptionFraction == null || consumptionFraction < 0f || consumptionFraction > 1f) {
            LoggerProject.logWarning(CLASS_ID + "012", "Invalid consumptionFraction for resource: " + itemIdRaw
                + ". Using default value of " + VillageEconConfig.DEF_CONSUMPTION_FRACTION);
            this.consumptionFraction = VillageEconConfig.DEF_CONSUMPTION_FRACTION;
            return;
        }
        this.consumptionFraction = consumptionFraction;
    }

    public void setWeight(Integer weight) {
        if (weight == null || weight < 0) {
            LoggerProject.logWarning(CLASS_ID + "009", "Invalid weight for resource: " + itemIdRaw
                + ". Using default value of " + VillageEconConfig.DEF_WEIGHT);
            this.weight = VillageEconConfig.DEF_WEIGHT;
            return;
        }
        this.weight = weight;
    }


    //** Hydration and validation **//

    public void hydrate() {
        this.item = HBUtil.ItemUtil.itemNameToItem(itemIdRaw);
        if (useTagsRaw != null && !useTagsRaw.isBlank())
        {
            String tagId = useTagsRaw.trim();
            if (tagId.startsWith("#")) tagId = tagId.substring(1);
            if (!tagId.contains(":")) tagId = "minecraft:" + tagId;
            try {
                this.tag = TagKey.create(Registries.ITEM, new ResourceLocation(tagId));
            } catch (Exception e) {
                LoggerProject.logError(CLASS_ID + "001", "Invalid useTags '" + useTagsRaw
                    + "' for resource: " + itemIdRaw + ". " + e.getMessage());
                this.tag = null;
            }
        }
    }

    /** True if the hydrated item resolved to a real registry entry **/
    public boolean isValid() {
        return item != null && !item.equals(Items.AIR);
    }

    /** True if the given stack is a valid item of exchange for this resource **/
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (tag != null && stack.is(tag)) return true;
        return item != null && stack.is(item);
    }

    /** True if this resource may be assigned to a village in the given biome **/
    public boolean allowsBiome(ResourceLocation biome) {
        if (biome == null) return true;
        if (biomeBlackList.contains(biome)) return false;
        return biomeWhiteList.isEmpty() || biomeWhiteList.contains(biome);
    }


    //** Serialization **//

    public JsonObject serialize()
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("item", itemIdRaw);
        if (useTagsRaw != null && !useTagsRaw.isBlank())
            obj.addProperty("useTags", useTagsRaw);

        obj.addProperty("startProduction", startProduction);
        obj.addProperty("startProductionAtLevel", startProductionAtLevel);
        obj.addProperty("productionMultiplier", productionMultiplier);
        obj.addProperty("consumptionFraction", consumptionFraction);

        if (type == ResourceType.LUXURY)
            obj.addProperty("weight", weight);

        if (!biomeWhiteList.isEmpty()) {
            JsonArray whitelist = new JsonArray();
            biomeWhiteList.forEach(b -> whitelist.add(b.toString()));
            obj.add("biomeWhiteList", whitelist);
        }
        if (!biomeBlackList.isEmpty()) {
            JsonArray blacklist = new JsonArray();
            biomeBlackList.forEach(b -> blacklist.add(b.toString()));
            obj.add("biomeBlackList", blacklist);
        }
        return obj;
    }

    public static EconomyResource deserialize(JsonObject obj, ResourceType type)
    {
        String itemId = obj.has("item") ? obj.get("item").getAsString() : "";
        EconomyResource resource = new EconomyResource(type, itemId);

        if (itemId.isEmpty()) {
            LoggerProject.logError(CLASS_ID + "002", "Economy resource entry is missing 'item' property, entry will be pruned");
            return resource;
        }

        try {
            if (obj.has("useTags"))
                resource.setUseTagsRaw(obj.get("useTags").getAsString());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "003", "Error parsing useTags for resource: " + itemId + ". " + e.getMessage());
        }

        try {
            if (obj.has("startProduction"))
                resource.setStartProduction(obj.get("startProduction").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "004", "Error parsing startProduction for resource: " + itemId
                + ". Using default value. " + e.getMessage());
        }

        try {
            if (obj.has("startProductionAtLevel"))
                resource.setStartProductionAtLevel(obj.get("startProductionAtLevel").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "015", "Error parsing startProductionAtLevel for resource: " + itemId
                + ". Using default value. " + e.getMessage());
        }

        try {
            if (obj.has("productionMultiplier"))
                resource.setProductionMultiplier(obj.get("productionMultiplier").getAsFloat());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "005", "Error parsing productionMultiplier for resource: " + itemId
                + ". Using default value. " + e.getMessage());
        }

        try {
            if (obj.has("consumptionFraction"))
                resource.setConsumptionFraction(obj.get("consumptionFraction").getAsFloat());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "013", "Error parsing consumptionFraction for resource: " + itemId
                + ". Using default value. " + e.getMessage());
        }

        try {
            if (obj.has("weight"))
                resource.setWeight(obj.get("weight").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "006", "Error parsing weight for resource: " + itemId + ". " + e.getMessage());
        }

        parseBiomeList(obj, "biomeWhiteList", resource.biomeWhiteList, itemId);
        parseBiomeList(obj, "biomeBlackList", resource.biomeBlackList, itemId);

        return resource;
    }

    static void parseBiomeList(JsonObject obj, String property, Set<ResourceLocation> target, String ownerId) {
        try {
            target.clear();
            if (obj.has(property) && obj.get(property).isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray(property);
                for (int i = 0; i < arr.size(); i++) {
                    target.add(HBUtil.LevelUtil.toBiomeResourceLocation(arr.get(i).getAsString()));
                }
            }
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "008", "Error parsing " + property
                + " for: " + ownerId + ". " + e.getMessage());
        }
    }
}
