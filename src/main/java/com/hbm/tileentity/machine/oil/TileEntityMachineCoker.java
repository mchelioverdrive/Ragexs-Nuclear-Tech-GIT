package com.hbm.tileentity.machine.oil;

import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.container.ContainerMachineCoker;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineCoker;
import com.hbm.inventory.recipes.CokerRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.FurnaceGasEmission;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

public class TileEntityMachineCoker extends TileEntityMachineBase implements api.hbm.energymk2.IEnergyReceiverMK2, IFluidStandardTransceiver, IGUIProvider, IFluidCopiable, com.hbm.realsim.IMachineInfo, api.hbm.tile.IProcessHeatReceiver {
    @Override public api.hbm.tile.IProcessHeatSource getProcessHeatSource() {
        return com.hbm.realsim.ProcessMachine.heaterBelow(this, xCoord, yCoord, zCoord);
    }


	public boolean wasOn;
	public int progress;
	public static int processTime = 20_000;
	
	public int heat;
	public static int maxHeat = 100_000;
	public static double diffusion = 0.25D;
	
	public final com.hbm.realsim.ProcessBatch physicalBatch = new com.hbm.realsim.ProcessBatch();
    public long energyQuanta;
    public FluidTank[] tanks;
	private com.hbm.realsim.ProcessRecipe cachedRecipe;
	private boolean runtimeInitialized;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidType observedInputType;
	private long observedRecipeRevision;
	private static final int TASK_SIMULATE = 1;
	private static final int TASK_SLOT_MAIN = 0;

