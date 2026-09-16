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

    public MayorTradeOffer(String resourceId, Item item, int ledgerAmount, int quotaAmount, float marketRate) {
        this.resourceId = (resourceId == null) ? "" : resourceId;
        this.item = item;
        this.ledgerAmount = ledgerAmount;
        this.quotaAmount = quotaAmount;
        this.marketRate = marketRate;
    }

    public String getResourceId() { return resourceId; }

    public Item getItem() { return item; }

    public ItemStack getIcon() { return (item != null) ? new ItemStack(item) : ItemStack.EMPTY; }

    public int getLedgerAmount() { return ledgerAmount; }

    public int getQuotaAmount() { return quotaAmount; }

    public float getMarketRate() { return marketRate; }


    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(resourceId);
        buf.writeItem(getIcon());
        buf.writeVarInt(ledgerAmount);
        buf.writeVarInt(quotaAmount);
        buf.writeFloat(marketRate);
    }

    public static MayorTradeOffer read(FriendlyByteBuf buf) {
        String resourceId = buf.readUtf();
        ItemStack icon = buf.readItem();
        int ledger = buf.readVarInt();
        int quota = buf.readVarInt();
        float rate = buf.readFloat();
        return new MayorTradeOffer(resourceId, icon.getItem(), ledger, quota, rate);
    }
}
