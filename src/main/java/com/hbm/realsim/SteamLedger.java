package com.hbm.realsim;

import java.math.BigInteger;

/** Frozen Phase 5D states. Inventory counts are water-equivalent batches, never gas liters or PU. */
public final class SteamLedger {
    public static final long MASS_PER_KG = 1_000_000L; // milligrams
    public static final long MICROJ_PER_QUANTUM = 500_000L;
    public enum State {
        COLD("FRESH_WATER", 20, 101420, 1_000_000, 0, 0),
        WARM("WARM_CONDENSATE", 46, 10000, 1_000_000, 107900, 107900),
        ORDINARY("STEAM", 100, 101420, 10000, 2591690, 335260),
        HOT("HOTSTEAM", 300, 1000000, 100000, 2967690, 953090),
        SUPERHOT("SUPERHOTSTEAM", 450, 4000000, 1000000, 3247290, 1533990),
        ULTRAHOT("ULTRAHOTSTEAM", 600, 16000000, 10000000, 3490490, 2559390),
        SPENT("SPENTSTEAM", 46, 10000, 1000000, 2316090, 107900);
        public final String identity;
        public final int temperatureC;
        public final long pressurePa, massPerUnit, enthalpyJPerKg, sensibleJPerKg;
        State(String id, int t, long p, long m, long h, long sensible) {
            identity = id; temperatureC = t; pressurePa = p; massPerUnit = m;
            enthalpyJPerKg = h; sensibleJPerKg = sensible;
        }
        public long mass(long count) { return Math.multiplyExact(count, massPerUnit); }
        public long enthalpyMicroJ(long mass) { return Math.multiplyExact(mass, enthalpyJPerKg); }
        public long latentJPerKg() { return enthalpyJPerKg - sensibleJPerKg; }
    }
    public enum Route { BOILER, COMPRESSOR, TURBINE, CONDENSER, HEAT_EXCHANGER }
    public static final class Transition {
        public final State inlet, outlet;
        public final Route route;
        public final long heatJPerKg, workJPerKg, electricJPerKg, rejectionJPerKg;
        Transition(State in, State out, Route route, long heat, long work, long electric) {
            inlet = in; outlet = out; this.route = route;
            heatJPerKg = heat; workJPerKg = work; electricJPerKg = electric;
            rejectionJPerKg = Math.subtractExact(Math.addExact(in.enthalpyJPerKg, Math.addExact(heat, work)), Math.addExact(out.enthalpyJPerKg, electric));
            if(rejectionJPerKg < 0) throw new IllegalArgumentException("Unclosed thermal transition");
        }
    }
    private SteamLedger() { }
    public static State state(String name) {
        if("WATER".equals(name)) return State.COLD;
        for(State s : State.values()) if(s.identity.equals(name)) return s;
        return null;
    }
    public static State expansionOutlet(State in) {
        if(in == State.ORDINARY) return State.SPENT;
        if(in == State.HOT) return State.ORDINARY;
        if(in == State.SUPERHOT) return State.HOT;
        if(in == State.ULTRAHOT) return State.SUPERHOT;
        return null;
    }
    public static long expansionWork(State in) {
        if(in == State.ORDINARY) return 250000;
        if(in == State.HOT) return 300000;
        if(in == State.SUPERHOT) return 220000;
        if(in == State.ULTRAHOT) return 200000;
        throw new IllegalArgumentException("No expansion from " + in);
    }
    /** Frozen direct condensing rows; an outlet cannot also be credited for the intermediate stages. */
    public static long fullExpansionWork(State in) {
        if(in == State.ORDINARY) return 250000;
        if(in == State.HOT) return 550000;
        if(in == State.SUPERHOT) return 770000;
        if(in == State.ULTRAHOT) return 970000;
        throw new IllegalArgumentException("No condensing expansion from " + in);
    }
    public static State compressorOutlet(State in) {
        if(in == State.ORDINARY) return State.HOT;
        if(in == State.HOT) return State.SUPERHOT;
        if(in == State.SUPERHOT) return State.ULTRAHOT;
        return null;
    }
    public static Transition transition(State in, State out, Route route, int efficiencyPpm) {
        if(in == null || out == null || efficiencyPpm <= 0 || efficiencyPpm > 1000000) throw new IllegalArgumentException("Invalid thermal state/efficiency");
        long heat = 0, work = 0, electric = 0;
        switch(route) {
        case BOILER:
            if((in != State.COLD && in != State.WARM) || expansionOutlet(out) == null) throw new IllegalArgumentException("Boiler requires liquid feed");
            // Liquid pressure pump; retained pump work is part of, not additional to, the state rise.
            work = out == State.ORDINARY ? 0 : out == State.HOT ? 1500 : out == State.SUPERHOT ? 6000 : 24000;
            heat = ceilDiv(Math.multiplyExact(out.enthalpyJPerKg - in.enthalpyJPerKg - work, 1000000L), efficiencyPpm);
            break;
        case COMPRESSOR:
            if(compressorOutlet(in) != out) throw new IllegalArgumentException("Illegal steam compressor stage");
            work = in == State.ORDINARY ? 735610 : in == State.HOT ? 599730 : 739500;
            break;
        case TURBINE:
            if(expansionOutlet(in) != out && !(expansionOutlet(in) != null && out == State.SPENT)) throw new IllegalArgumentException("Illegal expansion stage");
            electric = mulDiv(out == State.SPENT ? fullExpansionWork(in) : expansionWork(in), efficiencyPpm, 1000000);
            break;
        case CONDENSER:
            if(!((in == State.SPENT && (out == State.WARM || out == State.COLD)) || (in == State.WARM && out == State.COLD))) throw new IllegalArgumentException("Illegal condensate return");
            // Fans/vacuum: 10 kJ/kg at powered equipment; 0 denotes the passive ambient sink.
            work = efficiencyPpm == 1000000 ? 0 : 10000;
            break;
        case HEAT_EXCHANGER:
            if(in.enthalpyJPerKg <= out.enthalpyJPerKg || (expansionOutlet(in) != out && !(in == State.SPENT && out == State.COLD))) throw new IllegalArgumentException("Illegal heat recovery");
            break;
        default: throw new IllegalArgumentException("Unknown route");
        }
        return new Transition(in, out, route, heat, work, electric);
    }
    /** Checked wide intermediate; only bounded per-tick/batch results enter long storage. */
    public static long mulDiv(long a, long b, long divisor) {
        if(a < 0 || b < 0 || divisor <= 0) throw new IllegalArgumentException("Negative thermal arithmetic");
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(divisor)).longValueExact();
    }
    public static long ceilDiv(long value, long divisor) { return value == 0 ? 0 : Math.addExact(1, (value - 1) / divisor); }
    public static boolean canHeat(State out, int sourceC, int approachC) { return sourceC >= out.temperatureC + approachC; }
}
