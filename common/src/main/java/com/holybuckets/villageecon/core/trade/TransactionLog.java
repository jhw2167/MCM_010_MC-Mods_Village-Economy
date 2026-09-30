package com.holybuckets.villageecon.core.trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.datastore.WorldSaveData;
import com.holybuckets.villageecon.Constants;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.core.VillageManager;
import com.holybuckets.villageecon.core.model.Mayor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores all transactions for sending to client and replaying after server shutsdown
 * Daily transactions are serialized to the respective mayor entity.
 */
public class TransactionLog {

    public static final String CLASS_ID = "029";
    public static final String KEY_DAILY_TRANSACTIONS = "dailyTransactions";

    private static final Map<LevelAccessor, TransactionLog> LOGS = new HashMap<>();

    private final ServerLevel level;
    private final List<Transaction> pending = new ArrayList<>();

    private TransactionLog(ServerLevel level) {
        this.level = level;
    }

    public static TransactionLog init(ServerLevel level) {
        TransactionLog log = new TransactionLog(level);
        LOGS.put(level, log);
        LoggerProject.logInit("029000", TransactionLog.class.getName());
        return log;
    }

    @Nullable
    public static TransactionLog get(LevelAccessor level) {
        return (level == null) ? null : LOGS.get(level);
    }

    public static void clearAll() {
        LOGS.clear();
    }

    public List<Transaction> getPending() {
        return new ArrayList<>(pending);
    }

    public int size() {
        return pending.size();
    }


    //** RECORDING **//

    public void record(@Nullable Transaction transaction) {
        if (transaction == null || !transaction.isValid()) return;
        pending.add(transaction);
    }

    /** Records both sides of a completed inter village sale **/
    public static void recordSale(LevelAccessor level, Sale sale) {
        TransactionLog log = get(level);
        if (log == null || sale == null) return;
        log.record(Transaction.buyerSide(sale));
        log.record(Transaction.sellerSide(sale));
    }

    /** Records a trade settled directly with a player **/
    public static void recordPlayerTrade(Mayor mayor, String resourceId, int quantity, float currency) {
        if (mayor == null) return;
        TransactionLog log = get(mayor.getLevel());
        if (log == null) return;
        log.record(Transaction.playerTrade(mayor, resourceId, quantity, currency));
    }


    //** DAILY ROLLOVER **//

    public void clearPendingOnDailyCycle() {
        pending.clear();
    }


    //** REPLAY **//

    /**
     * Applies serialzied journalled transactions from the previous day to the
     * respective mayors so they don't lose progress prior to server shutdown
     */
    public int replay(VillageManager manager) {
        if (manager == null || pending.isEmpty()) return 0;

        int applied = 0;
        int orphaned = 0;
        int contractor = 0;

        for (Transaction transaction : pending) {
            if (Mayor.DUMMY_ID.equals(transaction.getVillageChunkId())) { contractor++; continue; }

            Mayor mayor = manager.getMayor(transaction.getVillageChunkId());
            if (mayor == null) { orphaned++; continue; }
            transaction.apply(mayor.getTheoLedger());
            applied++;
        }

        LoggerProject.logInfo( "029002", "Replayed " + applied + " transactions");

        pending.clear();
        return applied;
    }


    //** PERSISTENCE **//

    public void save(DataStore ds) {
        if (ds == null) return;
        WorldSaveData worldData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);

        JsonArray arr = new JsonArray();
        for (Transaction transaction : pending)
            arr.add(transaction.serialize());

        worldData.addProperty(KEY_DAILY_TRANSACTIONS, arr);
    }

    public void load(DataStore ds) {
        if (ds == null) return;
        WorldSaveData worldData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);

        pending.clear();
        JsonElement el = worldData.get(KEY_DAILY_TRANSACTIONS);
        if (el == null || !el.isJsonArray()) return;

        for (JsonElement entry : el.getAsJsonArray()) {
            try {
                if (!entry.isJsonObject()) continue;
                Transaction transaction = Transaction.deserialize(entry.getAsJsonObject());
                if (transaction != null && transaction.isValid()) pending.add(transaction);
            } catch (Exception e) {
                LoggerProject.logWarning("029003", "Could not parse transaction " + entry + ". " + e.getMessage());
            }
        }

        LoggerProject.logInfo("029004", "Loaded " + pending.size() + "transactions");
    }
}
