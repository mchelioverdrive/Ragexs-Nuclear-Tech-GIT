package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.AlkylationRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IPersistentNBT;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineAlkylation extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IPersistentNBT, com.hbm.realsim.IMachineInfo {
	
	public final com.hbm.realsim.ProcessBatch physicalBatch = new com.hbm.realsim.ProcessBatch();
    public long energyQuanta;
	public static final long maxPower = 1_000_000;

	public FluidTank[] tanks;
	private static final int TASK_BATCH = 1;
	private long observedRecipeRevision = -1L;
	private long nextBatchTick = -1L;
	private int runtimeConnectionPolls;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private com.hbm.realsim.ProcessRecipe cachedRecipe;

	public TileEntityMachineAlkylation() {
		super(11);
		
		this.tanks = new FluidTank[4];
		this.tanks[0] = new FluidTank(Fluids.CHLOROMETHANE, 8_000);
		this.tanks[1] = new FluidTank(Fluids.NONE, 4_000);
		this.tanks[2] = new FluidTank(Fluids.UNSATURATEDS, 8_000);
		this.tanks[3] = new FluidTank(Fluids.CHLORINE, 8_000);
		for(FluidTank tank : this.tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.alkylation";
	}

	@Override
	public void updateEntity() {
		// Batch chemistry and fluid-network maintenance are owned by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.refreshCachedRecipe();
		this.configureRecipeTanks();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime(), 1L);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_BATCH || taskSlot != 0 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		nextBatchTick = -1L;
		if(this.canProcessBatch()) {
			this.processBatch();
			nextBatchTick = now + 1L;
		}
		this.evaluateAndSchedule(now, 1L);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(++runtimeConnectionPolls >= 2) {
				runtimeConnectionPolls = 0;
				this.updateConnections();
			}
			return;
		}
		if(cadence != 20) return;
		boolean recipeChanged = observedRecipeRevision != SerializableRecipe.getRegistryRevision();
		if(recipeChanged) {
			observedRecipeRevision = SerializableRecipe.getRegistryRevision();
			this.refreshCachedRecipe();
		}
		if(recipeChanged) this.markMachineDirty(MachineDirtyCause.RECIPE);
		this.networkPackNTIfDirty(25);
	}

	private void refreshCachedRecipe() {
        cachedRecipe = AlkylationRecipes.getPhysical(tanks[0].getTankType());
	}

	private void configureRecipeTanks() {
        com.hbm.realsim.ProcessRecipe bill = physicalBatch.active() ? physicalBatch.recipe() : cachedRecipe;
        if(bill != null) tanks[1].configureForRecipe(Fluids.fromName(bill.inputs[1]), bill.pressures[1]);
        com.hbm.realsim.ProcessMachine.configure(physicalBatch, cachedRecipe, new FluidTank[] {tanks[2], tanks[3]});
	}

	private boolean canProcessBatch() {
        return physicalBatch.active() || com.hbm.realsim.ProcessMachine.inputsReady(cachedRecipe, new FluidTank[] {tanks[0], tanks[1]}, slots, 0, -1);
	}

	private void processBatch() {
        this.beginMachineFluidMutation(); runtimeEnergyMutation = true;
        try { com.hbm.realsim.ProcessMachine.step(this, physicalBatch, cachedRecipe, new FluidTank[] {tanks[0], tanks[1]}, new FluidTank[] {tanks[2], tanks[3]}, slots, 0, -1, 0, -1); }
        finally { runtimeEnergyMutation = false; this.endMachineFluidMutation(); }
        this.markNetworkDirty();
	}

	private void evaluateAndSchedule(long now, long delay) {
		if(this.canProcessBatch()) {
			long due = Math.max(now + delay, nextBatchTick);
			nextBatchTick = due;
			this.scheduleMachineTransition(due, TASK_BATCH, 0);
		} else {
			this.cancelMachineTransition(TASK_BATCH, 0);
			nextBatchTick = -1L;
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
		this.energyQuanta = buf.readLong();
		for(int i = 0; i < tanks.length; i++) tanks[i].deserialize(buf);
	}
	
	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[1].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			if(tanks[2].getFill() > 0) this.sendFluid(tanks[2], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			if(tanks[3].getFill() > 0) this.sendFluid(tanks[3], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	
	public DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);
		
		return new DirPos[] {
			new DirPos(xCoord + rot.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2, dir),
			new DirPos(xCoord + rot.offsetX * 2 + dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2 + dir.offsetZ * 2, dir),
			new DirPos(xCoord + rot.offsetX * 1 + dir.offsetX * 3, yCoord, zCoord + rot.offsetZ * 1 + dir.offsetZ * 3, dir),
			new DirPos(xCoord + rot.offsetX * 2 - dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2 - dir.offsetZ * 2, dir),
			new DirPos(xCoord + rot.offsetX * 1 - dir.offsetX * 3, yCoord, zCoord + rot.offsetZ * 1 - dir.offsetZ * 3, dir),

			new DirPos(xCoord - rot.offsetX * 2, yCoord, zCoord - rot.offsetZ * 2, dir),
			new DirPos(xCoord - rot.offsetX * 2 + dir.offsetX * 2, yCoord, zCoord - rot.offsetZ * 2 + dir.offsetZ * 2, dir),
			new DirPos(xCoord - rot.offsetX * 1 + dir.offsetX * 3, yCoord, zCoord - rot.offsetZ * 1 + dir.offsetZ * 3, dir),
			new DirPos(xCoord - rot.offsetX * 2 - dir.offsetX * 2, yCoord, zCoord - rot.offsetZ * 2 - dir.offsetZ * 2, dir),
			new DirPos(xCoord - rot.offsetX * 1 - dir.offsetX * 3, yCoord, zCoord - rot.offsetZ * 1 - dir.offsetZ * 3, dir),
		};
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
        physicalBatch.read(nbt);
		super.readFromNBT(nbt);
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "t" + i);
		runtimeInitialized = false;
		nextBatchTick = -1L;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
        physicalBatch.write(nbt);
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		for(int i = 0; i < tanks.length; i++) tanks[i].writeToNBT(nbt, "t" + i);
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 3,
				yCoord,
				zCoord - 3,
				xCoord + 3,
				yCoord + 3,
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

	@Override public long getStoredEnergyQuanta() { return energyQuanta; }
	@Override public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}
	@Override public long getEnergyCapacityQuanta() { return maxPower; }
	@Override public FluidTank[] getAllTanks() { return tanks; }
	@Override public FluidTank[] getSendingTanks() { return new FluidTank[] {tanks[2], tanks[3]}; }
	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] {tanks[0], tanks[1]}; }
	@Override public boolean canConnect(ForgeDirection dir) { return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN; }
	@Override public boolean canConnect(FluidType type, ForgeDirection dir) { return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN; }

	@Override
	public void writeNBT(NBTTagCompound nbt) {
        physicalBatch.write(nbt);
		NBTTagCompound data = new NBTTagCompound();
		for(int i = 0; i < tanks.length; i++) this.tanks[i].writeToNBT(data, "t" + i);
		nbt.setTag(NBT_PERSISTENT_KEY, data);
	}

	@Override
	public void readNBT(NBTTagCompound nbt) {
        physicalBatch.read(nbt);
		NBTTagCompound data = nbt.getCompoundTag(NBT_PERSISTENT_KEY);
		for(int i = 0; i < tanks.length; i++) this.tanks[i].readFromNBT(data, "t" + i);
	}

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        com.hbm.realsim.MachineInfo.process(lines, physicalBatch.active() ? physicalBatch.recipe() : AlkylationRecipes.getPhysical(tanks[0].getTankType()));
        lines.add("Blocked operation: check feed state, paid utilities and product storage");
    }
}
