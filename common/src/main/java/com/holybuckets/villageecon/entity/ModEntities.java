package com.holybuckets.villageecon.entity;

import com.holybuckets.villageecon.Constants;
import net.blay09.mods.balm.api.DeferredObject;
import net.blay09.mods.balm.api.entity.BalmEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.Villager;

public class ModEntities {

    public static DeferredObject<EntityType<MayorEntity>> mayor;

    public static void initialize(BalmEntities entities)
    {
        mayor = entities.registerEntity(id("mayor"),
            EntityType.Builder.<MayorEntity>of(MayorEntity::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10),
            MayorEntity::createAttributes);
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(Constants.MOD_ID, name);
    }
}
