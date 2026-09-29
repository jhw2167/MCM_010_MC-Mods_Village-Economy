package com.holybuckets.villageecon.core.trade;

import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.VillageManager;
import com.holybuckets.villageecon.core.model.Mayor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import static com.holybuckets.foundation.HBUtil.ChunkUtil.chunkDist;

/**
 * Abstraction where villages sell and buy items
 * a market encapsulates a single tradable resource
 * when a market is flushed, all postings are removed whether they find a partner or not.
 *
 * buy and sell postings are posted via the bazaar, flushing occurs every 5-6 seconds.
 */
public class Market {

    public static final String CLASS_ID = "020";
    private static final Random RANDOM = new Random();

    private final Item item;
    private final String resourceId;
    private final MarketRate marketRate;

    public static final int SALE_HISTORY_SIZE = 20;

    /** The contractor always moves exactly one unit **/
    private static final int DUMMY_QUANTITY = 1;

    private final Queue<Post> buyPosts = new ArrayDeque<>();
    private final List<Post> sellPosts = new ArrayList<>();
    private final ArrayDeque<Float> recentSalePrices = new ArrayDeque<>();
    private final Set<Mayor> dummyTradesToday = new HashSet<>();

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

    public List<Float> getRecentSalePrices() { return new ArrayList<>(recentSalePrices); }

    public List<Integer> getRecentSalePricesRounded() {
        List<Integer> rounded = new ArrayList<>(recentSalePrices.size());
        for (Float price : recentSalePrices)
            rounded.add(Math.round(price));
        return rounded;
    }

    /**
     * Primes this market at a known rate, filling both the moving average and the visible
     * sale history so a restored rate holds steady until real trades displace it.
     */
    public void seed(float rate, int samples) {
        if (rate <= 0f) return;

        marketRate.seed(rate);
        recentSalePrices.clear();
        for (int i = 0; i < samples; i++) {
            marketRate.addSample(rate, 1);
            recentSalePrices.addLast(rate);
        }
        while (recentSalePrices.size() > SALE_HISTORY_SIZE)
            recentSalePrices.removeFirst();
    }

    public void clearDummyTrades() {
        dummyTradesToday.clear();
    }

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
        String item = this.resourceId;
        if(sellPosts.isEmpty() && buyPosts.isEmpty()) {
            return; //no Market
        } else if(sellPosts.isEmpty()) {
            int rand = RANDOM.nextInt(buyPosts.size());
            sellToDummy( buyPosts.stream().skip(rand).findFirst().orElse(null), bazaar);
            return;
        } else if(buyPosts.isEmpty()) {
            int rand = RANDOM.nextInt(sellPosts.size());
            buyFromDummy(sellPosts.get(rand), bazaar);
            return;
        }
        List<Post> sortedBuyPosts = new ArrayList<>(buyPosts);
        sortedBuyPosts.sort((a, b) -> Float.compare(b.getDemandReserve(), a.getDemandReserve()));
        int sales = 0;

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
                TransactionLog.recordSale(buy.getVillage().getLevel(), sale);
                sales++;
            }

        }

        logMarketSales(buyPosts.size(), sellPosts.size(), sales, this);

        buyPosts.clear();
        sellPosts.clear();
    }

    /**
     * Prices good as a weighted midpoint between the market rate
     * and the villager's reserve price to move the market cheaper or more expensive and encourage more trades
     */
    private float dummyPrice(float villageReserve)
    {
        int weight = marketRate.getWindow();
        return (marketRate.rate() * weight + villageReserve) / (weight + 1);
    }

    private static long saleTime(Post post)
    {
        Mayor village = post.getVillage();
        if (village == null || village.getLevel() == null) return 0L;
        return village.getLevel().getGameTime();
    }

    //Creates dummy seller for a buyer to move market rate to equilibrium
    private void sellToDummy(Post buy, Bazaar bazaar)
    {
        if (buy == null || buy.getVillage() == null) return;
        if (!dummyTradesToday.add(buy.getVillage())) return;

        float price = dummyPrice(buy.getDemandReserve());
        Sale sale = new Sale(VillageManager.INDEPENDENT_MAYOR, buy.getVillage(),
            price, DUMMY_QUANTITY, saleTime(buy), item, resourceId);

        buy.getLedger().logTrade(sale, true);
        bazaar.recordSale(sale);
        TransactionLog.recordSale(buy.getVillage().getLevel(), sale);

        buyPosts.remove(buy);
        LoggerProject.logDebug("013004", "Market: " + resourceId
            + ": contractor sold 1 to " + buy.getVillage().getName() + " at " + price);
    }

    //Creates dummy buyer for a seller to move market rate
    private void buyFromDummy(Post sell, Bazaar bazaar)
    {
        if (sell == null || sell.getVillage() == null) return;
        if (!dummyTradesToday.add(sell.getVillage())) return;

        float price = dummyPrice(sell.getDemandReserve());
        Sale sale = new Sale(sell.getVillage(), VillageManager.INDEPENDENT_MAYOR,
            price, DUMMY_QUANTITY, saleTime(sell), item, resourceId);

        sell.getLedger().logTrade(sale, false);
        bazaar.recordSale(sale);
        TransactionLog.recordSale(sell.getVillage().getLevel(), sale);

        sellPosts.remove(sell);
        LoggerProject.logDebug("013005", "Market: " + resourceId
            + ": contractor bought 1 from " + sell.getVillage().getName() + " at " + price);
    }

    private static void logMarketSales(int buyPostsSize, int sellPostsSize, int sales, Market market)
        {
            int sum = market.recentSalePrices.stream().mapToInt(Float::intValue).sum();
            int avg = (market.recentSalePrices.size() > 0) ? sum / market.recentSalePrices.size() : 0;
            LoggerProject.logInfo("013003", "Market: " + market.resourceId
                + ": " + sales + " sales out of " + buyPostsSize + " buy posts and " + sellPostsSize + " sell posts. At average price: " + avg );
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
     * Determines sale price from the buyer's and the seller's **RESERVE PRICE**.
     * Each reserve price is the market rate adjusted by that village's rolled profit
     * expectation: the most a buyer will pay, the least a seller will accept.
     *
     * Sale is returned if present
     */
    private Sale haggle(Post buy, Post sell)
    {
        float reserveBuy = buy.getDemandReserve();
        float reserveSell = sell.getDemandReserve();

        //No zone of agreement - the natural fallthrough
        if (reserveBuy < reserveSell) return null;

        //Fallthrough: small random chance the trade just doesn't occur
        float fallthrough = ModConfig.getBalmConfig().tradeConfigs.tradeFallthroughChance;
        if (RANDOM.nextFloat() < fallthrough) return null;

        float di = Math.abs(buy.getDemand());
        float dk = Math.abs(sell.getDemand());
        float weight = (di + dk <= 0) ? 0.5f : di / (di + dk);

        float price = reserveSell + weight * (reserveBuy - reserveSell);
        int quantity = Math.min(buy.getQuantityDemand(), sell.getQuantityDemand());
        if (quantity <= 0) return null;

        long saleTime = (buy.getVillage().getLevel() != null)
            ? buy.getVillage().getLevel().getGameTime() : 0L;

        return new Sale(sell.getVillage(), buy.getVillage(), price, quantity, saleTime, item, resourceId);
    }
}
