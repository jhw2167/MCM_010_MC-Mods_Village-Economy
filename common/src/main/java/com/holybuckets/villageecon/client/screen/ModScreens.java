package com.holybuckets.villageecon.client.screen;

import com.holybuckets.villageecon.menu.ModMenus;
import net.blay09.mods.balm.api.client.screen.BalmScreens;

public class ModScreens {
    public static void clientInitialize(BalmScreens screens) {
        screens.registerScreen(
            ModMenus.countingChestMenu::get,
            CountingChestScreen::new
        );
        screens.registerScreen(
            ModMenus.mayorTradeMenu::get,
            MayorTradeScreen::new
        );
    }

}
