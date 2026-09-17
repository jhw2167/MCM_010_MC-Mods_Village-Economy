package com.holybuckets.villageecon.menu;

import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.model.EconomyResource;
import com.holybuckets.villageecon.core.MarketState;
import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.ResourceLedger;
import com.holybuckets.villageecon.entity.MayorEntity;
import net.minecraft.network.FriendlyByteBuf;
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

    private static final int INPUT_SLOTS = 1;
    private static final int OUTPUT_SLOT = 1;

    private final Container tradeContainer;
    private final List<MayorTradeOffer> offers = new ArrayList<>();
    private final Player player;

    @Nullable
    private final Mayor mayor;

    private int selectedOffer = 0;
    private boolean graphView = true;
    private boolean updatingOutput = false;

    public MayorTradeMenu(int syncId, Inventory playerInventory, @Nullable Mayor mayor, List<MayorTradeOffer> offers)
    {
        super(ModMenus.mayorTradeMenu.get(), syncId);
        this.player = playerInventory.player;
        this.mayor = mayor;
        if (offers != null) this.offers.addAll(offers);

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
        return new MayorTradeMenu(syncId, playerInventory, null, offers);
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

        ResourceLedger ledger = mayor.getStaticLedger();
        int level = mayor.getVillageLevel();

        for (EconomyResource resource : mayor.getActiveResources())
        {
            String id = resource.getResourceId();
            int quota = 2 * resource.productionAt(level + 1);
            offers.add(new MayorTradeOffer(id, resource.getItem(),
                ledger.get(id), quota, MarketState.marketRate(id)));
        }
        return offers;
    }


    public List<MayorTradeOffer> getOffers() { return offers; }

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

    private void computeOutput()
    {
        MayorTradeOffer offer = getSelected();
        if (offer == null) {
            tradeContainer.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
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
            else if (offer.getItem() != null && stack.is(offer.getItem()))
                resourceIn += stack.getCount()/64;
        }

        if (resourceIn > 0 && offer.getItem() != null) {
            int payout = (int) Math.floor(resourceIn * rate);
            tradeContainer.setItem(OUTPUT_SLOT, payout > 0 ? new ItemStack(currency, payout) : ItemStack.EMPTY);
            return;
        }

        if (currencyIn > 0 && offer.getItem() != null) {
            int available = offer.getLedgerAmount();
            int affordable = (int) Math.floor(currencyIn / rate);
            int amount = Math.min(available, affordable);
            tradeContainer.setItem(OUTPUT_SLOT, amount > 0 ? new ItemStack(offer.getItem(), amount*64) : ItemStack.EMPTY);
            return;
        }

        tradeContainer.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
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
        ResourceLedger ledger = mayor.getStaticLedger();

        int leftover = 0;
        if (taken.is(currency)) {
            int resourceIn = countInput(offer.getItem())/64;
            leftover =  tradeContainer.getItem(0).getCount() % 64;
            ledger.add(offer.getResourceId(), resourceIn);
            ledger.addCurrency(-taken.getCount());
        } else if (offer.getItem() != null && taken.is(offer.getItem())) {
            int currencyIn = countInput(currency);
            ledger.remove(offer.getResourceId(), taken.getCount());
            ledger.addCurrency(currencyIn);
        }

        consumeInputs(leftover);
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

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int invStart = 3;
        int invEnd = this.slots.size();

        if (index < invStart) {
            if (!this.moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, result);
        } else {
            if (!this.moveItemStackTo(stack, 0, INPUT_SLOTS, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
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
