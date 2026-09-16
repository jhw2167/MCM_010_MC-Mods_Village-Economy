package com.holybuckets.villageecon.entity;

import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.core.VillageManager;
import com.holybuckets.villageecon.core.model.Mayor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Mayor villager entity in world, owned by Mayor object
 */
public class MayorEntity extends Villager {

    public static final String CLASS_ID = "024";
    private static final String NBT_MAYOR = "mayor";
    private static final String NBT_VILLAGE_CHUNK_ID = "villageChunkId";

    private String villageChunkId;
    private CompoundTag pendingMayorData;

    public MayorEntity(EntityType<? extends Villager> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }


    //** GETTERS / SETTERS **//

    public String getVillageChunkId() {
        return villageChunkId;
    }

    public void setVillageChunkId(String villageChunkId) {
        this.villageChunkId = villageChunkId;
    }

    public CompoundTag getPendingMayorData() {
        return pendingMayorData;
    }

    public void setPendingMayorData(CompoundTag pendingMayorData) {
        this.pendingMayorData = pendingMayorData;
    }


    //** LIFECYCLE **//

    private static int TOTAL_TICKS = 20;
    private int count = 0;
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        if (++count < TOTAL_TICKS) return;
            count = 0;
        VillageManager.mayorEntityAdded(this.level(), villageChunkId, this);
    }

    @Override
    public void remove(RemovalReason reason) {
        VillageManager.mayorEntityRemoved(this.level(), villageChunkId, reason, this);
        super.remove(reason);
    }

    @Override
    public void die(DamageSource damageSource)
    {
        super.die(damageSource);
        if (this.level().isClientSide()) return;
        if (villageChunkId == null) return;
        VillageManager.mayorEntityRemoved(this.level(), villageChunkId, Entity.RemovalReason.KILLED, this);
    }


    //** INTERACTION **//
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide())
            return InteractionResult.SUCCESS;

        if (!this.isAlive() || this.isSleeping() || this.isTrading())
            return InteractionResult.PASS;

        Mayor m = null;
        if (villageChunkId != null) m = Mayor.getMayor(this.level(), villageChunkId);

        if (m == null) {
            LoggerProject.logError("024001",
                "Mayor entity interacted with, but no mayor data found for village chunk: " + villageChunkId);
            return InteractionResult.PASS;
        }
        net.blay09.mods.balm.api.Balm.getNetworking().openMenu(player, new com.holybuckets.villageecon.menu.MayorMenuProvider(m));

        return InteractionResult.CONSUME;

    }


    //** BEHAVIOR **//

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }


    //** SERIALIZATION **//

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (villageChunkId != null) tag.putString(NBT_VILLAGE_CHUNK_ID, villageChunkId);
        else if (pendingMayorData != null) tag.put(NBT_MAYOR, pendingMayorData);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_VILLAGE_CHUNK_ID)) this.villageChunkId = tag.getString(NBT_VILLAGE_CHUNK_ID);
        if (tag.contains(NBT_MAYOR)) this.pendingMayorData = tag.getCompound(NBT_MAYOR);
    }
}
