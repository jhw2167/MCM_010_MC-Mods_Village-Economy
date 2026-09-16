package com.holybuckets.villageecon.menu;

import com.holybuckets.villageecon.core.model.Mayor;
import net.blay09.mods.balm.api.menu.BalmMenuProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nullable;
import java.util.List;

public class MayorMenuProvider implements BalmMenuProvider {

    private final Mayor mayor;

    public MayorMenuProvider(Mayor mayor) {
        this.mayor = mayor;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.hbs_village_econ.mayor_trade");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new MayorTradeMenu(syncId, inventory, mayor, MayorTradeMenu.buildOffers(mayor));
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        List<MayorTradeOffer> offers = MayorTradeMenu.buildOffers(mayor);
        MayorTradeMenu.writeOffers(buf, offers);
    }
}
