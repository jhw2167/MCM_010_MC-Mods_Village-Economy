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


    public static final FriendlyByteBuf encodeMayorOffersSync(MayorOffersSync object, FriendlyByteBuf buf) {
        com.holybuckets.villageecon.menu.MayorTradeMenu.writeOffers(buf, object.getOffers());
        buf.writeFloat(object.getReserveCurrency());
        buf.writeUtf(object.getVillageName());
        buf.writeFloat(object.getCurrencyDelta());
        return buf;
    }

    public static final MayorOffersSync decodeMayorOffersSync(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<com.holybuckets.villageecon.menu.MayorTradeOffer> offers = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++)
            offers.add(com.holybuckets.villageecon.menu.MayorTradeOffer.read(buf));
        float reserveCurrency = buf.readFloat();
        String villageName = buf.readUtf();
        float currencyDelta = buf.readFloat();
        return new MayorOffersSync(offers, reserveCurrency, villageName, currencyDelta);
    }

}
