package com.holybuckets.villageecon.config.model;

import java.util.HashMap;
import java.util.Map;

public class BiasModifier {

    public static final String CLASS_ID = "031";

    public static final float DEF_BIAS = 1f;
    public static final int DEF_TARGET = 0;

    private final Map<EconomyResource, Float> bias = new HashMap<>();
    private final Map<EconomyResource, Integer> target = new HashMap<>();

    public float getBias(EconomyResource resource) {
        return bias.getOrDefault(resource, DEF_BIAS);
    }

    public int getTarget(EconomyResource resource) {
        return target.getOrDefault(resource, DEF_TARGET);
    }

    public Map<EconomyResource, Float> getBiases() { return bias; }

    public Map<EconomyResource, Integer> getTargets() { return target; }

    public void addBias(EconomyResource resource, Float value) {
        if (resource == null || value == null) return;
        bias.put(resource, value);
    }

    public void addTarget(EconomyResource resource, Integer value) {
        if (resource == null || value == null) return;
        target.put(resource, value);
    }

    public void clear() {
        bias.clear();
        target.clear();
    }

    public boolean isEmpty() {
        return bias.isEmpty() && target.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder bias = new StringBuilder();
        StringBuilder demand = new StringBuilder();
        for(EconomyResource resource : this.bias.keySet()) {
            bias.append(resource.getResourceId() + ": " + this.bias.get(resource) + ", ");
            demand.append(resource.getResourceId() + ": " + this.target.get(resource) + ", ");
        }

        return "bias " + bias + "\n targets " + demand;
    }
}
