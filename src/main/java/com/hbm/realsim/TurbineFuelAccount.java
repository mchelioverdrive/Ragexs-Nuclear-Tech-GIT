package com.hbm.realsim;

import com.hbm.inventory.fluid.tank.FluidTank;
import net.minecraft.nbt.NBTTagCompound;

/** One chemical owner; reserves inventory once and splits only the energy actually burned this tick. */
public final class TurbineFuelAccount {
    private long chemicalCredit, electricRemainder, lastTick = Long.MIN_VALUE;
    private String fuel = "";
    public final ExhaustAccount exhaust = new ExhaustAccount();
    public long chemicalInputMicroJ, electricMicroJ, recoverableMicroJ, rejectedMicroJ;
    /** Further allocation of generated electrical work, not an additional chemical-energy source. */
    public long loadRejectedQuanta;
    public int consumedUnits;
    public long chemicalCredit() { return chemicalCredit; }
    public String fuel() { return fuel; }
    public static boolean suitable(String name) {
        // Fuel suitability is explicit. Sour gas, oxyhydrogen and pure H2 lack a suitable stock combustor.
        return FuelSuitability.has(name, MaterialRole.GAS_TURBINE_FUEL);
    }
    public long burn(long tick, FluidTank tank, long requestedChemicalMicroJ, int electricPpm, int recoverablePpm, int qualityC) {
        return burn(tick, tank, requestedChemicalMicroJ, electricPpm, recoverablePpm, qualityC, true);
    }
    public long burn(long tick, FluidTank tank, long requestedChemicalMicroJ, int electricPpm, int recoverablePpm, int qualityC, boolean gasOnly) {
        chemicalInputMicroJ = electricMicroJ = recoverableMicroJ = rejectedMicroJ = 0; consumedUnits = 0;
        loadRejectedQuanta = 0;
        if(tick <= lastTick || requestedChemicalMicroJ <= 0) return 0;
        if(electricPpm < 0 || recoverablePpm < 0 || electricPpm > 1000000 - recoverablePpm) throw new IllegalArgumentException("Invalid turbine split");
        long burned = Math.min(requestedChemicalMicroJ, chemicalCredit);
        long left = requestedChemicalMicroJ - burned;
        PhysicalState state = tank.getTankType().getPhysicalState();
        boolean suitable = gasOnly ? suitable(tank.getTankType().getName())
                : FuelSuitability.has(tank.getTankType().getName(), MaterialRole.AVIATION_FUEL) && tank.getTankType().hasTrait(com.hbm.inventory.fluid.trait.FT_Combustible.class)
                && tank.getTankType().getTrait(com.hbm.inventory.fluid.trait.FT_Combustible.class).getGrade() == com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade.AERO;
        long credit = 0; int count = 0;
        if(left > 0 && suitable && state.isResolved() && state.chemicalMicrojoulesPerUnit() > 0 && tank.getFill() > 0) {
            count = Math.toIntExact(Math.min(tank.getFill(), SteamLedger.ceilDiv(left, state.chemicalMicrojoulesPerUnit())));
            credit = Math.multiplyExact(state.chemicalMicrojoulesPerUnit(), count); // Validate before any debit.
        }
        lastTick = tick;
        chemicalCredit -= burned;
        if(count > 0) {
            tank.setFill(tank.getFill() - count); chemicalCredit = credit; fuel = tank.getTankType().getName(); consumedUnits = count;
            long extra = Math.min(left, chemicalCredit); chemicalCredit -= extra; burned = Math.addExact(burned, extra);
        }
        CombustionLedger ledger = new CombustionLedger(burned, electricPpm, recoverablePpm);
        chemicalInputMicroJ = burned; electricMicroJ = ledger.usefulMicrojoules;
        recoverableMicroJ = ledger.recoverableHeatMicrojoules;
        long vented = exhaust.add(recoverableMicroJ, qualityC);
        recoverableMicroJ -= vented; rejectedMicroJ = Math.addExact(ledger.rejectedMicrojoules, vented);
        electricRemainder = Math.addExact(electricRemainder, electricMicroJ);
        long quanta = electricRemainder / 500000; electricRemainder %= 500000;
        return quanta;
    }
    public long admitElectrical(long generated, long room) {
        long admitted = Math.min(generated, Math.max(0, room));
        loadRejectedQuanta = generated - admitted;
        return admitted;
    }
    public void write(NBTTagCompound parent) {
        NBTTagCompound n = new NBTTagCompound(); n.setInteger("version", 1); n.setString("fuel", fuel);
        n.setLong("chemical", chemicalCredit); n.setLong("electric", electricRemainder); n.setLong("tick", lastTick);
        parent.setTag("realSimTurbineFuel", n); exhaust.write(parent);
    }
    public void read(NBTTagCompound parent) {
        chemicalCredit = electricRemainder = 0; fuel = ""; lastTick = Long.MIN_VALUE; exhaust.read(parent);
        if(!parent.hasKey("realSimTurbineFuel", 10)) return;
        NBTTagCompound n = parent.getCompoundTag("realSimTurbineFuel");
        if(n.getInteger("version") != 1) throw new IllegalStateException("Unsupported turbine fuel account");
        fuel = n.getString("fuel"); chemicalCredit = n.getLong("chemical"); electricRemainder = n.getLong("electric"); lastTick = n.getLong("tick");
        if(chemicalCredit < 0 || electricRemainder < 0 || electricRemainder >= 500000 || chemicalCredit > 0 && !PhysicalRegistry.get(fuel).isResolved())
            throw new IllegalStateException("Invalid turbine fuel ownership");
    }
}
