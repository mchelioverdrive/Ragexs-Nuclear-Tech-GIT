package com.hbm.tileentity.machine;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingStep;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.saveddata.TomSaveData;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IHeatSource;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;

/** Shared continuous simulation and runtime ownership for both heat boiler variants. */
public abstract class TileEntityHeatBoilerBase extends TileEntityLoadedBase implements INBTPacketReceiver, api.hbm.energymk2.IEnergyReceiverMK2, IFluidStandardTransceiver, IConfigurableMachine, IFluidCopiable, com.hbm.realsim.IMachineInfo, api.hbm.tile.IProcessHeatReceiver {
    @Override public api.hbm.tile.IProcessHeatSource getProcessHeatSource() {
        return com.hbm.realsim.ProcessMachine.heaterBelow(this, xCoord, yCoord, zCoord);
    }

    public final com.hbm.realsim.ThermalAccount thermal = new com.hbm.realsim.ThermalAccount();
    public long thermalBufferQuanta, pumpEnergyQuanta, startupPaidQuanta;
    private long lastThermalTick = Long.MIN_VALUE;
    public long getRatedThermalWatts() { return 200000L; }
    public long getRatedFeedPumpWatts() { return 100000L; }
    protected long getThermalCapacityQuanta() { return 32000000L; }
    protected long getStartupQuanta() { return 500000L; }
    protected com.hbm.realsim.SteamLedger.State maximumSteamOutlet() { return com.hbm.realsim.SteamLedger.State.ORDINARY; }
    private com.hbm.realsim.SteamLedger.State selectedSteamOutlet;
    protected com.hbm.realsim.SteamLedger.State boilerOutlet() { return selectedSteamOutlet == null ? maximumSteamOutlet() : selectedSteamOutlet; }
    protected int internalHeatTemperatureC() { return 0; }
    public boolean selectSteamOutlet(com.hbm.realsim.SteamLedger.State state) {
        if(state != com.hbm.realsim.SteamLedger.State.ORDINARY && state != maximumSteamOutlet()) return false;
        if(thermal.ownedMass() > 0 || physicalBatch.active() || tanks[1].getFill() > 0 && tanks[1].getTankType() != Fluids.fromName(state.identity)) return false;
        selectedSteamOutlet = state; markDirty();
        if(worldObj != null) markMachineFluidDirty();
        return true;
    }
    public static com.hbm.realsim.ProcessRecipe heatingRecipe(com.hbm.inventory.fluid.FluidType type) {
        com.hbm.realsim.ProcessRecipe oil = crudeHeating.get(type.getName());
        return oil == null ? com.hbm.realsim.CoolantLedger.heating(type.getName()) : oil;
    }
    public static boolean supportsFeed(com.hbm.inventory.fluid.FluidType type) {
        if(type == null) return false;
        com.hbm.realsim.SteamLedger.State steam = com.hbm.realsim.SteamLedger.state(type.getName());
        if(steam != null) return steam == com.hbm.realsim.SteamLedger.State.COLD || steam == com.hbm.realsim.SteamLedger.State.WARM;
        return type == Fluids.AIR || type == Fluids.AIR_HEATED || heatingRecipe(type) != null
                || type.hasTrait(FT_Heatable.class) && type.getTrait(FT_Heatable.class).getEfficiency(HeatingType.BOILER) > 0;
    }
    public boolean selectFeed(com.hbm.inventory.fluid.FluidType type) {
        if(!supportsFeed(type) || tanks[0].getFill() > 0 && tanks[0].getTankType() != type) return false;
        if(thermal.ownedMass() > 0 || physicalBatch.active()) return false;
        tanks[0].setTankType(type); markDirty();
        if(worldObj != null) markMachineFluidDirty();
        return true;
    }
    protected long additionalHeat(long requestQuanta) { return 0; }
    private long processHeatQuanta;
    /** Private paid buffer, not a new heat export port on the boiler. */
    private final api.hbm.tile.IProcessHeatSource processHeat = new api.hbm.tile.IProcessHeatSource() {
        @Override public int getProcessTemperatureC() { return physicalBatch.active() ? physicalBatch.recipe().temperatureC : 0; }
        @Override public int getHeatStored() { return (int) Math.min(Integer.MAX_VALUE, processHeatQuanta); }
        @Override public void useUpHeat(int amount) { processHeatQuanta -= Math.min(Math.max(0, amount), processHeatQuanta); }
    };
    private long processHeatRequest() {
        com.hbm.realsim.ProcessRecipe bill = physicalBatch.recipe();
        long step = com.hbm.realsim.SteamLedger.ceilDiv(bill.heatQuanta, bill.duration);
        long bufferLimit = Math.min(physicalBatch.remainingHeat(), Math.multiplyExact(step, 2L));
        return Math.min(getRatedThermalWatts() / 10L, Math.max(0, bufferLimit - processHeatQuanta));
    }
    private void runPhysicalHeating(com.hbm.realsim.ProcessRecipe selected) {
        long now = worldObj.getTotalWorldTime();
        if(now <= lastThermalTick) return;
        lastThermalTick = now; isOn = false;
        if(tanks[1].getFill() > 0) sendFluid();
        FluidTank[] inputs = {tanks[0]}, outputs = {tanks[1]};
        com.hbm.realsim.ProcessMachine.sizeProcessBuffers(physicalBatch.active() ? physicalBatch.recipe() : selected, inputs, outputs);
        if(!physicalBatch.active() && !com.hbm.realsim.ProcessMachine.reserveInputs(physicalBatch, selected, inputs, outputs, null, 0, -1, 0, -1)) return;
        com.hbm.realsim.ProcessRecipe bill = physicalBatch.recipe();
        if(com.hbm.realsim.ProcessMachine.outputReady(physicalBatch, bill, outputs, null, 0, -1)
                && pumpEnergyQuanta >= physicalBatch.nextWork()) {
            api.hbm.tile.IProcessHeatSource source = com.hbm.realsim.ProcessMachine.heater(this);
            boolean externalQualified = source != null && source.getProcessTemperatureC() >= bill.temperatureC;
            boolean internalQualified = internalHeatTemperatureC() >= bill.temperatureC;
            if(physicalBatch.nextHeat() <= processHeatQuanta || externalQualified || internalQualified) {
                long need = processHeatRequest();
                long pulled = externalQualified ? source.drawProcessHeatQuanta(now, need) : 0;
                // Partial supply is saved against this immutable bill; never relabel steam heat quality.
                processHeatQuanta += pulled + (internalQualified ? additionalHeat(need - pulled) : 0);
                int before = physicalBatch.ticks();
                com.hbm.realsim.ProcessMachine.stepWithHeater(this, physicalBatch, selected, inputs, outputs, null, 0, -1, 0, -1, processHeat);
                isOn = before != physicalBatch.ticks() || !physicalBatch.active();
            }
        }
        if(tanks[1].getFill() > 0) sendFluid();
        markDirty();
    }
    @Override public long getStoredEnergyQuanta() { return pumpEnergyQuanta; }
    @Override public long getEnergyCapacityQuanta() { return 2000000L; }
    @Override public void setStoredEnergyQuanta(long value) { pumpEnergyQuanta = value; markPowerNetDirty(); markMachineEnergyDirty(); markDirty(); }
    private boolean isPhysicalSteamPath() { return thermal.ownedMass() > 0 || com.hbm.realsim.ThermalMachine.state(tanks[0]) != null; }
    private boolean isBlastHeatingPath() { return thermal.ownedMass() == 0 && !physicalBatch.active() && (tanks[0].getTankType() == Fluids.AIR || tanks[0].getTankType() == Fluids.AIR_HEATED); }
    private com.hbm.inventory.fluid.FluidType blastOutlet() { return tanks[0].getTankType() == Fluids.AIR ? Fluids.AIR_HEATED : Fluids.AIRBLAST; }
    private void runBlastHeater() {
        long now = worldObj.getTotalWorldTime();
        if(now <= lastThermalTick) return;
        lastThermalTick = now; isOn = false;
        com.hbm.inventory.fluid.FluidType outlet = blastOutlet();
        if(!tanks[1].configureForRecipe(outlet, tanks[0].getPressure())) return;
        if(tanks[1].getFill() > 0) sendFluid();
        api.hbm.tile.IProcessHeatSource source = com.hbm.realsim.ProcessMachine.heater(this);
        // Blast quality cannot be upgraded by stored heat from a cooler source.
        boolean externalQualified = source != null && source.getProcessTemperatureC() >= outlet.temperature + 30;
        boolean internalQualified = internalHeatTemperatureC() >= outlet.temperature + 30;
        if(!externalQualified && !internalQualified) return;
        long perLiter = com.hbm.realsim.SteamLedger.ceilDiv(com.hbm.realsim.PhysicalRegistry.get("AIR").massUnits
                * (outlet.temperature - tanks[0].getTankType().temperature) * 1000L, com.hbm.realsim.PhysicalState.MASS_UNITS_PER_KG) * 2;
        int amount = Math.min(tanks[0].getFill(), Math.max(0, tanks[1].getMaxFill() - tanks[1].getFill()));
        long allowance = getRatedThermalWatts() / 10;
        long need = Math.min(allowance, Math.max(0, amount * perLiter - thermalBufferQuanta));
        long pulled = externalQualified ? source.drawProcessHeatQuanta(now, need) : 0;
        thermalBufferQuanta += pulled + (internalQualified ? additionalHeat(need - pulled) : 0);
        amount = (int) Math.min(amount, Math.min(allowance, thermalBufferQuanta) / perLiter);
        if(amount > 0) {
            tanks[0].setFill(tanks[0].getFill() - amount); tanks[1].setFill(tanks[1].getFill() + amount);
            thermalBufferQuanta -= amount * perLiter; isOn = true; markDirty();
        }
        if(tanks[1].getFill() > 0) sendFluid();
        heat = Math.toIntExact(com.hbm.realsim.SteamLedger.mulDiv(thermalBufferQuanta, getMaxBoilerHeat(), getThermalCapacityQuanta()));
    }
    private void runSteamBoiler() {
        long now = worldObj.getTotalWorldTime();
        if(now <= lastThermalTick) return;
        lastThermalTick = now; thermal.clearCurrent();
        com.hbm.realsim.SteamLedger.State in = thermal.ownedMass() > 0 ? thermal.inlet() : com.hbm.realsim.ThermalMachine.state(tanks[0]);
        // Grade upgrades require the compressor/aftercooler route. Heat alone cannot relabel pressure.
        if(in != com.hbm.realsim.SteamLedger.State.COLD && in != com.hbm.realsim.SteamLedger.State.WARM) { isOn = false; return; }
        com.hbm.realsim.SteamLedger.State out = thermal.ownedMass() > 0 ? thermal.outlet() : boilerOutlet();
        if(!com.hbm.realsim.ThermalMachine.configure(tanks[1], out)) { isOn = false; return; }
        long rating = getRatedThermalWatts() / 10;
        long request = Math.min(rating, Math.max(0, getThermalCapacityQuanta() - thermalBufferQuanta));
        api.hbm.tile.IProcessHeatSource source = com.hbm.realsim.ProcessMachine.heater(this);
        long pulled = 0;
        if(source != null && com.hbm.realsim.SteamLedger.canHeat(out, source.getProcessTemperatureC(), 30)) {
            pulled = source.drawProcessHeatQuanta(now, request);
        }
        thermalBufferQuanta = Math.addExact(thermalBufferQuanta, Math.addExact(pulled, additionalHeat(request - pulled)));
        if(startupPaidQuanta < getStartupQuanta()) {
            long warmup = Math.min(thermalBufferQuanta, getStartupQuanta() - startupPaidQuanta);
            thermalBufferQuanta -= warmup; startupPaidQuanta += warmup;
        }
        if(startupPaidQuanta == getStartupQuanta()) {
            com.hbm.realsim.SteamLedger.Transition t = com.hbm.realsim.SteamLedger.transition(in, out, com.hbm.realsim.SteamLedger.Route.BOILER, 980000);
            com.hbm.realsim.ThermalAccount.Result r = com.hbm.realsim.ThermalMachine.transfer(thermal, now, t, tanks[0], tanks[1],
                    this instanceof TileEntityHeatBoilerIndustrial ? 400000L : 10000L,
                    thermalBufferQuanta, pumpEnergyQuanta, 0, rating, getRatedFeedPumpWatts() / 10L, 0, Long.MAX_VALUE);
            thermalBufferQuanta -= r.heatQuanta; pumpEnergyQuanta -= r.workQuanta;
            isOn = r.mass > 0;
            if(shouldExplodeOnBlockedOutput() && tanks[0].getFill() > 0 && tanks[1].getFill() >= tanks[1].getMaxFill() && thermalBufferQuanta >= getThermalCapacityQuanta()) handleBlockedOutput();
        } else isOn = false;
        // heat remains a legacy GUI field; the long physical buffer is authoritative.
        heat = Math.toIntExact(com.hbm.realsim.SteamLedger.mulDiv(thermalBufferQuanta, getMaxBoilerHeat(), getThermalCapacityQuanta()));
        if(tanks[1].getFill() > 0) sendFluid();
        markDirty();
    }

