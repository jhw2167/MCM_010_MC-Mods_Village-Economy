package com.holybuckets.villageecon.core.trade;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import javax.annotation.Nullable;
import java.util.*;

/**
 * The Bazaar holds all the markets
 * Each market encapsulates a single tradable Resource by the village
 * The bazaars job is to hold all markets and call the per tick trade alg on them
 */
public class Bazaar {

    public static final String CLASS_ID = "019";

    //** STATICS
    static final Map<LevelAccessor, Bazaar> BAZAARS = new HashMap<>();

    //** VARIABLES
    private final ServerLevel level;
    private final Map<Item, Market> markets;
    private final List<Sale> saleLog;       //global tracker of all sales, transient for now


    //** CONSTRUCTORS
    public Bazaar(ServerLevel level) {
        this.level = level;
        this.markets = new HashMap<>();
        this.saleLog = new ArrayList<>();
        BAZAARS.put(level, this);

        for (EconomyResource resource : ModConfig.getInstance().getAllResources()) {
            if (resource.getItem() == null) continue;
            addMarket(resource.getItem());
            markets.get(resource.getItem()).getMarketRate().seed(initialRate(resource));
        }
        LoggerProject.logInit(CLASS_ID + "000", Bazaar.class.getName() + " with " + markets.size() + " market(s)");
    }


    //** GETTERS **//

    @Nullable
    public static Bazaar get(LevelAccessor level) {
        return BAZAARS.get(level);
    }

    public ServerLevel getLevel() { return level; }

    @Nullable
    public Market getMarket(Item item) { return markets.get(item); }

    public float getMarketRate(Item item) {
        Market market = markets.get(item);
        return (market != null) ? market.rate() : 0f;
    }

    public List<Sale> getSaleLog() {
        return Collections.unmodifiableList(saleLog);
    }


    //** CORE **//

    public void addMarket(Item item) {
        if (item == null || markets.containsKey(item)) return;
        markets.put(item, new Market(item));
    }

    public void postBuyOffer(Item item, Post posting) {
        Market market = markets.get(item);
        if (market == null) return;
        market.buy(posting);
    }

    public void postSellOffer(Item item, Post posting) {
        Market market = markets.get(item);
        if (market == null) return;
        market.sell(posting);
    }

    //Flushes all market postings for this tick
    public void flushMarkets() {
        for (Market market : markets.values()) {
            market.flushMarket(this);
        }

    }

    public void recordSale(Sale sale) {
        if (sale == null) return;
        saleLog.add(sale);
        Market market = markets.get(sale.getResource());
        if (market != null) market.recordSale(sale);
    }


    private static float initialRate(EconomyResource resource) {
        float r = ModConfig.getDefaults().growthFactor;
        float q = 0.5f;
        return r / q;
    }


    //** STATIC OFFERS **//

    public static void buyOffer(Level level, Item item, Post posting) {
        Bazaar bazaar = BAZAARS.get(level);
        if (bazaar != null) bazaar.postBuyOffer(item, posting);
    }

    public static void sellOffer(Level level, Item item, Post posting) {
        Bazaar bazaar = BAZAARS.get(level);
        if (bazaar != null) bazaar.postSellOffer(item, posting);
    }

}
