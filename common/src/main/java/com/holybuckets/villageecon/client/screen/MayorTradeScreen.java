package com.holybuckets.villageecon.client.screen;

import com.holybuckets.villageecon.Constants;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.menu.MayorTradeMenu;
import com.holybuckets.villageecon.menu.MayorTradeOffer;
import com.holybuckets.villageecon.networking.MarketSalesCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MayorTradeScreen extends AbstractContainerScreen<MayorTradeMenu> {

    private static final ResourceLocation TEXTURE_GRAPH =
        new ResourceLocation(Constants.MOD_ID, "textures/gui/trade_screen_graph_gui.png");
    private static final ResourceLocation TEXTURE_INV =
        new ResourceLocation(Constants.MOD_ID, "textures/gui/trade_screen_inv_gui.png");
    private static final ResourceLocation VILLAGER_LOCATION =
        new ResourceLocation("textures/gui/container/villager2.png");

    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int ARROW_U = 15;
    private static final int ARROW_V = 171;
    private static final int ARROW_WIDTH = 10;
    private static final int ARROW_HEIGHT = 9;

    private static final int OFFER_LEDGER_X = 3;
    private static final int OFFER_ARROW_X = 21;
    private static final int OFFER_QUOTA_X = 34;
    private static final int OFFER_RATE_X = 66;
    private static final int OFFER_ITEM_Y = 2;
    private static final int OFFER_ARROW_Y = 6;

    private static final int BANNER_Y = 6;
    private static final int BANNER_MARGIN = 8;
    private static final int BANNER_ICON_SIZE = 16;
    /** Lifts the 16px item icon so its centre lines up with the 8px text baseline **/
    private static final int BANNER_ICON_OFFSET = 4;

    private static final int GRAPH_SALE_WINDOW = 16;
    private static final int GRAPH_PADDING = 3;
    /** Item sprites are 16px; markers are drawn at half scale so 16 fit across the graph **/
    private static final float MARKER_SCALE = 0.5f;
    private static final int MARKER_SIZE = (int) (16 * MARKER_SCALE);

    private static final int LEDGER_Y = 78;
    private static final int LEDGER_COLUMNS = 3;
    private static final int LEDGER_ROW_HEIGHT = 18;

    private static final int COLOR_GAIN = 0x2E8B2E;
    private static final int COLOR_LOSS = 0xB03030;
    private static final int COLOR_NEUTRAL = 0x808080;

    private static final int COLOR_TEXT = 0x404040;
    /** Legacy formatting prefix; stack counts are drawn gold to mark them as stacks, not items **/
    private static final String STACK_COUNT_PREFIX = "\u00A76";
    private static final int COLOR_ROW_SELECTED = 0xFFFFFFA0;
    private static final int COLOR_PLOT = 0xFF4CE04C;
    private static final int COLOR_PLOT_LOW = 0xFFE04C4C;
    private static final int COLOR_SCROLL = 0xFFC6C6C6;

    private final TradeOfferButton[] tradeOfferButtons = new TradeOfferButton[MayorTradeMenu.VISIBLE_ROWS];
    private Button toggleButton;
    private int scrollOffset = 0;

    public MayorTradeScreen(MayorTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = MayorTradeMenu.GUI_WIDTH;
        this.imageHeight = MayorTradeMenu.GUI_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 200;
    }

    @Override
    protected void init() {
        super.init();

        int rowY = this.topPos + MayorTradeMenu.TRADE_LIST_Y;
        for (int i = 0; i < this.tradeOfferButtons.length; i++) {
            this.tradeOfferButtons[i] = this.addRenderableWidget(new TradeOfferButton(
                this.leftPos + MayorTradeMenu.TRADE_LIST_X, rowY, i, b -> {
                    if (b instanceof TradeOfferButton offerButton) selectOffer(offerButton.getIndex() + scrollOffset);
                }));
            rowY += MayorTradeMenu.TRADE_ROW_HEIGHT;
        }

        this.toggleButton = Button.builder(toggleLabel(), b -> toggleView())
            .bounds(this.leftPos + MayorTradeMenu.TOGGLE_X, this.topPos + MayorTradeMenu.TOGGLE_Y,
                MayorTradeMenu.TOGGLE_WIDTH, MayorTradeMenu.TOGGLE_HEIGHT)
            .build();
        this.addRenderableWidget(this.toggleButton);
    }

    private void selectOffer(int index) {
        if (index < 0 || index >= this.menu.getOffers().size()) return;
        this.menu.clickMenuButton(this.minecraft.player, index);
        sendButton(index);
    }

    private Component toggleLabel() {
        return this.menu.isGraphView()
            ? Component.translatable("screen.hbs_village_econ.inventory")
            : Component.translatable("screen.hbs_village_econ.graph");
    }

    private void toggleView() {
        this.menu.setGraphView(!this.menu.isGraphView());
        sendButton(MayorTradeMenu.BUTTON_TOGGLE_VIEW);
        this.toggleButton.setMessage(toggleLabel());
    }

    private void sendButton(int id) {
        if (this.minecraft == null || this.minecraft.gameMode == null) return;
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        ResourceLocation texture = this.menu.isGraphView() ? TEXTURE_GRAPH : TEXTURE_INV;
        gui.blit(texture, this.leftPos, this.topPos, 0, 0.0F, 0.0F,
            this.imageWidth, this.imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        renderScrollbar(gui);
    }

    private void renderOffers(GuiGraphics gui)
    {
        List<MayorTradeOffer> offers = this.menu.getOffers();
        if (offers.isEmpty()) return;

        ItemStack currency = new ItemStack(ModConfig.getInstance().getCurrencyItem());
        int rows = Math.min(this.tradeOfferButtons.length, Math.max(0, offers.size() - scrollOffset));

        for (int i = 0; i < rows; i++)
        {
            int index = i + scrollOffset;
            MayorTradeOffer offer = offers.get(index);

            int x = this.leftPos + MayorTradeMenu.TRADE_LIST_X;
            int y = this.topPos + MayorTradeMenu.TRADE_LIST_Y + i * MayorTradeMenu.TRADE_ROW_HEIGHT;

            gui.pose().pushPose();
            gui.pose().translate(0.0F, 0.0F, 100.0F);

            if (index == this.menu.getSelectedOffer()) renderSelectionOutline(gui, x, y);

            renderCount(gui, offer.getIcon(), x + OFFER_LEDGER_X, y + OFFER_ITEM_Y, offer.getLedgerAmount());

            RenderSystem.enableBlend();
            gui.blit(VILLAGER_LOCATION, x + OFFER_ARROW_X, y + OFFER_ARROW_Y, 0,
                ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

            renderCount(gui, offer.getIcon(), x + OFFER_QUOTA_X, y + OFFER_ITEM_Y, offer.getQuotaAmount());
            renderCount(gui, currency, x + OFFER_RATE_X, y + OFFER_ITEM_Y, Math.round(offer.getMarketRate()));

            gui.pose().popPose();
        }
    }

    /**
     * Cycle ledger: what this village has traded away or taken on this cycle, laid out in
     * three columns of item sprite plus signed delta. Currency leads, then each produced
     * resource; resources the village cannot produce at its level are omitted.
     */
    private void renderCycleLedger(GuiGraphics gui)
    {
        int originX = this.leftPos + MayorTradeMenu.GRAPH_X;
        int originY = this.topPos + LEDGER_Y;
        int columnWidth = MayorTradeMenu.GRAPH_WIDTH / LEDGER_COLUMNS;

        int slot = 0;
        slot = renderLedgerEntry(gui, new ItemStack(ModConfig.getInstance().getCurrencyItem()),
            Math.round(this.menu.getCurrencyDelta()), originX, originY, columnWidth, slot);

        for (MayorTradeOffer offer : this.menu.getOffers()) {
            if (!offer.producesResource()) continue;
            slot = renderLedgerEntry(gui, offer.getIcon(), offer.getCycleDelta(),
                originX, originY, columnWidth, slot);
        }
    }

    private int renderLedgerEntry(GuiGraphics gui, ItemStack icon, int delta,
        int originX, int originY, int columnWidth, int slot)
    {
        if (icon.isEmpty()) return slot;

        int x = originX + (slot % LEDGER_COLUMNS) * columnWidth;
        int y = originY + (slot / LEDGER_COLUMNS) * LEDGER_ROW_HEIGHT;

        gui.pose().pushPose();
        gui.pose().translate(0.0F, 0.0F, 100.0F);
        gui.renderFakeItem(icon, x, y);
        gui.pose().popPose();

        int color = (delta > 0) ? COLOR_GAIN : (delta < 0) ? COLOR_LOSS : COLOR_NEUTRAL;
        String label = (delta > 0 ? "+" : "") + delta;
        gui.drawString(this.font, label, x + 18, y + 4, color, false);

        return slot + 1;
    }

    private void renderCount(GuiGraphics gui, ItemStack stack, int x, int y, int count) {
        if (stack.isEmpty()) return;
        gui.renderFakeItem(stack, x, y);
        gui.renderItemDecorations(this.font, stack, x, y, STACK_COUNT_PREFIX + count);
    }

    private void renderSelectionOutline(GuiGraphics gui, int x, int y) {
        int w = MayorTradeMenu.TRADE_LIST_WIDTH;
        int h = MayorTradeMenu.TRADE_ROW_HEIGHT;
        gui.fill(x, y, x + w, y + 1, COLOR_ROW_SELECTED);
        gui.fill(x, y + h - 1, x + w, y + h, COLOR_ROW_SELECTED);
        gui.fill(x, y, x + 1, y + h, COLOR_ROW_SELECTED);
        gui.fill(x + w - 1, y, x + w, y + h, COLOR_ROW_SELECTED);
    }

    private void renderScrollbar(GuiGraphics gui)
    {
        int total = this.menu.getOffers().size();
        if (total <= MayorTradeMenu.VISIBLE_ROWS) return;

        int trackX = this.leftPos + MayorTradeMenu.SCROLLBAR_X;
        int trackY = this.topPos + MayorTradeMenu.SCROLLBAR_Y;
        int handleHeight = Math.max(10, MayorTradeMenu.SCROLLBAR_HEIGHT * MayorTradeMenu.VISIBLE_ROWS / total);
        int maxScroll = total - MayorTradeMenu.VISIBLE_ROWS;
        int travel = MayorTradeMenu.SCROLLBAR_HEIGHT - handleHeight;
        int handleY = trackY + (maxScroll == 0 ? 0 : travel * scrollOffset / maxScroll);

        gui.fill(trackX, handleY, trackX + MayorTradeMenu.SCROLLBAR_WIDTH, handleY + handleHeight, COLOR_SCROLL);
    }

    /**
     * Plots the last GRAPH_SALE_WINDOW global sales of the selected resource as small dots,
     * chronological left to right. The horizontal centre line is the current market rate D;
     * the vertical scale is the largest deviation from D across the window, so the extreme
     * sale sits GRAPH_PADDING pixels inside the top or bottom edge.
     */
    private void renderGraph(GuiGraphics gui)
    {
        MayorTradeOffer offer = this.menu.getSelected();
        if (offer == null) return;

        int x = this.leftPos + MayorTradeMenu.GRAPH_X;
        int y = this.topPos + MayorTradeMenu.INV_Y;
        int w = MayorTradeMenu.GRAPH_WIDTH;
        int h = MayorTradeMenu.GRAPH_HEIGHT;
        int midY = y + h / 2;

        float rate = MarketSalesCache.getRate(offer.getResourceId(), offer.getMarketRate());
        List<Integer> all = MarketSalesCache.getSales(offer.getResourceId());
        if (all.isEmpty()) return;

        List<Integer> sales = all.subList(Math.max(0, all.size() - GRAPH_SALE_WINDOW), all.size());

        //Symmetric about D so the centre line stays the market rate
        float halfRange = 1f;
        for (Integer price : sales)
            halfRange = Math.max(halfRange, Math.abs(price - rate));

        int half = (h / 2) - GRAPH_PADDING - MARKER_SIZE / 2;
        int count = sales.size();
        int stepDenominator = Math.max(1, count - 1);
        int span = w - 2 * GRAPH_PADDING - MARKER_SIZE;

        ItemStack marker = new ItemStack(ModConfig.getInstance().getGraphMarkerItem());

        for (int i = 0; i < count; i++)
        {
            int price = sales.get(i);
            int px = x + GRAPH_PADDING + (count == 1 ? span / 2 : span * i / stepDenominator);
            int py = midY - Math.round(((price - rate) / halfRange) * half) - MARKER_SIZE / 2;

            gui.pose().pushPose();
            gui.pose().translate(px, py, 200.0F);
            gui.pose().scale(MARKER_SCALE, MARKER_SCALE, 1.0F);
            gui.renderFakeItem(marker, 0, 0);
            gui.pose().popPose();
        }

        String rateLabel = String.valueOf(Math.round(rate));
        gui.drawString(this.font, rateLabel, x + w - 4 - this.font.width(rateLabel), midY - 10, COLOR_TEXT, false);
    }

    private void drawLine(GuiGraphics gui, int x1, int y1, int x2, int y2, int color)
    {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int cx = x1;
        int cy = y1;
        int guard = 0;

        while (guard++ < 512) {
            gui.fill(cx, cy, cx + 1, cy + 1, color);
            if (cx == x2 && cy == y2) break;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; cx += sx; }
            if (e2 < dx) { err += dx; cy += sy; }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(this.font, this.title, MayorTradeMenu.TRADE_LIST_X + 12, BANNER_Y, COLOR_TEXT, false);

        Component reserve = Component.translatable("screen.hbs_village_econ.village_reserve",
            Math.round(this.menu.getReserveCurrency()));

        //Currency icon sits flush against the right edge, the amount immediately left of it
        ItemStack currency = new ItemStack(ModConfig.getInstance().getCurrencyItem());
        int iconX = this.imageWidth - BANNER_MARGIN - BANNER_ICON_SIZE;
        int textX = iconX - 2 - this.font.width(reserve);

        gui.drawString(this.font, reserve, textX, BANNER_Y, COLOR_TEXT, false);

        gui.pose().pushPose();
        gui.pose().translate(0.0F, 0.0F, 100.0F);
        gui.renderFakeItem(currency, iconX, BANNER_Y - BANNER_ICON_OFFSET);
        gui.pose().popPose();

        //Village name centred between the title and the reserve readout
        String village = this.menu.getVillageName();
        if (village != null && !village.isBlank()) {
            int centre = (MayorTradeMenu.TRADE_LIST_X + 12 + this.font.width(this.title) + textX) / 2;
            gui.drawString(this.font, village,
                centre - this.font.width(village) / 2, BANNER_Y, COLOR_TEXT, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        int total = this.menu.getOffers().size();
        int maxScroll = Math.max(0, total - MayorTradeMenu.VISIBLE_ROWS);
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);

        renderOffers(gui);
        if (this.menu.isGraphView()) {
            renderGraph(gui);
            renderCycleLedger(gui);
        }

        for (TradeOfferButton button : this.tradeOfferButtons) {
            if (button == null) continue;
            if (button.isHoveredOrFocused()) button.renderToolTip(gui, mouseX, mouseY);
            button.visible = button.getIndex() + scrollOffset < this.menu.getOffers().size();
        }

        RenderSystem.enableDepthTest();
        this.renderTooltip(gui, mouseX, mouseY);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.toggleButton != null) this.toggleButton.setMessage(toggleLabel());
    }

    class TradeOfferButton extends Button {

        private final int index;

        TradeOfferButton(int x, int y, int index, Button.OnPress onPress) {
            super(x, y, MayorTradeMenu.TRADE_LIST_WIDTH, MayorTradeMenu.TRADE_ROW_HEIGHT,
                CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
            this.index = index;
            this.visible = false;
        }

        public int getIndex() {
            return this.index;
        }

        public void renderToolTip(GuiGraphics gui, int mouseX, int mouseY) {
            List<MayorTradeOffer> offers = MayorTradeScreen.this.menu.getOffers();
            int offerIndex = this.index + MayorTradeScreen.this.scrollOffset;
            if (!this.isHovered || offerIndex >= offers.size()) return;

            MayorTradeOffer offer = offers.get(offerIndex);
            if (mouseX >= this.getX() + OFFER_RATE_X)
                gui.renderTooltip(MayorTradeScreen.this.font,
                    new ItemStack(ModConfig.getInstance().getCurrencyItem()), mouseX, mouseY);
            else
                gui.renderTooltip(MayorTradeScreen.this.font, offer.getIcon(), mouseX, mouseY);
        }
    }
}
