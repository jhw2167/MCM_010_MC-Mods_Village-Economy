package com.holybuckets.villageecon.client;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.villageecon.Constants;
import com.holybuckets.villageecon.client.render.MayorRenderer;
import com.holybuckets.villageecon.entity.ModEntities;
import net.blay09.mods.balm.api.client.rendering.BalmRenderers;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class ModRenderers {

    //public static ModelLayerLocation someModel;

    public static void clientInitialize(BalmRenderers renderers) {
        renderers.registerEntityRenderer(id("mayor"), ModEntities.mayor::get, (c) -> new MayorRenderer(c));
        //waystoneModel = renderers.registerModel(new ResourceLocation(Waystones.MOD_ID, "waystone"), () -> WaystoneModel.createLayer(CubeDeformation.NONE));
        //renderers.setBlockRenderType(() -> ModBlocks.stoneBrickBlockEntity, RenderType.cutout());
    }

    private static ResourceLocation id(String name) {
        return HBUtil.LOC(Constants.MOD_ID, name);
    }

}
