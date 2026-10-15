package com.hbm.realsim;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import net.minecraft.nbt.NBTTagCompound;

/** Pressure/temperature capability at the admission boundary; old incompatible stock remains recoverable. */
public final class SteamInletTank extends FluidTank {
    private final SteamLedger.State maximum;
    private final int ordinaryCapacity;
    private final boolean massSized;
    public SteamInletTank(SteamLedger.State maximum, int capacity, boolean massSized) {
        super(Fluids.STEAM, capacity);
        this.maximum = maximum; this.ordinaryCapacity = capacity; this.massSized = massSized;
        withDefaultCapacity();
    }
    public boolean compatible() { return GeneratorContract.acceptsSteam(SteamLedger.state(getTankType().getName()), maximum); }
    public boolean accepts(SteamLedger.State state) { return GeneratorContract.acceptsSteam(state, maximum); }
    @Override public void setTankType(FluidType type) {
        if(type == null || !GeneratorContract.acceptsSteam(SteamLedger.state(type.getName()), maximum)) return;
        if(getFill() > 0 && getTankType() != type) return;
        super.setTankType(type);
        resize();
    }
    private void resize() {
        resizePreservingContents(massSized ? GeneratorContract.steamCapacity(ordinaryCapacity, SteamLedger.state(getTankType().getName())) : ordinaryCapacity);
    }
    @Override public void readFromNBT(NBTTagCompound tag, String key) { super.readFromNBT(tag, key); resize(); }
}
