package com.holybuckets.villageecon.core;

import com.holybuckets.villageecon.core.model.Mayor;
import com.holybuckets.villageecon.core.model.ResourceLedger;

//Future concept will trades will physically move from one town to the next

public class TradeEngine {

    public static final String CLASS_ID = "018";

    /** Schedule a trade delivering the resource from another village to this one.
    * - diff > 0
*/
    public static void incomingTrade(Mayor village, String resourceId, int diff, ResourceLedger ledger) {
        ledger.add(resourceId, diff);
    }

    /** Schedule a trade shipping the resource from this village to another.
    * - diff > 0, the negative is subtracted from the ledger to reflect the outgoing trade
  */
    public static void outgoingTrade(Mayor village, String resourceId, int diff, ResourceLedger ledger) {
        ledger.add(resourceId, -diff);
    }

    /** Match and execute all scheduled trades across villages. Called once per cycle. TODO **/
    public static void executeScheduledTrades() {

    }
}
