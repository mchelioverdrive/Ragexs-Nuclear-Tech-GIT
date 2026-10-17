package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.io.IOException;

import com.hbm.blocks.BlockDummyable;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.handler.CompatHandler;
import com.hbm.inventory.container.ContainerMachineTurbine;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineTurbine;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityMachineTurbine extends TileEntityLoadedBase implements ISidedInventory, api.hbm.energymk2.IRatedGenerator, IFluidStandardTransceiver, IBufPacketReceiver, IGUIProvider, SimpleComponent, IInfoProviderEC, CompatHandler.OCComponent, IConfigurableMachine, IFluidCopiable, com.hbm.realsim.IMachineInfo {
    public long getSteamFlowMassPerTick() { return Math.max(0, steamFlowMassPerTick); }
    public int getExpansionEfficiencyPpm() { if(worldObj != null && worldObj.isRemote && thermal.installedExpansionEfficiencyPpm >= 0) return thermal.installedExpansionEfficiencyPpm; return com.hbm.realsim.ThermalMachine.efficiency(efficiency, 750000); }
    public final com.hbm.realsim.ThermalAccount thermal = new com.hbm.realsim.ThermalAccount();
    private final com.hbm.realsim.OutputBudget outputBudget = new com.hbm.realsim.OutputBudget();
    @Override public com.hbm.realsim.OutputBudget getOutputBudget() { return outputBudget; }
    @Override public long getRatedOutputWatts() { return 75000L; }

	private static final int TASK_CONVERT = 1;
	private boolean runtimeInitialized;
	private boolean runtimeFluidMutation;
	private boolean runtimeEnergyMutation;
	private final FluidTank.ChangeListener tankListener = new FluidTank.ChangeListener() {
		@Override public void onTankChanged(FluidTank tank) {
			if(worldObj != null && !worldObj.isRemote && !runtimeFluidMutation) markMachineFluidDirty();
		}
	};

	private ItemStack slots[];

	public long energyQuanta;
	public int age = 0;
	public FluidTank[] tanks;
	
	private static final int[] slots_top = new int[] {4};
	private static final int[] slots_bottom = new int[] {6};
	private static final int[] slots_side = new int[] {4};
	
	private String customName;
	protected double[] info = new double[3];
	
	//Configurable values
	public static long maxPower = 15000L;
	public static int inputTankSize = 100;
	public static int outputTankSize = 2;
	public static int maxSteamPerTick = 6_000; // Additional per-tick inlet-count ceiling, retained for existing configurations.
    public static long steamFlowMassPerTick = 10000L;
	public static boolean massSizedInlet = true;
	public static double efficiency = 0.75D;

	public TileEntityMachineTurbine() {
		slots = new ItemStack[7];
		tanks = new FluidTank[2];
		tanks[0] = new com.hbm.realsim.SteamInletTank(com.hbm.realsim.SteamLedger.State.HOT, inputTankSize, massSizedInlet);
		tanks[1] = new FluidTank(Fluids.SPENTSTEAM, outputTankSize).withDefaultCapacity();
		tanks[0].setChangeListener(tankListener);
		tanks[1].setChangeListener(tankListener);
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.runtimeFluidMutation = true;
		try { this.loadInputContainers(); }
		finally { this.runtimeFluidMutation = false; }
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		this.subscribeToInput();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendStandard(25);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.updatePowerConnections();
		this.subscribeToInput();
		this.sendStandard(25);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_CONVERT || taskSlot != 0 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeFluidMutation = true;
		runtimeEnergyMutation = true;
		try { this.runTurbineStep(); }
		finally {
			runtimeFluidMutation = false;
			runtimeEnergyMutation = false;
		}
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private void loadInputContainers() {
		tanks[0].setType(0, 1, slots);
		if(((com.hbm.realsim.SteamInletTank) tanks[0]).compatible()) tanks[0].loadTank(2, 3, slots);
	}

	private void subscribeToInput() {
		this.subscribeToAllAround(tanks[0].getTankType(), this);
	}

	private void updatePowerConnections() {
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.registerPowerConnection(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	private void evaluateAndSchedule(long now) {
		if(tanks[0].getFill() > 0 || tanks[1].getFill() > 0 || energyQuanta > 0) this.scheduleMachineTransition(now + 1L, TASK_CONVERT, 0);
		else this.cancelMachineTransition(TASK_CONVERT, 0);
	}
	@Override
	public String getConfigName() {
		return "steamturbine";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		maxPower = IConfigurableMachine.grabEnergyQuanta(obj, "L:energyCapacityQuanta", "L:maxPower", maxPower);
		massSizedInlet = IConfigurableMachine.grab(obj, "B:massSizedInlet", !obj.has("I:inputTankSize"));
		inputTankSize = IConfigurableMachine.grab(obj, "I:inputTankSize", inputTankSize);
		outputTankSize = IConfigurableMachine.grab(obj, "I:outputTankSize", outputTankSize);
		maxSteamPerTick = IConfigurableMachine.grab(obj, "I:maxSteamPerTick", maxSteamPerTick);
        steamFlowMassPerTick = Math.max(0, IConfigurableMachine.grab(obj, "L:steamFlowMilligramsPerTick", steamFlowMassPerTick));
		efficiency = IConfigurableMachine.grab(obj, "D:efficiency", efficiency);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("L:energyCapacityQuanta").value(maxPower);
		writer.name("I:inputTankSize").value(inputTankSize);
		writer.name("B:massSizedInlet").value(massSizedInlet);
		writer.name("I:outputTankSize").value(outputTankSize);
		writer.name("I:maxSteamPerTick").value(maxSteamPerTick);
        writer.name("L:steamFlowMilligramsPerTick").value(steamFlowMassPerTick);
        writer.name("INFO").value("75 kWe; ordinary/hot steam only; mass-flow ceiling and legacy inlet-count cap both apply");
		writer.name("D:efficiency").value(efficiency);
	}

	@Override
	public int getSizeInventory() {
		return slots.length;
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		return slots[i];
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int i) {
		if(slots[i] != null)
		{
			ItemStack itemStack = slots[i];
			slots[i] = null;
			if(!runtimeFluidMutation) this.markMachineDirty(MachineDirtyCause.INVENTORY);
			return itemStack;
		} else {
		return null;
		}
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemStack) {
		slots[i] = itemStack;
		if(!runtimeFluidMutation) this.markMachineDirty(MachineDirtyCause.INVENTORY);
		if(itemStack != null && itemStack.stackSize > getInventoryStackLimit())
		{
			itemStack.stackSize = getInventoryStackLimit();
		}
	}

	@Override
	public String getInventoryName() {
		return this.hasCustomInventoryName() ? this.customName : "container.machineTurbine";
	}

	@Override
	public boolean hasCustomInventoryName() {
		return this.customName != null && this.customName.length() > 0;
	}
	
	public void setCustomName(String name) {
		this.customName = name;
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		if(worldObj.getTileEntity(xCoord, yCoord, zCoord) != this)
		{
			return false;
		}else{
			return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <=64;
		}
	}
	
	//You scrubs aren't needed for anything (right now)
	@Override
	public void openInventory() {}
	@Override
	public void closeInventory() {}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		
		if(i == 4)
			if(stack != null && stack.getItem() instanceof IBatteryItem)
				return true;
		
		return false;
	}
	
	@Override
	public ItemStack decrStackSize(int i, int j) {
		if(slots[i] != null)
		{
			if(slots[i].stackSize <= j)
			{
				ItemStack itemStack = slots[i];
				slots[i] = null;
				if(!runtimeFluidMutation) this.markMachineDirty(MachineDirtyCause.INVENTORY);
				return itemStack;
			}
			ItemStack itemStack1 = slots[i].splitStack(j);
			if (slots[i].stackSize == 0)
			{
				slots[i] = null;
			}
			if(!runtimeFluidMutation) this.markMachineDirty(MachineDirtyCause.INVENTORY);
			
			return itemStack1;
		} else {
			return null;
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
        thermal.read(nbt); outputBudget.read(nbt);
		NBTTagList list = nbt.getTagList("items", 10);

		tanks[0].readFromNBT(nbt, "water");
		tanks[1].readFromNBT(nbt, "steam");
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		
		slots = new ItemStack[getSizeInventory()];
		
		for(int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound nbt1 = list.getCompoundTagAt(i);
			byte b0 = nbt1.getByte("slot");
			if(b0 >= 0 && b0 < slots.length)
			{
				slots[b0] = ItemStack.loadItemStackFromNBT(nbt1);
			}
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
        thermal.write(nbt); outputBudget.write(nbt);
		tanks[0].writeToNBT(nbt, "water");
		tanks[1].writeToNBT(nbt, "steam");
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		
		NBTTagList list = new NBTTagList();
		
		for(int i = 0; i < slots.length; i++)
		{
			if(slots[i] != null)
			{
				NBTTagCompound nbt1 = new NBTTagCompound();
				nbt1.setByte("slot", (byte)i);
				slots[i].writeToNBT(nbt1);
				list.appendTag(nbt1);
			}
		}
		nbt.setTag("items", list);
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int p_94128_1_)
    {
        return p_94128_1_ == 0 ? slots_bottom : (p_94128_1_ == 1 ? slots_top : slots_side);
    }

	@Override
	public boolean canInsertItem(int i, ItemStack itemStack, int j) {
		return this.isItemValidForSlot(i, itemStack);
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return false;
	}
	
	public long getPowerScaled(int i) {
		return (Math.min(energyQuanta, maxPower) * i) / Math.max(1, maxPower);
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts((long) info[2]);
	}
	
	@Override
	public void updateEntity() {
		// Steam conversion and energy transfer are owned by MachineRuntime.
	}

	private void runTurbineStep() {
            // Exhaust saturation governs admission; it is not a pressure measurement.

			
			this.info[0] = this.info[1] = this.info[2] = 0;
			
			age++;
			if(age >= 2) {
				age = 0;
			}
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			this.providePowerToDirectReceiver(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			
			this.loadInputContainers();
			this.setStoredEnergyQuanta(this.chargeRatedItems(slots, 4, maxPower));
			
			FluidType in = tanks[0].getTankType();
			boolean valid = false;

            if((thermal.ownedMass() > 0 || com.hbm.realsim.GeneratorContract.acceptsSteam(com.hbm.realsim.ThermalMachine.state(tanks[0]), com.hbm.realsim.SteamLedger.State.HOT))) {
                long generated = com.hbm.realsim.ThermalMachine.ungovernedTurbine(thermal, worldObj.getTotalWorldTime(), tanks[0], tanks[1], Math.min(getSteamFlowMassPerTick(), (thermal.ownedMass() > 0 ? thermal.inlet() : com.hbm.realsim.ThermalMachine.state(tanks[0])).mass(Math.max(0, maxSteamPerTick))),
                        75000L, getExpansionEfficiencyPpm(), energyQuanta, maxPower);
                this.setStoredEnergyQuanta(Math.addExact(energyQuanta, generated));
                info[0] = thermal.currentMass == 0 ? 0 : thermal.currentMass / (double) thermal.inlet().massPerUnit;
                info[1] = thermal.currentMass == 0 ? 0 : thermal.currentMass / (double) com.hbm.realsim.ThermalMachine.state(tanks[1]).massPerUnit;
                info[2] = generated;
                valid = true;

            }

			if(!valid && tanks[1].getFill() <= 0) tanks[1].setTankType(Fluids.NONE);

			
			this.sendFluidToAll(tanks[1], this);
            if(!((com.hbm.realsim.SteamInletTank) tanks[0]).compatible()) this.sendFluidToAll(tanks[0], this);
			
			tanks[1].unloadTank(5, 6, slots);
			
			this.sendStandard(25);
	}


	@Override public void serialize(ByteBuf buf) {
		buf.writeLong(energyQuanta);
        buf.writeLong(thermal.currentMass); buf.writeLong(thermal.currentElectricQuanta); buf.writeInt(getExpansionEfficiencyPpm());
		tanks[0].serialize(buf);
		tanks[1].serialize(buf);
	}

	@Override public void deserialize(ByteBuf buf) {
		this.energyQuanta = buf.readLong();
        thermal.currentMass = buf.readLong(); thermal.currentElectricQuanta = buf.readLong(); thermal.installedExpansionEfficiencyPpm = buf.readInt();
		tanks[0].deserialize(buf);
		tanks[1].deserialize(buf);
	}
	
	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return ((com.hbm.realsim.SteamInletTank) tanks[0]).compatible() ? new FluidTank[] {tanks[1]} : tanks;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return ((com.hbm.realsim.SteamInletTank) tanks[0]).compatible() ? new FluidTank[] {tanks[0]} : new FluidTank[0];
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "ntm_turbine";
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFluid(Context context, Arguments args) {
		return new Object[] {tanks[0].getFill(), tanks[0].getMaxFill(), tanks[1].getFill(), tanks[1].getMaxFill()};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getType(Context context, Arguments args) {
		return CompatHandler.steamTypeToInt(tanks[1].getTankType());
	}

	@Callback(direct = false, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] setType(Context context, Arguments args) {
		tanks[0].setTankType(CompatHandler.intToSteamType(args.checkInteger(0)));
		return new Object[] {true};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getInfo(Context context, Arguments args) {
		return new Object[] {tanks[0].getFill(), tanks[0].getMaxFill(), tanks[1].getFill(), tanks[1].getMaxFill(), CompatHandler.steamTypeToInt(tanks[0].getTankType())};
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineTurbine(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineTurbine(player.inventory, this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, info[1] > 0);
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_MB, info[0]);
		data.setDouble(CompatEnergyControl.D_OUTPUT_MB, info[1]);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(info[2]));
	}

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Unloaded expansion continues while the spent-steam outlet has room; unused work is rejected");
        if(live) lines.add("Current electrical output: " + com.hbm.realsim.MachineInfo.watts(thermal.currentElectricQuanta * 10L));
        lines.add(String.format(java.util.Locale.US, "Steam mass-flow ceiling: %.2f kg/s", getSteamFlowMassPerTick() / 50000D));
        lines.add(String.format(java.util.Locale.US, "Expansion work factor: %.1f%%", getExpansionEfficiencyPpm() / 10000D));
        lines.add("Accepts ordinary or Hot Steam; exhaust must be drained");
    }
}
