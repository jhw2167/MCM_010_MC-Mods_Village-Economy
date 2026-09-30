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

import java.util.ArrayList;
import java.util.List;

//Code mostly duplicated from VillagerScreen with a toggle feature
// that changes view from resource graph and trade ledger overview
// and the natural trade screen
//
//Includes list of resource modules akin to vanilla villager trades.
//Includes top banner with village name and total currency
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
    private static final int BANNER_MARGIN = 10;
    private static final int BANNER_ICON_SZ = 16;

    private static final int BANNER_ICON_OFFSET = 4;

    private static final int GRAPH_WINDOW = 12;
    private static final int GRAPH_PADDING = 3;

    //Scales down items sprites to use as graph dots
    private static final float MARKER_SCALE = 0.5f;
    private static final int MARKER_SIZE = (int) (16 * MARKER_SCALE);

    private static final int LEDGER_Y = 90;
    private static final int LEDGER_COLUMNS = 3;
    private static final int LEDGER_ROW_HEIGHT = 18;
    private static final int LEDGER_ROWS = 3;
    private static final int LEDGER_CAPACITY = LEDGER_COLUMNS * LEDGER_ROWS;

    private static final int COLOR_GAIN = 0x2E8B2E; //green
    private static final int COLOR_LOSS = 0xB03030; //red
    private static final int COLOR_NEUTRAL = 0x808080; //grey

    private static final int COLOR_PLOT_LINE = 0xFF505050;  //grey
    private static final int COLOR_NEGATIVE = 0xB03030;
    private static final int COLOR_TEXT = 0x404040; //black

    private static final String STACK_COUNT_PREFIX = "\u00A76";
    private static final int COLOR_ROW_SELECTED = 0xFFFFFFA0;
    private static final int COLOR_SCROLL = 0xFFC6C6C6;

    private final TradeOfferButton[] tradeOfferButtons = new TradeOfferButton[MayorTradeMenu.VISIBLE_ROWS];
    private Button toggleButton;
    private int scrollOffset = 0;

    private final List<int[]> markerHits = new ArrayList<>();
    private int reserveHitX, reserveHitY, reserveHitW;

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

    //Drop down list of trades offered by the villager. In this system they are the
    //items that the villages trades for against the player and on the market
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

            renderCount(gui, offer.getIcon(), x + OFFER_LEDGER_X, y + OFFER_ITEM_Y, offer.getLedgerAmount(), true);

            RenderSystem.enableBlend();
            gui.blit(VILLAGER_LOCATION, x + OFFER_ARROW_X, y + OFFER_ARROW_Y, 0,
                ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

            renderCount(gui, offer.getIcon(), x + OFFER_QUOTA_X, y + OFFER_ITEM_Y, offer.getQuotaAmount(), true);
            renderCount(gui, currency, x + OFFER_RATE_X, y + OFFER_ITEM_Y, Math.round(offer.getMarketRate()), false);

            gui.pose().popPose();
        }
    }

    private void renderCycleLedger(GuiGraphics gui)
    {
        int originX = this.leftPos + MayorTradeMenu.GRAPH_X;
        int originY = this.topPos + LEDGER_Y;
        int columnWidth = (MayorTradeMenu.GRAPH_WIDTH / LEDGER_COLUMNS)+6;

        int slot = 0;
        //Indivual ledger +/- next to item icon. 3x3 == 9 maxium listed under the graph
        slot = renderLedgerEntry(gui, new ItemStack(ModConfig.getInstance().getCurrencyItem()),
            Math.round(this.menu.getCurrencyDelta()), originX, originY, columnWidth, slot);

        for (MayorTradeOffer offer : this.menu.getOffers()) {
            if (slot >= LEDGER_CAPACITY) break;
            if (!offer.producesResource()) continue;
            slot = renderLedgerEntry(gui, offer.getIcon(), offer.getCycleDelta(),
                originX, originY, columnWidth, slot);
        }
    }

    //Indivual ledger +/- next to item icon. 3x3 == 9 maxium listed under the graph
    private int renderLedgerEntry(GuiGraphics gui, ItemStack icon, int delta,
        int originX, int originY, int columnWidth, int slot)
    {
        if (icon.isEmpty() || slot >= LEDGER_CAPACITY) return slot;

        int x = originX + (slot % LEDGER_COLUMNS) * columnWidth;
        int y = originY + (slot / LEDGER_COLUMNS) * LEDGER_ROW_HEIGHT;

        gui.pose().pushPose();
        gui.pose().translate(0.0F, 0.0F, 100.0F);
        gui.renderFakeItem(icon, x, y);
        gui.pose().popPose();

        int color = (delta > 0) ? COLOR_GAIN : (delta < 0) ? COLOR_LOSS : COLOR_NEUTRAL;
        String label = (delta > 0 ? "+" : "") + delta;
        gui.drawString(this.font, label, x + 18, y + 6, color, false);

        return slot + 1;
    }

    /**
     * @param stacks true when the number counts stacks rather than items, drawn gold to
     *               distinguish it from an ordinary white item count
     */
    private void renderCount(GuiGraphics gui, ItemStack stack, int x, int y, int count, boolean stacks) {
        if (stack.isEmpty()) return;
        gui.renderFakeItem(stack, x, y);
        gui.renderItemDecorations(this.font, stack, x, y,
            (stacks ? STACK_COUNT_PREFIX : "") + count);
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
        markerHits.clear();

        MayorTradeOffer offer = this.menu.getSelected();
        if (offer == null) return;

        int x = this.leftPos + MayorTradeMenu.GRAPH_X;
        int y = this.topPos + MayorTradeMenu.GRAPH_Y;
        int w = MayorTradeMenu.GRAPH_WIDTH;
        int h = MayorTradeMenu.GRAPH_HEIGHT;
        int midY = y + h / 2;

        float rate = MarketSalesCache.getRate(offer.getResourceId(), offer.getMarketRate());
        List<Integer> all = MarketSalesCache.getSales(offer.getResourceId());
        List<Integer> allQty = MarketSalesCache.getQuantities(offer.getResourceId());
        if (all.isEmpty()) return;

        int from = Math.max(0, all.size() - GRAPH_WINDOW);
        List<Integer> sales = all.subList(from, all.size());

        float halfRange = 1f;
        for (Integer price : sales)
            halfRange = Math.max(halfRange, Math.abs(price - rate));

        int half = (h / 2) - GRAPH_PADDING - MARKER_SIZE / 2;
        int count = sales.size();
        int stepDenominator = Math.max(1, count - 1);
        int span = w - 2 * GRAPH_PADDING - MARKER_SIZE;

        int[] px = new int[count];
        int[] py = new int[count];
        for (int i = 0; i < count; i++) {
            px[i] = x + GRAPH_PADDING + (count == 1 ? span / 2 : span * i / stepDenominator);
            py[i] = midY - Math.round(((sales.get(i) - rate) / halfRange) * half) - MARKER_SIZE / 2;
        }

        for (int i = 1; i < count; i++) {
            drawLine(gui, px[i - 1] + MARKER_SIZE / 2, py[i - 1] + MARKER_SIZE / 2,
                px[i] + MARKER_SIZE / 2, py[i] + MARKER_SIZE / 2, COLOR_PLOT_LINE);
        }

        ItemStack marker = offer.getItem().getDefaultInstance();

        for (int i = 0; i < count; i++)
        {
            gui.pose().pushPose();
            gui.pose().translate(px[i], py[i], 200.0F);
            gui.pose().scale(MARKER_SCALE, MARKER_SCALE, 1.0F);
            gui.renderFakeItem(marker, 0, 0);
            gui.pose().popPose();

            int index = from + i;
            int quantity = (index < allQty.size()) ? allQty.get(index) : 1;
            markerHits.add(new int[]{ px[i], py[i], sales.get(i), quantity });
        }

        String rateLabel = String.valueOf(Math.round(rate));
        gui.drawString(this.font, rateLabel, x + w - 4 - this.font.width(rateLabel), midY - 10, COLOR_TEXT, false);
    }

    /** Line between two points, used for connecting graph points **/
    private void drawLine(GuiGraphics gui, int x1, int y1, int x2, int y2, int color)
    {
        int dx = Math.abs(x2 - x1);
        int dy = -Math.abs(y2 - y1);
        int sx = (x1 < x2) ? 1 : -1;
        int sy = (y1 < y2) ? 1 : -1;
        int err = dx + dy;

        while (true) {
            gui.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x1 += sx; }
            if (e2 <= dx) { err += dx; y1 += sy; }
        }
    }

    private void renderGraphTooltips(GuiGraphics gui, int mouseX, int mouseY)
    {
        for (int[] hit : markerHits) {
            if (mouseX < hit[0] || mouseX > hit[0] + MARKER_SIZE) continue;
            if (mouseY < hit[1] || mouseY > hit[1] + MARKER_SIZE) continue;

            gui.renderComponentTooltip(this.font, List.of(
                Component.translatable("screen.hbs_village_econ.sale_price", hit[2]),
                Component.translatable("screen.hbs_village_econ.sale_quantity", hit[3])
            ), mouseX, mouseY);
            return;
        }
    }

    private void renderReserveTooltip(GuiGraphics gui, int mouseX, int mouseY)
    {
        if (mouseX < reserveHitX || mouseX > reserveHitX + reserveHitW) return;
        if (mouseY < reserveHitY || mouseY > reserveHitY + 10) return;

        gui.renderComponentTooltip(this.font, List.of(
            Component.translatable("screen.hbs_village_econ.stored", Math.round(this.menu.getReserveCurrency())),
            Component.translatable("screen.hbs_village_econ.projected", Math.round(this.menu.getProjectedCurrency()))
        ), mouseX, mouseY);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        //Sends message to server indicating player finalized trade
        if (!this.menu.isGraphView() && this.menu.getOutputAmount() > 0
            && this.isHovering(MayorTradeMenu.OUTPUT_X, MayorTradeMenu.OUTPUT_Y, 16, 16, mouseX, mouseY)) {
            sendButton(MayorTradeMenu.BUTTON_CLAIM_OUTPUT);
            return true;
        }

        if (!this.menu.isGraphView() && hasShiftDown()
            && this.isHovering(MayorTradeMenu.INPUT_B_X, MayorTradeMenu.INPUT_B_Y, 16, 16, mouseX, mouseY)) {
            sendButton(MayorTradeMenu.BUTTON_CANCEL_TRADE);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(this.font, this.title, MayorTradeMenu.TRADE_LIST_X + 12, BANNER_Y, COLOR_TEXT, false);

        Component reserve = Component.translatable("screen.hbs_village_econ.village_reserve",
            Math.round(this.menu.getProjectedCurrency()));

        //Currency icon sits flush against the right edge, the amount immediately left of it
        ItemStack currency = new ItemStack(ModConfig.getInstance().getCurrencyItem());
        int iconX = this.imageWidth - BANNER_MARGIN - BANNER_ICON_SZ;
        int textX = iconX - 2 - this.font.width(reserve);

        int reserveColor = (this.menu.getProjectedCurrency() < 0f) ? COLOR_NEGATIVE : COLOR_TEXT;
        gui.drawString(this.font, reserve, textX, BANNER_Y, reserveColor, false);

        reserveHitX = this.leftPos + textX;
        reserveHitY = this.topPos + BANNER_Y;
        reserveHitW = (iconX + BANNER_ICON_SZ) - textX;

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

        if (this.menu.isGraphView()) renderGraphTooltips(gui, mouseX, mouseY);
        renderReserveTooltip(gui, mouseX, mouseY);
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