	public TileEntityMachineCoker() {
		super(2);
		tanks = new FluidTank[4];
		tanks[0] = new FluidTank(Fluids.HEAVYOIL, 16_000);
		tanks[1] = new FluidTank(Fluids.OIL_COKER, 8_000);
		tanks[2] = new FluidTank(Fluids.GAS_COKER, 8000);
        tanks[3] = new FluidTank(Fluids.SALT_WATER, 8000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.machineCoker";
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) return;
		if(this.wasOn && worldObj.getTotalWorldTime() % 2 == 0) {
			NBTTagCompound fx = new NBTTagCompound();
			fx.setString("type", "tower");
			fx.setFloat("lift", 10F);
			fx.setFloat("base", 0.75F);
			fx.setFloat("max", 3F);
			fx.setInteger("life", 200 + worldObj.rand.nextInt(50));
			fx.setInteger("color",0x404040);
			fx.setDouble("posX", xCoord + 0.5);
			fx.setDouble("posY", yCoord + 22);
			fx.setDouble("posZ", zCoord + 0.5);
			MainRegistry.proxy.effectNT(fx);
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20 | MachineExecutionStrategy.COARSE_100;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeState();
		FluidType inputType = tanks[0].getTankType();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedInputType != inputType) this.updateConnections();
		observedInputType = inputType;
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
        if(taskType != TASK_SIMULATE || worldObj == null || worldObj.isRemote) return;
        this.beginMachineFluidMutation();
        try {
            int before = physicalBatch.ticks();
            com.hbm.realsim.ProcessMachine.step(this, physicalBatch, cachedRecipe, new FluidTank[] {tanks[0]}, new FluidTank[] {tanks[1], tanks[2], tanks[3]}, slots, 0, -1, 1, 1);
            wasOn = physicalBatch.ticks() != before;
            if(wasOn && worldObj.getTotalWorldTime() % 5L == 0L) PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.SOOT, PollutionHandler.SOOT_PER_SECOND * 5);
            if(wasOn) FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 300);
            progress = physicalBatch.active() ? (int) ((long) physicalBatch.ticks() * processTime / physicalBatch.recipe().duration) : 0;
            api.hbm.tile.IProcessHeatSource heater = com.hbm.realsim.ProcessMachine.heater(this);
            heat = heater == null ? 0 : Math.min(maxHeat, heater.getHeatStored());
        } finally { this.endMachineFluidMutation(); }
        if(worldObj.getTotalWorldTime() % 5L == 0L) this.sendOutputFluid();
        if(worldObj.getTotalWorldTime() % 20L == 0L) this.sendRuntimeState();
        this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
			else if(this.hasHeatSource()) this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		} else if(cadence == 20) {
			this.updateConnections();
		} else if(cadence == 100) {
			long revision = SerializableRecipe.getRegistryRevision();
			if(revision != observedRecipeRevision) {
				observedRecipeRevision = revision;
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}
	
	public DirPos[] getConPos() {
		
		return new DirPos[] {
				new DirPos(xCoord + 2, yCoord, zCoord + 1, Library.POS_X),
				new DirPos(xCoord + 2, yCoord, zCoord - 1, Library.POS_X),
				new DirPos(xCoord - 2, yCoord, zCoord + 1, Library.NEG_X),
				new DirPos(xCoord - 2, yCoord, zCoord - 1, Library.NEG_X),
				new DirPos(xCoord + 1, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord - 1, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord + 1, yCoord, zCoord - 2, Library.NEG_Z),
				new DirPos(xCoord - 1, yCoord, zCoord - 2, Library.NEG_Z)
		};
	}
	
	public boolean canProcess() {
        return physicalBatch.active() || cachedRecipe != null && tanks[0].getFill() > 0;
	}

	private void refreshRuntimeState() {
        tanks[0].setType(0, slots); cachedRecipe = CokerRecipes.getPhysical(tanks[0].getTankType());
        com.hbm.realsim.ProcessMachine.configure(physicalBatch, cachedRecipe, new FluidTank[] {tanks[1], tanks[2], tanks[3]}); this.observeInventoryFingerprint();
	}


	private boolean hasHeatSource() {
        return com.hbm.realsim.ProcessMachine.heater(this) != null;
	}

	private void evaluateAndSchedule(long now) {
        if(this.canProcess()) this.scheduleMachineTransition(now + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
        else { wasOn = false; this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN); }
	}

	private void updateConnections() {
		for(DirPos pos : getConPos()) { this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir()); this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir()); }
	}

	private void sendOutputFluid() {
        for(DirPos pos : getConPos()) for(int i = 1; i < tanks.length; i++) if(tanks[i].getFill() > 0) this.sendFluid(tanks[i], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		data.setBoolean("wasOn", wasOn);
        api.hbm.energymk2.EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setInteger("heat", heat);
		data.setInteger("progress", progress);
		tanks[0].writeToNBT(data, "t0");
		tanks[1].writeToNBT(data, "t1");
        tanks[2].writeToNBT(data, "t2");
        tanks[3].writeToNBT(data, "t3");
		this.networkPack(data, 25);
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
	
	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.wasOn = nbt.getBoolean("wasOn");
        energyQuanta = api.hbm.energymk2.EnergyUnits.readEnergyQuanta(nbt, "power");
		this.heat = nbt.getInteger("heat");
		this.progress = nbt.getInteger("progress");
		tanks[0].readFromNBT(nbt, "t0");
		tanks[1].readFromNBT(nbt, "t1");
        if(nbt.hasKey("t2", 10)) tanks[2].readFromNBT(nbt, "t2");
        if(nbt.hasKey("t3", 10)) tanks[3].readFromNBT(nbt, "t3");
	}


	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return true;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 1 };
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
        physicalBatch.read(nbt);
        energyQuanta = api.hbm.energymk2.EnergyUnits.readEnergyQuanta(nbt, "power");
		super.readFromNBT(nbt);
		this.tanks[0].readFromNBT(nbt, "t0");
		this.tanks[1].readFromNBT(nbt, "t1");
        if(nbt.hasKey("t2", 10)) tanks[2].readFromNBT(nbt, "t2");
        if(nbt.hasKey("t3", 10)) tanks[3].readFromNBT(nbt, "t3");
		this.progress = 0; // Legacy progress owned no input and cannot become physical credit.
		this.heat = nbt.getInteger("heat");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
        physicalBatch.write(nbt);
        api.hbm.energymk2.EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		super.writeToNBT(nbt);
		this.tanks[0].writeToNBT(nbt, "t0");
		this.tanks[1].writeToNBT(nbt, "t1");
        this.tanks[2].writeToNBT(nbt, "t2");
        this.tanks[3].writeToNBT(nbt, "t3");
		nbt.setInteger("prog", progress);
		nbt.setInteger("heat", heat);
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[1], tanks[2], tanks[3] };
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0] };
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 2,
					yCoord,
					zCoord - 2,
					xCoord + 3,
					yCoord + 23,
					zCoord + 3
					);
		}
		
		return bb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineCoker(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineCoker(player.inventory, this);
	}
    @Override public long getStoredEnergyQuanta() { return energyQuanta; }
    @Override public long getEnergyCapacityQuanta() { return 1_000_000L; }
    @Override public void setStoredEnergyQuanta(long amount) { energyQuanta = amount; markDirty(); markMachineEnergyDirty(); }

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        com.hbm.realsim.MachineInfo.process(lines, physicalBatch.active() ? physicalBatch.recipe() : CokerRecipes.getPhysical(tanks[0].getTankType()));
        lines.add("Heater: upward top contact centered directly beneath the bottom core; full 3x3 heater layer below the base");
        lines.add("Blocked operation: check feed state, paid utilities and product storage");
    }
}
