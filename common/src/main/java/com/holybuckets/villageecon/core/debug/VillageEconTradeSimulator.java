package com.holybuckets.villageecon.core.debug;

import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.TickType;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.VillageManager;
import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.ResourceLedger;
import com.holybuckets.villageecon.core.trade.Bazaar;
import com.holybuckets.villageecon.core.trade.Market;
import com.holybuckets.villageecon.core.trade.Sale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Logs dummy sales against a single village so the trade screen, the market rate
 * moving average and the live sales graph can be exercised without a second village.
 */
public class VillageEconTradeSimulator {

    public static final String CLASS_ID = "025";

    private static final Random RANDOM = new Random();
    private static final int DEFAULT_QUANTITY = 1;

    private static boolean autoSimulate = false;
    private static String autoResourceId = null;
    private static float volatility = 0.25f;

    public static void init(EventRegistrar reg) {
        reg.registerOnServerTick(TickType.ON_120_TICKS, VillageEconTradeSimulator::onTradeTick);
    }


    //** STATE **//

    public static boolean isAutoSimulate() { return autoSimulate; }

    public static void setAutoSimulate(boolean enabled) { autoSimulate = enabled; }

    public static String getAutoResourceId() { return autoResourceId; }

    public static void setAutoResourceId(String resourceId) { autoResourceId = resourceId; }

    public static float getVolatility() { return volatility; }

    public static void setVolatility(float value) { volatility = Math.max(0f, value); }


    //** LOOKUPS **//

    @Nullable
    public static Mayor firstMayor(ServerLevel level) {
        VillageManager manager = VillageManager.get(level);
        if (manager == null) return null;
        for (Mayor mayor : manager.getMayors().values())
            return mayor;
        return null;
    }

    @Nullable
    private static EconomyResource resolveResource(Mayor mayor, @Nullable String resourceId) {
        List<EconomyResource> active = mayor.getActiveResources();
        if (active.isEmpty()) return null;
        if (resourceId == null || resourceId.isBlank()) return active.get(0);

        for (EconomyResource resource : active) {
            if (resource.getResourceId().equalsIgnoreCase(resourceId)) return resource;
        }
        return null;
    }


    //** SIMULATION **//

    /**
     * Records one dummy sale at a price jittered around the resource's current market
     * rate. The sale feeds the Bazaar log and the moving average only; no ledger is moved.
     */
    @Nullable
    public static Sale logDummySale(ServerLevel level, @Nullable String resourceId, int quantity)
    {
        Mayor mayor = firstMayor(level);
        if (mayor == null) return null;

        Bazaar bazaar = Bazaar.get(level);
        if (bazaar == null) return null;

        EconomyResource resource = resolveResource(mayor, resourceId);
        if (resource == null || resource.getItem() == null) return null;

        Item item = resource.getItem();
        Market market = bazaar.getMarket(item);
        if (market == null) return null;

        float rate = market.rate();
        if (rate <= 0f) rate = 1f;

        int price = Math.max(1, Math.round(rate * (1f + (float) RANDOM.nextGaussian() * volatility)));
        int amount = Math.max(1, quantity);

        Sale sale = new Sale(mayor, mayor, price, amount, level.getGameTime(), item, resource.getResourceId());
        bazaar.recordSale(sale);

        LoggerProject.logDebug(CLASS_ID + "001", "Simulated " + sale + " new rate " + market.rate());
        return sale;
    }

    public static int logDummySales(ServerLevel level, @Nullable String resourceId, int count)
    {
        int logged = 0;
        for (int i = 0; i < Math.max(1, count); i++) {
            if (logDummySale(level, resourceId, DEFAULT_QUANTITY) != null) logged++;
        }
        return logged;
    }

