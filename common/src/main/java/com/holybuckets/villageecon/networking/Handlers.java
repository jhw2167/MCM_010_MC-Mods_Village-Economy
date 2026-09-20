package com.holybuckets.villageecon.networking;

import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ThreadPoolExecutor;

public class Handlers {

    public static String CLASS_ID = "014";

    private static int RECEIVED = 0;
    private static ThreadPoolExecutor POOL = new ThreadPoolExecutor(2, 2, 60L, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<Runnable>());

    public static void init() {
        //Initializing class
    }

    public static void handleLedgerSalesSync(Player p, LedgerSalesSync m) {
        MarketSalesCache.accept(m);
    }

    public static void handleMayorOffersSync(Player p, MayorOffersSync m) {
        if (p == null || m == null) return;
        if (p.containerMenu instanceof com.holybuckets.villageecon.menu.MayorTradeMenu menu)
            menu.setOffers(m.getOffers(), m.getReserveCurrency(), m.getVillageName(), m.getCurrencyDelta());
    }


}
