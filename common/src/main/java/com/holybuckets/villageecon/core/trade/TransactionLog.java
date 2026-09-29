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
 * Journal of every ledger affecting transaction since the last daily process.
 *
 * Mayors live in RAM and write their ledgers into their entity's compound tag, but an
 * entity that is unloaded (or a server that stops mid day) leaves that tag stale. This
 * log is written to the DataStore alongside it, so on world load the theoretical ledgers
 * can be replayed forward from whatever the entities happened to persist.
 *
 * The log is cleared at the end of each daily process, once every living mayor has
 * pushed its fresh ledger state back onto its entity.
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
        LoggerProject.logInit(CLASS_ID + "000", TransactionLog.class.getName());
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

    /**
     * Called once the day's ledger state has been pushed back onto the mayor entities.
     * Everything up to this point is now captured in entity NBT, so the journal restarts.
     */
    public void rollOver() {
        if (!pending.isEmpty()) {
            LoggerProject.logInfo(CLASS_ID + "001",
                "Discarding " + pending.size() + " journalled transaction(s) now captured in mayor entity data");
        }
        pending.clear();
    }


    //** REPLAY **//

    /**
     * Applies every journalled transaction to the matching mayor's theoretical ledger.
     * Run only after the mayor entities have loaded and their ledgers been rehydrated,
     * otherwise the transactions would be applied to ledgers that are about to be
     * overwritten by the entity's own compound tag.
     */
    public int replay(VillageManager manager) {
        if (manager == null || pending.isEmpty()) return 0;

        int applied = 0;
        int orphaned = 0;
        int contractor = 0;

        for (Transaction transaction : pending) {
            //The independent contractor is journalled for the record but has no ledger to
            //restore, so its side of a sale is skipped rather than counted as orphaned
            if (Mayor.DUMMY_ID.equals(transaction.getVillageChunkId())) { contractor++; continue; }

            Mayor mayor = manager.getMayor(transaction.getVillageChunkId());
            if (mayor == null) { orphaned++; continue; }
            transaction.apply(mayor.getTheoLedger());
            applied++;
        }

        LoggerProject.logInfo(CLASS_ID + "002", "Replayed " + applied
            + " transaction(s) onto theoretical ledgers"
            + (contractor > 0 ? "; skipped " + contractor + " contractor side(s)" : "")
            + (orphaned > 0 ? "; " + orphaned + " had no resident mayor and were dropped" : ""));

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
                LoggerProject.logWarning(CLASS_ID + "003",
                    "Could not parse journalled transaction " + entry + ". " + e.getMessage());
            }
        }

        LoggerProject.logInfo(CLASS_ID + "004",
            "Loaded " + pending.size() + " journalled transaction(s) awaiting replay");
    }
}
