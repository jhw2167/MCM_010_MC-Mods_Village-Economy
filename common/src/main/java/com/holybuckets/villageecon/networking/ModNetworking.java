package com.holybuckets.villageecon.networking;

import com.holybuckets.villageecon.Constants;
import net.blay09.mods.balm.api.network.BalmNetworking;
import net.minecraft.resources.ResourceLocation;

public class ModNetworking {

    public static void initialize(BalmNetworking networking) {
        networking.registerClientboundPacket(
            id(LedgerSalesSync.LOCATION),
            LedgerSalesSync.class,
            Codecs::encodeLedgerSalesSync,
            Codecs::decodeLedgerSalesSync,
            Handlers::handleLedgerSalesSync);
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(Constants.MOD_ID, name);
    }
}
