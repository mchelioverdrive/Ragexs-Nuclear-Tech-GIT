package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.inventory.container.ContainerMachineMilkReformer;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMilkReformer;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;


public class TileEntityMachineMilkReformer extends TileEntityMachineBase implements IGUIProvider, IFluidStandardTransceiver, IEnergyReceiverMK2 {

	public FluidTank tanks[];
	public long energyQuanta;
    public final com.hbm.realsim.ProcessBatch physicalBatch = new com.hbm.realsim.ProcessBatch();
	public static final long maxPower = 100_000_000;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeBatchMutation;
	private boolean runtimeBatchActive;
	private long lastBatchTick = Long.MIN_VALUE;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidType observedInputType;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_FLUID = 3;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private static final int TASK_SLOT_FLUID = 2;

	public TileEntityMachineMilkReformer() {
		super(11);

		this.tanks = new FluidTank[5];
		this.tanks[0] = new FluidTank(Fluids.MILK, 10).withDefaultCapacity();
		this.tanks[1] = new FluidTank(Fluids.EMILK, 10).withDefaultCapacity();
		this.tanks[2] = new FluidTank(Fluids.CMILK, 4).withDefaultCapacity();
		this.tanks[3] = new FluidTank(Fluids.CREAM, 4).withDefaultCapacity();
		tanks[4] = new FluidTank(Fluids.FRESH_WATER, 10).withDefaultCapacity();
        for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.milkreformer";
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
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
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		if(!runtimeBatchMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleBatchesThrough(worldObj.getTotalWorldTime() - 1L);
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return tanks[0].getFill() > tanks[0].getMaxFill() || tanks[0].getFill() > 0 && tanks[0].getTankType() != Fluids.MILK
                ? new FluidTank[] {tanks[1], tanks[2], tanks[3], tanks[4], tanks[0]} : new FluidTank[] {tanks[1], tanks[2], tanks[3], tanks[4]};
		}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0]};
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineMilkReformer(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMilkReformer(player.inventory, this);
	}

	@Override
	public void updateEntity() {
		// Server processing is driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		boolean wasActive = runtimeBatchActive;
		if(lastBatchTick == Long.MIN_VALUE) lastBatchTick = now;
		else this.settleBatchesThrough(now - 1L);
		runtimeInitialized = true;
		this.refreshRuntimeState();
		FluidType inputType = tanks[0].getTankType();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedInputType != inputType) this.updateConnections();
		observedInputType = inputType;
		if(wasActive) this.settleBatchesThrough(now);
		else lastBatchTick = now;
		this.evaluateAndSchedule(now);
		this.networkPackNT(150);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			this.settleBatchesThrough(now);
			long before = energyQuanta;
			runtimeEnergyMutation = true;
			try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower)); }
			finally { runtimeEnergyMutation = false; }
			if(this.observeInventoryFingerprint()) this.markNetworkDirty();
			if(before != energyQuanta) {
				this.markDirty();
				this.scheduleMachineTransition(now, TASK_ACCOUNTING, TASK_SLOT_MAIN);
			}
			if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
			else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
			return;
		} else if(taskType == TASK_ACCOUNTING && taskSlot == TASK_SLOT_MAIN) {
			this.settleBatchesThrough(now);
			if(this.hasFluidOutput()) this.scheduleMachineTransition(now, TASK_FLUID, TASK_SLOT_FLUID);
			else this.cancelMachineTransition(TASK_FLUID, TASK_SLOT_FLUID);
			this.networkPackNT(150);
		} else if(taskType == TASK_FLUID && taskSlot == TASK_SLOT_FLUID) {
			this.sendOutputFluids();
			if(this.hasFluidOutput()) this.scheduleMachineTransition(now + 1L, TASK_FLUID, TASK_SLOT_FLUID);
			else this.cancelMachineTransition(TASK_FLUID, TASK_SLOT_FLUID);
			this.networkPackNT(150);
		} else return;
		this.evaluateAndSchedule(now);
	}

    private void settleBatchesThrough(long targetTick) {
        if(worldObj == null || worldObj.isRemote || lastBatchTick == Long.MIN_VALUE || targetTick <= lastBatchTick || runtimeBatchMutation) return;
        lastBatchTick=targetTick;runtimeBatchMutation=true;runtimeEnergyMutation=true;this.beginMachineFluidMutation();
        try {
            com.hbm.realsim.ProcessRecipe recipe=physicalBatch.active()?physicalBatch.recipe():com.hbm.realsim.AncillaryProcesses.dairy();
            com.hbm.realsim.ProcessMachine.sizeProcessBuffers(recipe,new FluidTank[]{tanks[0]},new FluidTank[]{tanks[1],tanks[2],tanks[3],tanks[4]});
            com.hbm.realsim.ProcessMachine.step(this,physicalBatch,recipe,new FluidTank[]{tanks[0]},new FluidTank[]{tanks[1],tanks[2],tanks[3],tanks[4]},slots,0,-1,0,-1);
        } finally { this.endMachineFluidMutation();runtimeEnergyMutation=false;runtimeBatchMutation=false; }
        this.markDirty();
    }

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.ENERGY);
		} else if(cadence == 20) {
			this.updateConnections();
		}
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		for(int i = 0; i < tanks.length; i++) tanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		for(int i = 0; i < tanks.length; i++) tanks[i].deserialize(buf);
	}

	private void refreshRuntimeState() {
		this.beginMachineFluidMutation();
		try {
			tanks[0].loadTank(1, 2, slots);
			this.unloadOutputContainers();
		} finally {
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == com.hbm.items.ModItems.battery_creative || slots[0].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private boolean hasFluidOutput() {
		for(FluidTank tank:getSendingTanks()) if(tank.getFill()>0) return true; return false;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
        runtimeBatchActive=physicalBatch.active() || tanks[0].getTankType()==Fluids.MILK && tanks[0].getFill()>=10;
        if(runtimeBatchActive) this.scheduleMachineTransition(now+1,TASK_ACCOUNTING,TASK_SLOT_MAIN);
        else this.cancelMachineTransition(TASK_ACCOUNTING,TASK_SLOT_MAIN);
		if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
		if(this.hasFluidOutput()) this.scheduleMachineTransition(now + 1L, TASK_FLUID, TASK_SLOT_FLUID);
		else this.cancelMachineTransition(TASK_FLUID, TASK_SLOT_FLUID);
	}

	private void unloadOutputContainers() {
		tanks[1].unloadTank(3, 4, slots);
		tanks[2].unloadTank(5, 6, slots);
		tanks[3].unloadTank(7, 8, slots);
	}

	private void sendOutputFluids() {
		for(DirPos pos : getConPos()) {
			for(FluidTank output:getSendingTanks()) if(output.getFill() > 0) this.sendFluid(output, worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	private int inventoryFingerprint() {
		int hash = 1;
		for(net.minecraft.item.ItemStack stack : slots) {
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

	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
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
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		lastBatchTick = Long.MIN_VALUE;
		runtimeInitialized = false;
		runtimeBatchMutation = false;
		runtimeBatchActive = false;

		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "input");
		tanks[1].readFromNBT(nbt, "m1");
		tanks[2].readFromNBT(nbt, "m2");
		tanks[3].readFromNBT(nbt, "m3");
        if(nbt.hasKey("m4")) tanks[4].readFromNBT(nbt, "m4");
        physicalBatch.read(nbt.getCompoundTag("realSimDairy"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleBatchesThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tanks[0].writeToNBT(nbt, "input");
		tanks[1].writeToNBT(nbt, "m1");
		tanks[2].writeToNBT(nbt, "m2");
		tanks[3].writeToNBT(nbt, "m3");
        tanks[4].writeToNBT(nbt, "m4");
        NBTTagCompound owned=new NBTTagCompound();physicalBatch.write(owned);nbt.setTag("realSimDairy",owned);
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(!runtimeBatchMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleBatchesThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override protected void beforeFluidStorageChanged(FluidTank tank) {
		if(!runtimeBatchMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleBatchesThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleBatchesThrough(worldObj.getTotalWorldTime() - 1L);
		lastBatchTick = Long.MIN_VALUE;
		runtimeBatchActive = false;
		super.onChunkUnload();
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
				yCoord + 7,
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
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

}
