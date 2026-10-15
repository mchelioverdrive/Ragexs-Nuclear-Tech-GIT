package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.realsim.Combustion;
import com.hbm.realsim.FuelCredit;
import com.hbm.realsim.GeneratorStats;
import com.hbm.realsim.OutputBudget;
import java.io.IOException;
import java.util.HashMap;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.FluidContainerRegistry;
import com.hbm.inventory.container.ContainerMachineDiesel;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Polluting;
import com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade;
import com.hbm.inventory.fluid.trait.FluidTrait.FluidReleaseType;
import com.hbm.inventory.gui.GUIMachineDiesel;
import com.hbm.items.ModItems;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachinePolluting;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.FurnaceGasEmission;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineDiesel extends TileEntityMachinePolluting implements api.hbm.energymk2.IRatedGenerator, IFluidStandardTransceiver, IConfigurableMachine, IGUIProvider, IInfoProviderEC, IFluidCopiable, com.hbm.realsim.IMachineInfo {
	private static final int TASK_GENERATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeWaterlogged;
	private boolean runtimeWaterStateInitialized;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidTank observedFuelTank;

	public long energyQuanta;
	public int soundCycle = 0;
	public long powerCap = maxPower;
	public FluidTank tank;
	public final FuelCredit fuelCredit = new FuelCredit();
	public final GeneratorStats generatorStats = new GeneratorStats();
	private final OutputBudget outputBudget = new OutputBudget();
	private long lastGenerationTick = Long.MIN_VALUE;
	public static long ratedPowerWatts = 200_000L;
	
	/* CONFIGURABLE CONSTANTS */
	public static long maxPower = 40000;
	public static int fluidCap = 20;
	/** Retained for binary/source compatibility; physical efficiencies are owned by Combustion. */
	@Deprecated public static HashMap<FuelGrade, Double> fuelEfficiency = new HashMap();
	static {
		fuelEfficiency.put(FuelGrade.MEDIUM,	0.5D);
		fuelEfficiency.put(FuelGrade.HIGH,		0.75D);
		fuelEfficiency.put(FuelGrade.AERO,		0.1D);
	}
	public static boolean shutUp = false;

	private static final int[] slots_top = new int[] { 0 };
	private static final int[] slots_bottom = new int[] { 1, 2 };
	private static final int[] slots_side = new int[] { 2 };

	public TileEntityMachineDiesel() {
		super(5, 100);
		tank = new FluidTank(Fluids.DIESEL, Math.max(1, fluidCap)).withDefaultCapacity();
		this.trackMachineFluidTank(tank);
		this.trackMachineFluidTank(smoke);
		this.trackMachineFluidTank(smoke_leaded);
		this.trackMachineFluidTank(smoke_poison);
		observedFuelTank = tank;
	}

	@Override
	public String getName() {
		return "container.machineDiesel";
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		if (i == 0)
			if (FluidContainerRegistry.getFluidContent(stack, tank.getTankType()) > 0)
				return true;
		if (i == 2)
			if (stack.getItem() instanceof IBatteryItem)
				return true;

		return false;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "powerTime");
		this.powerCap = maxPower; // Configuration sets capacity; legacy energy remains untouched.
		tank.readFromNBT(nbt, "fuel");
		fuelCredit.read(nbt);
		outputBudget.read(nbt);
		lastGenerationTick = nbt.hasKey("realSimGenerationTick") ? nbt.getLong("realSimGenerationTick") : Long.MIN_VALUE;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		EnergyUnits.writeCapacityQuanta(nbt, powerCap);
		tank.writeToNBT(nbt, "fuel");
		fuelCredit.write(nbt);
		outputBudget.write(nbt);
		nbt.setLong("realSimGenerationTick", lastGenerationTick);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return side == 0 ? slots_bottom : (side == 1 ? slots_top : slots_side);
	}

	@Override
	public boolean canExtractItem(int i, ItemStack stack, int j) {
		if(i == 1) {
			if(stack.getItem() == ModItems.canister_empty || stack.getItem() == ModItems.tank_steel) {
				return true;
			}
		}
		if(i == 2) {
			if(stack.getItem() instanceof IBatteryItem && ((IBatteryItem) stack.getItem()).getStoredEnergyQuanta(stack) == ((IBatteryItem) stack.getItem()).getEnergyCapacityQuanta(stack)) {
				return true;
			}
		}

		return false;
	}

	public long getPowerScaled(long i) {
		return (Math.min(energyQuanta, powerCap) * i) / Math.max(1, powerCap);
	}

	@Override
	public void updateEntity() {
		// Fuel conversion and fluid/power logistics are driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		runtimeInitialized = true;
		this.refreshRuntimeEnvironment();
		this.refreshInventoryFingerprint();
		if(tank != observedFuelTank) {
			this.trackMachineFluidTank(tank);
			observedFuelTank = tank;
		}
		this.subscribeToAllAround(tank.getTankType(), this);
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(cadence == 5) {
			boolean environmentChanged = this.refreshRuntimeEnvironment();
			boolean inventoryChanged = this.observeInventoryFingerprint();
			if(tank != observedFuelTank) {
				this.trackMachineFluidTank(tank);
				observedFuelTank = tank;
				this.markMachineFluidDirty();
			}
			if(environmentChanged) this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
			if(inventoryChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID);
		} else if(cadence == 20) {
			this.subscribeToAllAround(tank.getTankType(), this);
			this.sendRuntimeState();
		}
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_GENERATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(lastGenerationTick == worldObj.getTotalWorldTime()) return;
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				this.providePowerToDirectReceiver(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
				this.sendSmoke(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			}

			FluidType previousType = tank.getTankType();
			if(tank.setType(3, 4, slots)) {
				this.unsubscribeToAllAround(previousType, this);
				this.subscribeToAllAround(tank.getTankType(), this);
			}
			tank.loadTank(0, 1, slots);
			long previousCapacity = powerCap;
			powerCap = maxPower;
			if(powerCap != previousCapacity) this.markPowerNetDirty();

			long charge = Math.min(energyQuanta, getMaxOutputQuantaPerTick());
			this.extractEnergyQuanta(charge - Library.chargeItemsFromTE(slots, 2, charge, charge));
			generate();
			this.markDirty();
			this.sendRuntimeState();
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.refreshInventoryFingerprint();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private void updatePowerConnections() {
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.registerPowerConnection(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	private boolean refreshRuntimeEnvironment() {
		boolean current = this.isWaterlogged();
		boolean changed = runtimeWaterStateInitialized && current != runtimeWaterlogged;
		runtimeWaterlogged = current;
		runtimeWaterStateInitialized = true;
		return changed;
	}

	private boolean hasFuelCandidate() {
		return fuelCredit.hasCredit() || (tank.getFill() > 0 && getEnergyQuantaFromFuel() > 0) || slots[0] != null || slots[3] != null || slots[4] != null;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		boolean smokeReady = smoke.getFill() > 0 || smoke_leaded.getFill() > 0 || smoke_poison.getFill() > 0;
		if(this.hasFuelCandidate() || energyQuanta > 0 || smokeReady) this.scheduleMachineTransition(now + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_GENERATE, TASK_SLOT_MAIN);
	}

	private int inventoryFingerprint() {
		int hash = 1;
		for(ItemStack stack : slots) {
			hash = 31 * hash + (stack == null ? 0 : System.identityHashCode(stack));
			if(stack != null) {
				hash = 31 * hash + stack.stackSize;
				hash = 31 * hash + stack.getItemDamage();
				hash = 31 * hash + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			}
		}
		return hash;
	}

	private boolean observeInventoryFingerprint() {
		int current = this.inventoryFingerprint();
		boolean changed = inventoryFingerprintInitialized && current != observedInventoryFingerprint;
		observedInventoryFingerprint = current;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	private void refreshInventoryFingerprint() {
		observedInventoryFingerprint = this.inventoryFingerprint();
		inventoryFingerprintInitialized = true;
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		EnergyUnits.writeCapacityQuanta(data, powerCap);
		tank.writeToNBT(data, "t");
		generatorStats.write(data, fuelCredit, ratedPowerWatts, com.hbm.realsim.Combustion.dieselEfficiency(tank.getTankType()));
		this.networkPack(data, 50);
	}
	
	public void networkUnpack(NBTTagCompound data) {
		super.networkUnpack(data);

		energyQuanta = EnergyUnits.readEnergyQuanta(data, "power");
		powerCap = EnergyUnits.readCapacityQuanta(data, "powerCap");
		com.hbm.realsim.GeneratorContract.readTankStatus(tank, data, "t");
		generatorStats.read(data, fuelCredit);
	}
	
	public boolean hasAcceptableFuel() {
		return getEnergyQuantaFromFuel() > 0;
	}
	
	public long getEnergyQuantaFromFuel() {
		return getEnergyQuantaFromFuel(tank.getTankType());
	}

	@Deprecated public long getHEFromFuel() { return EnergyUnits.quantaToLegacyHe(getEnergyQuantaFromFuel()); }
	@Deprecated public static long getHEFromFuel(FluidType type) { return EnergyUnits.quantaToLegacyHe(getEnergyQuantaFromFuel(type)); }
	
	public static long getEnergyQuantaFromFuel(FluidType type) {
		int efficiency = Combustion.dieselEfficiency(type);
		return efficiency == 0 ? 0 : (long) (com.hbm.realsim.PhysicalRegistry.get(type.getName()).chemicalMicrojoulesPerUnit() / 500_000D * efficiency / 1_000_000D);
	}

	public void generate() {
		if(worldObj == null || worldObj.isRemote || lastGenerationTick == worldObj.getTotalWorldTime()) return;
		lastGenerationTick = worldObj.getTotalWorldTime();
		generatorStats.idle();
		long demand = Math.min(ratedPowerWatts / 10L, Math.max(0L, powerCap - energyQuanta));
		if(runtimeWaterlogged || demand <= 0 || !(fuelCredit.hasCredit() || hasAcceptableFuel() && tank.getFill() > 0) || !breatheAir(1)) return;
		long generated = Combustion.generate(tank, fuelCredit, demand, Combustion.dieselEfficiency(tank.getTankType()));
		this.setStoredEnergyQuanta(energyQuanta + generated);
		generatorStats.record(generated, fuelCredit);
		if(generated > 0) {
			if(!shutUp && soundCycle++ % 3 == 0) this.worldObj.playSoundEffect(xCoord, yCoord, zCoord, "fireworks.blast", this.getVolume(0.75F), 0.5F);
			if(worldObj.getTotalWorldTime() % 5 == 0) super.pollute(Fluids.fromName(fuelCredit.fuel()), FluidReleaseType.BURN, (float) (generatorStats.fuelUnitsPerSecond * 1.25D));
			FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 900);
		}
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		this.markDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(generatorStats.outputQuanta);
	}
    @Override public OutputBudget getOutputBudget() { return outputBudget; }
    @Override public long getRatedOutputWatts() { return ratedPowerWatts; }

	@Override
	public long getEnergyCapacityQuanta() {
		return this.powerCap;
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tank};
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public String getConfigName() {
		return "dieselgen";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		maxPower = IConfigurableMachine.grabEnergyQuanta(obj, "L:energyCapacityQuanta", "L:powerCap", maxPower);
		ratedPowerWatts = Math.max(10L, Math.min(500_000L, IConfigurableMachine.grab(obj, "L:ratedPowerWatts", ratedPowerWatts)));
		fluidCap = IConfigurableMachine.grab(obj, "I:fuelCap", fluidCap);
		
		shutUp = IConfigurableMachine.grab(obj, "B:shutUp", shutUp);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("L:energyCapacityQuanta").value(maxPower);
		writer.name("L:ratedPowerWatts").value(ratedPowerWatts);
		writer.name("I:fuelCap").value(fluidCap);
		
		writer.name("B:shutUp").value(shutUp);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineDiesel(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineDiesel(player.inventory, this);
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return this.getSmokeTanks();
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		long energyFromFuelQuanta = generatorStats.outputQuanta;
		boolean active = energyFromFuelQuanta > 0;
		data.setBoolean(CompatEnergyControl.B_ACTIVE, active);
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_MB, generatorStats.fuelUnitsPerSecond / 20D);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(energyFromFuelQuanta));
	}

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Rated electrical output: " + com.hbm.realsim.MachineInfo.watts(generatorStats.ratedWatts(ratedPowerWatts)));
        if(live) { String[] status = generatorStats.info(ratedPowerWatts, fuelCredit, tank.getTankType().getName(), tank.getFill(), com.hbm.realsim.Combustion.dieselEfficiency(tank.getTankType()), false); for(int i = 1; i < status.length; i++) lines.add(status[i]); }
        lines.add(String.format(java.util.Locale.US, "Installed fuel efficiency: %.1f%%", generatorStats.ratedEfficiency(com.hbm.realsim.Combustion.dieselEfficiency(tank.getTankType())) / 10000D));
    }
}
