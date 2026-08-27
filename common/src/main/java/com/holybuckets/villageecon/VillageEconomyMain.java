package com.holybuckets.villageecon;


import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.villageecon.config.ModConfig;
import com.holybuckets.villageecon.config.VillageEconConfig;
import com.holybuckets.villageecon.core.VillageManager;
import com.holybuckets.villageecon.core.trade.Bazaar;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;

/**
 * Main instance of the mod, initialize this class statically via commonClass
 * This class will init all major Manager instances and events for the mod
 */
public class VillageEconomyMain {
    private static boolean DEV_MODE = false;;
    private static VillageEconConfig CONFIG;
    public static VillageEconomyMain INSTANCE;

    public VillageEconomyMain()
    {
        super();
        INSTANCE = this;
        init();
        // LoggerProject.logInit( "001000", this.getClass().getName() ); // Uncomment if you have a logging system in place
    }

    private void init()
    {

        //Events
        EventRegistrar registrar = EventRegistrar.getInstance();
        ModConfig.init(registrar);
        VillageManager.init(registrar);
        Bazaar.init(registrar);


        //register local events
        registrar.registerOnBeforeServerStarted(this::onServerStarting);

    }

    private void onServerStarting(ServerStartingEvent e) {
        CONFIG = Balm.getConfig().getActiveConfig(VillageEconConfig.class);
        //this.DEV_MODE = CONFIG.devMode;
        this.DEV_MODE = false;
    }


}
