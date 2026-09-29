package com.holybuckets.villageecon.item;


import com.holybuckets.villageecon.Constants;
import net.blay09.mods.balm.api.DeferredObject;
import net.blay09.mods.balm.api.item.BalmItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class ModItems {

    /** Marker sprite plotted on the trade screen price graph **/
    public static DeferredObject<Item> dot;

    public static void initialize(BalmItems items) {
        dot = items.registerItem(rl -> new Item(new Item.Properties()), id("dot"), null);
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(Constants.MOD_ID, name);
    }

}