	private static final int TASK_SIMULATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeFluidMutation;
	private FluidTank[] runtimeTrackedTanks;
	private DirPos[] runtimeConnections;
	private int observedOrientation = Integer.MIN_VALUE;
	private final FluidTank.ChangeListener runtimeTankListener = new FluidTank.ChangeListener() {
		@Override public void onTankChanged(FluidTank changedTank) {
			if(worldObj == null) return;
			markDirty();
			if(!runtimeFluidMutation && !worldObj.isRemote) markMachineFluidDirty();
		}
	};

	public int heat;
	public FluidTank[] tanks;
	public boolean isOn;
    public final com.hbm.realsim.ProcessBatch physicalBatch = new com.hbm.realsim.ProcessBatch();
    private static final java.util.Map<String, com.hbm.realsim.ProcessRecipe> crudeHeating = com.hbm.realsim.PetroleumProcesses.defaults("heat");

    private com.hbm.realsim.ProcessRecipe selectedHeating() {
        return heatingRecipe(tanks[0].getTankType());
    }

	protected abstract int getMaxBoilerHeat();
	protected abstract double getBoilerDiffusion();
	protected abstract DirPos[] getBoilerConnections();
	protected abstract boolean shouldExplodeOnBlockedOutput();
	protected abstract void handleBlockedOutput();

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void updateEntity() {
		// Boiler physics, fluid conversion, and transfer are driven by MachineRuntime.
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		if(this.trackRuntimeTanks()) this.markMachineFluidDirty();
		this.reconcileTankTypes();
		this.refreshRuntimeConnections();
		this.subscribeToInput();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState(heat);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(this.trackRuntimeTanks()) this.markMachineFluidDirty();
		this.refreshRuntimeConnections();
		this.subscribeToInput();
		this.sendRuntimeState(heat);
		if(!this.isBoilerDisabled() && heat == 0 && tanks[0].getFill() == 0 && tanks[1].getFill() == 0 && this.hasExternalHeatOpportunity()) {
			this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
		}
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_SIMULATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(this.isBoilerDisabled()) {
			this.sendRuntimeState(heat);
			this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
			return;
		}
        if(isPhysicalSteamPath()) {
            runtimeFluidMutation = true;
            try { runSteamBoiler(); } finally { runtimeFluidMutation = false; }
            sendRuntimeState(heat); evaluateAndSchedule(worldObj.getTotalWorldTime()); return;
        }
        if(isBlastHeatingPath()) {
            runtimeFluidMutation = true;
            try { runBlastHeater(); } finally { runtimeFluidMutation = false; }
            sendRuntimeState(heat); evaluateAndSchedule(worldObj.getTotalWorldTime()); return;
        }

        com.hbm.realsim.ProcessRecipe crude = selectedHeating();
        if(physicalBatch.active() || crude != null) {
            runtimeFluidMutation = true;
            try {
                runPhysicalHeating(crude);
            } finally { runtimeFluidMutation = false; }
            this.sendRuntimeState(heat);
            this.evaluateAndSchedule(worldObj.getTotalWorldTime());
            return;
        }
		this.runtimeFluidMutation = true;
		int previousHeat = this.heat;
		boolean previousActive = this.isOn;
		try {
			this.setupTanks();
			this.tryPullHeat();
			int reportedHeat = this.heat;
			this.applyAmbientHeat();
			NBTTagCompound data = new NBTTagCompound();
        thermal.writeStatus(data); data.setLong("realSimHeatBuffer", thermalBufferQuanta); data.setString("realSimSteamOutlet", boilerOutlet().identity);
			tanks[0].writeToNBT(data, "0");
			data.setInteger("heat", reportedHeat);
			this.isOn = false;
			this.tryConvert();
			tanks[1].writeToNBT(data, "1");
			if(tanks[1].getFill() > 0) this.sendFluid();
			data.setBoolean("isOn", this.isOn);
			data.setBoolean("muffled", this.muffled);
			this.writeExtraPacketData(data);
			INBTPacketReceiver.networkPack(this, data, 25);
		} finally {
			this.runtimeFluidMutation = false;
		}
		if(previousHeat != this.heat || previousActive != this.isOn) this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	protected boolean isBoilerDisabled() { return false; }
	protected void writeExtraPacketData(NBTTagCompound data) { }

	private boolean trackRuntimeTanks() {
		if(runtimeTrackedTanks == null || runtimeTrackedTanks.length != tanks.length) runtimeTrackedTanks = new FluidTank[tanks.length];
		boolean changed = false;
		for(int i = 0; i < tanks.length; i++) {
			if(runtimeTrackedTanks[i] != tanks[i]) {
				runtimeTrackedTanks[i] = tanks[i];
				if(tanks[i] != null) tanks[i].setChangeListener(runtimeTankListener);
				changed = true;
			}
		}
		return changed;
	}

	private void reconcileTankTypes() {
		runtimeFluidMutation = true;
		try { this.setupTanks(); }
		finally { runtimeFluidMutation = false; }
	}

	private void refreshRuntimeConnections() {
		int orientation = this.getBlockMetadata();
		if(runtimeConnections == null || observedOrientation != orientation) {
			runtimeConnections = this.getBoilerConnections();
			observedOrientation = orientation;
		}
	}

	private void subscribeToInput() {
		for(DirPos pos : runtimeConnections) {
            for(FluidTank tank : getReceivingTanks()) this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
            this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
        }
	}

	private void sendFluid() {
		for(DirPos pos : runtimeConnections) this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir().getOpposite());
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized || this.isBoilerDisabled()) {
			this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
			return;
		}
		if(thermal.ownedMass() > 0 || (isPhysicalSteamPath() || isBlastHeatingPath()) && tanks[0].getFill() > 0 || physicalBatch.active() || selectedHeating() != null && tanks[0].getFill() > 0 || heat > 0 || tanks[1].getFill() > 0 || this.hasExternalHeatOpportunity()) this.scheduleMachineTransition(now + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
	}

