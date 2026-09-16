package com.holybuckets.villageecon.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.holybuckets.villageecon.Constants;
import com.holybuckets.villageecon.entity.MayorEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;

public class MayorRenderer extends MobRenderer<MayorEntity, VillagerModel<MayorEntity>> {

    private static final ResourceLocation MAYOR_TEXTURE =
        new ResourceLocation(Constants.MOD_ID, "textures/entity/mayor.png");

    public MayorRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager"));
        this.addLayer(new CrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(MayorEntity mayor) {
        return MAYOR_TEXTURE;
    }

    @Override
    protected void scale(MayorEntity mayor, PoseStack poseStack, float partialTick) {
        float f = 0.9375F;
        if (mayor.isBaby()) {
            f *= 0.5F;
            this.shadowRadius = 0.25F;
        } else {
            this.shadowRadius = 0.5F;
        }
        poseStack.scale(f, f, f);
    }
}