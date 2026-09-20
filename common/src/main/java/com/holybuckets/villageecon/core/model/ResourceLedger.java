package com.holybuckets.villageecon.core.model;

import com.holybuckets.villageecon.core.trade.Sale;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public boolean remove(String resourceId, int count) {
        set(resourceId, get(resourceId) - count);
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


    //** Trading **//

    public void logTrade(Sale sale, boolean isBuyer) {
        if (sale == null) return;
        float totalPrice = sale.getSalePrice() * sale.getSaleQuantity();
        if (isBuyer) {
            add(sale.getResourceId(), sale.getSaleQuantity());
            addCurrency(-totalPrice);
        } else {
            remove(sale.getResourceId(), sale.getSaleQuantity());
            addCurrency(totalPrice);
        }
    }


    //** Reconciliation **//

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