	private boolean hasExternalHeatOpportunity() {
		TileEntity con = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
		if(con instanceof IHeatSource && ((IHeatSource) con).getHeatStored() > heat) return true;
		return this.isAmbientHeatAvailable() && heat < this.getMaxBoilerHeat();
	}

	private boolean isAmbientHeatAvailable() {
		int light = worldObj.getSavedLightValue(EnumSkyBlock.Sky, xCoord, yCoord, zCoord);
		return light > 7 && TomSaveData.forWorld(worldObj).fire > 1e-5;
	}

	private void applyAmbientHeat() {
		if(this.isAmbientHeatAvailable()) this.heat += ((this.getMaxBoilerHeat() - heat) * 0.000005D);
	}

	protected void tryPullHeat() {
		TileEntity con = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
		if(con instanceof IHeatSource) {
			IHeatSource source = (IHeatSource) con;
			int diff = source.getHeatStored() - this.heat;
			if(diff == 0) return;
			if(diff > 0) {
				diff = (int) Math.ceil(diff * this.getBoilerDiffusion());
				source.useUpHeat(diff);
				this.heat += diff;
				if(this.heat > this.getMaxBoilerHeat()) this.heat = this.getMaxBoilerHeat();
				return;
			}
		}
		this.heat = Math.max(this.heat - Math.max(this.heat / 1000, 1), 0);
	}

