package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.AlkylationRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IPersistentNBT;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.Tuple.Triplet;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineAlkylation extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IPersistentNBT {
	
	public long energyQuanta;
	public static final long maxPower = 1_000_000;

	public FluidTank[] tanks;
	private static final int TASK_BATCH = 1;
	private long observedRecipeRevision = -1L;
	private long nextBatchTick = -1L;
	private int runtimeConnectionPolls;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private FluidType cachedFeedType;
	private Triplet<FluidStack, FluidStack, FluidStack> cachedRecipe;

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
			nextBatchTick = now + 2L;
		}
		this.evaluateAndSchedule(now, 2L);
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
			cachedFeedType = null;
			this.refreshCachedRecipe();
		}
		if(recipeChanged) this.markMachineDirty(MachineDirtyCause.RECIPE);
		this.networkPackNTIfDirty(25);
	}

	private void refreshCachedRecipe() {
		FluidType feed = tanks[0].getTankType();
		long revision = SerializableRecipe.getRegistryRevision();
		if(cachedFeedType != feed || observedRecipeRevision != revision) {
			cachedFeedType = feed;
			observedRecipeRevision = revision;
			cachedRecipe = AlkylationRecipes.getOutput(feed);
		}
	}

	private void configureRecipeTanks() {
		this.beginMachineFluidMutation();
		try {
			if(cachedRecipe == null) {
				tanks[2].setTankType(Fluids.NONE);
				tanks[3].setTankType(Fluids.NONE);
				return;
			}
			tanks[1].setTankType(cachedRecipe.getX().type);
			tanks[2].setTankType(cachedRecipe.getY().type);
			tanks[3].setTankType(cachedRecipe.getZ().type);
		} finally { this.endMachineFluidMutation(); }
	}

	private boolean canProcessBatch() {
		if(cachedRecipe == null || energyQuanta < 4_000L || tanks[0].getFill() < 100 || tanks[1].getFill() < cachedRecipe.getX().fill) return false;
		return tanks[2].getFill() + cachedRecipe.getY().fill <= tanks[2].getMaxFill() && tanks[3].getFill() + cachedRecipe.getZ().fill <= tanks[3].getMaxFill();
	}

	private void processBatch() {
		if(!this.canProcessBatch()) return;
		this.beginMachineFluidMutation();
		this.runtimeEnergyMutation = true;
		try {
			tanks[0].setFill(tanks[0].getFill() - 100);
			tanks[1].setFill(tanks[1].getFill() - cachedRecipe.getX().fill);
			tanks[2].setFill(tanks[2].getFill() + cachedRecipe.getY().fill);
			tanks[3].setFill(tanks[3].getFill() + cachedRecipe.getZ().fill);
			this.setStoredEnergyQuanta(energyQuanta - 4_000L);
		} finally {
			this.runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.markDirty();
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
		super.readFromNBT(nbt);
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "t" + i);
		runtimeInitialized = false;
		cachedFeedType = null;
		nextBatchTick = -1L;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
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
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] {tanks[0], tanks[1]}; }
	@Override public boolean canConnect(ForgeDirection dir) { return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN; }
	@Override public boolean canConnect(FluidType type, ForgeDirection dir) { return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN; }

	@Override
	public void writeNBT(NBTTagCompound nbt) {
		if(tanks[0].getFill() == 0 && tanks[1].getFill() == 0 && tanks[2].getFill() == 0 && tanks[3].getFill() == 0) return;
		NBTTagCompound data = new NBTTagCompound();
		for(int i = 0; i < tanks.length; i++) this.tanks[i].writeToNBT(data, "t" + i);
		nbt.setTag(NBT_PERSISTENT_KEY, data);
	}

	@Override
	public void readNBT(NBTTagCompound nbt) {
		NBTTagCompound data = nbt.getCompoundTag(NBT_PERSISTENT_KEY);
		for(int i = 0; i < tanks.length; i++) this.tanks[i].readFromNBT(data, "t" + i);
	}
}
