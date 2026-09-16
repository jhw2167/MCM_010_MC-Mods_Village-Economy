package com.holybuckets.villageecon.networking;

import com.holybuckets.foundation.HBUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

public class Codecs {


    public static final FriendlyByteBuf encodeLedgerSalesSync(LedgerSalesSync object, FriendlyByteBuf buf) {
        buf.writeUtf(object.getResourceId());
        buf.writeFloat(object.getMarketRate());
        List<Integer> prices = object.getSalePrices();
        buf.writeVarInt(prices.size());
        for (Integer price : prices)
            buf.writeVarInt(price);
        return buf;
    }

    public static final LedgerSalesSync decodeLedgerSalesSync(FriendlyByteBuf buf) {
        String resourceId = buf.readUtf();
        float marketRate = buf.readFloat();
        int size = buf.readVarInt();
        List<Integer> prices = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++)
            prices.add(buf.readVarInt());
        return new LedgerSalesSync(resourceId, marketRate, prices);
    }


}
