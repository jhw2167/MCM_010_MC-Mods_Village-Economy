package com.holybuckets.villageecon.config.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class: EconomyResource
 * Description: Represents a single tradeable resource configuration entry (staple, basic or luxury).
 * Holds the raw JSON-backed strings and hydrates them into registry-backed types
 * (Item, TagKey) at server start via hydrate().
 *
 * production and consumption are level-indexed arrays: index i holds the base
 * production/consumption (Rho) for a village at level i+1. Arrays shorter than
 * MAX_VILLAGE_LEVEL repeat their last entry for higher levels.
 */
public class EconomyResource {

    public static final String CLASS_ID = "007";

    /** Resource class determines at which village level the resource is produced **/
    public enum ResourceType { STAPLE, BASIC, LUXURY }

    public static final List<Integer> DEF_PRODUCTION = List.of(16, 16, 16, 24, 32, 48, 80, 160, 320, 320);
    public static final List<Integer> DEF_CONSUMPTION = List.of(8, 8, 8, 12, 16, 24, 40, 80, 160, 160);

    private final ResourceType type;
    private final String itemIdRaw;         //serialized item name, e.g. "oak_log"
    private String useTagsRaw;              //serialized tag, e.g. "#minecraft:logs", accepts any item in tag for exchange
    private List<Integer> production;       //base production Rho, indexed by village level
    private List<Integer> consumption;      //base consumption, indexed by village level
    private int weight;                     //luxury only: weight against total pool for village assignment
    private final Set<ResourceLocation> biomeWhiteList = new HashSet<>();
    private final Set<ResourceLocation> biomeBlackList = new HashSet<>();

    private Item item;                      //hydrated item
    private TagKey<Item> tag;               //hydrated tag


    //** Constructors **//

    public EconomyResource(ResourceType type, String itemIdRaw) {
        this.type = (type == null) ? ResourceType.STAPLE : type;
        this.itemIdRaw = (itemIdRaw == null) ? "" : itemIdRaw.trim();
        this.production = new ArrayList<>(DEF_PRODUCTION);
        this.consumption = new ArrayList<>(DEF_CONSUMPTION);
        this.weight = VillageEconConfig.DEF_WEIGHT;
    }

    public EconomyResource(ResourceType type, String itemIdRaw, List<Integer> production, List<Integer> consumption) {
        this(type, itemIdRaw);
        if (production != null && !production.isEmpty()) this.production = new ArrayList<>(production);
        if (consumption != null && !consumption.isEmpty()) this.consumption = new ArrayList<>(consumption);
    }


    //** Getters **//

    public ResourceType getType() { return type; }

    /** Unique id of this resource on the market; the raw item name as written in JSON **/
    public String getResourceId() { return itemIdRaw; }

    public String getUseTagsRaw() { return useTagsRaw; }

    @Nullable
    public Item getItem() { return item; }

    @Nullable
    public TagKey<Item> getTag() { return tag; }

    public int getWeight() { return weight; }

    public Set<ResourceLocation> getBiomeWhiteList() { return biomeWhiteList; }

    public Set<ResourceLocation> getBiomeBlackList() { return biomeBlackList; }

    /** Base production Rho for the given village level (1-indexed). Clamps to the last configured entry **/
    public int productionAt(int level) {
        return levelIndexed(production, level);
    }

    /** Base consumption for the given village level (1-indexed). Clamps to the last configured entry **/
    public int consumptionAt(int level) {
        return levelIndexed(consumption, level);
    }

    private static int levelIndexed(List<Integer> arr, int level) {
        if (arr == null || arr.isEmpty()) return 0;
        int i = Math.max(0, Math.min(level - 1, arr.size() - 1));
        return arr.get(i);
    }


    //** Setters / mutation used during deserialization **//

    public void setUseTagsRaw(String useTagsRaw) { this.useTagsRaw = useTagsRaw; }

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

    /** Resolves the raw item / tag strings against registries; called at beforeServerStarted **/
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

        JsonArray prod = new JsonArray();
        production.forEach(prod::add);
        obj.add("production", prod);

        JsonArray cons = new JsonArray();
        consumption.forEach(cons::add);
        obj.add("consumption", cons);

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
            List<Integer> prod = parseIntArray(obj, "production");
            if (prod != null) resource.production = prod;
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "004", "Error parsing production array for resource: " + itemId
                + ". Using default values. " + e.getMessage());
        }

        try {
            List<Integer> cons = parseIntArray(obj, "consumption");
            if (cons != null) resource.consumption = cons;
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "005", "Error parsing consumption array for resource: " + itemId
                + ". Using default values. " + e.getMessage());
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

    @Nullable
    private static List<Integer> parseIntArray(JsonObject obj, String property) {
        if (!obj.has(property) || !obj.get(property).isJsonArray()) return null;
        JsonArray arr = obj.getAsJsonArray(property);
        if (arr.isEmpty()) return null;
        List<Integer> values = new ArrayList<>();
        for (JsonElement e : arr) {
            int v = e.getAsInt();
            values.add(Math.max(0, v));
        }
        return values;
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
