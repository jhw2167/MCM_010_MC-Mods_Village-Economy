package com.holybuckets.villageecon.menu;

import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.MarketState;
import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.ResourceLedger;
import com.holybuckets.villageecon.entity.MayorEntity;
import com.holybuckets.villageecon.core.trade.Bazaar;
import com.holybuckets.villageecon.core.trade.Market;
import com.holybuckets.villageecon.networking.LedgerSalesSync;
import com.holybuckets.villageecon.networking.MayorOffersSync;
import com.holybuckets.foundation.HBUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MayorTradeMenu extends AbstractContainerMenu {

    public static final int GUI_WIDTH = 276;
    public static final int GUI_HEIGHT = 166;

    public static final int TRADE_LIST_X = 5;
    public static final int TRADE_LIST_Y = 18;
    public static final int TRADE_LIST_WIDTH = 88;
    public static final int TRADE_LIST_HEIGHT = 140;
    public static final int TRADE_ROW_HEIGHT = 20;
    public static final int VISIBLE_ROWS = TRADE_LIST_HEIGHT / TRADE_ROW_HEIGHT;

    public static final int SCROLLBAR_X = 94;
    public static final int SCROLLBAR_Y = 18;
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int SCROLLBAR_HEIGHT = 140;

    public static final int INPUT_A_X = 116;
    public static final int INPUT_A_Y = 108;
    public static final int INPUT_B_X = 162;
    public static final int INPUT_B_Y = 37;
    public static final int OUTPUT_X = 176;
    public static final int OUTPUT_Y = 112;
    public static final int ARROW_X = 186;
    public static final int ARROW_Y = 38;

    public static final int TOGGLE_WIDTH = 60;
    public static final int TOGGLE_HEIGHT = 18;
    public static final int TOGGLE_X = 268 - TOGGLE_WIDTH;
    public static final int TOGGLE_Y = 142 - TOGGLE_HEIGHT - 2;

    public static final int INV_X = 106;
    public static final int INV_Y = 18;
    public static final int HOTBAR_Y = 142;

    public static final int GRAPH_X = 108;
    public static final int GRAPH_WIDTH = 162;
    public static final int GRAPH_HEIGHT = 54;

    public static final int BUTTON_TOGGLE_VIEW = 100;

    /** FriendlyByteBuf.writeItem serialises the stack count as a single byte, so a slot
     *  stack that must survive a round trip to the client cannot exceed this. **/
    public static final int MAX_SYNC_COUNT = 127;

    private static final int INPUT_SLOTS = 1;
    private static final int OUTPUT_SLOT = 1;

    private final Container tradeContainer;
    private final List<MayorTradeOffer> offers = new ArrayList<>();
    private final Player player;

    @Nullable
    private final Mayor mayor;

    private float reserveCurrency = 0f;
    private String villageName = "";
    private float currencyDelta = 0f;
    private int selectedOffer = 0;
    private boolean graphView = true;
    private boolean updatingOutput = false;

    public MayorTradeMenu(int syncId, Inventory playerInventory, @Nullable Mayor mayor,
        List<MayorTradeOffer> offers, float reserveCurrency, String villageName, float currencyDelta)
    {
        super(ModMenus.mayorTradeMenu.get(), syncId);
        this.player = playerInventory.player;
        this.mayor = mayor;
        if (offers != null) this.offers.addAll(offers);
        this.reserveCurrency = reserveCurrency;
        this.villageName = (villageName == null) ? "" : villageName;
        this.currencyDelta = currencyDelta;
        if (mayor != null) mayor.beginInteraction();

        this.tradeContainer = new SimpleContainer(2) {
            @Override
            public int getMaxStackSize() {
                return ModConfig.getInstance().getMayorTradeSlotCapacity();
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!MayorTradeMenu.this.updatingOutput)
                    MayorTradeMenu.this.updateOutput();
            }
        };

        this.addSlot(new CapacitySlot(tradeContainer, 0, INPUT_A_X, INPUT_A_Y));
        //this.addSlot(new CapacitySlot(tradeContainer, 1, INPUT_B_X, INPUT_B_Y));
        this.addSlot(new OutputSlot(tradeContainer, OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new ToggleSlot(playerInventory, col + row * 9 + 9,
                    INV_X + col * 18, INV_Y + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public static MayorTradeMenu fromNetwork(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<MayorTradeOffer> offers = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            offers.add(MayorTradeOffer.read(buf));
        float reserveCurrency = buf.readFloat();
        String villageName = buf.readUtf();
        float currencyDelta = buf.readFloat();
        return new MayorTradeMenu(syncId, playerInventory, null, offers, reserveCurrency, villageName, currencyDelta);
    }

    public static void writeOffers(FriendlyByteBuf buf, List<MayorTradeOffer> offers) {
        buf.writeVarInt(offers.size());
        for (MayorTradeOffer offer : offers)
            offer.write(buf);
    }

    public static List<MayorTradeOffer> buildOffers(Mayor mayor)
    {
        List<MayorTradeOffer> offers = new ArrayList<>();
        if (mayor == null) return offers;

        int level = mayor.getVillageLevel();
        for (EconomyResource resource : mayor.getActiveResources())
        {
            String id = resource.getResourceId();
            int delta = mayor.getTheoLedger().get(id) - mayor.getStaticLedger().get(id);
            boolean produces = resource.productionAt(level) > 0;
            offers.add(new MayorTradeOffer(id, resource.getItem(),
                mayor.getAvailable(id), mayor.getQuota(id), MarketState.marketRate(id), delta, produces));
        }
        return offers;
    }


    public Container getTradeContainer() { return tradeContainer; }

    public List<MayorTradeOffer> getOffers() { return offers; }

    /** The village's reserve currency, as last synced from the server **/
    public float getReserveCurrency() { return reserveCurrency; }

    /** Display name of the village being traded with **/
    public String getVillageName() { return villageName; }

    /** Net currency gained or lost through village to village trades this cycle **/
    public float getCurrencyDelta() { return currencyDelta; }

    /** theoLedger currency less staticLedger currency **/
    public static float currencyDelta(Mayor mayor) {
        if (mayor == null) return 0f;
        return mayor.getTheoLedger().getCurrency() - mayor.getStaticLedger().getCurrency();
    }

    public int getSelectedOffer() { return selectedOffer; }

    public boolean isGraphView() { return graphView; }

    @Nullable
    public MayorTradeOffer getSelected() {
        if (offers.isEmpty()) return null;
        int index = Math.max(0, Math.min(selectedOffer, offers.size() - 1));
        return offers.get(index);
    }

    public void setGraphView(boolean graphView) {
        this.graphView = graphView;
    }

    /** Applied on the client when the server pushes refreshed stock after a trade **/
    public void setOffers(List<MayorTradeOffer> updated, float reserveCurrency, String villageName, float currencyDelta) {
        this.reserveCurrency = reserveCurrency;
        this.villageName = (villageName == null) ? "" : villageName;
        this.currencyDelta = currencyDelta;
        if (updated == null) return;
        this.offers.clear();
        this.offers.addAll(updated);
        if (this.selectedOffer >= this.offers.size())
            this.selectedOffer = Math.max(0, this.offers.size() - 1);
    }

    /** Rebuilds offers from the mayor's current stock and pushes them to the client **/
    private void syncOffers() {
        if (mayor == null || !(player instanceof ServerPlayer serverPlayer)) return;
        List<MayorTradeOffer> updated = buildOffers(mayor);
        float reserve = mayor.getStaticLedger().getCurrency();
        String name = mayor.getName();
        float delta = currencyDelta(mayor);
        setOffers(updated, reserve, name, delta);
        HBUtil.NetworkUtil.serverSendToPlayer(serverPlayer, new MayorOffersSync(updated, reserve, name, delta));
    }

    /**
     * Pushes the global sale history for the newly selected resource straight away, so the
     * graph has data the moment a player switches items rather than on the next sync tick.
     */
    private void syncSelectedSales() {
        if (mayor == null || !(player instanceof ServerPlayer serverPlayer)) return;

        MayorTradeOffer offer = getSelected();
        if (offer == null || offer.getItem() == null) return;

        Bazaar bazaar = Bazaar.get(mayor.getLevel());
        if (bazaar == null) return;

        Market market = bazaar.getMarket(offer.getItem());
        if (market == null) return;

        HBUtil.NetworkUtil.serverSendToPlayer(serverPlayer, new LedgerSalesSync(
            offer.getResourceId(), market.rate(), market.getRecentSalePricesRounded()));
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == tradeContainer) updateOutput();
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        if (id == BUTTON_TOGGLE_VIEW) {
            this.graphView = !this.graphView;
            return true;
        }
        if (id >= 0 && id < offers.size()) {
            this.selectedOffer = id;
            updateOutput();
            syncSelectedSales();
            return true;
        }
        return false;
    }

    private void updateOutput()
    {
        if (updatingOutput) return;
        updatingOutput = true;
        try {
            computeOutput();
        } finally {
            updatingOutput = false;
        }
    }

    /**
     * Writes the output slot and pushes it to the client directly. Slot changes made while
     * the server is handling a container click are suppressed by the vanilla packet handler,
     * so the result has to be sent explicitly, as the vanilla crafting menus do.
     */
    private void setOutput(ItemStack stack)
    {
        tradeContainer.setItem(OUTPUT_SLOT, stack);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                this.containerId, this.incrementStateId(), OUTPUT_SLOT, stack));
        }
    }

    /**
     * True if the stack is a valid item of exchange for this offer. Defers to the
     * EconomyResource so a useTags entry accepts any item in the tag, not just the
     * resource's own item. Falls back to item equality if the resource is unavailable.
     */
    private boolean matchesOffer(MayorTradeOffer offer, ItemStack stack)
    {
        if (offer == null || stack.isEmpty()) return false;

        ModConfig config = ModConfig.getInstance();
        EconomyResource resource = (config != null) ? config.getResource(offer.getResourceId()) : null;
        if (resource != null) return resource.matches(stack);

        return offer.getItem() != null && stack.is(offer.getItem());
    }

    private void computeOutput()
    {
        MayorTradeOffer offer = getSelected();
        if (offer == null) {
            setOutput(ItemStack.EMPTY);
            return;
        }

        Item currency = ModConfig.getInstance().getCurrencyItem();
        float rate = Math.max(0.01f, offer.getMarketRate());

        int currencyIn = 0;
        int resourceIn = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = tradeContainer.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(currency)) currencyIn += stack.getCount();
            else if (matchesOffer(offer, stack))
                resourceIn += stack.getCount()/64;
        }

        if (resourceIn > 0 && offer.getItem() != null) {
            int payout = (int) Math.floor(resourceIn * rate);
            setOutput(payout > 0 ? new ItemStack(currency, payout) : ItemStack.EMPTY);
            return;
        }

        if (currencyIn > 0 && offer.getItem() != null) {
            int available = offer.getLedgerAmount();
            int affordable = (int) Math.floor(currencyIn / rate);
            int amount = Math.min(available, affordable);
            setOutput(amount > 0 ? new ItemStack(offer.getItem(), amount*64) : ItemStack.EMPTY);
            return;
        }

        setOutput(ItemStack.EMPTY);
    }

    private void onOutputTaken(ItemStack taken)
    {
        MayorTradeOffer offer = getSelected();
        if (offer == null || mayor == null) {
            consumeInputs(0);
            return;
        }
        //calculate leftover in input slot after dividing by 64, and put it back in the input slot

        Item currency = ModConfig.getInstance().getCurrencyItem();
        ResourceLedger ledger = mayor.getTheoLedger();
        ResourceLedger staticLedger = mayor.getStaticLedger();
        float rate = Math.max(0.01f, offer.getMarketRate());

        int leftover = 0;
        if (taken.is(currency)) {
            //Player sold stacks of the resource and took currency
            int resourceIn = countOfferInput(offer);
            int stacks = resourceIn / 64;
            leftover = resourceIn % 64;
            ledger.add(offer.getResourceId(), stacks);
            ledger.addCurrency(-taken.getCount());
            staticLedger.add(offer.getResourceId(), stacks);
            staticLedger.addCurrency(-taken.getCount());
        } else if (matchesOffer(offer, taken)) {
            //Player spent currency and took stacks of the resource
            int currencyIn = countInput(currency);
            int stacks = taken.getCount() / 64;
            int cost = (int) Math.ceil(stacks * rate);
            leftover = Math.max(0, currencyIn - cost);
            ledger.remove(offer.getResourceId(), stacks);
            ledger.addCurrency(cost);
            staticLedger.remove(offer.getResourceId(), stacks);
            staticLedger.addCurrency(cost);
        }

        consumeInputs(leftover);
        syncOffers();
    }

    private int countOfferInput(MayorTradeOffer offer) {
        int total = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = tradeContainer.getItem(i);
            if (matchesOffer(offer, stack)) total += stack.getCount();
        }
        return total;
    }

    private int countInput(@Nullable Item item) {
        if (item == null) return 0;
        int total = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = tradeContainer.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private void consumeInputs(int leftOverStack) {
        updatingOutput = true;
        try {
            if(leftOverStack > 0 ) {
                tradeContainer.getItem(0).setCount(leftOverStack);
            } else {
             tradeContainer.setItem(0, ItemStack.EMPTY);
            }
            tradeContainer.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
        } finally {
            updatingOutput = false;
        }
    }

    /**
     * Vanilla moveItemStackTo places at most one slot's worth into an empty slot and then
     * stops. The trade slots hold far more than a vanilla stack, so an oversized stack has
     * to be moved out across several passes until it is empty or the inventory is full.
     */
    private boolean moveEntireStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection)
    {
        boolean moved = false;
        while (!stack.isEmpty()) {
            if (!this.moveItemStackTo(stack, startIndex, endIndex, reverseDirection)) break;
            moved = true;
        }
        return moved;
    }

    /** Effective input capacity: the configured slot size, bounded by what can be synced **/
    private int inputCapacity() {
        return Math.min(ModConfig.getInstance().getMayorTradeSlotCapacity(), MAX_SYNC_COUNT);
    }

    /**
     * Merges a stack into the input slot past the item's own max stack size, so repeated
     * shift clicks accumulate. Vanilla moveItemStackTo caps merges at stack.getMaxStackSize().
     */
    private boolean mergeIntoInput(ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        ItemStack current = tradeContainer.getItem(0);
        int cap = inputCapacity();

        if (current.isEmpty()) {
            int move = Math.min(stack.getCount(), cap);
            if (move <= 0) return false;
            tradeContainer.setItem(0, stack.split(move));
            return true;
        }

        if (!ItemStack.isSameItemSameTags(current, stack)) return false;
        int room = cap - current.getCount();
        if (room <= 0) return false;

        int move = Math.min(room, stack.getCount());
        current.grow(move);
        stack.shrink(move);
        tradeContainer.setChanged();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        final int invStart = INPUT_SLOTS + 1;
        final int invEnd = this.slots.size();

        if (index == OUTPUT_SLOT) {
            if (!moveEntireStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, result);
            onOutputTaken(result);
        } else if (index < invStart) {
            if (!moveEntireStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            if (!mergeIntoInput(stack)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        updateOutput();
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (mayor != null && !player.level().isClientSide()) mayor.endInteraction();
        if (player.level().isClientSide()) return;
        updatingOutput = true;
        try {
            for (int i = 0; i < INPUT_SLOTS; i++) {
                ItemStack stack = tradeContainer.getItem(i);
                if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
                tradeContainer.setItem(i, ItemStack.EMPTY);
            }
            tradeContainer.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
        } finally {
            updatingOutput = false;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (mayor == null) return true;
        MayorEntity entity = mayor.getEntity();
        if (entity == null) return false;
        return entity.isAlive() && entity.distanceToSqr(player) <= 64.0D;
    }


    private class CapacitySlot extends Slot {
        CapacitySlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !MayorTradeMenu.this.graphView;
        }

        @Override
        public int getMaxStackSize() {
            return ModConfig.getInstance().getMayorTradeSlotCapacity();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return getMaxStackSize();
        }
    }

    private class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !MayorTradeMenu.this.graphView;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            MayorTradeMenu.this.onOutputTaken(stack);
            super.onTake(player, stack);
        }

        @Override
        public int getMaxStackSize() {
            return ModConfig.getInstance().getMayorTradeSlotCapacity();
        }
    }

    private class ToggleSlot extends Slot {
        ToggleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !MayorTradeMenu.this.graphView;
        }
    }
}
