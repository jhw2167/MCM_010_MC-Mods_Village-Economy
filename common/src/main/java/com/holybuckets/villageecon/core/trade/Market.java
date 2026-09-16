package com.holybuckets.villageecon.core.trade;

import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.model.Mayor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import static com.holybuckets.foundation.HBUtil.ChunkUtil.chunkDist;

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

    public static final int SALE_HISTORY_SIZE = 20;

    private final Queue<Post> buyPosts = new ArrayDeque<>();
    private final List<Post> sellPosts = new ArrayList<>();
    private final ArrayDeque<Integer> recentSalePrices = new ArrayDeque<>();

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

    public List<Integer> getRecentSalePrices() { return new ArrayList<>(recentSalePrices); }

    public void recordSale(Sale sale) {
        if (sale == null) return;
        marketRate.addSample(sale);
        recentSalePrices.addLast(sale.getSalePrice());
        while (recentSalePrices.size() > SALE_HISTORY_SIZE)
            recentSalePrices.removeFirst();
    }


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
     * - Sorts buy postings by reserve demand, more desperate goes first
     * - Attempts to trade with nearest seller within the buyer's radius
     * - All buy and sell posts are cleared each cycle
     */
    public void flushMarket(Bazaar bazaar)
    {
        //sort
        List<Post> sortedBuyPosts = new ArrayList<>(buyPosts);
        sortedBuyPosts.sort((a, b) -> Integer.compare(b.getDemandReserve(), a.getDemandReserve()));

        for(Post buy : sortedBuyPosts)
        {
            Post sell = pickSeller(buy);
            if (sell == null) continue;

            sellPosts.remove(sell);     //each seller trades at most once per tickTrade
            Sale sale = haggle(buy, sell);
            if (sale != null) {
                buy.getLedger().logTrade(sale, true);
                sell.getLedger().logTrade(sale, false);
                bazaar.recordSale(sale);
            }

        }

        buyPosts.clear();
        sellPosts.clear();
    }

    //Find the closest seller by distance to the buyer's village
    @Nullable
    private Post pickSeller(Post buy)
    {
        Mayor buyer = buy.getVillage();
        int radius = buyer.getBuyRadius();

        Post eligible = null;
        for (Post sell : sellPosts) {
            if (sell.getVillage() == buyer) continue;
            if (chunkDist(buyer, sell) <= radius)
            {
                if (eligible == null) eligible = sell;
                else if (chunkDist(buyer, sell) < chunkDist(buyer, eligible))
                        eligible = sell;
            }
        }
        return eligible;
    }

    private int chunkDist(Mayor buyer, Post sell) {
        if (buyer == null || sell == null) return Integer.MAX_VALUE;
        return chunkDist(buyer.getChunkPos(), sell.getVillage().getChunkPos());
    }

    private static int chunkDist(Post a, Post b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        return chunkDist(a.getVillage().getChunkPos(), b.getVillage().getChunkPos());
    }

    private static int chunkDist(ChunkPos a, ChunkPos b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    /**
     * Determines sale price based on the buyers and sellers **RESERVER PRICE**
     * Reserver price is a Random number with lambda = marketRate, multipied by agreeableness
     *
     * Sale is returned if present
     */
    private Sale haggle(Post buy, Post sell)
    {
        int reserveBuy = buy.getDemandReserve();
        int reserveSell = sell.getDemandReserve();

        //No zone of agreement - the natural fallthrough
        if (reserveBuy < reserveSell) return null;

        //Fallthrough: small random chance the trade just doesn't occur
        float fallthrough = ModConfig.getBalmConfig().tradeConfigs.tradeFallthroughChance;
        if (RANDOM.nextFloat() < fallthrough) return null;

        float di = Math.abs(buy.getDemand());
        float dk = Math.abs(sell.getDemand());
        float weight = (di + dk <= 0) ? 0.5f : di / (di + dk);

        int price = Math.round(reserveSell + weight * (reserveBuy - reserveSell));
        int quantity = Math.min(buy.getQuantityDemand(), sell.getQuantityDemand());
        if (quantity <= 0) return null;

        long saleTime = (buy.getVillage().getLevel() != null)
            ? buy.getVillage().getLevel().getGameTime() : 0L;

        return new Sale(sell.getVillage(), buy.getVillage(), price, quantity, saleTime, item, resourceId);
    }
}
