package com.holybuckets.villageecon.item;


import com.holybuckets.villageecon.Constants;
import net.blay09.mods.balm.api.DeferredObject;
import net.blay09.mods.balm.api.item.BalmItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class ModItems {

    public static DeferredObject<Item> mayorSpawnEgg;

    public static void initialize(BalmItems items) {
        mayorSpawnEgg = items.registerItem(rl -> new MayorSpawnEggItem(), id("mayor_spawn_egg"),
            com.holybuckets.foundation.item.ModItems.FOUNDATIONS_TAB);
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(Constants.MOD_ID, name);
    }

}
