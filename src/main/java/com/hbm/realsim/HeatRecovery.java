package com.hbm.realsim;

import com.hbm.realsim.SteamLedger.State;

/** HRSG debit into the same thermal account. Temperature approach and transfer rating are independent of storage. */
public final class HeatRecovery {
    public static final long RATED_HEAT_WATTS = 6000000L;
    public static final int APPROACH_C = 30;
    private HeatRecovery() { }
    public static ThermalAccount.Result advance(long tick, ExhaustAccount exhaust, ThermalAccount steam,
            State outlet, int feedCount, int outputRoom, long availableWorkQuanta) {
        if(outlet != State.ORDINARY && outlet != State.HOT && outlet != State.SUPERHOT) throw new IllegalArgumentException("HRSG outlet exceeds plant capability");
        int minimumC = outlet.temperatureC + APPROACH_C;
        long heat = exhaust.availableMicroJ(minimumC) / SteamLedger.MICROJ_PER_QUANTUM;
        ThermalAccount.Result result = steam.advance(tick, SteamLedger.transition(State.COLD, outlet, SteamLedger.Route.BOILER, 900000),
                feedCount, outputRoom, 200000L, heat, availableWorkQuanta, 0, RATED_HEAT_WATTS / 10, 20000, 0, Long.MAX_VALUE);
        long requested = Math.multiplyExact(result.heatQuanta, SteamLedger.MICROJ_PER_QUANTUM);
        if(exhaust.take(requested, minimumC) != requested) throw new IllegalStateException("HRSG exhaust overdraw");
        return result;
    }
}
