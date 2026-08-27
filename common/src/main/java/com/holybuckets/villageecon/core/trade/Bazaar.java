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
 * Class: Bazaar
 * Description: Singleton per level (for now just the Overworld). Holds the map of
 * <Item, Market> markets, the global log of all sales, and updates market rates
 * as sales are recorded.
 *
 * On creation, a Market is added for every configured economy resource and its
 * MarketRate is seeded with the initial rate r/q added N times, so the seed is
 * slowly phased out by real trades.
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
    private Bazaar(ServerLevel level) {
        this.level = level;
        this.markets = new HashMap<>();
        this.saleLog = new ArrayList<>();
        BAZAARS.put(level, this);

        //Create a market for every configured resource and seed its rate
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

    /** Global log of all sales; resides in the bazaar for now **/
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

    /** Matches and haggles all posted offers; called once per tickTrade after all villages post **/
    public void flushMarkets() {
        for (Market market : markets.values())
            market.flushMarket(this);
    }

    /**
     * Saves the sale to the global tracker and updates the market rate with this data.
     * Ledger updates already occurred in Market::haggle.
     */
    public void recordSale(Sale sale) {
        if (sale == null) return;
        saleLog.add(sale);
        Market market = markets.get(sale.getResource());
        if (market != null) market.getMarketRate().addSample(sale);
    }

    /**
     * Initial market rate: base demand Do = r/q.
     * TODO: use the real per-unit quota reward r once global currency / MarketState is live;
     * for now r is approximated by the configured growthFactor at an assumed half quota.
     */
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


    //** EVENTS **//

    public static void init(EventRegistrar reg) {
        reg.registerOnLevelLoad(Bazaar::onLevelLoad);
        reg.registerOnServerStopped(Bazaar::onServerStopped);
    }

    /** Create the bazaar for the Overworld once it loads (markets need hydrated resources) **/
    private static void onLevelLoad(LevelLoadingEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel() != GeneralConfig.OVERWORLD) return;
        if (BAZAARS.containsKey(event.getLevel())) return;
        new Bazaar((ServerLevel) event.getLevel());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        BAZAARS.clear();
    }
}
