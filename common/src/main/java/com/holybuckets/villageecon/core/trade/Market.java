package com.holybuckets.villageecon.core.trade;

import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.model.VillageEconomy;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Class: Market
 * Description: The open market for a single resource (Item). Villages post buy and
 * sell offers each tickTrade; flushMarket() matches buyers against sellers within
 * the buyer's chunk radius and haggles a price.
 *
 * Each buyer and seller may trade only once per tickTrade; posts are removed after
 * their haggle attempt, successful or not.
 */
public class Market {

    public static final String CLASS_ID = "020";
    private static final Random RANDOM = new Random();

    private final Item item;
    private final String resourceId;
    private final MarketRate marketRate;

    private final Queue<Post> buyPosts = new ArrayDeque<>();
    private final List<Post> sellPosts = new ArrayList<>();

    public Market(Item item) {
        this.item = item;
        this.resourceId = resolveResourceId(item);
        int window = ModConfig.getBalmConfig().tradeConfigs.marketRateTradeWindow;
        this.marketRate = new MarketRate(window);
    }

    /** Reverse lookup of the economy resource id for this item **/
    private static String resolveResourceId(Item item) {
        for (EconomyResource resource : ModConfig.getInstance().getAllResources()) {
            if (item.equals(resource.getItem())) return resource.getResourceId();
        }
        return item.toString();
    }


    //** GETTERS **//

    public Item getItem() { return item; }

    public String getResourceId() { return resourceId; }

    public MarketRate getMarketRate() { return marketRate; }

    public float rate() { return marketRate.rate(); }


    //** POSTING **//

    /** Adds a buy offer to the buy queue for this tickTrade **/
    public void buy(Post post) {
        if (post == null || post.getQuantityDemand() <= 0) return;
        buyPosts.add(post);
    }

    /** Adds a sell offer to the sell list for this tickTrade **/
    public void sell(Post post) {
        if (post == null || post.getQuantityDemand() <= 0) return;
        sellPosts.add(post);
    }


    //** MATCHING **//

    /**
     * Iterates over all buyers; for each, filters sellers to those within the buyer's
     * chunk radius (dependent on village level, VillageEconomy::getBuyRadius), draws a
     * random eligible seller and haggles. Unmatched posts are discarded at the end of
     * the tickTrade.
     */
    public void flushMarket(Bazaar bazaar)
    {
        while (!buyPosts.isEmpty())
        {
            Post buy = buyPosts.poll();
            Post sell = pickSeller(buy);
            if (sell == null) continue;

            sellPosts.remove(sell);     //each seller trades at most once per tickTrade
            haggle(bazaar, buy, sell);
        }
        sellPosts.clear();
    }

    /** Random seller within the buyer's radius; null if none eligible **/
    @Nullable
    private Post pickSeller(Post buy)
    {
        VillageEconomy buyer = buy.getVillage();
        int radius = buyer.getBuyRadius();

        List<Post> eligible = new ArrayList<>();
        for (Post sell : sellPosts) {
            if (sell.getVillage() == buyer) continue;
            if (chunkDist(buyer.getChunkPos(), sell.getVillage().getChunkPos()) <= radius)
                eligible.add(sell);
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(RANDOM.nextInt(eligible.size()));
    }

    private static int chunkDist(ChunkPos a, ChunkPos b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    /**
     * Haggles a price between one buyer and one seller according to each post's
     * demandReserve:
     *   weight_i = |d_i| / (|d_i| + |d_k|)
     *   price = reservation_sell_k + weight_i * (reservation_buy_i - reservation_sell_k)
     * Quantity is the min of both quantityDemands. A small fallthrough chance means
     * the trade simply doesn't occur.
     */
    private void haggle(Bazaar bazaar, Post buy, Post sell)
    {
        int reserveBuy = buy.getDemandReserve();
        int reserveSell = sell.getDemandReserve();

        //No zone of agreement - the natural fallthrough
        if (reserveBuy < reserveSell) return;

        //Fallthrough: small random chance the trade just doesn't occur
        float fallthrough = ModConfig.getBalmConfig().tradeConfigs.tradeFallthroughChance;
        if (RANDOM.nextFloat() < fallthrough) return;

        float di = Math.abs(buy.getDemand());
        float dk = Math.abs(sell.getDemand());
        float weight = (di + dk <= 0) ? 0.5f : di / (di + dk);

        int price = Math.round(reserveSell + weight * (reserveBuy - reserveSell));
        int quantity = Math.min(buy.getQuantityDemand(), sell.getQuantityDemand());
        if (quantity <= 0) return;

        long saleTime = (buy.getVillage().getLevel() != null)
            ? buy.getVillage().getLevel().getGameTime() : 0L;

        Sale sale = new Sale(sell.getVillage(), buy.getVillage(), price, quantity, saleTime, item, resourceId);

        //Update each village's ledger with the trade
        buy.getLedger().logTrade(sale, true);
        sell.getLedger().logTrade(sale, false);

        //Global tracker + market rate update
        bazaar.recordSale(sale);

        LoggerProject.logDebug(CLASS_ID + "001", "Haggled " + sale);
    }
}
