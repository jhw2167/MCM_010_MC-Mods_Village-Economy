package com.holybuckets.villageecon.core.trade;

import com.google.gson.JsonObject;
import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.ResourceLedger;

/**
 * One ledger affecting event, recorded from the point of view of a single village.
 * Every inter village sale produces two of these, one for the buyer and one for the
 * seller; a player trade produces one. Quantities and currency are signed, so applying
 * a transaction to a ledger is always an addition.
 *
 * These are journalled to the DataStore each day so a mayor whose entity is unloaded
 * (or whose entity NBT is a day stale after a crash) can be caught up on world load.
 */
public class Transaction {

    public static final String CLASS_ID = "028";

    private static final String KEY_VILLAGE = "v";
    private static final String KEY_RESOURCE = "r";
    private static final String KEY_QUANTITY = "q";
    private static final String KEY_CURRENCY = "c";
    private static final String KEY_TIME = "t";

    private final String villageChunkId;    //village whose ledger this applies to
    private final String resourceId;
    private final int quantity;             //stacks gained (+) or given up (-)
    private final float currency;           //currency gained (+) or spent (-)
    private final long gameTime;

    public Transaction(String villageChunkId, String resourceId, int quantity, float currency, long gameTime) {
        this.villageChunkId = (villageChunkId == null) ? "" : villageChunkId;
        this.resourceId = (resourceId == null) ? "" : resourceId;
        this.quantity = quantity;
        this.currency = currency;
        this.gameTime = gameTime;
    }

    public String getVillageChunkId() { return villageChunkId; }

    public String getResourceId() { return resourceId; }

    public int getQuantity() { return quantity; }

    public float getCurrency() { return currency; }

    public long getGameTime() { return gameTime; }

    public boolean isValid() {
        return !villageChunkId.isEmpty() && !resourceId.isEmpty();
    }

    /** Applies this transaction to a ledger. Signed, so this is always additive **/
    public void apply(ResourceLedger ledger) {
        if (ledger == null) return;
        if (quantity != 0) ledger.add(resourceId, quantity);
        if (currency != 0f) ledger.addCurrency(currency);
    }


    //** Factories **//

    /** The buyer's side of a sale: gains resources, pays currency **/
    public static Transaction buyerSide(Sale sale) {
        if (sale == null || sale.getBuyer() == null) return null;
        return new Transaction(sale.getBuyer().getVillageChunkId(), sale.getResourceId(),
            sale.getSaleQuantity(), -sale.getSalePrice() * sale.getSaleQuantity(), sale.getSaleTime());
    }

    /** The seller's side of a sale: gives up resources, receives currency **/
    public static Transaction sellerSide(Sale sale) {
        if (sale == null || sale.getSeller() == null) return null;
        return new Transaction(sale.getSeller().getVillageChunkId(), sale.getResourceId(),
            -sale.getSaleQuantity(), sale.getSalePrice() * sale.getSaleQuantity(), sale.getSaleTime());
    }

    /** A trade settled directly with a player **/
    public static Transaction playerTrade(Mayor mayor, String resourceId, int quantity, float currency) {
        if (mayor == null) return null;
        long time = (mayor.getLevel() != null) ? mayor.getLevel().getGameTime() : 0L;
        return new Transaction(mayor.getVillageChunkId(), resourceId, quantity, currency, time);
    }


    //** Serialization **//

    public JsonObject serialize() {
        JsonObject obj = new JsonObject();
        obj.addProperty(KEY_VILLAGE, villageChunkId);
        obj.addProperty(KEY_RESOURCE, resourceId);
        obj.addProperty(KEY_QUANTITY, quantity);
        obj.addProperty(KEY_CURRENCY, currency);
        obj.addProperty(KEY_TIME, gameTime);
        return obj;
    }

    public static Transaction deserialize(JsonObject obj) {
        if (obj == null) return null;
        return new Transaction(
            obj.has(KEY_VILLAGE) ? obj.get(KEY_VILLAGE).getAsString() : "",
            obj.has(KEY_RESOURCE) ? obj.get(KEY_RESOURCE).getAsString() : "",
            obj.has(KEY_QUANTITY) ? obj.get(KEY_QUANTITY).getAsInt() : 0,
            obj.has(KEY_CURRENCY) ? obj.get(KEY_CURRENCY).getAsFloat() : 0f,
            obj.has(KEY_TIME) ? obj.get(KEY_TIME).getAsLong() : 0L);
    }

    @Override
    public String toString() {
        return String.format("Transaction[%s %s x%+d, %+.2f currency, t=%d]",
            villageChunkId, resourceId, quantity, currency, gameTime);
    }
}
