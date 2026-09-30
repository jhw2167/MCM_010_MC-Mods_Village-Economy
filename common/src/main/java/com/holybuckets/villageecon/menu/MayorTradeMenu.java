package com.holybuckets.villageecon.menu;

import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.MarketState;
import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.ResourceLedger;
import com.holybuckets.villageecon.entity.MayorEntity;
import com.holybuckets.villageecon.core.trade.Bazaar;
import com.holybuckets.villageecon.core.trade.Market;
import com.holybuckets.villageecon.core.trade.TransactionLog;
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
import net.minecraft.world.inventory.DataSlot;
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
    public static final int TRADE_LIST_HEIGHT = 120;
    public static final int TRADE_ROW_HEIGHT = 20;
    public static final int VISIBLE_ROWS = TRADE_LIST_HEIGHT / TRADE_ROW_HEIGHT;

    public static final int SCROLLBAR_X = 94;
    public static final int SCROLLBAR_Y = 18;
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int SCROLLBAR_HEIGHT = 120;

    //Vanilla merchant geometry: two inputs, arrow, result
    public static final int INPUT_A_X = 136;
    public static final int INPUT_A_Y = 37;
    public static final int INPUT_B_X = 162;
    public static final int INPUT_B_Y = 37;
    public static final int OUTPUT_X = 220;
    public static final int OUTPUT_Y = 37;
    public static final int ARROW_X = 186;
    public static final int ARROW_Y = 38;

    //Toggle sits under the trade list, clear of the shortened list above it
    public static final int TOGGLE_WIDTH = TRADE_LIST_WIDTH;
    public static final int TOGGLE_HEIGHT = 18;
    public static final int TOGGLE_X = TRADE_LIST_X;
    public static final int TOGGLE_Y = 142;

    //Player inventory sits directly above the hotbar, as in the vanilla screen
    public static final int INV_X = 108;
    public static final int INV_Y = 84;
    public static final int HOTBAR_Y = 142;

    public static final int GRAPH_X = 108;
    public static final int GRAPH_Y = 18;
    public static final int GRAPH_WIDTH = 162;
    public static final int GRAPH_HEIGHT = 80;

    public static final int BUTTON_TOGGLE_VIEW = 100;
    public static final int BUTTON_CLAIM_OUTPUT = 101;
    public static final int BUTTON_CANCEL_TRADE = 102;

    public static final int MAX_OUTPUT_AMOUNT = 32767;
    public static final int MAX_SYNC_COUNT = 127;

    public static final int INPUT_SLOT = 0;

    public static final int STACK_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    private static final int SLOT_COUNT = OUTPUT_SLOT + 1;

    private final Container tradeContainer;
    private final List<MayorTradeOffer> offers = new ArrayList<>();
    private final Player player;

    @Nullable
    private final Mayor mayor;

    private final DataSlot outputAmount = DataSlot.standalone();

    private float reserveCurrency = 0f;
    private float projectedCurrency = 0f;
    private String villageName = "";
    private float currencyDelta = 0f;
    private int selectedOffer = 0;
    private boolean graphView = true;
    private boolean updatingOutput = false;

    public MayorTradeMenu(int syncId, Inventory playerInventory, @Nullable Mayor mayor,
        List<MayorTradeOffer> offers, float reserveCurrency, float projectedCurrency, String villageName, float currencyDelta)
    {
        super(ModMenus.mayorTradeMenu.get(), syncId);
        this.player = playerInventory.player;
        this.mayor = mayor;
        if (offers != null) this.offers.addAll(offers);
        this.reserveCurrency = reserveCurrency;
        this.projectedCurrency = projectedCurrency;
        this.villageName = (villageName == null) ? "" : villageName;
        this.currencyDelta = currencyDelta;
        if (mayor != null) mayor.beginInteraction();

        this.tradeContainer = new SimpleContainer(SLOT_COUNT) {
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

        this.addSlot(new InputSlot(tradeContainer, INPUT_SLOT, INPUT_A_X, INPUT_A_Y));
        this.addSlot(new StackCounterSlot(tradeContainer, STACK_SLOT, INPUT_B_X, INPUT_B_Y));
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

        this.addDataSlot(outputAmount);
    }

    public static MayorTradeMenu fromNetwork(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<MayorTradeOffer> offers = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            offers.add(MayorTradeOffer.read(buf));
        float reserveCurrency = buf.readFloat();
        float projectedCurrency = buf.readFloat();
        String villageName = buf.readUtf();
        float currencyDelta = buf.readFloat();
        return new MayorTradeMenu(syncId, playerInventory, null, offers, reserveCurrency, projectedCurrency, villageName, currencyDelta);
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
            int delta = mayor.getTradeDiff(id);
            boolean produces = resource.productionAt(level) > 0;
            offers.add(new MayorTradeOffer(id, resource.getItem(),
                mayor.getAvailable(id), mayor.getQuota(id), mayor.playerPrice(id), delta, produces));
        }
        return offers;
    }


    public Container getTradeContainer() { return tradeContainer; }

    public List<MayorTradeOffer> getOffers() { return offers; }


    public float getReserveCurrency() { return reserveCurrency; }

    public float getProjectedCurrency() { return projectedCurrency; }


    public String getVillageName() { return villageName; }


    public float getCurrencyDelta() { return currencyDelta; }

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

    //resets offers after trade completion
    public void setOffers(List<MayorTradeOffer> updated, float reserveCurrency, float projectedCurrency, String villageName, float currencyDelta) {
        this.reserveCurrency = reserveCurrency;
        this.projectedCurrency = projectedCurrency;
        this.villageName = (villageName == null) ? "" : villageName;
        this.currencyDelta = currencyDelta;
        if (updated == null) return;
        this.offers.clear();
        this.offers.addAll(updated);
        if (this.selectedOffer >= this.offers.size())
            this.selectedOffer = Math.max(0, this.offers.size() - 1);
    }

    //Sends server mayor trade data to client
    private void syncOffers() {
        if (mayor == null || !(player instanceof ServerPlayer serverPlayer)) return;
        List<MayorTradeOffer> updated = buildOffers(mayor);
        float reserve = mayor.getStaticLedger().getCurrency();
        String name = mayor.getName();
        float delta = mayor.currencyDiff();
        float projected = mayor.getTheoLedger().getCurrency();
        setOffers(updated, reserve, projected, name, delta);
        HBUtil.NetworkUtil.serverSendToPlayer(serverPlayer, new MayorOffersSync(updated, reserve, projected, name, delta));
    }

    private void syncSelectedSales() {
        if (mayor == null || !(player instanceof ServerPlayer serverPlayer)) return;

        MayorTradeOffer offer = getSelected();
        if (offer == null || offer.getItem() == null) return;

        Bazaar bazaar = Bazaar.get(mayor.getLevel());
        if (bazaar == null) return;

        Market market = bazaar.getMarket(offer.getItem());
        if (market == null) return;

        HBUtil.NetworkUtil.serverSendToPlayer(serverPlayer, new LedgerSalesSync(
            offer.getResourceId(), market.rate(), market.getRecentSalePricesRounded(), market.getRecentSaleQuantities()));
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
            cancelTrade(player);
            this.graphView = !this.graphView;
            return true;
        }
        if (id == BUTTON_CLAIM_OUTPUT) {
            claimOutput(player);
            return true;
        }
        if (id == BUTTON_CANCEL_TRADE) {
            cancelTrade(player);
            return true;
        }
        if (id >= 0 && id < offers.size()) {
            cancelTrade(player);
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

    private void setOutput(@Nullable Item item, int amount)
    {
        int clamped = Math.max(0, Math.min(amount, MAX_OUTPUT_AMOUNT));
        ItemStack icon = (item == null || clamped <= 0) ? ItemStack.EMPTY : new ItemStack(item, 1);

        outputAmount.set(clamped);
        tradeContainer.setItem(OUTPUT_SLOT, icon);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                this.containerId, this.incrementStateId(), OUTPUT_SLOT, icon));
        }
    }

    private void clearOutput() {
        setOutput(null, 0);
    }

    public int getOutputAmount() { return outputAmount.get(); }

    @Nullable
    public Item getOutputItem() {
        ItemStack icon = tradeContainer.getItem(OUTPUT_SLOT);
        return icon.isEmpty() ? null : icon.getItem();
    }

    private boolean stackMatchesOfferResource(MayorTradeOffer offer, ItemStack stack) {
        if(offer==null) return false;
        EconomyResource resource = EconomyResource.getById(offer.getResourceId());
        if(resource==null || resource.getTag()==null) return stack.is(offer.getItem());
        return stack.is(resource.getTag());
    }

    private void computeOutput()
    {
        MayorTradeOffer offer = getSelected();
        if (offer == null) {
            clearOutput();
            return;
        }

        Item currency = ModConfig.getInstance().getCurrencyItem();
        float rate = Math.max(0.01f, offer.getMarketRate());

        normalizeInput();

        int currencyIn = countInput(currency);
        int resourceRaw = countOfferInput(offer);
        int resourceIn = (offer.getItem() != null) ? resourceRaw / stackUnit(offer.getItem()) : 0;
        if (currencyIn > 0) resourceIn = 0;

        if (resourceIn > 0 && offer.getItem() != null) {
            int payout = (int) Math.floor(resourceIn * rate);
            setOutput(currency, payout);
            return;
        }

        if (currencyIn > 0 && offer.getItem() != null) {
            int available = offer.getLedgerAmount();
            int affordable = (int) Math.floor(currencyIn / rate);
            int amount = Math.min(available, affordable);
            setOutput(offer.getItem(), amount * stackUnit(offer.getItem()));
            return;
        }

        clearOutput();
    }

    /**
     * - Clears trade input,
     * - updates then ledgers and gives player the resource
     * - currency stacks.
     */
    private void claimOutput(Player player)
    {
        MayorTradeOffer offer = getSelected();
        Item payoutItem = getOutputItem();
        int payout = outputAmount.get();

        if (offer == null || mayor == null || payoutItem == null || payout <= 0) return;

        Item currency = ModConfig.getInstance().getCurrencyItem();
        ResourceLedger ledger = mayor.getTheoLedger();
        ResourceLedger staticLedger = mayor.getStaticLedger();
        float rate = Math.max(0.01f, offer.getMarketRate());

        int leftover = 0;
        if (payoutItem == currency) {
            //Player sold stacks of the resource and is paid currency
            int unit = stackUnit(offer.getItem());
            int resourceIn = countOfferInput(offer);
            int stacks = resourceIn / unit;
            leftover = resourceIn % unit;

            ledger.add(offer.getResourceId(), stacks);
            ledger.addCurrency(-payout);
            staticLedger.add(offer.getResourceId(), stacks);
            staticLedger.addCurrency(-payout);
            TransactionLog.recordPlayerTrade(mayor, offer.getResourceId(), stacks, -payout);
        } else {
            //Player spent currency and is given stacks of the resource
            int currencyIn = countInput(currency);
            int stacks = payout / stackUnit(payoutItem);
            int cost = (int) Math.ceil(stacks * rate);
            leftover = Math.max(0, currencyIn - cost);

            ledger.remove(offer.getResourceId(), stacks);
            ledger.addCurrency(cost);
            staticLedger.remove(offer.getResourceId(), stacks);
            staticLedger.addCurrency(cost);
            TransactionLog.recordPlayerTrade(mayor, offer.getResourceId(), -stacks, cost);
        }

        givePlayer(player, payoutItem, payout);
        consumeInputs(leftover);
        syncOffers();
    }

    private void givePlayer(Player recipient, Item item, int amount)
    {
        int unit = stackUnit(item);
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(unit, remaining);
            recipient.getInventory().placeItemBackInInventory(new ItemStack(item, give));
            remaining -= give;
        }
    }

    private static int stackUnit(@Nullable Item item) {
        if (item == null) return 64;
        return Math.max(1, item.getMaxStackSize());
    }

    @Nullable
    private Item heldInputItem() {
        ItemStack loose = tradeContainer.getItem(INPUT_SLOT);
        if (!loose.isEmpty()) return loose.getItem();
        ItemStack counted = tradeContainer.getItem(STACK_SLOT);
        return counted.isEmpty() ? null : counted.getItem();
    }

    //counts the total stacks of the offer resource in the input slots, including the counter
    private int countOfferInput(MayorTradeOffer offer) {
        int total = 0;
        ItemStack inputItem = tradeContainer.getItem(INPUT_SLOT);
        if (stackMatchesOfferResource(offer, inputItem)) total += inputItem.getCount();

        ItemStack counted = tradeContainer.getItem(STACK_SLOT);
        if (stackMatchesOfferResource(offer, counted)) total += counted.getCount() * stackUnit(counted.getItem());

        return total;
    }

    private int countInput(@Nullable Item item) {
        if (item == null) return 0;
        int total = 0;
        ItemStack inputItem = tradeContainer.getItem(INPUT_SLOT);
        if (inputItem.is(item)) total += inputItem.getCount();

        ItemStack counted = tradeContainer.getItem(STACK_SLOT);
        if (counted.is(item)) total += counted.getCount() * stackUnit(item);

        return total;
    }

    private void normalizeInput()
    {
        ItemStack inputItem = tradeContainer.getItem(INPUT_SLOT);
        if (inputItem.isEmpty()) return;

        int unit = stackUnit(inputItem.getItem());
        if (inputItem.getCount() < unit) return;

        MayorTradeOffer offer = getSelected();
        Item defaultItem = inputItem.getItem();
        if (stackMatchesOfferResource(offer, inputItem))
            defaultItem = offer.getItem();

        ItemStack counted = tradeContainer.getItem(STACK_SLOT);
        if (!counted.isEmpty() && !counted.is(defaultItem)) return;

        int held = counted.isEmpty() ? 0 : counted.getCount();
        int drained = Math.min(inputItem.getCount() / unit, MAX_SYNC_COUNT - held);
        if (drained <= 0) return;

        int remainder = inputItem.getCount() - drained * unit;

        updatingOutput = true;
        try {
            tradeContainer.setItem(STACK_SLOT, new ItemStack(defaultItem, held + drained));
            tradeContainer.setItem(INPUT_SLOT,
                remainder > 0 ? new ItemStack(inputItem.getItem(), remainder) : ItemStack.EMPTY);
        } finally {
            updatingOutput = false;
        }
    }

    //Shift click moves items into the partial input slots which accepts partial stacks
    //Once the stack reaches a full stack, it is moved over to the stack slot
    private boolean mergeIntoPartialInputSlot(ItemStack incoming)
    {
        if (incoming.isEmpty()) return false;
        MayorTradeOffer offer = getSelected();
        if(offer==null) return false;
        if(incoming.getItem() == ModConfig.getInstance().getCurrencyItem() ) {
            //good
        }
        else if(!stackMatchesOfferResource(offer, incoming)) {
            return false;
        }

        ItemStack inputSlotStack = tradeContainer.getItem(INPUT_SLOT);
        int stackSize = stackUnit(incoming.getItem());

        if (inputSlotStack.isEmpty()) {
            int move = Math.min(incoming.getCount(), stackSize);
            tradeContainer.setItem(INPUT_SLOT, incoming.split(move));
            return true;
        }

        int room = stackSize - inputSlotStack.getCount();
        if (room <= 0) return false;

        int move = Math.min(room, incoming.getCount());
        inputSlotStack.grow(move);
        incoming.shrink(move);
        tradeContainer.setChanged();
        return true;
    }

    /**
     * Cancels the Mayor trade with the player shift clicks the stack or switches offers
     */
    private void cancelTrade(Player p)
    {
        updatingOutput = true;
        try {
            ItemStack loose = tradeContainer.getItem(INPUT_SLOT);
            if (!loose.isEmpty()) givePlayer(p, loose.getItem(), loose.getCount());

            ItemStack counted = tradeContainer.getItem(STACK_SLOT);
            if (!counted.isEmpty())
                givePlayer(p, counted.getItem(), counted.getCount() * stackUnit(counted.getItem()));

            tradeContainer.setItem(INPUT_SLOT, ItemStack.EMPTY);
            tradeContainer.setItem(STACK_SLOT, ItemStack.EMPTY);
        } finally {
            updatingOutput = false;
        }
        clearOutput();
    }

    private void setInputRaw(@Nullable Item item, int rawCount)
    {
        updatingOutput = true;
        try {
            if (item == null || rawCount <= 0) {
                tradeContainer.setItem(INPUT_SLOT, ItemStack.EMPTY);
                tradeContainer.setItem(STACK_SLOT, ItemStack.EMPTY);
            } else {
                int unit = stackUnit(item);
                int stacks = Math.min(rawCount / unit, MAX_SYNC_COUNT);
                int remainder = rawCount - stacks * unit;
                tradeContainer.setItem(STACK_SLOT, stacks > 0 ? new ItemStack(item, stacks) : ItemStack.EMPTY);
                tradeContainer.setItem(INPUT_SLOT, remainder > 0 ? new ItemStack(item, remainder) : ItemStack.EMPTY);
            }
        } finally {
            updatingOutput = false;
        }
        clearOutput();
    }

    private void consumeInputs(int leftOverRaw) {
        setInputRaw(heldInputItem(), leftOverRaw);
    }

    private boolean moveEntireStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection)
    {
        boolean moved = false;
        while (!stack.isEmpty()) {
            if (!this.moveItemStackTo(stack, startIndex, endIndex, reverseDirection)) break;
            moved = true;
        }
        return moved;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack result = ItemStack.EMPTY;
        if(this.graphView) return ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        final int invStart = SLOT_COUNT;
        final int invEnd = this.slots.size();

        if (index == OUTPUT_SLOT) {
            claimOutput(player);
            return ItemStack.EMPTY;
        } else if (index == STACK_SLOT) {
            cancelTrade(player);
            return ItemStack.EMPTY;
        } else if (index < invStart) {
            if (!moveEntireStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            if (!mergeIntoPartialInputSlot(stack)) return ItemStack.EMPTY;
            normalizeInput();
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
            ItemStack loose = tradeContainer.getItem(INPUT_SLOT);
            if (!loose.isEmpty()) player.getInventory().placeItemBackInInventory(loose);

            //The counter holds N stacks; hand them back as real stacks
            ItemStack counted = tradeContainer.getItem(STACK_SLOT);
            if (!counted.isEmpty()) {
                int unit = stackUnit(counted.getItem());
                for (int i = 0; i < counted.getCount(); i++)
                    player.getInventory().placeItemBackInInventory(new ItemStack(counted.getItem(), unit));
            }

            tradeContainer.setItem(INPUT_SLOT, ItemStack.EMPTY);
            tradeContainer.setItem(STACK_SLOT, ItemStack.EMPTY);
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


    /** Loose input: accepts one vanilla stack at a time, drained into the counter **/
    private class InputSlot extends Slot {
        InputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !MayorTradeMenu.this.graphView;
        }
    }

    /**
     * Counter slot: its stack count is a number of full stacks, not of items. Nothing
     * can be placed or taken here directly; it fills as the loose slot drains and is
     * handed back as real stacks when the menu closes.
     */
    private class StackCounterSlot extends Slot {
        StackCounterSlot(Container container, int index, int x, int y) {
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
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return MAX_SYNC_COUNT;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return MAX_SYNC_COUNT;
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

        /** Claimed through the menu button so the payout never becomes a carried stack **/
        @Override
        public boolean mayPickup(Player player) {
            return false;
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
