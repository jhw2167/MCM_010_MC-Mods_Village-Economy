package com.holybuckets.villageecon;

import com.holybuckets.foundation.event.BalmEventRegister;
import com.holybuckets.villageecon.block.ModBlocks;
import com.holybuckets.villageecon.block.be.ModBlockEntities;
import com.holybuckets.villageecon.item.ModItems;
import com.holybuckets.villageecon.menu.ModMenus;
import com.holybuckets.villageecon.platform.Services;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;


public class CommonClass {

    public static boolean isInitialized = false;
    public static void init()
    {
        if (isInitialized)
            return;

        Constants.LOG.info("Hello from Common init on {}! we are currently in a {} environment!", com.holybuckets.villageecon.platform.Services.PLATFORM.getPlatformName(), com.holybuckets.villageecon.platform.Services.PLATFORM.getEnvironmentName());
        Constants.LOG.info("The ID for diamonds is {}", BuiltInRegistries.ITEM.getKey(Items.DIAMOND));

        //Initialize Foundations
        com.holybuckets.foundation.FoundationInitializers.commonInitialize();

        if (Services.PLATFORM.isModLoaded(Constants.MOD_ID)) {
            Constants.LOG.info("Hello to " + Constants.MOD_NAME + "!");
        }

        //RegisterConfigs
        Balm.getConfig().registerConfig(com.holybuckets.villageecon.config.VillageEconConfig.class);
        VillageEconomyMain.INSTANCE = new VillageEconomyMain();
        BalmEventRegister.registerEvents();
        BalmEventRegister.registerCommands();
        ModBlocks.initialize(Balm.getBlocks());
        ModBlockEntities.initialize(Balm.getBlockEntities());
        ModItems.initialize(Balm.getItems());
        ModMenus.initialize(Balm.getMenus());
        com.holybuckets.villageecon.entity.ModEntities.initialize(Balm.getEntities());
        com.holybuckets.villageecon.networking.ModNetworking.initialize(Balm.getNetworking());
        
        isInitialized = true;
    }

    /**
     * Description: Run sample tests methods
     */
    public static void sample()
    {

    }
}