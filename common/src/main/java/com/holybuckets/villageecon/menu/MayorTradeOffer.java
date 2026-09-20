package com.holybuckets.villageecon.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MayorTradeOffer {

    private final String resourceId;
    private final Item item;
    private final int ledgerAmount;
    private final int quotaAmount;
    private final float marketRate;
    private final int cycleDelta;      //theoLedger - staticLedger: net traded this cycle
    private final boolean produces;    //village produces this resource at its current level

    public MayorTradeOffer(String resourceId, Item item, int ledgerAmount, int quotaAmount,
        float marketRate, int cycleDelta, boolean produces) {
        this.resourceId = (resourceId == null) ? "" : resourceId;
        this.item = item;
        this.ledgerAmount = ledgerAmount;
        this.quotaAmount = quotaAmount;
        this.marketRate = marketRate;
        this.cycleDelta = cycleDelta;
        this.produces = produces;
    }

    public String getResourceId() { return resourceId; }

    public Item getItem() { return item; }

    public ItemStack getIcon() { return (item != null) ? new ItemStack(item) : ItemStack.EMPTY; }

    public int getLedgerAmount() { return ledgerAmount; }

    public int getQuotaAmount() { return quotaAmount; }

    public float getMarketRate() { return marketRate; }

    public int getCycleDelta() { return cycleDelta; }

    public boolean producesResource() { return produces; }


    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(resourceId);
        buf.writeItem(getIcon());
        buf.writeVarInt(ledgerAmount);
        buf.writeVarInt(quotaAmount);
        buf.writeFloat(marketRate);
        buf.writeInt(cycleDelta);
        buf.writeBoolean(produces);
    }

    public static MayorTradeOffer read(FriendlyByteBuf buf) {
        String resourceId = buf.readUtf();
        ItemStack icon = buf.readItem();
        int ledger = buf.readVarInt();
        int quota = buf.readVarInt();
        float rate = buf.readFloat();
        int cycleDelta = buf.readInt();
        boolean produces = buf.readBoolean();
        return new MayorTradeOffer(resourceId, icon.getItem(), ledger, quota, rate, cycleDelta, produces);
    }
}
