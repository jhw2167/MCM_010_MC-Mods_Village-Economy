package com.holybuckets.villageecon.core.model;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class: ResourceLedger
 * Description: Per-village bookkeeping of resource counts and reserve currency.
 * The Mayor holds two instances:
 *  - staticLedger: true current value of resources owned by the village. Only mutated
 *    by dailyProcess (production, interest) and executed trades. Rectified each cycle.
 *  - theoLedger: theoretical values of all village resources after speculative trades,
 *    updated every trade sequence (tickProcess).
 *
 * Resource counts are keyed by resource id (the raw item name from the economy JSON config)
 * and denominated in individual items.
 */
public class ResourceLedger {

    public static final String CLASS_ID = "014";

    private final Map<String, Integer> resources = new LinkedHashMap<>();
    private float currency;


    //** Constructors **//

    public ResourceLedger() { }

    /** Deep copy **/
    public static ResourceLedger copyOf(ResourceLedger other) {
        ResourceLedger copy = new ResourceLedger();
        copy.resources.putAll(other.resources);
        copy.currency = other.currency;
        return copy;
    }


    //** Resources **//

    public int get(String resourceId) {
        return resources.getOrDefault(resourceId, 0);
    }

    public void set(String resourceId, int count) {
        resources.put(resourceId, Math.max(0, count));
    }

    public void add(String resourceId, int count) {
        set(resourceId, get(resourceId) + count);
    }

    /** Removes count of the resource; returns false (and removes nothing) if there is not enough **/
    public boolean remove(String resourceId, int count) {
        int current = get(resourceId);
        if (count > current) return false;
        set(resourceId, current - count);
        return true;
    }

    public Map<String, Integer> getResources() {
        return Collections.unmodifiableMap(resources);
    }


    //** Currency **//

    public float getCurrency() {
        return currency;
    }

    public void setCurrency(float currency) {
        this.currency = currency;
    }

    public void addCurrency(float amount) {
        this.currency += amount;
    }


    //** Reconciliation **//

    /**
     * Computes (this - other) per resource id across both ledgers.
     * When called as theoLedger.diff(staticLedger):
     *  - positive entries are surpluses: resources to be traded from other villages to this one
     *  - negative entries are deficits: resources this village must schedule as outgoing trades
     */
    public Map<String, Integer> diff(ResourceLedger other) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String id : resources.keySet())
            result.put(id, get(id) - other.get(id));
        for (String id : other.resources.keySet())
            result.putIfAbsent(id, get(id) - other.get(id));
        return result;
    }

    public void clear() {
        resources.clear();
        currency = 0;
    }


    //** Serialization **//

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag resourcesTag = new CompoundTag();
        resources.forEach(resourcesTag::putInt);
        tag.put("resources", resourcesTag);
        tag.putFloat("currency", currency);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;
        resources.clear();
        CompoundTag resourcesTag = tag.getCompound("resources");
        for (String key : resourcesTag.getAllKeys())
            resources.put(key, resourcesTag.getInt(key));
        this.currency = tag.getFloat("currency");
    }
}
