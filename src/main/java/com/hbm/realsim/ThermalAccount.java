package com.hbm.realsim;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import com.hbm.realsim.SteamLedger.State;
import com.hbm.realsim.SteamLedger.Transition;

/** Owned fractional mass, prepaid utilities and unreleased work. No offline production or unpaid stock. */
public final class ThermalAccount {
    private long ownedMass, heatCredit, workCredit, electricCredit;
    private long ventRemainder;
    private State inlet, outlet;
    private final Map<String, Long> productMass = new HashMap<String, Long>();
    private long lastTick = Long.MIN_VALUE;
    public long currentMass, currentElectricQuanta, currentRejectedMicroJ, currentHeatQuanta;
    public long ownedMass() { return ownedMass; }
    public State inlet() { return inlet; }
    public State outlet() { return outlet; }
    public long remainder(State state) { Long m = productMass.get(state.identity); return m == null ? 0 : m; }
    public void clearCurrent() { currentMass = currentElectricQuanta = currentRejectedMicroJ = currentHeatQuanta = 0; }
    /** Visual status only; never transfers owned mass or energy to a client. */
    public void writeStatus(NBTTagCompound n) {
        n.setLong("thermalFlow", currentMass); n.setLong("thermalElectric", currentElectricQuanta);
        n.setLong("thermalRejected", currentRejectedMicroJ); n.setLong("thermalTransfer", currentHeatQuanta);
    }
    public void readStatus(NBTTagCompound n) {
        currentMass = n.getLong("thermalFlow"); currentElectricQuanta = n.getLong("thermalElectric");
        currentRejectedMicroJ = n.getLong("thermalRejected"); currentHeatQuanta = n.getLong("thermalTransfer");
    }
    public static final class Result {
        public int consumed, produced;
        public long heatQuanta, workQuanta, electricQuanta, mass, rejectionMicroJ, ventedMass;
    }
    public Result advance(long tick, Transition t, int inputCount, int outputRoom, long flowMass,
            long availableHeatQuanta, long availableWorkQuanta, long electricRoomQuanta,
            long heatRatingQuanta, long workRatingQuanta, long electricRatingQuanta, long rejectionRatingQuanta) {
        Result r = new Result();
        clearCurrent();
        if(tick <= lastTick) return r;
        lastTick = tick;
        if(ownedMass > 0 && (inlet != t.inlet || outlet != t.outlet)) return r;
        boolean changeQuality = ownedMass == 0 && outlet != null && outlet != t.outlet;
        long retiredHeat = changeQuality ? heatCredit : 0;
        long compatibleHeat = changeQuality ? 0 : heatCredit;
        if(inputCount < 0 || outputRoom <= 0 || flowMass <= 0) return r;
        long mass = Math.min(flowMass, Math.addExact(ownedMass, t.inlet.mass(inputCount)));
        mass = Math.min(mass, Math.max(0, t.outlet.mass(outputRoom) - remainder(t.outlet)));
        if(t.heatJPerKg > 0) mass = Math.min(mass, Math.min(
                Math.addExact(Math.multiplyExact(Math.max(0, Math.min(availableHeatQuanta, heatRatingQuanta)), SteamLedger.MICROJ_PER_QUANTUM), compatibleHeat) / t.heatJPerKg,
                Math.multiplyExact(heatRatingQuanta, SteamLedger.MICROJ_PER_QUANTUM) / t.heatJPerKg));
        if(t.workJPerKg > 0) mass = Math.min(mass, Math.min(
                Math.addExact(Math.multiplyExact(Math.max(0, Math.min(availableWorkQuanta, workRatingQuanta)), SteamLedger.MICROJ_PER_QUANTUM), workCredit) / t.workJPerKg,
                Math.multiplyExact(workRatingQuanta, SteamLedger.MICROJ_PER_QUANTUM) / t.workJPerKg));
        if(t.electricJPerKg > 0) {
            long room = Math.min(Math.max(0, electricRoomQuanta), electricRatingQuanta);
            if(room == 0) return r;
            mass = Math.min(mass, Math.max(0, Math.multiplyExact(room, SteamLedger.MICROJ_PER_QUANTUM) - electricCredit) / t.electricJPerKg);
        }
        if(rejectionRatingQuanta != Long.MAX_VALUE && t.rejectionJPerKg > 0)
            mass = Math.min(mass, Math.multiplyExact(rejectionRatingQuanta, SteamLedger.MICROJ_PER_QUANTUM) / t.rejectionJPerKg);
        if(mass <= 0) return r;
        long heat = Math.multiplyExact(mass, t.heatJPerKg), work = Math.multiplyExact(mass, t.workJPerKg);
        r.heatQuanta = SteamLedger.ceilDiv(Math.max(0, heat - compatibleHeat), SteamLedger.MICROJ_PER_QUANTUM);
        r.workQuanta = SteamLedger.ceilDiv(Math.max(0, work - workCredit), SteamLedger.MICROJ_PER_QUANTUM);
        r.consumed = Math.toIntExact(SteamLedger.ceilDiv(Math.max(0, mass - ownedMass), t.inlet.massPerUnit));
        ownedMass = Math.addExact(ownedMass, t.inlet.mass(r.consumed)) - mass;
        inlet = t.inlet; outlet = t.outlet;
        heatCredit = Math.addExact(compatibleHeat, Math.multiplyExact(r.heatQuanta, SteamLedger.MICROJ_PER_QUANTUM)) - heat;
        workCredit = Math.addExact(workCredit, Math.multiplyExact(r.workQuanta, SteamLedger.MICROJ_PER_QUANTUM)) - work;
        electricCredit = Math.addExact(electricCredit, Math.multiplyExact(mass, t.electricJPerKg));
        r.electricQuanta = electricCredit / SteamLedger.MICROJ_PER_QUANTUM;
        electricCredit %= SteamLedger.MICROJ_PER_QUANTUM;
        // Declared 0.5% steam loss/blowdown equivalent. Lost mass and its state heat are rejected once.
        // Cooling remains separate; condensers do not evaporate feedwater.
        long vent = 0;
        if(t.route == SteamLedger.Route.BOILER) {
            long loss = Math.addExact(Math.multiplyExact(mass, 5000), ventRemainder);
            vent = loss / 1000000; ventRemainder = loss % 1000000;
        }
        r.ventedMass = vent;
        long output = Math.addExact(remainder(t.outlet), mass - vent);
        r.produced = Math.toIntExact(output / t.outlet.massPerUnit);
        productMass.put(t.outlet.identity, output % t.outlet.massPerUnit);
        r.mass = currentMass = mass;
        r.rejectionMicroJ = currentRejectedMicroJ = Math.addExact(retiredHeat, Math.addExact(Math.multiplyExact(mass, t.rejectionJPerKg), Math.multiplyExact(vent, t.outlet.enthalpyJPerKg)));
        currentElectricQuanta = r.electricQuanta; currentHeatQuanta = r.heatQuanta;
        return r;
    }
    public void write(NBTTagCompound parent) {
        NBTTagCompound n = new NBTTagCompound(); n.setInteger("version", 1);
        n.setLong("mass", ownedMass); n.setString("in", inlet == null ? "" : inlet.identity); n.setString("out", outlet == null ? "" : outlet.identity);
        n.setLong("heat", heatCredit); n.setLong("work", workCredit); n.setLong("electric", electricCredit); n.setLong("tick", lastTick); n.setLong("ventRemainder", ventRemainder);
        NBTTagCompound products = new NBTTagCompound();
        for(Map.Entry<String, Long> e : productMass.entrySet()) products.setLong(e.getKey(), e.getValue());
        n.setTag("products", products); parent.setTag("realSimThermal", n);
    }
    public void read(NBTTagCompound parent) {
        ownedMass = heatCredit = workCredit = electricCredit = ventRemainder = 0; inlet = outlet = null; productMass.clear(); lastTick = Long.MIN_VALUE;
        if(!parent.hasKey("realSimThermal", 10)) return; // Legacy progress/heat is never certified work.
        NBTTagCompound n = parent.getCompoundTag("realSimThermal");
        if(n.getInteger("version") != 1) throw new IllegalStateException("Unsupported thermal account");
        ownedMass = n.getLong("mass"); inlet = SteamLedger.state(n.getString("in")); outlet = SteamLedger.state(n.getString("out"));
        heatCredit = n.getLong("heat"); workCredit = n.getLong("work"); electricCredit = n.getLong("electric"); lastTick = n.getLong("tick"); ventRemainder = n.getLong("ventRemainder");
        if(ownedMass < 0 || ownedMass > 10000000 || ownedMass > 0 && (inlet == null || outlet == null || ownedMass >= inlet.massPerUnit)
                || heatCredit < 0 || heatCredit >= 500000 || workCredit < 0 || workCredit >= 500000 || electricCredit < 0 || electricCredit >= 500000 || ventRemainder < 0 || ventRemainder >= 1000000)
            throw new IllegalStateException("Invalid thermal ownership");
        NBTTagCompound p = n.getCompoundTag("products");
        for(Object key : p.func_150296_c()) {
            String id = (String) key; State s = SteamLedger.state(id); long mass = p.getLong(id);
            if(s == null || mass < 0 || mass >= s.massPerUnit) throw new IllegalStateException("Invalid thermal product remainder");
            productMass.put(id, mass);
        }
    }
}