    /** Fills the village's static ledger so the trade rows show meaningful stack counts **/
    public static int stockVillage(ServerLevel level, int stacks)
    {
        Mayor mayor = firstMayor(level);
        if (mayor == null) return 0;

        ResourceLedger ledger = mayor.getStaticLedger();
        int stocked = 0;
        for (EconomyResource resource : mayor.getActiveResources()) {
            ledger.set(resource.getResourceId(), Math.max(0, stacks));
            stocked++;
        }
        ledger.setCurrency(Math.max(ledger.getCurrency(), stacks));
        return stocked;
    }

    public static boolean runTickProcess(ServerLevel level) {
        Mayor mayor = firstMayor(level);
        if (mayor == null) return false;
        mayor.tickProcess();
        Bazaar bazaar = Bazaar.get(level);
        if (bazaar != null) bazaar.flushMarkets();
        return true;
    }

    public static boolean runDailyProcess(ServerLevel level) {
        Mayor mayor = firstMayor(level);
        if (mayor == null) return false;
        mayor.dailyProcess();
        return true;
    }

    public static boolean runCycleProcess(ServerLevel level) {
        Mayor mayor = firstMayor(level);
        if (mayor == null) return false;
        mayor.cycleProcess();
        return true;
    }


    //** REPORTING **//

    public static String status(ServerLevel level)
    {
        VillageManager manager = VillageManager.get(level);
        if (manager == null) return "No VillageManager for this level";

        Mayor mayor = firstMayor(level);
        if (mayor == null) return "No mayors loaded. Villages: " + manager.getVillageCount();

        Bazaar bazaar = Bazaar.get(level);
        ResourceLedger ledger = mayor.getStaticLedger();
        Map<String, Float> demand = mayor.getDemand();

        StringBuilder sb = new StringBuilder();
        sb.append("Village ").append(mayor.getVillageChunkId())
          .append(" level ").append(mayor.getVillageLevel())
          .append(" alive=").append(mayor.isAlive())
          .append(" currency=").append(String.format("%.1f", ledger.getCurrency()))
          .append("\nCycle day ").append(manager.getDayOfCycle())
          .append(" of cycle ").append(manager.getCycleIndex())
          .append("\nAuto simulate: ").append(autoSimulate)
          .append(" volatility ").append(volatility);

        for (EconomyResource resource : mayor.getActiveResources())
        {
            String id = resource.getResourceId();
            float rate = (bazaar != null && resource.getItem() != null)
                ? bazaar.getMarketRate(resource.getItem()) : 0f;
            int sales = (bazaar != null && resource.getItem() != null && bazaar.getMarket(resource.getItem()) != null)
                ? bazaar.getMarket(resource.getItem()).getRecentSalePrices().size() : 0;

            sb.append("\n  ").append(id)
              .append(" stock=").append(ledger.get(id))
              .append(" quota=").append(2 * resource.productionAt(mayor.getVillageLevel() + 1))
              .append(" rate=").append(String.format("%.2f", rate))
              .append(" demand=").append(String.format("%.2f", demand.getOrDefault(id, 0f)))
              .append(" samples=").append(sales);
        }

        sb.append("\nCurrency item: ").append(ModConfig.getInstance().getCurrencyItem());
        return sb.toString();
    }

    public static String listVillages(ServerLevel level)
    {
        VillageManager manager = VillageManager.get(level);
        if (manager == null) return "No VillageManager for this level";

        StringBuilder sb = new StringBuilder("Villages: " + manager.getVillageCount()
            + " | mayors in memory: " + manager.getMayors().size());
        for (Mayor mayor : manager.getMayors().values()) {
            sb.append("\n  ").append(mayor.getVillageChunkId())
              .append(" level ").append(mayor.getVillageLevel())
              .append(" alive=").append(mayor.isAlive())
              .append(" entityLoaded=").append(mayor.isEntityLoaded());
        }
        return sb.toString();
    }


    //** EVENTS **//

    private static void onTradeTick(ServerTickEvent event) {
        if (!autoSimulate) return;

        VillageManager manager = VillageManager.getInstance();
        if (manager == null) return;
        logDummySale(manager.getLevel(), autoResourceId, DEFAULT_QUANTITY);
    }
}