	protected void setupTanks() {
        if(isBlastHeatingPath()) {
            if(tanks[0].getFill() == 0) tanks[0].withPressure(1);
            tanks[1].configureForRecipe(blastOutlet(), tanks[0].getPressure());
            return;
        }
        if(isPhysicalSteamPath()) {
            com.hbm.realsim.SteamLedger.State in = thermal.ownedMass() > 0 ? thermal.inlet() : com.hbm.realsim.ThermalMachine.state(tanks[0]);
            if(in == com.hbm.realsim.SteamLedger.State.COLD || in == com.hbm.realsim.SteamLedger.State.WARM)
                com.hbm.realsim.ThermalMachine.configure(tanks[1], thermal.ownedMass() > 0 ? thermal.outlet() : boilerOutlet());
            return;
        }
        com.hbm.realsim.ProcessRecipe crude = selectedHeating();
        if(physicalBatch.active() || crude != null) {
            com.hbm.realsim.ProcessMachine.configure(physicalBatch, crude, new FluidTank[] {tanks[1]});
            return;
        }
		if(tanks[0].getTankType().hasTrait(FT_Heatable.class)) {
			FT_Heatable trait = tanks[0].getTankType().getTrait(FT_Heatable.class);
			if(trait.getEfficiency(HeatingType.BOILER) > 0) {
				HeatingStep entry = trait.getFirstStep();
				if(tanks[1].configureForRecipe(entry.typeProduced, tanks[0].getPressure()))
					tanks[1].resizePreservingContents((int) Math.min(Integer.MAX_VALUE, (long) tanks[0].getMaxFill() * entry.amountProduced / entry.amountReq));
				return;
			}
		}
		if(tanks[0].getFill() == 0) tanks[0].setTankType(Fluids.NONE);
		if(tanks[1].getFill() == 0) tanks[1].setTankType(Fluids.NONE);
	}

