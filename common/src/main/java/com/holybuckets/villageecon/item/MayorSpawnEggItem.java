package com.holybuckets.villageecon.item;

import com.holybuckets.villageecon.entity.MayorEntity;
import com.holybuckets.villageecon.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.Objects;

/**
 * Places a Mayor, designating the chunk it lands in as a village chunk.
 * The entity type is resolved on use rather than at construction so item
 * registration does not depend on entity registration order.
 */
public class MayorSpawnEggItem extends Item {

    public static final String CLASS_ID = "027";

    public MayorSpawnEggItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

        EntityType<MayorEntity> type = ModEntities.mayor.get();
        if (type == null) return InteractionResult.FAIL;

        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clicked);

        BlockPos spawnPos = state.getCollisionShape(level, clicked).isEmpty()
            ? clicked : clicked.relative(face);

        MayorEntity mayor = type.spawn(serverLevel, stack, context.getPlayer(), spawnPos,
            MobSpawnType.SPAWN_EGG, true,
            !Objects.equals(clicked, spawnPos) && face == Direction.UP);

        if (mayor == null) return InteractionResult.FAIL;

        stack.shrink(1);
        level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, clicked);
        return InteractionResult.CONSUME;
    }
}
