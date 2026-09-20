package com.holybuckets.villageecon.core;

import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.ResourceLedger;

/**
 * Class: TradeEngine
 * Description: PLACEHOLDER. Matches and executes inter-village trades at cycle end.
 * Villages schedule incoming trades (theoLedger surpluses) and outgoing trades
 * (theoLedger deficits) during cycleProcess; the engine matches them across villages
 * and settles resources and currency into static ledgers.
 */
public class TradeEngine {

    public static final String CLASS_ID = "018";

    /** Schedule a trade delivering the resource from another village to this one.
    * - diff > 0
    * TODO **/
    public static void scheduleIncomingTrade(Mayor village, String resourceId, int diff, ResourceLedger ledger) {
        ledger.add(resourceId, diff);
    }

    /** Schedule a trade shipping the resource from this village to another.
    * - diff > 0, the negative is subtracted from the ledger to reflect the outgoing trade
    * TODO **/
    public static void scheduleOutgoingTrade(Mayor village, String resourceId, int diff, ResourceLedger ledger) {
        ledger.add(resourceId, -diff);
    }

    /** Match and execute all scheduled trades across villages. Called once per cycle. TODO **/
    public static void executeScheduledTrades() {
        //TODO
    }
}