	protected void tryConvert() {
		if(!tanks[0].getTankType().hasTrait(FT_Heatable.class)) return;
		FT_Heatable trait = tanks[0].getTankType().getTrait(FT_Heatable.class);
		if(trait.getEfficiency(HeatingType.BOILER) <= 0) return;
        if(!tanks[1].configureForRecipe(trait.getFirstStep().typeProduced, tanks[0].getPressure())) return;
		HeatingStep entry = trait.getFirstStep();
		int inputOps = tanks[0].getFill() / entry.amountReq;
		int outputOps = Math.max(0, tanks[1].getMaxFill() - tanks[1].getFill()) / entry.amountProduced;
		int heatOps = this.heat / entry.heatReq;
		int ops = Math.min(inputOps, Math.min(outputOps, heatOps));
		tanks[0].setFill(tanks[0].getFill() - entry.amountReq * ops);
		tanks[1].setFill(tanks[1].getFill() + entry.amountProduced * ops);
		this.heat -= entry.heatReq * ops;
		if(ops > 0 && worldObj.rand.nextInt(400) == 0) worldObj.playSoundEffect(xCoord + 0.5, yCoord + 2, zCoord + 0.5, "hbm:block.boilerGroan", 0.5F, 1.0F);
		if(ops > 0) this.isOn = true;
		if(inputOps > 0 && heatOps > 0 && outputOps == 0 && this.shouldExplodeOnBlockedOutput()) this.handleBlockedOutput();
	}

