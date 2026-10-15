package com.hbm.realsim;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.realsim.SteamLedger.State;
import com.hbm.realsim.SteamLedger.Route;
import com.hbm.realsim.SteamLedger.Transition;

/** Native owning-thread tank adapter. Traits are only a deferred non-steam compatibility boundary. */
public final class ThermalMachine {
    private ThermalMachine() { }
    public static State state(FluidTank tank) { return SteamLedger.state(tank.getTankType().getName()); }
    public static boolean configure(FluidTank output, State state) {
        FluidType type = Fluids.fromName(state.identity);
        return type != null && type.getName().equals(state.identity) && output.configureForRecipe(type, output.getPressure());
    }
    public static ThermalAccount.Result transfer(ThermalAccount account, long tick, Transition transition,
            FluidTank in, FluidTank out, long flowMass, long heat, long work, long powerRoom,
            long heatRating, long workRating, long powerRating, long rejectionRating) {
        if(!configure(out, transition.outlet) || state(in) != transition.inlet && account.ownedMass() == 0) { account.clearCurrent(); return new ThermalAccount.Result(); }
        ThermalAccount.Result r = account.advance(tick, transition, state(in) == transition.inlet ? in.getFill() : 0, Math.max(0, out.getMaxFill() - out.getFill()),
                flowMass, heat, work, powerRoom, heatRating, workRating, powerRating, rejectionRating);
        in.setFill(in.getFill() - r.consumed); out.setFill(out.getFill() + r.produced);
        return r;
    }
    public static long turbine(ThermalAccount account, long tick, FluidTank in, FluidTank out,
            long flowMassPerTick, long ratingWatts, int efficiencyPpm, long energy, long capacity) {
        State inlet = account.ownedMass() > 0 ? account.inlet() : state(in);
        boolean legacyOwnedOnly = in instanceof SteamInletTank && !((SteamInletTank) in).accepts(inlet);
        if(legacyOwnedOnly && account.ownedMass() == 0) { account.clearCurrent(); return 0; }
        State outlet = SteamLedger.expansionOutlet(inlet) == null ? null : State.SPENT;
        if(outlet == null || efficiencyPpm <= 0) { account.clearCurrent(); return 0; }
        Transition t = SteamLedger.transition(inlet, outlet, Route.TURBINE, efficiencyPpm);
        if(legacyOwnedOnly) {
            // Finish only the fractional parcel admitted before the capability change.
            // Incompatible tank stock is never debited and can be piped to suitable equipment.
            if(!configure(out, outlet)) { account.clearCurrent(); return 0; }
            ThermalAccount.Result r = account.advance(tick, t, 0, Math.max(0, out.getMaxFill() - out.getFill()),
                    flowMassPerTick, 0, 0, Math.max(0, capacity - energy), 0, 0, ratingWatts / 10, Long.MAX_VALUE);
            out.setFill(out.getFill() + r.produced);
            return r.electricQuanta;
        }
        return transfer(account, tick, t, in, out, flowMassPerTick, 0, 0, Math.max(0, capacity - energy),
                0, 0, ratingWatts / 10, Long.MAX_VALUE).electricQuanta;
    }
    public static int efficiency(double setting, int maximumPpm) {
        return Double.isFinite(setting) ? Math.max(0, Math.min(maximumPpm, (int) (setting * 1000000))) : maximumPpm;
    }
}
