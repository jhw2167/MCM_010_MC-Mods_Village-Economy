package com.holybuckets.villageecon.core;

import com.holybuckets.villageecon.core.model.VillageEconomy;

/**
 * Class: TradeEngine
 * Description: PLACEHOLDER. Matches and executes inter-village trades at cycle end.
 * Villages schedule incoming trades (theoLedger surpluses) and outgoing trades
 * (theoLedger deficits) during cycleProcess; the engine matches them across villages
 * and settles resources and currency into static ledgers.
 */
public class TradeEngine {

    public static final String CLASS_ID = "018";

    /** Schedule a trade delivering the resource from another village to this one. TODO **/
    public static void scheduleIncomingTrade(VillageEconomy village, String resourceId, int count) {
        //TODO
    }

    /** Schedule a trade shipping the resource from this village to another. TODO **/
    public static void scheduleOutgoingTrade(VillageEconomy village, String resourceId, int count) {
        //TODO
    }

    /** Match and execute all scheduled trades across villages. Called once per cycle. TODO **/
    public static void executeScheduledTrades() {
        //TODO
    }
}
