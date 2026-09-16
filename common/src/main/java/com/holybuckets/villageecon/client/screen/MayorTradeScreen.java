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

    private static final int COLOR_TEXT = 0x404040;
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

    private void renderCount(GuiGraphics gui, ItemStack stack, int x, int y, int count) {
        if (stack.isEmpty()) return;
        gui.renderFakeItem(stack, x, y);
        gui.renderItemDecorations(this.font, stack, x, y, String.valueOf(count));
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

    private void renderGraph(GuiGraphics gui)
    {
        int x = this.leftPos + MayorTradeMenu.GRAPH_X;
        int y = this.topPos + MayorTradeMenu.INV_Y;
        int w = MayorTradeMenu.GRAPH_WIDTH;
        int h = MayorTradeMenu.GRAPH_HEIGHT;

        MayorTradeOffer offer = this.menu.getSelected();
        if (offer == null) return;

        float rate = MarketSalesCache.getRate(offer.getResourceId(), offer.getMarketRate());
        List<Integer> sales = MarketSalesCache.getSales(offer.getResourceId());

        int midY = y + h / 2;

        if (sales.isEmpty()) return;

        float maxDeviation = 1f;
        for (Integer price : sales)
            maxDeviation = Math.max(maxDeviation, Math.abs(price - rate));

        int half = (h / 2) - 3;
        int count = sales.size();
        int stepDenominator = Math.max(1, count - 1);

        int prevX = -1;
        int prevY = -1;
        for (int i = 0; i < count; i++)
        {
            int price = sales.get(i);
            int px = x + 2 + (w - 4) * i / stepDenominator;
            int py = midY - Math.round(((price - rate) / maxDeviation) * half);
            py = Math.max(y + 1, Math.min(y + h - 2, py));

            if (prevX >= 0) drawLine(gui, prevX, prevY, px, py, COLOR_PLOT);

            int color = price >= rate ? COLOR_PLOT : COLOR_PLOT_LOW;
            gui.fill(px - 1, py - 1, px + 2, py + 2, color);

            prevX = px;
            prevY = py;
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
        gui.drawString(this.font, this.title, MayorTradeMenu.TRADE_LIST_X + 12, 6, COLOR_TEXT, false);
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