	private void sendRuntimeState(int reportedHeat) {
		NBTTagCompound data = new NBTTagCompound();
        thermal.writeStatus(data); data.setLong("realSimHeatBuffer", thermalBufferQuanta); data.setString("realSimSteamOutlet", boilerOutlet().identity);
		tanks[0].writeToNBT(data, "0");
		tanks[1].writeToNBT(data, "1");
		data.setInteger("heat", reportedHeat);
		data.setBoolean("isOn", this.isOn);
		data.setBoolean("muffled", this.muffled);
		this.writeExtraPacketData(data);
		INBTPacketReceiver.networkPack(this, data, 25);
	}

	@Override public void networkUnpack(NBTTagCompound nbt) {
        thermal.readStatus(nbt); thermalBufferQuanta = nbt.getLong("realSimHeatBuffer");
        if(nbt.hasKey("realSimSteamOutlet")) readSteamOutlet(nbt);
		this.heat = nbt.getInteger("heat");
		this.tanks[0].readFromNBT(nbt, "0");
		this.tanks[1].readFromNBT(nbt, "1");
		this.isOn = nbt.getBoolean("isOn");
		this.muffled = nbt.getBoolean("muffled");
	}

	@Override public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
        thermal.read(nbt); thermalBufferQuanta = nbt.getLong("realSimHeatQuanta"); pumpEnergyQuanta = nbt.getLong("realSimPumpQuanta"); startupPaidQuanta = nbt.getLong("realSimStartupQuanta"); lastThermalTick = nbt.hasKey("realSimThermalTick") ? nbt.getLong("realSimThermalTick") : Long.MIN_VALUE;
        physicalBatch.read(nbt);
        processHeatQuanta = nbt.getLong("realSimProcessHeatQuanta");
        if(processHeatQuanta < 0 || processHeatQuanta > physicalBatch.remainingHeat())
            throw new IllegalStateException("Invalid owned boiler process heat");
        selectedSteamOutlet = null; readSteamOutlet(nbt);
		tanks[0].readFromNBT(nbt, "water");
		tanks[1].readFromNBT(nbt, "steam");
		heat = nbt.getInteger("heat");
	}

    private void readSteamOutlet(NBTTagCompound nbt) {
        com.hbm.realsim.SteamLedger.State selected = com.hbm.realsim.SteamLedger.state(nbt.getString("realSimSteamOutlet"));
        if(selected == com.hbm.realsim.SteamLedger.State.ORDINARY || selected == maximumSteamOutlet()) selectedSteamOutlet = selected;
    }
	@Override public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
        thermal.write(nbt); nbt.setLong("realSimHeatQuanta", thermalBufferQuanta); nbt.setLong("realSimPumpQuanta", pumpEnergyQuanta); nbt.setLong("realSimStartupQuanta", startupPaidQuanta); nbt.setLong("realSimThermalTick", lastThermalTick);
        physicalBatch.write(nbt);
        nbt.setLong("realSimProcessHeatQuanta", processHeatQuanta);
        nbt.setString("realSimSteamOutlet", boilerOutlet().identity);
		tanks[0].writeToNBT(nbt, "water");
		tanks[1].writeToNBT(nbt, "steam");
		nbt.setInteger("heat", heat);
	}

	@Override public FluidTank[] getAllTanks() { return tanks; }
	@Override public FluidTank[] getSendingTanks() { return tanks[0].getFill() > tanks[0].getMaxFill() ? tanks : new FluidTank[] {tanks[1]}; }
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] {tanks[0]}; }

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        if(live) com.hbm.realsim.ThermalInfo.boiler(lines, thermal, getRatedThermalWatts(), thermalBufferQuanta);
        else lines.add("Rated thermal transfer: " + com.hbm.realsim.MachineInfo.thermalWatts(getRatedThermalWatts()));
        lines.add("Feed-pump electrical ceiling: " + com.hbm.realsim.MachineInfo.watts(getRatedFeedPumpWatts()));
        if(live) lines.add("Current feed-pump draw: " + com.hbm.realsim.MachineInfo.watts(thermal.currentWorkQuanta * 10L));
        lines.add("Sufficiently hot process heat is required");
        lines.add("External heater: center its upward top contact directly under the boiler's bottom core; leave its full 3x3 layer clear");
        if(this instanceof TileEntityHeatBoilerIndustrial) lines.add("Internal fuel firing or qualified external heat supplies steam, blast, oil and coolant duty");
        if(live && physicalBatch.active()) {
            com.hbm.realsim.MachineInfo.process(lines, physicalBatch.recipe());
            lines.add("Paid heat awaiting process progress: " + api.hbm.energymk2.EnergyUnits.formatJoules(processHeatQuanta));
        }
        lines.add("Shared processes: water, hot blast, oil heating and compatible coolants");
        lines.add("Use a fluid identifier to select the inlet; drain old feed first");
        lines.add("Steam outlet: " + boilerOutlet().identity + "; select a supported steam identifier with outlet drained");
    }
}
